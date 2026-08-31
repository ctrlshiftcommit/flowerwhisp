package com.flowerwhisp.mobile.domain.insights

import com.flowerwhisp.mobile.domain.model.CleanupStatus
import com.flowerwhisp.mobile.domain.model.Dictation
import com.flowerwhisp.mobile.domain.model.DictationStatus
import com.flowerwhisp.mobile.domain.model.LanguageMode
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 8, 28)

    @Test
    fun completedHistoryCalculatesActivityRhythmAndPatterns() {
        val history = listOf(
            dictation(
                text = "one two three",
                durationMs = 120_000,
                language = LanguageMode.ENGLISH,
                date = today,
                hour = 9,
                favorite = true,
                cleanupStatus = CleanupStatus.APPLIED,
            ),
            dictation(
                text = "four five",
                durationMs = 60_000,
                language = LanguageMode.ENGLISH,
                date = today.minusDays(1),
                hour = 18,
                cleanupStatus = CleanupStatus.UNCHANGED,
            ),
            dictation(
                text = "ignored",
                durationMs = 99_000,
                language = LanguageMode.HINDI,
                date = today,
                status = DictationStatus.TRANSCRIPTION_FAILED,
            ),
        )

        val result = calculateInsights(history, today, zone)

        assertEquals(2, result.totalSessions)
        assertEquals(3, result.attemptedSessions)
        assertEquals(1, result.failedSessions)
        assertEquals(5, result.totalWords)
        assertEquals(180_000, result.speakingTimeMs)
        assertEquals(2.5, result.averageWordsPerSession!!, 0.001)
        assertEquals(90_000L, result.averageSessionDurationMs)
        assertEquals(1.666, result.wordsPerMinute!!, 0.001)
        assertEquals(2.0 / 3.0, result.completionRate!!, 0.001)
        assertEquals(2, result.activeDays)
        assertEquals(2, result.currentStreakDays)
        assertEquals(2, result.longestStreakDays)
        assertEquals(120_000, result.longestSessionMs)
        assertEquals(1, result.favoriteSessions)
        assertEquals(LanguageMode.ENGLISH, result.mostUsedLanguage)
        assertEquals(today, result.bestDay?.date)
        assertEquals(1, result.dayParts.single { it.part == DayPart.MORNING }.sessions)
        assertEquals(1, result.dayParts.single { it.part == DayPart.EVENING }.sessions)
        assertEquals(1, result.cleanupApplied)
        assertEquals(1, result.cleanupUnchanged)
        assertEquals(1, result.cleanupDisabled)
        assertEquals(14, result.recentDays.size)
        assertEquals(3, result.recentDays.last().words)
        assertTrue(result.hasData)
        assertTrue(result.hasHistory)
    }

    @Test
    fun emptyHistoryKeepsACompleteZeroValueScaffold() {
        val result = calculateInsights(emptyList(), today, zone)

        assertFalse(result.hasData)
        assertFalse(result.hasHistory)
        assertEquals(0, result.totalSessions)
        assertEquals(0, result.totalWords)
        assertNull(result.averageWordsPerSession)
        assertNull(result.averageSessionDurationMs)
        assertNull(result.wordsPerMinute)
        assertNull(result.completionRate)
        assertNull(result.bestDay)
        assertEquals(14, result.recentDays.size)
        assertTrue(result.recentDays.all { it.sessions == 0 && it.words == 0 && it.speakingTimeMs == 0L })
        assertEquals(DayPart.entries.toList(), result.dayParts.map(DayPartInsight::part))
        assertTrue(result.dayParts.all { it.sessions == 0 && it.words == 0 })
        assertTrue(result.languages.isEmpty())
    }

    @Test
    fun reliabilityUsesOnlyFinalizedOutcomesAndPreservesRecoveryCounts() {
        val history = listOf(
            dictation("done", 2_000, status = DictationStatus.COMPLETE, cleanupStatus = CleanupStatus.APPLIED, recoveryPath = "done.m4a"),
            dictation("raw", 3_000, status = DictationStatus.TRANSCRIPTION_FAILED, recoveryPath = "failed.m4a"),
            dictation("raw", 3_000, status = DictationStatus.REFINEMENT_FAILED, cleanupStatus = CleanupStatus.FAILED),
            dictation("raw", 3_000, status = DictationStatus.INSERTION_FAILED, cleanupStatus = CleanupStatus.UNCHANGED),
            dictation("raw", 3_000, status = DictationStatus.CANCELLED),
            dictation("raw", 3_000, status = DictationStatus.RECORDING),
            dictation("raw", 3_000, status = DictationStatus.PROCESSING),
        )

        val result = calculateInsights(history, today, zone)

        assertEquals(5, result.attemptedSessions)
        assertEquals(3, result.failedSessions)
        assertEquals(1, result.cancelledSessions)
        assertEquals(0.25, result.completionRate!!, 0.001)
        assertEquals(1, result.insertionFallbacks)
        assertEquals(2, result.recoveryRecordings)
        assertEquals(1, result.cleanupApplied)
        assertEquals(1, result.cleanupUnchanged)
        assertEquals(1, result.cleanupFailed)
        assertEquals(2, result.cleanupDisabled)
    }

    @Test
    fun wordCountUsesRefinedThenSafeThenRawTextAndIgnoresPunctuation() {
        val history = listOf(
            dictation("unused", 1_000, refined = "", safe = "Hello, world! 42"),
            dictation("raw fallback", 1_000, refined = "", safe = ""),
            dictation("unused", 1_000, refined = "...", safe = "also unused"),
        )

        val result = calculateInsights(history, today, zone)

        assertEquals(5, result.totalWords)
    }

    @Test
    fun streakCanEndYesterdayAndLongestStreakUsesAllSavedDays() {
        val history = listOf(
            dictation("one", 1_000, date = today.minusDays(1)),
            dictation("two", 1_000, date = today.minusDays(2)),
            dictation("a b c", 1_000, date = today.minusDays(4)),
            dictation("one", 1_000, date = today.minusDays(5)),
            dictation("one", 1_000, date = today.minusDays(6)),
        )

        val result = calculateInsights(history, today, zone)

        assertEquals(2, result.currentStreakDays)
        assertEquals(3, result.longestStreakDays)
        assertEquals(today.minusDays(4), result.bestDay?.date)
    }

    @Test
    fun negativeDurationsAreClampedAndLanguageTiesAreStable() {
        val history = listOf(
            dictation("hello", -4_000, language = LanguageMode.HINDI),
            dictation("world", 60_000, language = LanguageMode.ENGLISH),
        )

        val result = calculateInsights(history, today, zone)

        assertEquals(60_000, result.speakingTimeMs)
        assertEquals(60_000, result.longestSessionMs)
        assertEquals(LanguageMode.ENGLISH, result.mostUsedLanguage)
        assertEquals(listOf(LanguageMode.ENGLISH, LanguageMode.HINDI), result.languages.map(LanguageInsight::language))
    }

    private fun dictation(
        text: String,
        durationMs: Long,
        language: LanguageMode = LanguageMode.ENGLISH,
        date: LocalDate = today,
        hour: Int = 12,
        status: DictationStatus = DictationStatus.COMPLETE,
        refined: String = text,
        safe: String = text,
        favorite: Boolean = false,
        recoveryPath: String? = null,
        cleanupStatus: CleanupStatus = CleanupStatus.DISABLED,
    ) = Dictation(
        createdAtEpochMs = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        originalText = text,
        safeText = safe,
        refinedText = refined,
        durationMs = durationMs,
        language = language,
        status = status,
        isFavorite = favorite,
        recoveryAudioPath = recoveryPath,
        cleanupStatus = cleanupStatus,
    )
}
