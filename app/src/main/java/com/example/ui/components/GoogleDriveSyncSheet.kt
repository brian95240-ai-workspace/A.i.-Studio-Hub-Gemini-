package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ResearchProject
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.sync.DrivePushReport
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoogleDriveSyncSheet(
    project: ResearchProject,
    driveSyncStatus: String,
    driveWebUrl: String?,
    lastSyncLabel: String?,
    lastReport: DrivePushReport?,
    syncing: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onApplyTemplate: (ScaffoldingBlueprint) -> Unit,
    onPushToDrive: () -> Unit,
    onPullFromDrive: () -> Unit
) {
    val context = LocalContext.current
    val activeBlueprint = remember(project.blueprintId) {
        ScaffoldingBlueprint.fromWire(project.blueprintId)
    }
    var selectedBlueprint by remember(activeBlueprint) { mutableStateOf(activeBlueprint) }
    val statusColor = when (driveSyncStatus) {
        "In Sync" -> VerifiedEmerald
        "Local Ahead" -> BridgeAmber
        else -> MaterialTheme.colorScheme.primary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google Drive Cloud-Sync Engine", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "My Drive / Vortex Researcher / ${project.rootFolderName}/",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TelemetryPill(text = driveSyncStatus, accentColor = statusColor)
            }

            Text(
                text = "Mirrors your local workspace to Google Drive over least-privilege drive.file OAuth using MD5 differential sync and canonical system architecture templates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Primary Push / Pull Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPushToDrive,
                    enabled = !syncing,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("drive_sheet_push_button")
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Push → Drive ↑")
                    }
                }
                OutlinedButton(
                    onClick = onPullFromDrive,
                    enabled = !syncing && canEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("drive_sheet_pull_button")
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pull ← Drive ↓")
                }
            }

            if (!driveWebUrl.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { openExternalUrl(context, driveWebUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_drive_folder_web_button")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Mirrored Folder in Google Drive")
                }
            }

            if (!lastSyncLabel.isNullOrBlank() || lastReport != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Last Differential Sync Telemetry",
                            style = MaterialTheme.typography.labelLarge,
                            color = VerifiedEmerald
                        )
                        Text(
                            text = lastSyncLabel ?: "+${lastReport?.uploadedCount} new · ${lastReport?.updatedCount} updated · ${lastReport?.skippedUnchangedCount} unchanged",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        Text(
                            text = "Includes vortex-workspace.json manifest & all canonical directories.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // System Architecture Template Enforcement
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("System Architecture Template Contract", style = MaterialTheme.typography.titleSmall)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScaffoldingBlueprint.entries.forEach { bp ->
                    FilterChip(
                        selected = selectedBlueprint == bp,
                        onClick = { selectedBlueprint = bp },
                        label = { Text(bp.title) },
                        modifier = Modifier.testTag("drive_template_chip_${bp.wire.lowercase()}")
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selectedBlueprint.plainEnglishSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Enforced Canonical Folders",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedBlueprint.canonicalFolders.forEach { folder ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = VerifiedEmerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "/$folder",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (canEdit && selectedBlueprint != activeBlueprint) {
                        Button(
                            onClick = { onApplyTemplate(selectedBlueprint) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("apply_template_locally_button")
                        ) {
                            Text("Apply '${selectedBlueprint.title}' Template")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
