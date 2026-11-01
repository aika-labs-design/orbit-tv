const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');
const K = require(path.join(__dirname, '../../main/assets/web/keyboard.js'));

// Walk the focus to the key with this label using only arrow presses, as a person with the remote would.
function go(s, label) {
  const at = K.find(label); assert.ok(at, 'no key ' + label);
  let guard = 40;
  while ((s.r !== at.r || s.c !== at.c) && guard--) {
    const k = s.r < at.r ? 'ArrowDown' : s.r > at.r ? 'ArrowUp' : s.c < at.c ? 'ArrowRight' : 'ArrowLeft';
    s = K.press(s, k);
  }
  assert.ok(s.r === at.r && s.c === at.c, 'reached ' + label);
  return s;
}
const tap = (s, label) => K.press(go(s, label), 'Enter');

test('keyboard types Ilé-Ifẹ̀ through the accent row opened by a long press', () => {
  let s = K.init('');
  s = tap(s, 'i'); s = tap(s, 'l');
  s = tap(s, 'e'); s = K.press(s, 'Hold');           // pressed, then held: the plain e is replaced by the accent row
  assert.ok(s.accents && s.accents.list.includes('é'));
  assert.strictEqual(s.text, 'Il');
  s = K.press(s, 'Enter');                             // é is first
  s = tap(s, '-'); s = tap(s, 'i'); s = tap(s, 'f');
  s = go(s, 'e'); s = K.press(s, 'Hold');             // held without a press first
  while (s.accents.list[s.accents.i] !== 'ẹ̀') s = K.press(s, 'ArrowRight');
  s = K.press(s, 'Enter');
  assert.strictEqual(s.text, 'Ilé-Ifẹ̀');
  assert.strictEqual(s.accents, null);
});

test('the first letter and letters after a space or hyphen are capitals; shift flips that once', () => {
  let s = K.init('');
  s = tap(s, 'n'); s = tap(s, 'e'); s = tap(s, 'w'); s = tap(s, 'space'); s = tap(s, 'y');
  assert.strictEqual(s.text, 'New Y');
  s = tap(s, 'shift'); s = tap(s, 'o'); s = tap(s, 'r');
  assert.strictEqual(s.text, 'New YOr');
});

test('delete removes a whole letter with its accent marks; cancelling the accent row keeps the letter', () => {
  let s = K.init('Ifẹ̀');
  s = tap(s, 'delete');
  assert.strictEqual(s.text, 'If');
  s = tap(s, 'o'); s = K.press(s, 'Hold'); s = K.press(s, 'Back');
  assert.strictEqual(s.text, 'Ifo'); assert.strictEqual(s.accents, null);
});

test('the accent key opens the row for the letter just typed', () => {
  let s = K.init('Bu');
  s = tap(s, 'u'); s = tap(s, 'accent');
  assert.ok(s.accents && s.accents.list.includes('ü'));
  while (s.accents.list[s.accents.i] !== 'ü') s = K.press(s, 'ArrowRight');
  s = K.press(s, 'Enter');
  assert.strictEqual(s.text, 'Buü');
});

test('Done marks the text finished; arrows stay on the board', () => {
  let s = K.init('Tolu');
  for (let i = 0; i < 12; i++) s = K.press(s, 'ArrowUp');
  assert.strictEqual(s.r, 0);
  s = tap(s, 'done');
  assert.strictEqual(s.done, true); assert.strictEqual(s.text, 'Tolu');
});
