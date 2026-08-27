import React from 'react';
import { useLocation } from 'react-router-dom';
import { motion, useReducedMotion } from 'motion/react';

/**
 * Springs each routed page in (fade + rise + settle). Keyed on pathname so
 * every navigation gets an entrance; no exit phase — keeps back/forward
 * instant and avoids AnimatePresence double-mount pitfalls.
 */
export const RouteTransition: React.FC<React.PropsWithChildren> = ({ children }) => {
  const { pathname } = useLocation();
  const reduce = useReducedMotion();

  if (reduce) return <>{children}</>;

  return (
    <motion.div
      key={pathname}
      initial={{ opacity: 0, y: 18, scale: 0.985 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 240, damping: 26 }}
    >
      {children}
    </motion.div>
  );
};
