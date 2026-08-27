import React, { useRef } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { motion, useReducedMotion, useScroll, useSpring, useTransform } from 'motion/react';
import { db } from '../../db/db';
import { useAuth } from '../../contexts/AuthContext';
import { AnimatedNumber } from '../../components/motion/AnimatedNumber';
import { SceneryFar, SceneryNear } from '../../components/illustrations/JourneyScenery';
import { EzhilanMoment } from '../../components/mascot/Ezhilan';

type NodeState = 'completed' | 'active' | 'locked';

interface JourneyNode {
  id: number;
  ta: string;
  en: string;
  state: NodeState;
  stars?: number;
  icon: string;
}

/** Vertical wavy SVG path weaving through the node rail (x centred at 32). */
function wavyPath(height: number, segments: number): string {
  const segH = height / Math.max(segments, 1);
  let d = `M 32 0`;
  for (let i = 0; i < segments; i++) {
    const y0 = i * segH;
    const bend = i % 2 === 0 ? 18 : 46; // weave left/right of the rail
    d += ` C ${bend} ${y0 + segH * 0.33}, ${bend} ${y0 + segH * 0.66}, 32 ${y0 + segH}`;
  }
  return d;
}

/** Completed-milestone badge: a gold coin that flips in when scrolled into view. */
const CoinBadge: React.FC<{ children: React.ReactNode; delay?: number }> = ({ children, delay = 0 }) => {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className="relative w-16 h-16 rounded-full flex items-center justify-center flex-shrink-0 z-10"
      style={{
        transformPerspective: 600,
        background: 'radial-gradient(circle at 35% 30%, rgba(98,249,238,0.45), rgba(98,249,238,0.12) 60%, rgba(10,26,30,0.9))',
        border: '2px solid rgba(98,249,238,0.7)',
        boxShadow: '0 0 18px rgba(98,249,238,0.35), inset 0 2px 6px rgba(255,255,255,0.25)',
      }}
      initial={reduce ? false : { rotateY: -180, scale: 0.5, opacity: 0 }}
      whileInView={{ rotateY: 0, scale: 1, opacity: 1 }}
      viewport={{ once: true, margin: '-40px' }}
      whileHover={reduce ? undefined : { rotateY: 360, transition: { duration: 0.8 } }}
      transition={{ type: 'spring', stiffness: 200, damping: 18, delay }}
    >
      {children}
    </motion.div>
  );
};

export const JourneyMap: React.FC = () => {
  const { session } = useAuth();
  const studentId = session?.studentId || '';
  const reduce = useReducedMotion();

  const pathRef = useRef<HTMLDivElement>(null);

  // Path draws itself as the child scrolls down the journey.
  const { scrollYProgress } = useScroll({
    target: pathRef,
    offset: ['start 0.85', 'end 0.55'],
  });
  const pathLength = useSpring(scrollYProgress, { stiffness: 90, damping: 25 });

  // Parallax: background layers drift at different speeds vs. content.
  const { scrollY } = useScroll();
  const bgFar = useTransform(scrollY, v => (reduce ? 0 : v * 0.12));
  const bgNear = useTransform(scrollY, v => (reduce ? 0 : v * 0.28));

  const mapData = useLiveQuery(async () => {
    if (!studentId) return { completed: 0, total: 8, nodes: [] as JourneyNode[] };

    const lessons = await db.lessons.toArray();
    const sortedLessons = lessons.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
    const totalCount = Math.max(sortedLessons.length, 8);

    const progressList = await db.lesson_progress
      .where('studentId')
      .equals(studentId)
      .filter(p => !!p.completedAt)
      .toArray();
    const completedIds = new Set(progressList.map(p => p.lessonId));

    const nodes: JourneyNode[] = [];
    let completedCount = 0;
    let foundActive = false;

    for (let i = 0; i < totalCount; i++) {
      const lesson = sortedLessons[i];
      const lessonTitleTa = lesson ? lesson.title : `பாடம் ${i + 1}`;
      const isCompleted = lesson ? completedIds.has(lesson.id) : false;
      let state: NodeState = 'locked';
      let stars: number | undefined;

      if (isCompleted) {
        state = 'completed';
        completedCount++;
        const lp = progressList.find(p => p.lessonId === lesson?.id);
        const score = lp?.quizScorePercent ?? 1.0;
        stars = score >= 0.9 ? 3 : score >= 0.6 ? 2 : 1;
      } else if (!foundActive) {
        state = 'active';
        foundActive = true;
      }

      nodes.push({
        id: i + 1,
        ta: lessonTitleTa,
        en: `Lesson ${i + 1}`,
        state,
        stars,
        icon: i === totalCount - 1 ? 'emoji_events' : i % 2 === 0 ? 'abc' : 'menu_book',
      });
    }

    return { completed: completedCount, total: totalCount, nodes };
  }, [studentId]) || { completed: 0, total: 8, nodes: [] as JourneyNode[] };

  const NODE_STYLE: Record<NodeState, { ring: string; bg: string; text: string; icon: string }> = {
    completed: { ring: 'border-primary-fixed', bg: 'bg-primary-fixed/20', text: 'text-primary-fixed', icon: 'text-primary-fixed' },
    active:    { ring: 'border-secondary',     bg: 'bg-secondary/20',     text: 'text-secondary',     icon: 'text-secondary' },
    locked:    { ring: 'border-white/10',      bg: 'bg-white/5',          text: 'text-text-muted',    icon: 'text-text-muted' },
  };

  const NODE_ROW_H = 112; // p-4 card + gap — keep in sync with the list below
  const pathHeight = mapData.nodes.length * NODE_ROW_H;

  // overflow-x-clip because the node cards reveal from x: 32 and sit
  // translated until they scroll into view, which pushed the page 32px wider
  // than a 375px phone and made it scroll sideways. Motion's own
  // scroll-triggered example clips the container holding the animating element
  // for the same reason. clip rather than hidden so the vertical glow is
  // untouched and no scroll container is created.
  return (
    <div className="space-y-6 max-w-lg mx-auto font-body-tamil relative overflow-x-clip">
      {/* Parallax scenery layers (decorative, transform-only) — Tamil Nadu
          village at dusk, drawn in the Ezhil shape language */}
      <motion.div aria-hidden className="pointer-events-none fixed inset-x-0 top-8 -z-10 opacity-50 overflow-hidden" style={{ y: bgFar }}>
        <SceneryFar className="w-full max-w-3xl mx-auto" />
      </motion.div>
      <motion.div aria-hidden className="pointer-events-none fixed inset-x-0 bottom-0 -z-10 opacity-60 overflow-hidden" style={{ y: bgNear }}>
        <SceneryNear className="w-full max-w-3xl mx-auto" />
      </motion.div>

      <div className="flex items-center gap-3 animate-fade-in">
        <EzhilanMoment trigger="point" size={56} />
        <div>
          <h1 className="font-display-tamil text-3xl font-bold heading-display-accent">என் பயணம்</h1>
          {/* Milestone counts only — never percentages (pedagogical rule) */}
          <p className="text-text-muted text-sm mt-1">
            My Learning Journey · {mapData.completed} / {mapData.total} பாடங்கள் முடிந்தது
          </p>
        </div>
      </div>

      {/* Progress summary */}
      <div className="glass-panel r-card p-5 flex items-center gap-5 relative overflow-hidden shimmer animate-slide-in">
        <div className="absolute -top-10 -right-8 w-32 h-32 bg-primary-fixed/10 rounded-full blur-2xl" />
        <div className="flex-1 space-y-2 relative">
          <div className="flex justify-between text-sm">
            <span className="text-text-muted">Milestone Progress</span>
            <span className="text-primary-fixed font-bold">
              <AnimatedNumber value={mapData.completed} /> / {mapData.total}
            </span>
          </div>
          <div className="h-2.5 bg-white/5 rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-primary-fixed to-accent-teal rounded-full shadow-[0_0_12px_rgba(98,249,238,0.5)] transition-all duration-700"
              style={{ width: `${(mapData.completed / mapData.total) * 100}%` }}
            />
          </div>
        </div>
        <div className="text-4xl relative animate-bob origin-bottom">🏆</div>
      </div>

      {/* Winding path that draws itself with scroll */}
      <div className="relative" ref={pathRef}>
        <svg
          aria-hidden
          className="absolute left-0 top-0 h-full"
          width="64"
          viewBox={`0 0 64 ${pathHeight}`}
          preserveAspectRatio="none"
          style={{ height: '100%' }}
        >
          <path d={wavyPath(pathHeight, mapData.nodes.length)}
            fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="3" strokeLinecap="round" />
          <motion.path
            d={wavyPath(pathHeight, mapData.nodes.length)}
            fill="none"
            stroke="url(#journeyGrad)"
            strokeWidth="3"
            strokeLinecap="round"
            style={{ pathLength: reduce ? 1 : pathLength }}
            filter="drop-shadow(0 0 6px rgba(98,249,238,0.6))"
          />
          <defs>
            <linearGradient id="journeyGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#62F9EE" />
              <stop offset="100%" stopColor="#FFB955" />
            </linearGradient>
          </defs>
        </svg>

        <div className="space-y-4">
          {mapData.nodes.map((node, i) => {
            const s = NODE_STYLE[node.state];
            const isActive = node.state === 'active';
            const circle =
              node.state === 'completed' ? (
                <CoinBadge delay={i * 0.04}>
                  <span className="material-symbols-outlined text-primary-fixed text-2xl" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
                </CoinBadge>
              ) : (
                <div className={`relative w-16 h-16 rounded-full border-2 ${s.ring} ${s.bg} flex items-center justify-center flex-shrink-0 z-10 ${isActive ? 'animate-pulse-ring' : ''}`}>
                  <span className={`material-symbols-outlined ${s.icon} text-2xl`}>
                    {node.state === 'locked' ? 'lock' : node.icon}
                  </span>
                </div>
              );

            return (
              <motion.div
                key={node.id}
                className={`relative flex items-center gap-4 p-4 r-card ${
                  isActive ? 'glass-panel shadow-[0_0_24px_rgba(255,185,85,0.1)]' : 'bg-black/10 hover:bg-white/3'
                }`}
                initial={reduce ? false : { opacity: 0, x: 32 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: '-40px' }}
                transition={{ type: 'spring', stiffness: 220, damping: 24 }}
              >
                {circle}
                <div className="flex-1 min-w-0">
                  <h3 className={`font-display-tamil font-bold text-lg ${s.text}`}>{node.ta}</h3>
                  <p className="text-text-muted text-sm font-bilingual-sub">{i + 1}. {node.en}</p>
                  {node.stars && (
                    <div className="flex gap-0.5 mt-1">
                      {[1, 2, 3].map(n => (
                        <motion.span
                          key={n}
                          className={`material-symbols-outlined text-sm ${n <= node.stars! ? 'text-secondary drop-shadow-[0_0_4px_rgba(255,185,85,0.6)]' : 'text-white/10'}`}
                          style={{ fontVariationSettings: "'FILL' 1" }}
                          initial={reduce ? false : { scale: 0, rotate: -90 }}
                          whileInView={{ scale: 1, rotate: 0 }}
                          viewport={{ once: true }}
                          transition={{ type: 'spring', stiffness: 300, damping: 15, delay: 0.15 + n * 0.08 }}
                        >
                          star
                        </motion.span>
                      ))}
                    </div>
                  )}
                </div>
                {isActive && (
                  <div className="bg-secondary text-bg-deep font-bold text-xs px-3 py-1.5 rounded-full animate-pulse">GO!</div>
                )}
              </motion.div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
