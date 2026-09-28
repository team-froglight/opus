@file:JvmName("Modifiers")

package opus.core

/**
 * Chainable style/layout element, Compose-[Modifier] style.
 * Backed by Yoga in :opus-yoga; interpreted for draw in :opus-minecraft.
 */
interface Modifier {
    companion object : Modifier {
        /** Java-friendly equivalent of Kotlin's `Modifier` singleton. */
        @JvmField val EMPTY: Modifier = this
        override fun then(other: Modifier): Modifier =
            if (other === Modifier) this else CombinedModifier(this, other)
    }

    infix fun then(other: Modifier): Modifier

    interface Element : Modifier {
        override fun then(other: Modifier): Modifier =
            if (other === Modifier) this else CombinedModifier(this, other)
    }
}

private class CombinedModifier(
    val outer: Modifier,
    val inner: Modifier
) : Modifier {
    override fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    override fun toString(): String = "[$outer then $inner]"
}

/** Flattens the chain outer-first. */
fun Modifier.elements(): List<Modifier.Element> {
    val out = ArrayList<Modifier.Element>()
    fun visit(m: Modifier) {
        when (m) {
            is Modifier.Companion -> Unit
            is Modifier.Element -> out += m
            is CombinedModifier -> {
                visit(m.outer)
                visit(m.inner)
            }
            else -> error("Unknown Modifier: $m")
        }
    }
    visit(this)
    return out
}

// ---- Layout elements ----

data class Padding(val start: Float = 0f, val top: Float = 0f, val end: Float = 0f, val bottom: Float = 0f) : Modifier.Element
data class FixedSize(val width: Float? = null, val height: Float? = null) : Modifier.Element
data class FillMax(val widthFraction: Float = 0f, val heightFraction: Float = 0f) : Modifier.Element
data class Weight(val weight: Float) : Modifier.Element
data class Offset(val x: Float, val y: Float) : Modifier.Element
/** Opacity is inherited by descendants; layout space is unchanged. */
data class Opacity(val alpha: Float) : Modifier.Element {
    init { require(alpha.isFinite() && alpha in 0f..1f) { "Opacity must be between 0 and 1" } }
}
data class InputEnabled(val enabled: Boolean) : Modifier.Element

fun Modifier.padding(all: Float): Modifier = then(Padding(all, all, all, all))
fun Modifier.padding(horizontal: Float = 0f, vertical: Float = 0f): Modifier =
    then(Padding(horizontal, vertical, horizontal, vertical))

@JvmOverloads
fun Modifier.paddingEach(start: Float = 0f, top: Float = 0f, end: Float = 0f, bottom: Float = 0f): Modifier =
    then(Padding(start, top, end, bottom))

fun Modifier.size(width: Float, height: Float): Modifier = then(FixedSize(width, height))
fun Modifier.width(width: Float): Modifier = then(FixedSize(width = width))
fun Modifier.height(height: Float): Modifier = then(FixedSize(height = height))
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = then(FillMax(widthFraction = fraction))
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier = then(FillMax(heightFraction = fraction))
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = then(FillMax(fraction, fraction))
fun Modifier.weight(weight: Float): Modifier = then(Weight(weight))
fun Modifier.offset(x: Float, y: Float): Modifier = then(Offset(x, y))
fun Modifier.opacity(alpha: Float): Modifier = then(Opacity(alpha))
fun Modifier.inputEnabled(enabled: Boolean): Modifier = then(InputEnabled(enabled))

// ---- Style elements (drawn by the GL style layer) ----

data class Background(val color: Int, val cornerRadius: Float? = null) : Modifier.Element
data class Border(val width: Float, val color: Int, val cornerRadius: Float? = null) : Modifier.Element
data class Outline(val width: Float, val color: Int, val offset: Float = 0f) : Modifier.Element
data class Shadow(val blurRadius: Float, val offsetX: Float = 0f, val offsetY: Float = 0f, val color: Int = 0x80000000.toInt()) : Modifier.Element
data class CornerClip(val topStart: Float = 0f, val topEnd: Float = 0f, val bottomEnd: Float = 0f, val bottomStart: Float = 0f) : Modifier.Element
data class Cutout(val kind: CutoutKind = CutoutKind.NOTCH_TOP, val size: Float = 16f) : Modifier.Element

enum class CutoutKind { NOTCH_TOP, NOTCH_START, HOLE_CENTER, TAB_END }

fun Modifier.background(color: Int, cornerRadius: Float? = null): Modifier = then(Background(color, cornerRadius))
fun Modifier.border(width: Float, color: Int, cornerRadius: Float? = null): Modifier = then(Border(width, color, cornerRadius))
fun Modifier.outline(width: Float, color: Int, offset: Float = 0f): Modifier = then(Outline(width, color, offset))
fun Modifier.shadow(blurRadius: Float, offsetX: Float = 0f, offsetY: Float = 0f, color: Int = 0x80000000.toInt()): Modifier =
    then(Shadow(blurRadius, offsetX, offsetY, color))

fun Modifier.clip(radius: Float): Modifier = then(CornerClip(radius, radius, radius, radius))
fun Modifier.clip(topStart: Float = 0f, topEnd: Float = 0f, bottomEnd: Float = 0f, bottomStart: Float = 0f): Modifier =
    then(CornerClip(topStart, topEnd, bottomEnd, bottomStart))

fun Modifier.cutout(kind: CutoutKind = CutoutKind.NOTCH_TOP, size: Float = 16f): Modifier = then(Cutout(kind, size))

// ---- Interaction ----

data class Clickable(val onClick: () -> Unit) : Modifier.Element

fun Modifier.clickable(onClick: () -> Unit): Modifier = then(Clickable(onClick))
