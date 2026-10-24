package opus.minecraft;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import opus.core.*;
import org.lwjgl.glfw.GLFW;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;

/** Adapts native editing/selection/clipboard behavior without exposing game types in the component API. */
final class InputFields {
    private final Font font;
    private final Map<String, Entry> entries = new HashMap<>();
    private String focused;
    private boolean dragging;
    /** A held NumberField arrow repeats after a delay, like a native spin button. */
    private static final long STEP_DELAY_NANOS = 400_000_000L, STEP_REPEAT_NANOS = 50_000_000L;
    private Entry stepping;
    private int stepDirection;
    private long nextStep;

    InputFields(Font font) { this.font = font; }
    static boolean isInput(UiNode node) {
        return TextInputs.isTextInput(node);
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
        stepping = null;
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
    void release() { dragging = false; stepping = null; }

    /**
     * Repeats a held NumberField arrow while the pointer stays on it. Runs before layout, because a step
     * recomposes the tree. At most one step per frame, so a slow frame never bursts.
     */
    void repeatHeldStep(long now, double x, double y) {
        Entry entry = stepping;
        if (entry == null) return;
        if (entries.get(entry.node.getIdentity()) != entry || entry.stepped(stepDirection) == null) { stepping = null; return; }
        if (entry.stepDirectionAt(x, y) != stepDirection || now < nextStep) return;
        entry.step(stepDirection);
        nextStep = Math.max(nextStep + STEP_REPEAT_NANOS, now);
    }

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

    /** Top edge of a DateField's floating calendar: just below the field, over its supporting text. */
    static float pickerTop(UiNode node, float y) {
        int rows = ((Number) node.getProps().get("rows")).intValue();
        boolean dense = ((InputOptions) node.getProps().get("inputOptions")).getDense();
        return y + InputMetrics.labelHeight((String) node.getProps().get("label")) + InputMetrics.fieldHeight(rows, dense)
            + DateInput.CALENDAR_GAP;
    }

    void drawPicker(UiNode node, GuiGraphics g, float x, float y, float width) {
        Entry entry = entries.get(node.getIdentity());
        if (entry != null) entry.datePicker.draw(node, g, font, x, y, width, entry.accent());
    }

    void clickPicker(UiNode node, float x, float y, float width, double mouseX, double mouseY) {
        Entry entry = entries.get(node.getIdentity());
        if (entry == null || entry.options.getReadOnly()) return;
        focus(node.getIdentity());
        entry.datePicker.click(node, x, y, width, mouseX, mouseY);
    }

    boolean click(UiNode node, double x, double y) {
        Entry entry = entries.get(node.getIdentity());
        if (entry == null || !entry.options.getEnabled()) return false;
        focus(node.getIdentity());
        int direction = entry.stepDirectionAt(x, y);
        if (direction != 0) {
            entry.step(direction);
            stepping = entry;
            stepDirection = direction;
            nextStep = System.nanoTime() + STEP_DELAY_NANOS;
            return true;
        }
        if (node.getType().equals("DateField") && !entry.options.getReadOnly()) {
            if (x >= entry.x + entry.width - 28 && y >= entry.y + entry.labelHeight && y <= entry.y + entry.labelHeight + entry.fieldHeight) {
                if (DatePicker.expanded(node)) entry.datePicker.close(node); else entry.datePicker.open(node);
                return true;
            }
            entry.datePicker.edit();
        }
        entry.widget.mouseClicked(Math.clamp(x, entry.widget.getX(), entry.widget.getX() + entry.widget.getWidth() - 1),
            Math.clamp(y, entry.widget.getY(), entry.widget.getY() + entry.widget.getHeight() - 1), 0);
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
                if (!entry.options.getReadOnly() && entry.node.getEvents().getSubmit() != null) entry.node.getEvents().getSubmit().run();
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
        int x, y, width, fieldHeight, labelHeight;

        void sync(UiNode next, VisualBounds bounds) {
            node = next;
            options = (InputOptions) node.getProps().get("inputOptions");
            interactive = bounds.getInputEnabled() && options.getEnabled();
            x = (int) bounds.getX(); y = (int) bounds.getY(); width = Math.max(24, (int) node.getWidth());
            fieldHeight = (int) InputMetrics.fieldHeight(((Number) node.getProps().get("rows")).intValue(), options.getDense());
            labelHeight = (int) InputMetrics.labelHeight((String) node.getProps().get("label"));
            boolean multiline = node.getType().equals("TextArea");
            boolean leadingIcon = node.getProps().get("leadingIcon") instanceof PixelIcon;
            int innerWidth = InputMetrics.textWidth(width, leadingIcon,
                node.getType().equals("DateField") || node.getType().equals("NumberField"));
            if (widget == null || (widget instanceof Area) != multiline) {
                widget = multiline ? new Area(innerWidth, fieldHeight - 8, Component.literal((String) node.getProps().get("label")))
                    : new Single(innerWidth, Component.literal((String) node.getProps().get("label")));
                if (widget instanceof EditBox box) box.setResponder(value -> changed(value, true));
                else ((Area) widget).setValueListener(value -> changed(value, true));
                widget.setFocused(node.getIdentity().equals(focused));
            }
            widget.setX(x + (multiline ? 4 : InputMetrics.textInset(leadingIcon)));
            widget.setY(y + labelHeight + (multiline ? 4 : InputMetrics.textTop(fieldHeight)));
            if (widget instanceof Single single) single.nudge = InputMetrics.textNudge(fieldHeight);
            widget.setWidth(innerWidth);
            widget.setHeight(multiline ? fieldHeight - 8 : 10);
            widget.active = interactive;
            syncing = true;
            try {
                if (widget instanceof EditBox box) {
                    box.setMaxLength(options.getMaxLength());
                    box.setEditable(!options.getReadOnly());
                    ((Single) box).showCaret = interactive && !options.getReadOnly();
                    ((Single) box).selectionColor = colors().getSelection();
                    ((Single) box).caretColor = accent();
                    box.setTextColor(options.getEnabled() ? colors().getOnSurface() : colors().getDisabledContent());
                    box.setTextColorUneditable(colors().getOnSurface());
                    box.setFilter(node.getType().equals("NumberField") ? NumberInput::accepts
                        : node.getType().equals("DateField") ? DateInput::accepts : value -> true);
                } else {
                    ((Area) widget).showCaret = interactive && !options.getReadOnly();
                    ((Area) widget).colors = colors();
                    ((Area) widget).caretColor = accent();
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
        Colors colors() { return node.getTheme().getColors(); }
        /** An explicit [InputOptions] accent, or else the theme accent of the field's scope. */
        int accent() { return options.getAccent() != null ? options.getAccent() : colors().getAccent(); }
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

        /** 1 over a NumberField's upper arrow, -1 over its lower arrow, otherwise 0. */
        int stepDirectionAt(double px, double py) {
            if (!node.getType().equals("NumberField")) return 0;
            float top = y + labelHeight;
            if (px < x + width - 28 || px > x + width || py < top || py >= top + fieldHeight) return 0;
            return py < top + fieldHeight / 2f ? 1 : -1;
        }

        void step(int direction) {
            String next = stepped(direction);
            if (next == null) return;
            syncing = true;
            try { setValue(next); } finally { syncing = false; }
            changed(next, true);
        }

        void changed(String value, boolean record) {
            if (syncing || value.equals(lastValue)) return;
            if (record) history.record(lastValue);
            lastValue = value;
            var callback = node.getEvents().getTextChange();
            if (callback != null) callback.accept(value);
        }

        void draw(GuiGraphics g, double mouseX, double mouseY, float scrollY) {
            int drawY = (int) (y - scrollY);
            int boxY = drawY + labelHeight;
            Colors colors = colors();
            int accent = accent();
            boolean hovered = interactive && mouseX >= x && mouseX <= x + width && mouseY >= boxY && mouseY <= boxY + fieldHeight;
            int border = options.getError() != null ? colors.getError() : widget.isFocused() ? accent : hovered ? colors.getMuted() : colors.getOutline();
            ShapeRenderer.border(g, x, boxY, width, fieldHeight, node.getTheme().getShapes().getCornerRadius(), widget.isFocused() ? 1.5f : 1f,
                border, options.getEnabled() ? colors.getSurface() : colors.getOverlay(), null);
            if (labelHeight > 0) g.drawString(font, font.plainSubstrByWidth((String) node.getProps().get("label"), width), x, drawY,
                options.getEnabled() ? colors.getOnSurface() : colors.getDisabledContent(), false);
            // Scissor respects both the field and the enclosing scrolled screen.
            g.enableScissor(x + 3, boxY + 3, x + width - 3, boxY + fieldHeight - 3);
            try {
                if (options.showsPlaceholder(value(), widget.isFocused())) {
                    if (widget instanceof Area) g.drawWordWrap(font, Component.literal(options.getPlaceholder()), x + 8, boxY + 8, width - 24, colors.getMuted());
                    else {
                        g.pose().pushPose();
                        g.pose().translate(0, InputMetrics.textNudge(fieldHeight), 0);
                        g.drawString(font, font.plainSubstrByWidth(options.getPlaceholder(), widget.getWidth()),
                            widget.getX(), widget.getY(), colors.getMuted(), false);
                        g.pose().popPose();
                    }
                }
                if (node.getProps().get("leadingIcon") instanceof PixelIcon icon) {
                    PixelIconRenderer.draw(g, icon, x + InputMetrics.CONTENT_INSET + PixelIcon.SIZE / 2f,
                        boxY + fieldHeight / 2f, options.getEnabled() ? colors.getMuted() : colors.getDisabledContent());
                }
                widget.render(g, (int) mouseX, (int) mouseY, 0);
            } finally { g.disableScissor(); }
            String support = options.getError() != null ? options.getError() : options.getHelperText();
            if (!support.isEmpty()) g.drawWordWrap(font, Component.literal(support), x, boxY + fieldHeight + 5, width,
                options.getError() != null ? colors.getError() : colors.getMuted());
            if (node.getType().equals("DateField")) {
                DatePicker.icon(g, x + width - 21, boxY + fieldHeight / 2f - 6, interactive && !options.getReadOnly() ? accent : colors.getMuted());
            } else if (node.getType().equals("NumberField")) {
                float left = x + width - 28, middle = boxY + fieldHeight / 2f;
                ShapeRenderer.line(g, left, boxY + 3, left, boxY + fieldHeight - 3, 1, colors.getOutline());
                ShapeRenderer.line(g, left + 3, middle, x + width - 3, middle, 1, colors.getOutline());
                for (int direction : new int[]{1, -1}) {
                    float cy = boxY + fieldHeight * (direction == 1 ? 0.25f : 0.75f);
                    boolean available = stepped(direction) != null;
                    boolean over = mouseX >= left && mouseX <= x + width && Math.abs(mouseY - cy) < fieldHeight / 4f;
                    int color = !available ? colors.getDisabledContent() : over ? accent : colors.getOnSurface();
                    PixelIconRenderer.draw(g, direction == 1 ? PixelIcon.CHEVRON_UP : PixelIcon.CHEVRON_DOWN, left + 14, cy, color);
                }
            }
        }
    }

    /**
     * EditBox's scroll offset, for painting. Minecraft is an automatic module, so its packages are open;
     * without access the editor falls back to vanilla painting.
     */
    private static final VarHandle DISPLAY_POS = displayPosHandle();

    private static VarHandle displayPosHandle() {
        try {
            return MethodHandles.privateLookupIn(EditBox.class, MethodHandles.lookup())
                .findVarHandle(EditBox.class, "displayPos", int.class);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private final class Single extends EditBox {
        private int anchor, highlight, textColor, uneditableColor;
        private float nudge = InputMetrics.TEXT_NUDGE;
        private boolean showCaret = true, editable = true, paintingWithoutCaret;
        private int selectionColor, caretColor;
        private long blinkStart;
        private int blinkCursor = -1;
        private String blinkValue = "";
        Single(int width, Component label) {
            super(font, 0, 0, width, 10, label);
            setBordered(false);
            setTextShadow(false);
        }
        // EditBox's only highlight writes go through this method.
        @Override public void setHighlightPos(int position) { super.setHighlightPos(position); highlight = Math.clamp(position, 0, getValue().length()); }
        @Override public void setTextColor(int color) { super.setTextColor(color); textColor = color; }
        @Override public void setTextColorUneditable(int color) { super.setTextColorUneditable(color); uneditableColor = color; }
        @Override public void setEditable(boolean value) { super.setEditable(value); editable = value; }
        // Fallback only: vanilla paints its caret from isFocused(), even when setEditable(false).
        @Override public boolean isFocused() { return !paintingWithoutCaret && super.isFocused(); }

        /**
         * Vanilla draws the text in two halves split at the cursor and moves the second half back one
         * pixel to undo the shadow's extra advance. Without a shadow that overlaps the glyph before the
         * cursor, which shows wherever no caret covers it, such as in read-only fields. Paint the visible
         * text in one pass instead, with a themed selection behind it and a caret on top.
         */
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (DISPLAY_POS == null) {
                paintingWithoutCaret = !showCaret;
                try { super.renderWidget(g, mouseX, mouseY, partialTick); }
                finally { paintingWithoutCaret = false; }
                return;
            }
            if (!isVisible()) return;
            g.pose().pushPose();
            g.pose().translate(0, nudge, 0);
            try { paintText(g); } finally { g.pose().popPose(); }
        }

        /** Text, selection and caret, all centered on the cap band (rows 0 to 6 of the glyph cell). */
        private void paintText(GuiGraphics g) {
            String value = getValue();
            int start = Math.clamp((int) DISPLAY_POS.get(this), 0, value.length());
            String visible = font.plainSubstrByWidth(value.substring(start), getInnerWidth());
            int x = getX(), y = getY();
            int cursor = getCursorPosition() - start;
            int lo = Math.clamp(Math.min(cursor, highlight - start), 0, visible.length());
            int hi = Math.clamp(Math.max(cursor, highlight - start), 0, visible.length());
            if (lo < hi) g.fill(x + font.width(visible.substring(0, lo)), y - 2, x + font.width(visible.substring(0, hi)), y + 9, selectionColor);
            g.drawString(font, visible, x, y, editable ? textColor : uneditableColor, false);
            long now = System.nanoTime();
            // Restart the blink on every edit or cursor move so the caret stays visible while typing.
            if (getCursorPosition() != blinkCursor || !value.equals(blinkValue)) {
                blinkCursor = getCursorPosition(); blinkValue = value; blinkStart = now;
            }
            boolean blinkOn = (now - blinkStart) / 300_000_000L % 2 == 0;
            if (showCaret && super.isFocused() && blinkOn && cursor >= 0 && cursor <= visible.length()) {
                // Sit in the previous glyph's spacing column rather than on the next glyph.
                int caretX = cursor == 0 ? x : x + font.width(visible.substring(0, cursor)) - 1;
                g.fill(caretX, y - 2, caretX + 1, y + 9, caretColor);
            }
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
        private Colors colors = new Colors();
        private int caretColor = colors.getAccent();
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
            editor.draw(g, font, getX() + 4, getY() + 4, getWidth() - 8, isFocused() && showCaret, colors, caretColor);
        }
        @Override protected void renderBackground(GuiGraphics g) {}
        @Override protected void renderDecorations(GuiGraphics g) {
            if (getMaxScrollAmount() > 0) {
                float thumb = Math.max(10, getHeight() * getHeight() / (float) (getInnerHeight() + 8));
                float top = getY() + (float) (scrollAmount() / getMaxScrollAmount()) * (getHeight() - thumb);
                ShapeRenderer.rounded(g, getX() + getWidth() + 3, top, 2, thumb, 1, 1, 1, 1, colors.getMuted());
            }
        }
        boolean scroll(double x, double y, double dx, double dy) {
            double before = scrollAmount();
            super.mouseScrolled(x, y, dx, dy);
            return before != scrollAmount();
        }
    }
}
