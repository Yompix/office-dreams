const { chromium } = require('/opt/node-tools/node_modules/playwright');
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 200, height: 200 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="200" height="200"></canvas></body></html>`;
  await p.route(/\/web\/_pr\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/web/_pr.html?gfx=retro&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); });
  const r = await p.evaluate(() => { const T = THREE, A = window.__dbgA, g = document.getElementById('c').getContext('2d'), out = [];
    const C = A.mihalych, inv = new T.Matrix4();
    const L = n => C.bone[n].getWorldPosition(new T.Vector3()).applyMatrix4(inv);
    const run = (shot, dur, talkFn, props, label) => { let first = true, worst = 0, wt = 0, wpart = '', bad = 0, n = 0;
      for (let t = 0; t < dur; t += 1 / 30) { const tk = talkFn(t);
        World.draw(g, 200, 200, shot, { t, T: 100 + t, settle: first, props, talk: tk ? { who: 'ФРЭНК', v: .3 + .3 * Math.abs(Math.sin(t * 11)) } : null }); first = false;
        C.root.updateMatrixWorld(true); inv.copy(C.root.matrixWorld).invert();
        const sp = ['pelvis', 'spine_01', 'spine_02', 'spine_03', 'neck_01'].map(L), el = L('lowerarm_r'), ha = L('hand_r'), fi = L('middle_02_r');
        const pts = []; for (let k = 0; k <= 6; k++) pts.push([el.clone().lerp(ha, k / 6), 'предплечье']); for (let k = 1; k <= 3; k++) pts.push([ha.clone().lerp(fi, k / 3), 'кисть']);
        let fw = 0; n++;
        for (const [q, part] of pts) { if (q.y < sp[0].y - .05 || q.y > sp[4].y) continue;
          let a = sp[0], b = sp[1]; for (let k = 0; k < 4; k++) if (q.y >= sp[k].y) { a = sp[k]; b = sp[k + 1]; }
          const u = Math.max(0, Math.min(1, (q.y - a.y) / ((b.y - a.y) || 1))), cx = a.x + (b.x - a.x) * u, cz = a.z + (b.z - a.z) * u + .02;
          const top = a === sp[3] ? u : 0, rx = .165 - .095 * top, rz = .115 - .05 * top, d = Math.hypot((q.x - cx) / rx, (q.z - cz) / rz), pen = (1 - d) * rz;   // выше груди туловище сужается к шее   // туловище — эллипс 33×23 см вокруг позвоночника
          if (pen > fw) fw = pen; if (pen > worst) { worst = pen; wt = t; wpart = part; } }
        if (fw > .02) bad++; }
      out.push(`${label}: глубже 2 см в туловище — ${bad} кадров из ${n}; самое глубокое ${(worst * 100).toFixed(1)} см (${wpart}, t=${wt.toFixed(2)})`); };
    run('x_smoke', 10, () => false, {}, 'Закуривает');
    run('x_smoke', 14, t => t > .5 && t < 8, { cig: 1 }, 'Говорит, потом затяжка');
    run('x_two', 12, () => false, { cig: 1 }, 'Общий план');
    return out; });
  console.log(r.join('\n')); await br.close();
})();
