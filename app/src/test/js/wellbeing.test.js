// Android counts screen time for the whole TV: every line about it says so, never as the viewer's own.
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const vm = require('node:vm');
const ctx = { window: {} }; vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/assets/web/wellbeing.js'), 'utf8'), ctx);
const W = ctx.window.Wellbeing;

test('screen-time lines and nudges speak of this TV, not of the viewer', () => {
  const heavy = { today: 300, week: 900, perDay: 130, reason: 'heavy' }, light = { today: 30, week: 200, perDay: 30, reason: null };
  assert.match(W.line(heavy), /ON THIS TV/);
  for (const p of ['butler', 'trailer', 'reluctant', 'mission', 'fortune']) for (const w of [heavy, light]) {
    const n = W.nudges(w, p).join(' ');
    assert.match(n, /this TV|THIS TV|this television/i, p + ': ' + n);
  }
  const calm = W.calmPick([{ label: 'Spotify' }], { windDown: true, reason: 'heavy', today: 300 });
  assert.match(calm.reason, /this TV/);
});
