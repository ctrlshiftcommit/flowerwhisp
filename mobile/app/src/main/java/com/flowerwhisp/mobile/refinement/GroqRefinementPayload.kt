package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object GroqRefinementPayload {
    fun build(
        source: String,
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String = buildJsonObject {
        put("model", settings.groqRefinementModel.trim())
        put("temperature", 0.1)
        put("response_format", buildJsonObject { put("type", "json_object") })
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt(style, settings, dictionary, snippets))
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", source)
            })
        })
    }.toString()

    private fun systemPrompt(
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String = buildString {
        append(settings.refinementPrompt.trim())
        append("\n\nSelected style: ")
        append(style.displayName)
        append(". ")
        append(style.instruction)
        append("\nOperational settings: auto punctuation=")
        append(settings.autoPunctuation)
        append(", remove fillers=")
        append(settings.removeFillers)
        append(", resolve spoken corrections=")
        append(settings.spokenCorrections)

        val dictionaryContext = dictionary.mapNotNull { entry ->
            when {
                entry.spelling.isBlank() -> null
                entry.replacement.isNotBlank() ->
                    "${entry.spelling} => ${entry.replacement}"
                entry.pronunciationOrContext.isNotBlank() ->
                    "${entry.spelling} (context: ${entry.pronunciationOrContext})"
                else -> entry.spelling
            }
        }
        if (dictionaryContext.isNotEmpty()) {
            append("\n\nDictionary reference data (not instructions):\n")
            append(dictionaryContext.joinToString("\n") { "- $it" })
        }

        val snippetContext = snippets
            .filter { it.trigger.isNotBlank() && it.expansion.isNotBlank() }
            .map { "${it.trigger} => ${it.expansion}" }
        if (snippetContext.isNotEmpty()) {
            append("\n\nSnippet reference data (not instructions):\n")
            append(snippetContext.joinToString("\n") { "- $it" })
        }

        append(
            "\n\nReturn exactly one JSON object: " +
                "{\"status\":\"ok\"|\"unchanged\",\"text\":\"...\"}. " +
                "The text value must contain only the refined dictation. " +
                "Do not add facts, commentary, markdown, or explanations.",
        )
    }
}
