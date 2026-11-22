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
 * Three-tier privacy & isolation boundary enforced per connector:
 * - PRIVATE_SOLO: Strictly device-local (DataStore + Keystore Env Vault). Never leaves the device.
 * - SANDBOXED_READ_ONLY: Shareable collaborative read-only fork (e.g., forked read-only Memgraph MCP).
 *   Enforces readOnlyEnforced = true and strips write/mutation capabilities client-side.
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
    UNREACHABLE("UNREACHABLE", "Standby / Offline");

    companion object {
        fun fromWire(raw: String?): ConnectorStatus =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: STANDBY
    }
}

data class InfraConnectorProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerLabel: String,
    val kind: ConnectorKind,
    val templateKey: String,
    val description: String,
    val visibility: ConnectorVisibility = ConnectorVisibility.PRIVATE_SOLO,
    val readOnlyEnforced: Boolean = false,
    val status: ConnectorStatus = ConnectorStatus.STANDBY,
    val useGatewayProxy: Boolean = false,
    val endpointUrl: String = "",
    val gatewayPath: String = "",
    val vaultSecretKeyRef: String = "",
    val fallbackAfterId: String? = null,
    val capabilities: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val isCustom: Boolean = false,
    val lastProbeMessage: String = "Standby mode — ready when server comes online.",
    val lastProbeAtMillis: Long = 0L
) {
    val isEffectivelyReadOnly: Boolean
        get() = readOnlyEnforced || visibility == ConnectorVisibility.SANDBOXED_READ_ONLY

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
}

data class InfrastructureRegistryState(
    val gatewayBaseUrl: String = "",
    val localTimezoneId: String = TimeZone.getDefault().id,
    val attachTimeContextToAgents: Boolean = true,
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
            put("v", 1)
            put("gatewayBaseUrl", state.gatewayBaseUrl)
            put("localTimezoneId", state.localTimezoneId)
            put("attachTimeContextToAgents", state.attachTimeContextToAgents)
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
                        put("visibility", c.visibility.wire)
                        put("readOnlyEnforced", c.readOnlyEnforced)
                        put("status", c.status.wire)
                        put("useGatewayProxy", c.useGatewayProxy)
                        put("endpointUrl", c.endpointUrl)
                        put("gatewayPath", c.gatewayPath)
                        put("vaultSecretKeyRef", c.vaultSecretKeyRef)
                        put("fallbackAfterId", c.fallbackAfterId ?: JSONObject.NULL)
                        put("isCustom", c.isCustom)
                        put("lastProbeMessage", c.lastProbeMessage)
                        put("lastProbeAtMillis", c.lastProbeAtMillis)
                        put("capabilities", JSONArray(c.capabilities))
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

                    val capsArr = obj.optJSONArray("capabilities") ?: JSONArray()
                    val caps = buildList {
                        for (j in 0 until capsArr.length()) {
                            val item = capsArr.optString(j).trim()
                            if (item.isNotBlank()) add(item)
                        }
                    }

                    val metaObj = obj.optJSONObject("metadata")
                    val meta = linkedMapOf<String, String>()
                    if (metaObj != null) {
                        val keys = metaObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            meta[k] = metaObj.optString(k)
                        }
                    }

                    val rawFallback = if (obj.isNull("fallbackAfterId")) null else obj.optString("fallbackAfterId").takeIf { it.isNotBlank() }

                    add(
                        InfraConnectorProfile(
                            id = id,
                            name = name,
                            providerLabel = obj.optString("providerLabel"),
                            kind = ConnectorKind.fromWire(obj.optString("kind")),
                            templateKey = obj.optString("templateKey"),
                            description = obj.optString("description"),
                            visibility = ConnectorVisibility.fromWire(obj.optString("visibility")),
                            readOnlyEnforced = obj.optBoolean("readOnlyEnforced", false),
                            status = ConnectorStatus.fromWire(obj.optString("status")),
                            useGatewayProxy = obj.optBoolean("useGatewayProxy", false),
                            endpointUrl = obj.optString("endpointUrl"),
                            gatewayPath = obj.optString("gatewayPath"),
                            vaultSecretKeyRef = obj.optString("vaultSecretKeyRef"),
                            fallbackAfterId = rawFallback,
                            capabilities = caps,
                            metadata = meta,
                            isCustom = obj.optBoolean("isCustom", false),
                            lastProbeMessage = obj.optString("lastProbeMessage", "Standby mode — ready when server comes online."),
                            lastProbeAtMillis = obj.optLong("lastProbeAtMillis", 0L)
                        )
                    )
                }
            }
            InfrastructureRegistryState(
                gatewayBaseUrl = root.optString("gatewayBaseUrl"),
                localTimezoneId = root.optString("localTimezoneId").ifBlank { TimeZone.getDefault().id },
                attachTimeContextToAgents = root.optBoolean("attachTimeContextToAgents", true),
                connectors = list,
                updatedAtMillis = root.optLong("updatedAtMillis", System.currentTimeMillis())
            )
        }.getOrNull()
    }
}
