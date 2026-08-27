"""
The two heavy models must not fight each other while loading.

On a 16 GB host the SLM and OCR pre-loads used to run concurrently, and the
combination defeated itself: the SLM faulted in a 3.9 GB model while OCR
measured what was free, decided it could not fit, stopped the SLM to make
room, then still refused because the pages had not come back. Both ended up
down. Worse, clearing the global mid-load crashed the SLM loader with
`AttributeError: 'NoneType' object has no attribute 'poll'`, and the traceback
was swallowed into "Future exception was never retrieved".
"""
import subprocess
import types

import pytest

from services import paddle_client, slm_service


class _FakeProc:
    """A process that stays alive and never answers /health."""

    def __init__(self):
        self.returncode = None
        self.pid = 99999

    def poll(self):
        return None


@pytest.fixture
def loadable_slm(monkeypatch, tmp_path):
    """_load_slm bails before spawning unless the binary and model exist."""
    binary = tmp_path / "llama-server.exe"
    gguf = tmp_path / "model.gguf"
    binary.write_bytes(b"")
    gguf.write_bytes(b"")
    monkeypatch.setattr(slm_service, "_SERVER_BIN", binary)
    monkeypatch.setattr(slm_service, "_GGUF_PATH", gguf)

    import config

    monkeypatch.setattr(
        config,
        "get_settings",
        lambda: types.SimpleNamespace(
            DEMO_MODE=False, SLM_RELEASE_FOR_OCR=True, SLM_IDLE_TIMEOUT_S=0
        ),
    )

    from services import child_reaper

    monkeypatch.setattr(child_reaper, "adopt", lambda proc, name: None)
    monkeypatch.setattr(child_reaper, "terminate", lambda proc, name: None)

    monkeypatch.setattr(slm_service, "_load_attempted", False)
    monkeypatch.setattr(slm_service, "_backend", None)
    monkeypatch.setattr(slm_service, "_server_proc", None)


def test_stopping_the_server_mid_load_does_not_crash(loadable_slm, monkeypatch):
    fake = _FakeProc()
    monkeypatch.setattr(subprocess, "Popen", lambda *a, **k: fake)

    def _healthy(timeout: float = 2.0) -> bool:
        # Once the server is spawned, do what the OCR pre-load does: take its
        # memory away. That clears _server_proc from under the wait loop.
        if slm_service._server_proc is fake:
            slm_service.release_memory("OCR")
        return False

    monkeypatch.setattr(slm_service, "_server_healthy", _healthy)

    assert slm_service._load_slm() is False
    assert slm_service._backend is None
    # The stop resets the attempt flag so the next request starts it again
    # rather than dropping to templates for the rest of the process's life.
    assert slm_service._load_attempted is False


def test_a_server_that_becomes_healthy_still_loads(loadable_slm, monkeypatch):
    fake = _FakeProc()
    monkeypatch.setattr(subprocess, "Popen", lambda *a, **k: fake)

    seen: list[int] = []

    def _healthy(timeout: float = 2.0) -> bool:
        seen.append(1)
        return len(seen) > 1        # not the pre-spawn reuse check, the next one

    monkeypatch.setattr(slm_service, "_server_healthy", _healthy)

    assert slm_service._load_slm() is True
    assert slm_service._backend == "server"
    assert slm_service._server_proc is fake


def test_memory_wait_gives_the_pages_time_to_come_back(monkeypatch):
    # Terminating a 3.9 GB process does not free it synchronously. The fixed
    # 2 s sleep this replaced measured 457 MB and refused over a stale figure.
    calls: list[int] = []

    def _check() -> str | None:
        calls.append(1)
        return "only 457 MB free" if len(calls) < 4 else None

    monkeypatch.setattr(paddle_client, "_check_memory", _check)
    monkeypatch.setattr(paddle_client.time, "sleep", lambda s: None)

    assert paddle_client._wait_for_memory(timeout_s=30) is None
    assert len(calls) == 4


def test_memory_wait_still_gives_up_when_it_never_recovers(monkeypatch):
    monkeypatch.setattr(paddle_client, "_check_memory", lambda: "only 457 MB free")
    monkeypatch.setattr(paddle_client.time, "sleep", lambda s: None)

    # Honest refusal beats spawning into a segfault during detection.
    assert paddle_client._wait_for_memory(timeout_s=0.1) == "only 457 MB free"


def test_status_never_starts_a_worker(monkeypatch):
    """
    Reading OCR status must not spawn.

    status() used to fall through to _ensure(), so a /health poll could trigger
    the memory check, stand the LLM down, and wait for a worker to come up.
    /health awaits this on the event loop, so one poll serialised the whole API:
    a load test measured every endpoint at ~16 s, and /health itself at 8 s.
    """
    spawned = []
    monkeypatch.setattr(paddle_client, "_ensure", lambda: spawned.append(1))
    monkeypatch.setattr(paddle_client, "_healthy", lambda timeout=2.0: False)
    monkeypatch.setattr(paddle_client, "_health_checked_at", 0.0)

    assert paddle_client.status() == "unavailable"
    assert spawned == [], "status() started a worker"


def test_status_reports_starting_while_a_spawn_holds_the_lock(monkeypatch):
    monkeypatch.setattr(paddle_client, "_healthy", lambda timeout=2.0: False)
    monkeypatch.setattr(paddle_client, "_health_checked_at", 0.0)
    with paddle_client._lock:
        # RLock is reentrant, so a same-thread acquire would succeed and report
        # "unavailable". Another thread is what a real spawn looks like.
        import threading
        out = []
        t = threading.Thread(target=lambda: out.append(paddle_client.status()))
        t.start(); t.join()
    assert out == ["starting"]


def test_health_probe_is_cached(monkeypatch):
    calls = []
    monkeypatch.setattr(paddle_client, "_healthy", lambda timeout=2.0: (calls.append(1), True)[1])
    monkeypatch.setattr(paddle_client, "_health_checked_at", 0.0)
    for _ in range(5):
        paddle_client.status()
    assert len(calls) == 1, f"probed the worker {len(calls)} times for 5 status reads"
