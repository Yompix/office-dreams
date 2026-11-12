// где бывает швабра Гаса: node mop_probe.js shot '{"sh":{"gus":1}}' [T=55] — колодка и верх черенка против стен коридора (x=±1,3), ведра, таблички
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const [shot = 'cam07', props = '{"sh":{"gus":1}}', T = '55'] = process.argv.slice(2);
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 640, height: 360 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><canvas id="c" width="640" height="360"></canvas></body></html>`;
  await p.route(/\/web\/_mp\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/web/_mp.html?gfx=retro&lang=ru');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  const rows = await p.evaluate(async ([shot, props, T]) => { await window.__W3chars.load(); World.init(); World.setLand && World.setLand(true);
    const W3 = window.__W3chars, o0 = W3.propAim, rows = []; let cur = null;
    W3.propAim = function (n, sc, pos, dir, yaw, fl) { if (n === 'mop') cur = { pos: pos.slice(), dir: dir.slice(), yaw: yaw || 0 }; return o0.apply(this, arguments); };
    const g = document.getElementById('c').getContext('2d');
    for (let t = 0; t <= T; t += .1) { cur = null; World.draw(g, 640, 360, shot, { t, T: 100 + t, settle: t === 0, props }); if (!cur) continue;
      const C = W3.actor && W3.actor('cleaner'); rows.push([t, cur.pos, cur.dir, cur.yaw]); }
    return rows; }, [shot, JSON.parse(props), +T]);
  let bad = 0; const st = { hx: [9, -9], hz: [9, -9], tx: [9, -9], tz: [9, -9], ty: [9, -9] }, mm = (a, v) => { a[0] = Math.min(a[0], v); a[1] = Math.max(a[1], v); };
  for (const [t, h, d, yaw] of rows) { const top = h.map((v, i) => v + d[i] * 1.47), ex = Math.abs(Math.cos(yaw)) * .18 + Math.abs(Math.sin(yaw)) * .1;
    mm(st.hx, h[0] - ex); mm(st.hx, h[0] + ex); mm(st.hz, h[2]); mm(st.tx, top[0]); mm(st.tz, top[2]); mm(st.ty, top[1]);
    const why = []; if (Math.abs(h[0]) + ex > 1.3) why.push('колодка в стене'); if (Math.abs(top[0]) > 1.28) why.push('черенок в стене'); if (top[1] > 2.55) why.push('потолок');
    for (const [nm, bx, bz, r] of [['ведро', .6, -10.35, .2], ['табличка', -.85, -6.55, .22]]) for (let s = 0; s <= 1.47; s += .05) { const q = h.map((v, i) => v + d[i] * s); if (q[1] < .45 && Math.hypot(q[0] - bx, q[2] - bz) < r) { why.push(nm); break; } }
    if (Math.hypot(h[0] - .6, h[2] + 10.35) < .2 + .2) why.push('колодка у ведра');
    if (why.length) { bad++; if (bad < 25) console.log(t.toFixed(1), why.join(', '), h.map(v => v.toFixed(2)).join(' '), 'yaw', yaw.toFixed(2)); } }
  console.log('кадров', rows.length, 'плохих', bad, JSON.stringify(st, (k, v) => typeof v === 'number' ? +v.toFixed(2) : v)); await br.close();
})();
