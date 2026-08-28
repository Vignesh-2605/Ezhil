import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { useLiveQuery } from 'dexie-react-hooks';
import { useApiQuery } from '../../hooks/useApi';
import { IdDisplay } from '../../components/ui/IdDisplay';

/** The teacher dashboard payload. Only the lesson count is used here. */
interface TeacherDashData { lessons_published: number; }

/** Selectable reporting windows. `days: null` means no date filtering. */
const DATE_RANGES = [
  { key: 'all', label: 'All time / எல்லா காலமும்', days: null },
  { key: '7',   label: 'Last 7 days',              days: 7    },
  { key: '30',  label: 'Last 30 days',             days: 30   },
  { key: '90',  label: 'Last 90 days',             days: 90   },
] as const;

/**
 * Risk bands for the per-metric distribution bars.
 *
 * `phoneme` and `syllable` reproduce cut-offs already used elsewhere in the
 * product, so the same assessment is banded identically wherever it is shown:
 *   - phoneme 0.40 / 0.20 — StudentDetailScreen.kt:308-309
 *   - syllable 0.30       — screeningHeuristic.ts:73 (the syllable_skip tag)
 *
 * The two marked PROVISIONAL have no established cut-off anywhere in the
 * codebase. They are a starting point for a clinician to correct, not a
 * validated instrument — change them here and every bar follows.
 */
const RISK_BANDS = {
  // Higher error rate is worse.
  phoneme:  { high: 0.40, medium: 0.20 },
  // Higher skip rate is worse. `medium` is PROVISIONAL.
  syllable: { high: 0.30, medium: 0.15 },
  // Lower words-per-minute is worse. Both values are PROVISIONAL.
  wpm:      { high: 30,   medium: 60   },
};

type Band = { high: number; medium: number; low: number; total: number };

const emptyBand = (): Band => ({ high: 0, medium: 0, low: 0, total: 0 });

/** Bucket one reading into a band. `lowerIsWorse` inverts the comparison. */
const addToBand = (band: Band, value: number | undefined, cuts: { high: number; medium: number }, lowerIsWorse = false) => {
  if (value === undefined || value === null || Number.isNaN(value)) return;
  const worseThan = (cut: number) => (lowerIsWorse ? value < cut : value > cut);
  if (worseThan(cuts.high)) band.high += 1;
  else if (worseThan(cuts.medium)) band.medium += 1;
  else band.low += 1;
  band.total += 1;
};

export const ReportsPage: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const teacherId = session?.userId || '';

  const [rangeKey, setRangeKey] = useState<string>('all');
  const [grade] = useState('Grade 4-B');

  const activeRange = DATE_RANGES.find(r => r.key === rangeKey) ?? DATE_RANGES[0];

  // Lesson counts come from the server when it answers, exactly as the
  // dashboard does (`data ?? localStats`). This page used to read only the
  // local Dexie table, so the dashboard could say "2 Lessons live" while
  // Reports said "0 Lessons Published" on the same data — the server had the
  // lessons and this browser had never pulled them.
  //
  // Local remains the offline fallback. The sync pull only sends *published*
  // lessons for this teacher, so the local table is already the right shape
  // to count; it is just often emptier than the server.
  const { data: serverDash } = useApiQuery<TeacherDashData>('/api/v1/dashboard/teacher');

  /** RFC 4180 quoting: double the quotes, wrap anything with a delimiter. */
  const csvCell = (v: unknown) => {
    const s = String(v ?? '');
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };

  const exportCsv = () => {
    const rows = reportsData?.tableRows ?? [];
    if (!rows.length) return;

    const header = ['Student', 'Risk level', 'Last screened', 'Lessons completed (%)'];
    const body = rows.map(r => [r.name, r.risk, r.lastDateStr, r.progressPercent]);
    // BOM so Excel opens Tamil names as UTF-8 rather than mojibake.
    const csv = '﻿' + [header, ...body].map(r => r.map(csvCell).join(',')).join('\r\n');

    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `ezhil-report-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Query database dynamically for reports stats
  const reportsData = useLiveQuery(async () => {
    if (!teacherId) return null;

    const studentsList = await db.students.where('teacherId').equals(teacherId).toArray();
    const studentIds = studentsList.map(s => s.id);

    const totalStudents = studentsList.length;
    const highRisk = studentsList.filter(s => s.riskLevel === 'high').length;
    const mediumRisk = studentsList.filter(s => s.riskLevel === 'medium').length;
    const lowRisk = studentsList.filter(s => s.riskLevel === 'low').length;
    const unscreened = studentsList.filter(s => s.riskLevel === 'unscreened').length;

    // Assessments in the selected window. Fetched rather than counted, because
    // the per-metric distribution bars below are computed from the same rows —
    // one pass, and the bars can never disagree with the count above them.
    const cutoff = activeRange.days === null
      ? null
      : new Date(Date.now() - activeRange.days * 86_400_000).toISOString();

    const assessmentsInRange = (await db.assessments
      .where('studentId')
      .anyOf(studentIds)
      .toArray()
    ).filter(a => cutoff === null || (a.conductedAt ?? '') >= cutoff);

    const totalScreenings = assessmentsInRange.length;

    // Real distributions, replacing what used to be three hardcoded bars.
    const phonemeBand  = emptyBand();
    const wpmBand      = emptyBand();
    const syllableBand = emptyBand();

    for (const a of assessmentsInRange) {
      addToBand(phonemeBand,  a.phonemeErrorRate,  RISK_BANDS.phoneme);
      addToBand(syllableBand, a.syllableSkipRate,  RISK_BANDS.syllable);
      addToBand(wpmBand,      a.readingSpeedWpm,   RISK_BANDS.wpm, true);
    }

    // Offline fallback only — the server figure is preferred below.
    const lessonsList = await db.lessons.toArray();
    const lessonsPublishedLocal = lessonsList.filter(l => l.isPublished).length;

    // Map student list details for reports table
    const tableRows = [];
    for (const s of studentsList) {
      const assessments = await db.assessments
        .where('studentId')
        .equals(s.id)
        .sortBy('conductedAt');
      const lastAss = assessments[assessments.length - 1];

      let lastDateStr = 'Not screened';
      if (lastAss && lastAss.conductedAt) {
        const date = new Date(lastAss.conductedAt);
        lastDateStr = date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
      }

      // Quiz progress calculation
      const doneCount = await db.lesson_progress
        .where('studentId')
        .equals(s.id)
        .filter(lp => !!lp.completedAt)
        .count();
      const totalLessons = lessonsList.length > 0 ? lessonsList.length : 10;
      const progressPercent = totalLessons > 0 ? Math.round((doneCount / totalLessons) * 100) : 0;

      tableRows.push({
        id: s.id,
        name: s.name,
        initials: s.name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase(),
        risk: s.riskLevel,
        lastDateStr,
        progressPercent
      });
    }

    return {
      totalStudents,
      highRisk,
      mediumRisk,
      lowRisk,
      unscreened,
      totalScreenings,
      lessonsPublishedLocal,
      tableRows,
      phonemeBand,
      wpmBand,
      syllableBand
    };
  }, [teacherId, rangeKey]);

  if (!reportsData) {
    return (
      <div className="flex items-center justify-center py-20 text-text-muted">
        Loading classroom statistics...
      </div>
    );
  }

  const {
    totalStudents,
    highRisk,
    mediumRisk,
    lowRisk,
    unscreened,
    totalScreenings,
    lessonsPublishedLocal,
    tableRows,
    phonemeBand,
    wpmBand,
    syllableBand
  } = reportsData;

  // Server wins when it answers; local covers the offline case.
  const lessonsPublished = serverDash?.lessons_published ?? lessonsPublishedLocal;

  // Calculate percentages for stacked progress bar
  const totalScreened = highRisk + mediumRisk + lowRisk;
  const highPct = totalScreened > 0 ? Math.round((highRisk / totalScreened) * 100) : 0;
  const medPct = totalScreened > 0 ? Math.round((mediumRisk / totalScreened) * 100) : 0;
  const lowPct = totalScreened > 0 ? 100 - highPct - medPct : 0;

  // The three per-metric bars. Each reports how many assessments in the
  // selected window it could actually band — a reading is skipped when the
  // metric is absent, so `band.total` is per-metric rather than per-student.
  const metricRows = [
    { label: 'Phonemic Awareness / ஒலிப்பு விழிப்புணர்வு', band: phonemeBand  },
    { label: 'Decoding Speed / குறிவிலக்க வேகம்',           band: wpmBand      },
    { label: 'Reading Fluency / வாசிப்பு சரளம்',            band: syllableBand },
  ];

  /** Whole percentages that always sum to 100, so the bar never under-fills. */
  const bandPercents = (b: Band) => {
    if (b.total === 0) return { high: 0, medium: 0, low: 0 };
    const high = Math.round((b.high / b.total) * 100);
    const medium = Math.round((b.medium / b.total) * 100);
    return { high, medium, low: 100 - high - medium };
  };

  return (
    <div className="space-y-lg animate-fade-in font-body-tamil select-none">
      
      {/* Page Header */}
      <div className="pb-sm border-b border-outline-variant/10 flex items-center gap-3">
        <span className="w-11 h-11 r-card bg-teacher-blue/15 border border-teacher-blue/30 flex items-center justify-center shadow-[0_0_14px_rgba(59,130,246,0.2)] flex-shrink-0">
          <span className="material-symbols-outlined text-teacher-blue" style={{ fontVariationSettings: "'FILL' 1" }}>assessment</span>
        </span>
        <div>
          <h1 className="font-tamil-reader text-3xl font-bold heading-display">Reports / அறிக்கைகள்</h1>
          <p className="text-text-muted text-sm mt-1">Classroom Risk Distribution & Student Performance Metrics</p>
        </div>
      </div>

      {/* Filters & Actions bar */}
      <div className="flex flex-wrap items-center justify-between gap-md bg-surface-container-low/50 p-md r-chip border border-outline-variant/10">
        <div className="flex items-center gap-md flex-wrap">
          <div className="flex flex-col gap-xs">
            <label htmlFor="report-range" className="text-xs uppercase font-boldr text-on-surface-variant">Date Range / தேதி வரம்பு</label>
            <div className="flex items-center bg-surface-container-high border border-outline-variant/30 px-md py-2 r-chip hover:bg-surface-variant transition-colors focus-within:ring-2 focus-within:ring-primary-fixed">
              <span className="material-symbols-outlined text-base text-primary-fixed mr-sm">calendar_month</span>
              <select
                id="report-range"
                value={rangeKey}
                onChange={e => setRangeKey(e.target.value)}
                className="text-xs font-bold text-white bg-transparent border-0 outline-none cursor-pointer pr-md appearance-none"
              >
                {DATE_RANGES.map(r => (
                  <option key={r.key} value={r.key} className="bg-surface-container-high text-white">
                    {r.label}
                  </option>
                ))}
              </select>
              <span className="material-symbols-outlined text-base text-text-muted -ml-md pointer-events-none">expand_more</span>
            </div>
          </div>

          <div className="flex flex-col gap-xs">
            <label className="text-xs uppercase font-boldr text-on-surface-variant">Classroom / வகுப்பு</label>
            <div className="flex items-center bg-surface-container-high border border-outline-variant/30 px-md py-2 r-chip cursor-pointer">
              <span className="text-xs font-bold text-white">{session?.schoolCode ? `Grade 4 (${session.schoolCode})` : grade}</span>
              <span className="material-symbols-outlined text-base text-text-muted ml-lg">expand_more</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-sm pt-md sm:pt-0">
          <button onClick={exportCsv}
            disabled={!reportsData?.tableRows.length}
            className="flex items-center gap-xs px-md py-2.5 r-chip border border-outline-variant/30 hover:bg-surface-container-high text-xs font-bold transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed">
            <span className="material-symbols-outlined text-base">csv</span>
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Summary Bento Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-lg stagger-children">

        {/* Screening completed card */}
        <div className="glass-card card-lift p-xl r-card relative overflow-hidden group border border-outline-variant/20 shadow-md">
          <div className="absolute -right-4 -top-4 w-32 h-32 bg-secondary/5 rounded-full blur-3xl group-hover:bg-secondary/10 transition-colors"></div>
          <div className="relative z-10">
            <p className="text-caption font-caption text-on-surface-variant mb-xs flex items-center gap-xs uppercase font-bold tracking-wider">
              <span className="material-symbols-outlined text-sm text-secondary" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
              Screenings Completed
            </p>
            <h3 className="text-display-md font-display-md text-white font-bold leading-none mt-xs">{totalScreenings}</h3>
            <div className="mt-md flex items-center gap-sm text-xs">
              <span className="text-on-surface-variant font-medium">{activeRange.label}</span>
            </div>
          </div>
        </div>

        {/* Lessons Published card */}
        <div className="glass-card card-lift p-xl r-card relative overflow-hidden group border border-outline-variant/20 shadow-md">
          <div className="absolute -right-4 -top-4 w-32 h-32 bg-studio-purple-light/5 rounded-full blur-3xl group-hover:bg-studio-purple-light/10 transition-colors"></div>
          <div className="relative z-10">
            <p className="text-caption font-caption text-on-surface-variant mb-xs flex items-center gap-xs uppercase font-bold tracking-wider">
              <span className="material-symbols-outlined text-sm text-studio-purple-light" style={{ fontVariationSettings: "'FILL' 1" }}>auto_stories</span>
              Lessons Published
            </p>
            <h3 className="text-display-md font-display-md text-white font-bold leading-none mt-xs">{lessonsPublished}</h3>
            <div className="mt-md flex items-center gap-sm text-xs">
              <span className="text-on-surface-variant font-medium">
                {lessonsPublished === 1 ? 'active module' : 'active modules'}
              </span>
            </div>
          </div>
        </div>

        {/* Risk Distribution overview stacked bar */}
        <div className="glass-card card-lift p-xl r-card relative overflow-hidden group border border-outline-variant/20 shadow-md">
          <div className="relative z-10 h-full flex flex-col justify-between">
            <p className="text-caption font-caption text-on-surface-variant uppercase font-boldr mb-sm">Risk Summary / அபாய சுருக்கம்</p>
            <div className="space-y-sm">
              <div className="flex justify-between text-xs font-bold">
                <span className="text-risk-high">High: {highRisk}</span>
                <span className="text-risk-medium">Med: {mediumRisk}</span>
                <span className="text-risk-low">Low: {lowRisk}</span>
              </div>
              <div className="w-full h-3 bg-surface-container-highest flex rounded-full overflow-hidden">
                <div className="h-full bg-risk-high transition-all" style={{ width: `${highPct}%` }}></div>
                <div className="h-full bg-risk-medium transition-all" style={{ width: `${medPct}%` }}></div>
                <div className="h-full bg-risk-low transition-all" style={{ width: `${lowPct}%` }}></div>
              </div>
              <p className="text-xs text-text-muted italic">Total screened: {totalScreened} students</p>
            </div>
          </div>
        </div>
      </div>

      {/* Risk Metrics Breakdown Panel */}
      <section className="glass-card r-chip p-xl border-l-4 border-secondary border border-outline-variant/20 shadow-lg">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-md mb-lg">
          <div>
            <h4 className="text-headline-sm font-headline-sm text-white font-bold">Risk Distribution / அபாய விநியோகம்</h4>
            <p className="text-caption font-caption text-on-surface-variant mt-0.5">Phonological risk metrics across active student screenings.</p>
          </div>
          <div className="flex gap-md">
            <div className="flex items-center gap-xs text-xs font-bold uppercase tracking-wider">
              <div className="w-2.5 h-2.5 rounded-full bg-risk-high"></div>
              <span className="text-risk-high">High Risk</span>
            </div>
            <div className="flex items-center gap-xs text-xs font-bold uppercase tracking-wider">
              <div className="w-2.5 h-2.5 rounded-full bg-risk-medium"></div>
              <span className="text-risk-medium">Medium</span>
            </div>
            <div className="flex items-center gap-xs text-xs font-bold uppercase tracking-wider">
              <div className="w-2.5 h-2.5 rounded-full bg-risk-low"></div>
              <span className="text-risk-low">Low Risk</span>
            </div>
          </div>
        </div>

        {totalScreenings === 0 ? (
          <div className="py-xl text-center">
            <span className="material-symbols-outlined text-4xl text-text-muted/50">bar_chart_4_bars</span>
            <p className="text-sm text-text-muted mt-sm">
              No screenings in this period / இந்தக் காலத்தில் மதிப்பீடுகள் இல்லை
            </p>
            <p className="text-xs text-text-muted/70 mt-1">
              {activeRange.days === null
                ? 'Risk metrics appear here once students have been screened.'
                : 'Try a wider date range, or screen a student to see risk metrics here.'}
            </p>
          </div>
        ) : (
          <div className="space-y-lg">
            {metricRows.map(({ label, band }) => {
              const pct = bandPercents(band);
              return (
                <div key={label} className="space-y-1">
                  <div className="flex justify-between items-end text-xs">
                    <span className="font-bold text-white">{label}</span>
                    <span className="text-text-muted font-mono">
                      {band.total} {band.total === 1 ? 'Reading' : 'Readings'}
                    </span>
                  </div>
                  {band.total === 0 ? (
                    <div className="w-full h-8 bg-surface-container flex items-center justify-center r-chip border border-outline-variant/30 shadow-inner">
                      <span className="text-xs text-text-muted italic">Not recorded for these screenings</span>
                    </div>
                  ) : (
                    <div className="w-full h-8 bg-surface-container flex r-chip overflow-hidden border border-outline-variant/30 shadow-inner">
                      {pct.high > 0 && (
                        <div className="h-full bg-risk-high flex items-center justify-center text-xs font-bold text-white hover:brightness-110 transition-all cursor-help"
                             style={{ width: `${pct.high}%` }} title={`High risk — ${band.high} of ${band.total}`}>{pct.high}%</div>
                      )}
                      {pct.medium > 0 && (
                        <div className="h-full bg-risk-medium flex items-center justify-center text-xs font-bold text-white hover:brightness-110 transition-all cursor-help"
                             style={{ width: `${pct.medium}%` }} title={`Medium risk — ${band.medium} of ${band.total}`}>{pct.medium}%</div>
                      )}
                      {pct.low > 0 && (
                        <div className="h-full bg-risk-low flex items-center justify-center text-xs font-bold text-white hover:brightness-110 transition-all cursor-help"
                             style={{ width: `${pct.low}%` }} title={`Low risk — ${band.low} of ${band.total}`}>{pct.low}%</div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* Detailed Student Reports Table */}
      <section className="glass-card r-chip overflow-hidden shadow-2xl border border-outline-variant/20">
        <div className="p-lg bg-surface-container-high border-b border-outline-variant/30 flex justify-between items-center">
          <h4 className="text-body-lg font-body-lg font-bold text-white">Student Reports / மாணவர் அறிக்கைகள்</h4>
          <button 
            onClick={() => navigate('/teacher/roster')}
            className="text-secondary text-xs font-bold uppercase flex items-center gap-xs hover:underline cursor-pointer"
          >
            <span>View All</span>
            <span className="material-symbols-outlined text-sm">arrow_forward</span>
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant/20">
                <th className="px-xl py-md text-xs font-bold text-on-surface-variant uppercase">Student Name / பெயர்</th>
                <th className="px-xl py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-center">Last Screening</th>
                <th className="px-xl py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-center">Risk Level</th>
                <th className="px-xl py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-center">Lesson Progress</th>
                <th className="px-xl py-md text-xs font-bold text-on-surface-variant uppercase tracking-wider text-right font-bold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/10">
              {tableRows.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-xl py-xl text-center text-text-muted">
                    தரவுகள் இல்லை / No student reports available.
                  </td>
                </tr>
              ) : (
                tableRows.map(r => {
                  const isHigh = r.risk === 'high';
                  const isMed = r.risk === 'medium';
                  const isLow = r.risk === 'low';
                  
                  let badgeClass = 'bg-risk-unscreened/10 text-risk-unscreened border-risk-unscreened/20';
                  let badgeLabel = 'Unscreened';
                  if (isHigh) {
                    badgeClass = 'bg-risk-high/10 text-risk-high border-risk-high/20';
                    badgeLabel = 'High / அதிக';
                  } else if (isMed) {
                    badgeClass = 'bg-risk-medium/10 text-risk-medium border-risk-medium/20';
                    badgeLabel = 'Medium / நடுத்தர';
                  } else if (isLow) {
                    badgeClass = 'bg-risk-low/10 text-risk-low border-risk-low/20';
                    badgeLabel = 'Low / குறைவு';
                  }

                  return (
                    <tr 
                      key={r.id} 
                      onClick={() => navigate('/teacher/student-profile', { state: { studentId: r.id } })}
                      className="hover:bg-surface-container-high transition-transform duration-200 hover:translate-x-1 hover:bg-white/5 transition-colors group cursor-pointer"
                    >
                      <td className="px-xl py-lg">
                        <div className="flex items-center gap-md">
                          <div className={`w-10 h-10 rounded bg-secondary-container/10 border border-secondary-container/20 flex items-center justify-center text-secondary font-bold text-sm`}>
                            {r.initials}
                          </div>
                            <div className="text-body-sm font-bold text-white group-hover:text-primary-fixed transition-colors">{r.name}</div>
                            <div className="flex items-center gap-1.5 text-caption text-on-surface-variant font-tamil-body text-xs mt-0.5" onClick={e => e.stopPropagation()}>
                              <span>மாணவர் ஐடி:</span>
                              <IdDisplay id={r.id} maxLength={6} />
                            </div>
                        </div>
                      </td>
                      <td className="px-xl py-lg text-center font-mono text-xs text-on-surface-variant">
                        {r.lastDateStr}
                      </td>
                      <td className="px-xl py-lg text-center">
                        <div className={`inline-flex items-center gap-xs px-md py-1 rounded-full border text-xs font-bold ${badgeClass}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${isHigh ? 'bg-risk-high' : isMed ? 'bg-risk-medium' : isLow ? 'bg-risk-low' : 'bg-risk-unscreened'}`}></span>
                          {badgeLabel}
                        </div>
                      </td>
                      <td className="px-xl py-lg">
                        <div className="flex items-center gap-md max-w-[120px] mx-auto">
                          <div className="flex-1 h-1.5 bg-surface-container rounded-full overflow-hidden">
                            <div className={`h-full ${isHigh ? 'bg-risk-high' : isMed ? 'bg-risk-medium' : 'bg-secondary'}`} style={{ width: `${r.progressPercent}%` }}></div>
                          </div>
                          <span className="text-xs font-mono text-white">{r.progressPercent}%</span>
                        </div>
                      </td>
                      <td className="px-xl py-lg text-right">
                        <div className="flex justify-end gap-sm md:opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => { e.stopPropagation(); navigate('/teacher/student-profile', { state: { studentId: r.id } }); }}
                            className="p-sm hover:bg-surface-variant r-chip text-on-surface-variant hover:text-primary-fixed" 
                            title="View Details"
                          >
                            <span className="material-symbols-outlined text-lg">visibility</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};
