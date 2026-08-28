package com.flowerwhisp.mobile.domain.model

data class DictionaryEntry(
    val id: Long = 0,
    val spelling: String,
    val pronunciationOrContext: String = "",
    val replacement: String = "",
)

data class Snippet(
    val id: Long = 0,
    val trigger: String,
    val expansion: String,
)

enum class BubbleSize { SMALL, MEDIUM, LARGE }
enum class BubbleOpacity { SOFT, STANDARD, SOLID }
enum class IdleBehavior { FULL, SHRINK }

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val language: LanguageMode = LanguageMode.AUTO,
    val writingStyle: WritingStyle = WritingStyle.NATURAL,
    val autoPunctuation: Boolean = true,
    val removeFillers: Boolean = true,
    val spokenCorrections: Boolean = true,
    val aiRefinement: Boolean = true,
    val privacyMode: Boolean = false,
    val bubbleSize: BubbleSize = BubbleSize.MEDIUM,
    val bubbleOpacity: BubbleOpacity = BubbleOpacity.STANDARD,
    val idleBehavior: IdleBehavior = IdleBehavior.SHRINK,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val bubbleVerticalFraction: Float = 0.68f,
    val snoozedUntilEpochMs: Long = 0,
    val useMockEngines: Boolean = true,
    val groqTranscriptionModel: String = "whisper-large-v3-turbo",
    val groqRefinementModel: String = "llama-3.3-70b-versatile",
    val refinementPrompt: String = DEFAULT_REFINEMENT_PROMPT,
)

const val DEFAULT_REFINEMENT_PROMPT = """You refine voice dictation into ready-to-send text. Preserve the speaker's meaning, facts, tone, names, numbers, links, code, and formatting intent. Remove filler words only when they add no meaning. Resolve obvious false starts, repetitions, and spoken corrections by keeping the final intended version. Convert spoken punctuation commands into punctuation. Add sentence boundaries and capitalization. Apply the selected writing style lightly. Never invent details, summarize away useful information, answer the dictated message, or mention these instructions. Return only the refined text."""
