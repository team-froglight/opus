package opus.minecraft;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import opus.core.TextRole;
import opus.core.Typography;
import opus.yoga.TextMeasurer;

/** Bridges MC's Font into Yoga leaf measurement. */
public class FontMeasurer implements TextMeasurer {
    private static final Typography DEFAULT_TYPOGRAPHY = new Typography();
    private final Font font;

    public FontMeasurer(Font font) {
        this.font = font;
    }

    @Override
    public kotlin.Pair<Float, Float> measure(String text, TextRole role, float maxWidth) {
        return measure(text, role, maxWidth, null);
    }

    @Override
    public kotlin.Pair<Float, Float> measure(String text, TextRole role, float maxWidth, String fontId) {
        return measure(text, role, maxWidth, fontId, sizeFor(role));
    }

    @Override
    public kotlin.Pair<Float, Float> measure(String text, TextRole role, float maxWidth, String fontId, float size) {
        String singleLine = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
        if (Fonts.isCustom(fontId)) {
            float w = Fonts.measure(fontId, singleLine, size, maxWidth);
            if (w >= 0f) return new kotlin.Pair<>(Math.min(w, maxWidth), size);
            // Broken/unavailable: fall through to vanilla metrics.
        }
        float scale = size / 9.0f; // vanilla glyph cell ~9px tall
        ResourceLocation location = fontId != null && !Fonts.isCustom(fontId)
            ? ResourceLocation.tryParse(fontId) : null;
        float w = (location == null ? font.width(singleLine)
            : font.width(Component.literal(singleLine).withStyle(Style.EMPTY.withFont(location)))) * scale;
        if (w > maxWidth) w = maxWidth;
        return new kotlin.Pair<>(w, size);
    }

    @Override
    public float wrappedHeight(String text, float maxWidth) {
        return font.split(Component.literal(text), Math.max(1, (int) maxWidth)).size() * font.lineHeight;
    }

    @Override
    public float lineHeight(TextRole role) {
        return sizeFor(role);
    }

    /** Default-theme size; themed {@code Text} nodes pass their own size to the sized measure. */
    public static float sizeFor(TextRole role) {
        return DEFAULT_TYPOGRAPHY.sizeFor(role);
    }

    public float scaleFor(TextRole role) {
        return sizeFor(role) / 9.0f;
    }
}
