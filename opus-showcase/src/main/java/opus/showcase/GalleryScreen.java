package opus.showcase;

import opus.minecraft.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** An opaque canvas with a scroll indicator and a return destination. */
public final class GalleryScreen extends UiScreen {
    private final Screen parent;

    public GalleryScreen() {
        super(Gallery.content());
        parent = Minecraft.getInstance().screen;
        setReducedMotion(Gallery.reducedMotion());
        setOnSwipe(Gallery::swipeTab);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Gallery.BACKGROUND);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (contentHeight() > height) {
            int trackHeight = Math.max(1, height - 16);
            int thumbHeight = Math.min(trackHeight, Math.max(20, (int) (trackHeight * height / contentHeight())));
            int top = 8 + (int) ((trackHeight - thumbHeight) * scrollOffset() / (contentHeight() - height));
            graphics.fill(width - 4, 8, width - 2, height - 8, 0xFF1D2A39);
            graphics.fill(width - 4, top, width - 2, top + thumbHeight, 0xFF617589);
        }
    }

    @Override
    protected boolean isSwipeAvailable(opus.core.SwipeDirection direction) { return Gallery.canSwipeTab(direction); }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
