package com.example.data.export

import com.example.data.model.ProjectFile
import com.example.data.model.ResearchProject
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BulkProjectExportBundle(
    val project: ResearchProject,
    val files: List<ProjectFile>
)

data class BulkZipExportResult(
    val bytes: ByteArray,
    val projectCount: Int,
    val fileCount: Int,
    val uncompressedBytes: Long,
    val compressedBytes: Int,
    val suggestedFileName: String
)

object BulkZipExporter {

    fun suggestedArchiveFileName(bundles: List<BulkProjectExportBundle>): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return when (bundles.size) {
            0 -> "vortex-backup-$stamp.zip"
            1 -> {
                val p = bundles.first().project
                val slug = sanitizeSegment(p.rootFolderName.ifBlank { p.title }).ifBlank { "vortex-project" }
                "$slug-backup-$stamp.zip"
            }
            else -> "vortex-bulk-${bundles.size}-projects-$stamp.zip"
        }
    }

    fun buildCompressedZip(
        bundles: List<BulkProjectExportBundle>,
        includeManifest: Boolean = true
    ): BulkZipExportResult {
        val activeBundles = bundles.filter { it.files.isNotEmpty() }
        val baos = ByteArrayOutputStream()
        val usedRootDirs = mutableMapOf<String, Int>()
        val writtenEntries = mutableSetOf<String>()
        var totalFileCount = 0
        var uncompressedTotal = 0L

        val manifestProjects = JSONArray()

        ZipOutputStream(baos).use { zos ->
            zos.setLevel(Deflater.BEST_COMPRESSION)

            activeBundles.forEach { bundle ->
                val rawRoot = sanitizeSegment(
                    bundle.project.rootFolderName.ifBlank { bundle.project.title }
                ).ifBlank { "project-${bundle.project.id.take(6)}" }
                val rootKey = rawRoot.lowercase(Locale.US)
                val seenCount = usedRootDirs[rootKey] ?: 0
                usedRootDirs[rootKey] = seenCount + 1
                val uniqueRoot = if (seenCount == 0) rawRoot else "$rawRoot-${seenCount + 1}"

                val projectFilesJson = JSONArray()
                var projectFileCount = 0

                bundle.files.sortedBy { it.relativePath }.forEach { file ->
                    val cleanRel = sanitizeRelativePath(file.relativePath)
                    if (cleanRel.isBlank()) return@forEach
                    val entryPath = "$uniqueRoot/$cleanRel"
                    if (writtenEntries.add(entryPath.lowercase(Locale.US))) {
                        val contentBytes = file.content.toByteArray(Charsets.UTF_8)
                        zos.putNextEntry(ZipEntry(entryPath))
                        zos.write(contentBytes)
                        zos.closeEntry()

                        totalFileCount++
                        projectFileCount++
                        uncompressedTotal += contentBytes.size.toLong()
                        projectFilesJson.put(
                            JSONObject().apply {
                                put("path", entryPath)
                                put("category", file.category)
                                put("bytes", contentBytes.size)
                            }
                        )
                    }
                }

                if (projectFileCount > 0) {
                    manifestProjects.put(
                        JSONObject().apply {
                            put("id", bundle.project.id)
                            put("title", bundle.project.title)
                            put("archiveFolder", uniqueRoot)
                            put("domain", bundle.project.detectedDomain)
                            put("latestPrdVersion", bundle.project.latestPrdVersion)
                            put("githubRepoUrl", bundle.project.githubRepoUrl)
                            put("fileCount", projectFileCount)
                            put("files", projectFilesJson)
                        }
                    )
                }
            }

            if (includeManifest && totalFileCount > 0) {
                val manifest = JSONObject().apply {
                    put("format", "vortex-bulk-backup-v1")
                    put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    put("projectCount", activeBundles.size)
                    put("fileCount", totalFileCount)
                    put("uncompressedBytes", uncompressedTotal)
                    put("projects", manifestProjects)
                }.toString(2)
                val manifestBytes = manifest.toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("VORTEX_BACKUP_MANIFEST.json"))
                zos.write(manifestBytes)
                zos.closeEntry()
                uncompressedTotal += manifestBytes.size.toLong()
            }
        }

        val zipBytes = baos.toByteArray()
        return BulkZipExportResult(
            bytes = zipBytes,
            projectCount = activeBundles.size,
            fileCount = totalFileCount,
            uncompressedBytes = uncompressedTotal,
            compressedBytes = zipBytes.size,
            suggestedFileName = suggestedArchiveFileName(activeBundles)
        )
    }

    private fun sanitizeSegment(raw: String): String =
        raw.trim()
            .replace(Regex("[/\\\\]+"), "-")
            .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
            .trim('-')

    private fun sanitizeRelativePath(raw: String): String =
        raw.trim()
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
}
