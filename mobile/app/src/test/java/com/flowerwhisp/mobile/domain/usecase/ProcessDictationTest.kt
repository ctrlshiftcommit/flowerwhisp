package com.flowerwhisp.mobile.domain.usecase

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.RetentionMode
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
import org.junit.Assert.assertFalse
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
            settings = AppSettings(
                aiRefinement = true,
                retentionMode = RetentionMode.NEVER,
                privacyMode = true,
            ),
            transcriptionEngine = TranscriptionEngine { _, _ -> "Keep this exact text" },
            refinementEngine = object : TextRefinementEngine {
                override suspend fun refine(source: String, style: WritingStyle, settings: AppSettings, dictionary: List<DictionaryEntry>, snippets: List<Snippet>): String = error("provider unavailable")
            },
        )

        assertTrue(result is ProcessingResult.Complete)
        val record = (result as ProcessingResult.Complete).dictation
        assertEquals("Keep this exact text", record.originalText)
        assertEquals("Keep this exact text.", record.safeText)
        assertEquals("Keep this exact text.", record.refinedText)
        assertEquals(CleanupStatus.FAILED, record.cleanupStatus)
        assertEquals(audio.absolutePath, record.recoveryAudioPath)
        assertEquals(record, history.get(record.id))
        assertTrue(audio.exists())
        audio.delete()
        Unit
    }

    @Test
    fun neverRetentionDeletesSuccessfulAudioAndHistory() = runBlocking {
        val history = MemoryHistory()
        val audio = File.createTempFile("flowerwhisp", ".m4a")
        val result = ProcessDictation(history, EmptyDictionary, EmptySnippets)(
            recording = AudioRecording(audio, 900),
            settings = AppSettings(
                aiRefinement = false,
                retentionMode = RetentionMode.NEVER,
                privacyMode = true,
            ),
            transcriptionEngine = TranscriptionEngine { _, _ -> "Do not retain this" },
            refinementEngine = object : TextRefinementEngine {
                override suspend fun refine(source: String, style: WritingStyle, settings: AppSettings, dictionary: List<DictionaryEntry>, snippets: List<Snippet>): String = source
            },
        )

        val record = (result as ProcessingResult.Complete).dictation
        assertEquals(null, record.recoveryAudioPath)
        assertEquals(null, history.get(record.id))
        assertFalse(audio.exists())
    }

    @Test
    fun deterministicPersonalizationRunsBeforeLlmCleanup() = runBlocking {
        val history = MemoryHistory()
        val audio = File.createTempFile("flowerwhisp", ".m4a")
        var cleanupInput = ""
        val result = ProcessDictation(
            history,
            FixedDictionary(listOf(DictionaryEntry(spelling = "flower wisp", replacement = "FlowerWhisp"))),
            FixedSnippets(listOf(Snippet(trigger = "/sig", expansion = "Thanks, Tushar"))),
        )(
            recording = AudioRecording(audio, 1_000),
            settings = AppSettings(
                autoPunctuation = false,
                removeFillers = false,
                spokenCorrections = false,
            ),
            transcriptionEngine = TranscriptionEngine { _, _ -> "send to flower wisp /sig" },
            refinementEngine = object : TextRefinementEngine {
                override suspend fun refine(source: String, style: WritingStyle, settings: AppSettings, dictionary: List<DictionaryEntry>, snippets: List<Snippet>): String {
                    cleanupInput = source
                    return source
                }
            },
        )

        val record = (result as ProcessingResult.Complete).dictation
        assertEquals("send to FlowerWhisp Thanks, Tushar", cleanupInput)
        assertEquals(cleanupInput, record.safeText)
        assertEquals(cleanupInput, record.refinedText)
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

private class FixedDictionary(private val entries: List<DictionaryEntry>) : DictionaryRepository {
    override fun observeAll(): Flow<List<DictionaryEntry>> = flowOf(entries)
    override suspend fun upsert(entry: DictionaryEntry): Long = 0
    override suspend fun delete(id: Long) = Unit
}

private class FixedSnippets(private val entries: List<Snippet>) : SnippetRepository {
    override fun observeAll(): Flow<List<Snippet>> = flowOf(entries)
    override suspend fun upsert(snippet: Snippet): Long = 0
    override suspend fun delete(id: Long) = Unit
}

private fun interface TranscriptionEngine : com.flowerwhisp.mobile.domain.ports.TranscriptionEngine {
    override suspend fun transcribe(audio: File, language: LanguageMode): String
}
