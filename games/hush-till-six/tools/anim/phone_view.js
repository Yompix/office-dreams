// крупно кисть с телефоном с трёх сторон: node phone_view.js OUT.png [shot] [who] [l|r]
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const out = process.argv[2] || 'phone_view.png', shot = process.argv[3] || 'booth_call', who = process.argv[4] || 'temych', sd = process.argv[5] || 'l';
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 1500, height: 520 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#222"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="a" width="500" height="500"></canvas><canvas id="b" width="500" height="500"></canvas><canvas id="c" width="500" height="500"></canvas></body></html>`;
  await p.route(/\/web\/_pv\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/web/_pv.html?gfx=retro&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); });
  await p.evaluate(([shot, who, sd]) => { const T = THREE, C = window.__dbgA[who], g0 = document.getElementById('a').getContext('2d');
    for (let t = 0; t < 2; t += 1 / 30) World.draw(g0, 500, 500, shot, { t, T: 100 + t, settle: t === 0, props: {} });
    C.root.updateMatrixWorld(true); const h = C.bone.Head.getWorldPosition(new T.Vector3()), m = C.root.matrixWorld, ax = i => new T.Vector3().setFromMatrixColumn(m, i).normalize();
    const X = ax(0), Z = ax(2), s = sd === 'l' ? 1 : -1, c = h.clone().addScaledVector(X, s * .07).add(new T.Vector3(0, -.02, 0));
    const views = [X.clone().multiplyScalar(s), X.clone().multiplyScalar(s * .75).addScaledVector(Z, .65), X.clone().multiplyScalar(s * .15).addScaledVector(Z, 1)];
    ['a', 'b', 'c'].forEach((id, i) => { const pos = c.clone().addScaledVector(views[i].normalize(), .85); World.dbgShot('_pv' + i, shot, { pos: pos.toArray(), look: c.toArray(), fov: 34, cctv: 0 });
      const g = document.getElementById(id).getContext('2d'); for (let k = 0; k < 3; k++) World.draw(g, 500, 500, '_pv' + i, { t: 2, T: 102, settle: k === 0, props: {} }); }); }, [shot, who, sd]);
  await p.screenshot({ path: out }); await br.close(); console.log('готово', out);
})();
