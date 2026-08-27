import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../db/db';
import { speakTamil } from '../../services/speechService';
import { useTamilVoice } from '../../hooks/useTamilVoice';
import { SyncManager } from '../../services/syncManager';

const ROUNDS = [
  { sound: 'க', options: ['க', 'ச', 'ட', 'த'], answer: 'க' },
  { sound: 'ம', options: ['ந', 'ம', 'ல', 'வ'], answer: 'ம' },
  { sound: 'ழ', options: ['ல', 'ள', 'ழ', 'ர'], answer: 'ழ' },
];

export const MatchSoundPlaying: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';
  
  const [round, setRound] = useState(0);
  const [score, setScore] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [shakingOption, setShakingOption] = useState<string | null>(null);
  const hasVoice = useTamilVoice();

  const current = ROUNDS[round];

  // Auto-speak the sound when round changes
  useEffect(() => {
    if (current?.sound) {
      speakTamil(current.sound);
    }
  }, [round, current]);

  const handleSelect = (opt: string) => {
    if (selected) return;
    setSelected(opt);
    const isCorrect = opt === current.answer;
    
    if (isCorrect) {
      setScore(s => s + 1);
    } else {
      setShakingOption(opt);
      setTimeout(() => setShakingOption(null), 400);
    }

    const finalScore = score + (isCorrect ? 1 : 0);

    setTimeout(async () => {
      if (round + 1 >= ROUNDS.length) {
        if (studentId) {
          const starsEarned = finalScore === ROUNDS.length ? 3 : finalScore >= Math.ceil(ROUNDS.length * 0.6) ? 2 : 1;
          const gameSession = {
            id: `gs-${crypto.randomUUID()}`,
            studentId,
            gameType: 'match_sound' as const,
            playedAt: new Date().toISOString(),
            roundsTotal: ROUNDS.length,
            roundsCorrect: finalScore,
            durationMs: 12000,
            errorMatrixJson: JSON.stringify({}),
            difficultyLevel: 1,
            starsEarned,
            syncStatus: 'pending' as const,
            createdAt: new Date().toISOString()
          };
          try {
            await db.game_sessions.put(gameSession);
            SyncManager.sync().catch(err => console.error('Auto sync error:', err));
          } catch (e) {
            console.error('Failed to save game session:', e);
          }
        }
        navigate('/student/games/summary', { state: { game: 'Match Sound', score: finalScore, total: ROUNDS.length } });
      } else {
        setRound(r => r + 1);
        setSelected(null);
      }
    }, 1000);
  };

  const playSoundHint = () => {
    if (current?.sound) {
      speakTamil(current.sound);
    }
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col font-body-tamil px-6 py-8 gap-8">
      <div className="space-y-2">
        <div className="flex justify-between text-sm text-text-muted">
          <span>Round {round + 1}/{ROUNDS.length}</span>
        </div>
        <div className="h-2 bg-white/5 rounded-full overflow-hidden">
          <div className="h-full bg-primary-fixed rounded-full" style={{ width: `${((round) / ROUNDS.length) * 100}%` }} />
        </div>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center gap-8">
        {hasVoice ? (
          <>
            <button aria-label="Play sound" onClick={playSoundHint} className="w-24 h-24 rounded-full bg-primary-fixed/20 border-2 border-primary-fixed flex items-center justify-center animate-pulse-ring hover:scale-110 transition-transform active:scale-90">
              <span className="material-symbols-outlined text-primary-fixed text-4xl" style={{ fontVariationSettings: "'FILL' 1" }}>volume_up</span>
            </button>
            <p className="text-text-muted text-sm">Tap to hear the sound</p>
          </>
        ) : (
          <div className="glass-panel r-card px-5 py-3 max-w-sm text-center">
            <p className="text-secondary text-sm font-bold">🔇 ஒலி இல்லை / No Tamil voice on this device</p>
            <p className="text-text-muted text-xs mt-1">Match the letter you see below instead.</p>
          </div>
        )}
        <p className="font-display-tamil text-7xl font-bold text-white text-glow-teal">{current.sound}</p>

        <div className="grid grid-cols-2 gap-4 w-full max-w-sm">
          {current.options.map(opt => (
            <button key={opt} onClick={() => handleSelect(opt)}
              className={`h-20 r-card font-display-tamil text-4xl font-bold transition-all border-2 ${
                !selected ? 'border-white/10 bg-bg-surface text-white hover:border-primary-fixed/50 hover:bg-primary-fixed/5 active:scale-95' :
                opt === current.answer ? 'border-success bg-success/20 text-success' :
                opt === shakingOption ? 'border-white/20 bg-bg-surface/80 text-white animate-shake' :
                'border-white/5 bg-bg-surface/30 text-text-muted opacity-40'
              }`}>
              {opt}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
