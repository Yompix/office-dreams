// Рисованные кодом картинки интерфейса старой игры → PNG для Godot (godot/nightshift/ui/).
// Сейчас: заставка телефона — детский рисунок Лили (engine.js drawing()), без подписей: подписи («ИТАН», «Я», «БУБЛИК») Godot пишет
// сам на языке игры детским шрифтом. node tools/godot/export_ui_art.js   (сервер: cd public/games && python3 -m http.server 8771)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, '../../godot/nightshift/ui');
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.route(/engine\.js/, async r => { const res = await r.fetch(); let s = await res.text();
    const n0 = s.length; s = s.replace(/word\('ИТАН'[^;]*;\s*word\('Я'[^;]*;\s*word\('БУБЛИК'[^;]*;/, '/* подписи — в Godot */');
    if (s.length === n0) throw new Error('подписи рисунка не найдены в engine.js');
    s = s.replace('function drawing() {', 'window.__drawing = drawing; function drawing() {'); await r.fulfill({ response: res, body: s }); });
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?lang=ru&rs=1`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  const png = await p.evaluate(() => window.__drawing().toDataURL('image/png').split(',')[1]);
  fs.writeFileSync(path.join(OUT, 'lily_drawing.png'), Buffer.from(png, 'base64'));
  console.log('ui/lily_drawing.png', errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
