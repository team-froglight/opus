@file:JvmName("Transitions")

package opus.core

/** Slides include a fade so the view can change without a visible jump. */
enum class ViewTransition { FADE, SLIDE_HORIZONTAL, SLIDE_VERTICAL }

private class ViewState<T>(var displayed: T) {
    val progress = AnimationValue(1f) { a, b, t -> a + (b - a) * t }
    var generation = 0L
}

/**
 * Fades/slides the current view out, then the latest requested view in.
 * [durationMillis] is the total exit + enter duration. Rapid changes coalesce to the latest target.
 * Hoist state that should survive navigation outside this container.
 */
@Composable
@JvmOverloads
fun <T> AnimatedContent(target: T, modifier: Modifier = Modifier,
                        transition: ViewTransition = ViewTransition.SLIDE_VERTICAL,
                        durationMillis: Int = 280, easing: Easing = Easing.EASE_IN_OUT,
                        distance: Float = 12f, content: @Composable (T) -> Unit) {
    require(durationMillis >= 0 && distance.isFinite() && distance >= 0f)
    val composer = Composition.currentComposer ?: error("AnimatedContent outside composition")
    composer.startGroup("AnimatedContent")
    try {
        val state = composer.remember { ViewState(target) }
        val snap = composer.reducedMotion || durationMillis <= 1
        if (state.displayed != target && (snap || state.progress.value <= 0f)) {
            state.displayed = target
            state.generation++
        }
        val leaving = state.displayed != target
        val duration = if (snap) 0 else if (leaving) durationMillis / 2 else durationMillis - durationMillis / 2
        val progress = composer.animate(state.progress, if (leaving) 0f else 1f, duration, easing)
        val style = transitionStyle(modifier, progress, transition, distance)
            .inputEnabled(!leaving && progress >= 1f)
        Box(style) {
            composer.startGroup("View#${state.generation}")
            try { content(state.displayed) } finally { composer.endGroup() }
        }
    } finally { composer.endGroup() }
}

/** Keeps outgoing content mounted until its exit finishes, then releases its layout space and state. */
@Composable
@JvmOverloads
fun AnimatedVisibility(visible: Boolean, modifier: Modifier = Modifier,
                       transition: ViewTransition = ViewTransition.SLIDE_VERTICAL,
                       durationMillis: Int = 220, easing: Easing = Easing.EASE_IN_OUT,
                       distance: Float = 8f, content: @Composable () -> Unit) {
    require(durationMillis >= 0 && distance.isFinite() && distance >= 0f)
    val composer = Composition.currentComposer ?: error("AnimatedVisibility outside composition")
    composer.startGroup("AnimatedVisibility")
    try {
        val progress = animateFloat(if (visible) 1f else 0f, durationMillis, easing)
        if (visible || progress > 0f) {
            // Same direction on enter/exit preserves position during reversals.
            Box(transitionStyle(modifier, progress, transition, distance)
                .inputEnabled(visible && progress >= 1f), content)
        }
    } finally { composer.endGroup() }
}

private fun transitionStyle(modifier: Modifier, progress: Float,
                            transition: ViewTransition, distance: Float): Modifier {
    val offset = (1f - progress) * distance
    return modifier.opacity(progress).then(Offset(
        if (transition == ViewTransition.SLIDE_HORIZONTAL) offset else 0f,
        if (transition == ViewTransition.SLIDE_VERTICAL) offset else 0f))
}

private class StyleState {
    val values = mutableMapOf<String, AnimationValue<*>>()
}

/**
 * Transitions the preceding modifier's size, padding, offset, opacity and style properties.
 * Newly added/removed properties and automatic (null) dimensions snap; existing numeric values tween.
 * Keep this call in a stable composition slot, like remember.
 */
@Composable
@JvmOverloads
fun Modifier.animateChanges(durationMillis: Int = 220, easing: Easing = Easing.EASE_OUT): Modifier {
    require(durationMillis >= 0)
    val composer = Composition.currentComposer ?: error("animateChanges outside composition")
    val state = composer.remember { StyleState() }
    val used = mutableSetOf<String>()
    val occurrences = mutableMapOf<String, Int>()
    fun <T> value(key: String, target: T, interpolate: (T, T, Float) -> T): T {
        used.add(key)
        @Suppress("UNCHECKED_CAST")
        val track = state.values.getOrPut(key) { AnimationValue(target, interpolate) } as AnimationValue<T>
        return composer.animate(track, target, durationMillis, easing)
    }
    var result: Modifier = Modifier
    for (element in elements()) {
        val name = element.javaClass.name
        val index = occurrences.getOrDefault(name, 0)
        occurrences[name] = index + 1
        val prefix = "$name#$index/"
        fun f(key: String, target: Float): Float {
            require(target.isFinite()) { "Transition values must be finite" }
            return value(prefix + key, target) { a, b, t -> (a.toDouble() * (1.0 - t) + b.toDouble() * t).toFloat() }
        }
        fun optional(key: String, target: Float?): Float? = target?.let { f(key, it) }
        fun color(key: String, target: Int): Int = value(prefix + key, target, ::interpolateColor)
        result = result.then(when (element) {
            is FixedSize -> FixedSize(optional("w", element.width), optional("h", element.height))
            is Padding -> Padding(f("s", element.start), f("t", element.top), f("e", element.end), f("b", element.bottom))
            is Offset -> Offset(f("x", element.x), f("y", element.y))
            is Opacity -> Opacity(f("a", element.alpha).coerceIn(0f, 1f))
            is Background -> Background(color("c", element.color), optional("r", element.cornerRadius))
            is Border -> Border(f("w", element.width), color("c", element.color), optional("r", element.cornerRadius))
            is Outline -> Outline(f("w", element.width), color("c", element.color), f("o", element.offset))
            is Shadow -> Shadow(f("r", element.blurRadius), f("x", element.offsetX), f("y", element.offsetY), color("c", element.color))
            is CornerClip -> CornerClip(f("ts", element.topStart), f("te", element.topEnd), f("be", element.bottomEnd), f("bs", element.bottomStart))
            else -> element
        })
    }
    state.values.keys.retainAll(used)
    return result
}
