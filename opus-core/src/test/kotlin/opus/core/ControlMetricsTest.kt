package opus.core

import kotlin.test.*

class ControlMetricsTest {
    @Test fun optionRowsMapToIndicesWithinTheList() {
        assertEquals(-1, ControlMetrics.optionAt(-0.1f, 3))
        assertEquals(0, ControlMetrics.optionAt(0f, 3))
        assertEquals(0, ControlMetrics.optionAt(17.9f, 3))
        assertEquals(1, ControlMetrics.optionAt(18f, 3))
        assertEquals(2, ControlMetrics.optionAt(53.9f, 3))
        assertEquals(-1, ControlMetrics.optionAt(54f, 3))
        assertEquals(-1, ControlMetrics.optionAt(0f, 0))
    }

    @Test fun paginationActionsTakeAThirdUpToSixtyPixels() {
        assertEquals(30f, ControlMetrics.paginationActionWidth(90f))
        assertEquals(60f, ControlMetrics.paginationActionWidth(400f))
    }

    @Test fun accordionContentStartsBelowTheTitleBar() {
        val root = setContent { Accordion("Title", true, {}) { Text("Body") } }.root
        val padding = root.findAll("Accordion").single().modifier.elements().filterIsInstance<Padding>().single()
        assertEquals(ControlMetrics.ACCORDION_HEADER_HEIGHT, padding.top)
    }
}
