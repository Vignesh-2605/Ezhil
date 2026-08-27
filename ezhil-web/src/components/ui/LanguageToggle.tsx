import React from 'react';
import clsx from 'clsx';
import { useLanguage } from '../../contexts/LanguageContext';

interface LanguageToggleProps {
  className?: string;
}

export const LanguageToggle: React.FC<LanguageToggleProps> = ({ className }) => {
  const { lang, toggle } = useLanguage();

  return (
    <div className={clsx('bg-bg-surface border border-accent-teal/30 px-1 py-1 rounded-full flex items-center shadow-sm w-fit', className)}>
      <button
        onClick={() => lang !== 'ta' && toggle()}
        className={clsx(
          'px-3 py-1 rounded-full font-bold text-sm transition-all duration-200 whitespace-nowrap min-w-[64px] text-center cursor-pointer',
          lang === 'ta' ? 'bg-primary-fixed text-bg-deep shadow-sm' : 'text-text-muted hover:text-on-surface'
        )}
      >
        தமிழ்
      </button>
      <button
        onClick={() => lang !== 'en' && toggle()}
        className={clsx(
          'px-3 py-1 rounded-full font-bold text-sm transition-all duration-200 whitespace-nowrap min-w-[64px] text-center cursor-pointer',
          lang === 'en' ? 'bg-primary-fixed text-bg-deep shadow-sm' : 'text-text-muted hover:text-on-surface'
        )}
      >
        EN
      </button>
    </div>
  );
};
