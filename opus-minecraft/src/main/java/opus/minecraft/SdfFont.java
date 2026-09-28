package opus.minecraft;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.stb.STBTruetype.*;

/** Lazily packed distance-field glyphs, with fractional placement and shared layout metrics. */
final class SdfFont implements AutoCloseable {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int SDF_PADDING = 6;
    private static final int ATLAS_SIZE = 512;

    private record GlyphImage(Atlas atlas, int x, int y, int width, int height, int offsetX, int offsetY) {}

    private static final class Atlas {
        final ResourceLocation location;
        final DynamicTexture texture;
        final int size;
        int cursorX = 1;
        int cursorY = 1;
        int rowHeight;
        boolean dirty;

        Atlas(ResourceLocation location, DynamicTexture texture, int size) {
            this.location = location;
            this.texture = texture;
            this.size = size;
        }

        boolean fits(int width, int height) {
            if (width + 2 > size || height + 2 > size) return false;
            int nextY = cursorX + width + 1 > size ? cursorY + rowHeight + 1 : cursorY;
            return nextY + height + 1 <= size;
        }

        GlyphImage insert(ByteBuffer pixels, int width, int height, int offsetX, int offsetY) {
            if (cursorX + width + 1 > size) {
                cursorX = 1;
                cursorY += rowHeight + 1;
                rowHeight = 0;
            }
            int x = cursorX, y = cursorY;
            NativeImage image = texture.getPixels();
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int distance = pixels.get(row * width + col) & 255;
                    image.setPixelRGBA(x + col, y + row, (distance << 24) | 0x00FFFFFF);
                }
            }
            cursorX += width + 1;
            rowHeight = Math.max(rowHeight, height);
            dirty = true;
            return new GlyphImage(this, x, y, width, height, offsetX, offsetY);
        }

        void upload() {
            if (!dirty) return;
            texture.upload();
            // NativeImage.upload resets sampling state, so set filtering afterwards.
            texture.setFilter(true, false);
            dirty = false;
        }
    }

    private final FontFace face;
    private final TextureManager textures;
    private final String atlasPath = "fonts/" + UUID.randomUUID();
    private final List<Atlas> atlases = new ArrayList<>();
    private final Map<Integer, GlyphImage> glyphImages = new HashMap<>();
    private boolean closed;
    private boolean failed;

    private SdfFont(TextureManager textures, FontFace face) {
        this.textures = textures;
        this.face = face;
    }

    public static SdfFont bake(Path ttf, TextureManager textures) {
        return bake(ttf.toAbsolutePath().normalize().toString(), ttf, textures);
    }

    /** Loads metrics now; glyph textures are created only when text is drawn. */
    public static SdfFont bake(String fontId, Path ttf, TextureManager textures) {
        try {
            SdfFont font = new SdfFont(textures, FontFace.load(ttf));
            LOGGER.info("Opus: loaded distance-field font {} from {}", fontId, ttf.getFileName());
            return font;
        } catch (Exception failure) {
            LOGGER.warn("Opus: cannot load font {}", ttf, failure);
            return new SdfFont(null, null);
        }
    }

    public boolean isBroken() { return face == null || closed || failed; }

    public float measure(String text, float px, float maxWidth) {
        if (isBroken() || px <= 0) return 0;
        return Math.max(0, Math.min(face.layout(text).width() * px / FontFace.ATLAS_HEIGHT, maxWidth));
    }

    private GlyphImage glyphImage(int index) {
        return glyphImages.computeIfAbsent(index, key -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var offsetX = stack.mallocInt(1);
                var offsetY = stack.mallocInt(1);
                ByteBuffer pixels = stbtt_GetGlyphSDF(face.info(), face.scale(), key, SDF_PADDING,
                    (byte) 128, 24f, width, height, offsetX, offsetY);
                // Spaces have an advance but no outline or texture.
                if (pixels == null) return new GlyphImage(null, 0, 0, 0, 0, 0, 0);
                try {
                    int w = width.get(0), h = height.get(0);
                    Atlas atlas = atlases.stream().filter(candidate -> candidate.fits(w, h)).findFirst().orElse(null);
                    if (atlas == null) atlas = createAtlas(w, h);
                    return atlas.insert(pixels, w, h, offsetX.get(0), offsetY.get(0));
                } finally {
                    stbtt_FreeSDF(pixels);
                }
            }
        });
    }

    private Atlas createAtlas(int width, int height) {
        int size = ATLAS_SIZE;
        while (size < Math.max(width, height) + 2 && size < 4096) size *= 2;
        if (Math.max(width, height) + 2 > size) throw new IllegalArgumentException("Font glyph exceeds atlas capacity");
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("opus", atlasPath + "/" + atlases.size());
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
        DynamicTexture texture = null;
        try {
            texture = new DynamicTexture(image);
            textures.register(location, texture);
            Atlas atlas = new Atlas(location, texture, size);
            atlases.add(atlas);
            return atlas;
        } catch (RuntimeException failure) {
            if (texture == null) image.close();
            else { texture.close(); texture.releaseId(); }
            throw failure;
        }
    }

    /** Draws one line; false requests the caller's vanilla fallback. */
    public boolean draw(GuiGraphics g, String text, float x, float y, float px, int argb) {
        if (isBroken()) return false;
        if (px <= 0 || (argb >>> 24) == 0) return true;
        FontFace.Line line = face.layout(text);
        if (line.glyphs().isEmpty()) return true;
        g.flush();
        ShaderInstance previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            // Prepare all glyphs before opening the shared builder or uploading textures.
            try {
                for (var glyph : line.glyphs()) glyphImage(glyph.index());
                for (Atlas atlas : atlases) atlas.upload();
            } catch (RuntimeException failure) {
                failed = true;
                LOGGER.warn("Opus: could not prepare font glyphs; using vanilla fallback", failure);
                return false;
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShader(ShaderPrograms::font);
            float scale = px / FontFace.ATLAS_HEIGHT;
            float baseline = y + face.ascent() * scale;
            Matrix4f pose = g.pose().last().pose();
            Atlas current = null;
            BufferBuilder buffer = null;
            // Preserve text order across atlas boundaries (important for overlapping marks).
            for (var glyph : line.glyphs()) {
                GlyphImage image = glyphImages.get(glyph.index());
                if (image.atlas() == null) continue;
                if (current != image.atlas()) {
                    if (buffer != null) BufferUploader.drawWithShader(buffer.buildOrThrow());
                    current = image.atlas();
                    RenderSystem.setShaderTexture(0, current.texture.getId());
                    buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                }
                float x0 = x + (glyph.x() + image.offsetX()) * scale;
                float y0 = baseline + image.offsetY() * scale;
                float x1 = x0 + image.width() * scale;
                float y1 = y0 + image.height() * scale;
                float u0 = (float) image.x() / current.size, v0 = (float) image.y() / current.size;
                float u1 = (float) (image.x() + image.width()) / current.size;
                float v1 = (float) (image.y() + image.height()) / current.size;
                vertex(buffer, pose, x0, y0, u0, v0, argb);
                vertex(buffer, pose, x0, y1, u0, v1, argb);
                vertex(buffer, pose, x1, y1, u1, v1, argb);
                vertex(buffer, pose, x1, y0, u1, v0, argb);
            }
            if (buffer != null) BufferUploader.drawWithShader(buffer.buildOrThrow());
            return true;
        } finally {
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderTexture(0, previousTexture);
            if (cullEnabled) RenderSystem.enableCull();
            if (!blendEnabled) RenderSystem.disableBlend();
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float x, float y, float u, float v, int color) {
        buffer.addVertex(pose, x, y, 0).setUv(u, v).setColor(color);
    }

    /** Releases all GPU atlases and the retained native font data on the render thread. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            for (Atlas atlas : atlases) {
                try { textures.release(atlas.location); }
                catch (RuntimeException failure) { LOGGER.warn("Opus: failed to release font atlas {}", atlas.location, failure); }
            }
        } finally {
            atlases.clear();
            glyphImages.clear();
            if (face != null) face.close();
        }
    }
}
