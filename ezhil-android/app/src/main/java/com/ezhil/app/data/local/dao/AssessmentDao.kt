package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.AssessmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentDao {

    @Query("SELECT * FROM assessments WHERE studentId = :studentId ORDER BY conductedAt DESC")
    fun observeByStudent(studentId: String): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE studentId = :studentId ORDER BY conductedAt DESC LIMIT 1")
    suspend fun getLatestForStudent(studentId: String): AssessmentEntity?

    @Query("SELECT * FROM assessments WHERE studentId IN (:studentIds) ORDER BY conductedAt DESC")
    fun observeByStudentIds(studentIds: List<String>): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE syncStatus = 'pending' LIMIT 100")
    suspend fun getPending(): List<AssessmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: AssessmentEntity)

    @Query("UPDATE assessments SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE assessments SET syncStatus = 'conflict' WHERE id = :id")
    suspend fun markConflict(id: String)

    @Query("SELECT COUNT(*) FROM assessments WHERE syncStatus = 'conflict'")
    fun observeConflictCount(): Flow<Int>

    @Query("UPDATE assessments SET syncStatus = 'pending' WHERE syncStatus = 'conflict'")
    suspend fun retryConflicts()

    @Query("DELETE FROM assessments WHERE syncStatus = 'conflict'")
    suspend fun deleteConflicts()
}
