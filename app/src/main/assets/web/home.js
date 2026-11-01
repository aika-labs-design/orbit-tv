// Home Mode context: home time, greeting, festivals, soundscape and host lines for one profile's home. Pure functions, no DOM; tested with node.
(function (root) {
  const pad = (n) => String(n).padStart(2, '0');

  // The time at home, from the home place's time zone (daylight saving included).
  function localNow(tz, nowMs) {
    const p = {}; new Intl.DateTimeFormat('en-GB', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .formatToParts(new Date(nowMs)).forEach(x => { p[x.type] = x.value; });
    return { dateKey: `${p.year}-${p.month}-${p.day}`, hour: +p.hour, hhmm: `${p.hour}:${p.minute}` };
  }

  function bucket(h) { return h >= 5 && h <= 7 ? 'dawn' : h >= 8 && h <= 11 ? 'morning' : h >= 12 && h <= 16 ? 'day' : h >= 17 && h <= 19 ? 'evening' : 'night'; }
  // In the main language where a greeting is verified (greeting(lang, hour) gives null otherwise), else English.
  function greet(profile, hour, greeting) {
    if (!profile) return '';
    const english = hour >= 5 && hour < 12 ? 'Good morning' : hour >= 12 && hour < 17 ? 'Good afternoon' : 'Good evening';
    return (greeting && greeting((profile.languages || [])[0], hour)) || english;
  }

  // The profile whose home is open: the one asked for (Shell.openHome), else the person watching. An unknown id matches nobody,
  // and with neither there is no home to show (the page says it is getting ready), never someone else's.
  function pick(profiles, wantedId, currentId) {
    const ps = profiles || [];
    return ps.find(p => p.id === wantedId) || ps.find(p => p.id === currentId) || null;
  }
  // The next person's home for "Switch to": only a person who exists and has a home place; null when there is nobody else.
  function next(profiles, id) {
    const ps = (profiles || []).filter(p => p && (p.id === id || (p.home && (p.home.places || []).length))), i = ps.findIndex(p => p.id === id);
    return ps.length > 1 ? ps[(i + 1) % ps.length] : null;
  }
  // Ready once the profile has a home place and the feed carries this profile's home; until then the page says it is getting ready.
  function ready(profile, feedHome) {
    return !!(profile && profile.home && (profile.home.places || []).length && feedHome && feedHome.pack && feedHome.pack.id === profile.id);
  }
  // Wikipedia's summary image at a standard Wikimedia width that fills the 1920 root (scaled by half on the TV).
  const photoUrl = (u) => !u ? null : u.replace(/\/\d+px-/, '/960px-');

  const dayNum = (key) => Date.UTC(+key.slice(0, 4), +key.slice(5, 7) - 1, +key.slice(8, 10)) / 86400000;
  function nextFestival(festivals, dateKey) {
    const today = dayNum(dateKey);
    for (const f of festivals || []) {
      const days = dayNum(f.date) - today;
      if (days >= 0) return days <= 30 ? { name: f.name, local: f.local, days } : null;
    }
    return null;
  }

  // The bundled rain, birdsong and night recordings suit any home. Touches that belong to one country (a recording, a dawn or evening line)
  // come from homes/local.json, keyed by country code; the page loads it with loadLocal.
  let LOCAL = {};
  const loadLocal = (data) => { LOCAL = data || {}; };
  const local = (country) => LOCAL[country] || {};
  function sounds(country) { const s = local(country).sound; return ['rain', ...(s ? [s] : []), 'birds', 'night']; }
  function autoSound(b, wet, country) { return wet ? 'rain' : (b === 'dawn' || b === 'evening') ? (local(country).sound || 'birds') : b === 'night' ? 'night' : 'birds'; }
  // What the page should play right now: nothing while Orbit is paused, the radio is on, or sound is off.
  function soundFor(s) { if (s.paused || s.radioOn || s.mode === 'off') return ''; return s.mode === 'auto' ? autoSound(s.bucket, s.wet, s.country) : s.mode; }
  // Weather older than maxMs is not "right now"; drop it so the page falls back to its no-weather lines.
  function freshWeather(weather, weatherMs, nowMs, maxMs) { return weather && weatherMs && nowMs - weatherMs <= (maxMs || 3 * 3600 * 1000) ? weather : []; }

  const WET = /rain|drizzle|showers|thunder/;
  const TIME = {
    dawn: (t, p, c) => `${t} in ${p}. ${local(c).dawn || 'The day is only just starting.'}`,
    morning: (t, p) => `${t} in ${p}. The city is on its way to work.`,
    day: (t, p) => `${t} in ${p}. Lunch is done and the afternoon is slow.`,
    evening: (t, p, c) => `${t} in ${p}. ${local(c).evening || 'The evening is settling in.'}`,
    night: (t, p) => `${t} in ${p}. The house back home is quiet now.`,
  };

  // Every line is a full sentence built only from data that is present. ctx.here: the city the person lives in, with its weather.
  function lines(ctx) {
    const out = []; const place = ctx.place || ctx.label || 'home';
    const f = ctx.festival;
    if (f) out.push(f.days === 0 ? `Happy ${f.name}. Home is celebrating today.` : f.days === 1 ? `${f.name} tomorrow. Home is getting ready.` : `${f.name} in ${f.days} days.`);
    const w = (ctx.weather || [])[0], here = ctx.here;
    if (w) {
      if (w.wet && here && here.name && WET.test(here.desc || '')) out.push(`Rain in ${here.name}, and it's raining in ${w.name} too.`);
      else if (w.wet) out.push(`Rain in ${w.name} right now, ${w.tempC} degrees.`);
      else out.push(`${w.tempC} degrees and ${w.desc} in ${w.name}.`);
      if (here && here.name && typeof here.tempC === 'number' && here.tempC !== w.tempC) out.push(`${w.tempC} degrees in ${w.name}, ${here.tempC} in ${here.name}.`);
    }
    const w2 = (ctx.weather || [])[1];
    if (w2) out.push(`${w2.name}: ${w2.tempC} degrees, ${w2.desc}.`);
    if (ctx.liveChannel) out.push(`${ctx.liveChannel} is live from home right now.`);
    if (ctx.hhmm && TIME[ctx.bucket]) out.push(TIME[ctx.bucket](ctx.hhmm, place, ctx.country));
    if (!out.length) out.push(`Welcome home to ${ctx.label || 'home'}.`);
    return out;
  }

  // Radio Browser names run long ("88.9 Noticias - 88.9 FM - XHM-FM - Grupo ACIR - ..."): the card takes the first part.
  const stationName = (n) => String(n || '').split(' - ')[0].trim();
  const Home = { stationName, loadLocal, localNow, bucket, greet, pick, next, ready, photoUrl, nextFestival, sounds, autoSound, soundFor, freshWeather, lines };
  if (typeof module !== 'undefined' && module.exports) module.exports = Home; else root.Home = Home;
})(this);
