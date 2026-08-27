/**
 * Ezhil service worker — app-shell caching only.
 *
 * Data is NOT cached here. Dexie/IndexedDB is the offline store for students,
 * lessons, assessments and progress; SyncManager reconciles with the server.
 * Caching /api responses on top of that would give the UI two disagreeing
 * sources of truth, so API requests always go straight to the network.
 *
 * Strategy:
 *   navigation  → network-first, fall back to the cached shell when offline
 *   static asset → cache-first (Vite filenames are content-hashed, so a cached
 *                  entry is never stale — a new build requests a new URL)
 *   /api/*       → never touched
 */
const CACHE = 'ezhil-shell-v1';
const SHELL = '/index.html';

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE).then(c => c.addAll([SHELL, '/'])).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches
      .keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // Only same-origin GETs. Never the API.
  if (request.method !== 'GET') return;
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/')) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put(SHELL, copy));
          return res;
        })
        .catch(() => caches.match(SHELL).then(r => r ?? Response.error())),
    );
    return;
  }

  event.respondWith(
    caches.match(request).then(
      hit =>
        hit ??
        fetch(request).then(res => {
          // Opaque and error responses must not poison the cache.
          if (res.ok && res.type === 'basic') {
            const copy = res.clone();
            caches.open(CACHE).then(c => c.put(request, copy));
          }
          return res;
        }),
    ),
  );
});
