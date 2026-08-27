import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

export const QuizCorrectFeedback: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state as { current: number; score: number; lessonId?: string; totalQuestions: number }) ?? { current: 0, score: 0, totalQuestions: 3 };

  const handleNext = () => {
    if (state.current + 1 >= state.totalQuestions) {
      navigate('/student/lesson/complete', { state: { lessonId: state.lessonId, score: state.score } });
    } else {
      navigate('/student/quiz', { state: { current: state.current + 1, score: state.score, lessonId: state.lessonId } });
    }
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-success/12 top-[-4rem] left-1/2 -translate-x-1/2" />
      <EzhilanMoment trigger="celebrateSmall" size={96} className="relative" />
      <div className="relative flex items-center justify-center">
        <div className="absolute w-44 h-44 rounded-full bg-success/15 blur-2xl animate-pulse" />
        <div className="relative text-[100px] drop-shadow-[0_0_30px_rgba(67,160,71,0.5)] animate-pop-in">✅</div>
      </div>
      <div className="text-center space-y-2 relative animate-pop-in">
        <h1 className="font-display-tamil text-4xl font-bold text-success drop-shadow-[0_0_16px_rgba(67,160,71,0.4)]">சரியானது!</h1>
        <p className="text-on-surface-variant text-lg">Correct! Great job! 🎉</p>
        <p className="font-mono-metadata text-primary-fixed font-bold">+10 XP</p>
      </div>
      <button onClick={handleNext}
        className="relative w-full max-w-xs h-14 bg-success text-white font-bold text-lg r-chip active:scale-95 transition-all cursor-pointer shadow-[0_0_24px_rgba(67,160,71,0.3)] hover:shadow-[0_0_32px_rgba(67,160,71,0.5)]">
        அடுத்த கேள்வி / Next Question
      </button>
    </div>
  );
};
