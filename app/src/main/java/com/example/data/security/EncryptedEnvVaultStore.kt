package com.example.data.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local-only Android Keystore (AES-256-GCM) encrypted `.env` vault.
 * - Secrets never leave the device (never synced to Firestore, GitHub, Drive, or ZIP exports).
 * - Secret values are never logged.
 * - Supports discrete Auto-Fill resolution and per-entry AI Model permission enforcement.
 */
class EncryptedEnvVaultStore(private val context: Context) {

    companion object {
        private const val TAG = "EncryptedEnvVaultStore"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "vortex_env_vault_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val VAULT_FILENAME = "vortex_env_vault.enc"
    }

    private val mutex = Mutex()
    private val vaultFile: File by lazy { File(context.filesDir, VAULT_FILENAME) }

    private val _entries = MutableStateFlow<List<EnvSecretEntry>>(emptyList())
    val entries: StateFlow<List<EnvSecretEntry>> = _entries.asStateFlow()

    init {
        _entries.value = runCatching { readDecryptedEntriesSync() }.getOrDefault(emptyList())
    }

    fun currentEntries(): List<EnvSecretEntry> = _entries.value

    suspend fun upsertEntry(entry: EnvSecretEntry): Result<List<EnvSecretEntry>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val sanitizedKey = EnvFileCodec.sanitizeKey(entry.key)
                    require(sanitizedKey.isNotBlank() && EnvFileCodec.isValidKey(sanitizedKey)) {
                        "Secret key must start with a letter or underscore and use only A-Z, 0-9, _, ., -."
                    }
                    val normalized = entry.copy(
                        key = sanitizedKey,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    val updated = _entries.value.toMutableList()
                    val existingIdx = updated.indexOfFirst {
                        it.id == normalized.id || it.key.equals(normalized.key, ignoreCase = true)
                    }
                    if (existingIdx >= 0) {
                        updated[existingIdx] = normalized.copy(id = updated[existingIdx].id)
                    } else {
                        updated.add(normalized)
                    }
                    val sorted = updated.sortedBy { it.key }
                    writeEncryptedEntriesSync(sorted)
                    _entries.value = sorted
                    sorted
                }
            }
        }

    suspend fun updatePermissions(
        entryId: String,
        transform: (ModelSecretPermissions) -> ModelSecretPermissions
    ): Result<List<EnvSecretEntry>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val updated = _entries.value.map { entry ->
                    if (entry.id == entryId) {
                        entry.copy(
                            permissions = transform(entry.permissions),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    } else {
                        entry
                    }
                }
                writeEncryptedEntriesSync(updated)
                _entries.value = updated
                updated
            }
        }
    }

    suspend fun toggleAutoFill(
        entryId: String,
        enabled: Boolean,
        target: EnvAutoFillTarget? = null
    ): Result<List<EnvSecretEntry>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val updated = _entries.value.map { entry ->
                    if (entry.id == entryId) {
                        entry.copy(
                            autoFillEnabled = enabled,
                            autoFillTarget = target ?: entry.autoFillTarget,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    } else {
                        entry
                    }
                }
                writeEncryptedEntriesSync(updated)
                _entries.value = updated
                updated
            }
        }
    }

    suspend fun deleteEntry(entryId: String): Result<List<EnvSecretEntry>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val updated = _entries.value.filterNot { it.id == entryId }
                    writeEncryptedEntriesSync(updated)
                    _entries.value = updated
                    updated
                }
            }
        }

    suspend fun importEnvFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected .env file.")
                require(bytes.size <= 512_000) { ".env file exceeds 500 KB safety limit." }
                require(bytes.take(4096).none { it == 0.toByte() }) {
                    "Selected file appears to be binary rather than a UTF-8 .env file."
                }
                val text = String(bytes, Charsets.UTF_8)
                val existingByKey = _entries.value.associateBy { it.key.uppercase() }
                val imported = EnvFileCodec.parseEnvFile(text, existingByKey)
                require(imported.isNotEmpty()) {
                    "No valid KEY=VALUE pairs found in the selected .env file."
                }

                val merged = _entries.value.associateBy { it.key.uppercase() }.toMutableMap()
                imported.forEach { entry ->
                    merged[entry.key.uppercase()] = entry
                }
                val sorted = merged.values.sortedBy { it.key }
                writeEncryptedEntriesSync(sorted)
                _entries.value = sorted
                imported.size
            }
        }
    }

    suspend fun createStarterEnvInVault(): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val starters = listOf(
                    EnvSecretEntry(
                        key = "GITHUB_PAT",
                        value = "",
                        category = EnvSecretCategory.TOKEN,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.GITHUB_PAT,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "Fine-Grained GitHub Personal Access Token (Contents: Read/Write)"
                    ),
                    EnvSecretEntry(
                        key = "GITHUB_OAUTH_CLIENT_ID",
                        value = "",
                        category = EnvSecretCategory.TOKEN,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.GITHUB_OAUTH_CLIENT_ID,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "Optional GitHub OAuth App Client ID for Device Flow"
                    ),
                    EnvSecretEntry(
                        key = "OPENROUTER_API_KEY",
                        value = "",
                        category = EnvSecretCategory.API_KEY,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "BYOK External Router API Key (OpenRouter / OpenAI-compatible)"
                    ),
                    EnvSecretEntry(
                        key = "OPENROUTER_BASE_URL",
                        value = "https://openrouter.ai/api/v1/",
                        category = EnvSecretCategory.ENDPOINT,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.EXTERNAL_ROUTER_BASE_URL,
                        permissions = ModelSecretPermissions(allowReadOnly = true, noAccessHumanOnly = false),
                        note = "Base URL for OpenRouter or custom OpenAI-compatible gateway"
                    ),
                    EnvSecretEntry(
                        key = "SERVICE_USERNAME",
                        value = "",
                        category = EnvSecretCategory.LOGIN,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.CUSTOM_CREDENTIAL,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "Optional external service login username or email (Human Only)"
                    ),
                    EnvSecretEntry(
                        key = "SERVICE_PASSWORD",
                        value = "",
                        category = EnvSecretCategory.LOGIN,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.CUSTOM_CREDENTIAL,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "Optional external service login password (Human Only)"
                    )
                )
                val existingKeys = _entries.value.map { it.key.uppercase() }.toSet()
                val toAdd = starters.filterNot { it.key.uppercase() in existingKeys }
                val merged = (_entries.value + toAdd).sortedBy { it.key }
                writeEncryptedEntriesSync(merged)
                _entries.value = merged
                toAdd.size
            }
        }
    }

    suspend fun exportEnvToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entriesSnapshot = _entries.value
            val formatted = EnvFileCodec.formatAsEnvFile(entriesSnapshot, includeStarterTemplateIfEmpty = true)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(formatted.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open target destination to write .env file.")
            entriesSnapshot.size
        }
    }

    /**
     * Discretely resolves any secret whose Auto-Fill switch is enabled and value is non-blank.
     */
    fun resolveDiscreteAutoFill(currentEntries: List<EnvSecretEntry> = _entries.value): DiscreteAutoFillResolution {
        val enabled = currentEntries.filter { it.autoFillEnabled && it.value.isNotBlank() }
        var githubPat: String? = null
        var githubPatKey: String? = null
        var githubOAuth: String? = null
        var githubOAuthKey: String? = null
        var routerKey: String? = null
        var routerKeyName: String? = null
        var routerBaseUrl: String? = null
        var routerBaseUrlName: String? = null
        var neonUrl: String? = null
        var objectStorageUrl: String? = null
        var lanceDbUrl: String? = null
        var vpsGatewayUrl: String? = null
        var mcpAuthToken: String? = null

        enabled.forEach { entry ->
            when (entry.resolvedTarget()) {
                EnvAutoFillTarget.GITHUB_PAT -> if (githubPat == null) {
                    githubPat = entry.value.trim()
                    githubPatKey = entry.key
                }
                EnvAutoFillTarget.GITHUB_OAUTH_CLIENT_ID -> if (githubOAuth == null) {
                    githubOAuth = entry.value.trim()
                    githubOAuthKey = entry.key
                }
                EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY -> if (routerKey == null) {
                    routerKey = entry.value.trim()
                    routerKeyName = entry.key
                }
                EnvAutoFillTarget.EXTERNAL_ROUTER_BASE_URL -> if (routerBaseUrl == null) {
                    routerBaseUrl = entry.value.trim()
                    routerBaseUrlName = entry.key
                }
                EnvAutoFillTarget.NEON_DATABASE_URL -> if (neonUrl == null) {
                    neonUrl = entry.value.trim()
                }
                EnvAutoFillTarget.OBJECT_STORAGE_ENDPOINT -> if (objectStorageUrl == null) {
                    objectStorageUrl = entry.value.trim()
                }
                EnvAutoFillTarget.LANCEDB_URL -> if (lanceDbUrl == null) {
                    lanceDbUrl = entry.value.trim()
                }
                EnvAutoFillTarget.VPS_GATEWAY_URL -> if (vpsGatewayUrl == null) {
                    vpsGatewayUrl = entry.value.trim()
                }
                EnvAutoFillTarget.MCP_AUTH_TOKEN -> if (mcpAuthToken == null) {
                    mcpAuthToken = entry.value.trim()
                }
                else -> Unit
            }
        }

        return DiscreteAutoFillResolution(
            githubPat = githubPat,
            githubPatKeyName = githubPatKey,
            githubOAuthClientId = githubOAuth,
            githubOAuthClientIdKeyName = githubOAuthKey,
            externalRouterApiKey = routerKey,
            externalRouterApiKeyName = routerKeyName,
            externalRouterBaseUrl = routerBaseUrl,
            externalRouterBaseUrlKeyName = routerBaseUrlName,
            neonDatabaseUrl = neonUrl,
            objectStorageEndpoint = objectStorageUrl,
            lanceDbUrl = lanceDbUrl,
            vpsGatewayUrl = vpsGatewayUrl,
            mcpAuthToken = mcpAuthToken,
            activeAutoFillCount = enabled.size
        )
    }

    /**
     * Builds a strict, policy-enforced summary for AI model prompts.
     * - Entries marked `No Access (Human Only)` are completely excluded (neither key nor value exposed).
     * - Entries with `Read Only`, `Create`, or `Edit (Add/Delete/Change)` expose only their authorized
     *   permission declaration (never leaking raw secret values into cloud AI prompts unless an endpoint URL).
     */
    fun buildAuthorizedModelPolicySummary(): String {
        val allowed = _entries.value.filter { !it.permissions.noAccessHumanOnly }
        if (allowed.isEmpty()) return ""
        return buildString {
            appendLine("Authorized .env Environment Capabilities (Human-Only secrets strictly excluded):")
            allowed.forEach { entry ->
                val perms = entry.permissions.summaryLabel()
                val safeDisplay = if (entry.category == EnvSecretCategory.ENDPOINT && entry.permissions.canModelRead) {
                    " = ${entry.value.take(120)}"
                } else {
                    " (value redacted in transit)"
                }
                appendLine("- ${entry.key}$safeDisplay [$perms]")
            }
        }.trim()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun writeEncryptedEntriesSync(entries: List<EnvSecretEntry>) {
        val plaintext = EnvFileCodec.encodeEntriesJson(entries).toByteArray(Charsets.UTF_8)
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val envelope = JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }.toString()

        val tempFile = File(context.filesDir, "$VAULT_FILENAME.tmp")
        tempFile.writeText(envelope, Charsets.UTF_8)
        if (!tempFile.renameTo(vaultFile)) {
            vaultFile.writeText(envelope, Charsets.UTF_8)
            tempFile.delete()
        }
    }

    private fun readDecryptedEntriesSync(): List<EnvSecretEntry> {
        if (!vaultFile.exists()) return emptyList()
        return try {
            val raw = vaultFile.readText(Charsets.UTF_8)
            val envelope = JSONObject(raw)
            val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
            val ct = Base64.decode(envelope.getString("ct"), Base64.NO_WRAP)
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = String(cipher.doFinal(ct), Charsets.UTF_8)
            EnvFileCodec.decodeEntriesJson(plaintext)
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted vault could not be decrypted; starting empty.")
            emptyList()
        }
    }
}
