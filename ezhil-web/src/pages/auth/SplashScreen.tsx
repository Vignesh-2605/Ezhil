import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { EzhilMark } from '../../components/brand/EzhilLogo';

export const SplashScreen: React.FC = () => {
  const navigate = useNavigate();
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const duration = 2800; // total animation time in ms
    const intervalTime = 20;
    const steps = duration / intervalTime;
    const stepIncrement = 100 / steps;

    const timer = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          clearInterval(timer);
          return 100;
        }
        return prev + stepIncrement;
      });
    }, intervalTime);

    const navTimeout = setTimeout(() => {
      navigate('/onboarding/1');
    }, 3100);

    return () => {
      clearInterval(timer);
      clearTimeout(navTimeout);
    };
  }, [navigate]);

  return (
    <main className="relative flex flex-col items-center justify-center min-h-dvh w-full overflow-hidden bg-bg-deep select-none">
      {/* Ambient Radial Background Glows */}
      <div className="absolute inset-0 pointer-events-none z-0">
        <div className="absolute top-[30%] left-1/2 -translate-x-1/2 w-[500px] h-[500px] rounded-full blur-[140px] opacity-40 bg-[radial-gradient(circle,_rgba(98,249,238,0.18)_0%,_transparent_70%)]" />
        <div className="absolute -top-40 -left-40 w-96 h-96 bg-primary-fixed/5 rounded-full blur-[120px] animate-pulse" />
        <div className="absolute -bottom-40 -right-40 w-[450px] h-[450px] bg-studio-purple/5 rounded-full blur-[150px]" />
      </div>

      {/* Main Brand Container */}
      <div className="relative z-10 flex flex-col items-center text-center px-6">
        {/* Pulsing Decorative Ring */}
        <div className="relative w-36 h-36 rounded-full flex items-center justify-center mb-8">
          <div className="absolute inset-0 rounded-full border border-primary-fixed/20 animate-ping opacity-35" />
          <div className="absolute inset-2 rounded-full border border-accent-teal/30 animate-pulse-ring" />
          <div className="w-24 h-24 rounded-full bg-bg-surface/40 backdrop-blur-xl border border-white/5 flex items-center justify-center shadow-[0_8px_32px_rgba(0,0,0,0.4)]">
            <EzhilMark size={64} />
          </div>
        </div>

        {/* Text Logo with Scale Entry */}
        <div className="animate-slide-in space-y-1">
          <h1 className="font-display-tamil heading-display-accent text-[68px] leading-none select-none">
            எழில்
          </h1>
          <div className="flex items-center justify-center gap-3">
            <div className="h-[1px] w-8 bg-gradient-to-r from-transparent to-border-muted" />
            <span className="font-dashboard-title text-xl text-on-surface-variant font-bold tracking-[0.25em] uppercase">
              Ezhil
            </span>
            <div className="h-[1px] w-8 bg-gradient-to-l from-transparent to-border-muted" />
          </div>
          <p className="font-body-tamil text-base text-accent-cyan font-medium opacity-90 pt-3">
            எழு. படி. வலி.
          </p>
        </div>
      </div>

      {/* Loading Progress Section */}
      <div className="absolute bottom-[20%] w-full flex flex-col items-center gap-4 z-10 px-8">
        <div className="w-full max-w-[280px] bg-bg-surface/50 border border-white/5 rounded-full h-[6px] p-[1px] overflow-hidden backdrop-blur-sm">
          <div 
            className="h-full bg-gradient-to-r from-primary-fixed-dim to-primary-fixed rounded-full shadow-[0_0_12px_rgba(98,249,238,0.6)] transition-all duration-75 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>
        <div className="flex items-center gap-2 text-xs font-mono-metadata text-text-muted opacity-80 uppercase">
          <span>ஏற்றுகிறது</span>
          <span className="w-8 text-right font-semibold text-primary-fixed">{Math.round(progress)}%</span>
        </div>
      </div>

      {/* Footer Version Details */}
      <footer className="absolute bottom-8 w-full text-center z-10">
        <code className="font-mono-metadata text-xs text-border-muted opacity-40 uppercase tracking-[0.3em]">
          v1.0.0
        </code>
      </footer>
    </main>
  );
};
