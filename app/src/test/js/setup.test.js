const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const assets = path.join(__dirname, '../../main/assets');
const read = (f) => JSON.parse(fs.readFileSync(path.join(assets, f), 'utf8'));
const K = require(path.join(assets, 'web/keyboard.js'));
const Setup = require(path.join(assets, 'web/setup.js'));
const Profiles = require(path.join(assets, 'web/profiles.js'));
const world = { countries: read('countries.json'), languages: read('languages.json') };
const interests = read('interests.json');
Setup.load({ countries: world.countries, languages: world.languages, interests });

const press = (s, ...keys) => keys.reduce((x, k) => Setup.next(x, k), s);
// Type on whichever on-screen keyboard is open, walking the focus with arrows like the remote does.
function type(s, text) {
  for (const ch of text) s = tapKey(s, ch);
  return s;
}
function tapKey(s, label) {
  const at = K.find(label); assert.ok(at, 'no key ' + label);
  let guard = 40;
  while ((s.kb.r !== at.r || s.kb.c !== at.c) && guard--) s = Setup.next(s, s.kb.r < at.r ? 'ArrowDown' : s.kb.r > at.r ? 'ArrowUp' : s.kb.c < at.c ? 'ArrowRight' : 'ArrowLeft');
  return Setup.next(s, 'Enter');
}
const until = (s, key, done) => { for (let i = 0; i < 80 && !done(s); i++) s = Setup.next(s, key); assert.ok(done(s), 'reached'); return s; };
const chip = (s, code) => Setup.chips(s).indexOf(code);
// Walk the loves grid to a tile: rows with Down, then columns with Right (Right on an unloved tile never opens a panel).
function gridTo(s, id) {
  const at = Setup.tiles(s).findIndex(t => t.id === id); assert.ok(at >= 0, id);
  s = until(s, 'ArrowDown', x => Math.floor(x.f.i / 4) === Math.floor(at / 4));
  return until(s, s.f.i < at ? 'ArrowRight' : 'ArrowLeft', x => x.f.i === at);
}
function chipTo(s, code) { const want = chip(s, code); assert.ok(want >= 0, code); return until(s, want > s.f.i ? 'ArrowRight' : 'ArrowLeft', x => x.f.i === want); }

// Tolu: name, home Lagos (Nigeria), on a TV whose clock says Berlin.
function toLanguages() {
  let s = Setup.init({ tz: 'Europe/Berlin' });
  assert.strictEqual(s.step, 'name');
  s = type(s, 'tolu'); s = tapKey(s, 'done');
  assert.strictEqual(s.name, 'Tolu'); assert.strictEqual(s.step, 'home');
  assert.strictEqual(s.f.zone, 'search');
  assert.strictEqual(Setup.countryAt(s).code, 'DE', 'the list starts at the country the TV is in');
  s = press(s, 'Enter');                                    // the search pill opens the keyboard
  s = type(s, 'nigeria'); s = tapKey(s, 'done');
  assert.strictEqual(Setup.countryAt(s).code, 'NG');
  s = press(s, 'Enter');                                    // Nigeria's cities
  assert.strictEqual(s.f.zone, 'cities');
  assert.strictEqual(Setup.cityRows(s)[0].name, 'Lagos');
  s = press(s, 'Enter');                                    // Lagos
  assert.deepStrictEqual(s.home.places.map(p => p.name), ['Lagos']);
  s = until(s, 'ArrowRight', x => x.f.zone === 'next');
  s = press(s, 'Enter');
  assert.strictEqual(s.step, 'languages');
  return s;
}
function pickLanguages(s) {
  for (const code of ['yo', 'en', 'ig', 'de']) { s = chipTo(s, code); s = press(s, 'Enter'); }
  return s;
}

test('Back from languages returns to home with choices kept', () => {
  let s = pickLanguages(toLanguages());
  assert.deepStrictEqual(s.languages, ['yo', 'en', 'ig', 'de']);
  assert.strictEqual(s.living.name, 'Berlin'); assert.strictEqual(s.living.country, 'DE');
  s = press(s, 'Back');
  assert.strictEqual(s.step, 'home');
  assert.strictEqual(s.home.country, 'NG');
  assert.deepStrictEqual(s.home.places.map(p => p.name), ['Lagos']);
  assert.strictEqual(Setup.countryAt(s).code, 'NG');
  assert.deepStrictEqual(s.languages, ['yo', 'en', 'ig', 'de']);
  s = until(s, 'ArrowRight', x => x.f.zone === 'next'); s = press(s, 'Enter');
  assert.strictEqual(s.step, 'languages'); assert.deepStrictEqual(s.languages, ['yo', 'en', 'ig', 'de']);
});

test('the language chips put the home country first, then where you live, then More', () => {
  const s = toLanguages(), c = Setup.chips(s);
  assert.deepStrictEqual(c.slice(0, 2), ['en', 'ha']);
  assert.ok(c.indexOf('ig') < c.indexOf('de'), 'Igbo (Nigeria) before German (Berlin)');
  assert.strictEqual(c[c.length - 1], 'more');
  assert.strictEqual(Setup.chipName('yo'), 'Yorùbá');
});

test('skipping loves still produces a valid profile', () => {
  let s = until(toLanguages(), 'ArrowDown', x => x.f.zone === 'next'); s = press(s, 'Enter');
  assert.strictEqual(s.step, 'loves');
  s = until(s, 'ArrowDown', x => x.f.zone === 'next'); s = press(s, 'Enter');
  assert.strictEqual(s.step, 'summary');
  const p = Setup.profile(s, { id: 'p-tolu', now: 1761955200000 });
  assert.deepStrictEqual(Profiles.validate(p), []);
  assert.deepStrictEqual(p.loves, []);
  assert.deepStrictEqual(p.languages, ['en'], 'no language picked: the home country first language');
  assert.strictEqual(p.living.name, 'Berlin');
});

test('skipping home and languages still gives a valid profile from where the TV is', () => {
  let s = Setup.init({ tz: 'Europe/Berlin' });
  s = type(s, 'ann'); s = tapKey(s, 'done');
  for (const step of ['home', 'languages', 'loves']) { assert.strictEqual(s.step, step); s = until(s, 'ArrowRight', x => x.f.zone === 'next' || x.f.zone === 'panel'); if (s.f.zone !== 'next') s = until(s, 'ArrowDown', x => x.f.zone === 'next'); s = press(s, 'Enter'); if (step === 'home') s = press(s, 'Enter'); }   // home: the first Next picks and shows the city
  const p = Setup.profile(s, { id: 'p-ann', now: 1 });
  assert.deepStrictEqual(Profiles.validate(p), []);
  assert.strictEqual(p.home.country, 'DE'); assert.strictEqual(p.home.places[0].name, 'Berlin'); assert.strictEqual(p.living.name, 'Berlin');
});

test('Left/Right on a picked language chip reorders priority', () => {
  let s = pickLanguages(toLanguages());
  s = until(s, 'ArrowDown', x => x.f.zone === 'next'); s = press(s, 'Enter');
  s = until(s, 'ArrowDown', x => x.f.zone === 'next'); s = press(s, 'Enter');
  assert.strictEqual(s.step, 'summary'); assert.strictEqual(s.f.zone, 'langs');
  s = press(s, 'ArrowRight');                                 // focus English (second)
  s = press(s, 'Enter');                                      // pick it up
  assert.ok(s.f.grabbed);
  s = press(s, 'ArrowLeft');
  assert.deepStrictEqual(s.languages, ['en', 'yo', 'ig', 'de']);
  s = press(s, 'ArrowRight', 'ArrowRight');
  assert.deepStrictEqual(s.languages, ['yo', 'ig', 'en', 'de']);
  s = press(s, 'Enter');                                      // put it down; arrows move focus again
  s = press(s, 'ArrowLeft');
  assert.deepStrictEqual(s.languages, ['yo', 'ig', 'en', 'de']); assert.strictEqual(s.f.i, 1);
});

test('loves: Select loves a tile, Right on it opens the follow-up, Search anything adds a custom love', () => {
  let s = pickLanguages(toLanguages());
  s = until(s, 'ArrowDown', x => x.f.zone === 'next'); s = press(s, 'Enter');
  assert.strictEqual(s.step, 'loves'); assert.strictEqual(s.f.zone, 'grid');
  assert.strictEqual(Setup.tiles(s)[0].id, 'afrobeats', 'home (Nigeria) first');
  s = gridTo(s, 'football');
  s = press(s, 'Enter', 'ArrowRight');
  assert.strictEqual(s.f.zone, 'panel');
  const opts = Setup.panel(s).options.map(o => o.id);
  s = until(s, 'ArrowRight', x => opts[x.f.pi] === 'arsenal'); s = press(s, 'Enter');
  s = until(s, 'ArrowRight', x => opts[x.f.pi] === 'super-eagles'); s = press(s, 'Enter');
  s = until(s, 'ArrowLeft', x => x.f.zone === 'grid');
  const after = Setup.tiles(s)[s.f.i + 1].id;
  s = press(s, 'ArrowRight');                                  // after saying more, Right moves on to the next tile
  assert.strictEqual(Setup.tiles(s)[s.f.i].id, after);
  s = press(s, 'Enter');
  assert.deepStrictEqual(s.loves, [{ id: 'football', refine: ['arsenal', 'super-eagles'] }, { id: after, refine: [] }]);
  s = until(s, 'ArrowUp', x => x.f.zone === 'tabs');
  s = until(s, 'ArrowRight', x => Setup.TABS[x.f.tab].id === 'search');
  s = press(s, 'Enter'); s = type(s, 'jollof rice'); s = tapKey(s, 'done');
  const t = Setup.tiles(s);
  assert.strictEqual(t[t.length - 1].id, 'custom:jollof-rice');
  s = until(s, 'ArrowRight', x => x.f.i === t.length - 1); s = press(s, 'Enter');
  assert.deepStrictEqual(s.loves[2], { id: 'custom:jollof-rice', refine: [], custom: 'Jollof rice' });
});

test('summary for Tolu names Yorùbá films, Arsenal and Super Eagles live, Lagos news', () => {
  const tolu = {
    id: 'p-tolu', name: 'Tolu', created: 1,
    home: { country: 'NG', places: [{ name: 'Lagos', lat: 6.45, lon: 3.39, tz: 'Africa/Lagos' }] },
    languages: ['yo', 'en', 'pcm', 'de'],
    living: { name: 'Berlin', lat: 52.52, lon: 13.41, tz: 'Europe/Berlin', country: 'DE' },
    loves: [{ id: 'football', refine: ['arsenal', 'super-eagles'] }, { id: 'afrobeats', refine: [] }, { id: 'west-african-cooking', refine: [] }, { id: 'fashion', refine: [] }, { id: 'custom:chess-boxing', refine: [], custom: 'Chess boxing' }],
  };
  const lines = Setup.summary(tolu, world, interests), all = lines.join('\n');
  assert.match(all, /films[^\n]*Yorùbá|Yorùbá[^\n]*films/i);
  assert.match(all, /Nollywood/);
  assert.match(all, /then English, Naijá and Deutsch/);
  assert.match(all, /Arsenal and Super Eagles[^\n]*live/);
  assert.match(all, /news from Lagos/);
  assert.match(all, /Berlin/);
  assert.match(all, /Afrobeats, West African cooking and Fashion/);
  assert.match(all, /Chess boxing[^\n]*less tuned/);
});

test('the living city is guessed from the TV time zone', () => {
  assert.deepStrictEqual(Setup.guessLiving('Europe/Berlin', world.countries), { name: 'Berlin', lat: 52.52, lon: 13.41, tz: 'Europe/Berlin', country: 'DE' });
  assert.strictEqual(Setup.guessLiving('Africa/Lagos', world.countries).name, 'Lagos');
  assert.strictEqual(Setup.guessLiving('Mars/Base', world.countries), null);
});

test('custom love ids follow the native rule for Latin words', () => {
  assert.deepStrictEqual(Setup.customLove('  Chess   Boxing '), { id: 'custom:chess-boxing', refine: [], custom: 'Chess Boxing' });
  assert.strictEqual(Setup.customLove('Café culture').id, 'custom:cafe-culture');
});

test('saved loves are stamped with when they were chosen', () => {
  const s = Object.assign(until(toLanguages(), 'ArrowDown', x => x.f.zone === 'next'), {});
  const p = Setup.profile(Object.assign({}, s, { loves: [{ id: 'football', refine: ['arsenal'] }, { id: 'custom:x', refine: [], custom: 'X' }] }), { id: 'p', now: 1761955200000 });
  assert.deepStrictEqual(p.loves.map(l => l.since), [1761955200000, 1761955200000]);
});

test('Change my setup starts from the saved profile and keeps its id, start date and what Orbit learned', () => {
  const was = { id: 'p-tolu', name: 'Tolu', created: 1000,
    home: { country: 'NG', places: [{ name: 'Lagos', lat: 6.45, lon: 3.39, tz: 'Africa/Lagos' }] }, languages: ['yo', 'en'],
    living: { name: 'Berlin', lat: 52.52, lon: 13.41, tz: 'Europe/Berlin', country: 'DE' },
    loves: [{ id: 'football', refine: ['arsenal'], weight: 2.5, since: 2000 }, { id: 'afrobeats', refine: [], since: 3000 }] };
  let s = Setup.init({ tz: 'Europe/Berlin', from: was });
  assert.strictEqual(s.name, 'Tolu'); assert.strictEqual(s.home.country, 'NG'); assert.deepStrictEqual(s.languages, ['yo', 'en']);
  assert.strictEqual(s.living.name, 'Berlin', 'not the TV guess'); assert.strictEqual(s.livingSure, true);
  assert.deepStrictEqual(s.loves.map(l => l.id), ['football', 'afrobeats']);
  s = Object.assign({}, s, { loves: s.loves.filter(l => l.id !== 'afrobeats').concat([{ id: 'fashion', refine: [] }]) });
  const p = Setup.profile(s, { id: was.id, now: 9000, was });
  assert.strictEqual(p.id, 'p-tolu'); assert.strictEqual(p.created, 1000);
  assert.deepStrictEqual(p.loves, [{ id: 'football', refine: ['arsenal'], since: 2000, weight: 2.5 }, { id: 'fashion', refine: [], since: 9000 }]);
  assert.deepStrictEqual(Profiles.validate(p), []);
});

// QA (Ana): Right/Next from a highlighted Mexico without a city went on and silently made the city where the TV is her home.
test('Next from a highlighted country picks that country\'s first city and shows it, never another place', () => {
  let s = Setup.init({ tz: 'Europe/Berlin' });
  s = type(s, 'ana'); s = tapKey(s, 'done');
  s = press(s, 'ArrowDown');
  s = until(s, Setup.countryAt(s).name < 'Mexico' ? 'ArrowDown' : 'ArrowUp', x => Setup.countryAt(x).code === 'MX');
  s = press(s, 'ArrowRight'); assert.strictEqual(s.f.zone, 'next');
  s = press(s, 'Enter');
  assert.strictEqual(s.step, 'home', 'shown before moving on');
  assert.strictEqual(s.home.country, 'MX');
  assert.deepStrictEqual(s.home.places.map(p => p.name), [world.countries.find(c => c.code === 'MX').cities[0].name]);
  s = press(s, 'Enter');
  assert.strictEqual(s.step, 'languages');
  assert.notStrictEqual(Setup.profile(s, { id: 'p', now: 1 }).home.places[0].name, 'Berlin');
});

// QA: from row 150 of 249 countries only Up, one row at a time, reached Search
test('Left from the country list jumps to Search', () => {
  let s = Setup.init({ tz: 'Europe/Berlin' });
  s = type(s, 'ana'); s = tapKey(s, 'done');
  s = press(s, 'ArrowDown', 'ArrowDown', 'ArrowDown'); assert.strictEqual(s.f.zone, 'list');
  s = press(s, 'ArrowLeft'); assert.strictEqual(s.f.zone, 'search');
});

// QA (Ana, home Mexico, living in Canada): Popular near you led with Afrobeats and West African cooking; Mexican food was 28th in Food.
test('loves list the home and living countries\' cuisines and music first, then the world-generic ones, then other countries\'', () => {
  const ana = { step: 'loves', home: { country: 'MX', places: [] }, living: { country: 'CA' }, loves: [], loveQuery: '', mem: {}, f: { zone: 'grid', tab: 0, i: 0 } };
  const ids = (tab) => Setup.tiles(ana, tab).map(t => t.id);
  const pop = ids('popular');
  assert.deepStrictEqual(pop.slice(0, 2), ['mexican-food', 'latin-music'], 'Mexico\'s cuisine and music lead Popular near you');
  assert.ok(pop.indexOf('football') < pop.indexOf('afrobeats') && pop.indexOf('jazz') < pop.indexOf('west-african-cooking'), 'world-generic before other countries\'');
  assert.strictEqual(ids('food')[0], 'mexican-food');
  assert.strictEqual(ids('music')[0], 'latin-music');
  assert.ok(ids('food').indexOf('weeknight-meals') < ids('food').indexOf('nigerian-food'));
});

// QA: Change my setup put the focus on the "a" key, so Select typed "Anaa"
test('Change my setup starts on Done with the name kept, and says so', () => {
  const was = { id: 'p-ana', name: 'Ana', created: 1, home: { country: 'MX', places: [{ name: 'Mexico City', lat: 19.43, lon: -99.13, tz: 'America/Mexico_City' }] }, languages: ['es', 'en'],
    living: { name: 'Toronto', lat: 43.65, lon: -79.38, tz: 'America/Toronto', country: 'CA' }, loves: [] };
  let s = Setup.init({ tz: 'America/Toronto', from: was });
  assert.strictEqual(s.editing, true);
  const done = K.find('done'); assert.deepStrictEqual([s.kb.r, s.kb.c], [done.r, done.c]);
  s = press(s, 'Enter');
  assert.strictEqual(s.name, 'Ana'); assert.strictEqual(s.step, 'home');
  assert.strictEqual(Setup.init({ tz: 'America/Toronto' }).editing, false);
});

// QA (Ana loves Tennis): "kick-off times" for tennis, and a follow-up offering only "Indian players"
test('sports name their own start times, and refine options are generic unless they belong to the person\'s countries', () => {
  assert.strictEqual(Setup.startWord(['tennis']), 'match times');
  assert.strictEqual(Setup.startWord(['formula-1']), 'race times');
  assert.strictEqual(Setup.startWord(['football']), 'kick-off times');
  assert.strictEqual(Setup.startWord(['tennis', 'football']), 'start times');
  const p = { languages: ['es'], home: { country: 'MX', places: [{ name: 'Mexico City' }] }, living: { name: 'Toronto', country: 'CA' }, loves: [{ id: 'tennis', refine: [] }] };
  assert.ok(Setup.summary(p).some(l => l === 'Tennis live, with match times in Toronto time'));
  const at = (home) => ({ step: 'loves', home: { country: home, places: [] }, living: { country: 'CA' }, loves: [{ id: 'tennis', refine: [] }], loveQuery: '', mem: {}, panelId: 'tennis',
    f: { zone: 'grid', tab: Setup.TABS.findIndex(t => t.id === 'sport'), i: 0 } });
  const opts = (home) => Setup.panel(at(home)).options.map(o => o.id);
  assert.ok(opts('MX').includes('top-players') && !opts('MX').includes('indian-tennis'));
  assert.ok(opts('IN').includes('indian-tennis'));
});

// review: sport wording was app code; it comes from the catalogue (interests.json live.startWord) now
test('a sport\'s start word is catalogue data, start times without one', () => {
  Setup.load({ countries: world.countries, languages: world.languages, interests: interests.concat([{ id: 'sumo', name: 'Sumo', theme: 'sport', live: { kind: 'espn', league: 'x/y', startWord: 'bout times' } }, { id: 'kabaddi', name: 'Kabaddi', theme: 'sport', live: { kind: 'espn', league: 'x/z' } }]) });
  assert.strictEqual(Setup.startWord(['sumo']), 'bout times');
  assert.strictEqual(Setup.startWord(['kabaddi']), 'start times');
  Setup.load({ countries: world.countries, languages: world.languages, interests });
});
