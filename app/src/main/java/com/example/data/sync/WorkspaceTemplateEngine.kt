package com.example.data.sync

import com.example.data.model.ExportFileFormat
import com.example.data.model.PrdDocument
import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.ScaffoldingBlueprint
import com.example.data.remote.VortexFirestoreRepository
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkspaceBuildResult(
    val blueprint: ScaffoldingBlueprint,
    val canonicalFolders: List<String>,
    val files: List<ProjectFile>,
    val workspaceSha256: String,
    val missingFoldersBeforeRepair: List<String>
)

object WorkspaceTemplateEngine {

    private val codeExtensions = setOf(
        "py", "ts", "tsx", "js", "jsx", "mjs", "rs", "go", "kt", "kts", "java",
        "swift", "dart", "c", "cpp", "h", "hpp", "cs", "rb", "php", "sh", "sql",
        "proto", "graphql", "yaml", "yml", "json", "jsonl", "toml", "xml", "html", "css"
    )

    fun buildConsistentWorkspace(
        project: ResearchProject,
        prds: List<PrdDocument>,
        roundtableItems: List<RoundtableItem>,
        overrideBlueprint: ScaffoldingBlueprint? = null
    ): WorkspaceBuildResult {
        val blueprint = overrideBlueprint ?: ScaffoldingBlueprint.fromWire(project.blueprintId)
        val ext = ExportFileFormat.fromWire(project.fileFormat).extension
        val sortedPrds = prds.sortedWith(
            compareBy<PrdDocument> { it.majorVersion }.thenBy { it.minorVersion }
        )

        val existingFiles = project.parsedFiles()
        val presentTopFolders = existingFiles
            .mapNotNull { it.relativePath.substringBefore('/', "").takeIf { dir -> dir.isNotBlank() && it.relativePath.contains('/') } }
            .toSet()

        val missingBeforeRepair = blueprint.canonicalFolders.filter { req ->
            val top = req.substringBefore('/')
            presentTopFolders.none { it.equals(top, ignoreCase = true) }
        }

        val normalized = linkedMapOf<String, ProjectFile>()

        // 1. Place research & architecture files (re-mapping cleanly if switching to Canonical Systems Workspace)
        val researchFiles = existingFiles.filterNot { f ->
            f.relativePath.startsWith("PRD/", ignoreCase = true) ||
                f.relativePath.startsWith("prd/", ignoreCase = true) ||
                f.relativePath.startsWith("AI_Roundtable/", ignoreCase = true) ||
                f.relativePath.startsWith("roundtable/", ignoreCase = true) ||
                f.relativePath.equals("vortex-workspace.json", ignoreCase = true)
        }

        if (researchFiles.isEmpty() && project.chatAnswer.isNotBlank()) {
            normalized["README.$ext"] = ProjectFile("README.$ext", project.chatAnswer, "RESEARCH")
        } else {
            researchFiles.forEach { file ->
                val targetPath = mapPathToBlueprint(file.relativePath, blueprint)
                normalized[targetPath] = file.copy(relativePath = targetPath)
            }
        }

        // 2. Canonical PRD folder (/prd or /PRD) with PRD_INDEX pinned + all versioned PRDs
        val prdDir = blueprint.prdFolder
        val prdIndexPath = "$prdDir/PRD_INDEX.$ext"
        normalized[prdIndexPath] = ProjectFile(
            relativePath = prdIndexPath,
            content = VortexFirestoreRepository.buildPrdIndexContent(
                projectTitle = project.title,
                extension = ext,
                prds = sortedPrds,
                rootFolderName = project.rootFolderName,
                prdFolder = prdDir
            ),
            category = "PRD"
        )
        sortedPrds.forEach { prd ->
            val pPath = "$prdDir/PRD_${prd.version}.$ext"
            normalized[pPath] = ProjectFile(
                relativePath = pPath,
                content = prd.markdownContent,
                category = "PRD"
            )
        }

        // 3. Canonical Roundtable folder (/roundtable or /AI_Roundtable) + all created model sub-folders
        val rtDir = blueprint.roundtableFolder
        val hasSrcFolder = blueprint.canonicalFolders.any { it.equals("src", ignoreCase = true) }
        val modelSubFolders = (project.effectiveRoundtableFolders() + roundtableItems.map { it.modelFolder })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        roundtableItems.forEach { item ->
            val rtPath = "$rtDir/${item.modelFolder}/${item.fileName}"
            normalized[rtPath] = ProjectFile(
                relativePath = rtPath,
                content = item.content,
                category = "ROUNDTABLE"
            )
            if (hasSrcFolder && item.extension.lowercase() in codeExtensions) {
                val srcPath = "src/${item.modelFolder.lowercase()}/${item.fileName}"
                normalized[srcPath] = ProjectFile(
                    relativePath = srcPath,
                    content = item.content,
                    category = "SRC"
                )
            }
        }

        // Seed README anchors for each created AI model sub-directory that has no files yet
        modelSubFolders.forEach { modelDir ->
            val subFolderPath = "$rtDir/$modelDir"
            val hasModelFiles = normalized.keys.any {
                it.startsWith("$subFolderPath/", ignoreCase = true)
            }
            if (!hasModelFiles) {
                val anchorPath = "$subFolderPath/README.$ext"
                normalized[anchorPath] = ProjectFile(
                    relativePath = anchorPath,
                    content = buildModelSubfolderAnchor(modelDir, rtDir, project.title),
                    category = "ROUNDTABLE"
                )
            }
        }

        val allCanonicalFolders = (blueprint.canonicalFolders + modelSubFolders.map { "$rtDir/$it" })
            .distinctBy { it.lowercase() }

        // 4. Ensure every canonical directory in the template has at least a clean README anchor so empty dirs stay consistent
        blueprint.canonicalFolders.forEach { folder ->
            val hasAnyFileInFolder = normalized.keys.any {
                it.startsWith("$folder/", ignoreCase = true)
            }
            if (!hasAnyFileInFolder) {
                val anchorPath = "$folder/README.$ext"
                normalized[anchorPath] = ProjectFile(
                    relativePath = anchorPath,
                    content = buildFolderAnchorContent(folder, project.title, blueprint),
                    category = if (folder.equals(prdDir, ignoreCase = true)) "PRD" else "RESEARCH"
                )
            }
        }

        // 5. Generate canonical workspace consistency manifest (`vortex-workspace.json`)
        val contentFiles = normalized.values.sortedBy { it.relativePath }
        val workspaceSha = SafFolderSyncManager.computeProjectHash(contentFiles)
        val manifestJson = buildManifestJson(project, blueprint, allCanonicalFolders, contentFiles, workspaceSha)
        val finalFiles = contentFiles + ProjectFile(
            relativePath = "vortex-workspace.json",
            content = manifestJson,
            category = "MANIFEST"
        )

        return WorkspaceBuildResult(
            blueprint = blueprint,
            canonicalFolders = allCanonicalFolders,
            files = finalFiles,
            workspaceSha256 = workspaceSha,
            missingFoldersBeforeRepair = missingBeforeRepair
        )
    }

    fun computeMd5Hex(content: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun mapPathToBlueprint(path: String, blueprint: ScaffoldingBlueprint): String {
        val clean = path.trim().trimStart('/')
        if (blueprint != ScaffoldingBlueprint.SYSTEM_ARCHITECTURE_CANONICAL) return clean

        val lower = clean.lowercase()
        val fileName = clean.substringAfterLast('/')
        return when {
            lower == "readme.md" || lower == "readme.txt" -> clean
            lower.startsWith("docs/") || lower.startsWith("src/") ||
                lower.startsWith("research/") || lower.startsWith("prd/") ||
                lower.startsWith("roundtable/") || lower.startsWith("tests/") ||
                lower.startsWith("infra/") || lower.startsWith("diagrams/") ||
                lower.startsWith("scripts/") -> clean
            lower.contains("adr") -> "docs/adr/$fileName"
            lower.startsWith("02_architecture/") -> "docs/$fileName"
            lower.startsWith("01_analysis/") || lower.startsWith("03_verification/") ||
                lower.startsWith("01_methodology/") || lower.startsWith("02_findings/") ||
                lower.startsWith("03_mitigations") || lower.startsWith("04_bibliography/") -> "research/$fileName"
            else -> "research/$fileName"
        }
    }

    private fun buildFolderAnchorContent(
        folder: String,
        projectTitle: String,
        blueprint: ScaffoldingBlueprint
    ): String = buildString {
        appendLine("# /$folder — $projectTitle")
        appendLine()
        appendLine("Managed by **Vortex Researcher** using the **${blueprint.title}** system architecture template.")
        appendLine()
        val purpose = when (folder.lowercase()) {
            "docs", "docs/adr" -> "Architecture specifications, design overviews, and Architecture Decision Records (ADRs)."
            "src" -> "Source code prototypes, schemas, and verified coding artifacts synced from AI Roundtable or local imports."
            "prd" -> "Immutable versioned Product Requirements Documents (`PRD_vX.Y`) and the auto-maintained `PRD_INDEX` ledger."
            "research" -> "Grounded 7-stage empirical research outputs, expert lenses, gap audits, and verified bibliographies."
            "roundtable", "ai_roundtable" -> "External AI model proposals, code audits, and Gemini scrutiny records."
            else -> "Structured workspace artifacts for $projectTitle."
        }
        appendLine("**Directory Purpose:** $purpose")
    }

    private fun buildModelSubfolderAnchor(
        modelFolder: String,
        roundtableRoot: String,
        projectTitle: String
    ): String = buildString {
        appendLine("# /$roundtableRoot/$modelFolder — A.i. Roundtable Sub-Directory")
        appendLine()
        appendLine("**Project:** $projectTitle  ")
        appendLine("**Attributed AI Model:** `$modelFolder`")
        appendLine()
        appendLine("Drop documents, research notes, LaTeX/Mermaid specs, PDFs, or native source code files generated by **$modelFolder** into this folder for Gemini cross-model empirical scrutiny and attributed RAG.")
    }

    private fun buildManifestJson(
        project: ResearchProject,
        blueprint: ScaffoldingBlueprint,
        canonicalFolders: List<String>,
        files: List<ProjectFile>,
        workspaceSha: String
    ): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val root = JSONObject().apply {
            put("schemaVersion", "1.0")
            put("projectTitle", project.title)
            put("rootFolderName", project.rootFolderName)
            put("templateId", blueprint.wire)
            put("templateTitle", blueprint.title)
            put("canonicalFolders", JSONArray(canonicalFolders))
            put("workspaceSha256", workspaceSha)
            put("generatedAt", dateFmt.format(Date()))
            put("fileCount", files.size)
            val entries = JSONArray()
            files.forEach { f ->
                entries.put(
                    JSONObject().apply {
                        put("path", f.relativePath)
                        put("category", f.category)
                        put("md5", computeMd5Hex(f.content))
                        put("bytes", f.content.toByteArray(Charsets.UTF_8).size)
                    }
                )
            }
            put("files", entries)
        }
        return root.toString(2)
    }

    /**
     * Converts a human-readable project title or folder name into a portable, cross-platform
     * directory slug safe for Local SAF, Google Drive, and GitHub repositories.
     */
    fun sanitizeRootFolderSlug(raw: String, fallback: String = "systems-project"): String {
        val normalized = raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(64)
        return normalized.ifBlank { fallback }
    }

    /**
     * Builds standardized starter files for a newly initialized Systems Architect project so every
     * folder (/docs, /docs/adr, /src, /prd, /research, /roundtable, etc.) starts with consistent
     * architectural templates before passing through [buildConsistentWorkspace].
     */
    fun buildInitialTemplateFiles(
        projectTitle: String,
        rootFolderName: String,
        domain: String,
        blueprint: ScaffoldingBlueprint,
        fileFormat: ExportFileFormat = ExportFileFormat.MD,
        extraFolders: List<String> = emptyList()
    ): List<ProjectFile> {
        val ext = fileFormat.extension
        val cleanTitle = projectTitle.trim().ifBlank { "Systems Architecture Project" }
        val cleanRoot = sanitizeRootFolderSlug(rootFolderName.ifBlank { cleanTitle })
        val cleanDomain = domain.trim().ifBlank { "Systems Architecture" }
        val cleanExtras = extraFolders
            .map { it.trim().trim('/').lowercase(Locale.US).replace(Regex("[^a-z0-9_/-]"), "-") }
            .filter { it.isNotBlank() }
            .distinct()

        val files = linkedMapOf<String, ProjectFile>()

        // Root README charter
        val allRootDirs = (blueprint.canonicalFolders + cleanExtras).distinctBy { it.lowercase(Locale.US) }
        files["README.$ext"] = ProjectFile(
            relativePath = "README.$ext",
            content = buildString {
                appendLine("# $cleanTitle")
                appendLine()
                appendLine("- **Workspace Root:** `$cleanRoot/`")
                appendLine("- **Domain Focus:** $cleanDomain")
                appendLine("- **Architecture Template:** ${blueprint.title} (`${blueprint.wire}`)")
                appendLine()
                appendLine("## Standardized Directory Contract")
                appendLine()
                allRootDirs.forEach { dir ->
                    appendLine("- `/$dir` — ${describeStandardFolder(dir)}")
                }
                appendLine()
                appendLine("## Next Steps")
                appendLine("1. Run a 7-Stage Empirical Synthesis query to populate research findings and ADRs.")
                appendLine("2. Approve synthesis to publish `PRD_v1.0` inside `/${blueprint.prdFolder}`.")
                appendLine("3. Drop external AI proposals into `/${blueprint.roundtableFolder}` for cross-model scrutiny.")
            },
            category = "RESEARCH"
        )

        // Blueprint-specific architectural starter files
        when (blueprint) {
            ScaffoldingBlueprint.SYSTEM_ARCHITECTURE_CANONICAL -> {
                files["docs/architecture_overview.$ext"] = ProjectFile(
                    relativePath = "docs/architecture_overview.$ext",
                    content = buildString {
                        appendLine("# Architecture Overview — $cleanTitle")
                        appendLine()
                        appendLine("## 1. System Context & Boundaries")
                        appendLine("_Define upstream producers, downstream consumers, and trust boundaries._")
                        appendLine()
                        appendLine("## 2. Core Components & Data Flow")
                        appendLine("_Document service topology, persistence tiers, and synchronization contracts._")
                        appendLine()
                        appendLine("## 3. Reliability, Security & SLO Targets")
                        appendLine("- **Availability Target:** TBD")
                        appendLine("- **Latency Budget (p95):** TBD")
                        appendLine("- **Security / Secrets Policy:** Zero plaintext secrets in repo; managed via encrypted `.env` vault.")
                    },
                    category = "RESEARCH"
                )
                files["docs/adr/ADR-001-architecture-charter.$ext"] = ProjectFile(
                    relativePath = "docs/adr/ADR-001-architecture-charter.$ext",
                    content = buildString {
                        appendLine("# ADR-001: Initial Architecture Charter")
                        appendLine()
                        appendLine("- **Status:** Proposed")
                        appendLine("- **Project:** $cleanTitle")
                        appendLine("- **Domain:** $cleanDomain")
                        appendLine()
                        appendLine("## Context")
                        appendLine("Establish a standardized, auditable workspace layout across `/docs`, `/src`, `/prd`, `/research`, and `/roundtable`.")
                        appendLine()
                        appendLine("## Decision")
                        appendLine("Adopt the Canonical Systems Workspace template with deterministic `vortex-workspace.json` integrity manifests.")
                        appendLine()
                        appendLine("## Consequences")
                        appendLine("- Predictable navigation across Local SAF, Google Drive, and GitHub mirrors.")
                        appendLine("- Formal separation between architecture decisions (`/docs/adr`), specs (`/prd`), and implementation (`/src`).")
                    },
                    category = "RESEARCH"
                )
                files["src/README.$ext"] = ProjectFile(
                    relativePath = "src/README.$ext",
                    content = buildFolderAnchorContent("src", cleanTitle, blueprint),
                    category = "SRC"
                )
                files["research/README.$ext"] = ProjectFile(
                    relativePath = "research/README.$ext",
                    content = buildFolderAnchorContent("research", cleanTitle, blueprint),
                    category = "RESEARCH"
                )
            }
            ScaffoldingBlueprint.SYSTEMS_ADR -> {
                files["02_architecture/adr/ADR-001-architecture-charter.$ext"] = ProjectFile(
                    relativePath = "02_architecture/adr/ADR-001-architecture-charter.$ext",
                    content = buildString {
                        appendLine("# ADR-001: Systems Architecture Charter — $cleanTitle")
                        appendLine()
                        appendLine("- **Status:** Proposed")
                        appendLine("- **Domain:** $cleanDomain")
                        appendLine()
                        appendLine("## Context & Problem Statement")
                        appendLine("_Capture architectural drivers, constraints, and non-functional requirements._")
                        appendLine()
                        appendLine("## Decision Outcome")
                        appendLine("_Record chosen option and justification once Stage 5-6 synthesis completes._")
                    },
                    category = "RESEARCH"
                )
            }
            ScaffoldingBlueprint.PRODUCT_MONOREPO -> {
                files["docs/rfcs/RFC-001-system-charter.$ext"] = ProjectFile(
                    relativePath = "docs/rfcs/RFC-001-system-charter.$ext",
                    content = buildString {
                        appendLine("# RFC-001: System Charter — $cleanTitle")
                        appendLine()
                        appendLine("## Summary")
                        appendLine("_Outline product goals, technical architecture, and rollout phases._")
                    },
                    category = "RESEARCH"
                )
                files["src/README.$ext"] = ProjectFile(
                    relativePath = "src/README.$ext",
                    content = buildFolderAnchorContent("src", cleanTitle, blueprint),
                    category = "SRC"
                )
            }
            ScaffoldingBlueprint.ACADEMIC_DOSSIER -> {
                files["00_abstract_summary.$ext"] = ProjectFile(
                    relativePath = "00_abstract_summary.$ext",
                    content = buildString {
                        appendLine("# Abstract Summary — $cleanTitle")
                        appendLine()
                        appendLine("**Domain:** $cleanDomain")
                        appendLine()
                        appendLine("_Executive abstract, empirical hypothesis, and key verified findings._")
                    },
                    category = "RESEARCH"
                )
            }
            ScaffoldingBlueprint.FLAT_MINIMALIST -> {
                files["01_executive_synthesis.$ext"] = ProjectFile(
                    relativePath = "01_executive_synthesis.$ext",
                    content = buildString {
                        appendLine("# Executive Synthesis — $cleanTitle")
                        appendLine()
                        appendLine("**Domain:** $cleanDomain")
                        appendLine()
                        appendLine("_Concise top-level findings and architectural recommendations._")
                    },
                    category = "RESEARCH"
                )
            }
        }

        // Optional extra architect directories (/tests, /infra, /diagrams, /scripts, etc.)
        cleanExtras.forEach { extraDir ->
            val path = "$extraDir/README.$ext"
            if (path !in files) {
                files[path] = ProjectFile(
                    relativePath = path,
                    content = buildString {
                        appendLine("# /$extraDir — $cleanTitle")
                        appendLine()
                        appendLine("**Directory Purpose:** ${describeStandardFolder(extraDir)}")
                    },
                    category = if (extraDir == "src" || extraDir == "tests" || extraDir == "scripts" || extraDir == "infra") "SRC" else "RESEARCH"
                )
            }
        }

        return files.values.toList()
    }

    /**
     * Generates a lightweight, deterministic list of relative paths that will be scaffolded
     * when initializing a project with the chosen Systems Architect template.
     */
    fun previewInitialTree(
        blueprint: ScaffoldingBlueprint,
        fileFormat: ExportFileFormat = ExportFileFormat.MD,
        extraFolders: List<String> = emptyList()
    ): List<String> {
        val ext = fileFormat.extension
        val starter = buildInitialTemplateFiles(
            projectTitle = "Preview",
            rootFolderName = "preview",
            domain = "Systems Architecture",
            blueprint = blueprint,
            fileFormat = fileFormat,
            extraFolders = extraFolders
        )
        val mockProject = ResearchProject(
            title = "Preview",
            rootFolderName = "preview",
            fileFormat = fileFormat.wire,
            blueprintId = blueprint.wire,
            filesJson = com.example.data.model.VortexJson.encodeFiles(starter)
        )
        return buildConsistentWorkspace(
            project = mockProject,
            prds = emptyList(),
            roundtableItems = emptyList(),
            overrideBlueprint = blueprint
        ).files.map { it.relativePath }
    }

    fun describeStandardFolder(folder: String): String = when (folder.lowercase(Locale.US)) {
        "docs" -> "System design docs, context diagrams, and architecture overviews"
        "docs/adr", "02_architecture/adr" -> "Immutable Architecture Decision Records (ADRs)"
        "docs/discovery" -> "Problem discovery, stakeholder interviews, and domain exploration"
        "docs/rfcs" -> "Engineering Requests for Comments (RFCs) & technical proposals"
        "docs/risks-and-mitigations" -> "Threat models, empirical gap audits, and mitigation bridges"
        "docs/references", "04_bibliography" -> "Verified citations, primary sources, and references"
        "src" -> "Source code prototypes, schemas, and verified implementation modules"
        "prd" -> "Versioned Product Requirements Documents & PRD_INDEX ledger"
        "research" -> "7-stage empirical research reports, expert panels, and gap audits"
        "roundtable", "ai_roundtable" -> "External AI model sub-folders for cross-model scrutiny"
        "tests" -> "Verification suites, integration specs, and benchmark harnesses"
        "infra" -> "Infrastructure-as-Code, deployment manifests, and CI/CD pipelines"
        "diagrams" -> "Mermaid, PlantUML, and C4 architecture diagrams"
        "scripts" -> "Automation scripts, migrations, and developer tooling"
        "01_analysis" -> "Query deconstruction and multi-expert domain lens analyses"
        "02_architecture" -> "Unified systems synthesis and architectural specifications"
        "03_verification" -> "Empirical gap audits and grounded citation ledgers"
        "01_methodology" -> "Research methodology and expert panel selection"
        "02_findings" -> "Empirical findings and domain lens analyses"
        "03_mitigations_and_bridges" -> "Emulation layers, workarounds, and risk bridges"
        else -> "Project artifacts for /$folder"
    }
}
