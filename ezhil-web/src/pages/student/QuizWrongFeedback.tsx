import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

export const QuizWrongFeedback: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  
  const state = (location.state as { current: number; score: number; lessonId?: string; totalQuestions: number; correctAnswer?: string }) ?? { 
    current: 0, 
    score: 0, 
    totalQuestions: 3, 
    correctAnswer: 'காட்டில்' 
  };

  const handleNext = () => {
    if (state.current + 1 >= state.totalQuestions) {
      navigate('/student/lesson/complete', { state: { lessonId: state.lessonId, score: state.score } });
    } else {
      navigate('/student/quiz', { state: { current: state.current + 1, score: state.score, lessonId: state.lessonId } });
    }
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-error/8 top-[-4rem] left-1/2 -translate-x-1/2" />
      <EzhilanMoment trigger="encourage" size={96} className="relative" />
      <div className="relative flex items-center justify-center">
        <div className="absolute w-40 h-40 rounded-full bg-error/10 blur-2xl" />
        <div className="relative text-[100px] drop-shadow-[0_0_30px_rgba(229,57,53,0.5)] animate-pop-in">❌</div>
      </div>
      <div className="text-center space-y-2 relative animate-fade-in">
        <h1 className="font-display-tamil text-4xl font-bold text-error">தவறானது!</h1>
        <p className="text-on-surface-variant text-lg">Wrong answer. Try again next time! 💪</p>
      </div>
      <div className="glass-panel r-card p-4 w-full max-w-xs text-center space-y-1 relative ring-gradient">
        <p className="text-text-muted text-sm">Correct answer:</p>
        <p className="font-display-tamil heading-display-accent font-bold text-lg">{state.correctAnswer}</p>
      </div>
      <button onClick={handleNext}
        className="relative w-full max-w-xs h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all cursor-pointer shadow-[0_0_20px_rgba(98,249,238,0.25)] hover:shadow-[0_0_30px_rgba(98,249,238,0.45)]">
        தொடரு / Continue
      </button>
    </div>
  );
};
