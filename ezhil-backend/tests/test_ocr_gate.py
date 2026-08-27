"""
OCR quality-gate tests.

The gate previously rejected below 0.45. A real EasyOCR extraction scoring
0.533 passed it, was 17.6% word-accurate, and produced a lesson containing
non-words that a dyslexic child would then have practised reading. These tests
pin the tightened behaviour so that cannot regress.
"""
import pytest

from config import get_settings
from services import integrity_checker
from services.integrity_checker import check_source_integrity, review_requirement

# Enough clean text that the length and academic-ratio gates pass, leaving the
# confidence gate as the only thing under test.
_TEXT = (
    "ஒரு பெரிய யானை காட்டில் வாழ்ந்தது. அது எறும்பை கேலி செய்தது. "
    "எறும்புகள் யானையின் காதுக்குள் சென்றன. இறுதியில் யானை பணிவை கற்றது. "
    "இந்தக் கதை நமக்கு பணிவின் அருமையை கற்றுத் தருகிறது."
)
_STATS = {"CONTENT": 8, "METADATA": 0}


def _check(conf, **kw):
    return check_source_integrity(_TEXT, _TEXT, _STATS, ocr_confidence=conf, **kw)


def test_floor_is_configured_not_hardcoded():
    assert get_settings().OCR_MIN_CONFIDENCE == pytest.approx(0.75)


def test_rejects_the_confidence_that_used_to_pass():
    # The exact score of the extraction that produced a garbage lesson.
    ok, _, msg = _check(0.533)
    assert not ok
    assert "blurry or dark" in msg


def test_rejects_just_below_floor():
    ok, _, msg = _check(0.74)
    assert not ok
    assert msg


def test_accepts_a_clean_paddle_page():
    # PaddleOCR's measured score on the reference page.
    ok, diag, msg = _check(0.9532, engine="paddleocr")
    assert ok and msg is None
    assert not diag.get("requires_review")


def test_marginal_confidence_is_accepted_but_flagged():
    ok, diag, msg = _check(0.80, engine="paddleocr")
    assert ok and msg is None
    assert diag["requires_review"] is True
    assert "check it" in diag["ocr_warning"]


def test_degraded_engine_always_requires_review():
    # EasyOCR rates its own bad Tamil output highly, so a high score from it
    # must not buy a pass.
    ok, diag, msg = _check(0.99, engine="easyocr", degraded=True)
    assert ok and msg is None
    assert diag["requires_review"] is True


def test_engine_is_recorded_in_diagnostics():
    _, diag, _ = _check(0.96, engine="paddleocr")
    assert diag["ocr_engine"] == "paddleocr"


# ── review_requirement: the same gate, surfaced at extraction time ────────────

def test_review_not_required_for_clean_paddle():
    needs, reason = review_requirement(0.9532, engine="paddleocr")
    assert needs is False and reason is None


def test_review_required_for_any_non_paddle_engine():
    needs, reason = review_requirement(0.99, engine="easyocr")
    assert needs is True
    assert "backup reader" in reason


def test_review_required_below_floor():
    needs, reason = review_requirement(0.60, engine="paddleocr")
    assert needs is True
    assert "hard to read" in reason


def test_review_thresholds_track_the_setting():
    floor = get_settings().OCR_MIN_CONFIDENCE
    assert review_requirement(floor - 0.01, engine="paddleocr")[0] is True
    assert review_requirement(floor + 0.20, engine="paddleocr")[0] is False


# ── The generate endpoint must not accept a flagged extraction on trust ──────

def _teacher_token(call):
    r = call("POST", "/api/v1/auth/login",
             json={"school_code": "SCH-T01", "teacher_id": "T-TEST", "pin": "1234"})
    assert r.status_code == 200
    return r.json()["access_token"]


def _flag_extraction(hash_: str, **over):
    """Seed the router's extraction cache as a real upload would."""
    from routers.studio import _EXTRACTION_CACHE

    _EXTRACTION_CACHE[hash_] = {
        "average_confidence": 0.80, "minimum_confidence": 0.78,
        "page_confidence": [0.80], "engine": "easyocr",
        "degraded": True, "requires_review": True, **over,
    }


def test_generate_refuses_flagged_extraction_without_review(call):
    token = _teacher_token(call)
    _flag_extraction("hash-needs-review")
    r = call("POST", "/api/v1/studio/generate", token=token, json={
        "ocr_text": _TEXT, "difficulty": 1, "language": "tamil",
        "source_hash": "hash-needs-review",
    })
    assert r.status_code == 428
    assert "check the extracted text" in r.json()["detail"]


def test_generate_refuses_when_client_omits_the_flag(call):
    # A client that simply does not send text_reviewed must not slip through.
    token = _teacher_token(call)
    _flag_extraction("hash-omitted-flag")
    r = call("POST", "/api/v1/studio/generate", token=token, json={
        "ocr_text": _TEXT, "source_hash": "hash-omitted-flag",
        "text_reviewed": False,
    })
    assert r.status_code == 428


def test_generate_proceeds_past_the_gate_once_reviewed(call):
    token = _teacher_token(call)
    _flag_extraction("hash-reviewed")
    r = call("POST", "/api/v1/studio/generate", token=token, json={
        "ocr_text": _TEXT, "source_hash": "hash-reviewed", "text_reviewed": True,
    })
    # Past the review gate. Tests run with DEMO_MODE so generation itself is
    # stubbed — the point is only that 428 is no longer raised.
    assert r.status_code != 428


def test_generate_unflagged_extraction_needs_no_review(call):
    token = _teacher_token(call)
    _flag_extraction("hash-clean", engine="paddleocr", degraded=False,
                     requires_review=False, average_confidence=0.9532)
    r = call("POST", "/api/v1/studio/generate", token=token, json={
        "ocr_text": _TEXT, "source_hash": "hash-clean",
    })
    assert r.status_code != 428


# ── Reviewed text must not be blocked by the photo's score ───────────────────

def test_reviewed_text_bypasses_the_confidence_floor():
    # The floor scores the photograph. Once a teacher has read and corrected
    # the extraction, blocking on that score would make review a dead end.
    ok, diag, msg = _check(0.533, engine="easyocr", degraded=True, text_reviewed=True)
    assert ok and msg is None
    assert diag["text_reviewed"] is True


def test_reviewed_text_still_fails_the_content_gates():
    # Review vouches for the words, not for there being enough of them.
    ok, _, msg = check_source_integrity(
        "x", "x", {"CONTENT": 1}, ocr_confidence=0.99, text_reviewed=True
    )
    assert not ok
    assert msg


def test_unreviewed_text_below_floor_is_still_rejected():
    ok, _, msg = _check(0.533, engine="paddleocr", text_reviewed=False)
    assert not ok and msg


# ── Worker memory guard ──────────────────────────────────────────────────────

def test_memory_check_reports_a_shortfall(monkeypatch):
    # A worker that cannot fit dies from a native segfault with no traceback,
    # so the shortfall has to be reported before it starts.
    from services import paddle_client

    monkeypatch.setattr(paddle_client, "free_memory_mb", lambda: 900.0)
    msg = paddle_client._check_memory()
    assert msg and "900 MB free" in msg


def test_memory_check_silent_when_there_is_room(monkeypatch):
    from services import paddle_client

    monkeypatch.setattr(paddle_client, "free_memory_mb", lambda: 8000.0)
    assert paddle_client._check_memory() is None


def test_memory_check_silent_when_unreadable(monkeypatch):
    from services import paddle_client

    monkeypatch.setattr(paddle_client, "free_memory_mb", lambda: None)
    assert paddle_client._check_memory() is None


def test_generate_recomputes_gate_when_hash_is_not_cached(call):
    # Multi-file uploads combine extractions into text no cached hash matches.
    # The gate must be rebuilt from the reported confidence, not skipped.
    token = _teacher_token(call)
    r = call("POST", "/api/v1/studio/generate", token=token, json={
        "ocr_text": _TEXT, "source_hash": "hash-never-cached",
        "average_confidence": 0.62, "minimum_confidence": 0.62,
        "ocr_engine": "paddleocr",
    })
    assert r.status_code == 428


def test_generate_uncached_clean_extraction_passes(call):
    token = _teacher_token(call)
    r = call("POST", "/api/v1/studio/generate", token=token, json={
        "ocr_text": _TEXT, "source_hash": "hash-never-cached-2",
        "average_confidence": 0.95, "minimum_confidence": 0.93,
        "ocr_engine": "paddleocr",
    })
    assert r.status_code != 428


def test_spawn_refuses_instead_of_dying_when_memory_is_short(monkeypatch):
    # Spawning into insufficient memory costs a two-minute load and then a
    # segfault, with the teacher watching a spinner throughout.
    from services import paddle_client

    monkeypatch.setattr(paddle_client, "free_memory_mb", lambda: 800.0)
    called = []
    monkeypatch.setattr(paddle_client.subprocess, "Popen",
                        lambda *a, **k: called.append(a) or (_ for _ in ()).throw(AssertionError))
    assert paddle_client._spawn() is None
    assert not called, "must not launch a worker that cannot fit"
    assert "800 MB free" in paddle_client.unavailable_reason()


def test_reader_outage_is_not_blamed_on_the_photo(monkeypatch):
    from services import paddle_client

    monkeypatch.setattr(paddle_client, "unavailable_reason", lambda: "only 800 MB free")
    _, reason = review_requirement(0.0, engine="none")
    assert "reader is not available" in reason
    assert "retake" not in reason.lower()


def test_unreadable_photo_still_says_retake(monkeypatch):
    from services import paddle_client

    monkeypatch.setattr(paddle_client, "unavailable_reason", lambda: None)
    _, reason = review_requirement(0.0, engine="none")
    assert "retake the photo" in reason


# ── Reclaiming the LLM's memory for OCR ──────────────────────────────────────

def test_ocr_asks_the_llm_to_stand_down_before_giving_up(monkeypatch):
    # Gemma holds most of what the OCR worker needs. The two are never used at
    # the same instant — a teacher reads a page, checks the text, then
    # generates — so handing the memory over beats refusing to read at all.
    from services import paddle_client, slm_service

    freed = []
    monkeypatch.setattr(slm_service, "release_memory", lambda reason="": freed.append(reason) or True)
    monkeypatch.setattr(paddle_client, "free_memory_mb", lambda: 800.0)
    monkeypatch.setattr(paddle_client.time, "sleep", lambda _s: None)
    monkeypatch.setattr(paddle_client.subprocess, "Popen",
                        lambda *a, **k: (_ for _ in ()).throw(AssertionError("should not spawn")))

    assert paddle_client._spawn() is None      # still short after releasing
    assert freed == ["OCR"], "OCR must ask the LLM for its memory first"


def test_release_memory_is_a_no_op_when_the_llm_is_not_running(monkeypatch):
    from services import slm_service

    monkeypatch.setattr(slm_service, "is_server_running", lambda: False)
    assert slm_service.release_memory("OCR") is False


def test_release_memory_respects_the_setting(monkeypatch):
    # On a host with memory to spare the reload would be pure cost.
    from config import get_settings
    from services import slm_service

    cfg = get_settings()
    monkeypatch.setattr(cfg, "SLM_RELEASE_FOR_OCR", False)
    monkeypatch.setattr(slm_service, "is_server_running", lambda: True)
    assert slm_service.release_memory("OCR") is False
