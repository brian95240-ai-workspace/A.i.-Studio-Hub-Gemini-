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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.github.GitHubAuthManager
import com.example.data.github.GitHubAuthMethod
import com.example.data.github.GitHubDeviceCodeSession
import com.example.data.github.GitHubMirrorReport
import com.example.data.github.GitHubRepoSummary
import com.example.data.model.ResearchProject
import com.example.data.model.RoutingPreferences
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GitHubMirrorSheet(
    project: ResearchProject,
    routingPrefs: RoutingPreferences,
    githubSyncStatus: String,
    lastSyncLabel: String?,
    lastReport: GitHubMirrorReport?,
    userRepos: List<GitHubRepoSummary>,
    loadingRepos: Boolean,
    syncing: Boolean,
    canEdit: Boolean,
    hasBoundSafFolder: Boolean,
    deviceSession: GitHubDeviceCodeSession?,
    deviceAuthPolling: Boolean,
    onDismiss: () -> Unit,
    onVerifyPat: (String) -> Unit,
    onStartDeviceAuth: (String) -> Unit,
    onCancelDeviceAuth: () -> Unit,
    onDisconnectGitHub: () -> Unit,
    onToggleAutoMirror: (Boolean) -> Unit,
    onToggleDefaultPrivate: (Boolean) -> Unit,
    onRefreshRepos: () -> Unit,
    onLinkExistingRepo: (GitHubRepoSummary) -> Unit,
    onMirrorWorkspaceNow: () -> Unit,
    onMirrorBoundFolderNow: () -> Unit,
    onPullFromGitHub: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isConnected = routingPrefs.githubPat.isNotBlank() && routingPrefs.githubUsername.isNotBlank()
    val authMethod = GitHubAuthMethod.fromWire(routingPrefs.githubAuthMethod)

    var patInput by remember(routingPrefs.githubPat) { mutableStateOf(routingPrefs.githubPat) }
    var showDeviceOAuth by remember { mutableStateOf(deviceSession != null) }
    var oauthClientId by remember(routingPrefs.githubOAuthClientId) {
        mutableStateOf(routingPrefs.githubOAuthClientId)
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
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.github_console_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Atomic single-commit mirroring with Git SHA-1 diff skipping",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Authentication Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) VerifiedEmerald.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    1.dp,
                    if (isConnected) VerifiedEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${routingPrefs.githubUsername}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = VerifiedEmerald
                                )
                                Text(
                                    text = "Authenticated via ${authMethod.label} · Stored locally",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = onDisconnectGitHub,
                                modifier = Modifier.testTag("github_disconnect_button")
                            ) {
                                Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Disconnect", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Connect GitHub Account", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "Generate a token with pre-selected 'repo' scope in 1 tap, or authenticate via GitHub OAuth Device Flow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = patInput,
                            onValueChange = { patInput = it },
                            label = { Text("GitHub Personal Access Token") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_console_pat_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    openExternalUrl(context, GitHubAuthManager.PREFILLED_CLASSIC_TOKEN_URL)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("github_prefilled_token_button")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Get Token (Pre-filled)")
                            }

                            OutlinedButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text?.trim().orEmpty()
                                    if (clip.isNotBlank()) {
                                        patInput = clip
                                        onVerifyPat(clip)
                                    } else {
                                        onToast("Clipboard is empty. Copy your token from GitHub first.")
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("github_paste_connect_button")
                            ) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Paste & Connect")
                            }

                            Button(
                                onClick = { onVerifyPat(patInput) },
                                enabled = patInput.isNotBlank(),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("github_console_verify_button")
                            ) {
                                Text("Connect")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Optional RFC 8628 OAuth Device Flow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeviceOAuth = !showDeviceOAuth }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.github_device_login_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (showDeviceOAuth) "Hide" else "Configure",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showDeviceOAuth) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = oauthClientId,
                                    onValueChange = { oauthClientId = it },
                                    label = { Text("GitHub OAuth App Client ID (Device Flow)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("github_oauth_client_id_input")
                                )

                                if (deviceSession != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = deviceSession.userCode,
                                                    style = MaterialTheme.typography.headlineSmall.copy(
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (deviceAuthPolling) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Waiting for confirmation at ${deviceSession.verificationUri}...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        copyToClipboard(context, "GitHub Device Code", deviceSession.userCode)
                                                        openExternalUrl(context, deviceSession.verificationUri)
                                                        onToast("Copied code ${deviceSession.userCode} & opened GitHub.")
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Copy & Authorize")
                                                }
                                                OutlinedButton(onClick = onCancelDeviceAuth) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onStartDeviceAuth(oauthClientId) },
                                        enabled = oauthClientId.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("github_start_device_auth_button")
                                    ) {
                                        Text("Start GitHub OAuth Device Login")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Minimal-Intervention Automation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoMode,
                            contentDescription = null,
                            tint = if (routingPrefs.githubAutoMirror) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.github_auto_mirror_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.github_auto_mirror_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = routingPrefs.githubAutoMirror,
                            onCheckedChange = onToggleAutoMirror,
                            enabled = isConnected,
                            modifier = Modifier.testTag("github_auto_mirror_toggle")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (routingPrefs.githubDefaultPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Auto-create new repos as Private",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = routingPrefs.githubDefaultPrivate,
                            onCheckedChange = onToggleDefaultPrivate,
                            modifier = Modifier.testTag("github_private_default_toggle")
                        )
                    }
                }
            }

            // 3. Target Repository & Sync Telemetry
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Target Remote Repository", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (project.githubRepoUrl.isNotBlank()) {
                                    "${project.githubRepoUrl.removePrefix("https://")} (${project.githubBranch.ifBlank { "main" }})"
                                } else {
                                    "Auto-provision: github.com/${routingPrefs.githubUsername.ifBlank { "you" }}/${project.rootFolderName.ifBlank { "vortex-research" }}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TelemetryPill(
                            text = githubSyncStatus,
                            accentColor = when (githubSyncStatus) {
                                "In Sync" -> VerifiedEmerald
                                "Local Ahead" -> BridgeAmber
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    if (!lastSyncLabel.isNullOrBlank()) {
                        Text(
                            text = "Last mirror: $lastSyncLabel",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    lastReport?.let { rep ->
                        Surface(
                            color = VerifiedEmerald.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = buildString {
                                    append(rep.summaryLabel)
                                    if (rep.commitSha.isNotBlank()) append(" · commit ${rep.commitSha}")
                                    if (rep.autoCreatedRepo) append(" · auto-created repo")
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = VerifiedEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (project.githubRepoUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = { openExternalUrl(context, project.githubRepoUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Repo")
                            }
                        }
                        if (isConnected) {
                            OutlinedButton(
                                onClick = onRefreshRepos,
                                enabled = !loadingRepos,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("github_refresh_repos_button")
                            ) {
                                if (loadingRepos) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (userRepos.isEmpty()) "Link Existing Repo" else "Refresh Repos")
                            }
                        }
                    }

                    if (userRepos.isNotEmpty()) {
                        Text(
                            text = "Tap a repository to bind this project folder:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            userRepos.take(10).forEach { repo ->
                                val isLinked = project.githubRepoUrl.equals(repo.htmlUrl, ignoreCase = true)
                                FilterChip(
                                    selected = isLinked,
                                    onClick = { onLinkExistingRepo(repo) },
                                    label = {
                                        Text(
                                            text = repo.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Mirror Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onMirrorWorkspaceNow,
                    enabled = isConnected && !syncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("github_console_mirror_now_button")
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Mirroring Atomic Commit...")
                    } else {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.github_mirror_now))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPullFromGitHub,
                        enabled = isConnected && !syncing && canEdit && project.githubRepoUrl.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("github_console_pull_button")
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pull GitHub ↓")
                    }

                    if (hasBoundSafFolder) {
                        OutlinedButton(
                            onClick = onMirrorBoundFolderNow,
                            enabled = isConnected && !syncing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("github_mirror_saf_folder_button")
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mirror Local Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
