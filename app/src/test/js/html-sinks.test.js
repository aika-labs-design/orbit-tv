// Feed text (Cricinfo, ESPN, TMDB, YouTube, RSS) must never reach markup unescaped: the pages run next to the native bridge.
// This scans every page and script for a bare `${a.b}` interpolation of a feed-looking field on a line that builds markup.
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');

const WEB = path.join(__dirname, '../../main/assets/web');
const RISKY = /\.(title|line1|line2|art|rank|name|desc|tag|state|image|thumb|label|sub|venue|source|channel|short|url)$/;

function findings() {
  const out = [];
  for (const f of fs.readdirSync(WEB).filter(f => /\.(js|html)$/.test(f) && f !== 'three.min.js')) {
    fs.readFileSync(path.join(WEB, f), 'utf8').split('\n').forEach((line, n) => {
      if (!/<[a-z]/i.test(line)) return;   // only lines that build markup
      for (const m of line.matchAll(/\$\{\s*([\w.]+)\s*\}/g)) if (RISKY.test(m[1])) out.push(`${f}:${n + 1} \${${m[1]}}`);
    });
  }
  return out;
}

test('no feed field is interpolated into markup without Shell.esc', () => {
  assert.deepStrictEqual(findings(), []);
});
