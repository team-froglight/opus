package opus.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JavaControlApiTest {
    @Test void completeJavaFacadeCompilesWithoutKotlinCallbacks() {
        try (Composition composition = Ui.compose(() -> {
            Ui.button("button", () -> {}, Modifier.EMPTY, true, 123);
            Ui.radioButton(false, () -> {});
            Ui.radioGroup(List.of("one"), -1, index -> {});
            Ui.switchControl(false, value -> {});
            Ui.dropdown(List.of("one"), -1, index -> {}, false, expanded -> {});
            Ui.multiSelect(List.of("one"), Set.of(), values -> {});
            Ui.pagination(0, 1, page -> {});
            Ui.accordion("title", false, expanded -> {}, () -> {});
            Ui.tooltip("tip", () -> {});
            Ui.popup(() -> {}, () -> {});
            Ui.itemSlot("minecraft:stone");
            Ui.image("test:texture", Ui.clickable(Modifier.EMPTY, () -> {}));
            Ui.progressBar(0.5f);
            Ui.listColumn(2, index -> Ui.text("" + index));
            Ui.dateField("", value -> {}, "Date", Modifier.EMPTY, InputOptions.DEFAULT, LocalDate.of(2024, 1, 1), Locale.ROOT);
        })) {
            assertEquals(2, composition.getRoot().findAll("Text").size());
            assertEquals(123, composition.getRoot().findAll("Button").getFirst().getProps().get("accent"));
        }
    }
}
