package com.flowerwhisp.mobile.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet

@Entity(
    tableName = "dictations",
    indices = [Index(value = ["createdAtEpochMs"])],
)
data class DictationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    val originalText: String,
    val refinedText: String,
    val durationMs: Long,
    val language: String,
    val status: String,
    val isFavorite: Boolean,
    val recoveryAudioPath: String?,
)

@Entity(tableName = "dictionary_entries")
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spelling: String,
    val pronunciationOrContext: String,
    val replacement: String,
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val expansion: String,
)

fun Dictation.toEntity(): DictationEntity = DictationEntity(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    originalText = originalText,
    refinedText = refinedText,
    durationMs = durationMs,
    language = language.name,
    status = status.name,
    isFavorite = isFavorite,
    recoveryAudioPath = recoveryAudioPath,
)

fun DictationEntity.toDomain(): Dictation = Dictation(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    originalText = originalText,
    refinedText = refinedText,
    durationMs = durationMs,
    language = language.toLanguageMode(),
    status = status.toDictationStatus(),
    isFavorite = isFavorite,
    recoveryAudioPath = recoveryAudioPath,
)

fun DictionaryEntry.toEntity(): DictionaryEntryEntity = DictionaryEntryEntity(
    id = id,
    spelling = spelling,
    pronunciationOrContext = pronunciationOrContext,
    replacement = replacement,
)

fun DictionaryEntryEntity.toDomain(): DictionaryEntry = DictionaryEntry(
    id = id,
    spelling = spelling,
    pronunciationOrContext = pronunciationOrContext,
    replacement = replacement,
)

fun Snippet.toEntity(): SnippetEntity = SnippetEntity(
    id = id,
    trigger = trigger,
    expansion = expansion,
)

fun SnippetEntity.toDomain(): Snippet = Snippet(
    id = id,
    trigger = trigger,
    expansion = expansion,
)

fun String.toLanguageMode(): LanguageMode =
    LanguageMode.entries.firstOrNull { it.name == this } ?: LanguageMode.AUTO

fun String.toDictationStatus(): DictationStatus =
    DictationStatus.entries.firstOrNull { it.name == this } ?: DictationStatus.CANCELLED
