package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons WHERE isPublished = 1 ORDER BY createdAt DESC")
    fun observePublished(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE teacherId = :teacherId ORDER BY createdAt DESC")
    fun observeByTeacher(teacherId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getById(id: String): LessonEntity?

    @Query("SELECT * FROM lessons WHERE sourceHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): LessonEntity?

    @Query("SELECT * FROM lessons WHERE syncStatus = 'pending' LIMIT 100")
    suspend fun getPending(): List<LessonEntity>

    // @Upsert, not @Insert(REPLACE): REPLACE deletes the row first, which
    // cascades away child rows (assessments, game_sessions) and trips the
    // NO ACTION foreign key on lesson_progress. Sync re-writes these rows
    // constantly, so REPLACE meant data loss or a crash on every pull.
    @Upsert
    suspend fun upsert(lesson: LessonEntity)

    @Query("UPDATE lessons SET isPublished = 1, syncStatus = 'pending' WHERE id = :id")
    suspend fun publish(id: String)

    @Query("UPDATE lessons SET isPublished = 0, syncStatus = 'pending' WHERE id = :id")
    suspend fun unpublish(id: String)

    @Query("UPDATE lessons SET contentJson = :contentJson WHERE id = :id")
    suspend fun updateContent(id: String, contentJson: String)

    @Query("UPDATE lessons SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE lessons SET syncStatus = 'conflict' WHERE id = :id")
    suspend fun markConflict(id: String)

    @Delete
    suspend fun delete(lesson: LessonEntity)
}
