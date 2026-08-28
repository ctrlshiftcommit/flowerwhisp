package com.flowerwhisp.mobile.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class BubbleOverlayController(
    context: Context,
    private val scope: CoroutineScope,
    private val positionStore: BubblePositionStore?,
    private val onExplicitTap: () -> Unit,
    private val onPushToTalkStart: () -> Unit,
    private val onPushToTalkFinish: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val hitTargetPx = (48f * appContext.resources.displayMetrics.density).toInt()
    private var view: BubbleOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var visibilityAllowed = false
    private var verticalFraction = DEFAULT_VERTICAL_FRACTION
    private var snappedRight = true
    private var renderedState: BubbleState = BubbleState.Hidden
    private var bubbleScale = 0.88f
    private var bubbleOpacity = 0.88f

    init {
        OverlayRuntime.update(OverlayStatus.Hidden("No supported field is focused"))
        if (positionStore != null) {
            scope.launch {
                runCatching { positionStore.loadVerticalFraction() }
                    .onSuccess {
                        verticalFraction = it.coerceIn(0f, 1f)
                        recalculatePosition()
                    }
                    .onFailure { reportFailure("load position", it) }
            }
        }
    }

    fun render(state: BubbleState) {
        renderedState = state
        view?.let { attached ->
            attached.bubbleState = state
            resizeForState(attached)
        }
    }

    fun applyAppearance(settings: AppSettings) {
        bubbleScale = when (settings.bubbleSize) {
            BubbleSize.SMALL -> 0.76f
            BubbleSize.MEDIUM -> 0.88f
            BubbleSize.LARGE -> 0.96f
        }
        bubbleOpacity = when (settings.bubbleOpacity) {
            BubbleOpacity.SOFT -> 0.72f
            BubbleOpacity.STANDARD -> 0.88f
            BubbleOpacity.SOLID -> 1f
        }
        view?.apply {
            this.bubbleScale = this@BubbleOverlayController.bubbleScale
            this.bubbleOpacity = this@BubbleOverlayController.bubbleOpacity
        }
    }

    fun setVisibilityAllowed(allowed: Boolean) {
        if (visibilityAllowed == allowed && ((allowed && view != null) || (!allowed && view == null))) return
        visibilityAllowed = allowed
        if (allowed) show() else hide("A supported field and live accessibility/overlay access are required")
    }

    fun onConfigurationChanged() {
        recalculatePosition()
    }

    fun destroy() {
        visibilityAllowed = false
        hide("Accessibility service disconnected")
        OverlayRuntime.update(OverlayStatus.Detached)
    }

    private fun show() {
        if (!Settings.canDrawOverlays(appContext)) {
            reportFailure("add", SecurityException("Display-over-other-apps access is not active"))
            return
        }
        if (view != null) {
            recalculatePosition()
            return
        }

        val bubbleView = BubbleOverlayView(
            context = appContext,
            onDrag = ::moveBy,
            onDragFinished = ::snapAndRemember,
            onExplicitTap = onExplicitTap,
            onPushToTalkStart = onPushToTalkStart,
            onPushToTalkFinish = onPushToTalkFinish,
        ).also {
            it.bubbleState = renderedState
            it.bubbleScale = bubbleScale
            it.bubbleOpacity = bubbleOpacity
        }
        val params = WindowManager.LayoutParams(
            bubbleView.desiredWidthPx().coerceAtLeast(hitTargetPx),
            bubbleView.desiredHeightPx().coerceAtLeast(hitTargetPx),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "FlowerWhisp bubble"
        }
        position(params, safeBounds())
        try {
            windowManager.addView(bubbleView, params)
            view = bubbleView
            layoutParams = params
            OverlayRuntime.update(OverlayStatus.Visible)
        } catch (failure: RuntimeException) {
            view = null
            layoutParams = null
            reportFailure("add", failure)
        }
    }

    private fun hide(reason: String) {
        val attached = view
        if (attached != null) {
            try {
                windowManager.removeViewImmediate(attached)
            } catch (failure: RuntimeException) {
                reportFailure("remove", failure)
            } finally {
                view = null
                layoutParams = null
            }
        }
        if (OverlayRuntime.status.value !is OverlayStatus.Failure) {
            OverlayRuntime.update(OverlayStatus.Hidden(reason))
        }
    }

    private fun moveBy(deltaX: Int, deltaY: Int) {
        val params = layoutParams ?: return
        val safe = safeBounds()
        params.x = (params.x + deltaX).coerceIn(safe.left, max(safe.left, safe.right - params.width))
        params.y = (params.y + deltaY).coerceIn(safe.top, max(safe.top, safe.bottom - params.height))
        updateLayout("drag")
    }

    private fun snapAndRemember() {
        val params = layoutParams ?: return
        val safe = safeBounds()
        val left = safe.left
        val right = max(left, safe.right - params.width)
        snappedRight = params.x + params.width / 2 >= safe.centerX()
        params.x = if (snappedRight) right else left
        val verticalRange = max(1, safe.height() - params.height)
        verticalFraction = ((params.y - safe.top).toFloat() / verticalRange).coerceIn(0f, 1f)
        updateLayout("edge snap")
        positionStore?.let { store ->
            scope.launch {
                runCatching { store.saveVerticalFraction(verticalFraction) }
                    .onFailure { reportFailure("save position", it) }
            }
        }
    }

    private fun recalculatePosition() {
        val params = layoutParams ?: return
        position(params, safeBounds())
        updateLayout("recalculate")
    }

    private fun position(params: WindowManager.LayoutParams, safe: Rect) {
        val right = max(safe.left, safe.right - params.width)
        params.x = if (snappedRight) right else safe.left
        val verticalRange = max(0, safe.height() - params.height)
        params.y = safe.top + (verticalRange * verticalFraction).toInt()
    }

    private fun resizeForState(attached: BubbleOverlayView) {
        val params = layoutParams ?: return
        val safe = safeBounds()
        params.width = attached.desiredWidthPx().coerceAtLeast(hitTargetPx)
        params.height = attached.desiredHeightPx().coerceAtLeast(hitTargetPx)
        params.x = if (snappedRight) max(safe.left, safe.right - params.width) else safe.left
        val verticalRange = max(0, safe.height() - params.height)
        params.y = (safe.top + verticalRange * verticalFraction).toInt()
        updateLayout("resize")
    }

    private fun safeBounds(): Rect {
        val metrics = windowManager.currentWindowMetrics
        val bounds = Rect(metrics.bounds)
        val stable = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val dynamic = metrics.windowInsets.getInsets(
            WindowInsets.Type.ime() or WindowInsets.Type.systemGestures(),
        )
        val leftInset = max(stable.left, dynamic.left)
        val topInset = max(stable.top, dynamic.top)
        val rightInset = max(stable.right, dynamic.right)
        val bottomInset = max(stable.bottom, dynamic.bottom)
        return Rect(
            bounds.left + leftInset,
            bounds.top + topInset,
            max(bounds.left + leftInset + hitTargetPx, bounds.right - rightInset),
            max(bounds.top + topInset + hitTargetPx, bounds.bottom - bottomInset),
        )
    }

    private fun updateLayout(operation: String) {
        val attached = view ?: return
        val params = layoutParams ?: return
        try {
            windowManager.updateViewLayout(attached, params)
            OverlayRuntime.update(OverlayStatus.Visible)
        } catch (failure: RuntimeException) {
            reportFailure(operation, failure)
        }
    }

    private fun reportFailure(operation: String, failure: Throwable) {
        val message = when (operation) {
            "add" -> "FlowerWhisp could not add the visible bubble"
            "remove" -> "FlowerWhisp could not remove the bubble cleanly"
            "drag", "edge snap", "recalculate" -> "FlowerWhisp could not update the bubble position"
            "load position", "save position" -> "FlowerWhisp could not restore the remembered bubble position"
            else -> "FlowerWhisp overlay operation failed"
        }
        OverlayRuntime.update(OverlayStatus.Failure(operation, message))
        onFailure(message)
    }

    private companion object {
        const val DEFAULT_VERTICAL_FRACTION = 0.68f
    }
}
