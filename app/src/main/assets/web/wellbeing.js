// Digital wellbeing: honest screen-time facts, a wind-down mode late at night or after heavy days, and gentle nudges.
window.Wellbeing = (function () {
  const WIND_DOWN_HOUR = 23, HEAVY_DAY_MIN = 240, LATE_SESSION_START = 22;
  const fmt = (min) => min >= 60 ? `${Math.round(min / 60 * 10) / 10} h` : `${min} min`;

  function summary(apps, now) {
    now = now || new Date(); const hour = now.getHours();
    const today = apps.reduce((s, a) => s + (a.minutesToday || 0), 0);
    const week = apps.reduce((s, a) => s + (a.minutesWeek || 0), 0);
    const perDay = Math.round(week / 7);
    const windDown = hour >= WIND_DOWN_HOUR || hour < 4 || today >= HEAVY_DAY_MIN;
    const reason = hour >= WIND_DOWN_HOUR || hour < 4 ? 'late' : today >= HEAVY_DAY_MIN ? 'heavy' : null;
    return { today, week, perDay, hour, windDown, reason, late: hour >= LATE_SESSION_START };
  }

  function line(w) { return `ON THIS TV TODAY ${fmt(w.today).toUpperCase()}  ·  WEEK ${fmt(w.week).toUpperCase()}  ·  AVG ${fmt(w.perDay).toUpperCase()} A DAY`; }

  // Host nudges, gentle and specific. Returned only when relevant; the caller mixes them into the persona's pool.
  function nudges(w, persona) {
    const out = [];
    if (w.reason === 'late') out.push({ butler: `It is past ${WIND_DOWN_HOUR}:00. Might I suggest one more, and only one more?`, trailer: `LATE NIGHT. ONE EPISODE. THEN SLEEP. A TRUE STORY.`, reluctant: `It's late. I'm saying that for you, not me. I don't sleep.`, mission: `Local time past ${WIND_DOWN_HOUR}:00. Recommend short mission, then lights out.`, fortune: `The hour is late. I foresee a better morning if this is the last one.` }[persona]);
    // screen time is the whole TV's (Android counts it per device, not per person): said of this TV, never of the viewer
    if (w.reason === 'heavy') out.push({ butler: `${fmt(w.today)} on this TV today. The sofa will keep. Perhaps a short one.`, trailer: `${fmt(w.today).toUpperCase()} ON THIS TV TODAY. THE COUCH FIGHTS BACK.`, reluctant: `${fmt(w.today)} on this TV today. I'd suggest a walk, but I'm a television.`, mission: `Screen time on this TV today ${fmt(w.today)}. Crew rest advised.`, fortune: `${fmt(w.today)} on this TV today. The cards suggest fresh air before the next chapter.` }[persona]);
    if (!w.reason && w.today > 0 && w.today < 60) out.push({ butler: `Only ${fmt(w.today)} on this TV today. A balanced sort of day.`, trailer: `A LIGHT DAY ON THIS TV. ${fmt(w.today).toUpperCase()}. CRITICS ARE STUNNED.`, reluctant: `${fmt(w.today)} on this TV today. Suspiciously restrained.`, mission: `Screen time on this TV today ${fmt(w.today)}. Well within limits.`, fortune: `A light day on this TV, ${fmt(w.today)}. The balance pleases the stars.` }[persona]);
    return out.filter(Boolean);
  }

  // In wind-down, the Tonight pick leans calm: music apps if installed, otherwise a goodnight card.
  function calmPick(apps, w) {
    if (!w.windDown) return null;
    const calm = apps.find(a => /spotify|music|radio|podcast/i.test(a.label));
    if (calm) return { app: calm, reason: w.reason === 'late' ? 'Late. Something quiet before bed?' : `${fmt(w.today)} on this TV today. Wind down with sound instead.`, tag: 'WIND DOWN' };
    return { app: null, reason: w.reason === 'late' ? `It's past ${WIND_DOWN_HOUR}:00. One short one, then lights out.` : `${fmt(w.today)} on this TV today. A short one, or call it a night.`, tag: 'WIND DOWN', label: 'Lights out?' };
  }

  return { summary, line, nudges, calmPick, fmt };
})();
