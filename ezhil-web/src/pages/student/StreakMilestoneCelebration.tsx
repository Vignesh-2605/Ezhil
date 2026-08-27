import React from 'react';
import { useNavigate } from 'react-router-dom';
import { AchievementBadge } from '../../components/illustrations/Badges';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

export const StreakMilestoneCelebration: React.FC = () => {
  const navigate = useNavigate();
  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      {/* Warm ambient glow */}
      <div className="orb w-[30rem] h-[30rem] bg-secondary/12 top-[-6rem] left-1/2 -translate-x-1/2" />
      <div className="orb w-72 h-72 bg-brand-marigold/10 bottom-0 left-0" />
      {[...Array(18)].map((_, i) => (
        <div key={i} className="absolute w-3 h-3 rounded-sm animate-bounce opacity-70"
          style={{ left: `${Math.random() * 100}%`, top: `${Math.random() * 100}%`, background: ['#FFB955','#62F9EE','#7C3AED'][i%3], animationDelay: `${Math.random()*2}s`, transform: `rotate(${Math.random()*360}deg)` }} />
      ))}
      <div className="relative flex items-center justify-center">
        <div className="absolute w-52 h-52 rounded-full bg-[conic-gradient(from_0deg,rgba(255,185,85,0.3),transparent_45%,rgba(245,166,35,0.25),transparent_75%,rgba(255,185,85,0.3))] blur-xl animate-spin-slow" />
        <div className="relative animate-bob origin-bottom drop-shadow-[0_0_40px_rgba(255,185,85,0.45)]">
          <AchievementBadge kind="streak-7" size={176} />
        </div>
        <div className="absolute -right-20 bottom-0 hidden sm:block">
          <EzhilanMoment trigger="celebrateBig" size={88} />
        </div>
      </div>
      <div className="text-center space-y-3 relative z-10 animate-pop-in">
        <h1 className="font-display-tamil text-5xl font-bold text-gradient-warm">7 நாட்கள்!</h1>
        <p className="font-dashboard-title text-3xl text-white">7-Day Streak!</p>
        <p className="text-on-surface-variant">You've been learning for 7 days in a row. Amazing!</p>
      </div>
      <div className="glass-panel r-card px-8 py-4 border border-secondary/30 relative z-10 animate-glow-breathe">
        <p className="font-mono-metadata text-secondary font-bold text-2xl text-center">+50 Bonus XP 🎁</p>
      </div>
      <button onClick={() => navigate('/student/home')}
        className="w-full max-w-xs h-14 bg-secondary text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_24px_rgba(255,185,85,0.4)] relative z-10">
        தொடர்க / Continue
      </button>
    </div>
  );
};
