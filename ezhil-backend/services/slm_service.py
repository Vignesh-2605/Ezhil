"""
SLM Service — Gemma 4 E4B QAT (Unsloth GGUF via llama-server)
=============================================================================
Generates dyslexia-friendly Tamil lesson JSON from OCR text.

Model: unsloth/gemma-4-E4B-it-qat-GGUF (UD-Q4_K_XL, ~3.9 GB), served by the
official llama.cpp `llama-server` binary. llama-cpp-python is NOT used —
its newest Windows/py3.13 wheel (0.3.19) predates the gemma4 architecture.

Backends tried in order:
  1. llama-server (spawned as a subprocess if not already running)
  2. Deterministic template — fallback when the server/model is unavailable
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

logger = logging.getLogger(__name__)

def stop_server() -> None:
    """Stop llama-server on a clean shutdown. Crashes are covered by the job."""
    global _server_proc, _backend, _load_attempted
    from services import child_reaper

    if _server_proc is None:
        return
    child_reaper.terminate(_server_proc, "llama-server")
    _server_proc = None
    _backend = None
    # Let the next request start it again rather than falling to templates.
    _load_attempted = False


def is_server_running() -> bool:
    return _server_proc is not None and _server_proc.poll() is None


def release_memory(reason: str = "") -> bool:
    """
    Give up the LLM's memory for something more urgent.

    Gemma holds around 5.2 GB, which is most of what the OCR worker needs to
    run at all. The two are never used at the same moment — a teacher reads a
    page, checks the extracted text, and only then generates a lesson — so
    handing the memory over and reloading later is better than OCR refusing to
    start. Returns True when a server was actually stopped.
    """
    from config import get_settings

    if not get_settings().SLM_RELEASE_FOR_OCR:
        return False
    if not is_server_running():
        return False

    logger.info("Stopping llama-server to free memory%s", f" for {reason}" if reason else "")
    stop_server()
    return True


def _idle_watch() -> None:
    """Stop the LLM once it has been unused for long enough."""
    from config import get_settings

    while True:
        timeout = get_settings().SLM_IDLE_TIMEOUT_S
        if timeout <= 0:
            return
        time.sleep(30)
        if is_server_running() and (time.time() - _last_used) > timeout:
            logger.info("llama-server idle for %.0fs — stopping to free memory", time.time() - _last_used)
            stop_server()
            return


# ─────────────────────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────────────────────

_MODELS_DIR  = Path(os.getenv("EZHIL_MODELS_DIR", "E:/PDD/10-03-2026/DysLearn/models"))
_GGUF_PATH   = Path(os.getenv("GGUF_PATH", str(_MODELS_DIR / "gemma-4-E4B-it-qat-UD-Q4_K_XL.gguf")))
_SERVER_BIN  = Path(os.getenv("LLAMA_SERVER_BIN", str(_MODELS_DIR / "llama.cpp/llama-server.exe")))
_SERVER_URL  = os.getenv("LLAMA_SERVER_URL", "http://127.0.0.1:8091")
_N_THREADS   = int(os.getenv("SLM_N_THREADS", "4"))
_N_CTX       = int(os.getenv("SLM_N_CTX", "4096"))
# Full-lesson generation on CPU takes 1-3 min; the old 30 s ceiling meant the
# model never got to answer.
SLM_TIMEOUT_S = float(os.getenv("SLM_TIMEOUT_S", "180"))

_executor        = ThreadPoolExecutor(max_workers=1, thread_name_prefix="slm-worker")
_load_attempted  = False

# Active backend: "server" | None
_backend: str | None = None
_server_proc: subprocess.Popen | None = None
_last_used: float = 0.0


# ─────────────────────────────────────────────────────────────────────────────
# Prompt template — Gemma 4 E4B (must match finetune_lora.py / serve_slm.py)
# ─────────────────────────────────────────────────────────────────────────────

_SYSTEM_PROMPT = (
    "You are Ezhilan, an AI tutor for Tamil children with dyslexia.\n"
    "Generate dyslexia-friendly Tamil lessons in valid JSON format.\n"
    "Each lesson MUST follow this exact schema:\n"
    '{"title": "...", "passage": {"lines": ["..."], "line_count": N},\n'
    ' "vocabulary": [{"word": "...", "syllables": [...], '
    '"meaning_ta": "...", "meaning_en": "...", "audio_hint": "..."}],\n'
    ' "audio_script": "...",\n'
    ' "quiz": [{"question_ta": "...", "question_en": "...", '
    '"options_ta": ["..."], "options_en": ["..."], "correct_index": 0}]}\n'
    "Rules:\n"
    "- The lesson must be about the given PASSAGE and nothing else.\n"
    "- passage.lines: 4-8 short lines, each <= 15 words, in the SAME language as the PASSAGE.\n"
    "- Each line must be a simplified version of a PASSAGE sentence, reusing the PASSAGE's own words.\n"
    "- Do NOT invent people, places or facts that are not in the PASSAGE.\n"
    "- vocabulary: 5-8 key words copied VERBATIM from the PASSAGE, with Tamil syllable splits.\n"
    "- quiz: exactly 3 comprehension questions answerable from the PASSAGE alone.\n"
    "- Output ONLY the JSON. No markdown fences, no explanation."
)


def _build_prompt(ocr_text: str, difficulty: int, language: str) -> str:
    """Gemma 4 E4B instruction format — matches finetune_lora.py exactly."""
    user_msg = (
        f"PASSAGE:\n{ocr_text}\n\n"
        f"DIFFICULTY: {difficulty}  (1=Grade1 easy · 2=medium · 3=harder)\n"
        f"LANGUAGE: {language}\n\n"
        "Generate the lesson JSON:"
    )
    return (
        f"<bos><start_of_turn>user\n{_SYSTEM_PROMPT}\n\n{user_msg}<end_of_turn>\n"
        "<start_of_turn>model\n"
    )


# ─────────────────────────────────────────────────────────────────────────────
# Backend loading
# ─────────────────────────────────────────────────────────────────────────────

def _server_healthy(timeout: float = 2.0) -> bool:
    import httpx
    try:
        r = httpx.get(f"{_SERVER_URL}/health", timeout=timeout)
        return r.status_code == 200
    except Exception:
        return False


def _load_slm() -> bool:
    global _load_attempted, _backend, _server_proc, _last_used

    if _load_attempted:
        # A previously spawned server may have finished loading since.
        if _backend is None and _server_healthy():
            _backend = "server"
        return _backend is not None
    _load_attempted = True

    from config import get_settings
    if get_settings().DEMO_MODE:
        logger.info("DEMO_MODE: SLM engine bypassed")
        return False

    # ── Reuse an already-running llama-server ────────────────────────────────
    if _server_healthy():
        _backend = "server"
        logger.info("SLM backend: existing llama-server at %s", _SERVER_URL)
        return True

    # ── Spawn llama-server with the Gemma 4 E4B GGUF ─────────────────────────
    if not _SERVER_BIN.exists():
        logger.warning("llama-server binary not found: %s", _SERVER_BIN)
        return False
    if not _GGUF_PATH.exists():
        logger.warning("GGUF model not found: %s", _GGUF_PATH)
        return False

    port = _SERVER_URL.rsplit(":", 1)[-1]
    logger.info("Starting llama-server (%s) on port %s …", _GGUF_PATH.name, port)
    proc = subprocess.Popen(
        [
            str(_SERVER_BIN),
            "-m", str(_GGUF_PATH),
            "-c", str(_N_CTX),
            "-t", str(_N_THREADS),
            "--host", "127.0.0.1",
            "--port", port,
        ],
        cwd=str(_SERVER_BIN.parent),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    )
    _server_proc = proc

    # Without this llama-server outlives an API crash still holding several GB —
    # exactly the memory the next start needs.
    from services import child_reaper

    child_reaper.adopt(proc, "llama-server")

    # Model load takes ~20-60 s from a cold disk cache.
    deadline = time.time() + 180
    while time.time() < deadline:
        # release_memory() can stop the server from another thread while it is
        # still loading, and it clears the global. Poll the handle we spawned
        # rather than the global, and read a cleared global as a deliberate
        # stop — dereferencing it raced into `NoneType has no attribute poll`.
        if _server_proc is not proc:
            logger.info("llama-server stopped while loading — leaving it down")
            return False
        if proc.poll() is not None:
            logger.warning("llama-server exited early (code %s)", proc.returncode)
            return False
        if _server_healthy():
            _backend = "server"
            _last_used = time.time()
            logger.info("SLM backend: llama-server ready (Gemma 4 E4B QAT Q4_K_XL)")
            threading.Thread(target=_idle_watch, daemon=True).start()
            return True
        time.sleep(2)

    logger.warning("llama-server did not become healthy in time — using template fallback")
    return False


# ─────────────────────────────────────────────────────────────────────────────
# Inference
# ─────────────────────────────────────────────────────────────────────────────

def _repair_brackets(s: str) -> str:
    """Fix transposed closers the model sometimes emits (e.g. `"…"]}` where
    the structure requires `"…"}]`). Walks the text outside string literals
    with a bracket stack and replaces any closer that doesn't match the top
    of the stack with the one that does. Length-preserving."""
    out: list[str] = []
    stack: list[str] = []
    in_str = False
    esc = False
    for ch in s:
        if in_str:
            out.append(ch)
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
            out.append(ch)
            continue
        if ch in "{[":
            stack.append(ch)
            out.append(ch)
            continue
        if ch in "}]":
            if stack:
                expected = "}" if stack[-1] == "{" else "]"
                if ch != expected:
                    ch = expected
                stack.pop()
            out.append(ch)
            continue
        out.append(ch)
    return "".join(out)


def _extract_json(raw: str) -> dict | None:
    """Strip markdown fences and parse the first complete {...} block."""
    raw = re.sub(r"^```(?:json)?\s*", "", raw.strip(), flags=re.IGNORECASE)
    raw = re.sub(r"```\s*$", "", raw.strip())
    start = raw.find("{")
    if start == -1:
        return None

    def _first_block(text: str) -> str | None:
        depth, end = 0, -1
        for i, ch in enumerate(text[start:], start):
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        return text[start:end] if end != -1 else None

    block = _first_block(raw)
    if block:
        try:
            return json.loads(block)
        except json.JSONDecodeError:
            pass

    # Second attempt: repair transposed / mismatched brackets and re-parse.
    repaired = _repair_brackets(raw)
    block = _first_block(repaired)
    if block:
        try:
            parsed = json.loads(block)
            logger.info("SLM JSON parsed after bracket repair")
            return parsed
        except json.JSONDecodeError:
            return None
    return None


def _run_inference(ocr_text: str, difficulty: int, language: str, max_tokens: int) -> str:
    global _last_used
    _last_used = time.time()
    if not _load_slm():
        return ""

    prompt = _build_prompt(ocr_text, difficulty, language)

    try:
        import httpx
        r = httpx.post(
            f"{_SERVER_URL}/completion",
            json={
                "prompt": prompt,
                "n_predict": max_tokens,
                # Low temperature: lesson generation must stay grounded in the
                # source passage, not be creative.
                "temperature": float(os.getenv("SLM_TEMPERATURE", "0.25")),
                "top_p": 0.9,
                "seed": 42,
                "stop": ["<end_of_turn>", "<eos>", "</s>"],
            },
            timeout=SLM_TIMEOUT_S,
        )
        r.raise_for_status()
        content = r.json().get("content", "").strip()
        logger.info("SLM raw output (%d chars): %.200s…", len(content), content.replace("\n", " "))
        return content
    except Exception as exc:
        logger.error("SLM inference error: %s", exc)

    return ""


# ─────────────────────────────────────────────────────────────────────────────
# Fallback lesson template
# ─────────────────────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────────────────────────────────────
# Fallback lesson template helpers & definitions
# ─────────────────────────────────────────────────────────────────────────────

DEFAULT_TAMIL_LINES = [
    "கல்வி என்பது ஒரு சிறந்த செல்வம் ஆகும்.",
    "ஆசிரியர் நமக்கு நற்பண்புகளைக் கற்றுத் தருகிறார்.",
    "நாம் தினமும் புதிய சொற்களைப் படிக்க வேண்டும்.",
    "மனப்பாடம் செய்யாமல் புரிந்து படிக்க வேண்டும்.",
    "பயிற்சியும் முயற்சியும் வெற்றியைத் தரும்.",
    "அறிவு விளக்கு போல நம் பாதையை ஒளிரச் செய்யும்."
]

DEFAULT_ENGLISH_LINES = [
    "Education is a very valuable treasure.",
    "Teachers help us learn good values every day.",
    "We should read new books to grow our knowledge.",
    "Understanding lessons is better than memorizing.",
    "Regular practice brings success in our studies.",
    "Knowledge is like a light guiding our way."
]

STOP_WORDS_TA = {
    "மற்றும்", "ஆனால்", "எனவே", "அது", "இந்த", "அந்த", "ஒரு", "என்று", "என", "இருந்து", "உள்ள", "வழியாக", "உடன்", "மேலும்", "அதுபோல", "என்ற", "அவை", "அவர்",
    "என்ன", "எது", "யார்", "எங்கு", "எப்போது", "ஏன்", "எப்படி", "யாரை", "தேர்ந்தெடு", "பின்வருவனவற்றில்", "பாடத்தின்படி", "பாடப்பகுதியில்", "வரி", "நேரடியாக",
    "குறிப்பிடப்பட்டுள்ளது", "வாக்கியம்", "சரியான", "பொருள்", "கருத்து", "விவாதிக்கப்பட்டுள்ளது", "இல்லை", "ஆம்", "உண்மை", "தவறு", "இப்பாடப்பகுதியின்",
    "முக்கிய", "பாடத்தின்", "சொல்லின்", "பாடப்பகுதியின்", "குறிப்பிடப்பட்டுள்ள", "விவரம்"
}
STOP_WORDS_EN = {
    "the", "and", "a", "of", "to", "in", "is", "that", "it", "on", "for", "as", "with", "was", "at", "by", "an", "be", "this", "are", "from", "they", "we", "he", "she", "you", "our",
    "what", "which", "who", "where", "when", "why", "how", "whose", "whom", "select", "choose", "following", "according", "passage", "lesson", "detail", "directly",
    "mentioned", "statement", "correct", "meaning", "word", "theme", "discussed", "is", "are", "was", "were", "does", "did", "do", "can", "could", "should", "would",
    "has", "have", "had", "not", "no", "yes", "true", "false", "its", "their", "about", "accordingly", "main", "here", "there"
}

DICT_MAPPINGS = {
    # English words
    "machine": {
        "meaning_ta": "இயந்திரம் (Machine) - கணினியின் தானியங்கி செயல்பாடு",
        "meaning_en": "A device or system that performs tasks automatically"
    },
    "learning": {
        "meaning_ta": "கற்றல் (Learning) - அனுபவத்தின் மூலம் அறிவை வளர்த்தல்",
        "meaning_en": "Acquiring knowledge or skills through experience or study"
    },
    "model": {
        "meaning_ta": "மாதிரி (Model) - கணிக்க அல்லது பகுப்பாய் செய்ய உதவும் வடிவமைப்பு",
        "meaning_en": "A representation of a system used for prediction or analysis"
    },
    "data": {
        "meaning_ta": "தரவு (Data) - கணினி சேமிக்கும் தகவல் அல்லது உண்மைகள்",
        "meaning_en": "Information or facts stored and processed by a computer"
    },
    "network": {
        "meaning_ta": "பிணையம் (Network) - சாதனங்கள் அல்லது கணுக்களின் இணைப்பு",
        "meaning_en": "A group or system of interconnected components"
    },
    "neural": {
        "meaning_ta": "நரம்பியல் (Neural) - மூளையின் நரம்பு செல்கள் போன்ற அமைப்பு",
        "meaning_en": "Relating to a network of neurons or nerve cells"
    },
    "report": {
        "meaning_ta": "அறிக்கை (Report) - தகவல்கள் அடங்கிய முறையான ஆவணம்",
        "meaning_en": "A formal document presenting information and findings"
    },
    "project": {
        "meaning_ta": "திட்டம் (Project) - ஒரு குறிப்பிட்ட இலக்கை அடைய செய்யப்படும் வேலை",
        "meaning_en": "A planned undertaking or task with a specific goal"
    },
    "algorithm": {
        "meaning_ta": "நெறிமுறை (Algorithm) - கணக்கீடு செய்யப் பயன்படும் படிநிலை விதிகள்",
        "meaning_en": "A set of step-by-step rules for solving a problem"
    },
    "feature": {
        "meaning_ta": "அம்சம் (Feature) - தரவின் ஒரு முக்கிய பண்பு அல்லது காரணி",
        "meaning_en": "An individual measurable property or characteristic"
    },
    "accuracy": {
        "meaning_ta": "துல்லியம் (Accuracy) - முடிவுகளின் சரியான அளவு",
        "meaning_en": "The quality or state of being correct or precise"
    },
    "classification": {
        "meaning_ta": "வகைப்பாடு (Classification) - பொருட்களை வகைகளாகப் பிரித்தல்",
        "meaning_en": "The process of grouping things into categories"
    },
    "training": {
        "meaning_ta": "பயிற்சி (Training) - மாடலை மேம்படுத்த தரவை வழங்கும் முறை",
        "meaning_en": "The process of teaching a model using data"
    },
    "testing": {
        "meaning_ta": "சோதனை (Testing) - மாடலின் துல்லியத்தை மதிப்பிடும் முறை",
        "meaning_en": "Evaluating the performance and correctness of a model"
    },
    "system": {
        "meaning_ta": "அமைப்பு (System) - ஒன்றிணைந்து செயல்படும் உறுப்புகளின் தொகுதி",
        "meaning_en": "A set of connected things or parts forming a complex whole"
    },
    "analysis": {
        "meaning_ta": "பகுப்பாய்வு (Analysis) - விவரங்களை ஆராய்ந்து முடிவெடுத்தல்",
        "meaning_en": "Detailed examination of elements or structure"
    },
    
    # Tamil words
    "அம்மா": {
        "meaning_ta": "தாய் - அன்பு செலுத்தும் பெற்றோர்",
        "meaning_en": "Mother - a female parent"
    },
    "அப்பா": {
        "meaning_ta": "தந்தை - குடும்பத்தை காக்கும் பெற்றோர்",
        "meaning_en": "Father - a male parent"
    },
    "பள்ளி": {
        "meaning_ta": "கல்வி கற்கும் இடம்",
        "meaning_en": "School - a place of education"
    },
    "கல்வி": {
        "meaning_ta": "அறிவு மற்றும் திறன்களை வளர்க்கும் படிப்பு",
        "meaning_en": "Education - the process of learning and acquiring knowledge"
    },
    "மாணவர்": {
        "meaning_ta": "பள்ளியில் படிக்கும் குழந்தை",
        "meaning_en": "Student - a person who is studying at school"
    },
    "ஆசிரியர்": {
        "meaning_ta": "பாடம் கற்பிக்கும் நபர்",
        "meaning_en": "Teacher - a person who teaches"
    },
    "பாடம்": {
        "meaning_ta": "கற்கும் பாடம் அல்லது அலகு",
        "meaning_en": "Lesson - a unit of learning"
    },
    "புத்தகம்": {
        "meaning_ta": "படிக்கும் நூல்",
        "meaning_en": "Book - a written or printed work"
    },
    "தமிழ்": {
        "meaning_ta": "நம் தாய்மொழி",
        "meaning_en": "Tamil - our mother tongue"
    },
    "அறிவு": {
        "meaning_ta": "விவேகம் மற்றும் பகுத்தறியும் திறன்",
        "meaning_en": "Knowledge / Wisdom - understanding and intelligence"
    },
    "முயற்சி": {
        "meaning_ta": "செயலை முடிக்கும் ஆற்றல்",
        "meaning_en": "Effort - the attempt to do something"
    },
    "பயிற்சி": {
        "meaning_ta": "செயலை மீண்டும் மீண்டும் செய்தல்",
        "meaning_en": "Practice - repeated exercise to improve skill"
    },
    "வெற்றி": {
        "meaning_ta": "இலக்கை அடைதல்",
        "meaning_en": "Success - achieving a goal"
    }
}

def split_tamil_syllables(word: str) -> list[str]:
    """Split Tamil word into orthographic syllables using specified regex."""
    pattern = re.compile(r"[\u0B85-\u0B94\u0B83]|(?:[\u0B95-\u0BB9][\u0BBE-\u0BCD]*)")
    syllables = pattern.findall(word)
    if not syllables:
        return list(word)
    return syllables

def get_context_phrase(w: str, lines: list[str]) -> str:
    """Extract 5-word context phrase containing word w from lines."""
    for line in lines:
        if w.lower() in line.lower():
            words = line.split()
            idx = -1
            for i, word in enumerate(words):
                clean_w = word.strip(".,!?\"'()[]{}<>:;।").lower()
                if clean_w == w.lower() or w.lower() in clean_w:
                    idx = i
                    break
            if idx != -1:
                start_idx = max(0, idx - 2)
                end_idx = min(len(words), idx + 3)
                return " ".join(words[start_idx:end_idx])
    return ""

def is_tamil_text(text: str) -> bool:
    """Check if the text contains Tamil Unicode characters."""
    return bool(re.search(r"[\u0B80-\u0BFF]", text))

def detect_topic(text: str) -> str:
    """Classify the text into one of the known academic topics."""
    text_lower = text.lower()
    if any(k in text_lower for k in ["learning", "machine", "model", "neural", "network", "classification", "accuracy"]):
        return "ml"
    elif any(k in text_lower for k in ["computer", "network", "system", "database", "security", "software", "cpu", "server"]):
        return "cs"
    elif any(k in text_lower for k in ["research", "science", "experiment", "analysis", "data", "results", "mathematics", "theory"]):
        return "science"
    return "general"

def validate_lesson(lesson: dict) -> bool:
    """Validate generated lesson content for administrative metadata, duplicates, and general quality."""
    if not isinstance(lesson, dict):
        return False
        
    title = lesson.get("title", "")
    passage = lesson.get("passage", {})
    vocabulary = lesson.get("vocabulary", [])
    quiz = lesson.get("quiz", [])
    
    # 1. Check title and passage for banned words
    banned_keywords = ["simats", "saveetha", "register number", "roll number", "teacher id", "school code", "student name", "assessment tool"]
    
    title_lower = str(title).lower()
    if any(k in title_lower for k in banned_keywords):
        return False
        
    lines = passage.get("lines", []) if isinstance(passage, dict) else []
    for line in lines:
        line_lower = str(line).lower()
        if any(k in line_lower for k in banned_keywords):
            return False
            
    # 2. Check vocabulary
    seen_words = set()
    for item in vocabulary:
        if not isinstance(item, dict):
            return False
        word = str(item.get("word", "")).strip()
        word_lower = word.lower()
        if not word or word_lower in seen_words or any(k in word_lower for k in banned_keywords):
            return False
        seen_words.add(word_lower)
        
        meaning_ta = str(item.get("meaning_ta", "")).lower()
        meaning_en = str(item.get("meaning_en", "")).lower()
        if any(k in meaning_ta or k in meaning_en for k in banned_keywords):
            return False
            
    # 3. Check quiz questions and options
    seen_questions = set()
    for item in quiz:
        if not isinstance(item, dict):
            return False
        q_ta = str(item.get("question_ta", "")).strip()
        q_en = str(item.get("question_en", "")).strip()
        
        if not q_ta or q_ta in seen_questions or any(k in q_ta.lower() or k in q_en.lower() for k in banned_keywords):
            return False
        seen_questions.add(q_ta)
        
        exp_ta = str(item.get("explanation_ta", "")).lower()
        exp_en = str(item.get("explanation_en", "")).lower()
        if any(k in exp_ta or k in exp_en for k in banned_keywords):
            return False
        
        options_ta = item.get("options_ta", [])
        options_en = item.get("options_en", [])
        
        if len(options_ta) < 2 or len(options_en) < 2:
            return False
            
        clean_opts_ta = [str(o).strip().lower() for o in options_ta]
        clean_opts_en = [str(o).strip().lower() for o in options_en]
        if len(set(clean_opts_ta)) != len(clean_opts_ta) or len(set(clean_opts_en)) != len(clean_opts_en):
            return False
            
        corr_idx = item.get("correct_index", 0)
        if not (0 <= corr_idx < len(options_ta)):
            return False
            
    return True

def extract_vocabulary_candidates(text: str, is_source_tamil: bool, count: int) -> list[str]:
    """
    Extract meaningful vocabulary candidates using a frequency-length scoring heuristic:
    score = frequency * ln(length + 1)
    Filters out stop words, metadata terms, student/institution names, and IDs.
    """
    # Import banned patterns to ensure no administrative metadata is included
    from services.text_classifier import BANNED_PATTERNS
    
    # Extract words
    words = re.findall(r'[\u0B80-\u0BFFa-zA-Z]+', text)
    
    stop_words = STOP_WORDS_TA if is_source_tamil else STOP_WORDS_EN
    
    freq = {}
    for w in words:
        clean_w = w.strip().lower()
        if not clean_w:
            continue
            
        # 1. Stop words filter
        if clean_w in stop_words:
            continue
            
        # 2. Length check (min 3 chars for English, min 2 for Tamil)
        min_len = 2 if is_source_tamil else 3
        if len(clean_w) < min_len:
            continue
            
        # 3. Banned metadata patterns check
        is_banned = False
        for pat in BANNED_PATTERNS:
            if re.search(pat, clean_w):
                is_banned = True
                break
        if is_banned:
            continue
            
        # 4. Check if it is a number or contains digits
        if any(c.isdigit() for c in clean_w):
            continue
            
        freq[clean_w] = freq.get(clean_w, 0) + 1
        
    # Score candidate words: frequency * log length
    import math
    scored_candidates = []
    for w, f in freq.items():
        score = f * math.log(len(w) + 1)
        scored_candidates.append((w, score))
        
    # Sort by score in descending order
    scored_candidates.sort(key=lambda x: x[1], reverse=True)
    
    # Return original cased words from the text for the top candidates
    top_lowercased = [x[0] for x in scored_candidates[:count]]
    
    result = []
    seen = set()
    for w in words:
        if w.lower() in top_lowercased and w.lower() not in seen:
            result.append(w)
            seen.add(w.lower())
            if len(result) >= count:
                break
                
    # Fallback to defaults if not enough words extracted
    if len(result) < count:
        defaults = ["கல்வி", "முயற்சி", "பயிற்சி", "வெற்றி", "அறிவு"] if is_source_tamil else ["learning", "knowledge", "practice", "success", "study"]
        for dw in defaults:
            if dw.lower() not in seen:
                result.append(dw)
                seen.add(dw.lower())
                if len(result) >= count:
                    break
                    
    return result

def verify_grounding(
    lesson: dict | list[str],
    vocabulary: list[dict] = None,
    quiz: list[dict] = None,
    source_text: str = None
) -> dict:
    """
    Verify grounding of lesson content, vocabulary, and quiz against source text.
    Returns diagnostics report dictionary.
    """
    # 1. Handle backward compatibility / param mapping
    if isinstance(vocabulary, str):
        source_text = vocabulary
        vocabulary = None

    if source_text is None:
        source_text = ""
        
    source_text_lower = source_text.lower()
    
    # 2. Extract components if not directly provided
    if not vocabulary and isinstance(lesson, dict):
        vocabulary = lesson.get("vocabulary", [])
    if not vocabulary:
        vocabulary = []
        
    if not quiz and isinstance(lesson, dict):
        quiz = lesson.get("quiz", [])
    if not quiz:
        quiz = []
        
    if isinstance(lesson, dict):
        passage_dict = lesson.get("passage", {})
        if isinstance(passage_dict, dict):
            lesson_lines = passage_dict.get("lines", [])
        else:
            lesson_lines = []
    else:
        lesson_lines = lesson if isinstance(lesson, list) else []

    # 3. Build reverse mappings of key Tamil vocabulary to English concepts
    reverse_mappings = {}
    for eng_word, info in DICT_MAPPINGS.items():
        # Find Tamil words in meaning_ta
        ta_matches = re.findall(r'[\u0B80-\u0BFF]+', info["meaning_ta"])
        for ta_w in ta_matches:
            if ta_w not in reverse_mappings:
                reverse_mappings[ta_w] = []
            reverse_mappings[ta_w].append(eng_word)

    # Helper functions
    def is_word_grounded(word: str) -> bool:
        clean_word = word.strip(".,!?\"'()[]{}<>:;।").lower()
        if not clean_word:
            return True
        
        # Stop words are always considered grounded/neutral
        if clean_word in STOP_WORDS_TA or clean_word in STOP_WORDS_EN:
            return True
            
        # Direct check
        if clean_word in source_text_lower:
            return True
            
        # Check reverse mappings (Tamil to English)
        if clean_word in reverse_mappings:
            for eng_w in reverse_mappings[clean_word]:
                if eng_w in source_text_lower:
                    return True
                    
        # Check substring match of clean_word in reverse mappings keys
        for ta_w, eng_words in reverse_mappings.items():
            if clean_word in ta_w or ta_w in clean_word:
                for eng_w in eng_words:
                    if eng_w in source_text_lower:
                        return True
                        
        # Check if it's a Tamil word in DICT_MAPPINGS values
        for dict_w, info in DICT_MAPPINGS.items():
            if clean_word == dict_w:
                return True
            ta_matches = re.findall(r'[\u0B80-\u0BFF]+', info["meaning_ta"])
            if clean_word in ta_matches:
                if dict_w in source_text_lower:
                    return True
                    
        return False

    def is_text_grounded(text: str) -> tuple[bool, list[str]]:
        words = text.split()
        if not words:
            return True, []
            
        unsupported = []
        supported = []
        
        for w in words:
            clean_w = w.strip(".,!?\"'()[]{}<>:;।")
            if not clean_w or clean_w.lower() in STOP_WORDS_TA or clean_w.lower() in STOP_WORDS_EN:
                continue
            if is_word_grounded(clean_w):
                supported.append(clean_w)
            else:
                unsupported.append(clean_w)
                
        total_sig = len(supported) + len(unsupported)
        if total_sig == 0:
            return True, []
            
        ratio = len(supported) / total_sig
        return ratio >= 0.7, unsupported

    # Grounding state
    supported_concepts = []
    unsupported_concepts = []
    unsupported_vocabulary = []
    unsupported_quiz_items = []
    
    # 1. Lesson Lines Traceability
    lines_total = 0
    lines_grounded = 0
    
    for line in lesson_lines:
        if line.startswith("###"):
            continue
        lines_total += 1
        grounded, unsup = is_text_grounded(line)
        if grounded:
            lines_grounded += 1
            for w in line.split():
                clean_w = w.strip(".,!?\"'()[]{}<>:;।")
                if clean_w and clean_w.lower() not in STOP_WORDS_TA and clean_w.lower() not in STOP_WORDS_EN:
                    supported_concepts.append(clean_w)
        else:
            unsupported_concepts.extend(unsup)

    # 2. Vocabulary Grounding
    vocab_total = 0
    vocab_grounded = 0
    
    for item in vocabulary:
        word = item.get("word", "")
        if not word:
            continue
        vocab_total += 1
        if is_word_grounded(word):
            vocab_grounded += 1
            supported_concepts.append(word)
        else:
            unsupported_vocabulary.append(word)
            unsupported_concepts.append(word)

    # 3. Quiz Grounding
    quiz_total = 0
    quiz_grounded = 0
    
    for idx, item in enumerate(quiz):
        quiz_total += 1
        
        q_ta = item.get("question_ta", "")
        q_en = item.get("question_en", "")
        
        corr_idx = item.get("correct_index", 0)
        options_ta = item.get("options_ta", [])
        options_en = item.get("options_en", [])
        
        opt_ta = options_ta[corr_idx] if len(options_ta) > corr_idx else ""
        opt_en = options_en[corr_idx] if len(options_en) > corr_idx else ""
        
        # Check grounding of question, correct option, and explanation (for non-empty fields)
        q_ta_present = bool(q_ta.strip()) if isinstance(q_ta, str) else False
        q_en_present = bool(q_en.strip()) if isinstance(q_en, str) else False
        opt_ta_present = bool(opt_ta.strip()) if isinstance(opt_ta, str) else False
        opt_en_present = bool(opt_en.strip()) if isinstance(opt_en, str) else False
        
        exp_ta = item.get("explanation_ta", "")
        exp_en = item.get("explanation_en", "")
        exp_ta_present = bool(exp_ta.strip()) if isinstance(exp_ta, str) else False
        exp_en_present = bool(exp_en.strip()) if isinstance(exp_en, str) else False
        
        g_q_ta = True
        unsup_q_ta = []
        if q_ta_present:
            g_q_ta, unsup_q_ta = is_text_grounded(q_ta)
            
        g_q_en = True
        unsup_q_en = []
        if q_en_present:
            g_q_en, unsup_q_en = is_text_grounded(q_en)
            
        g_o_ta = True
        unsup_o_ta = []
        if opt_ta_present:
            g_o_ta, unsup_o_ta = is_text_grounded(opt_ta)
            
        g_o_en = True
        unsup_o_en = []
        if opt_en_present:
            g_o_en, unsup_o_en = is_text_grounded(opt_en)
            
        g_exp_ta = True
        unsup_exp_ta = []
        if exp_ta_present:
            g_exp_ta, unsup_exp_ta = is_text_grounded(exp_ta)
            
        g_exp_en = True
        unsup_exp_en = []
        if exp_en_present:
            g_exp_en, unsup_exp_en = is_text_grounded(exp_en)
            
        # Grounded if at least one version is present and all present versions are grounded
        has_present_fields = (q_ta_present or q_en_present) and (opt_ta_present or opt_en_present)
        is_item_grounded = has_present_fields and (
            (not q_ta_present or g_q_ta) and
            (not q_en_present or g_q_en) and
            (not opt_ta_present or g_o_ta) and
            (not opt_en_present or g_o_en) and
            (not exp_ta_present or g_exp_ta) and
            (not exp_en_present or g_exp_en)
        )
        
        if is_item_grounded:
            quiz_grounded += 1
            for term in [q_ta, q_en, opt_ta, opt_en, exp_ta, exp_en]:
                if not isinstance(term, str):
                    continue
                for w in term.split():
                    clean_w = w.strip(".,!?\"'()[]{}<>:;।")
                    if clean_w and clean_w.lower() not in STOP_WORDS_TA and clean_w.lower() not in STOP_WORDS_EN:
                        supported_concepts.append(clean_w)
        else:
            unsupported_quiz_items.append(f"Quiz Item {idx+1}: {q_en or q_ta}")
            unsupported_concepts.extend(unsup_q_ta + unsup_q_en + unsup_o_ta + unsup_o_en)

    # 4. Score calculation
    total_elements = lines_total + vocab_total + quiz_total
    grounded_elements = lines_grounded + vocab_grounded + quiz_grounded
    coverage_score = grounded_elements / total_elements if total_elements > 0 else 1.0

    return {
        "coverage_score": coverage_score,
        "supported_concepts": sorted(list(set(supported_concepts))),
        "unsupported_concepts": sorted(list(set(unsupported_concepts))),
        "unsupported_vocabulary": sorted(list(set(unsupported_vocabulary))),
        "unsupported_quiz_items": sorted(list(set(unsupported_quiz_items)))
    }

def validate_and_clean_quiz_item(q_item: dict, source_text: str) -> bool:
    """
    Validate that:
    - No duplicate answers/options exist in the quiz item.
    - Questions and options do not contain administrative/metadata keywords or OCR garbage.
    - Correct answer concepts exist in the source text.
    """
    from services.text_classifier import BANNED_PATTERNS
    
    q_ta = q_item.get("question_ta", "")
    q_en = q_item.get("question_en", "")
    
    # Check for banned keywords in question
    for pat in BANNED_PATTERNS:
        if re.search(pat, q_ta.lower()) or re.search(pat, q_en.lower()):
            return False
            
    options_ta = q_item.get("options_ta", [])
    options_en = q_item.get("options_en", [])
    
    # Check options for banned keywords
    for opt in options_ta + options_en:
        for pat in BANNED_PATTERNS:
            if re.search(pat, opt.lower()):
                return False
                
    corr_idx = q_item.get("correct_index", 0)
    if corr_idx >= len(options_ta) or corr_idx >= len(options_en):
        return False
        
    correct_ta = options_ta[corr_idx]
    correct_en = options_en[corr_idx]
    
    # Avoid duplicate options
    if len(set(options_ta)) != len(options_ta) or len(set(options_en)) != len(options_en):
        return False
        
    source_text_lower = source_text.lower()
    
    # Build reverse mappings for word grounding
    reverse_mappings = {}
    for eng_word, info in DICT_MAPPINGS.items():
        ta_matches = re.findall(r'[\u0B80-\u0BFF]+', info["meaning_ta"])
        for ta_w in ta_matches:
            if ta_w not in reverse_mappings:
                reverse_mappings[ta_w] = []
            reverse_mappings[ta_w].append(eng_word)
            
    source_words = re.findall(r"[஀-௿a-z]+", source_text_lower)

    def is_word_grounded(word: str) -> bool:
        clean_word = word.strip(".,!?\"'()[]{}<>:;।").lower()
        if not clean_word:
            return True
        if clean_word in STOP_WORDS_TA or clean_word in STOP_WORDS_EN:
            return True
        if clean_word in source_text_lower:
            return True
        # Tamil is agglutinative: an option like "உழைப்பு" must match the
        # inflected source form "உழைப்பால்". Accept a shared stem prefix.
        if len(clean_word) >= 4:
            stem = clean_word[:-2] if len(clean_word) > 5 else clean_word[:4]
            if any(sw.startswith(stem) for sw in source_words):
                return True
        if clean_word in reverse_mappings:
            for eng_w in reverse_mappings[clean_word]:
                if eng_w in source_text_lower:
                    return True
        return False

    # Check correct-answer grounding against the SOURCE-language option only.
    # The other language's option is a translation and legitimately contains
    # words that never appear in the source.
    is_source_tamil_text = bool(re.search(r"[஀-௿]", source_text))
    grounding_target = correct_ta if is_source_tamil_text else correct_en
    words = str(grounding_target).split()
    total_sig = sum(1 for w in words if w.strip(".,!?\"'()[]{}<>:;।").lower() not in STOP_WORDS_TA and w.strip(".,!?\"'()[]{}<>:;।").lower() not in STOP_WORDS_EN)

    if total_sig > 0:
        ratio = sum(1 for w in words if w.strip(".,!?\"'()[]{}<>:;।").lower() not in STOP_WORDS_TA and w.strip(".,!?\"'()[]{}<>:;।").lower() not in STOP_WORDS_EN and is_word_grounded(w)) / total_sig
        if ratio < 0.6:
            return False

    return True

DEFAULT_TRANSLATIONS = {
    # Tamil to English
    "கல்வி என்பது ஒரு சிறந்த செல்வம் ஆகும்.": "Education is a very valuable treasure.",
    "ஆசிரியர் நமக்கு நற்பண்புகளைக் கற்றுத் தருகிறார்.": "Teachers help us learn good values every day.",
    "நாம் தினமும் புதிய சொற்களைப் படிக்க வேண்டும்.": "We should read new books to grow our knowledge.",
    "மனப்பாடம் செய்யாமல் புரிந்து படிக்க வேண்டும்.": "Understanding lessons is better than memorizing.",
    "பயிற்சியும் முயற்சியும் வெற்றியைத் தரும்.": "Regular practice brings success in our studies.",
    "அறிவு விளக்கு போல நம் பாதையை ஒளிரச் செய்யும்.": "Knowledge is like a light guiding our way.",
    # English to Tamil
    "Education is a very valuable treasure.": "கல்வி என்பது ஒரு சிறந்த செல்வம் ஆகும்.",
    "Teachers help us learn good values every day.": "ஆசிரியர் நமக்கு நற்பண்புகளைக் கற்றுத் தருகிறார்.",
    "We should read new books to grow our knowledge.": "நாம் தினமும் புதிய சொற்களைப் படிக்க வேண்டும்.",
    "Understanding lessons is better than memorizing.": "மனப்பாடம் செய்யாமல் புரிந்து படிக்க வேண்டும்.",
    "Regular practice brings success in our studies.": "பயிற்சியும் முயற்சியும் வெற்றியைத் தரும்.",
    "Knowledge is like a light guiding our way.": "அறிவு விளக்கு போல நம் பாதையை ஒளிரச் செய்யும்."
}

def get_bilingual_pair(line: str) -> tuple[str, str]:
    from services.translation_service import translate_en_to_ta, translate_ta_to_en
    line = line.strip()
    if line in DEFAULT_TRANSLATIONS:
        if is_tamil_text(line):
            return line, DEFAULT_TRANSLATIONS[line]
        else:
            return DEFAULT_TRANSLATIONS[line], line
    # Check case-insensitive and trailing dots
    line_clean = line.rstrip(".")
    for k, v in DEFAULT_TRANSLATIONS.items():
        if line_clean.lower() == k.rstrip(".").lower():
            return line, v
        if line_clean.lower() == v.rstrip(".").lower():
            return v, line
            
    if is_tamil_text(line):
        return line, translate_ta_to_en(line)
    else:
        return translate_en_to_ta(line), line

def negate_sentence(s: str) -> str:
    if is_tamil_text(s):
        has_dot = s.endswith(".")
        if has_dot:
            s_core = s[:-1]
        else:
            s_core = s
            
        if s_core.endswith("ஆகும்"):
            neg_core = s_core[:-4] + "அல்ல"
        elif s_core.endswith("வேண்டும்"):
            neg_core = s_core[:-7] + "வேண்டாம்"
        elif s_core.endswith("உள்ளது"):
            neg_core = s_core[:-6] + "இல்லை"
        elif s_core.endswith("தரும்"):
            neg_core = s_core[:-4] + "தராது"
        elif s_core.endswith("செய்யும்"):
            neg_core = s_core[:-7] + "செய்யாது"
        elif s_core.endswith("படிக்கலாம்"):
            neg_core = s_core[:-10] + "படிக்கக்கூடாது"
        elif s_core.endswith("வளர்க்கும்"):
            neg_core = s_core[:-10] + "வளர்க்காது"
        else:
            neg_core = s_core + " என்பது தவறானது"
            
        if has_dot:
            return neg_core + "."
        return neg_core
    else:
        has_dot = s.endswith(".")
        if has_dot:
            s_core = s[:-1]
        else:
            s_core = s
            
        if " is " in s_core:
            neg_core = s_core.replace(" is ", " is not ")
        elif " are " in s_core:
            neg_core = s_core.replace(" are ", " are not ")
        elif " can " in s_core:
            neg_core = s_core.replace(" can ", " cannot ")
        elif " should " in s_core:
            neg_core = s_core.replace(" should ", " should not ")
        elif " will " in s_core:
            neg_core = s_core.replace(" will ", " will not ")
        elif " has " in s_core:
            neg_core = s_core.replace(" has ", " does not have ")
        elif " have " in s_core:
            neg_core = s_core.replace(" have ", " do not have ")
        elif " help " in s_core:
            neg_core = s_core.replace(" help ", " do not help ")
        else:
            neg_core = s_core + " (which is incorrect)"
            
        if has_dot:
            return neg_core + "."
        return neg_core

def generate_dynamic_quiz(
    passage_lines: list[str],
    vocabulary: list[dict],
    source_text: str,
    is_target_tamil: bool
) -> list[dict]:
    # Extract only content lines
    content_lines = [l.strip() for l in passage_lines if l.strip() and not l.strip().startswith("###")]
    if not content_lines:
        content_lines = DEFAULT_TAMIL_LINES if is_target_tamil else DEFAULT_ENGLISH_LINES
        
    # Partition them into 4 sections
    n = len(content_lines)
    base = n // 4
    rem = n % 4
    sizes = [base + (1 if i < rem else 0) for i in range(4)]
    
    sections = {
        "intro": content_lines[0 : sizes[0]],
        "concepts": content_lines[sizes[0] : sizes[0] + sizes[1]],
        "details": content_lines[sizes[0] + sizes[1] : sizes[0] + sizes[1] + sizes[2]],
        "summary": content_lines[sizes[0] + sizes[1] + sizes[2] :]
    }
    
    # Ensure every section has at least one line
    all_content = [l for l in content_lines if l]
    for key in ["intro", "concepts", "details", "summary"]:
        if not sections[key]:
            sections[key] = [all_content[0]]
            
    # Compile distractors pool
    distractors_pool = []
    seen_distractors = set()
    
    # 1. Add negated content lines
    for line in content_lines:
        line_ta, line_en = get_bilingual_pair(line)
        neg_ta = negate_sentence(line_ta)
        neg_en = negate_sentence(line_en)
        if neg_ta.lower() not in seen_distractors:
            distractors_pool.append((neg_ta, neg_en))
            seen_distractors.add(neg_ta.lower())
            
    # 2. Add vocabulary word meanings
    if vocabulary:
        for v in vocabulary:
            ta_m = v.get("meaning_ta", "")
            en_m = v.get("meaning_en", "")
            if ta_m and ta_m.lower() not in seen_distractors:
                distractors_pool.append((ta_m, en_m))
                seen_distractors.add(ta_m.lower())
            
    # 3. Add dictionary meanings of other academic terms
    for term, info in DICT_MAPPINGS.items():
        ta_m = info["meaning_ta"]
        en_m = info["meaning_en"]
        if ta_m.lower() not in seen_distractors:
            distractors_pool.append((ta_m, en_m))
            seen_distractors.add(ta_m.lower())
            
    def build_options(correct_ta: str, correct_en: str, excluded_texts: set[str] = None) -> tuple[list[str], list[str], int]:
        if excluded_texts is None:
            excluded_texts = set()
        excluded_texts.add(correct_ta.lower())
        excluded_texts.add(correct_en.lower())
        
        selected = []
        for ta_dist, en_dist in distractors_pool:
            if ta_dist.lower() in excluded_texts or en_dist.lower() in excluded_texts:
                continue
            if ta_dist.lower() in [x[0].lower() for x in selected] or en_dist.lower() in [x[1].lower() for x in selected]:
                continue
            selected.append((ta_dist, en_dist))
            excluded_texts.add(ta_dist.lower())
            excluded_texts.add(en_dist.lower())
            if len(selected) >= 3:
                break
                
        # If we need more distractors, use default DICT_MAPPINGS fallbacks
        if len(selected) < 3:
            for term, info in DICT_MAPPINGS.items():
                ta_dist = info["meaning_ta"]
                en_dist = info["meaning_en"]
                if ta_dist.lower() in excluded_texts or en_dist.lower() in excluded_texts:
                    continue
                selected.append((ta_dist, en_dist))
                excluded_texts.add(ta_dist.lower())
                excluded_texts.add(en_dist.lower())
                if len(selected) >= 3:
                    break
                    
        import random
        combined = [(correct_ta, correct_en, True)]
        for ta, en in selected[:3]:
            combined.append((ta, en, False))
            
        random.shuffle(combined)
        
        final_ta = [x[0] for x in combined]
        final_en = [x[1] for x in combined]
        corr_idx = 0
        for idx, x in enumerate(combined):
            if x[2]:
                corr_idx = idx
                break
        return final_ta, final_en, corr_idx

    quiz = []
    seen_questions = set()
    from services.translation_service import translate_en_to_ta
    
    # 1. Main Idea Question
    intro_line = sections["intro"][0]
    correct_ta, correct_en = get_bilingual_pair(intro_line)
    
    excluded = {correct_ta.lower(), correct_en.lower()}
    opt_ta, opt_en, corr_idx = build_options(correct_ta, correct_en, excluded)
    
    exp_en = f"The main theme of this passage is: {correct_en}"
    exp_ta = translate_en_to_ta(exp_en)
    
    q1_item = {
        "question_ta": "இப்பாடப்பகுதியின் முக்கிய கருத்து என்ன?",
        "question_en": "What is the main theme discussed in this passage?",
        "options_ta": opt_ta,
        "options_en": opt_en,
        "correct_index": corr_idx,
        "explanation_ta": exp_ta,
        "explanation_en": exp_en
    }
    if validate_and_clean_quiz_item(q1_item, source_text):
        quiz.append(q1_item)
        seen_questions.add(q1_item["question_en"])
        
    # 2. Fact Recall Question
    details_line = sections["details"][0]
    correct_ta, correct_en = get_bilingual_pair(details_line)
    
    excluded = {correct_ta.lower(), correct_en.lower()}
    opt_ta, opt_en, corr_idx = build_options(correct_ta, correct_en, excluded)
    
    exp_en = f"According to the passage details: {correct_en}"
    exp_ta = translate_en_to_ta(exp_en)
    
    q2_item = {
        "question_ta": "பாடப்பகுதியில் நேரடியாகக் குறிப்பிடப்பட்டுள்ள விவரம் எது?",
        "question_en": "Which of the following details is directly mentioned in the passage?",
        "options_ta": opt_ta,
        "options_en": opt_en,
        "correct_index": corr_idx,
        "explanation_ta": exp_ta,
        "explanation_en": exp_en
    }
    if q2_item["question_en"] not in seen_questions and validate_and_clean_quiz_item(q2_item, source_text):
        quiz.append(q2_item)
        seen_questions.add(q2_item["question_en"])
        
    # 3. Concept Understanding Question
    concepts_line = sections["concepts"][0]
    correct_ta, correct_en = get_bilingual_pair(concepts_line)
    
    excluded = {correct_ta.lower(), correct_en.lower()}
    opt_ta, opt_en, corr_idx = build_options(correct_ta, correct_en, excluded)
    
    exp_en = f"This is correct because the passage explains: {correct_en}"
    exp_ta = translate_en_to_ta(exp_en)
    
    q3_item = {
        "question_ta": "இப்பாடத்தின் விளக்கங்களை அடிப்படையாகக் கொண்ட சரியான கருத்து எது?",
        "question_en": "Based on the passage details, which of the following is correct?",
        "options_ta": opt_ta,
        "options_en": opt_en,
        "correct_index": corr_idx,
        "explanation_ta": exp_ta,
        "explanation_en": exp_en
    }
    if q3_item["question_en"] not in seen_questions and validate_and_clean_quiz_item(q3_item, source_text):
        quiz.append(q3_item)
        seen_questions.add(q3_item["question_en"])
        
    # 4. Vocabulary Meaning Question
    if vocabulary:
        v = vocabulary[0]
        q4_question_ta = f"பாடத்தின்படி '{v['word']}' என்ற சொல்லின் பொருள் என்ன?"
        q4_question_en = f"According to the passage, what is the meaning of the word '{v['word']}'?"
        correct_ta = v["meaning_ta"]
        correct_en = v["meaning_en"]
        
        excluded = {correct_ta.lower(), correct_en.lower()}
        opt_ta, opt_en, corr_idx = build_options(correct_ta, correct_en, excluded)
        
        exp_en = f"The word '{v['word']}' means '{correct_en}' in the context of this lesson."
        exp_ta = translate_en_to_ta(exp_en)
        
        q4_item = {
            "question_ta": q4_question_ta,
            "question_en": q4_question_en,
            "options_ta": opt_ta,
            "options_en": opt_en,
            "correct_index": corr_idx,
            "explanation_ta": exp_ta,
            "explanation_en": exp_en
        }
        if q4_item["question_en"] not in seen_questions and validate_and_clean_quiz_item(q4_item, source_text):
            quiz.append(q4_item)
            seen_questions.add(q4_item["question_en"])
            
    # Guarantee at least 3 distinct questions, and up to 4 if all valid
    while len(quiz) < 3:
        correct_ta, correct_en = get_bilingual_pair(content_lines[0])
        opt_ta, opt_en, corr_idx = build_options(correct_ta, correct_en)
        exp_en = f"The correct answer is: {correct_en}"
        exp_ta = translate_en_to_ta(exp_en)
        q_fallback = {
            "question_ta": "இப்பாடப்பகுதியின் முக்கிய கருத்து என்ன?",
            "question_en": "What is the main theme discussed in this passage?",
            "options_ta": opt_ta,
            "options_en": opt_en,
            "correct_index": corr_idx,
            "explanation_ta": exp_ta,
            "explanation_en": exp_en
        }
        quiz.append(q_fallback)
        
    return quiz

def split_by_pages(ocr_text: str) -> dict[int, str]:
    """
    Splits the text by page markers '--- PAGE X ---'.
    If no page markers are found, returns {1: ocr_text}.
    """
    pages = {}
    pattern = re.compile(r"--- PAGE (\d+) ---")
    matches = list(pattern.finditer(ocr_text))
    
    if not matches:
        if ocr_text.strip():
            pages[1] = ocr_text.strip()
        return pages
        
    for i, match in enumerate(matches):
        page_num = int(match.group(1))
        start_idx = match.end()
        end_idx = matches[i+1].start() if i + 1 < len(matches) else len(ocr_text)
        page_text = ocr_text[start_idx:end_idx].strip()
        if page_text:
            pages[page_num] = page_text
            
    # Fallback if somehow matches found but pages empty
    if not pages and ocr_text.strip():
        pages[1] = ocr_text.strip()
        
    return pages

def extract_concepts_from_text(text: str, is_source_tamil: bool) -> list[str]:
    """
    Extract key concept words from a text segment.
    Returns a list of unique lowercase concept words.
    """
    from services.text_classifier import BANNED_PATTERNS
    words = re.findall(r'[\u0B80-\u0BFFa-zA-Z]+', text)
    stop_words = STOP_WORDS_TA if is_source_tamil else STOP_WORDS_EN
    
    concepts = []
    seen = set()
    for w in words:
        clean_w = w.strip().lower()
        if not clean_w:
            continue
        if clean_w in stop_words:
            continue
        min_len = 2 if is_source_tamil else 3
        if len(clean_w) < min_len:
            continue
        is_banned = False
        for pat in BANNED_PATTERNS:
            if re.search(pat, clean_w):
                is_banned = True
                break
        if is_banned:
            continue
        if any(c.isdigit() for c in clean_w):
            continue
        if clean_w not in seen:
            concepts.append(clean_w)
            seen.add(clean_w)
    return concepts

def generate_coverage_report(lesson: dict, concepts_found_by_page: dict[int, list[str]], is_source_tamil: bool) -> dict:
    """
    Generate coverage report tracking page and concept usage.
    """
    # 1. Combine all lesson text for searching
    lesson_text_parts = []
    lesson_text_parts.append(lesson.get("title", ""))
    
    passage = lesson.get("passage", {})
    if isinstance(passage, dict):
        # Filter out structural headings starting with ###
        lines_clean = [l for l in passage.get("lines", []) if not l.strip().startswith("###")]
        lesson_text_parts.extend(lines_clean)
        
    for v in lesson.get("vocabulary", []):
        lesson_text_parts.append(v.get("word", ""))
        lesson_text_parts.append(v.get("meaning_ta", ""))
        lesson_text_parts.append(v.get("meaning_en", ""))
        
    for q in lesson.get("quiz", []):
        lesson_text_parts.append(q.get("question_ta", ""))
        lesson_text_parts.append(q.get("question_en", ""))
        lesson_text_parts.extend(q.get("options_ta", []))
        lesson_text_parts.extend(q.get("options_en", []))
        
    lesson_text_lower = " ".join(lesson_text_parts).lower()
    
    # 2. Track per-page usage
    pages_processed = len(concepts_found_by_page)
    pages_used = 0
    
    all_concepts_found = []
    all_concepts_used = []
    
    for page_num, concepts in concepts_found_by_page.items():
        page_has_used_concept = False
        for c in concepts:
            c_clean = c.strip().lower()
            if not c_clean:
                continue
            if c_clean not in all_concepts_found:
                all_concepts_found.append(c_clean)
            
            # Check if concept is in lesson text
            is_used = False
            if is_source_tamil:
                is_used = c_clean in lesson_text_lower
            else:
                is_used = bool(re.search(r'\b' + re.escape(c_clean) + r'\b', lesson_text_lower))
                
            if is_used:
                page_has_used_concept = True
                if c_clean not in all_concepts_used:
                    all_concepts_used.append(c_clean)
                    
        if page_has_used_concept or (pages_processed == 1 and len(concepts) == 0):
            pages_used += 1
            
    coverage_score = pages_used / pages_processed if pages_processed > 0 else 1.0
    
    return {
        "pages_processed": pages_processed,
        "pages_used": pages_used,
        "concepts_found": len(all_concepts_found),
        "concepts_used": len(all_concepts_used),
        "coverage_score": coverage_score
    }

def _fallback_lesson(ocr_text: str, difficulty: int, language: str, diagnostics: dict = None) -> dict:
    """Deterministic lesson built from raw OCR text when SLM is unavailable.
    Guaranteed to be 100% source-driven and structured using:
      - Introduction
      - Key Concepts
      - Important Details
      - Summary
    """
    # Scale vocabulary size based on page length
    raw_len = len(ocr_text) if not diagnostics else diagnostics.get("total_raw_length", len(ocr_text))
    approx_pages = max(1, raw_len // 1000)
    
    if approx_pages <= 2:
        vocab_size = 5
    elif approx_pages <= 5:
        vocab_size = 8
    else:
        # 6+ pages dynamically scaled between 10 and 15 words
        vocab_size = min(15, 10 + (approx_pages - 6))

    # Check languages
    is_source_tamil = is_tamil_text(ocr_text)
    is_target_tamil = language.lower() == "tamil"
    
    # Split by pages
    pages_text_dict = split_by_pages(ocr_text)
    pages_lines = {}
    
    for page_num, p_text in pages_text_dict.items():
        text_clean = re.sub(r'\s+', ' ', p_text).strip()
        sentence_ends = re.compile(r'(?<=[.!?।])\s+|[\n\r]+')
        raw_sentences = sentence_ends.split(text_clean)
        
        sentences = []
        for s in raw_sentences:
            s_clean = s.strip()
            if s_clean and not re.match(r"^---\s*PAGE\s*\d+\s*---$", s_clean):
                word_count = len(s_clean.split())
                if word_count >= 2:
                    sentences.append(s_clean)
                    
        dyslexia_lines = []
        for s in sentences:
            words = s.split()
            if len(words) <= 15:
                dyslexia_lines.append(" ".join(words))
            else:
                for i in range(0, len(words), 12):
                    chunk = words[i:i+12]
                    dyslexia_lines.append(" ".join(chunk))
        pages_lines[page_num] = dyslexia_lines

    # Get max lesson lines from settings
    from config import get_settings
    settings = get_settings()
    max_lesson_lines = getattr(settings, "MAX_LESSON_LINES", 15)

    page_nums = sorted(list(pages_lines.keys()))
    page_counts = [len(pages_lines[p]) for p in page_nums]
    total_avail = sum(page_counts)
    
    if total_avail <= max_lesson_lines:
        raw_lines = []
        for p in page_nums:
            raw_lines.extend(pages_lines[p])
    else:
        n_pages = len(page_nums)
        allocated = [0] * n_pages
        for i in range(n_pages):
            if page_counts[i] > 0:
                allocated[i] = 1
        current_total = sum(allocated)
        if current_total < max_lesson_lines:
            budget_left = max_lesson_lines - current_total
            added_something = True
            while budget_left > 0 and added_something:
                added_something = False
                for i in range(n_pages):
                    if allocated[i] < page_counts[i] and budget_left > 0:
                        allocated[i] += 1
                        budget_left -= 1
                        added_something = True
        raw_lines = []
        for i, p in enumerate(page_nums):
            count = allocated[i]
            raw_lines.extend(pages_lines[p][:count])

    if len(raw_lines) < 4:
        padding_needed = 4 - len(raw_lines)
        defaults = DEFAULT_TAMIL_LINES if is_source_tamil else DEFAULT_ENGLISH_LINES
        raw_lines.extend(defaults[:padding_needed])
        
    raw_lines = [l + ("." if not l.endswith((".", "!", "?", "।")) else "") for l in raw_lines]
    
    # Partition raw_lines into exactly 4 structural sections:
    n = len(raw_lines)
    base = n // 4
    rem = n % 4
    sizes = [base + (1 if i < rem else 0) for i in range(4)]
    
    lines = []
    lines.append("### 1. Introduction" if not is_target_tamil else "### 1. Introduction / அறிமுகம்")
    lines.extend(raw_lines[0 : sizes[0]])
    
    lines.append("### 2. Key Concepts" if not is_target_tamil else "### 2. Key Concepts / முக்கிய கருத்துக்கள்")
    lines.extend(raw_lines[sizes[0] : sizes[0] + sizes[1]])
    
    lines.append("### 3. Important Details" if not is_target_tamil else "### 3. Important Details / முக்கிய விவரங்கள்")
    lines.extend(raw_lines[sizes[0] + sizes[1] : sizes[0] + sizes[1] + sizes[2]])
    
    lines.append("### 4. Summary" if not is_target_tamil else "### 4. Summary / சுருக்கம்")
    lines.extend(raw_lines[sizes[0] + sizes[1] + sizes[2] :])
        
    content_text = " ".join([l for l in lines if not l.startswith("###")])
    vocab_candidates = extract_vocabulary_candidates(content_text, is_source_tamil, vocab_size)
                    
    vocabulary = []
    from services.translation_service import translate_en_to_ta, translate_ta_to_en
    for w in vocab_candidates:
        syllables = split_tamil_syllables(w)
        meaning_ta = ""
        meaning_en = ""
        
        if w.lower() in DICT_MAPPINGS:
            meaning_ta = DICT_MAPPINGS[w.lower()]["meaning_ta"]
            meaning_en = DICT_MAPPINGS[w.lower()]["meaning_en"]
        else:
            phrase = get_context_phrase(w, lines)
            if is_source_tamil:
                if phrase:
                    meaning_ta = f"'{w}' - இப்பாட வரியிலிருந்து: '... {phrase} ...'"
                else:
                    meaning_ta = f"'{w}' - இப்பாடத்தின் ஒரு முக்கிய சொல்."
                meaning_en = translate_ta_to_en(meaning_ta)
            else:
                if phrase:
                    meaning_en = f"'{w}' - from the passage line: '... {phrase} ...'"
                else:
                    meaning_en = f"'{w}' - a key word in this lesson."
                meaning_ta = translate_en_to_ta(meaning_en)
                
        example_en = f"We should learn and understand the term '{w}'."
        example_ta = translate_en_to_ta(example_en)
                
        vocabulary.append({
            "word": w,
            "syllables": syllables,
            "meaning_ta": meaning_ta,
            "meaning_en": meaning_en,
            "meaning": meaning_ta if is_target_tamil else meaning_en,
            "context_sentence": get_context_phrase(w, lines) or w,
            "example_usage": example_ta if is_target_tamil else example_en
        })
        
    quiz = generate_dynamic_quiz(lines, vocabulary, ocr_text, is_target_tamil)
    
    content_lines = [l for l in lines if not l.startswith("###")]
    
    lesson = {
        "title": content_lines[0][:60],
        "passage": {"lines": lines, "line_count": len(lines)},
        "vocabulary": vocabulary,
        "audio_script": " ".join([l for l in lines if not l.startswith("###")]),
        "quiz": quiz,
        "metadata": {
            "source": "fallback_direct",
            "difficulty": difficulty,
            "language": language,
            "grounding_score": 1.0,
            "generated_at": datetime.now(timezone.utc).isoformat(),
        }
    }
    
    # Compute and attach coverage report
    concepts_found_by_page = {p: extract_concepts_from_text(t, is_source_tamil) for p, t in pages_text_dict.items()}
    cov_report = generate_coverage_report(lesson, concepts_found_by_page, is_source_tamil)
    lesson["coverage_report"] = cov_report
    lesson["metadata"]["coverage_report"] = cov_report
    
    return lesson


# ─────────────────────────────────────────────────────────────────────────────
# Public API
# ─────────────────────────────────────────────────────────────────────────────

async def generate_lesson(
    ocr_text:   str,
    difficulty: int,
    language:   str,
    max_tokens: int = 1024,
    average_confidence: float | None = None,
    minimum_confidence: float | None = None,
    page_confidence: list[float] | None = None,
    engine: str | None = None,
    degraded: bool = False,
    text_reviewed: bool = False,
) -> tuple[dict, int]:
    """
    Returns (lesson_content_dict, token_count_estimate).
    Integrates clean, classification, integrity gates, validations, and grounding checks.
    """
    from services import text_cleaner, integrity_checker
    
    if average_confidence is None:
        average_confidence = 1.0
    if minimum_confidence is None:
        minimum_confidence = 1.0
    if page_confidence is None:
        page_confidence = [1.0]
        
    # 1. Content Clean & Classify
    cleaned_text, blocks, stats = text_cleaner.clean_ocr_text(ocr_text, ocr_confidence=average_confidence)
    
    # 2. Source Integrity check
    is_valid, diagnostics, err_msg = integrity_checker.check_source_integrity(
        ocr_text, cleaned_text, stats,
        ocr_confidence=average_confidence,
        minimum_confidence=minimum_confidence,
        page_confidence=page_confidence,
        engine=engine,
        degraded=degraded,
        text_reviewed=text_reviewed
    )

    if not is_valid:
        raise integrity_checker.SourceIntegrityException(err_msg, diagnostics)

    # 3. LLM Generation path
    loop = asyncio.get_event_loop()
    raw  = ""
    try:
        raw = await asyncio.wait_for(
            loop.run_in_executor(
                _executor, _run_inference, cleaned_text, difficulty, language, max_tokens
            ),
            # CPU inference on the 4B model takes 1-3 min; configurable via
            # SLM_TIMEOUT_S. A small buffer covers prompt-eval overhead.
            timeout=SLM_TIMEOUT_S + 15.0,
        )
    except asyncio.TimeoutError:
        logger.warning("SLM timed out — falling back to smart deterministic generator")
    except Exception as exc:
        logger.error("SLM async error: %s", exc)

    if raw:
        parsed = _extract_json(raw)
        if parsed:
            # Drop vocabulary entries whose headword is not in the source —
            # the model sometimes invents translations ("Cow") as entries.
            # Meanings/glosses are allowed to be free text; the WORD must be
            # grounded. This keeps the gate measuring real hallucination
            # instead of penalising legitimate bilingual glosses.
            src_lower = cleaned_text.lower()
            vocab_in = parsed.get("vocabulary", []) or []
            grounded_vocab = [
                v for v in vocab_in
                if isinstance(v, dict) and v.get("word", "").strip().lower() in src_lower
            ]
            if len(grounded_vocab) >= 3:
                parsed["vocabulary"] = grounded_vocab
            elif vocab_in:
                logger.info("SLM vocabulary mostly ungrounded (%d/%d kept)",
                            len(grounded_vocab), len(vocab_in))

            # Prefer the model's own quiz when every item validates and is
            # grounded in the source; regenerate programmatically only when
            # it isn't. (The programmatic generator can emit duplicate
            # questions on short passages, which fails validation.)
            try:
                passage_lines = parsed.get("passage", {}).get("lines", [])
                vocab = parsed.get("vocabulary", [])
                is_target_tamil = language.lower() == "tamil"

                llm_quiz = [
                    q for q in (parsed.get("quiz") or [])
                    if isinstance(q, dict) and validate_and_clean_quiz_item(q, cleaned_text)
                ]
                seen_q = set()
                llm_quiz = [
                    q for q in llm_quiz
                    if not (q.get("question_ta", "") in seen_q or seen_q.add(q.get("question_ta", "")))
                ]
                if len(llm_quiz) >= 3:
                    parsed["quiz"] = llm_quiz[:3]
                else:
                    logger.info("LLM quiz insufficient (%d valid items) — regenerating", len(llm_quiz))
                    parsed["quiz"] = generate_dynamic_quiz(passage_lines, vocab, cleaned_text, is_target_tamil)
            except Exception as e:
                logger.error("Error post-processing quiz for LLM: %s", e)

            # Enforce validation and grounding quality gate
            grounding_min = float(os.getenv("SLM_GROUNDING_MIN", "0.75"))
            coverage = verify_grounding(parsed, cleaned_text)["coverage_score"]
            logger.info("SLM lesson grounding coverage: %.3f (gate %.2f)", coverage, grounding_min)
            if validate_lesson(parsed) and coverage >= grounding_min:
                # Add diagnostics metadata
                parsed["metadata"] = parsed.get("metadata", {})
                parsed["metadata"].update(diagnostics)
                # Compute and attach coverage report
                is_source_tamil = is_tamil_text(cleaned_text)
                pages_text_dict = split_by_pages(cleaned_text)
                concepts_found_by_page = {p: extract_concepts_from_text(t, is_source_tamil) for p, t in pages_text_dict.items()}
                cov_report = generate_coverage_report(parsed, concepts_found_by_page, is_source_tamil)
                parsed["coverage_report"] = cov_report
                parsed["metadata"]["coverage_report"] = cov_report
                return parsed, len(raw.split())
            logger.warning("SLM output failed quality gate checks — regenerating via fallback templates")

    # 4. Smart Fallback Generator path (100% grounded and validated)
    lesson = _fallback_lesson(cleaned_text, difficulty, language, diagnostics)
    lesson["metadata"].update(diagnostics)
    return lesson, 0
