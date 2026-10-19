// Night Shift: поиск мигающих мест (совпадающие плоскости, z-fighting) во всех местах действия.
// Нужен сервер статики: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1;  запуск: node tools/nightshift-zfight.js [допуск_м=0.012]
// SAME — одинаковый материал без текстуры (не видно), OFFSET — уже с polygonOffset, hidden — объект скрыт.
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body>
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script></body></html>`;
(async () => {
  const tol = +(process.argv[2] || .02);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('PAGEERR', String(e)));
  await p.route('**/nightshift/_a.html', r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/nightshift/_a.html');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 90000 });
  const res = await p.evaluate(async (tol) => {
    await window.__W3chars.load(); World.init();
    const T = THREE, L = window.__dbgL, out = [];
    for (const [ln, o] of Object.entries(L)) {
      o.reset && o.reset();
      o.S.updateMatrixWorld(true);
      const F = [];
      o.S.traverse(m => {
        if (!m.isMesh || m.isSkinnedMesh) return;
        let par = m, inChar = false; while (par) { if (par.userData && par.userData.char) inChar = true; par = par.parent; }
        const g = m.geometry, pos = g.attributes.position; if (!pos) return;
        const idx = g.index ? g.index.array : null, n = idx ? idx.length : pos.count;
        const mats = Array.isArray(m.material) ? m.material : [m.material];
        const groups = g.groups.length ? g.groups : [{ start: 0, count: n, materialIndex: 0 }];
        const flip = m.matrixWorld.determinant() < 0;
        const name = (m.name || m.geometry.type.replace('Geometry', '')) + '@' + new T.Vector3().setFromMatrixPosition(m.matrixWorld).toArray().map(v => v.toFixed(2)).join(',');
        for (const gr of groups) {
          const mt = Array.isArray(m.material) ? mats[gr.materialIndex || 0] : m.material; if (!mt || mt.visible === false) continue;
          for (let i = gr.start; i < Math.min(n, gr.start + gr.count); i += 3) {
            const v = [0, 1, 2].map(k => new T.Vector3().fromBufferAttribute(pos, idx ? idx[i + k] : i + k).applyMatrix4(m.matrixWorld));
            const nr = new T.Vector3().crossVectors(v[1].clone().sub(v[0]), v[2].clone().sub(v[0]));
            const area = nr.length() / 2; if (area < 1e-5) continue; nr.normalize(); if (flip) nr.negate();
            const sides = mt.side === T.DoubleSide ? [nr, nr.clone().negate()] : mt.side === T.BackSide ? [nr.clone().negate()] : [nr];
            for (const s of sides) F.push({ m, name, v, n: s, d: s.dot(v[0]), mt, off: !!mt.polygonOffset, tr: !!mt.transparent, vis: m.visible });
          }
        }
      });
      // пары: почти одна плоскость, смотрят в одну сторону, перекрываются
      const key = f => [Math.round(f.n.x * 20), Math.round(f.n.y * 20), Math.round(f.n.z * 20)].join();
      const byN = {}; for (const f of F) (byN[key(f)] = byN[key(f)] || []).push(f);
      const pairs = {};
      for (const arr of Object.values(byN)) {
        arr.sort((a, b) => a.d - b.d);
        for (let i = 0; i < arr.length; i++) for (let j = i + 1; j < arr.length && arr[j].d - arr[i].d < tol; j++) {
          const A = arr[i], B = arr[j]; if (A.m === B.m || A.n.dot(B.n) < .999) continue;
          // 2D-проекция на плоскость, SAT для треугольников (с усадкой 2 мм, чтобы соседние куски не считались)
          const u = new T.Vector3(), w = new T.Vector3(); u.copy(Math.abs(A.n.x) < .9 ? new T.Vector3(1, 0, 0) : new T.Vector3(0, 1, 0)).cross(A.n).normalize(); w.crossVectors(A.n, u);
          const pr = t => t.v.map(p => [p.dot(u), p.dot(w)]);
          const shrink = P => { const c = [(P[0][0] + P[1][0] + P[2][0]) / 3, (P[0][1] + P[1][1] + P[2][1]) / 3]; return P.map(q => { const dx = q[0] - c[0], dy = q[1] - c[1], l = Math.hypot(dx, dy) || 1; return [q[0] - dx / l * .002, q[1] - dy / l * .002]; }); };
          const P = shrink(pr(A)), Q = shrink(pr(B));
          let sep = false;
          for (const R of [P, Q]) for (let e = 0; e < 3 && !sep; e++) { const a = R[e], c = R[(e + 1) % 3], ax = [-(c[1] - a[1]), c[0] - a[0]];
            const pj = S => S.map(q => q[0] * ax[0] + q[1] * ax[1]); const p1 = pj(P), p2 = pj(Q);
            if (Math.max(...p1) <= Math.min(...p2) || Math.max(...p2) <= Math.min(...p1)) sep = true; }
          if (sep) continue;
          const k = [A.name, B.name].sort().join(' | ');
          const g = pairs[k] = pairs[k] || { a: A.name, b: B.name, gap: B.d - A.d, n: A.n.toArray().map(x => +x.toFixed(2)), cnt: 0, off: A.off || B.off, tr: A.tr || B.tr, vis: A.vis && B.vis, same: A.mt === B.mt && !A.mt.map, ca: (A.mt.map ? 'T' : '') + (A.mt.color ? A.mt.color.getHexString() : ''), cb: (B.mt.map ? 'T' : '') + (B.mt.color ? B.mt.color.getHexString() : '') };
          g.cnt++;
        }
      }
      for (const g of Object.values(pairs)) out.push(Object.assign({ loc: ln }, g));
    }
    return out;
  }, tol);
  for (const r of res.sort((a, b) => a.loc.localeCompare(b.loc) || a.gap - b.gap)) console.log(`${r.loc}\tgap ${r.gap.toFixed(4)}\t${r.off ? 'OFFSET ' : ''}${r.tr ? 'TRANSP ' : ''}${r.vis ? '' : 'hidden '}${r.same ? 'SAME ' : ''}n=${r.n}\t${r.a} #${r.ca}  ~  ${r.b} #${r.cb}  (${r.cnt})`);
  console.log('total', res.length);
  await b.close();
})();
