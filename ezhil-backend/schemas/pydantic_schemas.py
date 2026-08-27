from typing import Any, Optional
from pydantic import BaseModel


# ─── AUTH ─────────────────────────────────────────────────────────────────────

class LoginRequest(BaseModel):
    school_code: str
    teacher_id: str
    pin: Optional[str] = None


class LoginResponse(BaseModel):
    access_token: str
    teacher_id: str
    school_id: str
    teacher_name: str
    school_name: str
    class_name: str
    district: str
    schoolCode: str
    schoolName: str
    teacherId: str
    teacherName: str


class RegisterRequest(BaseModel):
    school_code: str
    school_name: str
    district: str = ""
    teacher_code: str
    teacher_name: str
    class_name: str
    pin: str


class StudentLoginRequest(BaseModel):
    school_code: str
    student_code: str
    pin: Optional[str] = None


class StudentLoginResponse(BaseModel):
    access_token: str
    student_id: str
    student_name: str
    school_name: str
    teacher_name: str
    class_name: str
    risk_level: str
    schoolCode: str
    schoolName: str
    teacherId: str
    teacherName: str


# ─── SYNC ─────────────────────────────────────────────────────────────────────

class SyncPushRequest(BaseModel):
    table: str
    rows: list[dict[str, Any]]


class SyncPushResponse(BaseModel):
    accepted: int
    conflicts: list[str]


class StudentDto(BaseModel):
    id: str
    name: str
    teacher_id: str
    dob: Optional[str] = None
    risk_level: str = "unscreened"


class LessonDto(BaseModel):
    id: str
    title: str
    content_json: str
    difficulty: int = 1
    language: str = "tamil"
    is_published: bool = True
    teacher_id: Optional[str] = None
    source_hash: Optional[str] = None
    lesson_type: str = "story"
    assigned_to: str = "class"
    cache_hit: bool = False
    created_at: Optional[str] = None  # ISO 8601; the library lists lessons by date


class SyncPullResponse(BaseModel):
    server_time: str = ""
    lessons: list[LessonDto]
    roster: list[StudentDto]


# ─── STUDIO ───────────────────────────────────────────────────────────────────

class OcrResponse(BaseModel):
    extracted_text: str
    char_count: int
    confidence: float = 0.0
    minimum_confidence: float = 0.0
    ocr_engine: Optional[str] = None
    source_hash: Optional[str] = None
    # True when the text is usable but must be read by the teacher before it
    # is turned into a lesson. The client should show it for correction.
    requires_review: bool = False
    review_reason: Optional[str] = None


class GenerateRequest(BaseModel):
    ocr_text: str
    difficulty: int = 1
    language: str = "tamil"
    source_hash: str = ""
    average_confidence: Optional[float] = None
    minimum_confidence: Optional[float] = None
    page_confidence: Optional[list[float]] = None
    ocr_engine: Optional[str] = None
    # Set by the studio once the teacher has read the extracted text. Required
    # when the extraction came back flagged for review.
    text_reviewed: bool = False


class LessonPassage(BaseModel):
    lines: list[str]
    line_count: Optional[int] = None


class LessonVocabEntry(BaseModel):
    word: str
    syllables: list[str] = []
    meaning_ta: str = ""
    meaning_en: str = ""
    audio_hint: Optional[str] = None


class LessonQuizItem(BaseModel):
    question_ta: str
    question_en: Optional[str] = None
    options_ta: list[str] = []
    options_en: Optional[list[str]] = None
    correct_index: int = 0
    explanation_ta: Optional[str] = None
    explanation_en: Optional[str] = None


class LessonMetadata(BaseModel):
    source: str = "generated"
    difficulty: int = 1
    language: str = "tamil"
    generated_at: Optional[str] = None
    coverage_report: Optional[dict] = None


class LessonContent(BaseModel):
    title: str
    passage: LessonPassage
    vocabulary: list[LessonVocabEntry] = []
    quiz: list[LessonQuizItem] = []
    audio_script: Optional[str] = None
    metadata: Optional[LessonMetadata] = None
    coverage_report: Optional[dict] = None


class GenerateResponse(BaseModel):
    lesson_id: str
    lesson: LessonContent
    cache_hit: bool
    source_hash: str
    token_count: int = 0


# ─── MULTI-FILE EXTRACTION ────────────────────────────────────────────────────

class FileExtractionResult(BaseModel):
    filename:       str
    file_type:      str           # "image" | "pdf" | "docx" | "text"
    extracted_text: str
    char_count:     int
    page_count:     int = 1
    method:         str = ""      # "native" | "ocr" | "direct"
    status:         str           # "success" | "empty" | "error"
    error:          Optional[str] = None
    average_confidence: Optional[float] = None
    minimum_confidence: Optional[float] = None
    page_confidence: Optional[list[float]] = None
    ocr_engine: Optional[str] = None


class MultiExtractResponse(BaseModel):
    files:         list[FileExtractionResult]
    combined_text: str
    total_chars:   int
    source_hash:   str
    average_confidence: Optional[float] = None
    minimum_confidence: Optional[float] = None
    page_confidence: Optional[list[float]] = None
    ocr_engine: Optional[str] = None
    # True when the extraction is good enough to use but not good enough to
    # trust unread. The studio must show the text and require confirmation.
    requires_review: bool = False
    review_reason: Optional[str] = None
