package opus.core

import kotlin.test.*

class TextInputsTest {
    @Test fun numberStepsUseExactDecimalsAndRespectBounds() {
        val options = NumberOptions(0.1, 0.0, 1.0)
        assertEquals("0.3", NumberInput.step("0.2", 1, options))
        assertEquals("0.1", NumberInput.step("0.2", -1, options))
        assertEquals("1", NumberInput.step("0.95", 1, options))
        assertEquals("0", NumberInput.step("0", -1, options))
        assertEquals("0.1", NumberInput.step("-", 1, options))
        assertEquals("1000000000000000001", NumberInput.step("1000000000000000000", 1))
        assertFailsWith<IllegalArgumentException> { NumberOptions(step = 0.0) }
        assertFailsWith<IllegalArgumentException> { NumberOptions(min = 2.0, max = 1.0) }
        assertFailsWith<IllegalArgumentException> { NumberOptions(step = Double.NaN) }
        Ui.compose(Runnable { Ui.numberField("25", {}, "Amount", Modifier, InputOptions(), null, NumberOptions(1.0, 0.0, 100.0)) }).use { c ->
            assertEquals(NumberOptions(1.0, 0.0, 100.0), c.root.children.single().props["numberOptions"])
        }
    }
    @Test fun limitsPreserveSurrogatePairsAndAccountForSelectedReplacement() {
        assertEquals("A", TextEdits.limit("A😀B", 2))
        assertEquals("A😀", TextEdits.limit("A😀B", 3))
        assertEquals("new", TextEdits.insertion("new", 10, 10, 10))
        assertEquals("", TextEdits.insertion("new", 10, 0, 3))
        val limit = mutableStateOf(10)
        setContent { TextArea("1234567890", {}, "Notes", options = InputOptions(maxLength = limit.value)) }.use { c ->
            limit.value = 3
            assertEquals("123", c.root.children.single().props["value"])
        }
    }

    @Test fun datesValidateLeapDaysAndCalendarAlignment() {
        assertTrue(DateInput.accepts("2026-0"))
        assertFalse(DateInput.accepts("September 27"))
        assertEquals(java.time.LocalDate.of(2024, 2, 29), DateInput.parseOrNull("2024-02-29"))
        assertNull(DateInput.parseOrNull("2026-02-29"))
        assertNull(DateInput.parseOrNull("2026-13-01"))
        assertNull(DateInput.parseOrNull("2026-09"))
        val month = java.time.LocalDate.of(2026, 9, 1)
        assertEquals(java.time.LocalDate.of(2026, 8, 31), DateInput.calendarStart(month))
        setContent { DateField("", {}, "Date") }.use { c ->
            @Suppress("UNCHECKED_CAST")
            (c.root.children.single().props["onExpandedChange"] as (Boolean) -> Unit)(true)
            assertEquals(true, c.root.children.single().props["expanded"])
        }
    }
    @Test fun controlledTextRecomposesWithoutChangingIdentity() {
        val value = mutableStateOf("")
        setContent { TextField(value.value, { value.value = it }, "Name") }.use { c ->
            val original = c.root.children.single()
            @Suppress("UNCHECKED_CAST")
            (original.props["onValueChange"] as (String) -> Unit)("Привіт")
            assertEquals("Привіт", c.root.children.single().props["value"])
            assertEquals(original.identity, c.root.children.single().identity)
        }
    }

    @Test fun numericDraftsAllowEditingButOnlyCompleteFiniteNumbersParse() {
        for (draft in listOf("", "-", ".", "-.", "1.", "-.25", "001.2")) assertTrue(NumberInput.accepts(draft), draft)
        for (bad in listOf("1..2", "hello", "NaN", "Infinity", "1e3", "1,2", "1\n2")) assertFalse(NumberInput.accepts(bad), bad)
        assertNull(NumberInput.parseOrNull("-"))
        assertNull(NumberInput.parseOrNull("9".repeat(400)))
        assertEquals(-0.25, NumberInput.parseOrNull("-.25"))
    }

    @Test fun javaOptionsAndSubmitCallbackReachTheNode() {
        var submitted = false
        val options = InputOptions.DEFAULT.withPlaceholder("Name").withMaxLength(40).withError("Required")
        Ui.compose(Runnable {
            Ui.textField("", {}, "Display name", Modifier, options, Runnable { submitted = true })
            Ui.textArea("", {}, "Notes")
            Ui.numberField("-", {}, "Amount")
        }).use { c ->
            assertEquals(listOf("TextField", "TextArea", "NumberField"), c.root.children.map { it.type })
            assertEquals(options, c.root.children[0].props["inputOptions"])
            @Suppress("UNCHECKED_CAST")
            (c.root.children[0].props["onSubmit"] as () -> Unit)()
            assertTrue(submitted)
        }
        assertEquals("", InputOptions.DEFAULT.placeholder)
        assertFailsWith<IllegalArgumentException> { InputOptions(maxLength = -1) }
        assertFailsWith<IllegalArgumentException> { setContent { TextArea("", {}, "Notes", rows = 1) } }
    }
}
