import React from 'react';

/**
 * Ezhil brand marks — all inline SVG, zero assets.
 *
 * Shape language (see docs/design-brief.md): rounded rect + circle + rounded
 * triangle only, 2 teal fills + cream + one accent, no outlines, silhouette
 * legible at 48px.
 */

const TEAL = '#0F2E33';
const TEAL_LIGHT = '#17444B';
const CREAM = '#FBF3E0';
const CYAN = '#62F9EE';
const AMBER = '#FFB955';

/** Ezhilan owl-face app mark on a rounded-square tile. */
export const EzhilMark: React.FC<{ size?: number; className?: string; title?: string }> = ({
  size = 40,
  className,
  title = 'Ezhil',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 64 64"
    className={className}
    role="img"
    aria-label={title}
  >
    <defs>
      <radialGradient id="ezm-bg" cx="50%" cy="30%" r="90%">
        <stop offset="0%" stopColor={TEAL_LIGHT} />
        <stop offset="100%" stopColor={TEAL} />
      </radialGradient>
    </defs>
    <rect width="64" height="64" rx="15" fill="url(#ezm-bg)" />
    {/* fireflies */}
    <circle cx="10" cy="12" r="1.3" fill={CYAN} opacity="0.7" />
    <circle cx="55" cy="9" r="1" fill={CYAN} opacity="0.5" />
    <circle cx="52" cy="55" r="1.1" fill={CYAN} opacity="0.4" />
    {/* ear tufts */}
    <path d="M15 16 q-1.5 -7 5 -8.5 q1.5 5.5 -0.5 8 z" fill={TEAL_LIGHT} />
    <path d="M49 16 q1.5 -7 -5 -8.5 q-1.5 5.5 0.5 8 z" fill={TEAL_LIGHT} />
    {/* face plate */}
    <ellipse cx="32" cy="34" rx="23" ry="24" fill={TEAL_LIGHT} />
    {/* brows */}
    <path d="M12 20 q9 -6 17 -1.5" stroke={CYAN} strokeWidth="2.6" fill="none" strokeLinecap="round" />
    <path d="M35 18.5 q8 -4.5 17 1.5" stroke={CYAN} strokeWidth="2.6" fill="none" strokeLinecap="round" />
    {/* big owl eyes — nearly touching */}
    <circle cx="22" cy="32" r="11" fill={CREAM} />
    <circle cx="42" cy="32" r="11" fill={CREAM} />
    <circle cx="23.5" cy="33" r="5.4" fill={TEAL} />
    <circle cx="40.5" cy="33" r="5.4" fill={TEAL} />
    <circle cx="25.4" cy="31.2" r="1.8" fill="#fff" />
    <circle cx="42.4" cy="31.2" r="1.8" fill="#fff" />
    <circle cx="22.2" cy="35" r="0.9" fill="#fff" opacity="0.7" />
    <circle cx="39.2" cy="35" r="0.9" fill="#fff" opacity="0.7" />
    {/* beak — small rounded triangle */}
    <path d="M32 42 l-4.2 3.2 q4.2 5.4 8.4 0 z" fill={AMBER} />
    {/* belly hint */}
    <path d="M20 55 q12 7 24 0 l0 9 l-24 0 z" fill={CREAM} opacity="0.14" />
  </svg>
);

/**
 * Full wordmark: mark + எழில் + EZHIL. `stacked` centres everything
 * vertically (login/onboarding); default is a horizontal lockup (sidebar).
 */
export const EzhilWordmark: React.FC<{
  markSize?: number;
  stacked?: boolean;
  className?: string;
}> = ({ markSize = 44, stacked = false, className }) => (
  <div
    className={`flex items-center gap-3 ${stacked ? 'flex-col gap-2 text-center' : ''} ${className ?? ''}`}
  >
    <EzhilMark size={markSize} />
    <div className={stacked ? 'space-y-0.5' : ''}>
      <p
        className="font-display-tamil font-extrabold leading-none text-white"
        style={{ fontSize: markSize * 0.62 }}
      >
        எழில்
      </p>
      <p
        className="font-mono-metadata uppercase text-primary-fixed"
        style={{ fontSize: markSize * 0.22, letterSpacing: '0.42em', marginLeft: '0.1em' }}
      >
        Ezhil
      </p>
    </div>
  </div>
);
