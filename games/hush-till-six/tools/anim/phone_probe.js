// телефон у уха: node phone_probe.js "shot:who,..."  — где локоть относительно плеча, кисть относительно головы (в осях персонажа)
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const list = (process.argv[2] || 'booth_call:temych').split(',');
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 200, height: 200 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="200" height="200"></canvas></body></html>`;
  await p.route(/\/web\/_pr\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/web/_pr.html?gfx=retro&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); });
  for (const it of list) { const [shot, who] = it.split(':');
    const r = await p.evaluate(([shot, who]) => { const T = THREE, C = window.__dbgA[who], g = document.getElementById('c').getContext('2d'), out = [];
      for (let t = 0; t < 3; t += 1 / 30) World.draw(g, 200, 200, shot, { t, T: 100 + t, settle: t === 0, props: {} });
      C.root.updateMatrixWorld(true); const inv = new T.Matrix4().copy(C.root.matrixWorld).invert();
      const L = b => C.bone[b].getWorldPosition(new T.Vector3()).applyMatrix4(inv), f = v => '[' + v.toArray().map(x => (x * 100).toFixed(0)).join(', ') + ']';
      const head = L('Head'), sc = C.root.getWorldScale(new T.Vector3()).x;
      out.push('  масштаб ' + sc.toFixed(2) + ', голова ' + f(head) + ' см (x вбок, y вверх, z вперёд, оси персонажа)');
      for (const s of ['l', 'r']) { const a = L('upperarm_' + s), b = L('lowerarm_' + s), c = L('hand_' + s);
        const ab = b.clone().sub(a), bc = c.clone().sub(b), bend = ab.clone().normalize().angleTo(bc.clone().normalize()) * 57.3;
        const up = Math.asin(ab.clone().normalize().y) * 57.3;
        out.push(`  рука ${s}: плечо ${f(a)} локоть ${f(b)} кисть ${f(c)}; локоть ${up > 0 ? 'ВЫШЕ' : 'ниже'} плеча на ${Math.abs(ab.y * 100).toFixed(0)} см (плечо поднято на ${(up + 90).toFixed(0)}° от «вниз»), сгиб ${bend.toFixed(0)}°, кисть от головы ${(c.distanceTo(head) * 100).toFixed(0)} см`); }
      return out; }, [shot, who]);
    console.log(shot + ' / ' + who + '\n' + r.join('\n')); }
  await br.close();
})();
