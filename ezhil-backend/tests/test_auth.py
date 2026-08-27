"""Auth: PIN enforcement, student matching, roles, registration."""


def _teacher_token(call, pin="1234"):
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-TEST", "pin": pin})
    assert r.status_code == 200
    return r.json()["access_token"]


def _student_token(call):
    r = call("POST", "/api/v1/auth/student/login",
             json={"school_code": "SCH-T01", "student_code": "KAVIN", "pin": "0512"})
    assert r.status_code == 200
    return r.json()


def test_teacher_login_wrong_pin_rejected(call):
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-TEST", "pin": "9999"})
    assert r.status_code == 401


def test_teacher_login_missing_pin_rejected(call):
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-TEST"})
    assert r.status_code == 401


def test_teacher_login_correct_pin(call):
    body = call("POST", "/api/v1/auth/login",
                json={"school_code": "SCH-T01", "teacher_id": "T-TEST", "pin": "1234"}).json()
    assert body["teacherName"] == "Test Teacher"
    assert body["access_token"]


def test_legacy_teacher_pin_set_on_first_login_then_enforced(call):
    # Trust-on-first-use for rows created before PIN enforcement…
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-LEGACY", "pin": "5678"})
    assert r.status_code == 200
    # …after which the wrong PIN must fail.
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-LEGACY", "pin": "0000"})
    assert r.status_code == 401


def test_student_login_requires_exact_first_name(call):
    # "KAV" must not prefix-match "Kavin S." — the old vulnerability.
    r = call("POST", "/api/v1/auth/student/login",
             json={"school_code": "SCH-T01", "student_code": "KAV", "pin": "0512"})
    assert r.status_code == 401


def test_student_login_wrong_pin_rejected(call):
    r = call("POST", "/api/v1/auth/student/login",
             json={"school_code": "SCH-T01", "student_code": "KAVIN", "pin": "0000"})
    assert r.status_code == 401


def test_student_login_dob_pin(call):
    body = _student_token(call)
    assert body["student_id"] == "student-1"


def test_student_token_rejected_on_teacher_route(call):
    token = _student_token(call)["access_token"]
    r = call("GET", "/api/v1/dashboard/teacher", token=token)
    assert r.status_code == 403


def test_register_then_login(call):
    r = call("POST", "/api/v1/auth/register", json={
        "school_code": "SCH-T01", "school_name": "Test School", "district": "",
        "teacher_code": "T-NEW", "teacher_name": "New Teacher",
        "class_name": "G1", "pin": "4321",
    })
    assert r.status_code == 200
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-NEW", "pin": "4321"})
    assert r.status_code == 200


def test_register_duplicate_teacher_code_conflict(call):
    r = call("POST", "/api/v1/auth/register", json={
        "school_code": "SCH-T01", "school_name": "Test School", "district": "",
        "teacher_code": "T-TEST", "teacher_name": "Imposter",
        "class_name": "G1", "pin": "1111",
    })
    assert r.status_code == 409
