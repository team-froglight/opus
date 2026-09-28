@file:JvmName("Components")

package opus.core

// ---- Text ----

enum class TextRole { HEADING_1, HEADING_2, HEADING_3, PARAGRAPH, LABEL }

@Composable
@JvmOverloads
fun Text(text: String, role: TextRole = TextRole.PARAGRAPH, modifier: Modifier = Modifier, fontId: String? = null, color: Int = 0xFFFFFFFF.toInt()) {
    emit("Text", modifier, mapOf("text" to text, "role" to role, "fontId" to fontId, "color" to color))
}

@Composable
@JvmOverloads
fun Heading(text: String, level: Int = 1, modifier: Modifier = Modifier) {
    val role = when (level) {
        1 -> TextRole.HEADING_1
        2 -> TextRole.HEADING_2
        else -> TextRole.HEADING_3
    }
    Text(text, role, modifier)
}

@Composable
@JvmOverloads
fun Paragraph(text: String, modifier: Modifier = Modifier) {
    Text(text, TextRole.PARAGRAPH, modifier)
}

@Composable
@JvmOverloads
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, TextRole.LABEL, modifier)
}

// ---- Button ----

@Composable
@JvmOverloads
fun Button(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Int = 0xFF7C5CFF.toInt()) {
    buttonNode(text, onClick, modifier, enabled, accent, null)
}

enum class ArrowDirection { LEFT, RIGHT }

/** A shader-drawn navigation arrow with a descriptive label for host accessibility/tooltip support. */
@Composable
@JvmOverloads
fun ArrowButton(direction: ArrowDirection, label: String, onClick: () -> Unit,
                modifier: Modifier = Modifier, enabled: Boolean = true, accent: Int = 0xFF7C5CFF.toInt()) {
    buttonNode(label, onClick, modifier.size(26f, 24f), enabled, accent, direction)
}

private fun buttonNode(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean,
                       accent: Int, arrow: ArrowDirection?) {
    val composer = Composition.currentComposer ?: error("Button outside composition")
    composer.startGroup("Button")
    try {
        val color = animateColor(if (enabled) accent else 0xFF3A3A44.toInt(), 160)
        emit("Button", modifier.padding(horizontal = 12f, vertical = 6f).clickable(onClick),
            mapOf("text" to text, "enabled" to enabled, "accent" to color, "arrowDirection" to arrow))
    } finally { composer.endGroup() }
}

// ---- Selection controls ----

@Composable
@JvmOverloads
fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null, accent: Int = 0xFF7C5CFF.toInt()) {
    emit("Checkbox", modifier, mapOf("checked" to checked, "onCheckedChange" to onCheckedChange, "label" to label, "accent" to accent))
}

@Composable
@JvmOverloads
fun RadioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, accent: Int = 0xFF7C5CFF.toInt()) {
    emit("RadioButton", modifier, mapOf("selected" to selected, "onClick" to onClick, "label" to label, "accent" to accent))
}

@Composable
@JvmOverloads
fun RadioGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Int = 0xFF7C5CFF.toInt()
) {
    emit("RadioGroup", modifier, mapOf("options" to options, "selectedIndex" to selectedIndex, "onSelect" to onSelect, "accent" to accent))
}

@Composable
@JvmOverloads
fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, accent: Int = 0xFF7C5CFF.toInt()) {
    val composer = Composition.currentComposer ?: error("Switch outside composition")
    composer.startGroup("Switch")
    try {
        val position = animateFloat(if (checked) 1f else 0f, 180)
        val trackColor = animateColor(if (checked) accent else 0xFF3A3A44.toInt(), 180)
        emit("Switch", modifier, mapOf("checked" to checked, "onCheckedChange" to onCheckedChange,
            "accent" to accent, "position" to position, "trackColor" to trackColor))
    } finally {
        composer.endGroup()
    }
}

@Composable
@JvmOverloads
fun Toggle(value: Boolean, onValueChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(value, onValueChange, modifier)
}

// ---- Ranges ----

@Composable
@JvmOverloads
fun Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, min: Float = 0f, max: Float = 1f, accent: Int = 0xFF7C5CFF.toInt()) {
    emit("Slider", modifier, mapOf("value" to value, "onValueChange" to onValueChange, "min" to min, "max" to max, "accent" to accent))
}

@Composable
@JvmOverloads
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, accent: Int = 0xFF7C5CFF.toInt()) {
    emit("ProgressBar", modifier, mapOf("progress" to progress.coerceIn(0f, 1f), "accent" to accent))
}

// ---- Media / items ----

@Composable
@JvmOverloads
fun Image(textureId: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    emit("Image", modifier, mapOf("textureId" to textureId, "contentDescription" to contentDescription))
}

@Composable
@JvmOverloads
fun ItemSlot(itemId: String, count: Int = 1, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val mod = if (onClick != null) modifier.clickable(onClick) else modifier
    emit("ItemSlot", mod, mapOf("itemId" to itemId, "count" to count))
}

// ---- Overlays ----

@Composable
@JvmOverloads
fun Tooltip(tip: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Tooltip", modifier, mapOf("tip" to tip), content)
}

@Composable
@JvmOverloads
fun Popup(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Popup", modifier, mapOf("onDismiss" to onDismiss), content)
}

// ---- Disclosure ----

@Composable
@JvmOverloads
fun Accordion(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Title bar occupies the top 18px; children start below it.
    emit("Accordion", modifier.paddingEach(top = 18f), mapOf("title" to title, "expanded" to expanded, "onExpandedChange" to onExpandedChange), content)
}

@Composable
@JvmOverloads
fun Dropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val composer = Composition.currentComposer ?: error("Dropdown outside composition")
    composer.startGroup("Dropdown")
    try {
        val progress = animateFloat(if (expanded) 1f else 0f, 180, Easing.EASE_OUT)
        emit(
            "Dropdown", modifier,
            mapOf(
                "options" to options,
                "selectedIndex" to selectedIndex,
                "onSelect" to onSelect,
                "expanded" to expanded,
                "expansion" to progress,
                "onExpandedChange" to onExpandedChange
            )
        )
    } finally { composer.endGroup() }
}

@Composable
@JvmOverloads
fun MultiSelect(
    options: List<String>,
    selected: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    accent: Int = 0xFF7C5CFF.toInt()
) {
    emit("MultiSelect", modifier, mapOf("options" to options, "selected" to selected, "onSelectionChange" to onSelectionChange, "accent" to accent))
}

// ---- Navigation / lists ----

@Composable
@JvmOverloads
fun Pagination(page: Int, pageCount: Int, onPageChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    emit("Pagination", modifier, mapOf("page" to page, "pageCount" to pageCount, "onPageChange" to onPageChange))
}

@Composable
@JvmOverloads
fun ListColumn(
    itemCount: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int) -> Unit
) {
    emit("ListColumn", modifier, mapOf("itemCount" to itemCount)) {
        for (i in 0 until itemCount) itemContent(i)
    }
}

/**
 * Compatibility alias for [ListColumn]. Rendering culls offscreen nodes, but
 * list items are composed eagerly because no viewport is available here.
 */
@Deprecated("Items are composed eagerly; use ListColumn for accurate naming", ReplaceWith("ListColumn(itemCount, modifier, itemContent)"))
@Composable
@JvmOverloads
@Suppress("UNUSED_PARAMETER")
fun LazyColumn(
    itemCount: Int,
    modifier: Modifier = Modifier,
    overscan: Int = 3,
    itemContent: @Composable (Int) -> Unit
) {
    ListColumn(itemCount, modifier, itemContent)
}
