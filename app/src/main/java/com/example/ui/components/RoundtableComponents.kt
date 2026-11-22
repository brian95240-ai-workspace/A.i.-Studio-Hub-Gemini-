package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.model.RoundtableItem
import com.example.data.model.RoundtableStatus
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

private val PRESET_MODEL_FOLDERS = listOf(
    "Claude",
    "ChatGPT-o3",
    "DeepSeek-R1",
    "Grok",
    "Perplexity",
    "Llama-4",
    "Mistral",
    "Qwen"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSubfolderRow(
    roundtableRoot: String,
    folderName: String,
    selected: Boolean,
    fileCount: Int,
    queuedCount: Int,
    adoptedCount: Int,
    canEdit: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onQuickUpload: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("model_folder_${folderName.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else VerifiedEmerald,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "$roundtableRoot/$folderName/",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TelemetryPill(text = "$fileCount file(s)")
                    if (queuedCount > 0) {
                        TelemetryPill(text = "$queuedCount queued", accentColor = BridgeAmber)
                    }
                    if (adoptedCount > 0) {
                        TelemetryPill(text = "$adoptedCount adopted", accentColor = VerifiedEmerald)
                    }
                }
            }
            if (canEdit) {
                IconButton(
                    onClick = onQuickUpload,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quick_upload_folder_${folderName.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.cd_upload_to_folder),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (canRemove && fileCount == 0) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("remove_folder_${folderName.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_remove_model_folder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundtableArtifactCard(
    item: RoundtableItem,
    roundtableRoot: String,
    canEdit: Boolean,
    scrutinyRunning: Boolean,
    onOpenViewer: (startInEditMode: Boolean) -> Unit,
    onCompareSideBySide: () -> Unit,
    onScrutinize: () -> Unit,
    onCopyRelay: (String) -> Unit,
    onCopySnippet: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val status = RoundtableStatus.fromWire(item.queueStatus)
    val statusColor = when (status) {
        RoundtableStatus.ADOPTED, RoundtableStatus.CONSENSUS_RESOLVED -> VerifiedEmerald
        RoundtableStatus.REBUTTAL_NEEDED -> BridgeAmber
        RoundtableStatus.REJECTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val sources = remember(item.groundingSourcesJson) { item.parsedSources() }
    val sizeLabel = remember(item.byteSize) {
        if (item.byteSize >= 1024) "${item.byteSize / 1024} KB" else "${item.byteSize} B"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.testTag("roundtable_artifact_${item.fileName.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$roundtableRoot/${item.modelFolder}/${item.fileName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TelemetryPill(text = item.detectedLanguage, accentColor = MaterialTheme.colorScheme.secondary)
                        TelemetryPill(text = status.label, accentColor = statusColor)
                        TelemetryPill(text = sizeLabel)
                        if (item.scrutinizedByModel.isNotBlank()) {
                            TelemetryPill(text = item.scrutinizedByModel)
                        }
                    }
                }
                if (canEdit) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete artifact",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Unified Multi-Format Preview (Syntax-Highlighted Code / Native Markdown Previewer)
            UnifiedArtifactInlinePreview(
                item = item,
                canEdit = canEdit,
                onOpenFullViewer = onOpenViewer,
                onCopyCode = onCopySnippet
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onCompareSideBySide,
                    modifier = Modifier.testTag("compare_artifact_${item.fileName.lowercase()}")
                ) {
                    Icon(Icons.Outlined.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compare vs Gemini")
                }
                if (canEdit && status in listOf(RoundtableStatus.QUEUED, RoundtableStatus.REBUTTAL_NEEDED)) {
                    OutlinedButton(
                        onClick = onScrutinize,
                        enabled = !scrutinyRunning,
                        modifier = Modifier.testTag("scrutinize_single_${item.fileName.lowercase()}")
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (status == RoundtableStatus.QUEUED) "Scrutinize This File" else "Re-Audit File")
                    }
                }
            }

            AnimatedVisibility(visible = item.scrutinyVerdict.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Gemini Empirical Audit", style = MaterialTheme.typography.labelLarge, color = statusColor)
                    SelectionContainer {
                        Text(item.scrutinyVerdict, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (sources.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sources.take(5).forEach { src ->
                                AssistChip(
                                    onClick = { openExternalUrl(context, src.uri) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = src.title.take(30),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Distinct Outbound Relay Card with native Copy icon in the UPPER-RIGHT corner
            if (item.outboundRelayPrompt.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Outbound Relay → Send back to ${item.modelFolder}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onCopyRelay(item.outboundRelayPrompt) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("copy_outbound_relay_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_outbound),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = item.outboundRelayPrompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddModelFolderDialog(
    roundtableRoot: String,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    val availablePresets = remember(existingFolders) {
        PRESET_MODEL_FOLDERS.filter { preset ->
            existingFolders.none { it.equals(preset, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Create AI Model Sub-Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Creates a dedicated sub-directory under $roundtableRoot/ synced across Firestore, Google Drive, GitHub, and local storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Model Sub-Folder Name") },
                    placeholder = { Text("e.g., Grok-3, Perplexity, Llama-4") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_model_folder_input")
                )
                if (availablePresets.isNotEmpty()) {
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availablePresets.forEach { preset ->
                            AssistChip(
                                onClick = { folderName = preset },
                                label = { Text(preset) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sanitized = folderName.trim().replace(Regex("[/\\\\:*?\"<>|]"), "-")
                    if (sanitized.isNotBlank()) {
                        onCreate(sanitized)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_create_model_folder_button")
            ) { Text("Create Sub-Folder") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PasteRoundtableArtifactDialog(
    roundtableRoot: String,
    modelFolder: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var fileName by remember { mutableStateOf("proposal.md") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Paste into $roundtableRoot/$modelFolder/") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename (.md, .py, .rs, .ts, .yaml, .tex...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Paste external model proposal or code") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_roundtable_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onSubmit(fileName, content)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_paste_roundtable_button")
            ) { Text("Queue for Scrutiny") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
