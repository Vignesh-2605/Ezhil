import re
import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth_utils import create_access_token, hash_pin
from db import get_db
from models.db_models import School, Teacher, Student
from schemas.pydantic_schemas import (
    LoginRequest,
    LoginResponse,
    RegisterRequest,
    StudentLoginRequest,
    StudentLoginResponse,
)

router = APIRouter()

_INVALID = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")


def _dob_to_pin(dob: str | None) -> str | None:
    """Default student PIN is the birthday as MMDD — mirrors Android's dobToPin().
    Accepts YYYY-MM-DD or DD/MM/YYYY embedded anywhere in the string."""
    if not dob:
        return None
    iso = re.search(r"(\d{4})-(\d{2})-(\d{2})", dob)
    if iso:
        return f"{iso.group(2)}{iso.group(3)}"
    dmy = re.search(r"(\d{2})/(\d{2})/(\d{4})", dob)
    if dmy:
        return f"{dmy.group(2)}{dmy.group(1)}"
    return None


@router.post("/login", response_model=LoginResponse)
async def login(req: LoginRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(Teacher).where(Teacher.teacher_code == req.teacher_id.upper().strip())
    )
    teacher = result.scalar_one_or_none()

    if teacher is None:
        raise _INVALID

    school = await db.get(School, teacher.school_id)
    if school is None or school.code.upper() != req.school_code.upper().strip():
        raise _INVALID

    pin = (req.pin or "").strip()
    if not pin:
        raise _INVALID
    if teacher.hashed_pin is None:
        # Legacy row from before PIN enforcement: trust-on-first-use.
        teacher.hashed_pin = hash_pin(pin)
        await db.flush()
    elif teacher.hashed_pin != hash_pin(pin):
        raise _INVALID

    token = create_access_token({"sub": teacher.id}, role="teacher")
    return LoginResponse(
        access_token=token,
        teacher_id=teacher.id,
        school_id=teacher.school_id,
        teacher_name=teacher.name,
        school_name=school.name,
        class_name=teacher.class_name,
        district=school.district,
        schoolCode=school.code,
        schoolName=school.name,
        teacherId=teacher.teacher_code,
        teacherName=teacher.name,
    )


@router.post("/register", response_model=LoginResponse)
async def register(req: RegisterRequest, db: AsyncSession = Depends(get_db)):
    school_code = req.school_code.upper().strip()
    teacher_code = req.teacher_code.upper().strip()
    pin = (req.pin or "").strip()

    if len(pin) < 4:
        raise HTTPException(status_code=400, detail="PIN must be at least 4 digits")

    existing = await db.execute(select(Teacher).where(Teacher.teacher_code == teacher_code))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=409, detail="Teacher ID already registered")

    school_q = await db.execute(select(School).where(School.code == school_code))
    school = school_q.scalar_one_or_none()
    if school is None:
        school = School(
            id=str(uuid.uuid4()),
            code=school_code,
            name=req.school_name.strip(),
            district=req.district.strip() or "—",
        )
        db.add(school)
        await db.flush()

    teacher = Teacher(
        id=str(uuid.uuid4()),
        school_id=school.id,
        teacher_code=teacher_code,
        name=req.teacher_name.strip(),
        class_name=req.class_name.strip(),
        hashed_pin=hash_pin(pin),
    )
    db.add(teacher)
    await db.flush()

    token = create_access_token({"sub": teacher.id}, role="teacher")
    return LoginResponse(
        access_token=token,
        teacher_id=teacher.id,
        school_id=school.id,
        teacher_name=teacher.name,
        school_name=school.name,
        class_name=teacher.class_name,
        district=school.district,
        schoolCode=school.code,
        schoolName=school.name,
        teacherId=teacher.teacher_code,
        teacherName=teacher.name,
    )


@router.post("/student/login", response_model=StudentLoginResponse)
async def student_login(req: StudentLoginRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(School).where(School.code == req.school_code.upper().strip())
    )
    school = result.scalar_one_or_none()
    if school is None:
        raise _INVALID

    code = req.student_code.strip().upper()
    stmt = (
        select(Student, Teacher)
        .join(Teacher, Student.teacher_id == Teacher.id)
        .where(Teacher.school_id == school.id)
    )
    res = await db.execute(stmt)

    # Exact match on the student's first name (children type just their name),
    # never a bare prefix scan.
    match = None
    for student, teacher in res.all():
        first = student.name.split()[0].upper() if student.name else ""
        if first == code:
            match = (student, teacher)
            break
    if match is None:
        raise _INVALID

    student, teacher = match

    expected_pin = _dob_to_pin(student.dob)
    if expected_pin is not None and (req.pin or "").strip() != expected_pin:
        raise _INVALID

    token = create_access_token({"sub": student.id}, role="student")
    return StudentLoginResponse(
        access_token=token,
        student_id=student.id,
        student_name=student.name,
        school_name=school.name,
        teacher_name=teacher.name,
        class_name=teacher.class_name,
        risk_level=student.risk_level,
        schoolCode=school.code,
        schoolName=school.name,
        teacherId=teacher.teacher_code,
        teacherName=teacher.name,
    )
