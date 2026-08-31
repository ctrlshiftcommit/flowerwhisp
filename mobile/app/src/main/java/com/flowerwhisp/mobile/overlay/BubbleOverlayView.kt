package com.flowerwhisp.mobile.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import androidx.core.content.res.ResourcesCompat
import com.flowerwhisp.mobile.R
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import kotlin.math.abs
import kotlin.math.min

@SuppressLint("ViewConstructor")
internal class BubbleOverlayView(
    context: Context,
    private val onDrag: (deltaX: Int, deltaY: Int) -> Unit,
    private val onDragFinished: () -> Unit,
    private val onExplicitTap: () -> Unit,
    private val onPushToTalkStart: () -> Unit,
    private val onPushToTalkFinish: () -> Unit,
    private val onCancel: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.25f
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = density * 2.2f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.dm_sans_variable)
            ?: android.graphics.Typeface.DEFAULT
        textSize = density * 13f
    }
    private val bubbleRect = RectF()
    private val readyMark = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false
    private var longPressActive = false
    private var tapX = 0f
    private val longPressAction = Runnable {
        if (!moved && bubbleState == BubbleState.Ready) {
            longPressActive = true
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onPushToTalkStart()
        }
    }

    var bubbleScale: Float = 0.88f
        set(value) {
            field = value.coerceIn(0.7f, 1f)
            invalidate()
        }

    var bubbleOpacity: Float = 0.88f
        set(value) {
            field = value.coerceIn(0.55f, 1f)
            invalidate()
        }

    var hapticsEnabled: Boolean = true

    var darkTheme: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var idleExpanded: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var bubbleState: BubbleState = BubbleState.Hidden
        set(value) {
            field = value
            contentDescription = descriptionFor(value)
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = descriptionFor(bubbleState)
    }

    fun desiredWidthPx(): Int = (desiredWidthDp(bubbleState) * density).toInt()

    fun desiredHeightPx(): Int = (52f * density).toInt()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                moved = false
                longPressActive = false
                tapX = event.x
                if (bubbleState == BubbleState.Ready) {
                    postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressActive &&
                    (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop)
                ) {
                    moved = true
                    removeCallbacks(longPressAction)
                }
                val dx = (event.rawX - lastRawX).toInt()
                val dy = (event.rawY - lastRawY).toInt()
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (!longPressActive && moved && (dx != 0 || dy != 0)) onDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressAction)
                if (longPressActive) {
                    onPushToTalkFinish()
                    longPressActive = false
                } else if (moved) {
                    onDragFinished()
                } else {
                    tapX = event.x
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressAction)
                if (longPressActive) onPushToTalkFinish()
                longPressActive = false
                if (moved) onDragFinished()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val horizontalFraction = if (width > 0) tapX / width.toFloat() else 0.5f
        when (bubbleTapAction(bubbleState, horizontalFraction)) {
            BubbleTapAction.TOGGLE, BubbleTapAction.STATE_ACTION -> onExplicitTap()
            BubbleTapAction.CANCEL -> onCancel()
            BubbleTapAction.NONE -> Unit
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density * (1.5f + (1f - bubbleScale) * 8f)
        bubbleRect.set(inset, inset, width - inset, height - inset)
        val rect = bubbleRect
        val radius = min(rect.height() / 2f, density * 22f)
        fill.color = surfaceColor
        fill.alpha = (255 * bubbleOpacity).toInt()
        stroke.color = if (bubbleState is BubbleState.Recording) accentColor else outlineColor
        stroke.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        when (val state = bubbleState) {
            BubbleState.Hidden -> Unit
            BubbleState.Ready -> drawReady(canvas)
            is BubbleState.Recording -> drawRecording(canvas, state)
            is BubbleState.Processing -> drawProcessing(canvas, state.stage)
            is BubbleState.Success -> drawSuccess(canvas, state.inserted)
            is BubbleState.InsertionFallback -> drawMessage(canvas, "Copy text", warningColor)
            is BubbleState.AccessibilityError -> drawMessage(canvas, "Insertion unavailable", errorColor)
            is BubbleState.ServiceError -> drawMessage(canvas, "Dictation stopped", errorColor)
            BubbleState.Reconnecting -> drawMessage(canvas, "Reconnecting", warningColor)
            is BubbleState.Snoozed -> drawMessage(canvas, "Snoozed", secondaryColor)
        }
    }

    private fun drawReady(canvas: Canvas) {
        val cx = if (idleExpanded) 28f * density else width / 2f
        val cy = height / 2f
        line.color = accentColor
        line.alpha = (255 * bubbleOpacity).toInt()
        readyMark.set(cx - 10f * density, cy - 15f * density, cx + 10f * density, cy + 7f * density)
        canvas.drawArc(readyMark, 0f, 180f, false, line)
        canvas.drawLine(cx, cy + 7f * density, cx, cy + 15f * density, line)
        canvas.drawLine(cx - 8f * density, cy + 15f * density, cx + 8f * density, cy + 15f * density, line)
        if (idleExpanded) {
            text.color = primaryColor
            text.alpha = (255 * bubbleOpacity).toInt()
            canvas.drawText("Ready", 52f * density, cy + 4f * density, text)
        }
    }

    private fun drawRecording(canvas: Canvas, state: BubbleState.Recording) {
        val centerY = height / 2f
        val level = state.level.coerceIn(0f, 1f)
        drawCross(canvas, 25f * density, centerY, secondaryColor)
        line.color = accentColor
        line.alpha = (255 * bubbleOpacity).toInt()
        val heights = floatArrayOf(0.34f, 0.62f + level * 0.32f, 0.9f, 0.62f + level * 0.32f, 0.34f)
        heights.forEachIndexed { index, scale ->
            val x = 61f * density + index * 7f * density
            val half = (5f + 20f * scale) * density / 2f
            canvas.drawLine(x, centerY - half, x, centerY + half, line)
        }
        drawCheck(canvas, width - 25f * density, centerY, primaryColor)
    }

    private fun drawProcessing(canvas: Canvas, stage: ProcessingStage) {
        val centerY = height / 2f
        text.color = primaryColor
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(stageLabel(stage), 16f * density, centerY + 4f * density, text)
        line.color = accentColor
        line.alpha = (255 * bubbleOpacity).toInt()
        drawCross(canvas, width - 24f * density, centerY, secondaryColor)
    }

    private fun drawSuccess(canvas: Canvas, inserted: Boolean) {
        val centerY = height / 2f
        line.color = resolvedColor
        line.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawLine(17f * density, centerY, 23f * density, centerY + 6f * density, line)
        canvas.drawLine(23f * density, centerY + 6f * density, 34f * density, centerY - 7f * density, line)
        text.color = primaryColor
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(if (inserted) "Inserted" else "Text ready", 46f * density, centerY + 4f * density, text)
    }

    private fun drawMessage(canvas: Canvas, label: String, color: Int) {
        val centerY = height / 2f
        text.color = color
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(label, 18f * density, centerY + 4f * density, text)
    }

    private fun drawCross(canvas: Canvas, centerX: Float, centerY: Float, color: Int) {
        line.color = color
        line.alpha = (255 * bubbleOpacity).toInt()
        val radius = 7f * density
        canvas.drawLine(centerX - radius, centerY - radius, centerX + radius, centerY + radius, line)
        canvas.drawLine(centerX + radius, centerY - radius, centerX - radius, centerY + radius, line)
    }

    private fun drawCheck(canvas: Canvas, centerX: Float, centerY: Float, color: Int) {
        line.color = color
        line.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawLine(centerX - 9f * density, centerY, centerX - 2f * density, centerY + 7f * density, line)
        canvas.drawLine(centerX - 2f * density, centerY + 7f * density, centerX + 10f * density, centerY - 8f * density, line)
    }

    private fun descriptionFor(state: BubbleState): String = when (state) {
        BubbleState.Hidden -> "FlowerWhisp unavailable"
        BubbleState.Ready -> "FlowerWhisp ready. Tap to record or hold for push to talk"
        is BubbleState.Recording -> "Recording. Tap the cross to cancel or the visualizer and check to finish"
        is BubbleState.Processing -> "FlowerWhisp is processing. Tap the cross to cancel"
        is BubbleState.Success -> "FlowerWhisp inserted the dictation"
        is BubbleState.InsertionFallback -> "FlowerWhisp copied the dictation for manual paste"
        is BubbleState.AccessibilityError -> "FlowerWhisp insertion error"
        is BubbleState.ServiceError -> "FlowerWhisp recording error"
        BubbleState.Reconnecting -> "FlowerWhisp is reconnecting"
        is BubbleState.Snoozed -> "FlowerWhisp bubble is snoozed"
    }

    private fun desiredWidthDp(state: BubbleState): Float = when (state) {
        BubbleState.Hidden -> 52f
        BubbleState.Ready -> if (idleExpanded) 112f else 52f
        is BubbleState.Recording -> 156f
        is BubbleState.Processing -> 148f
        is BubbleState.Success -> 128f
        is BubbleState.InsertionFallback, is BubbleState.AccessibilityError, is BubbleState.ServiceError -> 236f
        BubbleState.Reconnecting, is BubbleState.Snoozed -> 148f
    }

    private fun stageLabel(stage: ProcessingStage): String = when (stage) {
        ProcessingStage.TRANSCRIBING -> "Transcribing"
        ProcessingStage.REFINING -> "Refining"
        ProcessingStage.INSERTING -> "Inserting"
    }

    private val surfaceColor: Int get() = if (darkTheme) 0xFF080808.toInt() else 0xFFFFFFFF.toInt()
    private val outlineColor: Int get() = if (darkTheme) 0xFF2C2C2C.toInt() else 0xFFD4D4D4.toInt()
    private val primaryColor: Int get() = if (darkTheme) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
    private val secondaryColor: Int get() = if (darkTheme) 0xFFA3A3A3.toInt() else 0xFF666666.toInt()
    private val accentColor: Int get() = primaryColor
    private val resolvedColor: Int get() = primaryColor
    private val warningColor: Int get() = if (darkTheme) 0xFFFFB454.toInt() else 0xFF8A4B08.toInt()
    private val errorColor: Int get() = if (darkTheme) 0xFFFF7A70.toInt() else 0xFFB42318.toInt()
}
