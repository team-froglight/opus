@file:JvmName("TextInputs")

package opus.core

import java.time.LocalDate
import java.util.function.Consumer

/**
 * Shared input presentation and constraints. Labels remain visible when a value is entered.
 * A null [accent] follows the theme of the scope the field is composed in. A [dense] single-line field is
 * 20 units tall instead of 28, for toolbars and pickers; a blank label drops the label row entirely.
 */
data class InputOptions @JvmOverloads constructor(
    val placeholder: String = "",
    val helperText: String = "",
    val error: String? = null,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val maxLength: Int = 1024,
    val accent: Int? = null,
    val dense: Boolean = false
) {
    init { require(maxLength >= 0) { "maxLength must be nonnegative" } }
    fun withPlaceholder(value: String) = copy(placeholder = value)
    fun withHelperText(value: String) = copy(helperText = value)
    fun withError(value: String?) = copy(error = value)
    fun withEnabled(value: Boolean) = copy(enabled = value)
    fun withReadOnly(value: Boolean) = copy(readOnly = value)
    fun withMaxLength(value: Int) = copy(maxLength = value)
    fun withAccent(value: Int?) = copy(accent = value)
    fun withDense(value: Boolean) = copy(dense = value)
    /** Hints are idle-state guidance, never painted underneath the active caret. */
    fun showsPlaceholder(value: String, focused: Boolean) = value.isEmpty() && placeholder.isNotEmpty() && !focused
    companion object { @JvmField val DEFAULT = InputOptions() }
}

@Composable @JvmOverloads
fun TextField(value: String, onValueChange: (String) -> Unit, label: String,
              modifier: Modifier = Modifier, options: InputOptions = InputOptions(), onSubmit: (() -> Unit)? = null) =
    input("TextField", value, onValueChange, label, modifier, options, 1, onSubmit)

/** A single text editor with a decorative leading icon inside its border, not another focus target. */
@Composable @JvmOverloads
fun IconTextField(value: String, onValueChange: (String) -> Unit, label: String, leadingIcon: PixelIcon,
                  modifier: Modifier = Modifier, options: InputOptions = InputOptions(), onSubmit: (() -> Unit)? = null) =
    input("TextField", value, onValueChange, label, modifier, options, 1, onSubmit, mapOf("leadingIcon" to leadingIcon))

@Composable @JvmOverloads
fun TextArea(value: String, onValueChange: (String) -> Unit, label: String,
             modifier: Modifier = Modifier, options: InputOptions = InputOptions(), rows: Int = 4) {
    require(rows in 2..100) { "TextArea rows must be between 2 and 100" }
    input("TextArea", value, onValueChange, label, modifier, options, rows, null)
}

/** Controlled decimal draft: preserves empty values, minus signs and trailing decimal points while editing. */
@Composable @JvmOverloads
fun NumberField(value: String, onValueChange: (String) -> Unit, label: String,
                modifier: Modifier = Modifier, options: InputOptions = InputOptions(), onSubmit: (() -> Unit)? = null,
                numbers: NumberOptions = NumberOptions()) {
    require(NumberInput.accepts(value)) { "NumberField value must be a decimal draft" }
    input("NumberField", value, onValueChange, label, modifier, options, 1, onSubmit, mapOf("numberOptions" to numbers))
}

data class NumberOptions @JvmOverloads constructor(val step: Double = 1.0, val min: Double? = null, val max: Double? = null) {
    init {
        require(step.isFinite() && step > 0) { "step must be positive and finite" }
        require(min == null || min.isFinite())
        require(max == null || max.isFinite())
        require(min == null || max == null || min <= max) { "min must not exceed max" }
    }
    fun withStep(value: Double) = copy(step = value)
    fun withMin(value: Double?) = copy(min = value)
    fun withMax(value: Double?) = copy(max = value)
    companion object { @JvmField val DEFAULT = NumberOptions() }
}

object NumberInput {
    private val draft = Regex("-?[0-9]*(\\.[0-9]*)?")
    @JvmStatic fun accepts(value: String): Boolean = draft.matches(value)
    @JvmStatic fun parseOrNull(value: String): Double? =
        if (accepts(value)) value.toDoubleOrNull()?.takeIf { it.isFinite() } else null
    /** Decimal arithmetic avoids floating-point tails; incomplete drafts start from zero. */
    @JvmStatic @JvmOverloads fun step(value: String, direction: Int, options: NumberOptions = NumberOptions()): String {
        require(direction == -1 || direction == 1)
        var result = (if (accepts(value)) value.toBigDecimalOrNull() else null) ?: java.math.BigDecimal.ZERO
        result += java.math.BigDecimal.valueOf(options.step).multiply(java.math.BigDecimal.valueOf(direction.toLong()))
        options.min?.let { result = result.max(java.math.BigDecimal.valueOf(it)) }
        options.max?.let { result = result.min(java.math.BigDecimal.valueOf(it)) }
        return result.stripTrailingZeros().toPlainString()
    }
}

/** ISO date draft with a calendar picker. Empty and incomplete values remain editable. */
@Composable @JvmOverloads
fun DateField(value: String, onValueChange: (String) -> Unit, label: String,
              modifier: Modifier = Modifier, options: InputOptions = InputOptions(),
              today: LocalDate = LocalDate.now(), locale: java.util.Locale = java.util.Locale.getDefault()) {
    require(DateInput.accepts(value)) { "DateField value must be an ISO date draft (yyyy-MM-dd)" }
    require(options.maxLength >= 10) { "DateField maxLength must be at least 10 for a complete ISO date" }
    require(today.year in 1..9999) { "DateField today must be in years 1..9999" }
    composeGroup("DateField") { composer ->
        val expanded = composer.remember { mutableStateOf(false) }
        val month = composer.remember { mutableStateOf((DateInput.parseOrNull(value) ?: today).withDayOfMonth(1)) }
        val onExpanded: (Boolean) -> Unit = { open ->
            if (open) month.value = (DateInput.parseOrNull(value) ?: today).withDayOfMonth(1)
            expanded.value = open
        }
        val onMonth: (LocalDate) -> Unit = { date -> month.value = date.withDayOfMonth(1) }
        val onDate: (LocalDate) -> Unit = { date ->
            require(date.year in 1..9999) { "Selected date must be in years 1..9999" }
            onValueChange(date.toString()); expanded.value = false
        }
        input("DateField", value, onValueChange, label, modifier, options.copy(maxLength = minOf(10, options.maxLength)), 1, null,
            mapOf("expanded" to (expanded.value && options.enabled && !options.readOnly), "month" to month.value,
                "today" to today, "locale" to locale,
                "events" to ControlEvents(expandedChange = Consumer { onExpanded(it) },
                    monthChange = Consumer { onMonth(it) }, dateSelect = Consumer { onDate(it) })))
    }
}

object DateInput {
    private val draft = Regex("[0-9]{0,4}(-[0-9]{0,2}(-[0-9]{0,2})?)?")
    @JvmStatic fun accepts(value: String): Boolean = value.length <= 10 && draft.matches(value)
    @JvmStatic fun parseOrNull(value: String): LocalDate? = try {
        if (value.length == 10) LocalDate.parse(value).takeIf { it.year in 1..9999 } else null
    } catch (_: java.time.DateTimeException) { null }
    @JvmStatic fun calendarStart(month: LocalDate): LocalDate =
        month.withDayOfMonth(1).minusDays((month.withDayOfMonth(1).dayOfWeek.value - 1).toLong())
    const val CALENDAR_HEIGHT = 184f
    const val CALENDAR_GAP = 8f
}

object InputMetrics {
    const val LABEL_HEIGHT = 14f
    const val FIELD_HEIGHT = 28f
    const val CONTENT_INSET = 8
    const val ICON_GAP = 7
    /** Vanilla glyph cells are 8 units tall: 7 rows of capitals and x-height letters, then a descender row. */
    const val GLYPH_HEIGHT = 8
    /** The rows the eye reads as the text's body; labels center this band, not the whole cell. */
    const val CAP_HEIGHT = 7
    const val TEXT_OFFSET_Y = 0
    /** Editors sit at whole units, so they round the band's top down and paint [TEXT_NUDGE] lower. */
    const val TEXT_TOP = (FIELD_HEIGHT.toInt() - CAP_HEIGHT) / 2 + TEXT_OFFSET_Y
    const val TEXT_NUDGE = (FIELD_HEIGHT - CAP_HEIGHT) / 2f - TEXT_TOP

    /** Top of the glyph cell that centers a label's cap band in a box, in units of [scale]d glyphs. */
    @JvmStatic @JvmOverloads fun labelTop(boxTop: Float, boxHeight: Float, scale: Float = 1f): Float =
        boxTop + (boxHeight - CAP_HEIGHT * scale) / 2f
    @JvmStatic fun textInset(hasLeadingIcon: Boolean): Int =
        CONTENT_INSET + if (hasLeadingIcon) PixelIcon.SIZE + ICON_GAP else 0
    @JvmStatic fun textWidth(width: Int, hasLeadingIcon: Boolean, hasTrailingControl: Boolean): Int =
        (width - textInset(hasLeadingIcon) - CONTENT_INSET - if (hasTrailingControl) 24 else 0).coerceAtLeast(8)
    const val DENSE_FIELD_HEIGHT = 20f
    @JvmStatic @JvmOverloads fun fieldHeight(rows: Int, dense: Boolean = false): Float =
        if (rows > 1) rows * 9f + 16f else if (dense) DENSE_FIELD_HEIGHT else FIELD_HEIGHT
    /** The label row above a field, or nothing when the label is blank. */
    @JvmStatic fun labelHeight(label: String?): Float = if (label.isNullOrBlank()) 0f else LABEL_HEIGHT
    /** Whole-unit top of a single-line editor's text in a field of [fieldHeight]; see [textNudge]. */
    @JvmStatic fun textTop(fieldHeight: Float): Int = ((fieldHeight - CAP_HEIGHT) / 2f).toInt() + TEXT_OFFSET_Y
    /** The half unit an editor paints below [textTop] so its cap band is exactly centered. */
    @JvmStatic fun textNudge(fieldHeight: Float): Float = (fieldHeight - CAP_HEIGHT) / 2f - textTop(fieldHeight)
    @JvmStatic @JvmOverloads fun height(rows: Int, options: InputOptions, hasLabel: Boolean = true): Float =
        (if (hasLabel) LABEL_HEIGHT else 0f) + fieldHeight(rows, options.dense) +
        if (options.helperText.isNotEmpty() || options.error != null) 14f else 0f
}

private val textInputTypes = setOf("TextField", "TextArea", "NumberField", "DateField")

/** Whether [node] was emitted by a text-input composable ([TextField], [TextArea], [NumberField], [DateField]). */
fun isTextInput(node: UiNode): Boolean = node.type in textInputTypes

private fun input(type: String, value: String, onValueChange: (String) -> Unit, label: String,
                  modifier: Modifier, options: InputOptions, rows: Int, onSubmit: (() -> Unit)?, extra: Map<String, Any?> = emptyMap()) {
    val normalized = if (rows > 1) value.replace("\r\n", "\n").replace('\r', '\n') else value.replace("\r", "").replace("\n", "")
    emit(type, modifier, mapOf("value" to TextEdits.limit(normalized, options.maxLength), "label" to label,
        "inputOptions" to options, "rows" to rows, "enabled" to options.enabled) + extra +
        mapOf("events" to ((extra["events"] as? ControlEvents) ?: ControlEvents.NONE).copy(
            textChange = Consumer { onValueChange(it) }, submit = onSubmit?.let { Runnable { it() } })))
}
