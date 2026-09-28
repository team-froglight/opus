package opus.showcase;

import opus.core.Modifier;
import opus.core.Modifiers;
import opus.core.MutableState;
import opus.core.Ui;
import opus.minecraft.UiScreen;

/** Compiled example of the Java API; callers can pass this screen to Minecraft.setScreen. */
public final class CounterExample {
    private CounterExample() {}

    public static UiScreen create() {
        return UiScreen.create(() -> {
            MutableState<Integer> count = Ui.remember(() -> Ui.state(0));
            Ui.column(() -> {
                Ui.text("Count: " + count.getValue());
                Ui.button("Add", () -> count.setValue(count.getValue() + 1));
            }, Modifiers.padding(Modifier.EMPTY, 16f));
        });
    }
}
