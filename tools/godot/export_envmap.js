// Карта окружения мест старой игры для Godot: в «Кино» (cineLoc, scenes.js) каждое место один раз снималось кубом со всеми лампами
// (PMREM) — этот снимок давал мягкий рассеянный свет и блики всему в комнате (стены 0.7, люди 0.22, Жилец 0.15).
// Здесь снимаем то же самое (та же точка, те же лампы, без пыли и лучей) шестью камерами 90° в буфер с плавающей точкой и
// перекладываем в равнопромежуточную панораму 256×128: godot/nightshift/world/<место>_env.bin (float32 RGB, линейные цвета).
// Направление пикселя (u, v): азимут a = (u − 0.5)·2π, полярный угол t = v·π; d = (sin a · sin t, cos t, −cos a · sin t) — так же
// читает шейдер неба в Godot (scripts/world/location.gd, ENV_SKY).
// Сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1;  запуск: node tools/godot/export_envmap.js [места]
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const OUT = path.join(__dirname, '../../godot/nightshift/world'), PORT = process.env.PORT || 8771;
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 960, height: 540 } }); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 200)));
  await p.addInitScript(() => { localStorage.setItem('ns1_land3', 'true'); localStorage.setItem('ns1_tutDone', '1'); });
  await p.goto(`http://127.0.0.1:${PORT}/nightshift/index.html?gfx=ultra&lang=ru`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  for (const loc of (process.argv[2] || 'lobby,corridor,server,stairs,basement').split(',')) {
    const r = await p.evaluate(loc => {
      const T = window.THREE, R = window.__W3core.R, L = window.__dbgL[loc], S = L.S, N = 128, W = 256, H = 128;
      // точка съёмки — как в cineLoc
      const at = { lobby: [0, 1.6, 2], corridor: [0, 1.6, -12], server: [0, 1.5, 0], stairs: [0, 4.6, -3], basement: [0, 1.4, -7] }[loc] || [0, 1.6, 0];
      if (L.reset) L.reset();
      const restore = [];
      const hide = o => { if (o && o.isObject3D && o.visible) { o.visible = false; restore.push(() => (o.visible = true)); } };
      for (const a of Object.values(window.World.actors || {})) hide(a && (a.root || a));
      hide(L.dust); [].concat(L.beams || []).forEach(x => hide(x && x.m));
      if (L.lamps) L.lamps.forEach(l => { const i0 = l.li.intensity; l.li.intensity = 2.4; restore.push(() => (l.li.intensity = i0)); });   // setLamps(o, 7)
      const env0 = S.environment; S.environment = null; S.updateMatrixWorld(true);
      const rt = new T.WebGLRenderTarget(N, N, { type: T.HalfFloatType, depthBuffer: true });
      const cam = new T.PerspectiveCamera(90, 1, .05, 60); cam.position.set(at[0], at[1], at[2]);
      const tm0 = R.toneMapping; R.toneMapping = T.NoToneMapping;
      const faces = [];   // [вперёд, вверх, пиксели]
      const dirs = [[[1, 0, 0], [0, 1, 0]], [[-1, 0, 0], [0, 1, 0]], [[0, 1, 0], [0, 0, 1]], [[0, -1, 0], [0, 0, -1]], [[0, 0, 1], [0, 1, 0]], [[0, 0, -1], [0, 1, 0]]];
      const half = h => { const s = (h & 0x8000) ? -1 : 1, e = (h >> 10) & 0x1f, f = h & 0x3ff; return e === 0 ? s * Math.pow(2, -14) * f / 1024 : e === 31 ? (f ? NaN : s * Infinity) : s * Math.pow(2, e - 15) * (1 + f / 1024); };
      for (const [fw, up] of dirs) {
        cam.up.set(up[0], up[1], up[2]); cam.lookAt(at[0] + fw[0], at[1] + fw[1], at[2] + fw[2]); cam.updateMatrixWorld(true);
        R.setRenderTarget(rt); R.clear(); R.render(S, cam);
        const buf = new Uint16Array(N * N * 4); R.readRenderTargetPixels(rt, 0, 0, N, N, buf);
        const px = new Float32Array(N * N * 3); for (let i = 0; i < N * N; i++) for (let c = 0; c < 3; c++) { const v = half(buf[i * 4 + c]); px[i * 3 + c] = isFinite(v) && v > 0 ? Math.min(v, 64) : 0; }
        const f = new T.Vector3(...fw), u = new T.Vector3().copy(cam.up), rgt = new T.Vector3(); cam.getWorldDirection(f); rgt.crossVectors(f, u).normalize(); u.crossVectors(rgt, f).normalize();
        faces.push([f.clone(), u.clone(), rgt.clone(), px]);
      }
      R.setRenderTarget(null); R.toneMapping = tm0; S.environment = env0; restore.forEach(fn => fn()); rt.dispose();
      // панорама: для каждого направления — грань, в которую оно смотрит сильнее всего, билинейно
      const pano = new Float32Array(W * H * 3), d = new T.Vector3();
      for (let j = 0; j < H; j++) for (let i = 0; i < W; i++) {
        const a = ((i + .5) / W - .5) * Math.PI * 2, t = (j + .5) / H * Math.PI;
        d.set(Math.sin(a) * Math.sin(t), Math.cos(t), -Math.cos(a) * Math.sin(t));
        let best = null, bd = -1; for (const fc of faces) { const k = d.dot(fc[0]); if (k > bd) { bd = k; best = fc; } }
        const [f, u, rgt, px] = best, x = d.dot(rgt) / bd, y = d.dot(u) / bd;
        const fx = Math.min(N - 1.001, Math.max(0, (x + 1) / 2 * N - .5)), fy = Math.min(N - 1.001, Math.max(0, (y + 1) / 2 * N - .5));   // строки буфера — снизу вверх
        const x0 = Math.floor(fx), y0 = Math.floor(fy), tx = fx - x0, ty = fy - y0;
        for (let c = 0; c < 3; c++) {
          const g = (xx, yy) => px[(yy * N + xx) * 3 + c];
          pano[(j * W + i) * 3 + c] = (g(x0, y0) * (1 - tx) + g(x0 + 1, y0) * tx) * (1 - ty) + (g(x0, y0 + 1) * (1 - tx) + g(x0 + 1, y0 + 1) * tx) * ty;
        }
      }
      let s = ''; const u8 = new Uint8Array(pano.buffer); for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
      let mean = 0; for (let i = 0; i < pano.length; i++) mean += pano[i]; mean /= pano.length;
      return { w: W, h: H, at, mean, bin: btoa(s) };
    }, loc);
    fs.writeFileSync(path.join(OUT, loc + '_env.bin'), Buffer.from(r.bin, 'base64'));
    const meta = JSON.parse(fs.readFileSync(path.join(OUT, loc + '.json'), 'utf8')); meta.env = { w: r.w, h: r.h, at: r.at, mean: +r.mean.toFixed(4) };
    fs.writeFileSync(path.join(OUT, loc + '.json'), JSON.stringify(meta, null, 1));
    console.log(loc, 'панорама', r.w + '×' + r.h, 'средняя яркость', r.mean.toFixed(3));
  }
  console.log(errs.length ? 'ошибки JS:\n' + errs.join('\n') : 'ошибок JS нет'); await b.close();
})();
