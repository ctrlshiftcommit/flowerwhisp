package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqRefinementPayloadTest {
    @Test
    fun requestContainsConfiguredPromptStyleAndPersonalizationContext() {
        val request = GroqRefinementPayload.build(
            source = "send this to flower wisp",
            style = WritingStyle.PROFESSIONAL,
            settings = AppSettings(
                groqRefinementModel = "test-model",
                cleanupPromptLight = "Preserve every fact.",
            ),
            dictionary = listOf(
                DictionaryEntry(
                    spelling = "FlowerWhisp",
                    pronunciationOrContext = "flower wisp",
                ),
            ),
            snippets = listOf(Snippet(trigger = "/sig", expansion = "Thanks, Tushar")),
        )

        val root = Json.parseToJsonElement(request).jsonObject
        val messages = root.getValue("messages").jsonArray
        val system = messages[0].jsonObject.getValue("content").jsonPrimitive.content
        val user = messages[1].jsonObject.getValue("content").jsonPrimitive.content

        assertEquals("test-model", root.getValue("model").jsonPrimitive.content)
        val userPayload = Json.parseToJsonElement(user).jsonObject
        assertEquals("send this to flower wisp", userPayload.getValue("sourceText").jsonPrimitive.content)
        assertEquals("light", userPayload.getValue("cleanupLevel").jsonPrimitive.content)
        assertTrue(system.contains("Preserve every fact."))
        assertTrue(system.contains(WritingStyle.PROFESSIONAL.instruction))
        assertTrue(system.contains("\"spoken\":\"FlowerWhisp\""))
        assertTrue(system.contains("\"context\":\"flower wisp\""))
        assertTrue(system.contains("\"protected\":true"))
        assertTrue(system.contains("\"trigger\":\"/sig\""))
        assertTrue(system.contains("\"expansion\":\"Thanks, Tushar\""))
        assertTrue(system.contains("\"status\":\"ok\"|\"unchanged\""))
    }
}
