package opus.minecraft;

import kotlin.Unit;
import com.mojang.blaze3d.systems.RenderSystem;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import opus.core.Background;
import opus.core.Border;
import opus.core.Clickable;
import opus.core.CornerClip;
import opus.core.Cutout;
import opus.core.Modifier;
import opus.core.Modifiers;
import opus.core.Composition;
import opus.core.DropdownMetrics;
import opus.core.UiNode;
import opus.core.VisualTree;
import opus.core.VisualBounds;
import opus.core.Outline;
import opus.core.Padding;
import opus.core.Shadow;
import opus.core.TextRole;
import opus.yoga.TextMeasurer;
import opus.yoga.YogaTree;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws an UiNode tree with GuiGraphics + the direct-GL style layer.
 * Skips subtrees outside the viewport; supports screen-level vertical scrolling with scissor.
 */
public class UiRenderer {
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
    private float[] drawColor = {1f, 1f, 1f, 1f};
    private final Map<String, HoverFeedback> hoverFeedback = new HashMap<>();
    private long frameNanos;
    private float durationScale = 1f;
    private int layoutVersion = -1;
    private float layoutWidth, layoutHeight;
    private TooltipAnchor pendingTooltip;
    private record TooltipAnchor(String text, float top, float bottom) {}

    public UiRenderer(Font font, TextMeasurer measurer) {
        this.font = font;
        this.yoga = new YogaTree(measurer);
        this.inputs = new InputFields(font);
    }

    public YogaTree yoga() {
        return yoga;
    }

    public void layout(Composition composition, float width, float height) {
        yoga.layout(composition.getRoot(), composition, width, height);
        visuals = new VisualTree(composition.getRoot());
        layoutVersion = composition.getCompositionVersion();
        layoutWidth = width;
        layoutHeight = height;
        hoverFeedback.keySet().removeIf(id -> visuals.find(id) == null);
        focusTargets = opus.core.FocusNavigation.targets(composition.getRoot(), visuals);
        inputs.sync(visuals, composition.getRoot());
        if (focusedId != null && focusTargets.stream().noneMatch(node -> focusedId.equals(node.getIdentity()))) setFocus(null, false);
        if (draggingSlider != null) {
            String identity = draggingSlider.getIdentity();
            draggingSlider = identity == null ? null : visuals.find(identity);
            if (draggingSlider != null && (!draggingSlider.getType().equals("Slider")
                || !visuals.bounds(draggingSlider).getInputEnabled())) draggingSlider = null;
        }
    }

    public float contentHeight(Composition composition) {
        // The root is laid out against the previous content height to keep
        // scrolling stable across a recompose. Its own height is therefore
        // not a measurement of the current content, especially after resize.
        float bottom = 0;
        for (UiNode child : composition.getRoot().getChildren()) {
            bottom = Math.max(bottom, subtreeBottom(child));
        }
        return bottom;
    }

    private static float subtreeBottom(UiNode node) {
        float bottom = node.getY() + node.getHeight();
        for (UiNode child : node.getChildren()) {
            bottom = Math.max(bottom, subtreeBottom(child));
        }
        return bottom;
    }

    public void render(Composition composition, GuiGraphics g, int mouseX, int mouseY,
                       float partialTick, float viewW, float viewH, float scrollY) {
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
        } finally {
            g.pose().popPose();
            g.disableScissor();
        }
        if (pendingTooltip != null) drawTooltip(g, mouseX, scrollY, viewW, viewH);
    }

    private void drawChildren(UiNode parent, GuiGraphics g, float scrollY, float viewW, float viewH) {
        for (UiNode child : parent.getChildren()) {
            drawNode(child, g, scrollY, viewW, viewH);
        }
    }

    private void drawNode(UiNode node, GuiGraphics g, float scrollY, float viewW, float viewH) {
        VisualBounds bounds = visuals == null ? null : visuals.bounds(node);
        if (bounds == null || bounds.getOpacity() <= 0f) return;
        float[] current = RenderSystem.getShaderColor();
        float alpha = drawColor[3] * bounds.getOpacity();
        if (current[0] == drawColor[0] && current[1] == drawColor[1]
            && current[2] == drawColor[2] && current[3] == alpha) {
            paintNode(node, g, scrollY, viewW, viewH);
            return;
        }
        float[] previous = current.clone();
        g.flush();
        RenderSystem.setShaderColor(drawColor[0], drawColor[1], drawColor[2], alpha);
        try {
            paintNode(node, g, scrollY, viewW, viewH);
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

        VisualPosition position = visualPosition(node);
        float x = position.x();
        float y = position.y();
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
        if (bg != null && border == null && !isContentPainted(node.getType())) {
            ShapeRenderer.rounded(g, x, y, w, h, radius, radius, radius, radius, bg.getColor(), notch);
        }
        if (border != null) {
            Integer bgc = bg != null ? bg.getColor() : null;
            ShapeRenderer.border(g, x, y, w, h, radius, border.getWidth(), border.getColor(), bgc, notch);
        }

        drawContent(node, g, x, y, w, h, radius, padding, scrollY);
        if (keyboardFocus && java.util.Objects.equals(focusedId, node.getIdentity()) && !InputFields.isInput(node)) {
            float focusY = y, focusH = h;
            if (node.getType().equals("RadioGroup") || node.getType().equals("MultiSelect")) {
                focusY += keyboard.option() * 18; focusH = 16;
            } else if (node.getType().equals("Accordion")) focusH = 18;
            else if (node.getType().equals("Dropdown")) {
                focusH = DropdownMetrics.HEADER_HEIGHT;
                if (bool(node, "expanded", false) && floatProp(node, "expansion", 1f) >= 1f) {
                    focusY += DropdownMetrics.HEADER_HEIGHT + DropdownMetrics.MENU_GAP + DropdownMetrics.MENU_PADDING + keyboard.option() * DropdownMetrics.ROW_HEIGHT;
                    focusH = DropdownMetrics.ROW_HEIGHT;
                }
            }
            ShapeRenderer.outline(g, x, focusY, w, focusH, 4, 1.2f, 2, 0xFF8AE4D4, null);
        }
        if (node.getType().equals("Accordion") && !bool(node, "expanded", false)) return;
        drawChildren(node, g, scrollY, viewW, viewH);
        if (node.getType().equals("Tooltip")) {
            String tip = str(node, "tip", "");
            if (visuals.bounds(node).getInputEnabled() && !tip.isEmpty() && hoverContentX >= x && hoverContentX <= x + w && hoverContentY >= y && hoverContentY <= y + h) {
                pendingTooltip = new TooltipAnchor(tip, y, y + h);
            }
        }
    }

    private void drawTooltip(GuiGraphics g, int mouseX, float scrollY, float viewW, float viewH) {
        var lines = font.split(net.minecraft.network.chat.Component.literal(pendingTooltip.text()), Math.max(1, (int) viewW - 28));
        int width = lines.stream().mapToInt(font::width).max().orElse(0) + 12;
        int height = lines.size() * font.lineHeight + 12;
        float x = Math.max(4, Math.min(mouseX + 10, viewW - width - 4));
        float y = pendingTooltip.top() - scrollY - height - 6;
        if (y < 4) y = pendingTooltip.bottom() - scrollY + 6;
        y = Math.max(4, Math.min(y, viewH - height - 4));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        try {
            ShapeRenderer.border(g, x, y, width, height, 4, 1, 0xFF465568, 0xFF111B28, null);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), (int) x + 6, (int) y + 6 + i * font.lineHeight, 0xFFFFFFFF, false);
            }
            g.flush();
        } finally { g.pose().popPose(); }
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
        switch (node.getType()) {
            case "TextField", "TextArea", "NumberField", "DateField" -> inputs.draw(node, g, hoverContentX, hoverContentY, scrollY);
            case "Icon" -> {
                if (node.getProps().get("icon") instanceof opus.core.PixelIcon icon)
                    PixelIconRenderer.draw(g, icon, x + w / 2f, y + h / 2f,
                        intProp(node, "color", node.getTheme().getColors().getOnSurface()), opus.core.PixelIcon.scaleForSize(w, h));
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
                int textColor = intProp(node, "color", 0xFFFFFFFF);
                if (Fonts.isCustom(fontId)
                    && Fonts.draw(g, fontId, line, textX, textY, textSize, textColor)) {
                    break;
                }
                g.pose().pushPose();
                g.pose().translate(textX, textY, 0);
                g.pose().scale(scale, scale, 1f);
                if (fontId != null && !Fonts.isCustom(fontId)) {
                    try {
                        var style = net.minecraft.network.chat.Style.EMPTY
                            .withFont(ResourceLocation.parse(fontId));
                        g.drawString(font, net.minecraft.network.chat.Component.literal(line).withStyle(style),
                            0, 0, textColor, false);
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
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                boolean hovered = enabled && visuals.bounds(node).getInputEnabled() && hoverContentX >= x && hoverContentX <= x + w && hoverContentY >= y && hoverContentY <= y + h;
                float hover = node.getIdentity() == null ? (hovered ? 1f : 0f)
                    : hoverFeedback.computeIfAbsent(node.getIdentity(), ignored -> new HoverFeedback())
                        .sample(hovered, frameNanos, durationScale);
                int bgc = brighten(accent, Math.round(18f * hover));
                ShapeRenderer.rounded(g, x, y, w, Math.max(h, 24f), radius, radius, radius, radius, bgc);
                if (node.getProps().get("arrowDirection") instanceof opus.core.ArrowDirection direction) {
                    float cx = x + w / 2f, cy = y + h / 2f;
                    int color = enabled ? node.getTheme().getColors().getOnSurface() : 0xFF89939F;
                    PixelIconRenderer.draw(g, direction == opus.core.ArrowDirection.LEFT ? opus.core.PixelIcon.ARROW_LEFT : opus.core.PixelIcon.ARROW_RIGHT, cx, cy, color);
                } else if (node.getProps().get("icon") instanceof opus.core.PixelIcon icon) {
                    PixelIconRenderer.draw(g, icon, x + w / 2f, y + h / 2f,
                        enabled ? node.getTheme().getColors().getOnSurface() : 0xFF89939F);
                } else {
                    g.drawCenteredString(font, str(node, "text", ""), (int) (x + w / 2), (int) (y + h / 2 - 4 + opus.core.InputMetrics.TEXT_OFFSET_Y),
                        enabled ? node.getTheme().getColors().getOnSurface() : 0xFF9A9AA5);
                }
            }
            case "Checkbox" -> {
                boolean checked = bool(node, "checked", false);
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                ShapeRenderer.rounded(g, x, y, 14f, 14f, 4f, 4f, 4f, 4f, checked ? accent : node.getTheme().getColors().getSurface());
                if (checked) {
                    drawCheck(g, x, y);
                }
                String label = str(node, "label", null);
                if (label != null) g.drawString(font, label, (int) x + 18, (int) y + 3, 0xFFFFFFFF, false);
            }
            case "RadioButton" -> {
                boolean selected = bool(node, "selected", false);
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                ShapeRenderer.rounded(g, x, y, 14f, 14f, 7f, 7f, 7f, 7f, node.getTheme().getColors().getSurface());
                if (selected) ShapeRenderer.rounded(g, x + 4, y + 4, 6f, 6f, 3f, 3f, 3f, 3f, accent);
                String label = str(node, "label", null);
                if (label != null) g.drawString(font, label, (int) x + 18, (int) y + 3, 0xFFFFFFFF, false);
            }
            case "RadioGroup" -> {
                            List<String> options = node.getOptions();
                int sel = intProp(node, "selectedIndex", 0);
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        float iy = y + i * 18;
                        boolean s = i == sel;
                        ShapeRenderer.rounded(g, x, iy, 14f, 14f, 7f, 7f, 7f, 7f, node.getTheme().getColors().getSurface());
                        if (s) ShapeRenderer.rounded(g, x + 4, iy + 4, 6f, 6f, 3f, 3f, 3f, 3f, accent);
                        g.drawString(font, options.get(i), (int) x + 18, (int) iy + 3, 0xFFFFFFFF, false);
                    }
                }
            }
            case "Switch" -> {
                boolean checked = bool(node, "checked", false);
                float tw = Math.max(w, 34f);
                float th = 18f;
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                int trackColor = intProp(node, "trackColor", checked ? accent : 0xFF3A3A44);
                ShapeRenderer.rounded(g, x, y, tw, th, 9f, 9f, 9f, 9f, trackColor);
                float position = Math.max(0f, Math.min(1f, floatProp(node, "position", checked ? 1f : 0f)));
                float tx = x + 2f + (tw - 18f) * position;
                ShapeRenderer.rounded(g, tx, y + 2, 14f, 14f, 7f, 7f, 7f, 7f, 0xFFFFFFFF);
            }
            case "Slider" -> {
                float min = floatProp(node, "min", 0f);
                float max = floatProp(node, "max", 1f);
                float v = (floatProp(node, "value", min) - min) / Math.max(1e-6f, max - min);
                v = Math.min(1f, Math.max(0f, v));
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                float th = 6f;
                float cy = y + h / 2;
                ShapeRenderer.rounded(g, x, cy - th / 2, w, th, 3f, 3f, 3f, 3f, 0xFF3A3A44);
                ShapeRenderer.rounded(g, x, cy - th / 2, w * v, th, 3f, 3f, 3f, 3f, accent);
                ShapeRenderer.rounded(g, x + w * v - 7, cy - 7, 14f, 14f, 7f, 7f, 7f, 7f, 0xFFFFFFFF);
            }
            case "ProgressBar" -> {
                float p = floatProp(node, "progress", 0f);
                int accent = intProp(node, "accent", node.getTheme().getColors().getAccent());
                ShapeRenderer.rounded(g, x, y, w, Math.max(h, 8f), 4f, 4f, 4f, 4f, node.getTheme().getColors().getSurface());
                ShapeRenderer.rounded(g, x, y, w * p, Math.max(h, 8f), 4f, 4f, 4f, 4f, accent);
            }
            case "Image" -> {
                String id = str(node, "textureId", "");
                if (!id.isEmpty()) {
                    try {
                        ResourceLocation rl = ResourceLocation.parse(id);
                        g.blit(rl, (int) x, (int) y, 0, 0, (int) w, (int) h, (int) w, (int) h);
                    } catch (Exception ignored) {
                        g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF5A2A2A);
                    }
                }
            }
            case "ItemSlot" -> {
                ShapeRenderer.rounded(g, x, y, Math.max(w, 20f), Math.max(h, 20f), 4f, 4f, 4f, 4f, node.getTheme().getColors().getSurface());
                String itemId = str(node, "itemId", "minecraft:stone");
                int count = intProp(node, "count", 1);
                try {
                    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    ItemStack stack = new ItemStack(item, count);
                    g.renderItem(stack, (int) x + 2, (int) y + 2);
                    if (count > 1) {
                        String label = String.valueOf(count);
                        int right = (int) (x + Math.max(w, 20f) - 2);
                        int bottom = (int) (y + Math.max(h, 20f) - 2);
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
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
                float actionWidth = Math.min(60f, w / 3f);
                ShapeRenderer.rounded(g, x, y, actionWidth, h, 4f, 4f, 4f, 4f, 0xFF263444);
                ShapeRenderer.rounded(g, x + w - actionWidth, y, actionWidth, h, 4f, 4f, 4f, 4f, 0xFF263444);
                int labelY = (int) (y + h / 2f - 4f);
                int labelWidth = Math.max(0, (int) actionWidth - 8);
                String previous = font.plainSubstrByWidth(str(node, "previousLabel", "< Prev"), labelWidth);
                String next = font.plainSubstrByWidth(str(node, "nextLabel", "Next >"), labelWidth);
                g.drawCenteredString(font, previous, (int) (x + actionWidth / 2f), labelY, page > 0 ? node.getTheme().getColors().getOnSurface() : 0xFF6F7D8D);
                g.drawCenteredString(font, (page + 1) + " / " + count, (int) (x + w / 2), labelY, 0xFF98A8BB);
                g.drawCenteredString(font, next, (int) (x + w - actionWidth / 2f), labelY, page < count - 1 ? node.getTheme().getColors().getOnSurface() : 0xFF6F7D8D);
            }
            case "Accordion" -> {
                PixelIconRenderer.draw(g, bool(node, "expanded", false) ? opus.core.PixelIcon.CHEVRON_DOWN : opus.core.PixelIcon.CHEVRON_RIGHT,
                    x + 5, y + 7, node.getTheme().getColors().getOnSurface());
                g.drawString(font, str(node, "title", ""), (int) x + 16, (int) y + 2, node.getTheme().getColors().getOnSurface(), false);
            }
            case "Dropdown" -> {
                            List<String> options = node.getOptions();
                int sel = intProp(node, "selectedIndex", 0);
                boolean expanded = bool(node, "expanded", false);
                float progress = floatProp(node, "expansion", expanded ? 1f : 0f);
                float header = DropdownMetrics.HEADER_HEIGHT;
                ShapeRenderer.border(g, x, y, w, header, 6f, 1f,
                    expanded ? 0xFF5B948F : 0xFF3B4858, node.getTheme().getColors().getSurface(), null);
                String current = options != null && sel >= 0 && sel < options.size() ? options.get(sel) : "";
                g.drawString(font, font.plainSubstrByWidth(current, Math.max(0, (int) w - 32)),
                    (int) x + 9, (int) (y + (header - font.lineHeight) / 2), node.getTheme().getColors().getOnSurface(), false);
                float arrowY = y + header / 2;
                PixelIconRenderer.draw(g, expanded ? opus.core.PixelIcon.CHEVRON_UP : opus.core.PixelIcon.CHEVRON_DOWN,
                    x + w - 14, arrowY, 0xFFB5C3D1);
                if (progress > 0 && options != null) {
                    float menuY = y + header + DropdownMetrics.MENU_GAP;
                    float menuH = DropdownMetrics.menuHeight(options.size());
                    float visibleH = Math.min(menuH, Math.max(0, h - header - DropdownMetrics.MENU_GAP));
                    if (visibleH <= 0) break;
                    g.enableScissor((int) x, (int) Math.floor(menuY - scrollY), (int) Math.ceil(x + w), (int) Math.ceil(menuY + visibleH - scrollY));
                    try {
                        ShapeRenderer.border(g, x, menuY, w, menuH, 6f, 1f, 0xFF3B4858, node.getTheme().getColors().getSurface(), null);
                        for (int i = 0; i < options.size(); i++) {
                            float iy = menuY + DropdownMetrics.MENU_PADDING + i * DropdownMetrics.ROW_HEIGHT;
                            boolean hovered = expanded && progress >= 1 && visuals.bounds(node).getInputEnabled()
                                && hoverContentX >= x + 4 && hoverContentX < x + w - 4
                                && DropdownMetrics.optionAt((float) (hoverContentY - y), options.size()) == i;
                            if (hovered || i == sel) ShapeRenderer.rounded(g, x + 4, iy, w - 8, DropdownMetrics.ROW_HEIGHT - 1,
                                4, 4, 4, 4, hovered ? 0xFF354C5B : 0xFF254B49);
                            String label = font.plainSubstrByWidth(options.get(i), Math.max(0, (int) w - 36));
                            g.drawString(font, label, (int) x + 10, (int) (iy + (DropdownMetrics.ROW_HEIGHT - font.lineHeight) / 2),
                                i == sel ? 0xFF9BE7D7 : 0xFFD3DEE9, false);
                            if (i == sel) drawCheck(g, x + w - 24, iy + 3);
                        }
                    } finally { g.disableScissor(); }
                }
            }
            case "MultiSelect" -> {
                            List<String> options = node.getOptions();
                            java.util.Set<Integer> selected = node.getSelectedOptions();
                if (options != null) {
                    for (int i = 0; i < options.size(); i++) {
                        float iy = y + i * 18;
                        boolean s = selected != null && selected.contains(i);
                        ShapeRenderer.rounded(g, x, iy, 14f, 14f, 4f, 4f, 4f, 4f, s ? intProp(node, "accent", node.getTheme().getColors().getAccent()) : node.getTheme().getColors().getSurface());
                        if (s) drawCheck(g, x, iy);
                        g.drawString(font, options.get(i), (int) x + 18, (int) iy + 3, 0xFFFFFFFF, false);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void drawCheck(GuiGraphics g, float x, float y) {
        PixelIconRenderer.draw(g, opus.core.PixelIcon.CHECK, x + 7, y + 7, 0xFFFFFFFF);
    }

    // ---- Input ----

    public boolean handleClick(Composition composition, double mouseX, double mouseY, float scrollY, int button) {
        if (button != 0) return false;
        ensureLayout(composition);
        double contentY = mouseY + scrollY;
        if (visuals == null) return false;
        List<UiNode> hitPath = visuals.hitPath((float) mouseX, (float) contentY);
        if (dismissDropdowns(composition, hitPath)) return true;
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

    private void setFocus(UiNode node, boolean fromKeyboard) {
        String next = node == null ? null : node.getIdentity();
        if (!java.util.Objects.equals(next, focusedId) && node != null) keyboard.focus(node);
        focusedId = next;
        keyboardFocus = fromKeyboard;
        inputs.focus(node != null && InputFields.isInput(node) ? next : null);
    }

    public boolean handleKey(Composition composition, int key, int scan, int modifiers) {
        ensureLayout(composition);
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            inputs.dismissPicker();
            dismissDropdowns(composition);
            ensureLayout(composition);
            setFocus(opus.core.FocusNavigation.next(focusTargets, focusedId, net.minecraft.client.gui.screens.Screen.hasShiftDown()), true);
            return focusedId != null;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && inputs.dismissPicker()) return true;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && focusedId != null) { setFocus(null, false); return true; }
        if (inputs.hasFocus()) return inputs.key(key, scan, modifiers);
        UiNode node = focusedId == null || visuals == null ? null : visuals.find(focusedId);
        if (node == null) return false;
        keyboardFocus = true;
        return keyboard.key(node, key, net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    public boolean handleCharacter(Composition composition, char character, int modifiers) {
        ensureLayout(composition);
        return inputs.character(character, modifiers);
    }

    /** Keep a keyboard-focused control visible without jumping on ordinary mouse clicks. */
    public float revealFocus(Composition composition, float scroll, float viewportHeight) {
        ensureLayout(composition);
        UiNode node = focusedId == null || visuals == null ? null : visuals.find(focusedId);
        if (node == null) return scroll;
        float top = visuals.bounds(node).getY(), bottom = top + node.getHeight();
        if (bottom > scroll + viewportHeight - 8) scroll = bottom - viewportHeight + 8;
        if (top < scroll + 8) scroll = top - 8;
        return Math.max(0, Math.min(Math.max(0, contentHeight(composition) - viewportHeight), scroll));
    }

    public boolean handleInputScroll(Composition composition, double x, double y, float scroll, double dx, double dy) {
        ensureLayout(composition);
        return inputs.scroll(x, y + scroll, dx, dy);
    }

    /** Escape closes open menus before the host closes the screen. */
    public boolean dismissDropdowns(Composition composition) {
        ensureLayout(composition);
        return dismissDropdowns(composition, List.of());
    }

    private boolean dismissDropdowns(Composition composition, List<UiNode> keepOpen) {
        if (visuals == null) return false;
        // Collect first: callbacks may synchronously replace the composition tree.
        List<java.util.function.Consumer<Boolean>> callbacks = new java.util.ArrayList<>();
        for (UiNode node : composition.getRoot().findAll("Dropdown")) {
            VisualBounds bounds = visuals.bounds(node);
            if (bounds != null && bounds.getInputEnabled() && bool(node, "expanded", false)
                && !keepOpen.contains(node)) {
                java.util.function.Consumer<Boolean> callback = node.getEvents().getExpandedChange();
                if (callback != null) callbacks.add(callback);
            }
        }
        callbacks.forEach(callback -> callback.accept(false));
        return !callbacks.isEmpty();
    }

    private record VisualPosition(float x, float y) {}

    private VisualPosition visualPosition(UiNode node) {
        VisualBounds bounds = visuals == null ? null : visuals.bounds(node);
        return bounds == null ? new VisualPosition(node.getX(), node.getY()) : new VisualPosition(bounds.getX(), bounds.getY());
    }

    /** Structural controls interpret clicks over their own area. */
    private boolean fireControl(UiNode node, double mouseX, double mouseY) {
        VisualPosition position = visualPosition(node);
        float x = position.x();
        float y = position.y();
        switch (node.getType()) {
            case "Checkbox", "Switch" -> {
                java.util.function.Consumer<Boolean> cb = node.getEvents().getCheckedChange();
                if (cb != null) {
                    cb.accept(!bool(node, "checked", bool(node, "value", false)));
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
                java.util.function.Consumer<Boolean> cb = node.getEvents().getExpandedChange();
                if (cb != null && mouseY < y + 18) {
                    cb.accept(!bool(node, "expanded", false));
                    return true;
                }
            }
            case "Dropdown" -> {
                List<String> options = node.getOptions();
                java.util.function.Consumer<Integer> onSelect = node.getEvents().getSelect();
                java.util.function.Consumer<Boolean> onExpanded = node.getEvents().getExpandedChange();
                if (options == null) return false;
                if (bool(node, "expanded", false)) {
                    if (mouseY < y + DropdownMetrics.HEADER_HEIGHT) {
                        if (onExpanded != null) onExpanded.accept(false);
                        return onExpanded != null;
                    }
                    if (floatProp(node, "expansion", 1f) < 1f) return true;
                    int idx = DropdownMetrics.optionAt((float) (mouseY - y), options.size());
                    if (idx >= 0 && idx < options.size() && onSelect != null) {
                        onSelect.accept(idx);
                        if (onExpanded != null) onExpanded.accept(false);
                        return true;
                    }
                    return true;
                } else if (onExpanded != null && mouseY < y + DropdownMetrics.HEADER_HEIGHT) {
                    onExpanded.accept(true);
                    return true;
                }
                // Consume clicks on the remaining exit animation, without selecting a row.
                return true;
            }
            case "RadioGroup" -> {
                List<String> options = node.getOptions();
                java.util.function.Consumer<Integer> onSelect = node.getEvents().getSelect();
                if (options != null && onSelect != null) {
                    int idx = (int) Math.floor((mouseY - y) / 18);
                    if (idx >= 0 && idx < options.size()) {
                        onSelect.accept(idx);
                        return true;
                    }
                }
            }
            case "MultiSelect" -> {
                List<String> options = node.getOptions();
                java.util.Set<Integer> selected = node.getSelectedOptions();
                java.util.function.Consumer<java.util.Set<Integer>> onChange =
                    node.getEvents().getSelectionChange();
                if (options != null && onChange != null) {
                    int idx = (int) Math.floor((mouseY - y) / 18);
                    if (idx >= 0 && idx < options.size()) {
                        java.util.Set<Integer> next = new java.util.HashSet<>(selected != null ? selected : java.util.Set.of());
                        if (!next.remove(idx)) next.add(idx);
                        onChange.accept(next);
                        return true;
                    }
                }
            }
            case "Pagination" -> {
                java.util.function.Consumer<Integer> cb = node.getEvents().getPageChange();
                int page = intProp(node, "page", 0);
                int count = intProp(node, "pageCount", 1);
                if (cb != null) {
                    double actionWidth = Math.min(60.0, node.getWidth() / 3.0);
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

    /** Keep swipe navigation from moving away from an open menu or popup. */
    public boolean blocksSwipeNavigation(Composition composition) {
        ensureLayout(composition);
        if (inputs.hasFocus()) return true;
        return blocksSwipeNavigation(composition.getRoot());
    }

    private boolean blocksSwipeNavigation(UiNode node) {
        VisualBounds bounds = visuals == null ? null : visuals.bounds(node);
        if (bounds == null || bounds.getOpacity() <= 0) return false;
        if (node.getType().equals("Popup")) return true;
        if (node.getType().equals("Dropdown") && (bool(node, "expanded", false)
            || floatProp(node, "expansion", 0f) > 0f)) return true;
        for (UiNode child : node.getChildren()) if (blocksSwipeNavigation(child)) return true;
        return false;
    }

    private void ensureLayout(Composition composition) {
        if (visuals != null && layoutVersion != composition.getCompositionVersion()) {
            layout(composition, layoutWidth, layoutHeight);
        }
    }

    private void dragSliderTo(UiNode slider, double mouseX) {
        java.util.function.Consumer<Float> cb = slider.getEvents().getFloatChange();
        if (cb == null) return;
        float min = floatProp(slider, "min", 0f);
        float max = floatProp(slider, "max", 1f);
        float frac = (float) ((mouseX - visualPosition(slider).x()) / Math.max(1, slider.getWidth()));
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
