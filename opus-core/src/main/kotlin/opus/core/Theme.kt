@file:JvmName("Themes")

package opus.core

/**
 * Every color a built-in control paints. Derived defaults follow [accent], so overriding the
 * accent alone recolors selection and focus too.
 */
data class Colors @JvmOverloads constructor(
    val surface: Int = 0xFF1E1E28.toInt(),
    val onSurface: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFF7C5CFF.toInt(),
    /** Content drawn on [accent]: checkmarks and switch/slider thumbs. */
    val onAccent: Int = 0xFFFFFFFF.toInt(),
    /** Secondary text, hints, placeholders and idle icons. */
    val muted: Int = 0xFF98A8BB.toInt(),
    /** Borders of fields, menus, calendars and tooltips. */
    val outline: Int = 0xFF435367.toInt(),
    /** Floating menus, calendars and tooltips. */
    val overlay: Int = 0xFF111B28.toInt(),
    /** Secondary surfaces, such as pagination actions. */
    val surfaceVariant: Int = 0xFF263444.toInt(),
    /** Hovered and keyboard-highlighted rows. */
    val highlight: Int = 0x1FFFFFFF,
    /** Selected rows, days and text. */
    val selection: Int = withAlpha(accent, 0x66),
    /** Keyboard focus rings. */
    val focus: Int = accent,
    val error: Int = 0xFFF29A9A.toInt(),
    /** Disabled buttons, unchecked switches and slider rails. */
    val inactive: Int = 0xFF3A3A44.toInt(),
    /** Text and icons of disabled controls. */
    val disabledContent: Int = 0xFF6F7D8D.toInt()
) {
    fun withSurface(value: Int) = copy(surface = value)
    fun withOnSurface(value: Int) = copy(onSurface = value)
    /** Also moves [selection] and [focus] when they still follow the previous accent. */
    fun withAccent(value: Int) = copy(accent = value,
        selection = if (selection == withAlpha(accent, 0x66)) withAlpha(value, 0x66) else selection,
        focus = if (focus == accent) value else focus)
    fun withOnAccent(value: Int) = copy(onAccent = value)
    fun withMuted(value: Int) = copy(muted = value)
    fun withOutline(value: Int) = copy(outline = value)
    fun withOverlay(value: Int) = copy(overlay = value)
    fun withSurfaceVariant(value: Int) = copy(surfaceVariant = value)
    fun withHighlight(value: Int) = copy(highlight = value)
    fun withSelection(value: Int) = copy(selection = value)
    fun withFocus(value: Int) = copy(focus = value)
    fun withError(value: Int) = copy(error = value)
    fun withInactive(value: Int) = copy(inactive = value)
    fun withDisabledContent(value: Int) = copy(disabledContent = value)
}

private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0xFFFFFF)

data class Shapes(val cornerRadius: Float = 12f) {
    init { require(cornerRadius.isFinite() && cornerRadius >= 0f) { "cornerRadius must be finite and nonnegative" } }
}

data class Typography(
    val h1Size: Float = 24f,
    val h2Size: Float = 19f,
    val h3Size: Float = 15f,
    val bodySize: Float = 12f,
    val labelSize: Float = 10f
) {
    init {
        require(listOf(h1Size, h2Size, h3Size, bodySize, labelSize).all { it.isFinite() && it > 0f }) {
            "Typography sizes must be positive and finite"
        }
    }
    fun sizeFor(role: TextRole): Float = when (role) {
        TextRole.HEADING_1 -> h1Size
        TextRole.HEADING_2 -> h2Size
        TextRole.HEADING_3 -> h3Size
        TextRole.PARAGRAPH -> bodySize
        TextRole.LABEL -> labelSize
    }
}

data class Theme(
    val colors: Colors = Colors(),
    val shapes: Shapes = Shapes(),
    val typography: Typography = Typography()
)

val LocalTheme: CompositionLocal<Theme> = compositionLocalOf { Theme() }

@Composable
fun Theme(theme: Theme = Theme(), content: @Composable () -> Unit) {
    composeGroup("Theme") { LocalTheme.scoped(theme) { content() } }
}
