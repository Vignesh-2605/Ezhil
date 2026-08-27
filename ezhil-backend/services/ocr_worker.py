"""
PaddleOCR worker — a tiny HTTP service that runs in its own process.

Two reasons it is a separate process rather than a module:

1. PaddlePaddle ships MKL/OpenMP DLLs that shadow the ones PyTorch loads. With
   both imported into one process on Windows, torch dies with
   `WinError 126: Error loading torch_python.dll`, taking EasyOCR and the NLLB
   translator with it. The API process never imports paddle.
2. Paddle's C++ layer writes to stdout and stderr at will, so those streams
   cannot carry a protocol.

An earlier version spoke line-delimited JSON over stdin/stdout. Under the API
server the worker kept reaching EOF on stdin seconds after starting and exiting
mid-request, which read as a timeout and silently demoted every upload to the
EasyOCR fallback. HTTP on the loopback interface has none of that pipe
lifetime or buffering behaviour, and it can be exercised with curl.

    GET  /health -> {"ok": true, "langs": ["ta"]}
    POST /ocr    <- {"image_path": "C:/…/page.png", "lang": "ta"}
                 -> {"ok": true, "text": "…", "average_confidence": 0.95, …}

Run it by hand to check an install:

    python -m services.ocr_worker --port 8092
    python -m services.ocr_worker --selftest page.png ta
"""
from __future__ import annotations

import json
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# Keep paddle's C++ logging quiet — it is noise in the worker log.
os.environ.setdefault("GLOG_minloglevel", "3")
os.environ.setdefault("FLAGS_call_stack_level", "0")

DEFAULT_LANG = os.getenv("PADDLE_OCR_LANG", "ta")

_engines: dict[str, object] = {}
# PaddleOCR predictors are not thread-safe, and the server is threading, so
# inference is serialised. It is CPU-bound anyway.
_infer_lock = threading.Lock()


def _load(lang: str = DEFAULT_LANG):
    if lang in _engines:
        return _engines[lang]

    import warnings

    warnings.filterwarnings("ignore")
    from paddleocr import PaddleOCR

    kwargs = dict(
        lang=lang,
        # Document orientation, unwarping and textline orientation each add a
        # model and a second or more per page. Photographs of a textbook held
        # flat do not need them; enable via env if field images prove skewed.
        use_doc_orientation_classify=os.getenv("PADDLE_DOC_ORIENT", "0") == "1",
        use_doc_unwarping=os.getenv("PADDLE_UNWARP", "0") == "1",
        use_textline_orientation=os.getenv("PADDLE_TEXTLINE_ORIENT", "0") == "1",
        # oneDNN must stay off. Paddle 3.0's oneDNN kernels raise
        # "ConvertPirAttribute2RuntimeAttribute not support
        # [pir::ArrayAttribute<pir::DoubleAttribute>]" on every predict() call
        # with these models, so inference fails outright. It is also four times
        # faster without: 13s a page against 53s+.
        enable_mkldnn=os.getenv("PADDLE_MKLDNN", "0") == "1",
        cpu_threads=int(os.getenv("PADDLE_CPU_THREADS", "4")),
    )
    det = os.getenv("PADDLE_DET_MODEL")
    if det:
        kwargs["text_detection_model_name"] = det

    _engines[lang] = PaddleOCR(**kwargs)
    return _engines[lang]


def recognise(image_path: str, lang: str = DEFAULT_LANG) -> dict:
    with _infer_lock:
        ocr = _load(lang)
        pages = ocr.predict(image_path)

    texts: list[str] = []
    scores: list[float] = []
    for page in pages:
        texts.extend(page.get("rec_texts", []) or [])
        scores.extend(float(s) for s in (page.get("rec_scores", []) or []))

    kept = [(t.strip(), s) for t, s in zip(texts, scores) if t and t.strip()]
    if not kept:
        return {
            "ok": True, "lang": lang, "texts": [], "scores": [], "text": "",
            "average_confidence": 0.0, "minimum_confidence": 0.0,
        }

    confs = [s for _, s in kept]
    return {
        "ok": True,
        "lang": lang,
        "texts": [t for t, _ in kept],
        "scores": confs,
        "text": " ".join(t for t, _ in kept),
        "average_confidence": sum(confs) / len(confs),
        "minimum_confidence": min(confs),
    }


class _Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send(self, code: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802 — BaseHTTPRequestHandler's naming
        if self.path == "/health":
            self._send(200, {"ok": True, "langs": sorted(_engines)})
        else:
            self._send(404, {"ok": False, "error": "not found"})

    def do_POST(self):  # noqa: N802
        if self.path != "/ocr":
            self._send(404, {"ok": False, "error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", 0))
            req = json.loads(self.rfile.read(length) or b"{}")
            self._send(200, recognise(req["image_path"], req.get("lang", DEFAULT_LANG)))
        except Exception as exc:  # noqa: BLE001 — must answer, not hang the client
            self._send(200, {"ok": False, "error": f"{type(exc).__name__}: {exc}"})

    def log_message(self, *args):  # noqa: D102 — silence per-request stderr spam
        pass


def _exit_with_parent(parent_pid: int) -> None:
    """
    Shut down when the API process goes away.

    Nothing else reaps this worker: when the API is killed or crashes, the
    worker keeps its ~1.6 GB resident. That memory is exactly what the next
    start needs, so a single crash made every following start likelier to fail
    too. Polling is crude but portable, and the worker is long-lived so the
    cost is nil.
    """
    def watch():
        while True:
            time.sleep(5)
            if not _pid_alive(parent_pid):
                os._exit(0)

    threading.Thread(target=watch, daemon=True).start()


def _pid_alive(pid: int) -> bool:
    if sys.platform == "win32":
        import ctypes

        SYNCHRONIZE = 0x00100000
        handle = ctypes.windll.kernel32.OpenProcess(SYNCHRONIZE, False, pid)
        if not handle:
            return False
        # WAIT_TIMEOUT (258) means still running; WAIT_OBJECT_0 means exited.
        alive = ctypes.windll.kernel32.WaitForSingleObject(handle, 0) == 258
        ctypes.windll.kernel32.CloseHandle(handle)
        return alive
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def main() -> None:
    if "--selftest" in sys.argv:
        i = sys.argv.index("--selftest")
        path = sys.argv[i + 1]
        lang = sys.argv[i + 2] if len(sys.argv) > i + 2 else DEFAULT_LANG
        print(json.dumps(recognise(path, lang), ensure_ascii=False)[:800])
        return

    port = int(sys.argv[sys.argv.index("--port") + 1]) if "--port" in sys.argv else 8092

    parent = os.getenv("EZHIL_PARENT_PID")
    if parent:
        _exit_with_parent(int(parent))

    # Load before binding, so a successful /health means genuinely ready and
    # the client never sends a request that would sit behind a model load.
    _load()

    server = ThreadingHTTPServer(("127.0.0.1", port), _Handler)
    server.daemon_threads = True
    print(f"ocr worker listening on 127.0.0.1:{port}", file=sys.stderr, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
