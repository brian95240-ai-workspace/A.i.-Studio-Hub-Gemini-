package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.sync.ConflictKind
import com.example.data.sync.ConflictResolutionStrategy
import com.example.data.sync.FileSyncConflict
import com.example.data.sync.SyncConflictHelper
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncConflictResolutionDialog(
    conflict: FileSyncConflict,
    isResolving: Boolean,
    onResolve: (ConflictResolutionStrategy) -> Unit,
    onDismissLater: () -> Unit
) {
    var selectedTab by remember(conflict.conflictId) { mutableIntStateOf(0) } // 0=Local, 1=Server, 2=Summary
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    val localTimeLabel = remember(conflict.localUpdatedAtMillis) {
        if (conflict.localUpdatedAtMillis > 0L) timeFmt.format(Date(conflict.localUpdatedAtMillis)) else "Local edit"
    }
    val serverTimeLabel = remember(conflict.serverUpdatedAtMillis) {
        if (conflict.serverUpdatedAtMillis > 0L) timeFmt.format(Date(conflict.serverUpdatedAtMillis)) else "Latest server"
    }
    val localLines = remember(conflict.localContent) { conflict.localContent.lines().size }
    val serverLines = remember(conflict.serverContent) { conflict.serverContent.lines().size }
    val copyPathPreview = remember(conflict.filePath) {
        SyncConflictHelper.buildConflictCopyPath(conflict.filePath)
    }

    AlertDialog(
        onDismissRequest = { if (!isResolving) onDismissLater() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = {
            Icon(
                imageVector = Icons.Outlined.CompareArrows,
                contentDescription = null,
                tint = BridgeAmber,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.sync_conflict_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_conflict_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.sync_conflict_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // File badge + source kind
                Surface(
                    color = BridgeAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BridgeAmber.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = conflict.filePath,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = BridgeAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (conflict.kind == ConflictKind.ROUNDTABLE_FILE) {
                                "A.i. Roundtable Editor · Local ($localTimeLabel) vs Server ($serverTimeLabel)"
                            } else {
                                "Repository File Editor · Local ($localTimeLabel) vs Server ($serverTimeLabel)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Version preview selector chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Local ($localLines ln)") },
                        modifier = Modifier.testTag("conflict_tab_local")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Server ($serverLines ln)") },
                        modifier = Modifier.testTag("conflict_tab_server")
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("Compare") },
                        modifier = Modifier.testTag("conflict_tab_compare")
                    )
                }

                // Content preview pane
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                Text(
                                    text = "LOCAL ROOM CACHE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = conflict.localContent.ifBlank { "(Empty file)" }.take(4000),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            1 -> {
                                Text(
                                    text = "FIRESTORE SERVER VERSION",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = VerifiedEmerald
                                )
                                Text(
                                    text = conflict.serverContent.ifBlank { "(Empty file)" }.take(4000),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            else -> {
                                ConflictDiffSnippet(
                                    title = "Local Cache (${conflict.localContent.length} chars)",
                                    accent = MaterialTheme.colorScheme.primary,
                                    snippet = conflict.localContent.take(600)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                ConflictDiffSnippet(
                                    title = "Server Firestore (${conflict.serverContent.length} chars)",
                                    accent = VerifiedEmerald,
                                    snippet = conflict.serverContent.take(600)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Tip: 'Keep Both' preserves the server version and saves your local edits as $copyPathPreview.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Resolution Actions
                if (isResolving) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Applying resolution & syncing...", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onResolve(ConflictResolutionStrategy.KEEP_LOCAL) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("conflict_resolve_keep_local")
                            ) {
                                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sync_conflict_keep_local), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onResolve(ConflictResolutionStrategy.USE_SERVER) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("conflict_resolve_use_server")
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sync_conflict_use_server), maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = { onResolve(ConflictResolutionStrategy.KEEP_BOTH) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VerifiedEmerald),
                            border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("conflict_resolve_keep_both")
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${stringResource(R.string.sync_conflict_keep_both)} (Save Local Copy)")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isResolving) {
                TextButton(
                    onClick = onDismissLater,
                    modifier = Modifier.testTag("conflict_resolve_later")
                ) {
                    Text(stringResource(R.string.sync_conflict_later))
                }
            }
        }
    )
}

@Composable
private fun ConflictDiffSnippet(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    snippet: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = accent
        )
        Text(
            text = snippet.ifBlank { "(Empty)" },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                .padding(6.dp)
        )
    }
}
