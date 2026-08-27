import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// apiClient touches window.location and localStorage — provide minimal stubs
// so the 401 policy can be tested in a plain node environment.
const store = new Map<string, string>();
const localStorageStub = {
  getItem: (k: string) => store.get(k) ?? null,
  setItem: (k: string, v: string) => void store.set(k, v),
  removeItem: (k: string) => void store.delete(k),
};
const windowStub = { location: { href: '/original' } };

vi.stubGlobal('localStorage', localStorageStub);
vi.stubGlobal('window', windowStub);

const { apiFetch } = await import('../apiClient');

function mockFetch(status: number, body: unknown = {}) {
  const fn = vi.fn().mockResolvedValue({
    status,
    ok: status >= 200 && status < 300,
    json: async () => body,
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

beforeEach(() => {
  store.clear();
  store.set('ezhil_session', JSON.stringify({ accessToken: 'tok-123' }));
  windowStub.location.href = '/original';
});

afterEach(() => vi.restoreAllMocks());

describe('apiFetch', () => {
  it('attaches the bearer token from the stored session', async () => {
    const fetchMock = mockFetch(200, { ok: true });
    await apiFetch('/api/v1/sync/pull');
    const headers = fetchMock.mock.calls[0][1].headers;
    expect(headers.Authorization).toBe('Bearer tok-123');
  });

  it('401 on a background call must NOT wipe the session or redirect', async () => {
    // The original bug: a background sync 401 logged a child out mid-lesson.
    mockFetch(401, { detail: 'Unauthorized' });
    await expect(apiFetch('/api/v1/sync/push', { background: true })).rejects.toThrow();
    expect(store.has('ezhil_session')).toBe(true);
    expect(windowStub.location.href).toBe('/original');
  });

  it('401 on a login attempt surfaces the error inline without redirecting', async () => {
    mockFetch(401, { detail: 'Invalid credentials' });
    await expect(apiFetch('/api/v1/auth/login', { method: 'POST' }))
      .rejects.toThrow('Invalid credentials');
    expect(store.has('ezhil_session')).toBe(true);
    expect(windowStub.location.href).toBe('/original');
  });

  it('401 on a foreground non-auth call ends the session and redirects to login', async () => {
    mockFetch(401, { detail: 'Token expired' });
    await expect(apiFetch('/api/v1/lessons')).rejects.toThrow();
    expect(store.has('ezhil_session')).toBe(false);
    expect(windowStub.location.href).toBe('/login');
  });

  it('non-401 errors throw the server detail message', async () => {
    mockFetch(400, { detail: 'Unknown table' });
    await expect(apiFetch('/api/v1/sync/push')).rejects.toThrow('Unknown table');
  });
});
