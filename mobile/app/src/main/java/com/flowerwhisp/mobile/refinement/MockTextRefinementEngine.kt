package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.domain.ports.TextTransformEngine

class MockTextRefinementEngine : TextRefinementEngine, TextTransformEngine {
    override suspend fun refine(
        source: String,
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String = DeterministicTextRefiner.refine(source, style, settings, dictionary, snippets)

    override suspend fun transform(source: String, instructions: String, settings: AppSettings): String =
        source.trim()
}
