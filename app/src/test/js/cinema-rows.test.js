const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const assets = path.join(__dirname, '../../main/assets');
const read = (f) => JSON.parse(fs.readFileSync(path.join(assets, f), 'utf8'));
const Taste = require(path.join(assets, 'web/taste.js'));
const WorldData = require(path.join(assets, 'web/world-data.js'));
const CinemaRows = require(path.join(assets, 'web/cinema-rows.js'));

const languages = read('languages.json'), countries = read('countries.json'), interests = read('interests.json');
WorldData.load(languages);
const LANG = {}; languages.forEach(l => { LANG[l.code] = l; });
const COUNTRY = {}; countries.forEach(c => { COUNTRY[c.code] = c; });
const world = { filmTab: WorldData.filmTab, langName: (c) => { const l = LANG[c]; if (!l) return c; const n = l.native.replace(/^Èdè\s+/, ''); return n.charAt(0).toUpperCase() + n.slice(1); }, countryName: (c) => (COUNTRY[c] && COUNTRY[c].name) || c };

const NOW = Date.UTC(2026, 10, 1, 18);   // 19:00 in Berlin, 19:00 in Lagos
const DAY = 86400000;
const T = (id, extra) => Object.assign({ id, kind: 'movie', title: 'T' + id, year: '2024', lang: 'yo', overview: 'o', genres: [18], genreNames: ['Drama'], popularity: 100 - id, rating: 7,
  poster: `p${id}.jpg`, backdrop: `b${id}.jpg`, logo: `l${id}.png`, localTitle: null, runtime: 110, trailers: ['k' + id], providers: [], apps: [] }, extra || {});

// Tolu, as on the approved pictures: home Lagos, living in Berlin, Yorùbá first, four loves picked in this order.
const tolu = { id: 'p-tolu', name: 'Tolu', home: { country: 'NG', places: [{ name: 'Lagos', lat: 6.45, lon: 3.39, tz: 'Africa/Lagos' }] }, languages: ['yo', 'en', 'pcm', 'de'],
  living: { name: 'Berlin', lat: 52.52, lon: 13.4, tz: 'Europe/Berlin', country: 'DE' },
  loves: [{ id: 'football', refine: ['arsenal', 'super-eagles'] }, { id: 'afrobeats', refine: [] }, { id: 'west-african-cooking', refine: [] }, { id: 'fashion', refine: [] }], created: NOW - 10 * DAY };
const ravi = { id: 'p-ravi', name: 'Ravi', home: { country: 'IN', places: [{ name: 'Mysuru', lat: 12.30, lon: 76.64, tz: 'Asia/Kolkata' }] }, languages: ['kn', 'en'],
  living: { name: 'Toronto', lat: 43.65, lon: -79.38, tz: 'America/Toronto', country: 'CA' }, loves: [{ id: 'cricket', refine: ['india-cricket'] }], created: NOW - 10 * DAY };
const sigrun = { id: 'p-sig', name: 'Sigrún', home: { country: 'IS', places: [{ name: 'Reykjavík', lat: 64.15, lon: -21.94, tz: 'Atlantic/Reykjavik' }] }, languages: ['is'],
  living: { name: 'Reykjavík', lat: 64.15, lon: -21.94, tz: 'Atlantic/Reykjavik', country: 'IS' }, loves: [{ id: 'hiking', refine: [] }], created: NOW };

const toluLoves = {
  football: { why: 'Because you follow Arsenal, Super Eagles in Football', shows: [], videos: [], news: [{ title: 'Arsenal sign a striker', link: 'https://n/1', source: 'BBC', publishedMs: NOW - 3600000 }],
    live: [{ kind: 'sport', title: 'Arsenal v Chelsea', detail: 'Premier League', state: 'pre', startMs: Date.UTC(2026, 10, 1, 19), venue: 'Emirates Stadium', score: '' }] },
  afrobeats: { why: 'Because you love Afrobeats', shows: [], news: [], live: [{ kind: 'stream', id: 's1', title: 'Afrobeats live from Lagos', channel: 'Lagos Live', minutes: 0, live: true, thumb: 'https://i.ytimg.com/vi/s1/maxresdefault.jpg' }],
    videos: [{ kind: 'video', id: 'v2', title: 'Burna Boy live session', channel: 'COLORS', minutes: 22, live: false, thumb: 'https://i.ytimg.com/vi/v2/maxresdefault.jpg' }] },
  'west-african-cooking': { why: 'Because you love West African cooking', shows: [{ id: 77, kind: 'tv', title: 'Jollof Wars', year: '2023', overview: 'Cooks.', poster: 'jp.jpg', backdrop: 'jb.jpg' }], news: [], live: [],
    videos: [{ kind: 'video', id: 'v1', title: 'Party jollof, the smoky way', channel: 'Sisi Jemimah', minutes: 18, live: false, thumb: 'https://i.ytimg.com/vi/v1/maxresdefault.jpg' }] },
  fashion: { why: 'Because you love Fashion', shows: [], news: [], live: [], videos: [] },
};
const toluHome = { pack: { id: 'p-tolu', label: 'Nigeria', channelTag: 'Nigeria' }, weather: [{ name: 'Lagos', tempC: 27, desc: 'clear' }], weatherMs: NOW,
  news: { id: 'home_p-tolu', label: 'Nigeria', headlines: [{ title: 'Third Mainland Bridge reopens after repairs', source: 'Punch', publishedMs: NOW - 40 * 60000 }, { title: 'Lagos rain', source: 'Premium Times', publishedMs: NOW - 2 * 3600000 }] } };
const items = (extra) => Object.assign({ cont: [{ kind: 'app', id: 'app:yt', name: 'YouTube', art: 'yt.png', pkg: 'y' }], ch: [], sport: [] }, extra || {});
const toluCat = [T(1), T(2, { apps: [{ name: 'Netflix', pkg: 'n' }] }), T(3, { genres: [35], genreNames: ['Comedy'] }), T(4, { lang: 'en', title: 'English one' })];
const base = (o) => Object.assign({ profile: tolu, weights: {}, catalog: toluCat, loves: toluLoves, home: toluHome, interests, world, items: items(),
  taste: Taste.empty(), history: [], rank: (t) => t, nowMs: NOW, tab: 'foryou' }, o || {});
const build = (o) => CinemaRows.build(base(o));
const ids = (r) => r.shelves.map(s => s.id);
const tabsOf = (p, cat, w) => CinemaRows.tabs(p, w || {}, Object.assign({ catalog: cat, interests }, world)).map(t => t.label);

test("Tolu's tabs: For You, Nollywood, three loves by weight, Journeys, Apps", () => {
  assert.deepStrictEqual(tabsOf(tolu, toluCat), ['For You', 'Nollywood', 'Football', 'Afrobeats', 'Cooking', 'Journeys', 'Apps']);
  // the most used loves lead; the fourth love is not a tab
  assert.deepStrictEqual(tabsOf(tolu, toluCat, { fashion: 3, afrobeats: 2 }), ['For You', 'Nollywood', 'Fashion', 'Afrobeats', 'Football', 'Journeys', 'Apps']);
  // weights saved on the profile count when none are passed
  const saved = Object.assign({}, tolu, { loves: tolu.loves.map(l => l.id === 'fashion' ? Object.assign({}, l, { weight: 4 }) : l) });
  assert.strictEqual(tabsOf(saved, toluCat)[2], 'Fashion');
});

test('an Icelandic-only profile with no catalogue has no film tab', () => {
  assert.deepStrictEqual(tabsOf(sigrun, []), ['For You', 'Hiking', 'Journeys', 'Apps']);
  // a catalogue only in other languages does not make the main language's film tab either
  assert.ok(!tabsOf(tolu, [T(4, { lang: 'en' })]).includes('Nollywood'));
  assert.deepStrictEqual(CinemaRows.tabs(null).map(t => t.id), ['foryou', 'journeys', 'apps']);
});

test('love tab labels stay short', () => {
  assert.strictEqual(CinemaRows.shortName('West African cooking'), 'Cooking');
  assert.strictEqual(CinemaRows.shortName('Nature and wildlife'), 'Nature');
  assert.strictEqual(CinemaRows.shortName('Football'), 'Football');
  assert.strictEqual(CinemaRows.shortName('Artificial intelligence'), 'Artificial intelligence');
});

test('every For You row says why: a love, the home or what was watched', () => {
  const h = Taste.remember([], T(3), 'watched', NOW - 2 * DAY);
  const ch = [{ kind: 'channel', id: 'ch:1', name: 'Channels TV', live: true, tag: 'Nigeria · News', art: 'c.jpg' }];
  const r = build({ history: h, items: items({ ch }) });
  const names = ['Football', 'Afrobeats', 'West African cooking', 'Fashion', 'Arsenal', 'Lagos', 'Nigeria', 'Yorùbá', 'English', 'T3'];
  const names_ = (s) => names.some(n => (s || '').includes(n));
  assert.ok(r.shelves.length >= 5, ids(r).join());
  r.shelves.forEach(s => {
    if (s.id === 'tonight') s.cards.forEach(c => assert.ok(names_(c.why) || /Watch again|Opened/.test(c.sub || ''), `tonight card ${c.title}: ${c.why}`));
    else assert.ok(names_(s.why), `${s.id}: ${s.why}`);
  });
  assert.ok(!ids(r).includes('apps'), 'apps have their own tab');
});

test('Tonight, for you mixes live, new videos and home news, each saying why', () => {
  const sport = [{ kind: 'live', id: 'live:football:1', name: 'Arsenal v Chelsea', live: true, pkg: 'sky', data: { sport: 'football', short: 'ARS 2-1 CHE', competition: 'Premier League', handoffLabel: 'Sky Go' } },
    { kind: 'live', id: 'live:cricket:1', name: 'India v England', live: true, pkg: 'x', data: { sport: 'cricket', short: 'IND 94/6', handoffLabel: 'SonyLIV' } }];
  const r = build({ items: items({ sport }) });
  const t = r.shelves[0];
  assert.strictEqual(t.id, 'tonight'); assert.strictEqual(t.title, 'Tonight, for you'); assert.strictEqual(t.subtitle, 'live, new and close to home');
  const kinds = t.cards.map(c => c.kind);
  assert.strictEqual(kinds[0], 'live', 'a live match of a love leads');
  assert.strictEqual(t.cards[0].sub, 'Premier League · on Sky Go');
  assert.ok(!t.cards.some(c => c.title === 'India v England'), 'a sport nobody here loves is not shown');
  const video = t.cards.find(c => c.kind === 'video');
  assert.ok(video, 'a new video from a love'); assert.ok(/^Because you love .+ · \d+ min$/.test(video.sub), video.sub); assert.ok(video.love);
  const news = t.cards.find(c => c.kind === 'news');
  assert.strictEqual(news.title, 'Home news'); assert.strictEqual(news.headline, 'Third Mainland Bridge reopens after repairs');
  assert.strictEqual(news.face, 'LAGOS · 40 MIN AGO'); assert.strictEqual(news.sub, '\u2068Punch\u2069 · \u2068Premium Times\u2069 · in Yorùbá and English');
  const stream = t.cards.find(c => c.kind === 'stream');
  assert.strictEqual(stream.title, 'Afrobeats live from Lagos'); assert.strictEqual(stream.why, 'Because you love Afrobeats');
  assert.ok(t.cards.some(c => c.kind === 'title'));
  assert.strictEqual(r.hero.kind, 'title');
});

test('a fixture says when it plays in the living city', () => {
  const r = build({ tab: 'love:football' });
  const c = r.shelves[0].cards.find(x => x.title === 'Arsenal v Chelsea');
  assert.strictEqual(c.sub, 'Today 20:00 your time');
  assert.strictEqual(c.love, 'football');
});

// QA: a live tennis match offered "When it plays"
test('a live match says Watch, on the hand-off app when the TV has one; a match to come says when it plays', () => {
  const p = Object.assign({}, tolu, { loves: [{ id: 'tennis', refine: [] }] });
  const loves = { tennis: { why: 'Because you love Tennis', shows: [], videos: [], news: [], live: [
    { kind: 'sport', title: 'Ana Uno v Bea Dos', detail: '3rd Set', state: 'in', startMs: NOW - 3600000, venue: '', score: '6-3 4-6 2-1' },
    { kind: 'sport', title: 'Cy Tres v Di Cuatro', detail: 'Scheduled', state: 'pre', startMs: NOW + 3600000, venue: '', score: '' }] } };
  const cards = (it) => build({ profile: p, loves, tab: 'love:tennis', items: items(it) }).shelves[0].cards;
  const live = (cs) => cs.find(c => c.title === 'Ana Uno v Bea Dos'), next = (cs) => cs.find(c => c.title === 'Cy Tres v Di Cuatro');
  assert.strictEqual(live(cards()).action, 'Watch');
  assert.strictEqual(next(cards()).action, 'When it plays');
  const sport = [{ kind: 'live', id: 'live:tennis', name: 'Tennis', pkg: 'com.tennis.tv', state: 'NONE', live: false, data: { sport: 'tennis', handoffLabel: 'Tennis TV' } }];
  const withApp = live(cards({ sport }));
  assert.strictEqual(withApp.action, 'Watch on Tennis TV'); assert.strictEqual(withApp.pkg, 'com.tennis.tv');
  assert.strictEqual(next(cards({ sport })).pkg, undefined);
});

test('a love tab shows that love only, each shelf saying why', () => {
  const r = build({ tab: 'love:west-african-cooking' });
  assert.ok(r.shelves.length >= 2);
  r.shelves.forEach(s => assert.strictEqual(s.why, 'Because you love West African cooking'));
  const all = r.shelves.flatMap(s => s.cards);
  assert.ok(all.some(c => c.title === 'Party jollof, the smoky way'));
  assert.ok(all.some(c => c.title === 'Jollof Wars'));
  assert.ok(all.every(c => c.love === 'west-african-cooking'));
  assert.ok(!all.some(c => c.title === 'Burna Boy live session'));
});

test('the film tab is the main language catalogue, by genre, then the other languages', () => {
  const r = build({ tab: 'films' });
  assert.deepStrictEqual(r.shelves.map(s => s.title), ['Nollywood', 'Drama', 'In English']);
  r.shelves.forEach(s => assert.ok(/Yorùbá|English/.test(s.why), s.why));
});

test('switching profile changes the tabs and the rows', () => {
  const kn = [T(10, { lang: 'kn', title: 'Kantara' })];
  const a = build(), b = build({ profile: ravi, catalog: kn, loves: { cricket: { why: 'Because you follow India in Cricket', shows: [], news: [], videos: [{ kind: 'video', id: 'c1', title: 'India highlights', channel: 'IPL', minutes: 12, thumb: 't' }, { kind: 'video', id: 'c2', title: 'India at Chinnaswamy', channel: 'IPL', minutes: 15, thumb: 't' }], live: [] } }, home: null });
  assert.deepStrictEqual(tabsOf(ravi, kn), ['For You', 'Sandalwood', 'Cricket', 'Journeys', 'Apps']);
  assert.notDeepStrictEqual(ids(a), ids(b));
  assert.ok(ids(b).includes('love:cricket')); assert.ok(!ids(b).includes('love:football'));
  assert.strictEqual(b.hero.title, 'Kantara');
});

test('a new person sees a picked hero with no invented reason or match', () => {
  const r = build({ loves: {}, home: null });
  assert.strictEqual(r.hero.title, 'T1');
  assert.strictEqual(r.hero.eyebrow, 'Picked for you'); assert.strictEqual(r.hero.why, 'Popular in Yorùbá'); assert.strictEqual(r.hero.match, null);
  assert.ok(r.hero.meta.includes('Yorùbá'));
});

test('after picks the hero says why and how well it matches', () => {
  let p = Taste.empty(), h = [];
  p = Taste.learn(p, T(2), 'watched', NOW - DAY * 3); h = Taste.remember(h, T(2), 'watched', NOW - DAY * 3);
  const r = build({ taste: p, history: h });
  assert.strictEqual(r.hero.why, 'because you loved T2');
  assert.ok(r.hero.match >= 50 && r.hero.match <= 99);
  assert.strictEqual(r.shelves.find(s => s.id === 'because').title, 'Because you loved T2');
});

test('a recently watched title says Watch again with its app', () => {
  const h = Taste.remember([], T(2, { apps: [{ name: 'Netflix', pkg: 'n' }] }), 'watched', NOW - DAY);
  const c = build({ history: h }).shelves[0].cards.find(x => x.title === 'T2');
  assert.strictEqual(c.sub, 'Watch again · Netflix'); assert.strictEqual(c.progress, undefined);
  const o = Taste.remember([], T(3), 'pick', NOW - DAY);
  assert.strictEqual(build({ history: o }).shelves[0].cards.find(x => x.title === 'T3').sub, 'Opened yesterday');
});

test('a title on an app says where, in the living country', () => {
  const c = build().shelves[0].cards.find(x => x.title === 'T2');
  assert.ok(c.meta.includes('On Netflix in Germany'), c.meta.join());
  assert.strictEqual(build().shelves[0].cards.find(x => x.title === 'T1').country, 'Germany');
});

test('no catalogue and no loves still gives a working home', () => {
  const r = build({ catalog: [], loves: {}, home: null });
  assert.deepStrictEqual(ids(r), ['apps']); assert.strictEqual(r.hero.kind, 'app');
  assert.deepStrictEqual(ids(build({ tab: 'apps' })), ['apps']);
});

test('opening an item from a love row records the love and the minutes watched', () => {
  const v = build({ tab: 'love:afrobeats' }).shelves.flatMap(s => s.cards).find(c => c.kind === 'video');
  let h = CinemaRows.record([], v, 'pick', NOW);
  assert.strictEqual(h[0].love, 'afrobeats'); assert.strictEqual(h[0].title.title, 'Burna Boy live session');
  h = CinemaRows.record(h, { data: h[0].title }, 'watched', NOW + 25 * 60000, 25);
  assert.strictEqual(h.length, 1); assert.strictEqual(h[0].love, 'afrobeats'); assert.strictEqual(h[0].minutes, 25); assert.strictEqual(h[0].signal, 'watched');
  const w = Taste.loveWeights(h, tolu.loves, NOW + DAY, interests, tolu.created);
  assert.ok(w.afrobeats > w.fashion, JSON.stringify(w));
});

test('weights are saved only when they changed', () => {
  assert.strictEqual(CinemaRows.weightsChanged(tolu, { football: 1, afrobeats: 1 }), false);
  assert.strictEqual(CinemaRows.weightsChanged(tolu, { afrobeats: 2.4 }), true);
  const saved = Taste.withWeights(tolu, { afrobeats: 2.4 });
  assert.strictEqual(CinemaRows.weightsChanged(saved, { afrobeats: 2.41 }), false);
});

test('Orbit noticed says what was watched', () => {
  const street = interests.find(i => i.id === 'street-food');
  let h = [];
  ['Suya street food in Lagos', 'Street food tour of Accra', 'Night market street food'].forEach((t, k) => { h = Taste.remember(h, { kind: 'video', id: 'x' + k, title: t }, 'pick', NOW - k * DAY); });
  assert.strictEqual(CinemaRows.noticedLine(h, street, NOW), 'You opened three things about Street food lately, most recently "Suya street food in Lagos".');
});

test('the hero refreshes when the focused card changes in place', () => {
  const a = { id: 'live:1', score: 'IND 94/6 v UAE 97', logo: null, still: null, trailers: [], caption: 'x', why: 'y' };
  assert.strictEqual(CinemaRows.heroChanged(a, Object.assign({}, a)), false);
  assert.strictEqual(CinemaRows.heroChanged(a, Object.assign({}, a, { score: 'IND 120/6 v UAE 97' })), true);
  assert.strictEqual(CinemaRows.heroChanged(a, Object.assign({}, a, { logo: 'l.png' })), true);
  assert.strictEqual(CinemaRows.heroChanged(a, Object.assign({}, a, { trailers: ['k'] })), true);
  assert.strictEqual(CinemaRows.heroChanged(null, a), true);
});

test('changing tab or going back lands on the hero of the new page, not the old shelf', () => {
  const r = build();
  const heroIdx = r.shelves[0].cards.findIndex(c => c.id === r.hero.id);
  const last = r.shelves.length - 1, lastCard = r.shelves[last].cards[0].id;
  assert.deepStrictEqual(CinemaRows.landing(r, { keepShelf: r.shelves[last].id, keepCard: lastCard, reset: true }), { ri: 0, ci: heroIdx });
  assert.deepStrictEqual(CinemaRows.landing(r, { keepShelf: r.shelves[last].id, keepCard: lastCard, reset: false }), { ri: last, ci: 0 });
  assert.deepStrictEqual(CinemaRows.landing(r, { keepShelf: 'gone', keepCard: 'x', reset: false }), { ri: 0, ci: heroIdx });
});

test('the main language leads For You even when other languages are more popular', () => {
  const cat = [T(4, { lang: 'en', title: 'Big English hit', popularity: 900 }), T(1, { popularity: 5 })];
  const r = build({ catalog: cat, loves: {}, home: null });
  assert.strictEqual(r.hero.title, 'T1');
  assert.strictEqual(r.shelves.find(s => s.id === 'tonight').cards[0].title, 'T1');
});

test('a headline drops the source Google News appends to it', () => {
  const loves = Object.assign({}, toluLoves, { afrobeats: Object.assign({}, toluLoves.afrobeats, { news: [{ title: 'Burna Boy sells out Berlin - Vanguard News', link: 'l', source: 'Vanguard News', publishedMs: NOW - 3600000 }] }) });
  const c = build({ tab: 'love:afrobeats', loves }).shelves.flatMap(s => s.cards).find(x => x.kind === 'news');
  assert.strictEqual(c.headline, 'Burna Boy sells out Berlin');
  assert.strictEqual(c.reader.headlines[0].title, 'Burna Boy sells out Berlin');
});

test('a radio station is a playable live card for its love', () => {
  const loves = Object.assign({}, toluLoves, { afrobeats: Object.assign({}, toluLoves.afrobeats, { live: [{ kind: 'radio', title: 'Afrobeats FM', url: 'https://radio/afro.mp3', detail: 'MP3' }] }) });
  const c = build({ tab: 'love:afrobeats', loves }).shelves.find(s => s.id === 'l:live').cards[0];
  assert.strictEqual(c.kind, 'radio'); assert.strictEqual(c.title, 'Afrobeats FM'); assert.strictEqual(c.url, 'https://radio/afro.mp3');
  assert.strictEqual(c.why, 'Because you love Afrobeats'); assert.strictEqual(c.sub, 'Because you love Afrobeats · radio');
  assert.strictEqual(c.love, 'afrobeats'); assert.strictEqual(c.live, true);
});

test('live story updates (MotoGP, cricket stories) are cards with their text', () => {
  const loves = { motogp: { why: 'Because you love MotoGP', shows: [], news: [], videos: [], live: [{ kind: 'story', title: 'Marquez takes pole in Valencia', link: 'https://r/1', state: 'in', startMs: NOW - 30 * 60000 }] } };
  const p = Object.assign({}, tolu, { loves: [{ id: 'motogp', refine: [] }] });
  const c = build({ profile: p, loves, tab: 'love:motogp' }).shelves.find(s => s.id === 'l:live').cards[0];
  assert.strictEqual(c.kind, 'news'); assert.strictEqual(c.headline, 'Marquez takes pole in Valencia');
  assert.strictEqual(c.face, '\u2068MOTOGP\u2069 · 30 MIN AGO'); assert.strictEqual(c.love, 'motogp');
  assert.strictEqual(c.reader.headlines[0].title, 'Marquez takes pole in Valencia');
});

test('a For You love row shows at most two headlines; the love tab keeps them all', () => {
  const heads = [1, 2, 3, 4, 5].map(k => ({ title: 'Arsenal story ' + k, link: 'l' + k, source: 'BBC', publishedMs: NOW - k * 3600000 }));
  const loves = Object.assign({}, toluLoves, { football: Object.assign({}, toluLoves.football, { news: heads }) });
  const row = build({ loves }).shelves.find(s => s.id === 'love:football');
  assert.strictEqual(row.cards.filter(c => c.kind === 'news').length, 2);
  const tab = build({ loves, tab: 'love:football' }).shelves.find(s => s.id === 'l:news');
  assert.strictEqual(tab.cards.length, 5);
});

test('a match that has not started is Next, not LIVE, with no score, and leaves Tonight unless it starts today', () => {
  const at = (ms) => ({ kind: 'live', id: 'live:football', name: 'Liverpool v Arsenal', live: false, state: 'NEXT', pkg: 'sky',
    data: { sport: 'football', state: 'NEXT', short: 'LIV 0 v ARS 0', startMs: ms, competition: 'Premier League', handoffLabel: 'Sky Go' } });
  const later = build({ items: items({ sport: [at(NOW + 3 * DAY)] }) });
  const c = build({ items: items({ sport: [at(NOW + 3 * DAY)] }), tab: 'love:football' }).shelves[0].cards.find(x => x.title === 'Liverpool v Arsenal');
  assert.strictEqual(c.live, false); assert.strictEqual(c.eyebrow, 'Next'); assert.strictEqual(c.score, null);
  assert.ok(!later.shelves[0].cards.some(x => x.title === 'Liverpool v Arsenal'), 'a match in three days is not Tonight');
  const today = build({ items: items({ sport: [at(NOW + 2 * 3600000)] }) });   // 21:00 in Berlin
  assert.ok(today.shelves[0].cards.some(x => x.title === 'Liverpool v Arsenal'), 'a match later today is Tonight');
  const live = Object.assign(at(NOW - 3600000), { live: true, state: 'LIVE' }); live.data = Object.assign({}, live.data, { state: 'LIVE', short: 'LIV 1 v ARS 0' });
  const l = build({ items: items({ sport: [live] }) }).shelves[0].cards[0];
  assert.strictEqual(l.live, true); assert.strictEqual(l.eyebrow, 'Live now'); assert.strictEqual(l.score, 'LIV 1 v ARS 0');
});

test('a love with nothing to show has no tab; the next love takes its place', () => {
  const tevita = { id: 'p-tev', name: 'Tevita', home: { country: 'TO', places: [{ name: "Nuku'alofa", lat: -21.13, lon: -175.2, tz: 'Pacific/Tongatapu' }] }, languages: ['to', 'en'],
    living: { name: 'Auckland', lat: -36.85, lon: 174.76, tz: 'Pacific/Auckland', country: 'NZ' },
    loves: [{ id: 'c-kava', custom: 'kava ceremonies', refine: [] }, { id: 'afrobeats', refine: [] }, { id: 'west-african-cooking', refine: [] }, { id: 'fashion', refine: [] }], created: NOW };
  const loves = Object.assign({}, toluLoves, { 'c-kava': { why: 'Because you love Kava ceremonies', shows: [], news: [], videos: [], live: [] },
    fashion: { why: 'Because you love Fashion', shows: [], news: [{ title: 'Lagos Fashion Week', link: 'f', source: 'Vogue', publishedMs: NOW }], videos: [], live: [] } });
  const labels = CinemaRows.tabs(tevita, {}, Object.assign({ catalog: [], interests, loves, sport: [] }, world)).map(t => t.label);
  assert.deepStrictEqual(labels, ['For You', 'Afrobeats', 'Cooking', 'Fashion', 'Journeys', 'Apps']);
  // a love with a match today keeps its tab even with nothing else
  const sport = [{ kind: 'live', id: 'live:football', name: 'Arsenal v Chelsea', live: true, state: 'LIVE', data: { sport: 'football' } }];
  const quiet = { football: { why: 'w', shows: [], news: [], videos: [], live: [] } };
  assert.ok(CinemaRows.tabs(tolu, {}, Object.assign({ catalog: [], interests, loves: quiet, sport }, world)).some(t => t.id === 'love:football'));
  assert.ok(!CinemaRows.tabs(tolu, {}, Object.assign({ catalog: [], interests, loves: quiet, sport: [] }, world)).some(t => t.id === 'love:football'));
});

test('Popular in names the language it shows, or is hidden', () => {
  const tevita = Object.assign({}, tolu, { id: 'p-tev', languages: ['to', 'en'] });
  const cat = [T(4, { lang: 'en', title: 'English one' }), T(5, { lang: 'fr', title: 'French one' })];
  const pop = build({ profile: tevita, catalog: cat, loves: {}, home: null }).shelves.find(s => s.id === 'because');
  assert.strictEqual(pop.title, 'Popular in English'); assert.deepStrictEqual(pop.cards.map(c => c.title), ['English one']);
  const none = build({ profile: tevita, catalog: [T(5, { lang: 'fr', title: 'French one' })], loves: {}, home: null }).shelves.find(s => s.id === 'because');
  assert.strictEqual(none, undefined);
});

test('the inputs key changes only when something Cinema shows changed', () => {
  const sport = (line1, fetchedMs, short) => [{ kind: 'live', id: 'live:f', name: 'A v B', live: true, state: 'LIVE', pkg: 'p', desc: line1 + '. x', eyebrow: 'F', data: { sport: 'football', line1, fetchedMs, short } }];
  const ch = (eyebrow) => [{ kind: 'channel', id: 'ch:1', name: 'C', art: 'a', tag: 'N', desc: 'd', live: true, eyebrow }];
  const o = (x) => Object.assign({ profile: tolu, tab: 'foryou', catalogN: 1, loves: toluLoves, home: toluHome, items: items({ sport: sport('in 5 h 0 m', 1, ''), ch: ch('LIVE') }), history: '[]', taste: '{}', nowMs: NOW }, x || {});
  const k = CinemaRows.inputsKey(o());
  assert.strictEqual(CinemaRows.inputsKey(o()), k);
  // a second push of the same feeds, a countdown or fetch time, an ago label, or a few minutes later: no rebuild
  assert.strictEqual(CinemaRows.inputsKey(o({ items: items({ sport: sport('in 4 h 58 m', 2, ''), ch: ch('LAST SEEN LIVE 5 MIN AGO') }), nowMs: NOW + 3 * 60000 })), k);
  assert.strictEqual(CinemaRows.inputsKey(o({ home: Object.assign({}, toluHome, { weatherMs: NOW + 1, weather: [] }) })), k);
  // a score, a love's items, the catalogue, the person, the tab, history or the next ten minutes: rebuild
  [o({ items: items({ sport: sport('x', 1, 'ARS 1-0 CHE'), ch: ch('LIVE') }) }), o({ loves: Object.assign({}, toluLoves, { fashion: toluLoves.afrobeats }) }), o({ catalogN: 2 }), o({ profile: ravi }),
    o({ tab: 'films' }), o({ history: '[1]' }), o({ taste: '{"a":1}' }), o({ nowMs: NOW + 11 * 60000 })]
    .forEach((x, i) => assert.notStrictEqual(CinemaRows.inputsKey(x), k, 'case ' + i));
});

test('the last pick moves from the old Stage key to the Cinema key once', () => {
  const m = { 'orbit.stage.lastPick': '{"pkg":"a"}' };
  const s = { getItem: k => (k in m ? m[k] : null), setItem: (k, v) => { m[k] = String(v); }, removeItem: k => { delete m[k]; } };
  CinemaRows.adoptOldPick(s);
  assert.deepStrictEqual(m, { 'orbit.cinema.lastPick': '{"pkg":"a"}' });
  m['orbit.cinema.lastPick'] = '{"pkg":"b"}'; m['orbit.stage.lastPick'] = '{"pkg":"old"}';
  CinemaRows.adoptOldPick(s);   // a newer pick under the new key wins; the old key still goes
  assert.deepStrictEqual(m, { 'orbit.cinema.lastPick': '{"pkg":"b"}' });
  CinemaRows.adoptOldPick(s); assert.deepStrictEqual(m, { 'orbit.cinema.lastPick': '{"pkg":"b"}' });
  assert.strictEqual(CinemaRows.PICK_KEY, 'orbit.cinema.lastPick');
});

// QA: a new person saw "YouTube 25.1 hours this week" as if it were theirs. Android counts app use for the whole TV.
test('apps and their use are labelled as the TV\'s, never the person\'s', () => {
  const r = build({ tab: 'apps' }), s = r.shelves[0];
  assert.strictEqual(s.title, 'Apps on this TV'); assert.strictEqual(s.why, 'Apps on this TV');
  assert.strictEqual(s.cards[0].eyebrow, 'On this TV');
  assert.ok(!/\byour\b/i.test(JSON.stringify([s.title, s.why, s.cards.map(c => [c.eyebrow, c.why])])));
});

// QA: "Popular in English" could lead the page for a Spanish-first person
test('the hero is a title in the person\'s main language when the first shelf has one', () => {
  const history = [1, 2, 3].map(id => ({ title: { kind: 'movie', id, title: 'T' + id }, signal: 'pick', at: NOW - 3600000 }));
  const r = build({ history, catalog: [T(1), T(2), T(3), T(4, { lang: 'en', title: 'English one' }), T(5, { lang: 'en' })] });
  assert.strictEqual(r.hero.kind, 'title');
  assert.strictEqual(r.hero.data.lang, 'yo');
});

// QA: an Arabic source name reordered "4 h ago" around it
test('a news source is isolated from the time beside it, so a right-to-left name cannot reorder the line', () => {
  const loves = Object.assign({}, toluLoves, { football: Object.assign({}, toluLoves.football, { news: [{ title: 'Arsenal win', link: 'https://n/2', source: 'الجزيرة', publishedMs: NOW - 4 * 3600000 }] }) });
  const c = build({ loves, tab: 'love:football' }).shelves.flatMap(s => s.cards).find(x => x.kind === 'news');
  assert.strictEqual(c.face, '⁨الجزيرة⁩ · 4 H AGO');
  assert.strictEqual(c.overview, '⁨الجزيرة⁩ · 4 h ago');
});

// QA: selecting a title that is on none of the person's apps counted as loved and "Opened today"
test('only opening something counts: a title on none of the person\'s apps teaches nothing', () => {
  assert.strictEqual(CinemaRows.counts({ kind: 'title', pkg: null, trailers: ['k'] }), false);
  assert.strictEqual(CinemaRows.counts({ kind: 'title', pkg: null, trailers: [] }), false);
  assert.strictEqual(CinemaRows.counts({ kind: 'title', pkg: 'com.netflix.ninja', trailers: [] }), true);
  assert.strictEqual(CinemaRows.counts({ kind: 'video' }), true);
});

// QA: the "Feels like Mexico" card and hero were empty and dark
test('the Feels like card shows the home photograph once it is known', () => {
  const card = (o) => build(o).shelves.find(s => s.id === 'home').cards.find(c => c.kind === 'homemode');
  assert.strictEqual(card({}).still, null);
  assert.strictEqual(card({ homePhoto: 'https://upload.wikimedia.org/x/960px-Lagos.jpg' }).still, 'https://upload.wikimedia.org/x/960px-Lagos.jpg');
});

// TV: late in the evening Tonight showed "0-0" on a dark card for a match ESPN still called scheduled hours after 17:30
const H = 3600000;
const soccer = (state, startMs, score) => ({ football: Object.assign({}, toluLoves.football, { live: [{ kind: 'sport', title: 'Liverpool v Arsenal', detail: 'Scheduled', state, startMs, venue: 'Anfield', score }] }) });
const tonightOf = (loves) => build({ loves: Object.assign({}, toluLoves, loves) }).shelves.find(s => s.id === 'tonight').cards;

test('a match not yet started shows its kick-off time, never a score', () => {
  const c = tonightOf(soccer('pre', Date.UTC(2026, 10, 1, 16, 30) + 2 * H, '0-0')).find(x => x.title === 'Liverpool v Arsenal');   // 19:30 in Berlin
  assert.strictEqual(c.score, null);
  assert.deepStrictEqual(c.board, { title: 'Liverpool v Arsenal', line: '19:30' });
  assert.strictEqual(c.sub, 'Today 19:30 your time');
});

test('a scheduled match whose start is more than three hours past leaves Tonight and the love rows', () => {
  const stale = soccer('pre', NOW - 4 * H, '0-0');
  assert.ok(!tonightOf(stale).some(x => x.title === 'Liverpool v Arsenal'), 'not in Tonight');
  const tab = build({ loves: Object.assign({}, toluLoves, stale), tab: 'love:football' }).shelves.flatMap(s => s.cards);
  assert.ok(!tab.some(x => x.title === 'Liverpool v Arsenal'), 'not on the love tab');
  assert.ok(tonightOf(soccer('pre', NOW - 1 * H, '')).some(x => x.title === 'Liverpool v Arsenal'), 'an hour late may still kick off');
  assert.ok(tonightOf(soccer('in', NOW - 4 * H, '2-1')).some(x => x.title === 'Liverpool v Arsenal'), 'a long live match stays');
});

test('a live or finished match shows teams and the score on its board', () => {
  const live = tonightOf(soccer('in', NOW - H, '1-0')).find(x => x.title === 'Liverpool v Arsenal');
  assert.deepStrictEqual(live.board, { title: 'Liverpool v Arsenal', line: '1-0' });
});

test('a NEXT shared sport card shows the teams large and its kick-off time below, with the competition', () => {
  const next = { kind: 'live', id: 'live:football', name: 'Liverpool v Arsenal', live: false, state: 'NEXT', pkg: 'sky',
    data: { sport: 'football', state: 'NEXT', short: 'LIV v ARS', startMs: NOW + 2 * H, competition: 'Premier League', handoffLabel: 'Sky Go' } };
  const c = build({ items: items({ sport: [next] }) }).shelves[0].cards.find(x => x.title === 'Liverpool v Arsenal');
  assert.deepStrictEqual(c.board, { title: 'Liverpool v Arsenal', line: '21:00' });
  assert.strictEqual(c.caption, 'Premier League'); assert.strictEqual(c.score, null); assert.strictEqual(c.eyebrow, 'Next');
  const tomorrow = build({ items: items({ sport: [Object.assign({}, next, { data: Object.assign({}, next.data, { startMs: NOW + 20 * H }) })] }), tab: 'love:football' })
    .shelves[0].cards.find(x => x.title === 'Liverpool v Arsenal');
  assert.strictEqual(tomorrow.board.line, 'Tomorrow 15:00');
  const stale = Object.assign({}, next, { data: Object.assign({}, next.data, { startMs: NOW - 4 * H }) });
  assert.ok(!build({ items: items({ sport: [stale] }) }).shelves[0].cards.some(x => x.title === 'Liverpool v Arsenal'), 'a stale NEXT card is dropped');
});
