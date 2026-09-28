package opus.showcase;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class GalleryKeys {
    public static final KeyMapping OPEN_GALLERY = new KeyMapping(
        "key.opus_showcase.open",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        "key.categories.opus_showcase"
    );

    private GalleryKeys() {
    }
}
