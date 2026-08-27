import React, { useEffect } from 'react';
import { motion, useMotionValue, useReducedMotion, useSpring, useTransform } from 'motion/react';

/** Counts up to `value` with a spring — stats feel alive instead of static. */
export const AnimatedNumber: React.FC<{ value: number; className?: string }> = ({ value, className }) => {
  const reduce = useReducedMotion();
  const mv = useMotionValue(0);
  const spring = useSpring(mv, { stiffness: 80, damping: 20 });
  const rounded = useTransform(spring, v => Math.round(v).toLocaleString());

  useEffect(() => {
    if (reduce) spring.jump(value);
    else mv.set(value);
  }, [value, mv, spring, reduce]);

  return <motion.span className={className}>{rounded}</motion.span>;
};
