// On-screen keyboard for the remote: alphabetical keys, a wide action row, and an accent row opened by holding Select
// on a letter (or the accent key after typing it). Pure; tested with node. The page draws ROWS and ACTIONS.
(function (root) {
  const ROWS = [
    'abcdefghij'.split(''),
    'klmnopqrst'.split(''),
    ['u', 'v', 'w', 'x', 'y', 'z', "'", '-', '.', '&'],
    '1234567890'.split(''),
  ];
  // The bottom row: wide keys covering columns from..to of the ten above.
  const ACTIONS = [
    { id: 'shift', label: 'Aa', from: 0, to: 1 }, { id: 'accent', label: 'à é', from: 2, to: 3 }, { id: 'space', label: 'Space', from: 4, to: 6 },
    { id: 'delete', label: 'Delete', from: 7, to: 7 }, { id: 'done', label: 'Done', from: 8, to: 9 },
  ];
  const ACT = ROWS.length;   // row index of the action row
  // Accented forms, most common first per letter. Yorùbá and Igbo tone marks sit on dotted vowels (ẹ́ ẹ̀ ọ́ ọ̀) as letter + combining mark.
  const ACCENTS = {
    a: ['á', 'à', 'â', 'ä', 'ã', 'å', 'ā', 'æ'], c: ['ç', 'č', 'ć'], d: ['đ'], e: ['é', 'è', 'ê', 'ë', 'ẹ', 'ẹ́', 'ẹ̀', 'ē', 'ę', 'ě'],
    g: ['ğ'], i: ['í', 'ì', 'î', 'ï', 'ị', 'ī'], l: ['ł'], n: ['ñ', 'ń', 'ṅ'], o: ['ó', 'ò', 'ô', 'ö', 'õ', 'ọ', 'ọ́', 'ọ̀', 'ø', 'ō', 'œ'],
    s: ['ṣ', 'ß', 'š', 'ś'], u: ['ú', 'ù', 'û', 'ü', 'ụ', 'ū'], y: ['ý', 'ÿ'], z: ['ž', 'ź', 'ż'],
  };
  const MAX = 40;

  // caps: 'words' (names and places: capitals at the start and after a space or hyphen) or 'first' (searches: the first letter only).
  const init = (text, caps) => ({ text: text || '', caps: caps || 'words', r: 0, c: 0, shift: false, accents: null, last: null, done: false });
  const keyAt = (r, c) => r === ACT ? ACTIONS.find(a => c >= a.from && c <= a.to) : { id: ROWS[r][c], label: ROWS[r][c], char: true };
  function find(label) {
    for (let r = 0; r < ROWS.length; r++) { const c = ROWS[r].indexOf(label); if (c >= 0) return { r, c }; }
    const a = ACTIONS.find(x => x.id === label);
    if (a) return { r: ACT, c: a.from };
    if (label === ' ') return find('space');
    return null;
  }
  // Shift flips the capitals rule for one letter.
  const upperNow = (s) => (s.text === '' || (s.caps !== 'first' && /[\s-]$/.test(s.text))) !== s.shift;
  const caseOf = (ch, up) => { if (!up) return ch; const u = ch.toUpperCase(); return u.length === ch.length ? u : ch; };
  // The last letter with its combining marks, so Delete never leaves a stray tone mark.
  const dropLast = (t) => { const base = t.replace(/[̀-ͯ]+$/, ''); const a = Array.from(base); a.pop(); return a.join(''); };

  function openAccents(s, base, up, removed) {
    const list = ACCENTS[base]; if (!list) return s;
    return Object.assign({}, s, { accents: { list: list.map(ch => caseOf(ch, up)), i: 0, removed: removed || '' }, shift: false });
  }

  function press(s, key) {
    s = Object.assign({}, s, { done: false });
    if (s.accents) {
      const a = s.accents;
      if (key === 'ArrowRight') return Object.assign(s, { accents: Object.assign({}, a, { i: Math.min(a.list.length - 1, a.i + 1) }) });
      if (key === 'ArrowLeft') return Object.assign(s, { accents: Object.assign({}, a, { i: Math.max(0, a.i - 1) }) });
      if (key === 'Enter') return Object.assign(s, { text: (s.text + a.list[a.i]).slice(0, MAX + 4), accents: null, last: null });
      return Object.assign(s, { text: s.text + a.removed, accents: null });   // Back, Up, Down: cancel and keep what was there
    }
    if (key === 'ArrowLeft' || key === 'ArrowRight') {
      if (s.r === ACT) {
        const i = ACTIONS.findIndex(a => s.c >= a.from && s.c <= a.to), j = Math.max(0, Math.min(ACTIONS.length - 1, i + (key === 'ArrowRight' ? 1 : -1)));
        return Object.assign(s, { c: ACTIONS[j].from });
      }
      return Object.assign(s, { c: Math.max(0, Math.min(ROWS[s.r].length - 1, s.c + (key === 'ArrowRight' ? 1 : -1))) });
    }
    if (key === 'ArrowUp') return Object.assign(s, { r: Math.max(0, s.r - 1) });
    if (key === 'ArrowDown') return Object.assign(s, { r: Math.min(ACT, s.r + 1) });
    const k = keyAt(s.r, s.c);
    if (key === 'Hold') {
      if (!k.char || !ACCENTS[k.id]) return s;
      // A hold starts as a press: take back the letter that press just typed, keeping its case.
      const typed = s.last && s.last.r === s.r && s.last.c === s.c && s.text.length ? Array.from(s.text).pop() : null;
      if (typed && typed.toLowerCase() === k.id) return openAccents(Object.assign(s, { text: dropLast(s.text), last: null }), k.id, typed !== k.id, typed);
      return openAccents(s, k.id, upperNow(s), '');
    }
    if (key !== 'Enter') return s;
    if (k.char) {
      if (s.text.length >= MAX) return s;
      return Object.assign(s, { text: s.text + caseOf(k.id, upperNow(s)), shift: false, last: { r: s.r, c: s.c } });
    }
    if (k.id === 'shift') return Object.assign(s, { shift: !s.shift });
    if (k.id === 'space') return s.text && !/\s$/.test(s.text) ? Object.assign(s, { text: s.text + ' ', last: null }) : s;
    if (k.id === 'delete') return Object.assign(s, { text: dropLast(s.text), last: null });
    if (k.id === 'done') return Object.assign(s, { done: true });
    if (k.id === 'accent') {
      const prev = Array.from(s.text.replace(/[̀-ͯ]+$/, '')).pop();
      if (!prev || !ACCENTS[prev.toLowerCase()] || prev !== Array.from(s.text).pop()) return s;
      return openAccents(Object.assign(s, { text: dropLast(s.text), last: null }), prev.toLowerCase(), prev !== prev.toLowerCase(), prev);
    }
    return s;
  }

  const Keyboard = { ROWS, ACTIONS, ACCENTS, init, press, find, keyAt };
  if (typeof module !== 'undefined' && module.exports) module.exports = Keyboard; else root.Keyboard = Keyboard;
})(this);
