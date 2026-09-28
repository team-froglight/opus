@file:JvmName("Themes")

package opus.core

data class Colors(
    val surface: Int = 0xFF1E1E28.toInt(),
    val onSurface: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFF7C5CFF.toInt()
)

data class Shapes(val cornerRadius: Float = 12f)

data class Typography(
    val h1Size: Float = 24f,
    val h2Size: Float = 19f,
    val h3Size: Float = 15f,
    val bodySize: Float = 12f,
    val labelSize: Float = 10f
)

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
