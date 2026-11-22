package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ExternalRouterApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            val endpoint = if (normalizedBase.endsWith("/chat/completions")) {
                normalizedBase
            } else {
                "$normalizedBase/chat/completions"
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            val payload = JSONObject().apply {
                put("model", modelId)
                put("messages", messages)
                put("temperature", 0.35)
            }.toString()

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://vortex-researcher.app")
                .addHeader("X-Title", "Vortex Researcher")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("External API HTTP ${response.code}: ${rawBody.take(240)}")
                    )
                }
                val json = JSONObject(rawBody)
                val choices = json.optJSONArray("choices")
                val text = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (text.isBlank()) {
                    Result.failure(IllegalStateException("External model returned empty completion."))
                } else {
                    Result.success(text.trim())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
