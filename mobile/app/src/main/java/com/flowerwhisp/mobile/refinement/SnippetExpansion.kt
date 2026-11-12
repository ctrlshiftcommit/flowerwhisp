package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.Snippet
import java.util.Locale

/**
 * Replaces spoken snippet triggers with opaque tokens while deterministic and cloud cleanup run.
 * The saved expansion is restored only after cleanup, so user-authored text cannot be rewritten,
 * interpreted as an instruction, or recursively expanded by another snippet.
 */
internal class ProtectedSnippetExpansion private constructor(
    val protectedText: String,
    replacements: List<Replacement>,
    private val markerNamespace: String,
) {
    private val orderedReplacements = replacements.sortedBy { replacement ->
        protectedText.indexOf(replacement.token)
    }

    val expansionCount: Int = orderedReplacements.size

    /**
     * Returns null when cleanup removed, duplicated, reordered, or invented one of our markers.
     * Callers must then discard the cleanup result and use their local safe-text fallback.
     */
    fun restore(candidate: String): String? {
        if (orderedReplacements.isEmpty()) return candidate

        var cursor = 0
        orderedReplacements.forEach { replacement ->
            val occurrence = candidate.indexOf(replacement.token, startIndex = cursor)
            if (occurrence < 0) return null
            if (candidate.indexOf(replacement.token, startIndex = occurrence + replacement.token.length) >= 0) {
                return null
            }
            cursor = occurrence + replacement.token.length
        }

        val withoutKnownMarkers = orderedReplacements.fold(candidate) { text, replacement ->
            text.replace(replacement.token, "")
        }
        if (withoutKnownMarkers.contains("[[$markerNamespace")) return null

        return orderedReplacements.fold(candidate) { text, replacement ->
            text.replace(replacement.token, replacement.expansion)
        }
    }

    private data class Replacement(
        val token: String,
        val expansion: String,
    )

    companion object {
        fun protect(source: String, snippets: List<Snippet>): ProtectedSnippetExpansion {
            val eligible = snippets
                .asSequence()
                .filter { snippet ->
                    snippet.enabled && snippet.trigger.isNotBlank() && snippet.expansion.isNotBlank()
                }
                .sortedByDescending { snippet -> normalizePhrase(snippet.trigger).length }
                .distinctBy { snippet -> normalizePhrase(snippet.trigger).lowercase(Locale.ROOT) }
                .toList()
            val markerNamespace = collisionFreeNamespace(source)
            if (source.isBlank() || eligible.isEmpty()) {
                return ProtectedSnippetExpansion(source, emptyList(), markerNamespace)
            }

            eligible.firstOrNull { snippet ->
                fullDictationPattern(triggerAliases(snippet.trigger)).matches(source)
            }?.let { snippet ->
                val token = token(markerNamespace, 0)
                return ProtectedSnippetExpansion(
                    protectedText = token,
                    replacements = listOf(Replacement(token, snippet.expansion)),
                    markerNamespace = markerNamespace,
                )
            }

            var nextToken = 0
            var protected = source
            val replacements = mutableListOf<Replacement>()
            eligible.forEach { snippet ->
                val pattern = phrasePattern(triggerAliases(snippet.trigger))
                protected = pattern.replace(protected) {
                    val token = token(markerNamespace, nextToken++)
                    replacements += Replacement(token, snippet.expansion)
                    token
                }
            }
            return ProtectedSnippetExpansion(protected, replacements, markerNamespace)
        }

        private fun phrasePattern(aliases: List<String>): Regex = Regex(
            pattern = "(?iu)(?<![\\p{L}\\p{N}_])(?:${aliasAlternatives(aliases)})(?![\\p{L}\\p{N}_])",
        )

        private fun fullDictationPattern(aliases: List<String>): Regex = Regex(
            pattern = "(?iu)^\\s*(?:${aliasAlternatives(aliases)})(?:[.!?。！？])?\\s*$",
        )

        private fun aliasAlternatives(aliases: List<String>): String = aliases
            .asSequence()
            .map(::normalizePhrase)
            .filter(String::isNotBlank)
            .distinctBy { alias -> alias.lowercase(Locale.ROOT) }
            .sortedByDescending(String::length)
            .joinToString("|") { alias ->
                alias.split(WHITESPACE).joinToString("\\s+") { part -> Regex.escape(part) }
            }

        /**
         * Older builds and tests encouraged symbolic triggers such as /sig and ;addr. Speech
         * engines normally return those as "slash sig" or "semicolon addr", so retain those
         * entries while treating ordinary natural-language triggers exactly as entered.
         */
        private fun triggerAliases(trigger: String): List<String> {
            val normalized = normalizePhrase(trigger)
            val spokenPrefix = SPOKEN_PREFIXES.firstOrNull { (symbol, _) ->
                normalized.startsWith(symbol)
            } ?: return listOf(normalized)
            val (symbol, spoken) = spokenPrefix
            val remainder = normalized.removePrefix(symbol).trimStart()
            if (remainder.isBlank()) return listOf(normalized)
            return listOf(
                normalized,
                "$symbol $remainder",
                "$spoken $remainder",
            )
        }

        private fun collisionFreeNamespace(source: String): String {
            var suffix = 0
            var namespace = BASE_NAMESPACE
            while (source.contains("[[$namespace")) {
                suffix += 1
                namespace = "${BASE_NAMESPACE}_SAFE_$suffix"
            }
            return namespace
        }

        private fun token(namespace: String, index: Int): String =
            "[[${namespace}_${index.toString().padStart(4, '0')}]]"

        private fun normalizePhrase(value: String): String = value.trim().replace(WHITESPACE, " ")

        private const val BASE_NAMESPACE = "FLOWERWHISP_SNIPPET"
        private val WHITESPACE = Regex("\\s+")
        private val SPOKEN_PREFIXES = listOf(
            "/" to "slash",
            ";" to "semicolon",
            ":" to "colon",
            "#" to "hash",
            "@" to "at",
            "&" to "ampersand",
        )
    }
}

internal fun String.endsWithProtectedSnippetToken(): Boolean =
    PROTECTED_SNIPPET_AT_END.containsMatchIn(this)

private val PROTECTED_SNIPPET_AT_END = Regex(
    "\\[\\[FLOWERWHISP_SNIPPET(?:_SAFE_\\d+)?_\\d{4}]]$",
)
