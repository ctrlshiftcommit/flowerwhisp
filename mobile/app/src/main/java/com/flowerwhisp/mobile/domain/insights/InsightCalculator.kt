package com.flowerwhisp.mobile.domain.insights

import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.LanguageMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyInsight(
    val date: LocalDate,
    val sessions: Int,
    val words: Int,
)

data class InsightSnapshot(
    val totalSessions: Int,
    val totalWords: Int,
    val speakingTimeMs: Long,
    val averageWordsPerSession: Int,
    val mostUsedLanguage: LanguageMode?,
    val recentDays: List<DailyInsight>,
) {
    val hasData: Boolean get() = totalSessions > 0
}

/**
 * Insights intentionally describe persisted, completed work only. Nothing in
 * this calculator invents a time-saved estimate or treats a failed record as
 * a successful outcome.
 */
fun calculateInsights(
    history: List<Dictation>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    dayCount: Int = 7,
): InsightSnapshot {
    val completed = history.filter { it.status == DictationStatus.COMPLETE }
    val totalWords = completed.sumOf { it.insightWordCount() }
    val speakingTimeMs = completed.sumOf { it.durationMs.coerceAtLeast(0L) }
    val language = completed
        .groupingBy { it.language }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<LanguageMode, Int>> { it.value }.thenBy { it.key.ordinal })
        ?.key
    val firstDay = today.minusDays((dayCount.coerceAtLeast(1) - 1).toLong())
    val byDate = completed.groupBy { item ->
        Instant.ofEpochMilli(item.createdAtEpochMs).atZone(zone).toLocalDate()
    }
    val recentDays = (0 until dayCount.coerceAtLeast(1)).map { offset ->
        val date = firstDay.plusDays(offset.toLong())
        val entries = byDate[date].orEmpty()
        DailyInsight(date, entries.size, entries.sumOf { it.insightWordCount() })
    }
    return InsightSnapshot(
        totalSessions = completed.size,
        totalWords = totalWords,
        speakingTimeMs = speakingTimeMs,
        averageWordsPerSession = if (completed.isEmpty()) 0 else totalWords / completed.size,
        mostUsedLanguage = language,
        recentDays = recentDays,
    )
}

private fun Dictation.insightWordCount(): Int =
    (refinedText.ifBlank { originalText })
        .trim()
        .split(Regex("\\s+"))
        .count(String::isNotBlank)
