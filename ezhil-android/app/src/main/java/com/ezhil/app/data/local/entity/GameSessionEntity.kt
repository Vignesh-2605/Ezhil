package com.ezhil.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "game_sessions",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("studentId")]
)
data class GameSessionEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val gameType: String,           // "match_sound"|"spot_letter"|"build_word"
    val playedAt: String = Instant.now().toString(),
    val roundsTotal: Int,
    val roundsCorrect: Int,
    val durationMs: Int,
    val errorMatrixJson: String = "{}",
    val difficultyLevel: Int = 1,
    val starsEarned: Int = 0,
    val syncStatus: String = "pending",
    val createdAt: String = Instant.now().toString()
)
