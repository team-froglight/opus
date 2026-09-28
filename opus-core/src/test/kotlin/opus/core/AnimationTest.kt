package opus.core

import kotlin.test.*

class AnimationTest {
    private fun Composition.frame(millis: Long) = advanceAnimations(millis * 1_000_000)

    @Test fun startsAtTargetAndStopsRecomposingWhenSettled() {
        val target = mutableStateOf(20f)
        var displayed = 0f
        val c = setContent { displayed = animateFloat(target.value, 100, Easing.LINEAR) }
        assertEquals(20f, displayed)
        assertFalse(c.hasRunningAnimations)
        c.frame(0)
        target.value = 100f
        assertEquals(20f, displayed)
        c.frame(25)
        assertEquals(40f, displayed)
        c.frame(100)
        assertEquals(100f, displayed)
        assertFalse(c.hasRunningAnimations)
        val count = c.recomposeCount
        c.frame(200)
        assertEquals(count, c.recomposeCount)
        c.close()
    }

    @Test fun retargetsFromDisplayedValueWithoutJumping() {
        val target = mutableStateOf(0f)
        var displayed = 0f
        val c = setContent { displayed = animateFloat(target.value, 100, Easing.LINEAR) }
        c.frame(0)
        target.value = 100f
        c.frame(40)
        assertEquals(40f, displayed)
        target.value = 0f
        assertEquals(40f, displayed)
        c.frame(90)
        assertEquals(20f, displayed)
        c.frame(140)
        assertEquals(0f, displayed)
        c.close()
    }

    @Test fun unrelatedRecompositionDoesNotRestartTransition() {
        val target = mutableStateOf(0f)
        val label = mutableStateOf("before")
        var displayed = 0f
        val c = setContent {
            displayed = animateFloat(target.value, 100, Easing.LINEAR)
            Text(label.value)
        }
        c.frame(0)
        target.value = 1f
        c.frame(40)
        label.value = "after"
        c.frame(100)
        assertEquals(1f, displayed)
        assertFalse(c.hasRunningAnimations)
        c.close()
    }

    @Test fun batchesTracksAndInterpolatesPremultipliedAlpha() {
        val expanded = mutableStateOf(false)
        var distance = 0f
        var color = 0
        val c = setContent {
            distance = Ui.animateFloat(if (expanded.value) 100f else 0f, 100, Easing.LINEAR)
            color = Ui.animateColor(if (expanded.value) 0xFFFF0000.toInt() else 0, 100, Easing.LINEAR)
        }
        c.frame(0)
        expanded.value = true
        val count = c.recomposeCount
        c.frame(50)
        assertEquals(count + 1, c.recomposeCount)
        assertEquals(50f, distance)
        assertEquals(0x80FF0000.toInt(), color)
        c.frame(100)
        assertEquals(0xFFFF0000.toInt(), color)
        c.close()
    }

    @Test fun reducedMotionFinishesActiveAndFutureTransitions() {
        val target = mutableStateOf(0f)
        var displayed = 0f
        val c = setContent { displayed = animateFloat(target.value, 100) }
        c.frame(0)
        target.value = 1f
        c.frame(20)
        c.animationDurationScale = 0f
        c.frame(21)
        assertEquals(1f, displayed)
        assertFalse(c.hasRunningAnimations)
        target.value = 2f
        assertEquals(2f, displayed)
        c.close()
    }

    @Test fun leavingCompositionStopsTracksAndReentryUsesFreshTarget() {
        val visible = mutableStateOf(true)
        val target = mutableStateOf(0f)
        var displayed = 0f
        val c = setContent {
            if (visible.value) displayed = animateFloat(target.value, 100, Easing.LINEAR)
        }
        c.frame(0)
        target.value = 1f
        c.frame(30)
        visible.value = false
        assertFalse(c.hasRunningAnimations)
        val count = c.recomposeCount
        c.frame(50)
        assertEquals(count, c.recomposeCount)
        visible.value = true
        assertEquals(1f, displayed)
        c.close()
        c.frame(100)
        assertFalse(c.hasRunningAnimations)
    }

    @Test fun clockHandlesSlowFramesBackwardsTimestampsAndDurationScale() {
        val target = mutableStateOf(0f)
        var displayed = 0f
        val c = setContent { displayed = animateFloat(target.value, 100, Easing.LINEAR) }
        c.animationDurationScale = 2f
        c.frame(1000)
        target.value = 1f
        c.frame(1100)
        assertEquals(0.5f, displayed)
        c.frame(1050)
        assertEquals(0.5f, displayed)
        c.frame(9000)
        assertEquals(1f, displayed)
        c.close()
    }

    @Test fun switchKeepsLogicalStateWhileVisualPositionAnimates() {
        val checked = mutableStateOf(false)
        val c = setContent { Switch(checked.value, { checked.value = it }) }
        c.frame(0)
        checked.value = true
        var node = c.root.findAll("Switch").single()
        assertEquals(true, node.props["checked"])
        assertEquals(0f, node.props["position"])
        c.frame(90)
        node = c.root.findAll("Switch").single()
        assertTrue(node.props["position"] as Float in 0.1f..0.99f)
        c.frame(180)
        assertEquals(1f, c.root.findAll("Switch").single().props["position"])
        c.close()
    }

    @Test fun zeroDurationAndInvalidInputsAreExplicit() {
        val target = mutableStateOf(0f)
        var displayed = 0f
        val c = setContent { displayed = animateFloat(target.value, 0) }
        target.value = 10f
        assertEquals(10f, displayed)
        assertFalse(c.hasRunningAnimations)
        assertFailsWith<IllegalArgumentException> { c.animationDurationScale = -1f }
        assertFailsWith<IllegalArgumentException> { c.animationDurationScale = Float.NaN }
        assertFailsWith<IllegalArgumentException> { setContent { animateFloat(Float.NaN) } }
        assertFailsWith<IllegalArgumentException> { setContent { animateFloat(1f, -1) } }
        assertFailsWith<IllegalStateException> { animateFloat(1f) }
        c.close()
    }

    @Test fun easingCurvesPreserveEndpointsAndStayMonotonic() {
        for (curve in Easing.entries) {
            assertEquals(0f, curve.transform(-1f))
            assertEquals(1f, curve.transform(2f))
            val samples = (0..100).map { curve.transform(it / 100f) }
            assertTrue(samples.zipWithNext().all { (a, b) -> a <= b })
        }
    }
}
