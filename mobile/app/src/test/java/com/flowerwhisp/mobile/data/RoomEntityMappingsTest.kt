package com.flowerwhisp.mobile.data

import androidx.datastore.preferences.core.emptyPreferences
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomEntityMappingsTest {
    @Test
    fun dictationMappingPreservesAllFields() {
        val source = Dictation(
            id = 42,
            createdAtEpochMs = 1234,
            originalText = "Original नमस्ते",
            refinedText = "Refined text",
            durationMs = 987,
            language = LanguageMode.HINGLISH,
            status = DictationStatus.REFINEMENT_FAILED,
            isFavorite = true,
            recoveryAudioPath = "recordings/recovery.m4a",
        )

        assertEquals(source, source.toEntity().toDomain())
    }

    @Test
    fun personalizationMappingsPreserveAllFields() {
        val dictionary = DictionaryEntry(
            id = 7,
            spelling = "FlowerWhisp",
            pronunciationOrContext = "product name",
            replacement = "FlowerWhisp",
        )
        val snippet = Snippet(id = 8, trigger = ";addr", expansion = "123 Main Street")

        assertEquals(dictionary, dictionary.toEntity().toDomain())
        assertEquals(snippet, snippet.toEntity().toDomain())
    }

    @Test
    fun unknownStoredEnumsUseSafeDefaults() {
        assertEquals(LanguageMode.AUTO, "future_language".toLanguageMode())
        assertEquals(DictationStatus.CANCELLED, "future_status".toDictationStatus())
    }

    @Test
    fun emptyPreferencesMapToDomainDefaults() {
        assertEquals(AppSettings(), emptyPreferences().toAppSettings())
    }

    @Test
    fun privacyModeSuppressesFutureHistoryUpserts() = runBlocking {
        val dao = RecordingDictationDao()
        val repository = RoomHistoryRepository(dao, privacyModeEnabled = { true })
        val dictation = Dictation(
            createdAtEpochMs = 100,
            originalText = "private",
            refinedText = "private",
            durationMs = 200,
            language = LanguageMode.ENGLISH,
            status = DictationStatus.COMPLETE,
        )

        assertEquals(0L, repository.upsert(dictation))
        assertEquals(0, dao.upsertCount)
    }

    @Test
    fun replacementDatabaseUsesIsolatedFilename() {
        assertEquals("flowerwhisp_v2.db", FlowerWhispDatabase.DATABASE_NAME)
    }

    private class RecordingDictationDao : DictationDao {
        var upsertCount = 0

        override fun observeAll(): Flow<List<DictationEntity>> = flowOf(emptyList())

        override fun search(query: String): Flow<List<DictationEntity>> = flowOf(emptyList())

        override suspend fun upsert(dictation: DictationEntity): Long {
            upsertCount += 1
            return 1
        }

        override suspend fun get(id: Long): DictationEntity? = null

        override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit

        override suspend fun delete(id: Long) = Unit
    }
}
