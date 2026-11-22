package com.example.ui.components

import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.StageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class PgEntityCategory(val label: String) {
    SPATIAL_SITE("Spatial / GIS"),
    OBSERVATION("Observation / Metric"),
    DOMAIN_ENTITY("Domain Entity"),
    GRAPH_NODE("Graph Node"),
    EVIDENCE("Evidence / Citation")
}

enum class PgGeometryKind(val label: String, val postGisType: String?) {
    NONE("No Geometry", null),
    POINT("Point", "GEOMETRY(Point, %d)"),
    LINESTRING("LineString", "GEOMETRY(LineString, %d)"),
    POLYGON("Polygon", "GEOMETRY(Polygon, %d)"),
    GENERIC("Geometry", "GEOMETRY(Geometry, %d)"),
    GEOGRAPHY_POINT("Geography Point", "GEOGRAPHY(Point, 4326)");

    fun formatSqlType(srid: Int): String? {
        val raw = postGisType ?: return null
        return if (raw.contains("%d")) String.format(Locale.US, raw, srid) else raw
    }
}

enum class PgColumnDataType(val label: String, val sqlType: String) {
    TEXT("TEXT", "TEXT"),
    VARCHAR("VARCHAR(255)", "VARCHAR(255)"),
    INTEGER("INTEGER", "INTEGER"),
    BIGINT("BIGINT", "BIGINT"),
    DOUBLE("DOUBLE", "DOUBLE PRECISION"),
    NUMERIC("NUMERIC", "NUMERIC(14,4)"),
    BOOLEAN("BOOLEAN", "BOOLEAN"),
    TIMESTAMPTZ("TIMESTAMPTZ", "TIMESTAMPTZ"),
    JSONB("JSONB", "JSONB"),
    TEXT_ARRAY("TEXT[]", "TEXT[]"),
    UUID("UUID", "UUID");

    companion object {
        fun fromHint(raw: String): PgColumnDataType {
            val u = raw.trim().uppercase(Locale.US)
            return when {
                u.contains("UUID") -> UUID
                u.contains("JSON") -> JSONB
                u.contains("BOOL") -> BOOLEAN
                u.contains("TIME") || u.contains("DATE") -> TIMESTAMPTZ
                u.contains("DOUBLE") || u.contains("FLOAT") || u.contains("REAL") -> DOUBLE
                u.contains("NUM") || u.contains("DEC") -> NUMERIC
                u.contains("BIGINT") -> BIGINT
                u.contains("INT") -> INTEGER
                u.contains("ARRAY") || u.endsWith("[]") -> TEXT_ARRAY
                u.contains("VARCHAR") -> VARCHAR
                else -> TEXT
            }
        }
    }
}

data class PgMappedColumn(
    val id: String,
    val name: String,
    val dataType: PgColumnDataType = PgColumnDataType.TEXT,
    val nullable: Boolean = true,
    val isUnique: Boolean = false,
    val referencesTable: String? = null,
    val sampleValue: String = ""
)

data class PgMappedEntity(
    val id: String,
    val entityName: String,
    val tableName: String,
    val category: PgEntityCategory,
    val description: String = "",
    val geometryKind: PgGeometryKind = PgGeometryKind.NONE,
    val geometryColumnName: String = "geom",
    val columns: List<PgMappedColumn> = emptyList(),
    val enabled: Boolean = true,
    val sampleWkt: String = "",
    val sourceLabel: String = "Heuristic"
)

data class PgMappedRelationship(
    val id: String,
    val fromTable: String,
    val fromColumn: String,
    val toTable: String,
    val relationLabel: String = "RELATED_TO",
    val onDelete: String = "SET NULL"
)

data class PgSchemaMappingConfig(
    val schemaName: String = "research",
    val defaultSrid: Int = 4326,
    val enablePostGis: Boolean = true,
    val enableApacheAge: Boolean = true,
    val ageGraphName: String = "research_kg",
    val includeSpatialIndexes: Boolean = true,
    val includeGinIndexes: Boolean = true,
    val includeSeedInserts: Boolean = true,
    val includeAnalyticsViews: Boolean = true,
    val useUuidPrimaryKeys: Boolean = false
)

data class PgSchemaMappingPlan(
    val entities: List<PgMappedEntity>,
    val relationships: List<PgMappedRelationship>,
    val sourceSummary: String,
    val usedAiExtraction: Boolean = false
)

object PostGisSchemaMapperEngine {

    private val reservedPgWords = setOf(
        "user", "order", "group", "table", "select", "from", "where", "index", "check",
        "primary", "foreign", "references", "constraint", "default", "all", "and", "or",
        "not", "null", "true", "false", "case", "when", "then", "else", "end", "limit",
        "offset", "union", "join", "create", "drop", "alter", "grant", "to", "in", "as"
    )

    private val spatialKeywords = listOf(
        "location", "coord", "latitude", "longitude", "lat", "lon", "lng", "region", "zone",
        "polygon", "boundary", "route", "corridor", "parcel", "station", "sensor", "site",
        "basin", "watershed", "city", "campus", "facility", "node", "geo", "postgis", "spatial",
        "map", "area", "district", "footprint", "point", "trajectory", "track"
    )

    private val coordPairRegex = Regex(
        """(?i)(?:lat(?:itude)?\s*[:=]\s*([-+]?\d{1,2}\.\d{2,6})[^0-9+-]{1,12}lon(?:g(?:itude)?)?\s*[:=]\s*([-+]?\d{1,3}\.\d{2,6}))|\(\s*([-+]?\d{1,2}\.\d{3,6})\s*,\s*([-+]?\d{1,3}\.\d{3,6})\s*\)"""
    )

    fun sanitizeIdentifier(raw: String, fallback: String): String {
        var cleaned = raw.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        if (cleaned.isEmpty()) cleaned = fallback
        if (cleaned.first().isDigit()) cleaned = "e_$cleaned"
        if (cleaned in reservedPgWords) cleaned = "${cleaned}_item"
        return cleaned.take(60)
    }

    fun validateSrid(srid: Int): Int = if (srid in 2000..999999) srid else 4326

    fun escapeSqlLiteral(raw: String): String =
        raw.replace("\u0000", "").replace("'", "''").replace("\r\n", "\n").trim()

    fun buildExtractionCorpus(
        project: ResearchProject?,
        stages: List<StageRecord>,
        roundtableItems: List<RoundtableItem>,
        files: List<ProjectFile>
    ): String = buildString {
        if (project != null) {
            appendLine("# Project: ${project.title.ifBlank { project.rootFolderName }}")
            if (project.detectedDomain.isNotBlank()) appendLine("Domain: ${project.detectedDomain}")
            if (project.query.isNotBlank()) appendLine("Research Query: ${project.query}")
            if (project.chatAnswer.isNotBlank()) {
                appendLine("\n## Synthesis\n${project.chatAnswer.take(4000)}")
            }
        }
        stages.sortedBy { it.stageNumber }.forEach { st ->
            if (st.content.isNotBlank()) {
                appendLine("\n## Stage ${st.stageNumber}: ${st.title}")
                appendLine(st.content.take(2200))
            }
        }
        roundtableItems.take(6).forEach { rt ->
            if (rt.content.isNotBlank()) {
                appendLine("\n## Roundtable ${rt.fileName}")
                appendLine(rt.content.take(1200))
            }
        }
        files.take(10).forEach { f ->
            if (f.content.isNotBlank()) {
                appendLine("\n## File ${f.relativePath}")
                appendLine(f.content.take(1600))
            }
        }
    }.take(18_000)

    fun extractEntitiesLocally(
        project: ResearchProject?,
        stages: List<StageRecord>,
        roundtableItems: List<RoundtableItem>,
        files: List<ProjectFile>
    ): PgSchemaMappingPlan {
        val entitiesByTable = LinkedHashMap<String, PgMappedEntity>()
        val relationships = mutableListOf<PgMappedRelationship>()
        val corpus = buildExtractionCorpus(project, stages, roundtableItems, files)

        fun addEntity(entity: PgMappedEntity) {
            if (!entitiesByTable.containsKey(entity.tableName)) {
                entitiesByTable[entity.tableName] = entity
            }
        }

        // 1. Extract spatial entities directly from GeoJSON/WKT files or inline blocks
        val specializedSpecs = SpecializedFormatEngine.collectProjectSpecializedArtifacts(
            stages = stages,
            synthesisMarkdown = project?.chatAnswer.orEmpty(),
            project = project,
            projectFiles = files
        )
        specializedSpecs.forEach { spec ->
            when (spec.kind) {
                SpecializedFormatKind.GEOSPATIAL -> {
                    val geo = SpecializedFormatParser.parseGeospatial(spec.code)
                    val firstShape = geo.features.firstOrNull()?.shapes?.firstOrNull()
                    val geomKind = when (firstShape) {
                        is GeoShape.Polygon -> PgGeometryKind.POLYGON
                        is GeoShape.LineString -> PgGeometryKind.LINESTRING
                        is GeoShape.Point -> PgGeometryKind.POINT
                        null -> PgGeometryKind.GENERIC
                    }
                    val propKeys = geo.features.flatMap { it.properties.keys }.distinct().take(5)
                    val cols = buildList {
                        add(PgMappedColumn("c_name", "name", PgColumnDataType.TEXT, nullable = false, sampleValue = geo.features.firstOrNull()?.label ?: spec.title))
                        propKeys.forEachIndexed { idx, k ->
                            val sample = geo.features.firstOrNull()?.properties?.get(k).orEmpty()
                            add(
                                PgMappedColumn(
                                    id = "c_geo_$idx",
                                    name = sanitizeIdentifier(k, "attr_$idx"),
                                    dataType = inferScalarType(sample),
                                    sampleValue = sample
                                )
                            )
                        }
                        add(PgMappedColumn("c_props", "properties", PgColumnDataType.JSONB, sampleValue = "{}"))
                    }
                    val sampleWkt = firstShape?.let { shapeToWkt(it) } ?: "POINT(0 0)"
                    val tableName = sanitizeIdentifier(spec.title.substringBefore('.'), "spatial_features")
                    addEntity(
                        PgMappedEntity(
                            id = "ent_geo_${tableName}",
                            entityName = spec.title.substringBefore('.'),
                            tableName = tableName,
                            category = PgEntityCategory.SPATIAL_SITE,
                            description = "Mapped from ${geo.sourceDialect} (${geo.features.size} spatial features)",
                            geometryKind = geomKind,
                            columns = cols,
                            sampleWkt = sampleWkt,
                            sourceLabel = "PostGIS Geo"
                        )
                    )
                }

                SpecializedFormatKind.CYPHER_AGE,
                SpecializedFormatKind.GRAPH_DOT,
                SpecializedFormatKind.GRAPH_EXCHANGE,
                SpecializedFormatKind.POSTGRES_SQL -> {
                    val graph = SpecializedFormatParser.parseToUnifiedGraph(spec.kind, spec.code, spec.title)
                    val groupedNodes = graph.nodes.groupBy { it.group.ifBlank { it.label }.take(32) }
                    groupedNodes.entries.take(5).forEach { (grp, nodes) ->
                        val tbl = sanitizeIdentifier(grp, "graph_entity")
                        val isSpatial = looksSpatial(grp + " " + nodes.joinToString(" ") { it.label })
                        val propKeys = nodes.flatMap { it.properties.keys }.distinct().take(4)
                        val cols = buildList {
                            add(PgMappedColumn("c_key", "external_key", PgColumnDataType.VARCHAR, nullable = false, isUnique = true, sampleValue = nodes.firstOrNull()?.id ?: "n1"))
                            add(PgMappedColumn("c_label", "display_name", PgColumnDataType.TEXT, nullable = false, sampleValue = nodes.firstOrNull()?.label ?: grp))
                            propKeys.forEachIndexed { idx, k ->
                                val sample = nodes.firstOrNull()?.properties?.get(k).orEmpty()
                                add(PgMappedColumn("c_p_$idx", sanitizeIdentifier(k, "prop_$idx"), inferScalarType(sample), sampleValue = sample))
                            }
                            add(PgMappedColumn("c_meta", "attributes", PgColumnDataType.JSONB, sampleValue = "{}"))
                        }
                        addEntity(
                            PgMappedEntity(
                                id = "ent_g_$tbl",
                                entityName = grp,
                                tableName = tbl,
                                category = if (isSpatial) PgEntityCategory.SPATIAL_SITE else PgEntityCategory.GRAPH_NODE,
                                description = "Extracted from ${graph.sourceDialect} (${nodes.size} nodes)",
                                geometryKind = if (isSpatial) PgGeometryKind.POINT else PgGeometryKind.NONE,
                                columns = cols,
                                sampleWkt = if (isSpatial) "POINT(-122.4194 37.7749)" else "",
                                sourceLabel = spec.kind.badgeLabel
                            )
                        )
                    }
                    // Map graph edges into relational foreign keys where source/target map to distinct groups
                    val nodeToTable = graph.nodes.associate { n ->
                        n.id to sanitizeIdentifier(n.group.ifBlank { n.label }.take(32), "graph_entity")
                    }
                    graph.edges.forEachIndexed { idx, edge ->
                        val srcTbl = nodeToTable[edge.source]
                        val dstTbl = nodeToTable[edge.target]
                        if (srcTbl != null && dstTbl != null && srcTbl != dstTbl &&
                            entitiesByTable.containsKey(srcTbl) && entitiesByTable.containsKey(dstTbl)
                        ) {
                            val fkCol = sanitizeIdentifier("${dstTbl}_id", "ref_id")
                            relationships.add(
                                PgMappedRelationship(
                                    id = "rel_g_$idx",
                                    fromTable = srcTbl,
                                    fromColumn = fkCol,
                                    toTable = dstTbl,
                                    relationLabel = edge.label.ifBlank { "REFERENCES" }
                                )
                            )
                        }
                    }
                }

                SpecializedFormatKind.LATEX -> Unit
            }
        }

        // 2. Scan raw coordinates mentioned in prose
        val inlineCoords = coordPairRegex.findAll(corpus).mapNotNull { m ->
            val lat = (m.groupValues[1].ifBlank { m.groupValues[3] }).toDoubleOrNull()
            val lon = (m.groupValues[2].ifBlank { m.groupValues[4] }).toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                GeoCoord(lon, lat)
            } else null
        }.toList()

        if (inlineCoords.isNotEmpty() && entitiesByTable.values.none { it.geometryKind != PgGeometryKind.NONE }) {
            val first = inlineCoords.first()
            addEntity(
                PgMappedEntity(
                    id = "ent_inline_coords",
                    entityName = "Spatial Sites",
                    tableName = "spatial_sites",
                    category = PgEntityCategory.SPATIAL_SITE,
                    description = "${inlineCoords.size} coordinate pairs extracted from research text",
                    geometryKind = PgGeometryKind.POINT,
                    columns = listOf(
                        PgMappedColumn("c_name", "site_name", PgColumnDataType.TEXT, nullable = false, sampleValue = project?.title?.take(48) ?: "Primary Site"),
                        PgMappedColumn("c_region", "region_code", PgColumnDataType.VARCHAR, sampleValue = "WGS84"),
                        PgMappedColumn("c_elev", "elevation_m", PgColumnDataType.DOUBLE, sampleValue = "0.0"),
                        PgMappedColumn("c_meta", "metadata", PgColumnDataType.JSONB, sampleValue = "{}")
                    ),
                    sampleWkt = String.format(Locale.US, "POINT(%.5f %.5f)", first.lon, first.lat),
                    sourceLabel = "Coordinates"
                )
            )
        }

        // 3. Extract structured Markdown tables as candidate research entities
        extractMarkdownTableEntities(corpus).take(3).forEach { addEntity(it) }

        // 4. Extract domain entities from headings / query / stages so every project has rich relational + spatial coverage
        val domainLabel = project?.detectedDomain?.ifBlank { "Research" } ?: "Research"
        val corpusLooksSpatial = looksSpatial(corpus)
        if (entitiesByTable.isEmpty() || entitiesByTable.size < 2) {
            val studyTable = sanitizeIdentifier(
                project?.rootFolderName?.ifBlank { domainLabel } ?: "research_entities",
                "research_entities"
            )
            addEntity(
                PgMappedEntity(
                    id = "ent_study_core",
                    entityName = "${domainLabel.take(28)} Entities",
                    tableName = studyTable,
                    category = if (corpusLooksSpatial) PgEntityCategory.SPATIAL_SITE else PgEntityCategory.DOMAIN_ENTITY,
                    description = "Core research entity table derived from project brief and synthesis",
                    geometryKind = if (corpusLooksSpatial) PgGeometryKind.POINT else PgGeometryKind.NONE,
                    columns = listOf(
                        PgMappedColumn("c_title", "entity_name", PgColumnDataType.TEXT, nullable = false, sampleValue = project?.title?.take(56)?.ifBlank { "Primary Entity" } ?: "Primary Entity"),
                        PgMappedColumn("c_domain", "domain_tag", PgColumnDataType.VARCHAR, sampleValue = domainLabel),
                        PgMappedColumn("c_score", "confidence_score", PgColumnDataType.DOUBLE, sampleValue = "0.92"),
                        PgMappedColumn("c_tags", "tags", PgColumnDataType.TEXT_ARRAY, sampleValue = "ARRAY['research','verified']"),
                        PgMappedColumn("c_payload", "attributes", PgColumnDataType.JSONB, sampleValue = "{}")
                    ),
                    sampleWkt = if (corpusLooksSpatial) "POINT(-122.4194 37.7749)" else "",
                    sourceLabel = "Project Brief"
                )
            )
        }

        // Always offer an observations/findings table linked via FK to the primary entity for analytical joins
        val primaryTable = entitiesByTable.keys.first()
        if (!entitiesByTable.containsKey("research_observations")) {
            addEntity(
                PgMappedEntity(
                    id = "ent_observations",
                    entityName = "Research Observations",
                    tableName = "research_observations",
                    category = PgEntityCategory.OBSERVATION,
                    description = "Empirical findings, measurements, and stage outputs linked to $primaryTable",
                    geometryKind = if (corpusLooksSpatial) PgGeometryKind.POLYGON else PgGeometryKind.NONE,
                    columns = listOf(
                        PgMappedColumn("c_fk", "${primaryTable}_id", PgColumnDataType.BIGINT, nullable = true, referencesTable = primaryTable),
                        PgMappedColumn("c_stage", "stage_number", PgColumnDataType.INTEGER, sampleValue = stages.lastOrNull()?.stageNumber?.toString() ?: "1"),
                        PgMappedColumn("c_metric", "metric_name", PgColumnDataType.VARCHAR, nullable = false, sampleValue = "synthesis_confidence"),
                        PgMappedColumn("c_val", "metric_value", PgColumnDataType.DOUBLE, sampleValue = (project?.complexityScore ?: 7).toString()),
                        PgMappedColumn("c_summary", "finding_summary", PgColumnDataType.TEXT, sampleValue = project?.query?.take(90)?.ifBlank { "Empirical observation" } ?: "Empirical observation"),
                        PgMappedColumn("c_observed", "observed_at", PgColumnDataType.TIMESTAMPTZ)
                    ),
                    sampleWkt = if (corpusLooksSpatial) "POLYGON((-122.45 37.75, -122.38 37.75, -122.38 37.81, -122.45 37.81, -122.45 37.75))" else "",
                    sourceLabel = "Pipeline Stages"
                )
            )
            relationships.add(
                PgMappedRelationship(
                    id = "rel_obs_primary",
                    fromTable = "research_observations",
                    fromColumn = "${primaryTable}_id",
                    toTable = primaryTable,
                    relationLabel = "OBSERVES"
                )
            )
        }

        if (!entitiesByTable.containsKey("evidence_sources")) {
            addEntity(
                PgMappedEntity(
                    id = "ent_evidence",
                    entityName = "Evidence & Citations",
                    tableName = "evidence_sources",
                    category = PgEntityCategory.EVIDENCE,
                    description = "Grounding sources, papers, and repository artifacts supporting extracted findings",
                    geometryKind = PgGeometryKind.NONE,
                    columns = listOf(
                        PgMappedColumn("c_obs_fk", "observation_id", PgColumnDataType.BIGINT, referencesTable = "research_observations"),
                        PgMappedColumn("c_title", "source_title", PgColumnDataType.TEXT, nullable = false, sampleValue = files.firstOrNull()?.relativePath ?: "Synthesis Brief"),
                        PgMappedColumn("c_uri", "source_uri", PgColumnDataType.TEXT, sampleValue = "workspace://${project?.rootFolderName ?: "project"}"),
                        PgMappedColumn("c_meta", "citation_meta", PgColumnDataType.JSONB, sampleValue = "{}")
                    ),
                    sourceLabel = "Citations"
                )
            )
            relationships.add(
                PgMappedRelationship(
                    id = "rel_ev_obs",
                    fromTable = "evidence_sources",
                    fromColumn = "observation_id",
                    toTable = "research_observations",
                    relationLabel = "SUPPORTS"
                )
            )
        }

        val uniqueRels = relationships.distinctBy { "${it.fromTable}.${it.fromColumn}->${it.toTable}" }
        val spatialCount = entitiesByTable.values.count { it.geometryKind != PgGeometryKind.NONE }
        val summary = "${entitiesByTable.size} tables ($spatialCount PostGIS spatial) · ${uniqueRels.size} relations"

        return PgSchemaMappingPlan(
            entities = entitiesByTable.values.toList(),
            relationships = uniqueRels,
            sourceSummary = summary,
            usedAiExtraction = false
        )
    }

    fun parseAiExtractionJson(rawJson: String): Result<PgSchemaMappingPlan> = runCatching {
        val cleaned = rawJson.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = JSONObject(cleaned)
        val entitiesArr = root.optJSONArray("entities") ?: JSONArray()
        val entities = mutableListOf<PgMappedEntity>()
        val tableNames = HashSet<String>()

        for (i in 0 until entitiesArr.length()) {
            val e = entitiesArr.optJSONObject(i) ?: continue
            val displayName = e.optString("entity_name").ifBlank { "Entity ${i + 1}" }
            val tbl = sanitizeIdentifier(e.optString("table_name").ifBlank { displayName }, "entity_${i + 1}")
            if (!tableNames.add(tbl)) continue

            val catRaw = e.optString("category").uppercase(Locale.US)
            val category = when {
                catRaw.contains("SPATIAL") || catRaw.contains("GIS") -> PgEntityCategory.SPATIAL_SITE
                catRaw.contains("OBS") || catRaw.contains("METRIC") -> PgEntityCategory.OBSERVATION
                catRaw.contains("GRAPH") -> PgEntityCategory.GRAPH_NODE
                catRaw.contains("EVID") || catRaw.contains("CIT") -> PgEntityCategory.EVIDENCE
                else -> PgEntityCategory.DOMAIN_ENTITY
            }
            val geomRaw = e.optString("geometry_type").uppercase(Locale.US)
            val geomKind = when {
                geomRaw.contains("GEOGRAPHY") -> PgGeometryKind.GEOGRAPHY_POINT
                geomRaw.contains("POLYGON") -> PgGeometryKind.POLYGON
                geomRaw.contains("LINE") -> PgGeometryKind.LINESTRING
                geomRaw.contains("POINT") -> PgGeometryKind.POINT
                geomRaw.contains("GEOM") -> PgGeometryKind.GENERIC
                else -> PgGeometryKind.NONE
            }
            val colsArr = e.optJSONArray("columns") ?: JSONArray()
            val cols = mutableListOf<PgMappedColumn>()
            val seenCols = HashSet<String>()
            for (cIdx in 0 until colsArr.length()) {
                val cObj = colsArr.optJSONObject(cIdx) ?: continue
                val cName = sanitizeIdentifier(cObj.optString("name"), "col_$cIdx")
                if (cName == "id" || cName == "geom" || !seenCols.add(cName)) continue
                cols.add(
                    PgMappedColumn(
                        id = "ai_${tbl}_$cIdx",
                        name = cName,
                        dataType = PgColumnDataType.fromHint(cObj.optString("data_type", "TEXT")),
                        nullable = cObj.optBoolean("nullable", true),
                        isUnique = cObj.optBoolean("is_unique", false),
                        sampleValue = cObj.optString("sample_value")
                    )
                )
            }
            if (cols.isEmpty()) {
                cols.add(PgMappedColumn("ai_${tbl}_name", "name", PgColumnDataType.TEXT, nullable = false, sampleValue = displayName))
            }
            val wkt = e.optString("sample_wkt").ifBlank {
                when (geomKind) {
                    PgGeometryKind.POINT, PgGeometryKind.GEOGRAPHY_POINT -> "POINT(-122.4194 37.7749)"
                    PgGeometryKind.LINESTRING -> "LINESTRING(-122.42 37.77, -122.40 37.79)"
                    PgGeometryKind.POLYGON -> "POLYGON((-122.44 37.76, -122.39 37.76, -122.39 37.80, -122.44 37.80, -122.44 37.76))"
                    PgGeometryKind.GENERIC -> "POINT(0 0)"
                    PgGeometryKind.NONE -> ""
                }
            }
            entities.add(
                PgMappedEntity(
                    id = "ai_ent_$tbl",
                    entityName = displayName,
                    tableName = tbl,
                    category = category,
                    description = e.optString("description"),
                    geometryKind = geomKind,
                    columns = cols,
                    sampleWkt = wkt,
                    sourceLabel = "Firebase AI"
                )
            )
        }

        require(entities.isNotEmpty()) { "Firebase AI returned no mappable entities." }

        val relsArr = root.optJSONArray("relationships") ?: JSONArray()
        val rels = mutableListOf<PgMappedRelationship>()
        for (i in 0 until relsArr.length()) {
            val r = relsArr.optJSONObject(i) ?: continue
            val fromTbl = sanitizeIdentifier(r.optString("from_table"), "")
            val toTbl = sanitizeIdentifier(r.optString("to_table"), "")
            if (fromTbl in tableNames && toTbl in tableNames && fromTbl != toTbl) {
                val fkCol = sanitizeIdentifier(r.optString("from_column").ifBlank { "${toTbl}_id" }, "${toTbl}_id")
                rels.add(
                    PgMappedRelationship(
                        id = "ai_rel_$i",
                        fromTable = fromTbl,
                        fromColumn = fkCol,
                        toTable = toTbl,
                        relationLabel = r.optString("relation_label").ifBlank { "LINKS_TO" }
                    )
                )
            }
        }

        val spatialCount = entities.count { it.geometryKind != PgGeometryKind.NONE }
        PgSchemaMappingPlan(
            entities = entities,
            relationships = rels.distinctBy { "${it.fromTable}.${it.fromColumn}->${it.toTable}" },
            sourceSummary = "Firebase AI mapped ${entities.size} tables ($spatialCount PostGIS spatial) · ${rels.size} relations",
            usedAiExtraction = true
        )
    }

    fun generatePostgresMigrationSql(
        plan: PgSchemaMappingPlan,
        config: PgSchemaMappingConfig,
        projectTitle: String
    ): String = buildString {
        val schema = sanitizeIdentifier(config.schemaName, "research")
        val srid = validateSrid(config.defaultSrid)
        val activeEntities = plan.entities.filter { it.enabled }
        val activeTableSet = activeEntities.map { sanitizeIdentifier(it.tableName, "entity") }.toSet()
        val pkType = if (config.useUuidPrimaryKeys) "UUID" else "BIGINT"
        val pkDef = if (config.useUuidPrimaryKeys) "UUID PRIMARY KEY DEFAULT gen_random_uuid()" else "BIGSERIAL PRIMARY KEY"
        val anySpatial = config.enablePostGis && activeEntities.any { it.geometryKind != PgGeometryKind.NONE }

        appendLine("-- ============================================================================")
        appendLine("-- PostgreSQL / PostGIS Research Entity Persistence Schema")
        appendLine("-- Project : ${projectTitle.replace("\n", " ").take(80)}")
        appendLine("-- Schema  : $schema | SRID: EPSG:$srid | Entities: ${activeEntities.size}")
        appendLine("-- ============================================================================")
        appendLine()
        appendLine("BEGIN;")
        appendLine()
        if (config.useUuidPrimaryKeys) {
            appendLine("CREATE EXTENSION IF NOT EXISTS pgcrypto;")
        }
        if (config.enablePostGis) {
            appendLine("CREATE EXTENSION IF NOT EXISTS postgis;")
        }
        if (config.enableApacheAge) {
            appendLine("CREATE EXTENSION IF NOT EXISTS age;")
            appendLine("LOAD 'age';")
            appendLine("SET search_path = ag_catalog, \"$schema\", public;")
        }
        appendLine()
        appendLine("CREATE SCHEMA IF NOT EXISTS $schema;")
        appendLine()

        // Emit CREATE TABLE statements
        activeEntities.forEach { entity ->
            val tbl = sanitizeIdentifier(entity.tableName, "entity")
            val entityRels = plan.relationships.filter {
                sanitizeIdentifier(it.fromTable, "") == tbl &&
                    sanitizeIdentifier(it.toTable, "") in activeTableSet
            }
            if (entity.description.isNotBlank()) {
                appendLine("-- ${entity.entityName}: ${entity.description.replace("\n", " ")}")
            }
            appendLine("CREATE TABLE IF NOT EXISTS $schema.$tbl (")
            val lines = mutableListOf<String>()
            lines.add("    id $pkDef")

            val declaredColNames = mutableSetOf("id")
            entity.columns.forEach { col ->
                val cName = sanitizeIdentifier(col.name, "field")
                if (!declaredColNames.add(cName)) return@forEach
                val refTbl = col.referencesTable?.let { sanitizeIdentifier(it, "") }?.takeIf { it in activeTableSet }
                val colSqlType = if (refTbl != null) pkType else col.dataType.sqlType
                val constraints = buildString {
                    if (!col.nullable) append(" NOT NULL")
                    if (col.isUnique) append(" UNIQUE")
                    if (col.dataType == PgColumnDataType.JSONB && col.nullable) append(" DEFAULT '{}'::jsonb")
                    if (col.dataType == PgColumnDataType.TIMESTAMPTZ && col.nullable) append(" DEFAULT NOW()")
                    if (refTbl != null) append(" REFERENCES $schema.$refTbl(id) ON DELETE SET NULL")
                }
                lines.add("    $cName $colSqlType$constraints")
            }

            // Add relationship FKs not already declared as columns
            entityRels.forEach { rel ->
                val fkCol = sanitizeIdentifier(rel.fromColumn, "${rel.toTable}_id")
                val targetTbl = sanitizeIdentifier(rel.toTable, "")
                if (declaredColNames.add(fkCol)) {
                    lines.add("    $fkCol $pkType REFERENCES $schema.$targetTbl(id) ON DELETE ${rel.onDelete}")
                }
            }

            if (config.enablePostGis && entity.geometryKind != PgGeometryKind.NONE) {
                val geomCol = sanitizeIdentifier(entity.geometryColumnName, "geom")
                val geomType = entity.geometryKind.formatSqlType(srid)
                if (geomType != null && declaredColNames.add(geomCol)) {
                    lines.add("    $geomCol $geomType")
                }
            }

            if (declaredColNames.add("created_at")) {
                lines.add("    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()")
            }

            appendLine(lines.joinToString(",\n"))
            appendLine(");")
            appendLine()
        }

        // Emit PostGIS GiST, JSONB GIN, and FK B-Tree indexes
        activeEntities.forEach { entity ->
            val tbl = sanitizeIdentifier(entity.tableName, "entity")
            if (config.enablePostGis && config.includeSpatialIndexes && entity.geometryKind != PgGeometryKind.NONE) {
                val geomCol = sanitizeIdentifier(entity.geometryColumnName, "geom")
                appendLine("CREATE INDEX IF NOT EXISTS idx_${tbl}_${geomCol}_gist ON $schema.$tbl USING GIST ($geomCol);")
            }
            if (config.includeGinIndexes) {
                entity.columns.filter { it.dataType == PgColumnDataType.JSONB || it.dataType == PgColumnDataType.TEXT_ARRAY }
                    .forEach { col ->
                        val cName = sanitizeIdentifier(col.name, "field")
                        appendLine("CREATE INDEX IF NOT EXISTS idx_${tbl}_${cName}_gin ON $schema.$tbl USING GIN ($cName);")
                    }
            }
            plan.relationships.filter {
                sanitizeIdentifier(it.fromTable, "") == tbl && sanitizeIdentifier(it.toTable, "") in activeTableSet
            }.forEach { rel ->
                val fkCol = sanitizeIdentifier(rel.fromColumn, "${rel.toTable}_id")
                appendLine("CREATE INDEX IF NOT EXISTS idx_${tbl}_${fkCol} ON $schema.$tbl ($fkCol);")
            }
        }
        appendLine()

        // Optional seed INSERT statements
        if (config.includeSeedInserts && activeEntities.isNotEmpty()) {
            appendLine("-- Sample Seed Records Extracted from Research Artifacts")
            activeEntities.forEach { entity ->
                val tbl = sanitizeIdentifier(entity.tableName, "entity")
                val insertCols = mutableListOf<String>()
                val insertVals = mutableListOf<String>()
                entity.columns.filter { it.referencesTable == null && it.dataType != PgColumnDataType.TIMESTAMPTZ }
                    .take(5)
                    .forEach { col ->
                        val cName = sanitizeIdentifier(col.name, "field")
                        insertCols.add(cName)
                        insertVals.add(formatSeedLiteral(col))
                    }
                if (config.enablePostGis && entity.geometryKind != PgGeometryKind.NONE) {
                    val geomCol = sanitizeIdentifier(entity.geometryColumnName, "geom")
                    val wkt = escapeSqlLiteral(entity.sampleWkt.ifBlank { "POINT(0 0)" })
                    insertCols.add(geomCol)
                    val expr = if (entity.geometryKind == PgGeometryKind.GEOGRAPHY_POINT) {
                        "ST_GeogFromText('SRID=4326;$wkt')"
                    } else {
                        "ST_SetSRID(ST_GeomFromText('$wkt'), $srid)"
                    }
                    insertVals.add(expr)
                }
                if (insertCols.isNotEmpty()) {
                    appendLine("INSERT INTO $schema.$tbl (${insertCols.joinToString(", ")})")
                    appendLine("VALUES (${insertVals.joinToString(", ")});")
                }
            }
            appendLine()
        }

        // Optional Apache AGE Graph Projection
        if (config.enableApacheAge && activeEntities.isNotEmpty()) {
            val graphName = sanitizeIdentifier(config.ageGraphName, "research_kg")
            appendLine("-- Apache AGE Property Graph Mirror")
            appendLine("SELECT ag_catalog.create_graph('$graphName');")
            appendLine("SELECT * FROM ag_catalog.cypher('$graphName', \$\$")
            activeEntities.forEachIndexed { idx, entity ->
                val tbl = sanitizeIdentifier(entity.tableName, "entity")
                val label = entity.entityName.replace(Regex("[^A-Za-z0-9_]"), "").ifBlank { "Entity" }
                appendLine("  MERGE (e$idx:$label {table: '$tbl', category: '${escapeSqlLiteral(entity.category.label)}'})")
            }
            plan.relationships.forEach { rel ->
                val sIdx = activeEntities.indexOfFirst { sanitizeIdentifier(it.tableName, "") == sanitizeIdentifier(rel.fromTable, "") }
                val tIdx = activeEntities.indexOfFirst { sanitizeIdentifier(it.tableName, "") == sanitizeIdentifier(rel.toTable, "") }
                if (sIdx >= 0 && tIdx >= 0 && sIdx != tIdx) {
                    val edgeLabel = rel.relationLabel.uppercase(Locale.US).replace(Regex("[^A-Z0-9_]"), "_").ifBlank { "RELATED_TO" }
                    appendLine("  MERGE (e$sIdx)-[:$edgeLabel]->(e$tIdx)")
                }
            }
            appendLine("  RETURN count(*)")
            appendLine("\$\$) AS (graph_entities agtype);")
            appendLine()
        }

        // Optional PostGIS Analytical Views
        if (config.includeAnalyticsViews && anySpatial) {
            val spatialEntity = activeEntities.first { it.geometryKind != PgGeometryKind.NONE }
            val sTbl = sanitizeIdentifier(spatialEntity.tableName, "spatial_sites")
            val gCol = sanitizeIdentifier(spatialEntity.geometryColumnName, "geom")
            appendLine("-- PostGIS Spatial Analytics Summary View")
            appendLine("CREATE OR REPLACE VIEW $schema.v_${sTbl}_spatial_summary AS")
            appendLine("SELECT")
            appendLine("    COUNT(*) AS feature_count,")
            appendLine("    ST_AsGeoJSON(ST_Extent($gCol::geometry)) AS bounding_box_geojson,")
            appendLine("    ST_AsText(ST_Centroid(ST_Collect($gCol::geometry))) AS centroid_wkt")
            appendLine("FROM $schema.$sTbl")
            appendLine("WHERE $gCol IS NOT NULL;")
            appendLine()
        }

        appendLine("COMMIT;")
    }

    private fun extractMarkdownTableEntities(corpus: String): List<PgMappedEntity> {
        val lines = corpus.replace("\r\n", "\n").lines()
        val out = mutableListOf<PgMappedEntity>()
        var lastHeading = "Extracted Dataset"
        var i = 0
        while (i < lines.size - 2) {
            val line = lines[i].trim()
            if (line.startsWith("#")) {
                lastHeading = line.trimStart('#').trim().take(36).ifBlank { lastHeading }
            }
            if (line.startsWith("|") && lines[i + 1].trim().matches(Regex("""^\|?[\s:|-]+\|?$"""))) {
                val headers = line.trim('|').split('|').map { it.trim() }.filter { it.isNotBlank() }
                val firstRow = lines[i + 2].trim().trim('|').split('|').map { it.trim() }
                if (headers.size >= 2) {
                    val tbl = sanitizeIdentifier(lastHeading, "dataset_${out.size + 1}")
                    val isSpatial = looksSpatial(lastHeading + " " + headers.joinToString(" "))
                    val cols = headers.take(6).mapIndexed { idx, h ->
                        val sample = firstRow.getOrElse(idx) { "" }
                        PgMappedColumn(
                            id = "tbl_${out.size}_$idx",
                            name = sanitizeIdentifier(h, "col_$idx"),
                            dataType = inferScalarType(sample),
                            sampleValue = sample.take(64)
                        )
                    }
                    out.add(
                        PgMappedEntity(
                            id = "ent_md_$tbl",
                            entityName = lastHeading,
                            tableName = tbl,
                            category = if (isSpatial) PgEntityCategory.SPATIAL_SITE else PgEntityCategory.OBSERVATION,
                            description = "Mapped from Markdown table (${headers.size} columns)",
                            geometryKind = if (isSpatial) PgGeometryKind.POINT else PgGeometryKind.NONE,
                            columns = cols,
                            sampleWkt = if (isSpatial) "POINT(0 0)" else "",
                            sourceLabel = "Markdown Table"
                        )
                    )
                }
                i += 3
                continue
            }
            i++
        }
        return out
    }

    private fun inferScalarType(sample: String): PgColumnDataType {
        val s = sample.trim()
        return when {
            s.equals("true", true) || s.equals("false", true) -> PgColumnDataType.BOOLEAN
            s.matches(Regex("""^-?\d+$""")) -> PgColumnDataType.INTEGER
            s.matches(Regex("""^-?\d+\.\d+$""")) -> PgColumnDataType.DOUBLE
            s.startsWith("{") && s.endsWith("}") -> PgColumnDataType.JSONB
            else -> PgColumnDataType.TEXT
        }
    }

    private fun looksSpatial(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return spatialKeywords.any { lower.contains(it) }
    }

    private fun shapeToWkt(shape: GeoShape): String = when (shape) {
        is GeoShape.Point -> String.format(Locale.US, "POINT(%.5f %.5f)", shape.coord.lon, shape.coord.lat)
        is GeoShape.LineString -> "LINESTRING(${shape.coords.joinToString(", ") { String.format(Locale.US, "%.5f %.5f", it.lon, it.lat) }})"
        is GeoShape.Polygon -> "POLYGON(${shape.rings.joinToString(", ") { r -> "(${r.joinToString(", ") { String.format(Locale.US, "%.5f %.5f", it.lon, it.lat) }})" }})"
    }

    private fun formatSeedLiteral(col: PgMappedColumn): String {
        val v = col.sampleValue.trim()
        return when (col.dataType) {
            PgColumnDataType.INTEGER, PgColumnDataType.BIGINT -> v.toLongOrNull()?.toString() ?: "1"
            PgColumnDataType.DOUBLE, PgColumnDataType.NUMERIC -> v.toDoubleOrNull()?.toString() ?: "0.0"
            PgColumnDataType.BOOLEAN -> if (v.equals("false", true)) "FALSE" else "TRUE"
            PgColumnDataType.JSONB -> "'${escapeSqlLiteral(if (v.startsWith("{")) v else "{}")}'::jsonb"
            PgColumnDataType.TEXT_ARRAY -> if (v.startsWith("ARRAY[")) v else "ARRAY['${escapeSqlLiteral(v.ifBlank { "research" })}']"
            PgColumnDataType.UUID -> "gen_random_uuid()"
            PgColumnDataType.TIMESTAMPTZ -> "NOW()"
            PgColumnDataType.TEXT, PgColumnDataType.VARCHAR -> "'${escapeSqlLiteral(v.ifBlank { col.name })}'"
        }
    }
}
