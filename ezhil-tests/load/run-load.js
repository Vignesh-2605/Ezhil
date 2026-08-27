#!/usr/bin/env node
/**
 * Load test for the Ezhil API — 100 concurrent users for 1 minute by default.
 *
 * Plain node:http with keep-alive, no dependencies, so it runs on any machine
 * that can run the backend. CI uses load/k6-load.js instead; both drive the
 * same endpoint mix and report the same metrics.
 *
 * Every figure printed here is measured. Nothing is substituted when a number
 * comes out small or inconvenient — a request that takes 0.4 ms is recorded as
 * 0.4 ms, because a load report whose durations were invented tells you
 * nothing about the server.
 */
const http = require('node:http');
const { URL } = require('node:url');
const fs = require('node:fs');
const path = require('node:path');

const BASE_URL = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');
const VUS = Number(process.env.VUS || 100);
const DURATION_S = Number(process.env.DURATION_S || 60);
const OUT = process.env.LOAD_OUT || path.join(__dirname, '..', 'reports', 'load-summary.json');

// Thresholds the run is judged against.
const MAX_FAIL_RATE = Number(process.env.MAX_FAIL_RATE || 0.05);
const MAX_P95_MS = Number(process.env.MAX_P95_MS || 1500);

const CREDS = {
  school_code: process.env.TEST_SCHOOL || 'SCH-001',
  teacher_id: process.env.TEST_TEACHER || '1001',
  pin: process.env.TEST_PIN || '1234',
};

const agent = new http.Agent({ keepAlive: true, maxSockets: VUS * 2 });

function request(method, urlStr, { headers = {}, body = null } = {}) {
  return new Promise(resolve => {
    const u = new URL(urlStr);
    const payload = body == null ? null : Buffer.from(JSON.stringify(body));
    const started = process.hrtime.bigint();
    const req = http.request(
      {
        agent,
        method,
        hostname: u.hostname,
        port: u.port || 80,
        path: u.pathname + u.search,
        headers: {
          ...headers,
          ...(payload ? { 'Content-Type': 'application/json', 'Content-Length': payload.length } : {}),
        },
        timeout: 30_000,
      },
      res => {
        const chunks = [];
        res.on('data', c => chunks.push(c));
        res.on('end', () => {
          const ms = Number(process.hrtime.bigint() - started) / 1e6;
          resolve({ status: res.statusCode, ms, body: Buffer.concat(chunks).toString('utf8') });
        });
      },
    );
    req.on('timeout', () => { req.destroy(new Error('timeout')); });
    req.on('error', err => {
      const ms = Number(process.hrtime.bigint() - started) / 1e6;
      resolve({ status: 0, ms, error: err.message, body: '' });
    });
    if (payload) req.write(payload);
    req.end();
  });
}

/**
 * What a virtual user does, weighted the way a classroom would use it: the
 * dashboard and lesson list are polled far more often than anyone logs in.
 */
function scenarios(token) {
  const auth = { Authorization: `Bearer ${token}` };
  return [
    { name: 'GET /health', weight: 20, run: () => request('GET', `${BASE_URL}/health`) },
    { name: 'GET /dashboard/teacher', weight: 30, run: () => request('GET', `${BASE_URL}/api/v1/dashboard/teacher`, { headers: auth }) },
    { name: 'GET /lessons', weight: 30, run: () => request('GET', `${BASE_URL}/api/v1/lessons`, { headers: auth }) },
    { name: 'GET /sync/pull', weight: 15, run: () => request('GET', `${BASE_URL}/api/v1/sync/pull?since=1970-01-01T00:00:00Z`, { headers: auth }) },
    { name: 'POST /auth/login', weight: 5, run: () => request('POST', `${BASE_URL}/api/v1/auth/login`, { body: CREDS }) },
  ];
}

function pick(list, total) {
  let r = Math.random() * total;
  for (const s of list) { r -= s.weight; if (r <= 0) return s; }
  return list[list.length - 1];
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

async function main() {
  process.stdout.write(`Ezhil API load test\n  target   ${BASE_URL}\n  users    ${VUS}\n  duration ${DURATION_S}s\n\n`);

  const health = await request('GET', `${BASE_URL}/health`);
  if (health.status !== 200) {
    console.error(`Backend is not answering on ${BASE_URL}/health (status ${health.status}${health.error ? ', ' + health.error : ''}).`);
    console.error('Start it with: cd ezhil-backend && .venv/Scripts/python.exe main.py');
    process.exit(2);
  }

  const login = await request('POST', `${BASE_URL}/api/v1/auth/login`, { body: CREDS });
  if (login.status !== 200) {
    console.error(`Login failed (status ${login.status}): ${login.body.slice(0, 200)}`);
    console.error(`Check TEST_SCHOOL/TEST_TEACHER/TEST_PIN — currently ${CREDS.school_code}/${CREDS.teacher_id}.`);
    process.exit(2);
  }
  const token = JSON.parse(login.body).access_token;

  const mix = scenarios(token);
  const totalWeight = mix.reduce((a, s) => a + s.weight, 0);
  const perScenario = new Map(mix.map(s => [s.name, { n: 0, fail: 0, ms: [] }]));
  const all = [];
  let failures = 0;
  let done = false;

  const startedAt = Date.now();
  const deadline = startedAt + DURATION_S * 1000;

  const ticker = setInterval(() => {
    const elapsed = (Date.now() - startedAt) / 1000;
    if (elapsed <= DURATION_S) {
      process.stdout.write(`\r  ${elapsed.toFixed(0).padStart(3)}s  ${all.length} requests  ${failures} failed   `);
    }
  }, 2000);

  async function virtualUser() {
    while (!done && Date.now() < deadline) {
      const s = pick(mix, totalWeight);
      const res = await s.run();
      const ok = res.status >= 200 && res.status < 400;
      const rec = perScenario.get(s.name);
      rec.n++;
      rec.ms.push(res.ms);
      if (!ok) { rec.fail++; failures++; }
      all.push(res.ms);
    }
  }

  await Promise.all(Array.from({ length: VUS }, virtualUser));
  done = true;
  clearInterval(ticker);

  const wallSeconds = (Date.now() - startedAt) / 1000;
  const sorted = [...all].sort((a, b) => a - b);
  const sum = all.reduce((a, b) => a + b, 0);

  const summary = {
    generatedAt: new Date().toISOString(),
    target: BASE_URL,
    virtualUsers: VUS,
    durationSeconds: Number(wallSeconds.toFixed(2)),
    totalRequests: all.length,
    failedRequests: failures,
    failureRate: all.length ? failures / all.length : 0,
    requestsPerSecond: all.length / wallSeconds,
    latencyMs: {
      min: sorted[0] ?? 0,
      avg: all.length ? sum / all.length : 0,
      p50: percentile(sorted, 50),
      p90: percentile(sorted, 90),
      p95: percentile(sorted, 95),
      p99: percentile(sorted, 99),
      max: sorted[sorted.length - 1] ?? 0,
    },
    thresholds: {
      failureRate: { limit: MAX_FAIL_RATE, actual: all.length ? failures / all.length : 0 },
      p95Ms: { limit: MAX_P95_MS, actual: percentile(sorted, 95) },
    },
    byEndpoint: [...perScenario.entries()].map(([name, r]) => {
      const s2 = [...r.ms].sort((a, b) => a - b);
      return {
        name,
        requests: r.n,
        failed: r.fail,
        avgMs: r.n ? r.ms.reduce((a, b) => a + b, 0) / r.n : 0,
        p95Ms: percentile(s2, 95),
        maxMs: s2[s2.length - 1] ?? 0,
      };
    }).sort((a, b) => b.requests - a.requests),
  };

  summary.thresholds.failureRate.passed = summary.failureRate < MAX_FAIL_RATE;
  summary.thresholds.p95Ms.passed = summary.latencyMs.p95 < MAX_P95_MS;
  summary.passed = summary.thresholds.failureRate.passed && summary.thresholds.p95Ms.passed;

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify(summary, null, 2));

  const f = n => n.toFixed(1).padStart(9);
  process.stdout.write('\r' + ' '.repeat(60) + '\r');
  console.log('─'.repeat(64));
  console.log(`  Requests        ${summary.totalRequests}  (${summary.failedRequests} failed, ${(summary.failureRate * 100).toFixed(2)}%)`);
  console.log(`  Throughput      ${summary.requestsPerSecond.toFixed(1)} req/sec over ${summary.durationSeconds}s`);
  console.log('─'.repeat(64));
  console.log(`  Latency    min ${f(summary.latencyMs.min)} ms`);
  console.log(`             avg ${f(summary.latencyMs.avg)} ms`);
  console.log(`             p50 ${f(summary.latencyMs.p50)} ms`);
  console.log(`             p95 ${f(summary.latencyMs.p95)} ms`);
  console.log(`             p99 ${f(summary.latencyMs.p99)} ms`);
  console.log(`             max ${f(summary.latencyMs.max)} ms`);
  console.log('─'.repeat(64));
  for (const e of summary.byEndpoint) {
    console.log(`  ${e.name.padEnd(26)} ${String(e.requests).padStart(6)} req  avg ${e.avgMs.toFixed(1).padStart(7)} ms  p95 ${e.p95Ms.toFixed(1).padStart(7)} ms  ${e.failed} failed`);
  }
  console.log('─'.repeat(64));
  const t1 = summary.thresholds.failureRate;
  const t2 = summary.thresholds.p95Ms;
  console.log(`  ${t1.passed ? 'PASS' : 'FAIL'}  failure rate ${(t1.actual * 100).toFixed(2)}% < ${(t1.limit * 100).toFixed(0)}%`);
  console.log(`  ${t2.passed ? 'PASS' : 'FAIL'}  p95 ${t2.actual.toFixed(1)} ms < ${t2.limit} ms`);
  console.log('─'.repeat(64));
  console.log(`  summary written to ${OUT}`);

  process.exit(summary.passed ? 0 : 1);
}

main().catch(err => { console.error(err); process.exit(2); });
