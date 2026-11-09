// быстрый просмотр кадра смерти в выбранные секунды (каждый снимок — 8 кадров разгона, без прогона всей сцены):
// node gq.js "gore@hush_srv:face" "0.2,1.0,1.6" OUT [W H] [gfx]   или несколько: node gq.js "gore@a:face@0.2,1;gore@b:eyes@1.5" - OUT
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const [sid, ts, out = 'gq', Wd = '270', Hd = '480', gfx = 'cine'] = process.argv.slice(2), W = +Wd, H = +Hd;
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text().slice(0, 300)); });
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script>
<canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route(/\/nightshift\/_gq\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8784}/nightshift/_gq.html?gfx=${gfx}&lang=en`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async (land) => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (land) World.setLand(true); }, W > H);
  fs.mkdirSync(out, { recursive: true });
  const jobs = ts === '-' ? sid.split(';').map(x => { const i = x.lastIndexOf('@'); return [x.slice(0, i), x.slice(i + 1)]; }) : [[sid, ts]];
  for (const [sid, ts] of jobs) for (const tw of ts.split(',').map(Number)) {
    const u = await p.evaluate(([sid, tw]) => { const c = document.getElementById('c'), g = c.getContext('2d');
      const D = (tt, st) => World.draw(g, c.width, c.height, sid, { t: tt, T: 100 + tt, cx: .5, cy: .5, z: 1, sx: 0, sy: 0, settle: st, cctv: 0, props: { clock: '03:00', jacket: 1, badge: 1, book: 0 } });
      const t0 = Math.max(0, tw - 8 / 30); D(t0, true); for (let t = t0 + 1 / 30; t < tw - 1e-6; t += 1 / 30) D(t, false); D(tw, false);
      return c.toDataURL('image/jpeg', .88); }, [sid, tw]);
    fs.writeFileSync(path.join(out, sid.replace(/[@:|]/g, '_') + '_' + tw.toFixed(2) + '.jpg'), Buffer.from(u.split(',')[1], 'base64'));
  }
  console.log('готово', out);
  await br.close();
})();
