package com.flowerwhisp.mobile.data

import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.DEFAULT_TRANSFORMS
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.RetentionMode
import com.flowerwhisp.mobile.domain.model.TransformProfile
import com.flowerwhisp.mobile.domain.ports.DictionaryRepository
import com.flowerwhisp.mobile.domain.ports.HistoryRepository
import com.flowerwhisp.mobile.domain.ports.SettingsRepository
import com.flowerwhisp.mobile.domain.ports.SnippetRepository
import com.flowerwhisp.mobile.domain.ports.TransformRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room-backed history. The optional provider lets the application container wire privacy mode
 * without changing the domain port. A privacy-enabled upsert is a no-op; explicit user deletes
 * and favorite changes still apply to already persisted rows.
 */
class RoomHistoryRepository(
    private val dao: DictationDao,
    private val privacyModeEnabled: suspend () -> Boolean = { false },
    private val retentionMode: suspend () -> RetentionMode = {
        if (privacyModeEnabled()) RetentionMode.NEVER else RetentionMode.FOREVER
    },
) : HistoryRepository {
    constructor(dao: DictationDao, settingsRepository: SettingsRepository) : this(
        dao = dao,
        privacyModeEnabled = { settingsRepository.settings.first().privacyMode },
        retentionMode = { settingsRepository.settings.first().retentionMode },
    )

    override fun observeAll(): Flow<List<Dictation>> = dao.observeAll().map { rows ->
        rows.map(DictationEntity::toDomain)
    }

    override fun search(query: String): Flow<List<Dictation>> =
        dao.search(query.trim()).map { rows -> rows.map(DictationEntity::toDomain) }

    override suspend fun upsert(dictation: Dictation): Long =
        if (
            retentionMode() == RetentionMode.NEVER &&
            dictation.id == 0L &&
            dictation.status == DictationStatus.COMPLETE &&
            dictation.cleanupStatus != CleanupStatus.FAILED &&
            dictation.recoveryAudioPath == null
        ) {
            dictation.id
        } else {
            dao.upsert(dictation.toEntity())
        }

    override suspend fun get(id: Long): Dictation? = dao.get(id)?.toDomain()

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    suspend fun pruneBefore(cutoffEpochMs: Long): List<String> {
        val recoveryPaths = dao.getBefore(cutoffEpochMs).mapNotNull { it.recoveryAudioPath }
        dao.deleteBefore(cutoffEpochMs)
        return recoveryPaths
    }
}

/** Room-backed custom dictionary entries. */
class RoomDictionaryRepository(
    private val dao: DictionaryEntryDao,
) : DictionaryRepository {
    override fun observeAll(): Flow<List<DictionaryEntry>> = dao.observeAll().map { rows ->
        rows.map(DictionaryEntryEntity::toDomain)
    }

    override suspend fun upsert(entry: DictionaryEntry): Long = dao.upsert(entry.toEntity())

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}

/** Room-backed text snippets. */
class RoomSnippetRepository(
    private val dao: SnippetDao,
) : SnippetRepository {
    override fun observeAll(): Flow<List<Snippet>> = dao.observeAll().map { rows ->
        rows.map(SnippetEntity::toDomain)
    }

    override suspend fun upsert(snippet: Snippet): Long = dao.upsert(snippet.toEntity())

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}

class RoomTransformRepository(
    private val dao: TransformProfileDao,
) : TransformRepository {
    override fun observeAll(): Flow<List<TransformProfile>> = dao.observeAll().map { rows ->
        rows.map(TransformProfileEntity::toDomain)
    }

    override suspend fun upsert(transform: TransformProfile): Long = dao.upsert(transform.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteCustom(id)
    }

    suspend fun ensureDefaults() {
        if (dao.count() != 0) return
        DEFAULT_TRANSFORMS.forEach { transform -> dao.upsert(transform.toEntity()) }
    }
}
