package com.flowerwhisp.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationDao {
    @Query("SELECT * FROM dictations ORDER BY createdAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<DictationEntity>>

    @Query(
        """
        SELECT * FROM dictations
        WHERE :query = ''
           OR originalText LIKE '%' || :query || '%' COLLATE NOCASE
           OR refinedText LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY createdAtEpochMs DESC, id DESC
        """,
    )
    fun search(query: String): Flow<List<DictationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dictation: DictationEntity): Long

    @Query("SELECT * FROM dictations WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): DictationEntity?

    @Query("UPDATE dictations SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM dictations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM dictations WHERE createdAtEpochMs < :cutoffEpochMs")
    suspend fun getBefore(cutoffEpochMs: Long): List<DictationEntity>

    @Query("DELETE FROM dictations WHERE createdAtEpochMs < :cutoffEpochMs")
    suspend fun deleteBefore(cutoffEpochMs: Long)
}

@Dao
interface DictionaryEntryDao {
    @Query("SELECT * FROM dictionary_entries ORDER BY spelling COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<DictionaryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DictionaryEntryEntity): Long

    @Query("DELETE FROM dictionary_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY trigger COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<SnippetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: SnippetEntity): Long

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TransformProfileDao {
    @Query("SELECT * FROM transform_profiles ORDER BY builtIn DESC, name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<TransformProfileEntity>>

    @Query("SELECT COUNT(*) FROM transform_profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transform: TransformProfileEntity): Long

    @Query("DELETE FROM transform_profiles WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long)
}
