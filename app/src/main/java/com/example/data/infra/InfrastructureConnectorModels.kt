package com.example.data.infra

import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import java.util.UUID

/**
 * Universal infrastructure connector categories supported by Vortex Researcher.
 * Designed as vendor-neutral BYO-infrastructure contracts so every user can attach
 * their own databases, object storage, vector stores, and MCP endpoints.
 */
enum class ConnectorKind(val wire: String, val label: String) {
    DATABASE_POSTGRES("DATABASE_POSTGRES", "PostgreSQL / Neon"),
    OBJECT_STORAGE("OBJECT_STORAGE", "Object Storage (S3/R2)"),
    VECTOR_LANCEDB("VECTOR_LANCEDB", "LanceDB Vector"),
    MCP_SERVER("MCP_SERVER", "MCP Server"),
    CUSTOM_DB("CUSTOM_DB", "Custom Database / Storage"),
    CUSTOM_MCP("CUSTOM_MCP", "Custom MCP Server");

    val isStorageTier: Boolean
        get() = this == DATABASE_POSTGRES || this == OBJECT_STORAGE || this == VECTOR_LANCEDB || this == CUSTOM_DB

    val isMcpTier: Boolean
        get() = this == MCP_SERVER || this == CUSTOM_MCP

    companion object {
        fun fromWire(raw: String?): ConnectorKind =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: MCP_SERVER
    }
}

/**
 * Three-tier privacy & isolation boundary per connector:
 * - PRIVATE_SOLO: Strictly device-local (DataStore + Keystore Env Vault). Never leaves the device.
 *   Never included by name in third-party cloud prompts unless shareNameWithAi is toggled on.
 * - SANDBOXED_READ_ONLY: Shareable collaborative read-only fork (e.g., read-only Memgraph sandbox
 *   enforced on the server via MCP_READ_ONLY + query allowlist proxy).
 * - PUBLIC_TEAM: Explicit opt-in shared endpoint descriptor (secrets still remain local per user).
 */
enum class ConnectorVisibility(val wire: String, val label: String, val shortBadge: String) {
    PRIVATE_SOLO("PRIVATE_SOLO", "Private Solo (Device Only)", "PRIVATE SOLO"),
    SANDBOXED_READ_ONLY("SANDBOXED_READ_ONLY", "Sandboxed Read-Only Fork", "SANDBOX RO"),
    PUBLIC_TEAM("PUBLIC_TEAM", "Public / Team Opt-In", "PUBLIC TEAM");

    companion object {
        fun fromWire(raw: String?): ConnectorVisibility =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: PRIVATE_SOLO
    }
}

enum class ConnectorStatus(val wire: String, val label: String) {
    STANDBY("STANDBY", "Standby"),
    CONFIGURED("CONFIGURED", "Configured"),
    PROBING("PROBING", "Probing…"),
    ONLINE("ONLINE", "Online"),
    AUTH_REQUIRED("AUTH_REQUIRED", "Auth Required"),
    UNREACHABLE("UNREACHABLE", "Standby / Offline");

    companion object {
        fun fromWire(raw: String?): ConnectorStatus =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: STANDBY
    }
}

/**
 * Tool descriptor returned by an MCP server's live `tools/list` response.
 * [serverReadOnlyHint] mirrors `annotations.readOnlyHint` from the server and is
 * advisory metadata ONLY — never treated as a security guarantee by itself.
 */
data class McpDiscoveredTool(
    val name: String,
    val description: String = "",
    val serverReadOnlyHint: Boolean? = null
)

data class InfraConnectorProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerLabel: String,
    val kind: ConnectorKind,
    val templateKey: String,
    val description: String,
    val enabled: Boolean = true,
    val visibility: ConnectorVisibility = ConnectorVisibility.PRIVATE_SOLO,
    val readOnlyEnforced: Boolean = false,
    /**
     * User-set badge indicating "this server is configured read-only on the server side"
     * (e.g., Memgraph MCP_READ_ONLY + query allowlist proxy). Server-side controls are the
     * real security boundary; client-side display is advisory only.
     */
    val serverEnforcesReadOnly: Boolean = false,
    /**
     * For PRIVATE_SOLO connectors, controls whether the connector's name may appear in
     * third-party cloud LLM prompt context. Strictly OFF by default.
     */
    val shareNameWithAi: Boolean = false,
    /**
     * When true (mandatory for VPS-Access), EVERY tool invocation requires explicit user
     * confirmation regardless of readOnlyHint or user read-only markings.
     */
    val requirePerCallConfirmation: Boolean = false,
    /**
     * Whether this MCP server is permitted to be called directly from the phone client.
     * False for server-side-only tiers (World Mechanic "Stage" where phone is untrusted,
     * and Browser Use which runs only in an isolated server sandbox tier).
     */
    val phoneDirectCallable: Boolean = true,
    val status: ConnectorStatus = ConnectorStatus.STANDBY,
    /**
     * Per-connector direct HTTPS URL is the primary path (Tailscale `https://<machine>.<tailnet>.ts.net:<port>/mcp`
     * or Cloudflare Access sandbox URLs). Optional Gateway Base URL proxy remains available when toggled on.
     */
    val useGatewayProxy: Boolean = false,
    val endpointUrl: String = "",
    val gatewayPath: String = "",
    val vaultSecretKeyRef: String = "",
    /**
     * Optional additional HTTP headers whose values are resolved just-in-time from
     * EncryptedEnvVaultStore (Header-Name -> VAULT_KEY_REF). Raw values are never stored here.
     */
    val customHeaderVaultRefs: Map<String, String> = emptyMap(),
    val fallbackAfterId: String? = null,
    val fallbackTrigger: String = "",
    /**
     * Research pipeline stages (1..7) where this connector may appear in agent runtime context
     * when enabled, ONLINE, and callable.
     */
    val selectedStages: Set<Int> = ALL_PIPELINE_STAGES,
    /**
     * Expected tool names from templates ("expected; confirm with tools/list when online").
     */
    val capabilities: List<String> = emptyList(),
    /**
     * Live tool definitions fetched from the server via MCP `tools/list`.
     */
    val discoveredTools: List<McpDiscoveredTool> = emptyList(),
    /**
     * Deny-by-default exact-name allowlist of tools permitted to be invoked from the phone.
     * Populated only by explicit user opt-in from `discoveredTools` — never substring matched.
     */
    val allowedToolNames: Set<String> = emptySet(),
    /**
     * Exact tool names the user has explicitly marked as read-only in the Infrastructure Hub.
     * A tool is classified read-only ONLY if BOTH server `annotations.readOnlyHint == true`
     * AND the tool is in [userMarkedReadOnlyTools].
     */
    val userMarkedReadOnlyTools: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val isCustom: Boolean = false,
    val lastProbeMessage: String = "Standby mode — ready when server comes online.",
    val lastProbeAtMillis: Long = 0L
) {
    val isEffectivelyReadOnly: Boolean
        get() = readOnlyEnforced || visibility == ConnectorVisibility.SANDBOXED_READ_ONLY

    val hasLiveToolsList: Boolean
        get() = discoveredTools.isNotEmpty()

    /**
     * Strict dual-condition read-only classification:
     * A tool counts as read-only ONLY if:
     * 1. Connector does not require per-call confirmation (e.g., VPS-Access always confirms every call),
     * 2. The server's `tools/list` entry carries `annotations.readOnlyHint == true`, AND
     * 3. The user has explicitly marked that exact tool name read-only in the Infrastructure Hub.
     * Any tool missing either condition, or with an unknown/absent hint, is treated as non-read.
     */
    fun isToolClassifiedReadOnly(toolName: String): Boolean {
        if (requirePerCallConfirmation || id == ID_VPS_ACCESS) return false
        val discovered = discoveredTools.firstOrNull { it.name == toolName } ?: return false
        return discovered.serverReadOnlyHint == true && toolName in userMarkedReadOnlyTools
    }

    /**
     * Detects drift between expected template tool names and the live `tools/list` response.
     */
    fun toolListMismatchSummary(): ToolListMismatch? {
        if (discoveredTools.isEmpty() || capabilities.isEmpty()) return null
        val liveNames = discoveredTools.map { it.name }.toSet()
        val expectedNames = capabilities.toSet()
        val unexpectedOnServer = (liveNames - expectedNames).sorted()
        val missingFromServer = (expectedNames - liveNames).sorted()
        if (unexpectedOnServer.isEmpty() && missingFromServer.isEmpty()) return null
        return ToolListMismatch(
            unexpectedOnServer = unexpectedOnServer,
            missingFromServer = missingFromServer
        )
    }

    fun allowedVisibilities(): List<ConnectorVisibility> = when (id) {
        ID_VPS_ACCESS -> listOf(ConnectorVisibility.PRIVATE_SOLO)
        ID_WORLD_MECHANIC -> listOf(
            ConnectorVisibility.PRIVATE_SOLO,
            ConnectorVisibility.SANDBOXED_READ_ONLY
        )
        else -> ConnectorVisibility.entries
    }

    fun resolvedTargetUrl(gatewayBaseUrl: String): String {
        val direct = endpointUrl.trim()
        if (!useGatewayProxy) return direct
        val base = gatewayBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return direct
        val suffix = gatewayPath.trim().let { p ->
            if (p.isBlank()) "" else if (p.startsWith("/")) p else "/$p"
        }
        return "$base$suffix"
    }

    companion object {
        val ALL_PIPELINE_STAGES: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
        const val ID_WORLD_MECHANIC = "mcp_world_mechanic"
        const val ID_VPS_ACCESS = "mcp_vps_access"
        const val ID_MEMGRAPH = "mcp_memgraph"
        const val ID_TIME_SYNC = "mcp_time_sync"
        const val ID_PLAYWRIGHT = "mcp_playwright"
        const val ID_BROWSER_USE = "mcp_browser_use"
    }
}

data class ToolListMismatch(
    val unexpectedOnServer: List<String>,
    val missingFromServer: List<String>
)

data class InfrastructureRegistryState(
    val gatewayBaseUrl: String = "",
    val localTimezoneId: String = TimeZone.getDefault().id,
    val attachTimeContextToAgents: Boolean = true,
    val agentContextTokenBudget: Int = DEFAULT_AGENT_CONTEXT_TOKEN_BUDGET,
    val mcpResultCharCap: Int = DEFAULT_MCP_RESULT_CHAR_CAP,
    val connectors: List<InfraConnectorProfile> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val storageConnectors: List<InfraConnectorProfile>
        get() = connectors.filter { it.kind.isStorageTier }

    val mcpConnectors: List<InfraConnectorProfile>
        get() = connectors.filter { it.kind.isMcpTier }

    val privateSoloCount: Int
        get() = connectors.count { it.visibility == ConnectorVisibility.PRIVATE_SOLO }

    val sandboxedReadOnlyCount: Int
        get() = connectors.count { it.visibility == ConnectorVisibility.SANDBOXED_READ_ONLY }

    val publicTeamCount: Int
        get() = connectors.count { it.visibility == ConnectorVisibility.PUBLIC_TEAM }

    val configuredCount: Int
        get() = connectors.count { it.resolvedTargetUrl(gatewayBaseUrl).isNotBlank() }

    val onlineCount: Int
        get() = connectors.count { it.status == ConnectorStatus.ONLINE }

    val authRequiredCount: Int
        get() = connectors.count { it.status == ConnectorStatus.AUTH_REQUIRED }

    companion object {
        const val DEFAULT_AGENT_CONTEXT_TOKEN_BUDGET = 150
        const val DEFAULT_MCP_RESULT_CHAR_CAP = 2_000
    }
}

data class TemporalSyncEnvelope(
    val utcIso: String,
    val localIso: String,
    val timezoneId: String,
    val timezoneDisplayName: String,
    val utcOffsetLabel: String,
    val epochSeconds: Long,
    val sourceLabel: String
)

data class ConnectorProbeResult(
    val status: ConnectorStatus,
    val httpCode: Int? = null,
    val latencyMs: Long = 0L,
    val summary: String,
    val resolvedUrl: String = ""
)

object InfrastructureRegistryCodec {

    fun encode(state: InfrastructureRegistryState): String {
        val root = JSONObject().apply {
            put("v", 2)
            put("gatewayBaseUrl", state.gatewayBaseUrl)
            put("localTimezoneId", state.localTimezoneId)
            put("attachTimeContextToAgents", state.attachTimeContextToAgents)
            put("agentContextTokenBudget", state.agentContextTokenBudget.coerceIn(40, 1000))
            put("mcpResultCharCap", state.mcpResultCharCap.coerceIn(250, 12_000))
            put("updatedAtMillis", state.updatedAtMillis)
            val arr = JSONArray()
            state.connectors.forEach { c ->
                arr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("providerLabel", c.providerLabel)
                        put("kind", c.kind.wire)
                        put("templateKey", c.templateKey)
                        put("description", c.description)
                        put("enabled", c.enabled)
                        put("visibility", c.visibility.wire)
                        put("readOnlyEnforced", c.readOnlyEnforced)
                        put("serverEnforcesReadOnly", c.serverEnforcesReadOnly)
                        put("shareNameWithAi", c.shareNameWithAi)
                        put("requirePerCallConfirmation", c.requirePerCallConfirmation)
                        put("phoneDirectCallable", c.phoneDirectCallable)
                        put("status", c.status.wire)
                        put("useGatewayProxy", c.useGatewayProxy)
                        put("endpointUrl", c.endpointUrl)
                        put("gatewayPath", c.gatewayPath)
                        put("vaultSecretKeyRef", c.vaultSecretKeyRef)
                        put("customHeaderVaultRefs", JSONObject().apply {
                            c.customHeaderVaultRefs.forEach { (k, v) -> put(k, v) }
                        })
                        put("fallbackAfterId", c.fallbackAfterId ?: JSONObject.NULL)
                        put("fallbackTrigger", c.fallbackTrigger)
                        put("selectedStages", JSONArray(c.selectedStages.sorted()))
                        put("isCustom", c.isCustom)
                        put("lastProbeMessage", c.lastProbeMessage)
                        put("lastProbeAtMillis", c.lastProbeAtMillis)
                        put("capabilities", JSONArray(c.capabilities))
                        put("discoveredTools", JSONArray().apply {
                            c.discoveredTools.forEach { t ->
                                put(
                                    JSONObject().apply {
                                        put("name", t.name)
                                        put("description", t.description)
                                        if (t.serverReadOnlyHint == null) {
                                            put("serverReadOnlyHint", JSONObject.NULL)
                                        } else {
                                            put("serverReadOnlyHint", t.serverReadOnlyHint)
                                        }
                                    }
                                )
                            }
                        })
                        put("allowedToolNames", JSONArray(c.allowedToolNames.sorted()))
                        put("userMarkedReadOnlyTools", JSONArray(c.userMarkedReadOnlyTools.sorted()))
                        put("metadata", JSONObject().apply {
                            c.metadata.forEach { (k, v) -> put(k, v) }
                        })
                    }
                )
            }
            put("connectors", arr)
        }
        return root.toString()
    }

    fun decode(rawJson: String?): InfrastructureRegistryState? {
        if (rawJson.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("connectors") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    if (id.isBlank() || name.isBlank()) continue

                    val caps = decodeStringList(obj.optJSONArray("capabilities"))
                    val allowedTools = decodeStringList(obj.optJSONArray("allowedToolNames")).toSet()
                    val userMarkedRo = decodeStringList(obj.optJSONArray("userMarkedReadOnlyTools")).toSet()

                    val stagesArr = obj.optJSONArray("selectedStages")
                    val stages = if (stagesArr == null) {
                        InfraConnectorProfile.ALL_PIPELINE_STAGES
                    } else {
                        buildSet {
                            for (s in 0 until stagesArr.length()) {
                                val st = stagesArr.optInt(s, -1)
                                if (st in 1..7) add(st)
                            }
                        }
                    }

                    val discoveredArr = obj.optJSONArray("discoveredTools") ?: JSONArray()
                    val discovered = buildList {
                        for (j in 0 until discoveredArr.length()) {
                            val tObj = discoveredArr.optJSONObject(j) ?: continue
                            val tName = tObj.optString("name").trim()
                            if (tName.isBlank()) continue
                            val roHint: Boolean? = if (!tObj.has("serverReadOnlyHint") || tObj.isNull("serverReadOnlyHint")) {
                                null
                            } else {
                                tObj.optBoolean("serverReadOnlyHint")
                            }
                            add(
                                McpDiscoveredTool(
                                    name = tName,
                                    description = tObj.optString("description"),
                                    serverReadOnlyHint = roHint
                                )
                            )
                        }
                    }

                    val customHeaders = decodeStringMap(obj.optJSONObject("customHeaderVaultRefs"))
                    val meta = decodeStringMap(obj.optJSONObject("metadata"))
                    val rawFallback = if (obj.isNull("fallbackAfterId")) null
                    else obj.optString("fallbackAfterId").takeIf { it.isNotBlank() }

                    add(
                        InfraConnectorProfile(
                            id = id,
                            name = name,
                            providerLabel = obj.optString("providerLabel"),
                            kind = ConnectorKind.fromWire(obj.optString("kind")),
                            templateKey = obj.optString("templateKey"),
                            description = obj.optString("description"),
                            enabled = obj.optBoolean("enabled", true),
                            visibility = ConnectorVisibility.fromWire(obj.optString("visibility")),
                            readOnlyEnforced = obj.optBoolean("readOnlyEnforced", false),
                            serverEnforcesReadOnly = obj.optBoolean("serverEnforcesReadOnly", false),
                            shareNameWithAi = obj.optBoolean("shareNameWithAi", false),
                            requirePerCallConfirmation = obj.optBoolean("requirePerCallConfirmation", false),
                            phoneDirectCallable = obj.optBoolean("phoneDirectCallable", true),
                            status = ConnectorStatus.fromWire(obj.optString("status")),
                            useGatewayProxy = obj.optBoolean("useGatewayProxy", false),
                            endpointUrl = obj.optString("endpointUrl"),
                            gatewayPath = obj.optString("gatewayPath"),
                            vaultSecretKeyRef = obj.optString("vaultSecretKeyRef"),
                            customHeaderVaultRefs = customHeaders,
                            fallbackAfterId = rawFallback,
                            fallbackTrigger = obj.optString("fallbackTrigger"),
                            selectedStages = stages,
                            capabilities = caps,
                            discoveredTools = discovered,
                            allowedToolNames = allowedTools,
                            userMarkedReadOnlyTools = userMarkedRo,
                            metadata = meta,
                            isCustom = obj.optBoolean("isCustom", false),
                            lastProbeMessage = obj.optString(
                                "lastProbeMessage",
                                "Standby mode — ready when server comes online."
                            ),
                            lastProbeAtMillis = obj.optLong("lastProbeAtMillis", 0L)
                        )
                    )
                }
            }
            InfrastructureRegistryState(
                gatewayBaseUrl = root.optString("gatewayBaseUrl"),
                localTimezoneId = root.optString("localTimezoneId").ifBlank { TimeZone.getDefault().id },
                attachTimeContextToAgents = root.optBoolean("attachTimeContextToAgents", true),
                agentContextTokenBudget = root.optInt(
                    "agentContextTokenBudget",
                    InfrastructureRegistryState.DEFAULT_AGENT_CONTEXT_TOKEN_BUDGET
                ).coerceIn(40, 1000),
                mcpResultCharCap = root.optInt(
                    "mcpResultCharCap",
                    InfrastructureRegistryState.DEFAULT_MCP_RESULT_CHAR_CAP
                ).coerceIn(250, 12_000),
                connectors = list,
                updatedAtMillis = root.optLong("updatedAtMillis", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    private fun decodeStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (j in 0 until arr.length()) {
                val item = arr.optString(j).trim()
                if (item.isNotBlank()) add(item)
            }
        }
    }

    private fun decodeStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optString(k)
        }
        return out
    }
}
