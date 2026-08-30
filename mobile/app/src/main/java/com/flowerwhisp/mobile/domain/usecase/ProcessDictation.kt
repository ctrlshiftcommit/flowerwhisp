package com.flowerwhisp.mobile.domain.usecase

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.CleanupLevel
import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.RetentionMode
import com.flowerwhisp.mobile.domain.ports.AudioRecording
import com.flowerwhisp.mobile.domain.ports.DictionaryRepository
import com.flowerwhisp.mobile.domain.ports.HistoryRepository
import com.flowerwhisp.mobile.domain.ports.SnippetRepository
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import com.flowerwhisp.mobile.refinement.DeterministicTextRefiner
import kotlinx.coroutines.flow.first

sealed interface ProcessingResult {
    data class Complete(val dictation: Dictation) : ProcessingResult
    data class TranscriptionFailed(val recovery: Dictation, val reason: String) : ProcessingResult
}

class ProcessDictation(
    private val historyRepository: HistoryRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val snippetRepository: SnippetRepository,
) {
    suspend operator fun invoke(
        recording: AudioRecording,
        settings: AppSettings,
        transcriptionEngine: TranscriptionEngine,
        refinementEngine: TextRefinementEngine,
    ): ProcessingResult {
        val createdAt = System.currentTimeMillis()
        var record = Dictation(
            createdAtEpochMs = createdAt,
            originalText = "",
            refinedText = "",
            durationMs = recording.durationMs,
            language = settings.language,
            status = DictationStatus.PROCESSING,
            recoveryAudioPath = recording.file.absolutePath,
        )
        val pendingId = historyRepository.upsert(record)
        record = record.copy(id = pendingId)

        val raw = try {
            transcriptionEngine.transcribe(recording.file, settings.language).trim()
                .takeIf(String::isNotEmpty)
                ?: error("No speech was detected in the recording")
        } catch (error: Exception) {
            val failed = record.copy(status = DictationStatus.TRANSCRIPTION_FAILED)
            historyRepository.upsert(failed)
            return ProcessingResult.TranscriptionFailed(failed, error.safeMessage("Transcription failed"))
        }

        val dictionary = dictionaryRepository.observeAll().first()
        val snippets = snippetRepository.observeAll().first()
        val safeText = DeterministicTextRefiner.refine(
            source = raw,
            style = settings.writingStyle,
            settings = settings,
            dictionary = dictionary,
            snippets = snippets,
        )
        var cleanupStatus = CleanupStatus.DISABLED
        var cleanupError: String? = null
        val refined = if (settings.aiRefinement && settings.cleanupLevel != CleanupLevel.NONE) {
            try {
                refinementEngine.refine(safeText, settings.writingStyle, settings, dictionary, snippets).trim()
                    .takeIf(String::isNotEmpty)
                    ?.also { cleanupStatus = if (it == safeText) CleanupStatus.UNCHANGED else CleanupStatus.APPLIED }
                    ?: safeText.also { cleanupStatus = CleanupStatus.UNCHANGED }
            } catch (error: Exception) {
                cleanupStatus = CleanupStatus.FAILED
                cleanupError = error.safeMessage("Text cleanup failed")
                safeText
            }
        } else {
            safeText
        }

        val retainAudio = settings.retentionMode != RetentionMode.NEVER || cleanupStatus == CleanupStatus.FAILED
        if (!retainAudio) recording.file.delete()
        val complete = record.copy(
            originalText = raw,
            safeText = safeText,
            refinedText = refined,
            status = DictationStatus.COMPLETE,
            recoveryAudioPath = recording.file.absolutePath.takeIf { retainAudio },
            cleanupStatus = cleanupStatus,
            cleanupError = cleanupError,
        )
        if (
            (settings.privacyMode || settings.retentionMode == RetentionMode.NEVER) &&
            cleanupStatus != CleanupStatus.FAILED
        ) {
            historyRepository.delete(pendingId)
        } else {
            historyRepository.upsert(complete)
        }
        return ProcessingResult.Complete(complete)
    }
}

private fun Throwable.safeMessage(fallback: String): String = message
    ?.take(240)
    ?.takeIf(String::isNotBlank)
    ?: fallback
