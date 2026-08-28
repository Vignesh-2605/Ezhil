import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Offline app shell. Production only — in dev the SW would serve a stale
// bundle and shadow Vite's HMR. Registration failure is non-fatal: the app
// still works online, it just will not launch without a connection.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    // Base-relative, not root-relative: on GitHub Pages the app lives at
    // /<repo>/, where a request for /sw.js is a 404 and registration fails.
    // The scope must be the base too, or the worker may not control the app.
    const base = import.meta.env.BASE_URL;
    navigator.serviceWorker
      .register(`${base}sw.js`, { scope: base })
      .catch(err => console.warn('[SW] registration failed:', err));
  });
}
