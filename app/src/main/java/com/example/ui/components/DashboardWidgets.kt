package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToDrive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChatSearchMatch
import com.example.data.model.MetadataTagEngine
import com.example.data.model.ResearchMode
import com.example.data.model.RoundtableStatus
import com.example.data.model.TeamRole
import com.example.data.sync.OfflineSyncTelemetry
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.DashboardProjectFilter
import com.example.ui.viewmodel.DashboardProjectSummary
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.ProjectSyncBadge
import com.example.ui.viewmodel.RecentPrdCardItem
import com.example.ui.viewmodel.RecentRoundtableCardItem
import com.example.ui.viewmodel.SyncChannelHealth

@Composable
fun syncHealthColor(health: SyncChannelHealth): Color = when (health) {
    SyncChannelHealth.SYNCED -> VerifiedEmerald
    SyncChannelHealth.SYNCING -> ElectricCyan
    SyncChannelHealth.AHEAD_PENDING -> BridgeAmber
    SyncChannelHealth.OFFLINE -> BridgeAmber
    SyncChannelHealth.ERROR -> CrimsonError
    SyncChannelHealth.UNLINKED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun DashboardCommandHero(
    userDisplayName: String,
    dashboardState: DashboardUiState,
    syncTelemetry: OfflineSyncTelemetry,
    bulkMode: Boolean,
    envSecretCount: Int,
    onNewProject: () -> Unit,
    onInitFromTemplate: () -> Unit,
    onOpenJoinDialog: () -> Unit,
    onToggleBulkMode: () -> Unit,
    onTriggerSync: () -> Unit,
    onOpenInfraHub: () -> Unit,
    onOpenNextStepsHandoff: () -> Unit,
    onOpenEnvVault: () -> Unit,
    onOpenRouterSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .drawBehind {
                drawRect(color = surfaceColor)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.16f),
                            TelemetryBlue.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.12f),
                        radius = size.maxDimension * 0.85f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$userDisplayName · ${dashboardState.totalProjects} active workspace(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val statusAccent = when {
                    syncTelemetry.hasActiveConflict -> CrimsonError
                    !syncTelemetry.isOnline -> BridgeAmber
                    dashboardState.pendingSyncProjectsCount > 0 -> BridgeAmber
                    else -> VerifiedEmerald
                }
                val statusLabel = when {
                    syncTelemetry.hasActiveConflict -> "Conflict"
                    !syncTelemetry.isOnline -> "Offline Cache"
                    dashboardState.pendingSyncProjectsCount > 0 -> "${dashboardState.pendingSyncProjectsCount} Pending"
                    else -> "All Synced"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = statusAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(role = Role.Button, onClick = onTriggerSync)
                            .testTag("dashboard_hero_sync_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (syncTelemetry.isOnline) Icons.Outlined.Sync else Icons.Outlined.CloudOff,
                                contentDescription = stringResource(R.string.cd_sync_now),
                                tint = statusAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = statusAccent
                            )
                        }
                    }

                    // Single Clean Dashboard Menu (Template Init, Team Join, Bulk ZIP, .env Vault, Router, Sign Out)
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { overflowMenuExpanded = true }
                                .testTag("dashboard_menu_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.cd_dashboard_menu),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.testTag("dashboard_overflow_menu")
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountTree,
                                        contentDescription = null,
                                        tint = ElectricCyan
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = stringResource(R.string.dashboard_menu_init_template),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.dashboard_menu_init_template_sub),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = ElectricCyan
                                        )
                                    }
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onInitFromTemplate()
                                },
                                modifier = Modifier.testTag("dashboard_menu_init_template")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.GroupAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                text = { Text(stringResource(R.string.dashboard_join_team)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenJoinDialog()
                                },
                                modifier = Modifier.testTag("dashboard_menu_join_team")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderZip,
                                        contentDescription = null,
                                        tint = ElectricCyan
                                    )
                                },
                                text = {
                                    Text(
                                        if (bulkMode) "Exit Bulk ZIP Mode"
                                        else "Bulk ZIP Export Mode"
                                    )
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onToggleBulkMode()
                                },
                                modifier = Modifier.testTag("dashboard_menu_bulk_zip")
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Hub,
                                        contentDescription = null,
                                        tint = ElectricCyan
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(stringResource(R.string.dashboard_menu_infra_hub))
                                        Text(
                                            text = stringResource(R.string.dashboard_menu_infra_hub_sub),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenInfraHub()
                                },
                                modifier = Modifier.testTag("dashboard_menu_infra_hub")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = VerifiedEmerald
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(stringResource(R.string.dashboard_menu_next_steps))
                                        Text(
                                            text = stringResource(R.string.dashboard_menu_next_steps_sub),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenNextStepsHandoff()
                                },
                                modifier = Modifier.testTag("dashboard_menu_next_steps")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Security,
                                        contentDescription = null,
                                        tint = if (envSecretCount > 0) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text("Encrypted .env Secrets Vault")
                                        Text(
                                            text = if (envSecretCount > 0) "$envSecretCount key(s) configured" else "Template form & clipboard assist",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenEnvVault()
                                },
                                modifier = Modifier.testTag("dashboard_menu_env_vault")
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = { Text(stringResource(R.string.model_router_settings)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onOpenRouterSettings()
                                },
                                modifier = Modifier.testTag("dashboard_menu_router_settings")
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                                        contentDescription = null,
                                        tint = CrimsonError
                                    )
                                },
                                text = {
                                    Text(
                                        text = stringResource(R.string.sign_out),
                                        color = CrimsonError
                                    )
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onSignOut()
                                },
                                modifier = Modifier.testTag("dashboard_menu_sign_out")
                            )
                        }
                    }
                }
            }

            // Direct 1-Tap Creation Row (zero nested menus)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInitFromTemplate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_new_from_template_button")
                ) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "New Template",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onNewProject,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_new_project_button")
                ) {
                    Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Quick Session",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SyncFleetCoverageStrip(
    dashboardState: DashboardUiState,
    selectedFilter: DashboardProjectFilter,
    onSelectFilter: (DashboardProjectFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = dashboardState.totalProjects.coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SyncCoverageMetricTile(
            title = stringResource(R.string.dashboard_sync_github),
            countLabel = "${dashboardState.githubLinkedCount}/${dashboardState.totalProjects}",
            subtitle = "Mirrored",
            progress = dashboardState.githubLinkedCount.toFloat() / total.toFloat(),
            icon = Icons.Outlined.Code,
            accent = ElectricCyan,
            selected = selectedFilter == DashboardProjectFilter.GITHUB,
            testTag = "dashboard_metric_github",
            onClick = {
                onSelectFilter(
                    if (selectedFilter == DashboardProjectFilter.GITHUB) DashboardProjectFilter.ALL
                    else DashboardProjectFilter.GITHUB
                )
            },
            modifier = Modifier.weight(1f)
        )
        SyncCoverageMetricTile(
            title = stringResource(R.string.dashboard_sync_drive),
            countLabel = "${dashboardState.driveLinkedCount}/${dashboardState.totalProjects}",
            subtitle = "Linked",
            progress = dashboardState.driveLinkedCount.toFloat() / total.toFloat(),
            icon = Icons.Outlined.AddToDrive,
            accent = VerifiedEmerald,
            selected = selectedFilter == DashboardProjectFilter.DRIVE,
            testTag = "dashboard_metric_drive",
            onClick = {
                onSelectFilter(
                    if (selectedFilter == DashboardProjectFilter.DRIVE) DashboardProjectFilter.ALL
                    else DashboardProjectFilter.DRIVE
                )
            },
            modifier = Modifier.weight(1f)
        )
        val localAccent = if (dashboardState.pendingSyncProjectsCount > 0) BridgeAmber else TelemetryBlue
        val localSubtitle = if (dashboardState.pendingSyncProjectsCount > 0) {
            "${dashboardState.pendingSyncProjectsCount} Pending"
        } else {
            "${dashboardState.localBoundCount} SAF Bound"
        }
        SyncCoverageMetricTile(
            title = stringResource(R.string.dashboard_sync_local),
            countLabel = "${dashboardState.totalProjects}/${dashboardState.totalProjects}",
            subtitle = localSubtitle,
            progress = if (dashboardState.totalProjects == 0) 0f else 1f,
            icon = Icons.Outlined.Storage,
            accent = localAccent,
            selected = selectedFilter == DashboardProjectFilter.NEEDS_SYNC,
            testTag = "dashboard_metric_local",
            onClick = {
                onSelectFilter(
                    if (selectedFilter == DashboardProjectFilter.NEEDS_SYNC) DashboardProjectFilter.ALL
                    else DashboardProjectFilter.NEEDS_SYNC
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SyncCoverageMetricTile(
    title: String,
    countLabel: String,
    subtitle: String,
    progress: Float,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outline,
        animationSpec = tween(220),
        label = "metric_border_$title"
    )
    Surface(
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title sync coverage: $countLabel $subtitle" }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = countLabel,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 1
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.16f),
                strokeCap = StrokeCap.Round
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentPrdQuickCard(
    item: RecentPrdCardItem,
    bulkMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bumpColor = when (item.bumpLabel) {
        "Major" -> ElectricCyan
        "Minor" -> VerifiedEmerald
        else -> TelemetryBlue
    }
    val cardBorder = if (bulkMode && isSelected) {
        BorderStroke(1.5.dp, ElectricCyan)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    val cardBg = if (bulkMode && isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .width(268.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("dashboard_prd_card_${item.prd.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = VerifiedEmerald.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.prd.version.ifBlank { "v1.0" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = VerifiedEmerald,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                TelemetryPill(text = item.bumpLabel, accentColor = bumpColor)
                Spacer(Modifier.weight(1f))
                if (bulkMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected for ZIP" else "Select PRD for ZIP",
                        tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = item.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = item.prd.title.ifBlank { "${item.project.title} PRD" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.prd.deltaSummary.ifBlank {
                    item.prd.revisionRequest.ifBlank { "Baseline product specification & acceptance requirements." }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${item.project.rootFolderName.ifBlank { item.project.title }}/",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open PRD Studio",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun RecentRoundtableQuickCard(
    entry: RecentRoundtableCardItem,
    bulkMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusAccent = when (entry.status) {
        RoundtableStatus.ADOPTED, RoundtableStatus.CONSENSUS_RESOLVED -> VerifiedEmerald
        RoundtableStatus.REBUTTAL_NEEDED -> BridgeAmber
        RoundtableStatus.ANALYZING -> ElectricCyan
        RoundtableStatus.REJECTED -> CrimsonError
        RoundtableStatus.QUEUED -> TelemetryBlue
    }
    val cardBorder = if (bulkMode && isSelected) {
        BorderStroke(1.5.dp, ElectricCyan)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    val cardBg = if (bulkMode && isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .width(268.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("dashboard_roundtable_card_${entry.item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = TelemetryBlue.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, TelemetryBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${entry.item.modelFolder}/",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TelemetryBlue,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                TelemetryPill(text = entry.status.label, accentColor = statusAccent)
                Spacer(Modifier.weight(1f))
                if (bulkMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected for ZIP" else "Select file for ZIP",
                        tint = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = entry.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = entry.item.fileName.ifBlank { "Roundtable Note" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${entry.item.detectedLanguage} · Open project to view discussion & audit details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = entry.project.title.ifBlank { entry.project.rootFolderName },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open Roundtable",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardProjectCard(
    summary: DashboardProjectSummary,
    bulkMode: Boolean = false,
    selectedFilePaths: Set<String> = emptySet(),
    isFilesExpanded: Boolean = false,
    matchedTags: List<String> = emptyList(),
    matchedChatCount: Int = 0,
    onLongPressSelect: () -> Unit = {},
    onToggleSelectProject: () -> Unit = {},
    onToggleExpandFiles: () -> Unit = {},
    onToggleSelectFile: (String) -> Unit = {},
    onSelectAllProjectFiles: () -> Unit = {},
    onClearProjectFiles: () -> Unit = {},
    onOpenResearch: () -> Unit,
    onOpenPrds: () -> Unit,
    onOpenRoundtable: () -> Unit,
    onOpenGitHubConsole: () -> Unit,
    onOpenDriveConsole: () -> Unit,
    onSyncLocal: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenTeamShare: () -> Unit = {},
    canDelete: Boolean = false,
    onRequestDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var cardMenuExpanded by remember { mutableStateOf(false) }
    val project = summary.project
    val totalSelectable = summary.selectableFiles.size
    val selectedCount = selectedFilePaths.size
    val triState = when {
        selectedCount <= 0 -> ToggleableState.Off
        totalSelectable > 0 && selectedCount >= totalSelectable -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val hasBulkSelection = bulkMode && selectedCount > 0

    val containerColor = when {
        hasBulkSelection -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
        summary.isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = when {
        hasBulkSelection -> ElectricCyan
        summary.isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (hasBulkSelection || summary.isActive) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { if (bulkMode) onToggleSelectProject() else onOpenResearch() },
                onLongClick = onLongPressSelect
            )
            .testTag("dashboard_project_card_${project.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Bulk TriStateCheckbox (when active), Title, folder path & Export / File Picker action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (bulkMode) {
                    TriStateCheckbox(
                        state = triState,
                        onClick = onToggleSelectProject,
                        colors = CheckboxDefaults.colors(checkedColor = ElectricCyan),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_bulk_checkbox_${project.id}")
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (summary.isActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan)
                            )
                        }
                        Text(
                            text = project.title.ifBlank { "Untitled Research" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = "${project.rootFolderName.ifBlank { "workspace" }}/ · ${ResearchMode.fromWire(project.mode).label}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Updated ${summary.updatedLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dashboard_project_updated_${project.id}")
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (bulkMode) {
                    Surface(
                        color = if (isFilesExpanded) ElectricCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (selectedCount > 0 || isFilesExpanded) ElectricCyan.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onToggleExpandFiles)
                            .testTag("dashboard_bulk_expand_files_${project.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "$selectedCount/$totalSelectable",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (selectedCount > 0) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (isFilesExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = stringResource(R.string.cd_dashboard_expand_files),
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenExport,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dashboard_export_project_${project.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = stringResource(R.string.cd_dashboard_export_project),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Export",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { cardMenuExpanded = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_project_more_${project.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Project options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = cardMenuExpanded,
                                onDismissRequest = { cardMenuExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Groups,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    text = { Text(stringResource(R.string.team_share_settings)) },
                                    onClick = {
                                        cardMenuExpanded = false
                                        onOpenTeamShare()
                                    },
                                    modifier = Modifier.testTag("dashboard_project_team_share_${project.id}")
                                )
                                if (canDelete) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = null,
                                                tint = CrimsonError
                                            )
                                        },
                                        text = { Text("Delete Project", color = CrimsonError) },
                                        onClick = {
                                            cardMenuExpanded = false
                                            onRequestDelete()
                                        },
                                        modifier = Modifier.testTag("dashboard_project_delete_${project.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Metadata & Auto-Tag pills
            val displayTags = remember(project.id, project.tags, project.detectedDomain, matchedTags) {
                val all = MetadataTagEngine.effectiveProjectTags(project)
                    .filterNot { it.equals(project.detectedDomain, ignoreCase = true) }
                (matchedTags + all).distinctBy { it.lowercase() }.take(3)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (summary.isActive) {
                    TelemetryPill("Active", ElectricCyan)
                }
                TelemetryPill(
                    text = summary.role.label,
                    accentColor = if (summary.role == TeamRole.ADMIN) BridgeAmber else TelemetryBlue
                )
                if (project.detectedDomain.isNotBlank()) {
                    TelemetryPill(project.detectedDomain, MaterialTheme.colorScheme.secondary)
                }
                if (project.latestPrdVersion.isNotBlank()) {
                    TelemetryPill("PRD ${project.latestPrdVersion}", VerifiedEmerald)
                }
                displayTags.forEach { tag ->
                    val isMatched = matchedTags.any { it.equals(tag, ignoreCase = true) }
                    TelemetryPill(
                        text = "#$tag",
                        accentColor = if (isMatched) ElectricCyan else TelemetryBlue
                    )
                }
            }

            // Chats hidden until project is opened indicator (never leaks chat content on dashboard during normal browsing)
            Surface(
                color = if (matchedChatCount > 0) ElectricCyan.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(
                    1.dp,
                    if (matchedChatCount > 0) ElectricCyan.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_chats_hidden_${project.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (matchedChatCount > 0) Icons.Outlined.Forum else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = if (matchedChatCount > 0) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            matchedChatCount > 0 -> {
                                val unit = if (matchedChatCount == 1) "chat" else "chats"
                                "$matchedChatCount matching $unit · Open project to view"
                            }
                            summary.chatCount > 0 -> {
                                val unit = if (summary.chatCount == 1) "chat" else "chats"
                                "${summary.chatCount} $unit hidden · Open project to view"
                            }
                            else -> "Chats hidden · Open project to start"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (matchedChatCount > 0) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "Open project",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Expandable per-file selector when in Bulk Selection Mode
            AnimatedVisibility(visible = bulkMode && isFilesExpanded) {
                BulkProjectFilePickerDrawer(
                    projectId = project.id,
                    files = summary.selectableFiles,
                    selectedPaths = selectedFilePaths,
                    onToggleFile = onToggleSelectFile,
                    onSelectAllProjectFiles = onSelectAllProjectFiles,
                    onClearProjectFiles = onClearProjectFiles
                )
            }

            // 3-Channel Sync Status Matrix (GitHub | Drive | Local)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProjectSyncChannelTile(
                    channelTitle = stringResource(R.string.dashboard_sync_github),
                    icon = Icons.Outlined.Code,
                    badge = summary.githubBadge,
                    contentDesc = stringResource(R.string.cd_dashboard_open_github),
                    testTag = "dashboard_sync_github_${project.id}",
                    onClick = onOpenGitHubConsole,
                    modifier = Modifier.weight(1f)
                )
                ProjectSyncChannelTile(
                    channelTitle = stringResource(R.string.dashboard_sync_drive),
                    icon = Icons.Outlined.AddToDrive,
                    badge = summary.driveBadge,
                    contentDesc = stringResource(R.string.cd_dashboard_open_drive),
                    testTag = "dashboard_sync_drive_${project.id}",
                    onClick = onOpenDriveConsole,
                    modifier = Modifier.weight(1f)
                )
                ProjectSyncChannelTile(
                    channelTitle = stringResource(R.string.dashboard_sync_local),
                    icon = Icons.Outlined.Storage,
                    badge = summary.localBadge,
                    contentDesc = stringResource(R.string.cd_dashboard_open_local),
                    testTag = "dashboard_sync_local_${project.id}",
                    onClick = onSyncLocal,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick-jump footer buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickJumpChip(
                    icon = Icons.Outlined.Science,
                    label = "${summary.fileCount} Files",
                    accent = ElectricCyan,
                    testTag = "dashboard_jump_research_${project.id}",
                    onClick = if (bulkMode) onToggleExpandFiles else onOpenResearch,
                    modifier = Modifier.weight(1f)
                )
                QuickJumpChip(
                    icon = Icons.Outlined.Description,
                    label = if (project.latestPrdVersion.isNotBlank()) "PRD ${project.latestPrdVersion}" else "${summary.prdCount} PRDs",
                    accent = VerifiedEmerald,
                    testTag = "dashboard_jump_prd_${project.id}",
                    onClick = onOpenPrds,
                    modifier = Modifier.weight(1f)
                )
                QuickJumpChip(
                    icon = Icons.Outlined.Forum,
                    label = "${summary.roundtableCount} Notes",
                    accent = TelemetryBlue,
                    testTag = "dashboard_jump_roundtable_${project.id}",
                    onClick = onOpenRoundtable,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProjectSyncChannelTile(
    channelTitle: String,
    icon: ImageVector,
    badge: ProjectSyncBadge,
    contentDesc: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = syncHealthColor(badge.health)
    val muted = badge.health == SyncChannelHealth.UNLINKED
    Surface(
        color = accent.copy(alpha = if (muted) 0.06f else 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (muted) 0.25f else 0.45f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$channelTitle ${badge.label}. $contentDesc" }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (badge.health) {
                    SyncChannelHealth.SYNCING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.6.dp,
                            color = accent
                        )
                    }
                    SyncChannelHealth.ERROR -> {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = channelTitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = badge.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge.detail,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickJumpChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardChatSearchMatchCard(
    match: ChatSearchMatch,
    onOpenProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = match.turn.role.equals("USER", ignoreCase = true)
    val categoryAccent = noteCategoryAccent(match.category)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, categoryAccent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onOpenProject)
            .testTag("dashboard_chat_match_${match.project.id}_${match.turnIndex}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = categoryAccent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = match.project.title.ifBlank { match.project.rootFolderName },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPill(
                    text = match.category.label,
                    accentColor = categoryAccent
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open matching chat project",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }

            Text(
                text = "${if (isUser) "You" else match.turn.modelUsed.ifBlank { "Vortex AI" }}: ${match.snippet}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val pills = (match.matchedTags + match.effectiveTags)
                .filterNot { it.equals(match.category.label, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .take(4)
            if (pills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pills.forEach { tag ->
                        val highlighted = match.matchedTags.any { it.equals(tag, ignoreCase = true) }
                        TelemetryPill(
                            text = "#$tag",
                            accentColor = if (highlighted) ElectricCyan else TelemetryBlue
                        )
                    }
                }
            }
        }
    }
}

