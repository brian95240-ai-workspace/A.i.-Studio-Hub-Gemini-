package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class ResearchMode(val wire: String, val label: String) {
    CHAT("CHAT", "Chat Mode"),
    PROJECT("PROJECT", "Project Mode");

    companion object {
        fun fromWire(value: String?): ResearchMode =
            entries.firstOrNull { it.wire == value } ?: PROJECT
    }
}

enum class ExportFileFormat(val wire: String, val extension: String, val label: String) {
    MD("MD", "md", "Markdown (.md)"),
    TXT("TXT", "txt", "Plain Text (.txt)");

    companion object {
        fun fromWire(value: String?): ExportFileFormat =
            entries.firstOrNull { it.wire == value } ?: MD
    }
}

enum class ScaffoldingBlueprint(
    val wire: String,
    val title: String,
    val bestFor: String,
    val plainEnglishSummary: String,
    val canonicalFolders: List<String>,
    val prdFolder: String,
    val roundtableFolder: String,
    val exampleTree: List<String>
) {
    SYSTEM_ARCHITECTURE_CANONICAL(
        wire = "SYSTEM_ARCHITECTURE_CANONICAL",
        title = "Canonical Systems Workspace",
        bestFor = "Full-stack systems architecture mirrored cleanly to Google Drive & GitHub",
        plainEnglishSummary = "Enforces strict separation across /docs, /src, /prd, /research, and /roundtable so every project shares an identical, predictable workspace contract across devices.",
        canonicalFolders = listOf("docs", "docs/adr", "src", "prd", "research", "roundtable"),
        prdFolder = "prd",
        roundtableFolder = "roundtable",
        exampleTree = listOf(
            "README.md",
            "docs/architecture_overview.md",
            "docs/adr/ADR-001-emulation-layer.md",
            "docs/adr/ADR-002-mitigations.md",
            "src/README.md",
            "research/01_deconstruction_and_experts.md",
            "research/02_unified_synthesis.md",
            "research/03_empirical_gap_audit.md",
            "research/04_verified_sources.md",
            "prd/PRD_INDEX.md",
            "roundtable/"
        )
    ),
    SYSTEMS_ADR(
        wire = "SYSTEMS_ADR",
        title = "Systems Architecture & ADR",
        bestFor = "Distributed infrastructure, RFCs & formal Architecture Decision Records",
        plainEnglishSummary = "Separates raw domain analysis from formal Architecture Decision Records (ADRs) and empirical verification audits—the layout used by staff systems architects.",
        canonicalFolders = listOf("01_analysis", "02_architecture", "02_architecture/adr", "03_verification", "PRD", "AI_Roundtable"),
        prdFolder = "PRD",
        roundtableFolder = "AI_Roundtable",
        exampleTree = listOf(
            "README.md",
            "01_analysis/01_deconstruction.md",
            "01_analysis/02_expert_panel.md",
            "01_analysis/03_multi_expert_lens.md",
            "02_architecture/04_unified_synthesis.md",
            "02_architecture/adr/ADR-001-emulation-layer.md",
            "02_architecture/adr/ADR-002-mitigations.md",
            "03_verification/05_empirical_gap_audit.md",
            "03_verification/06_sources_bibliography.md",
            "PRD/PRD_INDEX.md",
            "AI_Roundtable/"
        )
    ),
    ACADEMIC_DOSSIER(
        wire = "ACADEMIC_DOSSIER",
        title = "Academic & Empirical Dossier",
        bestFor = "Scientific research, white papers, biotech & formal proofs",
        plainEnglishSummary = "Mirrors peer-reviewed research dossiers with an executive abstract, methodology, empirical findings, bridge mitigations, and a strict bibliography.",
        canonicalFolders = listOf("01_methodology", "02_findings", "03_mitigations_and_bridges", "04_bibliography", "PRD", "AI_Roundtable"),
        prdFolder = "PRD",
        roundtableFolder = "AI_Roundtable",
        exampleTree = listOf(
            "00_abstract_summary.md",
            "01_methodology/expert_panel_selection.md",
            "01_methodology/query_deconstruction.md",
            "02_findings/domain_lens_analyses.md",
            "02_findings/empirical_gap_analysis.md",
            "03_mitigations_and_bridges/emulation_and_loopholes.md",
            "04_bibliography/verified_sources.md",
            "PRD/PRD_INDEX.md",
            "AI_Roundtable/"
        )
    ),
    PRODUCT_MONOREPO(
        wire = "PRODUCT_MONOREPO",
        title = "Product & Engineering Monorepo",
        bestFor = "Startup apps, SaaS platforms & cross-functional product teams",
        plainEnglishSummary = "Organizes research under a clean docs/ and src/ hierarchy with discovery notes, RFCs, risk mitigations, and references ready to mirror straight to Drive or GitHub.",
        canonicalFolders = listOf("docs", "docs/discovery", "docs/rfcs", "docs/risks-and-mitigations", "docs/references", "src", "PRD", "AI_Roundtable"),
        prdFolder = "PRD",
        roundtableFolder = "AI_Roundtable",
        exampleTree = listOf(
            "README.md",
            "docs/discovery/deconstruction_and_experts.md",
            "docs/rfcs/RFC-001-unified-architecture.md",
            "docs/rfcs/RFC-002-emulation-bridge.md",
            "docs/risks-and-mitigations/gaps_and_loopholes.md",
            "docs/references/sources.md",
            "src/README.md",
            "PRD/PRD_INDEX.md",
            "AI_Roundtable/"
        )
    ),
    FLAT_MINIMALIST(
        wire = "FLAT_MINIMALIST",
        title = "Flat Minimalist Bundle",
        bestFor = "Fast reads, executive briefs & lightweight knowledge vaults",
        plainEnglishSummary = "Keeps all core synthesis files at the top level in clean numbered order while still dedicating nested PRD/ and AI_Roundtable/ workspaces.",
        canonicalFolders = listOf("PRD", "AI_Roundtable"),
        prdFolder = "PRD",
        roundtableFolder = "AI_Roundtable",
        exampleTree = listOf(
            "01_executive_synthesis.md",
            "02_expert_analyses.md",
            "03_gaps_and_bridges.md",
            "04_verified_sources.md",
            "PRD/PRD_INDEX.md",
            "AI_Roundtable/"
        )
    );

    companion object {
        fun fromWire(value: String?): ScaffoldingBlueprint =
            entries.firstOrNull { it.wire == value } ?: SYSTEM_ARCHITECTURE_CANONICAL
    }
}

enum class PipelineStatus(val wire: String) {
    IDLE("IDLE"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    companion object {
        fun fromWire(value: String?): PipelineStatus =
            entries.firstOrNull { it.wire == value } ?: IDLE
    }
}

enum class GapVerdict(val wire: String, val label: String) {
    NONE("NONE", "Pending Analysis"),
    RESOLVED("RESOLVED", "Empirical Resolution Found"),
    NEEDS_EMULATION_LAYER("NEEDS_EMULATION_LAYER", "Emulation Layer Required"),
    NEEDS_LOOPHOLE_MITIGATION("NEEDS_LOOPHOLE_MITIGATION", "Loophole & Mitigation Bridge");

    companion object {
        fun fromWire(value: String?): GapVerdict =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

enum class TeamRole(val wire: String, val label: String) {
    ADMIN("ADMIN", "Admin"),
    EDITOR("EDITOR", "Editor"),
    VIEWER("VIEWER", "Read-Only");

    companion object {
        fun fromWire(value: String?): TeamRole =
            entries.firstOrNull { it.wire == value } ?: VIEWER
    }
}

enum class RoutingMode(val wire: String, val label: String, val description: String) {
    AUTO_STRATEGIC(
        "AUTO_STRATEGIC",
        "Auto Strategic",
        "Maximize Gemini 3.8 Flash; surgically escalate complex STEM/code/bridging stages to Gemini 4 Argon or your external API."
    ),
    FLASH_ONLY(
        "FLASH_ONLY",
        "Flash Only",
        "Run every grounded research stage on fast Gemini 3.8 Flash with Flash-Lite triage."
    ),
    MAX_PRECISION(
        "MAX_PRECISION",
        "Max Precision",
        "Prefer Gemini 4 Argon (or external domain model) across synthesis, gap auditing, and bridging passes."
    );

    companion object {
        fun fromWire(value: String?): RoutingMode =
            entries.firstOrNull { it.wire == value } ?: AUTO_STRATEGIC
    }
}

enum class ExternalRouterType(val wire: String, val label: String, val endpointHint: String) {
    OPENAI_COMPATIBLE(
        "OPENAI_COMPATIBLE",
        "OpenAI-Compatible (/chat/completions)",
        "https://openrouter.ai/api/v1/"
    ),
    SELF_HOSTED_TEXT(
        "SELF_HOSTED_TEXT",
        "Self-Hosted Text (/query)",
        "https://llm.example.com"
    );

    companion object {
        fun fromWire(value: String?): ExternalRouterType =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: OPENAI_COMPATIBLE
    }
}

enum class RoundtableStatus(val wire: String, val label: String) {
    QUEUED("QUEUED", "Queued"),
    ANALYZING("ANALYZING", "Scrutinizing"),
    ADOPTED("ADOPTED", "Adopted"),
    REBUTTAL_NEEDED("REBUTTAL_NEEDED", "Rebuttal Ready"),
    CONSENSUS_RESOLVED("CONSENSUS_RESOLVED", "Consensus"),
    REJECTED("REJECTED", "Rejected");

    companion object {
        fun fromWire(value: String?): RoundtableStatus =
            entries.firstOrNull { it.wire == value } ?: QUEUED
    }
}

@JsonClass(generateAdapter = true)
data class GroundingSource(
    val title: String = "",
    val uri: String = ""
)

@JsonClass(generateAdapter = true)
data class StageRecord(
    val stageNumber: Int = 0,
    val title: String = "",
    val status: String = "PENDING", // PENDING, RUNNING, COMPLETED, SKIPPED, FAILED
    val modelUsed: String = "",
    val responseLane: String = "",
    val responseLatencySeconds: Double? = null,
    val routingReason: String = "",
    val fallbackNote: String = "",
    val content: String = "",
    val searchQueries: List<String> = emptyList(),
    val sources: List<GroundingSource> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProjectFile(
    val relativePath: String = "",
    val content: String = "",
    val category: String = "RESEARCH" // RESEARCH, PRD, ROUNDTABLE
)

data class RoutingPreferences(
    val strategicRoutingEnabled: Boolean = true,
    val routingMode: RoutingMode = RoutingMode.AUTO_STRATEGIC,
    val escalationThreshold: Int = 6, // 8=Conservative, 6=Balanced, 4=Aggressive
    val externalEnabled: Boolean = false,
    val externalRouterType: ExternalRouterType = ExternalRouterType.OPENAI_COMPATIBLE,
    val externalBaseUrl: String = "https://openrouter.ai/api/v1/",
    val externalAuthVaultRef: String = "EXTERNAL_API_KEY",
    val externalApiKey: String = "", // In-memory projection from EncryptedEnvVaultStore only; never stored in DataStore
    val selfHostedIncludeGrounding: Boolean = false, // Opt-in per request; OFF by default for /query
    val selfHostedApprovedSummary: String = "",
    val domainModelMatrix: Map<String, String> = defaultDomainMatrix(),
    val githubPat: String = "", // In-memory projection from EncryptedEnvVaultStore only; never stored in DataStore
    val githubUsername: String = "",
    val githubDefaultPrivate: Boolean = true,
    val githubAutoMirror: Boolean = false,
    val githubAuthMethod: String = "NONE",
    val githubOAuthClientId: String = ""
) {
    companion object {
        fun defaultDomainMatrix(): Map<String, String> = mapOf(
            "Systems Architecture" to "gemini-4-argon",
            "Software Engineering & Code" to "gemini-4-argon",
            "Mathematics & Formal Logic" to "gemini-4-argon",
            "Biomedical & Hard Science" to "gemini-4-argon",
            "Legal & Regulatory Policy" to "gemini-3.8-flash",
            "Product Strategy & Market" to "gemini-3.8-flash",
            "General Research" to "gemini-3.8-flash"
        )
    }
}

@Keep
data class ChatTurn(
    val id: String = "",
    val role: String = "USER",
    val content: String = "",
    val modelUsed: String = "",
    val responseLane: String = "",
    val responseLatencySeconds: Double? = null,
    val sources: List<GroundingSource> = emptyList(),
    val timestampMillis: Long = 0L,
    val category: String = "",
    val tags: List<String> = emptyList()
)

@Keep
data class PublicPrdSnapshotEntry(
    val version: String = "v1.0",
    val title: String = "",
    val deltaSummary: String = "",
    val markdownContent: String = "",
    val modelUsed: String = "",
    val authorName: String = ""
)

@Keep
data class PublicRoundtableSnapshotEntry(
    val modelFolder: String = "",
    val fileName: String = "",
    val detectedLanguage: String = "Markdown",
    val queueStatus: String = RoundtableStatus.QUEUED.wire,
    val content: String = "",
    val scrutinyVerdict: String = "",
    val scrutinizedByModel: String = ""
)

@Keep
@IgnoreExtraProperties
data class ResearchProject(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val rootFolderName: String = "",
    val mode: String = ResearchMode.PROJECT.wire,
    val fileFormat: String = ExportFileFormat.MD.wire,
    val blueprintId: String = ScaffoldingBlueprint.SYSTEMS_ADR.wire,
    val query: String = "",
    val complexityScore: Int = 0,
    val detectedDomain: String = "",
    val routingPlanSummary: String = "",
    val pipelineStatus: String = PipelineStatus.IDLE.wire,
    val gapVerdict: String = GapVerdict.NONE.wire,
    val stagesJson: String = "[]",
    val filesJson: String = "[]",
    val chatAnswer: String = "",
    val chatHistoryJson: String = "[]",
    val approved: Boolean = false,
    val latestPrdVersion: String = "",
    val githubRepoUrl: String = "",
    val githubBranch: String = "main",
    val publicShareCode: String = "",
    val publicShareActive: Boolean = false,
    val lastJoinedInviteCode: String = "",
    val roundtableFolders: List<String> = DEFAULT_ROUNDTABLE_FOLDERS,
    val tags: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val memberRoles: Map<String, String> = emptyMap(),
    val memberDisplayNames: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleFor(uid: String?): TeamRole {
        if (uid.isNullOrBlank()) return TeamRole.VIEWER
        if (uid == ownerId) return TeamRole.ADMIN
        return TeamRole.fromWire(memberRoles[uid])
    }

    fun canEdit(uid: String?): Boolean = roleFor(uid) in listOf(TeamRole.ADMIN, TeamRole.EDITOR)
    fun canAdmin(uid: String?): Boolean = roleFor(uid) == TeamRole.ADMIN

    fun effectiveRoundtableFolders(): List<String> =
        roundtableFolders
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .ifEmpty { DEFAULT_ROUNDTABLE_FOLDERS }

    fun parsedStages(): List<StageRecord> = VortexJson.decodeStages(stagesJson)
    fun parsedFiles(): List<ProjectFile> = VortexJson.decodeFiles(filesJson)
    fun parsedChatHistory(): List<ChatTurn> = VortexJson.decodeChatHistory(chatHistoryJson)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "ownerId" to ownerId,
        "title" to title.ifBlank { "Untitled Research" }.take(200),
        "rootFolderName" to rootFolderName.ifBlank { "vortex-research" }.take(120),
        "mode" to mode,
        "fileFormat" to fileFormat,
        "blueprintId" to blueprintId,
        "query" to query.take(10000),
        "complexityScore" to complexityScore.coerceIn(0, 10),
        "detectedDomain" to detectedDomain.take(120),
        "routingPlanSummary" to routingPlanSummary.take(500),
        "pipelineStatus" to pipelineStatus,
        "gapVerdict" to gapVerdict,
        "stagesJson" to stagesJson.take(300000),
        "filesJson" to filesJson.take(450000),
        "chatAnswer" to chatAnswer.take(200000),
        "chatHistoryJson" to chatHistoryJson.take(220000),
        "approved" to approved,
        "latestPrdVersion" to latestPrdVersion.take(32),
        "githubRepoUrl" to githubRepoUrl.take(300),
        "githubBranch" to githubBranch.ifBlank { "main" }.take(80),
        "publicShareCode" to publicShareCode.take(64),
        "publicShareActive" to publicShareActive,
        "roundtableFolders" to effectiveRoundtableFolders().take(40),
        "tags" to MetadataTagEngine.normalizeTags(tags, maxCount = 24),
        "memberUids" to memberUids,
        "memberRoles" to memberRoles,
        "memberDisplayNames" to memberDisplayNames,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toContentUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.ifBlank { "Untitled Research" }.take(200),
        "rootFolderName" to rootFolderName.ifBlank { "vortex-research" }.take(120),
        "mode" to mode,
        "fileFormat" to fileFormat,
        "blueprintId" to blueprintId,
        "query" to query.take(10000),
        "complexityScore" to complexityScore.coerceIn(0, 10),
        "detectedDomain" to detectedDomain.take(120),
        "routingPlanSummary" to routingPlanSummary.take(500),
        "pipelineStatus" to pipelineStatus,
        "gapVerdict" to gapVerdict,
        "stagesJson" to stagesJson.take(300000),
        "filesJson" to filesJson.take(450000),
        "chatAnswer" to chatAnswer.take(200000),
        "chatHistoryJson" to chatHistoryJson.take(220000),
        "approved" to approved,
        "latestPrdVersion" to latestPrdVersion.take(32),
        "githubRepoUrl" to githubRepoUrl.take(300),
        "githubBranch" to githubBranch.ifBlank { "main" }.take(80),
        "roundtableFolders" to effectiveRoundtableFolders().take(40),
        "tags" to MetadataTagEngine.normalizeTags(tags, maxCount = 24),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val DEFAULT_ROUNDTABLE_FOLDERS = listOf("Claude", "ChatGPT-o3", "DeepSeek-R1")
    }
}

@Keep
@IgnoreExtraProperties
data class PrdDocument(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val version: String = "v1.0",
    val majorVersion: Int = 1,
    val minorVersion: Int = 0,
    val title: String = "",
    val deltaSummary: String = "",
    val revisionRequest: String = "",
    val markdownContent: String = "",
    val modelUsed: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "projectId" to projectId,
        "version" to version.take(24),
        "majorVersion" to majorVersion.coerceIn(1, 999),
        "minorVersion" to minorVersion.coerceIn(0, 999),
        "title" to title.ifBlank { "PRD $version" }.take(200),
        "deltaSummary" to deltaSummary.ifBlank { "Initial baseline PRD." }.take(600),
        "revisionRequest" to revisionRequest.take(4000),
        "markdownContent" to markdownContent.take(400000),
        "modelUsed" to modelUsed.ifBlank { "gemini-3.8-flash" }.take(100),
        "authorUid" to authorUid,
        "authorName" to authorName.ifBlank { "Researcher" }.take(80),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RoundtableItem(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val modelFolder: String = "",
    val fileName: String = "",
    val extension: String = "md",
    val detectedLanguage: String = "Markdown",
    val content: String = "",
    val byteSize: Int = 0,
    val queueStatus: String = RoundtableStatus.QUEUED.wire,
    val scrutinyVerdict: String = "",
    val outboundRelayPrompt: String = "",
    val groundingSourcesJson: String = "[]",
    val scrutinizedByModel: String = "",
    val authorUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedSources(): List<GroundingSource> = VortexJson.decodeSources(groundingSourcesJson)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "projectId" to projectId,
        "modelFolder" to modelFolder.ifBlank { "External-AI" }.take(80),
        "fileName" to fileName.ifBlank { "note.md" }.take(180),
        "extension" to extension.take(24),
        "detectedLanguage" to detectedLanguage.ifBlank { "Text" }.take(60),
        "content" to content.ifBlank { " " }.take(350000),
        "byteSize" to byteSize.coerceIn(0, 25000000),
        "queueStatus" to queueStatus,
        "scrutinyVerdict" to scrutinyVerdict.take(100000),
        "outboundRelayPrompt" to outboundRelayPrompt.take(50000),
        "groundingSourcesJson" to groundingSourcesJson.take(50000),
        "scrutinizedByModel" to scrutinizedByModel.take(100),
        "authorUid" to authorUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "projectId" to projectId,
        "modelFolder" to modelFolder.ifBlank { "External-AI" }.take(80),
        "fileName" to fileName.ifBlank { "note.md" }.take(180),
        "extension" to extension.take(24),
        "detectedLanguage" to detectedLanguage.ifBlank { "Text" }.take(60),
        "content" to content.ifBlank { " " }.take(350000),
        "byteSize" to byteSize.coerceIn(0, 25000000),
        "queueStatus" to queueStatus,
        "scrutinyVerdict" to scrutinyVerdict.take(100000),
        "outboundRelayPrompt" to outboundRelayPrompt.take(50000),
        "groundingSourcesJson" to groundingSourcesJson.take(50000),
        "scrutinizedByModel" to scrutinizedByModel.take(100),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ProjectInvite(
    @DocumentId val inviteCode: String = "",
    val projectId: String = "",
    val projectTitle: String = "",
    val assignedRole: String = TeamRole.VIEWER.wire,
    val createdByUid: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "projectId" to projectId,
        "projectTitle" to projectTitle.ifBlank { "Vortex Project" }.take(200),
        "assignedRole" to if (assignedRole == TeamRole.EDITOR.wire) TeamRole.EDITOR.wire else TeamRole.VIEWER.wire,
        "createdByUid" to createdByUid,
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PublicShareSnapshot(
    @DocumentId val shareCode: String = "",
    val projectId: String = "",
    val title: String = "",
    val rootFolderName: String = "",
    val mode: String = ResearchMode.PROJECT.wire,
    val fileFormat: String = ExportFileFormat.MD.wire,
    val blueprintId: String = ScaffoldingBlueprint.SYSTEMS_ADR.wire,
    val query: String = "",
    val detectedDomain: String = "",
    val stagesJson: String = "[]",
    val filesJson: String = "[]",
    val chatAnswer: String = "",
    val latestPrdVersion: String = "",
    val latestPrdMarkdown: String = "",
    val prdIndexMarkdown: String = "",
    val prdsJson: String = "[]",
    val roundtableJson: String = "[]",
    @ServerTimestamp val publishedAt: Timestamp? = null
) {
    fun parsedStages(): List<StageRecord> = VortexJson.decodeStages(stagesJson)
    fun parsedFiles(): List<ProjectFile> = VortexJson.decodeFiles(filesJson)
    fun parsedPrds(): List<PublicPrdSnapshotEntry> = VortexJson.decodePublicPrds(prdsJson)
    fun parsedRoundtable(): List<PublicRoundtableSnapshotEntry> = VortexJson.decodePublicRoundtable(roundtableJson)

    fun toPublishMap(): Map<String, Any> = mapOf(
        "projectId" to projectId,
        "title" to title.ifBlank { "Shared Research" }.take(200),
        "rootFolderName" to rootFolderName.ifBlank { "vortex-research" }.take(120),
        "mode" to mode,
        "fileFormat" to fileFormat,
        "blueprintId" to blueprintId,
        "query" to query.take(10000),
        "detectedDomain" to detectedDomain.take(120),
        "stagesJson" to stagesJson.take(260000),
        "filesJson" to filesJson.take(300000),
        "chatAnswer" to chatAnswer.take(100000),
        "latestPrdVersion" to latestPrdVersion.take(32),
        "latestPrdMarkdown" to latestPrdMarkdown.take(120000),
        "prdIndexMarkdown" to prdIndexMarkdown.take(45000),
        "prdsJson" to prdsJson.take(180000),
        "roundtableJson" to roundtableJson.take(180000),
        "publishedAt" to FieldValue.serverTimestamp()
    )
}

object VortexJson {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val stageListType = Types.newParameterizedType(List::class.java, StageRecord::class.java)
    private val fileListType = Types.newParameterizedType(List::class.java, ProjectFile::class.java)
    private val sourceListType = Types.newParameterizedType(List::class.java, GroundingSource::class.java)
    private val chatListType = Types.newParameterizedType(List::class.java, ChatTurn::class.java)
    private val publicPrdListType = Types.newParameterizedType(List::class.java, PublicPrdSnapshotEntry::class.java)
    private val publicRtListType = Types.newParameterizedType(List::class.java, PublicRoundtableSnapshotEntry::class.java)
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringMapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)

    private val stageAdapter = moshi.adapter<List<StageRecord>>(stageListType)
    private val fileAdapter = moshi.adapter<List<ProjectFile>>(fileListType)
    private val sourceAdapter = moshi.adapter<List<GroundingSource>>(sourceListType)
    private val chatAdapter = moshi.adapter<List<ChatTurn>>(chatListType)
    private val publicPrdAdapter = moshi.adapter<List<PublicPrdSnapshotEntry>>(publicPrdListType)
    private val publicRtAdapter = moshi.adapter<List<PublicRoundtableSnapshotEntry>>(publicRtListType)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)
    private val mapAdapter = moshi.adapter<Map<String, String>>(stringMapType)

    fun encodeStages(stages: List<StageRecord>): String = stageAdapter.toJson(stages)
    fun decodeStages(json: String?): List<StageRecord> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { stageAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodeFiles(files: List<ProjectFile>): String = fileAdapter.toJson(files)
    fun decodeFiles(json: String?): List<ProjectFile> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { fileAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodeSources(sources: List<GroundingSource>): String = sourceAdapter.toJson(sources)
    fun decodeSources(json: String?): List<GroundingSource> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { sourceAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodeChatHistory(turns: List<ChatTurn>): String = chatAdapter.toJson(turns)
    fun decodeChatHistory(json: String?): List<ChatTurn> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { chatAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodePublicPrds(prds: List<PublicPrdSnapshotEntry>): String = publicPrdAdapter.toJson(prds)
    fun decodePublicPrds(json: String?): List<PublicPrdSnapshotEntry> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { publicPrdAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodePublicRoundtable(items: List<PublicRoundtableSnapshotEntry>): String = publicRtAdapter.toJson(items)
    fun decodePublicRoundtable(json: String?): List<PublicRoundtableSnapshotEntry> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { publicRtAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodeStringList(list: List<String>): String = stringListAdapter.toJson(list)
    fun decodeStringList(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { stringListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())

    fun encodeStringMap(map: Map<String, String>): String = mapAdapter.toJson(map)
    fun decodeStringMap(json: String?): Map<String, String> =
        if (json.isNullOrBlank()) emptyMap()
        else runCatching { mapAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyMap())
}
