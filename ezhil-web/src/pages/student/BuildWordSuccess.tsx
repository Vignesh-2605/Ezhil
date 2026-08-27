import React from 'react';
import { useNavigate } from 'react-router-dom';

export const BuildWordSuccess: React.FC = () => {
  const navigate = useNavigate();
  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-secondary/10 top-[-4rem] left-1/2 -translate-x-1/2" />
      <div className="orb w-72 h-72 bg-primary-fixed/8 bottom-0 left-0" />
      <div className="relative flex items-center justify-center">
        <div className="absolute w-44 h-44 rounded-full bg-secondary/15 blur-2xl animate-pulse" />
        <div className="relative text-[100px] drop-shadow-[0_0_30px_rgba(255,185,85,0.5)] animate-bob origin-bottom">🏗️</div>
      </div>
      <div className="text-center space-y-2 relative animate-pop-in">
        <h1 className="font-display-tamil text-4xl font-bold text-gradient-warm">சரியானது!</h1>
        <p className="text-on-surface-variant text-xl">Word Built Correctly!</p>
        <p className="font-display-tamil text-5xl font-bold heading-display-accent mt-4">யானை</p>
      </div>
      <div className="flex gap-3 justify-center relative">
        {[1,2,3].map(n => <span key={n} className="material-symbols-outlined text-4xl text-tertiary-fixed-dim drop-shadow-[0_0_8px_rgba(255,210,127,0.6)] animate-pop-in" style={{ fontVariationSettings: "'FILL' 1", animationDelay: `${n * 160}ms` }}>star</span>)}
      </div>
      <div className="w-full max-w-xs space-y-3 relative">
        <button onClick={() => navigate('/student/games/summary', { state: { game: 'Build a Word', score: 8, total: 10 } })}
          className="w-full h-14 bg-secondary text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all">
          See Results
        </button>
        <button onClick={() => navigate('/student/games')}
          className="w-full h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors">
          Back to Games
        </button>
      </div>
    </div>
  );
};
