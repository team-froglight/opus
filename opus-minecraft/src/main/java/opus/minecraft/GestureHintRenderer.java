package opus.minecraft;

import net.minecraft.client.gui.GuiGraphics;
import opus.core.SwipeDirection;

final class GestureHintRenderer {
    private GestureHintRenderer() {}

    static void draw(GuiGraphics graphics, GestureHint.Frame hint, int width, int height, boolean reducedMotion) {
        if (hint == null) return;
        boolean previous = hint.direction() == SwipeDirection.RIGHT;
        float progress = hint.available() ? hint.progress() : hint.progress() * 0.45f;
        float inset = reducedMotion ? 28f : (-8f + 64f * progress) * hint.opacity();
        float cx = previous ? inset : width - inset;
        float cy = height / 2f;
        float sign = previous ? -1f : 1f;
        int fill = hint.progress() >= 1f && hint.available() ? 0xFF286F66 : 0xFF1D2A39;
        int edge = hint.available() ? 0xFF5DD2BD : 0xFF617589;
        int arrow = hint.available() ? 0xFFEAF0F7 : 0xFF98A8BB;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);
        graphics.enableScissor(0, 0, width, height);
        try {
            ShapeRenderer.border(graphics, cx - 15, cy - 15, 30, 30, 15, 1,
                alpha(edge, hint.opacity()), alpha(fill, hint.opacity()), null);
            int color = alpha(arrow, hint.opacity());
            ShapeRenderer.line(graphics, cx - sign * 5, cy, cx + sign * 5, cy, 1.8f, color);
            ShapeRenderer.line(graphics, cx + sign, cy - 4, cx + sign * 5, cy, 1.8f, color);
            ShapeRenderer.line(graphics, cx + sign * 5, cy, cx + sign, cy + 4, 1.8f, color);
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    private static int alpha(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }
}
