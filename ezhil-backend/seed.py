"""
Integrated Seed Script — Merges old and new data into ezhil.db.
Maintains Teacher T-0042 while adding the new presentation team.

Run:
    cd ezhil-backend
    python seed.py
"""
import asyncio
import json
import sys
import uuid

sys.path.insert(0, ".")

from sqlalchemy import select
from auth_utils import hash_pin
from db import AsyncSessionLocal, init_db, engine
from models.db_models import School, Teacher, Student, Lesson

# All demo teachers share this PIN. Students' PINs are their birthday MMDD.
DEMO_TEACHER_PIN = "1234"
DEMO_STUDENT_DOBS = [
    "2016-05-12",  # PIN 0512
    "2016-03-20",  # PIN 0320
    "2015-08-10",  # PIN 0810
    "2016-01-05",  # PIN 0105
    "2015-11-22",  # PIN 1122
]

# ── Shared Demo Content ──────────────────────────────────────────────────────

LESSON_YANAI = {
    "title": "யானையும் எறும்பும்",
    "passage": {
        "lines": [
            "ஒரு பெரிய யானை காட்டில் வாழ்ந்தது.",
            "அது எல்லா உயிரினங்களையும் ஏளனம் செய்தது.",
            "யானை எறும்பை கேலி செய்தது.",
            "எறும்பு தன் அணியை அழைத்தது.",
            "யானை பணிவை கற்றது."
        ],
        "line_count": 5
    },
    "vocabulary": [
        {"word": "யானை", "syllables": ["யா","னை"], "meaning_ta": "பெரிய விலங்கு", "meaning_en": "Elephant"},
        {"word": "எறும்பு", "syllables": ["எ","றும்","பு"], "meaning_ta": "சிறிய பூச்சி", "meaning_en": "Ant"}
    ],
    "quiz": [
        {
            "question_ta": "யானை எங்கு வாழ்ந்தது?",
            "question_en": "Where did the elephant live?",
            "options_ta": ["நகரம்", "காடு", "கடல்"],
            "options_en": ["City", "Forest", "Sea"],
            "correct_index": 1
        }
    ]
}

# ── Integrated Data Schema ───────────────────────────────────────────────────

ALL_DATA = [
    {
        "code": "SCH-001", "name": "Govt. Primary School, Madurai", "district": "Madurai",
        "teachers": [
            {
                "id": "T-0042", "name": "மீனாட்சி", "class": "Grade 2",
                "students": ["Kavin S.", "Iniya R.", "Mughil V.", "Arun K.", "Oviya P."]
            },
            {
                "id": "1001", "name": "Suresh Kumar", "class": "Grade 3-A",
                "students": ["Anbuselvan A.", "Bharathi K.", "Chandrasekhar V.", "Thamaraiselvi K.", "Adithyan S."]
            },
            {
                "id": "1002", "name": "Priya Dharshini", "class": "Grade 3-B",
                "students": ["Murugan P.", "Nivetha S.", "Prakash T.", "Revathi K.", "Deepak M."]
            }
        ]
    },
    {
        "code": "SCH-002", "name": "Chennai Public School", "district": "Chennai",
        "teachers": [
            {
                "id": "2001", "name": "Meena Kumari", "class": "Grade 4-B",
                "students": ["Faizal A.", "Gowri N.", "Hari P.", "Aishwarya K.", "Jayanthi M."]
            },
            {
                "id": "2002", "name": "Rajesh Nair", "class": "Grade 4-A",
                "students": ["Lakshmi V.", "Manohar S.", "Nandhini P.", "Om Prakash T.", "Padma R."]
            }
        ]
    },
    {
        "code": "SCH-003", "name": "Delhi Model School", "district": "New Delhi",
        "teachers": [
            {
                "id": "3001", "name": "Amit Sharma", "class": "Grade 5-C",
                "students": ["Ishaan M.", "Jaya R.", "Kiran S.", "Lovely T.", "Megha P."]
            }
        ]
    }
]

async def seed() -> None:
    print("Connecting to database...")
    await init_db()

    async with AsyncSessionLocal() as db:
        print("Starting data integration...")

        for s_info in ALL_DATA:
            # 1. Ensure School exists
            res = await db.execute(select(School).where(School.code == s_info["code"]))
            school = res.scalar_one_or_none()

            if not school:
                school = School(
                    id=str(uuid.uuid4()),
                    code=s_info["code"],
                    name=s_info["name"],
                    district=s_info["district"]
                )
                db.add(school)
                await db.flush()
                print(f"Created School: {s_info['code']}")

            for t_info in s_info["teachers"]:
                # 2. Ensure Teacher exists
                res = await db.execute(select(Teacher).where(Teacher.teacher_code == t_info["id"]))
                teacher = res.scalar_one_or_none()

                if not teacher:
                    teacher = Teacher(
                        id=str(uuid.uuid4()),
                        school_id=school.id,
                        teacher_code=t_info["id"],
                        name=t_info["name"],
                        class_name=t_info["class"],
                        hashed_pin=hash_pin(DEMO_TEACHER_PIN)
                    )
                    db.add(teacher)
                    await db.flush()
                    print(f"  Added Teacher: {t_info['id']} ({t_info['name']})")
                elif teacher.hashed_pin is None:
                    teacher.hashed_pin = hash_pin(DEMO_TEACHER_PIN)

                # 3. Ensure Lesson exists for this teacher
                res = await db.execute(select(Lesson).where(Lesson.teacher_id == teacher.id, Lesson.title == LESSON_YANAI["title"]))
                if not res.scalar_one_or_none():
                    lesson = Lesson(
                        id=str(uuid.uuid4()),
                        teacher_id=teacher.id,
                        title=LESSON_YANAI["title"],
                        content_json=json.dumps(LESSON_YANAI, ensure_ascii=False),
                        is_published=True,
                        lesson_type="story",
                        difficulty=1
                    )
                    db.add(lesson)

                # 4. Add missing students
                res = await db.execute(select(Student).where(Student.teacher_id == teacher.id))
                existing_students = {s.name: s for s in res.scalars().all()}

                for idx, s_name in enumerate(t_info["students"]):
                    dob = DEMO_STUDENT_DOBS[idx % len(DEMO_STUDENT_DOBS)]
                    if s_name not in existing_students:
                        student = Student(
                            id=str(uuid.uuid4()),
                            teacher_id=teacher.id,
                            name=s_name,
                            dob=dob,
                            risk_level="unscreened",
                            streak_days=0
                        )
                        db.add(student)
                        print(f"    + Student: {s_name} (PIN {dob[5:7]}{dob[8:10]})")
                    elif not existing_students[s_name].dob:
                        existing_students[s_name].dob = dob

        await db.commit()
        print("\nIntegration complete. Database is up to date.")
        print("\nALL AVAILABLE LOGINS (teacher PIN: 1234, student PIN: birthday MMDD):")
        print("  1. School: SCH-001 | Teacher: T-0042 (Original)")
        print("  2. School: SCH-001 | Teacher: 1001")
        print("  3. School: SCH-002 | Teacher: 2001")
        print("  4. School: SCH-003 | Teacher: 3001")

    await engine.dispose()

if __name__ == "__main__":
    asyncio.run(seed())
