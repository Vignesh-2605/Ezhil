import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { db } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';

const QUESTIONS = [
  { q: 'யானை எங்கே இருந்தது?', options: ['காட்டில்', 'நகரத்தில்', 'கடலில்', 'மலையில்'], answer: 0 },
  { q: 'எறும்பு என்ன சொன்னது?', options: ['நான் ஓடுவேன்', 'நான் வலிமையானவன்', 'நான் சின்னவன்', 'நான் பயப்படுகிறேன்'], answer: 1 },
  { q: 'யானை என்ன கற்றுக்கொண்டது?', options: ['ஓட வேண்டும்', 'சிறியவரை அலட்சியப்படுத்தக் கூடாது', 'எறும்புகளை பயப்படவேண்டும்', 'காட்டை விட வேண்டும்'], answer: 1 },
];

export const LessonQuiz: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  // Load state passed from previous steps or default
  const state = (location.state as { current?: number; score?: number; lessonId?: string }) ?? {};
  const current = state.current ?? 0;
  const score = state.score ?? 0;
  const lessonId = state.lessonId;

  const [selected, setSelected] = useState<number | null>(null);
  const [answered, setAnswered] = useState(false);

  // Live query lesson from DB
  const dbLesson = useLiveQuery(async () => {
    if (!lessonId) return null;
    return await db.lessons.get(lessonId);
  }, [lessonId]);

  let quizQuestions = QUESTIONS;
  if (dbLesson) {
    try {
      const parsed = JSON.parse(dbLesson.contentJson);
      if (Array.isArray(parsed.quiz) && parsed.quiz.length > 0) {
        quizQuestions = parsed.quiz.map((q: any) => ({
          q: q.question_ta || q.q || '',
          options: q.options_ta || q.options || [],
          answer: q.correct_index !== undefined ? q.correct_index : 0
        }));
      }
    } catch (e) {
      console.error("Failed to parse quiz from contentJson", e);
    }
  }

  // Guard against index out of bounds
  if (current >= quizQuestions.length) {
    return null;
  }

  const q = quizQuestions[current];
  const isCorrect = selected === q.answer;

  const handleSelect = (i: number) => {
    if (answered) return;
    setSelected(i);
    setAnswered(true);
    setTimeout(() => {
      const nextState = {
        current,
        score: i === q.answer ? score + 1 : score,
        lessonId,
        totalQuestions: quizQuestions.length,
        correctAnswer: q.options[q.answer]
      };
      if (i === q.answer) {
        navigate('/student/quiz/feedback/correct', { state: nextState });
      } else {
        navigate('/student/quiz/feedback/wrong', { state: nextState });
      }
    }, 800);
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col font-body-tamil px-6 py-8 gap-8">
      {/* Progress */}
      <div className="space-y-2">
        <div className="flex justify-between text-sm text-text-muted">
          <span>Question {current + 1} / {quizQuestions.length}</span>
        </div>
        <div className="h-2 bg-white/5 rounded-full overflow-hidden">
          <div className="h-full bg-primary-fixed rounded-full transition-all duration-300" style={{ width: `${((current) / quizQuestions.length) * 100}%` }} />
        </div>
      </div>

      {/* Question */}
      <div className="glass-panel r-card p-8 flex-1 flex flex-col gap-8 overflow-safe-container">
        <div className="text-center space-y-2">
          <span className="material-symbols-outlined text-primary-fixed text-3xl">quiz</span>
          <h2 className="font-display-tamil text-2xl font-bold text-white word-break-normal leading-snug">{q.q}</h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {q.options.map((opt, i) => (
            <button key={i} onClick={() => handleSelect(i)}
              className={`w-full p-4 min-h-[64px] r-chip text-left font-body-tamil text-base sm:text-lg font-medium transition-all border word-break-normal cursor-pointer flex items-center ${
                !answered ? 'border-white/10 text-on-surface hover:border-primary-fixed/50 hover:bg-primary-fixed/5' :
                i === q.answer ? 'border-success bg-success/15 text-success' :
                i === selected ? 'border-error bg-error/15 text-error' :
                'border-white/5 text-text-muted opacity-50'
              }`}>
              <span className="font-mono-metadata text-xs mr-2 opacity-60 flex-shrink-0">{String.fromCharCode(65 + i)}.</span>
              <span className="flex-1">{opt}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
