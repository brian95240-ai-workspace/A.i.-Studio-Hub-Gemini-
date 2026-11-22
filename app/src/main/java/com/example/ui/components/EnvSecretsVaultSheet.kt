package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.security.DiscreteAutoFillResolution
import com.example.data.security.EnvAutoFillTarget
import com.example.data.security.EnvFileCodec
import com.example.data.security.EnvSecretCategory
import com.example.data.security.EnvSecretEntry
import com.example.data.security.EnvSecretValidator
import com.example.data.security.EnvValidationSeverity
import com.example.data.security.ModelSecretPermissions
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EnvSecretsVaultSheet(
    entries: List<EnvSecretEntry>,
    autoFillResolution: DiscreteAutoFillResolution,
    onDismiss: () -> Unit,
    onUpsertEntry: (EnvSecretEntry) -> Unit,
    onDeleteEntry: (String, String) -> Unit,
    onUpdatePermissions: (String, (ModelSecretPermissions) -> ModelSecretPermissions) -> Unit,
    onToggleAutoFill: (String, Boolean, EnvAutoFillTarget?) -> Unit,
    onImportEnvUri: (Uri) -> Unit,
    onCreateStarterEnv: () -> Unit,
    onExportEnvUri: (Uri) -> Unit,
    onApplyAutoFillNow: () -> Unit
) {
    var editingEntry by remember { mutableStateOf<EnvSecretEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var guidedClipboardAssistEnabled by remember { mutableStateOf(true) }
    var guidedPasteQueue by remember { mutableStateOf<List<EnvSecretEntry>>(emptyList()) }
    var showGuidedLoginDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("TEMPLATE") }
    var permissionsDialogKey by remember { mutableStateOf<String?>(null) }
    var showFileTools by remember { mutableStateOf(entries.isEmpty()) }
    var showPlaintextExportConfirm by remember { mutableStateOf(false) }

    val importEnvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportEnvUri(uri)
    }

    val exportEnvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) onExportEnvUri(uri)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = VerifiedEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Secure .env Secrets Vault",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Hardware-backed AES-256-GCM local vault · Never synced to cloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald
                    )
                }
            }

            Text(
                text = "Create or upload a .env file for API keys, tokens, and login credentials. Enable Discrete Auto-Fill per entry, and explicitly grant or block AI model permissions for every secret.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Vault Summary Telemetry Strip
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TelemetryPill(
                    text = "${entries.size} Secret(s)",
                    accentColor = MaterialTheme.colorScheme.primary
                )
                TelemetryPill(
                    text = "${autoFillResolution.activeAutoFillCount} Auto-Fill Active",
                    accentColor = if (autoFillResolution.activeAutoFillCount > 0) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val humanOnlyCount = entries.count { it.permissions.noAccessHumanOnly }
                TelemetryPill(
                    text = "$humanOnlyCount Human Only",
                    accentColor = BridgeAmber
                )
            }

            // Primary Apply Auto-Fill CTA (immediately surfaced when active) + Collapsible File & Guided Tools
            if (autoFillResolution.activeAutoFillCount > 0) {
                Button(
                    onClick = onApplyAutoFillNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_apply_autofill_button")
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply Discrete Auto-Fill (${autoFillResolution.activeAutoFillCount})")
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (showFileTools) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFileTools = !showFileTools }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("env_toggle_file_tools_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import / Export .env & Guided Clipboard Tools",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (guidedClipboardAssistEnabled) "Guided Paste-Assist ON · Add, upload, or export .env"
                                else "Add custom key, upload .env, or export .env",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (showFileTools) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = "Toggle file and clipboard tools",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showFileTools) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier.testTag("env_add_secret_button")
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Key")
                                }
                                OutlinedButton(
                                    onClick = { importEnvLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.testTag("env_upload_file_button")
                                ) {
                                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Upload .env")
                                }
                                OutlinedButton(
                                    onClick = onCreateStarterEnv,
                                    modifier = Modifier.testTag("env_create_starter_button")
                                ) {
                                    Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create Template")
                                }
                                OutlinedButton(
                                    onClick = { showPlaintextExportConfirm = true },
                                    modifier = Modifier.testTag("env_export_file_button")
                                ) {
                                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export .env (Plaintext)")
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { guidedClipboardAssistEnabled = !guidedClipboardAssistEnabled },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = guidedClipboardAssistEnabled,
                                    onCheckedChange = { guidedClipboardAssistEnabled = it },
                                    modifier = Modifier.testTag("env_guided_clipboard_checkbox")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Guided Clipboard Paste-Assist",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (guidedClipboardAssistEnabled) VerifiedEmerald else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Prompt to copy tokens, API keys, or login pairs and paste safely without breaking formatting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(visible = guidedClipboardAssistEnabled) {
                                val emptyTemplateEntries = entries.filter { it.value.isBlank() }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (emptyTemplateEntries.isNotEmpty()) {
                                        Button(
                                            onClick = { guidedPasteQueue = emptyTemplateEntries },
                                            modifier = Modifier.testTag("env_guided_fill_empty_button")
                                        ) {
                                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Guided Fill Empty (${emptyTemplateEntries.size})")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { showGuidedLoginDialog = true },
                                        modifier = Modifier.testTag("env_guided_login_pair_button")
                                    ) {
                                        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("+ Guided Login (User + Pass)")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // View Switcher: Template Form (default) vs Detailed Permissions & Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewMode == "TEMPLATE",
                    onClick = { viewMode = "TEMPLATE" },
                    label = { Text("Template Form") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("env_view_template_chip")
                )
                FilterChip(
                    selected = viewMode == "CARDS",
                    onClick = { viewMode = "CARDS" },
                    label = { Text("Permissions & Cards (${entries.size})") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("env_view_cards_chip")
                )
            }

            if (viewMode == "TEMPLATE") {
                EnvTemplateConfigPanel(
                    entries = entries,
                    guidedClipboardAssistEnabled = guidedClipboardAssistEnabled,
                    onUpsertEntry = onUpsertEntry,
                    onUpdatePermissions = onUpdatePermissions,
                    onToggleAutoFill = onToggleAutoFill,
                    onGuidedPasteSingle = { entry -> guidedPasteQueue = listOf(entry) },
                    onOpenPermissionsForEntry = { entry -> permissionsDialogKey = entry.key }
                )
            } else if (entries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("Encrypted .env Vault is Empty", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Switch to Template Form to paste keys directly, tap 'Create Template', or 'Upload .env' to import an existing file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                entries.forEach { entry ->
                    EnvSecretVaultCard(
                        entry = entry,
                        guidedClipboardAssistEnabled = guidedClipboardAssistEnabled,
                        onGuidedPaste = { guidedPasteQueue = listOf(entry) },
                        onEdit = { editingEntry = entry },
                        onDelete = { onDeleteEntry(entry.id, entry.key) },
                        onUpdatePermissions = { transform -> onUpdatePermissions(entry.id, transform) },
                        onToggleAutoFill = { enabled, target -> onToggleAutoFill(entry.id, enabled, target) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        EnvSecretEditorDialog(
            initialEntry = null,
            existingKeys = entries.map { it.key.uppercase() }.toSet(),
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                onUpsertEntry(entry)
                showAddDialog = false
            }
        )
    }

    editingEntry?.let { current ->
        EnvSecretEditorDialog(
            initialEntry = current,
            existingKeys = entries.map { it.key.uppercase() }.toSet(),
            onDismiss = { editingEntry = null },
            onSave = { updated ->
                onUpsertEntry(updated)
                editingEntry = null
            }
        )
    }

    if (guidedPasteQueue.isNotEmpty()) {
        GuidedClipboardPasteDialog(
            queue = guidedPasteQueue,
            onDismiss = { guidedPasteQueue = emptyList() },
            onSaveEntry = onUpsertEntry
        )
    }

    if (showGuidedLoginDialog) {
        GuidedLoginPairDialog(
            onDismiss = { showGuidedLoginDialog = false },
            onSavePair = { userEntry, passEntry ->
                onUpsertEntry(userEntry)
                onUpsertEntry(passEntry)
                showGuidedLoginDialog = false
            }
        )
    }

    permissionsDialogKey?.let { targetKey ->
        val entry = entries.firstOrNull { it.key.equals(targetKey, ignoreCase = true) }
        if (entry != null) {
            EnvPermissionsQuickDialog(
                entry = entry,
                onUpdatePermissions = { transform -> onUpdatePermissions(entry.id, transform) },
                onDismiss = { permissionsDialogKey = null }
            )
        }
    }

    if (showPlaintextExportConfirm) {
        var acknowledgedPlaintextRisk by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPlaintextExportConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = "Warning: Unencrypted Plaintext Export",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Exporting .env writes all ${entries.size} vaulted credential(s) as UNENCRYPTED PLAINTEXT to the file destination you pick. Any app or cloud sync folder with access to that file will be able to read your tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acknowledgedPlaintextRisk = !acknowledgedPlaintextRisk },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acknowledgedPlaintextRisk,
                            onCheckedChange = { acknowledgedPlaintextRisk = it },
                            modifier = Modifier.testTag("env_export_plaintext_ack_checkbox")
                        )
                        Text(
                            text = "I understand this exported .env file is unencrypted plaintext and will protect or delete it after use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPlaintextExportConfirm = false
                        exportEnvLauncher.launch(".env")
                    },
                    enabled = acknowledgedPlaintextRisk,
                    modifier = Modifier.testTag("env_export_plaintext_confirm_btn")
                ) {
                    Text("Export Plaintext .env")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPlaintextExportConfirm = false },
                    modifier = Modifier.testTag("env_export_plaintext_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvSecretVaultCard(
    entry: EnvSecretEntry,
    guidedClipboardAssistEnabled: Boolean,
    onGuidedPaste: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdatePermissions: ((ModelSecretPermissions) -> ModelSecretPermissions) -> Unit,
    onToggleAutoFill: (Boolean, EnvAutoFillTarget?) -> Unit
) {
    var revealed by remember(entry.id) { mutableStateOf(false) }
    var showTargetPicker by remember(entry.id) { mutableStateOf(false) }
    val perms = entry.permissions

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                entry.autoFillEnabled -> VerifiedEmerald.copy(alpha = 0.6f)
                perms.noAccessHumanOnly -> MaterialTheme.colorScheme.outline
                else -> BridgeAmber.copy(alpha = 0.55f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("env_secret_card_${entry.key.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Key Header + Reveal/Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = if (entry.autoFillEnabled) VerifiedEmerald else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.key,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (revealed) entry.value.ifBlank { "(empty)" } else entry.maskedValue(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (revealed) "Hide secret value" else "Reveal secret value",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("env_edit_${entry.key.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit secret",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("env_delete_${entry.key.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete secret",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TelemetryPill(text = entry.category.label, accentColor = MaterialTheme.colorScheme.secondary)
                TelemetryPill(
                    text = perms.summaryLabel(),
                    accentColor = if (perms.noAccessHumanOnly) BridgeAmber else MaterialTheme.colorScheme.primary
                )
                if (entry.autoFillEnabled) {
                    TelemetryPill(
                        text = "Auto-Fill → ${entry.resolvedTarget().label}",
                        accentColor = VerifiedEmerald
                    )
                }
            }

            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (guidedClipboardAssistEnabled) {
                if (entry.value.isBlank()) {
                    Button(
                        onClick = onGuidedPaste,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("env_guided_paste_${entry.key.lowercase()}")
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy & Guided Paste into ${entry.key}")
                    }
                } else {
                    OutlinedButton(
                        onClick = onGuidedPaste,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("env_guided_paste_${entry.key.lowercase()}")
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Replace via Guided Clipboard Paste")
                    }
                }
            }

            // Discrete Auto-Fill Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Discrete Auto-Fill", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Slot: ${entry.resolvedTarget().label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showTargetPicker = !showTargetPicker },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (showTargetPicker) "Done" else "Target")
                        }
                        Switch(
                            checked = entry.autoFillEnabled,
                            onCheckedChange = { enabled -> onToggleAutoFill(enabled, null) },
                            modifier = Modifier.testTag("env_autofill_switch_${entry.key.lowercase()}")
                        )
                    }

                    AnimatedVisibility(visible = showTargetPicker) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EnvAutoFillTarget.entries.forEach { target ->
                                FilterChip(
                                    selected = entry.autoFillTarget == target,
                                    onClick = { onToggleAutoFill(entry.autoFillEnabled, target) },
                                    label = { Text(target.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // AI Model Access Permission Matrix (Create, Edit[Add/Delete/Change], Read Only, No Access Human Only)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "AI Model Access Permissions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    // 1) Create
                    PermissionCheckboxRow(
                        label = "1. Create (allow model to create/derive entry)",
                        checked = perms.allowCreate,
                        testTag = "env_perm_create_${entry.key.lowercase()}",
                        onCheckedChange = { checked ->
                            onUpdatePermissions { it.toggleCreate(checked) }
                        }
                    )

                    // 2) Edit (Parent Tri-State + 3 Sub-boxes: Add, Delete, Change)
                    val editTriState = when {
                        perms.hasAllEdit -> ToggleableState.On
                        perms.hasAnyEdit -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !perms.hasAllEdit
                                onUpdatePermissions { it.toggleEditParent(next) }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(
                            state = editTriState,
                            onClick = {
                                val next = !perms.hasAllEdit
                                onUpdatePermissions { it.toggleEditParent(next) }
                            },
                            modifier = Modifier.testTag("env_perm_edit_${entry.key.lowercase()}")
                        )
                        Text(
                            text = "2. Edit (select sub-permissions below)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 3 Sub-checkboxes for Edit: Add, Delete, Change
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        CompactSubPermissionBox(
                            label = "Add",
                            checked = perms.allowEditAdd,
                            testTag = "env_perm_edit_add_${entry.key.lowercase()}",
                            onCheckedChange = { c -> onUpdatePermissions { it.toggleEditAdd(c) } }
                        )
                        CompactSubPermissionBox(
                            label = "Delete",
                            checked = perms.allowEditDelete,
                            testTag = "env_perm_edit_delete_${entry.key.lowercase()}",
                            onCheckedChange = { c -> onUpdatePermissions { it.toggleEditDelete(c) } }
                        )
                        CompactSubPermissionBox(
                            label = "Change",
                            checked = perms.allowEditChange,
                            testTag = "env_perm_edit_change_${entry.key.lowercase()}",
                            onCheckedChange = { c -> onUpdatePermissions { it.toggleEditChange(c) } }
                        )
                    }

                    // 3) Read Only
                    PermissionCheckboxRow(
                        label = "3. Read Only (model can reference, cannot modify)",
                        checked = perms.allowReadOnly,
                        testTag = "env_perm_readonly_${entry.key.lowercase()}",
                        onCheckedChange = { checked ->
                            onUpdatePermissions { it.toggleReadOnly(checked) }
                        }
                    )

                    // 4) No Access (Human Only)
                    PermissionCheckboxRow(
                        label = "4. No Access (Human Only — hidden from all AI models)",
                        checked = perms.noAccessHumanOnly,
                        highlightColor = BridgeAmber,
                        testTag = "env_perm_human_only_${entry.key.lowercase()}",
                        onCheckedChange = { checked ->
                            onUpdatePermissions { it.toggleNoAccessHumanOnly(checked) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckboxRow(
    label: String,
    checked: Boolean,
    testTag: String,
    highlightColor: androidx.compose.ui.graphics.Color? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (checked && highlightColor != null) highlightColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompactSubPermissionBox(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvPermissionsQuickDialog(
    entry: EnvSecretEntry,
    onUpdatePermissions: ((ModelSecretPermissions) -> ModelSecretPermissions) -> Unit,
    onDismiss: () -> Unit
) {
    val perms = entry.permissions
    val editTriState = when {
        perms.hasAllEdit -> ToggleableState.On
        perms.hasAnyEdit -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Column {
                Text("AI Model Access Policy", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PermissionCheckboxRow(
                    label = "1. Create (allow model to create/derive entry)",
                    checked = perms.allowCreate,
                    testTag = "quick_perm_create_${entry.key.lowercase()}",
                    onCheckedChange = { c -> onUpdatePermissions { it.toggleCreate(c) } }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdatePermissions { it.toggleEditParent(!perms.hasAllEdit) } }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriStateCheckbox(
                        state = editTriState,
                        onClick = { onUpdatePermissions { it.toggleEditParent(!perms.hasAllEdit) } },
                        modifier = Modifier.testTag("quick_perm_edit_${entry.key.lowercase()}")
                    )
                    Text("2. Edit (select sub-permissions below)", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactSubPermissionBox(
                        label = "Add",
                        checked = perms.allowEditAdd,
                        testTag = "quick_perm_edit_add_${entry.key.lowercase()}",
                        onCheckedChange = { c -> onUpdatePermissions { it.toggleEditAdd(c) } }
                    )
                    CompactSubPermissionBox(
                        label = "Delete",
                        checked = perms.allowEditDelete,
                        testTag = "quick_perm_edit_delete_${entry.key.lowercase()}",
                        onCheckedChange = { c -> onUpdatePermissions { it.toggleEditDelete(c) } }
                    )
                    CompactSubPermissionBox(
                        label = "Change",
                        checked = perms.allowEditChange,
                        testTag = "quick_perm_edit_change_${entry.key.lowercase()}",
                        onCheckedChange = { c -> onUpdatePermissions { it.toggleEditChange(c) } }
                    )
                }
                PermissionCheckboxRow(
                    label = "3. Read Only (model can reference, cannot modify)",
                    checked = perms.allowReadOnly,
                    testTag = "quick_perm_readonly_${entry.key.lowercase()}",
                    onCheckedChange = { c -> onUpdatePermissions { it.toggleReadOnly(c) } }
                )
                PermissionCheckboxRow(
                    label = "4. No Access (Human Only — hidden from all AI models)",
                    checked = perms.noAccessHumanOnly,
                    highlightColor = BridgeAmber,
                    testTag = "quick_perm_human_only_${entry.key.lowercase()}",
                    onCheckedChange = { c -> onUpdatePermissions { it.toggleNoAccessHumanOnly(c) } }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvSecretEditorDialog(
    initialEntry: EnvSecretEntry?,
    existingKeys: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (EnvSecretEntry) -> Unit
) {
    val context = LocalContext.current
    var keyText by remember { mutableStateOf(initialEntry?.key.orEmpty()) }
    var valueText by remember { mutableStateOf(initialEntry?.value.orEmpty()) }
    var noteText by remember { mutableStateOf(initialEntry?.note.orEmpty()) }
    var category by remember { mutableStateOf(initialEntry?.category ?: EnvSecretCategory.API_KEY) }
    var autoFillEnabled by remember { mutableStateOf(initialEntry?.autoFillEnabled ?: false) }
    var autoFillTarget by remember { mutableStateOf(initialEntry?.autoFillTarget ?: EnvAutoFillTarget.AUTO_DETECT) }
    var showSecret by remember { mutableStateOf(false) }
    var clearClipboardOnPaste by remember { mutableStateOf(true) }
    var pasteNotice by remember { mutableStateOf<String?>(null) }

    val keyValidation = remember(keyText, existingKeys, initialEntry?.key) {
        EnvSecretValidator.validateKey(keyText, existingKeys, initialEntry?.key)
    }
    val valueValidation = remember(keyText, valueText, category, autoFillTarget) {
        EnvSecretValidator.validateValue(keyText, valueText, category, autoFillTarget)
    }
    val canSave = keyValidation.canSave && valueValidation.canSave

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(if (initialEntry == null) "Add .env Secret Entry" else "Edit ${initialEntry.key}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        if (initialEntry == null) {
                            category = EnvSecretCategory.inferFromKey(it)
                        }
                    },
                    label = { Text("Variable Key (e.g., GITHUB_PAT, OPENROUTER_API_KEY)") },
                    isError = keyText.isNotEmpty() && keyValidation.severity == EnvValidationSeverity.ERROR,
                    supportingText = if (keyText.isNotEmpty()) {
                        {
                            Text(
                                text = keyValidation.message,
                                color = if (keyValidation.severity == EnvValidationSeverity.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else VerifiedEmerald
                            )
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_editor_key_input")
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Secret Value / Token / Login") },
                    isError = valueValidation.severity == EnvValidationSeverity.ERROR,
                    supportingText = {
                        Text(
                            text = valueValidation.message,
                            color = when (valueValidation.severity) {
                                EnvValidationSeverity.ERROR -> MaterialTheme.colorScheme.error
                                EnvValidationSeverity.WARNING -> BridgeAmber
                                EnvValidationSeverity.VALID -> VerifiedEmerald
                                EnvValidationSeverity.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showSecret) "Hide value" else "Show value"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_editor_value_input")
                )
                OutlinedButton(
                    onClick = {
                        val cleaned = EnvFileCodec.sanitizeClipboardValue(
                            readClipboardPlainText(context).orEmpty(),
                            keyText
                        )
                        if (cleaned.isBlank()) {
                            pasteNotice = "Clipboard is empty — copy your value first."
                        } else {
                            valueText = cleaned
                            if (clearClipboardOnPaste) clearDeviceClipboard(context)
                            pasteNotice = "Pasted & auto-formatted safely" + if (clearClipboardOnPaste) " (clipboard cleared)." else "."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_editor_paste_clipboard_button")
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste & Auto-Format from Clipboard")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearClipboardOnPaste = !clearClipboardOnPaste },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = clearClipboardOnPaste,
                        onCheckedChange = { clearClipboardOnPaste = it }
                    )
                    Text(
                        text = "Clear clipboard right after pasting",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                pasteNotice?.let { notice ->
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald
                    )
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Optional Note (e.g., Work GitHub account)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EnvSecretCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Enable Discrete Auto-Fill",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = autoFillEnabled, onCheckedChange = { autoFillEnabled = it })
                }
                if (autoFillEnabled) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EnvAutoFillTarget.entries.forEach { target ->
                            FilterChip(
                                selected = autoFillTarget == target,
                                onClick = { autoFillTarget = target },
                                label = { Text(target.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sanitizedValue = EnvFileCodec.sanitizeClipboardValue(valueText, keyText)
                    onSave(
                        EnvSecretEntry(
                            id = initialEntry?.id ?: java.util.UUID.randomUUID().toString(),
                            key = EnvFileCodec.sanitizeKey(keyText),
                            value = sanitizedValue,
                            category = category,
                            autoFillEnabled = autoFillEnabled,
                            autoFillTarget = autoFillTarget,
                            permissions = initialEntry?.permissions ?: ModelSecretPermissions(noAccessHumanOnly = true),
                            note = noteText.trim()
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.testTag("env_editor_save_button")
            ) { Text("Save to Vault") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
