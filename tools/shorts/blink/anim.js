// «Don't blink back» — самостоятельная хоррор-анимация (не про игры): ночная улица из бумажных силуэтов, тёплые окна, один красный акцент.
// Всё рисуется кодом на холсте 1080×1920, 24 к/с; сюжет и реплики — tools/shorts/blink/lines.json (голос: games/hush-till-six/tools/shorts/dub.py).
//   node tools/shorts/blink/anim.js OUT.mp4 [--vo ПАПКА с n1.wav…] [--png DIR --at 2,9,15]
// Пишет OUT.json с событиями звука → games/hush-till-six/tools/shorts/mix.py OUT.mp4 ПАПКА_ГОЛОСА [язык].
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), { spawn, execFileSync } = require('child_process');
const args = process.argv.slice(2), opt = n => { const i = args.indexOf('--' + n); return i < 0 ? null : args[i + 1]; };
const outMp4 = args[0], FPS = 24, VO = opt('vo'), PNG = opt('png'), AT = opt('at') ? opt('at').split(',').map(Number) : null;
const dur = (k, def) => { try { return +execFileSync('ffprobe', ['-v', '0', '-show_entries', 'format=duration', '-of', 'csv=p=0', path.join(VO, k + '.wav')]).toString().trim() || def; } catch (e) { return def; } };

function spec() {
  const S = [], ev = []; let t = 0;
  const seg = (id, d, o = {}) => { S.push(Object.assign({ id, t0: t, t1: t + d }, o)); t += d; };
  const vo = (k, at, def) => { ev.push({ f: 'vo:' + k, at, g: 1 }); return dur(k, def); };
  const sfx = (f, at, g = .5, d = 0) => ev.push({ f, at, g, d });
  const blink = (at, g = .6) => { sfx('click', at, g, .25); sfx('click', at + .5, g, .25); };   // два щелчка — «мигнул дважды»
  let d = vo('n1', .5, 6.5); seg('street', Math.max(6.4, d + 1.1), { b: d * .78 + .5 }); blink(S[0].b);
  d = vo('n2', t + .3, 5); seg('porch', Math.max(4.6, d + 1.0), { b: d * .8 + .3 }); blink(S[1].t0 + S[1].b, .8);
  d = vo('n3', t + .3, 4); seg('all', Math.max(4.2, d + 1.2), { b: .9 }); for (let i = 0; i < 7; i++) blink(S[2].t0 + .9 + i * .22, .45);
  d = vo('n4', t + .5, 3); seg('mine', Math.max(3.6, d + 1.1)); sfx('sub_presence', S[3].t0 + .3, .7, 3);
  d = vo('n5', t + .4, 6); seg('up', Math.max(6.6, d + 1.2)); for (let i = 0; i < 4; i++) blink(S[4].t0 + .7 + i * 1.6, .7); sfx('heart', S[4].t0 + 1.5, .5, 5);
  sfx('reverse_swell', t - 1.25, .7, 1.3);
  d = vo('n6', t + .9, 2); seg('alone', Math.max(4.4, d + 2.9)); sfx('boom', S[5].t0, .7, 2); blink(S[5].t1 - 1.25, .9);
  d = vo('n7', t + .8, 3.5); seg('outro', Math.max(5.2, d + 2.2)); sfx('static', S[6].t0 - .05, .5, .35);
  ev.push({ f: 'drone_dread', at: 0, g: .55, d: t, loop: 1 }, { f: 'room_tone', at: 0, g: .4, d: t, loop: 1 }, { f: 'tension', at: S[3].t0, g: .35, d: t - S[3].t0 - 4 });
  return { name: 'blink', dur: t, segs: S, ev };
}

function pageMain(SP) {
  const o = document.getElementById('o'), g = o.getContext('2d'), W = 1080, H = 1920;
  const BONE = '#ece8dc', RED = '#e8262e', WARM = '#ffc866';
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const noise = document.createElement('canvas'); noise.width = noise.height = 256;
  { const n = noise.getContext('2d'), dd = n.createImageData(256, 256); for (let i = 0; i < dd.data.length; i += 4) { const v = rnd() * 255; dd.data[i] = dd.data[i + 1] = dd.data[i + 2] = v; dd.data[i + 3] = 255; } n.putImageData(dd, 0, 0); }
  const cl = (x, a, b) => Math.max(a, Math.min(b, x)), sm = (a, b, x) => { const t = cl((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }, eo = x => 1 - Math.pow(1 - cl(x, 0, 1), 3), lerp = (a, b, k) => a + (b - a) * k;
  const F = px => `${px}px "NSUI", "Arial Narrow", Arial, sans-serif`;
  function text(s, x, y, px, col, q = {}) { g.save(); g.font = F(px); g.textAlign = q.al || 'center'; g.textBaseline = 'middle'; g.globalAlpha = q.a ?? 1; if (q.ls) g.letterSpacing = q.ls + 'px';
    if (q.gl) { g.globalCompositeOperation = 'lighter'; g.fillStyle = 'rgba(232,38,46,.85)'; g.fillText(s, x - q.gl, y); g.fillStyle = 'rgba(60,180,255,.7)'; g.fillText(s, x + q.gl, y); g.globalCompositeOperation = 'source-over'; }
    g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 16; g.fillStyle = col; g.fillText(s, x, y); g.restore(); }
  const fit = (s, px, max) => { g.font = F(px); const w = g.measureText(s).width; return w > max ? Math.floor(px * max / w) : px; };
  // «мигнул дважды»: 1 — горит, 0 — нет; два коротких включения
  const two = (t, t0) => (t >= t0 && t < t0 + .2) || (t >= t0 + .5 && t < t0 + .7) ? 1 : 0;
  function glow(x, y, r, col, a) { if (a <= 0) return; g.save(); g.globalCompositeOperation = 'lighter'; const gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, col); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.globalAlpha = a; g.fillStyle = gr; g.fillRect(x - r, y - r, r * 2, r * 2); g.restore(); }
  function sky(T, k = 1) { const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, '#070b1c'); gr.addColorStop(.38, '#131c3f'); gr.addColorStop(.66, '#2b3a72'); gr.addColorStop(.8, '#3a4a86'); gr.addColorStop(1, '#141a36'); g.fillStyle = gr; g.fillRect(-200, -200, W + 400, H + 400);
    seed = 99; for (let i = 0; i < 90; i++) { const x = rnd() * W, y = rnd() * 950, r = .8 + rnd() * 1.9, tw = .45 + .55 * Math.sin(T * (1 + rnd() * 2) + i); g.fillStyle = `rgba(210,220,255,${(.25 + .5 * tw) * k})`; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); }
    glow(880, 600, 420, 'rgba(160,185,255,.6)', .6 * k); g.fillStyle = `rgba(226,232,250,${.96 * k})`; g.beginPath(); g.arc(880, 600, 84, 0, 7); g.fill(); g.fillStyle = '#1c2752'; g.beginPath(); g.arc(916, 578, 79, 0, 7); g.fill(); }   // луна ниже и правее — не под часами
  function fog(T, y, a, sp = 14) { g.save(); for (let i = 0; i < 3; i++) { const yy = y + i * 46, gr = g.createLinearGradient(0, yy - 70, 0, yy + 70); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(.5, `rgba(150,165,215,${a * (1 - i * .25)})`); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(-300 + ((T * sp * (i + 1)) % 200), yy - 70, W + 600, 140); } g.restore(); }
  // дом-силуэт: x — центр, y — земля; ws — окна [dx, dy, w, h, свет 0..1]; lamp — фонарь у двери {on}
  function house(x, y, w, h, col, q = {}) { const roof = q.roof ?? .42, L = x - w / 2, top = y - h;
    g.fillStyle = col; g.fillRect(L, top, w, h); g.beginPath(); g.moveTo(L - w * .06, top + 2); g.lineTo(x + (q.peak || 0) * w, top - h * roof); g.lineTo(L + w * 1.06, top + 2); g.closePath(); g.fill();
    if (q.chim !== 0) g.fillRect(x + w * (q.chim || .22), top - h * roof * .78, w * .09, h * roof * .7);
    for (const [dx, dy, ww, wh, on] of q.ws || []) { const wx = x + dx * w - ww / 2, wy = y - dy * h - wh / 2;
      if (on > 0) { glow(wx + ww / 2, wy + wh / 2, ww * 2.4, 'rgba(255,190,90,.55)', .6 * on); g.fillStyle = `rgba(255,204,110,${.25 + .75 * on})`; } else g.fillStyle = 'rgba(58,70,118,.95)';
      g.fillRect(wx, wy, ww, wh); g.fillStyle = col; g.fillRect(wx + ww / 2 - 2, wy, 4, wh); g.fillRect(wx, wy + wh / 2 - 2, ww, 4); }
    if (q.door !== 0) { const dw = w * .13, dh = h * .36, dx = x + w * (q.door || -.12); g.fillStyle = 'rgba(20,24,42,.95)'; g.fillRect(dx - dw / 2, y - dh, dw, dh);
      const lx = dx + dw * .95, ly = y - dh - h * .05, on = q.lamp || 0; g.fillStyle = on ? WARM : 'rgba(110,120,160,.95)'; g.beginPath(); g.arc(lx, ly, Math.max(6, w * .026), 0, 7); g.fill();
      if (on) { glow(lx, ly, w * .9, 'rgba(255,196,96,.75)', .85 * on); g.save(); g.globalCompositeOperation = 'lighter'; g.globalAlpha = .16 * on; g.fillStyle = WARM; g.beginPath(); g.moveTo(lx, ly); g.lineTo(lx - w * .34, y + h * .12); g.lineTo(lx + w * .34, y + h * .12); g.closePath(); g.fill(); g.restore(); } } }
  function tree(x, y, s, col) { g.fillStyle = col; g.fillRect(x - 5 * s, y - 150 * s, 10 * s, 150 * s); seed = Math.floor(x) + 3; for (let i = 0; i < 7; i++) { g.beginPath(); g.arc(x + (rnd() - .5) * 120 * s, y - 150 * s - rnd() * 130 * s, (40 + rnd() * 42) * s, 0, 7); g.fill(); } }
  function wires(y, col) { g.strokeStyle = col; g.lineWidth = 2.5; for (let k = 0; k < 2; k++) { g.beginPath(); for (let x = -100; x <= W + 100; x += 20) { const u = ((x + 100) % 400) / 400, yy = y + k * 22 + Math.sin(u * Math.PI) * 34; x === -100 ? g.moveTo(x, yy) : g.lineTo(x, yy); } g.stroke(); } g.fillStyle = col; for (let x = -100; x <= W + 100; x += 400) g.fillRect(x - 7, y - 30, 14, 520); }
  function ground(y, col) { g.fillStyle = col; g.fillRect(-300, y, W + 600, H); }
  function road(y) { const gr = g.createLinearGradient(0, y, 0, H); gr.addColorStop(0, '#1a2244'); gr.addColorStop(.35, '#10152c'); gr.addColorStop(1, '#05070f'); g.fillStyle = gr; g.fillRect(-300, y, W + 600, H); g.fillStyle = 'rgba(200,212,245,.26)'; for (let i = 0; i < 5; i++) { const yy = y + 110 + i * i * 34, ww = 60 + i * 44; g.fillRect(W / 2 - ww / 2, yy, ww, 7 + i * 3); } }
  // фигура в окне: вытянутая голова набок, узкие плечи, длинные пальцы на стекле, две бледные точки
  function figure(cx, by, s, eyes = 0, col = '#05060a') { g.save(); g.translate(cx, by); g.scale(s, s); g.fillStyle = col;
    g.beginPath(); g.moveTo(-120, 0); g.quadraticCurveTo(-118, -150, -52, -178); g.lineTo(52, -178); g.quadraticCurveTo(118, -150, 120, 0); g.closePath(); g.fill();
    g.save(); g.translate(8, -190); g.rotate(.2); g.fillRect(-17, -6, 34, 40); g.beginPath(); g.ellipse(6, -78, 50, 84, 0, 0, 7); g.fill();
    if (eyes > 0) { g.fillStyle = `rgba(236,232,220,${eyes})`; g.beginPath(); g.arc(-14, -84, 5.5, 0, 7); g.arc(26, -84, 5.5, 0, 7); g.fill(); } g.restore();
    g.strokeStyle = col; g.lineCap = 'round'; g.lineWidth = 9; for (const sd of [-1, 1]) for (let i = 0; i < 5; i++) { g.beginPath(); g.moveTo(sd * (150 + i * 4), -40); g.lineTo(sd * (128 + i * 16), -150 - (i === 2 ? 34 : i % 2 ? 22 : 6)); g.stroke(); } g.restore(); }
  // ряд домов через улицу; lit(i) → {lamp, win}
  const ROW = [[110, 250, 300, .4, -.1], [330, 230, 350, .5, .12], [540, 270, 320, .36, -.14], [760, 240, 370, .46, .1], [980, 260, 310, .42, -.12]];
  function row(y, col, lit, sc = 1) { ROW.forEach(([x, w, h, roof, door], i) => { const L = lit(i) || {}; house(x, y, w * sc, h * sc, col, { roof, door, lamp: L.lamp || 0, peak: (i % 2 ? .08 : -.06), ws: [[-.24, .62, w * .16 * sc, h * .2 * sc, L.win || 0], [.22, .62, w * .16 * sc, h * .2 * sc, L.win2 || 0], [.24, .26, w * .15 * sc, h * .18 * sc, 0]] }); }); }
  function post(T, k = 1) { g.save(); g.globalAlpha = .09 * k; g.globalCompositeOperation = 'overlay'; seed = 500 + Math.floor(T * 24) * 13; for (let y = 0; y < H; y += 512) for (let x = 0; x < W; x += 512) g.drawImage(noise, (rnd() * 100) | 0, (rnd() * 100) | 0, 150, 150, x, y, 512, 512); g.restore();
    const r = g.createRadialGradient(W / 2, H / 2, 420, W / 2, H / 2, 1220); r.addColorStop(0, 'rgba(0,0,0,0)'); r.addColorStop(1, 'rgba(0,0,0,.6)'); g.fillStyle = r; g.fillRect(0, 0, W, H); }
  function cam(z, dx, dy, T, fn) { const j = Math.floor(T * 8); seed = 31 + j; const bx = (rnd() - .5) * 2.4, by = (rnd() - .5) * 2.4;   // лёгкое «дыхание» кадра, как у покадровой бумажной анимации
    g.save(); g.translate(W / 2 + dx + bx, H / 2 + dy + by); g.scale(z, z); g.translate(-W / 2, -H / 2); fn(); g.restore(); }
  const clockTxt = (a, gl = 0) => { text('3:07', W / 2, 250, 210, BONE, { a, gl, ls: 6 }); text('A M', W / 2, 372, 44, 'rgba(236,232,220,.6)', { a, ls: 18 }); };

  function draw(T) {
    const sg = SP.segs.find(s => T >= s.t0 && T < s.t1) || SP.segs[SP.segs.length - 1], lt = T - sg.t0, D = sg.t1 - sg.t0, k = lt / D;
    g.setTransform(1, 0, 0, 1, 0, 0); g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    if (sg.id === 'street') { const b = two(lt, sg.b);
      cam(1 + .1 * k, 0, -30 * k, T, () => { sky(T); row(1150, '#1b2448', i => ({ win: i % 2 ? .18 : 0 }), .8); wires(760, '#0c1124'); tree(40, 1360, 1.9, '#0e1430'); tree(1050, 1360, 1.7, '#0e1430'); row(1360, '#0a0e22', i => i === 2 ? { lamp: b } : (i === 0 ? { win2: .3 } : i === 4 ? { win: .22 } : null), 1.32); road(1360); fog(T, 1395, .16); });
      clockTxt(eo((lt - .3) / .6), lt < .7 ? 12 : 0); if (lt > sg.b - .1 && lt < sg.b + 1.6) text('TWICE.', W / 2, 1560, 96, BONE, { a: eo((lt - sg.b - .55) / .25), ls: 14 }); }
    if (sg.id === 'porch') { const b = two(lt, sg.b);
      cam(1.04 + .05 * k, 0, 0, T, () => { sky(T, .9); row(1180, '#0d1228', i => i === 2 ? { lamp: .0 } : (i === 4 ? { win: .2 } : null), 1.05); road(1180); fog(T, 1215, .14);
        const lx = 190, ly = 560; g.fillStyle = '#04060d'; g.fillRect(lx - 6, 250, 12, ly - 290); g.fillRect(lx - 46, ly - 66, 92, 26); g.fillStyle = b ? WARM : '#5a6288'; g.beginPath(); g.arc(lx, ly, 34, 0, 7); g.fill();   // свой фонарь за стеклом
        if (b) { glow(lx, ly, 760, 'rgba(255,196,96,.85)', .95); g.save(); g.globalCompositeOperation = 'lighter'; g.globalAlpha = .12; g.fillStyle = WARM; g.beginPath(); g.moveTo(lx, ly); g.lineTo(lx - 360, 1500); g.lineTo(lx + 620, 1500); g.closePath(); g.fill(); g.restore(); }
        const fc = b ? '#2a1c10' : '#04050b'; g.fillStyle = fc; g.fillRect(-100, -100, 150, H + 200); g.fillRect(W - 50, -100, 150, H + 200); g.fillRect(-100, -100, W + 200, 290); g.fillRect(-100, 1490, W + 200, 700); g.fillRect(W / 2 - 16, 190, 32, 1300); g.fillRect(50, 830, W - 100, 30);   // рама окна
        if (b) { g.save(); g.globalCompositeOperation = 'lighter'; g.globalAlpha = .5; g.fillStyle = 'rgba(255,190,100,.5)'; g.fillRect(50, 186, W - 100, 6); g.fillRect(48, 190, 6, 1300); g.fillRect(W / 2 - 18, 190, 4, 1300); g.restore(); } });
      if (lt > sg.b - .2) text('I BLINKED BACK.', W / 2, 1700, fit('I BLINKED BACK.', 92, W - 140), BONE, { a: eo((lt - sg.b - .6) / .3), ls: 8 }); }
    if (sg.id === 'all') { cam(1.12 - .1 * k, 0, 10, T, () => { sky(T); row(1120, '#1b2448', i => ({ lamp: two(lt, sg.b + .11 + i * .22) }), .85); ground(1120, '#141b3a'); fog(T, 1150, .14); wires(760, '#0c1124');
        row(1390, '#0a0e22', i => ({ lamp: two(lt, sg.b + i * .22) }), 1.32); road(1390); fog(T, 1425, .16); });
      clockTxt(eo(lt / .4), lt < .4 ? 10 : 0); text('TONIGHT', W / 2, 470, 60, RED, { a: eo((lt - .3) / .4), ls: 22 }); }
    if (sg.id === 'mine' || sg.id === 'up') { const up = sg.id === 'up', z = up ? 1.25 + .9 * eo(k * .9) : 1 + .25 * eo(k), cy = up ? lerp(120, 560, eo(k * .9)) : 120 * eo(k);
      const cyc = up ? Math.floor((lt - .7) / 1.6) : -1, b = up && lt > .7 ? two((lt - .7) % 1.6, 0) : 0, fig = cyc >= 1 ? 1 : 0;
      cam(z, 0, cy, T, () => { sky(T, .9); row(1150, '#1b2448', i => ({ lamp: up ? 0 : .6, win: i === 3 ? .3 : 0 }), .62); ground(1150, '#141b3a'); fog(T, 1185, .14);
        const hx = 540, hy = 1500, hw = 700, hh = 720, col = '#04050a'; house(hx, hy, hw, hh, col, { roof: .46, door: -.02, chim: .26, ws: [[-.27, .3, 130, 170, 0], [.27, .3, 130, 170, 0]] }); ground(hy, '#070a16');
        const wx = hx - 105, wy = hy - hh * .72 - 130, ww = 210, wh = 260;   // окно наверху
        if (b) { glow(hx, wy + wh / 2, 560, 'rgba(255,190,90,.7)', .8); g.fillStyle = WARM; g.fillRect(wx, wy, ww, wh); if (fig) { g.save(); g.beginPath(); g.rect(wx, wy, ww, wh); g.clip(); figure(hx, wy + wh + 8, .42 + .09 * Math.min(cyc, 3), 0); g.restore(); } }
        else { g.fillStyle = 'rgba(22,26,46,.95)'; g.fillRect(wx, wy, ww, wh); }
        g.fillStyle = col; g.fillRect(hx - 3, wy, 6, wh); g.fillRect(wx, wy + wh / 2 - 3, ww, 6); });
      if (!up) text('EXCEPT MINE.', W / 2, 330, fit('EXCEPT MINE.', 120, W - 140), BONE, { a: eo((lt - D * .45) / .35), gl: lt < D * .45 + .3 ? 8 : 0, ls: 8 });
      else if (lt > D * .62) text('FROM UPSTAIRS.', W / 2, 1720, fit('FROM UPSTAIRS.', 84, W - 160), RED, { a: eo((lt - D * .62) / .4), ls: 8 }); }
    if (sg.id === 'alone') { const off = lt > D - 1.45, a = eo(lt / .25), eyeB = off ? (two(lt, D - 1.25) ? 0 : 1) : 1, sh = lt < .35 ? (1 - lt / .35) * 22 : 0; seed = 7 + Math.floor(T * 24);
      cam(1.02 + .1 * k, (rnd() - .5) * sh, (rnd() - .5) * sh, T, () => { const wx = 150, wy = 330, ww = 780, wh = 1160;
        if (!off) { g.fillStyle = `rgba(255,${Math.round(lerp(196, 120, k))},${Math.round(lerp(100, 70, k))},${a})`; g.fillRect(wx, wy, ww, wh); glow(W / 2, wy + wh / 2, 1100, 'rgba(255,150,80,.6)', .5 * a); }
        g.save(); g.beginPath(); g.rect(wx, wy, ww, wh); g.clip(); figure(W / 2, wy + wh + 30, 2.5 + .25 * k, off ? eyeB : .95 * a, '#030305'); g.restore();
        g.fillStyle = '#030305'; g.fillRect(W / 2 - 13, wy, 26, wh); g.fillRect(wx, wy + wh * .47, ww, 26); g.lineWidth = 44; g.strokeStyle = '#030305'; g.strokeRect(wx, wy, ww, wh); });
      if (lt > 1.0 && !off) text('I LIVE ALONE.', W / 2, 1690, fit('I LIVE ALONE.', 120, W - 140), BONE, { a: eo((lt - 1.0) / .3), ls: 8 }); }
    if (sg.id === 'outro') { const a = eo(lt / .5); seed = 5; glow(W / 2, 700, 900, 'rgba(232,38,46,.35)', .25 * a);
      const eb = two(lt % 2.6, 1.6) ? 0 : 1; g.fillStyle = `rgba(236,232,220,${.8 * a * eb})`; g.beginPath(); g.arc(W / 2 - 46, 470, 9, 0, 7); g.arc(W / 2 + 46, 470, 9, 0, 7); g.fill();
      text("DON'T", W / 2, 720, 230, BONE, { a, gl: (1 - a) * 26, ls: 10 }); text('BLINK BACK', W / 2, 930, fit('BLINK BACK', 190, W - 110), RED, { a: eo((lt - .25) / .5), ls: 8 });
      const b = eo((lt - 1.1) / .45); g.globalAlpha = b; g.fillStyle = RED; g.fillRect(190, 1210, W - 380, 160); g.globalAlpha = 1; text('SUBSCRIBE', W / 2, 1292, 96, '#fff', { a: b, ls: 12 });
      text('before it blinks back', W / 2, 1440, 52, BONE, { a: eo((lt - 1.6) / .5) * .9, ls: 3 }); text('a new night story — soon', W / 2, 1530, 40, 'rgba(236,232,220,.6)', { a: eo((lt - 2.0) / .5) });
      text('YOMPIX', W / 2, 1770, 40, 'rgba(236,232,220,.55)', { a: eo((lt - 2.3) / .5), ls: 16 }); }
    post(T, sg.id === 'outro' ? 1.4 : 1);
    if (sg.id !== 'street' && lt < .12) { g.fillStyle = `rgba(0,0,0,${1 - lt / .12})`; g.fillRect(0, 0, W, H); }   // короткая склейка через чёрное
    if (T < .6) { g.fillStyle = `rgba(0,0,0,${1 - T / .6})`; g.fillRect(0, 0, W, H); }
  }
  window.__frame = T => { draw(T); return o.toDataURL('image/jpeg', .9); };
}

(async () => {
  const SP = spec(); fs.writeFileSync(outMp4.replace(/\.mp4$/, '') + '.json', JSON.stringify(SP, null, 1));
  const font = fs.readFileSync(path.join(__dirname, '../../../games/hush-till-six/web/fonts/ns-ui.woff')).toString('base64');
  const br = await chromium.launch(); const p = await br.newPage({ viewport: { width: 1280, height: 800 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 400)));
  await p.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>@font-face{font-family:NSUI;src:url(data:font/woff;base64,${font})}</style></head><body style="margin:0;background:#000"><canvas id="o" width="1080" height="1920"></canvas></body></html>`);
  await p.evaluate(() => document.fonts.load('40px NSUI'));
  await p.evaluate('(' + pageMain.toString() + ')(' + JSON.stringify(SP) + ')');
  const shot = async T => Buffer.from((await p.evaluate(t => window.__frame(t), T)).split(',')[1], 'base64');
  if (PNG) { fs.mkdirSync(PNG, { recursive: true }); const Q = SP.segs, AUTO = [Q[0].t0 + Q[0].b + .08, Q[1].t0 + Q[1].b - .5, Q[1].t0 + Q[1].b + .08, Q[2].t0 + Q[2].b + .5, Q[3].t1 - .4, Q[4].t0 + .78, Q[4].t0 + .7 + 1.6 * 2 + .08, Q[4].t0 + .7 + 1.6 * 3 + .08, Q[5].t0 + 1.6, Q[5].t1 - .9, Q[6].t0 + 3.2].map(x => +x.toFixed(2));
    for (const T of AT || AUTO) fs.writeFileSync(path.join(PNG, 'blink_' + T + '.jpg'), await shot(T)); await br.close(); console.log('кадры', PNG, 'длина', SP.dur.toFixed(1), SP.segs.map(s => s.id + '@' + s.t0.toFixed(1)).join(' ')); return; }
  const ff = spawn('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-r', String(FPS), outMp4], { stdio: ['pipe', 'inherit', 'inherit'] });
  const N = Math.round(SP.dur * FPS);
  for (let i = 0; i < N; i++) { const b = await shot(i / FPS); if (!ff.stdin.write(b)) await new Promise(r => ff.stdin.once('drain', r)); if (i % 120 === 0) console.log('кадр', i, '/', N); }
  ff.stdin.end(); await new Promise(r => ff.on('close', r)); await br.close(); console.log('готово', outMp4, SP.dur.toFixed(1) + ' с');
})();
