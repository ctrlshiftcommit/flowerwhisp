package com.flowerwhisp.mobile.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface OverlayStatus {
    data object Detached : OverlayStatus
    data class Hidden(val reason: String) : OverlayStatus
    data object Visible : OverlayStatus
    data class Failure(val operation: String, val message: String) : OverlayStatus
}

object OverlayRuntime {
    private val mutableStatus = MutableStateFlow<OverlayStatus>(OverlayStatus.Detached)
    val status = mutableStatus.asStateFlow()

    internal fun update(status: OverlayStatus) {
        mutableStatus.value = status
    }
}
