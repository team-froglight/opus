package opus.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import opus.minecraft.ShaderPrograms;

/** Client-only NeoForge setup. Call once from the client mod constructor. */
public final class UiClient {
    private UiClient() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(UiClient::registerShaders);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        ShaderPrograms.register(event.getResourceProvider(), event::registerShader);
    }
}
