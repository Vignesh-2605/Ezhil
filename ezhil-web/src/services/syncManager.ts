import { db, LocalStudent, LocalAssessment, LocalGameSession, LocalLesson, LocalLessonProgress } from '../db/db';
import { apiFetch } from './apiClient';

interface StudentDto {
  id: string;
  name: string;
  teacher_id: string;
  dob?: string | null;
  risk_level: 'unscreened' | 'low' | 'medium' | 'high';
}

interface LessonDto {
  id: string;
  teacher_id?: string | null;
  source_hash?: string | null;
  title: string;
  lesson_type: string;
  difficulty: number;
  language: string;
  content_json: string;
  is_published: boolean;
  assigned_to: string;
  cache_hit: boolean;
}

interface SyncPullResponse {
  server_time?: string;
  lessons: LessonDto[];
  roster: StudentDto[];
}

/**
 * Local record → sync-push payload. Keys become the server's snake_case
 * column names; client-only fields are stripped. Unknown keys are silently
 * dropped server-side, so a wrong key here means silently lost data.
 */
export function toApiRow(item: Record<string, unknown>): Record<string, unknown> {
  const cleaned: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(item)) {
    if (key === 'syncStatus') continue; // client-only
    cleaned[key.replace(/[A-Z]/g, c => `_${c.toLowerCase()}`)] = value;
  }
  return cleaned;
}

export class SyncManager {
  private static syncing = false;

  public static isSyncing(): boolean {
    return this.syncing;
  }

  /**
   * Run a full sync: Push local changes, then Pull cloud updates
   */
  public static async sync(): Promise<{ pushed: number; pulled: number; success: boolean; error?: string }> {
    if (this.syncing) {
      return { pushed: 0, pulled: 0, success: false, error: 'Sync already in progress' };
    }
    
    this.syncing = true;
    console.log('[SYNC] Starting sync cycle...');
    let pushedCount = 0;
    let pulledCount = 0;

    try {
      // 1. PUSH local 'pending' records
      pushedCount += await this.pushTable('students', db.students);
      pushedCount += await this.pushTable('assessments', db.assessments);
      pushedCount += await this.pushTable('game_sessions', db.game_sessions);
      pushedCount += await this.pushTable('lesson_progress', db.lesson_progress);
      
      console.log(`[SYNC] Push completed. Pushed ${pushedCount} records.`);

      // 2. PULL updates from server
      // Get latest completed_at or created_at as timestamp reference
      const lastSyncTime = localStorage.getItem('ezhil_last_sync') || '1970-01-01T00:00:00Z';
      
      const response = await apiFetch<SyncPullResponse>(
        `/api/v1/sync/pull?last_sync=${encodeURIComponent(lastSyncTime)}`,
        { background: true }
      );

      // Save pulled Students (Roster)
      for (const s of response.roster) {
        const existing = await db.students.get(s.id);
        const updatedStudent: LocalStudent = {
          id: s.id,
          teacherId: s.teacher_id,
          name: s.name,
          dob: s.dob || undefined,
          riskLevel: s.risk_level,
          streakDays: existing?.streakDays ?? 0,
          lastActive: existing?.lastActive,
          syncStatus: 'synced',
          createdAt: existing?.createdAt ?? new Date().toISOString()
        };
        await db.students.put(updatedStudent);
        pulledCount++;
      }

      // Save pulled Lessons
      for (const l of response.lessons) {
        const existing = await db.lessons.get(l.id);
        const updatedLesson: LocalLesson = {
          id: l.id,
          teacherId: l.teacher_id || undefined,
          sourceHash: l.source_hash || undefined,
          title: l.title,
          lessonType: l.lesson_type,
          difficulty: l.difficulty,
          language: l.language,
          contentJson: l.content_json,
          isPublished: l.is_published ? 1 : 0,
          assignedTo: l.assigned_to,
          cacheHit: l.cache_hit,
          syncStatus: 'synced' as const,
          createdAt: existing?.createdAt ?? new Date().toISOString()
        };
        await db.lessons.put(updatedLesson);
        pulledCount++;
      }

      console.log(`[SYNC] Pull completed. Pulled ${pulledCount} updates.`);

      // Use the server's clock, not ours — client clock skew loses records
      localStorage.setItem('ezhil_last_sync', response.server_time || new Date().toISOString());
      
      this.syncing = false;
      return { pushed: pushedCount, pulled: pulledCount, success: true };
    } catch (e: unknown) {
      this.syncing = false;
      const errorMsg = e instanceof Error ? e.message : 'Sync failed';
      console.error('[SYNC] Sync error:', errorMsg);
      return { pushed: pushedCount, pulled: 0, success: false, error: errorMsg };
    }
  }

  /**
   * Push pending records for a specific table
   */
  private static async pushTable(tableName: string, table: any): Promise<number> {
    const pending = await table.where('syncStatus').equals('pending').toArray();
    if (pending.length === 0) return 0;

    const rows = pending.map(toApiRow);

    console.log(`[SYNC] Pushing ${pending.length} rows for table ${tableName}...`);

    const resp = await apiFetch<{ accepted: number; conflicts: string[] }>('/api/v1/sync/push', {
      method: 'POST',
      background: true,
      body: JSON.stringify({
        table: tableName,
        rows: rows
      })
    });

    // Only rows the server accepted become 'synced'; rejected rows are
    // marked 'conflict' so they stop retrying but stay inspectable.
    const conflictSet = new Set(resp.conflicts || []);
    const okIds = pending.map((item: any) => item.id).filter((id: string) => !conflictSet.has(id));
    const badIds = pending.map((item: any) => item.id).filter((id: string) => conflictSet.has(id));
    if (okIds.length) await table.where('id').anyOf(okIds).modify({ syncStatus: 'synced' });
    if (badIds.length) {
      console.warn(`[SYNC] ${tableName}: server rejected ${badIds.length} row(s)`, badIds);
      await table.where('id').anyOf(badIds).modify({ syncStatus: 'conflict' });
    }

    return okIds.length;
  }
}

// Automatically bind browser online event
if (typeof window !== 'undefined') {
  window.addEventListener('online', () => {
    console.log('[SYNC] Browser back online. Triggering auto-sync...');
    SyncManager.sync().catch(err => console.error('[SYNC] Auto-sync failed:', err));
  });
}
