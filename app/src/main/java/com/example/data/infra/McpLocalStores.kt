package com.example.data.infra

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class McpStoredResult(
    val refId: String,
    val timestampMillis: Long,
    val connectorId: String,
    val toolName: String,
    val rawContent: String,
    val byteSize: Int
)

data class McpAuditLogEntry(
    val timestampMillis: Long,
    val connectorId: String,
    val connectorName: String,
    val toolName: String,
    val argumentsSha256: String,
    val resultSizeBytes: Int,
    val status: String, // SUCCESS, ERROR, DENIED, AUTH_REQUIRED
    val resultRefId: String = "",
    val readOnlyClassification: String = "NON_READ" // READ_ONLY_CONFIRMED, NON_READ
)

/**
 * Local-only store for full raw MCP tool results keyed by reference ID.
 * Excluded from Android cloud backups and device transfers via backup_rules.xml / data_extraction_rules.xml.
 */
class McpFullResultStore(private val baseDir: File) {

    private val storeFile: File
        get() = File(baseDir, "vortex_mcp_results.json")

    @Synchronized
    fun saveResult(
        connectorId: String,
        toolName: String,
        rawContent: String
    ): McpStoredResult {
        val refId = "mcpref_${UUID.randomUUID().toString().take(12)}"
        val entry = McpStoredResult(
            refId = refId,
            timestampMillis = System.currentTimeMillis(),
            connectorId = connectorId,
            toolName = toolName,
            rawContent = rawContent,
            byteSize = rawContent.toByteArray(Charsets.UTF_8).size
        )
        val existing = loadAllInternal().toMutableList()
        existing.add(0, entry)
        val trimmed = existing.take(MAX_STORED_RESULTS)
        writeAllInternal(trimmed)
        return entry
    }

    @Synchronized
    fun getByRefId(refId: String): McpStoredResult? =
        loadAllInternal().firstOrNull { it.refId == refId }

    @Synchronized
    fun listRecent(): List<McpStoredResult> = loadAllInternal()

    @Synchronized
    fun clear() {
        if (storeFile.exists()) storeFile.delete()
    }

    private fun loadAllInternal(): List<McpStoredResult> {
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(storeFile.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        McpStoredResult(
                            refId = o.optString("refId"),
                            timestampMillis = o.optLong("timestampMillis", 0L),
                            connectorId = o.optString("connectorId"),
                            toolName = o.optString("toolName"),
                            rawContent = o.optString("rawContent"),
                            byteSize = o.optInt("byteSize", 0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAllInternal(entries: List<McpStoredResult>) {
        if (!baseDir.exists()) baseDir.mkdirs()
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("refId", e.refId)
                    put("timestampMillis", e.timestampMillis)
                    put("connectorId", e.connectorId)
                    put("toolName", e.toolName)
                    put("rawContent", e.rawContent)
                    put("byteSize", e.byteSize)
                }
            )
        }
        val tmp = File(baseDir, "vortex_mcp_results.json.tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(storeFile)) {
            storeFile.writeText(arr.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    companion object {
        private const val MAX_STORED_RESULTS = 40
    }
}

/**
 * Local-only audit log for every MCP tool invocation.
 * Stores timestamp, connector, tool name, SHA-256 hash of arguments JSON, result size, and status.
 * NEVER stores raw authorization headers, Bearer tokens, or Vault secrets.
 */
class McpAuditLogStore(private val baseDir: File) {

    private val logFile: File
        get() = File(baseDir, "vortex_mcp_audit_log.json")

    @Synchronized
    fun append(entry: McpAuditLogEntry): List<McpAuditLogEntry> {
        val current = loadAllInternal().toMutableList()
        current.add(0, entry)
        val trimmed = current.take(MAX_AUDIT_ENTRIES)
        writeAllInternal(trimmed)
        return trimmed
    }

    @Synchronized
    fun loadAll(): List<McpAuditLogEntry> = loadAllInternal()

    @Synchronized
    fun clear() {
        if (logFile.exists()) logFile.delete()
    }

    private fun loadAllInternal(): List<McpAuditLogEntry> {
        if (!logFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(logFile.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        McpAuditLogEntry(
                            timestampMillis = o.optLong("timestampMillis", 0L),
                            connectorId = o.optString("connectorId"),
                            connectorName = o.optString("connectorName"),
                            toolName = o.optString("toolName"),
                            argumentsSha256 = o.optString("argumentsSha256"),
                            resultSizeBytes = o.optInt("resultSizeBytes", 0),
                            status = o.optString("status"),
                            resultRefId = o.optString("resultRefId"),
                            readOnlyClassification = o.optString("readOnlyClassification", "NON_READ")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAllInternal(entries: List<McpAuditLogEntry>) {
        if (!baseDir.exists()) baseDir.mkdirs()
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("timestampMillis", e.timestampMillis)
                    put("connectorId", e.connectorId)
                    put("connectorName", e.connectorName)
                    put("toolName", e.toolName)
                    put("argumentsSha256", e.argumentsSha256)
                    put("resultSizeBytes", e.resultSizeBytes)
                    put("status", e.status)
                    put("resultRefId", e.resultRefId)
                    put("readOnlyClassification", e.readOnlyClassification)
                }
            )
        }
        val tmp = File(baseDir, "vortex_mcp_audit_log.json.tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(logFile)) {
            logFile.writeText(arr.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    companion object {
        private const val MAX_AUDIT_ENTRIES = 200

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
