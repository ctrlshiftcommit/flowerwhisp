package com.flowerwhisp.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.DictionaryScope
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.TransformProfile

@Entity(
    tableName = "dictations",
    indices = [Index(value = ["createdAtEpochMs"])],
)
data class DictationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    val originalText: String,
    @ColumnInfo(defaultValue = "''") val safeText: String,
    val refinedText: String,
    val durationMs: Long,
    val language: String,
    val status: String,
    val isFavorite: Boolean,
    val recoveryAudioPath: String?,
    @ColumnInfo(defaultValue = "'DISABLED'") val cleanupStatus: String,
    val cleanupError: String?,
)

@Entity(tableName = "dictionary_entries")
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spelling: String,
    val pronunciationOrContext: String,
    val replacement: String,
    @ColumnInfo(defaultValue = "'ALL'") val scope: String,
    @ColumnInfo(defaultValue = "1") val isProtected: Boolean,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean,
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val expansion: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean,
)

@Entity(tableName = "transform_profiles")
data class TransformProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val instructions: String,
    val enabled: Boolean,
    val builtIn: Boolean,
)

fun Dictation.toEntity(): DictationEntity = DictationEntity(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    originalText = originalText,
    safeText = safeText,
    refinedText = refinedText,
    durationMs = durationMs,
    language = language.name,
    status = status.name,
    isFavorite = isFavorite,
    recoveryAudioPath = recoveryAudioPath,
    cleanupStatus = cleanupStatus.name,
    cleanupError = cleanupError,
)

fun DictationEntity.toDomain(): Dictation = Dictation(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    originalText = originalText,
    safeText = safeText.ifBlank { originalText },
    refinedText = refinedText,
    durationMs = durationMs,
    language = language.toLanguageMode(),
    status = status.toDictationStatus(),
    isFavorite = isFavorite,
    recoveryAudioPath = recoveryAudioPath,
    cleanupStatus = cleanupStatus.toCleanupStatus(),
    cleanupError = cleanupError,
)

fun DictionaryEntry.toEntity(): DictionaryEntryEntity = DictionaryEntryEntity(
    id = id,
    spelling = spelling,
    pronunciationOrContext = pronunciationOrContext,
    replacement = replacement,
    scope = scope.name,
    isProtected = isProtected,
    enabled = enabled,
)

fun DictionaryEntryEntity.toDomain(): DictionaryEntry = DictionaryEntry(
    id = id,
    spelling = spelling,
    pronunciationOrContext = pronunciationOrContext,
    replacement = replacement,
    scope = scope.toDictionaryScope(),
    isProtected = isProtected,
    enabled = enabled,
)

fun Snippet.toEntity(): SnippetEntity = SnippetEntity(
    id = id,
    trigger = trigger,
    expansion = expansion,
    enabled = enabled,
)

fun SnippetEntity.toDomain(): Snippet = Snippet(
    id = id,
    trigger = trigger,
    expansion = expansion,
    enabled = enabled,
)

fun TransformProfile.toEntity(): TransformProfileEntity = TransformProfileEntity(
    id = id,
    name = name,
    description = description,
    instructions = instructions,
    enabled = enabled,
    builtIn = builtIn,
)

fun TransformProfileEntity.toDomain(): TransformProfile = TransformProfile(
    id = id,
    name = name,
    description = description,
    instructions = instructions,
    enabled = enabled,
    builtIn = builtIn,
)

fun String.toLanguageMode(): LanguageMode =
    LanguageMode.entries.firstOrNull { it.name == this } ?: LanguageMode.AUTO

fun String.toDictationStatus(): DictationStatus =
    DictationStatus.entries.firstOrNull { it.name == this } ?: DictationStatus.CANCELLED

fun String.toCleanupStatus(): CleanupStatus =
    CleanupStatus.entries.firstOrNull { it.name == this } ?: CleanupStatus.DISABLED

fun String.toDictionaryScope(): DictionaryScope =
    when (this) {
        // Version 2 called the work-app scope "technical". Preserve those rows on upgrade.
        "TECHNICAL" -> DictionaryScope.WORK
        else -> DictionaryScope.entries.firstOrNull { it.name == this } ?: DictionaryScope.ALL
    }
