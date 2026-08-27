@file:Suppress("ANNOTATION_TARGETS_NON_EXISTENT_ACCESSOR")

package com.ezhil.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── AUTH ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @field:Json(name = "school_code") val schoolCode: String,
    @field:Json(name = "teacher_id")  val teacherId: String,
    val pin: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @field:Json(name = "school_code")  val schoolCode: String,
    @field:Json(name = "school_name")  val schoolName: String,
    val district: String = "",
    @field:Json(name = "teacher_code") val teacherCode: String,
    @field:Json(name = "teacher_name") val teacherName: String,
    @field:Json(name = "class_name")   val className: String,
    val pin: String
)

// ── STUDENT AUTH ──────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StudentLoginRequest(
    @field:Json(name = "school_code")  val schoolCode: String,
    @field:Json(name = "student_code") val studentCode: String,
    val pin: String
)

@JsonClass(generateAdapter = true)
data class StudentLoginResponse(
    @field:Json(name = "access_token") val accessToken: String,
    @field:Json(name = "student_id")   val studentId: String,
    @field:Json(name = "student_name") val studentName: String,
    @field:Json(name = "school_name")  val schoolName: String,
    @field:Json(name = "teacher_name") val teacherName: String,
    @field:Json(name = "class_name")   val className: String,
    @field:Json(name = "risk_level")   val riskLevel: String,
    val dob: String = ""
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @field:Json(name = "access_token")  val accessToken: String,
    @field:Json(name = "teacher_id")    val teacherId: String,
    @field:Json(name = "school_id")     val schoolId: String,
    @field:Json(name = "teacher_name")  val teacherName: String,
    @field:Json(name = "school_name")   val schoolName: String,
    @field:Json(name = "class_name")    val className: String,
    @field:Json(name = "district")      val district: String
)

// ── SYNC ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    val table: String,
    val rows: List<Map<String, Any?>>
)

@JsonClass(generateAdapter = true)
data class SyncPushResponse(
    val accepted: Int,
    val conflicts: List<String>
)

@JsonClass(generateAdapter = true)
data class SyncPullResponse(
    @field:Json(name = "server_time") val serverTime: String = "",
    val lessons: List<LessonDto>,
    val roster: List<StudentDto>
)

@JsonClass(generateAdapter = true)
data class LessonDto(
    val id: String,
    val title: String,
    @field:Json(name = "content_json") val contentJson: String,
    val difficulty: Int = 1,
    val language: String = "tamil",
    @field:Json(name = "is_published") val isPublished: Boolean = true,
    @field:Json(name = "teacher_id") val teacherId: String? = null
)

@JsonClass(generateAdapter = true)
data class StudentDto(
    val id: String,
    val name: String,
    @field:Json(name = "teacher_id") val teacherId: String,
    val dob: String? = null,
    @field:Json(name = "risk_level") val riskLevel: String = "unscreened"
)

// ── STUDIO ───────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class OcrResponse(
    @field:Json(name = "extracted_text") val extractedText: String,
    @field:Json(name = "char_count")     val charCount: Int = 0,
    val confidence: Float = 0f,
    @field:Json(name = "minimum_confidence") val minimumConfidence: Float = 0f,
    @field:Json(name = "ocr_engine")     val ocrEngine: String? = null,
    @field:Json(name = "source_hash")    val sourceHash: String? = null,
    /** The text is usable but a teacher must check it before it becomes a lesson. */
    @field:Json(name = "requires_review") val requiresReview: Boolean = false,
    @field:Json(name = "review_reason")  val reviewReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateRequest(
    @field:Json(name = "ocr_text")     val ocrText: String,
    val difficulty: Int,
    val language: String,
    @field:Json(name = "source_hash")  val sourceHash: String,
    // Reported so the server can judge the extraction even when the text was
    // recombined on device and matches no cached hash.
    @field:Json(name = "average_confidence") val averageConfidence: Float? = null,
    @field:Json(name = "minimum_confidence") val minimumConfidence: Float? = null,
    @field:Json(name = "ocr_engine")   val ocrEngine: String? = null,
    /** Set once the teacher has read, and possibly corrected, the text. */
    @field:Json(name = "text_reviewed") val textReviewed: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GenerateResponse(
    @field:Json(name = "lesson_id")    val lessonId: String,
    val lesson: LessonContent,
    @field:Json(name = "cache_hit")    val cacheHit: Boolean,
    @field:Json(name = "source_hash")  val sourceHash: String,
    @field:Json(name = "token_count")  val tokenCount: Int = 0
)

// ── MULTI-FILE EXTRACTION ────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FileExtractionResult(
    val filename: String,
    @field:Json(name = "file_type")      val fileType: String,
    @field:Json(name = "extracted_text") val extractedText: String,
    @field:Json(name = "char_count")     val charCount: Int,
    val status: String,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ExtractMultiResponse(
    @field:Json(name = "combined_text") val combinedText: String,
    @field:Json(name = "total_chars")   val totalChars: Int,
    val files: List<FileExtractionResult>,
    @field:Json(name = "source_hash")   val sourceHash: String
)

// ── LESSON CONTENT ───────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LessonContent(
    val title: String,
    val passage: LessonPassage,
    val vocabulary: List<LessonVocabEntry> = emptyList(),
    val quiz: List<LessonQuizItem> = emptyList(),
    val metadata: LessonMetadata? = null
)

@JsonClass(generateAdapter = true)
data class LessonPassage(
    val lines: List<String>,
    @field:Json(name = "line_count") val lineCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class LessonVocabEntry(
    val word: String,
    val syllables: List<String> = emptyList(),
    @field:Json(name = "meaning_ta") val meaningTa: String,
    @field:Json(name = "meaning_en") val meaningEn: String,
    @field:Json(name = "audio_hint") val audioHint: String? = null
)

@JsonClass(generateAdapter = true)
data class LessonQuizItem(
    @field:Json(name = "question_ta") val questionTa: String,
    @field:Json(name = "question_en") val questionEn: String? = null,
    @field:Json(name = "options_ta")  val optionsTa: List<String>,
    @field:Json(name = "options_en")  val optionsEn: List<String>? = null,
    @field:Json(name = "correct_index") val correctIndex: Int
)

@JsonClass(generateAdapter = true)
data class LessonMetadata(
    val source: String = "generated",
    val difficulty: Int = 1,
    val language: String = "tamil",
    @field:Json(name = "generated_at") val generatedAt: String? = null
)
