package opus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComposerTest {
    @Test
    fun emitsTree() {
        val c = setContent {
            Column {
                Heading("Hi")
                Button("Ok", onClick = {})
            }
        }
        assertEquals(1, c.root.children.size)
        val col = c.root.children[0]
        assertEquals("Column", col.type)
        assertEquals(listOf("Text", "Button"), col.children.map { it.type })
        assertEquals("Hi", col.children[0].props["text"])
    }

    @Test
    fun stateChangeRecomposes() {
        val checked = mutableStateOf(false)
        var recomps = 0
        val c = setContent {
            val v: Boolean by checked
            Checkbox(v, onCheckedChange = { checked.value = it })
        }
        c.onRecomposed = { recomps++ }
        assertEquals(false, c.root.findAll("Checkbox")[0].props["checked"])
        checked.value = true
        assertEquals(true, c.root.findAll("Checkbox")[0].props["checked"])
        assertEquals(1, recomps)
        assertEquals(1, c.recomposeCount)
    }

    @Test
    fun rememberSurvivesRecompose() {
        val tick = mutableStateOf(0)
        var inits = 0
        setContent {
            val t: Int by tick
            Composition.currentComposer?.remember("k") {
                inits++
                "v$t"
            }
        }
        tick.value = 1
        assertEquals(1, inits)
    }

    @Test
    fun remembersDistinctValuesAroundChildGroupsIncludingNull() {
        val refresh = mutableStateOf(0)
        val initialized = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        val composition = setContent {
            refresh.value
            val composer = Composition.currentComposer!!
            values += composer.remember { initialized += "before"; "first" }
            Column {
                values += Composition.currentComposer!!.remember { initialized += "child"; "nested" }
            }
            values += composer.remember { initialized += "after"; "second" }
            values += composer.remember { initialized += "null"; null }
        }

        refresh.value = 1
        assertEquals(listOf("before", "child", "after", "null"), initialized)
        assertEquals(
            listOf<Any?>("first", "nested", "second", null, "first", "nested", "second", null),
            values
        )
        composition.close()
    }

    @Test
    fun nestedAndFailedCompositionsRestoreOuterContext() {
        val outer = setContent {
            Text("before")
            val nested = setContent { Text("inside") }
            assertEquals("inside", nested.root.findAll("Text").single().props["text"])
            nested.close()
            Text("after")
        }
        assertEquals(listOf("before", "after"), outer.root.findAll("Text").map { it.props["text"] })
        outer.close()

        assertFailsWith<IllegalStateException> {
            setContent {
                Column { error("failed content") }
            }
        }
        val next = setContent { Text("recovered") }
        assertEquals("recovered", next.root.findAll("Text").single().props["text"])
        next.close()
    }

    @Test
    fun removedStateSubscriptionsDoNotRecompose() {
        val showFirst = mutableStateOf(true)
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val composition = setContent {
            if (showFirst.value) Text("${first.value}") else Text("${second.value}")
        }

        showFirst.value = false
        val countAfterSwitch = composition.recomposeCount
        first.value = 3
        assertEquals(countAfterSwitch, composition.recomposeCount)
        second.value = 4
        assertEquals(countAfterSwitch + 1, composition.recomposeCount)
        composition.close()
        second.value = 5
        assertEquals(countAfterSwitch + 1, composition.recomposeCount)
    }

    @Test
    fun explicitComposeReplacesTreeAndAdvancesVersion() {
        var label = "first"
        val composition = setContent { Text(label) }
        val original = composition.root
        label = "second"
        composition.compose()
        assertEquals(2, composition.compositionVersion)
        assertEquals(0, composition.recomposeCount)
        assertTrue(composition.root !== original)
        assertEquals("second", composition.root.findAll("Text").single().props["text"])
        composition.close()
    }

    @Test
    fun cullsNodesOutsideViewport() {
        val n = UiNode("Box").apply {
            x = 0f; y = 0f; width = 100f; height = 100f
        }
        val inside = UiNode("Text").apply {
            x = 10f; y = 10f; width = 20f; height = 10f
        }
        val outside = UiNode("Text").apply {
            x = 500f; y = 500f; width = 20f; height = 10f
        }
        n.children += inside
        n.children += outside
        val seen = mutableListOf<String>()
        n.visitVisible(0f, 0f, 200f, 200f) { seen += it.type + "@${it.x}" }
        assertTrue(seen.any { it == "Text@10.0" })
        assertTrue(seen.none { it == "Text@500.0" })
    }
}
