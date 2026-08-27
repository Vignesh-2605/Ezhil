import React from 'react';
import { useNavigate } from 'react-router-dom';

export const AssessmentStart: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-10 relative overflow-hidden">
      {/* Ambient glow */}
      <div className="orb w-96 h-96 bg-secondary/10 top-0 left-1/2 -translate-x-1/2" />
      <div className="orb w-72 h-72 bg-primary-fixed/8 bottom-0 right-0" />

      <div className="text-center space-y-3 relative animate-fade-in">
        <div className="relative w-24 h-24 mx-auto mb-4 flex items-center justify-center">
          <div className="absolute inset-0 rounded-full bg-secondary/20 border-2 border-secondary/50 animate-pulse-ring" />
          <div className="absolute inset-0 rounded-full bg-secondary/10 blur-xl" />
          <span className="relative material-symbols-outlined text-secondary text-5xl" style={{ fontVariationSettings: "'FILL' 1" }}>mic</span>
        </div>
        <h1 className="font-display-tamil text-4xl font-bold text-gradient-warm">படி Aloud</h1>
        <p className="text-on-surface-variant text-lg">Read Aloud Assessment</p>
      </div>

      <div className="glass-panel w-full max-w-sm r-card p-6 space-y-4 relative animate-slide-in">
        <h2 className="font-display-tamil text-xl font-bold text-white text-center">யானையும் எறும்பும்</h2>
        <p className="text-on-surface-variant text-sm text-center">Read the passage clearly. We'll assess your pronunciation and fluency.</p>
        <div className="space-y-2 text-sm">
          {[
            { icon: 'mic', text: 'Microphone required' },
            { icon: 'volume_up', text: 'Read in a quiet place' },
            { icon: 'timer', text: 'About 2 minutes' },
          ].map(tip => (
            <div key={tip.text} className="flex items-center gap-3 text-on-surface-variant">
              <span className="material-symbols-outlined text-primary-fixed text-base">{tip.icon}</span>
              {tip.text}
            </div>
          ))}
        </div>
      </div>

      <div className="w-full max-w-sm space-y-3 relative">
        <button onClick={() => navigate('/student/assessment/recording')}
          className="w-full h-14 bg-secondary text-bg-deep font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_20px_rgba(255,185,85,0.3)] hover:shadow-[0_0_32px_rgba(255,185,85,0.5)] flex items-center justify-center gap-2">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>mic</span>
          தொடங்கு / Start Recording
        </button>
        <button onClick={() => navigate(-1)} className="w-full h-12 text-on-surface-variant font-medium hover:text-white transition-colors">
          Cancel
        </button>
      </div>
    </div>
  );
};
