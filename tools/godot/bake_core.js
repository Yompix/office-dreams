// Запись одного кадра старой игры в странице (общее для bake_shots.js и bake_shift.js): кадр ставится без рисования,
// покадрово пишутся кости героев, лицо, камера кадра, подвижные части места и свет. Возвращает { meta, bin (base64 float32) }.
//   opts: { dur, fps, hold, props (World.draw props: { sh: {…} } — состояние смены), store (имя: запомнить кадр 0 как основу),
//           ref (имя основы: писать только то, что отличается от неё, — слой смены), loop (без «ожидания»: запись — петля) }
module.exports = function pageBake([id, o]) {
  const W = window.World, R = window.__W3core.R, T = window.THREE, sh = W.shots[id] || W.shots[(W.alias || {})[id]] || (/^gore@/.test(id) && window.__goreShot ? window.__goreShot(id) : null);
  if (!sh) return { err: 'нет кадра' };
  const FPS = o.fps || 30, HOLD = o.loop ? 0 : (o.hold ?? 6), dur = o.dur;
  const c = document.createElement('canvas'); c.width = 64; c.height = 36; const g = c.getContext('2d');
  const draw = (t, TT) => W.draw(g, 64, 36, id, Object.assign({ t, T: TT, cx: .5, cy: .5, z: 1, sx: 0, sy: 0 }, o.props ? { props: o.props } : {}));
  R.render = () => {};
  const L = window.__dbgL[sh.loc], S = L.S, actors = Object.entries(W.actors).filter(([k, C]) => C && C.root);
  // состояние места по умолчанию (как в выгруженной модели): постоянные каналы места, совпадающие с ним, не нужны
  if (L.reset) L.reset(); S.updateMatrixWorld(true); const base = {};
  const skip0 = new Set([...['beams', 'dust', 'blobs', 'pad'].flatMap(k => [].concat(L[k] || []))]), aroots = new Set(actors.map(([k, C]) => C.root));
  const v = new T.Vector3(), q = new T.Quaternion();
  const walkLoc = row => (function walk(x) { if (aroots.has(x) || skip0.has(x)) return;
    if (x.isLight) { const n = x.name; row[`l:${n}:i`] = [x.intensity]; row[`l:${n}:v`] = [x.visible ? 1 : 0]; row[`l:${n}:c`] = [x.color.r, x.color.g, x.color.b];
      x.getWorldPosition(v); row[`l:${n}:t`] = [v.x, v.y, v.z]; if (x.target) { x.target.getWorldPosition(v); row[`l:${n}:d`] = [v.x, v.y, v.z]; }
      if (x.distance !== undefined) row[`l:${n}:r`] = [x.distance]; }
    else if (x.name && x !== S) { const n = x.name; row[`o:${n}:t`] = x.position.toArray(); row[`o:${n}:r`] = [x.quaternion.x, x.quaternion.y, x.quaternion.z, x.quaternion.w];
      row[`o:${n}:s`] = x.scale.toArray(); row[`o:${n}:v`] = [x.visible ? 1 : 0]; }
    for (const ch of x.children) walk(ch); })(S);
  walkLoc(base);
  const times = [];
  for (let i = 0; i <= Math.round(dur * FPS); i++) times.push([i / FPS, i / FPS]);
  for (let i = 1; i <= HOLD * FPS; i++) times.push([dur, dur + i / FPS]);   // ожидание выбора: время кадра стоит, жизнь идёт
  // запись не зависит от того, что игра рисовала до неё: рот после прошлой реплики, губы, смыкание — с нуля (речь Godot считает сам)
  for (const [, C] of actors) { if (C.U && C.U.uTalk) C.U.uTalk.value = 0; C.talk = 0; C.talkHist = []; C.vPrev = C.vCur = null; C.vK = 0; C.qT = 0; C.spk = 0; }
  const frames = []; const seen = new Set();
  for (const [t, TT] of times) { draw(t, TT); const row = {};
    S.updateMatrixWorld(true);
    for (const [k, C] of actors) { let on = C.root.visible; for (let x = C.root; x; x = x.parent) if (!x.visible) on = false; if (!on || !C.root.parent) continue;
      seen.add(k);
      C.root.getWorldPosition(v); C.root.getWorldQuaternion(q); row[`a:${k}:root`] = [v.x, v.y, v.z, q.x, q.y, q.z, q.w];
      row[`a:${k}:inner`] = C.inner.scale.toArray();
      for (const [bn, bo] of Object.entries(C.bone)) { row[`a:${k}:q:${bn}`] = [bo.quaternion.x, bo.quaternion.y, bo.quaternion.z, bo.quaternion.w];
        row[`a:${k}:p:${bn}`] = bo.position.toArray(); row[`a:${k}:s:${bn}`] = bo.scale.toArray(); }
      const skin = (C.M || []).find(m => m.name === 'skin') || (C.M || [])[0];
      if (skin && skin.morphTargetInfluences) { const d = skin.morphTargetDictionary; for (const [mn, mi] of Object.entries(d)) row[`a:${k}:f:${mn}`] = [skin.morphTargetInfluences[mi]]; }
    }
    const num = a => Array.isArray(a) && a.length >= 3 && a.every(x => typeof x === 'number') ? a.slice(0, 3) : [NaN, NaN, NaN];
    row['cam'] = [...num(sh.pos), ...num(sh.look), typeof sh.fov === 'number' ? sh.fov : 50, typeof sh.roll === 'number' ? sh.roll : 0];
    row['x:ir'] = [L.ir ? 1 : 0];   // темно — камера в ИК (ч/б с подсветкой у объектива)
    walkLoc(row);
    frames.push(row); }
  R.render = window.__R0;
  // основа слоёв смены: что в кадре по умолчанию (первый кадр) и какие герои
  const store = window.__bakeRef || (window.__bakeRef = {});
  if (o.store) store[o.store] = { f0: frames[0], actors: new Set(seen) };
  const ref = o.ref ? store[o.ref] : null;
  // только каналы, которые меняются (или есть не во всех кадрах); остальное — постоянное значение в шапке
  const keys = new Set(); frames.forEach(f => Object.keys(f).forEach(k => keys.add(k)));
  const meta = { id, loc: sh.loc, fps: FPS, dur, hold: HOLD, frames: frames.length, face: {}, const: {}, chans: [] };
  if (o.loop) meta.loop = 1;
  if (ref) meta.hide = [...ref.actors].filter(k => !seen.has(k));   // в этом состоянии героя основы нет (Итан не за столом)
  const same0 = (a, b) => a && b && a.length === b.length && a.every((y, i) => Math.abs(y - b[i]) < 1e-5);
  for (const k of keys) {
    if (ref) {   // слой: герои основы и то, что совпадает с основой, не пишем
      if (k === 'cam' || (k === 'x:ir' && frames.every(f => same0(f[k], ref.f0[k])))) continue;
      const ak = /^a:([^:]+):/.exec(k); if (ak && ref.actors.has(ak[1]) && !o.keepActors) continue;
      if (/^[ol]:/.test(k) && frames.every(f => same0(f[k], ref.f0[k]))) continue; }
    const first = frames[0][k]; let same = !!first;
    for (const f of frames) { const x = f[k]; if (!x || !first || x.length !== first.length || x.some((y, i) => Math.abs(y - first[i]) > 1e-5)) { same = false; break; } }
    if (same) { const b0 = base[k], asBase = same0(b0, first);
      if (!(/^[ol]:/.test(k) && asBase && !ref)) meta.const[k] = first.map(x => typeof x === 'number' ? +x.toFixed(6) : 0); continue; }   // часть места как в модели — не нужна (у слоя — нужна: основа могла её сдвинуть)
    const n = Math.max(...frames.map(f => f[k] ? f[k].length : 0)); meta.chans.push([k, n]); }
  const data = [];
  for (const [k, n] of meta.chans) for (const f of frames) { const x = f[k]; for (let i = 0; i < n; i++) data.push(x ? x[i] : NaN); }
  const f32 = new Float32Array(data); let s = ''; const u8 = new Uint8Array(f32.buffer); for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
  return { meta, bin: btoa(s) };
};
