package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey val id: String,
    val syncedAt: String = Instant.now().toString(),
    val pushCount: Int = 0,
    val pullCount: Int = 0,
    val error: String? = null
)
