package opus.core

import kotlin.test.*

class VisualTreeTest {
    @Test fun descendantsInheritOffsetsOpacityAndMatchingHitCoordinates() {
        val root = UiNode("Root")
        val parent = UiNode("Box").apply { x = 10f; y = 10f; width = 50f; height = 50f; modifier = Modifier.offset(20f, 30f).opacity(0.5f) }
        val child = UiNode("Button").apply { x = 15f; y = 15f; width = 10f; height = 10f; modifier = Modifier.offset(2f, 3f).opacity(0.5f) }
        root.children += parent
        parent.children += child
        val tree = VisualTree(root)
        assertEquals(VisualBounds(37f, 48f, 0.25f, true), tree.bounds(child))
        assertEquals(listOf(child, parent), tree.hitPath(40f, 50f))
        assertTrue(tree.hitPath(16f, 16f).isEmpty())
    }

    @Test fun outgoingContentBlocksInputAndHiddenContentCannotBeClicked() {
        val root = UiNode("Root")
        val behind = UiNode("Button").apply { width = 100f; height = 100f }
        val outgoing = UiNode("Box").apply { width = 100f; height = 100f; modifier = Modifier.inputEnabled(false) }
        val child = UiNode("Button").apply { width = 100f; height = 100f }
        outgoing.children += child
        root.children += listOf(behind, outgoing)
        assertEquals(listOf(outgoing), VisualTree(root).hitPath(10f, 10f))
        outgoing.modifier = outgoing.modifier.opacity(0f)
        assertEquals(listOf(behind), VisualTree(root).hitPath(10f, 10f))
        outgoing.modifier = Modifier
        outgoing.visible = false
        assertFalse(VisualTree(root).bounds(child)!!.inputEnabled)
    }

    @Test fun identitiesSurviveRecompositionButChangeWithViewLifetime() {
        val page = mutableStateOf(false)
        val c = setContent { AnimatedContent(page.value, durationMillis = 0) { Slider(0f, {}) } }
        val old = c.root.findAll("Slider").single().identity!!
        c.compose()
        assertNotNull(VisualTree(c.root).find(old))
        page.value = true
        assertNull(VisualTree(c.root).find(old))
        c.close()
    }
}
