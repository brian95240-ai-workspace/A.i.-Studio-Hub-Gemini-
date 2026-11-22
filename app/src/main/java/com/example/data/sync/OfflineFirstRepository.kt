package com.example.data.sync

import com.example.data.local.cache.PendingSyncOpEntity
import com.example.data.local.cache.SyncOpType
import com.example.data.local.cache.VortexLocalDatabase
import com.example.data.local.cache.toCacheEntity
import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.VortexJson
import com.example.data.remote.VortexFirestoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SyncConnectionState {
    ONLINE,
    SYNCING,
    OFFLINE
}

data class OfflineSyncTelemetry(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val isHydratingFromFirestore: Boolean = false,
    val firestoreFromCache: Boolean = false,
    val firestoreHasPendingWrites: Boolean = false,
    val firestoreStreamError: Boolean = false,
    val cachedProjectCount: Int = 0,
    val pendingOpCount: Int = 0,
    val hasActiveConflict: Boolean = false,
    val dirtyProjectIds: Set<String> = emptySet(),
    val lastSyncedLabel: String = "",
    val lastSyncSummary: String = "",
    val lastSyncError: String? = null
) {
    /**
     * Authoritative synchronization status combining Android network state,
     * Room pending-sync outbox state, and Cloud Firestore snapshot metadata.
     */
    val connectionState: SyncConnectionState
        get() = when {
            !isOnline || (firestoreFromCache && firestoreStreamError) -> SyncConnectionState.OFFLINE
            isSyncing || pendingOpCount > 0 || firestoreHasPendingWrites || isHydratingFromFirestore ->
                SyncConnectionState.SYNCING
            firestoreFromCache -> SyncConnectionState.OFFLINE
            else -> SyncConnectionState.ONLINE
        }
}

data class LocalMutationOutcome(
    val syncedImmediately: Boolean,
    val pendingQueueCount: Int
)

/**
 * Local-First Repository Coordinator backed by Room + Cloud Firestore.
 *
 * - UI observes Room Flows exclusively (instant startup, full offline browsing & file organization).
 * - Whenever online, Firestore snapshot listeners hydrate Room in the background while never
 *   overwriting local records with pending offline mutations.
 * - Offline mutations commit instantaneously to Room and append to the durable `pending_sync_ops`
 *   outbox; as soon as connectivity returns, pending ops replay in FIFO order automatically.
 */
class OfflineFirstRepository(
    private val localDb: VortexLocalDatabase,
    private val remoteRepo: VortexFirestoreRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val scope: CoroutineScope
) {
    private val projectDao = localDb.projectCacheDao()
    private val prdDao = localDb.prdCacheDao()
    private val roundtableDao = localDb.roundtableCacheDao()
    private val pendingSyncDao = localDb.pendingSyncDao()

    private val syncMutex = Mutex()
    private var projectsHydrationJob: Job? = null
    private var detailsHydrationJob: Job? = null
    private var dashboardHydrationJob: Job? = null
    private var activeHydratedProjectId: String? = null
    private val hydratedDashboardProjectIds = mutableSetOf<String>()

    private val _telemetry = MutableStateFlow(
        OfflineSyncTelemetry(isOnline = connectivityMonitor.isOnline.value)
    )
    val telemetry: StateFlow<OfflineSyncTelemetry> = _telemetry.asStateFlow()

    private val _activeConflict = MutableStateFlow<FileSyncConflict?>(null)
    val activeConflict: StateFlow<FileSyncConflict?> = _activeConflict.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

    init {
        // Observe connectivity & pending outbox count continuously
        scope.launch(Dispatchers.IO) {
            combine(
                connectivityMonitor.isOnline,
                pendingSyncDao.observePendingCount(),
                pendingSyncDao.observePendingProjectIds()
            ) { online, pendingCount, pendingProjects ->
                Triple(online, pendingCount, pendingProjects.toSet())
            }.collect { (online, pendingCount, pendingProjects) ->
                _telemetry.update {
                    it.copy(
                        isOnline = online,
                        pendingOpCount = pendingCount,
                        dirtyProjectIds = pendingProjects
                    )
                }
            }
        }

        // Automatically trigger background synchronization whenever connectivity is restored
        scope.launch(Dispatchers.IO) {
            connectivityMonitor.isOnline
                .collect { online ->
                    if (online) {
                        syncPendingOperations()
                    }
                }
        }
    }

    // ==================== SINGLE SOURCE OF TRUTH (ROOM FLOWS) ====================

    fun observeUserProjects(uid: String): Flow<List<ResearchProject>> =
        projectDao.observeProjectsForUid(uid).map { list -> list.map { it.toDomain() } }

    fun observeProjectPrds(projectId: String): Flow<List<PrdDocument>> =
        prdDao.observePrdsForProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeRoundtableItems(projectId: String): Flow<List<RoundtableItem>> =
        roundtableDao.observeItemsForProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeRecentPrds(limit: Int = 20): Flow<List<PrdDocument>> =
        prdDao.observeRecentPrds(limit).map { list -> list.map { it.toDomain() } }

    fun observeAllPrds(): Flow<List<PrdDocument>> =
        prdDao.observeAllPrds().map { list -> list.map { it.toDomain() } }

    fun observeRecentRoundtableItems(limit: Int = 20): Flow<List<RoundtableItem>> =
        roundtableDao.observeRecentItems(limit).map { list -> list.map { it.toDomain() } }

    fun observeAllRoundtableItems(): Flow<List<RoundtableItem>> =
        roundtableDao.observeAllItems().map { list -> list.map { it.toDomain() } }

    fun observePrdCountsByProject(): Flow<Map<String, Int>> =
        prdDao.observePrdCountsByProject().map { rows -> rows.associate { it.projectId to it.itemCount } }

    fun observeRoundtableCountsByProject(): Flow<Map<String, Int>> =
        roundtableDao.observeItemCountsByProject().map { rows -> rows.associate { it.projectId to it.itemCount } }

    // ==================== BACKGROUND FIRESTORE -> ROOM HYDRATION ====================

    private var projectsCountJob: Job? = null

    fun startUserProjectHydration(uid: String) {
        if (uid.isBlank()) return
        projectsCountJob?.cancel()
        projectsCountJob = scope.launch(Dispatchers.IO) {
            projectDao.observeProjectCountForUid(uid).collect { count ->
                _telemetry.update { it.copy(cachedProjectCount = count) }
            }
        }

        projectsHydrationJob?.cancel()
        _telemetry.update {
            it.copy(
                isHydratingFromFirestore = connectivityMonitor.isOnline.value,
                firestoreStreamError = false
            )
        }
        projectsHydrationJob = scope.launch(Dispatchers.IO) {
            remoteRepo.observeUserProjectsWithMetadata(uid)
                .catch { cause ->
                    if (cause is CancellationException) throw cause
                    _telemetry.update {
                        it.copy(
                            isHydratingFromFirestore = false,
                            firestoreStreamError = true,
                            firestoreFromCache = true
                        )
                    }
                }
                .collect { batch ->
                    val remoteProjects = batch.items
                    val dirtyIds = projectDao.getDirtyProjectIds(uid).toSet()
                    val pendingDeleteIds = pendingSyncDao.getTargetIdsByType(SyncOpType.DELETE_PROJECT).toSet()
                    val protectedIds = dirtyIds + pendingDeleteIds

                    // Check live server snapshots against protected local dirty projects for immediate divergence detection
                    if (!batch.isFromCache && dirtyIds.isNotEmpty() && _activeConflict.value == null) {
                        for (remoteProject in remoteProjects) {
                            if (remoteProject.id in dirtyIds) {
                                val op = pendingSyncDao.getOpByTargetAndType(
                                    remoteProject.id,
                                    SyncOpType.UPDATE_PROJECT_CONTENT
                                )
                                val localEntity = projectDao.getProjectById(remoteProject.id)
                                if (op != null && localEntity != null) {
                                    detectProjectConflict(op, localEntity.toDomain(), localEntity.updatedAtMillis, localEntity.cachedForUid, remoteProject)
                                        ?.let { conflict -> publishConflict(conflict) }
                                }
                            }
                        }
                    }

                    val toUpsert = remoteProjects
                        .filterNot { it.id in protectedIds }
                        .map { it.toCacheEntity(cachedForUid = uid, pendingSync = false) }

                    if (toUpsert.isNotEmpty()) {
                        projectDao.upsertProjects(toUpsert)
                    }

                    val remoteIds = remoteProjects.map { it.id }
                    if (remoteIds.isEmpty()) {
                        projectDao.deleteAllSyncedProjectsForUid(uid)
                    } else {
                        projectDao.deleteStaleSyncedProjects(uid, remoteIds)
                    }

                    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
                    _telemetry.update { current ->
                        current.copy(
                            isHydratingFromFirestore = false,
                            firestoreFromCache = batch.isFromCache,
                            firestoreHasPendingWrites = batch.hasPendingWrites,
                            firestoreStreamError = false,
                            lastSyncedLabel = if (!batch.isFromCache && !batch.hasPendingWrites) {
                                "Synced ${timeFmt.format(Date())}"
                            } else {
                                current.lastSyncedLabel
                            }
                        )
                    }
                }
        }
    }

    fun startProjectDetailsHydration(projectId: String?) {
        if (projectId == activeHydratedProjectId) return
        detailsHydrationJob?.cancel()
        activeHydratedProjectId = projectId
        if (projectId.isNullOrBlank()) return

        detailsHydrationJob = scope.launch(Dispatchers.IO) {
            launch {
                remoteRepo.observeProjectPrds(projectId)
                    .catch { cause ->
                        if (cause is CancellationException) throw cause
                    }
                    .collect { remotePrds ->
                        prdDao.replacePrdsForProject(
                            projectId = projectId,
                            prds = remotePrds.map { it.toCacheEntity() }
                        )
                    }
            }
            launch {
                remoteRepo.observeRoundtableItems(projectId)
                    .catch { cause ->
                        if (cause is CancellationException) throw cause
                    }
                    .collect { remoteItems ->
                        val dirtyIds = roundtableDao.getDirtyItemIds(projectId).toSet()
                        val pendingDeleteIds = pendingSyncDao
                            .getTargetIdsByType(SyncOpType.DELETE_ROUNDTABLE_ITEM)
                            .toSet()
                        val protectedIds = dirtyIds + pendingDeleteIds

                        val toUpsert = remoteItems
                            .filterNot { it.id in protectedIds }
                            .map { it.toCacheEntity(pendingSync = false) }

                        if (toUpsert.isNotEmpty()) {
                            roundtableDao.upsertItems(toUpsert)
                        }

                        val remoteIds = remoteItems.map { it.id }
                        if (remoteIds.isEmpty()) {
                            roundtableDao.deleteAllSyncedItemsForProject(projectId)
                        } else {
                            roundtableDao.deleteStaleSyncedItems(projectId, remoteIds)
                        }
                    }
            }
        }
    }

    /**
     * Lazily hydrates PRDs and Roundtable items into Room for the most recently updated
     * projects so the Centralized Project Dashboard surfaces fresh quick-access cards across
     * all active projects while never overwriting local dirty offline changes.
     */
    fun hydrateDashboardProjects(projectIds: List<String>) {
        if (!connectivityMonitor.isOnline.value) return
        val targets = projectIds
            .filter { it.isNotBlank() && it != activeHydratedProjectId && it !in hydratedDashboardProjectIds }
            .take(8)
        if (targets.isEmpty()) return

        dashboardHydrationJob?.cancel()
        dashboardHydrationJob = scope.launch(Dispatchers.IO) {
            for (projectId in targets) {
                if (!connectivityMonitor.isOnline.value) break
                remoteRepo.fetchProjectPrdsOnce(projectId).onSuccess { remotePrds ->
                    prdDao.replacePrdsForProject(
                        projectId = projectId,
                        prds = remotePrds.map { it.toCacheEntity() }
                    )
                }
                remoteRepo.fetchRoundtableItemsOnce(projectId).onSuccess { remoteItems ->
                    val dirtyIds = roundtableDao.getDirtyItemIds(projectId).toSet()
                    val pendingDeleteIds = pendingSyncDao
                        .getTargetIdsByType(SyncOpType.DELETE_ROUNDTABLE_ITEM)
                        .toSet()
                    val protectedIds = dirtyIds + pendingDeleteIds
                    val toUpsert = remoteItems
                        .filterNot { it.id in protectedIds }
                        .map { it.toCacheEntity(pendingSync = false) }
                    if (toUpsert.isNotEmpty()) {
                        roundtableDao.upsertItems(toUpsert)
                    }
                    val remoteIds = remoteItems.map { it.id }
                    if (remoteIds.isEmpty()) {
                        roundtableDao.deleteAllSyncedItemsForProject(projectId)
                    } else {
                        roundtableDao.deleteStaleSyncedItems(projectId, remoteIds)
                    }
                }
                hydratedDashboardProjectIds.add(projectId)
            }
        }
    }

    // ==================== DIRECT CACHE WRITE-THROUGH (AFTER ONLINE AI CALLS) ====================

    suspend fun cacheSyncedProject(project: ResearchProject, uid: String) {
        if (project.id.isBlank() || uid.isBlank()) return
        projectDao.upsertProject(project.toCacheEntity(cachedForUid = uid, pendingSync = false))
    }

    suspend fun cacheSyncedPrd(prd: PrdDocument) {
        if (prd.id.isBlank()) return
        prdDao.upsertPrd(prd.toCacheEntity())
    }

    // ==================== LOCAL-FIRST OFFLINE MUTATIONS ====================

    suspend fun saveProjectContentOfflineFirst(
        project: ResearchProject,
        uid: String,
        editedFilePath: String = ""
    ): LocalMutationOutcome {
        val now = System.currentTimeMillis()
        val existingOp = pendingSyncDao.getOpByTargetAndType(project.id, SyncOpType.UPDATE_PROJECT_CONTENT)
        val existingCached = projectDao.getProjectById(project.id)

        // Preserve the original server timestamp baseline across consecutive offline edits
        val baselinePayload = if (existingOp != null && existingOp.payloadJson.isNotBlank()) {
            val (baseMillis, prevPath) = SyncConflictHelper.decodeBaseline(existingOp.payloadJson)
            SyncConflictHelper.encodeBaseline(
                baseServerUpdatedAtMillis = baseMillis,
                editedFilePath = editedFilePath.ifBlank { prevPath }
            )
        } else {
            val baseMillis = existingCached?.takeIf { !it.pendingSync }?.updatedAtMillis
                ?: project.updatedAt?.toDate()?.time
                ?: 0L
            SyncConflictHelper.encodeBaseline(
                baseServerUpdatedAtMillis = baseMillis,
                editedFilePath = editedFilePath
            )
        }

        projectDao.upsertProject(
            project.toCacheEntity(
                cachedForUid = uid,
                pendingSync = true,
                updatedAtOverrideMillis = now
            )
        )
        // Coalesce repeated content/file organization edits for the same project
        pendingSyncDao.deleteByTargetAndType(project.id, SyncOpType.UPDATE_PROJECT_CONTENT)
        pendingSyncDao.enqueue(
            PendingSyncOpEntity(
                projectId = project.id,
                targetEntityId = project.id,
                opType = SyncOpType.UPDATE_PROJECT_CONTENT,
                payloadJson = baselinePayload,
                createdAtMillis = now
            )
        )
        return tryImmediateSyncIfOnline()
    }

    suspend fun updateRoundtableFoldersOfflineFirst(
        project: ResearchProject,
        updatedFolders: List<String>,
        uid: String
    ): LocalMutationOutcome {
        val cleaned = updatedFolders
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(40)
        val now = System.currentTimeMillis()
        val updatedProject = project.copy(roundtableFolders = cleaned)
        projectDao.upsertProject(
            updatedProject.toCacheEntity(
                cachedForUid = uid,
                pendingSync = true,
                updatedAtOverrideMillis = now
            )
        )
        pendingSyncDao.deleteByTargetAndType(project.id, SyncOpType.UPDATE_ROUNDTABLE_FOLDERS)
        pendingSyncDao.enqueue(
            PendingSyncOpEntity(
                projectId = project.id,
                targetEntityId = project.id,
                opType = SyncOpType.UPDATE_ROUNDTABLE_FOLDERS,
                payloadJson = VortexJson.encodeStringList(cleaned),
                createdAtMillis = now
            )
        )
        return tryImmediateSyncIfOnline()
    }

    suspend fun addRoundtableItemOfflineFirst(item: RoundtableItem): LocalMutationOutcome {
        val now = System.currentTimeMillis()
        val localId = item.id.ifBlank { "$LOCAL_RT_PREFIX${UUID.randomUUID().toString().take(12)}" }
        val localItem = item.copy(id = localId)
        roundtableDao.upsertItem(
            localItem.toCacheEntity(pendingSync = true, updatedAtOverrideMillis = now)
        )
        pendingSyncDao.enqueue(
            PendingSyncOpEntity(
                projectId = item.projectId,
                targetEntityId = localId,
                opType = SyncOpType.CREATE_ROUNDTABLE_ITEM,
                createdAtMillis = now
            )
        )
        return tryImmediateSyncIfOnline()
    }

    suspend fun updateRoundtableItemOfflineFirst(item: RoundtableItem): LocalMutationOutcome {
        val now = System.currentTimeMillis()
        val existingOp = pendingSyncDao.getOpByTargetAndType(item.id, SyncOpType.UPDATE_ROUNDTABLE_ITEM)
        val existingCached = roundtableDao.getItemById(item.id)
        val baselinePayload = if (existingOp != null && existingOp.payloadJson.isNotBlank()) {
            existingOp.payloadJson
        } else {
            val baseMillis = existingCached?.takeIf { !it.pendingSync }?.updatedAtMillis
                ?: item.updatedAt?.toDate()?.time
                ?: 0L
            SyncConflictHelper.encodeBaseline(
                baseServerUpdatedAtMillis = baseMillis,
                editedFilePath = "${item.modelFolder}/${item.fileName}"
            )
        }

        roundtableDao.upsertItem(
            item.toCacheEntity(pendingSync = true, updatedAtOverrideMillis = now)
        )
        val hasPendingCreate = pendingSyncDao.countByTargetAndType(
            item.id,
            SyncOpType.CREATE_ROUNDTABLE_ITEM
        ) > 0
        if (!hasPendingCreate) {
            pendingSyncDao.deleteByTargetAndType(item.id, SyncOpType.UPDATE_ROUNDTABLE_ITEM)
            pendingSyncDao.enqueue(
                PendingSyncOpEntity(
                    projectId = item.projectId,
                    targetEntityId = item.id,
                    opType = SyncOpType.UPDATE_ROUNDTABLE_ITEM,
                    payloadJson = baselinePayload,
                    createdAtMillis = now
                )
            )
        }
        return tryImmediateSyncIfOnline()
    }

    suspend fun deleteRoundtableItemOfflineFirst(
        projectId: String,
        itemId: String
    ): LocalMutationOutcome {
        roundtableDao.deleteItemById(itemId)
        val wasLocalOnly = itemId.startsWith(LOCAL_RT_PREFIX) ||
            pendingSyncDao.countByTargetAndType(itemId, SyncOpType.CREATE_ROUNDTABLE_ITEM) > 0
        pendingSyncDao.deleteAllForTarget(itemId)
        if (!wasLocalOnly) {
            pendingSyncDao.enqueue(
                PendingSyncOpEntity(
                    projectId = projectId,
                    targetEntityId = itemId,
                    opType = SyncOpType.DELETE_ROUNDTABLE_ITEM,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
        return tryImmediateSyncIfOnline()
    }

    suspend fun deleteProjectOfflineFirst(projectId: String): LocalMutationOutcome {
        projectDao.deleteProjectById(projectId)
        prdDao.deletePrdsForProject(projectId)
        roundtableDao.deleteItemsForProject(projectId)
        pendingSyncDao.deleteAllForProject(projectId)
        pendingSyncDao.enqueue(
            PendingSyncOpEntity(
                projectId = projectId,
                targetEntityId = projectId,
                opType = SyncOpType.DELETE_PROJECT,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        return tryImmediateSyncIfOnline()
    }

    // ==================== BACKGROUND SYNC ENGINE ====================

    private suspend fun tryImmediateSyncIfOnline(): LocalMutationOutcome {
        val online = connectivityMonitor.refreshNow()
        if (online) {
            syncPendingOperations()
        }
        val remaining = pendingSyncDao.getAllPendingOps().size
        return LocalMutationOutcome(
            syncedImmediately = online && remaining == 0,
            pendingQueueCount = remaining
        )
    }

    private sealed interface OpExecutionOutcome {
        data object Succeeded : OpExecutionOutcome
        data class ConflictDetected(val conflict: FileSyncConflict) : OpExecutionOutcome
        data class Failed(val errorMessage: String) : OpExecutionOutcome
    }

    suspend fun syncPendingOperations(): Int = syncMutex.withLock {
        if (!connectivityMonitor.refreshNow()) {
            return@withLock 0
        }
        val initialOps = pendingSyncDao.getAllPendingOps()
        if (initialOps.isEmpty()) {
            return@withLock 0
        }

        _telemetry.update { it.copy(isSyncing = true, lastSyncError = null) }

        var syncedCount = 0
        var lastErr: String? = null

        // Replay pending outbox ops in FIFO order with brief retry on transient hiccups
        for (op in initialOps) {
            if (!connectivityMonitor.refreshNow()) break
            // Pause op if already awaiting user conflict resolution
            if (_activeConflict.value?.opId == op.opId) continue

            var outcome: OpExecutionOutcome = OpExecutionOutcome.Failed("Sync failed")
            for (attempt in 0..1) {
                outcome = executeSingleOp(op)
                if (outcome !is OpExecutionOutcome.Failed) break
                if (attempt == 0 && connectivityMonitor.refreshNow()) {
                    delay(600L)
                }
            }

            when (outcome) {
                is OpExecutionOutcome.Succeeded -> {
                    pendingSyncDao.deleteById(op.opId)
                    syncedCount++
                }
                is OpExecutionOutcome.ConflictDetected -> {
                    publishConflict(outcome.conflict)
                }
                is OpExecutionOutcome.Failed -> {
                    pendingSyncDao.incrementRetry(op.opId, outcome.errorMessage)
                    lastErr = outcome.errorMessage
                }
            }
        }

        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        val remaining = pendingSyncDao.getAllPendingOps().size
        _telemetry.update {
            it.copy(
                isSyncing = false,
                pendingOpCount = remaining,
                hasActiveConflict = _activeConflict.value != null,
                lastSyncedLabel = if (syncedCount > 0) "Synced ${timeFmt.format(Date())}" else it.lastSyncedLabel,
                lastSyncSummary = when {
                    _activeConflict.value != null -> "Sync paused: resolve file conflict to finish syncing."
                    syncedCount > 0 && remaining == 0 -> "Background sync pushed $syncedCount change(s) to cloud."
                    syncedCount > 0 && remaining > 0 -> "Synced $syncedCount change(s); $remaining still queued."
                    else -> it.lastSyncSummary
                },
                lastSyncError = lastErr
            )
        }
        syncedCount
    }

    suspend fun resolveConflict(
        conflict: FileSyncConflict,
        strategy: ConflictResolutionStrategy
    ): Result<String> = syncMutex.withLock {
        runCatching {
            when (conflict.kind) {
                ConflictKind.REPO_FILE -> resolveRepoFileConflict(conflict, strategy)
                ConflictKind.ROUNDTABLE_FILE -> resolveRoundtableFileConflict(conflict, strategy)
            }
        }.onSuccess { message ->
            _activeConflict.value = null
            val remaining = pendingSyncDao.getAllPendingOps().size
            _telemetry.update {
                it.copy(
                    hasActiveConflict = false,
                    pendingOpCount = remaining,
                    lastSyncSummary = message,
                    lastSyncError = null
                )
            }
        }
    }

    private suspend fun resolveRepoFileConflict(
        conflict: FileSyncConflict,
        strategy: ConflictResolutionStrategy
    ): String {
        val localProj = conflict.localProject
            ?: projectDao.getProjectById(conflict.projectId)?.toDomain()
            ?: error("Local project not found.")
        val serverProj = conflict.serverProject
            ?: remoteRepo.getProjectFromServer(conflict.projectId).getOrNull()
            ?: localProj

        return when (strategy) {
            ConflictResolutionStrategy.KEEP_LOCAL -> {
                remoteRepo.updateProjectContent(localProj).getOrThrow()
                pendingSyncDao.deleteById(conflict.opId)
                projectDao.upsertProject(
                    localProj.toCacheEntity(
                        cachedForUid = conflict.cachedForUid,
                        pendingSync = false,
                        updatedAtOverrideMillis = System.currentTimeMillis()
                    )
                )
                "Resolved conflict: pushed your local version of ${conflict.filePath}."
            }

            ConflictResolutionStrategy.USE_SERVER -> {
                pendingSyncDao.deleteById(conflict.opId)
                projectDao.upsertProject(
                    serverProj.toCacheEntity(
                        cachedForUid = conflict.cachedForUid,
                        pendingSync = false
                    )
                )
                "Resolved conflict: adopted Firestore server version of ${conflict.filePath}."
            }

            ConflictResolutionStrategy.KEEP_BOTH -> {
                val copyPath = SyncConflictHelper.buildConflictCopyPath(conflict.filePath)
                val localFile = localProj.parsedFiles().firstOrNull { it.relativePath == conflict.filePath }
                val mergedFiles = serverProj.parsedFiles()
                    .filterNot { it.relativePath.equals(copyPath, ignoreCase = true) }
                    .toMutableList()
                mergedFiles.add(
                    ProjectFile(
                        relativePath = copyPath,
                        content = conflict.localContent,
                        category = localFile?.category ?: "RESEARCH"
                    )
                )
                val mergedProj = serverProj.copy(filesJson = VortexJson.encodeFiles(mergedFiles))
                remoteRepo.updateProjectContent(mergedProj).getOrThrow()
                pendingSyncDao.deleteById(conflict.opId)
                projectDao.upsertProject(
                    mergedProj.toCacheEntity(
                        cachedForUid = conflict.cachedForUid,
                        pendingSync = false,
                        updatedAtOverrideMillis = System.currentTimeMillis()
                    )
                )
                "Resolved conflict: kept server file and saved local copy at $copyPath."
            }
        }
    }

    private suspend fun resolveRoundtableFileConflict(
        conflict: FileSyncConflict,
        strategy: ConflictResolutionStrategy
    ): String {
        val localItem = conflict.localRoundtableItem
            ?: roundtableDao.getItemById(conflict.targetEntityId)?.toDomain()
            ?: error("Local Roundtable file not found.")
        val serverItem = conflict.serverRoundtableItem
            ?: remoteRepo.getRoundtableItemFromServer(conflict.projectId, conflict.targetEntityId).getOrNull()
            ?: localItem

        return when (strategy) {
            ConflictResolutionStrategy.KEEP_LOCAL -> {
                remoteRepo.updateRoundtableItem(localItem).getOrThrow()
                pendingSyncDao.deleteById(conflict.opId)
                roundtableDao.upsertItem(
                    localItem.toCacheEntity(
                        pendingSync = false,
                        updatedAtOverrideMillis = System.currentTimeMillis()
                    )
                )
                "Resolved conflict: pushed your local edits for ${conflict.filePath}."
            }

            ConflictResolutionStrategy.USE_SERVER -> {
                pendingSyncDao.deleteById(conflict.opId)
                roundtableDao.upsertItem(serverItem.toCacheEntity(pendingSync = false))
                "Resolved conflict: adopted Firestore server version of ${conflict.filePath}."
            }

            ConflictResolutionStrategy.KEEP_BOTH -> {
                val copyFileName = SyncConflictHelper.buildConflictCopyPath(localItem.fileName)
                val copyItem = localItem.copy(
                    id = "",
                    fileName = copyFileName,
                    content = conflict.localContent,
                    byteSize = conflict.localContent.toByteArray(Charsets.UTF_8).size
                )
                val remoteCopyId = remoteRepo.addRoundtableItem(copyItem).getOrThrow()
                pendingSyncDao.deleteById(conflict.opId)
                roundtableDao.upsertItem(serverItem.toCacheEntity(pendingSync = false))
                roundtableDao.upsertItem(copyItem.copy(id = remoteCopyId).toCacheEntity(pendingSync = false))
                "Resolved conflict: kept server version and created ${localItem.modelFolder}/$copyFileName."
            }
        }
    }

    private fun publishConflict(conflict: FileSyncConflict) {
        _activeConflict.value = conflict
        _telemetry.update { it.copy(hasActiveConflict = true) }
    }

    private fun detectProjectConflict(
        op: PendingSyncOpEntity,
        localProject: ResearchProject,
        localUpdatedAtMillis: Long,
        cachedForUid: String,
        serverProject: ResearchProject
    ): FileSyncConflict? {
        val (baseServerMillis, editedPathHint) = SyncConflictHelper.decodeBaseline(op.payloadJson)
        val serverUpdatedAtMillis = serverProject.updatedAt?.toDate()?.time ?: 0L
        if (baseServerMillis <= 0L || serverUpdatedAtMillis <= baseServerMillis) return null
        if (serverProject.filesJson == localProject.filesJson) return null

        val localFiles = localProject.parsedFiles().associateBy { it.relativePath }
        val serverFiles = serverProject.parsedFiles().associateBy { it.relativePath }

        val divergingPath = when {
            editedPathHint.isNotBlank() &&
                localFiles[editedPathHint]?.content != serverFiles[editedPathHint]?.content -> editedPathHint
            else -> (localFiles.keys + serverFiles.keys).firstOrNull { path ->
                localFiles[path]?.content != serverFiles[path]?.content
            }
        } ?: return null

        val localText = localFiles[divergingPath]?.content.orEmpty()
        val serverText = serverFiles[divergingPath]?.content.orEmpty()
        if (localText == serverText) return null

        return FileSyncConflict(
            conflictId = "REPO_FILE:${localProject.id}:$divergingPath",
            opId = op.opId,
            kind = ConflictKind.REPO_FILE,
            projectId = localProject.id,
            targetEntityId = localProject.id,
            filePath = divergingPath,
            localContent = localText,
            serverContent = serverText,
            localUpdatedAtMillis = localUpdatedAtMillis,
            serverUpdatedAtMillis = serverUpdatedAtMillis,
            cachedForUid = cachedForUid,
            localProject = localProject,
            serverProject = serverProject
        )
    }

    private fun detectRoundtableConflict(
        op: PendingSyncOpEntity,
        localItem: RoundtableItem,
        localUpdatedAtMillis: Long,
        serverItem: RoundtableItem
    ): FileSyncConflict? {
        val (baseServerMillis, _) = SyncConflictHelper.decodeBaseline(op.payloadJson)
        val serverUpdatedAtMillis = serverItem.updatedAt?.toDate()?.time ?: 0L
        if (baseServerMillis <= 0L || serverUpdatedAtMillis <= baseServerMillis) return null
        if (serverItem.content == localItem.content) return null

        return FileSyncConflict(
            conflictId = "ROUNDTABLE_FILE:${localItem.projectId}:${localItem.id}",
            opId = op.opId,
            kind = ConflictKind.ROUNDTABLE_FILE,
            projectId = localItem.projectId,
            targetEntityId = localItem.id,
            filePath = "${localItem.modelFolder}/${localItem.fileName}",
            localContent = localItem.content,
            serverContent = serverItem.content,
            localUpdatedAtMillis = localUpdatedAtMillis,
            serverUpdatedAtMillis = serverUpdatedAtMillis,
            cachedForUid = localItem.authorUid,
            localRoundtableItem = localItem,
            serverRoundtableItem = serverItem
        )
    }

    private suspend fun executeSingleOp(op: PendingSyncOpEntity): OpExecutionOutcome {
        return try {
            when (op.opType) {
                SyncOpType.UPDATE_PROJECT_CONTENT -> {
                    val entity = projectDao.getProjectById(op.targetEntityId)
                        ?: return OpExecutionOutcome.Succeeded
                    val localProject = entity.toDomain()

                    // Check live Firestore server version for divergence before overwriting
                    val serverProject = remoteRepo.getProjectFromServer(entity.id).getOrNull()
                    if (serverProject != null) {
                        val conflict = detectProjectConflict(
                            op = op,
                            localProject = localProject,
                            localUpdatedAtMillis = entity.updatedAtMillis,
                            cachedForUid = entity.cachedForUid,
                            serverProject = serverProject
                        )
                        if (conflict != null) {
                            return OpExecutionOutcome.ConflictDetected(conflict)
                        }
                    }

                    remoteRepo.updateProjectContent(localProject).fold(
                        onSuccess = {
                            projectDao.markSynced(entity.id)
                            OpExecutionOutcome.Succeeded
                        },
                        onFailure = { err ->
                            if (err is CancellationException) throw err
                            OpExecutionOutcome.Failed(err.message?.take(140) ?: "Project sync failed")
                        }
                    )
                }

                SyncOpType.UPDATE_ROUNDTABLE_FOLDERS -> {
                    val folders = VortexJson.decodeStringList(op.payloadJson)
                    if (folders.isEmpty()) return OpExecutionOutcome.Succeeded
                    remoteRepo.updateRoundtableFolders(op.projectId, folders).fold(
                        onSuccess = {
                            val hasMoreProjectOps = pendingSyncDao.countByTargetAndType(
                                op.projectId,
                                SyncOpType.UPDATE_PROJECT_CONTENT
                            ) > 0
                            if (!hasMoreProjectOps) {
                                projectDao.markSynced(op.projectId)
                            }
                            OpExecutionOutcome.Succeeded
                        },
                        onFailure = { err ->
                            if (err is CancellationException) throw err
                            OpExecutionOutcome.Failed(err.message?.take(140) ?: "Folder sync failed")
                        }
                    )
                }

                SyncOpType.DELETE_PROJECT -> {
                    remoteRepo.deleteProject(op.projectId).fold(
                        onSuccess = { OpExecutionOutcome.Succeeded },
                        onFailure = { err ->
                            if (err is CancellationException) throw err
                            OpExecutionOutcome.Failed(err.message?.take(140) ?: "Delete sync failed")
                        }
                    )
                }

                SyncOpType.CREATE_ROUNDTABLE_ITEM -> {
                    val entity = roundtableDao.getItemById(op.targetEntityId)
                        ?: return OpExecutionOutcome.Succeeded
                    remoteRepo.addRoundtableItem(entity.toDomain().copy(id = "")).fold(
                        onSuccess = { remoteId ->
                            roundtableDao.replaceItemId(
                                oldId = entity.id,
                                newItem = entity.copy(id = remoteId, pendingSync = false)
                            )
                            OpExecutionOutcome.Succeeded
                        },
                        onFailure = { err ->
                            if (err is CancellationException) throw err
                            OpExecutionOutcome.Failed(err.message?.take(140) ?: "Create item sync failed")
                        }
                    )
                }

                SyncOpType.UPDATE_ROUNDTABLE_ITEM -> {
                    val entity = roundtableDao.getItemById(op.targetEntityId)
                        ?: return OpExecutionOutcome.Succeeded
                    val localItem = entity.toDomain()

                    // Check live Firestore server version for divergence before overwriting
                    val serverItem = remoteRepo.getRoundtableItemFromServer(entity.projectId, entity.id).getOrNull()
                    if (serverItem != null) {
                        val conflict = detectRoundtableConflict(
                            op = op,
                            localItem = localItem,
                            localUpdatedAtMillis = entity.updatedAtMillis,
                            serverItem = serverItem
                        )
                        if (conflict != null) {
                            return OpExecutionOutcome.ConflictDetected(conflict)
                        }
                    }

                    remoteRepo.updateRoundtableItem(localItem).fold(
                        onSuccess = {
                            roundtableDao.markSynced(entity.id)
                            OpExecutionOutcome.Succeeded
                        },
                        onFailure = { err ->
                            if (err is CancellationException) throw err
                            OpExecutionOutcome.Failed(err.message?.take(140) ?: "Update item sync failed")
                        }
                    )
                }

                SyncOpType.DELETE_ROUNDTABLE_ITEM -> {
                    remoteRepo.deleteRoundtableItem(op.projectId, op.targetEntityId).fold(
                        onSuccess = { OpExecutionOutcome.Succeeded },
                        onFailure = { err ->
                            if (err is CancellationException) throw err
                            OpExecutionOutcome.Failed(err.message?.take(140) ?: "Delete item sync failed")
                        }
                    )
                }

                else -> OpExecutionOutcome.Succeeded
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpExecutionOutcome.Failed(e.message?.take(140) ?: "Sync failed")
        }
    }

    fun close() {
        projectsCountJob?.cancel()
        projectsHydrationJob?.cancel()
        detailsHydrationJob?.cancel()
        dashboardHydrationJob?.cancel()
        connectivityMonitor.unregister()
    }

    companion object {
        const val LOCAL_RT_PREFIX = "local_rt_"
    }
}
