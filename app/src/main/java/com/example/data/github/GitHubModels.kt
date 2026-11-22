package com.example.data.github

enum class GitHubAuthMethod(val wire: String, val label: String) {
    NONE("NONE", "Not Connected"),
    PERSONAL_ACCESS_TOKEN("PAT", "Personal Access Token"),
    OAUTH_DEVICE_FLOW("OAUTH_DEVICE", "GitHub OAuth Device");

    companion object {
        fun fromWire(value: String?): GitHubAuthMethod =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

data class GitHubUserProfile(
    val login: String,
    val displayName: String,
    val profileUrl: String,
    val authMethod: GitHubAuthMethod
)

data class GitHubRepoSummary(
    val fullName: String,
    val owner: String,
    val name: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val updatedAt: String
)

data class GitHubDeviceCodeSession(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

sealed interface GitHubDevicePollOutcome {
    data class Authorized(val accessToken: String) : GitHubDevicePollOutcome
    data class Pending(val nextIntervalSeconds: Int) : GitHubDevicePollOutcome
    data class Failed(val message: String) : GitHubDevicePollOutcome
}

data class GitHubMirrorReport(
    val repoUrl: String,
    val owner: String,
    val repoName: String,
    val branch: String,
    val commitSha: String,
    val uploadedCount: Int,
    val updatedCount: Int,
    val skippedUnchangedCount: Int,
    val workspaceSha256: String,
    val autoCreatedRepo: Boolean
) {
    val totalMirroredFiles: Int get() = uploadedCount + updatedCount
    val summaryLabel: String
        get() = "+$uploadedCount new · $updatedCount updated · $skippedUnchangedCount unchanged"
}

enum class GitHubSyncDirection {
    IDLE,
    PUSH,
    PULL
}

enum class GitHubSyncPhase {
    NOT_CONNECTED,
    UNLINKED,
    IN_SYNC,
    LOCAL_AHEAD,
    PREPARING,
    DIFFING,
    TRANSFERRING,
    COMMITTING,
    ERROR
}

data class GitHubSyncProgressUpdate(
    val phase: GitHubSyncPhase,
    val direction: GitHubSyncDirection,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val stageLabel: String = "",
    val progressFraction: Float? = null
)

data class GitHubHeaderSyncState(
    val phase: GitHubSyncPhase = GitHubSyncPhase.NOT_CONNECTED,
    val direction: GitHubSyncDirection = GitHubSyncDirection.IDLE,
    val progressFraction: Float? = null,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val currentStageLabel: String = "",
    val linkedRepoSlug: String = "",
    val branch: String = "main",
    val autoMirrorEnabled: Boolean = false,
    val lastSyncSummary: String = "",
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = phase == GitHubSyncPhase.PREPARING ||
            phase == GitHubSyncPhase.DIFFING ||
            phase == GitHubSyncPhase.TRANSFERRING ||
            phase == GitHubSyncPhase.COMMITTING
}

