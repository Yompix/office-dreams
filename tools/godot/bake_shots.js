// Запись постановки кадров старой игры для Godot: в странице игры (three.js) кадр ставится без рисования (1–2 мс),
// и покадрово (30 к/с) пишется всё, что двигается: кости героев, лицо, предметы в руках, камера кадра, подвижные части места и свет.
// Godot проигрывает запись своим светом — позы, касания и «твёрдый мир» те же, что в старой игре (перезапись — после правок старой игры).
// Губы и громкость речи не пишем (talk пустой): их Godot считает сам по языку игры.
//   node tools/godot/bake_shots.js [кадр,кадр…|all]   →  godot/nightshift/bake/<кадр>.json + .bin (float32)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '../..'), OUT = path.join(ROOT, 'godot/nightshift/bake'), FPS = 30, HOLD = 6, MARGIN = 2;
const story = JSON.parse(fs.readFileSync(path.join(ROOT, 'godot/nightshift/data/story.json'), 'utf8'));
const timings = fs.readdirSync(path.join(ROOT, 'public/games/nightshift')).filter(f => /^timing(_\w+)?\.json$/.test(f)).map(f => JSON.parse(fs.readFileSync(path.join(ROOT, 'public/games/nightshift', f), 'utf8')));
// сколько секунд кадр (img) идёт в сценарии: реплики по самому длинному языку, подряд идущие кадры с тем же img — одно время
function needs() {
  const need = {}; const lineDur = k => Math.max(0, ...timings.map(T => (T[k] && T[k].dur) || 0));
  for (const sc of Object.values(story.scenes)) { let prev = null, acc = 0;
    for (const b of sc.beats || []) { let d = (b.sayAt || .15); for (const k of b.say || []) d += lineDur(k) + .3;
      if (b.text) d += b.text.length / 14; d = Math.max(d + (b.pad != null ? b.pad : .3), 1.2);
      if (b.img && b.img === prev) acc += d; else acc = d; prev = b.img;
      if (b.img) need[b.img] = Math.max(need[b.img] || 0, acc); }
    const th = sc.then || {}; for (const k of ['explore', 'talk']) if (th[k] && th[k].img) need[th[k].img] = Math.max(need[th[k].img] || 0, 4); }
  return need;
}
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const need = needs(); const want = (process.argv[2] || 'all') === 'all' ? Object.keys(need) : process.argv[2].split(',');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${process.env.PORT || 8771}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.evaluate(require('./naming.js')); await p.evaluate(() => { window.__R0 = window.__W3core.R.render.bind(window.__W3core.R); });
  let total = 0;
  for (const id of want) {
    const dur = (need[id] || 6) + MARGIN;
    let r; try { r = await p.evaluate(([id, dur, FPS, HOLD]) => {
      const W = window.World, R = window.__W3core.R, T = window.THREE, sh = W.shots[id] || W.shots[(W.alias || {})[id]]; if (!sh) return { err: 'нет кадра' };
      const c = document.createElement('canvas'); c.width = 64; c.height = 36; const g = c.getContext('2d');
      const draw = (t, TT) => W.draw(g, 64, 36, id, { t, T: TT, cx: .5, cy: .5, z: 1, sx: 0, sy: 0 });
      R.render = () => {};
      const L = window.__dbgL[sh.loc], S = L.S, actors = Object.entries(W.actors).filter(([k, C]) => C && C.root);
      // состояние места по умолчанию (как в выгруженной модели): постоянные каналы места, совпадающие с ним, не нужны
      if (L.reset) L.reset(); S.updateMatrixWorld(true); const base = {};
      const skip0 = new Set([...['beams', 'dust', 'blobs', 'pad'].flatMap(k => [].concat(L[k] || []))]), aroots = new Set(actors.map(([k, C]) => C.root));
      (function walk(o) { if (aroots.has(o) || skip0.has(o)) return;
        if (o.isLight) { const n = o.name; base[`l:${n}:i`] = [o.intensity]; base[`l:${n}:v`] = [o.visible ? 1 : 0]; base[`l:${n}:c`] = [o.color.r, o.color.g, o.color.b];
          const w = new T.Vector3(); o.getWorldPosition(w); base[`l:${n}:t`] = w.toArray(); if (o.target) { o.target.getWorldPosition(w); base[`l:${n}:d`] = w.toArray(); }
          if (o.distance !== undefined) base[`l:${n}:r`] = [o.distance]; }
        else if (o.name && o !== S) { const n = o.name; base[`o:${n}:t`] = o.position.toArray(); base[`o:${n}:r`] = [o.quaternion.x, o.quaternion.y, o.quaternion.z, o.quaternion.w];
          base[`o:${n}:s`] = o.scale.toArray(); base[`o:${n}:v`] = [o.visible ? 1 : 0]; }
        for (const c of o.children) walk(c); })(S);
      const frames = []; const times = [];
      for (let i = 0; i <= Math.round(dur * FPS); i++) times.push([i / FPS, i / FPS]);
      for (let i = 1; i <= HOLD * FPS; i++) times.push([dur, dur + i / FPS]);   // ожидание выбора: время кадра стоит, жизнь идёт
      const q = new T.Quaternion(), v = new T.Vector3();
      const chans = new Map(); const order = []; const put = (k, vals) => { if (!chans.has(k)) { chans.set(k, order.length); order.push([k, vals.length]); } return vals; };
      for (const [t, TT] of times) { draw(t, TT); const row = {};
        S.updateMatrixWorld(true);
        const roots = new Set();
        for (const [k, C] of actors) { roots.add(C.root); let on = C.root.visible; for (let x = C.root; x; x = x.parent) if (!x.visible) on = false; if (!on || !C.root.parent) continue;
          C.root.getWorldPosition(v); C.root.getWorldQuaternion(q); row[`a:${k}:root`] = [v.x, v.y, v.z, q.x, q.y, q.z, q.w];
          row[`a:${k}:inner`] = C.inner.scale.toArray();
          for (const [bn, bo] of Object.entries(C.bone)) { row[`a:${k}:q:${bn}`] = [bo.quaternion.x, bo.quaternion.y, bo.quaternion.z, bo.quaternion.w];
            row[`a:${k}:p:${bn}`] = bo.position.toArray(); row[`a:${k}:s:${bn}`] = bo.scale.toArray(); }
          const skin = (C.M || []).find(m => m.name === 'skin') || (C.M || [])[0];
          if (skin && skin.morphTargetInfluences) { const d = skin.morphTargetDictionary; for (const [mn, mi] of Object.entries(d)) row[`a:${k}:f:${mn}`] = [skin.morphTargetInfluences[mi]]; }
        }
        const num = a => Array.isArray(a) && a.length >= 3 && a.every(x => typeof x === 'number') ? a.slice(0, 3) : [NaN, NaN, NaN];
        row['cam'] = [...num(sh.pos), ...num(sh.look), typeof sh.fov === 'number' ? sh.fov : 50, typeof sh.roll === 'number' ? sh.roll : 0];
        const skipK = new Set([...['beams', 'dust', 'blobs', 'pad'].flatMap(k => [].concat(L[k] || []))]);
        (function walk(o) { if (roots.has(o) || skipK.has(o)) return;
          if (o.isLight) { const n = o.name; row[`l:${n}:i`] = [o.intensity]; row[`l:${n}:v`] = [o.visible ? 1 : 0]; row[`l:${n}:c`] = [o.color.r, o.color.g, o.color.b];
            o.getWorldPosition(v); row[`l:${n}:t`] = [v.x, v.y, v.z]; if (o.target) { o.target.getWorldPosition(v); row[`l:${n}:d`] = [v.x, v.y, v.z]; }
            if (o.distance !== undefined) row[`l:${n}:r`] = [o.distance]; }
          else if (o.name && o !== S) { const n = o.name; row[`o:${n}:t`] = o.position.toArray(); row[`o:${n}:r`] = [o.quaternion.x, o.quaternion.y, o.quaternion.z, o.quaternion.w];
            row[`o:${n}:s`] = o.scale.toArray(); row[`o:${n}:v`] = [o.visible ? 1 : 0]; }
          for (const c of o.children) walk(c); })(S);
        frames.push(row); }
      R.render = window.__R0;
      // только каналы, которые меняются (или есть не во всех кадрах); остальное — постоянное значение в шапке
      const keys = new Set(); frames.forEach(f => Object.keys(f).forEach(k => keys.add(k)));
      const meta = { id, loc: sh.loc, fps: FPS, dur, hold: HOLD, frames: frames.length, face: {}, bones: {}, const: {}, chans: [] };
      for (const [k, C] of actors) { const skin = (C.M || []).find(m => m.name === 'skin') || (C.M || [])[0];
        if (skin && skin.morphTargetDictionary) meta.face[k] = Object.entries(skin.morphTargetDictionary).sort((a, b) => a[1] - b[1]).map(e => e[0]); }
      const data = [];
      for (const k of keys) { const first = frames[0][k]; let same = !!first;
        for (const f of frames) { const x = f[k]; if (!x || !first || x.length !== first.length || x.some((y, i) => Math.abs(y - first[i]) > 1e-5)) { same = false; break; } }
        if (same) { const b0 = base[k], asBase = b0 && b0.length === first.length && b0.every((y, i) => Math.abs(y - first[i]) < 1e-5);
          if (!(/^[ol]:/.test(k) && asBase)) meta.const[k] = first.map(x => typeof x === 'number' ? +x.toFixed(6) : 0); continue; }   // часть места как в модели — не нужна
        const n = Math.max(...frames.map(f => f[k] ? f[k].length : 0)); meta.chans.push([k, n]);
        for (const f of frames) { const x = f[k]; for (let i = 0; i < n; i++) data.push(x ? x[i] : NaN); } }
      const f32 = new Float32Array(data); let s = ''; const u8 = new Uint8Array(f32.buffer); for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
      return { meta, bin: btoa(s) };
    }, [id, dur, FPS, HOLD]); } catch (e) { console.log(id, 'ошибка:', String(e).split('\n')[0].slice(0, 160)); continue; }
    if (r.err) { console.log(id, r.err); continue; }
    const bin = Buffer.from(r.bin, 'base64'); fs.writeFileSync(path.join(OUT, id + '.bin'), bin); fs.writeFileSync(path.join(OUT, id + '.json'), JSON.stringify(r.meta));
    total += bin.length; console.log(id, r.meta.dur.toFixed(1) + ' с,', r.meta.frames, 'кадров,', r.meta.chans.length, 'каналов,', (bin.length / 1e6).toFixed(2), 'МБ');
  }
  console.log('всего', (total / 1e6).toFixed(1), 'МБ;', errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
