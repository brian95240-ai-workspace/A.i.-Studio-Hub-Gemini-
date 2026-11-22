package com.example.data.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.ai.VortexModelRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

data class IngestedArtifact(
    val fileName: String,
    val extension: String,
    val detectedLanguage: String,
    val content: String,
    val byteSize: Int
)

data class IngestionBatchReport(
    val artifacts: List<IngestedArtifact>,
    val skippedMessages: List<String>
)

class UniversalArtifactIngestor(
    private val modelRouter: VortexModelRouter
) {
    private val ignoredZipDirs = listOf(
        "node_modules/", "target/", ".git/", "build/", "dist/", "__pycache__/", ".gradle/", ".idea/"
    )

    private val extensionLanguageMap: Map<String, String> = mapOf(
        "py" to "Python",
        "ts" to "TypeScript",
        "tsx" to "TypeScript JSX",
        "js" to "JavaScript",
        "jsx" to "JavaScript JSX",
        "mjs" to "JavaScript Module",
        "cjs" to "CommonJS",
        "rs" to "Rust",
        "go" to "Go",
        "kt" to "Kotlin",
        "kts" to "Kotlin Script",
        "java" to "Java",
        "swift" to "Swift",
        "dart" to "Dart",
        "c" to "C",
        "cpp" to "C++",
        "cc" to "C++",
        "h" to "C/C++ Header",
        "hpp" to "C++ Header",
        "cs" to "C#",
        "rb" to "Ruby",
        "php" to "PHP",
        "sh" to "Shell Script",
        "bash" to "Bash",
        "zsh" to "Zsh",
        "sql" to "PostgreSQL / SQL",
        "pgsql" to "PostgreSQL / PostGIS",
        "psql" to "PostgreSQL Script",
        "cypher" to "Cypher / Apache AGE",
        "cql" to "Cypher Query",
        "dot" to "GraphViz DOT",
        "gv" to "GraphViz DOT",
        "graphml" to "GraphML (NetworkX)",
        "gexf" to "GEXF Graph XML",
        "geojson" to "GeoJSON (PostGIS)",
        "topojson" to "TopoJSON Geospatial",
        "wkt" to "PostGIS WKT Geometry",
        "ewkt" to "PostGIS EWKT Geometry",
        "proto" to "Protobuf",
        "graphql" to "GraphQL",
        "gql" to "GraphQL",
        "yaml" to "YAML",
        "yml" to "YAML",
        "json" to "JSON",
        "jsonl" to "JSON Lines",
        "toml" to "TOML",
        "ini" to "INI Config",
        "csv" to "CSV Data",
        "md" to "Markdown",
        "markdown" to "Markdown",
        "txt" to "Plain Text",
        "tex" to "LaTeX",
        "latex" to "LaTeX",
        "bib" to "BibTeX",
        "mmd" to "Mermaid Diagram",
        "mermaid" to "Mermaid Diagram",
        "svg" to "SVG Vector",
        "xml" to "XML",
        "html" to "HTML",
        "css" to "CSS",
        "scss" to "SCSS",
        "r" to "R",
        "scala" to "Scala",
        "ex" to "Elixir",
        "zig" to "Zig"
    )

    fun detectLanguage(fileName: String, ext: String): String {
        val specialLang = when (fileName.substringAfterLast('/').lowercase()) {
            "dockerfile" -> "Dockerfile"
            "makefile" -> "Makefile"
            "cargo.toml" -> "Rust Cargo"
            "package.json" -> "Node Package JSON"
            "go.mod" -> "Go Module"
            else -> null
        }
        return specialLang ?: extensionLanguageMap[ext.lowercase()] ?: "UTF-8 Source / Text"
    }

    suspend fun ingestUris(context: Context, uris: List<Uri>): IngestionBatchReport =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<IngestedArtifact>()
            val skipped = mutableListOf<String>()

            for (uri in uris) {
                val displayName = resolveDisplayName(context, uri)
                val ext = displayName.substringAfterLast('.', "").lowercase()
                val rawBytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()

                if (rawBytes == null || rawBytes.isEmpty()) {
                    skipped.add("$displayName: empty or unreadable file.")
                    continue
                }

                when (ext) {
                    "pdf" -> {
                        if (rawBytes.size > 20 * 1024 * 1024) {
                            skipped.add("$displayName: exceeds 20 MB inline PDF limit.")
                        } else {
                            val extracted = runCatching {
                                modelRouter.extractPdfTextWithGemini(rawBytes, displayName)
                            }.getOrElse { err ->
                                skipped.add("$displayName: PDF extraction failed (${err.message?.take(60)}).")
                                ""
                            }
                            if (extracted.isNotBlank()) {
                                results.add(
                                    IngestedArtifact(
                                        fileName = displayName,
                                        extension = "pdf",
                                        detectedLanguage = "PDF Document",
                                        content = extracted.take(340_000),
                                        byteSize = rawBytes.size
                                    )
                                )
                            }
                        }
                    }

                    "zip" -> {
                        unpackZip(rawBytes, displayName, results, skipped)
                    }

                    else -> {
                        ingestSingleRawFile(displayName, ext, rawBytes, results, skipped)
                    }
                }
            }

            IngestionBatchReport(artifacts = results, skippedMessages = skipped)
        }

    private fun unpackZip(
        zipBytes: ByteArray,
        archiveName: String,
        results: MutableList<IngestedArtifact>,
        skipped: MutableList<String>
    ) {
        var unpacked = 0
        runCatching {
            ZipInputStream(zipBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null && unpacked < 25) {
                    val entryName = entry.name.trimStart('/')
                    if (!entry.isDirectory && ignoredZipDirs.none { entryName.contains(it, ignoreCase = true) }) {
                        val bytes = zis.readBytes()
                        val ext = entryName.substringAfterLast('.', "").lowercase()
                        if (bytes.isNotEmpty() && bytes.size <= 350_000) {
                            val before = results.size
                            ingestSingleRawFile(entryName, ext, bytes, results, skipped)
                            if (results.size > before) unpacked++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }.onFailure { err ->
            skipped.add("$archiveName: ZIP read error (${err.message?.take(60)}).")
        }
    }

    private fun ingestSingleRawFile(
        fileName: String,
        ext: String,
        rawBytes: ByteArray,
        results: MutableList<IngestedArtifact>,
        skipped: MutableList<String>
    ) {
        if (rawBytes.size > 400_000) {
            skipped.add("$fileName: exceeds 400 KB per-file text limit.")
            return
        }
        val hasNulByte = rawBytes.take(8192).any { it == 0.toByte() }
        if (hasNulByte) {
            skipped.add("$fileName: binary file skipped (non-UTF-8).")
            return
        }
        val detectedLang = detectLanguage(fileName, ext)
        val decoded = String(rawBytes, Charsets.UTF_8)
        results.add(
            IngestedArtifact(
                fileName = fileName,
                extension = ext,
                detectedLanguage = detectedLang,
                content = decoded.take(340_000),
                byteSize = rawBytes.size
            )
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "artifact.txt" } ?: "artifact.txt"
    }
}
