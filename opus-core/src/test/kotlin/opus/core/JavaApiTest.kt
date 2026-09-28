package opus.core

import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaApiTest {
    @Test fun navigationArrowsKeepLabelsAndDisabledState() {
        Ui.compose(Runnable {
            Ui.arrowButton(ArrowDirection.LEFT, "Previous tab", Runnable {}, Modifier, false)
            Ui.arrowButton(ArrowDirection.RIGHT, "Next tab", Runnable {})
        }).use { composition ->
            val buttons = composition.root.findAll("Button")
            assertEquals("Previous tab", buttons[0].props["text"])
            assertEquals(false, buttons[0].props["enabled"])
            assertEquals(ArrowDirection.LEFT, buttons[0].props["arrowDirection"])
            assertEquals(ArrowDirection.RIGHT, buttons[1].props["arrowDirection"])
        }
    }

    @Test fun javaCallbacksPreserveStateAndRecompose() {
        val count = Ui.state(0)
        Ui.compose(Runnable {
            Ui.column(Runnable {
                Ui.text("Count: ${count.value}")
                Ui.button("Add", Runnable { count.value++ })
            })
        }).use { composition ->
            val click = composition.root.findAll("Button").single().modifier.elements().filterIsInstance<Clickable>().single()
            click.onClick()
            assertEquals("Count: 1", composition.root.findAll("Text").single().props["text"])
        }
    }

    @Test fun javaRememberKeepsItsValueAcrossRecomposition() {
        var initializations = 0
        val observed = mutableListOf<Int>()
        Ui.compose(Runnable {
            observed += Ui.remember(Supplier { ++initializations })
        }).use { composition -> composition.compose() }
        assertEquals(listOf(1, 1), observed)
    }
}
