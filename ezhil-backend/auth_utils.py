import hashlib
import logging
from datetime import datetime, timedelta, timezone

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from sqlalchemy.ext.asyncio import AsyncSession

from config import get_settings
from db import get_db

settings = get_settings()
logger = logging.getLogger(__name__)

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login", auto_error=False)


def hash_pin(pin: str) -> str:
    """SHA-256 hex digest — matches the Android client's LocalAuth.hashPin()."""
    return hashlib.sha256(pin.encode("utf-8")).hexdigest()


def create_access_token(data: dict, role: str = "teacher") -> str:
    payload = data.copy()
    payload["role"] = role
    expire = datetime.now(timezone.utc) + timedelta(days=settings.ACCESS_TOKEN_EXPIRE_DAYS)
    payload["exp"] = expire
    return jwt.encode(payload, settings.SECRET_KEY, algorithm=settings.ALGORITHM)


def _decode_token(token: str | None) -> dict:
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
    except JWTError as e:
        logger.warning("JWT decode failed: %s", e)
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    if not payload.get("sub"):
        raise HTTPException(status_code=401, detail="Invalid token payload")
    return payload


class CurrentUser:
    """Resolved identity for a request. Exactly one of teacher/student is set."""

    def __init__(self, role: str, teacher=None, student=None):
        self.role = role          # "teacher" | "student"
        self.teacher = teacher
        self.student = student

    @property
    def id(self) -> str:
        return self.teacher.id if self.role == "teacher" else self.student.id


async def get_current_user(
    token: str | None = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db),
) -> CurrentUser:
    """Accepts both teacher and student tokens. Use for sync endpoints."""
    from models.db_models import Student, Teacher

    payload = _decode_token(token)
    sub = payload["sub"]
    role = payload.get("role", "teacher")

    if role == "student":
        student = await db.get(Student, sub)
        if student is None:
            raise HTTPException(status_code=401, detail="User no longer exists")
        return CurrentUser("student", student=student)

    teacher = await db.get(Teacher, sub)
    if teacher is None:
        raise HTTPException(status_code=401, detail="User no longer exists")
    return CurrentUser("teacher", teacher=teacher)


async def get_current_teacher(
    user: CurrentUser = Depends(get_current_user),
):
    """Teacher-only endpoints (studio, dashboard, lessons)."""
    if user.role != "teacher":
        raise HTTPException(status_code=403, detail="Teacher access required")
    return user.teacher
