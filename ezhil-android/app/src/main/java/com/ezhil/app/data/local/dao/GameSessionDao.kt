package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.GameSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSessionDao {

    @Query("SELECT * FROM game_sessions WHERE studentId = :studentId ORDER BY playedAt DESC")
    fun observeByStudent(studentId: String): Flow<List<GameSessionEntity>>

    @Query("SELECT * FROM game_sessions WHERE syncStatus = 'pending' LIMIT 100")
    suspend fun getPending(): List<GameSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: GameSessionEntity)

    @Query("UPDATE game_sessions SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE game_sessions SET syncStatus = 'conflict' WHERE id = :id")
    suspend fun markConflict(id: String)

    @Query("SELECT COUNT(*) FROM game_sessions WHERE syncStatus = 'conflict'")
    fun observeConflictCount(): Flow<Int>

    @Query("UPDATE game_sessions SET syncStatus = 'pending' WHERE syncStatus = 'conflict'")
    suspend fun retryConflicts()

    @Query("DELETE FROM game_sessions WHERE syncStatus = 'conflict'")
    suspend fun deleteConflicts()
}
