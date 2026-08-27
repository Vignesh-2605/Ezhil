from fastapi import APIRouter, Depends
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel

from auth_utils import get_current_teacher
from db import get_db
from models.db_models import Lesson, LessonProgress, Student, Teacher

router = APIRouter()

class TeacherDashboardResponse(BaseModel):
    total_students: int
    high_risk: int
    medium_risk: int
    lessons_published: int
    avg_quiz_score: int

@router.get("/teacher", response_model=TeacherDashboardResponse)
async def get_teacher_dashboard_stats(
    db: AsyncSession = Depends(get_db),
    teacher: Teacher = Depends(get_current_teacher)
):
    """
    Get classroom analytics for the logged-in teacher
    """
    # 1. Total students
    s_count_r = await db.execute(
        select(func.count(Student.id)).where(Student.teacher_id == teacher.id)
    )
    total_students = s_count_r.scalar() or 0

    # 2. High risk students
    high_risk_r = await db.execute(
        select(func.count(Student.id))
        .where(Student.teacher_id == teacher.id)
        .where(Student.risk_level == "high")
    )
    high_risk = high_risk_r.scalar() or 0

    # 3. Medium risk students
    medium_risk_r = await db.execute(
        select(func.count(Student.id))
        .where(Student.teacher_id == teacher.id)
        .where(Student.risk_level == "medium")
    )
    medium_risk = medium_risk_r.scalar() or 0

    # 4. Lessons published
    l_pub_r = await db.execute(
        select(func.count(Lesson.id))
        .where(Lesson.teacher_id == teacher.id)
        .where(Lesson.is_published == True)
    )
    lessons_published = l_pub_r.scalar() or 0

    # 5. Average quiz score percent
    # We join lesson_progress and student to filter by teacher_id
    avg_score_r = await db.execute(
        select(func.avg(LessonProgress.quiz_score_percent))
        .join(Student, Student.id == LessonProgress.student_id)
        .where(Student.teacher_id == teacher.id)
    )
    avg_score_float = avg_score_r.scalar()
    
    # Coerce score to integer (0 - 100)
    avg_quiz_score = int(avg_score_float * 100) if avg_score_float is not None else 0

    return TeacherDashboardResponse(
        total_students=total_students,
        high_risk=high_risk,
        medium_risk=medium_risk,
        lessons_published=lessons_published,
        avg_quiz_score=avg_quiz_score
    )
