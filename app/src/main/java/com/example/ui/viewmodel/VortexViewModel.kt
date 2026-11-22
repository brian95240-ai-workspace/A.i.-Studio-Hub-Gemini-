package com.example.ui.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.auth.DriveAuthResult
import com.example.auth.GoogleDriveAuthManager
import com.example.data.ai.AudioTranscriptionResult
import com.example.data.ai.ExternalRouterApi
import com.example.data.ai.PipelinePassFailureException
import com.example.data.ai.SelfHostedHealthResult
import com.example.data.ai.Stage0TriageResult
import com.example.data.ai.VortexModelRouter
import com.example.data.ai.VortexPipelineOrchestrator
import com.example.data.voice.VortexSpeechController
import com.example.data.docs.VortexNextStepsDocProvider
import com.example.data.export.BulkProjectExportBundle
import com.example.data.export.BulkZipExporter
import com.example.data.export.PdfReportOptions
import com.example.data.export.UniversalExportFormat
import com.example.data.export.UniversalFormatCatalog
import com.example.data.github.GitHubAuthManager
import com.example.data.infra.ConnectorKind
import com.example.data.infra.ConnectorStatus
import com.example.data.infra.ConnectorVisibility
import com.example.data.infra.InfraConnectorProfile
import com.example.data.infra.InfrastructureRegistryEngine
import com.example.data.infra.InfrastructureRegistryState
import com.example.data.infra.McpAuditLogEntry
import com.example.data.infra.McpAuditLogStore
import com.example.data.infra.McpCallPolicyDecision
import com.example.data.infra.McpFullResultStore
import com.example.data.infra.McpJsonRpcClient
import com.example.data.infra.McpStoredResult
import com.example.data.infra.McpToolCallOutcome
import com.example.data.github.GitHubAuthMethod
import com.example.data.github.GitHubDeviceCodeSession
import com.example.data.github.GitHubGitSyncEngine
import com.example.data.github.GitHubHeaderSyncState
import com.example.data.github.GitHubMirrorReport
import com.example.data.github.GitHubRepoSummary
import com.example.data.github.GitHubSyncDirection
import com.example.data.github.GitHubSyncPhase
import com.example.data.github.GitHubSyncProgressUpdate
import com.example.data.local.LocalPreferencesStore
import com.example.data.local.cache.VortexLocalDatabase
import com.example.data.model.ChatTurn
import com.example.data.model.ExportFileFormat
import com.example.data.model.ExternalRouterType
import com.example.data.model.GapVerdict
import com.example.data.model.MetadataTagEngine
import com.example.data.model.NoteCategory
import com.example.data.model.PipelineStatus
import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ProjectInvite
import com.example.data.model.ResearchMode
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoundtableStatus
import com.example.data.model.RoutingPreferences
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.model.StageRecord
import com.example.data.model.TeamRole
import com.example.data.model.VortexJson
import com.example.data.remote.VortexFirestoreRepository
import com.example.data.security.DiscreteAutoFillResolution
import com.example.data.security.EncryptedEnvVaultStore
import com.example.data.security.EnvAutoFillTarget
import com.example.data.security.EnvSecretCategory
import com.example.data.security.EnvSecretEntry
import com.example.data.security.ModelSecretPermissions
import com.example.data.security.SecretVaultMigration
import com.example.data.sync.ConflictResolutionStrategy
import com.example.data.sync.DriveHeaderSyncState
import com.example.data.sync.DrivePushReport
import com.example.data.sync.DriveSyncDirection
import com.example.data.sync.DriveSyncPhase
import com.example.data.sync.DriveSyncProgressUpdate
import com.example.data.sync.FileSyncConflict
import com.example.data.sync.GoogleDriveSyncService
import com.example.data.sync.NetworkConnectivityMonitor
import com.example.data.sync.OfflineFirstRepository
import com.example.data.sync.OfflineSyncTelemetry
import com.example.data.sync.SafFolderSyncManager
import com.example.data.sync.UniversalArtifactIngestor
import com.example.data.sync.WorkspaceTemplateEngine
import com.example.ui.components.PgSchemaMappingPlan
import com.example.ui.components.PostGisSchemaMapperEngine
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PendingDriveAction { PUSH, PULL }

data class VortexUiState(
    val projects: List<ResearchProject> = emptyList(),
    val activeProjectId: String? = null,
    val draftQuery: String = "",
    val draftMode: ResearchMode = ResearchMode.PROJECT,
    val draftFileFormat: ExportFileFormat = ExportFileFormat.MD,
    val draftBlueprint: ScaffoldingBlueprint = ScaffoldingBlueprint.SYSTEM_ARCHITECTURE_CANONICAL,
    val recommendedBlueprint: ScaffoldingBlueprint? = null,
    val draftRootFolder: String = "",
    val liveStages: List<StageRecord> = emptyList(),
    val pipelineRunning: Boolean = false,
    val transcribingVoiceNote: Boolean = false,
    val lastVoiceNoteResult: AudioTranscriptionResult? = null,
    val lastVoiceNoteUsingBrief: Boolean = true,
    val preVoiceNoteQuerySnapshot: String? = null,
    val voiceNoteError: String? = null,
    val chatFollowUpRunning: Boolean = false,
    val prdGenerating: Boolean = false,
    val scrutinyRunning: Boolean = false,
    val autoDispatchRunning: Boolean = false,
    val crossModelLoading: Boolean = false,
    val crossModelSynthesis: String = "",
    val crossModelUsed: String = "",
    val ragLoading: Boolean = false,
    val ragAnswer: String = "",
    val ragModelUsed: String = "",
    val syncingGitHub: Boolean = false,
    val syncingSaf: Boolean = false,
    val syncingDrive: Boolean = false,
    val exportingReport: Boolean = false,
    val extractingPostGisEntities: Boolean = false,
    val initializingTemplateProject: Boolean = false,
    val pendingDriveConsent: PendingIntent? = null,
    val pendingDriveAction: PendingDriveAction? = null,
    val lastDriveReport: DrivePushReport? = null,
    val lastGitHubReport: GitHubMirrorReport? = null,
    val githubUserRepos: List<GitHubRepoSummary> = emptyList(),
    val loadingGitHubRepos: Boolean = false,
    val githubDeviceSession: GitHubDeviceCodeSession? = null,
    val githubDevicePolling: Boolean = false,
    val customModelFolders: List<String> = ResearchProject.DEFAULT_ROUNDTABLE_FOLDERS,
    val lastInvite: ProjectInvite? = null,
    val bannerMessage: String? = null,
    val errorMessage: String? = null,
    val projectsLoaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class VortexViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = VortexFirestoreRepository(appContext)
    private val localDb = VortexLocalDatabase.getInstance(appContext)
    private val connectivityMonitor = NetworkConnectivityMonitor(appContext)
    private val offlineRepository = OfflineFirstRepository(
        localDb = localDb,
        remoteRepo = repository,
        connectivityMonitor = connectivityMonitor,
        scope = viewModelScope
    )
    private val prefsStore = LocalPreferencesStore(appContext)
    private val envVaultStore = EncryptedEnvVaultStore(appContext)
    private val mcpResultStore = McpFullResultStore(appContext.filesDir)
    private val mcpAuditLogStore = McpAuditLogStore(appContext.filesDir)
    private val mcpClient = McpJsonRpcClient(mcpResultStore, mcpAuditLogStore)
    private val externalRouterApi = ExternalRouterApi()
    private val modelRouter = VortexModelRouter(externalRouterApi)
    private val orchestrator = VortexPipelineOrchestrator(modelRouter)
    private val githubAuthManager = GitHubAuthManager()
    private val githubSyncEngine = GitHubGitSyncEngine(githubAuthManager)
    private val driveSyncService = GoogleDriveSyncService()
    private val artifactIngestor = UniversalArtifactIngestor(modelRouter)
    private var githubDevicePollJob: Job? = null
    private var lastStage0Triage: Stage0TriageResult? = null

    private val currentUidFlow = MutableStateFlow(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
    private val activeProjectIdFlow = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(VortexUiState())
    val uiState: StateFlow<VortexUiState> = _uiState.asStateFlow()

    val syncTelemetry: StateFlow<OfflineSyncTelemetry> = offlineRepository.telemetry
    val activeSyncConflict: StateFlow<FileSyncConflict?> = offlineRepository.activeConflict

    val envSecrets: StateFlow<List<EnvSecretEntry>> = envVaultStore.entries
    val envAutoFillResolution: StateFlow<DiscreteAutoFillResolution> = envVaultStore.entries
        .map { entries -> envVaultStore.resolveDiscreteAutoFill(entries) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            envVaultStore.resolveDiscreteAutoFill()
        )

    private val _mcpAuditLog = MutableStateFlow<List<McpAuditLogEntry>>(mcpAuditLogStore.loadAll())
    val mcpAuditLog: StateFlow<List<McpAuditLogEntry>> = _mcpAuditLog.asStateFlow()

    private val _selfHostedHealthResult = MutableStateFlow<SelfHostedHealthResult?>(null)
    val selfHostedHealthResult: StateFlow<SelfHostedHealthResult?> = _selfHostedHealthResult.asStateFlow()

    private val _checkingSelfHostedHealth = MutableStateFlow(false)
    val checkingSelfHostedHealth: StateFlow<Boolean> = _checkingSelfHostedHealth.asStateFlow()

    private val _resolvingConflict = MutableStateFlow(false)
    val resolvingConflict: StateFlow<Boolean> = _resolvingConflict.asStateFlow()

    val routingPreferences: StateFlow<RoutingPreferences> = combine(
        prefsStore.routingPreferences,
        envAutoFillResolution,
        envSecrets
    ) { prefs, autoFill, _ ->
        applyDiscreteAutoFillToPrefs(prefs, autoFill)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutingPreferences())

    val infraRegistryState: StateFlow<InfrastructureRegistryState> = prefsStore.infrastructureRegistry
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            InfrastructureRegistryEngine.defaultState()
        )

    val safFolderBindings: StateFlow<Map<String, String>> = prefsStore.safFolderBindings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val safSyncedHashes: StateFlow<Map<String, String>> = prefsStore.safSyncedHashes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _safRemoteHashes = MutableStateFlow<Map<String, String>>(emptyMap())
    val safRemoteHashes: StateFlow<Map<String, String>> = _safRemoteHashes.asStateFlow()

    val driveFolderIds: StateFlow<Map<String, String>> = prefsStore.driveFolderIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val driveSyncedHashes: StateFlow<Map<String, String>> = prefsStore.driveSyncedHashes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val driveLastSyncLabels: StateFlow<Map<String, String>> = prefsStore.driveLastSyncLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val githubSyncedHashes: StateFlow<Map<String, String>> = prefsStore.githubSyncedHashes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val githubLastSyncLabels: StateFlow<Map<String, String>> = prefsStore.githubLastSyncLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _githubLiveProgress = MutableStateFlow<GitHubSyncProgressUpdate?>(null)
    private val _githubLastError = MutableStateFlow<String?>(null)
    private val _driveLiveProgress = MutableStateFlow<DriveSyncProgressUpdate?>(null)
    private val _driveLastError = MutableStateFlow<String?>(null)

    val activeProjectPrds: StateFlow<List<PrdDocument>> = activeProjectIdFlow
        .flatMapLatest { projectId ->
            if (projectId.isNullOrBlank()) flowOf(emptyList())
            else offlineRepository.observeProjectPrds(projectId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeRoundtableItems: StateFlow<List<RoundtableItem>> = activeProjectIdFlow
        .flatMapLatest { projectId ->
            if (projectId.isNullOrBlank()) flowOf(emptyList())
            else offlineRepository.observeRoundtableItems(projectId)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val githubHashSnapshotFlow = combine(
        githubSyncedHashes,
        githubLastSyncLabels,
        _githubLiveProgress,
        _githubLastError
    ) { hashes, labels, progress, lastErr ->
        GitHubSyncMetaSnapshot(hashes, labels, progress, lastErr)
    }

    val githubHeaderSyncState: StateFlow<GitHubHeaderSyncState> = combine(
        _uiState,
        routingPreferences,
        activeProjectPrds,
        activeRoundtableItems,
        githubHashSnapshotFlow
    ) { state, prefs, prds, rtItems, meta ->
        val project = state.projects.firstOrNull { it.id == state.activeProjectId }
        val connected = prefs.githubPat.isNotBlank() && prefs.githubUsername.isNotBlank()
        val parsedSlug = project?.githubRepoUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { githubSyncEngine.parseOwnerRepo(it) }
            ?.let { (owner, repo) -> "$owner/$repo" }
            ?: ""
        val branch = project?.githubBranch?.ifBlank { "main" } ?: "main"
        val lastSummary = project?.id?.let { meta.lastLabels[it] }.orEmpty()

        val live = meta.liveProgress
        if (state.syncingGitHub || live != null) {
            val activePhase = live?.phase?.takeUnless { it == GitHubSyncPhase.IN_SYNC }
                ?: GitHubSyncPhase.PREPARING
            return@combine GitHubHeaderSyncState(
                phase = activePhase,
                direction = live?.direction ?: GitHubSyncDirection.PUSH,
                progressFraction = live?.progressFraction,
                completedFiles = live?.completedFiles ?: 0,
                totalFiles = live?.totalFiles ?: 0,
                currentStageLabel = live?.stageLabel?.ifBlank { "Synchronizing with GitHub..." }
                    ?: "Synchronizing with GitHub...",
                linkedRepoSlug = parsedSlug.ifBlank {
                    project?.rootFolderName?.takeIf { it.isNotBlank() }?.let {
                        if (prefs.githubUsername.isNotBlank()) "${prefs.githubUsername}/${githubSyncEngine.sanitizeRepoName(it)}" else it
                    }.orEmpty()
                },
                branch = branch,
                autoMirrorEnabled = prefs.githubAutoMirror,
                lastSyncSummary = lastSummary,
                errorMessage = null
            )
        }

        if (!connected) {
            return@combine GitHubHeaderSyncState(
                phase = GitHubSyncPhase.NOT_CONNECTED,
                linkedRepoSlug = parsedSlug,
                branch = branch,
                autoMirrorEnabled = prefs.githubAutoMirror,
                lastSyncSummary = lastSummary,
                errorMessage = meta.lastError
            )
        }

        if (!meta.lastError.isNullOrBlank()) {
            return@combine GitHubHeaderSyncState(
                phase = GitHubSyncPhase.ERROR,
                linkedRepoSlug = parsedSlug,
                branch = branch,
                autoMirrorEnabled = prefs.githubAutoMirror,
                lastSyncSummary = lastSummary,
                errorMessage = meta.lastError
            )
        }

        if (project == null || project.githubRepoUrl.isBlank()) {
            return@combine GitHubHeaderSyncState(
                phase = GitHubSyncPhase.UNLINKED,
                linkedRepoSlug = "",
                branch = branch,
                autoMirrorEnabled = prefs.githubAutoMirror,
                lastSyncSummary = lastSummary
            )
        }

        val lastHash = meta.syncedHashes[project.id]
        val phase = if (lastHash.isNullOrBlank()) {
            GitHubSyncPhase.LOCAL_AHEAD
        } else {
            val workspaceFiles = WorkspaceTemplateEngine.buildConsistentWorkspace(
                project = project,
                prds = prds,
                roundtableItems = rtItems
            ).files
            val currentHash = SafFolderSyncManager.computeProjectHash(workspaceFiles)
            if (currentHash == lastHash) GitHubSyncPhase.IN_SYNC else GitHubSyncPhase.LOCAL_AHEAD
        }

        GitHubHeaderSyncState(
            phase = phase,
            linkedRepoSlug = parsedSlug,
            branch = branch,
            autoMirrorEnabled = prefs.githubAutoMirror,
            lastSyncSummary = lastSummary
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GitHubHeaderSyncState())

    private val driveMetaSnapshotFlow = combine(
        driveFolderIds,
        driveSyncedHashes,
        driveLastSyncLabels,
        _driveLiveProgress,
        _driveLastError
    ) { folderIds, hashes, labels, progress, lastErr ->
        DriveSyncMetaSnapshot(folderIds, hashes, labels, progress, lastErr)
    }

    val driveHeaderSyncState: StateFlow<DriveHeaderSyncState> = combine(
        _uiState,
        syncTelemetry,
        activeProjectPrds,
        activeRoundtableItems,
        driveMetaSnapshotFlow
    ) { state, telemetry, prds, rtItems, meta ->
        val project = state.projects.firstOrNull { it.id == state.activeProjectId }
        val folderId = project?.id?.let { meta.folderIds[it] }.orEmpty()
        val lastSummary = project?.id?.let { meta.lastLabels[it] }.orEmpty()

        val live = meta.liveProgress
        if (state.syncingDrive || live != null) {
            return@combine DriveHeaderSyncState(
                phase = DriveSyncPhase.SYNCING,
                direction = live?.direction ?: DriveSyncDirection.PUSH,
                progressFraction = live?.progressFraction,
                completedFiles = live?.completedFiles ?: 0,
                totalFiles = live?.totalFiles ?: 0,
                currentStageLabel = live?.stageLabel?.ifBlank { "Mirroring with Google Drive..." }
                    ?: "Mirroring with Google Drive...",
                lastSyncSummary = lastSummary,
                folderId = folderId,
                errorMessage = null
            )
        }

        if (!telemetry.isOnline) {
            return@combine DriveHeaderSyncState(
                phase = DriveSyncPhase.OFFLINE,
                lastSyncSummary = lastSummary,
                folderId = folderId,
                errorMessage = meta.lastError
            )
        }

        if (!meta.lastError.isNullOrBlank()) {
            return@combine DriveHeaderSyncState(
                phase = DriveSyncPhase.ERROR,
                lastSyncSummary = lastSummary,
                folderId = folderId,
                errorMessage = meta.lastError
            )
        }

        if (project == null || folderId.isBlank()) {
            return@combine DriveHeaderSyncState(
                phase = DriveSyncPhase.NOT_LINKED,
                lastSyncSummary = lastSummary,
                folderId = ""
            )
        }

        val lastHash = meta.syncedHashes[project.id]
        val phase = if (lastHash.isNullOrBlank()) {
            DriveSyncPhase.LOCAL_AHEAD
        } else {
            val currentSha = WorkspaceTemplateEngine.buildConsistentWorkspace(
                project = project,
                prds = prds,
                roundtableItems = rtItems
            ).workspaceSha256
            if (currentSha == lastHash) DriveSyncPhase.IN_SYNC else DriveSyncPhase.LOCAL_AHEAD
        }

        DriveHeaderSyncState(
            phase = phase,
            lastSyncSummary = lastSummary,
            folderId = folderId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveHeaderSyncState())

    private val dashboardCrossProjectDataFlow = combine(
        offlineRepository.observeAllPrds().catch { emit(emptyList()) },
        offlineRepository.observeAllRoundtableItems().catch { emit(emptyList()) },
        offlineRepository.observePrdCountsByProject().catch { emit(emptyMap()) },
        offlineRepository.observeRoundtableCountsByProject().catch { emit(emptyMap()) }
    ) { allPrds, allRt, prdCounts, rtCounts ->
        DashboardCrossProjectSnapshot(allPrds, allRt, prdCounts, rtCounts)
    }

    private val dashboardSyncBindingsFlow = combine(
        safFolderBindings,
        safSyncedHashes,
        githubHashSnapshotFlow,
        driveMetaSnapshotFlow
    ) { safBindings, safHashes, ghMeta, driveMeta ->
        DashboardSyncBindingsSnapshot(safBindings, safHashes, ghMeta, driveMeta)
    }

    val dashboardState: StateFlow<DashboardUiState> = combine(
        _uiState,
        syncTelemetry,
        routingPreferences,
        dashboardCrossProjectDataFlow,
        dashboardSyncBindingsFlow
    ) { state, telemetry, prefs, crossData, bindings ->
        val uid = currentUidFlow.value
        val projects = state.projects
        val projectsById = projects.associateBy { it.id }
        val prdsByProject = crossData.allPrds.groupBy { it.projectId }
        val rtByProject = crossData.allRoundtable.groupBy { it.projectId }
        val ghConnected = prefs.githubPat.isNotBlank() && prefs.githubUsername.isNotBlank()

        var ghLinkedCount = 0
        var driveLinkedCount = 0
        var localBoundCount = 0
        var pendingCount = 0

        val summaries = projects.map { project ->
            val isActive = project.id == state.activeProjectId
            val projPrds = prdsByProject[project.id].orEmpty()
            val projRt = rtByProject[project.id].orEmpty()
            val selectableFiles = WorkspaceTemplateEngine.buildConsistentWorkspace(
                project = project,
                prds = projPrds,
                roundtableItems = projRt
            ).files
            val fileCount = selectableFiles.size
            val prdCount = crossData.prdCounts[project.id]
                ?: if (project.latestPrdVersion.isNotBlank()) 1 else 0
            val rtCount = crossData.roundtableCounts[project.id] ?: 0
            val isDirtyRoom = project.id in telemetry.dirtyProjectIds
            val hasConflict = telemetry.hasActiveConflict &&
                activeSyncConflict.value?.projectId == project.id

            // 1. GitHub Sync Badge
            val ghBadge = computeProjectGitHubBadge(
                project = project,
                isActive = isActive,
                syncingGitHub = state.syncingGitHub && isActive,
                ghConnected = ghConnected,
                ghMeta = bindings.ghMeta,
                projectFiles = selectableFiles
            )
            if (project.githubRepoUrl.isNotBlank()) ghLinkedCount++

            // 2. Google Drive Sync Badge
            val driveFolderId = bindings.driveMeta.folderIds[project.id].orEmpty()
            val driveBadge = computeProjectDriveBadge(
                project = project,
                isActive = isActive,
                syncingDrive = state.syncingDrive && isActive,
                isOnline = telemetry.isOnline,
                driveFolderId = driveFolderId,
                driveMeta = bindings.driveMeta
            )
            if (driveFolderId.isNotBlank()) driveLinkedCount++

            // 3. Local (Room + SAF Folder) Sync Badge
            val safBound = !bindings.safBindings[project.id].isNullOrBlank()
            if (safBound) localBoundCount++
            val localBadge = computeProjectLocalBadge(
                project = project,
                isActive = isActive,
                syncingSaf = state.syncingSaf && isActive,
                isOnline = telemetry.isOnline,
                isSyncingRoom = telemetry.isSyncing,
                isDirtyRoom = isDirtyRoom,
                hasConflict = hasConflict,
                safBound = safBound,
                safSyncedHash = bindings.safHashes[project.id],
                projectFiles = selectableFiles
            )

            val needsSync = isDirtyRoom ||
                hasConflict ||
                ghBadge.health == SyncChannelHealth.AHEAD_PENDING ||
                driveBadge.health == SyncChannelHealth.AHEAD_PENDING ||
                localBadge.health == SyncChannelHealth.AHEAD_PENDING
            if (needsSync) pendingCount++

            val parsedChats = project.parsedChatHistory()
            val chatCount = if (parsedChats.isNotEmpty()) {
                parsedChats.count { it.role.equals("USER", ignoreCase = true) }.coerceAtLeast(1)
            } else if (project.chatAnswer.isNotBlank() || project.query.isNotBlank()) {
                1
            } else {
                0
            }

            DashboardProjectSummary(
                project = project,
                isActive = isActive,
                role = project.roleFor(uid),
                fileCount = fileCount,
                prdCount = prdCount,
                roundtableCount = rtCount,
                chatCount = chatCount,
                selectableFiles = selectableFiles,
                githubBadge = ghBadge,
                driveBadge = driveBadge,
                localBadge = localBadge,
                hasPendingSync = needsSync,
                updatedLabel = formatDashboardTimestamp(
                    project.updatedAt?.toDate()?.time ?: project.createdAt?.toDate()?.time
                )
            )
        }

        val summariesById = summaries.associateBy { it.project.id }

        val recentPrdCards = crossData.allPrds
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            .mapNotNull { prd ->
                val proj = projectsById[prd.projectId] ?: return@mapNotNull null
                val bp = ScaffoldingBlueprint.fromWire(proj.blueprintId)
                val ext = ExportFileFormat.fromWire(proj.fileFormat).extension
                val expectedPath = "${bp.prdFolder}/PRD_${prd.version}.$ext"
                val matchedPath = summariesById[proj.id]?.selectableFiles
                    ?.firstOrNull {
                        it.relativePath.equals(expectedPath, ignoreCase = true) ||
                            it.relativePath.contains("PRD_${prd.version}", ignoreCase = true)
                    }
                    ?.relativePath
                    ?: expectedPath
                RecentPrdCardItem(
                    prd = prd,
                    project = proj,
                    bumpLabel = VortexFirestoreRepository.versionBumpLabel(prd),
                    timestampLabel = formatDashboardTimestamp(prd.createdAt?.toDate()?.time),
                    archiveRelativePath = matchedPath
                )
            }
            .take(10)

        val recentRtCards = crossData.allRoundtable
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
            .mapNotNull { item ->
                val proj = projectsById[item.projectId] ?: return@mapNotNull null
                val bp = ScaffoldingBlueprint.fromWire(proj.blueprintId)
                val folder = item.modelFolder.ifBlank { "External_Model" }
                val expectedPath = "${bp.roundtableFolder}/$folder/${item.fileName}"
                val matchedPath = summariesById[proj.id]?.selectableFiles
                    ?.firstOrNull {
                        it.relativePath.equals(expectedPath, ignoreCase = true) ||
                            it.relativePath.endsWith("/$folder/${item.fileName}", ignoreCase = true)
                    }
                    ?.relativePath
                    ?: expectedPath
                RecentRoundtableCardItem(
                    item = item,
                    project = proj,
                    status = RoundtableStatus.fromWire(item.queueStatus),
                    timestampLabel = formatDashboardTimestamp(
                        item.updatedAt?.toDate()?.time ?: item.createdAt?.toDate()?.time
                    ),
                    archiveRelativePath = matchedPath
                )
            }
            .take(10)

        DashboardUiState(
            projects = summaries,
            recentPrds = recentPrdCards,
            recentRoundtable = recentRtCards,
            totalProjects = projects.size,
            githubLinkedCount = ghLinkedCount,
            driveLinkedCount = driveLinkedCount,
            localBoundCount = localBoundCount,
            pendingSyncProjectsCount = pendingCount,
            totalPrdsCount = crossData.prdCounts.values.sum().coerceAtLeast(recentPrdCards.size),
            totalRoundtableCount = crossData.roundtableCounts.values.sum().coerceAtLeast(recentRtCards.size),
            isLoaded = state.projectsLoaded
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun computeProjectGitHubBadge(
        project: ResearchProject,
        isActive: Boolean,
        syncingGitHub: Boolean,
        ghConnected: Boolean,
        ghMeta: GitHubSyncMetaSnapshot,
        projectFiles: List<ProjectFile>
    ): ProjectSyncBadge {
        if (syncingGitHub) {
            val pct = ghMeta.liveProgress?.progressFraction?.let { "${(it * 100).toInt()}%" } ?: "…"
            return ProjectSyncBadge(
                label = "Syncing $pct",
                detail = ghMeta.liveProgress?.stageLabel?.ifBlank { "Mirroring to GitHub" } ?: "Mirroring to GitHub",
                health = SyncChannelHealth.SYNCING
            )
        }
        if (isActive && !ghMeta.lastError.isNullOrBlank()) {
            return ProjectSyncBadge(
                label = "Error",
                detail = ghMeta.lastError.take(40),
                health = SyncChannelHealth.ERROR
            )
        }
        if (!ghConnected) {
            return ProjectSyncBadge(
                label = if (project.githubRepoUrl.isNotBlank()) "Auth Needed" else "Setup",
                detail = if (project.githubRepoUrl.isNotBlank()) "Reconnect GitHub" else "Connect GitHub",
                health = SyncChannelHealth.UNLINKED
            )
        }
        if (project.githubRepoUrl.isBlank()) {
            return ProjectSyncBadge(
                label = "Unlinked",
                detail = "Tap to mirror",
                health = SyncChannelHealth.UNLINKED
            )
        }
        val slug = githubSyncEngine.parseOwnerRepo(project.githubRepoUrl)
            ?.let { (owner, repo) -> "$owner/$repo" }
            ?: project.rootFolderName
        val lastHash = ghMeta.syncedHashes[project.id]
        val lastLabel = ghMeta.lastLabels[project.id].orEmpty()
        if (lastHash.isNullOrBlank()) {
            return ProjectSyncBadge(
                label = "Ahead",
                detail = slug,
                health = SyncChannelHealth.AHEAD_PENDING
            )
        }
        val currentHash = if (isActive) {
            SafFolderSyncManager.computeProjectHash(
                WorkspaceTemplateEngine.buildConsistentWorkspace(
                    project = project,
                    prds = activeProjectPrds.value,
                    roundtableItems = activeRoundtableItems.value
                ).files
            )
        } else {
            SafFolderSyncManager.computeProjectHash(projectFiles)
        }
        return if (currentHash == lastHash || (!isActive && lastLabel.isNotBlank())) {
            ProjectSyncBadge(
                label = "Synced",
                detail = lastLabel.ifBlank { slug },
                health = SyncChannelHealth.SYNCED
            )
        } else {
            ProjectSyncBadge(
                label = "Ahead",
                detail = slug,
                health = SyncChannelHealth.AHEAD_PENDING
            )
        }
    }

    private fun computeProjectDriveBadge(
        project: ResearchProject,
        isActive: Boolean,
        syncingDrive: Boolean,
        isOnline: Boolean,
        driveFolderId: String,
        driveMeta: DriveSyncMetaSnapshot
    ): ProjectSyncBadge {
        if (syncingDrive) {
            val pct = driveMeta.liveProgress?.progressFraction?.let { "${(it * 100).toInt()}%" } ?: "…"
            return ProjectSyncBadge(
                label = "Syncing $pct",
                detail = driveMeta.liveProgress?.stageLabel?.ifBlank { "Mirroring Drive" } ?: "Mirroring Drive",
                health = SyncChannelHealth.SYNCING
            )
        }
        if (isActive && !driveMeta.lastError.isNullOrBlank()) {
            return ProjectSyncBadge(
                label = "Error",
                detail = driveMeta.lastError.take(40),
                health = SyncChannelHealth.ERROR
            )
        }
        if (driveFolderId.isBlank()) {
            return ProjectSyncBadge(
                label = "Unlinked",
                detail = "Tap to mirror",
                health = SyncChannelHealth.UNLINKED
            )
        }
        if (!isOnline) {
            return ProjectSyncBadge(
                label = "Offline",
                detail = driveMeta.lastLabels[project.id]?.ifBlank { "Paused offline" } ?: "Paused offline",
                health = SyncChannelHealth.OFFLINE
            )
        }
        val lastHash = driveMeta.syncedHashes[project.id]
        val lastLabel = driveMeta.lastLabels[project.id].orEmpty()
        if (lastHash.isNullOrBlank()) {
            return ProjectSyncBadge(
                label = "Ahead",
                detail = "Ready to push",
                health = SyncChannelHealth.AHEAD_PENDING
            )
        }
        if (isActive) {
            val currentSha = WorkspaceTemplateEngine.buildConsistentWorkspace(
                project = project,
                prds = activeProjectPrds.value,
                roundtableItems = activeRoundtableItems.value
            ).workspaceSha256
            return if (currentSha == lastHash) {
                ProjectSyncBadge(
                    label = "Synced",
                    detail = lastLabel.ifBlank { "Drive mirrored" },
                    health = SyncChannelHealth.SYNCED
                )
            } else {
                ProjectSyncBadge(
                    label = "Ahead",
                    detail = "Local updates ready",
                    health = SyncChannelHealth.AHEAD_PENDING
                )
            }
        }
        return ProjectSyncBadge(
            label = "Synced",
            detail = lastLabel.ifBlank { "Drive mirrored" },
            health = SyncChannelHealth.SYNCED
        )
    }

    private fun computeProjectLocalBadge(
        project: ResearchProject,
        isActive: Boolean,
        syncingSaf: Boolean,
        isOnline: Boolean,
        isSyncingRoom: Boolean,
        isDirtyRoom: Boolean,
        hasConflict: Boolean,
        safBound: Boolean,
        safSyncedHash: String?,
        projectFiles: List<ProjectFile>
    ): ProjectSyncBadge {
        if (hasConflict) {
            return ProjectSyncBadge(
                label = "Conflict",
                detail = "Resolve divergence",
                health = SyncChannelHealth.ERROR
            )
        }
        if (syncingSaf || (isSyncingRoom && isDirtyRoom)) {
            return ProjectSyncBadge(
                label = "Syncing",
                detail = if (syncingSaf) "SAF folder mirror" else "Flushing Room",
                health = SyncChannelHealth.SYNCING
            )
        }
        if (isDirtyRoom) {
            return ProjectSyncBadge(
                label = if (isOnline) "Queued" else "Offline Edits",
                detail = "Room outbox pending",
                health = SyncChannelHealth.AHEAD_PENDING
            )
        }
        if (!isOnline) {
            return ProjectSyncBadge(
                label = "Offline",
                detail = if (safBound) "Room + SAF ready" else "Room cached",
                health = SyncChannelHealth.OFFLINE
            )
        }
        if (safBound) {
            val currentHash = SafFolderSyncManager.computeProjectHash(projectFiles)
            val safInSync = !safSyncedHash.isNullOrBlank() && (safSyncedHash == currentHash || !isActive)
            return if (safInSync) {
                ProjectSyncBadge(
                    label = "Room + SAF",
                    detail = "Cached & mirrored",
                    health = SyncChannelHealth.SYNCED
                )
            } else {
                ProjectSyncBadge(
                    label = "SAF Ahead",
                    detail = "Push to local folder",
                    health = SyncChannelHealth.AHEAD_PENDING
                )
            }
        }
        return ProjectSyncBadge(
            label = "Cached",
            detail = "Room · Live cloud",
            health = SyncChannelHealth.SYNCED
        )
    }

    init {
        // One-time verified migration of legacy plaintext DataStore secrets into AES-256-GCM vault
        viewModelScope.launch {
            val report = SecretVaultMigration.migrateIfNeeded(prefsStore, envVaultStore)
            if (report.errorMessage != null) {
                _uiState.update { it.copy(errorMessage = "Secret vault migration note: ${report.errorMessage}") }
            }
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        currentUidFlow.value = uid
        if (uid.isNotBlank()) {
            // Hydrate Room cache from Firestore whenever online
            offlineRepository.startUserProjectHydration(uid)

            // Hydrate PRDs & Roundtable subcollections for whichever project is selected
            viewModelScope.launch {
                activeProjectIdFlow.collect { projectId ->
                    offlineRepository.startProjectDetailsHydration(projectId)
                }
            }

            // Observe Room as Single Source of Truth so offline browsing & file edits work seamlessly
            viewModelScope.launch {
                offlineRepository.observeUserProjects(uid)
                    .catch { err ->
                        _uiState.update {
                            it.copy(
                                projectsLoaded = true,
                                errorMessage = "Local cache warning: ${err.message?.take(120)}"
                            )
                        }
                    }
                    .collect { projects ->
                        val currentSelected = activeProjectIdFlow.value
                        val resolvedId = when {
                            currentSelected != null && projects.any { it.id == currentSelected } -> currentSelected
                            projects.isNotEmpty() -> projects.first().id
                            else -> null
                        }
                        if (resolvedId != activeProjectIdFlow.value) {
                            activeProjectIdFlow.value = resolvedId
                        }
                        _uiState.update { state ->
                            val selectedProj = projects.firstOrNull { it.id == resolvedId }
                            state.copy(
                                projects = projects,
                                activeProjectId = resolvedId,
                                projectsLoaded = true,
                                liveStages = if (state.pipelineRunning) state.liveStages
                                else selectedProj?.parsedStages().orEmpty()
                            )
                        }
                        if (projects.isNotEmpty()) {
                            offlineRepository.hydrateDashboardProjects(projects.map { it.id })
                        }
                    }
            }

            // Surface automatic background sync completion banners
            viewModelScope.launch {
                var lastShownSummary = ""
                offlineRepository.telemetry.collect { telemetry ->
                    if (telemetry.lastSyncSummary.isNotBlank() && telemetry.lastSyncSummary != lastShownSummary) {
                        lastShownSummary = telemetry.lastSyncSummary
                        _uiState.update { it.copy(bannerMessage = telemetry.lastSyncSummary) }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        githubDevicePollJob?.cancel()
        offlineRepository.close()
        super.onCleared()
    }

    fun activeProject(): ResearchProject? =
        _uiState.value.projects.firstOrNull { it.id == _uiState.value.activeProjectId }

    fun currentUid(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun currentDisplayName(): String =
        FirebaseAuth.getInstance().currentUser?.displayName?.ifBlank { "Researcher" } ?: "Researcher"

    fun clearBanner() = _uiState.update { it.copy(bannerMessage = null, errorMessage = null) }

    fun updateDraftQuery(value: String) = _uiState.update { it.copy(draftQuery = value) }
    fun updateDraftMode(mode: ResearchMode) = _uiState.update { it.copy(draftMode = mode) }
    fun updateDraftFormat(format: ExportFileFormat) = _uiState.update { it.copy(draftFileFormat = format) }
    fun updateDraftBlueprint(bp: ScaffoldingBlueprint) = _uiState.update { it.copy(draftBlueprint = bp) }
    fun updateDraftRootFolder(name: String) = _uiState.update { it.copy(draftRootFolder = name) }

    fun startNewSession() {
        activeProjectIdFlow.value = null
        lastStage0Triage = null
        _uiState.update {
            it.copy(
                activeProjectId = null,
                draftQuery = "",
                draftRootFolder = "",
                recommendedBlueprint = null,
                liveStages = emptyList(),
                ragAnswer = "",
                lastInvite = null,
                lastVoiceNoteResult = null,
                preVoiceNoteQuerySnapshot = null,
                voiceNoteError = null
            )
        }
    }

    /**
     * Transcribes a recorded microphone audio clip using Firebase AI Logic (Gemini 3.8 Flash)
     * into both a verbatim transcript and a structured synthesis brief, merges it into the
     * research composer, optionally saves a timestamped verbal note into the active project's
     * `/research/voice_notes/` folder, and optionally launches the 7-stage synthesis pipeline.
     */
    fun transcribeRecordedAudioNote(
        audioBytes: ByteArray,
        mimeType: String,
        autoRunPipeline: Boolean,
        saveNoteToRepo: Boolean
    ) {
        if (_uiState.value.transcribingVoiceNote || _uiState.value.pipelineRunning) return
        val priorQuery = _uiState.value.draftQuery
        val mode = _uiState.value.draftMode

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    transcribingVoiceNote = true,
                    voiceNoteError = null,
                    errorMessage = null
                )
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    modelRouter.transcribeVoiceNoteWithGemini(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        modeLabel = mode.label,
                        existingBrief = priorQuery
                    )
                }

                val mergedQuery = VortexSpeechController.mergeTranscriptIntoField(
                    existing = priorQuery,
                    spoken = result.refinedResearchBrief
                )
                val shouldFillFolder = mode == ResearchMode.PROJECT && _uiState.value.draftRootFolder.isBlank()

                _uiState.update {
                    it.copy(
                        transcribingVoiceNote = false,
                        draftQuery = mergedQuery,
                        draftRootFolder = if (shouldFillFolder) result.suggestedFolderSlug else it.draftRootFolder,
                        lastVoiceNoteResult = result,
                        lastVoiceNoteUsingBrief = true,
                        preVoiceNoteQuerySnapshot = priorQuery,
                        voiceNoteError = null,
                        bannerMessage = if (autoRunPipeline) {
                            "Tagged '${result.category.label}' via Firebase AI (${result.modelUsed}) — starting 7-stage synthesis…"
                        } else {
                            "Tagged '${result.category.label}' via Firebase AI (${result.modelUsed}) — review or run synthesis."
                        }
                    )
                }

                if (saveNoteToRepo && activeProject() != null) {
                    persistVoiceNoteMarkdown(result)
                }

                if (autoRunPipeline) {
                    runResearchPipeline()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("VortexViewModel", "Firebase AI voice transcription timed out", e)
                val msg = "Audio transcription timed out after 60 seconds. Try recording a shorter verbal note."
                _uiState.update {
                    it.copy(
                        transcribingVoiceNote = false,
                        voiceNoteError = msg,
                        errorMessage = msg
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VortexViewModel", "Firebase AI voice transcription failed", e)
                val msg = e.localizedMessage ?: "Firebase AI failed to transcribe the audio recording."
                _uiState.update {
                    it.copy(
                        transcribingVoiceNote = false,
                        voiceNoteError = msg,
                        errorMessage = msg
                    )
                }
            }
        }
    }

    fun toggleVoiceNoteTranscriptMode(useRefinedBrief: Boolean) {
        val state = _uiState.value
        val result = state.lastVoiceNoteResult ?: return
        val base = state.preVoiceNoteQuerySnapshot.orEmpty()
        val chosen = if (useRefinedBrief) result.refinedResearchBrief else result.verbatimTranscript
        val merged = VortexSpeechController.mergeTranscriptIntoField(base, chosen)
        _uiState.update {
            it.copy(
                draftQuery = merged,
                lastVoiceNoteUsingBrief = useRefinedBrief
            )
        }
    }

    fun undoLastVoiceNoteTranscript() {
        val snapshot = _uiState.value.preVoiceNoteQuerySnapshot
        _uiState.update {
            it.copy(
                draftQuery = snapshot ?: it.draftQuery,
                lastVoiceNoteResult = null,
                preVoiceNoteQuerySnapshot = null,
                voiceNoteError = null
            )
        }
    }

    fun saveLastVoiceNoteToRepo() {
        val result = _uiState.value.lastVoiceNoteResult ?: return
        if (activeProject() == null) {
            _uiState.update { it.copy(bannerMessage = "Open or run a project first to save voice notes into the repository.") }
            return
        }
        persistVoiceNoteMarkdown(result)
    }

    fun updateLastVoiceNoteCategory(category: NoteCategory) {
        val current = _uiState.value.lastVoiceNoteResult ?: return
        val updatedTags = MetadataTagEngine.normalizeTags(
            raw = listOf(category.label) + current.tags.filterNot { existing ->
                NoteCategory.entries.any { it.label.equals(existing, ignoreCase = true) }
            },
            maxCount = 8
        )
        _uiState.update {
            it.copy(
                lastVoiceNoteResult = current.copy(
                    category = category,
                    tags = updatedTags
                )
            )
        }
    }

    fun dismissVoiceNoteReview() {
        _uiState.update {
            it.copy(
                lastVoiceNoteResult = null,
                preVoiceNoteQuerySnapshot = null,
                voiceNoteError = null
            )
        }
    }

    private fun persistVoiceNoteMarkdown(result: AudioTranscriptionResult) {
        val slug = modelRouter.sanitizeFolderName(result.suggestedTitle).take(32)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val path = "research/voice_notes/${stamp}_${slug}.md"
        val tagsLine = MetadataTagEngine.normalizeTags(
            listOf(result.category.label) + result.tags,
            maxCount = 8
        ).joinToString(" ") { "#${it.replace(" ", "-")}" }
        val body = buildString {
            appendLine("# Voice Note: ${result.suggestedTitle}")
            appendLine()
            appendLine("- **Category:** ${result.category.label}")
            if (tagsLine.isNotBlank()) {
                appendLine("- **Tags:** $tagsLine")
            }
            appendLine("- **Transcribed by:** Firebase AI Logic (`${result.modelUsed}`)")
            appendLine("- **Captured:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}")
            appendLine()
            appendLine("## Synthesized Research Brief")
            appendLine(result.refinedResearchBrief)
            if (result.keyIdeas.isNotEmpty()) {
                appendLine()
                appendLine("## Key Ideas Extracted")
                result.keyIdeas.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## Verbatim Transcript")
            appendLine(result.verbatimTranscript)
        }
        createRepoFile(path, body)
        mergeTagsIntoActiveProject(listOf(result.category.label) + result.tags)
    }

    private fun mergeTagsIntoActiveProject(newTags: List<String>) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (uid.isBlank() || !project.canEdit(uid)) return
        val merged = MetadataTagEngine.normalizeTags(
            raw = project.tags + newTags,
            maxCount = 24
        )
        if (merged == project.tags) return
        viewModelScope.launch {
            runCatching {
                offlineRepository.saveProjectContentOfflineFirst(project.copy(tags = merged), uid)
            }
        }
    }

    /**
     * Initializes a brand-new project pre-scaffolded with a standardized 'Systems Architect'
     * folder template (e.g. /docs, /docs/adr, /src, /prd, /research, /roundtable) and persists
     * it to Firestore + Room cache with zero chat turns (chats stay hidden until opened).
     */
    fun initializeProjectFromTemplate(
        title: String,
        rootFolderName: String,
        blueprint: ScaffoldingBlueprint,
        fileFormat: ExportFileFormat = ExportFileFormat.MD,
        domain: String = "Systems Architecture",
        extraFolders: List<String> = emptyList(),
        openAfterCreate: Boolean = true,
        onInitialized: (ResearchProject) -> Unit = {}
    ) {
        val uid = currentUid()
        if (uid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Sign in to initialize a new project workspace.") }
            return
        }
        if (_uiState.value.initializingTemplateProject) return

        val cleanTitle = title.trim().ifBlank { "Systems Architecture Project" }.take(180)
        val cleanRoot = WorkspaceTemplateEngine.sanitizeRootFolderSlug(
            raw = rootFolderName.ifBlank { cleanTitle },
            fallback = "systems-project"
        )
        val cleanDomain = domain.trim().ifBlank { "Systems Architecture" }.take(120)

        viewModelScope.launch {
            _uiState.update { it.copy(initializingTemplateProject = true, errorMessage = null) }
            try {
                val starterFiles = WorkspaceTemplateEngine.buildInitialTemplateFiles(
                    projectTitle = cleanTitle,
                    rootFolderName = cleanRoot,
                    domain = cleanDomain,
                    blueprint = blueprint,
                    fileFormat = fileFormat,
                    extraFolders = extraFolders
                )

                val autoTags = MetadataTagEngine.deriveProjectTags(
                    title = cleanTitle,
                    domain = cleanDomain,
                    blueprint = blueprint,
                    existingTags = extraFolders,
                    maxCount = 12
                )

                val draftProject = ResearchProject(
                    ownerId = uid,
                    title = cleanTitle,
                    rootFolderName = cleanRoot,
                    mode = ResearchMode.PROJECT.wire,
                    fileFormat = fileFormat.wire,
                    blueprintId = blueprint.wire,
                    query = "",
                    complexityScore = 0,
                    detectedDomain = cleanDomain,
                    routingPlanSummary = "Initialized via ${blueprint.title} Systems Architect template",
                    pipelineStatus = PipelineStatus.IDLE.wire,
                    gapVerdict = GapVerdict.NONE.wire,
                    stagesJson = "[]",
                    filesJson = VortexJson.encodeFiles(starterFiles),
                    chatAnswer = "",
                    chatHistoryJson = "[]",
                    tags = autoTags,
                    memberUids = listOf(uid),
                    memberRoles = mapOf(uid to TeamRole.ADMIN.wire),
                    memberDisplayNames = mapOf(uid to currentDisplayName())
                )

                val builtWorkspace = WorkspaceTemplateEngine.buildConsistentWorkspace(
                    project = draftProject,
                    prds = emptyList(),
                    roundtableItems = emptyList(),
                    overrideBlueprint = blueprint
                )

                val finalProject = draftProject.copy(
                    filesJson = VortexJson.encodeFiles(builtWorkspace.files)
                )

                val newId = repository.createProject(finalProject).getOrThrow()
                val savedProject = finalProject.copy(id = newId)
                offlineRepository.cacheSyncedProject(savedProject, uid)

                if (openAfterCreate) {
                    selectProject(savedProject)
                }

                val totalFolders = (builtWorkspace.canonicalFolders + extraFolders).distinctBy { it.lowercase() }.size
                _uiState.update {
                    it.copy(
                        initializingTemplateProject = false,
                        bannerMessage = "Initialized '${savedProject.title}' (${blueprint.title} · $totalFolders folders, ${builtWorkspace.files.size} files)."
                    )
                }
                onInitialized(savedProject)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        initializingTemplateProject = false,
                        errorMessage = "Could not initialize template workspace: ${e.message?.take(140) ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun selectProject(project: ResearchProject) {
        activeProjectIdFlow.value = project.id
        val parsed = project.parsedStages()
        val inferredRec = inferRecommendedBlueprint(parsed)
        lastStage0Triage = if (project.detectedDomain.isNotBlank()) {
            Stage0TriageResult(
                complexityScore = project.complexityScore.coerceIn(1, 10),
                detectedDomain = project.detectedDomain,
                recommendedBlueprint = inferredRec ?: ScaffoldingBlueprint.fromWire(project.blueprintId),
                suggestedRootFolder = project.rootFolderName,
                projectTitle = project.title,
                escalationReason = project.routingPlanSummary
            )
        } else null
        _uiState.update {
            it.copy(
                activeProjectId = project.id,
                draftQuery = project.query,
                draftMode = ResearchMode.fromWire(project.mode),
                draftFileFormat = ExportFileFormat.fromWire(project.fileFormat),
                draftBlueprint = ScaffoldingBlueprint.fromWire(project.blueprintId),
                recommendedBlueprint = inferredRec,
                draftRootFolder = project.rootFolderName,
                liveStages = parsed,
                ragAnswer = ""
            )
        }
        refreshSafFolderStatus(project)
    }

    private fun inferRecommendedBlueprint(stages: List<StageRecord>): ScaffoldingBlueprint? {
        val stage0Text = stages.firstOrNull { it.stageNumber == 0 }?.content.orEmpty()
        if (stage0Text.isBlank()) return null
        return ScaffoldingBlueprint.entries.firstOrNull { bp ->
            stage0Text.contains(bp.title, ignoreCase = true) || stage0Text.contains(bp.wire, ignoreCase = true)
        }
    }

    fun runResearchPipeline() {
        executePipelineFromPass(startPass = 0)
    }

    fun retryPipelineFromStage(stageNumber: Int) {
        val pass = when (stageNumber) {
            0 -> 0
            in 1..3 -> 1
            in 4..5 -> 2
            in 6..7 -> 3
            else -> 0
        }
        executePipelineFromPass(startPass = pass)
    }

    private fun executePipelineFromPass(startPass: Int) {
        val state = _uiState.value
        val query = state.draftQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a research query before running the pipeline.") }
            return
        }
        val uid = currentUid()
        if (uid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Sign in with Google to run and sync research.") }
            return
        }
        if (state.pipelineRunning) return

        viewModelScope.launch {
            val statusMsg = if (startPass == 0) {
                "Running Stage 0 Triage & 7-Stage Grounded Pipeline..."
            } else {
                "Retrying from Pass $startPass (preserving completed prior stages)..."
            }
            _uiState.update {
                it.copy(
                    pipelineRunning = true,
                    errorMessage = null,
                    bannerMessage = statusMsg
                )
            }
            try {
                val prefs = resolveEffectiveRoutingPreferences()
                val detectedLangs = activeRoundtableItems.value
                    .map { it.detectedLanguage }
                    .filter { it !in listOf("Markdown", "Plain Text", "Text") }
                    .distinct()
                val envPolicy = buildAgentRuntimeContext(stageId = null)

                val output = orchestrator.runFromPass(
                    startPass = startPass,
                    existingStages = if (startPass == 0) VortexPipelineOrchestrator.initialSkeleton() else state.liveStages,
                    existingTriage = if (startPass == 0) null else lastStage0Triage,
                    query = query,
                    mode = state.draftMode,
                    fileFormat = state.draftFileFormat,
                    selectedBlueprint = state.draftBlueprint,
                    rootFolderName = state.draftRootFolder,
                    prefs = prefs,
                    detectedLanguages = detectedLangs,
                    envPolicyContext = envPolicy,
                    stageContextProvider = { stageId -> buildAgentRuntimeContext(stageId) },
                    onProgress = { stages, triage ->
                        if (triage != null) lastStage0Triage = triage
                        _uiState.update { cur ->
                            cur.copy(
                                liveStages = stages,
                                recommendedBlueprint = triage?.recommendedBlueprint ?: cur.recommendedBlueprint,
                                draftRootFolder = cur.draftRootFolder.ifBlank {
                                    triage?.suggestedRootFolder.orEmpty()
                                }
                            )
                        }
                    }
                )

                val existing = activeProject()
                val finalRoot = state.draftRootFolder.trim().ifBlank { output.triage.suggestedRootFolder }
                val allSources = output.stages.flatMap { it.sources }.distinctBy { it.uri }
                val stage4 = output.stages.firstOrNull { it.stageNumber == 4 }
                val primaryModel = stage4?.modelUsed.orEmpty()
                val primaryLane = stage4?.responseLane.orEmpty()
                val primaryLatency = stage4?.responseLatencySeconds
                val voiceResult = state.lastVoiceNoteResult
                val initialTurns = listOf(
                    MetadataTagEngine.tagChatTurn(
                        turn = ChatTurn(
                            id = UUID.randomUUID().toString(),
                            role = "USER",
                            content = query,
                            timestampMillis = System.currentTimeMillis()
                        ),
                        domain = output.triage.detectedDomain,
                        voiceNoteCategory = voiceResult?.category,
                        voiceNoteTags = voiceResult?.tags.orEmpty() + output.triage.autoTags,
                        fallbackCategory = NoteCategory.RESEARCH_QUERY
                    ),
                    MetadataTagEngine.tagChatTurn(
                        turn = ChatTurn(
                            id = UUID.randomUUID().toString(),
                            role = "ASSISTANT",
                            content = output.chatAnswer,
                            modelUsed = primaryModel,
                            responseLane = primaryLane,
                            responseLatencySeconds = primaryLatency,
                            sources = allSources,
                            timestampMillis = System.currentTimeMillis()
                        ),
                        domain = output.triage.detectedDomain,
                        voiceNoteTags = output.triage.autoTags,
                        fallbackCategory = NoteCategory.RESEARCH_QUERY
                    )
                )
                val autoProjectTags = MetadataTagEngine.deriveProjectTags(
                    title = output.triage.projectTitle,
                    domain = output.triage.detectedDomain,
                    blueprint = state.draftBlueprint,
                    query = query,
                    aiTags = output.triage.autoTags,
                    existingTags = existing?.tags.orEmpty() + initialTurns.flatMap { it.tags },
                    voiceNoteCategory = voiceResult?.category,
                    voiceNoteTags = voiceResult?.tags.orEmpty(),
                    maxCount = 16
                )

                val savedProject: ResearchProject
                if (existing != null && existing.canEdit(uid)) {
                    val existingPrdFiles = existing.parsedFiles().filter { it.category == "PRD" }
                    val mergedFiles = output.files + existingPrdFiles
                    val updated = existing.copy(
                        title = output.triage.projectTitle,
                        rootFolderName = finalRoot,
                        mode = state.draftMode.wire,
                        fileFormat = state.draftFileFormat.wire,
                        blueprintId = state.draftBlueprint.wire,
                        query = query,
                        complexityScore = output.triage.complexityScore,
                        detectedDomain = output.triage.detectedDomain,
                        routingPlanSummary = output.routingPlanSummary,
                        pipelineStatus = PipelineStatus.COMPLETED.wire,
                        gapVerdict = output.gapVerdict.wire,
                        stagesJson = VortexJson.encodeStages(output.stages),
                        filesJson = VortexJson.encodeFiles(mergedFiles),
                        chatAnswer = output.chatAnswer,
                        chatHistoryJson = VortexJson.encodeChatHistory(initialTurns),
                        tags = autoProjectTags
                    )
                    repository.updateProjectContent(updated).getOrThrow()
                    offlineRepository.cacheSyncedProject(updated, uid)
                    savedProject = updated
                } else {
                    val created = ResearchProject(
                        ownerId = uid,
                        title = output.triage.projectTitle,
                        rootFolderName = finalRoot,
                        mode = state.draftMode.wire,
                        fileFormat = state.draftFileFormat.wire,
                        blueprintId = state.draftBlueprint.wire,
                        query = query,
                        complexityScore = output.triage.complexityScore,
                        detectedDomain = output.triage.detectedDomain,
                        routingPlanSummary = output.routingPlanSummary,
                        pipelineStatus = PipelineStatus.COMPLETED.wire,
                        gapVerdict = output.gapVerdict.wire,
                        stagesJson = VortexJson.encodeStages(output.stages),
                        filesJson = VortexJson.encodeFiles(output.files),
                        chatAnswer = output.chatAnswer,
                        chatHistoryJson = VortexJson.encodeChatHistory(initialTurns),
                        tags = autoProjectTags,
                        memberUids = listOf(uid),
                        memberRoles = mapOf(uid to TeamRole.ADMIN.wire),
                        memberDisplayNames = mapOf(uid to currentDisplayName())
                    )
                    val newId = repository.createProject(created).getOrThrow()
                    savedProject = created.copy(id = newId)
                    offlineRepository.cacheSyncedProject(savedProject, uid)
                    activeProjectIdFlow.value = newId
                    _uiState.update { it.copy(activeProjectId = newId) }
                }

                _uiState.update {
                    it.copy(
                        pipelineRunning = false,
                        liveStages = output.stages,
                        recommendedBlueprint = output.triage.recommendedBlueprint,
                        draftRootFolder = finalRoot,
                        bannerMessage = "7-Stage synthesis completed (${output.gapVerdict.label})."
                    )
                }
                autoRefreshPublicShareIfActive(savedProject, activeProjectPrds.value, activeRoundtableItems.value)
                maybeTriggerAutoGitHubMirror("Vortex 7-stage synthesis complete")
            } catch (e: CancellationException) {
                throw e
            } catch (e: PipelinePassFailureException) {
                lastStage0Triage = e.triageSnapshot ?: lastStage0Triage
                _uiState.update {
                    it.copy(
                        pipelineRunning = false,
                        liveStages = e.stagesSnapshot,
                        recommendedBlueprint = e.triageSnapshot?.recommendedBlueprint ?: it.recommendedBlueprint,
                        errorMessage = "Pass ${e.failedPass} failed: ${e.message?.take(120)}. Tap Retry Stage on the failed card to resume without losing completed stages."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        pipelineRunning = false,
                        errorMessage = "Pipeline interrupted: ${e.message?.take(140) ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun sendChatFollowUp(followUpQuestion: String) {
        val question = followUpQuestion.trim()
        if (question.isBlank()) return
        val project = activeProject() ?: run {
            _uiState.update { it.copy(errorMessage = "Run an initial research query before asking follow-up questions.") }
            return
        }
        val uid = currentUid()
        if (!project.canEdit(uid) || _uiState.value.chatFollowUpRunning) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    chatFollowUpRunning = true,
                    errorMessage = null,
                    bannerMessage = "Researching follow-up with live Google Search..."
                )
            }
            try {
                val existingHistory = project.parsedChatHistory().ifEmpty {
                    buildList {
                        if (project.query.isNotBlank()) {
                            add(ChatTurn(UUID.randomUUID().toString(), "USER", project.query, timestampMillis = System.currentTimeMillis()))
                        }
                        if (project.chatAnswer.isNotBlank()) {
                            add(ChatTurn(UUID.randomUUID().toString(), "ASSISTANT", project.chatAnswer, timestampMillis = System.currentTimeMillis()))
                        }
                    }
                }
                val prefs = resolveEffectiveRoutingPreferences()
                val result = if (prefs.externalEnabled &&
                    prefs.externalRouterType == ExternalRouterType.SELF_HOSTED_TEXT
                ) {
                    modelRouter.callSelfHostedDirect(
                        userPrompt = question,
                        prefs = prefs
                    ).getOrThrow()
                } else {
                    val envPolicy = buildAgentRuntimeContext(stageId = null)
                    orchestrator.answerChatFollowUp(
                        project = project,
                        history = existingHistory,
                        followUpQuestion = question,
                        prefs = prefs,
                        envPolicyContext = envPolicy
                    )
                }

                val now = System.currentTimeMillis()
                val taggedUserTurn = MetadataTagEngine.tagChatTurn(
                    turn = ChatTurn(
                        id = UUID.randomUUID().toString(),
                        role = "USER",
                        content = question,
                        timestampMillis = now
                    ),
                    domain = project.detectedDomain,
                    fallbackCategory = NoteCategory.FOLLOW_UP
                )
                val taggedAssistantTurn = MetadataTagEngine.tagChatTurn(
                    turn = ChatTurn(
                        id = UUID.randomUUID().toString(),
                        role = "ASSISTANT",
                        content = result.text,
                        modelUsed = result.modelUsed,
                        responseLane = result.responseLane,
                        responseLatencySeconds = result.latencySeconds,
                        sources = result.sources,
                        timestampMillis = now + 1L
                    ),
                    domain = project.detectedDomain,
                    fallbackCategory = NoteCategory.FOLLOW_UP
                )
                val updatedHistory = existingHistory + listOf(taggedUserTurn, taggedAssistantTurn)
                val refreshedProjectTags = MetadataTagEngine.normalizeTags(
                    raw = MetadataTagEngine.effectiveProjectTags(project) +
                        taggedUserTurn.tags +
                        taggedAssistantTurn.tags,
                    maxCount = 20
                )
                val updatedProject = project.copy(
                    chatHistoryJson = VortexJson.encodeChatHistory(updatedHistory),
                    chatAnswer = result.text,
                    tags = refreshedProjectTags
                )
                offlineRepository.saveProjectContentOfflineFirst(updatedProject, uid)
                autoRefreshPublicShareIfActive(updatedProject, activeProjectPrds.value, activeRoundtableItems.value)
                _uiState.update {
                    it.copy(
                        chatFollowUpRunning = false,
                        bannerMessage = "Follow-up answered by ${result.modelUsed}."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        chatFollowUpRunning = false,
                        errorMessage = "Follow-up failed: ${e.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun approveAndGeneratePrd(revisionRequest: String = "", isMajorBump: Boolean = false) {
        val project = activeProject() ?: run {
            _uiState.update { it.copy(errorMessage = "Run a research session before generating a PRD.") }
            return
        }
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot generate PRD versions.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    prdGenerating = true,
                    errorMessage = null,
                    bannerMessage = "Synthesizing versioned PRD & updating PRD/PRD_INDEX..."
                )
            }
            try {
                val existingPrds = activeProjectPrds.value
                val previous = existingPrds.maxWithOrNull(
                    compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion }
                )
                val (nextMajor, nextMinor) = when {
                    previous == null -> 1 to 0
                    isMajorBump -> (previous.majorVersion + 1) to 0
                    else -> previous.majorVersion to (previous.minorVersion + 1)
                }
                val nextVersion = "v$nextMajor.$nextMinor"
                val adoptedSummary = activeRoundtableItems.value
                    .filter { it.queueStatus in listOf(RoundtableStatus.ADOPTED.wire, RoundtableStatus.CONSENSUS_RESOLVED.wire) }
                    .joinToString("\n") { "- [${it.modelFolder}/${it.fileName}]: ${it.scrutinyVerdict.take(450)}" }

                val prefs = resolveEffectiveRoutingPreferences()
                val synth = orchestrator.generatePrdVersion(
                    project = project,
                    existingPrds = existingPrds,
                    revisionRequest = revisionRequest,
                    isMajorBump = isMajorBump,
                    prefs = prefs,
                    roundtableAdoptedSummary = adoptedSummary
                )

                val prdDoc = PrdDocument(
                    projectId = project.id,
                    version = nextVersion,
                    majorVersion = nextMajor,
                    minorVersion = nextMinor,
                    title = synth.title,
                    deltaSummary = synth.deltaSummary,
                    revisionRequest = revisionRequest,
                    markdownContent = synth.markdownContent,
                    modelUsed = synth.modelUsed,
                    authorUid = uid,
                    authorName = currentDisplayName()
                )
                val savedPrd = repository.createPrdAndUpdateIndex(project, existingPrds, prdDoc).getOrThrow()
                offlineRepository.cacheSyncedPrd(savedPrd)

                val updatedPrdList = (existingPrds + savedPrd).distinctBy { it.version }
                autoRefreshPublicShareIfActive(
                    project = project.copy(approved = true, latestPrdVersion = nextVersion),
                    prds = updatedPrdList,
                    roundtableItems = activeRoundtableItems.value
                )

                _uiState.update {
                    it.copy(
                        prdGenerating = false,
                        bannerMessage = if (project.publicShareActive) {
                            "Generated $nextVersion, updated PRD/PRD_INDEX & refreshed live public share!"
                        } else {
                            "Generated $nextVersion & updated PRD/PRD_INDEX!"
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        prdGenerating = false,
                        errorMessage = "PRD generation failed: ${e.message?.take(140)}"
                    )
                }
            }
        }
    }

    // ==================== AI ROUNDTABLE ====================

    fun addModelFolder(folderName: String) {
        val clean = folderName.trim().take(48)
        if (clean.isBlank()) return
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot create model folders.") }
            return
        }
        val existing = project.effectiveRoundtableFolders()
        if (existing.any { it.equals(clean, ignoreCase = true) }) {
            _uiState.update { it.copy(bannerMessage = "Sub-folder '$clean/' already exists.") }
            return
        }
        val updatedFolders = existing + clean
        val rtDir = ScaffoldingBlueprint.fromWire(project.blueprintId).roundtableFolder
        viewModelScope.launch {
            val outcome = offlineRepository.updateRoundtableFoldersOfflineFirst(project, updatedFolders, uid)
            val syncTag = if (outcome.syncedImmediately) "synced to cloud" else "saved offline · queued for sync"
            _uiState.update { state ->
                state.copy(
                    customModelFolders = (state.customModelFolders + clean).distinctBy { it.lowercase() },
                    bannerMessage = "Created $rtDir/$clean/ sub-folder ($syncTag)."
                )
            }
        }
    }

    fun removeModelFolder(folderName: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) return
        val hasItems = activeRoundtableItems.value.any { it.modelFolder.equals(folderName, ignoreCase = true) }
        if (hasItems) {
            _uiState.update { it.copy(errorMessage = "Delete files inside '$folderName/' before removing the sub-folder.") }
            return
        }
        val existing = project.effectiveRoundtableFolders()
        if (existing.size <= 1) {
            _uiState.update { it.copy(errorMessage = "Keep at least one model sub-folder in A.i. Roundtable.") }
            return
        }
        val updatedFolders = existing.filterNot { it.equals(folderName, ignoreCase = true) }
        viewModelScope.launch {
            val outcome = offlineRepository.updateRoundtableFoldersOfflineFirst(project, updatedFolders, uid)
            val syncTag = if (outcome.syncedImmediately) "" else " (queued for cloud sync)"
            _uiState.update { it.copy(bannerMessage = "Removed empty sub-folder '$folderName/'$syncTag.") }
        }
    }

    fun addPastedRoundtableIdea(modelFolder: String, fileName: String, content: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid) || content.isBlank()) return

        val safeName = fileName.trim().ifBlank { "idea_${System.currentTimeMillis() % 10000}.md" }
        val ext = safeName.substringAfterLast('.', "md").lowercase()
        val detectedLang = artifactIngestor.detectLanguage(safeName, ext)
        val rtDir = ScaffoldingBlueprint.fromWire(project.blueprintId).roundtableFolder
        val item = RoundtableItem(
            projectId = project.id,
            modelFolder = modelFolder,
            fileName = safeName,
            extension = ext,
            detectedLanguage = detectedLang,
            content = content.trim(),
            byteSize = content.toByteArray(Charsets.UTF_8).size,
            queueStatus = RoundtableStatus.QUEUED.wire,
            authorUid = uid
        )
        viewModelScope.launch {
            ensureFolderPersisted(project, modelFolder)
            val outcome = offlineRepository.addRoundtableItemOfflineFirst(item)
            autoRefreshPublicShareIfActive(project, activeProjectPrds.value, activeRoundtableItems.value + item)
            val syncTag = if (outcome.syncedImmediately) "" else " (cached offline)"
            _uiState.update { it.copy(bannerMessage = "Queued $safeName ($detectedLang) under $rtDir/$modelFolder/$syncTag.") }
        }
    }

    fun ingestRoundtableFiles(modelFolder: String, uris: List<Uri>) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid) || uris.isEmpty()) return

        val rtDir = ScaffoldingBlueprint.fromWire(project.blueprintId).roundtableFolder
        viewModelScope.launch {
            _uiState.update { it.copy(bannerMessage = "Ingesting ${uris.size} file(s) into $rtDir/$modelFolder/...") }
            ensureFolderPersisted(project, modelFolder)
            val report = artifactIngestor.ingestUris(appContext, uris)
            var saved = 0
            var anyOffline = false
            val addedItems = mutableListOf<RoundtableItem>()
            for (artifact in report.artifacts) {
                val item = RoundtableItem(
                    projectId = project.id,
                    modelFolder = modelFolder,
                    fileName = artifact.fileName,
                    extension = artifact.extension,
                    detectedLanguage = artifact.detectedLanguage,
                    content = artifact.content,
                    byteSize = artifact.byteSize,
                    queueStatus = RoundtableStatus.QUEUED.wire,
                    authorUid = uid
                )
                val outcome = offlineRepository.addRoundtableItemOfflineFirst(item)
                if (!outcome.syncedImmediately) anyOffline = true
                addedItems.add(item)
                saved++
            }
            if (addedItems.isNotEmpty()) {
                autoRefreshPublicShareIfActive(project, activeProjectPrds.value, activeRoundtableItems.value + addedItems)
            }
            val skipNote = if (report.skippedMessages.isNotEmpty()) {
                " Skipped: ${report.skippedMessages.first()}"
            } else ""
            val offlineNote = if (anyOffline) " (cached offline · auto-syncs online)" else ""
            _uiState.update {
                it.copy(bannerMessage = "Uploaded $saved file(s) into $rtDir/$modelFolder/$offlineNote.$skipNote")
            }
        }
    }

    private suspend fun ensureFolderPersisted(project: ResearchProject, folderName: String) {
        val current = project.effectiveRoundtableFolders()
        if (current.none { it.equals(folderName, ignoreCase = true) }) {
            offlineRepository.updateRoundtableFoldersOfflineFirst(project, current + folderName, currentUid())
        }
    }

    fun scrutinizeSingleRoundtableItem(item: RoundtableItem) {
        val project = activeProject() ?: return
        if (!project.canEdit(currentUid()) || _uiState.value.scrutinyRunning) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scrutinyRunning = true,
                    bannerMessage = "Scrutinizing ${item.modelFolder}/${item.fileName} with live Google Search..."
                )
            }
            try {
                val prefs = resolveEffectiveRoutingPreferences()
                repository.updateRoundtableItem(item.copy(queueStatus = RoundtableStatus.ANALYZING.wire))
                val outcome = orchestrator.scrutinizeRoundtableItem(project, item, prefs)
                val auditedItem = item.copy(
                    queueStatus = outcome.status.wire,
                    scrutinyVerdict = outcome.verdict,
                    outboundRelayPrompt = outcome.outboundRelayPrompt,
                    groundingSourcesJson = VortexJson.encodeSources(outcome.sources),
                    scrutinizedByModel = outcome.modelUsed
                )
                repository.updateRoundtableItem(auditedItem)
                val refreshedList = activeRoundtableItems.value.map { if (it.id == auditedItem.id) auditedItem else it }
                autoRefreshPublicShareIfActive(project, activeProjectPrds.value, refreshedList)
                _uiState.update {
                    it.copy(
                        scrutinyRunning = false,
                        bannerMessage = "Audit complete for ${item.modelFolder}/${item.fileName} (${outcome.status.label})."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scrutinyRunning = false,
                        errorMessage = "Scrutiny failed: ${e.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun runScrutinyQueue(folderFilter: String? = null) {
        val project = activeProject() ?: return
        if (!project.canEdit(currentUid())) return
        val queued = activeRoundtableItems.value.filter { item ->
            item.queueStatus == RoundtableStatus.QUEUED.wire &&
                (folderFilter == null || item.modelFolder.equals(folderFilter, ignoreCase = true))
        }
        if (queued.isEmpty()) {
            val scopeLabel = folderFilter?.let { "'$it/'" } ?: "A.i. Roundtable"
            _uiState.update { it.copy(bannerMessage = "No QUEUED items waiting for scrutiny in $scopeLabel.") }
            return
        }

        viewModelScope.launch {
            val scopeLabel = folderFilter?.let { "$it/" } ?: "all model folders"
            _uiState.update {
                it.copy(
                    scrutinyRunning = true,
                    bannerMessage = "Scrutinizing ${queued.size} file(s) in $scopeLabel with Google Search grounding..."
                )
            }
            try {
                val prefs = resolveEffectiveRoutingPreferences()
                val updatedMap = mutableMapOf<String, RoundtableItem>()
                for (item in queued) {
                    repository.updateRoundtableItem(item.copy(queueStatus = RoundtableStatus.ANALYZING.wire))
                    val outcome = orchestrator.scrutinizeRoundtableItem(project, item, prefs)
                    val audited = item.copy(
                        queueStatus = outcome.status.wire,
                        scrutinyVerdict = outcome.verdict,
                        outboundRelayPrompt = outcome.outboundRelayPrompt,
                        groundingSourcesJson = VortexJson.encodeSources(outcome.sources),
                        scrutinizedByModel = outcome.modelUsed
                    )
                    repository.updateRoundtableItem(audited)
                    updatedMap[audited.id] = audited
                }
                val refreshedList = activeRoundtableItems.value.map { updatedMap[it.id] ?: it }
                autoRefreshPublicShareIfActive(project, activeProjectPrds.value, refreshedList)
                _uiState.update {
                    it.copy(
                        scrutinyRunning = false,
                        bannerMessage = "Scrutiny Queue complete! Copy Outbound Relay Cards to continue 2-way debates."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scrutinyRunning = false,
                        errorMessage = "Scrutiny interrupted: ${e.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun runCrossModelComparison() {
        val project = activeProject() ?: return
        val items = activeRoundtableItems.value
        val distinctModels = items.map { it.modelFolder }.distinctBy { it.lowercase() }
        if (distinctModels.size < 2) {
            _uiState.update {
                it.copy(bannerMessage = "Upload files into at least 2 AI model sub-folders to run cross-model scrutiny.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    crossModelLoading = true,
                    errorMessage = null,
                    bannerMessage = "Running cross-model scrutiny across ${distinctModels.joinToString(" vs ")}..."
                )
            }
            try {
                val prefs = resolveEffectiveRoutingPreferences()
                val res = orchestrator.runCrossModelComparison(project, items, prefs)
                _uiState.update {
                    it.copy(
                        crossModelLoading = false,
                        crossModelSynthesis = res.text,
                        crossModelUsed = res.modelUsed,
                        bannerMessage = "Cross-model empirical comparison ready (${distinctModels.size} models)."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        crossModelLoading = false,
                        errorMessage = "Cross-model comparison failed: ${e.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun askRoundtableRag(question: String) {
        val project = activeProject() ?: return
        val items = activeRoundtableItems.value
        if (question.isBlank() || items.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(ragLoading = true, errorMessage = null) }
            try {
                val prefs = resolveEffectiveRoutingPreferences()
                val res = orchestrator.answerRoundtableRagQuestion(project, items, question.trim(), prefs)
                _uiState.update {
                    it.copy(
                        ragLoading = false,
                        ragAnswer = res.text,
                        ragModelUsed = res.modelUsed
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(ragLoading = false, errorMessage = "RAG query failed: ${e.message?.take(100)}")
                }
            }
        }
    }

    fun deleteRoundtableItem(item: RoundtableItem) {
        viewModelScope.launch {
            val outcome = offlineRepository.deleteRoundtableItemOfflineFirst(item.projectId, item.id)
            if (!outcome.syncedImmediately) {
                _uiState.update { it.copy(bannerMessage = "Deleted ${item.fileName} locally (queued for sync).") }
            }
        }
    }

    fun autoDispatchToExternalModel(
        modelFolder: String,
        targetModelId: String,
        customDirective: String = ""
    ) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid) || _uiState.value.autoDispatchRunning) return

        val cleanModelId = targetModelId.trim()
        if (cleanModelId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a valid external model ID (e.g., anthropic/claude-3.7-sonnet).") }
            return
        }

        viewModelScope.launch {
            val prefs = resolveEffectiveRoutingPreferences()
            val apiKey = prefs.externalApiKey.trim()
            val baseUrl = prefs.externalBaseUrl.trim().ifBlank { "https://openrouter.ai/api/v1/" }
            if (apiKey.isBlank()) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Configure an External Router API key in Settings or enable Auto-Fill for OPENROUTER_API_KEY in the Secure .env Vault."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    autoDispatchRunning = true,
                    errorMessage = null,
                    bannerMessage = "Auto-dispatching Audit Packet to $cleanModelId → $modelFolder/..."
                )
            }
            try {
                val packet = buildString {
                    appendLine("# Vortex Audit Packet: ${project.title}")
                    appendLine("Query: ${project.query}")
                    appendLine()
                    appendLine(
                        project.chatAnswer.ifBlank {
                            project.parsedFiles().firstOrNull()?.content.orEmpty()
                        }.take(16000)
                    )
                    if (customDirective.isNotBlank()) {
                        appendLine()
                        appendLine("### Specific Review Directive")
                        appendLine(customDirective.trim())
                    }
                    appendLine()
                    appendLine("Provide an authoritative empirical critique, concrete alternatives, and production-ready code/specifications where relevant.")
                }

                val sysPrompt = buildString {
                    append("You are an external AI architectural reviewer participating in a Vortex Researcher A.i. Roundtable under folder '$modelFolder'. Be rigorous, empirical, and specific.")
                    val policy = envVaultStore.buildAuthorizedModelPolicySummary()
                    if (policy.isNotBlank()) {
                        append("\n\n$policy")
                    }
                }

                val responseText = if (prefs.externalRouterType == ExternalRouterType.SELF_HOSTED_TEXT) {
                    val composed = ExternalRouterApi.composeSelfHostedPrompt(
                        userPrompt = packet.take(ExternalRouterApi.SELF_HOSTED_MAX_PROMPT_CHARS),
                        approvedSummary = prefs.selfHostedApprovedSummary
                    ).take(ExternalRouterApi.SELF_HOSTED_MAX_PROMPT_CHARS)
                    externalRouterApi.selfHostedQuery(
                        baseUrl = baseUrl,
                        bearerToken = apiKey,
                        prompt = composed
                    ).getOrThrow().let { res ->
                        "**Self-Hosted Router** · `lane=${res.lane}` · `model=${res.model}`\n\n${res.response}"
                    }
                } else {
                    externalRouterApi.chatCompletion(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        modelId = cleanModelId,
                        systemPrompt = sysPrompt,
                        userPrompt = packet
                    ).getOrThrow()
                }

                val safeFolder = modelFolder.trim().ifBlank { "External-AI" }
                ensureFolderPersisted(project, safeFolder)
                val safeSlug = cleanModelId.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "-").take(28)
                val fileName = "auto-dispatch-${safeSlug}-${(System.currentTimeMillis() / 1000) % 10000}.md"
                val content = buildString {
                    appendLine("# Auto-Dispatched Response: $cleanModelId")
                    appendLine("> Target Folder: $safeFolder | Project: ${project.title}")
                    appendLine()
                    append(responseText)
                }

                val item = RoundtableItem(
                    projectId = project.id,
                    modelFolder = safeFolder,
                    fileName = fileName,
                    extension = "md",
                    detectedLanguage = "Markdown",
                    content = content,
                    byteSize = content.toByteArray(Charsets.UTF_8).size,
                    queueStatus = RoundtableStatus.QUEUED.wire,
                    authorUid = uid
                )
                offlineRepository.addRoundtableItemOfflineFirst(item)
                autoRefreshPublicShareIfActive(project, activeProjectPrds.value, activeRoundtableItems.value + item)

                _uiState.update {
                    it.copy(
                        autoDispatchRunning = false,
                        bannerMessage = "Saved $cleanModelId response into $safeFolder/$fileName — ready for Gemini scrutiny!"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        autoDispatchRunning = false,
                        errorMessage = "Auto-Dispatch failed: ${e.message?.take(140)}"
                    )
                }
            }
        }
    }

    fun updateRoundtableItemContent(
        item: RoundtableItem,
        updatedContent: String,
        requeueForScrutiny: Boolean
    ) {
        val project = activeProject() ?: return
        if (!project.canEdit(currentUid())) return
        val trimmed = updatedContent.ifBlank { " " }
        val updated = item.copy(
            content = trimmed,
            byteSize = trimmed.toByteArray(Charsets.UTF_8).size,
            queueStatus = if (requeueForScrutiny) RoundtableStatus.QUEUED.wire else item.queueStatus
        )
        viewModelScope.launch {
            val outcome = offlineRepository.updateRoundtableItemOfflineFirst(updated)
            val offlineTag = if (outcome.syncedImmediately) "" else " (cached offline · syncs when online)"
            _uiState.update {
                it.copy(
                    bannerMessage = if (requeueForScrutiny) {
                        "Saved ${item.fileName} & re-queued for scrutiny$offlineTag."
                    } else {
                        "Saved edits to ${item.modelFolder}/${item.fileName}$offlineTag."
                    }
                )
            }
        }
    }

    // ==================== COLLABORATION & PUBLIC SHARE ====================

    fun createTeamInvite(role: TeamRole) {
        val project = activeProject() ?: return
        if (!project.canAdmin(currentUid())) {
            _uiState.update { it.copy(errorMessage = "Only Admins can create team invite links.") }
            return
        }
        viewModelScope.launch {
            repository.createInvite(project, role, currentUid())
                .onSuccess { invite ->
                    _uiState.update {
                        it.copy(
                            lastInvite = invite,
                            bannerMessage = "Created ${role.label} invite link."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun joinProjectByCode(rawCodeOrUrl: String) {
        val uid = currentUid()
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.joinProjectWithInvite(rawCodeOrUrl, uid, currentDisplayName())
                .onSuccess { joinedId ->
                    activeProjectIdFlow.value = joinedId
                    _uiState.update {
                        it.copy(
                            activeProjectId = joinedId,
                            bannerMessage = "Joined collaborative repository!"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not redeem invite link.") }
                }
        }
    }

    fun updateMemberRole(targetUid: String, newRole: TeamRole) {
        val project = activeProject() ?: return
        if (!project.canAdmin(currentUid())) return
        viewModelScope.launch {
            repository.updateMemberRole(project, targetUid, newRole)
                .onSuccess { _uiState.update { it.copy(bannerMessage = "Updated member role to ${newRole.label}.") } }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun removeTeamMember(targetUid: String) {
        val project = activeProject() ?: return
        if (!project.canAdmin(currentUid())) return
        viewModelScope.launch {
            repository.removeMember(project, targetUid)
                .onSuccess { _uiState.update { it.copy(bannerMessage = "Removed team member.") } }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun togglePublicShare(enable: Boolean) {
        val project = activeProject() ?: return
        if (!project.canAdmin(currentUid())) return
        viewModelScope.launch {
            if (enable) {
                repository.publishPublicShare(project, activeProjectPrds.value, activeRoundtableItems.value)
                    .onSuccess { code ->
                        _uiState.update {
                            it.copy(bannerMessage = "Published sign-in-free read-only link (Stages, Files, PRDs & Roundtable): vortexresearcher://share/$code")
                        }
                    }
                    .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            } else {
                repository.revokePublicShare(project)
                    .onSuccess { _uiState.update { it.copy(bannerMessage = "Revoked public read-only link.") } }
                    .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
            }
        }
    }

    private suspend fun autoRefreshPublicShareIfActive(
        project: ResearchProject,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ) {
        if (project.publicShareActive && project.canAdmin(currentUid()) && connectivityMonitor.isOnline.value) {
            runCatching {
                repository.publishPublicShare(project, prds, roundtableItems)
            }
        }
    }

    fun deleteProject(project: ResearchProject) {
        if (project.ownerId != currentUid()) return
        viewModelScope.launch {
            val outcome = offlineRepository.deleteProjectOfflineFirst(project.id)
            val syncTag = if (outcome.syncedImmediately) "" else " (queued for cloud delete)"
            _uiState.update { it.copy(bannerMessage = "Deleted '${project.title}'$syncTag.") }
        }
    }

    // ==================== OFFLINE-FIRST REPO FILE ORGANIZATION & SYNC ====================

    fun triggerManualOfflineSync() {
        viewModelScope.launch {
            if (!connectivityMonitor.refreshNow()) {
                _uiState.update {
                    it.copy(bannerMessage = "Device is offline. Queued edits stay safe in Room and sync automatically once connected.")
                }
                return@launch
            }
            val synced = offlineRepository.syncPendingOperations()
            if (synced == 0) {
                _uiState.update { it.copy(bannerMessage = "Local Room cache is already in sync with cloud.") }
            }
        }
    }

    fun resolveActiveSyncConflict(strategy: ConflictResolutionStrategy) {
        val conflict = activeSyncConflict.value ?: return
        if (_resolvingConflict.value) return
        viewModelScope.launch {
            _resolvingConflict.value = true
            offlineRepository.resolveConflict(conflict, strategy)
                .onSuccess { msg ->
                    _uiState.update { it.copy(bannerMessage = msg) }
                    offlineRepository.syncPendingOperations()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = "Could not resolve conflict: ${err.message?.take(120)}")
                    }
                }
            _resolvingConflict.value = false
        }
    }

    fun createRepoFile(relativePath: String, content: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot create repository files.") }
            return
        }
        val normalized = normalizeRepoPath(relativePath)
        if (normalized.isBlank()) return

        val existing = project.parsedFiles().toMutableList()
        val category = inferFileCategory(normalized)
        existing.removeAll { it.relativePath.equals(normalized, ignoreCase = true) }
        existing.add(ProjectFile(relativePath = normalized, content = content, category = category))

        val updated = project.copy(filesJson = VortexJson.encodeFiles(existing))
        viewModelScope.launch {
            val outcome = offlineRepository.saveProjectContentOfflineFirst(updated, uid, editedFilePath = normalized)
            val syncTag = if (outcome.syncedImmediately) "synced" else "saved offline · queued"
            _uiState.update { it.copy(bannerMessage = "Created $normalized ($syncTag).") }
            if (outcome.syncedImmediately) maybeTriggerAutoGitHubMirror("Add $normalized", updated)
        }
    }

    /**
     * Silently persists a Mermaid collaborative brainstorming board (freehand strokes & node-anchored
     * sticky notes) into the project repository so all collaborators receive real-time Firestore updates
     * and offline Room caching without spamming UI status banners on every stroke or drag.
     */
    fun saveDiagramBoard(relativePath: String, boardJson: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) return
        val normalized = normalizeRepoPath(relativePath)
        if (normalized.isBlank()) return

        val existing = project.parsedFiles().toMutableList()
        existing.removeAll { it.relativePath.equals(normalized, ignoreCase = true) }
        existing.add(ProjectFile(relativePath = normalized, content = boardJson, category = "RESEARCH"))

        val updated = project.copy(filesJson = VortexJson.encodeFiles(existing))
        viewModelScope.launch {
            offlineRepository.saveProjectContentOfflineFirst(updated, uid, editedFilePath = normalized)
        }
    }

    fun moveOrRenameRepoFile(oldPath: String, newPath: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot reorganize repository files.") }
            return
        }
        val normalizedNew = normalizeRepoPath(newPath)
        if (normalizedNew.isBlank() || normalizedNew == oldPath) return

        val existing = project.parsedFiles()
        val target = existing.firstOrNull { it.relativePath == oldPath } ?: return
        if (existing.any { it.relativePath.equals(normalizedNew, ignoreCase = true) && it.relativePath != oldPath }) {
            _uiState.update { it.copy(errorMessage = "A file already exists at '$normalizedNew'.") }
            return
        }
        val updatedFiles = existing.map { file ->
            if (file.relativePath == oldPath) {
                file.copy(relativePath = normalizedNew, category = inferFileCategory(normalizedNew))
            } else file
        }
        val updated = project.copy(filesJson = VortexJson.encodeFiles(updatedFiles))
        viewModelScope.launch {
            val outcome = offlineRepository.saveProjectContentOfflineFirst(updated, uid, editedFilePath = normalizedNew)
            val syncTag = if (outcome.syncedImmediately) "synced" else "cached offline · queued"
            _uiState.update { it.copy(bannerMessage = "Moved to $normalizedNew ($syncTag).") }
            if (outcome.syncedImmediately) maybeTriggerAutoGitHubMirror("Move $oldPath -> $normalizedNew", updated)
        }
    }

    fun updateRepoFileContent(relativePath: String, newContent: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot edit repository files.") }
            return
        }
        val updatedFiles = project.parsedFiles().map { file ->
            if (file.relativePath == relativePath) file.copy(content = newContent) else file
        }
        val updated = project.copy(filesJson = VortexJson.encodeFiles(updatedFiles))
        viewModelScope.launch {
            val outcome = offlineRepository.saveProjectContentOfflineFirst(updated, uid, editedFilePath = relativePath)
            val syncTag = if (outcome.syncedImmediately) "synced" else "saved offline · queued"
            _uiState.update { it.copy(bannerMessage = "Saved ${relativePath.substringAfterLast('/')} ($syncTag).") }
            if (outcome.syncedImmediately) maybeTriggerAutoGitHubMirror("Update $relativePath", updated)
        }
    }

    fun deleteRepoFile(relativePath: String) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot delete repository files.") }
            return
        }
        val updatedFiles = project.parsedFiles().filterNot { it.relativePath == relativePath }
        val updated = project.copy(filesJson = VortexJson.encodeFiles(updatedFiles))
        viewModelScope.launch {
            val outcome = offlineRepository.saveProjectContentOfflineFirst(updated, uid, editedFilePath = relativePath)
            val syncTag = if (outcome.syncedImmediately) "synced" else "deleted offline · queued"
            _uiState.update { it.copy(bannerMessage = "Removed ${relativePath.substringAfterLast('/')} ($syncTag).") }
        }
    }

    private fun normalizeRepoPath(raw: String): String =
        raw.trim()
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")

    private fun inferFileCategory(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.startsWith("prd/") -> "PRD"
            lower.startsWith("roundtable/") || lower.startsWith("ai_roundtable/") -> "ROUNDTABLE"
            else -> "RESEARCH"
        }
    }

    // ==================== ROUTER SETTINGS, GITHUB & SAF MIRRORING ====================

    fun saveRoutingPreferences(updated: RoutingPreferences) {
        viewModelScope.launch {
            val externalKeyRef = updated.externalAuthVaultRef
                .trim()
                .ifBlank { EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY }
            val trimmedExtKey = updated.externalApiKey.trim()
            if (trimmedExtKey.isNotBlank()) {
                envVaultStore.upsertManagedCredential(
                    key = externalKeyRef,
                    value = trimmedExtKey,
                    category = EnvSecretCategory.API_KEY,
                    target = EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY,
                    note = "External Router Bearer token (stored in AES-256-GCM vault)"
                )
            } else {
                envVaultStore.clearManagedCredentialTarget(EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY)
            }

            val trimmedPat = updated.githubPat.trim()
            if (trimmedPat.isNotBlank()) {
                envVaultStore.upsertManagedCredential(
                    key = EncryptedEnvVaultStore.KEY_GITHUB_PAT,
                    value = trimmedPat,
                    category = EnvSecretCategory.TOKEN,
                    target = EnvAutoFillTarget.GITHUB_PAT,
                    note = "GitHub access token (stored in AES-256-GCM vault)"
                )
            } else {
                envVaultStore.clearManagedCredentialTarget(EnvAutoFillTarget.GITHUB_PAT)
            }

            // Persist strictly non-secret fields to DataStore
            prefsStore.saveRoutingPreferences(
                updated.copy(
                    externalAuthVaultRef = externalKeyRef,
                    externalApiKey = "",
                    githubPat = ""
                )
            )
            _uiState.update { it.copy(bannerMessage = "Saved settings locally (credentials encrypted in AES-256-GCM vault).") }
        }
    }

    fun checkSelfHostedRouterHealth(baseUrl: String) {
        if (_checkingSelfHostedHealth.value) return
        viewModelScope.launch {
            _checkingSelfHostedHealth.value = true
            val res = externalRouterApi.selfHostedHealth(baseUrl)
            _selfHostedHealthResult.value = res
            _checkingSelfHostedHealth.value = false
            _uiState.update { it.copy(bannerMessage = "Self-Hosted /health: ${res.summary}") }
        }
    }

    fun setStrategicRoutingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setStrategicRoutingEnabled(enabled)
            _uiState.update {
                it.copy(
                    bannerMessage = if (enabled) {
                        "Strategic Model Routing enabled — auto-upgrading complex queries."
                    } else {
                        "Strategic Model Routing disabled — locked to Gemini 3.8 Flash."
                    }
                )
            }
        }
    }

    fun verifyGitHubToken(pat: String, onVerified: (String) -> Unit = {}) {
        viewModelScope.launch {
            githubAuthManager.verifyAccessToken(pat, GitHubAuthMethod.PERSONAL_ACCESS_TOKEN)
                .onSuccess { profile ->
                    envVaultStore.upsertManagedCredential(
                        key = EncryptedEnvVaultStore.KEY_GITHUB_PAT,
                        value = pat.trim(),
                        category = EnvSecretCategory.TOKEN,
                        target = EnvAutoFillTarget.GITHUB_PAT,
                        note = "GitHub PAT verified for @${profile.login}"
                    )
                    prefsStore.saveGitHubSessionMetadata(
                        username = profile.login,
                        authMethodWire = GitHubAuthMethod.PERSONAL_ACCESS_TOKEN.wire
                    )
                    onVerified(profile.login)
                    _githubLastError.value = null
                    _uiState.update { it.copy(bannerMessage = "Connected GitHub account @${profile.login} (token encrypted in vault).") }
                    refreshGitHubRepositories()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun startGitHubDeviceAuth(clientId: String) {
        val cleanId = clientId.trim()
        if (cleanId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a GitHub OAuth App Client ID first.") }
            return
        }
        githubDevicePollJob?.cancel()
        githubDevicePollJob = viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, githubDevicePolling = true) }
            githubAuthManager.startDeviceAuthFlow(cleanId)
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            githubDeviceSession = session,
                            bannerMessage = "Enter code ${session.userCode} at github.com/login/device to finish signing in."
                        )
                    }
                    githubAuthManager.pollDeviceAuthUntilComplete(cleanId, session)
                        .onSuccess { accessToken ->
                            val profile = githubAuthManager.verifyAccessToken(
                                accessToken,
                                GitHubAuthMethod.OAUTH_DEVICE_FLOW
                            ).getOrThrow()
                            envVaultStore.upsertManagedCredential(
                                key = EncryptedEnvVaultStore.KEY_GITHUB_PAT,
                                value = accessToken.trim(),
                                category = EnvSecretCategory.TOKEN,
                                target = EnvAutoFillTarget.GITHUB_PAT,
                                note = "GitHub OAuth Device token for @${profile.login}"
                            )
                            prefsStore.saveGitHubSessionMetadata(
                                username = profile.login,
                                authMethodWire = GitHubAuthMethod.OAUTH_DEVICE_FLOW.wire,
                                oauthClientId = cleanId
                            )
                            _uiState.update {
                                it.copy(
                                    githubDeviceSession = null,
                                    githubDevicePolling = false,
                                    bannerMessage = "Authenticated with GitHub OAuth as @${profile.login} (token encrypted in vault)!"
                                )
                            }
                            refreshGitHubRepositories()
                        }
                        .onFailure { err ->
                            _uiState.update {
                                it.copy(
                                    githubDeviceSession = null,
                                    githubDevicePolling = false,
                                    errorMessage = err.message
                                )
                            }
                        }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            githubDeviceSession = null,
                            githubDevicePolling = false,
                            errorMessage = err.message
                        )
                    }
                }
        }
    }

    fun cancelGitHubDeviceAuth() {
        githubDevicePollJob?.cancel()
        _uiState.update { it.copy(githubDeviceSession = null, githubDevicePolling = false) }
    }

    fun disconnectGitHub() {
        githubDevicePollJob?.cancel()
        _githubLiveProgress.value = null
        _githubLastError.value = null
        viewModelScope.launch {
            envVaultStore.clearManagedCredentialTarget(EnvAutoFillTarget.GITHUB_PAT)
            prefsStore.clearGitHubCredentials()
            _uiState.update {
                it.copy(
                    githubUserRepos = emptyList(),
                    lastGitHubReport = null,
                    githubDeviceSession = null,
                    githubDevicePolling = false,
                    bannerMessage = "Disconnected GitHub account and cleared encrypted token."
                )
            }
        }
    }

    fun setGitHubAutoMirror(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setGitHubAutoMirror(enabled)
            _uiState.update {
                it.copy(
                    bannerMessage = if (enabled) {
                        "GitHub Auto-Mirror enabled — changes will mirror atomically."
                    } else {
                        "GitHub Auto-Mirror paused."
                    }
                )
            }
        }
    }

    fun setGitHubDefaultPrivate(isPrivate: Boolean) {
        viewModelScope.launch {
            prefsStore.setGitHubDefaultPrivate(isPrivate)
        }
    }

    fun refreshGitHubRepositories() {
        val pat = routingPreferences.value.githubPat
        if (pat.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingGitHubRepos = true) }
            githubAuthManager.listUserRepositories(pat)
                .onSuccess { repos ->
                    _uiState.update { it.copy(loadingGitHubRepos = false, githubUserRepos = repos) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(loadingGitHubRepos = false, errorMessage = err.message) }
                }
        }
    }

    fun linkExistingGitHubRepo(repo: GitHubRepoSummary) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot change linked GitHub repository.") }
            return
        }
        val updated = project.copy(githubRepoUrl = repo.htmlUrl, githubBranch = repo.defaultBranch)
        _githubLastError.value = null
        viewModelScope.launch {
            offlineRepository.saveProjectContentOfflineFirst(updated, uid)
            _uiState.update { it.copy(bannerMessage = "Linked project to ${repo.fullName} (${repo.defaultBranch}).") }
        }
    }

    fun computeGitHubSyncStatusLabel(project: ResearchProject?): String {
        if (project == null) return "Unlinked"
        val prefs = routingPreferences.value
        if (prefs.githubPat.isBlank() || prefs.githubUsername.isBlank()) return "Not Connected"
        if (project.githubRepoUrl.isBlank()) return "Auto-Provision"
        val lastHash = githubSyncedHashes.value[project.id] ?: return "Linked"
        val currentHash = SafFolderSyncManager.computeProjectHash(buildAllRepoFiles(project))
        return if (currentHash == lastHash) "In Sync" else "Local Ahead"
    }

    private fun maybeTriggerAutoGitHubMirror(reason: String, projectOverride: ResearchProject? = null) {
        val prefs = routingPreferences.value
        if (!prefs.githubAutoMirror || prefs.githubPat.isBlank() || !connectivityMonitor.refreshNow()) return
        if (_uiState.value.syncingGitHub) return
        pushToGitHub(commitReason = reason, projectOverride = projectOverride, silentIfUnchanged = true)
    }

    fun refreshPrdIndex() {
        val project = activeProject() ?: return
        if (!project.canEdit(currentUid())) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot modify PRD_INDEX.") }
            return
        }
        viewModelScope.launch {
            repository.syncPrdIndexFile(project, activeProjectPrds.value)
                .onSuccess {
                    val ext = ExportFileFormat.fromWire(project.fileFormat).extension
                    _uiState.update {
                        it.copy(bannerMessage = "Synchronized PRD/PRD_INDEX.$ext (${activeProjectPrds.value.size} versions).")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun buildAllRepoFiles(project: ResearchProject): List<ProjectFile> {
        return WorkspaceTemplateEngine.buildConsistentWorkspace(
            project = project,
            prds = activeProjectPrds.value,
            roundtableItems = activeRoundtableItems.value
        ).files
    }

    fun applyWorkspaceTemplateLocally(blueprint: ScaffoldingBlueprint) {
        val project = activeProject() ?: return
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot reorganize workspace templates.") }
            return
        }
        viewModelScope.launch {
            val built = WorkspaceTemplateEngine.buildConsistentWorkspace(
                project = project,
                prds = activeProjectPrds.value,
                roundtableItems = activeRoundtableItems.value,
                overrideBlueprint = blueprint
            )
            val updated = project.copy(
                blueprintId = blueprint.wire,
                filesJson = VortexJson.encodeFiles(built.files)
            )
            val outcome = offlineRepository.saveProjectContentOfflineFirst(updated, uid)
            val syncTag = if (outcome.syncedImmediately) "synced" else "saved offline · auto-syncs online"
            _uiState.update {
                it.copy(
                    draftBlueprint = blueprint,
                    bannerMessage = "Applied '${blueprint.title}' template ($syncTag)."
                )
            }
        }
    }

    fun computeDriveSyncStatusLabel(project: ResearchProject?): String {
        if (project == null) return "Not Linked"
        val folderId = driveFolderIds.value[project.id]
        if (folderId.isNullOrBlank()) return "Not Linked"
        val lastHash = driveSyncedHashes.value[project.id] ?: return "Linked"
        val currentSha = WorkspaceTemplateEngine.buildConsistentWorkspace(
            project = project,
            prds = activeProjectPrds.value,
            roundtableItems = activeRoundtableItems.value
        ).workspaceSha256
        return if (currentSha == lastHash) "In Sync" else "Local Ahead"
    }

    fun driveWebUrlFor(project: ResearchProject?): String? {
        val id = project?.id?.let { driveFolderIds.value[it] } ?: return null
        return "https://drive.google.com/drive/folders/$id"
    }

    fun requestDriveSync(activityContext: Context, action: PendingDriveAction) {
        if (activeProject() == null) {
            _uiState.update { it.copy(errorMessage = "Select or run a project before syncing to Google Drive.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncingDrive = true, errorMessage = null) }
            when (val auth = GoogleDriveAuthManager.authorizeDrive(activityContext)) {
                is DriveAuthResult.Authorized -> executeDriveAction(auth.accessToken, action)
                is DriveAuthResult.NeedsConsent -> {
                    _uiState.update {
                        it.copy(
                            syncingDrive = false,
                            pendingDriveConsent = auth.pendingIntent,
                            pendingDriveAction = action
                        )
                    }
                }
                is DriveAuthResult.Error -> {
                    _uiState.update {
                        it.copy(syncingDrive = false, errorMessage = auth.message)
                    }
                }
            }
        }
    }

    fun onDriveConsentCompleted(activityContext: Context, resultData: Intent?) {
        val pendingAction = _uiState.value.pendingDriveAction
        _uiState.update { it.copy(pendingDriveConsent = null, pendingDriveAction = null) }
        if (pendingAction == null) return

        when (val res = GoogleDriveAuthManager.extractTokenFromIntent(activityContext, resultData)) {
            is DriveAuthResult.Authorized -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(syncingDrive = true, errorMessage = null) }
                    executeDriveAction(res.accessToken, pendingAction)
                }
            }
            is DriveAuthResult.Error -> {
                _uiState.update { it.copy(syncingDrive = false, errorMessage = res.message) }
            }
            is DriveAuthResult.NeedsConsent -> Unit
        }
    }

    private suspend fun executeDriveAction(accessToken: String, action: PendingDriveAction) {
        val project = activeProject() ?: run {
            _driveLiveProgress.value = null
            _uiState.update { it.copy(syncingDrive = false) }
            return
        }
        val existingId = driveFolderIds.value[project.id]
        _driveLastError.value = null
        _driveLiveProgress.value = DriveSyncProgressUpdate(
            direction = if (action == PendingDriveAction.PULL) DriveSyncDirection.PULL else DriveSyncDirection.PUSH,
            stageLabel = if (action == PendingDriveAction.PULL) "Connecting to Google Drive..." else "Preparing Drive mirror...",
            progressFraction = 0.05f
        )

        try {
            when (action) {
                PendingDriveAction.PUSH -> {
                    val workspace = WorkspaceTemplateEngine.buildConsistentWorkspace(
                        project = project,
                        prds = activeProjectPrds.value,
                        roundtableItems = activeRoundtableItems.value
                    )
                    driveSyncService.pushWorkspaceToDrive(
                        accessToken = accessToken,
                        rootFolderName = project.rootFolderName,
                        existingDriveFolderId = existingId,
                        workspace = workspace,
                        onProgress = { progress -> _driveLiveProgress.value = progress }
                    ).onSuccess { report ->
                        val summary = "+${report.uploadedCount} new · ${report.updatedCount} updated · ${report.skippedUnchangedCount} unchanged"
                        prefsStore.saveDriveSyncState(
                            projectId = project.id,
                            folderId = report.rootFolderId,
                            sha256 = report.workspaceSha256,
                            summaryLabel = summary
                        )
                        val uid = currentUid()
                        if (project.canEdit(uid)) {
                            offlineRepository.saveProjectContentOfflineFirst(
                                project.copy(filesJson = VortexJson.encodeFiles(workspace.files)),
                                uid
                            )
                        }
                        _uiState.update {
                            it.copy(
                                syncingDrive = false,
                                lastDriveReport = report,
                                bannerMessage = "Google Drive Mirror complete ($summary) ↑"
                            )
                        }
                    }.onFailure { err ->
                        val errText = err.message ?: "Google Drive push failed."
                        _driveLastError.value = errText
                        _uiState.update {
                            it.copy(
                                syncingDrive = false,
                                errorMessage = errText
                            )
                        }
                    }
                }

                PendingDriveAction.PULL -> {
                    driveSyncService.pullWorkspaceFromDrive(
                        accessToken = accessToken,
                        rootFolderName = project.rootFolderName,
                        existingDriveFolderId = existingId,
                        onProgress = { progress -> _driveLiveProgress.value = progress }
                    ).onSuccess { report ->
                        val uid = currentUid()
                        if (report.pulledFiles.isNotEmpty() && project.canEdit(uid)) {
                            offlineRepository.saveProjectContentOfflineFirst(
                                project.copy(filesJson = VortexJson.encodeFiles(report.pulledFiles)),
                                uid
                            )
                        }
                        val summary = "Pulled ${report.pulledFiles.size} file(s) from Drive"
                        prefsStore.saveDriveSyncState(
                            projectId = project.id,
                            folderId = report.rootFolderId,
                            sha256 = report.workspaceSha256,
                            summaryLabel = summary
                        )
                        _uiState.update {
                            it.copy(
                                syncingDrive = false,
                                bannerMessage = "Pulled ${report.pulledFiles.size} file(s) from Google Drive ↓"
                            )
                        }
                    }.onFailure { err ->
                        val errText = err.message ?: "Google Drive pull failed."
                        _driveLastError.value = errText
                        _uiState.update {
                            it.copy(
                                syncingDrive = false,
                                errorMessage = errText
                            )
                        }
                    }
                }
            }
        } finally {
            _driveLiveProgress.value = null
        }
    }

    fun pushToGitHub(
        commitReason: String? = null,
        projectOverride: ResearchProject? = null,
        silentIfUnchanged: Boolean = false
    ) {
        val project = projectOverride ?: activeProject() ?: return
        val prefs = routingPreferences.value
        if (prefs.githubPat.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Connect your GitHub account in GitHub Mirror Console first.") }
            return
        }
        viewModelScope.launch {
            _githubLastError.value = null
            _githubLiveProgress.value = GitHubSyncProgressUpdate(
                phase = GitHubSyncPhase.PREPARING,
                direction = GitHubSyncDirection.PUSH,
                stageLabel = "Preparing workspace files...",
                progressFraction = 0.05f
            )
            _uiState.update { it.copy(syncingGitHub = true, errorMessage = null) }
            try {
                val files = buildAllRepoFiles(project)
                val targetRepoOrUrl = project.githubRepoUrl.ifBlank {
                    project.rootFolderName.ifBlank { "vortex-research" }
                }
                val commitMsg = commitReason ?: "Vortex Researcher mirror (${project.latestPrdVersion.ifBlank { "workspace" }})"
                val lastHash = githubSyncedHashes.value[project.id]

                githubSyncEngine.mirrorFilesAtomically(
                    token = prefs.githubPat,
                    ownerHint = prefs.githubUsername,
                    repoNameOrUrl = targetRepoOrUrl,
                    isPrivate = prefs.githubDefaultPrivate,
                    files = files,
                    commitMessage = commitMsg,
                    lastSyncedWorkspaceSha = lastHash,
                    forcePush = !silentIfUnchanged,
                    onProgress = { progress -> _githubLiveProgress.value = progress }
                ).onSuccess { report ->
                    val uid = currentUid()
                    if (project.canEdit(uid) &&
                        (project.githubRepoUrl != report.repoUrl || project.githubBranch != report.branch)
                    ) {
                        offlineRepository.saveProjectContentOfflineFirst(
                            project.copy(githubRepoUrl = report.repoUrl, githubBranch = report.branch),
                            uid
                        )
                    }
                    prefsStore.saveGitHubSyncState(
                        projectId = project.id,
                        workspaceSha256 = report.workspaceSha256,
                        summaryLabel = report.summaryLabel
                    )
                    val msg = if (report.totalMirroredFiles == 0 && silentIfUnchanged) {
                        null
                    } else if (report.totalMirroredFiles == 0) {
                        "GitHub already up to date (${report.skippedUnchangedCount} unchanged)."
                    } else {
                        val shaSuffix = if (report.commitSha.isNotBlank()) " · ${report.commitSha}" else ""
                        "Mirrored to GitHub (${report.summaryLabel}$shaSuffix) ↑"
                    }
                    _uiState.update {
                        it.copy(
                            syncingGitHub = false,
                            lastGitHubReport = report,
                            bannerMessage = msg ?: it.bannerMessage
                        )
                    }
                }.onFailure { err ->
                    val errText = err.message ?: "GitHub mirror failed."
                    _githubLastError.value = errText
                    _uiState.update {
                        it.copy(syncingGitHub = false, errorMessage = errText)
                    }
                }
            } finally {
                _githubLiveProgress.value = null
            }
        }
    }

    fun mirrorBoundLocalFolderToGitHub() {
        val project = activeProject() ?: return
        val prefs = routingPreferences.value
        if (prefs.githubPat.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Connect GitHub before mirroring your local folder.") }
            return
        }
        val uriStr = safFolderBindings.value[project.id] ?: run {
            _uiState.update { it.copy(errorMessage = "Bind a local project folder first.") }
            return
        }
        viewModelScope.launch {
            _githubLastError.value = null
            _githubLiveProgress.value = GitHubSyncProgressUpdate(
                phase = GitHubSyncPhase.PREPARING,
                direction = GitHubSyncDirection.PUSH,
                stageLabel = "Scanning bound local folder...",
                progressFraction = 0.06f
            )
            _uiState.update { it.copy(syncingGitHub = true, errorMessage = null) }
            try {
                SafFolderSyncManager.pullProjectFromSafTree(
                    context = appContext,
                    treeUri = Uri.parse(uriStr),
                    rootFolderName = project.rootFolderName
                ).onSuccess { (localFiles, _) ->
                    if (localFiles.isEmpty()) {
                        val errText = "Bound local folder has no readable files to mirror."
                        _githubLastError.value = errText
                        _uiState.update {
                            it.copy(syncingGitHub = false, errorMessage = errText)
                        }
                        return@onSuccess
                    }
                    val targetRepoOrUrl = project.githubRepoUrl.ifBlank {
                        project.rootFolderName.ifBlank { "vortex-research" }
                    }
                    githubSyncEngine.mirrorFilesAtomically(
                        token = prefs.githubPat,
                        ownerHint = prefs.githubUsername,
                        repoNameOrUrl = targetRepoOrUrl,
                        isPrivate = prefs.githubDefaultPrivate,
                        files = localFiles,
                        commitMessage = "Mirror local folder ${project.rootFolderName}/",
                        forcePush = true,
                        onProgress = { progress -> _githubLiveProgress.value = progress }
                    ).onSuccess { report ->
                        val uid = currentUid()
                        if (project.canEdit(uid)) {
                            offlineRepository.saveProjectContentOfflineFirst(
                                project.copy(
                                    githubRepoUrl = report.repoUrl,
                                    githubBranch = report.branch,
                                    filesJson = VortexJson.encodeFiles(localFiles)
                                ),
                                uid
                            )
                        }
                        prefsStore.saveGitHubSyncState(
                            projectId = project.id,
                            workspaceSha256 = report.workspaceSha256,
                            summaryLabel = report.summaryLabel
                        )
                        _uiState.update {
                            it.copy(
                                syncingGitHub = false,
                                lastGitHubReport = report,
                                bannerMessage = "Mirrored local folder to GitHub (${report.summaryLabel}) ↑"
                            )
                        }
                    }.onFailure { err ->
                        val errText = err.message ?: "Local folder GitHub mirror failed."
                        _githubLastError.value = errText
                        _uiState.update {
                            it.copy(syncingGitHub = false, errorMessage = errText)
                        }
                    }
                }.onFailure { err ->
                    val errText = "Could not read bound folder: ${err.message?.take(100)}"
                    _githubLastError.value = errText
                    _uiState.update {
                        it.copy(syncingGitHub = false, errorMessage = errText)
                    }
                }
            } finally {
                _githubLiveProgress.value = null
            }
        }
    }

    fun pullFromGitHub() {
        val project = activeProject() ?: return
        if (project.githubRepoUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Mirror to GitHub or link an existing repository first.") }
            return
        }
        val prefs = routingPreferences.value
        viewModelScope.launch {
            _githubLastError.value = null
            _githubLiveProgress.value = GitHubSyncProgressUpdate(
                phase = GitHubSyncPhase.PREPARING,
                direction = GitHubSyncDirection.PULL,
                stageLabel = "Connecting to GitHub remote...",
                progressFraction = 0.08f
            )
            _uiState.update { it.copy(syncingGitHub = true, errorMessage = null) }
            try {
                githubSyncEngine.pullRepositoryFiles(
                    token = prefs.githubPat,
                    repoUrl = project.githubRepoUrl,
                    branch = project.githubBranch.ifBlank { "main" },
                    onProgress = { progress -> _githubLiveProgress.value = progress }
                ).onSuccess { pulled ->
                    val uid = currentUid()
                    if (pulled.isNotEmpty() && project.canEdit(uid)) {
                        offlineRepository.saveProjectContentOfflineFirst(
                            project.copy(filesJson = VortexJson.encodeFiles(pulled)),
                            uid
                        )
                    }
                    val sha = SafFolderSyncManager.computeProjectHash(pulled)
                    prefsStore.saveGitHubSyncState(project.id, sha, "Pulled ${pulled.size} file(s)")
                    _uiState.update {
                        it.copy(
                            syncingGitHub = false,
                            bannerMessage = "Pulled ${pulled.size} file(s) from GitHub ↓"
                        )
                    }
                }.onFailure { err ->
                    val errText = err.message ?: "GitHub pull failed."
                    _githubLastError.value = errText
                    _uiState.update {
                        it.copy(syncingGitHub = false, errorMessage = errText)
                    }
                }
            } finally {
                _githubLiveProgress.value = null
            }
        }
    }

    fun rawGitHubUrlFor(relativePath: String): String? {
        val project = activeProject() ?: return null
        if (project.githubRepoUrl.isBlank()) return null
        return githubSyncEngine.buildRawFileUrl(project.githubRepoUrl, project.githubBranch, relativePath)
    }

    fun bindSafFolder(treeUri: Uri) {
        val project = activeProject() ?: return
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
        viewModelScope.launch {
            prefsStore.saveSafFolderBinding(project.id, treeUri.toString())
            _uiState.update { it.copy(bannerMessage = "Bound persistent folder mirror for '${project.rootFolderName}'.") }
            refreshSafFolderStatus(project, treeUri.toString())
        }
    }

    fun refreshSafFolderStatus(project: ResearchProject? = activeProject(), overrideUri: String? = null) {
        val target = project ?: return
        val uriStr = overrideUri ?: safFolderBindings.value[target.id] ?: return
        viewModelScope.launch {
            SafFolderSyncManager.computeSafTreeHash(
                context = appContext,
                treeUri = Uri.parse(uriStr),
                rootFolderName = target.rootFolderName
            ).onSuccess { remoteHash ->
                _safRemoteHashes.update { it + (target.id to remoteHash) }
            }
        }
    }

    fun pushToSafFolder() {
        val project = activeProject() ?: return
        val uriStr = safFolderBindings.value[project.id] ?: run {
            _uiState.update { it.copy(errorMessage = "Bind a local or cloud-provider folder first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncingSaf = true, errorMessage = null) }
            val files = buildAllRepoFiles(project)
            SafFolderSyncManager.pushProjectToSafTree(
                context = appContext,
                treeUri = Uri.parse(uriStr),
                rootFolderName = project.rootFolderName,
                files = files
            ).onSuccess { res ->
                prefsStore.saveSafSyncedHash(project.id, res.sha256)
                _safRemoteHashes.update { it + (project.id to res.sha256) }
                _uiState.update {
                    it.copy(
                        syncingSaf = false,
                        bannerMessage = "Mirrored ${res.fileCount} nested file(s) Phone → Folder ↑"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(syncingSaf = false, errorMessage = "Folder push failed: ${err.message?.take(100)}")
                }
            }
        }
    }

    fun pullFromSafFolder() {
        val project = activeProject() ?: return
        val uriStr = safFolderBindings.value[project.id] ?: run {
            _uiState.update { it.copy(errorMessage = "Bind a folder before pulling.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncingSaf = true, errorMessage = null) }
            SafFolderSyncManager.pullProjectFromSafTree(
                context = appContext,
                treeUri = Uri.parse(uriStr),
                rootFolderName = project.rootFolderName
            ).onSuccess { (files, hash) ->
                if (files.isNotEmpty() && project.canEdit(currentUid())) {
                    repository.updateProjectContent(
                        project.copy(filesJson = VortexJson.encodeFiles(files))
                    )
                }
                prefsStore.saveSafSyncedHash(project.id, hash)
                _safRemoteHashes.update { it + (project.id to hash) }
                _uiState.update {
                    it.copy(
                        syncingSaf = false,
                        bannerMessage = "Pulled ${files.size} file(s) Folder → Phone ↓"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(syncingSaf = false, errorMessage = "Folder pull failed: ${err.message?.take(100)}")
                }
            }
        }
    }

    fun computeSyncStatusLabel(project: ResearchProject?): String {
        if (project == null) return "Unbound"
        val bound = safFolderBindings.value[project.id]
        if (bound.isNullOrBlank()) return "Folder Unbound"
        val lastHash = safSyncedHashes.value[project.id]
        val remoteHash = _safRemoteHashes.value[project.id].orEmpty()
        if (lastHash.isNullOrBlank()) {
            return if (remoteHash.isNotBlank()) "Folder Ahead" else "Ready to Push"
        }
        val currentHash = SafFolderSyncManager.computeProjectHash(buildAllRepoFiles(project))
        val localChanged = currentHash != lastHash
        val remoteChanged = remoteHash.isNotBlank() && remoteHash != lastHash
        return when {
            localChanged && remoteChanged -> "Diverged (Local & Folder)"
            remoteChanged -> "Folder Ahead"
            localChanged -> "Local Ahead"
            else -> "In Sync"
        }
    }

    // ==================== SECURE .ENV SECRETS VAULT & DISCRETE AUTO-FILL ====================

    private fun applyDiscreteAutoFillToPrefs(
        base: RoutingPreferences,
        autoFill: DiscreteAutoFillResolution
    ): RoutingPreferences {
        val vaultRefKey = envVaultStore.getSecretValue(base.externalAuthVaultRef)
        val resolvedExtKey = vaultRefKey ?: autoFill.externalRouterApiKey ?: ""
        val resolvedPat = autoFill.githubPat
            ?: envVaultStore.getSecretValue(EncryptedEnvVaultStore.KEY_GITHUB_PAT)
            ?: ""
        return base.copy(
            githubPat = resolvedPat,
            githubOAuthClientId = autoFill.githubOAuthClientId ?: base.githubOAuthClientId,
            externalApiKey = resolvedExtKey,
            externalBaseUrl = autoFill.externalRouterBaseUrl ?: base.externalBaseUrl,
            externalEnabled = if (!autoFill.externalRouterApiKey.isNullOrBlank()) true else base.externalEnabled
        )
    }

    private suspend fun resolveEffectiveRoutingPreferences(): RoutingPreferences {
        val base = prefsStore.getCurrentRoutingPreferences()
        return applyDiscreteAutoFillToPrefs(base, envVaultStore.resolveDiscreteAutoFill())
    }

    fun upsertEnvSecret(entry: EnvSecretEntry) {
        viewModelScope.launch {
            envVaultStore.upsertEntry(entry)
                .onSuccess {
                    if (entry.autoFillEnabled) {
                        syncDiscreteAutoFillToPreferencesSilently()
                    }
                    _uiState.update { it.copy(bannerMessage = "Saved '${entry.key}' in local encrypted .env vault.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to save secret entry.") }
                }
        }
    }

    fun deleteEnvSecret(entryId: String, keyLabel: String) {
        viewModelScope.launch {
            envVaultStore.deleteEntry(entryId)
                .onSuccess {
                    _uiState.update { it.copy(bannerMessage = "Removed '$keyLabel' from encrypted .env vault.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun updateEnvSecretPermissions(
        entryId: String,
        transform: (ModelSecretPermissions) -> ModelSecretPermissions
    ) {
        viewModelScope.launch {
            envVaultStore.updatePermissions(entryId, transform)
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun toggleEnvSecretAutoFill(entryId: String, enabled: Boolean, target: EnvAutoFillTarget? = null) {
        viewModelScope.launch {
            envVaultStore.toggleAutoFill(entryId, enabled, target)
                .onSuccess {
                    if (enabled) {
                        syncDiscreteAutoFillToPreferencesSilently()
                    }
                }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun importEnvFileFromUri(uri: Uri) {
        viewModelScope.launch {
            envVaultStore.importEnvFromUri(uri)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            bannerMessage = "Imported $count key(s) into encrypted .env vault (default: Human Only / No AI Access)."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = "Failed to import .env: ${err.message?.take(120)}") }
                }
        }
    }

    fun createStarterEnvVault() {
        viewModelScope.launch {
            envVaultStore.createStarterEnvInVault()
                .onSuccess { added ->
                    val msg = if (added > 0) {
                        "Created $added starter .env entries in local encrypted vault."
                    } else {
                        "Starter .env keys already exist in your encrypted vault."
                    }
                    _uiState.update { it.copy(bannerMessage = msg) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
                }
        }
    }

    fun exportEnvFileToUri(uri: Uri) {
        viewModelScope.launch {
            envVaultStore.exportEnvToUri(uri)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(bannerMessage = "Exported .env file ($count entries) cleanly to selected location.")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = "Export .env failed: ${err.message?.take(120)}") }
                }
        }
    }

    fun applyDiscreteEnvAutoFillNow() {
        viewModelScope.launch {
            val applied = syncDiscreteAutoFillToPreferencesSilently()
            val msg = if (applied > 0) {
                "Discretely auto-filled $applied integration credential(s) from encrypted .env vault."
            } else {
                "No .env entries have Auto-Fill enabled with a non-empty value yet."
            }
            _uiState.update { it.copy(bannerMessage = msg) }
        }
    }

    private suspend fun syncDiscreteAutoFillToPreferencesSilently(): Int {
        val resolution = envVaultStore.resolveDiscreteAutoFill()
        val current = prefsStore.getCurrentRoutingPreferences()
        var applied = 0
        var updated = current
        // Secrets stay exclusively in EncryptedEnvVaultStore; only non-secret routing metadata is persisted to DataStore.
        if (!resolution.githubPat.isNullOrBlank()) {
            updated = updated.copy(githubAuthMethod = "PAT")
            applied++
        }
        if (!resolution.githubOAuthClientId.isNullOrBlank()) {
            updated = updated.copy(githubOAuthClientId = resolution.githubOAuthClientId)
            applied++
        }
        if (!resolution.externalRouterApiKey.isNullOrBlank()) {
            updated = updated.copy(
                externalEnabled = true,
                externalAuthVaultRef = resolution.externalRouterApiKeyName ?: current.externalAuthVaultRef
            )
            applied++
        }
        if (!resolution.externalRouterBaseUrl.isNullOrBlank()) {
            updated = updated.copy(externalBaseUrl = resolution.externalRouterBaseUrl)
            applied++
        }
        if (applied > 0) {
            prefsStore.saveRoutingPreferences(updated.copy(externalApiKey = "", githubPat = ""))
        }
        return applied
    }

    fun exportSingleFile(uri: Uri, content: String) {
        viewModelScope.launch {
            SafFolderSyncManager.writeSingleDocument(appContext, uri, content)
                .onSuccess { _uiState.update { it.copy(bannerMessage = "Exported document successfully.") } }
                .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun exportProjectFileToUri(uri: Uri, file: ProjectFile) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportingReport = true, errorMessage = null) }
            SafFolderSyncManager.writeSingleDocument(appContext, uri, file.content)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            exportingReport = false,
                            bannerMessage = "Exported ${file.relativePath} successfully."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            exportingReport = false,
                            errorMessage = "File export failed: ${err.message?.take(100)}"
                        )
                    }
                }
        }
    }

    fun shareProjectFile(file: ProjectFile) {
        val fileName = file.relativePath.substringAfterLast('/').ifBlank { "export.txt" }
        SafFolderSyncManager.shareViaFileProvider(
            context = appContext,
            fileName = fileName,
            content = file.content,
            mimeType = UniversalFormatCatalog.mimeTypeForFileName(fileName)
        ).onFailure { err ->
            _uiState.update { it.copy(errorMessage = err.message ?: "Could not share file.") }
        }
    }

    fun exportProjectToUri(
        uri: Uri,
        format: UniversalExportFormat,
        pdfOptions: PdfReportOptions = PdfReportOptions()
    ) {
        val project = activeProject() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(exportingReport = true, errorMessage = null) }
            runCatching {
                val files = buildAllRepoFiles(project)
                val prds = activeProjectPrds.value
                val rtItems = activeRoundtableItems.value
                val payload = withContext(Dispatchers.IO) {
                    UniversalFormatCatalog.exportProjectBytes(
                        project = project,
                        files = files,
                        prds = prds,
                        roundtableItems = rtItems,
                        format = format,
                        pdfOptions = pdfOptions
                    )
                }
                SafFolderSyncManager.writeBinaryDocument(appContext, uri, payload).getOrThrow()
                payload.size
            }.onSuccess { byteCount ->
                val kb = String.format(java.util.Locale.US, "%.1f", byteCount / 1024f)
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        bannerMessage = "Exported ${format.label} (.${format.extension} · $kb KB) successfully."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        errorMessage = "Export failed: ${err.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun shareProjectExport(
        format: UniversalExportFormat,
        pdfOptions: PdfReportOptions = PdfReportOptions()
    ) {
        val project = activeProject() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(exportingReport = true, errorMessage = null) }
            runCatching {
                val files = buildAllRepoFiles(project)
                val prds = activeProjectPrds.value
                val rtItems = activeRoundtableItems.value
                val payload = withContext(Dispatchers.IO) {
                    UniversalFormatCatalog.exportProjectBytes(
                        project = project,
                        files = files,
                        prds = prds,
                        roundtableItems = rtItems,
                        format = format,
                        pdfOptions = pdfOptions
                    )
                }
                val fileName = UniversalFormatCatalog.suggestedFileName(project, format)
                SafFolderSyncManager.shareBinaryViaFileProvider(
                    context = appContext,
                    fileName = fileName,
                    bytes = payload,
                    mimeType = format.mimeType
                ).getOrThrow()
                fileName
            }.onSuccess { fileName ->
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        bannerMessage = "Prepared $fileName for sharing."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        errorMessage = "Share export failed: ${err.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun shareDocument(fileName: String, content: String) {
        SafFolderSyncManager.shareViaFileProvider(appContext, fileName, content)
            .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
    }

    private fun resolveBulkBundles(selection: Map<String, Set<String>>): List<BulkProjectExportBundle> {
        val summariesById = dashboardState.value.projects.associateBy { it.project.id }
        return selection.mapNotNull { (projectId, selectedPaths) ->
            if (selectedPaths.isEmpty()) return@mapNotNull null
            val summary = summariesById[projectId] ?: return@mapNotNull null
            val pickedFiles = summary.selectableFiles.filter { it.relativePath in selectedPaths }
            if (pickedFiles.isEmpty()) null
            else BulkProjectExportBundle(project = summary.project, files = pickedFiles)
        }
    }

    fun suggestedBulkZipFileName(selection: Map<String, Set<String>>): String =
        BulkZipExporter.suggestedArchiveFileName(resolveBulkBundles(selection))

    fun exportBulkZipToUri(
        uri: Uri,
        selection: Map<String, Set<String>>,
        includeManifest: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        val bundles = resolveBulkBundles(selection)
        if (bundles.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one project or file to export.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(exportingReport = true, errorMessage = null) }
            runCatching {
                val archive = withContext(Dispatchers.IO) {
                    BulkZipExporter.buildCompressedZip(bundles, includeManifest)
                }
                SafFolderSyncManager.writeBinaryDocument(appContext, uri, archive.bytes).getOrThrow()
                archive
            }.onSuccess { archive ->
                val sizeLabel = formatCompactByteSize(archive.compressedBytes.toLong())
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        bannerMessage = "Saved ZIP backup (${archive.projectCount} project(s) · ${archive.fileCount} files · $sizeLabel)."
                    )
                }
                onComplete()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        errorMessage = "Bulk ZIP export failed: ${err.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun shareBulkZip(
        selection: Map<String, Set<String>>,
        includeManifest: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        val bundles = resolveBulkBundles(selection)
        if (bundles.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one project or file to share.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(exportingReport = true, errorMessage = null) }
            runCatching {
                val archive = withContext(Dispatchers.IO) {
                    BulkZipExporter.buildCompressedZip(bundles, includeManifest)
                }
                SafFolderSyncManager.shareBinaryViaFileProvider(
                    context = appContext,
                    fileName = archive.suggestedFileName,
                    bytes = archive.bytes,
                    mimeType = "application/zip"
                ).getOrThrow()
                archive
            }.onSuccess { archive ->
                val sizeLabel = formatCompactByteSize(archive.compressedBytes.toLong())
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        bannerMessage = "Prepared ${archive.suggestedFileName} (${archive.fileCount} files · $sizeLabel) for sharing."
                    )
                }
                onComplete()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        exportingReport = false,
                        errorMessage = "Bulk ZIP share failed: ${err.message?.take(120)}"
                    )
                }
            }
        }
    }

    fun extractPostGisSchemaEntities(
        corpus: String,
        projectTitle: String,
        onComplete: (Result<PgSchemaMappingPlan>) -> Unit
    ) {
        if (_uiState.value.extractingPostGisEntities) return
        viewModelScope.launch {
            _uiState.update { it.copy(extractingPostGisEntities = true) }
            val result = runCatching {
                val rawJson = withContext(Dispatchers.IO) {
                    modelRouter.extractPostGisEntitiesWithGemini(corpus, projectTitle)
                }
                PostGisSchemaMapperEngine.parseAiExtractionJson(rawJson).getOrThrow()
            }
            _uiState.update { it.copy(extractingPostGisEntities = false) }
            onComplete(result)
        }
    }

    /**
     * Unified runtime context assembler:
     * Combines Authorized .env Policy + UTC<->Local Temporal Envelope + stage-scoped,
     * budget-capped ONLINE allowlisted MCP tools with zero leakage of PRIVATE_SOLO names/URLs or secrets.
     */
    private fun buildAgentRuntimeContext(stageId: Int? = null): String = listOf(
        envVaultStore.buildAuthorizedModelPolicySummary(),
        InfrastructureRegistryEngine.buildAuthorizedAgentInfraContext(
            state = infraRegistryState.value,
            currentStageId = stageId
        )
    ).filter { it.isNotBlank() }.joinToString("\n\n")

    fun updateInfraConnector(updated: InfraConnectorProfile) {
        viewModelScope.launch {
            val current = infraRegistryState.value
            val sanitizedVisibility = if (updated.visibility in updated.allowedVisibilities()) {
                updated.visibility
            } else {
                ConnectorVisibility.PRIVATE_SOLO
            }
            val safeUpdated = updated.copy(
                visibility = sanitizedVisibility,
                requirePerCallConfirmation = if (updated.id == InfraConnectorProfile.ID_VPS_ACCESS) true else updated.requirePerCallConfirmation
            )
            val nextList = current.connectors.map { if (it.id == safeUpdated.id) safeUpdated else it }
            prefsStore.saveInfrastructureRegistry(current.copy(connectors = nextList))
            _uiState.update { it.copy(bannerMessage = "Saved ${safeUpdated.name} (${safeUpdated.visibility.shortBadge}) locally.") }
        }
    }

    fun addCustomInfraConnector(
        name: String,
        kind: ConnectorKind,
        endpointUrl: String,
        visibility: ConnectorVisibility,
        secretRef: String
    ) {
        viewModelScope.launch {
            val current = infraRegistryState.value
            val cleanUrl = endpointUrl.trim()
            val profile = InfraConnectorProfile(
                name = name.trim(),
                providerLabel = if (kind.isStorageTier) "Custom BYO Database / Storage" else "Custom BYO MCP Server",
                kind = kind,
                templateKey = "custom_${System.currentTimeMillis()}",
                description = "User-registered universal connector (stored strictly on device unless explicitly shared). Confirm live tools via tools/list when online.",
                enabled = true,
                visibility = visibility,
                readOnlyEnforced = visibility == ConnectorVisibility.SANDBOXED_READ_ONLY,
                status = if (cleanUrl.isNotBlank()) ConnectorStatus.CONFIGURED else ConnectorStatus.STANDBY,
                useGatewayProxy = false,
                endpointUrl = cleanUrl,
                vaultSecretKeyRef = secretRef.trim(),
                capabilities = if (kind.isStorageTier) listOf("query.read", "query.write") else emptyList(),
                isCustom = true
            )
            prefsStore.saveInfrastructureRegistry(current.copy(connectors = current.connectors + profile))
            _uiState.update { it.copy(bannerMessage = "Added custom connector '${profile.name}' locally.") }
        }
    }

    fun removeCustomInfraConnector(connectorId: String) {
        viewModelScope.launch {
            val current = infraRegistryState.value
            val nextList = current.connectors.filterNot { it.id == connectorId && it.isCustom }
            mcpClient.clearSession(connectorId)
            prefsStore.saveInfrastructureRegistry(current.copy(connectors = nextList))
            _uiState.update { it.copy(bannerMessage = "Removed custom connector.") }
        }
    }

    fun updateInfraGatewayAndTime(
        gatewayBaseUrl: String,
        timezoneId: String,
        attachTimeContext: Boolean,
        agentContextTokenBudget: Int = infraRegistryState.value.agentContextTokenBudget,
        mcpResultCharCap: Int = infraRegistryState.value.mcpResultCharCap
    ) {
        viewModelScope.launch {
            val current = infraRegistryState.value
            prefsStore.saveInfrastructureRegistry(
                current.copy(
                    gatewayBaseUrl = gatewayBaseUrl.trim(),
                    localTimezoneId = timezoneId.trim().ifBlank { current.localTimezoneId },
                    attachTimeContextToAgents = attachTimeContext,
                    agentContextTokenBudget = agentContextTokenBudget.coerceIn(40, 1000),
                    mcpResultCharCap = mcpResultCharCap.coerceIn(250, 12_000)
                )
            )
            _uiState.update { it.copy(bannerMessage = "Saved Gateway, Time Sync & Governance settings locally.") }
        }
    }

    fun probeInfraConnector(connectorId: String) {
        viewModelScope.launch {
            val current = infraRegistryState.value
            val target = current.connectors.firstOrNull { it.id == connectorId } ?: return@launch
            val probingList = current.connectors.map {
                if (it.id == connectorId) it.copy(status = ConnectorStatus.PROBING, lastProbeMessage = "Probing endpoint…") else it
            }
            prefsStore.saveInfrastructureRegistry(current.copy(connectors = probingList))

            val result = InfrastructureRegistryEngine.probeConnector(
                profile = target,
                gatewayBaseUrl = current.gatewayBaseUrl,
                secretResolver = { keyRef -> envVaultStore.getSecretValue(keyRef) }
            )
            val latest = infraRegistryState.value
            val updatedList = latest.connectors.map {
                if (it.id == connectorId) {
                    it.copy(
                        status = result.status,
                        lastProbeMessage = result.summary,
                        lastProbeAtMillis = System.currentTimeMillis()
                    )
                } else it
            }
            prefsStore.saveInfrastructureRegistry(latest.copy(connectors = updatedList))
            _uiState.update { it.copy(bannerMessage = "${target.name}: ${result.summary}") }
        }
    }

    fun syncMcpToolsList(connectorId: String) {
        viewModelScope.launch {
            val current = infraRegistryState.value
            val target = current.connectors.firstOrNull { it.id == connectorId } ?: return@launch
            val probingList = current.connectors.map {
                if (it.id == connectorId) it.copy(status = ConnectorStatus.PROBING, lastProbeMessage = "Initializing MCP session & fetching tools/list…") else it
            }
            prefsStore.saveInfrastructureRegistry(current.copy(connectors = probingList))

            val resolver: (String) -> String? = { ref -> envVaultStore.getSecretValue(ref) }
            val initRes = mcpClient.initialize(target, current.gatewayBaseUrl, resolver)
            if (initRes.authRequired) {
                saveConnectorStatus(connectorId, ConnectorStatus.AUTH_REQUIRED, initRes.errorMessage ?: "Auth Required.")
                _uiState.update { it.copy(bannerMessage = "${target.name}: Auth Required during MCP initialize.") }
                return@launch
            }
            val listRes = mcpClient.listTools(target, current.gatewayBaseUrl, resolver)
            val latest = infraRegistryState.value
            if (listRes.authRequired) {
                saveConnectorStatus(connectorId, ConnectorStatus.AUTH_REQUIRED, listRes.errorMessage ?: "Auth Required.")
                _uiState.update { it.copy(bannerMessage = "${target.name}: Auth Required during tools/list.") }
                return@launch
            }
            if (!listRes.success) {
                saveConnectorStatus(
                    connectorId,
                    ConnectorStatus.UNREACHABLE,
                    listRes.errorMessage ?: initRes.errorMessage ?: "MCP tools/list unreachable."
                )
                _uiState.update { it.copy(errorMessage = "${target.name}: ${listRes.errorMessage ?: "tools/list failed"}") }
                return@launch
            }
            val liveNames = listRes.tools.map { it.name }.toSet()
            val updatedList = latest.connectors.map { c ->
                if (c.id == connectorId) {
                    val prunedAllowlist = c.allowedToolNames.intersect(liveNames)
                    val prunedUserRo = c.userMarkedReadOnlyTools.intersect(liveNames)
                    val updatedProfile = c.copy(
                        status = ConnectorStatus.ONLINE,
                        discoveredTools = listRes.tools,
                        allowedToolNames = prunedAllowlist,
                        userMarkedReadOnlyTools = prunedUserRo,
                        lastProbeAtMillis = System.currentTimeMillis(),
                        lastProbeMessage = "MCP Online · ${listRes.tools.size} tool(s) discovered" +
                            (initRes.sessionId?.let { " · Session active" } ?: "")
                    )
                    updatedProfile
                } else c
            }
            prefsStore.saveInfrastructureRegistry(latest.copy(connectors = updatedList))
            _uiState.update {
                it.copy(bannerMessage = "${target.name}: synced ${listRes.tools.size} tool(s) from live tools/list.")
            }
        }
    }

    fun evaluateMcpToolCall(connectorId: String, toolName: String): McpCallPolicyDecision? {
        val profile = infraRegistryState.value.connectors.firstOrNull { it.id == connectorId } ?: return null
        return mcpClient.evaluateToolCallPolicy(profile, toolName)
    }

    fun invokeMcpTool(
        connectorId: String,
        toolName: String,
        argumentsJson: String,
        confirmedByUser: Boolean,
        onOutcome: (McpToolCallOutcome) -> Unit
    ) {
        viewModelScope.launch {
            val state = infraRegistryState.value
            val profile = state.connectors.firstOrNull { it.id == connectorId } ?: return@launch
            val outcome = mcpClient.callTool(
                profile = profile,
                gatewayBaseUrl = state.gatewayBaseUrl,
                toolName = toolName,
                argumentsJson = argumentsJson,
                charCap = state.mcpResultCharCap,
                confirmedByUser = confirmedByUser,
                secretResolver = { ref -> envVaultStore.getSecretValue(ref) }
            )
            _mcpAuditLog.value = mcpAuditLogStore.loadAll()
            if (outcome.authRequired) {
                saveConnectorStatus(connectorId, ConnectorStatus.AUTH_REQUIRED, outcome.errorMessage ?: "Auth Required.")
            }
            onOutcome(outcome)
        }
    }

    fun lookupMcpStoredResult(refId: String): McpStoredResult? = mcpResultStore.getByRefId(refId)

    fun clearMcpAuditLog() {
        mcpAuditLogStore.clear()
        mcpResultStore.clear()
        _mcpAuditLog.value = emptyList()
        _uiState.update { it.copy(bannerMessage = "Cleared local MCP audit log and cached tool outputs.") }
    }

    private suspend fun saveConnectorStatus(connectorId: String, status: ConnectorStatus, message: String) {
        val latest = infraRegistryState.value
        val updatedList = latest.connectors.map {
            if (it.id == connectorId) {
                it.copy(
                    status = status,
                    lastProbeMessage = message,
                    lastProbeAtMillis = System.currentTimeMillis()
                )
            } else it
        }
        prefsStore.saveInfrastructureRegistry(latest.copy(connectors = updatedList))
    }

    fun mirrorNextStepsDocToProjectAndGitHub() {
        val project = activeProject() ?: run {
            _uiState.update { it.copy(errorMessage = "Select or create a project first to add '${VortexNextStepsDocProvider.CANONICAL_REPO_PATH}'.") }
            return
        }
        val uid = currentUid()
        if (!project.canEdit(uid)) {
            _uiState.update { it.copy(errorMessage = "Read-Only members cannot write documentation into this repository.") }
            return
        }
        val docPath = VortexNextStepsDocProvider.CANONICAL_REPO_PATH
        val content = VortexNextStepsDocProvider.fullMarkdown()
        val existing = project.parsedFiles().toMutableList()
        existing.removeAll { it.relativePath.equals(docPath, ignoreCase = true) }
        existing.add(ProjectFile(relativePath = docPath, content = content, category = "DOCS"))
        val updated = project.copy(filesJson = VortexJson.encodeFiles(existing))

        viewModelScope.launch {
            offlineRepository.saveProjectContentOfflineFirst(updated, uid, editedFilePath = docPath)
            val prefs = routingPreferences.value
            if (prefs.githubPat.isNotBlank()) {
                _uiState.update { it.copy(bannerMessage = "Saved $docPath — mirroring to GitHub…") }
                pushToGitHub(
                    commitReason = "Add/Update ${VortexNextStepsDocProvider.CANONICAL_DOC_TITLE}",
                    projectOverride = updated
                )
            } else {
                _uiState.update { it.copy(bannerMessage = "Saved $docPath to '${project.title}'. Connect GitHub Mirror to push.") }
            }
        }
    }
}

private data class GitHubSyncMetaSnapshot(
    val syncedHashes: Map<String, String>,
    val lastLabels: Map<String, String>,
    val liveProgress: GitHubSyncProgressUpdate?,
    val lastError: String?
)

private data class DriveSyncMetaSnapshot(
    val folderIds: Map<String, String>,
    val syncedHashes: Map<String, String>,
    val lastLabels: Map<String, String>,
    val liveProgress: DriveSyncProgressUpdate?,
    val lastError: String?
)

private data class DashboardCrossProjectSnapshot(
    val allPrds: List<PrdDocument>,
    val allRoundtable: List<RoundtableItem>,
    val prdCounts: Map<String, Int>,
    val roundtableCounts: Map<String, Int>
)

private data class DashboardSyncBindingsSnapshot(
    val safBindings: Map<String, String>,
    val safHashes: Map<String, String>,
    val ghMeta: GitHubSyncMetaSnapshot,
    val driveMeta: DriveSyncMetaSnapshot
)
