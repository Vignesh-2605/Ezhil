import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Tamil must never be letter-spaced.
 *
 * Tamil letters combine into conjuncts; tracking pulls those apart and the
 * result reads as broken glyphs rather than words. It is a cosmetic slip in
 * most apps and an accessibility failure in this one, where the readers are
 * children with dyslexia.
 *
 * The trap is bilingual labels — "Date Range / தேதி வரம்பு" — where tracking is
 * added for the uppercase English half and silently applies to the Tamil too.
 * Thirteen such labels existed before this test.
 */

const SRC = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const TAMIL = /[஀-௿]/;
/**
 * Any tracking, in either direction. Negative tracking is not the safe option
 * it looks like: `tracking-tight` pulls Tamil glyphs together until the vowel
 * signs and the pulli collide with their neighbours. Only `tracking-normal`
 * is acceptable on Tamil.
 */
const TRACKING = /tracking-(?!normal)[a-z[]/;
/**
 * A Tamil face implies Tamil content even when the text is a variable.
 * `font-bilingual-sub` is deliberately absent — it maps to DM Sans, a Latin
 * face used for the English half of a bilingual pair.
 */
const TAMIL_FONT = /font-(display|body|reader)-tamil/;

/**
 * The text belonging to the element that opens on [start].
 *
 * JSX splits an element across lines, so checking a single line misses the
 * usual shape — className on one line, the Tamil on the next. Reading a fixed
 * window instead over-reports: a Latin heading gets blamed for Tamil in the
 * element below it. This walks to the element's own closing tag and stops.
 */
function elementText(lines: string[], start: number): string {
  if (lines[start].includes('</')) return lines[start];   // self-contained
  const out = [lines[start]];
  for (let i = start + 1; i < Math.min(start + 6, lines.length); i++) {
    out.push(lines[i]);
    if (lines[i].includes('</')) break;
  }
  return out.join(' ');
}

function tsxFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(entry => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return tsxFiles(full);
    return full.endsWith('.tsx') ? [full] : [];
  });
}

describe('Tamil typography', () => {
  const files = tsxFiles(SRC);

  it('scans a realistic number of files', () => {
    // Guard the guard. If the path were wrong this suite would find nothing
    // and pass forever while protecting nothing at all.
    expect(files.length).toBeGreaterThan(30);
  });

  it('sees the Tamil it is meant to be checking', () => {
    // Likewise: if the Tamil pattern stopped matching, every scan would come
    // back clean for the wrong reason.
    const withTamil = files.filter(f => TAMIL.test(readFileSync(f, 'utf-8')));
    expect(withTamil.length).toBeGreaterThan(10);
  });

  it('flags letter-spacing on Tamil when it is present', () => {
    const sample = '<p className="uppercase tracking-wider">பொருள்</p>';
    expect(TRACKING.test(sample) && TAMIL.test(sample)).toBe(true);
  });

  it('no element applies letter-spacing to Tamil text', () => {
    const offenders: string[] = [];

    for (const file of files) {
      const lines = readFileSync(file, 'utf-8').split('\n');
      lines.forEach((line, i) => {
        if (!TRACKING.test(line)) return;
        if (TAMIL.test(elementText(lines, i)) || TAMIL_FONT.test(line)) {
          offenders.push(`${file.slice(SRC.length)}:${i + 1}`);
        }
      });
    }

    expect(offenders, `letter-spacing applied to Tamil in:\n${offenders.join('\n')}`)
      .toEqual([]);
  });
});
