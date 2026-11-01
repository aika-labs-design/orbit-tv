// Shell.saveProfiles runs one save at a time; a queued save is built from the list the TV pushed back last.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../../main/assets/web');

function shell() {
  const sent = [], storage = {};
  const window = {
    Wellbeing: { summary: () => ({ windDown: false }), nudges: () => [] },
    Personal: { personas: ['a'], insights: () => [], hostLine: () => '' },
    Sky: { load: () => new Promise(() => {}) },
    Feeds: { hostLine: () => '' },
    __orbitToken: 't',
    // the TV saves, then pushes the saved list back a moment later
    Android: { saveProfiles: (json) => { sent.push(JSON.parse(json)); setTimeout(() => window.orbit.setProfiles(JSON.parse(json)), 5); }, log: () => {} },
    addEventListener: () => {},
  };
  const ctx = Object.assign(window, {
    window, console, JSON, Math, Date, Promise, String, Array, Object, Set,
    setTimeout, clearTimeout, setInterval: () => 0,
    localStorage: { getItem: (k) => (k in storage ? storage[k] : null), setItem: (k, v) => { storage[k] = String(v); }, removeItem: (k) => { delete storage[k]; } },
    document: { addEventListener: () => {}, getElementById: () => null, documentElement: { style: { setProperty: () => {} } }, body: { classList: { toggle: () => {} } } },
  });
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(web, 'common.js'), 'utf8'), ctx);
  return { Shell: ctx.Shell, orbit: ctx.orbit, sent };
}

const tolu = { id: 'p-tolu', name: 'Tolu', languages: ['yo'], loves: [{ id: 'football', refine: [] }] };

test('an accept followed by a weight save keeps the accepted love', async () => {
  const { Shell, orbit, sent } = shell();
  orbit.setProfiles([tolu]);
  const accept = Shell.saveProfiles(list => list.map(p => Object.assign({}, p, { loves: p.loves.concat([{ id: 'street-food', refine: [] }]) })));
  const weights = Shell.saveProfiles(list => list.map(p => Object.assign({}, p, { loves: p.loves.map(l => Object.assign({}, l, { weight: 2 })) })));
  assert.deepStrictEqual(await Promise.all([accept, weights]), [true, true]);
  assert.strictEqual(sent.length, 2);
  const last = Shell.profiles()[0];
  assert.deepStrictEqual(last.loves.map(l => l.id), ['football', 'street-food']);
  assert.ok(last.loves.every(l => l.weight === 2));
});

test('a plain list is still saved as given', async () => {
  const { Shell, orbit, sent } = shell();
  orbit.setProfiles([]);
  assert.strictEqual(await Shell.saveProfiles([tolu]), true);
  assert.strictEqual(sent[0][0].name, 'Tolu');
});

test('the same profiles pushed again do not ask every page to redraw', () => {
  const { Shell, orbit } = shell();
  let data = 0, profiles = 0; Shell.on('data', () => data++); Shell.on('profiles', () => profiles++);
  orbit.setProfiles([tolu]); orbit.setProfiles(JSON.parse(JSON.stringify([tolu])));
  assert.strictEqual(data, 1); assert.strictEqual(profiles, 2);
  orbit.setProfiles([Object.assign({}, tolu, { name: 'Tolú' })]);
  assert.strictEqual(data, 2);
});
