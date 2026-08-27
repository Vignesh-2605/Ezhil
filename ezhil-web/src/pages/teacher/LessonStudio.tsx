import React, { useState, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../../services/apiClient';
import {
  aggregate,
  emptyExtraction,
  exactExtraction,
  nextStep,
  worstOf,
  type Extraction,
} from '../../lib/ocrQuality';

type Step = 'upload' | 'extracting' | 'review' | 'generating' | 'preview' | 'manual';
type FileStatus = 'pending' | 'extracting' | 'done' | 'error';

interface FileResult {
  file:     File;
  status:   FileStatus;
  text:     string;
  progress: string;
  quality?: Extraction;
}

interface Vocab     { word: string; meaning: string; }
interface QuizItem  { q: string; options: string[]; answer: number; }
interface LessonContent {
  title:      string;
  passage:    string;
  vocabulary: Vocab[];
  questions:  QuizItem[];
}
interface GenerateResponse { lesson_id: string; lesson: LessonContent; cache_hit: boolean; }

const STEPS: Step[] = ['upload', 'extracting', 'generating', 'preview'];

const STEP_LABELS: Record<Step, string> = {
  upload:     'Add files',
  extracting: 'Reading',
  review:     'Check the text',
  generating: 'Writing lesson',
  preview:    'Review',
  manual:     'Enter text',
};

const ACCEPT = 'image/*,application/pdf,.pdf,text/plain,.txt,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.docx';

// Friendly, user-facing messages — deliberately hides the internal AI pipeline.
const LOADING_MESSAGES = [
  { ta: 'உங்கள் ஆவணத்தைப் படிக்கிறது…',        en: 'Reading your document' },
  { ta: 'உள்ளடக்கத்தைப் புரிந்துகொள்கிறது…',     en: 'Understanding the content' },
  { ta: 'பாடத்தை எழுதுகிறது…',                  en: 'Writing the lesson' },
  { ta: 'புதிய சொற்களைத் தேர்ந்தெடுக்கிறது…',    en: 'Picking out new words' },
  { ta: 'வினாக்களை உருவாக்குகிறது…',           en: 'Creating quiz questions' },
  { ta: 'இறுதி வேலைகளை முடிக்கிறது…',          en: 'Adding the finishing touches' },
];

const parseLessonSections = (passageText: string) => {
  const parts = passageText.split(/###\s+/);
  const sections: { title: string; content: string }[] = [];
  
  const first = parts[0]?.trim();
  if (first) {
    if (parts.length > 1) {
      sections.push({ title: 'அறிமுகம் / Introduction', content: first });
    } else {
      sections.push({ title: 'பாடம் / Lesson Content', content: first });
    }
  }

  for (let i = 1; i < parts.length; i++) {
    const lines = parts[i].split('\n');
    const title = lines[0].trim();
    const content = lines.slice(1).join('\n').trim();
    if (title && content) {
      sections.push({ title, content });
    }
  }
  return sections;
};

// ── SHA-256 helper ───────────────────────────────────────────────────────────
async function sha256hex(bytes: ArrayBuffer): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('');
}

// ── Per-file extractors ──────────────────────────────────────────────────────

interface OcrResponse {
  extracted_text:     string;
  confidence?:        number;
  minimum_confidence?: number;
  ocr_engine?:        string | null;
  source_hash?:       string | null;
  requires_review?:   boolean;
  review_reason?:     string | null;
}

/**
 * Reading a page happens on the server, where PaddleOCR lives. On our
 * reference Tamil page it reads 94% of words correctly; the browser has no
 * Tamil reader that comes close.
 */
async function extractFromImage(
  file: File,
  onProgress: (s: string) => void,
  language: string = 'tamil',
): Promise<Extraction> {
  onProgress('படிக்கிறது… / Reading…');
  try {
    const stored = localStorage.getItem('ezhil_session');
    const token  = stored ? (JSON.parse(stored) as { accessToken?: string }).accessToken : null;
    const fd     = new FormData();
    fd.append('image', file, file.name);
    fd.append('source_hash', '');
    fd.append('language', language);
    const res  = await fetch('/api/v1/studio/ocr', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: fd,
    });
    if (!res.ok) return emptyExtraction();
    const data = (await res.json()) as OcrResponse;
    const text = data.extracted_text?.trim() ?? '';
    return {
      text,
      confidence:     data.confidence ?? null,
      minConfidence:  data.minimum_confidence ?? null,
      engine:         data.ocr_engine ?? null,
      sourceHash:     data.source_hash ?? null,
      // An empty result is a failure the teacher has to act on, whatever the
      // server said about it.
      requiresReview: data.requires_review ?? !text,
      reviewReason:   data.review_reason ?? null,
    };
  } catch {
    return emptyExtraction();
  }
}

async function extractFromPdf(
  file: File,
  onProgress: (s: string) => void,
  language: string = 'tamil',
): Promise<Extraction> {
  onProgress('PDF திறக்கிறது…');
  const { getDocument, GlobalWorkerOptions } = await import('pdfjs-dist');

  // Use the bundled worker URL via Vite's import.meta.url resolution
  if (!GlobalWorkerOptions.workerSrc) {
    GlobalWorkerOptions.workerSrc = new URL(
      'pdfjs-dist/build/pdf.worker.min.mjs',
      import.meta.url,
    ).href;
  }

  const buf = await file.arrayBuffer();
  const pdf = await getDocument({ data: buf }).promise;
  const texts: string[] = [];
  const scanned: Extraction[] = [];
  const maxOcrPages = 20;
  let ocrPageCount = 0;

  for (let i = 1; i <= pdf.numPages; i++) {
    onProgress(`PDF பக்கம் ${i}/${pdf.numPages}…`);
    const page    = await pdf.getPage(i);
    const content = await page.getTextContent();
    const pageText = content.items
      .map((item: unknown) => {
        const it = item as Record<string, unknown>;
        return typeof it['str'] === 'string' ? it['str'] : '';
      })
      .join(' ')
      .trim();

    if (pageText) {
      texts.push(pageText);
      continue;
    }

    // Scanned page — render + Server OCR
    ocrPageCount++;
    if (ocrPageCount > maxOcrPages) {
      texts.push(`\n[TRUNCATED: PDF has too many pages. OCR is limited to the first ${maxOcrPages} pages for performance.]\n`);
      break;
    }

    onProgress(`OCR பக்கம் ${i}/${pdf.numPages}…`);
    const viewport = page.getViewport({ scale: 2.0 });
    const canvas   = document.createElement('canvas');
    canvas.width   = viewport.width;
    canvas.height  = viewport.height;
    const ctx      = canvas.getContext('2d')!;
    await page.render({ canvasContext: ctx, viewport }).promise;

    const blob = await new Promise<Blob>(res => canvas.toBlob(b => res(b!), 'image/jpeg', 0.85));
    const ocr = await extractFromImage(
      new File([blob], 'page.jpg', { type: 'image/jpeg' }), onProgress, language,
    );
    if (ocr.text) texts.push(ocr.text);
    scanned.push(ocr);
  }

  // A PDF is only as trustworthy as its worst scanned page. Pages that carried
  // real text are exact and should not drag the score down.
  const worst = worstOf(scanned);
  return {
    text:           texts.join('\n\n'),
    confidence:     worst?.confidence ?? null,
    minConfidence:  worst?.minConfidence ?? null,
    engine:         worst?.engine ?? null,
    // Only a single-image upload can reuse the server's cached hash; a
    // multi-page PDF is recombined here and gets its own.
    sourceHash:     null,
    requiresReview: scanned.some(e => e.requiresReview),
    reviewReason:   worst?.reviewReason ?? null,
  };
}

async function extractFromDocx(
  file: File,
  onProgress: (s: string) => void,
): Promise<string> {
  onProgress('.docx படிக்கிறது…');
  const mammoth = await import('mammoth');
  const buf     = await file.arrayBuffer();
  const result  = await mammoth.extractRawText({ arrayBuffer: buf });
  return result.value.trim();
}

async function extractFromTxt(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader  = new FileReader();
    reader.onload = () => resolve((reader.result as string).trim());
    reader.onerror = reject;
    reader.readAsText(file, 'utf-8');
  });
}

async function extractFile(
  file: File,
  onProgress: (s: string) => void,
  language: string = 'tamil',
): Promise<Extraction> {
  const mime = file.type.toLowerCase();
  const name = file.name.toLowerCase();

  if (mime.startsWith('image/'))           return extractFromImage(file, onProgress, language);
  if (mime === 'application/pdf'
      || name.endsWith('.pdf'))             return extractFromPdf(file, onProgress, language);
  if (mime.includes('wordprocessingml')
      || name.endsWith('.docx'))           return exactExtraction(await extractFromDocx(file, onProgress));
  if (mime === 'text/plain'
      || name.endsWith('.txt'))            return exactExtraction(await extractFromTxt(file));
  return emptyExtraction();
}

// ── Component ────────────────────────────────────────────────────────────────

export const LessonStudio: React.FC = () => {
  const navigate = useNavigate();
  const [step,        setStep]        = useState<Step>('upload');
  const [fileResults, setFileResults] = useState<FileResult[]>([]);
  const [docLanguage, setDocLanguage] = useState<'tamil' | 'english'>('tamil');
  const [manualText,  setManualText]  = useState('');
  const [lesson,      setLesson]      = useState<LessonContent | null>(null);
  // Server-side id of the draft /studio/generate just created. Publishing
  // updates that row; without it we would create a second draft instead.
  const [lessonId,    setLessonId]    = useState<string | null>(null);
  const [publishing,  setPublishing]  = useState(false);
  const [dragOver,    setDragOver]    = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const humanSize = (bytes: number) =>
    bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

  const fileKind = (f: File) => {
    const t = f.type; const n = f.name.toLowerCase();
    if (t.startsWith('image/')) return { label: 'Image', color: 'text-primary-fixed', bg: 'bg-primary-fixed/10', ring: 'border-primary-fixed/25' };
    if (t.includes('pdf') || n.endsWith('.pdf')) return { label: 'PDF', color: 'text-error', bg: 'bg-error/10', ring: 'border-error/25' };
    if (t.includes('wordprocessing') || n.endsWith('.docx')) return { label: 'Word', color: 'text-teacher-blue', bg: 'bg-teacher-blue/10', ring: 'border-teacher-blue/25' };
    return { label: 'Text', color: 'text-secondary', bg: 'bg-secondary/10', ring: 'border-secondary/25' };
  };

  // Dynamic progress bar & Error/Dev Logs
  const [currentStageIdx, setCurrentStageIdx] = useState(0);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [devLogs, setDevLogs] = useState<string | null>(null);
  const [showDevLogs, setShowDevLogs] = useState(false);
  const [activeSectionIdx, setActiveSectionIdx] = useState<number | null>(0);

  const updateResult = useCallback((idx: number, patch: Partial<FileResult>) => {
    setFileResults(prev => {
      const next = [...prev];
      next[idx]  = { ...next[idx], ...patch };
      return next;
    });
  }, []);

  // What the extraction pass found, held so the review step and the generate
  // request can both see it.
  const [extraction, setExtraction] = useState<Extraction | null>(null);
  const [reviewText, setReviewText] = useState('');

  // ── Generate lesson from combined text ──────────────────────────────────────
  const generateLesson = useCallback(async (
    text: string,
    hash = '',
    quality: Extraction | null = null,
    reviewed = false,
  ) => {
    setStep('generating');
    setErrorMsg(null);
    setDevLogs(null);
    setCurrentStageIdx(0);

    // Rotate the friendly loading messages while the request is in flight
    const interval = setInterval(() => {
      setCurrentStageIdx(prev => {
        if (prev < LOADING_MESSAGES.length - 1) return prev + 1;
        return prev;
      });
    }, 2200);

    try {
      const stored = localStorage.getItem('ezhil_session');
      const token  = stored ? (JSON.parse(stored) as { accessToken?: string }).accessToken : null;
      
      const res = await fetch('/api/v1/studio/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify({
          ocr_text: text,
          difficulty: 1,
          language: docLanguage,
          source_hash: hash,
          // Reported so the server can judge the extraction even when the text
          // was recombined here and matches no cached hash.
          average_confidence: quality?.confidence ?? null,
          minimum_confidence: quality?.minConfidence ?? null,
          ocr_engine: quality?.engine ?? null,
          text_reviewed: reviewed,
        })
      });

      clearInterval(interval);

      if (!res.ok) {
        const errData = await res.json().catch(() => ({ detail: res.statusText }));
        const errMsg = errData.detail || 'Generation failed';

        // 428: the server wants the text checked before it becomes a lesson.
        // Send the teacher to the review step rather than a dead end.
        if (res.status === 428) {
          setReviewText(text);
          setExtraction(prev => ({
            ...(prev ?? emptyExtraction(text)),
            requiresReview: true,
            reviewReason: errMsg,
          }));
          setStep('review');
          return;
        }

        setErrorMsg(errMsg);
        setDevLogs(JSON.stringify(errData, null, 2));
        setStep('manual');
        return;
      }

      const result = (await res.json()) as GenerateResponse;

      if (result?.lesson) {
        const raw          = result.lesson as unknown as Record<string, unknown>;
        const passageLines = (raw.passage as { lines?: string[] } | undefined)?.lines ?? [];
        const passage      = passageLines.join('\n') || String(raw.passage || text.slice(0, 200));
        const vocabulary: Vocab[] = ((raw.vocabulary ?? []) as Record<string, unknown>[]).map(v => ({
          word:    String(v.word ?? ''),
          meaning: String(v.meaning_en ?? v.meaning ?? ''),
        }));
        const questions: QuizItem[] = ((raw.quiz ?? raw.questions ?? []) as Record<string, unknown>[]).map(q => ({
          q:       String(q.question_ta ?? q.q ?? ''),
          options: (q.options_ta ?? q.options ?? []) as string[],
          answer:  Number(q.correct_index ?? q.answer ?? 0),
        }));
        setLesson({ title: String(raw.title ?? 'பாடம்'), passage, vocabulary, questions });
        setLessonId(result.lesson_id ?? null);
        setStep('preview');
        setActiveSectionIdx(0);
      } else {
        setErrorMsg('Invalid response from lesson studio server.');
        setStep('manual');
      }
    } catch (err: any) {
      clearInterval(interval);
      setErrorMsg(err.message || 'Network error occurred.');
      setStep('manual');
    }
  }, [docLanguage]);

  // ── Main: process all selected files in order ────────────────────────────────
  const handleProcess = useCallback(async () => {
    if (!fileResults.length) return;
    setStep('extracting');

    const combinedParts: string[] = [];
    const extractions: Extraction[] = [];

    for (let i = 0; i < fileResults.length; i++) {
      updateResult(i, { status: 'extracting', progress: '…' });

      const result = await extractFile(
        fileResults[i].file,
        p => updateResult(i, { progress: p }),
        docLanguage,
      );

      updateResult(i, {
        status: 'done',
        text: result.text,
        progress: result.text ? `${result.text.length} chars` : 'nothing readable',
        quality: result,
      });
      if (result.text) combinedParts.push(result.text);
      extractions.push(result);
    }

    const combined = combinedParts.join('\n\n---\n\n');

    if (!combined.trim()) {
      setStep('manual');
      return;
    }

    // The batch inherits its weakest reading: one bad page is enough to make
    // the whole lesson wrong, so it is enough to warrant a check.
    const quality = aggregate(extractions, combined);

    // A single extraction can reuse the hash the server already cached against
    // it; anything recombined here needs its own.
    const buf  = await new Blob([combined]).arrayBuffer();
    const hash = quality.sourceHash
      ?? 'multi:' + (await sha256hex(buf)).slice(0, 32);

    const resolved = { ...quality, sourceHash: hash };
    setExtraction(resolved);

    if (nextStep(resolved) === 'review') {
      setReviewText(combined);
      setStep('review');
      return;
    }

    await generateLesson(combined, hash, resolved);
  }, [fileResults, generateLesson, updateResult, docLanguage]);

  /** Teacher has read (and possibly corrected) the extracted text. */
  const confirmReview = useCallback(async () => {
    const q = extraction ?? emptyExtraction(reviewText);
    await generateLesson(reviewText, q.sourceHash ?? '', q, true);
  }, [extraction, reviewText, generateLesson]);

  /** Canonical lesson JSON — the shape LessonReader and LessonQuiz parse. */
  const toContentJson = (l: LessonContent) => {
    const lines = l.passage.split('\n').map(s => s.trim()).filter(Boolean);
    return JSON.stringify({
      title:      l.title,
      passage:    { lines, line_count: lines.length },
      vocabulary: l.vocabulary.map(v => ({ word: v.word, meaning_en: v.meaning })),
      quiz:       l.questions.map(q => ({
        question_ta:   q.q,
        options_ta:    q.options,
        correct_index: q.answer,
      })),
    });
  };

  const handlePublish = async () => {
    if (!lesson) return;
    if (!lessonId) {
      setErrorMsg('This lesson has no server id yet — regenerate it before publishing.');
      return;
    }
    setPublishing(true);
    setErrorMsg(null);
    try {
      // Publishing updates the draft /studio/generate created. Posting to
      // /generate again would produce a second, still-unpublished lesson.
      await apiFetch(`/api/v1/lessons/${lessonId}`, {
        method: 'PUT',
        body: JSON.stringify({
          id:           lessonId,
          title:        lesson.title,
          content_json: toContentJson(lesson),
          difficulty:   1,
          language:     docLanguage,
          is_published: true,
          lesson_type:  'story',
          assigned_to:  'class',
          cache_hit:    false,
        }),
      });
      navigate('/teacher/lessons');
    } catch (err) {
      // A failed publish must not look like a successful one — the teacher
      // would tell students to expect a lesson that never arrives.
      setErrorMsg(err instanceof Error ? err.message : 'Could not publish the lesson.');
    } finally {
      setPublishing(false);
    }
  };

  const handleFilesSelected = (incoming: FileList | null) => {
    if (!incoming) return;
    const arr = Array.from(incoming);
    setFileResults(arr.map(f => ({ file: f, status: 'pending', text: '', progress: '' })));
  };

  const removeFile = (idx: number) => {
    setFileResults(prev => prev.filter((_, i) => i !== idx));
  };

  const stepIdx = STEPS.indexOf(step as (typeof STEPS)[number]);

  // ── File type icon ───────────────────────────────────────────────────────────
  const fileIcon = (f: File) => {
    const t = f.type;
    if (t.startsWith('image/'))      return 'image';
    if (t.includes('pdf'))           return 'picture_as_pdf';
    if (t.includes('wordprocessing') || f.name.endsWith('.docx')) return 'description';
    return 'text_snippet';
  };

  // ── Status chip ──────────────────────────────────────────────────────────────
  return (
    <div className="max-w-3xl mx-auto space-y-8 font-body-tamil">
      {/* Header */}
      <div className="flex items-center gap-3 animate-fade-in">
        <div className="w-12 h-12 r-chip bg-studio-purple/20 border border-studio-purple/30 flex items-center justify-center shadow-[0_0_16px_rgba(124,58,237,0.25)] flex-shrink-0">
          <span className="material-symbols-outlined text-studio-purple text-2xl" style={{ fontVariationSettings: "'FILL' 1" }}>auto_awesome</span>
        </div>
        <div>
          <h1 className="font-display-tamil text-2xl font-bold heading-display">Lesson Studio</h1>
          <p className="text-text-muted text-sm">பாட உருவாக்கம் — Upload → Extract → AI Generate</p>
        </div>
      </div>

      {/* Step rail. Four identical bars showed progress without saying what was
          happening — a teacher watching a 30s generation wants to know the app
          is reading their document, not just that "something" is at 50%.
          Labels collapse to the current step only on narrow screens. */}
      <nav aria-label="Progress" className="flex items-stretch gap-1.5">
        {STEPS.map((s, i) => {
          const done    = stepIdx > i;
          const current = step === s;
          return (
            <div key={s} className="flex-1 min-w-0">
              <div
                className={`h-1.5 r-chip transition-colors duration-500 ${
                  current ? 'bg-primary-fixed'
                  : done  ? 'bg-primary-fixed/45'
                          : 'bg-white/10'
                }`}
              />
              <p
                className={`mt-2 text-xs truncate transition-colors ${
                  current ? 'text-primary-fixed font-bold'
                  : done  ? 'text-on-surface-variant'
                          : 'text-text-muted'
                } ${current ? '' : 'hidden sm:block'}`}
              >
                {STEP_LABELS[s]}
              </p>
            </div>
          );
        })}
      </nav>

      {/* Error Alert Display */}
      {errorMsg && (
        <div className="bg-error/10 border border-error/20 r-chip p-4 space-y-2 text-left animate-slide-in">
          <div className="flex items-start gap-2.5">
            <span className="material-symbols-outlined text-error text-xl mt-0.5">warning</span>
            <div className="flex-1">
              <p className="text-error font-bold text-sm">உருவாக்கம் தோல்வியடைந்தது / Generation Failed</p>
              <p className="text-white text-sm mt-0.5">{errorMsg}</p>
            </div>
            <button onClick={() => setErrorMsg(null)} className="text-text-muted hover:text-white cursor-pointer">
              <span className="material-symbols-outlined text-sm">close</span>
            </button>
          </div>
          
          <div className="pt-1">
            <button 
              onClick={() => setShowDevLogs(!showDevLogs)}
              className="text-xs text-text-muted hover:text-white uppercase tracking-wider font-bold underline cursor-pointer"
            >
              {showDevLogs ? 'Hide technical logs' : 'Show technical logs (developer)'}
            </button>
            {showDevLogs && devLogs && (
              <pre className="mt-2 p-3 bg-black/40 rounded border border-white/5 font-mono text-xs text-text-muted overflow-x-auto whitespace-pre-wrap max-h-40">
                {devLogs}
              </pre>
            )}
          </div>
        </div>
      )}

      {/* ── Step: Upload ── */}
      {step === 'upload' && (
        <div className="glass-panel r-hero p-6 md:p-8 space-y-6 relative overflow-hidden animate-fade-in">
          <div className="orb w-72 h-72 bg-studio-purple/10 top-[-4rem] right-[-3rem]" />

          {/* Language Selector */}
          <div className="relative flex flex-row items-center justify-between gap-4 p-3 bg-black/20 r-card border border-white/10">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-text-muted text-lg">translate</span>
              <span className="text-sm font-semibold text-text-primary">Document Language</span>
            </div>
            <div className="flex bg-white/5 p-1 r-chip border border-white/5">
              {(['tamil', 'english'] as const).map(lang => (
                <button key={lang} type="button" onClick={() => setDocLanguage(lang)}
                  className={`px-4 py-1.5 r-chip text-xs font-bold transition-all duration-200 cursor-pointer ${
                    docLanguage === lang
                      ? 'bg-primary-fixed text-bg-deep shadow-[0_0_12px_rgba(98,249,238,0.45)]'
                      : 'text-text-muted hover:text-white hover:bg-white/5'
                  }`}>
                  {lang === 'tamil' ? 'தமிழ் / Tamil' : 'English'}
                </button>
              ))}
            </div>
          </div>

          <input ref={fileRef} type="file" accept={ACCEPT} multiple className="hidden"
            onChange={e => handleFilesSelected(e.target.files)} />

          {/* Drag & drop zone */}
          <div
            onClick={() => fileRef.current?.click()}
            onDragOver={e => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={e => { e.preventDefault(); setDragOver(false); }}
            onDrop={e => { e.preventDefault(); setDragOver(false); handleFilesSelected(e.dataTransfer.files); }}
            className={`relative cursor-pointer r-card border-2 border-dashed px-6 py-10 flex flex-col items-center justify-center text-center transition-all duration-300 group ${
              dragOver
                ? 'border-primary-fixed bg-primary-fixed/10 scale-[1.01] shadow-[0_0_40px_rgba(98,249,238,0.2)]'
                : 'border-white/15 hover:border-primary-fixed/60 hover:bg-white/[0.03]'
            }`}
          >
            <div className={`relative w-20 h-20 mb-4 flex items-center justify-center transition-transform duration-300 ${dragOver ? 'scale-110' : 'group-hover:scale-105'}`}>
              <div className="absolute inset-0 r-card bg-primary-fixed/15 blur-xl" />
              <div className={`absolute inset-0 r-card border border-primary-fixed/25 ${dragOver ? 'animate-pulse-ring' : ''}`} />
              <span className="relative material-symbols-outlined text-primary-fixed text-5xl animate-bob origin-bottom" style={{ fontVariationSettings: "'FILL' 1" }}>
                {dragOver ? 'file_download' : 'cloud_upload'}
              </span>
            </div>
            <h2 className="text-xl font-bold text-white mb-1">
              {dragOver ? 'விடுங்கள்! / Drop to upload' : 'கோப்புகளை இழுத்து விடுங்கள்'}
            </h2>
            <p className="text-text-muted text-sm">
              Drag & drop, or <span className="text-primary-fixed font-semibold">browse</span> your files
            </p>
            {/* Supported type chips */}
            <div className="flex flex-wrap items-center justify-center gap-2 mt-4">
              {[
                { i: 'image',           t: 'JPG / PNG' },
                { i: 'picture_as_pdf',  t: 'PDF' },
                { i: 'description',     t: 'Word' },
                { i: 'text_snippet',    t: 'Text' },
              ].map(c => (
                <span key={c.t} className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-text-muted text-xs font-medium">
                  <span className="material-symbols-outlined text-sm">{c.i}</span>{c.t}
                </span>
              ))}
            </div>
          </div>

          {/* Selected files */}
          {fileResults.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center justify-between px-1">
                <p className="text-text-muted text-xs uppercase tracking-wider font-bold">{fileResults.length} file{fileResults.length > 1 ? 's' : ''} ready</p>
                <button onClick={() => setFileResults([])} className="text-text-muted hover:text-error text-xs font-semibold transition-colors cursor-pointer">Clear all</button>
              </div>
              <div className="space-y-2 stagger-children">
                {fileResults.map((r, idx) => {
                  const k = fileKind(r.file);
                  return (
                    <div key={idx} className="flex items-center gap-3 p-3 bg-black/20 r-chip border border-white/10 hover:border-white/20 transition-colors group">
                      <div className={`w-10 h-10 r-chip ${k.bg} border ${k.ring} flex items-center justify-center flex-shrink-0`}>
                        <span className={`material-symbols-outlined ${k.color}`}>{fileIcon(r.file)}</span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-white text-sm font-medium truncate">{r.file.name}</p>
                        <p className="text-text-muted text-xs">{k.label} · {humanSize(r.file.size)}</p>
                      </div>
                      <button onClick={() => removeFile(idx)} title="Remove"
                        className="w-8 h-8 r-chip flex items-center justify-center text-text-muted hover:text-error hover:bg-error/10 transition-all opacity-60 group-hover:opacity-100">
                        <span className="material-symbols-outlined text-lg">close</span>
                      </button>
                    </div>
                  );
                })}
              </div>

              {/* Add more / Scan */}
              <div className="flex gap-3 pt-1">
                <button onClick={() => fileRef.current?.click()}
                  className="flex-1 h-12 border border-white/15 text-text-muted r-chip text-sm font-semibold hover:bg-white/5 hover:text-white transition-colors flex items-center justify-center gap-2 cursor-pointer">
                  <span className="material-symbols-outlined text-base">add</span> Add more
                </button>
                <button onClick={handleProcess}
                  className="flex-[1.5] h-12 bg-gradient-to-r from-primary-fixed to-accent-cyan text-bg-deep r-chip font-bold text-sm shadow-[0_0_20px_rgba(98,249,238,0.3)] hover:shadow-[0_0_32px_rgba(98,249,238,0.5)] active:scale-[0.98] transition-all flex items-center justify-center gap-2 cursor-pointer">
                  <span className="material-symbols-outlined text-base">auto_awesome</span>
                  Scan &amp; Generate Lesson
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Step: Extracting (scanning) ── */}
      {step === 'extracting' && (
        <div className="glass-panel r-hero p-6 md:p-10 space-y-7 relative overflow-hidden animate-fade-in border border-primary-fixed/15">
          <div className="orb w-80 h-80 bg-primary-fixed/10 top-[-4rem] left-1/2 -translate-x-1/2" />

          {/* Animated document scanner */}
          <div className="relative mx-auto w-28 h-32 flex items-center justify-center">
            <div className="absolute inset-0 rounded-full bg-primary-fixed/10 blur-2xl scale-150" />
            <div className="relative w-24 h-30 r-chip bg-bg-surface/70 border border-white/15 shadow-[0_8px_28px_rgba(0,0,0,0.4)] overflow-hidden p-3" style={{ height: '120px' }}>
              {/* faux text lines */}
              <div className="space-y-2 pt-1">
                {[10,8,11,7,9,6].map((w, i) => (
                  <div key={i} className="h-1.5 rounded-full bg-white/12" style={{ width: `${w * 8}%` }} />
                ))}
              </div>
              {/* scan line */}
              <div className="absolute left-0 right-0 h-[3px] bg-gradient-to-r from-transparent via-primary-fixed to-transparent shadow-[0_0_14px_rgba(98,249,238,0.9)] animate-scan-sweep" />
            </div>
          </div>

          <div className="text-center relative">
            <h2 className="text-xl font-bold heading-display">எழுத்துக்களை படிக்கிறது…</h2>
            <p className="text-text-muted text-sm mt-1">Scanning your documents for text</p>
          </div>

          {/* Per-file progress */}
          <div className="space-y-2.5 relative">
            {fileResults.map((r, idx) => {
              const k = fileKind(r.file);
              const done = r.status === 'done';
              const err = r.status === 'error';
              return (
                <div key={idx} className="flex items-center gap-3 p-3 bg-black/20 r-chip border border-white/10">
                  <div className={`w-10 h-10 r-chip ${k.bg} border ${k.ring} flex items-center justify-center flex-shrink-0`}>
                    <span className={`material-symbols-outlined ${k.color}`}>{fileIcon(r.file)}</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-white text-sm font-medium truncate">{r.file.name}</p>
                    <div className="h-1.5 bg-white/5 rounded-full overflow-hidden mt-1.5">
                      <div className={`h-full rounded-full transition-all duration-500 ${
                        done ? 'w-full bg-success' : err ? 'w-full bg-error' : 'w-2/3 bg-primary-fixed animate-pulse'
                      }`} />
                    </div>
                  </div>
                  <span className={`material-symbols-outlined text-xl flex-shrink-0 ${
                    done ? 'text-success' : err ? 'text-error' : 'text-primary-fixed animate-spin'
                  }`} style={done ? { fontVariationSettings: "'FILL' 1" } : undefined}>
                    {done ? 'check_circle' : err ? 'error' : 'progress_activity'}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ── Step: Check the extracted text ──
          Shown when the reader was not confident. The words here become a
          passage a dyslexic child will practise, so a wrong one costs more
          than the minute it takes to check. */}
      {step === 'review' && (
        <div className="glass-panel r-card p-6 md:p-8 space-y-5 max-w-3xl mx-auto surface-lit">
          <div className="flex items-start gap-3">
            <span className="material-symbols-outlined text-secondary text-2xl mt-0.5">
              rate_review
            </span>
            <div className="min-w-0">
              <h2 className="heading-display text-xl">Check the text before we build the lesson</h2>
              <p className="text-text-muted text-sm mt-1">
                {extraction?.reviewReason
                  ?? 'Some words may have been read incorrectly. Fix anything that looks wrong.'}
              </p>
            </div>
          </div>

          {extraction?.confidence != null && (
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-text-muted tabular-nums">
              <span>
                Reading accuracy&nbsp;
                <span className="text-secondary font-semibold">
                  {Math.round(extraction.confidence * 100)}%
                </span>
              </span>
              {extraction.engine && <span>Reader: {extraction.engine}</span>}
            </div>
          )}

          <label htmlFor="review-text" className="sr-only">Extracted lesson text</label>
          <textarea
            id="review-text"
            rows={12}
            value={reviewText}
            onChange={e => setReviewText(e.target.value)}
            spellCheck={false}
            placeholder="இங்கே பாட உரையை தட்டச்சு செய்யவும் / Type the lesson text here…"
            className="w-full bg-black/20 border border-white/10 r-chip px-4 py-3 text-white
                       resize-y focus:outline-none focus:border-primary-fixed
                       font-reader-tamil text-lg leading-relaxed"
          />

          {/* A barely-readable photo lands here with almost nothing in the box.
              Without this the button is simply dead and the reason invisible. */}
          {reviewText.trim().length < 10 && (
            <p className="text-text-muted text-sm">
              {reviewText.trim().length === 0
                ? 'Nothing could be read from this photo. Type the passage above, or go back and retake it.'
                : 'Only a few characters came through. Type the rest of the passage above, or go back and retake the photo.'}
            </p>
          )}

          <div className="flex flex-col sm:flex-row gap-3">
            <button
              onClick={() => setStep('upload')}
              className="sm:flex-1 h-12 min-h-12 border border-white/15 text-text-muted r-chip
                         hover:bg-white/5 transition-colors"
            >
              ← Use a different photo
            </button>
            <button
              onClick={confirmReview}
              disabled={reviewText.trim().length < 10}
              className="sm:flex-[2] h-12 min-h-12 bg-primary-fixed text-bg-deep r-chip font-bold
                         disabled:opacity-50 hover:brightness-110 active:scale-[0.99] transition-all"
            >
              This is correct — create the lesson →
            </button>
          </div>
        </div>
      )}

      {/* ── Step: Manual entry ── */}
      {step === 'manual' && (
        <div className="glass-panel r-card p-8 space-y-5">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-studio-purple text-2xl">edit_note</span>
            <div>
              <h2 className="text-xl font-bold text-white">Manual Text Entry</h2>
              <p className="text-text-muted text-sm">OCR could not extract text — type or paste the passage below.</p>
            </div>
          </div>
          <textarea rows={8} value={manualText} onChange={e => setManualText(e.target.value)}
            className="w-full bg-black/20 border border-white/10 r-chip px-4 py-3 text-white resize-none focus:outline-none focus:border-primary-fixed font-reader-tamil text-lg leading-relaxed"
            placeholder="இங்கே பாட உரையை தட்டச்சு செய்யவும் / Type lesson text here…" />
          <div className="flex gap-3">
            <button onClick={() => setStep('upload')}
              className="flex-1 h-12 border border-white/15 text-text-muted r-chip hover:bg-white/5 transition-colors">
              ← Back
            </button>
            {/* Typed by hand, so it is reviewed by definition — the quality
                gate must not block text a teacher wrote themselves. */}
            <button onClick={() => generateLesson(manualText, '', null, true)} disabled={manualText.trim().length < 10}
              className="flex-1 h-12 bg-primary-fixed text-bg-deep r-chip font-bold disabled:opacity-50 transition-all">
              Generate Lesson →
            </button>
          </div>
        </div>
      )}

      {/* ── Step: Generating — friendly loading screen ── */}
      {step === 'generating' && (() => {
        const msg = LOADING_MESSAGES[Math.min(currentStageIdx, LOADING_MESSAGES.length - 1)];
        const pct = Math.min(95, Math.round(((currentStageIdx + 1) / LOADING_MESSAGES.length) * 100));
        return (
          <div className="glass-panel r-hero p-8 md:p-14 animate-fade-in max-w-xl mx-auto border border-studio-purple/20 shadow-2xl relative overflow-hidden">
            {/* Ambient glows + drifting sparkles */}
            <div className="orb w-80 h-80 bg-studio-purple/18 top-[-5rem] left-1/2 -translate-x-1/2" />
            <div className="orb w-64 h-64 bg-primary-fixed/12 bottom-[-4rem] right-[-2rem]" />
            <span className="absolute top-10 right-12 text-lg animate-twinkle">✨</span>
            <span className="absolute top-24 left-12 text-sm animate-twinkle delay-300">⭐</span>
            <span className="absolute bottom-16 right-20 text-base animate-twinkle delay-500">✨</span>

            <div className="relative flex flex-col items-center text-center gap-7">
              {/* Animated AI orb with spinning halo */}
              <div className="relative w-32 h-32 flex items-center justify-center">
                <div className="absolute w-32 h-32 rounded-full bg-[conic-gradient(from_0deg,rgba(124,58,237,0.5),transparent_35%,rgba(98,249,238,0.4),transparent_70%,rgba(124,58,237,0.5))] blur-md animate-spin-slow" />
                <div className="absolute inset-1 rounded-full border border-studio-purple/30 animate-pulse-ring" />
                <div className="relative w-24 h-24 rounded-full bg-bg-surface/70 backdrop-blur-xl border border-white/10 flex items-center justify-center shadow-[0_8px_32px_rgba(0,0,0,0.45)]">
                  <span className="material-symbols-outlined text-studio-purple text-5xl animate-pulse" style={{ fontVariationSettings: "'FILL' 1" }}>auto_awesome</span>
                </div>
              </div>

              {/* Title + rotating friendly message */}
              <div className="space-y-2">
                <h2 className="text-2xl font-bold heading-display">உங்கள் பாடம் தயாராகிறது</h2>
                <p key={currentStageIdx} className="font-display-tamil text-lg text-white animate-fade-in">{msg.ta}</p>
                <p className="text-text-muted text-sm">{msg.en}…</p>
              </div>

              {/* Smooth progress bar */}
              <div className="w-full max-w-sm">
                <div className="h-2.5 bg-white/5 rounded-full overflow-hidden relative shimmer">
                  <div className="h-full bg-gradient-to-r from-studio-purple via-primary-fixed to-accent-cyan rounded-full transition-all duration-700 ease-out shadow-[0_0_14px_rgba(98,249,238,0.5)]"
                    style={{ width: `${pct}%` }} />
                </div>
                <p className="text-text-muted text-xs mt-3 flex items-center justify-center gap-1.5">
                  <span className="material-symbols-outlined text-sm text-primary-fixed">bolt</span>
                  This usually takes just a few moments
                </p>
              </div>
            </div>
          </div>
        );
      })()}

      {/* ── Step: Preview ── */}
      {step === 'preview' && lesson && (
        <div className="space-y-5">
          <div className="glass-panel r-card p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-bold text-white">Preview Generated Lesson</h2>
              <button onClick={() => setStep('upload')} className="text-text-muted hover:text-white text-sm flex items-center gap-1">
                <span className="material-symbols-outlined text-base">restart_alt</span> Regenerate
              </button>
            </div>
            <div>
              <label className="text-text-muted text-xs uppercase">Title / தலைப்பு</label>
              <input value={lesson.title} onChange={e => setLesson({ ...lesson, title: e.target.value })}
                className="w-full bg-black/20 border border-white/10 r-chip px-4 py-2.5 text-white mt-1 focus:outline-none focus:border-primary-fixed" />
            </div>
            <div>
              <label className="text-text-muted text-xs uppercase">Passage / பாட உரை (Raw Edit)</label>
              <textarea rows={4} value={lesson.passage} onChange={e => setLesson({ ...lesson, passage: e.target.value })}
                className="w-full bg-black/20 border border-white/10 r-chip px-4 py-3 text-white mt-1 resize-none focus:outline-none focus:border-primary-fixed font-reader-tamil text-lg leading-relaxed" />
            </div>

            {/* Collapsible structured section view */}
            <div className="space-y-3 pt-2">
              <label className="text-text-muted text-xs uppercase">Passage Sections / பாட பிரிவுகள்</label>
              <div className="space-y-2">
                {parseLessonSections(lesson.passage).map((sec, idx) => (
                  <div key={idx} className="border border-white/10 r-chip overflow-hidden bg-black/10">
                    <button 
                      type="button"
                      onClick={() => setActiveSectionIdx(activeSectionIdx === idx ? null : idx)}
                      className="w-full px-4 py-3 flex justify-between items-center bg-white/5 hover:bg-white/10 transition-colors text-left cursor-pointer"
                    >
                      <span className="font-bold text-white text-sm">{sec.title}</span>
                      <span className="material-symbols-outlined text-text-muted">
                        {activeSectionIdx === idx ? 'expand_less' : 'expand_more'}
                      </span>
                    </button>
                    {activeSectionIdx === idx && (
                      <div className="p-4 text-on-surface font-reader-tamil text-base leading-relaxed whitespace-pre-line border-t border-white/5 word-break-normal">
                        {sec.content}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {lesson.vocabulary.length > 0 && (
            <div className="glass-panel r-card p-5 space-y-3">
              <h3 className="text-white font-bold">Vocabulary / சொற்கள் ({lesson.vocabulary.length} words)</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {lesson.vocabulary.map((v, i) => (
                  <div key={i} className="bg-black/20 r-chip p-3 border border-white/5">
                    <p className="text-primary-fixed font-bold font-display-tamil">{v.word}</p>
                    <p className="text-text-muted text-xs">{v.meaning}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {lesson.questions.length > 0 && (
            <div className="glass-panel r-card p-5 space-y-4">
              <h3 className="text-white font-bold">Quiz Questions ({lesson.questions.length})</h3>
              {lesson.questions.map((q, qi) => (
                <div key={qi} className="bg-black/20 r-chip p-4 border border-white/5 space-y-2">
                  <p className="text-white font-semibold font-display-tamil">{qi + 1}. {q.q}</p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {q.options.map((opt, oi) => (
                      <div key={oi} className={`text-xs px-3 py-2 r-chip ${oi === q.answer ? 'bg-success/20 text-success border border-success/30' : 'bg-white/5 text-text-muted'}`}>
                        {oi === q.answer && '✓ '}{opt}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          <button onClick={handlePublish} disabled={publishing}
            className="w-full h-14 bg-primary-fixed text-bg-deep r-chip font-bold text-lg disabled:opacity-50 transition-all flex items-center justify-center gap-2">
            {publishing
              ? <div className="w-5 h-5 border-2 border-bg-deep/30 border-t-bg-deep rounded-full animate-spin" />
              : <span className="material-symbols-outlined">publish</span>}
            {publishing ? 'Publishing...' : 'Publish Lesson / வெளியிடு'}
          </button>
        </div>
      )}
    </div>
  );
};
