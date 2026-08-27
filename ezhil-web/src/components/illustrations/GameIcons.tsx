import React from 'react';

/**
 * Phonics game icons — one tile per game, in each game's accent colour.
 * match-sound = cyan · spot-letter = purple · build-word = amber.
 */

const TEAL = '#0F2E33';
const TEAL_LIGHT = '#17444B';
const CREAM = '#FBF3E0';
const CYAN = '#62F9EE';
const AMBER = '#FFB955';
const PURPLE = '#A78BFA';

const Tile: React.FC<React.PropsWithChildren<{ size: number; className?: string; label: string }>> = ({
  size, className, label, children,
}) => (
  <svg width={size} height={size} viewBox="0 0 64 64" className={className} role="img" aria-label={label}>
    <defs>
      <radialGradient id={`tile-${label.replace(/\s/g, '')}`} cx="50%" cy="30%" r="90%">
        <stop offset="0%" stopColor={TEAL_LIGHT} />
        <stop offset="100%" stopColor={TEAL} />
      </radialGradient>
    </defs>
    <rect width="64" height="64" rx="16" fill={`url(#tile-${label.replace(/\s/g, '')})`} />
    {children}
  </svg>
);

/** ஒலி பொருத்தம் — speaker with sound waves reaching a letter. */
export const MatchSoundIcon: React.FC<{ size?: number; className?: string }> = ({ size = 56, className }) => (
  <Tile size={size} className={className} label="Match Sound game">
    <path d="M12 26 l7 0 l8 -8 l0 28 l-8 -8 l-7 0 z" fill={CREAM} />
    <path d="M33 24 q5 8 0 16" stroke={CYAN} strokeWidth="3.2" fill="none" strokeLinecap="round" />
    <path d="M39 19 q9 13 0 26" stroke={CYAN} strokeWidth="3.2" fill="none" strokeLinecap="round" opacity="0.6" />
    <rect x="44" y="24" width="16" height="16" rx="4" fill={CYAN} transform="rotate(8 52 32)" />
    <text x="51.5" y="36.5" fontSize="10" fontWeight="700" textAnchor="middle" fill={TEAL} fontFamily="'Noto Sans Tamil', sans-serif" transform="rotate(8 52 32)">ஒ</text>
  </Tile>
);

/** எழுத்து கண்டுபிடி — magnifier locked onto a letter among faded ones. */
export const SpotLetterIcon: React.FC<{ size?: number; className?: string }> = ({ size = 56, className }) => (
  <Tile size={size} className={className} label="Spot the Letter game">
    <text x="14" y="24" fontSize="12" fill={CREAM} opacity="0.28" fontFamily="'Noto Sans Tamil', sans-serif">க</text>
    <text x="44" y="20" fontSize="11" fill={CREAM} opacity="0.22" fontFamily="'Noto Sans Tamil', sans-serif">ப</text>
    <text x="46" y="52" fontSize="12" fill={CREAM} opacity="0.26" fontFamily="'Noto Sans Tamil', sans-serif">ம</text>
    <circle cx="28" cy="34" r="13" fill={TEAL_LIGHT} stroke={PURPLE} strokeWidth="3.4" />
    <text x="28" y="40" fontSize="15" fontWeight="700" textAnchor="middle" fill={PURPLE} fontFamily="'Noto Sans Tamil', sans-serif">எ</text>
    <rect x="38" y="43" width="15" height="5.4" rx="2.7" fill={CREAM} transform="rotate(45 38 43)" />
  </Tile>
);

/** சொல் கட்டு — letter blocks stacking into a word. */
export const BuildWordIcon: React.FC<{ size?: number; className?: string }> = ({ size = 56, className }) => (
  <Tile size={size} className={className} label="Build a Word game">
    <rect x="10" y="36" width="18" height="18" rx="4.5" fill={CREAM} />
    <text x="19" y="49.5" fontSize="10" fontWeight="700" textAnchor="middle" fill={TEAL} fontFamily="'Noto Sans Tamil', sans-serif">சொ</text>
    <rect x="32" y="36" width="18" height="18" rx="4.5" fill={AMBER} />
    <text x="41" y="49.5" fontSize="10" fontWeight="700" textAnchor="middle" fill={TEAL} fontFamily="'Noto Sans Tamil', sans-serif">ல்</text>
    <rect x="21" y="15" width="18" height="18" rx="4.5" fill={TEAL_LIGHT} stroke={AMBER} strokeWidth="1.6" strokeDasharray="3 3" />
    <path d="M30 20 l0 8 M26 24 l8 0" stroke={AMBER} strokeWidth="2.4" strokeLinecap="round" />
    <path d="M52 16 l1.8 4 4 1.8 -4 1.8 -1.8 4 -1.8 -4 -4 -1.8 4 -1.8 z" fill={AMBER} opacity="0.9" />
  </Tile>
);
