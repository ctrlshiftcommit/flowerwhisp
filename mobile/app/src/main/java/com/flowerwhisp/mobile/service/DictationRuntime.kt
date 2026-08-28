package com.flowerwhisp.mobile.service

import com.flowerwhisp.mobile.domain.model.ProcessingStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DictationRuntime {
    private val coordinator = BubbleStateCoordinator()
    private val mutableFallbackReason = MutableStateFlow<String?>(null)

    val bubbleState = coordinator.state
    val fallbackReason = mutableFallbackReason.asStateFlow()

    fun onBubbleAvailabilityChanged(available: Boolean) = coordinator.onAvailabilityChanged(available)

    fun beginRecording(startedAtElapsedMs: Long): Boolean {
        mutableFallbackReason.value = null
        return coordinator.beginRecording(startedAtElapsedMs)
    }

    fun updateLevel(level: Float) = coordinator.updateLevel(level)
    fun processing(stage: ProcessingStage) = coordinator.processing(stage)
    fun onSuccess() = coordinator.success(inserted = true)

    fun onFallback(text: String, reason: String) {
        mutableFallbackReason.value = reason
        coordinator.fallback(text)
    }

    fun onServiceError(message: String, recoverableRecordingId: Long? = null) {
        mutableFallbackReason.value = null
        coordinator.serviceError(message, recoverableRecordingId)
    }

    fun onOverlayFailure(message: String) = coordinator.overlayError(message)
    fun snooze(untilEpochMs: Long) = coordinator.snooze(untilEpochMs)

    fun resetToAvailability(available: Boolean) {
        mutableFallbackReason.value = null
        coordinator.resetToAvailability(available)
    }
}
