// «Хват по касанию»: все кадры игры, где в руке предмет, — сколько пальцев касаются предмета и насколько пальцы в него входят,
// до (window.__NOWRAP = 1 — прежняя заготовленная поза пальцев) и после (пальцы смыкаются до касания, chars2.js wrapGrip).
//   node tools/anim/grip_view.js [regexp кадров] [секунды через запятую] [ПАПКА для крупных планов]
// Сервер не нужен (файлы отдаются с диска). С ПАПКОЙ — по каждому найденному хвату два крупных плана кисти (до/после), GFX=cine|retro.
const { chromium } = require('/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const ROOT = path.resolve(__dirname, '../..'), [re = '.', times = '.6,2', OUT] = process.argv.slice(2), GFX = process.env.GFX || (OUT ? 'cine' : 'retro'), W = 640, H = 480;
const MIME = { '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.wasm': 'application/wasm', '.ktx2': 'image/ktx2' };
(async () => {
  if (OUT) fs.mkdirSync(OUT, { recursive: true });
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } }); p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route('http://127.0.0.1:8771/**', r => { const u = new URL(r.request().url()); if (u.pathname === '/web/_g.html') return r.fulfill({ contentType: 'text/html', body: HTML });
    const f = path.join(ROOT, decodeURIComponent(u.pathname)); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return r.fulfill({ status: 404, body: '' });
    r.fulfill({ body: fs.readFileSync(f), contentType: MIME[path.extname(f)] || 'application/octet-stream' }); });
  await p.goto('http://127.0.0.1:8771/web/_g.html?gfx=' + GFX + '&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); });
  const ids = await p.evaluate(re => Object.keys(World.shots).filter(k => new RegExp(re).test(k) && k[0] !== '_'), re);
  const PR = { clock: '21:30', jacket: 1, badge: 1, book: 1 }, rows = [], seen = new Set();
  const run = (id, t, nowrap) => p.evaluate(([id, t, nowrap, PR, W, H]) => { try { window.__NOWRAP = nowrap; const g = document.getElementById('c').getContext('2d');
    for (const k in window.__W3chars.PROPS3) { const pr = window.__W3chars.PROPS3[k]; pr.C = null; pr.was = false; }
    for (const k in World.actors) World.actors[k].wrapC = null;
    for (let s = 0; s <= 8; s++) World.draw(g, W, H, id, { t: Math.max(0, t - .5 + s / 16), T: 100 + t, settle: s === 0, props: PR });
    const out = []; for (const k in window.__W3chars.PROPS3) { const i = window.__W3chars.gripInfo(k); if (i) out.push(Object.assign({ prop: k }, i)); } return out; } catch (e) { return [{ err: String(e).slice(0, 120) }]; } }, [id, t, nowrap, PR, W, H]);
  for (const id of ids) for (const t of times.split(',').map(Number)) {
    const a = await run(id, t, 1); if (!a.length || a[0].err) continue; const b = await run(id, t, 0);
    for (const x of a) { const y = b.find(q => q.prop === x.prop && q.sd === x.sd) || {}, key = [x.who, x.prop, x.g, x.sd].join('|');
      rows.push({ id, t, prop: x.prop, g: x.g, who: x.who, sd: x.sd, pen0: x.pen, touch0: x.touch, pen1: y.pen, touch1: y.touch, per0: x.per, per1: y.per });
      if (OUT && !seen.has(key)) { seen.add(key);
        for (const nw of [1, 0]) { const i = (await run(id, t, nw)).find(q => q.prop === x.prop && q.sd === x.sd); if (!i) continue;
          for (const [tag, k] of [['a', .9], ['b', -.9]]) { const h = i.hand, c = i.chest, dx = h[0] - c[0], dz = h[2] - c[2], L = Math.hypot(dx, dz) || 1, ox = dx / L, oz = dz / L, cs = Math.cos(k), sn = Math.sin(k), n = [0, 0, 0],
            pos = [h[0] + (ox * cs - oz * sn) * .34, h[1] + .1, h[2] + (ox * sn + oz * cs) * .34];   // снаружи от тела, с двух сторон от кисти
            await p.evaluate(([id, t, pos, look, PR, W, H]) => { World.dbgShot('_cam', id, { pos, look, fov: 26, dof: 0 }); const g = document.getElementById('c').getContext('2d');
              for (let s = 0; s <= 8; s++) World.draw(g, W, H, '_cam', { t: Math.max(0, t - .5 + s / 16), T: 100 + t, settle: s === 0, props: PR }); }, [id, t, pos, [h[0] + n[0] * .03, h[1] + n[1] * .03, h[2] + n[2] * .03], PR, W, H]);
            await p.waitForTimeout(250); await p.screenshot({ path: path.join(OUT, `${id}_${x.prop}_${x.sd}_${nw ? '0before' : '1after'}_${tag}.png`), timeout: 240000 }); } } } } }
  const fmt = r => `${r.id}@${r.t} ${r.who} ${r.prop}/${r.g} рука ${r.sd}: было — в предмете ${r.pen0} мм, касаются ${r.touch0}/5; стало — ${r.pen1} мм, ${r.touch1}/5   ${JSON.stringify(r.per1)}`;
  for (const r of rows) console.log(fmt(r));
  const n = rows.length, bad0 = rows.filter(r => r.pen0 > 3).length, bad1 = rows.filter(r => r.pen1 > 3).length, air0 = rows.filter(r => r.touch0 < 2).length, air1 = rows.filter(r => r.touch1 < 2).length;
  console.log(`\nхватов: ${n}; пальцы в предмете глубже 3 мм: было ${bad0}, стало ${bad1}; держит меньше чем двумя пальцами: было ${air0}, стало ${air1}`);
  await br.close();
})();
