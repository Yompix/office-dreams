// «Hush Till Six» — кадры трейлера из движка игры (только 3D, без надписей: их кладёт comp.js).
//   node tools/trailer/render.js TIMELINE.json CACHE [--rs .75] [--only id,id] [--preview]
//   Сервер статики: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1
// Кэш: CACHE/<ключ сегмента>/00000.jpg… + done.json (список файлов по кадрам). Ключ — от кадра, времени, плана, размера и языка,
// поэтому перемонтаж пересчитывает только изменённые сегменты. Камера наблюдения — 12 кадров в секунду, как в игре.
// --preview: по три кадра на сегмент в малом размере → CACHE/preview/*.jpg и лист sheet.jpg (проверка монтажа глазами).
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), crypto = require('crypto'), { execFileSync } = require('child_process');
const args = process.argv.slice(2), opt = n => { const i = args.indexOf('--' + n); return i < 0 ? null : args[i + 1]; };
const [tlPath, cache] = args;
const TL = JSON.parse(fs.readFileSync(tlPath)), PREVIEW = args.includes('--preview'), ONLY = opt('only') ? opt('only').split(',') : null;
const LAND = TL.fmt === 'yt', RS = +(opt('rs') || (PREVIEW ? .4 : .75)), FPS = TL.fps;
const [W, H] = PREVIEW ? (LAND ? [640, 360] : [360, 640]) : [TL.W, TL.H];
const ROOT = path.join(__dirname, '../../public/games/nightshift');
const TIM = JSON.parse(fs.readFileSync(path.join(ROOT, TL.lang === 'en' ? 'timing_en.json' : 'timing.json')));
const ENGINE = fs.readFileSync(path.join(ROOT, 'engine.js'), 'utf8');
const VIS_CH = eval(ENGINE.match(/const VIS_CH = (\(\(\) => \{[\s\S]*?return m; \}\)\(\));/)[1]);   // формы губ по буквам — как в игре
const HTML = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#000">
<script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script>${TL.lang === 'en' ? '<script src="i18n.js"></script>' : ''}<script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script>
<canvas id="c" width="${W}" height="${H}"></canvas></body></html>`;
const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
const scrX = x => LAND ? .5 + (x - .5) * (9 / 16) / (16 / 9) : x;

// громкость голоса по кадрам (губы): как анализатор игры — RMS коротких окон × 6, сглаживание
const envCache = {};
function envOf(file, g) {
  if (envCache[file]) return envCache[file];
  const raw = execFileSync('ffmpeg', ['-v', 'error', '-i', path.join(ROOT, file), '-ac', '1', '-ar', '24000', '-f', 'f32le', '-'], { maxBuffer: 1 << 28 });
  const x = new Float32Array(raw.buffer, raw.byteOffset, raw.length / 4), win = 288;   // 12 мс
  const lv = t => { const i1 = Math.min(x.length, Math.round(t * 24000)), i0 = Math.max(0, i1 - win); let q = 0; for (let i = i0; i < i1; i++) q += x[i] * x[i]; return i1 > i0 ? Math.min(1, Math.sqrt(q / (i1 - i0)) * g * 6) : 0; };
  return (envCache[file] = { lv, len: x.length / 24000 });
}
function visemeAt(k, tl) {
  const w = (TIM[k] || {}).w; if (!w || !w.length) return null;
  let i = -1; for (let j = 0; j < w.length && w[j][0] <= tl; j++) i = j;
  if (i < 0) return 'sil';
  const L = [...String(w[i][1]).toLowerCase()].filter(c => VIS_CH[c]); if (!L.length) return 'sil';
  const st = w[i][0], nx = i + 1 < w.length ? w[i + 1][0] : st + 9, en = Math.min(nx - .02, st + .075 * L.length + .1);
  if (tl > en) return 'sil';
  return VIS_CH[L[Math.min(L.length - 1, Math.floor((tl - st) / Math.max(.05, en - st) * L.length))]];
}
// кто говорит вслух в момент T трейлера (для губ)
function talkAt(Tt, smooth) {
  for (const v of TL.vo) {
    if (!v.aloud || Tt < v.at || Tt > v.at + v.dur) continue;
    const e = envOf(v.file, v.g), tl = (Tt - v.at) * v.rate;
    smooth.v += (e.lv(tl) - smooth.v) * .75;
    return { who: v.who, v: smooth.v, vis: visemeAt(v.k, tl) };
  }
  smooth.v *= .4; return null;
}

// параметры World.draw для кадра i сегмента
function frameOpt(s, i, smooth) {
  const tau = i / FPS, Tt = s.start + tau, T = 100 + (s.t0 || 0) + tau, c = (LAND ? s.camL : s.camP) || s.cam || [.5, .5, 1, 1], raw = LAND && !!s.camL;   // camL — доли кадра 16:9 как есть, cam — доли 9:16 (как в story.js)
  const lp = Math.max(0, Math.min(1, tau / Math.max(.1, s.dur))), z = c[2] + (c[3] - c[2]) * ease(lp);
  const shift = /^cam(\d|_m2)/.test(s.shot), cctv = !!s.cctv;
  const o = { t: shift ? T : (s.t0 || 0) + tau, T, z, props: Object.assign({ clock: '03:00', jacket: 1, badge: 1, book: 0 }, s.props || {}), cctv: cctv ? 1 : 0, settle: i === 0 };
  if (Array.isArray(c[0])) o.wp = [c[0], c[1]]; else { o.cx = raw ? c[0] : scrX(c[0]); o.cy = c[1]; }
  if (!cctv && !shift) { o.sx = Math.sin(T * 1.6) * 8 + Math.sin(T * 4.9) * 3; o.sy = Math.cos(T * 1.2) * 8 + Math.sin(T * 4.1) * 3; } else { o.sx = 0; o.sy = 0; }
  const tk = talkAt(Tt, smooth); if (tk) o.talk = tk;
  if (s.scareAt != null && tau >= s.scareAt) o.scare = Math.exp(-(tau - s.scareAt) * 2.2);
  return o;
}
const keyOf = (s, lang = TL.lang) => crypto.createHash('sha1').update(JSON.stringify([s.shot, s.t0, (LAND ? s.camL : s.camP) || s.cam, s.cctv ? 1 : 0, s.props, s.scareAt, s.n, s.dur, W, H, RS, lang, TL.vo.filter(v => v.aloud && v.at < s.start + s.dur && v.at + v.dur > s.start).map(v => [v.k, +(v.at - s.start).toFixed(3)])])).digest('hex').slice(0, 16);

(async () => {
  fs.mkdirSync(cache, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: W, height: H } });
  const errs = []; p.on('pageerror', e => errs.push(String(e))); p.on('console', m => m.type() === 'error' && errs.push(m.text().slice(0, 300)));
  await p.route(/\/nightshift\/_tr\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:8771/nightshift/_tr.html?gfx=cine&lang=' + TL.lang);
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 120000 });
  await p.evaluate(async ([rs, land]) => { window.__W3core.V.rs = rs; window.__W3core.V.rsPin = true; await window.__W3chars.load(); World.init(); if (land) World.setLand(true); }, [RS, LAND]);
  // кадр в странице: World.draw и JPEG (пустая строка — прогрев без снимка)
  const draw = (id, o, snap) => p.evaluate(([id, o, snap, land]) => {
    const c = document.getElementById('c'), g = c.getContext('2d');
    if (o.wp) { const q = World.projectAt(id, o.wp[0]); o.cx = land ? .5 + (q[0] - .5) * (16 / 9) / (9 / 16) : q[0]; o.cx = land ? .5 + (o.cx - .5) * (9 / 16) / (16 / 9) : o.cx; o.cy = q[1] + (o.wp[1] || 0); delete o.wp; }
    const ok = World.draw(g, c.width, c.height, id, o); if (!ok) { g.fillStyle = '#000'; g.fillRect(0, 0, c.width, c.height); console.error('нет кадра ' + id); }
    return snap ? c.toDataURL('image/jpeg', snap) : ''; }, [id, o, snap, LAND]);
  const save = (file, url) => fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));

  const segs = TL.segs.filter(s => s.shot && (!ONLY || ONLY.includes(s.id)));
  if (PREVIEW) {
    const dir = path.join(cache, 'preview'); fs.mkdirSync(dir, { recursive: true }); const list = [];
    for (const s of segs) {
      const picks = [0, Math.floor(s.n / 2), s.n - 1].filter((v, i, a) => a.indexOf(v) === i);
      for (const fi of picks) {
        const sm = { v: 0 };
        for (let w = Math.max(0, fi - 6); w < fi; w++) await draw(s.shot, frameOpt(s, w, sm), 0);   // прогрев: позы и взгляд «доигрывают»
        const f = path.join(dir, `${s.id}_${fi}.jpg`); save(f, await draw(s.shot, frameOpt(s, fi, sm), .85)); list.push({ id: s.id, shot: s.shot, fi, f, t: +(s.start + fi / FPS).toFixed(2) });
      }
      process.stdout.write(s.id + ' ');
    }
    fs.writeFileSync(path.join(dir, 'list.json'), JSON.stringify(list));
    console.log('\npreview', list.length, errs.length ? 'ERR ' + errs.slice(0, 3).join(' | ') : '');
    await b.close(); return;
  }
  let total = 0; const t0 = Date.now(), idxPath = path.join(cache, TL.fmt + '_' + TL.lang + '.json');
  const index = fs.existsSync(idxPath) ? JSON.parse(fs.readFileSync(idxPath)) : {};
  for (const s of TL.segs) if (s.shot) index[s.id] = keyOf(s);
  // --reuse ИНДЕКС --probe ПРОБА: одинаковые на обоих языках сегменты берём из готового кэша другого языка (probe.js)
  const reuse = opt('reuse') ? JSON.parse(fs.readFileSync(opt('reuse'))) : null, same = opt('probe') ? JSON.parse(fs.readFileSync(opt('probe'))).neutral : [];
  if (reuse) for (const id of same) if (reuse[id] && fs.existsSync(path.join(cache, reuse[id], 'done.json'))) index[id] = reuse[id];
  fs.writeFileSync(idxPath, JSON.stringify(index, null, 1));   // comp.js берёт кадры сегментов по этому списку
  for (const s of segs) {
    const key = index[s.id], dir = path.join(cache, key), done = path.join(dir, 'done.json');
    if (fs.existsSync(done)) { console.log('кэш', s.id, key); continue; }
    fs.mkdirSync(dir, { recursive: true });
    const held = !!s.cctv || /^cam(\d|_m2)/.test(s.shot), files = [], sm = { v: 0 }; let last = null, lastSlot = -1;
    for (let w = -10; w < 0; w++) await draw(s.shot, Object.assign(frameOpt(s, 0, sm), { t: Math.max(0, (s.t0 || 0) + w / FPS), settle: w === -10 }), 0);   // прогрев перед первым кадром
    for (let i = 0; i < s.n; i++) {
      const o = frameOpt(s, i, sm); o.settle = false;
      const slot = held ? Math.floor((i / FPS) * 12 + 1e-6) : i;
      if (held && slot === lastSlot && last) { files.push(last); continue; }
      const f = String(i).padStart(5, '0') + '.jpg'; save(path.join(dir, f), await draw(s.shot, o, .93)); files.push(f); last = f; lastSlot = slot; total++;
    }
    fs.writeFileSync(done, JSON.stringify({ id: s.id, shot: s.shot, files }));
    const el = (Date.now() - t0) / 1000; console.log(`${s.id} ${key} ${s.n} кадров, всего ${total} за ${el.toFixed(0)} с (${(el / Math.max(1, total)).toFixed(2)} с/кадр)`);
  }
  if (errs.length) console.log('ERR', errs.slice(0, 5).join('\n'));
  await b.close();
})();
