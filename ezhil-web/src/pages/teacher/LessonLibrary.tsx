import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApiQuery } from '../../hooks/useApi';
import { PageLoading } from '../../components/ui/LoadingSpinner';
import { EmptyState } from '../../components/ui/EmptyState';
import { apiFetch } from '../../services/apiClient';

/** Mirrors the server's LessonDto. `difficulty` is an int (1|2|3), not a label. */
interface Lesson {
  id:           string;
  title:        string;
  content_json: string;
  difficulty:   number;
  language:     string;
  is_published: boolean;
  lesson_type:  string;
  assigned_to:  string;
  cache_hit:    boolean;
  created_at?:  string | null;
}

const DIFF_ICON:  Record<number, string> = { 1: '🌱', 2: '🌿', 3: '🌳' };
const DIFF_LABEL: Record<number, string> = { 1: 'beginner', 2: 'intermediate', 3: 'advanced' };

const formatDate = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

export const LessonLibrary: React.FC = () => {
  const navigate = useNavigate();
  const { data, loading, refetch } = useApiQuery<Lesson[]>('/api/v1/lessons');
  const [lessons, setLessons]     = useState<Lesson[] | null>(null);
  const [deleting, setDeleting]   = useState<string | null>(null);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [toggling, setToggling]   = useState<string | null>(null);
  const [error, setError]         = useState<string | null>(null);

  const source = lessons ?? data ?? [];

  if (loading && !data) return <PageLoading />;

  const handleDelete = async (id: string) => {
    setDeleting(id);
    setError(null);
    try {
      await apiFetch(`/api/v1/lessons/${id}`, { method: 'DELETE' });
      setLessons(source.filter(l => l.id !== id));
    } catch (err) {
      // Never hide a failed delete behind a local removal — the lesson would
      // reappear on refresh and the teacher would not know why.
      setError(err instanceof Error ? err.message : 'Could not delete the lesson.');
    } finally {
      setDeleting(null);
      setConfirmId(null);
    }
  };

  /** Flip is_published server-side. This is the only thing that makes a
   *  lesson reach students — sync/pull filters on it. */
  const handleTogglePublish = async (lesson: Lesson) => {
    setToggling(lesson.id);
    setError(null);
    try {
      const updated = await apiFetch<Lesson>(`/api/v1/lessons/${lesson.id}`, {
        method: 'PUT',
        body: JSON.stringify({ ...lesson, is_published: !lesson.is_published }),
      });
      setLessons(source.map(l => (l.id === lesson.id ? updated : l)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update the lesson.');
    } finally {
      setToggling(null);
    }
  };

  return (
    <div className="space-y-6 font-body-tamil">
      {/* Confirm dialog */}
      {confirmId && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="glass-panel r-card p-6 max-w-sm w-full space-y-4 border border-error/30">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 r-chip bg-error/20 flex items-center justify-center flex-shrink-0">
                <span className="material-symbols-outlined text-error">delete</span>
              </div>
              <div>
                <h3 className="text-white font-bold">Delete Lesson?</h3>
                <p className="text-text-muted text-sm">This cannot be undone.</p>
              </div>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setConfirmId(null)}
                className="flex-1 h-10 border border-white/15 text-text-muted r-chip hover:bg-white/5 transition-colors text-sm">
                Cancel
              </button>
              <button onClick={() => handleDelete(confirmId)} disabled={deleting === confirmId}
                className="flex-1 h-10 bg-error text-white r-chip font-bold text-sm disabled:opacity-50 transition-all">
                {deleting === confirmId ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between animate-fade-in">
        <div className="flex items-center gap-3">
          <span className="w-11 h-11 r-card bg-studio-purple/15 border border-studio-purple/30 flex items-center justify-center shadow-[0_0_14px_rgba(124,58,237,0.2)] flex-shrink-0">
            <span className="material-symbols-outlined text-studio-purple" style={{ fontVariationSettings: "'FILL' 1" }}>auto_stories</span>
          </span>
          <div>
            <h1 className="font-display-tamil text-3xl font-bold heading-display">Lesson Library</h1>
            <p className="text-text-muted text-sm mt-1">பாட நூலகம் · {source.length} lessons</p>
          </div>
        </div>
        <button onClick={() => navigate('/teacher/lesson-studio')}
          className="flex items-center gap-2 px-5 py-2.5 bg-primary-fixed text-bg-deep r-chip font-bold shadow-[0_0_16px_rgba(98,249,238,0.25)] hover:shadow-[0_0_28px_rgba(98,249,238,0.45)] hover:-translate-y-0.5 transition-all active:scale-95">
          <span className="material-symbols-outlined text-xl">auto_awesome</span> New Lesson
        </button>
      </div>

      {error && (
        <div role="alert" className="glass-panel border-l-4 border-error r-chip p-4 flex items-start gap-3">
          <span className="material-symbols-outlined text-error">error</span>
          <p className="text-error text-sm">{error}</p>
        </div>
      )}

      {source.length === 0 ? (
        <EmptyState art="lessons" title="No lessons yet" subtitle="Create your first lesson in the Studio."
          action={<button onClick={() => navigate('/teacher/lesson-studio')} className="mt-2 px-5 py-2.5 bg-primary-fixed text-bg-deep r-chip font-bold">Open Studio</button>} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 stagger-children">
          {source.map(l => (
            <div key={l.id} className="glass-panel card-lift r-card p-5 flex flex-col gap-3 group relative overflow-hidden">
              <div className="absolute -top-10 -right-10 w-24 h-24 bg-studio-purple/10 rounded-full blur-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
              <div className="flex items-start justify-between relative">
                <span className="text-3xl group-hover:scale-110 transition-transform">{DIFF_ICON[l.difficulty] || '📖'}</span>

                <span className={`text-xs px-2.5 py-1 rounded-full font-semibold ${
                  l.is_published
                    ? 'bg-success/20 text-success border border-success/30'
                    : 'bg-white/10 text-text-muted border border-white/10'
                }`}>
                  {l.is_published ? 'Published' : 'Draft'}
                </span>
              </div>
              <div className="flex-1 relative">
                <h3 className="font-display-tamil text-white font-bold text-lg leading-tight group-hover:text-primary-fixed transition-colors">{l.title}</h3>
                <p className="text-text-muted text-xs mt-1 capitalize">{DIFF_LABEL[l.difficulty] ?? 'beginner'} · {formatDate(l.created_at)}</p>
              </div>
              <div className="flex gap-2 pt-2 border-t border-white/5">
                <button onClick={() => navigate(`/teacher/lesson-studio?edit=${l.id}`)}
                  className="flex-1 text-sm py-1.5 text-primary-fixed hover:bg-primary-fixed/10 r-chip transition-colors font-semibold">
                  Edit
                </button>
                <button onClick={() => handleTogglePublish(l)}
                  disabled={toggling === l.id}
                  className="flex-1 text-sm py-1.5 text-text-muted hover:text-white hover:bg-white/5 r-chip transition-colors disabled:opacity-40">
                  {toggling === l.id ? '…' : l.is_published ? 'Unpublish' : 'Publish'}
                </button>
                <button onClick={() => setConfirmId(l.id)}
                  disabled={deleting === l.id}
                  className="w-8 h-8 flex items-center justify-center text-text-muted hover:text-error hover:bg-error/10 r-chip transition-colors disabled:opacity-40">
                  <span className="material-symbols-outlined text-base">delete</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
