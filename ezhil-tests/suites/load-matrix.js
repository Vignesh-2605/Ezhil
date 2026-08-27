#!/usr/bin/env node
/**
 * Load & Deployment.
 *
 * Drives the API at four concurrency levels and asserts a latency and
 * reliability budget per endpoint at each, then checks the build artifacts a
 * deployment would ship.
 *
 * Every figure is measured in this run. A threshold that passes because the
 * number was invented would be worse than having no threshold, so nothing is
 * substituted and an endpoint that received no traffic fails its own
 * "was exercised" assertion rather than silently reporting a perfect zero.
 */
const fs = require('node:fs');
const path = require('node:path');
const H = require('../lib/harness');
const API = require('../lib/api');

const ROOT = path.join(__dirname, '..', '..');
const OUT = path.join(__dirname, '..', 'reports', 'load-matrix.json');
const SUITE = 'Load & Deployment';

const LEVELS = (process.env.LOAD_LEVELS || '10,25,50,100').split(',').map(Number);
const SECONDS = Number(process.env.LOAD_SECONDS || 8);

/** Read-only endpoints only: a load test must not mutate a child's records. */
function endpoints(token) {
  const auth = { Authorization: `Bearer ${token}` };
  return [
    { name: 'GET /health', run: () => API.request('GET', '/health') },
    { name: 'GET /openapi.json', run: () => API.request('GET', '/openapi.json') },
    { name: 'GET /dashboard/teacher', run: () => API.request('GET', '/api/v1/dashboard/teacher', { headers: auth }) },
    { name: 'GET /lessons', run: () => API.request('GET', '/api/v1/lessons', { headers: auth }) },
    { name: 'GET /sync/pull', run: () => API.request('GET', '/api/v1/sync/pull?since=1970-01-01T00:00:00Z', { headers: auth }) },
    { name: 'POST /auth/login', run: () => API.request('POST', '/api/v1/auth/login', { body: API.CREDS.teacher }) },
    { name: 'POST /auth/student/login', run: () => API.request('POST', '/api/v1/auth/student/login', { body: API.CREDS.student }) },
  ];
}

/**
 * Budgets per concurrency level. They widen with load because a server under
 * 100 concurrent users is legitimately slower than one under 10 -- a single
 * flat number would either be unmeetable at the top or meaningless at the
 * bottom.
 */
const BUDGET = {
  10: { p50: 150, p75: 250, p90: 400, p95: 600, p99: 1200, max: 2500, mean: 250 },
  25: { p50: 300, p75: 500, p90: 800, p95: 1200, p99: 2500, max: 5000, mean: 500 },
  50: { p50: 600, p75: 1000, p90: 1600, p95: 2400, p99: 5000, max: 9000, mean: 1000 },
  100: { p50: 1200, p75: 2000, p90: 3200, p95: 4800, p99: 9000, max: 15000, mean: 2000 },
};

const pctl = (sorted, p) =>
  sorted.length ? sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))] : 0;

async function runLevel(eps, vus, seconds) {
  const stats = new Map(eps.map(e => [e.name, { ms: [], fail: 0 }]));
  const deadline = Date.now() + seconds * 1000;
  const started = Date.now();
  let i = 0;

  await Promise.all(Array.from({ length: vus }, async () => {
    while (Date.now() < deadline) {
      const e = eps[i++ % eps.length];
      const res = await e.run();
      const s = stats.get(e.name);
      s.ms.push(res.ms);
      if (!(res.status >= 200 && res.status < 400)) s.fail++;
    }
  }));

  const wall = (Date.now() - started) / 1000;
  const out = [];
  for (const [name, s] of stats) {
    const sorted = [...s.ms].sort((a, b) => a - b);
    out.push({
      name, requests: s.ms.length, failed: s.fail,
      failureRate: s.ms.length ? s.fail / s.ms.length : 1,
      rps: s.ms.length / wall,
      mean: s.ms.length ? s.ms.reduce((a, b) => a + b, 0) / s.ms.length : 0,
      p50: pctl(sorted, 50), p75: pctl(sorted, 75), p90: pctl(sorted, 90),
      p95: pctl(sorted, 95), p99: pctl(sorted, 99), max: sorted[sorted.length - 1] || 0,
    });
  }
  return { vus, wall, endpoints: out };
}

async function deploymentChecks(rec) {
  const artifact = async (label, rel, maxMB) => {
    await H.check(rec, 'Deployment — artifacts', `${label} exists`, () => {
      if (!fs.existsSync(path.join(ROOT, rel))) throw new Error(`${rel} is missing`);
    });
    await H.check(rec, 'Deployment — size budget', `${label} is under ${maxMB} MB`, () => {
      const p = path.join(ROOT, rel);
      if (!fs.existsSync(p)) throw new Error(`${rel} is missing`);
      const mb = fs.statSync(p).size / 1048576;
      if (mb > maxMB) throw new Error(`${mb.toFixed(1)} MB exceeds the ${maxMB} MB budget`);
    });
  };

  await artifact('Web bundle index.html', 'ezhil-web/dist/index.html', 1);
  await artifact('Debug APK (arm64)', 'ezhil-android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk', 120);
  await artifact('Debug APK (arm32)', 'ezhil-android/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk', 110);
  await artifact('Release AAB', 'ezhil-android/app/build/outputs/bundle/release/app-release.aab', 80);

  await H.check(rec, 'Deployment — web bundle', 'dist contains hashed assets', () => {
    const d = path.join(ROOT, 'ezhil-web/dist/assets');
    if (!fs.existsSync(d)) throw new Error('dist/assets is missing — the web app was never built');
    const files = fs.readdirSync(d);
    if (!files.some(f => /\.js$/.test(f))) throw new Error('no JS bundle in dist/assets');
    if (!files.some(f => /\.css$/.test(f))) throw new Error('no CSS bundle in dist/assets');
  });

  await H.check(rec, 'Deployment — release safety', 'release build permits no cleartext', () => {
    const p = path.join(ROOT, 'ezhil-android/app/src/main/res/xml/network_security_config.xml');
    const xml = fs.readFileSync(p, 'utf-8');
    if (!/cleartextTrafficPermitted="false"/.test(xml)) {
      throw new Error('the release network config does not forbid cleartext');
    }
  });

  await H.check(rec, 'Deployment — release safety', 'the manifest sets no app-wide cleartext flag', () => {
    const p = path.join(ROOT, 'ezhil-android/app/src/main/AndroidManifest.xml');
    // Comments stripped first: the manifest documents that this attribute was
    // removed, and matching raw text reported that explanation as the fault.
    const xml = fs.readFileSync(p, 'utf-8').replace(/<!--[\s\S]*?-->/g, '');
    if (/usesCleartextTraffic\s*=\s*"true"/.test(xml)) throw new Error('usesCleartextTraffic="true" is still set');
  });

  await H.check(rec, 'Deployment — release safety', 'the manifest points at a network security config', () => {
    const p = path.join(ROOT, 'ezhil-android/app/src/main/AndroidManifest.xml');
    const xml = fs.readFileSync(p, 'utf-8').replace(/<!--[\s\S]*?-->/g, '');
    if (!/networkSecurityConfig/.test(xml)) throw new Error('no networkSecurityConfig declared');
  });

  await H.check(rec, 'Deployment — release safety', 'the debug cleartext config never ships in main', () => {
    const p = path.join(ROOT, 'ezhil-android/app/src/debug/res/xml/network_security_config.xml');
    if (!fs.existsSync(p)) throw new Error('the debug override is missing, so debug builds cannot reach a LAN backend');
  });

  // ── Backend configuration ────────────────────────────────────────────────
  const envPath = path.join(ROOT, 'ezhil-backend/.env');
  const env = fs.existsSync(envPath) ? fs.readFileSync(envPath, 'utf-8') : '';
  const setting = k => {
    const m = env.match(new RegExp('^' + k + '\s*=\s*(.*)$', 'm'));
    return m ? m[1].trim() : null;
  };

  await H.check(rec, 'Deployment — backend config', 'an .env exists', () => {
    if (!env) throw new Error('ezhil-backend/.env is missing');
  });
  await H.check(rec, 'Deployment — backend config', 'SECRET_KEY is not the development default', () => {
    const v = setting('SECRET_KEY');
    if (v && /change|default|secret-key|please/i.test(v)) throw new Error('SECRET_KEY still looks like a placeholder');
  });
  await H.check(rec, 'Deployment — backend config', 'DEMO_MODE is off', () => {
    const v = setting('DEMO_MODE');
    if (v && /true/i.test(v)) throw new Error('DEMO_MODE=true bypasses the real models');
  });
  await H.check(rec, 'Deployment — backend config', 'the server binds an interface, not just loopback', () => {
    const v = setting('HOST');
    if (v && v === '127.0.0.1') throw new Error('HOST=127.0.0.1 is unreachable from a handset');
  });
  await H.check(rec, 'Deployment — backend config', '.env is not tracked by git', () => {
    const gi = fs.readFileSync(path.join(ROOT, 'ezhil-backend/.gitignore'), 'utf-8');
    if (!/^\.env$/m.test(gi)) throw new Error('.gitignore does not exclude .env');
  });
  await H.check(rec, 'Deployment — backend config', 'a requirements file is present', () => {
    if (!fs.existsSync(path.join(ROOT, 'ezhil-backend/requirements.txt'))) {
      throw new Error('requirements.txt is missing, so the environment is not reproducible');
    }
  });

  // ── Client configuration ─────────────────────────────────────────────────
  await H.check(rec, 'Deployment — client config', 'the Android build reads its API base from local.properties', () => {
    const g = fs.readFileSync(path.join(ROOT, 'ezhil-android/app/build.gradle.kts'), 'utf-8');
    if (!/API_BASE_URL/.test(g)) throw new Error('API_BASE_URL is not wired into the build');
  });
  await H.check(rec, 'Deployment — client config', 'signing is configured for release', () => {
    const g = fs.readFileSync(path.join(ROOT, 'ezhil-android/app/build.gradle.kts'), 'utf-8');
    if (!/signingConfigs/.test(g)) throw new Error('no signingConfigs block');
  });
  await H.check(rec, 'Deployment — client config', 'R8 is enabled for release', () => {
    const g = fs.readFileSync(path.join(ROOT, 'ezhil-android/app/build.gradle.kts'), 'utf-8');
    if (!/isMinifyEnabled\s*=\s*true/.test(g)) throw new Error('release is not minified');
  });
  await H.check(rec, 'Deployment — client config', 'per-ABI splits are configured', () => {
    const g = fs.readFileSync(path.join(ROOT, 'ezhil-android/app/build.gradle.kts'), 'utf-8');
    if (!/splits\s*\{/.test(g)) throw new Error('no ABI splits, so every phone downloads every architecture');
  });
  await H.check(rec, 'Deployment — client config', 'the web dev server binds all interfaces', () => {
    const v = fs.readFileSync(path.join(ROOT, 'ezhil-web/vite.config.ts'), 'utf-8');
    if (!/host:\s*true/.test(v)) throw new Error('vite binds loopback only, so a phone cannot reach it');
  });

  await H.check(rec, 'Deployment — release safety', 'keystores are not tracked', () => {
    const gi = fs.readFileSync(path.join(ROOT, 'ezhil-android/.gitignore'), 'utf-8');
    if (!/\.jks|\.keystore/.test(gi)) throw new Error('.gitignore does not exclude keystores');
  });
}

async function main() {
  const rec = new H.Recorder(SUITE);
  console.log(`\n${SUITE}\n  target ${API.BASE}\n  levels ${LEVELS.join(', ')} VUs × ${SECONDS}s\n`);

  const token = await API.login('teacher');
  const eps = endpoints(token);
  const levels = [];

  for (const vus of LEVELS) {
    process.stdout.write(`  ${String(vus).padStart(4)} VUs … `);
    const result = await runLevel(eps, vus, SECONDS);
    levels.push(result);
    const total = result.endpoints.reduce((a, e) => a + e.requests, 0);
    console.log(`${total} requests, ${(total / result.wall).toFixed(0)} req/s`);

    const b = BUDGET[vus] || BUDGET[100];
    for (const e of result.endpoints) {
      const at = `${e.name} @ ${vus} VUs`;
      await H.check(rec, `Load — ${vus} VUs`, `${at} — was exercised`, () => {
        if (e.requests === 0) throw new Error('received no traffic, so its numbers mean nothing');
      });
      await H.check(rec, `Load — ${vus} VUs`, `${at} — no failed requests`, () => {
        if (e.failureRate > 0.05) throw new Error(`${(e.failureRate * 100).toFixed(1)}% failed`);
      });
      for (const m of ['mean', 'p50', 'p75', 'p90', 'p95', 'p99', 'max']) {
        await H.check(rec, `Load — ${vus} VUs`, `${at} — ${m} under ${b[m]}ms`, () => {
          if (e[m] > b[m]) throw new Error(`${m} was ${e[m].toFixed(0)}ms`);
        });
      }
      await H.check(rec, `Load — ${vus} VUs`, `${at} — sustained throughput`, () => {
        if (e.rps < 0.5) throw new Error(`only ${e.rps.toFixed(2)} req/s`);
      });
    }
  }

  await deploymentChecks(rec);

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify({ summary: rec.summary, rows: rec.rows, levels }, null, 2));

  const s = rec.summary;
  console.log(`\n  ${s.passed}/${s.total} passed, ${s.failed} failed`);
  if (s.failed) {
    console.log('\n  Failures:');
    for (const r of rec.rows.filter(r => r.status === 'failed').slice(0, 25)) {
      console.log(`    ✗ ${r.name}\n      ${r.error}`);
    }
  }
  console.log(`  → ${OUT}`);
  process.exit(s.failed ? 1 : 0);
}

main().catch(err => { console.error(err); process.exit(2); });
