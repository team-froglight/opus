package opus.minecraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditHistoryTest {
    @Test void undoRedoPreservesUnicodeAndNewEditsDiscardRedo() {
        var history = new EditHistory();
        history.record("");
        history.record("Привіт");
        assertEquals("Привіт", history.undo("Привіт\nworld"));
        assertEquals("", history.undo("Привіт"));
        assertEquals("Привіт", history.redo(""));
        history.record("Привіт");
        assertNull(history.redo("new"));
        history.clear();
        assertNull(history.undo("new"));
    }
    @Test void historyIsBounded() {
        var history = new EditHistory();
        for (int i = 0; i < 110; i++) history.record(Integer.toString(i));
        for (int i = 109; i >= 10; i--) assertEquals(Integer.toString(i), history.undo("current"));
        assertNull(history.undo("current"));
    }
}
