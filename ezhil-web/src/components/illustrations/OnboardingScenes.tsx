import React from 'react';

/**
 * Onboarding scene illustrations — coded SVG in the Ezhil shape language
 * (rounded rect / circle / rounded triangle, 2 teals + cream + one accent,
 * no outlines). One scene per slide: Read (cyan) · Play (purple) · Win (amber).
 */

const TEAL = '#0F2E33';
const TEAL_LIGHT = '#17444B';
const CREAM = '#FBF3E0';
const CYAN = '#62F9EE';
const AMBER = '#FFB955';
const PURPLE = '#A78BFA';

/** Static mini-Ezhilan used inside scenes (v2 proportions, no animation). */
const SceneOwl: React.FC<{ x?: number; y?: number; s?: number; lidsDown?: boolean }> = ({
  x = 0, y = 0, s = 1, lidsDown = false,
}) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <ellipse cx="0" cy="14" rx="15" ry="15" fill={TEAL} />
    <circle cx="0" cy="0" r="14" fill={TEAL} />
    <path d="M-10 -10 q-1 -4.5 3 -5.5 q0.5 3.5 -0.5 5 z" fill={TEAL} />
    <path d="M10 -10 q1 -4.5 -3 -5.5 q-0.5 3.5 0.5 5 z" fill={TEAL} />
    <ellipse cx="0" cy="17" rx="8.5" ry="8.5" fill={CREAM} />
    <circle cx="-6.7" cy="0" r="6.7" fill="white" />
    <circle cx="6.7" cy="0" r="6.7" fill="white" />
    {lidsDown ? (
      <>
        <path d="M-12 0 q5.3 3.4 10.6 0" stroke={TEAL} strokeWidth="2.4" fill="none" strokeLinecap="round" />
        <path d="M1.4 0 q5.3 3.4 10.6 0" stroke={TEAL} strokeWidth="2.4" fill="none" strokeLinecap="round" />
      </>
    ) : (
      <>
        <circle cx="-6" cy="0.4" r="3.3" fill={TEAL} />
        <circle cx="7.4" cy="0.4" r="3.3" fill={TEAL} />
        <circle cx="-4.9" cy="-0.8" r="1.1" fill="white" />
        <circle cx="8.5" cy="-0.8" r="1.1" fill="white" />
      </>
    )}
    <path d="M0 6 l-2.1 1.5 q2.1 2.8 4.2 0 z" fill={AMBER} />
  </g>
);

const Firefly: React.FC<{ cx: number; cy: number; r?: number; o?: number }> = ({ cx, cy, r = 1.6, o = 0.8 }) => (
  <g>
    <circle cx={cx} cy={cy} r={r * 2.6} fill={CYAN} opacity={o * 0.18} />
    <circle cx={cx} cy={cy} r={r} fill={CYAN} opacity={o} />
  </g>
);

/** Slide 1 — படிக்கலாம் / Let's Read: Ezhilan on a book under the moon. */
export const SceneRead: React.FC<{ className?: string; size?: number }> = ({ className, size = 200 }) => (
  <svg width={size} height={size} viewBox="0 0 200 200" className={className} role="img" aria-label="Owl reading a book">
    {/* moon */}
    <g transform="translate(152 38)">
      <circle r="20" fill={CREAM} opacity="0.92" />
      <circle cx="-8" cy="-5" r="18" fill={TEAL} opacity="0" />
      <circle cx="-9" cy="-4" r="17.5" fill="#0E141C" />
    </g>
    <Firefly cx={34} cy={42} />
    <Firefly cx={62} cy={24} r={1.2} o={0.55} />
    <Firefly cx={172} cy={92} r={1.3} o={0.5} />
    <Firefly cx={22} cy={110} r={1.4} o={0.65} />
    {/* ground hill */}
    <path d="M-4 176 q60 -26 104 -12 q56 16 104 2 l0 40 l-208 0 z" fill={TEAL_LIGHT} opacity="0.55" />
    {/* open book */}
    <g transform="translate(100 148)">
      <path d="M-52 0 q26 -14 52 -6 q26 -8 52 6 l0 14 q-26 -12 -52 -5 q-26 -7 -52 5 z" fill={TEAL_LIGHT} />
      <path d="M-46 -2 q23 -11 46 -4 l0 12 q-23 -6 -46 4 z" fill={CREAM} />
      <path d="M46 -2 q-23 -11 -46 -4 l0 12 q23 -6 46 4 z" fill={CREAM} />
      {/* text lines */}
      <g stroke={TEAL} strokeWidth="2" strokeLinecap="round" opacity="0.5">
        <path d="M-38 2 q16 -6 32 -3" fill="none" />
        <path d="M-38 7 q13 -5 26 -2.5" fill="none" />
        <path d="M38 2 q-16 -6 -32 -3" fill="none" />
        <path d="M38 7 q-13 -5 -26 -2.5" fill="none" />
      </g>
      {/* cyan reading glow rising from the page */}
      <ellipse cx="0" cy="-10" rx="34" ry="14" fill={CYAN} opacity="0.10" />
    </g>
    {/* owl perched behind the book */}
    <SceneOwl x={100} y={104} s={1.5} />
    {/* floating letters */}
    <text x="52" y="76" fontSize="17" fill={CYAN} opacity="0.85" fontFamily="'Noto Sans Tamil', sans-serif">அ</text>
    <text x="140" y="64" fontSize="14" fill={AMBER} opacity="0.8" fontFamily="'Noto Sans Tamil', sans-serif">க</text>
    <text x="36" y="146" fontSize="12" fill={CREAM} opacity="0.5" fontFamily="'Noto Sans Tamil', sans-serif">ழ</text>
  </svg>
);

/** Slide 2 — விளையாடலாம் / Let's Play: letter blocks, sound wave, magnifier. */
export const ScenePlay: React.FC<{ className?: string; size?: number }> = ({ className, size = 200 }) => (
  <svg width={size} height={size} viewBox="0 0 200 200" className={className} role="img" aria-label="Tamil letter game blocks">
    <Firefly cx={170} cy={30} r={1.4} o={0.6} />
    <Firefly cx={26} cy={52} r={1.3} o={0.5} />
    {/* ground */}
    <path d="M-4 172 q80 -22 208 -4 l0 36 l-208 0 z" fill={TEAL_LIGHT} opacity="0.55" />
    {/* letter blocks — rounded rects, one purple accent */}
    <g>
      <rect x="42" y="118" width="46" height="46" rx="10" fill={TEAL_LIGHT} />
      <text x="65" y="151" fontSize="24" fill={CREAM} textAnchor="middle" fontFamily="'Noto Sans Tamil', sans-serif" fontWeight="700">சொ</text>
      <rect x="96" y="118" width="46" height="46" rx="10" fill={PURPLE} />
      <text x="119" y="151" fontSize="24" fill={TEAL} textAnchor="middle" fontFamily="'Noto Sans Tamil', sans-serif" fontWeight="700">ல்</text>
      <rect x="70" y="66" width="46" height="46" rx="10" fill={TEAL} />
      <text x="93" y="99" fontSize="24" fill={CYAN} textAnchor="middle" fontFamily="'Noto Sans Tamil', sans-serif" fontWeight="700">எ</text>
    </g>
    {/* owl peeking from behind the top block */}
    <SceneOwl x={146} y={84} s={1.05} />
    {/* sound waves from the owl */}
    <g stroke={PURPLE} fill="none" strokeLinecap="round" opacity="0.75">
      <path d="M168 70 q6 14 0 28" strokeWidth="3" />
      <path d="M177 64 q9 20 0 40" strokeWidth="3" opacity="0.5" />
    </g>
    {/* magnifier resting against blocks */}
    <g transform="translate(38 92) rotate(-18)">
      <circle r="13" fill="none" stroke={CREAM} strokeWidth="4" opacity="0.9" />
      <rect x="9" y="9" width="16" height="6" rx="3" fill={AMBER} transform="rotate(45 9 9)" />
    </g>
    {/* sparkle stars */}
    <path d="M160 130 l2.2 5 5 2.2 -5 2.2 -2.2 5 -2.2 -5 -5 -2.2 5 -2.2 z" fill={AMBER} opacity="0.85" />
    <path d="M30 150 l1.6 3.6 3.6 1.6 -3.6 1.6 -1.6 3.6 -1.6 -3.6 -3.6 -1.6 3.6 -1.6 z" fill={CYAN} opacity="0.6" />
  </svg>
);

/** Slide 3 — வெல்லலாம் / Let's Win: trophy, stars, celebrating owl. */
export const SceneWin: React.FC<{ className?: string; size?: number }> = ({ className, size = 200 }) => (
  <svg width={size} height={size} viewBox="0 0 200 200" className={className} role="img" aria-label="Trophy and celebrating owl">
    <Firefly cx={30} cy={36} r={1.4} o={0.6} />
    <Firefly cx={174} cy={52} r={1.2} o={0.5} />
    {/* podium ground */}
    <path d="M-4 174 q80 -18 208 -2 l0 32 l-208 0 z" fill={TEAL_LIGHT} opacity="0.55" />
    {/* trophy */}
    <g transform="translate(100 108)">
      {/* cup */}
      <path d="M-26 -34 l52 0 q0 30 -26 36 q-26 -6 -26 -36 z" fill={AMBER} />
      <path d="M-26 -34 q-16 0 -14 14 q2 12 15 13" fill="none" stroke={AMBER} strokeWidth="6" strokeLinecap="round" />
      <path d="M26 -34 q16 0 14 14 q-2 12 -15 13" fill="none" stroke={AMBER} strokeWidth="6" strokeLinecap="round" />
      {/* highlight */}
      <path d="M-14 -28 q-2 18 8 26" stroke={CREAM} strokeWidth="4" fill="none" strokeLinecap="round" opacity="0.5" />
      {/* stem + base */}
      <rect x="-6" y="2" width="12" height="12" rx="3" fill="#D99B45" />
      <rect x="-20" y="14" width="40" height="10" rx="5" fill={TEAL_LIGHT} />
      {/* star on cup */}
      <path d="M0 -22 l3 6.6 7 1 -5 5 1.2 7 -6.2 -3.4 -6.2 3.4 1.2 -7 -5 -5 7 -1 z" fill={TEAL} />
    </g>
    {/* celebrating owl beside trophy — happy closed eyes */}
    <SceneOwl x={44} y={134} s={1.15} lidsDown />
    {/* burst stars */}
    <path d="M100 34 l3.4 7.6 7.6 3.4 -7.6 3.4 -3.4 7.6 -3.4 -7.6 -7.6 -3.4 7.6 -3.4 z" fill={AMBER} />
    <path d="M148 70 l2.4 5.4 5.4 2.4 -5.4 2.4 -2.4 5.4 -2.4 -5.4 -5.4 -2.4 5.4 -2.4 z" fill={CYAN} opacity="0.85" />
    <path d="M56 62 l2 4.5 4.5 2 -4.5 2 -2 4.5 -2 -4.5 -4.5 -2 4.5 -2 z" fill={CREAM} opacity="0.7" />
    {/* confetti dots */}
    <circle cx="132" cy="42" r="2.4" fill={PURPLE} opacity="0.8" />
    <circle cx="70" cy="40" r="2" fill={CYAN} opacity="0.7" />
    <circle cx="162" cy="118" r="2.2" fill={AMBER} opacity="0.7" />
  </svg>
);
