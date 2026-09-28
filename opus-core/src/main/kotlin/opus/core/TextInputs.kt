@file:JvmName("TextInputs")

package opus.core

import java.time.LocalDate

/** Shared input presentation and constraints. Labels remain visible when a value is entered. */
data class InputOptions @JvmOverloads constructor(
    val placeholder: String = "",
    val helperText: String = "",
    val error: String? = null,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val maxLength: Int = 1024,
    val accent: Int = 0xFF5DD2BD.toInt()
) {
    init { require(maxLength >= 0) { "maxLength must be nonnegative" } }
    fun withPlaceholder(value: String) = copy(placeholder = value)
    fun withHelperText(value: String) = copy(helperText = value)
    fun withError(value: String?) = copy(error = value)
    fun withEnabled(value: Boolean) = copy(enabled = value)
    fun withReadOnly(value: Boolean) = copy(readOnly = value)
    fun withMaxLength(value: Int) = copy(maxLength = value)
    fun withAccent(value: Int) = copy(accent = value)
    companion object { @JvmField val DEFAULT = InputOptions() }
}

@Composable @JvmOverloads
fun TextField(value: String, onValueChange: (String) -> Unit, label: String,
              modifier: Modifier = Modifier, options: InputOptions = InputOptions(), onSubmit: (() -> Unit)? = null) =
    input("TextField", value, onValueChange, label, modifier, options, 1, onSubmit)

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
              modifier: Modifier = Modifier, options: InputOptions = InputOptions()) {
    require(DateInput.accepts(value)) { "DateField value must be an ISO date draft (yyyy-MM-dd)" }
    val composer = Composition.currentComposer ?: error("DateField outside composition")
    composer.startGroup("DateField")
    try {
        val expanded = composer.remember { mutableStateOf(false) }
        val month = composer.remember { mutableStateOf((DateInput.parseOrNull(value) ?: LocalDate.now()).withDayOfMonth(1)) }
        input("DateField", value, onValueChange, label, modifier, options.copy(maxLength = minOf(10, options.maxLength)), 1, null,
            mapOf("expanded" to (expanded.value && options.enabled && !options.readOnly), "month" to month.value,
                "onExpandedChange" to { open: Boolean ->
                    if (open) month.value = (DateInput.parseOrNull(value) ?: LocalDate.now()).withDayOfMonth(1)
                    expanded.value = open
                },
                "onMonthChange" to { date: LocalDate -> month.value = date.withDayOfMonth(1) },
                "onDateSelect" to { date: LocalDate -> onValueChange(date.toString()); expanded.value = false }))
    } finally { composer.endGroup() }
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
    @JvmStatic fun fieldHeight(rows: Int): Float = if (rows <= 1) FIELD_HEIGHT else rows * 9f + 16f
    @JvmStatic fun height(rows: Int, options: InputOptions): Float = LABEL_HEIGHT + fieldHeight(rows) +
        if (options.helperText.isNotEmpty() || options.error != null) 14f else 0f
}

private fun input(type: String, value: String, onValueChange: (String) -> Unit, label: String,
                  modifier: Modifier, options: InputOptions, rows: Int, onSubmit: (() -> Unit)?, extra: Map<String, Any?> = emptyMap()) {
    val normalized = if (rows > 1) value.replace("\r\n", "\n").replace('\r', '\n') else value.replace("\r", "").replace("\n", "")
    emit(type, modifier, mapOf("value" to TextEdits.limit(normalized, options.maxLength), "onValueChange" to onValueChange, "label" to label,
        "inputOptions" to options, "rows" to rows, "enabled" to options.enabled, "onSubmit" to onSubmit) + extra)
}
