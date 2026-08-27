"""
Studio Router — lesson creation pipeline
========================================
Endpoints:
  POST /ocr            — single image → text  (original, kept for mobile compat)
  POST /extract-multi  — 1-20 files (image/pdf/docx/txt) → combined text  (NEW)
  POST /generate       — text → AI lesson JSON
"""
from __future__ import annotations

import hashlib
import json
import uuid
from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth_utils import get_current_teacher
from config import get_settings
from db import get_db
from models.db_models import Lesson, Teacher
from schemas.pydantic_schemas import (
    FileExtractionResult,
    GenerateRequest,
    GenerateResponse,
    LessonContent,
    LessonMetadata,
    LessonPassage,
    MultiExtractResponse,
    OcrResponse,
)
from services import integrity_checker, ocr_service, slm_service

router = APIRouter()

# In-memory cache for extraction confidence metadata
_EXTRACTION_CACHE = {}


# ── helpers ──────────────────────────────────────────────────────────────────

def _file_type(filename: str, content_type: str) -> str:
    name = (filename or "").lower()
    ct   = (content_type or "").lower()
    if name.endswith(".pdf")  or "pdf"  in ct:             return "pdf"
    if name.endswith(".docx") or "wordprocessingml" in ct: return "docx"
    if name.endswith(".doc")  or "msword" in ct:           return "docx"
    if name.endswith(".txt")  or "text/plain" in ct:       return "text"
    return "image"


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


# ── /ocr  (single image — original endpoint, backwards-compatible) ────────────

@router.post("/ocr", response_model=OcrResponse)
async def studio_ocr(
    image: UploadFile = File(...),
    source_hash: str = Form(""),
    language: str = Form("tamil"),
    _teacher: Teacher = Depends(get_current_teacher),
):
    try:
        print(f"[STUDIO] OCR request from teacher: {_teacher.name} (Code: {_teacher.teacher_code}, lang={language})")
    except UnicodeEncodeError:
        safe_name = _teacher.name.encode('ascii', errors='replace').decode('ascii')
        print(f"[STUDIO] OCR request from teacher: {safe_name} (Code: {_teacher.teacher_code}, lang={language})")
    image_bytes = await image.read()
    
    from starlette.concurrency import run_in_threadpool
    text, conf_info = await run_in_threadpool(ocr_service.ocr_image, image_bytes, lang=language)
    
    needs_review, review_reason = integrity_checker.review_requirement(
        conf_info["average_confidence"],
        engine=conf_info.get("engine"),
        degraded=conf_info.get("degraded", False),
    )
    conf_info = {**conf_info, "requires_review": needs_review}

    # Cache under the hash of the extracted text so /generate can recover the
    # confidence for this extraction — the client sends the hash back.
    src_hash = ""
    if text.strip():
        try:
            import hashlib

            src_hash = hashlib.sha256(text.encode()).hexdigest()
            _EXTRACTION_CACHE[src_hash] = conf_info
        except Exception:
            pass

    return OcrResponse(
        extracted_text=text,
        char_count=len(text),
        confidence=conf_info["average_confidence"],
        minimum_confidence=conf_info["minimum_confidence"],
        ocr_engine=conf_info.get("engine"),
        source_hash=src_hash,
        requires_review=needs_review,
        review_reason=review_reason,
    )


# ── /extract-multi  (NEW — multi-file, mixed types) ───────────────────────────

def _extract_single_file_sync(filename: str, content_type: str, raw: bytes, lang: str = "tamil") -> FileExtractionResult:
    ftype = _file_type(filename, content_type)
    try:
        if ftype == "image":
            text, conf_info = ocr_service.ocr_image(raw, lang=lang)
            if text.strip():
                text = f"--- PAGE 1 ---\n{text}"
            return FileExtractionResult(
                filename       = filename or "image",
                file_type      = "image",
                extracted_text = text,
                char_count     = len(text),
                page_count     = 1,
                method         = "ocr",
                status         = "success" if text.strip() else "empty",
                average_confidence = conf_info["average_confidence"],
                minimum_confidence = conf_info["minimum_confidence"],
                page_confidence = conf_info["page_confidence"],
                ocr_engine = conf_info.get("engine"),
            )

        elif ftype == "pdf":
            from services import pdf_service
            text, pages, method, conf_info = pdf_service.extract_text(raw, lang=lang)
            return FileExtractionResult(
                filename       = filename or "document.pdf",
                file_type      = "pdf",
                extracted_text = text,
                char_count     = len(text),
                page_count     = pages,
                method         = method,
                status         = "success" if text.strip() else "empty",
                average_confidence = conf_info["average_confidence"],
                minimum_confidence = conf_info["minimum_confidence"],
                page_confidence = conf_info["page_confidence"]
            )

        elif ftype == "docx":
            from services import docx_service
            text, paras = docx_service.extract_text(raw)
            if text.strip():
                text = f"--- PAGE 1 ---\n{text}"
            return FileExtractionResult(
                filename       = filename or "document.docx",
                file_type      = "docx",
                extracted_text = text,
                char_count     = len(text),
                page_count     = paras,
                method         = "direct",
                status         = "success" if text.strip() else "empty",
                average_confidence = 1.0,
                minimum_confidence = 1.0,
                page_confidence = [1.0] * paras
            )

        else:   # plain text
            text = raw.decode("utf-8", errors="replace").strip()
            if text.strip():
                text = f"--- PAGE 1 ---\n{text}"
            return FileExtractionResult(
                filename       = filename or "file.txt",
                file_type      = "text",
                extracted_text = text,
                char_count     = len(text),
                page_count     = 1,
                method         = "direct",
                status         = "success" if text else "empty",
                average_confidence = 1.0,
                minimum_confidence = 1.0,
                page_confidence = [1.0]
            )

    except Exception as exc:
        return FileExtractionResult(
            filename       = filename or "unknown",
            file_type      = ftype,
            extracted_text = "",
            char_count     = 0,
            status         = "error",
            error          = str(exc),
        )


@router.post("/extract-multi", response_model=MultiExtractResponse)
async def studio_extract_multi(
    files: List[UploadFile] = File(...),
    language: str = Form("tamil"),
    _teacher: Teacher = Depends(get_current_teacher),
):
    """
    Accept 1-20 files of mixed types and return per-file extraction results
    plus a single combined text ready to pass to /generate.

    Supported types:
      • Images (JPEG, PNG, WEBP, BMP, TIFF) → EasyOCR
      • PDF  → PyMuPDF native text; OCR fallback for scanned pages
      • DOCX → python-docx paragraphs + table cells
      • TXT  → direct UTF-8 decode
    """
    if len(files) > 20:
        raise HTTPException(status_code=400, detail="Maximum 20 files per request.")

    from services.integrity_checker import SourceIntegrityException
    try:
        results:   List[FileExtractionResult] = []
        all_texts: List[str] = []
        multi      = len(files) > 1

        from starlette.concurrency import run_in_threadpool

        import re
        global_page_counter = 1
        for upload in files:
            raw   = await upload.read()
            result = await run_in_threadpool(
                _extract_single_file_sync,
                upload.filename or "",
                upload.content_type or "",
                raw,
                language,
            )
            if result.extracted_text.strip():
                text = result.extracted_text.strip()
                def replace_page_marker(match):
                    nonlocal global_page_counter
                    replacement = f"--- PAGE {global_page_counter} ---"
                    global_page_counter += 1
                    return replacement
                
                reindexed_text = re.sub(r"--- PAGE \d+ ---", replace_page_marker, text)
                result.extracted_text = reindexed_text
                
                header = f"[{upload.filename}]\n" if multi else ""
                all_texts.append(header + reindexed_text)
            results.append(result)

        combined = "\n\n---\n\n".join(all_texts)
        src_hash = _sha256(combined.encode()) if combined else ""

        # Compute combined confidence across all extracted files
        valid_confs = [r.average_confidence for r in results if r.average_confidence is not None]
        avg_c = sum(valid_confs) / len(valid_confs) if valid_confs else 1.0
        
        valid_mins = [r.minimum_confidence for r in results if r.minimum_confidence is not None]
        min_c = min(valid_mins) if valid_mins else 1.0
        
        combined_page_confs = []
        for r in results:
            if r.page_confidence:
                combined_page_confs.extend(r.page_confidence)
        if not combined_page_confs:
            combined_page_confs = [1.0]
            
        # A batch is only as trustworthy as its weakest page, so the engine
        # reported for the whole extraction is the worst one used.
        engines = {r.ocr_engine for r in results if r.ocr_engine}
        engine = "easyocr" if "easyocr" in engines else next(iter(engines), None)
        degraded = engine not in (None, "paddleocr")

        needs_review, review_reason = integrity_checker.review_requirement(
            min_c if valid_mins else avg_c, engine=engine, degraded=degraded
        ) if engine else (False, None)

        conf_info = {
            "average_confidence": avg_c,
            "minimum_confidence": min_c,
            "page_confidence": combined_page_confs,
            "engine": engine,
            "degraded": degraded,
            "requires_review": needs_review,
        }

        if src_hash:
            _EXTRACTION_CACHE[src_hash] = conf_info

        return MultiExtractResponse(
            files         = results,
            combined_text = combined,
            total_chars   = len(combined),
            source_hash   = src_hash,
            average_confidence = avg_c,
            minimum_confidence = min_c,
            page_confidence = combined_page_confs,
            ocr_engine = engine,
            requires_review = needs_review,
            review_reason = review_reason,
        )
    except SourceIntegrityException as exc:
        raise HTTPException(status_code=400, detail=exc.args[0])


# ── /generate  (unchanged) ────────────────────────────────────────────────────

@router.post("/generate", response_model=GenerateResponse)
async def studio_generate(
    req: GenerateRequest,
    db: AsyncSession = Depends(get_db),
    teacher: Teacher = Depends(get_current_teacher),
):
    if req.source_hash:
        cached_r = await db.execute(
            select(Lesson).where(
                Lesson.source_hash == req.source_hash,
                Lesson.is_published.is_(True),
            )
        )
        cached = cached_r.scalar_one_or_none()
        if cached:
            try:
                lesson_content = LessonContent(**json.loads(cached.content_json))
            except Exception:
                lesson_content = _minimal_content(cached.title)
            return GenerateResponse(
                lesson_id   = cached.id,
                lesson      = lesson_content,
                cache_hit   = True,
                source_hash = req.source_hash,
                token_count = 0,
            )

    cfg = get_settings()
    from services.integrity_checker import SourceIntegrityException
    
    # Retrieve confidence metrics from request or extraction cache
    avg_c = req.average_confidence
    min_c = req.minimum_confidence
    page_c = req.page_confidence
    
    engine = req.ocr_engine
    degraded = False
    needs_review = False

    if req.source_hash and req.source_hash in _EXTRACTION_CACHE:
        cached_conf = _EXTRACTION_CACHE[req.source_hash]
        if avg_c is None: avg_c = cached_conf.get("average_confidence")
        if min_c is None: min_c = cached_conf.get("minimum_confidence")
        if page_c is None: page_c = cached_conf.get("page_confidence")
        engine = engine or cached_conf.get("engine")
        degraded = cached_conf.get("degraded", False)
        needs_review = cached_conf.get("requires_review", False)
    elif avg_c is not None:
        # Cache miss. This is the normal case for a multi-file upload, where
        # the client combines several extractions into text no single cached
        # hash describes. The client still reports the confidence it was given,
        # so the gate is recomputed here rather than skipped — otherwise
        # combining two pages would be enough to bypass review entirely.
        degraded = bool(engine) and engine != "paddleocr"
        needs_review, _ = integrity_checker.review_requirement(
            min_c if min_c is not None else avg_c, engine=engine, degraded=degraded
        )

    # A flagged extraction must not reach the generator on trust alone. The
    # cost of skipping this is a published lesson containing non-words, which a
    # dyslexic child then practises reading — the client cannot be allowed to
    # decide it knows better, so the check lives here.
    if needs_review and not req.text_reviewed:
        raise HTTPException(
            status_code=428,
            detail=(
                "This page was hard to read. Please check the extracted text and "
                "correct any wrong words before generating the lesson."
            ),
        )

    try:
        content_dict, token_count = await slm_service.generate_lesson(
            ocr_text   = req.ocr_text,
            difficulty = req.difficulty,
            language   = req.language,
            max_tokens = cfg.SLM_MAX_TOKENS,
            average_confidence = avg_c,
            minimum_confidence = min_c,
            page_confidence = page_c,
            engine = engine,
            degraded = degraded,
            text_reviewed = req.text_reviewed,
        )
    except SourceIntegrityException as exc:
        raise HTTPException(status_code=400, detail=exc.args[0])

    lesson_id  = str(uuid.uuid4())
    new_lesson = Lesson(
        id           = lesson_id,
        teacher_id   = teacher.id,
        source_hash  = req.source_hash or None,
        title        = content_dict.get("title", "பாடம்"),
        lesson_type  = "story",
        difficulty   = req.difficulty,
        language     = req.language,
        content_json = json.dumps(content_dict, ensure_ascii=False),
        is_published = False,
        cache_hit    = False,
    )
    db.add(new_lesson)
    await db.flush()

    try:
        lesson_content = LessonContent(**content_dict)
    except Exception:
        lesson_content = _minimal_content(content_dict.get("title", "பாடம்"))

    return GenerateResponse(
        lesson_id   = lesson_id,
        lesson      = lesson_content,
        cache_hit   = False,
        source_hash = req.source_hash,
        token_count = token_count,
    )


def _minimal_content(title: str) -> LessonContent:
    return LessonContent(
        title    = title,
        passage  = LessonPassage(lines=[title], line_count=1),
        metadata = LessonMetadata(),
    )
