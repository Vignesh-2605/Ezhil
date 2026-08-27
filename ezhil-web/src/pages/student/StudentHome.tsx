import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { TopAppBar } from '../../components/layout/TopAppBar';
import { ProgressRing } from '../../components/ui/ProgressRing';
import { db } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';
import { TiltCard } from '../../components/motion/Interactive';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';
import { EmptyActivityArt } from '../../components/illustrations/Illustrations';

const ACTIVITY_GRID = [
  { icon: 'mic',           color: 'text-secondary',     border: 'border-secondary',     bg: 'bg-secondary/10',     ta: 'படிக்கலாம்',    en: 'Read Aloud', to: '/student/assessment/start' },
  { icon: 'sports_esports',color: 'text-primary-fixed', border: 'border-primary-fixed', bg: 'bg-primary-fixed/10', ta: 'விளையாடலாம்',  en: 'Word Games', to: '/student/games'           },
  { icon: 'menu_book',     color: 'text-studio-purple', border: 'border-studio-purple', bg: 'bg-studio-purple/10', ta: 'என் பாடங்கள்', en: 'My Lessons', to: '/student/lessons'         },
  { icon: 'map',           color: 'text-success',       border: 'border-success',       bg: 'bg-success/10',       ta: 'என் பயணம்',    en: 'My Journey', to: '/student/journey'         },
];

export const StudentHome: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';

  // 1. Live Query student details
  const student = useLiveQuery(async () => {
    if (!studentId) return null;
    return await db.students.get(studentId);
  }, [studentId]);

  // 2. Live Query progress metrics
  const progressMetrics = useLiveQuery(async () => {
    if (!studentId) return { completed: 0, total: 0, percent: 0 };
    const totalLessons = await db.lessons.count();
    const completedProgress = await db.lesson_progress
      .where('studentId')
      .equals(studentId)
      .filter(p => !!p.completedAt)
      .count();

    const percent = totalLessons > 0 ? Math.round((completedProgress / totalLessons) * 100) : 0;
    return { completed: completedProgress, total: totalLessons, percent };
  }, [studentId]) || { completed: 0, total: 0, percent: 0 };

  // 3. Live Query recent activities (merging games and lesson progress)
  const recentActivities = useLiveQuery(async () => {
    if (!studentId) return [];
    
    // Fetch last 3 games
    const games = await db.game_sessions
      .where('studentId')
      .equals(studentId)
      .reverse()
      .sortBy('playedAt');
      
    // Fetch last 3 lessons completed
    const lessonsProgress = await db.lesson_progress
      .where('studentId')
      .equals(studentId)
      .reverse()
      .sortBy('completedAt');

    const activities = [];

    // Map games
    for (const g of games.slice(0, 3)) {
      const typeMap = { match_sound: 'ஒலியை பொருத்து', spot_letter: 'எழுத்தை கண்டுபிடி', build_word: 'வார்த்தை உருவாக்கு' };
      activities.push({
        type: 'game',
        icon: 'sports_esports',
        color: 'text-primary-fixed',
        bg: 'bg-primary-fixed/20',
        title: typeMap[g.gameType] || 'விளையாட்டு',
        sub: `Rounds: ${g.roundsCorrect}/${g.roundsTotal} · Stars: ${g.starsEarned}`,
        timestamp: new Date(g.playedAt).getTime(),
        timeStr: formatTimeAgo(g.playedAt)
      });
    }

    // Map lessons
    for (const lp of lessonsProgress.slice(0, 3)) {
      const lesson = await db.lessons.get(lp.lessonId);
      activities.push({
        type: 'lesson',
        icon: 'auto_stories',
        color: 'text-success',
        bg: 'bg-success/20',
        title: lesson?.title || 'வாசிப்பு பயிற்சி',
        sub: lp.quizScorePercent !== undefined ? 'Quiz Completed' : 'Done',
        timestamp: lp.completedAt ? new Date(lp.completedAt).getTime() : 0,
        timeStr: lp.completedAt ? formatTimeAgo(lp.completedAt) : ''
      });
    }

    // Sort combined by timestamp DESC
    return activities.sort((a, b) => b.timestamp - a.timestamp).slice(0, 3);
  }, [studentId]) || [];

  // 4. Daily goal suggestion
  const nextLesson = useLiveQuery(async () => {
    const all = await db.lessons.toArray();
    if (all.length === 0) return null;
    
    // Find first lesson that is not completed
    for (const l of all) {
      if (studentId) {
        const completed = await db.lesson_progress
          .where('studentId')
          .equals(studentId)
          .filter(p => p.lessonId === l.id && !!p.completedAt)
          .count();
        if (completed === 0) return l;
      }
    }
    return all[0]; // fallback to first
  }, [studentId]);

  function formatTimeAgo(dateStr: string): string {
    const seconds = Math.floor((new Date().getTime() - new Date(dateStr).getTime()) / 1000);
    if (seconds < 60) return 'Just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return new Date(dateStr).toLocaleDateString();
  }

  const studentName = student?.name || session?.name || 'மாணவர்';
  const streak = student?.streakDays ?? 0;

  return (
    <div className="font-body-tamil text-on-surface flex flex-col min-h-dvh">
      <div className="md:hidden"><TopAppBar title="எழில் | Ezhil" /></div>

      <main className="w-full max-w-7xl mx-auto py-8 px-4 sm:px-6 space-y-8 flex-grow">
        {/* Header & Streak */}
        <section className="flex flex-col md:flex-row md:items-center justify-between gap-6 animate-fade-in">
          <div>
            <h2 className="font-display-tamil text-4xl font-bold mb-2 flex items-center gap-3 flex-wrap">
              <span className="text-white">வணக்கம்,</span>
              <span className="heading-display-accent">{studentName}!</span>
              <span className="inline-block animate-bob origin-bottom">👋</span>
            </h2>
            <p className="text-text-muted text-lg font-bilingual-sub">இன்று நன்றாக படிக்கலாம்! / Let's study well today!</p>
          </div>

          <div className="glass-panel r-card p-4 flex items-center justify-between gap-6 border-l-4 border-secondary hover:scale-[1.02] transition-transform cursor-default relative overflow-hidden shimmer">
            <div className="flex items-center gap-4">
              <span className="text-4xl drop-shadow-[0_0_10px_rgba(255,185,85,0.5)] animate-bob origin-bottom">🔥</span>
              <div>
                <p className="text-secondary font-bold text-lg">{streak} நாட்கள் தொடர்ந்து!</p>
                <p className="text-secondary/70 text-xs font-mono-metadata">{streak}-Day Streak!</p>
              </div>
            </div>
            <div className="flex gap-1.5">
              {[1, 2, 3].map(i => (
                <div key={i}
                  className={`w-2 h-2 rounded-full ${i <= (streak % 4) ? 'bg-secondary shadow-[0_0_8px_rgba(255,185,85,0.8)]' : 'bg-secondary/30'}`}
                />
              ))}
            </div>
          </div>
        </section>

        {/* Hero card - Suggest next lesson */}
        <div className="relative w-full h-64 md:h-80 r-hero overflow-hidden bg-gradient-to-br from-bg-teacher-card to-[#0A1A1E] border border-white/10 p-8 flex items-end group animate-slide-in shadow-[0_20px_50px_rgba(0,0,0,0.4)] texture-noise">
          {/* Decorative drifting orbs + slow halo */}
          <div className="absolute -top-16 -right-10 w-64 h-64 bg-primary-fixed/15 rounded-full blur-[80px] group-hover:bg-primary-fixed/25 transition-colors duration-500" />
          <div className="absolute -bottom-20 left-10 w-56 h-56 bg-studio-purple/10 rounded-full blur-[80px]" />
          <span className="absolute top-8 right-10 text-2xl animate-twinkle">✨</span>
          <span className="absolute top-16 right-24 text-base animate-twinkle delay-300">⭐</span>
          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />
          {/* Ezhilan waves hello from the hero card */}
          <div className="absolute top-4 right-6 z-10 hidden sm:block">
            <EzhilanMoment trigger="wave" size={104} />
          </div>
          <div className="relative z-10 w-full flex flex-col md:flex-row md:items-end justify-between gap-6">
            <div>
              <span className="bg-primary-fixed/20 border border-primary-fixed/30 text-primary-fixed text-xs px-3 py-1 rounded-full font-boldr mb-4 inline-block">
                DAILY GOAL / தினசரி இலக்கு
              </span>
              <h3 className="font-display-tamil text-white font-bold text-3xl md:text-5xl mb-2">
                {nextLesson ? nextLesson.title : 'வாசிப்பு பயிற்சி'}
              </h3>
              <p className="text-text-muted text-base md:text-xl">
                {nextLesson ? `Difficulty: ${nextLesson.difficulty === 3 ? '🌳 Advanced' : nextLesson.difficulty === 2 ? '🌿 Intermediate' : '🌱 Beginner'}` : 'Start reading a new story'}
              </p>
            </div>
            <button onClick={() => navigate('/student/lessons')}
              className="bg-primary-fixed text-bg-deep px-8 py-4 r-chip font-bold text-lg shadow-[0_0_20px_rgba(98,249,238,0.3)] hover:shadow-[0_0_30px_rgba(98,249,238,0.5)] active:scale-95 transition-all flex items-center gap-2 whitespace-nowrap">
              தொடங்கு / Start
              <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>play_circle</span>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Activity grid */}
          <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4 md:gap-6 stagger-children">
            {ACTIVITY_GRID.map(a => (
              <TiltCard key={a.en} onClick={() => navigate(a.to)}
                className={`glass-panel r-card p-6 text-left border-t-2 ${a.border} group relative overflow-hidden cursor-pointer`}>
                <div className={`absolute -top-10 -right-10 w-28 h-28 ${a.bg} rounded-full blur-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-500`} />
                <div className={`${a.bg} w-14 h-14 r-chip flex items-center justify-center mb-4 group-hover:scale-110 group-hover:-rotate-6 transition-transform duration-300`}
                  style={{ transform: 'translateZ(30px)' }}>
                  <span className={`material-symbols-outlined ${a.color} text-3xl`} style={{ fontVariationSettings: "'FILL' 1" }}>{a.icon}</span>
                </div>
                <h4 className="font-display-tamil text-white font-bold text-xl mb-1 relative" style={{ transform: 'translateZ(20px)' }}>{a.ta}</h4>
                <p className="text-text-muted text-sm font-bilingual-sub uppercase tracking-wider relative">{a.en}</p>
                <span className={`material-symbols-outlined ${a.color} absolute bottom-5 right-5 opacity-0 -translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-300`}>arrow_forward</span>
              </TiltCard>
            ))}
          </div>

          {/* Progress ring + recent activity */}
          <section className="glass-panel r-hero p-6 flex flex-col gap-6">
            <div className="flex flex-col items-center">
              {/* COMPLIANCE CHECK: Label shows milestone progress count, no raw percent text is shown directly inside */}
              <ProgressRing 
                percent={progressMetrics.percent} 
                size={100} 
                stroke={8} 
                label={`${progressMetrics.completed} / ${progressMetrics.total} முடிந்தது`} 
              />
              <p className="text-text-muted text-xs font-mono-metadata uppercase tracking-widest mt-2">Classroom Progress</p>
            </div>

            <div className="space-y-3">
              <h3 className="text-white font-bold text-sm flex items-center gap-2 pb-3 border-b border-white/5 uppercase tracking-wider">
                <span className="w-1.5 h-5 bg-primary-fixed rounded-full shadow-[0_0_8px_rgba(98,249,238,0.5)]" />
                Recent Activity
              </h3>
              
              {recentActivities.length === 0 ? (
                <div className="flex flex-col items-center gap-2 py-4">
                  <EmptyActivityArt size={130} className="opacity-90" />
                  <p className="text-xs text-text-muted text-center">செயல்பாடுகள் எதுவும் இல்லை / No recent activity</p>
                </div>
              ) : (
                recentActivities.map((act, i) => (
                  <div key={i} className="bg-black/20 p-3 r-chip flex items-center gap-3 border border-white/5">
                    <div className={`w-10 h-10 r-chip ${act.bg} flex items-center justify-center`}>
                      <span className={`material-symbols-outlined ${act.color}`}>{act.icon}</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between items-center">
                        <h5 className="font-display-tamil text-white text-sm font-bold truncate">{act.title}</h5>
                        <span className="text-xs text-text-muted font-mono-metadata ml-2 flex-shrink-0">{act.timeStr}</span>
                      </div>
                      <p className="text-xs text-text-muted">{act.sub}</p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      </main>

      <button onClick={() => navigate('/student/assessment/start')}
        className="md:hidden fixed right-6 bottom-24 w-14 h-14 bg-primary-fixed text-bg-deep shadow-[0_0_20px_rgba(98,249,238,0.4)] rounded-full flex items-center justify-center hover:scale-110 active:scale-95 transition-all z-40">
        <span className="material-symbols-outlined text-3xl font-bold">play_arrow</span>
      </button>
    </div>
  );
};
