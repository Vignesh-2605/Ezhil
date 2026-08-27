import React from 'react';

/**
 * Journey-map scenery layers — Tamil Nadu village at dusk, split into
 * separately-translatable SVG layers for the existing parallax
 * (far = moon/stars/mountains, near = palms/houses). All decorative:
 * aria-hidden, pointer-events none, transform-only animation upstream.
 */

const TEAL = '#0F2E33';
const TEAL_LIGHT = '#17444B';
const CREAM = '#FBF3E0';
const CYAN = '#62F9EE';
const AMBER = '#FFB955';
const DUSK = '#0A1A1E';

/** Far layer: crescent moon, stars, distant mountain ridge. */
export const SceneryFar: React.FC<{ className?: string }> = ({ className }) => (
  <svg aria-hidden className={className} viewBox="0 0 400 300" fill="none">
    {/* moon */}
    <g transform="translate(356 26)" opacity="0.7">
      <circle r="17" fill={CREAM} />
      <circle cx="-6.5" cy="-3.5" r="15.5" fill="#0D1319" />
    </g>
    {/* stars */}
    <g fill={CREAM}>
      <circle cx="40" cy="40" r="1.6" opacity="0.55" />
      <circle cx="120" cy="24" r="1.2" opacity="0.4" />
      <circle cx="210" cy="60" r="1.4" opacity="0.5" />
      <circle cx="280" cy="30" r="1.1" opacity="0.35" />
      <circle cx="70" cy="90" r="1.2" opacity="0.35" />
    </g>
    <g fill={CYAN}>
      <circle cx="160" cy="44" r="1.5" opacity="0.5" />
      <circle cx="360" cy="120" r="1.3" opacity="0.4" />
      <circle cx="24" cy="140" r="1.4" opacity="0.45" />
    </g>
    {/* distant ridge */}
    <path d="M0 230 q60 -50 120 -28 q50 18 90 -8 q60 -38 190 -2 l0 108 l-400 0 z" fill={TEAL} opacity="0.5" />
  </svg>
);

/** Near layer: rolling hills, palmyra palms, village houses with lit windows. */
export const SceneryNear: React.FC<{ className?: string }> = ({ className }) => (
  <svg aria-hidden className={className} viewBox="0 0 400 260" fill="none">
    {/* hills */}
    <path d="M0 160 q90 -44 180 -14 q100 32 220 0 l0 114 l-400 0 z" fill={TEAL_LIGHT} opacity="0.45" />
    {/* palmyra palm — left */}
    <g transform="translate(52 120)" fill={DUSK} opacity="0.9">
      <path d="M-3 0 q1 40 -2 66 l10 0 q-3 -26 -2 -66 z" />
      <path d="M0 2 q-22 -16 -36 -7 q17 3 30 13 z" />
      <path d="M0 2 q22 -16 36 -7 q-17 3 -30 13 z" />
      <path d="M0 0 q-10 -22 -25 -24 q11 10 20 26 z" />
      <path d="M0 0 q10 -22 25 -24 q-11 10 -20 26 z" />
      <path d="M0 -2 q0 -24 -4 -32 q8 9 9 32 z" />
    </g>
    {/* palm — right, smaller */}
    <g transform="translate(352 150) scale(0.7)" fill={DUSK} opacity="0.85">
      <path d="M-3 0 q1 40 -2 66 l10 0 q-3 -26 -2 -66 z" />
      <path d="M0 2 q-22 -16 -36 -7 q17 3 30 13 z" />
      <path d="M0 2 q22 -16 36 -7 q-17 3 -30 13 z" />
      <path d="M0 0 q-10 -22 -25 -24 q11 10 20 26 z" />
      <path d="M0 0 q10 -22 25 -24 q-11 10 -20 26 z" />
    </g>
    {/* village houses */}
    <g transform="translate(250 168)">
      <rect x="0" y="10" width="44" height="30" rx="3" fill={DUSK} />
      <path d="M-4 12 l26 -16 l26 16 z" fill={TEAL_LIGHT} />
      <rect x="16" y="21" width="11" height="10" rx="1.5" fill={AMBER} opacity="0.85" />
    </g>
    <g transform="translate(305 182) scale(0.78)">
      <rect x="0" y="10" width="44" height="30" rx="3" fill="#0C2026" />
      <path d="M-4 12 l26 -16 l26 16 z" fill={TEAL_LIGHT} opacity="0.8" />
      <rect x="16" y="21" width="11" height="10" rx="1.5" fill={AMBER} opacity="0.65" />
    </g>
    {/* fireflies among the houses */}
    <circle cx="230" cy="150" r="1.6" fill={CYAN} opacity="0.7" />
    <circle cx="90" cy="180" r="1.3" fill={CYAN} opacity="0.5" />
    <circle cx="330" cy="140" r="1.2" fill={CYAN} opacity="0.45" />
  </svg>
);
