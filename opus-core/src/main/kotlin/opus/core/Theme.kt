@file:JvmName("Themes")

package opus.core

data class Colors(
    val surface: Int = 0xFF1E1E28.toInt(),
    val onSurface: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFF7C5CFF.toInt()
)

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
    val composer = Composition.currentComposer ?: error("Theme outside composition")
    composer.startGroup("Theme")
    try {
        LocalTheme.scoped(theme) { content() }
    } finally {
        composer.endGroup()
    }
}
