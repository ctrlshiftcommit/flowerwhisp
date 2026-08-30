package com.flowerwhisp.mobile.transcription

import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import java.io.File

/** A stable provider substitute used by the local mock journey. */
class MockTranscriptionEngine : TranscriptionEngine {
    override suspend fun transcribe(audio: File, language: LanguageMode): String = when (language) {
        LanguageMode.HINDI -> "नमस्ते, क्या आप शुक्रवार तक मीटिंग के नोट्स भेज सकते हैं? धन्यवाद।"
        LanguageMode.HINGLISH -> "Hi, kya aap Friday tak meeting ke notes bhej sakte ho? Thanks."
        else -> "Hey, can you send me the meeting notes by Friday? Thanks."
    }
}
