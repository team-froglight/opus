package opus.showcase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = GalleryMod.MOD_ID, value = Dist.CLIENT)
public final class GalleryEvents {
    private static final AtomicBoolean OPEN_REQUESTED = new AtomicBoolean();

    private GalleryEvents() {
    }

    @SubscribeEvent
    public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen titleScreen)) return;

        int buttonWidth = 110;
        event.addListener(Button.builder(
                Component.translatable("button.opus_showcase.gallery"),
                button -> Minecraft.getInstance().setScreen(new GalleryScreen())
            )
            .bounds(Math.max(4, titleScreen.width - buttonWidth - 4), 4, buttonWidth, 20)
            .build());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (GalleryKeys.OPEN_GALLERY.consumeClick()) {
            OPEN_REQUESTED.set(true);
        }
        if (OPEN_REQUESTED.getAndSet(false)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null || minecraft.screen instanceof TitleScreen) {
                minecraft.setScreen(new GalleryScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("opus")
            .executes(context -> {
                OPEN_REQUESTED.set(true);
                return 1;
            }));
    }
}
