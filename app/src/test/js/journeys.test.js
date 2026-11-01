const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const J = require(path.join(__dirname, '../../main/assets/web/journeys.js'));
const places = JSON.parse(fs.readFileSync(path.join(__dirname, '../../main/assets/web/places.json'), 'utf8'));
const AT = Date.UTC(2026, 9, 31, 21, 52);   // Saturday 31 Oct 2026, 21:52 UTC

test('local time comes from the place time zone, across date lines and DST', () => {
  assert.deepStrictEqual(J.localTime('Asia/Tokyo', AT), { hhmm: '06:52', weekday: 'Sunday', hour: 6 + 52 / 60 });
  assert.strictEqual(J.localTime('Pacific/Auckland', AT).hhmm, '10:52');      // NZDT, UTC+13
  assert.strictEqual(J.localTime('Pacific/Honolulu', AT).hhmm, '11:52');      // no DST, UTC-10
  assert.strictEqual(J.localTime('Pacific/Honolulu', AT).weekday, 'Saturday');
  assert.strictEqual(J.localTime('Asia/Kolkata', AT).hhmm, '03:22');
});

test('phases follow the sun', () => {
  assert.strictEqual(J.phase(6.9, 6.2, 17.3), 'dawn');
  assert.strictEqual(J.phase(12, 6.2, 17.3), 'day');
  assert.strictEqual(J.phase(16.5, 6.2, 17.3), 'golden');
  assert.strictEqual(J.phase(21, 6.2, 17.3), 'night');
});

test('awake now puts dawn first, then golden hour, then lit nights; sleeping places drop out', () => {
  const ps = [{ id: 'a', tz: 'Asia/Kolkata' }, { id: 'tokyo', tz: 'Asia/Tokyo' }, { id: 'ny', tz: 'America/New_York' }, { id: 'lon', tz: 'Europe/London' }];
  assert.deepStrictEqual(J.awakeNow(ps, AT).map(p => p.id), ['tokyo', 'ny', 'lon']);   // Tokyo 06:52 dawn, NY 17:52 golden, London 21:52 night; Kolkata 03:22 asleep
});

test('the globe projects places for a frame and hides the far side', () => {
  const c = J.project(0, 0, 0, 0); assert.ok(Math.abs(c.x - 0.5) < 1e-9 && Math.abs(c.y - 0.5) < 1e-9 && c.visible);
  assert.strictEqual(J.project(0, 180, 0, 0).visible, false);
  const k = J.project(35, 135.75, J.frameFor(135.75) * 5, 20); assert.ok(k.visible && Math.abs(k.x - 0.5) < 0.02 && k.y < 0.5);
  assert.strictEqual(J.frameFor(0), 0); assert.strictEqual(J.frameFor(77.6), 16); assert.strictEqual(J.frameFor(-74), 57); assert.strictEqual(J.frameFor(359), 0);
});

test('the modes are fixed', () => {
  assert.deepStrictEqual(J.MODES, ['walk', 'drive', 'food', 'festivals', 'nature', 'stays', 'live']);
});

test('the curated places are complete and valid', () => {
  assert.ok(places.length >= 44);
  places.forEach(p => { assert.ok(p.id && p.name && p.country && p.tz && Array.isArray(p.tags), p.id); assert.ok(Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180, p.id); J.localTime(p.tz, AT); });
  ['bengaluru', 'mysuru', 'hampi', 'varanasi', 'kyoto', 'times-square'].forEach(id => assert.ok(places.some(p => p.id === id), id));
});

test('a live cam must be of the place it claims, country included', () => {
  const venice = { name: 'Venice', country: 'Italy' }, dubai = { name: 'Dubai', country: 'United Arab Emirates' };
  assert.strictEqual(J.liveQuery(venice), 'Venice Italy live cam');
  assert.strictEqual(J.isOf({ title: 'Venice Italy live: Grand Canal', channel: 'X' }, venice), true);
  assert.strictEqual(J.isOf({ title: 'Los Angeles Live Cam · Venice Beach', channel: 'X' }, venice), false);   // another country's Venice
  assert.strictEqual(J.isOf({ title: 'LIVE WEBCAM from LANZAROTE AIRPORT', channel: 'X' }, dubai), false);
  assert.strictEqual(J.isOf({ title: 'Dubai Marina live', channel: 'X' }, dubai), true);
});

test('history keeps one entry per place, newest first; ten minutes makes it loved', () => {
  const kyoto = { id: 'kyoto', name: 'Kyoto', tags: ['heritage'] }, hampi = { id: 'hampi', name: 'Hampi', tags: ['heritage'] };
  let h = J.remember([], kyoto, 'walk', 3, AT);
  assert.deepStrictEqual(h.map(e => [e.id, e.mode, e.loved]), [['kyoto', 'walk', false]]);
  h = J.remember(h, hampi, 'nature', 12, AT + 1);
  h = J.remember(h, kyoto, 'food', 11, AT + 2);
  assert.deepStrictEqual(h.map(e => [e.id, e.mode, e.loved]), [['kyoto', 'food', true], ['hampi', 'nature', true]]);
  h = J.remember(h, kyoto, 'walk', 1, AT + 3);
  assert.strictEqual(h[0].loved, true);   // once loved, stays loved
  for (let i = 0; i < 40; i++) h = J.remember(h, { id: 'p' + i, name: 'P', tags: [] }, 'walk', 1, AT + 10 + i);
  assert.strictEqual(h.length, 30);
});

test('smallImg asks Wikimedia for a standard 500 px width and YouTube for the medium thumbnail', () => {
  const w = 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Sydney.jpg/1280px-Sydney.jpg?utm_source=x';
  assert.strictEqual(J.smallImg(w), 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Sydney.jpg/500px-Sydney.jpg?utm_source=x');
  assert.strictEqual(J.smallImg('https://i.ytimg.com/vi/abc/maxresdefault.jpg'), 'https://i.ytimg.com/vi/abc/mqdefault.jpg');
  assert.strictEqual(J.smallImg('https://i.ytimg.com/vi/abc/hqdefault_live.jpg'), 'https://i.ytimg.com/vi/abc/hqdefault_live.jpg');
  assert.strictEqual(J.smallImg(null), null);
});

test('a reply only settles the request it belongs to: same page, same id, same kind', () => {
  const got = [];
  const a = J.requestBook('pageA'), b = J.requestBook('pageB');
  const ida = a.add('place', (r) => got.push(['a', r.op]));
  const idb = b.add('videos', (r) => got.push(['b', r.op]));
  assert.notStrictEqual(ida, idb);
  assert.strictEqual(b.settle({ id: ida, op: 'place' }), false);   // an old page's reply arriving late
  assert.strictEqual(b.settle({ id: idb, op: 'place' }), false);   // wrong kind of answer
  assert.strictEqual(b.settle({ id: idb, op: 'videos' }), true);
  assert.strictEqual(b.settle({ id: idb, op: 'videos' }), false);  // settled once
  assert.deepStrictEqual(got, [['b', 'videos']]);
});

const tolu = { id: 'p-tolu', name: 'Tolu', home: { country: 'NG', places: [{ name: 'Lagos', lat: 6.45, lon: 3.39, tz: 'Africa/Lagos' }] }, languages: ['yo', 'en'],
  living: { name: 'Berlin', lat: 52.52, lon: 13.4, tz: 'Europe/Berlin', country: 'DE' }, loves: [] };
const ravi = { id: 'p-ravi', name: 'Ravi', home: { country: 'IN', places: [{ name: 'Mumbai', lat: 19.08, lon: 72.88, tz: 'Asia/Kolkata' }, { name: 'Mysore', lat: 12.30, lon: 76.65, tz: 'Asia/Kolkata' }] }, languages: ['kn'],
  living: { name: 'Toronto', lat: 43.65, lon: -79.38, tz: 'America/Toronto', country: 'CA' }, loves: [] };

test('no curated place is anyone\'s home: homes come from the profile at runtime', () => {
  assert.deepStrictEqual(places.filter(p => p.tags.includes('home')).map(p => p.id), []);
});

test('the Your homes shelf for Tolu is Lagos, and nobody else\'s home is tagged', () => {
  const all = J.withHomes(places, tolu, 'Nigeria'), homes = all.filter(p => p.tags.includes('home'));
  assert.deepStrictEqual(homes.map(p => [p.name, p.country, p.tz]), [['Lagos', 'Nigeria', 'Africa/Lagos']]);
  assert.strictEqual(all.length, places.length + 1);
  assert.ok(!all.find(p => p.id === 'mumbai').tags.includes('home'));
  assert.strictEqual(places.find(p => p.id === 'mumbai').tags.includes('home'), false);   // the curated list is not changed
});

test('a home that is a curated place reuses it (same id, so its history and photo carry over), in the profile\'s order', () => {
  const all = J.withHomes(places, ravi, 'India'), homes = all.filter(p => p.tags.includes('home'));
  assert.deepStrictEqual(homes.map(p => p.id), ['mumbai', 'mysuru']);   // Mysore is Mysuru by distance
  assert.strictEqual(all.length, places.length);
  assert.deepStrictEqual(J.withHomes(places, null, '').filter(p => p.tags.includes('home')), []);
});

test('the journey clock reads the city the person lives in', () => {
  assert.strictEqual(J.livingClock(tolu, AT), 'Berlin 22:52');
  assert.strictEqual(J.livingClock(ravi, AT), 'Toronto 17:52');
  assert.strictEqual(J.livingClock(null, AT), '');
});

// Small towns that were once curated; a town that is someone's home comes from their profile now.
const REMOVED = ['tirupati', 'visakhapatnam', 'gokarna', 'coorg'];

test('places.json is world-iconic places: small towns are gone and never reach anyone\'s Awake now', () => {
  REMOVED.forEach(id => assert.ok(!places.some(p => p.id === id), id));
  const all = J.withHomes(places, tolu, 'Nigeria').filter(p => !p.tags.includes('home'));
  for (let h = 0; h < 24; h++) J.awakeNow(all, Date.UTC(2026, 10, 1, h)).forEach(p => assert.ok(!REMOVED.includes(p.id), p.id));
});

test('a profile whose home is Vijayawada has Vijayawada in Your homes', () => {
  const asha = { id: 'p-asha', name: 'Asha', home: { country: 'IN', places: [{ name: 'Vijayawada', lat: 16.51, lon: 80.65, tz: 'Asia/Kolkata' }] }, languages: ['te'],
    living: { name: 'Toronto', lat: 43.65, lon: -79.38, tz: 'America/Toronto', country: 'CA' }, loves: [] };
  const homes = J.withHomes(places, asha, 'India').filter(p => p.tags.includes('home'));
  assert.deepStrictEqual(homes.map(p => [p.name, p.country, p.tz]), [['Vijayawada', 'India', 'Asia/Kolkata']]);
});

test('every journey search names the place\'s country, and footage of a namesake elsewhere is dropped', () => {
  const lagos = { name: 'Lagos', country: 'Nigeria' };
  assert.strictEqual(J.searchName(lagos), 'Lagos Nigeria');
  assert.strictEqual(J.searchName({ name: 'Paris, Île-de-France' }), 'Paris');
  const countries = ['Nigeria', 'Portugal', 'Ghana', 'Niger', 'Japan'];
  const found = [
    { title: 'Lagos Portugal walking tour 4K', channel: 'Algarve Walks' },
    { title: 'Walking in Lagos Nigeria - Africa\'s biggest city', channel: 'Fragile Rovers' },
    { title: 'Balogun Market, Lagos walking tour', channel: 'Naija Walks' },
    { title: 'Discover Lagos Old Town | Algarve, Portugal', channel: 'X' },
    { title: 'Abuja city walk', channel: 'Y' },
  ];
  assert.deepStrictEqual(found.filter(v => J.isOf(v, lagos, countries)).map(v => v.channel), ['Fragile Rovers', 'Naija Walks']);
  assert.strictEqual(J.isOf({ title: 'Kyoto walk 4K', channel: 'Z' }, { name: 'Kyoto', country: 'Japan' }, countries), true);
});

test('curated places name their country as countries.json does, so their own country never counts as elsewhere', () => {
  const names = new Set(JSON.parse(fs.readFileSync(path.join(__dirname, '../../main/assets/countries.json'), 'utf8')).map(c => c.name));
  places.forEach(p => assert.ok(names.has(p.country), p.id + ' ' + p.country));
  const istanbul = places.find(p => p.id === 'istanbul');
  assert.strictEqual(J.isOf({ title: 'Istanbul Turkey walking tour 4K', channel: 'X' }, istanbul, [...names]), true);
});

const ALIASES = (() => { try { return JSON.parse(fs.readFileSync(path.join(__dirname, '../../main/assets/web/aliases.json'), 'utf8')); } catch (e) { return []; } })();
const COUNTRY_NAMES = JSON.parse(fs.readFileSync(path.join(__dirname, '../../main/assets/countries.json'), 'utf8')).map(c => c.name);

test('a place is found under its well-known other names: Bombay for Mumbai, Mysore for Mysuru', () => {
  const bom = places.find(p => p.id === 'mumbai'), mys = places.find(p => p.id === 'mysuru'), vns = places.find(p => p.id === 'varanasi');
  assert.strictEqual(J.isOf({ title: 'Bombay walking tour 4K', channel: 'X' }, bom, COUNTRY_NAMES), true);
  assert.strictEqual(J.isOf({ title: 'Bombay street walk, Lisbon Portugal', channel: 'X' }, bom, COUNTRY_NAMES), false);   // still not another country's
  assert.strictEqual(J.isOf({ title: 'Mysore Palace at night', channel: 'X' }, mys, COUNTRY_NAMES), true);
  assert.strictEqual(J.isOf({ title: 'Banaras ghats at dawn', channel: 'X' }, vns, COUNTRY_NAMES), true);
  assert.strictEqual(J.isOf({ title: 'Delhi street food', channel: 'X' }, mys, COUNTRY_NAMES), false);
});

test('a Mumbai home keeps Bombay footage; a runtime home gets its other names from the bundled table', () => {
  const asBom = J.withHomes(places, ravi, 'India', ALIASES).find(p => p.id === 'mumbai');
  assert.ok(asBom.tags.includes('home'));
  assert.strictEqual(J.isOf({ title: 'Bombay walking tour 4K', channel: 'X' }, asBom, COUNTRY_NAMES), true);
  const meera = { id: 'p-m', name: 'Meera', home: { country: 'IN', places: [{ name: 'Kochi', lat: 9.93, lon: 76.27, tz: 'Asia/Kolkata' }] }, languages: ['ml'],
    living: { name: 'Toronto', lat: 43.65, lon: -79.38, tz: 'America/Toronto', country: 'CA' }, loves: [] };
  const kochi = J.withHomes(places, meera, 'India', ALIASES).find(p => p.tags.includes('home'));
  assert.ok((kochi.aliases || []).includes('Cochin'));
  assert.strictEqual(J.isOf({ title: 'Fort Cochin walking tour', channel: 'X' }, kochi, COUNTRY_NAMES), true);
  const bombay = Object.assign({}, ravi, { home: { country: 'IN', places: [{ name: 'Bombay', lat: 19.0, lon: 72.9, tz: 'Asia/Kolkata' }] } });
  assert.strictEqual(J.withHomes(places, bombay, 'India', ALIASES)[0].id, 'mumbai');   // an old name finds the curated place too
});

test('the alias table is groups of names for one place, and curated aliases are well formed', () => {
  assert.ok(Array.isArray(ALIASES) && ALIASES.length >= 10);
  ALIASES.forEach(g => assert.ok(Array.isArray(g) && g.length >= 2 && g.every(n => typeof n === 'string' && n.trim()), JSON.stringify(g)));
  ['Mysuru', 'Varanasi', 'Mumbai', 'Kochi', 'Chennai', 'Kolkata', 'Beijing', 'Kyiv', 'Yangon', 'Ho Chi Minh City'].forEach(n => assert.ok(ALIASES.some(g => g.includes(n)), n));
  places.forEach(p => assert.ok(p.aliases === undefined || (Array.isArray(p.aliases) && p.aliases.every(a => typeof a === 'string')), p.id));
});

// QA: Drive mode played a walking tour first
test('each mode keeps footage of its own kind first and drops the other kind', () => {
  const v = (title) => ({ id: title, title });
  const list = [v('Oaxaca 4K walking tour'), v('Driving Oaxaca city at night 4K'), v('Oaxaca road trip dashcam'), v('Oaxaca 4K')];
  assert.deepStrictEqual(J.forMode(list, 'drive').map(x => x.id), ['Driving Oaxaca city at night 4K', 'Oaxaca road trip dashcam', 'Oaxaca 4K']);
  assert.deepStrictEqual(J.forMode(list, 'walk').map(x => x.id), ['Oaxaca 4K walking tour', 'Oaxaca 4K']);
  assert.deepStrictEqual(J.forMode(list, 'food'), list);
});

// QA: the labels of Mexico City (home) and Oaxaca City (focused) overlapped on the globe
test('a home label gives way to the focused place when the two would overlap', () => {
  const pins = [{ id: 'mx', x: 300, y: 500 }, { id: 'oax', x: 330, y: 540 }, { id: 'tokyo', x: 900, y: 300 }];
  assert.deepStrictEqual([...J.labelled(pins, 'oax', 'mx')], ['oax']);
  assert.deepStrictEqual([...J.labelled(pins, 'tokyo', 'mx')].sort(), ['mx', 'tokyo']);
  assert.deepStrictEqual([...J.labelled(pins, null, 'mx')], ['mx']);
});
