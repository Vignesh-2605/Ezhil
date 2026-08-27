const BASE = import.meta.env.VITE_API_URL || '';
const SESSION_KEY = 'ezhil_session';

interface ApiOptions extends RequestInit {
  /** Background calls (sync, prefetch) must never wipe the session or
   *  hard-redirect on 401 — a child mid-lesson would be kicked out. */
  background?: boolean;
}

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { background, ...init } = options;
  const stored = localStorage.getItem(SESSION_KEY);
  const accessToken = stored ? (JSON.parse(stored) as { accessToken?: string }).accessToken : null;

  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(init.headers || {}),
    },
  });

  if (res.status === 401) {
    // A 401 from a login/register attempt is a form error, not an expired
    // session — let the caller display it instead of hard-redirecting.
    const isAuthCall = path.includes('/auth/');
    if (!background && !isAuthCall) {
      localStorage.removeItem(SESSION_KEY);
      window.location.href = '/login';
    }
    const err = await res.json().catch(() => ({ detail: 'Invalid credentials' }));
    throw new Error((err as { detail?: string }).detail || 'Invalid credentials');
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error((err as { detail?: string }).detail || 'Request failed');
  }

  return res.json() as Promise<T>;
}
