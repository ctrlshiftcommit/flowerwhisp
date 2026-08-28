package com.flowerwhisp.mobile.domain.insights

import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.LanguageMode
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 8, 28)

    @Test
    fun countsCompletedWordsDurationAndLanguage() {
        val history = listOf(
            dictation("one two three", 120_000, LanguageMode.ENGLISH, today),
            dictation("four five", 60_000, LanguageMode.ENGLISH, today.minusDays(1)),
            dictation("ignored", 99_000, LanguageMode.HINDI, today, DictationStatus.TRANSCRIPTION_FAILED),
        )

        val result = calculateInsights(history, today, zone)

        assertEquals(2, result.totalSessions)
        assertEquals(5, result.totalWords)
        assertEquals(180_000, result.speakingTimeMs)
        assertEquals(2, result.averageWordsPerSession)
        assertEquals(LanguageMode.ENGLISH, result.mostUsedLanguage)
        assertEquals(1, result.recentDays.last().sessions)
        assertEquals(3, result.recentDays.last().words)
        assertEquals(1, result.recentDays[result.recentDays.lastIndex - 1].sessions)
        assertEquals(2, result.recentDays[result.recentDays.lastIndex - 1].words)
        assertTrue(result.hasData)
    }

    @Test
    fun emptyHistoryProducesSevenEmptyDays() {
        val result = calculateInsights(emptyList(), today, zone)

        assertFalse(result.hasData)
        assertEquals(7, result.recentDays.size)
        assertTrue(result.recentDays.all { it.sessions == 0 && it.words == 0 })
    }

    private fun dictation(
        text: String,
        durationMs: Long,
        language: LanguageMode,
        date: LocalDate,
        status: DictationStatus = DictationStatus.COMPLETE,
    ) = Dictation(
        createdAtEpochMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        originalText = text,
        refinedText = text,
        durationMs = durationMs,
        language = language,
        status = status,
    )
}
