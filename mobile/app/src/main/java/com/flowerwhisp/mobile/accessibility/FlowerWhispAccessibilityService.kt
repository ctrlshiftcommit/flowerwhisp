package com.flowerwhisp.mobile.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flowerwhisp.mobile.domain.ports.InsertionResult
import com.flowerwhisp.mobile.domain.ports.TextInsertionGateway
import com.flowerwhisp.mobile.overlay.BubbleOverlayController
import com.flowerwhisp.mobile.overlay.SettingsBubblePositionStore
import com.flowerwhisp.mobile.platform.CapabilityMonitor
import com.flowerwhisp.mobile.service.DictationDependencyRegistry
import com.flowerwhisp.mobile.service.DictationRuntime
import com.flowerwhisp.mobile.service.DictationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class FlowerWhispAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val targetTracker = TargetGenerationTracker()
    private lateinit var capabilityMonitor: CapabilityMonitor
    private var overlayController: BubbleOverlayController? = null
    private var snoozed = false

    override fun onServiceConnected() {
        bridge = WeakReference(this)
        capabilityMonitor = CapabilityMonitor(this)
        val settingsRepository = DictationDependencyRegistry.peek()?.settingsRepository
        val positionStore = settingsRepository?.let(::SettingsBubblePositionStore)
        overlayController = BubbleOverlayController(
            context = this,
            scope = serviceScope,
            positionStore = positionStore,
            onExplicitTap = ::onBubbleTapped,
            onPushToTalkStart = ::onPushToTalkStarted,
            onPushToTalkFinish = ::onPushToTalkFinished,
            onFailure = DictationRuntime::onOverlayFailure,
        )
        if (settingsRepository != null) {
            serviceScope.launch {
                settingsRepository.settings.collectLatest { settings ->
                    overlayController?.applyAppearance(settings)
                    val remaining = settings.snoozedUntilEpochMs - System.currentTimeMillis()
                    if (remaining > 0L) {
                        snoozed = true
                        DictationRuntime.snooze(settings.snoozedUntilEpochMs)
                        reconcileOverlay()
                        delay(remaining)
                    }
                    if (settings.snoozedUntilEpochMs <= System.currentTimeMillis()) {
                        snoozed = false
                        val available = fieldState.value.available &&
                            capabilityMonitor.snapshot(accessibilityConnected = true).canShowBubble
                        DictationRuntime.resetToAvailability(available)
                        reconcileOverlay()
                    }
                }
            }
        }
        serviceScope.launch {
            DictationRuntime.bubbleState.collectLatest { state ->
                overlayController?.render(state)
                reconcileOverlay()
            }
        }
        refreshFocus(
            node = currentFocusedNode(),
            explicitFocusEvent = false,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventSource = event.source
        val candidate = eventSource?.takeIf { it.isFocused } ?: currentFocusedNode()
        refreshFocus(
            node = candidate,
            explicitFocusEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged()
        refreshFocus(currentFocusedNode(), explicitFocusEvent = false)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (bridge?.get() === this) bridge = null
        fieldState.value = FocusedField()
        DictationRuntime.onBubbleAvailabilityChanged(false)
        overlayController?.destroy()
        overlayController = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onBubbleTapped() {
        when (DictationRuntime.bubbleState.value) {
            com.flowerwhisp.mobile.domain.model.BubbleState.Ready -> {
                when (val capture = captureTargetInternal()) {
                    is TargetCaptureResult.Captured -> {
                        val failure = DictationService.startFromBubble(this, capture.token)
                        if (failure != null) DictationRuntime.onServiceError(failure)
                    }
                    is TargetCaptureResult.Rejected -> {
                        DictationRuntime.onServiceError(capture.reason)
                    }
                }
            }
            is com.flowerwhisp.mobile.domain.model.BubbleState.Recording -> {
                DictationService.stopFromBubble(this)
            }
            else -> Unit
        }
    }

    private fun onPushToTalkStarted() {
        if (DictationRuntime.bubbleState.value == com.flowerwhisp.mobile.domain.model.BubbleState.Ready) {
            onBubbleTapped()
        }
    }

    private fun onPushToTalkFinished() {
        if (DictationRuntime.bubbleState.value is com.flowerwhisp.mobile.domain.model.BubbleState.Recording) {
            DictationService.stopFromBubble(this)
        }
    }

    private fun refreshFocus(node: AccessibilityNodeInfo?, explicitFocusEvent: Boolean) {
        fieldState.value = describe(node, explicitFocusEvent)
        reconcileOverlay()
    }

    private fun reconcileOverlay() {
        if (!::capabilityMonitor.isInitialized) return
        val capabilities = capabilityMonitor.snapshot(accessibilityConnected = true)
        val mayShow = !snoozed && fieldState.value.available && capabilities.canShowBubble
        DictationRuntime.onBubbleAvailabilityChanged(mayShow)
        overlayController?.setVisibilityAllowed(mayShow)
    }

    private fun currentFocusedNode(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun describe(node: AccessibilityNodeInfo?, explicitFocusEvent: Boolean): FocusedField {
        val identity = node?.toIdentity()
        val token = targetTracker.observe(
            nextIdentity = identity,
            nextPlatformUniqueId = node?.uniqueId,
            explicitFocusEvent = explicitFocusEvent,
        )
        if (node == null || identity == null || token == null) return FocusedField()

        val packageName = identity.packageName
        if (packageName.isBlank()) return FocusedField(reason = "The focused field has no package identity")
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        if (!supportsSetText) {
            return FocusedField(
                packageName = packageName,
                reason = "The focused app does not expose editable text insertion",
            )
        }

        return when (
            val decision = SensitiveFieldPolicy.evaluate(
                FieldMetadata(
                    editable = node.isEditable,
                    enabled = node.isEnabled,
                    visible = node.isVisibleToUser,
                    password = node.isPassword,
                    inputType = node.inputType,
                    className = identity.className,
                    viewIdResourceName = identity.viewIdResourceName,
                    hintText = node.hintText?.toString(),
                ),
            )
        ) {
            FieldPolicyDecision.Supported -> FocusedField(
                available = true,
                packageName = packageName,
                reason = "",
                token = token,
            )
            is FieldPolicyDecision.Rejected -> FocusedField(
                available = false,
                sensitive = decision.sensitive,
                packageName = packageName,
                reason = decision.reason,
            )
        }
    }

    private fun AccessibilityNodeInfo.toIdentity(): TargetIdentity? {
        val packageValue = packageName?.toString().orEmpty()
        if (packageValue.isBlank() || windowId < 0) return null
        return TargetIdentity(
            packageName = packageValue,
            windowId = windowId,
            className = className?.toString(),
            viewIdResourceName = viewIdResourceName,
        )
    }

    private fun captureTargetInternal(): TargetCaptureResult {
        val focused = describe(currentFocusedNode(), explicitFocusEvent = false)
        fieldState.value = focused
        val token = focused.token
        return if (focused.available && token != null) {
            TargetCaptureResult.Captured(token)
        } else {
            TargetCaptureResult.Rejected(focused.reason, focused.sensitive)
        }
    }

    private fun isCurrentTargetInternal(token: TargetToken): Boolean {
        val focused = describe(currentFocusedNode(), explicitFocusEvent = false)
        fieldState.value = focused
        return focused.available && token.matches(focused.token)
    }

    private fun insertAtFrozenTarget(token: TargetToken, text: String): TargetInsertionOutcome {
        if (text.isBlank()) {
            return TargetInsertionOutcome.ClipboardFallback(
                text = text,
                reason = "Blank dictation was not inserted or copied",
                copied = false,
            )
        }

        val initialNode = currentFocusedNode()
            ?: return clipboardFallback(text, "The original field is no longer focused")
        val initialField = describe(initialNode, explicitFocusEvent = false)
        if (!initialField.available || !token.matches(initialField.token)) {
            return clipboardFallback(text, "The focused field changed while FlowerWhisp was processing")
        }

        // Field text is read only here to compose the local cursor-aware replacement. It is never retained or logged.
        val plan = when (
            val result = InsertionPlanner.plan(
                existingText = initialNode.text?.toString().orEmpty(),
                selectionStart = initialNode.textSelectionStart,
                selectionEnd = initialNode.textSelectionEnd,
                dictatedText = text,
            )
        ) {
            is InsertionPlanResult.Planned -> result.plan
            is InsertionPlanResult.Rejected -> return TargetInsertionOutcome.ClipboardFallback(
                text = text,
                reason = result.reason,
                copied = false,
            )
        }

        val setTextAccepted = initialNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    plan.replacementText,
                )
            },
        )
        if (!setTextAccepted) {
            return clipboardFallback(text, "The original field rejected direct text insertion")
        }

        val selectionNode = currentFocusedNode()
            ?: return clipboardFallback(text, "The original field disappeared after accepting text")
        val selectionField = describe(selectionNode, explicitFocusEvent = false)
        if (!selectionField.available || !token.matches(selectionField.token)) {
            return clipboardFallback(text, "The focused field changed before cursor placement")
        }
        selectionNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, plan.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, plan.cursor)
            },
        )

        val verificationNode = currentFocusedNode()
            ?: return clipboardFallback(text, "The original field could not be verified after insertion")
        val verificationField = describe(verificationNode, explicitFocusEvent = false)
        if (!verificationField.available || !token.matches(verificationField.token)) {
            return clipboardFallback(text, "The focused field changed before insertion could be verified")
        }
        if (verificationNode.text?.toString() != plan.replacementText) {
            return clipboardFallback(text, "The original field did not retain the inserted text")
        }
        val cursorExposed = verificationNode.textSelectionStart >= 0 || verificationNode.textSelectionEnd >= 0
        val cursorVerified = !cursorExposed ||
            (verificationNode.textSelectionStart == plan.cursor && verificationNode.textSelectionEnd == plan.cursor)
        if (!cursorVerified) {
            return clipboardFallback(text, "The original field did not accept the requested cursor position")
        }
        return TargetInsertionOutcome.VerifiedInserted
    }

    private fun clipboardFallback(text: String, reason: String): TargetInsertionOutcome.ClipboardFallback {
        val copied = runCatching {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("FlowerWhisp dictation", text))
        }.isSuccess
        val honestReason = if (copied) reason else "$reason. FlowerWhisp could not copy the text"
        return TargetInsertionOutcome.ClipboardFallback(text, honestReason, copied)
    }

    companion object {
        private var bridge: WeakReference<FlowerWhispAccessibilityService>? = null
        private val fieldState = kotlinx.coroutines.flow.MutableStateFlow(FocusedField())
        val focusedField = fieldState.asStateFlow()

        val targetAwareGateway: TargetAwareInsertionGateway = object : TargetAwareInsertionGateway {
            override fun captureTarget(): TargetCaptureResult = bridge?.get()?.captureTargetInternal()
                ?: TargetCaptureResult.Rejected("FlowerWhisp accessibility is not connected", false)

            override fun isCurrentTarget(token: TargetToken): Boolean =
                bridge?.get()?.isCurrentTargetInternal(token) == true

            override suspend fun insert(token: TargetToken, text: String): TargetInsertionOutcome =
                withContext(Dispatchers.Main.immediate) {
                    bridge?.get()?.insertAtFrozenTarget(token, text)
                        ?: TargetInsertionOutcome.ClipboardFallback(
                            text = text,
                            reason = "FlowerWhisp accessibility disconnected before insertion and could not copy text",
                            copied = false,
                        )
                }

            override fun hasSupportedFocusedField(): Boolean = fieldState.value.available
        }

        val insertionGateway: TextInsertionGateway = object : TextInsertionGateway {
            override suspend fun insert(text: String): InsertionResult {
                val capture = withContext(Dispatchers.Main.immediate) { targetAwareGateway.captureTarget() }
                if (capture !is TargetCaptureResult.Captured) {
                    return if ((capture as TargetCaptureResult.Rejected).sensitive) {
                        InsertionResult.SensitiveField
                    } else {
                        InsertionResult.NoFocusedField
                    }
                }
                return when (val outcome = targetAwareGateway.insert(capture.token, text)) {
                    TargetInsertionOutcome.VerifiedInserted -> InsertionResult.Inserted
                    is TargetInsertionOutcome.ClipboardFallback -> InsertionResult.ClipboardFallback(
                        outcome.text,
                        outcome.reason,
                    )
                }
            }

            override fun hasSupportedFocusedField(): Boolean = targetAwareGateway.hasSupportedFocusedField()
        }

        fun isConnected(): Boolean = bridge?.get() != null
    }
}
