import React, {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react';
import { motion, useMotionValue, useReducedMotion, useSpring } from 'motion/react';

/**
 * Ezhilan — the Ezhil owl mascot, drawn entirely in SVG and animated with
 * springs. No external assets; every state from the mascot spec is
 * implemented parametrically.
 *
 * v2 proportions (docs/design-brief.md): single egg silhouette, eyes ≈45%
 * of head height and nearly touching, smaller beak, thicker brows — the
 * eyes carry all the emotion.
 *
 * Continuous modes (prop):  idle · listening · thinking · reading · sleepy
 * One-shot triggers (ref):  wave · celebrateSmall · celebrateBig ·
 *                           encourage · point · (poke fires on tap)
 *
 * Design rules honoured:
 *  - ENCOURAGE is warm ("almost!"), never sad or head-shaking.
 *  - All motion collapses under prefers-reduced-motion.
 *  - Eyes track the pointer (whole document) with soft springs.
 */

export type EzhilanMode = 'idle' | 'listening' | 'thinking' | 'reading' | 'sleepy';
export type EzhilanTrigger = 'wave' | 'celebrateSmall' | 'celebrateBig' | 'encourage' | 'point';

export interface EzhilanHandle {
  trigger: (name: EzhilanTrigger) => void;
}

const SPRING_SOFT = { type: 'spring', stiffness: 120, damping: 14 } as const;
const SPRING_SNAP = { type: 'spring', stiffness: 400, damping: 18 } as const;

// Eye geometry — shared by pupils, lids, happy arcs and reading glasses.
const EYE_Y = 36;
const EYE_R = 13.5;
const EYE_LX = 36.5;
const EYE_RX = 63.5;

export const Ezhilan = forwardRef<EzhilanHandle, {
  mode?: EzhilanMode;
  size?: number;
  className?: string;
  /** Set false to disable pointer-following eyes (e.g. many owls on screen). */
  trackPointer?: boolean;
}>(({ mode = 'idle', size = 110, className, trackPointer = true }, ref) => {
  const reduce = useReducedMotion();
  const svgRef = useRef<SVGSVGElement>(null);

  // One-shot overlay state (plays over the current mode, then clears)
  const [oneShot, setOneShot] = useState<EzhilanTrigger | 'poke' | null>(null);
  const oneShotTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const play = useCallback((name: EzhilanTrigger | 'poke', ms: number) => {
    clearTimeout(oneShotTimer.current);
    setOneShot(name);
    oneShotTimer.current = setTimeout(() => setOneShot(null), ms);
  }, []);

  useImperativeHandle(ref, () => ({
    trigger: (name: EzhilanTrigger) =>
      play(name, name === 'celebrateBig' ? 2000 : name === 'encourage' ? 1500 : 1200),
  }), [play]);

  useEffect(() => () => clearTimeout(oneShotTimer.current), []);

  // Random blinks while awake (3–6 s apart, 130 ms each)
  const [blink, setBlink] = useState(false);
  useEffect(() => {
    if (reduce) return;
    let alive = true;
    let t: ReturnType<typeof setTimeout>;
    const loop = () => {
      t = setTimeout(() => {
        if (!alive) return;
        setBlink(true);
        setTimeout(() => { if (alive) setBlink(false); }, 130);
        loop();
      }, 3000 + Math.random() * 3000);
    };
    loop();
    return () => { alive = false; clearTimeout(t); };
  }, [reduce]);

  // ── Eye tracking ──────────────────────────────────────────────────────────
  const lookX = useMotionValue(0);
  const lookY = useMotionValue(0);
  const pupilX = useSpring(lookX, { stiffness: 200, damping: 20 });
  const pupilY = useSpring(lookY, { stiffness: 200, damping: 20 });

  useEffect(() => {
    if (!trackPointer || reduce) return;
    const onMove = (e: PointerEvent) => {
      const r = svgRef.current?.getBoundingClientRect();
      if (!r) return;
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      // Normalised −1..1 with distance falloff
      lookX.set(Math.max(-1, Math.min(1, (e.clientX - cx) / 300)) * 4.5);
      lookY.set(Math.max(-1, Math.min(1, (e.clientY - cy) / 300)) * 3.5);
    };
    window.addEventListener('pointermove', onMove);
    return () => window.removeEventListener('pointermove', onMove);
  }, [trackPointer, reduce, lookX, lookY]);

  // ── Derived pose per state ────────────────────────────────────────────────
  const active = oneShot ?? mode;

  const pose = {
    // whole-owl transform
    y: 0, rotate: 0, scaleY: 1,
    // head
    headTilt: 0,
    // wings (deg, positive = raised)
    wingL: 0, wingR: 0,
    // eyelids 0 = open, 1 = closed
    lids: 0,
    // brows raise
    brows: 0,
    ...( {
      idle:           {},
      listening:      { headTilt: -10, wingR: 55, brows: 1.5 },
      thinking:       { headTilt: 6, wingR: 35, brows: 3, lids: 0.15 },
      reading:        { headTilt: 3, lids: 0.2 },
      sleepy:         { lids: 0.75, headTilt: 4 },
      wave:           { wingR: 120, brows: 2, rotate: -3 },
      celebrateSmall: { y: -8, wingL: 60, wingR: 60, brows: 3, scaleY: 1.04 },
      celebrateBig:   { y: -16, wingL: 130, wingR: 130, brows: 4, rotate: 4, scaleY: 1.06 },
      encourage:      { headTilt: -8, wingR: 70, brows: 2.5 },
      point:          { wingR: 95, headTilt: -4 },
      poke:           { scaleY: 0.82, y: 4, brows: 4 },
    } as Record<string, Partial<Record<string, number>>> )[active],
  };

  // Idle breathing + blink loop (declarative via motion keyframe props)
  const breathing = !reduce && (active === 'idle' || active === 'reading' || active === 'sleepy');

  const teal = '#0F2E33';
  const tealLight = '#17444B';
  const cream = '#FBF3E0';
  const cyan = '#62F9EE';
  const amber = '#FFB955';

  return (
    <motion.svg
      ref={svgRef}
      className={className}
      width={size}
      height={size}
      viewBox="0 0 100 100"
      style={{ cursor: 'pointer', overflow: 'visible', touchAction: 'manipulation' }}
      onPointerDown={() => play('poke', 900)}
      animate={{ y: pose.y, rotate: pose.rotate }}
      transition={reduce ? { duration: 0 } : SPRING_SNAP}
      role="img"
      aria-label="Ezhilan the owl"
    >
      {/* Celebration star burst */}
      {oneShot === 'celebrateBig' && !reduce && (
        <g>
          {[...Array(6)].map((_, i) => {
            const a = (i / 6) * Math.PI * 2;
            return (
              <motion.text
                key={i}
                fontSize="9"
                initial={{ x: 50, y: 42, opacity: 1, scale: 0.4 }}
                animate={{
                  x: 50 + Math.cos(a) * 46,
                  y: 42 + Math.sin(a) * 40,
                  opacity: 0,
                  scale: 1.2,
                  rotate: 90,
                }}
                transition={{ duration: 1.1, ease: 'easeOut' }}
              >
                {i % 2 === 0 ? '⭐' : '✨'}
              </motion.text>
            );
          })}
        </g>
      )}

      {/* Body (breathes) */}
      <motion.g
        style={{ originX: '50px', originY: '78px' }}
        animate={breathing ? { scaleY: [1, 1.025, 1] } : { scaleY: pose.scaleY }}
        transition={breathing
          ? { duration: active === 'sleepy' ? 3.2 : 2.4, repeat: Infinity, ease: 'easeInOut' }
          : (reduce ? { duration: 0 } : SPRING_SNAP)}
      >
        {/* Left wing */}
        <motion.g
          style={{ originX: '27px', originY: '56px' }}
          animate={{ rotate: -(pose.wingL ?? 0) }}
          transition={reduce ? { duration: 0 } : SPRING_SOFT}
        >
          <ellipse cx="21" cy="63" rx="10" ry="16" fill={tealLight} />
        </motion.g>
        {/* Right wing */}
        <motion.g
          style={{ originX: '73px', originY: '56px' }}
          animate={{ rotate: pose.wingR ?? 0 }}
          transition={reduce ? { duration: 0 } : SPRING_SOFT}
        >
          <ellipse cx="79" cy="63" rx="10" ry="16" fill={tealLight} />
        </motion.g>

        {/* Torso — heavy overlap with the head so the two shapes read as one
            egg silhouette even mid-tilt */}
        <ellipse cx="50" cy="64" rx="29" ry="29" fill={teal} />
        <ellipse cx="50" cy="71" rx="17" ry="17" fill={cream} />
        {/* Belly feather marks */}
        <path d="M42 67 q4 3 8 0 M46 75 q4 3 8 0" stroke={amber} strokeWidth="1.4" fill="none" strokeLinecap="round" opacity="0.55" />
        {/* Feet */}
        <path d="M40 91 l-3 5 M40 91 l0 6 M40 91 l3 5 M60 91 l-3 5 M60 91 l0 6 M60 91 l3 5"
          stroke={amber} strokeWidth="2.4" strokeLinecap="round" />

        {/* Head */}
        <motion.g
          style={{ originX: '50px', originY: '48px' }}
          animate={{ rotate: pose.headTilt }}
          transition={reduce ? { duration: 0 } : SPRING_SOFT}
        >
          <circle cx="50" cy="36" r="27" fill={teal} />
          {/* Ear tufts */}
          <path d="M30 16 q-2 -9 6 -11 q1 7 -1 10 z" fill={teal} />
          <path d="M70 16 q2 -9 -6 -11 q-1 7 1 10 z" fill={teal} />

          {/* Brows (cyan, raise with mood) — thick: they carry expression */}
          <motion.path
            d="M27 21 q9 -6 17 -1.5" stroke={cyan} strokeWidth="3" fill="none" strokeLinecap="round"
            animate={{ y: -(pose.brows ?? 0) }} transition={reduce ? { duration: 0 } : SPRING_SOFT}
          />
          <motion.path
            d="M56 19.5 q8 -4.5 17 1.5" stroke={cyan} strokeWidth="3" fill="none" strokeLinecap="round"
            animate={{ y: -(pose.brows ?? 0) }} transition={reduce ? { duration: 0 } : SPRING_SOFT}
          />

          {/* Eyes — big and nearly touching (the whole personality) */}
          {(['L', 'R'] as const).map(side => {
            const ex = side === 'L' ? EYE_LX : EYE_RX;
            return (
              <g key={side}>
                <circle cx={ex} cy={EYE_Y} r={EYE_R} fill="white" />
                <circle cx={ex} cy={EYE_Y} r={EYE_R} fill="none" stroke={tealLight} strokeWidth="1.4" />
                {/* Pupil follows the pointer */}
                <motion.g style={{ x: pupilX, y: pupilY }}>
                  <circle cx={ex} cy={EYE_Y} r="6.6" fill={teal} />
                  <circle cx={ex + 2.3} cy={EYE_Y - 2.4} r="2.2" fill="white" />
                  <circle cx={ex - 2} cy={EYE_Y + 2.6} r="1" fill="white" opacity="0.7" />
                </motion.g>
                {/* Eyelid (slides down; brief full close on blink) */}
                <motion.rect
                  x={ex - 14} width="28" height="28" rx="14" fill={teal}
                  animate={{ y: (EYE_Y - EYE_R) - 28 + Math.max(pose.lids ?? 0, blink ? 1 : 0) * 28 }}
                  transition={reduce ? { duration: 0 } : { type: 'spring', stiffness: 500, damping: 30 }}
                />
                {/* Happy arcs when celebrating */}
                {(active === 'celebrateSmall' || active === 'celebrateBig' || active === 'wave') && (
                  <path d={`M${ex - 8.5} ${EYE_Y + 1.5} q8.5 -10 17 0`} stroke={teal} strokeWidth="4"
                    fill={teal} strokeLinecap="round" />
                )}
              </g>
            );
          })}

          {/* Beak — small rounded triangle (big beaks read grumpy) */}
          <path d="M50 48 l-4.2 3 q4.2 5.5 8.4 0 z" fill={amber} />

          {/* Reading glasses */}
          {active === 'reading' && (
            <g stroke={amber} strokeWidth="1.8" fill="none" opacity="0.9">
              <circle cx={EYE_LX} cy={EYE_Y} r="15.5" />
              <circle cx={EYE_RX} cy={EYE_Y} r="15.5" />
              <path d={`M${EYE_LX + 15.5} ${EYE_Y} h${EYE_RX - EYE_LX - 31} M${EYE_LX - 15.5} ${EYE_Y} h-4 M${EYE_RX + 15.5} ${EYE_Y} h4`} />
            </g>
          )}
        </motion.g>
      </motion.g>

      {/* Sleepy zzz */}
      {active === 'sleepy' && !reduce && (
        <motion.text
          x="78" y="16" fontSize="10" fill={cyan}
          animate={{ opacity: [0, 1, 0], y: [18, 10, 4] }}
          transition={{ duration: 2.4, repeat: Infinity }}
        >
          z Z
        </motion.text>
      )}

      {/* Thinking sparkle */}
      {active === 'thinking' && !reduce && (
        <motion.text
          x="74" y="12" fontSize="9"
          animate={{ opacity: [0.2, 1, 0.2], rotate: [0, 20, 0] }}
          transition={{ duration: 1.6, repeat: Infinity }}
        >
          💭
        </motion.text>
      )}
    </motion.svg>
  );
});

Ezhilan.displayName = 'Ezhilan';

/** Owl that fires a one-shot trigger shortly after mounting — for
 *  celebration/encouragement screens. */
export const EzhilanMoment: React.FC<{
  trigger: EzhilanTrigger;
  mode?: EzhilanMode;
  size?: number;
  className?: string;
}> = ({ trigger, mode = 'idle', size, className }) => {
  const ref = useRef<EzhilanHandle>(null);
  useEffect(() => {
    const t = setTimeout(() => ref.current?.trigger(trigger), 300);
    return () => clearTimeout(t);
  }, [trigger]);
  return <Ezhilan ref={ref} mode={mode} size={size} className={className} />;
};
