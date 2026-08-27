import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { TiltCard } from '../../components/motion/Interactive';
import { AnimatedNumber } from '../../components/motion/AnimatedNumber';
import { MatchSoundIcon, SpotLetterIcon, BuildWordIcon } from '../../components/illustrations/GameIcons';

const GAMES = [
  {
    id: 'match-sound',
    icon: 'hearing',
    ta: 'ஒலி பொருத்தம்',
    en: 'Match Sound',
    desc: 'Hear a sound and match it to the right letter.',
    color: 'text-primary-fixed', border: 'border-primary-fixed', bg: 'bg-primary-fixed/10',
    to: '/student/games/match-sound/ready',
    xp: '+15 XP',
  },
  {
    id: 'spot-letter',
    icon: 'search',
    ta: 'எழுத்து கண்டுபிடி',
    en: 'Spot the Letter',
    desc: 'Find the correct letter among distractors.',
    color: 'text-studio-purple', border: 'border-studio-purple', bg: 'bg-studio-purple/10',
    to: '/student/games/spot-letter/playing',
    xp: '+15 XP',
  },
  {
    id: 'build-word',
    icon: 'build',
    ta: 'சொல் கட்டு',
    en: 'Build a Word',
    desc: 'Arrange letters to form Tamil words.',
    color: 'text-secondary', border: 'border-secondary', bg: 'bg-secondary/10',
    to: '/student/games/build-word/playing',
    xp: '+20 XP',
  },
];

const GAME_LABELS: Record<string, { name: string; icon: string; color: string }> = {
  match_sound: { name: 'Match Sound', icon: 'hearing', color: 'text-primary-fixed' },
  spot_letter: { name: 'Spot the Letter', icon: 'search', color: 'text-studio-purple' },
  build_word: { name: 'Build a Word', icon: 'build', color: 'text-secondary' },
};

const DAILY_XP_GOAL = 60;
const XP_PER_STAR = 5;

export const PhonicsGamesHub: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';

  // Today's XP, computed from real game sessions — never a hardcoded number.
  const todayXp = useLiveQuery(async () => {
    if (!studentId) return 0;
    const todayPrefix = new Date().toISOString().slice(0, 10);
    const sessions = await db.game_sessions.where('studentId').equals(studentId).toArray();
    return sessions
      .filter(s => s.playedAt.startsWith(todayPrefix))
      .reduce((sum, s) => sum + s.starsEarned * XP_PER_STAR, 0);
  }, [studentId]) ?? 0;

  const recentScores = useLiveQuery(async () => {
    if (!studentId) return [];
    const sessions = await db.game_sessions.where('studentId').equals(studentId).toArray();
    return sessions
      .sort((a, b) => b.playedAt.localeCompare(a.playedAt))
      .slice(0, 3)
      .map(s => ({
        ...GAME_LABELS[s.gameType] ?? { name: s.gameType, icon: 'sports_esports', color: 'text-primary-fixed' },
        score: `${s.roundsCorrect}/${s.roundsTotal}`,
        id: s.id,
      }));
  }, [studentId]) ?? [];

  const goalPct = Math.min(100, Math.round((todayXp / DAILY_XP_GOAL) * 100));

  return (
    <div className="space-y-8 font-body-tamil">
      <div className="animate-fade-in flex items-center gap-4">
        <span className="text-4xl animate-bob origin-bottom">🎮</span>
        <div>
          <h1 className="font-display-tamil text-3xl font-bold heading-display-accent">விளையாட்டுகள்</h1>
          <p className="text-text-muted text-sm mt-1">Phonics Games · Earn XP while learning!</p>
        </div>
      </div>

      {/* XP summary — live data with a counting ticker */}
      <div className="glass-panel r-card p-5 flex items-center gap-5 border-l-4 border-secondary relative overflow-hidden shimmer animate-slide-in">
        <div className="absolute -top-10 -right-8 w-32 h-32 bg-secondary/10 rounded-full blur-2xl" />
        <span className="text-4xl animate-bob origin-bottom drop-shadow-[0_0_10px_rgba(255,185,85,0.5)]">⭐</span>
        <div className="flex-1 relative">
          <p className="text-white font-bold text-lg">
            Today's XP: <AnimatedNumber value={todayXp} className="text-secondary" />
          </p>
          <p className="text-text-muted text-sm">
            {todayXp >= DAILY_XP_GOAL
              ? 'இலக்கு முடிந்தது! / Goal reached! 🎉'
              : `Goal: ${todayXp} / ${DAILY_XP_GOAL} XP`}
          </p>
          <div className="h-2.5 bg-white/5 rounded-full overflow-hidden mt-2 relative">
            <div className="h-full bg-gradient-to-r from-secondary to-brand-marigold rounded-full shadow-[0_0_12px_rgba(255,185,85,0.5)] transition-all duration-700"
              style={{ width: `${goalPct}%` }} />
          </div>
        </div>
      </div>

      {/* Games grid — 3D tilt cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 stagger-children">
        {GAMES.map(g => (
          <TiltCard key={g.id} onClick={() => navigate(g.to)}
            className={`glass-panel r-card p-6 text-left flex flex-col gap-4 border-t-2 ${g.border} group relative overflow-hidden cursor-pointer`}>
            <div className={`absolute -top-10 -right-10 w-28 h-28 ${g.bg} rounded-full blur-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-500`} />
            <div className="flex items-start justify-between relative" style={{ transform: 'translateZ(30px)' }}>
              <div className="group-hover:scale-110 group-hover:-rotate-6 transition-transform duration-300 r-card shadow-[0_6px_16px_rgba(0,0,0,0.35)]">
                {g.id === 'match-sound' ? <MatchSoundIcon size={56} />
                  : g.id === 'spot-letter' ? <SpotLetterIcon size={56} />
                  : <BuildWordIcon size={56} />}
              </div>
              <span className={`font-mono-metadata text-xs font-bold ${g.color} ${g.bg} px-2 py-1 rounded-full border ${g.border}/30`}>{g.xp}</span>
            </div>
            <div className="relative" style={{ transform: 'translateZ(20px)' }}>
              <h3 className={`font-display-tamil font-bold text-xl ${g.color}`}>{g.ta}</h3>
              <p className="text-text-muted text-sm font-bilingual-sub">{g.en}</p>
            </div>
            <p className="text-on-surface-variant text-xs leading-relaxed relative">{g.desc}</p>
            <div className={`flex items-center gap-1 ${g.color} text-sm font-bold mt-auto relative group-hover:gap-2 transition-all`}>
              Play Now <span className="material-symbols-outlined text-base group-hover:translate-x-1 transition-transform">arrow_forward</span>
            </div>
          </TiltCard>
        ))}
      </div>

      {/* Recent scores — real sessions from this device */}
      <div className="glass-panel r-card p-5 space-y-3">
        <h2 className="text-white font-bold uppercase tracking-wider text-sm">Recent Scores</h2>
        {recentScores.length === 0 ? (
          <p className="text-xs text-text-muted text-center py-3">
            விளையாடி முதல் மதிப்பெண்ணைப் பெறுங்கள்! / Play a game to see your first score!
          </p>
        ) : (
          recentScores.map(r => (
            <div key={r.id} className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className={`material-symbols-outlined ${r.color} text-xl`}>{r.icon}</span>
                <span className="text-on-surface text-sm font-medium">{r.name}</span>
              </div>
              <span className={`font-mono-metadata font-bold ${r.color}`}>{r.score}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
