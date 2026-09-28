package opus.minecraft;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import opus.core.UiNode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlKeyboardTest {
    @Test void sliderSupportsSmallLargeStepsAndBounds() {
        var slider = new UiNode("Slider");
        slider.getProps().put("min", 0f); slider.getProps().put("max", 100f); slider.getProps().put("value", 50f);
        slider.getProps().put("onValueChange", (Function1<Float, Unit>) value -> { slider.getProps().put("value", value); return Unit.INSTANCE; });
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
    @Test void dropdownMovesThenCommitsAndCloses() {
        var node = new UiNode("Dropdown");
        node.getProps().put("options", List.of("Apple", "Pear"));
        node.getProps().put("selectedIndex", 0);
        node.getProps().put("onExpandedChange", (Function1<Boolean, Unit>) value -> { node.getProps().put("expanded", value); return Unit.INSTANCE; });
        node.getProps().put("onSelect", (Function1<Integer, Unit>) value -> { node.getProps().put("selectedIndex", value); return Unit.INSTANCE; });
        var keys = new ControlKeyboard(); keys.focus(node);
        keys.key(node, 264, false);
        assertEquals(true, node.getProps().get("expanded"));
        assertEquals(0, node.getProps().get("selectedIndex"));
        keys.key(node, 257, false);
        assertEquals(1, node.getProps().get("selectedIndex"));
        assertEquals(false, node.getProps().get("expanded"));
    }
    @Test void multiselectMovesWithoutChangingSelectionUntilSpace() {
        var node = new UiNode("MultiSelect");
        node.getProps().put("options", List.of("Apple", "Pear"));
        node.getProps().put("selected", Set.of(0));
        node.getProps().put("onSelectionChange", (Function1<Set<Integer>, Unit>) value -> { node.getProps().put("selected", value); return Unit.INSTANCE; });
        var keys = new ControlKeyboard(); keys.focus(node);
        keys.key(node, 264, false);
        assertEquals(Set.of(0), node.getProps().get("selected"));
        keys.key(node, 32, false);
        assertEquals(Set.of(0, 1), node.getProps().get("selected"));
    }
}
