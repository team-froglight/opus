package opus.minecraft;

import java.util.ArrayDeque;

/** Bounded undo history, owned by an input's stable composition identity. */
final class EditHistory {
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    void record(String value) {
        if (undo.isEmpty() || !undo.peekLast().equals(value)) undo.addLast(value);
        if (undo.size() > 100) undo.removeFirst();
        redo.clear();
    }
    String undo(String current) {
        if (undo.isEmpty()) return null;
        redo.addLast(current);
        return undo.removeLast();
    }
    String redo(String current) {
        if (redo.isEmpty()) return null;
        undo.addLast(current);
        return redo.removeLast();
    }
    void clear() { undo.clear(); redo.clear(); }
}
