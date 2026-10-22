// Проверка смены в настоящей игре: запускает час, ускоряет время, открывает пульт/журнал/запись, снимает кадры, ловит ошибки.
//   node tools/story/shift_test.js S1 [скорость=30] [папка для снимков]
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const seg = process.argv[2] || 'S1', spd = +(process.argv[3] || 30), out = process.argv[4];
  if (out) fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 }, locale: 'ru-RU' });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300))); p.on('console', m => m.type() === 'error' && errs.push(m.text().slice(0, 300)));
  await p.goto('http://127.0.0.1:8771/nightshift/index.html?gfx=cine');
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
  await p.evaluate(s => { window.__ns.startScene('sh_' + s); }, seg);
  await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 30000 });
  await p.evaluate(v => window.__ns.shSpeed(v), spd);
  const snap = async n => { if (out) await p.screenshot({ path: `${out}/${seg}_${n}.png`, timeout: 120000 }).catch(() => {}); };
  const st = () => p.evaluate(() => { const f = window.__ns.shf; return f ? { M: f.M.toFixed(1), act: f.act.map(a => a.id), miss: f.miss, cam: f.cam, task: f.task && f.task.text } : { mode: window.__ns.mode, scene: window.__ns.scene }; });
  await snap('start'); console.log('старт', JSON.stringify(await st()));
  const cams = ['01', '10', '03', '07', '04', 'm2', '02'];
  for (let i = 0; i < 40; i++) {
    await p.waitForTimeout(1500);
    const s = await st(); if (!s.M) { console.log('конец смены →', JSON.stringify(s)); break; }
    console.log(JSON.stringify(s));
    if (i % 3 === 0) await p.evaluate(c => window.__ns.shiftCam(c), cams[(i / 3) % cams.length]);
    if (i === 4) { await p.evaluate(() => window.__ns.shiftPanel()); await snap('panel'); await p.evaluate(() => document.getElementById('shX') && document.getElementById('shX').click()); }
    if (i === 6) { await p.evaluate(() => window.__ns.shiftBook()); await snap('book'); await p.evaluate(() => document.getElementById('shX') && document.getElementById('shX').click()); }
    if (i === 8) { await p.evaluate(() => window.__ns.shiftLog()); await snap('log'); await p.evaluate(() => document.getElementById('shX') && document.getElementById('shX').click()); }
    if (i % 5 === 2) await snap('t' + i);
  }
  console.log(errs.length ? 'ОШИБКИ:\n' + errs.slice(0, 6).join('\n') : 'ошибок нет');
  await b.close();
})();
