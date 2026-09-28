package opus.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FontFaceTest {
    private FontFace openFont() throws Exception {
        String windows = System.getenv().getOrDefault("SystemRoot", "C:/Windows");
        Path font = List.of(Path.of(windows, "Fonts/arial.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"))
            .stream().filter(Files::isRegularFile).findFirst().orElse(null);
        Assumptions.assumeTrue(font != null, "Requires an installed Arial or DejaVu Sans test font");
        return FontFace.load(font);
    }

    @Test void appliesKerningToBothWidthAndGlyphPositions() throws Exception {
        try (var face = openFont()) {
            var pair = face.layout("AV");
            float separate = face.layout("A").width() + face.layout("V").width();
            assertTrue(pair.width() < separate, "AV should be kerned closer together");
            assertEquals(pair.width(), pair.glyphs().get(1).x() + face.glyph('V').advance(), 0.0001f);
            assertNotEquals(Math.round(pair.glyphs().get(1).x()), pair.glyphs().get(1).x(), 0.001f);
        }
    }

    @Test void normalizesDecomposedAccents() throws Exception {
        try (var face = openFont()) {
            assertEquals(face.layout("Café Å"), face.layout("Cafe\u0301 A\u030A"));
        }
    }

    @Test void resolvesGreekAndCyrillicFromTheFont() throws Exception {
        try (var face = openFont()) {
            int missing = face.glyph(0x10FFFF).index();
            assertNotEquals(missing, face.glyph('Ω').index());
            assertNotEquals(missing, face.glyph('ї').index());
            assertEquals(1, face.layout(new String(Character.toChars(0x10FFFF))).glyphs().size());
        }
    }

    @Test void skipsFormattingControlsAndStopsAtLineBreaks() throws Exception {
        try (var face = openFont()) {
            assertEquals(face.layout("AV"), face.layout("A\u200BV\uFE0F\r\nignored"));
            assertEquals(face.layout("AV"), face.layout("AV\nignored"));
            assertEquals(0f, face.layout("\u200B").width());
        }
    }

    @Test void tabsMoveToTheNextFourSpaceStop() throws Exception {
        try (var face = openFont()) {
            var line = face.layout("\tA");
            assertEquals(face.glyph(' ').advance() * 4, line.glyphs().getFirst().x(), 0.0001f);
        }
    }

    @Test void closingTwiceIsSafeAndPreventsNativeAccess() throws Exception {
        var face = openFont();
        face.close();
        face.close();
        assertThrows(IllegalStateException.class, () -> face.layout("closed"));
        assertThrows(IllegalStateException.class, face::info);
    }
}
