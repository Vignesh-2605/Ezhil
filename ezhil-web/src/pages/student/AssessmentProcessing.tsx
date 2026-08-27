import React, { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { db } from '../../db/db';
import { SyncManager } from '../../services/syncManager';
import { Ezhilan } from '../../components/mascot/Ezhilan';
import { errorTags, riskLevel, score } from '../../lib/screeningHeuristic';

/**
 * Analyses the recorded audio with the same heuristic the Android app uses:
 * signal energy (mumbling proxy) + long-pause count (syllable-skip proxy).
 * Metrics the audio cannot support (letter reversal, lip sync, WPM) are left
 * unset rather than invented. Results carry modelVersion "heuristic-web-0.2"
 * so the teacher dashboard can label them as estimates.
 */
async function analyzeAudio(blob: Blob): Promise<{
  phonemeErrorRate: number;
  syllableSkipRate: number;
  cnnRiskScore: number;
  riskLevel: 'low' | 'medium' | 'high';
  errorTags: string[];
  durationMs: number;
}> {
  const ctx = new AudioContext();
  try {
    const audio = await ctx.decodeAudioData(await blob.arrayBuffer());
    const samples = audio.getChannelData(0);
    const sampleRate = audio.sampleRate;

    // Mean-square energy. Note this is *not* on a 0..1 loudness scale — normal
    // speech sits near 0.045 — so it must go through the heuristic's dBFS
    // conversion rather than being compared against 1 directly.
    let sumSq = 0;
    for (let i = 0; i < samples.length; i++) sumSq += samples[i] * samples[i];
    const meanSquare = samples.length > 0 ? sumSq / samples.length : 0;

    // Count pauses longer than 300 ms below the silence threshold
    const minSilenceSamples = Math.floor(sampleRate * 0.3);
    const silenceLevel = 0.01;
    let pauses = 0;
    let silenceRun = 0;
    let inSilence = false;
    for (let i = 0; i < samples.length; i++) {
      if (Math.abs(samples[i]) < silenceLevel) {
        silenceRun++;
        if (silenceRun >= minSilenceSamples && !inSilence) {
          pauses++;
          inSilence = true;
        }
      } else {
        silenceRun = 0;
        inSilence = false;
      }
    }

    const s = score(meanSquare, pauses);

    return {
      phonemeErrorRate: parseFloat(s.phonemeErrorRate.toFixed(2)),
      syllableSkipRate: parseFloat(s.syllableSkipRate.toFixed(2)),
      cnnRiskScore: parseFloat(s.risk.toFixed(2)),
      riskLevel: riskLevel(s.risk),
      errorTags: errorTags(s),
      durationMs: Math.round(audio.duration * 1000),
    };
  } finally {
    ctx.close().catch(() => {});
  }
}

export const AssessmentProcessing: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { session } = useAuth();
  const studentId = session?.studentId || '';

  const state = location.state as { audioBlob?: Blob; framesCount?: number; durationMs?: number } | null;

  useEffect(() => {
    let cancelled = false;

    async function processAssessment() {
      // No student or no recording — nothing real to analyse. Never invent a
      // result: send the child to the gentle try-again screen instead.
      if (!studentId || !state?.audioBlob) {
        setTimeout(() => { if (!cancelled) navigate('/student/assessment/timeout'); }, 1500);
        return;
      }

      try {
        const result = await analyzeAudio(state.audioBlob);

        const newAssessment = {
          id: `assess-${crypto.randomUUID()}`,
          studentId,
          conductedAt: new Date().toISOString(),
          phonemeErrorRate: result.phonemeErrorRate,
          syllableSkipRate: result.syllableSkipRate,
          cnnRiskScore: result.cnnRiskScore,
          riskLevel: result.riskLevel,
          errorTagsJson: JSON.stringify(result.errorTags),
          audioDurationMs: result.durationMs || state.durationMs || 0,
          modelVersion: 'heuristic-web-0.2',
          syncStatus: 'pending' as const,
          createdAt: new Date().toISOString(),
        };

        await db.assessments.put(newAssessment);
        await db.students.update(studentId, { riskLevel: result.riskLevel });

        SyncManager.sync().catch(err => console.error('Sync failed after assessment:', err));

        setTimeout(() => { if (!cancelled) navigate('/student/assessment/complete'); }, 2000);
      } catch (err) {
        console.error('Audio analysis failed:', err);
        setTimeout(() => { if (!cancelled) navigate('/student/assessment/timeout'); }, 1500);
      }
    }

    processAssessment();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studentId, navigate]);

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col items-center justify-center font-body-tamil px-6 gap-10 relative overflow-hidden">
      <div className="orb w-[28rem] h-[28rem] bg-primary-fixed/10 top-1/4 left-1/2 -translate-x-1/2" />
      <div className="relative flex items-center justify-center">
        <div className="absolute w-44 h-44 rounded-full bg-primary-fixed/10 blur-2xl animate-pulse" />
        <Ezhilan mode="thinking" size={120} className="relative" />
      </div>

      <div className="text-center space-y-3 relative animate-fade-in">
        <h1 className="font-display-tamil text-3xl font-bold heading-display-accent">பகுப்பாய்வு...</h1>
        <p className="text-on-surface-variant">Checking your recording</p>
        <p className="text-text-muted text-sm">Just a moment</p>
      </div>
    </div>
  );
};
