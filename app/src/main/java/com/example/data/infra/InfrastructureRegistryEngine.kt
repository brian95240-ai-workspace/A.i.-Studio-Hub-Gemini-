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
 * 1. Vendor-neutral BYO-infrastructure templates — zero personal hostnames, IPs, ports, or secrets.
 * 2. Standby-First — all connectors default to STANDBY with zero automatic background polling.
 * 3. Direct Per-Connector HTTPS Primary Path — supports any valid HTTPS host (Tailscale
 *    `https://<machine>.<tailnet>.ts.net:<port>/mcp`, Cloudflare Access sandboxes, Neon, S3/R2/MinIO)
 *    while retaining optional Gateway Base URL proxy support.
 * 4. Honest Security Boundaries — server-side controls are the real security boundary. Client-side
 *    capability filtering is explicitly labeled Advisory capability display only; live MCP tool
 *    execution uses a deny-by-default exact-name allowlist from `tools/list`, never substrings.
 * 5. Privacy-Hardened Agent Context — only enabled + ONLINE + stage-selected + phone-callable
 *    connectors appear in agent runtime prompts, PRIVATE_SOLO names are omitted unless explicitly
 *    opted in, and total prompt context stays within a configurable token budget (default 150 tokens).
 */
object InfrastructureRegistryEngine {

    /**
     * ADVISORY ONLY — NOT a security control.
     * Real MCP tool names defeat substring heuristics (e.g., Memgraph's `run_cypher_query` can
     * execute write Cypher while matching none of these tokens). Live tool execution uses a
     * deny-by-default exact-name allowlist populated from the server's `tools/list`.
     */
    private val advisoryMutatingTokens = listOf(
        "write", "create", "merge", "delete", "drop", "insert", "update",
        "mutate", "launch", "restart", "terminate", "deploy", "exec", "upload", "put"
    )

    private val blockedMetadataHosts = setOf(
        "169.254.169.254",
        "metadata.google.internal",
        "metadata.goog",
        "100.100.100.200"
    )

    private const val APPROX_CHARS_PER_TOKEN = 4

    fun defaultState(): InfrastructureRegistryState = InfrastructureRegistryState(
        gatewayBaseUrl = "",
        localTimezoneId = TimeZone.getDefault().id,
        attachTimeContextToAgents = true,
        agentContextTokenBudget = InfrastructureRegistryState.DEFAULT_AGENT_CONTEXT_TOKEN_BUDGET,
        mcpResultCharCap = InfrastructureRegistryState.DEFAULT_MCP_RESULT_CHAR_CAP,
        connectors = defaultUniversalTemplates()
    )

    /**
     * Merges persisted user state with updated universal templates while preserving user URLs,
     * live `discoveredTools`, exact allowlists, and custom entries — and enforcing security
     * invariants on World Mechanic (no PUBLIC_TEAM, server-side only) and VPS-Access (PRIVATE_SOLO only,
     * per-call confirmation required).
     */
    fun mergeWithDefaults(persisted: InfrastructureRegistryState?): InfrastructureRegistryState {
        if (persisted == null || persisted.connectors.isEmpty()) return defaultState()
        val existingById = persisted.connectors.associateBy { it.id }
        val defaults = defaultUniversalTemplates()

        val orderedDefaults = defaults.map { template ->
            val saved = existingById[template.id]
            if (saved != null) {
                val sanitizedVisibility = if (saved.visibility in template.allowedVisibilities()) {
                    saved.visibility
                } else {
                    template.visibility
                }
                val enforcePerCall = template.requirePerCallConfirmation || saved.requirePerCallConfirmation
                val enforcePhoneCallable = if (!template.phoneDirectCallable) false else saved.phoneDirectCallable
                saved.copy(
                    name = template.name,
                    providerLabel = template.providerLabel,
                    description = template.description,
                    visibility = sanitizedVisibility,
                    requirePerCallConfirmation = enforcePerCall,
                    phoneDirectCallable = enforcePhoneCallable,
                    fallbackAfterId = saved.fallbackAfterId ?: template.fallbackAfterId,
                    fallbackTrigger = saved.fallbackTrigger.ifBlank { template.fallbackTrigger },
                    capabilities = template.capabilities,
                    metadata = template.metadata + saved.metadata
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
            description = "Serverless PostgreSQL with PostGIS spatial geometry (SRID 4326) and Apache AGE graph extensions. Direct HTTPS SQL endpoint primary (example: https://sql.example.com/query). Expected capabilities; confirm when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            serverEnforcesReadOnly = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/db/neon-sql",
            vaultSecretKeyRef = "NEON_DATABASE_URL",
            capabilities = listOf("sql.read", "sql.write", "postgis.query", "age.cypher_read", "age.cypher_write"),
            metadata = mapOf(
                "schema" to "public",
                "postgis_srid" to "4326",
                "age_graph" to "vortex_research",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = "storage_object_s3",
            name = "Object Storage (S3 / R2 / MinIO)",
            providerLabel = "S3-Compatible Blob Tier",
            kind = ConnectorKind.OBJECT_STORAGE,
            templateKey = "object_storage_s3",
            description = "Offloads large research files, GeoJSON, GraphML, diagrams, and audio above 256 KB. Direct HTTPS S3-compatible endpoint primary (example: https://storage.example.com). Expected capabilities; confirm when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            serverEnforcesReadOnly = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/storage/presign",
            vaultSecretKeyRef = "OBJECT_STORAGE_ENDPOINT",
            capabilities = listOf("blob.read", "blob.presign_get", "blob.upload", "blob.delete"),
            metadata = mapOf(
                "bucket" to "vortex-artifacts",
                "offload_threshold_kb" to "256",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = "vector_lancedb",
            name = "LanceDB Vector Store",
            providerLabel = "Columnar Hybrid Vector DB",
            kind = ConnectorKind.VECTOR_LANCEDB,
            templateKey = "lancedb",
            description = "Semantic vector + full-text hybrid RAG indexing across research stages, voice notes, Roundtable critiques, and repository chunks. Direct HTTPS URL primary. Expected capabilities; confirm when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            serverEnforcesReadOnly = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/vector/lancedb",
            vaultSecretKeyRef = "LANCEDB_URL",
            capabilities = listOf("vector.search", "vector.hybrid_query", "vector.upsert", "vector.delete"),
            metadata = mapOf(
                "table" to "vortex_research_chunks",
                "metric" to "cosine",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),

        // --- TAB 2: 9 UNIVERSAL MCP SERVER TEMPLATES ---
        InfraConnectorProfile(
            id = "mcp_temporal",
            name = "Temporal MCP",
            providerLabel = "Durable Workflow Engine",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_temporal",
            description = "Durable workflow orchestration for long-running pipelines and background jobs. Direct Tailscale/HTTPS URL primary (https://<machine>.<tailnet>.ts.net:<port>/mcp). Expected tools; confirm with tools/list when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/temporal",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("workflow.start", "workflow.status", "workflow.signal", "workflow.cancel"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = "mcp_letta",
            name = "Letta MCP",
            providerLabel = "Stateful Agent Memory",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_letta",
            description = "Persistent hierarchical persona, core, and archival memory across sessions. Direct HTTPS URL primary. Expected tools; confirm with tools/list when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/letta",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("memory.read", "memory.search_archival", "memory.write", "persona.update"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = InfraConnectorProfile.ID_MEMGRAPH,
            name = "Memgraph MCP",
            providerLabel = "In-Memory Cypher Graph",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_memgraph",
            description = "Cypher property graph queries & schema introspection. Production Memgraph is NEVER exposed; only a read-only sandbox copy is shared with collaborators (enforced server-side via MCP_READ_ONLY + query allowlist proxy). Note: run_cypher_query can write unless restricted server-side. Expected tools; confirm with tools/list when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            serverEnforcesReadOnly = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/memgraph",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf(
                "list_databases",
                "use_database",
                "run_cypher_query",
                "search_schema",
                "get_node_schema",
                "get_relationship_schema",
                "get_enum_schema"
            ),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "sandbox_enforcement" to "Server-side MCP_READ_ONLY + query allowlist proxy",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = InfraConnectorProfile.ID_TIME_SYNC,
            name = "Time & Date Sync MCP",
            providerLabel = "Optional Remote Clock (Local Engine Active)",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_time_sync",
            description = "Optional remote time server exposing get_current_time. The local offline time engine already covers the agent UTC/local temporal header, so a remote server is optional. Expected tool; confirm with tools/list when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            serverEnforcesReadOnly = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/time-sync",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("get_current_time"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "local_fallback" to "Local offline time engine covers agent header; remote optional",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = "mcp_vertex_solver",
            name = "Vertex Solver MCP",
            providerLabel = "Optimization & Constraint Solver",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_vertex_solver",
            description = "Formal graph optimization, spatial routing, and linear/constraint solving over extracted research entities. Direct HTTPS URL primary. Expected tools; confirm with tools/list when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/vertex-solver",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("solver.evaluate", "solver.optimize_graph", "solver.verify_constraints"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = InfraConnectorProfile.ID_PLAYWRIGHT,
            name = "Playwright MCP",
            providerLabel = "Deterministic Browser Automation",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_playwright",
            description = "Primary headless browser automation for deterministic DOM extraction and structured web capture. Treat all returned web/DOM content as untrusted data. Expected tools; confirm with tools/list when online.",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/playwright",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf("browser.navigate", "browser.extract_dom", "browser.screenshot", "browser.pdf"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "role" to "Primary Browser Engine",
                "output_trust" to "UNTRUSTED_DATA",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = InfraConnectorProfile.ID_BROWSER_USE,
            name = "Browser Use MCP",
            providerLabel = "Server-Side Fallback Browser Agent",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_browser_use",
            description = "Fallback browser agent after Playwright MCP. Runs ONLY server-side in an isolated sandbox tier and is NEVER driven directly from the phone. Treat all browser output as untrusted data. Expected tools; confirm with tools/list when online.",
            enabled = false,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = true,
            phoneDirectCallable = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/browser-use",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            fallbackAfterId = InfraConnectorProfile.ID_PLAYWRIGHT,
            fallbackTrigger = "Selector not found after retries, visual-only navigation, or anti-automation layout",
            capabilities = listOf("browser.agent_navigate", "browser.visual_extract", "browser.fallback_crawl"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "role" to "Server-side fallback after Playwright MCP (never phone-direct)",
                "output_trust" to "UNTRUSTED_DATA",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = InfraConnectorProfile.ID_VPS_ACCESS,
            name = "VPS-Access Launcher MCP",
            providerLabel = "Remote Service Control (Private Solo Only)",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_vps_access",
            description = "Launches and restarts remote services on user-owned hosts. Disabled by default, locked strictly to PRIVATE_SOLO (never Sandboxed Read-Only or Public Team), and requires explicit user confirmation on EVERY tool call. Expected tools; confirm with tools/list when online.",
            enabled = false,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            requirePerCallConfirmation = true,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/vps-access",
            vaultSecretKeyRef = "VPS_GATEWAY_URL",
            capabilities = listOf("vps.health_check", "vps.list_services", "vps.launch_service", "vps.restart_container"),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "security" to "Locked PRIVATE_SOLO · Confirm every call",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        ),
        InfraConnectorProfile(
            id = InfraConnectorProfile.ID_WORLD_MECHANIC,
            name = "World Mechanic (\"Stage\") MCP",
            providerLabel = "Sandboxed Code-Execution Server",
            kind = ConnectorKind.MCP_SERVER,
            templateKey = "mcp_world_mechanic",
            description = "Sandboxed code-execution server (\"Stage\"). Trust is bound server-side to the listening port; untrusted callers are refused, and phone-originated calls count as untrusted. Disabled by default; PUBLIC_TEAM visibility is blocked. Expected tools (stage_browse is a refusing stub); confirm with tools/list when online.",
            enabled = false,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            readOnlyEnforced = false,
            phoneDirectCallable = false,
            status = ConnectorStatus.STANDBY,
            useGatewayProxy = false,
            gatewayPath = "/mcp/world-mechanic",
            vaultSecretKeyRef = "MCP_AUTH_TOKEN",
            capabilities = listOf(
                "stage_exec",
                "stage_files",
                "stage_session",
                "stage_audit",
                "stage_browse"
            ),
            metadata = mapOf(
                "protocol" to "MCP JSON-RPC 2.0 / Streamable HTTP",
                "security" to "Server-side port-bound trust; phone calls count as untrusted",
                "stage_browse_note" to "Refusing stub",
                "tools_status" to "expected; confirm with tools/list when online"
            )
        )
    )

    /**
     * ADVISORY CAPABILITY DISPLAY ONLY — NOT a security control.
     * Displays expected capabilities that do not match common mutating tokens for quick UI scanning.
     * Real enforcement lives on the server (e.g., `serverEnforcesReadOnly`) and in the MCP client's
     * deny-by-default exact-name allowlist + dual-condition read-only confirmation gate.
     */
    fun advisoryReadCapabilities(profile: InfraConnectorProfile): List<String> {
        if (!profile.isEffectivelyReadOnly) return profile.capabilities
        return profile.capabilities.filter { cap ->
            val lower = cap.lowercase(Locale.US)
            advisoryMutatingTokens.none { token -> lower.contains(token) }
        }
    }

    /**
     * ADVISORY CAPABILITY DISPLAY ONLY — highlights capability strings matching common mutating tokens.
     * Note: Tools like `run_cypher_query` do not match these tokens even though they can mutate state.
     */
    fun advisoryMutatingCapabilities(profile: InfraConnectorProfile): List<String> {
        if (!profile.isEffectivelyReadOnly) return emptyList()
        val nonMutating = advisoryReadCapabilities(profile).toSet()
        return profile.capabilities.filterNot { it in nonMutating }
    }

    /**
     * Applies the 1-tap Memgraph Read-Only Sandbox preset:
     * marks visibility as SANDBOXED_READ_ONLY, readOnlyEnforced = true, and
     * serverEnforcesReadOnly = true (server enforces MCP_READ_ONLY + query allowlist proxy).
     */
    fun applyMemgraphSandboxPreset(profile: InfraConnectorProfile): InfraConnectorProfile =
        profile.copy(
            visibility = ConnectorVisibility.SANDBOXED_READ_ONLY,
            readOnlyEnforced = true,
            serverEnforcesReadOnly = true,
            lastProbeMessage = "Sandboxed Read-Only preset active — server must enforce MCP_READ_ONLY + query allowlist proxy."
        )

    /**
     * Validates remote endpoint URLs before saving, probing, or calling:
     * - Allows ANY valid HTTPS host (Tailscale `*.ts.net:<port>`, Cloudflare Access sandboxes,
     *   Neon, S3/R2/MinIO, custom HTTPS domains)
     * - Allows cleartext HTTP ONLY on loopback (`localhost`, `127.0.0.1`, `10.0.2.2`)
     * - Blocks known cloud-metadata SSRF hosts
     */
    fun validateRemoteEndpointUrl(rawUrl: String): Pair<Boolean, String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return true to "Standby — no endpoint URL configured yet."
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return false to "Malformed URL syntax. Example: https://<machine>.<tailnet>.ts.net:<port>/mcp or https://mcp.example.com/mcp"
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
        val portNote = if (uri.port > 0) " (port ${uri.port})" else ""
        return true to "Valid ${scheme.uppercase(Locale.US)} endpoint$portNote."
    }

    /**
     * Pure response classifier so probe status logic is deterministic and unit-testable.
     * - 2xx / 405 -> ONLINE (405 proves POST-only MCP endpoint is live)
     * - 401 / 403 -> AUTH_REQUIRED (reachable, needs Bearer token or identity auth)
     * - 301 / 302 / 303 / 307 / 308 -> AUTH_REQUIRED when redirecting to login/Cloudflare Access/auth,
     *   or AUTH_REQUIRED/redirect notice (redirects are NEVER followed automatically)
     * - Other codes -> UNREACHABLE
     */
    fun classifyProbeResponse(
        httpCode: Int,
        locationHeader: String?,
        latencyMs: Long,
        resolvedUrl: String
    ): ConnectorProbeResult {
        val loc = locationHeader?.trim().orEmpty()
        val locLower = loc.lowercase(Locale.US)
        val looksLikeAuthRedirect = locLower.contains("cloudflareaccess") ||
            locLower.contains("/cdn-cgi/access") ||
            locLower.contains("login") ||
            locLower.contains("auth") ||
            locLower.contains("sso") ||
            locLower.contains("signin")

        return when {
            httpCode in 200..299 || httpCode == 405 -> ConnectorProbeResult(
                status = ConnectorStatus.ONLINE,
                httpCode = httpCode,
                latencyMs = latencyMs,
                summary = "Online · HTTP $httpCode in ${latencyMs}ms",
                resolvedUrl = resolvedUrl
            )
            httpCode == 401 || httpCode == 403 -> ConnectorProbeResult(
                status = ConnectorStatus.AUTH_REQUIRED,
                httpCode = httpCode,
                latencyMs = latencyMs,
                summary = "Auth Required · HTTP $httpCode in ${latencyMs}ms — verify Bearer token in Vault or server identity login.",
                resolvedUrl = resolvedUrl
            )
            httpCode in listOf(301, 302, 303, 307, 308) -> {
                val hint = if (looksLikeAuthRedirect) {
                    "Auth Required · HTTP $httpCode login redirect (e.g., Cloudflare Access; redirects not followed)."
                } else {
                    "Auth Required / Redirect · HTTP $httpCode (redirects not followed; verify direct endpoint URL)."
                }
                ConnectorProbeResult(
                    status = ConnectorStatus.AUTH_REQUIRED,
                    httpCode = httpCode,
                    latencyMs = latencyMs,
                    summary = hint,
                    resolvedUrl = resolvedUrl
                )
            }
            else -> ConnectorProbeResult(
                status = ConnectorStatus.UNREACHABLE,
                httpCode = httpCode,
                latencyMs = latencyMs,
                summary = "Standby / HTTP $httpCode in ${latencyMs}ms — server may be restarting or offline.",
                resolvedUrl = resolvedUrl
            )
        }
    }

    /**
     * Manual, bounded, on-demand connectivity probe.
     * - Never called automatically in the background while servers stay in Standby.
     * - Resolves Authorization: Bearer <vaultSecretKeyRef> and any customHeaderVaultRefs
     *   just-in-time from [secretResolver] without logging secret values.
     * - Redirects are NEVER followed (`instanceFollowRedirects = false`).
     */
    suspend fun probeConnector(
        profile: InfraConnectorProfile,
        gatewayBaseUrl: String,
        secretResolver: (String) -> String? = { null }
    ): ConnectorProbeResult = withContext(Dispatchers.IO) {
        val target = profile.resolvedTargetUrl(gatewayBaseUrl)
        if (target.isBlank()) {
            return@withContext ConnectorProbeResult(
                status = ConnectorStatus.STANDBY,
                summary = "Standby — add a direct HTTPS URL (e.g. https://<machine>.<tailnet>.ts.net:<port>/mcp) when online.",
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
                setRequestProperty("User-Agent", "VortexResearcher-ConnectorProbe/2.0")

                if (profile.vaultSecretKeyRef.isNotBlank()) {
                    val bearer = secretResolver(profile.vaultSecretKeyRef)?.trim().orEmpty()
                    if (bearer.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $bearer")
                    }
                }
                profile.customHeaderVaultRefs.forEach { (headerName, vaultRef) ->
                    val cleanHeader = headerName.trim()
                    val cleanRef = vaultRef.trim()
                    if (cleanHeader.isNotBlank() && cleanRef.isNotBlank() &&
                        !cleanHeader.equals("Authorization", ignoreCase = true)
                    ) {
                        val headerVal = secretResolver(cleanRef)?.trim().orEmpty()
                        if (headerVal.isNotBlank()) {
                            setRequestProperty(cleanHeader, headerVal)
                        }
                    }
                }
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            classifyProbeResponse(code, location, elapsed, target)
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
            put("schema", "vortex.infra.shareable-manifest.v2")
            put("privacyNote", "PRIVATE_SOLO endpoints and all Encrypted Env Vault secrets are strictly excluded. Client-side lists are advisory; server-side enforcement is authoritative.")
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
                        put("serverEnforcesReadOnly", c.serverEnforcesReadOnly)
                        put("accessMode", if (c.isEffectivelyReadOnly) "READ_ONLY" else "READ_WRITE")
                        put("endpointUrl", if (target.isNotBlank()) target else "(configured per collaborator)")
                        put("fallbackAfterId", c.fallbackAfterId ?: JSONObject.NULL)
                        if (c.fallbackTrigger.isNotBlank()) {
                            put("fallbackTrigger", c.fallbackTrigger)
                        }
                        put("advisoryExpectedCapabilities", JSONArray(advisoryReadCapabilities(c)))
                        put("allowlistedTools", JSONArray(c.allowedToolNames.sorted()))
                    }
                )
            }
            put("connectors", arr)
        }
        return root.toString(2)
    }

    /**
     * Builds a privacy-hardened, budget-capped runtime context block for pipeline prompts:
     * 1. Temporal UTC <-> Local Timezone header (when enabled).
     * 2. Includes ONLY connectors that are:
     *    - enabled == true
     *    - status == ConnectorStatus.ONLINE
     *    - MCP tier and phoneDirectCallable == true
     *    - selected for [currentStageId] (when specified)
     *    - have at least one user-allowlisted tool from live `tools/list`
     * 3. NEVER includes PRIVATE_SOLO connector names unless `shareNameWithAi == true`
     *    (if private & `!shareNameWithAi`, uses an anonymized label like `Private Toolset #1`).
     * 4. Enforces [InfrastructureRegistryState.agentContextTokenBudget] (default 150 tokens).
     */
    fun buildAuthorizedAgentInfraContext(
        state: InfrastructureRegistryState,
        currentStageId: Int? = null
    ): String {
        val maxChars = (state.agentContextTokenBudget.coerceIn(40, 1000)) * APPROX_CHARS_PER_TOKEN
        val builder = StringBuilder()

        if (state.attachTimeContextToAgents) {
            builder.appendLine(TimeSyncContextEngine.buildCompactAgentTemporalHeader(state))
        }

        val eligible = state.connectors.filter { c ->
            c.enabled &&
                c.status == ConnectorStatus.ONLINE &&
                c.kind.isMcpTier &&
                c.phoneDirectCallable &&
                c.allowedToolNames.isNotEmpty() &&
                (currentStageId == null || currentStageId in c.selectedStages)
        }

        if (eligible.isNotEmpty()) {
            val header = "ONLINE MCP TOOLS (exact allowlist; secrets/URLs redacted):\n"
            if (builder.length + header.length < maxChars) {
                builder.append(header)
                var privateIdx = 0
                for (c in eligible) {
                    val displayLabel = if (c.visibility == ConnectorVisibility.PRIVATE_SOLO && !c.shareNameWithAi) {
                        privateIdx += 1
                        "Private Toolset #$privateIdx"
                    } else {
                        c.name
                    }
                    val roTag = if (c.serverEnforcesReadOnly) "SERVER-RO" else "CONFIRM-NON-RO"
                    val tools = c.allowedToolNames.sorted().joinToString(",")
                    val line = "- $displayLabel [$roTag]: $tools\n"
                    if (builder.length + line.length > maxChars) break
                    builder.append(line)
                }
            }
        }

        val result = builder.toString().trim()
        return if (result.length <= maxChars) {
            result
        } else {
            result.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"
        }
    }

    fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length + APPROX_CHARS_PER_TOKEN - 1) / APPROX_CHARS_PER_TOKEN
    }
}

/**
 * Built-in local UTC <-> Local Timezone translator that mirrors the Time & Date Sync MCP contract.
 * Works immediately offline and covers the agent temporal header without requiring a remote server.
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
            sourceLabel = if (remoteTimeSyncOnline) "Time & Date Sync MCP" else "Local Offline Time Engine"
        )
    }

    fun buildAgentTemporalHeader(state: InfrastructureRegistryState): String {
        val remoteOnline = state.connectors.any {
            it.id == InfraConnectorProfile.ID_TIME_SYNC && it.status == ConnectorStatus.ONLINE
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

    fun buildCompactAgentTemporalHeader(state: InfrastructureRegistryState): String {
        val remoteOnline = state.connectors.any {
            it.id == InfraConnectorProfile.ID_TIME_SYNC && it.status == ConnectorStatus.ONLINE
        }
        val env = currentEnvelope(
            timezoneId = state.localTimezoneId,
            remoteTimeSyncOnline = remoteOnline
        )
        return "TIME (${env.sourceLabel}): UTC=${env.utcIso} | Local=${env.localIso} (${env.timezoneId})"
    }
}
