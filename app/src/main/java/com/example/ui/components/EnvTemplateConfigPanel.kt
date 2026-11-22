package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.util.UUID

private data class EnvTemplateSlotSpec(
    val key: String,
    val label: String,
    val helperHint: String,
    val category: EnvSecretCategory,
    val target: EnvAutoFillTarget,
    val defaultValue: String = "",
    val defaultPermissions: ModelSecretPermissions = ModelSecretPermissions(noAccessHumanOnly = true)
)

private data class EnvTemplateGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val slots: List<EnvTemplateSlotSpec>
)

private val CanonicalEnvTemplateGroups = listOf(
    EnvTemplateGroup(
        id = "github",
        title = "GitHub Mirror & OAuth",
        subtitle = "Mirror projects & repos directly without manually editing token files",
        icon = Icons.Outlined.Key,
        slots = listOf(
            EnvTemplateSlotSpec(
                key = "GITHUB_PAT",
                label = "GitHub Personal Access Token",
                helperHint = "Starts with ghp_ or github_pat_ (Contents: Read & Write)",
                category = EnvSecretCategory.TOKEN,
                target = EnvAutoFillTarget.GITHUB_PAT
            ),
            EnvTemplateSlotSpec(
                key = "GITHUB_OAUTH_CLIENT_ID",
                label = "GitHub OAuth App Client ID",
                helperHint = "Optional Device Flow OAuth Client ID (e.g., Ov23li...)",
                category = EnvSecretCategory.TOKEN,
                target = EnvAutoFillTarget.GITHUB_OAUTH_CLIENT_ID
            )
        )
    ),
    EnvTemplateGroup(
        id = "router",
        title = "External AI Model Router",
        subtitle = "Power Roundtable Auto-Dispatch & Strategic Escalation (BYOK)",
        icon = Icons.Outlined.Public,
        slots = listOf(
            EnvTemplateSlotSpec(
                key = "OPENROUTER_API_KEY",
                label = "External Router / OpenRouter API Key",
                helperHint = "e.g., sk-or-v1-... or OpenAI-compatible API key",
                category = EnvSecretCategory.API_KEY,
                target = EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY
            ),
            EnvTemplateSlotSpec(
                key = "OPENROUTER_BASE_URL",
                label = "External Router Base URL",
                helperHint = "HTTPS endpoint (default: https://openrouter.ai/api/v1/)",
                category = EnvSecretCategory.ENDPOINT,
                target = EnvAutoFillTarget.EXTERNAL_ROUTER_BASE_URL,
                defaultValue = "https://openrouter.ai/api/v1/",
                defaultPermissions = ModelSecretPermissions(allowReadOnly = true, noAccessHumanOnly = false)
            )
        )
    ),
    EnvTemplateGroup(
        id = "logins",
        title = "Service Logins & Credentials",
        subtitle = "Locked to Human Only by default so AI models never see login values",
        icon = Icons.Outlined.Person,
        slots = listOf(
            EnvTemplateSlotSpec(
                key = "SERVICE_USERNAME",
                label = "Service Username / Email",
                helperHint = "Username or email for an external tool or portal",
                category = EnvSecretCategory.LOGIN,
                target = EnvAutoFillTarget.CUSTOM_CREDENTIAL
            ),
            EnvTemplateSlotSpec(
                key = "SERVICE_PASSWORD",
                label = "Service Password",
                helperHint = "Masked password stored strictly in AES-256-GCM vault",
                category = EnvSecretCategory.LOGIN,
                target = EnvAutoFillTarget.CUSTOM_CREDENTIAL
            )
        )
    )
)

/**
 * Structured `.env` Template Configuration UI.
 * Displays canonical template sections + any custom uploaded secrets with a 1-tap
 * "Paste from Clipboard" button right next to every field and a real-time validation
 * layer preventing broken `.env` formatting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvTemplateConfigPanel(
    entries: List<EnvSecretEntry>,
    guidedClipboardAssistEnabled: Boolean,
    onUpsertEntry: (EnvSecretEntry) -> Unit,
    onUpdatePermissions: (String, (ModelSecretPermissions) -> ModelSecretPermissions) -> Unit,
    onToggleAutoFill: (String, Boolean, EnvAutoFillTarget?) -> Unit,
    onGuidedPasteSingle: (EnvSecretEntry) -> Unit,
    onOpenPermissionsForEntry: (EnvSecretEntry) -> Unit
) {
    val context = LocalContext.current
    var autoClearClipboard by remember { mutableStateOf(true) }
    var showMaskedPreview by remember { mutableStateOf(false) }

    val entriesByKey = remember(entries) {
        entries.associateBy { it.key.uppercase() }
    }
    val canonicalKeys = remember {
        CanonicalEnvTemplateGroups.flatMap { g -> g.slots.map { it.key.uppercase() } }.toSet()
    }
    val customEntries = remember(entries) {
        entries.filter { it.key.uppercase() !in canonicalKeys }.sortedBy { it.key }
    }

    // Aggregate validation summary across all stored entries
    val validationStats = remember(entries) {
        var validCount = 0
        var warnCount = 0
        var errorCount = 0
        entries.forEach { entry ->
            when (EnvSecretValidator.validateEntry(entry).severity) {
                EnvValidationSeverity.VALID -> validCount++
                EnvValidationSeverity.WARNING -> warnCount++
                EnvValidationSeverity.ERROR -> errorCount++
                EnvValidationSeverity.EMPTY -> Unit
            }
        }
        Triple(validCount, warnCount, errorCount)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Validation & Clipboard Safety Strip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                when {
                    validationStats.third > 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    validationStats.second > 0 -> BridgeAmber.copy(alpha = 0.55f)
                    else -> VerifiedEmerald.copy(alpha = 0.45f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TelemetryPill(
                        text = "${validationStats.first} Validated",
                        accentColor = VerifiedEmerald
                    )
                    if (validationStats.second > 0) {
                        TelemetryPill(
                            text = "${validationStats.second} Warning(s)",
                            accentColor = BridgeAmber
                        )
                    }
                    if (validationStats.third > 0) {
                        TelemetryPill(
                            text = "${validationStats.third} Format Error(s)",
                            accentColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        TelemetryPill(
                            text = "0 Syntax Errors",
                            accentColor = VerifiedEmerald
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoClearClipboard = !autoClearClipboard },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoClearClipboard,
                        onCheckedChange = { autoClearClipboard = it },
                        modifier = Modifier.testTag("template_auto_clear_clipboard_checkbox")
                    )
                    Text(
                        text = "Auto-clear device clipboard after pasting any secret (Recommended)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Canonical Template Sections
        CanonicalEnvTemplateGroups.forEach { group ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("env_template_group_${group.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = group.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    group.slots.forEachIndexed { idx, slot ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                        val existing = entriesByKey[slot.key.uppercase()]
                        val effectiveEntry = existing ?: EnvSecretEntry(
                            id = UUID.randomUUID().toString(),
                            key = slot.key,
                            value = slot.defaultValue,
                            category = slot.category,
                            autoFillEnabled = false,
                            autoFillTarget = slot.target,
                            permissions = slot.defaultPermissions,
                            note = slot.helperHint
                        )
                        EnvTemplateFieldRow(
                            entry = effectiveEntry,
                            existsInVault = existing != null,
                            displayLabel = slot.label,
                            helperHint = slot.helperHint,
                            autoClearClipboard = autoClearClipboard,
                            guidedClipboardAssistEnabled = guidedClipboardAssistEnabled,
                            onSaveValidated = { sanitizedValue, autoEnableFill ->
                                val isKnownSlot = slot.target != EnvAutoFillTarget.CUSTOM_CREDENTIAL
                                onUpsertEntry(
                                    effectiveEntry.copy(
                                        value = sanitizedValue,
                                        autoFillEnabled = if (autoEnableFill && isKnownSlot) true else effectiveEntry.autoFillEnabled,
                                        updatedAtMillis = System.currentTimeMillis()
                                    )
                                )
                            },
                            onToggleAutoFill = { enabled ->
                                if (existing != null) {
                                    onToggleAutoFill(existing.id, enabled, slot.target)
                                } else {
                                    onUpsertEntry(effectiveEntry.copy(autoFillEnabled = enabled))
                                }
                            },
                            onGuidedPaste = { onGuidedPasteSingle(effectiveEntry) },
                            onEditPermissions = {
                                if (existing == null) {
                                    onUpsertEntry(effectiveEntry)
                                }
                                onOpenPermissionsForEntry(effectiveEntry)
                            },
                            onUpdatePermissions = { transform ->
                                if (existing != null) {
                                    onUpdatePermissions(existing.id, transform)
                                } else {
                                    onUpsertEntry(effectiveEntry.copy(permissions = transform(effectiveEntry.permissions)))
                                }
                            },
                            context = context
                        )
                    }
                }
            }
        }

        // Custom / Uploaded .env Entries Section
        if (customEntries.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("env_template_group_custom")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom & Uploaded .env Variables (${customEntries.size})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Additional keys imported from your .env file or added manually",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    customEntries.forEachIndexed { idx, entry ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                        EnvTemplateFieldRow(
                            entry = entry,
                            existsInVault = true,
                            displayLabel = entry.note.ifBlank { entry.category.label },
                            helperHint = "Category: ${entry.category.label} · Slot: ${entry.resolvedTarget().label}",
                            autoClearClipboard = autoClearClipboard,
                            guidedClipboardAssistEnabled = guidedClipboardAssistEnabled,
                            onSaveValidated = { sanitizedValue, _ ->
                                onUpsertEntry(
                                    entry.copy(
                                        value = sanitizedValue,
                                        updatedAtMillis = System.currentTimeMillis()
                                    )
                                )
                            },
                            onToggleAutoFill = { enabled -> onToggleAutoFill(entry.id, enabled, null) },
                            onGuidedPaste = { onGuidedPasteSingle(entry) },
                            onEditPermissions = { onOpenPermissionsForEntry(entry) },
                            onUpdatePermissions = { transform -> onUpdatePermissions(entry.id, transform) },
                            context = context
                        )
                    }
                }
            }
        }

        // Collapsible Masked .env File Preview
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMaskedPreview = !showMaskedPreview }
                        .testTag("toggle_masked_env_preview_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Masked .env Template Preview (Safe View)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showMaskedPreview) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (showMaskedPreview) "Hide preview" else "Show preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = showMaskedPreview) {
                    val maskedPreviewText = remember(entries) {
                        val previewEntries = if (entries.isEmpty()) {
                            CanonicalEnvTemplateGroups.flatMap { g ->
                                g.slots.map { slot ->
                                    EnvSecretEntry(key = slot.key, value = slot.defaultValue)
                                }
                            }
                        } else {
                            entries.sortedBy { it.key }
                        }
                        buildString {
                            appendLine("# Auto-validated .env format (values masked for safety)")
                            previewEntries.forEach { e ->
                                val shownVal = when {
                                    e.value.isBlank() -> ""
                                    e.category == EnvSecretCategory.ENDPOINT -> e.value
                                    else -> e.maskedValue()
                                }
                                appendLine("${e.key}=$shownVal")
                            }
                        }.trimEnd()
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = maskedPreviewText,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvTemplateFieldRow(
    entry: EnvSecretEntry,
    existsInVault: Boolean,
    displayLabel: String,
    helperHint: String,
    autoClearClipboard: Boolean,
    guidedClipboardAssistEnabled: Boolean,
    onSaveValidated: (sanitizedValue: String, autoEnableFill: Boolean) -> Unit,
    onToggleAutoFill: (Boolean) -> Unit,
    onGuidedPaste: () -> Unit,
    onEditPermissions: () -> Unit,
    onUpdatePermissions: ((ModelSecretPermissions) -> ModelSecretPermissions) -> Unit,
    context: android.content.Context
) {
    var draftValue by remember(entry.id, entry.value) { mutableStateOf(entry.value) }
    var revealed by remember(entry.id) { mutableStateOf(entry.category == EnvSecretCategory.ENDPOINT) }
    var clipboardFeedback by remember(entry.id) { mutableStateOf<String?>(null) }
    var feedbackIsError by remember(entry.id) { mutableStateOf(false) }

    val validation = remember(entry.key, draftValue, entry.category, entry.autoFillTarget) {
        EnvSecretValidator.validateValue(entry.key, draftValue, entry.category, entry.autoFillTarget)
    }
    val hasUnsavedChanges = draftValue != entry.value

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Key Name + Status Badge Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val (badgeText, badgeColor) = when (validation.severity) {
                EnvValidationSeverity.VALID -> "Valid" to VerifiedEmerald
                EnvValidationSeverity.WARNING -> "Check" to BridgeAmber
                EnvValidationSeverity.ERROR -> "Invalid" to MaterialTheme.colorScheme.error
                EnvValidationSeverity.EMPTY -> "Empty" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            TelemetryPill(text = badgeText, accentColor = badgeColor)
        }

        // Input Field + Direct "Paste from Clipboard" Button Side-by-Side
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draftValue,
                onValueChange = {
                    draftValue = it
                    clipboardFeedback = null
                },
                placeholder = {
                    Text(
                        text = helperHint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                isError = validation.severity == EnvValidationSeverity.ERROR,
                singleLine = true,
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { revealed = !revealed },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (revealed) "Hide ${entry.key}" else "Reveal ${entry.key}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("template_input_${entry.key.lowercase()}")
            )

            // 1-Tap Paste from Clipboard button directly beside every field
            FilledTonalIconButton(
                onClick = {
                    val raw = readClipboardPlainText(context)
                    val cleaned = EnvFileCodec.sanitizeClipboardValue(raw.orEmpty(), entry.key)
                    if (cleaned.isBlank()) {
                        feedbackIsError = true
                        clipboardFeedback = "Clipboard is empty — copy your value first, then tap Paste."
                    } else {
                        val pastedCheck = EnvSecretValidator.validateValue(
                            entry.key, cleaned, entry.category, entry.autoFillTarget
                        )
                        draftValue = cleaned
                        if (pastedCheck.canSave) {
                            onSaveValidated(cleaned, true)
                            if (autoClearClipboard) clearDeviceClipboard(context)
                            feedbackIsError = false
                            clipboardFeedback = if (pastedCheck.severity == EnvValidationSeverity.WARNING) {
                                "Pasted & saved (notice: ${pastedCheck.message})"
                            } else {
                                "Pasted, validated & vaulted cleanly" + if (autoClearClipboard) " · clipboard cleared." else "."
                            }
                        } else {
                            feedbackIsError = true
                            clipboardFeedback = "Blocked invalid format: ${pastedCheck.message}"
                        }
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("template_paste_${entry.key.lowercase()}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = "Paste from clipboard into ${entry.key}"
                )
            }
        }

        // Real-time Validation Feedback & Save Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusIcon = when {
                feedbackIsError || validation.severity == EnvValidationSeverity.ERROR -> Icons.Outlined.ErrorOutline
                validation.severity == EnvValidationSeverity.WARNING -> Icons.Outlined.WarningAmber
                validation.severity == EnvValidationSeverity.VALID -> Icons.Outlined.CheckCircle
                else -> null
            }
            val statusColor = when {
                feedbackIsError || validation.severity == EnvValidationSeverity.ERROR -> MaterialTheme.colorScheme.error
                validation.severity == EnvValidationSeverity.WARNING -> BridgeAmber
                validation.severity == EnvValidationSeverity.VALID -> VerifiedEmerald
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            if (statusIcon != null) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = clipboardFeedback ?: validation.message,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.weight(1f)
            )
            if (hasUnsavedChanges) {
                Button(
                    onClick = {
                        val cleaned = EnvFileCodec.sanitizeClipboardValue(draftValue, entry.key)
                        val check = EnvSecretValidator.validateValue(entry.key, cleaned, entry.category, entry.autoFillTarget)
                        if (check.canSave) {
                            draftValue = cleaned
                            onSaveValidated(cleaned, false)
                            feedbackIsError = false
                            clipboardFeedback = "Saved & validated."
                        } else {
                            feedbackIsError = true
                            clipboardFeedback = check.message
                        }
                    },
                    enabled = validation.canSave,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("template_save_${entry.key.lowercase()}")
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Compact Auto-Fill + Human-Only Permission Control Strip
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (entry.resolvedTarget() != EnvAutoFillTarget.CUSTOM_CREDENTIAL) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleAutoFill(!entry.autoFillEnabled) }
                ) {
                    Switch(
                        checked = entry.autoFillEnabled,
                        onCheckedChange = onToggleAutoFill,
                        modifier = Modifier.testTag("template_autofill_${entry.key.lowercase()}")
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (entry.autoFillEnabled) "Auto-Fill ON" else "Auto-Fill",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.autoFillEnabled) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = onEditPermissions,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("template_permissions_${entry.key.lowercase()}")
            ) {
                Icon(
                    imageVector = if (entry.permissions.noAccessHumanOnly) Icons.Outlined.Lock else Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (entry.permissions.noAccessHumanOnly) BridgeAmber else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = entry.permissions.summaryLabel(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (guidedClipboardAssistEnabled) {
                TextButton(
                    onClick = onGuidedPaste,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Guided Prompt", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
