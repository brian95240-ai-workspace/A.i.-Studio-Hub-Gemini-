package com.example.data.ai

import com.example.data.infra.InfrastructureRegistryEngine
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

data class SelfHostedQueryResponse(
    val response: String,
    val lane: String,
    val model: String,
    val latencySeconds: Double? = null
)

data class SelfHostedHealthResult(
    val reachable: Boolean,
    val httpCode: Int? = null,
    val summary: String,
    val latencyMs: Long = 0L
)

class ExternalRouterApi(
    private val client: OkHttpClient = defaultHttpClient()
) {

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
            val (valid, validationMsg) = InfrastructureRegistryEngine.validateRemoteEndpointUrl(endpoint)
            if (normalizedBase.isBlank() || !valid) {
                return@withContext Result.failure(IllegalArgumentException(validationMsg))
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

    /**
     * Self-Hosted Text Router contract:
     * - POST {base}/query
     * - Authorization: Bearer <token>
     * - Content-Type: application/json
     * - Body: {"prompt": "..."} (NO system prompt; server provides its own)
     * - Enforces non-blank prompt <= 4,000 chars client-side before sending
     * - Handles HTTP 200 ({response, lane, model, latency_s}), 400 (empty or >4,000 chars),
     *   and 401 (bad or missing Bearer token) explicitly.
     */
    suspend fun selfHostedQuery(
        baseUrl: String,
        bearerToken: String,
        prompt: String
    ): Result<SelfHostedQueryResponse> = withContext(Dispatchers.IO) {
        try {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Self-hosted router base URL is empty. Example: https://llm.example.com")
                )
            }
            val endpoint = if (normalizedBase.endsWith("/query")) {
                normalizedBase
            } else {
                "$normalizedBase/query"
            }
            val (valid, validationMsg) = InfrastructureRegistryEngine.validateRemoteEndpointUrl(endpoint)
            if (!valid) {
                return@withContext Result.failure(IllegalArgumentException(validationMsg))
            }

            val trimmedPrompt = prompt.trim()
            if (trimmedPrompt.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Prompt cannot be empty for self-hosted /query.")
                )
            }
            if (trimmedPrompt.length > SELF_HOSTED_MAX_PROMPT_CHARS) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Prompt is ${trimmedPrompt.length} characters, exceeding the $SELF_HOSTED_MAX_PROMPT_CHARS-character limit for self-hosted /query. Trim before sending."
                    )
                )
            }
            val cleanToken = bearerToken.trim()
            if (cleanToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("HTTP 401: Missing Bearer token. Bind the token in the Encrypted .env Vault.")
                )
            }

            val payload = buildSelfHostedPayloadJson(trimmedPrompt)
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $cleanToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                parseSelfHostedHttpResponse(response.code, rawBody)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Manual, bounded unauthenticated GET {base}/health check for the Self-Hosted Text Router.
     */
    suspend fun selfHostedHealth(baseUrl: String): SelfHostedHealthResult = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            return@withContext SelfHostedHealthResult(
                reachable = false,
                summary = "Standby — enter a Self-Hosted Text Router HTTPS URL first (e.g. https://llm.example.com)."
            )
        }
        val endpoint = when {
            normalizedBase.endsWith("/health") -> normalizedBase
            normalizedBase.endsWith("/query") -> normalizedBase.removeSuffix("/query") + "/health"
            else -> "$normalizedBase/health"
        }
        val (valid, validationMsg) = InfrastructureRegistryEngine.validateRemoteEndpointUrl(endpoint)
        if (!valid) {
            return@withContext SelfHostedHealthResult(
                reachable = false,
                summary = "Blocked: $validationMsg"
            )
        }

        val healthClient = client.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        val startMs = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .addHeader("Accept", "application/json, text/plain")
                .build()
            healthClient.newCall(request).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val snippet = resp.body?.string().orEmpty().trim().take(120)
                if (resp.isSuccessful) {
                    SelfHostedHealthResult(
                        reachable = true,
                        httpCode = resp.code,
                        latencyMs = elapsed,
                        summary = "Healthy · HTTP ${resp.code} in ${elapsed}ms" +
                            if (snippet.isNotBlank()) " · $snippet" else ""
                    )
                } else {
                    SelfHostedHealthResult(
                        reachable = false,
                        httpCode = resp.code,
                        latencyMs = elapsed,
                        summary = "Unhealthy / HTTP ${resp.code} in ${elapsed}ms" +
                            if (snippet.isNotBlank()) " · $snippet" else ""
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            SelfHostedHealthResult(
                reachable = false,
                latencyMs = elapsed,
                summary = "Standby / Offline (${e.javaClass.simpleName}: ${e.message?.take(80) ?: "unreachable"})"
            )
        }
    }

    companion object {
        const val SELF_HOSTED_MAX_PROMPT_CHARS = 4_000

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        /**
         * Builds the exact JSON payload for POST /query: strictly `{"prompt": "..."}` with no system prompt.
         */
        fun buildSelfHostedPayloadJson(prompt: String): String =
            JSONObject().apply {
                put("prompt", prompt)
            }.toString()

        /**
         * Composes the self-hosted prompt string from the user's prompt plus optional user-approved
         * short summary and optional opt-in grounding evidence (grounding is OFF by default).
         */
        fun composeSelfHostedPrompt(
            userPrompt: String,
            approvedSummary: String = "",
            groundingEvidence: String = ""
        ): String = buildString {
            val cleanSummary = approvedSummary.trim()
            if (cleanSummary.isNotBlank()) {
                appendLine("Approved Context:")
                appendLine(cleanSummary)
                appendLine()
            }
            append(userPrompt.trim())
            val cleanGrounding = groundingEvidence.trim()
            if (cleanGrounding.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine("Grounding Evidence:")
                append(cleanGrounding)
            }
        }.trim()

        /**
         * Pure HTTP response parser for POST /query (200 / 400 / 401 / other).
         */
        fun parseSelfHostedHttpResponse(httpCode: Int, rawBody: String): Result<SelfHostedQueryResponse> {
            return when (httpCode) {
                200 -> runCatching {
                    val json = JSONObject(rawBody)
                    val text = json.optString("response").trim()
                    if (text.isBlank()) {
                        throw IllegalStateException("Self-hosted /query returned an empty 'response' field.")
                    }
                    val lane = json.optString("lane").trim().ifBlank { "default" }
                    val model = json.optString("model").trim().ifBlank { "self-hosted" }
                    val latency = if (json.has("latency_s") && !json.isNull("latency_s")) {
                        json.optDouble("latency_s").takeIf { !it.isNaN() }
                    } else {
                        null
                    }
                    SelfHostedQueryResponse(
                        response = text,
                        lane = lane,
                        model = model,
                        latencySeconds = latency
                    )
                }
                400 -> Result.failure(
                    IllegalArgumentException(
                        "HTTP 400 Bad Request: Prompt was empty or exceeded $SELF_HOSTED_MAX_PROMPT_CHARS characters. ${rawBody.take(160)}"
                    )
                )
                401 -> Result.failure(
                    IllegalStateException(
                        "HTTP 401 Unauthorized: Bearer token was missing or rejected by the self-hosted router."
                    )
                )
                in listOf(301, 302, 303, 307, 308) -> Result.failure(
                    IllegalStateException(
                        "HTTP $httpCode Auth Redirect: Redirects are not followed. Verify access login or endpoint URL."
                    )
                )
                else -> Result.failure(
                    IllegalStateException(
                        "Self-hosted /query HTTP $httpCode: ${rawBody.take(200)}"
                    )
                )
            }
        }
    }
}
