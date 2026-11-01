// The real world: low orbit over Earth with NASA imagery, the sun where it actually is right now, the Milky Way behind.
// Apps are stations spaced along the orbit; the camera is a station window flying between them.
window.World = function (renderer, camera, mood, loader, perf) {
  perf = perf || new Set();
  const scene = new THREE.Scene();
  const V = THREE.Vector3;
  const R = 6.371;                       // Earth radius in scene units (1 unit = 1000 km)
  const ORBIT = R + 1.3;                 // stations at ~1300 km
  const CAM_ALT = R + 2.3;               // the window sits ~2300 km up, looking along the track

  const tex = (f, srgb) => { const t = loader.load('world/' + f); t.minFilter = THREE.LinearMipmapLinearFilter; t.anisotropy = 4; if (srgb) t.encoding = THREE.sRGBEncoding; return t; };
  const dayTex = tex('earth_day.jpg', true), nightTex = tex('earth_night.jpg', true), cloudTex = tex('earth_clouds.jpg', false), skyTex = tex('milkyway.jpg', true);
  renderer.outputEncoding = THREE.sRGBEncoding;
  camera.far = 1200; camera.updateProjectionMatrix();

  function radial(stops, size) { const c = document.createElement('canvas'); c.width = c.height = size || 256; const g = c.getContext('2d'); const r = g.createRadialGradient(c.width/2, c.height/2, 0, c.width/2, c.height/2, c.width/2); stops.forEach(([p, col]) => r.addColorStop(p, col)); g.fillStyle = r; g.fillRect(0, 0, c.width, c.height); return new THREE.CanvasTexture(c); }
  const softDisc = radial([[0, 'rgba(255,255,255,1)'], [0.25, 'rgba(255,255,255,.9)'], [1, 'rgba(255,255,255,0)']]);
  const limbTex = radial([[0, 'rgba(0,0,0,0)'], [0.86, 'rgba(0,0,0,0)'], [0.905, 'rgba(90,150,255,.95)'], [0.94, 'rgba(60,110,230,.45)'], [1, 'rgba(40,80,200,0)']], 1024);

  // ---------- sky ----------
  const sky = new THREE.Mesh(new THREE.SphereGeometry(400, 48, 24), new THREE.MeshBasicMaterial({ map: skyTex, side: THREE.BackSide, color: 0x9aa4b8 }));
  sky.rotation.y = Math.PI * 0.35; sky.visible = !perf.has('nosky'); scene.add(sky);

  // ---------- Earth: day/night mix with the real sun direction, soft terminator, specular on oceans ----------
  const sunDir = new V(1, 0, 0);
  const earthMat = new THREE.ShaderMaterial({
    uniforms: { dayMap: { value: dayTex }, nightMap: { value: nightTex }, cloudMap: { value: cloudTex }, cloudShift: { value: 0 }, sunDir: { value: sunDir }, camPos: { value: camera.position } },
    vertexShader: `varying vec2 vUv; varying vec3 vN; varying vec3 vW; void main(){ vUv = uv; vN = normalize(mat3(modelMatrix) * normal); vec4 w = modelMatrix * vec4(position,1.0); vW = w.xyz; gl_Position = projectionMatrix * viewMatrix * w; }`,
    fragmentShader: `precision mediump float; uniform sampler2D dayMap, nightMap, cloudMap; uniform float cloudShift; uniform vec3 sunDir, camPos; varying vec2 vUv; varying vec3 vN; varying vec3 vW;
      void main(){ vec3 n = normalize(vN); float ndl = dot(n, sunDir); float day = smoothstep(-0.12, 0.25, ndl);
        vec3 d = texture2D(dayMap, vUv).rgb; vec3 nt = texture2D(nightMap, vUv).rgb; float c = texture2D(cloudMap, vec2(vUv.x + cloudShift, vUv.y)).r;
        d = mix(d, vec3(0.97), c * 0.9); nt *= (1.0 - c * 0.7);
        vec3 lit = d * (0.25 + 0.95 * max(ndl, 0.0));
        vec3 v = normalize(camPos - vW); vec3 h = normalize(v + sunDir); float spec = pow(max(dot(n, h), 0.0), 48.0) * 0.35 * step(0.55, 1.0 - length(d - vec3(d.b)) ) * day;
        vec3 night = nt * vec3(1.0, 0.85, 0.6) * 1.6;
        vec3 col = mix(night, lit, day) + spec; 
        float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0); col += vec3(0.25, 0.5, 1.0) * rim * (0.35 + 0.65 * day);
        gl_FragColor = vec4(col, 1.0); }`,
  });
  const earth = new THREE.Mesh(new THREE.SphereGeometry(R, 64, 40), earthMat); earth.visible = !perf.has('noearth'); scene.add(earth);
  // atmosphere: a limb glow sprite centred on Earth (a shell would cost a second full pass on this GPU)
  const limb = new THREE.Mesh(new THREE.RingGeometry(R * 0.95, R * 1.12, 96, 1), new THREE.MeshBasicMaterial({ map: limbTex, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0.9 })); limb.visible = !perf.has('nolimb'); scene.add(limb);
  // sun: bright sprite far away in the sun direction
  const sunSprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: (() => { const c = document.createElement('canvas'); c.width = c.height = 256; const g = c.getContext('2d'); const r = g.createRadialGradient(128, 128, 0, 128, 128, 128); r.addColorStop(0, 'rgba(255,255,255,1)'); r.addColorStop(0.12, 'rgba(255,250,230,1)'); r.addColorStop(0.3, 'rgba(255,220,160,.45)'); r.addColorStop(1, 'rgba(255,200,120,0)'); g.fillStyle = r; g.fillRect(0, 0, 256, 256); return new THREE.CanvasTexture(c); })(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false })); sunSprite.scale.set(60, 60, 1); scene.add(sunSprite);

  // Real sun direction from UTC: subsolar latitude (declination) and longitude (equation of time ignored, minute-level accuracy is fine).
  let sunLonDeg = 0;
  function updateSun(date) {
    const d = (date - Date.UTC(date.getUTCFullYear(), 0, 0)) / 86400000; const decl = -23.44 * Math.cos(2 * Math.PI / 365 * (d + 10)) * Math.PI / 180;
    const utcH = date.getUTCHours() + date.getUTCMinutes() / 60; const lon = (12 - utcH) * 15 * Math.PI / 180;  // subsolar longitude
    sunLonDeg = (12 - utcH) * 15;
    // texture u=0 at lon -180: sphere geometry maps longitude so that +x is lon 90... align by rotating Earth: keep Earth fixed, place the sun by lon/lat
    sunDir.set(Math.cos(decl) * Math.cos(lon), Math.sin(decl), -Math.cos(decl) * Math.sin(lon)).normalize();
    sunSprite.position.copy(sunDir).multiplyScalar(300);
  }
  updateSun(new Date());

  // ---------- rails: windows hanging in front of the seat ----------
  // Rail 0 is Apps (slot -1 the Tonight card), rail 1 Live sport, rail 2 Today's headlines. Each rail is laid out in camera
  // space on an arc, stacked vertically; panels always face the viewer and the Earth and sky behind them are the real world.
  const RAIL = 3.3, SLOT = 0.37, RAIL_GAP = 1.75, PANEL_W = 0.9, PANEL_H = PANEL_W * 9 / 16;
  const rails = [[], [], []];              // items per rail
  const focus = [-1, 0, 0], focusPos = [-1, 0, 0];
  let railIndex = 0, railPos = 0, beacon = null;
  // Panels are opaque and drawn front-to-back so the frame behind costs only its border: this GPU is fill-bound at 1080p
  // (one blended full-screen layer is about 11 ms), so fades are done by darkening the colour, not by alpha.
  const geoCache = new Map();  // one PlaneGeometry per size, shared by every panel (panels are rebuilt often)
  const geo = (w, h) => { const k = w.toFixed(3) + 'x' + h.toFixed(3); if (!geoCache.has(k)) geoCache.set(k, new THREE.PlaneGeometry(w, h)); return geoCache.get(k); };
  const loadArt = (dataUrl) => { const t = loader.load(dataUrl); t.minFilter = THREE.LinearFilter; t.generateMipmaps = false; t.encoding = THREE.sRGBEncoding; return t; };
  function makePanel(dataUrl, w, h, brand) {
    const group = new THREE.Group();
    const art = loadArt(dataUrl);
    const panel = new THREE.Mesh(geo(w, h), new THREE.MeshBasicMaterial({ map: art })); group.add(panel);
    const frame = new THREE.Mesh(geo(w + 0.036, h + 0.036), new THREE.MeshBasicMaterial({ color: 0x0a0f18 })); frame.position.z = -0.004; group.add(frame);
    scene.add(group);
    return { group, panel, frame, art, brand: new THREE.Color(brand || mood.accent), focus: 0 };
  }
  function dropPanel(item) { scene.remove(item.group); item.art.dispose(); item.panel.material.dispose(); item.frame.material.dispose(); }
  function swapArt(item, dataUrl) { const old = item.art; item.art = loadArt(dataUrl); item.panel.material.map = item.art; item.panel.material.needsUpdate = true; old.dispose(); }
  function setHologram(dataUrl) {
    if (beacon) { dropPanel(beacon); beacon = null; }
    if (!dataUrl) return;
    beacon = makePanel(dataUrl, 1.1, 1.1 * 9 / 16, mood.accent); beacon.slot = -1; beacon.rail = 0; beacon.kind = 'tonight';
  }
  function build(apps) {
    rails[0].forEach(dropPanel); rails[0] = [];
    apps.forEach((app, i) => { const st = makePanel(app.art, PANEL_W, PANEL_H, app.brand); st.app = app; st.data = app; st.i = i; st.slot = i; st.rail = 0; st.kind = 'app'; rails[0].push(st); });
    clampFocus();
  }
  /**
   * Live (rail 1) and Today (rail 2): items are { id, key, art, brand, data } where art() draws the card lazily and key is the
   * card's JSON. Panels are keyed by id: unchanged cards keep their panel and focus, changed cards swap the texture only.
   */
  function setCards(railName, items) {
    const r = railName === 'live' ? 1 : 2;
    const old = new Map(rails[r].map(st => [st.id, st]));
    rails[r] = items.map((it, i) => {
      let st = old.get(it.id);
      if (st) { old.delete(it.id); if (st.key !== it.key) { swapArt(st, it.art()); st.key = it.key; st.brand.set(it.brand); } }
      else { st = makePanel(it.art(), PANEL_W, PANEL_H, it.brand); st.id = it.id; st.key = it.key; st.rail = r; st.kind = railName; }
      st.data = it.data; st.i = i; st.slot = i; return st;
    });
    old.forEach(dropPanel);
    clampFocus();
  }
  /** Redraw one card's texture in place (used for the age text ticking over). */
  function updateCard(railName, id, dataUrl) { const st = rails[railName === 'live' ? 1 : 2].find(x => x.id === id); if (st) swapArt(st, dataUrl); }
  function railCount() { let n = 1; while (n < 3 && rails[n].length) n++; return n; }  // a rail without cards is not reachable, nor any above it
  function clampFocus() {
    railIndex = Math.min(railIndex, railCount() - 1);
    focus[0] = Math.max(beacon ? -1 : 0, Math.min(rails[0].length - 1, focus[0]));
    for (let r = 1; r < 3; r++) focus[r] = Math.max(0, Math.min(rails[r].length - 1, focus[r]));
  }

  // ---------- camera: a window seat in orbit ----------
  // Orbital position in latitude and longitude (degrees), so the seat can fly over a venue; longitude drifts west to east at rest.
  const cam = { pos: new V(CAM_ALT, 0, 0), vel: new V(), look: new V(0, 0, 0), lookVel: new V() };
  const orb = { lat: 0, lon: 0, tLat: 0, tLon: 0, venue: null };
  const desired = new V(), desiredLook = new V(), tmp = new V(), up = new V(0, 1, 0), local = new V();
  let warp = null, introT = 1, idle = 0;
  const orbitSpeed = 0.010 * 180 / Math.PI;   // deg/s: Earth rolls by under the seat
  const RAD = Math.PI / 180;
  function dirFor(out, latDeg, lonDeg) { const la = latDeg * RAD, lo = lonDeg * RAD; return out.set(Math.cos(la) * Math.cos(lo), Math.sin(la), -Math.cos(la) * Math.sin(lo)); }
  function flyTo(lat, lon) { if (typeof lat !== 'number' || typeof lon !== 'number') { flyHome(); return; } orb.venue = { lat, lon }; }
  function flyHome() { if (orb.venue) { orb.tLon = orb.lon; } orb.venue = null; }
  function targetFor(outPos, outLook) {
    dirFor(outPos, orb.lat, orb.lon).multiplyScalar(CAM_ALT);
    dirFor(outLook, orb.lat - 3, orb.lon + 24).multiplyScalar(R + 0.55);
  }
  const settled = () => Math.abs(focusPos[railIndex] - focus[railIndex]) < 0.02 && Math.abs(railPos - railIndex) < 0.02 && !warp && introT >= 1;

  function placeSlot(item, dt, ease) {
    const k = item.slot - focusPos[item.rail], ak = Math.abs(k), dr = item.rail - railPos, adr = Math.abs(dr);
    item.group.visible = ak < 3.2 && adr < 1.6 && !perf.has('nopanels'); if (!item.group.visible) return;
    const kc = THREE.MathUtils.clamp(k, -2.2, 2.2), th = kc * SLOT, far = 1 + Math.max(0, ak - 1) * 0.1;
    local.set(Math.sin(th) * RAIL + (k - kc) * 1.1, 0.12 + ak * 0.03 + dr * RAIL_GAP, -Math.cos(th) * RAIL * far - (1 - ease) * 6);
    const want = Math.max(0, 1 - ak) * Math.max(0, 1 - adr); item.focus += (want - item.focus) * Math.min(1, dt * 6);
    if (warp && warp.item === item) { const e = warp.t * warp.t; local.z += (RAIL - 0.45) * e; local.y *= 1 - e; local.x *= 1 - e; }
    item.group.position.copy(local).applyMatrix4(camera.matrixWorld);
    item.group.quaternion.copy(camera.quaternion);
    const fade = THREE.MathUtils.clamp(1 - (ak - 1) * 0.28, 0.25, 1) * THREE.MathUtils.clamp(1 - adr * 0.6, 0.2, 1);
    item.panel.material.color.setScalar((0.62 + 0.38 * item.focus) * fade);
    item.frame.material.color.setRGB(0.04, 0.06, 0.09).lerp(item.brand, item.focus * 0.6).multiplyScalar(fade);
    item.group.scale.setScalar((1 + 0.28 * item.focus) * ease);
  }

  function step(dt, time) {
    earthMat.uniforms.cloudShift.value += dt * 0.0009; // Earth stays put: day and night are geographically true
    if (perf.has('dream')) { orb.tLat = 14 * Math.sin(time / 320); orb.tLon = sunLonDeg - 38 + 10 * Math.sin(time / 210); }  // screensaver: stay over the sunlit Earth (the seat sits 38 degrees west of the subsolar point, looking east into full daylight)
    else if (orb.venue) { orb.tLat = orb.venue.lat; orb.tLon = orb.venue.lon; } else { orb.tLat = 0; orb.tLon += orbitSpeed * dt; }
    let dl = orb.tLon - orb.lon; dl = ((dl + 540) % 360) - 180;   // shortest way round
    orb.lat += (orb.tLat - orb.lat) * Math.min(1, dt * 1.6); orb.lon += dl * Math.min(1, dt * 1.6);
    if (time % 30 < dt) updateSun(new Date());
    if (introT < 1) introT = Math.min(1, introT + dt / 2.2);
    const ease = 1 - Math.pow(1 - introT, 3);
    for (let r = 0; r < 3; r++) focusPos[r] += (focus[r] - focusPos[r]) * Math.min(1, dt * 7);
    railPos += (railIndex - railPos) * Math.min(1, dt * 6);
    targetFor(desired, desiredLook);
    idle = settled() ? idle + dt : 0;
    if (idle > 5) { const sway = Math.min(1, (idle - 5) / 4), a = (idle - 5) * 0.05; desired.y += Math.sin(a) * 0.2 * sway; tmp.copy(desired).sub(desiredLook); tmp.applyAxisAngle(up, Math.sin(a * 0.7) * 0.08 * sway); desired.copy(desiredLook).add(tmp); }
    if (introT < 1) { tmp.copy(desired).normalize().multiplyScalar(CAM_ALT + 7); tmp.y += 5; desired.lerpVectors(tmp, desired, ease); }
    if (warp) { warp.t = Math.min(1, warp.t + dt / 0.8); const e = warp.t * warp.t; camera.fov = 38 + 26 * e; camera.updateProjectionMatrix(); }
    else if (Math.abs(camera.fov - 38) > 0.05) { camera.fov += (38 - camera.fov) * Math.min(1, dt * 4); camera.updateProjectionMatrix(); }
    cam.vel.addScaledVector(tmp.copy(desired).sub(cam.pos), 9 * dt).multiplyScalar(Math.max(0, 1 - 4.8 * dt)); cam.pos.addScaledVector(cam.vel, dt);
    cam.lookVel.addScaledVector(tmp.copy(desiredLook).sub(cam.look), 12 * dt).multiplyScalar(Math.max(0, 1 - 5.5 * dt)); cam.look.addScaledVector(cam.lookVel, dt);
    camera.position.copy(cam.pos); camera.up.copy(cam.pos).normalize(); camera.lookAt(cam.look); camera.updateMatrixWorld();
    limb.lookAt(camera.position);
    rails.forEach(items => items.forEach(s => placeSlot(s, dt, ease))); if (beacon) placeSlot(beacon, dt, ease);
    if (warp && warp.t >= 1 && warp.done) { const d = warp.done; warp.done = null; d(); }
  }

  function next(dir) { idle = 0; const lo = railIndex === 0 && beacon ? -1 : 0, hi = rails[railIndex].length - 1; if (hi < lo) return; focus[railIndex] = Math.max(lo, Math.min(hi, focus[railIndex] + dir)); }
  function climb(dir) { idle = 0; railIndex = Math.max(0, Math.min(railCount() - 1, railIndex + dir)); }
  function focusApp(pkg) { idle = 0; railIndex = 0; const i = rails[0].findIndex(s => s.app.pkg === pkg); focus[0] = i >= 0 ? i : (beacon ? -1 : 0); }
  function focused() {
    if (railIndex === 0) { if (focus[0] < 0) return beacon ? { kind: 'tonight', data: null, i: -1 } : null; const s = rails[0][focus[0]]; return s ? { kind: 'app', data: s.app, app: s.app, i: s.i } : null; }
    const s = rails[railIndex][focus[railIndex]]; return s ? { kind: s.kind, data: s.data, i: s.i } : null;
  }
  function focusedItem() { return railIndex === 0 && focus[0] < 0 ? beacon : rails[railIndex][focus[railIndex]]; }
  function startWarp(done) { const s = focusedItem(); if (!s) { done(); return; } warp = { item: s, t: 0, done }; }
  function endWarp() { warp = null; camera.fov = 38; camera.updateProjectionMatrix(); }
  function playIntro() { introT = 0; cam.vel.set(0, 0, 0); targetFor(cam.pos, cam.look); tmp.copy(cam.pos).normalize().multiplyScalar(CAM_ALT + 7); cam.pos.copy(tmp); cam.pos.y += 5; }
  return { scene, build, setCards, updateCard, setHologram, step, next, climb, focusApp, flyTo, flyHome, startWarp, endWarp, playIntro, settled, focused, rail: () => railIndex, railCount, isStar: () => railIndex === 0 && focus[0] < 0, planets: () => rails[0] };
};
