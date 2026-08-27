package com.ezhil.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ezhil.app.data.local.dao.*
import com.ezhil.app.data.local.entity.*

@Database(
    entities = [
        SchoolEntity::class,
        TeacherEntity::class,
        StudentEntity::class,
        AssessmentEntity::class,
        GameSessionEntity::class,
        LessonEntity::class,
        LessonProgressEntity::class,
        SyncLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class EzhilDatabase : RoomDatabase() {
    abstract fun schoolDao(): SchoolDao
    abstract fun teacherDao(): TeacherDao
    abstract fun studentDao(): StudentDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun gameSessionDao(): GameSessionDao
    abstract fun lessonDao(): LessonDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        const val DATABASE_NAME = "ezhil.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE teachers ADD COLUMN schoolCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE teachers ADD COLUMN hashedPin TEXT")
                db.execSQL("ALTER TABLE schools ADD COLUMN schoolCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE students ADD COLUMN hashedPin TEXT")
            }
        }
    }
}
