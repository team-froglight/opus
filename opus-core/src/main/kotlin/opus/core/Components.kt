@file:JvmName("Components")

package opus.core

// ---- Text ----

enum class TextRole { HEADING_1, HEADING_2, HEADING_3, PARAGRAPH, LABEL }

@Composable
@JvmOverloads
fun Text(text: String, role: TextRole = TextRole.PARAGRAPH, modifier: Modifier = Modifier, fontId: String? = null, color: Int = LocalTheme.current.colors.onSurface) {
    emit("Text", modifier, mapOf("text" to text, "role" to role, "fontId" to fontId, "color" to color, "textSize" to LocalTheme.current.typography.sizeFor(role)))
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
fun Button(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Int = LocalTheme.current.colors.accent) {
    buttonNode(text, onClick, modifier, enabled, accent, null)
}

enum class ArrowDirection { LEFT, RIGHT }

/** A pixel-grid navigation arrow with a descriptive label for host accessibility/tooltip support. */
@Composable
@JvmOverloads
fun ArrowButton(direction: ArrowDirection, label: String, onClick: () -> Unit,
                modifier: Modifier = Modifier, enabled: Boolean = true, accent: Int = LocalTheme.current.colors.accent) {
    buttonNode(label, onClick, modifier.size(26f, 24f), enabled, accent, direction)
}

@Composable @JvmOverloads
fun Icon(icon: PixelIcon, contentDescription: String? = null, modifier: Modifier = Modifier.size(12f, 12f),
         color: Int = LocalTheme.current.colors.onSurface) {
    emit("Icon", modifier, mapOf("icon" to icon, "contentDescription" to contentDescription, "color" to color))
}

@Composable @JvmOverloads
fun IconButton(icon: PixelIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier.size(28f, 28f),
               enabled: Boolean = true, accent: Int = LocalTheme.current.colors.surface) {
    require(label.isNotBlank()) { "IconButton requires a descriptive label" }
    Tooltip(label) { buttonNode(label, onClick, modifier, enabled, accent, null, icon) }
}

private fun buttonNode(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean,
                       accent: Int, arrow: ArrowDirection?, icon: PixelIcon? = null) {
    val composer = Composition.currentComposer ?: error("Button outside composition")
    composer.startGroup("Button")
    try {
        val color = animateColor(if (enabled) accent else 0xFF3A3A44.toInt(), 160)
        emit("Button", modifier.padding(horizontal = 12f, vertical = 6f).clickable(onClick),
            mapOf("text" to text, "enabled" to enabled, "accent" to color, "arrowDirection" to arrow, "icon" to icon))
    } finally { composer.endGroup() }
}

// ---- Selection controls ----

@Composable
@JvmOverloads
fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null, accent: Int = LocalTheme.current.colors.accent) {
    emit("Checkbox", modifier, mapOf("checked" to checked, "onCheckedChange" to onCheckedChange, "events" to ControlEvents(checkedChange = java.util.function.Consumer { onCheckedChange(it) }), "label" to label, "accent" to accent))
}

@Composable
@JvmOverloads
fun RadioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, accent: Int = LocalTheme.current.colors.accent) {
    emit("RadioButton", modifier, mapOf("selected" to selected, "onClick" to onClick, "events" to ControlEvents(click = Runnable { onClick() }), "label" to label, "accent" to accent))
}

@Composable
@JvmOverloads
fun RadioGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Int = LocalTheme.current.colors.accent
) {
    validateSelection(options, selectedIndex)
    emit("RadioGroup", modifier, mapOf("options" to java.util.Collections.unmodifiableList(options.toList()), "selectedIndex" to selectedIndex, "onSelect" to onSelect, "events" to ControlEvents(select = java.util.function.Consumer { onSelect(it) }), "accent" to accent))
}

@Composable
@JvmOverloads
fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, accent: Int = LocalTheme.current.colors.accent) {
    val composer = Composition.currentComposer ?: error("Switch outside composition")
    composer.startGroup("Switch")
    try {
        val position = animateFloat(if (checked) 1f else 0f, 180)
        val trackColor = animateColor(if (checked) accent else 0xFF3A3A44.toInt(), 180)
        emit("Switch", modifier, mapOf("checked" to checked, "onCheckedChange" to onCheckedChange, "events" to ControlEvents(checkedChange = java.util.function.Consumer { onCheckedChange(it) }),
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
fun Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, min: Float = 0f, max: Float = 1f, accent: Int = LocalTheme.current.colors.accent) {
    require(min.isFinite() && max.isFinite() && min < max) { "Slider requires finite min < max" }
    require(value.isFinite() && value in min..max) { "Slider value must be finite and in min..max" }
    emit("Slider", modifier, mapOf("value" to value, "onValueChange" to onValueChange, "events" to ControlEvents(floatChange = java.util.function.Consumer { onValueChange(it) }), "min" to min, "max" to max, "accent" to accent))
}

@Composable
@JvmOverloads
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, accent: Int = LocalTheme.current.colors.accent) {
    require(progress.isFinite()) { "ProgressBar progress must be finite" }
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
    emit("Accordion", modifier.paddingEach(top = 18f), mapOf("title" to title, "expanded" to expanded, "onExpandedChange" to onExpandedChange, "events" to ControlEvents(expandedChange = java.util.function.Consumer { onExpandedChange(it) })), content)
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
    validateSelection(options, selectedIndex)
    val composer = Composition.currentComposer ?: error("Dropdown outside composition")
    composer.startGroup("Dropdown")
    try {
        val progress = animateFloat(if (expanded) 1f else 0f, 180, Easing.EASE_OUT)
        emit(
            "Dropdown", modifier,
            mapOf(
                "options" to java.util.Collections.unmodifiableList(options.toList()),
                "selectedIndex" to selectedIndex,
                "onSelect" to onSelect, "events" to ControlEvents(select = java.util.function.Consumer { onSelect(it) }, expandedChange = java.util.function.Consumer { onExpandedChange(it) }),
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
    accent: Int = LocalTheme.current.colors.accent
) {
    require(selected.all { it in options.indices }) { "MultiSelect selected indices must exist in options" }
    emit("MultiSelect", modifier, mapOf("options" to java.util.Collections.unmodifiableList(options.toList()), "selected" to java.util.Collections.unmodifiableSet(selected.toSet()), "onSelectionChange" to onSelectionChange, "events" to ControlEvents(selectionChange = java.util.function.Consumer { onSelectionChange(java.util.Collections.unmodifiableSet(it.toSet())) }), "accent" to accent))
}

// ---- Navigation / lists ----

@Composable
@JvmOverloads
fun Pagination(page: Int, pageCount: Int, onPageChange: (Int) -> Unit, modifier: Modifier = Modifier,
               previousLabel: String = "< Prev", nextLabel: String = "Next >") {
    require(pageCount >= 1 && page in 0 until pageCount) { "Pagination uses zero-based page in 0 until pageCount, pageCount >= 1" }
    emit("Pagination", modifier, mapOf("page" to page, "pageCount" to pageCount, "onPageChange" to onPageChange, "events" to ControlEvents(pageChange = java.util.function.Consumer { onPageChange(it) }),
        "previousLabel" to previousLabel, "nextLabel" to nextLabel))
}

@Composable
@JvmOverloads
fun ListColumn(
    itemCount: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int) -> Unit
) {
    require(itemCount >= 0) { "ListColumn itemCount must be nonnegative" }
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

/** -1 represents no selection; all other indices must exist, including for an empty list. */
private fun validateSelection(options: List<String>, selectedIndex: Int) {
    require(selectedIndex == -1 || selectedIndex in options.indices) { "selectedIndex must be -1 or an existing option index" }
}
