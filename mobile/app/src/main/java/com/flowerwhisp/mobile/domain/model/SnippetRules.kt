package com.flowerwhisp.mobile.domain.model

import java.util.Locale

object SnippetRules {
    const val MAX_TRIGGER_LENGTH = 60
    const val MAX_EXPANSION_LENGTH = 4_000

    fun normalize(snippet: Snippet): Snippet = snippet.copy(
        trigger = snippet.trigger.trim().replace(WHITESPACE, " "),
    )

    fun validationError(candidate: Snippet, existing: List<Snippet>): String? {
        if (candidate.trigger.isBlank()) return "Add a spoken phrase"
        if (candidate.trigger.length > MAX_TRIGGER_LENGTH) {
            return "Spoken phrases can be up to $MAX_TRIGGER_LENGTH characters"
        }
        if (candidate.expansion.isBlank()) return "Add the text to insert"
        if (candidate.expansion.length > MAX_EXPANSION_LENGTH) {
            return "Snippet text can be up to $MAX_EXPANSION_LENGTH characters"
        }

        val otherEntries = existing.filterNot { entry -> entry.id == candidate.id && candidate.id != 0L }
        if (otherEntries.any { entry -> canonicalTrigger(entry.trigger) == canonicalTrigger(candidate.trigger) }) {
            return "That spoken phrase already exists"
        }
        if (otherEntries.any { entry -> entry.expansion.trim() == candidate.expansion.trim() }) {
            return "That snippet text already exists"
        }
        return null
    }

    private fun canonicalTrigger(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

    private val WHITESPACE = Regex("\\s+")
}
