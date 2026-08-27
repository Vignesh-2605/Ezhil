package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "schools")
data class SchoolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val district: String,
    val schoolCode: String = "",
    val createdAt: String = Instant.now().toString()
)
