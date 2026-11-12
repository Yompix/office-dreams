// Вертикальные ролики (YouTube Shorts, 1080×1920, 24 к/с) из кадров игры: камера наблюдения сверху, крупные надписи, «найди странность».
//   node tools/shorts/short.js spot|rules OUT.mp4 [--vo ПАПКА с a1.wav…] [--png DIR --at 1.5,8,20]   (сервер: python3 -m http.server 8771 из папки игры)
// 3D рисует сама игра (World.draw, «Ретро», 12 к/с — как запись с камеры); надписи, таймер, «увеличение», помехи — здесь; звук — tools/shorts/mix.py.
// Правило канала: без концовок, смертей и лица Жильца (posts/README.md → Leak-safe).
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), { spawn, execFileSync } = require('child_process');
const args = process.argv.slice(2), opt = n => { const i = args.indexOf('--' + n); return i < 0 ? null : args[i + 1]; };
const [name, outMp4] = args, FPS = 24, VO = opt('vo'), PNG = opt('png'), AT = opt('at') ? opt('at').split(',').map(Number) : null;
const dur = (k, def) => { try { return +execFileSync('ffprobe', ['-v', '0', '-show_entries', 'format=duration', '-of', 'csv=p=0', path.join(VO, k + '.wav')]).toString().trim(); } catch (e) { return def; } };

function spec() {
  const S = [], ev = []; let t = 0;
  const seg = (d, o) => { S.push(Object.assign({ t0: t, t1: t + d }, o)); t += d; };
  const vo = (k, at, def) => { const d = dur(k, def); ev.push({ f: 'vo:' + k, at, g: 1 }); return d; };
  const sfx = (f, at, g = .5, d = 0) => ev.push({ f, at, g, d });
  if (name === 'spot') {
    const base = { clk: 1290 }, cam = { shot: 'cam03', cam: 'CAM 03', place: 'LOBBY · LIFTS' };
    const d1 = vo('a1', .25, 4.2); sfx('static', 0, .5, .35);
    seg(Math.max(3.4, d1 + .25), Object.assign({ kind: 'hook', props: base, title: ['SPOT THE', 'ANOMALY'], cap: 'Night shift. Camera 03.' }, cam));
    const R = [
      { props: { palmMove: 1 }, at: [.535, .53, .13], ans: 'THE PALM MOVED', sub: 'Nobody is in the lobby.', vo: null, sf: ['metal_groan', .35] },
      { props: { lift: '1', liftOpen: 1, liftBoth: 1 }, at: [.60, .50, .30], ans: 'BOTH LIFTS OPENED', sub: 'Nobody called them.', vo: 'a3', sf: ['ding', .7] },
      { props: { frank02: 1 }, at: [.215, .60, .12], ans: 'SOMEONE IS STANDING THERE', sub: 'You are alone in the building.', vo: 'a4', sf: ['sub_presence', .8] }];
    R.forEach((r, i) => {
      const n = (i + 1) + ' / 3';
      if (i === 1) vo('a2', t + .1, 1);
      seg(1.5, Object.assign({ kind: 'before', props: base, n }, cam));
      sfx('static', t - .08, .55, .3);
      for (let k = 0; k < 5; k++) sfx('tick', t + .35 + k * .5, .5, .2);
      seg(2.7, Object.assign({ kind: 'after', props: Object.assign({}, base, r.props), n, cut: 1 }, cam));
      sfx(r.sf[0], t, r.sf[1], 2.4); sfx('boom', t, .5, 1.5);
      const dv = r.vo ? vo(r.vo, t + .35, 2.4) : 0;
      seg(Math.max(2.3, dv + .7), Object.assign({ kind: 'reveal', props: Object.assign({}, base, r.props), n, at: r.at, ans: r.ans, sub: r.sub }, cam));
    });
    sfx('reverse_swell', t - 1.2, .6, 1.3); sfx('static', t - .05, .6, .4);
    const d5 = vo('a5', t + .5, 3);
    seg(Math.max(4.2, d5 + 1.4), { kind: 'outro', line: 'Log it before 6 AM.' });
  } else {
    const d1 = vo('b1', .3, 3); sfx('static', 0, .5, .35);
    seg(Math.max(3.2, d1 + .7), { kind: 'hook', shot: 'cam02', props: { clk: 1305 }, cam: 'CAM 02', place: 'LOBBY', title: ['4 RULES', 'OF THE NIGHT SHIFT'], cap: 'Horizon Business Center. 21:45.' });
    const R = [
      { vo: 'b2', def: 6, shot: 'cam07', props: { l7: 1, clk: 180 }, cam: 'CAM 07', place: 'FLOOR 7', big: ['AT 3:00 ON FLOOR 7', 'THE LIGHTS COME ON.'], red: 'DO NOT GO UP.', sf: ['fluor_flicker', .6] },
      { vo: 'b3', def: 4, shot: 'cam03', props: { lift: '1', liftOpen: 1, clk: 75 }, cam: 'CAM 03', place: 'LIFTS', big: ['THE ELEVATOR', 'CAME BY ITSELF.'], red: 'STAY OUT.', sf: ['ding', .7] },
      { vo: 'b4', def: 3.6, shot: 'cam01', props: { lily01: 1, clk: 20 }, cam: 'CAM 01', place: 'ENTRANCE', big: ['AFTER MIDNIGHT'], red: 'LET NO ONE IN.', mark: [.585, .2, .055], sf: ['glass_tap', .7] },
      { vo: 'b5', def: 4, nosig: 1, cam: 'CAM 13', place: '— — —', big: ['CAMERA 13'], red: 'NEVER TURN IT ON.', sf: ['static', .5] }];
    R.forEach((r, i) => {
      sfx('static', t - .06, .55, .3); sfx('boom', t + .05, .45, 1.5); sfx(r.sf[0], t + .5, r.sf[1], 2.5);
      const dv = vo(r.vo, t + .45, r.def);
      seg(Math.max(3.6, dv + 1.0), Object.assign({ kind: 'rule', n: i + 1, cut: 1 }, r));
    });
    sfx('reverse_swell', t - 1.2, .6, 1.3); sfx('static', t - .05, .6, .4);
    const d6 = vo('b6', t + .7, 2);
    seg(Math.max(4.2, d6 + 2.2), { kind: 'outro', line: 'Can you keep all four?' });
  }
  ev.push({ f: 'drone_dread', at: 0, g: .5, d: t, loop: 1 }, { f: 'room_tone', at: 0, g: .35, d: t, loop: 1 }, { f: 'heart', at: Math.max(0, t - 9), g: .35, d: 5 });
  return { name, dur: t, segs: S, ev };
}

function pageMain(SP) {
  const c = document.getElementById('c'), cg = c.getContext('2d'), o = document.getElementById('o'), g = o.getContext('2d'), W = 1080, H = 1920;
  const BONE = '#ece8dc', RED = '#e8262e', GOLD = '#f2c21b', DIM = 'rgba(236,232,220,.55)';
  const FY = 500, FH = 608;   // окно камеры
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const noise = document.createElement('canvas'); noise.width = noise.height = 256;
  { const n = noise.getContext('2d'), d = n.createImageData(256, 256); for (let i = 0; i < d.data.length; i += 4) { const v = rnd() * 255; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; } n.putImageData(d, 0, 0); }
  const cl = (x, a, b) => Math.max(a, Math.min(b, x)), sm = (a, b, x) => { const t = cl((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }, eo = x => 1 - Math.pow(1 - cl(x, 0, 1), 3);
  const F = (px, w = 'ui') => `${px}px "${w === 'logo' ? 'NSLogo' : 'NSUI'}", "Arial Narrow", Arial, sans-serif`;
  function text(s, x, y, px, col, o2 = {}) { g.save(); g.font = F(px, o2.font); g.textAlign = o2.al || 'center'; g.textBaseline = 'middle'; g.globalAlpha = o2.a ?? 1;
    if (o2.ls) g.letterSpacing = o2.ls + 'px'; if (o2.sh !== 0) { g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 18; }
    if (o2.gl) { g.globalCompositeOperation = 'lighter'; g.fillStyle = 'rgba(232,38,46,.9)'; g.fillText(s, x - o2.gl, y); g.fillStyle = 'rgba(40,200,255,.8)'; g.fillText(s, x + o2.gl, y); g.globalCompositeOperation = 'source-over'; }
    g.fillStyle = col; g.fillText(s, x, y); g.restore(); }
  const fit = (s, px, max, font) => { g.font = F(px, font); const w = g.measureText(s).width; return w > max ? Math.floor(px * max / w) : px; };
  let last3 = -1, lastSeg = null;
  function feed(sg, T) {   // кадр игры 12 раз в секунду
    const k = Math.floor(T * 12); if (k === last3 && lastSeg === sg) return; const fresh = lastSeg !== sg; last3 = k; lastSeg = sg;
    if (sg.nosig) return;
    const tt = k / 12, pr = { sh: sg.props || {} };
    if (fresh) for (let w = 0; w < 6; w++) World.draw(cg, 1280, 720, sg.shot, { t: tt, T: 100 + tt, settle: w === 0, props: pr });
    World.draw(cg, 1280, 720, sg.shot, { t: tt, T: 100 + tt, props: pr });
  }
  function clock(sg, T) { const m = (((sg.props && sg.props.clk) || 0) + Math.floor(T / 4)) % 1440; return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0') + ':' + String(Math.floor(T * 7) % 60).padStart(2, '0'); }
  function panel(sg, T, lt) {   // окно камеры + подложка
    g.fillStyle = '#050608'; g.fillRect(0, 0, W, H);
    if (!sg.nosig) { g.save(); g.filter = 'blur(46px) brightness(.42) saturate(1.2)'; g.drawImage(c, 180, 0, 920, 720, -260, -40, 1600, H + 80); g.restore(); }
    g.save(); g.beginPath(); g.rect(0, FY, W, FH); g.clip();
    if (sg.nosig) { g.fillStyle = '#0a0b0d'; g.fillRect(0, FY, W, FH); g.globalAlpha = .55; for (let y = FY; y < FY + FH; y += 256) for (let x = 0; x < W; x += 256) g.drawImage(noise, (rnd() * 128) | 0, (rnd() * 128) | 0, 128, 128, x, y, 256, 256); g.globalAlpha = 1;
      const by = FY + ((T * 190) % (FH + 120)) - 60; g.fillStyle = 'rgba(255,255,255,.12)'; g.fillRect(0, by, W, 46); }
    else { const z = 1 + .03 * sm(0, sg.t1 - sg.t0, lt); g.drawImage(c, 0, 0, 1280, 720, W / 2 - W * z / 2, FY + FH / 2 - FH * z / 2, W * z, FH * z); }
    if (sg.cut && lt < .28) { const kk = 1 - lt / .28; for (let i = 0; i < 7; i++) { const y = FY + rnd() * FH, h = 12 + rnd() * 70, dx = (rnd() - .5) * 220 * kk; g.drawImage(o, 0, y, W, h, dx, y, W, h); } g.globalAlpha = .5 * kk; g.drawImage(noise, 0, 0, 256, 256, 0, FY, W, FH); g.globalAlpha = 1; }
    g.fillStyle = 'rgba(0,0,0,.16)'; for (let y = FY; y < FY + FH; y += 4) g.fillRect(0, y, W, 1.6);
    g.restore();
    g.strokeStyle = 'rgba(236,232,220,.28)'; g.lineWidth = 3; g.strokeRect(1.5, FY, W - 3, FH);
    const blink = Math.floor(T * 2) % 2 === 0; g.fillStyle = blink ? RED : 'rgba(232,38,46,.25)'; g.beginPath(); g.arc(44, FY + 44, 11, 0, 7); g.fill();
    text('REC', 66, FY + 46, 30, BONE, { al: 'left', ls: 3 }); text(sg.cam + '  ·  ' + sg.place, 44, FY + FH - 40, 28, BONE, { al: 'left', ls: 2 });
    text(sg.nosig ? '--:--:--' : clock(sg, T), W - 44, FY + 46, 30, BONE, { al: 'right', ls: 2 });
  }
  function fx(T, k = 1) { g.save(); g.globalAlpha = .07 * k; g.globalCompositeOperation = 'overlay'; for (let y = 0; y < H; y += 512) for (let x = 0; x < W; x += 512) g.drawImage(noise, (rnd() * 100) | 0, (rnd() * 100) | 0, 150, 150, x, y, 512, 512); g.restore();
    const r = g.createRadialGradient(W / 2, H / 2, 500, W / 2, H / 2, 1250); r.addColorStop(0, 'rgba(0,0,0,0)'); r.addColorStop(1, 'rgba(0,0,0,.72)'); g.fillStyle = r; g.fillRect(0, 0, W, H); }
  function brand(a = 1) { text('HUSH TILL SIX', W / 2, 96, 40, BONE, { font: 'logo', ls: 6, a: .8 * a }); g.fillStyle = `rgba(232,38,46,${.9 * a})`; g.fillRect(W / 2 - 60, 132, 120, 4); }
  function lines(arr, y, px, col, lt, o2 = {}) { arr.forEach((s, i) => { const a = eo((lt - i * .22) / .32), p = fit(s, px, W - 110, o2.font); text(s, W / 2 + (1 - a) * (i % 2 ? 90 : -90), y + i * px * 1.08, p, col, Object.assign({ a, gl: (1 - a) * 14 }, o2)); }); }
  function draw(T) {
    seed = 1000 + Math.floor(T * 24) * 7;
    const sg = SP.segs.find(s => T >= s.t0 && T < s.t1) || SP.segs[SP.segs.length - 1], lt = T - sg.t0, D = sg.t1 - sg.t0;
    if (sg.kind === 'outro') {
      g.fillStyle = '#050608'; g.fillRect(0, 0, W, H); g.save(); g.globalAlpha = .16; g.filter = 'blur(30px)'; g.drawImage(c, 0, 0, 1280, 720, -700, 300, 2480, 1395); g.restore();
      const a = eo(lt / .5); text('HUSH', W / 2, 700, 230, BONE, { font: 'logo', a, gl: (1 - a) * 30, ls: 10 }); text('TILL SIX', W / 2, 900, fit('TILL SIX', 170, W - 120, 'logo'), RED, { font: 'logo', a: eo((lt - .25) / .5), ls: 10 });
      text(sg.line, W / 2, 1090, 58, BONE, { a: eo((lt - .8) / .5) });
      const b = eo((lt - 1.3) / .5); g.globalAlpha = b; g.fillStyle = RED; g.fillRect(140, 1250, W - 280, 150); g.globalAlpha = 1; text('PLAY FREE IN YOUR BROWSER', W / 2, 1326, fit('PLAY FREE IN YOUR BROWSER', 54, W - 340), '#fff', { a: b, sh: 0, ls: 2 });
      text('yompix.github.io', W / 2, 1470, 56, GOLD, { a: eo((lt - 1.6) / .5) }); text('News & behind the scenes:  t.me/yompixgames', W / 2, 1570, fit('News & behind the scenes:  t.me/yompixgames', 40, W - 120), BONE, { a: eo((lt - 1.9) / .5) * .9 });
      text('18+  ·  horror', W / 2, 1760, 32, DIM, { a: eo((lt - 2.2) / .5) }); fx(T, 1.4); if (lt < .3) { g.globalAlpha = .7 * (1 - lt / .3); g.drawImage(noise, 0, 0, 256, 256, 0, 0, W, H); g.globalAlpha = 1; } return;
    }
    feed(sg, T); panel(sg, T, lt); brand();
    if (sg.kind === 'hook') { lines(sg.title, 250, sg.title[1].length > 10 ? 104 : 150, BONE, lt - .15, { font: 'ui', ls: 4 }); text(sg.cap, W / 2, FY + FH + 110, 52, BONE, { a: eo((lt - .9) / .5) });
      const a = eo((lt - 1.5) / .5); if (SP.name === 'spot') { text('3 rounds. 3 seconds each.', W / 2, FY + FH + 200, 44, GOLD, { a }); } else text('Break one — and you do not see 6 AM.', W / 2, FY + FH + 200, fit('Break one — and you do not see 6 AM.', 44, W - 120), GOLD, { a }); }
    if (sg.kind === 'before' || sg.kind === 'after' || sg.kind === 'reveal') text('ROUND ' + sg.n, W / 2, 250, 64, DIM, { ls: 8 });
    if (sg.kind === 'before' || sg.kind === 'after') { const R = SP.segs.filter(s => s.kind === 'reveal'), y0 = 1430; text('SHIFT LOG', 110, y0, 34, DIM, { al: 'left', ls: 8 }); g.fillStyle = 'rgba(236,232,220,.25)'; g.fillRect(110, y0 + 30, W - 220, 2);
      R.forEach((r, i) => { const done = T >= r.t1 - .01, cur = !done && T >= (i ? R[i - 1].t1 : 0), y = y0 + 92 + i * 84; text(String(i + 1).padStart(2, '0'), 110, y, 44, done ? RED : DIM, { al: 'left' });
        text(done ? r.ans : cur ? '? ? ?' : '· · ·', 200, y, fit(done ? r.ans : '? ? ?', 44, W - 320), done ? BONE : cur ? GOLD : DIM, { al: 'left', ls: 2 }); }); }
    if (sg.kind === 'before') { text('REMEMBER THIS', W / 2, 372, 96, BONE, { font: 'ui', a: eo(lt / .25), ls: 3 }); g.fillStyle = 'rgba(60,220,120,.9)'; g.fillRect(0, FY - 8, W, 8); g.fillRect(0, FY + FH, W, 8); text('everything is normal', W / 2, FY + FH + 110, 48, 'rgba(120,235,160,.95)'); }
    if (sg.kind === 'after') { text('WHAT CHANGED?', W / 2, 372, 108, GOLD, { font: 'ui', a: eo(lt / .2), gl: lt < .3 ? 10 : 0, ls: 3 });
      const k = 1 - cl((lt - .25) / (D - .25), 0, 1), n = Math.ceil(k * 3); g.fillStyle = 'rgba(236,232,220,.15)'; g.fillRect(90, FY + FH + 84, W - 180, 26); g.fillStyle = k < .34 ? RED : GOLD; g.fillRect(90, FY + FH + 84, (W - 180) * k, 26);
      text(String(Math.max(1, n)), W / 2, FY + FH + 300, 300, k < .34 ? RED : BONE, { font: 'ui', a: .92 }); }
    if (sg.kind === 'reveal') { const [ax, ay, ar] = sg.at, px = ax * W, py = FY + ay * FH, pr = ar * W, a = eo(lt / .3), pulse = 1 + .06 * Math.sin(lt * 9);
      g.save(); g.beginPath(); g.rect(0, FY, W, FH); g.clip(); g.fillStyle = `rgba(0,0,0,${.5 * a})`; g.beginPath(); g.rect(0, FY, W, FH); g.arc(px, py, pr * pulse, 0, 7, true); g.fill('evenodd');
      g.strokeStyle = RED; g.lineWidth = 9; g.beginPath(); g.arc(px, py, pr * pulse * (2 - a), 0, 7); g.stroke(); g.restore();
      text(sg.ans, W / 2, 372, fit(sg.ans, 104, W - 90, 'ui'), RED, { font: 'ui', a, gl: (1 - a) * 22, ls: 2 });
      const iw = 620, ih = 620, ix = W / 2 - iw / 2, iy = FY + FH + 70, b = eo((lt - .25) / .35), sw = ar * 1280 * 2.1, sx = cl(ax * 1280 - sw / 2, 0, 1280 - sw), sy = cl(ay * 720 - sw / 2, 0, 720 - sw);   // «увеличение»
      g.save(); g.globalAlpha = b; g.imageSmoothingEnabled = false; g.drawImage(c, sx, sy, sw, Math.min(sw, 720), ix, iy + (1 - b) * 60, iw, ih); g.imageSmoothingEnabled = true; g.fillStyle = 'rgba(0,0,0,.18)'; for (let y = iy; y < iy + ih; y += 5) g.fillRect(ix, y, iw, 2);
      g.strokeStyle = RED; g.lineWidth = 6; g.strokeRect(ix, iy + (1 - b) * 60, iw, ih); g.restore(); text('ENHANCE ×' + (1 / (ar * 2.1)).toFixed(1), ix + 14, iy + 34, 26, RED, { al: 'left', a: b, ls: 2 });
      text(sg.sub, W / 2, iy + ih + 78, fit(sg.sub, 52, W - 100), BONE, { a: eo((lt - .7) / .4) }); }
    if (sg.kind === 'rule') { text('RULE', 150, 300, 60, DIM, { al: 'left', ls: 10 }); text(String(sg.n), W - 150, 300, 330, RED, { font: 'ui', al: 'right', a: eo(lt / .25), gl: lt < .3 ? 16 : 0 }); text('/ 4', W - 96, 380, 54, DIM, { al: 'right' });
      lines(sg.big, FY + FH + 120, 86, BONE, lt - .35, { font: 'ui', ls: 2 });
      const tr = Math.max(1.4, D * .52), a = eo((lt - tr) / .22), y = FY + FH + 150 + sg.big.length * 96, p = fit(sg.red, 118, W - 150, 'ui'); if (a > 0) { g.save(); g.globalAlpha = a; g.fillStyle = RED; const sc = 1 + (1 - a) * .5; g.translate(W / 2, y + 40); g.scale(sc, sc); g.fillRect(-W / 2 + 50, -92, W - 100, 184); g.restore(); text(sg.red, W / 2, y + 44, p, '#fff', { font: 'ui', a, sh: 0, ls: 2 }); }
      if (sg.mark && lt > .9) { const [mx, my, mr] = sg.mark, px = mx * W, py = FY + my * FH, r = mr * W * (1 + .08 * Math.sin(lt * 8)), q = r * .45; g.save(); g.strokeStyle = RED; g.lineWidth = 6; g.globalAlpha = eo((lt - .9) / .3);
        for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) { g.beginPath(); g.moveTo(px + sx * r, py + sy * (r - q)); g.lineTo(px + sx * r, py + sy * r); g.lineTo(px + sx * (r - q), py + sy * r); g.stroke(); } g.restore(); }
      if (sg.nosig) { text('NO SIGNAL', W / 2, FY + FH / 2, 96, BONE, { font: 'ui', a: .5 + .5 * (Math.floor(T * 3) % 2), ls: 6 }); } }
    fx(T);
  }
  window.__frame = T => { draw(T); return o.toDataURL('image/jpeg', .9); };
}

(async () => {
  const SP = spec(); fs.writeFileSync(outMp4.replace(/\.mp4$/, '') + '.json', JSON.stringify(SP, null, 1));
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 1280, height: 800 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 400)));
  const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>@font-face{font-family:NSLogo;src:url(fonts/ns-logo.woff)}@font-face{font-family:NSUI;src:url(fonts/ns-ui.woff)}</style></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><canvas id="c" width="1280" height="720"></canvas><canvas id="o" width="1080" height="1920"></canvas></body></html>`;
  await p.route(/\/web\/_short\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  await p.goto('http://127.0.0.1:' + (process.env.PORT || 8771) + '/web/_short.html?gfx=retro&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(true); await Promise.all([document.fonts.load('40px NSLogo'), document.fonts.load('40px NSUI')]); });
  await p.evaluate('(' + pageMain.toString() + ')(' + JSON.stringify(SP) + ')');
  const shot = async T => Buffer.from((await p.evaluate(t => window.__frame(t), T)).split(',')[1], 'base64');
  if (PNG) { fs.mkdirSync(PNG, { recursive: true }); for (const T of AT || [1]) { await shot(Math.max(0, T - .2)); fs.writeFileSync(path.join(PNG, name + '_' + T + '.jpg'), await shot(T)); } await br.close(); console.log('кадры', PNG, 'длина', SP.dur.toFixed(1)); return; }
  const ff = spawn('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-r', String(FPS), outMp4], { stdio: ['pipe', 'inherit', 'inherit'] });
  const N = Math.round(SP.dur * FPS);
  for (let i = 0; i < N; i++) { const b = await shot(i / FPS); if (!ff.stdin.write(b)) await new Promise(r => ff.stdin.once('drain', r)); if (i % 96 === 0) console.log('кадр', i, '/', N); }
  ff.stdin.end(); await new Promise(r => ff.on('close', r)); await br.close(); console.log('готово', outMp4, SP.dur.toFixed(1) + ' с');
})();
