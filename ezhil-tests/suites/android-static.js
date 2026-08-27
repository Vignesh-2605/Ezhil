#!/usr/bin/env node
/**
 * Android — Build & Source Contracts.
 *
 * Everything about the Android app that can be established without a device:
 * every screen exists and is reachable, the manifest and build are configured
 * the way a release needs, the shipped APKs are within budget, and the unit
 * suite passes. The Appium suite covers what only a running app can show.
 *
 * These are contracts rather than style opinions: each one, if broken, changes
 * what a child or teacher can actually do.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const H = require('../lib/harness');

const ROOT = path.join(__dirname, '..', '..');
const APP = path.join(ROOT, 'ezhil-android', 'app');
const SRC = path.join(APP, 'src', 'main', 'java', 'com', 'ezhil', 'app');
const OUT = path.join(__dirname, '..', 'reports', 'android-static.json');
const SUITE = 'Android — Build & Source';

const read = p => fs.readFileSync(p, 'utf-8');

/**
 * Every balanced `Text( ... )` call in a Compose file.
 *
 * Checking a whole file is too coarse: the splash screen sets letterSpacing on
 * the Latin wordmark and renders Tamil elsewhere, and a file-wide scan called
 * that a Tamil tracking fault. Typography rules apply to the Text that carries
 * the string, so they have to be evaluated per call.
 */
function textBlocks(src) {
  const out = [];
  const re = /\bText\s*\(/g;
  let m;
  while ((m = re.exec(src))) {
    let depth = 1;
    let i = m.index + m[0].length;
    while (i < src.length && depth > 0) {
      const ch = src[i];
      if (ch === '(') depth++;
      else if (ch === ')') depth--;
      else if (ch === '"') {                       // skip string literals
        i++;
        while (i < src.length && src[i] !== '"') i += src.charCodeAt(i) === 92 ? 2 : 1;
      }
      i++;
    }
    out.push(src.slice(m.index, i));
  }
  return out;
}
const stripComments = s => s.replace(/<!--[\s\S]*?-->/g, '').replace(/\/\*[\s\S]*?\*\//g, '');

function walk(dir, pred, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, pred, acc);
    else if (pred(p)) acc.push(p);
  }
  return acc;
}

async function screenContracts(rec) {
  const screens = walk(SRC, p => p.endsWith('Screen.kt'));
  const navGraph = read(path.join(SRC, 'ui', 'navigation', 'AppNavGraph.kt'));
  const routesFile = walk(path.join(SRC, 'ui', 'navigation'), p => p.endsWith('.kt'))
    .map(read).join('\n');

  console.log(`  ${screens.length} screens\n`);

  for (const file of screens) {
    const name = path.basename(file, '.kt');
    const src = read(file);
    const body = stripComments(src);
    const c = (cat, what, fn) => H.check(rec, cat, `${name} — ${what}`, fn);

    await c('Screens — structure', 'declares a @Composable', () => {
      if (!/@Composable/.test(body)) throw new Error('no @Composable in the file');
    });

    await c('Screens — structure', 'exposes a function matching its filename', () => {
      if (!new RegExp(`fun\\s+${name}\\s*\\(`).test(body)) {
        throw new Error(`no "fun ${name}(" — the file and its entry point disagree`);
      }
    });

    await c('Screens — navigation', 'is reachable from the nav graph', () => {
      if (!navGraph.includes(name)) {
        throw new Error('never referenced in AppNavGraph, so nothing can navigate to it');
      }
    });

    await c('Screens — readability', 'declares no type below the 12sp floor', () => {
      // fontSize only. The previous pattern matched any "N.sp", so a
      // letterSpacing of 4.sp was reported as a 4sp font.
      const bad = [...body.matchAll(/fontSize\s*=\s*[^,\n]*?(\d+(?:\.\d+)?)\s*\.\s*sp\b/g)]
        .map(m => Number(m[1]))
        .filter(n => n > 0 && n < 12);
      if (bad.length) throw new Error(`${bad.length} font size(s) below 12sp: ${[...new Set(bad)].join(', ')}`);
    });

    await c('Screens — Tamil typography', 'applies no letter-spacing to Tamil', () => {
      const offenders = textBlocks(body)
        .filter(b => /[஀-௿]/.test(b))
        .map(b => (b.match(/letterSpacing\s*=\s*([\d.]+)\s*\.\s*(?:sp|em)/) || [])[1])
        .filter(v => v !== undefined && Number(v) !== 0);
      if (offenders.length) {
        throw new Error(`letterSpacing ${offenders.join(', ')} on Text rendering Tamil`);
      }
    });

    await c('Screens — resilience', 'hardcodes no localhost or LAN address', () => {
      const m = body.match(/https?:\/\/(?:localhost|127\.0\.0\.1|10\.0\.2\.2|192\.168\.[\d.]+|172\.\d+\.[\d.]+)/);
      if (m) throw new Error(`hardcoded ${m[0]} — the base URL belongs in BuildConfig`);
    });

    await c('Screens — completeness', 'leaves no TODO() in a shipped screen', () => {
      if (/TODO\s*\(/.test(body)) throw new Error('TODO() throws at runtime the moment that branch is reached');
    });

    await c('Screens — completeness', 'leaves no debug printing', () => {
      if (/println\s*\(|System\.out\./.test(body)) {
        throw new Error('println survives into release and can leak whatever it prints');
      }
    });

    await c('Screens — design system', 'takes its colours from the theme', () => {
      // Raw hex bypasses the theme, so a colour cannot follow a contrast or
      // dark-mode change made centrally.
      const raw = [...body.matchAll(/Color\s*\(\s*0x[0-9A-Fa-f]{8}\s*\)/g)];
      if (raw.length) throw new Error(`${raw.length} hardcoded colour(s) bypass the theme`);
    });

    await c('Screens — resilience', 'logs no credentials', () => {
      if (/Log\.[dviwe]\s*\([^)]*\b(pin|password|token|accessToken)\b/i.test(body)) {
        throw new Error('a credential appears in a log call');
      }
    });
  }

  await H.check(rec, 'Screens — navigation', 'every nav route resolves to a screen', () => {
    const routes = [...routesFile.matchAll(/(?:data\s+)?object\s+(\w+)\s*:\s*Screen/g)].map(m => m[1]);
    if (routes.length < 10) throw new Error(`only ${routes.length} routes found — the route scan is not working`);
    const missing = routes.filter(r => !navGraph.includes(r));
    if (missing.length) throw new Error(`declared but never wired: ${missing.join(', ')}`);
  });
}

async function manifestContracts(rec) {
  const mf = stripComments(read(path.join(APP, 'src', 'main', 'AndroidManifest.xml')));
  const c = (what, fn) => H.check(rec, 'Manifest', what, fn);

  await c('locks the app to portrait', () => {
    if (!/screenOrientation\s*=\s*"portrait"/.test(mf)) {
      throw new Error('no portrait lock — D11 says rotation must not reflow a reading screen');
    }
  });
  await c('sets no app-wide cleartext flag', () => {
    if (/usesCleartextTraffic\s*=\s*"true"/.test(mf)) throw new Error('usesCleartextTraffic="true" is set');
  });
  await c('declares a network security config', () => {
    if (!/networkSecurityConfig/.test(mf)) throw new Error('no networkSecurityConfig');
  });
  await c('requests the microphone', () => {
    if (!/RECORD_AUDIO/.test(mf)) throw new Error('no RECORD_AUDIO — screening cannot record');
  });
  await c('requests internet access', () => {
    if (!/android\.permission\.INTERNET/.test(mf)) throw new Error('no INTERNET permission');
  });
  await c('requests no location permission', () => {
    if (/ACCESS_(FINE|COARSE)_LOCATION/.test(mf)) {
      throw new Error('location is requested but never needed — children\'s apps should ask for less');
    }
  });
  await c('requests no contacts permission', () => {
    if (/READ_CONTACTS|WRITE_CONTACTS/.test(mf)) throw new Error('contacts permission requested');
  });
  await c('requests no SMS permission', () => {
    if (/READ_SMS|SEND_SMS|RECEIVE_SMS/.test(mf)) throw new Error('SMS permission requested');
  });
  await c('exports exactly one launcher activity', () => {
    const exported = [...mf.matchAll(/android:exported\s*=\s*"true"/g)].length;
    if (exported === 0) throw new Error('nothing is exported, so the app has no launcher');
    if (exported > 1) throw new Error(`${exported} exported components — each is an entry point into the app`);
  });
  await c('allows no debuggable flag in the main manifest', () => {
    if (/android:debuggable\s*=\s*"true"/.test(mf)) throw new Error('debuggable="true" in the shipped manifest');
  });
  await c('disables backup of a child\'s local data', () => {
    if (/allowBackup\s*=\s*"true"/.test(mf)) {
      throw new Error('allowBackup="true" copies screening records into cloud backups');
    }
  });
}

async function buildContracts(rec) {
  const g = stripComments(read(path.join(APP, 'build.gradle.kts')));
  const c = (what, fn) => H.check(rec, 'Build configuration', what, fn);

  await c('reads the API base from local.properties', () => {
    if (!/API_BASE_URL/.test(g)) throw new Error('API_BASE_URL is not wired into the build');
  });
  await c('never hardcodes a LAN address in the build script', () => {
    const m = g.match(/192\.168\.[\d.]+|172\.\d+\.[\d.]+\.\d+/);
    if (m) throw new Error(`${m[0]} is baked into build.gradle.kts`);
  });
  await c('enables R8 for release', () => {
    if (!/isMinifyEnabled\s*=\s*true/.test(g)) throw new Error('release is not minified');
  });
  await c('configures signing', () => {
    if (!/signingConfigs/.test(g)) throw new Error('no signingConfigs block');
  });
  await c('splits by ABI', () => {
    if (!/splits\s*\{/.test(g)) throw new Error('no ABI splits, so every phone downloads every architecture');
  });
  await c('includes both shipping ABIs', () => {
    for (const abi of ['arm64-v8a', 'armeabi-v7a']) {
      if (!g.includes(abi)) throw new Error(`${abi} is not built`);
    }
  });
  await c('targets a recent SDK', () => {
    const m = g.match(/targetSdk\s*=\s*(\d+)/);
    if (!m) throw new Error('no targetSdk');
    if (Number(m[1]) < 34) throw new Error(`targetSdk ${m[1]} is below the Play requirement`);
  });
  await c('supports a realistic minimum SDK', () => {
    const m = g.match(/minSdk\s*=\s*(\d+)/);
    if (!m) throw new Error('no minSdk');
    if (Number(m[1]) > 26) throw new Error(`minSdk ${m[1]} excludes older classroom handsets`);
  });
  await c('keeps keystores out of git', () => {
    const gi = read(path.join(ROOT, 'ezhil-android', '.gitignore'));
    if (!/\.jks|\.keystore/.test(gi)) throw new Error('.gitignore does not exclude keystores');
  });
  await c('keeps local.properties out of git', () => {
    const gi = read(path.join(ROOT, 'ezhil-android', '.gitignore'));
    if (!/local\.properties/.test(gi)) throw new Error('local.properties is tracked');
  });
}

async function artifactContracts(rec) {
  const outputs = path.join(APP, 'build', 'outputs');
  const apks = [
    ['arm64 debug', 'apk/debug/app-arm64-v8a-debug.apk', 120],
    ['arm32 debug', 'apk/debug/app-armeabi-v7a-debug.apk', 110],
  ];
  for (const [label, rel, maxMB] of apks) {
    const p = path.join(outputs, rel);
    await H.check(rec, 'Artifacts', `${label} APK exists`, () => {
      if (!fs.existsSync(p)) throw new Error(`${rel} is missing — run assembleDebug`);
    });
    await H.check(rec, 'Artifacts', `${label} APK is under ${maxMB} MB`, () => {
      if (!fs.existsSync(p)) throw new Error(`${rel} is missing`);
      const mb = fs.statSync(p).size / 1048576;
      if (mb > maxMB) throw new Error(`${mb.toFixed(1)} MB exceeds ${maxMB} MB`);
    });
    await H.check(rec, 'Artifacts', `${label} APK is a real zip archive`, () => {
      if (!fs.existsSync(p)) throw new Error(`${rel} is missing`);
      const head = Buffer.alloc(4);
      const fd = fs.openSync(p, 'r');
      fs.readSync(fd, head, 0, 4, 0);
      fs.closeSync(fd);
      if (head[0] !== 0x50 || head[1] !== 0x4b) throw new Error('not a zip — the APK is corrupt');
    });
  }
}

function runUnitTests(rec) {
  const xml = path.join(APP, 'build', 'test-results', 'testDebugUnitTest');
  if (!fs.existsSync(xml)) {
    H.check(rec, 'Unit — android', 'unit test results exist', () => {
      throw new Error('no test-results directory — run ./gradlew testDebugUnitTest');
    });
    return 0;
  }
  let n = 0;
  for (const f of fs.readdirSync(xml).filter(f => f.endsWith('.xml'))) {
    const content = read(path.join(xml, f));
    for (const m of content.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
      const attrs = m[1], inner = m[2] || '';
      const name = (attrs.match(/\bname="([^"]*)"/) || [, '?'])[1];
      const cls = (attrs.match(/\bclassname="([^"]*)"/) || [, ''])[1];
      const time = parseFloat((attrs.match(/\btime="([^"]*)"/) || [, '0'])[1]) * 1000;
      const failed = /<(failure|error)\b/.test(inner);
      rec.record({
        category: 'Unit — android (JUnit)',
        name: cls ? `${cls}::${name}` : name,
        status: failed ? 'failed' : 'passed',
        durationMs: time,
        error: failed ? (inner.match(/message="([^"]*)"/) || [, 'failed'])[1] : '',
      });
      n++;
    }
  }
  return n;
}

async function main() {
  const rec = new H.Recorder(SUITE);
  console.log(`\n${SUITE}\n`);

  await screenContracts(rec);
  await manifestContracts(rec);
  await buildContracts(rec);
  await artifactContracts(rec);
  console.log(`  ${runUnitTests(rec)} unit cases imported`);

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
