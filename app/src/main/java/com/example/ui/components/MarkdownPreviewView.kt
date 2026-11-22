package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SyntaxAnnotation
import com.example.ui.theme.SyntaxProperty

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val marker: String, val text: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class CodeFence(val language: String, val code: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

@Composable
fun MarkdownPreviewView(
    markdown: String,
    modifier: Modifier = Modifier,
    maxBlocks: Int = Int.MAX_VALUE,
    onCopyCode: ((String) -> Unit)? = null
) {
    val blocks = remember(markdown, maxBlocks) {
        parseMarkdownBlocks(markdown).take(maxBlocks)
    }

    SelectionContainer(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        val color = if (block.level <= 2) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = parseInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                            style = style,
                            color = color
                        )
                        if (block.level == 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = parseInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = block.marker,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = parseInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownBlock.Blockquote -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = parseInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    is MarkdownBlock.CodeFence -> {
                        val isMermaid = block.language.equals("mermaid", ignoreCase = true) ||
                            block.language.equals("mmd", ignoreCase = true)
                        val specializedKind = remember(block.language, block.code) {
                            if (isMermaid) null else SpecializedFormatParser.detectKind(block.language, block.code)
                        }
                        if (isMermaid && block.code.isNotBlank()) {
                            DisableSelection {
                                MermaidDiagramCard(
                                    code = block.code,
                                    title = MermaidDiagramEngine.detectDiagramType(block.code),
                                    canvasHeight = 270.dp,
                                    onCopyCode = onCopyCode
                                )
                            }
                        } else if (specializedKind != null && block.code.isNotBlank()) {
                            DisableSelection {
                                SpecializedFormatCard(
                                    kind = specializedKind,
                                    code = block.code,
                                    title = specializedKind.displayName,
                                    sourceLanguage = block.language.ifBlank { specializedKind.defaultExtension },
                                    canvasHeight = 270.dp
                                )
                            }
                        } else {
                            val highlighted = remember(block.code, block.language) {
                                CodeSyntaxHighlighter.highlight(block.code, block.language.ifBlank { "txt" })
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianSurfaceElevated)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = block.language.ifBlank { "code" }.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = SyntaxAnnotation,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (onCopyCode != null) {
                                            IconButton(
                                                onClick = { onCopyCode(block.code) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = stringResource(R.string.cd_copy_text),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = highlighted,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is MarkdownBlock.Table -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(ObsidianSurfaceElevated)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    block.headers.forEach { header ->
                                        Text(
                                            text = header,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.widthIn(min = 96.dp, max = 220.dp)
                                        )
                                    }
                                }
                                block.rows.forEach { row ->
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        block.headers.indices.forEach { colIdx ->
                                            val cell = row.getOrElse(colIdx) { "" }
                                            Text(
                                                text = parseInlineMarkdown(cell, MaterialTheme.colorScheme.primary),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.widthIn(min = 96.dp, max = 220.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").lines()
    val result = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // consume closing ```
            result.add(MarkdownBlock.CodeFence(language = lang, code = codeLines.joinToString("\n")))
            continue
        }

        // Display LaTeX math block ($$ ... $$)
        if (trimmed.startsWith("$$")) {
            if (trimmed.length > 4 && trimmed.endsWith("$$")) {
                val singleLineExpr = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                if (singleLineExpr.isNotBlank()) {
                    result.add(MarkdownBlock.CodeFence(language = "latex", code = singleLineExpr))
                    i++
                    continue
                }
            } else {
                val mathLines = mutableListOf<String>()
                val remainder = trimmed.removePrefix("$$").trim()
                if (remainder.isNotBlank()) mathLines.add(remainder)
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val closingPart = lines[i].trim().removeSuffix("$$").trim()
                    if (closingPart.isNotBlank()) mathLines.add(closingPart)
                    i++
                }
                val combined = mathLines.joinToString("\n").trim()
                if (combined.isNotBlank()) {
                    result.add(MarkdownBlock.CodeFence(language = "latex", code = combined))
                }
                continue
            }
        }

        // Horizontal rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            result.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Heading
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
            val headingText = trimmed.drop(level).trim()
            if (headingText.isNotBlank()) {
                result.add(MarkdownBlock.Heading(level, headingText))
                i++
                continue
            }
        }

        // Markdown Table
        if (trimmed.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val headers = splitTableRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(splitTableRow(lines[i].trim()))
                i++
            }
            result.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            result.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // Bullet or numbered list item
        val orderedMatch = Regex("^(\\d+\\.)\\s+(.*)$").find(trimmed)
        if (orderedMatch != null) {
            result.add(MarkdownBlock.BulletItem(orderedMatch.groupValues[1], orderedMatch.groupValues[2]))
            i++
            continue
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
            result.add(MarkdownBlock.BulletItem("•", trimmed.drop(2).trim()))
            i++
            continue
        }

        // Standard paragraph (coalesce consecutive plain lines)
        val para = mutableListOf(trimmed)
        i++
        while (i < lines.size) {
            val next = lines[i].trim()
            if (next.isEmpty() || next.startsWith("#") || next.startsWith("```") ||
                next.startsWith(">") || next.startsWith("- ") || next.startsWith("* ") ||
                next.startsWith("|") || next == "---" || Regex("^\\d+\\.\\s+").containsMatchIn(next)
            ) {
                break
            }
            para.add(next)
            i++
        }
        result.add(MarkdownBlock.Paragraph(para.joinToString(" ")))
    }
    return result
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("|") && t.contains("---")
}

private fun splitTableRow(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun parseInlineMarkdown(text: String, accentColor: Color): AnnotatedString {
    val len = text.length
    return buildAnnotatedString {
        var i = 0
        while (i < len) {
            // Bold **...**
            if (text.startsWith("**", i)) {
                val close = text.indexOf("**", i + 2)
                if (close != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, close))
                    pop()
                    i = close + 2
                    continue
                }
            }
            // Inline code `...`
            if (text[i] == '`') {
                val close = text.indexOf('`', i + 1)
                if (close != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            color = SyntaxProperty,
                            background = ObsidianSurfaceElevated
                        )
                    )
                    append(" ${text.substring(i + 1, close)} ")
                    pop()
                    i = close + 1
                    continue
                }
            }
            // Markdown link [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        pushStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(text.substring(i + 1, closeBracket))
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }
            // Italic *...*
            if (text[i] == '*' && (i + 1 < len && text[i + 1] != ' ')) {
                val close = text.indexOf('*', i + 1)
                if (close != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, close))
                    pop()
                    i = close + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
