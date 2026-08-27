import React, { useRef } from 'react';
import { motion, useMotionValue, useReducedMotion, useSpring, useTransform } from 'motion/react';

/**
 * Physical interaction primitives. All effects are transform/opacity only
 * (GPU-cheap) and collapse to plain divs under prefers-reduced-motion —
 * children with vestibular sensitivity must never be forced through motion.
 */

const PRESS_SPRING = { type: 'spring', stiffness: 500, damping: 30 } as const;

/** Spring press-scale wrapper for tappable things. */
export const Pressable: React.FC<
  React.PropsWithChildren<{ className?: string; onClick?: () => void; scale?: number }>
> = ({ children, className, onClick, scale = 0.95 }) => {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className={className}
      onClick={onClick}
      whileTap={reduce ? undefined : { scale }}
      whileHover={reduce ? undefined : { scale: 1.02 }}
      transition={PRESS_SPRING}
    >
      {children}
    </motion.div>
  );
};

/**
 * Pointer-tracking 3D tilt card with a moving glare highlight — the cheap
 * trick that reads as "real depth". Tilt is capped at ±10° and springs back
 * on leave.
 */
export const TiltCard: React.FC<
  React.PropsWithChildren<{ className?: string; onClick?: () => void; maxTilt?: number }>
> = ({ children, className, onClick, maxTilt = 10 }) => {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const px = useMotionValue(0.5); // pointer position 0..1
  const py = useMotionValue(0.5);

  const rotateX = useSpring(useTransform(py, [0, 1], [maxTilt, -maxTilt]), {
    stiffness: 300, damping: 25,
  });
  const rotateY = useSpring(useTransform(px, [0, 1], [-maxTilt, maxTilt]), {
    stiffness: 300, damping: 25,
  });
  const glareX = useTransform(px, [0, 1], ['0%', '100%']);
  const glareY = useTransform(py, [0, 1], ['0%', '100%']);

  if (reduce) {
    return <div className={className} onClick={onClick}>{children}</div>;
  }

  return (
    <motion.div
      ref={ref}
      className={className}
      onClick={onClick}
      style={{ rotateX, rotateY, transformPerspective: 800, transformStyle: 'preserve-3d' }}
      whileTap={{ scale: 0.96 }}
      transition={PRESS_SPRING}
      onPointerMove={e => {
        const r = ref.current?.getBoundingClientRect();
        if (!r) return;
        px.set((e.clientX - r.left) / r.width);
        py.set((e.clientY - r.top) / r.height);
      }}
      onPointerLeave={() => { px.set(0.5); py.set(0.5); }}
    >
      {children}
      {/* Glare sheen that follows the pointer */}
      <motion.div
        aria-hidden
        className="pointer-events-none absolute inset-0 rounded-[inherit] overflow-hidden"
        style={{ zIndex: 1 }}
      >
        <motion.div
          className="absolute w-[150%] h-[150%] -translate-x-1/2 -translate-y-1/2"
          style={{
            left: glareX,
            top: glareY,
            background:
              'radial-gradient(circle at center, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.0) 55%)',
          }}
        />
      </motion.div>
    </motion.div>
  );
};

/** Staggered spring entrance for a list/grid of children. */
export const SpringIn: React.FC<
  React.PropsWithChildren<{ className?: string; delay?: number }>
> = ({ children, className, delay = 0 }) => {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className={className}
      initial={reduce ? false : { opacity: 0, y: 24, scale: 0.96 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 260, damping: 22, delay }}
    >
      {children}
    </motion.div>
  );
};
