package com.flowerwhisp.mobile.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.ProcessingStage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
internal class BubbleOverlayView(
    context: Context,
    private val onDrag: (deltaX: Int, deltaY: Int) -> Unit,
    private val onDragFinished: () -> Unit,
    private val onExplicitTap: () -> Unit,
    private val onPushToTalkStart: () -> Unit,
    private val onPushToTalkFinish: () -> Unit,
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
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
        textSize = density * 13f
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
        textSize = density * 11f
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false
    private var longPressActive = false
    private val longPressAction = Runnable {
        if (!moved && bubbleState == BubbleState.Ready) {
            longPressActive = true
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

    fun desiredHeightPx(): Int = (56f * density).toInt()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                moved = false
                longPressActive = false
                postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop) {
                    moved = true
                    removeCallbacks(longPressAction)
                }
                val dx = (event.rawX - lastRawX).toInt()
                val dy = (event.rawY - lastRawY).toInt()
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (moved && (dx != 0 || dy != 0)) onDrag(dx, dy)
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
        onExplicitTap()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density * (1.5f + (1f - bubbleScale) * 8f)
        val rect = RectF(inset, inset, width - inset, height - inset)
        val radius = min(rect.height() / 2f, density * 22f)
        fill.color = SURFACE
        fill.alpha = (255 * bubbleOpacity).toInt()
        stroke.color = if (bubbleState is BubbleState.Recording) CLAY else OUTLINE
        stroke.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        when (val state = bubbleState) {
            BubbleState.Hidden -> Unit
            BubbleState.Ready -> drawReady(canvas)
            is BubbleState.Recording -> drawRecording(canvas, state)
            is BubbleState.Processing -> drawProcessing(canvas, state.stage)
            is BubbleState.Success -> drawSuccess(canvas, state.inserted)
            is BubbleState.InsertionFallback -> drawMessage(canvas, "Copy text", WARNING)
            is BubbleState.AccessibilityError -> drawMessage(canvas, "Insertion unavailable", ERROR)
            is BubbleState.ServiceError -> drawMessage(canvas, "Dictation stopped", ERROR)
            BubbleState.Reconnecting -> drawMessage(canvas, "Reconnecting", WARNING)
            is BubbleState.Snoozed -> drawMessage(canvas, "Snoozed", SECONDARY)
        }
    }

    private fun drawReady(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        line.color = CLAY
        line.alpha = (255 * bubbleOpacity).toInt()
        val mark = RectF(cx - 10f * density, cy - 15f * density, cx + 10f * density, cy + 7f * density)
        canvas.drawArc(mark, 0f, 180f, false, line)
        canvas.drawLine(cx, cy + 7f * density, cx, cy + 15f * density, line)
        canvas.drawLine(cx - 8f * density, cy + 15f * density, cx + 8f * density, cy + 15f * density, line)
    }

    private fun drawRecording(canvas: Canvas, state: BubbleState.Recording) {
        val centerY = height / 2f
        val level = state.level.coerceIn(0f, 1f)
        line.color = CLAY
        line.alpha = (255 * bubbleOpacity).toInt()
        val heights = floatArrayOf(0.34f, 0.62f + level * 0.32f, 0.9f, 0.62f + level * 0.32f, 0.34f)
        heights.forEachIndexed { index, scale ->
            val x = 20f * density + index * 7f * density
            val half = (5f + 20f * scale) * density / 2f
            canvas.drawLine(x, centerY - half, x, centerY + half, line)
        }
        text.color = PRIMARY
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(formatElapsed((SystemClock.elapsedRealtime() - state.startedAtElapsedMs).coerceAtLeast(0L) / 1_000L), 67f * density, centerY + 4f * density, text)
        line.color = PRIMARY
        canvas.drawRoundRect(
            RectF(width - 41f * density, centerY - 7f * density, width - 27f * density, centerY + 7f * density),
            3f * density,
            3f * density,
            line,
        )
        canvas.drawLine(width - 18f * density, centerY - 7f * density, width - 7f * density, centerY + 7f * density, line)
        canvas.drawLine(width - 7f * density, centerY - 7f * density, width - 18f * density, centerY + 7f * density, line)
    }

    private fun drawProcessing(canvas: Canvas, stage: ProcessingStage) {
        val centerY = height / 2f
        text.color = PRIMARY
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(stageLabel(stage), 22f * density, centerY + 4f * density, text)
        line.color = CLAY
        line.alpha = (255 * bubbleOpacity).toInt()
        val x = width - 22f * density
        canvas.drawCircle(x, centerY, 3f * density, line)
    }

    private fun drawSuccess(canvas: Canvas, inserted: Boolean) {
        val centerY = height / 2f
        line.color = RESOLVED
        line.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawLine(17f * density, centerY, 23f * density, centerY + 6f * density, line)
        canvas.drawLine(23f * density, centerY + 6f * density, 34f * density, centerY - 7f * density, line)
        text.color = PRIMARY
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(if (inserted) "Inserted" else "Text ready", 46f * density, centerY + 4f * density, text)
    }

    private fun drawMessage(canvas: Canvas, label: String, color: Int) {
        val centerY = height / 2f
        text.color = color
        text.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawText(label, 18f * density, centerY + 4f * density, text)
    }

    private fun descriptionFor(state: BubbleState): String = when (state) {
        BubbleState.Hidden -> "FlowerWhisp unavailable"
        BubbleState.Ready -> "Start FlowerWhisp recording"
        is BubbleState.Recording -> "Stop FlowerWhisp recording"
        is BubbleState.Processing -> "FlowerWhisp is processing"
        is BubbleState.Success -> "FlowerWhisp inserted the dictation"
        is BubbleState.InsertionFallback -> "FlowerWhisp copied the dictation for manual paste"
        is BubbleState.AccessibilityError -> "FlowerWhisp insertion error"
        is BubbleState.ServiceError -> "FlowerWhisp recording error"
        BubbleState.Reconnecting -> "FlowerWhisp is reconnecting"
        is BubbleState.Snoozed -> "FlowerWhisp bubble is snoozed"
    }

    private fun desiredWidthDp(state: BubbleState): Float = when (state) {
        BubbleState.Hidden, BubbleState.Ready -> 56f
        is BubbleState.Recording -> 224f
        is BubbleState.Processing, is BubbleState.Success -> 168f
        is BubbleState.InsertionFallback, is BubbleState.AccessibilityError, is BubbleState.ServiceError -> 300f
        BubbleState.Reconnecting, is BubbleState.Snoozed -> 170f
    }

    private fun stageLabel(stage: ProcessingStage): String = when (stage) {
        ProcessingStage.TRANSCRIBING -> "Transcribing"
        ProcessingStage.REFINING -> "Refining"
        ProcessingStage.INSERTING -> "Inserting"
    }

    private companion object {
        const val INK = 0xFF0C0B0A.toInt()
        const val SURFACE = 0xFF201D19.toInt()
        const val OUTLINE = 0xFF3A342D.toInt()
        const val PRIMARY = 0xFFF5F0E7.toInt()
        const val SECONDARY = 0xFFBDB4A8.toInt()
        const val CLAY = 0xFFD17A5A.toInt()
        const val RESOLVED = 0xFFE4BC83.toInt()
        const val WARNING = 0xFFE4BC83.toInt()
        const val ERROR = 0xFFFFB3A7.toInt()
    }
}

private fun formatElapsed(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
