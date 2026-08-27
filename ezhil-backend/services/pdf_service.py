"""
PDF Text Extraction Service
===========================
Two-pass strategy:
  Pass 1 — Extract embedded text (digital/searchable PDFs).
            Fast, no OCR, preserves all Tamil Unicode.
  Pass 2 — If text is too sparse (<40 chars/page avg), fall back:
            render each page as a PIL image and run EasyOCR on it.
            Handles scanned Tamil textbooks from government schools.

Dependencies:
    pip install pymupdf
"""
from __future__ import annotations

import io
import logging
from typing import List, Tuple

logger = logging.getLogger(__name__)

_MIN_CHARS_PER_PAGE = 40   # threshold to decide "this is a scanned PDF"


def extract_text(pdf_bytes: bytes, lang: str = "tamil") -> Tuple[str, int, str, dict]:
    """
    Extract text from a PDF.

    Returns:
        (combined_text, page_count, method, confidence_dict)
        method is 'native' | 'ocr' | 'empty'
    """
    try:
        import fitz  # PyMuPDF
    except ImportError:
        logger.warning("PyMuPDF not installed — pip install pymupdf")
        return "", 0, "error", {"average_confidence": 0.0, "minimum_confidence": 0.0, "page_confidence": [0.0]}

    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
        page_count = doc.page_count

        # ── Pass 1: native text ────────────────────────────────────────────
        pages_text: List[str] = []
        for page_num, page in enumerate(doc):
            text = page.get_text("text").strip()
            if text:
                pages_text.append(f"--- PAGE {page_num + 1} ---\n{text}")

        combined = "\n\n".join(p for p in pages_text if p)
        avg_chars = len(combined) / max(page_count, 1)

        if avg_chars >= _MIN_CHARS_PER_PAGE:
            from services import text_cleaner
            cleaned, _blocks, _stats = text_cleaner.clean_ocr_text(combined)
            logger.info("PDF native extraction: %d pages, %d chars (cleaned)", page_count, len(cleaned))
            doc.close()
            return cleaned, page_count, "native", {
                "average_confidence": 1.0,
                "minimum_confidence": 1.0,
                "page_confidence": [1.0] * page_count
            }

        # ── Pass 2: render pages → OCR ─────────────────────────────────────
        logger.info(
            "PDF avg %.0f chars/page < threshold — falling back to OCR on %d pages (lang=%s)",
            avg_chars, page_count, lang,
        )
        from services import ocr_service
        ocr_texts: List[str] = []
        page_confs: List[float] = []
        min_confs: List[float] = []
        max_ocr_pages = 20
        for page_num, page in enumerate(doc):
            if page_num >= max_ocr_pages:
                ocr_texts.append(f"\n[TRUNCATED: PDF has too many pages. OCR is limited to the first {max_ocr_pages} pages for performance.]\n")
                break
            mat    = fitz.Matrix(2.0, 2.0)   # 2× scale for better OCR
            pix    = page.get_pixmap(matrix=mat, alpha=False)
            img_bytes = pix.tobytes("png")
            text, conf_info = ocr_service.ocr_image(img_bytes, lang=lang)
            if text.strip():
                ocr_texts.append(f"--- PAGE {page_num + 1} ---\n{text.strip()}")
            page_confs.append(conf_info["average_confidence"])
            min_confs.append(conf_info["minimum_confidence"])
            logger.debug("Page %d OCR: %d chars, conf=%.2f", page_num + 1, len(text), conf_info["average_confidence"])

        doc.close()
        combined_ocr = "\n\n".join(ocr_texts)
        
        from services import text_cleaner
        cleaned_ocr, _blocks, _stats = text_cleaner.clean_ocr_text(combined_ocr)
        logger.info("PDF OCR extraction: %d chars total (cleaned)", len(cleaned_ocr))
        
        avg_conf = sum(page_confs) / len(page_confs) if page_confs else 0.0
        min_conf = min(min_confs) if min_confs else 0.0
        
        return cleaned_ocr, page_count, "ocr", {
            "average_confidence": avg_conf,
            "minimum_confidence": min_conf,
            "page_confidence": page_confs
        }

    except Exception as exc:
        logger.error("PDF extraction error: %s", exc)
        return "", 0, "error", {"average_confidence": 0.0, "minimum_confidence": 0.0, "page_confidence": [0.0]}

