package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoundtableStatus
import com.example.data.model.ScaffoldingBlueprint
import com.example.ui.components.AddModelFolderDialog
import com.example.ui.components.GeminiComparisonSource
import com.example.ui.components.ModelSubfolderRow
import com.example.ui.components.PasteRoundtableArtifactDialog
import com.example.ui.components.RoundtableArtifactCard
import com.example.ui.components.RoundtableSideBySideCard
import com.example.ui.components.RoundtableSideBySideDialog
import com.example.ui.components.TelemetryPill
import com.example.ui.components.UnifiedArtifactViewerDialog
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.VortexUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundtableScreen(
    uiState: VortexUiState,
    activeProject: ResearchProject?,
    items: List<RoundtableItem>,
    currentUid: String,
    externalRouterConfigured: Boolean = false,
    onAddModelFolder: (String) -> Unit,
    onRemoveModelFolder: (String) -> Unit,
    onAddPastedIdea: (String, String, String) -> Unit,
    onIngestFiles: (String, List<Uri>) -> Unit,
    onAutoDispatchExternal: (modelFolder: String, targetModelId: String, customDirective: String) -> Unit = { _, _, _ -> },
    onOpenEnvVault: () -> Unit = {},
    onScrutinizeSingle: (RoundtableItem) -> Unit,
    onRunScrutinyQueue: (String?) -> Unit,
    onRunCrossModelComparison: () -> Unit,
    onAskRag: (String) -> Unit,
    onUpdateItemContent: (RoundtableItem, String, Boolean) -> Unit,
    onDeleteItem: (RoundtableItem) -> Unit,
    onMergeAdoptedIntoPrd: () -> Unit,
    onRawGitHubUrl: (String) -> String?,
    onOpenDashboard: () -> Unit = {},
    onOpenResearch: () -> Unit = {},
    onToast: (String) -> Unit
) {
    val context = LocalContext.current

    if (activeProject == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.roundtable_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Select a project from the Dashboard or start a research session to open its A.i. Roundtable model sub-directories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenDashboard,
                    modifier = Modifier.testTag("roundtable_empty_open_dashboard_button")
                ) {
                    Text("Open Dashboard")
                }
                OutlinedButton(
                    onClick = onOpenResearch,
                    modifier = Modifier.testTag("roundtable_empty_open_research_button")
                ) {
                    Text("Start Research")
                }
            }
        }
        return
    }

    val blueprint = remember(activeProject.blueprintId) {
        ScaffoldingBlueprint.fromWire(activeProject.blueprintId)
    }
    val rtDir = blueprint.roundtableFolder

    val allFolders = remember(activeProject.id, activeProject.roundtableFolders, items) {
        (activeProject.effectiveRoundtableFolders() + items.map { it.modelFolder })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }
    var selectedFolder by remember(activeProject.id, allFolders) {
        mutableStateOf(
            allFolders.firstOrNull { it.equals("Claude", ignoreCase = true) }
                ?: allFolders.firstOrNull()
                ?: "Claude"
        )
    }
    if (allFolders.none { it.equals(selectedFolder, ignoreCase = true) } && allFolders.isNotEmpty()) {
        selectedFolder = allFolders.first()
    }

    var uploadTargetFolder by remember(activeProject.id) { mutableStateOf(selectedFolder) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddArtifactDialog by remember { mutableStateOf(false) }
    var showAutoDispatchDialog by remember { mutableStateOf(false) }
    var viewerItemId by remember(activeProject.id) { mutableStateOf<String?>(null) }
    var viewerStartInEdit by remember { mutableStateOf(false) }
    var compareSelectedItemId by remember(activeProject.id) { mutableStateOf<String?>(null) }
    var fullCompareItemId by remember(activeProject.id) { mutableStateOf<String?>(null) }
    var fullCompareGeminiSource by remember { mutableStateOf(GeminiComparisonSource.AUDIT_VERDICT) }
    var ragQuestion by remember { mutableStateOf("") }

    val universalMultiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onIngestFiles(uploadTargetFolder, uris)
        }
    }

    val canEdit = activeProject.canEdit(currentUid)
    val folderItems = items.filter { it.modelFolder.equals(selectedFolder, ignoreCase = true) }
    val folderQueuedCount = folderItems.count { it.queueStatus == RoundtableStatus.QUEUED.wire }
    val totalQueuedCount = items.count { it.queueStatus == RoundtableStatus.QUEUED.wire }
    val adoptedCount = items.count {
        it.queueStatus in listOf(RoundtableStatus.ADOPTED.wire, RoundtableStatus.CONSENSUS_RESOLVED.wire)
    }
    val activeModelCount = items.map { it.modelFolder.lowercase() }.distinct().size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with blueprint-aware A.i. Roundtable root path
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.roundtable_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Text(
                    text = "${activeProject.rootFolderName}/$rtDir/",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Create dedicated sub-directories for external AI models and upload documents, PDFs, LaTeX, Mermaid, or native code (.py, .ts, .rs, .go, .kt, .yaml, .jsonl, .zip) for cross-model empirical scrutiny.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // External Model Access Strip (Audit Packet + Raw GitHub URL)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Send Project Context to External LLMs", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val packet = buildString {
                                    appendLine("# Vortex Audit Packet: ${activeProject.title}")
                                    appendLine("Query: ${activeProject.query}")
                                    appendLine()
                                    appendLine(
                                        activeProject.chatAnswer.ifBlank {
                                            activeProject.parsedFiles().firstOrNull()?.content.orEmpty()
                                        }
                                    )
                                    appendLine()
                                    appendLine("Please critique this architecture rigorously and propose concrete improvements or code.")
                                }
                                copyToClipboard(context, "Audit Packet", packet)
                                onToast("Copied self-contained Audit Packet for any external LLM.")
                            },
                            modifier = Modifier.testTag("copy_audit_packet_button")
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Audit Packet")
                        }

                        if (canEdit) {
                            Button(
                                onClick = { showAutoDispatchDialog = true },
                                enabled = !uiState.autoDispatchRunning,
                                modifier = Modifier.testTag("auto_dispatch_external_button")
                            ) {
                                if (uiState.autoDispatchRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Dispatching…")
                                } else {
                                    Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Auto-Dispatch → $selectedFolder")
                                }
                            }
                        }

                        val readmePath = activeProject.parsedFiles().firstOrNull()?.relativePath ?: "README.md"
                        val rawUrl = onRawGitHubUrl(readmePath)
                        if (rawUrl != null) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "Raw GitHub URL", rawUrl)
                                    onToast("Copied Raw HTTPS URL for external browsing LLMs.")
                                }
                            ) {
                                Text("Copy Raw GitHub URL")
                            }
                        }
                    }
                }
            }
        }

        // Dedicated A.i. Roundtable Sub-Directory Explorer Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("roundtable_subfolders_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.roundtable_subfolders_header),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${allFolders.size} model folder(s) · Tap a folder to browse or '+' to upload directly",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEdit) {
                            OutlinedButton(
                                onClick = { showAddFolderDialog = true },
                                modifier = Modifier.testTag("add_model_folder_button")
                            ) {
                                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ New Model")
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allFolders.forEach { folder ->
                            val matching = items.filter { it.modelFolder.equals(folder, ignoreCase = true) }
                            val fQueued = matching.count { it.queueStatus == RoundtableStatus.QUEUED.wire }
                            val fAdopted = matching.count {
                                it.queueStatus in listOf(
                                    RoundtableStatus.ADOPTED.wire,
                                    RoundtableStatus.CONSENSUS_RESOLVED.wire
                                )
                            }
                            ModelSubfolderRow(
                                roundtableRoot = rtDir,
                                folderName = folder,
                                selected = selectedFolder.equals(folder, ignoreCase = true),
                                fileCount = matching.size,
                                queuedCount = fQueued,
                                adoptedCount = fAdopted,
                                canEdit = canEdit,
                                canRemove = allFolders.size > 1,
                                onSelect = { selectedFolder = folder },
                                onQuickUpload = {
                                    selectedFolder = folder
                                    uploadTargetFolder = folder
                                    universalMultiPicker.launch(arrayOf("*/*"))
                                },
                                onRemove = { onRemoveModelFolder(folder) }
                            )
                        }
                    }
                }
            }
        }

        // Side-by-Side Comparison View (Gemini Response vs External Model Outputs with Per-Section Copy)
        if (items.isNotEmpty()) {
            item {
                RoundtableSideBySideCard(
                    project = activeProject,
                    items = items,
                    crossModelSynthesis = uiState.crossModelSynthesis,
                    selectedItemId = compareSelectedItemId ?: folderItems.firstOrNull()?.id ?: items.firstOrNull()?.id,
                    onSelectItem = { compareSelectedItemId = it },
                    onOpenFullScreen = { chosenItem, chosenSource ->
                        fullCompareItemId = chosenItem.id
                        fullCompareGeminiSource = chosenSource
                    },
                    onToast = onToast
                )
            }
        }

        // Cross-Model Empirical Comparison Card (activates when 2+ model sub-folders hold files)
        if (activeModelCount >= 2 || uiState.crossModelSynthesis.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("cross_model_scrutiny_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CompareArrows,
                                contentDescription = null,
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cross-Model Empirical Scrutiny", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Compare $activeModelCount AI model folders side-by-side with Google Search verification.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onRunCrossModelComparison,
                            enabled = !uiState.crossModelLoading && activeModelCount >= 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_cross_model_scrutiny_button")
                        ) {
                            if (uiState.crossModelLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Comparing $activeModelCount Models...")
                            } else {
                                Icon(Icons.Outlined.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.roundtable_cross_model_cta))
                            }
                        }

                        if (uiState.crossModelSynthesis.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.crossModelUsed.isNotBlank()) {
                                    TelemetryPill(text = "Synthesized by ${uiState.crossModelUsed}", accentColor = VerifiedEmerald)
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, "Cross-Model Synthesis", uiState.crossModelSynthesis)
                                        onToast("Copied Cross-Model Scrutiny synthesis.")
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_copy_text),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            SelectionContainer {
                                Text(uiState.crossModelSynthesis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Active Model Sub-Folder Action Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Active Sub-Folder: $rtDir/$selectedFolder/",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canEdit) {
                        Button(
                            onClick = {
                                uploadTargetFolder = selectedFolder
                                universalMultiPicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.testTag("upload_universal_files_button")
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+ Upload to $selectedFolder")
                        }
                        OutlinedButton(
                            onClick = { showAddArtifactDialog = true },
                            modifier = Modifier.testTag("add_roundtable_artifact_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste Note/Code")
                        }
                        if (folderQueuedCount > 0) {
                            Button(
                                onClick = { onRunScrutinyQueue(selectedFolder) },
                                enabled = !uiState.scrutinyRunning,
                                modifier = Modifier.testTag("run_folder_scrutiny_button")
                            ) {
                                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Audit $selectedFolder ($folderQueuedCount)")
                            }
                        }
                        Button(
                            onClick = { onRunScrutinyQueue(null) },
                            enabled = !uiState.scrutinyRunning && totalQueuedCount > 0,
                            modifier = Modifier.testTag("run_scrutiny_queue_button")
                        ) {
                            if (uiState.scrutinyRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Scrutinizing...")
                            } else {
                                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scrutinize All ($totalQueuedCount)")
                            }
                        }
                        if (adoptedCount > 0) {
                            OutlinedButton(
                                onClick = onMergeAdoptedIntoPrd,
                                enabled = !uiState.prdGenerating,
                                modifier = Modifier.testTag("merge_adopted_ideas_button")
                            ) {
                                Icon(Icons.Outlined.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Merge $adoptedCount Adopted → PRD")
                            }
                        }
                    }
                }
            }
        }

        // Artifacts inside Selected Model Sub-Folder
        if (folderItems.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "$rtDir/$selectedFolder/ is ready for uploads",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        Text(
                            text = "Tap '+ Upload to $selectedFolder' to attach research docs, PDFs, LaTeX, Mermaid, or source code files, or paste a proposal directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(folderItems, key = { it.id }) { item ->
                RoundtableArtifactCard(
                    item = item,
                    roundtableRoot = rtDir,
                    canEdit = canEdit,
                    scrutinyRunning = uiState.scrutinyRunning,
                    onOpenViewer = { startEdit ->
                        viewerStartInEdit = startEdit
                        viewerItemId = item.id
                    },
                    onCompareSideBySide = {
                        compareSelectedItemId = item.id
                        fullCompareItemId = item.id
                        fullCompareGeminiSource = if (item.scrutinyVerdict.isNotBlank()) {
                            GeminiComparisonSource.AUDIT_VERDICT
                        } else {
                            GeminiComparisonSource.PROJECT_SYNTHESIS
                        }
                    },
                    onScrutinize = { onScrutinizeSingle(item) },
                    onCopyRelay = { relay ->
                        copyToClipboard(context, "Relay to ${item.modelFolder}", relay)
                        onToast("Copied Outbound Relay prompt for ${item.modelFolder}!")
                    },
                    onCopySnippet = { snippet ->
                        copyToClipboard(context, "Code Snippet", snippet)
                        onToast("Copied code block.")
                    },
                    onDelete = { onDeleteItem(item) }
                )
            }
        }

        // Attributed RAG Q&A Bar across all AI model sub-folders
        if (items.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Attributed Roundtable RAG Q&A", style = MaterialTheme.typography.titleSmall)
                        }
                        OutlinedTextField(
                            value = ragQuestion,
                            onValueChange = { ragQuestion = it },
                            label = { Text("Ask Gemini across all model sub-folders...") },
                            placeholder = { Text("e.g., Compare Claude's Rust code vs ChatGPT-o3's architecture") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("roundtable_rag_input")
                        )
                        Button(
                            onClick = { onAskRag(ragQuestion) },
                            enabled = !uiState.ragLoading && ragQuestion.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ask_roundtable_rag_button")
                        ) {
                            if (uiState.ragLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Querying Attributed Corpus...")
                            } else {
                                Text("Ask with [Model / File] Attribution")
                            }
                        }

                        if (uiState.ragAnswer.isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            if (uiState.ragModelUsed.isNotBlank()) {
                                TelemetryPill(text = "Answered by ${uiState.ragModelUsed}")
                            }
                            SelectionContainer {
                                Text(uiState.ragAnswer, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFolderDialog) {
        AddModelFolderDialog(
            roundtableRoot = rtDir,
            existingFolders = allFolders,
            onDismiss = { showAddFolderDialog = false },
            onCreate = { created ->
                onAddModelFolder(created)
                selectedFolder = created
            }
        )
    }

    if (showAddArtifactDialog) {
        PasteRoundtableArtifactDialog(
            roundtableRoot = rtDir,
            modelFolder = selectedFolder,
            onDismiss = { showAddArtifactDialog = false },
            onSubmit = { fileName, body ->
                onAddPastedIdea(selectedFolder, fileName, body)
            }
        )
    }

    val activeViewerItem = items.firstOrNull { it.id == viewerItemId }
    if (activeViewerItem != null) {
        UnifiedArtifactViewerDialog(
            item = activeViewerItem,
            roundtableRoot = rtDir,
            canEdit = canEdit,
            initialEditMode = viewerStartInEdit,
            onDismiss = { viewerItemId = null },
            onSaveContent = { updatedText, requeue ->
                onUpdateItemContent(activeViewerItem, updatedText, requeue)
            },
            onToast = onToast
        )
    }

    val compareDialogId = fullCompareItemId
    if (compareDialogId != null && items.isNotEmpty()) {
        RoundtableSideBySideDialog(
            project = activeProject,
            items = items,
            crossModelSynthesis = uiState.crossModelSynthesis,
            initialItemId = compareDialogId,
            initialGeminiSource = fullCompareGeminiSource,
            onDismiss = { fullCompareItemId = null },
            onToast = onToast
        )
    }

    if (showAutoDispatchDialog) {
        AutoDispatchExternalDialog(
            roundtableRoot = rtDir,
            modelFolder = selectedFolder,
            externalRouterConfigured = externalRouterConfigured,
            onDismiss = { showAutoDispatchDialog = false },
            onOpenEnvVault = {
                showAutoDispatchDialog = false
                onOpenEnvVault()
            },
            onDispatch = { modelId, directive ->
                showAutoDispatchDialog = false
                onAutoDispatchExternal(selectedFolder, modelId, directive)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoDispatchExternalDialog(
    roundtableRoot: String,
    modelFolder: String,
    externalRouterConfigured: Boolean,
    onDismiss: () -> Unit,
    onOpenEnvVault: () -> Unit,
    onDispatch: (targetModelId: String, customDirective: String) -> Unit
) {
    val defaultPreset = remember(modelFolder) {
        val lower = modelFolder.lowercase()
        when {
            "claude" in lower -> "anthropic/claude-3.7-sonnet"
            "chatgpt" in lower || "openai" in lower || "o3" in lower -> "openai/o3-mini"
            "deepseek" in lower -> "deepseek/deepseek-r1"
            "grok" in lower -> "x-ai/grok-3"
            "perplexity" in lower -> "perplexity/sonar-pro"
            else -> "anthropic/claude-3.7-sonnet"
        }
    }
    val presets = listOf(
        "anthropic/claude-3.7-sonnet",
        "openai/o3-mini",
        "deepseek/deepseek-r1",
        "x-ai/grok-3",
        "perplexity/sonar-pro"
    )
    var modelId by remember(modelFolder) { mutableStateOf(defaultPreset) }
    var customDirective by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Auto-Dispatch to $modelFolder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Dispatches this project's Audit Packet via your configured OpenRouter/OpenAI-compatible endpoint and saves the reply directly into $roundtableRoot/$modelFolder/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!externalRouterConfigured) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BridgeAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, BridgeAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "External API Key Required",
                                style = MaterialTheme.typography.labelLarge,
                                color = BridgeAmber
                            )
                            Text(
                                text = "Add an OPENROUTER_API_KEY or OPENAI_API_KEY in your encrypted .env Vault (with Auto-Fill ON) or in Strategic Router settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = onOpenEnvVault,
                                modifier = Modifier.testTag("auto_dispatch_open_env_vault")
                            ) {
                                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open .env Vault")
                            }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = modelId.equals(preset, ignoreCase = true),
                            onClick = { modelId = preset },
                            label = { Text(preset.substringAfter('/')) }
                        )
                    }
                }

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("External Model ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_dispatch_model_id_input")
                )

                OutlinedTextField(
                    value = customDirective,
                    onValueChange = { customDirective = it },
                    label = { Text("Optional Critique Focus") },
                    placeholder = { Text("e.g., Audit memory safety, security threats, or propose Rust code...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_dispatch_directive_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDispatch(modelId.trim(), customDirective.trim()) },
                enabled = modelId.isNotBlank() && externalRouterConfigured,
                modifier = Modifier.testTag("confirm_auto_dispatch_button")
            ) {
                Text("Dispatch Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
