# -*- coding: utf-8 -*-
"""
SLM Templates — Predefined High-Quality Bilingual Fallback Templates
===================================================================
Provides structured lessons, vocabulary lists, and quiz questions for
ML, CS, Science, and General topics when the SLM model is offline or
cannot generate valid grounded content.
"""

TEMPLATES = {
    "ml": {
        "title_ta": "இயந்திரக் கற்றல் அறிமுகம்",
        "lines_ta": [
            "கணினிகள் தானாகவே தரவுகளிலிருந்து கற்றுக்கொள்ளும் முறையே இயந்திரக் கற்றல் ஆகும்.",
            "மனிதர்கள் வழிகாட்டாமல் கணினிகள் புதிய கணிப்புகளை செய்ய இது உதவுகிறது.",
            "நிரலாக்கக் குறியீடுகள் எழுதப்படாமல் கணினிகள் தங்களைத் தாங்களே திருத்திக் கொள்ளும்.",
            "தரவு அதிகமாகும் போது கணினியின் கணிப்புகள் மிகவும் துல்லியமாக மாறும்.",
            "புகைப்படங்களை அடையாளம் காணவும் குரல்களைப் புரிந்து கொள்ளவும் இது பயன்படுகிறது.",
            "இணையத்தில் நாம் தேடும் தகவல்களை வரிசைப்படுத்த இயந்திரக் கற்றல் உதவுகிறது.",
            "நம்முடைய அன்றாட வாழ்வில் இந்த தொழில்நுட்பம் முக்கியப் பங்கு வகிக்கிறது.",
            "எதிர்காலத்தில் அனைத்து துறைகளிலும் இயந்திரக் கற்றல் இன்னும் பெரிய மாற்றங்களை ஏற்படுத்தும்."
        ],
        "vocab": [
            {"word_ta": "இயந்திரம்", "meaning_ta": "வேலையை எளிதாக்கும் கருவி", "meaning_en": "Machine", "context_ta": "கணினி ஒரு நவீன இயந்திரம்.", "example_ta": "கணினி ஒரு நவீன இயந்திரம் ஆகும்."},
            {"word_ta": "கற்றல்", "meaning_ta": "அறிவைப் பெறுதல்", "meaning_en": "Learning", "context_ta": "தொடர்ந்து கற்றல் நன்று.", "example_ta": "இயந்திரக் கற்றல் ஒரு புதிய அறிவியல் பிரிவு."},
            {"word_ta": "தரவு", "meaning_ta": "தகவல்களின் தொகுப்பு", "meaning_en": "Data", "context_ta": "கணினியில் தரவு சேமிக்கப்படுகிறது.", "example_ta": "அதிக தரவு இருந்தால் கணிப்பு துல்லியமாகும்."},
            {"word_ta": "துல்லியம்", "meaning_ta": "சரியான தன்மை", "meaning_en": "Accuracy", "context_ta": "கணிதத்தில் துல்லியம் முக்கியம்.", "example_ta": "மாதிரியின் துல்லியம் 95 சதவீதம் ஆகும்."},
            {"word_ta": "நிரலாக்கம்", "meaning_ta": "கணினிக் கட்டளை உருவாக்குதல்", "meaning_en": "Programming", "context_ta": "அவர் நிரலாக்கம் பயில்கிறார்.", "example_ta": "நிரலாக்கம் மூலம் புதிய செயலிகளை உருவாக்கலாம்."},
            {"word_ta": "கணிப்பு", "meaning_ta": "முன்கூட்டியே மதிப்பிடுதல்", "meaning_en": "Prediction", "context_ta": "வானிலை கணிப்பு மழையைக் காட்டுகிறது.", "example_ta": "இயந்திரக் கற்றல் துல்லியமான கணிப்புகளைச் செய்கிறது."},
            {"word_ta": "இணையம்", "meaning_ta": "உலகளாவிய கணினி வலை", "meaning_en": "Internet", "context_ta": "இணையம் பல தகவல்களைத் தருகிறது.", "example_ta": "இணையம் மூலம் உலகைத் தொடர்பு கொள்ளலாம்."},
            {"word_ta": "தொழில்நுட்பம்", "meaning_ta": "அறிவியல் நடைமுறைப் பயன்பாடு", "meaning_en": "Technology", "context_ta": "புதிய தொழில்நுட்பம் பயனுள்ளது.", "example_ta": "கணினித் தொழில்நுட்பம் வேகமாக வளர்ந்து வருகிறது."},
            {"word_ta": "மாற்றம்", "meaning_ta": "வேறுபட்ட நிலை", "meaning_en": "Change", "context_ta": "மாற்றம் இயற்கையானது.", "example_ta": "இயந்திரக் கற்றல் கல்வி முறையில் மாற்றத்தை ஏற்படுத்தும்."},
            {"word_ta": "எதிர்காலம்", "meaning_ta": "வரவிருக்கும் காலம்", "meaning_en": "Future", "context_ta": "நமது எதிர்காலம் சிறப்பாக இருக்கும்.", "example_ta": "செயற்கை நுண்ணறிவு எதிர்காலத்தை ஆளும்."},
            {"word_ta": "தகவல்", "meaning_ta": "செய்தி அல்லது அறிவு", "meaning_en": "Information", "context_ta": "நூலகத்தில் பல தகவல்கள் உள்ளன.", "example_ta": "இணையத்தில் பயனுள்ள தகவல்கள் நிறைந்துள்ளன."},
            {"word_ta": "வழிகாட்டி", "meaning_ta": "நெறிப்படுத்துபவர்", "meaning_en": "Guide / Mentor", "context_ta": "ஆசிரியர் ஒரு சிறந்த வழிகாட்டி.", "example_ta": "இயந்திரக் கற்றல் நமக்கு வழிகாட்டியாகச் செயல்படும்."}
        ],
        "quiz": [
            {
                "question_ta": "இயந்திரக் கற்றல் என்றால் என்ன?",
                "question_en": "What is machine learning?",
                "options_ta": [
                    "கணினிகள் தானாகவே தரவுகளிலிருந்து கற்றுக்கொள்ளும் முறை",
                    "கணினிகளை மனிதர்கள் மட்டுமே இயக்குவது",
                    "நிரலாக்கம் செய்யாமல் கணினியைப் பூட்டுவது",
                    "ஒரு சாதாரண கணினி விளையாட்டு"
                ],
                "options_en": [
                    "A method where computers learn automatically from data",
                    "Operating computers only by humans",
                    "Locking the computer without programming",
                    "A simple computer game"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "இயந்திரக் கற்றலில் கணிப்புகள் எப்போது துல்லியமாக மாறும்?",
                "question_en": "When do predictions become accurate in machine learning?",
                "options_ta": [
                    "தரவு அதிகமாகும் போது",
                    "கணினி அணைக்கப்படும் போது",
                    "நிரலாக்கம் நிறுத்தப்படும் போது",
                    "மின்சாரம் இல்லாத போது"
                ],
                "options_en": [
                    "When data increases",
                    "When the computer is turned off",
                    "When programming is stopped",
                    "When there is no electricity"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "பின்வருவனவற்றில் எதற்கு இயந்திரக் கற்றல் பயன்படுகிறது?",
                "question_en": "Which of the following uses machine learning?",
                "options_ta": [
                    "புகைப்படங்களை அடையாளம் காணவும் குரல்களைப் புரிந்து கொள்ளவும்",
                    "வீட்டைச் சுத்தம் செய்ய",
                    "புத்தகங்களை அச்சிட",
                    "காகிதம் தயாரிக்க"
                ],
                "options_en": [
                    "To identify photos and understand voices",
                    "To clean the house",
                    "To print books",
                    "To make paper"
                ],
                "correct_index": 0
            }
        ]
    },
    "cs": {
        "title_ta": "கணினி அமைப்புகள்",
        "lines_ta": [
            "கணினி என்பது தகவல்களைச் செயலாக்கும் ஒரு மின்னணு சாதனம் ஆகும்.",
            "இது வன்பொருள் மற்றும் மென்பொருள் ஆகிய இரு பகுதிகளைக் கொண்டது.",
            "மத்திய செயலாக்க அலகு அல்லது சிபியு கணினியின் மூளையாகச் செயல்படுகிறது.",
            "தரவுத்தளம் என்பது தகவல்களை ஒழுங்கமைத்துச் சேமிக்கும் இடமாகும்.",
            "கணினி வலைப்பின்னல் பல கணினிகளை இணைத்துத் தகவல்களைப் பகிர்கிறது.",
            "தரவுகளின் பாதுகாப்பை உறுதி செய்ய பாதுகாப்பு மென்பொருள் அவசியம்.",
            "வழங்கி அல்லது சர்வர் என்பது பிற கணினிகளுக்குச் சேவைகளை வழங்குகிறது.",
            "மென்பொருள் உருவாக்குநர்கள் புதிய நிரல்களை எழுதி கணினியை இயக்குகிறார்கள்."
        ],
        "vocab": [
            {"word_ta": "மின்னணு", "meaning_ta": "மின்சக்தியால் இயங்கும் முறை", "meaning_en": "Electronic", "context_ta": "கணினி ஒரு மின்னணு சாதனம்.", "example_ta": "மின்னணு சாதனங்கள் நமது வேலையை வேகமாக்குகின்றன."},
            {"word_ta": "வன்பொருள்", "meaning_ta": "கணினியின் தொட்டுணரக்கூடிய பாகங்கள்", "meaning_en": "Hardware", "context_ta": "விசைப்பலகை ஒரு வன்பொருள்.", "example_ta": "வன்பொருள் இல்லாமல் கணினியை இயக்க முடியாது."},
            {"word_ta": "மென்பொருள்", "meaning_ta": "கணினி இயக்கும் கட்டளைத் தொகுப்பு", "meaning_en": "Software", "context_ta": "இயக்க முறைமை ஒரு மென்பொருள்.", "example_ta": "மென்பொருள் வன்பொருளைக் கட்டுப்படுத்துகிறது."},
            {"word_ta": "செயலாக்கம்", "meaning_ta": "விவரங்களைச் சீரமைக்கும் பணி", "meaning_en": "Processing", "context_ta": "சிபியு தரவுகளைச் செயலாக்குகிறது.", "example_ta": "செயலாக்கம் முடிந்ததும் விடை திரையில் தோன்றும்."},
            {"word_ta": "மூளை", "meaning_ta": "சிந்தனை செய்யும் முதன்மைப் பகுதி", "meaning_en": "Brain", "context_ta": "சிபியு கணினியின் மூளையாகும்.", "example_ta": "மனித மூளையைப் போன்றது கணினியின் சிபியு."},
            {"word_ta": "தரவுத்தளம்", "meaning_ta": "தரவு சேமிப்பு முறை", "meaning_en": "Database", "context_ta": "பள்ளித் தரவுத்தளத்தில் மாணவர்கள் விவரங்கள் உள்ளன.", "example_ta": "தரவுத்தளம் தரவுகளைப் பாதுகாப்பாகச் சேமிக்கிறது."},
            {"word_ta": "வலைப்பின்னல்", "meaning_ta": "இணைக்கப்பட்ட அமைப்பு", "meaning_en": "Network", "context_ta": "எங்கள் பள்ளியில் கணினி வலைப்பின்னல் உள்ளது.", "example_ta": "வலைப்பின்னல் மூலம் கோப்புகளைப் பகிரலாம்."},
            {"word_ta": "பாதுகாப்பு", "meaning_ta": "ஆபத்து இல்லாத நிலை", "meaning_en": "Security", "context_ta": "கணினிக்கு கடவுச்சொல் பாதுகாப்பு அளிக்கிறது.", "example_ta": "இணையப் பாதுகாப்பு மிக முக்கியமான ஒன்றாகும்."},
            {"word_ta": "வழங்கி", "meaning_ta": "சேவைகளை வழங்கும் கணினி", "meaning_en": "Server", "context_ta": "இணையதளங்கள் வழங்கியில் சேமிக்கப்படுகின்றன.", "example_ta": "வழங்கி எப்போதும் இயங்கிக் கொண்டிருக்க வேண்டும்."},
            {"word_ta": "நிரல்", "meaning_ta": "கணினி வழிகாட்டும் குறிமுறை", "meaning_en": "Program", "context_ta": "அவர் ஒரு புதிய கணினி நிரலை எழுதினார்.", "example_ta": "கணினி நிரல் சரியாக இருந்தால் மட்டுமே இயங்கும்."},
            {"word_ta": "சேவை", "meaning_ta": "உதவி அல்லது பணி", "meaning_en": "Service", "context_ta": "வலைச் சேவை மிக வேகமாக உள்ளது.", "example_ta": "வழங்கி பல்வேறு சேவைகளை வழங்குகிறது."},
            {"word_ta": "ஒழுங்கமைப்பு", "meaning_ta": "முறையாக வரிசைப்படுத்துதல்", "meaning_en": "Organization", "context_ta": "கோப்புகளின் ஒழுங்கமைப்பு நன்று.", "example_ta": "ஒழுங்கமைப்பு வேலைகளை எளிதாக்குகிறது."}
        ],
        "quiz": [
            {
                "question_ta": "கணினியின் மூளையாகச் செயல்படுவது எது?",
                "question_en": "What acts as the brain of the computer?",
                "options_ta": [
                    "மத்திய செயலாக்க அலகு (CPU)",
                    "விசைப்பலகை (Keyboard)",
                    "சுட்டி (Mouse)",
                    "அச்சுப்பொறி (Printer)"
                ],
                "options_en": [
                    "Central Processing Unit (CPU)",
                    "Keyboard",
                    "Mouse",
                    "Printer"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "வன்பொருளுக்கும் மென்பொருளுக்கும் என்ன தொடர்பு?",
                "question_en": "What is the relationship between hardware and software?",
                "options_ta": [
                    "மென்பொருள் வன்பொருளைக் கட்டுப்படுத்தி இயக்குகிறது",
                    "இரண்டுக்கும் எந்தத் தொடர்பும் இல்லை",
                    "வன்பொருள் என்பது ஒரு கணினி விளையாட்டு",
                    "மென்பொருள் என்பது ஒரு கணினித் திரை"
                ],
                "options_en": [
                    "Software controls and runs the hardware",
                    "There is no connection between the two",
                    "Hardware is a computer game",
                    "Software is a computer screen"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "தகவல்களை ஒழுங்கமைத்துச் சேமிக்கும் இடம் எது?",
                "question_en": "Where is information organized and stored?",
                "options_ta": [
                    "தரவுத்தளம் (Database)",
                    "சுட்டி (Mouse)",
                    "வன்பொருள் (Hardware)",
                    "மின்கம்பி (Power cable)"
                ],
                "options_en": [
                    "Database",
                    "Mouse",
                    "Hardware",
                    "Power cable"
                ],
                "correct_index": 0
            }
        ]
    },
    "science": {
        "title_ta": "அறிவியல் ஆராய்ச்சி",
        "lines_ta": [
            "அறிவியல் என்பது உலகத்தைப் பற்றிய முறையான அறிவைப் பெறுவதாகும்.",
            "ஆராய்ச்சி என்பது புதிய உண்மைகளைக் கண்டறியும் ஒரு செயல்முறை ஆகும்.",
            "ஆராய்ச்சியாளர்கள் சோதனைகள் மூலம் தங்கள் கோட்பாடுகளைச் சரிபார்க்கிறார்கள்.",
            "ஒவ்வொரு பரிசோதனைக்கும் துல்லியமான தரவு பகுப்பாய்வு தேவைப்படுகிறது.",
            "கண்டுபிடிப்புகளின் முடிவுகள் எதிர்கால அறிவியல் வளர்ச்சிக்கு உதவும்.",
            "கணித சமன்பாடுகள் அறிவியல் விதிகளை விளக்கப் பயன்படுகின்றன.",
            "அறிவியல் சோதனைகள் ஆய்வகங்களில் பாதுகாப்பான முறையில் நடத்தப்படுகின்றன.",
            "புதிய கண்டுபிடிப்புகள் மனித சமூகத்தின் முன்னேற்றத்திற்கு வழிவகுக்கும்."
        ],
        "vocab": [
            {"word_ta": "அறிவியல்", "meaning_ta": "முறையான உலகியல் அறிவு", "meaning_en": "Science", "context_ta": "அறிவியல் பல உண்மைகளை விளக்குகிறது.", "example_ta": "அறிவியல் பாடம் மிகவும் சுவாரஸ்யமானது."},
            {"word_ta": "ஆராய்ச்சி", "meaning_ta": "புதிய அறிவைத் தேடும் பணி", "meaning_en": "Research", "context_ta": "அவர் புற்றுநோய் ஆராய்ச்சி செய்கிறார்.", "example_ta": "ஆராய்ச்சி மூலம் புதிய மருந்து கண்டறியப்பட்டது."},
            {"word_ta": "சோதனை", "meaning_ta": "செய்து பார்த்து அறிதல்", "meaning_en": "Experiment", "context_ta": "ஆய்வகத்தில் ஒரு சோதனை நடக்கிறது.", "example_ta": "அறிவியல் சோதனைகள் பொறுமையாக செய்யப்பட வேண்டும்."},
            {"word_ta": "கோட்பாடு", "meaning_ta": "அறிவியல் கொள்கை அல்லது விதி", "meaning_en": "Theory", "context_ta": "ஐன்ஸ்டீனின் கோட்பாடு பிரபலமானது.", "example_ta": "இந்த சோதனையின் கோட்பாடு மிக எளிது."},
            {"word_ta": "பகுப்பாய்வு", "meaning_ta": "ஆராய்ந்து பிரித்துப் பார்த்தல்", "meaning_en": "Analysis", "context_ta": "விவரங்களின் பகுப்பாய்வு முடிந்தது.", "example_ta": "தரவு பகுப்பாய்வு துல்லியமாக இருக்க வேண்டும்."},
            {"word_ta": "முடிவு", "meaning_ta": "இறுதியாகக் கிடைக்கும் பலன்", "meaning_en": "Result", "context_ta": "தேர்வு முடிவுகள் இன்று வரும்.", "example_ta": "ஆராய்ச்சியின் முடிவு மகிழ்ச்சி அளிக்கிறது."},
            {"word_ta": "வளர்ச்சி", "meaning_ta": "முன்னேற்றம் அல்லது உயர்வு", "meaning_en": "Development", "context_ta": "குழந்தையின் வளர்ச்சி ஆரோக்கியமானது.", "example_ta": "அறிவியல் வளர்ச்சி பல நன்மைகளைத் தருகிறது."},
            {"word_ta": "சமன்பாடு", "meaning_ta": "இருபுறமும் சமமாக இருக்கும் கணித விதி", "meaning_en": "Equation", "context_ta": "இந்த சமன்பாடு மிகவும் கடினம்.", "example_ta": "கணித சமன்பாடுகள் அறிவியலுக்கு அடிப்படை."},
            {"word_ta": "விதி", "meaning_ta": "மாறாத அறிவியல் ஒழுங்கு", "meaning_en": "Law", "context_ta": "ஈர்ப்பு விதி மிக முக்கியமானது.", "example_ta": "இயற்கையின் விதிகள் மாறாதவை."},
            {"word_ta": "ஆய்வகம்", "meaning_ta": "சோதனைகள் செய்யும் அறை", "meaning_en": "Laboratory", "context_ta": "ஆய்வகத்தில் பல கருவிகள் உள்ளன.", "example_ta": "அறிவியல் ஆய்வகம் சுத்தமாக இருக்க வேண்டும்."},
            {"word_ta": "முன்னேற்றம்", "meaning_ta": "உயர்ந்த நிலைக்குச் செல்லுதல்", "meaning_en": "Progress", "context_ta": "நாடு வேகமாக முன்னேற்றம் அடைகிறது.", "example_ta": "கல்வி மனித முன்னேற்றத்திற்குத் தேவை."},
            {"word_ta": "சமூகம்", "meaning_ta": "மக்களின் கூட்டமைப்பு", "meaning_en": "Society", "context_ta": "நாம் சமூகத்தோடு வாழ வேண்டும்.", "example_ta": "அறிவியல் சமூகத்திற்கு உதவ வேண்டும்."}
        ],
        "quiz": [
            {
                "question_ta": "ஆராய்ச்சி என்பது என்ன?",
                "question_en": "What is research?",
                "options_ta": [
                    "புதிய உண்மைகளைக் கண்டறியும் ஒரு செயல்முறை",
                    "பழைய புத்தகங்களை நகலெடுப்பது",
                    "அமைதியாக தூங்குவது",
                    "ஒரு வகை விளையாட்டு"
                ],
                "options_en": [
                    "A process of discovering new facts",
                    "Copying old books",
                    "Sleeping quietly",
                    "A type of game"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "அறிவியல் சோதனைகள் எங்கு நடத்தப்படுகின்றன?",
                "question_en": "Where are scientific experiments conducted?",
                "options_ta": [
                    "ஆய்வகங்களில் (Laboratories)",
                    "பூங்காக்களில் (Parks)",
                    "சந்தைகளில் (Markets)",
                    "விளையாட்டு மைதானத்தில் (Playground)"
                ],
                "options_en": [
                    "In laboratories",
                    "In parks",
                    "In markets",
                    "In play grounds"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "புதிய கண்டுபிடிப்புகள் எதற்கு வழிவகுக்கும்?",
                "question_en": "What do new discoveries lead to?",
                "options_ta": [
                    "மனித சமூகத்தின் முன்னேற்றத்திற்கு",
                    "பொருட்களை வீணாக்க",
                    "காலத்தை விரயம் செய்ய",
                    "முன்னேற்றத்தைத் தடுக்க"
                ],
                "options_en": [
                    "To the progress of human society",
                    "To waste things",
                    "To waste time",
                    "To block progress"
                ],
                "correct_index": 0
            }
        ]
    },
    "general": {
        "title_ta": "யானையும் எறும்பும்",
        "lines_ta": [
            "ஒரு காட்டில் பலம் பொருந்திய ஒரு பெரிய யானை வாழ்ந்து வந்தது.",
            "அந்த யானை மிகவும் அகங்காரம் கொண்டு மற்ற விலங்குகளைத் துன்புறுத்தியது.",
            "அதே காட்டில் சுறுசுறுப்பான ஒரு சிறிய எறும்பு தன் கூட்டோடு வாழ்ந்தது.",
            "யானை எறும்பைப் பார்த்து அதன் சிறிய உடலை எள்ளி நகையாடியது.",
            "இதனால் வருத்தமுற்ற எறும்பு யானைக்கு ஒரு பாடம் புகட்ட நினைத்தது.",
            "ஒரு நாள் எறும்பு மெதுவாக யானையின் தும்பிக்கைக்குள் நுழைந்து கடித்தது.",
            "வலியால் துடித்த யானை தன் தவறை உணர்ந்து எறும்பிடம் மன்னிப்புக் கேட்டது.",
            "உருவத்தைக் கண்டு யாரையும் குறைத்து மதிப்பிடக் கூடாது என்பதை உணர்ந்தது."
        ],
        "vocab": [
            {"word_ta": "யானை", "meaning_ta": "மிகப் பெரிய நில விலங்கு", "meaning_en": "Elephant", "context_ta": "யானை கரும்பு தின்னும்.", "example_ta": "யானை காட்டில் வாழும் ஒரு பெரிய விலங்கு."},
            {"word_ta": "எறும்பு", "meaning_ta": "சிறிய சுறுசுறுப்பான பூச்சி", "meaning_en": "Ant", "context_ta": "எறும்பு வரிசையாகச் செல்லும்.", "example_ta": "எறும்பு சேமிப்புக்குச் சிறந்த உதாரணம்."},
            {"word_ta": "அகங்காரம்", "meaning_ta": "தலைக்கனம் அல்லது ஆணவம்", "meaning_en": "Pride / Ego", "context_ta": "அகங்காரம் மனிதனை அழிக்கும்.", "example_ta": "யானை தன் பலத்தால் அகங்காரம் கொண்டது."},
            {"word_ta": "விலங்கு", "meaning_ta": "காட்டுயிரி", "meaning_en": "Animal", "context_ta": "சிங்கம் காட்டின் விலங்கு.", "example_ta": "காட்டில் பல விலங்குகள் வாழ்கின்றன."},
            {"word_ta": "சுறுசுறுப்பு", "meaning_ta": "சோம்பலின்மை", "meaning_en": "Active / Hardworking", "context_ta": "எறும்பு சுறுசுறுப்பாக இருக்கும்.", "example_ta": "சுறுசுறுப்புடன் படித்தால் வெற்றி பெறலாம்."},
            {"word_ta": "உடல்", "meaning_ta": "மெய் அல்லது தேகம்", "meaning_en": "Body", "context_ta": "உடலைத் தூய்மையாக வை.", "example_ta": "எறும்பு சிறிய உடலைக் கொண்டது."},
            {"word_ta": "வருத்தம்", "meaning_ta": "துன்பம் அல்லது கவலை", "meaning_en": "Sadness / Regret", "context_ta": "அவர் முகத்தில் வருத்தம் தெரிந்தது.", "example_ta": "யானையின் பேச்சால் எறும்பு வருத்தம் அடைந்தது."},
            {"word_ta": "தும்பிக்கை", "meaning_ta": "யானையின் மூக்கு", "meaning_en": "Trunk", "context_ta": "யானை தும்பிக்கையால் நீர் குடிக்கும்.", "example_ta": "தும்பிக்கைக்குள் எறும்பு நுழைந்தது."},
            {"word_ta": "மன்னிப்பு", "meaning_ta": "பிழையைப் பொறுத்தல்", "meaning_en": "Forgiveness", "context_ta": "மன்னிப்பு கேட்பது நல்லது.", "example_ta": "யானை எறும்பிடம் மன்னிப்பு கேட்டது."},
            {"word_ta": "உருவம்", "meaning_ta": "தோற்றம்", "meaning_en": "Appearance / Form", "context_ta": "அதன் உருவம் அழகாக இருந்தது.", "example_ta": "உருவத்தைக் கண்டு யாரையும் எடை போடாதே."},
            {"word_ta": "காடு", "meaning_ta": "மரங்கள் நிறைந்த பகுதி", "meaning_en": "Forest", "context_ta": "காடு நமக்கு மழையைத் தரும்.", "example_ta": "யானையும் எறும்பும் காட்டில் வாழ்ந்தன."},
            {"word_ta": "வலி", "meaning_ta": "வேதனை", "meaning_en": "Pain", "context_ta": "அவளுக்குத் தலையில் வலி இருந்தது.", "example_ta": "கடியால் யானைக்குக் கடுமையான வலி ஏற்பட்டது."}
        ],
        "quiz": [
            {
                "question_ta": "யானை மற்ற விலங்குகளை ஏன் துன்புறுத்தியது?",
                "question_en": "Why did the elephant trouble other animals?",
                "options_ta": [
                    "அகங்காரம் கொண்டு தன் பலத்தால்",
                    "மற்ற விலங்குகள் அதைத் தாக்கியதால்",
                    "விளையாடுவதற்காக",
                    "காட்டை விட்டு ஓட வைக்க"
                ],
                "options_en": [
                    "Out of pride in its own strength",
                    "Because other animals attacked it",
                    "Just to play",
                    "To drive them out of the forest"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "எறும்பு யானைக்கு எங்கு கடித்தது?",
                "question_en": "Where did the ant bite the elephant?",
                "options_ta": [
                    "தும்பிக்கைக்குள் (Inside the trunk)",
                    "காலில் (On the leg)",
                    "வாலில் (On the tail)",
                    "காதில் (On the ear)"
                ],
                "options_en": [
                    "Inside the trunk",
                    "On the leg",
                    "On the tail",
                    "On the ear"
                ],
                "correct_index": 0
            },
            {
                "question_ta": "இந்தக் கதையின் நீதி என்ன?",
                "question_en": "What is the moral of this story?",
                "options_ta": [
                    "உருவத்தைக் கண்டு யாரையும் குறைத்து மதிப்பிடக் கூடாது",
                    "யானையே எப்போதுமே பலம் வாய்ந்தது",
                    "எறும்பு எப்போதும் தீமை செய்யும்",
                    "விலங்குகள் எப்போதும் சண்டையிடும்"
                ],
                "options_en": [
                    "Do not judge anyone by their appearance",
                    "The elephant is always the strongest",
                    "Ants always do bad things",
                    "Animals will always fight"
                ],
                "correct_index": 0
            }
        ]
    }
}
