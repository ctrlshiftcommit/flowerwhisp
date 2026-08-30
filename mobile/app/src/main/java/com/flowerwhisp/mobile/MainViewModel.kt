package com.flowerwhisp.mobile

import android.media.MediaPlayer
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.AppearanceMode
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import com.flowerwhisp.mobile.domain.model.BubbleState
import com.flowerwhisp.mobile.domain.model.CleanupLevel
import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.IdleBehavior
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.RetentionMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.StyleContext
import com.flowerwhisp.mobile.domain.model.TransformProfile
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.model.cleanupPrompt
import com.flowerwhisp.mobile.accessibility.FlowerWhispAccessibilityService
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    private var mediaPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                mutableUiState.update {
                    it.copy(
                        onboardingComplete = settings.onboardingComplete,
                        onboardingStep = OnboardingStep.entries.getOrElse(settings.onboardingStep.coerceIn(0, OnboardingStep.entries.lastIndex)) { OnboardingStep.WELCOME },
                        settings = settings,
                        refinementPromptDraft = settings.cleanupPrompt(),
                        groqApiKeyConfigured = container.settingsRepository.hasGroqApiKey(),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.historyRepository.observeAll()
                .catch { failure ->
                    mutableUiState.update {
                        it.copy(
                            historyLoading = false,
                            historyError = failure.message ?: "Saved dictations could not be loaded",
                        )
                    }
                }
                .collect { history ->
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
            container.transformRepository.ensureDefaults()
            container.transformRepository.observeAll().collect { transforms ->
                mutableUiState.update { it.copy(transforms = transforms) }
            }
        }
        viewModelScope.launch {
            DictationRuntime.bubbleState.collect { state ->
                mutableUiState.update { it.copy(bubbleState = state) }
            }
        }
        viewModelScope.launch {
            FlowerWhispAccessibilityService.connected.collect {
                refreshCapabilities()
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
        mutableUiState.update {
            it.copy(
                capabilities = capabilityMonitor.snapshot(
                    accessibilityConnected = FlowerWhispAccessibilityService.isConnected(),
                ),
            )
        }
    }

    fun navigate(destination: FlowerWhispDestination) = updateUi { copy(destination = destination) }
    fun advanceOnboarding(step: OnboardingStep) {
        updateUi { copy(onboardingStep = step) }
        updateSettings { it.copy(onboardingStep = step.ordinal) }
    }
    fun selectLibrary(section: LibrarySection) = updateUi { copy(librarySection = section) }
    fun searchHistory(query: String) = updateUi { copy(historyQuery = query) }
    fun refreshHistory() = viewModelScope.launch {
        updateUi { copy(historyLoading = true, historyError = null) }
        runCatching { container.historyRepository.observeAll().first() }
            .onSuccess { history -> updateUi { copy(history = history, historyLoading = false) } }
            .onFailure { failure ->
                updateUi {
                    copy(
                        historyLoading = false,
                        historyError = failure.message ?: "Saved dictations could not be loaded",
                    )
                }
            }
    }

    fun completeOnboarding() = updateSettings {
        it.copy(onboardingComplete = true, onboardingStep = OnboardingStep.READY.ordinal)
    }

    fun skipOnboarding() = completeOnboarding()

    fun startDictation() {
        val state = mutableUiState.value
        if (!state.settings.useMockEngines && !state.groqApiKeyConfigured) {
            updateUi {
                copy(
                    destination = FlowerWhispDestination.SETTINGS,
                    serviceMessage = "Add a Groq key before starting dictation",
                )
            }
            return
        }
        val failure = DictationService.startFromVisibleInAppAction(application)
        updateUi { copy(serviceMessage = failure) }
    }

    fun finishDictation() = DictationService.stopFromBubble(application)

    fun cancelDictation() {
        DictationService.cancel(application)
    }

    fun retryCurrentState() {
        val recoveryId = (mutableUiState.value.bubbleState as? BubbleState.ServiceError)
            ?.recoverableRecordingId
        if (recoveryId != null) retryHistory(recoveryId) else restartService()
    }

    fun openHistory(id: Long) = viewModelScope.launch {
        updateUi { copy(selectedDictation = container.historyRepository.get(id)) }
    }

    fun closeHistory() = updateUi { copy(selectedDictation = null) }

    fun favoriteHistory(id: Long, favorite: Boolean) = launchAction {
        container.historyRepository.setFavorite(id, favorite)
    }

    fun deleteHistory(id: Long) = launchAction {
        if (mutableUiState.value.playingDictationId == id) stopPlayback()
        container.historyRepository.get(id)?.recoveryAudioPath?.let { path ->
            val file = File(path)
            check(!file.exists() || file.delete()) { "The saved recording could not be deleted" }
        }
        container.historyRepository.delete(id)
        updateUi { copy(selectedDictation = selectedDictation?.takeUnless { it.id == id }) }
    }

    fun retryHistory(id: Long) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        val path = item.recoveryAudioPath ?: error("No recovery audio is available")
        val file = File(path)
        require(file.isFile && file.length() > 0L) { "The recovery recording is missing" }
        val result = processDictation(
            recording = AudioRecording(file, item.durationMs),
            settings = mutableUiState.value.settings,
            transcriptionEngine = container.transcriptionEngine,
            refinementEngine = container.refinementEngine,
        )
        when (result) {
            is com.flowerwhisp.mobile.domain.usecase.ProcessingResult.Complete -> {
                container.historyRepository.delete(id)
                val keepRecoveredText = mutableUiState.value.settings.retentionMode != RetentionMode.NEVER ||
                    result.dictation.cleanupStatus == CleanupStatus.FAILED
                DictationRuntime.resetToAvailability(false)
                updateUi {
                    copy(
                        selectedDictation = when {
                            selectedDictation?.id != id -> selectedDictation
                            keepRecoveredText -> result.dictation
                            else -> null
                        },
                        serviceMessage = "Transcript recovered",
                    )
                }
            }
            is com.flowerwhisp.mobile.domain.usecase.ProcessingResult.TranscriptionFailed -> {
                updateUi { copy(serviceMessage = result.reason) }
            }
        }
    }

    fun withHistoryText(id: Long, consume: (String) -> Unit) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        consume(item.refinedText.ifBlank { item.originalText })
    }

    fun withLatestHistoryText(consume: (String) -> Unit) = launchAction {
        val item = mutableUiState.value.history.firstOrNull() ?: error("No transcript is available yet")
        consume(item.refinedText.ifBlank { item.safeText.ifBlank { item.originalText } })
    }

    fun withHistoryAudio(id: Long, consume: (File) -> Unit) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        val path = item.recoveryAudioPath ?: error("No saved audio is available")
        val file = File(path)
        require(file.isFile && file.length() > 0L) { "The saved recording is missing" }
        consume(file)
    }

    fun undoHistoryCleanup(id: Long) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        require(item.originalText.isNotBlank()) { "The original transcript is unavailable" }
        val updated = item.copy(
            safeText = item.originalText,
            refinedText = item.originalText,
            cleanupStatus = CleanupStatus.DISABLED,
            cleanupError = null,
        )
        container.historyRepository.upsert(updated)
        updateUi {
            copy(
                selectedDictation = selectedDictation?.let { selected ->
                    if (selected.id == id) updated else selected
                },
                serviceMessage = "AI cleanup undone",
            )
        }
    }

    fun playHistory(id: Long) = viewModelScope.launch {
        if (mutableUiState.value.playingDictationId == id) {
            stopPlayback()
            return@launch
        }
        runCatching {
            val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
            val path = item.recoveryAudioPath ?: error("No saved audio is available")
            val file = File(path)
            require(file.isFile && file.length() > 0L) { "The saved recording is missing" }
            stopPlayback()
            MediaPlayer().also { player ->
                mediaPlayer = player
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener { stopPlayback() }
                player.setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    message("Audio playback failed")
                    true
                }
                player.prepare()
                player.start()
                updateUi { copy(playingDictationId = id) }
            }
        }.onFailure { failure ->
            stopPlayback()
            message(failure.message ?: "Audio playback failed")
        }
    }

    fun deleteHistoryAudio(id: Long) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        if (mutableUiState.value.playingDictationId == id) stopPlayback()
        item.recoveryAudioPath?.let { path ->
            val file = File(path)
            check(!file.exists() || file.delete()) { "The saved recording could not be deleted" }
        }
        val updated = item.copy(recoveryAudioPath = null)
        container.historyRepository.upsert(updated)
        updateUi { copy(selectedDictation = selectedDictation?.let { selected -> if (selected.id == id) updated else selected }) }
    }

    fun sendHistoryToScratchpad(id: Long) = launchAction {
        val item = container.historyRepository.get(id) ?: error("That dictation no longer exists")
        val text = item.refinedText.ifBlank { item.safeText.ifBlank { item.originalText } }.trim()
        require(text.isNotBlank()) { "That dictation has no text" }
        container.settingsRepository.update { settings ->
            val joined = if (settings.scratchpad.isBlank()) text else "${settings.scratchpad.trim()}\n\n$text"
            settings.copy(scratchpad = joined.take(MAX_SCRATCHPAD_CHARS))
        }
        updateUi { copy(destination = FlowerWhispDestination.SCRATCHPAD, selectedDictation = null) }
    }

    fun upsertDictionary(entry: DictionaryEntry) = launchAction { container.dictionaryRepository.upsert(entry) }
    fun deleteDictionary(id: Long) = launchAction { container.dictionaryRepository.delete(id) }
    fun setDictionaryEnabled(entry: DictionaryEntry, enabled: Boolean) = upsertDictionary(entry.copy(enabled = enabled))
    fun upsertSnippet(snippet: Snippet) = launchAction { container.snippetRepository.upsert(snippet) }
    fun deleteSnippet(id: Long) = launchAction { container.snippetRepository.delete(id) }
    fun setSnippetEnabled(snippet: Snippet, enabled: Boolean) = upsertSnippet(snippet.copy(enabled = enabled))
    fun upsertTransform(transform: TransformProfile) = launchAction { container.transformRepository.upsert(transform) }
    fun deleteTransform(id: Long) = launchAction { container.transformRepository.delete(id) }

    fun runHistoryTransform(dictationId: Long, transformId: Long) = viewModelScope.launch {
        val item = container.historyRepository.get(dictationId)
        val source = item?.let { record ->
            record.refinedText.ifBlank { record.safeText.ifBlank { record.originalText } }
        }.orEmpty()
        runTransform(source, transformId)
    }

    fun runScratchpadTransform(transformId: Long) = viewModelScope.launch {
        runTransform(mutableUiState.value.settings.scratchpad, transformId)
    }

    fun dismissTransform() = updateUi { copy(transformPreview = null, transformBusy = false) }
    fun saveScratchpad(value: String) = updateSettings { it.copy(scratchpad = value.take(MAX_SCRATCHPAD_CHARS)) }

    fun setLanguage(value: LanguageMode) = updateSettings { it.copy(language = value) }
    fun setWritingStyle(value: WritingStyle) = updateSettings { it.copy(writingStyle = value) }
    fun setContextWritingStyle(context: StyleContext, value: WritingStyle) = updateSettings {
        when (context) {
            StyleContext.PERSONAL -> it.copy(personalWritingStyle = value)
            StyleContext.WORK -> it.copy(workWritingStyle = value)
            StyleContext.EMAIL -> it.copy(emailWritingStyle = value)
            StyleContext.OTHER -> it.copy(otherWritingStyle = value, writingStyle = value)
        }
    }
    fun setContextStyleInstructions(context: StyleContext, value: String) = updateSettings {
        val instructions = value.trim().take(2_000)
        when (context) {
            StyleContext.PERSONAL -> it.copy(personalStyleInstructions = instructions)
            StyleContext.WORK -> it.copy(workStyleInstructions = instructions)
            StyleContext.EMAIL -> it.copy(emailStyleInstructions = instructions)
            StyleContext.OTHER -> it.copy(otherStyleInstructions = instructions)
        }
    }
    fun setAutoPunctuation(value: Boolean) = updateSettings { it.copy(autoPunctuation = value) }
    fun setRemoveFillers(value: Boolean) = updateSettings { it.copy(removeFillers = value) }
    fun setSpokenCorrections(value: Boolean) = updateSettings { it.copy(spokenCorrections = value) }
    fun setAiRefinement(value: Boolean) = updateSettings {
        it.copy(
            aiRefinement = value,
            cleanupLevel = if (value) it.cleanupLevel.takeUnless { level -> level == CleanupLevel.NONE }
                ?: CleanupLevel.LIGHT else CleanupLevel.NONE,
        )
    }
    fun setCleanupLevel(value: CleanupLevel) = updateSettings {
        it.copy(cleanupLevel = value, aiRefinement = value != CleanupLevel.NONE)
    }
    fun setCleanupPrompt(level: CleanupLevel, value: String) = updateSettings {
        when (level) {
            CleanupLevel.NONE -> it.copy(cleanupPromptNone = value)
            CleanupLevel.LIGHT -> it.copy(cleanupPromptLight = value, refinementPrompt = value)
            CleanupLevel.MEDIUM -> it.copy(cleanupPromptMedium = value)
        }
    }
    fun setTranscriptionModel(value: String) = updateSettings { it.copy(groqTranscriptionModel = value) }
    fun setRefinementModel(value: String) = updateSettings { it.copy(groqRefinementModel = value) }
    fun setHaptics(value: Boolean) = updateSettings { it.copy(haptics = value) }
    fun setPlaySounds(value: Boolean) = updateSettings { it.copy(playSounds = value) }
    fun setMuteMusic(value: Boolean) = updateSettings { it.copy(muteMusicWhileDictating = value) }
    fun setReduceMotion(value: Boolean) = updateSettings { it.copy(reduceMotion = value) }
    fun setPrivacy(value: Boolean) = updateSettings {
        it.copy(
            privacyMode = value,
            retentionMode = if (value) RetentionMode.NEVER else RetentionMode.FOREVER,
        )
    }
    fun setRetention(value: RetentionMode) = updateSettings {
        it.copy(retentionMode = value, privacyMode = value == RetentionMode.NEVER)
    }
    fun setAppearance(value: AppearanceMode) = updateSettings { it.copy(appearanceMode = value) }
    fun setBubbleSize(value: BubbleSize) = updateSettings { it.copy(bubbleSize = value) }
    fun setBubbleOpacity(value: BubbleOpacity) = updateSettings { it.copy(bubbleOpacity = value) }
    fun setIdleBehavior(value: IdleBehavior) = updateSettings { it.copy(idleBehavior = value) }
    fun setUseMockEngines(value: Boolean) = updateSettings { it.copy(useMockEngines = value) }
    fun setRefinementPrompt(value: String) = setCleanupPrompt(CleanupLevel.LIGHT, value)

    fun saveApiKey(value: String) = launchAction {
        container.settingsRepository.setGroqApiKey(value)
        container.settingsRepository.update { it.copy(useMockEngines = false) }
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

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

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

    private suspend fun runTransform(source: String, transformId: Long) {
        if (source.isBlank()) {
            updateUi { copy(serviceMessage = "Nothing to transform") }
            return
        }
        val transform = mutableUiState.value.transforms.firstOrNull { it.id == transformId && it.enabled }
        if (transform == null) {
            updateUi { copy(serviceMessage = "That transform is unavailable") }
            return
        }
        updateUi { copy(transformBusy = true, serviceMessage = null) }
        runCatching {
            container.transformEngine.transform(source, transform.instructions, mutableUiState.value.settings)
        }.onSuccess { result ->
            updateUi {
                copy(
                    transformBusy = false,
                    transformPreview = com.flowerwhisp.mobile.ui.app.TransformPreview(
                        name = transform.name,
                        source = source,
                        result = result,
                    ),
                )
            }
        }.onFailure { failure ->
            updateUi {
                copy(
                    transformBusy = false,
                    serviceMessage = failure.message ?: "Transform failed",
                )
            }
        }
    }

    private fun stopPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        updateUi { copy(playingDictationId = null) }
    }

    private inline fun updateUi(transform: FlowerWhispUiState.() -> FlowerWhispUiState) {
        mutableUiState.update(transform)
    }

    private companion object {
        const val MAX_SCRATCHPAD_CHARS = 100_000
    }
}
