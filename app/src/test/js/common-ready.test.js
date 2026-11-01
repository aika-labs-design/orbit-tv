// ready() is token-gated on the TV: the page waits for the token the shell injects when loading finishes, then sends it.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../../main/assets/web');

test('the page sends ready with the page token once the token arrives', async () => {
  const readies = []; let onLoaded = null;
  const window = {
    Wellbeing: { summary: () => ({ windDown: false }), nudges: () => [] },
    Personal: { personas: ['a'], insights: () => [], hostLine: () => '' },
    Sky: { load: () => new Promise(() => {}) },
    Feeds: { hostLine: () => '' },
    Android: { ready: (...a) => { readies.push(a); }, log: () => {} },
    addEventListener: () => {},
  };
  const ctx = Object.assign(window, {
    window, console, JSON, Math, Date, Promise, String, Array, Object, Set,
    setTimeout, clearTimeout, setInterval: () => 0,
    localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
    fetch: () => new Promise(() => {}),
    document: { addEventListener: (ev, fn) => { if (ev === 'DOMContentLoaded') onLoaded = fn; }, getElementById: (id) => id === 'root' ? { appendChild: () => {} } : null, createElement: () => ({}), documentElement: { style: { setProperty: () => {} } }, body: { classList: { toggle: () => {} } } },
  });
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(web, 'common.js'), 'utf8'), ctx);
  onLoaded();
  await new Promise(r => setTimeout(r, 150));
  assert.deepStrictEqual(readies, []);   // no token yet: nothing sent
  window.__orbitToken = 'tok';
  await new Promise(r => setTimeout(r, 150));
  assert.deepStrictEqual(readies, [['tok']]);
});

test('the page never gives up on the token: a slow page load still sends ready with it', () => {
  const readies = []; let onLoaded = null; const timers = [];
  const window = {
    Wellbeing: { summary: () => ({ windDown: false }), nudges: () => [] },
    Personal: { personas: ['a'], insights: () => [], hostLine: () => '' },
    Sky: { load: () => new Promise(() => {}) },
    Feeds: { hostLine: () => '' },
    Android: { ready: (...a) => { readies.push(a); }, log: () => {} },
    addEventListener: () => {},
  };
  const ctx = Object.assign(window, {
    window, console, JSON, Math, Date, Promise, String, Array, Object, Set,
    setTimeout: (fn) => { timers.push(fn); return timers.length; }, clearTimeout: () => {}, setInterval: () => 0,
    localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
    fetch: () => new Promise(() => {}),
    document: { addEventListener: (ev, fn) => { if (ev === 'DOMContentLoaded') onLoaded = fn; }, getElementById: (id) => id === 'root' ? { appendChild: () => {} } : null, createElement: () => ({}), documentElement: { style: { setProperty: () => {} } }, body: { classList: { toggle: () => {} } } },
  });
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(web, 'common.js'), 'utf8'), ctx);
  onLoaded();
  for (let i = 0; i < 300 && timers.length; i++) timers.shift()();   // 30 s of 100 ms waits
  assert.deepStrictEqual(readies, []);   // still waiting, never an empty token
  window.__orbitToken = 'tok';
  while (timers.length) timers.shift()();
  assert.deepStrictEqual(readies, [['tok']]);
});
