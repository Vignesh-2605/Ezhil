package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.LessonProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonProgressDao {

    @Query("SELECT * FROM lesson_progress WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun observeByStudent(studentId: String): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE syncStatus = 'pending' LIMIT 100")
    suspend fun getPending(): List<LessonProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: LessonProgressEntity)

    @Query("UPDATE lesson_progress SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE lesson_progress SET syncStatus = 'conflict' WHERE id = :id")
    suspend fun markConflict(id: String)

    @Query("SELECT COUNT(*) FROM lesson_progress WHERE syncStatus = 'conflict'")
    fun observeConflictCount(): Flow<Int>

    @Query("UPDATE lesson_progress SET syncStatus = 'pending' WHERE syncStatus = 'conflict'")
    suspend fun retryConflicts()

    @Query("DELETE FROM lesson_progress WHERE syncStatus = 'conflict'")
    suspend fun deleteConflicts()
}
