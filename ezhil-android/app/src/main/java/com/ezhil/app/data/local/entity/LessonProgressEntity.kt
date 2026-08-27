package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "lesson_progress",
    foreignKeys = [
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"]),
        ForeignKey(entity = LessonEntity::class,  parentColumns = ["id"], childColumns = ["lessonId"])
    ],
    indices = [Index("studentId"), Index("lessonId")]
)
data class LessonProgressEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val lessonId: String,
    val completedAt: String? = null,
    val quizScorePercent: Float? = null,
    val durationMs: Int? = null,
    val syncStatus: String = "pending",
    val createdAt: String = Instant.now().toString()
)
