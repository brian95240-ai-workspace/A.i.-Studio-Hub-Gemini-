package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.data.security.EnvAutoFillTarget
import com.example.data.security.EnvFileCodec
import com.example.data.security.EnvSecretCategory
import com.example.data.security.EnvSecretEntry
import com.example.data.security.ModelSecretPermissions
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald
import java.util.UUID

/**
 * Reads plain text from the Android clipboard strictly on explicit user action.
 */
internal fun readClipboardPlainText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/**
 * Clears the Android clipboard after a secret has been safely pasted into the encrypted vault.
 */
internal fun clearDeviceClipboard(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cm.clearPrimaryClip()
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

/**
 * Novice-friendly guided paste modal for one or more `.env` template fields.
 * Prompts the user to copy their token, API key, or username/password, then safely pastes,
 * strips stray formatting/prefixes, masks the value, keeps AI access Human Only, and
 * optionally wipes the Android clipboard afterward.
 */
@Composable
fun GuidedClipboardPasteDialog(
    queue: List<EnvSecretEntry>,
    onDismiss: () -> Unit,
    onSaveEntry: (EnvSecretEntry) -> Unit
) {
    if (queue.isEmpty()) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    var currentIndex by remember(queue) { mutableIntStateOf(0) }
    val activeEntry = queue.getOrNull(currentIndex) ?: run {
        onDismiss()
        return
    }

    val isKnownAutoFillSlot = remember(activeEntry.id) {
        activeEntry.resolvedTarget() != EnvAutoFillTarget.CUSTOM_CREDENTIAL
    }
    var clearClipboardAfterPaste by remember { mutableStateOf(true) }
    var enableAutoFillOnPaste by remember(activeEntry.id) {
        mutableStateOf(activeEntry.autoFillEnabled || isKnownAutoFillSlot)
    }
    var statusMessage by remember(activeEntry.id) { mutableStateOf<String?>(null) }
    var isError by remember(activeEntry.id) { mutableStateOf(false) }

    val friendlyItemType = remember(activeEntry.key, activeEntry.category) {
        val upper = activeEntry.key.uppercase()
        when {
            upper.contains("PASSWORD") || upper.contains("PASS") -> "password"
            upper.contains("USER") || upper.contains("EMAIL") || upper.contains("LOGIN") -> "username or login"
            activeEntry.category == EnvSecretCategory.TOKEN -> "access token"
            activeEntry.category == EnvSecretCategory.API_KEY -> "API key"
            activeEntry.category == EnvSecretCategory.ENDPOINT -> "endpoint URL"
            else -> "secret value"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (queue.size > 1) {
                        "Guided Paste (${currentIndex + 1} of ${queue.size})"
                    } else {
                        "Guided Clipboard Paste"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = activeEntry.key,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeEntry.note.isNotBlank()) {
                    Text(
                        text = activeEntry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Step 1: Copy your $friendlyItemType",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Switch to your browser, provider dashboard, or password manager and copy your $friendlyItemType to the clipboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Step 2: Tap 'Paste & Format Safely' below",
                            style = MaterialTheme.typography.labelLarge,
                            color = VerifiedEmerald
                        )
                        Text(
                            text = "Vortex will automatically strip extra spaces, stray quotes, or accidental '${activeEntry.key}=' prefixes so your .env syntax stays clean. Your value stays masked and locked to '${activeEntry.permissions.summaryLabel()}'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearClipboardAfterPaste = !clearClipboardAfterPaste },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = clearClipboardAfterPaste,
                        onCheckedChange = { clearClipboardAfterPaste = it },
                        modifier = Modifier.testTag("guided_paste_clear_clipboard_checkbox")
                    )
                    Text(
                        text = "Clear device clipboard immediately after pasting (Recommended)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isKnownAutoFillSlot) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enableAutoFillOnPaste = !enableAutoFillOnPaste },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableAutoFillOnPaste,
                            onCheckedChange = { enableAutoFillOnPaste = it },
                            modifier = Modifier.testTag("guided_paste_autofill_checkbox")
                        )
                        Text(
                            text = "Enable Discrete Auto-Fill → ${activeEntry.resolvedTarget().label}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isError) BridgeAmber.copy(alpha = 0.14f) else VerifiedEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (isError) BridgeAmber.copy(alpha = 0.5f) else VerifiedEmerald.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Outlined.Lock else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) BridgeAmber else VerifiedEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val raw = readClipboardPlainText(context)
                    val sanitized = EnvFileCodec.sanitizeClipboardValue(raw.orEmpty(), activeEntry.key)
                    if (sanitized.isBlank()) {
                        isError = true
                        statusMessage = "Clipboard is empty — copy your $friendlyItemType first, then tap Paste again."
                    } else {
                        val updated = activeEntry.copy(
                            value = sanitized,
                            autoFillEnabled = if (isKnownAutoFillSlot) enableAutoFillOnPaste else activeEntry.autoFillEnabled,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        onSaveEntry(updated)
                        if (clearClipboardAfterPaste) {
                            clearDeviceClipboard(context)
                        }
                        if (currentIndex + 1 < queue.size) {
                            currentIndex++
                        } else {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("guided_paste_confirm_button")
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste & Format Safely")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (queue.size > 1 && currentIndex + 1 < queue.size) {
                    TextButton(
                        onClick = { currentIndex++ },
                        modifier = Modifier.testTag("guided_paste_skip_button")
                    ) {
                        Text("Skip")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

/**
 * Novice-friendly wizard for creating a paired `SERVICE_USERNAME` and `SERVICE_PASSWORD`
 * in the encrypted `.env` vault via guided clipboard paste, with zero raw syntax editing.
 */
@Composable
fun GuidedLoginPairDialog(
    onDismiss: () -> Unit,
    onSavePair: (usernameEntry: EnvSecretEntry, passwordEntry: EnvSecretEntry) -> Unit
) {
    val context = LocalContext.current
    var serviceName by remember { mutableStateOf("") }
    var usernameValue by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }
    var clearClipboardAfterPaste by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val sanitizedPrefix = remember(serviceName) {
        val raw = EnvFileCodec.sanitizeKey(serviceName.ifBlank { "SERVICE" })
        raw.removeSuffix("_USERNAME").removeSuffix("_PASSWORD").ifBlank { "SERVICE" }
    }
    val userKey = "${sanitizedPrefix}_USERNAME"
    val passKey = "${sanitizedPrefix}_PASSWORD"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Guided Login Pair (.env)")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Name the service, then copy & paste your username and password one at a time. Vortex formats both .env keys cleanly and locks them to Human Only (No AI Access).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Service Name (e.g., GitHub, Supabase, Jira)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guided_login_service_input")
                )

                Text(
                    text = "Will create: $userKey & $passKey",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )

                // Step 1: Username
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (usernameValue.isNotBlank()) VerifiedEmerald.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. Copy Username / Email, then paste into $userKey",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (usernameValue.isNotBlank()) {
                            val masked = EnvSecretEntry(key = userKey, value = usernameValue).maskedValue()
                            Text(
                                text = "Captured: $masked",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = VerifiedEmerald
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val pasted = EnvFileCodec.sanitizeClipboardValue(
                                    readClipboardPlainText(context).orEmpty(),
                                    userKey
                                )
                                if (pasted.isBlank()) {
                                    statusText = "Clipboard is empty — copy your username/email first."
                                } else {
                                    usernameValue = pasted
                                    if (clearClipboardAfterPaste) clearDeviceClipboard(context)
                                    statusText = "Username captured cleanly. Now copy your password for Step 2."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guided_login_paste_username_button")
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (usernameValue.isBlank()) "Paste Username from Clipboard" else "Re-Paste Username")
                        }
                    }
                }

                // Step 2: Password
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (passwordValue.isNotBlank()) VerifiedEmerald.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "2. Copy Password, then paste into $passKey",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (passwordValue.isNotBlank()) {
                            Text(
                                text = "Captured: •••••••• (masked & ready)",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = VerifiedEmerald
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val pasted = EnvFileCodec.sanitizeClipboardValue(
                                    readClipboardPlainText(context).orEmpty(),
                                    passKey
                                )
                                if (pasted.isBlank()) {
                                    statusText = "Clipboard is empty — copy your password first."
                                } else {
                                    passwordValue = pasted
                                    if (clearClipboardAfterPaste) clearDeviceClipboard(context)
                                    statusText = "Password captured & masked safely. Tap 'Save Login Pair' below."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guided_login_paste_password_button")
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (passwordValue.isBlank()) "Paste Password from Clipboard" else "Re-Paste Password")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearClipboardAfterPaste = !clearClipboardAfterPaste },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = clearClipboardAfterPaste,
                        onCheckedChange = { clearClipboardAfterPaste = it }
                    )
                    Text(
                        text = "Clear clipboard after each paste (Recommended)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                statusText?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val label = serviceName.trim().ifBlank { "Service" }
                    val userEntry = EnvSecretEntry(
                        id = UUID.randomUUID().toString(),
                        key = userKey,
                        value = usernameValue,
                        category = EnvSecretCategory.LOGIN,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.CUSTOM_CREDENTIAL,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "$label login username"
                    )
                    val passEntry = EnvSecretEntry(
                        id = UUID.randomUUID().toString(),
                        key = passKey,
                        value = passwordValue,
                        category = EnvSecretCategory.LOGIN,
                        autoFillEnabled = false,
                        autoFillTarget = EnvAutoFillTarget.CUSTOM_CREDENTIAL,
                        permissions = ModelSecretPermissions(noAccessHumanOnly = true),
                        note = "$label login password"
                    )
                    onSavePair(userEntry, passEntry)
                },
                enabled = serviceName.isNotBlank() && (usernameValue.isNotBlank() || passwordValue.isNotBlank()),
                modifier = Modifier.testTag("guided_login_save_pair_button")
            ) {
                Text("Save Login Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
