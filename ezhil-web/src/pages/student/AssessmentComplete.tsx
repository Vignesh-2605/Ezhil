import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ProgressRing } from '../../components/ui/ProgressRing';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

const PHONEME_ERRORS = [
  { phoneme: 'ழ', label: 'ழ (zha)', severity: 'high',   count: 3 },
  { phoneme: 'ண', label: 'ண (na)',  severity: 'medium', count: 2 },
  { phoneme: 'ற', label: 'ற (ra)',  severity: 'low',    count: 1 },
];

const SEV_COLOR: Record<string, string> = { high: 'text-error border-error/30 bg-error/10', medium: 'text-secondary border-secondary/30 bg-secondary/10', low: 'text-success border-success/30 bg-success/10' };

export const AssessmentComplete: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="space-y-8 max-w-lg mx-auto pb-8 font-body-tamil relative">
      {/* Score card */}
      <div className="glass-panel r-hero p-8 flex flex-col items-center gap-4 border-t-4 border-primary-fixed relative overflow-hidden animate-slide-in shimmer">
        <div className="absolute -top-16 left-1/2 -translate-x-1/2 w-64 h-32 bg-primary-fixed/12 blur-3xl rounded-full" />
        <EzhilanMoment trigger="celebrateBig" size={110} className="relative" />
        <h1 className="font-display-tamil text-2xl font-bold heading-display-accent relative">முடிவுகள் | Results</h1>
        <div className="flex gap-2 justify-center my-2 relative">
          {[1, 2, 3].map(n => (
            <span key={n} className="material-symbols-outlined text-5xl text-tertiary-fixed-dim drop-shadow-[0_0_10px_rgba(255,210,127,0.6)] animate-pop-in" style={{ fontVariationSettings: "'FILL' 1", animationDelay: `${n * 160}ms` }}>star</span>
          ))}
        </div>
        <p className="text-2xl font-bold text-primary-fixed relative">மிக நன்று / Well Done! 🎉</p>
        <p className="text-on-surface-variant text-center relative">Good reading! Keep practicing the highlighted sounds to improve.</p>
      </div>

      {/* Phoneme errors */}
      <div className="glass-panel r-card p-6 space-y-4 stagger-children">
        <h2 className="text-white font-bold text-lg">⚠️ Sounds to Practice</h2>
        {PHONEME_ERRORS.map(e => (
          <div key={e.phoneme} className={`flex items-center justify-between p-3 r-chip border ${SEV_COLOR[e.severity]}`}>
            <div className="flex items-center gap-3">
              <span className="font-display-tamil text-2xl font-bold">{e.phoneme}</span>
              <span className="text-sm">{e.label}</span>
            </div>
            <span className="font-mono-metadata text-xs font-bold">{e.count} errors</span>
          </div>
        ))}
      </div>

      {/* Recommended lesson */}
      <div className="glass-panel r-card p-5 border border-primary-fixed/20 flex items-center gap-4">
        <span className="material-symbols-outlined text-primary-fixed text-3xl">lightbulb</span>
        <div>
          <p className="text-white font-bold">Recommended Practice</p>
          <p className="text-text-muted text-sm">ழ, ண, ற — Special Tamil Sounds</p>
        </div>
        <button className="ml-auto bg-primary-fixed/10 text-primary-fixed font-bold text-sm px-4 py-2 r-chip border border-primary-fixed/30 hover:bg-primary-fixed/20 transition-colors">
          Start
        </button>
      </div>

      <div className="space-y-3">
        <button onClick={() => navigate('/student/achievement?type=lesson&stars=3')}
          className="w-full h-14 bg-primary-fixed text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_20px_rgba(98,249,238,0.25)]">
          View Achievement
        </button>
        <button onClick={() => navigate('/student/home')}
          className="w-full h-12 border border-white/15 text-on-surface-variant r-chip font-medium hover:bg-white/5 transition-colors">
          Back to Home
        </button>
      </div>
    </div>
  );
};
