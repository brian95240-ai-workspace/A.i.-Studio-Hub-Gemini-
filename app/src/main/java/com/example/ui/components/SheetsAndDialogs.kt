package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ProjectInvite
import com.example.data.model.ResearchProject
import com.example.data.model.RoutingMode
import com.example.data.model.RoutingPreferences
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.model.TeamRole
import com.example.data.security.DiscreteAutoFillResolution
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelRouterAndGitHubSheet(
    currentPrefs: RoutingPreferences,
    envSecretCount: Int = 0,
    envAutoFill: DiscreteAutoFillResolution = DiscreteAutoFillResolution(),
    onOpenEnvVault: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (RoutingPreferences) -> Unit,
    onToggleStrategicRouting: (Boolean) -> Unit,
    onVerifyGitHub: (String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var strategicRoutingEnabled by remember(currentPrefs.strategicRoutingEnabled) {
        mutableStateOf(currentPrefs.strategicRoutingEnabled)
    }
    var routingMode by remember { mutableStateOf(currentPrefs.routingMode) }
    var threshold by remember { mutableIntStateOf(currentPrefs.escalationThreshold) }
    var externalEnabled by remember { mutableStateOf(currentPrefs.externalEnabled) }
    var externalBaseUrl by remember(currentPrefs.externalBaseUrl) { mutableStateOf(currentPrefs.externalBaseUrl) }
    var externalApiKey by remember(currentPrefs.externalApiKey) { mutableStateOf(currentPrefs.externalApiKey) }
    var domainMatrix by remember(currentPrefs.domainModelMatrix) { mutableStateOf(currentPrefs.domainModelMatrix) }
    var githubPat by remember(currentPrefs.githubPat) { mutableStateOf(currentPrefs.githubPat) }
    var githubUser by remember(currentPrefs.githubUsername) { mutableStateOf(currentPrefs.githubUsername) }
    var githubPrivate by remember(currentPrefs.githubDefaultPrivate) { mutableStateOf(currentPrefs.githubDefaultPrivate) }

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
            Text(
                text = "Strategic Model Router & GitHub Mirror",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Maximize Gemini 3.8 Flash while surgically escalating high-complexity STEM, systems architecture, or coding stages. Credentials stay strictly local on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Secure .env Secrets Vault & Discrete Auto-Fill Launcher Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Secure .env Secrets Vault", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "$envSecretCount key(s) encrypted locally · ${envAutoFill.activeAutoFillCount} discrete auto-fill active",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedEmerald
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenEnvVault,
                            modifier = Modifier.testTag("open_env_vault_from_settings_button")
                        ) {
                            Text("Manage .env")
                        }
                    }
                    if (!envAutoFill.externalRouterApiKeyName.isNullOrBlank() || !envAutoFill.githubPatKeyName.isNullOrBlank()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            envAutoFill.externalRouterApiKeyName?.let { k ->
                                TelemetryPill("Router Key ← .env:$k", VerifiedEmerald)
                            }
                            envAutoFill.githubPatKeyName?.let { k ->
                                TelemetryPill("GitHub PAT ← .env:$k", VerifiedEmerald)
                            }
                        }
                    }
                }
            }

            // Primary Strategic Model Routing Master Toggle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (strategicRoutingEnabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
                border = BorderStroke(
                    width = if (strategicRoutingEnabled) 1.5.dp else 1.dp,
                    color = if (strategicRoutingEnabled) VerifiedEmerald else MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strategic_routing_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = if (strategicRoutingEnabled) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.strategic_routing_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            TelemetryPill(
                                text = if (strategicRoutingEnabled) {
                                    stringResource(R.string.strategic_routing_status_on)
                                } else {
                                    stringResource(R.string.strategic_routing_status_off)
                                },
                                accentColor = if (strategicRoutingEnabled) VerifiedEmerald else BridgeAmber
                            )
                        }
                        Switch(
                            checked = strategicRoutingEnabled,
                            onCheckedChange = { enabled ->
                                strategicRoutingEnabled = enabled
                                onToggleStrategicRouting(enabled)
                            },
                            modifier = Modifier.testTag("strategic_routing_toggle")
                        )
                    }
                    Text(
                        text = if (strategicRoutingEnabled) {
                            stringResource(R.string.strategic_routing_desc_on)
                        } else {
                            stringResource(R.string.strategic_routing_desc_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = strategicRoutingEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Routing Mode
                    Text("Routing Strategy", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoutingMode.entries.forEach { mode ->
                            FilterChip(
                                selected = routingMode == mode,
                                onClick = { routingMode = mode },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("routing_mode_${mode.wire.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = routingMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Escalation Sensitivity
                    Text("Escalation Sensitivity Threshold", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8 to "Conservative (8+)", 6 to "Balanced (6+)", 4 to "Aggressive (4+)").forEach { (score, label) ->
                            FilterChip(
                                selected = threshold == score,
                                onClick = { threshold = score },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Optional External API (OpenRouter / OpenAI-compatible)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("External Domain Routing API (BYOK)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Optional OpenRouter or OpenAI-compatible router for custom domain escalation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = externalEnabled,
                            onCheckedChange = { externalEnabled = it },
                            modifier = Modifier.testTag("external_router_switch")
                        )
                    }

                    if (externalEnabled) {
                        OutlinedTextField(
                            value = externalBaseUrl,
                            onValueChange = { externalBaseUrl = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = externalApiKey,
                            onValueChange = { externalApiKey = it },
                            label = { Text("API Key (Stored Locally Only)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Domain-to-Model Matrix
                    Text("Domain-to-Model Matrix", style = MaterialTheme.typography.titleSmall)
                    domainMatrix.entries.forEach { (domain, modelId) ->
                        OutlinedTextField(
                            value = modelId,
                            onValueChange = { updated ->
                                domainMatrix = domainMatrix.toMutableMap().apply { put(domain, updated) }
                            },
                            label = { Text(domain) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // GitHub One-Tap Mirror Setup
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("GitHub Repo Mirror (1-Tap Push/Pull)", style = MaterialTheme.typography.titleSmall)
            }
            if (githubUser.isNotBlank()) {
                TelemetryPill(text = "Connected as @$githubUser", accentColor = VerifiedEmerald)
            }
            OutlinedTextField(
                value = githubPat,
                onValueChange = { githubPat = it },
                label = { Text("Fine-Grained PAT (Contents: Read & Write)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_pat_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        openExternalUrl(context, "https://github.com/settings/personal-access-tokens/new")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Token")
                }
                Button(
                    onClick = { onVerifyGitHub(githubPat) { verified -> githubUser = verified } },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("verify_github_token_button")
                ) {
                    Text("Verify Token")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Create new GitHub repos as Private by default",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = githubPrivate, onCheckedChange = { githubPrivate = it })
            }

            Button(
                onClick = {
                    val trimmedPat = githubPat.trim()
                    val resolvedAuthMethod = when {
                        trimmedPat.isBlank() -> "NONE"
                        currentPrefs.githubAuthMethod == "OAUTH_DEVICE" && trimmedPat == currentPrefs.githubPat -> "OAUTH_DEVICE"
                        else -> "PAT"
                    }
                    onSave(
                        currentPrefs.copy(
                            strategicRoutingEnabled = strategicRoutingEnabled,
                            routingMode = routingMode,
                            escalationThreshold = threshold,
                            externalEnabled = externalEnabled,
                            externalBaseUrl = externalBaseUrl,
                            externalApiKey = externalApiKey,
                            domainModelMatrix = domainMatrix,
                            githubPat = trimmedPat,
                            githubUsername = if (trimmedPat.isBlank()) "" else githubUser,
                            githubDefaultPrivate = githubPrivate,
                            githubAutoMirror = currentPrefs.githubAutoMirror,
                            githubAuthMethod = resolvedAuthMethod,
                            githubOAuthClientId = currentPrefs.githubOAuthClientId
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_router_settings_button")
            ) {
                Text("Save Settings")
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamAndPublicShareSheet(
    project: ResearchProject,
    currentUid: String,
    lastInvite: ProjectInvite?,
    onDismiss: () -> Unit,
    onCreateInvite: (TeamRole) -> Unit,
    onChangeRole: (String, TeamRole) -> Unit,
    onRemoveMember: (String) -> Unit,
    onTogglePublicShare: (Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isAdmin = project.canAdmin(currentUid)

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
            Text("Team Collaboration & Public Share", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Repository: ${project.rootFolderName} · Your Role: ${project.roleFor(currentUid).label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Sign-In-Free Public Read-Only Link
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Public Read-Only Link (No Sign-In)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Share with friends or reviewers without requiring a Google account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = project.publicShareActive,
                            enabled = isAdmin,
                            onCheckedChange = { onTogglePublicShare(it) },
                            modifier = Modifier.testTag("public_share_toggle")
                        )
                    }

                    if (project.publicShareActive && project.publicShareCode.isNotBlank()) {
                        val shareUrl = "vortexresearcher://share/${project.publicShareCode}"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = shareUrl,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, "Public Share Link", shareUrl)
                                        onMessage("Copied sign-in-free public link.")
                                    },
                                    modifier = Modifier.testTag("copy_public_share_link")
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy public link")
                                }
                            }
                        }
                    }
                }
            }

            // Team Invite Generator (Admin only)
            if (isAdmin) {
                Text("Generate Team Invite Link (Google Sign-In)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onCreateInvite(TeamRole.VIEWER) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_viewer_invite_button")
                    ) {
                        Text("Read-Only Link")
                    }
                    Button(
                        onClick = { onCreateInvite(TeamRole.EDITOR) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_editor_invite_button")
                    ) {
                        Text("Editor Link")
                    }
                }

                if (lastInvite != null && lastInvite.projectId == project.id) {
                    val inviteUrl = "vortexresearcher://join?code=${lastInvite.inviteCode}"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Invite (${TeamRole.fromWire(lastInvite.assignedRole).label})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VerifiedEmerald
                                )
                                Text(
                                    inviteUrl,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                                )
                            }
                            IconButton(onClick = {
                                copyToClipboard(context, "Team Invite", inviteUrl)
                                onMessage("Copied team invite URL.")
                            }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy invite URL")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Members & Role Management
            Text("Project Members (${project.memberUids.size})", style = MaterialTheme.typography.titleSmall)
            project.memberUids.forEach { uid ->
                val displayName = project.memberDisplayNames[uid] ?: "Collaborator"
                val role = project.roleFor(uid)
                val isOwner = uid == project.ownerId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = if (role == TeamRole.ADMIN) BridgeAmber else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uid == currentUid) "$displayName (You)" else displayName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryPill(
                                text = if (isOwner) "Owner · Admin" else role.label,
                                accentColor = if (role == TeamRole.ADMIN) BridgeAmber else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isAdmin && !isOwner) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TeamRole.entries.forEach { r ->
                                    FilterChip(
                                        selected = role == r,
                                        onClick = { onChangeRole(uid, r) },
                                        label = { Text(r.label) }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { onRemoveMember(uid) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Remove member",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueprintGuideSheet(
    selected: ScaffoldingBlueprint,
    recommendedBlueprint: ScaffoldingBlueprint? = null,
    onSelect: (ScaffoldingBlueprint) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Architect Repo Scaffolding Blueprints", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick the folder architecture that matches your project. Every blueprint automatically includes dedicated PRD/ (with PRD_INDEX) and AI_Roundtable/ workspaces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ScaffoldingBlueprint.entries.forEach { bp ->
                val isSelected = bp == selected
                val isRecommended = bp == recommendedBlueprint
                Card(
                    onClick = {
                        onSelect(bp)
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            isRecommended -> VerifiedEmerald.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected || isRecommended) 1.5.dp else 1.dp,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isRecommended -> VerifiedEmerald
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blueprint_option_${bp.wire.lowercase()}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bp.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isRecommended) TelemetryPill("Stage 0 Best Fit", VerifiedEmerald)
                            if (isSelected) TelemetryPill("Selected")
                        }
                        Text(
                            text = "Best for: ${bp.bestFor}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = bp.plainEnglishSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = bp.exampleTree.joinToString("\n") { "├── $it" },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
