// Выгружает героев старой игры такими, какими их собирает chars2.js (castV3): тело, лицо с 53 формами, волосы, одежда,
// рубаха Жильца (skinGown), кепка сменщика и прочее, построенное кодом; скелет — уже перекладанный под клипы (как в игре),
// имена костей — как в записи кадров (root, Head…). → godot/nightshift/actors/<ключ>.glb
//   node tools/godot/export_actors.js [ключ,ключ…]   (сервер: cd public/games && python3 -m http.server 8771)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, '../../godot/nightshift/actors');
const EXPORTER = fs.readFileSync(path.join(__dirname, 'vendor/GLTFExporter.r159.classic.js'), 'utf8');
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.addScriptTag({ content: EXPORTER });
  const keys = process.argv[2] ? process.argv[2].split(',') : await p.evaluate(() => Object.keys(window.World.actors).filter(k => window.World.actors[k] && window.World.actors[k].root));
  for (const k of keys) {
    const r = await p.evaluate(async k => {
      const C = window.World.actors[k]; if (!C || !C.root) return { err: 'нет героя' };
      const R = C.root, par = R.parent, pos = R.position.clone(), q = R.quaternion.clone(), vis = R.visible;
      if (par) par.remove(R); R.position.set(0, 0, 0); R.quaternion.identity(); R.visible = true;
      // предметы в руках — отдельными моделями (world/props); здесь их прячем
      const hid = []; for (const n of ['keys', 'paper', 'sheet', 'cig', 'lighter', 'mop', 'puffs']) [].concat(C[n] || []).forEach(o => { if (o && o.isObject3D && o.visible) { o.visible = false; hid.push(o); } });
      // сетки игры квантованы (int16, шаг 6 байт) — glTF так нельзя: на время выгрузки — обычные float
      const T3 = window.THREE, back = [];
      const fl = a => { const n = a.count, k = a.itemSize, f = new Float32Array(n * k); for (let i = 0; i < n; i++) for (let j = 0; j < k; j++) f[i * k + j] = a.getComponent ? a.getComponent(i, j) : [a.getX(i), a.getY(i), a.getZ(i), a.getW(i)][j]; return new T3.BufferAttribute(f, k); };
      R.traverse(o => { if (!o.isMesh || !o.geometry) return; const g = o.geometry, old = { attrs: Object.assign({}, g.attributes), morph: Object.fromEntries(Object.entries(g.morphAttributes).map(([kk, v]) => [kk, v.slice()])) };
        for (const [n, a] of Object.entries(g.attributes)) if (n !== 'skinIndex' && (a.isInterleavedBufferAttribute || !(a.array instanceof Float32Array))) g.setAttribute(n, fl(a));
        for (const [n, list] of Object.entries(g.morphAttributes)) g.morphAttributes[n] = list.map(a => (a.isInterleavedBufferAttribute || !(a.array instanceof Float32Array)) ? fl(a) : a);
        back.push([g, old]); });
      const inner0 = C.inner ? C.inner.name : null; if (C.inner) C.inner.name = '__inner';   // рост героя (запись кадров: канал inner) — по этому имени
      // рубаха Жильца: свой шейдер (складки вокруг тела, грязь, мокрый рваный подол) берёт точку покоя aB, долю длины aF и угол aS —
      // в glTF их кладём в UV (угол, доля длины) и цвет вершин (точка покоя × 0,2 + 0,5)
      // кожа: точка покоя каждой вершины (vB = uQ · position — как в шейдере кожи старой игры) — для савана на коже Жильца и тёмных
      // глазниц (socket); uQ и точки глаз берём из готовой программы материала (renderer.properties), для этого — компиляция
      const meta = {};
      if (C.o && (C.o.shroud || C.o.socket)) { const Rr = window.__W3core.R, cam = new T3.PerspectiveCamera(); const tmp = new T3.Scene(); const p0 = R.parent; tmp.add(R); try { Rr.compile(tmp, cam); } catch (e) {} tmp.remove(R); if (p0) p0.add(R);
        R.traverse(o => { if (!o.isMesh || o.name !== 'skin') return; const U = (Rr.properties.get(o.material) || {}).uniforms; if (!U || !U.uQ) { meta.err = 'нет uQ'; return; }
          const Q = U.uQ.value, g = o.geometry, ps = g.getAttribute('position'), n = ps.count, col = new Float32Array(n * 4), v = new T3.Vector3();
          for (let i = 0; i < n; i++) { v.set(ps.getX(i), ps.getY(i), ps.getZ(i)).applyMatrix4(Q); col[4 * i] = v.x * .2 + .5; col[4 * i + 1] = v.y * .2 + .5; col[4 * i + 2] = v.z * .2 + .5; col[4 * i + 3] = 1; }
          const had = g.getAttribute('color'); g.setAttribute('color', new T3.BufferAttribute(col, 4)); meta.vb = true;
          for (const k of ['uEyeL3', 'uEyeR3']) if (U[k]) meta[k] = U[k].value.toArray();
          (meta.restore = meta.restore || []).push(() => { if (had) g.setAttribute('color', had); else g.deleteAttribute('color'); }); }); }
      const gownAdd = []; R.traverse(o => { if (!o.isMesh || o.name !== 'gown') return; const g = o.geometry, aB = g.getAttribute('aB'), aF = g.getAttribute('aF'), aS = g.getAttribute('aS'); if (!aB || !aF || !aS) return;
        const n = aF.count, uv = new Float32Array(n * 2), col = new Float32Array(n * 4);
        for (let i = 0; i < n; i++) { uv[2 * i] = Math.atan2(aS.getX(i), aS.getY(i) + 1e-5) / (2 * Math.PI) + .5; uv[2 * i + 1] = aF.getX(i);
          col[4 * i] = aB.getX(i) * .2 + .5; col[4 * i + 1] = aB.getY(i) * .2 + .5; col[4 * i + 2] = aB.getZ(i) * .2 + .5; col[4 * i + 3] = 1; }
        const had = { uv: g.getAttribute('uv'), color: g.getAttribute('color') }; g.setAttribute('uv', new T3.BufferAttribute(uv, 2)); g.setAttribute('color', new T3.BufferAttribute(col, 4)); gownAdd.push([g, had]); });
      const sc = new window.THREE.Scene(); sc.add(R); R.updateMatrixWorld(true);
      const meshes = []; R.traverse(o => { if (o.isMesh && o.visible) meshes.push(o.name + (o.isSkinnedMesh ? '*' : '')); });
      let glb; try { glb = await new window.GLTFExporter().parseAsync(sc, { binary: true, onlyVisible: true, embedImages: true, maxTextureSize: 2048, trs: true }); }
      finally { sc.remove(R); R.position.copy(pos); R.quaternion.copy(q); R.visible = vis; if (par) par.add(R); hid.forEach(o => (o.visible = true));
        for (const [g, old] of back) { for (const [n, a] of Object.entries(old.attrs)) g.setAttribute(n, a); g.morphAttributes = old.morph; }
        if (C.inner) C.inner.name = inner0;
        for (const [g, had] of gownAdd) for (const k of ['uv', 'color']) { if (had[k]) g.setAttribute(k, had[k]); else g.deleteAttribute(k); }
        (meta.restore || []).forEach(f => f()); delete meta.restore; }
      let s = ''; const u8 = new Uint8Array(glb); for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
      return { glb: btoa(s), meshes, meta };
    }, k).catch(e => ({ err: String(e).split('\n')[0].slice(0, 200) }));
    if (r.err) { console.log(k, r.err); continue; }
    const buf = Buffer.from(r.glb, 'base64'); fs.writeFileSync(path.join(OUT, k + '.glb'), buf);
    if (r.meta && (r.meta.vb || r.meta.err)) { fs.writeFileSync(path.join(OUT, k + '.json'), JSON.stringify(r.meta)); console.log(k, 'кожа:', JSON.stringify(r.meta)); }
    console.log(k, (buf.length / 1e6).toFixed(1) + ' МБ,', r.meshes.join(' '));
  }
  console.log(errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
