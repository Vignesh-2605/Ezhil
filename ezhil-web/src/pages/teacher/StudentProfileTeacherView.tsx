import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { RiskBadge } from '../../components/ui/RiskBadge';
import { ProgressRing } from '../../components/ui/ProgressRing';
import { PageLoading } from '../../components/ui/LoadingSpinner';
import { EmptyState } from '../../components/ui/EmptyState';

interface Screening {
  date:         string;
  risk:         string;
  stars:        number;
  modelVersion: string;
}

interface Profile {
  name:        string;
  dob?:        string;
  risk:        string;
  lastActive:  string;
  streakDays:  number;
  /** Real completion counts — no synthetic skill breakdown. */
  lessonsDone:   number;
  lessonsTotal:  number;
  gamesPlayed:   number;
  avgQuizPct:    number | null;
  history:       Screening[];
}

const fmt = (iso?: string | null) => {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
};

/** Stars from the screening's own risk level — the child-facing encoding
 *  already used elsewhere. Not a score, and never shown to the student here. */
const starsForRisk = (risk: string) => (risk === 'low' ? 3 : risk === 'medium' ? 2 : 1);

export const StudentProfileTeacherView: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const studentId = (location.state as { studentId?: string } | null)?.studentId;

  const profile = useLiveQuery(async (): Promise<Profile | null> => {
    if (!studentId) return null;
    const s = await db.students.get(studentId);
    if (!s) return null;

    const assessments = await db.assessments.where('studentId').equals(studentId).sortBy('conductedAt');
    const progress    = await db.lesson_progress.where('studentId').equals(studentId).toArray();
    const games       = await db.game_sessions.where('studentId').equals(studentId).count();
    const lessonsTotal = await db.lessons.where('isPublished').equals(1).count();

    const scored = progress.filter(p => p.quizScorePercent !== undefined);
    const avgQuizPct = scored.length
      ? Math.round(scored.reduce((a, p) => a + (p.quizScorePercent ?? 0), 0) / scored.length * 100)
      : null;

    return {
      name:       s.name,
      dob:        s.dob,
      risk:       s.riskLevel,
      lastActive: fmt(s.lastActive),
      streakDays: s.streakDays,
      lessonsDone:  progress.filter(p => !!p.completedAt).length,
      lessonsTotal,
      gamesPlayed:  games,
      avgQuizPct,
      history: [...assessments].reverse().map(a => ({
        date:         fmt(a.conductedAt),
        risk:         a.riskLevel,
        stars:        starsForRisk(a.riskLevel),
        modelVersion: a.modelVersion,
      })),
    };
  }, [studentId]);

  if (!studentId) {
    return (
      <div className="max-w-2xl font-body-tamil space-y-6">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-text-muted hover:text-white transition-colors">
          <span className="material-symbols-outlined">arrow_back</span> Back to Roster
        </button>
        <EmptyState art="students"
          title="No student selected"
          subtitle="Open a student from the roster or a risk flag to see their profile." />
      </div>
    );
  }

  if (profile === undefined) return <PageLoading />;

  if (profile === null) {
    return (
      <div className="max-w-2xl font-body-tamil space-y-6">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-text-muted hover:text-white transition-colors">
          <span className="material-symbols-outlined">arrow_back</span> Back to Roster
        </button>
        <EmptyState art="students"
          title="Student not found"
          subtitle="This student is no longer in your local roster. Sync and try again." />
      </div>
    );
  }

  const lessonPct = profile.lessonsTotal > 0
    ? Math.round((profile.lessonsDone / profile.lessonsTotal) * 100)
    : 0;

  return (
    <div className="space-y-8 max-w-2xl font-body-tamil pb-8">
      <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-text-muted hover:text-white transition-colors">
        <span className="material-symbols-outlined">arrow_back</span> Back to Roster
      </button>

      {/* Profile header */}
      <section className="flex flex-col sm:flex-row items-center sm:items-start gap-6 glass-panel r-card surface-lit p-6">
        <div className="relative flex-shrink-0">
          <div className="w-24 h-24 rounded-full bg-bg-surface border-2 border-accent-teal flex items-center justify-center">
            <span className="font-display-tamil text-4xl text-primary-fixed">
              {profile.name.trim().charAt(0) || '?'}
            </span>
          </div>
          <div className={`absolute bottom-0 right-0 w-5 h-5 rounded-full border-2 border-bg-deep ${
            profile.risk === 'high' ? 'bg-risk-high' : profile.risk === 'medium' ? 'bg-risk-medium' : 'bg-success'
          }`} />
        </div>
        <div className="flex-1 text-center sm:text-left">
          <h2 className="font-display-tamil heading-display text-3xl">{profile.name}</h2>
          <p className="text-text-muted text-sm mt-1">Last active: {profile.lastActive}</p>
          {profile.dob && <p className="text-text-muted text-xs mt-0.5">DOB: {profile.dob}</p>}
          <div className="mt-3 flex items-center gap-3 justify-center sm:justify-start flex-wrap">
            <RiskBadge level={profile.risk} />
            {profile.risk === 'unscreened' && (
              <span className="text-xs text-text-muted border border-white/10 px-2 py-0.5 rounded-full">Needs screening</span>
            )}
            {profile.streakDays > 0 && (
              <span className="text-xs text-tertiary-fixed-dim border border-tertiary-fixed-dim/30 px-2 py-0.5 rounded-full">
                🔥 {profile.streakDays}-day streak
              </span>
            )}
          </div>
        </div>
      </section>

      {/* Activity — counted from stored records, not modelled from risk level */}
      <section className="glass-panel r-card surface-lit p-6 space-y-4">
        <h3 className="font-dashboard-title heading-display text-lg">Activity</h3>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="flex flex-col items-center gap-2">
            <ProgressRing percent={lessonPct} size={80} stroke={7} color="#62F9EE" />
            <p className="font-display-tamil text-white text-sm font-bold text-center">பாடங்கள்</p>
            <p className="text-text-muted text-xs font-mono-metadata">
              {profile.lessonsDone}/{profile.lessonsTotal} lessons
            </p>
          </div>
          <div className="flex flex-col items-center gap-2">
            <ProgressRing percent={profile.avgQuizPct ?? 0} size={80} stroke={7} color="#FB8C00" />
            <p className="font-display-tamil text-white text-sm font-bold text-center">வினாடி வினா</p>
            <p className="text-text-muted text-xs font-mono-metadata">
              {profile.avgQuizPct === null ? 'no quizzes yet' : `${profile.avgQuizPct}% average`}
            </p>
          </div>
          <div className="flex flex-col items-center gap-2">
            <div className="w-20 h-20 rounded-full border-[7px] border-studio-purple/30 flex items-center justify-center">
              <span className="font-mono-metadata text-2xl font-bold text-studio-purple">{profile.gamesPlayed}</span>
            </div>
            <p className="font-display-tamil text-white text-sm font-bold text-center">விளையாட்டு</p>
            <p className="text-text-muted text-xs font-mono-metadata">sessions played</p>
          </div>
        </div>
      </section>

      {/* Screening history */}
      <section className="glass-panel r-card surface-lit p-6 space-y-3">
        <h3 className="font-dashboard-title heading-display text-lg">Screening History</h3>
        {profile.history.length === 0 ? (
          <p className="text-text-muted text-sm py-4 text-center">
            No screenings yet. Run the first assessment below.
          </p>
        ) : (
          profile.history.map((h, i) => (
            <div key={i} className={`flex items-center justify-between p-4 r-chip border-l-4 bg-black/20 ${
              h.risk === 'high' ? 'border-risk-high' : h.risk === 'medium' ? 'border-risk-medium' : 'border-risk-low'
            }`}>
              <div>
                <p className="font-mono-metadata text-on-surface text-sm">{h.date}</p>
                <div className="flex gap-0.5 mt-1">
                  {[1, 2, 3].map(n => (
                    <span key={n} className={`material-symbols-outlined text-sm ${n <= h.stars ? 'text-tertiary-fixed-dim' : 'text-white/15'}`}
                      style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
                  ))}
                </div>
                <p className="text-text-muted text-xs font-mono-metadata mt-1">{h.modelVersion}</p>
              </div>
              <RiskBadge level={h.risk} compact />
            </div>
          ))
        )}
        <p className="text-text-muted text-xs border-t border-white/5 pt-3">
          Screening results are heuristic estimates for a teacher's attention, not a diagnosis.
        </p>
      </section>

      <div className="space-y-3">
        <button onClick={() => navigate('/student/assessment/start')}
          className="w-full h-14 r-chip border-2 border-primary-fixed text-primary-fixed flex items-center justify-center gap-2 font-bold active:scale-95 transition-all hover:bg-primary-fixed/10">
          <span className="material-symbols-outlined">mic</span> Run Assessment
        </button>
      </div>
    </div>
  );
};
