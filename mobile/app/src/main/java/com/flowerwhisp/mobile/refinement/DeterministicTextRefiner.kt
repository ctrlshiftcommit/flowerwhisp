package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import java.util.Locale

internal object DeterministicTextRefiner {
    fun refine(
        source: String,
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String {
        if (source.isBlank()) return ""

        var result = source.trim().replace(WHITESPACE, " ")
        result = applySnippets(result, snippets)
        result = applyDictionary(result, dictionary)
        if (settings.removeFillers) result = removeFillers(result)
        if (settings.spokenCorrections) result = applySpokenCorrections(result)
        if (settings.autoPunctuation) result = applySpokenPunctuation(result)
        if (style == WritingStyle.CONCISE) result = removeImmediateRepetitions(result)
        result = tidySpacing(result)
        if (settings.autoPunctuation) result = addSentencePresentation(result)
        return result.trim()
    }

    private fun applySnippets(source: String, snippets: List<Snippet>): String =
        snippets
            .asSequence()
            .filter { it.enabled && it.trigger.isNotBlank() && it.expansion.isNotBlank() }
            .sortedByDescending { it.trigger.length }
            .fold(source) { text, snippet ->
                exactPhrase(snippet.trigger).replace(text) { snippet.expansion }
            }

    private fun applyDictionary(source: String, dictionary: List<DictionaryEntry>): String =
        dictionary
            .asSequence()
            .filter { it.enabled && it.spelling.isNotBlank() && it.replacement.isNotBlank() }
            .sortedByDescending { it.spelling.length }
            .fold(source) { text, entry ->
                exactPhrase(entry.spelling).replace(text) { entry.replacement }
            }

    private fun removeFillers(source: String): String = source
        .replace(FILLER_WORD, "")
        .replace(WHITESPACE, " ")

    private fun applySpokenCorrections(source: String): String {
        var result = EXPLICIT_CORRECTION.replace(source) { match -> match.groupValues[2] }
        result = COMMA_NO_CORRECTION.replace(result) { match -> match.groupValues[2] }
        return result
    }

    private fun applySpokenPunctuation(source: String): String =
        SPOKEN_PUNCTUATION.replace(source) { match ->
            when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "comma" -> ", "
                "period", "full stop" -> ". "
                "question mark" -> "? "
                "exclamation mark", "exclamation point" -> "! "
                "colon" -> ": "
                "semicolon" -> "; "
                "new line" -> "\n"
                "new paragraph" -> "\n\n"
                else -> match.value
            }
        }

    private fun removeImmediateRepetitions(source: String): String =
        REPEATED_WORD.replace(source) { match -> match.groupValues[1] }

    private fun tidySpacing(source: String): String = source
        .replace(SPACE_BEFORE_PUNCTUATION, "$1")
        .replace(HORIZONTAL_WHITESPACE, " ")
        .replace(SPACE_AROUND_NEWLINE, "\n")
        .replace(EXCESS_NEWLINES, "\n\n")
        .trim()

    private fun addSentencePresentation(source: String): String {
        if (source.isEmpty()) return source
        val capitalized = buildString(source.length) {
            var capitalizeNext = true
            source.forEach { character ->
                if (capitalizeNext && character.isLetter()) {
                    append(character.titlecaseChar())
                    capitalizeNext = false
                } else {
                    append(character)
                    if (!character.isWhitespace()) capitalizeNext = character in ".!?\n"
                }
                if (character == '\n') capitalizeNext = true
            }
        }
        if (capitalized.lastOrNull() in TERMINAL_PUNCTUATION) return capitalized
        val firstWord = capitalized.substringBefore(' ').lowercase(Locale.ROOT)
        val ending = if (firstWord in QUESTION_STARTERS) "?" else "."
        return capitalized + ending
    }

    private fun exactPhrase(value: String): Regex = Regex(
        pattern = "(?iu)(?<![\\p{L}\\p{N}_])${Regex.escape(value.trim())}(?![\\p{L}\\p{N}_])",
    )

    private val WHITESPACE = Regex("\\s+")
    private val HORIZONTAL_WHITESPACE = Regex("[ \\t]+")
    private val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.;:!?])")
    private val SPACE_AROUND_NEWLINE = Regex("[ \\t]*\\n[ \\t]*")
    private val EXCESS_NEWLINES = Regex("\\n{3,}")
    private val FILLER_WORD = Regex(
        "(?iu)(?<![\\p{L}\\p{N}])(?:um+|uh+|erm+|hmm+)(?![\\p{L}\\p{N}])[,]?\\s*",
    )
    private val EXPLICIT_CORRECTION = Regex(
        "(?iu)\\b([\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*)\\s+(?:i mean|actually)\\s+" +
            "([\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*)\\b",
    )
    private val COMMA_NO_CORRECTION = Regex(
        "(?iu)\\b([\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*)\\s*,\\s*no\\s*,?\\s*" +
            "([\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*)\\b",
    )
    private val SPOKEN_PUNCTUATION = Regex(
        "(?iu)\\s*\\b(new paragraph|new line|question mark|exclamation mark|" +
            "exclamation point|full stop|semicolon|comma|period|colon)\\b\\s*",
    )
    private val REPEATED_WORD = Regex(
        "(?iu)\\b([\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*)\\b(?:\\s+\\1\\b)+",
    )
    private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', '।', '！', '？')
    private val QUESTION_STARTERS = setOf(
        "am", "are", "can", "could", "did", "do", "does", "how", "is", "may", "should",
        "was", "were", "what", "when", "where", "which", "who", "why", "will", "would",
    )
}
