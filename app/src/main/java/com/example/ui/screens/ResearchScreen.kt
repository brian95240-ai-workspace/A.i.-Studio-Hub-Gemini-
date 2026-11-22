package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToDrive
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatTurn
import com.example.data.ai.ExternalRouterApi
import com.example.data.model.ExportFileFormat
import com.example.data.model.ExternalRouterType
import com.example.data.model.GapVerdict
import com.example.data.model.MetadataTagEngine
import com.example.data.model.NoteCategory
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchMode
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoutingPreferences
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.sync.OfflineSyncTelemetry
import com.example.data.voice.DictationTarget
import com.example.data.voice.VortexSpeechController
import com.example.ui.components.BlueprintGuideSheet
import com.example.ui.components.CreateRepoFileDialog
import com.example.ui.components.EditRepoFileDialog
import com.example.ui.components.MarkdownPreviewView
import com.example.ui.components.MermaidDiagramCard
import com.example.ui.components.MonospaceCodeBlock
import com.example.ui.components.MoveRenameRepoFileDialog
import com.example.ui.components.PgSchemaMappingPlan
import com.example.ui.components.PostGisSchemaMapperCard
import com.example.ui.components.ResearchSpecializedArtifactsCard
import com.example.ui.components.ResearchSynthesisDiagramView
import com.example.ui.components.SpecializedFormatCard
import com.example.ui.components.SpecializedFormatParser
import com.example.ui.components.StagePipelineCard
import com.example.ui.components.TelemetryPill
import com.example.ui.components.VoiceListeningPanel
import com.example.ui.components.VoiceNoteErrorBanner
import com.example.ui.components.VoiceNoteRecordButton
import com.example.ui.components.VoiceNoteRecordingPanel
import com.example.ui.components.VoiceNoteTranscriptReviewCard
import com.example.ui.components.VoiceNoteTranscribingBanner
import com.example.ui.components.copyToClipboard
import com.example.ui.components.noteCategoryAccent
import com.example.ui.components.rememberVoiceDictationHandle
import com.example.ui.components.rememberVoiceNoteRecorderHandle
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.VortexUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResearchScreen(
    uiState: VortexUiState,
    activeProject: ResearchProject?,
    routingPrefs: RoutingPreferences,
    syncTelemetry: OfflineSyncTelemetry,
    safSyncStatus: String,
    driveSyncStatus: String,
    githubSyncStatus: String,
    currentUid: String,
    envSecretCount: Int = 0,
    roundtableItems: List<RoundtableItem> = emptyList(),
    onUpdateQuery: (String) -> Unit,
    onUpdateMode: (ResearchMode) -> Unit,
    onUpdateFormat: (ExportFileFormat) -> Unit,
    onUpdateBlueprint: (ScaffoldingBlueprint) -> Unit,
    onUpdateRootFolder: (String) -> Unit,
    onNewSession: () -> Unit,
    onRunPipeline: () -> Unit,
    onTranscribeVoiceNote: (audioBytes: ByteArray, mimeType: String, autoRunPipeline: Boolean, saveNoteToRepo: Boolean) -> Unit = { _, _, _, _ -> },
    onSelectVoiceNoteVariant: (useRefinedBrief: Boolean) -> Unit = {},
    onChangeVoiceNoteCategory: (NoteCategory) -> Unit = {},
    onUndoVoiceNote: () -> Unit = {},
    onSaveVoiceNoteToRepo: () -> Unit = {},
    onDismissVoiceNoteReview: () -> Unit = {},
    onRetryStage: (Int) -> Unit = {},
    onSendChatFollowUp: (String) -> Unit = {},
    onApprovePrd: () -> Unit,
    onOpenRouterSettings: () -> Unit,
    onOpenEnvVault: () -> Unit = {},
    onOpenTeamShare: () -> Unit,
    onPushGitHub: () -> Unit,
    onPullGitHub: () -> Unit,
    onOpenGitHubConsole: () -> Unit,
    onPushDrive: () -> Unit,
    onPullDrive: () -> Unit,
    onOpenDriveConsole: () -> Unit,
    onBindSafFolder: (Uri) -> Unit,
    onPushSaf: () -> Unit,
    onPullSaf: () -> Unit,
    onRefreshSafStatus: () -> Unit = {},
    onCreateRepoFile: (relativePath: String, content: String) -> Unit,
    onSaveDiagramBoard: (relativePath: String, boardJson: String) -> Unit = { _, _ -> },
    onMoveRenameRepoFile: (oldPath: String, newPath: String) -> Unit,
    onEditRepoFile: (relativePath: String, newContent: String) -> Unit,
    onDeleteRepoFile: (relativePath: String) -> Unit,
    onRawGitHubUrl: (String) -> String?,
    onShareDoc: (String, String) -> Unit,
    onOpenExportStudio: () -> Unit,
    onOpenPrdStudio: () -> Unit,
    onAiExtractPostGisEntities: ((corpus: String, projectTitle: String, onComplete: (Result<PgSchemaMappingPlan>) -> Unit) -> Unit)? = null,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showBlueprintGuide by remember { mutableStateOf(false) }
    var selectedFilePath by remember(activeProject?.id) { mutableStateOf<String?>(null) }
    var collapsedFolders by remember(activeProject?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var fileToMove by remember { mutableStateOf<ProjectFile?>(null) }
    var fileToEdit by remember { mutableStateOf<ProjectFile?>(null) }
    var followUpDraft by remember(activeProject?.id) { mutableStateOf("") }
    var showLocalSafTools by remember(activeProject?.id) { mutableStateOf(false) }
    var voiceNoteAutoRun by remember { mutableStateOf(false) }
    var voiceNoteSaveToRepo by remember { mutableStateOf(true) }

    val voiceNoteRecorder = rememberVoiceNoteRecorderHandle(
        onAudioCaptured = { clip ->
            onTranscribeVoiceNote(
                clip.bytes,
                clip.mimeType,
                voiceNoteAutoRun,
                voiceNoteSaveToRepo && activeProject != null
            )
        },
        onNotice = onToast
    )

    val voiceHandle = rememberVoiceDictationHandle(
        mode = uiState.draftMode,
        onTranscriptReady = { target, spoken ->
            when (target) {
                DictationTarget.QUERY -> {
                    val merged = VortexSpeechController.mergeTranscriptIntoField(uiState.draftQuery, spoken)
                    onUpdateQuery(merged)
                }
                DictationTarget.PROJECT_FOLDER -> {
                    val slug = VortexSpeechController.formatSpokenFolderSlug(spoken)
                    if (slug.isNotBlank()) {
                        onUpdateRootFolder(slug)
                        onToast("Transcribed folder name '$slug' — edit anytime before running.")
                    }
                }
                DictationTarget.FOLLOW_UP -> {
                    followUpDraft = VortexSpeechController.mergeTranscriptIntoField(followUpDraft, spoken)
                    onToast("Transcribed follow-up — review or edit before sending.")
                }
                DictationTarget.SEARCH -> Unit
            }
        },
        onNotice = onToast
    )

    LaunchedEffect(uiState.pipelineRunning) {
        if (uiState.pipelineRunning) {
            if (voiceHandle.state.isRecording) voiceHandle.cancel()
            if (voiceNoteRecorder.state.isRecording) voiceNoteRecorder.cancel()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onBindSafFolder(uri)
    }

    val canEdit = activeProject == null || activeProject.canEdit(currentUid)
    val stages = uiState.liveStages
    val files = remember(activeProject?.filesJson) { activeProject?.parsedFiles().orEmpty() }
    val selectedFile = remember(files, selectedFilePath) {
        files.firstOrNull { it.relativePath == selectedFilePath }
    }
    val availableFolders = remember(activeProject?.blueprintId, files) {
        val bpFolders = activeProject?.let {
            ScaffoldingBlueprint.fromWire(it.blueprintId).canonicalFolders
        }.orEmpty()
        val existingFolders = files.mapNotNull { file ->
            val idx = file.relativePath.lastIndexOf('/')
            if (idx > 0) file.relativePath.substring(0, idx) else null
        }
        (bpFolders + existingFolders).distinct()
    }
    val isProjectPendingSync = activeProject != null && activeProject.id in syncTelemetry.dirtyProjectIds
    val groupedFolders = remember(files) {
        files.groupBy { file ->
            val slashIdx = file.relativePath.lastIndexOf('/')
            if (slashIdx == -1) "" else file.relativePath.substring(0, slashIdx)
        }.toSortedMap(compareBy<String> { folder ->
            when {
                folder.isEmpty() -> "0"
                folder.equals("PRD", ignoreCase = true) -> "1_PRD"
                else -> "2_$folder"
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = if (activeProject != null && stages.any { it.status == "COMPLETED" }) 96.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header & Controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProject?.title?.ifBlank { "New Research Session" } ?: "Vortex Research Engine",
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (routingPrefs.strategicRoutingEnabled) {
                                "Strategic Routing: ON · ${routingPrefs.routingMode.label} (${routingPrefs.escalationThreshold}+)"
                            } else {
                                "Strategic Routing: OFF · Locked to Gemini 3.8 Flash"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (routingPrefs.strategicRoutingEnabled) MaterialTheme.colorScheme.primary else BridgeAmber,
                            modifier = Modifier
                                .clickable(onClick = onOpenRouterSettings)
                                .testTag("research_routing_status_label")
                        )
                    }
                    IconButton(
                        onClick = onNewSession,
                        modifier = Modifier.testTag("new_research_session_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Start new research session")
                    }
                    if (activeProject != null) {
                        IconButton(
                            onClick = onOpenExportStudio,
                            modifier = Modifier.testTag("open_export_studio_header_button")
                        ) {
                            Icon(
                                Icons.Outlined.PictureAsPdf,
                                contentDescription = stringResource(R.string.cd_export_project),
                                tint = VerifiedEmerald
                            )
                        }
                        IconButton(
                            onClick = onOpenTeamShare,
                            modifier = Modifier.testTag("open_team_share_button")
                        ) {
                            Icon(
                                Icons.Outlined.Groups,
                                contentDescription = stringResource(R.string.cd_share_team),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenEnvVault,
                        modifier = Modifier.testTag("open_env_vault_button")
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = "Open encrypted .env secrets vault",
                            tint = if (envSecretCount > 0) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenRouterSettings,
                        modifier = Modifier.testTag("open_router_settings_button")
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Chat Mode vs Project Mode Switcher
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ResearchMode.entries.forEach { mode ->
                        val selected = uiState.draftMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { if (canEdit) onUpdateMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_chip_${mode.wire.lowercase()}")
                        )
                    }
                }
            }

            // Project Mode Configuration Strip (Root Folder, .md/.txt, 4 Blueprints)
            if (uiState.draftMode == ResearchMode.PROJECT) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isListeningFolder = voiceHandle.state.activeTarget == DictationTarget.PROJECT_FOLDER
                            OutlinedTextField(
                                value = uiState.draftRootFolder,
                                onValueChange = onUpdateRootFolder,
                                enabled = canEdit && !uiState.pipelineRunning,
                                label = { Text("Project Root Folder Name") },
                                placeholder = { Text("e.g., quantum-cooling-architecture") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { voiceHandle.startOrToggle(DictationTarget.PROJECT_FOLDER) },
                                        enabled = canEdit && !uiState.pipelineRunning,
                                        modifier = Modifier.testTag("voice_dictate_folder_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isListeningFolder) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                            contentDescription = stringResource(R.string.cd_voice_dictate_folder),
                                            tint = if (isListeningFolder) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("root_folder_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExportFileFormat.entries.forEach { fmt ->
                                    FilterChip(
                                        selected = uiState.draftFileFormat == fmt,
                                        onClick = { if (canEdit) onUpdateFormat(fmt) },
                                        label = { Text(fmt.label) },
                                        modifier = Modifier.testTag("format_chip_${fmt.wire.lowercase()}")
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showBlueprintGuide = true }
                                    .testTag("blueprint_selector_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.FolderCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Blueprint: ${uiState.draftBlueprint.title}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = uiState.draftBlueprint.bestFor,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Examples",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            uiState.recommendedBlueprint?.let { rec ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = VerifiedEmerald.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("stage0_recommended_blueprint_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = VerifiedEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (rec == uiState.draftBlueprint) {
                                                "Stage 0 Best Fit active: ${rec.title}"
                                            } else {
                                                "Stage 0 recommends: ${rec.title}"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VerifiedEmerald,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (rec != uiState.draftBlueprint && canEdit) {
                                            TextButton(
                                                onClick = { onUpdateBlueprint(rec) },
                                                modifier = Modifier.testTag("apply_recommended_blueprint_button")
                                            ) {
                                                Text("Apply", color = VerifiedEmerald)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Research Query Composer (Chat Mode & Project Mode with Firebase AI Voice Note Recording)
            item {
                val anyRecording = voiceNoteRecorder.state.isRecording || voiceHandle.state.isRecording
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(
                        1.dp,
                        if (anyRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.draftQuery,
                            onValueChange = onUpdateQuery,
                            enabled = canEdit && !uiState.pipelineRunning && !uiState.transcribingVoiceNote,
                            label = {
                                Text(
                                    if (uiState.draftMode == ResearchMode.CHAT) {
                                        "Chat Research Query (Type or Record Voice Note)"
                                    } else {
                                        "Project Architectural Brief (Type or Record Voice Note)"
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    if (uiState.draftMode == ResearchMode.CHAT) {
                                        "Ask a focused research question, or tap Voice Note to record and transcribe with Firebase AI…"
                                    } else {
                                        "Describe the system, scientific problem, or product idea — or tap Voice Note to record verbal notes directly into the synthesis pipeline…"
                                    }
                                )
                            },
                            trailingIcon = if (uiState.draftQuery.isNotBlank() && canEdit && !uiState.pipelineRunning && !uiState.transcribingVoiceNote) {
                                {
                                    IconButton(
                                        onClick = { onUpdateQuery("") },
                                        modifier = Modifier.testTag("clear_research_query_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.cd_clear_query),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else null,
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("research_query_input")
                        )

                        if (routingPrefs.externalEnabled &&
                            routingPrefs.externalRouterType == ExternalRouterType.SELF_HOSTED_TEXT
                        ) {
                            val composedPreviewPrompt = ExternalRouterApi.composeSelfHostedPrompt(
                                userPrompt = uiState.draftQuery,
                                approvedSummary = routingPrefs.selfHostedApprovedSummary
                            )
                            val charCount = composedPreviewPrompt.length
                            val overLimit = charCount > ExternalRouterApi.SELF_HOSTED_MAX_PROMPT_CHARS
                            var showComposerPayloadPreview by remember { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (overLimit) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Self-Hosted /query: $charCount / ${ExternalRouterApi.SELF_HOSTED_MAX_PROMPT_CHARS} chars" +
                                                if (routingPrefs.selfHostedIncludeGrounding) " · Grounding ON" else " · Grounding OFF",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (overLimit) {
                                                TextButton(
                                                    onClick = {
                                                        val budget = (ExternalRouterApi.SELF_HOSTED_MAX_PROMPT_CHARS -
                                                            routingPrefs.selfHostedApprovedSummary.length - 32).coerceAtLeast(100)
                                                        onUpdateQuery(uiState.draftQuery.take(budget))
                                                    },
                                                    modifier = Modifier.testTag("trim_self_hosted_prompt_btn")
                                                ) {
                                                    Text("Trim")
                                                }
                                            }
                                            TextButton(
                                                onClick = { showComposerPayloadPreview = true },
                                                modifier = Modifier.testTag("preview_self_hosted_payload_btn")
                                            ) {
                                                Text("Preview JSON")
                                            }
                                        }
                                    }
                                    if (overLimit) {
                                        Text(
                                            text = "Exceeds 4,000-character limit for self-hosted /query. Trim before sending.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            if (showComposerPayloadPreview) {
                                AlertDialog(
                                    onDismissRequest = { showComposerPayloadPreview = false },
                                    title = { Text("Exact /query Payload Preview") },
                                    text = {
                                        Text(
                                            text = ExternalRouterApi.buildSelfHostedPayloadJson(composedPreviewPrompt),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showComposerPayloadPreview = false }) {
                                            Text("Close")
                                        }
                                    }
                                )
                            }
                        }

                        VoiceNoteRecordingPanel(
                            state = voiceNoteRecorder.state,
                            autoRunPipeline = voiceNoteAutoRun,
                            saveToProjectRepo = voiceNoteSaveToRepo,
                            hasActiveProject = activeProject != null && canEdit,
                            onToggleAutoRun = { voiceNoteAutoRun = it },
                            onToggleSaveToRepo = { voiceNoteSaveToRepo = it },
                            onStopAndTranscribe = voiceNoteRecorder.stopAndTranscribe,
                            onCancel = voiceNoteRecorder.cancel
                        )

                        VoiceNoteTranscribingBanner(visible = uiState.transcribingVoiceNote)

                        VoiceNoteErrorBanner(
                            errorMessage = uiState.voiceNoteError,
                            onDismiss = onDismissVoiceNoteReview
                        )

                        VoiceNoteTranscriptReviewCard(
                            result = uiState.lastVoiceNoteResult,
                            usingRefinedBrief = uiState.lastVoiceNoteUsingBrief,
                            hasActiveProject = activeProject != null,
                            canEdit = canEdit,
                            onSelectVariant = onSelectVoiceNoteVariant,
                            onChangeCategory = onChangeVoiceNoteCategory,
                            onSaveToRepo = onSaveVoiceNoteToRepo,
                            onUndo = onUndoVoiceNote,
                            onDismiss = onDismissVoiceNoteReview
                        )

                        if (voiceHandle.state.activeTarget == DictationTarget.PROJECT_FOLDER) {
                            VoiceListeningPanel(
                                state = voiceHandle.state,
                                mode = uiState.draftMode,
                                onDone = voiceHandle.stopAndKeepPartial,
                                onCancel = voiceHandle.cancel
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VoiceNoteRecordButton(
                                isRecording = voiceNoteRecorder.state.isRecording,
                                isTranscribing = uiState.transcribingVoiceNote,
                                elapsedSec = voiceNoteRecorder.state.elapsedSec,
                                enabled = canEdit && !uiState.pipelineRunning,
                                onClick = {
                                    if (voiceHandle.state.isRecording) voiceHandle.cancel()
                                    voiceNoteRecorder.startOrStop()
                                }
                            )

                            Button(
                                onClick = {
                                    if (voiceHandle.state.isRecording) {
                                        voiceHandle.stopAndKeepPartial()
                                    }
                                    if (voiceNoteRecorder.state.isRecording) {
                                        voiceNoteRecorder.cancel()
                                    }
                                    onRunPipeline()
                                },
                                enabled = canEdit && !uiState.pipelineRunning && !uiState.transcribingVoiceNote && uiState.draftQuery.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("run_pipeline_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.pipelineRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Running Pipeline…",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.run_pipeline),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Routing & Gap Telemetry Banner
            if (activeProject != null && activeProject.detectedDomain.isNotBlank()) {
                item {
                    val gap = GapVerdict.fromWire(activeProject.gapVerdict)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TelemetryPill(
                                    text = "${activeProject.detectedDomain} · ${activeProject.complexityScore}/10",
                                    accentColor = MaterialTheme.colorScheme.primary
                                )
                                TelemetryPill(
                                    text = gap.label,
                                    accentColor = if (gap == GapVerdict.RESOLVED) VerifiedEmerald else BridgeAmber
                                )
                                if (activeProject.latestPrdVersion.isNotBlank()) {
                                    TelemetryPill(
                                        text = "PRD ${activeProject.latestPrdVersion}",
                                        accentColor = VerifiedEmerald
                                    )
                                }
                                val projectTags = remember(activeProject.id, activeProject.tags, activeProject.detectedDomain) {
                                    MetadataTagEngine.effectiveProjectTags(activeProject)
                                        .filterNot { it.equals(activeProject.detectedDomain, ignoreCase = true) }
                                        .take(4)
                                }
                                projectTags.forEach { tag ->
                                    TelemetryPill(
                                        text = "#$tag",
                                        accentColor = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            if (activeProject.routingPlanSummary.isNotBlank()) {
                                Text(
                                    text = activeProject.routingPlanSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Dynamic Mermaid.js Research Synthesis & Architecture Diagram View (renders live as pipeline runs)
            if (stages.isNotEmpty() || (activeProject != null && (activeProject.detectedDomain.isNotBlank() || files.isNotEmpty()))) {
                item(key = "research_synthesis_mermaid_view") {
                    ResearchSynthesisDiagramView(
                        stages = stages,
                        synthesisMarkdown = activeProject?.chatAnswer.orEmpty(),
                        project = activeProject,
                        draftTitle = activeProject?.title?.ifBlank { uiState.draftQuery.take(48) }
                            ?: uiState.draftQuery.take(48),
                        routingPrefs = routingPrefs,
                        roundtableItems = roundtableItems,
                        isPipelineRunning = uiState.pipelineRunning,
                        initiallyExpanded = true,
                        canSaveToRepo = canEdit && activeProject != null,
                        currentUserUid = currentUid,
                        currentUserName = activeProject?.memberDisplayNames?.get(currentUid)
                            ?.takeIf { it.isNotBlank() } ?: "Researcher",
                        onSaveDiagramToRepo = { relativePath, code ->
                            onCreateRepoFile(relativePath, code)
                        },
                        onSaveDiagramBoard = onSaveDiagramBoard,
                        onToast = onToast
                    )
                }

                // Dynamic Specialized Format Visualizer (LaTeX, Cypher/Apache AGE, PostGIS GeoJSON/WKT, D3/NetworkX/GraphML/DOT, PostgreSQL)
                item(key = "research_specialized_artifacts_view") {
                    ResearchSpecializedArtifactsCard(
                        stages = stages,
                        synthesisMarkdown = activeProject?.chatAnswer.orEmpty(),
                        project = activeProject,
                        projectFiles = files,
                        canSaveToRepo = canEdit && activeProject != null,
                        onSaveArtifactToRepo = { relativePath, updatedContent ->
                            onCreateRepoFile(relativePath, updatedContent)
                        },
                        onToast = onToast
                    )
                }

                // PostGIS / PostgreSQL Research Entity Schema Mapper
                item(key = "research_postgis_schema_mapper") {
                    PostGisSchemaMapperCard(
                        project = activeProject,
                        stages = stages,
                        roundtableItems = roundtableItems,
                        files = files,
                        canSaveToRepo = canEdit && activeProject != null,
                        isAiExtracting = uiState.extractingPostGisEntities,
                        onAiExtractEntities = onAiExtractPostGisEntities,
                        onSaveMigrationToRepo = { relativePath, sqlContent ->
                            onCreateRepoFile(relativePath, sqlContent)
                        },
                        onToast = onToast
                    )
                }
            }

            // Live 7-Stage Cards
            if (stages.isNotEmpty()) {
                item {
                    Text(
                        text = "7-Stage Empirical Pipeline",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(stages, key = { it.stageNumber }) { stage ->
                    StagePipelineCard(
                        stage = stage,
                        initiallyExpanded = stage.stageNumber == 4 || stage.status == "FAILED",
                        onCopied = onToast,
                        onRetryStage = if (canEdit && !uiState.pipelineRunning) onRetryStage else null
                    )
                }
            }

            // Chat Mode Formatted Output & Multi-Turn Follow-Up Thread
            if (activeProject != null &&
                ResearchMode.fromWire(activeProject.mode) == ResearchMode.CHAT &&
                (activeProject.chatAnswer.isNotBlank() || activeProject.parsedChatHistory().isNotEmpty())
            ) {
                item {
                    val chatTurns = remember(activeProject.chatHistoryJson, activeProject.chatAnswer, activeProject.query, activeProject.detectedDomain) {
                        MetadataTagEngine.effectiveChatTurns(activeProject)
                    }
                    val fullTranscript = remember(chatTurns) {
                        chatTurns.joinToString("\n\n---\n\n") { turn ->
                            val header = if (turn.role == "USER") "### You" else "### Vortex AI (${turn.modelUsed.ifBlank { "Gemini" }})"
                            "$header\n\n${turn.content}"
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("chat_mode_thread_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Research Conversation (${chatTurns.count { it.role == "ASSISTANT" }} turns)",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, activeProject.title, fullTranscript)
                                        onToast("Copied research thread.")
                                    },
                                    modifier = Modifier.testTag("copy_chat_answer_button")
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_answer))
                                }
                                Button(
                                    onClick = onOpenExportStudio,
                                    modifier = Modifier.testTag("export_pdf_from_chat_button")
                                ) {
                                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.export_pdf_button))
                                }
                                OutlinedButton(
                                    onClick = {
                                        onShareDoc("${activeProject.rootFolderName}.md", fullTranscript)
                                    }
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.share_output))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            chatTurns.forEachIndexed { idx, turn ->
                                val isUser = turn.role.equals("USER", ignoreCase = true)
                                val turnCategory = NoteCategory.fromWireOrLabel(turn.category)
                                val turnCatAccent = noteCategoryAccent(turnCategory)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_turn_${idx}_${turn.role.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                TelemetryPill(
                                                    text = if (isUser) "You" else turn.modelUsed.ifBlank { "Vortex AI" },
                                                    accentColor = if (isUser) MaterialTheme.colorScheme.primary else VerifiedEmerald
                                                )
                                                if (!isUser && turn.responseLane.isNotBlank()) {
                                                    TelemetryPill(
                                                        text = "lane: ${turn.responseLane}",
                                                        accentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                if (!isUser && turn.responseLatencySeconds != null) {
                                                    TelemetryPill(
                                                        text = String.format(java.util.Locale.US, "%.2fs", turn.responseLatencySeconds),
                                                        accentColor = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                                TelemetryPill(
                                                    text = turnCategory.label,
                                                    accentColor = turnCatAccent
                                                )
                                                turn.tags
                                                    .filterNot { it.equals(turnCategory.label, ignoreCase = true) }
                                                    .take(3)
                                                    .forEach { tg ->
                                                        TelemetryPill(
                                                            text = "#$tg",
                                                            accentColor = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                            }
                                            IconButton(
                                                onClick = {
                                                    copyToClipboard(context, "Turn ${idx + 1}", turn.content)
                                                    onToast("Copied message.")
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.ContentCopy,
                                                    contentDescription = stringResource(R.string.cd_copy_text),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (isUser) {
                                            SelectionContainer {
                                                Text(
                                                    text = turn.content,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        } else {
                                            MarkdownPreviewView(
                                                markdown = turn.content,
                                                onCopyCode = { snippet ->
                                                    copyToClipboard(context, "Code Snippet", snippet)
                                                    onToast("Copied code block.")
                                                }
                                            )
                                        }
                                        if (turn.sources.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                turn.sources.forEach { src ->
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                        modifier = Modifier.clickable {
                                                            runCatching { uriHandler.openUri(src.uri) }
                                                        }
                                                    ) {
                                                        Text(
                                                            text = src.title.ifBlank { src.uri }.take(34),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (canEdit) {
                                val isListeningFollowUp = voiceHandle.state.activeTarget == DictationTarget.FOLLOW_UP
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Text("Continue Thread (Grounded Follow-Up)", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(
                                    value = followUpDraft,
                                    onValueChange = { followUpDraft = it },
                                    enabled = !uiState.pipelineRunning && !uiState.chatFollowUpRunning,
                                    label = { Text("Ask a follow-up question (Type or Speak)...") },
                                    placeholder = { Text("e.g., Compare latency trade-offs or drill into edge cases...") },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { voiceHandle.startOrToggle(DictationTarget.FOLLOW_UP) },
                                            enabled = !uiState.pipelineRunning && !uiState.chatFollowUpRunning,
                                            modifier = Modifier.testTag("voice_dictate_followup_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isListeningFollowUp) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                                contentDescription = stringResource(R.string.cd_voice_dictate),
                                                tint = if (isListeningFollowUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_followup_input")
                                )
                                if (isListeningFollowUp) {
                                    VoiceListeningPanel(
                                        state = voiceHandle.state,
                                        mode = ResearchMode.CHAT,
                                        onDone = voiceHandle.stopAndKeepPartial,
                                        onCancel = voiceHandle.cancel
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (isListeningFollowUp) {
                                            voiceHandle.stopAndKeepPartial()
                                        }
                                        val q = followUpDraft.trim()
                                        if (q.isNotBlank()) {
                                            followUpDraft = ""
                                            onSendChatFollowUp(q)
                                        }
                                    },
                                    enabled = !uiState.pipelineRunning && !uiState.chatFollowUpRunning && followUpDraft.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("send_chat_followup_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (uiState.chatFollowUpRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Synthesizing Follow-Up…")
                                    } else {
                                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Send Follow-Up")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Project Mode Repo Tree + Offline-First File Organization + Cloud Mirroring Bar
            if (activeProject != null && (files.isNotEmpty() || ResearchMode.fromWire(activeProject.mode) == ResearchMode.PROJECT)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${activeProject.rootFolderName}/ (${files.size} files)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                    val safNeedsAttention = safSyncStatus.contains("Ahead", ignoreCase = true) ||
                                        safSyncStatus.contains("Diverged", ignoreCase = true)
                                    Text(
                                        text = "Cache: ${if (isProjectPendingSync) "Pending Sync" else "Room"} · GitHub: $githubSyncStatus · Drive: $driveSyncStatus · SAF: $safSyncStatus",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isProjectPendingSync || safNeedsAttention) BridgeAmber
                                        else if (githubSyncStatus == "In Sync" || driveSyncStatus == "In Sync" || safSyncStatus == "In Sync") VerifiedEmerald
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (canEdit) {
                                    OutlinedButton(
                                        onClick = { showCreateFileDialog = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("repo_new_file_button")
                                    ) {
                                        Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.repo_new_file))
                                    }
                                }
                            }

                            // Streamlined 4-Channel Sync & Export Strip (eliminates 11-button clutter)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenExportStudio,
                                    enabled = !uiState.exportingReport,
                                    modifier = Modifier.testTag("export_pdf_from_repo_button")
                                ) {
                                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (uiState.exportingReport) "Exporting…" else "Export")
                                }
                                OutlinedButton(
                                    onClick = onOpenDriveConsole,
                                    enabled = !uiState.syncingDrive,
                                    modifier = Modifier.testTag("open_drive_sync_console_button")
                                ) {
                                    Icon(Icons.Outlined.AddToDrive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (uiState.syncingDrive) "Drive Syncing…" else "Drive · $driveSyncStatus")
                                }
                                OutlinedButton(
                                    onClick = onOpenGitHubConsole,
                                    enabled = !uiState.syncingGitHub,
                                    modifier = Modifier.testTag("open_github_console_button")
                                ) {
                                    Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (uiState.syncingGitHub) "GitHub Syncing…" else "GitHub · $githubSyncStatus")
                                }
                                FilterChip(
                                    selected = showLocalSafTools,
                                    onClick = { showLocalSafTools = !showLocalSafTools },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("Local Folder · $safSyncStatus") },
                                    modifier = Modifier.testTag("toggle_local_saf_tools_chip")
                                )
                            }

                            AnimatedVisibility(visible = showLocalSafTools) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Local Storage Access Framework (SAF) Mirror",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { folderPicker.launch(null) },
                                                modifier = Modifier.testTag("saf_bind_folder_button")
                                            ) {
                                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(if (safSyncStatus == "Folder Unbound") "Bind Folder" else "Change Folder")
                                            }
                                            OutlinedButton(
                                                onClick = onPushSaf,
                                                enabled = !uiState.syncingSaf && safSyncStatus != "Folder Unbound",
                                                modifier = Modifier.testTag("saf_push_button")
                                            ) {
                                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(5.dp))
                                                Text("Push ↑")
                                            }
                                            OutlinedButton(
                                                onClick = onPullSaf,
                                                enabled = !uiState.syncingSaf && safSyncStatus != "Folder Unbound",
                                                modifier = Modifier.testTag("saf_pull_button")
                                            ) {
                                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(5.dp))
                                                Text("Pull ↓")
                                            }
                                            if (safSyncStatus != "Folder Unbound") {
                                                OutlinedButton(
                                                    onClick = onRefreshSafStatus,
                                                    enabled = !uiState.syncingSaf,
                                                    modifier = Modifier.testTag("saf_check_status_button")
                                                ) {
                                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Check")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            // Collapsible Nested Repo Folder Tree (with PRD/ & PRD_INDEX highlighted)
                            groupedFolders.forEach { (folderPath, folderFiles) ->
                                val isPrdFolder = folderPath.equals("PRD", ignoreCase = true)
                                val isCollapsed = folderPath in collapsedFolders
                                val sortedFiles = folderFiles.sortedWith(
                                    compareBy<ProjectFile> { !it.relativePath.contains("PRD_INDEX", ignoreCase = true) }
                                        .thenBy { it.relativePath }
                                )

                                if (folderPath.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isPrdFolder) VerifiedEmerald.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPrdFolder) VerifiedEmerald.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                collapsedFolders = if (isCollapsed) {
                                                    collapsedFolders - folderPath
                                                } else {
                                                    collapsedFolders + folderPath
                                                }
                                            }
                                            .testTag("repo_folder_${folderPath.lowercase().replace('/', '_')}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Folder,
                                                contentDescription = "Folder $folderPath",
                                                tint = if (isPrdFolder) VerifiedEmerald else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "$folderPath/ (${sortedFiles.size})",
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                                color = if (isPrdFolder) VerifiedEmerald else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isPrdFolder) {
                                                TextButton(
                                                    onClick = onOpenPrdStudio,
                                                    modifier = Modifier.testTag("open_prd_studio_from_tree")
                                                ) {
                                                    Text("PRD Studio", color = VerifiedEmerald)
                                                }
                                            }
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                                contentDescription = "Toggle folder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (!isCollapsed) {
                                    sortedFiles.forEach { file ->
                                        val isSelected = selectedFile?.relativePath == file.relativePath
                                        val isPrdIndex = file.relativePath.contains("PRD_INDEX", ignoreCase = true)
                                        val displayName = if (folderPath.isEmpty()) file.relativePath
                                        else file.relativePath.substringAfterLast('/')

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isPrdIndex -> VerifiedEmerald.copy(alpha = 0.5f)
                                                    else -> MaterialTheme.colorScheme.outline
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = if (folderPath.isEmpty()) 0.dp else 14.dp)
                                                .clickable {
                                                    selectedFilePath = if (isSelected) null else file.relativePath
                                                }
                                                .testTag("repo_file_${displayName.lowercase().replace('.', '_')}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isPrdIndex) Icons.Outlined.Toc else Icons.Outlined.Description,
                                                    contentDescription = stringResource(R.string.cd_open_file),
                                                    tint = if (file.category == "PRD" || isPrdFolder) VerifiedEmerald else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isPrdIndex) {
                                                    TelemetryPill(text = "Index", accentColor = VerifiedEmerald)
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                if (canEdit) {
                                                    IconButton(
                                                        onClick = { fileToMove = file },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("move_repo_file_${displayName.lowercase().replace('.', '_')}")
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.DriveFileMove,
                                                            contentDescription = stringResource(R.string.cd_move_rename_file),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { fileToEdit = file },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Edit,
                                                            contentDescription = stringResource(R.string.cd_edit_repo_file),
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (selectedFilePath == file.relativePath) {
                                                                selectedFilePath = null
                                                            }
                                                            onDeleteRepoFile(file.relativePath)
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.DeleteOutline,
                                                            contentDescription = stringResource(R.string.cd_delete_repo_file),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                val rawUrl = onRawGitHubUrl(file.relativePath)
                                                if (rawUrl != null) {
                                                    IconButton(
                                                        onClick = {
                                                            copyToClipboard(context, "Raw GitHub URL", rawUrl)
                                                            onToast("Copied Raw HTTPS URL for external AI models.")
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.Link,
                                                            contentDescription = "Copy raw GitHub HTTPS URL",
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onShareDoc(file.relativePath.substringAfterLast('/'), file.content)
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Share,
                                                        contentDescription = "Share file",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            selectedFile?.let { f ->
                                val fileExt = f.relativePath.substringAfterLast('.', "").lowercase()
                                val specializedKind = remember(fileExt, f.content) {
                                    if (fileExt == "mmd" || fileExt == "mermaid" || fileExt == "md" || fileExt == "markdown") null
                                    else SpecializedFormatParser.detectKind(fileExt, f.content)
                                }
                                when {
                                    fileExt == "mmd" || fileExt == "mermaid" -> {
                                        MermaidDiagramCard(
                                            code = f.content,
                                            title = f.relativePath.substringAfterLast('/'),
                                            subtitle = "${activeProject.rootFolderName}/${f.relativePath}",
                                            canvasHeight = 300.dp,
                                            onCopyCode = { src ->
                                                copyToClipboard(context, f.relativePath, src)
                                                onToast("Copied ${f.relativePath}")
                                            }
                                        )
                                    }
                                    specializedKind != null -> {
                                        SpecializedFormatCard(
                                            kind = specializedKind,
                                            code = f.content,
                                            title = f.relativePath.substringAfterLast('/'),
                                            subtitle = "${activeProject.rootFolderName}/${f.relativePath}",
                                            sourceLanguage = fileExt.ifBlank { specializedKind.defaultExtension },
                                            canvasHeight = 320.dp,
                                            onSaveToRepo = if (canEdit) {
                                                { updated -> onCreateRepoFile(f.relativePath, updated) }
                                            } else null,
                                            onToast = onToast
                                        )
                                    }
                                    fileExt == "md" || fileExt == "markdown" -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.background,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${activeProject.rootFolderName}/${f.relativePath}",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            copyToClipboard(context, f.relativePath, f.content)
                                                            onToast("Copied ${f.relativePath}")
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Outlined.ContentCopy,
                                                            contentDescription = stringResource(R.string.cd_copy_text),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                                MarkdownPreviewView(
                                                    markdown = f.content,
                                                    onCopyCode = { snippet ->
                                                        copyToClipboard(context, f.relativePath, snippet)
                                                        onToast("Copied snippet from ${f.relativePath}")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        MonospaceCodeBlock(
                                            title = "${activeProject.rootFolderName}/${f.relativePath}",
                                            body = f.content,
                                            onCopy = {
                                                copyToClipboard(context, f.relativePath, f.content)
                                                onToast("Copied ${f.relativePath}")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Approve & Generate PRD CTA
        if (activeProject != null && stages.any { it.status == "COMPLETED" } && canEdit) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onApprovePrd,
                        enabled = !uiState.prdGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("approve_generate_prd_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerifiedEmerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.prdGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Generating PRD & PRD_INDEX...")
                        } else {
                            Icon(Icons.Outlined.Approval, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (activeProject.latestPrdVersion.isBlank()) {
                                    stringResource(R.string.approve_generate_prd)
                                } else {
                                    "Approve & Generate Next PRD (${activeProject.latestPrdVersion} →)"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBlueprintGuide) {
        BlueprintGuideSheet(
            selected = uiState.draftBlueprint,
            recommendedBlueprint = uiState.recommendedBlueprint,
            onSelect = onUpdateBlueprint,
            onDismiss = { showBlueprintGuide = false }
        )
    }

    if (showCreateFileDialog) {
        CreateRepoFileDialog(
            availableFolders = availableFolders,
            onCreate = { path, content ->
                onCreateRepoFile(path, content)
                selectedFilePath = path
            },
            onDismiss = { showCreateFileDialog = false }
        )
    }

    fileToMove?.let { targetFile ->
        MoveRenameRepoFileDialog(
            file = targetFile,
            availableFolders = availableFolders,
            onMoveRename = { oldPath, newPath ->
                onMoveRenameRepoFile(oldPath, newPath)
                if (selectedFilePath == oldPath) selectedFilePath = newPath
            },
            onDismiss = { fileToMove = null }
        )
    }

    fileToEdit?.let { targetFile ->
        EditRepoFileDialog(
            file = targetFile,
            onSaveContent = onEditRepoFile,
            onDismiss = { fileToEdit = null }
        )
    }
}
