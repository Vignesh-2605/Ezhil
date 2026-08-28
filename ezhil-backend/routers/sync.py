from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import DateTime, and_, inspect, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth_utils import CurrentUser, get_current_user
from db import get_db
from models.db_models import (
    Assessment,
    GameSession,
    Lesson,
    LessonProgress,
    Student,
)
from schemas.pydantic_schemas import (
    LessonDto,
    StudentDto,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
)

router = APIRouter()

_TABLE_MAP = {
    "students": Student,
    "assessments": Assessment,
    "game_sessions": GameSession,
    "lesson_progress": LessonProgress,
    "lessons": Lesson,
}


def _valid_columns(model_class) -> set[str]:
    return {col.key for col in inspect(model_class).mapper.column_attrs}


def _coerce_row(model_class, row: dict[str, Any]) -> dict[str, Any]:
    """
    Strip client-only fields and coerce ISO datetime strings to datetime objects
    for any column whose SQLAlchemy type is DateTime.
    SQLite's DateTime type rejects plain strings — they must be Python datetime.
    """
    mapper = inspect(model_class).mapper
    dt_cols = {
        col.key
        for col in mapper.column_attrs
        if hasattr(col, "columns")
        and any(isinstance(c.type, DateTime) for c in col.columns)
    }
    coerced: dict[str, Any] = {}
    for k, v in row.items():
        if k not in _valid_columns(model_class):
            continue  # strip unknown / client-only keys
        if k in dt_cols and isinstance(v, str) and v:
            try:
                coerced[k] = datetime.fromisoformat(v.replace("Z", "+00:00")).replace(tzinfo=None)
            except ValueError:
                coerced[k] = None
        else:
            coerced[k] = v
    return coerced


def _parse_since(ts: str) -> datetime:
    ts = ts.replace("Z", "+00:00")
    try:
        dt = datetime.fromisoformat(ts)
        return dt.replace(tzinfo=None)
    except ValueError:
        return datetime(2000, 1, 1)


async def _owned_student_ids(db: AsyncSession, user: CurrentUser) -> set[str]:
    if user.role == "student":
        return {user.student.id}
    result = await db.execute(select(Student.id).where(Student.teacher_id == user.teacher.id))
    return {row[0] for row in result.all()}


def _row_student_id(model_cls, clean: dict[str, Any], existing) -> str | None:
    """The student a row belongs to, preferring the server-side record over
    client-supplied values so ownership can't be spoofed on update."""
    if model_cls is Student:
        return existing.id if existing is not None else clean.get("id")
    if existing is not None:
        return existing.student_id
    return clean.get("student_id")


async def _refresh_risk_levels(db: AsyncSession, student_ids: set[str]) -> int:
    """
    Recompute `students.risk_level` from each student's newest assessment.

    `risk_level` is denormalised onto the student row because both dashboards
    read it directly, but nothing on the server ever derived it — it was
    whatever the last client happened to push. Android updates the field
    locally after a screening without marking the row dirty, so the value never
    left the handset: the phone showed "low" while the server, and therefore
    the web app, still said "unscreened" (see StudentDao.updateRiskLevel).

    Deriving it here makes the server the single source of truth, so every
    client converges on the same answer regardless of what it remembered to
    push, and rows already stranded are repaired the next time an assessment
    for that student is pushed.
    """
    if not student_ids:
        return 0

    updated = 0
    for sid in student_ids:
        latest = (
            await db.execute(
                select(Assessment.risk_level)
                .where(Assessment.student_id == sid)
                .order_by(Assessment.conducted_at.desc())
                .limit(1)
            )
        ).scalar_one_or_none()

        # No assessments left (all deleted) is a real state — "unscreened" —
        # but a NULL risk on an existing assessment is not something to act on.
        if latest is None:
            continue

        student = await db.get(Student, sid)
        if student is not None and student.risk_level != latest:
            student.risk_level = latest
            updated += 1

    return updated


@router.post("/push", response_model=SyncPushResponse)
async def sync_push(
    req: SyncPushRequest,
    db: AsyncSession = Depends(get_db),
    user: CurrentUser = Depends(get_current_user),
):
    model_cls = _TABLE_MAP.get(req.table)
    if model_cls is None:
        raise HTTPException(status_code=400, detail=f"Unknown table: {req.table!r}")

    owned = await _owned_student_ids(db, user)
    accepted = 0
    conflicts: list[str] = []
    touched_students: set[str] = set()

    for row in req.rows:
        clean = _coerce_row(model_cls, row)
        pk = clean.get("id")
        if not pk:
            continue

        existing = await db.get(model_cls, pk)

        # Ownership: a teacher may only write rows for their own students;
        # a student may only write rows about themselves.
        if model_cls is Lesson:
            # Lessons belong to a teacher, not a student. Students may never
            # push them — that would let a child publish to the whole class.
            if user.role != "teacher":
                conflicts.append(pk)
                continue
            if existing is not None and existing.teacher_id != user.teacher.id:
                conflicts.append(pk)
                continue
            clean["teacher_id"] = user.teacher.id
        elif model_cls is Student:
            if existing is not None and existing.teacher_id != (
                user.teacher.id if user.role == "teacher" else existing.teacher_id
            ):
                conflicts.append(pk)
                continue
            if user.role == "teacher":
                # New student rows always belong to the pushing teacher.
                clean["teacher_id"] = existing.teacher_id if existing else user.teacher.id
            elif pk not in owned:
                conflicts.append(pk)
                continue
        else:
            sid = _row_student_id(model_cls, clean, existing)
            if sid not in owned:
                conflicts.append(pk)
                continue

        if existing is not None:
            for k, v in clean.items():
                setattr(existing, k, v)
        else:
            db.add(model_cls(**clean))
        accepted += 1

        if model_cls is Assessment:
            sid = _row_student_id(model_cls, clean, existing)
            if sid:
                touched_students.add(sid)

    # After the assessments are in the session, so the newest one is visible.
    await db.flush()
    if touched_students:
        await _refresh_risk_levels(db, touched_students)
        await db.flush()

    return SyncPushResponse(accepted=accepted, conflicts=conflicts)


@router.get("/pull", response_model=SyncPullResponse)
async def sync_pull(
    last_sync: str = "2000-01-01T00:00:00Z",
    db: AsyncSession = Depends(get_db),
    user: CurrentUser = Depends(get_current_user),
):
    since = _parse_since(last_sync)
    teacher_id = user.teacher.id if user.role == "teacher" else user.student.teacher_id

    # Lessons: published, owned by this teacher, updated or created after last_sync
    lessons_q = await db.execute(
        select(Lesson).where(
            Lesson.teacher_id == teacher_id,
            Lesson.is_published.is_(True),
            or_(
                and_(Lesson.updated_at.isnot(None), Lesson.updated_at > since),
                and_(Lesson.updated_at.is_(None), Lesson.created_at > since),
            ),
        )
    )
    lessons = lessons_q.scalars().all()

    # Roster: the teacher's class, or just the student themself
    roster_where = [
        Student.teacher_id == teacher_id,
        or_(
            and_(Student.updated_at.isnot(None), Student.updated_at > since),
            and_(Student.updated_at.is_(None), Student.created_at > since),
        ),
    ]
    if user.role == "student":
        roster_where.append(Student.id == user.student.id)
    roster_q = await db.execute(select(Student).where(*roster_where))
    roster = roster_q.scalars().all()

    return SyncPullResponse(
        server_time=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        lessons=[
            LessonDto(
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
            for l in lessons
        ],
        roster=[
            StudentDto(
                id=s.id,
                name=s.name,
                teacher_id=s.teacher_id,
                dob=s.dob,
                risk_level=s.risk_level,
            )
            for s in roster
        ],
    )
