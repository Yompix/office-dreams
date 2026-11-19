// «Твёрдые руки и ноги»: где solidLimbs (scenes.js) поправил руку или ступню — по всем кадрам игры, и проникновения до/после.
//   node tools/anim/limb_check.js [regexp кадров] [секунды через запятую]      (сервер не нужен)
// По каждому кадру: журнал правок (кто, какая рука/ступни, в каком предмете, на сколько мм) и сумма World.clips() без правки и с ней.
const { chromium } = require('/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const ROOT = path.resolve(__dirname, '../..'), [re = '.', times = '.4,1.5,3'] = process.argv.slice(2), W = 320, H = 180;
const MIME = { '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.wasm': 'application/wasm', '.ktx2': 'image/ktx2' };
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } }); p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route('http://127.0.0.1:8771/**', r => { const u = new URL(r.request().url()); if (u.pathname === '/web/_g.html') return r.fulfill({ contentType: 'text/html', body: HTML });
    const f = path.join(ROOT, decodeURIComponent(u.pathname)); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return r.fulfill({ status: 404, body: '' });
    r.fulfill({ body: fs.readFileSync(f), contentType: MIME[path.extname(f)] || 'application/octet-stream' }); });
  await p.goto('http://127.0.0.1:8771/web/_g.html?gfx=retro&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); });
  const ids = await p.evaluate(re => Object.keys(World.shots).filter(k => new RegExp(re).test(k) && k[0] !== '_'), re);
  const PR = { clock: '21:30', jacket: 1, badge: 1, book: 1 }; let n = 0, s0 = 0, s1 = 0; const acts = {};
  for (const id of ids) for (const t of times.split(',').map(Number)) {
    const r = await p.evaluate(([id, t, PR, W, H]) => { const g = document.getElementById('c').getContext('2d'), out = {};
      const sum = () => World.clips().filter(x => x.n >= 4 && x.d >= .03 && (x.prop !== 'пол' || x.d >= .05)).map(x => [x.k, x.prop, Math.round(x.d * 100), x.n]);
      try { for (const on of [0, 1]) { window.__LIMBS = on; window.__LIMBLOG = [];
        for (let s = 0; s <= 8; s++) { if (s === 8) window.__LIMBLOG = []; World.draw(g, W, H, id, { t: Math.max(0, t - .5 + s / 16), T: 100 + t, settle: s === 0, props: PR }); }
        out[on] = { clips: sum(), log: window.__LIMBLOG }; } } catch (e) { return { err: String(e).slice(0, 160) }; } return out; }, [id, t, PR, W, H]);
    if (r.err) { console.log('ошибка', id, r.err); continue; } n++;
    const c0 = r[0].clips, c1 = r[1].clips; s0 += c0.length; s1 += c1.length;
    if (r[1].log.length || c0.length || c1.length) console.log(`${id}@${t}  правки: ${r[1].log.map(l => l.slice(1).join(' ')).join('; ') || '—'}   | проникновения было: ${c0.map(x => x.join(' ')).join('; ') || '—'}   | стало: ${c1.map(x => x.join(' ')).join('; ') || '—'}`);
    for (const l of r[1].log) acts[id] = Math.max(acts[id] || 0, l[4]);
  }
  console.log(`\nснимков ${n}; проникновений (≥3 см) было ${s0}, стало ${s1}; кадров с правками: ${Object.keys(acts).length}`);
  console.log('кадры с правками (мм): ' + Object.entries(acts).sort((a, b) => b[1] - a[1]).map(([k, v]) => k + ' ' + v).join(', '));
  await br.close();
})();
