from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # Without this, pydantic-settings reads OS environment variables only and
    # silently ignores .env — so the documented `cp .env.example .env` setup
    # step had no effect. Absolute path so the file is found regardless of the
    # working directory the server is launched from.
    model_config = SettingsConfigDict(
        env_file=Path(__file__).resolve().parent / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    DATABASE_URL: str = "sqlite+aiosqlite:///./ezhil.db"
    # Dev-only default. Startup refuses to serve with this value unless
    # DEMO_MODE is on — see main.py. Generate one with:
    #   python -c "import secrets; print(secrets.token_hex(32))"
    SECRET_KEY: str = "ezhil-dev-key-123"
    ALGORITHM: str = "HS256"
    # Teachers and children in low-connectivity classrooms should not be
    # logged out mid-term, but a year-long token with no revocation path is
    # too long-lived. 30 days is the compromise.
    ACCESS_TOKEN_EXPIRE_DAYS: int = 30
    SLM_MODEL_NAME: str = "unsloth/gemma-4-E4B-it-qat-GGUF"
    GGUF_PATH:      str = "E:/PDD/10-03-2026/DysLearn/models/gemma-4-E4B-it-qat-UD-Q4_K_XL.gguf"
    # Tamil script is token-heavy (~2 tokens/char); a full lesson JSON needs
    # well over 1024 tokens or the output truncates mid-JSON.
    SLM_MAX_TOKENS: int = 2048
    MAX_LESSON_LINES: int = 15
    SLM_DEVICE:     str = "auto"   # "auto" detects CUDA/MPS/CPU at runtime
    SLM_N_THREADS:  int = 4
    OCR_FALLBACK_ENABLED: bool = True
    # OCR engine. PaddleOCR's Tamil recogniser measured 94.1% word accuracy on
    # our reference page against EasyOCR's 17.6% — the difference between a
    # usable lesson and one full of mangled words. It runs in a separate
    # process (services/ocr_worker) because paddle and torch cannot share one
    # on Windows.
    #
    # The default is "paddle", with no in-process fallback, for a memory
    # reason rather than a quality one. The worker peaks at ~3.5 GB during
    # detection, and loading EasyOCR alongside it costs the API process
    # another ~1 GB. On a 16 GB host running the Gemma server too, that
    # combination leaves under 2 GB free and paddle segfaults mid-inference —
    # so the fallback reliably destroys the engine it is meant to back up.
    # A failed extraction now asks the teacher to retype or retake instead,
    # which is honest. Set "auto" only on a host with memory to spare.
    OCR_ENGINE: str = "paddle"          # paddle | easyocr | auto
    # Peak resident memory the OCR worker needs while running detection. Below
    # this it dies with a native segfault rather than an exception.
    OCR_WORKER_PEAK_MB: int = 3500
    # The Gemma server holds ~5.2 GB resident and OCR peaks at ~3.5 GB, which
    # does not fit alongside everything else on a 16 GB host. They are never
    # needed at the same instant — a teacher reads a page, checks the text, and
    # only then generates — so OCR is allowed to stop the LLM to make room, and
    # the LLM reloads on the next generation. Set false on a host with enough
    # memory for both, where the reload would be pure cost.
    SLM_RELEASE_FOR_OCR: bool = True
    # Stop the LLM after this long with no generation, so its memory is not
    # held overnight. 0 disables the idle timer.
    SLM_IDLE_TIMEOUT_S: int = 900
    OCR_MIN_CONFIDENCE: float = 0.75
    # How long to wait for memory to come back after standing the LLM down.
    # Terminating a 3.9 GB process does not free it synchronously. Tests set
    # this to ~0 so the suite does not sit through the real wait.
    OCR_MEMORY_WAIT_S: float = 30.0
    # Longest edge an image is scaled to before detection. A 12 MP phone photo
    # at full size did not finish detecting in ten minutes; at 1800px the same
    # page reads in seconds with no loss of accuracy.
    OCR_MAX_IMAGE_PX: int = 1800
    # NLLB-200-distilled-600M powers bilingual glosses in the lesson generator.
    # It is ~2.4 GB and is fetched from HuggingFace on first use. Turn it off to
    # fall back to the built-in dictionary — lower quality, but instant and
    # fully offline. Always off in DEMO_MODE.
    TRANSLATION_ENABLED: bool = True
    # Never reach the network for model weights; requires a warm HF cache.
    HF_LOCAL_FILES_ONLY: bool = False
    # Comma-separated origins allowed to call the API. "*" disables credentialed
    # requests (see main.py) so it is not directly exploitable, but it should
    # name the real web origin in production rather than accepting anything.
    ALLOWED_ORIGINS: str = "*"
    LOG_LEVEL: str = "INFO"
    HOST: str = "0.0.0.0"
    PORT: int = 8080
    DEMO_MODE: bool = False  # Set to False to enable OCR and SLM engines


@lru_cache
def get_settings() -> Settings:
    return Settings()
