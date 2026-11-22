package com.example.ui.components

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class SpecializedFormatKind(
    val displayName: String,
    val badgeLabel: String,
    val defaultExtension: String,
    val defaultMimeType: String,
    val isGraphLike: Boolean = false
) {
    LATEX(
        displayName = "LaTeX / Math",
        badgeLabel = "LaTeX",
        defaultExtension = "tex",
        defaultMimeType = "application/x-tex"
    ),
    CYPHER_AGE(
        displayName = "Cypher / Apache AGE",
        badgeLabel = "Cypher · AGE",
        defaultExtension = "cypher",
        defaultMimeType = "text/plain",
        isGraphLike = true
    ),
    GRAPH_DOT(
        displayName = "GraphViz DOT",
        badgeLabel = "GraphViz DOT",
        defaultExtension = "dot",
        defaultMimeType = "text/vnd.graphviz",
        isGraphLike = true
    ),
    GRAPH_EXCHANGE(
        displayName = "Graph Data (D3 / NetworkX / GraphML)",
        badgeLabel = "D3 / NetworkX",
        defaultExtension = "json",
        defaultMimeType = "application/json",
        isGraphLike = true
    ),
    GEOSPATIAL(
        displayName = "PostGIS Geospatial (GeoJSON / WKT)",
        badgeLabel = "PostGIS Geo",
        defaultExtension = "geojson",
        defaultMimeType = "application/geo+json"
    ),
    POSTGRES_SQL(
        displayName = "PostgreSQL / PostGIS / AGE Schema",
        badgeLabel = "PostgreSQL",
        defaultExtension = "sql",
        defaultMimeType = "application/sql",
        isGraphLike = true
    )
}

data class SpecializedArtifactSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: SpecializedFormatKind,
    val sourceLanguage: String,
    val code: String,
    val suggestedFileName: String
)

data class UnifiedGraphNode(
    val id: String,
    val label: String,
    val group: String = "",
    val properties: Map<String, String> = emptyMap(),
    val details: List<String> = emptyList()
)

data class UnifiedGraphEdge(
    val source: String,
    val target: String,
    val label: String = "",
    val properties: Map<String, String> = emptyMap(),
    val directed: Boolean = true
)

data class UnifiedGraphModel(
    val title: String,
    val graphName: String = "vortex_graph",
    val nodes: List<UnifiedGraphNode>,
    val edges: List<UnifiedGraphEdge>,
    val sourceDialect: String = ""
)

data class GeoCoord(
    val lon: Double,
    val lat: Double
)

sealed interface GeoShape {
    data class Point(val coord: GeoCoord) : GeoShape
    data class LineString(val coords: List<GeoCoord>) : GeoShape
    data class Polygon(val rings: List<List<GeoCoord>>) : GeoShape
}

data class UnifiedGeoFeature(
    val id: String,
    val label: String,
    val shapes: List<GeoShape>,
    val properties: Map<String, String> = emptyMap()
)

data class UnifiedGeoModel(
    val srid: Int = 4326,
    val features: List<UnifiedGeoFeature>,
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
    val sourceDialect: String = "GeoJSON"
)

data class SpecializedExportOption(
    val id: String,
    val label: String,
    val description: String,
    val extension: String,
    val mimeType: String,
    val isImage: Boolean = false,
    val imageFormat: MermaidImageExportFormat? = null,
    val transform: ((source: String, title: String) -> String)? = null
)

object SpecializedFormatParser {

    private val latexCommandRegex = Regex("""\\(?:begin|frac|sum|int|sqrt|alpha|beta|gamma|theta|lambda|sigma|nabla|partial|mathbb|mathcal|text|section|documentclass|usepackage)\b""")
    private val cypherPatternRegex = Regex("""(?i)\b(?:MATCH|CREATE|MERGE|RETURN|WITH|OPTIONAL\s+MATCH)\b[\s\S]{0,160}\([A-Za-z0-9_:`\s{},'"=.-]*\)""")
    private val ageCypherCallRegex = Regex("""(?is)\b(?:ag_catalog\.)?cypher\s*\(\s*'([^']+)'\s*,\s*\$\$(\s*[\s\S]*?)\$\$\s*\)""")
    private val createTableRegex = Regex("""(?is)CREATE\s+(?:UNLOGGED\s+|TEMP(?:ORARY)?\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z0-9_."`]+)\s*\(([\s\S]*?)\)\s*;""")
    private val alterFkRegex = Regex("""(?is)ALTER\s+TABLE\s+(?:ONLY\s+)?([A-Za-z0-9_."`]+)\s+ADD\s+(?:CONSTRAINT\s+[A-Za-z0-9_."`]+\s+)?FOREIGN\s+KEY\s*\(([^)]+)\)\s+REFERENCES\s+([A-Za-z0-9_."`]+)\s*\(([^)]+)\)""")
    private val wktPrefixRegex = Regex("""(?i)^\s*(?:SRID\s*=\s*\d+\s*;\s*)?(?:MULTI)?(?:POINT|LINESTRING|POLYGON|GEOMETRYCOLLECTION)\s*(?:Z|M|ZM)?\s*\(""")

    fun detectKind(extOrLang: String, content: String): SpecializedFormatKind? {
        val norm = extOrLang.trim().lowercase().removePrefix(".")
        val trimmed = content.trim()
        if (trimmed.isBlank()) return null

        return when (norm) {
            "tex", "latex", "math", "katex" -> SpecializedFormatKind.LATEX
            "cypher", "cql", "age" -> SpecializedFormatKind.CYPHER_AGE
            "dot", "gv", "graphviz" -> SpecializedFormatKind.GRAPH_DOT
            "graphml", "gexf", "d3", "networkx", "d3json" -> SpecializedFormatKind.GRAPH_EXCHANGE
            "geojson", "topojson", "wkt", "ewkt", "postgis" -> SpecializedFormatKind.GEOSPATIAL
            "sql", "pgsql", "psql", "postgres", "postgresql", "plpgsql" -> {
                if (looksLikePostgresSchemaOrAgeOrPostGis(trimmed)) {
                    SpecializedFormatKind.POSTGRES_SQL
                } else {
                    null
                }
            }
            "json" -> when {
                looksLikeGeoJson(trimmed) -> SpecializedFormatKind.GEOSPATIAL
                looksLikeD3OrNetworkXJson(trimmed) -> SpecializedFormatKind.GRAPH_EXCHANGE
                else -> null
            }
            "xml" -> when {
                trimmed.contains("<graphml", ignoreCase = true) ||
                    trimmed.contains("<gexf", ignoreCase = true) -> SpecializedFormatKind.GRAPH_EXCHANGE
                else -> null
            }
            else -> detectFromContentOnly(trimmed)
        }
    }

    private fun detectFromContentOnly(trimmed: String): SpecializedFormatKind? {
        return when {
            looksLikeGeoJson(trimmed) || wktPrefixRegex.containsMatchIn(trimmed) -> SpecializedFormatKind.GEOSPATIAL
            looksLikeD3OrNetworkXJson(trimmed) -> SpecializedFormatKind.GRAPH_EXCHANGE
            trimmed.contains("<graphml", ignoreCase = true) || trimmed.contains("<gexf", ignoreCase = true) ->
                SpecializedFormatKind.GRAPH_EXCHANGE
            ageCypherCallRegex.containsMatchIn(trimmed) -> SpecializedFormatKind.CYPHER_AGE
            cypherPatternRegex.containsMatchIn(trimmed) && (trimmed.contains("->") || trimmed.contains("<-")) ->
                SpecializedFormatKind.CYPHER_AGE
            Regex("""(?i)^\s*(?:strict\s+)?(?:di)?graph\b""").containsMatchIn(trimmed) ->
                SpecializedFormatKind.GRAPH_DOT
            looksLikePostgresSchemaOrAgeOrPostGis(trimmed) -> SpecializedFormatKind.POSTGRES_SQL
            trimmed.contains("\\documentclass") || (trimmed.contains("$$") && latexCommandRegex.containsMatchIn(trimmed)) ->
                SpecializedFormatKind.LATEX
            else -> null
        }
    }

    fun looksLikeGeoJson(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("{")) return false
        return runCatching {
            val obj = JSONObject(t)
            val type = obj.optString("type")
            type in setOf(
                "FeatureCollection", "Feature", "Point", "MultiPoint",
                "LineString", "MultiLineString", "Polygon", "MultiPolygon", "GeometryCollection"
            )
        }.getOrDefault(false)
    }

    fun looksLikeD3OrNetworkXJson(text: String): Boolean {
        val t = text.trim()
        if (!t.startsWith("{")) return false
        return runCatching {
            val obj = JSONObject(t)
            val hasNodes = obj.optJSONArray("nodes") != null
            val hasEdges = obj.optJSONArray("links") != null || obj.optJSONArray("edges") != null
            hasNodes && hasEdges
        }.getOrDefault(false)
    }

    fun looksLikePostgresSchemaOrAgeOrPostGis(text: String): Boolean {
        return createTableRegex.containsMatchIn(text) ||
            ageCypherCallRegex.containsMatchIn(text) ||
            text.contains("create_graph(", ignoreCase = true) ||
            text.contains("ST_GeomFrom", ignoreCase = true) ||
            text.contains("GEOMETRY(", ignoreCase = true) ||
            text.contains("GEOGRAPHY(", ignoreCase = true)
    }

    fun parseToUnifiedGraph(kind: SpecializedFormatKind, source: String, title: String): UnifiedGraphModel {
        return when (kind) {
            SpecializedFormatKind.CYPHER_AGE -> parseCypherOrAge(source, title)
            SpecializedFormatKind.GRAPH_DOT -> parseDot(source, title)
            SpecializedFormatKind.GRAPH_EXCHANGE -> {
                val trimmed = source.trim()
                if (trimmed.startsWith("<")) parseGraphXml(trimmed, title)
                else parseD3OrNetworkXJson(trimmed, title)
            }
            SpecializedFormatKind.POSTGRES_SQL -> parsePostgresSchema(source, title)
            else -> UnifiedGraphModel(title = title, nodes = emptyList(), edges = emptyList())
        }
    }

    fun parseCypherOrAge(source: String, title: String = "Cypher Graph"): UnifiedGraphModel {
        val ageMatches = ageCypherCallRegex.findAll(source).toList()
        val graphName = ageMatches.firstOrNull()?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "vortex_graph" }
            ?: Regex("""(?i)create_graph\s*\(\s*'([^']+)'\s*\)""").find(source)?.groupValues?.getOrNull(1)
            ?: "vortex_graph"

        val cypherBody = if (ageMatches.isNotEmpty()) {
            ageMatches.joinToString("\n") { it.groupValues[2] }
        } else {
            source
        }

        val cleaned = cypherBody.lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("--") }
            .joinToString("\n")

        val nodeMap = LinkedHashMap<String, UnifiedGraphNode>()
        val edges = mutableListOf<UnifiedGraphEdge>()
        var anonCounter = 1

        fun upsertNode(rawInside: String): String {
            val inside = rawInside.trim()
            val propStart = inside.indexOf('{')
            val propEnd = inside.lastIndexOf('}')
            val props = if (propStart >= 0 && propEnd > propStart) {
                parseInlinePropertyMap(inside.substring(propStart + 1, propEnd))
            } else {
                emptyMap()
            }
            val head = (if (propStart >= 0) inside.substring(0, propStart) else inside).trim()
            val parts = head.split(':').map { it.trim().trim('`') }.filter { it.isNotEmpty() }
            val alias = when {
                head.startsWith(":") -> "node_${anonCounter++}"
                parts.isNotEmpty() -> parts.first()
                else -> "node_${anonCounter++}"
            }
            val labels = when {
                head.startsWith(":") -> parts
                parts.size > 1 -> parts.drop(1)
                else -> emptyList()
            }
            val group = labels.joinToString(":")
            val displayLabel = props["name"] ?: props["title"] ?: props["label"] ?: props["id"] ?: alias
            val existing = nodeMap[alias]
            val mergedProps = (existing?.properties.orEmpty()) + props
            val mergedGroup = group.ifBlank { existing?.group.orEmpty() }
            val finalLabel = if (displayLabel != alias) displayLabel else (existing?.label ?: alias)
            val details = buildList {
                if (mergedGroup.isNotBlank()) add(":$mergedGroup")
                mergedProps.entries.take(4).forEach { (k, v) -> add("$k: $v") }
            }
            nodeMap[alias] = UnifiedGraphNode(
                id = alias,
                label = finalLabel,
                group = mergedGroup,
                properties = mergedProps,
                details = details
            )
            return alias
        }

        // Parse edge chains: (a)-[r:REL {props}]->(b) or (a)<-[r:REL]-(b) or (a)-[r:REL]-(b)
        val edgeChainRegex = Regex("""\(([^()]*)\)\s*(<?)-\s*(?:\[([^\]]*)\])?\s*-(>?)\s*\(([^()]*)\)""")
        var searchIndex = 0
        while (searchIndex < cleaned.length) {
            val match = edgeChainRegex.find(cleaned, searchIndex) ?: break
            val leftId = upsertNode(match.groupValues[1])
            val leftArrow = match.groupValues[2] == "<"
            val relSpec = match.groupValues[3].trim()
            val rightArrow = match.groupValues[4] == ">"
            val rightId = upsertNode(match.groupValues[5])

            val relPropStart = relSpec.indexOf('{')
            val relPropEnd = relSpec.lastIndexOf('}')
            val relProps = if (relPropStart >= 0 && relPropEnd > relPropStart) {
                parseInlinePropertyMap(relSpec.substring(relPropStart + 1, relPropEnd))
            } else {
                emptyMap()
            }
            val relHead = (if (relPropStart >= 0) relSpec.substring(0, relPropStart) else relSpec).trim()
            val relLabel = relHead.substringAfter(':', relHead)
                .trim()
                .trim('`')
                .ifBlank { relProps["type"] ?: "LINKS" }

            val directed = leftArrow xor rightArrow
            val (src, dst) = if (leftArrow && !rightArrow) rightId to leftId else leftId to rightId
            edges.add(
                UnifiedGraphEdge(
                    source = src,
                    target = dst,
                    label = relLabel,
                    properties = relProps,
                    directed = directed
                )
            )
            // Advance just before the right node so chained patterns (a)->(b)->(c) parse completely
            val rightNodeStart = cleaned.lastIndexOf('(', match.range.last)
            searchIndex = if (rightNodeStart > match.range.first) rightNodeStart else match.range.last + 1
        }

        // Also capture standalone declared nodes (e.g., CREATE (n:Vertex {name: 'A'}))
        val standaloneNodeRegex = Regex("""\(([A-Za-z_][A-Za-z0-9_]*\s*(?::[A-Za-z0-9_`]+)+(?:\s*\{[^}]*\})?)\)""")
        standaloneNodeRegex.findAll(cleaned).forEach { m ->
            upsertNode(m.groupValues[1])
        }

        return UnifiedGraphModel(
            title = title,
            graphName = graphName,
            nodes = nodeMap.values.toList(),
            edges = edges.distinctBy { "${it.source}|${it.target}|${it.label}" },
            sourceDialect = if (ageMatches.isNotEmpty()) "Apache AGE (PostgreSQL)" else "openCypher"
        )
    }

    fun parseDot(source: String, title: String = "GraphViz DOT"): UnifiedGraphModel {
        val cleaned = source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lines()
            .filterNot { it.trim().startsWith("//") || it.trim().startsWith("#") }
            .joinToString("\n")

        val isDirected = !Regex("""(?i)^\s*(?:strict\s+)?graph\b""").containsMatchIn(cleaned)
        val graphName = Regex("""(?i)\b(?:di)?graph\s+([A-Za-z0-9_"]+)""").find(cleaned)
            ?.groupValues?.getOrNull(1)?.trim('"') ?: "dot_graph"

        val nodeMap = LinkedHashMap<String, UnifiedGraphNode>()
        val edges = mutableListOf<UnifiedGraphEdge>()

        fun ensureNode(rawId: String, attrs: Map<String, String> = emptyMap()): String {
            val id = rawId.trim().trim('"')
            val existing = nodeMap[id]
            val label = attrs["label"]?.replace("\\n", " ") ?: existing?.label ?: id
            val group = attrs["shape"] ?: attrs["cluster"] ?: existing?.group.orEmpty()
            val mergedProps = (existing?.properties.orEmpty()) + attrs
            nodeMap[id] = UnifiedGraphNode(
                id = id,
                label = label,
                group = group,
                properties = mergedProps,
                details = mergedProps.entries.filter { it.key != "label" }.take(3).map { "${it.key}: ${it.value}" }
            )
            return id
        }

        val edgeRegex = Regex("""("[^"]+"|[A-Za-z0-9_.:-]+)\s*(->|--)\s*("[^"]+"|[A-Za-z0-9_.:-]+)(?:\s*\[([^\]]*)\])?""")
        edgeRegex.findAll(cleaned).forEach { m ->
            val src = ensureNode(m.groupValues[1])
            val op = m.groupValues[2]
            val dst = ensureNode(m.groupValues[3])
            val attrs = parseDotAttrs(m.groupValues[4])
            edges.add(
                UnifiedGraphEdge(
                    source = src,
                    target = dst,
                    label = attrs["label"].orEmpty(),
                    properties = attrs,
                    directed = op == "->" || isDirected
                )
            )
        }

        val nodeDeclRegex = Regex("""(?m)^\s*("[^"]+"|[A-Za-z_][A-Za-z0-9_.-]*)\s*\[([^\]]+)\]\s*;?\s*$""")
        nodeDeclRegex.findAll(cleaned).forEach { m ->
            val id = m.groupValues[1].trim().trim('"')
            if (id.lowercase() !in setOf("node", "edge", "graph")) {
                ensureNode(id, parseDotAttrs(m.groupValues[2]))
            }
        }

        return UnifiedGraphModel(
            title = title,
            graphName = graphName,
            nodes = nodeMap.values.toList(),
            edges = edges,
            sourceDialect = if (isDirected) "GraphViz Digraph" else "GraphViz Graph"
        )
    }

    fun parseGraphXml(source: String, title: String = "GraphML / GEXF"): UnifiedGraphModel {
        val isGexf = source.contains("<gexf", ignoreCase = true)
        val nodeMap = LinkedHashMap<String, UnifiedGraphNode>()
        val edges = mutableListOf<UnifiedGraphEdge>()

        val nodeBlockRegex = Regex("""(?is)<node\b([^>]*?)(?:/>|>([\s\S]*?)</node>)""")
        val dataTagRegex = Regex("""(?is)<data\b[^>]*key\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)</data>""")
        val attValRegex = Regex("""(?is)<attvalue\b[^>]*for\s*=\s*["']([^"']+)["'][^>]*value\s*=\s*["']([^"']*)["'][^>]*/>""")

        nodeBlockRegex.findAll(source).forEach { m ->
            val attrs = parseXmlAttrs(m.groupValues[1])
            val body = m.groupValues[2]
            val id = attrs["id"]?.ifBlank { null } ?: return@forEach
            val props = LinkedHashMap<String, String>()
            dataTagRegex.findAll(body).forEach { d ->
                props[d.groupValues[1]] = unescapeXml(d.groupValues[2].trim())
            }
            attValRegex.findAll(body).forEach { a ->
                props[a.groupValues[1]] = unescapeXml(a.groupValues[2].trim())
            }
            val label = attrs["label"]?.ifBlank { null }
                ?: props["label"] ?: props["name"] ?: props["title"] ?: id
            val group = props["type"] ?: props["group"] ?: props["category"] ?: ""
            nodeMap[id] = UnifiedGraphNode(
                id = id,
                label = unescapeXml(label),
                group = group,
                properties = props,
                details = props.entries.take(3).map { "${it.key}: ${it.value}" }
            )
        }

        val edgeBlockRegex = Regex("""(?is)<edge\b([^>]*?)(?:/>|>([\s\S]*?)</edge>)""")
        edgeBlockRegex.findAll(source).forEach { m ->
            val attrs = parseXmlAttrs(m.groupValues[1])
            val body = m.groupValues[2]
            val src = attrs["source"] ?: return@forEach
            val dst = attrs["target"] ?: return@forEach
            if (!nodeMap.containsKey(src)) nodeMap[src] = UnifiedGraphNode(src, src)
            if (!nodeMap.containsKey(dst)) nodeMap[dst] = UnifiedGraphNode(dst, dst)
            val props = LinkedHashMap<String, String>()
            dataTagRegex.findAll(body).forEach { d ->
                props[d.groupValues[1]] = unescapeXml(d.groupValues[2].trim())
            }
            val label = attrs["label"]?.ifBlank { null }
                ?: props["label"] ?: props["relationship"] ?: props["weight"] ?: ""
            edges.add(
                UnifiedGraphEdge(
                    source = src,
                    target = dst,
                    label = unescapeXml(label),
                    properties = props,
                    directed = attrs["directed"]?.lowercase() != "false"
                )
            )
        }

        return UnifiedGraphModel(
            title = title,
            graphName = if (isGexf) "gexf_graph" else "graphml_graph",
            nodes = nodeMap.values.toList(),
            edges = edges,
            sourceDialect = if (isGexf) "GEXF XML" else "GraphML XML (NetworkX)"
        )
    }

    fun parseD3OrNetworkXJson(source: String, title: String = "D3 / NetworkX Graph"): UnifiedGraphModel {
        val obj = runCatching { JSONObject(source.trim()) }.getOrNull()
            ?: return UnifiedGraphModel(title = title, nodes = emptyList(), edges = emptyList(), sourceDialect = "Graph JSON")

        val nodesArr = obj.optJSONArray("nodes") ?: JSONArray()
        val edgesArr = obj.optJSONArray("links") ?: obj.optJSONArray("edges") ?: JSONArray()
        val directed = obj.optBoolean("directed", true)

        val orderedIds = mutableListOf<String>()
        val nodeMap = LinkedHashMap<String, UnifiedGraphNode>()

        for (i in 0 until nodesArr.length()) {
            val raw = nodesArr.opt(i)
            if (raw is JSONObject) {
                val id = raw.opt("id")?.toString()?.ifBlank { "n$i" } ?: "n$i"
                val label = raw.optString("label")
                    .ifBlank { raw.optString("name") }
                    .ifBlank { raw.optString("title") }
                    .ifBlank { id }
                val group = raw.opt("group")?.toString()
                    ?: raw.opt("type")?.toString()
                    ?: raw.opt("category")?.toString()
                    ?: ""
                val props = LinkedHashMap<String, String>()
                raw.keys().forEach { k ->
                    if (k !in setOf("id", "label", "name")) {
                        val v = raw.opt(k)?.toString().orEmpty()
                        if (v.isNotBlank() && v.length <= 64) props[k] = v
                    }
                }
                orderedIds.add(id)
                nodeMap[id] = UnifiedGraphNode(
                    id = id,
                    label = label,
                    group = group,
                    properties = props,
                    details = props.entries.take(3).map { "${it.key}: ${it.value}" }
                )
            } else if (raw != null) {
                val id = raw.toString()
                orderedIds.add(id)
                nodeMap[id] = UnifiedGraphNode(id = id, label = id)
            }
        }

        fun resolveEndpoint(raw: Any?): String? {
            if (raw == null) return null
            if (raw is Number) {
                val idx = raw.toInt()
                return orderedIds.getOrNull(idx) ?: idx.toString()
            }
            val str = raw.toString()
            if (nodeMap.containsKey(str)) return str
            val idx = str.toIntOrNull()
            if (idx != null && idx in orderedIds.indices) return orderedIds[idx]
            nodeMap[str] = UnifiedGraphNode(str, str)
            return str
        }

        val edges = mutableListOf<UnifiedGraphEdge>()
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.optJSONObject(i) ?: continue
            val src = resolveEndpoint(e.opt("source") ?: e.opt("from")) ?: continue
            val dst = resolveEndpoint(e.opt("target") ?: e.opt("to")) ?: continue
            val label = e.optString("label")
                .ifBlank { e.optString("relationship") }
                .ifBlank { e.optString("type") }
                .ifBlank { e.opt("weight")?.toString().orEmpty() }
            edges.add(UnifiedGraphEdge(source = src, target = dst, label = label, directed = directed))
        }

        val dialect = if (obj.has("multigraph") || obj.has("graph")) "NetworkX node-link JSON" else "D3.js Force Graph JSON"
        return UnifiedGraphModel(
            title = title,
            graphName = obj.optJSONObject("graph")?.optString("name")?.ifBlank { null } ?: "d3_graph",
            nodes = nodeMap.values.toList(),
            edges = edges,
            sourceDialect = dialect
        )
    }

    fun parsePostgresSchema(source: String, title: String = "PostgreSQL Schema"): UnifiedGraphModel {
        val nodeMap = LinkedHashMap<String, UnifiedGraphNode>()
        val edges = mutableListOf<UnifiedGraphEdge>()

        // 1. Parse CREATE TABLE blocks including PK, FK REFERENCES, and PostGIS geometry/geography columns
        createTableRegex.findAll(source).forEach { match ->
            val tableName = cleanSqlIdent(match.groupValues[1])
            val body = match.groupValues[2]
            val columns = mutableListOf<String>()
            var hasPostGis = false

            splitTopLevelCommas(body).forEach { rawLine ->
                val line = rawLine.trim().replace(Regex("""--[^\n]*"""), "").trim()
                if (line.isBlank()) return@forEach

                val fkMatch = Regex("""(?i)FOREIGN\s+KEY\s*\(([^)]+)\)\s+REFERENCES\s+([A-Za-z0-9_."`]+)""").find(line)
                if (fkMatch != null) {
                    val col = cleanSqlIdent(fkMatch.groupValues[1])
                    val target = cleanSqlIdent(fkMatch.groupValues[2])
                    edges.add(UnifiedGraphEdge(source = tableName, target = target, label = "FK $col"))
                    return@forEach
                }

                if (Regex("""(?i)^(?:CONSTRAINT|PRIMARY\s+KEY|UNIQUE|CHECK|EXCLUDE)\b""").containsMatchIn(line)) {
                    return@forEach
                }

                val tokens = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
                if (tokens.size >= 2) {
                    val colName = cleanSqlIdent(tokens[0])
                    val colType = tokens[1].uppercase()
                    val isPk = line.contains("PRIMARY KEY", ignoreCase = true)
                    val isGeo = colType.startsWith("GEOMETRY") || colType.startsWith("GEOGRAPHY")
                    if (isGeo) hasPostGis = true
                    val tag = buildString {
                        if (isPk) append("PK ")
                        if (isGeo) append("GIS ")
                        append("$colName: $colType")
                    }
                    columns.add(tag.take(36))

                    val inlineRef = Regex("""(?i)\bREFERENCES\s+([A-Za-z0-9_."`]+)""").find(line)
                    if (inlineRef != null) {
                        val target = cleanSqlIdent(inlineRef.groupValues[1])
                        edges.add(UnifiedGraphEdge(source = tableName, target = target, label = colName))
                    }
                }
            }

            val group = if (hasPostGis) "PostGIS Table" else "PG Table"
            nodeMap[tableName] = UnifiedGraphNode(
                id = tableName,
                label = tableName,
                group = group,
                properties = mapOf("columns" to columns.size.toString()),
                details = columns.take(6)
            )
        }

        // 2. Parse ALTER TABLE ... FOREIGN KEY constraints
        alterFkRegex.findAll(source).forEach { m ->
            val src = cleanSqlIdent(m.groupValues[1])
            val col = cleanSqlIdent(m.groupValues[2])
            val dst = cleanSqlIdent(m.groupValues[3])
            if (!nodeMap.containsKey(src)) nodeMap[src] = UnifiedGraphNode(src, src, group = "PG Table")
            if (!nodeMap.containsKey(dst)) nodeMap[dst] = UnifiedGraphNode(dst, dst, group = "PG Table")
            edges.add(UnifiedGraphEdge(source = src, target = dst, label = "FK $col"))
        }

        // 3. Also merge any embedded Apache AGE cypher(...) graph statements
        if (ageCypherCallRegex.containsMatchIn(source)) {
            val ageGraph = parseCypherOrAge(source, title)
            ageGraph.nodes.forEach { n ->
                val prefixedId = "age:${n.id}"
                nodeMap[prefixedId] = n.copy(
                    id = prefixedId,
                    group = "AGE ${n.group}".trim()
                )
            }
            ageGraph.edges.forEach { e ->
                edges.add(e.copy(source = "age:${e.source}", target = "age:${e.target}"))
            }
        }

        // Ensure edge targets exist as nodes
        edges.forEach { e ->
            if (!nodeMap.containsKey(e.source)) nodeMap[e.source] = UnifiedGraphNode(e.source, e.source, group = "Table")
            if (!nodeMap.containsKey(e.target)) nodeMap[e.target] = UnifiedGraphNode(e.target, e.target, group = "Table")
        }

        val hasAge = ageCypherCallRegex.containsMatchIn(source) || source.contains("create_graph(", ignoreCase = true)
        val hasPostGis = source.contains("GEOMETRY", ignoreCase = true) || source.contains("GEOGRAPHY", ignoreCase = true) ||
            source.contains("ST_", ignoreCase = true)
        val dialect = buildList {
            add("PostgreSQL")
            if (hasPostGis) add("PostGIS")
            if (hasAge) add("Apache AGE")
        }.joinToString(" + ")

        return UnifiedGraphModel(
            title = title,
            graphName = "postgres_schema",
            nodes = nodeMap.values.toList(),
            edges = edges.distinctBy { "${it.source}|${it.target}|${it.label}" },
            sourceDialect = dialect
        )
    }

    fun parseGeospatial(source: String): UnifiedGeoModel {
        val trimmed = source.trim()
        return if (trimmed.startsWith("{")) {
            parseGeoJson(trimmed)
        } else {
            parseWkt(trimmed)
        }
    }

    private fun parseGeoJson(source: String): UnifiedGeoModel {
        val features = mutableListOf<UnifiedGeoFeature>()
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY

        fun track(c: GeoCoord) {
            minLon = min(minLon, c.lon)
            maxLon = max(maxLon, c.lon)
            minLat = min(minLat, c.lat)
            maxLat = max(maxLat, c.lat)
        }

        fun parseCoord(arr: JSONArray?): GeoCoord? {
            if (arr == null || arr.length() < 2) return null
            val lon = arr.optDouble(0, Double.NaN)
            val lat = arr.optDouble(1, Double.NaN)
            if (lon.isNaN() || lat.isNaN()) return null
            return GeoCoord(lon, lat).also(::track)
        }

        fun parseCoordList(arr: JSONArray?): List<GeoCoord> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { parseCoord(arr.optJSONArray(it)) }
        }

        fun parseGeometry(geom: JSONObject?): List<GeoShape> {
            if (geom == null) return emptyList()
            val coords = geom.optJSONArray("coordinates")
            return when (geom.optString("type")) {
                "Point" -> listOfNotNull(parseCoord(coords)?.let { GeoShape.Point(it) })
                "MultiPoint" -> parseCoordList(coords).map { GeoShape.Point(it) }
                "LineString" -> {
                    val pts = parseCoordList(coords)
                    if (pts.size >= 2) listOf(GeoShape.LineString(pts)) else emptyList()
                }
                "MultiLineString" -> {
                    if (coords == null) emptyList() else (0 until coords.length()).mapNotNull { i ->
                        val pts = parseCoordList(coords.optJSONArray(i))
                        if (pts.size >= 2) GeoShape.LineString(pts) else null
                    }
                }
                "Polygon" -> {
                    if (coords == null) emptyList() else {
                        val rings = (0 until coords.length()).map { parseCoordList(coords.optJSONArray(it)) }
                            .filter { it.size >= 3 }
                        if (rings.isNotEmpty()) listOf(GeoShape.Polygon(rings)) else emptyList()
                    }
                }
                "MultiPolygon" -> {
                    if (coords == null) emptyList() else (0 until coords.length()).mapNotNull { p ->
                        val polyArr = coords.optJSONArray(p) ?: return@mapNotNull null
                        val rings = (0 until polyArr.length()).map { parseCoordList(polyArr.optJSONArray(it)) }
                            .filter { it.size >= 3 }
                        if (rings.isNotEmpty()) GeoShape.Polygon(rings) else null
                    }
                }
                "GeometryCollection" -> {
                    val geoms = geom.optJSONArray("geometries") ?: return emptyList()
                    (0 until geoms.length()).flatMap { parseGeometry(geoms.optJSONObject(it)) }
                }
                else -> emptyList()
            }
        }

        runCatching {
            val root = JSONObject(source)
            when (root.optString("type")) {
                "FeatureCollection" -> {
                    val arr = root.optJSONArray("features") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val f = arr.optJSONObject(i) ?: continue
                        val shapes = parseGeometry(f.optJSONObject("geometry"))
                        if (shapes.isNotEmpty()) {
                            val propsObj = f.optJSONObject("properties")
                            val props = LinkedHashMap<String, String>()
                            propsObj?.keys()?.forEach { k ->
                                val v = propsObj.opt(k)?.toString().orEmpty()
                                if (v.isNotBlank() && v.length <= 64) props[k] = v
                            }
                            val label = props["name"] ?: props["title"] ?: props["label"]
                                ?: f.opt("id")?.toString() ?: "Feature ${i + 1}"
                            features.add(UnifiedGeoFeature("f_$i", label, shapes, props))
                        }
                    }
                }
                "Feature" -> {
                    val shapes = parseGeometry(root.optJSONObject("geometry"))
                    val propsObj = root.optJSONObject("properties")
                    val props = LinkedHashMap<String, String>()
                    propsObj?.keys()?.forEach { k -> props[k] = propsObj.opt(k)?.toString().orEmpty() }
                    val label = props["name"] ?: props["title"] ?: "Feature 1"
                    if (shapes.isNotEmpty()) features.add(UnifiedGeoFeature("f_0", label, shapes, props))
                }
                else -> {
                    val shapes = parseGeometry(root)
                    if (shapes.isNotEmpty()) features.add(UnifiedGeoFeature("g_0", root.optString("type", "Geometry"), shapes))
                }
            }
        }

        if (minLon.isInfinite()) {
            minLon = -180.0; maxLon = 180.0; minLat = -90.0; maxLat = 90.0
        }
        return UnifiedGeoModel(
            srid = 4326,
            features = features,
            minLon = minLon,
            maxLon = maxLon,
            minLat = minLat,
            maxLat = maxLat,
            sourceDialect = "GeoJSON (RFC 7946)"
        )
    }

    private fun parseWkt(source: String): UnifiedGeoModel {
        var detectedSrid = 4326
        val features = mutableListOf<UnifiedGeoFeature>()
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY

        fun track(c: GeoCoord): GeoCoord {
            minLon = min(minLon, c.lon)
            maxLon = max(maxLon, c.lon)
            minLat = min(minLat, c.lat)
            maxLat = max(maxLat, c.lat)
            return c
        }

        fun parseCoordPairs(inner: String): List<GeoCoord> {
            return inner.split(',').mapNotNull { token ->
                val nums = token.trim().split(Regex("""\s+""")).mapNotNull { it.toDoubleOrNull() }
                if (nums.size >= 2) track(GeoCoord(nums[0], nums[1])) else null
            }
        }

        val lines = source.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("--") }
        lines.forEachIndexed { idx, rawLine ->
            val sridMatch = Regex("""(?i)^SRID\s*=\s*(\d+)\s*;""").find(rawLine)
            if (sridMatch != null) {
                detectedSrid = sridMatch.groupValues[1].toIntOrNull() ?: 4326
            }
            val wkt = rawLine.replace(Regex("""(?i)^SRID\s*=\s*\d+\s*;"""), "").trim()
            val upper = wkt.uppercase()
            val shapes = mutableListOf<GeoShape>()

            when {
                upper.startsWith("POINT") -> {
                    val inner = wkt.substringAfter('(', "").substringBeforeLast(')', "")
                    parseCoordPairs(inner).firstOrNull()?.let { shapes.add(GeoShape.Point(it)) }
                }
                upper.startsWith("MULTIPOINT") -> {
                    val inner = wkt.substringAfter('(', "").substringBeforeLast(')', "")
                        .replace("(", "").replace(")", "")
                    parseCoordPairs(inner).forEach { shapes.add(GeoShape.Point(it)) }
                }
                upper.startsWith("LINESTRING") -> {
                    val inner = wkt.substringAfter('(', "").substringBeforeLast(')', "")
                    val pts = parseCoordPairs(inner)
                    if (pts.size >= 2) shapes.add(GeoShape.LineString(pts))
                }
                upper.startsWith("MULTILINESTRING") -> {
                    Regex("""\(([^()]+)\)""").findAll(wkt).forEach { m ->
                        val pts = parseCoordPairs(m.groupValues[1])
                        if (pts.size >= 2) shapes.add(GeoShape.LineString(pts))
                    }
                }
                upper.startsWith("POLYGON") || upper.startsWith("MULTIPOLYGON") -> {
                    val rings = Regex("""\(([^()]+)\)""").findAll(wkt).map { m ->
                        parseCoordPairs(m.groupValues[1])
                    }.filter { it.size >= 3 }.toList()
                    if (rings.isNotEmpty()) shapes.add(GeoShape.Polygon(rings))
                }
            }

            if (shapes.isNotEmpty()) {
                val label = upper.substringBefore('(').trim() + " #${idx + 1}"
                features.add(UnifiedGeoFeature("wkt_$idx", label, shapes, mapOf("srid" to detectedSrid.toString())))
            }
        }

        if (minLon.isInfinite()) {
            minLon = -180.0; maxLon = 180.0; minLat = -90.0; maxLat = 90.0
        }
        return UnifiedGeoModel(
            srid = detectedSrid,
            features = features,
            minLon = minLon,
            maxLon = maxLon,
            minLat = minLat,
            maxLat = maxLat,
            sourceDialect = "PostGIS EWKT (SRID $detectedSrid)"
        )
    }

    fun toGraphPayloadJson(model: UnifiedGraphModel): String {
        val root = JSONObject()
        root.put("title", model.title)
        root.put("graphName", model.graphName)
        root.put("dialect", model.sourceDialect)
        val nodesArr = JSONArray()
        model.nodes.forEach { n ->
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("label", n.label)
            obj.put("group", n.group)
            val detArr = JSONArray()
            n.details.forEach { detArr.put(it) }
            obj.put("details", detArr)
            nodesArr.put(obj)
        }
        val edgesArr = JSONArray()
        model.edges.forEach { e ->
            val obj = JSONObject()
            obj.put("source", e.source)
            obj.put("target", e.target)
            obj.put("label", e.label)
            obj.put("directed", e.directed)
            edgesArr.put(obj)
        }
        root.put("nodes", nodesArr)
        root.put("edges", edgesArr)
        return root.toString()
    }

    fun toGeoPayloadJson(model: UnifiedGeoModel): String {
        val root = JSONObject()
        root.put("srid", model.srid)
        root.put("dialect", model.sourceDialect)
        root.put("minLon", model.minLon)
        root.put("maxLon", model.maxLon)
        root.put("minLat", model.minLat)
        root.put("maxLat", model.maxLat)
        val featArr = JSONArray()
        model.features.forEach { f ->
            val fObj = JSONObject()
            fObj.put("id", f.id)
            fObj.put("label", f.label)
            val shapesArr = JSONArray()
            f.shapes.forEach { sh ->
                val sObj = JSONObject()
                when (sh) {
                    is GeoShape.Point -> {
                        sObj.put("type", "Point")
                        sObj.put("lon", sh.coord.lon)
                        sObj.put("lat", sh.coord.lat)
                    }
                    is GeoShape.LineString -> {
                        sObj.put("type", "LineString")
                        val pts = JSONArray()
                        sh.coords.forEach { c -> pts.put(JSONArray().put(c.lon).put(c.lat)) }
                        sObj.put("coords", pts)
                    }
                    is GeoShape.Polygon -> {
                        sObj.put("type", "Polygon")
                        val rings = JSONArray()
                        sh.rings.forEach { r ->
                            val rArr = JSONArray()
                            r.forEach { c -> rArr.put(JSONArray().put(c.lon).put(c.lat)) }
                            rings.put(rArr)
                        }
                        sObj.put("rings", rings)
                    }
                }
                shapesArr.put(sObj)
            }
            fObj.put("shapes", shapesArr)
            featArr.put(fObj)
        }
        root.put("features", featArr)
        return root.toString()
    }

    private fun parseInlinePropertyMap(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val pairRegex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*:\s*('([^']*)'|"([^"]*)"|([^,}\s]+))""")
        pairRegex.findAll(raw).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[3].ifEmpty { m.groupValues[4].ifEmpty { m.groupValues[5] } }
            out[key] = value
        }
        return out
    }

    private fun parseDotAttrs(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val attrRegex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:"([^"]*)"|([^\s,\]]+))""")
        attrRegex.findAll(raw).forEach { m ->
            out[m.groupValues[1].lowercase()] = m.groupValues[2].ifEmpty { m.groupValues[3] }
        }
        return out
    }

    private fun parseXmlAttrs(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("""([A-Za-z_][A-Za-z0-9_:-]*)\s*=\s*["']([^"']*)["']""").findAll(raw).forEach { m ->
            out[m.groupValues[1].lowercase()] = m.groupValues[2]
        }
        return out
    }

    private fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun cleanSqlIdent(raw: String): String =
        raw.trim().trim('"', '`', '\'').substringAfterLast('.').trim('"', '`')

    private fun splitTopLevelCommas(body: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inSingle = false
        for (ch in body) {
            when {
                ch == '\'' -> { inSingle = !inSingle; current.append(ch) }
                !inSingle && ch == '(' -> { depth++; current.append(ch) }
                !inSingle && ch == ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(ch) }
                !inSingle && depth == 0 && ch == ',' -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}

object SpecializedFormatConverters {

    fun exportOptionsFor(kind: SpecializedFormatKind): List<SpecializedExportOption> {
        val imageOptions = listOf(
            SpecializedExportOption(
                id = "png",
                label = "Export as PNG (.png)",
                description = "High-resolution 2× raster image",
                extension = "png",
                mimeType = "image/png",
                isImage = true,
                imageFormat = MermaidImageExportFormat.PNG
            ),
            SpecializedExportOption(
                id = "svg",
                label = "Export as SVG (.svg)",
                description = "Scalable vector graphic",
                extension = "svg",
                mimeType = "image/svg+xml",
                isImage = true,
                imageFormat = MermaidImageExportFormat.SVG
            )
        )

        val ecosystemOptions = when (kind) {
            SpecializedFormatKind.LATEX -> listOf(
                SpecializedExportOption(
                    id = "tex_raw",
                    label = "LaTeX Source (.tex)",
                    description = "Raw LaTeX expression / snippet",
                    extension = "tex",
                    mimeType = "application/x-tex",
                    transform = { src, _ -> src }
                ),
                SpecializedExportOption(
                    id = "tex_doc",
                    label = "Standalone LaTeX Document (.tex)",
                    description = "Compilable article with amsmath & geometry",
                    extension = "tex",
                    mimeType = "application/x-tex",
                    transform = { src, title -> toStandaloneLatexDocument(src, title) }
                )
            )

            SpecializedFormatKind.GEOSPATIAL -> listOf(
                SpecializedExportOption(
                    id = "geojson",
                    label = "GeoJSON (.geojson)",
                    description = "RFC 7946 FeatureCollection for QGIS / D3 / Leaflet",
                    extension = "geojson",
                    mimeType = "application/geo+json",
                    transform = { src, _ -> toGeoJson(SpecializedFormatParser.parseGeospatial(src)) }
                ),
                SpecializedExportOption(
                    id = "wkt",
                    label = "PostGIS EWKT (.wkt)",
                    description = "Well-Known Text geometries with SRID",
                    extension = "wkt",
                    mimeType = "text/plain",
                    transform = { src, _ -> toWkt(SpecializedFormatParser.parseGeospatial(src)) }
                ),
                SpecializedExportOption(
                    id = "postgis_sql",
                    label = "PostGIS PostgreSQL (.sql)",
                    description = "Executable DDL, ST_GeomFromGeoJSON & GiST index",
                    extension = "sql",
                    mimeType = "application/sql",
                    transform = { src, title -> toPostGisSql(SpecializedFormatParser.parseGeospatial(src), title) }
                )
            )

            SpecializedFormatKind.CYPHER_AGE,
            SpecializedFormatKind.GRAPH_DOT,
            SpecializedFormatKind.GRAPH_EXCHANGE,
            SpecializedFormatKind.POSTGRES_SQL -> buildList {
                if (kind == SpecializedFormatKind.POSTGRES_SQL) {
                    add(
                        SpecializedExportOption(
                            id = "raw_sql",
                            label = "PostgreSQL Source (.sql)",
                            description = "Original PostgreSQL / PostGIS / AGE script",
                            extension = "sql",
                            mimeType = "application/sql",
                            transform = { src, _ -> src }
                        )
                    )
                }
                add(
                    SpecializedExportOption(
                        id = "cypher",
                        label = "Cypher Query (.cypher)",
                        description = "openCypher graph script",
                        extension = "cypher",
                        mimeType = "text/plain",
                        transform = { src, title ->
                            if (kind == SpecializedFormatKind.CYPHER_AGE && !src.contains("ag_catalog.cypher", true)) src
                            else toCypher(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
                add(
                    SpecializedExportOption(
                        id = "age_sql",
                        label = "Apache AGE PostgreSQL (.sql)",
                        description = "ag_catalog.create_graph + cypher() wrapper",
                        extension = "sql",
                        mimeType = "application/sql",
                        transform = { src, title ->
                            toApacheAgeSql(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
                add(
                    SpecializedExportOption(
                        id = "networkx_py",
                        label = "NetworkX Python (.py)",
                        description = "Runnable Python networkx DiGraph builder",
                        extension = "py",
                        mimeType = "text/x-python",
                        transform = { src, title ->
                            toNetworkXPython(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
                add(
                    SpecializedExportOption(
                        id = "d3_json",
                        label = "D3.js / NetworkX JSON (.json)",
                        description = "Standard node-link JSON graph payload",
                        extension = "json",
                        mimeType = "application/json",
                        transform = { src, title ->
                            toD3Json(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
                add(
                    SpecializedExportOption(
                        id = "graphml",
                        label = "GraphML (.graphml)",
                        description = "XML graph interchange for NetworkX / Gephi",
                        extension = "graphml",
                        mimeType = "application/xml",
                        transform = { src, title ->
                            toGraphMl(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
                add(
                    SpecializedExportOption(
                        id = "dot",
                        label = "GraphViz DOT (.dot)",
                        description = "DOT language digraph source",
                        extension = "dot",
                        mimeType = "text/vnd.graphviz",
                        transform = { src, title ->
                            toDot(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
                add(
                    SpecializedExportOption(
                        id = "mermaid",
                        label = "Mermaid Flowchart (.mmd)",
                        description = "Mermaid.js syntax diagram",
                        extension = "mmd",
                        mimeType = "text/plain",
                        transform = { src, title ->
                            toMermaid(SpecializedFormatParser.parseToUnifiedGraph(kind, src, title))
                        }
                    )
                )
            }
        }

        return imageOptions + ecosystemOptions
    }

    fun toStandaloneLatexDocument(raw: String, title: String): String {
        val trimmed = raw.trim()
        if (trimmed.contains("\\documentclass")) return trimmed
        val safeTitle = title.replace("_", "\\_").replace("&", "\\&").replace("%", "\\%")
        val body = if (trimmed.startsWith("$$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\begin{")) {
            trimmed
        } else {
            "\\[\n$trimmed\n\\]"
        }
        return buildString {
            appendLine("\\documentclass[11pt]{article}")
            appendLine("\\usepackage[margin=1in]{geometry}")
            appendLine("\\usepackage{amsmath,amssymb,mathtools,hyperref}")
            appendLine("\\title{$safeTitle}")
            appendLine("\\date{\\today}")
            appendLine("\\begin{document}")
            appendLine("\\maketitle")
            appendLine()
            appendLine(body)
            appendLine()
            appendLine("\\end{document}")
        }
    }

    fun toCypher(graph: UnifiedGraphModel): String = buildString {
        appendLine("// openCypher / Apache AGE Graph Script: ${graph.title}")
        val idToVar = LinkedHashMap<String, String>()
        graph.nodes.forEachIndexed { idx, node ->
            val v = sanitizeCypherVar(node.id, idx)
            idToVar[node.id] = v
            val label = sanitizeCypherLabel(node.group.ifBlank { "Entity" })
            val safeName = escapeSingleQuote(node.label)
            appendLine("MERGE ($v:$label {id: '${escapeSingleQuote(node.id)}', name: '$safeName'})")
        }
        graph.edges.forEach { edge ->
            val src = idToVar[edge.source] ?: return@forEach
            val dst = idToVar[edge.target] ?: return@forEach
            val rel = sanitizeCypherRel(edge.label.ifBlank { "RELATED_TO" })
            appendLine("MERGE ($src)-[:$rel]->($dst)")
        }
        appendLine("RETURN *;")
    }

    fun toApacheAgeSql(graph: UnifiedGraphModel): String = buildString {
        val graphName = graph.graphName.lowercase().replace(Regex("[^a-z0-9_]+"), "_").ifBlank { "vortex_graph" }
        appendLine("-- PostgreSQL + Apache AGE Graph Provisioning Script")
        appendLine("CREATE EXTENSION IF NOT EXISTS age;")
        appendLine("LOAD 'age';")
        appendLine("SET search_path = ag_catalog, \"\$user\", public;")
        appendLine()
        appendLine("SELECT ag_catalog.create_graph('$graphName');")
        appendLine()
        appendLine("SELECT * FROM ag_catalog.cypher('$graphName', \$\$")
        val idToVar = LinkedHashMap<String, String>()
        graph.nodes.forEachIndexed { idx, node ->
            val v = sanitizeCypherVar(node.id, idx)
            idToVar[node.id] = v
            val label = sanitizeCypherLabel(node.group.ifBlank { "Entity" })
            appendLine("  CREATE ($v:$label {id: '${escapeSingleQuote(node.id)}', name: '${escapeSingleQuote(node.label)}'})")
        }
        graph.edges.forEach { edge ->
            val src = idToVar[edge.source] ?: return@forEach
            val dst = idToVar[edge.target] ?: return@forEach
            val rel = sanitizeCypherRel(edge.label.ifBlank { "RELATED_TO" })
            appendLine("  CREATE ($src)-[:$rel]->($dst)")
        }
        appendLine("  RETURN count(*)")
        appendLine("\$\$) AS (nodes_created agtype);")
    }

    fun toNetworkXPython(graph: UnifiedGraphModel): String = buildString {
        appendLine("# NetworkX Graph Builder — ${graph.title}")
        appendLine("import json")
        appendLine("import networkx as nx")
        appendLine()
        appendLine("G = nx.DiGraph(name=${JSONObject.quote(graph.graphName)})")
        graph.nodes.forEach { node ->
            appendLine(
                "G.add_node(${JSONObject.quote(node.id)}, label=${JSONObject.quote(node.label)}, group=${JSONObject.quote(node.group)})"
            )
        }
        appendLine()
        graph.edges.forEach { edge ->
            appendLine(
                "G.add_edge(${JSONObject.quote(edge.source)}, ${JSONObject.quote(edge.target)}, label=${JSONObject.quote(edge.label)})"
            )
        }
        appendLine()
        appendLine("if __name__ == '__main__':")
        appendLine("    print(f'Nodes: {G.number_of_nodes()}, Edges: {G.number_of_edges()}')")
        appendLine("    print(json.dumps(nx.node_link_data(G), indent=2))")
    }

    fun toD3Json(graph: UnifiedGraphModel): String {
        val root = JSONObject()
        root.put("directed", true)
        root.put("multigraph", false)
        root.put("graph", JSONObject().put("name", graph.graphName).put("title", graph.title))
        val nodesArr = JSONArray()
        graph.nodes.forEach { n ->
            nodesArr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("label", n.label)
                    .put("group", n.group.ifBlank { "default" })
            )
        }
        val linksArr = JSONArray()
        graph.edges.forEach { e ->
            linksArr.put(
                JSONObject()
                    .put("source", e.source)
                    .put("target", e.target)
                    .put("label", e.label)
            )
        }
        root.put("nodes", nodesArr)
        root.put("links", linksArr)
        return root.toString(2)
    }

    fun toGraphMl(graph: UnifiedGraphModel): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<graphml xmlns="http://graphml.graphdrawing.org/xmlns">""")
        appendLine("""  <key id="d_label" for="node" attr.name="label" attr.type="string"/>""")
        appendLine("""  <key id="d_group" for="node" attr.name="group" attr.type="string"/>""")
        appendLine("""  <key id="e_label" for="edge" attr.name="label" attr.type="string"/>""")
        appendLine("""  <graph id="${escapeXml(graph.graphName)}" edgedefault="directed">""")
        graph.nodes.forEach { n ->
            appendLine("""    <node id="${escapeXml(n.id)}">""")
            appendLine("""      <data key="d_label">${escapeXml(n.label)}</data>""")
            if (n.group.isNotBlank()) appendLine("""      <data key="d_group">${escapeXml(n.group)}</data>""")
            appendLine("""    </node>""")
        }
        graph.edges.forEachIndexed { idx, e ->
            appendLine("""    <edge id="e$idx" source="${escapeXml(e.source)}" target="${escapeXml(e.target)}">""")
            if (e.label.isNotBlank()) appendLine("""      <data key="e_label">${escapeXml(e.label)}</data>""")
            appendLine("""    </edge>""")
        }
        appendLine("""  </graph>""")
        appendLine("""</graphml>""")
    }

    fun toDot(graph: UnifiedGraphModel): String = buildString {
        val gName = graph.graphName.replace(Regex("[^A-Za-z0-9_]+"), "_").ifBlank { "vortex_graph" }
        appendLine("digraph $gName {")
        appendLine("  rankdir=LR;")
        appendLine("""  node [shape=box, style="rounded,filled"];""")
        graph.nodes.forEach { n ->
            val lbl = if (n.group.isNotBlank()) "${n.label}\\n:${n.group}" else n.label
            appendLine("  ${JSONObject.quote(n.id)} [label=${JSONObject.quote(lbl)}];")
        }
        graph.edges.forEach { e ->
            val attr = if (e.label.isNotBlank()) " [label=${JSONObject.quote(e.label)}]" else ""
            appendLine("  ${JSONObject.quote(e.source)} -> ${JSONObject.quote(e.target)}$attr;")
        }
        appendLine("}")
    }

    fun toMermaid(graph: UnifiedGraphModel): String = buildString {
        appendLine("flowchart LR")
        val idMap = LinkedHashMap<String, String>()
        graph.nodes.forEachIndexed { idx, n ->
            val safeId = "N$idx"
            idMap[n.id] = safeId
            val safeLbl = n.label.replace("\"", "'")
            appendLine("""  $safeId["$safeLbl"]""")
        }
        graph.edges.forEach { e ->
            val s = idMap[e.source] ?: return@forEach
            val t = idMap[e.target] ?: return@forEach
            if (e.label.isNotBlank()) {
                appendLine("""  $s -->|"${e.label.replace("\"", "'")}"| $t""")
            } else {
                appendLine("  $s --> $t")
            }
        }
    }

    fun toGeoJson(geo: UnifiedGeoModel): String {
        val fc = JSONObject().put("type", "FeatureCollection")
        val arr = JSONArray()
        geo.features.forEach { f ->
            f.shapes.forEach { sh ->
                val geom = when (sh) {
                    is GeoShape.Point -> JSONObject()
                        .put("type", "Point")
                        .put("coordinates", JSONArray().put(sh.coord.lon).put(sh.coord.lat))
                    is GeoShape.LineString -> JSONObject()
                        .put("type", "LineString")
                        .put("coordinates", JSONArray().apply {
                            sh.coords.forEach { put(JSONArray().put(it.lon).put(it.lat)) }
                        })
                    is GeoShape.Polygon -> JSONObject()
                        .put("type", "Polygon")
                        .put("coordinates", JSONArray().apply {
                            sh.rings.forEach { r ->
                                put(JSONArray().apply { r.forEach { put(JSONArray().put(it.lon).put(it.lat)) } })
                            }
                        })
                }
                val props = JSONObject().put("name", f.label)
                f.properties.forEach { (k, v) -> props.put(k, v) }
                arr.put(
                    JSONObject()
                        .put("type", "Feature")
                        .put("properties", props)
                        .put("geometry", geom)
                )
            }
        }
        fc.put("features", arr)
        return fc.toString(2)
    }

    fun toWkt(geo: UnifiedGeoModel): String = buildString {
        geo.features.forEach { f ->
            f.shapes.forEach { sh ->
                val wktBody = when (sh) {
                    is GeoShape.Point -> "POINT(${fmt(sh.coord.lon)} ${fmt(sh.coord.lat)})"
                    is GeoShape.LineString -> "LINESTRING(${sh.coords.joinToString(", ") { "${fmt(it.lon)} ${fmt(it.lat)}" }})"
                    is GeoShape.Polygon -> "POLYGON(${sh.rings.joinToString(", ") { r -> "(${r.joinToString(", ") { "${fmt(it.lon)} ${fmt(it.lat)}" }})" }})"
                }
                appendLine("SRID=${geo.srid};$wktBody")
            }
        }
    }

    fun toPostGisSql(geo: UnifiedGeoModel, title: String): String = buildString {
        val tbl = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(36).ifBlank { "spatial_features" }
        appendLine("-- PostgreSQL + PostGIS Spatial Table & Seed Script")
        appendLine("CREATE EXTENSION IF NOT EXISTS postgis;")
        appendLine()
        appendLine("CREATE TABLE IF NOT EXISTS $tbl (")
        appendLine("    id BIGSERIAL PRIMARY KEY,")
        appendLine("    name TEXT NOT NULL,")
        appendLine("    properties JSONB NOT NULL DEFAULT '{}'::jsonb,")
        appendLine("    geom GEOMETRY(Geometry, ${geo.srid}) NOT NULL")
        appendLine(");")
        appendLine()
        appendLine("CREATE INDEX IF NOT EXISTS idx_${tbl}_geom ON $tbl USING GIST (geom);")
        appendLine()
        geo.features.forEach { f ->
            val propsJson = JSONObject().apply { f.properties.forEach { (k, v) -> put(k, v) } }.toString()
            f.shapes.forEach { sh ->
                val wktBody = when (sh) {
                    is GeoShape.Point -> "POINT(${fmt(sh.coord.lon)} ${fmt(sh.coord.lat)})"
                    is GeoShape.LineString -> "LINESTRING(${sh.coords.joinToString(", ") { "${fmt(it.lon)} ${fmt(it.lat)}" }})"
                    is GeoShape.Polygon -> "POLYGON(${sh.rings.joinToString(", ") { r -> "(${r.joinToString(", ") { "${fmt(it.lon)} ${fmt(it.lat)}" }})" }})"
                }
                appendLine(
                    "INSERT INTO $tbl (name, properties, geom) VALUES (" +
                        "'${escapeSingleQuote(f.label)}', " +
                        "'${escapeSingleQuote(propsJson)}'::jsonb, " +
                        "ST_SetSRID(ST_GeomFromText('$wktBody'), ${geo.srid}));"
                )
            }
        }
    }

    private fun sanitizeCypherVar(raw: String, idx: Int): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (cleaned.isNotEmpty() && cleaned.first().isLetter()) cleaned else "n_${idx}_$cleaned"
    }

    private fun sanitizeCypherLabel(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9_]"), "")
        return if (cleaned.isNotEmpty() && cleaned.first().isLetter()) cleaned else "Entity"
    }

    private fun sanitizeCypherRel(raw: String): String {
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9_]"), "_").trim('_')
        return if (cleaned.isNotEmpty() && cleaned.first().isLetter()) cleaned else "RELATED_TO"
    }

    private fun escapeSingleQuote(s: String): String = s.replace("'", "''")

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
}
