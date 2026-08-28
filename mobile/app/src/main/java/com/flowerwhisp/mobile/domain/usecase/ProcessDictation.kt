package com.flowerwhisp.mobile.domain.usecase

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.ports.AudioRecording
import com.flowerwhisp.mobile.domain.ports.DictionaryRepository
import com.flowerwhisp.mobile.domain.ports.HistoryRepository
import com.flowerwhisp.mobile.domain.ports.SnippetRepository
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import kotlinx.coroutines.flow.first

sealed interface ProcessingResult {
    data class Complete(val dictation: Dictation) : ProcessingResult
    data class RefinementFailed(val dictation: Dictation, val reason: String) : ProcessingResult
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
        val refined = if (settings.aiRefinement) {
            try {
                refinementEngine.refine(raw, settings.writingStyle, settings, dictionary, snippets).trim()
                    .takeIf(String::isNotEmpty)
                    ?: raw
            } catch (error: Exception) {
                val fallback = record.copy(
                    originalText = raw,
                    refinedText = raw,
                    status = DictationStatus.REFINEMENT_FAILED,
                )
                historyRepository.upsert(fallback)
                return ProcessingResult.RefinementFailed(fallback, error.safeMessage("Text refinement failed"))
            }
        } else {
            raw
        }

        recording.file.delete()
        val complete = record.copy(
            originalText = raw,
            refinedText = refined,
            status = DictationStatus.COMPLETE,
            recoveryAudioPath = null,
        )
        if (settings.privacyMode) {
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
