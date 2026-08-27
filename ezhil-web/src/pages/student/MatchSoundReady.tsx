import React from 'react';
import { useNavigate } from 'react-router-dom';
import { MatchSoundIcon } from '../../components/illustrations/GameIcons';

export const MatchSoundReady: React.FC = () => {
  const navigate = useNavigate();
  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-primary-fixed/10 top-1/4 left-1/2 -translate-x-1/2" />
      <div className="relative w-28 h-28 flex items-center justify-center">
        <div className="absolute -inset-2 rounded-[28px] bg-primary-fixed/20 border-2 border-primary-fixed/50 animate-pulse-ring" />
        <div className="absolute inset-0 rounded-[24px] bg-primary-fixed/10 blur-xl" />
        <MatchSoundIcon size={104} className="relative rounded-[24px] shadow-[0_10px_28px_rgba(0,0,0,0.4)]" />
      </div>
      <div className="text-center space-y-2 relative animate-fade-in">
        <h1 className="font-display-tamil text-4xl font-bold heading-display-accent">ஒலி பொருத்தம்</h1>
        <p className="text-on-surface-variant text-xl">Match Sound Game</p>
        <p className="text-text-muted text-sm max-w-xs">Listen to each Tamil sound and tap the matching letter card.</p>
      </div>
      <div className="glass-panel r-card p-5 w-full max-w-sm space-y-3 relative animate-slide-in">
        {[{ icon: 'hearing', text: '10 rounds' }, { icon: 'timer', text: '5 sec per round' }, { icon: 'bolt', text: '+15 XP on completion' }].map(tip => (
          <div key={tip.text} className="flex items-center gap-3 text-on-surface-variant">
            <span className="material-symbols-outlined text-primary-fixed">{tip.icon}</span> {tip.text}
          </div>
        ))}
      </div>
      <div className="w-full max-w-xs space-y-3">
        <button onClick={() => navigate('/student/games/match-sound/playing')}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_20px_rgba(98,249,238,0.3)]">
          Start Game!
        </button>
        <button onClick={() => navigate('/student/games')} className="w-full h-12 text-on-surface-variant font-medium hover:text-white transition-colors">
          Back to Games
        </button>
      </div>
    </div>
  );
};
