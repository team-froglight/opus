package opus.core

import java.util.function.Consumer
import java.util.function.Supplier

/** Java entry points using standard Java callbacks. Kotlin callers can use the composable DSL. */
object Ui {
    @JvmStatic @JvmOverloads
    fun dateField(value: String, onChange: Consumer<String>, label: String, modifier: Modifier = Modifier,
                  options: InputOptions = InputOptions(), today: java.time.LocalDate = java.time.LocalDate.now(),
                  locale: java.util.Locale = java.util.Locale.getDefault()) = DateField(value, { onChange.accept(it) }, label, modifier, options, today, locale)
    @JvmStatic @JvmOverloads
    fun textField(value: String, onChange: Consumer<String>, label: String, modifier: Modifier = Modifier,
                  options: InputOptions = InputOptions(), onSubmit: Runnable? = null) =
        TextField(value, { onChange.accept(it) }, label, modifier, options, onSubmit?.let { { it.run() } })
    @JvmStatic @JvmOverloads
    fun iconTextField(value: String, onChange: Consumer<String>, label: String, leadingIcon: PixelIcon,
                      modifier: Modifier = Modifier, options: InputOptions = InputOptions(), onSubmit: Runnable? = null) =
        IconTextField(value, { onChange.accept(it) }, label, leadingIcon, modifier, options, onSubmit?.let { { it.run() } })
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
                           transition: ViewTransition = ViewTransition.SLIDE_VERTICAL, durationMillis: Int = Motion.VIEW) =
        AnimatedContent(target, modifier, transition, durationMillis) { content.accept(it) }
    @JvmStatic @JvmOverloads
    fun animatedVisibility(visible: Boolean, content: Runnable, modifier: Modifier = Modifier,
                           transition: ViewTransition = ViewTransition.SLIDE_VERTICAL, durationMillis: Int = Motion.STANDARD) =
        AnimatedVisibility(visible, modifier, transition, durationMillis) { content.run() }
    @JvmStatic @JvmOverloads
    fun transition(modifier: Modifier, durationMillis: Int = Motion.STANDARD, easing: Easing = Easing.EASE_OUT): Modifier =
        modifier.animateChanges(durationMillis, easing)
    @JvmStatic fun compose(content: Runnable): Composition = setContent { content.run() }
    @JvmStatic fun clickable(modifier: Modifier, onClick: Runnable): Modifier = modifier.clickable { onClick.run() }
    @JvmStatic fun onScroll(modifier: Modifier, onScroll: Consumer<Float>): Modifier = modifier.onScroll { onScroll.accept(it) }
    @JvmStatic fun <T> state(initial: T): MutableState<T> = mutableStateOf(initial)
    @JvmStatic fun <T> remember(factory: Supplier<T>): T = opus.core.remember { factory.get() }
    @JvmStatic @JvmOverloads
    fun animateFloat(target: Float, durationMillis: Int = Motion.STANDARD, easing: Easing = Easing.EASE_OUT): Float =
        opus.core.animateFloat(target, durationMillis, easing)
    @JvmStatic @JvmOverloads
    fun animateColor(target: Int, durationMillis: Int = Motion.STANDARD, easing: Easing = Easing.EASE_OUT): Int =
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
             fontId: String? = null, color: Int = LocalTheme.current.colors.onSurface) = Text(text, role, modifier, fontId, color)
    @JvmStatic @JvmOverloads
    fun button(text: String, onClick: Runnable, modifier: Modifier = Modifier, enabled: Boolean = true,
               accent: Int = LocalTheme.current.colors.accent) = Button(text, { onClick.run() }, modifier, enabled, accent)
    @JvmStatic @JvmOverloads
    fun arrowButton(direction: ArrowDirection, label: String, onClick: Runnable,
                    modifier: Modifier = Modifier, enabled: Boolean = true, accent: Int = LocalTheme.current.colors.accent) =
        ArrowButton(direction, label, { onClick.run() }, modifier, enabled, accent)
    @JvmStatic @JvmOverloads
    fun icon(icon: PixelIcon, description: String? = null, modifier: Modifier = Modifier.size(12f, 12f),
             color: Int = LocalTheme.current.colors.onSurface) = Icon(icon, description, modifier, color)
    @JvmStatic @JvmOverloads
    fun iconButton(icon: PixelIcon, label: String, onClick: Runnable, modifier: Modifier = Modifier.size(28f, 28f),
                   enabled: Boolean = true, accent: Int = LocalTheme.current.colors.surface) =
        IconButton(icon, label, { onClick.run() }, modifier, enabled, accent)
    @JvmStatic @JvmOverloads
    fun checkbox(checked: Boolean, onChange: Consumer<Boolean>, modifier: Modifier = Modifier, label: String? = null,
                 accent: Int = LocalTheme.current.colors.accent) = Checkbox(checked, { onChange.accept(it) }, modifier, label, accent)
    @JvmStatic @JvmOverloads
    fun slider(value: Float, onChange: Consumer<Float>, modifier: Modifier = Modifier, min: Float = 0f, max: Float = 1f,
               accent: Int = LocalTheme.current.colors.accent) = Slider(value, { onChange.accept(it) }, modifier, min, max, accent)

    @JvmStatic fun theme(theme: Theme, content: Runnable) = Theme(theme) { content.run() }
    @JvmStatic @JvmOverloads
    fun surface(content: Runnable, modifier: Modifier = Modifier): Unit = LocalTheme.current.let { theme ->
        Box(modifier.background(theme.colors.surface, theme.shapes.cornerRadius)) { content.run() }
    }
    @JvmStatic @JvmOverloads
    fun switchControl(checked: Boolean, onChange: Consumer<Boolean>, modifier: Modifier = Modifier,
                      accent: Int = LocalTheme.current.colors.accent) = Switch(checked, { onChange.accept(it) }, modifier, accent)
    @JvmStatic @JvmOverloads
    fun radioButton(selected: Boolean, onClick: Runnable, modifier: Modifier = Modifier, label: String? = null,
                    accent: Int = LocalTheme.current.colors.accent) = RadioButton(selected, { onClick.run() }, modifier, label, accent)
    @JvmStatic @JvmOverloads
    fun radioGroup(options: List<String>, selectedIndex: Int, onSelect: Consumer<Int>, modifier: Modifier = Modifier,
                   accent: Int = LocalTheme.current.colors.accent) = RadioGroup(options, selectedIndex, { onSelect.accept(it) }, modifier, accent)
    @JvmStatic @JvmOverloads
    fun dropdown(options: List<String>, selectedIndex: Int, onSelect: Consumer<Int>, modifier: Modifier = Modifier,
                 placeholder: String = "", enabled: Boolean = true, accent: Int = LocalTheme.current.colors.accent) =
        Dropdown(options, selectedIndex, { onSelect.accept(it) }, modifier, placeholder, enabled, accent)
    @JvmStatic @JvmOverloads
    fun multiSelect(options: List<String>, selected: Set<Int>, onChange: Consumer<Set<Int>>, modifier: Modifier = Modifier,
                    accent: Int = LocalTheme.current.colors.accent) = MultiSelect(options, selected, { onChange.accept(it) }, modifier, accent)
    @JvmStatic @JvmOverloads
    fun pagination(page: Int, pageCount: Int, onChange: Consumer<Int>, modifier: Modifier = Modifier,
                   previousLabel: String = "< Prev", nextLabel: String = "Next >") =
        Pagination(page, pageCount, { onChange.accept(it) }, modifier, previousLabel, nextLabel)
    @JvmStatic @JvmOverloads
    fun accordion(title: String, expanded: Boolean, onChange: Consumer<Boolean>, content: Runnable, modifier: Modifier = Modifier) =
        Accordion(title, expanded, { onChange.accept(it) }, modifier) { content.run() }
    @JvmStatic @JvmOverloads
    fun tooltip(tip: String, content: Runnable, modifier: Modifier = Modifier) = Tooltip(tip, modifier) { content.run() }
    @JvmStatic @JvmOverloads
    fun popup(onDismiss: Runnable, content: Runnable, modifier: Modifier = Modifier) = Popup({ onDismiss.run() }, modifier) { content.run() }
    @JvmStatic @JvmOverloads
    fun image(textureId: String, modifier: Modifier = Modifier, description: String? = null) = Image(textureId, modifier, description)
    @JvmStatic @JvmOverloads
    fun itemSlot(itemId: String, count: Int = 1, modifier: Modifier = Modifier, onClick: Runnable? = null, framed: Boolean = true) =
        ItemSlot(itemId, count, modifier, onClick?.let { { it.run() } }, framed)
    @JvmStatic @JvmOverloads
    fun progressBar(progress: Float, modifier: Modifier = Modifier, accent: Int = LocalTheme.current.colors.accent) =
        ProgressBar(progress, modifier, accent)
    @JvmStatic @JvmOverloads
    fun listColumn(itemCount: Int, itemContent: Consumer<Int>, modifier: Modifier = Modifier) =
        ListColumn(itemCount, modifier) { itemContent.accept(it) }
}
