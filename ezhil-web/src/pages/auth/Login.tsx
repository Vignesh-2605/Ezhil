import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { apiFetch } from '../../services/apiClient';
import { EzhilWordmark } from '../../components/brand/EzhilLogo';

type Tab = 'teacher' | 'student';

const LOADING_TEXTS = [
  { en: 'Logging in...', ta: 'உள்நுழைந்து கொண்டிருக்கிறது...' },
  { en: 'Fetching profile...', ta: 'சுயவிவரத்தைப் பெறுகிறது...' },
  { en: 'Syncing data...', ta: 'தரவை ஒத்திசைக்கிறது...' }
];

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const roleParam = searchParams.get('role');
  const { loginTeacher, loginStudent } = useAuth();
  const [tab, setTab] = useState<Tab>(() => {
    if (roleParam === 'student' || roleParam === 'teacher') {
      return roleParam;
    }
    return 'teacher';
  });
  const [loading, setLoading] = useState(false);
  // Bilingual: the banner shows an English line and a Tamil line, so both
  // must describe the same failure. A fixed Tamil string contradicted any
  // message that was not about bad credentials.
  const [error, setError] = useState<{ en: string; ta: string } | null>(null);
  const [loadingTextIdx, setLoadingTextIdx] = useState(0);

  /* Teacher fields */
  const [tSchool, setTSchool] = useState('');
  const [tCode, setTCode] = useState('');
  const [tPin, setTPin] = useState('');

  /* Student fields */
  const [sSchool, setSSchool] = useState('');
  const [sCode, setSCode] = useState('');
  const [sPin, setSPin] = useState('');

  // Handle rotating text in loading state
  useEffect(() => {
    let timer: any;
    if (loading) {
      timer = setInterval(() => {
        setLoadingTextIdx(prev => (prev + 1) % LOADING_TEXTS.length);
      }, 2500);
    } else {
      setLoadingTextIdx(0);
    }
    return () => clearInterval(timer);
  }, [loading]);

  /** Which fields are empty, named individually. The server answers every
   *  auth failure with one generic "Invalid credentials" by design, so a
   *  teacher who simply left a box blank must be told that here — otherwise
   *  a typo and an empty field look identical. */
  const emptyFields = (): string[] => {
    const missing: string[] = [];
    if (tab === 'teacher') {
      if (!tSchool.trim()) missing.push('School code');
      if (!tCode.trim())   missing.push('Teacher ID');
      if (!tPin.trim())    missing.push('PIN');
    } else {
      if (!sSchool.trim()) missing.push('School code');
      if (!sCode.trim())   missing.push('Your name');
      if (!sPin.trim())    missing.push('PIN');
    }
    return missing;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const missing = emptyFields();
    if (missing.length) {
      setError({
        en: missing.length === 1
          ? `${missing[0]} is required.`
          : `${missing.slice(0, -1).join(', ')} and ${missing[missing.length - 1]} are required.`,
        ta: missing.length === 1
          ? 'இந்த புலம் தேவை.'
          : 'அனைத்து புலங்களையும் நிரப்பவும்.',
      });
      return;
    }

    setLoading(true);
    try {
      if (tab === 'teacher') {
        const res = await apiFetch<{
          access_token: string;
          teacher_id: string;
          school_id: string;
          teacher_name: string;
          school_name: string;
          class_name: string;
          district: string;
          schoolCode: string;
          schoolName: string;
          teacherId: string;
          teacherName: string;
        }>('/api/v1/auth/login', {
          method: 'POST',
          body: JSON.stringify({ school_code: tSchool.trim(), teacher_id: tCode.trim(), pin: tPin }),
        });
        loginTeacher(
          res.access_token,
          res.teacherName,
          res.teacher_id,
          res.schoolCode,
          res.schoolName,
          res.teacherId,
          res.teacherName
        );
        navigate('/teacher/dashboard');
      } else {
        const res = await apiFetch<{
          access_token: string;
          student_id: string;
          student_name: string;
          school_name: string;
          teacher_name: string;
          class_name: string;
          risk_level: string;
          schoolCode: string;
          schoolName: string;
          teacherId: string;
          teacherName: string;
        }>('/api/v1/auth/student/login', {
          method: 'POST',
          body: JSON.stringify({ school_code: sSchool.trim(), student_code: sCode.trim().toUpperCase(), pin: sPin }),
        });
        loginStudent(
          res.access_token,
          res.student_name,
          res.student_id,
          res.student_id,
          res.schoolCode,
          res.schoolName,
          res.teacherId,
          res.teacherName
        );
        navigate('/student/home');
      }
    } catch (err: unknown) {
      const raw = err instanceof Error ? err.message : '';
      // The server answers every auth failure identically on purpose, so it
      // must not be shown verbatim — "Invalid credentials" tells a teacher
      // nothing about what to do next.
      const generic = !raw || /invalid credentials/i.test(raw);
      setError({
        en: generic
          ? tab === 'teacher'
            ? 'That school code, teacher ID or PIN is not correct. Check them and try again.'
            : 'That school code, name or PIN is not correct. Ask your teacher if you are unsure.'
          : raw,
        ta: generic
          ? 'பள்ளி குறியீடு, அடையாள எண் அல்லது கடவுச்சொல் தவறானது.'
          : raw,
      });
    } finally {
      setLoading(false);
    }
  };

  const hasError = !!error;

  return (
    <div className="min-h-dvh w-full flex items-center justify-center bg-surface-container-lowest text-on-surface select-none relative overflow-hidden font-body-tamil">
      {/* Visual Accent Glow Filters */}
      <div className="fixed top-0 left-0 w-80 h-80 bg-secondary/5 rounded-full blur-[120px] -translate-x-1/3 -translate-y-1/3 pointer-events-none" />
      <div className="fixed bottom-0 right-0 w-96 h-96 bg-primary-fixed/5 rounded-full blur-[120px] translate-x-1/3 translate-y-1/3 pointer-events-none" />

      {/* Main Container */}
      <main className="relative z-10 w-full max-w-[460px] px-md">
        {/* Branding Title */}
        <div className="mb-lg text-center animate-fade-in">
          <div className="flex justify-center">
            <EzhilWordmark stacked markSize={64} />
          </div>
          <p className="font-body-sm text-body-sm text-on-surface-variant mt-3">
            {tab === 'teacher' ? 'Teacher Portal • Dyslexia Support' : 'Student Panel • Phonics Learning'}
          </p>
        </div>

        {/* Login Card */}
        <div className={`glass-card r-hero surface-lit p-lg lg:p-xl relative overflow-hidden transition-all duration-300 ${
          hasError ? 'border-error-text/30 error-banner-glow animate-shake' : 'border-outline-variant/30'
        }`}>
          
          {/* 1. Inline Error Banner when error exists */}
          {hasError && !loading && (
            <div className="mb-lg bg-[#1A0000] border border-error-text/30 r-chip p-md flex items-start gap-md animate-fade-in">
              <span className="material-symbols-outlined text-error-text mt-0.5" style={{ fontVariationSettings: "'FILL' 1" }}>
                warning
              </span>
              <div className="flex-1">
                <p className="font-body-sm text-body-sm text-error-text leading-snug font-bold">
                  {error?.en}
                </p>
                <p className="font-tamil-body text-xs text-error-text mt-1 leading-snug">
                  {error?.ta}
                </p>
              </div>
            </div>
          )}

          {/* Login tab bar - Only visible in non-loading state and when no role parameter is present */}
          {!loading && !roleParam && (
            <div className="flex border-b border-outline-variant/20 mb-lg">
              {(['teacher', 'student'] as Tab[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => {
                    setTab(t);
                    setError(null);
                  }}
                  className={`flex-1 pb-3 font-bold text-xs uppercase tracking-wider transition-all duration-300 cursor-pointer ${
                    tab === t
                      ? 'text-primary-fixed border-b-2 border-primary-fixed'
                      : 'text-text-muted hover:text-white'
                  }`}
                >
                  {t === 'teacher' ? '🧑‍🏫 Teacher' : '👦 Student'}
                </button>
              ))}
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} noValidate className={`space-y-lg ${loading ? 'opacity-50 pointer-events-none' : ''}`}>
            {tab === 'teacher' ? (
              <>
                {/* School Code Input */}
                <div className="space-y-sm">
                  <div className="flex justify-between items-end px-xs">
                    <label className={`font-body-sm text-body-sm font-semibold ${hasError ? 'text-error-text' : 'text-on-surface'}`}>
                      School Code / <span className="font-tamil-body text-xs">பள்ளி குறியீடு</span>
                    </label>
                    {hasError && <span className="text-xs font-bold text-error-text uppercase font-mono">Error</span>}
                  </div>
                  <div className="relative">
                    <input
                      required
                      disabled={loading}
                      value={tSchool}
                      onChange={(e) => setTSchool(e.target.value)}
                      className={`w-full h-12 pl-12 pr-10 bg-surface-container-low border r-chip outline-none text-body-lg transition-all placeholder:text-on-surface-variant/40 ${
                        hasError 
                          ? 'border-error-text border-2 text-error-text focus:ring-2 focus:ring-error-text' 
                          : 'border-outline-variant/30 text-on-surface focus:ring-2 focus:ring-primary-fixed'
                      }`}
                      placeholder={hasError ? 'Enter school code' : 'e.g., SCH-001'}
                      type="text"
                    />
                    <span className={`absolute left-4 top-1/2 -translate-y-1/2 material-symbols-outlined ${hasError ? 'text-error-text' : 'text-on-surface-variant/40'}`}>
                      corporate_fare
                    </span>
                    {hasError && (
                      <span className="absolute right-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-error-text text-lg">
                        error
                      </span>
                    )}
                  </div>
                </div>

                {/* Teacher ID Input */}
                <div className="space-y-sm">
                  <div className="flex justify-between items-end px-xs">
                    <label className={`font-body-sm text-body-sm font-semibold ${hasError ? 'text-error-text' : 'text-on-surface'}`}>
                      Teacher ID / <span className="font-tamil-body text-xs">ஆசிரியர் குறியீடு</span>
                    </label>
                    {hasError && <span className="text-xs font-bold text-error-text uppercase font-mono">Check ID</span>}
                  </div>
                  <div className="relative">
                    <input
                      required
                      disabled={loading}
                      value={tCode}
                      onChange={(e) => setTCode(e.target.value)}
                      className={`w-full h-12 pl-12 pr-10 bg-surface-container-low border r-chip outline-none text-body-lg transition-all placeholder:text-on-surface-variant/40 ${
                        hasError 
                          ? 'border-error-text border-2 text-error-text focus:ring-2 focus:ring-error-text' 
                          : 'border-outline-variant/30 text-on-surface focus:ring-2 focus:ring-primary-fixed'
                      }`}
                      placeholder={hasError ? 'Enter teacher ID' : 'e.g., 1001'}
                      type="text"
                    />
                    <span className={`absolute left-4 top-1/2 -translate-y-1/2 material-symbols-outlined ${hasError ? 'text-error-text' : 'text-on-surface-variant/40'}`}>
                      badge
                    </span>
                    {hasError && (
                      <span className="absolute right-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-error-text text-lg">
                        error
                      </span>
                    )}
                  </div>
                </div>

                {/* PIN Input */}
                <div className="space-y-sm">
                  <div className="flex justify-between items-end px-xs">
                    <label className={`font-body-sm text-body-sm font-semibold ${hasError ? 'text-error-text' : 'text-on-surface'}`}>
                      PIN / <span className="font-tamil-body text-xs">கடவுச்சொல்</span>
                    </label>
                  </div>
                  <div className="relative">
                    <input
                      required
                      disabled={loading}
                      value={tPin}
                      onChange={(e) => setTPin(e.target.value)}
                      className={`w-full h-12 pl-12 pr-4 bg-surface-container-low border r-chip outline-none text-body-lg transition-all placeholder:text-on-surface-variant/40 ${
                        hasError 
                          ? 'border-error-text border-2 text-error-text focus:ring-2 focus:ring-error-text' 
                          : 'border-outline-variant/30 text-on-surface focus:ring-2 focus:ring-primary-fixed'
                      }`}
                      placeholder="e.g., 1234"
                      type="password"
                      maxLength={6}
                    />
                    <span className={`absolute left-4 top-1/2 -translate-y-1/2 material-symbols-outlined ${hasError ? 'text-error-text' : 'text-on-surface-variant/40'}`}>
                      lock
                    </span>
                  </div>
                </div>
              </>
            ) : (
              <>
                {/* Student School Code Input */}
                <div className="space-y-sm">
                  <div className="flex justify-between items-end px-xs">
                    <label className={`font-body-sm text-body-sm font-semibold ${hasError ? 'text-error-text' : 'text-on-surface'}`}>
                      School Code / <span className="font-tamil-body text-xs">பள்ளி குறியீடு</span>
                    </label>
                  </div>
                  <div className="relative">
                    <input
                      required
                      disabled={loading}
                      value={sSchool}
                      onChange={(e) => setSSchool(e.target.value)}
                      className={`w-full h-12 pl-12 pr-10 bg-surface-container-low border r-chip outline-none text-body-lg transition-all placeholder:text-on-surface-variant/40 ${
                        hasError 
                          ? 'border-error-text border-2 text-error-text focus:ring-2 focus:ring-error-text' 
                          : 'border-outline-variant/30 text-on-surface focus:ring-2 focus:ring-primary-fixed'
                      }`}
                      placeholder={hasError ? 'Enter school code' : 'e.g., SCH-001'}
                      type="text"
                    />
                    <span className={`absolute left-4 top-1/2 -translate-y-1/2 material-symbols-outlined ${hasError ? 'text-error-text' : 'text-on-surface-variant/40'}`}>
                      corporate_fare
                    </span>
                  </div>
                </div>

                {/* Student Name Input */}
                <div className="space-y-sm">
                  <div className="flex justify-between items-end px-xs">
                    <label className={`font-body-sm text-body-sm font-semibold ${hasError ? 'text-error-text' : 'text-on-surface'}`}>
                      Student Name / <span className="font-tamil-body text-xs">மாணவர் பெயர்</span>
                    </label>
                  </div>
                  <div className="relative">
                    <input
                      required
                      disabled={loading}
                      value={sCode}
                      onChange={(e) => setSCode(e.target.value.toUpperCase())}
                      className={`w-full h-12 pl-12 pr-10 bg-surface-container-low border r-chip outline-none text-body-lg transition-all placeholder:text-on-surface-variant/40 ${
                        hasError 
                          ? 'border-error-text border-2 text-error-text focus:ring-2 focus:ring-error-text' 
                          : 'border-outline-variant/30 text-on-surface focus:ring-2 focus:ring-primary-fixed'
                      }`}
                      placeholder={hasError ? 'Enter student name' : 'e.g., KAVIN'}
                      type="text"
                      style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}
                    />
                    <span className={`absolute left-4 top-1/2 -translate-y-1/2 material-symbols-outlined ${hasError ? 'text-error-text' : 'text-on-surface-variant/40'}`}>
                      person
                    </span>
                  </div>
                </div>

                {/* Student PIN Input */}
                <div className="space-y-sm">
                  <div className="flex justify-between items-end px-xs">
                    <label className={`font-body-sm text-body-sm font-semibold ${hasError ? 'text-error-text' : 'text-on-surface'}`}>
                      PIN / <span className="font-tamil-body text-xs">கடவுச்சொல்</span>
                    </label>
                  </div>
                  <div className="relative">
                    <input
                      required
                      disabled={loading}
                      value={sPin}
                      onChange={(e) => setSPin(e.target.value)}
                      className={`w-full h-12 pl-12 pr-4 bg-surface-container-low border r-chip outline-none text-body-lg transition-all placeholder:text-on-surface-variant/40 ${
                        hasError 
                          ? 'border-error-text border-2 text-error-text focus:ring-2 focus:ring-error-text' 
                          : 'border-outline-variant/30 text-on-surface focus:ring-2 focus:ring-primary-fixed'
                      }`}
                      placeholder="e.g., 0512"
                      type="password"
                      maxLength={4}
                    />
                    <span className={`absolute left-4 top-1/2 -translate-y-1/2 material-symbols-outlined ${hasError ? 'text-error-text' : 'text-on-surface-variant/40'}`}>
                      lock
                    </span>
                  </div>
                </div>
              </>
            )}

            {/* Actions button */}
            {!loading && (
              <div className="pt-md">
                <button
                  type="submit"
                  className="w-full bg-secondary-container hover:bg-secondary-container/95 text-white font-headline-sm text-headline-sm py-md r-chip transition-transform active:scale-[0.98] shadow-lg flex items-center justify-center gap-sm cursor-pointer"
                >
                  Sign In / <span className="font-tamil-body text-base">உள்நுழைய</span>
                </button>
              </div>
            )}
          </form>

          {/* 2. Loading State Overlay inside card - Activates when loading is true */}
          {loading && (
            <div className="absolute inset-0 bg-surface-container-lowest/80 flex flex-col justify-center items-center p-xl gap-lg animate-fade-in">
              <svg className="w-12 h-12 text-primary-fixed animate-spin" viewBox="0 0 50 50">
                <circle
                  className="opacity-25"
                  cx="25"
                  cy="25"
                  r="20"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="5"
                />
                {/* Drawn as a dashed arc on the same circle rather than path
                    data. The previous path contained "V4y20", and `y` is not an
                    SVG command — the browser rejected the whole `d` and the
                    spinner rendered as a static ring with no moving part. */}
                <circle
                  className="opacity-75"
                  cx="25"
                  cy="25"
                  r="20"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="5"
                  strokeLinecap="round"
                  strokeDasharray="31.4 125.7"
                />
              </svg>
              <div className="flex flex-col items-center text-center gap-xs">
                <p className="text-white font-bold text-lg leading-tight transition-all duration-300">
                  {LOADING_TEXTS[loadingTextIdx].en}
                </p>
                <p className="text-on-surface-variant font-tamil-body text-xs transition-all duration-300">
                  {LOADING_TEXTS[loadingTextIdx].ta}
                </p>
              </div>
            </div>
          )}

          {/* Footer state indicator - only when not loading */}
          {!loading && (
            <div className="flex items-center justify-between pt-lg border-t border-outline-variant/10 mt-md">
              <a className="font-caption text-caption text-secondary hover:underline cursor-pointer" onClick={() => navigate('/role-selection')}>
                Forgot ID?
              </a>
              <div className="flex items-center gap-xs">
                <span className="w-2 h-2 rounded-full bg-success animate-pulse"></span>
                <span className="font-caption text-caption text-on-surface-variant">System Ready</span>
              </div>
            </div>
          )}
        </div>

        {/* Security badges grayscaled footer - Only when not loading */}
        {!loading && (
          <div className="mt-xl grid grid-cols-2 gap-md opacity-50 hover:opacity-100 transition-all duration-500">
            <div className="flex items-center gap-md p-md border border-outline-variant/20 r-chip bg-surface-container-low/50">
              <span className="material-symbols-outlined text-secondary">security</span>
              <div>
                <p className="font-caption text-caption text-white font-bold">Secure Access</p>
                <p className="text-xs text-text-muted">256-bit encryption</p>
              </div>
            </div>
            <div className="flex items-center gap-md p-md border border-outline-variant/20 r-chip bg-surface-container-low/50">
              <span className="material-symbols-outlined text-secondary">language</span>
              <div>
                <p className="font-caption text-caption text-white font-bold">Multi-language</p>
                <p className="text-xs text-text-muted">Tamil/English</p>
              </div>
            </div>
          </div>
        )}

        {/* Demo Hint block for helper logins */}
        {!loading && (
          <div className="mt-md p-md bg-white/5 r-card border border-white/10 text-xs text-text-muted font-mono space-y-1">
            <p className="font-semibold text-white/50 mb-0.5">Demo credentials:</p>
            <p>🧑‍🏫 School Code: SCH-001 · Teacher: 1001 · PIN: 1234</p>
            <p>👦 School Code: SCH-001 · Student: KAVIN · PIN: 0512</p>
          </div>
        )}
      </main>
    </div>
  );
};
