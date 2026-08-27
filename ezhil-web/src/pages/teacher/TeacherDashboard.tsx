import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useApiQuery } from '../../hooks/useApi';
import { db } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';
import { SyncManager } from '../../services/syncManager';
import { AnimatedNumber } from '../../components/motion/AnimatedNumber';

interface DashData {
  total_students: number;
  high_risk: number;
  medium_risk: number;
  lessons_published: number;
  avg_quiz_score: number;
}

const MOCK: DashData = { total_students: 0, high_risk: 0, medium_risk: 0, lessons_published: 0, avg_quiz_score: 0 };

/**
 * Supporting metric. Deliberately lower contrast and smaller than the primary
 * card — if every tile shouts, the one that matters cannot be found.
 */
// Full class strings, not `bg-${tone}` — Tailwind scans source statically and
// never generates CSS for an interpolated class name, so the tiles would have
// rendered with no colour at all.
const TILE_TONES = {
  students: 'text-secondary bg-secondary/10',
  lessons:  'text-studio-purple-light bg-studio-purple-light/10',
} as const;

const StatTile: React.FC<{
  icon: string;
  tone: keyof typeof TILE_TONES;
  value: number;
  label: string;
  tamil: string;
  note?: string;
}> = ({ icon, tone, value, label, tamil, note }) => (
  <div className="r-card surface-lit bg-surface-container-low/60 border border-outline-variant/15 p-md flex flex-col justify-between gap-sm">
    <div className="flex items-start justify-between gap-sm">
      <span
        className={`material-symbols-outlined ${TILE_TONES[tone]} r-chip p-1.5 text-xl`}
        style={{ fontVariationSettings: "'FILL' 1" }}
      >
        {icon}
      </span>
      {note && <span className="text-caption text-success font-bold">{note}</span>}
    </div>
    <div>
      <p className="font-display-md text-white font-bold leading-none text-3xl" data-numeric>
        <AnimatedNumber value={value} />
      </p>
      <p className="font-body-sm text-on-surface-variant mt-1">{label}</p>
      <p className="font-tamil-body text-caption text-text-muted">{tamil}</p>
    </div>
  </div>
);

export const TeacherDashboard: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const teacherId = session?.userId || '';
  
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<string | null>(null);

  // 1. Fetch server stats
  const { data, loading, refetch } = useApiQuery<DashData>('/api/v1/dashboard/teacher');

  // 2. Query IndexedDB for offline fallback stats
  const localStats = useLiveQuery(async () => {
    if (!teacherId) return MOCK;
    
    const studentsList = await db.students.where('teacherId').equals(teacherId).toArray();
    const total_students = studentsList.length;
    const high_risk = studentsList.filter(s => s.riskLevel === 'high').length;
    const medium_risk = studentsList.filter(s => s.riskLevel === 'medium').length;
    
    const lessonsList = await db.lessons.toArray();
    const lessons_published = lessonsList.filter(l => l.isPublished).length;

    // Calculate avg quiz score from local student completions
    let sumScore = 0;
    let count = 0;
    const studentIds = studentsList.map(s => s.id);
    const progressList = await db.lesson_progress
      .where('studentId')
      .anyOf(studentIds)
      .filter(p => !!p.completedAt && p.quizScorePercent !== undefined)
      .toArray();

    for (const p of progressList) {
      sumScore += p.quizScorePercent ?? 0;
      count++;
    }

    const avg_quiz_score = count > 0 ? Math.round(sumScore / count) : 0;

    return { total_students, high_risk, medium_risk, lessons_published, avg_quiz_score };
  }, [teacherId]) || MOCK;

  // Real weekly-growth figure for the stat chip (never hardcoded).
  const newThisWeek = useLiveQuery(async () => {
    if (!teacherId) return 0;
    const weekAgo = new Date(Date.now() - 7 * 24 * 3600 * 1000).toISOString();
    const students = await db.students.where('teacherId').equals(teacherId).toArray();
    return students.filter(s => s.createdAt >= weekAgo).length;
  }, [teacherId]) ?? 0;

  // Recent activity from REAL local events (assessments, lessons, roster adds).
  const recentEvents = useLiveQuery(async () => {
    if (!teacherId) return [];
    const students = await db.students.where('teacherId').equals(teacherId).toArray();
    const nameById = new Map(students.map(s => [s.id, s.name]));
    const studentIds = students.map(s => s.id);

    const events: { id: string; ts: string; kind: string; title: string; sub: string; color: string; dot: string }[] = [];

    const assessments = studentIds.length
      ? await db.assessments.where('studentId').anyOf(studentIds).toArray()
      : [];
    for (const a of assessments) {
      events.push({
        id: `a-${a.id}`, ts: a.conductedAt, kind: 'Screening',
        title: nameById.get(a.studentId) ?? 'Student',
        sub: `Read-aloud completed · ${new Date(a.conductedAt).toLocaleDateString()}`,
        color: 'text-secondary', dot: 'bg-secondary',
      });
    }
    const lessons = await db.lessons.toArray();
    for (const l of lessons.filter(l => l.isPublished).slice(-3)) {
      events.push({
        id: `l-${l.id}`, ts: l.createdAt, kind: 'Lesson Published',
        title: l.title, sub: new Date(l.createdAt).toLocaleDateString(),
        color: 'text-studio-purple-light', dot: 'bg-studio-purple-light',
      });
    }
    for (const s of students.slice(-3)) {
      events.push({
        id: `s-${s.id}`, ts: s.createdAt, kind: 'Student Added',
        title: s.name, sub: new Date(s.createdAt).toLocaleDateString(),
        color: 'text-success', dot: 'bg-success',
      });
    }

    return events.sort((a, b) => b.ts.localeCompare(a.ts)).slice(0, 3);
  }, [teacherId]) ?? [];

  // 3. Query high/medium risk students for "Attention Needed" panel with live assessment metrics
  const attentionStudents = useLiveQuery(async () => {
    if (!teacherId) return [];
    
    const high = await db.students.where('riskLevel').equals('high').filter(s => s.teacherId === teacherId).toArray();
    const medium = await db.students.where('riskLevel').equals('medium').filter(s => s.teacherId === teacherId).toArray();
    
    const combined = [...high, ...medium];
    
    const list = [];
    for (const s of combined.slice(0, 4)) {
      // Find latest assessment
      const latestAssessment = await db.assessments
        .where('studentId')
        .equals(s.id)
        .sortBy('conductedAt');
      
      const lastAss = latestAssessment[latestAssessment.length - 1];
      
      // Calculate display metric
      let metricLabel = 'No test';
      let scorePercent = 0;
      if (lastAss) {
        // Use phoneme error rate or reading speed as visual gauge
        scorePercent = Math.round((lastAss.phonemeErrorRate || 0) * 100);
        metricLabel = `Phoneme Error: ${scorePercent}%`;
      } else {
        // No assessment yet — say so; never invent a number.
        scorePercent = 0;
        metricLabel = 'Awaiting screening';
      }

      list.push({
        id: s.id,
        name: s.name,
        initials: s.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase(),
        risk: s.riskLevel,
        metricLabel,
        scorePercent,
        riskLabel: s.riskLevel === 'high' ? 'HIGH RISK' : 'MEDIUM RISK',
        color: s.riskLevel === 'high' ? 'text-risk-high' : 'text-risk-medium',
        bg: s.riskLevel === 'high' ? 'bg-risk-high/10' : 'bg-risk-medium/10',
        border: s.riskLevel === 'high' ? 'border-risk-high/20' : 'border-risk-medium/20',
        barColor: s.riskLevel === 'high' ? 'bg-risk-high' : 'bg-risk-medium',
      });
    }
    return list;
  }, [teacherId]) || [];

  const handleManualSync = async () => {
    setSyncing(true);
    setSyncResult(null);
    try {
      const res = await SyncManager.sync();
      if (res.success) {
        setSyncResult(`Synced successfully! pushed: ${res.pushed}, pulled: ${res.pulled}`);
        refetch(); // Reload server stats
      } else {
        setSyncResult(`Sync failed: ${res.error}`);
      }
    } catch (err: any) {
      setSyncResult(`Sync failed: ${err.message || err}`);
    } finally {
      setSyncing(false);
      setTimeout(() => setSyncResult(null), 5000);
    }
  };

  const d = data ?? localStats;

  return (
    <div className="space-y-lg animate-fade-in font-body-tamil select-none">
      {/* Greeting Header */}
      <section className="flex flex-col md:flex-row md:items-end justify-between gap-md border-b border-outline-variant/10 pb-md">
        <div>
          <h3 className="font-display-md text-display-md text-white flex items-baseline gap-sm flex-wrap">
            <span className="font-tamil-reader font-bold">
              {new Date().getHours() < 12 ? 'காலை வணக்கம்' : new Date().getHours() < 17 ? 'மதிய வணக்கம்' : 'மாலை வணக்கம்'},{' '}
              {session?.name?.split(' ')[0] || 'ஆசிரியர்'}!
            </span>
            <span className="text-xl text-on-surface-variant font-medium">
              / {new Date().getHours() < 12 ? 'Good morning' : new Date().getHours() < 17 ? 'Good afternoon' : 'Good evening'}
            </span>
          </h3>
          <p className="text-on-surface-variant text-sm mt-1">Here's what's happening with your students today.</p>
        </div>
        <div className="flex items-center gap-sm">
          <button 
            onClick={handleManualSync} 
            disabled={syncing}
            className="glass-card px-md py-sm r-chip flex items-center gap-sm text-primary-fixed hover:bg-white/5 transition-all disabled:opacity-50 font-bold text-xs uppercase cursor-pointer"
          >
            <span className={`material-symbols-outlined text-base ${syncing ? 'animate-spin' : ''}`}>sync</span>
            <span>{syncing ? 'Syncing...' : 'Sync Now'}</span>
          </button>
          <button 
            onClick={() => navigate('/teacher/roster')}
            className="bg-primary-fixed text-bg-deep px-lg py-sm r-chip font-bold text-xs uppercase shadow-[0_0_16px_rgba(98,249,238,0.25)] hover:shadow-[0_0_24px_rgba(98,249,238,0.4)] transition-all flex items-center gap-sm hover:-translate-y-0.5 cursor-pointer"
          >
            <span className="material-symbols-outlined text-base">add</span> Add Student
          </button>
        </div>
      </section>

      {/* Sync result notification banner */}
      {syncResult && (
        <div className="p-sm bg-white/5 r-chip border border-white/10 text-xs font-semibold text-center text-primary-fixed animate-fade-in">
          {syncResult}
        </div>
      )}

      {/* Stats — deliberately asymmetric. Three identical cards in a row is the
          most generic dashboard layout there is, and it also lies: these three
          numbers are not equally important. A teacher opens this to find out
          who needs help, so that number gets the weight and the other two
          sit beside it as supporting context. */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-lg stagger-children">

        {/* Primary — students needing attention */}
        <section
          className={`lg:col-span-3 relative overflow-hidden r-hero surface-lit grain p-lg md:p-xl flex flex-col justify-between min-h-[190px] ${
            d.high_risk > 0
              ? 'bg-risk-high/[0.07] border border-risk-high/25'
              : 'bg-success/[0.06] border border-success/20'
          }`}
        >
          <div
            aria-hidden
            className={`absolute -top-20 -right-16 w-56 h-56 rounded-full blur-3xl pointer-events-none ${
              d.high_risk > 0 ? 'bg-risk-high/15' : 'bg-success/12'
            }`}
          />
          <div className="relative flex items-start justify-between gap-md">
            <span
              className={`material-symbols-outlined r-chip p-sm ${
                d.high_risk > 0 ? 'text-risk-high bg-risk-high/12' : 'text-success bg-success/12'
              }`}
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              {d.high_risk > 0 ? 'priority_high' : 'check_circle'}
            </span>
            {d.high_risk > 0 && (
              <span className="text-caption font-caption text-error-text font-bold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-error-text animate-pulse" />
                Needs review today
              </span>
            )}
          </div>

          <div className="relative mt-lg flex items-end gap-md flex-wrap">
            <span
              className="font-display-lg font-bold text-white leading-[0.85] tracking-tight"
              style={{ fontSize: 'clamp(3.25rem, 9vw, 5rem)' }}
              data-numeric
            >
              <AnimatedNumber value={d.high_risk} />
            </span>
            <div className="pb-1">
              <p className="font-body-sm text-on-surface font-bold">
                {d.high_risk === 1 ? 'student needs attention' : 'students need attention'}
              </p>
              <p className="font-tamil-body text-caption text-text-muted mt-0.5">கவனம் தேவை</p>
            </div>
          </div>

          {d.medium_risk > 0 && (
            <p className="relative mt-md text-caption text-on-surface-variant">
              {d.medium_risk} more at medium risk
            </p>
          )}
        </section>

        {/* Secondary — quieter by design, stacked beside the primary */}
        <div className="lg:col-span-2 grid grid-cols-2 lg:grid-cols-1 gap-lg">
          <StatTile
            icon="group"
            tone="students"
            value={d.total_students}
            label="Students"
            tamil="மாணவர்கள்"
            note={newThisWeek > 0 ? `+${newThisWeek} this week` : undefined}
          />
          <StatTile
            icon="auto_stories"
            tone="lessons"
            value={d.lessons_published}
            label="Lessons live"
            tamil="பாடங்கள்"
          />
        </div>
      </div>

      {/* Two Column Layout: Attention Table + Recent Activity timeline */}
      <div className="flex flex-col lg:flex-row gap-lg">
        
        {/* Left Col (2/3): Attention List Table */}
        <section className="lg:w-2/3 flex flex-col gap-md">
          <div className="flex justify-between items-center mb-xs">
            <h3 className="font-headline-sm text-headline-sm text-white font-bold flex items-center gap-sm">
              <span>Attention Needed</span>
              <span className="text-sm text-on-surface-variant font-tamil-body font-normal">/ கவனம் தேவை</span>
            </h3>
            <button 
              onClick={() => navigate('/teacher/roster')} 
              className="text-primary-fixed font-bold text-xs uppercase hover:underline"
            >
              View Roster
            </button>
          </div>
          
          <div className="glass-card r-chip overflow-hidden border border-outline-variant/20 shadow-lg overflow-x-auto">
            <table className="w-full text-left border-collapse min-w-[600px]">
              <thead>
                <tr className="bg-surface-container-high/50 border-b border-outline-variant/30">
                  <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider">Student Name</th>
                  <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider">Risk Level</th>
                  <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider">Assessment Metric</th>
                  <th className="px-lg py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/10">
                {attentionStudents.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-lg py-xl text-center text-text-muted">
                      கவனம் தேவைப்படும் மாணவர்கள் இல்லை / No students need attention.
                    </td>
                  </tr>
                ) : (
                  attentionStudents.map(s => (
                    <tr key={s.id} className="hover:bg-surface-variant/20 transition-colors group">
                      <td className="px-lg py-md">
                        <div className="flex items-center gap-md">
                          <div className={`w-8 h-8 rounded-full border bg-bg-deep flex items-center justify-center font-bold text-xs ${
                            s.risk === 'high' ? 'border-risk-high text-risk-high bg-risk-high/5' : 'border-risk-medium text-risk-medium bg-risk-medium/5'
                          }`}>
                            {s.initials}
                          </div>
                          <span className="font-body-sm font-bold text-white group-hover:text-primary-fixed transition-colors">{s.name}</span>
                        </div>
                      </td>
                      <td className="px-lg py-md">
                        <span className={`flex items-center gap-sm text-xs font-bold ${s.color} px-md py-1 rounded-full ${s.bg} border ${s.border} w-fit`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${s.risk === 'high' ? 'bg-risk-high' : 'bg-risk-medium'}`}></span>
                          {s.riskLabel}
                        </span>
                      </td>
                      <td className="px-lg py-md">
                        <div className="flex flex-col">
                          <span className="font-mono-metadata text-xs text-white">{s.metricLabel}</span>
                          <div className="w-24 bg-surface-container-highest h-1 rounded-full mt-xs overflow-hidden">
                            <div className={`h-full ${s.barColor}`} style={{ width: `${s.scorePercent}%` }}></div>
                          </div>
                        </div>
                      </td>
                      <td className="px-lg py-md text-right">
                        <button 
                          onClick={() => navigate('/teacher/roster')}
                          className="bg-primary-fixed/10 text-primary-fixed hover:bg-primary-fixed hover:text-bg-deep px-md py-1.5 rounded text-xs font-bold transition-all duration-200 cursor-pointer"
                        >
                          Review
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* Right Col (1/3): Recent Activity Feed timeline */}
        <section className="lg:w-1/3 flex flex-col gap-md">
          <div className="flex justify-between items-center mb-xs">
            <h3 className="font-headline-sm text-headline-sm text-white font-bold flex items-center gap-sm">
              <span>Recent Activity</span>
              <span className="text-sm text-on-surface-variant font-tamil-body font-normal">/ செயல்பாடு</span>
            </h3>
          </div>
          
          <div className="glass-card r-chip p-lg space-y-lg flex-1 border border-outline-variant/20 shadow-lg flex flex-col justify-between">
            {recentEvents.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center gap-2 py-6 text-center">
                <span className="text-3xl">🌱</span>
                <p className="text-on-surface-variant text-sm font-bold">இன்னும் செயல்பாடு இல்லை</p>
                <p className="text-text-muted text-xs">Activity appears here as students read and play.</p>
              </div>
            ) : (
              <div className="relative pl-lg border-l-2 border-outline-variant/30 space-y-lg mt-sm">
                {recentEvents.map(ev => (
                  <div key={ev.id} className="relative animate-fade-in">
                    <span className={`absolute -left-[30px] top-1 w-3 h-3 rounded-full ${ev.dot} border-2 border-bg-surface`}></span>
                    <div className="flex flex-col">
                      <span className={`text-caption font-caption ${ev.color} font-bold font-mono`}>{ev.kind}</span>
                      <p className="font-body-sm font-bold text-white mt-xs">{ev.title}</p>
                      <p className="text-xs text-text-muted mt-0.5">{ev.sub}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}

            <button
              onClick={() => navigate('/teacher/roster')}
              className="w-full mt-lg py-md border border-outline-variant/30 r-chip text-body-sm font-bold hover:bg-surface-variant hover:text-white transition-all flex items-center justify-center gap-sm active:scale-98 cursor-pointer"
            >
              <span className="material-symbols-outlined text-lg">group</span>
              <span>View Roster</span>
            </button>
          </div>
        </section>

      </div>
    </div>
  );
};
