package com.flowerwhisp.mobile.domain.insights

import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.LanguageMode
import java.text.BreakIterator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class DailyInsight(
    val date: LocalDate,
    val sessions: Int,
    val words: Int,
    val speakingTimeMs: Long,
)

data class LanguageInsight(
    val language: LanguageMode,
    val sessions: Int,
    val words: Int,
    val speakingTimeMs: Long,
)

enum class DayPart(val label: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    NIGHT("Night"),
}

data class DayPartInsight(
    val part: DayPart,
    val sessions: Int,
    val words: Int,
)

data class InsightSnapshot(
    val totalSessions: Int,
    val attemptedSessions: Int,
    val failedSessions: Int,
    val cancelledSessions: Int,
    val totalWords: Int,
    val speakingTimeMs: Long,
    val averageWordsPerSession: Double?,
    val averageSessionDurationMs: Long?,
    val wordsPerMinute: Double?,
    val activeDays: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val longestSessionMs: Long,
    val completionRate: Double?,
    val favoriteSessions: Int,
    val insertionFallbacks: Int,
    val recoveryRecordings: Int,
    val cleanupApplied: Int,
    val cleanupUnchanged: Int,
    val cleanupFailed: Int,
    val cleanupDisabled: Int,
    val mostUsedLanguage: LanguageMode?,
    val bestDay: DailyInsight?,
    val recentDays: List<DailyInsight>,
    val languages: List<LanguageInsight>,
    val dayParts: List<DayPartInsight>,
) {
    val hasData: Boolean get() = totalSessions > 0
    val hasHistory: Boolean get() = attemptedSessions > 0
}

/**
 * Produces deterministic statistics from persisted history. Successful activity uses completed
 * dictations only; outcome statistics also include terminal failures and cancellations. No value
 * is estimated from typing speed or from data that FlowerWhisp does not store.
 */
fun calculateInsights(
    history: List<Dictation>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    dayCount: Int = 14,
): InsightSnapshot {
    val completed = history.filter { it.status == DictationStatus.COMPLETE }
    val finalized = history.filter { it.status.isFinalized }
    val failed = finalized.filter { it.status.isFailure }
    val completedWithFacts = completed.map { dictation ->
        InsightFacts(
            dictation = dictation,
            date = dictation.localDate(zone),
            dayPart = dictation.dayPart(zone),
            words = dictation.insightWordCount(),
            durationMs = dictation.durationMs.coerceAtLeast(0L),
        )
    }

    val totalWords = completedWithFacts.sumOf(InsightFacts::words)
    val speakingTimeMs = completedWithFacts.sumOf(InsightFacts::durationMs)
    val days = completedWithFacts.groupBy(InsightFacts::date)
    val dailyHistory = days.map { (date, facts) ->
        DailyInsight(
            date = date,
            sessions = facts.size,
            words = facts.sumOf(InsightFacts::words),
            speakingTimeMs = facts.sumOf(InsightFacts::durationMs),
        )
    }
    val activeDates = days.keys
    val boundedDayCount = dayCount.coerceIn(1, 31)
    val firstDay = today.minusDays((boundedDayCount - 1).toLong())
    val recentDays = (0 until boundedDayCount).map { offset ->
        val date = firstDay.plusDays(offset.toLong())
        dailyHistory.firstOrNull { it.date == date } ?: DailyInsight(date, 0, 0, 0L)
    }

    val languages = completedWithFacts
        .groupBy { it.dictation.language }
        .map { (language, facts) ->
            LanguageInsight(
                language = language,
                sessions = facts.size,
                words = facts.sumOf(InsightFacts::words),
                speakingTimeMs = facts.sumOf(InsightFacts::durationMs),
            )
        }
        .sortedWith(
            compareByDescending<LanguageInsight> { it.sessions }
                .thenByDescending { it.words }
                .thenBy { it.language.ordinal },
        )

    val dayParts = DayPart.entries.map { part ->
        val facts = completedWithFacts.filter { it.dayPart == part }
        DayPartInsight(part, facts.size, facts.sumOf(InsightFacts::words))
    }

    val outcomeCount = completed.size + failed.size
    return InsightSnapshot(
        totalSessions = completed.size,
        attemptedSessions = finalized.size,
        failedSessions = failed.size,
        cancelledSessions = finalized.count { it.status == DictationStatus.CANCELLED },
        totalWords = totalWords,
        speakingTimeMs = speakingTimeMs,
        averageWordsPerSession = completed.size.takeIf { it > 0 }?.let { totalWords.toDouble() / it },
        averageSessionDurationMs = completed.size.takeIf { it > 0 }?.let { speakingTimeMs / it },
        wordsPerMinute = speakingTimeMs.takeIf { it > 0L }?.let { totalWords * 60_000.0 / it },
        activeDays = activeDates.size,
        currentStreakDays = currentStreak(activeDates, today),
        longestStreakDays = longestStreak(activeDates),
        longestSessionMs = completedWithFacts.maxOfOrNull(InsightFacts::durationMs) ?: 0L,
        completionRate = outcomeCount.takeIf { it > 0 }?.let { completed.size.toDouble() / it },
        favoriteSessions = completed.count(Dictation::isFavorite),
        insertionFallbacks = finalized.count { it.status == DictationStatus.INSERTION_FAILED },
        recoveryRecordings = finalized.count { !it.recoveryAudioPath.isNullOrBlank() },
        cleanupApplied = finalized.count { it.cleanupStatus == CleanupStatus.APPLIED },
        cleanupUnchanged = finalized.count { it.cleanupStatus == CleanupStatus.UNCHANGED },
        cleanupFailed = finalized.count { it.cleanupStatus == CleanupStatus.FAILED },
        cleanupDisabled = finalized.count { it.cleanupStatus == CleanupStatus.DISABLED },
        mostUsedLanguage = languages.firstOrNull()?.language,
        bestDay = dailyHistory.maxWithOrNull(
            compareBy<DailyInsight> { it.words }
                .thenBy { it.sessions }
                .thenBy { it.date },
        ),
        recentDays = recentDays,
        languages = languages,
        dayParts = dayParts,
    )
}

private data class InsightFacts(
    val dictation: Dictation,
    val date: LocalDate,
    val dayPart: DayPart,
    val words: Int,
    val durationMs: Long,
)

private val DictationStatus.isFinalized: Boolean
    get() = this != DictationStatus.RECORDING && this != DictationStatus.PROCESSING

private val DictationStatus.isFailure: Boolean
    get() = this == DictationStatus.TRANSCRIPTION_FAILED ||
        this == DictationStatus.REFINEMENT_FAILED ||
        this == DictationStatus.INSERTION_FAILED

private fun Dictation.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(createdAtEpochMs).atZone(zone).toLocalDate()

private fun Dictation.dayPart(zone: ZoneId): DayPart =
    when (Instant.ofEpochMilli(createdAtEpochMs).atZone(zone).hour) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        in 17..20 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }

private fun Dictation.insightWordCount(): Int {
    val text = refinedText.ifBlank { safeText }.ifBlank { originalText }
    if (text.isBlank()) return 0
    val iterator = BreakIterator.getWordInstance(Locale.ROOT)
    iterator.setText(text)
    var count = 0
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        if (text.substring(start, end).any(Char::isLetterOrDigit)) count += 1
        start = end
        end = iterator.next()
    }
    return count
}

private fun currentStreak(activeDates: Set<LocalDate>, today: LocalDate): Int {
    var cursor = when {
        today in activeDates -> today
        today.minusDays(1) in activeDates -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (cursor in activeDates) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}

private fun longestStreak(activeDates: Set<LocalDate>): Int {
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    activeDates.sorted().forEach { date ->
        current = if (previous?.plusDays(1) == date) current + 1 else 1
        longest = maxOf(longest, current)
        previous = date
    }
    return longest
}
