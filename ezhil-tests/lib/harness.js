/**
 * Shared harness: a headless Chrome session, a session installer, and a
 * recorder that keeps whatever duration it measured.
 *
 * On durations: a fast assertion is recorded as fast. Substituting a random
 * 3-10 ms when the real figure is 0 ms makes a report of no-ops indistinguishable
 * from a report of real work, which defeats the point of having the report.
 * Where a number here looks implausibly small, that is information — it usually
 * means the assertion is not reaching the app.
 */
const { Builder, By, until } = require('selenium-webdriver');
const chrome = require('selenium-webdriver/chrome');

const BASE_URL = (process.env.TEST_BASE_URL || 'http://localhost:3000').replace(/\/+$/, '');
const API_URL = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');

const CREDS = {
  teacher: { school_code: 'SCH-001', teacher_id: '1001', pin: '1234' },
  student: { school_code: 'SCH-001', student_code: 'KAVIN', pin: '0512' },
};

async function buildDriver() {
  const opts = new chrome.Options();
  opts.addArguments(
    '--headless=new',
    '--no-sandbox',
    '--disable-dev-shm-usage',
    '--disable-gpu',
    '--window-size=1280,900',
    // Chrome throttles rAF and timers in backgrounded windows; headless windows
    // count as backgrounded, which stalls anything animation-driven.
    '--disable-background-timer-throttling',
    '--disable-renderer-backgrounding',
    '--disable-backgrounding-occluded-windows',
    // The screening screens ask for a microphone. Headless has no device, so
    // without these the app correctly reports NotFoundError and the suite
    // blames the app for the runner's missing hardware.
    '--use-fake-device-for-media-stream',
    '--use-fake-ui-for-media-stream',
    // A synthetic device alone gives a stream MediaRecorder refuses to encode.
    // Feeding a real WAV makes the recording screen exercisable headlessly.
    `--use-file-for-fake-audio-capture=${require('node:path').join(__dirname, '..', 'fixtures', 'fake-audio.wav')}`,
  );
  return new Builder().forBrowser('chrome').setChromeOptions(opts).build();
}

/** Log in over the API and write the session the app expects into localStorage. */
async function installSession(driver, role) {
  await driver.get(`${BASE_URL}/login`);
  const result = await driver.executeAsyncScript(
    async function (apiUrl, role, creds, done) {
      try {
        const path = role === 'teacher' ? '/api/v1/auth/login' : '/api/v1/auth/student/login';
        const res = await fetch(apiUrl + path, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(creds),
        });
        if (!res.ok) return done({ ok: false, status: res.status, body: (await res.text()).slice(0, 200) });
        const d = await res.json();
        const session = role === 'teacher'
          ? { accessToken: d.access_token, role: 'teacher', name: d.teacher_name, userId: d.teacher_id,
              teacherId: d.teacher_id, schoolCode: 'SCH-001', schoolName: d.school_name, teacherName: d.teacher_name }
          : { accessToken: d.access_token, role: 'student', name: d.student_name, userId: d.student_id,
              studentId: d.student_id, schoolCode: 'SCH-001', schoolName: d.school_name };
        localStorage.setItem('ezhil_session', JSON.stringify(session));
        done({ ok: true });
      } catch (e) { done({ ok: false, error: String(e) }); }
    },
    API_URL, role, role === 'teacher' ? CREDS.teacher : CREDS.student,
  );
  if (!result.ok) throw new Error(`Could not establish a ${role} session: ${JSON.stringify(result)}`);
}

async function clearSession(driver) {
  await driver.get(`${BASE_URL}/login`);
  await driver.executeScript('localStorage.clear(); sessionStorage.clear();');
}

/**
 * Navigate and wait for React to have painted something.
 * Returns the console errors collected during the load.
 */
async function visit(driver, path) {
  await driver.executeScript('window.__errs = []; window.__rejections = [];');
  await driver.get(`${BASE_URL}${path}`);
  await driver.executeScript(`
    window.__errs = window.__errs || [];
    window.__rejections = window.__rejections || [];
    if (!window.__hooked) {
      window.__hooked = true;
      const oe = console.error;
      console.error = function (...a) { window.__errs.push(a.map(String).join(' ')); oe.apply(console, a); };
      window.addEventListener('error', e => window.__errs.push('error: ' + e.message));
      window.addEventListener('unhandledrejection', e => window.__rejections.push(String(e.reason)));
    }
  `);
  await driver.wait(async () => {
    const ready = await driver.executeScript('return document.readyState === "complete"');
    return ready;
  }, 15000);
  // Wait for the route to settle rather than sleeping a fixed interval.
  // A guard redirect is a render-then-navigate, so a fixed sleep samples
  // mid-flight now and then — which showed up as a lone access-control
  // assertion failing on one run and passing on the next three. Poll until the
  // pathname has held steady, which is the condition actually being waited on.
  let last = null;
  let stableFor = 0;
  const startedAt = Date.now();
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) {
    const now = await driver.executeScript('return location.pathname');
    stableFor = now === last ? stableFor + 1 : 0;
    last = now;
    // Three polls, and never before 500ms have elapsed. Two polls could be
    // satisfied by a path that simply had not navigated yet, which is the
    // race this loop exists to remove.
    if (stableFor >= 3 && Date.now() - startedAt > 500) break;
    await driver.sleep(120);
  }
  return driver;
}

async function pageProbe(driver) {
  return driver.executeScript(`
    const main = document.querySelector('main') || document.body;
    const text = (main.innerText || '').replace(/\\s+/g, ' ').trim();
    const tamil = /[\\u0B80-\\u0BFF]/;
    // The design system deliberately tracks the Tamil body and reader faces a
    // little (0.02em and 0.03em) for readability. What must never happen is
    // tracking beyond that, or any negative tracking -- both collide or split
    // the vowel signs and pulli. So the rule is expressed as a ratio of the
    // font size rather than a flat pixel figure, which is what actually
    // distinguishes a deliberate 0.03em from a stray tracking-widest at 0.1em.
    const tracked = [...document.querySelectorAll('*')].filter(el => {
      if (!tamil.test(el.textContent || '')) return false;
      if (el.children.length) return false;
      const cs = getComputedStyle(el);
      const ls = cs.letterSpacing;
      if (!ls || ls === 'normal') return false;
      // letter-spacing inherits as a computed *length*, not as an em ratio, so
      // a 0.03em rule on a 32px parent hands 0.96px down to a 24px child and
      // that child reads as 0.04em without anything being wrong. The positive
      // bound therefore sits above the design system's base plus that drift,
      // where tracking-wider (0.05em) and tracking-widest (0.1em) still fall
      // outside it. Negative tracking has no legitimate use over Tamil at all.
      const ratio = parseFloat(ls) / parseFloat(cs.fontSize);
      return ratio > 0.049 || ratio < -0.001;
    }).length;
    const tinyText = [...document.querySelectorAll('p,span,label,td,th,li,a,button,h1,h2,h3,h4,h5,h6')]
      .filter(el => el.children.length === 0 && (el.textContent || '').trim().length > 1)
      .filter(el => parseFloat(getComputedStyle(el).fontSize) < 12).length;
    const namelessButtons = [...document.querySelectorAll('button,a[href]')]
      .filter(el => !(el.getAttribute('aria-label') || el.getAttribute('title') || el.textContent || '').trim()).length;
    const imgsNoAlt = [...document.querySelectorAll('img')].filter(i => i.getAttribute('alt') === null).length;
    return {
      url: location.pathname,
      textLength: text.length,
      headText: text.slice(0, 90),
      // Overflow, measured per element rather than off scrollWidth.
      //
      // scrollWidth alone lies in both directions here. A position:fixed layer
      // is laid out against the initial containing block, which *includes* the
      // scrollbar, so a correctly full-width decorative layer measures 391 in a
      // 375 viewport and reads as overflow. Meanwhile content clipped by an
      // overflow-hidden ancestor is not overflow at all. So: skip anything a
      // clipping ancestor already contains, and judge fixed elements against
      // the width they were actually laid out against.
      overflowX: (() => {
        const client = document.documentElement.clientWidth;
        const outer = window.innerWidth;
        const clipped = el => {
          for (let p = el.parentElement; p; p = p.parentElement) {
            const o = getComputedStyle(p).overflowX;
            if (o === 'hidden' || o === 'clip' || o === 'auto' || o === 'scroll') return true;
          }
          return false;
        };
        const hit = [...document.querySelectorAll('body *')].find(el => {
          const cs = getComputedStyle(el);
          if (cs.display === 'none' || cs.visibility === 'hidden') return false;
          // A fixed ancestor is laid out against the initial containing block,
          // which includes the scrollbar, and its children inherit that box.
          // Judging those children against clientWidth reports the scrollbar
          // width as overflow on every page with a fixed header or nav.
          let inFixed = cs.position === 'fixed';
          for (let a = el.parentElement; a && !inFixed; a = a.parentElement) {
            if (getComputedStyle(a).position === 'fixed') inFixed = true;
          }
          const limit = inFixed ? outer : client;
          const b = el.getBoundingClientRect();
          if (b.width === 0) return false;
          if (b.right <= limit + 1 && b.left >= -1) return false;
          return !clipped(el);
        });
        if (hit) {
          const b = hit.getBoundingClientRect();
          window.__ovf = hit.tagName + ' .' + (hit.className.toString() || '').trim().slice(0, 60)
            + ' spans ' + Math.round(b.left) + '..' + Math.round(b.right) + 'px';
        } else { window.__ovf = null; }
        return !!hit;
      })(),
      overflowDetail: null,
      docScrollWidth: document.documentElement.scrollWidth,
      overflowWho: window.__ovf || null,
      innerWidth: document.documentElement.clientWidth,
      trackedTamil: tracked,
      tinyText,
      namelessButtons,
      imgsNoAlt,
      focusables: document.querySelectorAll('a[href],button,input,select,textarea,[tabindex]:not([tabindex="-1"])').length,
      errors: (window.__errs || []).slice(0, 5),
      rejections: (window.__rejections || []).slice(0, 5),
      hasBodyBg: (() => {
        const cs = getComputedStyle(document.body);
        // The theme paints a radial-gradient, which lands in backgroundImage —
        // backgroundColor stays transparent and is not evidence of anything.
        return cs.backgroundColor !== 'rgba(0, 0, 0, 0)' || cs.backgroundImage !== 'none';
      })(),
    };
  `);
}

/** Collects results with real durations for the Excel/HTML reports. */
class Recorder {
  constructor(suite) {
    this.suite = suite;
    this.rows = [];
    this.startedAt = new Date();
  }
  record({ category, name, status, durationMs, error }) {
    this.rows.push({
      suite: this.suite,
      category,
      name,
      status,
      // Measured, not synthesised. Three decimals so sub-millisecond work is
      // visible as sub-millisecond rather than rounding to a misleading 0.
      durationMs: Number(durationMs.toFixed(3)),
      error: error ? String(error).slice(0, 500) : '',
      at: new Date().toISOString(),
    });
  }
  get summary() {
    const passed = this.rows.filter(r => r.status === 'passed').length;
    const failed = this.rows.filter(r => r.status === 'failed').length;
    return {
      suite: this.suite,
      total: this.rows.length,
      passed,
      failed,
      passRate: this.rows.length ? passed / this.rows.length : 0,
      totalDurationMs: Number(this.rows.reduce((a, r) => a + r.durationMs, 0).toFixed(3)),
      startedAt: this.startedAt.toISOString(),
      finishedAt: new Date().toISOString(),
    };
  }
}

/** Time a function and record the outcome. Never swallows a real failure. */
async function check(rec, category, name, fn) {
  const t0 = process.hrtime.bigint();
  try {
    await fn();
    rec.record({ category, name, status: 'passed', durationMs: Number(process.hrtime.bigint() - t0) / 1e6 });
    return true;
  } catch (err) {
    rec.record({ category, name, status: 'failed', durationMs: Number(process.hrtime.bigint() - t0) / 1e6, error: err.message });
    return false;
  }
}

/**
 * Wait for layout to stop moving after a viewport change.
 *
 * A fixed 200 ms was not enough: the teacher header is `hidden md:flex` with
 * transitions on it, so crossing the md breakpoint left it mid-reflow and it
 * measured 781px wide in a 762px viewport -- a clean false positive that
 * survived three attempts to explain it as a real overflow. Poll until the
 * document's scroll width holds steady instead.
 */
async function settleLayout(driver, timeoutMs = 3000) {
  let last = null;
  let stable = 0;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    // Width alone is not enough of a signature: on a page that has not painted
    // yet the width is trivially stable, so this returned before any control
    // existed and 38 routes were reported as having no focusable element.
    // Include what is actually on the page.
    const w = await driver.executeScript(`
      return [
        document.documentElement.scrollWidth,
        document.documentElement.clientWidth,
        document.querySelectorAll('a[href],button,input,select,textarea,[tabindex]:not([tabindex="-1"])').length,
        document.body.innerText.length,
      ].join('x');
    `);
    stable = w === last ? stable + 1 : 0;
    last = w;
    if (stable >= 2) return;
    await driver.sleep(100);
  }
}

async function setViewport(driver, width, height) {
  await driver.sendDevToolsCommand('Emulation.setDeviceMetricsOverride', {
    width, height, deviceScaleFactor: 1, mobile: width < 768,
  });
}

module.exports = {
  setViewport, settleLayout,
  BASE_URL, API_URL, CREDS,
  buildDriver, installSession, clearSession, visit, pageProbe,
  Recorder, check, By, until,
};
