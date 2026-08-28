package com.flowerwhisp.mobile.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionPlannerTest {
    @Test
    fun insertsAtCursorWithoutReplacingSurroundingText() {
        val result = InsertionPlanner.plan(
            existingText = "Hello world",
            selectionStart = 6,
            selectionEnd = 6,
            dictatedText = "bright ",
        )

        assertTrue(result is InsertionPlanResult.Planned)
        val plan = (result as InsertionPlanResult.Planned).plan
        assertEquals("Hello bright world", plan.replacementText)
        assertEquals(13, plan.cursor)
    }

    @Test
    fun replacesOnlyTheSelectedRange() {
        val result = InsertionPlanner.plan(
            existingText = "Send it Tuesday",
            selectionStart = 8,
            selectionEnd = 15,
            dictatedText = "Wednesday",
        ) as InsertionPlanResult.Planned

        assertEquals("Send it Wednesday", result.plan.replacementText)
        assertEquals(17, result.plan.cursor)
    }
}
