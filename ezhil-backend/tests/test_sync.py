"""Sync: ownership scoping, conflicts, student tokens, server_time."""


def _teacher_token(call):
    return call("POST", "/api/v1/auth/login",
                json={"school_code": "SCH-T01", "teacher_id": "T-TEST", "pin": "1234"}
                ).json()["access_token"]


def _student_token(call):
    return call("POST", "/api/v1/auth/student/login",
                json={"school_code": "SCH-T01", "student_code": "KAVIN", "pin": "0512"}
                ).json()["access_token"]


def _assessment_row(row_id: str, student_id: str) -> dict:
    return {
        "id": row_id, "student_id": student_id,
        "conducted_at": "2026-07-01T10:00:00Z", "risk_level": "low",
        "model_version": "heuristic-0.1",
    }


def test_student_can_push_own_assessment(call):
    token = _student_token(call)
    r = call("POST", "/api/v1/sync/push",
             json={"table": "assessments", "rows": [_assessment_row("a-own", "student-1")]},
             token=token)
    assert r.status_code == 200
    assert r.json() == {"accepted": 1, "conflicts": []}


def test_student_cannot_push_other_students_data(call):
    token = _student_token(call)
    r = call("POST", "/api/v1/sync/push",
             json={"table": "assessments", "rows": [_assessment_row("a-foreign", "student-2")]},
             token=token)
    assert r.status_code == 200
    body = r.json()
    assert body["accepted"] == 0
    assert body["conflicts"] == ["a-foreign"]


def test_teacher_cannot_push_for_other_class(call):
    token = _teacher_token(call)
    # student-2 belongs to teacher-2
    r = call("POST", "/api/v1/sync/push",
             json={"table": "assessments", "rows": [_assessment_row("a-cross", "student-2")]},
             token=token)
    assert r.json()["conflicts"] == ["a-cross"]


def test_push_unknown_table_rejected(call):
    token = _teacher_token(call)
    r = call("POST", "/api/v1/sync/push",
             json={"table": "teachers", "rows": []}, token=token)
    assert r.status_code == 400


def test_push_requires_token(call):
    r = call("POST", "/api/v1/sync/push", json={"table": "assessments", "rows": []})
    assert r.status_code == 401


def test_pull_returns_server_time_and_scoped_roster(call):
    token = _teacher_token(call)
    r = call("GET", "/api/v1/sync/pull?last_sync=2000-01-01T00:00:00Z", token=token)
    assert r.status_code == 200
    body = r.json()
    assert body["server_time"]
    roster_ids = {s["id"] for s in body["roster"]}
    assert "student-1" in roster_ids
    assert "student-2" not in roster_ids  # other teacher's student


def test_student_pull_sees_only_self(call):
    token = _student_token(call)
    r = call("GET", "/api/v1/sync/pull?last_sync=2000-01-01T00:00:00Z", token=token)
    assert r.status_code == 200
    assert {s["id"] for s in r.json()["roster"]} <= {"student-1"}


# ── Lesson push (teacher-owned rows) ─────────────────────────────────────────

def _lesson_row(row_id: str, published: bool = True) -> dict:
    return {
        "id": row_id, "title": "யானையும் எறும்பும்",
        "content_json": '{"title":"x","passage":{"lines":["a"],"line_count":1}}',
        "difficulty": 1, "language": "tamil", "is_published": published,
        "lesson_type": "story", "assigned_to": "class", "cache_hit": False,
        "created_at": "2026-07-01T10:00:00Z",
    }


def test_teacher_can_push_lesson(call):
    token = _teacher_token(call)
    r = call("POST", "/api/v1/sync/push",
             json={"table": "lessons", "rows": [_lesson_row("lesson-1")]}, token=token)
    assert r.status_code == 200
    assert r.json() == {"accepted": 1, "conflicts": []}


def test_pushed_published_lesson_reaches_pull(call):
    """The whole point of the lesson push: publish state must propagate."""
    token = _teacher_token(call)
    call("POST", "/api/v1/sync/push",
         json={"table": "lessons", "rows": [_lesson_row("lesson-pub")]}, token=token)
    r = call("GET", "/api/v1/sync/pull?last_sync=2000-01-01T00:00:00Z", token=token)
    assert "lesson-pub" in {l["id"] for l in r.json()["lessons"]}


def test_unpublished_lesson_absent_from_pull(call):
    token = _teacher_token(call)
    call("POST", "/api/v1/sync/push",
         json={"table": "lessons", "rows": [_lesson_row("lesson-draft", published=False)]},
         token=token)
    r = call("GET", "/api/v1/sync/pull?last_sync=2000-01-01T00:00:00Z", token=token)
    assert "lesson-draft" not in {l["id"] for l in r.json()["lessons"]}


def test_student_cannot_push_lessons(call):
    """A child must never be able to publish content to the class."""
    token = _student_token(call)
    r = call("POST", "/api/v1/sync/push",
             json={"table": "lessons", "rows": [_lesson_row("lesson-evil")]}, token=token)
    assert r.json() == {"accepted": 0, "conflicts": ["lesson-evil"]}


def test_teacher_cannot_overwrite_another_teachers_lesson(call):
    token = _teacher_token(call)
    call("POST", "/api/v1/sync/push",
         json={"table": "lessons", "rows": [_lesson_row("lesson-owned")]}, token=token)
    # teacher-2 (T-LEGACY, PIN set to 5678 by the earlier trust-on-first-use test)
    other = call("POST", "/api/v1/auth/login",
                 json={"school_code": "SCH-T01", "teacher_id": "T-LEGACY", "pin": "5678"}
                 ).json()["access_token"]
    r = call("POST", "/api/v1/sync/push",
             json={"table": "lessons", "rows": [_lesson_row("lesson-owned")]}, token=other)
    assert r.json()["conflicts"] == ["lesson-owned"]
