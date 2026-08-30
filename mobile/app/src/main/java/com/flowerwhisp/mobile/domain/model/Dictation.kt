package com.flowerwhisp.mobile.domain.model

data class Dictation(
    val id: Long = 0,
    val createdAtEpochMs: Long,
    val originalText: String,
    val safeText: String = originalText,
    val refinedText: String,
    val durationMs: Long,
    val language: LanguageMode,
    val status: DictationStatus,
    val isFavorite: Boolean = false,
    val recoveryAudioPath: String? = null,
    val cleanupStatus: CleanupStatus = CleanupStatus.DISABLED,
    val cleanupError: String? = null,
) {
    val wordCount: Int get() = refinedText.split(Regex("\\s+")).count(String::isNotBlank)
}

enum class DictationStatus { RECORDING, PROCESSING, COMPLETE, TRANSCRIPTION_FAILED, REFINEMENT_FAILED, INSERTION_FAILED, CANCELLED }
enum class CleanupStatus { DISABLED, APPLIED, UNCHANGED, FAILED }

enum class LanguageMode(val displayName: String, val providerCode: String?) {
    AUTO("Auto detect", null),
    ENGLISH("English", "en"),
    HINDI("Hindi", "hi"),
    HINGLISH("Hinglish", null),
    MARATHI("Marathi", "mr"),
    BENGALI("Bengali", "bn"),
    GUJARATI("Gujarati", "gu"),
    TAMIL("Tamil", "ta"),
    TELUGU("Telugu", "te"),
    KANNADA("Kannada", "kn"),
    MALAYALAM("Malayalam", "ml"),
    URDU("Urdu", "ur"),
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"),
    GERMAN("German", "de"),
    ITALIAN("Italian", "it"),
    PORTUGUESE("Portuguese", "pt"),
    ARABIC("Arabic", "ar"),
    RUSSIAN("Russian", "ru"),
    JAPANESE("Japanese", "ja"),
    KOREAN("Korean", "ko"),
    CHINESE("Chinese", "zh"),
}

enum class WritingStyle(val displayName: String, val instruction: String) {
    NATURAL("Natural", "Keep the writer's natural voice and make only necessary corrections."),
    PROFESSIONAL("Professional", "Use clear professional phrasing without making the message stiff."),
    CASUAL("Casual", "Keep the message relaxed, direct, and conversational."),
    VERY_CASUAL("Very casual", "Use relaxed lowercase phrasing and omit sentence-ending punctuation where natural. Keep names and deliberate capitalization intact, and do not add slang or change meaning."),
    CONCISE("Concise", "Remove repetition and unnecessary words while preserving every important detail."),
    FORMAL("Formal", "Use formal grammar and respectful wording without adding claims."),
    ENTHUSIASTIC("Enthusiastic", "Add restrained positive energy without exaggeration or extra meaning."),
}
