package com.ezhil.app.data.local

import com.ezhil.app.data.local.entity.LessonEntity
import com.ezhil.app.data.local.entity.SchoolEntity
import com.ezhil.app.data.local.entity.StudentEntity
import com.ezhil.app.data.local.entity.TeacherEntity

// Fixed IDs for demo entities — lets upsert correct them on every launch.
private const val DEMO_SCHOOL_ID   = "demo-school-001"
private const val DEMO_TEACHER_ID  = "demo-teacher-001"

/**
 * Inserts/corrects demo data on every launch.
 *
 * Uses fixed IDs so upsert always overwrites stale rows (e.g. old Tamil-name
 * students from a previous build). Safe to call repeatedly.
 *
 * Demo teacher  : SCH-001 / T-0042 / PIN 1234
 * Demo students : name typed exactly as shown below, PIN = birthday MMDD
 */
suspend fun seedDemoDataIfNeeded(db: EzhilDatabase) {

    // Stand down once a real account holds this teacherCode. The column is
    // UNIQUE, so seeding on top of a signed-in teacher cannot insert the demo
    // row — and the demo students that reference it then fail their foreign
    // key, killing this coroutine on every launch. Real data always wins.
    val existing = db.teacherDao().findBySchoolAndCode("SCH-001", "T-0042")
    if (existing != null && existing.id != DEMO_TEACHER_ID) return

    // ── School ────────────────────────────────────────────────────────────────
    db.schoolDao().upsert(
        SchoolEntity(
            id         = DEMO_SCHOOL_ID,
            name       = "Govt. Primary School, Madurai",
            district   = "Madurai",
            schoolCode = "SCH-001",
        )
    )

    // ── Teacher  (PIN = 1234) ─────────────────────────────────────────────────
    db.teacherDao().upsert(
        TeacherEntity(
            id          = DEMO_TEACHER_ID,
            schoolId    = DEMO_SCHOOL_ID,
            teacherCode = "T-0042",
            name        = "மீனாட்சி",
            className   = "Grade 2",
            schoolCode  = "SCH-001",
            hashedPin   = hashPin("1234"),
            syncStatus  = "synced",
        )
    )

    // ── Students  (name = what student types, PIN = birthday MMDD) ───────────
    val students = listOf(
        Triple("demo-stu-001", "ANBU",  "2016-05-12"),   // PIN: 0512
        Triple("demo-stu-002", "KAVYA", "2016-03-20"),   // PIN: 0320
        Triple("demo-stu-003", "RAJAN", "2015-08-10"),   // PIN: 0810
        Triple("demo-stu-004", "PRIYA", "2016-01-05"),   // PIN: 0105
        Triple("demo-stu-005", "MUTHU", "2015-11-22"),   // PIN: 1122
    )
    for ((id, name, dob) in students) {
        db.studentDao().upsert(
            StudentEntity(
                id         = id,
                teacherId  = DEMO_TEACHER_ID,
                name       = name,
                dob        = dob,
                riskLevel  = "unscreened",
                syncStatus = "synced",
            )
        )
    }

    // ── Demo lessons ──────────────────────────────────────────────────────────
    for (lesson in demoLessons(DEMO_TEACHER_ID)) {
        db.lessonDao().upsert(lesson)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lesson content
// ─────────────────────────────────────────────────────────────────────────────

private fun demoLessons(teacherId: String): List<LessonEntity> = listOf(

    LessonEntity(
        id          = "demo-lesson-001",
        teacherId   = teacherId,
        title       = "காடும் மழையும்",
        titleEn     = "The Forest and Rain",
        lessonType  = "story",
        difficulty  = 1,
        language    = "tamil",
        isPublished = true,
        syncStatus  = "synced",
        contentJson = """
{
  "title": "காடும் மழையும்",
  "passage": {
    "lines": [
      "பச்சை மரங்கள் நிறைந்த காடு.",
      "மழை பெய்தது. ஆறு ஓடியது.",
      "குருவி பாடியது. மான் ஓடியது.",
      "காடு மகிழ்ச்சியாக இருந்தது."
    ]
  },
  "vocabulary": [
    {
      "word": "காடு",
      "syllables": ["கா", "டு"],
      "meaning_ta": "மரங்கள் நிறைந்த இடம்",
      "meaning_en": "forest",
      "audio_hint": "kaadu"
    },
    {
      "word": "மழை",
      "syllables": ["ம", "ழை"],
      "meaning_ta": "வானில் இருந்து விழும் தண்ணீர்",
      "meaning_en": "rain",
      "audio_hint": "mazhai"
    },
    {
      "word": "குருவி",
      "syllables": ["கு", "ரு", "வி"],
      "meaning_ta": "சிறிய பறவை",
      "meaning_en": "sparrow",
      "audio_hint": "kuruvi"
    }
  ],
  "quiz": [
    {
      "question_ta": "காட்டில் என்ன பெய்தது?",
      "question_en": "What fell in the forest?",
      "options_ta": ["மழை", "பனி"],
      "options_en": ["Rain", "Snow"],
      "correct_index": 0
    },
    {
      "question_ta": "காட்டில் எது பாடியது?",
      "question_en": "What sang in the forest?",
      "options_ta": ["குருவி", "மான்"],
      "options_en": ["Sparrow", "Deer"],
      "correct_index": 0
    }
  ],
  "metadata": {
    "source": "default",
    "difficulty": 1,
    "language": "tamil"
  }
}
        """.trimIndent()
    ),

    LessonEntity(
        id          = "demo-lesson-002",
        teacherId   = teacherId,
        title       = "என் குடும்பம்",
        titleEn     = "My Family",
        lessonType  = "story",
        difficulty  = 1,
        language    = "tamil",
        isPublished = true,
        syncStatus  = "synced",
        contentJson = """
{
  "title": "என் குடும்பம்",
  "passage": {
    "lines": [
      "என் குடும்பம் மிகவும் அன்பானது.",
      "அம்மா சமைக்கிறார். அப்பா வேலை செய்கிறார்.",
      "அக்கா படிக்கிறாள். நான் விளையாடுகிறேன்.",
      "நாங்கள் மகிழ்ச்சியாக வாழ்கிறோம்."
    ]
  },
  "vocabulary": [
    {
      "word": "குடும்பம்",
      "syllables": ["கு", "டும்", "பம்"],
      "meaning_ta": "அம்மா, அப்பா, குழந்தைகள்",
      "meaning_en": "family",
      "audio_hint": "kudumbam"
    },
    {
      "word": "அம்மா",
      "syllables": ["அம்", "மா"],
      "meaning_ta": "தாய்",
      "meaning_en": "mother",
      "audio_hint": "ammaa"
    },
    {
      "word": "மகிழ்ச்சி",
      "syllables": ["ம", "கிழ்ச்", "சி"],
      "meaning_ta": "சந்தோஷம்",
      "meaning_en": "happiness",
      "audio_hint": "magizchi"
    }
  ],
  "quiz": [
    {
      "question_ta": "அம்மா என்ன செய்கிறார்?",
      "question_en": "What does mother do?",
      "options_ta": ["சமைக்கிறார்", "படிக்கிறார்"],
      "options_en": ["Cooks", "Studies"],
      "correct_index": 0
    },
    {
      "question_ta": "குடும்பம் எப்படி வாழ்கிறது?",
      "question_en": "How does the family live?",
      "options_ta": ["மகிழ்ச்சியாக", "சோகமாக"],
      "options_en": ["Happily", "Sadly"],
      "correct_index": 0
    }
  ],
  "metadata": {
    "source": "default",
    "difficulty": 1,
    "language": "tamil"
  }
}
        """.trimIndent()
    ),

    LessonEntity(
        id          = "demo-lesson-003",
        teacherId   = teacherId,
        title       = "சூரியனும் நிலவும்",
        titleEn     = "The Sun and Moon",
        lessonType  = "story",
        difficulty  = 2,
        language    = "tamil",
        isPublished = true,
        syncStatus  = "synced",
        contentJson = """
{
  "title": "சூரியனும் நிலவும்",
  "passage": {
    "lines": [
      "சூரியன் பகலில் ஒளி தருகிறான்.",
      "நிலவு இரவில் குளிர்ந்த ஒளி தருகிறது.",
      "விண்மீன்கள் இரவு வானில் மின்னுகின்றன.",
      "இரவும் பகலும் மாறி மாறி வருகின்றன.",
      "இயற்கை நமக்கு கொடுத்த வரம் இது."
    ]
  },
  "vocabulary": [
    {
      "word": "சூரியன்",
      "syllables": ["சூ", "ரி", "யன்"],
      "meaning_ta": "பகல் வெளிச்சம் தரும் நட்சத்திரம்",
      "meaning_en": "sun",
      "audio_hint": "suriyan"
    },
    {
      "word": "நிலவு",
      "syllables": ["நி", "ல", "வு"],
      "meaning_ta": "இரவில் தெரியும் வெண்ணிற வட்டம்",
      "meaning_en": "moon",
      "audio_hint": "nilavu"
    },
    {
      "word": "விண்மீன்",
      "syllables": ["விண்", "மீன்"],
      "meaning_ta": "இரவு வானில் ஒளிரும் சிறு புள்ளிகள்",
      "meaning_en": "star",
      "audio_hint": "vinmeen"
    },
    {
      "word": "இயற்கை",
      "syllables": ["இ", "யற்", "கை"],
      "meaning_ta": "மனிதன் செய்யாத படைப்புகள்",
      "meaning_en": "nature",
      "audio_hint": "iyarkai"
    }
  ],
  "quiz": [
    {
      "question_ta": "சூரியன் எப்போது ஒளி தருகிறான்?",
      "question_en": "When does the sun give light?",
      "options_ta": ["பகலில்", "இரவில்"],
      "options_en": ["Daytime", "Nighttime"],
      "correct_index": 0
    },
    {
      "question_ta": "விண்மீன்கள் எங்கே மின்னுகின்றன?",
      "question_en": "Where do stars shine?",
      "options_ta": ["இரவு வானில்", "கடலில்"],
      "options_en": ["Night sky", "In the sea"],
      "correct_index": 0
    }
  ],
  "metadata": {
    "source": "default",
    "difficulty": 2,
    "language": "tamil"
  }
}
        """.trimIndent()
    ),
)
