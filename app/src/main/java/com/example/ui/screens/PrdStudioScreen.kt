package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Toc
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ExportFileFormat
import com.example.data.model.PrdDocument
import com.example.data.model.ResearchProject
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.remote.VortexFirestoreRepository
import com.example.ui.components.MarkdownPreviewView
import com.example.ui.components.MermaidDiagramEngine
import com.example.ui.components.MermaidStudioDialog
import com.example.ui.components.MonospaceCodeBlock
import com.example.ui.components.TelemetryPill
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.VortexUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrdStudioScreen(
    uiState: VortexUiState,
    activeProject: ResearchProject?,
    prds: List<PrdDocument>,
    currentUid: String,
    onGenerateInitialPrd: () -> Unit,
    onRevisePrd: (String, Boolean) -> Unit,
    onRefreshPrdIndex: () -> Unit,
    onExportSingleFile: (Uri, String) -> Unit,
    onShareDoc: (String, String) -> Unit,
    onOpenDashboard: () -> Unit = {},
    onOpenResearch: () -> Unit = {},
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedPrdId by remember(prds) { mutableStateOf(prds.firstOrNull()?.id) }
    var indexExpanded by remember { mutableStateOf(true) }
    var showRawIndex by remember { mutableStateOf(false) }
    var newestFirst by remember { mutableStateOf(false) }
    var showReviseDialog by remember { mutableStateOf(false) }
    var revisionPrompt by remember { mutableStateOf("") }
    var majorBump by remember { mutableStateOf(false) }
    var pendingSaveContent by remember { mutableStateOf("") }
    var showPrdDiagramStudio by remember { mutableStateOf(false) }

    val selectedPrd = prds.firstOrNull { it.id == selectedPrdId } ?: prds.firstOrNull()
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null && pendingSaveContent.isNotBlank()) {
            onExportSingleFile(uri, pendingSaveContent)
        }
    }

    if (activeProject == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("PRD Studio", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Select a project from the Dashboard or run a research pipeline and tap 'Approve & Generate PRD' to populate PRD_INDEX.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenDashboard,
                    modifier = Modifier.testTag("prd_empty_open_dashboard_button")
                ) {
                    Text("Open Dashboard")
                }
                OutlinedButton(
                    onClick = onOpenResearch,
                    modifier = Modifier.testTag("prd_empty_open_research_button")
                ) {
                    Text("Start Research")
                }
            }
        }
        return
    }

    val canEdit = activeProject.canEdit(currentUid)
    val ext = ExportFileFormat.fromWire(activeProject.fileFormat).extension
    val prdDir = ScaffoldingBlueprint.fromWire(activeProject.blueprintId).prdFolder
    val sortedAscending = remember(prds) {
        prds.sortedWith(compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion })
    }
    val indexEntries = remember(sortedAscending, newestFirst) {
        if (newestFirst) sortedAscending.reversed() else sortedAscending
    }
    val prdIndexText = remember(sortedAscending, activeProject.title, activeProject.rootFolderName, ext, prdDir) {
        VortexFirestoreRepository.buildPrdIndexContent(
            projectTitle = activeProject.title,
            extension = ext,
            prds = sortedAscending,
            rootFolderName = activeProject.rootFolderName,
            prdFolder = prdDir
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Nested PRD Folder Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "Nested PRD folder",
                    tint = VerifiedEmerald,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${activeProject.rootFolderName}/$prdDir/",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = VerifiedEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "PRD_INDEX.$ext + ${prds.size} versioned document(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit && prds.isNotEmpty()) {
                    Button(
                        onClick = { showReviseDialog = true },
                        enabled = !uiState.prdGenerating,
                        modifier = Modifier.testTag("revise_prd_button")
                    ) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.revise_prd))
                    }
                }
            }
        }

        if (prds.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("$prdDir/ Folder Initialized", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Approve your 7-stage synthesis to publish PRD_v1.0.$ext and populate $prdDir/PRD_INDEX.$ext with auto-generated delta summaries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (canEdit) {
                            Button(
                                onClick = onGenerateInitialPrd,
                                enabled = !uiState.prdGenerating,
                                modifier = Modifier.testTag("generate_v1_prd_button")
                            ) {
                                if (uiState.prdGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Generating v1.0...")
                                } else {
                                    Icon(Icons.Outlined.Approval, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Approve & Generate PRD v1.0")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Dedicated Interactive PRD Index Card inside the nested PRD/ folder
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("prd_index_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { indexExpanded = !indexExpanded }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Toc,
                                contentDescription = "PRD Index",
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$prdDir/PRD_INDEX.$ext",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = VerifiedEmerald
                                )
                                Text(
                                    text = "Version Evolution Index · Tap any row to open document",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEdit) {
                                IconButton(
                                    onClick = onRefreshPrdIndex,
                                    modifier = Modifier.testTag("sync_prd_index_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Sync,
                                        contentDescription = "Synchronize PRD Index file",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (indexExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = "Toggle PRD Index"
                            )
                        }

                        AnimatedVisibility(visible = indexExpanded) {
                            Column(
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                                // View Mode & Order Controls
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = !showRawIndex,
                                        onClick = { showRawIndex = false },
                                        label = { Text("Evolution List") },
                                        modifier = Modifier.testTag("prd_index_mode_list")
                                    )
                                    FilterChip(
                                        selected = showRawIndex,
                                        onClick = { showRawIndex = true },
                                        label = { Text("Raw PRD_INDEX.$ext") },
                                        modifier = Modifier.testTag("prd_index_mode_raw")
                                    )
                                    if (!showRawIndex && prds.size > 1) {
                                        FilterChip(
                                            selected = newestFirst,
                                            onClick = { newestFirst = !newestFirst },
                                            label = { Text(if (newestFirst) "Newest First" else "Oldest → Newest") }
                                        )
                                    }
                                }

                                if (showRawIndex) {
                                    MonospaceCodeBlock(
                                        title = "${activeProject.rootFolderName}/$prdDir/PRD_INDEX.$ext",
                                        body = prdIndexText,
                                        onCopy = {
                                            copyToClipboard(context, "PRD_INDEX", prdIndexText)
                                            onToast("Copied $prdDir/PRD_INDEX.$ext")
                                        }
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        indexEntries.forEach { prd ->
                                            val isSelected = selectedPrd?.id == prd.id
                                            val bumpLabel = VortexFirestoreRepository.versionBumpLabel(prd)
                                            val bumpColor = when (bumpLabel) {
                                                "Baseline" -> VerifiedEmerald
                                                "Major" -> BridgeAmber
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                            val dateStr = prd.createdAt?.toDate()?.let { dateFmt.format(it) }
                                                ?: dateFmt.format(Date())

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) VerifiedEmerald else MaterialTheme.colorScheme.outline
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedPrdId = prd.id }
                                                    .testTag("prd_index_row_${prd.version}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(4.dp)
                                                            .fillMaxHeight()
                                                            .background(bumpColor)
                                                    )
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            TelemetryPill(text = prd.version, accentColor = bumpColor)
                                                            TelemetryPill(text = bumpLabel, accentColor = bumpColor)
                                                            Spacer(Modifier.weight(1f))
                                                            Text(
                                                                text = dateStr,
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontFamily = JetBrainsMonoFamily
                                                                ),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        Text(
                                                            text = "├── $prdDir/PRD_${prd.version}.$ext",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontFamily = JetBrainsMonoFamily
                                                            ),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = prd.deltaSummary,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (prd.revisionRequest.isNotBlank()) {
                                                            Text(
                                                                text = "Trigger: ${prd.revisionRequest.take(120)}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Direct PRD_INDEX File Actions
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            copyToClipboard(context, "PRD_INDEX", prdIndexText)
                                            onToast("Copied $prdDir/PRD_INDEX.$ext")
                                        },
                                        modifier = Modifier.testTag("copy_prd_index_button")
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy Index")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            pendingSaveContent = prdIndexText
                                            saveLauncher.launch("PRD_INDEX.$ext")
                                        },
                                        modifier = Modifier.testTag("save_prd_index_button")
                                    ) {
                                        Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Index")
                                    }
                                    OutlinedButton(
                                        onClick = { onShareDoc("PRD_INDEX.$ext", prdIndexText) }
                                    ) {
                                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share Index")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Version Switcher Strip
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(prds, key = { it.id }) { prd ->
                        FilterChip(
                            selected = selectedPrd?.id == prd.id,
                            onClick = { selectedPrdId = prd.id },
                            label = { Text(prd.version) },
                            modifier = Modifier.testTag("prd_version_chip_${prd.version}")
                        )
                    }
                }
            }

            // Selected PRD Full Reader
            selectedPrd?.let { prd ->
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
                            Text(prd.title, style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TelemetryPill(prd.version, VerifiedEmerald)
                                TelemetryPill(VortexFirestoreRepository.versionBumpLabel(prd))
                                TelemetryPill(prd.modelUsed, MaterialTheme.colorScheme.secondary)
                            }
                            if (prd.deltaSummary.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = "Delta Summary: ${prd.deltaSummary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showPrdDiagramStudio = true },
                                    modifier = Modifier.testTag("prd_open_mermaid_studio_button")
                                ) {
                                    Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Diagrams")
                                }
                                OutlinedButton(
                                    onClick = {
                                        copyToClipboard(context, prd.title, prd.markdownContent)
                                        onToast("Copied PRD ${prd.version}")
                                    }
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingSaveContent = prd.markdownContent
                                        saveLauncher.launch("PRD_${prd.version}.$ext")
                                    }
                                ) {
                                    Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = { onShareDoc("PRD_${prd.version}.$ext", prd.markdownContent) }
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            MarkdownPreviewView(
                                markdown = prd.markdownContent,
                                onCopyCode = { snippet ->
                                    copyToClipboard(context, "PRD ${prd.version} snippet", snippet)
                                    onToast("Copied code block from PRD ${prd.version}.")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPrdDiagramStudio && selectedPrd != null) {
        val prdDiagrams = remember(activeProject.id, selectedPrd.id, selectedPrd.markdownContent) {
            MermaidDiagramEngine.collectPrdDiagrams(activeProject, selectedPrd)
        }
        MermaidStudioDialog(
            diagrams = prdDiagrams,
            initialDiagramId = prdDiagrams.firstOrNull()?.id.orEmpty(),
            onDismiss = { showPrdDiagramStudio = false },
            onSaveToRepo = null,
            onToast = onToast
        )
    }

    if (showReviseDialog) {
        AlertDialog(
            onDismissRequest = { showReviseDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Create Follow-Up PRD Revision") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !majorBump,
                            onClick = { majorBump = false },
                            label = { Text("Minor (+0.1)") }
                        )
                        FilterChip(
                            selected = majorBump,
                            onClick = { majorBump = true },
                            label = { Text("Major (+1.0)") }
                        )
                    }
                    OutlinedTextField(
                        value = revisionPrompt,
                        onValueChange = { revisionPrompt = it },
                        label = { Text("Follow-up constraints, edits or scope changes") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prd_revision_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (revisionPrompt.isNotBlank()) {
                            onRevisePrd(revisionPrompt, majorBump)
                            revisionPrompt = ""
                            showReviseDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_revise_prd_button")
                ) { Text("Generate Revision") }
            },
            dismissButton = {
                TextButton(onClick = { showReviseDialog = false }) { Text("Cancel") }
            }
        )
    }
}
