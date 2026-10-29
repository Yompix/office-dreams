// Night Shift: мерцание от движения камеры во всех кадрах игры (то, что игрок видит как «мигает текстура»).
// Время стоит, камера поворачивается на доли пикселя (как ручная камера, sx = 0 → 2,5 → 5). Ровная картинка меняется плавно
// (вторая разность ≈ 0), край предмета — полоской в 1–2 точки, а «спор» поверхностей по глубине (z-fighting) и рябь отражений — пятнами шума.
// Нужен сервер статики: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1
// Запуск: node tools/nightshift-jitter.js OUTDIR [часть/частей, напр. 0/3 — три процесса параллельно] [W H]   (ONLY=pro_enter,… GFX=retro)
// В OUTDIR: res_<часть>.json (пятна по кадрам) и картинки кадров с пятнами (лиловые — пятна, красные — линии). Помехи на экранах
// мониторов будки — норма (там шум и так меняется с каждым кадром). Плоскости в одной плоскости без движения — tools/nightshift-zfight.js.
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path');
const [out, part = '0/1', Wd = '640', Hd = '360'] = process.argv.slice(2), W = +Wd, H = +Hd, [pi, pn] = part.split('/').map(Number);
if (!out) { console.log('укажи папку: node tools/nightshift-jitter.js OUTDIR [0/3] [W H]'); process.exit(1); }
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: W, height: H } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 200)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><script src="story.js"></script>
<canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
  await p.route(/\/nightshift\/_jit\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/nightshift/_jit.html?gfx=' + (process.env.GFX || 'cine') + '&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async (land) => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (land) World.setLand(true); }, W > H);
  const list = await p.evaluate(() => {   // каждый кадр игры — с реквизитом и ракурсом его первой сцены; потом кадры не из сюжета (смерти, финалы)
    const seen = new Map();
    for (const sc in window.STORY.scenes) { const S = window.STORY.scenes[sc]; (S.beats || []).forEach((bt, i) => { if (!bt.img || seen.has(bt.img) || !World.shots[bt.img]) return;
      seen.set(bt.img, { id: bt.img, from: sc + ':' + i, cam: (bt.cams && bt.cams[0]) || [.5, .5, 1, 1], props: Object.assign({ clock: '21:00', jacket: 1, badge: 1, book: 0 }, S.props || {}, bt.props || {}) }); }); }
    for (const id in World.shots) if (!seen.has(id)) seen.set(id, { id, from: '-', cam: [.5, .5, 1, 1], props: { clock: '21:00', jacket: 1, badge: 1, book: 0 } });
    return [...seen.values()];
  });
  // дождь (линии, летят по времени) при сдвиге камеры дал бы ложные пятна — на время проверки его нет
  await p.evaluate(() => { const r = window.__dbgL.lobby.rain; if (r) Object.defineProperty(r, 'visible', { get: () => false, set: () => {} }); });
  const mine = list.filter((it, i) => i % pn === pi && (!process.env.ONLY || process.env.ONLY.split(',').includes(it.id)));
  fs.mkdirSync(out, { recursive: true });
  const res = [];
  for (const it of mine) {
    const t0 = Date.now();
    const r = await p.evaluate(({ it, land }) => {
      const c = document.getElementById('c'), g = c.getContext('2d', { willReadFrequently: true }), t = 1.2, T = 101.2;
      const cx = land ? .5 + (it.cam[0] - .5) * (9 / 16) / (16 / 9) : it.cam[0];
      const dr = (sx, settle) => World.draw(g, c.width, c.height, it.id, { t, T, cx, cy: it.cam[1], z: it.cam[2], sx, sy: sx * .6, settle, cctv: 0, props: it.props });
      try { dr(0, true); for (let i = 0; i < 4; i++) dr(0, false); } catch (e) { return { err: String(e).slice(0, 160) }; }   // свет и позы успокоились
      const F = [];
      for (const sx of [0, 2.5, 5]) { dr(sx, false); dr(sx, false); const d = g.getImageData(0, 0, c.width, c.height).data, Y = new Float32Array(c.width * c.height);
        for (let i = 0; i < Y.length; i++) Y[i] = d[i * 4] * .3 + d[i * 4 + 1] * .59 + d[i * 4 + 2] * .11; F.push(Y); }
      const w = c.width, h = c.height, fl = new Uint8Array(w * h);
      for (let i = 0; i < fl.length; i++) fl[i] = Math.abs(F[2][i] - 2 * F[1][i] + F[0][i]) > 36 ? 1 : 0;
      // пятна, а не линии: в окне 7×7 отмечено ≥ 20 точек
      const S = new Uint32Array((w + 1) * (h + 1));
      for (let y = 0; y < h; y++) { let row = 0; for (let x = 0; x < w; x++) { row += fl[y * w + x]; S[(y + 1) * (w + 1) + x + 1] = S[y * (w + 1) + x + 1] + row; } }
      let n = 0, bx0 = w, by0 = h, bx1 = 0, by1 = 0; const mk = new Uint8Array(w * h);
      for (let y = 3; y < h - 3; y++) for (let x = 3; x < w - 3; x++) { const a = S[(y - 3) * (w + 1) + x - 3], b = S[(y - 3) * (w + 1) + x + 4], cc = S[(y + 4) * (w + 1) + x - 3], dd = S[(y + 4) * (w + 1) + x + 4];
        if (dd - b - cc + a >= 20 && fl[y * w + x]) { n++; mk[y * w + x] = 1; if (x < bx0) bx0 = x; if (y < by0) by0 = y; if (x > bx1) bx1 = x; if (y > by1) by1 = y; } }
      let img = null;
      if (n > 0) { const id = g.getImageData(0, 0, w, h); for (let i = 0; i < w * h; i++) if (mk[i]) { id.data[i * 4] = 255; id.data[i * 4 + 1] = 0; id.data[i * 4 + 2] = 255; } else if (fl[i]) { id.data[i * 4] = 255; id.data[i * 4 + 1] *= .4; id.data[i * 4 + 2] *= .4; }
        g.putImageData(id, 0, 0); img = c.toDataURL('image/jpeg', .85); }
      let lines = 0; for (let i = 0; i < fl.length; i++) lines += fl[i];
      return { n, raw: lines, box: n ? [bx0, by0, bx1, by1] : null, img };
    }, { it, land: W > H });
    if (r.img) fs.writeFileSync(path.join(out, `${it.id}.jpg`), Buffer.from(r.img.split(',')[1], 'base64'));
    res.push({ id: it.id, from: it.from, n: r.n, raw: r.raw, box: r.box, err: r.err });
    console.log(it.id, r.err ? 'ОШИБКА ' + r.err : `пятна ${r.n} (линии ${r.raw})`, Date.now() - t0, 'мс');
  }
  fs.writeFileSync(path.join(out, `res_${pi}.json`), JSON.stringify(res, null, 1));
  await br.close();
})();
