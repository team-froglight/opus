package opus.minecraft;

import opus.core.SwipeDirection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GestureHintTest {
    @Test void releasingPartialPullStartsRetreatImmediately() {
        var hint = new GestureHint();
        hint.update(SwipeDirection.LEFT, 0.7f, false, true, 0);
        hint.dismiss(10_000_000);
        assertEquals(0.5f, hint.sample(50_000_000, false).opacity(), 0.001f);
        assertNull(hint.sample(90_000_000, false));
        hint.update(SwipeDirection.LEFT, 0.7f, false, true, 100_000_000);
        hint.dismiss(100_000_000);
        assertNull(hint.sample(100_000_000, true));
    }

    @Test void unfinishedPullFadesAndReleasesItsState() {
        var hint = new GestureHint();
        hint.update(SwipeDirection.LEFT, 0.4f, false, true, 0);
        assertEquals(0.4f, hint.sample(100_000_000, false).progress());
        assertEquals(1f, hint.sample(100_000_000, false).opacity());
        assertEquals(0.5f, hint.sample(180_000_000, false).opacity(), 0.001f);
        assertNull(hint.sample(220_000_000, false));
    }

    @Test void completedSwipeConfirmsThenDisappearsAndCanBeInterrupted() {
        var hint = new GestureHint();
        hint.update(SwipeDirection.LEFT, 1f, true, true, 0);
        assertTrue(hint.sample(0, false).confirmed());
        assertEquals(0.5f, hint.sample(50_000_000, false).opacity(), 0.001f);
        assertNull(hint.sample(100_000_000, false));
        hint.update(SwipeDirection.RIGHT, 0.3f, false, true, 300_000_000);
        assertEquals(SwipeDirection.RIGHT, hint.sample(310_000_000, false).direction());
        assertFalse(hint.sample(310_000_000, false).confirmed());
        hint.clear();
        assertNull(hint.sample(320_000_000, false));
    }

    @Test void unavailableDirectionAndReducedMotionRemainDistinct() {
        var hint = new GestureHint();
        hint.update(SwipeDirection.RIGHT, 1f, false, false, 0);
        assertFalse(hint.sample(100_000_000, true).available());
        assertEquals(1f, hint.sample(139_000_000, true).opacity());
        assertNull(hint.sample(140_000_000, true));
        hint.update(SwipeDirection.RIGHT, 1f, true, false, 200_000_000);
        assertNull(hint.sample(200_000_000, true));
    }
}
