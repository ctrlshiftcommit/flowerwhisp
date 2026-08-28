package com.flowerwhisp.mobile.service

import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleStateCoordinatorTest {
    @Test
    fun recordingCanOnlyBeginFromReadyAndCarriesMeasuredLevel() {
        val coordinator = BubbleStateCoordinator()
        assertFalse(coordinator.beginRecording(10L))
        coordinator.onAvailabilityChanged(true)
        assertTrue(coordinator.beginRecording(10L))
        coordinator.updateLevel(0.73f)

        assertEquals(BubbleState.Recording(10L, 0.73f), coordinator.state.value)
        assertFalse(coordinator.beginRecording(20L))
    }

    @Test
    fun processingAndSuccessAreExplicitStates() {
        val coordinator = BubbleStateCoordinator(BubbleState.Ready)
        assertTrue(coordinator.beginRecording(10L))
        coordinator.processing(ProcessingStage.TRANSCRIBING)
        assertEquals(BubbleState.Processing(ProcessingStage.TRANSCRIBING), coordinator.state.value)
        coordinator.processing(ProcessingStage.INSERTING)
        assertEquals(BubbleState.Processing(ProcessingStage.INSERTING), coordinator.state.value)
        coordinator.success(inserted = true)
        assertEquals(BubbleState.Success(inserted = true), coordinator.state.value)
    }

    @Test
    fun snoozeDoesNotInterruptAnActiveRecording() {
        val coordinator = BubbleStateCoordinator(BubbleState.Ready)
        coordinator.beginRecording(10L)
        coordinator.snooze(50_000L)
        assertTrue(coordinator.state.value is BubbleState.Recording)
    }
}
