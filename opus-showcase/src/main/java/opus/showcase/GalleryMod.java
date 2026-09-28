package opus.showcase;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.slf4j.Logger;
import net.neoforged.api.distmarker.Dist;
import opus.neoforge.UiClient;

@Mod(value = GalleryMod.MOD_ID, dist = Dist.CLIENT)
public class GalleryMod {
    public static final String MOD_ID = "opus_showcase";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GalleryMod(IEventBus modBus) {
        LOGGER.info("Opus Showcase starting");
        UiClient.initialize(modBus);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(GalleryKeys.OPEN_GALLERY));
    }
}
