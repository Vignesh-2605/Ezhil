"""
Text extraction from uploaded document images.

Two engines sit behind one entry point. PaddleOCR is primary: on our reference
Tamil page it scored 94.1% word accuracy against EasyOCR's 17.6%, and EasyOCR's
output was bad enough that generated lessons contained non-words. EasyOCR is
kept only as a fallback for when the paddle worker cannot start, and its output
is marked low-confidence so the integrity gate treats it with suspicion.

Paddle runs out of process — see services/ocr_worker for why.
"""
import logging

logger = logging.getLogger(__name__)

# EasyOCR readers, keyed by our language name. Paddle keeps its own cache in
# the worker process.
_readers = {}

# Our language names -> the code each engine expects.
_PADDLE_LANG = {"tamil": "ta", "english": "en"}
_EASY_LANG = {"tamil": (["ta", "en"], ["en"]), "english": (["en"],)}


def _settings():
    from config import get_settings
    return get_settings()


# --------------------------------------------------------------------------
# PaddleOCR (primary)
# --------------------------------------------------------------------------

def _paddle_enabled() -> bool:
    cfg = _settings()
    return not cfg.DEMO_MODE and cfg.OCR_ENGINE in ("paddle", "auto")


def _downscale_for_paddle(image_bytes: bytes) -> bytes:
    """
    Cap the image before detection.

    Teachers photograph textbook pages with phone cameras, which produce 12 MP
    images. Fed in at full size, detection did not finish in ten minutes on the
    reference machine — the page is legible long before that resolution, and
    the cost is superlinear. Also EXIF-corrects, because a photo taken in
    portrait arrives rotated and reads as gibberish.
    """
    import io

    from PIL import Image, ImageOps

    try:
        img = ImageOps.exif_transpose(Image.open(io.BytesIO(image_bytes)).convert("RGB"))
    except Exception:  # noqa: BLE001 — hand the original to paddle and let it judge
        return image_bytes

    max_side = _settings().OCR_MAX_IMAGE_PX
    scale = min(max_side / max(img.size), 1.0)
    if scale < 1.0:
        img = img.resize(
            (max(int(img.width * scale), 1), max(int(img.height * scale), 1)),
            Image.Resampling.LANCZOS,
        )
        logger.info("Downscaled page to %dx%d for OCR", img.width, img.height)

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _ocr_paddle(image_bytes: bytes, lang_key: str) -> tuple[str, dict] | None:
    """Returns (text, confidence) or None when paddle is unusable."""
    if not _paddle_enabled():
        return None

    from services import paddle_client

    result = paddle_client.recognise(
        _downscale_for_paddle(image_bytes), lang=_PADDLE_LANG.get(lang_key, "ta")
    )
    if result is None:
        return None

    text = result.get("text", "")
    avg = float(result.get("average_confidence", 0.0))
    logger.info(
        "OCR/paddle (lang=%s) extracted %d chars across %d lines, avg conf %.3f",
        lang_key, len(text), len(result.get("texts", [])), avg,
    )
    return text, {
        "average_confidence": avg,
        "minimum_confidence": float(result.get("minimum_confidence", 0.0)),
        "page_confidence": [avg],
        "engine": "paddleocr",
        "line_count": len(result.get("texts", [])),
    }


# --------------------------------------------------------------------------
# EasyOCR (fallback)
# --------------------------------------------------------------------------

def _init_easyocr(lang_key: str) -> bool:
    if lang_key in _readers:
        return True

    cfg = _settings()
    if cfg.DEMO_MODE:
        logger.info("DEMO_MODE: OCR engine bypassed")
        return False
    if cfg.OCR_ENGINE == "paddle":
        return False

    try:
        # EasyOCR still calls the constant Pillow removed in 10.0.
        import PIL.Image
        if not hasattr(PIL.Image, "ANTIALIAS"):
            PIL.Image.ANTIALIAS = PIL.Image.Resampling.LANCZOS
        import easyocr
    except ImportError as exc:
        logger.warning("easyocr not installed (%s)", exc)
        return False

    for langs in _EASY_LANG.get(lang_key, (["en"],)):
        try:
            _readers[lang_key] = easyocr.Reader(langs, gpu=False, verbose=False)
            logger.info("EasyOCR ready (lang=%s, langs=%s)", lang_key, langs)
            return True
        except Exception as exc:  # noqa: BLE001
            logger.warning("EasyOCR init failed with langs=%s (%s)", langs, exc)

    logger.error("All EasyOCR init attempts failed for lang=%s", lang_key)
    return False


def _prepare_image(image_bytes: bytes):
    """EXIF-correct and downscale, for EasyOCR only — paddle handles its own."""
    import io
    import numpy as np
    from PIL import Image, ImageOps

    img = ImageOps.exif_transpose(Image.open(io.BytesIO(image_bytes)).convert("RGB"))
    scale = min(1800 / max(img.size), 1.0)
    if scale < 1.0:
        img = img.resize(
            (int(img.width * scale), int(img.height * scale)),
            Image.Resampling.LANCZOS,
        )
    return np.array(img)


def _ocr_easyocr(image_bytes: bytes, lang_key: str) -> tuple[str, dict] | None:
    if not _init_easyocr(lang_key):
        return None
    try:
        results = _readers[lang_key].readtext(_prepare_image(image_bytes))
    except Exception as exc:  # noqa: BLE001
        logger.error("EasyOCR error (lang=%s): %s", lang_key, exc)
        return None

    accepted = [
        (text.strip(), float(conf))
        for _, text, conf in results
        if conf >= 0.15 and text.strip()
    ]
    if not accepted:
        return None

    confs = [c for _, c in accepted]
    avg = sum(confs) / len(confs)
    logger.info(
        "OCR/easyocr (lang=%s) extracted %d lines, avg conf %.3f — fallback engine",
        lang_key, len(accepted), avg,
    )
    return " ".join(t for t, _ in accepted), {
        "average_confidence": avg,
        "minimum_confidence": min(confs),
        "page_confidence": [avg],
        "engine": "easyocr",
        "line_count": len(accepted),
        # EasyOCR's Tamil word accuracy is poor enough that a teacher must read
        # the extracted text before it reaches the generator, whatever the
        # engine's own confidence claims.
        "degraded": True,
    }


# --------------------------------------------------------------------------
# Public API
# --------------------------------------------------------------------------

_EMPTY = {
    "average_confidence": 0.0,
    "minimum_confidence": 0.0,
    "page_confidence": [0.0],
    "engine": "none",
    "line_count": 0,
}


def is_ready() -> bool:
    """True when at least one engine can serve a request. Never blocks."""
    if _paddle_enabled():
        from services import paddle_client

        if paddle_client.status() == "ready":
            return True
    if _settings().OCR_ENGINE == "paddle":
        return False
    return _init_easyocr("tamil")


def engine_status() -> dict:
    """Per-engine readiness, for /health and for diagnosing a bad extraction."""
    cfg = _settings()
    status = {"configured": cfg.OCR_ENGINE, "paddle": False, "easyocr": False}
    if _paddle_enabled():
        from services import paddle_client

        state = paddle_client.status()
        status["paddle_state"] = state          # ready | starting | unavailable
        status["paddle"] = state == "ready"
        if state == "unavailable":
            status["paddle_error"] = paddle_client.unavailable_reason()
    if cfg.OCR_ENGINE != "paddle":
        status["easyocr"] = _init_easyocr("tamil")
    return status


def ocr_image(image_bytes: bytes, lang: str = "tamil") -> tuple[str, dict]:
    """
    Extract text from [image_bytes].

    Returns (text, confidence_dict). On total failure the text is empty and
    every confidence is 0.0, which the integrity gate rejects.
    """
    lang_key = lang.lower().strip()

    result = _ocr_paddle(image_bytes, lang_key)
    if result is not None and result[0]:
        return result

    if result is None and _settings().OCR_ENGINE == "paddle":
        logger.error("PaddleOCR unavailable and fallback disabled (OCR_ENGINE=paddle)")
        return "", dict(_EMPTY)

    fallback = _ocr_easyocr(image_bytes, lang_key)
    if fallback is not None:
        return fallback

    return "", dict(_EMPTY)
