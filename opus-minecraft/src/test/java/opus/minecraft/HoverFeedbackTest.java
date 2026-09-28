package opus.minecraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HoverFeedbackTest {
    @Test void reversesWithoutJumpingAndSettles() {
        var hover = new HoverFeedback();
        assertEquals(0f, hover.sample(true, 0, 1f));
        float middle = hover.sample(true, 60_000_000, 1f);
        assertTrue(middle > 0f && middle < 1f);
        assertEquals(middle, hover.sample(false, 60_000_000, 1f));
        assertEquals(0f, hover.sample(false, 180_000_000, 1f));
    }
    @Test void reducedMotionSnapsBothDirections() {
        var hover = new HoverFeedback();
        assertEquals(1f, hover.sample(true, 0, 0f));
        assertEquals(0f, hover.sample(false, 1, 0f));
    }
}
