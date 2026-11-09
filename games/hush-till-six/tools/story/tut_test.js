// Обучение с диспетчером Верой: проходит все шаги как игрок (камера 03 → «Записать» → «Лифт» → пульт → журнал), снимает кадры.
//   LANG2=ja node tools/story/tut_test.js ПАПКА   (LANG2 — язык игры, по умолчанию русский)
// После каждого объяснения без действия Вера ждёт «Продолжить» (#tutGo) — тест жмёт его, как игрок, и проверяет, что субтитр не исчез до нажатия.
const { chromium } = require('/opt/node-tools/node_modules/playwright');
(async () => {
  const out = process.argv[2] || '/tmp/tut'; require('fs').mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 }, locale: 'ru-RU' });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.goto('http://127.0.0.1:' + (process.env.PORT || 8771) + '/nightshift/index.html?gfx=retro' + (process.env.LANG2 ? '&lang=' + process.env.LANG2 : ''));
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
  await p.evaluate(() => { localStorage.removeItem('ns1_tutDone'); window.__ns.startScene('sh_S1'); });
  const shot = n => p.screenshot({ path: `${out}/${n}.png` });
  let goN = 0, goSub = 0; const goT = setInterval(async () => { try {   // «Продолжить»: субтитр Веры стоит до нажатия — жмём через 1,5 с
    const r = await p.evaluate(() => { const b = document.getElementById('tutGo'), d = document.getElementById('dlg'); if (!b || b.classList.contains('hide')) return null; return { sub: !!d && !d.classList.contains('hide') && d.textContent.trim().length > 0 }; });
    if (r) { await p.waitForTimeout(1500); if (r.sub) goSub++; goN++; await p.click('#tutGo').catch(() => {}); } } catch {} }, 700);
  const waitRing = sel => p.waitForFunction(s => { const r = document.getElementById('tutRing'); return r && !r.classList.contains('hide') && (!s || document.querySelector(s)); }, sel, { timeout: 180000 });
  await p.waitForSelector('#tutCard:not(.hide)', { timeout: 60000 }); await p.waitForTimeout(1500); await shot('1_vera');
  await waitRing('button[data-c="03"]'); await shot('2_cam'); await p.click('button[data-c="03"]');
  await waitRing('#shLog'); await shot('3_lift13'); await p.click('#shLog'); await p.waitForSelector('.sh-grid button'); await shot('4_log');
  await p.click('.sh-grid button[data-i="2"]');   // «Лифт»
  await p.waitForFunction(() => document.getElementById('tutDo').textContent === window.tr('MIC слева вверху: столбики прыгают — звук'), null, { timeout: 180000 }); await p.waitForTimeout(800); await shot('5_mic');
  await p.waitForFunction(() => document.getElementById('tutDo').textContent === window.tr('Открой «Пульт»'), null, { timeout: 180000 }); await p.click('#shPanel'); await p.waitForSelector('#pDoor'); await p.waitForTimeout(500); await shot('6_panel'); await p.click('#shX');
  await p.waitForFunction(() => document.getElementById('tutDo').textContent === window.tr('Открой «Журнал»'), null, { timeout: 180000 }); await p.click('#shBook'); await p.waitForSelector('.sh-tabs'); await p.waitForTimeout(500); await p.click('#shX');
  await p.waitForFunction(() => document.getElementById('tutCard').classList.contains('hide'), null, { timeout: 180000 }); await p.waitForTimeout(800); await shot('7_end');
  console.log('обучение пройдено:', await p.evaluate(() => localStorage.getItem('ns1_tutDone')), '| время идёт:', await p.evaluate(() => { const a = window.__ns.shf.M; return new Promise(r => setTimeout(() => r(window.__ns.shf.M > a), 3000)); }));
  clearInterval(goT); console.log(`«Продолжить» нажато ${goN} раз, субтитр стоял до нажатия: ${goSub} из ${goN}`);
  await p.click('#shHelp'); await p.waitForSelector('#tutAgain'); await shot('8_help');
  console.log(errs.length ? 'ОШИБКИ:\n' + errs.join('\n') : 'ошибок нет'); await b.close();
})();
