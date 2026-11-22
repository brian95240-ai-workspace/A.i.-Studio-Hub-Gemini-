package com.example.data.ai

import android.util.Log
import com.example.data.model.GroundingSource
import com.example.data.model.MetadataTagEngine
import com.example.data.model.NoteCategory
import com.example.data.model.RoutingMode
import com.example.data.model.RoutingPreferences
import com.example.data.model.ScaffoldingBlueprint
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class Stage0TriageResult(
    val complexityScore: Int,
    val detectedDomain: String,
    val recommendedBlueprint: ScaffoldingBlueprint,
    val suggestedRootFolder: String,
    val projectTitle: String,
    val escalationReason: String,
    val autoTags: List<String> = emptyList()
)

data class GroundedGenerationResult(
    val text: String,
    val modelUsed: String,
    val routingReason: String,
    val fallbackNote: String = "",
    val searchQueries: List<String> = emptyList(),
    val sources: List<GroundingSource> = emptyList()
)

data class AudioTranscriptionResult(
    val verbatimTranscript: String,
    val refinedResearchBrief: String,
    val suggestedTitle: String,
    val suggestedFolderSlug: String,
    val keyIdeas: List<String>,
    val category: NoteCategory = NoteCategory.RESEARCH_QUERY,
    val tags: List<String> = emptyList(),
    val modelUsed: String = VortexModelRouter.MODEL_FLASH
)

class VortexModelRouter(
    private val externalRouterApi: ExternalRouterApi = ExternalRouterApi()
) {
    companion object {
        private const val TAG = "VortexModelRouter"
        const val MODEL_FLASH_LITE = "gemini-3.5-flash-lite"
        const val MODEL_FLASH = "gemini-3.8-flash"
        const val MODEL_ARGON = "gemini-4-argon"
        private const val TIMEOUT_MS = 95_000L
    }

    private val flashGrounded by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            tools = listOf(Tool.googleSearch())
        )
    }

    private val argonGrounded by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_ARGON,
            tools = listOf(Tool.googleSearch())
        )
    }

    private val flashStandard by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH
        )
    }

    private val audioTranscriberModel by lazy {
        val transcriptSchema = Schema.obj(
            properties = mapOf(
                "verbatimTranscript" to Schema.string(
                    description = "Accurate, cleanly punctuated verbatim transcription of the spoken audio note."
                ),
                "refinedResearchBrief" to Schema.string(
                    description = "Well-structured, concise research brief or project specification distilled from the verbal note for the 7-stage synthesis pipeline, faithfully preserving all user constraints, technical terms, and goals without inventing facts."
                ),
                "suggestedTitle" to Schema.string(
                    description = "Concise 3 to 7 word professional title capturing the core verbal note or project idea."
                ),
                "suggestedFolderSlug" to Schema.string(
                    description = "Short kebab-case project root folder name, e.g., distributed-cache-architecture."
                ),
                "keyIdeas" to Schema.array(
                    items = Schema.string(),
                    description = "2 to 5 concise key technical ideas, requirements, or questions mentioned in the recording."
                ),
                "noteCategory" to Schema.enumeration(
                    values = NoteCategory.allLabels,
                    description = "Best-fitting metadata category for the spoken note: Research Query, Action Item, Draft, Architecture Note, Decision, or Follow-Up."
                ),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 6 concise, searchable topic or technical metadata tags (1 to 3 words each, no # prefix) for indexing this note and its project."
                )
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = transcriptSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun triageQuery(
        query: String,
        detectedCodeLanguages: List<String> = emptyList()
    ): Stage0TriageResult {
        val triageSchema = Schema.obj(
            properties = mapOf(
                "complexityScore" to Schema.integer(
                    description = "Integer from 1 (simple lookup) to 10 (frontier multi-disciplinary systems architecture, formal proofs, or hard empirical engineering)."
                ),
                "detectedDomain" to Schema.enumeration(
                    values = listOf(
                        "Systems Architecture",
                        "Software Engineering & Code",
                        "Mathematics & Formal Logic",
                        "Biomedical & Hard Science",
                        "Legal & Regulatory Policy",
                        "Product Strategy & Market",
                        "General Research"
                    ),
                    description = "Primary domain category."
                ),
                "recommendedBlueprint" to Schema.enumeration(
                    values = ScaffoldingBlueprint.entries.map { it.wire },
                    description = "Most fitting professional repository folder scaffolding blueprint."
                ),
                "suggestedRootFolder" to Schema.string(
                    description = "Short kebab-case root folder name, e.g., solid-state-battery-architecture"
                ),
                "projectTitle" to Schema.string(
                    description = "Concise 3 to 7 word professional title for the research project."
                ),
                "escalationReason" to Schema.string(
                    description = "One concise sentence explaining whether Gemini 3.8 Flash suffices or why escalation is beneficial."
                ),
                "autoTags" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 6 concise, high-signal searchable metadata tags (1 to 3 words each, no # prefix) describing the project's technologies, domain, and focus."
                )
            )
        )

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_FLASH_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = triageSchema
                temperature = 0.2f
            }
        )

        val langContext = if (detectedCodeLanguages.isNotEmpty()) {
            "\nAttached coding languages detected in Roundtable: ${detectedCodeLanguages.joinToString(", ")}."
        } else ""

        val prompt = """
            Classify this research request for strategic AI model routing, repository scaffolding, and searchable metadata auto-tagging.
            Request: $query$langContext
            Score complexity accurately: 1-4 standard research, 5-6 intermediate multi-discipline, 7-10 deep STEM, distributed architecture, formal proofs, security cryptography, or multi-language code synthesis.
            Also generate 3-6 concise searchable autoTags for indexing this project and its chats.
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) { model.generateContent(prompt) }
            val json = JSONObject(response.text.orEmpty())
            val tagsArr = json.optJSONArray("autoTags")
            val parsedTags = buildList {
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val item = tagsArr.optString(i).trim()
                        if (item.isNotBlank()) add(item)
                    }
                }
            }
            Stage0TriageResult(
                complexityScore = json.optInt("complexityScore", 6).coerceIn(1, 10),
                detectedDomain = json.optString("detectedDomain", "General Research"),
                recommendedBlueprint = ScaffoldingBlueprint.fromWire(json.optString("recommendedBlueprint")),
                suggestedRootFolder = sanitizeFolderName(
                    json.optString("suggestedRootFolder", "vortex-research")
                ),
                projectTitle = json.optString("projectTitle", query.take(48)).ifBlank { "Research Project" },
                escalationReason = json.optString(
                    "escalationReason",
                    "Evaluated for balanced Gemini 3.8 Flash execution."
                ),
                autoTags = MetadataTagEngine.normalizeTags(parsedTags, maxCount = 8)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stage 0 triage fallback triggered", e)
            Stage0TriageResult(
                complexityScore = 6,
                detectedDomain = if (detectedCodeLanguages.isNotEmpty()) "Software Engineering & Code" else "General Research",
                recommendedBlueprint = ScaffoldingBlueprint.SYSTEMS_ADR,
                suggestedRootFolder = sanitizeFolderName(query.take(36)),
                projectTitle = query.take(54).ifBlank { "Research Session" },
                escalationReason = "Defaulting to Gemini 3.8 Flash grounded pipeline.",
                autoTags = MetadataTagEngine.extractKeywordTags(query, maxCount = 5)
            )
        }
    }

    suspend fun generateGrounded(
        systemDirective: String,
        userPrompt: String,
        domain: String,
        complexityScore: Int,
        highStakesStage: Boolean,
        prefs: RoutingPreferences
    ): GroundedGenerationResult {
        val decision = resolveRoute(domain, complexityScore, highStakesStage, prefs)
        val combinedPrompt = "$systemDirective\n\n$userPrompt"

        if (decision.useExternalApi && prefs.externalEnabled && prefs.externalApiKey.isNotBlank()) {
            // First gather concise live Google Search evidence via Flash if available, then hand off to External Model
            val searchBrief = runCatching {
                callGeminiGrounded(
                    model = flashGrounded,
                    modelLabel = MODEL_FLASH,
                    prompt = "Conduct a concise live Google Search evidence sweep for this task, citing primary sources, standards, and empirical benchmarks only:\n$userPrompt",
                    routingReason = "Pre-search evidence sweep for external router"
                )
            }.getOrNull()

            val augmentedPrompt = if (searchBrief != null && searchBrief.text.isNotBlank()) {
                "$userPrompt\n\n### Live Google Search Grounding Evidence\n${searchBrief.text}"
            } else {
                userPrompt
            }

            val externalRes = externalRouterApi.chatCompletion(
                baseUrl = prefs.externalBaseUrl,
                apiKey = prefs.externalApiKey,
                modelId = decision.targetModel,
                systemPrompt = systemDirective,
                userPrompt = augmentedPrompt
            )
            externalRes.onSuccess { text ->
                return GroundedGenerationResult(
                    text = text,
                    modelUsed = decision.targetModel,
                    routingReason = decision.reason,
                    searchQueries = searchBrief?.searchQueries.orEmpty(),
                    sources = searchBrief?.sources.orEmpty()
                )
            }.onFailure { err ->
                Log.w(TAG, "External router call failed, falling back to Gemini 3.8 Flash", err)
                val fallback = callGeminiGrounded(
                    model = flashGrounded,
                    modelLabel = MODEL_FLASH,
                    prompt = combinedPrompt,
                    routingReason = decision.reason,
                    fallbackNote = "External API (${decision.targetModel}) unavailable (${err.message?.take(60)}); auto-fallback to $MODEL_FLASH."
                )
                return fallback
            }
        }

        if (decision.targetModel == MODEL_ARGON) {
            return try {
                callGeminiGrounded(
                    model = argonGrounded,
                    modelLabel = MODEL_ARGON,
                    prompt = combinedPrompt,
                    routingReason = decision.reason
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini 4 Argon unavailable; graceful fallback to Gemini 3.8 Flash", e)
                callGeminiGrounded(
                    model = flashGrounded,
                    modelLabel = MODEL_FLASH,
                    prompt = combinedPrompt,
                    routingReason = decision.reason,
                    fallbackNote = "$MODEL_ARGON unavailable on current tier; gracefully continued on $MODEL_FLASH."
                )
            }
        }

        return callGeminiGrounded(
            model = flashGrounded,
            modelLabel = MODEL_FLASH,
            prompt = combinedPrompt,
            routingReason = decision.reason
        )
    }

    suspend fun generateStructuredJson(
        prompt: String,
        schema: Schema,
        useFlashLite: Boolean = true
    ): String {
        val modelName = if (useFlashLite) MODEL_FLASH_LITE else MODEL_FLASH
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.25f
            }
        )
        val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
        return response.text.orEmpty()
    }

    suspend fun extractPdfTextWithGemini(pdfBytes: ByteArray, fileName: String): String {
        val requestContent = content {
            inlineData(pdfBytes, "application/pdf")
            text(
                "Extract the complete technical content, equations, methodology, claims, and references from '$fileName' into clean Markdown. Preserve code blocks, tables, and section headings faithfully without omitting details."
            )
        }
        val response = withTimeout(TIMEOUT_MS) { flashStandard.generateContent(requestContent) }
        return response.text.orEmpty().ifBlank { "Unable to extract text from $fileName." }
    }

    suspend fun transcribeVoiceNoteWithGemini(
        audioBytes: ByteArray,
        mimeType: String,
        modeLabel: String,
        existingBrief: String = ""
    ): AudioTranscriptionResult {
        require(audioBytes.isNotEmpty()) { "Audio recording is empty." }
        require(audioBytes.size <= 18 * 1024 * 1024) {
            "Audio recording exceeds the 18 MB inline payload limit."
        }

        val contextHint = if (existingBrief.isNotBlank()) {
            "\nExisting draft brief already in the composer (preserve context without duplicating it verbatim):\n${existingBrief.take(1500)}"
        } else ""

        val requestContent = content {
            inlineData(audioBytes, mimeType)
            text(
                """
                Listen carefully to this recorded verbal note or project idea intended for Vortex Researcher ($modeLabel).
                1. Transcribe the spoken words faithfully into verbatimTranscript with clean punctuation and technical terminology.
                2. Distill the speaker's intent, requirements, constraints, and questions into refinedResearchBrief ready to feed directly into a 7-stage empirical research synthesis pipeline. Do not invent facts or requirements not stated in the audio.
                3. Provide a concise suggestedTitle (3-7 words), kebab-case suggestedFolderSlug, and 2-5 keyIdeas.
                4. Classify the note into the single best noteCategory (${NoteCategory.allLabels.joinToString(", ")}) and extract 3-6 concise searchable metadata tags (without #).$contextHint
                """.trimIndent()
            )
        }

        val response = withTimeout(60_000L) {
            audioTranscriberModel.generateContent(requestContent)
        }
        val jsonText = response.text?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Firebase AI returned an empty transcription response.")

        val json = JSONObject(jsonText)
        val verbatim = json.optString("verbatimTranscript").trim()
        val refined = json.optString("refinedResearchBrief").trim().ifBlank { verbatim }
        if (verbatim.isBlank() && refined.isBlank()) {
            throw IllegalStateException("No speech could be transcribed from the recording.")
        }
        val title = json.optString("suggestedTitle").trim().ifBlank { "Verbal Research Note" }
        val slug = sanitizeFolderName(json.optString("suggestedFolderSlug").ifBlank { title })
        val keyIdeasArr = json.optJSONArray("keyIdeas")
        val ideas = buildList {
            if (keyIdeasArr != null) {
                for (i in 0 until keyIdeasArr.length()) {
                    val item = keyIdeasArr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }
        val rawCategory = json.optString("noteCategory").trim()
        val resolvedCategory = if (rawCategory.isNotBlank()) {
            NoteCategory.fromWireOrLabel(rawCategory)
        } else {
            MetadataTagEngine.classifyTextCategory("$title $verbatim", isUser = true)
        }
        val tagsArr = json.optJSONArray("tags")
        val aiTags = buildList {
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    val item = tagsArr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }
        val normalizedTags = MetadataTagEngine.normalizeTags(
            raw = aiTags + MetadataTagEngine.extractKeywordTags("$title $refined", maxCount = 4),
            maxCount = 8
        )
        return AudioTranscriptionResult(
            verbatimTranscript = verbatim.ifBlank { refined },
            refinedResearchBrief = refined,
            suggestedTitle = title,
            suggestedFolderSlug = slug,
            keyIdeas = ideas,
            category = resolvedCategory,
            tags = normalizedTags,
            modelUsed = MODEL_FLASH
        )
    }

    /**
     * Extracts normalized PostgreSQL / PostGIS entity tables, typed columns, spatial geometry
     * types (Point, LineString, Polygon, Geography), and foreign-key relationships from
     * research synthesis text and project artifacts using Firebase AI Logic structured JSON.
     */
    suspend fun extractPostGisEntitiesWithGemini(
        corpus: String,
        projectTitle: String
    ): String {
        val columnSchema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string("snake_case PostgreSQL column name"),
                "data_type" to Schema.string("One of TEXT, VARCHAR, INTEGER, BIGINT, DOUBLE, NUMERIC, BOOLEAN, TIMESTAMPTZ, JSONB, TEXT_ARRAY, UUID"),
                "nullable" to Schema.boolean("Whether column allows NULL"),
                "is_unique" to Schema.boolean("Whether column has a UNIQUE constraint"),
                "sample_value" to Schema.string("Short realistic sample value grounded in the research corpus")
            ),
            optionalProperties = listOf("nullable", "is_unique", "sample_value")
        )
        val entitySchema = Schema.obj(
            properties = mapOf(
                "entity_name" to Schema.string("Human-readable entity title"),
                "table_name" to Schema.string("snake_case plural PostgreSQL table name"),
                "category" to Schema.string("One of SPATIAL_SITE, OBSERVATION, DOMAIN_ENTITY, GRAPH_NODE, EVIDENCE"),
                "description" to Schema.string("Concise 1-sentence description of what this table persists"),
                "geometry_type" to Schema.string("PostGIS geometry type: NONE, POINT, LINESTRING, POLYGON, GEOMETRY, or GEOGRAPHY_POINT"),
                "sample_wkt" to Schema.string("Valid WKT geometry if spatial (e.g. POINT(-122.4194 37.7749)), else empty string"),
                "columns" to Schema.array(columnSchema)
            ),
            optionalProperties = listOf("description", "sample_wkt")
        )
        val relationSchema = Schema.obj(
            properties = mapOf(
                "from_table" to Schema.string("Child table name holding the foreign key"),
                "from_column" to Schema.string("Foreign key column name e.g. site_id"),
                "to_table" to Schema.string("Referenced parent table name"),
                "relation_label" to Schema.string("Short UPPER_SNAKE_CASE relationship verb e.g. LOCATED_AT, OBSERVES, SUPPORTS")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "entities" to Schema.array(entitySchema),
                "relationships" to Schema.array(relationSchema)
            )
        )

        val prompt = buildString {
            appendLine("You are a Principal PostgreSQL, PostGIS, and Apache AGE Data Architect.")
            appendLine("Analyze the following research corpus for project '$projectTitle' and map the extracted domain entities into a normalized PostgreSQL + PostGIS relational schema (3 to 7 tables).")
            appendLine("Rules:")
            appendLine("1. Identify every distinct domain entity, geospatial site/corridor/region, empirical measurement/observation, graph node type, and evidence/citation source.")
            appendLine("2. Assign an appropriate PostGIS geometry_type (POINT, LINESTRING, POLYGON, GEOGRAPHY_POINT) to any entity that represents physical locations, regions, sensors, trajectories, or geospatial zones; use NONE for purely non-spatial entities.")
            appendLine("3. Provide realistic snake_case column names (do NOT include 'id' or 'geom' in columns; they are generated automatically), accurate PostgreSQL types, and foreign-key relationships between tables.")
            appendLine("4. Ground sample_value and sample_wkt in facts/coordinates from the corpus whenever available.")
            appendLine()
            appendLine("RESEARCH CORPUS:")
            appendLine(corpus.take(14_000))
        }

        return generateStructuredJson(
            prompt = prompt,
            schema = rootSchema,
            useFlashLite = false
        )
    }

    private suspend fun callGeminiGrounded(
        model: com.google.firebase.ai.GenerativeModel,
        modelLabel: String,
        prompt: String,
        routingReason: String,
        fallbackNote: String = ""
    ): GroundedGenerationResult {
        val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
        val text = response.text.orEmpty().trim()
        val metadata = response.candidates.firstOrNull()?.groundingMetadata
        val queries = metadata?.webSearchQueries.orEmpty()
        val sources = metadata?.groundingChunks
            ?.mapNotNull { chunk ->
                val web = chunk.web
                val uri = web?.uri.orEmpty()
                val title = web?.title.orEmpty().ifBlank { uri }
                if (uri.isNotBlank()) GroundingSource(title = title, uri = uri) else null
            }
            ?.distinctBy { it.uri }
            .orEmpty()

        return GroundedGenerationResult(
            text = text,
            modelUsed = modelLabel,
            routingReason = routingReason,
            fallbackNote = fallbackNote,
            searchQueries = queries,
            sources = sources
        )
    }

    private data class RouteDecision(
        val targetModel: String,
        val useExternalApi: Boolean,
        val reason: String
    )

    private fun resolveRoute(
        domain: String,
        complexityScore: Int,
        highStakesStage: Boolean,
        prefs: RoutingPreferences
    ): RouteDecision {
        if (!prefs.strategicRoutingEnabled) {
            return RouteDecision(
                targetModel = MODEL_FLASH,
                useExternalApi = false,
                reason = "Strategic Model Routing disabled — locked to $MODEL_FLASH (auto-upgrade off)."
            )
        }

        if (prefs.routingMode == RoutingMode.FLASH_ONLY) {
            return RouteDecision(
                targetModel = MODEL_FLASH,
                useExternalApi = false,
                reason = "Flash Only mode active ($MODEL_FLASH)."
            )
        }

        val mappedModel = prefs.domainModelMatrix[domain]?.trim().orEmpty()
        val isCustomExternal = mappedModel.isNotBlank() &&
            mappedModel != MODEL_FLASH &&
            mappedModel != MODEL_ARGON &&
            mappedModel != MODEL_FLASH_LITE

        val shouldEscalate = when (prefs.routingMode) {
            RoutingMode.MAX_PRECISION -> true
            RoutingMode.AUTO_STRATEGIC -> highStakesStage && complexityScore >= prefs.escalationThreshold
            RoutingMode.FLASH_ONLY -> false
        }

        if (!shouldEscalate) {
            return RouteDecision(
                targetModel = MODEL_FLASH,
                useExternalApi = false,
                reason = "Complexity $complexityScore/10 handled optimally by $MODEL_FLASH."
            )
        }

        if (isCustomExternal && prefs.externalEnabled && prefs.externalApiKey.isNotBlank()) {
            return RouteDecision(
                targetModel = mappedModel,
                useExternalApi = true,
                reason = "Domain '$domain' (complexity $complexityScore/10) escalated to external model $mappedModel."
            )
        }

        val target = if (mappedModel == MODEL_FLASH && prefs.routingMode == RoutingMode.AUTO_STRATEGIC) {
            MODEL_FLASH
        } else {
            MODEL_ARGON
        }
        return RouteDecision(
            targetModel = target,
            useExternalApi = false,
            reason = if (target == MODEL_ARGON) {
                "Complexity $complexityScore/10 in '$domain' surgically escalated to $MODEL_ARGON."
            } else {
                "Domain '$domain' mapped to $MODEL_FLASH."
            }
        )
    }

    fun sanitizeFolderName(raw: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
        return cleaned.ifBlank { "vortex-research" }
    }
}
