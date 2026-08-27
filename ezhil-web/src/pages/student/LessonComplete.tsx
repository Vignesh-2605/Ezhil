import React, { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';

export const LessonComplete: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { session } = useAuth();

  const { lessonId, score } = (location.state as { lessonId?: string; score?: number }) ?? {};

  useEffect(() => {
    const saveProgress = async () => {
      if (!session?.studentId || !lessonId) return;
      try {
        const studentId = session.studentId;
        const progressId = `${studentId}_${lessonId}`;
        const existing = await db.lesson_progress.get(progressId);
        
        await db.lesson_progress.put({
          id: progressId,
          studentId,
          lessonId,
          completedAt: new Date().toISOString(),
          quizScorePercent: score !== undefined ? score : (existing?.quizScorePercent ?? 3),
          syncStatus: 'pending',
          createdAt: existing?.createdAt ?? new Date().toISOString()
        });
      } catch (err) {
        console.error('Failed to save lesson progress:', err);
      }
    };
    saveProgress();
  }, [session, lessonId, score]);

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      {/* Ambient glows */}
      <div className="orb w-96 h-96 bg-primary-fixed/10 top-[-4rem] left-1/2 -translate-x-1/2" />
      <div className="orb w-72 h-72 bg-secondary/8 bottom-0 left-0" />

      <div className="relative flex items-center justify-center">
        <div className="absolute w-48 h-48 rounded-full bg-primary-fixed/10 blur-2xl animate-pulse" />
        <div className="relative text-[100px] animate-bob origin-bottom drop-shadow-[0_0_30px_rgba(98,249,238,0.4)]">🎓</div>
      </div>
      <div className="text-center space-y-3 relative animate-pop-in">
        <h1 className="font-display-tamil text-4xl font-bold heading-display-accent">பாடம் முடிந்தது!</h1>
        <p className="text-on-surface-variant text-xl">Lesson Complete!</p>
        <div className="flex gap-2 justify-center">
          {[1,2,3].map(n => (
            <span key={n} className="material-symbols-outlined text-4xl text-secondary drop-shadow-[0_0_8px_rgba(255,185,85,0.6)] animate-pop-in" style={{ fontVariationSettings: "'FILL' 1", animationDelay: `${n * 160}ms` }}>star</span>
          ))}
        </div>
      </div>
      <div className="flex gap-4 relative">
        <div className="glass-panel card-lift r-chip px-6 py-3 text-center">
          <p className="font-mono-metadata text-primary-fixed font-bold text-2xl">+30 XP</p>
          <p className="text-text-muted text-xs">Experience</p>
        </div>
        <div className="glass-panel card-lift r-chip px-6 py-3 text-center">
          <p className="font-mono-metadata text-secondary font-bold text-2xl">8🔥</p>
          <p className="text-text-muted text-xs">Streak</p>
        </div>
      </div>
      <div className="w-full max-w-xs space-y-3 relative">
        <button onClick={() => navigate('/student/achievement?type=lesson&stars=3')}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all cursor-pointer">
          View Achievement
        </button>
        <button onClick={() => navigate('/student/lessons')}
          className="w-full h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors cursor-pointer">
          More Lessons
        </button>
      </div>
    </div>
  );
};
