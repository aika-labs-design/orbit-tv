const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const YtFrame = require(path.join(__dirname, '../../main/assets/web/ytframe.js'));

test('the embed URL carries the JS API flag, our origin and the mute choice', () => {
  const u = new URL(YtFrame.src('dQw4w9WgXcQ', 'https://orbit.local', { muted: true }));
  assert.strictEqual(u.origin, 'https://www.youtube.com');
  assert.strictEqual(u.pathname, '/embed/dQw4w9WgXcQ');
  assert.strictEqual(u.searchParams.get('enablejsapi'), '1');
  assert.strictEqual(u.searchParams.get('autoplay'), '1');
  assert.strictEqual(u.searchParams.get('mute'), '1');
  assert.strictEqual(u.searchParams.get('controls'), '0');
  assert.strictEqual(u.searchParams.get('origin'), 'https://orbit.local');
  assert.strictEqual(new URL(YtFrame.src('dQw4w9WgXcQ', 'https://orbit.local', {})).searchParams.get('mute'), '0');
});

test('anything that is not a YouTube video id gets no player', () => {
  for (const bad of ['', null, undefined, 'short', 'dQw4w9WgXcQ"><script>', '../../evil', 'dQw4w9WgXcQ?x=1']) assert.strictEqual(YtFrame.src(bad, 'https://orbit.local', {}), null);
});

test('commands are the widget protocol messages', () => {
  assert.deepStrictEqual(JSON.parse(YtFrame.command('playVideo')), { event: 'command', func: 'playVideo', args: [], id: 1, channel: 'widget' });
  assert.deepStrictEqual(JSON.parse(YtFrame.command('setVolume', [100])).args, [100]);
  assert.deepStrictEqual(JSON.parse(YtFrame.listening()), { event: 'listening', id: 1, channel: 'widget' });
});

test('player messages become ready, state and error; anything else is ignored', () => {
  const yt = 'https://www.youtube.com';
  assert.deepStrictEqual(YtFrame.parse(yt, '{"event":"onReady","id":1}'), { type: 'ready' });
  assert.deepStrictEqual(YtFrame.parse(yt, '{"event":"onStateChange","info":1}'), { type: 'state', state: 1 });
  assert.deepStrictEqual(YtFrame.parse(yt, '{"event":"infoDelivery","info":{"playerState":0,"currentTime":12}}'), { type: 'state', state: 0 });
  assert.deepStrictEqual(YtFrame.parse(yt, '{"event":"infoDelivery","info":{"currentTime":12}}'), null);
  assert.deepStrictEqual(YtFrame.parse(yt, '{"event":"onError","info":150}'), { type: 'error', code: 150 });
  assert.deepStrictEqual(YtFrame.parse(yt, { event: 'onReady' }), { type: 'ready' });
  assert.strictEqual(YtFrame.parse('https://evil.example', '{"event":"onReady"}'), null);   // only YouTube's own frame speaks for the player
  assert.strictEqual(YtFrame.parse(yt, 'not json'), null);
  assert.strictEqual(YtFrame.parse(yt, 'null'), null);
});

test('no page loads a YouTube script into the main frame', () => {
  const web = path.join(__dirname, '../../main/assets/web');
  for (const f of fs.readdirSync(web).filter(f => /\.(js|html)$/.test(f))) {
    const s = fs.readFileSync(path.join(web, f), 'utf8');
    assert.ok(!/youtube\.com\/iframe_api/.test(s), f + ' loads iframe_api');
    assert.ok(!/YT\.Player/.test(s), f + ' uses YT.Player');
  }
});

test('a player that never answers fails with a timeout after about ten seconds', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval'] });
  const listeners = [];
  global.location = { origin: 'https://orbit.local' };
  global.window = { addEventListener: (ev, fn) => listeners.push(fn), removeEventListener: () => {} };
  global.document = { createElement: () => ({ setAttribute() {}, remove() {}, contentWindow: { postMessage() {} } }) };
  try {
    const errors = [];
    const host = { innerHTML: '', appendChild() {} };
    YtFrame.mount(host, 'dQw4w9WgXcQ', { muted: true }, { error: (c) => errors.push(c) });
    t.mock.timers.tick(9000); assert.deepStrictEqual(errors, []);
    t.mock.timers.tick(1500); assert.deepStrictEqual(errors, ['timeout']);
    t.mock.timers.tick(30000); assert.deepStrictEqual(errors, ['timeout']);   // once
    const p = YtFrame.mount(host, 'dQw4w9WgXcQ', {}, { error: (c) => errors.push(c) });
    p.destroy(); t.mock.timers.tick(20000); assert.deepStrictEqual(errors, ['timeout']);   // a destroyed player says nothing
  } finally { delete global.location; delete global.window; delete global.document; }
});

// TV: YouTube captions showed over the shelf during a muted Cinema trailer
test('a player asked for no captions loads none and unloads the captions module', () => {
  const u = new URL(YtFrame.src('dQw4w9WgXcQ', 'https://orbit.local', { muted: true, captions: false }));
  assert.strictEqual(u.searchParams.get('cc_load_policy'), '0');
  assert.strictEqual(new URL(YtFrame.src('dQw4w9WgXcQ', 'https://orbit.local', {})).searchParams.get('cc_load_policy'), null);
  assert.deepStrictEqual(YtFrame.startCommands({ muted: true, captions: false }).map(m => JSON.parse(m).func), ['addEventListener', 'addEventListener', 'mute', 'unloadModule', 'playVideo']);
  assert.deepStrictEqual(JSON.parse(YtFrame.startCommands({ captions: false })[4]).args, ['captions']);
});
