package opus.core

import kotlin.test.*

class TransitionTest {
    private fun Composition.frame(ms: Long) = advanceAnimations(ms * 1_000_000)
    private fun Composition.label() = root.findAll("Text").single().props["text"]
    private fun Composition.opacity() = root.children.single().modifier.elements().filterIsInstance<Opacity>().single().alpha

    @Test fun exitsOldViewBeforeEnteringNewViewAndDisablesInput() {
        val page = mutableStateOf("A")
        val c = setContent { AnimatedContent(page.value, durationMillis = 200, easing = Easing.LINEAR) { Text(it) } }
        c.frame(0)
        page.value = "B"
        assertEquals("A", c.label())
        assertFalse(c.root.children.single().modifier.elements().filterIsInstance<InputEnabled>().single().enabled)
        c.frame(50)
        assertEquals(0.5f, c.opacity())
        assertEquals("A", c.label())
        c.frame(100)
        assertEquals("B", c.label())
        assertEquals(0f, c.opacity())
        c.frame(150)
        assertEquals(0.5f, c.opacity())
        c.frame(200)
        assertEquals(1f, c.opacity())
        assertTrue(c.root.children.single().modifier.elements().filterIsInstance<InputEnabled>().single().enabled)
        assertFalse(c.hasRunningAnimations)
        c.close()
    }

    @Test fun rapidNavigationCoalescesAndReversalsPreservePosition() {
        val page = mutableStateOf("A")
        val c = setContent { AnimatedContent(page.value, durationMillis = 200, easing = Easing.LINEAR) { Text(it) } }
        c.frame(0)
        page.value = "B"
        c.frame(40)
        val before = c.root.children.single().modifier.elements().filterIsInstance<Offset>().single()
        page.value = "A"
        assertEquals(before, c.root.children.single().modifier.elements().filterIsInstance<Offset>().single())
        page.value = "C"
        page.value = "D"
        c.frame(140)
        assertEquals("D", c.label())
        c.frame(240)
        assertEquals(1f, c.opacity())
        c.close()
    }

    @Test fun visibilityRetainsExitContentThenReleasesState() {
        val shown = mutableStateOf(true)
        var initialized = 0
        val c = setContent {
            AnimatedVisibility(shown.value, durationMillis = 100, easing = Easing.LINEAR) {
                Text("${remember { ++initialized }}")
            }
        }
        c.frame(0)
        shown.value = false
        c.frame(50)
        assertEquals("1", c.label())
        assertEquals(0.5f, c.opacity())
        shown.value = true
        assertEquals(0.5f, c.opacity())
        c.frame(150)
        shown.value = false
        c.frame(250)
        assertTrue(c.root.children.isEmpty())
        assertFalse(c.hasRunningAnimations)
        shown.value = true
        assertEquals("2", c.label())
        c.frame(350)
        assertEquals(1f, c.opacity())
        c.close()
    }

    @Test fun viewContentGetsFreshSlotsWithDifferentStateTypes() {
        val page = mutableStateOf(false)
        val c = setContent {
            AnimatedContent(page.value, durationMillis = 100) { second ->
                if (second) Text(remember { 42 }.toString()) else Text(remember { "first" })
            }
        }
        c.frame(0)
        page.value = true
        c.frame(50)
        assertEquals("42", c.label())
        c.frame(100)
        page.value = false
        c.frame(150)
        assertEquals("first", c.label())
        c.close()
    }

    @Test fun reducedMotionAndZeroDurationSwapImmediately() {
        val page = mutableStateOf("A")
        val c = setContent { AnimatedContent(page.value) { Text(it) } }
        c.animationDurationScale = 0f
        page.value = "B"
        assertEquals("B", c.label())
        assertEquals(1f, c.opacity())
        assertFalse(c.hasRunningAnimations)
        c.close()
        val instant = setContent { AnimatedContent(page.value, durationMillis = 0) { Text(it) } }
        page.value = "C"
        assertEquals("C", instant.label())
        assertFalse(instant.hasRunningAnimations)
        instant.close()
    }

    @Test fun styleTransitionsBatchPropertiesWithoutRestartingWhenChainChanges() {
        val expanded = mutableStateOf(false)
        val border = mutableStateOf(false)
        var style: Modifier = Modifier
        val c = setContent {
            val target = Modifier.width(if (expanded.value) 100f else 20f)
                .background(if (expanded.value) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 8f)
                .padding(if (expanded.value) 12f else 4f)
            style = (if (border.value) target.border(1f, -1) else target).animateChanges(100, Easing.LINEAR)
            Box(style) {}
        }
        c.frame(0)
        expanded.value = true
        c.frame(50)
        assertEquals(60f, style.elements().filterIsInstance<FixedSize>().single().width)
        assertEquals(0xFF808080.toInt(), style.elements().filterIsInstance<Background>().single().color)
        assertEquals(8f, style.elements().filterIsInstance<Padding>().single().start)
        border.value = true
        c.frame(100)
        assertEquals(100f, style.elements().filterIsInstance<FixedSize>().single().width)
        assertFalse(c.hasRunningAnimations)
        c.close()
    }

    @Test fun removedRememberSlotsDoNotRetainOldValues() {
        val show = mutableStateOf(true)
        var creations = 0
        val c = setContent { if (show.value) Box { remember { ++creations } } }
        show.value = false
        show.value = true
        assertEquals(2, creations)
        c.close()
    }

    @Test fun javaTransitionEntryPointsCompose() {
        Ui.compose {
            Ui.animatedContent("page", { Ui.text(it) })
            Ui.animatedVisibility(true, { Ui.text("visible") })
            Ui.box({}, Ui.transition(Modifier.width(32f)))
        }.use { assertEquals(listOf("page", "visible"), it.root.findAll("Text").map { n -> n.props["text"] }) }
    }
}
