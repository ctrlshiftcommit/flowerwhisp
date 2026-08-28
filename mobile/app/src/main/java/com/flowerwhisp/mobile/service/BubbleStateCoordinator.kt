package com.flowerwhisp.mobile.service

import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BubbleStateCoordinator(initial: BubbleState = BubbleState.Hidden) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<BubbleState> = mutableState.asStateFlow()

    @Synchronized
    fun onAvailabilityChanged(available: Boolean) {
        mutableState.value = when (mutableState.value) {
            BubbleState.Hidden -> if (available) BubbleState.Ready else BubbleState.Hidden
            BubbleState.Ready -> if (available) BubbleState.Ready else BubbleState.Hidden
            else -> mutableState.value
        }
    }

    @Synchronized
    fun beginRecording(startedAtElapsedMs: Long): Boolean {
        if (mutableState.value != BubbleState.Ready) return false
        mutableState.value = BubbleState.Recording(startedAtElapsedMs)
        return true
    }

    @Synchronized
    fun updateLevel(level: Float) {
        val recording = mutableState.value as? BubbleState.Recording ?: return
        mutableState.value = recording.copy(level = level.coerceIn(0f, 1f))
    }

    @Synchronized
    fun processing(stage: ProcessingStage) {
        if (mutableState.value is BubbleState.Recording || mutableState.value is BubbleState.Processing) {
            mutableState.value = BubbleState.Processing(stage)
        }
    }

    @Synchronized
    fun success(inserted: Boolean) {
        mutableState.value = BubbleState.Success(inserted)
    }

    @Synchronized
    fun fallback(text: String) {
        mutableState.value = BubbleState.InsertionFallback(text)
    }

    @Synchronized
    fun serviceError(message: String, recoverableRecordingId: Long? = null) {
        mutableState.value = BubbleState.ServiceError(message, recoverableRecordingId)
    }

    @Synchronized
    fun overlayError(message: String) {
        if (mutableState.value !is BubbleState.Recording && mutableState.value !is BubbleState.Processing) {
            mutableState.value = BubbleState.AccessibilityError(message)
        }
    }

    @Synchronized
    fun snooze(untilEpochMs: Long) {
        if (mutableState.value !is BubbleState.Recording && mutableState.value !is BubbleState.Processing) {
            mutableState.value = BubbleState.Snoozed(untilEpochMs)
        }
    }

    @Synchronized
    fun resetToAvailability(available: Boolean) {
        mutableState.value = if (available) BubbleState.Ready else BubbleState.Hidden
    }
}
