package com.example.ui.components

import com.example.data.model.GapVerdict
import com.example.data.model.PrdDocument
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoutingPreferences
import com.example.data.model.StageRecord
import org.json.JSONObject

enum class MermaidDiagramCategory(val label: String) {
    SYNTHESIS("Research Synthesis"),
    MODEL_ARCHITECTURE("Model Architecture"),
    EXTRACTED("Diagram")
}

enum class MermaidThemePreset(
    val id: String,
    val label: String,
    val canvasBgHex: String,
    val nodeBgHex: String,
    val nodeBorderHex: String,
    val textHex: String,
    val edgeHex: String,
    val subgraphBgHex: String,
    val accentEmeraldHex: String,
    val accentAmberHex: String
) {
    OBSIDIAN(
        id = "obsidian",
        label = "Obsidian",
        canvasBgHex = "#0B1018",
        nodeBgHex = "#121A26",
        nodeBorderHex = "#00E5FF",
        textHex = "#EAF2FA",
        edgeHex = "#7C9EFF",
        subgraphBgHex = "#0F1724",
        accentEmeraldHex = "#34D399",
        accentAmberHex = "#FFB74D"
    ),
    BLUEPRINT(
        id = "blueprint",
        label = "Blueprint",
        canvasBgHex = "#F8FAFC",
        nodeBgHex = "#FFFFFF",
        nodeBorderHex = "#0284C7",
        textHex = "#0F172A",
        edgeHex = "#334155",
        subgraphBgHex = "#EFF6FF",
        accentEmeraldHex = "#059669",
        accentAmberHex = "#D97706"
    ),
    EMERALD(
        id = "emerald",
        label = "Emerald",
        canvasBgHex = "#050B0A",
        nodeBgHex = "#0C1E1B",
        nodeBorderHex = "#34D399",
        textHex = "#ECFDF5",
        edgeHex = "#2DD4BF",
        subgraphBgHex = "#081614",
        accentEmeraldHex = "#34D399",
        accentAmberHex = "#FBBF24"
    )
}

data class MermaidDiagramSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: MermaidDiagramCategory,
    val code: String,
    val suggestedFileName: String
)

object MermaidDiagramEngine {

    const val PINNED_MERMAID_CDN_URL = "https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"
    const val SANDBOX_BASE_URL = "https://vortex.local/"

    private val mermaidFenceRegex = Regex(
        pattern = "(?:^|\\n)```(?:mermaid|mmd)\\s*\\n([\\s\\S]*?)\\n```",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    /**
     * Cleans common LLM-emitted Mermaid formatting quirks (stray fences, curly quotes,
     * unquoted parentheses/colons inside square-bracket node labels) so dynamically
     * generated synthesis diagrams render reliably in Mermaid.js v11.
     */
    fun sanitizeMermaidSyntax(raw: String): String {
        var cleaned = raw.replace("\r\n", "\n").trim()
        // Strip accidental surrounding markdown fences
        cleaned = cleaned
            .removePrefix("```mermaid")
            .removePrefix("```mmd")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        // Normalize smart quotes to ASCII
        cleaned = cleaned
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')

        val unquotedBracketNode = Regex("""(\b[A-Za-z][A-Za-z0-9_-]*)\[([^"\[\]\n]+)\]""")
        return cleaned.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("%%") ||
                trimmed.startsWith("classDef ") ||
                trimmed.startsWith("style ") ||
                trimmed.startsWith("linkStyle ") ||
                trimmed.startsWith("subgraph ")
            ) {
                line
            } else {
                unquotedBracketNode.replace(line) { m ->
                    val id = m.groupValues[1]
                    val lbl = m.groupValues[2].trim()
                    if (lbl.any { it in listOf('(', ')', ':', '/', '&', ',', ';') }) {
                        val safeLbl = lbl.replace("\"", "'")
                        "$id[\"$safeLbl\"]"
                    } else {
                        m.value
                    }
                }
            }
        }
    }

    fun extractMermaidBlocks(
        markdown: String,
        sourceLabel: String,
        category: MermaidDiagramCategory = MermaidDiagramCategory.EXTRACTED
    ): List<MermaidDiagramSpec> {
        if (markdown.isBlank()) return emptyList()
        val normalized = markdown.replace("\r\n", "\n")
        val matches = mermaidFenceRegex.findAll(normalized).toList()
        return matches.mapIndexedNotNull { idx, match ->
            val rawCode = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val code = sanitizeMermaidSyntax(rawCode)
            if (code.isBlank()) return@mapIndexedNotNull null

            val precedingText = normalized.substring(0, match.range.first).trimEnd()
            val precedingHeading = precedingText.lines()
                .lastOrNull { it.trim().startsWith("#") }
                ?.trim()
                ?.trimStart('#')
                ?.trim()
                ?.take(48)

            val diagramKind = detectDiagramType(code)
            val title = when {
                !precedingHeading.isNullOrBlank() -> precedingHeading
                matches.size == 1 -> "$sourceLabel · $diagramKind"
                else -> "$sourceLabel · $diagramKind ${idx + 1}"
            }
            val slug = title.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "diagram_${idx + 1}" }

            MermaidDiagramSpec(
                id = "extracted_${sourceLabel.lowercase().replace(Regex("[^a-z0-9]"), "_")}_$idx",
                title = title,
                subtitle = "$sourceLabel · $diagramKind",
                category = category,
                code = code,
                suggestedFileName = "$slug.mmd"
            )
        }
    }

    fun detectDiagramType(code: String): String {
        val firstLine = code.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }
            ?.lowercase()
            .orEmpty()
        return when {
            firstLine.startsWith("sequencediagram") -> "Sequence Diagram"
            firstLine.startsWith("classdiagram") -> "Class Diagram"
            firstLine.startsWith("statediagram") -> "State Diagram"
            firstLine.startsWith("erdiagram") -> "ER Diagram"
            firstLine.startsWith("gantt") -> "Roadmap Gantt"
            firstLine.startsWith("mindmap") -> "Mindmap"
            firstLine.startsWith("timeline") -> "Timeline"
            firstLine.startsWith("c4") -> "C4 Architecture"
            firstLine.startsWith("architecture") -> "Architecture"
            firstLine.startsWith("flowchart") || firstLine.startsWith("graph") -> "Architecture Flow"
            else -> "Mermaid Diagram"
        }
    }

    /**
     * Dynamically collects all Mermaid.js diagrams from live or completed research synthesis
     * pipeline output (works even before a project is persisted), putting AI-generated synthesis
     * diagrams first alongside live pipeline topology, findings graph, and model routing diagrams.
     */
    fun collectSynthesisPipelineDiagrams(
        stages: List<StageRecord>,
        synthesisMarkdown: String = "",
        project: ResearchProject? = null,
        fallbackTitle: String = "",
        routingPrefs: RoutingPreferences = RoutingPreferences(),
        roundtableItems: List<RoundtableItem> = emptyList()
    ): List<MermaidDiagramSpec> {
        val extracted = mutableListOf<MermaidDiagramSpec>()

        // 1. Extract explicit AI-generated Mermaid diagrams from pipeline stages (Stage 4 & 6 prioritized)
        val prioritizedStages = stages
            .filter { it.content.isNotBlank() }
            .sortedBy {
                when (it.stageNumber) {
                    4 -> 0
                    6 -> 1
                    7 -> 2
                    else -> it.stageNumber + 10
                }
            }
        prioritizedStages.forEach { stage ->
            val category = if (stage.stageNumber == 6) MermaidDiagramCategory.MODEL_ARCHITECTURE
            else MermaidDiagramCategory.SYNTHESIS
            extracted.addAll(
                extractMermaidBlocks(
                    markdown = stage.content,
                    sourceLabel = "Stage ${stage.stageNumber}: ${stage.title}",
                    category = category
                )
            )
        }

        // 2. Extract from direct synthesis markdown / chat answer if provided
        if (synthesisMarkdown.isNotBlank()) {
            extracted.addAll(
                extractMermaidBlocks(
                    markdown = synthesisMarkdown,
                    sourceLabel = "Synthesis Output",
                    category = MermaidDiagramCategory.SYNTHESIS
                )
            )
        }

        // 3. Extract from persisted repository files (.mmd/.mermaid or fenced blocks inside .md)
        project?.parsedFiles()?.forEach { file ->
            val ext = file.relativePath.substringAfterLast('.', "").lowercase()
            val fileName = file.relativePath.substringAfterLast('/')
            if (ext == "mmd" || ext == "mermaid") {
                val cleanCode = sanitizeMermaidSyntax(file.content)
                if (cleanCode.isNotBlank()) {
                    extracted.add(
                        MermaidDiagramSpec(
                            id = "repo_mmd_${file.relativePath.hashCode()}",
                            title = fileName.substringBeforeLast('.'),
                            subtitle = "Repo · ${file.relativePath}",
                            category = MermaidDiagramCategory.EXTRACTED,
                            code = cleanCode,
                            suggestedFileName = fileName
                        )
                    )
                }
            } else if (ext == "md" || ext == "markdown") {
                extracted.addAll(
                    extractMermaidBlocks(
                        markdown = file.content,
                        sourceLabel = fileName,
                        category = MermaidDiagramCategory.EXTRACTED
                    )
                )
            }
        }

        val effectiveProject = project ?: ResearchProject(
            id = "live_session",
            title = fallbackTitle.ifBlank { "Live Research Synthesis" },
            detectedDomain = inferDomainFromStage0(stages),
            complexityScore = inferComplexityFromStage0(stages)
        )

        // 4. Dynamic live 7-Stage Pipeline Blueprint (updates live as stages stream in)
        val completedCount = stages.count { it.status == "COMPLETED" }
        val synthesisSpec = MermaidDiagramSpec(
            id = "auto_research_synthesis_${effectiveProject.id}",
            title = "7-Stage Synthesis Blueprint",
            subtitle = "${effectiveProject.detectedDomain.ifBlank { "Research Pipeline" }} · $completedCount/${stages.size.coerceAtLeast(8)} Stages",
            category = MermaidDiagramCategory.SYNTHESIS,
            code = buildResearchSynthesisDiagram(effectiveProject, stages),
            suggestedFileName = "research_synthesis_blueprint.mmd"
        )

        // 5. Dynamic Synthesis Knowledge Map built from Stage 4 / synthesis markdown headings
        val findingsCode = buildSynthesisFindingsDiagram(effectiveProject, stages, synthesisMarkdown)
        val findingsSpec = findingsCode?.let { code ->
            MermaidDiagramSpec(
                id = "auto_synthesis_findings_${effectiveProject.id}",
                title = "Synthesis Architecture Map",
                subtitle = "Derived dynamically from Stage 4 & pipeline headings",
                category = MermaidDiagramCategory.SYNTHESIS,
                code = code,
                suggestedFileName = "synthesis_architecture_map.mmd"
            )
        }

        // 6. Model Routing & Tri-Sync Architecture
        val modelArchSpec = MermaidDiagramSpec(
            id = "auto_model_architecture_${effectiveProject.id}",
            title = "Model Routing & Architecture",
            subtitle = if (routingPrefs.strategicRoutingEnabled) {
                "${routingPrefs.routingMode.label} · Threshold ${routingPrefs.escalationThreshold}+"
            } else {
                "Locked to Gemini 3.8 Flash"
            },
            category = MermaidDiagramCategory.MODEL_ARCHITECTURE,
            code = buildModelArchitectureDiagram(effectiveProject, stages, routingPrefs, roundtableItems),
            suggestedFileName = "model_routing_architecture.mmd"
        )

        // Prioritize AI-extracted synthesis diagrams first when present, followed by live blueprints
        val ordered = buildList {
            addAll(extracted)
            add(synthesisSpec)
            if (findingsSpec != null) add(findingsSpec)
            add(modelArchSpec)
        }
        return ordered.distinctBy { it.code.trim() }
    }

    fun collectProjectDiagrams(
        project: ResearchProject,
        stages: List<StageRecord>,
        routingPrefs: RoutingPreferences,
        roundtableItems: List<RoundtableItem> = emptyList()
    ): List<MermaidDiagramSpec> = collectSynthesisPipelineDiagrams(
        stages = stages,
        synthesisMarkdown = project.chatAnswer,
        project = project,
        fallbackTitle = project.title,
        routingPrefs = routingPrefs,
        roundtableItems = roundtableItems
    )

    private fun inferDomainFromStage0(stages: List<StageRecord>): String {
        val text = stages.firstOrNull { it.stageNumber == 0 }?.content.orEmpty()
        val match = Regex("""Domain:\s*([^\n·|]+)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.trim()?.take(48).orEmpty()
    }

    private fun inferComplexityFromStage0(stages: List<StageRecord>): Int {
        val text = stages.firstOrNull { it.stageNumber == 0 }?.content.orEmpty()
        val match = Regex("""(\d{1,2})\s*/\s*10""").find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 6
    }

    private fun buildSynthesisFindingsDiagram(
        project: ResearchProject,
        stages: List<StageRecord>,
        synthesisMarkdown: String
    ): String? {
        val stage4 = stages.firstOrNull { it.stageNumber == 4 }?.content.orEmpty()
        val stage5 = stages.firstOrNull { it.stageNumber == 5 }?.content.orEmpty()
        val stage6 = stages.firstOrNull { it.stageNumber == 6 }?.content.orEmpty()
        val corpus = listOf(stage4, synthesisMarkdown, stage6).firstOrNull { it.isNotBlank() }.orEmpty()
        if (corpus.isBlank()) return null

        val headings = corpus.lines()
            .map { it.trim() }
            .filter { it.startsWith("##") }
            .map { it.trimStart('#').trim() }
            .filter { it.length >= 4 && !it.startsWith("```") }
            .distinctBy { it.lowercase() }
            .take(6)
        if (headings.size < 2) return null

        val rootTitle = sanitizeLabel(project.title.ifBlank { "Unified Synthesis" }, 38)
        val gapHeadings = stage5.lines()
            .map { it.trim() }
            .filter { it.startsWith("###") || it.startsWith("- **") }
            .map { it.trimStart('#', '-', '*', ' ').substringBefore(':').replace("**", "").trim() }
            .filter { it.length in 4..44 }
            .distinctBy { it.lowercase() }
            .take(3)

        return buildString {
            appendLine("flowchart TD")
            appendLine("    ROOT([\"$rootTitle\"])")
            headings.forEachIndexed { idx, h ->
                appendLine("    H$idx[\"${sanitizeLabel(h, 40)}\"]")
                appendLine("    ROOT --> H$idx")
            }
            if (gapHeadings.isNotEmpty()) {
                appendLine("    subgraph GAPS[\"Empirical Audit & Mitigations\"]")
                gapHeadings.forEachIndexed { idx, g ->
                    appendLine("        G$idx{\"${sanitizeLabel(g, 36)}\"}")
                }
                appendLine("    end")
                val anchorIdx = headings.lastIndex.coerceAtLeast(0)
                gapHeadings.indices.forEach { idx ->
                    appendLine("    H$anchorIdx -.-> G$idx")
                }
            }
            appendLine("    classDef root fill:#003640,stroke:#00E5FF,color:#EAF2FA,stroke-width:2px")
            appendLine("    classDef node fill:#121A26,stroke:#34D399,color:#ECFDF5,stroke-width:1.5px")
            appendLine("    classDef gap fill:#3D2808,stroke:#FFB74D,color:#FFFBEB,stroke-width:1.5px")
            appendLine("    class ROOT root")
            headings.indices.forEach { appendLine("    class H$it node") }
            gapHeadings.indices.forEach { appendLine("    class G$it gap") }
        }.trimEnd()
    }

    fun collectPrdDiagrams(
        project: ResearchProject,
        prd: PrdDocument
    ): List<MermaidDiagramSpec> {
        val extracted = extractMermaidBlocks(
            markdown = prd.markdownContent,
            sourceLabel = "PRD ${prd.version}",
            category = MermaidDiagramCategory.SYNTHESIS
        )
        if (extracted.isNotEmpty()) return extracted

        val safeTitle = sanitizeLabel(prd.title.ifBlank { project.title }, maxLen = 40)
        val safeDomain = sanitizeLabel(project.detectedDomain.ifBlank { "Systems Architecture" }, maxLen = 30)
        val safeModel = sanitizeLabel(prd.modelUsed.ifBlank { "Gemini" }, maxLen = 28)
        val safeDelta = sanitizeLabel(prd.deltaSummary.ifBlank { "Approved baseline requirements" }, maxLen = 46)

        val fallbackPrdFlow = """
            flowchart TD
                REQ["PRD ${sanitizeLabel(prd.version, 12)}: $safeTitle"]
                DOM["Domain: $safeDomain"]
                SYN["Synthesized by: $safeModel"]
                DEL["Delta: $safeDelta"]
                ROAD["Phased Implementation & Acceptance"]
                REQ --> DOM
                DOM --> SYN
                SYN --> DEL
                DEL --> ROAD
                classDef primary fill:#121A26,stroke:#00E5FF,color:#EAF2FA,stroke-width:2px
                classDef emerald fill:#093625,stroke:#34D399,color:#ECFDF5,stroke-width:2px
                class REQ,ROAD emerald
                class DOM,SYN,DEL primary
        """.trimIndent()

        return listOf(
            MermaidDiagramSpec(
                id = "prd_auto_${prd.id}",
                title = "PRD ${prd.version} Architecture Flow",
                subtitle = "${prd.version} · ${prd.modelUsed}",
                category = MermaidDiagramCategory.SYNTHESIS,
                code = fallbackPrdFlow,
                suggestedFileName = "prd_${prd.version.lowercase()}_architecture.mmd"
            )
        )
    }

    fun buildResearchSynthesisDiagram(
        project: ResearchProject,
        stages: List<StageRecord>
    ): String {
        val stageMap = stages.associateBy { it.stageNumber }
        val domain = sanitizeLabel(project.detectedDomain.ifBlank { "General Research" }, 30)
        val title = sanitizeLabel(project.title.ifBlank { project.rootFolderName.ifBlank { "Research Project" } }, 36)
        val gap = GapVerdict.fromWire(project.gapVerdict)

        fun statusTag(num: Int): String = when (stageMap[num]?.status) {
            "COMPLETED" -> "DONE"
            "RUNNING" -> "LIVE"
            "FAILED" -> "ERR"
            "SKIPPED" -> "SKIP"
            else -> "WAIT"
        }

        fun classFor(num: Int): String = when (stageMap[num]?.status) {
            "COMPLETED" -> "done"
            "RUNNING" -> "active"
            "FAILED" -> "err"
            "SKIPPED" -> "muted"
            else -> "pending"
        }

        val s0Sources = stageMap[0]?.sources?.size ?: 0
        val s1Sources = stageMap[1]?.sources?.size ?: 0
        val s4Sources = stageMap[4]?.sources?.size ?: 0
        val prdLabel = if (project.latestPrdVersion.isNotBlank()) {
            "Approved PRD ${sanitizeLabel(project.latestPrdVersion, 12)}"
        } else {
            "Ready for PRD Approval"
        }

        return buildString {
            appendLine("flowchart TD")
            appendLine("    Q[\"Query: $title\"]")
            appendLine("    S0[\"Stage 0 · Triage · $domain (${project.complexityScore}/10) [${statusTag(0)}]\"]")
            appendLine("    subgraph P1[\"Pass 1 · Grounded Inquiry & Expert Panel\"]")
            appendLine("        S1[\"Stage 1 · Problem Framing · $s1Sources src [${statusTag(1)}]\"]")
            appendLine("        S2[\"Stage 2 · Domain Expert Panel [${statusTag(2)}]\"]")
            appendLine("        S3[\"Stage 3 · Multi-Lens Critique [${statusTag(3)}]\"]")
            appendLine("        S1 --> S2 --> S3")
            appendLine("    end")
            appendLine("    subgraph P2[\"Pass 2 · Unified Synthesis & Empirical Audit\"]")
            appendLine("        S4[\"Stage 4 · Unified Synthesis · $s4Sources src [${statusTag(4)}]\"]")
            appendLine("        S5{\"Stage 5 · Gap Audit: ${sanitizeLabel(gap.label, 24)} [${statusTag(5)}]\"}")
            appendLine("        S4 --> S5")
            appendLine("    end")
            appendLine("    subgraph P3[\"Pass 3 · Bridging & Production Spec\"]")
            appendLine("        S6[\"Stage 6 · Emulation Layer Architecture [${statusTag(6)}]\"]")
            appendLine("        S7[\"Stage 7 · Mitigation Methodology [${statusTag(7)}]\"]")
            appendLine("        PRD([\"$prdLabel\"])")
            appendLine("        S6 --> S7 --> PRD")
            appendLine("    end")
            appendLine("    Q --> S0")
            appendLine("    S0 -->|\"$s0Sources refs\"| S1")
            appendLine("    S3 --> S4")
            if (gap == GapVerdict.RESOLVED) {
                appendLine("    S5 -->|\"Resolved\"| PRD")
                appendLine("    S5 -.->|\"Skipped\"| S6")
            } else {
                appendLine("    S5 -->|\"Gap Detected\"| S6")
            }
            appendLine("    classDef done fill:#093625,stroke:#34D399,color:#ECFDF5,stroke-width:2px")
            appendLine("    classDef active fill:#003640,stroke:#00E5FF,color:#EAF2FA,stroke-width:2px")
            appendLine("    classDef err fill:#420D19,stroke:#FF5370,color:#FFE4E6,stroke-width:2px")
            appendLine("    classDef muted fill:#182334,stroke:#637691,color:#94A8C2,stroke-dasharray:4 4")
            appendLine("    classDef pending fill:#121A26,stroke:#1E2B3D,color:#94A8C2")
            appendLine("    class Q active")
            for (n in 0..7) {
                appendLine("    class S$n ${classFor(n)}")
            }
            appendLine("    class PRD ${if (project.latestPrdVersion.isNotBlank()) "done" else "active"}")
        }.trimEnd()
    }

    fun buildModelArchitectureDiagram(
        project: ResearchProject,
        stages: List<StageRecord>,
        routingPrefs: RoutingPreferences,
        roundtableItems: List<RoundtableItem>
    ): String {
        val domain = sanitizeLabel(project.detectedDomain.ifBlank { "Systems Architecture" }, 28)
        val threshold = routingPrefs.escalationThreshold
        val complexity = project.complexityScore.coerceIn(1, 10)
        val stage0Model = sanitizeLabel(stages.firstOrNull { it.stageNumber == 0 }?.modelUsed.orEmpty().ifBlank { "gemini-3.8-flash" }, 26)
        val pass1Model = sanitizeLabel(stages.firstOrNull { it.stageNumber == 1 }?.modelUsed.orEmpty().ifBlank { "gemini-3.8-flash" }, 26)
        val pass2Model = sanitizeLabel(
            stages.firstOrNull { it.stageNumber == 4 }?.modelUsed.orEmpty().ifBlank {
                if (routingPrefs.strategicRoutingEnabled && complexity >= threshold) "gemini-4-argon" else "gemini-3.8-flash"
            },
            26
        )
        val pass3Model = sanitizeLabel(
            stages.firstOrNull { it.stageNumber == 6 }?.modelUsed.orEmpty().ifBlank { pass2Model },
            26
        )
        val configuredDomainModel = routingPrefs.domainModelMatrix[project.detectedDomain]
            ?.takeIf { it.isNotBlank() && routingPrefs.externalEnabled && routingPrefs.externalApiKey.isNotBlank() && !it.startsWith("gemini-") }
        val roundtableModels = roundtableItems
            .map { it.modelFolder.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)

        val githubConnected = project.githubRepoUrl.isNotBlank()
        val githubSlug = project.githubRepoUrl
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .ifBlank { "Linked" }

        return buildString {
            appendLine("flowchart LR")
            appendLine("    subgraph INGRESS[\"1 · Query & Policy Gate\"]")
            appendLine("        USR[\"User Brief · $domain\"]")
            appendLine("        ENV[\"Local .env Vault · AES-256\"]")
            appendLine("        TRIAGE[\"Stage 0 Triage · $stage0Model\"]")
            appendLine("        USR --> TRIAGE")
            appendLine("        ENV -.->|\"Permission Guard\"| TRIAGE")
            appendLine("    end")
            appendLine("    subgraph ROUTER[\"2 · Strategic Model Router\"]")
            if (routingPrefs.strategicRoutingEnabled) {
                appendLine("        GATE{\"Score $complexity/10 >= $threshold? (${sanitizeLabel(routingPrefs.routingMode.label, 18)})\"}")
            } else {
                appendLine("        GATE{\"Strategic Routing OFF · Flash Lock\"}")
            }
            appendLine("        M_P1[\"Pass 1 Inquiry: $pass1Model\"]")
            appendLine("        M_P2[\"Pass 2 Synthesis: $pass2Model\"]")
            appendLine("        M_P3[\"Pass 3 Emulation: $pass3Model\"]")
            appendLine("        GATE --> M_P1")
            appendLine("        GATE --> M_P2")
            appendLine("        M_P2 --> M_P3")
            if (configuredDomainModel != null) {
                appendLine("        EXT[\"External Endpoint: ${sanitizeLabel(configuredDomainModel, 24)}\"]")
                appendLine("        GATE -.->|\"Domain Override\"| EXT")
            }
            appendLine("    end")
            appendLine("    subgraph RT[\"3 · AI Roundtable Scrutiny\"]")
            if (roundtableModels.isEmpty()) {
                appendLine("        RT_EMPTY[\"Cross-Model Audit Ready\"]")
            } else {
                roundtableModels.forEachIndexed { idx, folder ->
                    val count = roundtableItems.count { it.modelFolder == folder }
                    appendLine("        RT_$idx[\"${sanitizeLabel(folder, 22)} · $count file(s)\"]")
                }
            }
            appendLine("    end")
            appendLine("    subgraph SYNC[\"4 · Tri-Sync Persistence\"]")
            appendLine("        ROOM[(\"Room Local Cache\")]")
            appendLine("        FS[(\"Cloud Firestore\")]")
            appendLine("        GH[\"GitHub: ${if (githubConnected) sanitizeLabel(githubSlug, 22) else "Ready"}\"]")
            appendLine("        GD[\"Google Drive Mirror\"]")
            appendLine("        ROOM <--> FS")
            appendLine("        ROOM --> GH")
            appendLine("        ROOM --> GD")
            appendLine("    end")
            appendLine("    TRIAGE --> GATE")
            if (roundtableModels.isEmpty()) {
                appendLine("    M_P3 --> RT_EMPTY")
                appendLine("    RT_EMPTY --> ROOM")
            } else {
                roundtableModels.indices.forEach { idx ->
                    appendLine("    M_P3 --> RT_$idx")
                    appendLine("    RT_$idx --> ROOM")
                }
            }
            appendLine("    classDef cyan fill:#003640,stroke:#00E5FF,color:#EAF2FA,stroke-width:2px")
            appendLine("    classDef emerald fill:#093625,stroke:#34D399,color:#ECFDF5,stroke-width:2px")
            appendLine("    classDef amber fill:#3D2808,stroke:#FFB74D,color:#FFFBEB,stroke-width:2px")
            appendLine("    classDef slate fill:#121A26,stroke:#7C9EFF,color:#EAF2FA")
            appendLine("    class USR,TRIAGE,M_P1 cyan")
            appendLine("    class GATE,M_P2,M_P3 amber")
            appendLine("    class ENV,ROOM,FS emerald")
            if (githubConnected) appendLine("    class GH emerald") else appendLine("    class GH slate")
            appendLine("    class GD slate")
            if (configuredDomainModel != null) appendLine("    class EXT amber")
        }.trimEnd()
    }

    fun sanitizeLabel(raw: String, maxLen: Int = 44): String {
        val cleaned = raw
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\"", "'")
            .replace("<", "")
            .replace(">", "")
            .replace("[", "(")
            .replace("]", ")")
            .replace("{", "(")
            .replace("}", ")")
            .replace("`", "")
            .replace("|", "/")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > maxLen) cleaned.take(maxLen - 1).trimEnd() + "…" else cleaned
    }

    /**
     * Produces a JSON string literal safe for embedding inside an inline <script> tag.
     * Escapes `<`, `>`, `&`, and JS line separators so untrusted diagram text can never break
     * out of the script context or inject HTML markup.
     */
    fun toSafeJsStringLiteral(raw: String): String =
        JSONObject.quote(raw)
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
            .replace("&", "\\u0026")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

    /**
     * Builds the sandboxed, CSP-protected HTML document that renders Mermaid.js diagrams with
     * `securityLevel: 'strict'` and `htmlLabels: false`, plus a zero-dependency offline SVG
     * fallback engine when the device has no network connection.
     */
    fun buildSandboxedViewerHtml(
        initialCode: String,
        theme: MermaidThemePreset
    ): String {
        val safeCodeLiteral = toSafeJsStringLiteral(initialCode)
        val safeThemeId = toSafeJsStringLiteral(theme.id)
        val isDark = theme != MermaidThemePreset.BLUEPRINT

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes" />
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; style-src 'unsafe-inline'; img-src data:; font-src data:;" />
<style>
  * { box-sizing: border-box; }
  html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    min-height: 100%;
    background: ${theme.canvasBgHex};
    color: ${theme.textHex};
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: auto;
    -webkit-overflow-scrolling: touch;
  }
  #viewport {
    width: 100%;
    min-height: 100vh;
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding: 14px;
  }
  #canvas {
    transform-origin: top center;
    transition: transform 0.14s ease-out;
    display: inline-block;
    max-width: 100%;
  }
  #canvas svg {
    display: block;
    max-width: none;
    height: auto;
  }
  #error-box {
    display: none;
    margin: 12px;
    padding: 12px 14px;
    border-radius: 10px;
    border: 1px solid #FF5370;
    background: rgba(255, 83, 112, 0.12);
    color: ${theme.textHex};
    font-family: monospace;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-word;
  }
</style>
<script src="$PINNED_MERMAID_CDN_URL" async onload="window.__onMermaidLoaded()" onerror="window.__onMermaidLoadFailed()"></script>
</head>
<body>
  <div id="viewport">
    <div id="canvas"></div>
  </div>
  <div id="error-box"></div>
<script>
(function() {
  var THEMES = {
    obsidian: {
      dark: true,
      bg: '#0B1018', nodeBg: '#121A26', nodeBorder: '#00E5FF',
      text: '#EAF2FA', edge: '#7C9EFF', clusterBg: '#0F1724',
      clusterBorder: '#1E2B3D', emerald: '#34D399', amber: '#FFB74D'
    },
    blueprint: {
      dark: false,
      bg: '#F8FAFC', nodeBg: '#FFFFFF', nodeBorder: '#0284C7',
      text: '#0F172A', edge: '#334155', clusterBg: '#EFF6FF',
      clusterBorder: '#93C5FD', emerald: '#059669', amber: '#D97706'
    },
    emerald: {
      dark: true,
      bg: '#050B0A', nodeBg: '#0C1E1B', nodeBorder: '#34D399',
      text: '#ECFDF5', edge: '#2DD4BF', clusterBg: '#081614',
      clusterBorder: '#134E4A', emerald: '#34D399', amber: '#FBBF24'
    }
  };

  var state = {
    code: $safeCodeLiteral,
    themeId: $safeThemeId,
    zoom: 1.0,
    mermaidReady: false,
    mermaidFailed: false,
    renderSeq: 0
  };

  function getTheme() {
    return THEMES[state.themeId] || THEMES.obsidian;
  }

  function applySurfaceTheme() {
    var t = getTheme();
    document.documentElement.style.background = t.bg;
    document.body.style.background = t.bg;
    document.body.style.color = t.text;
  }

  function configureMermaid() {
    if (!window.mermaid) return;
    var t = getTheme();
    window.mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      htmlLabels: false,
      suppressErrorRendering: true,
      theme: 'base',
      themeVariables: {
        darkMode: t.dark,
        background: t.bg,
        primaryColor: t.nodeBg,
        primaryTextColor: t.text,
        primaryBorderColor: t.nodeBorder,
        secondaryColor: t.clusterBg,
        secondaryTextColor: t.text,
        secondaryBorderColor: t.edge,
        tertiaryColor: t.clusterBg,
        tertiaryTextColor: t.text,
        lineColor: t.edge,
        textColor: t.text,
        mainBkg: t.nodeBg,
        nodeBorder: t.nodeBorder,
        clusterBkg: t.clusterBg,
        clusterBorder: t.clusterBorder,
        titleColor: t.text,
        edgeLabelBackground: t.bg,
        actorBkg: t.nodeBg,
        actorBorder: t.nodeBorder,
        actorTextColor: t.text,
        signalColor: t.edge,
        signalTextColor: t.text,
        fontFamily: '-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif',
        fontSize: '13px'
      },
      flowchart: { htmlLabels: false, curve: 'basis', useMaxWidth: false, padding: 14 },
      sequence: { useMaxWidth: false, actorMargin: 44, messageMargin: 32 }
    });
  }

  function clearEl(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
  }

  function showError(msg) {
    var box = document.getElementById('error-box');
    box.style.display = 'block';
    box.textContent = 'Mermaid syntax error:\n' + String(msg || 'Invalid syntax');
    console.log('VORTEX_MERMAID:ERROR:' + String(msg || 'Invalid syntax').replace(/\n/g, ' ').slice(0, 180));
  }

  function hideError() {
    var box = document.getElementById('error-box');
    box.style.display = 'none';
    box.textContent = '';
  }

  function renderOfflineFallback(code) {
    var t = getTheme();
    var canvas = document.getElementById('canvas');
    clearEl(canvas);
    hideError();

    var NS = 'http://www.w3.org/2000/svg';
    var lines = String(code || '').replace(/\r\n/g, '\n').split('\n');
    var nodes = [];
    var nodeMap = {};
    var edges = [];

    function cleanLbl(raw) {
      return String(raw || '')
        .replace(/^[\[\(\{\"]+|[\]\)\}\"]+$/g, '')
        .replace(/\"/g, '')
        .trim();
    }

    function ensureNode(id, label) {
      var k = String(id || '').trim();
      if (!k) return null;
      if (!nodeMap[k]) {
        nodeMap[k] = { id: k, label: cleanLbl(label) || k };
        nodes.push(nodeMap[k]);
      } else if (label && cleanLbl(label).length > nodeMap[k].label.length) {
        nodeMap[k].label = cleanLbl(label);
      }
      return nodeMap[k];
    }

    var tokenRe = /([A-Za-z0-9_]+)\s*(?:\[\(\"?([^\)\]]+)\"?\)\]|\(\[\"?([^\]\)]+)\"?\]\)|\[\"?([^\]]+)\"?\]|\{\"?([^\}]+)\"?\}|\(\"?([^\)]+)\"?\))?/;
    var edgeRe = /^(.+?)\s*(?:<-->|-->|-\.->|==>|---|->>|-->>)\s*(?:\|([^|]+)\|)?\s*(.+)$/;

    for (var i = 0; i < lines.length; i++) {
      var ln = lines[i].trim();
      if (!ln || ln.indexOf('%%') === 0 || /^(flowchart|graph|sequenceDiagram|subgraph|end|classDef|class |style |linkStyle)/i.test(ln)) {
        continue;
      }
      var em = ln.match(edgeRe);
      if (em) {
        var leftParts = em[1].split(/-->|-\.->|==>/);
        var fromM = leftParts[leftParts.length - 1].trim().match(tokenRe);
        var toM = em[3].trim().match(tokenRe);
        if (fromM && toM) {
          var fn = ensureNode(fromM[1], fromM[2] || fromM[3] || fromM[4] || fromM[5] || fromM[6]);
          var tn = ensureNode(toM[1], toM[2] || toM[3] || toM[4] || toM[5] || toM[6]);
          if (fn && tn) edges.push({ from: fn.id, to: tn.id, label: cleanLbl(em[2] || '') });
        }
      } else {
        var nm = ln.match(tokenRe);
        if (nm && nm[1] && (nm[2] || nm[3] || nm[4] || nm[5] || nm[6])) {
          ensureNode(nm[1], nm[2] || nm[3] || nm[4] || nm[5] || nm[6]);
        }
      }
    }

    if (nodes.length === 0) {
      ensureNode('N0', 'Mermaid Diagram Ready (Offline Preview)');
    }

    var nodeW = 240, nodeH = 48, gapY = 30, padX = 28, padY = 24;
    var svgW = nodeW + padX * 2;
    var svgH = padY * 2 + nodes.length * nodeH + Math.max(0, nodes.length - 1) * gapY;

    var svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('xmlns', NS);
    svg.setAttribute('width', String(svgW));
    svg.setAttribute('height', String(svgH));
    svg.setAttribute('viewBox', '0 0 ' + svgW + ' ' + svgH);

    var defs = document.createElementNS(NS, 'defs');
    var marker = document.createElementNS(NS, 'marker');
    marker.setAttribute('id', 'vortexArrow');
    marker.setAttribute('markerWidth', '8');
    marker.setAttribute('markerHeight', '8');
    marker.setAttribute('refX', '7');
    marker.setAttribute('refY', '4');
    marker.setAttribute('orient', 'auto');
    var poly = document.createElementNS(NS, 'polygon');
    poly.setAttribute('points', '0 0, 8 4, 0 8');
    poly.setAttribute('fill', t.edge);
    marker.appendChild(poly);
    defs.appendChild(marker);
    svg.appendChild(defs);

    var pos = {};
    for (var j = 0; j < nodes.length; j++) {
      pos[nodes[j].id] = { x: padX, y: padY + j * (nodeH + gapY) };
    }

    for (var e = 0; e < edges.length; e++) {
      var ed = edges[e];
      var a = pos[ed.from], b = pos[ed.to];
      if (!a || !b) continue;
      var path = document.createElementNS(NS, 'path');
      var x1 = a.x + nodeW / 2, y1 = a.y + nodeH;
      var x2 = b.x + nodeW / 2, y2 = b.y;
      var d = (Math.abs(y2 - y1) <= gapY + 6 && y2 >= y1)
        ? ('M ' + x1 + ' ' + y1 + ' L ' + x2 + ' ' + y2)
        : ('M ' + (a.x + nodeW) + ' ' + (a.y + nodeH / 2) + ' C ' + (a.x + nodeW + 24) + ' ' + (a.y + nodeH / 2) + ', ' + (b.x + nodeW + 24) + ' ' + (b.y + nodeH / 2) + ', ' + (b.x + nodeW) + ' ' + (b.y + nodeH / 2));
      path.setAttribute('d', d);
      path.setAttribute('fill', 'none');
      path.setAttribute('stroke', t.edge);
      path.setAttribute('stroke-width', '1.8');
      path.setAttribute('marker-end', 'url(#vortexArrow)');
      svg.appendChild(path);
    }

    for (var k = 0; k < nodes.length; k++) {
      var nd = nodes[k];
      var p = pos[nd.id];
      var rect = document.createElementNS(NS, 'rect');
      rect.setAttribute('class', 'vortex-node');
      rect.setAttribute('data-node-id', nd.id);
      rect.setAttribute('data-node-label', nd.label);
      rect.setAttribute('x', String(p.x));
      rect.setAttribute('y', String(p.y));
      rect.setAttribute('width', String(nodeW));
      rect.setAttribute('height', String(nodeH));
      rect.setAttribute('rx', '10');
      rect.setAttribute('fill', t.nodeBg);
      rect.setAttribute('stroke', k === nodes.length - 1 ? t.emerald : t.nodeBorder);
      rect.setAttribute('stroke-width', '1.6');
      svg.appendChild(rect);

      var txt = document.createElementNS(NS, 'text');
      txt.setAttribute('x', String(p.x + nodeW / 2));
      txt.setAttribute('y', String(p.y + nodeH / 2 + 4));
      txt.setAttribute('text-anchor', 'middle');
      txt.setAttribute('fill', t.text);
      txt.setAttribute('font-size', '12');
      txt.setAttribute('font-weight', '500');
      txt.textContent = nd.label.length > 34 ? (nd.label.slice(0, 33) + '…') : nd.label;
      svg.appendChild(txt);
    }

    canvas.appendChild(svg);
    applyZoom();
    scheduleViewportReport(true);
    console.log('VORTEX_MERMAID:RENDERED:OFFLINE_SVG');
  }

  var latestNodesJson = '[]';
  var viewportRaf = 0;

  function cleanMermaidNodeId(rawId, idx) {
    var s = String(rawId || '').trim();
    if (!s) return 'node_' + idx;
    var m = s.match(/^flowchart-([A-Za-z0-9_-]+?)-\d+$/);
    if (m && m[1]) return m[1];
    return s;
  }

  function collectRenderedNodes(svg, svgRect) {
    if (!svg || !svgRect || svgRect.width <= 1 || svgRect.height <= 1) return [];
    var selectors = [
      'g.node',
      'g.actor',
      'g.statediagram-state',
      'g.classGroup',
      'g.entityBox',
      'g.mindmap-node',
      '.vortex-node'
    ].join(',');
    var els = svg.querySelectorAll(selectors);
    var out = [];
    var seen = {};
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var r = el.getBoundingClientRect();
      if (!r || r.width < 10 || r.height < 10) continue;
      var rawId = el.getAttribute('data-node-id') || el.getAttribute('data-id') || el.getAttribute('id') || ('node_' + i);
      var id = cleanMermaidNodeId(rawId, i);
      var label = el.getAttribute('data-node-label') || (el.textContent || '').replace(/\s+/g, ' ').trim();
      if (!label) label = id;
      if (label.length > 56) label = label.slice(0, 55) + '…';
      var key = id + ':' + Math.round(r.left) + ':' + Math.round(r.top);
      if (seen[key]) continue;
      seen[key] = true;
      out.push({
        id: id,
        label: label,
        u: Number(((r.left - svgRect.left) / svgRect.width).toFixed(4)),
        v: Number(((r.top - svgRect.top) / svgRect.height).toFixed(4)),
        w: Number((r.width / svgRect.width).toFixed(4)),
        h: Number((r.height / svgRect.height).toFixed(4))
      });
    }
    return out;
  }

  function reportViewportAndNodes(includeNodes) {
    var svg = document.querySelector('#canvas svg');
    var vw = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    var vh = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    if (!svg) return;
    var r = svg.getBoundingClientRect();
    if (!r || r.width < 4 || r.height < 4) return;
    var relL = (r.left / vw).toFixed(4);
    var relT = (r.top / vh).toFixed(4);
    var relW = (r.width / vw).toFixed(4);
    var relH = (r.height / vh).toFixed(4);
    var nodeFlag = 0;
    if (includeNodes) {
      var nodes = collectRenderedNodes(svg, r);
      latestNodesJson = JSON.stringify(nodes);
      nodeFlag = nodes.length;
    }
    console.log('VORTEX_MERMAID:VIEWPORT:' + relL + ',' + relT + ',' + relW + ',' + relH + ',' + nodeFlag);
  }

  function scheduleViewportReport(includeNodes) {
    if (viewportRaf) cancelAnimationFrame(viewportRaf);
    viewportRaf = requestAnimationFrame(function() {
      viewportRaf = 0;
      reportViewportAndNodes(!!includeNodes);
    });
  }

  window.addEventListener('scroll', function() { scheduleViewportReport(false); }, { passive: true, capture: true });
  window.addEventListener('resize', function() { scheduleViewportReport(true); }, { passive: true });
  if (window.visualViewport) {
    window.visualViewport.addEventListener('scroll', function() { scheduleViewportReport(false); });
    window.visualViewport.addEventListener('resize', function() { scheduleViewportReport(true); });
  }

  function applyZoom() {
    var canvas = document.getElementById('canvas');
    canvas.style.transform = 'scale(' + state.zoom.toFixed(2) + ')';
    console.log('VORTEX_MERMAID:ZOOM:' + Math.round(state.zoom * 100));
    scheduleViewportReport(false);
    setTimeout(function() { reportViewportAndNodes(false); }, 180);
  }

  function fitDiagramToViewport() {
    var viewport = document.getElementById('viewport');
    var svg = document.querySelector('#canvas svg');
    if (!viewport || !svg) {
      applyZoom();
      return;
    }
    var availW = Math.max(200, viewport.clientWidth - 28);
    var rect = svg.getBoundingClientRect();
    var naturalW = rect.width / (state.zoom || 1.0);
    if (naturalW > availW) {
      state.zoom = Math.max(0.45, Math.min(1.0, availW / naturalW));
    } else {
      state.zoom = 1.0;
    }
    applyZoom();
  }

  function renderCurrent() {
    applySurfaceTheme();
    if (!state.code || !state.code.trim()) {
      clearEl(document.getElementById('canvas'));
      showError('Empty Mermaid diagram.');
      return;
    }
    if (state.mermaidFailed || !window.mermaid) {
      if (state.mermaidFailed) {
        renderOfflineFallback(state.code);
      }
      return;
    }
    state.renderSeq += 1;
    var seq = state.renderSeq;
    var renderId = 'vortex_mmd_' + seq;
    configureMermaid();
    try {
      window.mermaid.render(renderId, state.code).then(function(res) {
        if (seq !== state.renderSeq) return;
        hideError();
        var canvas = document.getElementById('canvas');
        // Mermaid v11 with securityLevel:'strict' and htmlLabels:false produces sanitized SVG;
        // apply defense-in-depth DOM sanitization before attaching to the canvas.
        var parser = new DOMParser();
        var doc = parser.parseFromString(res.svg, 'image/svg+xml');
        var svgEl = doc.documentElement;
        clearEl(canvas);
        if (svgEl && svgEl.nodeName.toLowerCase() === 'svg') {
          var blockedTags = svgEl.querySelectorAll('script, foreignObject, iframe, object, embed');
          for (var b = 0; b < blockedTags.length; b++) {
            if (blockedTags[b].parentNode) blockedTags[b].parentNode.removeChild(blockedTags[b]);
          }
          var allEls = svgEl.querySelectorAll('*');
          for (var a = 0; a < allEls.length; a++) {
            var attrs = allEls[a].attributes;
            for (var ai = attrs.length - 1; ai >= 0; ai--) {
              var an = attrs[ai].name.toLowerCase();
              var av = String(attrs[ai].value || '').trim().toLowerCase();
              if (an.indexOf('on') === 0 || ((an === 'href' || an === 'xlink:href') && av.indexOf('javascript:') === 0)) {
                allEls[a].removeAttribute(attrs[ai].name);
              }
            }
          }
          canvas.appendChild(document.importNode(svgEl, true));
          fitDiagramToViewport();
          scheduleViewportReport(true);
          setTimeout(function() { reportViewportAndNodes(true); }, 200);
          console.log('VORTEX_MERMAID:RENDERED:MERMAID_JS');
        } else {
          renderOfflineFallback(state.code);
        }
      }).catch(function(err) {
        if (seq !== state.renderSeq) return;
        showError(err && err.message ? err.message : String(err));
      });
    } catch (syncErr) {
      showError(syncErr && syncErr.message ? syncErr.message : String(syncErr));
    }
  }

  window.__onMermaidLoaded = function() {
    state.mermaidReady = true;
    state.mermaidFailed = false;
    renderCurrent();
  };

  window.__onMermaidLoadFailed = function() {
    state.mermaidFailed = true;
    renderOfflineFallback(state.code);
  };

  window.vortexRender = function(newCode, newThemeId) {
    if (typeof newCode === 'string') state.code = newCode;
    if (typeof newThemeId === 'string' && THEMES[newThemeId]) state.themeId = newThemeId;
    renderCurrent();
  };

  window.vortexZoom = function(delta) {
    state.zoom = Math.max(0.4, Math.min(3.0, state.zoom + delta));
    applyZoom();
  };

  window.vortexResetZoom = function() {
    fitDiagramToViewport();
  };

  window.vortexRenderOfflineFallback = function() {
    renderOfflineFallback(state.code);
  };

  var exportPayloads = {};

  function utf8ToBase64(str) {
    if (typeof TextEncoder !== 'undefined') {
      var bytes = new TextEncoder().encode(str);
      var binary = '';
      var chunk = 0x8000;
      for (var i = 0; i < bytes.length; i += chunk) {
        binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
      }
      return btoa(binary);
    }
    return btoa(unescape(encodeURIComponent(str)));
  }

  window.vortexGetNodesJson = function() {
    return latestNodesJson || '[]';
  };

  function appendOverlayToExportSvg(clone, vbX, vbY, vbW, vbH, overlayJson) {
    if (!overlayJson) return;
    var board = null;
    try { board = JSON.parse(overlayJson); } catch (e) { return; }
    if (!board) return;
    var NS = 'http://www.w3.org/2000/svg';
    var g = document.createElementNS(NS, 'g');
    g.setAttribute('id', 'vortex-brainstorm-overlay');

    var strokes = Array.isArray(board.strokes) ? board.strokes : [];
    for (var i = 0; i < strokes.length; i++) {
      var s = strokes[i];
      if (!s || !Array.isArray(s.points) || s.points.length === 0) continue;
      var d = '';
      for (var p = 0; p < s.points.length; p++) {
        var x = vbX + s.points[p].u * vbW;
        var y = vbY + s.points[p].v * vbH;
        d += (p === 0 ? 'M ' : ' L ') + x.toFixed(1) + ' ' + y.toFixed(1);
      }
      var path = document.createElementNS(NS, 'path');
      path.setAttribute('d', d);
      path.setAttribute('fill', 'none');
      path.setAttribute('stroke', String(s.colorHex || '#00E5FF'));
      path.setAttribute('stroke-width', String(Math.max(1.5, Number(s.widthDp || 3))));
      path.setAttribute('stroke-opacity', String(Math.min(1, Math.max(0.15, Number(s.alpha || 1)))));
      path.setAttribute('stroke-linecap', 'round');
      path.setAttribute('stroke-linejoin', 'round');
      g.appendChild(path);
    }

    var notes = Array.isArray(board.notes) ? board.notes : [];
    for (var n = 0; n < notes.length; n++) {
      var nt = notes[n];
      if (!nt || !nt.text) continue;
      var nx = vbX + Number(nt.u || 0.5) * vbW;
      var ny = vbY + Number(nt.v || 0.5) * vbH;
      var cardW = 156;
      var cardH = 58;
      var box = document.createElementNS(NS, 'rect');
      box.setAttribute('x', (nx - 16).toFixed(1));
      box.setAttribute('y', (ny - 12).toFixed(1));
      box.setAttribute('width', String(cardW));
      box.setAttribute('height', String(cardH));
      box.setAttribute('rx', '8');
      box.setAttribute('fill', String(nt.colorHex || '#FDE68A'));
      box.setAttribute('fill-opacity', nt.resolved ? '0.75' : '0.96');
      box.setAttribute('stroke', '#0F172A');
      box.setAttribute('stroke-opacity', '0.28');
      box.setAttribute('stroke-width', '1');
      g.appendChild(box);

      var header = document.createElementNS(NS, 'text');
      header.setAttribute('x', (nx - 8).toFixed(1));
      header.setAttribute('y', (ny + 4).toFixed(1));
      header.setAttribute('fill', '#0F172A');
      header.setAttribute('font-size', '9');
      header.setAttribute('font-weight', '700');
      var hdrText = String(nt.tag || 'IDEA').toUpperCase() + (nt.anchoredNodeLabel ? (' · ' + nt.anchoredNodeLabel) : '');
      header.textContent = hdrText.length > 26 ? (hdrText.slice(0, 25) + '…') : hdrText;
      g.appendChild(header);

      var body = document.createElementNS(NS, 'text');
      body.setAttribute('x', (nx - 8).toFixed(1));
      body.setAttribute('y', (ny + 22).toFixed(1));
      body.setAttribute('fill', '#0F172A');
      body.setAttribute('font-size', '11');
      var txt = String(nt.text || '');
      body.textContent = txt.length > 28 ? (txt.slice(0, 27) + '…') : txt;
      g.appendChild(body);

      var meta = document.createElementNS(NS, 'text');
      meta.setAttribute('x', (nx - 8).toFixed(1));
      meta.setAttribute('y', (ny + 38).toFixed(1));
      meta.setAttribute('fill', '#334155');
      meta.setAttribute('font-size', '9');
      meta.textContent = '— ' + String(nt.authorName || 'Researcher').slice(0, 20);
      g.appendChild(meta);
    }

    clone.appendChild(g);
  }

  function buildStandaloneSvgPayload(overlayJson) {
    var svg = document.querySelector('#canvas svg');
    if (!svg) return null;
    var t = getTheme();
    var vbX = 0, vbY = 0, vbW = 0, vbH = 0;
    var vbAttr = svg.getAttribute('viewBox');
    if (vbAttr) {
      var parts = vbAttr.trim().split(/[\s,]+/).map(Number);
      if (parts.length === 4 && parts[2] > 0 && parts[3] > 0 && !isNaN(parts[0]) && !isNaN(parts[1])) {
        vbX = parts[0];
        vbY = parts[1];
        vbW = parts[2];
        vbH = parts[3];
      }
    }
    if (!(vbW > 0 && vbH > 0)) {
      try {
        var bb = svg.getBBox();
        if (bb && bb.width > 0 && bb.height > 0) {
          vbX = bb.x;
          vbY = bb.y;
          vbW = bb.width;
          vbH = bb.height;
        }
      } catch (e) {}
    }
    if (!(vbW > 0 && vbH > 0)) {
      var rect = svg.getBoundingClientRect();
      var z = state.zoom || 1.0;
      vbW = Math.max(120, Math.ceil(rect.width / z));
      vbH = Math.max(120, Math.ceil(rect.height / z));
    }

    var pad = 20;
    var outX = vbX - pad;
    var outY = vbY - pad;
    var outW = Math.ceil(vbW + pad * 2);
    var outH = Math.ceil(vbH + pad * 2);

    var clone = svg.cloneNode(true);
    clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    clone.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
    clone.setAttribute('viewBox', outX + ' ' + outY + ' ' + outW + ' ' + outH);
    clone.setAttribute('width', String(outW));
    clone.setAttribute('height', String(outH));
    clone.removeAttribute('style');

    var bgRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    bgRect.setAttribute('x', String(outX));
    bgRect.setAttribute('y', String(outY));
    bgRect.setAttribute('width', String(outW));
    bgRect.setAttribute('height', String(outH));
    bgRect.setAttribute('fill', t.bg);

    var insertBeforeNode = clone.firstChild;
    while (insertBeforeNode && (
      insertBeforeNode.nodeName.toLowerCase() === 'style' ||
      insertBeforeNode.nodeName.toLowerCase() === 'defs'
    )) {
      insertBeforeNode = insertBeforeNode.nextSibling;
    }
    if (insertBeforeNode) {
      clone.insertBefore(bgRect, insertBeforeNode);
    } else {
      clone.appendChild(bgRect);
    }

    if (overlayJson) {
      appendOverlayToExportSvg(clone, vbX, vbY, vbW, vbH, overlayJson);
    }

    var serialized = new XMLSerializer().serializeToString(clone);
    return {
      svg: '<?xml version="1.0" encoding="UTF-8"?>\n' + serialized,
      width: outW,
      height: outH,
      bg: t.bg
    };
  }

  window.vortexExportSvg = function(overlayJson) {
    var payload = buildStandaloneSvgPayload(overlayJson || '');
    return payload ? payload.svg : '';
  };

  window.vortexExportPngAsync = function(exportId, overlayJson) {
    var payload = buildStandaloneSvgPayload(overlayJson || '');
    if (!payload || !payload.svg) {
      console.log('VORTEX_MERMAID:EXPORT_ERR:' + exportId + ':No rendered diagram');
      return;
    }
    var maxDim = 4096;
    var scale = 2.0;
    var longest = Math.max(payload.width, payload.height);
    if (longest * scale > maxDim) {
      scale = Math.max(1.0, maxDim / longest);
    }
    var outW = Math.max(1, Math.round(payload.width * scale));
    var outH = Math.max(1, Math.round(payload.height * scale));

    try {
      var img = new Image();
      img.onload = function() {
        try {
          var c = document.createElement('canvas');
          c.width = outW;
          c.height = outH;
          var ctx = c.getContext('2d');
          ctx.fillStyle = payload.bg;
          ctx.fillRect(0, 0, outW, outH);
          ctx.drawImage(img, 0, 0, outW, outH);
          var dataUrl = c.toDataURL('image/png');
          if (dataUrl && dataUrl.indexOf('data:image/png;base64,') === 0) {
            exportPayloads[exportId] = dataUrl;
            console.log('VORTEX_MERMAID:EXPORT_PNG_READY:' + exportId);
          } else {
            console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + exportId);
          }
        } catch (canvasErr) {
          console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + exportId);
        }
      };
      img.onerror = function() {
        console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + exportId);
      };
      img.src = 'data:image/svg+xml;base64,' + utf8ToBase64(payload.svg);
    } catch (e) {
      console.log('VORTEX_MERMAID:EXPORT_PNG_FALLBACK:' + exportId);
    }
  };

  window.vortexTakeExportPayload = function(exportId) {
    var val = exportPayloads[exportId] || '';
    delete exportPayloads[exportId];
    return val;
  };

  // Fallback timer in case CDN hangs on slow/offline networks
  setTimeout(function() {
    if (!state.mermaidReady && !state.mermaidFailed) {
      state.mermaidFailed = true;
      renderOfflineFallback(state.code);
    }
  }, 4500);
})();
</script>
</body>
</html>
        """.trimIndent()
    }
}
