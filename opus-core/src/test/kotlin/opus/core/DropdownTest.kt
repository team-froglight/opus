package opus.core

import kotlin.test.*

class DropdownTest {
    @Test fun paddingAndGapDoNotSelectOptions() {
        assertEquals(-1, DropdownMetrics.optionAt(25f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(30f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(33.9f, 4))
        assertEquals(0, DropdownMetrics.optionAt(34f, 4))
        assertEquals(0, DropdownMetrics.optionAt(55.9f, 4))
        assertEquals(1, DropdownMetrics.optionAt(56f, 4))
        assertEquals(3, DropdownMetrics.optionAt(121.9f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(122f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(34f, 0))
    }

    @Test fun openingReversesWithoutJumpingAndHonorsReducedMotion() {
        val expanded = mutableStateOf(false)
        val c = setContent { Dropdown(listOf("Apple", "Pear"), 0, {}, expanded.value, {}) }
        fun progress() = c.root.children.single().props["expansion"] as Float
        c.advanceAnimations(0)
        assertEquals(0f, progress())
        expanded.value = true
        c.advanceAnimations(90_000_000)
        val halfway = progress()
        assertTrue(halfway > 0 && halfway < 1)
        expanded.value = false
        assertEquals(halfway, progress())
        c.advanceAnimations(270_000_000)
        assertEquals(0f, progress())
        c.animationDurationScale = 0f
        expanded.value = true
        assertEquals(1f, progress())
        assertFalse(c.hasRunningAnimations)
        c.close()
    }
}
