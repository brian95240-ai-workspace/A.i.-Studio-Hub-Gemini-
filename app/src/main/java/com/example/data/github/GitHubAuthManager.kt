package com.example.data.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles GitHub authentication (Fine-Grained/Classic PAT verification and RFC 8628 OAuth
 * Device Authorization Flow) and repository discovery via the official GitHub REST API.
 */
class GitHubAuthManager(
    private val client: OkHttpClient = defaultHttpClient()
) {

    suspend fun verifyAccessToken(
        token: String,
        method: GitHubAuthMethod = GitHubAuthMethod.PERSONAL_ACCESS_TOKEN
    ): Result<GitHubUserProfile> = withContext(Dispatchers.IO) {
        try {
            val clean = token.trim()
            require(clean.isNotBlank()) { "Provide a valid GitHub access token." }

            val req = apiRequestBuilder("https://api.github.com/user", clean)
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "GitHub authentication failed (HTTP ${res.code}). Confirm repo/contents permissions."
                        )
                    )
                }
                val json = JSONObject(body)
                val login = json.optString("login")
                if (login.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Could not resolve GitHub username.")
                    )
                }
                Result.success(
                    GitHubUserProfile(
                        login = login,
                        displayName = json.optString("name").ifBlank { login },
                        profileUrl = json.optString("html_url").ifBlank { "https://github.com/$login" },
                        authMethod = method
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listUserRepositories(
        token: String,
        limit: Int = 25
    ): Result<List<GitHubRepoSummary>> = withContext(Dispatchers.IO) {
        try {
            val clean = token.trim()
            require(clean.isNotBlank()) { "Connect GitHub first to list repositories." }

            val url = "https://api.github.com/user/repos?sort=updated&per_page=${limit.coerceIn(1, 50)}&affiliation=owner,collaborator"
            val req = apiRequestBuilder(url, clean).get().build()
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed to fetch repositories (HTTP ${res.code}).")
                    )
                }
                val arr = JSONArray(raw)
                val repos = mutableListOf<GitHubRepoSummary>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ownerObj = obj.optJSONObject("owner")
                    repos.add(
                        GitHubRepoSummary(
                            fullName = obj.optString("full_name"),
                            owner = ownerObj?.optString("login").orEmpty(),
                            name = obj.optString("name"),
                            htmlUrl = obj.optString("html_url"),
                            defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" },
                            isPrivate = obj.optBoolean("private", true),
                            updatedAt = obj.optString("updated_at")
                        )
                    )
                }
                Result.success(repos)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Initiates RFC 8628 GitHub OAuth Device Authorization Flow (requires only a public GitHub
     * OAuth App Client ID — no client secret needed on mobile).
     */
    suspend fun startDeviceAuthFlow(
        clientId: String,
        scope: String = "repo"
    ): Result<GitHubDeviceCodeSession> = withContext(Dispatchers.IO) {
        try {
            val cleanClientId = clientId.trim()
            require(cleanClientId.isNotBlank()) {
                "Enter your GitHub OAuth App Client ID (with Device Flow enabled) to start browserless OAuth."
            }
            val form = FormBody.Builder()
                .add("client_id", cleanClientId)
                .add("scope", scope)
                .build()
            val req = Request.Builder()
                .url("https://github.com/login/device/code")
                .addHeader("Accept", "application/json")
                .post(form)
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("GitHub Device Flow failed (HTTP ${res.code}): ${body.take(140)}")
                    )
                }
                val json = JSONObject(body)
                val error = json.optString("error")
                if (error.isNotBlank()) {
                    val desc = json.optString("error_description", error)
                    return@withContext Result.failure(IllegalStateException(desc))
                }
                Result.success(
                    GitHubDeviceCodeSession(
                        deviceCode = json.getString("device_code"),
                        userCode = json.getString("user_code"),
                        verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
                        expiresInSeconds = json.optInt("expires_in", 900),
                        intervalSeconds = json.optInt("interval", 5).coerceAtLeast(5)
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollDeviceAuthUntilComplete(
        clientId: String,
        session: GitHubDeviceCodeSession
    ): Result<String> = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + (session.expiresInSeconds * 1000L)
        var intervalSec = session.intervalSeconds.coerceAtLeast(5)

        while (System.currentTimeMillis() < deadline) {
            delay(intervalSec * 1000L)
            when (val outcome = pollOnce(clientId.trim(), session.deviceCode, intervalSec)) {
                is GitHubDevicePollOutcome.Authorized -> return@withContext Result.success(outcome.accessToken)
                is GitHubDevicePollOutcome.Pending -> intervalSec = outcome.nextIntervalSeconds
                is GitHubDevicePollOutcome.Failed -> return@withContext Result.failure(IllegalStateException(outcome.message))
            }
        }
        Result.failure(IllegalStateException("GitHub device authorization code expired. Start again."))
    }

    private fun pollOnce(
        clientId: String,
        deviceCode: String,
        currentInterval: Int
    ): GitHubDevicePollOutcome {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val req = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .addHeader("Accept", "application/json")
            .post(form)
            .build()

        return try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return GitHubDevicePollOutcome.Failed("OAuth token poll failed (HTTP ${res.code}).")
                }
                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                if (accessToken.isNotBlank()) {
                    return GitHubDevicePollOutcome.Authorized(accessToken)
                }
                when (val err = json.optString("error")) {
                    "authorization_pending" -> GitHubDevicePollOutcome.Pending(currentInterval)
                    "slow_down" -> GitHubDevicePollOutcome.Pending(currentInterval + 5)
                    "expired_token" -> GitHubDevicePollOutcome.Failed("Device code expired before confirmation.")
                    "access_denied" -> GitHubDevicePollOutcome.Failed("GitHub authorization was cancelled.")
                    else -> GitHubDevicePollOutcome.Failed(
                        json.optString("error_description").ifBlank { "GitHub OAuth error: $err" }
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GitHubDevicePollOutcome.Failed(e.message ?: "Network error polling GitHub OAuth.")
        }
    }

    companion object {
        const val PREFILLED_CLASSIC_TOKEN_URL =
            "https://github.com/settings/tokens/new?scopes=repo&description=Vortex+Researcher+Mirror"
        const val FINE_GRAINED_TOKEN_URL =
            "https://github.com/settings/personal-access-tokens/new"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        fun apiRequestBuilder(url: String, token: String): Request.Builder =
            Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .apply {
                    if (token.isNotBlank()) addHeader("Authorization", "Bearer $token")
                }
    }
}
