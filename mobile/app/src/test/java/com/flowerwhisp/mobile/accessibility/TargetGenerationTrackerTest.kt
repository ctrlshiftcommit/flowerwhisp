package com.flowerwhisp.mobile.accessibility

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetGenerationTrackerTest {
    @Test
    fun duplicateFocusEventKeepsTheFrozenTargetValid() {
        val tracker = TargetGenerationTracker()
        val identity = identity()
        val bounds = bounds(top = 120)

        val first = tracker.observe(identity, null, explicitFocusEvent = true, nextBounds = bounds)
        val duplicate = tracker.observe(identity, null, explicitFocusEvent = true, nextBounds = bounds)

        assertEquals(first, duplicate)
    }

    @Test
    fun anonymousFieldAtDifferentBoundsGetsANewGeneration() {
        val tracker = TargetGenerationTracker()

        val identity = identity()
        val first = tracker.observe(identity, null, explicitFocusEvent = true, nextBounds = bounds(top = 120))
        val second = tracker.observe(identity, null, explicitFocusEvent = true, nextBounds = bounds(top = 420))

        assertNotEquals(first?.generation, second?.generation)
    }

    @Test
    fun layoutResizeWithoutFocusChangeKeepsGeneration() {
        val tracker = TargetGenerationTracker()
        val identity = identity()
        val first = tracker.observe(identity, null, explicitFocusEvent = true, nextBounds = bounds(top = 120))
        val resized = tracker.observe(
            identity,
            null,
            explicitFocusEvent = false,
            nextBounds = TargetBounds(20, 120, 700, 360),
        )

        assertEquals(first?.generation, resized?.generation)
    }

    private fun identity() = TargetIdentity(
        packageName = "example.app",
        windowId = 4,
        className = "android.widget.EditText",
        viewIdResourceName = null,
    )

    private fun bounds(top: Int) = TargetBounds(20, top, 700, top + 96)
}
