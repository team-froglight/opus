package opus.core

import java.time.LocalDate
import java.util.function.Consumer

/**
 * Typed callbacks a renderer invokes on a node, stored in its `events` prop.
 * Built-in controls populate this at composition time; custom nodes start from [NONE].
 */
data class ControlEvents(
    val checkedChange: Consumer<Boolean>? = null,
    val expandedChange: Consumer<Boolean>? = null,
    val select: Consumer<Int>? = null,
    val selectionChange: Consumer<Set<Int>>? = null,
    val pageChange: Consumer<Int>? = null,
    val floatChange: Consumer<Float>? = null,
    val textChange: Consumer<String>? = null,
    val monthChange: Consumer<LocalDate>? = null,
    val dateSelect: Consumer<LocalDate>? = null,
    val click: Runnable? = null,
    val submit: Runnable? = null,
    val dismiss: Runnable? = null
) {
    fun withCheckedChange(value: Consumer<Boolean>?) = copy(checkedChange = value)
    fun withExpandedChange(value: Consumer<Boolean>?) = copy(expandedChange = value)
    fun withSelect(value: Consumer<Int>?) = copy(select = value)
    fun withSelectionChange(value: Consumer<Set<Int>>?) = copy(selectionChange = value)
    fun withPageChange(value: Consumer<Int>?) = copy(pageChange = value)
    fun withFloatChange(value: Consumer<Float>?) = copy(floatChange = value)
    fun withTextChange(value: Consumer<String>?) = copy(textChange = value)
    fun withMonthChange(value: Consumer<LocalDate>?) = copy(monthChange = value)
    fun withDateSelect(value: Consumer<LocalDate>?) = copy(dateSelect = value)
    fun withClick(value: Runnable?) = copy(click = value)
    fun withSubmit(value: Runnable?) = copy(submit = value)
    fun withDismiss(value: Runnable?) = copy(dismiss = value)

    companion object {
        @JvmField val NONE = ControlEvents()
    }
}
