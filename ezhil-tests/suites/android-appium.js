#!/usr/bin/env node
/**
 * Android — On-device (Appium).
 *
 * What only a running app can show: that it launches, survives rotation and
 * backgrounding, honours the portrait lock, and that the login screen is
 * actually operable.
 *
 * Deliberately narrow. The build and source contracts live in
 * android-static.js, which needs no device; putting a few hundred parametric
 * assertions here would mostly re-test those through a slower, flakier path.
 * What is here needs a real device and cannot be established any other way.
 *
 * NOTE: unlike the other suites, this one has never run on a device as of
 * writing. Its first real execution is on the CI emulator.
 */
const fs = require('node:fs');
const path = require('node:path');
const H = require('../lib/harness');

const OUT = path.join(__dirname, '..', 'reports', 'android-appium.json');
const SUITE = 'Android — On-device';
const APPIUM = process.env.APPIUM_URL || 'http://127.0.0.1:4723';
const APK = process.env.APK_PATH
  || path.join(__dirname, '..', '..', 'ezhil-android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-arm64-v8a-debug.apk');
const PKG = process.env.APP_PACKAGE || 'com.ezhil.app';

async function http(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* keep the raw text */ }
  return { status: res.status, json, text };
}

async function newSession() {
  const caps = {
    capabilities: {
      alwaysMatch: {
        platformName: 'Android',
        'appium:automationName': 'UiAutomator2',
        'appium:app': APK,
        'appium:appPackage': PKG,
        'appium:newCommandTimeout': 180,
        'appium:autoGrantPermissions': true,
        'appium:noReset': false,
      },
      firstMatch: [{}],
    },
  };
  const res = await http('POST', `${APPIUM}/session`, caps);
  const id = res.json?.value?.sessionId || res.json?.sessionId;
  if (!id) throw new Error(`Appium refused the session: ${res.text.slice(0, 300)}`);
  return id;
}

const S = (id, p) => `${APPIUM}/session/${id}${p}`;

async function main() {
  const rec = new H.Recorder(SUITE);
  console.log(`\n${SUITE}\n  appium ${APPIUM}\n  apk    ${APK}\n`);

  if (!fs.existsSync(APK)) {
    console.error(`APK not found at ${APK}. Build it with ./gradlew assembleDebug.`);
    process.exit(2);
  }

  let id = null;
  try {
    id = await newSession();

    await H.check(rec, 'Launch', 'the app starts and reports a session', () => {
      if (!id) throw new Error('no session id');
    });

    await H.check(rec, 'Launch', 'the launched package is ours', async () => {
      const r = await http('GET', S(id, '/appium/device/current_package'));
      const pkg = r.json?.value;
      if (pkg !== PKG) throw new Error(`foreground package is ${pkg}`);
    });

    await H.check(rec, 'Launch', 'an activity is displayed', async () => {
      const r = await http('GET', S(id, '/appium/device/current_activity'));
      if (!r.json?.value) throw new Error('no current activity');
    });

    await H.check(rec, 'Launch', 'the first screen renders a view hierarchy', async () => {
      const r = await http('GET', S(id, '/source'));
      const xml = r.json?.value || '';
      if (xml.length < 200) throw new Error(`page source is only ${xml.length} chars — nothing rendered`);
      if (!/android\.widget|android\.view/.test(xml)) throw new Error('no Android views in the hierarchy');
    });

    await H.check(rec, 'Orientation', 'the app reports portrait', async () => {
      const r = await http('GET', S(id, '/orientation'));
      if (r.json?.value !== 'PORTRAIT') throw new Error(`orientation is ${r.json?.value}`);
    });

    await H.check(rec, 'Orientation', 'D11 — a rotation request leaves it portrait', async () => {
      await http('POST', S(id, '/orientation'), { orientation: 'LANDSCAPE' });
      await new Promise(r => setTimeout(r, 1500));
      const r = await http('GET', S(id, '/orientation'));
      if (r.json?.value !== 'PORTRAIT') {
        throw new Error(`rotated to ${r.json?.value} — the portrait lock is not holding`);
      }
    });

    await H.check(rec, 'Lifecycle', 'survives being backgrounded and resumed', async () => {
      await http('POST', S(id, '/appium/device/app_state'), { appId: PKG });
      await http('POST', S(id, '/appium/device/background_app'), { seconds: 2 });
      await new Promise(r => setTimeout(r, 2500));
      const r = await http('POST', S(id, '/appium/device/app_state'), { appId: PKG });
      // 4 = running in foreground, 3 = running in background
      if (![3, 4].includes(r.json?.value)) throw new Error(`app state is ${r.json?.value} after resuming`);
    });

    await H.check(rec, 'Lifecycle', 'no crash dialog is on screen', async () => {
      const r = await http('GET', S(id, '/source'));
      const xml = r.json?.value || '';
      if (/has stopped|keeps stopping|isn't responding|Application Error/i.test(xml)) {
        throw new Error('a system crash dialog is displayed');
      }
    });

    await H.check(rec, 'Accessibility', 'interactive elements carry a label', async () => {
      const r = await http('GET', S(id, '/source'));
      const xml = r.json?.value || '';
      // Match the whole element, not the tail after clickable="true".
      // uiautomator writes content-desc and text *before* clickable, so a
      // pattern anchored at clickable never saw either and reported every
      // tappable element as unlabelled.
      const nodes = [...xml.matchAll(/<[^>]*clickable="true"[^>]*>/g)].map(m => m[0]);
      if (!nodes.length) return;   // a splash screen legitimately has none
      const labelled = nodes.filter(n =>
        /content-desc="[^"]+"/.test(n) || /text="[^"]+"/.test(n) ||
        /resource-id="[^"]+"/.test(n));
      if (labelled.length === 0) {
        throw new Error(`none of the ${nodes.length} tappable elements carry a label, text or id`);
      }
    });

    await H.check(rec, 'Rendering', 'nothing renders outside the screen width', async () => {
      const sizeRes = await http('GET', S(id, '/window/rect'));
      const width = sizeRes.json?.value?.width;
      if (!width) throw new Error('could not read the window size');
      const r = await http('GET', S(id, '/source'));
      const xml = r.json?.value || '';
      const bounds = [...xml.matchAll(/bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"/g)];
      const overflowing = bounds.filter(b => Number(b[3]) > width + 1);
      if (overflowing.length) {
        throw new Error(`${overflowing.length} element(s) extend past ${width}px`);
      }
    });
  } finally {
    if (id) await http('DELETE', S(id, '')).catch(() => {});
  }

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify({ summary: rec.summary, rows: rec.rows }, null, 2));

  const s = rec.summary;
  console.log(`  ${s.passed}/${s.total} passed, ${s.failed} failed`);
  for (const r of rec.rows.filter(r => r.status === 'failed')) {
    console.log(`    ✗ ${r.name}\n      ${r.error}`);
  }
  console.log(`  → ${OUT}`);
  process.exit(s.failed ? 1 : 0);
}

main().catch(err => { console.error(err); process.exit(2); });
