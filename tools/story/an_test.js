// Все странности смены за один запуск игры: включает каждую на её камере, снимает кадр, ловит ошибки страницы.
//   node tools/story/an_test.js ПАПКА [id1,id2,…]   (сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1)
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const out = process.argv[2] || '/tmp/an', only = process.argv[3] ? process.argv[3].split(',') : null;
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 }, locale: 'ru-RU' });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300))); p.on('console', m => m.type() === 'error' && errs.push(m.text().slice(0, 300)));
  await p.goto('http://127.0.0.1:8771/nightshift/index.html?gfx=cine');
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
  await p.evaluate(() => window.__ns.startScene('sh_S3'));
  await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 30000 });
  await p.evaluate(() => window.__ns.shSpeed(0));   // время стоит
  const ids = await p.evaluate(() => Object.keys(window.STORY.shift.an));
  for (const id of ids) {
    if (only && !only.includes(id)) continue;
    const cam = await p.evaluate(id => { const f = window.__ns.shf, def = window.STORY.shift.an[id]; f.act.length = 0; f.ev.forEach(e => (e.done = true));
      f.act.push({ id, def, m: f.M }); window.__ns.shiftCam(def.cam === '02' ? '01' : '02'); window.__ns.shiftCam(def.cam); return def.cam; }, id);
    await p.waitForTimeout(1300);
    await p.screenshot({ path: `${out}/${id}.png`, timeout: 120000 }).catch(() => {});
    console.log(id.padEnd(10), 'CAM', cam, errs.length ? 'ОШИБКИ: ' + errs.slice(-1)[0] : '');
  }
  console.log(errs.length ? 'ОШИБКИ:\n' + [...new Set(errs)].slice(0, 6).join('\n') : 'ошибок нет');
  await b.close();
})();
