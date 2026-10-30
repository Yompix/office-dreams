// Сервер: cd public/games && python3 -m http.server 8784 --bind 127.0.0.1 (PORT=… — другой).
// финал смерти: проникновения в стены/предметы (World.clips) и под пол (вершины ниже −2 см) по шагам — node gore_check.js over_k4,… [шаг]
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const [ids, st = '.1'] = process.argv.slice(2);
const ENG = fs.readFileSync(require('path').join(__dirname, '../../public/games/nightshift/engine.js'), 'utf8');
const GK = eval('(' + /const GORE_KIND = (\{[\s\S]*?\});/.exec(ENG)[1] + ')'), GB = eval('(' + /const GORE_BASE = (\{[\s\S]*?\});/.exec(ENG)[1] + ')');
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 180, height: 320 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script>
<canvas id="c" width="180" height="320"></canvas></body></html>`;
  await p.route(/\/nightshift\/_gc\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8784}/nightshift/_gc.html?gfx=retro&lang=en`);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); });
  for (const id of ids.split(',')) {
    const gk = GK[id] || GK[id.replace(/_(srv|hide)$/, '')];
    const r = await p.evaluate(([id, gk, GBid, st]) => {
      const T = THREE, A = window.__dbgA, S = window.STORY.scenes[id], bt = (S.beats || []).find(b => b.black != null);
      const sid = 'gore@' + (GBid || bt.img) + ':' + gk, TL = World.goreTL(sid), d = TL ? TL.d : 3.4;
      const c = document.getElementById('c'), g = c.getContext('2d'), props = Object.assign({ clock: '03:00', jacket: 1, badge: 1, book: 0 }, S.props || {}, bt.props || {});
      const v = new T.Vector3(), rows = []; let first = true, t = 0;
      const shotAt = tt => sid + (!TL && gk !== 'drag' && tt > .55 && tt < 2.15 ? '|c' : '');
      const D = tt => World.draw(g, c.width, c.height, shotAt(tt), { t: tt, T: 100 + tt, cx: .5, cy: .5, z: 1, sx: 0, sy: 0, settle: first, cctv: 0, props });
      for (let tw = 0; tw <= d + 1e-6; tw += st) { while (t < tw - 1e-6) { D(t); first = false; t = Math.min(tw, t + 1 / 30); } D(tw); first = false;
        const cl = World.clips(.04).map(x => x.k + ' в ' + x.prop + ' ' + x.d + ' м, ' + x.n + ' верш. ' + JSON.stringify(x.at));
        const under = {}; for (const k in A) { const C = A[k]; if (!C.root.parent || !C.root.visible || C.reflOnly) continue; let n = 0, lo = 9;
          C.root.traverse(sm => { if (!sm.isSkinnedMesh || !sm.visible) return; const N = sm.geometry.attributes.position.count, s = Math.max(1, N / 1500 | 0); for (let i = 0; i < N; i += s) { sm.getVertexPosition(i, v); v.applyMatrix4(sm.matrixWorld); if (v.y < lo) lo = v.y; if (v.y < -.02) n++; } });
          under[k] = [+lo.toFixed(3), n]; }
        rows.push({ t: +tw.toFixed(2), cl, under }); }
      return { sid, tl: !!TL, rows };
    }, [id, gk, GB[id], +st]);
    const bad = r.rows.filter(x => x.cl.length || Object.values(x.under).some(u => u[0] < -.03));
    console.log(id, r.sid, r.tl ? 'сценарий' : 'старый', 'снимков', r.rows.length, 'с проблемами', bad.length);
    for (const x of bad.slice(0, +process.env.N || 14)) console.log('  ', x.t, x.cl.slice(0, 3).join(' | '), JSON.stringify(x.under));   // N=99 — все снимки с проблемами
  }
  if (errs.length) console.log('ОШИБКИ JS:', errs.slice(0, 5));
  await br.close();
})();
