package opus.core

import java.time.LocalDate
import java.util.Locale
import kotlin.test.*

class ControlContractTest {
    @Test fun themesAreScopedCapturedAndExplicitOverridesWin() {
        val outer = Theme(Colors(surface = 1, onSurface = 2, accent = 3), Shapes(4f), Typography(bodySize = 18f))
        val inner = Theme(Colors(surface = 11, onSurface = 12, accent = 13))
        Ui.compose(Runnable {
            Ui.theme(outer, Runnable {
                Ui.text("outer")
                Ui.button("outer", Runnable {})
                Ui.textField("", {}, "Input")
                Ui.surface(Runnable {})
                Ui.theme(inner, Runnable { Ui.text("inner"); Ui.text("explicit", TextRole.LABEL, Modifier, null, 42) })
                Ui.text("restored")
            })
            Ui.text("default")
        }).use { c ->
            val text = c.root.findAll("Text")
            assertEquals(listOf(2, 12, 42, 2, -1), text.map { it.props["color"] })
            assertEquals(18f, text[0].props["textSize"])
            assertEquals(outer, text[0].theme)
            assertEquals(3, c.root.findAll("Button").single().props["accent"])
            val field = c.root.findAll("TextField").single()
            assertNull((field.props["inputOptions"] as InputOptions).accent)
            assertEquals(3, field.theme.colors.accent)
            assertEquals(1, c.root.findAll("Box").single().modifier.elements().filterIsInstance<Background>().single().color)
        }
        assertEquals(Theme(), LocalTheme.current)
    }

    @Test fun choicesSnapshotCallerCollectionsAndExposeTypedCallbacks() {
        val options = mutableListOf("first", "second")
        val selected = mutableSetOf(1)
        var choice = -1
        var result: Set<Int>? = null
        setContent {
            Dropdown(options, -1, { choice = it })
            MultiSelect(options, selected, { result = it })
        }.use { c ->
            options.clear(); selected.clear()
            val dropdown = c.root.findAll("Dropdown").single()
            assertEquals(listOf("first", "second"), dropdown.options)
            dropdown.events.select!!.accept(0)
            assertEquals(0, choice)
            val multi = c.root.findAll("MultiSelect").single()
            assertEquals(setOf(1), multi.selectedOptions)
            val next = mutableSetOf(0)
            multi.events.selectionChange!!.accept(next)
            next.clear()
            assertEquals(setOf(0), result)
        }
    }

    @Test fun invalidControlContractsFailAtCompositionInsteadOfDuringRendering() {
        val invalid: List<() -> Unit> = listOf(
            { Dropdown(emptyList(), 0, {}) },
            { Heading("Too deep", level = 4) },
            { RadioGroup(listOf("one"), 2, {}) },
            { MultiSelect(listOf("one"), setOf(-1), {}) },
            { Slider(Float.NaN, {}) },
            { Slider(0f, {}, min = 1f, max = 0f) },
            { Slider(2f, {}) },
            { Pagination(1, 1, {}) },
            { Pagination(0, 0, {}) },
            { ListColumn(-1) {} },
            { ProgressBar(Float.NaN) }
        )
        invalid.forEach { content -> assertFailsWith<IllegalArgumentException> { setContent(content) } }
        assertFailsWith<IllegalArgumentException> { Shapes(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { Typography(bodySize = 0f) }
    }

    @Test fun datesHaveExplicitTimeLocaleAndTypedEvents() {
        val today = LocalDate.of(2024, 2, 29)
        var selected = ""
        Ui.compose(Runnable { Ui.dateField("", { selected = it }, "Date", Modifier, InputOptions(), today, Locale.UK) }).use { c ->
            val date = c.root.findAll("DateField").single()
            assertEquals(today, date.props["today"])
            assertEquals(Locale.UK, date.props["locale"])
            assertEquals(LocalDate.of(2024, 2, 1), date.props["month"])
            date.events.dateSelect!!.accept(today)
            assertEquals("2024-02-29", selected)
        }
        assertFailsWith<IllegalArgumentException> { setContent { DateField("", {}, "Date", options = InputOptions(maxLength = 5)) } }
    }

    @Test fun customNodesReportInvalidDataAndOnlyExposeTypedEvents() {
        val node = UiNode("Dropdown")
        node.props["options"] = listOf(12)
        assertFailsWith<IllegalArgumentException> { node.options }
        node.props["onSelect"] = { _: Int -> }
        assertSame(ControlEvents.NONE, node.events)
        var chosen = -1
        node.props["events"] = ControlEvents.NONE.withSelect { chosen = it }
        node.events.select!!.accept(1)
        assertEquals(1, chosen)
    }

    @Test fun popupsExposeTheirDismissal() {
        var dismissed = false
        setContent { Popup({ dismissed = true }) { Text("Hi") } }.use { c ->
            c.root.findAll("Popup").single().events.dismiss!!.run()
            assertTrue(dismissed)
        }
    }
}
