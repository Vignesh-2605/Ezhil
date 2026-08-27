package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "teachers",
    foreignKeys = [ForeignKey(
        entity = SchoolEntity::class,
        parentColumns = ["id"],
        childColumns = ["schoolId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("schoolId"), Index("teacherCode", unique = true)]
)
data class TeacherEntity(
    @PrimaryKey val id: String,
    val schoolId: String,
    val teacherCode: String,
    val name: String,
    val className: String,
    val schoolCode: String = "",
    val hashedPin: String? = null,
    val syncStatus: String = "pending",
    val createdAt: String = Instant.now().toString()
)
