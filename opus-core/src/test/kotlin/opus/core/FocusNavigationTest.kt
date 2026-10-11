package opus.core

import kotlin.test.*

class FocusNavigationTest {
    @Test fun disabledContainersBlockDescendantFocusAndClicks() {
        setContent {
            emit("Box", props = mapOf("enabled" to false)) { Button("Blocked", {}) }
            Button("Available", {})
        }.use { c ->
            val visuals = VisualTree(c.root)
            val blocked = c.root.findAll("Button").first()
            assertFalse(visuals.bounds(blocked)!!.inputEnabled)
            assertEquals(listOf("Available"), FocusNavigation.targets(c.root, visuals).map { it.props["text"] })
        }
    }

    @Test fun traversesVisibleEnabledControlsInOrderAndWrapsBothWays() {
        setContent {
            TextField("", {}, "Name")
            Button("Disabled", {}, enabled = false)
            TextField("Hidden", {}, "Disabled field", options = InputOptions(enabled = false))
            Column(Modifier.inputEnabled(false)) { Button("Blocked", {}) }
            Column(Modifier.opacity(0f)) { Button("Invisible", {}) }
            Accordion("Details", false, {}) { Button("Collapsed child", {}) }
            TextField("Copy me", {}, "Read only", options = InputOptions(readOnly = true))
        }.use { c ->
            val targets = FocusNavigation.targets(c.root, VisualTree(c.root))
            assertEquals(listOf("TextField", "Accordion", "TextField"), targets.map { it.type })
            assertSame(targets.first(), FocusNavigation.next(targets, null, false))
            assertSame(targets.last(), FocusNavigation.next(targets, null, true))
            assertSame(targets.first(), FocusNavigation.next(targets, targets.last().identity, false))
            assertSame(targets.last(), FocusNavigation.next(targets, targets.first().identity, true))
        }
    }

    @Test fun vanishedFocusRecoversAndEmptyChoicesAreSkipped() {
        setContent {
            MultiSelect(emptyList(), emptySet(), {})
            Button("Available", {})
        }.use { c ->
            val targets = FocusNavigation.targets(c.root, VisualTree(c.root))
            assertEquals(1, targets.size)
            assertSame(targets.single(), FocusNavigation.next(targets, "removed", false))
            assertNull(FocusNavigation.next(emptyList(), "removed", false))
        }
    }
}
