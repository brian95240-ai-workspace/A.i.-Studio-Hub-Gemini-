package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.RoundtableItem
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.VerifiedEmerald

private enum class ViewerMode { PREVIEW, EDIT }

/**
 * Compact inline renderer embedded directly inside Roundtable artifact cards:
 * - Renders `.md` / `.markdown` / extracted `.pdf` using the native Markdown previewer.
 * - Renders `.py`, `.js`, `.ts`, `.rs`, and other source/config files with line-numbered syntax highlighting.
 */
@Composable
fun UnifiedArtifactInlinePreview(
    item: RoundtableItem,
    canEdit: Boolean,
    onOpenFullViewer: (startInEditMode: Boolean) -> Unit,
    onCopyCode: (String) -> Unit
) {
    val renderKind = remember(item.extension) {
        CodeSyntaxHighlighter.classifyExtension(item.extension)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unified_preview_${item.fileName.lowercase()}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact Renderer Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (renderKind) {
                        ArtifactRenderKind.MARKDOWN -> Icons.Outlined.Description
                        ArtifactRenderKind.MERMAID -> Icons.Outlined.AccountTree
                        else -> Icons.Outlined.Code
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (renderKind) {
                        ArtifactRenderKind.MARKDOWN -> "Markdown Preview"
                        ArtifactRenderKind.MERMAID -> "Mermaid.js Diagram"
                        ArtifactRenderKind.LATEX -> "LaTeX / KaTeX Renderer"
                        ArtifactRenderKind.CYPHER -> "Cypher / Apache AGE Graph"
                        ArtifactRenderKind.GRAPH_DATA -> "Interactive Graph (${item.detectedLanguage})"
                        ArtifactRenderKind.GEOSPATIAL -> "PostGIS Geospatial Map"
                        ArtifactRenderKind.POSTGRES_SQL -> "PostgreSQL / PostGIS / AGE"
                        ArtifactRenderKind.CODE -> "${item.detectedLanguage} · Syntax Highlighted"
                        ArtifactRenderKind.STRUCTURED_DATA -> "${item.detectedLanguage} · Structured View"
                        ArtifactRenderKind.RESEARCH_DOC -> "${item.detectedLanguage} · Research Spec"
                        ArtifactRenderKind.PLAIN_TEXT -> "Text Preview"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onOpenFullViewer(false) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("open_full_viewer_${item.fileName.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = stringResource(R.string.cd_open_unified_viewer),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Box(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                val specializedKind = remember(item.extension, item.content) {
                    if (renderKind == ArtifactRenderKind.MERMAID || renderKind == ArtifactRenderKind.MARKDOWN) null
                    else SpecializedFormatParser.detectKind(item.extension, item.content)
                }
                when {
                    renderKind == ArtifactRenderKind.MARKDOWN -> {
                        MarkdownPreviewView(
                            markdown = item.content.take(2400),
                            maxBlocks = 8,
                            onCopyCode = onCopyCode
                        )
                    }
                    renderKind == ArtifactRenderKind.MERMAID -> {
                        MermaidDiagramCard(
                            code = item.content,
                            title = item.fileName,
                            canvasHeight = 250.dp,
                            onCopyCode = onCopyCode,
                            onExpandStudio = { onOpenFullViewer(false) }
                        )
                    }
                    specializedKind != null -> {
                        SpecializedFormatCard(
                            kind = specializedKind,
                            code = item.content,
                            title = item.fileName,
                            sourceLanguage = item.extension.ifBlank { specializedKind.defaultExtension },
                            canvasHeight = 250.dp
                        )
                    }
                    else -> {
                        val previewLines = remember(item.content) {
                            val lines = item.content.replace("\r\n", "\n").lines()
                            if (lines.size > 14) lines.take(14).joinToString("\n") + "\n// … (${lines.size - 14} more lines)"
                            else item.content
                        }
                        SyntaxHighlightedCodePane(
                            code = previewLines,
                            extension = item.extension,
                            wordWrap = false
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onOpenFullViewer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("viewer_preview_btn_${item.fileName.lowercase()}")
                ) {
                    Icon(Icons.Outlined.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (renderKind == ArtifactRenderKind.MARKDOWN) "Full Preview" else "Full Viewer")
                }
                if (canEdit) {
                    OutlinedButton(
                        onClick = { onOpenFullViewer(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("viewer_edit_btn_${item.fileName.lowercase()}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (renderKind == ArtifactRenderKind.CODE) "Code Editor" else "Edit File")
                    }
                }
            }
        }
    }
}

/**
 * Line-numbered, syntax-highlighted read-only code block used in both inline cards and full-screen viewer.
 */
@Composable
fun SyntaxHighlightedCodePane(
    code: String,
    extension: String,
    wordWrap: Boolean,
    modifier: Modifier = Modifier
) {
    val normalized = remember(code) { code.replace("\r\n", "\n") }
    val highlighted = remember(normalized, extension) {
        CodeSyntaxHighlighter.highlight(normalized, extension)
    }
    val lineNumbers = remember(normalized) {
        val count = normalized.count { it == '\n' } + 1
        (1..count).joinToString("\n")
    }
    val codeTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = JetBrainsMonoFamily,
        lineHeight = 19.sp
    )

    Row(modifier = modifier.fillMaxWidth()) {
        // Line Number Gutter
        Text(
            text = lineNumbers,
            style = codeTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .padding(end = 10.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .then(if (!wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
        ) {
            SelectionContainer {
                Text(
                    text = highlighted,
                    style = codeTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = wordWrap
                )
            }
        }
    }
}

/**
 * Full-screen Unified Roundtable Artifact Viewer & Live Syntax-Highlighted Editor Modal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedArtifactViewerDialog(
    item: RoundtableItem,
    roundtableRoot: String,
    canEdit: Boolean,
    initialEditMode: Boolean,
    onDismiss: () -> Unit,
    onSaveContent: (updatedContent: String, requeueForScrutiny: Boolean) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val renderKind = remember(item.extension) {
        CodeSyntaxHighlighter.classifyExtension(item.extension)
    }
    var mode by remember(item.id, initialEditMode) {
        mutableStateOf(if (initialEditMode && canEdit) ViewerMode.EDIT else ViewerMode.PREVIEW)
    }
    var draftContent by remember(item.id, item.content) { mutableStateOf(item.content) }
    var wordWrap by remember { mutableStateOf(renderKind == ArtifactRenderKind.MARKDOWN || renderKind == ArtifactRenderKind.PLAIN_TEXT) }

    val hasChanges = draftContent != item.content
    val lineCount = remember(draftContent) { draftContent.count { it == '\n' } + 1 }
    val byteCount = remember(draftContent) { draftContent.toByteArray(Charsets.UTF_8).size }
    val visualTransformation = remember(item.extension) {
        SyntaxHighlightVisualTransformation(item.extension)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("close_unified_viewer_button")
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$roundtableRoot/${item.modelFolder}/ · ${item.detectedLanguage} · $lineCount lines · ${byteCount / 1024.coerceAtLeast(1)} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { wordWrap = !wordWrap },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("toggle_word_wrap_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WrapText,
                                    contentDescription = stringResource(R.string.cd_toggle_word_wrap),
                                    tint = if (wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, item.fileName, draftContent)
                                    onToast("Copied ${item.fileName}")
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("copy_viewer_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_text),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Mode Switcher Bar (Preview / Code View vs Live Syntax-Highlighted Editor)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            FilterChip(
                                selected = mode == ViewerMode.PREVIEW,
                                onClick = { mode = ViewerMode.PREVIEW },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (renderKind) {
                                            ArtifactRenderKind.MARKDOWN -> Icons.Outlined.Preview
                                            ArtifactRenderKind.MERMAID -> Icons.Outlined.AccountTree
                                            else -> Icons.Outlined.Code
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        when (renderKind) {
                                            ArtifactRenderKind.MARKDOWN -> "Markdown Preview"
                                            ArtifactRenderKind.MERMAID -> "Diagram View"
                                            else -> "Syntax View"
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("unified_viewer_mode_preview")
                            )
                            if (canEdit) {
                                FilterChip(
                                    selected = mode == ViewerMode.EDIT,
                                    onClick = { mode = ViewerMode.EDIT },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = {
                                        Text(
                                            if (renderKind == ArtifactRenderKind.CODE) "Live Code Editor"
                                            else "Raw Editor"
                                        )
                                    },
                                    modifier = Modifier.testTag("unified_viewer_mode_edit")
                                )
                            }
                            TelemetryPill(
                                text = ".${item.extension.ifBlank { "txt" }} · ${renderKind.badgeLabel}",
                                accentColor = if (renderKind == ArtifactRenderKind.MARKDOWN) VerifiedEmerald else MaterialTheme.colorScheme.primary
                            )
                            if (hasChanges) {
                                TelemetryPill(text = "Unsaved Edits", accentColor = BridgeAmber)
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (canEdit && hasChanges) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSaveContent(draftContent, false)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_viewer_changes_button")
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                            Button(
                                onClick = {
                                    onSaveContent(draftContent, true)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("save_requeue_viewer_button")
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save & Re-Queue")
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (mode) {
                    ViewerMode.PREVIEW -> {
                        val specializedKind = remember(item.extension, draftContent) {
                            if (renderKind == ArtifactRenderKind.MERMAID || renderKind == ArtifactRenderKind.MARKDOWN) null
                            else SpecializedFormatParser.detectKind(item.extension, draftContent)
                        }
                        when {
                            renderKind == ArtifactRenderKind.MARKDOWN -> {
                                MarkdownPreviewView(
                                    markdown = draftContent,
                                    onCopyCode = { snippet ->
                                        copyToClipboard(context, "Code Snippet", snippet)
                                        onToast("Copied code block.")
                                    }
                                )
                            }
                            renderKind == ArtifactRenderKind.MERMAID -> {
                                MermaidDiagramCard(
                                    code = draftContent,
                                    title = item.fileName,
                                    subtitle = "Interactive Mermaid.js Canvas",
                                    canvasHeight = 440.dp,
                                    onCopyCode = { snippet ->
                                        copyToClipboard(context, item.fileName, snippet)
                                        onToast("Copied Mermaid diagram.")
                                    }
                                )
                            }
                            specializedKind != null -> {
                                SpecializedFormatCard(
                                    kind = specializedKind,
                                    code = draftContent,
                                    title = item.fileName,
                                    sourceLanguage = item.extension.ifBlank { specializedKind.defaultExtension },
                                    canvasHeight = 440.dp,
                                    onToast = onToast
                                )
                            }
                            else -> {
                                SyntaxHighlightedCodePane(
                                    code = draftContent,
                                    extension = item.extension,
                                    wordWrap = wordWrap
                                )
                            }
                        }
                    }

                    ViewerMode.EDIT -> {
                        val editorLines = remember(draftContent) {
                            val count = draftContent.count { it == '\n' } + 1
                            (1..count).joinToString("\n")
                        }
                        val editorStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = editorLines,
                                    style = editorStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .widthIn(min = 32.dp)
                                        .padding(end = 10.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (!wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                                ) {
                                    BasicTextField(
                                        value = draftContent,
                                        onValueChange = { draftContent = it },
                                        textStyle = editorStyle,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        visualTransformation = visualTransformation,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("unified_code_editor_field")
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
