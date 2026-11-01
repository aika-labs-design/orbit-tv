// YouTube playback without YouTube's script in our frame: a plain embed iframe, driven over postMessage (the widget protocol).
// The main frame holds the bridge token, so no third-party code runs in it. The pure parts are tested with node.
(function (root) {
  const ORIGIN = 'https://www.youtube.com';
  const ID = /^[A-Za-z0-9_-]{11}$/;

  // The embed URL for a video id, or null for anything that is not one.
  function src(id, pageOrigin, opts) {
    if (typeof id !== 'string' || !ID.test(id)) return null;
    const q = { enablejsapi: 1, autoplay: 1, mute: opts && opts.muted ? 1 : 0, controls: 0, playsinline: 1, rel: 0, modestbranding: 1, iv_load_policy: 3, origin: pageOrigin };
    if (opts && opts.captions === false) q.cc_load_policy = 0;   // no captions over the page (muted trailers)
    return `${ORIGIN}/embed/${id}?` + Object.keys(q).map(k => k + '=' + encodeURIComponent(q[k])).join('&');
  }
  const command = (func, args) => JSON.stringify({ event: 'command', func, args: args || [], id: 1, channel: 'widget' });
  const listening = () => JSON.stringify({ event: 'listening', id: 1, channel: 'widget' });

  // A message from the player frame: { type: 'ready' } | { type: 'state', state } | { type: 'error', code }, or null.
  function parse(origin, data) {
    if (origin !== ORIGIN) return null;
    let m = data;
    if (typeof m === 'string') { try { m = JSON.parse(m); } catch (e) { return null; } }
    if (!m || typeof m !== 'object') return null;
    if (m.event === 'onReady') return { type: 'ready' };
    if (m.event === 'onStateChange' && typeof m.info === 'number') return { type: 'state', state: m.info };
    if (m.event === 'onError') return { type: 'error', code: m.info };
    const info = m.info;
    if ((m.event === 'infoDelivery' || m.event === 'initialDelivery') && info && typeof info.playerState === 'number') return { type: 'state', state: info.playerState };
    return null;
  }

  // What a player is told once it is ready: listen, sound (or mute), no captions when asked, play.
  function startCommands(opts) {
    opts = opts || {};
    const out = ['onStateChange', 'onError'].map(ev => command('addEventListener', [ev]));
    if (opts.muted) out.push(command('mute'));
    else out.push(command('unMute'), command('setVolume', [opts.volume == null ? 100 : opts.volume]));
    if (opts.captions === false) out.push(command('unloadModule', ['captions']));
    out.push(command('playVideo'));
    return out;
  }

  // Puts a player for `id` into `host`. opts: { muted, volume, captions }. on: { playing(), ended(), error(code) }. Returns { destroy() }.
  // States follow the YouTube API: 1 playing, 0 ended. A player that has not answered within 10 s fails with 'timeout'.
  function mount(host, id, opts, on) {
    opts = opts || {}; on = on || {};
    const url = src(id, location.origin, opts);
    if (!url) { setTimeout(() => on.error && on.error('badid'), 0); return { destroy() {} }; }
    const f = document.createElement('iframe');
    f.width = 1920; f.height = 1080; f.allow = 'autoplay; encrypted-media'; f.setAttribute('frameborder', '0'); f.src = url;
    host.innerHTML = ''; host.appendChild(f);
    let ready = false, last = null, failed = false, gone = false;
    const post = (m) => { try { f.contentWindow.postMessage(m, ORIGIN); } catch (e) {} };
    const hello = setInterval(() => post(listening()), 250);   // until the player answers onReady
    const fail = (code) => { if (failed || gone) return; failed = true; if (on.error) on.error(code); };
    const silent = setTimeout(() => { if (!ready) { clearInterval(hello); fail('timeout'); } }, 10000);
    const start = () => startCommands(opts).forEach(post);
    function onMessage(e) {
      if (gone || e.source !== f.contentWindow) return;
      const m = parse(e.origin, e.data); if (!m) return;
      if (m.type === 'ready') { if (!ready && !failed) { ready = true; clearInterval(hello); clearTimeout(silent); start(); } return; }
      if (m.type === 'state') {
        if (m.state === last) return; last = m.state;
        if (m.state === 1 && on.playing) on.playing();
        if (m.state === 0 && on.ended) on.ended();
        return;
      }
      if (m.type === 'error') fail(m.code);
    }
    window.addEventListener('message', onMessage);
    return {
      destroy() {
        if (gone) return; gone = true; clearInterval(hello); clearTimeout(silent); window.removeEventListener('message', onMessage);
        post(command('stopVideo')); f.src = 'about:blank'; f.remove();
      },
    };
  }

  const YtFrame = { src, command, startCommands, listening, parse, mount, ORIGIN };
  if (typeof module !== 'undefined' && module.exports) module.exports = YtFrame; else root.YtFrame = YtFrame;
})(this);
