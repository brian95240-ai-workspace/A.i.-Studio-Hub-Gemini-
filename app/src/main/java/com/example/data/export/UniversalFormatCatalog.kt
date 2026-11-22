package com.example.data.export

import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.StageRecord
import com.example.ui.components.GeoCoord
import com.example.ui.components.GeoShape
import com.example.ui.components.SpecializedFormatConverters
import com.example.ui.components.SpecializedFormatKind
import com.example.ui.components.SpecializedFormatParser
import com.example.ui.components.UnifiedGeoFeature
import com.example.ui.components.UnifiedGeoModel
import com.example.ui.components.UnifiedGraphEdge
import com.example.ui.components.UnifiedGraphModel
import com.example.ui.components.UnifiedGraphNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormatCategory(val label: String) {
    ALL("All"),
    DOCUMENTS("Docs & Reports"),
    DATA_CONFIG("Data & Config"),
    CODE("Code & Scripts"),
    DIAGRAMS_WEB("Diagrams & Web"),
    ARCHIVES("Archives")
}

data class UniversalExportFormat(
    val extension: String,
    val label: String,
    val mimeType: String,
    val category: ExportFormatCategory,
    val lineCommentPrefix: String? = null,
    val isBinary: Boolean = false
)

object UniversalFormatCatalog {

    /**
     * Complete 54-format parity catalog covering every accepted format in UniversalArtifactIngestor
     * (all 52 source/doc/config/diagram extensions + PDF + ZIP) so any accepted format can be exported.
     */
    val allFormats: List<UniversalExportFormat> = listOf(
        // Documents & Reports
        UniversalExportFormat("pdf", "PDF Stakeholder Report", "application/pdf", ExportFormatCategory.DOCUMENTS, isBinary = true),
        UniversalExportFormat("md", "Markdown", "text/markdown", ExportFormatCategory.DOCUMENTS),
        UniversalExportFormat("markdown", "Markdown (Full)", "text/markdown", ExportFormatCategory.DOCUMENTS),
        UniversalExportFormat("txt", "Plain Text", "text/plain", ExportFormatCategory.DOCUMENTS),
        UniversalExportFormat("tex", "LaTeX Document", "application/x-tex", ExportFormatCategory.DOCUMENTS, "%"),
        UniversalExportFormat("latex", "LaTeX (.latex)", "application/x-tex", ExportFormatCategory.DOCUMENTS, "%"),
        UniversalExportFormat("bib", "BibTeX References", "application/x-bibtex", ExportFormatCategory.DOCUMENTS, "%"),

        // Archives
        UniversalExportFormat("zip", "ZIP Workspace Archive", "application/zip", ExportFormatCategory.ARCHIVES, isBinary = true),

        // Data & Config (PostgreSQL / PostGIS / Apache AGE / Graph Ecosystem)
        UniversalExportFormat("json", "JSON Bundle", "application/json", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("jsonl", "JSON Lines", "application/x-ndjson", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("yaml", "YAML", "text/yaml", ExportFormatCategory.DATA_CONFIG, "#"),
        UniversalExportFormat("yml", "YAML (.yml)", "text/yaml", ExportFormatCategory.DATA_CONFIG, "#"),
        UniversalExportFormat("toml", "TOML", "application/toml", ExportFormatCategory.DATA_CONFIG, "#"),
        UniversalExportFormat("ini", "INI Config", "text/plain", ExportFormatCategory.DATA_CONFIG, ";"),
        UniversalExportFormat("csv", "CSV Inventory", "text/csv", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("xml", "XML Dossier", "application/xml", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("sql", "PostgreSQL / PostGIS / AGE (.sql)", "application/sql", ExportFormatCategory.DATA_CONFIG, "--"),
        UniversalExportFormat("pgsql", "PostgreSQL Migration (.pgsql)", "application/sql", ExportFormatCategory.DATA_CONFIG, "--"),
        UniversalExportFormat("psql", "PostgreSQL Script (.psql)", "application/sql", ExportFormatCategory.DATA_CONFIG, "--"),
        UniversalExportFormat("cypher", "Cypher / Apache AGE (.cypher)", "text/plain", ExportFormatCategory.DATA_CONFIG, "//"),
        UniversalExportFormat("cql", "Cypher Query (.cql)", "text/plain", ExportFormatCategory.DATA_CONFIG, "//"),
        UniversalExportFormat("geojson", "PostGIS GeoJSON (.geojson)", "application/geo+json", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("wkt", "PostGIS EWKT Geometry (.wkt)", "text/plain", ExportFormatCategory.DATA_CONFIG, "--"),
        UniversalExportFormat("graphml", "GraphML (NetworkX / Gephi)", "application/xml", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("gexf", "GEXF Graph XML (.gexf)", "application/xml", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("d3json", "D3.js / NetworkX Node-Link JSON", "application/json", ExportFormatCategory.DATA_CONFIG),
        UniversalExportFormat("proto", "Protobuf", "text/plain", ExportFormatCategory.DATA_CONFIG, "//"),
        UniversalExportFormat("graphql", "GraphQL", "application/graphql", ExportFormatCategory.DATA_CONFIG, "#"),
        UniversalExportFormat("gql", "GraphQL (.gql)", "application/graphql", ExportFormatCategory.DATA_CONFIG, "#"),

        // Diagrams & Web
        UniversalExportFormat("html", "HTML Stakeholder Dossier", "text/html", ExportFormatCategory.DIAGRAMS_WEB),
        UniversalExportFormat("mmd", "Mermaid Diagram", "text/plain", ExportFormatCategory.DIAGRAMS_WEB, "%%"),
        UniversalExportFormat("mermaid", "Mermaid (.mermaid)", "text/plain", ExportFormatCategory.DIAGRAMS_WEB, "%%"),
        UniversalExportFormat("dot", "GraphViz DOT (.dot)", "text/vnd.graphviz", ExportFormatCategory.DIAGRAMS_WEB, "//"),
        UniversalExportFormat("gv", "GraphViz DOT (.gv)", "text/vnd.graphviz", ExportFormatCategory.DIAGRAMS_WEB, "//"),
        UniversalExportFormat("svg", "SVG Structure Diagram", "image/svg+xml", ExportFormatCategory.DIAGRAMS_WEB),
        UniversalExportFormat("css", "CSS", "text/css", ExportFormatCategory.DIAGRAMS_WEB, "/*"),
        UniversalExportFormat("scss", "SCSS", "text/x-scss", ExportFormatCategory.DIAGRAMS_WEB, "//"),

        // Code & Scripts
        UniversalExportFormat("py", "Python (with NetworkX Graph)", "text/x-python", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("ts", "TypeScript", "text/typescript", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("tsx", "TypeScript JSX", "text/typescript", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("js", "JavaScript", "text/javascript", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("jsx", "JavaScript JSX", "text/javascript", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("mjs", "JavaScript Module", "text/javascript", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("cjs", "CommonJS", "text/javascript", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("rs", "Rust", "text/x-rust", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("go", "Go", "text/x-go", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("kt", "Kotlin", "text/x-kotlin", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("kts", "Kotlin Script", "text/x-kotlin", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("java", "Java", "text/x-java-source", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("swift", "Swift", "text/x-swift", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("dart", "Dart", "text/x-dart", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("c", "C", "text/x-c", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("cpp", "C++", "text/x-c++src", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("cc", "C++ (.cc)", "text/x-c++src", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("h", "C/C++ Header", "text/x-chdr", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("hpp", "C++ Header", "text/x-c++hdr", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("cs", "C#", "text/x-csharp", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("rb", "Ruby", "text/x-ruby", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("php", "PHP", "text/x-php", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("sh", "Shell Script", "text/x-shellscript", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("bash", "Bash Script", "text/x-shellscript", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("zsh", "Zsh Script", "text/x-shellscript", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("r", "R Script", "text/x-r", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("scala", "Scala", "text/x-scala", ExportFormatCategory.CODE, "//"),
        UniversalExportFormat("ex", "Elixir", "text/x-elixir", ExportFormatCategory.CODE, "#"),
        UniversalExportFormat("zig", "Zig", "text/plain", ExportFormatCategory.CODE, "//")
    )

    private val byExtension: Map<String, UniversalExportFormat> =
        allFormats.associateBy { it.extension.lowercase(Locale.US) }

    fun findByExtension(extension: String): UniversalExportFormat? =
        byExtension[extension.trim().trimStart('.').lowercase(Locale.US)]

    fun mimeTypeForFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return byExtension[ext]?.mimeType ?: "text/plain"
    }

    fun suggestedFileName(project: ResearchProject, format: UniversalExportFormat): String {
        val base = project.rootFolderName.ifBlank { project.title }
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "vortex-project" }
        val suffix = when (format.extension) {
            "pdf" -> "-stakeholder-report"
            "zip" -> "-workspace"
            else -> "-export"
        }
        return "$base$suffix.${format.extension}"
    }

    fun buildAsciiProjectTree(rootFolderName: String, files: List<ProjectFile>): String {
        val root = rootFolderName.ifBlank { "vortex-research" }
        val sb = StringBuilder()
        sb.appendLine("$root/")
        val grouped = files
            .sortedBy { it.relativePath }
            .groupBy { file ->
                val idx = file.relativePath.lastIndexOf('/')
                if (idx == -1) "" else file.relativePath.substring(0, idx)
            }
            .toSortedMap()

        val rootFiles = grouped[""].orEmpty()
        val folders = grouped.keys.filter { it.isNotEmpty() }

        rootFiles.forEachIndexed { idx, file ->
            val connector = if (idx == rootFiles.lastIndex && folders.isEmpty()) "└── " else "├── "
            sb.appendLine("$connector${file.relativePath} (${formatSize(file.content.length)})")
        }

        folders.forEachIndexed { fIdx, folder ->
            val isLastFolder = fIdx == folders.lastIndex
            val folderPrefix = if (isLastFolder) "└── " else "├── "
            sb.appendLine("$folderPrefix$folder/")
            val branchPrefix = if (isLastFolder) "    " else "│   "
            val folderFiles = grouped[folder].orEmpty()
            folderFiles.forEachIndexed { fileIdx, file ->
                val fileConnector = if (fileIdx == folderFiles.lastIndex) "└── " else "├── "
                val name = file.relativePath.substringAfterLast('/')
                sb.appendLine("$branchPrefix$fileConnector$name (${formatSize(file.content.length)})")
            }
        }
        return sb.toString().trimEnd()
    }

    fun exportProjectBytes(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>,
        format: UniversalExportFormat,
        pdfOptions: PdfReportOptions = PdfReportOptions()
    ): ByteArray {
        return when (format.extension) {
            "pdf" -> StakeholderPdfReportGenerator.generatePdfBytes(
                project = project,
                files = files,
                prds = prds,
                roundtableItems = roundtableItems,
                options = pdfOptions
            )
            "zip" -> buildZipArchive(project, files)
            "html" -> buildHtmlDossier(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "json" -> buildJsonBundle(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "jsonl" -> buildJsonLinesBundle(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "yaml", "yml" -> buildYamlBundle(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "toml" -> buildTomlBundle(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "ini" -> buildIniBundle(project, files).toByteArray(Charsets.UTF_8)
            "csv" -> buildCsvInventory(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "xml" -> buildXmlDossier(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "tex", "latex" -> buildLatexDossier(project, files, prds).toByteArray(Charsets.UTF_8)
            "bib" -> buildBibtexBibliography(project, files).toByteArray(Charsets.UTF_8)
            "mmd", "mermaid" -> buildMermaidGraph(project, files).toByteArray(Charsets.UTF_8)
            "cypher", "cql" -> buildCypherExport(project, files).toByteArray(Charsets.UTF_8)
            "sql", "pgsql", "psql" -> buildPostgresEcosystemExport(project, files).toByteArray(Charsets.UTF_8)
            "geojson" -> buildGeoJsonExport(project, files).toByteArray(Charsets.UTF_8)
            "wkt" -> buildWktExport(project, files).toByteArray(Charsets.UTF_8)
            "graphml" -> buildGraphMlExport(project, files).toByteArray(Charsets.UTF_8)
            "gexf" -> buildGexfExport(project, files).toByteArray(Charsets.UTF_8)
            "dot", "gv" -> buildDotExport(project, files).toByteArray(Charsets.UTF_8)
            "d3json" -> buildD3JsonExport(project, files).toByteArray(Charsets.UTF_8)
            "py" -> buildPythonOrNetworkXExport(project, files, format).toByteArray(Charsets.UTF_8)
            "svg" -> buildSvgDiagram(project, files).toByteArray(Charsets.UTF_8)
            "md", "markdown" -> buildMarkdownDossier(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            "txt" -> buildPlainTextDossier(project, files, prds, roundtableItems).toByteArray(Charsets.UTF_8)
            else -> buildLanguageSourceExport(project, files, format).toByteArray(Charsets.UTF_8)
        }
    }

    private fun buildZipArchive(project: ResearchProject, files: List<ProjectFile>): ByteArray {
        val baos = ByteArrayOutputStream()
        val rootDir = project.rootFolderName.trim('/').ifBlank { "vortex-research" }
        ZipOutputStream(baos).use { zos ->
            val written = mutableSetOf<String>()
            files.forEach { file ->
                val cleanPath = file.relativePath.trim().trimStart('/')
                if (cleanPath.isBlank()) return@forEach
                val entryName = "$rootDir/$cleanPath"
                if (written.add(entryName.lowercase(Locale.US))) {
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(file.content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            if (written.isEmpty()) {
                zos.putNextEntry(ZipEntry("$rootDir/README.md"))
                zos.write(buildMarkdownDossier(project, files, emptyList(), emptyList()).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun buildMarkdownDossier(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        val stages = project.parsedStages()
        appendLine("# ${project.title.ifBlank { project.rootFolderName }}")
        appendLine()
        appendLine("- **Workspace Root:** `${project.rootFolderName}/`")
        appendLine("- **Domain:** ${project.detectedDomain.ifBlank { "General Research" }} (Complexity ${project.complexityScore}/10)")
        appendLine("- **Gap Audit Verdict:** ${project.gapVerdict}")
        appendLine("- **Exported:** ${timestampLabel()}")
        appendLine()
        if (project.query.isNotBlank()) {
            appendLine("## Research Brief")
            appendLine(project.query.trim())
            appendLine()
        }
        appendLine("## Project Structure (${files.size} files)")
        appendLine("```text")
        appendLine(buildAsciiProjectTree(project.rootFolderName, files))
        appendLine("```")
        appendLine()
        if (project.chatAnswer.isNotBlank()) {
            appendLine("## Consolidated Research Synthesis")
            appendLine(project.chatAnswer.trim())
            appendLine()
        }
        if (stages.isNotEmpty()) {
            appendLine("## 7-Stage Empirical Research Notes")
            stages.forEach { stage ->
                appendLine("### Stage ${stage.stageNumber}: ${stage.title}")
                if (stage.modelUsed.isNotBlank()) appendLine("*Model: ${stage.modelUsed}*")
                appendLine()
                appendLine(stage.content.ifBlank { stage.routingReason })
                appendLine()
            }
        }
        if (prds.isNotEmpty()) {
            appendLine("## Product Requirements Documents (${prds.size})")
            prds.forEach { prd ->
                appendLine("### ${prd.version} — ${prd.title}")
                appendLine(prd.markdownContent)
                appendLine()
            }
        }
        if (roundtableItems.isNotEmpty()) {
            appendLine("## A.i. Roundtable Findings (${roundtableItems.size})")
            roundtableItems.forEach { item ->
                appendLine("### ${item.modelFolder}/${item.fileName} [${item.queueStatus}]")
                if (item.scrutinyVerdict.isNotBlank()) appendLine("> ${item.scrutinyVerdict}")
                appendLine()
                appendLine(item.content.take(4000))
                appendLine()
            }
        }
        if (files.isNotEmpty()) {
            appendLine("## Repository Files")
            files.sortedBy { it.relativePath }.forEach { file ->
                val ext = file.relativePath.substringAfterLast('.', "txt")
                appendLine("### `${file.relativePath}`")
                appendLine("```$ext")
                appendLine(file.content)
                appendLine("```")
                appendLine()
            }
        }
    }

    private fun buildPlainTextDossier(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String {
        val md = buildMarkdownDossier(project, files, prds, roundtableItems)
        return md
            .replace(Regex("(?m)^#{1,6}\\s+"), "")
            .replace("**", "")
            .replace("```[a-zA-Z0-9_-]*".toRegex(), "----------------------------------------")
    }

    private fun buildHtmlDossier(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        val stages = project.parsedStages()
        val title = escapeHtml(project.title.ifBlank { project.rootFolderName })
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\"><head><meta charset=\"UTF-8\"/>")
        appendLine("<title>$title — Stakeholder Report</title>")
        appendLine(
            "<style>body{font-family:system-ui,-apple-system,sans-serif;max-width:920px;margin:32px auto;padding:0 20px;color:#111827;line-height:1.6;}" +
                "header{background:#0f172a;color:#f8fafc;padding:24px 28px;border-radius:12px;margin-bottom:24px;}" +
                "h1{margin:0 0 8px 0;font-size:26px;}h2{border-bottom:2px solid #e2e8f0;padding-bottom:6px;margin-top:28px;color:#0f172a;}" +
                "h3{color:#1e293b;margin-top:20px;}pre{background:#f8fafc;border:1px solid #cbd5e1;border-radius:8px;padding:14px;overflow-x:auto;font-size:13px;}" +
                ".meta{color:#cbd5e1;font-size:13px;}.pill{display:inline-block;background:#1e293b;color:#38bdf8;padding:3px 10px;border-radius:999px;font-size:12px;margin-right:8px;}" +
                "</style></head><body>"
        )
        appendLine("<header><h1>$title</h1>")
        appendLine("<div class=\"meta\"><span class=\"pill\">${escapeHtml(project.detectedDomain.ifBlank { "Research" })}</span>")
        appendLine("<span class=\"pill\">Complexity ${project.complexityScore}/10</span>")
        appendLine("<span class=\"pill\">${files.size} Files</span>")
        appendLine("<span class=\"pill\">Generated ${escapeHtml(timestampLabel())}</span></div></header>")

        if (project.query.isNotBlank()) {
            appendLine("<h2>Executive Research Brief</h2><p>${escapeHtml(project.query)}</p>")
        }
        appendLine("<h2>Project Directory Structure</h2>")
        appendLine("<pre>${escapeHtml(buildAsciiProjectTree(project.rootFolderName, files))}</pre>")

        if (project.chatAnswer.isNotBlank()) {
            appendLine("<h2>Consolidated Research Notes</h2>")
            appendLine("<pre>${escapeHtml(project.chatAnswer)}</pre>")
        }
        if (stages.isNotEmpty()) {
            appendLine("<h2>Empirical Research Stages</h2>")
            stages.forEach { st ->
                appendLine("<h3>Stage ${st.stageNumber}: ${escapeHtml(st.title)}</h3>")
                appendLine("<pre>${escapeHtml(st.content.ifBlank { st.routingReason })}</pre>")
            }
        }
        if (prds.isNotEmpty()) {
            appendLine("<h2>Product Requirements (${prds.size})</h2>")
            prds.forEach { prd ->
                appendLine("<h3>${escapeHtml(prd.version)} — ${escapeHtml(prd.title)}</h3>")
                appendLine("<pre>${escapeHtml(prd.markdownContent)}</pre>")
            }
        }
        if (roundtableItems.isNotEmpty()) {
            appendLine("<h2>A.i. Roundtable Cross-Model Scrutiny</h2>")
            roundtableItems.forEach { rt ->
                appendLine("<h3>${escapeHtml("${rt.modelFolder}/${rt.fileName}")} (${escapeHtml(rt.queueStatus)})</h3>")
                appendLine("<pre>${escapeHtml(rt.content.take(4000))}</pre>")
            }
        }
        if (files.isNotEmpty()) {
            appendLine("<h2>Workspace Files Appendix</h2>")
            files.sortedBy { it.relativePath }.forEach { f ->
                appendLine("<h3>${escapeHtml(f.relativePath)}</h3>")
                appendLine("<pre>${escapeHtml(f.content)}</pre>")
            }
        }
        appendLine("</body></html>")
    }

    private fun buildJsonBundle(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String {
        val root = JSONObject().apply {
            put("title", project.title)
            put("rootFolderName", project.rootFolderName)
            put("query", project.query)
            put("detectedDomain", project.detectedDomain)
            put("complexityScore", project.complexityScore)
            put("gapVerdict", project.gapVerdict)
            put("exportedAt", timestampLabel())
            put("directoryTree", buildAsciiProjectTree(project.rootFolderName, files))
            put("chatAnswer", project.chatAnswer)
            put("stages", JSONArray().apply {
                project.parsedStages().forEach { s ->
                    put(JSONObject().apply {
                        put("stageNumber", s.stageNumber)
                        put("title", s.title)
                        put("status", s.status)
                        put("modelUsed", s.modelUsed)
                        put("routingReason", s.routingReason)
                        put("content", s.content)
                    })
                }
            })
            put("prds", JSONArray().apply {
                prds.forEach { p ->
                    put(JSONObject().apply {
                        put("version", p.version)
                        put("title", p.title)
                        put("markdownContent", p.markdownContent)
                    })
                }
            })
            put("roundtable", JSONArray().apply {
                roundtableItems.forEach { r ->
                    put(JSONObject().apply {
                        put("modelFolder", r.modelFolder)
                        put("fileName", r.fileName)
                        put("status", r.queueStatus)
                        put("scrutinyVerdict", r.scrutinyVerdict)
                        put("content", r.content)
                    })
                }
            })
            put("files", JSONArray().apply {
                files.forEach { f ->
                    put(JSONObject().apply {
                        put("relativePath", f.relativePath)
                        put("category", f.category)
                        put("content", f.content)
                    })
                }
            })
        }
        return root.toString(2)
    }

    private fun buildJsonLinesBundle(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        appendLine(JSONObject().apply {
            put("recordType", "project")
            put("title", project.title)
            put("rootFolderName", project.rootFolderName)
            put("query", project.query)
            put("domain", project.detectedDomain)
            put("complexity", project.complexityScore)
        }.toString())
        project.parsedStages().forEach { s ->
            appendLine(JSONObject().apply {
                put("recordType", "stage")
                put("stageNumber", s.stageNumber)
                put("title", s.title)
                put("modelUsed", s.modelUsed)
                put("content", s.content.ifBlank { s.routingReason })
            }.toString())
        }
        prds.forEach { p ->
            appendLine(JSONObject().apply {
                put("recordType", "prd")
                put("version", p.version)
                put("title", p.title)
                put("content", p.markdownContent)
            }.toString())
        }
        roundtableItems.forEach { r ->
            appendLine(JSONObject().apply {
                put("recordType", "roundtable")
                put("path", "${r.modelFolder}/${r.fileName}")
                put("status", r.queueStatus)
                put("content", r.content)
            }.toString())
        }
        files.forEach { f ->
            appendLine(JSONObject().apply {
                put("recordType", "file")
                put("relativePath", f.relativePath)
                put("category", f.category)
                put("content", f.content)
            }.toString())
        }
    }

    private fun buildYamlBundle(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        appendLine("project:")
        appendLine("  title: \"${escapeQuoted(project.title)}\"")
        appendLine("  root_folder: \"${escapeQuoted(project.rootFolderName)}\"")
        appendLine("  domain: \"${escapeQuoted(project.detectedDomain)}\"")
        appendLine("  complexity_score: ${project.complexityScore}")
        appendLine("  gap_verdict: \"${escapeQuoted(project.gapVerdict)}\"")
        appendLine("  exported_at: \"${timestampLabel()}\"")
        appendLine("  query: |")
        project.query.lines().forEach { appendLine("    $it") }
        appendLine("directory_tree: |")
        buildAsciiProjectTree(project.rootFolderName, files).lines().forEach { appendLine("  $it") }
        appendLine("files:")
        files.sortedBy { it.relativePath }.forEach { f ->
            appendLine("  - path: \"${escapeQuoted(f.relativePath)}\"")
            appendLine("    category: \"${escapeQuoted(f.category)}\"")
            appendLine("    size_bytes: ${f.content.toByteArray(Charsets.UTF_8).size}")
        }
        appendLine("prd_versions: ${prds.size}")
        appendLine("roundtable_artifacts: ${roundtableItems.size}")
    }

    private fun buildTomlBundle(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        appendLine("[project]")
        appendLine("title = \"${escapeQuoted(project.title)}\"")
        appendLine("root_folder = \"${escapeQuoted(project.rootFolderName)}\"")
        appendLine("domain = \"${escapeQuoted(project.detectedDomain)}\"")
        appendLine("complexity_score = ${project.complexityScore}")
        appendLine("gap_verdict = \"${escapeQuoted(project.gapVerdict)}\"")
        appendLine("prd_count = ${prds.size}")
        appendLine("roundtable_count = ${roundtableItems.size}")
        appendLine("exported_at = \"${timestampLabel()}\"")
        appendLine()
        files.sortedBy { it.relativePath }.forEach { f ->
            appendLine("[[files]]")
            appendLine("path = \"${escapeQuoted(f.relativePath)}\"")
            appendLine("category = \"${escapeQuoted(f.category)}\"")
            appendLine("size_bytes = ${f.content.toByteArray(Charsets.UTF_8).size}")
            appendLine()
        }
    }

    private fun buildIniBundle(project: ResearchProject, files: List<ProjectFile>): String = buildString {
        appendLine("[project]")
        appendLine("title=${project.title}")
        appendLine("root_folder=${project.rootFolderName}")
        appendLine("domain=${project.detectedDomain}")
        appendLine("complexity_score=${project.complexityScore}")
        appendLine("gap_verdict=${project.gapVerdict}")
        appendLine("exported_at=${timestampLabel()}")
        appendLine()
        appendLine("[files]")
        files.sortedBy { it.relativePath }.forEachIndexed { idx, f ->
            appendLine("file_${idx + 1}=${f.relativePath} (${f.category}, ${f.content.length} chars)")
        }
    }

    private fun buildCsvInventory(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        appendLine("Type,PathOrName,CategoryOrStatus,SizeBytes,Preview")
        files.sortedBy { it.relativePath }.forEach { f ->
            appendLine(
                listOf(
                    "FILE",
                    f.relativePath,
                    f.category,
                    f.content.toByteArray(Charsets.UTF_8).size.toString(),
                    f.content.replace('\n', ' ').take(240)
                ).joinToString(",") { csvCell(it) }
            )
        }
        project.parsedStages().forEach { s ->
            appendLine(
                listOf(
                    "STAGE",
                    "Stage ${s.stageNumber}: ${s.title}",
                    s.modelUsed.ifBlank { s.status },
                    s.content.toByteArray(Charsets.UTF_8).size.toString(),
                    s.content.ifBlank { s.routingReason }.replace('\n', ' ').take(240)
                ).joinToString(",") { csvCell(it) }
            )
        }
        prds.forEach { p ->
            appendLine(
                listOf("PRD", "${p.version} - ${p.title}", p.version, p.markdownContent.length.toString(), p.markdownContent.replace('\n', ' ').take(240))
                    .joinToString(",") { csvCell(it) }
            )
        }
        roundtableItems.forEach { r ->
            appendLine(
                listOf("ROUNDTABLE", "${r.modelFolder}/${r.fileName}", r.queueStatus, r.byteSize.toString(), r.scrutinyVerdict.ifBlank { r.content }.replace('\n', ' ').take(240))
                    .joinToString(",") { csvCell(it) }
            )
        }
    }

    private fun buildXmlDossier(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<vortexProject title=\"${escapeHtml(project.title)}\" rootFolder=\"${escapeHtml(project.rootFolderName)}\" exportedAt=\"${escapeHtml(timestampLabel())}\">")
        appendLine("  <query>${escapeHtml(project.query)}</query>")
        appendLine("  <directoryTree><![CDATA[${buildAsciiProjectTree(project.rootFolderName, files)}]]></directoryTree>")
        appendLine("  <files count=\"${files.size}\">")
        files.sortedBy { it.relativePath }.forEach { f ->
            appendLine("    <file path=\"${escapeHtml(f.relativePath)}\" category=\"${escapeHtml(f.category)}\"><![CDATA[${f.content.replace("]]>", "]]]]><![CDATA[>")}]]></file>")
        }
        appendLine("  </files>")
        appendLine("  <prds count=\"${prds.size}\">")
        prds.forEach { p ->
            appendLine("    <prd version=\"${escapeHtml(p.version)}\" title=\"${escapeHtml(p.title)}\"><![CDATA[${p.markdownContent.replace("]]>", "]]]]><![CDATA[>")}]]></prd>")
        }
        appendLine("  </prds>")
        appendLine("  <roundtable count=\"${roundtableItems.size}\">")
        roundtableItems.forEach { r ->
            appendLine("    <artifact folder=\"${escapeHtml(r.modelFolder)}\" file=\"${escapeHtml(r.fileName)}\" status=\"${escapeHtml(r.queueStatus)}\"/>")
        }
        appendLine("  </roundtable>")
        appendLine("</vortexProject>")
    }

    private fun buildLatexDossier(
        project: ResearchProject,
        files: List<ProjectFile>,
        prds: List<PrdDocument>
    ): String = buildString {
        val texFiles = files.filter {
            val ext = it.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            ext == "tex" || ext == "latex"
        }
        appendLine("\\documentclass[11pt]{article}")
        appendLine("\\usepackage[utf8]{inputenc}")
        appendLine("\\usepackage[margin=1in]{geometry}")
        appendLine("\\usepackage{amsmath,amssymb,mathtools,hyperref,verbatim}")
        appendLine("\\title{${escapeLatex(project.title.ifBlank { project.rootFolderName })}}")
        appendLine("\\date{${timestampLabel()}}")
        appendLine("\\begin{document}")
        appendLine("\\maketitle")
        appendLine("\\section{Research Brief}")
        appendLine(escapeLatex(project.query))
        if (texFiles.isNotEmpty()) {
            texFiles.forEach { tf ->
                appendLine("\\section{Embedded LaTeX: ${escapeLatex(tf.relativePath)}}")
                val body = tf.content
                    .replace(Regex("""\\documentclass[\s\S]*?\\begin\{document\}"""), "")
                    .replace("\\end{document}", "")
                    .trim()
                appendLine(body)
            }
        }
        appendLine("\\section{Project Directory Structure}")
        appendLine("\\begin{verbatim}")
        appendLine(buildAsciiProjectTree(project.rootFolderName, files))
        appendLine("\\end{verbatim}")
        project.parsedStages().forEach { s ->
            appendLine("\\section{Stage ${s.stageNumber}: ${escapeLatex(s.title)}}")
            appendLine("\\begin{verbatim}")
            appendLine(s.content.ifBlank { s.routingReason })
            appendLine("\\end{verbatim}")
        }
        prds.forEach { p ->
            appendLine("\\section{PRD ${escapeLatex(p.version)}: ${escapeLatex(p.title)}}")
            appendLine("\\begin{verbatim}")
            appendLine(p.markdownContent)
            appendLine("\\end{verbatim}")
        }
        appendLine("\\end{document}")
    }

    private fun buildProjectKnowledgeGraph(
        project: ResearchProject,
        files: List<ProjectFile>
    ): UnifiedGraphModel {
        // Prefer an existing Cypher/DOT/GraphML/D3 graph file if present
        val existingGraphFile = files.firstOrNull { f ->
            val ext = f.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            val kind = SpecializedFormatParser.detectKind(ext, f.content)
            kind != null && kind.isGraphLike
        }
        if (existingGraphFile != null) {
            val ext = existingGraphFile.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            val kind = SpecializedFormatParser.detectKind(ext, existingGraphFile.content)
            if (kind != null) {
                val parsed = SpecializedFormatParser.parseToUnifiedGraph(kind, existingGraphFile.content, project.title)
                if (parsed.nodes.isNotEmpty()) return parsed
            }
        }

        // Otherwise synthesize a clean workspace + research stages knowledge graph
        val graphName = project.rootFolderName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "vortex_graph" }
        val nodes = mutableListOf<UnifiedGraphNode>()
        val edges = mutableListOf<UnifiedGraphEdge>()

        nodes.add(UnifiedGraphNode("project_root", project.title.ifBlank { project.rootFolderName }, group = "Project"))
        val stages = project.parsedStages()
        var prevStageId = "project_root"
        stages.forEach { st ->
            val stId = "stage_${st.stageNumber}"
            nodes.add(UnifiedGraphNode(stId, "Stage ${st.stageNumber}: ${st.title.take(28)}", group = "ResearchStage"))
            edges.add(UnifiedGraphEdge(prevStageId, stId, if (prevStageId == "project_root") "HAS_STAGE" else "NEXT_STAGE"))
            prevStageId = stId
        }
        val folders = files.mapNotNull { f ->
            val idx = f.relativePath.lastIndexOf('/')
            if (idx > 0) f.relativePath.substring(0, idx) else null
        }.distinct().sorted()
        folders.forEachIndexed { idx, folder ->
            val dId = "dir_$idx"
            nodes.add(UnifiedGraphNode(dId, "$folder/", group = "Folder"))
            edges.add(UnifiedGraphEdge("project_root", dId, "CONTAINS"))
        }
        files.sortedBy { it.relativePath }.take(40).forEachIndexed { idx, file ->
            val fId = "file_$idx"
            val ext = file.relativePath.substringAfterLast('.', "txt").lowercase(Locale.US)
            nodes.add(UnifiedGraphNode(fId, file.relativePath.substringAfterLast('/'), group = ext.uppercase(Locale.US)))
            val slash = file.relativePath.lastIndexOf('/')
            val parentId = if (slash > 0) {
                val dIdx = folders.indexOf(file.relativePath.substring(0, slash))
                if (dIdx >= 0) "dir_$dIdx" else "project_root"
            } else "project_root"
            edges.add(UnifiedGraphEdge(parentId, fId, "INCLUDES"))
        }

        return UnifiedGraphModel(
            title = project.title.ifBlank { project.rootFolderName },
            graphName = graphName,
            nodes = nodes,
            edges = edges,
            sourceDialect = "Vortex Workspace Graph"
        )
    }

    private fun buildProjectGeoModel(
        project: ResearchProject,
        files: List<ProjectFile>
    ): UnifiedGeoModel {
        val geoFile = files.firstOrNull { f ->
            val ext = f.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            SpecializedFormatParser.detectKind(ext, f.content) == SpecializedFormatKind.GEOSPATIAL
        }
        if (geoFile != null) {
            return SpecializedFormatParser.parseGeospatial(geoFile.content)
        }
        // Provide a valid WGS84 scaffold FeatureCollection for PostGIS ingestion
        val fallbackFeature = UnifiedGeoFeature(
            id = "project_origin",
            label = project.title.ifBlank { project.rootFolderName },
            shapes = listOf(GeoShape.Point(GeoCoord(0.0, 0.0))),
            properties = mapOf(
                "project" to project.rootFolderName,
                "domain" to project.detectedDomain.ifBlank { "Research" },
                "files" to files.size.toString()
            )
        )
        return UnifiedGeoModel(
            srid = 4326,
            features = listOf(fallbackFeature),
            minLon = -1.0,
            maxLon = 1.0,
            minLat = -1.0,
            maxLat = 1.0,
            sourceDialect = "GeoJSON (WGS84)"
        )
    }

    private fun buildCypherExport(project: ResearchProject, files: List<ProjectFile>): String {
        val cypherFiles = files.filter {
            val ext = it.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            ext == "cypher" || ext == "cql"
        }
        return if (cypherFiles.isNotEmpty()) {
            cypherFiles.joinToString("\n\n") { f -> "// FILE: ${f.relativePath}\n${f.content}" }
        } else {
            SpecializedFormatConverters.toCypher(buildProjectKnowledgeGraph(project, files))
        }
    }

    private fun buildPostgresEcosystemExport(project: ResearchProject, files: List<ProjectFile>): String = buildString {
        val sqlFiles = files.filter {
            val ext = it.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            ext in setOf("sql", "pgsql", "psql")
        }
        if (sqlFiles.isNotEmpty()) {
            sqlFiles.sortedBy { it.relativePath }.forEach { f ->
                appendLine("-- FILE: ${f.relativePath}")
                appendLine(f.content)
                appendLine()
            }
        }
        val graph = buildProjectKnowledgeGraph(project, files)
        appendLine(SpecializedFormatConverters.toApacheAgeSql(graph))
        val hasGeo = files.any {
            val ext = it.relativePath.substringAfterLast('.', "").lowercase(Locale.US)
            SpecializedFormatParser.detectKind(ext, it.content) == SpecializedFormatKind.GEOSPATIAL
        }
        if (hasGeo) {
            appendLine()
            appendLine(SpecializedFormatConverters.toPostGisSql(buildProjectGeoModel(project, files), project.rootFolderName))
        }
    }

    private fun buildGeoJsonExport(project: ResearchProject, files: List<ProjectFile>): String =
        SpecializedFormatConverters.toGeoJson(buildProjectGeoModel(project, files))

    private fun buildWktExport(project: ResearchProject, files: List<ProjectFile>): String =
        SpecializedFormatConverters.toWkt(buildProjectGeoModel(project, files))

    private fun buildGraphMlExport(project: ResearchProject, files: List<ProjectFile>): String =
        SpecializedFormatConverters.toGraphMl(buildProjectKnowledgeGraph(project, files))

    private fun buildGexfExport(project: ResearchProject, files: List<ProjectFile>): String {
        val graph = buildProjectKnowledgeGraph(project, files)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gexf xmlns="http://gexf.net/1.3" version="1.3">""")
            appendLine("""  <graph mode="static" defaultedgetype="directed">""")
            appendLine("""    <nodes>""")
            graph.nodes.forEach { n ->
                appendLine("""      <node id="${escapeHtml(n.id)}" label="${escapeHtml(n.label)}"/>""")
            }
            appendLine("""    </nodes>""")
            appendLine("""    <edges>""")
            graph.edges.forEachIndexed { idx, e ->
                appendLine("""      <edge id="e$idx" source="${escapeHtml(e.source)}" target="${escapeHtml(e.target)}" label="${escapeHtml(e.label)}"/>""")
            }
            appendLine("""    </edges>""")
            appendLine("""  </graph>""")
            appendLine("""</gexf>""")
        }
    }

    private fun buildDotExport(project: ResearchProject, files: List<ProjectFile>): String =
        SpecializedFormatConverters.toDot(buildProjectKnowledgeGraph(project, files))

    private fun buildD3JsonExport(project: ResearchProject, files: List<ProjectFile>): String =
        SpecializedFormatConverters.toD3Json(buildProjectKnowledgeGraph(project, files))

    private fun buildPythonOrNetworkXExport(
        project: ResearchProject,
        files: List<ProjectFile>,
        format: UniversalExportFormat
    ): String {
        val pyFiles = files.filter { it.relativePath.endsWith(".py", ignoreCase = true) }
        return if (pyFiles.isNotEmpty()) {
            buildLanguageSourceExport(project, files, format)
        } else {
            SpecializedFormatConverters.toNetworkXPython(buildProjectKnowledgeGraph(project, files))
        }
    }

    private fun buildBibtexBibliography(project: ResearchProject, files: List<ProjectFile>): String = buildString {
        val urlRegex = Regex("https?://[^\\s)\\]>\"']+")
        val corpus = buildString {
            appendLine(project.chatAnswer)
            project.parsedStages().forEach { appendLine(it.content) }
            files.forEach { appendLine(it.content) }
        }
        val urls = urlRegex.findAll(corpus).map { it.value.trimEnd('.', ',', ';') }.distinct().toList()
        appendLine("% BibTeX Bibliography exported from Vortex Researcher: ${project.title}")
        appendLine("% Generated: ${timestampLabel()}")
        appendLine()
        if (urls.isEmpty()) {
            appendLine("@misc{vortex_project_dossier,")
            appendLine("  title = {${project.title.ifBlank { project.rootFolderName }}},")
            appendLine("  note = {Consolidated empirical research dossier (${files.size} workspace files)},")
            appendLine("  year = {${SimpleDateFormat("yyyy", Locale.US).format(Date())}}")
            appendLine("}")
        } else {
            urls.forEachIndexed { idx, url ->
                appendLine("@misc{ref_${idx + 1},")
                appendLine("  title = {Referenced Source ${idx + 1} (${project.title})},")
                appendLine("  howpublished = {\\url{$url}},")
                appendLine("  note = {Cited in Vortex Researcher project ${project.rootFolderName}}")
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun buildMermaidGraph(project: ResearchProject, files: List<ProjectFile>): String = buildString {
        appendLine("%% Project Structure Diagram for ${project.title}")
        appendLine("graph TD")
        appendLine("  ROOT[\"${escapeQuoted(project.rootFolderName)}/\"]")
        val folders = files.mapNotNull { f ->
            val idx = f.relativePath.lastIndexOf('/')
            if (idx > 0) f.relativePath.substring(0, idx) else null
        }.distinct().sorted()
        folders.forEachIndexed { i, dir ->
            appendLine("  DIR_$i[\"${escapeQuoted(dir)}/\"]")
            appendLine("  ROOT --> DIR_$i")
        }
        files.sortedBy { it.relativePath }.take(60).forEachIndexed { idx, file ->
            val dirIdx = file.relativePath.lastIndexOf('/').let { slash ->
                if (slash > 0) folders.indexOf(file.relativePath.substring(0, slash)) else -1
            }
            val parentId = if (dirIdx >= 0) "DIR_$dirIdx" else "ROOT"
            val label = file.relativePath.substringAfterLast('/')
            appendLine("  FILE_$idx[\"${escapeQuoted(label)}\"]")
            appendLine("  $parentId --> FILE_$idx")
        }
    }

    private fun buildSvgDiagram(project: ResearchProject, files: List<ProjectFile>): String {
        val treeLines = buildAsciiProjectTree(project.rootFolderName, files).lines()
        val height = (96 + treeLines.size * 20).coerceAtLeast(220)
        return buildString {
            appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"760\" height=\"$height\" viewBox=\"0 0 760 $height\">")
            appendLine("  <rect width=\"100%\" height=\"100%\" rx=\"12\" fill=\"#0f172a\"/>")
            appendLine("  <text x=\"24\" y=\"36\" fill=\"#f8fafc\" font-family=\"sans-serif\" font-size=\"18\" font-weight=\"bold\">${escapeHtml(project.title.ifBlank { project.rootFolderName })}</text>")
            appendLine("  <text x=\"24\" y=\"58\" fill=\"#38bdf8\" font-family=\"monospace\" font-size=\"12\">${files.size} workspace files · Generated ${escapeHtml(timestampLabel())}</text>")
            treeLines.forEachIndexed { idx, line ->
                val y = 86 + idx * 20
                appendLine("  <text x=\"24\" y=\"$y\" fill=\"#e2e8f0\" font-family=\"monospace\" font-size=\"12\" xml:space=\"preserve\">${escapeHtml(line)}</text>")
            }
            appendLine("</svg>")
        }
    }

    private fun buildLanguageSourceExport(
        project: ResearchProject,
        files: List<ProjectFile>,
        format: UniversalExportFormat
    ): String {
        val ext = format.extension
        val matchingFiles = files.filter {
            it.relativePath.substringAfterLast('.', "").equals(ext, ignoreCase = true)
        }
        val prefix = format.lineCommentPrefix ?: "//"
        val isBlockComment = prefix == "/*"

        fun commentLine(text: String): String =
            if (isBlockComment) "/* $text */" else "$prefix $text"

        return buildString {
            if (ext == "php") appendLine("<?php")
            if (ext in listOf("sh", "bash", "zsh")) appendLine("#!/usr/bin/env $ext")
            appendLine(commentLine("=================================================================="))
            appendLine(commentLine("Vortex Researcher Export (${format.label} .${format.extension})"))
            appendLine(commentLine("Project: ${project.title.ifBlank { project.rootFolderName }}"))
            appendLine(commentLine("Workspace Root: ${project.rootFolderName}/"))
            appendLine(commentLine("Exported: ${timestampLabel()}"))
            appendLine(commentLine("=================================================================="))
            appendLine()
            appendLine(commentLine("PROJECT STRUCTURE:"))
            buildAsciiProjectTree(project.rootFolderName, files).lines().forEach { line ->
                appendLine(commentLine("  $line"))
            }
            appendLine()

            if (matchingFiles.isNotEmpty()) {
                matchingFiles.sortedBy { it.relativePath }.forEach { f ->
                    appendLine(commentLine("--- FILE: ${f.relativePath} ---"))
                    appendLine(f.content)
                    appendLine()
                }
            } else {
                appendLine(commentLine("CONSOLIDATED RESEARCH NOTES:"))
                val summary = project.chatAnswer.ifBlank {
                    project.parsedStages().joinToString("\n\n") { st ->
                        "Stage ${st.stageNumber} — ${st.title}\n${st.content.ifBlank { st.routingReason }}"
                    }
                }.ifBlank { project.query }
                summary.lines().forEach { line ->
                    appendLine(commentLine(line))
                }
            }
        }
    }

    private fun formatSize(chars: Int): String =
        if (chars >= 1024) "${String.format(Locale.US, "%.1f", chars / 1024f)} KB" else "$chars B"

    private fun timestampLabel(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun escapeHtml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun escapeQuoted(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private fun escapeLatex(raw: String): String =
        raw.replace("\\", "\\textbackslash ")
            .replace("&", "\\&")
            .replace("%", "\\%")
            .replace("$", "\\$")
            .replace("#", "\\#")
            .replace("_", "\\_")
            .replace("{", "\\{")
            .replace("}", "\\}")

    private fun csvCell(raw: String): String =
        "\"${raw.replace("\"", "\"\"")}\""
}
