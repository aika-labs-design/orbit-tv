const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const Profiles = require(path.join(__dirname, '../../main/assets/web/profiles.js'));
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '../resources/feeds/profiles-seed.json'), 'utf8'));
const mem = (init) => { const m = Object.assign({}, init); return { getItem: k => (k in m ? m[k] : null), setItem: (k, v) => { m[k] = String(v); }, removeItem: k => { delete m[k]; }, key: i => Object.keys(m)[i] || null, get length() { return Object.keys(m).length; }, dump: () => m }; };

test('current picks the last used profile, else the first, else null', () => {
  assert.strictEqual(Profiles.current(seed, 'p-bo').id, 'p-bo');
  assert.strictEqual(Profiles.current(seed, 'gone').id, 'p-ann');
  assert.strictEqual(Profiles.current(seed, null).id, 'p-ann');
  assert.strictEqual(Profiles.current([], 'p-bo'), null);
  assert.strictEqual(Profiles.current(null, 'p-bo'), null);
});

test('validate accepts the seed and reports every problem', () => {
  assert.deepStrictEqual(Profiles.validate(seed[0]), []);
  const bad = { id: '', name: '', home: { country: '', places: [] }, languages: [], living: { name: 'x' }, loves: 'x' };
  const errs = Profiles.validate(bad);
  for (const k of ['id', 'name', 'home.country', 'home.places', 'languages', 'living', 'loves']) assert.ok(errs.some(e => e.startsWith(k)), k + ' in ' + errs);
  const four = JSON.parse(JSON.stringify(seed[0])); four.home.places = [1, 2, 3, 4].map(i => ({ name: 'p' + i, lat: 1, lon: 2, tz: 'UTC' }));
  assert.ok(Profiles.validate(four).some(e => e.startsWith('home.places')));
});

test('a profile whose first language has only a 639-3 code validates', () => {
  const p = JSON.parse(JSON.stringify(seed[0])); p.languages = ['pcm', 'en'];
  assert.deepStrictEqual(Profiles.validate(p), []);
  p.languages = ['english'];
  assert.ok(Profiles.validate(p).some(e => e.startsWith('languages')), 'codes are 2 or 3 letters');
});

test('a love with a numeric since validates, a non-numeric since does not', () => {
  const ok = Object.assign({}, seed[0], { loves: [{ id: 'a', refine: [], since: 5 }] });
  assert.deepStrictEqual(Profiles.validate(ok), []);
  assert.ok(Profiles.validate(Object.assign({}, seed[0], { loves: [{ id: 'a', refine: [], since: 'x' }] })).some(e => e.startsWith('loves')));
});

// QA (Ana, home Mexico, with Ravi, home India, in the house): the wall said "Mexico channels" over Indian channels and an India cricket ticker.
test('channels and live cards are the current person\'s only: their home country, their loved sports; nothing of another person\'s', () => {
  const ana = { id: 'p-ana', home: { country: 'MX' } }, ravi = { id: 'p-ravi', home: { country: 'IN' } };
  const channels = [{ id: 'a', countries: ['IN'] }, { id: 'b', countries: ['MX'] }, { id: 'c', countries: [] }, { id: 'd', countries: ['IN', 'MX'] }];
  assert.deepStrictEqual(Profiles.channelsFor(ana, channels).map(c => c.id), ['b', 'd']);
  assert.deepStrictEqual(Profiles.channelsFor(ravi, channels).map(c => c.id), ['a', 'd']);   // a channel listed for both homes belongs to both
  assert.deepStrictEqual(Profiles.channelsFor(null, channels), []);
  const live = [{ id: 'x', sport: 'cricket' }, { id: 'y', sport: 'tennis' }];
  const sportsBy = [{ id: 'p-ravi', sports: ['cricket'] }, { id: 'p-ana', sports: ['tennis'] }];
  assert.deepStrictEqual(Profiles.liveFor(ana, live, sportsBy).map(c => c.id), ['y']);
  assert.deepStrictEqual(Profiles.liveFor(ravi, live, sportsBy).map(c => c.id), ['x']);
  assert.deepStrictEqual(Profiles.liveFor({ id: 'p-new' }, live, sportsBy), []);   // loves no sport: nothing
  assert.deepStrictEqual(Profiles.liveFor(null, live, sportsBy), []);
});

// review: the screensaver fell back to the first person's sports when the current id was missing
test('the screensaver shows the exact current person\'s sports, nothing when that person is unknown', () => {
  const live = [{ id: 'x', sport: 'cricket' }, { id: 'y', sport: 'tennis' }];
  const sportsBy = [{ id: 'p-ravi', sports: ['cricket'] }, { id: 'p-ana', sports: ['tennis'] }];
  assert.deepStrictEqual(Profiles.liveOf('p-ana', live, sportsBy).map(c => c.id), ['y']);
  assert.deepStrictEqual(Profiles.liveOf(null, live, sportsBy), []);
  assert.deepStrictEqual(Profiles.liveOf('p-gone', live, sportsBy), []);
});
