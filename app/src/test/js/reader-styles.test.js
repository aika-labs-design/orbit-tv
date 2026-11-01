// The news reader and the toast are built by common.js on every page that loads it. A page whose stylesheets do not style them
// shows nothing while the reader takes every key (QA: Cinema's news reader was invisible). Setup never opens the reader.
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');

const WEB = path.join(__dirname, '../../main/assets/web');

function cssOf(page) {
  const html = fs.readFileSync(path.join(WEB, page), 'utf8');
  const linked = [...html.matchAll(/<link rel="stylesheet" href="([^"]+)">/g)].map(m => fs.readFileSync(path.join(WEB, m[1]), 'utf8'));
  const inline = [...html.matchAll(/<style>([\s\S]*?)<\/style>/g)].map(m => m[1]);
  return linked.concat(inline).join('\n');
}

const pages = fs.readdirSync(WEB).filter(f => f.endsWith('.html') && f !== 'setup.html' && /src="common\.js"/.test(fs.readFileSync(path.join(WEB, f), 'utf8')));

test('every page that loads common.js styles the reader and the toast', () => {
  assert.ok(pages.includes('cinema.html') && pages.includes('home.html'));
  for (const p of pages) {
    const css = cssOf(p);
    for (const sel of ['#reader{', '#reader.on{', '#reader .head{', '#reader .meta{', '#toast{', '#toast.on{']) assert.ok(css.includes(sel), `${p} has no ${sel} rule`);
  }
});
