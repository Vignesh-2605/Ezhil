import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { speakTamil } from '../../services/speechService';

const ROUNDS = [
  { target: 'க', grid: ['ச', 'க', 'ட', 'த', 'ப', 'ம', 'க', 'ன', 'வ'] },
  { target: 'ழ', grid: ['ல', 'ள', 'ர', 'ழ', 'ன', 'ழ', 'ல', 'வ', 'ர'] },
];

export const SpotLetterPlaying: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  
  const state = location.state as { round?: number; score?: number } | null;
  const round = state?.round ?? 0;
  const score = state?.score ?? 0;

  const [found, setFound] = useState<number[]>([]);
  const [shakingIndices, setShakingIndices] = useState<number[]>([]);

  const current = ROUNDS[round % ROUNDS.length];
  const correctIndices = current.grid.map((l, i) => l === current.target ? i : -1).filter(i => i >= 0);

  // Auto-speak target letter on load
  useEffect(() => {
    if (current?.target) {
      speakTamil(`எழுத்து ${current.target} ஐ கண்டுபிடி`);
    }
  }, [round, current]);

  const handleTap = (i: number) => {
    if (found.includes(i)) return;
    
    if (current.grid[i] === current.target) {
      const newFound = [...found, i];
      setFound(newFound);
      // Speak the correct letter on match
      speakTamil(current.target);

      if (newFound.length === correctIndices.length) {
        setTimeout(() => navigate('/student/games/spot-letter/round-complete', { 
          state: { 
            round: round + 1, 
            total: ROUNDS.length, 
            score: score + 1 
          } 
        }), 600);
      }
    } else {
      // Wrong tap! Shake button
      setShakingIndices(prev => [...prev, i]);
      setTimeout(() => {
        setShakingIndices(prev => prev.filter(idx => idx !== i));
      }, 400);
    }
  };

  const playTargetAudio = () => {
    if (current?.target) {
      speakTamil(current.target);
    }
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col font-body-tamil px-6 py-8 gap-8">
      <div className="text-center space-y-2">
        <p className="text-text-muted text-sm">Round {round + 1}/{ROUNDS.length}</p>
        <h2 className="text-on-surface-variant flex items-center justify-center gap-2">
          Find all instances of:
          <button aria-label="Play sound" onClick={playTargetAudio} className="w-8 h-8 rounded-full bg-primary-fixed/20 border border-primary-fixed/40 flex items-center justify-center active:scale-90 transition-transform">
            <span className="material-symbols-outlined text-primary-fixed text-sm">volume_up</span>
          </button>
        </h2>
        <span className="font-display-tamil text-7xl font-bold text-studio-purple text-glow-teal">{current.target}</span>
        <p className="text-text-muted text-xs">{found.length}/{correctIndices.length} found</p>
      </div>

      <div className="grid grid-cols-3 gap-3 flex-1 content-center max-w-sm mx-auto w-full">
        {current.grid.map((letter, i) => (
          <button key={i} onClick={() => handleTap(i)}
            className={`h-20 r-card font-display-tamil text-4xl font-bold transition-all border-2 ${
              found.includes(i) ? 'border-studio-purple bg-studio-purple/20 text-studio-purple scale-95' :
              shakingIndices.includes(i) ? 'border-white/20 bg-bg-surface text-white animate-shake' :
              'border-white/10 bg-bg-surface text-white hover:border-studio-purple/40 active:scale-90'
            }`}>
            {letter}
          </button>
        ))}
      </div>
    </div>
  );
};
