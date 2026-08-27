import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { useLiveQuery } from 'dexie-react-hooks';
import { SyncManager } from '../../services/syncManager';
import { PageLoading } from '../../components/ui/LoadingSpinner';
import { EmptyState } from '../../components/ui/EmptyState';

interface EnrichedLesson {
  id: string;
  title: string;
  difficulty: number;
  isCompleted: boolean;
  completionPct?: number; // kept for visual progress bar length only
}

const DIFF_COLORS: Record<number, string> = {
  1: 'text-success border-success bg-success/10',
  2: 'text-secondary border-secondary bg-secondary/10',
  3: 'text-error border-error bg-error/10',
};

const DIFF_LABELS: Record<number, string> = {
  1: 'Beginner',
  2: 'Intermediate',
  3: 'Advanced'
};

export const StudentLessonsList: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';
  const [filter, setFilter] = useState<'all' | 'beginner' | 'intermediate' | 'advanced'>('all');
  const [syncing, setSyncing] = useState(false);

  // Sync lessons on mount
  useEffect(() => {
    const triggerSync = async () => {
      setSyncing(true);
      try {
        await SyncManager.sync();
      } catch (err) {
        console.error('Lessons sync failed:', err);
      } finally {
        setSyncing(false);
      }
    };
    triggerSync();
  }, []);

  // Live query lessons and enrich with student progress
  const lessons = useLiveQuery(async () => {
    if (!studentId) return [] as EnrichedLesson[];
    
    // Only query published lessons
    const list = await db.lessons.where('isPublished').equals(1).toArray();
    const enriched: EnrichedLesson[] = [];

    for (const l of list) {
      const progress = await db.lesson_progress
        .where('studentId')
        .equals(studentId)
        .filter(p => p.lessonId === l.id)
        .first();

      const isCompleted = !!progress?.completedAt;
      const completionPct = isCompleted ? 100 : (progress?.quizScorePercent !== undefined ? Math.round(progress.quizScorePercent * 100) : 0);

      enriched.push({
        id: l.id,
        title: l.title,
        difficulty: l.difficulty,
        isCompleted,
        completionPct
      });
    }

    return enriched;
  }, [studentId]) || [];

  const filtered = lessons.filter(l => {
    if (filter === 'all') return true;
    const diffStr = DIFF_LABELS[l.difficulty]?.toLowerCase();
    return diffStr === filter;
  });

  if (lessons.length === 0 && syncing) return <PageLoading />;

  return (
    <div className="space-y-6 font-body-tamil">
      <div className="flex justify-between items-center animate-fade-in">
        <div className="flex items-center gap-3">
          <span className="text-3xl animate-bob origin-bottom">📚</span>
          <div>
            <h1 className="font-display-tamil text-3xl font-bold heading-display-accent">என் பாடங்கள்</h1>
            <p className="text-text-muted text-sm mt-1">My Lessons · {lessons.length} available</p>
          </div>
        </div>

        {syncing && (
          <span className="text-xs text-primary-fixed flex items-center gap-1">
            <span className="material-symbols-outlined text-sm animate-spin">sync</span>
            Updating...
          </span>
        )}
      </div>

      {/* Filter pills */}
      <div className="flex gap-2 overflow-x-auto hide-scrollbar pb-1">
        {(['all', 'beginner', 'intermediate', 'advanced'] as const).map(f => (
          <button key={f} onClick={() => setFilter(f)}
            className={`whitespace-nowrap px-4 py-2 rounded-full text-sm font-semibold transition-all capitalize active:scale-95 ${
              filter === f ? 'bg-primary-fixed text-bg-deep shadow-[0_0_18px_rgba(98,249,238,0.4)]' : 'border border-white/15 text-text-muted hover:border-primary-fixed/50 hover:text-primary-fixed'
            }`}>
            {f === 'all' ? 'All' : f.charAt(0).toUpperCase() + f.slice(1)}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <EmptyState art="lessons" title="No lessons yet" subtitle="Your teacher will publish lessons soon." />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 stagger-children">
          {filtered.map(lesson => (
            <button key={lesson.id} onClick={() => navigate('/student/lesson/reader', { state: { lessonId: lesson.id } })}
              className="glass-panel card-lift r-card p-5 text-left flex flex-col gap-3 group active:scale-[0.98] relative overflow-hidden">
              <div className="absolute -top-10 -right-10 w-24 h-24 bg-primary-fixed/10 rounded-full blur-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
              <div className="flex items-start justify-between relative">
                <div className={`text-xs px-2.5 py-1 rounded-full font-semibold border capitalize ${DIFF_COLORS[lesson.difficulty] || DIFF_COLORS[1]}`}>
                  {DIFF_LABELS[lesson.difficulty] || 'Beginner'}
                </div>
                {lesson.isCompleted && (
                  <span className="material-symbols-outlined text-success text-xl" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
                )}
              </div>
              
              <h3 className="relative font-display-tamil text-white font-bold text-lg leading-tight group-hover:text-primary-fixed transition-colors">
                {lesson.title}
              </h3>

              {/* COMPLIANCE CHECK: Visual progress bar is shown but the numeric percentage string is removed */}
              {lesson.completionPct !== undefined && lesson.completionPct > 0 && !lesson.isCompleted && (
                <div className="space-y-1 w-full">
                  <div className="flex justify-between text-xs text-text-muted">
                    <span>Progress</span>
                  </div>
                  <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                    <div className="h-full bg-primary-fixed rounded-full" style={{ width: `${lesson.completionPct}%` }} />
                  </div>
                </div>
              )}
              
              <div className="relative flex items-center gap-2 text-primary-fixed text-sm font-semibold mt-auto group-hover:gap-3 transition-all">
                <span className="material-symbols-outlined text-base" style={{ fontVariationSettings: "'FILL' 1" }}>play_circle</span>
                {lesson.isCompleted ? 'Read Again' : (lesson.completionPct ? 'Continue' : 'Start Lesson')}
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
