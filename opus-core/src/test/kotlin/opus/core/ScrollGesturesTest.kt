package opus.core

import kotlin.test.*

class ScrollGesturesTest {
    @Test fun fullPullOnlyArmsAndReleaseCommitsOnce() {
        val g = ScrollGestures()
        assertNull(g.accept(-4.0, 0.0, 0).swipe)
        assertEquals(0.5f, g.preview!!.progress)
        assertNull(g.accept(-4.0, 0.0, 20_000_000).swipe)
        assertEquals(1f, g.preview!!.progress)
        assertNull(g.advance(159_000_000).swipe)
        assertNull(g.advance(10_000_000_000).swipe)
        assertEquals(1f, g.preview!!.progress)
        assertEquals(SwipeDirection.LEFT, g.release().swipe)
        assertNull(g.preview)
        assertFalse(g.isActive)
        assertNull(g.advance(400_000_000).swipe)
    }

    @Test fun shortFlickCancelsAtRelease() {
        val g = ScrollGestures()
        g.accept(-3.0, 0.0, 0)
        assertEquals(0.375f, g.preview!!.progress)
        assertNull(g.release().swipe)
        assertNull(g.preview)
    }

    @Test fun pullingBackDisarmsImmediatelyEvenAfterOvershoot() {
        val g = ScrollGestures()
        g.accept(-100.0, 0.0, 0)
        assertEquals(1f, g.preview!!.progress)
        g.accept(2.0, 0.0, 30_000_000)
        assertEquals(0.75f, g.preview!!.progress)
        assertNull(g.release().swipe)
    }

    @Test fun returningToOriginCancelsOrCanStartAnOppositePull() {
        val g = ScrollGestures()
        g.accept(-4.0, 0.0, 0)
        g.accept(4.0, 0.0, 20_000_000)
        assertNull(g.preview)
        g.accept(8.0, 0.0, 40_000_000)
        assertEquals(SwipeDirection.RIGHT, g.preview!!.direction)
        assertEquals(SwipeDirection.RIGHT, g.release().swipe)
    }

    @Test fun continuousMovementAndMomentumCannotNavigateWhileStillPulling() {
        val g = ScrollGestures()
        for (i in 0..60) {
            assertNull(g.accept(-8.0 / (i + 1), 0.0, i * 16_000_000L).swipe)
            assertNull(g.advance(i * 16_000_000L + 8_000_000).swipe)
        }
        assertNull(g.advance(1_100_000_000).swipe)
        assertEquals(SwipeDirection.LEFT, g.release().swipe)
        assertNull(g.advance(1_500_000_000).swipe)
    }

    @Test fun successiveFullPullsWorkImmediatelyAfterRelease() {
        val g = ScrollGestures()
        for (i in 0..2) {
            val start = i * 160_000_000L
            assertNull(g.accept(-8.0, 0.0, start).swipe)
            assertEquals(SwipeDirection.LEFT, g.release().swipe)
        }
    }

    @Test fun newInputPreservesPriorReleaseWhenFrameWasMissed() {
        val g = ScrollGestures(inferRelease = true)
        g.accept(-8.0, 0.0, 0)
        assertEquals(SwipeDirection.LEFT, g.accept(4.0, 0.0, 160_000_000).swipe)
        assertEquals(SwipeDirection.RIGHT, g.preview!!.direction)
        assertEquals(0.5f, g.preview!!.progress)
        assertNull(g.release().swipe)
    }

    @Test fun preservesFractionalVerticalInputAndOsMomentum() {
        val g = ScrollGestures()
        val deltas = listOf(0.02, 0.1, 0.32, 0.15, 0.07, 0.01, -0.04)
        val updates = deltas.mapIndexed { i, delta -> g.accept(0.002, delta, i * 16_000_000L) }
        assertEquals(deltas.sum(), updates.sumOf { it.verticalScroll }, 0.0000001)
        assertTrue(updates.all { it.swipe == null })
        assertNull(g.preview)
        assertNull(g.release().swipe)
    }

    @Test fun verticalGestureStaysLockedDespiteHorizontalDrift() {
        val g = ScrollGestures()
        assertEquals(0.5, g.accept(0.1, 0.5, 0).verticalScroll)
        assertEquals(0.2, g.accept(20.0, 0.2, 20_000_000).verticalScroll)
        assertNull(g.preview)
        assertNull(g.release().swipe)
    }

    @Test fun diagonalDefaultsToVerticalAndPreservesPendingDeltas() {
        val g = ScrollGestures()
        assertEquals(ScrollGestureUpdate(), g.accept(0.2, 0.2, 0))
        assertEquals(0.6, g.accept(0.4, 0.4, 20_000_000).verticalScroll, 0.000001)
        g.accept(12.0, 0.1, 40_000_000)
        assertNull(g.release().swipe)
    }

    @Test fun blockedInputAndClickCancelEvenAnArmedPull() {
        val g = ScrollGestures()
        g.accept(-8.0, 0.0, 0)
        g.suppressSwipe()
        assertNull(g.preview)
        assertNull(g.release().swipe)
        assertEquals(0.1, g.accept(10.0, 0.1, 200_000_000, false).verticalScroll)
        assertNull(g.release().swipe)
    }

    @Test fun becomingBlockedBeforeReleaseDoesNotCommitTheOldPull() {
        val g = ScrollGestures()
        g.accept(-8.0, 0.0, 0)
        assertNull(g.accept(0.1, 0.1, 200_000_000, false).swipe)
    }

    @Test fun staleInvalidAndEmptyEventsDoNotCorruptPullOrExtendRelease() {
        val g = ScrollGestures()
        g.accept(8.0, 0.0, 100_000_000)
        assertNull(g.accept(-8.0, 0.0, 90_000_000).swipe)
        assertNull(g.accept(Double.NaN, 0.0, 120_000_000).swipe)
        assertNull(g.accept(0.0, Double.POSITIVE_INFINITY, 130_000_000).swipe)
        assertNull(g.accept(0.0, 0.0, 140_000_000).swipe)
        assertNull(g.advance(90_000_000).swipe)
        assertNull(g.advance(240_000_000).swipe)
        assertEquals(SwipeDirection.RIGHT, g.release().swipe)
    }

    @Test fun motionAfterLongStationaryHoldDoesNotCommitAndCanRetract() {
        val g = ScrollGestures()
        g.accept(-8.0, 0.0, 0)
        assertNull(g.advance(30_000_000_000).swipe)
        assertNull(g.accept(1.0, 0.0, 30_000_000_001).swipe)
        assertEquals(0.875f, g.preview!!.progress)
        assertNull(g.release().swipe)
    }

    @Test fun resetCancelsWithoutNavigation() {
        val g = ScrollGestures()
        g.accept(8.0, 0.0, 0)
        g.reset()
        assertNull(g.release().swipe)
        assertFalse(g.isActive)
    }

    @Test fun validatesConfigurationAndSupportsNativeFingerUp() {
        assertFailsWith<IllegalArgumentException> { ScrollGestures(0.0) }
        assertFailsWith<IllegalArgumentException> { ScrollGestures(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { ScrollGestures(quietMillis = 0) }
        val g = ScrollGestures(2.0)
        assertNull(g.accept(2.0, 0.0, 0).swipe)
        assertEquals(SwipeDirection.RIGHT, g.release().swipe)
    }
}
