package com.flowerwhisp.mobile.refinement

import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.ports.SettingsRepository
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.transcription.GroqApiSupport
import com.flowerwhisp.mobile.transcription.GroqEngineException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GroqTextRefinementEngine(
    private val settingsRepository: SettingsRepository,
    client: OkHttpClient,
) : TextRefinementEngine {
    private val httpClient = GroqApiSupport.boundedClient(client)

    override suspend fun refine(
        source: String,
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String {
        if (source.isBlank()) return ""
        if (settings.groqRefinementModel.isBlank()) {
            throw GroqEngineException("No Groq refinement model is configured")
        }
        val apiKey = settingsRepository.groqApiKey()?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GroqEngineException("Add a Groq API key before using cloud refinement")
        val body = GroqRefinementPayload.build(source, style, settings, dictionary, snippets)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(GroqApiSupport.CHAT_COMPLETIONS_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return GroqApiSupport.execute(httpClient, request).use { response ->
            if (!response.isSuccessful) {
                throw GroqEngineException(
                    "Groq rejected the refinement request (HTTP ${response.code})",
                )
            }
            val responseBody = response.body?.string()
                ?: throw GroqEngineException("Groq returned an empty refinement response")
            GroqApiSupport.parseRefinementResponse(responseBody)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
