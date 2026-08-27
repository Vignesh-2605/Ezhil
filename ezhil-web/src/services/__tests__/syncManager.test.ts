import { describe, expect, it } from 'vitest';
import { toApiRow } from '../syncManager';

describe('toApiRow', () => {
  it('converts camelCase keys to the server snake_case column names', () => {
    const row = toApiRow({
      id: 'a1',
      studentId: 's1',
      conductedAt: '2026-07-01T10:00:00Z',
      phonemeErrorRate: 0.2,
      cnnRiskScore: 0.15,
      audioDurationMs: 42000,
      modelVersion: 'heuristic-web-0.1',
      riskLevel: 'low',
      syncStatus: 'pending',
      createdAt: '2026-07-01T10:00:00Z',
    });

    expect(row).toEqual({
      id: 'a1',
      student_id: 's1',
      conducted_at: '2026-07-01T10:00:00Z',
      phoneme_error_rate: 0.2,
      cnn_risk_score: 0.15,
      audio_duration_ms: 42000,
      model_version: 'heuristic-web-0.1',
      risk_level: 'low',
      created_at: '2026-07-01T10:00:00Z',
    });
  });

  it('strips the client-only syncStatus field', () => {
    expect(toApiRow({ id: 'x', syncStatus: 'conflict' })).toEqual({ id: 'x' });
  });

  it('covers every game session field', () => {
    const row = toApiRow({
      gameType: 'match_sound',
      playedAt: 't',
      roundsTotal: 3,
      roundsCorrect: 2,
      durationMs: 1,
      errorMatrixJson: '{}',
      difficultyLevel: 1,
      starsEarned: 2,
    });
    expect(Object.keys(row).sort()).toEqual([
      'difficulty_level', 'duration_ms', 'error_matrix_json', 'game_type',
      'played_at', 'rounds_correct', 'rounds_total', 'stars_earned',
    ]);
  });

  it('leaves already-lowercase keys untouched', () => {
    expect(toApiRow({ id: 'x', name: 'Kavin S.', dob: '2016-05-12' }))
      .toEqual({ id: 'x', name: 'Kavin S.', dob: '2016-05-12' });
  });
});
