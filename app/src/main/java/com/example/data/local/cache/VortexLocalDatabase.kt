package com.example.data.local.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedProjectEntity::class,
        CachedPrdEntity::class,
        CachedRoundtableItemEntity::class,
        PendingSyncOpEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VortexLocalDatabase : RoomDatabase() {

    abstract fun projectCacheDao(): ProjectCacheDao
    abstract fun prdCacheDao(): PrdCacheDao
    abstract fun roundtableCacheDao(): RoundtableCacheDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile
        private var INSTANCE: VortexLocalDatabase? = null

        fun getInstance(context: Context): VortexLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VortexLocalDatabase::class.java,
                    "vortex_local_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
