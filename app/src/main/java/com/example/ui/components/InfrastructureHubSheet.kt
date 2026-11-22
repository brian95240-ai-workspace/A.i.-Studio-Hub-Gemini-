package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.infra.ConnectorKind
import com.example.data.infra.ConnectorStatus
import com.example.data.infra.ConnectorVisibility
import com.example.data.infra.InfraConnectorProfile
import com.example.data.infra.InfrastructureRegistryEngine
import com.example.data.infra.InfrastructureRegistryState
import com.example.data.infra.TimeSyncContextEngine
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InfrastructureHubSheet(
    state: InfrastructureRegistryState,
    envSecretCount: Int,
    onDismiss: () -> Unit,
    onUpdateConnector: (InfraConnectorProfile) -> Unit,
    onAddCustomConnector: (name: String, kind: ConnectorKind, endpointUrl: String, visibility: ConnectorVisibility, secretRef: String) -> Unit,
    onRemoveCustomConnector: (String) -> Unit,
    onProbeConnector: (String) -> Unit,
    onUpdateGatewayAndTime: (gatewayBaseUrl: String, timezoneId: String, attachTimeContext: Boolean) -> Unit,
    onOpenEnvVault: () -> Unit,
    onToast: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedConnectorId by remember { mutableStateOf<String?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("infra_hub_sheet"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Hub,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.infra_hub_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.privateSoloCount} Solo · ${state.sandboxedReadOnlyCount} Sandboxed RO · ${state.onlineCount}/${state.connectors.size} Online",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("infra_hub_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close)
                        )
                    }
                }

                // 3-Tab Studio Selector
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; showAddForm = false },
                        text = { Text("DB & Storage (${state.storageConnectors.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("infra_tab_storage")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; showAddForm = false },
                        text = { Text("MCP (${state.mcpConnectors.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("infra_tab_mcp")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; showAddForm = false },
                        text = { Text("Time & Privacy", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("infra_tab_privacy")
                    )
                }

                when (selectedTab) {
                    0 -> ConnectorListPane(
                        connectors = state.storageConnectors,
                        gatewayBaseUrl = state.gatewayBaseUrl,
                        bannerTitle = "Universal BYO Storage & Databases (Standby-First)",
                        bannerSubtitle = "Connect Neon/PostgreSQL (PostGIS + AGE), S3/R2 Object Storage, LanceDB, or any custom database. Personal endpoints stay device-local.",
                        addCtaLabel = "+ Add Custom Database / Storage",
                        defaultCustomKind = ConnectorKind.CUSTOM_DB,
                        expandedId = expandedConnectorId,
                        showAddForm = showAddForm,
                        onToggleExpand = { id -> expandedConnectorId = if (expandedConnectorId == id) null else id },
                        onToggleAddForm = { showAddForm = !showAddForm },
                        onUpdateConnector = onUpdateConnector,
                        onAddCustomConnector = { name, kind, url, vis, secret ->
                            onAddCustomConnector(name, kind, url, vis, secret)
                            showAddForm = false
                        },
                        onRemoveCustomConnector = onRemoveCustomConnector,
                        onProbeConnector = onProbeConnector,
                        onOpenEnvVault = onOpenEnvVault
                    )

                    1 -> ConnectorListPane(
                        connectors = state.mcpConnectors,
                        gatewayBaseUrl = state.gatewayBaseUrl,
                        bannerTitle = "Universal MCP Server Templates (9 Canonical + Custom)",
                        bannerSubtitle = "Standby contracts for Temporal, Letta, Memgraph (with Sandboxed Read-Only Fork preset), Time Sync, Vertex Solver, Playwright → Browser Use fallback, VPS Launcher & World Mechanic.",
                        addCtaLabel = "+ Add Custom MCP Server",
                        defaultCustomKind = ConnectorKind.CUSTOM_MCP,
                        expandedId = expandedConnectorId,
                        showAddForm = showAddForm,
                        onToggleExpand = { id -> expandedConnectorId = if (expandedConnectorId == id) null else id },
                        onToggleAddForm = { showAddForm = !showAddForm },
                        onUpdateConnector = onUpdateConnector,
                        onAddCustomConnector = { name, kind, url, vis, secret ->
                            onAddCustomConnector(name, kind, url, vis, secret)
                            showAddForm = false
                        },
                        onRemoveCustomConnector = onRemoveCustomConnector,
                        onProbeConnector = onProbeConnector,
                        onOpenEnvVault = onOpenEnvVault
                    )

                    2 -> TimeSyncAndPrivacyPane(
                        state = state,
                        envSecretCount = envSecretCount,
                        onUpdateGatewayAndTime = onUpdateGatewayAndTime,
                        onCopyText = { label, text ->
                            clipboard.setText(AnnotatedString(text))
                            onToast("Copied $label to clipboard.")
                        },
                        onOpenEnvVault = onOpenEnvVault
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectorListPane(
    connectors: List<InfraConnectorProfile>,
    gatewayBaseUrl: String,
    bannerTitle: String,
    bannerSubtitle: String,
    addCtaLabel: String,
    defaultCustomKind: ConnectorKind,
    expandedId: String?,
    showAddForm: Boolean,
    onToggleExpand: (String) -> Unit,
    onToggleAddForm: () -> Unit,
    onUpdateConnector: (InfraConnectorProfile) -> Unit,
    onAddCustomConnector: (String, ConnectorKind, String, ConnectorVisibility, String) -> Unit,
    onRemoveCustomConnector: (String) -> Unit,
    onProbeConnector: (String) -> Unit,
    onOpenEnvVault: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header_banner") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bannerTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan
                    )
                    Text(
                        text = bannerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleAddForm,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("infra_add_custom_toggle")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (showAddForm) "Cancel" else addCtaLabel)
                        }
                        TextButton(
                            onClick = onOpenEnvVault,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("infra_open_vault_btn")
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(".env Vault")
                        }
                    }
                }
            }
        }

        item(key = "add_custom_form") {
            AnimatedVisibility(visible = showAddForm) {
                AddCustomConnectorInlineCard(
                    defaultKind = defaultCustomKind,
                    onSubmit = onAddCustomConnector
                )
            }
        }

        items(connectors, key = { it.id }) { connector ->
            ConnectorProfileCard(
                profile = connector,
                gatewayBaseUrl = gatewayBaseUrl,
                expanded = expandedId == connector.id,
                onToggleExpand = { onToggleExpand(connector.id) },
                onUpdate = onUpdateConnector,
                onRemoveCustom = if (connector.isCustom) {
                    { onRemoveCustomConnector(connector.id) }
                } else null,
                onProbe = { onProbeConnector(connector.id) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectorProfileCard(
    profile: InfraConnectorProfile,
    gatewayBaseUrl: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdate: (InfraConnectorProfile) -> Unit,
    onRemoveCustom: (() -> Unit)?,
    onProbe: () -> Unit
) {
    var endpointDraft by remember(profile.id, profile.endpointUrl) { mutableStateOf(profile.endpointUrl) }
    var gatewayPathDraft by remember(profile.id, profile.gatewayPath) { mutableStateOf(profile.gatewayPath) }
    var vaultKeyDraft by remember(profile.id, profile.vaultSecretKeyRef) { mutableStateOf(profile.vaultSecretKeyRef) }

    val statusColor = when (profile.status) {
        ConnectorStatus.ONLINE -> VerifiedEmerald
        ConnectorStatus.CONFIGURED -> ElectricCyan
        ConnectorStatus.PROBING -> TelemetryBlue
        ConnectorStatus.STANDBY -> BridgeAmber
        ConnectorStatus.UNREACHABLE -> BridgeAmber
    }
    val visibilityColor = when (profile.visibility) {
        ConnectorVisibility.PRIVATE_SOLO -> ElectricCyan
        ConnectorVisibility.SANDBOXED_READ_ONLY -> VerifiedEmerald
        ConnectorVisibility.PUBLIC_TEAM -> TelemetryBlue
    }
    val effectiveCaps = remember(profile) { InfrastructureRegistryEngine.effectiveCapabilities(profile) }
    val blockedCaps = remember(profile) { InfrastructureRegistryEngine.blockedCapabilities(profile) }
    val resolvedTarget = remember(profile, gatewayBaseUrl) { profile.resolvedTargetUrl(gatewayBaseUrl) }
    val (urlValid, urlValidationMsg) = remember(endpointDraft) {
        InfrastructureRegistryEngine.validateRemoteEndpointUrl(endpointDraft)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) visibilityColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("infra_card_${profile.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (profile.kind.isStorageTier) Icons.Outlined.Storage else Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = visibilityColor,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = profile.providerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(48.dp).testTag("infra_expand_${profile.id}")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(R.string.cd_expand_stage)
                    )
                }
            }

            // Status, Visibility, Read-Only & Fallback Telemetry Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfraBadgeChip(label = profile.status.label.uppercase(), accent = statusColor)
                InfraBadgeChip(label = profile.visibility.shortBadge, accent = visibilityColor)
                if (profile.isEffectivelyReadOnly) {
                    InfraBadgeChip(label = "READ-ONLY GUARD", accent = VerifiedEmerald)
                }
                if (profile.fallbackAfterId == "mcp_playwright") {
                    InfraBadgeChip(label = "FALLBACK AFTER PLAYWRIGHT", accent = TelemetryBlue)
                }
            }

            Text(
                text = profile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1-Tap Sandboxed Read-Only Fork preset for Memgraph MCP
            if (profile.id == "mcp_memgraph" && profile.visibility != ConnectorVisibility.SANDBOXED_READ_ONLY) {
                OutlinedButton(
                    onClick = {
                        onUpdate(
                            profile.copy(
                                visibility = ConnectorVisibility.SANDBOXED_READ_ONLY,
                                readOnlyEnforced = true,
                                lastProbeMessage = "Sandboxed Read-Only Fork enabled — write/delete Cypher tools stripped."
                            )
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("memgraph_sandbox_preset_btn")
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = VerifiedEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply Sandboxed Read-Only Fork Preset", color = VerifiedEmerald)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Visibility Scope Selector
                    Text(
                        text = "Visibility & Privacy Isolation Scope",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConnectorVisibility.entries.forEach { scope ->
                            FilterChip(
                                selected = profile.visibility == scope,
                                onClick = {
                                    onUpdate(
                                        profile.copy(
                                            visibility = scope,
                                            readOnlyEnforced = if (scope == ConnectorVisibility.SANDBOXED_READ_ONLY) true else profile.readOnlyEnforced
                                        )
                                    )
                                },
                                label = { Text(scope.shortBadge, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("infra_vis_${profile.id}_${scope.wire}")
                            )
                        }
                    }

                    // Read-Only Sandbox Guard Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Read-Only Sandbox Guard",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (profile.visibility == ConnectorVisibility.SANDBOXED_READ_ONLY) {
                                    "Locked ON for Sandboxed Read-Only visibility."
                                } else {
                                    "Strips write/create/merge/delete/launch capabilities client-side."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.isEffectivelyReadOnly,
                            enabled = profile.visibility != ConnectorVisibility.SANDBOXED_READ_ONLY,
                            onCheckedChange = { checked ->
                                onUpdate(profile.copy(readOnlyEnforced = checked))
                            },
                            modifier = Modifier.testTag("infra_ro_switch_${profile.id}")
                        )
                    }

                    // Gateway Proxy vs Direct HTTPS toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Route via Unified Gateway",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (profile.useGatewayProxy) {
                                    "Appends gateway path to Tab 3 Gateway Base URL."
                                } else {
                                    "Connect directly to this endpoint's own HTTPS URL."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.useGatewayProxy,
                            onCheckedChange = { checked ->
                                onUpdate(profile.copy(useGatewayProxy = checked))
                            }
                        )
                    }

                    if (profile.useGatewayProxy) {
                        OutlinedTextField(
                            value = gatewayPathDraft,
                            onValueChange = { gatewayPathDraft = it },
                            label = { Text("Gateway Route Path") },
                            placeholder = { Text("/mcp/service-name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = endpointDraft,
                            onValueChange = { endpointDraft = it },
                            label = { Text("Direct HTTPS Endpoint URL (Optional in Standby)") },
                            placeholder = { Text("https://your-endpoint.example.com") },
                            isError = !urlValid,
                            supportingText = {
                                Text(
                                    text = urlValidationMsg,
                                    color = if (urlValid) MaterialTheme.colorScheme.onSurfaceVariant else CrimsonError
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = vaultKeyDraft,
                        onValueChange = { vaultKeyDraft = it.uppercase() },
                        label = { Text("Encrypted .env Vault Secret Reference") },
                        placeholder = { Text("MCP_AUTH_TOKEN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resolvedTarget.isNotBlank()) {
                        Text(
                            text = "Resolved Target: $resolvedTarget",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = ElectricCyan
                        )
                    }

                    // Capability Manifest
                    if (effectiveCaps.isNotEmpty()) {
                        Text(
                            text = "Allowed Capabilities: ${effectiveCaps.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = VerifiedEmerald
                        )
                    }
                    if (blockedCaps.isNotEmpty()) {
                        Text(
                            text = "Blocked (Read-Only Guard): ${blockedCaps.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = CrimsonError
                        )
                    }

                    Text(
                        text = "Last Diagnostic: ${profile.lastProbeMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val nextStatus = if (endpointDraft.isNotBlank() || (profile.useGatewayProxy && gatewayBaseUrl.isNotBlank())) {
                                    ConnectorStatus.CONFIGURED
                                } else {
                                    ConnectorStatus.STANDBY
                                }
                                onUpdate(
                                    profile.copy(
                                        endpointUrl = endpointDraft.trim(),
                                        gatewayPath = gatewayPathDraft.trim(),
                                        vaultSecretKeyRef = vaultKeyDraft.trim(),
                                        status = nextStatus
                                    )
                                )
                            },
                            enabled = profile.useGatewayProxy || urlValid,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("infra_save_${profile.id}")
                        ) {
                            Text("Save Local")
                        }
                        OutlinedButton(
                            onClick = onProbe,
                            enabled = profile.status != ConnectorStatus.PROBING,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("infra_probe_${profile.id}")
                        ) {
                            if (profile.status == ConnectorStatus.PROBING) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Test Endpoint")
                        }
                        if (onRemoveCustom != null) {
                            IconButton(
                                onClick = onRemoveCustom,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_repo_file),
                                    tint = CrimsonError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomConnectorInlineCard(
    defaultKind: ConnectorKind,
    onSubmit: (String, ConnectorKind, String, ConnectorVisibility, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var secretRef by remember { mutableStateOf(if (defaultKind.isStorageTier) "CUSTOM_DB_URL" else "MCP_AUTH_TOKEN") }
    var visibility by remember { mutableStateOf(ConnectorVisibility.PRIVATE_SOLO) }
    val (validUrl, urlMsg) = remember(url) { InfrastructureRegistryEngine.validateRemoteEndpointUrl(url) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("infra_custom_form_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (defaultKind.isStorageTier) "Register Custom Database / Storage" else "Register Custom MCP Server",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Connector Name") },
                placeholder = { Text(if (defaultKind.isStorageTier) "e.g., Supabase PostGIS or Qdrant" else "e.g., Custom Knowledge MCP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("HTTPS Endpoint URL (Optional while Standby)") },
                placeholder = { Text("https://api.example.com/mcp") },
                isError = !validUrl,
                supportingText = { Text(urlMsg) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secretRef,
                onValueChange = { secretRef = it.uppercase() },
                label = { Text("Vault Secret Key Reference") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectorVisibility.entries.forEach { scope ->
                    FilterChip(
                        selected = visibility == scope,
                        onClick = { visibility = scope },
                        label = { Text(scope.shortBadge, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Button(
                onClick = { onSubmit(name.trim(), defaultKind, url.trim(), visibility, secretRef.trim()) },
                enabled = name.isNotBlank() && validUrl,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("infra_custom_submit_btn")
            ) {
                Text("Add Connector (Device Local)")
            }
        }
    }
}

@Composable
private fun TimeSyncAndPrivacyPane(
    state: InfrastructureRegistryState,
    envSecretCount: Int,
    onUpdateGatewayAndTime: (String, String, Boolean) -> Unit,
    onCopyText: (String, String) -> Unit,
    onOpenEnvVault: () -> Unit
) {
    var gatewayDraft by remember(state.gatewayBaseUrl) { mutableStateOf(state.gatewayBaseUrl) }
    var tzDraft by remember(state.localTimezoneId) { mutableStateOf(state.localTimezoneId) }
    var attachDraft by remember(state.attachTimeContextToAgents) { mutableStateOf(state.attachTimeContextToAgents) }
    val (gatewayValid, gatewayMsg) = remember(gatewayDraft) {
        InfrastructureRegistryEngine.validateRemoteEndpointUrl(gatewayDraft)
    }
    val remoteTimeOnline = state.connectors.any { it.id == "mcp_time_sync" && it.status == ConnectorStatus.ONLINE }
    val envelope = remember(tzDraft, remoteTimeOnline) {
        TimeSyncContextEngine.currentEnvelope(tzDraft, remoteTimeOnline)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live UTC <-> Local Time Sync Card
        item(key = "time_sync") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = VerifiedEmerald)
                        Text(
                            text = "Time & Date Sync (UTC ↔ Local Agent Clock)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Source: ${envelope.sourceLabel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = VerifiedEmerald
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "UTC:   ${envelope.utcIso}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = ElectricCyan
                            )
                            Text(
                                text = "Local: ${envelope.localIso} (${envelope.timezoneId})",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = VerifiedEmerald
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TimeSyncContextEngine.commonTimezoneIds) { tz ->
                            FilterChip(
                                selected = tzDraft == tz,
                                onClick = { tzDraft = tz },
                                label = { Text(tz, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Inject UTC ↔ Local header into 7-stage agent prompts",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = attachDraft,
                            onCheckedChange = { attachDraft = it },
                            modifier = Modifier.testTag("infra_time_attach_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onCopyText(
                                "Temporal Sync Envelope",
                                TimeSyncContextEngine.buildAgentTemporalHeader(state.copy(localTimezoneId = tzDraft))
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Agent Temporal Header")
                    }
                }
            }
        }

        // 2. Unified Optional Gateway Base URL
        item(key = "gateway") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, TelemetryBlue.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Unified MCP / DB Gateway Base URL (Optional)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Supply your own reverse proxy or VPS gateway URL (e.g. Contabo, Fly, Cloudflare Tunnel) to route proxied connectors through one HTTPS origin. Stored strictly on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = gatewayDraft,
                        onValueChange = { gatewayDraft = it },
                        label = { Text("Gateway Base URL") },
                        placeholder = { Text("https://gateway.your-domain.example") },
                        isError = !gatewayValid,
                        supportingText = { Text(gatewayMsg) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onUpdateGatewayAndTime(gatewayDraft.trim(), tzDraft, attachDraft) },
                        enabled = gatewayValid,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("infra_save_gateway_time_btn")
                    ) {
                        Text("Save Gateway & Time Sync Settings")
                    }
                }
            }
        }

        // 3. Zero-Leakage Privacy Isolation Audit
        item(key = "privacy_audit") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = ElectricCyan)
                        Text(
                            text = "Zero-Leakage Multi-User Isolation Audit",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = "• ${state.privateSoloCount} PRIVATE SOLO connector(s): Strictly isolated in device-local DataStore. Excluded from Firestore team docs, public shares, and GitHub pushes.\n" +
                            "• ${state.sandboxedReadOnlyCount} SANDBOXED READ-ONLY connector(s): Shareable collaborative fork with client-side mutation blocking.\n" +
                            "• ${state.publicTeamCount} PUBLIC TEAM connector(s): Non-secret descriptors shareable; credentials remain local.\n" +
                            "• $envSecretCount secret(s) locked in Android Keystore AES-256-GCM .env Vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val manifest = InfrastructureRegistryEngine.exportShareableDescriptorsJson(state)
                                onCopyText("Zero-Leakage Shareable Manifest", manifest)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("infra_copy_safe_manifest_btn")
                        ) {
                            Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Safe Manifest", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenEnvVault,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VerifiedEmerald),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open .env Vault", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfraBadgeChip(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
