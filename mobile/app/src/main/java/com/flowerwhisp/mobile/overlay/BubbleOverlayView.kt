package com.flowerwhisp.mobile.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.flowerwhisp.mobile.domain.model.BubbleState
import kotlin.math.abs
import kotlin.math.min

@SuppressLint("ViewConstructor") // Programmatic overlay view: callbacks are mandatory construction dependencies.
internal class BubbleOverlayView(
    context: Context,
    private val onDrag: (deltaX: Int, deltaY: Int) -> Unit,
    private val onDragFinished: () -> Unit,
    private val onExplicitTap: () -> Unit,
    private val onPushToTalkStart: () -> Unit,
    private val onPushToTalkFinish: () -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = resources.displayMetrics.density * 2f
        strokeCap = Paint.Cap.ROUND
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
        val centerX = width / 2f
        val centerY = height / 2f
        val maximumRadius = min(width, height) / 2f
        val radius = maximumRadius * bubbleScale
        fill.color = when (bubbleState) {
            is BubbleState.Recording -> Color.rgb(5, 5, 5)
            is BubbleState.Processing -> Color.rgb(16, 18, 16)
            is BubbleState.Success -> Color.rgb(158, 229, 178)
            is BubbleState.InsertionFallback -> Color.rgb(242, 201, 125)
            is BubbleState.AccessibilityError, is BubbleState.ServiceError -> Color.rgb(255, 180, 171)
            else -> Color.rgb(16, 18, 16)
        }
        fill.alpha = (255 * bubbleOpacity).toInt()
        stroke.color = Color.rgb(184, 245, 208)
        stroke.alpha = (255 * bubbleOpacity).toInt()
        canvas.drawCircle(centerX, centerY, radius, fill)
        canvas.drawCircle(centerX, centerY, radius, stroke)

        if (bubbleState is BubbleState.Recording) {
            val level = (bubbleState as BubbleState.Recording).level.coerceIn(0f, 1f)
            val heights = floatArrayOf(0.34f, 0.62f + level * 0.38f, 0.46f + level * 0.24f)
            heights.forEachIndexed { index, height ->
                val x = centerX + (index - 1) * resources.displayMetrics.density * 5f
                val half = radius * height * 0.5f
                canvas.drawLine(x, centerY - half, x, centerY + half, barPaint)
            }
        }
    }

    private fun descriptionFor(state: BubbleState): String = when (state) {
        BubbleState.Hidden -> "FlowerWhisp unavailable"
        BubbleState.Ready -> "Start FlowerWhisp recording"
        is BubbleState.Recording -> "Stop FlowerWhisp recording"
        is BubbleState.Processing -> "FlowerWhisp is processing"
        is BubbleState.Success -> "FlowerWhisp inserted the dictation"
        is BubbleState.InsertionFallback -> "FlowerWhisp copied the dictation for manual paste"
        is BubbleState.AccessibilityError -> "FlowerWhisp overlay error"
        is BubbleState.ServiceError -> "FlowerWhisp recording error"
        BubbleState.Reconnecting -> "FlowerWhisp is reconnecting"
        is BubbleState.Snoozed -> "FlowerWhisp bubble is snoozed"
    }
}
