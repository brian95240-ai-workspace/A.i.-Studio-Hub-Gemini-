package com.example.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectCacheDao {

    @Query("SELECT * FROM cached_projects WHERE cachedForUid = :uid ORDER BY updatedAtMillis DESC")
    fun observeProjectsForUid(uid: String): Flow<List<CachedProjectEntity>>

    @Query("SELECT COUNT(*) FROM cached_projects WHERE cachedForUid = :uid")
    fun observeProjectCountForUid(uid: String): Flow<Int>

    @Query("SELECT id FROM cached_projects WHERE cachedForUid = :uid AND pendingSync = 1")
    fun observeDirtyProjectIds(uid: String): Flow<List<String>>

    @Query("SELECT * FROM cached_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): CachedProjectEntity?

    @Query("SELECT id FROM cached_projects WHERE cachedForUid = :uid AND pendingSync = 1")
    suspend fun getDirtyProjectIds(uid: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: CachedProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<CachedProjectEntity>)

    @Query("UPDATE cached_projects SET pendingSync = 0 WHERE id = :projectId")
    suspend fun markSynced(projectId: String)

    @Query("DELETE FROM cached_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("DELETE FROM cached_projects WHERE cachedForUid = :uid AND pendingSync = 0 AND id NOT IN (:activeRemoteIds)")
    suspend fun deleteStaleSyncedProjects(uid: String, activeRemoteIds: List<String>)

    @Query("DELETE FROM cached_projects WHERE cachedForUid = :uid AND pendingSync = 0")
    suspend fun deleteAllSyncedProjectsForUid(uid: String)
}

data class ProjectCountRow(
    val projectId: String,
    val itemCount: Int
)

@Dao
interface PrdCacheDao {

    @Query("SELECT * FROM cached_prds WHERE projectId = :projectId ORDER BY majorVersion DESC, minorVersion DESC")
    fun observePrdsForProject(projectId: String): Flow<List<CachedPrdEntity>>

    @Query("SELECT * FROM cached_prds ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecentPrds(limit: Int = 20): Flow<List<CachedPrdEntity>>

    @Query("SELECT * FROM cached_prds ORDER BY majorVersion DESC, minorVersion DESC")
    fun observeAllPrds(): Flow<List<CachedPrdEntity>>

    @Query("SELECT projectId, COUNT(*) AS itemCount FROM cached_prds GROUP BY projectId")
    fun observePrdCountsByProject(): Flow<List<ProjectCountRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrd(prd: CachedPrdEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrds(prds: List<CachedPrdEntity>)

    @Query("DELETE FROM cached_prds WHERE projectId = :projectId")
    suspend fun deletePrdsForProject(projectId: String)

    @Transaction
    suspend fun replacePrdsForProject(projectId: String, prds: List<CachedPrdEntity>) {
        deletePrdsForProject(projectId)
        if (prds.isNotEmpty()) {
            upsertPrds(prds)
        }
    }
}

@Dao
interface RoundtableCacheDao {

    @Query("SELECT * FROM cached_roundtable_items WHERE projectId = :projectId ORDER BY createdAtMillis DESC")
    fun observeItemsForProject(projectId: String): Flow<List<CachedRoundtableItemEntity>>

    @Query("SELECT * FROM cached_roundtable_items ORDER BY updatedAtMillis DESC, createdAtMillis DESC LIMIT :limit")
    fun observeRecentItems(limit: Int = 20): Flow<List<CachedRoundtableItemEntity>>

    @Query("SELECT * FROM cached_roundtable_items ORDER BY updatedAtMillis DESC, createdAtMillis DESC")
    fun observeAllItems(): Flow<List<CachedRoundtableItemEntity>>

    @Query("SELECT projectId, COUNT(*) AS itemCount FROM cached_roundtable_items GROUP BY projectId")
    fun observeItemCountsByProject(): Flow<List<ProjectCountRow>>

    @Query("SELECT * FROM cached_roundtable_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): CachedRoundtableItemEntity?

    @Query("SELECT id FROM cached_roundtable_items WHERE projectId = :projectId AND pendingSync = 1")
    suspend fun getDirtyItemIds(projectId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: CachedRoundtableItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<CachedRoundtableItemEntity>)

    @Query("UPDATE cached_roundtable_items SET pendingSync = 0 WHERE id = :itemId")
    suspend fun markSynced(itemId: String)

    @Query("DELETE FROM cached_roundtable_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM cached_roundtable_items WHERE projectId = :projectId")
    suspend fun deleteItemsForProject(projectId: String)

    @Query(
        "DELETE FROM cached_roundtable_items " +
            "WHERE projectId = :projectId AND pendingSync = 0 AND id NOT IN (:activeRemoteIds)"
    )
    suspend fun deleteStaleSyncedItems(projectId: String, activeRemoteIds: List<String>)

    @Query("DELETE FROM cached_roundtable_items WHERE projectId = :projectId AND pendingSync = 0")
    suspend fun deleteAllSyncedItemsForProject(projectId: String)

    @Transaction
    suspend fun replaceItemId(oldId: String, newItem: CachedRoundtableItemEntity) {
        deleteItemById(oldId)
        upsertItem(newItem)
    }
}

@Dao
interface PendingSyncDao {

    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT DISTINCT projectId FROM pending_sync_ops")
    fun observePendingProjectIds(): Flow<List<String>>

    @Query("SELECT * FROM pending_sync_ops ORDER BY opId ASC")
    suspend fun getAllPendingOps(): List<PendingSyncOpEntity>

    @Query("SELECT targetEntityId FROM pending_sync_ops WHERE opType = :opType")
    suspend fun getTargetIdsByType(opType: String): List<String>

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE targetEntityId = :targetEntityId AND opType = :opType")
    suspend fun countByTargetAndType(targetEntityId: String, opType: String): Int

    @Query("SELECT * FROM pending_sync_ops WHERE targetEntityId = :targetEntityId AND opType = :opType LIMIT 1")
    suspend fun getOpByTargetAndType(targetEntityId: String, opType: String): PendingSyncOpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: PendingSyncOpEntity): Long

    @Query("DELETE FROM pending_sync_ops WHERE opId = :opId")
    suspend fun deleteById(opId: Long)

    @Query("DELETE FROM pending_sync_ops WHERE targetEntityId = :targetEntityId AND opType = :opType")
    suspend fun deleteByTargetAndType(targetEntityId: String, opType: String)

    @Query("DELETE FROM pending_sync_ops WHERE targetEntityId = :targetEntityId")
    suspend fun deleteAllForTarget(targetEntityId: String)

    @Query("DELETE FROM pending_sync_ops WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: String)

    @Query("UPDATE pending_sync_ops SET retryCount = retryCount + 1, lastError = :error WHERE opId = :opId")
    suspend fun incrementRetry(opId: Long, error: String)
}
