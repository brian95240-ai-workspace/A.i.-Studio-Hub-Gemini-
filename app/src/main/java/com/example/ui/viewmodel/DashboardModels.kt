package com.example.ui.viewmodel

import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoundtableStatus
import com.example.data.model.TeamRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncChannelHealth {
    SYNCED,
    SYNCING,
    AHEAD_PENDING,
    OFFLINE,
    ERROR,
    UNLINKED
}

enum class DashboardProjectFilter(val label: String) {
    ALL("All"),
    GITHUB("GitHub"),
    DRIVE("Drive"),
    NEEDS_SYNC("Needs Sync")
}

data class ProjectSyncBadge(
    val label: String,
    val detail: String,
    val health: SyncChannelHealth
)

data class DashboardProjectSummary(
    val project: ResearchProject,
    val isActive: Boolean,
    val role: TeamRole,
    val fileCount: Int,
    val prdCount: Int,
    val roundtableCount: Int,
    val chatCount: Int = 0,
    val selectableFiles: List<ProjectFile> = emptyList(),
    val githubBadge: ProjectSyncBadge,
    val driveBadge: ProjectSyncBadge,
    val localBadge: ProjectSyncBadge,
    val hasPendingSync: Boolean,
    val updatedLabel: String
)

data class RecentPrdCardItem(
    val prd: PrdDocument,
    val project: ResearchProject,
    val bumpLabel: String,
    val timestampLabel: String,
    val archiveRelativePath: String = ""
)

data class RecentRoundtableCardItem(
    val item: RoundtableItem,
    val project: ResearchProject,
    val status: RoundtableStatus,
    val timestampLabel: String,
    val archiveRelativePath: String = ""
)

data class DashboardUiState(
    val projects: List<DashboardProjectSummary> = emptyList(),
    val recentPrds: List<RecentPrdCardItem> = emptyList(),
    val recentRoundtable: List<RecentRoundtableCardItem> = emptyList(),
    val totalProjects: Int = 0,
    val githubLinkedCount: Int = 0,
    val driveLinkedCount: Int = 0,
    val localBoundCount: Int = 0,
    val pendingSyncProjectsCount: Int = 0,
    val totalPrdsCount: Int = 0,
    val totalRoundtableCount: Int = 0,
    val isLoaded: Boolean = false
)

internal fun formatDashboardTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Just now"
    val now = System.currentTimeMillis()
    val diffMs = (now - millis).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L
    return when {
        mins < 1L -> "Just now"
        mins < 60L -> "${mins}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
    }
}

fun formatCompactByteSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
}
