// Night Shift: поиск мигающих мест — две поверхности разных предметов в одной плоскости (z-fighting) во всех местах действия.
// Нужен сервер статики: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1
// Запуск: node tools/nightshift-zfight.js [--near] [--gfx=retro] [--json=OUT.json]
// «СПОР» — плоскости совпадают (зазор < 0,3 мм), перекрытие ≥ 4 см², вид разный, обе рисуются с одной стороны, хотя бы одна пишет
// глубину или стоит на непрозрачной (прозрачное стекло без записи глубины тоже спорит с гранью позади — так мигала полоса над EXIT).
// «не видно?» — грани вниз у пола и т. п., проверить глазами. --near — ещё пары с зазором 0,3–3 мм (на WebGL 1 с 16-битной глубиной могут мигать).
// Проверка в движении — tools/nightshift-jitter.js.
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
const arg = k => (process.argv.find(a => a.startsWith('--' + k + '=')) || '').split('=')[1], NEAR = process.argv.includes('--near');
const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body>
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script></body></html>`;
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('PAGEERR', String(e)));
  await p.route('**/nightshift/_a.html*', r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/nightshift/_a.html?gfx=' + (arg('gfx') || 'cine') + '&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { await window.__W3chars.load(); World.init(); });
  const res = await p.evaluate(() => {
    const T = THREE, L = window.__dbgL, A = window.__dbgA, TX = window.__W3core.TX, out = [];
    const txName = new Map(); for (const k in TX) if (TX[k] && TX[k].isTexture) txName.set(TX[k], k);
    const charRoots = new Set(Object.values(A).map(c => c.root));
    const area2 = P => { let s = 0; for (let i = 0; i < P.length; i++) { const a = P[i], c = P[(i + 1) % P.length]; s += a[0] * c[1] - c[0] * a[1]; } return s / 2; };
    const clip = (subj, cl) => {   // Сазерленд — Ходжмен: subj ∩ cl (cl — выпуклый, против часовой)
      let o = subj;
      for (let i = 0; i < cl.length && o.length; i++) {
        const a = cl[i], c = cl[(i + 1) % cl.length], inp = o; o = [];
        const side = q => (c[0] - a[0]) * (q[1] - a[1]) - (c[1] - a[1]) * (q[0] - a[0]);
        for (let j = 0; j < inp.length; j++) { const P = inp[j], Q = inp[(j + 1) % inp.length], sp = side(P), sq = side(Q);
          if (sp >= 0) o.push(P); if ((sp >= 0) !== (sq >= 0)) { const t = sp / (sp - sq); o.push([P[0] + (Q[0] - P[0]) * t, P[1] + (Q[1] - P[1]) * t]); } }
      }
      return o;
    };
    const desc = m => {
      const M = Array.isArray(m.material) ? m.material[0] : m.material, bb = new T.Box3().setFromObject(m), c = bb.getCenter(new T.Vector3()), s = bb.getSize(new T.Vector3());
      let vis = true; for (let q = m; q; q = q.parent) if (!q.visible) { vis = false; break; }
      const g = m.geometry.parameters ? m.geometry.type.replace('Geometry', '') + '(' + Object.values(m.geometry.parameters).filter(v => typeof v === 'number').map(v => +v.toFixed(3)).join(',') + ')' : m.geometry.type;
      return { g, c: [c.x, c.y, c.z].map(v => +v.toFixed(3)), s: [s.x, s.y, s.z].map(v => +v.toFixed(3)), vis, mt: M ? M.type.replace('Material', '') : '?',
        col: M && M.color ? M.color.getHexString() : '', map: M && M.map ? (txName.get(M.map) || 'map') : '', tr: !!(M && M.transparent), dw: M ? M.depthWrite !== false : true,
        po: !!(M && M.polygonOffset), side: M ? M.side : 0 };
    };
    for (const lk in L) {
      const S = L[lk].S; S.updateMatrixWorld(true);
      const meshes = []; S.traverse(m => { if (!m.isMesh || m.isSkinnedMesh || !m.geometry || !m.geometry.attributes.position) return; for (let q = m; q; q = q.parent) if (charRoots.has(q)) return; meshes.push(m); });
      const B = new Map(), v0 = new T.Vector3(), v1 = new T.Vector3(), v2 = new T.Vector3(), e1 = new T.Vector3(), e2 = new T.Vector3(), n = new T.Vector3(), IM = new T.Matrix4(), W = new T.Matrix4();
      meshes.forEach((m, mi) => {   // треугольники по ячейкам: направление плоскости (до 0,01) × расстояние до начала (по 3 мм)
        const pos = m.geometry.attributes.position, idx = m.geometry.index, cnt = idx ? idx.count : pos.count;
        for (let ii = 0; ii < (m.isInstancedMesh ? m.count : 1); ii++) {
          if (m.isInstancedMesh) { m.getMatrixAt(ii, IM); W.multiplyMatrices(m.matrixWorld, IM); } else W.copy(m.matrixWorld);
          const id = m.isInstancedMesh ? mi + ':' + ii : mi + '';
          for (let t = 0; t + 2 < cnt; t += 3) {
            v0.fromBufferAttribute(pos, idx ? idx.getX(t) : t).applyMatrix4(W); v1.fromBufferAttribute(pos, idx ? idx.getX(t + 1) : t + 1).applyMatrix4(W); v2.fromBufferAttribute(pos, idx ? idx.getX(t + 2) : t + 2).applyMatrix4(W);
            e1.subVectors(v1, v0); e2.subVectors(v2, v0); n.crossVectors(e1, e2); if (n.length() < 4e-5) continue; n.normalize();
            const ax = Math.abs(n.x) >= Math.abs(n.y) && Math.abs(n.x) >= Math.abs(n.z) ? 0 : Math.abs(n.y) >= Math.abs(n.z) ? 1 : 2, sg = n.getComponent(ax) >= 0 ? 1 : -1;
            const nk = [n.x * sg, n.y * sg, n.z * sg], d = nk[0] * v0.x + nk[1] * v0.y + nk[2] * v0.z, ua = ax === 0 ? 1 : 0, va = ax === 2 ? 1 : 2;
            const P = [v0, v1, v2].map(v => [v.getComponent(ua), v.getComponent(va)]); if (area2(P) < 0) P.reverse();
            const key = nk.map(x => Math.round(x * 100)).join(), db = Math.round(d / .003), k2 = key + '|' + db;
            if (!B.has(k2)) B.set(k2, []);
            B.get(k2).push({ mi, id, face: sg, d, P, key, db, bb: [Math.min(P[0][0], P[1][0], P[2][0]), Math.min(P[0][1], P[1][1], P[2][1]), Math.max(P[0][0], P[1][0], P[2][0]), Math.max(P[0][1], P[1][1], P[2][1])] });
          }
        }
      });
      const pair = new Map();
      for (const [k2, arr] of B) {
        const [key, dbS] = k2.split('|'), db = +dbS, cand = arr.concat(B.get(key + '|' + (db + 1)) || []);
        if (new Set(cand.map(t => t.id)).size < 2) continue;
        for (const t1 of arr) for (const t2 of cand) {
          if (t2.id === t1.id || (t2.db === db && t1.id > t2.id)) continue;   // каждую пару в своей ячейке — один раз
          const gap = Math.abs(t1.d - t2.d); if (gap > .003) continue;
          if (t1.bb[2] <= t2.bb[0] || t2.bb[2] <= t1.bb[0] || t1.bb[3] <= t2.bb[1] || t2.bb[3] <= t1.bb[1]) continue;
          const ov = clip(t1.P, t2.P); if (ov.length < 3) continue; const a = Math.abs(area2(ov)); if (a < 1e-6) continue;
          const lo = t1.mi <= t2.mi ? t1 : t2, hi = lo === t1 ? t2 : t1, pk = lo.id + '~' + hi.id + '~' + key + '|' + db + '~' + (t1.face === t2.face);   // каждая плоскость пары — отдельно (низ у пола не прячет бока)
          const r = pair.get(pk) || { a: 0, gap: 9, mi: [lo.mi, hi.mi], same: t1.face === t2.face, n: key, fa: lo.face, d: +lo.d.toFixed(3) }; r.a += a; r.gap = Math.min(r.gap, gap); pair.set(pk, r);
        }
      }
      for (const r of pair.values()) if (r.a >= 4e-4) out.push(Object.assign({ loc: lk, area: +r.a.toFixed(4), gap: +(r.gap * 1000).toFixed(2), same: r.same, n: r.n, fa: r.fa, d: r.d }, { a: desc(meshes[r.mi[0]]), b: desc(meshes[r.mi[1]]) }));
    }
    return out;
  });
  const conflict = r => { const { a, b } = r; if (a.po || b.po) return false; if (a.tr && !a.dw && b.tr && !b.dw) return false;   // два прозрачных без записи глубины не спорят
    if (a.side === 2 || b.side === 2) return true; return r.same !== (a.side === 1) !== (b.side === 1); };   // BackSide меняет сторону, с которой грань видна
  const samelook = r => r.a.col === r.b.col && r.a.map === r.b.map && r.a.mt === r.b.mt;
  const hidden = r => r.n === '0,100,0' && r.fa < 0 && r.d < .03;   // грань вниз у самого пола
  const ax = { '100,0,0': 'x', '0,100,0': 'y', '0,0,100': 'z' }, s = d => `${d.g} ${d.mt} #${d.col}${d.map ? ' ' + d.map : ''} @${d.c.join(',')}${d.tr ? ' TR' : ''}${d.dw ? '' : ' noDW'}${d.side ? ' side' + d.side : ''}${d.vis ? '' : ' hidden'}`;
  const K = res.filter(r => conflict(r) && !samelook(r) && (NEAR || r.gap < .3)).sort((x, y) => x.gap >= .3 === y.gap >= .3 ? y.area - x.area : x.gap - y.gap);
  for (const r of K) console.log(`${r.gap < .3 ? (hidden(r) ? 'не видно?' : 'СПОР') : 'близко'}\t${r.loc}\t${Math.round(r.area * 1e4)} см²\t${ax[r.n] || r.n}=${r.d}\tзазор ${r.gap} мм\t${s(r.a)}  ~  ${s(r.b)}`);
  console.log('всего пар в одной плоскости:', res.length, '| СПОР:', K.filter(r => r.gap < .3 && !hidden(r)).length);
  if (arg('json')) fs.writeFileSync(arg('json'), JSON.stringify(res, null, 1));
  await b.close();
})();
