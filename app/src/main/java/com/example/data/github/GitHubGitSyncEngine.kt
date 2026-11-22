package com.example.data.github

import android.util.Base64
import com.example.data.model.ProjectFile
import com.example.data.sync.SafFolderSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Minimal-intervention GitHub mirroring engine powered by the GitHub Git Data API.
 *
 * Key capabilities:
 * - Auto-provisions missing repositories from the local project folder name.
 * - Computes native Git blob SHA-1 digests locally so unchanged files are skipped (zero wasted uploads).
 * - Pushes all new/changed files in a single atomic commit (tree -> commit -> ref fast-forward).
 * - Falls back seamlessly to Contents API if the remote repository is completely empty.
 * - Supports pulling remote trees back into the local workspace and resolving raw HTTPS URLs.
 */
class GitHubGitSyncEngine(
    private val authManager: GitHubAuthManager = GitHubAuthManager(),
    private val client: OkHttpClient = GitHubAuthManager.defaultHttpClient()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun mirrorFilesAtomically(
        token: String,
        ownerHint: String,
        repoNameOrUrl: String,
        isPrivate: Boolean,
        files: List<ProjectFile>,
        commitMessage: String,
        lastSyncedWorkspaceSha: String? = null,
        forcePush: Boolean = false,
        onProgress: (GitHubSyncProgressUpdate) -> Unit = {}
    ): Result<GitHubMirrorReport> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = token.trim()
            require(cleanToken.isNotBlank()) { "Authenticate with GitHub before mirroring." }

            val normalizedFiles = files
                .mapNotNull { file ->
                    val cleanPath = file.relativePath.trim().trimStart('/').replace('\\', '/')
                    if (cleanPath.isBlank()) null else file.copy(relativePath = cleanPath)
                }
                .distinctBy { it.relativePath.lowercase() }
            require(normalizedFiles.isNotEmpty()) { "No project files available to mirror yet." }

            val totalCount = normalizedFiles.size
            onProgress(
                GitHubSyncProgressUpdate(
                    phase = GitHubSyncPhase.PREPARING,
                    direction = GitHubSyncDirection.PUSH,
                    completedFiles = 0,
                    totalFiles = totalCount,
                    stageLabel = "Checking remote repository...",
                    progressFraction = 0.08f
                )
            )

            val workspaceSha = SafFolderSyncManager.computeProjectHash(normalizedFiles)

            // Resolve owner & repo name from either linked URL or folder name
            val parsedPair = parseOwnerRepo(repoNameOrUrl)
            val owner = parsedPair?.first?.ifBlank { null }
                ?: ownerHint.trim().ifBlank {
                    authManager.verifyAccessToken(cleanToken).getOrThrow().login
                }
            val safeRepo = parsedPair?.second?.ifBlank { null }
                ?: sanitizeRepoName(repoNameOrUrl)

            // 1. Ensure repository exists (auto-create if 404)
            val repoInfo = ensureRepository(
                token = cleanToken,
                owner = owner,
                repoName = safeRepo,
                isPrivate = isPrivate
            ).getOrThrow()

            // Fast-path: if workspace hash already matches and repo already existed, skip network work
            if (!forcePush && !repoInfo.autoCreated && lastSyncedWorkspaceSha == workspaceSha) {
                onProgress(
                    GitHubSyncProgressUpdate(
                        phase = GitHubSyncPhase.IN_SYNC,
                        direction = GitHubSyncDirection.PUSH,
                        completedFiles = totalCount,
                        totalFiles = totalCount,
                        stageLabel = "Remote already up to date",
                        progressFraction = 1f
                    )
                )
                return@withContext Result.success(
                    GitHubMirrorReport(
                        repoUrl = repoInfo.htmlUrl,
                        owner = owner,
                        repoName = safeRepo,
                        branch = repoInfo.defaultBranch,
                        commitSha = "",
                        uploadedCount = 0,
                        updatedCount = 0,
                        skippedUnchangedCount = normalizedFiles.size,
                        workspaceSha256 = workspaceSha,
                        autoCreatedRepo = false
                    )
                )
            }

            onProgress(
                GitHubSyncProgressUpdate(
                    phase = GitHubSyncPhase.DIFFING,
                    direction = GitHubSyncDirection.PUSH,
                    completedFiles = 0,
                    totalFiles = totalCount,
                    stageLabel = "Comparing $totalCount local file(s) with $owner/$safeRepo...",
                    progressFraction = 0.20f
                )
            )

            // 2. Fetch branch HEAD ref -> base commit -> base tree
            val headCommitSha = fetchHeadCommitSha(cleanToken, owner, safeRepo, repoInfo.defaultBranch)
            if (headCommitSha == null) {
                // Empty repository fallback: use Contents API to initialize the branch cleanly
                return@withContext mirrorViaContentsFallback(
                    token = cleanToken,
                    owner = owner,
                    repo = safeRepo,
                    branch = repoInfo.defaultBranch,
                    htmlUrl = repoInfo.htmlUrl,
                    files = normalizedFiles,
                    commitMessage = commitMessage,
                    workspaceSha = workspaceSha,
                    autoCreated = repoInfo.autoCreated,
                    onProgress = onProgress
                )
            }

            val baseTreeSha = fetchCommitTreeSha(cleanToken, owner, safeRepo, headCommitSha)
                ?: return@withContext mirrorViaContentsFallback(
                    token = cleanToken,
                    owner = owner,
                    repo = safeRepo,
                    branch = repoInfo.defaultBranch,
                    htmlUrl = repoInfo.htmlUrl,
                    files = normalizedFiles,
                    commitMessage = commitMessage,
                    workspaceSha = workspaceSha,
                    autoCreated = repoInfo.autoCreated,
                    onProgress = onProgress
                )

            val remoteBlobShas = fetchTreeBlobShas(cleanToken, owner, safeRepo, baseTreeSha)

            // 3. Diff local files against remote Git blob SHA-1s; upload only new/modified blobs
            var newCount = 0
            var updatedCount = 0
            var skippedCount = 0
            val treeEntries = JSONArray()

            normalizedFiles.forEachIndexed { index, file ->
                val contentBytes = file.content.toByteArray(Charsets.UTF_8)
                val localGitSha1 = computeGitBlobSha1(contentBytes)
                val remoteSha = remoteBlobShas[file.relativePath]
                val processed = index + 1
                val fraction = 0.24f + (0.62f * processed.toFloat() / totalCount.coerceAtLeast(1).toFloat())

                if (remoteSha != null && remoteSha.equals(localGitSha1, ignoreCase = true)) {
                    skippedCount++
                    onProgress(
                        GitHubSyncProgressUpdate(
                            phase = GitHubSyncPhase.TRANSFERRING,
                            direction = GitHubSyncDirection.PUSH,
                            completedFiles = processed,
                            totalFiles = totalCount,
                            stageLabel = "Verified ${file.relativePath.substringAfterLast('/')} ($processed/$totalCount)",
                            progressFraction = fraction
                        )
                    )
                    return@forEachIndexed
                }

                onProgress(
                    GitHubSyncProgressUpdate(
                        phase = GitHubSyncPhase.TRANSFERRING,
                        direction = GitHubSyncDirection.PUSH,
                        completedFiles = index,
                        totalFiles = totalCount,
                        stageLabel = "Uploading ${file.relativePath.substringAfterLast('/')} ($processed/$totalCount)",
                        progressFraction = fraction
                    )
                )

                val createdBlobSha = createGitBlob(cleanToken, owner, safeRepo, contentBytes).getOrThrow()
                if (remoteSha == null) newCount++ else updatedCount++

                treeEntries.put(
                    JSONObject().apply {
                        put("path", file.relativePath)
                        put("mode", "100644")
                        put("type", "blob")
                        put("sha", createdBlobSha)
                    }
                )
            }

            if (treeEntries.length() == 0) {
                onProgress(
                    GitHubSyncProgressUpdate(
                        phase = GitHubSyncPhase.IN_SYNC,
                        direction = GitHubSyncDirection.PUSH,
                        completedFiles = totalCount,
                        totalFiles = totalCount,
                        stageLabel = "All $skippedCount file(s) already in sync",
                        progressFraction = 1f
                    )
                )
                return@withContext Result.success(
                    GitHubMirrorReport(
                        repoUrl = repoInfo.htmlUrl,
                        owner = owner,
                        repoName = safeRepo,
                        branch = repoInfo.defaultBranch,
                        commitSha = headCommitSha.take(7),
                        uploadedCount = 0,
                        updatedCount = 0,
                        skippedUnchangedCount = skippedCount,
                        workspaceSha256 = workspaceSha,
                        autoCreatedRepo = repoInfo.autoCreated
                    )
                )
            }

            // 4. Create new tree -> atomic commit -> update branch ref
            onProgress(
                GitHubSyncProgressUpdate(
                    phase = GitHubSyncPhase.COMMITTING,
                    direction = GitHubSyncDirection.PUSH,
                    completedFiles = totalCount,
                    totalFiles = totalCount,
                    stageLabel = "Creating atomic commit (+$newCount new, $updatedCount updated)...",
                    progressFraction = 0.90f
                )
            )
            val newTreeSha = createGitTree(cleanToken, owner, safeRepo, baseTreeSha, treeEntries).getOrThrow()
            val fullCommitMessage = "$commitMessage (+$newCount new, $updatedCount updated)"
            val newCommitSha = createGitCommit(
                token = cleanToken,
                owner = owner,
                repo = safeRepo,
                message = fullCommitMessage,
                treeSha = newTreeSha,
                parentCommitSha = headCommitSha
            ).getOrThrow()

            onProgress(
                GitHubSyncProgressUpdate(
                    phase = GitHubSyncPhase.COMMITTING,
                    direction = GitHubSyncDirection.PUSH,
                    completedFiles = totalCount,
                    totalFiles = totalCount,
                    stageLabel = "Updating branch ${repoInfo.defaultBranch}...",
                    progressFraction = 0.96f
                )
            )
            updateBranchRef(cleanToken, owner, safeRepo, repoInfo.defaultBranch, newCommitSha).getOrThrow()

            Result.success(
                GitHubMirrorReport(
                    repoUrl = repoInfo.htmlUrl,
                    owner = owner,
                    repoName = safeRepo,
                    branch = repoInfo.defaultBranch,
                    commitSha = newCommitSha.take(7),
                    uploadedCount = newCount,
                    updatedCount = updatedCount,
                    skippedUnchangedCount = skippedCount,
                    workspaceSha256 = workspaceSha,
                    autoCreatedRepo = repoInfo.autoCreated
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pullRepositoryFiles(
        token: String,
        repoUrl: String,
        branch: String = "main",
        onProgress: (GitHubSyncProgressUpdate) -> Unit = {}
    ): Result<List<ProjectFile>> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = token.trim()
            val (owner, repo) = parseOwnerRepo(repoUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid GitHub repository URL."))

            onProgress(
                GitHubSyncProgressUpdate(
                    phase = GitHubSyncPhase.PREPARING,
                    direction = GitHubSyncDirection.PULL,
                    completedFiles = 0,
                    totalFiles = 0,
                    stageLabel = "Fetching remote tree $owner/$repo@$branch...",
                    progressFraction = 0.12f
                )
            )

            val treeUrl = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
            val treeReq = GitHubAuthManager.apiRequestBuilder(treeUrl, cleanToken).get().build()
            val blobPaths = mutableListOf<String>()

            client.newCall(treeReq).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed reading GitHub tree (HTTP ${res.code}).")
                    )
                }
                val treeArr = JSONObject(raw).optJSONArray("tree")
                    ?: return@withContext Result.success(emptyList())
                for (i in 0 until treeArr.length()) {
                    val item = treeArr.optJSONObject(i) ?: continue
                    if (item.optString("type") == "blob" && item.optInt("size", 0) <= 300_000) {
                        val path = item.optString("path")
                        if (path.isNotBlank() && !path.startsWith(".git")) {
                            blobPaths.add(path)
                        }
                    }
                }
            }

            val targets = blobPaths.take(50)
            val totalTargets = targets.size.coerceAtLeast(1)
            val pulledFiles = mutableListOf<ProjectFile>()
            targets.forEachIndexed { index, path ->
                val step = index + 1
                onProgress(
                    GitHubSyncProgressUpdate(
                        phase = GitHubSyncPhase.TRANSFERRING,
                        direction = GitHubSyncDirection.PULL,
                        completedFiles = index,
                        totalFiles = targets.size,
                        stageLabel = "Pulling ${path.substringAfterLast('/')} ($step/${targets.size})",
                        progressFraction = 0.16f + (0.80f * step.toFloat() / totalTargets.toFloat())
                    )
                )
                val encodedPath = path.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                val fileReq = GitHubAuthManager.apiRequestBuilder(
                    "https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=$branch",
                    cleanToken
                ).get().build()
                client.newCall(fileReq).execute().use { fRes ->
                    if (fRes.isSuccessful) {
                        val fJson = JSONObject(fRes.body?.string().orEmpty())
                        val encoded = fJson.optString("content").replace("\n", "")
                        if (encoded.isNotBlank()) {
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            val looksBinary = bytes.take(4096).any { it == 0.toByte() }
                            if (!looksBinary) {
                                val lower = path.lowercase()
                                val category = when {
                                    lower.startsWith("prd/") -> "PRD"
                                    lower.startsWith("roundtable/") || lower.startsWith("ai_roundtable/") -> "ROUNDTABLE"
                                    else -> "RESEARCH"
                                }
                                pulledFiles.add(
                                    ProjectFile(
                                        relativePath = path,
                                        content = String(bytes, Charsets.UTF_8),
                                        category = category
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Result.success(pulledFiles)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildRawFileUrl(repoUrl: String, branch: String, relativePath: String): String? {
        val (owner, repo) = parseOwnerRepo(repoUrl) ?: return null
        val cleanPath = relativePath.trim().trimStart('/')
        return "https://raw.githubusercontent.com/$owner/$repo/${branch.ifBlank { "main" }}/$cleanPath"
    }

    fun sanitizeRepoName(raw: String): String =
        raw.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "vortex-research-repo" }

    fun parseOwnerRepo(repoUrlOrSlug: String): Pair<String, String>? {
        val cleaned = repoUrlOrSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removeSuffix(".git")
            .trim('/')
        if (!cleaned.contains('/')) return null
        val parts = cleaned.split("/")
        return if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[0] to parts[1]
        } else null
    }

    // ==================== PRIVATE GIT DATA API HELPERS ====================

    private data class RepoCheckInfo(
        val htmlUrl: String,
        val defaultBranch: String,
        val autoCreated: Boolean
    )

    private fun ensureRepository(
        token: String,
        owner: String,
        repoName: String,
        isPrivate: Boolean
    ): Result<RepoCheckInfo> {
        val checkReq = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repoName",
            token
        ).get().build()

        client.newCall(checkReq).execute().use { checkRes ->
            if (checkRes.isSuccessful) {
                val rJson = JSONObject(checkRes.body?.string().orEmpty())
                return Result.success(
                    RepoCheckInfo(
                        htmlUrl = rJson.optString("html_url", "https://github.com/$owner/$repoName"),
                        defaultBranch = rJson.optString("default_branch", "main").ifBlank { "main" },
                        autoCreated = false
                    )
                )
            }
            if (checkRes.code != 404) {
                return Result.failure(
                    IllegalStateException("GitHub repo lookup failed (HTTP ${checkRes.code}).")
                )
            }
        }

        val createPayload = JSONObject().apply {
            put("name", repoName)
            put("private", isPrivate)
            put("description", "Scaffolded & mirrored automatically by Vortex Researcher")
            put("auto_init", true)
        }.toString()
        val createReq = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/user/repos",
            token
        ).post(createPayload.toRequestBody(jsonMedia)).build()

        client.newCall(createReq).execute().use { createRes ->
            val cBody = createRes.body?.string().orEmpty()
            if (!createRes.isSuccessful) {
                return Result.failure(
                    IllegalStateException("Failed to auto-create repo '$repoName' (HTTP ${createRes.code}): ${cBody.take(160)}")
                )
            }
            val cJson = JSONObject(cBody)
            return Result.success(
                RepoCheckInfo(
                    htmlUrl = cJson.optString("html_url", "https://github.com/$owner/$repoName"),
                    defaultBranch = cJson.optString("default_branch", "main").ifBlank { "main" },
                    autoCreated = true
                )
            )
        }
    }

    private fun fetchHeadCommitSha(
        token: String,
        owner: String,
        repo: String,
        branch: String
    ): String? {
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch",
            token
        ).get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val json = JSONObject(res.body?.string().orEmpty())
            return json.optJSONObject("object")?.optString("sha")?.takeIf { it.isNotBlank() }
        }
    }

    private fun fetchCommitTreeSha(
        token: String,
        owner: String,
        repo: String,
        commitSha: String
    ): String? {
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/commits/$commitSha",
            token
        ).get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val json = JSONObject(res.body?.string().orEmpty())
            return json.optJSONObject("tree")?.optString("sha")?.takeIf { it.isNotBlank() }
        }
    }

    private fun fetchTreeBlobShas(
        token: String,
        owner: String,
        repo: String,
        treeSha: String
    ): Map<String, String> {
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/trees/$treeSha?recursive=1",
            token
        ).get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyMap()
            val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("tree") ?: return emptyMap()
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("type") == "blob") {
                    val path = obj.optString("path")
                    val sha = obj.optString("sha")
                    if (path.isNotBlank() && sha.isNotBlank()) {
                        map[path] = sha
                    }
                }
            }
            return map
        }
    }

    private fun createGitBlob(
        token: String,
        owner: String,
        repo: String,
        contentBytes: ByteArray
    ): Result<String> {
        val payload = JSONObject().apply {
            put("content", Base64.encodeToString(contentBytes, Base64.NO_WRAP))
            put("encoding", "base64")
        }.toString()
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/blobs",
            token
        ).post(payload.toRequestBody(jsonMedia)).build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                return Result.failure(IllegalStateException("Blob upload failed (HTTP ${res.code}): ${body.take(120)}"))
            }
            val sha = JSONObject(body).optString("sha")
            return if (sha.isNotBlank()) Result.success(sha)
            else Result.failure(IllegalStateException("GitHub did not return blob SHA."))
        }
    }

    private fun createGitTree(
        token: String,
        owner: String,
        repo: String,
        baseTreeSha: String,
        treeEntries: JSONArray
    ): Result<String> {
        val payload = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeEntries)
        }.toString()
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/trees",
            token
        ).post(payload.toRequestBody(jsonMedia)).build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                return Result.failure(IllegalStateException("Git tree creation failed (HTTP ${res.code}): ${body.take(120)}"))
            }
            val sha = JSONObject(body).optString("sha")
            return if (sha.isNotBlank()) Result.success(sha)
            else Result.failure(IllegalStateException("GitHub did not return tree SHA."))
        }
    }

    private fun createGitCommit(
        token: String,
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentCommitSha: String
    ): Result<String> {
        val payload = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", JSONArray().put(parentCommitSha))
        }.toString()
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/commits",
            token
        ).post(payload.toRequestBody(jsonMedia)).build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                return Result.failure(IllegalStateException("Git commit failed (HTTP ${res.code}): ${body.take(120)}"))
            }
            val sha = JSONObject(body).optString("sha")
            return if (sha.isNotBlank()) Result.success(sha)
            else Result.failure(IllegalStateException("GitHub did not return commit SHA."))
        }
    }

    private fun updateBranchRef(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        commitSha: String
    ): Result<Unit> {
        val payload = JSONObject().apply {
            put("sha", commitSha)
            put("force", false)
        }.toString()
        val req = GitHubAuthManager.apiRequestBuilder(
            "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch",
            token
        ).patch(payload.toRequestBody(jsonMedia)).build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                return Result.failure(IllegalStateException("Branch update failed (HTTP ${res.code}): ${body.take(120)}"))
            }
            return Result.success(Unit)
        }
    }

    private fun mirrorViaContentsFallback(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        htmlUrl: String,
        files: List<ProjectFile>,
        commitMessage: String,
        workspaceSha: String,
        autoCreated: Boolean,
        onProgress: (GitHubSyncProgressUpdate) -> Unit = {}
    ): Result<GitHubMirrorReport> {
        var uploaded = 0
        var updated = 0
        val total = files.size.coerceAtLeast(1)
        files.forEachIndexed { index, file ->
            val step = index + 1
            onProgress(
                GitHubSyncProgressUpdate(
                    phase = GitHubSyncPhase.TRANSFERRING,
                    direction = GitHubSyncDirection.PUSH,
                    completedFiles = index,
                    totalFiles = files.size,
                    stageLabel = "Initializing ${file.relativePath.substringAfterLast('/')} ($step/${files.size})",
                    progressFraction = 0.22f + (0.74f * step.toFloat() / total.toFloat())
                )
            )
            val encodedPath = file.relativePath.split("/").joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val contentUrl = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath"
            var existingSha: String? = null
            val getReq = GitHubAuthManager.apiRequestBuilder("$contentUrl?ref=$branch", token).get().build()
            client.newCall(getReq).execute().use { getRes ->
                if (getRes.isSuccessful) {
                    existingSha = JSONObject(getRes.body?.string().orEmpty())
                        .optString("sha")
                        .takeIf { it.isNotBlank() }
                }
            }
            val base64Content = Base64.encodeToString(file.content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val putBody = JSONObject().apply {
                put("message", "$commitMessage (${file.relativePath})")
                put("content", base64Content)
                put("branch", branch)
                if (existingSha != null) put("sha", existingSha)
            }.toString()
            val putReq = GitHubAuthManager.apiRequestBuilder(contentUrl, token)
                .put(putBody.toRequestBody(jsonMedia))
                .build()
            client.newCall(putReq).execute().use { putRes ->
                if (!putRes.isSuccessful) {
                    val err = putRes.body?.string().orEmpty().take(160)
                    return Result.failure(IllegalStateException("Failed pushing '${file.relativePath}' (HTTP ${putRes.code}): $err"))
                }
                if (existingSha == null) uploaded++ else updated++
            }
        }
        return Result.success(
            GitHubMirrorReport(
                repoUrl = htmlUrl,
                owner = owner,
                repoName = repo,
                branch = branch,
                commitSha = "",
                uploadedCount = uploaded,
                updatedCount = updated,
                skippedUnchangedCount = 0,
                workspaceSha256 = workspaceSha,
                autoCreatedRepo = autoCreated
            )
        )
    }

    /**
     * Exact Git blob SHA-1 computation: SHA1("blob <byteSize>\0<bytes>").
     * Allows skipping unchanged files locally before uploading a single blob!
     */
    private fun computeGitBlobSha1(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(header)
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
