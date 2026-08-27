import Dexie, { Table } from 'dexie';

export interface LocalStudent {
  id: string;
  teacherId: string;
  name: string;
  dob?: string;
  riskLevel: 'unscreened' | 'low' | 'medium' | 'high';
  streakDays: number;
  lastActive?: string;
  syncStatus: 'pending' | 'synced' | 'conflict';
  createdAt: string;
}

export interface LocalAssessment {
  id: string;
  studentId: string;
  conductedAt: string;
  readingSpeedWpm?: number;
  phonemeErrorRate?: number; // 0.0 - 1.0
  letterReversalRate?: number; // 0.0 - 1.0
  syllableSkipRate?: number; // 0.0 - 1.0
  lipSyncConfidence?: number; // 0.0 - 1.0
  cnnRiskScore?: number; // 0.0 - 1.0 (Teacher-only metric)
  riskLevel: 'low' | 'medium' | 'high';
  errorTagsJson?: string;
  audioDurationMs?: number;
  modelVersion: string;
  syncStatus: 'pending' | 'synced' | 'conflict';
  createdAt: string;
}

export interface LocalGameSession {
  id: string;
  studentId: string;
  gameType: 'match_sound' | 'spot_letter' | 'build_word';
  playedAt: string;
  roundsTotal: number;
  roundsCorrect: number;
  durationMs: number;
  errorMatrixJson: string; // JSON string representing error patterns
  difficultyLevel: number;
  starsEarned: number; // 0-3
  syncStatus: 'pending' | 'synced' | 'conflict';
  createdAt: string;
}

export interface LocalLesson {
  id: string;
  teacherId?: string;
  sourceHash?: string;
  title: string;
  titleEn?: string;
  lessonType: string; // "story" | "vocabulary" | "comprehension" | "listen_repeat"
  difficulty: number; // 1 | 2 | 3
  language: string; // "tamil" | "english" | "both"
  contentJson: string; // Canonical Lesson JSON schema
  // 0 | 1, not boolean: IndexedDB rejects booleans as keys, so a boolean here
  // is silently dropped from the isPublished index and the student lesson
  // query returns nothing. Keep it numeric for as long as it stays indexed.
  isPublished: 0 | 1;
  assignedTo: string;
  cacheHit: boolean;
  syncStatus: 'pending' | 'synced' | 'conflict';
  createdAt: string;
}

export interface LocalLessonProgress {
  id: string;
  studentId: string;
  lessonId: string;
  completedAt?: string;
  quizScorePercent?: number;
  durationMs?: number;
  syncStatus: 'pending' | 'synced' | 'conflict';
  createdAt: string;
}

export class EzhilDexie extends Dexie {
  students!: Table<LocalStudent>;
  assessments!: Table<LocalAssessment>;
  game_sessions!: Table<LocalGameSession>;
  lessons!: Table<LocalLesson>;
  lesson_progress!: Table<LocalLessonProgress>;

  constructor() {
    super('EzhilWebDatabase');
    this.version(1).stores({
      students: '&id, teacherId, name, riskLevel, syncStatus',
      assessments: '&id, studentId, conductedAt, riskLevel, syncStatus',
      game_sessions: '&id, studentId, gameType, playedAt, syncStatus',
      lessons: '&id, teacherId, sourceHash, difficulty, isPublished, syncStatus',
      lesson_progress: '&id, studentId, lessonId, syncStatus',
    });

    // v2 — coerce isPublished from boolean to 0|1 so it becomes indexable.
    // Rows written by v1 are invisible to the isPublished index until upgraded.
    this.version(2).upgrade(tx =>
      tx.table('lessons').toCollection().modify(l => {
        l.isPublished = l.isPublished ? 1 : 0;
      }),
    );
  }
}

export const db = new EzhilDexie();
