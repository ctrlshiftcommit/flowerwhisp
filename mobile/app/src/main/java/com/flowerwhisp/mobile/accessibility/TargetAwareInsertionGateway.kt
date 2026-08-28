package com.flowerwhisp.mobile.accessibility

sealed interface TargetCaptureResult {
    data class Captured(val token: TargetToken) : TargetCaptureResult
    data class Rejected(val reason: String, val sensitive: Boolean) : TargetCaptureResult
}

sealed interface TargetInsertionOutcome {
    data object VerifiedInserted : TargetInsertionOutcome
    data class ClipboardFallback(
        val text: String,
        val reason: String,
        val copied: Boolean,
    ) : TargetInsertionOutcome
}

interface TargetAwareInsertionGateway {
    fun captureTarget(): TargetCaptureResult
    fun isCurrentTarget(token: TargetToken): Boolean
    suspend fun insert(token: TargetToken, text: String): TargetInsertionOutcome
    fun hasSupportedFocusedField(): Boolean
}
