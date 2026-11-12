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
  const r = await p.evaluate(() => { const T = THREE, g = document.getElementById('c').getContext('2d'), out = [], W = window.__W3chars, up = new T.Vector3();
    for (const shot of ['pro_two', 'pro_offer']) { let first = true, mx = 0, line = shot + ': наклон связки от отвеса по секундам —';
      for (let t = 0; t < 6; t += 1 / 30) { World.draw(g, 200, 200, shot, { t, T: 100 + t, settle: first, props: {}, talk: t > .5 && t < 4 ? { who: 'СМЕНЩИК', v: .4 } : null }); first = false;
        const pr = W.prop3('keys'); if (!pr.obj.visible) continue; up.set(0, -1, 0).applyQuaternion(pr.obj.quaternion); const a = Math.acos(Math.max(-1, Math.min(1, -up.y))) * 57.3; mx = Math.max(mx, a);
        if (Math.abs(t - Math.round(t)) < .017) line += ' ' + a.toFixed(0) + '°'; }
      out.push(line + ' · максимум ' + mx.toFixed(0) + '°'); }
    return out; });
  console.log(r.join('\n')); await br.close();
})();
