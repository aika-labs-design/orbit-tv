// Living sky: coarse location, sun times and weather from free services, cached locally. All optional; offline falls back to the clock.
window.Sky = (function () {
  const KEY = 'orbit.sky.v1', TTL = 30 * 60 * 1000;
  const CODES = { 0: 'clear', 1: 'mostly clear', 2: 'partly cloudy', 3: 'overcast', 45: 'fog', 48: 'fog', 51: 'drizzle', 53: 'drizzle', 55: 'drizzle', 61: 'rain', 63: 'rain', 65: 'heavy rain', 71: 'snow', 73: 'snow', 75: 'snow', 80: 'showers', 81: 'showers', 82: 'heavy showers', 95: 'thunder', 96: 'thunder', 99: 'thunder' };

  async function fetchJson(url, ms) {
    const ctrl = new AbortController(); const t = setTimeout(() => ctrl.abort(), ms || 6000);
    try { const r = await fetch(url, { signal: ctrl.signal }); if (!r.ok) throw new Error(r.status); return await r.json(); } finally { clearTimeout(t); }
  }

  async function locate() {
    try { const j = await fetchJson('https://ipwho.is/'); if (j && j.success !== false && j.latitude) return { lat: j.latitude, lon: j.longitude, city: j.city, tz: j.timezone && j.timezone.id }; } catch (e) {}
    try { const j = await fetchJson('https://ipapi.co/json/'); if (j && j.latitude) return { lat: j.latitude, lon: j.longitude, city: j.city, tz: j.timezone }; } catch (e) {}
    return null;
  }

  async function weather(loc) {
    const u = `https://api.open-meteo.com/v1/forecast?latitude=${loc.lat}&longitude=${loc.lon}&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&timezone=auto&forecast_days=1`;
    const j = await fetchJson(u);
    return { tempC: Math.round(j.current.temperature_2m), code: j.current.weather_code, isDay: j.current.is_day === 1, desc: CODES[j.current.weather_code] || 'cloudy', sunrise: j.daily.sunrise[0], sunset: j.daily.sunset[0] };
  }

  // Moon phase 0..1 (0 = new, 0.5 = full), from a known new moon.
  function moonPhase(d) { const synodic = 29.530588853; const ref = Date.UTC(2000, 0, 6, 18, 14); const days = (d.getTime() - ref) / 86400000; const p = (days / synodic) % 1; return p < 0 ? p + 1 : p; }
  function moonName(p) { const names = ['new moon', 'waxing crescent', 'first quarter', 'waxing gibbous', 'full moon', 'waning gibbous', 'last quarter', 'waning crescent']; return names[Math.round(p * 8) % 8]; }

  async function load() {
    try { const c = JSON.parse(localStorage.getItem(KEY) || 'null'); if (c && Date.now() - c.at < TTL) return c.data; } catch (e) {}
    const loc = await locate(); if (!loc) return null;
    const w = await weather(loc);
    const data = { city: loc.city, ...w, moon: moonName(moonPhase(new Date())) };
    try { localStorage.setItem(KEY, JSON.stringify({ at: Date.now(), data })); } catch (e) {}
    return data;
  }

  const hhmm = (iso) => iso ? iso.slice(11, 16) : '';
  function line(sky) {
    if (!sky) return '';
    const parts = [sky.city ? sky.city.toUpperCase() : null, `${sky.tempC}°`, sky.desc.toUpperCase(), sky.isDay ? `SUNSET ${hhmm(sky.sunset)}` : `SUNRISE ${hhmm(sky.sunrise)}`, sky.moon.toUpperCase()].filter(Boolean);
    return parts.join('  ·  ');
  }
  // Mood from the real sky: day = cyan/mint, dusk/night = violet/amber; rain and fog lean cool, clear nights violet.
  function moodId(sky, hour) {
    if (!sky) return hour >= 5 && hour <= 16 ? (Math.random() < 0.5 ? 'cyan' : 'mint') : (Math.random() < 0.5 ? 'violet' : 'amber');
    const wet = /rain|drizzle|showers|fog|thunder|snow/.test(sky.desc);
    if (sky.isDay) return wet ? 'cyan' : 'mint';
    return wet ? 'cyan' : (sky.desc === 'clear' || sky.desc === 'mostly clear' ? 'violet' : 'amber');
  }
  const weatherWord = (sky) => sky ? (/rain|drizzle|showers/.test(sky.desc) ? 'rain' : /thunder/.test(sky.desc) ? 'thunder' : /snow/.test(sky.desc) ? 'snow' : /fog/.test(sky.desc) ? 'fog' : /clear/.test(sky.desc) ? 'clear' : 'cloud') : null;

  return { load, line, moodId, weatherWord, hhmm };
})();
