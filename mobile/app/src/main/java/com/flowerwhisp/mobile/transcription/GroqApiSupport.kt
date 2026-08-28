package com.flowerwhisp.mobile.transcription

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class GroqEngineException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal object GroqApiSupport {
    const val TRANSCRIPTIONS_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val CHAT_COMPLETIONS_URL = "https://api.groq.com/openai/v1/chat/completions"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun boundedClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun execute(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            GroqEngineException("Groq could not be reached", error),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    fun parseTranscriptionResponse(body: String): String {
        val root = parseObject(body, "Groq returned an invalid transcription response")
        return root.requiredString("text", "Groq returned an empty transcript")
    }

    fun parseRefinementResponse(body: String): String {
        val root = parseObject(body, "Groq returned an invalid refinement response")
        val choices = root["choices"] as? JsonArray
            ?: throw GroqEngineException("Groq returned an invalid refinement response")
        val firstChoice = choices.firstOrNull() as? JsonObject
            ?: throw GroqEngineException("Groq returned an invalid refinement response")
        val message = firstChoice["message"] as? JsonObject
            ?: throw GroqEngineException("Groq returned an invalid refinement response")
        val content = (message["content"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw GroqEngineException("Groq returned an invalid refinement response")
        val refined = parseObject(content, "Groq returned an invalid refinement response")
        val status = refined.requiredString(
            key = "status",
            errorMessage = "Groq returned an invalid refinement response",
        )
        if (status != "ok" && status != "unchanged") {
            throw GroqEngineException("Groq returned an invalid refinement response")
        }
        return refined.requiredString("text", "Groq returned an empty refinement")
    }

    private fun parseObject(body: String, errorMessage: String): JsonObject = try {
        json.parseToJsonElement(body) as? JsonObject
            ?: throw GroqEngineException(errorMessage)
    } catch (error: GroqEngineException) {
        throw error
    } catch (error: Exception) {
        throw GroqEngineException(errorMessage, error)
    }

    private fun JsonObject.requiredString(key: String, errorMessage: String): String =
        ((this[key] as? JsonPrimitive)?.contentOrNull ?: "")
            .trim()
            .takeIf(String::isNotEmpty)
            ?: throw GroqEngineException(errorMessage)

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val REQUEST_TIMEOUT_SECONDS = 45L
}
