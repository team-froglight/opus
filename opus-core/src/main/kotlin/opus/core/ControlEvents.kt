package opus.core

import java.time.LocalDate
import java.util.function.Consumer

/** Typed event contract used by renderers. Built-ins populate this at composition time. */
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
    val submit: Runnable? = null
) {
    companion object {
        /** Only the legacy/custom string-map extension boundary needs an erased callback cast. */
        internal fun legacy(node: UiNode): ControlEvents = with(node.props) {
            ControlEvents(callback("onCheckedChange"), callback("onExpandedChange"), callback("onSelect"),
                callback("onSelectionChange"), callback("onPageChange"),
                if (node.type == "Slider") callback("onValueChange") else null,
                if (node.type.endsWith("Field") || node.type == "TextArea") callback("onValueChange") else null,
                callback("onMonthChange"), callback("onDateSelect"), action("onClick"), action("onSubmit"))
        }
        private fun Map<String, Any?>.action(key: String): Runnable? {
            val value = this[key] ?: return null
            require(value is Function0<*>) { "$key must be a zero-argument callback" }
            return Runnable { value.invoke() }
        }
        @Suppress("UNCHECKED_CAST")
        private fun <T> Map<String, Any?>.callback(key: String): Consumer<T>? {
            val value = this[key] ?: return null
            require(value is Function1<*, *>) { "$key must be a one-argument callback" }
            return Consumer { (value as Function1<T, *>).invoke(it) }
        }
    }
}
