@file:JvmName("Components")

package opus.core

import java.util.Collections
import java.util.function.Consumer

// ---- Text ----

enum class TextRole { HEADING_1, HEADING_2, HEADING_3, PARAGRAPH, LABEL }

@Composable
@JvmOverloads
fun Text(text: String, role: TextRole = TextRole.PARAGRAPH, modifier: Modifier = Modifier, fontId: String? = null, color: Int = LocalTheme.current.colors.onSurface) {
    emit("Text", modifier, mapOf("text" to text, "role" to role, "fontId" to fontId, "color" to color, "textSize" to LocalTheme.current.typography.sizeFor(role)))
}

/** [level] 1 to 3 maps to [TextRole.HEADING_1] to [TextRole.HEADING_3]. */
@Composable
@JvmOverloads
fun Heading(text: String, level: Int = 1, modifier: Modifier = Modifier) {
    val role = when (level) {
        1 -> TextRole.HEADING_1
        2 -> TextRole.HEADING_2
        3 -> TextRole.HEADING_3
        else -> throw IllegalArgumentException("Heading level must be 1, 2 or 3")
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
    composeGroup("Button") {
        val color = animateColor(if (enabled) accent else LocalTheme.current.colors.inactive, Motion.FAST)
        emit("Button", modifier.padding(horizontal = 12f, vertical = 6f).clickable(onClick),
            mapOf("text" to text, "enabled" to enabled, "accent" to color, "arrowDirection" to arrow, "icon" to icon))
    }
}

// ---- Selection controls ----

@Composable
@JvmOverloads
fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null, accent: Int = LocalTheme.current.colors.accent) {
    emit("Checkbox", modifier, mapOf("checked" to checked, "label" to label, "accent" to accent,
        "events" to ControlEvents(checkedChange = Consumer { onCheckedChange(it) })))
}

@Composable
@JvmOverloads
fun RadioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, accent: Int = LocalTheme.current.colors.accent) {
    emit("RadioButton", modifier, mapOf("selected" to selected, "label" to label, "accent" to accent,
        "events" to ControlEvents(click = Runnable { onClick() })))
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
    emit("RadioGroup", modifier, mapOf("options" to snapshot(options), "selectedIndex" to selectedIndex, "accent" to accent,
        "events" to ControlEvents(select = Consumer { onSelect(it) })))
}

@Composable
@JvmOverloads
fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, accent: Int = LocalTheme.current.colors.accent) {
    composeGroup("Switch") {
        val position = animateFloat(if (checked) 1f else 0f)
        val trackColor = animateColor(if (checked) accent else LocalTheme.current.colors.inactive)
        emit("Switch", modifier, mapOf("checked" to checked, "accent" to accent, "position" to position, "trackColor" to trackColor,
            "events" to ControlEvents(checkedChange = Consumer { onCheckedChange(it) })))
    }
}

// ---- Ranges ----

@Composable
@JvmOverloads
fun Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, min: Float = 0f, max: Float = 1f, accent: Int = LocalTheme.current.colors.accent) {
    require(min.isFinite() && max.isFinite() && min < max) { "Slider requires finite min < max" }
    require(value.isFinite() && value in min..max) { "Slider value must be finite and in min..max" }
    emit("Slider", modifier, mapOf("value" to value, "min" to min, "max" to max, "accent" to accent,
        "events" to ControlEvents(floatChange = Consumer { onValueChange(it) })))
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
/** An item in a slot. Without a [framed] background the item sits directly on its container, such as a menu row. */
fun ItemSlot(itemId: String, count: Int = 1, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, framed: Boolean = true) {
    val mod = if (onClick != null) modifier.clickable(onClick) else modifier
    emit("ItemSlot", mod, mapOf("itemId" to itemId, "count" to count, "framed" to framed))
}

// ---- Overlays ----

@Composable
@JvmOverloads
fun Tooltip(tip: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Tooltip", modifier, mapOf("tip" to tip), content)
}

/**
 * Inline content that asks to close on Escape or a click outside it.
 * [onDismiss] decides whether it actually leaves the composition.
 */
@Composable
@JvmOverloads
fun Popup(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    emit("Popup", modifier, mapOf("events" to ControlEvents(dismiss = Runnable { onDismiss() })), content)
}

// ---- Disclosure ----

@Composable
@JvmOverloads
fun Accordion(title: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Children start below the title bar.
    emit("Accordion", modifier.paddingEach(top = ControlMetrics.ACCORDION_HEADER_HEIGHT),
        mapOf("title" to title, "expanded" to expanded, "events" to ControlEvents(expandedChange = Consumer { onExpandedChange(it) })), content)
}

/**
 * A single choice whose menu floats over the following content. The dropdown owns its open
 * state: clicks, Enter/Space or the arrow keys open it; a choice, Escape, Tab or an outside click
 * closes it. [placeholder] shows while [selectedIndex] is -1.
 */
@Composable
@JvmOverloads
fun Dropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    accent: Int = LocalTheme.current.colors.accent
) {
    validateSelection(options, selectedIndex)
    composeGroup("Dropdown") { composer ->
        val open = composer.remember { mutableStateOf(false) }
        val expanded = open.value && enabled && options.isNotEmpty()
        // Menus open with the standard entrance and close quickly, accelerating away.
        val progress = if (expanded) animateFloat(1f) else animateFloat(0f, Motion.exitDuration(Motion.STANDARD), Easing.EASE_IN)
        emit("Dropdown", modifier, mapOf(
            "options" to snapshot(options), "selectedIndex" to selectedIndex, "placeholder" to placeholder,
            "enabled" to enabled, "accent" to accent, "expanded" to expanded, "expansion" to progress,
            "events" to ControlEvents(select = Consumer { onSelect(it) }, expandedChange = Consumer { open.value = it })))
    }
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
    emit("MultiSelect", modifier, mapOf("options" to snapshot(options), "selected" to Collections.unmodifiableSet(selected.toSet()), "accent" to accent,
        "events" to ControlEvents(selectionChange = Consumer { onSelectionChange(Collections.unmodifiableSet(it.toSet())) })))
}

// ---- Navigation / lists ----

@Composable
@JvmOverloads
fun Pagination(page: Int, pageCount: Int, onPageChange: (Int) -> Unit, modifier: Modifier = Modifier,
               previousLabel: String = "< Prev", nextLabel: String = "Next >") {
    require(pageCount >= 1 && page in 0 until pageCount) { "Pagination uses zero-based page in 0 until pageCount, pageCount >= 1" }
    emit("Pagination", modifier, mapOf("page" to page, "pageCount" to pageCount, "previousLabel" to previousLabel, "nextLabel" to nextLabel,
        "events" to ControlEvents(pageChange = Consumer { onPageChange(it) })))
}

/** Composes every item eagerly; rendering culls the ones outside the viewport. */
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

/** -1 represents no selection; all other indices must exist, including for an empty list. */
private fun validateSelection(options: List<String>, selectedIndex: Int) {
    require(selectedIndex == -1 || selectedIndex in options.indices) { "selectedIndex must be -1 or an existing option index" }
}

private fun snapshot(options: List<String>): List<String> = Collections.unmodifiableList(options.toList())
