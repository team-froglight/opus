package opus.minecraft;

import net.minecraft.client.gui.GuiGraphics;
import opus.core.PixelIcon;

/** Nearest-neighbor geometry: no font glyphs, diagonal shaders or filtered textures. */
final class PixelIconRenderer {
    private PixelIconRenderer() {}

    static void draw(GuiGraphics graphics, PixelIcon icon, float centerX, float centerY, int color) {
        draw(graphics, icon, centerX, centerY, color, 1);
    }

    static void draw(GuiGraphics graphics, PixelIcon icon, float centerX, float centerY, int color, int scale) {
        int left = Math.round(centerX - PixelIcon.SIZE * scale / 2f);
        int top = Math.round(centerY - PixelIcon.SIZE * scale / 2f);
        for (int row = 0; row < PixelIcon.SIZE; row++) {
            int start = -1;
            for (int column = 0; column <= PixelIcon.SIZE; column++) {
                boolean filled = column < PixelIcon.SIZE && icon.pixelAt(column, row);
                if (filled && start < 0) start = column;
                if (!filled && start >= 0) {
                    graphics.fill(left + start * scale, top + row * scale, left + column * scale, top + (row + 1) * scale, color);
                    start = -1;
                }
            }
        }
    }
}
