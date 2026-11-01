// Taste: an on-device profile per home that learns from what is picked in Orbit. Pure functions, no DOM; tested with node.
(function (root) {
  const DELTA = { pick: 1, watched: 2, quick: -0.5 };
  const MIN = -5, MAX = 8, DAILY = 0.97, TASTE = 0.3;   // taste counts at 0.3 so one pick never outweighs popularity

  function features(t) {
    const y = parseInt(t.year, 10);
    return [...(t.genres || []).map(g => 'g:' + g), 'k:' + t.kind, 'd:' + (y ? Math.floor(y / 10) * 10 + 's' : 'unknown'), 'a:' + ((t.apps && t.apps[0] && t.apps[0].name) || 'none')];
  }
  const empty = () => ({ w: {}, at: 0 });

  function decay(p, nowMs) {
    p = p || empty(); if (!p.at) return { w: Object.assign({}, p.w), at: nowMs };
    const f = Math.pow(DAILY, Math.max(0, nowMs - p.at) / 86400000), w = {};
    Object.keys(p.w).forEach(k => { w[k] = p.w[k] * f; });
    return { w, at: nowMs };
  }

  function learn(p, title, signal, nowMs) {
    const d = DELTA[signal]; const q = decay(p, nowMs); if (!d) return q;
    features(title).forEach(f => { q.w[f] = Math.max(MIN, Math.min(MAX, (q.w[f] || 0) + d)); });
    return q;
  }

  const score = (t, w) => TASTE * features(t).reduce((s, f) => s + (w[f] || 0), 0) + Math.log10(1 + (t.popularity || 0));

  // Small deterministic generator so the same seed gives the same exploration picks.
  function rng(seed) { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

  function rank(titles, profile, seed) {
    const w = (profile && profile.w) || {};
    const ranked = (titles || []).map((t, i) => ({ t, s: score(t, w), i })).sort((a, b) => b.s - a.s || a.i - b.i).map(x => x.t);
    if (ranked.length <= 10) return ranked;
    const pool = ranked.slice(10), next = rng(seed || 1), used = new Set(), out = [];
    let k = 0;
    while (out.length < ranked.length) {
      if ((out.length + 1) % 5 === 0 && pool.some(t => !used.has(t))) {   // slots 4, 9, 14, ... explore
        const free = pool.filter(t => !used.has(t)); const pick = free[Math.floor(next() * free.length)]; used.add(pick); out.push(pick); continue;
      }
      while (used.has(ranked[k])) k++;
      used.add(ranked[k]); out.push(ranked[k]); k++;
    }
    return out;
  }

  // Minutes spent in the app after a pick: a long stay means it was watched, a quick return means it was not what you wanted.
  const signalFor = (minutes) => !(minutes >= 0) ? null : minutes >= 20 ? 'watched' : minutes < 2 ? 'quick' : null;

  // Only the app that was launched for a pick, coming back within six hours, can teach that title.
  function attribute(pick, returnedPkg, nowMs) {
    if (!pick || !pick.pkg || pick.pkg !== returnedPkg || !(nowMs - pick.at <= 6 * 3600000)) return null;
    return signalFor((nowMs - pick.at) / 60000);
  }

  // History: one entry per title, newest first, keeping the strongest signal seen (watched > pick > quick); at most 30.
  const STRENGTH = { quick: 0, pick: 1, watched: 2 };
  const idOf = (t) => `${t.kind}:${t.id}`;
  function remember(history, title, signal, nowMs) {
    const old = (history || []).find(e => idOf(e.title) === idOf(title));
    const keep = old && STRENGTH[old.signal] > STRENGTH[signal] ? old.signal : signal;
    return [{ title, signal: keep, at: nowMs }, ...(history || []).filter(e => idOf(e.title) !== idOf(title))].slice(0, 30);
  }
  // Up to two loved titles (picked or watched, newest first) that share a genre with this one.
  function reason(history, title) {
    const g = new Set(title.genres || []);
    return (history || []).filter(e => e.signal !== 'quick' && idOf(e.title) !== idOf(title) && (e.title.genres || []).some(x => g.has(x))).slice(0, 2).map(e => e.title.title);
  }
  // A percentage only once the profile has learned something; no invented numbers for a new person.
  function match(profile, title) {
    const w = (profile && profile.w) || {}; if (!Object.keys(w).some(k => w[k])) return null;
    const s = TASTE * features(title).reduce((a, f) => a + (w[f] || 0), 0);
    return Math.round(50 + 49 * (1 - Math.exp(-Math.max(0, s))));
  }

  // ---- Learning across loves ----
  // A watch event is a history entry from Taste.remember ({ title, signal, at }) or Journeys.remember ({ place, mode, at, loved }),
  // optionally carrying `love` (the love id of the row it was opened from) and `minutes` watched.
  const DAY = 86400000, FADE_AFTER = 30 * DAY, OFFER_AT = 3, OFFER_WINDOW = 60 * DAY, MAX_IGNORED = 2;

  const fold = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/’/g, "'");
  const isWordChar = (c) => !!c && (c.toLowerCase() !== c.toUpperCase() || (c >= '0' && c <= '9'));

  // Same rule as the Kotlin Relevance.keep: a whole word or phrase, a plain plural "s"/"es" allowed, case and accents ignored.
  function termIn(text, terms) {
    const t = fold(text); if (!t.trim()) return false;
    return (terms || []).some(raw => {
      const term = fold(raw).trim().replace(/\s+/g, ' '); if (!term) return false;
      for (let i = t.indexOf(term); i >= 0; i = t.indexOf(term, i + 1)) {
        if (isWordChar(t[i - 1])) continue;
        const e = i + term.length;
        if (!isWordChar(t[e]) || (t[e] === 's' && !isWordChar(t[e + 1])) || (t[e] === 'e' && t[e + 1] === 's' && !isWordChar(t[e + 2]))) return true;
      }
      return false;
    });
  }

  const textOf = (e) => {
    const t = e.title || {}, p = e.place || {};
    return [e.text, t.title, t.name, t.overview, p.name, p.title].filter(Boolean).join(' . ');
  };
  // How much one event counts: a pick or a loved place is 1, a long watch 2, a quick return nothing; minutes add up to 2 more.
  function gain(e) {
    if (e.signal === 'quick') return 0;
    const base = e.signal === 'watched' || e.loved ? 2 : 1;
    return base + Math.min(2, Math.max(0, e.minutes || 0) / 30);
  }
  const byId = (catalogue) => { const m = {}; (catalogue || []).forEach(i => { m[i.id] = i; }); return m; };
  // Terms that point at a chosen love: its picked refinements (else the whole interest) and any custom term.
  function loveTerms(love, cat) {
    const opts = ((cat && cat.refine && cat.refine.options) || []).filter(o => (love.refine || []).includes(o.id));
    const base = opts.length ? opts.reduce((a, o) => a.concat(o.terms || []), []) : (cat ? [cat.name].concat(cat.terms || []) : []);
    return base.concat(love.custom ? [love.custom] : []);
  }
  const interestTerms = (i) => [i.name].concat(i.terms || []);
  const points = (e, id, terms) => e.love ? e.love === id : termIn(textOf(e), terms);

  // Weight of each chosen love: 1 plus what was watched (each event fading 3% a day). A love not touched for 30 days fades
  // toward 0.2 but is never removed. A love never watched fades from `love.since` (else the profile's `created`, passed last); with neither date it stays at 1.
  function loveWeights(history, loves, nowMs, catalogue, created) {
    const cats = byId(catalogue), out = {};
    (loves || []).forEach(love => {
      const terms = loveTerms(love, cats[love.id]);
      const mine = (history || []).filter(e => e.at && points(e, love.id, terms));
      let w = 1, last = 0;
      mine.forEach(e => { const g = gain(e); if (g > 0) { w += g * Math.pow(DAILY, Math.max(0, nowMs - e.at) / DAY); last = Math.max(last, e.at); } });
      last = Math.max(last, love.since || created || 0);   // a love never watched fades from when it was chosen
      const idle = last ? nowMs - last : 0;
      const f = idle <= FADE_AFTER ? 1 : Math.max(0.2, 0.5 * Math.pow(0.5, (idle - FADE_AFTER) / (60 * DAY)));
      out[love.id] = Math.round(w * f * 100) / 100;
    });
    return out;
  }
  // A copy of the profile with `weight` set on each love that has one in `weights`; the input is not touched.
  const withWeights = (profile, weights) => Object.assign({}, profile, {
    loves: ((profile && profile.loves) || []).map(l => (weights && weights[l.id] != null) ? Object.assign({}, l, { weight: weights[l.id] }) : l),
  });

  // Offer state (storable): { shown: {id: times shown}, ignored: {id: times ignored}, last: {id: ms of the last showing},
  // accepted: {id: true}, at: ms of the last change }.
  const offerState = () => ({ shown: {}, ignored: {}, last: {}, accepted: {}, at: 0 });
  const bump = (s, key, id, nowMs, f) => { const n = Object.assign(offerState(), s); n[key] = Object.assign({}, n[key], { [id]: f(n[key][id]) }); n.at = nowMs; return n; };
  const offerShown = (s, id, nowMs) => { const n = bump(s, 'shown', id, nowMs, v => (v || 0) + 1); n.last = Object.assign({}, n.last, { [id]: nowMs }); return n; };
  const offerIgnored = (s, id, nowMs) => bump(s, 'ignored', id, nowMs, v => (v || 0) + 1);
  const offerAccepted = (s, id, nowMs) => bump(s, 'accepted', id, nowMs, () => true);

  // The interest to offer, or null. Needs three or more watched or picked items (quick returns do not count) that point at an
  // interest the person has not chosen, newer than the last time it was offered and within 60 days. Two ignores or an accept
  // end the offer. `state` is the stored offer state; `nowMs` defaults to the newest event, for tests and old callers.
  function noticed(history, loves, catalogue, state, nowMs) {
    const s = Object.assign(offerState(), state), chosen = new Set((loves || []).map(l => l.id));
    const evs = (history || []).filter(e => e.at && gain(e) > 0);
    const now = nowMs || evs.reduce((m, e) => Math.max(m, e.at), 0);
    let best = null;
    (catalogue || []).forEach(i => {
      if (chosen.has(i.id) || s.accepted[i.id] || (s.ignored[i.id] || 0) >= MAX_IGNORED) return;
      const terms = interestTerms(i), since = s.last[i.id] || 0;
      const hits = evs.filter(e => e.at > since && now - e.at <= OFFER_WINDOW && points(e, i.id, terms));
      if (hits.length < OFFER_AT) return;
      const newest = hits.reduce((m, e) => Math.max(m, e.at), 0);
      if (!best || hits.length > best.n || (hits.length === best.n && newest > best.newest)) best = { i, n: hits.length, newest };
    });
    return best ? best.i : null;
  }

  const Taste = { features, empty, decay, learn, rank, signalFor, attribute, remember, reason, match, termIn, loveWeights, withWeights, offerState, offerShown, offerIgnored, offerAccepted, noticed };
  if (typeof module !== 'undefined' && module.exports) module.exports = Taste; else root.Taste = Taste;
})(this);
