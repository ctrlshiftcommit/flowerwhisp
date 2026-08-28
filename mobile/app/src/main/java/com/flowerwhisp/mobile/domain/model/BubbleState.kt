package com.flowerwhisp.mobile.domain.model

sealed interface BubbleState {
    data object Hidden : BubbleState
    data object Ready : BubbleState
    data class Recording(val startedAtElapsedMs: Long, val level: Float = 0f) : BubbleState
    data class Processing(val stage: ProcessingStage) : BubbleState
    data class Success(val inserted: Boolean) : BubbleState
    data class InsertionFallback(val text: String) : BubbleState
    data class AccessibilityError(val message: String) : BubbleState
    data class ServiceError(val message: String, val recoverableRecordingId: Long? = null) : BubbleState
    data object Reconnecting : BubbleState
    data class Snoozed(val untilEpochMs: Long) : BubbleState
}

enum class ProcessingStage { TRANSCRIBING, REFINING, INSERTING }
