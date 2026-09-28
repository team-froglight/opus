package opus.yoga

import opus.core.Column
import opus.core.Button
import opus.core.Grid
import opus.core.Heading
import opus.core.UiNode
import opus.core.Row
import opus.core.Text
import opus.core.fillMaxWidth
import opus.core.height
import opus.core.Modifier
import opus.core.padding
import opus.core.paddingEach
import opus.core.setContent
import opus.core.weight
import opus.core.width
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val fakeMeasurer = object : TextMeasurer {
    override fun measure(text: String, role: opus.core.TextRole, maxWidth: Float): Pair<Float, Float> =
        Pair(text.length * 6f, 12f)
    override fun lineHeight(role: opus.core.TextRole): Float = 12f
}

class YogaLayoutTest {
    @Test fun inputSupportWrapsWithoutOverlappingFollowingContent() {
        setContent {
            Column {
                opus.core.TextField("", {}, "Name", Modifier.fillMaxWidth(), opus.core.InputOptions(error = "A longer error that must wrap safely"))
                opus.core.TextArea("", {}, "Notes", Modifier.fillMaxWidth())
                Text("After the inputs")
            }
        }.use { c ->
            YogaTree(fakeMeasurer).use { tree ->
                tree.layout(c.root, c, 120f, 400f)
                val nodes = c.root.children.single().children
                assertTrue(nodes[0].height > opus.core.InputMetrics.height(1, opus.core.InputOptions(error = "Error")))
                assertEquals(nodes[0].height, nodes[1].y, 0.01f)
                assertTrue(nodes[2].y >= nodes[1].y + 66f)
            }
        }
    }
    @Test
    fun dropdownReservesAnimatedMenuAndBottomPadding() {
        val expanded = opus.core.mutableStateOf(false)
        val c = setContent {
            Column {
                opus.core.Dropdown(listOf("Apple", "Pear"), 0, {}, expanded.value, {}, Modifier.fillMaxWidth())
                Text("Following content")
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            fun checkLayout(expectedHeight: Float) {
                tree.layout(c.root, c, 200f, 200f)
                val children = c.root.children.single().children
                assertEquals(expectedHeight, children[0].height, 0.01f)
                assertEquals(expectedHeight, children[1].y, 0.01f)
            }
            c.advanceAnimations(0)
            checkLayout(26f)
            expanded.value = true
            c.advanceAnimations(90_000_000)
            checkLayout(75f)
            c.advanceAnimations(180_000_000)
            checkLayout(82f)
            expanded.value = false
            c.advanceAnimations(360_000_000)
            checkLayout(26f)
        }
        c.close()
    }

    @Test
    fun animatedSizeUpdatesLayoutAndFollowingSiblings() {
        val target = opus.core.mutableStateOf(20f)
        val composition = setContent {
            Column {
                opus.core.Box(Modifier.height(opus.core.animateFloat(target.value, 100, opus.core.Easing.LINEAR))) {}
                Text("follows")
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            composition.advanceAnimations(0)
            tree.layout(composition.root, composition, 200f, 200f)
            target.value = 60f
            composition.advanceAnimations(50_000_000)
            tree.layout(composition.root, composition, 200f, 200f)
            val children = composition.root.children.single().children
            assertEquals(40f, children[0].height)
            assertEquals(40f, children[1].y)
            composition.advanceAnimations(100_000_000)
            tree.layout(composition.root, composition, 200f, 200f)
            assertEquals(60f, composition.root.children.single().children[1].y)
        }
        composition.close()
    }

    @Test
    fun overfullColumnReservesPaintedTextAndButtonHeight() {
        val composition = setContent {
            Column {
                repeat(8) {
                    Heading("Section $it", level = 3, modifier = Modifier.paddingEach(top = 14f, bottom = 4f))
                    Button("Action $it", onClick = {})
                }
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(composition.root, composition, 200f, 100f)
            val children = composition.root.children.single().children
            val heading = children[0]
            val button = children[1]
            assertTrue(heading.height >= 30f, "heading height=${heading.height}")
            assertTrue(button.height >= 24f, "button height=${button.height}")
            assertTrue(children.last().y + children.last().height > 100f,
                "overfull content was constrained to the viewport")
            children.zipWithNext { current, next ->
                assertTrue(next.y >= current.y + current.height,
                    "${current.type} at ${current.y}+${current.height} overlaps ${next.type} at ${next.y}")
            }
        }
        composition.close()
    }

    @Test
    fun columnStacksVertically() {
        val c = setContent {
            Column {
                Text("ab", modifier = Modifier.height(10f))
                Text("cdef", modifier = Modifier.height(20f))
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(c.root, c, 200f, 100f)
            val col = c.root.children[0]
            assertEquals(200f, col.width)
            val (a, b) = col.children
            assertEquals(0f, a.y)
            assertEquals(10f, b.y)
            assertEquals(10f, a.height)
            assertEquals(20f, b.height)
        }
    }

    @Test
    fun rowWeightsShareWidth() {
        val c = setContent {
            Row(modifier = Modifier.width(300f).height(50f)) {
                Text("a", modifier = Modifier.weight(1f))
                Text("b", modifier = Modifier.weight(2f))
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(c.root, c, 300f, 50f)
            val row = c.root.children[0]
            val (a, b) = row.children
            assertTrue(a.width > 90f && a.width < 110f, "a.width=${a.width}")
            assertTrue(b.width > 190f && b.width < 210f, "b.width=${b.width}")
        }
    }

    @Test
    fun paddingApplies() {
        val c = setContent {
            Column(modifier = Modifier.width(100f).height(100f).padding(10f)) {
                Text("x")
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(c.root, c, 100f, 100f)
            val text = c.root.children[0].children[0]
            assertEquals(10f, text.x)
            assertEquals(10f, text.y)
        }
    }

    @Test
    fun textMeasured() {
        val c = setContent {
            Column { Text("hello") } // measured 30 wide, stretched to 200 by default align
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(c.root, c, 200f, 100f)
            val text = c.root.children[0].children[0]
            assertEquals(200f, text.width)
            assertEquals(12f, text.height)
        }
    }

    @Test
    fun textIntrinsicWidthWithoutStretch() {
        val c = setContent {
            Column {
                Row(modifier = Modifier.width(200f).height(20f)) {
                    Text("hello") // 5 chars * 6 = 30; row does not stretch children on main axis
                }
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(c.root, c, 200f, 100f)
            val text = c.root.children[0].children[0].children[0]
            assertEquals(30f, text.width)
            assertEquals(20f, text.height) // stretched to row height on cross axis
        }
    }

    @Test
    fun nodeCoordinatesAreAbsolute() {
        val c = setContent {
            Column(modifier = Modifier.width(200f).height(200f)) {
                Row(modifier = Modifier.height(50f)) {
                    Text("hi")
                }
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(c.root, c, 200f, 200f)
            val text = c.root.children[0].children[0].children[0]
            assertEquals(text.x, c.root.children[0].x + c.root.children[0].children[0].x + 0f)
        }
    }

    @Test
    fun explicitComposeRebuildsNativeLayout() {
        var text = "short"
        val composition = setContent {
            Row(modifier = Modifier.width(200f)) { Text(text) }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(composition.root, composition, 200f, 100f)
            val first = composition.root.findAll("Text").single()
            assertEquals(30f, first.width)

            text = "a longer label"
            composition.compose()
            tree.layout(composition.root, composition, 200f, 100f)
            val second = composition.root.findAll("Text").single()
            assertEquals(84f, second.width)
        }
        composition.close()
    }

    @Test
    fun gridKeepsRequestedColumnCountWithGap() {
        val composition = setContent {
            Grid(3, modifier = Modifier.width(300f), gap = 6f) {
                repeat(6) { Text("cell") }
            }
        }
        YogaTree(fakeMeasurer).use { tree ->
            tree.layout(composition.root, composition, 300f, 100f)
            val cells = composition.root.children.single().children
            assertEquals(cells[0].y, cells[1].y)
            assertEquals(cells[0].y, cells[2].y)
            assertTrue(cells[3].y > cells[0].y)
            assertEquals(cells[3].y, cells[5].y)
        }
        composition.close()
    }
}
