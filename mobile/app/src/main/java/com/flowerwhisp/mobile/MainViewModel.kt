package com.flowerwhisp.mobile

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.IdleBehavior
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.ports.AudioRecording
import com.flowerwhisp.mobile.domain.usecase.ProcessDictation
import com.flowerwhisp.mobile.platform.CapabilityMonitor
import com.flowerwhisp.mobile.service.DictationRuntime
import com.flowerwhisp.mobile.service.DictationService
import com.flowerwhisp.mobile.ui.app.FlowerWhispDestination
import com.flowerwhisp.mobile.ui.app.FlowerWhispUiState
import com.flowerwhisp.mobile.ui.app.LibrarySection
import com.flowerwhisp.mobile.ui.app.OnboardingStep
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val application: FlowerWhispApplication,
) : ViewModel() {
    private val container = application.container
    private val capabilityMonitor = CapabilityMonitor(application)
    private val processDictation = ProcessDictation(
        container.historyRepository,
        container.dictionaryRepository,
        container.snippetRepository,
    )
    private val mutableUiState = MutableStateFlow(FlowerWhispUiState(historyLoading = true))
    val uiState: StateFlow<FlowerWhispUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                mutableUiState.update {
                    it.copy(
                        onboardingComplete = settings.onboardingComplete,
                        settings = settings,
                        refinementPromptDraft = settings.refinementPrompt,
                        groqApiKeyConfigured = container.settingsRepository.hasGroqApiKey(),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.historyRepository.observeAll().collect { history ->
                mutableUiState.update { it.copy(history = history, historyLoading = false, historyError = null) }
            }
        }
        viewModelScope.launch {
            container.dictionaryRepository.observeAll().collect { dictionary ->
                mutableUiState.update { it.copy(dictionary = dictionary) }
            }
        }
        viewModelScope.launch {
            container.snippetRepository.observeAll().collect { snippets ->
                mutableUiState.update { it.copy(snippets = snippets) }
            }
        }
        viewModelScope.launch {
            DictationRuntime.bubbleState.collect { state ->
                mutableUiState.update { it.copy(bubbleState = state) }
            }
        }
        viewModelScope.launch {
            DictationRuntime.fallbackReason.collect { reason ->
                if (reason != null) mutableUiState.update { it.copy(serviceMessage = reason) }
            }
        }
        viewModelScope.launch {
            while (true) {
                val recording = mutableUiState.value.bubbleState as? BubbleState.Recording
                val elapsed = recording?.let {
                    ((SystemClock.elapsedRealtime() - it.startedAtElapsedMs) / 1_000L).coerceAtLeast(0L)
                } ?: 0L
                mutableUiState.update { it.copy(elapsedSeconds = elapsed) }
                delay(250)
            }
        }
        refreshCapabilities()
    }

    fun refreshCapabilities() {
        mutableUiState.update { it.copy(capabilities = capabilityMonitor.snapshot()) }
    }

    fun navigate(destination: FlowerWhispDestination) = updateUi { copy(destination = destination) }
    fun advanceOnboarding(step: OnboardingStep) = updateUi { copy(onboardingStep = step) }
    fun selectLibrary(section: LibrarySection) = updateUi { copy(librarySection = section) }
    fun searchHistory(query: String) = updateUi { copy(historyQuery = query) }

    fun completeOnboarding() = updateSettings { it.copy(onboardingComplete = true) }

    fun startDictation() {
        val failure = DictationService.startFromVisibleInAppAction(application)
        updateUi { copy(serviceMessage = failure) }
    }

    fun finishDictation() = DictationService.stopFromBubble(application)

    fun cancelDictation() {
        DictationService.cancel(application)
    }

    fun openHistory(id: Long) = viewModelScope.launch {
        updateUi { copy(selectedDictation = container.historyRepository.get(id)) }
    }

    fun closeHistory() = updateUi { copy(selectedDictation = null) }

    fun favoriteHistory(id: Long, favorite: Boolean) = launchAction {
        container.historyRepository.setFavorite(id, favorite)
    }

    fun deleteHistory(id: Long) = launchAction {
        container.historyRepository.get(id)?.recoveryAudioPath?.let { File(it).delete() }
        container.historyRepository.delete(id)
        updateUi { copy(selectedDictation = selectedDictation?.takeUnless { it.id == id }) }
    }

    fun retryHistory(id: Long) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        val path = item.recoveryAudioPath ?: error("No recovery audio is available")
        val file = File(path)
        require(file.isFile && file.length() > 0L) { "The recovery recording is missing" }
        processDictation(
            recording = AudioRecording(file, item.durationMs),
            settings = mutableUiState.value.settings,
            transcriptionEngine = container.transcriptionEngine,
            refinementEngine = container.refinementEngine,
        )
        container.historyRepository.delete(id)
    }

    fun withHistoryText(id: Long, consume: (String) -> Unit) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        consume(item.refinedText.ifBlank { item.originalText })
    }

    fun upsertDictionary(entry: DictionaryEntry) = launchAction { container.dictionaryRepository.upsert(entry) }
    fun deleteDictionary(id: Long) = launchAction { container.dictionaryRepository.delete(id) }
    fun upsertSnippet(snippet: Snippet) = launchAction { container.snippetRepository.upsert(snippet) }
    fun deleteSnippet(id: Long) = launchAction { container.snippetRepository.delete(id) }

    fun setLanguage(value: LanguageMode) = updateSettings { it.copy(language = value) }
    fun setWritingStyle(value: WritingStyle) = updateSettings { it.copy(writingStyle = value) }
    fun setAutoPunctuation(value: Boolean) = updateSettings { it.copy(autoPunctuation = value) }
    fun setRemoveFillers(value: Boolean) = updateSettings { it.copy(removeFillers = value) }
    fun setSpokenCorrections(value: Boolean) = updateSettings { it.copy(spokenCorrections = value) }
    fun setAiRefinement(value: Boolean) = updateSettings { it.copy(aiRefinement = value) }
    fun setHaptics(value: Boolean) = updateSettings { it.copy(haptics = value) }
    fun setReduceMotion(value: Boolean) = updateSettings { it.copy(reduceMotion = value) }
    fun setPrivacy(value: Boolean) = updateSettings { it.copy(privacyMode = value) }
    fun setBubbleSize(value: BubbleSize) = updateSettings { it.copy(bubbleSize = value) }
    fun setBubbleOpacity(value: BubbleOpacity) = updateSettings { it.copy(bubbleOpacity = value) }
    fun setIdleBehavior(value: IdleBehavior) = updateSettings { it.copy(idleBehavior = value) }
    fun setUseMockEngines(value: Boolean) = updateSettings { it.copy(useMockEngines = value) }
    fun setRefinementPrompt(value: String) = updateSettings { it.copy(refinementPrompt = value) }

    fun saveApiKey(value: String) = launchAction {
        container.settingsRepository.setGroqApiKey(value)
        updateUi { copy(groqApiKeyConfigured = true, serviceMessage = "Groq key saved securely") }
    }

    fun clearApiKey() = launchAction {
        container.settingsRepository.clearGroqApiKey()
        updateUi { copy(groqApiKeyConfigured = false, serviceMessage = "Groq key removed") }
    }

    fun snooze() {
        val until = System.currentTimeMillis() + 10 * 60 * 1_000L
        updateSettings { it.copy(snoozedUntilEpochMs = until) }
        DictationRuntime.snooze(until)
    }

    fun wake() {
        updateSettings { it.copy(snoozedUntilEpochMs = 0L) }
        DictationRuntime.resetToAvailability(false)
    }

    fun restartService() {
        refreshCapabilities()
        DictationRuntime.resetToAvailability(false)
        updateUi { copy(serviceMessage = "FlowerWhisp will reconnect when a supported text field is focused") }
    }

    fun message(value: String?) = updateUi { copy(serviceMessage = value) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = launchAction {
        container.settingsRepository.update(transform)
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { failure ->
                    updateUi { copy(serviceMessage = failure.message ?: "FlowerWhisp could not finish that action") }
                }
        }
    }

    private inline fun updateUi(transform: FlowerWhispUiState.() -> FlowerWhispUiState) {
        mutableUiState.update(transform)
    }
}
