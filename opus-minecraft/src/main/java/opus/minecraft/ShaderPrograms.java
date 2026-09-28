package opus.minecraft;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Minecraft shader definitions; loader adapters provide registration and reload ownership. */
public final class ShaderPrograms {
    private static ShaderInstance roundedShape;
    private static ShaderInstance font;

    private ShaderPrograms() {}

    /** Minecraft owns and closes each instance, including on resource reload. */
    public static void register(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> registrar) {
        try {
            registrar.accept(new ShaderInstance(resources,
                ResourceLocation.fromNamespaceAndPath("opus", "rounded_shape"),
                DefaultVertexFormat.POSITION_TEX), shader -> roundedShape = shader);
            registrar.accept(new ShaderInstance(resources,
                ResourceLocation.fromNamespaceAndPath("opus", "font_sdf"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> font = shader);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the Opus shaders", exception);
        }
    }

    static ShaderInstance roundedShape() {
        if (roundedShape == null) {
            throw new IllegalStateException("Initialize the client integration before rendering a UI");
        }
        return roundedShape;
    }

    static ShaderInstance font() {
        if (font == null) throw new IllegalStateException("Initialize the client integration before rendering custom fonts");
        return font;
    }
}
