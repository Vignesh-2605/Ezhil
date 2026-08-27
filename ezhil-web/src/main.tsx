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
    navigator.serviceWorker
      .register('/sw.js')
      .catch(err => console.warn('[SW] registration failed:', err));
  });
}
