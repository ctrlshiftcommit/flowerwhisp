package com.flowerwhisp.mobile.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetRulesTest {
    @Test
    fun normalizesSpokenPhraseWithoutChangingSavedExpansion() {
        val source = Snippet(trigger = "  my   signature  ", expansion = "\nBest,\nTushar\n")

        assertEquals(
            Snippet(trigger = "my signature", expansion = "\nBest,\nTushar\n"),
            SnippetRules.normalize(source),
        )
    }

    @Test
    fun rejectsCaseInsensitiveDuplicatePhraseAndDuplicateText() {
        val existing = listOf(Snippet(id = 7, trigger = "My Signature", expansion = "Best, Tushar"))

        assertEquals(
            "That spoken phrase already exists",
            SnippetRules.validationError(
                Snippet(trigger = "my  signature", expansion = "Different"),
                existing,
            ),
        )
        assertEquals(
            "That snippet text already exists",
            SnippetRules.validationError(
                Snippet(trigger = "email sign-off", expansion = " Best, Tushar "),
                existing,
            ),
        )
        assertNull(
            SnippetRules.validationError(
                Snippet(id = 7, trigger = "My Signature", expansion = "Best, Tushar"),
                existing,
            ),
        )
    }

    @Test
    fun enforcesReferenceLengthLimits() {
        assertEquals(
            "Spoken phrases can be up to 60 characters",
            SnippetRules.validationError(
                Snippet(trigger = "x".repeat(61), expansion = "Text"),
                emptyList(),
            ),
        )
        assertEquals(
            "Snippet text can be up to 4000 characters",
            SnippetRules.validationError(
                Snippet(trigger = "phrase", expansion = "x".repeat(4_001)),
                emptyList(),
            ),
        )
    }
}
