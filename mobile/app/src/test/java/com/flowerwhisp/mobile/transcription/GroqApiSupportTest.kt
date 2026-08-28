package com.flowerwhisp.mobile.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GroqApiSupportTest {
    @Test
    fun parsesTextFromTranscriptionResponse() {
        val result = GroqApiSupport.parseTranscriptionResponse(
            """{"text":"  Keep this exact transcript.  ","language":"en"}""",
        )

        assertEquals("Keep this exact transcript.", result)
    }

    @Test
    fun rejectsEmptyTranscriptionResponse() {
        assertThrows(GroqEngineException::class.java) {
            GroqApiSupport.parseTranscriptionResponse("""{"text":"  "}""")
        }
    }

    @Test
    fun parsesValidatedTextFromChatCompletion() {
        val result = GroqApiSupport.parseRefinementResponse(
            """{"choices":[{"message":{"content":"{\"status\":\"ok\",\"text\":\"Ready to send.\"}"}}]}""",
        )

        assertEquals("Ready to send.", result)
    }

    @Test
    fun rejectsUnvalidatedChatContent() {
        assertThrows(GroqEngineException::class.java) {
            GroqApiSupport.parseRefinementResponse(
                """{"choices":[{"message":{"content":"Here is the refined text"}}]}""",
            )
        }
    }
}
