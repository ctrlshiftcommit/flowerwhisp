package com.flowerwhisp.mobile.transcription

import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.ports.SettingsRepository
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import java.io.File
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

class GroqTranscriptionEngine(
    private val settingsRepository: SettingsRepository,
    client: OkHttpClient,
) : TranscriptionEngine {
    private val httpClient = GroqApiSupport.boundedClient(client)

    override suspend fun transcribe(audio: File, language: LanguageMode): String {
        require(audio.isFile && audio.length() > 0L) { "Recording is missing or empty" }
        val settings = settingsRepository.settings.first()
        val model = settings.groqTranscriptionModel.trim()
            .takeIf(String::isNotEmpty)
            ?: throw GroqEngineException("No Groq transcription model is configured")
        val apiKey = settingsRepository.groqApiKey()?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GroqEngineException("Add a Groq API key before using cloud transcription")

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audio.name, audio.asRequestBody(audio.mediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .apply {
                language.providerCode?.let { addFormDataPart("language", it) }
            }
            .build()
        val request = Request.Builder()
            .url(GroqApiSupport.TRANSCRIPTIONS_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        return GroqApiSupport.execute(httpClient, request).use { response ->
            if (!response.isSuccessful) {
                throw GroqEngineException(
                    "Groq rejected the transcription request (HTTP ${response.code})",
                )
            }
            val body = response.body?.string()
                ?: throw GroqEngineException("Groq returned an empty transcription response")
            GroqApiSupport.parseTranscriptionResponse(body)
        }
    }

    private fun File.mediaType() = when (extension.lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "webm" -> "audio/webm"
        "ogg", "oga" -> "audio/ogg"
        else -> "application/octet-stream"
    }.toMediaType()
}
