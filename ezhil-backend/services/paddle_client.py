"""
Client for the out-of-process PaddleOCR worker.

Owns the worker's lifecycle — lazy start, health, restart after a crash — and
talks to it over loopback HTTP. Nothing here imports paddle; that is the whole
point of the worker (see services/ocr_worker).
"""
from __future__ import annotations

import json
import logging
import os
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

logger = logging.getLogger(__name__)

_ROOT = Path(__file__).resolve().parent.parent
_STDERR_LOG = Path(tempfile.gettempdir()) / "ezhil_ocr_worker.log"

PORT = int(os.getenv("PADDLE_WORKER_PORT", "8092"))
_BASE = f"http://127.0.0.1:{PORT}"

# Guards the worker's lifecycle. Inference itself is serialised inside the
# worker, so this only needs to stop concurrent callers — /health among them —
# from racing to spawn duplicate processes.
_lock = threading.RLock()
_proc: subprocess.Popen | None = None
_unavailable_reason: str | None = None

# Short-lived cache for the worker health probe, so a burst of /health polls
# does not become a burst of sockets. See _healthy_cached().
_HEALTH_TTL_S = 1.0
_health_checked_at: float = 0.0
_health_ok: bool = False


def free_memory_mb() -> float | None:
    """Free physical memory, or None if it cannot be read on this platform."""
    try:
        if sys.platform == "win32":
            import ctypes

            class _Status(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong),
                    ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]

            st = _Status()
            st.dwLength = ctypes.sizeof(st)
            ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(st))
            return st.ullAvailPhys / 1048576
        with open("/proc/meminfo") as fh:
            for line in fh:
                if line.startswith("MemAvailable:"):
                    return int(line.split()[1]) / 1024
    except Exception:  # noqa: BLE001
        return None
    return None


def _check_memory() -> str | None:
    """
    Warn before starting a worker that cannot fit.

    PaddleOCR's detection stage peaks around 3.5 GB. When it does not fit, the
    process dies from a native segfault with no Python traceback and no log
    line, which is close to undiagnosable after the fact — so say so up front.
    """
    from config import get_settings

    need = get_settings().OCR_WORKER_PEAK_MB
    free = free_memory_mb()
    if free is None or free >= need:
        return None
    return (
        f"only {free:.0f} MB free, OCR needs about {need} MB during detection; "
        f"close other models or run the worker on another host"
    )


def _wait_for_memory(timeout_s: float | None = None) -> str | None:
    """
    Re-check free memory until it recovers, or the deadline passes.

    Terminating llama-server hands its pages back asynchronously, and a 3.9 GB
    working set does not come back inside the fixed two-second sleep this
    replaced. OCR was refusing over a figure that was already stale — 457 MB
    measured moments after the kill — which left the LLM stopped *and* OCR
    down, the worst of both.
    """
    if timeout_s is None:
        from config import get_settings

        timeout_s = get_settings().OCR_MEMORY_WAIT_S
    deadline = time.time() + timeout_s
    shortfall = _check_memory()
    while shortfall and time.time() < deadline:
        time.sleep(1.0)
        shortfall = _check_memory()
    return shortfall


def _worker_log_tail(lines: int = 15) -> str:
    try:
        text = _STDERR_LOG.read_text(encoding="utf-8", errors="replace")
        return "".join(text.splitlines(keepends=True)[-lines:]).strip() or "(worker log empty)"
    except Exception:  # noqa: BLE001
        return "(no worker log)"


def _get_json(path: str, timeout: float) -> dict | None:
    try:
        with urllib.request.urlopen(f"{_BASE}{path}", timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception:  # noqa: BLE001 — any failure means "not answering"
        return None


def _post_json(path: str, payload: dict, timeout: float) -> dict | None:
    req = urllib.request.Request(
        f"{_BASE}{path}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.URLError as exc:
        logger.warning("PaddleOCR request failed: %s", exc)
        return None
    except Exception as exc:  # noqa: BLE001
        logger.warning("PaddleOCR request failed: %s", exc)
        return None


def _healthy(timeout: float = 2.0) -> bool:
    resp = _get_json("/health", timeout)
    return bool(resp and resp.get("ok"))


def _spawn() -> subprocess.Popen | None:
    """Start the worker and wait for it to answer /health. Caller holds _lock."""
    global _unavailable_reason

    # Refuse rather than try. Spawning into insufficient memory means the
    # worker loads for a minute or two, segfaults during detection, and the
    # teacher watches a spinner the whole time before being told nothing could
    # be read. Failing here turns that into an immediate, honest answer.
    shortfall = _check_memory()
    if shortfall:
        # Most of the missing memory is usually the LLM sitting idle. Ask it to
        # stand down before giving up — it reloads on the next generation, and
        # the teacher reviews the extracted text in between.
        from services import slm_service

        if slm_service.release_memory("OCR"):
            shortfall = _wait_for_memory()

    if shortfall:
        _unavailable_reason = shortfall
        logger.error("PaddleOCR not started: %s", shortfall)
        return None

    try:
        log = open(_STDERR_LOG, "a", encoding="utf-8", errors="replace")
        proc = subprocess.Popen(
            [sys.executable, "-m", "services.ocr_worker", "--port", str(PORT)],
            cwd=str(_ROOT),
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=log,
            env={
                **os.environ,
                "PYTHONIOENCODING": "utf-8",
                # So the worker exits with us rather than leaking its memory.
                "EZHIL_PARENT_PID": str(os.getpid()),
            },
        )
    except Exception as exc:  # noqa: BLE001
        _unavailable_reason = f"could not start worker: {exc}"
        logger.warning("PaddleOCR %s", _unavailable_reason)
        return None

    # The worker also watches our pid, but the job object covers the gap
    # between a hard crash and the next poll.
    from services import child_reaper

    child_reaper.adopt(proc, "ocr-worker")

    # Loading the Tamil models takes ~10s on an idle machine and has taken
    # nearly two minutes with the LLM loading alongside it, so this waits
    # generously rather than declaring failure early.
    deadline = time.time() + float(os.getenv("PADDLE_LOAD_TIMEOUT_S", "240"))
    while time.time() < deadline:
        if _healthy():
            logger.info("PaddleOCR worker ready on port %s (pid %s)", PORT, proc.pid)
            _unavailable_reason = None
            return proc
        if proc.poll() is not None:
            _unavailable_reason = f"worker exited with code {proc.returncode}"
            logger.error(
                "PaddleOCR %s. Worker log tail:%s%s",
                _unavailable_reason, chr(10), _worker_log_tail(),
            )
            return None
        time.sleep(1.0)

    _unavailable_reason = "worker did not become healthy in time"
    logger.warning("PaddleOCR %s. Worker log tail:%s%s",
                   _unavailable_reason, chr(10), _worker_log_tail())
    _terminate(proc)
    return None


def _ensure() -> subprocess.Popen | None:
    """Caller must hold _lock."""
    global _proc
    if _proc is not None and _proc.poll() is None:
        return _proc
    if _proc is not None:
        logger.warning("PaddleOCR worker exited (code %s) — restarting", _proc.returncode)
        _proc = None
    # A worker from a previous run of this API may still own the port and be
    # perfectly healthy; adopt it rather than fighting over the bind.
    if _healthy():
        logger.info("PaddleOCR worker already listening on port %s — reusing", PORT)
        return _ADOPTED
    _proc = _spawn()
    return _proc


class _Adopted:
    """Stands in for a worker this process did not start (so never kills it)."""

    def poll(self):
        return None if _healthy() else 0


_ADOPTED = _Adopted()


def _healthy_cached() -> bool:
    """
    _healthy() with a short TTL.

    /health is polled by both apps, so without this every poll opens a socket
    to the worker. The TTL is well under any sensible poll interval, so the
    answer is still current.
    """
    global _health_checked_at, _health_ok
    now = time.time()
    if now - _health_checked_at < _HEALTH_TTL_S:
        return _health_ok
    _health_ok = _healthy()
    _health_checked_at = time.time()
    return _health_ok


def status() -> str:
    """
    "ready" | "starting" | "unavailable" — observation only, never starts a worker.

    This used to fall through to _ensure(), so reading the status could spawn:
    the memory check, the LLM standing down, a wait for the pages to come back,
    then up to four minutes waiting for the worker to answer. /health calls
    this, and the route awaits it on the event loop, so a single health poll
    stalled every other request behind it. A 10-user load test measured every
    endpoint — including /health itself — at about 16 s for exactly that reason.

    Starting the worker is now ensure_available()'s job, which the pre-load
    calls from a background thread where blocking is what you want.
    """
    if _healthy_cached():
        return "ready"
    # RLock has no locked(); a non-blocking acquire is how you ask. Holding it
    # only happens while a spawn is in progress.
    if not _lock.acquire(blocking=False):
        return "starting"
    _lock.release()
    return "unavailable"


def ensure_available() -> bool:
    """True when the worker is running, or could be started. May block for minutes."""
    with _lock:
        return _ensure() is not None


def is_available() -> bool:
    """Deprecated alias — kept because the pre-load and older callers use it."""
    return ensure_available()


def unavailable_reason() -> str | None:
    return _unavailable_reason


def recognise(image_bytes: bytes, lang: str = "ta", timeout: float | None = None) -> dict | None:
    """
    Run OCR on [image_bytes].

    Returns the worker's result dict, or None when Paddle is unusable so the
    caller can fall back to another engine.
    """
    timeout = timeout or float(os.getenv("PADDLE_REQUEST_TIMEOUT_S", "300"))

    with _lock:
        if _ensure() is None:
            return None

        # The worker reads a path rather than the bytes: a few MB of image
        # through JSON would need base64 and inflate the payload by a third.
        tmp = Path(tempfile.gettempdir()) / f"ezhil_ocr_{os.getpid()}_{threading.get_ident()}.png"
        try:
            tmp.write_bytes(image_bytes)
            resp = _post_json("/ocr", {"image_path": str(tmp), "lang": lang}, timeout)
        finally:
            tmp.unlink(missing_ok=True)

    if resp is None:
        shortfall = _check_memory()
        if shortfall:
            logger.error(
                "PaddleOCR failed mid-request and %s. This is the usual cause of a "
                "silent worker death.", shortfall,
            )
        else:
            logger.warning("PaddleOCR did not answer within %.0fs", timeout)
        return None
    if not resp.get("ok"):
        logger.warning("PaddleOCR error: %s", resp.get("error"))
        return None
    return resp


def _terminate(proc: subprocess.Popen) -> None:
    try:
        proc.terminate()
        proc.wait(timeout=5)
    except Exception:  # noqa: BLE001
        try:
            proc.kill()
        except Exception:  # noqa: BLE001
            pass


def shutdown() -> None:
    global _proc
    with _lock:
        if isinstance(_proc, subprocess.Popen):
            _terminate(_proc)
        _proc = None
