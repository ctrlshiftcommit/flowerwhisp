package com.flowerwhisp.mobile.overlay

import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleInteractionPolicyTest {
    @Test
    fun keyboardAlwaysPlacesBubbleOnSafeLeftEdge() {
        assertEquals(24, bubbleHorizontalPosition(24, 1080, 156, snappedRight = true, imeVisible = true))
        assertEquals(24, bubbleHorizontalPosition(24, 1080, 156, snappedRight = false, imeVisible = true))
    }

    @Test
    fun savedEdgeReturnsWhenKeyboardIsClosed() {
        assertEquals(924, bubbleHorizontalPosition(24, 1080, 156, snappedRight = true, imeVisible = false))
        assertEquals(24, bubbleHorizontalPosition(24, 1080, 156, snappedRight = false, imeVisible = false))
    }

    @Test
    fun recordingCrossCancelsAndTheRestOfThePillFinishes() {
        val recording = BubbleState.Recording(startedAtElapsedMs = 1L)

        assertEquals(BubbleTapAction.CANCEL, bubbleTapAction(recording, 0.1f))
        assertEquals(BubbleTapAction.TOGGLE, bubbleTapAction(recording, 0.5f))
        assertEquals(BubbleTapAction.TOGGLE, bubbleTapAction(recording, 0.9f))
    }

    @Test
    fun processingOnlyExposesItsCancelZone() {
        val processing = BubbleState.Processing(ProcessingStage.TRANSCRIBING)

        assertEquals(BubbleTapAction.NONE, bubbleTapAction(processing, 0.3f))
        assertEquals(BubbleTapAction.CANCEL, bubbleTapAction(processing, 0.9f))
    }

    @Test
    fun activeAndRecoveryStatesStayVisibleWhenFieldFocusChanges() {
        assertTrue(BubbleState.Recording(1L).keepsOverlayVisible)
        assertTrue(BubbleState.Processing(ProcessingStage.INSERTING).keepsOverlayVisible)
        assertTrue(BubbleState.InsertionFallback("recovered").keepsOverlayVisible)
        assertTrue(BubbleState.ServiceError("failed", recoverableRecordingId = 4L).keepsOverlayVisible)
        assertFalse(BubbleState.Ready.keepsOverlayVisible)
        assertFalse(BubbleState.Snoozed(99L).keepsOverlayVisible)
    }
}
