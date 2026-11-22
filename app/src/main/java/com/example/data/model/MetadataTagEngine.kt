package com.example.data.model

import java.util.Locale

enum class NoteCategory(
    val wire: String,
    val label: String
) {
    RESEARCH_QUERY("RESEARCH_QUERY", "Research Query"),
    ACTION_ITEM("ACTION_ITEM", "Action Item"),
    DRAFT("DRAFT", "Draft"),
    ARCHITECTURE_NOTE("ARCHITECTURE_NOTE", "Architecture Note"),
    DECISION("DECISION", "Decision"),
    FOLLOW_UP("FOLLOW_UP", "Follow-Up");

    companion object {
        val allLabels: List<String> = entries.map { it.label }

        fun fromWireOrLabel(raw: String?): NoteCategory {
            if (raw.isNullOrBlank()) return RESEARCH_QUERY
            val norm = raw.trim().lowercase(Locale.US)
            entries.firstOrNull {
                it.wire.lowercase(Locale.US) == norm || it.label.lowercase(Locale.US) == norm
            }?.let { return it }
            return when {
                norm.contains("action") || norm.contains("todo") || norm.contains("task") -> ACTION_ITEM
                norm.contains("draft") || norm.contains("wip") || norm.contains("idea") -> DRAFT
                norm.contains("arch") || norm.contains("design") || norm.contains("system") -> ARCHITECTURE_NOTE
                norm.contains("decision") || norm.contains("adr") || norm.contains("verdict") -> DECISION
                norm.contains("follow") || norm.contains("question") -> FOLLOW_UP
                else -> RESEARCH_QUERY
            }
        }
    }
}

data class ChatSearchMatch(
    val project: ResearchProject,
    val turn: ChatTurn,
    val turnIndex: Int,
    val category: NoteCategory,
    val effectiveTags: List<String>,
    val matchedTags: List<String>,
    val snippet: String,
    val score: Int
)

data class ProjectSearchHit(
    val projectId: String,
    val score: Int,
    val matchedTags: List<String>,
    val matchedChatCount: Int
)

data class UnifiedSearchResults(
    val projectHitsById: Map<String, ProjectSearchHit>,
    val chatMatches: List<ChatSearchMatch>
)

object MetadataTagEngine {

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "about", "using",
        "your", "our", "their", "have", "has", "had", "will", "would", "could", "should",
        "what", "when", "where", "which", "how", "why", "who", "are", "was", "were",
        "been", "being", "can", "may", "might", "must", "need", "needs", "want", "wants",
        "make", "create", "build", "project", "research", "system", "systems", "note",
        "notes", "please", "also", "then", "than", "more", "most", "some", "such", "each",
        "every", "other", "over", "under", "between", "through", "during", "before", "after",
        "within", "without", "across", "only", "just", "very", "here", "there", "like",
        "based", "well", "good", "best", "new", "use", "used", "using", "untitled", "vortex"
    )

    private val HIGH_SIGNAL_TERMS = mapOf(
        "api" to "API",
        "graphql" to "GraphQL",
        "rest" to "REST",
        "grpc" to "gRPC",
        "oauth" to "OAuth",
        "auth" to "Auth",
        "firebase" to "Firebase",
        "firestore" to "Firestore",
        "gemini" to "Gemini",
        "llm" to "LLM",
        "rag" to "RAG",
        "ai" to "AI",
        "ml" to "ML",
        "sql" to "SQL",
        "postgres" to "Postgres",
        "redis" to "Redis",
        "kafka" to "Kafka",
        "kubernetes" to "Kubernetes",
        "k8s" to "Kubernetes",
        "docker" to "Docker",
        "terraform" to "Terraform",
        "android" to "Android",
        "kotlin" to "Kotlin",
        "compose" to "Compose",
        "swift" to "Swift",
        "rust" to "Rust",
        "python" to "Python",
        "typescript" to "TypeScript",
        "react" to "React",
        "github" to "GitHub",
        "drive" to "Google Drive",
        "mermaid" to "Mermaid",
        "prd" to "PRD",
        "adr" to "ADR",
        "rfc" to "RFC",
        "ci/cd" to "CI/CD",
        "offline" to "Offline-First",
        "sync" to "Sync",
        "cache" to "Caching",
        "security" to "Security",
        "encryption" to "Encryption",
        "privacy" to "Privacy",
        "latency" to "Latency",
        "scalability" to "Scalability",
        "distributed" to "Distributed",
        "microservices" to "Microservices",
        "telemetry" to "Telemetry",
        "benchmark" to "Benchmark",
        "voice" to "Voice",
        "audio" to "Audio",
        "search" to "Search",
        "database" to "Database",
        "architecture" to "Architecture",
        "pipeline" to "Pipeline",
        "blockchain" to "Blockchain",
        "battery" to "Battery",
        "biotech" to "Biotech",
        "compliance" to "Compliance",
        "legal" to "Legal"
    )

    fun normalizeTag(raw: String): String {
        val stripped = raw.trim()
            .removePrefix("#")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', ';', '.', ':', '-', '_')
            .take(32)
        if (stripped.length < 2) return ""
        val lower = stripped.lowercase(Locale.US)
        HIGH_SIGNAL_TERMS[lower]?.let { return it }
        // Preserve NoteCategory canonical labels verbatim
        NoteCategory.entries.firstOrNull { it.label.equals(stripped, ignoreCase = true) }?.let {
            return it.label
        }
        return stripped.split(" ").joinToString(" ") { word ->
            val wl = word.lowercase(Locale.US)
            HIGH_SIGNAL_TERMS[wl] ?: if (word.length <= 3 && word == word.uppercase(Locale.US)) {
                word
            } else {
                word.replaceFirstChar { ch -> ch.titlecase(Locale.US) }
            }
        }
    }

    fun normalizeTags(raw: Iterable<String>, maxCount: Int = 12): List<String> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<String>()
        for (item in raw) {
            val clean = normalizeTag(item)
            if (clean.isNotBlank()) {
                val key = clean.lowercase(Locale.US)
                if (seen.add(key)) {
                    result.add(clean)
                    if (result.size >= maxCount) break
                }
            }
        }
        return result
    }

    fun classifyTextCategory(
        text: String,
        isUser: Boolean = true,
        fallback: NoteCategory = NoteCategory.RESEARCH_QUERY
    ): NoteCategory {
        val lower = text.lowercase(Locale.US)
        if (lower.isBlank()) return fallback
        if (lower.contains("todo") ||
            lower.contains("action item") ||
            lower.contains("need to ") ||
            lower.contains("must implement") ||
            lower.contains("fix ") ||
            lower.contains("schedule ") ||
            lower.contains("follow up with") ||
            lower.contains("assign ") ||
            lower.contains("next step") ||
            lower.contains("deliverable")
        ) {
            return NoteCategory.ACTION_ITEM
        }
        if (lower.contains("draft") ||
            lower.contains("rough idea") ||
            lower.contains("brainstorm") ||
            lower.contains("scratchpad") ||
            lower.contains("wip") ||
            lower.contains("early thought") ||
            lower.contains("outline")
        ) {
            return NoteCategory.DRAFT
        }
        if (lower.contains("adr") ||
            lower.contains("decided") ||
            lower.contains("decision") ||
            lower.contains("trade-off") ||
            lower.contains("tradeoff") ||
            lower.contains("we should choose") ||
            lower.contains("verdict")
        ) {
            return NoteCategory.DECISION
        }
        if (lower.contains("architecture") ||
            lower.contains("schema") ||
            lower.contains("diagram") ||
            lower.contains("microservice") ||
            lower.contains("data flow") ||
            lower.contains("protocol") ||
            lower.contains("emulation layer")
        ) {
            return NoteCategory.ARCHITECTURE_NOTE
        }
        if (!isUser && (lower.contains("summary") || lower.contains("recommendation"))) {
            return NoteCategory.RESEARCH_QUERY
        }
        if (lower.contains("?") ||
            lower.startsWith("how ") ||
            lower.startsWith("what ") ||
            lower.startsWith("why ") ||
            lower.startsWith("compare ") ||
            lower.startsWith("analyze ") ||
            lower.startsWith("investigate ")
        ) {
            return NoteCategory.RESEARCH_QUERY
        }
        return fallback
    }

    fun extractKeywordTags(text: String, maxCount: Int = 5): List<String> {
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase(Locale.US)
        val found = LinkedHashSet<String>()

        // 1. Prioritize recognized technical & architecture terms
        val tokens = lower.split(Regex("[^a-z0-9/+_-]+")).filter { it.length >= 2 }
        for (tok in tokens) {
            HIGH_SIGNAL_TERMS[tok]?.let { found.add(it) }
            if (found.size >= maxCount) return found.toList()
        }

        // 2. Fallback to highest-frequency salient domain nouns
        val freq = LinkedHashMap<String, Int>()
        for (tok in tokens) {
            val clean = tok.trim('-', '_', '/', '+')
            if (clean.length < 4 || clean in STOP_WORDS || clean.all { it.isDigit() }) continue
            freq[clean] = (freq[clean] ?: 0) + 1
        }
        freq.entries
            .sortedByDescending { it.value }
            .forEach { (word, _) ->
                if (found.size < maxCount) {
                    val norm = normalizeTag(word)
                    if (norm.isNotBlank()) found.add(norm)
                }
            }
        return found.take(maxCount)
    }

    fun deriveProjectTags(
        title: String,
        domain: String,
        blueprint: ScaffoldingBlueprint? = null,
        query: String = "",
        aiTags: List<String> = emptyList(),
        existingTags: List<String> = emptyList(),
        voiceNoteCategory: NoteCategory? = null,
        voiceNoteTags: List<String> = emptyList(),
        maxCount: Int = 14
    ): List<String> {
        val candidates = ArrayList<String>()
        candidates.addAll(aiTags)
        if (voiceNoteCategory != null) candidates.add(voiceNoteCategory.label)
        candidates.addAll(voiceNoteTags)
        if (domain.isNotBlank()) {
            candidates.add(domain)
            domain.split("&", "/").map { it.trim() }.filter { it.length >= 3 }.forEach {
                if (!it.equals(domain, ignoreCase = true)) candidates.add(it)
            }
        }
        if (blueprint != null) {
            val bpShort = when (blueprint) {
                ScaffoldingBlueprint.SYSTEM_ARCHITECTURE_CANONICAL -> "Systems Architect"
                ScaffoldingBlueprint.SYSTEMS_ADR -> "ADR"
                ScaffoldingBlueprint.ACADEMIC_DOSSIER -> "Academic Dossier"
                ScaffoldingBlueprint.PRODUCT_MONOREPO -> "Monorepo"
                ScaffoldingBlueprint.FLAT_MINIMALIST -> "Brief"
            }
            candidates.add(bpShort)
        }
        candidates.addAll(existingTags)
        candidates.addAll(extractKeywordTags("$title $query", maxCount = 5))
        return normalizeTags(candidates, maxCount = maxCount)
    }

    fun tagChatTurn(
        turn: ChatTurn,
        domain: String = "",
        voiceNoteCategory: NoteCategory? = null,
        voiceNoteTags: List<String> = emptyList(),
        fallbackCategory: NoteCategory = NoteCategory.RESEARCH_QUERY
    ): ChatTurn {
        val isUser = turn.role.equals("USER", ignoreCase = true)
        val resolvedCategory = when {
            voiceNoteCategory != null -> voiceNoteCategory
            turn.category.isNotBlank() -> NoteCategory.fromWireOrLabel(turn.category)
            else -> classifyTextCategory(turn.content, isUser = isUser, fallback = fallbackCategory)
        }
        val mergedTags = normalizeTags(
            raw = buildList {
                add(resolvedCategory.label)
                addAll(voiceNoteTags)
                addAll(turn.tags)
                if (domain.isNotBlank()) add(domain)
                addAll(extractKeywordTags(turn.content, maxCount = 4))
            },
            maxCount = 6
        )
        return turn.copy(
            category = resolvedCategory.label,
            tags = mergedTags
        )
    }

    fun effectiveProjectTags(project: ResearchProject): List<String> {
        val persisted = normalizeTags(project.tags, maxCount = 16)
        if (persisted.isNotEmpty()) return persisted
        val blueprint = ScaffoldingBlueprint.fromWire(project.blueprintId)
        val chatTags = effectiveChatTurns(project).flatMap { it.tags }.take(6)
        return deriveProjectTags(
            title = project.title,
            domain = project.detectedDomain,
            blueprint = blueprint,
            query = project.query,
            existingTags = chatTags,
            maxCount = 10
        )
    }

    fun effectiveChatTurns(project: ResearchProject): List<ChatTurn> {
        val parsed = project.parsedChatHistory()
        val rawTurns = if (parsed.isNotEmpty()) {
            parsed
        } else {
            buildList {
                if (project.query.isNotBlank()) {
                    add(
                        ChatTurn(
                            id = "${project.id}_q0",
                            role = "USER",
                            content = project.query,
                            timestampMillis = project.createdAt?.toDate()?.time ?: 0L
                        )
                    )
                }
                if (project.chatAnswer.isNotBlank()) {
                    add(
                        ChatTurn(
                            id = "${project.id}_a0",
                            role = "ASSISTANT",
                            content = project.chatAnswer,
                            timestampMillis = project.updatedAt?.toDate()?.time ?: 0L
                        )
                    )
                }
            }
        }
        return rawTurns.map { turn ->
            if (turn.category.isNotBlank() && turn.tags.isNotEmpty()) {
                turn
            } else {
                tagChatTurn(
                    turn = turn,
                    domain = project.detectedDomain,
                    fallbackCategory = if (turn.role.equals("USER", ignoreCase = true)) {
                        NoteCategory.RESEARCH_QUERY
                    } else {
                        NoteCategory.RESEARCH_QUERY
                    }
                )
            }
        }
    }

    fun collectPopularFilterTags(
        projects: List<ResearchProject>,
        maxCount: Int = 14
    ): List<String> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        fun bump(tag: String, weight: Int = 1) {
            val clean = normalizeTag(tag)
            if (clean.isBlank()) return
            val key = clean.lowercase(Locale.US)
            val prev = counts[key]
            counts[key] = (prev?.first ?: clean) to ((prev?.second ?: 0) + weight)
        }

        projects.forEach { proj ->
            effectiveProjectTags(proj).forEach { bump(it, weight = 2) }
            effectiveChatTurns(proj).forEach { turn ->
                if (turn.category.isNotBlank()) bump(turn.category, weight = 2)
                turn.tags.forEach { bump(it, weight = 1) }
            }
        }

        return counts.values
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }
            .take(maxCount)
    }

    fun searchProjectsAndChats(
        projects: List<ResearchProject>,
        rawQuery: String,
        selectedTag: String? = null
    ): UnifiedSearchResults {
        val cleanQuery = rawQuery.trim()
        val cleanSelectedTag = selectedTag?.trim()?.takeIf { it.isNotBlank() }
        if (cleanQuery.isBlank() && cleanSelectedTag == null) {
            val all = projects.associate { it.id to ProjectSearchHit(it.id, 1, emptyList(), 0) }
            return UnifiedSearchResults(all, emptyList())
        }

        val queryLower = cleanQuery.lowercase(Locale.US).removePrefix("#").trim()
        val tokens = queryLower
            .split(Regex("[\\s,#]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
        val tagFilterLower = cleanSelectedTag?.lowercase(Locale.US)?.removePrefix("#")

        val projectHits = LinkedHashMap<String, ProjectSearchHit>()
        val chatMatches = ArrayList<ChatSearchMatch>()

        projects.forEach { proj ->
            val projTags = effectiveProjectTags(proj)
            val projTagsLower = projTags.map { it.lowercase(Locale.US) }
            val titleLower = proj.title.lowercase(Locale.US)
            val folderLower = proj.rootFolderName.lowercase(Locale.US)
            val domainLower = proj.detectedDomain.lowercase(Locale.US)
            val queryFieldLower = proj.query.lowercase(Locale.US)
            val voiceNotesCorpus = proj.parsedFiles()
                .filter { it.relativePath.contains("voice_notes", ignoreCase = true) }
                .joinToString(" ") { "${it.relativePath} ${it.content.take(400)}" }
                .lowercase(Locale.US)

            val turns = effectiveChatTurns(proj)
            val matchedChatListForProject = ArrayList<ChatSearchMatch>()

            turns.forEachIndexed { idx, turn ->
                val turnCategory = NoteCategory.fromWireOrLabel(turn.category)
                val turnTags = normalizeTags(listOf(turnCategory.label) + turn.tags, maxCount = 8)
                val turnTagsLower = turnTags.map { it.lowercase(Locale.US) }
                val contentLower = turn.content.lowercase(Locale.US)

                // Check selected tag filter first if set
                if (tagFilterLower != null) {
                    val turnHasTag = turnTagsLower.any { it == tagFilterLower || it.contains(tagFilterLower) } ||
                        projTagsLower.any { it == tagFilterLower || it.contains(tagFilterLower) }
                    if (!turnHasTag) return@forEachIndexed
                }

                var chatScore = 0
                val hitTags = LinkedHashSet<String>()

                if (tagFilterLower != null) {
                    turnTags.filter { it.lowercase(Locale.US).contains(tagFilterLower) }.forEach {
                        hitTags.add(it)
                        chatScore += 18
                    }
                }

                if (queryLower.isNotBlank()) {
                    if (turnCategory.label.lowercase(Locale.US).contains(queryLower)) {
                        chatScore += 28
                        hitTags.add(turnCategory.label)
                    }
                    if (contentLower.contains(queryLower)) {
                        chatScore += 24
                    }
                    turnTags.forEach { tg ->
                        val tgl = tg.lowercase(Locale.US)
                        if (tgl == queryLower) {
                            chatScore += 32
                            hitTags.add(tg)
                        } else if (tgl.contains(queryLower)) {
                            chatScore += 20
                            hitTags.add(tg)
                        }
                    }

                    var matchedTokenCount = 0
                    tokens.forEach { tok ->
                        var tokenMatched = false
                        turnTags.forEach { tg ->
                            if (tg.lowercase(Locale.US).contains(tok)) {
                                hitTags.add(tg)
                                chatScore += 12
                                tokenMatched = true
                            }
                        }
                        if (contentLower.contains(tok)) {
                            chatScore += 9
                            tokenMatched = true
                        }
                        if (titleLower.contains(tok) || domainLower.contains(tok)) {
                            chatScore += 4
                            tokenMatched = true
                        }
                        if (tokenMatched) matchedTokenCount++
                    }

                    // Require strong token coverage for multi-word queries
                    if (tokens.size >= 2 && matchedTokenCount == 0) {
                        chatScore = 0
                    } else if (tokens.size >= 2 && matchedTokenCount == tokens.size) {
                        chatScore += 14
                    }
                } else if (tagFilterLower != null && hitTags.isNotEmpty()) {
                    // Tag-only filter directly matched this turn
                    chatScore += 10
                }

                if (chatScore > 0) {
                    val snippet = buildMatchSnippet(turn.content, queryLower, tokens)
                    val match = ChatSearchMatch(
                        project = proj,
                        turn = turn,
                        turnIndex = idx,
                        category = turnCategory,
                        effectiveTags = turnTags,
                        matchedTags = hitTags.toList(),
                        snippet = snippet,
                        score = chatScore
                    )
                    matchedChatListForProject.add(match)
                    chatMatches.add(match)
                }
            }

            // Now score the project itself
            val projectMatchesTagFilter = tagFilterLower == null ||
                projTagsLower.any { it == tagFilterLower || it.contains(tagFilterLower) } ||
                domainLower.contains(tagFilterLower) ||
                matchedChatListForProject.isNotEmpty()

            if (!projectMatchesTagFilter) return@forEach

            var projScore = 0
            val matchedProjectTags = LinkedHashSet<String>()

            if (tagFilterLower != null) {
                projTags.filter { it.lowercase(Locale.US).contains(tagFilterLower) }.forEach {
                    matchedProjectTags.add(it)
                    projScore += 26
                }
                if (domainLower.contains(tagFilterLower)) projScore += 16
            }

            if (queryLower.isNotBlank()) {
                if (titleLower.contains(queryLower)) projScore += 40
                if (folderLower.contains(queryLower)) projScore += 30
                if (domainLower.contains(queryLower)) projScore += 24
                if (queryFieldLower.contains(queryLower)) projScore += 18
                if (voiceNotesCorpus.contains(queryLower)) projScore += 22

                projTags.forEach { tg ->
                    val tgl = tg.lowercase(Locale.US)
                    if (tgl == queryLower) {
                        projScore += 38
                        matchedProjectTags.add(tg)
                    } else if (tgl.contains(queryLower)) {
                        projScore += 24
                        matchedProjectTags.add(tg)
                    }
                }

                var matchedTokenCount = 0
                tokens.forEach { tok ->
                    var tokHit = false
                    if (titleLower.contains(tok)) { projScore += 14; tokHit = true }
                    if (folderLower.contains(tok)) { projScore += 10; tokHit = true }
                    if (domainLower.contains(tok)) { projScore += 10; tokHit = true }
                    if (queryFieldLower.contains(tok)) { projScore += 7; tokHit = true }
                    if (voiceNotesCorpus.contains(tok)) { projScore += 9; tokHit = true }
                    projTags.forEach { tg ->
                        if (tg.lowercase(Locale.US).contains(tok)) {
                            matchedProjectTags.add(tg)
                            projScore += 13
                            tokHit = true
                        }
                    }
                    if (tokHit) matchedTokenCount++
                }
                if (tokens.size >= 2 && matchedTokenCount == tokens.size) {
                    projScore += 16
                }
            }

            if (matchedChatListForProject.isNotEmpty()) {
                projScore += 15 + matchedChatListForProject.maxOf { it.score } / 2
                matchedChatListForProject.flatMap { it.matchedTags }.forEach { matchedProjectTags.add(it) }
            }

            if (projScore > 0) {
                projectHits[proj.id] = ProjectSearchHit(
                    projectId = proj.id,
                    score = projScore,
                    matchedTags = matchedProjectTags.toList().take(4),
                    matchedChatCount = matchedChatListForProject.size
                )
            }
        }

        val sortedChats = chatMatches
            .sortedWith(
                compareByDescending<ChatSearchMatch> { it.score }
                    .thenByDescending { it.turn.timestampMillis }
            )
            .take(18)

        return UnifiedSearchResults(
            projectHitsById = projectHits,
            chatMatches = sortedChats
        )
    }

    private fun buildMatchSnippet(
        content: String,
        queryLower: String,
        tokens: List<String>,
        maxLen: Int = 150
    ): String {
        val cleaned = content
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[#*`>]+"), "")
            .trim()
        if (cleaned.length <= maxLen) return cleaned

        val lower = cleaned.lowercase(Locale.US)
        val anchor = when {
            queryLower.isNotBlank() && lower.indexOf(queryLower) >= 0 -> lower.indexOf(queryLower)
            else -> tokens.map { lower.indexOf(it) }.firstOrNull { it >= 0 } ?: 0
        }
        val start = (anchor - 36).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(cleaned.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < cleaned.length) "…" else ""
        return "$prefix${cleaned.substring(start, end).trim()}$suffix"
    }
}
