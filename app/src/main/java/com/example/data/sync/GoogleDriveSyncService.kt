package com.example.data.sync

import com.example.data.model.ProjectFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DrivePushReport(
    val rootFolderId: String,
    val driveWebUrl: String,
    val ensuredFoldersCount: Int,
    val uploadedCount: Int,
    val updatedCount: Int,
    val skippedUnchangedCount: Int,
    val workspaceSha256: String
)

data class DrivePullReport(
    val rootFolderId: String,
    val pulledFiles: List<ProjectFile>,
    val workspaceSha256: String
)

enum class DriveSyncDirection {
    IDLE,
    PUSH,
    PULL
}

enum class DriveSyncPhase {
    NOT_LINKED,
    IN_SYNC,
    LOCAL_AHEAD,
    SYNCING,
    OFFLINE,
    ERROR
}

data class DriveSyncProgressUpdate(
    val direction: DriveSyncDirection,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val stageLabel: String = "",
    val progressFraction: Float? = null
)

data class DriveHeaderSyncState(
    val phase: DriveSyncPhase = DriveSyncPhase.NOT_LINKED,
    val direction: DriveSyncDirection = DriveSyncDirection.IDLE,
    val progressFraction: Float? = null,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val currentStageLabel: String = "",
    val lastSyncSummary: String = "",
    val folderId: String = "",
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = phase == DriveSyncPhase.SYNCING
}

class GoogleDriveSyncService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val folderMime = "application/vnd.google-apps.folder"
    private val filesEndpoint = "https://www.googleapis.com/drive/v3/files"
    private val uploadEndpoint = "https://www.googleapis.com/upload/drive/v3/files"

    suspend fun pushWorkspaceToDrive(
        accessToken: String,
        rootFolderName: String,
        existingDriveFolderId: String?,
        workspace: WorkspaceBuildResult,
        onProgress: (DriveSyncProgressUpdate) -> Unit = {}
    ): Result<DrivePushReport> = withContext(Dispatchers.IO) {
        try {
            val safeRootName = rootFolderName.trim().ifBlank { "vortex-research" }
            val totalFiles = workspace.files.count { it.relativePath.trim().trimStart('/').isNotBlank() }

            onProgress(
                DriveSyncProgressUpdate(
                    direction = DriveSyncDirection.PUSH,
                    completedFiles = 0,
                    totalFiles = totalFiles,
                    stageLabel = "Locating Drive workspace '$safeRootName'...",
                    progressFraction = 0.08f
                )
            )

            val anchorId = findOrCreateFolder(accessToken, "Vortex Researcher", null)
            val projectFolderId = if (!existingDriveFolderId.isNullOrBlank() && folderExists(accessToken, existingDriveFolderId)) {
                existingDriveFolderId
            } else {
                findOrCreateFolder(accessToken, safeRootName, anchorId)
            }

            // Cache folder path -> Drive folder ID
            val folderIdCache = mutableMapOf<String, String>("" to projectFolderId)
            var ensuredFolders = 0

            suspend fun ensureNestedPath(dirPath: String): String {
                val clean = dirPath.trim().trim('/')
                if (clean.isEmpty()) return projectFolderId
                folderIdCache[clean]?.let { return it }
                val parentPath = clean.substringBeforeLast('/', "")
                val name = clean.substringAfterLast('/')
                val parentId = ensureNestedPath(parentPath)
                val id = findOrCreateFolder(accessToken, name, parentId)
                folderIdCache[clean] = id
                ensuredFolders++
                return id
            }

            onProgress(
                DriveSyncProgressUpdate(
                    direction = DriveSyncDirection.PUSH,
                    completedFiles = 0,
                    totalFiles = totalFiles,
                    stageLabel = "Ensuring template folders on Drive...",
                    progressFraction = 0.18f
                )
            )

            // 1. Explicitly enforce all canonical system architecture template folders on Google Drive
            for (canonicalDir in workspace.canonicalFolders) {
                ensureNestedPath(canonicalDir)
            }

            // 2. Differential file sync (compare local MD5 vs remote Drive md5Checksum)
            val remoteFolderListings = mutableMapOf<String, Map<String, RemoteDriveEntry>>()
            var uploaded = 0
            var updated = 0
            var skipped = 0
            var processed = 0
            val denom = totalFiles.coerceAtLeast(1).toFloat()

            for (file in workspace.files) {
                val cleanRel = file.relativePath.trim().trimStart('/')
                if (cleanRel.isBlank()) continue
                val dirPath = cleanRel.substringBeforeLast('/', "")
                val fileName = cleanRel.substringAfterLast('/')
                val parentId = ensureNestedPath(dirPath)

                val existingMap = remoteFolderListings.getOrPut(parentId) {
                    listChildren(accessToken, parentId).associateBy { it.name }
                }
                val remote = existingMap[fileName]
                val localMd5 = WorkspaceTemplateEngine.computeMd5Hex(file.content)
                val mime = mimeForFileName(fileName)

                processed++
                val fraction = 0.22f + (0.74f * processed.toFloat() / denom)

                if (remote != null && remote.mimeType != folderMime) {
                    if (remote.md5Checksum.equals(localMd5, ignoreCase = true)) {
                        skipped++
                        onProgress(
                            DriveSyncProgressUpdate(
                                direction = DriveSyncDirection.PUSH,
                                completedFiles = processed,
                                totalFiles = totalFiles,
                                stageLabel = "Drive verified $fileName ($processed/$totalFiles)",
                                progressFraction = fraction
                            )
                        )
                    } else {
                        onProgress(
                            DriveSyncProgressUpdate(
                                direction = DriveSyncDirection.PUSH,
                                completedFiles = processed - 1,
                                totalFiles = totalFiles,
                                stageLabel = "Updating $fileName on Drive ($processed/$totalFiles)",
                                progressFraction = fraction
                            )
                        )
                        patchFileMedia(accessToken, remote.id, file.content, mime)
                        updated++
                    }
                } else {
                    onProgress(
                        DriveSyncProgressUpdate(
                            direction = DriveSyncDirection.PUSH,
                            completedFiles = processed - 1,
                            totalFiles = totalFiles,
                            stageLabel = "Uploading $fileName to Drive ($processed/$totalFiles)",
                            progressFraction = fraction
                        )
                    )
                    uploadNewFileMultipart(accessToken, fileName, parentId, file.content, mime)
                    uploaded++
                }
            }

            onProgress(
                DriveSyncProgressUpdate(
                    direction = DriveSyncDirection.PUSH,
                    completedFiles = totalFiles,
                    totalFiles = totalFiles,
                    stageLabel = "Drive mirror finalized",
                    progressFraction = 1f
                )
            )

            Result.success(
                DrivePushReport(
                    rootFolderId = projectFolderId,
                    driveWebUrl = "https://drive.google.com/drive/folders/$projectFolderId",
                    ensuredFoldersCount = ensuredFolders,
                    uploadedCount = uploaded,
                    updatedCount = updated,
                    skippedUnchangedCount = skipped,
                    workspaceSha256 = workspace.workspaceSha256
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullWorkspaceFromDrive(
        accessToken: String,
        rootFolderName: String,
        existingDriveFolderId: String?,
        onProgress: (DriveSyncProgressUpdate) -> Unit = {}
    ): Result<DrivePullReport> = withContext(Dispatchers.IO) {
        try {
            val safeRootName = rootFolderName.trim().ifBlank { "vortex-research" }
            onProgress(
                DriveSyncProgressUpdate(
                    direction = DriveSyncDirection.PULL,
                    completedFiles = 0,
                    totalFiles = 0,
                    stageLabel = "Scanning Drive folder '$safeRootName'...",
                    progressFraction = 0.12f
                )
            )
            val projectFolderId = if (!existingDriveFolderId.isNullOrBlank() && folderExists(accessToken, existingDriveFolderId)) {
                existingDriveFolderId
            } else {
                val anchorId = findFolderByName(accessToken, "Vortex Researcher", null)
                    ?: return@withContext Result.failure(
                        IllegalStateException("No 'Vortex Researcher' workspace found in Google Drive yet. Push first.")
                    )
                findFolderByName(accessToken, safeRootName, anchorId)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Folder '$safeRootName' not found in Google Drive yet. Push first.")
                    )
            }

            val pulled = mutableListOf<ProjectFile>()
            pullRecursive(accessToken, projectFolderId, "", pulled, onProgress)

            val sha = SafFolderSyncManager.computeProjectHash(
                pulled.filterNot { it.relativePath.equals("vortex-workspace.json", ignoreCase = true) }
            )
            onProgress(
                DriveSyncProgressUpdate(
                    direction = DriveSyncDirection.PULL,
                    completedFiles = pulled.size,
                    totalFiles = pulled.size,
                    stageLabel = "Pulled ${pulled.size} file(s) from Drive",
                    progressFraction = 1f
                )
            )
            Result.success(
                DrivePullReport(
                    rootFolderId = projectFolderId,
                    pulledFiles = pulled,
                    workspaceSha256 = sha
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun pullRecursive(
        accessToken: String,
        folderId: String,
        prefix: String,
        out: MutableList<ProjectFile>,
        onProgress: (DriveSyncProgressUpdate) -> Unit
    ) {
        if (out.size >= 80) return
        val children = listChildren(accessToken, folderId)
        for (entry in children) {
            if (out.size >= 80) break
            val relPath = if (prefix.isBlank()) entry.name else "$prefix/${entry.name}"
            if (entry.mimeType == folderMime) {
                pullRecursive(accessToken, entry.id, relPath, out, onProgress)
            } else if (!entry.mimeType.startsWith("application/vnd.google-apps.")) {
                onProgress(
                    DriveSyncProgressUpdate(
                        direction = DriveSyncDirection.PULL,
                        completedFiles = out.size,
                        totalFiles = 0,
                        stageLabel = "Downloading ${entry.name} from Drive...",
                        progressFraction = null
                    )
                )
                val bytes = downloadFileBytes(accessToken, entry.id) ?: continue
                if (bytes.size <= 350_000 && bytes.take(4096).none { it == 0.toByte() }) {
                    val lower = relPath.lowercase()
                    val category = when {
                        lower.startsWith("prd/") -> "PRD"
                        lower.startsWith("roundtable/") || lower.startsWith("ai_roundtable/") -> "ROUNDTABLE"
                        lower.startsWith("src/") -> "SRC"
                        lower == "vortex-workspace.json" -> "MANIFEST"
                        else -> "RESEARCH"
                    }
                    out.add(
                        ProjectFile(
                            relativePath = relPath,
                            content = String(bytes, Charsets.UTF_8),
                            category = category
                        )
                    )
                }
            }
        }
    }

    private data class RemoteDriveEntry(
        val id: String,
        val name: String,
        val mimeType: String,
        val md5Checksum: String
    )

    private fun folderExists(accessToken: String, folderId: String): Boolean {
        val req = authRequest("$filesEndpoint/$folderId?fields=id,trashed", accessToken).get().build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) false
            else !JSONObject(res.body?.string().orEmpty()).optBoolean("trashed", false)
        }
    }

    private fun findOrCreateFolder(accessToken: String, name: String, parentId: String?): String {
        findFolderByName(accessToken, name, parentId)?.let { return it }
        val body = JSONObject().apply {
            put("name", name)
            put("mimeType", folderMime)
            if (!parentId.isNullOrBlank()) {
                put("parents", JSONArray().put(parentId))
            }
        }.toString()
        val req = authRequest("$filesEndpoint?fields=id", accessToken)
            .post(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Google Drive create folder '$name' failed (HTTP ${res.code}): ${raw.take(180)}")
            }
            return JSONObject(raw).getString("id")
        }
    }

    private fun findFolderByName(accessToken: String, name: String, parentId: String?): String? {
        val escaped = name.replace("'", "\\'")
        val parentClause = if (!parentId.isNullOrBlank()) " and '$parentId' in parents" else " and 'root' in parents"
        val query = "name = '$escaped' and mimeType = '$folderMime' and trashed = false$parentClause"
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val url = "$filesEndpoint?q=$encodedQ&fields=files(id,name)&pageSize=1"
        val req = authRequest(url, accessToken).get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val files = JSONObject(res.body?.string().orEmpty()).optJSONArray("files") ?: return null
            return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }

    private fun listChildren(accessToken: String, parentId: String): List<RemoteDriveEntry> {
        val query = "'$parentId' in parents and trashed = false"
        val encodedQ = URLEncoder.encode(query, "UTF-8")
        val url = "$filesEndpoint?q=$encodedQ&fields=files(id,name,mimeType,md5Checksum)&pageSize=100"
        val req = authRequest(url, accessToken).get().build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Google Drive list failed (HTTP ${res.code}): ${raw.take(160)}")
            }
            val arr = JSONObject(raw).optJSONArray("files") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        RemoteDriveEntry(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            mimeType = obj.optString("mimeType"),
                            md5Checksum = obj.optString("md5Checksum")
                        )
                    )
                }
            }
        }
    }

    private fun uploadNewFileMultipart(
        accessToken: String,
        fileName: String,
        parentId: String,
        content: String,
        mimeType: String
    ) {
        val boundary = "vortex_drive_sync_boundary_${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("mimeType", mimeType)
            put("parents", JSONArray().put(parentId))
        }.toString()

        val multipartBody = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: $mimeType; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--$boundary--")
        }

        val req = authRequest("$uploadEndpoint?uploadType=multipart&fields=id", accessToken)
            .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val err = res.body?.string().orEmpty().take(180)
                throw IllegalStateException("Google Drive upload '$fileName' failed (HTTP ${res.code}): $err")
            }
        }
    }

    private fun patchFileMedia(
        accessToken: String,
        fileId: String,
        content: String,
        mimeType: String
    ) {
        val req = authRequest("$uploadEndpoint/$fileId?uploadType=media&fields=id", accessToken)
            .patch(content.toRequestBody("$mimeType; charset=UTF-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val err = res.body?.string().orEmpty().take(180)
                throw IllegalStateException("Google Drive update failed (HTTP ${res.code}): $err")
            }
        }
    }

    private fun downloadFileBytes(accessToken: String, fileId: String): ByteArray? {
        val req = authRequest("$filesEndpoint/$fileId?alt=media", accessToken).get().build()
        return client.newCall(req).execute().use { res ->
            if (res.isSuccessful) res.body?.bytes() else null
        }
    }

    private fun mimeForFileName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> "text/markdown"
        "json", "jsonl" -> "application/json"
        "yaml", "yml" -> "text/yaml"
        "html" -> "text/html"
        "xml", "svg" -> "application/xml"
        "csv" -> "text/csv"
        else -> "text/plain"
    }

    private fun authRequest(url: String, accessToken: String): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
}
