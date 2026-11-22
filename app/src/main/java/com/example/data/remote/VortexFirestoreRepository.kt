package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.ExportFileFormat
import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectInvite
import com.example.data.model.PublicPrdSnapshotEntry
import com.example.data.model.PublicRoundtableSnapshotEntry
import com.example.data.model.PublicShareSnapshot
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.model.TeamRole
import com.example.data.model.VortexJson
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FirestoreSnapshotBatch<T>(
    val items: List<T>,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean
)

class VortexFirestoreRepository(context: Context) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.getString(R.string.firestore_database_id)
    )

    private val projectsRef = db.collection("projects")
    private val invitesRef = db.collection("projectInvites")
    private val publicSharesRef = db.collection("publicShares")

    fun observeUserProjectsWithMetadata(uid: String): Flow<FirestoreSnapshotBatch<ResearchProject>> =
        projectsRef
            .whereArrayContains("memberUids", uid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val items = snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(
                            ResearchProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                FirestoreSnapshotBatch(
                    items = items,
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                val ex = (cause as? Exception) ?: RuntimeException(cause)
                throw FirestoreErrorHandler.handleFirestoreError(ex, OperationType.LIST, "projects")
            }

    fun observeUserProjects(uid: String): Flow<List<ResearchProject>> =
        observeUserProjectsWithMetadata(uid).map { it.items }

    fun observeProjectPrds(projectId: String): Flow<List<PrdDocument>> =
        projectsRef.document(projectId)
            .collection("prds")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(
                            PrdDocument::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .sortedWith(
                        compareByDescending<PrdDocument> { it.majorVersion }
                            .thenByDescending { it.minorVersion }
                    )
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                val ex = (cause as? Exception) ?: RuntimeException(cause)
                throw FirestoreErrorHandler.handleFirestoreError(
                    ex,
                    OperationType.LIST,
                    "projects/$projectId/prds"
                )
            }

    fun observeRoundtableItems(projectId: String): Flow<List<RoundtableItem>> =
        projectsRef.document(projectId)
            .collection("roundtableItems")
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc ->
                        doc.toObject(
                            RoundtableItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                val ex = (cause as? Exception) ?: RuntimeException(cause)
                throw FirestoreErrorHandler.handleFirestoreError(
                    ex,
                    OperationType.LIST,
                    "projects/$projectId/roundtableItems"
                )
            }

    suspend fun fetchProjectPrdsOnce(projectId: String): Result<List<PrdDocument>> = runCatching {
        val snapshot = projectsRef.document(projectId)
            .collection("prds")
            .get()
            .await()
        snapshot.documents
            .mapNotNull { doc ->
                doc.toObject(
                    PrdDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .sortedWith(
                compareByDescending<PrdDocument> { it.majorVersion }
                    .thenByDescending { it.minorVersion }
            )
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.LIST,
            "projects/$projectId/prds"
        )
    }

    suspend fun fetchRoundtableItemsOnce(projectId: String): Result<List<RoundtableItem>> = runCatching {
        val snapshot = projectsRef.document(projectId)
            .collection("roundtableItems")
            .get()
            .await()
        snapshot.documents
            .mapNotNull { doc ->
                doc.toObject(
                    RoundtableItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.LIST,
            "projects/$projectId/roundtableItems"
        )
    }

    suspend fun createProject(project: ResearchProject): Result<String> = runCatching {
        val docRef = if (project.id.isNotBlank()) projectsRef.document(project.id) else projectsRef.document()
        val id = docRef.id
        docRef.set(project.toCreateMap()).await()
        id
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.CREATE,
            "projects/${project.id}"
        )
    }

    suspend fun updateProjectContent(project: ResearchProject): Result<Unit> = runCatching {
        require(project.id.isNotBlank()) { "Project ID is required for update." }
        projectsRef.document(project.id)
            .update(project.toContentUpdateMap())
            .await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projects/${project.id}"
        )
    }

    suspend fun getProjectFromServer(projectId: String): Result<ResearchProject?> = runCatching {
        val snap = projectsRef.document(projectId).get(Source.SERVER).await()
        if (!snap.exists()) null
        else snap.toObject(
            ResearchProject::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.GET,
            "projects/$projectId"
        )
    }

    suspend fun deleteProject(projectId: String): Result<Unit> = runCatching {
        projectsRef.document(projectId).delete().await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.DELETE,
            "projects/$projectId"
        )
    }

    suspend fun createPrdAndUpdateIndex(
        project: ResearchProject,
        existingPrds: List<PrdDocument>,
        newPrd: PrdDocument
    ): Result<PrdDocument> = runCatching {
        val prdCollection = projectsRef.document(project.id).collection("prds")
        val docRef = prdCollection.document()
        docRef.set(newPrd.toCreateMap()).await()

        val savedPrd = newPrd.copy(id = docRef.id)
        val allPrds = (existingPrds + savedPrd).sortedWith(
            compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion }
        )

        val ext = ExportFileFormat.fromWire(project.fileFormat).extension
        val prdDir = ScaffoldingBlueprint.fromWire(project.blueprintId).prdFolder
        val prdIndexContent = buildPrdIndexContent(
            projectTitle = project.title,
            extension = ext,
            prds = allPrds,
            rootFolderName = project.rootFolderName,
            prdFolder = prdDir
        )
        val prdFilePath = "$prdDir/PRD_${savedPrd.version}.$ext"
        val prdIndexPath = "$prdDir/PRD_INDEX.$ext"

        val updatedFiles = project.parsedFiles()
            .filterNot {
                it.relativePath.equals(prdFilePath, ignoreCase = true) ||
                    it.relativePath.equals(prdIndexPath, ignoreCase = true) ||
                    it.relativePath.equals("PRD/PRD_INDEX.$ext", ignoreCase = true) ||
                    it.relativePath.equals("prd/PRD_INDEX.$ext", ignoreCase = true)
            }
            .toMutableList()
        updatedFiles.add(
            ProjectFile(
                relativePath = prdIndexPath,
                content = prdIndexContent,
                category = "PRD"
            )
        )
        updatedFiles.add(
            ProjectFile(
                relativePath = prdFilePath,
                content = savedPrd.markdownContent,
                category = "PRD"
            )
        )

        val updatedProject = project.copy(
            approved = true,
            latestPrdVersion = savedPrd.version,
            filesJson = VortexJson.encodeFiles(updatedFiles)
        )
        projectsRef.document(project.id)
            .update(updatedProject.toContentUpdateMap())
            .await()

        savedPrd
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.CREATE,
            "projects/${project.id}/prds"
        )
    }

    suspend fun syncPrdIndexFile(
        project: ResearchProject,
        prds: List<PrdDocument>
    ): Result<String> = runCatching {
        val sortedPrds = prds.sortedWith(
            compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion }
        )
        val ext = ExportFileFormat.fromWire(project.fileFormat).extension
        val prdDir = ScaffoldingBlueprint.fromWire(project.blueprintId).prdFolder
        val prdIndexPath = "$prdDir/PRD_INDEX.$ext"
        val prdIndexContent = buildPrdIndexContent(
            projectTitle = project.title,
            extension = ext,
            prds = sortedPrds,
            rootFolderName = project.rootFolderName,
            prdFolder = prdDir
        )
        val existing = project.parsedFiles().toMutableList()
        existing.removeAll {
            it.relativePath.equals("PRD/PRD_INDEX.$ext", ignoreCase = true) ||
                it.relativePath.equals("prd/PRD_INDEX.$ext", ignoreCase = true)
        }
        existing.add(
            ProjectFile(
                relativePath = prdIndexPath,
                content = prdIndexContent,
                category = "PRD"
            )
        )
        sortedPrds.forEach { prd ->
            val pPath = "$prdDir/PRD_${prd.version}.$ext"
            if (existing.none { it.relativePath.equals(pPath, ignoreCase = true) }) {
                existing.add(ProjectFile(pPath, prd.markdownContent, "PRD"))
            }
        }
        val updatedProject = project.copy(
            latestPrdVersion = sortedPrds.lastOrNull()?.version ?: project.latestPrdVersion,
            filesJson = VortexJson.encodeFiles(existing)
        )
        projectsRef.document(project.id)
            .update(updatedProject.toContentUpdateMap())
            .await()
        prdIndexContent
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projects/${project.id}"
        )
    }

    suspend fun updateRoundtableFolders(
        projectId: String,
        folders: List<String>
    ): Result<Unit> = runCatching {
        val cleaned = folders
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(40)
        projectsRef.document(projectId)
            .update(
                mapOf(
                    "roundtableFolders" to cleaned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projects/$projectId"
        )
    }

    suspend fun addRoundtableItem(item: RoundtableItem): Result<String> = runCatching {
        val docRef = projectsRef.document(item.projectId)
            .collection("roundtableItems")
            .document()
        docRef.set(item.toCreateMap()).await()
        docRef.id
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.CREATE,
            "projects/${item.projectId}/roundtableItems"
        )
    }

    suspend fun updateRoundtableItem(item: RoundtableItem): Result<Unit> = runCatching {
        require(item.id.isNotBlank()) { "Roundtable item ID required." }
        projectsRef.document(item.projectId)
            .collection("roundtableItems")
            .document(item.id)
            .update(item.toUpdateMap())
            .await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projects/${item.projectId}/roundtableItems/${item.id}"
        )
    }

    suspend fun getRoundtableItemFromServer(
        projectId: String,
        itemId: String
    ): Result<RoundtableItem?> = runCatching {
        val snap = projectsRef.document(projectId)
            .collection("roundtableItems")
            .document(itemId)
            .get(Source.SERVER)
            .await()
        if (!snap.exists()) null
        else snap.toObject(
            RoundtableItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.GET,
            "projects/$projectId/roundtableItems/$itemId"
        )
    }

    suspend fun deleteRoundtableItem(projectId: String, itemId: String): Result<Unit> = runCatching {
        projectsRef.document(projectId)
            .collection("roundtableItems")
            .document(itemId)
            .delete()
            .await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.DELETE,
            "projects/$projectId/roundtableItems/$itemId"
        )
    }

    suspend fun createInvite(
        project: ResearchProject,
        role: TeamRole,
        creatorUid: String
    ): Result<ProjectInvite> = runCatching {
        val safeRole = if (role == TeamRole.EDITOR) TeamRole.EDITOR else TeamRole.VIEWER
        val code = generateSecureCode(16)
        val invite = ProjectInvite(
            inviteCode = code,
            projectId = project.id,
            projectTitle = project.title,
            assignedRole = safeRole.wire,
            createdByUid = creatorUid,
            active = true
        )
        invitesRef.document(code).set(invite.toCreateMap()).await()
        invite
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.CREATE,
            "projectInvites"
        )
    }

    suspend fun joinProjectWithInvite(
        rawCodeOrUrl: String,
        uid: String,
        displayName: String
    ): Result<String> = runCatching {
        val code = extractToken(rawCodeOrUrl, "code")
        require(code.length >= 12) { "Invite code must be at least 12 characters." }

        val inviteSnap = invitesRef.document(code).get().await()
        val invite = inviteSnap.toObject(ProjectInvite::class.java)
            ?: throw IllegalArgumentException("Invite link not found or expired.")
        require(invite.active) { "This invite link has been deactivated." }

        val assignedRole = if (invite.assignedRole == TeamRole.EDITOR.wire) {
            TeamRole.EDITOR.wire
        } else {
            TeamRole.VIEWER.wire
        }
        val safeDisplayName = displayName.ifBlank { "Collaborator" }.take(80)

        projectsRef.document(invite.projectId).update(
            mapOf(
                "memberUids" to FieldValue.arrayUnion(uid),
                "memberRoles.$uid" to assignedRole,
                "memberDisplayNames.$uid" to safeDisplayName,
                "lastJoinedInviteCode" to code,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        invite.projectId
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projectInvites/join"
        )
    }

    suspend fun updateMemberRole(
        project: ResearchProject,
        targetUid: String,
        newRole: TeamRole
    ): Result<Unit> = runCatching {
        require(targetUid != project.ownerId) { "Owner role cannot be changed." }
        projectsRef.document(project.id).update(
            mapOf(
                "memberRoles.$targetUid" to newRole.wire,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projects/${project.id}"
        )
    }

    suspend fun removeMember(project: ResearchProject, targetUid: String): Result<Unit> = runCatching {
        require(targetUid != project.ownerId) { "Owner cannot be removed." }
        val updatedUids = project.memberUids.filterNot { it == targetUid }
        val updatedRoles = project.memberRoles.toMutableMap().apply { remove(targetUid) }
        val updatedNames = project.memberDisplayNames.toMutableMap().apply { remove(targetUid) }

        projectsRef.document(project.id).update(
            mapOf(
                "memberUids" to updatedUids,
                "memberRoles" to updatedRoles,
                "memberDisplayNames" to updatedNames,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.UPDATE,
            "projects/${project.id}"
        )
    }

    suspend fun publishPublicShare(
        project: ResearchProject,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem> = emptyList()
    ): Result<String> = runCatching {
        val shareCode = project.publicShareCode.takeIf { it.length >= 16 } ?: generateSecureCode(24)
        val ext = ExportFileFormat.fromWire(project.fileFormat).extension
        val sortedPrds = prds.sortedWith(
            compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion }
        )
        val latestPrd = sortedPrds.lastOrNull()
        val prdDir = ScaffoldingBlueprint.fromWire(project.blueprintId).prdFolder
        val prdIndex = if (sortedPrds.isNotEmpty()) {
            buildPrdIndexContent(
                projectTitle = project.title,
                extension = ext,
                prds = sortedPrds,
                rootFolderName = project.rootFolderName,
                prdFolder = prdDir
            )
        } else {
            ""
        }

        val publicPrdEntries = sortedPrds.map { prd ->
            PublicPrdSnapshotEntry(
                version = prd.version,
                title = prd.title,
                deltaSummary = prd.deltaSummary,
                markdownContent = prd.markdownContent.take(30_000),
                modelUsed = prd.modelUsed,
                authorName = prd.authorName
            )
        }
        val publicRtEntries = roundtableItems.take(40).map { item ->
            PublicRoundtableSnapshotEntry(
                modelFolder = item.modelFolder,
                fileName = item.fileName,
                detectedLanguage = item.detectedLanguage,
                queueStatus = item.queueStatus,
                content = item.content.take(12_000),
                scrutinyVerdict = item.scrutinyVerdict.take(6_000),
                scrutinizedByModel = item.scrutinizedByModel
            )
        }

        val snapshot = PublicShareSnapshot(
            shareCode = shareCode,
            projectId = project.id,
            title = project.title,
            rootFolderName = project.rootFolderName,
            mode = project.mode,
            fileFormat = project.fileFormat,
            blueprintId = project.blueprintId,
            query = project.query,
            detectedDomain = project.detectedDomain,
            stagesJson = project.stagesJson,
            filesJson = project.filesJson,
            chatAnswer = project.chatAnswer,
            latestPrdVersion = latestPrd?.version ?: project.latestPrdVersion,
            latestPrdMarkdown = latestPrd?.markdownContent.orEmpty(),
            prdIndexMarkdown = prdIndex,
            prdsJson = VortexJson.encodePublicPrds(publicPrdEntries),
            roundtableJson = VortexJson.encodePublicRoundtable(publicRtEntries)
        )

        publicSharesRef.document(shareCode).set(snapshot.toPublishMap()).await()
        projectsRef.document(project.id).update(
            mapOf(
                "publicShareCode" to shareCode,
                "publicShareActive" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        shareCode
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.WRITE,
            "publicShares"
        )
    }

    suspend fun revokePublicShare(project: ResearchProject): Result<Unit> = runCatching {
        if (project.publicShareCode.length >= 16) {
            runCatching { publicSharesRef.document(project.publicShareCode).delete().await() }
        }
        projectsRef.document(project.id).update(
            mapOf(
                "publicShareActive" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.DELETE,
            "publicShares/${project.publicShareCode}"
        )
    }

    suspend fun getPublicShare(rawCodeOrUrl: String): Result<PublicShareSnapshot> = runCatching {
        val code = extractShareCode(rawCodeOrUrl)
        require(code.length >= 16) { "Enter a valid 16+ character public share code or link." }
        val snap = publicSharesRef.document(code).get().await()
        snap.toObject(
            PublicShareSnapshot::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: throw IllegalArgumentException("Shared repository not found or link was revoked.")
    }.onFailure { cause ->
        if (cause is CancellationException) throw cause
        FirestoreErrorHandler.handleFirestoreError(
            (cause as? Exception) ?: RuntimeException(cause),
            OperationType.GET,
            "publicShares/$rawCodeOrUrl"
        )
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        private val random = SecureRandom()

        fun generateSecureCode(length: Int): String = buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

        fun extractToken(input: String, queryParam: String): String {
            val trimmed = input.trim()
            val regex = Regex("[?&]$queryParam=([A-Za-z0-9_-]+)")
            return regex.find(trimmed)?.groupValues?.get(1)
                ?: trimmed.substringAfterLast('/').substringBefore('?').trim()
        }

        fun extractShareCode(input: String): String {
            val trimmed = input.trim()
            if (trimmed.contains("code=")) return extractToken(trimmed, "code")
            return trimmed.substringAfterLast('/').substringBefore('?').trim()
        }

        fun versionBumpLabel(prd: PrdDocument): String = when {
            prd.majorVersion <= 1 && prd.minorVersion == 0 -> "Baseline"
            prd.minorVersion == 0 -> "Major"
            else -> "Minor"
        }

        fun buildPrdIndexContent(
            projectTitle: String,
            extension: String,
            prds: List<PrdDocument>,
            rootFolderName: String = "",
            prdFolder: String = "PRD"
        ): String {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val folderHeader = if (rootFolderName.isNotBlank()) "$rootFolderName/$prdFolder/" else "$prdFolder/"
            return if (extension == "txt") {
                buildString {
                    appendLine("PRD VERSION INDEX & DELTA LEDGER")
                    appendLine("Project: $projectTitle")
                    appendLine("Folder: $folderHeader")
                    appendLine("Total Versions: ${prds.size}")
                    appendLine("=".repeat(56))
                    if (prds.isEmpty()) {
                        appendLine("No PRD versions published yet. Approve research synthesis to generate v1.0.")
                    } else {
                        prds.forEach { prd ->
                            val dateStr = prd.createdAt?.toDate()?.let { dateFmt.format(it) } ?: dateFmt.format(Date())
                            appendLine("[${prd.version} | ${versionBumpLabel(prd)}] ($dateStr) — File: $prdFolder/PRD_${prd.version}.txt")
                            appendLine("  Delta: ${prd.deltaSummary}")
                            if (prd.revisionRequest.isNotBlank()) {
                                appendLine("  Trigger: ${prd.revisionRequest.take(160)}")
                            }
                            appendLine()
                        }
                    }
                }
            } else {
                buildString {
                    appendLine("# PRD Version Index & Delta Ledger")
                    appendLine()
                    appendLine("**Project:** $projectTitle  ")
                    appendLine("**Folder:** `$folderHeader`  ")
                    appendLine("**Total Versions:** ${prds.size}")
                    appendLine()
                    if (prds.isEmpty()) {
                        appendLine("_No PRD versions published yet. Approve the 7-stage synthesis to publish `PRD_v1.0.md`._")
                    } else {
                        appendLine("| Version | Type | Date | File | Concise Delta Summary |")
                        appendLine("| :--- | :--- | :--- | :--- | :--- |")
                        prds.forEach { prd ->
                            val dateStr = prd.createdAt?.toDate()?.let { dateFmt.format(it) } ?: dateFmt.format(Date())
                            val cleanDelta = prd.deltaSummary.replace("|", "\\|").replace("\n", " ")
                            appendLine("| **${prd.version}** | ${versionBumpLabel(prd)} | $dateStr | `$prdFolder/PRD_${prd.version}.md` | $cleanDelta |")
                        }
                    }
                }
            }
        }
    }
}
