import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';

/**
 * Student-facing history. Pedagogical rule: children never see risk levels,
 * scores, or error rates — only effort (sessions completed, time spent reading).
 */
export const AssessmentHistory: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';

  const assessments = useLiveQuery(async () => {
    if (!studentId) return [];
    const list = await db.assessments.where('studentId').equals(studentId).toArray();
    return list.sort((a, b) => b.conductedAt.localeCompare(a.conductedAt));
  }, [studentId]) || [];

  const totalMinutes = Math.max(
    assessments.length > 0 ? 1 : 0,
    Math.round(assessments.reduce((sum, a) => sum + (a.audioDurationMs || 0), 0) / 60000)
  );
  const monthPrefix = new Date().toISOString().slice(0, 7); // "2026-07"
  const thisMonth = assessments.filter(a => a.conductedAt.startsWith(monthPrefix)).length;

  const fmtDate = (iso: string) => {
    try {
      return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    } catch {
      return iso.slice(0, 10);
    }
  };

  return (
    <div className="space-y-6 max-w-lg mx-auto font-body-tamil">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display-tamil text-3xl font-bold text-white">என் வாசிப்புகள்</h1>
          <p className="text-text-muted text-sm mt-1">My Reading Sessions</p>
        </div>
        <button onClick={() => navigate('/student/assessment/start')}
          className="bg-primary-fixed text-bg-deep font-bold text-sm px-4 py-2 r-chip active:scale-95 transition-all">
          New
        </button>
      </div>

      {/* Effort summary — sessions and practice time, never scores */}
      <div className="grid grid-cols-3 gap-2 sm:gap-3">
        {[
          { icon: 'menu_book', value: `${assessments.length}`, label: 'மொத்தம் / Total' },
          { icon: 'timer', value: `${totalMinutes} min`, label: 'பயிற்சி / Practice' },
          { icon: 'calendar_month', value: `${thisMonth}`, label: 'இந்த மாதம் / This month' },
        ].map(s => (
          <div key={s.icon} className="glass-panel r-card p-4 flex flex-col items-center gap-1 text-center">
            <span className="material-symbols-outlined text-primary-fixed">{s.icon}</span>
            <span className="font-mono-metadata font-bold text-white text-lg">{s.value}</span>
            <span className="text-text-muted text-xs leading-tight">{s.label}</span>
          </div>
        ))}
      </div>

      {assessments.length === 0 ? (
        <div className="glass-panel r-card p-8 flex flex-col items-center gap-3 text-center">
          <span className="text-4xl">📖</span>
          <p className="text-white font-bold">இன்னும் வாசிப்புகள் இல்லை</p>
          <p className="text-text-muted text-sm">Start a Read Aloud session to see your reading journey here.</p>
          <button onClick={() => navigate('/student/assessment/start')}
            className="mt-2 bg-primary-fixed text-bg-deep font-bold text-sm px-5 py-2.5 r-chip active:scale-95 transition-all">
            🎤 படிக்க ஆரம்பி / Start Reading
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {assessments.map((a, i) => (
            <div key={a.id} className="glass-panel r-chip p-4 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 r-chip bg-bg-surface flex items-center justify-center">
                  <span className="font-mono-metadata font-bold text-primary-fixed">#{assessments.length - i}</span>
                </div>
                <div>
                  <p className="font-mono-metadata text-white font-medium">{fmtDate(a.conductedAt)}</p>
                  <p className="text-text-muted text-xs mt-0.5">
                    🎤 {Math.round((a.audioDurationMs || 0) / 1000)}s reading
                  </p>
                </div>
              </div>
              {/* Completion badge — every session is a win */}
              <span className="flex items-center gap-1 text-success text-xs font-bold bg-success/10 border border-success/30 rounded-full px-3 py-1">
                <span className="material-symbols-outlined text-sm">check_circle</span>
                முடிந்தது
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
