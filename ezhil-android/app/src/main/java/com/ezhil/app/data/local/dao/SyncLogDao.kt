package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.SyncLogEntity

@Dao
interface SyncLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity)

    @Query("SELECT * FROM sync_log WHERE error IS NULL ORDER BY syncedAt DESC LIMIT 1")
    suspend fun getLastSuccessful(): SyncLogEntity?
}
