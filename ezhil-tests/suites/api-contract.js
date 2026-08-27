#!/usr/bin/env node
/**
 * Unit & API.
 *
 * Two halves. First, contract checks driven off the server's own OpenAPI
 * schema, so an endpoint added without tests shows up here rather than being
 * quietly uncovered. Second, the existing backend pytest and web vitest runs,
 * folded in as rows so one report covers everything that guards the app.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const H = require('../lib/harness');
const API = require('../lib/api');

const ROOT = path.join(__dirname, '..', '..');
const OUT = path.join(__dirname, '..', 'reports', 'api-contract.json');
const SUITE = 'Unit & API';

/** A token that is well-formed but signed with the wrong key. */
const FORGED =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'
  + '.eyJzdWIiOiJhdHRhY2tlciIsInJvbGUiOiJ0ZWFjaGVyIiwiZXhwIjo0MTAyNDQ0ODAwfQ'
  + '.wrong_signature_that_should_never_verify';

const LATENCY_BUDGET_MS = Number(process.env.API_LATENCY_BUDGET_MS || 2000);
const LEAKY = /Traceback \(most recent call last\)|File "[A-Za-z]:\\|site-packages|sqlalchemy\.|psycopg|\.py", line \d+/;

async function contractChecks(rec, token) {
  const ops = await API.operations();
  console.log(`  ${ops.length} operations advertised by the server\n`);

  for (const op of ops) {
    const label = `${op.method} ${op.path}`;
    const auth = { Authorization: `Bearer ${token}` };
    const body = op.requiresBody ? {} : null;

    // One authorised call per operation, reused by several assertions so the
    // suite does not hammer the server 13 times per endpoint.
    const ok = await API.request(op.method, op.concrete, { headers: auth, body });

    await H.check(rec, 'Contract — reachability', `${label} — is routed`, () => {
      if (ok.status === 0) throw new Error(ok.error || 'no response');
      if (ok.status === 404 && !op.hasPathParams) throw new Error('404 on a path the schema advertises');
    });

    await H.check(rec, 'Contract — no server error', `${label} — does not 5xx`, () => {
      if (ok.status >= 500) throw new Error(`${ok.status}: ${ok.body.slice(0, 160)}`);
    });

    await H.check(rec, 'Contract — content type', `${label} — answers JSON`, () => {
      const ct = ok.headers['content-type'] || '';
      if (ok.status === 204 || !ok.body) return;
      if (!/json/i.test(ct)) throw new Error(`content-type was "${ct}"`);
    });

    await H.check(rec, 'Contract — parseable', `${label} — body is valid JSON`, () => {
      if (!ok.body) return;
      try { JSON.parse(ok.body); } catch { throw new Error(`unparseable: ${ok.body.slice(0, 120)}`); }
    });

    await H.check(rec, 'Security — no stack traces', `${label} — leaks no traceback`, () => {
      if (LEAKY.test(ok.body)) throw new Error(`response exposes internals: ${ok.body.slice(0, 160)}`);
    });

    await H.check(rec, 'Security — no credentials echoed', `${label} — does not echo the PIN`, () => {
      if (/"?pin"?\s*[:=]\s*"?1234/.test(ok.body)) throw new Error('the PIN appears in the response body');
    });

    await H.check(rec, 'Performance — latency', `${label} — under ${LATENCY_BUDGET_MS}ms`, () => {
      if (ok.ms > LATENCY_BUDGET_MS) throw new Error(`took ${ok.ms.toFixed(0)}ms`);
    });

    await H.check(rec, 'Contract — unknown query params', `${label} — tolerates an unknown query param`, async () => {
      const r = await API.request(op.method, `${op.concrete}?__unknown=1`, { headers: auth, body });
      if (r.status >= 500) throw new Error(`${r.status} on an unknown query param`);
    });

    await H.check(rec, 'Contract — method handling', `${label} — rejects an unsupported verb cleanly`, async () => {
      const r = await API.request('TRACE', op.concrete, { headers: auth });
      if (r.status >= 500) throw new Error(`TRACE produced ${r.status}`);
    });

    if (!op.isOpen) {
      await H.check(rec, 'Auth — anonymous', `${label} — refuses a request with no token`, async () => {
        const r = await API.request(op.method, op.concrete, { body });
        if (r.status !== 401 && r.status !== 403) throw new Error(`answered ${r.status} without a token`);
      });

      await H.check(rec, 'Auth — malformed token', `${label} — refuses a malformed token`, async () => {
        const r = await API.request(op.method, op.concrete, { headers: { Authorization: 'Bearer not-a-jwt' }, body });
        if (r.status !== 401 && r.status !== 403) throw new Error(`answered ${r.status} for "not-a-jwt"`);
      });

      await H.check(rec, 'Auth — forged signature', `${label} — refuses a token signed with the wrong key`, async () => {
        const r = await API.request(op.method, op.concrete, { headers: { Authorization: `Bearer ${FORGED}` }, body });
        if (r.status !== 401 && r.status !== 403) throw new Error(`accepted a forged signature (${r.status})`);
      });

      await H.check(rec, 'Auth — scheme', `${label} — refuses a bare token without Bearer`, async () => {
        const r = await API.request(op.method, op.concrete, { headers: { Authorization: token }, body });
        if (r.status !== 401 && r.status !== 403) throw new Error(`accepted a bare token (${r.status})`);
      });
    }

    if (op.requiresBody) {
      await H.check(rec, 'Validation — malformed body', `${label} — 4xx on a body that is not JSON`, async () => {
        const r = await API.request(op.method, op.concrete, {
          headers: { ...auth, 'Content-Type': 'application/json' }, raw: '{not json',
        });
        if (r.status >= 500) throw new Error(`${r.status} on malformed JSON`);
        if (r.status < 400) throw new Error(`accepted malformed JSON with ${r.status}`);
      });

      await H.check(rec, 'Validation — wrong types', `${label} — 4xx when fields are the wrong type`, async () => {
        const r = await API.request(op.method, op.concrete, { headers: auth, body: { school_code: 12345, pin: [] } });
        if (r.status >= 500) throw new Error(`${r.status} on wrong field types`);
      });

      await H.check(rec, 'Validation — oversized field', `${label} — survives a very long field`, async () => {
        const r = await API.request(op.method, op.concrete, { headers: auth, body: { school_code: 'A'.repeat(20000) } });
        if (r.status >= 500) throw new Error(`${r.status} on a 20k-character field`);
      });
    }
  }
}

/** Login-specific behaviour: the one endpoint an attacker reaches first. */
async function authChecks(rec) {
  const P = '/api/v1/auth/login';
  const cases = [
    ['wrong pin', { school_code: 'SCH-001', teacher_id: '1001', pin: '9999' }],
    ['unknown teacher', { school_code: 'SCH-001', teacher_id: 'nope', pin: '1234' }],
    ['unknown school', { school_code: 'SCH-999', teacher_id: '1001', pin: '1234' }],
    ['empty pin', { school_code: 'SCH-001', teacher_id: '1001', pin: '' }],
    ['sql-ish teacher id', { school_code: 'SCH-001', teacher_id: "' OR '1'='1", pin: '1234' }],
    ['sql-ish school', { school_code: "'; DROP TABLE students;--", teacher_id: '1001', pin: '1234' }],
    ['null pin', { school_code: 'SCH-001', teacher_id: '1001', pin: null }],
    ['array injection', { school_code: ['SCH-001'], teacher_id: '1001', pin: '1234' }],
  ];

  for (const [name, body] of cases) {
    const r = await API.request('POST', P, { body });
    await H.check(rec, 'Auth — bad credentials', `login rejects ${name}`, () => {
      if (r.status === 200) throw new Error(`logged in with ${name}`);
      if (r.status >= 500) throw new Error(`${r.status} — a bad credential should not be a server error`);
    });
    await H.check(rec, 'Auth — no token on failure', `login returns no token for ${name}`, () => {
      if (/access_token/.test(r.body)) throw new Error('a token was issued for a rejected login');
    });
    await H.check(rec, 'Auth — no internals leaked', `login leaks nothing for ${name}`, () => {
      if (LEAKY.test(r.body)) throw new Error(`internals exposed: ${r.body.slice(0, 140)}`);
    });
  }

  // The students table must still be there after the injection attempts above.
  await H.check(rec, 'Auth — bad credentials', 'the database survived the injection attempts', async () => {
    const token = await API.login('teacher');
    const r = await API.request('GET', '/api/v1/dashboard/teacher', { headers: { Authorization: `Bearer ${token}` } });
    if (r.status !== 200) throw new Error(`dashboard answered ${r.status} afterwards`);
    const d = JSON.parse(r.body);
    if (typeof d.total_students !== 'number') throw new Error('dashboard no longer reports a student count');
  });
}

/** Fold an existing runner's results in, so one report covers everything. */
function importJunit(rec, xml, category) {
  // Self-closing and paired forms handled separately. A single lazy pattern
  // spanning to the next "/>" swallowed two elements at a time, which silently
  // reported 70 backend tests as 35 -- a parser bug that reads as missing
  // coverage rather than as an error.
  const cases = [...xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)];
  let n = 0;
  for (const [, attrs, inner] of cases) {
    const name = (attrs.match(/\bname="([^"]*)"/) || [, '?'])[1];
    const cls = (attrs.match(/\bclassname="([^"]*)"/) || [, ''])[1];
    const time = parseFloat((attrs.match(/\btime="([^"]*)"/) || [, '0'])[1]) * 1000;
    const failed = /<(failure|error)\b/.test(inner || '');
    const msg = failed ? ((inner.match(/message="([^"]*)"/) || [, 'failed'])[1]) : '';
    rec.record({
      category, name: cls ? `${cls}::${name}` : name,
      status: failed ? 'failed' : 'passed', durationMs: time, error: msg,
    });
    n++;
  }
  return n;
}

function runBackendTests(rec) {
  // The Windows venv layout does not exist on a Linux runner, where the
  // interpreter is simply on PATH. Prefer an explicit override, then whichever
  // venv layout is present, then PATH.
  const candidates = [
    process.env.PYTEST_PYTHON,
    path.join(ROOT, 'ezhil-backend', '.venv', 'Scripts', 'python.exe'),
    path.join(ROOT, 'ezhil-backend', '.venv', 'bin', 'python'),
  ].filter(Boolean);
  const py = candidates.find(c => c === process.env.PYTEST_PYTHON || fs.existsSync(c)) || 'python';
  const xmlPath = path.join(__dirname, '..', 'reports', '.pytest.xml');
  try {
    execFileSync(py, ['-m', 'pytest', 'tests/', '-q', `--junitxml=${xmlPath}`],
      { cwd: path.join(ROOT, 'ezhil-backend'), stdio: 'pipe' });
  } catch { /* non-zero exit means failures; the XML still describes them */ }
  if (!fs.existsSync(xmlPath)) return 0;
  const n = importJunit(rec, fs.readFileSync(xmlPath, 'utf-8'), 'Unit — backend (pytest)');
  fs.unlinkSync(xmlPath);
  return n;
}

function runWebTests(rec) {
  const cwd = path.join(ROOT, 'ezhil-web');
  const out = path.join(__dirname, '..', 'reports', '.vitest.xml');
  try {
    execFileSync('npx', ['vitest', 'run', '--reporter=junit', `--outputFile=${out}`],
      { cwd, stdio: 'pipe', shell: true });
  } catch { /* same: the XML is what matters */ }
  if (!fs.existsSync(out)) return 0;
  const n = importJunit(rec, fs.readFileSync(out, 'utf-8'), 'Unit — web (vitest)');
  fs.unlinkSync(out);
  return n;
}

async function main() {
  const rec = new H.Recorder(SUITE);
  console.log(`\n${SUITE}\n  target ${API.BASE}\n`);

  const token = await API.login('teacher');
  await contractChecks(rec, token);
  await authChecks(rec);

  console.log('  running backend pytest…');
  console.log(`    ${runBackendTests(rec)} cases imported`);
  console.log('  running web vitest…');
  console.log(`    ${runWebTests(rec)} cases imported`);

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify({ summary: rec.summary, rows: rec.rows }, null, 2));

  const s = rec.summary;
  console.log(`\n  ${s.passed}/${s.total} passed, ${s.failed} failed`);
  if (s.failed) {
    console.log('\n  Failures:');
    for (const r of rec.rows.filter(r => r.status === 'failed').slice(0, 30)) {
      console.log(`    ✗ ${r.name}\n      ${r.error}`);
    }
  }
  console.log(`  → ${OUT}`);
  process.exit(s.failed ? 1 : 0);
}

main().catch(err => { console.error(err); process.exit(2); });
