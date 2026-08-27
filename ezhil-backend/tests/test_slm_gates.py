"""SLM quality-gate unit tests: JSON repair, PIN derivation, quiz grounding."""
from routers.auth import _dob_to_pin
from services.slm_service import _extract_json, _repair_brackets, validate_and_clean_quiz_item


def test_dob_to_pin_iso():
    assert _dob_to_pin("2016-05-12") == "0512"


def test_dob_to_pin_dmy():
    assert _dob_to_pin("12/05/2016") == "0512"


def test_dob_to_pin_absent():
    assert _dob_to_pin(None) is None
    assert _dob_to_pin("not a date") is None


def test_extract_json_plain():
    assert _extract_json('{"a": 1}') == {"a": 1}


def test_extract_json_with_markdown_fences():
    assert _extract_json('```json\n{"a": 1}\n```') == {"a": 1}


def test_extract_json_repairs_transposed_brackets():
    # The exact failure mode Gemma produced: `"…"]}` where `"…"}]` is required.
    raw = '{"vocabulary": [{"word": "மகிழ்ச்சி", "audio_hint": "ம-கிழ்-சி"]}, "title": "x"}'
    parsed = _extract_json(raw)
    assert parsed is not None
    assert parsed["vocabulary"][0]["word"] == "மகிழ்ச்சி"


def test_repair_brackets_ignores_braces_inside_strings():
    s = '{"text": "a ] weird } string", "n": 1}'
    assert _repair_brackets(s) == s


def test_quiz_item_grounds_source_language_only():
    # English translations legitimately contain words absent from a Tamil
    # source; grounding must check the Tamil option only.
    source = "விவசாயி தன் உழைப்பால் நல்ல பலன் பெற்றார்."
    item = {
        "question_ta": "விவசாயி எதன் மூலம் பலன் பெற்றார்?",
        "question_en": "How did the farmer succeed?",
        "options_ta": ["செல்வம்", "உழைப்பு"],
        "options_en": ["Wealth", "Hard work"],
        "correct_index": 1,
    }
    # "உழைப்பு" appears inflected as "உழைப்பால்" — stem matching must accept it.
    assert validate_and_clean_quiz_item(item, source) is True


def test_quiz_item_rejects_ungrounded_answer():
    source = "விவசாயி தன் உழைப்பால் நல்ல பலன் பெற்றார்."
    item = {
        "question_ta": "எது சிறந்தது?",
        "question_en": "Which is best?",
        "options_ta": ["விண்கலம்", "கணினித்திரை"],  # neither in source
        "options_en": ["Spaceship", "Monitor"],
        "correct_index": 0,
    }
    assert validate_and_clean_quiz_item(item, source) is False
