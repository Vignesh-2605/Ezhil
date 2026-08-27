package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "assessments",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("studentId")]
)
data class AssessmentEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val conductedAt: String = Instant.now().toString(),
    val readingSpeedWpm: Float? = null,
    val phonemeErrorRate: Float? = null,
    val letterReversalRate: Float? = null,
    val syllableSkipRate: Float? = null,
    val lipSyncConfidence: Float? = null,
    val cnnRiskScore: Float? = null,       // TEACHER-ONLY — never pass to student composables
    val riskLevel: String,
    val errorTagsJson: String? = null,
    val audioDurationMs: Int? = null,
    val modelVersion: String = "1.0.0",
    val syncStatus: String = "pending",
    val createdAt: String = Instant.now().toString()
)
