import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { useAuth } from '../../contexts/AuthContext';
import { IdDisplay } from '../../components/ui/IdDisplay';
import type { Table } from 'dexie';
import { db } from '../../db/db';
import { SyncManager } from '../../services/syncManager';

// Widened to Table<any> — the tables have different row types and we only
// touch the shared syncStatus field here.
const CONFLICT_TABLES: { table: Table<any, string>; label: string }[] = [
  { table: db.students, label: 'Students' },
  { table: db.assessments, label: 'Assessments' },
  { table: db.game_sessions, label: 'Game sessions' },
  { table: db.lesson_progress, label: 'Lesson progress' },
];

export const TeacherProfile: React.FC = () => {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  // Rows the server rejected during sync (ownership/validation). They stop
  // retrying automatically — the teacher decides to retry or discard them.
  const conflicts = useLiveQuery(async () => {
    const counts = await Promise.all(
      CONFLICT_TABLES.map(async t => ({
        label: t.label,
        count: await t.table.where('syncStatus').equals('conflict').count(),
      }))
    );
    return counts.filter(c => c.count > 0);
  }, []) || [];

  const retryConflicts = async () => {
    for (const t of CONFLICT_TABLES) {
      await t.table.where('syncStatus').equals('conflict').modify({ syncStatus: 'pending' });
    }
    SyncManager.sync().catch(err => console.error('Retry sync failed:', err));
  };

  const discardConflicts = async () => {
    if (!window.confirm('Discard all conflicted records from this device? This cannot be undone.')) return;
    for (const t of CONFLICT_TABLES) {
      await t.table.where('syncStatus').equals('conflict').delete();
    }
  };

  return (
    <div className="space-y-8 max-w-lg font-body-tamil">
      <div className="animate-fade-in">
        <h1 className="font-display-tamil text-3xl font-bold heading-display">சுயவிவரம்</h1>
        <p className="text-text-muted text-sm mt-1">Teacher Profile</p>
      </div>

      {/* Avatar */}
      <div className="glass-panel card-lift r-hero p-8 flex flex-col items-center gap-4 relative overflow-hidden animate-slide-in">
        <div className="absolute -top-16 left-1/2 -translate-x-1/2 w-64 h-32 bg-teacher-blue/12 blur-3xl rounded-full" />
        <div className="relative w-24 h-24 rounded-full bg-teacher-blue/20 border-2 border-teacher-blue flex items-center justify-center shadow-[0_0_24px_rgba(59,130,246,0.25)]">
          <div className="absolute inset-0 rounded-full bg-teacher-blue/10 blur-md" />
          <span className="relative material-symbols-outlined text-teacher-blue text-4xl" style={{ fontVariationSettings: "'FILL' 1" }}>school</span>
        </div>
        <div className="relative text-center flex flex-col items-center">
          <h2 className="font-display-tamil text-2xl font-bold text-white">{session?.teacherName || session?.name || 'Teacher'}</h2>
          <div className="mt-1">
            <IdDisplay id={session?.teacherId || session?.userId || 'T-0042'} maxLength={8} />
          </div>
          <div className="flex items-center justify-center gap-2 mt-2">
            <span className="w-2 h-2 rounded-full bg-success" />
            <span className="text-success text-xs font-medium">Online</span>
          </div>
        </div>
      </div>

      {/* Info cards */}
      <div className="glass-panel r-card p-6 space-y-4">
        {[
          { icon: 'school',         label: 'School Code',    value: session?.schoolCode || 'SCH-001', isId: true },
          { icon: 'badge',          label: 'Teacher ID',     value: session?.teacherId || session?.userId || 'T-0042', isId: true },
          { icon: 'class',          label: 'Class',          value: 'Class 3B'                                                 },
          { icon: 'location_city',  label: 'School',         value: session?.schoolName || 'Govt. Primary School, Chennai'     },
        ].map(f => (
          <div key={f.label} className="flex items-center gap-4 py-2 border-b border-white/5 last:border-0">
            <div className="w-10 h-10 r-chip bg-bg-surface flex items-center justify-center flex-shrink-0">
              <span className="material-symbols-outlined text-primary-fixed text-xl">{f.icon}</span>
            </div>
            <div className="flex-1">
              <p className="text-text-muted text-xs uppercase tracking-wider">{f.label}</p>
              {f.isId ? (
                <div className="mt-0.5">
                  <IdDisplay id={f.value} maxLength={10} />
                </div>
              ) : (
                <p className="text-white font-semibold">{f.value}</p>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Sync issues — rows the server rejected during sync */}
      {conflicts.length > 0 && (
        <div className="glass-panel r-card p-5 space-y-3 border border-secondary/30">
          <h3 className="text-secondary text-xs uppercase tracking-wider font-bold flex items-center gap-2">
            <span className="material-symbols-outlined text-base">sync_problem</span>
            Sync Issues
          </h3>
          <p className="text-text-muted text-xs">
            These records were rejected by the server (usually because they belong
            to another class). Retry after fixing, or discard them from this device.
          </p>
          <div className="space-y-1">
            {conflicts.map(c => (
              <div key={c.label} className="flex justify-between text-sm">
                <span className="text-on-surface-variant">{c.label}</span>
                <span className="text-secondary font-bold">{c.count}</span>
              </div>
            ))}
          </div>
          <div className="flex gap-3 pt-1">
            <button onClick={retryConflicts}
              className="flex-1 h-10 bg-primary-fixed text-bg-deep font-bold text-sm r-chip active:scale-95 transition-all">
              Retry sync
            </button>
            <button onClick={discardConflicts}
              className="flex-1 h-10 border border-error-text/40 text-error-text font-bold text-sm r-chip hover:bg-error-text/10 transition-colors">
              Discard
            </button>
          </div>
        </div>
      )}

      {/* Settings */}
      <div className="glass-panel r-card p-5 space-y-2">
        <h3 className="text-text-muted text-xs uppercase tracking-wider mb-3">Settings</h3>
        {[
          { icon: 'notifications', label: 'Notifications', action: <div className="w-10 h-5 bg-primary-fixed rounded-full relative"><div className="absolute right-0.5 top-0.5 w-4 h-4 bg-bg-deep rounded-full" /></div> },
          { icon: 'translate',    label: 'Language: தமிழ்', action: <span className="material-symbols-outlined text-text-muted text-base">chevron_right</span> },
          { icon: 'cloud_sync',   label: 'Auto Sync',      action: <div className="w-10 h-5 bg-primary-fixed rounded-full relative"><div className="absolute right-0.5 top-0.5 w-4 h-4 bg-bg-deep rounded-full" /></div> },
        ].map(s => (
          <button key={s.label} className="w-full flex items-center gap-3 p-3 r-chip hover:bg-white/5 transition-colors">
            <span className="material-symbols-outlined text-primary-fixed">{s.icon}</span>
            <span className="text-on-surface flex-1 text-left">{s.label}</span>
            {s.action}
          </button>
        ))}
      </div>

      <button onClick={() => { logout(); navigate('/login'); }}
        className="w-full h-14 border-2 border-error/40 text-error font-bold r-chip hover:bg-error/10 active:scale-[0.98] transition-all flex items-center justify-center gap-2">
        <span className="material-symbols-outlined">logout</span>
        வெளியேறு / Log Out
      </button>
    </div>
  );
};
