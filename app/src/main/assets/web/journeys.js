// Journeys: local time, phase of day, suggestions, globe projection and footage rules. Pure; tested with node.
(function (root) {
  const MODES = ['walk', 'drive', 'food', 'festivals', 'nature', 'stays', 'live'];
  const NAMES = { walk: 'Walk', drive: 'Drive', food: 'Food', festivals: 'Festivals', nature: 'Nature', stays: 'Stays', live: 'Live cams' };
  const R = 0.9;      // globe radius as a fraction of half the frame (tools/globe/render.html uses the same)
  const TILT = 20;    // the frames look down from 20 degrees north

  function localTime(tz, nowMs) {
    const parts = {}; new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', weekday: 'long', hourCycle: 'h23' }).formatToParts(new Date(nowMs)).forEach(p => { parts[p.type] = p.value; });
    const h = +parts.hour, m = +parts.minute;
    return { hhmm: `${parts.hour}:${parts.minute}`, weekday: parts.weekday, hour: h + m / 60 };
  }
  function phase(hour, sunrise, sunset) {
    if (hour >= sunrise && hour < sunrise + 1.5) return 'dawn';
    if (hour >= sunset - 1.5 && hour < sunset) return 'golden';
    if (hour >= sunrise && hour < sunset) return 'day';
    return 'night';
  }
  // Places worth visiting this minute: dawn first, then golden hour, then lit evenings (19:00 to 23:00); sleeping places drop out.
  function awakeNow(places, nowMs) {
    const rank = { dawn: 0, golden: 1, night: 2 };
    return places.map(p => { const t = localTime(p.tz, nowMs); const ph = phase(t.hour, p.sunrise || 6.5, p.sunset || 18.5); return { p, ph, ok: ph === 'dawn' || ph === 'golden' || (ph === 'night' && t.hour >= 19 && t.hour < 23) }; })
      .filter(x => x.ok).sort((a, b) => rank[a.ph] - rank[b.ph]).map(x => x.p);
  }
  // Orthographic view of a frame centred on longitude frameDeg, seen from tiltDeg north; x, y as fractions of the frame image.
  function project(lat, lon, frameDeg, tiltDeg) {
    const d = Math.PI / 180, f = lat * d, l = (lon - frameDeg) * d, f0 = (tiltDeg == null ? TILT : tiltDeg) * d;
    const cosc = Math.sin(f0) * Math.sin(f) + Math.cos(f0) * Math.cos(f) * Math.cos(l);
    const x = Math.cos(f) * Math.sin(l), y = Math.cos(f0) * Math.sin(f) - Math.sin(f0) * Math.cos(f) * Math.cos(l);
    return { x: 0.5 + 0.5 * R * x, y: 0.5 - 0.5 * R * y, visible: cosc > 0 };
  }
  const frameFor = (lon) => Math.round((((lon % 360) + 360) % 360) / 5) % 72;
  // Every search names the place with its country when it has one ("Lagos Nigeria"), so a namesake elsewhere is not what comes back.
  const searchName = (p) => `${p.name.split(',')[0]} ${p.country || ''}`.replace(/\s+/g, ' ').trim();
  // Live cams are searched with the country, and a result must name the place and not sit in another well-known place of the same name.
  const liveQuery = (p) => `${p.name.split(',')[0]} ${p.country || ''} live cam`.replace(/\s+/g, ' ').trim();
  const ELSEWHERE = /los angeles|california|florida|lanzarote|canary|airport/i;
  // countries: every country's name (countries.json). Footage that names another country than the place's is of a namesake (Lagos, Portugal).
  const reEsc = (x) => x.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  function isOf(v, p, countries) {
    const t = `${v.title || ''} ${v.channel || ''}`, names = [p.name.split(',')[0].split(' ')[0], ...(p.aliases || [])];
    if (!names.some(n => new RegExp(reEsc(n), 'i').test(t))) return false;   // the name or a well-known other name (Bombay for Mumbai)
    const own = (p.country || '').toLowerCase();
    if ((countries || []).some(c => c && c.toLowerCase() !== own && !own.includes(c.toLowerCase()) && new RegExp(`\\b${reEsc(c)}\\b`, 'i').test(t))) return false;
    return !ELSEWHERE.test(t) || new RegExp(p.country || '^$', 'i').test(t);
  }
  // Where you went: one entry per place, newest first, at most 30; ten minutes or more makes it loved (and it stays loved).
  function remember(history, place, mode, minutes, nowMs) {
    const old = (history || []).find(e => e.id === place.id);
    const entry = { id: place.id, place, mode, at: nowMs, loved: !!(old && old.loved) || minutes >= 10 };
    return [entry, ...(history || []).filter(e => e.id !== place.id)].slice(0, 30);
  }
  // Cards are 250 px wide, so a smaller photo saves a large decode on the TV. Wikimedia serves only its
  // standard thumbnail widths (330, 500, 960...; 400 is refused), YouTube has a 320 px "mq" size.
  const smallImg = (u) => !u ? null : u.replace(/\/\d+px-/, '/500px-').replace(/\/(maxresdefault|hqdefault|sddefault)\.jpg/, '/mqdefault.jpg');
  // Pending travel requests of one page. Ids carry a per-page nonce and replies must match the request's op, because the
  // native workers keep answering an earlier page's requests after the next page has loaded.
  function requestBook(nonce) {
    const pending = {}; let n = 0;
    return {
      add(op, resolve) { const id = nonce + '-' + (++n); pending[id] = { op, resolve }; return id; },
      settle(r) { const p = r && pending[r.id]; if (!p || p.op !== r.op) return false; delete pending[r.id]; p.resolve(r); return true; },
      drop(id) { const p = pending[id]; delete pending[id]; return p ? p.resolve : null; },
    };
  }
  // The profile's home places joined to the curated list, tagged 'home', in the profile's order and first. A home that is a curated place
  // (same name, or within 30 km) is that place, so its history, photo and tags carry over; any other home is added with the country's name.
  // aliasGroups: aliases.json, groups of names for one place; a home found in a group carries the group's other names.
  function withHomes(places, profile, countryName, aliasGroups) {
    const homes = [], used = new Set();
    const km = (a, b) => { const r = Math.PI / 180, x = (b.lon - a.lon) * r * Math.cos((a.lat + b.lat) / 2 * r), y = (b.lat - a.lat) * r; return Math.sqrt(x * x + y * y) * 6371; };
    const first = (n) => String(n || '').split(',')[0].trim().toLowerCase();
    ((profile && profile.home && profile.home.places) || []).forEach((h, i) => {
      const group = (aliasGroups || []).find(g => g.some(n => n.toLowerCase() === first(h.name))) || [];
      const others = group.filter(n => n.toLowerCase() !== first(h.name));
      const c = places.find(p => !used.has(p.id) && ([p.name, ...(p.aliases || [])].some(n => first(n) === first(h.name) || others.some(o => o.toLowerCase() === first(n))) || km(p, h) <= 30));
      const id = c ? c.id : `home-${i}-${first(h.name).replace(/[^a-z0-9]+/g, '-')}`;
      used.add(id);
      homes.push(c ? Object.assign({}, c, { tags: [...c.tags.filter(t => t !== 'home'), 'home'] })
        : Object.assign({ id, name: h.name, country: countryName || '', lat: h.lat, lon: h.lon, tz: h.tz, tags: ['home'] }, others.length ? { aliases: others } : {}));
    });
    return [...homes, ...places.filter(p => !used.has(p.id))];
  }
  // Footage of a mode's own kind first, and never the other kind: no walking tour in Drive, no drive in Walk. Other modes keep their order.
  const DRIVE = /\bdriv(e|ing)\b|\bdashcam\b|\broad ?trip\b|\bscenic drive\b/i, WALK = /\bwalk(ing)?\b/i;
  function forMode(list, mode) {
    if (mode !== 'drive' && mode !== 'walk') return list;
    const mine = mode === 'drive' ? DRIVE : WALK, other = mode === 'drive' ? WALK : DRIVE, t = (v) => v.title || '';
    const kept = (list || []).filter(v => !(other.test(t(v)) && !mine.test(t(v))));
    return kept.filter(v => mine.test(t(v))).concat(kept.filter(v => !mine.test(t(v))));
  }
  // Which pins carry a label: the focused place, and the person's home unless it sits too close to the focused one (their labels overlapped).
  function labelled(pins, focusId, homeId) {
    const out = new Set(), at = (id) => (pins || []).find(p => p.id === id);
    const f = at(focusId), h = at(homeId);
    if (f) out.add(f.id);
    if (h && !(f && f !== h && Math.abs(f.x - h.x) < 280 && Math.abs(f.y - h.y) < 90)) out.add(h.id);
    return out;
  }
  // "Berlin 22:52": the clock of the city the person lives in; nothing without a profile.
  const livingClock = (profile, nowMs) => profile && profile.living ? `${profile.living.name} ${localTime(profile.living.tz, nowMs).hhmm}` : '';
  const Journeys = { MODES, forMode, labelled, searchName, withHomes, livingClock, smallImg, requestBook, liveQuery, isOf, remember, NAMES, R, TILT, localTime, phase, awakeNow, project, frameFor };
  if (typeof module !== 'undefined' && module.exports) module.exports = Journeys; else root.Journeys = Journeys;
})(this);
