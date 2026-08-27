"""
Make heavy child processes die with the API.

llama-server holds 2.5-5 GB and the OCR worker peaks at 3.5 GB. Neither is
reaped when the API goes away: a normal shutdown can stop them explicitly, but
a crash cannot, and on this project the API *has* crashed — from the memory
pressure those very children create. The orphan then keeps the memory the next
start needs, so one crash makes every following start likelier to fail.

On Windows a Job Object with JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE solves the
crash case properly: the kernel kills every assigned process when the last
handle to the job closes, which happens automatically when this process dies
however it dies. On POSIX the child is put in our process group instead, and
callers still stop children explicitly on clean shutdown.
"""
from __future__ import annotations

import logging
import subprocess
import sys

logger = logging.getLogger(__name__)

_job = None
_unavailable_logged = False


def _windows_job():
    """A kill-on-close job object, created once and cached."""
    global _job, _unavailable_logged
    if _job is not None:
        return _job

    try:
        import ctypes
        from ctypes import wintypes

        class _BasicLimits(ctypes.Structure):
            _fields_ = [
                ("PerProcessUserTimeLimit", ctypes.c_int64),
                ("PerJobUserTimeLimit", ctypes.c_int64),
                ("LimitFlags", wintypes.DWORD),
                ("MinimumWorkingSetSize", ctypes.c_size_t),
                ("MaximumWorkingSetSize", ctypes.c_size_t),
                ("ActiveProcessLimit", wintypes.DWORD),
                ("Affinity", ctypes.POINTER(ctypes.c_ulong)),
                ("PriorityClass", wintypes.DWORD),
                ("SchedulingClass", wintypes.DWORD),
            ]

        class _IoCounters(ctypes.Structure):
            _fields_ = [
                ("ReadOperationCount", ctypes.c_uint64),
                ("WriteOperationCount", ctypes.c_uint64),
                ("OtherOperationCount", ctypes.c_uint64),
                ("ReadTransferCount", ctypes.c_uint64),
                ("WriteTransferCount", ctypes.c_uint64),
                ("OtherTransferCount", ctypes.c_uint64),
            ]

        class _ExtendedLimits(ctypes.Structure):
            _fields_ = [
                ("BasicLimitInformation", _BasicLimits),
                ("IoInfo", _IoCounters),
                ("ProcessMemoryLimit", ctypes.c_size_t),
                ("JobMemoryLimit", ctypes.c_size_t),
                ("PeakProcessMemoryUsed", ctypes.c_size_t),
                ("PeakJobMemoryUsed", ctypes.c_size_t),
            ]

        JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000
        JobObjectExtendedLimitInformation = 9

        handle = ctypes.windll.kernel32.CreateJobObjectW(None, None)
        if not handle:
            raise OSError("CreateJobObjectW returned NULL")

        info = _ExtendedLimits()
        info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not ctypes.windll.kernel32.SetInformationJobObject(
            handle, JobObjectExtendedLimitInformation,
            ctypes.byref(info), ctypes.sizeof(info),
        ):
            raise OSError("SetInformationJobObject failed")

        _job = handle
        logger.info("Child reaper active — heavy models will exit with this process")
        return _job

    except Exception as exc:  # noqa: BLE001 — never block a model from starting
        if not _unavailable_logged:
            logger.warning(
                "Child reaper unavailable (%s); model processes may outlive a crash", exc
            )
            _unavailable_logged = True
        return None


def adopt(proc: subprocess.Popen, name: str = "child") -> None:
    """
    Tie [proc]'s lifetime to this process.

    Best effort by design: failing to adopt costs a leaked process on crash,
    which is far better than refusing to start the model at all.
    """
    if sys.platform != "win32":
        return  # start_new_session=False already keeps it in our process group

    job = _windows_job()
    if job is None:
        return

    try:
        import ctypes

        PROCESS_SET_QUOTA, PROCESS_TERMINATE = 0x0100, 0x0001
        h = ctypes.windll.kernel32.OpenProcess(
            PROCESS_SET_QUOTA | PROCESS_TERMINATE, False, proc.pid
        )
        if not h:
            raise OSError(f"OpenProcess({proc.pid}) failed")
        try:
            if not ctypes.windll.kernel32.AssignProcessToJobObject(job, h):
                raise OSError("AssignProcessToJobObject failed")
        finally:
            ctypes.windll.kernel32.CloseHandle(h)
        logger.debug("Reaper adopted %s (pid %s)", name, proc.pid)
    except Exception as exc:  # noqa: BLE001
        logger.warning("Could not adopt %s (pid %s): %s", name, proc.pid, exc)


def terminate(proc: subprocess.Popen | None, name: str = "child", timeout: float = 5.0) -> None:
    """Stop [proc] on a clean shutdown, escalating if it ignores the request."""
    if proc is None or proc.poll() is not None:
        return
    try:
        proc.terminate()
        proc.wait(timeout=timeout)
        logger.info("Stopped %s (pid %s)", name, proc.pid)
    except Exception:  # noqa: BLE001
        try:
            proc.kill()
            logger.info("Killed %s (pid %s)", name, proc.pid)
        except Exception:  # noqa: BLE001
            pass
