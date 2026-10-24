package opus.showcase;

import opus.minecraft.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** An opaque canvas with a return destination; UiScreen supplies the scrollbar. */
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
    }

    @Override
    protected boolean isSwipeAvailable(opus.core.SwipeDirection direction) { return Gallery.canSwipeTab(direction); }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
