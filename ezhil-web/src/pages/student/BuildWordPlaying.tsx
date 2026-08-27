import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../db/db';
import { speakTamil } from '../../services/speechService';
import { SyncManager } from '../../services/syncManager';

const WORDS = [{ word: 'யானை', letters: ['யா', 'ன', 'ல', 'ை', 'மா', 'ன்', 'வ'], answer: ['யா', 'ன', 'ை'] }];

export const BuildWordPlaying: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';
  
  const [selected, setSelected] = useState<string[]>([]);
  const [isShaking, setIsShaking] = useState(false);
  const current = WORDS[0];

  useEffect(() => {
    speakTamil(`சொல்லை உருவாக்கு: ${current.word}`);
  }, [current]);

  const toggle = (letter: string) => {
    setSelected(prev => prev.includes(letter) ? prev.filter(l => l !== letter) : [...prev, letter]);
  };

  const check = async () => {
    const isCorrect = selected.join('') === current.word;
    
    if (isCorrect) {
      if (studentId) {
        const gameSession = {
          id: `gs-${crypto.randomUUID()}`,
          studentId,
          gameType: 'build_word' as const,
          playedAt: new Date().toISOString(),
          roundsTotal: 1,
          roundsCorrect: 1,
          durationMs: 18000,
          errorMatrixJson: JSON.stringify({}),
          difficultyLevel: 1,
          starsEarned: 3,
          syncStatus: 'pending' as const,
          createdAt: new Date().toISOString()
        };
        try {
          await db.game_sessions.put(gameSession);
          SyncManager.sync().catch(err => console.error(err));
        } catch (err) {
          console.error('Failed to save game session:', err);
        }
      }
      navigate('/student/games/build-word/success');
    } else {
      setIsShaking(true);
      speakTamil('தவறு, மீண்டும் முயற்சி செய்');
      setTimeout(() => {
        setIsShaking(false);
        setSelected([]); // Clear incorrect selections for another attempt
      }, 500);
    }
  };

  const speakTargetWord = () => {
    speakTamil(current.word);
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col font-body-tamil px-6 py-8 gap-8">
      <div className="text-center space-y-2">
        <h1 className="font-display-tamil text-2xl font-bold text-secondary">சொல் கட்டு</h1>
        <p className="text-on-surface-variant">Build a Word</p>
        <p className="text-text-muted text-sm flex items-center justify-center gap-2">
          Arrange the letters to spell: 
          <span className="text-secondary font-bold">{current.word}</span>
          <button aria-label="Play sound" onClick={speakTargetWord} className="w-8 h-8 rounded-full bg-secondary/15 border border-secondary/35 flex items-center justify-center active:scale-90 transition-transform">
            <span className="material-symbols-outlined text-secondary text-sm">volume_up</span>
          </button>
        </p>
      </div>

      {/* Answer tray */}
      <div className={`flex gap-2 justify-center min-h-[60px] p-3 bg-bg-surface/50 r-card border-2 border-dashed border-secondary/30 ${isShaking ? 'animate-shake border-white/20' : ''}`}>
        {selected.map((l, i) => (
          <button key={i} onClick={() => toggle(l)}
            className="min-w-[48px] h-12 bg-secondary/20 border border-secondary/50 r-chip font-display-tamil text-xl font-bold text-secondary px-2 active:scale-90 transition-all">
            {l}
          </button>
        ))}
      </div>

      {/* Letter tiles */}
      <div className="flex flex-wrap gap-3 justify-center">
        {current.letters.map((l, i) => (
          <button key={i} onClick={() => toggle(l)} disabled={selected.includes(l)}
            className={`min-w-[56px] h-14 r-chip font-display-tamil text-2xl font-bold transition-all border-2 px-3 ${
              selected.includes(l) ? 'border-white/5 bg-bg-surface/20 text-text-muted opacity-30' :
              'border-secondary/30 bg-bg-surface text-white hover:border-secondary active:scale-90'
            }`}>
            {l}
          </button>
        ))}
      </div>

      <div className="flex gap-3 mt-auto">
        <button onClick={() => setSelected([])} className="flex-1 h-12 border border-white/15 text-text-muted r-chip font-medium hover:bg-white/5 transition-colors">
          Clear
        </button>
        <button onClick={check} disabled={selected.length === 0}
          className="flex-1 h-12 bg-secondary text-bg-deep font-bold r-chip active:scale-95 transition-all disabled:opacity-50">
          Check ✓
        </button>
      </div>
    </div>
  );
};
