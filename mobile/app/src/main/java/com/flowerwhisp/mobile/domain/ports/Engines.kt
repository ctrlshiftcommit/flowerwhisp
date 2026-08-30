package com.flowerwhisp.mobile.domain.ports

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class AudioRecording(val file: File, val durationMs: Long)

interface AudioRecorder {
    val level: StateFlow<Float>
    suspend fun start(output: File)
    suspend fun stop(): AudioRecording
    suspend fun cancel()
}

interface TranscriptionEngine {
    suspend fun transcribe(audio: File, language: LanguageMode): String
}

interface TextRefinementEngine {
    suspend fun refine(
        source: String,
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String
}

interface TextTransformEngine {
    suspend fun transform(
        source: String,
        instructions: String,
        settings: AppSettings,
    ): String
}

sealed interface InsertionResult {
    data object Inserted : InsertionResult
    data class ClipboardFallback(val text: String, val reason: String) : InsertionResult
    data object NoFocusedField : InsertionResult
    data object SensitiveField : InsertionResult
}

interface TextInsertionGateway {
    suspend fun insert(text: String): InsertionResult
    fun hasSupportedFocusedField(): Boolean
}
