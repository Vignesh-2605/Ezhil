import React from 'react';
import { useNavigate } from 'react-router-dom';
import { LanguageToggle } from '../ui/LanguageToggle';

interface TopAppBarProps {
  title?: string;
  showBack?: boolean;
}

export const TopAppBar: React.FC<TopAppBarProps> = ({
  title = 'எழில் | Ezhil',
  showBack = false,
}) => {
  const navigate = useNavigate();

  return (
    <header className="relative bg-bg-deep/70 backdrop-blur-xl font-dashboard-title font-bold text-lg flex justify-between items-center w-full px-4 h-16 sticky top-0 z-50 select-none overflow-hidden">
      {/* Gradient hairline + faint glow under the bar */}
      <div className="absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-primary-fixed/40 to-transparent" />
      <div className="absolute -top-10 left-6 w-32 h-16 bg-primary-fixed/10 blur-2xl rounded-full pointer-events-none" />

      <div className="relative flex items-center gap-3">
        {showBack ? (
          <button
            onClick={() => navigate(-1)}
            className="material-symbols-outlined text-primary-fixed p-2 hover:bg-primary-fixed/10 rounded-full transition-all active:scale-[0.9] cursor-pointer"
          >
            arrow_back
          </button>
        ) : (
          <span className="w-9 h-9 r-chip bg-primary-fixed/12 border border-primary-fixed/25 flex items-center justify-center shadow-[0_0_14px_rgba(98,249,238,0.25)]">
            <span className="material-symbols-outlined text-primary-fixed text-xl" style={{ fontVariationSettings: "'FILL' 1" }}>auto_stories</span>
          </span>
        )}
        <span className="font-display-tamil font-black text-xl heading-display">{title}</span>
      </div>
      <LanguageToggle />
    </header>
  );
};
