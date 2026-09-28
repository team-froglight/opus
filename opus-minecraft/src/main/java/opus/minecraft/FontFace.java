package opus.minecraft;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTKerningentry;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.*;

/** Font metrics and single-line positioning, independent of the graphics context.
 * Uses kerning and NFC normalization; does not perform complex-script shaping.
 */
final class FontFace implements AutoCloseable {
    static final float ATLAS_HEIGHT = 64f;
    record Glyph(int index, float advance) {}
    record PositionedGlyph(int index, float x) {}
    record Line(List<PositionedGlyph> glyphs, float width) {}

    private final ByteBuffer data;
    private final STBTTFontinfo info;
    private final float scale;
    private final float ascent;
    private final int replacement;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Integer> legacyKerning = new HashMap<>();
    private final Map<String, Line> lines = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Line> entry) { return size() > 128; }
    };
    private boolean closed;

    private FontFace(ByteBuffer data, STBTTFontinfo info) {
        this.data = data;
        this.info = info;
        scale = stbtt_ScaleForPixelHeight(info, ATLAS_HEIGHT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var value = stack.mallocInt(1);
            stbtt_GetFontVMetrics(info, value, null, null);
            ascent = value.get(0) * scale;
        }
        int fallback = stbtt_FindGlyphIndex(info, 0xFFFD);
        replacement = fallback != 0 ? fallback : stbtt_FindGlyphIndex(info, '?');
        // STB prefers GPOS whenever it exists, even when its lookup format is unsupported.
        // Retain the font's legacy pairs as a fallback in that case (e.g. Windows Arial).
        int count = stbtt_GetKerningTableLength(info);
        if (count > 0) {
            try (var pairs = STBTTKerningentry.malloc(count)) {
                int read = stbtt_GetKerningTable(info, pairs);
                for (int i = 0; i < read; i++) {
                    var pair = pairs.get(i);
                    legacyKerning.put(pairKey(pair.glyph1(), pair.glyph2()), pair.advance());
                }
            }
        }
    }

    static FontFace load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer data = memAlloc(bytes.length);
        STBTTFontinfo info = STBTTFontinfo.malloc();
        try {
            data.put(bytes).flip();
            if (!stbtt_InitFont(info, data)) throw new IOException("Unsupported TrueType font: " + path);
            return new FontFace(data, info);
        } catch (Throwable failure) {
            info.free();
            memFree(data);
            throw failure;
        }
    }

    STBTTFontinfo info() { ensureOpen(); return info; }
    float scale() { return scale; }
    float ascent() { return ascent; }

    Glyph glyph(int codepoint) {
        ensureOpen();
        return glyphs.computeIfAbsent(codepoint, cp -> {
            int index = stbtt_FindGlyphIndex(info, cp);
            if (index == 0) index = replacement;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var advance = stack.mallocInt(1);
                stbtt_GetGlyphHMetrics(info, index, advance, null);
                return new Glyph(index, advance.get(0) * scale);
            }
        });
    }

    Line layout(String text) {
        ensureOpen();
        return lines.computeIfAbsent(text, this::positionLine);
    }

    private Line positionLine(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        List<PositionedGlyph> positions = new ArrayList<>();
        float pen = 0;
        int previous = -1;
        for (int i = 0; i < normalized.length();) {
            int cp = normalized.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n' || cp == '\r') break;
            if (cp == '\t') {
                float tab = Math.max(1f, glyph(' ').advance() * 4f);
                pen = ((float) Math.floor(pen / tab) + 1) * tab;
                previous = -1;
                continue;
            }
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
                || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF)) continue;
            Glyph glyph = glyph(cp);
            if (previous >= 0) {
                int adjustment = stbtt_GetGlyphKernAdvance(info, previous, glyph.index());
                if (adjustment == 0) adjustment = legacyKerning.getOrDefault(pairKey(previous, glyph.index()), 0);
                pen += adjustment * scale;
            }
            positions.add(new PositionedGlyph(glyph.index(), pen));
            pen += glyph.advance();
            previous = glyph.index();
        }
        return new Line(List.copyOf(positions), Math.max(0, pen));
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Font face is closed");
    }

    private static long pairKey(int left, int right) { return ((long) left << 32) | (right & 0xFFFFFFFFL); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        lines.clear();
        glyphs.clear();
        legacyKerning.clear();
        info.free();
        memFree(data);
    }
}
