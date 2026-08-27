import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AchievementBadge, type BadgeKind } from '../../components/illustrations/Badges';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

export const AchievementScreen: React.FC = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const type = params.get('type') || 'lesson';
  const stars = parseInt(params.get('stars') || '3');
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setVisible(true), 100);
    return () => clearTimeout(t);
  }, []);

  const CONFIG: Record<string, { badge: BadgeKind; ta: string; en: string; color: string }> = {
    lesson:  { badge: 'first-lesson', ta: 'பாடம் முடிந்தது!',   en: 'Lesson Complete!',   color: 'heading-display-accent' },
    streak:  { badge: 'streak-7',     ta: '7 நாட்கள் தொடர்!',   en: '7-Day Streak!',      color: 'text-gradient-warm' },
    perfect: { badge: 'quiz-master',  ta: 'சரியான மதிப்பெண்!',  en: 'Perfect Score!',     color: 'text-gradient-warm' },
    firstwin:{ badge: 'first-read',   ta: 'முதல் வெற்றி!',       en: 'First Win!',         color: 'text-success'      },
  };

  const cfg = CONFIG[type] || CONFIG.lesson;

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 overflow-hidden relative">
      {/* Ambient celebratory glows */}
      <div className="orb w-[28rem] h-[28rem] bg-primary-fixed/10 top-[-6rem] left-1/2 -translate-x-1/2" />
      <div className="orb w-72 h-72 bg-studio-purple/10 bottom-0 right-0" />

      {/* Confetti bg */}
      <div className="absolute inset-0 pointer-events-none">
        {[...Array(24)].map((_, i) => (
          <div key={i} className="absolute w-2 h-2 rounded-sm opacity-70 animate-bounce"
            style={{ left: `${Math.random() * 100}%`, top: `${Math.random() * 100}%`, background: ['#62F9EE','#FFB955','#7C3AED','#43A047'][i % 4], animationDelay: `${Math.random() * 2}s`, animationDuration: `${1.5 + Math.random()}s`, transform: `rotate(${Math.random()*360}deg)` }} />
        ))}
      </div>

      <div className={`relative flex flex-col items-center text-center gap-8 transition-all duration-700 ${visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'}`}>
        {/* Earned badge with spinning halo, Ezhilan celebrating alongside */}
        <div className="relative flex items-center justify-center">
          <div className="absolute w-56 h-56 rounded-full bg-[conic-gradient(from_0deg,rgba(98,249,238,0.25),transparent_40%,rgba(124,58,237,0.2),transparent_70%,rgba(98,249,238,0.25))] blur-xl animate-spin-slow" />
          <div className="relative animate-bob origin-bottom filter drop-shadow-[0_0_40px_rgba(255,185,85,0.35)]">
            <AchievementBadge kind={cfg.badge} size={168} />
          </div>
          <div className="absolute -right-24 bottom-0 hidden sm:block">
            <EzhilanMoment trigger="celebrateBig" size={96} />
          </div>
        </div>

        {/* Title */}
        <div className="space-y-2 animate-pop-in">
          <h1 className={`font-display-tamil text-5xl font-bold ${cfg.color}`}>{cfg.ta}</h1>
          <p className="font-dashboard-title text-2xl text-on-surface">{cfg.en}</p>
        </div>

        {/* Stars */}
        <div className="flex gap-3">
          {[1,2,3].map(n => (
            <span key={n} className={`material-symbols-outlined text-5xl transition-all duration-500 ${n <= stars ? 'text-secondary drop-shadow-[0_0_14px_rgba(255,185,85,0.7)] animate-pop-in' : 'text-white/10'}`}
              style={{ fontVariationSettings: "'FILL' 1", animationDelay: `${n * 180}ms`, transitionDelay: `${n * 200}ms` }}>
              star
            </span>
          ))}
        </div>

        {/* XP badge */}
        <div className="glass-panel px-8 py-3 rounded-full flex items-center gap-3 border border-primary-fixed/30 animate-glow-breathe">
          <span className="material-symbols-outlined text-primary-fixed" style={{ fontVariationSettings: "'FILL' 1" }}>bolt</span>
          <span className="font-mono-metadata text-primary-fixed font-bold text-xl">+{stars * 10} XP</span>
        </div>

        {/* Actions */}
        <div className="w-full max-w-xs space-y-3 mt-4">
          <button onClick={() => navigate('/student/home')}
            className="w-full h-14 bg-primary-fixed text-bg-deep r-chip font-bold text-lg active:scale-95 transition-all shadow-[0_0_24px_rgba(98,249,238,0.3)]">
            தொடர்க / Continue
          </button>
          <button onClick={() => navigate('/student/lessons')}
            className="w-full h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors">
            Back to Lessons
          </button>
        </div>
      </div>
    </div>
  );
};
