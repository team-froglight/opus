package opus.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import opus.core.PixelIcon;

/** Nearest-neighbor geometry: no font glyphs, diagonal shaders or filtered textures. */
final class PixelIconRenderer {
    private PixelIconRenderer() {}

    static void draw(GuiGraphics graphics, PixelIcon icon, float centerX, float centerY, int color) {
        draw(graphics, icon, centerX, centerY, color, 1);
    }

    /**
     * Snaps the icon's corner to a screen pixel rather than a GUI unit. Every cell still covers whole
     * screen pixels, so it stays crisp, but at GUI scale 2 an icon can share a half-unit center with
     * the label beside it.
     */
    static void draw(GuiGraphics graphics, PixelIcon icon, float centerX, float centerY, int color, int scale) {
        float pixels = screenPixelsPerUnit();
        float left = Math.round((centerX - PixelIcon.SIZE * scale / 2f) * pixels) / pixels;
        float top = Math.round((centerY - PixelIcon.SIZE * scale / 2f) * pixels) / pixels;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        try {
            for (int row = 0; row < PixelIcon.SIZE; row++) {
                int start = -1;
                for (int column = 0; column <= PixelIcon.SIZE; column++) {
                    boolean filled = column < PixelIcon.SIZE && icon.pixelAt(column, row);
                    if (filled && start < 0) start = column;
                    if (!filled && start >= 0) {
                        graphics.fill(start * scale, row * scale, column * scale, (row + 1) * scale, color);
                        start = -1;
                    }
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static float screenPixelsPerUnit() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 1f : (float) Math.max(1, minecraft.getWindow().getGuiScale());
    }
}
