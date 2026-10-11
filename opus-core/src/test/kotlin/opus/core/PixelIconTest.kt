package opus.core

import kotlin.test.*

class PixelIconTest {
    @Test fun everySymbolUsesANonemptyDistinctNinePixelGrid() {
        val patterns = PixelIcon.entries.map { icon ->
            (0 until PixelIcon.SIZE).flatMap { y -> (0 until PixelIcon.SIZE).map { x -> icon.pixelAt(x, y) } }
        }
        assertEquals(9, PixelIcon.SIZE)
        assertTrue(patterns.all { it.any { cell -> cell } })
        assertEquals(patterns.size, patterns.distinct().size)
        assertFailsWith<IllegalArgumentException> { PixelIcon.CHECK.pixelAt(-1, 0) }
        assertFailsWith<IllegalArgumentException> { PixelIcon.CHECK.pixelAt(0, 9) }
    }

    @Test fun NavigationDirectionsAreExactMirrors() {
        for (y in 0 until PixelIcon.SIZE) for (x in 0 until PixelIcon.SIZE) {
            assertEquals(PixelIcon.ARROW_LEFT.pixelAt(x, y), PixelIcon.ARROW_RIGHT.pixelAt(8 - x, y))
            assertEquals(PixelIcon.CHEVRON_LEFT.pixelAt(x, y), PixelIcon.CHEVRON_RIGHT.pixelAt(8 - x, y))
            assertEquals(PixelIcon.CHEVRON_UP.pixelAt(x, y), PixelIcon.CHEVRON_DOWN.pixelAt(x, 8 - y))
        }
    }

    @Test fun IconsAndLabeledButtonsExposeTypedPropertiesAndCallbacks() {
        var clicked = false
        Ui.compose(Runnable {
            Ui.icon(PixelIcon.COIN, "Coins")
            Ui.iconButton(PixelIcon.CLOSE, "Close", Runnable { clicked = true })
        }).use { composition ->
            assertEquals(PixelIcon.COIN, composition.root.children[0].props["icon"])
            val button = composition.root.children[1].children.single()
            assertEquals(PixelIcon.CLOSE, button.props["icon"])
            assertEquals("Close", button.props["text"])
            val click = button.modifier.elements().filterIsInstance<Clickable>().single()
            click.onClick()
            assertTrue(clicked)
            assertTrue(FocusNavigation.targets(composition.root, VisualTree(composition.root)).contains(button))
        }
        assertFailsWith<IllegalArgumentException> { Ui.compose(Runnable { Ui.iconButton(PixelIcon.CLOSE, " ", Runnable {}) }) }
    }
}
