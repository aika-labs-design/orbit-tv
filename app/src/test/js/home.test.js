const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const Home = require(path.join(__dirname, '../../main/assets/web/home.js'));
const festivals = [{ date: '2026-11-01', name: 'Kannada Rajyotsava', local: 'ಕನ್ನಡ ರಾಜ್ಯೋತ್ಸವ' }, { date: '2026-11-08', name: 'Deepavali', local: 'ದೀಪಾವಳಿ' }];
const WorldData = require(path.join(__dirname, '../../main/assets/web/world-data.js'));
let localData = {}; try { localData = JSON.parse(fs.readFileSync(path.join(__dirname, '../../main/assets/web/homes/local.json'), 'utf8')); } catch (e) {}
if (Home.loadLocal) Home.loadLocal(localData);
const languages = JSON.parse(fs.readFileSync(path.join(__dirname, '../../main/assets/languages.json'), 'utf8'));

// Tolu, as on the approved pictures: home Lagos, living in Berlin, Yoruba first.
const tolu = { id: 'p-tolu', name: 'Tolu', home: { country: 'NG', places: [{ name: 'Lagos', lat: 6.45, lon: 3.39, tz: 'Africa/Lagos' }] }, languages: ['yo', 'en', 'pcm', 'de'],
  living: { name: 'Berlin', lat: 52.52, lon: 13.4, tz: 'Europe/Berlin', country: 'DE' }, loves: [] };
const ravi = { id: 'p-ravi', name: 'Ravi', home: { country: 'IN', places: [{ name: 'Mysuru', lat: 12.30, lon: 76.64, tz: 'Asia/Kolkata' }] }, languages: ['kn', 'en'],
  living: { name: 'Toronto', lat: 43.65, lon: -79.38, tz: 'America/Toronto', country: 'CA' }, loves: [] };

test('home time comes from the home place time zone: India at UTC+05:30, Lagos at UTC+01:00', () => {
  assert.deepStrictEqual(Home.localNow('Asia/Kolkata', Date.UTC(2026, 9, 31, 19, 0)), { dateKey: '2026-11-01', hour: 0, hhmm: '00:30' });
  assert.deepStrictEqual(Home.localNow('Asia/Kolkata', Date.UTC(2026, 9, 31, 8, 5)), { dateKey: '2026-10-31', hour: 13, hhmm: '13:35' });
  assert.deepStrictEqual(Home.localNow('Africa/Lagos', Date.UTC(2026, 9, 31, 23, 10)), { dateKey: '2026-11-01', hour: 0, hhmm: '00:10' });
});

test('Home Mode greets Tolu in Yoruba only where languages.json has a verified greeting, else in English', () => {
  WorldData.load(languages);
  assert.strictEqual(Home.greet(tolu, 9, WorldData.greeting), 'Ẹ kú àárọ̀');
  assert.strictEqual(Home.greet(tolu, 20, WorldData.greeting), 'Ẹ kú alẹ́');
  WorldData.load(languages.map(l => l.code === 'yo' ? Object.assign({}, l, { greeting: null }) : l));
  assert.strictEqual(Home.greet(tolu, 9, WorldData.greeting), 'Good morning');
  WorldData.load(languages);
  assert.strictEqual(Home.greet(Object.assign({}, tolu, { languages: ['pcm', 'yo'] }), 15, WorldData.greeting), 'Good afternoon');   // Pidgin has none
  assert.strictEqual(Home.greet(null, 15, WorldData.greeting), '');
});

test('Home Mode opens the asked profile, else the person watching; never a stale id', () => {
  assert.strictEqual(Home.pick([tolu, ravi], 'p-ravi', 'p-tolu').id, 'p-ravi');
  assert.strictEqual(Home.pick([tolu, ravi], 'ka', 'p-ravi').id, 'p-ravi');
  assert.strictEqual(Home.pick([tolu, ravi], null, 'p-tolu').id, 'p-tolu');
  assert.strictEqual(Home.pick([tolu, ravi], null, null), null);         // nobody asked and nobody watching: getting ready, not someone else's home
  assert.strictEqual(Home.pick([tolu, ravi], 'ka', 'p-gone'), null);
  assert.strictEqual(Home.pick([], 'p-ravi', null), null);
  assert.strictEqual(Home.next([tolu, ravi], 'p-ravi').id, 'p-tolu');
  assert.strictEqual(Home.next([tolu], 'p-tolu'), null);
  // only a person who exists with a home place to visit
  assert.strictEqual(Home.next([tolu, Object.assign({}, ravi, { home: { country: 'IN', places: [] } })], 'p-tolu'), null);
  assert.strictEqual(Home.next([tolu, null], 'p-tolu'), null);
});

test('a home without places or feed data is getting ready, never borrowed from someone else', () => {
  const feed = { pack: { id: 'p-tolu', label: 'Nigeria' }, weather: [] };
  assert.strictEqual(Home.ready(tolu, feed), true);
  assert.strictEqual(Home.ready(tolu, undefined), false);
  assert.strictEqual(Home.ready(tolu, { pack: { id: 'p-ravi' } }), false);
  assert.strictEqual(Home.ready(Object.assign({}, tolu, { home: { country: 'NG', places: [] } }), feed), false);
  assert.strictEqual(Home.ready(null, feed), false);
});

test('home photographs use a standard Wikimedia width that fills the screen', () => {
  assert.strictEqual(Home.photoUrl('https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Lagos.jpg/1280px-Lagos.jpg'), 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Lagos.jpg/960px-Lagos.jpg');
  assert.strictEqual(Home.photoUrl(null), null);
});

test('next festival counts India days and stops at thirty', () => {
  assert.deepStrictEqual(Home.nextFestival(festivals, '2026-11-01'), { name: 'Kannada Rajyotsava', local: 'ಕನ್ನಡ ರಾಜ್ಯೋತ್ಸವ', days: 0 });
  assert.strictEqual(Home.nextFestival(festivals, '2026-10-31').days, 1);
  assert.strictEqual(Home.nextFestival([{ date: '2027-01-01', name: 'X', local: 'x' }], '2026-12-01'), null);
  assert.strictEqual(Home.nextFestival([], '2026-12-01'), null);
});

test('buckets, greetings and auto sound follow the home hour and the home weather', () => {
  assert.strictEqual(Home.bucket(6), 'dawn'); assert.strictEqual(Home.bucket(9), 'morning'); assert.strictEqual(Home.bucket(14), 'day');
  assert.strictEqual(Home.bucket(18), 'evening'); assert.strictEqual(Home.bucket(23), 'night'); assert.strictEqual(Home.bucket(2), 'night');
  assert.strictEqual(Home.autoSound('day', true, 'IN'), 'rain'); assert.strictEqual(Home.autoSound('dawn', false, 'IN'), 'temple');
  assert.strictEqual(Home.autoSound('morning', false, 'IN'), 'birds'); assert.strictEqual(Home.autoSound('evening', false, 'IN'), 'temple'); assert.strictEqual(Home.autoSound('day', false, 'IN'), 'birds'); assert.strictEqual(Home.autoSound('night', false, 'IN'), 'night');
});

test('the temple recording is Indian, so only an Indian home hears it; every other home gets the generic sounds', () => {
  assert.strictEqual(Home.autoSound('dawn', false, 'NG'), 'birds'); assert.strictEqual(Home.autoSound('evening', false, 'NG'), 'birds');
  assert.strictEqual(Home.autoSound('night', false, 'NG'), 'night'); assert.strictEqual(Home.autoSound('day', true, 'NG'), 'rain');
  assert.deepStrictEqual(Home.sounds('IN'), ['rain', 'temple', 'birds', 'night']);
  assert.deepStrictEqual(Home.sounds('NG'), ['rain', 'birds', 'night']);
});

const base = { label: 'India', country: 'IN', place: 'Mysuru', weather: [], here: null, festival: null, liveChannel: null, bucket: 'evening', hhmm: '19:30' };
const all = (ctx) => Home.lines(ctx).join(' | ');

test('festival lines say today and tomorrow', () => {
  assert.match(all({ ...base, festival: { name: 'Kannada Rajyotsava', local: '', days: 0 } }), /Happy Kannada Rajyotsava/);
  assert.match(all({ ...base, festival: { name: 'Kannada Rajyotsava', local: '', days: 1 } }), /Kannada Rajyotsava tomorrow/);
});

test('shared rain is noticed, named after the city the person lives in', () => {
  const ctx = { ...base, weather: [{ name: 'Mysuru', tempC: 22, desc: 'rain', isDay: false, wet: true }], here: { name: 'Toronto', desc: 'drizzle', tempC: 9 } };
  assert.match(all(ctx), /Rain in Toronto, and it's raining in Mysuru too/);
  assert.match(all(ctx), /22 degrees in Mysuru, 9 in Toronto/);
});

test('host lines for a home outside India say nothing about temples or chai', () => {
  const lagos = { ...base, label: 'Nigeria', country: 'NG', place: 'Lagos' };
  ['dawn', 'morning', 'day', 'evening', 'night'].forEach(b => assert.doesNotMatch(all({ ...lagos, bucket: b }), /temple|chai|coffee|Toronto/i, b));
  assert.match(all({ ...base, bucket: 'evening' }), /chai/);   // an Indian home keeps its evening
});

test('with no data at all there is still a clean line', () => {
  const out = Home.lines(base);
  assert.ok(out.length >= 1);
  out.forEach(l => assert.doesNotMatch(l, /undefined|null|NaN/));
});

test('sound is silent while Orbit is paused or the radio is on', () => {
  const base = { mode: 'auto', bucket: 'dawn', wet: false, radioOn: false, paused: false, country: 'IN' };
  assert.strictEqual(Home.soundFor(base), 'temple');
  assert.strictEqual(Home.soundFor({ ...base, paused: true }), '');
  assert.strictEqual(Home.soundFor({ ...base, radioOn: true }), '');
  assert.strictEqual(Home.soundFor({ ...base, mode: 'off' }), '');
  assert.strictEqual(Home.soundFor({ ...base, mode: 'rain' }), 'rain');
  assert.strictEqual(Home.soundFor({ ...base, mode: 'rain', paused: true }), '');
});

test('weather older than three hours is not presented as current', () => {
  const w = [{ name: 'Vijayawada', tempC: 24, desc: 'rain', isDay: false, wet: true }], now = Date.UTC(2026, 10, 1, 12);
  assert.deepStrictEqual(Home.freshWeather(w, now - 30 * 60000, now), w);
  assert.deepStrictEqual(Home.freshWeather(w, now - 4 * 3600000, now), []);
  assert.deepStrictEqual(Home.freshWeather(w, 0, now), []);
  assert.deepStrictEqual(Home.freshWeather(undefined, now, now), []);
});

test('local touches are bundled data: an Indian home gets the temple and its lines, a Nigerian home gets none', () => {
  assert.ok(localData.IN && localData.IN.sound === 'temple', 'homes/local.json has IN');
  assert.strictEqual(localData.NG, undefined);
  assert.deepStrictEqual(Home.sounds('IN'), ['rain', 'temple', 'birds', 'night']);
  assert.deepStrictEqual(Home.sounds('NG'), ['rain', 'birds', 'night']);
  const ctx = { label: 'x', place: 'P', weather: [], here: null, festival: null, liveChannel: null, hhmm: '06:00' };
  assert.match(Home.lines({ ...ctx, bucket: 'dawn', country: 'IN' }).join(' '), new RegExp(localData.IN.dawn.replace(/[.]/g, '\\.')));
  assert.doesNotMatch(Home.lines({ ...ctx, bucket: 'evening', country: 'NG' }).join(' '), new RegExp(localData.IN.evening.replace(/[.]/g, '\\.')));
});

test('home.js names no country: no country code literals, no temple or chai', () => {
  const src = fs.readFileSync(path.join(__dirname, '../../main/assets/web/home.js'), 'utf8');
  assert.doesNotMatch(src, /['"`][A-Z]{2}['"`]|\bIN\b\s*:/);
  assert.doesNotMatch(src, /temple|chai|filter coffee/i);
});

// TV: long Radio Browser names overflowed the Home Mode card
test('a station is named by its first part, its call signs and network left for the description', () => {
  assert.strictEqual(Home.stationName('88.9 Noticias - 88.9 FM - XHM-FM - Grupo ACIR - Ciudad de México'), '88.9 Noticias');
  assert.strictEqual(Home.stationName('Café Romántico Radio (Monterrey) - Online - www.caferomanticoradio.com'), 'Café Romántico Radio (Monterrey)');
  assert.strictEqual(Home.stationName('LOS 40 Principales España'), 'LOS 40 Principales España');
  assert.strictEqual(Home.stationName('  '), '');
});
