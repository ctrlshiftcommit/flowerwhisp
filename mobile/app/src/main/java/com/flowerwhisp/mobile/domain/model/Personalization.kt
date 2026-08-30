package com.flowerwhisp.mobile.domain.model

data class DictionaryEntry(
    val id: Long = 0,
    val spelling: String,
    val pronunciationOrContext: String = "",
    val replacement: String = "",
    val scope: DictionaryScope = DictionaryScope.ALL,
    val isProtected: Boolean = true,
    val enabled: Boolean = true,
)

data class Snippet(
    val id: Long = 0,
    val trigger: String,
    val expansion: String,
    val enabled: Boolean = true,
)

data class TransformProfile(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val instructions: String,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
)

enum class DictionaryScope { ALL, PERSONAL, WORK }
enum class CleanupLevel(val displayName: String) { NONE("Off"), LIGHT("Light"), MEDIUM("Medium") }
enum class RetentionMode(val displayName: String) { FOREVER("Forever"), HOURS_24("24 hours"), NEVER("Never") }
enum class AppearanceMode(val displayName: String) { DARK("Black"), LIGHT("White"), SYSTEM("System") }

enum class BubbleSize { SMALL, MEDIUM, LARGE }
enum class BubbleOpacity { SOFT, STANDARD, SOLID }
enum class IdleBehavior { FULL, SHRINK }

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val onboardingStep: Int = 0,
    val language: LanguageMode = LanguageMode.AUTO,
    val writingStyle: WritingStyle = WritingStyle.NATURAL,
    val personalWritingStyle: WritingStyle = WritingStyle.CASUAL,
    val workWritingStyle: WritingStyle = WritingStyle.PROFESSIONAL,
    val emailWritingStyle: WritingStyle = WritingStyle.PROFESSIONAL,
    val otherWritingStyle: WritingStyle = WritingStyle.NATURAL,
    val personalStyleInstructions: String = "",
    val workStyleInstructions: String = "",
    val emailStyleInstructions: String = "",
    val otherStyleInstructions: String = "",
    /** Runtime-only instructions selected from the focused app's context. */
    val activeStyleInstructions: String = "",
    val autoPunctuation: Boolean = true,
    val removeFillers: Boolean = true,
    val spokenCorrections: Boolean = true,
    val aiRefinement: Boolean = true,
    val privacyMode: Boolean = false,
    val cleanupLevel: CleanupLevel = CleanupLevel.LIGHT,
    val cleanupPromptNone: String = DEFAULT_CLEANUP_PROMPT_NONE,
    val cleanupPromptLight: String = DEFAULT_CLEANUP_PROMPT_LIGHT,
    val cleanupPromptMedium: String = DEFAULT_CLEANUP_PROMPT_MEDIUM,
    val retentionMode: RetentionMode = RetentionMode.FOREVER,
    val appearanceMode: AppearanceMode = AppearanceMode.DARK,
    val scratchpad: String = "",
    val bubbleSize: BubbleSize = BubbleSize.MEDIUM,
    val bubbleOpacity: BubbleOpacity = BubbleOpacity.STANDARD,
    val idleBehavior: IdleBehavior = IdleBehavior.SHRINK,
    val haptics: Boolean = true,
    val playSounds: Boolean = false,
    val muteMusicWhileDictating: Boolean = false,
    val reduceMotion: Boolean = false,
    val bubbleVerticalFraction: Float = 0.68f,
    val snoozedUntilEpochMs: Long = 0,
    val useMockEngines: Boolean = false,
    val groqTranscriptionModel: String = "whisper-large-v3",
    val groqRefinementModel: String = "openai/gpt-oss-20b",
    val refinementPrompt: String = DEFAULT_CLEANUP_PROMPT_LIGHT,
)

enum class StyleContext(val displayName: String) {
    PERSONAL("Personal"),
    WORK("Work"),
    EMAIL("Email"),
    OTHER("Other"),
}

fun AppSettings.styleFor(context: StyleContext): WritingStyle = when (context) {
    StyleContext.PERSONAL -> personalWritingStyle
    StyleContext.WORK -> workWritingStyle
    StyleContext.EMAIL -> emailWritingStyle
    StyleContext.OTHER -> otherWritingStyle
}

fun AppSettings.styleInstructionsFor(context: StyleContext): String = when (context) {
    StyleContext.PERSONAL -> personalStyleInstructions
    StyleContext.WORK -> workStyleInstructions
    StyleContext.EMAIL -> emailStyleInstructions
    StyleContext.OTHER -> otherStyleInstructions
}

fun styleContextForPackage(packageName: String?): StyleContext =
    when (packageName.orEmpty().lowercase()) {
        "com.whatsapp",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging" -> StyleContext.PERSONAL

        "com.slack",
        "com.microsoft.teams",
        "com.linkedin.android" -> StyleContext.WORK

        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider" -> StyleContext.EMAIL

        else -> StyleContext.OTHER
    }

fun AppSettings.styleForPackage(packageName: String?): WritingStyle =
    styleFor(styleContextForPackage(packageName))

fun AppSettings.forPackageContext(packageName: String?): AppSettings {
    val context = styleContextForPackage(packageName)
    return copy(
        writingStyle = styleFor(context),
        activeStyleInstructions = styleInstructionsFor(context),
    )
}

fun DictionaryEntry.appliesTo(context: StyleContext): Boolean = when (scope) {
    DictionaryScope.ALL -> true
    DictionaryScope.PERSONAL -> context == StyleContext.PERSONAL
    DictionaryScope.WORK -> context == StyleContext.WORK || context == StyleContext.EMAIL
}

fun AppSettings.cleanupPrompt(level: CleanupLevel = cleanupLevel): String = when (level) {
    CleanupLevel.NONE -> cleanupPromptNone
    CleanupLevel.LIGHT -> cleanupPromptLight
    CleanupLevel.MEDIUM -> cleanupPromptMedium
}

const val DEFAULT_CLEANUP_PROMPT_NONE = """Do not edit the source text. Preserve every word, repetition, filler, punctuation mark, paragraph break, and ordering exactly as supplied."""

const val DEFAULT_CLEANUP_PROMPT_LIGHT = """Turn the raw speech transcript into clean written text with minimal intervention. Add confident sentence boundaries, punctuation, capitalization, spacing, and paragraph breaks. Fix only unmistakable transcription artifacts or immediate accidental word repetitions when the intended wording is clear from nearby context. Keep the speaker's vocabulary, contractions, tone, emphasis, ordering, and level of formality. Keep meaningful fillers and self-corrections. Do not paraphrase, shorten, expand, summarize, reorganize, add headings, or convert prose into a list."""

const val DEFAULT_CLEANUP_PROMPT_MEDIUM = """Edit the raw speech transcript into fluent, natural written language while preserving the complete meaning and voice. Apply light cleanup. Remove empty speech fillers when they carry no meaning; collapse obvious stutters and abandoned false starts; repair locally clear grammar and sentence flow. Preserve every fact, name, number, date, qualifier, uncertainty, negation, request, commitment, example, and useful detail. Do not summarize, add context, create a greeting or sign-off, or introduce claims that were not spoken."""

@Deprecated("Use DEFAULT_CLEANUP_PROMPT_LIGHT")
const val DEFAULT_REFINEMENT_PROMPT = DEFAULT_CLEANUP_PROMPT_LIGHT

val DEFAULT_TRANSFORMS = listOf(
    TransformProfile(
        name = "Polish",
        description = "Clarity without changing meaning",
        instructions = "Correct grammar, punctuation, awkward phrasing, and unnecessary repetition. Improve clarity and concision without changing tone, intent, certainty, formatting, or formality. Preserve every fact, name, number, qualifier, request, commitment, example, and useful detail. Return only the transformed text.",
        enabled = true,
        builtIn = true,
    ),
    TransformProfile(
        name = "Prompt engineer",
        description = "Structure text as a precise prompt",
        instructions = "Rewrite the text as a clear executable prompt. Preserve its objective, context, requirements, constraints, examples, tools, audience, tone, and requested output. Do not invent missing requirements. Return only the transformed text.",
        enabled = false,
        builtIn = true,
    ),
)
