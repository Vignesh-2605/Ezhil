#!/usr/bin/env node
/**
 * Build the Excel workbook and HTML report from whatever suites have run.
 *
 * Reads the per-suite JSON each suite writes and produces one workbook and one
 * page covering all of them. Durations are reported exactly as measured. A
 * fast assertion reads as fast: substituting a random few milliseconds when the
 * real figure is small would make a report of no-ops indistinguishable from a
 * report of real work, which is the whole value of having the report.
 */
const fs = require('node:fs');
const path = require('node:path');
const ExcelJS = require('exceljs');

const DIR = __dirname;
const OUT_XLSX = path.join(DIR, 'execution-report.xlsx');
const OUT_HTML = path.join(DIR, 'execution-report.html');
const OUT_JSON = path.join(DIR, 'execution-report.json');

const SUITE_FILES = [
  'web-functional.json',
  'web-ui-a11y.json',
  'api-contract.json',
  'android-static.json',
  'android-appium.json',
  'load-matrix.json',
];

function loadSuites() {
  const out = [];
  for (const f of SUITE_FILES) {
    const p = path.join(DIR, f);
    if (!fs.existsSync(p)) continue;
    try {
      const d = JSON.parse(fs.readFileSync(p, 'utf-8'));
      if (d.summary && Array.isArray(d.rows)) out.push(d);
    } catch (err) {
      console.warn(`  skipping ${f}: ${err.message}`);
    }
  }
  return out;
}

const pct = n => `${(n * 100).toFixed(1)}%`;

/**
 * A suite name Excel will accept as a tab.
 *
 * Excel caps sheet names at 31 characters and rejects : \ / ? * [ ]. Two of
 * ours are over the cap — "Selenium — Website UI & Accessibility" is 37 — so
 * they are trimmed rather than silently rejected at write time. The `used` set
 * keeps names unique if trimming makes two collide.
 */
function sheetName(raw, used) {
  let name = String(raw).replace(/[:\/?*[\]]/g, '-').trim();
  if (name.length > 31) name = name.slice(0, 31).trim();
  let candidate = name || 'Suite';
  let n = 2;
  while (used.has(candidate)) {
    const suffix = ` ${n++}`;
    candidate = `${name.slice(0, 31 - suffix.length)}${suffix}`;
  }
  used.add(candidate);
  return candidate;
}

async function buildExcel(suites, totals) {
  const wb = new ExcelJS.Workbook();
  wb.creator = 'Ezhil test suite';
  wb.created = new Date();

  const HEAD = { bold: true, color: { argb: 'FFFFFFFF' } };
  const HEAD_FILL = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF0F6B68' } };

  const styleHeader = row => {
    row.font = HEAD;
    row.fill = HEAD_FILL;
    row.alignment = { vertical: 'middle' };
    row.height = 22;
  };

  // ── Sheet 1: every assertion ───────────────────────────────────────────────
  const s1 = wb.addWorksheet('Test Report', { views: [{ state: 'frozen', ySplit: 1 }] });
  s1.columns = [
    { header: 'Suite', key: 'suite', width: 34 },
    { header: 'Category', key: 'category', width: 30 },
    { header: 'Test case', key: 'name', width: 62 },
    { header: 'Status', key: 'status', width: 10 },
    { header: 'Duration (ms)', key: 'durationMs', width: 15 },
    { header: 'Error', key: 'error', width: 70 },
    { header: 'Recorded at (UTC)', key: 'at', width: 24 },
  ];
  styleHeader(s1.getRow(1));
  for (const s of suites) for (const r of s.rows) s1.addRow(r);
  s1.getColumn('durationMs').numFmt = '0.000';
  s1.autoFilter = { from: 'A1', to: 'G1' };
  s1.eachRow((row, i) => {
    if (i === 1) return;
    const failed = row.getCell('status').value === 'failed';
    row.getCell('status').font = { bold: true, color: { argb: failed ? 'FFB4453C' : 'FF2F7D4F' } };
    if (failed) row.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF7E7E5' } };
  });

  // ── Sheet 2: by testing type ───────────────────────────────────────────────
  const s2 = wb.addWorksheet('Testing Types Summary');
  s2.columns = [
    { header: 'Testing type', key: 'category', width: 40 },
    { header: 'Suite', key: 'suite', width: 34 },
    { header: 'Total', key: 'total', width: 10 },
    { header: 'Passed', key: 'passed', width: 10 },
    { header: 'Failed', key: 'failed', width: 10 },
    { header: 'Pass rate', key: 'rate', width: 12 },
    { header: 'Total duration (ms)', key: 'ms', width: 20 },
  ];
  styleHeader(s2.getRow(1));
  const byCat = new Map();
  for (const s of suites) {
    for (const r of s.rows) {
      const key = `${s.summary.suite}||${r.category}`;
      const c = byCat.get(key) || { suite: s.summary.suite, category: r.category, total: 0, passed: 0, failed: 0, ms: 0 };
      c.total++;
      if (r.status === 'passed') c.passed++; else c.failed++;
      c.ms += r.durationMs;
      byCat.set(key, c);
    }
  }
  for (const c of [...byCat.values()].sort((a, b) => b.total - a.total)) {
    s2.addRow({ ...c, ms: Number(c.ms.toFixed(3)), rate: pct(c.total ? c.passed / c.total : 0) });
  }

  // ── Sheet 3: suite roll-up ─────────────────────────────────────────────────
  const s3 = wb.addWorksheet('Run Summary');
  s3.columns = [
    { header: 'Suite', key: 'suite', width: 40 },
    { header: 'Total', key: 'total', width: 10 },
    { header: 'Passed', key: 'passed', width: 10 },
    { header: 'Failed', key: 'failed', width: 10 },
    { header: 'Pass rate', key: 'rate', width: 12 },
    { header: 'Started (UTC)', key: 'startedAt', width: 24 },
    { header: 'Finished (UTC)', key: 'finishedAt', width: 24 },
  ];
  styleHeader(s3.getRow(1));
  for (const s of suites) {
    s3.addRow({ ...s.summary, rate: pct(s.summary.passRate) });
  }
  s3.addRow({});
  const totalRow = s3.addRow({
    suite: 'ALL SUITES', total: totals.total, passed: totals.passed,
    failed: totals.failed, rate: pct(totals.total ? totals.passed / totals.total : 0),
  });
  totalRow.font = { bold: true };

  // ── One tab per suite ─────────────────────────────────────────────────────
  // The roll-up sheets answer "did it pass"; these answer "what ran, and what
  // exactly failed" without filtering a combined sheet of ~1,600 rows.
  const used = new Set(['Test Report', 'Testing Types Summary', 'Run Summary']);
  for (const s of suites) {
    const sh = wb.addWorksheet(sheetName(s.summary.suite, used), {
      views: [{ state: 'frozen', ySplit: 1 }],
    });
    sh.columns = [
      { header: 'Category', key: 'category', width: 32 },
      { header: 'Test case', key: 'name', width: 74 },
      { header: 'Status', key: 'status', width: 10 },
      { header: 'Duration (ms)', key: 'durationMs', width: 15 },
      { header: 'Error', key: 'error', width: 70 },
      { header: 'Recorded at (UTC)', key: 'at', width: 24 },
    ];
    styleHeader(sh.getRow(1));
    for (const r of s.rows) sh.addRow(r);
    sh.getColumn('durationMs').numFmt = '0.000';
    sh.autoFilter = { from: 'A1', to: 'F1' };
    sh.eachRow((row, i) => {
      if (i === 1) return;
      const failed = row.getCell('status').value === 'failed';
      row.getCell('status').font = { bold: true, color: { argb: failed ? 'FFB4453C' : 'FF2F7D4F' } };
      if (failed) row.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF7E7E5' } };
    });
    // A tab that is all green should say so at a glance.
    const tabColour = s.summary.failed > 0 ? 'FFB4453C' : 'FF2F7D4F';
    sh.properties.tabColor = { argb: tabColour };
  }

  await wb.xlsx.writeFile(OUT_XLSX);
}

/**
 * The same run as machine-readable JSON.
 *
 * The workbook is for a person; this is for anything that needs to diff two
 * runs or feed a dashboard. Both are written from one in-memory result, so
 * they cannot disagree.
 */
function buildJson(suites, totals) {
  fs.writeFileSync(OUT_JSON, JSON.stringify({
    generatedAt: new Date().toISOString(),
    totals,
    suites: suites.map(s => ({ summary: s.summary, rows: s.rows })),
  }, null, 2));
}

function buildHtml(suites, totals) {
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const failures = suites.flatMap(s => s.rows.filter(r => r.status === 'failed'));

  const suiteCards = suites.map(s => `
    <article class="suite ${s.summary.failed ? 'bad' : 'ok'}">
      <h3>${esc(s.summary.suite)}</h3>
      <p class="big">${s.summary.passed}<span>/${s.summary.total}</span></p>
      <p class="sub">${s.summary.failed} failed · ${pct(s.summary.passRate)} pass rate</p>
      <p class="sub mono">${esc(s.summary.finishedAt || '')}</p>
    </article>`).join('');

  const byCat = new Map();
  for (const s of suites) for (const r of s.rows) {
    const c = byCat.get(r.category) || { total: 0, passed: 0, ms: 0 };
    c.total++; if (r.status === 'passed') c.passed++; c.ms += r.durationMs;
    byCat.set(r.category, c);
  }
  const catRows = [...byCat.entries()].sort((a, b) => b[1].total - a[1].total).map(([k, c]) => `
    <tr><td>${esc(k)}</td><td class="num">${c.total}</td><td class="num">${c.passed}</td>
    <td class="num">${c.total - c.passed}</td><td class="num">${pct(c.passed / c.total)}</td>
    <td class="num">${c.ms.toFixed(1)}</td></tr>`).join('');

  const failRows = failures.length ? failures.map(f => `
    <tr><td>${esc(f.suite)}</td><td>${esc(f.name)}</td><td>${esc(f.error)}</td></tr>`).join('')
    : '<tr><td colspan="3" class="none">No failures in this run.</td></tr>';

  return `<title>Ezhil Test Execution</title>
<style>
  :root{--bg:#10171A;--surface:#172023;--sunk:#131B1E;--ink:#E8EDED;--soft:#BCC6C7;--muted:#8A9698;
        --rule:#2A3538;--accent:#5FCFC9;--ok:#7FC79B;--bad:#F09A91;--bad-bg:#33201E}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.6 ui-sans-serif,system-ui,sans-serif}
  .wrap{max-width:1120px;margin:0 auto;padding:3rem 1.5rem 5rem}
  h1{font-size:2.2rem;font-weight:800;letter-spacing:-.02em;margin:0}
  .eyebrow{font-family:ui-monospace,monospace;font-size:.72rem;letter-spacing:.14em;
           text-transform:uppercase;color:var(--accent);margin:0 0 .7rem}
  .headline{margin-top:1rem;color:var(--soft)}
  .totals{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:1px;background:var(--rule);
          border:1px solid var(--rule);border-radius:10px;overflow:hidden;margin:2rem 0}
  .totals div{background:var(--surface);padding:1.1rem}
  .totals .n{font-size:2rem;font-weight:800;line-height:1;font-variant-numeric:tabular-nums}
  .totals .l{font-size:.78rem;color:var(--muted);margin-top:.35rem}
  .suites{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:.9rem;margin:2rem 0}
  .suite{background:var(--surface);border:1px solid var(--rule);border-left:3px solid var(--ok);
         border-radius:8px;padding:1rem 1.1rem}
  .suite.bad{border-left-color:var(--bad)}
  .suite h3{font-size:.95rem;margin:0 0 .5rem;font-weight:700}
  .big{font-size:1.9rem;font-weight:800;margin:0;font-variant-numeric:tabular-nums}
  .big span{font-size:1rem;color:var(--muted);font-weight:600}
  .sub{font-size:.8rem;color:var(--muted);margin:.2rem 0 0}
  .mono{font-family:ui-monospace,monospace;font-size:.7rem}
  h2{font-size:1.25rem;margin:2.6rem 0 .8rem;padding-bottom:.5rem;border-bottom:1px solid var(--rule)}
  .scroller{overflow-x:auto;border:1px solid var(--rule);border-radius:8px;background:var(--surface)}
  table{border-collapse:collapse;width:100%;min-width:560px;font-size:.9rem}
  th,td{text-align:left;padding:.6rem .9rem;border-bottom:1px solid var(--rule);vertical-align:top}
  tbody tr:last-child td{border-bottom:none}
  th{background:var(--sunk);font-size:.7rem;letter-spacing:.08em;text-transform:uppercase;color:var(--muted)}
  td.num{text-align:right;font-variant-numeric:tabular-nums;font-family:ui-monospace,monospace;font-size:.84rem}
  td.none{color:var(--muted);text-align:center;padding:1.4rem}
  .note{color:var(--muted);font-size:.85rem;margin-top:2.5rem;max-width:70ch}
</style>
<div class="wrap">
  <p class="eyebrow">Ezhil · எழில் — test execution</p>
  <h1>${totals.failed === 0 ? 'All suites green' : `${totals.failed} failing`}</h1>
  <p class="headline">${totals.total} assertions across ${suites.length} suite${suites.length === 1 ? '' : 's'}.
     Durations are as measured — nothing is substituted when a figure is small.</p>

  <div class="totals">
    <div><div class="n">${totals.total}</div><div class="l">Assertions</div></div>
    <div><div class="n" style="color:var(--ok)">${totals.passed}</div><div class="l">Passed</div></div>
    <div><div class="n" style="color:${totals.failed ? 'var(--bad)' : 'var(--muted)'}">${totals.failed}</div><div class="l">Failed</div></div>
    <div><div class="n">${pct(totals.total ? totals.passed / totals.total : 0)}</div><div class="l">Pass rate</div></div>
    <div><div class="n">${(totals.ms / 1000).toFixed(1)}s</div><div class="l">Measured time</div></div>
  </div>

  <div class="suites">${suiteCards}</div>

  <h2>By testing type</h2>
  <div class="scroller"><table>
    <thead><tr><th>Type</th><th class="num">Total</th><th class="num">Passed</th>
    <th class="num">Failed</th><th class="num">Pass rate</th><th class="num">Duration (ms)</th></tr></thead>
    <tbody>${catRows}</tbody>
  </table></div>

  <h2>Failures</h2>
  <div class="scroller"><table>
    <thead><tr><th>Suite</th><th>Test case</th><th>Error</th></tr></thead>
    <tbody>${failRows}</tbody>
  </table></div>

  <p class="note">Generated ${new Date().toISOString()} from the JSON each suite writes.
     Suites that have not run are absent rather than reported as passing.</p>
</div>`;
}

async function main() {
  const suites = loadSuites();
  if (!suites.length) {
    console.error('No suite results found in reports/. Run a suite first.');
    process.exit(2);
  }
  const totals = suites.reduce((a, s) => ({
    total: a.total + s.summary.total,
    passed: a.passed + s.summary.passed,
    failed: a.failed + s.summary.failed,
    ms: a.ms + s.summary.totalDurationMs,
  }), { total: 0, passed: 0, failed: 0, ms: 0 });

  await buildExcel(suites, totals);
  fs.writeFileSync(OUT_HTML, buildHtml(suites, totals));
  buildJson(suites, totals);

  console.log(`\nReport built from ${suites.length} suite(s)`);
  for (const s of suites) {
    console.log(`  ${s.summary.suite.padEnd(40)} ${s.summary.passed}/${s.summary.total}`);
  }
  console.log(`  ${'TOTAL'.padEnd(40)} ${totals.passed}/${totals.total}`);
  console.log(`\n  ${OUT_XLSX}`);
  console.log(`  ${OUT_HTML}`);
}

main().catch(err => { console.error(err); process.exit(1); });
