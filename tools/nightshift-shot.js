// Night Shift: кадры игры в PNG для проверки.  node tools/nightshift-shot.js OUTDIR "shot@t1,t2;shot2@t" '{"z":1.5,"cy":.45}'
// Нужен сервер статики: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1
// LAND=1 — горизонтальный кадр 16:9; GFX=cine|retro — вид графики; FAST=1 — прогрев 1 с перед кадром (FAST=8 — 8 кадров);
// STEP=3 — считать каждый третий кадр; RS=.5 — внутреннее разрешение вдвое меньше (быстрые проверки)
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const LAND = process.env.LAND === '1', GFX = process.env.GFX || 'retro', CW = LAND ? 960 : 540, CH = LAND ? 540 : 960;
const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script>
<canvas id="c" width="${CW}" height="${CH}"></canvas></body></html>`;
(async () => {
  const [out, spec, opt] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: CW, height: CH } });
  const errs = []; p.on('pageerror', e => errs.push(String(e))); p.on('console', m => m.type() === 'error' && errs.push(m.text().slice(0, 300)));
  await p.route(/\/nightshift\/_shot\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/nightshift/_shot.html?gfx=' + GFX);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 90000 }); await p.evaluate(async (rs) => { if (rs) window.__W3core.V.rs = rs; await window.__W3chars.load(); World.init(); }, +process.env.RS || 0); if (LAND) await p.evaluate(() => World.setLand(true));
  const O = JSON.parse(opt || '{}');
  for (const part of spec.split(';')) {
    const [id, ts] = part.split('@'); const want = ts.split(',').map(Number); const last = Math.max(...want);
    const STEP = +process.env.STEP || 1;   // STEP=3 — считать каждый третий кадр (быстрее; позы v3 зависят только от времени кадра)
    for (let f = process.env.FAST ? Math.max(0, Math.round(Math.min(...want) * 30) - (+process.env.FAST > 1 ? +process.env.FAST : 30)) : 0; f <= Math.round(last * 30); f++) {
      const t = f / 30, need = want.some(w => Math.abs(w - t) < .017);
      if (!need && f % STEP) continue;
      const url = await p.evaluate(([id, t, need, O]) => { const c = document.getElementById('c'), g = c.getContext('2d');
        World.draw(g, c.width, c.height, id, Object.assign({ t, T: t + 100, cx: .5, cy: .5, z: 1 }, O)); return need ? c.toDataURL('image/png') : ''; }, [id, t, need, O]);
      if (need) fs.writeFileSync(`${out}/${id}_${t.toFixed(2)}.png`, Buffer.from(url.split(',')[1], 'base64'));
    }
  }
  if (errs.length) console.log('ERR', errs.slice(0, 5).join('\n'));
  await b.close();
})();
