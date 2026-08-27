import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { RiskBadge } from '../../components/ui/RiskBadge';
import { PageLoading } from '../../components/ui/LoadingSpinner';

interface Flag {
  id:       string;
  name:     string;
  risk:     string;
  /** Observed signals, each traceable to a stored record. Never a diagnosis. */
  reasons:  string[];
  days:     number | null;
  modelVersion?: string;
}

/** Machine-readable error tags → plain language a teacher can act on. */
const TAG_LABEL: Record<string, string> = {
  phoneme_confusion: 'Phoneme confusion in the last reading',
  syllable_skip:     'Skipped syllables while reading aloud',
  letter_reversal:   'Letter reversals detected',
  slow_reading:      'Reading speed below the class range',
};

const daysSince = (iso?: string | null): number | null => {
  if (!iso) return null;
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return null;
  return Math.max(0, Math.floor((Date.now() - then) / 86_400_000));
};

export const RiskFlagsPage: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const teacherId = session?.userId;
  const [resolved, setResolved] = useState<string[]>([]);

  const flags = useLiveQuery(async () => {
    if (!teacherId) return [] as Flag[];
    const students = await db.students.where('teacherId').equals(teacherId).toArray();

    const out: Flag[] = [];
    for (const s of students) {
      if (s.riskLevel !== 'high' && s.riskLevel !== 'medium') continue;

      const assessments = await db.assessments.where('studentId').equals(s.id).sortBy('conductedAt');
      const latest = assessments[assessments.length - 1];

      const reasons: string[] = [];

      // 1 — error tags recorded by the screening run
      if (latest?.errorTagsJson) {
        try {
          for (const tag of JSON.parse(latest.errorTagsJson) as string[]) {
            reasons.push(TAG_LABEL[tag] ?? tag.replace(/_/g, ' '));
          }
        } catch { /* malformed tag blob — skip rather than guess */ }
      }

      // 2 — repeated high-risk screenings
      const highCount = assessments.filter(a => a.riskLevel === 'high').length;
      if (highCount > 1) reasons.push(`${highCount} screenings flagged high risk`);

      // 3 — disengagement
      const idle = daysSince(s.lastActive);
      if (idle !== null && idle >= 7) reasons.push(`No activity for ${idle} days`);

      // 4 — never screened despite a risk level on file
      if (!latest) reasons.push('Risk level on file but no screening recorded');

      out.push({
        id:      s.id,
        name:    s.name,
        risk:    s.riskLevel,
        reasons,
        days:    daysSince(latest?.conductedAt),
        modelVersion: latest?.modelVersion,
      });
    }

    // Highest concern first
    return out.sort((a, b) =>
      (a.risk === b.risk ? b.reasons.length - a.reasons.length : a.risk === 'high' ? -1 : 1));
  }, [teacherId]);

  if (flags === undefined) return <PageLoading />;

  const active = flags.filter(f => !resolved.includes(f.id));
  const highCount = active.filter(f => f.risk === 'high').length;

  return (
    <div className="space-y-6 font-body-tamil max-w-3xl">
      <div className="flex items-center gap-3 animate-fade-in">
        <span className="w-11 h-11 r-card bg-risk-high/15 border border-risk-high/30 flex items-center justify-center shadow-[0_0_14px_rgba(229,57,53,0.2)] flex-shrink-0">
          <span className="material-symbols-outlined text-risk-high" style={{ fontVariationSettings: "'FILL' 1" }}>flag</span>
        </span>
        <div>
          <h1 className="font-display-tamil text-3xl font-bold heading-display">Risk Flags</h1>
          <p className="text-text-muted text-sm mt-1">ஆபத்து அறிவிப்புகள் · {active.length} active flag{active.length === 1 ? '' : 's'}</p>
        </div>
      </div>

      {highCount > 0 && (
        <div className="glass-panel border-l-4 border-error r-chip p-4 flex items-center gap-3">
          <span className="material-symbols-outlined text-error text-2xl" style={{ fontVariationSettings: "'FILL' 1" }}>emergency</span>
          <div>
            <p className="text-error font-bold">
              {highCount === 1
                ? '1 high-risk student needs attention'
                : `${highCount} high-risk students need attention`}
            </p>
            <p className="text-text-muted text-sm">Review their profiles and consider intervention support.</p>
          </div>
        </div>
      )}

      <div className="space-y-4 stagger-children">
        {active.map(flag => (
          <div key={flag.id} className={`glass-panel card-lift r-card p-5 border-l-4 relative overflow-hidden ${flag.risk === 'high' ? 'border-error' : 'border-secondary'}`}>
            <div className={`absolute -top-10 -right-10 w-24 h-24 rounded-full blur-2xl ${flag.risk === 'high' ? 'bg-error/10' : 'bg-secondary/10'}`} />
            <div className="relative flex items-start justify-between gap-4">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-full bg-bg-deep border-2 border-accent-teal/20 flex items-center justify-center flex-shrink-0">
                  <span className="font-display-tamil text-lg text-primary-fixed">
                    {flag.name.trim().charAt(0) || '?'}
                  </span>
                </div>
                <h3 className="font-display-tamil text-white font-bold text-lg">{flag.name}</h3>
              </div>
              <RiskBadge level={flag.risk} />
            </div>

            {flag.reasons.length > 0 && (
              <ul className="text-on-surface-variant text-sm mt-4 bg-black/20 p-3 r-chip border border-white/5 space-y-1">
                {flag.reasons.map((r, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="text-text-muted">·</span>{r}
                  </li>
                ))}
              </ul>
            )}

            <div className="flex items-center justify-between mt-4 gap-3 flex-wrap">
              <span className="font-mono-metadata text-xs text-text-muted">
                {flag.days === null
                  ? 'No screening on record'
                  : `Last screened ${flag.days === 0 ? 'today' : `${flag.days} day${flag.days > 1 ? 's' : ''} ago`}`}
                {flag.modelVersion && ` · ${flag.modelVersion}`}
              </span>
              <div className="flex gap-2">
                <button onClick={() => navigate('/teacher/student-profile', { state: { studentId: flag.id } })}
                  className="px-4 py-1.5 text-sm border border-primary-fixed/30 text-primary-fixed r-chip hover:bg-primary-fixed/10 transition-colors font-medium">
                  View Profile
                </button>
                <button onClick={() => setResolved(r => [...r, flag.id])}
                  className="px-4 py-1.5 text-sm border border-success/30 text-success r-chip hover:bg-success/10 transition-colors font-medium">
                  Dismiss
                </button>
              </div>
            </div>
          </div>
        ))}

        {active.length === 0 && (
          <div className="text-center py-16 space-y-3">
            <span className="text-6xl">✅</span>
            <h3 className="text-white font-bold text-xl">
              {flags.length === 0 ? 'No flags right now' : 'All flags reviewed'}
            </h3>
            <p className="text-text-muted">
              {flags.length === 0
                ? 'No student in your class is currently at medium or high risk.'
                : 'You have dismissed every active flag in this session.'}
            </p>
          </div>
        )}
      </div>

      {/* Screening output is a heuristic estimate, not a clinical result —
          say so where a teacher is deciding whether to intervene. */}
      <p className="text-text-muted text-xs border-t border-white/5 pt-4">
        Flags are generated from recorded screening sessions and activity, and are
        indicators for a teacher's attention — not a diagnosis.
      </p>
    </div>
  );
};
