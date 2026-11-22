package com.example.data.security

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class EnvSecretCategory(val wire: String, val label: String) {
    API_KEY("API_KEY", "API Key"),
    TOKEN("TOKEN", "Token"),
    LOGIN("LOGIN", "Login / Credential"),
    ENDPOINT("ENDPOINT", "Endpoint / URL"),
    CUSTOM("CUSTOM", "Custom Secret");

    companion object {
        fun fromWire(value: String?): EnvSecretCategory =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: CUSTOM

        fun inferFromKey(key: String): EnvSecretCategory {
            val upper = key.uppercase()
            return when {
                upper.contains("URL") || upper.contains("ENDPOINT") || upper.contains("HOST") -> ENDPOINT
                upper.contains("PAT") || upper.contains("TOKEN") || upper.contains("BEARER") || upper.contains("OAUTH") -> TOKEN
                upper.contains("USER") || upper.contains("EMAIL") || upper.contains("PASS") || upper.contains("LOGIN") || upper.contains("ACCOUNT") -> LOGIN
                upper.contains("KEY") || upper.contains("SECRET") || upper.contains("API") -> API_KEY
                else -> CUSTOM
            }
        }
    }
}

enum class EnvAutoFillTarget(val wire: String, val label: String, val hintKeys: List<String>) {
    AUTO_DETECT(
        wire = "AUTO_DETECT",
        label = "Auto-Detect by Key Name",
        hintKeys = emptyList()
    ),
    GITHUB_PAT(
        wire = "GITHUB_PAT",
        label = "GitHub Token / PAT",
        hintKeys = listOf("GITHUB_PAT", "GITHUB_TOKEN", "GH_TOKEN", "VORTEX_GITHUB_PAT")
    ),
    GITHUB_OAUTH_CLIENT_ID(
        wire = "GITHUB_OAUTH_CLIENT_ID",
        label = "GitHub OAuth Client ID",
        hintKeys = listOf("GITHUB_OAUTH_CLIENT_ID", "GITHUB_CLIENT_ID", "GH_CLIENT_ID")
    ),
    EXTERNAL_ROUTER_API_KEY(
        wire = "EXTERNAL_ROUTER_API_KEY",
        label = "External AI Router API Key",
        hintKeys = listOf("OPENROUTER_API_KEY", "OPENAI_API_KEY", "EXTERNAL_API_KEY", "ROUTER_API_KEY", "ANTHROPIC_API_KEY")
    ),
    EXTERNAL_ROUTER_BASE_URL(
        wire = "EXTERNAL_ROUTER_BASE_URL",
        label = "External AI Router Base URL",
        hintKeys = listOf("OPENROUTER_BASE_URL", "OPENAI_BASE_URL", "EXTERNAL_BASE_URL", "ROUTER_BASE_URL")
    ),
    NEON_DATABASE_URL(
        wire = "NEON_DATABASE_URL",
        label = "PostgreSQL / Neon Proxy URL",
        hintKeys = listOf("NEON_DATABASE_URL", "NEON_HTTP_URL", "POSTGRES_URL", "DATABASE_URL")
    ),
    OBJECT_STORAGE_ENDPOINT(
        wire = "OBJECT_STORAGE_ENDPOINT",
        label = "Object Storage Endpoint (S3/R2)",
        hintKeys = listOf("OBJECT_STORAGE_ENDPOINT", "S3_ENDPOINT", "R2_ENDPOINT", "STORAGE_ENDPOINT")
    ),
    LANCEDB_URL(
        wire = "LANCEDB_URL",
        label = "LanceDB Vector Endpoint",
        hintKeys = listOf("LANCEDB_URL", "LANCEDB_ENDPOINT", "VECTOR_DB_URL")
    ),
    VPS_GATEWAY_URL(
        wire = "VPS_GATEWAY_URL",
        label = "VPS / MCP Gateway Base URL",
        hintKeys = listOf("VPS_GATEWAY_URL", "MCP_GATEWAY_URL", "CONTABO_GATEWAY_URL", "GATEWAY_BASE_URL")
    ),
    MCP_AUTH_TOKEN(
        wire = "MCP_AUTH_TOKEN",
        label = "MCP / Gateway Bearer Token",
        hintKeys = listOf("MCP_AUTH_TOKEN", "MCP_TOKEN", "GATEWAY_AUTH_TOKEN", "MEMGRAPH_MCP_TOKEN")
    ),
    CUSTOM_CREDENTIAL(
        wire = "CUSTOM_CREDENTIAL",
        label = "Custom / Manual Reference",
        hintKeys = emptyList()
    );

    companion object {
        fun fromWire(value: String?): EnvAutoFillTarget =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: AUTO_DETECT

        fun resolveEffectiveTarget(entry: EnvSecretEntry): EnvAutoFillTarget {
            if (entry.autoFillTarget != AUTO_DETECT) return entry.autoFillTarget
            val normalizedKey = entry.key.trim().uppercase()
            for (target in entries) {
                if (target.hintKeys.any { normalizedKey == it || normalizedKey.endsWith("_$it") }) {
                    return target
                }
            }
            return when {
                normalizedKey.contains("GITHUB") && (normalizedKey.contains("PAT") || normalizedKey.contains("TOKEN")) -> GITHUB_PAT
                normalizedKey.contains("GITHUB") && normalizedKey.contains("CLIENT") -> GITHUB_OAUTH_CLIENT_ID
                (normalizedKey.contains("OPENROUTER") || normalizedKey.contains("OPENAI") || normalizedKey.contains("EXTERNAL")) &&
                    normalizedKey.contains("KEY") -> EXTERNAL_ROUTER_API_KEY
                (normalizedKey.contains("OPENROUTER") || normalizedKey.contains("OPENAI") || normalizedKey.contains("EXTERNAL")) &&
                    (normalizedKey.contains("URL") || normalizedKey.contains("BASE")) -> EXTERNAL_ROUTER_BASE_URL
                normalizedKey.contains("NEON") || normalizedKey.contains("POSTGRES") -> NEON_DATABASE_URL
                normalizedKey.contains("OBJECT_STORAGE") || normalizedKey.contains("R2_") || normalizedKey.contains("S3_") -> OBJECT_STORAGE_ENDPOINT
                normalizedKey.contains("LANCEDB") || normalizedKey.contains("VECTOR_DB") -> LANCEDB_URL
                (normalizedKey.contains("VPS") || normalizedKey.contains("GATEWAY")) &&
                    (normalizedKey.contains("URL") || normalizedKey.contains("ENDPOINT")) -> VPS_GATEWAY_URL
                normalizedKey.contains("MCP") && (normalizedKey.contains("TOKEN") || normalizedKey.contains("KEY")) -> MCP_AUTH_TOKEN
                else -> CUSTOM_CREDENTIAL
            }
        }
    }
}

/**
 * Per-entry AI model access policy:
 * 1) Create
 * 2) Edit (with 3 explicit sub-permissions: Add, Delete, Change)
 * 3) Read Only
 * 4) No Access (Human Only) — default safest zero-trust posture
 */
data class ModelSecretPermissions(
    val allowCreate: Boolean = false,
    val allowEditAdd: Boolean = false,
    val allowEditDelete: Boolean = false,
    val allowEditChange: Boolean = false,
    val allowReadOnly: Boolean = false,
    val noAccessHumanOnly: Boolean = true
) {
    val hasAnyEdit: Boolean
        get() = allowEditAdd || allowEditDelete || allowEditChange

    val hasAllEdit: Boolean
        get() = allowEditAdd && allowEditDelete && allowEditChange

    val canModelRead: Boolean
        get() = !noAccessHumanOnly && (allowReadOnly || allowCreate || hasAnyEdit)

    fun summaryLabel(): String {
        if (noAccessHumanOnly) return "Human Only (No AI Access)"
        if (allowReadOnly && !allowCreate && !hasAnyEdit) return "AI: Read Only"
        val parts = mutableListOf<String>()
        if (allowCreate) parts.add("Create")
        if (hasAnyEdit) {
            val subs = mutableListOf<String>()
            if (allowEditAdd) subs.add("Add")
            if (allowEditDelete) subs.add("Del")
            if (allowEditChange) subs.add("Chg")
            parts.add("Edit(${subs.joinToString("/")})")
        }
        if (allowReadOnly) parts.add("Read")
        return if (parts.isEmpty()) "Human Only (No AI Access)" else "AI: ${parts.joinToString(" · ")}"
    }

    fun toggleCreate(enabled: Boolean): ModelSecretPermissions {
        val next = copy(
            allowCreate = enabled,
            allowReadOnly = if (enabled) false else allowReadOnly,
            noAccessHumanOnly = if (enabled) false else noAccessHumanOnly
        )
        return next.ensureConsistent()
    }

    fun toggleEditParent(enabled: Boolean): ModelSecretPermissions {
        val next = copy(
            allowEditAdd = enabled,
            allowEditDelete = enabled,
            allowEditChange = enabled,
            allowReadOnly = if (enabled) false else allowReadOnly,
            noAccessHumanOnly = if (enabled) false else noAccessHumanOnly
        )
        return next.ensureConsistent()
    }

    fun toggleEditAdd(enabled: Boolean): ModelSecretPermissions {
        val next = copy(
            allowEditAdd = enabled,
            allowReadOnly = if (enabled) false else allowReadOnly,
            noAccessHumanOnly = if (enabled) false else noAccessHumanOnly
        )
        return next.ensureConsistent()
    }

    fun toggleEditDelete(enabled: Boolean): ModelSecretPermissions {
        val next = copy(
            allowEditDelete = enabled,
            allowReadOnly = if (enabled) false else allowReadOnly,
            noAccessHumanOnly = if (enabled) false else noAccessHumanOnly
        )
        return next.ensureConsistent()
    }

    fun toggleEditChange(enabled: Boolean): ModelSecretPermissions {
        val next = copy(
            allowEditChange = enabled,
            allowReadOnly = if (enabled) false else allowReadOnly,
            noAccessHumanOnly = if (enabled) false else noAccessHumanOnly
        )
        return next.ensureConsistent()
    }

    fun toggleReadOnly(enabled: Boolean): ModelSecretPermissions {
        val next = if (enabled) {
            ModelSecretPermissions(
                allowCreate = false,
                allowEditAdd = false,
                allowEditDelete = false,
                allowEditChange = false,
                allowReadOnly = true,
                noAccessHumanOnly = false
            )
        } else {
            copy(allowReadOnly = false)
        }
        return next.ensureConsistent()
    }

    fun toggleNoAccessHumanOnly(enabled: Boolean): ModelSecretPermissions {
        return if (enabled) {
            ModelSecretPermissions(
                allowCreate = false,
                allowEditAdd = false,
                allowEditDelete = false,
                allowEditChange = false,
                allowReadOnly = false,
                noAccessHumanOnly = true
            )
        } else {
            // Unchecking No Access defaults cleanly to safest AI scope: Read Only
            ModelSecretPermissions(
                allowCreate = false,
                allowEditAdd = false,
                allowEditDelete = false,
                allowEditChange = false,
                allowReadOnly = true,
                noAccessHumanOnly = false
            )
        }
    }

    private fun ensureConsistent(): ModelSecretPermissions {
        val anyAi = allowCreate || allowEditAdd || allowEditDelete || allowEditChange || allowReadOnly
        return if (!anyAi) {
            ModelSecretPermissions(noAccessHumanOnly = true)
        } else {
            copy(noAccessHumanOnly = false)
        }
    }
}

data class EnvSecretEntry(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val category: EnvSecretCategory = EnvSecretCategory.inferFromKey(key),
    val autoFillEnabled: Boolean = false,
    val autoFillTarget: EnvAutoFillTarget = EnvAutoFillTarget.AUTO_DETECT,
    val permissions: ModelSecretPermissions = ModelSecretPermissions(),
    val note: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun maskedValue(): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "(empty)"
        if (trimmed.length <= 6) return "••••••"
        return "${trimmed.take(3)}••••••••${trimmed.takeLast(3)}"
    }

    fun resolvedTarget(): EnvAutoFillTarget = EnvAutoFillTarget.resolveEffectiveTarget(this)
}

data class DiscreteAutoFillResolution(
    val githubPat: String? = null,
    val githubPatKeyName: String? = null,
    val githubOAuthClientId: String? = null,
    val githubOAuthClientIdKeyName: String? = null,
    val externalRouterApiKey: String? = null,
    val externalRouterApiKeyName: String? = null,
    val externalRouterBaseUrl: String? = null,
    val externalRouterBaseUrlKeyName: String? = null,
    val neonDatabaseUrl: String? = null,
    val objectStorageEndpoint: String? = null,
    val lanceDbUrl: String? = null,
    val vpsGatewayUrl: String? = null,
    val mcpAuthToken: String? = null,
    val activeAutoFillCount: Int = 0
)

enum class EnvValidationSeverity {
    EMPTY,
    VALID,
    WARNING,
    ERROR
}

data class EnvFieldValidationResult(
    val severity: EnvValidationSeverity,
    val message: String,
    val canSave: Boolean = severity != EnvValidationSeverity.ERROR
)

/**
 * Validation layer for `.env` variable keys, values, and template slot formats.
 * Prevents broken `.env` syntax, newline/assignment injection, placeholder leaks,
 * and malformed URLs or tokens before saving to the encrypted vault.
 */
object EnvSecretValidator {

    private val placeholderTokens = listOf(
        "your_api_key", "your_token", "paste_here", "changeme", "replace_me", "<token>", "<key>", "xxx"
    )
    private val httpsUrlRegex = Regex("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/.*)?$")
    private val controlCharRegex = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun validateKey(rawKey: String, existingKeys: Set<String> = emptySet(), currentKey: String? = null): EnvFieldValidationResult {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Variable key is required (e.g., GITHUB_PAT)."
            )
        }
        if (trimmed.any { it.isWhitespace() }) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Key cannot contain spaces. Use underscores (e.g., MY_API_KEY)."
            )
        }
        val normalized = trimmed.uppercase()
        if (!EnvFileCodec.isValidKey(normalized)) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Key must start with A–Z or _ and use only letters, digits, _, ., or -."
            )
        }
        val isDuplicate = existingKeys.any { it.equals(normalized, ignoreCase = true) } &&
            !normalized.equals(currentKey, ignoreCase = true)
        if (isDuplicate) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "A secret named $normalized already exists in the vault."
            )
        }
        return EnvFieldValidationResult(
            severity = EnvValidationSeverity.VALID,
            message = "Valid .env key format."
        )
    }

    fun validateValue(
        key: String,
        value: String,
        category: EnvSecretCategory = EnvSecretCategory.inferFromKey(key),
        target: EnvAutoFillTarget = EnvAutoFillTarget.AUTO_DETECT
    ): EnvFieldValidationResult {
        if (value.isBlank()) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.EMPTY,
                message = "Not set yet — tap Paste from Clipboard to fill safely.",
                canSave = true
            )
        }

        if (controlCharRegex.containsMatchIn(value)) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Contains invalid control characters. Re-copy and paste cleanly."
            )
        }

        val isPem = value.contains("-----BEGIN ") && value.contains("-----END ")
        if (!isPem && (value.contains('\n') || value.contains('\r'))) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Multi-line value detected (breaks single-line .env format). Use Paste to auto-clean."
            )
        }

        val trimmed = value.trim()
        if (trimmed != value) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.WARNING,
                message = "Has leading/trailing spaces — will be trimmed automatically on save."
            )
        }

        val lower = trimmed.lowercase()
        if (placeholderTokens.any { lower == it || lower.startsWith("<") && lower.endsWith(">") }) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.WARNING,
                message = "Looks like a placeholder value rather than a real credential."
            )
        }

        val cleanKey = EnvFileCodec.sanitizeKey(key)
        if (cleanKey.isNotBlank() && trimmed.startsWith("$cleanKey=", ignoreCase = true)) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Value repeats '$cleanKey=' prefix — remove the prefix or tap Paste to auto-strip."
            )
        }
        if (trimmed.startsWith("export ", ignoreCase = true) || trimmed.startsWith("Bearer ", ignoreCase = true)) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Value includes a shell/header prefix ('export' or 'Bearer'). Strip it before saving."
            )
        }

        val resolvedTarget = if (target != EnvAutoFillTarget.AUTO_DETECT) {
            target
        } else {
            EnvAutoFillTarget.resolveEffectiveTarget(EnvSecretEntry(key = key, value = value))
        }

        return when (resolvedTarget) {
            EnvAutoFillTarget.GITHUB_PAT -> {
                if (trimmed.any { it.isWhitespace() }) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.ERROR,
                        message = "GitHub token cannot contain spaces."
                    )
                } else if (trimmed.length < 20) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.WARNING,
                        message = "Short token (${trimmed.length} chars) — GitHub PATs typically start with 'ghp_' or 'github_pat_'."
                    )
                } else if (trimmed.startsWith("ghp_") || trimmed.startsWith("github_pat_") ||
                    trimmed.startsWith("gho_") || trimmed.startsWith("ghu_") || trimmed.startsWith("ghs_")
                ) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.VALID,
                        message = "Valid GitHub token format."
                    )
                } else {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.WARNING,
                        message = "Unusual prefix — verify this is a valid GitHub Personal Access Token."
                    )
                }
            }

            EnvAutoFillTarget.GITHUB_OAUTH_CLIENT_ID -> {
                if (trimmed.any { it.isWhitespace() }) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.ERROR,
                        message = "OAuth Client ID cannot contain spaces."
                    )
                } else if (trimmed.length < 10) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.WARNING,
                        message = "GitHub OAuth Client IDs are usually 20+ characters (e.g., Ov23li...)."
                    )
                } else {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.VALID,
                        message = "Valid OAuth Client ID format."
                    )
                }
            }

            EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY -> {
                if (trimmed.any { it.isWhitespace() }) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.ERROR,
                        message = "API key cannot contain spaces."
                    )
                } else if (trimmed.length < 16) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.WARNING,
                        message = "API key looks unusually short (${trimmed.length} chars)."
                    )
                } else {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.VALID,
                        message = "Valid API key format."
                    )
                }
            }

            EnvAutoFillTarget.EXTERNAL_ROUTER_BASE_URL,
            EnvAutoFillTarget.OBJECT_STORAGE_ENDPOINT,
            EnvAutoFillTarget.LANCEDB_URL,
            EnvAutoFillTarget.VPS_GATEWAY_URL -> {
                validateEndpointUrl(trimmed)
            }

            EnvAutoFillTarget.NEON_DATABASE_URL -> {
                if (trimmed.startsWith("postgres://", ignoreCase = true) ||
                    trimmed.startsWith("postgresql://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.VALID,
                        message = "Valid PostgreSQL / Neon connection URL."
                    )
                } else {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.WARNING,
                        message = "Expected https:// (SQL proxy) or postgresql:// connection string."
                    )
                }
            }

            EnvAutoFillTarget.MCP_AUTH_TOKEN -> {
                if (trimmed.any { it.isWhitespace() }) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.ERROR,
                        message = "MCP bearer token cannot contain spaces."
                    )
                } else {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.VALID,
                        message = "Valid MCP / Gateway auth token."
                    )
                }
            }

            else -> {
                if (category == EnvSecretCategory.ENDPOINT) {
                    validateEndpointUrl(trimmed)
                } else if ((category == EnvSecretCategory.API_KEY || category == EnvSecretCategory.TOKEN) &&
                    trimmed.any { it.isWhitespace() }
                ) {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.WARNING,
                        message = "Contains spaces — double-check if this key/token should have whitespace."
                    )
                } else {
                    EnvFieldValidationResult(
                        severity = EnvValidationSeverity.VALID,
                        message = "Formatted cleanly for .env storage."
                    )
                }
            }
        }
    }

    private fun validateEndpointUrl(url: String): EnvFieldValidationResult {
        if (!httpsUrlRegex.matches(url)) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.ERROR,
                message = "Must be a valid URL starting with https:// (e.g., https://openrouter.ai/api/v1/)."
            )
        }
        if (url.startsWith("http://", ignoreCase = true)) {
            return EnvFieldValidationResult(
                severity = EnvValidationSeverity.WARNING,
                message = "Unencrypted http:// URL — prefer https:// for security."
            )
        }
        return EnvFieldValidationResult(
            severity = EnvValidationSeverity.VALID,
            message = "Valid HTTPS endpoint URL."
        )
    }

    fun validateEntry(entry: EnvSecretEntry): EnvFieldValidationResult {
        val keyRes = validateKey(entry.key)
        if (keyRes.severity == EnvValidationSeverity.ERROR) return keyRes
        return validateValue(entry.key, entry.value, entry.category, entry.autoFillTarget)
    }
}

object EnvFileCodec {

    private val validKeyRegex = Regex("^[A-Za-z_][A-Za-z0-9_.-]{0,95}$")

    fun sanitizeKey(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("export ")
            .trim()
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .uppercase()
        return if (cleaned.isEmpty()) ""
        else if (!cleaned.first().isLetter() && cleaned.first() != '_') "_$cleaned"
        else cleaned.take(96)
    }

    fun isValidKey(key: String): Boolean = validKeyRegex.matches(key)

    /**
     * Cleans raw clipboard text pasted by a user so accidental `export KEY=`, `KEY=`,
     * `Authorization: Bearer `, stray surrounding quotes, or trailing newlines never break `.env` syntax.
     */
    fun sanitizeClipboardValue(rawClipboard: String, expectedKey: String = ""): String {
        var text = rawClipboard.trim()
        if (text.isEmpty()) return ""

        // Preserve intentional multi-line PEM blocks; otherwise collapse single-line copy artifacts
        val isPemBlock = text.contains("-----BEGIN ") && text.contains("-----END ")
        if (!isPemBlock) {
            text = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }

        if (text.startsWith("export ", ignoreCase = true)) {
            text = text.substring(7).trim()
        }

        // If user copied a full `KEY=value` assignment line matching this key, keep only the value
        val cleanKey = sanitizeKey(expectedKey)
        if (cleanKey.isNotBlank()) {
            val eqIdx = text.indexOf('=')
            if (eqIdx > 0) {
                val left = sanitizeKey(text.substring(0, eqIdx))
                if (left == cleanKey) {
                    text = text.substring(eqIdx + 1).trim()
                }
            }
        }

        // Strip accidental HTTP auth header prefixes copied from docs
        if (text.startsWith("Authorization:", ignoreCase = true)) {
            text = text.substringAfter(":").trim()
        }
        if (text.startsWith("Bearer ", ignoreCase = true)) {
            text = text.substring(7).trim()
        }

        // Strip accidental matching surrounding quotes
        if ((text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) ||
            (text.startsWith("'") && text.endsWith("'") && text.length >= 2)
        ) {
            text = text.substring(1, text.length - 1).trim()
        }
        return text
    }

    fun parseEnvFile(rawText: String, existingByKey: Map<String, EnvSecretEntry> = emptyMap()): List<EnvSecretEntry> {
        val parsed = linkedMapOf<String, EnvSecretEntry>()
        var pendingNote = ""

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                pendingNote = ""
                return@forEach
            }
            if (line.startsWith("#")) {
                val commentBody = line.removePrefix("#").trim()
                if (!commentBody.startsWith("@vortex", ignoreCase = true) && commentBody.isNotBlank()) {
                    pendingNote = commentBody.take(120)
                }
                return@forEach
            }

            val stripped = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
            val eqIndex = stripped.indexOf('=')
            if (eqIndex <= 0) return@forEach

            val rawKey = stripped.substring(0, eqIndex).trim()
            val key = sanitizeKey(rawKey)
            if (key.isBlank()) return@forEach

            var rawVal = stripped.substring(eqIndex + 1).trim()
            if ((rawVal.startsWith("\"") && rawVal.endsWith("\"") && rawVal.length >= 2) ||
                (rawVal.startsWith("'") && rawVal.endsWith("'") && rawVal.length >= 2)
            ) {
                val quote = rawVal.first()
                rawVal = rawVal.substring(1, rawVal.length - 1)
                if (quote == '"') {
                    rawVal = rawVal
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                }
            } else {
                // Strip unquoted trailing inline comment separated by whitespace
                val inlineCommentIdx = rawVal.indexOf(" #")
                if (inlineCommentIdx > 0) {
                    rawVal = rawVal.substring(0, inlineCommentIdx).trim()
                }
            }

            val existing = existingByKey[key]
            parsed[key] = EnvSecretEntry(
                id = existing?.id ?: UUID.randomUUID().toString(),
                key = key,
                value = rawVal,
                category = existing?.category ?: EnvSecretCategory.inferFromKey(key),
                autoFillEnabled = existing?.autoFillEnabled ?: false,
                autoFillTarget = existing?.autoFillTarget ?: EnvAutoFillTarget.AUTO_DETECT,
                permissions = existing?.permissions ?: ModelSecretPermissions(noAccessHumanOnly = true),
                note = pendingNote.ifBlank { existing?.note.orEmpty() },
                updatedAtMillis = System.currentTimeMillis()
            )
            pendingNote = ""
        }

        return parsed.values.toList()
    }

    fun formatAsEnvFile(entries: List<EnvSecretEntry>, includeStarterTemplateIfEmpty: Boolean = true): String =
        buildString {
            appendLine("# Vortex Researcher — Local .env Secrets File")
            appendLine("# Keep this file private and NEVER commit it to version control.")
            appendLine()
            if (entries.isEmpty() && includeStarterTemplateIfEmpty) {
                appendLine("# GitHub Mirror Token (Fine-Grained PAT with Contents Read/Write)")
                appendLine("GITHUB_PAT=")
                appendLine()
                appendLine("# Optional GitHub OAuth App Client ID (Device Flow)")
                appendLine("GITHUB_OAUTH_CLIENT_ID=")
                appendLine()
                appendLine("# Optional External AI Router (OpenRouter or OpenAI-Compatible)")
                appendLine("OPENROUTER_API_KEY=")
                appendLine("OPENROUTER_BASE_URL=https://openrouter.ai/api/v1/")
                appendLine()
                appendLine("# Custom Service Tokens & Logins (Human Only by default)")
                appendLine("CUSTOM_API_TOKEN=")
                appendLine("SERVICE_USERNAME=")
                appendLine("SERVICE_PASSWORD=")
            } else {
                entries.sortedBy { it.key }.forEach { entry ->
                    if (entry.note.isNotBlank()) {
                        appendLine("# ${entry.note.replace("\n", " ").take(120)}")
                    }
                    val escaped = entry.value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                    val needsQuotes = entry.value.any { it.isWhitespace() || it == '#' || it == '=' || it == '"' || it == '\'' }
                    if (needsQuotes) {
                        appendLine("${entry.key}=\"$escaped\"")
                    } else {
                        appendLine("${entry.key}=${entry.value}")
                    }
                }
            }
        }

    fun encodeEntriesJson(entries: List<EnvSecretEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("key", e.key)
                    put("value", e.value)
                    put("category", e.category.wire)
                    put("autoFillEnabled", e.autoFillEnabled)
                    put("autoFillTarget", e.autoFillTarget.wire)
                    put("note", e.note)
                    put("updatedAtMillis", e.updatedAtMillis)
                    put(
                        "permissions",
                        JSONObject().apply {
                            put("allowCreate", e.permissions.allowCreate)
                            put("allowEditAdd", e.permissions.allowEditAdd)
                            put("allowEditDelete", e.permissions.allowEditDelete)
                            put("allowEditChange", e.permissions.allowEditChange)
                            put("allowReadOnly", e.permissions.allowReadOnly)
                            put("noAccessHumanOnly", e.permissions.noAccessHumanOnly)
                        }
                    )
                }
            )
        }
        return arr.toString()
    }

    fun decodeEntriesJson(json: String): List<EnvSecretEntry> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val key = obj.optString("key").trim()
                    if (key.isBlank()) continue
                    val permObj = obj.optJSONObject("permissions")
                    val perms = if (permObj != null) {
                        ModelSecretPermissions(
                            allowCreate = permObj.optBoolean("allowCreate", false),
                            allowEditAdd = permObj.optBoolean("allowEditAdd", false),
                            allowEditDelete = permObj.optBoolean("allowEditDelete", false),
                            allowEditChange = permObj.optBoolean("allowEditChange", false),
                            allowReadOnly = permObj.optBoolean("allowReadOnly", false),
                            noAccessHumanOnly = permObj.optBoolean("noAccessHumanOnly", true)
                        )
                    } else {
                        ModelSecretPermissions(noAccessHumanOnly = true)
                    }
                    add(
                        EnvSecretEntry(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            key = key,
                            value = obj.optString("value"),
                            category = EnvSecretCategory.fromWire(obj.optString("category")),
                            autoFillEnabled = obj.optBoolean("autoFillEnabled", false),
                            autoFillTarget = EnvAutoFillTarget.fromWire(obj.optString("autoFillTarget")),
                            permissions = perms,
                            note = obj.optString("note"),
                            updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
