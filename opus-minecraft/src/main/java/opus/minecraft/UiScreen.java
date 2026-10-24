package opus.minecraft;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import opus.core.Compositions;
import opus.core.Colors;
import opus.core.Composition;
import opus.core.ScrollGestures;
import opus.core.ScrollGestureUpdate;
import opus.core.SwipeDirection;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Hosts an Opus composition inside a vanilla Screen.
 * Recomposition happens via state; layout is Yoga-cached; nodes outside the viewport skip drawing.
 */
public class UiScreen extends Screen {
    private static int activeScreens;

    private final Function0<Unit> content;
    private Composition composition;
    private UiRenderer renderer;
    private boolean active;
    private float scrollY;
    private float lastContentHeight;
    private boolean reducedMotion;
    private final ScrollGestures scrollGestures = new ScrollGestures();
    private final GestureHint gestureHint = new GestureHint();
    private Consumer<SwipeDirection> onSwipe;
    private WindowsTouchpad touchpad;
    private boolean touchpadAttempted;

    /** Opt into horizontal two-finger navigation. Null disables navigation; vertical scrolling remains available. */
    public void setOnSwipe(Consumer<SwipeDirection> onSwipe) {
        this.onSwipe = onSwipe;
        scrollGestures.reset();
        gestureHint.clear();
        if (onSwipe == null) closeTouchpad();
        else if (renderer != null) installTouchpad();
    }

    /** Whether this host can observe actual touchpad finger release. */
    public boolean isTouchpadNavigationSupported() { return touchpad != null; }

    private void installTouchpad() {
        if (onSwipe == null || touchpadAttempted) return;
        touchpadAttempted = true;
        touchpad = WindowsTouchpad.install(Minecraft.getInstance().getWindow().getWindow(), new WindowsTouchpad.Listener() {
            @Override public void begin() {
                scrollGestures.reset();
                gestureHint.clear();
            }
            @Override public void release() {
                if (!canSwipeNavigate()) scrollGestures.suppressSwipe();
                var gesture = scrollGestures.release();
                if (gesture.getSwipe() == null) gestureHint.dismiss(System.nanoTime());
                else completeSwipe(gesture, System.nanoTime());
            }
            @Override public void cancel() {
                scrollGestures.reset();
                gestureHint.clear();
            }
        });
    }

    private void closeTouchpad() {
        if (touchpad != null) touchpad.close();
        touchpad = null;
        touchpadAttempted = false;
    }

    /** Override when navigation has boundaries. Unavailable directions show a muted edge hint. */
    protected boolean isSwipeAvailable(SwipeDirection direction) { return true; }

    /** Disables transitions for this screen; target values remain fully interactive. */
    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        if (composition != null) composition.setAnimationDurationScale(reducedMotion ? 0f : 1f);
    }

    public boolean isReducedMotion() { return reducedMotion; }

    public UiScreen(Function0<Unit> content) {
        super(Component.literal("Opus"));
        this.content = content;
    }

    /** Java subclasses override composeContent; no callback holder or Kotlin Unit bridge is needed. */
    protected UiScreen() {
        super(Component.literal("Opus"));
        this.content = () -> { composeContent(); return Unit.INSTANCE; };
    }

    /** Called after construction by init/recomposition, never from the constructor. */
    protected void composeContent() {}

    /** Java entry point: no Kotlin Unit or Function0 required. */
    public static UiScreen create(Runnable content) {
        return new UiScreen(() -> { content.run(); return Unit.INSTANCE; });
    }

    /** Replaces the current screen. Call on the client thread. */
    public static void open(Runnable content) {
        Minecraft.getInstance().setScreen(create(content));
    }

    /** Kotlin counterpart of {@link #open(Runnable)}. Call on the client thread; this does not schedule onto it. */
    public static void show(Function0<Unit> content) {
        Minecraft.getInstance().setScreen(new UiScreen(content));
    }

    public void scrollToTop() {
        scrollY = 0f;
    }

    protected float scrollOffset() {
        return scrollY;
    }

    protected float contentHeight() {
        return lastContentHeight;
    }

    @Override
    protected void init() {
        scrollGestures.reset();
        gestureHint.clear();
        if (composition == null) composition = Compositions.setContent(content);
        composition.setAnimationDurationScale(reducedMotion ? 0f : 1f);
        if (renderer == null) renderer = new UiRenderer(font, new FontMeasurer(font));
        installTouchpad();
    }

    @Override
    public void added() {
        super.added();
        if (!active) {
            active = true;
            activeScreens++;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (renderer == null || composition == null) return;
        long now = System.nanoTime();
        if (scrollGestures.isActive() && !canSwipeNavigate()) {
            scrollGestures.suppressSwipe();
            gestureHint.clear();
        }
        // No timer can end a held gesture. Keep its hint visible even when the fingers stop moving.
        updateSwipePreview(now);
        renderer.update(composition, mouseX, mouseY, scrollY);
        composition.advanceAnimations(now);
        // Lay out FIRST: right after a recompose the tree is fresh (all zeros)
        // and measuring content height before layout collapses maxScroll to 0,
        // which used to yank every click back to the top.
        renderer.layout(composition, width, Math.max(height, lastContentHeight));
        lastContentHeight = renderer.contentHeight(composition);
        float maxScroll = Math.max(0, lastContentHeight - height);
        scrollY = Math.min(Math.max(scrollY, 0), maxScroll);
        renderer.draw(composition, graphics, mouseX, mouseY, partialTick, width, height, scrollY);
        drawScrollbar(graphics, mouseX, mouseY);
        GestureHintRenderer.draw(graphics, gestureHint.sample(System.nanoTime(), reducedMotion), width, height, reducedMotion);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        scrollGestures.suppressSwipe();
        gestureHint.clear();
        if (button == 0 && beginScrollbarDrag(mouseX, mouseY)) return true;
        if (renderer != null && composition != null
            && renderer.handleClick(composition, mouseX, mouseY, scrollY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        scrollGestures.suppressSwipe();
        gestureHint.clear();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && renderer != null && composition != null
            && renderer.dismissOverlays(composition)) return true;
        if (renderer != null && composition != null && renderer.handleKey(composition, keyCode, scanCode, modifiers)) {
            scrollY = renderer.revealFocus(composition, scrollY, height);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return renderer != null && composition != null && renderer.handleCharacter(composition, character, modifiers)
            || super.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollThumbTo(mouseY - scrollbarGrab);
            return true;
        }
        if (renderer != null && composition != null && renderer.handleDrag(composition, mouseX, mouseY, scrollY, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        if (renderer != null) renderer.handleRelease();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (renderer != null && composition != null && renderer.handleInputScroll(composition, mouseX, mouseY, this.scrollY, scrollX, scrollY)) return true;
        boolean dragging = renderer != null && renderer.isDraggingControl();
        boolean touching = touchpad != null && touchpad.touching();
        boolean canSwipe = touching && canSwipeNavigate();
        long now = System.nanoTime();
        // Mouse wheels and post-release inertia keep scrolling, but cannot arm another swipe.
        var gesture = touching ? scrollGestures.accept(scrollX, scrollY, now, canSwipe)
            : new ScrollGestureUpdate(scrollY, null);
        if (touching && !canSwipe) gestureHint.clear();
        if (dragging) return true;
        float maxScroll = Math.max(0, lastContentHeight - height);
        this.scrollY = (float) Math.max(0, Math.min(maxScroll, this.scrollY - gesture.getVerticalScroll() * 12.0));
        if (touching) updateSwipePreview(now);
        return true;
    }

    private void updateSwipePreview(long now) {
        if (touchpad == null || !touchpad.touching()) return;
        if (canSwipeNavigate() && scrollGestures.getPreview() != null) {
            var preview = scrollGestures.getPreview();
            gestureHint.update(preview.getDirection(), preview.getProgress(), false,
                isSwipeAvailable(preview.getDirection()), now);
        } else gestureHint.clear();
    }

    private boolean canSwipeNavigate() {
        return onSwipe != null && composition != null && renderer != null && !draggingScrollbar
            && !renderer.isDraggingControl() && !renderer.blocksSwipeNavigation(composition);
    }

    private void completeSwipe(ScrollGestureUpdate gesture, long now) {
        if (gesture.getSwipe() == null || onSwipe == null) return;
        boolean available = isSwipeAvailable(gesture.getSwipe());
        gestureHint.update(gesture.getSwipe(), 1f, true, available, now);
        if (available) onSwipe.accept(gesture.getSwipe());
    }

    // ---- Scrollbar ----

    private static final int SCROLLBAR_INSET = 8, SCROLLBAR_HIT_WIDTH = 8;
    private boolean draggingScrollbar;
    private double scrollbarGrab;

    /** Override to hide the built-in scrollbar, for example when drawing your own. */
    protected boolean showsScrollbar() { return true; }

    private float maxScroll() { return Math.max(0, lastContentHeight - height); }
    private float trackHeight() { return Math.max(1, height - SCROLLBAR_INSET * 2); }
    private float thumbHeight() { return Math.min(trackHeight(), Math.max(20, trackHeight() * height / lastContentHeight)); }
    private float thumbTop() {
        float max = maxScroll();
        return SCROLLBAR_INSET + (max <= 0 ? 0 : (trackHeight() - thumbHeight()) * scrollY / max);
    }
    private boolean hasScrollbar() { return showsScrollbar() && maxScroll() > 0; }
    private boolean overScrollbar(double x, double y) {
        return hasScrollbar() && x >= width - SCROLLBAR_HIT_WIDTH && y >= SCROLLBAR_INSET && y <= SCROLLBAR_INSET + trackHeight();
    }

    /** Grabs the thumb where it was pressed; a press on the track first centers the thumb there. */
    private boolean beginScrollbarDrag(double x, double y) {
        if (!overScrollbar(x, y)) return false;
        float top = thumbTop();
        scrollbarGrab = y >= top && y <= top + thumbHeight() ? y - top : thumbHeight() / 2f;
        draggingScrollbar = true;
        scrollThumbTo(y - scrollbarGrab);
        return true;
    }

    private void scrollThumbTo(double top) {
        float travel = trackHeight() - thumbHeight();
        float fraction = travel <= 0 ? 0 : (float) ((top - SCROLLBAR_INSET) / travel);
        scrollY = Math.clamp(fraction, 0f, 1f) * maxScroll();
    }

    /** Colors follow the theme of the screen's outermost content. */
    private void drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hasScrollbar()) return;
        var children = composition.getRoot().getChildren();
        Colors colors = children.isEmpty() ? new Colors() : children.getFirst().getTheme().getColors();
        boolean active = draggingScrollbar || overScrollbar(mouseX, mouseY);
        int thumbWidth = active ? 4 : 2;
        int top = Math.round(thumbTop());
        graphics.fill(width - 4, SCROLLBAR_INSET, width - 2, SCROLLBAR_INSET + (int) trackHeight(), colors.getSurface());
        graphics.fill(width - 2 - thumbWidth, top, width - 2, top + Math.round(thumbHeight()),
            active ? colors.getMuted() : (colors.getMuted() & 0xFFFFFF) | 0x99000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        closeTouchpad();
        scrollGestures.reset();
        gestureHint.clear();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (composition != null) {
            composition.close();
            composition = null;
        }
        if (active) {
            active = false;
            if (--activeScreens == 0) Fonts.clearCache();
        }
        super.removed();
    }
}
