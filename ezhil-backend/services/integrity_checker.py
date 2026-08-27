import logging
from typing import Dict, Any, Tuple

logger = logging.getLogger(__name__)

class SourceIntegrityException(Exception):
    """Exception raised when document content fails quality or academic density checks."""
    def __init__(self, message: str, diagnostics: Dict[str, Any]):
        super().__init__(message)
        self.diagnostics = diagnostics

def check_source_integrity(
    raw_text: str, 
    cleaned_text: str, 
    stats: Dict[str, int], 
    ocr_confidence: float = 1.0,
    minimum_confidence: float = 1.0,
    page_confidence: list[float] = None,
    engine: str | None = None,
    degraded: bool = False,
    text_reviewed: bool = False
) -> Tuple[bool, Dict[str, Any], str | None]:
    """
    Calculates metrics and checks source document integrity thresholds.
    Returns: (is_valid, diagnostics_dict, error_message)
    """
    total_raw_len = len(raw_text)
    total_clean_len = len(cleaned_text)
    total_blocks = max(sum(stats.values()), 1)
    
    academic_ratio = total_clean_len / max(total_raw_len, 1)
    metadata_ratio = stats.get("METADATA", 0) / total_blocks
    
    if page_confidence is None:
        page_confidence = [ocr_confidence]
        
    diagnostics = {
        "total_raw_length": total_raw_len,
        "total_cleaned_length": total_clean_len,
        "academic_content_ratio": round(academic_ratio, 3),
        "metadata_ratio": round(metadata_ratio, 3),
        "ocr_confidence": round(ocr_confidence, 3),
        "average_confidence": round(ocr_confidence, 3),
        "minimum_confidence": round(minimum_confidence, 3),
        "page_confidence": [round(c, 3) for c in page_confidence],
        "block_statistics": stats,
        "ocr_engine": engine or "unknown"
    }
    
    # 1. Check if cleaned content is too short
    if total_clean_len < 100:
        msg = "The uploaded document contains insufficient academic content to generate a lesson."
        return False, diagnostics, msg
        
    # 2. Check if metadata ratio is excessive
    if metadata_ratio > 0.40:
        msg = "The uploaded file contains mostly metadata and administrative information."
        return False, diagnostics, msg
        
    # 3. Check if academic content ratio is too low
    if academic_ratio < 0.20:  # Allow slightly lower ratio for highly noisy scans, but flag
        msg = "The document quality is too low or contains too little learning content for reliable text extraction."
        return False, diagnostics, msg
        
    # 4. OCR quality gate.
    #
    # This threshold was 0.45, set back when EasyOCR was the only engine. It
    # let through a real extraction scoring 0.533 that was 17.6% word-accurate,
    # and the generator turned that into a lesson full of non-words. PaddleOCR
    # scores ~0.95 on a clean page, so a sub-0.75 result now means the photo
    # itself is unusable rather than the engine being weak.
    from config import get_settings
    floor = get_settings().OCR_MIN_CONFIDENCE
    # Just above the floor: accepted, but flagged so the teacher reads the
    # extracted text before generating.
    warn_ceiling = min(floor + 0.13, 0.99)

    # A teacher who has read the extraction and corrected it has replaced the
    # engine's judgement with their own. Rejecting them on the photo's score
    # would make the review step a dead end — the one path where the text is
    # known good is the one that could never proceed. The length and content
    # gates above still apply.
    if text_reviewed:
        diagnostics["text_reviewed"] = True
        if ocr_confidence < floor:
            logger.info(
                "Accepting reviewed text despite OCR confidence %.2f (engine=%s)",
                ocr_confidence, engine,
            )
        return True, diagnostics, None

    if ocr_confidence < floor:
        msg = (
            f"The photo is too blurry or dark to read reliably "
            f"(confidence {ocr_confidence:.2f}, needs {floor:.2f}). "
            f"Retake it in brighter light, holding the page flat and filling the frame."
        )
        return False, diagnostics, msg

    if ocr_confidence <= warn_ceiling:
        diagnostics["ocr_warning"] = (
            f"Text was extracted, but some words may be wrong "
            f"(confidence {ocr_confidence:.2f}). Please check it before generating."
        )
        diagnostics["requires_review"] = True
        logger.warning("Low OCR quality (avg confidence %.2f, engine=%s)", ocr_confidence, engine)

    # A degraded engine needs review whatever confidence it reports — EasyOCR
    # rates its own bad Tamil output highly.
    if degraded:
        diagnostics["requires_review"] = True
        diagnostics.setdefault(
            "ocr_warning",
            "Text was extracted with the backup reader, which is less accurate "
            "for Tamil. Please check every line before generating.",
        )
        logger.warning("Degraded OCR engine (%s) — review required", engine)

    return True, diagnostics, None



def review_requirement(
    ocr_confidence: float, engine: str | None = None, degraded: bool = False
) -> Tuple[bool, str | None]:
    """
    Whether the teacher must read the extracted text before generating.

    Called at extraction time so the studio can show the requirement up front,
    rather than at generation time when the teacher has already committed. The
    thresholds match check_source_integrity — this is the same gate, surfaced
    earlier.
    """
    from config import get_settings
    floor = get_settings().OCR_MIN_CONFIDENCE

    if engine in (None, "none") or ocr_confidence <= 0.0:
        from services import paddle_client

        if paddle_client.unavailable_reason():
            # The reader could not run at all. Saying "retake the photo" here
            # would send the teacher to fix something that is not broken.
            return True, (
                "The text reader is not available right now. Type or paste the "
                "passage below, or try again shortly."
            )
        return True, (
            "No text could be read from this page. Type or paste the passage "
            "below, or retake the photo in brighter light."
        )
    if degraded or engine != "paddleocr":
        return True, (
            "This page was read with the backup reader, which is less accurate "
            "for Tamil. Please check every line before generating."
        )
    if ocr_confidence < floor:
        return True, (
            f"The photo was hard to read (confidence {ocr_confidence:.2f}). "
            f"Please check the text carefully, or retake the photo."
        )
    if ocr_confidence <= min(floor + 0.13, 0.99):
        return True, (
            f"Most of the text came through, but some words may be wrong "
            f"(confidence {ocr_confidence:.2f}). Please check it before generating."
        )
    return False, None
