package com.example.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.PrdDocument
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.VortexJson
import com.google.firebase.Timestamp
import java.util.Date

object SyncOpType {
    const val UPDATE_PROJECT_CONTENT = "UPDATE_PROJECT_CONTENT"
    const val UPDATE_ROUNDTABLE_FOLDERS = "UPDATE_ROUNDTABLE_FOLDERS"
    const val DELETE_PROJECT = "DELETE_PROJECT"
    const val CREATE_ROUNDTABLE_ITEM = "CREATE_ROUNDTABLE_ITEM"
    const val UPDATE_ROUNDTABLE_ITEM = "UPDATE_ROUNDTABLE_ITEM"
    const val DELETE_ROUNDTABLE_ITEM = "DELETE_ROUNDTABLE_ITEM"
}

@Entity(
    tableName = "cached_projects",
    indices = [Index(value = ["cachedForUid"])]
)
data class CachedProjectEntity(
    @PrimaryKey val id: String,
    val cachedForUid: String,
    val ownerId: String,
    val title: String,
    val rootFolderName: String,
    val mode: String,
    val fileFormat: String,
    val blueprintId: String,
    val query: String,
    val complexityScore: Int,
    val detectedDomain: String,
    val routingPlanSummary: String,
    val pipelineStatus: String,
    val gapVerdict: String,
    val stagesJson: String,
    val filesJson: String,
    val chatAnswer: String,
    val chatHistoryJson: String = "[]",
    val approved: Boolean,
    val latestPrdVersion: String,
    val githubRepoUrl: String,
    val githubBranch: String,
    val publicShareCode: String,
    val publicShareActive: Boolean,
    val lastJoinedInviteCode: String,
    val roundtableFoldersJson: String,
    val tagsJson: String = "[]",
    val memberUidsJson: String,
    val memberRolesJson: String,
    val memberDisplayNamesJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pendingSync: Boolean = false
) {
    fun toDomain(): ResearchProject = ResearchProject(
        id = id,
        ownerId = ownerId,
        title = title,
        rootFolderName = rootFolderName,
        mode = mode,
        fileFormat = fileFormat,
        blueprintId = blueprintId,
        query = query,
        complexityScore = complexityScore,
        detectedDomain = detectedDomain,
        routingPlanSummary = routingPlanSummary,
        pipelineStatus = pipelineStatus,
        gapVerdict = gapVerdict,
        stagesJson = stagesJson,
        filesJson = filesJson,
        chatAnswer = chatAnswer,
        chatHistoryJson = chatHistoryJson,
        approved = approved,
        latestPrdVersion = latestPrdVersion,
        githubRepoUrl = githubRepoUrl,
        githubBranch = githubBranch,
        publicShareCode = publicShareCode,
        publicShareActive = publicShareActive,
        lastJoinedInviteCode = lastJoinedInviteCode,
        roundtableFolders = VortexJson.decodeStringList(roundtableFoldersJson)
            .ifEmpty { ResearchProject.DEFAULT_ROUNDTABLE_FOLDERS },
        tags = VortexJson.decodeStringList(tagsJson),
        memberUids = VortexJson.decodeStringList(memberUidsJson),
        memberRoles = VortexJson.decodeStringMap(memberRolesJson),
        memberDisplayNames = VortexJson.decodeStringMap(memberDisplayNamesJson),
        createdAt = if (createdAtMillis > 0L) Timestamp(Date(createdAtMillis)) else null,
        updatedAt = if (updatedAtMillis > 0L) Timestamp(Date(updatedAtMillis)) else null
    )
}

fun ResearchProject.toCacheEntity(
    cachedForUid: String,
    pendingSync: Boolean = false,
    updatedAtOverrideMillis: Long? = null
): CachedProjectEntity {
    val now = System.currentTimeMillis()
    return CachedProjectEntity(
        id = id,
        cachedForUid = cachedForUid,
        ownerId = ownerId,
        title = title,
        rootFolderName = rootFolderName,
        mode = mode,
        fileFormat = fileFormat,
        blueprintId = blueprintId,
        query = query,
        complexityScore = complexityScore,
        detectedDomain = detectedDomain,
        routingPlanSummary = routingPlanSummary,
        pipelineStatus = pipelineStatus,
        gapVerdict = gapVerdict,
        stagesJson = stagesJson,
        filesJson = filesJson,
        chatAnswer = chatAnswer,
        chatHistoryJson = chatHistoryJson,
        approved = approved,
        latestPrdVersion = latestPrdVersion,
        githubRepoUrl = githubRepoUrl,
        githubBranch = githubBranch,
        publicShareCode = publicShareCode,
        publicShareActive = publicShareActive,
        lastJoinedInviteCode = lastJoinedInviteCode,
        roundtableFoldersJson = VortexJson.encodeStringList(effectiveRoundtableFolders()),
        tagsJson = VortexJson.encodeStringList(tags),
        memberUidsJson = VortexJson.encodeStringList(memberUids),
        memberRolesJson = VortexJson.encodeStringMap(memberRoles),
        memberDisplayNamesJson = VortexJson.encodeStringMap(memberDisplayNames),
        createdAtMillis = createdAt?.toDate()?.time ?: now,
        updatedAtMillis = updatedAtOverrideMillis ?: updatedAt?.toDate()?.time ?: now,
        pendingSync = pendingSync
    )
}

@Entity(
    tableName = "cached_prds",
    indices = [Index(value = ["projectId"])]
)
data class CachedPrdEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val version: String,
    val majorVersion: Int,
    val minorVersion: Int,
    val title: String,
    val deltaSummary: String,
    val revisionRequest: String,
    val markdownContent: String,
    val modelUsed: String,
    val authorUid: String,
    val authorName: String,
    val createdAtMillis: Long
) {
    fun toDomain(): PrdDocument = PrdDocument(
        id = id,
        projectId = projectId,
        version = version,
        majorVersion = majorVersion,
        minorVersion = minorVersion,
        title = title,
        deltaSummary = deltaSummary,
        revisionRequest = revisionRequest,
        markdownContent = markdownContent,
        modelUsed = modelUsed,
        authorUid = authorUid,
        authorName = authorName,
        createdAt = if (createdAtMillis > 0L) Timestamp(Date(createdAtMillis)) else null
    )
}

fun PrdDocument.toCacheEntity(): CachedPrdEntity = CachedPrdEntity(
    id = id,
    projectId = projectId,
    version = version,
    majorVersion = majorVersion,
    minorVersion = minorVersion,
    title = title,
    deltaSummary = deltaSummary,
    revisionRequest = revisionRequest,
    markdownContent = markdownContent,
    modelUsed = modelUsed,
    authorUid = authorUid,
    authorName = authorName,
    createdAtMillis = createdAt?.toDate()?.time ?: System.currentTimeMillis()
)

@Entity(
    tableName = "cached_roundtable_items",
    indices = [Index(value = ["projectId"])]
)
data class CachedRoundtableItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val modelFolder: String,
    val fileName: String,
    val extension: String,
    val detectedLanguage: String,
    val content: String,
    val byteSize: Int,
    val queueStatus: String,
    val scrutinyVerdict: String,
    val outboundRelayPrompt: String,
    val groundingSourcesJson: String,
    val scrutinizedByModel: String,
    val authorUid: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pendingSync: Boolean = false
) {
    fun toDomain(): RoundtableItem = RoundtableItem(
        id = id,
        projectId = projectId,
        modelFolder = modelFolder,
        fileName = fileName,
        extension = extension,
        detectedLanguage = detectedLanguage,
        content = content,
        byteSize = byteSize,
        queueStatus = queueStatus,
        scrutinyVerdict = scrutinyVerdict,
        outboundRelayPrompt = outboundRelayPrompt,
        groundingSourcesJson = groundingSourcesJson,
        scrutinizedByModel = scrutinizedByModel,
        authorUid = authorUid,
        createdAt = if (createdAtMillis > 0L) Timestamp(Date(createdAtMillis)) else null,
        updatedAt = if (updatedAtMillis > 0L) Timestamp(Date(updatedAtMillis)) else null
    )
}

fun RoundtableItem.toCacheEntity(
    pendingSync: Boolean = false,
    updatedAtOverrideMillis: Long? = null
): CachedRoundtableItemEntity {
    val now = System.currentTimeMillis()
    return CachedRoundtableItemEntity(
        id = id,
        projectId = projectId,
        modelFolder = modelFolder,
        fileName = fileName,
        extension = extension,
        detectedLanguage = detectedLanguage,
        content = content,
        byteSize = byteSize,
        queueStatus = queueStatus,
        scrutinyVerdict = scrutinyVerdict,
        outboundRelayPrompt = outboundRelayPrompt,
        groundingSourcesJson = groundingSourcesJson,
        scrutinizedByModel = scrutinizedByModel,
        authorUid = authorUid,
        createdAtMillis = createdAt?.toDate()?.time ?: now,
        updatedAtMillis = updatedAtOverrideMillis ?: updatedAt?.toDate()?.time ?: now,
        pendingSync = pendingSync
    )
}

@Entity(
    tableName = "pending_sync_ops",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["targetEntityId"])
    ]
)
data class PendingSyncOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0L,
    val projectId: String,
    val targetEntityId: String,
    val opType: String,
    val payloadJson: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String = ""
)
