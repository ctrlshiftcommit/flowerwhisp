package com.flowerwhisp.mobile.transcription

import com.flowerwhisp.mobile.domain.model.LanguageMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockTranscriptionEngineTest {
    @Test
    fun resultIsStableForTheSameLanguage() = runBlocking {
        val engine = MockTranscriptionEngine()
        val audio = File("mock-recording.m4a")

        assertEquals(
            engine.transcribe(audio, LanguageMode.ENGLISH),
            engine.transcribe(audio, LanguageMode.ENGLISH),
        )
    }
}
