package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.ui.theme.BridgeAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.VerifiedEmerald

enum class GeminiComparisonSource(val label: String) {
    AUDIT_VERDICT("Gemini Audit"),
    OUTBOUND_RELAY("Outbound Relay"),
    PROJECT_SYNTHESIS("7-Stage Synthesis"),
    CROSS_MODEL_SYNTHESIS("Cross-Model")
}

data class ComparisonSection(
    val id: String,
    val title: String,
    val body: String
)

/**
 * Splits a Markdown/prose or source-code document into logical, individually copyable sections
 * so users can copy any specific heading, argument, or code block with one tap.
 */
fun splitIntoCopyableSections(
    rawText: String,
    fallbackTitle: String,
    isCodeFile: Boolean
): List<ComparisonSection> {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return emptyList()

    if (isCodeFile) {
        val lines = trimmed.replace("\r\n", "\n").lines()
        if (lines.size <= 35) {
            return listOf(ComparisonSection("code_0", fallbackTitle, trimmed))
        }
        val chunks = mutableListOf<ComparisonSection>()
        val currentTitle = StringBuilder(fallbackTitle)
        val buffer = mutableListOf<String>()
        val boundaryRegex = Regex(
            "^\\s*(pub\\s+)?(async\\s+)?(def |class |fn |impl |struct |enum |trait |function |export |interface |type |fun |const\\s+\\w+\\s*=\\s*\\()"
        )
        lines.forEach { line ->
            if (buffer.size >= 12 && boundaryRegex.containsMatchIn(line)) {
                chunks.add(
                    ComparisonSection(
                        id = "code_${chunks.size}",
                        title = currentTitle.toString(),
                        body = buffer.joinToString("\n").trimEnd()
                    )
                )
                buffer.clear()
                currentTitle.clear().append(line.trim().take(46))
            }
            buffer.add(line)
        }
        if (buffer.isNotEmpty()) {
            chunks.add(
                ComparisonSection(
                    id = "code_${chunks.size}",
                    title = if (chunks.isEmpty()) fallbackTitle else currentTitle.toString(),
                    body = buffer.joinToString("\n").trimEnd()
                )
            )
        }
        return chunks
    }

    // Split Markdown/text by headings (# / ## / ### / **1. ...**)
    val lines = trimmed.replace("\r\n", "\n").lines()
    val sections = mutableListOf<ComparisonSection>()
    var activeHeading = fallbackTitle
    val currentLines = mutableListOf<String>()
    var inFence = false

    fun flush() {
        val body = currentLines.joinToString("\n").trim()
        if (body.isNotBlank()) {
            sections.add(
                ComparisonSection(
                    id = "sec_${sections.size}",
                    title = activeHeading,
                    body = body
                )
            )
        }
        currentLines.clear()
    }

    for (line in lines) {
        val t = line.trim()
        if (t.startsWith("```")) inFence = !inFence
        if (!inFence && t.startsWith("#")) {
            val heading = t.trimStart('#').trim()
            if (heading.isNotBlank()) {
                flush()
                activeHeading = heading
                continue
            }
        }
        currentLines.add(line)
    }
    flush()

    // If the entire response had no headings, split long prose by double-newlines into scannable blocks
    if (sections.size <= 1 && trimmed.length > 480) {
        val paragraphs = trimmed.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (paragraphs.size >= 2) {
            return paragraphs.mapIndexed { idx, para ->
                ComparisonSection(
                    id = "para_$idx",
                    title = "$fallbackTitle · Part ${idx + 1}",
                    body = para
                )
            }
        }
    }

    return sections.ifEmpty { listOf(ComparisonSection("sec_0", fallbackTitle, trimmed)) }
}

private fun resolveGeminiComparisonText(
    source: GeminiComparisonSource,
    project: ResearchProject,
    selectedItem: RoundtableItem,
    crossModelSynthesis: String
): Pair<String, String> = when (source) {
    GeminiComparisonSource.AUDIT_VERDICT -> {
        val modelTag = selectedItem.scrutinizedByModel.ifBlank { "Gemini" }
        val text = selectedItem.scrutinyVerdict.ifBlank {
            "This artifact has not been audited yet. Tap 'Scrutinize This File' or switch to '7-Stage Synthesis' to compare against Gemini's baseline synthesis."
        }
        "$modelTag Audit" to text
    }

    GeminiComparisonSource.OUTBOUND_RELAY -> {
        val text = selectedItem.outboundRelayPrompt.ifBlank {
            "No Outbound Relay generated yet. Run Gemini scrutiny on ${selectedItem.fileName} to generate a structured counter-argument and follow-up questions."
        }
        "Gemini Outbound Relay" to text
    }

    GeminiComparisonSource.PROJECT_SYNTHESIS -> {
        val text = project.chatAnswer.ifBlank {
            project.parsedStages()
                .firstOrNull { it.stageNumber == 4 && it.content.isNotBlank() }
                ?.content
                ?: project.parsedFiles().firstOrNull()?.content.orEmpty()
        }
        "Gemini 7-Stage Synthesis" to text.ifBlank { "Run the 7-stage research pipeline to populate Gemini's synthesis." }
    }

    GeminiComparisonSource.CROSS_MODEL_SYNTHESIS -> {
        "Gemini Cross-Model Scrutiny" to crossModelSynthesis.ifBlank {
            "Upload files into 2+ model sub-folders and run Cross-Model Scrutiny to generate a unified multi-model comparison."
        }
    }
}

private fun buildRefinementPromptPacket(
    projectTitle: String,
    geminiLabel: String,
    geminiBody: String,
    externalItem: RoundtableItem
): String = buildString {
    appendLine("# Cross-Model Refinement Request — $projectTitle")
    appendLine()
    appendLine("## 1. $geminiLabel")
    appendLine(geminiBody.trim())
    appendLine()
    appendLine("## 2. External Model Output (${externalItem.modelFolder} / ${externalItem.fileName})")
    appendLine(externalItem.content.trim())
    appendLine()
    appendLine("## Refinement Instructions")
    appendLine("Reconcile the differences between Section 1 and Section 2 using verifiable primary evidence, fix any flaws or edge cases, and output the improved unified specification or code.")
}

/**
 * Inline Side-by-Side Comparison Card embedded in A.i. Roundtable, giving instant access to
 * side-by-side Gemini vs. External Model inspection with per-section copy buttons and full-screen expand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundtableSideBySideCard(
    project: ResearchProject,
    items: List<RoundtableItem>,
    crossModelSynthesis: String,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    onOpenFullScreen: (RoundtableItem, GeminiComparisonSource) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val activeItem = items.firstOrNull { it.id == selectedItemId } ?: items.firstOrNull() ?: return

    var geminiSource by remember(activeItem.id, activeItem.scrutinyVerdict.isNotBlank()) {
        mutableStateOf(
            if (activeItem.scrutinyVerdict.isNotBlank()) GeminiComparisonSource.AUDIT_VERDICT
            else GeminiComparisonSource.PROJECT_SYNTHESIS
        )
    }

    val (geminiHeading, geminiText) = remember(geminiSource, project.chatAnswer, activeItem, crossModelSynthesis) {
        resolveGeminiComparisonText(geminiSource, project, activeItem, crossModelSynthesis)
    }
    val geminiSections = remember(geminiText, geminiHeading) {
        splitIntoCopyableSections(geminiText, geminiHeading, isCodeFile = false)
    }
    val externalIsCode = remember(activeItem.extension) {
        CodeSyntaxHighlighter.classifyExtension(activeItem.extension) in listOf(
            ArtifactRenderKind.CODE,
            ArtifactRenderKind.STRUCTURED_DATA
        )
    }
    val externalSections = remember(activeItem.content, activeItem.fileName, externalIsCode) {
        splitIntoCopyableSections(activeItem.content, activeItem.fileName, isCodeFile = externalIsCode)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("roundtable_side_by_side_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Compare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.roundtable_side_by_side_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Gemini vs. External Model · Copy any section for manual refinement",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onOpenFullScreen(activeItem, geminiSource) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("expand_side_by_side_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = stringResource(R.string.cd_expand_side_by_side),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // External artifact quick selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { candidate ->
                    FilterChip(
                        selected = candidate.id == activeItem.id,
                        onClick = { onSelectItem(candidate.id) },
                        label = { Text("${candidate.modelFolder} / ${candidate.fileName}") },
                        modifier = Modifier.testTag("compare_pick_${candidate.fileName.lowercase()}")
                    )
                }
            }

            // Gemini source selector
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GeminiComparisonSource.entries.forEach { src ->
                    FilterChip(
                        selected = geminiSource == src,
                        onClick = { geminiSource = src },
                        label = { Text(src.label) },
                        modifier = Modifier.testTag("compare_gemini_src_${src.name.lowercase()}")
                    )
                }
            }

            // Bounded Side-by-Side Split Viewport
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ComparisonColumnPane(
                        headerTitle = geminiHeading,
                        headerBadge = "Gemini",
                        accentColor = MaterialTheme.colorScheme.primary,
                        isCode = false,
                        fileExtension = "md",
                        sections = geminiSections,
                        fullText = geminiText,
                        paneTag = "gemini",
                        onCopyText = { label, body ->
                            copyToClipboard(context, label, body)
                            onToast("Copied $label")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    ComparisonColumnPane(
                        headerTitle = activeItem.fileName,
                        headerBadge = activeItem.modelFolder,
                        accentColor = VerifiedEmerald,
                        isCode = externalIsCode,
                        fileExtension = activeItem.extension,
                        sections = externalSections,
                        fullText = activeItem.content,
                        paneTag = "external",
                        onCopyText = { label, body ->
                            copyToClipboard(context, label, body)
                            onToast("Copied $label")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Refinement Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val packet = buildRefinementPromptPacket(
                            projectTitle = project.title,
                            geminiLabel = geminiHeading,
                            geminiBody = geminiText,
                            externalItem = activeItem
                        )
                        copyToClipboard(context, "Refinement Packet", packet)
                        onToast("Copied combined Gemini + ${activeItem.modelFolder} refinement packet!")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_refinement_packet_button")
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Refinement Packet")
                }
                Button(
                    onClick = { onOpenFullScreen(activeItem, geminiSource) },
                    modifier = Modifier.testTag("open_full_comparison_button")
                ) {
                    Icon(Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Full Split")
                }
            }
        }
    }
}

/**
 * Full-Screen Side-by-Side Comparison Workbench Dialog for deep cross-model inspection & refinement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoundtableSideBySideDialog(
    project: ResearchProject,
    items: List<RoundtableItem>,
    crossModelSynthesis: String,
    initialItemId: String,
    initialGeminiSource: GeminiComparisonSource,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedItemId by remember(initialItemId) { mutableStateOf(initialItemId) }
    var geminiSource by remember(initialGeminiSource) { mutableStateOf(initialGeminiSource) }
    var splitColumns by remember { mutableStateOf(true) }

    val activeItem = items.firstOrNull { it.id == selectedItemId } ?: items.firstOrNull() ?: return
    val (geminiHeading, geminiText) = remember(geminiSource, project.chatAnswer, activeItem, crossModelSynthesis) {
        resolveGeminiComparisonText(geminiSource, project, activeItem, crossModelSynthesis)
    }
    val geminiSections = remember(geminiText, geminiHeading) {
        splitIntoCopyableSections(geminiText, geminiHeading, isCodeFile = false)
    }
    val externalIsCode = remember(activeItem.extension) {
        CodeSyntaxHighlighter.classifyExtension(activeItem.extension) in listOf(
            ArtifactRenderKind.CODE,
            ArtifactRenderKind.STRUCTURED_DATA
        )
    }
    val externalSections = remember(activeItem.content, activeItem.fileName, externalIsCode) {
        splitIntoCopyableSections(activeItem.content, activeItem.fileName, isCodeFile = externalIsCode)
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
                                    .testTag("close_side_by_side_dialog")
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.roundtable_side_by_side_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "$geminiHeading  ⇄  ${activeItem.modelFolder} / ${activeItem.fileName}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { splitColumns = !splitColumns },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("toggle_comparison_layout_button")
                            ) {
                                Icon(
                                    imageVector = if (splitColumns) Icons.Outlined.ViewColumn else Icons.Outlined.ViewAgenda,
                                    contentDescription = stringResource(R.string.cd_toggle_split_layout),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val packet = buildRefinementPromptPacket(
                                        projectTitle = project.title,
                                        geminiLabel = geminiHeading,
                                        geminiBody = geminiText,
                                        externalItem = activeItem
                                    )
                                    copyToClipboard(context, "Cross-Model Refinement Packet", packet)
                                    onToast("Copied full side-by-side refinement packet.")
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dialog_copy_both_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_refinement_packet),
                                    tint = VerifiedEmerald
                                )
                            }
                        }

                        // Artifact & Gemini Source selectors
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items, key = { it.id }) { candidate ->
                                FilterChip(
                                    selected = candidate.id == activeItem.id,
                                    onClick = { selectedItemId = candidate.id },
                                    label = { Text("${candidate.modelFolder}: ${candidate.fileName}") }
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GeminiComparisonSource.entries.forEach { src ->
                                FilterChip(
                                    selected = geminiSource == src,
                                    onClick = { geminiSource = src },
                                    label = { Text(src.label) }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (splitColumns) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ComparisonColumnPane(
                        headerTitle = geminiHeading,
                        headerBadge = "Gemini",
                        accentColor = MaterialTheme.colorScheme.primary,
                        isCode = false,
                        fileExtension = "md",
                        sections = geminiSections,
                        fullText = geminiText,
                        paneTag = "full_gemini",
                        onCopyText = { label, body ->
                            copyToClipboard(context, label, body)
                            onToast("Copied $label")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    ComparisonColumnPane(
                        headerTitle = activeItem.fileName,
                        headerBadge = activeItem.modelFolder,
                        accentColor = VerifiedEmerald,
                        isCode = externalIsCode,
                        fileExtension = activeItem.extension,
                        sections = externalSections,
                        fullText = activeItem.content,
                        paneTag = "full_external",
                        onCopyText = { label, body ->
                            copyToClipboard(context, label, body)
                            onToast("Copied $label")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ComparisonColumnPane(
                        headerTitle = geminiHeading,
                        headerBadge = "Gemini",
                        accentColor = MaterialTheme.colorScheme.primary,
                        isCode = false,
                        fileExtension = "md",
                        sections = geminiSections,
                        fullText = geminiText,
                        paneTag = "stack_gemini",
                        onCopyText = { label, body ->
                            copyToClipboard(context, label, body)
                            onToast("Copied $label")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ComparisonColumnPane(
                        headerTitle = activeItem.fileName,
                        headerBadge = activeItem.modelFolder,
                        accentColor = VerifiedEmerald,
                        isCode = externalIsCode,
                        fileExtension = activeItem.extension,
                        sections = externalSections,
                        fullText = activeItem.content,
                        paneTag = "stack_external",
                        onCopyText = { label, body ->
                            copyToClipboard(context, label, body)
                            onToast("Copied $label")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonColumnPane(
    headerTitle: String,
    headerBadge: String,
    accentColor: Color,
    isCode: Boolean,
    fileExtension: String,
    sections: List<ComparisonSection>,
    fullText: String,
    paneTag: String,
    onCopyText: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Sticky Pane Header with Pane-Wide Copy Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurfaceElevated)
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (headerBadge == "Gemini") Icons.Outlined.AutoAwesome else Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerBadge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { onCopyText("all of $headerBadge ($headerTitle)", fullText) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("copy_pane_$paneTag")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_section),
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Independently scrollable list of individually copyable sections
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sections.isEmpty()) {
                Text(
                    text = "No content available yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                sections.forEachIndexed { idx, section ->
                    ComparisonSectionCard(
                        section = section,
                        accentColor = accentColor,
                        isCode = isCode,
                        fileExtension = fileExtension,
                        testTagName = "copy_section_${paneTag}_$idx",
                        onCopy = { onCopyText("$headerBadge · ${section.title}", section.body) },
                        onCopyCodeSnippet = { snippet -> onCopyText("code block from $headerBadge", snippet) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonSectionCard(
    section: ComparisonSection,
    accentColor: Color,
    isCode: Boolean,
    fileExtension: String,
    testTagName: String,
    onCopy: () -> Unit,
    onCopyCodeSnippet: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.08f))
                    .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(testTagName)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_section),
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isCode) {
                    SyntaxHighlightedCodePane(
                        code = section.body,
                        extension = fileExtension,
                        wordWrap = true
                    )
                } else {
                    MarkdownPreviewView(
                        markdown = section.body,
                        onCopyCode = onCopyCodeSnippet
                    )
                }
            }
        }
    }
}
