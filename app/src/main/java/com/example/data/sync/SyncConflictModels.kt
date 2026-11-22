package com.example.data.sync

import com.example.data.model.ResearchProject
import com.example.data.model.RoundtableItem
import com.example.data.model.VortexJson

enum class ConflictKind {
    REPO_FILE,
    ROUNDTABLE_FILE
}

enum class ConflictResolutionStrategy {
    KEEP_LOCAL,
    USE_SERVER,
    KEEP_BOTH
}

data class FileSyncConflict(
    val conflictId: String,
    val opId: Long,
    val kind: ConflictKind,
    val projectId: String,
    val targetEntityId: String,
    val filePath: String,
    val localContent: String,
    val serverContent: String,
    val localUpdatedAtMillis: Long,
    val serverUpdatedAtMillis: Long,
    val cachedForUid: String,
    val localProject: ResearchProject? = null,
    val serverProject: ResearchProject? = null,
    val localRoundtableItem: RoundtableItem? = null,
    val serverRoundtableItem: RoundtableItem? = null
)

object SyncConflictHelper {
    private const val KEY_BASE_SERVER_UPDATED_AT = "baseServerUpdatedAtMillis"
    private const val KEY_EDITED_FILE_PATH = "editedFilePath"

    fun encodeBaseline(baseServerUpdatedAtMillis: Long, editedFilePath: String): String =
        VortexJson.encodeStringMap(
            mapOf(
                KEY_BASE_SERVER_UPDATED_AT to baseServerUpdatedAtMillis.toString(),
                KEY_EDITED_FILE_PATH to editedFilePath
            )
        )

    fun decodeBaseline(payloadJson: String?): Pair<Long, String> {
        if (payloadJson.isNullOrBlank()) return 0L to ""
        val map = VortexJson.decodeStringMap(payloadJson)
        val baseMillis = map[KEY_BASE_SERVER_UPDATED_AT]?.toLongOrNull() ?: 0L
        val path = map[KEY_EDITED_FILE_PATH].orEmpty()
        return baseMillis to path
    }

    fun buildConflictCopyPath(originalPath: String): String {
        val slashIdx = originalPath.lastIndexOf('/')
        val dir = if (slashIdx >= 0) originalPath.substring(0, slashIdx + 1) else ""
        val fileName = if (slashIdx >= 0) originalPath.substring(slashIdx + 1) else originalPath
        val dotIdx = fileName.lastIndexOf('.')
        val copyName = if (dotIdx > 0) {
            "${fileName.substring(0, dotIdx)}_local_copy${fileName.substring(dotIdx)}"
        } else {
            "${fileName}_local_copy"
        }
        return "$dir$copyName"
    }
}
