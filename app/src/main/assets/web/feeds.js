// Feeds: card artwork for the Live and Today rails, ages, and host lines about what is on. Data arrives from the native shell via orbit.setFeeds.
window.Feeds = (function () {
  const LIVE_RED = '#ff3b3b';
  function age(ms, now) { const m = Math.max(0, Math.round(((now || Date.now()) - ms) / 60000)); if (m < 1) return 'JUST NOW'; if (m < 60) return `${m} MIN AGO`; const h = Math.round(m / 60); if (h < 36) return `${h} H AGO`; return `${Math.round(h / 24)} D AGO`; }
  function wrap(g, text, x, y, maxW, lineH, maxLines) {
    const words = String(text || '').split(' '); let line = '', lines = 0;
    for (let i = 0; i < words.length; i++) {
      const t = line + words[i] + ' ';
      if (g.measureText(t).width > maxW && line) { if (++lines === maxLines) { g.fillText(line.trim() + '…', x, y); return y + lineH; } g.fillText(line.trim(), x, y); line = words[i] + ' '; y += lineH; } else line = t;
    }
    if (line.trim()) { g.fillText(line.trim(), x, y); y += lineH; }
    return y;
  }
  function pill(g, text, x, y, color, dark) {
    g.font = "700 20px 'Space Mono', monospace"; const w = g.measureText(text).width + 28;
    g.fillStyle = color; g.beginPath(); g.roundRect ? g.roundRect(x, y - 24, w, 34, 17) : g.rect(x, y - 24, w, 34); g.fill();
    g.fillStyle = dark ? '#0a0f18' : '#fff'; g.fillText(text, x + 14, y);
    return w;
  }
  const SPORT = { cricket: 'CRICKET', f1: 'FORMULA 1', motogp: 'MOTOGP', tennis: 'TENNIS' };
  function stateColor(card, mood) { return card.state === 'LIVE' ? LIVE_RED : card.state === 'NEXT' ? mood.accent : 'rgba(255,255,255,.6)'; }
  function brandColor(card, mood) { return card.state === 'LIVE' ? LIVE_RED : card.state === 'NEXT' ? mood.accent : '#8a93a6'; }
  function isStale(card, now) { return card.state === 'LIVE' && (now - card.fetchedMs) > 10 * 60000; }
  function stateText(card, now) { return isStale(card, now) ? 'LAST KNOWN' : card.state === 'NONE' ? 'QUIET' : card.state; }
  /** Changes whenever the card would need redrawing for time alone: the age text or the stale flag. */
  function ageKey(card, now) { return age(card.fetchedMs, now) + (isStale(card, now) ? '!' : ''); }

  function liveArt(card, mood, now) {
    now = now || Date.now();
    const c = document.createElement('canvas'); c.width = 768; c.height = 432; const g = c.getContext('2d');
    g.fillStyle = '#0b1020'; g.fillRect(0, 0, 768, 432);
    const grad = g.createLinearGradient(0, 0, 768, 432); grad.addColorStop(0, 'rgba(255,255,255,.06)'); grad.addColorStop(1, 'rgba(0,0,0,.25)'); g.fillStyle = grad; g.fillRect(0, 0, 768, 432);
    g.fillStyle = 'rgba(255,255,255,.55)'; g.font = "700 20px 'Space Mono', monospace"; g.fillText(SPORT[card.sport] || card.sport.toUpperCase(), 40, 64);
    const sw = g.measureText(SPORT[card.sport] || card.sport.toUpperCase()).width;
    pill(g, stateText(card, now), 40 + sw + 24, 64, stateColor(card, mood), card.state !== 'LIVE');
    g.fillStyle = '#fff'; g.font = "700 50px 'Unbounded', sans-serif"; let y = wrap(g, card.title, 40, 150, 690, 58, 2);
    g.fillStyle = 'rgba(255,255,255,.92)'; g.font = "500 32px 'Space Grotesk', sans-serif"; y = wrap(g, card.line1, 40, y + 22, 690, 40, 2);
    g.fillStyle = 'rgba(255,255,255,.62)'; g.font = "400 24px 'Space Grotesk', sans-serif"; wrap(g, card.line2, 40, y + 8, 690, 30, 2);
    g.fillStyle = card.state === 'LIVE' ? LIVE_RED : mood.accent; g.font = "400 18px 'Space Mono', monospace"; g.fillText(age(card.fetchedMs, now), 40, 396);
    g.fillStyle = 'rgba(255,255,255,.5)'; g.textAlign = 'right'; g.fillText((card.handoffPkg ? 'SELECT: ' : '') + (card.handoffLabel || '').toUpperCase(), 728, 396); g.textAlign = 'left';
    return c.toDataURL('image/png');
  }

  function topicArt(card, mood) {
    const c = document.createElement('canvas'); c.width = 768; c.height = 432; const g = c.getContext('2d');
    g.fillStyle = '#0b1020'; g.fillRect(0, 0, 768, 432);
    g.fillStyle = mood.accent; g.fillRect(0, 0, 768, 6);
    g.fillStyle = 'rgba(255,255,255,.55)'; g.font = "700 20px 'Space Mono', monospace"; g.fillText('TODAY', 40, 64);
    g.fillStyle = '#fff'; g.font = "700 48px 'Unbounded', sans-serif"; g.fillText(card.label, 40, 130);
    g.fillStyle = mood.accent; g.font = "400 18px 'Space Mono', monospace"; g.fillText(card.headlines.length ? `${card.headlines.length} HEADLINES  ·  LAST 24 H` : 'NOTHING NEW TODAY', 40, 170);
    if (card.headlines.length) { g.fillStyle = 'rgba(255,255,255,.9)'; g.font = "500 30px 'Space Grotesk', sans-serif"; const y = wrap(g, card.headlines[0].title, 40, 232, 690, 38, 3); g.fillStyle = 'rgba(255,255,255,.5)'; g.font = "400 18px 'Space Mono', monospace"; g.fillText((card.headlines[0].source || '').toUpperCase(), 40, Math.min(396, y + 10)); }
    g.fillStyle = 'rgba(255,255,255,.5)'; g.font = "400 18px 'Space Mono', monospace"; g.textAlign = 'right'; g.fillText('SELECT TO READ', 728, 396); g.textAlign = 'left';
    return c.toDataURL('image/png');
  }

  // Host lines about live sport, per persona; only when something is LIVE or starting soon.
  function hostLine(persona, live) {
    if (!live || !live.length) return null;
    const on = live.find(c => c.state === 'LIVE'), soon = live.find(c => c.state === 'NEXT');
    if (on) {
      const what = `${on.title}: ${on.line1}`;
      return { butler: `${what}, if you must know.`, trailer: `LIVE. NOW. ${what.toUpperCase()}.`, reluctant: `${what}. Yes, it's on. No, I won't do the commentary.`, mission: `Live feed: ${what}. Telemetry nominal.`, fortune: `I see ${on.title} unfolding as we speak. ${on.line1}.` }[persona] || what;
    }
    if (soon) {
      const what = `${soon.title} ${soon.line1.replace(/^Race /, 'race ')}`;
      return { butler: `${what}. I have taken the liberty of noting it.`, trailer: `COMING UP: ${what.toUpperCase()}.`, reluctant: `${what}. Set an alarm, I'm not your alarm.`, mission: `Next window: ${what}.`, fortune: `The stars align for ${what}.` }[persona] || what;
    }
    return null;
  }

  return { liveArt, topicArt, age, ageKey, hostLine, stateColor, brandColor };
})();
