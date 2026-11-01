// Setup: the five steps a person walks with the remote (name, home, languages and where they live, loves, summary).
// Pure step and focus logic; the page (setup.html) draws the state and runs the effects. Tested with node.
// Call Setup.load({ countries, languages, interests }) with the bundled data first.
(function (root) {
  const K = typeof module !== 'undefined' && module.exports ? require('./keyboard.js') : root.Keyboard;
  const STEPS = ['name', 'home', 'languages', 'loves', 'summary'];
  const TABS = [
    { id: 'popular', label: 'Popular near you' }, { id: 'food', label: 'Food' }, { id: 'music', label: 'Music' }, { id: 'sport', label: 'Sport' },
    { id: 'ideas', label: 'Ideas & science' }, { id: 'outdoors', label: 'Outdoors' }, { id: 'style', label: 'Style' }, { id: 'faith', label: 'Faith' },
    { id: 'search', label: 'Search anything' },
  ];
  const COLS = 4, MAX_PLACES = 3, PANEL_MAX = 8;
  const SPORT_LIVE = ['espn', 'cricinfo', 'jolpica', 'motogp'];
  let D = { countries: [], byCode: {}, langs: {}, langList: [], interests: [], byId: {} };

  function load(d) {
    D = { countries: d.countries || [], byCode: {}, langs: {}, langList: d.languages || [], interests: d.interests || [], byId: {} };
    D.countries.forEach(c => { D.byCode[c.code] = c; });
    D.langList.forEach(l => { D.langs[l.code] = l; });
    D.interests.forEach(i => { D.byId[i.id] = i; });
  }

  // ---------- small helpers ----------
  const norm = (t) => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  const list = (a) => a.length < 2 ? (a[0] || '') : a.slice(0, -1).join(', ') + ' and ' + a[a.length - 1];
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const set = (s, patch) => Object.assign({}, s, patch);
  const withF = (s, patch) => set(s, { f: Object.assign({}, s.f, patch) });
  // The name a chip shows: the language's own name, without the "Language" word some carry (Èdè Yorùbá).
  const chipName = (code) => { const l = D.langs[code]; if (!l) return code; const n = l.native.replace(/^Èdè\s+/, ''); return n.charAt(0).toUpperCase() + n.slice(1); };

  function guessLiving(tz, countries) {
    const c = (countries || D.countries).find(x => (x.timezones || []).includes(tz));
    if (!c || !c.cities.length) return null;
    const city = c.cities.find(x => x.tz === tz) || c.cities[0];
    return { name: city.name, lat: city.lat, lon: city.lon, tz: city.tz, country: c.code };
  }
  // Same id rule as native Interests.custom for Latin words; other scripts get a page hash that the TV replaces on save (Profiles.parse).
  function customLove(term) {
    const words = String(term || '').trim().split(/\s+/).filter(Boolean).join(' ');
    const folded = norm(words), slug = folded.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    let id = slug;
    if (!/^[a-z0-9 -]*$/.test(folded) || !slug) { let h = 2166136261; for (const ch of words.toLowerCase()) h = Math.imul(h ^ ch.codePointAt(0), 16777619) >>> 0; id = (slug ? slug + '-' : '') + h.toString(16); }
    return { id: 'custom:' + id, refine: [], custom: words };
  }

  // ---------- what each step shows ----------
  const STEP_FOCUS = {
    name: () => ({ zone: 'kb' }),
    home: (s) => ({ zone: 'search', i: Math.max(0, D.countries.findIndex(c => c.code === (s.home.country || (s.living && s.living.country)))), ci: 0 }),
    languages: () => ({ zone: 'chips', i: 0, li: 0, ri: 0 }),
    loves: () => ({ zone: 'grid', tab: 0, i: 0, pi: 0, said: false }),
    summary: (s) => ({ zone: s.languages.length ? 'langs' : 'start', i: 0, grabbed: false }),
  };
  const focusOf = (s, step) => s.step === step ? s.f : (s.mem[step] || STEP_FOCUS[step](s));
  const countryAt = (s) => D.countries[focusOf(s, 'home').i] || null;
  function cityRows(s) {
    const c = countryAt(s);
    const rows = s.towns ? s.towns.slice() : (c ? c.cities.map(x => ({ name: x.name, lat: x.lat, lon: x.lon, tz: x.tz, cc: c.code })) : []);
    rows.push({ other: true, name: c && !s.towns ? `Another town in ${c.name}` : 'Search another town' });
    return rows;
  }
  function chips(s) {
    const out = [];
    const add = (code) => { if (code && !out.includes(code)) out.push(code); };
    const home = D.byCode[s.home.country], live = s.living && D.byCode[s.living.country];
    ((home && home.languages) || []).forEach(add);
    ((live && live.languages) || []).forEach(add);
    s.languages.forEach(add);
    if (!out.length) add('en');
    out.push('more');
    return out;
  }
  const loved = (s, id) => s.loves.find(l => l.id === id) || null;
  function tiles(s, tabId) {
    const tab = tabId || TABS[focusOf(s, 'loves').tab].id;
    const tile = (i) => ({ id: i.id, name: i.name, image: i.image, refine: i.refine || null });
    if (tab !== 'search') {
      // Home country first, then where the person lives, then world-generic loves, then other countries' (interests.json countries).
      const home = s.home && s.home.country, live = s.living && s.living.country;
      const rank = (i) => { const cc = i.countries || []; return !cc.length ? 2 : cc.includes(home) ? 0 : cc.includes(live) ? 1 : 3; };
      const mine = (i) => rank(i) < 2 && ['food', 'music'].includes(i.theme);   // Popular near you also brings the home cuisines and music
      const list = D.interests.filter(i => i.theme === tab || (i.also || []).includes(tab) || (tab === 'popular' && mine(i)));
      return list.map((i, k) => ({ i, k, r: rank(i) })).sort((a, b) => a.r - b.r || a.k - b.k).map(x => tile(x.i));
    }
    const q = norm(s.loveQuery);
    if (!q) return [];
    const hits = D.interests.filter(i => norm(i.name).includes(q) || (i.terms || []).some(t => { const n = norm(t); return n.includes(q) || (n.length >= 4 && q.includes(n)); }));
    const out = hits.slice(0, 11).map(tile);
    if (!hits.some(i => norm(i.name) === q)) { const c = customLove(s.loveQuery); out.push({ id: c.id, name: c.custom, image: null, refine: null, custom: c.custom }); }
    return out;
  }
  // The follow-up beside the grid: for the focused tile when it is loved and has one, else the last one opened while still loved.
  function panel(s) {
    const f = focusOf(s, 'loves'), t = tiles(s)[f.i];
    const pick = (t && t.refine && loved(s, t.id)) ? t : (s.panelId && loved(s, s.panelId) && D.byId[s.panelId] && D.byId[s.panelId].refine ? { id: s.panelId, name: D.byId[s.panelId].name, refine: D.byId[s.panelId].refine } : null);
    if (!pick) return null;
    const home = s.home && s.home.country, live = s.living && s.living.country;   // an option tied to countries (Indian players) shows only for them
    const opts = pick.refine.options.filter(o => !o.countries || o.countries.includes(home) || o.countries.includes(live));
    return { id: pick.id, name: pick.name, label: pick.refine.label, options: opts.slice(0, PANEL_MAX), chosen: loved(s, pick.id).refine };
  }
  function matchCountry(text) {
    const q = norm(text); if (!q) return -1;
    let i = D.countries.findIndex(c => norm(c.name).startsWith(q));
    if (i < 0) i = D.countries.findIndex(c => norm(c.name).includes(q));
    if (i < 0) i = D.countries.findIndex(c => c.cities.some(x => norm(x.name).startsWith(q)));
    return i;
  }
  function matchLanguage(text) {
    const q = norm(text); if (!q) return null;
    return D.langList.find(l => norm(l.name).startsWith(q) || norm(l.native).startsWith(q) || norm(chipName(l.code)).startsWith(q)) || null;
  }

  // ---------- state ----------
  function init(o) {
    o = o || {};
    const living = guessLiving(o.tz);
    const s = { step: 'name', name: o.name || '', home: { country: null, places: [] }, languages: [], living, livingGuess: living, livingSure: false,
      loves: [], query: '', towns: null, livingResults: null, loveQuery: '', langMatch: null, panelId: null, mem: {}, effect: null, f: { zone: 'kb' }, kb: null, editing: !!o.from };
    const p = o.from;   // Change my setup: every step starts from the saved choices
    if (p) Object.assign(s, { name: p.name, home: { country: p.home.country, places: p.home.places.map(x => Object.assign({}, x)) }, languages: p.languages.slice(),
      living: Object.assign({}, p.living), livingSure: true, loves: p.loves.map(l => l.custom ? { id: l.id, refine: [], custom: l.custom } : { id: l.id, refine: (l.refine || []).slice() }) });
    s.kb = Object.assign(K.init(s.name, 'words'), { for: 'name' });
    if (p) { const at = K.find('done'); Object.assign(s.kb, { r: at.r, c: at.c }); }   // the name is already there: Select keeps it
    return s;
  }
  function go(s, step) {
    const mem = Object.assign({}, s.mem, { [s.step]: s.f });
    let n = set(s, { step, mem, kb: null });
    n.f = mem[step] || STEP_FOCUS[step](n);
    if (step === 'name') { const k = K.init(s.name, 'words'), at = K.find('done'); n.kb = Object.assign(k, { r: at.r, c: at.c, for: 'name' }); }
    if (step === 'summary') n.f = Object.assign({}, n.f, { zone: n.languages.length ? n.f.zone : 'start', i: clamp(n.f.i, 0, Math.max(0, n.languages.length - 1)), grabbed: false });
    return n;
  }
  const openKb = (s, what, text, caps) => set(s, { kb: Object.assign(K.init(text || '', caps || 'first'), { for: what }) });

  // The keyboard is open: keys type; Done or Back closes it and the step takes the text.
  function keyboard(s, key) {
    const what = s.kb.for;
    if (key === 'Back' && !s.kb.accents) {
      if (what === 'name') return set(s, { effect: { type: 'exit' } });
      return set(s, { kb: null });
    }
    const k = Object.assign(K.press(s.kb, key), { for: what }), text = k.text.trim();
    let n = set(s, { kb: k });
    if (what === 'name') { n.name = text; if (k.done && text) return go(n, 'home'); if (k.done) n.kb = Object.assign({}, k, { done: false }); return n; }
    if (what === 'home') {
      n.query = text; const i = matchCountry(text); if (i >= 0) n = withF(n, { i });
      if (!k.done) return n;
      n.kb = null; n.towns = null;
      if (!text) return withF(n, { zone: 'list' });
      if (i >= 0) return withF(n, { zone: 'list' });
      return set(withF(n, { zone: 'list' }), { effect: { type: 'geocode', for: 'home', query: text } });
    }
    if (what === 'town') { if (!k.done) return n; n.kb = null; return text ? set(n, { effect: { type: 'geocode', for: 'town', query: text, country: (countryAt(n) || {}).code } }) : n; }
    if (what === 'lang') {
      n.langMatch = matchLanguage(text);
      if (!k.done) return n;
      n.kb = null;
      if (n.langMatch && !n.languages.includes(n.langMatch.code)) { n.languages = n.languages.concat(n.langMatch.code); n = withF(n, { i: chips(n).indexOf(n.langMatch.code) }); }
      n.langMatch = null; return n;
    }
    if (what === 'living') { if (!k.done) return n; n.kb = null; return text ? set(n, { effect: { type: 'geocode', for: 'living', query: text } }) : n; }
    if (what === 'love') {
      if (!k.done) return n;
      n = set(n, { kb: null, loveQuery: text });
      return withF(n, { zone: tiles(n).length ? 'grid' : 'tabs', i: 0 });
    }
    return n;
  }

  // Answers from the page's geocoding (Open-Meteo through the travel service): [{ name, admin1, country, cc, lat, lon, tz }].
  function found(s, what, results) {
    const rows = (results || []).filter(r => r && D.byCode[r.cc]).slice(0, 5).map(r => ({ name: r.name, lat: r.lat, lon: r.lon, tz: r.tz, cc: r.cc, sub: [r.admin1, D.byCode[r.cc].name].filter((x, k, a) => x && a.indexOf(x) === k).join(', ') }));
    if (what === 'living') return set(withF(s, rows.length ? { zone: 'results', ri: 0 } : { zone: 'living', li: 1 }), { livingResults: rows.length ? rows : null });
    const cc = what === 'town' ? (countryAt(s) || {}).code : null;
    const towns = cc && rows.some(r => r.cc === cc) ? rows.filter(r => r.cc === cc) : rows;
    if (!towns.length) return s;
    const i = what === 'home' ? D.countries.findIndex(c => c.code === towns[0].cc) : s.f.i;
    return set(withF(s, { zone: 'cities', ci: 0, i }), { towns });
  }

  function togglePlace(s, row) {
    const place = { name: row.name, lat: row.lat, lon: row.lon, tz: row.tz };
    if (s.home.country !== row.cc) return set(s, { home: { country: row.cc, places: [place] } });
    const has = s.home.places.some(p => p.name === row.name);
    const places = has ? s.home.places.filter(p => p.name !== row.name) : s.home.places.concat(place).slice(0, MAX_PLACES);
    return set(s, { home: { country: places.length ? row.cc : null, places } });
  }

  // ---------- keys ----------
  function next(s, key) {
    s = set(s, { effect: null });
    if (s.kb) return keyboard(s, key);
    const f = s.f;
    if (s.step === 'home') {
      if (key === 'Back' && f.zone === 'cities') return set(withF(s, { zone: 'list' }), { towns: null });
      if (key === 'Back') return go(s, 'name');
      if (f.zone === 'search') {
        if (key === 'Enter') return openKb(s, 'home', s.query, 'words');
        if (key === 'ArrowDown') return withF(s, { zone: 'list' });
        if (key === 'ArrowRight') return withF(s, { zone: 'next', from: 'search' });
        return s;
      }
      if (f.zone === 'list') {
        if (key === 'ArrowUp') return f.i > 0 ? withF(s, { i: f.i - 1 }) : withF(s, { zone: 'search' });
        if (key === 'ArrowLeft') return withF(s, { zone: 'search' });   // a list of 249 countries: Search is one key away
        if (key === 'ArrowDown') return withF(s, { i: Math.min(D.countries.length - 1, f.i + 1) });
        if (key === 'Enter') return set(withF(s, { zone: 'cities', ci: 0 }), { towns: null });
        if (key === 'ArrowRight') return withF(s, { zone: 'next', from: 'list' });
        return s;
      }
      if (f.zone === 'cities') {
        const rows = cityRows(s);
        if (key === 'ArrowUp') return withF(s, { ci: Math.max(0, f.ci - 1) });
        if (key === 'ArrowDown') return withF(s, { ci: Math.min(rows.length - 1, f.ci + 1) });
        if (key === 'ArrowRight') return withF(s, { zone: 'next', from: 'cities' });
        if (key === 'Enter') { const row = rows[f.ci]; return row.other ? openKb(s, 'town', '', 'words') : togglePlace(s, row); }
        return s;
      }
      if (f.zone === 'next') {
        // Nothing picked yet: Next picks the highlighted country's first city and shows it; the next Select goes on. Never another place.
        if (key === 'Enter' && !s.home.places.length) { const c = countryAt(s), first = c && c.cities[0]; return first ? togglePlace(s, { name: first.name, lat: first.lat, lon: first.lon, tz: first.tz, cc: c.code }) : s; }
        if (key === 'Enter') return go(s, 'languages');
        if (key === 'ArrowLeft' || key === 'ArrowUp') return withF(s, { zone: f.from || 'list' });
        return s;
      }
      return s;
    }
    if (s.step === 'languages') {
      const c = chips(s);
      if (key === 'Back' && f.zone === 'results') return set(withF(s, { zone: 'living', li: 1 }), { livingResults: null });
      if (key === 'Back') return go(s, 'home');
      if (f.zone === 'chips') {
        if (key === 'ArrowLeft') return withF(s, { i: Math.max(0, f.i - 1) });
        if (key === 'ArrowRight') return f.i < c.length - 1 ? withF(s, { i: f.i + 1 }) : withF(s, { zone: 'living', li: 0 });
        if (key === 'ArrowDown') return withF(s, { zone: 'next' });
        if (key === 'Enter') {
          const code = c[clamp(f.i, 0, c.length - 1)];
          if (code === 'more') return openKb(s, 'lang', '', 'first');
          return set(s, { languages: s.languages.includes(code) ? s.languages.filter(x => x !== code) : s.languages.concat(code) });
        }
        return s;
      }
      if (f.zone === 'living') {
        if (key === 'ArrowLeft') return f.li > 0 ? withF(s, { li: 0 }) : withF(s, { zone: 'chips', i: c.length - 1 });
        if (key === 'ArrowRight') return f.li < 1 ? withF(s, { li: 1 }) : withF(s, { zone: 'next' });
        if (key === 'ArrowDown') return withF(s, { zone: 'next' });
        if (key === 'Enter') return f.li === 0 ? withF(set(s, { livingSure: true }), { zone: 'next' }) : openKb(s, 'living', '', 'words');
        return s;
      }
      if (f.zone === 'results') {
        const r = s.livingResults || [];
        if (key === 'ArrowUp') return withF(s, { ri: Math.max(0, f.ri - 1) });
        if (key === 'ArrowDown') return withF(s, { ri: Math.min(r.length - 1, f.ri + 1) });
        if (key === 'Enter' && r[f.ri]) { const p = r[f.ri]; return withF(set(s, { living: { name: p.name, lat: p.lat, lon: p.lon, tz: p.tz, country: p.cc }, livingSure: true, livingResults: null }), { zone: 'next' }); }
        return s;
      }
      if (f.zone === 'next') {
        if (key === 'Enter') return go(s, 'loves');
        if (key === 'ArrowUp' || key === 'ArrowLeft') return withF(s, { zone: 'chips' });
        return s;
      }
      return s;
    }
    if (s.step === 'loves') {
      const t = tiles(s), n = t.length, col = f.i % COLS, p = panel(s);
      if (key === 'Back' && f.zone === 'panel') return withF(s, { zone: 'grid', said: true });
      if (key === 'Back') return go(s, 'languages');
      if (f.zone === 'tabs') {
        if (key === 'ArrowLeft' || key === 'ArrowRight') return withF(s, { tab: clamp(f.tab + (key === 'ArrowRight' ? 1 : -1), 0, TABS.length - 1), i: 0, said: false });
        if (key === 'ArrowDown' || key === 'Enter') {
          if (TABS[f.tab].id === 'search' && (key === 'Enter' || !n)) return openKb(s, 'love', s.loveQuery, 'first');
          return n ? withF(s, { zone: 'grid', i: 0 }) : s;
        }
        return s;
      }
      if (f.zone === 'grid') {
        const tile = t[f.i];
        if (key === 'ArrowLeft') return col > 0 ? withF(s, { i: f.i - 1, said: false }) : s;
        if (key === 'ArrowRight') {
          if (tile && tile.refine && loved(s, tile.id) && !f.said) return set(withF(s, { zone: 'panel', pi: 0 }), { panelId: tile.id });
          if (col < COLS - 1 && f.i + 1 < n) return withF(s, { i: f.i + 1, said: false });
          return p ? set(withF(s, { zone: 'panel', pi: 0 }), { panelId: p.id }) : withF(s, { zone: 'next' });
        }
        if (key === 'ArrowUp') return f.i >= COLS ? withF(s, { i: f.i - COLS, said: false }) : withF(s, { zone: 'tabs' });
        if (key === 'ArrowDown') {
          if (f.i + COLS < n) return withF(s, { i: f.i + COLS, said: false });
          if (Math.floor(f.i / COLS) < Math.floor((n - 1) / COLS)) return withF(s, { i: n - 1, said: false });
          return withF(s, { zone: 'next' });
        }
        if (key === 'Enter' && tile) {
          if (loved(s, tile.id)) return withF(set(s, { loves: s.loves.filter(l => l.id !== tile.id) }), { said: false });
          const love = tile.custom ? { id: tile.id, refine: [], custom: tile.custom } : { id: tile.id, refine: [] };
          return withF(set(s, { loves: s.loves.concat(love), panelId: tile.refine ? tile.id : s.panelId }), { said: false });
        }
        return s;
      }
      if (f.zone === 'panel') {
        const opts = p ? p.options : [];
        if (!p) return withF(s, { zone: 'grid' });
        if (key === 'ArrowLeft' || key === 'ArrowUp') return f.pi > 0 ? withF(s, { pi: f.pi - 1 }) : withF(s, { zone: 'grid', said: true });
        if (key === 'ArrowRight') return withF(s, { pi: Math.min(opts.length - 1, f.pi + 1) });
        if (key === 'ArrowDown') return f.pi < opts.length - 1 ? withF(s, { pi: f.pi + 1 }) : withF(s, { zone: 'next' });
        if (key === 'Enter' && opts[f.pi]) {
          const id = opts[f.pi].id;
          return set(s, { loves: s.loves.map(l => l.id !== p.id ? l : Object.assign({}, l, { refine: l.refine.includes(id) ? l.refine.filter(x => x !== id) : l.refine.concat(id) })) });
        }
        return s;
      }
      if (f.zone === 'next') {
        if (key === 'Enter') return go(s, 'summary');
        if (key === 'ArrowUp' || key === 'ArrowLeft') return withF(s, { zone: n ? 'grid' : 'tabs' });
        return s;
      }
      return s;
    }
    if (s.step === 'summary') {
      if (key === 'Back' && f.grabbed) return withF(s, { grabbed: false });
      if (key === 'Back') return go(s, 'loves');
      if (f.zone === 'langs') {
        const L = s.languages;
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
          const j = clamp(f.i + (key === 'ArrowRight' ? 1 : -1), 0, L.length - 1);
          if (!f.grabbed || j === f.i) return withF(s, { i: j });
          const langs = L.slice(); langs.splice(j, 0, langs.splice(f.i, 1)[0]);
          return withF(set(s, { languages: langs }), { i: j });
        }
        if (key === 'Enter') return withF(s, { grabbed: !f.grabbed });
        if (key === 'ArrowDown') return withF(s, { zone: 'start', grabbed: false });
        return s;
      }
      if (f.zone === 'start') {
        if (key === 'Enter') return set(s, { effect: { type: 'save' } });
        if (key === 'ArrowUp' && s.languages.length) return withF(s, { zone: 'langs' });
        return s;
      }
    }
    return s;
  }

  // ---------- the result ----------
  function profile(s, o) {
    o = o || {};
    const h = s.home.places.length ? s.home : null;
    const living = s.living || (h ? Object.assign({}, h.places[0], { country: h.country }) : null);
    if (!living || !s.name) return null;
    const home = h ? { country: h.country, places: h.places.map(p => ({ name: p.name, lat: p.lat, lon: p.lon, tz: p.tz })) }
      : { country: living.country, places: [{ name: living.name, lat: living.lat, lon: living.lon, tz: living.tz }] };
    const first = ((D.byCode[home.country] || {}).languages || [])[0] || 'en';
    return {
      id: o.id, name: s.name, home, languages: s.languages.length ? s.languages.slice() : [first],
      living: { name: living.name, lat: living.lat, lon: living.lon, tz: living.tz, country: living.country },
      loves: s.loves.map(l => keep(l.custom ? { id: l.id, refine: [], custom: l.custom, since: o.now } : { id: l.id, refine: l.refine.slice(), since: o.now }, o.was)),
      created: (o.was && o.was.created) || o.now,
    };
  }

  // A love kept through Change my setup keeps when it was chosen and what Orbit learned about it.
  function keep(l, was) {
    const w = ((was && was.loves) || []).find(x => x.id === l.id); if (!w) return l;
    const n = Object.assign({}, l); if (w.since != null) n.since = w.since; if (w.weight != null) n.weight = w.weight; return n;
  }

  // What a sport calls its start (interests.json live.startWord: match times, race times, kick-off times); start times without one or for a mix.
  function startWord(ids) {
    const words = [...new Set(ids.map(id => D.byId[id]).filter(i => i && i.live && SPORT_LIVE.includes(i.live.kind)).map(i => i.live.startWord || 'start times'))];
    return words.length === 1 ? words[0] : 'start times';
  }

  // What Orbit will now do, in plain lines for the summary screen.
  function summary(p, world, interests) {
    if (world && interests) load({ countries: world.countries, languages: world.languages, interests });
    const out = [], langs = p.languages.map(chipName), film = (D.langs[p.languages[0]] || {}).film;
    out.push(`Films and series in ${langs[0]} first${langs.length > 1 ? ', then ' + list(langs.slice(1)) : ''}${film ? `, with a ${film} tab` : ''}`);
    const live = [], rest = [], custom = [];
    p.loves.forEach(l => {
      if (l.custom) { custom.push(l.custom); return; }
      const i = D.byId[l.id]; if (!i) return;
      if (i.live && SPORT_LIVE.includes(i.live.kind)) {
        const chosen = ((i.refine && i.refine.options) || []).filter(o => l.refine.includes(o.id)).map(o => o.name);
        live.push(...(chosen.length ? chosen : [i.name]));
      } else rest.push(i.name);
    });
    if (live.length) out.push(`${list(live)} live, with ${startWord(p.loves.map(l => l.id))} in ${p.living.name} time`);
    if (rest.length) out.push(`${list(rest)}: shows, videos and news picked for you`);
    if (custom.length) out.push(`${list(custom)}: less tuned for now, Orbit learns as you watch`);
    if (!p.loves.length) out.push('No loves yet: Orbit learns from what you watch');
    const hc = D.byCode[p.home.country], lc = D.byCode[p.living.country];
    out.push(`Home news from ${list(p.home.places.map(x => x.name))}, with ${hc ? hc.name + "'s" : 'its'} time, weather and festival days`);
    out.push(`Clock, weather, streaming and local news for ${p.living.name}${lc ? ', ' + lc.name : ''}`);
    return out;
  }

  // A short weather phrase for a pin or a card: "27° warm evening", "9° rain".
  function weatherLine(w, hour) {
    if (!w) return '';
    const calm = ['clear', 'mostly clear', 'partly cloudy'].includes(w.desc);
    const part = hour < 5 ? 'night' : hour < 12 ? 'morning' : hour < 17 ? 'afternoon' : hour < 22 ? 'evening' : 'night';
    const feel = w.tempC >= 24 ? 'warm' : w.tempC <= 8 ? 'cold' : 'mild';
    return `${w.tempC}° ${calm ? feel + ' ' + part : w.desc}`;
  }

  const Setup = { STEPS, TABS, COLS, load, init, next, found, profile, summary, guessLiving, customLove, chips, chipName, tiles, panel, cityRows, countryAt, focusOf, loved, matchCountry, weatherLine, list, startWord };
  if (typeof module !== 'undefined' && module.exports) module.exports = Setup; else root.Setup = Setup;
})(this);
