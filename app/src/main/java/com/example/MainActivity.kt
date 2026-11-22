package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.auth.AuthOutcome
import com.example.auth.GoogleAuthHelper
import com.example.auth.configureAppCheck
import com.example.ui.components.EnvSecretsVaultSheet
import com.example.ui.components.GitHubMirrorSheet
import com.example.ui.components.GoogleDriveSyncSheet
import com.example.ui.components.ModelRouterAndGitHubSheet
import com.example.ui.components.OfflineSyncStatusBanner
import com.example.ui.components.ProjectExportSheet
import com.example.ui.components.SyncConflictResolutionDialog
import com.example.ui.components.TeamAndPublicShareSheet
import com.example.ui.components.WorkspaceSyncHeader
import com.example.ui.screens.AuthWelcomeScreen
import com.example.ui.screens.GuestPublicShareViewerScreen
import com.example.ui.screens.PrdStudioScreen
import com.example.ui.screens.ProjectDashboardScreen
import com.example.ui.screens.ResearchScreen
import com.example.ui.screens.RoundtableScreen
import com.example.ui.theme.VortexTheme
import com.example.ui.viewmodel.GuestShareViewModel
import com.example.ui.viewmodel.PendingDriveAction
import com.example.ui.viewmodel.VortexViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_RESEARCH = "research"
private const val ROUTE_ROUNDTABLE = "roundtable"
private const val ROUTE_PRD = "prd"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    NavDestination(ROUTE_RESEARCH, R.string.nav_research, Icons.Filled.Science, Icons.Outlined.Science, "nav_research"),
    NavDestination(ROUTE_ROUNDTABLE, R.string.nav_roundtable, Icons.Filled.Forum, Icons.Outlined.Forum, "nav_roundtable"),
    NavDestination(ROUTE_PRD, R.string.nav_prd, Icons.Filled.Description, Icons.Outlined.Description, "nav_prd")
)

class MainActivity : ComponentActivity() {

    private val incomingInviteCode = mutableStateOf<String?>(null)
    private val incomingPublicShareCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure App Check with launch intent BEFORE UI, ViewModels, or Firebase
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        handleDeepLink(intent)

        setContent {
            VortexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VortexRootApp(
                        pendingInviteCode = incomingInviteCode.value,
                        pendingShareCode = incomingPublicShareCode.value,
                        onConsumeInvite = { incomingInviteCode.value = null },
                        onConsumeShare = { incomingPublicShareCode.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "vortexresearcher") {
            when (data.host) {
                "join" -> {
                    val code = data.getQueryParameter("code")
                    if (!code.isNullOrBlank()) incomingInviteCode.value = code
                }
                "share" -> {
                    val code = data.lastPathSegment
                    if (!code.isNullOrBlank()) incomingPublicShareCode.value = code
                }
            }
        }
    }
}

@Composable
private fun VortexRootApp(
    pendingInviteCode: String?,
    pendingShareCode: String?,
    onConsumeInvite: () -> Unit,
    onConsumeShare: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val guestViewModel: GuestShareViewModel = viewModel()
    val guestState by guestViewModel.uiState.collectAsStateWithLifecycle()

    var currentUser by remember { mutableStateOf<FirebaseUser?>(FirebaseAuth.getInstance().currentUser) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            GoogleAuthHelper.attemptAutoSignIn(context)
        }
    }

    LaunchedEffect(pendingShareCode) {
        if (!pendingShareCode.isNullOrBlank()) {
            guestViewModel.openShare(pendingShareCode)
            onConsumeShare()
        }
    }

    // If a guest (or user) opened a sign-in-free public share snapshot, render it immediately
    guestState.snapshot?.let { snapshot ->
        GuestPublicShareViewerScreen(
            snapshot = snapshot,
            onClose = { guestViewModel.closeShare() },
            onSignInPrompt = {
                guestViewModel.closeShare()
                scope.launch {
                    authLoading = true
                    authError = null
                    when (val res = GoogleAuthHelper.signInInteractive(context)) {
                        is AuthOutcome.Success -> currentUser = res.user
                        is AuthOutcome.Error -> authError = res.message
                        AuthOutcome.Cancelled -> Unit
                    }
                    authLoading = false
                }
            }
        )
        return
    }

    val user = currentUser
    if (user == null) {
        AuthWelcomeScreen(
            isLoading = authLoading || guestState.loading,
            errorMessage = guestState.error ?: authError,
            onGoogleSignIn = {
                scope.launch {
                    authLoading = true
                    authError = null
                    when (val res = GoogleAuthHelper.signInInteractive(context)) {
                        is AuthOutcome.Success -> currentUser = res.user
                        is AuthOutcome.Error -> authError = res.message
                        AuthOutcome.Cancelled -> Unit
                    }
                    authLoading = false
                }
            },
            onOpenPublicShareCode = { code -> guestViewModel.openShare(code) }
        )
    } else {
        key(user.uid) {
            AuthenticatedVortexWorkspace(
                uid = user.uid,
                pendingInviteCode = pendingInviteCode,
                onConsumeInvite = onConsumeInvite,
                onSignOut = {
                    scope.launch {
                        GoogleAuthHelper.signOut(context)
                        currentUser = null
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedVortexWorkspace(
    uid: String,
    pendingInviteCode: String?,
    onConsumeInvite: () -> Unit,
    onSignOut: () -> Unit
) {
    val viewModel: VortexViewModel = viewModel(key = "vortex_vm_$uid")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val syncTelemetry by viewModel.syncTelemetry.collectAsStateWithLifecycle()
    val activeSyncConflict by viewModel.activeSyncConflict.collectAsStateWithLifecycle()
    val resolvingConflict by viewModel.resolvingConflict.collectAsStateWithLifecycle()
    val routingPrefs by viewModel.routingPreferences.collectAsStateWithLifecycle()
    val prds by viewModel.activeProjectPrds.collectAsStateWithLifecycle()
    val roundtableItems by viewModel.activeRoundtableItems.collectAsStateWithLifecycle()
    val driveLastLabels by viewModel.driveLastSyncLabels.collectAsStateWithLifecycle()
    val driveHeaderSyncState by viewModel.driveHeaderSyncState.collectAsStateWithLifecycle()
    val githubLastLabels by viewModel.githubLastSyncLabels.collectAsStateWithLifecycle()
    val githubHeaderSyncState by viewModel.githubHeaderSyncState.collectAsStateWithLifecycle()
    val safFolderBindings by viewModel.safFolderBindings.collectAsStateWithLifecycle()
    val safSyncedHashes by viewModel.safSyncedHashes.collectAsStateWithLifecycle()
    val safRemoteHashes by viewModel.safRemoteHashes.collectAsStateWithLifecycle()
    val envSecrets by viewModel.envSecrets.collectAsStateWithLifecycle()
    val envAutoFillResolution by viewModel.envAutoFillResolution.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: ROUTE_DASHBOARD
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRouterSheet by remember { mutableStateOf(false) }
    var showEnvVaultSheet by remember { mutableStateOf(false) }
    var showTeamSheet by remember { mutableStateOf(false) }
    var showDriveSheet by remember { mutableStateOf(false) }
    var showGitHubSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var dismissedConflictId by remember { mutableStateOf<String?>(null) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveConsentCompleted(context, result.data)
    }

    LaunchedEffect(uiState.pendingDriveConsent) {
        val pending = uiState.pendingDriveConsent ?: return@LaunchedEffect
        driveConsentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }

    val activeProject = uiState.projects.firstOrNull { it.id == uiState.activeProjectId }

    LaunchedEffect(pendingInviteCode) {
        if (!pendingInviteCode.isNullOrBlank()) {
            viewModel.joinProjectByCode(pendingInviteCode)
            onConsumeInvite()
        }
    }

    LaunchedEffect(uiState.bannerMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                WorkspaceSyncHeader(
                    activeProjectFolder = activeProject?.rootFolderName,
                    telemetry = syncTelemetry,
                    driveSyncState = driveHeaderSyncState,
                    githubSyncState = githubHeaderSyncState,
                    onSyncNow = viewModel::triggerManualOfflineSync,
                    onOpenConflict = { dismissedConflictId = null },
                    onOpenDriveConsole = {
                        if (activeProject != null) {
                            showDriveSheet = true
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Select or initialize a project first to configure Google Drive sync.")
                            }
                        }
                    },
                    onOpenGitHubConsole = {
                        if (activeProject != null) {
                            showGitHubSheet = true
                        } else {
                            showRouterSheet = true
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    OfflineSyncStatusBanner(
                        telemetry = syncTelemetry,
                        onSyncNow = viewModel::triggerManualOfflineSync
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                ProjectDashboardScreen(
                                    dashboardState = dashboardState,
                                    currentUid = uid,
                                    userDisplayName = viewModel.currentDisplayName(),
                                    syncTelemetry = syncTelemetry,
                                    exportingReport = uiState.exportingReport,
                                    initializingTemplateProject = uiState.initializingTemplateProject,
                                    envSecretCount = envSecrets.size,
                                    onNewProject = {
                                        viewModel.startNewSession()
                                        navigateTo(ROUTE_RESEARCH)
                                    },
                                    onInitializeProjectFromTemplate = { title, rootFolder, blueprint, fileFormat, domain, extraFolders, openAfterCreate, onComplete ->
                                        viewModel.initializeProjectFromTemplate(
                                            title = title,
                                            rootFolderName = rootFolder,
                                            blueprint = blueprint,
                                            fileFormat = fileFormat,
                                            domain = domain,
                                            extraFolders = extraFolders,
                                            openAfterCreate = openAfterCreate
                                        ) {
                                            onComplete()
                                            if (openAfterCreate) {
                                                navigateTo(ROUTE_RESEARCH)
                                            }
                                        }
                                    },
                                    onJoinProject = { code ->
                                        viewModel.joinProjectByCode(code)
                                        navigateTo(ROUTE_RESEARCH)
                                    },
                                    onTriggerSync = {
                                        if (syncTelemetry.hasActiveConflict) {
                                            dismissedConflictId = null
                                        } else {
                                            viewModel.triggerManualOfflineSync()
                                        }
                                    },
                                    onOpenEnvVault = { showEnvVaultSheet = true },
                                    onOpenRouterSettings = { showRouterSheet = true },
                                    onSignOut = onSignOut,
                                    onOpenProjectResearch = { proj ->
                                        viewModel.selectProject(proj)
                                        navigateTo(ROUTE_RESEARCH)
                                    },
                                    onOpenProjectPrd = { proj ->
                                        viewModel.selectProject(proj)
                                        navigateTo(ROUTE_PRD)
                                    },
                                    onOpenProjectRoundtable = { proj ->
                                        viewModel.selectProject(proj)
                                        navigateTo(ROUTE_ROUNDTABLE)
                                    },
                                    onOpenProjectGitHub = { proj ->
                                        viewModel.selectProject(proj)
                                        showGitHubSheet = true
                                    },
                                    onOpenProjectDrive = { proj ->
                                        viewModel.selectProject(proj)
                                        showDriveSheet = true
                                    },
                                    onOpenProjectExport = { proj ->
                                        viewModel.selectProject(proj)
                                        showExportSheet = true
                                    },
                                    onOpenProjectTeamShare = { proj ->
                                        viewModel.selectProject(proj)
                                        showTeamSheet = true
                                    },
                                    onDeleteProject = viewModel::deleteProject,
                                    onSuggestedBulkZipName = viewModel::suggestedBulkZipFileName,
                                    onSaveBulkZipToUri = viewModel::exportBulkZipToUri,
                                    onShareBulkZip = viewModel::shareBulkZip,
                                    onOpenActivePrdStudio = { navigateTo(ROUTE_PRD) },
                                    onOpenActiveRoundtable = { navigateTo(ROUTE_ROUNDTABLE) },
                                    onToast = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(ROUTE_RESEARCH) {
                                // Observe safSyncedHashes & safRemoteHashes so Folder Ahead / In Sync updates reactively
                                val safStatus = remember(activeProject, safFolderBindings, safSyncedHashes, safRemoteHashes) {
                                    viewModel.computeSyncStatusLabel(activeProject)
                                }
                                ResearchScreen(
                                    uiState = uiState,
                                    activeProject = activeProject,
                                    routingPrefs = routingPrefs,
                                    syncTelemetry = syncTelemetry,
                                    safSyncStatus = safStatus,
                                    driveSyncStatus = viewModel.computeDriveSyncStatusLabel(activeProject),
                                    githubSyncStatus = viewModel.computeGitHubSyncStatusLabel(activeProject),
                                    currentUid = uid,
                                    envSecretCount = envSecrets.size,
                                    roundtableItems = roundtableItems,
                                    onUpdateQuery = viewModel::updateDraftQuery,
                                    onUpdateMode = viewModel::updateDraftMode,
                                    onUpdateFormat = viewModel::updateDraftFormat,
                                    onUpdateBlueprint = viewModel::updateDraftBlueprint,
                                    onUpdateRootFolder = viewModel::updateDraftRootFolder,
                                    onNewSession = viewModel::startNewSession,
                                    onRunPipeline = viewModel::runResearchPipeline,
                                    onTranscribeVoiceNote = viewModel::transcribeRecordedAudioNote,
                                    onSelectVoiceNoteVariant = viewModel::toggleVoiceNoteTranscriptMode,
                                    onChangeVoiceNoteCategory = viewModel::updateLastVoiceNoteCategory,
                                    onUndoVoiceNote = viewModel::undoLastVoiceNoteTranscript,
                                    onSaveVoiceNoteToRepo = viewModel::saveLastVoiceNoteToRepo,
                                    onDismissVoiceNoteReview = viewModel::dismissVoiceNoteReview,
                                    onRetryStage = viewModel::retryPipelineFromStage,
                                    onSendChatFollowUp = viewModel::sendChatFollowUp,
                                    onApprovePrd = {
                                        viewModel.approveAndGeneratePrd()
                                        navigateTo(ROUTE_PRD)
                                    },
                                    onOpenRouterSettings = { showRouterSheet = true },
                                    onOpenEnvVault = { showEnvVaultSheet = true },
                                    onOpenTeamShare = { showTeamSheet = true },
                                    onPushGitHub = {
                                        if (routingPrefs.githubPat.isBlank()) showGitHubSheet = true
                                        else viewModel.pushToGitHub()
                                    },
                                    onPullGitHub = viewModel::pullFromGitHub,
                                    onOpenGitHubConsole = { showGitHubSheet = true },
                                    onPushDrive = { viewModel.requestDriveSync(context, PendingDriveAction.PUSH) },
                                    onPullDrive = { viewModel.requestDriveSync(context, PendingDriveAction.PULL) },
                                    onOpenDriveConsole = { showDriveSheet = true },
                                    onBindSafFolder = viewModel::bindSafFolder,
                                    onPushSaf = viewModel::pushToSafFolder,
                                    onPullSaf = viewModel::pullFromSafFolder,
                                    onRefreshSafStatus = { viewModel.refreshSafFolderStatus(activeProject) },
                                    onCreateRepoFile = viewModel::createRepoFile,
                                    onSaveDiagramBoard = viewModel::saveDiagramBoard,
                                    onMoveRenameRepoFile = viewModel::moveOrRenameRepoFile,
                                    onEditRepoFile = viewModel::updateRepoFileContent,
                                    onDeleteRepoFile = viewModel::deleteRepoFile,
                                    onRawGitHubUrl = viewModel::rawGitHubUrlFor,
                                    onShareDoc = viewModel::shareDocument,
                                    onOpenExportStudio = { showExportSheet = true },
                                    onOpenPrdStudio = { navigateTo(ROUTE_PRD) },
                                    onAiExtractPostGisEntities = viewModel::extractPostGisSchemaEntities,
                                    onToast = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                        composable(ROUTE_ROUNDTABLE) {
                            RoundtableScreen(
                                uiState = uiState,
                                activeProject = activeProject,
                                items = roundtableItems,
                                currentUid = uid,
                                externalRouterConfigured = routingPrefs.externalApiKey.isNotBlank(),
                                onAddModelFolder = viewModel::addModelFolder,
                                onRemoveModelFolder = viewModel::removeModelFolder,
                                onAddPastedIdea = viewModel::addPastedRoundtableIdea,
                                onIngestFiles = viewModel::ingestRoundtableFiles,
                                onAutoDispatchExternal = viewModel::autoDispatchToExternalModel,
                                onOpenEnvVault = { showEnvVaultSheet = true },
                                onScrutinizeSingle = viewModel::scrutinizeSingleRoundtableItem,
                                onRunScrutinyQueue = viewModel::runScrutinyQueue,
                                onRunCrossModelComparison = viewModel::runCrossModelComparison,
                                onAskRag = viewModel::askRoundtableRag,
                                onUpdateItemContent = viewModel::updateRoundtableItemContent,
                                onDeleteItem = viewModel::deleteRoundtableItem,
                                onMergeAdoptedIntoPrd = {
                                    viewModel.approveAndGeneratePrd(
                                        revisionRequest = "Integrate all ADOPTED and CONSENSUS_RESOLVED insights from A.i. Roundtable with model attribution.",
                                        isMajorBump = false
                                    )
                                    navigateTo(ROUTE_PRD)
                                },
                                onRawGitHubUrl = viewModel::rawGitHubUrlFor,
                                onOpenDashboard = { navigateTo(ROUTE_DASHBOARD) },
                                onOpenResearch = { navigateTo(ROUTE_RESEARCH) },
                                onToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }

                        composable(ROUTE_PRD) {
                            PrdStudioScreen(
                                uiState = uiState,
                                activeProject = activeProject,
                                prds = prds,
                                currentUid = uid,
                                onGenerateInitialPrd = { viewModel.approveAndGeneratePrd() },
                                onRevisePrd = { req, major -> viewModel.approveAndGeneratePrd(req, major) },
                                onRefreshPrdIndex = viewModel::refreshPrdIndex,
                                onExportSingleFile = viewModel::exportSingleFile,
                                onShareDoc = viewModel::shareDocument,
                                onOpenDashboard = { navigateTo(ROUTE_DASHBOARD) },
                                onOpenResearch = { navigateTo(ROUTE_RESEARCH) },
                                onToast = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    if (showRouterSheet) {
        ModelRouterAndGitHubSheet(
            currentPrefs = routingPrefs,
            envSecretCount = envSecrets.size,
            envAutoFill = envAutoFillResolution,
            onOpenEnvVault = {
                showRouterSheet = false
                showEnvVaultSheet = true
            },
            onDismiss = { showRouterSheet = false },
            onSave = viewModel::saveRoutingPreferences,
            onToggleStrategicRouting = viewModel::setStrategicRoutingEnabled,
            onVerifyGitHub = viewModel::verifyGitHubToken
        )
    }

    if (showEnvVaultSheet) {
        EnvSecretsVaultSheet(
            entries = envSecrets,
            autoFillResolution = envAutoFillResolution,
            onDismiss = { showEnvVaultSheet = false },
            onUpsertEntry = viewModel::upsertEnvSecret,
            onDeleteEntry = viewModel::deleteEnvSecret,
            onUpdatePermissions = viewModel::updateEnvSecretPermissions,
            onToggleAutoFill = viewModel::toggleEnvSecretAutoFill,
            onImportEnvUri = viewModel::importEnvFileFromUri,
            onCreateStarterEnv = viewModel::createStarterEnvVault,
            onExportEnvUri = viewModel::exportEnvFileToUri,
            onApplyAutoFillNow = viewModel::applyDiscreteEnvAutoFillNow
        )
    }

    if (showTeamSheet && activeProject != null) {
        TeamAndPublicShareSheet(
            project = activeProject,
            currentUid = uid,
            lastInvite = uiState.lastInvite,
            onDismiss = { showTeamSheet = false },
            onCreateInvite = viewModel::createTeamInvite,
            onChangeRole = viewModel::updateMemberRole,
            onRemoveMember = viewModel::removeTeamMember,
            onTogglePublicShare = viewModel::togglePublicShare,
            onMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showDriveSheet && activeProject != null) {
        GoogleDriveSyncSheet(
            project = activeProject,
            driveSyncStatus = viewModel.computeDriveSyncStatusLabel(activeProject),
            driveWebUrl = viewModel.driveWebUrlFor(activeProject),
            lastSyncLabel = driveLastLabels[activeProject.id],
            lastReport = uiState.lastDriveReport,
            syncing = uiState.syncingDrive,
            canEdit = activeProject.canEdit(uid),
            onDismiss = { showDriveSheet = false },
            onApplyTemplate = viewModel::applyWorkspaceTemplateLocally,
            onPushToDrive = { viewModel.requestDriveSync(context, PendingDriveAction.PUSH) },
            onPullFromDrive = { viewModel.requestDriveSync(context, PendingDriveAction.PULL) }
        )
    }

    if (showGitHubSheet && activeProject != null) {
        GitHubMirrorSheet(
            project = activeProject,
            routingPrefs = routingPrefs,
            githubSyncStatus = viewModel.computeGitHubSyncStatusLabel(activeProject),
            lastSyncLabel = githubLastLabels[activeProject.id],
            lastReport = uiState.lastGitHubReport,
            userRepos = uiState.githubUserRepos,
            loadingRepos = uiState.loadingGitHubRepos,
            syncing = uiState.syncingGitHub,
            canEdit = activeProject.canEdit(uid),
            hasBoundSafFolder = !safFolderBindings[activeProject.id].isNullOrBlank(),
            deviceSession = uiState.githubDeviceSession,
            deviceAuthPolling = uiState.githubDevicePolling,
            onDismiss = { showGitHubSheet = false },
            onVerifyPat = { pat -> viewModel.verifyGitHubToken(pat) },
            onStartDeviceAuth = viewModel::startGitHubDeviceAuth,
            onCancelDeviceAuth = viewModel::cancelGitHubDeviceAuth,
            onDisconnectGitHub = viewModel::disconnectGitHub,
            onToggleAutoMirror = viewModel::setGitHubAutoMirror,
            onToggleDefaultPrivate = viewModel::setGitHubDefaultPrivate,
            onRefreshRepos = viewModel::refreshGitHubRepositories,
            onLinkExistingRepo = viewModel::linkExistingGitHubRepo,
            onMirrorWorkspaceNow = { viewModel.pushToGitHub() },
            onMirrorBoundFolderNow = viewModel::mirrorBoundLocalFolderToGitHub,
            onPullFromGitHub = viewModel::pullFromGitHub,
            onToast = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }

    if (showExportSheet && activeProject != null) {
        val exportFiles = remember(activeProject, prds, roundtableItems) {
            viewModel.buildAllRepoFiles(activeProject)
        }
        ProjectExportSheet(
            project = activeProject,
            files = exportFiles,
            prdCount = prds.size,
            roundtableCount = roundtableItems.size,
            exporting = uiState.exportingReport,
            onDismiss = { showExportSheet = false },
            onExportProjectToUri = viewModel::exportProjectToUri,
            onShareProjectExport = viewModel::shareProjectExport,
            onExportSingleFileToUri = viewModel::exportProjectFileToUri,
            onShareSingleFile = viewModel::shareProjectFile
        )
    }

    val visibleConflict = activeSyncConflict?.takeIf { it.conflictId != dismissedConflictId }
    if (visibleConflict != null) {
        SyncConflictResolutionDialog(
            conflict = visibleConflict,
            isResolving = resolvingConflict,
            onResolve = { strategy ->
                dismissedConflictId = null
                viewModel.resolveActiveSyncConflict(strategy)
            },
            onDismissLater = {
                dismissedConflictId = visibleConflict.conflictId
            }
        )
    }
}
