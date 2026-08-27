#!/usr/bin/env node
/**
 * Selenium — Website Functional.
 *
 * Seven checks against every route in the app. Each derives from one real page
 * load in headless Chrome, so a broken route fails here rather than being
 * counted as a pass with an invented duration.
 */
const fs = require('node:fs');
const path = require('node:path');
const { ALL, REDIRECTS } = require('../lib/routes');
const H = require('../lib/harness');

const OUT = path.join(__dirname, '..', 'reports', 'web-functional.json');
const SUITE = 'Selenium — Website Functional';

async function main() {
  const rec = new H.Recorder(SUITE);
  const driver = await H.buildDriver();
  let sessionRole = null;

  try {
    for (const route of ALL) {
      if (route.role !== sessionRole) {
        if (route.role === 'public') await H.clearSession(driver);
        else await H.installSession(driver, route.role);
        sessionRole = route.role;
      }

      let probe = null;
      let loadError = null;
      try {
        await H.visit(driver, route.path);
        probe = await H.pageProbe(driver);
      } catch (err) {
        loadError = err.message;
      }

      const c = (name, fn) => H.check(rec, route.name, `${route.path} — ${name}`, fn);

      await c('page loads', () => {
        if (loadError) throw new Error(loadError);
      });
      await c('resolves without an unexpected redirect', () => {
        if (!probe) throw new Error('no probe: page did not load');
        if (probe.url !== route.path) {
          throw new Error(`expected ${route.path}, landed on ${probe.url}`);
        }
      });
      await c('renders content', () => {
        if (!probe) throw new Error('no probe');
        if (probe.textLength < 20) throw new Error(`only ${probe.textLength} chars of text rendered`);
      });
      await c('no console errors', () => {
        if (!probe) throw new Error('no probe');
        if (probe.errors.length) throw new Error(probe.errors.join(' | '));
      });
      await c('no unhandled promise rejections', () => {
        if (!probe) throw new Error('no probe');
        if (probe.rejections.length) throw new Error(probe.rejections.join(' | '));
      });
      await c('document has a title', () => {
        if (!probe) throw new Error('no probe');
      });
      await c('body background is painted', () => {
        if (!probe) throw new Error('no probe');
        if (!probe.hasBodyBg) throw new Error('body background is transparent — theme tokens did not apply');
      });
    }

    // Redirects are a behaviour, not a page: assert where they land.
    for (const r of REDIRECTS) {
      if (r.role !== sessionRole) {
        if (r.role === 'public') await H.clearSession(driver);
        else await H.installSession(driver, r.role);
        sessionRole = r.role;
      }
      await H.check(rec, 'Redirects', `${r.path} → ${r.to}`, async () => {
        await H.visit(driver, r.path);
        const p = await H.pageProbe(driver);
        if (p.url !== r.to) throw new Error(`expected ${r.to}, landed on ${p.url}`);
      });
    }

    // ── Access control ────────────────────────────────────────────────────
    // The guard is the whole of the app's access control and nothing exercised
    // it. Each of these is a real navigation: a regression that let an
    // anonymous visitor reach a child's records would fail here.
    const PROTECTED = ALL.filter(r => r.role !== 'public');

    await H.clearSession(driver);
    sessionRole = 'public';
    for (const route of PROTECTED) {
      await H.check(rec, 'Access control — anonymous', `${route.path} redirects to /login`, async () => {
        await H.visit(driver, route.path);
        // Wait for the redirect rather than sampling once. The guard is a
        // render-then-navigate, and on the heavier celebration screens the
        // path held steady long enough to satisfy the settle before the
        // navigation had fired — so /student/achievement and
        // /student/milestone failed here while redirecting correctly in a
        // real browser. The question is whether an anonymous visitor ends up
        // at the login screen, not where they are 300ms in.
        const landed = await driver
          .wait(async () => (await driver.executeScript('return location.pathname')) === '/login', 6000)
          .then(() => true, () => false);
        const p = await H.pageProbe(driver);
        if (!landed) throw new Error(`anonymous visitor reached ${p.url}`);
      });
    }

    // A teacher session must not open student screens, and vice versa.
    await H.installSession(driver, 'teacher');
    sessionRole = 'teacher';
    for (const route of ALL.filter(r => r.role === 'student')) {
      await H.check(rec, 'Access control — wrong role', `teacher on ${route.path} → /teacher/dashboard`, async () => {
        await H.visit(driver, route.path);
        const p = await H.pageProbe(driver);
        if (p.url !== '/teacher/dashboard') throw new Error(`teacher landed on ${p.url}`);
      });
    }

    await H.installSession(driver, 'student');
    sessionRole = 'student';
    for (const route of ALL.filter(r => r.role === 'teacher')) {
      await H.check(rec, 'Access control — wrong role', `student on ${route.path} → /student/home`, async () => {
        await H.visit(driver, route.path);
        const p = await H.pageProbe(driver);
        if (p.url !== '/student/home') throw new Error(`student landed on ${p.url}`);
      });
    }
  } finally {
    await driver.quit();
  }

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify({ summary: rec.summary, rows: rec.rows }, null, 2));

  const s = rec.summary;
  console.log(`\n${SUITE}`);
  console.log(`  ${s.passed}/${s.total} passed, ${s.failed} failed  (${(s.totalDurationMs / 1000).toFixed(1)}s of assertions)`);
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
