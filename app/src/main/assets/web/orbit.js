// The screensaver page (index.html#dream): the Earth-orbit world with the clock, the date, the sky line and the live sport line.
// The dream service pushes the feeds through orbit.setFeeds; nothing here takes keys.
(function () {
  const log = (m) => { try { Android.log(m); } catch (e) { console.log(m); } };
  const $ = (id) => document.getElementById(id);
  const W = 1920, H = 1080;

  // ---------- mood and sky ----------
  const MOODS = {
    cyan:   { id: 'cyan', name: 'Cyan',   accent: '#3df2ff', deep: '#03101a', glow: 0x3df2ff },
    violet: { id: 'violet', name: 'Violet', accent: '#c77dff', deep: '#0c0516', glow: 0xc77dff },
    amber:  { id: 'amber', name: 'Amber',  accent: '#ffb347', deep: '#160d03', glow: 0xffb347 },
    mint:   { id: 'mint', name: 'Mint',   accent: '#6cffb8', deep: '#03150e', glow: 0x6cffb8 },
  };
  const hour = new Date().getHours();
  let sky = null; try { sky = JSON.parse(localStorage.getItem('orbit.sky.v1') || 'null'); sky = sky && Date.now() - sky.at < 30 * 60 * 1000 ? sky.data : null; } catch (e) {}
  const mood = MOODS[Sky.moodId(sky, hour)];
  document.body.classList.add('dream');
  document.body.style.background = mood.deep; $('veil').style.background = mood.deep; document.documentElement.style.setProperty('--accent', mood.accent);
  $('skyline').textContent = Sky.line(sky);
  Sky.load().then(s2 => { if (s2) { sky = s2; $('skyline').textContent = Sky.line(sky); log('sky: ' + Sky.line(sky)); } }).catch(e => log('sky failed ' + e));

  // ---------- renderer and world ----------
  const PERF = new Set(location.hash.slice(1).split(',').filter(Boolean));   // "dream", plus any render toggles world.js reads
  const renderer = new THREE.WebGLRenderer({ canvas: $('gl'), antialias: false, alpha: false, powerPreference: 'high-performance' });
  renderer.setPixelRatio(1);
  // The 3D layer renders at 0.8 scale (1536x864) and the TV scales it up; the HUD is DOM and stays at full 1080p (the GPU is fill-bound).
  const scale = [...PERF].map(f => /^scale=/.test(f) ? +f.slice(6) : 0).find(Boolean) || 0.8;
  renderer.setSize(Math.round(W * scale), Math.round(H * scale), false);
  renderer.setClearColor(new THREE.Color(mood.deep), 1); renderer.autoClear = false;
  const camera = new THREE.PerspectiveCamera(38, W / H, 0.1, 200);
  const sys = World(renderer, camera, mood, new THREE.TextureLoader(), PERF);

  // ---------- HUD: clock, date, sky and the live (or next) sport line ----------
  function setVeil(on, ms) { const v = $('veil'); v.style.transition = `opacity ${ms || 600}ms`; v.style.opacity = on ? 1 : 0; }
  let feeds = { live: [] };
  function dreamLines() {
    const on = feeds.live.find(c => c.state === 'LIVE'), next = feeds.live.find(c => c.state === 'NEXT');
    $('label').textContent = on ? `${on.title}` : next ? `${next.title}` : '';
    $('sub').textContent = on ? `LIVE  ·  ${on.line1}` : next ? `NEXT  ·  ${next.line1}` : '';
    $('rule').classList.toggle('on', !!(on || next));
  }
  // Only the current person's loved sports (the pages keep who is watching in orbit.profile, on the same origin); loving none shows nothing.
  const lastId = () => { try { return localStorage.getItem('orbit.profile'); } catch (e) { return null; } };
  window.orbit = { setFeeds(f) { const sb = (f && f.sportsBy) || []; feeds = { live: Profiles.liveOf(lastId(), (f && f.live) || [], sb) }; dreamLines(); } };
  const tick = () => { const d = new Date(); $('host').textContent = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`; $('meta').textContent = d.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }).toUpperCase(); $('skyline').textContent = Sky.line(sky); };
  tick(); setInterval(tick, 15000); setVeil(false, 1200); sys.focusApp(null); sys.flyHome();

  // ---------- frame loop ----------
  let frames = 0, lastReport = performance.now(), lastT = lastReport, t0 = lastReport;
  function frame(now) {
    requestAnimationFrame(frame);
    const dt = Math.min(0.1, (now - lastT) / 1000); lastT = now; const time = (now - t0) / 1000;
    sys.step(dt, time);
    renderer.clear(); renderer.render(sys.scene, camera);
    frames++; if (now - lastReport >= 2000) { const fps = (frames * 1000 / (now - lastReport)).toFixed(1); $('fps').textContent = `${fps} FPS`; log(`fps ${fps}`); frames = 0; lastReport = now; }
  }
  requestAnimationFrame(frame);
  try { Android.ready(window.__orbitToken || ''); } catch (e) { log('no native shell; demo mode'); }
  log('orbit dream ready, mood ' + mood.name);
})();
