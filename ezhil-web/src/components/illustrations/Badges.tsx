import React from 'react';

/**
 * Achievement badge set — gold-medal frame with a per-badge symbol.
 * Locked badges render desaturated at reduced opacity (never sad/grey-X:
 * a locked badge is a promise, not a failure).
 */

export type BadgeKind =
  | 'first-read'
  | 'streak-3'
  | 'streak-7'
  | 'first-lesson'
  | 'quiz-master'
  | 'word-builder'
  | 'sound-matcher'
  | 'letter-spotter';

const TEAL = '#0F2E33';
const TEAL_LIGHT = '#17444B';
const CREAM = '#FBF3E0';
const CYAN = '#62F9EE';
const AMBER = '#FFB955';
const AMBER_DEEP = '#D99B45';

export const BADGE_META: Record<BadgeKind, { ta: string; en: string }> = {
  'first-read':     { ta: 'முதல் வாசிப்பு',   en: 'First Read' },
  'streak-3':       { ta: '3 நாள் தொடர்',     en: '3-Day Streak' },
  'streak-7':       { ta: '7 நாள் தொடர்',     en: '7-Day Streak' },
  'first-lesson':   { ta: 'முதல் பாடம்',      en: 'First Lesson' },
  'quiz-master':    { ta: 'வினா வித்தகர்',    en: 'Quiz Master' },
  'word-builder':   { ta: 'சொல் சிற்பி',      en: 'Word Builder' },
  'sound-matcher':  { ta: 'ஒலி நிபுணர்',      en: 'Sound Matcher' },
  'letter-spotter': { ta: 'எழுத்து வேட்டை',   en: 'Letter Spotter' },
};

const Symbol: React.FC<{ kind: BadgeKind }> = ({ kind }) => {
  switch (kind) {
    case 'first-read':
      return (
        <g>
          <path d="M-14 -2 q7 -5 14 -2 q7 -3 14 2 l0 12 q-7 -4 -14 -1.5 q-7 -2.5 -14 1.5 z" fill={CREAM} />
          <path d="M0 -4 l0 13" stroke={TEAL_LIGHT} strokeWidth="1.6" />
          <path d="M-10 1 q5 -3 8 -1.5 M2 -0.5 q5 -1.5 8 1.5" stroke={TEAL_LIGHT} strokeWidth="1.4" fill="none" strokeLinecap="round" />
          <path d="M0 -13 l2 4.4 4.4 2 -4.4 2 -2 4.4 -2 -4.4 -4.4 -2 4.4 -2 z" fill={CYAN} />
        </g>
      );
    case 'streak-3':
    case 'streak-7':
      return (
        <g>
          <path d="M0 -14 q8 7 8 15 a8 9 0 0 1 -16 0 q0 -8 8 -15 z" fill={CREAM} />
          <path d="M0 -6 q4.5 4.5 4.5 8.5 a4.5 5 0 0 1 -9 0 q0 -4 4.5 -8.5 z" fill={AMBER_DEEP} />
          <text x="0" y="6.5" fontSize="10" fontWeight="800" textAnchor="middle" fill={TEAL} fontFamily="'DM Sans', sans-serif">
            {kind === 'streak-3' ? '3' : '7'}
          </text>
        </g>
      );
    case 'first-lesson':
      return (
        <g>
          <rect x="-11" y="-12" width="22" height="26" rx="4" fill={CREAM} />
          <path d="M-6 -5 l12 0 M-6 0 l12 0 M-6 5 l8 0" stroke={TEAL_LIGHT} strokeWidth="2" strokeLinecap="round" />
          <circle cx="9" cy="-9" r="6.5" fill={CYAN} />
          <path d="M6.5 -9 l2 2 l3.5 -3.5" stroke={TEAL} strokeWidth="1.8" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </g>
      );
    case 'quiz-master':
      return (
        <g>
          <path d="M-13 6 l-2 -14 l7 6 l8 -11 l8 11 l7 -6 l-2 14 z" fill={CREAM} />
          <rect x="-13" y="7" width="26" height="4.5" rx="2" fill={AMBER_DEEP} />
          <circle cx="-8" cy="-9" r="2" fill={CYAN} />
          <circle cx="0" cy="-14" r="2" fill={CYAN} />
          <circle cx="8" cy="-9" r="2" fill={CYAN} />
        </g>
      );
    case 'word-builder':
      return (
        <g>
          <rect x="-14" y="0" width="13" height="13" rx="3" fill={CREAM} />
          <rect x="1" y="0" width="13" height="13" rx="3" fill={CYAN} />
          <rect x="-6.5" y="-14" width="13" height="13" rx="3" fill={CREAM} opacity="0.85" />
          <text x="-7.5" y="10" fontSize="8" fontWeight="700" textAnchor="middle" fill={TEAL} fontFamily="'Noto Sans Tamil', sans-serif">அ</text>
          <text x="7.5" y="10" fontSize="8" fontWeight="700" textAnchor="middle" fill={TEAL} fontFamily="'Noto Sans Tamil', sans-serif">க</text>
          <text x="0" y="-4" fontSize="8" fontWeight="700" textAnchor="middle" fill={TEAL} fontFamily="'Noto Sans Tamil', sans-serif">எ</text>
        </g>
      );
    case 'sound-matcher':
      return (
        <g>
          <path d="M-12 -5 l6 0 l7 -7 l0 24 l-7 -7 l-6 0 z" fill={CREAM} />
          <path d="M6 -6 q5 6 0 12" stroke={CYAN} strokeWidth="2.6" fill="none" strokeLinecap="round" />
          <path d="M11 -10 q8 10 0 20" stroke={CYAN} strokeWidth="2.6" fill="none" strokeLinecap="round" opacity="0.6" />
        </g>
      );
    case 'letter-spotter':
      return (
        <g>
          <circle cx="-2" cy="-3" r="10" fill="none" stroke={CREAM} strokeWidth="3.4" />
          <text x="-2" y="1.5" fontSize="11" fontWeight="700" textAnchor="middle" fill={CYAN} fontFamily="'Noto Sans Tamil', sans-serif">எ</text>
          <rect x="5" y="6" width="12" height="4.6" rx="2.3" fill={AMBER_DEEP} transform="rotate(45 5 6)" />
        </g>
      );
  }
};

export const AchievementBadge: React.FC<{
  kind: BadgeKind;
  size?: number;
  earned?: boolean;
  className?: string;
}> = ({ kind, size = 96, earned = true, className }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 96 96"
    className={className}
    role="img"
    aria-label={`${BADGE_META[kind].en} badge${earned ? '' : ' (locked)'}`}
    style={earned ? undefined : { filter: 'saturate(0.15)', opacity: 0.45 }}
  >
    <defs>
      <radialGradient id={`bg-${kind}`} cx="50%" cy="35%" r="80%">
        <stop offset="0%" stopColor={TEAL_LIGHT} />
        <stop offset="100%" stopColor={TEAL} />
      </radialGradient>
      <linearGradient id={`ring-${kind}`} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stopColor={AMBER} />
        <stop offset="100%" stopColor={AMBER_DEEP} />
      </linearGradient>
    </defs>
    {/* ribbon tails */}
    <path d="M34 66 l-8 22 l12 -7 l4 9 l6 -16 z" fill={AMBER_DEEP} />
    <path d="M62 66 l8 22 l-12 -7 l-4 9 l-6 -16 z" fill={AMBER} />
    {/* medal */}
    <circle cx="48" cy="42" r="33" fill={`url(#ring-${kind})`} />
    <circle cx="48" cy="42" r="27" fill={`url(#bg-${kind})`} />
    {/* scalloped inner tick ring */}
    <g stroke={AMBER} strokeWidth="1.6" opacity="0.55" strokeLinecap="round">
      {Array.from({ length: 12 }).map((_, i) => {
        const a = (i / 12) * Math.PI * 2;
        const x1 = 48 + Math.cos(a) * 24, y1 = 42 + Math.sin(a) * 24;
        const x2 = 48 + Math.cos(a) * 26.5, y2 = 42 + Math.sin(a) * 26.5;
        return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} />;
      })}
    </g>
    {/* per-badge symbol */}
    <g transform="translate(48 42)">
      <Symbol kind={kind} />
    </g>
    {/* top-left sheen */}
    <path d="M27 24 a33 33 0 0 1 22 -8" stroke="#FFFFFF" strokeWidth="2.4" fill="none" strokeLinecap="round" opacity="0.35" />
  </svg>
);
