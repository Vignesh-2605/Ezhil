#!/usr/bin/env node
/**
 * Selenium — Website UI & Accessibility.
 *
 * Responsive behaviour at three real viewports, plus the accessibility rules
 * this app specifically has to hold to: Tamil is never letter-spaced, and no
 * text drops below the 12px readability floor. Both matter more here than in
 * most apps — the readers are children with dyslexia.
 */
const fs = require('node:fs');
const path = require('node:path');
const { ALL } = require('../lib/routes');
const H = require('../lib/harness');

const OUT = path.join(__dirname, '..', 'reports', 'web-ui-a11y.json');
const SUITE = 'Selenium — Website UI & Accessibility';

const VIEWPORTS = [
  { name: 'mobile', width: 375, height: 812 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'desktop', width: 1280, height: 900 },
];

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

      await H.visit(driver, route.path);

      // Responsive: the page body must never scroll sideways. Horizontal
      // overflow on a phone is how a Start button ends up off-screen.
      for (const vp of VIEWPORTS) {
        await H.setViewport(driver, vp.width, vp.height);
        await H.settleLayout(driver);
        const p = await H.pageProbe(driver);
        await H.check(rec, `Responsive — ${vp.name}`, `${route.path} — no horizontal overflow at ${vp.width}px`, () => {
          if (p.overflowX) {
            throw new Error(`${p.overflowWho || 'content'} overflows a ${p.innerWidth}px viewport`);
          }
        });
      }

      // Accessibility checks are viewport-independent; read once at desktop.
      await H.setViewport(driver, 1280, 900);
      await H.settleLayout(driver);
      const p = await H.pageProbe(driver);

      await H.check(rec, 'Tamil typography', `${route.path} — no letter-spaced Tamil`, () => {
        if (p.trackedTamil > 0) {
          throw new Error(`${p.trackedTamil} Tamil element(s) carry letter-spacing, which pulls conjuncts apart`);
        }
      });
      await H.check(rec, 'Readability floor', `${route.path} — no text below 12px`, () => {
        if (p.tinyText > 0) throw new Error(`${p.tinyText} text node(s) render below the 12px floor`);
      });
      await H.check(rec, 'Accessible names', `${route.path} — every control has a name`, () => {
        if (p.namelessButtons > 0) {
          throw new Error(`${p.namelessButtons} button/link(s) have no text, aria-label or title`);
        }
      });
      await H.check(rec, 'Images', `${route.path} — every image has an alt attribute`, () => {
        if (p.imgsNoAlt > 0) throw new Error(`${p.imgsNoAlt} image(s) missing alt`);
      });
      await H.check(rec, 'Keyboard', `${route.path} — has reachable focusable elements`, () => {
        // Splash and the analysis spinner navigate on their own and carry no
        // controls, which is the intended design rather than a keyboard trap.
        if (route.transient) return;
        if (p.focusables === 0) throw new Error('no focusable element on the page — unusable by keyboard');
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
    for (const r of rec.rows.filter(r => r.status === 'failed').slice(0, 30)) {
      console.log(`    ✗ ${r.name}\n      ${r.error}`);
    }
  }
  console.log(`  → ${OUT}`);
  process.exit(s.failed ? 1 : 0);
}

main().catch(err => { console.error(err); process.exit(2); });
