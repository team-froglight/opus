package opus.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import opus.core.ArrowDirection;
import opus.core.Background;
import opus.core.Border;
import opus.core.Clickable;
import opus.core.Colors;
import opus.core.Composition;
import opus.core.ControlMetrics;
import opus.core.CornerClip;
import opus.core.Cutout;
import opus.core.DropdownMetrics;
import opus.core.FocusNavigation;
import opus.core.InputMetrics;
import opus.core.Modifier;
import opus.core.Modifiers;
import opus.core.Outline;
import opus.core.Padding;
import opus.core.PixelIcon;
import opus.core.Shadow;
import opus.core.TextRole;
import opus.core.UiNode;
import opus.core.VisualBounds;
import opus.core.VisualTree;
import opus.yoga.TextMeasurer;
import opus.yoga.YogaTree;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Draws an UiNode tree with GuiGraphics + the direct-GL style layer.
 * Skips subtrees outside the viewport; supports screen-level vertical scrolling with scissor.
 * Dropdown menus and calendars float in an overlay layer above the tree and receive input first.
 */
public class UiRenderer implements AutoCloseable {
    private final Font font;
    private final YogaTree yoga;
    private double hoverContentX;
    private double hoverContentY;
    private UiNode draggingSlider;
    private final InputFields inputs;
    private final ControlKeyboard keyboard = new ControlKeyboard();
    private String focusedId;
    private boolean keyboardFocus;
    private List<UiNode> focusTargets = List.of();
    private VisualTree visuals;
    private List<Overlay> overlays = List.of();
    private float[] drawColor = {1f, 1f, 1f, 1f};
    private final Map<String, HoverFeedback> hoverFeedback = new HashMap<>();
    private long frameNanos;
    private float durationScale = 1f;
    private int layoutVersion = -1;
    private float layoutWidth, layoutHeight;
    private TooltipAnchor pendingTooltip;
    private record TooltipAnchor(String text, Colors colors) {}

    /** Floating content owned by a node, in content coordinates. Later overlays are on top. */
    private record Overlay(UiNode owner, float x, float y, float width, float height) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    public UiRenderer(Font font, TextMeasurer measurer) {
        this.font = font;
        this.yoga = new YogaTree(measurer);
        this.inputs = new InputFields(font);
    }

    public YogaTree yoga() {
        return yoga;
    }

    /** Releases the native layout tree. The renderer must not be used afterwards. */
    @Override
    public void close() {
        yoga.close();
        visuals = null;
        overlays = List.of();
        focusTargets = List.of();
        hoverFeedback.clear();
        draggingSlider = null;
    }

    public void layout(Composition composition, float width, float height) {
        // Round layout to screen pixels, not GUI units, so centered content keeps its half-unit offsets.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) yoga.setPointScaleFactor((float) minecraft.getWindow().getGuiScale());
        yoga.layout(composition.getRoot(), composition, width, height);
        visuals = new VisualTree(composition.getRoot());
        layoutVersion = composition.getCompositionVersion();
        layoutWidth = width;
        layoutHeight = height;
        hoverFeedback.keySet().removeIf(id -> visuals.find(id) == null);
        focusTargets = FocusNavigation.targets(composition.getRoot(), visuals);
        inputs.sync(visuals, composition.getRoot());
        List<Overlay> found = new ArrayList<>();
        collectOverlays(composition.getRoot(), found);
        overlays = List.copyOf(found);
        if (focusedId != null && focusTargets.stream().noneMatch(node -> focusedId.equals(node.getIdentity()))) setFocus(null, false);
        if (draggingSlider != null) {
            String identity = draggingSlider.getIdentity();
            draggingSlider = identity == null ? null : visuals.find(identity);
            if (draggingSlider != null && (!draggingSlider.getType().equals("Slider")
                || !visuals.bounds(draggingSlider).getInputEnabled())) draggingSlider = null;
        }
    }

    private void collectOverlays(UiNode node, List<Overlay> out) {
        VisualBounds bounds = visuals.bounds(node);
        if (bounds == null || bounds.getOpacity() <= 0f) return;
        switch (node.getType()) {
            case "Dropdown" -> {
                if (floatProp(node, "expansion", 0f) > 0f) out.add(new Overlay(node, bounds.getX(),
                    bounds.getY() + DropdownMetrics.HEADER_HEIGHT + DropdownMetrics.MENU_GAP,
                    node.getWidth(), DropdownMetrics.menuHeight(node.getOptions().size())));
            }
            case "DateField" -> {
                if (DatePicker.expanded(node)) out.add(new Overlay(node, bounds.getX(),
                    InputFields.pickerTop(node, bounds.getY()), Math.max(24, node.getWidth()), DatePicker.HEIGHT));
            }
            default -> { }
        }
        for (UiNode child : node.getChildren()) collectOverlays(child, out);
    }

    private Overlay overlayAt(double x, double y) {
        for (int i = overlays.size() - 1; i >= 0; i--) if (overlays.get(i).contains(x, y)) return overlays.get(i);
        return null;
    }

    /** Includes floating menus, so an open menu near the bottom can be scrolled into view. */
    public float contentHeight(Composition composition) {
        // The root is laid out against the previous content height to keep
        // scrolling stable across a recompose. Its own height is therefore
        // not a measurement of the current content, especially after resize.
        float bottom = 0;
        for (UiNode child : composition.getRoot().getChildren()) {
            bottom = Math.max(bottom, subtreeBottom(child));
        }
        for (Overlay overlay : overlays) bottom = Math.max(bottom, overlay.y() + overlay.height());
        return bottom;
    }

    private static float subtreeBottom(UiNode node) {
        float bottom = node.getY() + node.getHeight();
        for (UiNode child : node.getChildren()) {
            bottom = Math.max(bottom, subtreeBottom(child));
        }
        return bottom;
    }

    /**
     * Per-frame input work that may change state, such as a held NumberField arrow. Call once per frame
     * before {@link #layout}, so the frame lays out and draws the resulting composition.
     */
    public void update(Composition composition, double mouseX, double mouseY, float scrollY) {
        ensureLayout(composition);
        inputs.repeatHeldStep(System.nanoTime(), mouseX, mouseY + scrollY);
    }

    public void render(Composition composition, GuiGraphics g, int mouseX, int mouseY,
                       float partialTick, float viewW, float viewH, float scrollY) {
        update(composition, mouseX, mouseY, scrollY);
        layout(composition, viewW, Math.max(viewH, composition.getRoot().getHeight()));
        draw(composition, g, mouseX, mouseY, partialTick, viewW, viewH, scrollY);
    }

    /** Draws an already-laid-out tree (see {@link #layout}). */
    public void draw(Composition composition, GuiGraphics g, int mouseX, int mouseY,
                     float partialTick, float viewW, float viewH, float scrollY) {
        this.hoverContentX = mouseX;
        this.hoverContentY = mouseY + scrollY;
        pendingTooltip = null;
        drawColor = RenderSystem.getShaderColor().clone();
        frameNanos = System.nanoTime();
        durationScale = composition.getAnimationDurationScale();
        g.enableScissor(0, 0, (int) viewW, (int) viewH);
        g.pose().pushPose();
        g.pose().translate(0, -scrollY, 0);
        try {
            drawChildren(composition.getRoot(), g, scrollY, viewW, viewH);
            g.pose().translate(0, 0, 300);
            for (Overlay overlay : overlays) {
                VisualBounds bounds = visuals.bounds(overlay.owner());
                withOpacity(g, bounds.getOpacity(), () -> drawOverlay(overlay, g, scrollY));
            }
        } finally {
            g.pose().popPose();
            g.disableScissor();
        }
        if (pendingTooltip != null) drawTooltip(g, mouseX, mouseY, viewW, viewH);
    }

    private void drawChildren(UiNode parent, GuiGraphics g, float scrollY, float viewW, float viewH) {
        for (UiNode child : parent.getChildren()) {
            drawNode(child, g, scrollY, viewW, viewH);
        }
    }

    private void drawNode(UiNode node, GuiGraphics g, float scrollY, float viewW, float viewH) {
        VisualBounds bounds = visuals == null ? null : visuals.bounds(node);
        if (bounds == null || bounds.getOpacity() <= 0f) return;
        withOpacity(g, bounds.getOpacity(), () -> paintNode(node, g, scrollY, viewW, viewH));
    }

    /** Multiplies the host's shader alpha for [paint], flushing only when the color actually changes. */
    private void withOpacity(GuiGraphics g, float opacity, Runnable paint) {
        float[] current = RenderSystem.getShaderColor();
        float alpha = drawColor[3] * opacity;
        if (current[0] == drawColor[0] && current[1] == drawColor[1]
            && current[2] == drawColor[2] && current[3] == alpha) {
            paint.run();
            return;
        }
        float[] previous = current.clone();
        g.flush();
        RenderSystem.setShaderColor(drawColor[0], drawColor[1], drawColor[2], alpha);
        try {
            paint.run();
        } finally {
            g.flush();
            RenderSystem.setShaderColor(previous[0], previous[1], previous[2], previous[3]);
        }
    }

    private void paintNode(UiNode node, GuiGraphics g, float scrollY, float viewW, float viewH) {
        if (!node.getVisible()) return;

        Background bg = null;
        Border border = null;
        Outline outline = null;
        Shadow shadow = null;
        CornerClip clip = null;
        Cutout cutout = null;
        Padding padding = null;
        for (Modifier.Element el : Modifiers.elements(node.getModifier())) {
            if (el instanceof Background b) {
                bg = b;
            } else if (el instanceof Border b) {
                border = b;
            } else if (el instanceof Outline o) {
                outline = o;
            } else if (el instanceof Shadow s) {
                shadow = s;
            } else if (el instanceof CornerClip c) {
                clip = c;
            } else if (el instanceof Cutout c) {
                cutout = c;
            } else if (el instanceof Padding p) {
                padding = p;
            }
        }

        VisualBounds bounds = visuals.bounds(node);
        float x = bounds.getX();
        float y = bounds.getY();
        float w = node.getWidth();
        float h = node.getHeight();
        if (node.getChildren().isEmpty() && (x + w < 0 || y + h < scrollY || x > viewW || y > scrollY + viewH)) return;
        float radius = bg != null && bg.getCornerRadius() != null ? bg.getCornerRadius()
            : border != null && border.getCornerRadius() != null ? border.getCornerRadius()
            : clip != null ? Math.max(Math.max(clip.getTopStart(), clip.getTopEnd()),
                Math.max(clip.getBottomEnd(), clip.getBottomStart())) : node.getType().equals("Button") ? node.getTheme().getShapes().getCornerRadius() : 0f;

        ShapeRenderer.Rect notch = cutout == null ? null : notchRect(cutout, x, y, w, h);

        if (shadow != null) {
            ShapeRenderer.shadow(g, x, y, w, h, radius, shadow.getBlurRadius(), shadow.getOffsetX(), shadow.getOffsetY(), shadow.getColor());
        }
        if (outline != null) {
            ShapeRenderer.outline(g, x, y, w, h, radius, outline.getWidth(), outline.getOffset(), outline.getColor(), notch);
        }
        int fill = bg == null ? 0 : surfaceHoverTint(node, bg.getColor(), x, y, w, h);
        if (bg != null && border == null && !isContentPainted(node.getType())) {
            ShapeRenderer.rounded(g, x, y, w, h, radius, radius, radius, radius, fill, notch);
        }
        if (border != null) {
            Integer bgc = bg != null ? fill : null;
            ShapeRenderer.border(g, x, y, w, h, radius, border.getWidth(), border.getColor(), bgc, notch);
        }

        drawContent(node, g, x, y, w, h, radius, padding, scrollY);
        if (hasKeyboardFocus(node) && !InputFields.isInput(node)) drawFocusRing(node, g, x, y, w, h, radius);
        if (node.getType().equals("Accordion") && !bool(node, "expanded", false)) return;
        drawChildren(node, g, scrollY, viewW, viewH);
        if (node.getType().equals("Tooltip")) {
            String tip = str(node, "tip", "");
            if (bounds.getInputEnabled() && !tip.isEmpty() && isHovered(x, y, w, h)) {
                pendingTooltip = new TooltipAnchor(tip, colors(node));
            }
        }
    }

    /**
     * An inset ring around what the focused control paints, never its whole layout box. Full-width
     * rows would otherwise get a ring far wider than their content, and an outset ring is clipped
     * by whatever encloses the control.
     */
    private void drawFocusRing(UiNode node, GuiGraphics g, float x, float y, float w, float h, float radius) {
        float rx = x, ry = y, rw = w, rh = h, r = radius > 0 ? radius : 4;
        switch (node.getType()) {
            case "RadioGroup", "MultiSelect" -> {
                List<String> options = node.getOptions();
                int option = Math.clamp(keyboard.option(), 0, Math.max(0, options.size() - 1));
                String label = options.isEmpty() ? "" : options.get(option);
                rx = x - 3; ry = y + option * ControlMetrics.OPTION_ROW_HEIGHT - 2;
                rw = Math.min(w + 3, labelledControlWidth(label)); rh = ControlMetrics.OPTION_ROW_HEIGHT; r = 5;
            }
            case "Checkbox", "RadioButton" -> {
                rx = x - 3; ry = y - 2;
                rw = Math.min(w + 3, labelledControlWidth(str(node, "label", null))); rh = ControlMetrics.OPTION_ROW_HEIGHT; r = 5;
            }
            case "Accordion" -> {
                rw = Math.min(w, 16 + font.width(str(node, "title", "")) + 8); rh = ControlMetrics.ACCORDION_HEADER_HEIGHT; r = 5;
            }
            // An open menu highlights its keyboard row instead.
            case "Dropdown" -> { rh = DropdownMetrics.HEADER_HEIGHT; r = 6; }
            default -> { }
        }
        ShapeRenderer.border(g, rx, ry, rw, rh, Math.min(r, rh / 2f), 1.5f, colors(node).getFocus(), null, null);
    }

    /** A 14px box, its 4px gap and label, with padding for the ring on both sides. */
    private float labelledControlWidth(String label) {
        return label == null || label.isEmpty() ? 20 : 3 + 18 + font.width(label) + 5;
    }

    /**
     * Clickable surfaces (a clickable modifier on a non-button node with a background) brighten on
     * hover like buttons do, so cards and rows read as interactive.
     */
    private int surfaceHoverTint(UiNode node, int color, float x, float y, float w, float h) {
        if (node.getType().equals("Button") || node.getIdentity() == null) return color;
        boolean clickable = false;
        for (Modifier.Element element : Modifiers.elements(node.getModifier())) clickable |= element instanceof Clickable;
        if (!clickable) return color;
        boolean hovered = visuals.bounds(node).getInputEnabled() && isHovered(x, y, w, h);
        float hover = hoverFeedback.computeIfAbsent(node.getIdentity(), ignored -> new HoverFeedback())
            .sample(hovered, frameNanos, durationScale);
        return hover <= 0f ? color : brighten(color, Math.round(14f * hover));
    }

    private boolean hasKeyboardFocus(UiNode node) {
        return keyboardFocus && Objects.equals(focusedId, node.getIdentity());
    }

    private boolean isHovered(float x, float y, float w, float h) {
        return hoverContentX >= x && hoverContentX <= x + w && hoverContentY >= y && hoverContentY <= y + h;
    }

    /** Floats beside the pointer, flipping to its other side near the screen edges. */
    private void drawTooltip(GuiGraphics g, int mouseX, int mouseY, float viewW, float viewH) {
        Colors colors = pendingTooltip.colors();
        var lines = font.split(Component.literal(pendingTooltip.text()), Math.max(1, (int) viewW - 28));
        int width = lines.stream().mapToInt(font::width).max().orElse(0) + 12;
        int height = lines.size() * font.lineHeight + 12;
        float x = mouseX + 10, y = mouseY + 12;
        if (x + width > viewW - 4) x = mouseX - width - 6;
        if (y + height > viewH - 4) y = mouseY - height - 6;
        x = Math.max(4, Math.min(x, viewW - width - 4));
        y = Math.max(4, Math.min(y, viewH - height - 4));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        try {
            ShapeRenderer.border(g, x, y, width, height, 4, 1, colors.getOutline(), colors.getOverlay(), null);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), (int) x + 6, (int) y + 6 + i * font.lineHeight, colors.getOnSurface(), false);
            }
            g.flush();
        } finally { g.pose().popPose(); }
    }

    private void drawOverlay(Overlay overlay, GuiGraphics g, float scrollY) {
        UiNode node = overlay.owner();
        if (node.getType().equals("DateField")) {
            inputs.drawPicker(node, g, overlay.x(), overlay.y(), overlay.width());
            return;
        }
        Colors colors = colors(node);
        List<String> options = node.getOptions();
        float progress = floatProp(node, "expansion", 1f);
        float x = overlay.x(), y = overlay.y(), w = overlay.width();
        // Reveal from the header downwards while the menu opens or closes.
        g.enableScissor((int) x, (int) Math.floor(y - scrollY), (int) Math.ceil(x + w), (int) Math.ceil(y + overlay.height() * progress - scrollY));
        try {
            ShapeRenderer.border(g, x, y, w, overlay.height(), 6f, 1f, colors.getOutline(), colors.getOverlay(), null);
            int selected = intProp(node, "selectedIndex", -1);
            int active = activeMenuRow(overlay);
            for (int i = 0; i < options.size(); i++) {
                float rowY = y + DropdownMetrics.MENU_PADDING + i * DropdownMetrics.ROW_HEIGHT;
                if (i == selected) ShapeRenderer.rounded(g, x + 4, rowY, w - 8, DropdownMetrics.ROW_HEIGHT - 1, 4, 4, 4, 4, colors.getSelection());
                if (i == active) ShapeRenderer.rounded(g, x + 4, rowY, w - 8, DropdownMetrics.ROW_HEIGHT - 1, 4, 4, 4, 4, colors.getHighlight());
                String label = font.plainSubstrByWidth(options.get(i), Math.max(0, (int) w - 36));
                drawLabel(g, label, x + 10, rowY, DropdownMetrics.ROW_HEIGHT - 1, colors.getOnSurface(), false);
                if (i == selected) drawCheck(g, x + w - 24, rowY + 3, intProp(node, "accent", colors.getAccent()));
            }
        } finally { g.disableScissor(); }
    }

    /** The row under the pointer, or else the keyboard row while the dropdown has keyboard focus. */
    private int activeMenuRow(Overlay overlay) {
        UiNode node = overlay.owner();
        if (floatProp(node, "expansion", 1f) < 1f || !visuals.bounds(node).getInputEnabled()) return -1;
        if (overlay.contains(hoverContentX, hoverContentY) && hoverContentX >= overlay.x() + 4 && hoverContentX < overlay.x() + overlay.width() - 4) {
            return DropdownMetrics.optionAt((float) (hoverContentY - overlay.y()), node.getOptions().size());
        }
        return hasKeyboardFocus(node) ? keyboard.option() : -1;
    }

    /** Widgets that paint their own visuals (bg included) instead of generic bg. */
    private static boolean isContentPainted(String type) {
        return switch (type) {
            case "Button", "Checkbox", "RadioButton", "Switch", "Slider",
                "ProgressBar", "ItemSlot", "Divider" -> true;
            default -> false;
        };
    }

    private void drawContent(UiNode node, GuiGraphics g, float x, float y, float w, float h,
                             float radius, Padding padding, float scrollY) {
        if (InputFields.isInput(node)) {
            inputs.draw(node, g, hoverContentX, hoverContentY, scrollY);
            return;
        }
        Colors colors = colors(node);
        switch (node.getType()) {
            case "Icon" -> {
                if (node.getProps().get("icon") instanceof PixelIcon icon)
                    PixelIconRenderer.draw(g, icon, x + w / 2f, y + h / 2f,
                        intProp(node, "color", colors.getOnSurface()), PixelIcon.scaleForSize(w, h));
            }
            case "Text" -> {
                // Yoga reserves padding in the node's measured size; draw the
                // glyphs inside it so section spacing appears before headings.
                float textX = x + (padding == null ? 0f : padding.getStart());
                float textY = y + (padding == null ? 0f : padding.getTop());
                String text = str(node, "text", "");
                TextRole role = (TextRole) node.getProps().get("role");
                if (role == null) role = TextRole.PARAGRAPH;
                float textSize = floatProp(node, "textSize", node.getTheme().getTypography().sizeFor(role));
                float scale = textSize / 9f;
                String line = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
                String fontId = str(node, "fontId", null);
                int textColor = intProp(node, "color", colors.getOnSurface());
                if (Fonts.isCustom(fontId)
                    && Fonts.draw(g, fontId, line, textX, textY, textSize, textColor)) {
                    break;
                }
                g.pose().pushPose();
                // Center the cap band in the line box, as every control label does.
                g.pose().translate(textX, InputMetrics.labelTop(textY, textSize, scale), 0);
                g.pose().scale(scale, scale, 1f);
                if (fontId != null && !Fonts.isCustom(fontId)) {
                    try {
                        var style = Style.EMPTY.withFont(ResourceLocation.parse(fontId));
                        g.drawString(font, Component.literal(line).withStyle(style), 0, 0, textColor, false);
                    } catch (Exception e) {
                        g.drawString(font, line, 0, 0, textColor, false);
                    }
                } else {
                    g.drawString(font, line, 0, 0, textColor, false);
                }
                g.pose().popPose();
            }
            case "Button" -> {
                boolean enabled = bool(node, "enabled", true);
                int accent = intProp(node, "accent", colors.getAccent());
                boolean hovered = enabled && visuals.bounds(node).getInputEnabled() && isHovered(x, y, w, h);
                float hover = node.getIdentity() == null ? (hovered ? 1f : 0f)
                    : hoverFeedback.computeIfAbsent(node.getIdentity(), ignored -> new HoverFeedback())
                        .sample(hovered, frameNanos, durationScale);
                int bgc = brighten(accent, Math.round(18f * hover));
                ShapeRenderer.rounded(g, x, y, w, Math.max(h, 24f), radius, radius, radius, radius, bgc);
                int content = enabled ? colors.getOnSurface() : colors.getDisabledContent();
                if (node.getProps().get("arrowDirection") instanceof ArrowDirection direction) {
                    PixelIconRenderer.draw(g, direction == ArrowDirection.LEFT ? PixelIcon.ARROW_LEFT : PixelIcon.ARROW_RIGHT,
                        x + w / 2f, y + h / 2f, content);
                } else if (node.getProps().get("icon") instanceof PixelIcon icon) {
                    PixelIconRenderer.draw(g, icon, x + w / 2f, y + h / 2f, content);
                } else {
                    drawCenteredLabel(g, str(node, "text", ""), x + w / 2f, y, h, content);
                }
            }
            case "Checkbox" -> {
                boolean checked = bool(node, "checked", false);
                int accent = intProp(node, "accent", colors.getAccent());
                ShapeRenderer.rounded(g, x, y, 14f, 14f, 4f, 4f, 4f, 4f, checked ? accent : colors.getSurface());
                if (checked) drawCheck(g, x, y, colors.getOnAccent());
                String label = str(node, "label", null);
                if (label != null) drawLabel(g, label, x + 18, y, 14, colors.getOnSurface(), false);
            }
            case "RadioButton" -> {
                boolean selected = bool(node, "selected", false);
                int accent = intProp(node, "accent", colors.getAccent());
                ShapeRenderer.rounded(g, x, y, 14f, 14f, 7f, 7f, 7f, 7f, colors.getSurface());
                if (selected) ShapeRenderer.rounded(g, x + 4, y + 4, 6f, 6f, 3f, 3f, 3f, 3f, accent);
                String label = str(node, "label", null);
                if (label != null) drawLabel(g, label, x + 18, y, 14, colors.getOnSurface(), false);
            }
            case "RadioGroup" -> {
                List<String> options = node.getOptions();
                int sel = intProp(node, "selectedIndex", -1);
                int accent = intProp(node, "accent", colors.getAccent());
                for (int i = 0; i < options.size(); i++) {
                    float iy = y + i * ControlMetrics.OPTION_ROW_HEIGHT;
                    ShapeRenderer.rounded(g, x, iy, 14f, 14f, 7f, 7f, 7f, 7f, colors.getSurface());
                    if (i == sel) ShapeRenderer.rounded(g, x + 4, iy + 4, 6f, 6f, 3f, 3f, 3f, 3f, accent);
                    drawLabel(g, options.get(i), x + 18, iy, 14, colors.getOnSurface(), false);
                }
            }
            case "Switch" -> {
                boolean checked = bool(node, "checked", false);
                float tw = Math.max(w, 34f);
                float th = 18f;
                int accent = intProp(node, "accent", colors.getAccent());
                int trackColor = intProp(node, "trackColor", checked ? accent : colors.getInactive());
                ShapeRenderer.rounded(g, x, y, tw, th, 9f, 9f, 9f, 9f, trackColor);
                float position = Math.max(0f, Math.min(1f, floatProp(node, "position", checked ? 1f : 0f)));
                float tx = x + 2f + (tw - 18f) * position;
                ShapeRenderer.rounded(g, tx, y + 2, 14f, 14f, 7f, 7f, 7f, 7f, colors.getOnAccent());
            }
            case "Slider" -> {
                float min = floatProp(node, "min", 0f);
                float max = floatProp(node, "max", 1f);
                float v = (floatProp(node, "value", min) - min) / Math.max(1e-6f, max - min);
                v = Math.min(1f, Math.max(0f, v));
                int accent = intProp(node, "accent", colors.getAccent());
                float th = 6f;
                float cy = y + h / 2;
                ShapeRenderer.rounded(g, x, cy - th / 2, w, th, 3f, 3f, 3f, 3f, colors.getInactive());
                ShapeRenderer.rounded(g, x, cy - th / 2, w * v, th, 3f, 3f, 3f, 3f, accent);
                ShapeRenderer.rounded(g, x + w * v - 7, cy - 7, 14f, 14f, 7f, 7f, 7f, 7f, colors.getOnAccent());
            }
            case "ProgressBar" -> {
                float p = floatProp(node, "progress", 0f);
                int accent = intProp(node, "accent", colors.getAccent());
                ShapeRenderer.rounded(g, x, y, w, Math.max(h, 8f), 4f, 4f, 4f, 4f, colors.getSurface());
                ShapeRenderer.rounded(g, x, y, w * p, Math.max(h, 8f), 4f, 4f, 4f, 4f, accent);
            }
            case "Image" -> {
                String id = str(node, "textureId", "");
                if (!id.isEmpty()) {
                    try {
                        ResourceLocation rl = ResourceLocation.parse(id);
                        g.blit(rl, (int) x, (int) y, 0, 0, (int) w, (int) h, (int) w, (int) h);
                    } catch (Exception ignored) {
                        g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), colors.getError());
                    }
                }
            }
            case "ItemSlot" -> {
                float slotW = Math.max(w, 20f), slotH = Math.max(h, 20f);
                if (bool(node, "framed", true)) ShapeRenderer.rounded(g, x, y, slotW, slotH, 4f, 4f, 4f, 4f, colors.getSurface());
                String itemId = str(node, "itemId", "minecraft:stone");
                int count = intProp(node, "count", 1);
                try {
                    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    ItemStack stack = new ItemStack(item, count);
                    // Whole-pixel scale keeps item sprites crisp; larger slots show a larger, centered item.
                    int scale = Math.max(1, (int) ((Math.min(slotW, slotH) - 4) / 16));
                    int size = 16 * scale;
                    int itemX = Math.round(x + (slotW - size) / 2f), itemY = Math.round(y + (slotH - size) / 2f);
                    g.pose().pushPose();
                    g.pose().translate(itemX, itemY, 0);
                    g.pose().scale(scale, scale, 1f);
                    g.renderItem(stack, 0, 0);
                    g.pose().popPose();
                    if (count > 1) {
                        // Minecraft places the count on the item's bottom-right corner, not the slot's.
                        String label = String.valueOf(count);
                        int right = itemX + size + 1;
                        int bottom = itemY + size + 1;
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        // Item counts keep Minecraft's white, shadowed style.
                        g.drawString(font, label, right - font.width(label), bottom - font.lineHeight, 0xFFFFFFFF, true);
                        g.pose().popPose();
                    }
                } catch (Exception ignored) {
                }
            }
            case "Divider" -> {
                int color = intProp(node, "color", 0x33FFFFFF);
                g.fill((int) x, (int) (y + h / 2), (int) (x + w), (int) (y + h / 2 + 1), color);
            }
            case "Pagination" -> {
                int page = intProp(node, "page", 0);
                int count = Math.max(1, intProp(node, "pageCount", 1));
                float actionWidth = ControlMetrics.paginationActionWidth(w);
                ShapeRenderer.rounded(g, x, y, actionWidth, h, 4f, 4f, 4f, 4f, colors.getSurfaceVariant());
                ShapeRenderer.rounded(g, x + w - actionWidth, y, actionWidth, h, 4f, 4f, 4f, 4f, colors.getSurfaceVariant());
                int labelWidth = Math.max(0, (int) actionWidth - 8);
                String previous = font.plainSubstrByWidth(str(node, "previousLabel", "< Prev"), labelWidth);
                String next = font.plainSubstrByWidth(str(node, "nextLabel", "Next >"), labelWidth);
                drawCenteredLabel(g, previous, x + actionWidth / 2f, y, h, page > 0 ? colors.getOnSurface() : colors.getDisabledContent());
                drawCenteredLabel(g, (page + 1) + " / " + count, x + w / 2f, y, h, colors.getMuted());
                drawCenteredLabel(g, next, x + w - actionWidth / 2f, y, h, page < count - 1 ? colors.getOnSurface() : colors.getDisabledContent());
            }
            case "Accordion" -> {
                PixelIconRenderer.draw(g, bool(node, "expanded", false) ? PixelIcon.CHEVRON_DOWN : PixelIcon.CHEVRON_RIGHT,
                    x + 8, y + ControlMetrics.ACCORDION_HEADER_HEIGHT / 2f, colors.getOnSurface());
                drawLabel(g, str(node, "title", ""), x + 16, y, ControlMetrics.ACCORDION_HEADER_HEIGHT, colors.getOnSurface(), false);
            }
            case "Dropdown" -> {
                // The header only; the open menu is painted in the overlay layer.
                List<String> options = node.getOptions();
                int sel = intProp(node, "selectedIndex", -1);
                boolean enabled = bool(node, "enabled", true);
                boolean expanded = bool(node, "expanded", false);
                float header = DropdownMetrics.HEADER_HEIGHT;
                boolean hovered = enabled && visuals.bounds(node).getInputEnabled() && isHovered(x, y, w, header);
                int borderColor = !enabled ? colors.getInactive() : expanded ? intProp(node, "accent", colors.getAccent())
                    : hovered ? colors.getMuted() : colors.getOutline();
                ShapeRenderer.border(g, x, y, w, header, 6f, 1f, borderColor, colors.getSurface(), null);
                boolean hasValue = sel >= 0 && sel < options.size();
                String label = hasValue ? options.get(sel) : str(node, "placeholder", "");
                int textColor = !enabled ? colors.getDisabledContent() : hasValue ? colors.getOnSurface() : colors.getMuted();
                drawLabel(g, font.plainSubstrByWidth(label, Math.max(0, (int) w - 32)), x + 9, y, header, textColor, false);
                PixelIconRenderer.draw(g, expanded ? PixelIcon.CHEVRON_UP : PixelIcon.CHEVRON_DOWN,
                    x + w - 14, y + header / 2, enabled ? colors.getMuted() : colors.getDisabledContent());
            }
            case "MultiSelect" -> {
                List<String> options = node.getOptions();
                Set<Integer> selected = node.getSelectedOptions();
                int accent = intProp(node, "accent", colors.getAccent());
                for (int i = 0; i < options.size(); i++) {
                    float iy = y + i * ControlMetrics.OPTION_ROW_HEIGHT;
                    boolean s = selected.contains(i);
                    ShapeRenderer.rounded(g, x, iy, 14f, 14f, 4f, 4f, 4f, 4f, s ? accent : colors.getSurface());
                    if (s) drawCheck(g, x, iy, colors.getOnAccent());
                    drawLabel(g, options.get(i), x + 18, iy, 14, colors.getOnSurface(), false);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Draws a one-line label with its cap band centered in a box. Positions stay fractional: at GUI
     * scale 2 a half unit is a whole screen pixel, so rounding to units would visibly shift labels.
     */
    private void drawLabel(GuiGraphics g, String text, float x, float boxTop, float boxHeight, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, InputMetrics.labelTop(boxTop, boxHeight), 0);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** Button-style label: horizontally centered on [centerX], with Minecraft's shadow. */
    private void drawCenteredLabel(GuiGraphics g, String text, float centerX, float boxTop, float boxHeight, int color) {
        drawLabel(g, text, centerX - font.width(text) / 2f, boxTop, boxHeight, color, true);
    }

    private static void drawCheck(GuiGraphics g, float x, float y, int color) {
        PixelIconRenderer.draw(g, PixelIcon.CHECK, x + 7, y + 7, color);
    }

    // ---- Input ----

    public boolean handleClick(Composition composition, double mouseX, double mouseY, float scrollY, int button) {
        if (button != 0) return false;
        ensureLayout(composition);
        double contentY = mouseY + scrollY;
        if (visuals == null) return false;
        Overlay overlay = overlayAt(mouseX, contentY);
        if (overlay != null) {
            clickOverlay(overlay, mouseX, contentY);
            return true;
        }
        List<UiNode> hitPath = visuals.hitPath((float) mouseX, (float) contentY);
        // Outside clicks only close menus and popups, so they never also activate what lies beneath.
        if (dismissOutside(composition, hitPath, true)) return true;
        // Walk from the painted leaf toward its parents. Searching the whole
        // tree again could activate a clickable behind the topmost control.
        for (UiNode node : hitPath) {
            if (!visuals.bounds(node).getInputEnabled()) return true;
            if (!bool(node, "enabled", true)) return true;
            if (focusTargets.contains(node)) setFocus(node, false);
            if (InputFields.isInput(node)) return inputs.click(node, mouseX, contentY);
            if (fireControl(node, mouseX, contentY)) return true;
            for (Modifier.Element element : Modifiers.elements(node.getModifier())) {
                if (element instanceof Clickable clickable) {
                    clickable.getOnClick().invoke();
                    return true;
                }
            }
        }
        setFocus(null, false);
        return false;
    }

    private void clickOverlay(Overlay overlay, double x, double y) {
        UiNode node = overlay.owner();
        if (!visuals.bounds(node).getInputEnabled()) return;
        if (node.getType().equals("DateField")) {
            setFocus(node, false);
            inputs.clickPicker(node, overlay.x(), overlay.y(), overlay.width(), x, y);
            return;
        }
        // Clicks during the opening/closing animation are consumed without choosing a row.
        if (floatProp(node, "expansion", 1f) < 1f) return;
        int index = DropdownMetrics.optionAt((float) (y - overlay.y()), node.getOptions().size());
        if (index < 0) return;
        call(node.getEvents().getSelect(), index);
        call(node.getEvents().getExpandedChange(), false);
    }

    private void setFocus(UiNode node, boolean fromKeyboard) {
        String next = node == null ? null : node.getIdentity();
        if (!Objects.equals(next, focusedId) && node != null) keyboard.focus(node);
        focusedId = next;
        keyboardFocus = fromKeyboard;
        inputs.focus(node != null && InputFields.isInput(node) ? next : null);
    }

    public boolean handleKey(Composition composition, int key, int scan, int modifiers) {
        ensureLayout(composition);
        if (key == GLFW.GLFW_KEY_TAB) {
            inputs.dismissPicker();
            dismissOutside(composition, List.of(), false);
            ensureLayout(composition);
            setFocus(FocusNavigation.next(focusTargets, focusedId, Screen.hasShiftDown()), true);
            return focusedId != null;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && inputs.dismissPicker()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && focusedId != null) { setFocus(null, false); return true; }
        if (inputs.hasFocus()) return inputs.key(key, scan, modifiers);
        UiNode node = focusedId == null || visuals == null ? null : visuals.find(focusedId);
        if (node == null) return false;
        keyboardFocus = true;
        return keyboard.key(node, key, Screen.hasShiftDown());
    }

    public boolean handleCharacter(Composition composition, char character, int modifiers) {
        ensureLayout(composition);
        return inputs.character(character, modifiers);
    }

    /** Keep a keyboard-focused control, and its open menu, visible without jumping on ordinary mouse clicks. */
    public float revealFocus(Composition composition, float scroll, float viewportHeight) {
        ensureLayout(composition);
        UiNode node = focusedId == null || visuals == null ? null : visuals.find(focusedId);
        if (node == null) return scroll;
        float top = visuals.bounds(node).getY(), bottom = top + node.getHeight();
        for (Overlay overlay : overlays) {
            if (overlay.owner() == node) bottom = Math.max(bottom, overlay.y() + overlay.height());
        }
        if (bottom > scroll + viewportHeight - 8) scroll = bottom - viewportHeight + 8;
        if (top < scroll + 8) scroll = top - 8;
        return Math.max(0, Math.min(Math.max(0, contentHeight(composition) - viewportHeight), scroll));
    }

    public boolean handleInputScroll(Composition composition, double x, double y, float scroll, double dx, double dy) {
        ensureLayout(composition);
        // Wheel input over a floating menu must not scroll a text area hidden underneath it.
        if (overlayAt(x, y + scroll) != null) return false;
        if (inputs.scroll(x, y + scroll, dx, dy)) return true;
        if (visuals == null) return false;
        // The innermost scrollable node under the pointer takes the wheel instead of the screen.
        for (UiNode node : visuals.hitPath((float) x, (float) (y + scroll))) {
            if (!visuals.bounds(node).getInputEnabled()) return false;
            for (Modifier.Element element : Modifiers.elements(node.getModifier())) {
                if (element instanceof opus.core.Scrollable scrollable) {
                    scrollable.getOnScroll().invoke((float) dy);
                    return true;
                }
            }
        }
        return false;
    }

    /** Escape closes open menus and calendars first, then popups, before the host closes the screen. */
    public boolean dismissOverlays(Composition composition) {
        ensureLayout(composition);
        return dismissOutside(composition, List.of(), false) || dismissOutside(composition, List.of(), true);
    }

    /**
     * Closes open dropdowns and calendars that are not on [keepOpen], and with [popups] also dismisses
     * popups outside it. Returns whether anything was asked to close.
     */
    private boolean dismissOutside(Composition composition, List<UiNode> keepOpen, boolean popups) {
        if (visuals == null) return false;
        // Collect first: callbacks may synchronously replace the composition tree.
        List<Runnable> callbacks = new ArrayList<>();
        collectDismissals(composition.getRoot(), keepOpen, popups, callbacks);
        callbacks.forEach(Runnable::run);
        return !callbacks.isEmpty();
    }

    private void collectDismissals(UiNode node, List<UiNode> keepOpen, boolean popups, List<Runnable> out) {
        VisualBounds bounds = visuals.bounds(node);
        if (bounds == null || !bounds.getInputEnabled()) return;
        boolean outside = !keepOpen.contains(node);
        switch (node.getType()) {
            case "Dropdown", "DateField" -> {
                Consumer<Boolean> close = node.getEvents().getExpandedChange();
                if (outside && close != null && bool(node, "expanded", false)) out.add(() -> close.accept(false));
            }
            case "Popup" -> {
                Runnable dismiss = node.getEvents().getDismiss();
                if (popups && outside && dismiss != null) out.add(dismiss);
            }
            default -> { }
        }
        for (UiNode child : node.getChildren()) collectDismissals(child, keepOpen, popups, out);
    }

    /** Structural controls interpret clicks over their own area. */
    private boolean fireControl(UiNode node, double mouseX, double mouseY) {
        VisualBounds bounds = visuals.bounds(node);
        float x = bounds.getX();
        float y = bounds.getY();
        switch (node.getType()) {
            case "Checkbox", "Switch" -> {
                Consumer<Boolean> cb = node.getEvents().getCheckedChange();
                if (cb != null) {
                    cb.accept(!bool(node, "checked", false));
                    return true;
                }
            }
            case "RadioButton" -> {
                Runnable cb = node.getEvents().getClick();
                if (cb != null) {
                    cb.run();
                    return true;
                }
            }
            case "Slider" -> {
                draggingSlider = node;
                dragSliderTo(node, mouseX);
                return true;
            }
            case "Accordion" -> {
                Consumer<Boolean> cb = node.getEvents().getExpandedChange();
                if (cb != null && mouseY < y + ControlMetrics.ACCORDION_HEADER_HEIGHT) {
                    cb.accept(!bool(node, "expanded", false));
                    return true;
                }
            }
            case "Dropdown" -> {
                // The header toggles the menu; choices are made in the overlay.
                boolean open = !bool(node, "expanded", false);
                if (open) keyboard.focus(node);
                call(node.getEvents().getExpandedChange(), open);
                return true;
            }
            case "RadioGroup" -> {
                List<String> options = node.getOptions();
                Consumer<Integer> onSelect = node.getEvents().getSelect();
                if (onSelect != null) {
                    int idx = ControlMetrics.optionAt((float) (mouseY - y), options.size());
                    if (idx >= 0) {
                        onSelect.accept(idx);
                        return true;
                    }
                }
            }
            case "MultiSelect" -> {
                List<String> options = node.getOptions();
                Set<Integer> selected = node.getSelectedOptions();
                Consumer<Set<Integer>> onChange = node.getEvents().getSelectionChange();
                if (onChange != null) {
                    int idx = ControlMetrics.optionAt((float) (mouseY - y), options.size());
                    if (idx >= 0) {
                        Set<Integer> next = new HashSet<>(selected);
                        if (!next.remove(idx)) next.add(idx);
                        onChange.accept(next);
                        return true;
                    }
                }
            }
            case "Pagination" -> {
                Consumer<Integer> cb = node.getEvents().getPageChange();
                int page = intProp(node, "page", 0);
                int count = intProp(node, "pageCount", 1);
                if (cb != null) {
                    float actionWidth = ControlMetrics.paginationActionWidth(node.getWidth());
                    double lx = mouseX - x;
                    if (lx <= actionWidth && page > 0) cb.accept(page - 1);
                    else if (lx >= node.getWidth() - actionWidth && page < count - 1) cb.accept(page + 1);
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    public boolean handleDrag(Composition composition, double mouseX, double mouseY, float scrollY, double dx, double dy) {
        ensureLayout(composition);
        if (inputs.drag(mouseX, mouseY + scrollY, dx, dy)) return true;
        if (draggingSlider != null) {
            dragSliderTo(draggingSlider, mouseX);
            return true;
        }
        return false;
    }

    public boolean handleDrag(Composition composition, double mouseX, double mouseY) {
        return handleDrag(composition, mouseX, mouseY, 0f, 0, 0);
    }

    public void handleRelease() {
        draggingSlider = null;
        inputs.release();
    }

    public boolean isDraggingControl() { return draggingSlider != null || inputs.isDragging(); }

    /** Keep swipe navigation from moving away from an open menu, calendar or popup. */
    public boolean blocksSwipeNavigation(Composition composition) {
        ensureLayout(composition);
        if (inputs.hasFocus() || !overlays.isEmpty()) return true;
        return containsPopup(composition.getRoot());
    }

    private boolean containsPopup(UiNode node) {
        VisualBounds bounds = visuals == null ? null : visuals.bounds(node);
        if (bounds == null || bounds.getOpacity() <= 0) return false;
        if (node.getType().equals("Popup")) return true;
        for (UiNode child : node.getChildren()) if (containsPopup(child)) return true;
        return false;
    }

    private void ensureLayout(Composition composition) {
        if (visuals != null && layoutVersion != composition.getCompositionVersion()) {
            layout(composition, layoutWidth, layoutHeight);
        }
    }

    private void dragSliderTo(UiNode slider, double mouseX) {
        Consumer<Float> cb = slider.getEvents().getFloatChange();
        if (cb == null) return;
        float min = floatProp(slider, "min", 0f);
        float max = floatProp(slider, "max", 1f);
        float frac = (float) ((mouseX - visuals.bounds(slider).getX()) / Math.max(1, slider.getWidth()));
        frac = Math.min(1f, Math.max(0f, frac));
        cb.accept(min + frac * (max - min));
    }

    private ShapeRenderer.Rect notchRect(Cutout cutout, float x, float y, float w, float h) {
        float s = cutout.getSize();
        return switch (cutout.getKind()) {
            case NOTCH_TOP -> new ShapeRenderer.Rect(x + w / 2 - s / 2, y - 1, x + w / 2 + s / 2, y + s / 2);
            case NOTCH_START -> new ShapeRenderer.Rect(x - 1, y + h / 2 - s / 2, x + s / 2, y + h / 2 + s / 2);
            case HOLE_CENTER -> new ShapeRenderer.Rect(x + w / 2 - s / 4, y + h / 2 - s / 4, x + w / 2 + s / 4, y + h / 2 + s / 4);
            case TAB_END -> new ShapeRenderer.Rect(x + w - s / 2, y + h / 2 - s / 4, x + w + 1, y + h / 2 + s / 4);
        };
    }

    private static Colors colors(UiNode node) {
        return node.getTheme().getColors();
    }

    private static <T> void call(Consumer<T> callback, T value) {
        if (callback != null) callback.accept(value);
    }

    private static String str(UiNode node, String key, String fallback) {
        Object v = node.getProps().get(key);
        return v instanceof String s ? s : fallback;
    }

    private static boolean bool(UiNode node, String key, boolean fallback) {
        Object v = node.getProps().get(key);
        return v instanceof Boolean b ? b : fallback;
    }

    private static int intProp(UiNode node, String key, int fallback) {
        Object v = node.getProps().get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    private static float floatProp(UiNode node, String key, float fallback) {
        Object v = node.getProps().get(key);
        return v instanceof Number n ? n.floatValue() : fallback;
    }

    private static int brighten(int argb, int amt) {
        int a = argb & 0xFF000000;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amt);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amt);
        int b = Math.min(255, (argb & 0xFF) + amt);
        return a | (r << 16) | (g << 8) | b;
    }
}
