import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { ProgressRing } from '../../components/ui/ProgressRing';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

export const GameSummary: React.FC = () => {
  const navigate = useNavigate();
  const { state } = useLocation() as { state?: { game?: string; score?: number; total?: number } };
  const game = state?.game ?? 'Game';
  const score = state?.score ?? 8;
  const total = state?.total ?? 10;
  const pct = Math.round((score / total) * 100);

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      {/* Ambient glows */}
      <div className="orb w-96 h-96 bg-primary-fixed/10 top-[-4rem] left-1/2 -translate-x-1/2" />
      <div className="orb w-72 h-72 bg-studio-purple/8 bottom-0 right-0" />

      <div className="text-center space-y-1 relative animate-pop-in">
        <h1 className="font-display-tamil text-3xl font-bold heading-display-accent">Game Over!</h1>
        <p className="text-on-surface-variant">{game}</p>
      </div>

      <div className="relative">
        <div className="absolute inset-0 rounded-full bg-primary-fixed/10 blur-2xl" />
        <div className="relative"><ProgressRing percent={pct} size={140} stroke={12} color="#62F9EE" /></div>
        {/* Ezhilan celebrates a strong round, warmly encourages otherwise */}
        <div className="absolute -right-24 bottom-0 hidden sm:block">
          <EzhilanMoment trigger={pct >= 60 ? 'celebrateBig' : 'encourage'} size={88} />
        </div>
      </div>
      {/* Children see stars and XP, never a raw score or percentage — a
          struggling reader should not be handed a number to compare. The
          ring above fills without a label; the count stays in the teacher's
          reports, where it belongs. */}
      <div className="text-center relative">
        <p className="font-display-tamil text-3xl font-bold text-primary-fixed">
          {pct >= 80 ? 'அருமை!' : pct >= 60 ? 'நல்லது!' : 'தொடர்ந்து முயற்சி!'}
        </p>
        <p className="text-text-muted text-sm mt-1">
          {pct >= 80 ? 'Excellent!' : pct >= 60 ? 'Good job!' : 'Keep practising!'}
        </p>
      </div>

      <div className="flex gap-4 w-full max-w-xs relative">
        <div className="flex-1 glass-panel r-chip p-3 text-center">
          <p className="font-mono-metadata text-primary-fixed font-bold text-xl">+{score * 2} XP</p>
          <p className="text-text-muted text-xs">Earned</p>
        </div>
        <div className="flex-1 glass-panel r-chip p-3 text-center">
          <div className="flex gap-0.5 justify-center">
            {[1,2,3].map(n => <span key={n} className={`material-symbols-outlined text-lg ${n <= (pct >= 80 ? 3 : pct >= 60 ? 2 : 1) ? 'text-tertiary-fixed-dim' : 'text-white/10'}`} style={{ fontVariationSettings: "'FILL' 1" }}>star</span>)}
          </div>
          <p className="text-text-muted text-xs mt-0.5">Stars</p>
        </div>
      </div>

      <div className="w-full max-w-xs space-y-3 relative">
        <button onClick={() => navigate('/student/games')}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_24px_rgba(98,249,238,0.3)] hover:shadow-[0_0_32px_rgba(98,249,238,0.5)]">
          Play Again
        </button>
        <button onClick={() => navigate('/student/home')}
          className="w-full h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors">
          Home
        </button>
      </div>
    </div>
  );
};
