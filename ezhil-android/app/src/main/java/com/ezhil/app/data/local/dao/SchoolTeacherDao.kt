package com.ezhil.app.data.local.dao

import androidx.room.*
import com.ezhil.app.data.local.entity.SchoolEntity
import com.ezhil.app.data.local.entity.TeacherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolDao {
    // @Upsert, not @Insert(REPLACE): REPLACE deletes the row first, which
    // cascades away child rows (assessments, game_sessions) and trips the
    // NO ACTION foreign key on lesson_progress. Sync re-writes these rows
    // constantly, so REPLACE meant data loss or a crash on every pull.
    @Upsert
    suspend fun upsert(school: SchoolEntity)

    @Query("SELECT * FROM schools WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SchoolEntity?

    @Query("SELECT * FROM schools WHERE schoolCode = :code LIMIT 1")
    suspend fun findByCode(code: String): SchoolEntity?
}

@Dao
interface TeacherDao {
    // @Upsert, not @Insert(REPLACE): REPLACE deletes the row first, which
    // cascades away child rows (assessments, game_sessions) and trips the
    // NO ACTION foreign key on lesson_progress. Sync re-writes these rows
    // constantly, so REPLACE meant data loss or a crash on every pull.
    @Upsert
    suspend fun upsert(teacher: TeacherEntity)

    @Query("SELECT * FROM teachers WHERE id = :id")
    fun observeById(id: String): Flow<TeacherEntity?>

    @Query("SELECT * FROM teachers WHERE syncStatus = 'pending' LIMIT 100")
    suspend fun getPending(): List<TeacherEntity>

    @Query("UPDATE teachers SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM teachers WHERE schoolCode = :schoolCode AND teacherCode = :teacherCode LIMIT 1")
    suspend fun findBySchoolAndCode(schoolCode: String, teacherCode: String): TeacherEntity?

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TeacherEntity?

    /**
     * teacherCode carries a UNIQUE index, so the same teacher arriving under a
     * new server id (demo seed vs. real login) collides on insert. Clear the
     * stale identity first; its students cascade away and are re-pulled by sync.
     */
    @Query("DELETE FROM teachers WHERE teacherCode = :code AND id != :keepId")
    suspend fun deleteStaleDuplicates(code: String, keepId: String)
}
