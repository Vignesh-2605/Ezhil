from sqlalchemy import Boolean, Column, DateTime, Float, ForeignKey, Index, Integer, String, Text
from sqlalchemy.sql import func

from db import Base


class School(Base):
    __tablename__ = "schools"

    id         = Column(String(36), primary_key=True)
    code       = Column(String(20),  nullable=False, unique=True)
    name       = Column(String(255), nullable=False)
    district   = Column(String(100), nullable=False)
    created_at = Column(DateTime, server_default=func.now())


class Teacher(Base):
    __tablename__ = "teachers"

    id           = Column(String(36), primary_key=True)
    school_id    = Column(String(36), ForeignKey("schools.id"), nullable=False)
    teacher_code = Column(String(50),  nullable=False, unique=True)
    name         = Column(String(255), nullable=False)
    class_name   = Column(String(100), nullable=False)
    hashed_pin   = Column(String(64),  nullable=True)  # SHA-256 hex; NULL = set on first login
    created_at   = Column(DateTime, server_default=func.now())


class Student(Base):
    __tablename__ = "students"
    __table_args__ = (Index("ix_students_teacher_id", "teacher_id"),)

    id          = Column(String(36), primary_key=True)
    teacher_id  = Column(String(36), ForeignKey("teachers.id"), nullable=False)
    name        = Column(String(255), nullable=False)
    dob         = Column(String(20),  nullable=True)
    risk_level  = Column(String(20),  nullable=False, default="unscreened")
    streak_days = Column(Integer,     nullable=False, default=0)
    last_active = Column(String(30),  nullable=True)
    created_at  = Column(DateTime, server_default=func.now())
    updated_at  = Column(DateTime, onupdate=func.now())


class Assessment(Base):
    __tablename__ = "assessments"
    __table_args__ = (Index("ix_assessments_student_id", "student_id"),)

    id                   = Column(String(36), primary_key=True)
    student_id           = Column(String(36), ForeignKey("students.id"), nullable=False)
    conducted_at         = Column(String(30),  nullable=True)
    reading_speed_wpm    = Column(Float,       nullable=True)
    phoneme_error_rate   = Column(Float,       nullable=True)
    letter_reversal_rate = Column(Float,       nullable=True)
    syllable_skip_rate   = Column(Float,       nullable=True)
    lip_sync_confidence  = Column(Float,       nullable=True)
    cnn_risk_score       = Column(Float,       nullable=True)
    risk_level           = Column(String(20),  nullable=False, default="unscreened")
    error_tags_json      = Column(Text,        nullable=True)
    audio_duration_ms    = Column(Integer,     nullable=True)
    model_version        = Column(String(20),  nullable=False, default="1.0.0")
    created_at           = Column(DateTime, server_default=func.now())


class GameSession(Base):
    __tablename__ = "game_sessions"
    __table_args__ = (Index("ix_game_sessions_student_id", "student_id"),)

    id                = Column(String(36), primary_key=True)
    student_id        = Column(String(36), ForeignKey("students.id"), nullable=False)
    game_type         = Column(String(50),  nullable=False)
    played_at         = Column(String(30),  nullable=True)
    rounds_total      = Column(Integer,     nullable=False, default=0)
    rounds_correct    = Column(Integer,     nullable=False, default=0)
    duration_ms       = Column(Integer,     nullable=False, default=0)
    error_matrix_json = Column(Text,        nullable=False, default="{}")
    difficulty_level  = Column(Integer,     nullable=False, default=1)
    stars_earned      = Column(Integer,     nullable=False, default=0)
    created_at        = Column(DateTime, server_default=func.now())


class Lesson(Base):
    __tablename__ = "lessons"
    __table_args__ = (Index("ix_lessons_source_hash", "source_hash"),)

    id           = Column(String(36),  primary_key=True)
    teacher_id   = Column(String(36),  ForeignKey("teachers.id"), nullable=True)
    source_hash  = Column(String(128), nullable=True)
    title        = Column(String(512), nullable=False)
    title_en     = Column(String(512), nullable=True)
    lesson_type  = Column(String(50),  nullable=False, default="story")
    difficulty   = Column(Integer,     nullable=False, default=1)
    language     = Column(String(20),  nullable=False, default="tamil")
    content_json = Column(Text,        nullable=False)
    is_published = Column(Boolean,     nullable=False, default=False)
    assigned_to  = Column(String(50),  nullable=False, default="class")
    cache_hit    = Column(Boolean,     nullable=False, default=False)
    created_at   = Column(DateTime, server_default=func.now())
    updated_at   = Column(DateTime, onupdate=func.now())


class LessonProgress(Base):
    __tablename__ = "lesson_progress"
    __table_args__ = (Index("ix_lesson_progress_student_id", "student_id"),)

    id                 = Column(String(36), primary_key=True)
    student_id         = Column(String(36), ForeignKey("students.id"), nullable=False)
    lesson_id          = Column(String(36), ForeignKey("lessons.id"),  nullable=False)
    completed_at       = Column(String(30), nullable=True)
    quiz_score_percent = Column(Float,      nullable=True)
    duration_ms        = Column(Integer,    nullable=True)
    created_at         = Column(DateTime, server_default=func.now())
