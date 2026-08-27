package com.ezhil.app.sync

import com.ezhil.app.data.local.entity.AssessmentEntity
import com.ezhil.app.data.local.entity.GameSessionEntity
import com.ezhil.app.data.local.entity.LessonEntity
import com.ezhil.app.data.local.entity.LessonProgressEntity
import com.ezhil.app.data.local.entity.StudentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The push payload keys must match the FastAPI SQLAlchemy column names
 * exactly — unknown keys are silently stripped server-side, so a typo here
 * means silently dropped data (the original SyncWorker bug class).
 */
class SyncRowMappersTest {

    @Test
    fun `student row uses server column names and omits client-only fields`() {
        val row = StudentEntity(
            id = "s1", teacherId = "t1", name = "Kavin S.", dob = "2016-05-12",
            riskLevel = "low", streakDays = 3, lastActive = "2026-07-01",
            hashedPin = "SECRET", syncStatus = "pending",
        ).toSyncRow()

        assertEquals(
            setOf("id", "teacher_id", "name", "dob", "risk_level",
                  "streak_days", "last_active", "created_at"),
            row.keys
        )
        assertEquals("t1", row["teacher_id"])
        // The PIN hash must never leave the device via sync.
        assertFalse(row.values.contains("SECRET"))
    }

    @Test
    fun `assessment row carries all screening metrics`() {
        val row = AssessmentEntity(
            id = "a1", studentId = "s1", riskLevel = "low",
            phonemeErrorRate = 0.2f, syllableSkipRate = 0.1f,
            cnnRiskScore = 0.15f, modelVersion = "heuristic-cnn-1.0",
        ).toSyncRow()

        assertEquals("s1", row["student_id"])
        assertEquals(0.2f, row["phoneme_error_rate"])
        assertEquals("heuristic-cnn-1.0", row["model_version"])
        assertFalse(row.containsKey("syncStatus"))
        assertFalse(row.containsKey("studentId")) // camelCase must not leak
    }

    @Test
    fun `game session row maps snake_case`() {
        val row = GameSessionEntity(
            id = "g1", studentId = "s1", gameType = "match_sound",
            roundsTotal = 3, roundsCorrect = 2, durationMs = 12000,
            starsEarned = 2,
        ).toSyncRow()

        assertEquals("match_sound", row["game_type"])
        assertEquals(3, row["rounds_total"])
        assertEquals(2, row["stars_earned"])
    }

    @Test
    fun `lesson row carries publish state to the server`() {
        // Without is_published crossing the wire, a lesson published on the
        // tablet never reaches a student on any other device.
        val row = LessonEntity(
            id = "l1", teacherId = "t1", title = "யானையும் எறும்பும்",
            contentJson = """{"title":"x"}""", isPublished = true,
            lessonType = "story", difficulty = 2, language = "tamil",
            sourceHash = "abc123", assignedTo = "class", syncStatus = "pending",
        ).toSyncRow()

        assertEquals(true, row["is_published"])
        assertEquals("t1", row["teacher_id"])
        assertEquals("abc123", row["source_hash"])
        assertEquals("""{"title":"x"}""", row["content_json"])
        assertEquals(2, row["difficulty"])
        assertFalse(row.containsKey("syncStatus"))
        assertFalse(row.containsKey("isPublished")) // camelCase must not leak
    }

    @Test
    fun `lesson progress row maps snake_case`() {
        val row = LessonProgressEntity(
            id = "p1", studentId = "s1", lessonId = "l1",
            quizScorePercent = 80f, durationMs = 60000,
        ).toSyncRow()

        assertEquals("l1", row["lesson_id"])
        assertEquals(80f, row["quiz_score_percent"])
    }
}
