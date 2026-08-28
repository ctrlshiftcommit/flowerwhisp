package com.flowerwhisp.mobile.domain.model

data class Dictation(
    val id: Long = 0,
    val createdAtEpochMs: Long,
    val originalText: String,
    val refinedText: String,
    val durationMs: Long,
    val language: LanguageMode,
    val status: DictationStatus,
    val isFavorite: Boolean = false,
    val recoveryAudioPath: String? = null,
) {
    val wordCount: Int get() = refinedText.split(Regex("\\s+")).count(String::isNotBlank)
}

enum class DictationStatus { RECORDING, PROCESSING, COMPLETE, TRANSCRIPTION_FAILED, REFINEMENT_FAILED, INSERTION_FAILED, CANCELLED }

enum class LanguageMode(val displayName: String, val providerCode: String?) {
    AUTO("Auto detect", null),
    ENGLISH("English", "en"),
    HINDI("Hindi", "hi"),
    HINGLISH("Hinglish", null),
}

enum class WritingStyle(val displayName: String, val instruction: String) {
    NATURAL("Natural", "Keep the writer's natural voice and make only necessary corrections."),
    PROFESSIONAL("Professional", "Use clear professional phrasing without making the message stiff."),
    CASUAL("Casual", "Keep the message relaxed, direct, and conversational."),
    CONCISE("Concise", "Remove repetition and unnecessary words while preserving every important detail."),
    FORMAL("Formal", "Use formal grammar and respectful wording without adding claims."),
    ENTHUSIASTIC("Enthusiastic", "Add restrained positive energy without exaggeration or extra meaning."),
}
