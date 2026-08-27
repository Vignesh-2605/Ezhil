from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional

from auth_utils import get_current_teacher
from db import get_db
from models.db_models import Lesson, Teacher
from schemas.pydantic_schemas import LessonDto

router = APIRouter()

# Helper to map difficulty int -> string for frontend compatibility
def map_difficulty_to_str(diff_int: int) -> str:
    if diff_int == 2:
        return "intermediate"
    elif diff_int == 3:
        return "advanced"
    return "beginner"

# Helper to map difficulty string -> int for saving
def map_difficulty_to_int(diff_str: str) -> int:
    ds = diff_str.lower().strip()
    if ds == "intermediate":
        return 2
    elif ds == "advanced":
        return 3
    return 1

@router.get("", response_model=List[LessonDto])
async def get_lessons(
    role: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    teacher: Teacher = Depends(get_current_teacher)
):
    """
    Retrieve all lessons created by this teacher.
    If role == 'student', only return published lessons.
    """
    stmt = select(Lesson).where(Lesson.teacher_id == teacher.id)
    if role == "student":
        stmt = stmt.where(Lesson.is_published == True)
        
    result = await db.execute(stmt)
    lessons = result.scalars().all()

    return [_to_dto(l) for l in lessons]


def _to_dto(l: Lesson) -> LessonDto:
    return LessonDto(
        id=l.id,
        title=l.title,
        content_json=l.content_json,
        difficulty=l.difficulty,
        language=l.language,
        is_published=l.is_published,
        teacher_id=l.teacher_id,
        source_hash=l.source_hash,
        lesson_type=l.lesson_type,
        assigned_to=l.assigned_to,
        cache_hit=l.cache_hit,
        created_at=l.created_at.isoformat() if l.created_at else None,
    )

@router.put("/{lesson_id}", response_model=LessonDto)
async def update_lesson(
    lesson_id: str,
    payload: LessonDto,
    db: AsyncSession = Depends(get_db),
    teacher: Teacher = Depends(get_current_teacher)
):
    """
    Update a lesson's publish status or content
    """
    lesson = await db.get(Lesson, lesson_id)
    if not lesson or lesson.teacher_id != teacher.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Lesson not found"
        )
        
    lesson.title = payload.title
    lesson.content_json = payload.content_json
    lesson.difficulty = payload.difficulty
    lesson.language = payload.language
    lesson.is_published = payload.is_published
    lesson.lesson_type = payload.lesson_type
    lesson.assigned_to = payload.assigned_to

    await db.flush()
    await db.refresh(lesson)
    # Return the persisted row, not the request body — the client must see
    # what the server actually stored (id, teacher_id, created_at included).
    return _to_dto(lesson)

@router.delete("/{lesson_id}")
async def delete_lesson(
    lesson_id: str,
    db: AsyncSession = Depends(get_db),
    teacher: Teacher = Depends(get_current_teacher)
):
    """
    Delete a lesson
    """
    lesson = await db.get(Lesson, lesson_id)
    if not lesson or lesson.teacher_id != teacher.id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Lesson not found"
        )
        
    await db.delete(lesson)
    await db.flush()
    return {"status": "deleted", "lesson_id": lesson_id}
