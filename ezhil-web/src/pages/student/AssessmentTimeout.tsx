import React from 'react';
import { useNavigate } from 'react-router-dom';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

export const AssessmentTimeout: React.FC = () => {
  const navigate = useNavigate();
  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-error/8 top-1/4 left-1/2 -translate-x-1/2" />
      <EzhilanMoment trigger="encourage" size={100} className="relative" />
      <div className="relative w-24 h-24 flex items-center justify-center">
        <div className="absolute inset-0 rounded-full bg-error/10 border-2 border-error/30" />
        <div className="absolute inset-0 rounded-full bg-error/10 blur-xl" />
        <span className="relative material-symbols-outlined text-error text-5xl animate-bob origin-bottom" style={{ fontVariationSettings: "'FILL' 1" }}>timer_off</span>
      </div>
      <div className="text-center space-y-2 relative animate-fade-in">
        <h1 className="font-display-tamil text-3xl font-bold text-white">நேரம் முடிந்தது</h1>
        <p className="text-on-surface-variant">Time's up! Don't worry, try again. 💪</p>
      </div>
      <div className="w-full max-w-xs space-y-3 relative">
        <button onClick={() => navigate('/student/assessment/start')}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all">
          மீண்டும் முயல் / Try Again
        </button>
        <button onClick={() => navigate('/student/home')}
          className="w-full h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors">
          Back to Home
        </button>
      </div>
    </div>
  );
};
