import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../db/db';
import { SyncManager } from '../../services/syncManager';

export const SpotLetterRoundComplete: React.FC = () => {
  const navigate = useNavigate();
  const { session } = useAuth();
  const studentId = session?.studentId || '';
  const { state } = useLocation() as { state: { round: number; total: number; score: number } };
  
  const round = state?.round ?? 1;
  const score = state?.score ?? 0;
  const total = state?.total ?? 2;
  const isLast = round >= total;

  const handleFinish = async () => {
    if (studentId) {
      const starsEarned = score === total ? 3 : score >= Math.ceil(total * 0.6) ? 2 : 1;
      const gameSession = {
        id: `gs-${crypto.randomUUID()}`,
        studentId,
        gameType: 'spot_letter' as const,
        playedAt: new Date().toISOString(),
        roundsTotal: total,
        roundsCorrect: score,
        durationMs: 14000,
        errorMatrixJson: JSON.stringify({}),
        difficultyLevel: 1,
        starsEarned,
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
    navigate('/student/games/summary', { state: { game: 'Spot the Letter', score: score, total: total } });
  };

  const handleNext = () => {
    navigate('/student/games/spot-letter/playing', { state: { round: round, score: score } });
  };

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-studio-purple/12 top-1/4 left-1/2 -translate-x-1/2" />
      <div className="relative flex items-center justify-center">
        <div className="absolute w-40 h-40 rounded-full bg-studio-purple/15 blur-2xl animate-pulse" />
        <div className="relative text-[100px] animate-bob origin-bottom drop-shadow-[0_0_30px_rgba(124,58,237,0.5)]">🎯</div>
      </div>
      <div className="text-center space-y-2 relative animate-pop-in">
        <h1 className="font-display-tamil text-4xl font-bold text-studio-purple drop-shadow-[0_0_16px_rgba(124,58,237,0.4)]">சரியானது!</h1>
        <p className="text-on-surface-variant text-xl">Round {round} Complete!</p>
      </div>
      {isLast ? (
        <button onClick={handleFinish}
          className="relative w-full max-w-xs h-14 bg-studio-purple text-white font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_24px_rgba(124,58,237,0.35)] hover:shadow-[0_0_32px_rgba(124,58,237,0.5)]">
          See Results
        </button>
      ) : (
        <button onClick={handleNext}
          className="relative w-full max-w-xs h-14 bg-studio-purple text-white font-bold text-lg r-chip active:scale-95 transition-all shadow-[0_0_24px_rgba(124,58,237,0.35)] hover:shadow-[0_0_32px_rgba(124,58,237,0.5)]">
          Next Round →
        </button>
      )}
    </div>
  );
};
