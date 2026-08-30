package com.flowerwhisp.mobile.ui.app

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.AppearanceMode
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.CleanupLevel
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.StyleContext
import com.flowerwhisp.mobile.domain.model.RetentionMode
import com.flowerwhisp.mobile.domain.model.TransformProfile
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.platform.CapabilitySnapshot

enum class FlowerWhispDestination(val label: String) {
    HOME("Dictate"),
    HISTORY("History"),
    DICTIONARY("Dictionary"),
    SNIPPETS("Snippets"),
    STYLE("Style"),
    TRANSFORMS("Transforms"),
    SCRATCHPAD("Scratchpad"),
    INSIGHTS("Insights"),
    SETTINGS("Settings"),
}

enum class OnboardingStep {
    WELCOME,
    ACCESS,
    MICROPHONE,
    PROVIDER,
    TEST,
    READY,
}

enum class LibrarySection(val label: String) {
    DICTIONARY("Dictionary"),
    SNIPPETS("Snippets"),
    STYLE("Writing style"),
}

data class FlowerWhispUiState(
    val onboardingComplete: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.WELCOME,
    val destination: FlowerWhispDestination = FlowerWhispDestination.HOME,
    val capabilities: CapabilitySnapshot = CapabilitySnapshot(
        accessibilityEnabled = false,
        overlayEnabled = false,
        microphoneGranted = false,
        notificationsGranted = false,
    ),
    val bubbleState: BubbleState = BubbleState.Ready,
    val elapsedSeconds: Long = 0,
    val history: List<Dictation> = emptyList(),
    val historyQuery: String = "",
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val selectedDictation: Dictation? = null,
    val playingDictationId: Long? = null,
    val dictionary: List<DictionaryEntry> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val transforms: List<TransformProfile> = emptyList(),
    val librarySection: LibrarySection = LibrarySection.DICTIONARY,
    val settings: AppSettings = AppSettings(),
    val groqApiKeyConfigured: Boolean = false,
    val refinementPromptDraft: String = AppSettings().refinementPrompt,
    val transformPreview: TransformPreview? = null,
    val transformBusy: Boolean = false,
    val serviceMessage: String? = null,
)

data class TransformPreview(
    val name: String,
    val source: String,
    val result: String,
)

/**
 * UI events are deliberately callbacks rather than navigation or repository dependencies.
 * MainViewModel/Application owns their implementation and can map this contract to its
 * existing persistence, system-settings, and service boundaries.
 */
data class FlowerWhispActions(
    val onNavigate: (FlowerWhispDestination) -> Unit = {},
    val onAdvanceOnboarding: (OnboardingStep) -> Unit = {},
    val onCompleteOnboarding: () -> Unit = {},
    val onSkipOnboarding: () -> Unit = {},
    val onRequestAccessibility: () -> Unit = {},
    val onRequestOverlay: () -> Unit = {},
    val onRequestMicrophone: () -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onOnboardingTap: () -> Unit = {},
    val onOnboardingHold: () -> Unit = {},
    val onOnboardingRealTest: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onOpenApp: () -> Unit = {},
    val onSearchHistory: (String) -> Unit = {},
    val onRefreshHistory: () -> Unit = {},
    val onOpenHistory: (Long) -> Unit = {},
    val onCloseHistory: () -> Unit = {},
    val onFavoriteHistory: (Long, Boolean) -> Unit = { _, _ -> },
    val onDeleteHistory: (Long) -> Unit = {},
    val onCopyHistory: (Long) -> Unit = {},
    val onShareHistory: (Long) -> Unit = {},
    val onPlayHistory: (Long) -> Unit = {},
    val onDeleteHistoryAudio: (Long) -> Unit = {},
    val onSendHistoryToScratchpad: (Long) -> Unit = {},
    val onRetryHistory: (Long) -> Unit = {},
    val onAddDictionary: (DictionaryEntry) -> Unit = {},
    val onLibrarySectionChanged: (LibrarySection) -> Unit = {},
    val onEditDictionary: (DictionaryEntry) -> Unit = {},
    val onDeleteDictionary: (Long) -> Unit = {},
    val onDictionaryEnabledChanged: (DictionaryEntry, Boolean) -> Unit = { _, _ -> },
    val onAddSnippet: (Snippet) -> Unit = {},
    val onEditSnippet: (Snippet) -> Unit = {},
    val onDeleteSnippet: (Long) -> Unit = {},
    val onSnippetEnabledChanged: (Snippet, Boolean) -> Unit = { _, _ -> },
    val onSaveTransform: (TransformProfile) -> Unit = {},
    val onDeleteTransform: (Long) -> Unit = {},
    val onRunHistoryTransform: (Long, Long) -> Unit = { _, _ -> },
    val onRunScratchpadTransform: (Long) -> Unit = {},
    val onDismissTransform: () -> Unit = {},
    val onSaveScratchpad: (String) -> Unit = {},
    val onWritingStyleChanged: (WritingStyle) -> Unit = {},
    val onContextWritingStyleChanged: (StyleContext, WritingStyle) -> Unit = { _, _ -> },
    val onContextStyleInstructionsChanged: (StyleContext, String) -> Unit = { _, _ -> },
    val onLanguageChanged: (LanguageMode) -> Unit = {},
    val onAutoPunctuationChanged: (Boolean) -> Unit = {},
    val onRemoveFillersChanged: (Boolean) -> Unit = {},
    val onSpokenCorrectionsChanged: (Boolean) -> Unit = {},
    val onAiRefinementChanged: (Boolean) -> Unit = {},
    val onCleanupLevelChanged: (CleanupLevel) -> Unit = {},
    val onCleanupPromptChanged: (CleanupLevel, String) -> Unit = { _, _ -> },
    val onTranscriptionModelChanged: (String) -> Unit = {},
    val onRefinementModelChanged: (String) -> Unit = {},
    val onHapticsChanged: (Boolean) -> Unit = {},
    val onPlaySoundsChanged: (Boolean) -> Unit = {},
    val onMuteMusicChanged: (Boolean) -> Unit = {},
    val onReduceMotionChanged: (Boolean) -> Unit = {},
    val onPrivacyChanged: (Boolean) -> Unit = {},
    val onRetentionChanged: (RetentionMode) -> Unit = {},
    val onAppearanceChanged: (AppearanceMode) -> Unit = {},
    val onBubbleSizeChanged: (com.flowerwhisp.mobile.domain.model.BubbleSize) -> Unit = {},
    val onBubbleOpacityChanged: (com.flowerwhisp.mobile.domain.model.BubbleOpacity) -> Unit = {},
    val onIdleBehaviorChanged: (com.flowerwhisp.mobile.domain.model.IdleBehavior) -> Unit = {},
    val onSaveApiKey: (String) -> Unit = {},
    val onClearApiKey: () -> Unit = {},
    val onUseMockEnginesChanged: (Boolean) -> Unit = {},
    val onUndoHistoryCleanup: (Long) -> Unit = {},
    val onExportHistoryAudio: (Long) -> Unit = {},
    val onCopyLastTranscript: () -> Unit = {},
    val onRefinementPromptChanged: (String) -> Unit = {},
    val onSnooze: () -> Unit = {},
    val onWake: () -> Unit = {},
    val onRestartService: () -> Unit = {},
)
