package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.ExternalRouterApi
import com.example.data.ai.VortexModelRouter
import com.example.data.docs.VortexNextStepsDocProvider
import com.example.data.infra.ConnectorStatus
import com.example.data.infra.ConnectorVisibility
import com.example.data.infra.InfraConnectorProfile
import com.example.data.infra.InfrastructureRegistryCodec
import com.example.data.infra.InfrastructureRegistryEngine
import com.example.data.infra.McpAuditLogStore
import com.example.data.infra.McpCallPolicyStatus
import com.example.data.infra.McpDiscoveredTool
import com.example.data.infra.McpFullResultStore
import com.example.data.infra.McpJsonRpcClient
import com.example.data.local.LocalPreferencesStore
import com.example.data.local.cache.VortexLocalDatabase
import com.example.data.local.cache.toCacheEntity
import com.example.data.model.ExternalRouterType
import com.example.data.model.ResearchProject
import com.example.data.model.RoutingPreferences
import com.example.data.security.EncryptedEnvVaultStore
import com.example.data.security.EnvAutoFillTarget
import com.example.data.security.EnvSecretCategory
import com.example.data.security.SecretVaultMigration
import com.example.data.security.VaultCipher
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VortexHardeningAndMcpTest {

    private class JvmAesGcmVaultCipher(private val failDecrypt: Boolean = false) : VaultCipher {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        private val rng = SecureRandom()

        override fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
            val iv = ByteArray(12).also { rng.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plaintext)
            return iv to encrypted
        }

        override fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
            if (failDecrypt) throw IllegalStateException("Simulated decrypt failure")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        }
    }

    // -------------------------------------------------------------------------
    // Correction 1: Secrets at rest, verified DataStore -> Vault migration
    // -------------------------------------------------------------------------

    @Test
    fun `secret migration moves legacy DataStore keys to encrypted vault verifies read-back and deletes plaintext`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmpDir = Files.createTempDirectory("vortex_mig_ok").toFile()
        val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dsFile = File(tmpDir, "test_prefs.preferences_pb")
            val ds = PreferenceDataStoreFactory.create(scope = dsScope) { dsFile }
            val prefs = LocalPreferencesStore(context, ds)
            val vaultFile = File(tmpDir, "vault.enc")
            val vault = EncryptedEnvVaultStore(context, JvmAesGcmVaultCipher(), vaultFile)

            prefs.seedLegacyPlaintextSecretsForTest(
                externalApiKey = "test-ext-token-value-123",
                githubPat = "test-gh-token-value-456"
            )

            val report = SecretVaultMigration.migrateIfNeeded(prefs, vault)
            assertTrue(report.migratedExternalApiKey)
            assertTrue(report.migratedGithubPat)
            assertTrue(report.deletedLegacyExternalKey)
            assertTrue(report.deletedLegacyGithubPat)
            assertNull(report.errorMessage)

            // Verify vault reads back decrypted values and disk file does NOT contain plaintext
            assertEquals("test-ext-token-value-123", vault.getSecretValue(EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY))
            assertEquals("test-gh-token-value-456", vault.getSecretValue(EncryptedEnvVaultStore.KEY_GITHUB_PAT))
            val rawDisk = vaultFile.readText(Charsets.ISO_8859_1)
            assertFalse(rawDisk.contains("test-ext-token-value-123"))
            assertFalse(rawDisk.contains("test-gh-token-value-456"))

            // Verify legacy DataStore keys are deleted
            val legacyAfter = prefs.readLegacyPlaintextSecrets()
            assertFalse(legacyAfter.hasExternalApiKeyEntry)
            assertFalse(legacyAfter.hasGithubPatEntry)

            // Verify idempotency on second run
            val second = SecretVaultMigration.migrateIfNeeded(prefs, vault)
            assertFalse(second.anyMigrated)
            assertNull(second.errorMessage)
        } finally {
            dsScope.cancel()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `secret migration retains legacy DataStore keys if vault read-back verification fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmpDir = Files.createTempDirectory("vortex_mig_fail").toFile()
        val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = dsScope) { File(tmpDir, "fail_prefs.preferences_pb") }
            val prefs = LocalPreferencesStore(context, ds)
            val vault = EncryptedEnvVaultStore(context, JvmAesGcmVaultCipher(failDecrypt = true), File(tmpDir, "vault.enc"))

            prefs.seedLegacyPlaintextSecretsForTest(
                externalApiKey = "keep-me-safe-ext",
                githubPat = "keep-me-safe-gh"
            )

            val report = SecretVaultMigration.migrateIfNeeded(prefs, vault)
            assertFalse(report.deletedLegacyExternalKey)
            assertFalse(report.deletedLegacyGithubPat)
            assertNotNull(report.errorMessage)

            val legacyAfter = prefs.readLegacyPlaintextSecrets()
            assertTrue(legacyAfter.hasExternalApiKeyEntry)
            assertTrue(legacyAfter.hasGithubPatEntry)
            assertEquals("keep-me-safe-ext", legacyAfter.externalApiKey)
        } finally {
            dsScope.cancel()
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `secret migration preserves existing different vault value and deletes legacy DataStore entry`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmpDir = Files.createTempDirectory("vortex_mig_preserve").toFile()
        val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = dsScope) { File(tmpDir, "preserve_prefs.preferences_pb") }
            val prefs = LocalPreferencesStore(context, ds)
            val vault = EncryptedEnvVaultStore(context, JvmAesGcmVaultCipher(), File(tmpDir, "vault.enc"))

            // Pre-populate vault with newer value
            vault.upsertManagedCredential(
                key = EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY,
                value = "newer-vault-ext-value",
                category = EnvSecretCategory.API_KEY,
                target = EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY
            )
            // Seed legacy DataStore with an older different value
            prefs.seedLegacyPlaintextSecretsForTest(
                externalApiKey = "older-legacy-ext-value",
                githubPat = ""
            )

            val report = SecretVaultMigration.migrateIfNeeded(prefs, vault)
            assertTrue(report.migratedExternalApiKey)
            assertTrue(report.deletedLegacyExternalKey)
            assertEquals("newer-vault-ext-value", vault.getSecretValue(EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY))
            assertFalse(prefs.readLegacyPlaintextSecrets().hasExternalApiKeyEntry)
        } finally {
            dsScope.cancel()
            tmpDir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // Correction 2 & 3: Advisory display, serverEnforcesReadOnly, accurate templates
    // -------------------------------------------------------------------------

    @Test
    fun `templates enforce accurate roles standby defaults direct HTTPS and security invariants`() {
        val state = InfrastructureRegistryEngine.defaultState()
        val byId = state.connectors.associateBy { it.id }

        // All default to STANDBY and direct HTTPS primary (useGatewayProxy = false)
        state.connectors.forEach { c ->
            assertEquals("Expected STANDBY on ${c.id}", ConnectorStatus.STANDBY, c.status)
            assertFalse("Expected direct HTTPS primary on ${c.id}", c.useGatewayProxy)
        }

        // World Mechanic ("Stage"): disabled, no PUBLIC_TEAM, phoneDirectCallable = false
        val wm = byId[InfraConnectorProfile.ID_WORLD_MECHANIC]!!
        assertFalse(wm.enabled)
        assertFalse(wm.phoneDirectCallable)
        assertFalse(ConnectorVisibility.PUBLIC_TEAM in wm.allowedVisibilities())
        assertEquals("expected; confirm with tools/list when online", wm.metadata["tools_status"])
        assertTrue(wm.description.contains("confirm with tools/list when online", ignoreCase = true))

        // Time Sync: get_current_time
        val time = byId[InfraConnectorProfile.ID_TIME_SYNC]!!
        assertTrue("get_current_time" in time.capabilities)

        // Memgraph: 7 real tools + sandbox preset sets serverEnforcesReadOnly
        val mg = byId[InfraConnectorProfile.ID_MEMGRAPH]!!
        assertEquals(
            listOf(
                "list_databases",
                "use_database",
                "run_cypher_query",
                "search_schema",
                "get_node_schema",
                "get_relationship_schema",
                "get_enum_schema"
            ),
            mg.capabilities
        )
        // Demonstrate why advisory substring display is NOT a security control
        assertFalse("run_cypher_query" in InfrastructureRegistryEngine.advisoryMutatingCapabilities(mg.copy(readOnlyEnforced = true)))
        val sandboxedMg = InfrastructureRegistryEngine.applyMemgraphSandboxPreset(mg)
        assertTrue(sandboxedMg.serverEnforcesReadOnly)
        assertEquals(ConnectorVisibility.SANDBOXED_READ_ONLY, sandboxedMg.visibility)

        // VPS-Access: disabled, strictly PRIVATE_SOLO, confirm every call
        val vps = byId[InfraConnectorProfile.ID_VPS_ACCESS]!!
        assertFalse(vps.enabled)
        assertEquals(listOf(ConnectorVisibility.PRIVATE_SOLO), vps.allowedVisibilities())
        assertTrue(vps.requirePerCallConfirmation)

        // Browser Use: server-side isolated sandbox only + fallback after Playwright
        val bu = byId[InfraConnectorProfile.ID_BROWSER_USE]!!
        assertFalse(bu.phoneDirectCallable)
        assertEquals(InfraConnectorProfile.ID_PLAYWRIGHT, bu.fallbackAfterId)
        assertTrue(bu.fallbackTrigger.isNotBlank())

        // Round-trip codec preserves serverEnforcesReadOnly & new fields
        val encoded = InfrastructureRegistryCodec.encode(state.copy(connectors = listOf(sandboxedMg, vps)))
        val decoded = InfrastructureRegistryCodec.decode(encoded)!!
        assertTrue(decoded.connectors.first { it.id == InfraConnectorProfile.ID_MEMGRAPH }.serverEnforcesReadOnly)
        assertTrue(decoded.connectors.first { it.id == InfraConnectorProfile.ID_VPS_ACCESS }.requirePerCallConfirmation)
    }

    // -------------------------------------------------------------------------
    // Correction 4: Universal HTTPS validation + AUTH_REQUIRED probe
    // -------------------------------------------------------------------------

    @Test
    fun `universal HTTPS URL validator accepts Tailscale Cloudflare Neon S3 and blocks SSRF metadata`() {
        assertTrue(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://host.tailnet.ts.net:8443/mcp").first)
        assertTrue(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://sandbox.example.com/mcp").first)
        assertTrue(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://db.neon.example.com/sql").first)
        assertTrue(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://s3.example.com/bucket").first)
        assertTrue(InfrastructureRegistryEngine.validateRemoteEndpointUrl("http://127.0.0.1:8080/mcp").first)

        assertFalse(InfrastructureRegistryEngine.validateRemoteEndpointUrl("http://sandbox.example.com/mcp").first)
        assertFalse(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://169.254.169.254/latest/meta-data").first)
        assertFalse(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://metadata.google.internal/computeMetadata").first)
        assertFalse(InfrastructureRegistryEngine.validateRemoteEndpointUrl("https://100.100.100.200/latest").first)
    }

    @Test
    fun `probe sends Bearer and custom header vault refs and classifies 401 and 302 as AUTH_REQUIRED without following redirects`() = runBlocking {
        var seenAuthHeader: String? = null
        var seenCustomHeader: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { ex ->
            seenAuthHeader = ex.requestHeaders.getFirst("Authorization")
            seenCustomHeader = ex.requestHeaders.getFirst("X-Sandbox-Key")
            val body = """{"status":"ok"}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/cf-login") { ex ->
            ex.responseHeaders.add("Location", "https://login.example.com/cdn-cgi/access/login")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/unauth") { ex ->
            ex.sendResponseHeaders(401, -1)
            ex.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val secrets = mapOf("MCP_AUTH_TOKEN" to "secret-bearer-xyz", "CUSTOM_REF" to "custom-val-99")
            val profile = InfraConnectorProfile(
                id = "test_probe",
                name = "Test Probe",
                providerLabel = "Test",
                kind = com.example.data.infra.ConnectorKind.MCP_SERVER,
                templateKey = "test",
                description = "Test",
                endpointUrl = "$base/ok",
                vaultSecretKeyRef = "MCP_AUTH_TOKEN",
                customHeaderVaultRefs = mapOf("X-Sandbox-Key" to "CUSTOM_REF")
            )

            val okRes = InfrastructureRegistryEngine.probeConnector(profile, "") { secrets[it] }
            assertEquals(ConnectorStatus.ONLINE, okRes.status)
            assertEquals("Bearer secret-bearer-xyz", seenAuthHeader)
            assertEquals("custom-val-99", seenCustomHeader)

            val redirectRes = InfrastructureRegistryEngine.probeConnector(profile.copy(endpointUrl = "$base/cf-login"), "") { secrets[it] }
            assertEquals(ConnectorStatus.AUTH_REQUIRED, redirectRes.status)

            val unauthRes = InfrastructureRegistryEngine.probeConnector(profile.copy(endpointUrl = "$base/unauth"), "") { secrets[it] }
            assertEquals(ConnectorStatus.AUTH_REQUIRED, unauthRes.status)
        } finally {
            server.stop(0)
        }
    }

    // -------------------------------------------------------------------------
    // Correction 5: Stage-scoped, budgeted, private-name-protected context
    // -------------------------------------------------------------------------

    @Test
    fun `agent infra context excludes offline connectors respects stage filter anonymizes PRIVATE_SOLO and enforces token budget`() {
        val defaultState = InfrastructureRegistryEngine.defaultState()
        // Default standby connectors emit ZERO MCP tools (only optional UTC<->Local clock header)
        val standbyWithClock = InfrastructureRegistryEngine.buildAuthorizedAgentInfraContext(defaultState, currentStageId = 1)
        assertFalse(standbyWithClock.contains("ONLINE MCP TOOLS"))
        assertEquals(
            "",
            InfrastructureRegistryEngine.buildAuthorizedAgentInfraContext(
                defaultState.copy(attachTimeContextToAgents = false),
                currentStageId = 1
            )
        )

        val onlinePrivate = InfraConnectorProfile(
            id = "mcp_private_demo",
            name = "SuperSecretOwnerGraph",
            providerLabel = "MCP",
            kind = com.example.data.infra.ConnectorKind.MCP_SERVER,
            templateKey = "custom",
            description = "Private",
            enabled = true,
            visibility = ConnectorVisibility.PRIVATE_SOLO,
            shareNameWithAi = false,
            phoneDirectCallable = true,
            status = ConnectorStatus.ONLINE,
            selectedStages = setOf(4),
            discoveredTools = listOf(McpDiscoveredTool("get_schema", serverReadOnlyHint = true)),
            allowedToolNames = setOf("get_schema")
        )
        val state = defaultState.copy(
            attachTimeContextToAgents = false,
            agentContextTokenBudget = 80,
            connectors = listOf(onlinePrivate)
        )

        // Stage 1 should NOT see Stage-4-only connector
        assertEquals("", InfrastructureRegistryEngine.buildAuthorizedAgentInfraContext(state, currentStageId = 1))

        // Stage 4 sees it, but private name is anonymized when shareNameWithAi = false
        val stage4Hidden = InfrastructureRegistryEngine.buildAuthorizedAgentInfraContext(state, currentStageId = 4)
        assertFalse(stage4Hidden.contains("SuperSecretOwnerGraph"))
        assertTrue(stage4Hidden.contains("get_schema"))
        assertTrue(stage4Hidden.length <= 80 * 4 + 40)

        // When shareNameWithAi = true, name is included
        val stage4Named = InfrastructureRegistryEngine.buildAuthorizedAgentInfraContext(
            state.copy(connectors = listOf(onlinePrivate.copy(shareNameWithAi = true))),
            currentStageId = 4
        )
        assertTrue(stage4Named.contains("SuperSecretOwnerGraph"))
    }

    // -------------------------------------------------------------------------
    // Correction 6: Self-Hosted Text Router (/query & /health)
    // -------------------------------------------------------------------------

    @Test
    fun `self hosted text router enforces 4000-char cap sends exact prompt JSON parses lane and latency and checks health`() = runBlocking {
        var lastPayload = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/query") { ex ->
            lastPayload = ex.requestBody.bufferedReader().readText()
            val auth = ex.requestHeaders.getFirst("Authorization")
            if (auth != "Bearer valid-router-token") {
                val b = """{"error":"unauthorized"}""".toByteArray()
                ex.sendResponseHeaders(401, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            } else {
                val b = """{"response":"Synthesized answer","lane":"local-70b","model":"llama-3","latency_s":1.42}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
        }
        server.createContext("/health") { ex ->
            val b = """{"status":"healthy"}""".toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val api = ExternalRouterApi()

            // Health check
            val health = api.selfHostedHealth(base)
            assertTrue(health.reachable)
            assertEquals(200, health.httpCode)

            // 4,001-char prompt blocked client-side
            val tooLong = api.selfHostedQuery(base, "valid-router-token", "a".repeat(4001))
            assertTrue(tooLong.isFailure)

            // Grounding OFF by default produces raw query only
            val composedOff = ExternalRouterApi.composeSelfHostedPrompt(
                userPrompt = "Explain PostGIS",
                approvedSummary = "",
                groundingEvidence = ""
            )
            assertEquals("Explain PostGIS", composedOff)

            // Valid call checks exact {"prompt": ...} body with NO system key
            val ok = api.selfHostedQuery(base, "valid-router-token", "Explain PostGIS")
            assertTrue(ok.isSuccess)
            val parsed = ok.getOrThrow()
            assertEquals("Synthesized answer", parsed.response)
            assertEquals("local-70b", parsed.lane)
            assertEquals(1.42, parsed.latencySeconds!!, 0.001)
            val sentJson = JSONObject(lastPayload)
            assertEquals(1, sentJson.length())
            assertEquals("Explain PostGIS", sentJson.getString("prompt"))

            // 401 handled explicitly
            val badAuth = api.selfHostedQuery(base, "wrong-token", "Hello")
            assertTrue(badAuth.isFailure)
            assertTrue(badAuth.exceptionOrNull()?.message?.contains("401") == true)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `executeSelfHostedTextRoute fails with clear user-facing error on prompt over 4000 chars without silently cutting or sending`() = runBlocking {
        var httpRequestReceived = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/query") { ex ->
            httpRequestReceived = true
            val b = """{"response":"should never be reached","lane":"default","model":"m","latency_s":0.1}""".toByteArray()
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val router = VortexModelRouter(ExternalRouterApi())
            val prefs = RoutingPreferences(
                externalEnabled = true,
                externalRouterType = ExternalRouterType.SELF_HOSTED_TEXT,
                externalBaseUrl = base,
                externalApiKey = "valid-router-token",
                selfHostedIncludeGrounding = false,
                selfHostedApprovedSummary = "S".repeat(120)
            )
            val oversizedPrompt = "P".repeat(3950)
            var thrown: Throwable? = null
            try {
                router.executeSelfHostedTextRoute(
                    userPrompt = oversizedPrompt,
                    combinedGeminiFallbackPrompt = "fallback",
                    decision = VortexModelRouter.RouteDecision("self-hosted", true, "test"),
                    prefs = prefs
                )
            } catch (t: Throwable) {
                thrown = t
            }
            assertNotNull("Expected IllegalArgumentException for oversized composed prompt", thrown)
            assertTrue(thrown is IllegalArgumentException)
            assertTrue(thrown!!.message!!.contains("4000"))
            assertFalse("Oversized prompt must NEVER be silently truncated and sent to /query", httpRequestReceived)
        } finally {
            server.stop(0)
        }
    }

    // -------------------------------------------------------------------------
    // Correction 8: Streamable HTTP JSON-RPC 2.0 MCP Client + Dual-Condition RO
    // -------------------------------------------------------------------------

    @Test
    fun `mcp client handles initialize notifications-initialized session tools list dual-condition read-only untrusted wrapping and sha256 audit`() = runBlocking {
        val tmpDir = Files.createTempDirectory("vortex_mcp_test").toFile()
        var initializedNotified = false
        var notificationHadId = true
        var receivedSessionOnNotify: String? = null
        var receivedSessionOnList: String? = null
        var receivedSessionOnCall: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { ex ->
            val reqBody = ex.requestBody.bufferedReader().readText()
            val reqJson = JSONObject(reqBody)
            val method = reqJson.getString("method")
            val sessionHeader = ex.requestHeaders.getFirst("Mcp-Session-Id")
            when (method) {
                "initialize" -> {
                    val id = reqJson.getInt("id")
                    ex.responseHeaders.add("Mcp-Session-Id", "sess-abc-123")
                    val respStr = """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2025-03-26","serverInfo":{"name":"test-mcp"}}}"""
                    val bytes = respStr.toByteArray()
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.sendResponseHeaders(200, bytes.size.toLong())
                    ex.responseBody.use { it.write(bytes) }
                }
                "notifications/initialized" -> {
                    notificationHadId = reqJson.has("id")
                    receivedSessionOnNotify = sessionHeader
                    if (!notificationHadId && sessionHeader == "sess-abc-123") {
                        initializedNotified = true
                        ex.sendResponseHeaders(202, -1)
                    } else {
                        val err = """{"error":"notifications/initialized must omit id and match Mcp-Session-Id"}""".toByteArray()
                        ex.sendResponseHeaders(400, err.size.toLong())
                        ex.responseBody.use { it.write(err) }
                    }
                    ex.close()
                }
                "tools/list" -> {
                    if (!initializedNotified) {
                        val err = """{"jsonrpc":"2.0","id":${reqJson.optInt("id")},"error":{"code":-32002,"message":"Rejected: notifications/initialized required before tools/list"}}""".toByteArray()
                        ex.responseHeaders.add("Content-Type", "application/json")
                        ex.sendResponseHeaders(400, err.size.toLong())
                        ex.responseBody.use { it.write(err) }
                    } else {
                        val id = reqJson.getInt("id")
                        receivedSessionOnList = sessionHeader
                        val respStr = """{"jsonrpc":"2.0","id":$id,"result":{"tools":[
                            {"name":"get_schema","description":"Inspect graph schema","annotations":{"readOnlyHint":true}},
                            {"name":"run_cypher_query","description":"Run Cypher","annotations":{"readOnlyHint":false}},
                            {"name":"unannotated_tool","description":"No hint"}
                        ]}}"""
                        val bytes = respStr.toByteArray()
                        ex.responseHeaders.add("Content-Type", "application/json")
                        ex.sendResponseHeaders(200, bytes.size.toLong())
                        ex.responseBody.use { it.write(bytes) }
                    }
                }
                "tools/call" -> {
                    if (!initializedNotified) {
                        val err = """{"jsonrpc":"2.0","id":${reqJson.optInt("id")},"error":{"code":-32002,"message":"Rejected: notifications/initialized required before tools/call"}}""".toByteArray()
                        ex.responseHeaders.add("Content-Type", "application/json")
                        ex.sendResponseHeaders(400, err.size.toLong())
                        ex.responseBody.use { it.write(err) }
                    } else {
                        val id = reqJson.getInt("id")
                        receivedSessionOnCall = sessionHeader
                        val longText = "ROW_DATA_".repeat(80)
                        val respStr = """{"jsonrpc":"2.0","id":$id,"result":{"content":[{"type":"text","text":"$longText"}]}}"""
                        val bytes = respStr.toByteArray()
                        ex.responseHeaders.add("Content-Type", "application/json")
                        ex.sendResponseHeaders(200, bytes.size.toLong())
                        ex.responseBody.use { it.write(bytes) }
                    }
                }
                else -> {
                    val id = reqJson.optInt("id")
                    val respStr = """{"jsonrpc":"2.0","id":$id,"error":{"code":-32601,"message":"Method not found"}}"""
                    val bytes = respStr.toByteArray()
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.sendResponseHeaders(200, bytes.size.toLong())
                    ex.responseBody.use { it.write(bytes) }
                }
            }
        }
        server.start()
        try {
            val resultStore = McpFullResultStore(tmpDir)
            val auditStore = McpAuditLogStore(tmpDir)
            val client = McpJsonRpcClient(resultStore, auditStore)
            val baseProfile = InfraConnectorProfile(
                id = InfraConnectorProfile.ID_MEMGRAPH,
                name = "Memgraph MCP",
                providerLabel = "Memgraph",
                kind = com.example.data.infra.ConnectorKind.MCP_SERVER,
                templateKey = "memgraph",
                description = "Test",
                enabled = true,
                endpointUrl = "http://127.0.0.1:${server.address.port}/mcp",
                vaultSecretKeyRef = "MCP_AUTH_TOKEN",
                capabilities = listOf("get_schema", "expected_missing_tool")
            )
            val secretResolver: (String) -> String? = { if (it == "MCP_AUTH_TOKEN") "ultra-secret-bearer" else null }

            // 0. Calling tools/list BEFORE initialize + notifications/initialized is rejected by server
            val prematureList = client.listTools(baseProfile, "", secretResolver)
            assertFalse(prematureList.success)
            assertTrue(prematureList.errorMessage?.contains("notifications/initialized") == true)

            // 1. initialize captures Mcp-Session-Id AND sends notifications/initialized (no id) with same session ID
            val initRes = client.initialize(baseProfile, "", secretResolver)
            assertTrue(initRes.success)
            assertEquals("sess-abc-123", initRes.sessionId)
            assertTrue("Expected notifications/initialized to be sent after initialize", initializedNotified)
            assertFalse("notifications/initialized must NOT include a JSON-RPC id field", notificationHadId)
            assertEquals("sess-abc-123", receivedSessionOnNotify)

            // 2. tools/list propagates session ID and parses annotations.readOnlyHint
            val listRes = client.listTools(baseProfile, "", secretResolver)
            assertTrue(listRes.success)
            assertEquals("sess-abc-123", receivedSessionOnList)
            assertEquals(3, listRes.tools.size)
            assertEquals(true, listRes.tools[0].serverReadOnlyHint)
            assertEquals(false, listRes.tools[1].serverReadOnlyHint)
            assertNull(listRes.tools[2].serverReadOnlyHint)

            // 3. Mismatch detection between template expected list and live tools/list
            val syncedProfile = baseProfile.copy(discoveredTools = listRes.tools)
            val mismatch = syncedProfile.toolListMismatchSummary()
            assertNotNull(mismatch)
            assertTrue("expected_missing_tool" in mismatch!!.missingFromServer)
            assertTrue("run_cypher_query" in mismatch.unexpectedOnServer)

            // 4. Deny-by-default exact-name allowlist
            assertEquals(McpCallPolicyStatus.DENIED, client.evaluateToolCallPolicy(syncedProfile, "get_schema").status)

            // 5. Dual-condition read-only classification
            val allowlistedOnly = syncedProfile.copy(allowedToolNames = setOf("get_schema", "run_cypher_query", "unannotated_tool"))
            // Missing user RO toggle -> non-read (confirm every call)
            val roHintOnly = client.evaluateToolCallPolicy(allowlistedOnly, "get_schema")
            assertFalse(roHintOnly.isReadOnly)
            assertEquals(McpCallPolicyStatus.NEEDS_EVERY_CALL_CONFIRMATION, roHintOnly.status)

            // User RO toggle WITHOUT server readOnlyHint=true -> STILL non-read
            val userRoOnly = allowlistedOnly.copy(userMarkedReadOnlyTools = setOf("run_cypher_query", "unannotated_tool"))
            assertFalse(client.evaluateToolCallPolicy(userRoOnly, "run_cypher_query").isReadOnly)
            assertFalse(client.evaluateToolCallPolicy(userRoOnly, "unannotated_tool").isReadOnly)

            // BOTH server readOnlyHint=true AND user RO toggle -> dual-verified read-only (1x/session)
            val dualRoProfile = allowlistedOnly.copy(userMarkedReadOnlyTools = setOf("get_schema"))
            val dualPolicy = client.evaluateToolCallPolicy(dualRoProfile, "get_schema")
            assertTrue(dualPolicy.isReadOnly)
            assertEquals(McpCallPolicyStatus.NEEDS_SESSION_CONFIRMATION, dualPolicy.status)

            // VPS-Access always confirms every call even if both conditions were set
            val vpsProfile = dualRoProfile.copy(
                id = InfraConnectorProfile.ID_VPS_ACCESS,
                requirePerCallConfirmation = true
            )
            assertFalse(client.evaluateToolCallPolicy(vpsProfile, "get_schema").isReadOnly)
            assertEquals(McpCallPolicyStatus.NEEDS_EVERY_CALL_CONFIRMATION, client.evaluateToolCallPolicy(vpsProfile, "get_schema").status)

            // 6. Execute tools/call -> untrusted wrapper + charCap truncation + local full result + SHA-256 audit log (no secret)
            val callRes = client.callTool(
                profile = dualRoProfile,
                gatewayBaseUrl = "",
                toolName = "get_schema",
                argumentsJson = """{"limit": 10}""",
                charCap = 250,
                confirmedByUser = true,
                secretResolver = secretResolver
            )
            assertTrue(callRes.success)
            assertEquals("sess-abc-123", receivedSessionOnCall)
            assertTrue(callRes.truncated)
            assertTrue(callRes.wrappedModelPayload.startsWith("[UNTRUSTED_MCP_TOOL_DATA"))
            assertTrue(callRes.wrappedModelPayload.contains("DATA_ONLY_NEVER_INSTRUCTIONS"))
            assertTrue(callRes.wrappedModelPayload.contains(callRes.resultRefId))

            val storedFull = resultStore.getByRefId(callRes.resultRefId)
            assertNotNull(storedFull)
            assertTrue(storedFull!!.rawContent.length > 500)

            // Subsequent call to same dual-verified RO tool is auto-approved for session
            assertEquals(
                McpCallPolicyStatus.AUTO_APPROVED_FOR_SESSION,
                client.evaluateToolCallPolicy(dualRoProfile, "get_schema").status
            )

            // Audit log records SHA-256 hash and ZERO Bearer secrets on disk
            val audits = auditStore.loadAll()
            assertEquals(1, audits.size)
            assertEquals("SUCCESS", audits[0].status)
            assertEquals(64, audits[0].argumentsSha256.length)
            val rawAuditDisk = File(tmpDir, "vortex_mcp_audit_log.json").readText()
            assertFalse(rawAuditDisk.contains("ultra-secret-bearer"))
        } finally {
            server.stop(0)
            tmpDir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // Next Steps Doc Parity & JVM Release Smoke Checks (Room)
    // -------------------------------------------------------------------------

    @Test
    fun `next steps root markdown and in-app provider stay byte-for-byte identical`() {
        val candidates = listOf(
            File("Vortex Researcher- Next Steps.md"),
            File("../Vortex Researcher- Next Steps.md")
        )
        val rootFile = candidates.firstOrNull { it.exists() }
        assertNotNull("Could not locate root Vortex Researcher- Next Steps.md", rootFile)
        val diskText = rootFile!!.readText(Charsets.UTF_8).replace("\r\n", "\n").trim()
        val providerText = VortexNextStepsDocProvider.fullMarkdown().replace("\r\n", "\n").trim()
        assertEquals(diskText, providerText)
        assertTrue(providerText.contains("restrict the Firebase API key in Google Cloud Console"))
    }

    @Test
    fun `room cache database opens and round-trips a cached project in JVM smoke check`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VortexLocalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val entity = ResearchProject(
                id = "smoke_proj_1",
                ownerId = "smoke_user",
                title = "Smoke Project",
                rootFolderName = "smoke-project"
            ).toCacheEntity(cachedForUid = "smoke_user")
            db.projectCacheDao().upsertProject(entity)
            val fetched = db.projectCacheDao().getProjectById("smoke_proj_1")
            assertNotNull(fetched)
            assertEquals("Smoke Project", fetched!!.title)
        } finally {
            db.close()
        }
    }
}
