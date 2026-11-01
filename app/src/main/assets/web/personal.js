// Personal layer: insights from usage facts, data-driven host lines, and the Tonight pick.
window.Personal = (function () {
  const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const fmtHour = (h) => `${String(h).padStart(2, '0')}:00`;
  const ago = (ms) => { const m = Math.round((Date.now() - ms) / 60000); if (m < 60) return `${m} min ago`; const h = Math.round(m / 60); if (h < 36) return `${h} h ago`; return `${Math.round(h / 24)} days ago`; };

  function insights(apps, now, sky) {
    now = now || new Date();
    const dow = now.getDay(), hour = now.getHours();
    const withUse = apps.filter(a => (a.hours || []).some(x => x > 0));
    const totalMin = apps.reduce((s, a) => s + (a.minutesWeek || 0), 0);
    const hourTotals = new Array(24).fill(0); withUse.forEach(a => a.hours.forEach((n, h) => hourTotals[h] += n));
    const primeHour = hourTotals.some(x => x) ? hourTotals.indexOf(Math.max(...hourTotals)) : null;
    const byDay = withUse.map(a => ({ a, n: (a.days || [])[dow] || 0 })).filter(x => x.n > 0).sort((x, y) => y.n - x.n)[0];
    const byHour = withUse.map(a => ({ a, n: a.hours.slice(Math.max(0, hour - 1), hour + 2).reduce((s, x) => s + x, 0) })).filter(x => x.n > 0).sort((x, y) => y.n - x.n)[0];
    const streak = apps.filter(a => (a.streak || 0) >= 2).sort((x, y) => y.streak - x.streak)[0];
    const last = apps.filter(a => a.lastUsedMs > 0).sort((x, y) => y.lastUsedMs - x.lastUsedMs)[0];
    const top = apps.slice().sort((x, y) => (y.minutesWeek || 0) - (x.minutesWeek || 0))[0];
    const w = sky ? { weather: Sky.weatherWord(sky), weatherDesc: sky.desc, tempC: sky.tempC, city: sky.city, moon: sky.moon, isDay: sky.isDay, sunset: Sky.hhmm(sky.sunset), sunrise: Sky.hhmm(sky.sunrise) } : { weather: null };
    return { ...w, dow, dayName: DAYS[dow], hour, totalMin, hoursWeek: Math.round(totalMin / 60), primeHour, weekdayFav: byDay && byDay.n >= 2 ? byDay.a : null, hourFav: byHour && byHour.n >= 2 ? byHour.a : null, streak: streak || null, last: last || null, top: top && top.minutesWeek > 0 ? top : null };
  }

  // Tonight: one pick with a reason, in priority order of how personal the evidence is.
  function tonight(apps, ins) {
    if (ins.streak) return { app: ins.streak, reason: `${ins.streak.streak} nights in a row on this TV. Or try something new tonight.`, tag: 'STREAK' };
    if (ins.weekdayFav) return { app: ins.weekdayFav, reason: `This TV's ${ins.dayName} pick, ${ins.weekdayFav.days[ins.dow]} ${ins.dayName}s running.`, tag: ins.dayName.toUpperCase() };
    if (ins.hourFav) return { app: ins.hourFav, reason: `Usually on this TV around ${fmtHour(ins.hour)}.`, tag: 'THIS HOUR' };
    if (ins.top) return { app: ins.top, reason: `${Math.round(ins.top.minutesWeek / 60)} hours on this TV this week.`, tag: 'MOST WATCHED' };
    if (apps.length) return { app: apps[Math.floor(Math.random() * apps.length)], reason: 'No history yet, so I picked for you.', tag: 'WILDCARD' };
    return null;
  }

  // Host lines with data. Each persona has templated lines; a line is eligible only when its data exists.
  const T = {
    butler: [
      ['weather', (i) => i.weather === 'rain' ? `Rain outside, ${i.city || 'sir'}. Long film weather, I'd say.` : i.weather === 'clear' ? `A clear night over ${i.city || 'the house'}. The ${i.moon} is out, if one is interested.` : `${i.tempC} degrees and ${i.weatherDesc}. The couch, I feel, is the correct call.`],
      ['last', (i) => `Welcome back. ${i.last.label} was on ${ago(i.last.lastUsedMs)}. I kept it warm.`],
      ['streak', (i) => `${i.streak.label} again, I presume? That would be night ${i.streak.streak + 1}.`],
      ['hoursWeek', (i) => `${i.hoursWeek} hours on this television this week. I shall say nothing further.`],
      [null, () => 'Your usual seat is exactly where you left it.'],
    ],
    trailer: [
      ['weather', (i) => i.weather === 'rain' ? `OUTSIDE: ${i.weatherDesc.toUpperCase()}. INSIDE: EVERYTHING.` : `${i.city ? i.city.toUpperCase() : 'TONIGHT'}. ${i.tempC} DEGREES. ONE COUCH.`],
      ['streak', (i) => `NIGHT ${i.streak.streak + 1}. THE ${i.streak.label.toUpperCase()} SAGA CONTINUES.`],
      ['dayName', (i) => `THIS ${i.dayName.toUpperCase()}. ONE COUCH. NO REGRETS.`],
      ['primeHour', (i) => `COMING UP AT ${fmtHour(i.primeHour)}: THIS TV'S PRIME TIME.`],
      [null, () => 'IN A WORLD... where one person holds the remote.'],
    ],
    reluctant: [
      ['weather', (i) => i.weather === 'rain' ? `It's raining. You weren't going anywhere anyway.` : `${i.tempC} degrees out there. Not that you'd know.`],
      ['last', (i) => `Oh. Hello. It was ${i.last.label} last time on this TV. I remember everything.`],
      ['hoursWeek', (i) => `${i.hoursWeek} hours on this TV this week and nothing finished.`],
      ['top', (i) => `Let me guess. ${i.top.label}. Again. Fine.`],
      [null, () => 'Oh. You again. Fine, I\'m awake.'],
    ],
    mission: [
      ['weather', (i) => `Exterior ${i.tempC} degrees, ${i.weatherDesc}. ${i.isDay ? 'Sunset at ' + i.sunset : 'Sunrise at ' + i.sunrise}. Cabin conditions nominal.`],
      ['primeHour', (i) => `Prime time detected at ${fmtHour(i.primeHour)}. Orbit stable.`],
      ['weekdayFav', (i) => `${i.dayName} protocol: ${i.weekdayFav.label}. Awaiting your command.`],
      ['hoursWeek', (i) => `Weekly log: ${i.hoursWeek} hours on screen. Snacks not detected.`],
      [null, () => 'All systems nominal. Snacks not detected.'],
    ],
    fortune: [
      ['weather', (i) => `A ${i.moon} tonight${i.weather === 'rain' ? ', and rain' : ''}. The omens favour staying in.`],
      ['streak', (i) => `The cards show ${i.streak.label}. They have shown it ${i.streak.streak} nights now.`],
      ['hourFav', (i) => `At this hour the stars favour ${i.hourFav.label}.`],
      ['last', (i) => `I sense unfinished business in ${i.last.label}.`],
      [null, () => 'I see... a cliffhanger in your future.'],
    ],
  };
  const NAMES = { butler: 'The Butler', trailer: 'Trailer Voice', reluctant: 'Reluctant AI', mission: 'Mission Control', fortune: 'Fortune Teller' };

  function hostLine(personaId, ins) {
    const eligible = T[personaId].filter(([key]) => !key || (ins[key] !== null && ins[key] !== undefined && ins[key] !== 0));
    const dataLines = eligible.filter(([key]) => key);
    const pool = dataLines.length ? dataLines : eligible;
    return pool[Math.floor(Math.random() * pool.length)][1](ins);
  }

  return { insights, tonight, hostLine, personas: Object.keys(T), personaName: (id) => NAMES[id] };
})();
