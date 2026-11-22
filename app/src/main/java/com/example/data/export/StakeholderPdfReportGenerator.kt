package com.example.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.ScaffoldingBlueprint
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfReportOptions(
    val includeProjectTree: Boolean = true,
    val includeResearchStages: Boolean = true,
    val includePrds: Boolean = true,
    val includeRoundtable: Boolean = true,
    val includeFileAppendix: Boolean = true
)

/**
 * Native Android PdfDocument stakeholder report generator.
 * Renders an executive cover header, project KPI metrics, ASCII folder structure,
 * consolidated research notes, PRDs, Roundtable findings, and workspace file appendix
 * with automatic multi-page pagination, running headers, and page footers.
 */
object StakeholderPdfReportGenerator {

    private const val PAGE_WIDTH = 612   // US Letter 8.5in @ 72dpi
    private const val PAGE_HEIGHT = 792  // US Letter 11in @ 72dpi
    private const val MARGIN_X = 42f
    private const val TOP_CONTENT_Y = 56f
    private const val BOTTOM_LIMIT_Y = PAGE_HEIGHT - 50f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - (MARGIN_X * 2)).toInt()

    // Executive Slate & Emerald Palette
    private val COLOR_NAVY = Color.parseColor("#0F172A")
    private val COLOR_SLATE_DARK = Color.parseColor("#1E293B")
    private val COLOR_TEXT_PRIMARY = Color.parseColor("#111827")
    private val COLOR_TEXT_MUTED = Color.parseColor("#475569")
    private val COLOR_ACCENT_BLUE = Color.parseColor("#2563EB")
    private val COLOR_ACCENT_EMERALD = Color.parseColor("#059669")
    private val COLOR_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_CARD_BG = Color.parseColor("#F8FAFC")
    private val COLOR_CODE_BG = Color.parseColor("#F1F5F9")

    fun generatePdfBytes(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>,
        options: PdfReportOptions = PdfReportOptions()
    ): ByteArray {
        val document = PdfDocument()
        val generatedAt = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.US).format(Date())
        val reportTitle = project.title.ifBlank { project.rootFolderName.ifBlank { "Vortex Research Report" } }
        val stages = project.parsedStages()
        val blueprint = ScaffoldingBlueprint.fromWire(project.blueprintId)

        val painter = PdfPageWriter(document, reportTitle, generatedAt)
        try {
            // 1. Cover Header Banner on Page 1
            painter.drawCoverBanner(
                title = reportTitle,
                rootFolder = project.rootFolderName.ifBlank { "vortex-research" },
                domain = project.detectedDomain.ifBlank { "General Architecture & Research" },
                complexity = project.complexityScore,
                gapVerdict = project.gapVerdict,
                blueprintTitle = blueprint.title,
                generatedAt = generatedAt
            )

            // 2. KPI Summary Row
            painter.drawKpiRow(
                listOf(
                    "Workspace Files" to files.size.toString(),
                    "Research Stages" to "${stages.count { it.status == "COMPLETED" }}/${stages.size.coerceAtLeast(1)}",
                    "PRD Versions" to prds.size.toString(),
                    "Roundtable Notes" to roundtableItems.size.toString()
                )
            )

            // 3. Executive Research Brief
            if (project.query.isNotBlank()) {
                painter.drawSectionHeading("Executive Research Brief", "Problem Statement & Scope")
                painter.drawCalloutBox(project.query.trim())
            }

            // 4. Complete Project Structure Tree
            if (options.includeProjectTree) {
                painter.drawSectionHeading(
                    "Project Architecture & Folder Tree",
                    "${project.rootFolderName}/ (${files.size} files · ${blueprint.title})"
                )
                val asciiTree = UniversalFormatCatalog.buildAsciiProjectTree(project.rootFolderName, files)
                painter.drawMonospaceBlock(asciiTree)
            }

            // 5. Consolidated Research Notes (Chat synthesis + 7-Stage Pipeline)
            if (options.includeResearchStages) {
                if (project.chatAnswer.isNotBlank()) {
                    painter.drawSectionHeading("Consolidated Research Synthesis", "Formatted Output")
                    painter.drawMarkdownProse(project.chatAnswer)
                }
                if (stages.isNotEmpty()) {
                    painter.drawSectionHeading("7-Stage Empirical Research Notes", "Grounded Pipeline Telemetry")
                    stages.forEach { stage ->
                        val meta = buildString {
                            append("Status: ${stage.status}")
                            if (stage.modelUsed.isNotBlank()) append(" · Model: ${stage.modelUsed}")
                        }
                        painter.drawSubHeading("Stage ${stage.stageNumber}: ${stage.title}", meta)
                        val body = stage.content.ifBlank { stage.routingReason }
                        if (body.isNotBlank()) {
                            painter.drawMarkdownProse(body)
                        }
                    }
                }
            }

            // 6. Product Requirements Documents
            if (options.includePrds && prds.isNotEmpty()) {
                painter.drawSectionHeading("Product Requirements Documents", "${prds.size} versioned artifact(s)")
                prds.forEach { prd ->
                    painter.drawSubHeading(
                        "${prd.version} — ${prd.title}",
                        prd.deltaSummary.ifBlank { prd.revisionRequest.ifBlank { "Baseline specification" } }
                    )
                    painter.drawMarkdownProse(prd.markdownContent)
                }
            }

            // 7. A.i. Roundtable Cross-Model Scrutiny
            if (options.includeRoundtable && roundtableItems.isNotEmpty()) {
                painter.drawSectionHeading("A.i. Roundtable Scrutiny Findings", "${roundtableItems.size} cross-model artifact(s)")
                roundtableItems.forEach { item ->
                    painter.drawSubHeading(
                        "${item.modelFolder}/${item.fileName}",
                        "Verdict: ${item.queueStatus} · ${item.detectedLanguage}"
                    )
                    if (item.scrutinyVerdict.isNotBlank()) {
                        painter.drawCalloutBox(item.scrutinyVerdict)
                    }
                    if (item.content.isNotBlank()) {
                        painter.drawMonospaceBlock(item.content.take(5000))
                    }
                }
            }

            // 8. Workspace Files Appendix
            if (options.includeFileAppendix && files.isNotEmpty()) {
                painter.drawSectionHeading("Workspace Files Appendix", "Consolidated Repository Contents")
                files.sortedBy { it.relativePath }.forEach { file ->
                    val ext = file.relativePath.substringAfterLast('.', "txt").lowercase(Locale.US)
                    val formatMeta = UniversalFormatCatalog.findByExtension(ext)?.label ?: ext.uppercase(Locale.US)
                    painter.drawSubHeading(file.relativePath, "Category: ${file.category} · $formatMeta")
                    if (ext in setOf("md", "markdown", "txt")) {
                        painter.drawMarkdownProse(file.content.take(14_000))
                    } else {
                        painter.drawMonospaceBlock(file.content.take(10_000))
                    }
                }
            }

            painter.finishCurrentPage()
            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return out.toByteArray()
        } finally {
            document.close()
        }
    }

    private class PdfPageWriter(
        private val document: PdfDocument,
        private val reportTitle: String,
        private val generatedAt: String
    ) {
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNumber = 0
        private var cursorY = TOP_CONTENT_Y

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = COLOR_BORDER
        }
        private val runningHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val coverTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val coverMetaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        private val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val sectionSubtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val subHeadingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SLATE_DARK
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 9.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val bodyBoldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 10.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SLATE_DARK
            textSize = 8.2f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        init {
            startNewPage(drawRunningHeader = false)
        }

        private fun startNewPage(drawRunningHeader: Boolean = true) {
            finishCurrentPage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(info)
            currentPage = page
            canvas = page.canvas
            cursorY = if (drawRunningHeader) TOP_CONTENT_Y else 34f

            if (drawRunningHeader) {
                val c = page.canvas
                c.drawText(reportTitle.take(68), MARGIN_X, 28f, runningHeaderPaint)
                val badge = "Vortex Stakeholder Report"
                val w = runningHeaderPaint.measureText(badge)
                c.drawText(badge, PAGE_WIDTH - MARGIN_X - w, 28f, runningHeaderPaint)
                strokePaint.color = COLOR_BORDER
                c.drawLine(MARGIN_X, 36f, PAGE_WIDTH - MARGIN_X, 36f, strokePaint)
            }
        }

        fun finishCurrentPage() {
            val page = currentPage ?: return
            val c = page.canvas
            strokePaint.color = COLOR_BORDER
            c.drawLine(MARGIN_X, PAGE_HEIGHT - 36f, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 36f, strokePaint)
            c.drawText("Generated $generatedAt", MARGIN_X, PAGE_HEIGHT - 22f, runningHeaderPaint)
            val pageLabel = "Page $pageNumber"
            val labelW = runningHeaderPaint.measureText(pageLabel)
            c.drawText(pageLabel, PAGE_WIDTH - MARGIN_X - labelW, PAGE_HEIGHT - 22f, runningHeaderPaint)
            document.finishPage(page)
            currentPage = null
            canvas = null
        }

        private fun ensureSpace(neededHeight: Float) {
            if (cursorY + neededHeight > BOTTOM_LIMIT_Y) {
                startNewPage(drawRunningHeader = true)
            }
        }

        fun drawCoverBanner(
            title: String,
            rootFolder: String,
            domain: String,
            complexity: Int,
            gapVerdict: String,
            blueprintTitle: String,
            generatedAt: String
        ) {
            val c = canvas ?: return
            val titleLayout = staticLayout(title, coverTitlePaint, CONTENT_WIDTH - 32)
            val bannerHeight = (titleLayout.height + 76f).coerceAtLeast(108f)
            val rect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + bannerHeight)

            fillPaint.color = COLOR_NAVY
            c.drawRoundRect(rect, 10f, 10f, fillPaint)

            // Top emerald accent bar
            fillPaint.color = COLOR_ACCENT_EMERALD
            c.drawRoundRect(
                RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + 5f),
                4f,
                4f,
                fillPaint
            )

            c.save()
            c.translate(MARGIN_X + 16f, cursorY + 18f)
            titleLayout.draw(c)
            c.restore()

            val line1Y = cursorY + titleLayout.height + 34f
            c.drawText(
                "Workspace: $rootFolder/  ·  Blueprint: $blueprintTitle",
                MARGIN_X + 16f,
                line1Y,
                coverMetaPaint
            )
            c.drawText(
                "Domain: $domain ($complexity/10)  ·  Audit: $gapVerdict  ·  $generatedAt",
                MARGIN_X + 16f,
                line1Y + 16f,
                coverMetaPaint
            )

            cursorY += bannerHeight + 14f
        }

        fun drawKpiRow(items: List<Pair<String, String>>) {
            if (items.isEmpty()) return
            val boxHeight = 46f
            ensureSpace(boxHeight + 12f)
            val c = canvas ?: return
            val gap = 8f
            val cardWidth = (CONTENT_WIDTH - gap * (items.size - 1)) / items.size

            val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_ACCENT_BLUE
                textSize = 15f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_MUTED
                textSize = 8.2f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            items.forEachIndexed { idx, (label, value) ->
                val left = MARGIN_X + idx * (cardWidth + gap)
                val rect = RectF(left, cursorY, left + cardWidth, cursorY + boxHeight)
                fillPaint.color = COLOR_CARD_BG
                c.drawRoundRect(rect, 6f, 6f, fillPaint)
                strokePaint.color = COLOR_BORDER
                c.drawRoundRect(rect, 6f, 6f, strokePaint)

                c.drawText(value, left + 10f, cursorY + 22f, valuePaint)
                c.drawText(label, left + 10f, cursorY + 37f, labelPaint)
            }
            cursorY += boxHeight + 16f
        }

        fun drawSectionHeading(title: String, subtitle: String) {
            ensureSpace(44f)
            val c = canvas ?: return
            fillPaint.color = COLOR_ACCENT_BLUE
            c.drawRect(MARGIN_X, cursorY, MARGIN_X + 3.5f, cursorY + 26f, fillPaint)
            c.drawText(title, MARGIN_X + 10f, cursorY + 13f, sectionTitlePaint)
            if (subtitle.isNotBlank()) {
                c.drawText(subtitle, MARGIN_X + 10f, cursorY + 25f, sectionSubtitlePaint)
            }
            strokePaint.color = COLOR_BORDER
            c.drawLine(MARGIN_X, cursorY + 32f, PAGE_WIDTH - MARGIN_X, cursorY + 32f, strokePaint)
            cursorY += 40f
        }

        fun drawSubHeading(title: String, meta: String) {
            ensureSpace(32f)
            val c = canvas ?: return
            val layout = staticLayout(title, subHeadingPaint, CONTENT_WIDTH)
            c.save()
            c.translate(MARGIN_X, cursorY)
            layout.draw(c)
            c.restore()
            cursorY += layout.height + 2f
            if (meta.isNotBlank()) {
                c.drawText(meta.take(92), MARGIN_X, cursorY + 9f, sectionSubtitlePaint)
                cursorY += 15f
            } else {
                cursorY += 4f
            }
        }

        fun drawCalloutBox(text: String) {
            val clean = text.trim()
            if (clean.isEmpty()) return
            val layout = staticLayout(clean, bodyPaint, CONTENT_WIDTH - 24)
            val height = layout.height + 16f
            if (height > BOTTOM_LIMIT_Y - TOP_CONTENT_Y - 20f) {
                drawMarkdownProse(clean)
                return
            }
            ensureSpace(height + 10f)
            val c = canvas ?: return
            val rect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + height)
            fillPaint.color = COLOR_CARD_BG
            c.drawRoundRect(rect, 6f, 6f, fillPaint)
            strokePaint.color = COLOR_ACCENT_EMERALD
            c.drawRoundRect(rect, 6f, 6f, strokePaint)
            c.save()
            c.translate(MARGIN_X + 12f, cursorY + 8f)
            layout.draw(c)
            c.restore()
            cursorY += height + 12f
        }

        fun drawMarkdownProse(markdown: String) {
            val lines = markdown.replace("\r\n", "\n").lines()
            var inCodeFence = false
            val codeBuffer = StringBuilder()

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("```")) {
                    if (inCodeFence) {
                        if (codeBuffer.isNotEmpty()) {
                            drawMonospaceBlock(codeBuffer.toString().trimEnd())
                            codeBuffer.clear()
                        }
                        inCodeFence = false
                    } else {
                        inCodeFence = true
                    }
                    continue
                }
                if (inCodeFence) {
                    codeBuffer.appendLine(rawLine)
                    continue
                }
                if (trimmed.isEmpty()) {
                    cursorY += 5f
                    continue
                }

                when {
                    trimmed.startsWith("### ") -> drawWrappedParagraph(trimmed.removePrefix("### "), bodyBoldPaint, 4f)
                    trimmed.startsWith("## ") -> drawWrappedParagraph(trimmed.removePrefix("## "), subHeadingPaint, 6f)
                    trimmed.startsWith("# ") -> drawWrappedParagraph(trimmed.removePrefix("# "), subHeadingPaint, 7f)
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                        val bullet = "• " + trimmed.drop(2).replace("**", "")
                        drawWrappedParagraph(bullet, bodyPaint, 3f, indent = 10f)
                    }
                    else -> {
                        val cleanText = trimmed.replace("**", "").replace("`", "")
                        drawWrappedParagraph(cleanText, bodyPaint, 4f)
                    }
                }
            }
            if (codeBuffer.isNotEmpty()) {
                drawMonospaceBlock(codeBuffer.toString().trimEnd())
            }
            cursorY += 6f
        }

        fun drawMonospaceBlock(rawText: String) {
            val lines = rawText.replace("\r\n", "\n").lines()
            val chunkSize = 36
            lines.chunked(chunkSize).forEach { chunk ->
                val block = chunk.joinToString("\n")
                val layout = staticLayout(block, monoPaint, CONTENT_WIDTH - 16)
                val height = layout.height + 14f
                ensureSpace(height + 8f)
                val c = canvas ?: return
                val rect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + height)
                fillPaint.color = COLOR_CODE_BG
                c.drawRoundRect(rect, 5f, 5f, fillPaint)
                strokePaint.color = COLOR_BORDER
                c.drawRoundRect(rect, 5f, 5f, strokePaint)
                c.save()
                c.translate(MARGIN_X + 8f, cursorY + 7f)
                layout.draw(c)
                c.restore()
                cursorY += height + 8f
            }
        }

        private fun drawWrappedParagraph(
            text: String,
            paint: TextPaint,
            bottomGap: Float,
            indent: Float = 0f
        ) {
            val width = (CONTENT_WIDTH - indent.toInt()).coerceAtLeast(120)
            val layout = staticLayout(text, paint, width)
            ensureSpace(layout.height + bottomGap)
            val c = canvas ?: return
            c.save()
            c.translate(MARGIN_X + indent, cursorY)
            layout.draw(c)
            c.restore()
            cursorY += layout.height + bottomGap
        }

        private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(80))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2.2f, 1.0f)
                .setIncludePad(false)
                .build()
    }
}
