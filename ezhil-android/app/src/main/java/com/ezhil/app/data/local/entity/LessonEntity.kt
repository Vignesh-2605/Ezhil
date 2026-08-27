package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val teacherId: String? = null,
    val sourceHash: String? = null,
    val title: String,
    val titleEn: String? = null,
    val lessonType: String = "story",     // "story"|"vocabulary"|"comprehension"|"listen_repeat"
    val difficulty: Int = 1,
    val language: String = "tamil",       // "tamil"|"english"|"both"
    val contentJson: String,
    val isPublished: Boolean = false,
    val assignedTo: String = "class",
    val cacheHit: Boolean = false,
    val syncStatus: String = "pending",
    val createdAt: String = Instant.now().toString()
)
