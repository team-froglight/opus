package opus.minecraft;

import opus.core.ControlEvents;
import opus.core.UiNode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlKeyboardTest {
    @Test void sliderSupportsSmallLargeStepsAndBounds() {
        var slider = new UiNode("Slider");
        slider.getProps().put("min", 0f); slider.getProps().put("max", 100f); slider.getProps().put("value", 50f);
        slider.getProps().put("events", ControlEvents.NONE.withFloatChange(value -> slider.getProps().put("value", value)));
        var keys = new ControlKeyboard();
        keys.key(slider, 262, false);
        assertEquals(51f, slider.getProps().get("value"));
        keys.key(slider, 263, true);
        assertEquals(41f, slider.getProps().get("value"));
        keys.key(slider, 269, false); keys.key(slider, 262, false);
        assertEquals(100f, slider.getProps().get("value"));
        keys.key(slider, 268, false);
        assertEquals(0f, slider.getProps().get("value"));
    }

    private static UiNode dropdown(int selected) {
        var node = new UiNode("Dropdown");
        node.getProps().put("options", List.of("Apple", "Pear", "Plum"));
        node.getProps().put("selectedIndex", selected);
        node.getProps().put("events", ControlEvents.NONE
            .withExpandedChange(value -> node.getProps().put("expanded", value))
            .withSelect(value -> node.getProps().put("selectedIndex", value)));
        return node;
    }

    @Test void dropdownOpensOnTheCurrentChoiceThenMovesCommitsAndCloses() {
        var node = dropdown(1);
        var keys = new ControlKeyboard(); keys.focus(node);
        keys.key(node, 264, false);
        assertEquals(true, node.getProps().get("expanded"));
        assertEquals(1, keys.option());
        keys.key(node, 264, false);
        assertEquals(2, keys.option());
        assertEquals(1, node.getProps().get("selectedIndex"));
        keys.key(node, 257, false);
        assertEquals(2, node.getProps().get("selectedIndex"));
        assertEquals(false, node.getProps().get("expanded"));
    }

    @Test void reopeningADropdownForgetsTheAbandonedHighlight() {
        var node = dropdown(0);
        var keys = new ControlKeyboard(); keys.focus(node);
        keys.key(node, 264, false);
        keys.key(node, 264, false);
        assertEquals(1, keys.option());
        node.getProps().put("expanded", false);
        keys.key(node, 32, false);
        assertEquals(true, node.getProps().get("expanded"));
        assertEquals(0, keys.option());
    }

    @Test void multiselectMovesWithoutChangingSelectionUntilSpace() {
        var node = new UiNode("MultiSelect");
        node.getProps().put("options", List.of("Apple", "Pear"));
        node.getProps().put("selected", Set.of(0));
        node.getProps().put("events", ControlEvents.NONE.withSelectionChange(value -> node.getProps().put("selected", value)));
        var keys = new ControlKeyboard(); keys.focus(node);
        keys.key(node, 264, false);
        assertEquals(Set.of(0), node.getProps().get("selected"));
        keys.key(node, 32, false);
        assertEquals(Set.of(0, 1), node.getProps().get("selected"));
    }
}
