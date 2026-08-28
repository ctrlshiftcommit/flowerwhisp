package com.flowerwhisp.mobile.platform

import com.flowerwhisp.mobile.overlay.OverlayRuntime
import com.flowerwhisp.mobile.service.DictationRuntime

/** Stable read-only state surface for the main app. */
object FlowerWhispPlatform {
    val bubbleState = DictationRuntime.bubbleState
    val overlayStatus = OverlayRuntime.status
    val fallbackReason = DictationRuntime.fallbackReason
}
