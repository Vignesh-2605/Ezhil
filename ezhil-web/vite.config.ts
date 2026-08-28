import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // GitHub Pages serves the project site from /<repo>/, not from the domain
  // root, so a bundle built with the default base asks for /assets/… and gets
  // a 404 for every script — which renders as a blank white page with no error
  // anyone would notice. Only the Pages build sets VITE_BASE; local dev, the
  // CI preview server and any root-hosted deploy stay on '/'.
  base: process.env.VITE_BASE || '/',
  server: {
    // Bind on all interfaces, not just loopback, so the app is reachable from
    // a phone on the same network or through a tunnel. Without this the dev
    // server only answers on localhost and every external request times out.
    host: true,
    // 3000 by default, but honour PORT so a second dev server can run
    // alongside a first rather than failing to bind.
    port: Number(process.env.PORT) || 3000,
    // Tunnel hostnames are generated per session and cannot be listed ahead of
    // time. Vite rejects unknown Host headers by default, which surfaces as a
    // blank page rather than an error, so allow them explicitly.
    allowedHosts: ['.trycloudflare.com', '.ngrok-free.app', '.ngrok.io', '.loca.lt'],
    proxy: {
      // The browser only ever calls same-origin /api, so tunnelling the web
      // app carries the backend with it — one public URL covers both, and no
      // CORS is involved.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
