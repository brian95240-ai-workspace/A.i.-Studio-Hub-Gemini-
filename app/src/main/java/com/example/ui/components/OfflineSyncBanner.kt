package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ProjectFile
import com.example.data.sync.OfflineSyncTelemetry
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@Composable
fun OfflineSyncStatusBanner(
    telemetry: OfflineSyncTelemetry,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = !telemetry.isOnline || telemetry.isSyncing || telemetry.pendingOpCount > 0

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val accent = when {
            !telemetry.isOnline -> BridgeAmber
            telemetry.isSyncing -> MaterialTheme.colorScheme.primary
            telemetry.pendingOpCount > 0 -> MaterialTheme.colorScheme.primary
            else -> VerifiedEmerald
        }

        Surface(
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("offline_sync_status_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (telemetry.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                } else {
                    Icon(
                        imageVector = when {
                            !telemetry.isOnline -> Icons.Outlined.CloudOff
                            telemetry.pendingOpCount > 0 -> Icons.Outlined.Sync
                            else -> Icons.Outlined.CloudDone
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            telemetry.isSyncing ->
                                "Syncing ${telemetry.pendingOpCount} queued change(s) to cloud..."
                            !telemetry.isOnline ->
                                stringResource(R.string.offline_mode_banner_title)
                            else ->
                                "${telemetry.pendingOpCount} offline change(s) queued for cloud sync"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            telemetry.isSyncing ->
                                "Replaying local Room cache outbox automatically."
                            !telemetry.isOnline && telemetry.pendingOpCount > 0 ->
                                "${telemetry.pendingOpCount} local edit(s) saved in Room · auto-syncs when online."
                            !telemetry.isOnline ->
                                "Browse & organize cached files freely · sync resumes when connected."
                            else ->
                                telemetry.lastSyncError ?: "Tap Sync Now to push queued local edits immediately."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (telemetry.isOnline && !telemetry.isSyncing && telemetry.pendingOpCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = onSyncNow,
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        Text(
                            text = stringResource(R.string.offline_sync_now),
                            color = accent
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateRepoFileDialog(
    availableFolders: List<String>,
    onCreate: (relativePath: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFolder by remember { mutableStateOf(availableFolders.firstOrNull().orEmpty()) }
    var fileName by remember { mutableStateOf("notes.md") }
    var content by remember { mutableStateOf("# Notes\n\n") }

    val cleanFileName = fileName.trim().trimStart('/')
    val previewPath = if (selectedFolder.isBlank()) cleanFileName else "$selectedFolder/$cleanFileName"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.repo_new_file)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target folder (works offline via Room cache):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedFolder.isEmpty(),
                        onClick = { selectedFolder = "" },
                        label = { Text("root/") }
                    )
                    availableFolders.take(8).forEach { folder ->
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            label = { Text("$folder/") }
                        )
                    }
                }
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File name (e.g., notes.md, draft.py)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_repo_file_name_input")
                )
                Text(
                    text = "Path: $previewPath",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Initial content") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_repo_file_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cleanFileName.isNotBlank()) {
                        onCreate(previewPath, content)
                        onDismiss()
                    }
                },
                enabled = cleanFileName.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_repo_file_button")
            ) {
                Text("Create File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveRenameRepoFileDialog(
    file: ProjectFile,
    availableFolders: List<String>,
    onMoveRename: (oldPath: String, newPath: String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFolder = remember(file.relativePath) {
        val idx = file.relativePath.lastIndexOf('/')
        if (idx == -1) "" else file.relativePath.substring(0, idx)
    }
    val initialName = remember(file.relativePath) {
        file.relativePath.substringAfterLast('/')
    }
    var targetFolder by remember(file.relativePath) { mutableStateOf(initialFolder) }
    var targetName by remember(file.relativePath) { mutableStateOf(initialName) }

    val cleanName = targetName.trim().trimStart('/')
    val newPath = if (targetFolder.isBlank()) cleanName else "$targetFolder/$cleanName"
    val isChanged = cleanName.isNotBlank() && newPath != file.relativePath

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.repo_move_rename_file)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current: ${file.relativePath}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Move into folder:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = targetFolder.isEmpty(),
                        onClick = { targetFolder = "" },
                        label = { Text("root/") }
                    )
                    availableFolders.take(8).forEach { folder ->
                        FilterChip(
                            selected = targetFolder == folder,
                            onClick = { targetFolder = folder },
                            label = { Text("$folder/") }
                        )
                    }
                }
                OutlinedTextField(
                    value = targetFolder,
                    onValueChange = { targetFolder = it.trim().trim('/') },
                    label = { Text("Custom sub-folder (blank for root)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("move_repo_file_folder_input")
                )
                OutlinedTextField(
                    value = targetName,
                    onValueChange = { targetName = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("move_repo_file_name_input")
                )
                Text(
                    text = "New path: $newPath",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isChanged) {
                        onMoveRename(file.relativePath, newPath)
                        onDismiss()
                    }
                },
                enabled = isChanged,
                modifier = Modifier.testTag("confirm_move_repo_file_button")
            ) {
                Text("Save Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditRepoFileDialog(
    file: ProjectFile,
    onSaveContent: (relativePath: String, newContent: String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(file.relativePath, file.content) { mutableStateOf(file.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = "Edit ${file.relativePath.substringAfterLast('/')}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = file.relativePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                        .testTag("edit_repo_file_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveContent(file.relativePath, draft)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_edit_repo_file_button")
            ) {
                Text("Save Offline")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
