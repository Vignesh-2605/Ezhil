"""
Heavy model processes must not outlive the API.

llama-server holds several GB and the OCR worker peaks at 3.5 GB. When the API
crashed under that very memory pressure, both stayed running and kept the
memory the next start needed — so one crash made every following start likelier
to fail. Clean shutdown is easy; these cover the crash.
"""
import os
import subprocess
import sys
import textwrap
import time

import pytest

pytestmark = pytest.mark.skipif(
    sys.platform != "win32", reason="job objects are the Windows mechanism"
)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _alive(pid: int) -> bool:
    import ctypes

    h = ctypes.windll.kernel32.OpenProcess(0x00100000, False, pid)
    if not h:
        return False
    running = ctypes.windll.kernel32.WaitForSingleObject(h, 0) == 258
    ctypes.windll.kernel32.CloseHandle(h)
    return running


def test_adopted_child_dies_with_a_crashing_parent():
    parent_src = textwrap.dedent(f"""
        import sys, subprocess, time
        sys.path.insert(0, r"{ROOT}")
        from services import child_reaper
        child = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(120)"])
        child_reaper.adopt(child, "test-child")
        print("CHILD_PID", child.pid, flush=True)
        time.sleep(120)
    """)

    parent = subprocess.Popen(
        [sys.executable, "-c", parent_src], stdout=subprocess.PIPE, text=True
    )
    try:
        child_pid = int(parent.stdout.readline().split()[1])
        assert _alive(child_pid)

        # kill(), not terminate() — no shutdown handler runs, as in a segfault.
        parent.kill()
        parent.wait(timeout=10)

        deadline = time.time() + 15
        while time.time() < deadline:
            if not _alive(child_pid):
                return
            time.sleep(0.5)
        pytest.fail("child outlived its parent — the reaper is not working")
    finally:
        if parent.poll() is None:
            parent.kill()


def test_terminate_is_safe_on_a_dead_or_missing_process():
    from services import child_reaper

    child_reaper.terminate(None, "nothing")            # must not raise
    p = subprocess.Popen([sys.executable, "-c", "pass"])
    p.wait()
    child_reaper.terminate(p, "already-exited")        # must not raise


def test_adopt_never_raises_when_the_job_is_unavailable(monkeypatch):
    # Failing to adopt costs a leaked process on crash; refusing to start the
    # model would cost the feature. It must degrade, not throw.
    from services import child_reaper

    monkeypatch.setattr(child_reaper, "_windows_job", lambda: None)
    p = subprocess.Popen([sys.executable, "-c", "pass"])
    try:
        child_reaper.adopt(p, "orphan")
    finally:
        p.wait()
