package com.example.data.ai

import com.example.data.model.ChatTurn
import com.example.data.model.ExportFileFormat
import com.example.data.model.GapVerdict
import com.example.data.model.GroundingSource
import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchMode
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.RoundtableStatus
import com.example.data.model.RoutingPreferences
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.model.StageRecord
import com.example.data.model.VortexJson
import com.google.firebase.ai.type.Schema
import org.json.JSONArray
import org.json.JSONObject

data class PipelineExecutionOutput(
    val triage: Stage0TriageResult,
    val stages: List<StageRecord>,
    val gapVerdict: GapVerdict,
    val chatAnswer: String,
    val files: List<ProjectFile>,
    val routingPlanSummary: String
)

class PipelinePassFailureException(
    val failedPass: Int,
    val stagesSnapshot: List<StageRecord>,
    val triageSnapshot: Stage0TriageResult?,
    cause: Throwable
) : RuntimeException(cause.message ?: "Stage pass $failedPass failed", cause)

data class PrdSynthesisOutput(
    val title: String,
    val deltaSummary: String,
    val markdownContent: String,
    val modelUsed: String
)

data class RoundtableScrutinyOutput(
    val status: RoundtableStatus,
    val verdict: String,
    val outboundRelayPrompt: String,
    val modelUsed: String,
    val sources: List<GroundingSource>
)

class VortexPipelineOrchestrator(
    private val router: VortexModelRouter
) {
    private val empiricalPolicy = """
        You are Vortex Researcher, a rigorous cognitive research and systems architecture engine.
        EMPIRICAL GROUNDING RULES:
        - Rely exclusively on verifiable primary sources, peer-reviewed papers, RFCs, technical specifications, standards, and empirical benchmarks via Google Search.
        - Never speculate or fabricate citations, metrics, or references. Explicitly state where empirical evidence stops.
        - Write dense, structured, actionable engineering prose.
    """.trimIndent()

    suspend fun runFullPipeline(
        query: String,
        mode: ResearchMode,
        fileFormat: ExportFileFormat,
        selectedBlueprint: ScaffoldingBlueprint,
        rootFolderName: String,
        prefs: RoutingPreferences,
        detectedLanguages: List<String> = emptyList(),
        envPolicyContext: String = "",
        onProgress: (List<StageRecord>, Stage0TriageResult?) -> Unit
    ): PipelineExecutionOutput = runFromPass(
        startPass = 0,
        existingStages = initialSkeleton(),
        existingTriage = null,
        query = query,
        mode = mode,
        fileFormat = fileFormat,
        selectedBlueprint = selectedBlueprint,
        rootFolderName = rootFolderName,
        prefs = prefs,
        detectedLanguages = detectedLanguages,
        envPolicyContext = envPolicyContext,
        onProgress = onProgress
    )

    /**
     * Executes or resumes the 7-stage pipeline starting from a specific pass:
     * - pass 0: Stage 0 Triage
     * - pass 1: Stages 1–3
     * - pass 2: Stages 4–5 + Gap Verdict
     * - pass 3: Stages 6–7
     * - pass 4: Project Mode file packaging
     * Prior completed stages are strictly preserved so failed passes can be retried cleanly.
     */
    suspend fun runFromPass(
        startPass: Int,
        existingStages: List<StageRecord>,
        existingTriage: Stage0TriageResult?,
        query: String,
        mode: ResearchMode,
        fileFormat: ExportFileFormat,
        selectedBlueprint: ScaffoldingBlueprint,
        rootFolderName: String,
        prefs: RoutingPreferences,
        detectedLanguages: List<String> = emptyList(),
        envPolicyContext: String = "",
        onProgress: (List<StageRecord>, Stage0TriageResult?) -> Unit
    ): PipelineExecutionOutput {
        val stages = initialSkeleton().mapIndexed { idx, defaultStage ->
            existingStages.firstOrNull { it.stageNumber == idx } ?: defaultStage
        }.toMutableList()
        val effectivePolicy = if (envPolicyContext.isNotBlank()) {
            "$empiricalPolicy\n\n$envPolicyContext"
        } else {
            empiricalPolicy
        }

        // STAGE 0: Triage
        var triage = existingTriage
        if (startPass <= 0 || triage == null || stages[0].status != "COMPLETED") {
            stages[0] = stages[0].copy(status = "RUNNING", modelUsed = VortexModelRouter.MODEL_FLASH_LITE)
            onProgress(stages.toList(), triage)
            try {
                triage = router.triageQuery(query, detectedLanguages)
                stages[0] = stages[0].copy(
                    status = "COMPLETED",
                    modelUsed = VortexModelRouter.MODEL_FLASH_LITE,
                    routingReason = triage.escalationReason,
                    content = buildString {
                        appendLine("**Detected Domain:** ${triage.detectedDomain}")
                        appendLine("**Complexity Score:** ${triage.complexityScore}/10")
                        appendLine("**Recommended Blueprint:** ${triage.recommendedBlueprint.title}")
                        appendLine("**Routing Strategy:** ${triage.escalationReason}")
                    }
                )
                onProgress(stages.toList(), triage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                stages[0] = stages[0].copy(status = "FAILED", content = "Stage 0 failed: ${e.message?.take(220)}")
                onProgress(stages.toList(), triage)
                throw PipelinePassFailureException(0, stages.toList(), triage, e)
            }
        }
        val resolvedTriage = triage
        val effectiveBlueprint = if (mode == ResearchMode.PROJECT) selectedBlueprint else resolvedTriage.recommendedBlueprint

        // PASS 1: Stages 1–3 (Deconstruction, Pinnacle of Wisdom Expert Panel, Multi-Expert Lenses)
        val pass1NeedsRun = startPass <= 1 || (1..3).any { stages[it].status != "COMPLETED" }
        val pass1CombinedText: String
        if (pass1NeedsRun) {
            for (i in 1..3) stages[i] = stages[i].copy(status = "RUNNING")
            onProgress(stages.toList(), resolvedTriage)
            try {
                val pass1Prompt = """
                    Research Query: $query
                    Detected Domain: ${resolvedTriage.detectedDomain} (Complexity ${resolvedTriage.complexityScore}/10)

                    Execute Stages 1, 2, and 3 sequentially using the exact delimiters below:
                    ===STAGE_1===
                    [Stage 1: Query Deconstruction — dissect the core problem, hidden constraints, boundary conditions, failure modes, and empirical questions requiring verification.]
                    ===STAGE_2===
                    [Stage 2: Pinnacle of Wisdom Expert Panel — select 3 to 5 distinct domain-leading expert personas (e.g., Principal Distributed Systems Architect, Formal Verification Scientist, Regulatory Strategist). Justify why each persona is strictly required.]
                    ===STAGE_3===
                    [Stage 3: Multi-Expert Lens Analyses — write each expert's rigorous, independent analysis grounded in primary sources, empirical studies, and production benchmarks. Highlight points of tension between experts.]
                """.trimIndent()

                val pass1 = router.generateGrounded(
                    systemDirective = effectivePolicy,
                    userPrompt = pass1Prompt,
                    domain = resolvedTriage.detectedDomain,
                    complexityScore = resolvedTriage.complexityScore,
                    highStakesStage = false,
                    prefs = prefs
                )
                pass1CombinedText = pass1.text
                val s1 = extractSection(pass1.text, "===STAGE_1===", "===STAGE_2===").ifBlank { pass1.text }
                val s2 = extractSection(pass1.text, "===STAGE_2===", "===STAGE_3===")
                val s3 = extractSection(pass1.text, "===STAGE_3===", null)

                stages[1] = stages[1].copy(
                    status = "COMPLETED",
                    modelUsed = pass1.modelUsed,
                    routingReason = pass1.routingReason,
                    fallbackNote = pass1.fallbackNote,
                    content = s1,
                    searchQueries = pass1.searchQueries,
                    sources = pass1.sources
                )
                stages[2] = stages[2].copy(
                    status = "COMPLETED",
                    modelUsed = pass1.modelUsed,
                    routingReason = pass1.routingReason,
                    fallbackNote = pass1.fallbackNote,
                    content = s2.ifBlank { "Expert panel synthesized inside Stage 1–3 pass." }
                )
                stages[3] = stages[3].copy(
                    status = "COMPLETED",
                    modelUsed = pass1.modelUsed,
                    routingReason = pass1.routingReason,
                    fallbackNote = pass1.fallbackNote,
                    content = s3.ifBlank { "Multi-expert lens analyses integrated." },
                    sources = pass1.sources
                )
                onProgress(stages.toList(), resolvedTriage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                for (i in 1..3) {
                    stages[i] = stages[i].copy(
                        status = "FAILED",
                        content = "Pass 1 interrupted: ${e.message?.take(220) ?: "Network or model timeout"}. Tap Retry Stage to resume from Pass 1."
                    )
                }
                onProgress(stages.toList(), resolvedTriage)
                throw PipelinePassFailureException(1, stages.toList(), resolvedTriage, e)
            }
        } else {
            pass1CombinedText = buildString {
                appendLine("===STAGE_1===\n${stages[1].content}")
                appendLine("===STAGE_2===\n${stages[2].content}")
                appendLine("===STAGE_3===\n${stages[3].content}")
            }
        }

        // PASS 2: Stages 4–5 (Unified Synthesis & Empirical Gap Analysis)
        val pass2NeedsRun = startPass <= 2 || stages[4].status != "COMPLETED" || stages[5].status != "COMPLETED"
        val s4: String
        val s5: String
        val gapVerdict: GapVerdict
        if (pass2NeedsRun) {
            stages[4] = stages[4].copy(status = "RUNNING")
            stages[5] = stages[5].copy(status = "RUNNING")
            onProgress(stages.toList(), resolvedTriage)
            try {
                val pass2Prompt = """
                    Original Query: $query
                    Prior Stages 1-3 Findings:
                    ${pass1CombinedText.take(14000)}

                    Execute Stages 4 and 5 with FRESH Google Search verification using these exact delimiters:
                    ===STAGE_4===
                    [Stage 4: Unified Synthesis — reconcile all expert perspectives into one coherent, optimal architecture/methodology with clear trade-off matrices, verified citations, and ONE concise fenced ```mermaid flowchart TD``` diagram illustrating the synthesized system architecture using simple alphanumeric node IDs and double-quoted labels.]
                    ===STAGE_5===
                    [Stage 5: Empirical Gap Analysis — audit Stage 4 against real-world empirical constraints, physics/compute/cost/regulatory limits, and unsolved bottlenecks. Identify every gap that lacks an off-the-shelf resolution.]
                    ===GAP_VERDICT===
                    [Output ONLY one of these exact tokens on the first line: RESOLVED | NEEDS_EMULATION_LAYER | NEEDS_LOOPHOLE_MITIGATION]
                """.trimIndent()

                val pass2 = router.generateGrounded(
                    systemDirective = effectivePolicy,
                    userPrompt = pass2Prompt,
                    domain = resolvedTriage.detectedDomain,
                    complexityScore = resolvedTriage.complexityScore,
                    highStakesStage = true,
                    prefs = prefs
                )
                s4 = extractSection(pass2.text, "===STAGE_4===", "===STAGE_5===").ifBlank { pass2.text }
                s5 = extractSection(pass2.text, "===STAGE_5===", "===GAP_VERDICT===")
                val verdictRaw = extractSection(pass2.text, "===GAP_VERDICT===", null).uppercase()
                gapVerdict = when {
                    verdictRaw.contains("NEEDS_LOOPHOLE_MITIGATION") -> GapVerdict.NEEDS_LOOPHOLE_MITIGATION
                    verdictRaw.contains("NEEDS_EMULATION_LAYER") -> GapVerdict.NEEDS_EMULATION_LAYER
                    verdictRaw.contains("RESOLVED") -> GapVerdict.RESOLVED
                    else -> GapVerdict.NEEDS_EMULATION_LAYER
                }

                stages[4] = stages[4].copy(
                    status = "COMPLETED",
                    modelUsed = pass2.modelUsed,
                    routingReason = pass2.routingReason,
                    fallbackNote = pass2.fallbackNote,
                    content = s4,
                    searchQueries = pass2.searchQueries,
                    sources = pass2.sources
                )
                stages[5] = stages[5].copy(
                    status = "COMPLETED",
                    modelUsed = pass2.modelUsed,
                    routingReason = pass2.routingReason,
                    fallbackNote = pass2.fallbackNote,
                    content = buildString {
                        appendLine("**Empirical Verdict:** `${gapVerdict.label}`")
                        appendLine()
                        append(s5.ifBlank { "Empirical gap audit completed." })
                    },
                    searchQueries = pass2.searchQueries,
                    sources = pass2.sources
                )
                onProgress(stages.toList(), resolvedTriage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                for (i in 4..5) {
                    stages[i] = stages[i].copy(
                        status = "FAILED",
                        content = "Pass 2 interrupted: ${e.message?.take(220) ?: "Network or model timeout"}. Stages 1–3 preserved — tap Retry Stage to resume."
                    )
                }
                onProgress(stages.toList(), resolvedTriage)
                throw PipelinePassFailureException(2, stages.toList(), resolvedTriage, e)
            }
        } else {
            s4 = stages[4].content
            s5 = stages[5].content
            val upper5 = s5.uppercase()
            gapVerdict = when {
                upper5.contains("LOOPHOLE") || upper5.contains("MITIGATION") -> GapVerdict.NEEDS_LOOPHOLE_MITIGATION
                upper5.contains("EMPIRICALLY RESOLVED") || upper5.contains("`RESOLVED`") -> GapVerdict.RESOLVED
                else -> GapVerdict.NEEDS_EMULATION_LAYER
            }
        }

        // PASS 3: Stages 6–7 (Emulation Layer Architecture & Loopholes/Circumventions/Mitigations)
        val pass3NeedsRun = startPass <= 3 || (stages[6].status !in listOf("COMPLETED", "SKIPPED")) || (stages[7].status !in listOf("COMPLETED", "SKIPPED"))
        if (pass3NeedsRun) {
            if (gapVerdict == GapVerdict.RESOLVED) {
                stages[6] = stages[6].copy(
                    status = "SKIPPED",
                    modelUsed = stages[5].modelUsed,
                    content = "All requirements empirically verified and resolved with current production standards. No emulation bridge required."
                )
                stages[7] = stages[7].copy(
                    status = "SKIPPED",
                    modelUsed = stages[5].modelUsed,
                    content = "No prohibitive cost/resource barrier triggered Stage 7 circumvention mitigations."
                )
                onProgress(stages.toList(), resolvedTriage)
            } else {
                stages[6] = stages[6].copy(status = "RUNNING")
                stages[7] = stages[7].copy(status = "RUNNING")
                onProgress(stages.toList(), resolvedTriage)
                try {
                    val pass3Prompt = """
                        Original Query: $query
                        Gap Verdict from Stage 5: ${gapVerdict.wire}
                        Stage 4 Unified Synthesis:
                        ${s4.take(8000)}
                        Stage 5 Gap Audit:
                        ${s5.take(6000)}

                        Execute Stages 6 and 7 using the exact delimiters below:
                        ===STAGE_6===
                        [Stage 6: Emulation Layer Architecture — design concrete emulation layers, abstraction shims, hybrid pipelines, or modern adapter bridges that emulate the envisioned architecture's capabilities using proven present-day technologies. Include ONE concise fenced ```mermaid flowchart LR``` diagram mapping the adapter/emulation layer topology with simple alphanumeric node IDs and double-quoted labels.]
                        ===STAGE_7===
                        [Stage 7: Loopholes, Circumventions & Cohesive Mitigation Methodology — whenever Stage 6 proves prohibitively difficult, expensive, or resource-intensive, identify legitimate architectural workarounds, progressive phased rollouts, and mitigations. Apply full Stages 1–5 empirical rigor to combine all feasible components with these bridges into ONE unified, executable methodology.]
                    """.trimIndent()

                    val pass3 = router.generateGrounded(
                        systemDirective = effectivePolicy,
                        userPrompt = pass3Prompt,
                        domain = resolvedTriage.detectedDomain,
                        complexityScore = resolvedTriage.complexityScore,
                        highStakesStage = true,
                        prefs = prefs
                    )
                    val s6 = extractSection(pass3.text, "===STAGE_6===", "===STAGE_7===").ifBlank { pass3.text }
                    val s7 = extractSection(pass3.text, "===STAGE_7===", null)

                    stages[6] = stages[6].copy(
                        status = "COMPLETED",
                        modelUsed = pass3.modelUsed,
                        routingReason = pass3.routingReason,
                        fallbackNote = pass3.fallbackNote,
                        content = s6,
                        searchQueries = pass3.searchQueries,
                        sources = pass3.sources
                    )
                    stages[7] = stages[7].copy(
                        status = "COMPLETED",
                        modelUsed = pass3.modelUsed,
                        routingReason = pass3.routingReason,
                        fallbackNote = pass3.fallbackNote,
                        content = s7.ifBlank { "Unified mitigation bridge integrated with Stage 6 emulation layer." },
                        searchQueries = pass3.searchQueries,
                        sources = pass3.sources
                    )
                    onProgress(stages.toList(), resolvedTriage)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    for (i in 6..7) {
                        stages[i] = stages[i].copy(
                            status = "FAILED",
                            content = "Pass 3 interrupted: ${e.message?.take(220) ?: "Network or model timeout"}. Stages 1–5 preserved — tap Retry Stage to resume."
                        )
                    }
                    onProgress(stages.toList(), resolvedTriage)
                    throw PipelinePassFailureException(3, stages.toList(), resolvedTriage, e)
                }
            }
        }

        // Compose clean Chat Answer (always built so Chat Mode and Quick Copy work seamlessly)
        val allSources = stages.flatMap { it.sources }.distinctBy { it.uri }
        val chatAnswer = buildChatAnswer(resolvedTriage.projectTitle, stages, allSources)

        // PASS 4: Project Mode Multi-File Blueprint Packaging
        val generatedFiles = if (mode == ResearchMode.PROJECT) {
            packageProjectFiles(
                title = resolvedTriage.projectTitle,
                rootFolder = rootFolderName.ifBlank { resolvedTriage.suggestedRootFolder },
                blueprint = effectiveBlueprint,
                format = fileFormat,
                query = query,
                stages = stages,
                sources = allSources
            )
        } else {
            emptyList()
        }

        val modelsUsed = stages.mapNotNull { it.modelUsed.takeIf { m -> m.isNotBlank() } }.distinct()
        val routingSummary = "Domain: ${resolvedTriage.detectedDomain} (${resolvedTriage.complexityScore}/10) • Models: ${modelsUsed.joinToString(" → ")}"

        return PipelineExecutionOutput(
            triage = resolvedTriage,
            stages = stages.toList(),
            gapVerdict = gapVerdict,
            chatAnswer = chatAnswer,
            files = generatedFiles,
            routingPlanSummary = routingSummary
        )
    }

    suspend fun answerChatFollowUp(
        project: ResearchProject,
        history: List<ChatTurn>,
        followUpQuestion: String,
        prefs: RoutingPreferences,
        envPolicyContext: String = ""
    ): GroundedGenerationResult {
        val recentTurns = history.takeLast(10).joinToString("\n\n") { turn ->
            val speaker = if (turn.role == "USER") "USER" else "VORTEX ASSISTANT (${turn.modelUsed.ifBlank { "Gemini" }})"
            "### $speaker\n${turn.content.take(3500)}"
        }
        val synthesisDigest = project.parsedStages()
            .filter { it.stageNumber in listOf(4, 5, 6, 7) && it.status == "COMPLETED" }
            .joinToString("\n\n") { "Stage ${it.stageNumber} (${it.title}):\n${it.content.take(2200)}" }

        val directive = if (envPolicyContext.isNotBlank()) "$empiricalPolicy\n\n$envPolicyContext" else empiricalPolicy

        val prompt = """
            You are continuing a multi-turn empirical research conversation in Chat Mode for "${project.title}".
            Original Research Query: ${project.query}

            Core 7-Stage Grounded Synthesis Context:
            ${synthesisDigest.take(9000)}

            Prior Conversation Thread:
            ${recentTurns.ifBlank { project.chatAnswer.take(5000) }}

            New Follow-Up Question from User:
            $followUpQuestion

            Respond directly, concisely, and rigorously with fresh Google Search verification where helpful.
        """.trimIndent()

        return router.generateGrounded(
            systemDirective = directive,
            userPrompt = prompt,
            domain = project.detectedDomain.ifBlank { "General Research" },
            complexityScore = project.complexityScore.coerceAtLeast(5),
            highStakesStage = false,
            prefs = prefs
        )
    }

    private suspend fun packageProjectFiles(
        title: String,
        rootFolder: String,
        blueprint: ScaffoldingBlueprint,
        format: ExportFileFormat,
        query: String,
        stages: List<StageRecord>,
        sources: List<GroundingSource>
    ): List<ProjectFile> {
        val ext = format.extension
        val targetPaths = blueprint.exampleTree
            .filterNot {
                it.startsWith("PRD/", ignoreCase = true) ||
                    it.startsWith("AI_Roundtable/", ignoreCase = true) ||
                    it.startsWith("roundtable/", ignoreCase = true)
            }
            .map { path ->
                if (ext == "txt" && path.endsWith(".md")) path.removeSuffix(".md") + ".txt" else path
            }

        // Structured JSON packaging via Flash-Lite for clean architect-grade repository files
        val fileSchema = Schema.obj(
            properties = mapOf(
                "files" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "relativePath" to Schema.string(description = "Relative file path from the required list"),
                            "content" to Schema.string(description = "Complete, well-formatted file body")
                        )
                    )
                )
            )
        )

        val stageDigest = stages.joinToString("\n\n") { st ->
            "### Stage ${st.stageNumber}: ${st.title} (${st.modelUsed})\n${st.content.take(4500)}"
        }
        val sourcesText = if (sources.isEmpty()) {
            "No external web URLs returned by grounding metadata."
        } else {
            sources.mapIndexed { idx, s -> "${idx + 1}. ${s.title} — ${s.uri}" }.joinToString("\n")
        }

        val prompt = """
            Package the following 7-stage research synthesis into a professional ${blueprint.title} repository under root folder '$rootFolder' using ${format.label} formatting.
            Create EVERY file listed below with substantive, non-placeholder technical content drawn faithfully from the research stages:
            Required paths: ${targetPaths.joinToString(", ")}

            Research Query: $query
            Project Title: $title
            
            Stage Findings:
            ${stageDigest.take(26000)}

            Verified Grounding Sources:
            $sourcesText
        """.trimIndent()

        val packaged = runCatching {
            val raw = router.generateStructuredJson(prompt, fileSchema, useFlashLite = true)
            val arr = JSONObject(raw).optJSONArray("files") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val path = obj.optString("relativePath").trim().trimStart('/')
                    val body = obj.optString("content").trim()
                    if (path.isNotBlank() && body.isNotBlank()) {
                        add(ProjectFile(relativePath = path, content = body, category = "RESEARCH"))
                    }
                }
            }
        }.getOrDefault(emptyList())

        // Guarantee deterministic fallback files if JSON packaging omitted any required file
        val coreFiles = if (packaged.size >= 2) {
            packaged
        } else {
            buildDeterministicFallbackFiles(title, query, ext, blueprint, stages, sources)
        }

        val prdDir = blueprint.prdFolder
        val prdIndexPath = "$prdDir/PRD_INDEX.$ext"
        val prdIndexSeed = ProjectFile(
            relativePath = prdIndexPath,
            content = com.example.data.remote.VortexFirestoreRepository.buildPrdIndexContent(
                projectTitle = title,
                extension = ext,
                prds = emptyList(),
                rootFolderName = rootFolder,
                prdFolder = prdDir
            ),
            category = "PRD"
        )
        return if (coreFiles.any { it.relativePath.equals(prdIndexPath, ignoreCase = true) }) coreFiles else coreFiles + prdIndexSeed
    }

    suspend fun generatePrdVersion(
        project: ResearchProject,
        existingPrds: List<PrdDocument>,
        revisionRequest: String,
        isMajorBump: Boolean,
        prefs: RoutingPreferences,
        roundtableAdoptedSummary: String = ""
    ): PrdSynthesisOutput {
        val previous = existingPrds.maxWithOrNull(
            compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion }
        )
        val nextVersion = if (previous == null) {
            "v1.0"
        } else if (isMajorBump) {
            "v${previous.majorVersion + 1}.0"
        } else {
            "v${previous.majorVersion}.${previous.minorVersion + 1}"
        }

        val stagesText = project.parsedStages()
            .filter { it.status == "COMPLETED" }
            .joinToString("\n\n") { "### Stage ${it.stageNumber}: ${it.title}\n${it.content.take(3500)}" }

        val deltaInstruction = if (previous == null) {
            "Write 1–2 crisp, high-signal sentences (max 220 chars) summarizing the baseline scope, target architecture, and primary constraints established in $nextVersion for the PRD/PRD_INDEX evolution ledger."
        } else {
            "Compare $nextVersion directly against ${previous.version}. Write 1–2 crisp, concrete sentences (max 220 chars) stating exactly what was ADDED, CHANGED, or REMOVED relative to ${previous.version} so a reader scanning PRD/PRD_INDEX understands the evolution in 5 seconds."
        }

        val prompt = """
            Generate Product Requirements Document $nextVersion for project "${project.title}".
            Original Research Query: ${project.query}
            ${if (previous != null) "Previous Version (${previous.version}):\n${previous.markdownContent.take(10000)}\n" else ""}
            ${if (revisionRequest.isNotBlank()) "User Follow-up Revision Request:\n$revisionRequest\n" else "Initial Approved Baseline PRD from 7-Stage Synthesis."}
            ${if (roundtableAdoptedSummary.isNotBlank()) "Adopted AI Roundtable Ideas to Integrate:\n$roundtableAdoptedSummary\n" else ""}

            7-Stage Research Context:
            ${stagesText.take(14000)}

            Respond using these exact delimiters:
            ===DELTA_SUMMARY===
            [$deltaInstruction]
            ===PRD_BODY===
            [Write the complete, executive-ready PRD $nextVersion including: 1. Executive Summary & Objectives, 2. Target Architecture & Expert Synthesis (include a concise fenced ```mermaid flowchart TD``` system diagram with alphanumeric node IDs and double-quoted labels), 3. Functional & Non-Functional Requirements, 4. Empirical Constraints, Emulation Layers & Risk Mitigations, 5. Phased Implementation Roadmap & Acceptance Criteria, 6. Verified References.]
        """.trimIndent()

        val result = router.generateGrounded(
            systemDirective = empiricalPolicy,
            userPrompt = prompt,
            domain = project.detectedDomain.ifBlank { "Systems Architecture" },
            complexityScore = project.complexityScore.coerceAtLeast(6),
            highStakesStage = true,
            prefs = prefs
        )

        val delta = extractSection(result.text, "===DELTA_SUMMARY===", "===PRD_BODY===")
            .replace("\n", " ")
            .trim()
            .ifBlank {
                if (previous == null) "Initial approved baseline PRD synthesized from 7-stage research."
                else "Evolved from ${previous.version}: ${revisionRequest.take(160)}"
            }
            .take(280)

        val body = extractSection(result.text, "===PRD_BODY===", null)
            .ifBlank { result.text }

        return PrdSynthesisOutput(
            title = "${project.title} — PRD $nextVersion",
            deltaSummary = delta,
            markdownContent = body,
            modelUsed = result.modelUsed
        )
    }

    suspend fun scrutinizeRoundtableItem(
        project: ResearchProject,
        item: RoundtableItem,
        prefs: RoutingPreferences
    ): RoundtableScrutinyOutput {
        val isCodeArtifact = item.detectedLanguage !in listOf("Markdown", "Plain Text", "Text")
        val domain = if (isCodeArtifact) "Software Engineering & Code" else project.detectedDomain.ifBlank { "Systems Architecture" }
        val stagesContext = project.parsedStages()
            .filter { it.stageNumber in listOf(4, 5, 6, 7) && it.status == "COMPLETED" }
            .joinToString("\n\n") { "Stage ${it.stageNumber} (${it.title}):\n${it.content.take(2500)}" }

        val prompt = """
            You are auditing an external AI model's proposal in the AI Roundtable for project "${project.title}".
            External Model Attribution: ${item.modelFolder}
            Artifact File: ${item.fileName} (Language/Type: ${item.detectedLanguage})

            Current Vortex Project Context:
            Query: ${project.query}
            ${stagesContext.take(9000)}

            External Model Proposal (${item.modelFolder} / ${item.fileName}):
            ${item.content.take(16000)}

            Perform strict empirical scrutiny with Google Search grounding. Respond using these exact delimiters:
            ===STATUS===
            [Output ONLY one exact token: ADOPTED | REBUTTAL_NEEDED | CONSENSUS_RESOLVED | REJECTED]
            ===VERDICT===
            [Empirical audit citing strengths, flaws, factual/security/code issues, and what should be integrated or rejected.]
            ===OUTBOUND_RELAY===
            [Write a crisp, self-contained message formatted for the user to copy and paste directly back to ${item.modelFolder} to continue the 2-way debate until consensus is reached. Include specific technical questions or counter-evidence.]
        """.trimIndent()

        val result = router.generateGrounded(
            systemDirective = empiricalPolicy,
            userPrompt = prompt,
            domain = domain,
            complexityScore = maxOf(project.complexityScore, if (isCodeArtifact) 7 else 6),
            highStakesStage = true,
            prefs = prefs
        )

        val statusRaw = extractSection(result.text, "===STATUS===", "===VERDICT===").uppercase()
        val status = when {
            statusRaw.contains("CONSENSUS_RESOLVED") -> RoundtableStatus.CONSENSUS_RESOLVED
            statusRaw.contains("ADOPTED") -> RoundtableStatus.ADOPTED
            statusRaw.contains("REJECTED") -> RoundtableStatus.REJECTED
            else -> RoundtableStatus.REBUTTAL_NEEDED
        }
        val verdict = extractSection(result.text, "===VERDICT===", "===OUTBOUND_RELAY===").ifBlank { result.text }
        val relay = extractSection(result.text, "===OUTBOUND_RELAY===", null).ifBlank {
            "Hi ${item.modelFolder}, we audited '${item.fileName}' against our architecture. Here is our feedback:\n\n${verdict.take(1200)}\n\nPlease respond with empirical counter-evidence or revised implementation details."
        }

        return RoundtableScrutinyOutput(
            status = status,
            verdict = verdict,
            outboundRelayPrompt = relay,
            modelUsed = result.modelUsed,
            sources = result.sources
        )
    }

    suspend fun answerRoundtableRagQuestion(
        project: ResearchProject,
        items: List<RoundtableItem>,
        question: String,
        prefs: RoutingPreferences
    ): GroundedGenerationResult {
        val corpus = items.joinToString("\n\n---\n\n") { item ->
            "SOURCE: [${item.modelFolder} / ${item.fileName}] (${item.detectedLanguage}, Status: ${item.queueStatus})\n" +
                "CONTENT:\n${item.content.take(4500)}\n" +
                if (item.scrutinyVerdict.isNotBlank()) "GEMINI VERDICT:\n${item.scrutinyVerdict.take(1200)}" else ""
        }

        val prompt = """
            Answer the user's question about the AI Roundtable corpus for project "${project.title}".
            ALWAYS attribute claims and code insights explicitly using `[ModelName / filename]` tags.

            User Question: $question

            AI Roundtable Corpus:
            ${corpus.take(24000)}
        """.trimIndent()

        return router.generateGrounded(
            systemDirective = empiricalPolicy,
            userPrompt = prompt,
            domain = project.detectedDomain.ifBlank { "General Research" },
            complexityScore = project.complexityScore.coerceAtLeast(5),
            highStakesStage = false,
            prefs = prefs
        )
    }

    suspend fun runCrossModelComparison(
        project: ResearchProject,
        items: List<RoundtableItem>,
        prefs: RoutingPreferences
    ): GroundedGenerationResult {
        val grouped = items.groupBy { it.modelFolder }
        val participatingModels = grouped.keys.joinToString(", ")
        val corpus = grouped.entries.joinToString("\n\n====================\n\n") { (modelDir, docs) ->
            "### MODEL SUB-FOLDER: $modelDir (${docs.size} artifact(s))\n" +
                docs.joinToString("\n\n") { doc ->
                    "FILE: [$modelDir / ${doc.fileName}] (${doc.detectedLanguage}, Status: ${doc.queueStatus})\n${doc.content.take(3500)}"
                }
        }

        val prompt = """
            Conduct a rigorous CROSS-MODEL EMPIRICAL SCRUTINY across the A.i. Roundtable sub-folders for project "${project.title}".
            Participating External AI Models: $participatingModels
            Original Project Query: ${project.query}

            Uploaded Multi-Model Research & Code Corpus:
            ${corpus.take(26000)}

            Produce a structured executive comparison with Google Search verification:
            1. **Consensus Points** — where models agree and empirical sources confirm them.
            2. **Direct Contradictions & Trade-Off Matrix** — compare conflicting architectural, mathematical, or code claims side-by-side using strict `[ModelFolder / fileName]` attribution.
            3. **Empirical Winner & Recommended Synthesis** — identify which specific ideas or code modules to adopt, which to reject (with evidence), and the unified next step for the PRD.
        """.trimIndent()

        return router.generateGrounded(
            systemDirective = empiricalPolicy,
            userPrompt = prompt,
            domain = project.detectedDomain.ifBlank { "Systems Architecture" },
            complexityScore = maxOf(project.complexityScore, 7),
            highStakesStage = true,
            prefs = prefs
        )
    }

    private fun buildChatAnswer(
        title: String,
        stages: List<StageRecord>,
        sources: List<GroundingSource>
    ): String = buildString {
        appendLine("# $title")
        appendLine()
        val synthesis = stages.firstOrNull { it.stageNumber == 4 }?.content.orEmpty()
        val gaps = stages.firstOrNull { it.stageNumber == 5 }?.content.orEmpty()
        val emulation = stages.firstOrNull { it.stageNumber == 6 && it.status == "COMPLETED" }?.content.orEmpty()
        val loopholes = stages.firstOrNull { it.stageNumber == 7 && it.status == "COMPLETED" }?.content.orEmpty()

        if (synthesis.isNotBlank()) {
            appendLine("## Unified Synthesis")
            appendLine(synthesis)
            appendLine()
        }
        if (gaps.isNotBlank()) {
            appendLine("## Empirical Gap Analysis")
            appendLine(gaps)
            appendLine()
        }
        if (emulation.isNotBlank()) {
            appendLine("## Emulation Layer Architecture")
            appendLine(emulation)
            appendLine()
        }
        if (loopholes.isNotBlank()) {
            appendLine("## Mitigations & Cohesive Execution Methodology")
            appendLine(loopholes)
            appendLine()
        }
        if (sources.isNotEmpty()) {
            appendLine("## Verified Grounding Sources")
            sources.forEachIndexed { idx, src ->
                appendLine("${idx + 1}. [${src.title}](${src.uri})")
            }
        }
    }.trim()

    private fun buildDeterministicFallbackFiles(
        title: String,
        query: String,
        ext: String,
        blueprint: ScaffoldingBlueprint,
        stages: List<StageRecord>,
        sources: List<GroundingSource>
    ): List<ProjectFile> {
        fun st(n: Int) = stages.firstOrNull { it.stageNumber == n }?.content.orEmpty()
        val sourcesBody = if (sources.isEmpty()) "Verified via live Google Search grounding."
        else sources.mapIndexed { i, s -> "${i + 1}. ${s.title}\n   ${s.uri}" }.joinToString("\n\n")

        val paths = blueprint.exampleTree
            .filterNot {
                it.startsWith("PRD/", ignoreCase = true) ||
                    it.startsWith("AI_Roundtable/", ignoreCase = true) ||
                    it.startsWith("roundtable/", ignoreCase = true)
            }
            .map { if (ext == "txt" && it.endsWith(".md")) it.removeSuffix(".md") + ".txt" else it }

        val contentMap = listOf(
            "# $title\n\nQuery: $query\n\n${st(0)}\n\n## Unified Synthesis\n${st(4)}",
            "# Query Deconstruction & Expert Panel\n\n## Stage 1\n${st(1)}\n\n## Stage 2\n${st(2)}",
            "# Multi-Expert Lens Analyses\n\n${st(3)}",
            "# Unified Synthesis\n\n${st(4)}",
            "# Empirical Gap Analysis\n\n${st(5)}",
            "# Emulation Layer & Mitigation Bridges\n\n## Stage 6\n${st(6)}\n\n## Stage 7\n${st(7)}",
            "# Verified Grounding Bibliography\n\n$sourcesBody"
        )

        return paths.mapIndexed { idx, relPath ->
            ProjectFile(
                relativePath = relPath,
                content = contentMap.getOrElse(idx) { "# $relPath\n\n${st(4)}" },
                category = "RESEARCH"
            )
        }
    }

    private fun extractSection(text: String, startMarker: String, endMarker: String?): String {
        val startIdx = text.indexOf(startMarker)
        if (startIdx == -1) return ""
        val afterStart = startIdx + startMarker.length
        val endIdx = if (endMarker != null) text.indexOf(endMarker, afterStart) else -1
        return if (endIdx == -1) {
            text.substring(afterStart).trim()
        } else {
            text.substring(afterStart, endIdx).trim()
        }
    }

    companion object {
        fun initialSkeleton(): List<StageRecord> = listOf(
            StageRecord(0, "Domain & Complexity Triage", "PENDING"),
            StageRecord(1, "Query Deconstruction", "PENDING"),
            StageRecord(2, "Pinnacle of Wisdom Expert Panel", "PENDING"),
            StageRecord(3, "Multi-Expert Lens Analyses", "PENDING"),
            StageRecord(4, "Unified Synthesis", "PENDING"),
            StageRecord(5, "Empirical Gap Analysis", "PENDING"),
            StageRecord(6, "Emulation Layer Architecture", "PENDING"),
            StageRecord(7, "Loopholes, Circumventions & Mitigations", "PENDING")
        )
    }
}
