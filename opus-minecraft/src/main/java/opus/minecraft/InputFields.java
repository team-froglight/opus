package opus.minecraft;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import opus.core.*;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Adapts native editing/selection/clipboard behavior without exposing game types in the component API. */
final class InputFields {
    private final Font font;
    private final Map<String, Entry> entries = new HashMap<>();
    private String focused;
    private boolean dragging;

    InputFields(Font font) { this.font = font; }
    static boolean isInput(UiNode node) {
        return switch (node.getType()) { case "TextField", "TextArea", "NumberField", "DateField" -> true; default -> false; };
    }

    void sync(VisualTree visuals, UiNode root) {
        entries.keySet().removeIf(id -> visuals.find(id) == null || !isInput(visuals.find(id)));
        syncNode(visuals, root);
    }

    private void syncNode(VisualTree visuals, UiNode node) {
        var bounds = visuals.bounds(node);
        if (bounds == null) return;
        if (isInput(node) && node.getIdentity() != null) {
            Entry entry = entries.computeIfAbsent(node.getIdentity(), ignored -> new Entry());
            entry.sync(node, bounds);
        }
        for (UiNode child : node.getChildren()) syncNode(visuals, child);
    }

    void focus(String identity) {
        if (java.util.Objects.equals(focused, identity)) return;
        Entry previous = entries.get(focused);
        focused = identity;
        dragging = false;
        entries.forEach((id, entry) -> entry.widget.setFocused(id.equals(identity)));
        if (previous != null && DatePicker.expanded(previous.node)) previous.datePicker.close(previous.node);
    }

    boolean dismissPicker() {
        Entry entry = entries.get(focused);
        if (entry == null || !DatePicker.expanded(entry.node)) return false;
        entry.datePicker.close(entry.node); return true;
    }

    boolean hasFocus() { return focused != null && entries.containsKey(focused); }
    boolean isDragging() { return dragging; }
    void release() { dragging = false; }

    void draw(UiNode node, GuiGraphics g, double mouseX, double mouseY, float scrollY) {
        Entry entry = entries.get(node.getIdentity());
        if (entry == null) return;
        // Native widgets use screen-space scissor rectangles, independent of the pose stack.
        g.pose().pushPose();
        g.pose().translate(0, scrollY, 0);
        int widgetY = entry.widget.getY();
        entry.widget.setY((int) (widgetY - scrollY));
        try { entry.draw(g, mouseX, mouseY - scrollY, scrollY); }
        finally { entry.widget.setY(widgetY); g.pose().popPose(); }
    }

    boolean click(UiNode node, double x, double y) {
        Entry entry = entries.get(node.getIdentity());
        if (entry == null || !entry.options.getEnabled()) return false;
        focus(node.getIdentity());
        if (node.getType().equals("NumberField") && x >= entry.x + entry.width - 28
            && y >= entry.y + InputMetrics.LABEL_HEIGHT && y < entry.y + InputMetrics.LABEL_HEIGHT + entry.fieldHeight) {
            entry.step(y < entry.y + InputMetrics.LABEL_HEIGHT + entry.fieldHeight / 2f ? 1 : -1);
            return true;
        }
        if (node.getType().equals("DateField") && !entry.options.getReadOnly()) {
            if (DatePicker.expanded(node) && y >= entry.calendarY())
                return entry.datePicker.click(node, entry.x, entry.calendarY(), entry.width, x, y);
            if (x >= entry.x + entry.width - 28 && y >= entry.y + InputMetrics.LABEL_HEIGHT && y <= entry.y + InputMetrics.LABEL_HEIGHT + entry.fieldHeight) {
                if (DatePicker.expanded(node)) entry.datePicker.close(node); else entry.datePicker.open(node);
                return true;
            }
            entry.datePicker.edit();
        }
        entry.widget.mouseClicked(x, Math.clamp(y, entry.widget.getY(), entry.widget.getY() + entry.widget.getHeight() - 1), 0);
        dragging = true;
        return true;
    }

    boolean drag(double x, double y, double dx, double dy) {
        Entry entry = entries.get(focused);
        return dragging && entry != null && entry.widget.mouseDragged(x, y, 0, dx, dy);
    }

    boolean scroll(double x, double y, double dx, double dy) {
        for (Entry entry : entries.values()) {
            if (entry.widget instanceof Area area && entry.interactive && area.isMouseOver(x, y))
                return area.scroll(x, y, dx, dy);
        }
        return false;
    }

    boolean key(int key, int scan, int modifiers) {
        Entry entry = entries.get(focused);
        if (entry == null || !entry.interactive) return false;
        if (entry.node.getType().equals("NumberField") && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            entry.step(key == GLFW.GLFW_KEY_UP ? 1 : -1); return true;
        }
        if (entry.node.getType().equals("DateField") && !entry.options.getReadOnly()) {
            if (Screen.hasAltDown() && key == GLFW.GLFW_KEY_DOWN) { entry.datePicker.open(entry.node); return true; }
            if (entry.datePicker.key(entry.node, key)) return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!(entry.widget instanceof Area)) {
                if (!entry.options.getReadOnly() && entry.node.getProps().get("onSubmit") instanceof Function0<?> submit) submit.invoke();
                return true;
            }
        }
        if (entry.options.getReadOnly() && !(Screen.isCopy(key) || Screen.isSelectAll(key)
            || key >= GLFW.GLFW_KEY_RIGHT && key <= GLFW.GLFW_KEY_END)) return true;
        if (Screen.hasControlDown() && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            String value = key == GLFW.GLFW_KEY_Y || Screen.hasShiftDown() ? entry.history.redo(entry.value()) : entry.history.undo(entry.value());
            if (value != null) {
                entry.syncing = true;
                try { entry.setValue(value); } finally { entry.syncing = false; }
                entry.changed(value, false);
            }
            return true;
        }
        entry.widget.keyPressed(key, scan, modifiers);
        return true; // Typing cannot accidentally activate a control behind the field.
    }

    boolean character(char character, int modifiers) {
        Entry entry = entries.get(focused);
        if (entry == null || !entry.interactive) return false;
        if (entry.datePicker.navigating(entry.node)) return true;
        if (!entry.options.getReadOnly()) entry.widget.charTyped(character, modifiers);
        return true;
    }

    private final class Entry {
        UiNode node;
        InputOptions options;
        AbstractWidget widget;
        boolean syncing, interactive;
        String lastValue = "";
        final EditHistory history = new EditHistory();
        final DatePicker datePicker = new DatePicker();
        int x, y, width, fieldHeight;

        void sync(UiNode next, VisualBounds bounds) {
            node = next;
            options = (InputOptions) node.getProps().get("inputOptions");
            interactive = bounds.getInputEnabled() && options.getEnabled();
            x = (int) bounds.getX(); y = (int) bounds.getY(); width = Math.max(24, (int) node.getWidth());
            fieldHeight = (int) InputMetrics.fieldHeight(((Number) node.getProps().get("rows")).intValue());
            boolean multiline = node.getType().equals("TextArea");
            int innerWidth = Math.max(8, width - (node.getType().equals("DateField") || node.getType().equals("NumberField") ? 40 : 16));
            if (widget == null || (widget instanceof Area) != multiline) {
                widget = multiline ? new Area(innerWidth, fieldHeight - 8, Component.literal((String) node.getProps().get("label")))
                    : new Single(innerWidth, Component.literal((String) node.getProps().get("label")));
                if (widget instanceof EditBox box) box.setResponder(value -> changed(value, true));
                else ((Area) widget).setValueListener(value -> changed(value, true));
                widget.setFocused(node.getIdentity().equals(focused));
            }
            widget.setX(x + (multiline ? 4 : 8));
            widget.setY(y + (int) InputMetrics.LABEL_HEIGHT + (multiline ? 4 : 10));
            widget.setWidth(innerWidth);
            widget.setHeight(multiline ? fieldHeight - 8 : 10);
            widget.active = interactive;
            syncing = true;
            try {
                if (widget instanceof EditBox box) {
                    box.setMaxLength(options.getMaxLength());
                    box.setEditable(!options.getReadOnly());
                    ((Single) box).showCaret = interactive && !options.getReadOnly();
                    box.setTextColor(options.getEnabled() ? 0xFFEAF0F7 : 0xFF98A8BB);
                    box.setTextColorUneditable(0xFFBAC6D5);
                    box.setFilter(node.getType().equals("NumberField") ? NumberInput::accepts
                        : node.getType().equals("DateField") ? DateInput::accepts : value -> true);
                } else {
                    ((Area) widget).showCaret = interactive && !options.getReadOnly();
                    ((Area) widget).setCharacterLimit(options.getMaxLength());
                }
                String value = (String) node.getProps().get("value");
                if (!value.equals(lastValue)) history.clear();
                if (!value.equals(value())) {
                    int cursor = widget instanceof EditBox box ? box.getCursorPosition() : 0;
                    setValue(value);
                    if (widget instanceof EditBox box) box.moveCursorTo(Math.min(cursor, value.length()), false);
                }
                lastValue = value();
            } finally { syncing = false; }
        }

        String value() { return widget instanceof EditBox box ? box.getValue() : ((Area) widget).getValue(); }
        int calendarY() { return (int) (y + node.getHeight() - DateInput.CALENDAR_HEIGHT); }
        void setValue(String value) { if (widget instanceof EditBox box) box.setValue(value); else ((Area) widget).setValue(value); }

        String stepped(int direction) {
            if (!interactive || options.getReadOnly()) return null;
            var numbers = (NumberOptions) node.getProps().get("numberOptions");
            String next = NumberInput.step(value(), direction, numbers == null ? NumberOptions.DEFAULT : numbers);
            if (next.length() > options.getMaxLength() || next.equals(value())) return null;
            // A bound can have a different textual representation (e.g. 100.0).
            try {
                if (new java.math.BigDecimal(next).compareTo(new java.math.BigDecimal(value())) == 0) return null;
            } catch (NumberFormatException incompleteDraft) { /* The first step completes the draft. */ }
            return next;
        }

        void step(int direction) {
            String next = stepped(direction);
            if (next == null) return;
            syncing = true;
            try { setValue(next); } finally { syncing = false; }
            changed(next, true);
        }

        @SuppressWarnings("unchecked")
        void changed(String value, boolean record) {
            if (syncing || value.equals(lastValue)) return;
            if (record) history.record(lastValue);
            lastValue = value;
            var callback = (Function1<String, Unit>) node.getProps().get("onValueChange");
            if (callback != null) callback.invoke(value);
        }

        void draw(GuiGraphics g, double mouseX, double mouseY, float scrollY) {
            int drawY = (int) (y - scrollY);
            int boxY = drawY + (int) InputMetrics.LABEL_HEIGHT;
            int accent = options.getAccent();
            boolean hovered = interactive && mouseX >= x && mouseX <= x + width && mouseY >= boxY && mouseY <= boxY + fieldHeight;
            int border = options.getError() != null ? 0xFFF29A9A : widget.isFocused() ? accent : hovered ? 0xFF61748B : 0xFF435367;
            ShapeRenderer.border(g, x, boxY, width, fieldHeight, 5, widget.isFocused() ? 1.5f : 1f,
                border, options.getEnabled() ? 0xFF101924 : 0xFF19212C, null);
            g.drawString(font, font.plainSubstrByWidth((String) node.getProps().get("label"), width), x, drawY,
                options.getEnabled() ? 0xFFEAF0F7 : 0xFF98A8BB, false);
            // Scissor respects both the field and the enclosing scrolled screen.
            g.enableScissor(x + 3, boxY + 3, x + width - 3, boxY + fieldHeight - 3);
            try {
                if (value().isEmpty() && !options.getPlaceholder().isEmpty()) {
                    if (widget instanceof Area) g.drawWordWrap(font, Component.literal(options.getPlaceholder()), x + 8, boxY + 8, width - 24, 0xFF98A8BB);
                    else g.drawString(font, font.plainSubstrByWidth(options.getPlaceholder(), widget.getWidth()), x + 8, boxY + 10, 0xFF98A8BB, false);
                }
                widget.render(g, (int) mouseX, (int) mouseY, 0);
            } finally { g.disableScissor(); }
            String support = options.getError() != null ? options.getError() : options.getHelperText();
            if (!support.isEmpty()) g.drawWordWrap(font, Component.literal(support), x, boxY + fieldHeight + 5, width,
                options.getError() != null ? 0xFFF29A9A : 0xFF98A8BB);
            if (node.getType().equals("DateField")) {
                DatePicker.icon(g, x + width - 21, boxY + 7, interactive && !options.getReadOnly() ? options.getAccent() : 0xFF98A8BB);
                if (DatePicker.expanded(node)) datePicker.draw(node, g, font, x, calendarY() - scrollY, width, options.getAccent());
            } else if (node.getType().equals("NumberField")) {
                float left = x + width - 28, middle = boxY + fieldHeight / 2f;
                ShapeRenderer.line(g, left, boxY + 3, left, boxY + fieldHeight - 3, 1, 0xFF435367);
                ShapeRenderer.line(g, left + 3, middle, x + width - 3, middle, 1, 0xFF435367);
                for (int direction : new int[]{1, -1}) {
                    float cy = boxY + fieldHeight * (direction == 1 ? 0.25f : 0.75f);
                    boolean available = stepped(direction) != null;
                    boolean over = mouseX >= left && mouseX <= x + width && Math.abs(mouseY - cy) < fieldHeight / 4f;
                    int color = !available ? 0xFF61748B : over ? options.getAccent() : 0xFFEAF0F7;
                    float cx = left + 14;
                    ShapeRenderer.line(g, cx - 3, cy + direction * 1.5f, cx, cy - direction * 1.5f, 1.4f, color);
                    ShapeRenderer.line(g, cx, cy - direction * 1.5f, cx + 3, cy + direction * 1.5f, 1.4f, color);
                }
            }
        }
    }

    private final class Single extends EditBox {
        private int anchor;
        private boolean showCaret = true, paintingWithoutCaret;
        Single(int width, Component label) {
            super(font, 0, 0, width, 10, label);
            setBordered(false);
            setTextShadow(false);
        }
        // Native EditBox paints its caret from isFocused(), even when setEditable(false).
        // Hide focus only during painting: keyboard selection and clipboard focus remain intact.
        @Override public boolean isFocused() { return !paintingWithoutCaret && super.isFocused(); }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            paintingWithoutCaret = !showCaret;
            try { super.renderWidget(g, mouseX, mouseY, partialTick); }
            finally { paintingWithoutCaret = false; }
        }
        @Override public void onClick(double x, double y) { super.onClick(x, y); anchor = getCursorPosition(); }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (button != 0 || !isFocused()) return false;
            super.onClick(x, y);
            setHighlightPos(anchor);
            return true;
        }
    }

    private final class Area extends AbstractScrollWidget {
        private boolean showCaret = true;
        private MultilineEditor editor;
        private java.util.function.Consumer<String> listener = ignored -> {};
        Area(int width, int height, Component label) {
            super(0, 0, width, height, label);
            editor = new MultilineEditor(font, Math.max(1, width - 8));
            editor.setValueListener(value -> listener.accept(value));
            editor.setCursorListener(this::revealCursor);
        }
        void setValueListener(java.util.function.Consumer<String> listener) { this.listener = listener; }
        void setCharacterLimit(int limit) { editor.setCharacterLimit(limit); }
        void setValue(String value) { editor.setValue(value); }
        String getValue() { return editor.value(); }
        @Override public void setWidth(int width) {
            if (editor != null && width != getWidth()) {
                MultilineEditor previous = editor;
                editor = new MultilineEditor(font, Math.max(1, width - 8));
                editor.setCharacterLimit(previous.maximum());
                editor.setValue(previous.value());
                editor.restoreSelection(previous.anchor(), previous.cursor());
                editor.setValueListener(value -> listener.accept(value));
                editor.setCursorListener(this::revealCursor);
            }
            super.setWidth(width);
        }
        @Override public void setHeight(int height) {
            super.setHeight(height);
            if (editor != null) setScrollAmount(scrollAmount());
        }
        @Override public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) { return editor.keyPressed(key); }
        @Override public boolean charTyped(char character, int modifiers) {
            if (!isFocused() || !net.minecraft.util.StringUtil.isAllowedChatCharacter(character)) return false;
            editor.insertText(Character.toString(character)); return true;
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            editor.setSelecting(Screen.hasShiftDown());
            editor.seekCursorToPoint(x - getX() - 4, y - getY() - 4 + scrollAmount());
            editor.setSelecting(false); return true;
        }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (button != 0 || !isFocused()) return false;
            editor.setSelecting(true);
            editor.seekCursorToPoint(x - getX() - 4, y - getY() - 4 + scrollAmount());
            editor.setSelecting(false); return true;
        }
        private void revealCursor() {
            int top = Math.max(0, editor.getLineAtCursor()) * 9;
            if (top < scrollAmount()) setScrollAmount(top);
            else if (top + 13 > scrollAmount() + getHeight() - 4) setScrollAmount(top + 17 - getHeight());
        }
        @Override protected int getInnerHeight() { return editor.getLineCount() * 9; }
        @Override protected double scrollRate() { return 9; }
        @Override protected void renderContents(GuiGraphics g, int x, int y, float partialTick) {
            editor.draw(g, font, getX() + 4, getY() + 4, getWidth() - 8, isFocused() && showCaret);
        }
        @Override protected void renderBackground(GuiGraphics g) {}
        @Override protected void renderDecorations(GuiGraphics g) {
            if (getMaxScrollAmount() > 0) {
                float thumb = Math.max(10, getHeight() * getHeight() / (float) (getInnerHeight() + 8));
                float top = getY() + (float) (scrollAmount() / getMaxScrollAmount()) * (getHeight() - thumb);
                ShapeRenderer.rounded(g, getX() + getWidth() + 3, top, 2, thumb, 1, 1, 1, 1, 0xFF61748B);
            }
        }
        boolean scroll(double x, double y, double dx, double dy) {
            double before = scrollAmount();
            super.mouseScrolled(x, y, dx, dy);
            return before != scrollAmount();
        }
    }
}
