package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Query("SELECT * FROM students WHERE teacherId = :teacherId ORDER BY name ASC")
    fun observeByTeacher(teacherId: String): Flow<List<StudentEntity>>

    /** Duplicate check for the roster. Case-insensitive: "Kavin" and "kavin"
     *  are the same child to a teacher typing quickly. */
    @Query("SELECT COUNT(*) FROM students WHERE teacherId = :teacherId AND name = :name COLLATE NOCASE")
    suspend fun countByTeacherAndName(teacherId: String, name: String): Int

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: String): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE syncStatus = 'pending' LIMIT 100")
    suspend fun getPending(): List<StudentEntity>

    // @Upsert, not @Insert(REPLACE): REPLACE deletes the row first, which
    // cascades away child rows (assessments, game_sessions) and trips the
    // NO ACTION foreign key on lesson_progress. Sync re-writes these rows
    // constantly, so REPLACE meant data loss or a crash on every pull.
    @Upsert
    suspend fun upsert(student: StudentEntity)

    @Query("UPDATE students SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE students SET syncStatus = 'conflict' WHERE id = :id")
    suspend fun markConflict(id: String)

    @Query("SELECT COUNT(*) FROM students WHERE syncStatus = 'conflict'")
    fun observeConflictCount(): Flow<Int>

    @Query("UPDATE students SET syncStatus = 'pending' WHERE syncStatus = 'conflict'")
    suspend fun retryConflicts()

    @Query("DELETE FROM students WHERE syncStatus = 'conflict'")
    suspend fun deleteConflicts()

    @Query("UPDATE students SET riskLevel = :riskLevel WHERE id = :id")
    suspend fun updateRiskLevel(id: String, riskLevel: String)

    @Query("UPDATE students SET streakDays = streakDays + 1, lastActive = :today WHERE id = :id AND (lastActive IS NULL OR lastActive != :today)")
    suspend fun incrementStreak(id: String, today: String)

    @Delete
    suspend fun delete(student: StudentEntity)

    @Query("SELECT * FROM students WHERE UPPER(name) = UPPER(:name) LIMIT 1")
    suspend fun findByName(name: String): StudentEntity?

    /**
     * Match on the first word of the name, which is what a child actually
     * types — "Kavin", not "Kavin S.". The web client matches first-name-only
     * server-side, so requiring the full name here meant the same child signed
     * in differently depending on the device.
     *
     * Anchored to the first word rather than a prefix scan: "Kav" must not
     * match "Kavin".
     */
    @Query("""
        SELECT * FROM students
        WHERE teacherId = :teacherId
          AND UPPER(TRIM(SUBSTR(name, 1, CASE
                WHEN INSTR(name, ' ') > 0 THEN INSTR(name, ' ') - 1
                ELSE LENGTH(name) END))) = UPPER(TRIM(:firstName))
        LIMIT 1
    """)
    suspend fun findByFirstName(teacherId: String, firstName: String): StudentEntity?
}
