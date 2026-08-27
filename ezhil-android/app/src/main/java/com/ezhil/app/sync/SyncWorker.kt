package com.ezhil.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.AssessmentEntity
import com.ezhil.app.data.local.entity.GameSessionEntity
import com.ezhil.app.data.local.entity.LessonEntity
import com.ezhil.app.data.local.entity.LessonProgressEntity
import com.ezhil.app.data.local.entity.StudentEntity
import com.ezhil.app.data.local.entity.SyncLogEntity
import com.ezhil.app.data.remote.EzhilApiService
import com.ezhil.app.data.remote.dto.SyncPushRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: EzhilDatabase,
    private val api: EzhilApiService,
    private val prefs: SecurePrefs
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "sync starting")

        // Every bail-out below used to be silent, which made a worker that ran
        // and did nothing indistinguishable from one that never ran at all.
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        if (network == null) {
            Log.w(TAG, "no active network — will retry")
            return Result.retry()
        }
        val caps = cm.getNetworkCapabilities(network)
        if (caps == null) {
            Log.w(TAG, "no network capabilities — will retry")
            return Result.retry()
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            Log.w(TAG, "network has no INTERNET capability — will retry")
            return Result.retry()
        }

        val token = try {
            prefs.authToken
        } catch (e: Exception) {
            // EncryptedSharedPreferences can throw if its keystore entry is
            // gone (app reinstalled, keys rotated) rather than returning null.
            Log.e(TAG, "could not read auth token from secure prefs", e)
            return Result.failure()
        }
        if (token == null) {
            Log.w(TAG, "no auth token — signed out, nothing to sync")
            return Result.failure()
        }

        var pushCount = 0
        var pullCount = 0

        try {
            pushCount = push()
            pullCount = pull()

            db.syncLogDao().insert(SyncLogEntity(
                id        = UUID.randomUUID().toString(),
                syncedAt  = lastServerTime ?: Instant.now().toString(),
                pushCount = pushCount,
                pullCount = pullCount
            ))
            Log.i(TAG, "sync ok — pushed $pushCount, pulled $pullCount")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            db.syncLogDao().insert(SyncLogEntity(
                id        = UUID.randomUUID().toString(),
                syncedAt  = Instant.now().toString(),
                pushCount = pushCount,
                pullCount = pullCount,
                error     = e.message
            ))
            return Result.retry()
        }
    }

    // ── PUSH ─────────────────────────────────────────────────────────────────
    // Rows are only marked synced after the server accepts them; rows the
    // server rejects (ownership mismatch) are marked 'conflict' so they stop
    // retrying but stay visible for debugging.

    private suspend fun push(): Int {
        var total = 0

        total += pushRows(
            table = "students",
            rows = db.studentDao().getPending().associateBy({ it.id }, { it.toSyncRow() }),
            onSynced = { db.studentDao().markSynced(it) },
            onConflict = { db.studentDao().markConflict(it) },
        )
        total += pushRows(
            table = "assessments",
            rows = db.assessmentDao().getPending().associateBy({ it.id }, { it.toSyncRow() }),
            onSynced = { db.assessmentDao().markSynced(it) },
            onConflict = { db.assessmentDao().markConflict(it) },
        )
        total += pushRows(
            table = "game_sessions",
            rows = db.gameSessionDao().getPending().associateBy({ it.id }, { it.toSyncRow() }),
            onSynced = { db.gameSessionDao().markSynced(it) },
            onConflict = { db.gameSessionDao().markConflict(it) },
        )
        total += pushRows(
            table = "lesson_progress",
            rows = db.lessonProgressDao().getPending().associateBy({ it.id }, { it.toSyncRow() }),
            onSynced = { db.lessonProgressDao().markSynced(it) },
            onConflict = { db.lessonProgressDao().markConflict(it) },
        )
        // Lessons are teacher-authored content. Without this push a lesson
        // published on the tablet never leaves it, so no student on any other
        // device can ever receive it.
        total += pushRows(
            table = "lessons",
            rows = db.lessonDao().getPending().associateBy({ it.id }, { it.toSyncRow() }),
            onSynced = { db.lessonDao().markSynced(it) },
            onConflict = { db.lessonDao().markConflict(it) },
        )

        return total
    }

    private suspend fun pushRows(
        table: String,
        rows: Map<String, Map<String, Any?>>,
        onSynced: suspend (String) -> Unit,
        onConflict: suspend (String) -> Unit,
    ): Int {
        if (rows.isEmpty()) return 0

        val response = api.push(SyncPushRequest(table, rows.values.toList()))
        val conflicted = response.conflicts.toSet()

        for (id in rows.keys) {
            if (id in conflicted) onConflict(id) else onSynced(id)
        }
        if (conflicted.isNotEmpty()) {
            Log.w(TAG, "$table: server rejected ${conflicted.size} row(s): $conflicted")
        }
        return rows.size - conflicted.size
    }

    // ── PULL ─────────────────────────────────────────────────────────────────

    private var lastServerTime: String? = null

    /**
     * Guarantee schools → teachers exists for [teacherId] so roster inserts
     * satisfy their foreign key. Uses the signed-in session as the source of
     * truth; fills only what the session actually knows and never invents
     * identifiers.
     */
    private suspend fun ensureTeacherRow(teacherId: String) {
        if (db.teacherDao().findById(teacherId) != null) return

        val schoolId = prefs.schoolId
        if (schoolId == null) {
            Log.w(TAG, "teacher $teacherId missing locally and no schoolId in session — roster will be skipped")
            return
        }
        Log.i(TAG, "rebuilding missing local teacher row for $teacherId")

        if (db.schoolDao().findById(schoolId) == null) {
            db.schoolDao().upsert(
                com.ezhil.app.data.local.entity.SchoolEntity(
                    id         = schoolId,
                    name       = prefs.schoolName ?: "",
                    district   = prefs.district ?: "",
                    schoolCode = prefs.schoolCode ?: "",
                )
            )
        }
        // teacherCode is UNIQUE. If a stale row (e.g. the demo seed) holds it,
        // @Upsert quietly does nothing: the INSERT trips the secondary index,
        // and the UPDATE fallback matches on primary key, which doesn't exist.
        // No row, no exception. Clear the holder first.
        val code = prefs.teacherCode ?: teacherId
        db.teacherDao().deleteStaleDuplicates(code = code, keepId = teacherId)
        db.teacherDao().upsert(
            com.ezhil.app.data.local.entity.TeacherEntity(
                id          = teacherId,
                schoolId    = schoolId,
                teacherCode = code,
                name        = prefs.teacherName ?: "",
                className   = prefs.className ?: "",
                schoolCode  = prefs.schoolCode ?: "",
                syncStatus  = "synced",
            )
        )
    }

    private suspend fun pull(): Int {
        val lastSync = db.syncLogDao().getLastSuccessful()?.syncedAt ?: "1970-01-01T00:00:00Z"
        val response = api.pull(lastSync)
        lastServerTime = response.serverTime.ifBlank { null }
        var total = 0

        for (dto in response.lessons) {
            // upsert is REPLACE, so every column must be supplied. The DTO does
            // not carry sourceHash/titleEn/lessonType/assignedTo/cacheHit —
            // read them off the existing row or they are silently wiped.
            val existing = db.lessonDao().getById(dto.id)
            db.lessonDao().upsert(
                LessonEntity(
                    id          = dto.id,
                    teacherId   = dto.teacherId ?: existing?.teacherId,
                    sourceHash  = existing?.sourceHash,
                    title       = dto.title,
                    titleEn     = existing?.titleEn,
                    lessonType  = existing?.lessonType ?: "story",
                    difficulty  = dto.difficulty,
                    language    = dto.language,
                    contentJson = dto.contentJson,
                    isPublished = dto.isPublished,
                    assignedTo  = existing?.assignedTo ?: "class",
                    cacheHit    = existing?.cacheHit ?: false,
                    createdAt   = existing?.createdAt ?: Instant.now().toString(),
                    syncStatus  = "synced"
                )
            )
            total++
        }
        // The roster's foreign key points at teachers(id). If that row is
        // missing — a login that half-persisted, or app data cleared while the
        // session survived in prefs — every student insert throws
        // SQLITE_CONSTRAINT_FOREIGNKEY and takes the whole sync down with it.
        // Rebuild the parent chain from the session before writing the roster.
        var rosterWritable = true
        if (response.roster.isNotEmpty()) {
            val parentId = response.roster.first().teacherId
            ensureTeacherRow(parentId)
            // Confirm it actually landed. A silent @Upsert no-op would
            // otherwise surface as an opaque FK error on every student row and
            // abort the entire sync, including the lessons pulled above.
            if (db.teacherDao().findById(parentId) == null) {
                Log.e(TAG, "roster parent teacher $parentId still absent after rebuild " +
                        "— skipping roster this cycle (lessons still synced)")
                rosterWritable = false
            }
        }

        for (dto in if (rosterWritable) response.roster else emptyList()) {
            // Preserve locally-tracked fields (streak, last active, PIN) —
            // the server is only authoritative for identity and risk level.
            val existing = db.studentDao().getById(dto.id)
            db.studentDao().upsert(
                StudentEntity(
                    id         = dto.id,
                    teacherId  = dto.teacherId,
                    name       = dto.name,
                    dob        = dto.dob ?: existing?.dob,
                    riskLevel  = dto.riskLevel,
                    streakDays = existing?.streakDays ?: 0,
                    lastActive = existing?.lastActive,
                    hashedPin  = existing?.hashedPin,
                    createdAt  = existing?.createdAt ?: Instant.now().toString(),
                    syncStatus = "synced"
                )
            )
            total++
        }

        return total
    }

    companion object {
        private const val TAG = "EzhilSync"
        const val WORK_NAME = "ezhil_sync"

        const val ONE_SHOT_WORK_NAME = "ezhil_sync_now"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // Any internet connection
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .build()

            // UPDATE, not KEEP. Repeated failures drive WorkManager's
            // exponential backoff out to half an hour or more, and under KEEP
            // a relaunch cannot reset it — the job fires, WorkManager checks
            // its own backoff, and finishes without ever calling doWork().
            // UPDATE lets opening the app recover a wedged worker.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        /**
         * Run a sync immediately instead of waiting up to 15 minutes for the
         * periodic worker. Called after login and after publishing, where the
         * user reasonably expects their data to reach the server now.
         */
        fun syncNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    ONE_SHOT_WORK_NAME,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}

// ── Entity → sync payload mappers ────────────────────────────────────────────
// Keys are the server's snake_case column names. Client-only fields
// (syncStatus, hashedPin) are never sent.

internal fun StudentEntity.toSyncRow(): Map<String, Any?> = mapOf(
    "id"          to id,
    "teacher_id"  to teacherId,
    "name"        to name,
    "dob"         to dob,
    "risk_level"  to riskLevel,
    "streak_days" to streakDays,
    "last_active" to lastActive,
    "created_at"  to createdAt,
)

internal fun AssessmentEntity.toSyncRow(): Map<String, Any?> = mapOf(
    "id"                   to id,
    "student_id"           to studentId,
    "conducted_at"         to conductedAt,
    "reading_speed_wpm"    to readingSpeedWpm,
    "phoneme_error_rate"   to phonemeErrorRate,
    "letter_reversal_rate" to letterReversalRate,
    "syllable_skip_rate"   to syllableSkipRate,
    "lip_sync_confidence"  to lipSyncConfidence,
    "cnn_risk_score"       to cnnRiskScore,
    "risk_level"           to riskLevel,
    "error_tags_json"      to errorTagsJson,
    "audio_duration_ms"    to audioDurationMs,
    "model_version"        to modelVersion,
    "created_at"           to createdAt,
)

internal fun GameSessionEntity.toSyncRow(): Map<String, Any?> = mapOf(
    "id"                to id,
    "student_id"        to studentId,
    "game_type"         to gameType,
    "played_at"         to playedAt,
    "rounds_total"      to roundsTotal,
    "rounds_correct"    to roundsCorrect,
    "duration_ms"       to durationMs,
    "error_matrix_json" to errorMatrixJson,
    "difficulty_level"  to difficultyLevel,
    "stars_earned"      to starsEarned,
    "created_at"        to createdAt,
)

internal fun LessonEntity.toSyncRow(): Map<String, Any?> = mapOf(
    "id"           to id,
    "teacher_id"   to teacherId,
    "source_hash"  to sourceHash,
    "title"        to title,
    "title_en"     to titleEn,
    "lesson_type"  to lessonType,
    "difficulty"   to difficulty,
    "language"     to language,
    "content_json" to contentJson,
    "is_published" to isPublished,
    "assigned_to"  to assignedTo,
    "cache_hit"    to cacheHit,
    "created_at"   to createdAt,
)

internal fun LessonProgressEntity.toSyncRow(): Map<String, Any?> = mapOf(
    "id"                 to id,
    "student_id"         to studentId,
    "lesson_id"          to lessonId,
    "completed_at"       to completedAt,
    "quiz_score_percent" to quizScorePercent,
    "duration_ms"        to durationMs,
    "created_at"         to createdAt,
)
