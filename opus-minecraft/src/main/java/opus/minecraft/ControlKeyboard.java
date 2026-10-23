package opus.minecraft;

import opus.core.*;
import org.lwjgl.glfw.GLFW;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Keyboard semantics for the existing declarative controls. */
final class ControlKeyboard {
    private int option;
    int option() { return option; }
    /** Starts option navigation on the current choice. */
    void focus(UiNode node) { option = number(node, "selectedIndex", 0).intValue(); }

    boolean key(UiNode node, int key, boolean shift) {
        boolean activate = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
        int direction = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> -1;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> 1;
            default -> 0;
        };
        boolean home = key == GLFW.GLFW_KEY_HOME, end = key == GLFW.GLFW_KEY_END;
        switch (node.getType()) {
            case "Slider" -> {
                if (direction == 0 && !home && !end) return false;
                float min = number(node, "min", 0).floatValue(), max = number(node, "max", 1).floatValue();
                float value = number(node, "value", min).floatValue();
                float step = (max - min) * (shift ? 0.1f : 0.01f);
                call(node.getEvents().getFloatChange(), home ? min : end ? max : Math.clamp(value + direction * step, min, max));
                return true;
            }
            case "Dropdown", "RadioGroup", "MultiSelect" -> {
                List<String> options = node.getOptions();
                if (options.isEmpty()) return false;
                boolean closedMenu = node.getType().equals("Dropdown") && !Boolean.TRUE.equals(node.getProps().get("expanded"));
                if (closedMenu && (direction != 0 || home || end || activate)) {
                    // The first key opens the menu on the current choice rather than moving past it.
                    focus(node);
                    option = Math.clamp(option, 0, options.size() - 1);
                    call(node.getEvents().getExpandedChange(), true);
                    return true;
                }
                option = Math.clamp(option, 0, options.size() - 1);
                if (direction != 0 || home || end) {
                    option = home ? 0 : end ? options.size() - 1 : Math.clamp(option + direction, 0, options.size() - 1);
                    if (node.getType().equals("RadioGroup")) call(node.getEvents().getSelect(), option);
                    return true;
                }
                if (!activate) return false;
                if (node.getType().equals("MultiSelect")) {
                    Set<Integer> selected = new TreeSet<>(node.getSelectedOptions());
                    if (!selected.remove(option)) selected.add(option);
                    call(node.getEvents().getSelectionChange(), Collections.unmodifiableSet(selected));
                } else if (node.getType().equals("RadioGroup")) call(node.getEvents().getSelect(), option);
                else {
                    call(node.getEvents().getSelect(), option);
                    call(node.getEvents().getExpandedChange(), false);
                }
                return true;
            }
            case "Pagination" -> {
                if (direction == 0 && !home && !end) return false;
                int count = Math.max(1, number(node, "pageCount", 1).intValue());
                int page = number(node, "page", 0).intValue();
                call(node.getEvents().getPageChange(), home ? 0 : end ? count - 1 : Math.clamp(page + direction, 0, count - 1));
                return true;
            }
            case "Checkbox", "Switch" -> {
                if (!activate) return false;
                call(node.getEvents().getCheckedChange(), !Boolean.TRUE.equals(node.getProps().get("checked")));
                return true;
            }
            case "Accordion" -> {
                if (!activate) return false;
                call(node.getEvents().getExpandedChange(), !Boolean.TRUE.equals(node.getProps().get("expanded")));
                return true;
            }
            case "RadioButton" -> {
                if (!activate) return false;
                if (node.getEvents().getClick() != null) node.getEvents().getClick().run();
                return true;
            }
        }
        if (activate) for (var element : Modifiers.elements(node.getModifier())) {
            if (element instanceof Clickable action) { action.getOnClick().invoke(); return true; }
        }
        return false;
    }

    private static Number number(UiNode node, String name, Number fallback) {
        return node.getProps().get(name) instanceof Number number ? number : fallback;
    }
    private static <T> void call(Consumer<T> callback, T value) {
        if (callback != null) callback.accept(value);
    }
}
