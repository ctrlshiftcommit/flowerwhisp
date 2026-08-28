package com.flowerwhisp.mobile.ui.app

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.platform.CapabilitySnapshot

enum class FlowerWhispDestination(val label: String) {
    HOME("Dictate"),
    INSIGHTS("Insights"),
    HISTORY("History"),
    LIBRARY("Library"),
    SETTINGS("Settings"),
}

enum class OnboardingStep {
    WELCOME,
    ACCESS,
    MICROPHONE,
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
    val dictionary: List<DictionaryEntry> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val librarySection: LibrarySection = LibrarySection.DICTIONARY,
    val settings: AppSettings = AppSettings(),
    val groqApiKeyConfigured: Boolean = false,
    val refinementPromptDraft: String = AppSettings().refinementPrompt,
    val serviceMessage: String? = null,
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
    val onOpenHistory: (Long) -> Unit = {},
    val onCloseHistory: () -> Unit = {},
    val onFavoriteHistory: (Long, Boolean) -> Unit = { _, _ -> },
    val onDeleteHistory: (Long) -> Unit = {},
    val onCopyHistory: (Long) -> Unit = {},
    val onShareHistory: (Long) -> Unit = {},
    val onRetryHistory: (Long) -> Unit = {},
    val onAddDictionary: (DictionaryEntry) -> Unit = {},
    val onLibrarySectionChanged: (LibrarySection) -> Unit = {},
    val onEditDictionary: (DictionaryEntry) -> Unit = {},
    val onDeleteDictionary: (Long) -> Unit = {},
    val onAddSnippet: (Snippet) -> Unit = {},
    val onEditSnippet: (Snippet) -> Unit = {},
    val onDeleteSnippet: (Long) -> Unit = {},
    val onWritingStyleChanged: (WritingStyle) -> Unit = {},
    val onLanguageChanged: (LanguageMode) -> Unit = {},
    val onAutoPunctuationChanged: (Boolean) -> Unit = {},
    val onRemoveFillersChanged: (Boolean) -> Unit = {},
    val onSpokenCorrectionsChanged: (Boolean) -> Unit = {},
    val onAiRefinementChanged: (Boolean) -> Unit = {},
    val onHapticsChanged: (Boolean) -> Unit = {},
    val onReduceMotionChanged: (Boolean) -> Unit = {},
    val onPrivacyChanged: (Boolean) -> Unit = {},
    val onBubbleSizeChanged: (com.flowerwhisp.mobile.domain.model.BubbleSize) -> Unit = {},
    val onBubbleOpacityChanged: (com.flowerwhisp.mobile.domain.model.BubbleOpacity) -> Unit = {},
    val onIdleBehaviorChanged: (com.flowerwhisp.mobile.domain.model.IdleBehavior) -> Unit = {},
    val onSaveApiKey: (String) -> Unit = {},
    val onClearApiKey: () -> Unit = {},
    val onUseMockEnginesChanged: (Boolean) -> Unit = {},
    val onRefinementPromptChanged: (String) -> Unit = {},
    val onSnooze: () -> Unit = {},
    val onWake: () -> Unit = {},
    val onRestartService: () -> Unit = {},
)
