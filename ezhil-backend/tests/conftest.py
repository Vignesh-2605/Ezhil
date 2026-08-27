"""
Test environment. Must configure env vars BEFORE anything imports config's
cached Settings — pytest loads conftest first, so we do it at module import.
Uses an isolated SQLite file, recreated per test session.
"""
import asyncio
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

_TEST_DB = ROOT / "test_ezhil.db"
if _TEST_DB.exists():
    _TEST_DB.unlink()

os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{_TEST_DB.as_posix()}"
os.environ["DEMO_MODE"] = "true"  # never spawn the LLM server in tests
os.environ["SECRET_KEY"] = "test-secret-key"
# The real wait is 30 s per call; three OCR-gate tests exercise it, which took
# the suite from 41 s to over two minutes. The wait itself is covered directly
# in test_model_preload.py.
os.environ["OCR_MEMORY_WAIT_S"] = "0.05"

import pytest  # noqa: E402


@pytest.fixture(scope="session")
def seeded_app():
    """FastAPI app with one school, one PIN-protected teacher, two students."""
    from auth_utils import hash_pin
    from db import AsyncSessionLocal, init_db
    from main import app
    from models.db_models import School, Student, Teacher

    async def _seed():
        await init_db()
        async with AsyncSessionLocal() as db:
            school = School(id="school-1", code="SCH-T01", name="Test School", district="Test")
            teacher = Teacher(
                id="teacher-1", school_id="school-1", teacher_code="T-TEST",
                name="Test Teacher", class_name="G2", hashed_pin=hash_pin("1234"),
            )
            legacy = Teacher(
                id="teacher-2", school_id="school-1", teacher_code="T-LEGACY",
                name="Legacy Teacher", class_name="G3", hashed_pin=None,
            )
            s1 = Student(id="student-1", teacher_id="teacher-1", name="Kavin S.",
                         dob="2016-05-12", risk_level="unscreened", streak_days=0)
            s2 = Student(id="student-2", teacher_id="teacher-2", name="Oviya P.",
                         dob="2016-03-20", risk_level="unscreened", streak_days=0)
            db.add_all([school, teacher, legacy, s1, s2])
            await db.commit()

    asyncio.run(_seed())
    return app


@pytest.fixture()
def call(seeded_app):
    """Sync helper: call(method, path, json=None, token=None) -> Response."""
    from httpx import ASGITransport, AsyncClient

    def _call(method: str, path: str, json: dict | None = None, token: str | None = None):
        async def _run():
            headers = {"Authorization": f"Bearer {token}"} if token else {}
            async with AsyncClient(
                transport=ASGITransport(app=seeded_app), base_url="http://test"
            ) as client:
                return await client.request(method, path, json=json, headers=headers)

        return asyncio.run(_run())

    return _call
