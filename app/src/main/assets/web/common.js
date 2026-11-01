// Shared runtime for the DOM pages (home and wall): the native API, state, items, launching, the reader, keys.
// Pages register Shell.on('data', render) and Shell.on('key', fn). The orbit page (screensaver) has its own runtime.
window.Shell = (function () {
  const $ = (id) => document.getElementById(id);
  const log = (m) => { try { Android.log(m); } catch (e) { console.log(m); } };
  const listeners = {}; const on = (ev, fn) => { (listeners[ev] = listeners[ev] || []).push(fn); }; const emit = (ev, ...a) => (listeners[ev] || []).forEach(fn => { try { fn(...a); } catch (e) { log(`page error in ${ev}: ${e && e.stack || e}`); } });
  // livingName: the name of the country the person lives in (pages that load countries.json set it), for the Netflix Top 10 labels
  const state = {
    apps: [], feeds: { live: [], today: [], channels: [], tonight: [], homes: {}, loves: {}, sportsBy: [] }, livingName: '', countryNames: {}, catalog: {}, profiles: [],
    mood: 'watch', system: { audio: false, country: '' }, sky: null, well: Wellbeing.summary([]),
    persona: Personal.personas[Math.floor(Math.random() * Personal.personas.length)], hostLine: '',
  };
  try { const c = JSON.parse(localStorage.getItem('orbit.sky.v1') || 'null'); state.sky = c && Date.now() - c.at < 30 * 60 * 1000 ? c.data : null; } catch (e) {}
  Sky.load().then(s => { if (s) { state.sky = s; emit('data'); } }).catch(() => {});

  const ago = (ms) => { const m = Math.round((Date.now() - ms) / 60000); if (m < 1) return 'just now'; if (m < 60) return `${m} min ago`; const h = Math.round(m / 60); return h < 36 ? `${h} h ago` : `${Math.round(h / 24)} days ago`; };
  const hhmm = (d) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  function refreshWell() { state.well = Wellbeing.summary(state.apps); document.body.classList.toggle('winddown', state.well.windDown); }
  function newHostLine() {
    const ins = Personal.insights(state.apps, null, state.sky), nudges = Wellbeing.nudges(state.well, state.persona), sport = Feeds.hostLine(state.persona, myLive());
    state.hostLine = nudges.length && Math.random() < 0.6 ? nudges[0] : sport && Math.random() < 0.5 ? sport : Personal.hostLine(state.persona, ins);
    return state.hostLine;
  }

  // ---------- moods: how the person watching feels tonight reshapes the home ----------
  const MOODS = [
    { id: 'watch', label: 'Something good', accent: '#3df2ff', frames: 'cyan', order: ['tonight', 'cont', 'ch', 'sport', 'today', 'sys'], voice: 'Something good, then.' },
    { id: 'laugh', label: 'Make me laugh', accent: '#ffb347', frames: 'amber', order: ['tonight', 'ch', 'cont', 'sport', 'today', 'sys'], voice: 'Laugh mode. No sad films allowed.' },
    { id: 'thrill', label: 'Keep me on edge', accent: '#ff3b3b', frames: 'violet', order: ['tonight', 'sport', 'cont', 'ch', 'today', 'sys'], voice: 'Edge of the seat it is.' },
    { id: 'learn', label: 'Teach me something', accent: '#6cffb8', frames: 'mint', order: ['today', 'ch', 'tonight', 'cont', 'sport', 'sys'], voice: 'Curious tonight. Good.' },
    { id: 'cricket', label: 'Match day', accent: '#ffd166', frames: 'amber', order: ['sport', 'ch', 'cont', 'tonight', 'today', 'sys'], voice: 'Match day. Scores first.' },
    { id: 'chill', label: 'Wind me down', accent: '#c77dff', frames: 'violet', order: ['cont', 'tonight', 'ch', 'sys', 'sport', 'today'], quiet: true, voice: 'Winding down. Lights low.' },
  ];
  // Openers: one chip per person, "Feels like <first home place>", opens Home Mode for that person's home instead of reshaping the hero.
  function homeChips() {
    for (let i = MOODS.length - 1; i >= 0; i--) if (MOODS[i].opens) MOODS.splice(i, 1);
    profiles().forEach(p => { const pl = ((p.home && p.home.places) || [])[0]; if (pl) MOODS.push({ id: 'home:' + p.id, label: 'Feels like ' + pl.name, opens: p.id, accent: '#ff8f3f' }); });
  }
  try { const m = localStorage.getItem('orbit.mood'); if (MOODS.some(x => x.id === m && !x.opens)) state.mood = m; } catch (e) {}
  const mood = () => MOODS.find(m => m.id === state.mood && !m.opens) || MOODS[0];
  function setMood(id) { if (!MOODS.some(m => m.id === id && !m.opens)) return; state.mood = id; try { localStorage.setItem('orbit.mood', id); } catch (e) {} applyMood(); state.hostLine = mood().voice; emit('mood'); emit('data'); }
  function applyMood() { const m = mood(); document.documentElement.style.setProperty('--accent', m.accent); }

  // ---------- small sounds, generated, muted until the first key ----------
  let audio = null, armed = false;
  function beep(freq, ms, gain, type) {
    try {
      if (!armed) return;
      audio = audio || new (window.AudioContext || window.webkitAudioContext)(); if (audio.state === 'suspended') audio.resume();
      const o = audio.createOscillator(), g = audio.createGain(), t = audio.currentTime, end = t + ms / 1000;
      o.type = type || 'sine'; o.frequency.value = freq;
      g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(gain, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, end);
      o.connect(g).connect(audio.destination); o.start(); o.stop(end + 0.02);
    } catch (e) {}
  }
  const tick = () => beep(1800, 45, 0.025, 'triangle');
  const whoosh = () => { beep(440, 160, 0.04); setTimeout(() => beep(660, 220, 0.04), 90); };

  // One person's home channels as row items (Home Mode shows the home it opened, which may be another person's).
  function channelItems(person) {
    const STALE = 30 * 60 * 1000, installed = new Set(state.apps.map(a => a.pkg));
    return (window.Profiles && Profiles.channelsFor ? Profiles.channelsFor(person, state.feeds.channels) : []).map(c => {
      const stale = c.live && c.resolvedMs && Date.now() - c.resolvedMs > STALE, live = !!c.live && !stale;   // a LIVE flag older than 30 min is not trusted
      const isApp = !c.url, missing = isApp && c.pkg && !installed.has(c.pkg);
      return { kind: 'channel', id: 'ch:' + c.id, name: c.name, art: live ? c.thumb : null, color: c.color, pkg: missing ? null : c.pkg, url: c.url, live, missing, tag: c.tag, resolvedMs: c.resolvedMs,
        eyebrow: live ? `LIVE  ·  ${c.tag}` : stale ? `LAST SEEN LIVE ${ago(c.resolvedMs).toUpperCase()}  ·  ${c.tag}` : c.tag,
        desc: live ? (c.title || 'Live now.') : c.url ? 'Not live right now. Select opens the channel in YouTube.' : missing ? 'Not installed on this TV.' : 'Installed app.' };
    }).sort((a, b) => (b.live ? 1 : 0) - (a.live ? 1 : 0));
  }

  // ---------- items: one shape for every row ----------
  function items() {
    const hour = new Date().getHours(), todayHidden = state.well.windDown || hour >= 23 || hour < 7;
    const cont = state.apps.slice().sort((a, b) => (b.lastUsedMs || 0) - (a.lastUsedMs || 0)).slice(0, 10).map(a => ({
      // Android counts app use for the whole TV: it is labelled as the TV's, never as the person's
      kind: 'app', id: 'app:' + a.pkg, name: a.label, art: a.art, color: a.brand, pkg: a.pkg, used: !!a.lastUsedMs,
      eyebrow: a.lastUsedMs ? `On this TV  ·  opened ${ago(a.lastUsedMs)}` : 'App',
      desc: a.minutesWeek ? `On this TV: ${Math.round(a.minutesWeek / 60 * 10) / 10} hours this week${a.streak >= 2 ? `, ${a.streak} days running` : ''}.` : 'Not opened on this TV this week.' }));
    const ch = channelItems(current());
    const sport = myLive().map(c => ({
      kind: 'live', id: 'live:' + c.id, name: c.title, color: c.state === 'LIVE' ? '#5a1414' : c.state === 'NEXT' ? '#1a3550' : '#1a2340', pkg: c.handoffPkg, live: c.state === 'LIVE', state: c.state, tag: c.sport.toUpperCase(), data: c,
      eyebrow: `${c.sport.toUpperCase()}  ·  ${c.state === 'NONE' ? 'quiet' : c.state}`, desc: `${c.line1}${c.line2 ? '. ' + c.line2 : ''}. ${c.handoffPkg ? 'Select opens ' + c.handoffLabel + '.' : c.handoffLabel}` }));
    const today = todayHidden ? [] : state.feeds.today.map(t => ({
      kind: 'today', id: 'today:' + t.id, name: t.label, color: '#162038', tag: `${t.headlines.length} headlines`, data: t,
      eyebrow: `TODAY  ·  ${t.headlines.length} headlines`, desc: t.headlines.length ? t.headlines[0].title : 'Nothing new in the last 24 hours.' }));
    const sys = [
      { kind: 'system', id: 'sys:audio', action: 'audio', name: 'Audio boost', color: state.system.audio ? '#0b4f3f' : '#222a3a', tag: state.system.audio ? 'ON' : 'OFF', eyebrow: 'SYSTEM  ·  sound',
        desc: state.system.audio ? 'Loudness, presence and bass enhancement is on for every app. Select turns it off.' : 'System-wide sound enhancement is off. Select turns it on.' },
      { kind: 'system', id: 'sys:dream', action: 'dream', name: 'Screensaver', color: '#222a3a', tag: 'ON IDLE', eyebrow: 'SYSTEM  ·  idle', desc: 'The orbit world with the clock, the sky and the live score. It starts by itself after five minutes without a key press.' },
      { kind: 'system', id: 'sys:amazon', action: 'amazon', name: 'Fire TV home', color: '#222a3a', tag: 'ESCAPE', eyebrow: 'SYSTEM  ·  escape hatch', desc: 'Opens the Amazon home screen. Orbit comes back on the next wake.' },
    ];
    const m = mood();
    const tonight = state.feeds.tonight.map(t => ({
      kind: 'show', id: 'show:' + t.kind + ':' + t.title, name: t.title, art: t.image || null, poster: true, color: m.accent, pkg: t.pkg, rank: t.rank, tag: t.kind === 'film' ? 'FILM' : 'SERIES',
      eyebrow: `TOP 10 ON NETFLIX  ·  NO ${t.rank} ${t.kind === 'film' ? 'FILM' : 'SERIES'} ${state.livingName ? 'IN ' + state.livingName.toUpperCase() : 'WHERE YOU LIVE'}`, desc: t.blurb || `In the Netflix Top 10 for ${state.livingName || 'where you live'} this week. Select opens Netflix.` }));
    if (tonight.length || ch.length) tonight.push({ kind: 'surprise', id: 'surprise', name: 'Surprise me', color: m.accent, poster: true, tag: 'DICE', eyebrow: 'FEELING LUCKY', desc: 'Select and Orbit picks something for you: a live channel, a Top 10 title or an app you have been ignoring.' });
    if (m.id === 'learn') ch.sort((a, b) => ((/news/i.test(b.tag || '') ? 1 : 0) - (/news/i.test(a.tag || '') ? 1 : 0)) || (b.live ? 1 : 0) - (a.live ? 1 : 0));   // news channels first
    if (m.id === 'chill') cont.sort((a, b) => (/spotify|music/i.test(b.name) ? 1 : 0) - (/spotify|music/i.test(a.name) ? 1 : 0));
    const anyLive = sport.some(x => x.live);
    const rows = { tonight, cont, ch, sport: (m.quiet && !anyLive) ? [] : sport, today: m.quiet ? [] : today, sys };
    return Object.assign(rows, { todayHidden, order: m.order, mood: m });
  }

  // ---------- Journeys: ask the native travel service; answers come back through orbit.journeyResult ----------
  // Only pages that load journeys.js (Atlas, Journey) use this. The page token arrives when the page finishes loading; requests made earlier wait for it (up to 5 s).
  let book = null;
  function journey(req) { return new Promise((res) => { book = book || Journeys.requestBook(Math.random().toString(36).slice(2, 10)); const id = book.add(req.op, res); let tries = 0;
    const fail = (error) => { const r = book.drop(id); if (r) r({ id, op: req.op, ok: false, error }); };
    const send = () => { if (!window.__orbitToken && tries++ < 50) { setTimeout(send, 100); return; } try { Android.journey(JSON.stringify(Object.assign({}, req, { id })), window.__orbitToken || ''); } catch (e) { fail('bridge'); } }; send(); setTimeout(() => fail('timeout'), 25000); }); }

  // ---------- Home Mode ----------
  function openHome(id) { try { localStorage.setItem('orbit.home', id); } catch (e) {} try { Android.page('home', window.__orbitToken || ''); } catch (e) { log('open home ' + id); } }

  // ---------- launching ----------
  let rolling = false;
  const setVeil = (on) => { const v = $('veil'); if (v) v.style.opacity = on ? .85 : 0; };
  let toastTimer = 0; function toast(text) { const t = $('toast'); if (!t) return; t.textContent = text; t.classList.add('on'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('on'), 2600); }
  function launchItem(it) {
    if (!it) return;
    if (it.kind === 'surprise') {
      if (rolling) return;
      const all = items(); const pool = [...all.tonight.filter(x => x.kind === 'show'), ...all.ch.filter(x => x.live), ...all.cont.filter(x => !x.used)];
      const pick = pool[Math.floor(Math.random() * pool.length)]; if (!pick) { toast('Nothing to pick from yet'); return; }
      rolling = true; whoosh(); emit('surprise', pick); toast('Surprise: ' + pick.name); setTimeout(() => { rolling = false; launchItem(pick); }, 1400); return;
    }
    if (it.kind === 'today') { openReader(it.data); return; }
    if (it.kind === 'system') { try { Android.system(it.action + (it.arg ? ':' + it.arg : ''), window.__orbitToken || ''); } catch (e) { log('system ' + it.action); } return; }
    if (it.kind === 'live' && !it.pkg) { toast(it.data.handoffLabel || 'Not available on this TV'); return; }
    if (it.kind === 'channel' && it.missing) { toast(it.name + ' is not installed'); return; }
    whoosh(); setVeil(true);
    setTimeout(() => { try { if (it.url) Android.open(it.url, it.pkg, window.__orbitToken || ''); else Android.launch(it.pkg, window.__orbitToken || ''); } catch (e) { log('launch ' + (it.url || it.pkg)); setVeil(false); } }, 220);
  }

  // ---------- reader ----------
  let reader = null;
  function ensureReader() {
    if ($('reader')) return;
    const root = $('root'), r = document.createElement('div'); r.id = 'reader';
    r.innerHTML = '<div class="tag"></div><div class="head"></div><div class="meta"></div><div class="hint">Up · Down  next story  ·  Back  close</div>';
    root.appendChild(r);
    const t = document.createElement('div'); t.id = 'toast'; root.appendChild(t);
  }
  function renderReader() {
    const h = reader.card.headlines[reader.i], el = $('reader');
    el.querySelector('.tag').textContent = `${reader.card.label}  ·  ${reader.i + 1} / ${reader.card.headlines.length}`;
    el.querySelector('.head').textContent = h.title;
    const meta = el.querySelector('.meta'), src = document.createElement('bdi'); src.textContent = h.source || '';   // a right-to-left source cannot reorder the time
    meta.textContent = ''; meta.append(src, h.publishedMs ? '  ·  ' + ago(h.publishedMs) : '');
  }
  function openReader(card) { if (!card.headlines.length) { toast('Nothing new on ' + card.label); return; } reader = { card, i: 0 }; renderReader(); $('reader').classList.add('on'); }
  function closeReader() { reader = null; const r = $('reader'); if (r) r.classList.remove('on'); }
  const readerOpen = () => !!reader;

  // ---------- native API ----------
  window.orbit = {
    setApps(list) { state.apps = list; refreshWell(); newHostLine(); setVeil(false); emit('data'); log(`page: ${list.length} apps`); },
    setFeeds(f) {
      const g = f || {};
      state.feeds = { live: g.live || [], today: g.today || [], channels: g.channels || [], tonight: g.tonight || [], homes: g.homes || {}, loves: g.loves || {}, sportsBy: g.sportsBy || [] };
      if (g.system) state.system = Object.assign(state.system, g.system);
      if (g.country) state.system.country = g.country;
      emit('data');
    },
    setCatalog(c) { state.catalog = c || {}; emit('data'); },
    setProfiles(list) {
      const was = JSON.stringify(state.profiles);
      state.profiles = Array.isArray(list) ? list : []; homeChips(); if (savedWait) savedWait(state.profiles); emit('profiles');
      if (JSON.stringify(state.profiles) !== was) emit('data'); },   // the same list again (every page load, every save) redraws nothing
    setSystem(s) { state.system = Object.assign(state.system, s || {}); emit('data'); },
    wake() { refreshWell(); newHostLine(); closeReader(); emit('wake'); emit('data'); setVeil(false); },
    returned(pkg) { setVeil(false); emit('returned', pkg); emit('data'); },
    radioState(s) { emit('radio', s); },
    paused() { emit('pause'); },
    journeyResult(r) { if (book) book.settle(r); },
    resumed() { emit('resume'); },
    launchFailed(pkg) { emit('launchFailed', pkg); setVeil(false); const c = state.feeds.live.find(x => x.handoffPkg === pkg); toast(c ? `${c.handoffLabel} is not installed` : 'That app is not available here'); },
    back() { if (reader) closeReader(); else emit('back'); },
  };
  window.addEventListener('keydown', (e) => {
    const k = e.key; if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter'].includes(k)) return; e.preventDefault();
    armed = true; if (rolling) return; if (k !== 'Enter') tick();
    if (reader) { if (k === 'ArrowDown' || k === 'ArrowRight' || k === 'Enter') reader.i = (reader.i + 1) % reader.card.headlines.length; else reader.i = (reader.i - 1 + reader.card.headlines.length) % reader.card.headlines.length; renderReader(); return; }
    emit('key', k, !!e.repeat);   // repeat: the key is being held (setup's keyboard opens accents on a held Select)
  });
  setInterval(() => { refreshWell(); emit('tick'); }, 15000);
  // Country names (code -> name) for titles that name a person's home country. They only show with a person: with none yet, setProfiles redraws later.
  try { fetch('data/countries.json').then(r => r.json()).then(cs => { cs.forEach(c => { state.countryNames[c.code] = c.name; }); if (profiles().length) emit('data'); }).catch(() => {}); } catch (e) {}
  // ready needs the page token, which the shell injects when the page finishes loading (after every image on a slow TV), so the
  // page waits for it however long that takes. Without a native shell there is no token: after 5 s the page draws by itself.
  function ready() {
    let tries = 0;
    const send = () => {
      if (!window.__orbitToken) {
        if (++tries === 50 && typeof Android === 'undefined') { setVeil(false); emit('data'); }
        setTimeout(send, 100); return;
      }
      try { Android.ready(window.__orbitToken); } catch (e) { setVeil(false); emit('data'); }
    };
    send();
  }
  document.addEventListener('DOMContentLoaded', () => { ensureReader(); applyMood(); ready(); });
  const esc = (t) => String(t == null ? '' : t).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  // ---------- profiles (stored on the TV; see profiles.js) ----------
  const profiles = () => state.profiles || [];
  // The current person's share of the household feeds (profiles.js): their home country's channels, their loved sports' live cards.
  const myLive = () => window.Profiles && Profiles.liveFor ? Profiles.liveFor(current(), state.feeds.live, state.feeds.sportsBy) : [];
  function current() { let last = null; try { last = localStorage.getItem('orbit.profile'); } catch (e) {} return window.Profiles ? Profiles.current(profiles(), last) : (profiles()[0] || null); }
  // Saving waits for the page token (as journey does) and resolves true only when the TV pushes the saved list back through
  // orbit.setProfiles; a refused or lost save resolves false after 8 s, so the page can say so and offer to try again.
  let savedWait = null;
  const profileKey = (l) => (l || []).map(p => p ? [p.id, p.name, (p.languages || []).join('.'), (p.loves || []).length].join(':') : '').join('|');
  // One save at a time: a save given a function builds its list from the profiles the TV pushed back last, so a queued save
  // (learned weights) cannot drop what an earlier one added (an accepted love).
  let saving = Promise.resolve();
  function saveProfiles(next) {
    const run = saving.then(() => saveOne(typeof next === 'function' ? next(profiles()) : next));
    saving = run.catch(() => false);
    return run;
  }
  function saveOne(list) {
    return new Promise((res) => {
      let tries = 0, done = false; const want = profileKey(list);
      const finish = (ok) => { if (done) return; done = true; savedWait = null; res(ok); };
      savedWait = (got) => { if (profileKey(got) === want) finish(true); };
      const send = () => { if (done) return; if (!window.__orbitToken && tries++ < 50) { setTimeout(send, 100); return; } try { Android.saveProfiles(JSON.stringify(list), window.__orbitToken || ''); } catch (e) { log('saveProfiles ' + list.length); finish(false); } };
      send(); setTimeout(() => finish(false), 8000);
    });
  }
  return { state, on, items, profiles, saveProfiles, current, myLive, channelItems, launchItem, esc, moods: MOODS, mood, setMood, openHome, journey, openReader, closeReader, readerOpen, ago, hhmm, toast, setVeil, log, newHostLine };
})();
