import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '../../db/db';
import { speakTamil } from '../../services/speechService';

interface VocabEntry {
  word:       string;
  syllables:  string[];
  meaningTa:  string;
  meaningEn:  string;
  /** A line from the source passage that actually contains the word. */
  example:    string;
}

/** Find the word across every stored lesson's vocabulary. Nothing is invented:
 *  a field the lesson does not carry is left blank rather than filled in. */
function findVocab(lessons: { contentJson: string }[], word: string): VocabEntry | null {
  for (const lesson of lessons) {
    let parsed: any;
    try { parsed = JSON.parse(lesson.contentJson); } catch { continue; }

    const entry = (parsed.vocabulary ?? []).find(
      (v: any) => String(v?.word ?? '').trim() === word,
    );
    if (!entry) continue;

    const lines: string[] = parsed.passage?.lines ?? [];
    return {
      word,
      syllables: Array.isArray(entry.syllables) ? entry.syllables : [],
      meaningTa: String(entry.meaning_ta ?? ''),
      meaningEn: String(entry.meaning_en ?? entry.meaning ?? ''),
      example:   lines.find(l => l.includes(word)) ?? '',
    };
  }
  return null;
}

export const VocabularyDetail: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const word = (id ?? '').trim();

  const entry = useLiveQuery(async () => {
    if (!word) return null;
    return findVocab(await db.lessons.toArray(), word);
  }, [word]);

  const loading = entry === undefined;

  return (
    <div className="min-h-dvh bg-bg-deep flex flex-col font-body-tamil px-6 py-8 relative overflow-hidden">
      <div className="orb w-96 h-96 bg-primary-fixed/8 top-0 right-[-6rem]" />
      <button onClick={() => navigate(-1)} className="self-start material-symbols-outlined text-text-muted p-2 hover:text-white transition-colors mb-6 relative">
        arrow_back
      </button>

      <div className="glass-panel card-lift r-hero surface-lit p-8 space-y-6 relative animate-slide-in max-w-lg w-full mx-auto">
        <div className="text-center space-y-3">
          <div className="relative inline-block">
            <div className="absolute inset-0 bg-primary-fixed/15 blur-3xl rounded-full" />
            <span className="relative font-display-tamil text-7xl font-bold text-primary-fixed text-glow-teal animate-bob origin-bottom inline-block">
              {word || '—'}
            </span>
          </div>
          {entry?.meaningEn && (
            <p className="font-bilingual-sub text-on-surface-variant text-xl">{entry.meaningEn}</p>
          )}
        </div>

        {/* Syllable chips — the phonics breakdown, when the lesson supplies one */}
        {!!entry?.syllables.length && (
          <div className="flex flex-wrap justify-center gap-2">
            {entry.syllables.map((s, i) => (
              <button key={`${s}-${i}`} onClick={() => speakTamil(s)}
                className="px-4 py-2 r-chip bg-primary-fixed/10 border border-primary-fixed/25 font-display-tamil text-2xl text-primary-fixed active:scale-95 transition-transform">
                {s}
              </button>
            ))}
          </div>
        )}

        <hr className="border-white/10" />

        {loading ? (
          <p className="text-text-muted text-center text-sm">…</p>
        ) : entry ? (
          <div className="space-y-4">
            {entry.meaningTa && (
              <div>
                {/* Tamil label — no tracking. */}
                <p className="text-text-muted text-xs uppercase mb-1">பொருள்</p>
                <p className="font-reader-tamil text-on-surface text-lg">{entry.meaningTa}</p>
              </div>
            )}
            {entry.example && (
              <div>
                <p className="text-text-muted text-xs uppercase tracking-wider mb-1">Example Sentence</p>
                <p className="font-reader-tamil text-on-surface text-lg">{entry.example}</p>
              </div>
            )}
            <div>
              <p className="text-text-muted text-xs uppercase tracking-wider mb-2">Practice</p>
              <button onClick={() => speakTamil(word)}
                className="w-full h-11 bg-secondary/20 text-secondary r-chip font-bold text-sm flex items-center justify-center gap-1 active:scale-95 transition-all">
                <span className="material-symbols-outlined text-base">volume_up</span> Listen
              </button>
            </div>
          </div>
        ) : (
          <div className="text-center space-y-3">
            <p className="text-on-surface-variant text-sm">
              இந்தச் சொல் உங்கள் பாடங்களில் இல்லை.
            </p>
            <p className="text-text-muted text-xs">This word isn't in your lessons yet.</p>
            <button onClick={() => speakTamil(word)}
              className="mx-auto px-5 h-11 bg-secondary/20 text-secondary r-chip font-bold text-sm inline-flex items-center gap-1 active:scale-95 transition-all">
              <span className="material-symbols-outlined text-base">volume_up</span> Listen
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
