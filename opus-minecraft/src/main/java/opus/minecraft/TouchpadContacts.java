package opus.minecraft;

import java.util.HashSet;
import java.util.Set;

/** Contact lifetime is independent of movement and elapsed time. */
final class TouchpadContacts {
    private final Set<Integer> contacts = new HashSet<>();
    private boolean cancelled;

    boolean down(int id) {
        boolean first = contacts.isEmpty();
        if (first) cancelled = false;
        contacts.add(id);
        return first;
    }

    boolean contains(int id) { return contacts.contains(id); }
    boolean touching() { return !contacts.isEmpty(); }

    /** True only for the real, uncancelled final finger-up, never for an unknown pointer. */
    boolean up(int id) {
        return contacts.remove(id) && contacts.isEmpty() && !cancelled;
    }

    void cancel() {
        cancelled = true;
        contacts.clear();
    }
}
