// Нагрузка кадра: вызовы отрисовки и треугольники за один кадр (со всеми проходами теней и постобработки).
//   node tools/anim/perf_calls.js кадр [кадр …]      ROOT=папка игры (по умолчанию эта; можно копию прошлой версии — сравнить «до/после»), GFX=cine|retro
const { chromium } = require('/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const ROOT = path.resolve(process.env.ROOT || path.join(__dirname, '../..')), SHOTS = process.argv.slice(2), GFX = process.env.GFX || 'cine', W = 640, H = 360;
const MIME = { '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.wasm': 'application/wasm', '.ktx2': 'image/ktx2' };
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } }); p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route('http://127.0.0.1:8771/**', r => { const u = new URL(r.request().url()); if (u.pathname === '/web/_g.html') return r.fulfill({ contentType: 'text/html', body: HTML });
    const f = path.join(ROOT, decodeURIComponent(u.pathname)); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return r.fulfill({ status: 404, body: '' });
    r.fulfill({ body: fs.readFileSync(f), contentType: MIME[path.extname(f)] || 'application/octet-stream' }); });
  await p.goto('http://127.0.0.1:8771/web/_g.html?gfx=' + GFX + '&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); });
  for (const id of SHOTS) { const r = await p.evaluate(([id, W, H]) => { try { const g = document.getElementById('c').getContext('2d'), R = window.__W3core.R, PR = { clock: '21:30', jacket: 1, badge: 1, book: 1 };
      for (let s = 0; s < 4; s++) World.draw(g, W, H, id, { t: 1 + s / 15, T: 101 + s / 15, settle: s === 0, props: PR });
      R.info.autoReset = false; R.info.reset(); World.draw(g, W, H, id, { t: 1.3, T: 101.3, props: PR }); const o = { calls: R.info.render.calls, tris: R.info.render.triangles, geo: R.info.memory.geometries }; R.info.autoReset = true; return o; } catch (e) { return { err: String(e).slice(0, 120) }; } }, [id, W, H]);
    console.log(id.padEnd(14), JSON.stringify(r)); }
  await br.close();
})();
