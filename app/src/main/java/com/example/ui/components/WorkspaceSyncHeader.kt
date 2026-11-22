package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToDrive
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.github.GitHubHeaderSyncState
import com.example.data.github.GitHubSyncDirection
import com.example.data.github.GitHubSyncPhase
import com.example.data.sync.DriveHeaderSyncState
import com.example.data.sync.DriveSyncDirection
import com.example.data.sync.DriveSyncPhase
import com.example.data.sync.OfflineSyncTelemetry
import com.example.data.sync.SyncConnectionState
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import kotlin.math.roundToInt

@Composable
fun WorkspaceSyncHeader(
    activeProjectFolder: String?,
    telemetry: OfflineSyncTelemetry,
    driveSyncState: DriveHeaderSyncState = DriveHeaderSyncState(),
    githubSyncState: GitHubHeaderSyncState = GitHubHeaderSyncState(),
    onSyncNow: () -> Unit,
    onOpenConflict: () -> Unit = {},
    onOpenDriveConsole: () -> Unit = {},
    onOpenGitHubConsole: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showInspector by remember { mutableStateOf(false) }

    val openRoomOrFirestore: () -> Unit = {
        if (telemetry.hasActiveConflict) {
            onOpenConflict()
        } else {
            showInspector = true
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = when {
                        githubSyncState.linkedRepoSlug.isNotBlank() ->
                            "${githubSyncState.linkedRepoSlug}@${githubSyncState.branch}"
                        !activeProjectFolder.isNullOrBlank() ->
                            "$activeProjectFolder/ · ${telemetry.cachedProjectCount} cached"
                        else ->
                            "Room Cache · ${telemetry.cachedProjectCount} repos"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GitHubHeaderSyncIndicator(
                        state = githubSyncState,
                        onClick = onOpenGitHubConsole
                    )

                    SyncStatusHeaderIndicator(
                        telemetry = telemetry,
                        onClick = openRoomOrFirestore
                    )
                }
            }

            // Persistent real-time Room <-> Firestore <-> Google Drive synchronization bar
            TriLayerSyncPipelineBar(
                telemetry = telemetry,
                driveSyncState = driveSyncState,
                onClickRoom = openRoomOrFirestore,
                onClickFirestore = openRoomOrFirestore,
                onClickDrive = onOpenDriveConsole
            )

            AnimatedVisibility(
                visible = driveSyncState.isActive,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
            ) {
                ActiveSyncProgressStrip(
                    icon = if (driveSyncState.direction == DriveSyncDirection.PULL) {
                        Icons.Outlined.CloudDownload
                    } else {
                        Icons.Outlined.CloudUpload
                    },
                    stageLabel = driveSyncState.currentStageLabel.ifBlank { "Mirroring with Google Drive..." },
                    completedFiles = driveSyncState.completedFiles,
                    totalFiles = driveSyncState.totalFiles,
                    progressFraction = driveSyncState.progressFraction,
                    accent = VerifiedEmerald,
                    testTag = "header_drive_sync_progress_strip"
                )
            }

            AnimatedVisibility(
                visible = githubSyncState.isActive,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
            ) {
                ActiveSyncProgressStrip(
                    icon = if (githubSyncState.direction == GitHubSyncDirection.PULL) {
                        Icons.Outlined.CloudDownload
                    } else {
                        Icons.Outlined.CloudUpload
                    },
                    stageLabel = githubSyncState.currentStageLabel.ifBlank { "Syncing with GitHub..." },
                    completedFiles = githubSyncState.completedFiles,
                    totalFiles = githubSyncState.totalFiles,
                    progressFraction = githubSyncState.progressFraction,
                    accent = MaterialTheme.colorScheme.primary,
                    testTag = "header_github_sync_progress_strip"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        }
    }

    if (showInspector) {
        SyncTelemetryInspectorDialog(
            telemetry = telemetry,
            driveSyncState = driveSyncState,
            githubSyncState = githubSyncState,
            onSyncNow = {
                onSyncNow()
                showInspector = false
            },
            onOpenDriveConsole = {
                showInspector = false
                onOpenDriveConsole()
            },
            onOpenGitHubConsole = {
                showInspector = false
                onOpenGitHubConsole()
            },
            onDismiss = { showInspector = false }
        )
    }
}

@Composable
fun TriLayerSyncPipelineBar(
    telemetry: OfflineSyncTelemetry,
    driveSyncState: DriveHeaderSyncState,
    onClickRoom: () -> Unit,
    onClickFirestore: () -> Unit,
    onClickDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "tri_sync_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tri_sync_pulse_alpha"
    )

    // 1. Local Room storage state
    val roomSyncing = telemetry.isSyncing && telemetry.pendingOpCount > 0
    val roomHasQueued = telemetry.pendingOpCount > 0
    val roomAccent by animateColorAsState(
        targetValue = when {
            telemetry.hasActiveConflict -> BridgeAmber
            roomSyncing -> MaterialTheme.colorScheme.primary
            roomHasQueued -> BridgeAmber
            else -> VerifiedEmerald
        },
        animationSpec = tween(260),
        label = "room_node_accent"
    )
    val roomStatusText = when {
        telemetry.hasActiveConflict -> "Conflict"
        roomSyncing -> "Flushing"
        roomHasQueued -> "${telemetry.pendingOpCount} queued"
        else -> "Synced"
    }

    // 2. Cloud Firestore state
    val firestoreSyncing = telemetry.isOnline &&
        (telemetry.firestoreHasPendingWrites || telemetry.isHydratingFromFirestore || telemetry.isSyncing)
    val firestoreAccent by animateColorAsState(
        targetValue = when {
            telemetry.hasActiveConflict -> BridgeAmber
            !telemetry.isOnline || telemetry.firestoreStreamError -> BridgeAmber
            firestoreSyncing -> MaterialTheme.colorScheme.primary
            telemetry.firestoreFromCache -> BridgeAmber
            else -> VerifiedEmerald
        },
        animationSpec = tween(260),
        label = "firestore_node_accent"
    )
    val firestoreStatusText = when {
        telemetry.hasActiveConflict -> "Diverged"
        !telemetry.isOnline || telemetry.firestoreStreamError -> "Offline"
        telemetry.firestoreHasPendingWrites -> "Writing"
        telemetry.isHydratingFromFirestore || telemetry.isSyncing -> "Syncing"
        telemetry.firestoreFromCache -> "Cached"
        else -> "Live"
    }

    // 3. Google Drive state
    val driveAccent by animateColorAsState(
        targetValue = when (driveSyncState.phase) {
            DriveSyncPhase.IN_SYNC -> VerifiedEmerald
            DriveSyncPhase.SYNCING -> MaterialTheme.colorScheme.primary
            DriveSyncPhase.LOCAL_AHEAD -> BridgeAmber
            DriveSyncPhase.OFFLINE -> BridgeAmber
            DriveSyncPhase.ERROR -> MaterialTheme.colorScheme.error
            DriveSyncPhase.NOT_LINKED -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(260),
        label = "drive_node_accent"
    )
    val driveStatusText = when (driveSyncState.phase) {
        DriveSyncPhase.SYNCING -> {
            val arrow = if (driveSyncState.direction == DriveSyncDirection.PULL) "↓" else "↑"
            val pct = driveSyncState.progressFraction?.let { "${(it.coerceIn(0f, 1f) * 100f).roundToInt()}%" }
            if (pct != null) "$arrow $pct" else "$arrow Sync"
        }
        DriveSyncPhase.IN_SYNC -> "Synced"
        DriveSyncPhase.LOCAL_AHEAD -> "Ahead"
        DriveSyncPhase.OFFLINE -> "Offline"
        DriveSyncPhase.ERROR -> "Error"
        DriveSyncPhase.NOT_LINKED -> "Unlinked"
    }

    val roomToFirestoreLinkColor = when {
        telemetry.hasActiveConflict || !telemetry.isOnline -> BridgeAmber
        roomSyncing || firestoreSyncing -> MaterialTheme.colorScheme.primary
        roomHasQueued -> BridgeAmber
        else -> VerifiedEmerald
    }
    val firestoreToDriveLinkColor = when (driveSyncState.phase) {
        DriveSyncPhase.IN_SYNC -> VerifiedEmerald
        DriveSyncPhase.SYNCING -> MaterialTheme.colorScheme.primary
        DriveSyncPhase.LOCAL_AHEAD, DriveSyncPhase.OFFLINE -> BridgeAmber
        DriveSyncPhase.ERROR -> MaterialTheme.colorScheme.error
        DriveSyncPhase.NOT_LINKED -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("header_tri_sync_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StorageSyncNodePill(
            layerName = stringResource(R.string.tri_sync_room_label),
            statusText = roomStatusText,
            icon = Icons.Outlined.Storage,
            accent = roomAccent,
            isSpinning = roomSyncing,
            progressFraction = null,
            isPulsing = !roomSyncing && !roomHasQueued && !telemetry.hasActiveConflict,
            pulseAlpha = pulseAlpha,
            muted = false,
            accessibilityDesc = "Room $roomStatusText. ${stringResource(R.string.cd_header_room_status)}",
            testTag = "header_room_node",
            onClick = onClickRoom,
            modifier = Modifier.weight(1f)
        )

        SyncBridgeConnector(
            color = roomToFirestoreLinkColor,
            isActive = roomSyncing || firestoreSyncing,
            pulseAlpha = pulseAlpha
        )

        StorageSyncNodePill(
            layerName = stringResource(R.string.tri_sync_firestore_label),
            statusText = firestoreStatusText,
            icon = when {
                telemetry.hasActiveConflict -> Icons.Outlined.CompareArrows
                !telemetry.isOnline || telemetry.firestoreStreamError -> Icons.Outlined.CloudOff
                else -> Icons.Outlined.CloudDone
            },
            accent = firestoreAccent,
            isSpinning = firestoreSyncing,
            progressFraction = null,
            isPulsing = telemetry.connectionState == SyncConnectionState.ONLINE && !telemetry.hasActiveConflict,
            pulseAlpha = pulseAlpha,
            muted = false,
            accessibilityDesc = "Firestore $firestoreStatusText. ${stringResource(R.string.cd_header_firestore_status)}",
            testTag = "header_firestore_node",
            onClick = onClickFirestore,
            modifier = Modifier.weight(1.15f)
        )

        SyncBridgeConnector(
            color = firestoreToDriveLinkColor,
            isActive = driveSyncState.isActive,
            pulseAlpha = pulseAlpha
        )

        StorageSyncNodePill(
            layerName = stringResource(R.string.tri_sync_drive_label),
            statusText = driveStatusText,
            icon = when (driveSyncState.phase) {
                DriveSyncPhase.ERROR -> Icons.Outlined.ErrorOutline
                DriveSyncPhase.LOCAL_AHEAD -> Icons.Outlined.CloudUpload
                DriveSyncPhase.OFFLINE -> Icons.Outlined.CloudOff
                else -> Icons.Outlined.AddToDrive
            },
            accent = driveAccent,
            isSpinning = driveSyncState.isActive,
            progressFraction = driveSyncState.progressFraction,
            isPulsing = driveSyncState.phase == DriveSyncPhase.IN_SYNC || driveSyncState.phase == DriveSyncPhase.LOCAL_AHEAD,
            pulseAlpha = pulseAlpha,
            muted = driveSyncState.phase == DriveSyncPhase.NOT_LINKED,
            accessibilityDesc = "Google Drive $driveStatusText. ${stringResource(R.string.cd_header_drive_status)}",
            testTag = "header_drive_node",
            onClick = onClickDrive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StorageSyncNodePill(
    layerName: String,
    statusText: String,
    icon: ImageVector,
    accent: Color,
    isSpinning: Boolean,
    progressFraction: Float?,
    isPulsing: Boolean,
    pulseAlpha: Float,
    muted: Boolean,
    accessibilityDesc: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = (progressFraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "node_progress_$layerName"
    )

    // 48.dp minimum height touch target for Android Accessibility while keeping the pill slim visually
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = accessibilityDesc }
            .testTag(testTag)
    ) {
        Surface(
            color = accent.copy(alpha = if (muted) 0.06f else 0.11f),
            border = BorderStroke(1.dp, accent.copy(alpha = if (muted) 0.28f else 0.45f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isSpinning) {
                    if (progressFraction != null) {
                        CircularProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp,
                            color = accent,
                            trackColor = accent.copy(alpha = 0.22f),
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp,
                            color = accent
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .alpha(if (isPulsing) pulseAlpha else 1f)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            text = layerName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.5.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBridgeConnector(
    color: Color,
    isActive: Boolean,
    pulseAlpha: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.alpha(if (isActive) pulseAlpha else 0.85f)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(1.5.dp)
                .background(color.copy(alpha = 0.65f), RoundedCornerShape(1.dp))
        )
        Text(
            text = "↔",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(1.5.dp)
                .background(color.copy(alpha = 0.65f), RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun GitHubHeaderSyncIndicator(
    state: GitHubHeaderSyncState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetAccent = when (state.phase) {
        GitHubSyncPhase.IN_SYNC -> VerifiedEmerald
        GitHubSyncPhase.LOCAL_AHEAD -> BridgeAmber
        GitHubSyncPhase.PREPARING,
        GitHubSyncPhase.DIFFING,
        GitHubSyncPhase.TRANSFERRING,
        GitHubSyncPhase.COMMITTING -> MaterialTheme.colorScheme.primary
        GitHubSyncPhase.ERROR -> MaterialTheme.colorScheme.error
        GitHubSyncPhase.UNLINKED -> MaterialTheme.colorScheme.secondary
        GitHubSyncPhase.NOT_CONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 260),
        label = "github_header_accent"
    )

    val pulseTransition = rememberInfiniteTransition(label = "github_ahead_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "github_dot_alpha"
    )

    val animatedFraction by animateFloatAsState(
        targetValue = (state.progressFraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "github_ring_progress"
    )

    val labelText = when {
        state.isActive -> {
            val arrow = if (state.direction == GitHubSyncDirection.PULL) "↓" else "↑"
            val pct = state.progressFraction?.let { "${(it.coerceIn(0f, 1f) * 100f).roundToInt()}%" }
            if (pct != null) "GH $arrow $pct" else "GH $arrow"
        }
        state.phase == GitHubSyncPhase.IN_SYNC -> stringResource(R.string.github_header_synced)
        state.phase == GitHubSyncPhase.LOCAL_AHEAD -> stringResource(R.string.github_header_ahead)
        state.phase == GitHubSyncPhase.UNLINKED -> stringResource(R.string.github_header_auto)
        state.phase == GitHubSyncPhase.ERROR -> stringResource(R.string.github_header_error)
        else -> stringResource(R.string.github_header_connect)
    }

    val cdSuffix = stringResource(R.string.cd_header_github_sync)
    val accessibilityDesc = buildString {
        append(labelText)
        if (state.linkedRepoSlug.isNotBlank()) append(" (${state.linkedRepoSlug})")
        if (state.isActive && state.currentStageLabel.isNotBlank()) append(". ${state.currentStageLabel}")
        append(". $cdSuffix")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = accessibilityDesc }
            .testTag("header_github_sync_indicator")
    ) {
        Surface(
            color = accent.copy(alpha = if (state.phase == GitHubSyncPhase.NOT_CONNECTED) 0.08f else 0.14f),
            border = BorderStroke(
                1.dp,
                accent.copy(alpha = if (state.phase == GitHubSyncPhase.NOT_CONNECTED) 0.35f else 0.6f)
            ),
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                when {
                    state.isActive -> {
                        if (state.progressFraction != null) {
                            CircularProgressIndicator(
                                progress = { animatedFraction },
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = accent,
                                trackColor = accent.copy(alpha = 0.22f),
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = accent
                            )
                        }
                    }
                    state.phase == GitHubSyncPhase.IN_SYNC -> {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    state.phase == GitHubSyncPhase.LOCAL_AHEAD -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    state.phase == GitHubSyncPhase.ERROR -> {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accent,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ActiveSyncProgressStrip(
    icon: ImageVector,
    stageLabel: String,
    completedFiles: Int,
    totalFiles: Int,
    progressFraction: Float?,
    accent: Color,
    testTag: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progressFraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "active_sync_strip_progress"
    )
    val pctLabel = progressFraction?.let { "${(it.coerceIn(0f, 1f) * 100f).roundToInt()}%" } ?: "..."

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stageLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (totalFiles > 0) "$completedFiles/$totalFiles · $pctLabel" else pctLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accent,
                maxLines = 1
            )
        }
        if (progressFraction != null) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun SyncStatusHeaderIndicator(
    telemetry: OfflineSyncTelemetry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = telemetry.connectionState
    val hasConflict = telemetry.hasActiveConflict
    val targetAccent = when {
        hasConflict -> BridgeAmber
        state == SyncConnectionState.ONLINE -> VerifiedEmerald
        state == SyncConnectionState.SYNCING -> MaterialTheme.colorScheme.primary
        else -> BridgeAmber
    }
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 280),
        label = "sync_header_accent"
    )

    val pulseTransition = rememberInfiniteTransition(label = "sync_dot_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sync_dot_alpha"
    )

    val labelText = when {
        hasConflict -> stringResource(R.string.sync_state_conflict)
        state == SyncConnectionState.ONLINE -> stringResource(R.string.sync_state_online)
        state == SyncConnectionState.SYNCING -> if (telemetry.pendingOpCount > 0) {
            "${stringResource(R.string.sync_state_syncing)} (${telemetry.pendingOpCount})"
        } else {
            stringResource(R.string.sync_state_syncing)
        }
        else -> if (telemetry.pendingOpCount > 0) {
            "${stringResource(R.string.sync_state_offline)} · ${telemetry.pendingOpCount}"
        } else {
            stringResource(R.string.sync_state_offline)
        }
    }

    val cdPrefix = stringResource(R.string.cd_header_sync_status)
    val accessibilityDesc = "$labelText. $cdPrefix"

    // Outer container guarantees >= 48.dp touch target height for Android Accessibility
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = accessibilityDesc }
            .testTag("header_sync_status_indicator")
    ) {
        Surface(
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                when {
                    hasConflict -> {
                        Icon(
                            imageVector = Icons.Outlined.CompareArrows,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    state == SyncConnectionState.SYNCING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                    }
                    state == SyncConnectionState.ONLINE -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Icon(
                            imageVector = Icons.Outlined.CloudDone,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accent,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SyncTelemetryInspectorDialog(
    telemetry: OfflineSyncTelemetry,
    driveSyncState: DriveHeaderSyncState,
    githubSyncState: GitHubHeaderSyncState,
    onSyncNow: () -> Unit,
    onOpenDriveConsole: () -> Unit,
    onOpenGitHubConsole: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = telemetry.connectionState
    val stateAccent = when (state) {
        SyncConnectionState.ONLINE -> VerifiedEmerald
        SyncConnectionState.SYNCING -> MaterialTheme.colorScheme.primary
        SyncConnectionState.OFFLINE -> BridgeAmber
    }
    val stateTitle = when (state) {
        SyncConnectionState.ONLINE -> "Online · Cloud & Room In Sync"
        SyncConnectionState.SYNCING -> "Syncing · Updating Cloud & Room"
        SyncConnectionState.OFFLINE -> "Offline · Serving Local Room Cache"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.sync_inspector_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = stateAccent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, stateAccent.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stateTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = stateAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                SyncTelemetryRow(
                    icon = if (telemetry.isOnline) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                    label = "Device Network",
                    value = if (telemetry.isOnline) "Connected (Validated)" else "Offline",
                    accent = if (telemetry.isOnline) VerifiedEmerald else BridgeAmber
                )

                val roomValue = buildString {
                    append("${telemetry.cachedProjectCount} cached repo(s)")
                    if (telemetry.pendingOpCount > 0) {
                        append(" · ${telemetry.pendingOpCount} queued")
                    } else {
                        append(" · 0 pending")
                    }
                }
                SyncTelemetryRow(
                    icon = Icons.Outlined.Storage,
                    label = "Room Local Cache",
                    value = roomValue,
                    accent = if (telemetry.pendingOpCount > 0) BridgeAmber else VerifiedEmerald
                )

                val firestoreStatus = when {
                    telemetry.firestoreStreamError || !telemetry.isOnline -> "Disconnected (Fallback to Room)"
                    telemetry.firestoreHasPendingWrites -> "Writing changes to Firestore..."
                    telemetry.isHydratingFromFirestore -> "Hydrating live snapshot..."
                    telemetry.firestoreFromCache -> "Reading local Firestore cache"
                    else -> "Live Server Stream Active"
                }
                SyncTelemetryRow(
                    icon = if (telemetry.firestoreFromCache || !telemetry.isOnline) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                    label = "Cloud Firestore",
                    value = firestoreStatus,
                    accent = when {
                        !telemetry.isOnline || telemetry.firestoreFromCache -> BridgeAmber
                        telemetry.firestoreHasPendingWrites || telemetry.isHydratingFromFirestore -> MaterialTheme.colorScheme.primary
                        else -> VerifiedEmerald
                    }
                )

                val driveValue = when (driveSyncState.phase) {
                    DriveSyncPhase.SYNCING -> driveSyncState.currentStageLabel.ifBlank { "Mirroring with Google Drive..." }
                    DriveSyncPhase.IN_SYNC -> if (driveSyncState.lastSyncSummary.isNotBlank()) {
                        "In Sync · ${driveSyncState.lastSyncSummary}"
                    } else {
                        "In Sync with Drive template folders"
                    }
                    DriveSyncPhase.LOCAL_AHEAD -> "Local workspace ahead · Tap Drive to push"
                    DriveSyncPhase.OFFLINE -> "Paused while offline"
                    DriveSyncPhase.ERROR -> driveSyncState.errorMessage?.take(90) ?: "Last Drive sync failed"
                    DriveSyncPhase.NOT_LINKED -> "Not yet mirrored to Google Drive"
                }
                val driveAccent = when (driveSyncState.phase) {
                    DriveSyncPhase.IN_SYNC -> VerifiedEmerald
                    DriveSyncPhase.SYNCING -> MaterialTheme.colorScheme.primary
                    DriveSyncPhase.LOCAL_AHEAD, DriveSyncPhase.OFFLINE -> BridgeAmber
                    DriveSyncPhase.ERROR -> MaterialTheme.colorScheme.error
                    DriveSyncPhase.NOT_LINKED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                SyncTelemetryRow(
                    icon = Icons.Outlined.AddToDrive,
                    label = "Google Drive Mirror",
                    value = driveValue,
                    accent = driveAccent
                )

                val githubValue = when {
                    githubSyncState.isActive -> githubSyncState.currentStageLabel.ifBlank { "Mirroring with GitHub..." }
                    githubSyncState.phase == GitHubSyncPhase.IN_SYNC ->
                        "${githubSyncState.linkedRepoSlug} · In Sync"
                    githubSyncState.phase == GitHubSyncPhase.LOCAL_AHEAD ->
                        "${githubSyncState.linkedRepoSlug.ifBlank { "Linked repo" }} · Local changes pending push"
                    githubSyncState.phase == GitHubSyncPhase.UNLINKED ->
                        "Connected · Auto-provisions repo on mirror"
                    githubSyncState.phase == GitHubSyncPhase.ERROR ->
                        githubSyncState.errorMessage?.take(90) ?: "Last GitHub mirror failed"
                    else -> "Not connected"
                }
                val githubAccent = when (githubSyncState.phase) {
                    GitHubSyncPhase.IN_SYNC -> VerifiedEmerald
                    GitHubSyncPhase.LOCAL_AHEAD -> BridgeAmber
                    GitHubSyncPhase.ERROR -> MaterialTheme.colorScheme.error
                    GitHubSyncPhase.PREPARING,
                    GitHubSyncPhase.DIFFING,
                    GitHubSyncPhase.TRANSFERRING,
                    GitHubSyncPhase.COMMITTING -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                SyncTelemetryRow(
                    icon = Icons.Outlined.Code,
                    label = if (githubSyncState.autoMirrorEnabled) "GitHub Remote (Auto-Mirror ON)" else "GitHub Remote",
                    value = githubValue,
                    accent = githubAccent
                )

                if (telemetry.lastSyncedLabel.isNotBlank()) {
                    Text(
                        text = "Last cloud confirmation: ${telemetry.lastSyncedLabel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                telemetry.lastSyncError?.takeIf { it.isNotBlank() }?.let { err ->
                    Text(
                        text = "Last retry note: $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = BridgeAmber
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSyncNow,
                enabled = telemetry.isOnline && !telemetry.isSyncing,
                modifier = Modifier.testTag("inspector_sync_now_button")
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.offline_sync_now))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = onOpenDriveConsole,
                    modifier = Modifier.testTag("inspector_open_drive_button")
                ) {
                    Text("Drive")
                }
                TextButton(
                    onClick = onOpenGitHubConsole,
                    modifier = Modifier.testTag("inspector_open_github_button")
                ) {
                    Text("GitHub")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun SyncTelemetryRow(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
