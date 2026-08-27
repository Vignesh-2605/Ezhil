package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "students",
    foreignKeys = [ForeignKey(
        entity = TeacherEntity::class,
        parentColumns = ["id"],
        childColumns = ["teacherId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("teacherId")]
)
data class StudentEntity(
    @PrimaryKey val id: String,
    val teacherId: String,
    val name: String,
    val dob: String? = null,
    val riskLevel: String = "unscreened",
    val streakDays: Int = 0,
    val lastActive: String? = null,
    val hashedPin: String? = null,
    val syncStatus: String = "pending",
    val createdAt: String = Instant.now().toString()
)
