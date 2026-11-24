// Каждая кнопка меню, настроек и паузы что-то делает (меняется экран/состояние) и не бросает ошибку JS.
//   PW=$PWD/tools/nosrv_pw.js node tools/ui/buttons_test.js   (LAND=1 — горизонтально, LANG2=uk|en)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
(async () => {
  const PORT = process.env.PORT || 8774, LAND = process.env.LAND === '1', LANG = process.env.LANG2 || '';
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await b.newContext({ viewport: LAND ? { width: 640, height: 360 } : { width: 360, height: 640 }, locale: 'ru-RU' });
  await ctx.addInitScript(l => { if (!sessionStorage.getItem('init')) { sessionStorage.setItem('init', 1); localStorage.clear(); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_land3', l ? 'true' : 'false'); } }, LAND);
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.goto(`http://127.0.0.1:${PORT}/web/index.html?gfx=retro${LANG ? '&lang=' + LANG : ''}`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.waitForTimeout(4000);   // пункты меню выезжают анимацией
  let fail = 0; const bad = m => { console.log('  ✗ ' + m); fail++; };
  const snap0 = () => p.evaluate(() => [...document.querySelectorAll('input[type=range]')].map(e => e.value).join(',') + [...document.querySelectorAll('#ui, #pz, #stage > .layer')].map(e => e.innerHTML.replace(/\d\d:\d\d/g, '')).join('').length + (document.querySelector('#pz') || {}).className + '|' + window.__ns.mode + '|' + document.body.className + '|' + JSON.stringify(Object.entries(localStorage).sort()));
  const snap = async () => { for (let k = 0; k < 3; k++) { try { await p.waitForFunction(() => window.__ns && window.__ns.mode, null, { timeout: 120000 }); return await snap0(); } catch (e) { await p.waitForTimeout(1500); } } return 'reload'; };   // язык и графика могут перезагрузить страницу
  const vis = sel => p.evaluate(s => [...document.querySelectorAll(s)].filter(e => { const r = e.getBoundingClientRect(); return r.width > 2 && r.height > 2 && getComputedStyle(e).visibility !== 'hidden'; }).map((e, i) => i), sel);
  const label = (sel, i) => p.evaluate(([s, i]) => { const e = [...document.querySelectorAll(s)].filter(e => { const r = e.getBoundingClientRect(); return r.width > 2 && r.height > 2; })[i]; return e ? (e.textContent || e.title || e.id || e.className).trim().slice(0, 40) : '?'; }, [sel, i]);
  const click = (sel, i) => p.evaluate(([s, i]) => { const e = [...document.querySelectorAll(s)].filter(e => { const r = e.getBoundingClientRect(); return r.width > 2 && r.height > 2; })[i]; if (!e) return false;
    if (e.type === 'range') { e.value = +e.value > +e.min + 5 ? e.min : e.max; e.dispatchEvent(new Event('input', { bubbles: true })); e.dispatchEvent(new Event('change', { bubbles: true })); return true; }   // ползунок двигаем, а не жмём
    e.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); e.dispatchEvent(new PointerEvent('pointerup', { bubbles: true })); e.click(); return true; }, [sel, i]);
  const toMenu = async () => { for (let k = 0; k < 6; k++) { await p.waitForFunction(() => window.__ns && window.__ns.mode, null, { timeout: 120000 }); if (await p.evaluate(() => window.__ns.mode === 'menu' && !!document.querySelector('.title .mlist'))) return; await p.keyboard.press('Escape'); await p.waitForTimeout(400);
    await p.evaluate(() => { const x = document.querySelector('.screen .back, .screen [data-back], .set .back, button.back'); x && x.click(); }); await p.waitForTimeout(300); } };
  // 1) главное меню: каждый пункт (кроме «Начать»/«Продолжить» — их проверяет path_test)
  const MSEL = '.title .mlist button';
  const n = (await vis(MSEL)).length; console.log('меню: кнопок ' + n);
  for (let i = 0; i < n; i++) { await toMenu(); const t = await label(MSEL, i); if (/^(Начать|Продолжить|Почати|Продовжити|Start|Continue|New)/i.test(t) || i === 0) { console.log('  · ' + t + ' — пропуск (path_test)'); continue; }
    const a = await snap(); await click(MSEL, i); await p.waitForTimeout(700); const z = await snap();
    if (a === z) bad(`меню «${t}» — ничего не произошло`); else console.log('  ✓ ' + t);
    // внутри открывшегося экрана — все кнопки/переключатели
    const IN = '.screen button, .screen .set, .screen input, .screen select';
    const m = (await vis(IN)).length;
    for (let j = 0; j < m; j++) { const tj = await label(IN, j); if (/выход|exit|вийти|quit|закрыть игру/i.test(tj)) continue;
      const a2 = await snap(); await click(IN, j); await p.waitForTimeout(350); const z2 = await snap();
      if (a2 === z2) bad(`«${t}» → «${tj}» — ничего не произошло`);
      if (!(await p.evaluate(() => !!document.querySelector('.screen')))) { await toMenu(); await click(MSEL, i); await p.waitForTimeout(500); } }
  }
  // 2) пауза в игре: начать, дождаться сцены, Esc, все кнопки паузы
  await toMenu(); await p.waitForTimeout(1500); await p.evaluate(() => document.getElementById('mNew').click());
  await p.waitForFunction(() => window.__ns.mode !== 'menu', null, { timeout: 60000 }).catch(() => bad('«Начать смену» не сработала')); await p.waitForTimeout(3000);
  for (let k = 0; k < 20 && !(await p.evaluate(() => /scene|choice|shift/.test(window.__ns.mode))); k++) { await p.mouse.click(180, 320); await p.waitForTimeout(500); }
  await p.keyboard.press('Escape'); await p.waitForTimeout(600);
  const PZ = '#pz:not(.hide) button, #pz:not(.hide) .set';
  const q = (await vis(PZ)).length; console.log('пауза: кнопок ' + q); if (!q) bad('пауза не открылась по Esc');
  for (let j = 0; j < q; j++) { const tj = await label(PZ, j); if (/меню|menu|вийти|выход|exit/i.test(tj)) continue;
    const a = await snap(); await click(PZ, j); await p.waitForTimeout(400); const z = await snap(); if (a === z) bad(`пауза «${tj}» — ничего не произошло`); else console.log('  ✓ ' + tj);
    if (!(await p.evaluate(() => !!document.querySelector('#pz:not(.hide)')))) { await p.keyboard.press('Escape'); await p.waitForTimeout(500); } }
  if (errs.length) { for (const e of errs) bad('ошибка JS: ' + e); }
  console.log(fail ? `НАЙДЕНО ПРОБЛЕМ: ${fail}` : 'кнопки: всё работает');
  await b.close(); process.exit(fail ? 1 : 0);
})();
