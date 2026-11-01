// Profiles on the page: pick the current one and validate. Pure; tested with node.
(function (root) {
  function current(list, lastId) {
    if (!Array.isArray(list) || !list.length) return null;
    return list.find(p => p && p.id === lastId) || list[0];
  }
  const str = (v) => typeof v === 'string' && v.trim() !== '';
  const num = (v) => typeof v === 'number' && isFinite(v);
  function validate(p) {
    const e = [];
    if (!p || typeof p !== 'object') return ['profile: missing'];
    if (!str(p.id)) e.push('id: required');
    if (!str(p.name)) e.push('name: required');
    const h = p.home || {};
    if (!str(h.country)) e.push('home.country: required');
    if (!Array.isArray(h.places) || h.places.length < 1 || h.places.length > 3) e.push('home.places: 1 to 3 places');
    else if (!h.places.every(x => x && str(x.name) && num(x.lat) && num(x.lon) && str(x.tz))) e.push('home.places: each needs name, lat, lon, tz');
    // ISO 639-1, or 639-3 where a language has none (Nigerian Pidgin, pcm)
    if (!Array.isArray(p.languages) || !p.languages.length || !p.languages.every(c => typeof c === 'string' && /^[a-z]{2,3}$/.test(c))) e.push('languages: at least one code');
    const l = p.living;
    if (!l || !str(l.name) || !num(l.lat) || !num(l.lon) || !str(l.tz) || !str(l.country)) e.push('living: needs name, lat, lon, tz, country');
    if (!Array.isArray(p.loves) || !p.loves.every(v => v && str(v.id) && Array.isArray(v.refine) && (v.since == null || num(v.since)))) e.push('loves: each needs id and refine list');
    return e;
  }

  // What one person sees of the household's feeds: the channels of their home country (curated or searched) and the live cards of
  // the sports they love (sportsBy: [{ id, sports }] from the feeds). Nobody, or nothing of theirs: nothing, never another person's.
  function channelsFor(person, channels) {
    const cc = person && person.home && person.home.country;
    return cc ? (channels || []).filter(c => c && (c.countries || []).includes(cc)) : [];
  }
  function liveFor(person, live, sportsBy) {
    const mine = person && (sportsBy || []).find(s => s && s.id === person.id), sports = new Set((mine && mine.sports) || []);
    return (live || []).filter(c => c && sports.has(c.sport));
  }

  // The screensaver knows only the id the pages saved: that exact person's sports, none for a missing or unknown id (never the first person's).
  const liveOf = (id, live, sportsBy) => id ? liveFor({ id }, live, sportsBy) : [];

  const Profiles = { current, validate, channelsFor, liveFor, liveOf };
  if (typeof module !== 'undefined' && module.exports) module.exports = Profiles; else root.Profiles = Profiles;
})(this);
