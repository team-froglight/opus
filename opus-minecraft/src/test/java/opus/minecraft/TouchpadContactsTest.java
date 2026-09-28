package opus.minecraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TouchpadContactsTest {
    @Test void onlyLastFingerReleaseCompletesGesture() {
        var contacts = new TouchpadContacts();
        assertTrue(contacts.down(1));
        assertFalse(contacts.down(2));
        assertFalse(contacts.down(2));
        assertFalse(contacts.up(1));
        assertTrue(contacts.touching());
        assertFalse(contacts.up(99));
        assertTrue(contacts.up(2));
        assertFalse(contacts.touching());
        assertFalse(contacts.up(2));
    }

    @Test void cancellationIgnoresLateReleasesAndAllowsNextGesture() {
        var contacts = new TouchpadContacts();
        contacts.down(1);
        contacts.down(2);
        contacts.cancel();
        assertFalse(contacts.touching());
        assertFalse(contacts.up(1));
        assertFalse(contacts.up(2));
        assertTrue(contacts.down(1));
        assertTrue(contacts.up(1));
    }

    @Test void repeatedGesturesNeedNoCooldown() {
        var contacts = new TouchpadContacts();
        for (int i = 0; i < 20; i++) {
            assertTrue(contacts.down(1));
            assertFalse(contacts.down(2));
            assertFalse(contacts.up(2));
            assertTrue(contacts.up(1));
        }
    }
}
