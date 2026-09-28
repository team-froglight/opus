package opus.core

/** Ambient value holder, Compose-CompositionLocal style. */
class CompositionLocal<T>(private val defaultFactory: () -> T) {
    private val stack = ThreadLocal.withInitial { ArrayDeque<T>() }

    val current: T get() = stack.get().lastOrNull() ?: defaultFactory()

    internal fun push(value: T) {
        stack.get().addLast(value)
    }

    internal fun pop() {
        val values = stack.get()
        values.removeLast()
        if (values.isEmpty()) stack.remove()
    }
}

fun <T> compositionLocalOf(defaultFactory: () -> T): CompositionLocal<T> =
    CompositionLocal(defaultFactory)

internal fun <T> CompositionLocal<T>.scoped(value: T, block: () -> Unit) {
    push(value)
    try {
        block()
    } finally {
        pop()
    }
}
