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
    const C = A.mihalych, W = window.__W3chars, v = new T.Vector3(), m = new T.Vector3();
    const run = (shot, dur, talkFn, props, label) => { let first = true, line = label + ': ';
      for (let t = 0; t < dur; t += 1 / 30) { const tk = talkFn(t);
        World.draw(g, 200, 200, shot, { t, T: 100 + t, settle: first, props, talk: tk ? { who: 'ФРЭНК', v: .3 + .3 * Math.abs(Math.sin(t * 11)) } : null }); first = false;
        if (Math.abs(t * 2 - Math.round(t * 2)) < .017) { C.root.updateMatrixWorld(true); C.bone.index_02_r.getWorldPosition(v);
          const mo = C.mouthL ? C.bone.Head.localToWorld(C.mouthL.clone()) : C.bone.Head.getWorldPosition(m), pr = W.prop3('cig').obj, lt = W.prop3('lighter').obj;
          const dh = v.distanceTo(mo), dc = pr.visible ? pr.position.distanceTo(v) : -1, dm = pr.visible ? pr.position.distanceTo(mo) : -1;
          line += `\n  t=${t.toFixed(1)} ${tk ? 'говорит' : 'молчит '} рука→рот ${(dh * 100).toFixed(0)} см · сигарета ${pr.visible ? 'есть' : 'НЕТ '} (до руки ${(dc * 100).toFixed(0)}, до рта ${(dm * 100).toFixed(0)} см)${lt.visible ? ' · зажигалка' : ''}`; } }
      out.push(line); };
    run('x_smoke', 10, () => false, {}, 'Кадр «Фрэнк закурил» (молчит)');
    run('x_smoke', 14, t => t > .5 && t < 8, { cig: 1 }, 'Кадр, где Фрэнк говорит (0,5–8 с), потом молчит');
    run('x_two', 12, () => false, { cig: 1 }, 'Общий план, говорит Итан');
    return out; });
  console.log(r.join('\n')); await br.close();
})();
