@file:JvmName("States")

package opus.core

import kotlin.reflect.KProperty

/**
 * Snapshot-state primitive. Reads inside a composition subscribe its current
 * scope; writes invalidate subscribers and schedule recomposition.
 */
class MutableState<T>(initial: T) {
    private val scopes = mutableSetOf<RecomposeScope>()

    var value: T = initial
        get() {
            Composition.trackRead(this)
            return field
        }
        set(v) {
            if (field != v) {
                field = v
                val fire = scopes.toList()
                scopes.clear()
                fire.forEach { it.invalidate() }
            }
        }

    internal fun recordRead(scope: RecomposeScope?) {
        if (scope != null) {
            scopes += scope
            scope.track(this)
        }
    }

    internal fun removeScope(scope: RecomposeScope) {
        scopes -= scope
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, v: T) {
        value = v
    }
}

fun <T> mutableStateOf(initial: T): MutableState<T> = MutableState(initial)

/** Recompose scope: invalidating it re-runs the whole composition (v1 coarse). */
class RecomposeScope internal constructor(internal val composition: Composition) {
    private val observedStates = mutableSetOf<MutableState<*>>()

    internal fun track(state: MutableState<*>) {
        observedStates += state
    }

    internal fun dispose() {
        observedStates.forEach { it.removeScope(this) }
        observedStates.clear()
    }

    fun invalidate() = composition.requestRecompose()
}
