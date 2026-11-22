package com.example.data.infra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Universal Infrastructure & MCP Registry Engine.
 *
 * Design guarantees:
 * 1. Vendor-neutral BYO-infrastructure templates — ZERO hardcoded personal hostnames, IPs, or secrets.
 * 2. Standby-First — all connectors default to STANDBY with zero automatic background polling.
 * 3. Strict Privacy Isolation — PRIVATE_SOLO endpoints never leave the device; SANDBOXED_READ_ONLY
 *    enforces client-side write/mutation capability filtering (supporting forked read-only MCPs).
 * 4. Offline-Capable Time Sync — provides canonical UTC <-> Local Timezone envelopes immediately
 *    and upgrades seamlessly when a user's Time & Date Sync MCP server is online.
 */
object InfrastructureRegistryEngine {

    private val mutatingCapabilityTokens = listOf(
        "write", "create", "merge", "delete", "drop", "insert", "update",
        "mutate", "launch", "restart", "terminate", "deploy", "exec", "upload", "put"
    )

    private val blockedMetadataHosts = setOf(
        "169.254.169.254",
        "metadata.google.internal",
        "metadata.goog",
        "100.100.100.200"
    )

    fun defaultState(): InfrastructureRegistryState = InfrastructureRegistryState(
        gatewayBaseUrl = "",
        localTimezoneId = TimeZone.getDefault().id,
        attachTimeContextToAgents = true,
        connectors = defaultUniversalTemplates()
    )

    /**
     * Merges persisted user state with any newly shipped universal templates so upgrades
     * (such as Browser Use MCP fallback) appear cleanly without overwriting existing user configs.
     */
    fun mergeWithDefaults(persisted: InfrastructureRegistryState?): InfrastructureRegistryState {
        if (persisted == null || persisted.connectors.isEmpty()) return defaultState()
        val existingById = persisted.connectors.associateBy { it.id }
        val defaults = defaultUniversalTemplates()

        val orderedDefaults = defaults.map { template ->
            val saved = existingById[template.id]
            if (saved != null) {
                saved.copy(
                    fallbackAfterId = saved.fallbackAfterId ?: template.fallbackAfterId,
                    capabilities = saved.capabilities.ifEmpty { template.capabilities }
                )
            } else {
                template
            }
        }
        val defaultIds = defaults.map { it.id }.toSet()
        val customEntries = persisted.connectors.filterNot { it.id in defaultIds }
        return persisted.copy(connectors = orderedDefaults + customEntries)
    }

    fun defaultUniversalTemplates(): List<InfraConnectorProfile> = listOf(
        // --- TAB 1: DATABASES & OBJECT STORAGE ---
        InfraConnectorProfile(
            id = "db_neon_postgres",
            name = "Neon / PostgreSQL (PostGIS + AGE)",
            providerLabel = "PostgreSQL Ecosystem",
            kind = ConnectorKind.DATABASE_POSTGRES,
            templateKey = "neon_postgres",
            description = "Serverless PostgreSQL with PostGIS spatial geometry (SRID 4326) and Apache AGE graph extensions. Connects via HTTPS SQL proxy / gateway.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/db/neon-sql",
            vaultSecretKeyRef = "NEON_DATABASE_URL",
            capabilities = listOf("sql.read", "sql.write", "postgis.query", "age.cypher_read", "age.cypher_write", "migration.apply"),
            metadata = mapOf(
                "schema" to "public",
                "postgis_srid" to "4326",
                "age_graph" to "vortex_research",
                "transport" to "HTTPS SQL Proxy"
            )
        ),
        InfraConnectorProfile(
            id = "storage_object_s3",
            name = "Object Storage (S3 / R2 / MinIO)",
            providerLabel = "S3-Compatible Blob Tier",
            kind = ConnectorKind.OBJECT_STORAGE,
            templateKey = "object_storage_s3",
            description = "Offloads large research files, GeoJSON, GraphML, PNG/SVG diagrams, and audio notes above 256 KB to prevent hitting Firestore's 1 MB document ceiling.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/storage/presign",
            vaultSecretKeyRef = "OBJECT_STORAGE_ENDPOINT",
            capabilities = listOf("blob.read", "blob.presign_get", "blob.upload", "blob.delete"),
            metadata = mapOf(
                "bucket" to "vortex-artifacts",
                "offload_threshold_kb" to "256",
                "region" to "auto"
            )
        ),
        InfraConnectorProfile(
            id = "vector_lancedb",
            name = "LanceDB Vector Store",
            providerLabel = "Columnar Hybrid Vector DB",
            kind = ConnectorKind.VECTOR_LANCEDB,
            templateKey = "lancedb",
            description = "Semantic vector + full-text hybrid RAG indexing across research stages, voice notes, Roundtable critiques, and repository chunks.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/vector/lancedb",
            vaultSecretKeyRef = "LANCEDB_URL",
            capabilities = listOf("vector.search", "vector.hybrid_query", "vector.upsert", "vector.delete"),
            metadata = mapOf(
                "table" to "vortex_research_chunks",
                "metric" to "cosine",
                "dimensions" to "768"
            )
        ),

        // --- TAB 2: 9 UNIVERSAL MCP SERVER TEMPLATES ---
        InfraConnectorProfile(
            id = "mcp_temporal",
            name = "Temporal MCP",
            providerLabel = "Durable Workflow Engine",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_temporal",
            description = "Moves long-running 7-stage research pipelines, retries, and background sync jobs out of foreground Android coroutines into durable Temporal workflows.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/temporal",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("workflow.start", "workflow.status", "workflow.signal", "workflow.cancel"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP", "namespace" to "default")
        ),
        InfraConnectorProfile(
            id = "mcp_letta",
            name = "Letta MCP",
            providerLabel = "Stateful Agent Memory",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_letta",
            description = "Persistent hierarchical persona, core, and archival memory shared across research sessions and A.i. Roundtable critiques.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/letta",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("memory.read", "memory.search_archival", "memory.write", "persona.update"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP", "scope" to "project_memory")
        ),
        InfraConnectorProfile(
            id = "mcp_memgraph",
            name = "Memgraph MCP",
            providerLabel = "In-Memory Cypher Graph",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_memgraph",
            description = "Real-time Cypher property graph queries & ontology traversals. Supports a 1-tap Sandboxed Read-Only Fork preset for safe agentic collaboration.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/memgraph",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("cypher.read", "graph.schema", "graph.shortest_path", "cypher.write", "cypher.merge", "cypher.delete"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "fork_pattern" to "Supports Sandboxed Read-Only Fork"
            )
        ),
        InfraConnectorProfile(
            id = "mcp_time_sync",
            name = "Time & Date Sync MCP",
            providerLabel = "UTC ↔ Local Temporal Translator",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_time_sync",
            description = "Translates canonical UTC timestamps into the user's current local wall-clock timezone for all agents. Local fallback runs offline immediately.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/time-sync",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("time.now_utc", "time.to_local", "time.tz_convert"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP", "local_fallback" to "Active")
        ),
        InfraConnectorProfile(
            id = "mcp_vertex_solver",
            name = "Vertex Solver MCP",
            providerLabel = "Optimization & Constraint Solver",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_vertex_solver",
            description = "Solves formal graph optimization, spatial routing, and linear/constraint problems over extracted research entities and PostGIS tables.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/vertex-solver",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("solver.evaluate", "solver.optimize_graph", "solver.verify_constraints"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP")
        ),
        InfraConnectorProfile(
            id = "mcp_playwright",
            name = "Playwright MCP",
            providerLabel = "Deterministic Browser Automation",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_playwright",
            description = "Primary headless browser automation for deterministic DOM extraction, citation verification, and structured web capture.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/playwright",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("browser.navigate", "browser.extract_dom", "browser.screenshot", "browser.pdf"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP", "role" to "Primary Browser Engine")
        ),
        InfraConnectorProfile(
            id = "mcp_browser_use",
            name = "Browser Use MCP",
            providerLabel = "Fallback Autonomous Browser Agent",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_browser_use",
            description = "Automatic fallback method immediately after Playwright MCP when dynamic SPAs, visual navigation, or anti-selector layouts require agentic browser control.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/browser-use",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            fallbackAfterId = "mcp_playwright",
            capabilities = listOf("browser.agent_navigate", "browser.visual_extract", "browser.fallback_crawl"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "role" to "Fallback after Playwright MCP"
            )
        ),
        InfraConnectorProfile(
            id = "mcp_vps_access",
            name = "VPS-Access Launcher MCP",
            providerLabel = "Remote VPS & Container Control",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_vps_access",
            description = "Manages user-owned VPS instances (e.g., Contabo or any cloud VPS) for starting/checking gateway containers and remote workers. Strictly Private Solo by default.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/vps-access",
            vaultSecretKeyRef = "VPS_GATEWAY_URL",
            capabilities = listOf("vps.health_check", "vps.list_services", "vps.launch_service", "vps.restart_container"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP", "security" to "Keep Private Solo")
        ),
        InfraConnectorProfile(
            id = "mcp_world_mechanic",
            name = "World Mechanic MCP",
            providerLabel = "System Optimization Engine",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_world_mechanic",
            description = "Analyzes cross-silo telemetry, detects redundant pipeline work, and tunes orchestration budgets across your system.",
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = true,
            gatewayPath = "/mcp/world-mechanic",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("system.profile", "system.detect_overlap", "system.recommend_tuning"),
            metadata = mapOf("protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP")
        )
    )

    /**
     * Enforces the client-side Read-Only Sandbox Guard.
     * Whenever a connector is marked SANDBOXED_READ_ONLY or readOnlyEnforced = true,
     * all mutating capability verbs are stripped from the active agent manifest.
     */
    fun effectiveCapabilities(profile: InfraConnectorProfile): List<String> {
        if (!profile.isEffectivelyReadOnly) return profile.capabilities
        return profile.capabilities.filter { cap ->
            val lower = cap.lowercase(Locale.US)
            mutatingCapabilityTokens.none { token -> lower.contains(token) }
        }
    }

    fun blockedCapabilities(profile: InfraConnectorProfile): List<String> {
        if (!profile.isEffectivelyReadOnly) return emptyList()
        val allowed = effectiveCapabilities(profile).toSet()
        return profile.capabilities.filterNot { it in allowed }
    }

    /**
     * Validates remote endpoint URLs before saving or probing:
     * - Enforces HTTPS for remote endpoints (prevents cleartext credential leakage)
     * - Blocks known cloud metadata SSRF hosts
     */
    fun validateRemoteEndpointUrl(rawUrl: String): Pair<Boolean, String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return true to "Standby — no endpoint URL configured yet."
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return false to "Malformed URL syntax. Example: https://gateway.example.com/mcp/memgraph"
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (host.isBlank()) {
            return false to "URL must include a valid host name."
        }
        if (host in blockedMetadataHosts) {
            return false to "Blocked cloud metadata address ($host) for SSRF protection."
        }
        val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
        if (scheme != "https" && !(scheme == "http" && isLoopback)) {
            return false to "Remote endpoints must use https:// (cleartext http:// is only allowed on localhost)."
        }
        return true to "Valid ${scheme.uppercase(Locale.US)} endpoint."
    }

    /**
     * Manual, bounded, on-demand connectivity probe.
     * Never called automatically in the background while servers are offline in Standby.
     */
    suspend fun probeConnector(
        profile: InfraConnectorProfile,
        gatewayBaseUrl: String
    ): ConnectorProbeResult = withContext(Dispatchers.IO) {
        val target = profile.resolvedTargetUrl(gatewayBaseUrl)
        if (target.isBlank()) {
            return@withContext ConnectorProbeResult(
                status = ConnectorStatus.STANDBY,
                summary = "Standby — add a direct HTTPS URL or configure a Gateway Base URL when this server is online.",
                resolvedUrl = ""
            )
        }
        val (valid, validationMsg) = validateRemoteEndpointUrl(target)
        if (!valid) {
            return@withContext ConnectorProbeResult(
                status = ConnectorStatus.UNREACHABLE,
                summary = "Blocked: $validationMsg",
                resolvedUrl = target
            )
        }

        val startMs = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 4_000
                readTimeout = 4_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json, text/event-stream")
                setRequestProperty("User-Agent", "VortexResearcher-ConnectorProbe/1.0")
            }
            val code = conn.responseCode
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            // 2xx, 401/403 (auth required = server reachable), or 405 (HEAD not allowed on POST-only MCP) all prove endpoint liveness
            val reachable = code in 200..299 || code == 401 || code == 403 || code == 405
            if (reachable) {
                val authNote = if (code == 401 || code == 403) " (auth challenge received — bind vault token)" else ""
                ConnectorProbeResult(
                    status = ConnectorStatus.ONLINE,
                    httpCode = code,
                    latencyMs = elapsed,
                    summary = "Reachable · HTTP $code in ${elapsed}ms$authNote",
                    resolvedUrl = target
                )
            } else {
                ConnectorProbeResult(
                    status = ConnectorStatus.UNREACHABLE,
                    httpCode = code,
                    latencyMs = elapsed,
                    summary = "Standby / HTTP $code in ${elapsed}ms — server may be restarting or offline.",
                    resolvedUrl = target
                )
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ConnectorProbeResult(
                status = ConnectorStatus.UNREACHABLE,
                latencyMs = elapsed,
                summary = "Standby / Offline (${e.javaClass.simpleName}: ${e.message?.take(80) ?: "unreachable"})",
                resolvedUrl = target
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Zero-Leakage Share Filter:
     * Produces a shareable JSON descriptor manifest for team collaboration or GitHub docs.
     * STRICTLY excludes all PRIVATE_SOLO connectors and NEVER exports raw secret values.
     */
    fun exportShareableDescriptorsJson(state: InfrastructureRegistryState): String {
        val shareable = state.connectors.filter { it.visibility != ConnectorVisibility.PRIVATE_SOLO }
        val root = JSONObject().apply {
            put("schema", "vortex.infra.shareable-manifest.v1")
            put("privacyNote", "PRIVATE_SOLO endpoints and all Encrypted Env Vault secrets are strictly excluded.")
            put("exportedCount", shareable.size)
            val arr = JSONArray()
            shareable.forEach { c ->
                val target = c.resolvedTargetUrl(state.gatewayBaseUrl)
                arr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("kind", c.kind.wire)
                        put("visibility", c.visibility.wire)
                        put("accessMode", if (c.isEffectivelyReadOnly) "READ_ONLY" else "READ_WRITE")
                        put("endpointUrl", if (target.isNotBlank()) target else "(configured per collaborator)")
                        put("fallbackAfterId", c.fallbackAfterId ?: JSONObject.NULL)
                        put("allowedCapabilities", JSONArray(effectiveCapabilities(c)))
                    }
                )
            }
            put("connectors", arr)
        }
        return root.toString(2)
    }

    /**
     * Builds a safe, privacy-respecting runtime context block injected into the 7-stage
     * research pipeline prompts:
     * 1. Canonical UTC <-> Local Timezone envelope (when enabled).
     * 2. Authorized non-secret capability awareness (never leaks PRIVATE_SOLO URLs or vault tokens).
     */
    fun buildAuthorizedAgentInfraContext(state: InfrastructureRegistryState): String = buildString {
        if (state.attachTimeContextToAgents) {
            appendLine(TimeSyncContextEngine.buildAgentTemporalHeader(state))
        }
        val activeOrSandboxed = state.connectors.filter {
            it.status == ConnectorStatus.ONLINE ||
                it.visibility == ConnectorVisibility.SANDBOXED_READ_ONLY ||
                it.resolvedTargetUrl(state.gatewayBaseUrl).isNotBlank()
        }
        if (activeOrSandboxed.isNotEmpty()) {
            appendLine()
            appendLine("UNIVERSAL INFRASTRUCTURE & MCP CAPABILITY CONTRACT (Secrets & Private URLs Redacted):")
            activeOrSandboxed.forEach { c ->
                val mode = if (c.isEffectivelyReadOnly) "READ_ONLY_SANDBOX" else "STANDARD"
                val fallbackNote = c.fallbackAfterId?.let { " [Fallback after $it]" }.orEmpty()
                val caps = effectiveCapabilities(c).joinToString(", ")
                appendLine("- ${c.name} [${c.status.label} · ${c.visibility.shortBadge} · $mode]$fallbackNote: $caps")
            }
        }
    }.trim()
}

/**
 * Built-in local UTC <-> Local Timezone translator that mirrors the Time & Date Sync MCP contract.
 * Works immediately offline and auto-labels when a remote Time & Date Sync MCP server is ONLINE.
 */
object TimeSyncContextEngine {

    val commonTimezoneIds: List<String> = listOf(
        TimeZone.getDefault().id,
        "America/Los_Angeles",
        "America/Denver",
        "America/Chicago",
        "America/New_York",
        "UTC",
        "Europe/London",
        "Europe/Berlin",
        "Asia/Tokyo",
        "Asia/Singapore",
        "Australia/Sydney"
    ).distinct()

    fun currentEnvelope(
        timezoneId: String = TimeZone.getDefault().id,
        remoteTimeSyncOnline: Boolean = false
    ): TemporalSyncEnvelope {
        val now = Date()
        val tz = TimeZone.getTimeZone(timezoneId.ifBlank { TimeZone.getDefault().id })
        val utcFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val localFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = tz
        }
        val offsetMs = tz.getOffset(now.time)
        val sign = if (offsetMs >= 0) "+" else "-"
        val totalMinutes = abs(offsetMs) / 60_000
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val offsetLabel = String.format(Locale.US, "%s%02d:%02d", sign, hours, mins)

        return TemporalSyncEnvelope(
            utcIso = utcFmt.format(now),
            localIso = "${localFmt.format(now)}$offsetLabel",
            timezoneId = tz.id,
            timezoneDisplayName = tz.getDisplayName(tz.inDaylightTime(now), TimeZone.SHORT, Locale.US),
            utcOffsetLabel = "UTC$offsetLabel",
            epochSeconds = now.time / 1000L,
            sourceLabel = if (remoteTimeSyncOnline) "Time & Date Sync MCP" else "Local Device Fallback (MCP Standby)"
        )
    }

    fun buildAgentTemporalHeader(state: InfrastructureRegistryState): String {
        val remoteOnline = state.connectors.any {
            it.id == "mcp_time_sync" && it.status == ConnectorStatus.ONLINE
        }
        val env = currentEnvelope(
            timezoneId = state.localTimezoneId,
            remoteTimeSyncOnline = remoteOnline
        )
        return buildString {
            appendLine("TEMPORAL SYNC CONTEXT (${env.sourceLabel}):")
            appendLine("- Canonical UTC: ${env.utcIso} (epoch=${env.epochSeconds})")
            appendLine("- User Local Wall-Clock: ${env.localIso} (${env.timezoneId} / ${env.timezoneDisplayName} / ${env.utcOffsetLabel})")
            append("- Interpret relative dates ('today', 'yesterday', 'this week') in ${env.timezoneId}.")
        }
    }
}
