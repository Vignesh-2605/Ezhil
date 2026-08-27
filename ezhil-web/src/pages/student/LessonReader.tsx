import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { ExitConfirmationDialog } from '../../components/ui/ExitConfirmationDialog';
import { speakTamil } from '../../services/speechService';
import { useTamilVoice } from '../../hooks/useTamilVoice';
import { db } from '../../db/db';
import { useLiveQuery } from 'dexie-react-hooks';

const MOCK_LESSON = {
  title: 'யானையும் எறும்பும்',
  passage: `ஒரு காட்டில் ஒரு பெரிய யானை இருந்தது. அந்த யானைக்கு தன்னுடைய பெரிய உடலில் மிகுந்த பெருமை இருந்தது.

ஒரு நாள் அந்த யானை ஒரு சிறிய எறும்பு வீட்டின் மீது மிதித்தது. "நான் உன்னை மிதித்துவிடுவேன்!" என்று யானை சொன்னது.

"சிறியதாக இருந்தாலும் நான் வலிமையானவன்!" என்று எறும்பு சொன்னது.

யானை சிரித்தது. ஆனால் எறும்பு தன் தோழர்களை அழைத்தது. ஆயிரக்கணக்கான எறும்புகள் வந்து யானையின் காலை கடித்தன.

யானை வலியால் கத்தியது. "மன்னிக்கவும்! சிறியவர்களை ஒருபோதும் அலட்சியப்படுத்தக் கூடாது!" என்று கற்றுக்கொண்டது.`,
  vocabulary: [
    { word: 'யானை', meaning: 'Elephant' },
    { word: 'எறும்பு', meaning: 'Ant' },
    { word: 'காடு', meaning: 'Forest' },
    { word: 'வலிமை', meaning: 'Strength' },
    { word: 'அலட்சியம்', meaning: 'Neglect' },
  ],
};

export const LessonReader: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const lessonId = (location.state as { lessonId?: string })?.lessonId;
  const [exitOpen, setExitOpen] = useState(false);
  const [wordTapped, setWordTapped] = useState<string | null>(null);

  const dbLesson = useLiveQuery(async () => {
    if (!lessonId) return null;
    return await db.lessons.get(lessonId);
  }, [lessonId]);

  let parsedLesson = MOCK_LESSON;
  if (dbLesson) {
    try {
      const parsed = JSON.parse(dbLesson.contentJson);
      
      let passageText = '';
      if (parsed.passage) {
        if (Array.isArray(parsed.passage.lines)) {
          passageText = parsed.passage.lines.join('\n\n');
        } else if (typeof parsed.passage === 'string') {
          passageText = parsed.passage;
        } else if (typeof parsed.passage === 'object' && parsed.passage.text) {
          passageText = parsed.passage.text;
        }
      }

      parsedLesson = {
        title: dbLesson.title || parsed.title || 'பாடம்',
        passage: passageText || MOCK_LESSON.passage,
        vocabulary: parsed.vocabulary?.map((v: any) => ({
          word: v.word || '',
          meaning: v.meaning_ta || v.meaning || v.meaning_en || ''
        })) ?? MOCK_LESSON.vocabulary
      };
    } catch (e) {
      console.error("Failed to parse lesson contentJson", e);
    }
  }

  const { title, passage, vocabulary } = parsedLesson;
  const hasVoice = useTamilVoice();

  const handleWordTap = (word: string) => {
    // Strip punctuation for matching
    const cleanWord = word.replace(/[.,\/#!$%\^&\*;:{}=\-_`~()?]/g, "");
    setWordTapped(cleanWord);
    speakTamil(cleanWord);
  };

  const handleSpeakPassage = () => {
    speakTamil(passage);
  };

  return (
    <div className="bg-bg-reader min-h-dvh flex flex-col font-body-tamil">
      {/* Top bar */}
      {/* Reader chrome only — the reading surface below stays untouched
          (ReaderConstraints). 48px touch targets throughout. */}
      <header className="sticky top-0 z-50 bg-bg-reader/95 backdrop-blur-sm border-b border-[#0F2E33]/15 shadow-[0_2px_12px_rgba(15,46,51,0.08)] flex items-center justify-between px-3 h-16">
        <button onClick={() => setExitOpen(true)} aria-label="Back"
          className="w-12 h-12 flex items-center justify-center text-bg-deep/70 hover:bg-[#0F2E33]/8 rounded-full transition-colors cursor-pointer">
          <span className="material-symbols-outlined">arrow_back</span>
        </button>
        <h2 className="font-display-tamil font-bold text-bg-deep text-lg truncate flex-1 text-center px-3">{title}</h2>
        {hasVoice && (
          <button onClick={handleSpeakPassage} aria-label="Read passage aloud"
            className="w-12 h-12 flex items-center justify-center text-bg-deep/70 hover:bg-[#0F2E33]/8 rounded-full transition-colors mr-1 cursor-pointer">
            <span className="material-symbols-outlined">volume_up</span>
          </button>
        )}
        <button onClick={() => navigate('/student/assessment/start')}
          className="h-12 bg-[#0F2E33] text-bg-reader font-bold text-sm px-5 rounded-full active:scale-95 transition-all cursor-pointer">
          வாசி / Read
        </button>
      </header>

      {/* Reading area */}
      <main className="flex-1 w-full max-w-2xl mx-auto px-6 py-8 space-y-8 overflow-safe-container">
        <h1 className="font-display-tamil text-3xl font-bold text-bg-deep text-center">{title}</h1>

        <div 
          className="text-bg-deep/90 font-reader-tamil word-break-normal"
          style={{
            fontSize: '24px',
            lineHeight: '43px',
            letterSpacing: '0.96px'
          }}
        >
          {passage.split('\n\n').map((para, i) => (
            <p key={i} className="mb-6">
              {para.split(/(\s+)/).map((token, j) => {
                // Clean punctuation to look up the vocabulary word
                const cleanToken = token.replace(/[.,\/#!$%\^&\*;:{}=\-_`~()?]/g, "").trim();
                const hasVocab = cleanToken && vocabulary.some(v => v.word === cleanToken);
                return hasVocab ? (
                  <span key={j} onClick={() => handleWordTap(cleanToken)}
                    className="underline decoration-dotted decoration-primary-fixed-dim cursor-pointer hover:text-primary-fixed-dim transition-colors font-bold">
                    {token}
                  </span>
                ) : token;
              })}
            </p>
          ))}
        </div>

        {/* Vocabulary box */}
        <div className="bg-bg-deep/5 r-card p-5 border border-bg-deep/10 space-y-3">
          {/* No tracking: this label contains Tamil, where letter-spacing breaks
              the conjuncts a child is trying to read. */}
          <h3 className="font-dashboard-title font-bold text-bg-deep text-sm uppercase">
            சொற்கள் | Vocabulary
          </h3>
          <div className="grid grid-cols-1 gap-2">
            {vocabulary.map(v => (
              <div key={v.word} className="flex justify-between items-center py-2 border-b border-bg-deep/10 last:border-0">
                <button onClick={() => speakTamil(v.word)} className="font-display-tamil text-lg font-bold text-bg-deep hover:text-primary-fixed-dim transition-colors flex items-center gap-1.5 cursor-pointer">
                  {hasVoice && <span className="material-symbols-outlined text-sm">volume_up</span>}
                  {v.word}
                </button>
                <button onClick={() => navigate(`/student/vocabulary/${encodeURIComponent(v.word)}`)}
                  className="text-bg-deep/60 font-bilingual-sub text-sm hover:text-bg-deep transition-colors flex items-center gap-1 cursor-pointer">
                  {v.meaning}
                  <span className="material-symbols-outlined text-sm">chevron_right</span>
                </button>
              </div>
            ))}
          </div>
        </div>

        <button onClick={() => navigate('/student/quiz', { state: { lessonId } })}
          className="w-full h-14 bg-bg-deep text-bg-reader font-bold text-lg r-chip active:scale-[0.98] transition-all flex items-center justify-center gap-2 shadow-lg cursor-pointer">
          <span className="material-symbols-outlined" style={{ fontVariationSettings: "'FILL' 1" }}>quiz</span>
          Try Quiz
        </button>
      </main>


      {/* Word popup */}
      {wordTapped && (
        <div className="fixed inset-x-4 bottom-24 z-50 bg-bg-deep r-card surface-lit p-4 border border-white/10 flex items-center justify-between"
          onClick={() => setWordTapped(null)}>
          <div>
            <p className="font-display-tamil text-2xl font-bold text-primary-fixed">{wordTapped}</p>
            <p className="text-text-muted text-sm">{vocabulary.find(v => v.word === wordTapped)?.meaning}</p>
          </div>
          {hasVoice && (
            <button onClick={(e) => { e.stopPropagation(); speakTamil(wordTapped); }}
              className="w-10 h-10 rounded-full bg-primary-fixed/20 border border-primary-fixed/40 flex items-center justify-center active:scale-90 transition-transform">
              <span className="material-symbols-outlined text-primary-fixed text-lg">volume_up</span>
            </button>
          )}
        </div>
      )}

      <ExitConfirmationDialog isOpen={exitOpen} onConfirm={() => navigate('/student/lessons')} onCancel={() => setExitOpen(false)} />
    </div>
  );
};
