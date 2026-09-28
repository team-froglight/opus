package opus.core

import java.util.function.Consumer
import java.util.function.Supplier

/** Java entry points using standard Java callbacks. Kotlin callers can use the composable DSL. */
object Ui {
    @JvmStatic @JvmOverloads
    fun dateField(value: String, onChange: Consumer<String>, label: String, modifier: Modifier = Modifier,
                  options: InputOptions = InputOptions()) = DateField(value, { onChange.accept(it) }, label, modifier, options)
    @JvmStatic @JvmOverloads
    fun textField(value: String, onChange: Consumer<String>, label: String, modifier: Modifier = Modifier,
                  options: InputOptions = InputOptions(), onSubmit: Runnable? = null) =
        TextField(value, { onChange.accept(it) }, label, modifier, options, onSubmit?.let { { it.run() } })
    @JvmStatic @JvmOverloads
    fun textArea(value: String, onChange: Consumer<String>, label: String, modifier: Modifier = Modifier,
                 options: InputOptions = InputOptions(), rows: Int = 4) =
        TextArea(value, { onChange.accept(it) }, label, modifier, options, rows)
    @JvmStatic @JvmOverloads
    fun numberField(value: String, onChange: Consumer<String>, label: String, modifier: Modifier = Modifier,
                    options: InputOptions = InputOptions(), onSubmit: Runnable? = null, numbers: NumberOptions = NumberOptions()) =
        NumberField(value, { onChange.accept(it) }, label, modifier, options, onSubmit?.let { { it.run() } }, numbers)
    @JvmStatic @JvmOverloads
    fun <T> animatedContent(target: T, content: Consumer<T>, modifier: Modifier = Modifier,
                           transition: ViewTransition = ViewTransition.SLIDE_VERTICAL, durationMillis: Int = 280) =
        AnimatedContent(target, modifier, transition, durationMillis) { content.accept(it) }
    @JvmStatic @JvmOverloads
    fun animatedVisibility(visible: Boolean, content: Runnable, modifier: Modifier = Modifier,
                           transition: ViewTransition = ViewTransition.SLIDE_VERTICAL, durationMillis: Int = 220) =
        AnimatedVisibility(visible, modifier, transition, durationMillis) { content.run() }
    @JvmStatic @JvmOverloads
    fun transition(modifier: Modifier, durationMillis: Int = 220, easing: Easing = Easing.EASE_OUT): Modifier =
        modifier.animateChanges(durationMillis, easing)
    @JvmStatic fun compose(content: Runnable): Composition = setContent { content.run() }
    @JvmStatic fun <T> state(initial: T): MutableState<T> = mutableStateOf(initial)
    @JvmStatic fun <T> remember(factory: Supplier<T>): T = opus.core.remember { factory.get() }
    @JvmStatic @JvmOverloads
    fun animateFloat(target: Float, durationMillis: Int = 220, easing: Easing = Easing.EASE_OUT): Float =
        opus.core.animateFloat(target, durationMillis, easing)
    @JvmStatic @JvmOverloads
    fun animateColor(target: Int, durationMillis: Int = 220, easing: Easing = Easing.EASE_OUT): Int =
        opus.core.animateColor(target, durationMillis, easing)

    @JvmStatic @JvmOverloads
    fun column(content: Runnable, modifier: Modifier = Modifier) = Column(modifier) { content.run() }
    @JvmStatic @JvmOverloads
    fun row(content: Runnable, modifier: Modifier = Modifier) = Row(modifier) { content.run() }
    @JvmStatic @JvmOverloads
    fun box(content: Runnable, modifier: Modifier = Modifier) = Box(modifier) { content.run() }
    @JvmStatic @JvmOverloads
    fun grid(columns: Int, content: Runnable, modifier: Modifier = Modifier, gap: Float = 0f) =
        Grid(columns, modifier, gap) { content.run() }

    @JvmStatic @JvmOverloads
    fun text(text: String, role: TextRole = TextRole.PARAGRAPH, modifier: Modifier = Modifier,
             fontId: String? = null, color: Int = 0xFFFFFFFF.toInt()) = Text(text, role, modifier, fontId, color)
    @JvmStatic @JvmOverloads
    fun button(text: String, onClick: Runnable, modifier: Modifier = Modifier, enabled: Boolean = true) =
        Button(text, { onClick.run() }, modifier, enabled)
    @JvmStatic @JvmOverloads
    fun arrowButton(direction: ArrowDirection, label: String, onClick: Runnable,
                    modifier: Modifier = Modifier, enabled: Boolean = true) =
        ArrowButton(direction, label, { onClick.run() }, modifier, enabled)
    @JvmStatic @JvmOverloads
    fun checkbox(checked: Boolean, onChange: Consumer<Boolean>, modifier: Modifier = Modifier, label: String? = null) =
        Checkbox(checked, { onChange.accept(it) }, modifier, label)
    @JvmStatic @JvmOverloads
    fun slider(value: Float, onChange: Consumer<Float>, modifier: Modifier = Modifier, min: Float = 0f, max: Float = 1f) =
        Slider(value, { onChange.accept(it) }, modifier, min, max)
}
