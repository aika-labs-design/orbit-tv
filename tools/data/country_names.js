// Writes src/country-names.tsv: each country's name in each of its own languages (CLDR through Node's Intl.DisplayNames).
//   node tools/data/country_names.js
// Only names that differ from the English one are kept (Deutschland, Brasil, Italia, México). Standard library only.
const fs = require('fs'), path = require('path');
const SRC = path.join(__dirname, 'src');
const en = new Intl.DisplayNames(['en'], { type: 'region' });
const rows = [];
for (const line of fs.readFileSync(path.join(SRC, 'countryInfo.txt'), 'utf8').split('\n')) {
  if (!line.trim() || line.startsWith('#')) continue;
  const f = line.split('\t'), cc = f[0], langs = [...new Set((f[15] || '').split(',').map(x => x.split('-')[0]).filter(x => /^[a-z]{2}$/.test(x)))];
  const seen = new Set([en.of(cc)]);
  for (const l of langs) {
    let n; try { n = new Intl.DisplayNames([l], { type: 'region', fallback: 'none' }).of(cc); } catch (e) { continue; }
    if (!n || n === cc || seen.has(n)) continue;
    seen.add(n); rows.push([cc, l, n].join('\t'));
  }
}
fs.writeFileSync(path.join(SRC, 'country-names.tsv'), 'code\tlang\tname\n' + rows.join('\n') + '\n');
console.error(rows.length + ' native country names');
