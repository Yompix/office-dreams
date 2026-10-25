// Проверка pad.js в браузере: клавиатура (стрелки, Enter, Esc) и пульт с нестандартной раскладкой — xpad (Steam Deck / виртуальный пульт Steam в Linux)
// и DirectInput с крестовиной на оси 9. Сервер игры: cd public/games && python3 -m http.server 8771. Запуск: node tools/desktop/pad_test.js
const { chromium } = require('playwright');
const U = `http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?lang=ru&gfx=retro`;
let fails = 0; const ok = (c, m) => { console.log((c ? '  ✓ ' : '  ✗ ') + m); if (!c) fails++; };
(async () => {
  const br = await chromium.launch(); const pg = await br.newPage({ viewport: { width: 1280, height: 800 } });
  const errs = []; pg.on('pageerror', e => errs.push(String(e)));
  await pg.goto(U); await pg.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 180000 });
  const cur = () => pg.evaluate(() => { const c = window.__pad.cur; return c ? (c.id || c.className) + ':' + c.textContent.trim().slice(0, 20) : null; });
  const fr = n => pg.evaluate(n => new Promise(r => { const f0 = window.__pad.frames; const w = () => window.__pad.frames - f0 >= n ? r() : setTimeout(w, 15); w(); }), n);
  // титул
  if (await pg.evaluate(() => !!document.querySelector('#ui .title.gate'))) { await pg.keyboard.press('Enter'); await pg.waitForTimeout(900); }
  ok(await pg.evaluate(() => !!document.querySelector('#ui .title.open')), 'клавиатура: Enter открыл меню');
  await pg.keyboard.press('ArrowDown'); await fr(3); await pg.waitForTimeout(500);
  const c1 = await cur(); ok(await pg.evaluate(() => document.body.classList.contains('pad')) && !!c1, 'клавиатура: стрелка включила рамку (' + c1 + ')');
  // до «Настройки»
  for (let i = 0; i < 6 && !/Настрой/.test(await cur() || ''); i++) { await pg.keyboard.press('ArrowDown'); await fr(3); await pg.waitForTimeout(250); }
  ok(/Настрой/.test(await cur() || ''), 'клавиатура: рамка дошла до «Настройки» (' + await cur() + ')');
  await pg.keyboard.press('Enter'); await fr(3); await pg.waitForTimeout(700);
  ok(await pg.evaluate(() => !!document.querySelector('#ui .sets')), 'клавиатура: Enter открыл настройки');
  await pg.keyboard.press('Escape'); await fr(3); await pg.waitForTimeout(700);
  ok(await pg.evaluate(() => !document.querySelector('#ui .sets')), 'клавиатура: Esc закрыл настройки');
  // пульт xpad: 11 кнопок, 8 осей, mapping '' ; LT в покое −1
  await pg.evaluate(() => {
    const b = () => ({ pressed: false, touched: false, value: 0 });
    window.__fp = { id: 'Microsoft X-Box 360 pad (Vendor: 28de Product: 11ff)', index: 0, connected: true, mapping: '', timestamp: 0, buttons: Array.from({ length: 11 }, b), axes: [0, 0, -1, 0, 0, -1, 0, 0] };
    Object.defineProperty(navigator, 'getGamepads', { configurable: true, value: () => [window.__fp, null, null, null] });
  });
  const btn = (i, on) => pg.evaluate(([i, on]) => { const x = window.__fp.buttons[i]; x.pressed = on; x.value = on ? 1 : 0; }, [i, on]);
  const axis = (i, v) => pg.evaluate(([i, v]) => { window.__fp.axes[i] = v; }, [i, v]);
  // нажать на один кадр pad.js (страница под программной графикой рисует ~3 кадра в секунду — иначе срабатывает повтор)
  const tapAxis = (i, v, rest = 0) => pg.evaluate(([i, v, rest]) => new Promise(r => { window.__fp.axes[i] = v; const f0 = window.__pad.frames; const w = () => { if (window.__pad.frames > f0) { window.__fp.axes[i] = rest; r(); } else setTimeout(w, 1); }; w(); }), [i, v, rest]);
  const tapBtn = i => pg.evaluate(i => new Promise(r => { const x = window.__fp.buttons[i]; x.pressed = true; x.value = 1; const f0 = window.__pad.frames; const w = () => { if (window.__pad.frames > f0) { x.pressed = false; x.value = 0; r(); } else setTimeout(w, 1); }; w(); }), i);
  const press = async i => { await tapBtn(i); await fr(2); await pg.waitForTimeout(350); };
  await pg.mouse.move(10, 10); await pg.mouse.move(60, 60); await pg.waitForTimeout(300);   // мышь — рамка прячется });
  await tapAxis(7, 1); await fr(2); await pg.waitForTimeout(400);   // крестовина вниз (ось 7)
  ok(await pg.evaluate(() => document.body.classList.contains('pad')), 'xpad: крестовина (ось 7) включила рамку (' + await cur() + ')');
  for (const v of [-1, 1]) for (let i = 0; i < 6 && !/Настрой/.test(await cur() || ''); i++) { await tapAxis(7, v); await fr(2); await pg.waitForTimeout(250); console.log('    ', v, i, await cur()); }
  ok(/Настрой/.test(await cur() || ''), 'xpad: крестовина дошла до «Настройки»');
  await press(0); await pg.waitForTimeout(500);
  ok(await pg.evaluate(() => !!document.querySelector('#ui .sets')), 'xpad: A (кнопка 0) открыл настройки');
  await press(1); await pg.waitForTimeout(600);
  ok(await pg.evaluate(() => !document.querySelector('#ui .sets')), 'xpad: B (кнопка 1) — назад');
  // DirectInput: крестовина на оси 9 (1.2857 — не нажата), покой
  await pg.evaluate(() => { window.__fp.id = 'Generic USB Joystick (Vendor: 0079 Product: 0006)'; window.__fp.axes = [0, 0, 0, 0, 0, 0, 0, 0, 0, 1.2857]; });
  await fr(4); const before = await cur();
  await pg.waitForTimeout(600); ok(await cur() === before, 'DirectInput: покой оси 9 — рамка стоит (' + before + ')');
  await tapAxis(9, 1 / 7, 1.2857); await fr(2); await pg.waitForTimeout(400);   // вниз
  const after = await cur(); ok(after !== before, 'DirectInput: ось 9 «вниз» сдвинула рамку (' + after + ')');
  ok(!errs.length, 'ошибок страницы нет' + (errs.length ? ': ' + errs.slice(0, 3).join(' | ') : ''));
  await br.close(); console.log(fails ? 'ОШИБОК: ' + fails : 'всё прошло'); process.exit(fails ? 1 : 0);
})();
