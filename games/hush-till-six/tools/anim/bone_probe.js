// кости в мире: node probe.js SHOT "t1,t2" "temych,monster" "Head,neck_01,pelvis,hand_l,hand_r"
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [shot, ts = '1', who = 'temych,monster', bones = 'Head,neck_01,spine_03,pelvis,hand_l,hand_r,foot_l,foot_r'] = process.argv.slice(2);
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 200, height: 200 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="200" height="200"></canvas></body></html>`;
  await p.route(/\/nightshift\/_pr\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8784}/nightshift/_pr.html?gfx=retro&lang=en`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); });
  const r = await p.evaluate(([shot, ts, who, bones]) => { const T = THREE, A = window.__dbgA, c = document.getElementById('c'), g = c.getContext('2d'), out = [];
    let t = 0, first = true; const v = new T.Vector3();
    for (const tw of ts.split(',').map(Number)) { while (t < tw - 1e-6) { World.draw(g, 200, 200, shot, { t, T: 100 + t, settle: first, props: {} }); first = false; t = Math.min(tw, t + 1 / 30); }
      World.draw(g, 200, 200, shot, { t: tw, T: 100 + tw, settle: first, props: {} }); first = false;
      out.push('t=' + tw);
      for (const k of who.split(',')) { const C = A[k]; if (!C || !C.root.parent) { out.push('  ' + k + ' нет'); continue; } C.root.updateMatrixWorld(true);
        out.push('  ' + k + ' root ' + C.root.position.toArray().map(x => x.toFixed(3)).join(',') + ' ry ' + C.root.rotation.y.toFixed(2));
        for (const b of bones.split(',')) { const B = C.bone[b]; if (!B) continue; B.getWorldPosition(v); out.push('    ' + b.padEnd(12) + v.toArray().map(x => x.toFixed(3)).join(', ')); } }
      const cl = World.clips(.02).map(x => x.k + ' в ' + x.prop + ' ' + x.d + ' м ' + x.n + ' в.'); if (cl.length) out.push('  clips: ' + cl.join(' | ')); }
    return out; }, [shot, ts, who, bones]);
  console.log(r.join('\n')); await br.close();
})();
