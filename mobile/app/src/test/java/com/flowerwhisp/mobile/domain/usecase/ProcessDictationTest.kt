package com.flowerwhisp.mobile.domain.usecase

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.ports.AudioRecording
import com.flowerwhisp.mobile.domain.ports.DictionaryRepository
import com.flowerwhisp.mobile.domain.ports.HistoryRepository
import com.flowerwhisp.mobile.domain.ports.SnippetRepository
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProcessDictationTest {
    @Test
    fun refinementFailurePreservesRawTextAndRecoveryAudio() = runBlocking {
        val history = MemoryHistory()
        val audio = File.createTempFile("flowerwhisp", ".m4a")
        val result = ProcessDictation(history, EmptyDictionary, EmptySnippets)(
            recording = AudioRecording(audio, 1_200),
            settings = AppSettings(aiRefinement = true),
            transcriptionEngine = TranscriptionEngine { _, _ -> "Keep this exact text" },
            refinementEngine = object : TextRefinementEngine {
                override suspend fun refine(source: String, style: WritingStyle, settings: AppSettings, dictionary: List<DictionaryEntry>, snippets: List<Snippet>): String = error("provider unavailable")
            },
        )

        assertTrue(result is ProcessingResult.RefinementFailed)
        val record = (result as ProcessingResult.RefinementFailed).dictation
        assertEquals("Keep this exact text", record.originalText)
        assertEquals("Keep this exact text", record.refinedText)
        assertTrue(audio.exists())
        audio.delete()
        Unit
    }
}

private class MemoryHistory : HistoryRepository {
    private val records = linkedMapOf<Long, Dictation>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<Dictation>> = flowOf(records.values.toList())
    override fun search(query: String): Flow<List<Dictation>> = observeAll()
    override suspend fun upsert(dictation: Dictation): Long {
        val id = dictation.id.takeIf { it != 0L } ?: nextId++
        records[id] = dictation.copy(id = id)
        return id
    }
    override suspend fun get(id: Long): Dictation? = records[id]
    override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
    override suspend fun delete(id: Long) { records.remove(id) }
}

private object EmptyDictionary : DictionaryRepository {
    override fun observeAll(): Flow<List<DictionaryEntry>> = flowOf(emptyList())
    override suspend fun upsert(entry: DictionaryEntry): Long = 0
    override suspend fun delete(id: Long) = Unit
}

private object EmptySnippets : SnippetRepository {
    override fun observeAll(): Flow<List<Snippet>> = flowOf(emptyList())
    override suspend fun upsert(snippet: Snippet): Long = 0
    override suspend fun delete(id: Long) = Unit
}

private fun interface TranscriptionEngine : com.flowerwhisp.mobile.domain.ports.TranscriptionEngine {
    override suspend fun transcribe(audio: File, language: LanguageMode): String
}
