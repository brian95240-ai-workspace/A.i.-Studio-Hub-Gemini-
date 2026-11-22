package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.example.data.export.UniversalFormatCatalog
import com.example.data.model.ProjectFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class SafSyncResult(
    val fileCount: Int,
    val sha256: String
)

object SafFolderSyncManager {

    fun computeProjectHash(files: List<ProjectFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.sortedBy { it.relativePath }.forEach { f ->
            digest.update(f.relativePath.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(f.content.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun pushProjectToSafTree(
        context: Context,
        treeUri: Uri,
        rootFolderName: String,
        files: List<ProjectFile>
    ): Result<SafSyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val rootTreeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootTreeDocId)
            val projectDirUri = findOrCreateDirectory(context, treeUri, rootDocUri, rootFolderName.ifBlank { "vortex-research" })

            var count = 0
            for (file in files) {
                val segments = file.relativePath.trim().trimStart('/').split("/").filter { it.isNotBlank() }
                if (segments.isEmpty()) continue
                var currentDir = projectDirUri
                for (dirName in segments.dropLast(1)) {
                    currentDir = findOrCreateDirectory(context, treeUri, currentDir, dirName)
                }
                val fileName = segments.last()
                val mime = UniversalFormatCatalog.mimeTypeForFileName(fileName)
                val fileUri = findOrCreateFile(context, treeUri, currentDir, fileName, mime)
                resolver.openOutputStream(fileUri, "wt")?.use { out ->
                    out.write(file.content.toByteArray(Charsets.UTF_8))
                }
                count++
            }

            SafSyncResult(fileCount = count, sha256 = computeProjectHash(files))
        }
    }

    suspend fun pullProjectFromSafTree(
        context: Context,
        treeUri: Uri,
        rootFolderName: String
    ): Result<Pair<List<ProjectFile>, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val rootTreeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootTreeDocId)
            val projectDirUri = findChildByName(context, treeUri, rootDocUri, rootFolderName) ?: rootDocUri

            val pulled = mutableListOf<ProjectFile>()
            readRecursive(context, treeUri, projectDirUri, "", pulled)
            pulled to computeProjectHash(pulled)
        }
    }

    suspend fun computeSafTreeHash(
        context: Context,
        treeUri: Uri,
        rootFolderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rootTreeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootTreeDocId)
            val projectDirUri = findChildByName(context, treeUri, rootDocUri, rootFolderName)
                ?: return@runCatching ""
            val scanned = mutableListOf<ProjectFile>()
            readRecursive(context, treeUri, projectDirUri, "", scanned)
            if (scanned.isEmpty()) "" else computeProjectHash(scanned)
        }
    }

    suspend fun writeSingleDocument(context: Context, uri: Uri, content: String): Result<Unit> =
        writeBinaryDocument(context, uri, content.toByteArray(Charsets.UTF_8))

    suspend fun writeBinaryDocument(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(bytes)
                } ?: error("Unable to open target document for writing.")
            }
        }

    fun shareViaFileProvider(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = UniversalFormatCatalog.mimeTypeForFileName(fileName)
    ): Result<Unit> = shareBinaryViaFileProvider(
        context = context,
        fileName = fileName,
        bytes = content.toByteArray(Charsets.UTF_8),
        mimeType = mimeType
    )

    fun shareBinaryViaFileProvider(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String = UniversalFormatCatalog.mimeTypeForFileName(fileName)
    ): Result<Unit> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(exportDir, safeName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, safeName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export or Share ($safeName)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun readRecursive(
        context: Context,
        treeUri: Uri,
        dirUri: Uri,
        prefix: String,
        out: MutableList<ProjectFile>
    ) {
        if (out.size >= 60) return
        val dirDocId = DocumentsContract.getDocumentId(dirUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && out.size < 60) {
                val childId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2).orEmpty()
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                val relPath = if (prefix.isBlank()) name else "$prefix/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    readRecursive(context, treeUri, childUri, relPath, out)
                } else {
                    val bytes = context.contentResolver.openInputStream(childUri)?.use { it.readBytes() }
                        ?: continue
                    if (bytes.size <= 350_000 && bytes.take(4096).none { it == 0.toByte() }) {
                        val category = when {
                            relPath.startsWith("PRD/") -> "PRD"
                            relPath.startsWith("AI_Roundtable/") -> "ROUNDTABLE"
                            else -> "RESEARCH"
                        }
                        out.add(
                            ProjectFile(
                                relativePath = relPath,
                                content = String(bytes, Charsets.UTF_8),
                                category = category
                            )
                        )
                    }
                }
            }
        }
    }

    private fun findOrCreateDirectory(
        context: Context,
        treeUri: Uri,
        parentDocUri: Uri,
        name: String
    ): Uri {
        return findChildByName(context, treeUri, parentDocUri, name)
            ?: DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name
            )
            ?: error("Could not create folder '$name' in bound directory.")
    }

    private fun findOrCreateFile(
        context: Context,
        treeUri: Uri,
        parentDocUri: Uri,
        name: String,
        mimeType: String
    ): Uri {
        return findChildByName(context, treeUri, parentDocUri, name)
            ?: DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                name
            )
            ?: error("Could not create file '$name' in bound directory.")
    }

    private fun findChildByName(
        context: Context,
        treeUri: Uri,
        parentDocUri: Uri,
        targetName: String
    ): Uri? {
        val parentId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.equals(targetName, ignoreCase = false)) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        }
        return null
    }
}
