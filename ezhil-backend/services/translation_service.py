import logging
import re
from typing import Dict, List, Tuple

logger = logging.getLogger(__name__)

# Cache for NLLB tokenizer and model
_tokenizer = None
_model = None
_initialized = False

# Technical terms lookups and standard bilingual formats
TECHNICAL_TERMS = {
    "machine learning": "இயந்திரக் கற்றல் (Machine Learning)",
    "machine-learning": "இயந்திரக் கற்றல் (Machine Learning)",
    "neural networks": "நரம்பியல் வலைப்பின்னல்கள் (Neural Networks)",
    "neural network": "நரம்பியல் வலைப்பின்னல் (Neural Network)",
    "deep learning": "ஆழ்ந்த கற்றல் (Deep Learning)",
    "data analysis": "தரவு பகுப்பாய்வு (Data Analysis)",
    "artificial intelligence": "செயற்கை நுண்ணறிவு (Artificial Intelligence)",
    "cloud computing": "மேகக்கணிமை (Cloud Computing)",
    "database": "தரவுத்தளம் (Database)",
    "algorithm": "நெறிமுறை (Algorithm)",
    "model": "மாதிரி (Model)",
    "network": "வலைப்பின்னல் (Network)",
    "data": "தரவு (Data)",
}

def init_translation_pipeline():
    """Lazy initialize the NLLB-200 tokenizer and model directly.

    Loading pulls ~2.4 GB from HuggingFace on a cold cache. Because this runs
    inside a /studio/generate request, an unguarded load turns a "deterministic
    offline fallback" into a minutes-long network call. DEMO_MODE and
    TRANSLATION_ENABLED both gate it; either way the dictionary fallback keeps
    bilingual glosses working.
    """
    global _tokenizer, _model, _initialized
    if _initialized:
        return

    _initialized = True

    from config import get_settings
    cfg = get_settings()
    if cfg.DEMO_MODE or not cfg.TRANSLATION_ENABLED:
        logger.info(
            "NLLB disabled (DEMO_MODE=%s, TRANSLATION_ENABLED=%s) — using dictionary fallback",
            cfg.DEMO_MODE, cfg.TRANSLATION_ENABLED,
        )
        return

    try:
        logger.info("Initializing HuggingFace NLLB model & tokenizer...")
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        model_name = "facebook/nllb-200-distilled-600M"
        local_only = cfg.HF_LOCAL_FILES_ONLY
        _tokenizer = AutoTokenizer.from_pretrained(model_name, local_files_only=local_only)
        _model = AutoModelForSeq2SeqLM.from_pretrained(model_name, local_files_only=local_only)

        # Move to GPU if available
        if torch.cuda.is_available():
            _model = _model.to("cuda")
            
        logger.info("NLLB translation model & tokenizer initialized successfully.")
    except Exception as e:
        logger.warning("Failed to initialize NLLB: %s. Using dictionary fallback.", e)

def preserve_tech_terms_en_to_ta(english_text: str, tamil_text: str) -> str:
    """Post-process Tamil translation to inject preserved bilingual technical terms."""
    eng_lower = english_text.lower()
    tam_res = tamil_text
    
    # Common variations of Tamil translations to search in the generated Tamil text
    mappings_to_search = {
        "machine learning": ["இயந்திரக் கற்றல்", "இயந்திர கற்றல்", "இயந்திரவழிக் கற்றல்", "மெஷின் லேர்னிங்"],
        "machine-learning": ["இயந்திரக் கற்றல்", "இயந்திர கற்றல்", "இயந்திரவழிக் கற்றல்", "மெஷின் லேர்னிங்"],
        "neural networks": ["நரம்பியல் வலைப்பின்னல்கள்", "நரம்பியல் நெட்வொர்க்குகள்", "நரம்பியல் வலைகள்"],
        "neural network": ["நரம்பியல் வலைப்பின்னல்", "நரம்பியல் நெட்வொர்க்", "நரம்பியல் வலை"],
        "deep learning": ["ஆழ்ந்த கற்றல்", "ஆழமான கற்றல்", "டீப் லேர்னிங்"],
        "data analysis": ["தரவு பகுப்பாய்வு", "தரவு ஆய்வு", "தரவு பகுப்பாய்வு முறை"],
        "artificial intelligence": ["செயற்கை நுண்ணறிவு", "ஆர்டிபிஷியல் இன்டெலிஜென்ஸ்"],
        "cloud computing": ["மேகக்கணிமை", "கிளவுட் கம்ப்யூட்டிங்", "மேகக் கணினி"],
        "database": ["தரவுத்தளம்", "டேட்டாபேஸ்"],
        "algorithm": ["நெறிமுறை", "அல்காரிதம்"],
        "model": ["மாதிரி", "வடிவமைப்பு", "மாடல்"],
        "network": ["வலைப்பின்னல்", "பிணையம்", "நெட்வொர்க்"],
        "data": ["தரவு", "தரவுகள்", "டேட்டா"],
    }
    
    for eng_term, tam_options in mappings_to_search.items():
        if eng_term in eng_lower:
            # Check if any Tamil option is in the translation
            found_option = None
            for opt in tam_options:
                if opt in tam_res:
                    found_option = opt
                    break
            
            bilingual_format = TECHNICAL_TERMS[eng_term]
            if found_option:
                if f"({eng_term.title()})" not in tam_res and f"({eng_term})" not in tam_res:
                    tam_res = tam_res.replace(found_option, bilingual_format, 1)
            else:
                # If NLLB left it untranslated, replace it directly
                pattern = re.compile(re.escape(eng_term), re.IGNORECASE)
                if pattern.search(tam_res):
                    tam_res = pattern.sub(bilingual_format, tam_res, 1)
                    
    return tam_res

def fallback_translate_en_to_ta(text: str) -> str:
    """Robust dictionary-based fallback for English to Tamil translation."""
    from services.slm_service import DEFAULT_TRANSLATIONS, DICT_MAPPINGS
    
    clean_text = text.strip()
    if clean_text in DEFAULT_TRANSLATIONS:
        # Check if the key matches english or tamil in default translations
        val = DEFAULT_TRANSLATIONS[clean_text]
        return val
        
    # Check keys case-insensitively
    for k, v in DEFAULT_TRANSLATIONS.items():
        if clean_text.lower() == k.lower():
            return v
        if clean_text.lower() == v.lower():
            return k
            
    # Word-by-word translation
    words = clean_text.split()
    translated_words = []
    for w in words:
        clean_w = w.strip(".,!?\"'()[]{}<>:;।").lower()
        found = False
        if clean_w in DICT_MAPPINGS:
            ta_word = re.findall(r'[\u0B80-\u0BFF]+', DICT_MAPPINGS[clean_w]["meaning_ta"])
            if ta_word:
                # Add punctuation back
                prefix = re.match(r"^[.,!?\"'()]+", w)
                suffix = re.search(r"[.,!?\"'()]+$", w)
                pfx = prefix.group(0) if prefix else ""
                sfx = suffix.group(0) if suffix else ""
                translated_words.append(pfx + ta_word[0] + sfx)
                found = True
        if not found:
            translated_words.append(w)
            
    return " ".join(translated_words)

def _tamil_headword(meaning_ta: str) -> str:
    """
    The Tamil term an entry defines, without its English gloss or description.

    Entries are written as "திட்டம் (Project) - ஒரு குறிப்பிட்ட இலக்கை …", so
    everything from the bracket or the dash onward is prose about the term
    rather than the term itself.
    """
    head = meaning_ta.split("(")[0]
    head = re.split(r"\s[-–—]\s", head)[0]
    return head.strip()


def _matches_tamil_gloss(word: str, meaning_ta: str) -> bool:
    """
    True when `word` IS the Tamil term this entry defines.

    Compares against the entry's headword rather than its whole description.
    The substring test this replaced matched any word occurring anywhere in the
    prose — "ஒரு" appears inside project's description, so every "ஒரு" in a
    lesson was rendered as "project".

    This is deliberately stricter than what it replaced, so inflected forms now
    fall through untranslated. A Tamil word left as Tamil is a visibly missing
    translation; a Tamil word confidently replaced by an unrelated English one
    reads as a real definition and is worse.
    """
    head = _tamil_headword(meaning_ta)
    if not head:
        return False
    return word == head or word in head.split()


def fallback_translate_ta_to_en(text: str) -> str:
    """Robust dictionary-based fallback for Tamil to English translation."""
    from services.slm_service import DEFAULT_TRANSLATIONS, DICT_MAPPINGS
    
    clean_text = text.strip()
    if clean_text in DEFAULT_TRANSLATIONS:
        val = DEFAULT_TRANSLATIONS[clean_text]
        return val
        
    # Check keys case-insensitively
    for k, v in DEFAULT_TRANSLATIONS.items():
        if clean_text.lower() == k.lower():
            return v
        if clean_text.lower() == v.lower():
            return k
            
    words = clean_text.split()
    translated_words = []
    for w in words:
        clean_w = w.strip(".,!?\"'()[]{}<>:;।-–—")
        found = False

        # A token that is nothing but punctuation must be left alone. It used
        # to reach the loop below as "", and `"" in anything` is True, so every
        # stray hyphen matched the first dictionary entry and was rendered as
        # its headword — this is where "machine" came from.
        if clean_w:
            for eng_word, info in DICT_MAPPINGS.items():
                if _matches_tamil_gloss(clean_w, info["meaning_ta"]):
                    prefix = re.match(r"^[.,!?\"'()]+", w)
                    suffix = re.search(r"[.,!?\"'()]+$", w)
                    pfx = prefix.group(0) if prefix else ""
                    sfx = suffix.group(0) if suffix else ""
                    translated_words.append(pfx + eng_word + sfx)
                    found = True
                    break
        if not found:
            translated_words.append(w)

    return " ".join(translated_words)

def translate_en_to_ta(text: str) -> str:
    """Public translation API from English to Tamil."""
    if not text.strip():
        return text
    init_translation_pipeline()
    if not _model or not _tokenizer:
        return fallback_translate_en_to_ta(text)
        
    try:
        import torch
        _tokenizer.src_lang = "eng_Latn"
        inputs = _tokenizer(text, return_tensors="pt")
        
        if torch.cuda.is_available():
            inputs = {k: v.to("cuda") for k, v in inputs.items()}
            
        target_lang = "tam_Taml"
        if hasattr(_tokenizer, "lang_code_to_id"):
            forced_bos_token_id = _tokenizer.lang_code_to_id[target_lang]
        else:
            forced_bos_token_id = _tokenizer.convert_tokens_to_ids(target_lang)
            
        with torch.no_grad():
            translated_tokens = _model.generate(
                **inputs,
                forced_bos_token_id=forced_bos_token_id,
                max_length=400
            )
            
        translated = _tokenizer.batch_decode(translated_tokens, skip_special_tokens=True)[0]
        return preserve_tech_terms_en_to_ta(text, translated)
    except Exception as e:
        logger.error("Error during NLLB neural translation (EN->TA): %s", e)
        return fallback_translate_en_to_ta(text)

def translate_ta_to_en(text: str) -> str:
    """Public translation API from Tamil to English."""
    if not text.strip():
        return text
    init_translation_pipeline()
    if not _model or not _tokenizer:
        return fallback_translate_ta_to_en(text)
        
    try:
        import torch
        _tokenizer.src_lang = "tam_Taml"
        inputs = _tokenizer(text, return_tensors="pt")
        
        if torch.cuda.is_available():
            inputs = {k: v.to("cuda") for k, v in inputs.items()}
            
        target_lang = "eng_Latn"
        if hasattr(_tokenizer, "lang_code_to_id"):
            forced_bos_token_id = _tokenizer.lang_code_to_id[target_lang]
        else:
            forced_bos_token_id = _tokenizer.convert_tokens_to_ids(target_lang)
            
        with torch.no_grad():
            translated_tokens = _model.generate(
                **inputs,
                forced_bos_token_id=forced_bos_token_id,
                max_length=400
            )
            
        translated = _tokenizer.batch_decode(translated_tokens, skip_special_tokens=True)[0]
        return translated
    except Exception as e:
        logger.error("Error during NLLB neural translation (TA->EN): %s", e)
        return fallback_translate_ta_to_en(text)
