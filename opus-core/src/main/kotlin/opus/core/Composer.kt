@file:JvmName("Compositions")

package opus.core

/**
 * Runs @Composable content lambdas and builds an [UiNode] tree.
 * v1 invalidation is coarse: any invalidated scope re-runs the root content.
 * The public API ([RecomposeScope], [remember]) is scope-shaped so fine-grained
 * recomposition can land internally without breaking call sites.
 */
class Composition(private val content: @Composable () -> Unit) : AutoCloseable {
    var root: UiNode = UiNode("Root")
        private set

    private val slots = mutableMapOf<Pair<String, Any?>, MutableMap<Int, Any?>>()
    private var usedSlots = mutableMapOf<Pair<String, Any?>, MutableSet<Int>>()
    private val groupPath = ArrayDeque<String>()
    private val groupCounters = ArrayDeque<Int>()
    private var collectingScope: RecomposeScope? = null
    private var activeScope: RecomposeScope? = null
    private var recomposePending = false
    private var closed = false
    private var animations = linkedSetOf<AnimationValue<*>>()
    private var collectingAnimations: MutableSet<AnimationValue<*>>? = null
    private var previousFrameNanos: Long? = null
    private var animationTimeMillis = 0.0

    /** 1 = normal speed, 2 = half speed, 0 = reduced motion (snap to targets). */
    var animationDurationScale: Float = 1f
        set(value) {
            require(value.isFinite() && value >= 0f) { "Animation scale must be finite and nonnegative" }
            field = value
        }

    val hasRunningAnimations: Boolean get() = animations.any { it.running }

    /**
     * Advance from a monotonic clock (e.g. System.nanoTime), once before layout/drawing.
     * Batches all animated values into at most one recomposition per frame. Idle frames do no work.
     * The first call establishes the clock origin; backwards timestamps are ignored.
     */
    fun advanceAnimations(frameTimeNanos: Long) {
        if (closed) return
        val previous = previousFrameNanos
        if (previous != null && frameTimeNanos < previous) return
        previousFrameNanos = frameTimeNanos
        if (previous != null && animationDurationScale > 0f) {
            animationTimeMillis += (frameTimeNanos.toDouble() - previous.toDouble()) / 1_000_000.0 / animationDurationScale
        }
        var changed = false
        for (animation in animations) {
            if (animation.advance(animationTimeMillis, animationDurationScale == 0f)) changed = true
        }
        if (changed) requestRecompose()
    }

    internal fun <T> animate(value: AnimationValue<T>, target: T, duration: Int, easing: Easing): T {
        collectingAnimations!!.add(value)
        return value.target(target, duration, easing, animationTimeMillis,
            animationDurationScale == 0f || value !in animations)
    }

    /** Changes after every successful composition, including explicit [compose] calls. */
    var compositionVersion: Int = 0
        private set
    var recomposeCount: Int = 0
        private set

    var onRecomposed: (() -> Unit)? = null

    companion object {
        private val composerForThread = ThreadLocal<Composer?>()

        internal var currentComposer: Composer?
            get() = composerForThread.get()
            set(value) {
                if (value == null) composerForThread.remove() else composerForThread.set(value)
            }

        internal fun trackRead(state: MutableState<*>) {
            currentComposer?.track(state)
        }
    }

    fun compose(): UiNode {
        check(!closed) { "Composition is closed" }
        val composer = Composer(this)
        val previousComposer = currentComposer
        val nextRoot = UiNode("Root")
        val nextScope = RecomposeScope(this)
        val nextAnimations = linkedSetOf<AnimationValue<*>>()
        collectingAnimations = nextAnimations
        usedSlots = mutableMapOf()
        currentComposer = composer
        groupPath.clear()
        groupCounters.clear()
        groupCounters.addLast(0)
        collectingScope = nextScope
        try {
            withParentNode(nextRoot) { content() }
            slots.keys.retainAll(usedSlots.keys)
            slots.forEach { (key, values) -> values.keys.retainAll(usedSlots.getValue(key)) }
            activeScope?.dispose()
            activeScope = nextScope
            root = nextRoot
            animations = nextAnimations
            compositionVersion++
        } catch (error: Throwable) {
            nextScope.dispose()
            throw error
        } finally {
            collectingScope = null
            collectingAnimations = null
            currentComposer = previousComposer
            groupPath.clear()
            groupCounters.clear()
        }
        return root
    }

    fun requestRecompose() {
        if (closed) return
        if (recomposePending) return
        recomposePending = true
        try {
            compose()
            recomposeCount++
            onRecomposed?.invoke()
        } finally {
            recomposePending = false
        }
    }

    internal fun currentScope(): RecomposeScope? = collectingScope

    internal fun <T> rememberCached(key: Pair<String, Any?>, index: Int, init: () -> T): T {
        usedSlots.getOrPut(key) { mutableSetOf() }.add(index)
        val groupSlots = slots.getOrPut(key) { mutableMapOf() }
        @Suppress("UNCHECKED_CAST")
        if (groupSlots.containsKey(index)) return groupSlots[index] as T
        return init().also { groupSlots[index] = it }
    }

    internal fun enterGroup(name: String): String {
        val parent = groupPath.lastOrNull().orEmpty()
        val index = groupCounters.removeLast()
        groupCounters.addLast(index + 1)
        groupCounters.addLast(0)
        val key = if (parent.isEmpty()) "$name#$index" else "$parent/$name#$index"
        groupPath.addLast(key)
        return key
    }

    internal fun exitGroup() {
        groupPath.removeLast()
        groupCounters.removeLast()
    }

    internal fun currentGroupKey(): String = groupPath.lastOrNull().orEmpty()

    override fun close() {
        if (closed) return
        closed = true
        activeScope?.dispose()
        activeScope = null
        slots.clear()
        usedSlots.clear()
        animations.clear()
        previousFrameNanos = null
        onRecomposed = null
    }
}

/** Receiver available inside @Composable content via [Composition.currentComposer]. */
class Composer internal constructor(private val composition: Composition) {
    internal val reducedMotion: Boolean get() = composition.animationDurationScale == 0f
    internal fun nodeIdentity(index: Int): String = "${composition.currentGroupKey()}/node#$index"
    private val rememberIndices = ArrayDeque<Int>().apply { addLast(0) }

    fun startGroup(name: String): String {
        rememberIndices.addLast(0)
        return composition.enterGroup(name)
    }

    fun endGroup() {
        composition.exitGroup()
        rememberIndices.removeLast()
    }

    fun <T> remember(key: Any? = null, init: () -> T): T {
        val index = rememberIndices.removeLast()
        rememberIndices.addLast(index + 1)
        return composition.rememberCached(composition.currentGroupKey() to key, index, init)
    }

    internal fun track(state: MutableState<*>) {
        state.recordRead(composition.currentScope())
    }

    internal fun <T> animate(value: AnimationValue<T>, target: T, duration: Int, easing: Easing): T =
        composition.animate(value, target, duration, easing)
}

/** Entry point: builds a composition and returns it (call [Composition.compose] via this). */
fun setContent(content: @Composable () -> Unit): Composition {
    val composition = Composition(content)
    composition.compose()
    return composition
}

/** Retains a value in its composition slot. Use a key to reset that slot's value. */
@Composable
fun <T> remember(key: Any? = null, init: () -> T): T =
    (Composition.currentComposer ?: error("remember() outside composition")).remember(key, init)
