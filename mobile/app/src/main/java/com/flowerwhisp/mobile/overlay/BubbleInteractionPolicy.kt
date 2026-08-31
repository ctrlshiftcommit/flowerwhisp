package com.flowerwhisp.mobile.overlay

import com.flowerwhisp.mobile.domain.model.BubbleState
import kotlin.math.max

internal enum class BubbleTapAction {
    TOGGLE,
    CANCEL,
    STATE_ACTION,
    NONE,
}

internal val BubbleState.keepsOverlayVisible: Boolean
    get() = when (this) {
        BubbleState.Hidden, BubbleState.Ready, is BubbleState.Snoozed -> false
        is BubbleState.Recording,
        is BubbleState.Processing,
        is BubbleState.Success,
        is BubbleState.InsertionFallback,
        is BubbleState.AccessibilityError,
        is BubbleState.ServiceError,
        BubbleState.Reconnecting,
        -> true
    }

internal fun bubbleTapAction(state: BubbleState, horizontalFraction: Float): BubbleTapAction = when (state) {
    BubbleState.Hidden -> BubbleTapAction.NONE
    BubbleState.Ready -> BubbleTapAction.TOGGLE
    is BubbleState.Recording -> if (horizontalFraction.coerceIn(0f, 1f) <= 0.31f) {
        BubbleTapAction.CANCEL
    } else {
        BubbleTapAction.TOGGLE
    }
    is BubbleState.Processing -> if (horizontalFraction.coerceIn(0f, 1f) >= 0.69f) {
        BubbleTapAction.CANCEL
    } else {
        BubbleTapAction.NONE
    }
    is BubbleState.Success,
    is BubbleState.InsertionFallback,
    is BubbleState.AccessibilityError,
    is BubbleState.ServiceError,
    BubbleState.Reconnecting,
    is BubbleState.Snoozed,
    -> BubbleTapAction.STATE_ACTION
}

internal fun bubbleHorizontalPosition(
    safeLeft: Int,
    safeRight: Int,
    bubbleWidth: Int,
    snappedRight: Boolean,
    imeVisible: Boolean,
): Int {
    val right = max(safeLeft, safeRight - bubbleWidth)
    return if (imeVisible || !snappedRight) safeLeft else right
}
