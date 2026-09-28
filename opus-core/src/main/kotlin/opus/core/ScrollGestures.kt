package opus.core

import kotlin.math.abs

/** Direction of the horizontal scroll delta, after the operating system's direction preference. */
enum class SwipeDirection { LEFT, RIGHT }

data class ScrollGestureUpdate(val verticalScroll: Double = 0.0, val swipe: SwipeDirection? = null)
data class SwipePreview(val direction: SwipeDirection, val progress: Float)

/**
 * Axis-locked scrolling and pull-to-confirm navigation. Full progress only arms a swipe;
 * release commits it. Pulling back below full progress cancels it.
 * Hosts call [release] on actual finger-up; silence never ends a gesture by default.
 * Scroll-only hosts may explicitly opt into [inferRelease], an approximation that cannot
 * distinguish stationary fingers from release. No extra inertia or automatic repeat is added.
 */
class ScrollGestures @JvmOverloads constructor(
    val swipeThreshold: Double = 8.0,
    val quietMillis: Long = 140,
    val inferRelease: Boolean = false
) {
    init {
        require(swipeThreshold.isFinite() && swipeThreshold > 0)
        require(quietMillis in 1..60_000)
    }

    private enum class Axis { UNDECIDED, VERTICAL, HORIZONTAL }
    private var axis = Axis.UNDECIDED
    private var lastEvent: Long? = null
    private var travelX = 0.0
    private var travelY = 0.0
    private var pendingY = 0.0
    private var pull = 0.0
    private var suppressed = false

    val isActive: Boolean get() = lastEvent != null

    /** Follows the current pull, including retraction; stays visible while fully armed. */
    val preview: SwipePreview?
        get() = if (axis != Axis.HORIZONTAL || suppressed || pull == 0.0) null else
            SwipePreview(if (pull < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT,
                (abs(pull) / swipeThreshold).coerceIn(0.0, 1.0).toFloat())

    /** Cancel on screen removal or input-mode changes. */
    fun reset() {
        axis = Axis.UNDECIDED
        lastEvent = null
        travelX = 0.0
        travelY = 0.0
        pendingY = 0.0
        pull = 0.0
        suppressed = false
    }

    fun suppressSwipe() { if (isActive) suppressed = true }

    /** Native finger-up: partial, retracted and blocked pulls never navigate. */
    fun release(): ScrollGestureUpdate {
        val swipe = if (axis == Axis.HORIZONTAL && !suppressed && abs(pull) >= swipeThreshold)
            if (pull < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT else null
        reset()
        return ScrollGestureUpdate(swipe = swipe)
    }

    /** Optional idle inference for scroll-only hosts; inert unless [inferRelease] is enabled. */
    fun advance(timeNanos: Long): ScrollGestureUpdate {
        if (!inferRelease) return ScrollGestureUpdate()
        val previous = lastEvent ?: return ScrollGestureUpdate()
        return if (timeNanos >= previous && timeNanos - previous >= quietMillis * 1_000_000)
            release() else ScrollGestureUpdate()
    }

    @JvmOverloads
    fun accept(deltaX: Double, deltaY: Double, timeNanos: Long, swipesEnabled: Boolean = true): ScrollGestureUpdate {
        if (!deltaX.isFinite() || !deltaY.isFinite() || (deltaX == 0.0 && deltaY == 0.0)) return ScrollGestureUpdate()
        val previous = lastEvent
        if (previous != null && timeNanos < previous) return ScrollGestureUpdate()
        if (!swipesEnabled) suppressSwipe()
        // Preserve a completed release if the host missed the frame between gestures.
        val ended = advance(timeNanos)
        lastEvent = timeNanos
        suppressed = suppressed || !swipesEnabled
        if (suppressed && axis == Axis.UNDECIDED) axis = Axis.VERTICAL
        travelX += abs(deltaX)
        travelY += abs(deltaY)
        pull += deltaX
        pendingY += deltaY
        if (axis == Axis.UNDECIDED) {
            axis = when {
                travelY > 0 && travelY >= travelX * 1.5 -> Axis.VERTICAL
                travelX >= 0.25 && travelX >= travelY * 1.5 -> Axis.HORIZONTAL
                travelX + travelY >= 1.0 -> Axis.VERTICAL
                else -> Axis.UNDECIDED
            }
        }
        if (axis == Axis.VERTICAL) {
            val vertical = pendingY
            pendingY = 0.0
            return ScrollGestureUpdate(verticalScroll = vertical, swipe = ended.swipe)
        }
        // Reversal responds immediately even after pulling beyond the limit.
        if (axis == Axis.HORIZONTAL) pull = pull.coerceIn(-swipeThreshold, swipeThreshold)
        return ended
    }
}
