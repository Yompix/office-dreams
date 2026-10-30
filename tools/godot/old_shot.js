// Снимки кадров старой игры (three.js) по имени кадра — для сравнения «было / стало» с Godot-версией.
// node tools/godot/old_shot.js ПАПКА кадр[:секунда[:cctv]][,кадр…] [cine|ultra]   (сервер: cd public/games && python3 -m http.server 8771)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const [out, list, gfx = 'ultra'] = process.argv.slice(2); fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=${gfx}&lang=ru&rs=1`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  for (const item of list.split(',')) {
    const [id, t = '1.5', cc = ''] = item.split(':');   // кадр:секунда:cctv — с видом камеры наблюдения
    const png = await p.evaluate(([id, t, cc]) => { const c = document.createElement('canvas'); c.width = 960; c.height = 540; const g = c.getContext('2d');
      for (let i = 0; i < 4; i++) window.World.draw(g, 960, 540, id, { t: +t, T: +t, cx: .5, cy: .5, z: 1, sx: 0, sy: 0, cctv: cc === 'cctv' ? 1 : 0 });
      return c.toDataURL('image/png').split(',')[1]; }, [id, t, cc]);
    const f = path.join(out, `old_${id}.png`); fs.writeFileSync(f, Buffer.from(png, 'base64')); console.log(f);
  }
  console.log(errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
