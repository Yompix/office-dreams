// руки в разговоре: node arm_probe.js "shot:who:ИМЯ,..." [сек]  — рывки кистей (ускорение), выверт предплечья, локоть наизнанку
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const list = (process.argv[2] || 'guard:smenshik:СМЕНЩИК').split(','), dur = +(process.argv[3] || 24);
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 200, height: 200 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script><canvas id="c" width="200" height="200"></canvas></body></html>`;
  await p.route(/\/web\/_pr\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/web/_pr.html?gfx=retro&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); });
  for (const it of list) { const [shot, who, name] = it.split(':');
    const r = await p.evaluate(([shot, who, name, dur]) => { const T = THREE, A = window.__dbgA, g = document.getElementById('c').getContext('2d');
      const C = A[who], dt = 1 / 30, v = new T.Vector3(), q = new T.Quaternion(), q2 = new T.Quaternion(), rec = [];
      const speak = t => (t % 7) < 4.2;   // 4,2 с говорит, 2,8 с молчит
      let first = true;
      for (let t = 0; t < dur; t += dt) { const on = speak(t), lvl = on ? (.25 + .35 * Math.abs(Math.sin(t * 11)) * (Math.sin(t * 2.3) > -.6 ? 1 : 0)) : 0;
        World.draw(g, 200, 200, shot, { t, T: 100 + t, settle: first, props: {}, talk: on ? { who: name, v: lvl } : null }); first = false;
        C.root.updateMatrixWorld(true); const f = { t, on };
        for (const s of ['l', 'r']) { const U = C.bone['upperarm_' + s], Lo = C.bone['lowerarm_' + s], H = C.bone['hand_' + s];
          const a = U.getWorldPosition(new T.Vector3()), b = Lo.getWorldPosition(new T.Vector3()), c = H.getWorldPosition(new T.Vector3());
          const ab = b.clone().sub(a).normalize(), bc = c.clone().sub(b).normalize();
          f['h' + s] = c.toArray(); f['e' + s] = b.toArray(); f['bend' + s] = Math.acos(Math.max(-1, Math.min(1, ab.dot(bc)))) * 57.3;
          // выверт кисти относительно предплечья вокруг оси кости (локальный поворот hand → twist вокруг оси на кисть)
          const lq = H.quaternion, ax = H.position.clone().normalize(), pr = new T.Vector3(lq.x, lq.y, lq.z).projectOnVector(ax), tw = new T.Quaternion(pr.x, pr.y, pr.z, lq.w).normalize();
          f['tw' + s] = 2 * Math.acos(Math.min(1, Math.abs(tw.w))) * 57.3;
          const lq2 = Lo.quaternion, ax2 = Lo.position.clone().normalize(), pr2 = new T.Vector3(lq2.x, lq2.y, lq2.z).projectOnVector(ax2), tw2 = new T.Quaternion(pr2.x, pr2.y, pr2.z, lq2.w).normalize();
          f['tl' + s] = 2 * Math.acos(Math.min(1, Math.abs(tw2.w))) * 57.3; }
        rec.push(f); }
      const out = [], d = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
      for (const s of ['l', 'r']) { let vmax = 0, amax = 0, at = 0, spikes = [], twm = 0, tlm = 0, bmin = 999, bmax = 0, pv = 0;
        for (let i = 1; i < rec.length; i++) { const sp = d(rec[i]['h' + s], rec[i - 1]['h' + s]) / dt, ac = Math.abs(sp - pv) / dt; pv = sp;
          if (sp > vmax) vmax = sp; if (i > 2 && ac > amax) { amax = ac; at = rec[i].t; } if (i > 2 && ac > 25) spikes.push(rec[i].t.toFixed(2));
          twm = Math.max(twm, rec[i]['tw' + s]); tlm = Math.max(tlm, rec[i]['tl' + s]); bmin = Math.min(bmin, rec[i]['bend' + s]); bmax = Math.max(bmax, rec[i]['bend' + s]); }
        out.push(`  рука ${s}: скорость до ${vmax.toFixed(2)} м/с, ускорение до ${amax.toFixed(0)} м/с² (t=${at.toFixed(2)}), рывков >25: ${spikes.length} [${spikes.slice(0, 12).join(' ')}], выверт кисти до ${twm.toFixed(0)}°, предплечья до ${tlm.toFixed(0)}°, сгиб локтя ${bmin.toFixed(0)}–${bmax.toFixed(0)}°`); }
      return out; }, [shot, who, name, dur]);
    console.log(shot + ' / ' + who + '\n' + r.join('\n')); }
  await br.close();
})();
