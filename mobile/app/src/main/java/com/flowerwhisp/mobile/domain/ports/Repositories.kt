package com.flowerwhisp.mobile.domain.ports

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeAll(): Flow<List<Dictation>>
    fun search(query: String): Flow<List<Dictation>>
    suspend fun upsert(dictation: Dictation): Long
    suspend fun get(id: Long): Dictation?
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun delete(id: Long)
}

interface DictionaryRepository {
    fun observeAll(): Flow<List<DictionaryEntry>>
    suspend fun upsert(entry: DictionaryEntry): Long
    suspend fun delete(id: Long)
}

interface SnippetRepository {
    fun observeAll(): Flow<List<Snippet>>
    suspend fun upsert(snippet: Snippet): Long
    suspend fun delete(id: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun setGroqApiKey(value: String)
    suspend fun hasGroqApiKey(): Boolean
    suspend fun clearGroqApiKey()
    suspend fun groqApiKey(): String?
}
