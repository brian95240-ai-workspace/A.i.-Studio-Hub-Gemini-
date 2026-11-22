package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ProjectFile
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.formatCompactByteSize

@Composable
fun BulkSelectionModeBanner(
    selectedProjectCount: Int,
    selectedFileCount: Int,
    estimatedBytes: Long,
    allVisibleSelected: Boolean,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onExitBulkMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_bulk_mode_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderZip,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bulk ZIP Selection Active",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedFileCount == 0) {
                            "Tap projects, recent cards, or expand files to build a ZIP archive"
                        } else {
                            "$selectedProjectCount project(s) · $selectedFileCount file(s) · ~${formatCompactByteSize(estimatedBytes)}"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onExitBulkMode,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_bulk_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSelectAllVisible,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_bulk_select_all_button")
                ) {
                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (allVisibleSelected) "Deselect Visible" else stringResource(R.string.dashboard_bulk_select_all),
                        maxLines = 1
                    )
                }
                TextButton(
                    onClick = onClearSelection,
                    enabled = selectedFileCount > 0,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_bulk_clear_button")
                ) {
                    Text(stringResource(R.string.dashboard_bulk_clear))
                }
                TextButton(
                    onClick = onExitBulkMode,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_bulk_done_button")
                ) {
                    Text(stringResource(R.string.dashboard_bulk_done))
                }
            }
        }
    }
}

@Composable
fun BulkProjectFilePickerDrawer(
    projectId: String,
    files: List<ProjectFile>,
    selectedPaths: Set<String>,
    onToggleFile: (String) -> Unit,
    onSelectAllProjectFiles: () -> Unit,
    onClearProjectFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_bulk_file_drawer_$projectId")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Individual Files (${selectedPaths.size}/${files.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onSelectAllProjectFiles,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("All", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = onClearProjectFiles,
                    enabled = selectedPaths.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("None", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (files.isEmpty()) {
                Text(
                    text = "No scaffolded files in this workspace yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                files.forEach { file ->
                    val checked = file.relativePath in selectedPaths
                    val categoryColor = when (file.category.uppercase()) {
                        "PRD" -> VerifiedEmerald
                        "ROUNDTABLE" -> TelemetryBlue
                        else -> ElectricCyan
                    }
                    val sizeLabel = formatCompactByteSize(file.content.toByteArray(Charsets.UTF_8).size.toLong())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Checkbox) { onToggleFile(file.relativePath) }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("dashboard_bulk_file_row_${projectId}_${file.relativePath}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = ElectricCyan)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.relativePath,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (checked) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${file.category} · $sizeLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = categoryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BulkZipExportDock(
    visible: Boolean,
    selectedProjectCount: Int,
    selectedFileCount: Int,
    estimatedBytes: Long,
    includeManifest: Boolean,
    exporting: Boolean,
    onToggleIncludeManifest: () -> Unit,
    onSaveZip: () -> Unit,
    onShareZip: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_bulk_export_dock")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderZip,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ready to Compress ZIP Backup",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$selectedProjectCount project(s) · $selectedFileCount file(s) · ~${formatCompactByteSize(estimatedBytes)} raw",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilterChip(
                        selected = includeManifest,
                        onClick = onToggleIncludeManifest,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Manifest",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dashboard_bulk_manifest_chip")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSaveZip,
                        enabled = !exporting && selectedFileCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dashboard_bulk_save_zip_button")
                    ) {
                        if (exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.dashboard_bulk_save_zip))
                    }

                    OutlinedButton(
                        onClick = onShareZip,
                        enabled = !exporting && selectedFileCount > 0,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dashboard_bulk_share_zip_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.dashboard_bulk_share_zip))
                    }
                }
            }
        }
    }
}
