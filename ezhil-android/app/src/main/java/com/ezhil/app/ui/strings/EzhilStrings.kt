package com.ezhil.app.ui.strings

enum class AppLanguage { TAMIL, ENGLISH }

enum class StringKey {
    TAGLINE, STUDENT_ROLE, TEACHER_ROLE, TEACHER_LOGIN_TITLE, SCHOOL_CODE_LABEL,
    TEACHER_ID_LABEL, LOGIN_BUTTON, LOGIN_ERROR, WHO_ARE_YOU, TAP_YOUR_NAME,
    GREETING, READ_ALOUD, WORD_GAMES, MY_LESSONS, MY_JOURNEY,
    ASSESS_START, ASSESS_LISTENING, ASSESS_COMPLETE,
    CORRECT_FEEDBACK, WRONG_FEEDBACK, LESSON_COMPLETE, HEAR_PRONUNCIATION,
    RISK_HIGH, RISK_MEDIUM, RISK_LOW, UPLOAD_PROMPT, PUBLISH_BUTTON,
    SYNC_PENDING, SYNC_IN_PROGRESS, SYNC_ERROR, SYNC_NOW, OFFLINE_MODE,
    ADD_STUDENT, SAVE, STUDENT_NAME, DATE_OF_BIRTH, DASHBOARD, STUDIO, ROSTER,
    APPROVE_PUBLISH, SAVE_DRAFT, LESSON_STUDIO, REVIEW_LESSON, TAKE_PHOTO, GALLERY,
    SELECT_DIFFICULTY, SELECT_LANGUAGE, PROCESSING, CACHE_HIT_BANNER,
    STUDENT_STREAK, NO_ASSESSMENTS, NO_STUDENTS_TEACHER, NO_LESSONS_STUDENT,
    STUDENTS_COUNT, BACK, RETRY, DONE,
    ONBOARDING_1_TITLE, ONBOARDING_1_DESC,
    ONBOARDING_2_TITLE, ONBOARDING_2_DESC,
    ONBOARDING_3_TITLE, ONBOARDING_3_DESC,
    GET_STARTED, NEXT, SKIP,
    NEW_STUDENT_TITLE, OFFLINE_LOGIN_HINT,
    EMPTY_STATE_TITLE, EMPTY_STATE_DESC,
    // Risk
    RISK_UNSCREENED,
    // Assessment
    ASSESS_ANALYSING, ASSESS_RESULT_TITLE, ASSESS_TRY_AGAIN, ASSESS_MIC_NEEDED,
    ASSESS_MIC_DESC, ASSESS_OPEN_SETTINGS, ASSESS_NOT_NOW,
    ASSESS_STOP_CONFIRM, ASSESS_KEEP_RECORDING, ASSESS_DISCARD,
    ASSESS_INSTRUCTIONS_TITLE, ASSESS_HOLD_PHONE, ASSESS_LOOK_SCREEN, ASSESS_READ_CLEARLY,
    // Games
    GAMES_TITLE, GAMES_MATCH_SOUND, GAMES_SPOT_LETTER, GAMES_BUILD_WORD,
    GAMES_ROUND, GAMES_NEXT_ROUND, GAMES_RETAKE, GAMES_CHECK,
    GAMES_FIND_LETTER, GAMES_LISTEN_TO_SOUND, GAMES_BUILD_WORD_INSTRUCTION,
    GAMES_ROUND_COMPLETE, GAMES_ACCURACY, GAMES_BONUS_XP,
    // Achievements
    ACHIEVE_SUPER, ACHIEVE_WELL_DONE, ACHIEVE_READER, ACHIEVE_STREAK,
    ACHIEVE_LANDMARK, ACHIEVE_CLAIM, ACHIEVE_GO_HOME, ACHIEVE_PLAY_AGAIN,
    ACHIEVE_NEW_BADGE, ACHIEVE_SHARE,
    // My Assessments
    MY_ASSESSMENTS_TITLE, MY_ASSESSMENTS_TOTAL, MY_ASSESSMENTS_AVG, MY_ASSESSMENTS_SUCCESS,
    MY_ASSESSMENTS_RECENT, MY_ASSESSMENTS_EMPTY,
    // Teacher extras
    TEACHER_STUDENTS, TEACHER_NEED_ATTENTION, TEACHER_LESSONS, TEACHER_PROGRESS,
    TEACHER_CLASS_STATUS, TEACHER_STABLE, TEACHER_CAUTION, TEACHER_AT_RISK,
    TEACHER_VIEW_ALL, TEACHER_REVIEW, TEACHER_REFER_SUPPORT,
    TEACHER_RUN_ASSESSMENT, TEACHER_NO_NETWORK, TEACHER_RETRY,
    TEACHER_LESSON_LIBRARY, TEACHER_NEW_LESSON, TEACHER_REPORTS,
    TEACHER_FLUENCY_RATE, TEACHER_AVG_READING,
    // Lesson player
    EXIT_LESSON_TITLE, EXIT_LESSON_DESC, EXIT_LESSON_KEEP, EXIT_LESSON_EXIT,
    // Difficulty
    EASY, MEDIUM, HARD,
    // Splash
    SPLASH_LOADING,
    // Role selection
    ROLE_STUDENT_DESC, ROLE_TEACHER_DESC, ROLE_TITLE_HINT,
    // Dashboard/Detail
    STREAK_DAYS,
    // Generic
    CANCEL,
    // Stats
    STREAK, LESSONS, STARS, JOURNEY_MAP, MILESTONE_1, MILESTONE_5, MILESTONE_10, MILESTONE_20, MILESTONE_50,
    CHAMPION, LEVEL,
    // Student Profile
    PROFILE_TAP_HINT, PROFILE_NEW_HINT, SYNC_STATUS_ALL, CONTINUE,
    // Dashboard
    DASHBOARD_TITLE, YOUR_CLASSROOM, STREAK_LABEL, RISK_NONE,
    // Student Home
    HELLO, LEARN_HINT, DAILY_GOAL, NEW_STORY, START, ACTIVITIES,    RECENT_ACTIVITY,
    // Read Aloud
    READ_ALOUD_TITLE, READ_ALOUD_SUBTITLE, INSTRUCTIONS_TITLE, INSTRUCTIONS_HINT,
    INST_HOLD, INST_SCREEN, INST_READ, REC_STATUS, STOP_RECORDING, STOP_TITLE, STOP_SUBTITLE,
    STOP_DESC, STOP_HINT, KEEP_REC, DISCARD_REC, ANALYSING_DESC, STATUS_TITLE, STEP_TITLE,
    PRIVACY_HINT, TRY_AGAIN_TITLE, TRY_AGAIN_SUB, RECORDING_QUIET, MIC_CLEAR, GO_HOME,
    ASSESS_COMPLETE_DESC, MIC_TITLE, MIC_SUB, MIC_HINT, SECURITY_TITLE, SECURITY_DESC,
    // Lessons
    COMPLETED, ALL_SYNCED, LESSON_TAB_ALL, LESSON_TAB_STORY, LESSON_TAB_VOCAB, LESSON_TAB_QUIZ, LESSON_TAB_LISTEN,
    // Roster
    SEARCH_HINT, NO_STUDENTS_FOUND,
    LAST_7_DAYS, ASSESS_HISTORY
}

object EzhilStrings {
    data class BilingualString(val tamil: String, val english: String)

    fun get(key: StringKey, language: AppLanguage): String {
        val s = map[key] ?: return key.name
        return if (language == AppLanguage.TAMIL) s.tamil else s.english
    }

    private val map = mapOf(
        StringKey.TAGLINE              to BilingualString("எழு. படி. ஒளிர்.", "Rise. Read. Shine."),
        StringKey.STUDENT_ROLE         to BilingualString("மாணவர்", "Student"),
        StringKey.TEACHER_ROLE         to BilingualString("ஆசிரியர்", "Teacher"),
        StringKey.TEACHER_LOGIN_TITLE  to BilingualString("ஆசிரியர் உள்நுழைவு", "Teacher Login"),
        StringKey.SCHOOL_CODE_LABEL    to BilingualString("பள்ளி குறியீடு", "SCHOOL CODE"),
        StringKey.TEACHER_ID_LABEL     to BilingualString("ஆசிரியர் அடையாள எண்", "TEACHER ID"),
        StringKey.LOGIN_BUTTON         to BilingualString("உள்நுழை", "Log In"),
        StringKey.LOGIN_ERROR          to BilingualString("பள்ளி குறியீடு அல்லது ஆசிரியர் எண் தவறானது.", "School code or Teacher ID not found."),
        StringKey.WHO_ARE_YOU          to BilingualString("யார் நீ?", "Who are you?"),
        StringKey.TAP_YOUR_NAME        to BilingualString("உங்கள் பெயரை தொடுக", "Tap your name below"),
        StringKey.GREETING             to BilingualString("வணக்கம்", "Hello"),
        StringKey.READ_ALOUD           to BilingualString("படிக்கலாம்", "Read Aloud"),
        StringKey.WORD_GAMES           to BilingualString("விளையாடலாம்", "Word Games"),
        StringKey.MY_LESSONS           to BilingualString("என் பாடங்கள்", "My Lessons"),
        StringKey.MY_JOURNEY           to BilingualString("என் பயணம்", "My Journey"),
        StringKey.ASSESS_START         to BilingualString("படிக்க ஆரம்பிக்கலாம்!", "Let's start reading!"),
        StringKey.ASSESS_LISTENING     to BilingualString("நான் கேட்கிறேன்…", "I'm listening…"),
        StringKey.ASSESS_COMPLETE      to BilingualString("சூப்பர்! நீ செய்தாய்!", "Super! You did it!"),
        StringKey.CORRECT_FEEDBACK     to BilingualString("சூப்பர்! நீ செய்தாய்!", "Super! You did it!"),
        StringKey.WRONG_FEEDBACK       to BilingualString("பரவாயில்லை. மீண்டும் முயற்சிக்கலாம்.", "No worries. Try again!"),
        StringKey.LESSON_COMPLETE      to BilingualString("அருமை! நீ ஒரு வாசகன்!", "Amazing! You're a reader!"),
        StringKey.HEAR_PRONUNCIATION   to BilingualString("உச்சரிப்பு கேளு", "Hear pronunciation"),
        StringKey.RISK_HIGH            to BilingualString("அதிக கவனம்", "High Risk"),
        StringKey.RISK_MEDIUM          to BilingualString("நடுத்தரம்", "Medium Risk"),
        StringKey.RISK_LOW             to BilingualString("இயல்பு", "Low Risk"),
        StringKey.UPLOAD_PROMPT        to BilingualString("பாட புத்தக பக்கத்தை பதிவேற்றுங்கள்", "Upload a textbook page"),
        StringKey.PUBLISH_BUTTON       to BilingualString("அனுமதிக்கவும் & வெளியிடவும்", "Approve & Publish"),
        StringKey.SYNC_PENDING         to BilingualString("காத்திருக்கும் records", "records pending sync"),
        StringKey.SYNC_IN_PROGRESS     to BilingualString("ஒத்திசைக்கிறது...", "Syncing…"),
        StringKey.SYNC_ERROR           to BilingualString("ஒத்திசை தோல்வி — இணைய இணைப்பைச் சரிபார்க்கவும்", "Sync failed — check connection"),
        StringKey.SYNC_NOW             to BilingualString("இப்போது ஒத்திசை", "Sync Now"),
        StringKey.OFFLINE_MODE         to BilingualString("Offline — locally saved", "Offline — saved locally"),
        StringKey.ADD_STUDENT          to BilingualString("மாணவர் சேர்", "Add Student"),
        StringKey.SAVE                 to BilingualString("சேமி", "Save"),
        StringKey.STUDENT_NAME        to BilingualString("மாணவர் பெயர்", "Student Name"),
        StringKey.DATE_OF_BIRTH        to BilingualString("பிறந்த நாள் (விரும்பினால்)", "Date of Birth (optional)"),
        StringKey.DASHBOARD            to BilingualString("டாஷ்போர்டு", "Dashboard"),
        StringKey.STUDIO               to BilingualString("ஸ்டூடியோ", "Studio"),
        StringKey.ROSTER               to BilingualString("பட்டியல்", "Roster"),
        StringKey.APPROVE_PUBLISH      to BilingualString("அனுமதிக்கவும் & வெளியிடவும்", "Approve & Publish"),
        StringKey.SAVE_DRAFT           to BilingualString("வரைவாக சேமி", "Save Draft"),
        StringKey.LESSON_STUDIO        to BilingualString("பாட ஸ்டூடியோ", "Lesson Studio"),
        StringKey.REVIEW_LESSON        to BilingualString("பாட மதிப்பாய்வு", "Review Lesson"),
        StringKey.TAKE_PHOTO           to BilingualString("📷 புகைப்படம்", "📷 Camera"),
        StringKey.GALLERY              to BilingualString("🖼 கேலரி", "🖼 Gallery"),
        StringKey.SELECT_DIFFICULTY    to BilingualString("சிரமம் தேர்வு", "Select Difficulty"),
        StringKey.SELECT_LANGUAGE      to BilingualString("மொழி தேர்வு", "Select Language"),
        StringKey.PROCESSING           to BilingualString("செயலாக்கம்...", "Processing..."),
        StringKey.CACHE_HIT_BANNER     to BilingualString("இந்த பக்கம் ஏற்கனவே உள்ளது! ✨", "This page is already in the system! ✨"),
        StringKey.STUDENT_STREAK       to BilingualString("தொடர் நாட்கள்", "Streak"),
        StringKey.NO_ASSESSMENTS       to BilingualString("இன்னும் மதிப்பீடுகள் இல்லை", "No assessments yet"),
        StringKey.NO_STUDENTS_TEACHER  to BilingualString("மாணவர்கள் யாரும் இல்லை. ஆசிரியரிடம் கேளுங்கள்.", "No students yet. Ask your teacher to add you."),
        StringKey.NO_LESSONS_STUDENT   to BilingualString("ஆசிரியர் பாடங்களை அனுப்பவில்லை. சிறிது நேரம் பொறு!", "No lessons from teacher yet. Wait a bit!"),
        StringKey.STUDENTS_COUNT       to BilingualString("மாணவர்கள்", "students"),
        StringKey.BACK                 to BilingualString("திரும்பு", "Back"),
        StringKey.RETRY                to BilingualString("மீண்டும் முயற்சி", "Retry"),
        StringKey.DONE                 to BilingualString("முடித்தேன்", "Done"),
        StringKey.ONBOARDING_1_TITLE   to BilingualString("எழிலுடன் படியுங்கள்!", "Learn with Ezhil!"),
        StringKey.ONBOARDING_1_DESC    to BilingualString("தமிழ் மற்றும் ஆங்கிலத்தில் குழந்தைகளுக்கு வாசிக்க உதவும் AI பயிற்சியாளர்.", "AI-powered bilingual literacy coach for Tamil & English."),
        StringKey.ONBOARDING_2_TITLE   to BilingualString("உங்களுக்காக தயாரான பாடங்கள்!", "Lessons Made For You"),
        StringKey.ONBOARDING_2_DESC    to BilingualString("ஆசிரியர் பாடப்புத்தக பக்கங்களை பதிவேற்றி AI பாடங்களாக மாற்றுகிறது.", "Teachers upload textbook pages, AI turns them into personalised lessons."),
        StringKey.ONBOARDING_3_TITLE   to BilingualString("உன் பயணத்தைக் கண்காண்!", "Track Your Journey"),
        StringKey.ONBOARDING_3_DESC    to BilingualString("வாசிப்பு கண்டறிதல் மற்றும் தினசரி streak கொண்டு முன்னேற்றம் காண்.", "Reading screening & daily streak to see your progress."),
        StringKey.GET_STARTED          to BilingualString("தொடங்குவோம்!", "Get Started"),
        StringKey.NEXT                 to BilingualString("அடுத்து →", "Next →"),
        StringKey.SKIP                 to BilingualString("தவிர்", "Skip"),
        StringKey.NEW_STUDENT_TITLE    to BilingualString("புதிய மாணவர்", "New Student"),
        StringKey.OFFLINE_LOGIN_HINT   to BilingualString("இணைய இணைப்பு இல்லாமல் உள்நுழைய முடியாது. இணையத்தை இயக்கவும்.", "Login requires internet. Please enable internet and try again."),
        StringKey.EMPTY_STATE_TITLE    to BilingualString("வணக்கம்! தயாரா?", "Welcome! Ready to learn?"),
        StringKey.EMPTY_STATE_DESC     to BilingualString("ஆசிரியர் பாடங்களை அனுப்புவார். சிறிது நேரம் பொறு!", "Your teacher is preparing lessons. Check back soon!"),

        // Risk
        StringKey.RISK_UNSCREENED      to BilingualString("சோதிக்கப்படவில்லை", "Unscreened"),

        // Assessment
        StringKey.ASSESS_ANALYSING     to BilingualString("படிப்பை பகுப்பாய்வு செய்கிறேன்...", "Analysing your reading..."),
        StringKey.ASSESS_RESULT_TITLE  to BilingualString("சூப்பர்! நீ செய்தாய்!", "Super! You did it!"),
        StringKey.ASSESS_TRY_AGAIN     to BilingualString("மீண்டும் முயற்சிக்கலாம்", "Let's Try Again"),
        StringKey.ASSESS_MIC_NEEDED    to BilingualString("Microphone அனுமதி தேவை", "Microphone Permission Needed"),
        StringKey.ASSESS_MIC_DESC      to BilingualString("உங்கள் வாசிப்பைக் கேட்க மைக்ரோஃபோன் அணுகல் தேவை.", "Ezhil needs mic access to hear you read."),
        StringKey.ASSESS_OPEN_SETTINGS to BilingualString("Settings திற", "Open Settings"),
        StringKey.ASSESS_NOT_NOW       to BilingualString("இப்போது வேண்டாம்", "Not Now"),
        StringKey.ASSESS_STOP_CONFIRM  to BilingualString("Recording நிறுத்தணுமா?", "Stop Recording?"),
        StringKey.ASSESS_KEEP_RECORDING to BilingualString("தொடர் / Keep Recording", "Keep Recording"),
        StringKey.ASSESS_DISCARD       to BilingualString("நிறுத்து / Discard", "Discard"),
        StringKey.ASSESS_INSTRUCTIONS_TITLE to BilingualString("படிக்க ஆரம்பிக்கலாம்!", "Let's start reading!"),
        StringKey.ASSESS_HOLD_PHONE    to BilingualString("படிக்கும் போது மொபைலை இயல்பாகப் பிடிக்கவும்.", "Hold phone naturally while reading"),
        StringKey.ASSESS_LOOK_SCREEN   to BilingualString("படிக்கும் போது திரையைப் பார்க்கவும்.", "Look at the screen while reading"),
        StringKey.ASSESS_READ_CLEARLY  to BilingualString("பத்தியைத் தெளிவாக உரக்கப் படிக்கவும்.", "Read the passage out loud, clearly"),

        // Games
        StringKey.GAMES_TITLE          to BilingualString("விளையாடலாம்! / Let's Play!", "Let's Play!"),
        StringKey.GAMES_MATCH_SOUND    to BilingualString("ஒலியை இணைத்திடு", "Match Sound"),
        StringKey.GAMES_SPOT_LETTER    to BilingualString("எழுத்தைக் கண்டுபிடி", "Spot the Letter"),
        StringKey.GAMES_BUILD_WORD     to BilingualString("சொல்லை உருவாக்கு", "Build a Word"),
        StringKey.GAMES_ROUND          to BilingualString("சுற்று", "Round"),
        StringKey.GAMES_NEXT_ROUND     to BilingualString("அடுத்த சுற்று →", "Next Round →"),
        StringKey.GAMES_RETAKE         to BilingualString("மீண்டும் முயற்சி / மீண்டும் முயற்சிக்கவும்", "Retake Round"),
        StringKey.GAMES_CHECK          to BilingualString("சரிபார்க்கவும் / CHECK", "Check"),
        StringKey.GAMES_FIND_LETTER    to BilingualString("இந்த எழுத்தைக் கண்டுபிடி", "Find the Letter"),
        StringKey.GAMES_LISTEN_TO_SOUND to BilingualString("ஒலியைக் கேட்கவும்", "Listen to sound"),
        StringKey.GAMES_BUILD_WORD_INSTRUCTION to BilingualString("கீழே காட்டிய சொல்லை உருவாக்கு", "Build the word shown below"),
        StringKey.GAMES_ROUND_COMPLETE to BilingualString("சுற்று முடிந்தது!", "Round Complete!"),
        StringKey.GAMES_ACCURACY       to BilingualString("துல்லியம்", "Accuracy"),
        StringKey.GAMES_BONUS_XP       to BilingualString("வெகுமதி / Bonus XP", "Bonus XP"),

        // Achievements
        StringKey.ACHIEVE_SUPER        to BilingualString("சூப்பர்! நீ செய்தாய்!", "Super! You Did It!"),
        StringKey.ACHIEVE_WELL_DONE    to BilingualString("அருமை!", "Well Done!"),
        StringKey.ACHIEVE_READER       to BilingualString("அருமை! நீ ஒரு வாசகன்!", "Wonderful! You Are A Reader!"),
        StringKey.ACHIEVE_STREAK       to BilingualString("நாட்கள் தொடர்ந்து!", "Day Streak Achieved!"),
        StringKey.ACHIEVE_LANDMARK     to BilingualString("புதிய மைல்கல்!", "New Landmark Reached!"),
        StringKey.ACHIEVE_CLAIM        to BilingualString("பெறு / Claim!", "Claim!"),
        StringKey.ACHIEVE_GO_HOME      to BilingualString("முகப்புக்கு செல்", "Go Home"),
        StringKey.ACHIEVE_PLAY_AGAIN   to BilingualString("மீண்டும் விளையாடு", "Play Again"),
        StringKey.ACHIEVE_NEW_BADGE    to BilingualString("புதிய சாதனை", "New Badge"),
        StringKey.ACHIEVE_SHARE        to BilingualString("பகிர்", "Share Achievement"),

        // My Assessments
        StringKey.MY_ASSESSMENTS_TITLE   to BilingualString("என் மதிப்பீடுகள்", "My Assessments"),
        StringKey.MY_ASSESSMENTS_TOTAL   to BilingualString("மொத்த மதிப்பீடுகள்", "Total Completed"),
        StringKey.MY_ASSESSMENTS_AVG     to BilingualString("சராசரி", "Average"),
        StringKey.MY_ASSESSMENTS_SUCCESS to BilingualString("தேர்ச்சி", "Success"),
        StringKey.MY_ASSESSMENTS_RECENT  to BilingualString("சமீபத்திய முடிவுகள்", "Recent Results"),
        StringKey.MY_ASSESSMENTS_EMPTY   to BilingualString("இன்னும் மதிப்பீடுகள் இல்லை", "No assessments yet"),

        // Teacher
        StringKey.TEACHER_STUDENTS      to BilingualString("மாணவர்கள்", "Students"),
        StringKey.TEACHER_NEED_ATTENTION to BilingualString("கவனம் தேவை", "Need Attention"),
        StringKey.TEACHER_LESSONS       to BilingualString("பாடங்கள்", "Lessons"),
        StringKey.TEACHER_PROGRESS      to BilingualString("தேர்ச்சி", "Progress"),
        StringKey.TEACHER_CLASS_STATUS  to BilingualString("வகுப்பு நிலை", "Class Status"),
        StringKey.TEACHER_STABLE        to BilingualString("நிலையான", "Stable"),
        StringKey.TEACHER_CAUTION       to BilingualString("கவனிக்கவும்", "Caution"),
        StringKey.TEACHER_AT_RISK       to BilingualString("கவலைப்படுகிறோம்", "At Risk"),
        StringKey.TEACHER_VIEW_ALL      to BilingualString("அனைத்தையும் பார்", "View All"),
        StringKey.TEACHER_REVIEW        to BilingualString("மதிப்பாய்வு", "Review"),
        StringKey.TEACHER_REFER_SUPPORT to BilingualString("Support-க்கு refer செய்", "Refer for Support"),
        StringKey.TEACHER_RUN_ASSESSMENT to BilingualString("Assessment இயக்கு", "Run Assessment"),
        StringKey.TEACHER_NO_NETWORK    to BilingualString("நெட்வொர்க் இல்லை", "No Network"),
        StringKey.TEACHER_RETRY         to BilingualString("மீண்டும் முயற்சி / Retry", "Retry"),
        StringKey.TEACHER_LESSON_LIBRARY to BilingualString("பாட நூலகம்", "Lesson Library"),
        StringKey.TEACHER_NEW_LESSON    to BilingualString("புதிய பாடம்", "New Lesson"),
        StringKey.TEACHER_REPORTS       to BilingualString("அறிக்கைகள்", "Reports"),
        StringKey.TEACHER_FLUENCY_RATE  to BilingualString("உச்சரிப்பு விகிதம்", "Fluency Rate"),
        StringKey.TEACHER_AVG_READING   to BilingualString("சராசரி வாசிப்பு", "Avg. Reading"),

        // Lesson
        StringKey.EXIT_LESSON_TITLE     to BilingualString("Lesson விட்டு வெளியேறணுமா?", "Exit Lesson?"),
        StringKey.EXIT_LESSON_DESC      to BilingualString("உங்கள் முன்னேற்றம் சேமிக்கப்படும்.", "Your current progress will be saved."),
        StringKey.EXIT_LESSON_KEEP      to BilingualString("தொடர் / Keep Reading", "Keep Reading"),
        StringKey.EXIT_LESSON_EXIT      to BilingualString("வெளியேறு / Exit", "Exit"),

        // Difficulty
        StringKey.EASY                  to BilingualString("எளிது", "Easy"),
        StringKey.MEDIUM                to BilingualString("சாதாரண", "Medium"),
        StringKey.HARD                  to BilingualString("கடினம்", "Hard"),

        // Splash
        StringKey.SPLASH_LOADING        to BilingualString("எழு. படி. வலி.", "Rise. Read. Shine."),
        StringKey.STREAK_DAYS           to BilingualString("நாட்கள்", "days"),
        StringKey.CANCEL                to BilingualString("ரத்து", "Cancel"),

        // Role selection
        StringKey.ROLE_STUDENT_DESC     to BilingualString("படிக்க வா, விளையாட வா!", "Learn, play, and grow"),
        StringKey.ROLE_TEACHER_DESC     to BilingualString("மாணவர்களை கண்காணி", "Monitor & guide students"),
        StringKey.ROLE_TITLE_HINT       to BilingualString("யார் நீ இன்று?", "Who are you today?"),

        // Stats
        StringKey.STREAK                to BilingualString("தொடர்", "STREAK"),
        StringKey.LESSONS               to BilingualString("பாடங்கள்", "LESSONS"),
        StringKey.STARS                 to BilingualString("நட்சத்திரங்கள்", "STARS"),
        StringKey.JOURNEY_MAP           to BilingualString("உன் பயண வரைபடம்", "Your Journey Map"),
        StringKey.MILESTONE_1           to BilingualString("முதல் பாடம்", "First Lesson"),
        StringKey.MILESTONE_5           to BilingualString("5 பாடங்கள்", "5 Lessons"),
        StringKey.MILESTONE_10          to BilingualString("10 பாடங்கள்", "10 Lessons"),
        StringKey.MILESTONE_20          to BilingualString("20 பாடங்கள்", "20 Lessons"),
        StringKey.MILESTONE_50          to BilingualString("வாசக நிபுணன்", "Reading Expert"),
        StringKey.CHAMPION              to BilingualString("படிப்பு வீரன்!", "Reading Champion!"),
        StringKey.LEVEL                 to BilingualString("நிலை", "Level"),

        // Student Profile
        StringKey.PROFILE_TAP_HINT      to BilingualString("தொடர உங்கள் பெயரைத் தொடவும்", "Tap your name to continue"),
        StringKey.PROFILE_NEW_HINT      to BilingualString("புதிய சுயவிவரத்தை உருவாக்க தட்டவும்", "Tap to create a new profile"),
        StringKey.SYNC_STATUS_ALL       to BilingualString("அனைத்து தரவும் ஒத்திசைக்கப்பட்டன", "All data synced"),
        StringKey.CONTINUE              to BilingualString("தொடர்க", "Continue"),

        // Dashboard
        StringKey.DASHBOARD_TITLE       to BilingualString("ஆசிரியர் முகப்பு", "Teacher Dashboard"),
        StringKey.YOUR_CLASSROOM        to BilingualString("உங்கள் வகுப்பு", "Your Classroom"),
        StringKey.STREAK_LABEL          to BilingualString("தொடர்", "streak"),
        StringKey.RISK_NONE             to BilingualString("சோதனை இல்லை", "None"),

        // Student Home
        StringKey.HELLO                 to BilingualString("வணக்கம்!", "Hello!"),
        StringKey.LEARN_HINT             to BilingualString("இன்று நன்றாக படிக்கலாம்!", "Let's learn something today!"),
        StringKey.DAILY_GOAL            to BilingualString("இன்றைய இலக்கு", "Daily Goal"),
        StringKey.NEW_STORY             to BilingualString("புதிய கதை", "New Story"),
        StringKey.START                 to BilingualString("தொடங்கு", "Start"),
        StringKey.ACTIVITIES            to BilingualString("செயல்பாடுகள்", "Activities"),
        StringKey.RECENT_ACTIVITY       to BilingualString("சமீபத்திய செயல்பாடு", "Recent Activity"),

        // Read Aloud
        StringKey.READ_ALOUD_TITLE      to BilingualString("படிக்க ஆரம்பிக்கலாம்!", "Read Aloud"),
        StringKey.READ_ALOUD_SUBTITLE   to BilingualString("வாசிப்பு மதிப்பீடு", "READ ALOUD ASSESSMENT"),
        StringKey.INSTRUCTIONS_TITLE    to BilingualString("படிக்க ஆரம்பிக்கலாம்!", "Let's start reading!"),
        StringKey.INSTRUCTIONS_HINT     to BilingualString("உரக்கப் படிக்கவும்", "Read out loud"),
        StringKey.INST_HOLD             to BilingualString("மொபைலை இயற்பாகப் பிடிக்கவும்.", "Hold phone naturally while reading"),
        StringKey.INST_SCREEN           to BilingualString("படிக்கும் போது திரையைப் பார்க்கவும்.", "Look at the screen while reading"),
        StringKey.INST_READ             to BilingualString("பத்தியைத் தெளிவாக உரக்கப் படிக்கவும்.", "Read the passage out loud, clearly"),
        StringKey.REC_STATUS            to BilingualString("பதிவாகிறது", "REC"),
        StringKey.STOP_RECORDING        to BilingualString("நிறுத்து", "STOP RECORDING"),
        StringKey.STOP_TITLE            to BilingualString("Recording நிறுத்தணுமா?", "Stop Recording?"),
        StringKey.STOP_SUBTITLE         to BilingualString("பதிவை நிறுத்து", "Stop Recording"),
        StringKey.STOP_DESC             to BilingualString("உங்களது பதிவு நீக்கப்படும்.", "Your recording will be lost."),
        StringKey.STOP_HINT             to BilingualString("பதிவு தொலைந்துவிடும்", "Recording will be lost"),
        StringKey.KEEP_REC              to BilingualString("தொடர்", "Keep Recording"),
        StringKey.DISCARD_REC           to BilingualString("நிறுத்து", "Discard"),
        StringKey.ANALYSING_DESC        to BilingualString("வாசிப்பு மற்றும் மொழியியல் முறைகளை பகுப்பாய்வு செய்கிறேன்...", "Analysing reading progress and linguistic patterns..."),
        StringKey.STATUS_TITLE          to BilingualString("நிலை", "STATUS"),
        StringKey.STEP_TITLE            to BilingualString("படி", "STEP"),
        StringKey.PRIVACY_HINT          to BilingualString("மதிப்பீடு நடைபெறுகிறது. காத்திருக்கவும்.", "Assessment in progress. Please wait."),
        StringKey.TRY_AGAIN_TITLE       to BilingualString("மீண்டும் முயற்சிக்கலாம்", "Let's Try Again"),
        StringKey.TRY_AGAIN_SUB         to BilingualString("மீண்டும் முயற்சி", "LET'S TRY AGAIN"),
        StringKey.RECORDING_QUIET       to BilingualString("பதிவு மிகவும் அமைதியாக இருந்தது.", "The recording may have been too quiet."),
        StringKey.MIC_CLEAR             to BilingualString("மைக்ரோஃபோன் தெளிவாக இருக்கிறதா என்று பார்க்கவும்.", "Make sure the mic is clear."),
        StringKey.GO_HOME               to BilingualString("முகப்புக்கு செல்", "Go Home"),
        StringKey.ASSESS_COMPLETE_DESC  to BilingualString("மதிப்பீடு முடிந்தது. முடிவுகள் சேமிக்கப்பட்டன.", "Assessment complete. Results saved."),
        StringKey.MIC_TITLE             to BilingualString("Microphone அனுமதி தேவை", "Microphone Permission Needed"),
        StringKey.MIC_SUB               to BilingualString("மைக்ரோஃபோன் அணுகல்", "Microphone Permission Needed"),
        StringKey.MIC_HINT              to BilingualString("உங்கள் வாசிப்பைக் கேட்க எழிலுக்கு மைக்ரோபோன் அணுகல் தேவை.", "Ezhil needs mic access to hear you read."),
        StringKey.SECURITY_TITLE        to BilingualString("தகவல் பாதுகாப்பு", "Data Security"),
        StringKey.SECURITY_DESC         to BilingualString("உங்கள் குரல் மதிப்பீட்டிற்கு மட்டுமே பயன்படுத்தப்படும்.", "Your voice used only for assessment."),

        // Lessons
        StringKey.COMPLETED             to BilingualString("முடிந்தது", "Completed"),
        StringKey.ALL_SYNCED            to BilingualString("எல்லாம் புதுப்பிக்கப்பட்டது", "All Synced"),
        StringKey.LESSON_TAB_ALL        to BilingualString("அனைத்தும்", "All"),
        StringKey.LESSON_TAB_STORY      to BilingualString("கதை", "Story"),
        StringKey.LESSON_TAB_VOCAB      to BilingualString("சொற்கள்", "Vocab"),
        StringKey.LESSON_TAB_QUIZ       to BilingualString("புரிதல்", "Quiz"),
        StringKey.LESSON_TAB_LISTEN     to BilingualString("கேளு", "Listen"),
        StringKey.SEARCH_HINT           to BilingualString("மாணவரை தேடு...", "Search students..."),
        StringKey.NO_STUDENTS_FOUND      to BilingualString("மாணவர்கள் இல்லை", "No students found"),
        StringKey.LAST_7_DAYS          to BilingualString("கடந்த 7 நாட்கள்", "Last 7 Days"),
        StringKey.ASSESS_HISTORY       to BilingualString("மதிப்பீடு வரலாறு", "Assessment History"),
    )
}
