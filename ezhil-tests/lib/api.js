/**
 * Minimal HTTP client for the API contract suite.
 *
 * node:http rather than fetch so the response headers, the raw body and the
 * measured latency are all available without ceremony.
 */
const http = require('node:http');
const { URL } = require('node:url');

const BASE = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');

const CREDS = {
  teacher: { school_code: 'SCH-001', teacher_id: '1001', pin: '1234' },
  student: { school_code: 'SCH-001', student_code: 'KAVIN', pin: '0512' },
};

const agent = new http.Agent({ keepAlive: true, maxSockets: 8 });

function request(method, pathname, { headers = {}, body = null, raw = null } = {}) {
  return new Promise(resolve => {
    const u = new URL(BASE + pathname);
    const payload = raw != null ? Buffer.from(raw)
      : body != null ? Buffer.from(JSON.stringify(body)) : null;
    const started = process.hrtime.bigint();
    const req = http.request({
      agent, method,
      hostname: u.hostname, port: u.port || 80, path: u.pathname + u.search,
      headers: {
        ...headers,
        ...(payload && !headers['Content-Type'] ? { 'Content-Type': 'application/json' } : {}),
        ...(payload ? { 'Content-Length': payload.length } : {}),
      },
      timeout: 30_000,
    }, res => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => resolve({
        status: res.statusCode,
        headers: res.headers,
        body: Buffer.concat(chunks).toString('utf8'),
        ms: Number(process.hrtime.bigint() - started) / 1e6,
      }));
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', err => resolve({
      status: 0, headers: {}, body: '', error: err.message,
      ms: Number(process.hrtime.bigint() - started) / 1e6,
    }));
    if (payload) req.write(payload);
    req.end();
  });
}

async function login(role = 'teacher') {
  const p = role === 'teacher' ? '/api/v1/auth/login' : '/api/v1/auth/student/login';
  const res = await request('POST', p, { body: CREDS[role] });
  if (res.status !== 200) throw new Error(`${role} login failed (${res.status}): ${res.body.slice(0, 160)}`);
  return JSON.parse(res.body).access_token;
}

/** Every operation the running server advertises, read from its own schema. */
async function operations() {
  const res = await request('GET', '/openapi.json');
  if (res.status !== 200) throw new Error(`could not read /openapi.json (${res.status})`);
  const spec = JSON.parse(res.body);
  const ops = [];
  for (const [p, methods] of Object.entries(spec.paths)) {
    for (const [m, def] of Object.entries(methods)) {
      ops.push({
        method: m.toUpperCase(),
        path: p,
        // Path params get a syntactically valid but absent id, so the endpoint
        // is exercised without depending on a particular row existing.
        concrete: p.replace(/\{[^}]+\}/g, '00000000-0000-4000-8000-000000000000'),
        hasPathParams: /\{[^}]+\}/.test(p),
        operationId: def.operationId || `${m}:${p}`,
        requiresBody: Boolean(def.requestBody),
        isOpen: p.startsWith('/api/v1/auth/') || p === '/health' || p === '/',
      });
    }
  }
  return ops.sort((a, b) => (a.path + a.method).localeCompare(b.path + b.method));
}

module.exports = { BASE, CREDS, request, login, operations };
