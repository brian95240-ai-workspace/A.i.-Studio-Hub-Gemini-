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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
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
import com.example.data.infra.McpAuditLogEntry
import com.example.data.infra.McpCallPolicyDecision
import com.example.data.infra.McpCallPolicyStatus
import com.example.data.infra.McpDiscoveredTool
import com.example.data.infra.McpStoredResult
import com.example.data.infra.McpToolCallOutcome
import com.example.data.infra.TimeSyncContextEngine
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.VerifiedEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ActiveToolInvokeTarget(
    val profile: InfraConnectorProfile,
    val tool: McpDiscoveredTool
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InfrastructureHubSheet(
    state: InfrastructureRegistryState,
    envSecretCount: Int,
    mcpAuditLog: List<McpAuditLogEntry> = emptyList(),
    onDismiss: () -> Unit,
    onUpdateConnector: (InfraConnectorProfile) -> Unit,
    onAddCustomConnector: (name: String, kind: ConnectorKind, endpointUrl: String, visibility: ConnectorVisibility, secretRef: String) -> Unit,
    onRemoveCustomConnector: (String) -> Unit,
    onProbeConnector: (String) -> Unit,
    onSyncMcpTools: (String) -> Unit = {},
    onEvaluateMcpCall: (String, String) -> McpCallPolicyDecision? = { _, _ -> null },
    onInvokeMcpTool: (String, String, String, Boolean, (McpToolCallOutcome) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onLookupMcpResult: (String) -> McpStoredResult? = { null },
    onClearMcpAuditLog: () -> Unit = {},
    onUpdateGatewayAndTime: (
        gatewayBaseUrl: String,
        timezoneId: String,
        attachTimeContext: Boolean,
        agentContextTokenBudget: Int,
        mcpResultCharCap: Int
    ) -> Unit,
    onOpenEnvVault: () -> Unit,
    onToast: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedConnectorId by remember { mutableStateOf<String?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var invokeTarget by remember { mutableStateOf<ActiveToolInvokeTarget?>(null) }

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
                        val authSuffix = if (state.authRequiredCount > 0) " · ${state.authRequiredCount} Auth Req" else ""
                        Text(
                            text = "${state.privateSoloCount} Solo · ${state.sandboxedReadOnlyCount} Sandbox RO · ${state.onlineCount}/${state.connectors.size} Online$authSuffix",
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
                        bannerSubtitle = "Connect Neon/PostgreSQL (PostGIS + AGE), S3/R2/MinIO Object Storage, LanceDB, or any custom HTTPS endpoint. Direct URLs are primary; no background polling.",
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
                        onSyncMcpTools = onSyncMcpTools,
                        onOpenToolInvoker = { profile, tool -> invokeTarget = ActiveToolInvokeTarget(profile, tool) },
                        onOpenEnvVault = onOpenEnvVault
                    )

                    1 -> ConnectorListPane(
                        connectors = state.mcpConnectors,
                        gatewayBaseUrl = state.gatewayBaseUrl,
                        bannerTitle = "Universal MCP Servers (Standby-First · Direct HTTPS Primary)",
                        bannerSubtitle = "Supports direct Tailscale HTTPS (https://<machine>.<tailnet>.ts.net:<port>/mcp) and Cloudflare Access sandbox URLs. Server-side controls are the real boundary; tool calls use deny-by-default exact-name allowlists from live tools/list.",
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
                        onSyncMcpTools = onSyncMcpTools,
                        onOpenToolInvoker = { profile, tool -> invokeTarget = ActiveToolInvokeTarget(profile, tool) },
                        onOpenEnvVault = onOpenEnvVault
                    )

                    2 -> TimeSyncAndPrivacyPane(
                        state = state,
                        envSecretCount = envSecretCount,
                        mcpAuditLog = mcpAuditLog,
                        onUpdateGatewayAndTime = onUpdateGatewayAndTime,
                        onClearMcpAuditLog = onClearMcpAuditLog,
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

    invokeTarget?.let { target ->
        val latestProfile = state.connectors.firstOrNull { it.id == target.profile.id } ?: target.profile
        McpToolInvokeDialog(
            profile = latestProfile,
            tool = target.tool,
            policyDecision = onEvaluateMcpCall(latestProfile.id, target.tool.name),
            onInvoke = { argsJson, confirmed, onResult ->
                onInvokeMcpTool(latestProfile.id, target.tool.name, argsJson, confirmed, onResult)
            },
            onLookupFullResult = onLookupMcpResult,
            onCopyText = { label, text ->
                clipboard.setText(AnnotatedString(text))
                onToast("Copied $label.")
            },
            onDismiss = { invokeTarget = null }
        )
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
    onSyncMcpTools: (String) -> Unit,
    onOpenToolInvoker: (InfraConnectorProfile, McpDiscoveredTool) -> Unit,
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
                onProbe = { onProbeConnector(connector.id) },
                onSyncTools = { onSyncMcpTools(connector.id) },
                onInvokeTool = { tool -> onOpenToolInvoker(connector, tool) }
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
    onProbe: () -> Unit,
    onSyncTools: () -> Unit,
    onInvokeTool: (McpDiscoveredTool) -> Unit
) {
    var endpointDraft by remember(profile.id, profile.endpointUrl) { mutableStateOf(profile.endpointUrl) }
    var gatewayPathDraft by remember(profile.id, profile.gatewayPath) { mutableStateOf(profile.gatewayPath) }
    var vaultKeyDraft by remember(profile.id, profile.vaultSecretKeyRef) { mutableStateOf(profile.vaultSecretKeyRef) }
    var fallbackTriggerDraft by remember(profile.id, profile.fallbackTrigger) { mutableStateOf(profile.fallbackTrigger) }
    var newHeaderName by remember(profile.id) { mutableStateOf("") }
    var newHeaderVaultRef by remember(profile.id) { mutableStateOf("") }

    val statusColor = when (profile.status) {
        ConnectorStatus.ONLINE -> VerifiedEmerald
        ConnectorStatus.CONFIGURED -> ElectricCyan
        ConnectorStatus.PROBING -> TelemetryBlue
        ConnectorStatus.AUTH_REQUIRED -> CrimsonError
        ConnectorStatus.STANDBY -> BridgeAmber
        ConnectorStatus.UNREACHABLE -> BridgeAmber
    }
    val visibilityColor = when (profile.visibility) {
        ConnectorVisibility.PRIVATE_SOLO -> ElectricCyan
        ConnectorVisibility.SANDBOXED_READ_ONLY -> VerifiedEmerald
        ConnectorVisibility.PUBLIC_TEAM -> TelemetryBlue
    }
    val advisoryReadCaps = remember(profile) { InfrastructureRegistryEngine.advisoryReadCapabilities(profile) }
    val advisoryMutatingCaps = remember(profile) { InfrastructureRegistryEngine.advisoryMutatingCapabilities(profile) }
    val mismatch = remember(profile.discoveredTools, profile.capabilities) { profile.toolListMismatchSummary() }
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
                    tint = if (profile.enabled) visibilityColor else MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Status, Visibility, Server-Side Read-Only, Isolation & Fallback Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfraBadgeChip(label = profile.status.label.uppercase(), accent = statusColor)
                InfraBadgeChip(label = profile.visibility.shortBadge, accent = visibilityColor)
                if (!profile.enabled) {
                    InfraBadgeChip(label = "DISABLED", accent = BridgeAmber)
                }
                if (profile.serverEnforcesReadOnly) {
                    InfraBadgeChip(label = "SERVER READ-ONLY", accent = VerifiedEmerald)
                }
                if (!profile.phoneDirectCallable) {
                    InfraBadgeChip(label = "SERVER-SIDE ONLY", accent = BridgeAmber)
                }
                if (profile.requirePerCallConfirmation) {
                    InfraBadgeChip(label = "CONFIRM EVERY CALL", accent = CrimsonError)
                }
                if (profile.fallbackAfterId == InfraConnectorProfile.ID_PLAYWRIGHT) {
                    InfraBadgeChip(label = "FALLBACK AFTER PLAYWRIGHT", accent = TelemetryBlue)
                }
            }

            Text(
                text = profile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1-Tap Sandboxed Read-Only Fork preset for Memgraph MCP
            if (profile.id == InfraConnectorProfile.ID_MEMGRAPH &&
                (profile.visibility != ConnectorVisibility.SANDBOXED_READ_ONLY || !profile.serverEnforcesReadOnly)
            ) {
                OutlinedButton(
                    onClick = {
                        onUpdate(InfrastructureRegistryEngine.applyMemgraphSandboxPreset(profile))
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("memgraph_sandbox_preset_btn")
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = VerifiedEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply Memgraph Sandboxed Read-Only Preset", color = VerifiedEmerald)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Enabled switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connector Enabled",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (profile.enabled) {
                                    "Eligible for manual probes and (when ONLINE) stage-scoped context."
                                } else {
                                    "Disabled — excluded from agent context and tool calls."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.enabled,
                            onCheckedChange = { checked -> onUpdate(profile.copy(enabled = checked)) },
                            modifier = Modifier.testTag("infra_enabled_switch_${profile.id}")
                        )
                    }

                    // Visibility Scope Selector (enforcing allowedVisibilities per connector)
                    val allowedScopes = profile.allowedVisibilities()
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
                            val permitted = scope in allowedScopes
                            FilterChip(
                                selected = profile.visibility == scope,
                                enabled = permitted,
                                onClick = {
                                    if (permitted) {
                                        onUpdate(
                                            profile.copy(
                                                visibility = scope,
                                                readOnlyEnforced = if (scope == ConnectorVisibility.SANDBOXED_READ_ONLY) true else profile.readOnlyEnforced
                                            )
                                        )
                                    }
                                },
                                label = { Text(scope.shortBadge, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("infra_vis_${profile.id}_${scope.wire}")
                            )
                        }
                    }
                    if (allowedScopes.size < ConnectorVisibility.entries.size) {
                        Text(
                            text = when (profile.id) {
                                InfraConnectorProfile.ID_VPS_ACCESS ->
                                    "VPS-Access is locked to PRIVATE SOLO and requires explicit confirmation on every call."
                                InfraConnectorProfile.ID_WORLD_MECHANIC ->
                                    "World Mechanic (Stage) cannot be PUBLIC TEAM; phone clients are untrusted and must not invoke write tools."
                                else -> "Some visibility scopes are restricted for this connector."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = BridgeAmber
                        )
                    }

                    // PRIVATE_SOLO AI Name Sharing Opt-In (OFF by default)
                    if (profile.visibility == ConnectorVisibility.PRIVATE_SOLO) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Share Private Connector Name with Cloud AI",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "OFF by default. When OFF, private connector names are anonymized in cloud LLM prompts.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile.shareNameWithAi,
                                onCheckedChange = { checked -> onUpdate(profile.copy(shareNameWithAi = checked)) },
                                modifier = Modifier.testTag("infra_share_name_${profile.id}")
                            )
                        }
                    }

                    // Server Enforces Read-Only Badge Switch (Real Security Boundary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Server Enforces Read-Only",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Set ON when this server enforces read-only server-side (e.g., Memgraph MCP_READ_ONLY + query allowlist proxy). Server-side controls are the real security boundary.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.serverEnforcesReadOnly,
                            onCheckedChange = { checked -> onUpdate(profile.copy(serverEnforcesReadOnly = checked)) },
                            modifier = Modifier.testTag("infra_server_ro_switch_${profile.id}")
                        )
                    }

                    // Advisory Capability Display Switch (Explicitly NOT a security control)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Advisory Capability Display",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Advisory UI grouping only — NOT a security control (real tool names like run_cypher_query can write without matching keywords). Live MCP calls enforce exact-name allowlists from tools/list.",
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

                    // Pipeline Stage Selection (1..7)
                    Text(
                        text = "Pipeline Stages Allowed to See This Connector (when ONLINE)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (1..7).forEach { stageNum ->
                            val selected = stageNum in profile.selectedStages
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        profile.selectedStages - stageNum
                                    } else {
                                        profile.selectedStages + stageNum
                                    }
                                    onUpdate(profile.copy(selectedStages = updated))
                                },
                                label = { Text("S$stageNum", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("infra_stage_${profile.id}_$stageNum")
                            )
                        }
                    }

                    // Direct HTTPS URL (Primary) vs Optional Gateway Proxy toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Route via Optional Gateway Base URL",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (profile.useGatewayProxy) {
                                    "Appends gateway path to Tab 3 Gateway Base URL."
                                } else {
                                    "Primary path: connect directly to this server's HTTPS URL (Tailscale *.ts.net:<port> or Cloudflare Access)."
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
                            label = { Text("Direct HTTPS Endpoint URL (Primary · Optional in Standby)") },
                            placeholder = { Text("https://<machine>.<tailnet>.ts.net:<port>/mcp") },
                            isError = !urlValid,
                            supportingText = {
                                Text(
                                    text = urlValidationMsg,
                                    color = if (urlValid) MaterialTheme.colorScheme.onSurfaceVariant else CrimsonError
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("infra_endpoint_input_${profile.id}")
                        )
                    }

                    OutlinedTextField(
                        value = vaultKeyDraft,
                        onValueChange = { vaultKeyDraft = it.uppercase() },
                        label = { Text("Bearer Token Vault Reference (.env Vault Key)") },
                        placeholder = { Text("MCP_AUTH_TOKEN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Custom Header Vault References (Header-Name -> VAULT_KEY_REF)
                    Text(
                        text = "Optional Custom Header Vault Refs (Keystore-Resolved)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (profile.customHeaderVaultRefs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            profile.customHeaderVaultRefs.forEach { (hName, vRef) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$hName -> $vRef",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = ElectricCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            onUpdate(profile.copy(customHeaderVaultRefs = profile.customHeaderVaultRefs - hName))
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove header ref",
                                            tint = CrimsonError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newHeaderName,
                            onValueChange = { newHeaderName = it },
                            label = { Text("Header") },
                            placeholder = { Text("X-API-Key") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newHeaderVaultRef,
                            onValueChange = { newHeaderVaultRef = it.uppercase() },
                            label = { Text("Vault Ref") },
                            placeholder = { Text("HEADER_SECRET") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val h = newHeaderName.trim()
                                val r = newHeaderVaultRef.trim()
                                if (h.isNotBlank() && r.isNotBlank() && !h.equals("Authorization", ignoreCase = true)) {
                                    onUpdate(profile.copy(customHeaderVaultRefs = profile.customHeaderVaultRefs + (h to r)))
                                    newHeaderName = ""
                                    newHeaderVaultRef = ""
                                }
                            },
                            enabled = newHeaderName.isNotBlank() && newHeaderVaultRef.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    // Browser Use fallback trigger note
                    if (profile.id == InfraConnectorProfile.ID_BROWSER_USE) {
                        OutlinedTextField(
                            value = fallbackTriggerDraft,
                            onValueChange = { fallbackTriggerDraft = it },
                            label = { Text("Fallback Trigger Condition (after Playwright)") },
                            supportingText = { Text("Used by server-side orchestration when deterministic Playwright fails.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (resolvedTarget.isNotBlank()) {
                        Text(
                            text = "Resolved Target: $resolvedTarget",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = ElectricCyan
                        )
                    }

                    // Advisory Expected Capabilities Display
                    if (advisoryReadCaps.isNotEmpty()) {
                        Text(
                            text = "Expected Tools (Advisory Display — confirm via tools/list): ${advisoryReadCaps.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = VerifiedEmerald
                        )
                    }
                    if (advisoryMutatingCaps.isNotEmpty()) {
                        Text(
                            text = "Keyword-Flagged Tools (Advisory Display Only): ${advisoryMutatingCaps.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = BridgeAmber
                        )
                    }

                    // Live MCP tools/list Discovery, Mismatch Banner, Exact-Name Allowlist & Dual-Condition Read-Only Controls
                    if (profile.kind.isMcpTier) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live MCP Tools (${profile.discoveredTools.size} discovered · ${profile.allowedToolNames.size} allowlisted)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Deny-by-default exact-name allowlist. Read-only requires BOTH server annotations.readOnlyHint=true AND user Read-Only toggle.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onSyncTools,
                                enabled = profile.status != ConnectorStatus.PROBING,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("infra_sync_tools_${profile.id}")
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sync tools/list")
                            }
                        }

                        if (mismatch != null) {
                            Surface(
                                color = BridgeAmber.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, BridgeAmber.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = BridgeAmber, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Template vs. Live tools/list Mismatch",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = BridgeAmber
                                        )
                                    }
                                    if (mismatch.unexpectedOnServer.isNotEmpty()) {
                                        Text(
                                            text = "Extra on server: ${mismatch.unexpectedOnServer.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    }
                                    if (mismatch.missingFromServer.isNotEmpty()) {
                                        Text(
                                            text = "Missing from server: ${mismatch.missingFromServer.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    }
                                }
                            }
                        }

                        if (profile.discoveredTools.isEmpty()) {
                            Text(
                                text = "Standby — tap 'Sync tools/list' when server is online to populate exact-name allowlist.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                profile.discoveredTools.forEach { tool ->
                                    val isAllowed = tool.name in profile.allowedToolNames
                                    val userMarkedRo = tool.name in profile.userMarkedReadOnlyTools
                                    val effectiveRo = profile.isToolClassifiedReadOnly(tool.name)
                                    val hintLabel = when (tool.serverReadOnlyHint) {
                                        true -> "server readOnlyHint: true (advisory)"
                                        false -> "server readOnlyHint: false"
                                        null -> "server readOnlyHint: unknown"
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isAllowed) ElectricCyan.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = tool.name,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                InfraBadgeChip(
                                                    label = if (effectiveRo) "RO · 1x/SESSION" else "CONFIRM EVERY CALL",
                                                    accent = if (effectiveRo) VerifiedEmerald else BridgeAmber
                                                )
                                            }
                                            if (tool.description.isNotBlank()) {
                                                Text(
                                                    text = tool.description,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                InfraBadgeChip(
                                                    label = hintLabel,
                                                    accent = if (tool.serverReadOnlyHint == true) TelemetryBlue else BridgeAmber
                                                )
                                                FilterChip(
                                                    selected = isAllowed,
                                                    onClick = {
                                                        val next = if (isAllowed) {
                                                            profile.allowedToolNames - tool.name
                                                        } else {
                                                            profile.allowedToolNames + tool.name
                                                        }
                                                        onUpdate(profile.copy(allowedToolNames = next))
                                                    },
                                                    label = { Text(if (isAllowed) "Allowlisted" else "Allowlist Tool", style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag("mcp_allow_${profile.id}_${tool.name}")
                                                )
                                                FilterChip(
                                                    selected = userMarkedRo,
                                                    enabled = !profile.requirePerCallConfirmation && profile.id != InfraConnectorProfile.ID_VPS_ACCESS,
                                                    onClick = {
                                                        val next = if (userMarkedRo) {
                                                            profile.userMarkedReadOnlyTools - tool.name
                                                        } else {
                                                            profile.userMarkedReadOnlyTools + tool.name
                                                        }
                                                        onUpdate(profile.copy(userMarkedReadOnlyTools = next))
                                                    },
                                                    label = { Text(if (userMarkedRo) "User RO: ON" else "User RO: OFF", style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag("mcp_user_ro_${profile.id}_${tool.name}")
                                                )
                                                if (profile.enabled && profile.phoneDirectCallable && isAllowed) {
                                                    OutlinedButton(
                                                        onClick = { onInvokeTool(tool) },
                                                        modifier = Modifier.heightIn(min = 36.dp).testTag("mcp_run_${profile.id}_${tool.name}")
                                                    ) {
                                                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Run", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                                        fallbackTrigger = fallbackTriggerDraft.trim(),
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

@Composable
private fun McpToolInvokeDialog(
    profile: InfraConnectorProfile,
    tool: McpDiscoveredTool,
    policyDecision: McpCallPolicyDecision?,
    onInvoke: (argumentsJson: String, confirmedByUser: Boolean, onOutcome: (McpToolCallOutcome) -> Unit) -> Unit,
    onLookupFullResult: (String) -> McpStoredResult?,
    onCopyText: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var argsJson by remember(tool.name) { mutableStateOf("{}") }
    var running by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<McpToolCallOutcome?>(null) }
    var showRawResult by remember { mutableStateOf(false) }
    var confirmChecked by remember(tool.name, policyDecision?.status) {
        mutableStateOf(policyDecision?.status == McpCallPolicyStatus.AUTO_APPROVED_FOR_SESSION)
    }

    val isDenied = policyDecision == null || policyDecision.status == McpCallPolicyStatus.DENIED
    val needsExplicitConfirm = policyDecision?.status == McpCallPolicyStatus.NEEDS_EVERY_CALL_CONFIRMATION ||
        policyDecision?.status == McpCallPolicyStatus.NEEDS_SESSION_CONFIRMATION

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Invoke MCP Tool: ${tool.name}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Connector: ${profile.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                if (policyDecision != null) {
                    Surface(
                        color = if (isDenied) CrimsonError.copy(alpha = 0.14f)
                        else if (policyDecision.isReadOnly) VerifiedEmerald.copy(alpha = 0.14f)
                        else BridgeAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${policyDecision.status.name}: ${policyDecision.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = argsJson,
                    onValueChange = { argsJson = it },
                    label = { Text("Tool Arguments (JSON object)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("mcp_invoke_args_input")
                )
                if (needsExplicitConfirm) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (policyDecision?.isReadOnly == true) {
                                "Confirm read-only call (approves for this session)"
                            } else {
                                "I confirm this non-read call (required on every invocation; no auto-retry)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (policyDecision?.isReadOnly == true) VerifiedEmerald else BridgeAmber,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = confirmChecked,
                            onCheckedChange = { confirmChecked = it },
                            modifier = Modifier.testTag("mcp_invoke_confirm_switch")
                        )
                    }
                }
                outcome?.let { res ->
                    HorizontalDivider()
                    if (res.success) {
                        Text(
                            text = "Wrapped Output (${res.fullResultBytes} bytes · ref=${res.resultRefId})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = VerifiedEmerald
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showRawResult) {
                                    onLookupFullResult(res.resultRefId)?.rawContent ?: res.wrappedModelPayload
                                } else {
                                    res.wrappedModelPayload
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showRawResult = !showRawResult }) {
                                Text(if (showRawResult) "Show Capped Envelope" else "Inspect Full Local Output")
                            }
                            TextButton(onClick = { onCopyText("MCP Tool Output", res.wrappedModelPayload) }) {
                                Text("Copy")
                            }
                        }
                    } else {
                        Text(
                            text = res.errorMessage ?: "Tool invocation failed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonError
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    running = true
                    onInvoke(argsJson, confirmChecked) { res ->
                        outcome = res
                        running = false
                    }
                },
                enabled = !running && !isDenied && (!needsExplicitConfirm || confirmChecked),
                modifier = Modifier.testTag("mcp_invoke_execute_btn")
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Execute Tool")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
                label = { Text("Direct HTTPS Endpoint URL (Optional while Standby)") },
                placeholder = { Text("https://<machine>.<tailnet>.ts.net:<port>/mcp") },
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
    mcpAuditLog: List<McpAuditLogEntry>,
    onUpdateGatewayAndTime: (String, String, Boolean, Int, Int) -> Unit,
    onClearMcpAuditLog: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onOpenEnvVault: () -> Unit
) {
    var gatewayDraft by remember(state.gatewayBaseUrl) { mutableStateOf(state.gatewayBaseUrl) }
    var tzDraft by remember(state.localTimezoneId) { mutableStateOf(state.localTimezoneId) }
    var attachDraft by remember(state.attachTimeContextToAgents) { mutableStateOf(state.attachTimeContextToAgents) }
    var tokenBudgetDraft by remember(state.agentContextTokenBudget) { mutableStateOf(state.agentContextTokenBudget.toString()) }
    var charCapDraft by remember(state.mcpResultCharCap) { mutableStateOf(state.mcpResultCharCap.toString()) }
    val (gatewayValid, gatewayMsg) = remember(gatewayDraft) {
        InfrastructureRegistryEngine.validateRemoteEndpointUrl(gatewayDraft)
    }
    val remoteTimeOnline = state.connectors.any {
        it.id == InfraConnectorProfile.ID_TIME_SYNC && it.enabled && it.status == ConnectorStatus.ONLINE
    }
    val envelope = remember(tzDraft, remoteTimeOnline) {
        TimeSyncContextEngine.currentEnvelope(tzDraft, remoteTimeOnline)
    }
    val auditFormatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

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

        // 2. Optional Gateway Base URL & Prompt/MCP Budgets
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
                        text = "Optional Gateway Base URL & Agent Context Budgets",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Per-connector direct HTTPS URLs (Tailscale *.ts.net:<port> or Cloudflare Access) are the primary path. Optionally configure a reverse-proxy gateway here for connectors with proxy mode toggled on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = gatewayDraft,
                        onValueChange = { gatewayDraft = it },
                        label = { Text("Optional Gateway Base URL") },
                        placeholder = { Text("https://gateway.example.com") },
                        isError = !gatewayValid,
                        supportingText = { Text(gatewayMsg) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("infra_gateway_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tokenBudgetDraft,
                            onValueChange = { tokenBudgetDraft = it.filter(Char::isDigit).take(4) },
                            label = { Text("Infra Context Budget (tokens)") },
                            supportingText = { Text("Default 150 (40–1000)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("infra_token_budget_input")
                        )
                        OutlinedTextField(
                            value = charCapDraft,
                            onValueChange = { charCapDraft = it.filter(Char::isDigit).take(5) },
                            label = { Text("MCP Output Cap (chars)") },
                            supportingText = { Text("Default 2000 (250–12000)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("infra_char_cap_input")
                        )
                    }
                    Button(
                        onClick = {
                            val budget = tokenBudgetDraft.toIntOrNull()
                                ?: InfrastructureRegistryState.DEFAULT_AGENT_CONTEXT_TOKEN_BUDGET
                            val cap = charCapDraft.toIntOrNull()
                                ?: InfrastructureRegistryState.DEFAULT_MCP_RESULT_CHAR_CAP
                            onUpdateGatewayAndTime(gatewayDraft.trim(), tzDraft, attachDraft, budget, cap)
                        },
                        enabled = gatewayValid,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("infra_save_gateway_btn")
                    ) {
                        Text("Save Gateway, Time Sync & Budgets")
                    }
                }
            }
        }

        // 3. Zero-Leakage & Server-Side Boundary Audit Card
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
                            text = "Privacy Isolation & Security Boundary Audit",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = "• ${state.privateSoloCount} PRIVATE SOLO connector(s): Strictly device-local (excluded from Android cloud backup & transfer). Names are anonymized in cloud prompts unless explicitly opted in.\n" +
                            "• ${state.sandboxedReadOnlyCount} SANDBOXED READ-ONLY connector(s): Real read-only enforcement happens on the server (e.g., Memgraph MCP_READ_ONLY + query allowlist proxy); client display is advisory only.\n" +
                            "• ${state.publicTeamCount} PUBLIC TEAM connector(s): Non-secret descriptors shareable; all tokens stay in the local Keystore vault.\n" +
                            "• $envSecretCount secret(s) encrypted with Android Keystore AES-256-GCM (.env Vault).",
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

        // 4. Local MCP Audit Log Card
        item(key = "mcp_audit_log") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = TelemetryBlue)
                            Text(
                                text = "Local MCP Audit Log (${mcpAuditLog.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        if (mcpAuditLog.isNotEmpty()) {
                            TextButton(
                                onClick = onClearMcpAuditLog,
                                modifier = Modifier.testTag("infra_clear_audit_btn")
                            ) {
                                Text("Clear", color = CrimsonError)
                            }
                        }
                    }
                    Text(
                        text = "Records timestamp, connector, tool name, SHA-256 of arguments JSON, result size, and status locally. Raw secrets are never logged.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mcpAuditLog.isEmpty()) {
                        Text(
                            text = "No MCP tool calls logged yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        mcpAuditLog.take(15).forEach { entry ->
                            val entryAccent = when (entry.status) {
                                "SUCCESS" -> VerifiedEmerald
                                "AUTH_REQUIRED", "DENIED" -> CrimsonError
                                else -> BridgeAmber
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${entry.connectorName} · ${entry.toolName}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.status,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = entryAccent
                                        )
                                    }
                                    Text(
                                        text = "${auditFormatter.format(Date(entry.timestampMillis))} · sha256=${entry.argumentsSha256.take(12)}… · ${entry.resultSizeBytes}B · ${entry.readOnlyClassification}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
