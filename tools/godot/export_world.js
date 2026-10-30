// Выгружает 5 локаций старой игры (three.js) для Godot: godot/nightshift/world/<место>.glb + <место>.json
// (свет, туман, подвижные части по именам, коллайдеры). Геометрия и текстуры — те же, что в игре (графика «Ультра»).
// Сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1;  запуск: node tools/godot/export_world.js
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, '../../godot/nightshift/world'), PORT = process.env.PORT || 8771;
const EXPORTER = fs.readFileSync(path.join(__dirname, 'vendor/GLTFExporter.r159.classic.js'), 'utf8'), NAMING = require('./naming.js');
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${PORT}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.addScriptTag({ content: EXPORTER }); await p.evaluate(NAMING);
  const shots = await p.evaluate(() => { const out = {}; for (const [id, sh] of Object.entries(window.World.shots || {})) { const o = {};
      for (const [k, v] of Object.entries(sh)) { if (typeof v === 'function') { o[k] = '__fn'; continue; }
        if (v && v.isVector3) o[k] = [v.x, v.y, v.z]; else { try { JSON.stringify(v); o[k] = v; } catch (e) { o[k] = '__obj'; } } }
      out[id] = o; } return { shots: out, alias: window.World.alias || {} }; });
  fs.writeFileSync(path.join(__dirname, '../../godot/nightshift/data/shots.json'), JSON.stringify(shots));
  console.log('shots.json:', Object.keys(shots.shots).length, 'кадров');
  for (const loc of (process.argv[2] || 'lobby,corridor,server,stairs,basement').split(',')) {
    const r = await p.evaluate(async loc => {
      const T = window.THREE, L = window.__dbgL[loc], S = L.S; if (L.reset) L.reset(); S.updateMatrixWorld(true);
      const shown = o => { for (let x = o; x; x = x.parent) if (!x.visible) return false; return true; }, vis0 = new Map(); S.traverse(o => vis0.set(o, shown(o)));
      const hidden = [], hide = o => { if (o && o.isObject3D && o.visible) { o.visible = false; hidden.push(o); } };
      if (L.reset) L.reset();
      // не геометрия места: герои, лучи и пыль (в Godot — объёмный туман), пятна теней под телами, свет (идёт в JSON)
      for (const a of Object.values(window.World.actors || {})) hide(a && (a.root || a));
      for (const k of ['beams', 'dust', 'blobs', 'pad', 'torch', 'etorch', 'spray', 'guts', 'blood']) { const v = L[k]; (Array.isArray(v) ? v : [v]).forEach(hide); }
      S.traverse(o => { if (o.isLight || o.isLineSegments || o.isPoints || o.isSprite) hide(o); });
      // подвижные части — по именам ключей места (двери, лифты, ящик, часы…), чтобы Godot нашёл их в модели
      const named = {};
      for (const [k, v] of Object.entries(L)) { const list = Array.isArray(v) ? v : [v];
        list.forEach((o, i) => { if (o && o.isObject3D && !o.isLight && !o.isScene) { const n = list.length > 1 ? `${k}_${i}` : k; if (!o.name) o.name = n; named[n] = o.name; } }); }
      // свет: всё, что светит в этом месте (кроме выключенных служебных «пустышек» внизу на −60 м)
      const keyOf = new Map(); for (const [k, v] of Object.entries(L)) (Array.isArray(v) ? v : [v]).forEach((o, i) => { if (o && o.isLight) keyOf.set(o, Array.isArray(v) ? `${k}_${i}` : k); });
      const lights = []; const wp = new T.Vector3(), tp = new T.Vector3();
      S.updateMatrixWorld(true);
      S.traverse(o => { if (!o.isLight) return; o.getWorldPosition(wp); if (wp.y < -30) return;
        const e = { type: o.type, key: o.name, name: o.name, color: o.color.getHex(), intensity: o.intensity, pos: wp.toArray() };
        if (o.distance !== undefined) e.distance = o.distance; if (o.decay !== undefined) e.decay = o.decay;
        if (o.isSpotLight) { e.angle = o.angle; e.penumbra = o.penumbra; }
        if (o.target) { o.target.getWorldPosition(tp); e.target = tp.toArray(); }
        if (o.isHemisphereLight) e.ground = o.groundColor.getHex();
        e.shadow = !!o.castShadow; e.visible = vis0.get(o) !== false; lights.push(e); });
      const fog = S.fog ? { color: S.fog.color.getHex(), density: S.fog.density, near: S.fog.near, far: S.fog.far } : null;
      // размноженные сетки (крупинки соли) Godot не читает (EXT_mesh_gpu_instancing) — склеиваем копии в одну обычную сетку
      const merged = []; S.traverse(o => { if (!o.isInstancedMesh || !o.visible) return;
        const g0 = o.geometry.index ? o.geometry.toNonIndexed() : o.geometry, names = Object.keys(g0.attributes).filter(k => ['position', 'normal', 'uv', 'color'].includes(k));
        const out = {}; names.forEach(k => out[k] = []); const m4 = new T.Matrix4(), n3 = new T.Matrix3(), v = new T.Vector3();
        for (let i = 0; i < o.count; i++) { o.getMatrixAt(i, m4); n3.getNormalMatrix(m4);
          for (const k of names) { const at = g0.attributes[k];
            for (let j = 0; j < at.count; j++) {
              if (k === 'position') { v.fromBufferAttribute(at, j).applyMatrix4(m4); out[k].push(v.x, v.y, v.z); }
              else if (k === 'normal') { v.fromBufferAttribute(at, j).applyMatrix3(n3).normalize(); out[k].push(v.x, v.y, v.z); }
              else for (let c = 0; c < at.itemSize; c++) out[k].push(at.array[j * at.itemSize + c]); } } }
        const g = new T.BufferGeometry(); for (const k of names) g.setAttribute(k, new T.Float32BufferAttribute(out[k], g0.attributes[k].itemSize));
        const me = new T.Mesh(g, o.material); me.name = (o.name || 'inst') + '_merged'; me.position.copy(o.position); me.quaternion.copy(o.quaternion); me.scale.copy(o.scale);
        merged.push([o, me]); });
      merged.forEach(([o, me]) => { o.parent.add(me); o.visible = false; });
      // «комнаты изнутри» (материал BackSide): экспортёр теряет сторону — разворачиваем сетку лицом внутрь, иначе в Godot стены невидимы
      const flipped = []; S.traverse(o => { if (!o.isMesh || o.isInstancedMesh || !o.visible) return;
        const ms = Array.isArray(o.material) ? o.material : [o.material]; if (!ms.some(m => m && m.side === T.BackSide)) return;
        const g = o.geometry.clone(); if (g.index) { const ix = g.index.array; for (let i = 0; i < ix.length; i += 3) { const t = ix[i + 1]; ix[i + 1] = ix[i + 2]; ix[i + 2] = t; } }
        else for (const at of Object.values(g.attributes)) { const n = at.itemSize, a = at.array; for (let i = 0; i < at.count; i += 3) for (let c = 0; c < n; c++) { const t = a[(i + 1) * n + c]; a[(i + 1) * n + c] = a[(i + 2) * n + c]; a[(i + 2) * n + c] = t; } }
        if (g.attributes.normal) { const a = g.attributes.normal.array; for (let i = 0; i < a.length; i++) a[i] = -a[i]; }
        const mm = ms.map(m => { if (!m || m.side !== T.BackSide) return m; const c = m.clone(); c.side = T.FrontSide; return c; });
        const me = new T.Mesh(g, Array.isArray(o.material) ? mm : mm[0]); me.name = o.name || 'room'; me.position.copy(o.position); me.quaternion.copy(o.quaternion); me.scale.copy(o.scale);
        flipped.push([o, me]); });
      flipped.forEach(([o, me]) => { o.parent.add(me); o.visible = false; });
      // особые материалы старой игры (шейдеры стекла, лучей, ЭЛТ; «светящееся» сложение) экспортёр не переносит —
      // ставим простые материалы с метками в имени, Godot по меткам подставляет свои (scripts/world/location.gd)
      const screens = new Set(); [].concat(L.screens || []).forEach(o => o && o.traverse && o.traverse(x => screens.add(x)));
      const beams = new Set([].concat(L.beams || [])), swapped = [];
      S.traverse(o => { if (!o.isMesh || !o.visible) return; const arr = Array.isArray(o.material), ms = arr ? o.material : [o.material]; let ch = false;
        const nm = ms.map(m => { if (!m) return m;
          if (m.isShaderMaterial) { ch = true; const u = m.uniforms || {};
            const kind = beams.has(o) || (o.geometry && o.geometry.type === 'CylinderGeometry') ? '__beam' : screens.has(o) ? '__crt' : (u.uPrint || /glass/i.test(o.name || '')) ? '__glass' : '__fx';
            const r = new T.MeshStandardMaterial({ color: 0x808890, transparent: true, opacity: .25 }); r.name = kind; return r; }
          if (m.blending === T.AdditiveBlending) { ch = true; const c = m.clone(); c.blending = T.NormalBlending; c.name = '__add'; return c; }
          return m; });
        if (ch) { swapped.push([o, o.material]); o.material = arr ? nm : nm[0]; } });
      // текстуры, которые рисуются на лету (картинки камер на мониторах — render target), экспортёр не умеет — на время снимаем
      const ok = im => im && (im instanceof HTMLCanvasElement || im instanceof HTMLImageElement || im instanceof ImageBitmap || (typeof OffscreenCanvas !== 'undefined' && im instanceof OffscreenCanvas) || im.data !== undefined);
      const unmapped = []; S.traverse(o => { if (!o.isMesh || !o.visible) return; (Array.isArray(o.material) ? o.material : [o.material]).forEach(m => { if (!m) return;
        for (const k of ['map', 'emissiveMap', 'roughnessMap', 'metalnessMap', 'normalMap', 'bumpMap', 'alphaMap', 'aoMap', 'lightMap']) { const t = m[k];
          if (t && (t.isRenderTargetTexture || !ok(t.image))) { unmapped.push([m, k, t]); m[k] = null; } } }); });
      const glb = await new window.GLTFExporter().parseAsync(S, { binary: true, onlyVisible: true, embedImages: true, maxTextureSize: 2048, trs: true });
      hidden.forEach(o => o.visible = true);
      unmapped.forEach(([m, k, t]) => m[k] = t);
      swapped.forEach(([o, m]) => o.material = m);
      merged.forEach(([o, me]) => { me.parent.remove(me); o.visible = true; });
      flipped.forEach(([o, me]) => { me.parent.remove(me); o.visible = true; });
      let s = ''; const u8 = new Uint8Array(glb); for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
      return { glb: btoa(s), meta: { loc, fog, lights, named, flipped: flipped.length, merged: merged.length, bg: S.background && S.background.isColor ? S.background.getHex() : null } };
    }, loc);
    fs.writeFileSync(path.join(OUT, loc + '.glb'), Buffer.from(r.glb, 'base64'));
    fs.writeFileSync(path.join(OUT, loc + '.json'), JSON.stringify(r.meta, null, 1));
    console.log(loc, (Buffer.from(r.glb, 'base64').length / 1e6).toFixed(1) + ' МБ,', r.meta.lights.length, 'ламп,', Object.keys(r.meta.named).length, 'частей,', r.meta.flipped, 'комнат изнутри,', r.meta.merged, 'размноженных');
  }
  // предметы в руках (старая игра строит их кодом: chars2 propMesh) — отдельными моделями world/props/prop_<имя>.glb
  fs.mkdirSync(path.join(OUT, 'props'), { recursive: true });
  for (const name of ['keys', 'phone', 'sheet', 'radio', 'torch', 'mop', 'lighter', 'cig', 'pen', 'apple', 'splint', 'head']) {
    const g = await p.evaluate(async name => { const pr = window.__W3chars.prop3(name), o = pr.obj, par = o.parent, pos = o.position.clone(), q = o.quaternion.clone(), vis = o.visible;
      if (par) par.remove(o); o.position.set(0, 0, 0); o.quaternion.identity(); o.visible = true;
      const sc = new window.THREE.Scene(); sc.add(o);
      const glb = await new window.GLTFExporter().parseAsync(sc, { binary: true, embedImages: true, trs: true });
      sc.remove(o); o.position.copy(pos); o.quaternion.copy(q); o.visible = vis; if (par) par.add(o);
      let s = ''; const u8 = new Uint8Array(glb); for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s); }, name).catch(e => null);
    if (g) { fs.writeFileSync(path.join(OUT, 'props', 'prop_' + name + '.glb'), Buffer.from(g, 'base64')); } else console.log('предмет не выгружен:', name);
  }
  console.log('предметы:', fs.readdirSync(path.join(OUT, 'props')).length);
  console.log(errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
