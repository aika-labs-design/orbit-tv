const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const Taste = require(path.join(__dirname, '../../main/assets/web/taste.js'));

const T = (id, pop, genres, extra) => Object.assign({ id, kind: 'movie', title: 'T' + id, year: '2022', genres, popularity: pop, apps: [] }, extra || {});
const DAY = 86400000, NOW = Date.UTC(2026, 10, 1);
const THRILLER = 53, DRAMA = 18;

test('features describe genre, kind, decade and app', () => {
  assert.deepStrictEqual(Taste.features(T(1, 1, [THRILLER, DRAMA], { year: '1998', apps: [{ name: 'Netflix', pkg: 'x' }] })), ['g:53', 'g:18', 'k:movie', 'd:1990s', 'a:Netflix']);
  assert.deepStrictEqual(Taste.features(T(2, 1, [], { year: '' })), ['k:movie', 'd:unknown', 'a:none']);
});

test('an empty profile ranks by popularity', () => {
  const r = Taste.rank([T(1, 2, []), T(2, 50, []), T(3, 9, [])], Taste.empty(), 1);
  assert.deepStrictEqual(r.map(t => t.id), [2, 3, 1]);
});

test('one pick does not override popularity, three picks do', () => {
  const thriller = T(1, 5, [THRILLER]), drama = T(2, 30, [DRAMA]);
  let p = Taste.learn(Taste.empty(), thriller, 'pick', NOW);
  assert.strictEqual(Taste.rank([thriller, drama], p, 1)[0].id, 2);
  p = Taste.learn(Taste.learn(p, thriller, 'pick', NOW), thriller, 'pick', NOW);
  assert.strictEqual(Taste.rank([thriller, drama], p, 1)[0].id, 1);
});

test('weights fade by 3% a day', () => {
  const p = Taste.learn(Taste.empty(), T(1, 5, [THRILLER]), 'pick', NOW);
  const later = Taste.decay(p, NOW + 30 * DAY);
  assert.ok(Math.abs(later.w['g:53'] - Math.pow(0.97, 30)) < 1e-9);
  assert.strictEqual(later.at, NOW + 30 * DAY);
});

test('watched counts double, quick counts against, weights stay clamped', () => {
  const t = T(1, 5, [THRILLER]);
  assert.strictEqual(Taste.learn(Taste.empty(), t, 'watched', NOW).w['g:53'], 2);
  assert.strictEqual(Taste.learn(Taste.empty(), t, 'quick', NOW).w['g:53'], -0.5);
  let p = Taste.empty(); for (let i = 0; i < 10; i++) p = Taste.learn(p, t, 'watched', NOW);
  assert.strictEqual(p.w['g:53'], 8);
  let q = Taste.empty(); for (let i = 0; i < 20; i++) q = Taste.learn(q, t, 'quick', NOW);
  assert.strictEqual(q.w['g:53'], -5);
});

test('every fifth slot explores outside the top ten, stable for a seed, nothing lost', () => {
  const titles = Array.from({ length: 20 }, (_, i) => T(i + 1, 100 - i, []));
  const a = Taste.rank(titles, Taste.empty(), 7), b = Taste.rank(titles, Taste.empty(), 7);
  assert.deepStrictEqual(a.map(t => t.id), b.map(t => t.id));
  assert.strictEqual(new Set(a.map(t => t.id)).size, 20);
  [4, 9].forEach(i => assert.ok(a[i].id > 10, `slot ${i} holds ${a[i].id}`));
  assert.deepStrictEqual(a.slice(0, 4).map(t => t.id), [1, 2, 3, 4]);
});

test('short lists rank without exploring and never crash', () => {
  assert.deepStrictEqual(Taste.rank([], Taste.empty(), 1), []);
  assert.deepStrictEqual(Taste.rank([T(1, 1, [])], undefined, 1).map(t => t.id), [1]);
});

test('time away turns into a signal: twenty minutes is watched, under two is quick', () => {
  assert.strictEqual(Taste.signalFor(20), 'watched'); assert.strictEqual(Taste.signalFor(95), 'watched');
  assert.strictEqual(Taste.signalFor(1.5), 'quick'); assert.strictEqual(Taste.signalFor(0), 'quick');
  assert.strictEqual(Taste.signalFor(8), null); assert.strictEqual(Taste.signalFor(NaN), null);
});

test('a return only teaches the title that was actually launched, and only within six hours', () => {
  const pick = { home: 'ap', title: T(1, 5, []), pkg: 'com.netflix.ninja', at: NOW };
  assert.strictEqual(Taste.attribute(pick, 'com.netflix.ninja', NOW + 30 * 60000), 'watched');
  assert.strictEqual(Taste.attribute(pick, 'com.netflix.ninja', NOW + 60000), 'quick');
  assert.strictEqual(Taste.attribute(pick, 'com.amazon.firetv.youtube', NOW + 30 * 60000), null);
  assert.strictEqual(Taste.attribute(pick, 'com.netflix.ninja', NOW + 7 * 3600000), null);
  assert.strictEqual(Taste.attribute({ home: 'ap', title: T(1, 5, []), at: NOW }, 'com.netflix.ninja', NOW + 30 * 60000), null);
  assert.strictEqual(Taste.attribute(null, 'x', NOW), null);
});

test('history keeps one entry per title, newest first, strongest signal, at most thirty', () => {
  let h = Taste.remember([], T(1, 5, []), 'pick', NOW);
  h = Taste.remember(h, T(2, 5, []), 'pick', NOW + 1);
  h = Taste.remember(h, T(1, 5, []), 'watched', NOW + 2);
  h = Taste.remember(h, T(1, 5, []), 'quick', NOW + 3);
  assert.deepStrictEqual(h.map(e => [e.title.id, e.signal]), [[1, 'watched'], [2, 'pick']]);
  for (let i = 10; i < 50; i++) h = Taste.remember(h, T(i, 5, []), 'pick', NOW + i);
  assert.strictEqual(h.length, 30);
});

test('reasons name loved titles that share a genre; match needs a learned profile', () => {
  let h = Taste.remember([], T(1, 5, [THRILLER]), 'watched', NOW);
  h = Taste.remember(h, T(2, 5, [THRILLER]), 'pick', NOW + 1);
  h = Taste.remember(h, T(3, 5, [DRAMA]), 'pick', NOW + 2);
  h = Taste.remember(h, T(4, 5, [THRILLER]), 'quick', NOW + 3);
  assert.deepStrictEqual(Taste.reason(h, T(9, 5, [THRILLER])), ['T2', 'T1']);
  assert.deepStrictEqual(Taste.reason(h, T(1, 5, [THRILLER])), ['T2']);
  assert.strictEqual(Taste.match(Taste.empty(), T(9, 5, [THRILLER])), null);
  const p = Taste.learn(Taste.empty(), T(1, 5, [THRILLER]), 'watched', NOW);
  const m = Taste.match(p, T(9, 5, [THRILLER])); assert.ok(m > 50 && m <= 99, String(m));
  assert.ok(Taste.match(p, T(9, 5, [DRAMA])) < m);
});

// ---- Learning across loves (Task 9) ----
const CAT = [
  { id: 'space', name: 'Space', terms: ['mars rover', 'nasa', 'astronaut'], refine: { options: [{ id: 'mars', name: 'Mars', terms: ['Mars'] }] } },
  { id: 'football', name: 'Football', terms: ['arsenal', 'premier league'], refine: { options: [{ id: 'arsenal', name: 'Arsenal', terms: ['Arsenal'] }] } },
  { id: 'cooking', name: 'Cooking', terms: ['recipe', 'chef'], refine: { options: [] } },
];
const watched = (text, daysAgo, extra) => Object.assign({ title: { kind: 'video', id: text, title: text }, signal: 'watched', at: NOW - daysAgo * DAY }, extra || {});

test('whole-word matcher is accent and case insensitive with optional plural', () => {
  assert.ok(Taste.termIn('Mars rover lands', ['mars rover']));
  assert.ok(Taste.termIn('Two Arsenals', ['arsenal']) === true);
  assert.ok(!Taste.termIn('Parsenal fans', ['arsenal']));
  assert.ok(Taste.termIn('Café Olé night', ['cafe', 'ole']));
  assert.ok(Taste.termIn('A BIG Chef!', ['chef']));
  assert.ok(!Taste.termIn('Chefchaouen', ['chef']));
  assert.ok(!Taste.termIn('', ['chef']) && !Taste.termIn('chef', []));
});

test('watching raises a chosen love and an untouched one fades below it but stays listed', () => {
  const loves = [{ id: 'space', refine: [] }, { id: 'football', refine: [] }];
  const hist = [watched('NASA astronaut talk', 1), watched('Mars rover day', 2)];
  const w = Taste.loveWeights(hist, loves, NOW, CAT);
  assert.deepStrictEqual(Object.keys(w).sort(), ['football', 'space']);
  assert.ok(w.space > w.football, JSON.stringify(w));
  const old = Taste.loveWeights([watched('Premier League goals', 45, { love: 'football' })], loves, NOW, CAT);
  assert.ok(old.football < 1 && old.football > 0);
  const mix = Taste.loveWeights([watched('Premier League goals', 45), watched('NASA live', 1)], loves, NOW, CAT);
  assert.ok(mix.football < mix.space && mix.football > 0);
});

test('a direct love id and journeys history both count', () => {
  const loves = [{ id: 'cooking', refine: [] }];
  const j = { id: 'p1', place: { id: 'p1', name: 'Tokyo' }, mode: 'walk', at: NOW - DAY, loved: true };
  const w1 = Taste.loveWeights([watched('anything', 1, { love: 'cooking', minutes: 40 })], loves, NOW, CAT);
  assert.ok(w1.cooking > 1);
  assert.ok(Taste.loveWeights([watched('x', 1, { love: 'cooking' }), j], loves, NOW, CAT).cooking > 1);
});

test('a quick return does not raise a love', () => {
  const loves = [{ id: 'cooking', refine: [] }];
  const w = Taste.loveWeights([watched('best recipe', 1, { signal: 'quick' })], loves, NOW, CAT);
  assert.strictEqual(w.cooking, 1);
});

test('withWeights returns a new profile and leaves other loves alone', () => {
  const p = { id: 'a', loves: [{ id: 'space', refine: [] }, { id: 'cooking', refine: [], weight: 3 }] };
  const q = Taste.withWeights(p, { space: 2.5 });
  assert.strictEqual(q.loves[0].weight, 2.5); assert.strictEqual(q.loves[1].weight, 3);
  assert.strictEqual(p.loves[0].weight, undefined);
});

test('three watched items pointing at an unchosen love produce one offer', () => {
  const loves = [{ id: 'football', refine: [] }];
  const hist = [watched('NASA astronaut', 1), watched('Mars rover', 2), watched('Astronaut diary', 3), watched('Premier League', 1)];
  const n = Taste.noticed(hist, loves, CAT);
  assert.strictEqual(n && n.id, 'space');
  assert.strictEqual(Taste.noticed(hist.slice(0, 2), loves, CAT), null);
  assert.strictEqual(Taste.noticed(hist, [{ id: 'space', refine: [] }], CAT), null);   // already chosen
  assert.strictEqual(Taste.noticed([watched('a', 1, { signal: 'quick', love: 'space' }), watched('b', 1, { love: 'space' }), watched('c', 1, { love: 'space' })], [], CAT), null);
});

test('offered once: it returns only after three more items, and two ignores stop it', () => {
  const hist = [watched('NASA astronaut', 3), watched('Mars rover', 3), watched('Astronaut diary', 3)];
  let s = Taste.offerState();
  assert.strictEqual(Taste.noticed(hist, [], CAT, s).id, 'space');
  s = Taste.offerShown(s, 'space', NOW - DAY);
  assert.strictEqual(Taste.noticed(hist, [], CAT, s), null);
  const more = [watched('nasa 1', 0), watched('nasa 2', 0), watched('nasa 3', 0)].concat(hist);
  assert.strictEqual(Taste.noticed(more, [], CAT, s).id, 'space');
  s = Taste.offerIgnored(s, 'space', NOW);
  s = Taste.offerShown(s, 'space', NOW);
  assert.strictEqual(Taste.noticed(more, [], CAT, s), null);
  const more2 = [watched('nasa 4', -1), watched('nasa 5', -1), watched('nasa 6', -1)].concat(more);
  assert.strictEqual(Taste.noticed(more2, [], CAT, s).id, 'space');
  s = Taste.offerIgnored(s, 'space', NOW);
  assert.strictEqual(Taste.noticed(more2, [], CAT, s), null);
  assert.strictEqual(s.ignored.space, 2);
});

test('accepting an offer ends it', () => {
  const hist = [watched('a', 1, { love: 'space' }), watched('b', 1, { love: 'space' }), watched('c', 1, { love: 'space' })];
  const s = Taste.offerAccepted(Taste.offerShown(Taste.offerState(), 'space', NOW), 'space', NOW);
  assert.strictEqual(Taste.noticed(hist, [], CAT, s), null);
  assert.deepStrictEqual(s.accepted, { space: true });
});

test('a love never watched fades from when it was chosen (since or profile created)', () => {
  const used = [watched('NASA live', 1)];
  const w = Taste.loveWeights(used, [{ id: 'space', refine: [] }, { id: 'cooking', refine: [], since: NOW - 45 * DAY }], NOW, CAT);
  assert.ok(w.cooking < 1 && w.cooking > 0 && w.cooking < w.space, JSON.stringify(w));
  const w2 = Taste.loveWeights([], [{ id: 'cooking', refine: [] }], NOW, CAT, NOW - 45 * DAY);
  assert.ok(w2.cooking < 1 && w2.cooking > 0, JSON.stringify(w2));
  assert.strictEqual(Taste.loveWeights([], [{ id: 'cooking', refine: [], since: NOW - 5 * DAY }], NOW, CAT).cooking, 1);
  assert.strictEqual(Taste.loveWeights([], [{ id: 'cooking', refine: [] }], NOW, CAT).cooking, 1, 'no date at all: no fade');
});
