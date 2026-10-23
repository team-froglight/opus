package opus.core

import kotlin.test.*

class DropdownTest {
    @Test fun menuPaddingDoesNotSelectOptions() {
        assertEquals(-1, DropdownMetrics.optionAt(0f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(3.9f, 4))
        assertEquals(0, DropdownMetrics.optionAt(4f, 4))
        assertEquals(0, DropdownMetrics.optionAt(25.9f, 4))
        assertEquals(1, DropdownMetrics.optionAt(26f, 4))
        assertEquals(3, DropdownMetrics.optionAt(91.9f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(92f, 4))
        assertEquals(-1, DropdownMetrics.optionAt(4f, 0))
        assertEquals(96f, DropdownMetrics.menuHeight(4))
    }

    @Test fun dropdownOwnsItsOpenStateAndKeepsSelectionControlled() {
        var chosen = -1
        setContent { Dropdown(listOf("Apple", "Pear"), -1, { chosen = it }, placeholder = "Pick a fruit") }.use { c ->
            fun node() = c.root.children.single()
            assertEquals(false, node().props["expanded"])
            assertEquals("Pick a fruit", node().props["placeholder"])
            node().events.expandedChange!!.accept(true)
            assertEquals(true, node().props["expanded"])
            node().events.select!!.accept(1)
            assertEquals(1, chosen)
            assertEquals(-1, node().props["selectedIndex"])
        }
    }

    @Test fun disabledDropdownsNeverOpen() {
        setContent { Dropdown(listOf("Apple"), 0, {}, enabled = false) }.use { c ->
            val node = c.root.children.single()
            assertEquals(false, node.props["enabled"])
            node.events.expandedChange!!.accept(true)
            assertEquals(false, c.root.children.single().props["expanded"])
        }
    }

    @Test fun openingReversesWithoutJumpingAndHonorsReducedMotion() {
        val c = setContent { Dropdown(listOf("Apple", "Pear"), 0, {}) }
        fun node() = c.root.children.single()
        fun progress() = node().props["expansion"] as Float
        fun open(value: Boolean) = node().events.expandedChange!!.accept(value)
        c.advanceAnimations(0)
        assertEquals(0f, progress())
        open(true)
        c.advanceAnimations(90_000_000)
        val halfway = progress()
        assertTrue(halfway > 0 && halfway < 1)
        open(false)
        assertEquals(halfway, progress())
        c.advanceAnimations(270_000_000)
        assertEquals(0f, progress())
        c.animationDurationScale = 0f
        open(true)
        assertEquals(1f, progress())
        assertFalse(c.hasRunningAnimations)
        c.close()
    }
}
