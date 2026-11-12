package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetExpansionTest {
    private val literalSettings = AppSettings(
        autoPunctuation = false,
        removeFillers = false,
        spokenCorrections = false,
    )

    @Test
    fun naturalTriggerMatchesCaseInsensitivelyAsAWholePhrase() {
        val result = DeterministicTextRefiner.refine(
            source = "Send this to MY ADDRESS, not my addressable contact",
            style = WritingStyle.NATURAL,
            settings = literalSettings,
            dictionary = emptyList(),
            snippets = listOf(Snippet(trigger = "my address", expansion = "123 Main Street")),
        )

        assertEquals("Send this to 123 Main Street, not my addressable contact", result)
    }

    @Test
    fun symbolicLegacyTriggerCanBeSpokenNaturally() {
        val result = DeterministicTextRefiner.refine(
            source = "slash sig",
            style = WritingStyle.NATURAL,
            settings = literalSettings,
            dictionary = emptyList(),
            snippets = listOf(Snippet(trigger = "/sig", expansion = "Thanks, Tushar")),
        )

        assertEquals("Thanks, Tushar", result)
    }

    @Test
    fun savedExpansionBypassesDictionaryAndCleanupMutations() {
        val expansion = "um, period, OpenAI\nBest,\nTushar"
        val result = DeterministicTextRefiner.refine(
            source = "my signature.",
            style = WritingStyle.CONCISE,
            settings = AppSettings(
                autoPunctuation = true,
                removeFillers = true,
                spokenCorrections = true,
            ),
            dictionary = listOf(DictionaryEntry(spelling = "OpenAI", replacement = "Changed")),
            snippets = listOf(Snippet(trigger = "my signature", expansion = expansion)),
        )

        assertEquals(expansion, result)
    }

    @Test
    fun expansionDoesNotRecursivelyTriggerAnotherSnippet() {
        val result = DeterministicTextRefiner.refine(
            source = "first and second",
            style = WritingStyle.NATURAL,
            settings = literalSettings,
            dictionary = emptyList(),
            snippets = listOf(
                Snippet(trigger = "first", expansion = "second"),
                Snippet(trigger = "second", expansion = "expanded again"),
            ),
        )

        assertEquals("second and expanded again", result)
    }

    @Test
    fun cleanupMustPreserveEveryProtectedMarkerExactlyOnceAndInOrder() {
        val prepared = DeterministicTextRefiner.prepare(
            source = "first then second",
            style = WritingStyle.NATURAL,
            settings = literalSettings,
            dictionary = emptyList(),
            snippets = listOf(
                Snippet(trigger = "first", expansion = "ONE"),
                Snippet(trigger = "second", expansion = "TWO"),
            ),
        )

        assertEquals(2, prepared.expandedSnippetCount)
        assertEquals("ONE then TWO", prepared.safeText)
        assertEquals("ONE then TWO", prepared.restoreCleanup(prepared.cleanupInput))

        val tokens = TOKEN.findAll(prepared.cleanupInput).map { it.value }.toList()
        assertEquals(2, tokens.size)
        assertTrue(prepared.cleanupInput.indexOf(tokens[0]) < prepared.cleanupInput.indexOf(tokens[1]))
        assertNull(prepared.restoreCleanup(prepared.cleanupInput.replace(tokens[0], "")))
        assertNull(prepared.restoreCleanup("${prepared.cleanupInput} ${tokens[0]}"))
        assertNull(prepared.restoreCleanup("${tokens[1]} then ${tokens[0]}"))
        assertFalse(prepared.cleanupInput.contains("ONE"))
        assertFalse(prepared.cleanupInput.contains("TWO"))
    }

    private companion object {
        val TOKEN = Regex("\\[\\[FLOWERWHISP_SNIPPET[^]]+]]")
    }
}
