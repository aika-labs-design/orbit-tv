const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const fs = require('node:fs');
const assets = path.join(__dirname, '../../main/assets');
const W = require(path.join(assets, 'web/world-data.js'));
const countries = JSON.parse(fs.readFileSync(path.join(assets, 'countries.json'), 'utf8'));
const languages = JSON.parse(fs.readFileSync(path.join(assets, 'languages.json'), 'utf8'));
W.load(languages);
const by = (code) => countries.find(c => c.code === code);

test('film tab names the industry where it is well known, otherwise the language', () => {
  assert.strictEqual(W.filmTab('yo'), 'Nollywood');
  assert.strictEqual(W.filmTab('kn'), 'Sandalwood');
  assert.strictEqual(W.filmTab('ko'), 'K-drama');
  assert.strictEqual(W.filmTab('is'), 'Icelandic films');
  assert.strictEqual(W.filmTab('zz'), 'Films');
});

// QA: Ana (Spanish first) was greeted "Good morning"
test('Spanish, Portuguese and French greet in their own words and their own hours', () => {
  assert.strictEqual(W.greeting('es', 9), 'Buenos días'); assert.strictEqual(W.greeting('es', 13), 'Buenas tardes');
  assert.strictEqual(W.greeting('es', 19), 'Buenas tardes'); assert.strictEqual(W.greeting('es', 20), 'Buenas noches'); assert.strictEqual(W.greeting('es', 2), 'Buenas noches');
  assert.strictEqual(W.greeting('pt', 9), 'Bom dia'); assert.strictEqual(W.greeting('pt', 18), 'Boa tarde'); assert.strictEqual(W.greeting('pt', 19), 'Boa noite');
  assert.strictEqual(W.greeting('fr', 9), 'Bonjour'); assert.strictEqual(W.greeting('fr', 17), 'Bonjour'); assert.strictEqual(W.greeting('fr', 18), 'Bonsoir');
  assert.strictEqual(W.greeting('en', 17), 'Good evening');   // languages without their own hours keep 12 and 17
});

test('greeting follows the hour and is null where none is verified', () => {
  assert.strictEqual(W.greeting('is', 9), null);
  assert.strictEqual(W.greeting('zz', 9), null);
  assert.strictEqual(W.greeting('en', 9), 'Good morning');
  assert.strictEqual(W.greeting('en', 12), 'Good afternoon');
  assert.strictEqual(W.greeting('en', 16.9), 'Good afternoon');
  assert.strictEqual(W.greeting('en', 17), 'Good evening');
  assert.strictEqual(W.greeting('en', 23), 'Good evening');
  assert.strictEqual(W.greeting('kn', 8), 'ಶುಭೋದಯ');
});

test('bundled data has the agreed shape', () => {
  assert.ok(countries.length >= 190);
  assert.strictEqual(new Set(countries.map(c => c.code)).size, countries.length);
  const langCodes = new Set(languages.map(l => l.code));
  for (const c of countries) {
    assert.match(c.code, /^[A-Z]{2}$/);
    assert.ok(c.name);
    c.languages.forEach(l => assert.ok(langCodes.has(l), `${c.code} language ${l}`));
    c.cities.forEach(x => { assert.ok(x.name && typeof x.lat === 'number' && typeof x.lon === 'number' && x.tz, `${c.code} city`); });
    if (c.news) assert.deepStrictEqual(Object.keys(c.news), ['hl', 'gl', 'ceid']);
  }
  for (const l of languages) {
    assert.match(l.code, /^[a-z]{2,3}$/, 'ISO 639-1, or 639-3 when no 639-1 exists');
    if (l.code.length === 3) assert.ok(!languages.some(x => x.code.length === 2 && x.name === l.name), l.code + ' has a 639-1 code');
    assert.ok(l.name && l.native);
    assert.strictEqual(typeof l.rtl, 'boolean');
    if (l.greeting) {   // the three words, and optionally the language's own hours: afternoon and evening start
      assert.deepStrictEqual(Object.keys(l.greeting).slice(0, 3), ['morning', 'afternoon', 'evening']);
      Object.keys(l.greeting).slice(3).forEach(k => { assert.ok(['afternoonFrom', 'eveningFrom'].includes(k), l.code + ' ' + k); assert.ok(Number.isInteger(l.greeting[k])); });
    }
  }
  ['ar', 'he', 'fa', 'ur'].forEach(c => assert.strictEqual(languages.find(l => l.code === c).rtl, true));
  assert.strictEqual(languages.find(l => l.code === 'en').rtl, false);
  assert.ok(!/\u2014/.test(JSON.stringify(countries) + JSON.stringify(languages)));
});

test('Nigerian Pidgin has its 639-3 code and comes after Nigeria\'s 639-1 languages', () => {
  const pcm = languages.find(l => l.code === 'pcm');
  assert.deepStrictEqual(pcm, { code: 'pcm', name: 'Nigerian Pidgin', native: 'Naijá', film: null, greeting: null, rtl: false, common: [] });
  const ng = by('NG').languages;
  assert.strictEqual(ng[ng.length - 1], 'pcm');
});

test('no language is listed twice under one name (Twi is Akan)', () => {
  const names = languages.map(l => l.name);
  assert.deepStrictEqual(names.filter((n, i) => names.indexOf(n) !== i), []);
  assert.ok(!languages.some(l => l.code === 'tw'));
  assert.deepStrictEqual(by('GH').languages, ['en', 'ak', 'ee']);
  const known = new Set(languages.map(l => l.code));
  countries.forEach(c => c.languages.forEach(l => assert.ok(known.has(l), `${c.code}: ${l}`)));
});

// QA: "Aland Islands" without its A-ring
test('countries carry their proper names, accents included, in the list\'s alphabetical place', () => {
  const name = (cc) => countries.find(c => c.code === cc).name;
  assert.strictEqual(name('AX'), 'Åland Islands'); assert.strictEqual(name('CW'), 'Curaçao'); assert.strictEqual(name('RE'), 'Réunion');
  assert.strictEqual(name('BL'), 'Saint Barthélemy'); assert.strictEqual(name('ST'), 'São Tomé and Príncipe');
  assert.ok(countries.findIndex(c => c.code === 'AX') < countries.findIndex(c => c.code === 'AL'), 'Åland sorts as Aland');
});

// review: native country names and common words come from the data tool, not app code
test('countries carry their names in their own languages, and some languages their common words', () => {
  assert.ok(by('DE').names.includes('Deutschland')); assert.ok(by('BR').names.includes('Brasil')); assert.ok(by('IT').names.includes('Italia'));
  assert.ok(languages.find(l => l.code === 'es').common.includes('del'));
  assert.ok(languages.find(l => l.code === 'en').common.includes('the'));
});

// review: short aliases that are ordinary words ("Vin", "Bec", "Pa", "Rio") let unrelated headlines through the home news gate
test('place names keep real other names and drop short or common-word aliases', () => {
  const places = JSON.parse(fs.readFileSync(path.join(assets, 'place-names.json'), 'utf8'));
  const vienna = places.AT.Vienna.names;
  assert.ok(vienna.includes('Wien'));
  assert.ok(!vienna.includes('Vin') && !vienna.includes('Bec'));
  const common = new Set(languages.flatMap(l => l.common || []));
  for (const cc of Object.keys(places)) for (const [city, p] of Object.entries(places[cc])) for (const n of p.names) {
    assert.ok(n.length > 3, `${cc} ${city}: ${n}`); assert.ok(!common.has(n.toLowerCase()), `${cc} ${city}: ${n}`);
  }
});
