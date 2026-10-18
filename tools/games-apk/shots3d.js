// Снимки всех 3D-кадров: скопируй preview.html в public/games/__preview.html, подними сервер (python3 -m http.server 8766 в public/games) и запусти: node tools/games-apk/shots3d.js http://localhost:8766/__preview.html /tmp/shots
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const [url, O, only] = process.argv.slice(2);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 540, height: 960 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e))); p.on('console', m => (m.type() === 'error' || m.type() === 'warning') && errs.push(m.text().slice(0, 200)));
  await p.goto(url); await p.waitForTimeout(500);
  const ids = only ? only.split(',') : await p.evaluate(() => Object.keys(World.shots));
  fs.mkdirSync(O, { recursive: true });
  for (const id of ids) {
    const t0 = Date.now();
    // прогрев: несколько кадров, чтобы позы встали
    for (let k = 0; k < 12; k++) await p.evaluate(([id, t]) => shot(id, t), [id, 1 + k * .12]);
    const d = await p.evaluate(([id]) => shot(id, 2.5), [id]);
    fs.writeFileSync(`${O}/${id}.png`, Buffer.from(d.split(',')[1], 'base64'));
    process.stdout.write(id + ' ' + (Date.now() - t0) + 'ms; ');
  }
  console.log('\nerrors:', errs.slice(0, 10));
  await b.close();
})();
