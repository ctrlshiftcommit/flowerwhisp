package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockTextRefinementEngineTest {
    private val engine = MockTextRefinementEngine()

    @Test
    fun removesFillersAndAppliesExplicitCorrectionAndSpokenPunctuation() = runBlocking {
        val result = engine.refine(
            source = "um send it Tuesday, no, Wednesday comma please question mark",
            style = WritingStyle.NATURAL,
            settings = AppSettings(),
            dictionary = emptyList(),
            snippets = emptyList(),
        )

        assertEquals("Send it Wednesday, please?", result)
    }

    @Test
    fun disabledControlsPreserveWordsAndPunctuation() = runBlocking {
        val result = engine.refine(
            source = "um keep this exact",
            style = WritingStyle.ENTHUSIASTIC,
            settings = AppSettings(
                removeFillers = false,
                spokenCorrections = false,
                autoPunctuation = false,
            ),
            dictionary = emptyList(),
            snippets = emptyList(),
        )

        assertEquals("um keep this exact", result)
    }

    @Test
    fun expandsOnlyUserProvidedSnippetContent() = runBlocking {
        val result = engine.refine(
            source = "/sig",
            style = WritingStyle.NATURAL,
            settings = AppSettings(autoPunctuation = false),
            dictionary = emptyList(),
            snippets = listOf(Snippet(trigger = "/sig", expansion = "Thanks, Tushar")),
        )

        assertEquals("Thanks, Tushar", result)
    }
}
