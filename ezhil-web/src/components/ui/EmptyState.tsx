import React from 'react';
import {
  EmptyLessonsArt,
  EmptyStudentsArt,
  EmptyActivityArt,
} from '../illustrations/Illustrations';

type EmptyArt = 'lessons' | 'students' | 'activity';

const ART: Record<EmptyArt, React.FC<{ size?: number; className?: string }>> = {
  lessons: EmptyLessonsArt,
  students: EmptyStudentsArt,
  activity: EmptyActivityArt,
};

interface EmptyStateProps {
  /** Preferred: named illustration from the Ezhil set. */
  art?: EmptyArt;
  /** Legacy fallback — only shown when no `art` is given. */
  emoji?: string;
  title: string;
  subtitle: string;
  action?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ art, emoji, title, subtitle, action }) => {
  const Art = art ? ART[art] : null;
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-4 text-center animate-fade-in">
      {Art ? <Art size={170} className="opacity-90" /> : <span className="text-6xl">{emoji}</span>}
      <h3 className="text-white text-xl font-bold">{title}</h3>
      <p className="text-text-muted text-sm max-w-xs">{subtitle}</p>
      {action}
    </div>
  );
};
