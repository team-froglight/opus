@file:JvmName("Animations")

package opus.core

import kotlin.math.roundToInt

/** Timing curves for transitions. Progress and endpoints are always clamped. */
enum class Easing {
    LINEAR, EASE_OUT, EASE_IN_OUT;

    fun transform(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> t
            EASE_OUT -> 1f - (1f - t) * (1f - t) * (1f - t)
            EASE_IN_OUT -> t * t * (3f - 2f * t)
        }
    }
}

/**
 * Smoothly follows [target], starting at its initial value without an entrance animation.
 * Retargeting starts from the displayed value. Call in a stable composition slot, like [remember].
 * UiScreen supplies frames automatically; other hosts call [Composition.advanceAnimations].
 */
@Composable
@JvmOverloads
fun animateFloat(target: Float, durationMillis: Int = 220, easing: Easing = Easing.EASE_OUT): Float {
    require(target.isFinite()) { "Animation target must be finite" }
    return animationValue(target, durationMillis, easing) { from, to, t ->
        (from.toDouble() * (1.0 - t) + to.toDouble() * t).toFloat()
    }
}

/** Interpolates ARGB colors with premultiplied alpha, avoiding dark edges when fading. */
@Composable
@JvmOverloads
fun animateColor(target: Int, durationMillis: Int = 220, easing: Easing = Easing.EASE_OUT): Int =
    animationValue(target, durationMillis, easing, ::interpolateColor)

private fun <T> animationValue(target: T, durationMillis: Int, easing: Easing,
                               interpolate: (T, T, Float) -> T): T {
    require(durationMillis >= 0) { "Animation duration must not be negative" }
    val composer = Composition.currentComposer ?: error("Animation outside composition")
    val value = composer.remember { AnimationValue(target, interpolate) }
    return composer.animate(value, target, durationMillis, easing)
}

internal fun interpolateColor(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    val a0 = (from ushr 24).toFloat()
    val a1 = (to ushr 24).toFloat()
    val alpha = a0 * (1f - t) + a1 * t
    if (alpha <= 0f) return 0
    fun channel(shift: Int): Int =
        ((((from ushr shift) and 255) * a0 * (1f - t) +
            ((to ushr shift) and 255) * a1 * t) / alpha).roundToInt().coerceIn(0, 255)
    return (alpha.roundToInt() shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

internal class AnimationValue<T>(initial: T, private val interpolate: (T, T, Float) -> T) {
    var value: T = initial
        private set
    private var from = initial
    private var target = initial
    private var startedAt = 0.0
    private var durationMillis = 0
    private var easing = Easing.LINEAR
    var running = false
        private set

    fun target(next: T, duration: Int, curve: Easing, time: Double, snap: Boolean): T {
        if (snap || duration == 0) {
            target = next
            value = next
            running = false
        } else if (next != target) {
            from = value
            target = next
            startedAt = time
            durationMillis = duration
            easing = curve
            running = from != target
        }
        return value
    }

    fun advance(time: Double, snap: Boolean): Boolean {
        if (!running) return false
        val progress = if (snap) 1f else ((time - startedAt) / durationMillis).toFloat().coerceIn(0f, 1f)
        val previous = value
        value = if (progress >= 1f) target else interpolate(from, target, easing.transform(progress))
        if (progress >= 1f) running = false
        return previous != value
    }
}
