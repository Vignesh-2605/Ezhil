import re
import logging
from typing import List, Dict, Any, Tuple

logger = logging.getLogger(__name__)

# Banned institutional and administrative terms
BANNED_PATTERNS = [
    r"simats",
    r"saveetha",
    r"institute of medical",
    r"technical sciences",
    r"school of engineering",
    r"college of engineering",
    r"department of",
    r"register\s*no",
    r"reg\s*no",
    r"roll\s*no",
    r"course\s*code",
    r"subject\s*code",
    r"assessment\s*tool",
    r"academic\s*year",
    r"semester",
    r"date\s*of\s*exam",
    r"signature",
    r"examiner",
    r"hall\s*ticket",
    r"watermark",
    r"confidential",
    r"class\s*code",
]

QUESTION_PATTERNS = [
    r"^\s*\d+[\s\.)\-]+[A-Z\u0B85-\u0B94]",  # E.g. "1. Lesson" or "1) Lesson" or "1 - Lesson"
    r"^\s*q\d+[:\.\s\-]",
    r"^\s*question\s*\d+[:\.\s\-]",
]

TABLE_PATTERNS = [
    r"\|",
    r"\+---+",
    r"^\s*s\.no\b",
    r"^\s*serial\s*no\b",
]

HEADING_PATTERNS = [
    # English chapter, unit, section headings
    r"\bchapter\s+\d+\b",
    r"\bchapter\s+[ivxldcm]+\b",
    r"\bunit\s+\d+\b",
    r"\bunit\s+[ivxldcm]+\b",
    r"\bsection\s+\d+\b",
    r"^\s*introduction\b",
    r"^\s*summary\b",
    r"^\s*key\s+concepts\b",
    r"^\s*key\s+details\b",
    r"^\s*conclusion\b",
    r"^\s*\d+(\.\d+)*\s+[A-Za-z\u0B80-\u0BFF]",  # E.g. "1. Introduction" or "1.1 Core Concept"
    # Tamil chapter, unit, section headings
    r"^\s*அத்தியாயம்\s+\d+\b",
    r"^\s*அலகு\s+\d+\b",
    r"^\s*பகுதி\s+\d+\b",
    r"^\s*அறிமுகம்\b",
    r"^\s*சுருக்கம்\b",
    r"^\s*முன்னுரை\b",
    r"^\s*நோக்கம்\b",
]

CAPTION_PATTERNS = [
    # English figures and captions
    r"^\s*figure\s+\d+\b",
    r"^\s*fig\.\s*\d+\b",
    r"^\s*image\s+\d+\b",
    r"^\s*img\.\s*\d+\b",
    r"^\s*caption\b",
    # Tamil figures and captions
    r"^\s*படம்\s+\d+\b",
    r"^\s*வரைபடம்\s+\d+\b",
]

def classify_line(line: str) -> str:
    """Classify a single line of OCR text into structural categories."""
    clean_line = line.strip()
    if not clean_line:
        return "EMPTY"
        
    if re.match(r"^---\s*PAGE\s*\d+\s*---$", clean_line):
        return "PAGE_MARKER"
        
    line_lower = clean_line.lower()
    
    # 1. Page numbers
    page_patterns = [
        r"^\s*\d+\s*$",
        r"^\s*page\s*\d+\s*$",
        r"^\s*page\s*\d+\s*of\s*\d+\s*$",
        r"^\s*-\s*\d+\s*-\s*$",
    ]
    for pat in page_patterns:
        if re.search(pat, line_lower):
            return "FOOTER"  # page numbers are footer/removed
            
    # Dates pattern
    date_patterns = [
        r"\b\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4}\b",
        r"\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]* \d{1,2},? \d{4}\b"
    ]
    for pat in date_patterns:
        if re.search(pat, line_lower):
            return "METADATA"  # dates are metadata/removed
            
    # 2. Metadata / Banned Administrative content
    for pat in BANNED_PATTERNS:
        if re.search(pat, line_lower):
            return "METADATA"
            
    # 3. Table boundaries / Markdown tables
    for pat in TABLE_PATTERNS:
        if re.search(pat, line_lower):
            return "TABLE"
            
    # 4. Question formats
    for pat in QUESTION_PATTERNS:
        if re.match(pat, clean_line):
            return "QUESTION"
            
    # 5. Academic Headings (Chapter titles, Unit titles, Section headings)
    for pat in HEADING_PATTERNS:
        if re.search(pat, line_lower):
            return "ACADEMIC_HEADING"
            
    # 6. Image Captions
    for pat in CAPTION_PATTERNS:
        if re.search(pat, line_lower):
            return "IMAGE_CAPTION"
            
    # 7. Generic Headers / Footers (e.g. typical top/bottom margins)
    # If the line is short, capitalized, and looks like a footer/header
    if len(clean_line) < 30 and (clean_line.isupper() or line_lower.startswith("copy right") or "all rights reserved" in line_lower):
        if any(term in line_lower for term in ["test", "quiz", "assignment", "syllabus"]):
            return "HEADER"
        return "FOOTER"
        
    # Default: useful academic text
    return "ACADEMIC_CONTENT"

def clean_ocr_text(raw_text: str, ocr_confidence: float = 1.0) -> Tuple[str, List[Dict[str, Any]], Dict[str, int]]:
    """
    Classifies OCR text line-by-line, filters out non-academic blocks,
    and returns: (cleaned_text, classified_blocks, statistics).
    """
    logger.info("Cleaning and classifying OCR text (confidence: %.2f)", ocr_confidence)
    lines = raw_text.splitlines()
    classified_blocks = []
    retained_lines = []
    
    stats = {
        "EMPTY": 0,
        "HEADER": 0,
        "FOOTER": 0,
        "METADATA": 0,
        "ACADEMIC_HEADING": 0,
        "ACADEMIC_CONTENT": 0,
        "QUESTION": 0,
        "TABLE": 0,
        "IMAGE_CAPTION": 0,
        "PAGE_MARKER": 0
    }
    
    for line in lines:
        category = classify_line(line)
        stats[category] = stats.get(category, 0) + 1
        
        block_info = {
            "text": line,
            "category": category,
            "length": len(line)
        }
        classified_blocks.append(block_info)
        
        # Retain educational/academic content, headings, tables, questions, and page markers
        if category in ("ACADEMIC_CONTENT", "ACADEMIC_HEADING", "TABLE", "QUESTION", "PAGE_MARKER"):
            retained_lines.append(line.strip())
            
    # Correctly merge paragraphs split across page breaks
    cleaned_paragraphs = []
    current_para = []
    
    for line in retained_lines:
        if not line:
            if current_para:
                cleaned_paragraphs.append(" ".join(current_para))
                current_para = []
            continue
            
        if re.match(r"^---\s*PAGE\s*\d+\s*---$", line):
            if current_para:
                cleaned_paragraphs.append(" ".join(current_para))
                current_para = []
            cleaned_paragraphs.append(line)
            continue
            
        # Check if line continues previous sentence (e.g., lowercase first char, no ending punctuation)
        if current_para and (line[0].islower() or not current_para[-1].endswith((".", "!", "?", "।"))):
            # If line is not a new heading, question or table row, merge it
            if not any(re.search(pat, line.lower()) for pat in HEADING_PATTERNS) and not any(re.match(pat, line) for pat in QUESTION_PATTERNS) and "|" not in line:
                current_para.append(line)
                continue
                
        if current_para:
            cleaned_paragraphs.append(" ".join(current_para))
            current_para = []
        current_para.append(line)
        
    if current_para:
        cleaned_paragraphs.append(" ".join(current_para))
        
    cleaned_text = "\n\n".join(cleaned_paragraphs)
    
    return cleaned_text, classified_blocks, stats


