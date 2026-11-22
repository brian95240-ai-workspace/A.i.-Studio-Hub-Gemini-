package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ExportFileFormat
import com.example.data.model.MetadataTagEngine
import com.example.data.model.ResearchMode
import com.example.data.model.ResearchProject
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.sync.OfflineSyncTelemetry
import com.example.data.voice.DictationTarget
import com.example.ui.components.BulkSelectionModeBanner
import com.example.ui.components.BulkZipExportDock
import com.example.ui.components.DashboardChatSearchMatchCard
import com.example.ui.components.DashboardCommandHero
import com.example.ui.components.DashboardProjectCard
import com.example.ui.components.RecentPrdQuickCard
import com.example.ui.components.RecentRoundtableQuickCard
import com.example.ui.components.SyncFleetCoverageStrip
import com.example.ui.components.SystemsArchitectTemplateSheet
import com.example.ui.components.TelemetryPill
import com.example.ui.components.VoiceListeningPanel
import com.example.ui.components.rememberVoiceDictationHandle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.DashboardProjectFilter
import com.example.ui.viewmodel.DashboardProjectSummary
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.SyncChannelHealth

@Composable
fun ProjectDashboardScreen(
    dashboardState: DashboardUiState,
    currentUid: String,
    userDisplayName: String,
    syncTelemetry: OfflineSyncTelemetry,
    exportingReport: Boolean,
    initializingTemplateProject: Boolean,
    envSecretCount: Int,
    onNewProject: () -> Unit,
    onInitializeProjectFromTemplate: (
        title: String,
        rootFolderName: String,
        blueprint: ScaffoldingBlueprint,
        fileFormat: ExportFileFormat,
        domain: String,
        extraFolders: List<String>,
        openAfterCreate: Boolean,
        onComplete: () -> Unit
    ) -> Unit,
    onJoinProject: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onOpenEnvVault: () -> Unit,
    onOpenRouterSettings: () -> Unit,
    onSignOut: () -> Unit,
    onOpenProjectResearch: (ResearchProject) -> Unit,
    onOpenProjectPrd: (ResearchProject) -> Unit,
    onOpenProjectRoundtable: (ResearchProject) -> Unit,
    onOpenProjectGitHub: (ResearchProject) -> Unit,
    onOpenProjectDrive: (ResearchProject) -> Unit,
    onOpenProjectExport: (ResearchProject) -> Unit,
    onOpenProjectTeamShare: (ResearchProject) -> Unit,
    onDeleteProject: (ResearchProject) -> Unit,
    onSuggestedBulkZipName: (Map<String, Set<String>>) -> String,
    onSaveBulkZipToUri: (Uri, Map<String, Set<String>>, Boolean, () -> Unit) -> Unit,
    onShareBulkZip: (Map<String, Set<String>>, Boolean, () -> Unit) -> Unit,
    onOpenActivePrdStudio: () -> Unit,
    onOpenActiveRoundtable: () -> Unit,
    onToast: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(DashboardProjectFilter.ALL) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showTemplateInitSheet by remember { mutableStateOf(false) }
    var projectPendingDelete by remember { mutableStateOf<ResearchProject?>(null) }
    var inviteInput by remember { mutableStateOf("") }

    // Reuse VortexSpeechController + rememberVoiceDictationHandle for voice search
    val voiceSearchHandle = rememberVoiceDictationHandle(
        mode = ResearchMode.PROJECT,
        onTranscriptReady = { target, spoken ->
            if (target == DictationTarget.SEARCH) {
                val clean = spoken.trim()
                if (clean.isNotBlank()) {
                    searchQuery = clean
                }
            }
        },
        onNotice = onToast
    )

    // Bulk selection state: maps projectId -> set of selected relative file paths
    var bulkMode by remember { mutableStateOf(false) }
    var selectedFilesByProject by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var expandedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var includeManifest by remember { mutableStateOf(true) }

    fun exitBulkMode() {
        bulkMode = false
        selectedFilesByProject = emptyMap()
        expandedProjectIds = emptySet()
    }

    BackHandler(enabled = bulkMode) {
        exitBulkMode()
    }

    val createBulkZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onSaveBulkZipToUri(uri, selectedFilesByProject, includeManifest) {
                exitBulkMode()
            }
        }
    }

    val popularTags = remember(dashboardState.projects) {
        MetadataTagEngine.collectPopularFilterTags(
            projects = dashboardState.projects.map { it.project },
            maxCount = 14
        )
    }

    val isSearchActive = searchQuery.isNotBlank() || selectedTagFilter != null

    val searchResults = remember(dashboardState.projects, searchQuery, selectedTagFilter) {
        MetadataTagEngine.searchProjectsAndChats(
            projects = dashboardState.projects.map { it.project },
            rawQuery = searchQuery,
            selectedTag = selectedTagFilter
        )
    }

    val filteredProjects = remember(dashboardState.projects, selectedFilter, isSearchActive, searchResults) {
        val base = dashboardState.projects.filter { summary ->
            val matchesFilter = when (selectedFilter) {
                DashboardProjectFilter.ALL -> true
                DashboardProjectFilter.GITHUB -> summary.project.githubRepoUrl.isNotBlank()
                DashboardProjectFilter.DRIVE -> summary.driveBadge.health != SyncChannelHealth.UNLINKED
                DashboardProjectFilter.NEEDS_SYNC -> summary.hasPendingSync
            }
            val matchesSearch = !isSearchActive || searchResults.projectHitsById.containsKey(summary.project.id)
            matchesFilter && matchesSearch
        }
        if (isSearchActive) {
            base.sortedByDescending { searchResults.projectHitsById[it.project.id]?.score ?: 0 }
        } else {
            base
        }
    }

    val visibleChatMatches = remember(isSearchActive, searchResults, filteredProjects) {
        if (!isSearchActive) emptyList()
        else {
            val allowedProjectIds = filteredProjects.map { it.project.id }.toSet()
            searchResults.chatMatches.filter { it.project.id in allowedProjectIds }
        }
    }

    fun toggleProjectSelection(summary: DashboardProjectSummary) {
        val pid = summary.project.id
        val allPaths = summary.selectableFiles.map { it.relativePath }.toSet()
        val current = selectedFilesByProject[pid].orEmpty()
        selectedFilesByProject = if (allPaths.isNotEmpty() && current.size >= allPaths.size) {
            selectedFilesByProject - pid
        } else if (allPaths.isNotEmpty()) {
            selectedFilesByProject + (pid to allPaths)
        } else {
            selectedFilesByProject
        }
    }

    fun toggleSingleFileSelection(projectId: String, relativePath: String) {
        if (relativePath.isBlank()) return
        val current = selectedFilesByProject[projectId].orEmpty()
        val updated = if (relativePath in current) current - relativePath else current + relativePath
        selectedFilesByProject = if (updated.isEmpty()) {
            selectedFilesByProject - projectId
        } else {
            selectedFilesByProject + (projectId to updated)
        }
    }

    fun selectAllFilesForProject(summary: DashboardProjectSummary) {
        val allPaths = summary.selectableFiles.map { it.relativePath }.toSet()
        if (allPaths.isNotEmpty()) {
            selectedFilesByProject = selectedFilesByProject + (summary.project.id to allPaths)
        }
    }

    fun clearFilesForProject(projectId: String) {
        selectedFilesByProject = selectedFilesByProject - projectId
    }

    val summariesById = remember(dashboardState.projects) {
        dashboardState.projects.associateBy { it.project.id }
    }

    val selectionMetrics = remember(selectedFilesByProject, summariesById) {
        var projCount = 0
        var fileCount = 0
        var totalBytes = 0L
        selectedFilesByProject.forEach { (pid, paths) ->
            if (paths.isEmpty()) return@forEach
            val summary = summariesById[pid] ?: return@forEach
            val picked = summary.selectableFiles.filter { it.relativePath in paths }
            if (picked.isNotEmpty()) {
                projCount++
                fileCount += picked.size
                totalBytes += picked.sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() }
            }
        }
        Triple(projCount, fileCount, totalBytes)
    }
    val (selectedProjectCount, selectedFileCount, estimatedBytes) = selectionMetrics

    val allVisibleSelected = remember(filteredProjects, selectedFilesByProject) {
        val nonEmptyVisible = filteredProjects.filter { it.selectableFiles.isNotEmpty() }
        nonEmptyVisible.isNotEmpty() && nonEmptyVisible.all { summary ->
            val current = selectedFilesByProject[summary.project.id].orEmpty()
            current.size >= summary.selectableFiles.size
        }
    }

    fun toggleSelectAllVisible() {
        selectedFilesByProject = if (allVisibleSelected) {
            selectedFilesByProject - filteredProjects.map { it.project.id }.toSet()
        } else {
            val next = selectedFilesByProject.toMutableMap()
            filteredProjects.forEach { summary ->
                val allPaths = summary.selectableFiles.map { it.relativePath }.toSet()
                if (allPaths.isNotEmpty()) {
                    next[summary.project.id] = allPaths
                }
            }
            next
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp
        val projectRows: List<List<DashboardProjectSummary>> = remember(filteredProjects, isWideScreen) {
            if (isWideScreen) filteredProjects.chunked(2) else filteredProjects.map { listOf(it) }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("project_dashboard_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Atmospheric Command Hero
                item(key = "hero") {
                    DashboardCommandHero(
                        userDisplayName = userDisplayName,
                        dashboardState = dashboardState,
                        syncTelemetry = syncTelemetry,
                        bulkMode = bulkMode,
                        envSecretCount = envSecretCount,
                        onNewProject = onNewProject,
                        onInitFromTemplate = { showTemplateInitSheet = true },
                        onOpenJoinDialog = { showJoinDialog = true },
                        onToggleBulkMode = {
                            if (bulkMode) exitBulkMode() else bulkMode = true
                        },
                        onTriggerSync = onTriggerSync,
                        onOpenEnvVault = onOpenEnvVault,
                        onOpenRouterSettings = onOpenRouterSettings,
                        onSignOut = onSignOut
                    )
                }

                // 1b. Contextual Bulk Selection Mode Banner
                item(key = "bulk_banner") {
                    AnimatedVisibility(visible = bulkMode) {
                        BulkSelectionModeBanner(
                            selectedProjectCount = selectedProjectCount,
                            selectedFileCount = selectedFileCount,
                            estimatedBytes = estimatedBytes,
                            allVisibleSelected = allVisibleSelected,
                            onSelectAllVisible = ::toggleSelectAllVisible,
                            onClearSelection = { selectedFilesByProject = emptyMap() },
                            onExitBulkMode = ::exitBulkMode
                        )
                    }
                }

                // 2. Sync Coverage Matrix Strip (GitHub | Drive | Local)
                item(key = "sync_coverage") {
                    SyncFleetCoverageStrip(
                        dashboardState = dashboardState,
                        selectedFilter = selectedFilter,
                        onSelectFilter = { selectedFilter = it }
                    )
                }

                // 3. Active Projects Fleet Header, Search & Filters (Primary Central Dashboard Section)
                item(key = "active_projects_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardSectionHeader(
                            icon = Icons.Outlined.FolderSpecial,
                            title = stringResource(R.string.dashboard_active_projects),
                            countBadge = "${filteredProjects.size}/${dashboardState.totalProjects}",
                            accent = ElectricCyan,
                            actionLabel = if (bulkMode) stringResource(R.string.dashboard_bulk_done)
                            else stringResource(R.string.dashboard_bulk_select),
                            onAction = {
                                if (bulkMode) exitBulkMode() else bulkMode = true
                            }
                        )

                        val isListeningSearch = voiceSearchHandle.state.activeTarget == DictationTarget.SEARCH
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.testTag("dashboard_search_clear_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = stringResource(R.string.cd_dashboard_clear_search),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { voiceSearchHandle.startOrToggle(DictationTarget.SEARCH) },
                                        modifier = Modifier.testTag("dashboard_search_mic_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isListeningSearch) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                            contentDescription = if (isListeningSearch) {
                                                stringResource(R.string.cd_dashboard_voice_search_stop)
                                            } else {
                                                stringResource(R.string.cd_dashboard_voice_search)
                                            },
                                            tint = if (isListeningSearch) MaterialTheme.colorScheme.error else ElectricCyan
                                        )
                                    }
                                }
                            },
                            placeholder = { Text(stringResource(R.string.dashboard_search_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_search_input")
                        )

                        if (isListeningSearch) {
                            VoiceListeningPanel(
                                state = voiceSearchHandle.state,
                                mode = ResearchMode.PROJECT,
                                onDone = voiceSearchHandle.stopAndKeepPartial,
                                onCancel = voiceSearchHandle.cancel
                            )
                        }

                        if (popularTags.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.testTag("dashboard_tag_filter_row")
                            ) {
                                items(popularTags, key = { it.lowercase() }) { tag ->
                                    val selected = selectedTagFilter.equals(tag, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedTagFilter = if (selected) null else tag
                                        },
                                        label = {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = ElectricCyan
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("dashboard_tag_chip_${tag.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DashboardProjectFilter.entries, key = { it.name }) { filter ->
                                val selected = selectedFilter == filter
                                val countSuffix = when (filter) {
                                    DashboardProjectFilter.ALL -> " (${dashboardState.totalProjects})"
                                    DashboardProjectFilter.GITHUB -> " (${dashboardState.githubLinkedCount})"
                                    DashboardProjectFilter.DRIVE -> " (${dashboardState.driveLinkedCount})"
                                    DashboardProjectFilter.NEEDS_SYNC -> " (${dashboardState.pendingSyncProjectsCount})"
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label}$countSuffix") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("dashboard_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // 3b. Matching Chats across Projects (only revealed during active search or tag filtering; hidden during normal browsing)
                if (isSearchActive && visibleChatMatches.isNotEmpty()) {
                    item(key = "matching_chats_header") {
                        DashboardSectionHeader(
                            icon = Icons.Outlined.Forum,
                            title = stringResource(R.string.dashboard_matching_chats),
                            countBadge = "${visibleChatMatches.size}",
                            accent = VerifiedEmerald,
                            actionLabel = "Clear",
                            onAction = {
                                searchQuery = ""
                                selectedTagFilter = null
                            }
                        )
                    }
                    items(
                        items = visibleChatMatches.take(6),
                        key = { "chat_match_${it.project.id}_${it.turn.id}_${it.turnIndex}" }
                    ) { match ->
                        DashboardChatSearchMatchCard(
                            match = match,
                            onOpenProject = { onOpenProjectResearch(match.project) }
                        )
                    }
                }

                // 4. Active Projects Grid / List (Chats stay hidden until a project is opened)
                if (filteredProjects.isEmpty()) {
                    item(key = "empty_projects") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderSpecial,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (dashboardState.totalProjects == 0) {
                                        "Launch your first research project"
                                    } else {
                                        "No projects match this filter"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (dashboardState.totalProjects == 0) {
                                        "Start a 7-stage synthesis session to scaffold a workspace and sync across Local, Drive, and GitHub."
                                    } else {
                                        "Clear the search query or switch filters to see all active projects."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                if (dashboardState.totalProjects == 0) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { showTemplateInitSheet = true },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("dashboard_empty_init_template_button")
                                        ) {
                                            Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("From Template")
                                        }
                                        OutlinedButton(
                                            onClick = onNewProject,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Quick Session")
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            selectedTagFilter = null
                                            selectedFilter = DashboardProjectFilter.ALL
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Reset Filters")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = projectRows,
                        key = { row -> row.joinToString("_") { it.project.id } }
                    ) { rowItems ->
                        if (rowItems.size == 1) {
                            val summary = rowItems.first()
                            val pid = summary.project.id
                            val hit = if (isSearchActive) searchResults.projectHitsById[pid] else null
                            DashboardProjectCard(
                                summary = summary,
                                bulkMode = bulkMode,
                                selectedFilePaths = selectedFilesByProject[pid].orEmpty(),
                                isFilesExpanded = pid in expandedProjectIds,
                                matchedTags = hit?.matchedTags.orEmpty(),
                                matchedChatCount = hit?.matchedChatCount ?: 0,
                                onLongPressSelect = {
                                    if (!bulkMode) {
                                        bulkMode = true
                                        selectAllFilesForProject(summary)
                                    } else {
                                        toggleProjectSelection(summary)
                                    }
                                },
                                onToggleSelectProject = { toggleProjectSelection(summary) },
                                onToggleExpandFiles = {
                                    expandedProjectIds = if (pid in expandedProjectIds) {
                                        expandedProjectIds - pid
                                    } else {
                                        expandedProjectIds + pid
                                    }
                                },
                                onToggleSelectFile = { relPath -> toggleSingleFileSelection(pid, relPath) },
                                onSelectAllProjectFiles = { selectAllFilesForProject(summary) },
                                onClearProjectFiles = { clearFilesForProject(pid) },
                                onOpenResearch = { onOpenProjectResearch(summary.project) },
                                onOpenPrds = { onOpenProjectPrd(summary.project) },
                                onOpenRoundtable = { onOpenProjectRoundtable(summary.project) },
                                onOpenGitHubConsole = { onOpenProjectGitHub(summary.project) },
                                onOpenDriveConsole = { onOpenProjectDrive(summary.project) },
                                onSyncLocal = onTriggerSync,
                                onOpenExport = { onOpenProjectExport(summary.project) },
                                onOpenTeamShare = { onOpenProjectTeamShare(summary.project) },
                                canDelete = summary.project.ownerId == currentUid,
                                onRequestDelete = { projectPendingDelete = summary.project }
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { summary ->
                                    val pid = summary.project.id
                                    val hit = if (isSearchActive) searchResults.projectHitsById[pid] else null
                                    DashboardProjectCard(
                                        summary = summary,
                                        bulkMode = bulkMode,
                                        selectedFilePaths = selectedFilesByProject[pid].orEmpty(),
                                        isFilesExpanded = pid in expandedProjectIds,
                                        matchedTags = hit?.matchedTags.orEmpty(),
                                        matchedChatCount = hit?.matchedChatCount ?: 0,
                                        onLongPressSelect = {
                                            if (!bulkMode) {
                                                bulkMode = true
                                                selectAllFilesForProject(summary)
                                            } else {
                                                toggleProjectSelection(summary)
                                            }
                                        },
                                        onToggleSelectProject = { toggleProjectSelection(summary) },
                                        onToggleExpandFiles = {
                                            expandedProjectIds = if (pid in expandedProjectIds) {
                                                expandedProjectIds - pid
                                            } else {
                                                expandedProjectIds + pid
                                            }
                                        },
                                        onToggleSelectFile = { relPath -> toggleSingleFileSelection(pid, relPath) },
                                        onSelectAllProjectFiles = { selectAllFilesForProject(summary) },
                                        onClearProjectFiles = { clearFilesForProject(pid) },
                                        onOpenResearch = { onOpenProjectResearch(summary.project) },
                                        onOpenPrds = { onOpenProjectPrd(summary.project) },
                                        onOpenRoundtable = { onOpenProjectRoundtable(summary.project) },
                                        onOpenGitHubConsole = { onOpenProjectGitHub(summary.project) },
                                        onOpenDriveConsole = { onOpenProjectDrive(summary.project) },
                                        onSyncLocal = onTriggerSync,
                                        onOpenExport = { onOpenProjectExport(summary.project) },
                                        onOpenTeamShare = { onOpenProjectTeamShare(summary.project) },
                                        canDelete = summary.project.ownerId == currentUid,
                                        onRequestDelete = { projectPendingDelete = summary.project },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 5. Quick-Access Carousel: Recent PRDs
                item(key = "recent_prds_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardSectionHeader(
                            icon = Icons.Outlined.Description,
                            title = stringResource(R.string.dashboard_recent_prds),
                            countBadge = "${dashboardState.totalPrdsCount}",
                            accent = VerifiedEmerald,
                            actionLabel = "PRD Studio",
                            onAction = onOpenActivePrdStudio
                        )
                        if (dashboardState.recentPrds.isEmpty()) {
                            DashboardEmptyStripCard(
                                icon = Icons.Outlined.Description,
                                title = "No versioned PRDs yet",
                                description = "Approve a 7-stage research synthesis to publish PRD v1.0 and track deltas here.",
                                ctaLabel = "Open PRD Studio",
                                testTag = "dashboard_empty_prds_cta",
                                onCta = onOpenActivePrdStudio
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_recent_prds_row")
                            ) {
                                items(dashboardState.recentPrds, key = { "${it.project.id}_${it.prd.id}" }) { prdItem ->
                                    val isSelected = prdItem.archiveRelativePath.isNotBlank() &&
                                        prdItem.archiveRelativePath in selectedFilesByProject[prdItem.project.id].orEmpty()
                                    RecentPrdQuickCard(
                                        item = prdItem,
                                        bulkMode = bulkMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (bulkMode) {
                                                toggleSingleFileSelection(prdItem.project.id, prdItem.archiveRelativePath)
                                            } else {
                                                onOpenProjectPrd(prdItem.project)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Quick-Access Carousel: Recent A.i. Roundtable (metadata only; discussion text stays hidden)
                item(key = "recent_roundtable_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardSectionHeader(
                            icon = Icons.Outlined.Forum,
                            title = stringResource(R.string.dashboard_recent_roundtable),
                            countBadge = "${dashboardState.totalRoundtableCount}",
                            accent = TelemetryBlue,
                            actionLabel = "Roundtable",
                            onAction = onOpenActiveRoundtable
                        )
                        if (dashboardState.recentRoundtable.isEmpty()) {
                            DashboardEmptyStripCard(
                                icon = Icons.Outlined.Forum,
                                title = "No Roundtable discussions yet",
                                description = "Upload external AI proposals or code snippets to compare models and open them inside a project.",
                                ctaLabel = "Open Roundtable",
                                testTag = "dashboard_empty_roundtable_cta",
                                onCta = onOpenActiveRoundtable
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_recent_roundtable_row")
                            ) {
                                items(dashboardState.recentRoundtable, key = { "${it.project.id}_${it.item.id}" }) { rtItem ->
                                    val isSelected = rtItem.archiveRelativePath.isNotBlank() &&
                                        rtItem.archiveRelativePath in selectedFilesByProject[rtItem.project.id].orEmpty()
                                    RecentRoundtableQuickCard(
                                        entry = rtItem,
                                        bulkMode = bulkMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (bulkMode) {
                                                toggleSingleFileSelection(rtItem.project.id, rtItem.archiveRelativePath)
                                            } else {
                                                onOpenProjectRoundtable(rtItem.project)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Sticky Bottom Action Dock for Bulk Compressed ZIP Backup & Share
            BulkZipExportDock(
                visible = bulkMode && selectedFileCount > 0,
                selectedProjectCount = selectedProjectCount,
                selectedFileCount = selectedFileCount,
                estimatedBytes = estimatedBytes,
                includeManifest = includeManifest,
                exporting = exportingReport,
                onToggleIncludeManifest = { includeManifest = !includeManifest },
                onSaveZip = {
                    val fileName = onSuggestedBulkZipName(selectedFilesByProject)
                    createBulkZipLauncher.launch(fileName)
                },
                onShareZip = {
                    onShareBulkZip(selectedFilesByProject, includeManifest) {
                        exitBulkMode()
                    }
                }
            )
        }
    }

    if (showTemplateInitSheet) {
        SystemsArchitectTemplateSheet(
            initializing = initializingTemplateProject,
            onDismiss = { showTemplateInitSheet = false },
            onInitialize = { title, rootFolder, blueprint, fileFormat, domain, extraFolders, openAfterCreate ->
                onInitializeProjectFromTemplate(
                    title,
                    rootFolder,
                    blueprint,
                    fileFormat,
                    domain,
                    extraFolders,
                    openAfterCreate
                ) {
                    showTemplateInitSheet = false
                }
            }
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.join_project_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste an invite link (vortexresearcher://join?code=...) or invite code shared by a project Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = { inviteInput = it },
                        label = { Text("Invite URL or code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_join_invite_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteInput.isNotBlank()) {
                            onJoinProject(inviteInput)
                            inviteInput = ""
                            showJoinDialog = false
                        }
                    },
                    modifier = Modifier.testTag("dashboard_confirm_join_button")
                ) { Text("Join Project") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }

    projectPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { projectPendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Delete Project?") },
            text = {
                Text(
                    text = "Permanently remove '${target.title.ifBlank { target.rootFolderName }}' from your local cache and cloud workspace? External GitHub repos and Google Drive copies are not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(target)
                        projectPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("dashboard_confirm_delete_project_button")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DashboardSectionHeader(
    icon: ImageVector,
    title: String,
    countBadge: String,
    accent: androidx.compose.ui.graphics.Color,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TelemetryPill(text = countBadge, accentColor = accent)
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DashboardEmptyStripCard(
    icon: ImageVector,
    title: String,
    description: String,
    ctaLabel: String,
    testTag: String,
    onCta: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onCta,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(testTag)
            ) {
                Text(ctaLabel)
            }
        }
    }
}
