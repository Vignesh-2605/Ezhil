import React from 'react';

/**
 * Empty-state illustrations — quiet, warm scenes (never sad). Shape
 * language: rounded forms, 2 teals + cream + one accent, no outlines.
 */

const TEAL = '#0F2E33';
const TEAL_LIGHT = '#17444B';
const CREAM = '#FBF3E0';
const CYAN = '#62F9EE';
const AMBER = '#FFB955';

/** No lessons yet — a closed book resting under a crescent moon, bookmark ready. */
export const EmptyLessonsArt: React.FC<{ className?: string; size?: number }> = ({ className, size = 150 }) => (
  <svg width={size} height={size * 0.78} viewBox="0 0 180 140" className={className} role="img" aria-label="A book waiting to be opened">
    {/* crescent moon */}
    <g transform="translate(138 30)">
      <circle r="15" fill={CREAM} opacity="0.9" />
      <circle cx="-6" cy="-3" r="13.5" fill="#0E141C" />
    </g>
    <circle cx="36" cy="26" r="1.5" fill={CYAN} opacity="0.7" />
    <circle cx="62" cy="44" r="1.1" fill={CYAN} opacity="0.45" />
    <circle cx="160" cy="72" r="1.3" fill={CREAM} opacity="0.4" />
    {/* ground shadow */}
    <ellipse cx="86" cy="118" rx="52" ry="8" fill={TEAL} opacity="0.6" />
    {/* stacked closed books */}
    <g transform="translate(86 96)">
      <rect x="-44" y="4" width="88" height="18" rx="6" fill={TEAL_LIGHT} />
      <rect x="-38" y="7" width="6" height="12" rx="3" fill={CREAM} opacity="0.35" />
      <rect x="-40" y="-16" width="80" height="18" rx="6" fill={TEAL} />
      <rect x="-34" y="-13" width="6" height="12" rx="3" fill={CREAM} opacity="0.3" />
      {/* top book with amber bookmark */}
      <rect x="-36" y="-36" width="72" height="18" rx="6" fill={TEAL_LIGHT} />
      <rect x="18" y="-36" width="8" height="26" rx="3" fill={AMBER} />
      <path d="M18 -10 l4 -5 l4 5 z" fill={AMBER} />
      <rect x="-30" y="-33" width="6" height="12" rx="3" fill={CREAM} opacity="0.35" />
    </g>
    {/* a single firefly hovering over the books */}
    <g>
      <circle cx="86" cy="42" r="6" fill={CYAN} opacity="0.15" />
      <circle cx="86" cy="42" r="2" fill={CYAN} opacity="0.9" />
    </g>
  </svg>
);

/** No students yet — a cosy branch with one owl and a spot saved for more. */
export const EmptyStudentsArt: React.FC<{ className?: string; size?: number }> = ({ className, size = 150 }) => (
  <svg width={size} height={size * 0.78} viewBox="0 0 180 140" className={className} role="img" aria-label="An owl saving a spot on a branch">
    <circle cx="30" cy="28" r="1.4" fill={CYAN} opacity="0.6" />
    <circle cx="150" cy="24" r="1.2" fill={CREAM} opacity="0.5" />
    {/* branch */}
    <path d="M8 104 q60 -10 164 -2" stroke={TEAL_LIGHT} strokeWidth="10" fill="none" strokeLinecap="round" />
    <path d="M120 100 q10 -14 26 -18" stroke={TEAL_LIGHT} strokeWidth="6" fill="none" strokeLinecap="round" />
    {/* leaves */}
    <ellipse cx="150" cy="78" rx="9" ry="5" fill={TEAL_LIGHT} transform="rotate(-30 150 78)" />
    <ellipse cx="160" cy="86" rx="8" ry="4.5" fill={TEAL} transform="rotate(-16 160 86)" />
    {/* perched owl (v2 proportions) */}
    <g transform="translate(58 74)">
      <ellipse cx="0" cy="12" rx="14" ry="14" fill={TEAL} />
      <circle cx="0" cy="0" r="13" fill={TEAL} />
      <path d="M-9 -9 q-1 -4 3 -5 q0.5 3 -0.5 4.5 z" fill={TEAL} />
      <path d="M9 -9 q1 -4 -3 -5 q-0.5 3 0.5 4.5 z" fill={TEAL} />
      <ellipse cx="0" cy="15" rx="8" ry="8" fill={CREAM} />
      <circle cx="-6.2" cy="0" r="6.2" fill="white" />
      <circle cx="6.2" cy="0" r="6.2" fill="white" />
      <circle cx="-5.4" cy="0.4" r="3" fill={TEAL} />
      <circle cx="7" cy="0.4" r="3" fill={TEAL} />
      <circle cx="-4.4" cy="-0.7" r="1" fill="white" />
      <circle cx="8" cy="-0.7" r="1" fill="white" />
      <path d="M0 5.5 l-2 1.4 q2 2.6 4 0 z" fill={AMBER} />
      {/* feet gripping branch */}
      <path d="M-5 25 l0 5 M5 25 l0 5" stroke={AMBER} strokeWidth="2.2" strokeLinecap="round" />
    </g>
    {/* dotted "reserved seat" outline beside the owl */}
    <g transform="translate(116 78)">
      <circle cx="0" cy="6" r="15" fill="none" stroke={CYAN} strokeWidth="2" strokeDasharray="4 5" opacity="0.55" strokeLinecap="round" />
      <path d="M-4 6 l3 3 l6 -6" stroke={CYAN} strokeWidth="2.4" fill="none" strokeLinecap="round" strokeLinejoin="round" opacity="0.7" />
    </g>
  </svg>
);

/** No activity yet — sleeping owl under moon and stars (rest, not absence). */
export const EmptyActivityArt: React.FC<{ className?: string; size?: number }> = ({ className, size = 150 }) => (
  <svg width={size} height={size * 0.78} viewBox="0 0 180 140" className={className} role="img" aria-label="A sleeping owl under the moon">
    <g transform="translate(42 32)">
      <circle r="16" fill={CREAM} opacity="0.9" />
      <circle cx="-6.5" cy="-3" r="14.5" fill="#0E141C" />
    </g>
    <circle cx="120" cy="22" r="1.5" fill={CYAN} opacity="0.6" />
    <circle cx="148" cy="42" r="1.2" fill={CREAM} opacity="0.5" />
    <circle cx="102" cy="52" r="1" fill={CYAN} opacity="0.4" />
    {/* zzz */}
    <g fill={CYAN} opacity="0.8" fontFamily="'DM Sans', sans-serif" fontWeight="700">
      <text x="122" y="66" fontSize="13">z</text>
      <text x="132" y="54" fontSize="16">Z</text>
    </g>
    {/* ground */}
    <ellipse cx="90" cy="122" rx="56" ry="8" fill={TEAL} opacity="0.6" />
    {/* sleeping owl — round, tucked in */}
    <g transform="translate(90 92)">
      <ellipse cx="0" cy="10" rx="22" ry="20" fill={TEAL} />
      <circle cx="0" cy="-4" r="18" fill={TEAL} />
      <path d="M-13 -16 q-1.5 -5.5 4 -7 q1 4.5 -1 6.5 z" fill={TEAL} />
      <path d="M13 -16 q1.5 -5.5 -4 -7 q-1 4.5 1 6.5 z" fill={TEAL} />
      <ellipse cx="0" cy="14" rx="11" ry="10" fill={CREAM} />
      {/* closed eyes — gentle arcs */}
      <path d="M-14 -4 q5 4 10 0" stroke={CREAM} strokeWidth="2.6" fill="none" strokeLinecap="round" />
      <path d="M4 -4 q5 4 10 0" stroke={CREAM} strokeWidth="2.6" fill="none" strokeLinecap="round" />
      <path d="M0 3 l-2.2 1.6 q2.2 2.8 4.4 0 z" fill={AMBER} />
      {/* wing wrapped around */}
      <path d="M-22 6 q-2 14 10 20" stroke={TEAL_LIGHT} strokeWidth="7" fill="none" strokeLinecap="round" />
      <path d="M22 6 q2 14 -10 20" stroke={TEAL_LIGHT} strokeWidth="7" fill="none" strokeLinecap="round" />
    </g>
  </svg>
);
