package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.docs.VortexNextStepsDocProvider
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VerifiedEmerald

@Composable
fun NextStepsHandoffDialog(
    activeProjectName: String?,
    githubConnected: Boolean,
    onDismiss: () -> Unit,
    onExportMarkdownToUri: (Uri, String) -> Unit,
    onAddToProjectAndMirrorGitHub: () -> Unit,
    onToast: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var selectedSectionId by remember { mutableStateOf<String?>(null) } // null = Full Document

    val fullMarkdown = remember { VortexNextStepsDocProvider.fullMarkdown() }
    val visibleMarkdown = remember(selectedSectionId) {
        if (selectedSectionId == null) {
            fullMarkdown
        } else {
            VortexNextStepsDocProvider.sections.firstOrNull { it.id == selectedSectionId }?.markdown
                ?: fullMarkdown
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            onExportMarkdownToUri(uri, fullMarkdown)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("next_steps_handoff_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = VerifiedEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = VortexNextStepsDocProvider.CANONICAL_DOC_TITLE,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = VortexNextStepsDocProvider.CANONICAL_REPO_PATH,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("next_steps_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close)
                        )
                    }
                }

                // Section Jump Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all_sections") {
                        FilterChip(
                            selected = selectedSectionId == null,
                            onClick = { selectedSectionId = null },
                            label = { Text("Full Doc") },
                            modifier = Modifier.testTag("next_steps_chip_all")
                        )
                    }
                    items(VortexNextStepsDocProvider.sections, key = { it.id }) { section ->
                        FilterChip(
                            selected = selectedSectionId == section.id,
                            onClick = { selectedSectionId = section.id },
                            label = { Text(section.chipLabel) },
                            modifier = Modifier.testTag("next_steps_chip_${section.id}")
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                // Rendered Document Body
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "action_card") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Collaborator Handoff & GitHub Mirror",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = VerifiedEmerald
                                )
                                Text(
                                    text = if (!activeProjectName.isNullOrBlank()) {
                                        val ghStatus = if (githubConnected) "GitHub linked — will save & mirror now." else "Will save into '$activeProjectName' ready for GitHub push."
                                        "Active Project: $activeProjectName · $ghStatus"
                                    } else {
                                        "Open or create a project workspace to inject ${VortexNextStepsDocProvider.CANONICAL_REPO_PATH} directly into its GitHub repository."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onAddToProjectAndMirrorGitHub,
                                    enabled = !activeProjectName.isNullOrBlank(),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("next_steps_mirror_github_btn")
                                ) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (githubConnected) "Save to Project & Mirror to GitHub"
                                        else "Add to Active Project (Root)"
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(fullMarkdown))
                                            onToast("Copied '${VortexNextStepsDocProvider.CANONICAL_DOC_TITLE}' Markdown.")
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("next_steps_copy_btn")
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy .md")
                                    }
                                    OutlinedButton(
                                        onClick = { exportDocLauncher.launch(VortexNextStepsDocProvider.CANONICAL_FILE_NAME) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("next_steps_export_btn")
                                    ) {
                                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export .md")
                                    }
                                }
                            }
                        }
                    }

                    item(key = "markdown_content") {
                        MarkdownPreviewView(
                            markdown = visibleMarkdown,
                            onCopyCode = { code ->
                                clipboard.setText(AnnotatedString(code))
                                onToast("Copied snippet to clipboard.")
                            }
                        )
                    }
                }
            }
        }
    }
}
