// «Hush Till Six» — сборка кадров трейлера: 3D из кэша render.js + эффекты игры (зерно, виньетка, камера наблюдения, помехи, бросок)
// + надписи (субтитры, правила, название, где играть) → видео без звука.
//   node tools/trailer/comp.js TIMELINE.json CACHE OUT.mp4 [--from N --to M] [--png DIR --at 12.5,40]
//   --png: только снимки кадров в эти секунды (проверка надписей), без видео.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), { spawn } = require('child_process');
const args = process.argv.slice(2), opt = n => { const i = args.indexOf('--' + n); return i < 0 ? null : args[i + 1]; };
const [tlPath, cache, outMp4] = args;
const TL = JSON.parse(fs.readFileSync(tlPath)), LAND = TL.fmt === 'yt', FPS = TL.fps, W = TL.W, H = TL.H;
const index = JSON.parse(fs.readFileSync(path.join(cache, TL.fmt + '_' + TL.lang + '.json')));
const PNG = opt('png'), AT = opt('at') ? opt('at').split(',').map(Number) : null;

// в странице: всё рисование надписей и эффектов
function pageMain() {
  const c = document.getElementById('c'), g = c.getContext('2d'), W = c.width, H = c.height, LAND = W > H, U = LAND ? H / 1080 : W / 1080;
  const BONE = '#ece8dc', GOLD = '#f2c21b', RED = '#e8262e';
  let seed = 1; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const noise = document.createElement('canvas'); noise.width = noise.height = 256;
  { const n = noise.getContext('2d'), d = n.createImageData(256, 256); seed = 7; for (let i = 0; i < d.data.length; i += 4) { const v = rnd() * 255; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; } n.putImageData(d, 0, 0); }
  const pat = g.createPattern(noise, 'repeat');
  const grain = a => { g.save(); g.globalAlpha = a; g.globalCompositeOperation = 'overlay'; const ox = rnd() * 256, oy = rnd() * 256; g.translate(-ox, -oy); g.fillStyle = pat; g.fillRect(0, 0, W + 256, H + 256); g.restore(); };
  const vignette = (k = .8) => { const r = g.createRadialGradient(W / 2, H / 2, Math.min(W, H) * .3, W / 2, H / 2, Math.max(W, H) * .62); r.addColorStop(0, 'rgba(0,0,0,0)'); r.addColorStop(1, `rgba(0,0,0,${k})`); g.fillStyle = r; g.fillRect(0, 0, W, H); };
  const tear = () => { const y = rnd() * H * .9, h = (30 + rnd() * 110) * U, dx = (rnd() < .5 ? -1 : 1) * (18 + rnd() * 46) * U; g.drawImage(c, 0, y, W, h, dx, y, W, h); g.fillStyle = 'rgba(235,240,235,.22)'; g.fillRect(0, y + h * (.3 + rnd() * .4), W, 3 * U); };
  const cl = (x, a, b) => Math.max(a, Math.min(b, x)), sm = (a, b, x) => { const t = cl((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
  const fade = (t, at, dur, fi = .35, fo = .35) => Math.min(sm(at, at + fi, t), 1 - sm(at + dur - fo, at + dur, t));
  function hud(label, T, clock, US) {   // как cctvHud в engine.js
    g.save(); g.textBaseline = 'top'; g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 6;
    g.strokeStyle = 'rgba(235,240,235,.55)'; g.lineWidth = 4 * US; const m = 46 * US, L = 64 * US;
    g.beginPath(); for (const [x, y, sx, sy] of [[m, m, 1, 1], [W - m, m, -1, 1], [m, H - m, 1, -1], [W - m, H - m, -1, -1]]) { g.moveTo(x + sx * L, y); g.lineTo(x, y); g.lineTo(x, y + sy * L); } g.stroke();
    g.scale(US, US); g.font = '700 40px "Courier New", monospace'; g.fillStyle = 'rgba(235,240,235,.9)';
    const y0 = LAND ? 96 : 204;
    if ((T % 1) < .5) { g.fillStyle = RED; g.beginPath(); g.arc(98, y0 + 18, 13, 0, 7); g.fill(); g.fillStyle = 'rgba(235,240,235,.9)'; }
    g.fillText('REC', 124, y0);
    if (label) g.fillText(label, 84, y0 + 54);
    const s = Math.floor(T) % 60; g.font = '700 34px "Courier New", monospace'; g.fillText('29.10.2026  ' + clock + ':' + String(s).padStart(2, '0'), 84, y0 + 106);
    g.restore();
  }
  // текст с переносом по ширине
  function wrapG(s, maxW) { const out = []; let line = ''; for (const w of s.split(' ')) { const t = line ? line + ' ' + w : w; if (g.measureText(t).width > maxW && line) { out.push(line); line = w; } else line = t; } if (line) out.push(line); return out; }
  function wrap(s, maxW) {   // перенос «по-книжному»: две строки примерно равной длины, без одинокого слова внизу
    if (s.includes('\n')) return s.split('\n');
    if (g.measureText(s).width <= maxW) return [s];
    const w = s.split(' '); let best = null;
    for (let i = 1; i < w.length; i++) { const a = w.slice(0, i).join(' '), b = w.slice(i).join(' '), wa = g.measureText(a).width, wb = g.measureText(b).width; if (wa <= maxW && wb <= maxW && (!best || Math.max(wa, wb) < best[0])) best = [Math.max(wa, wb), [a, b]]; }
    return best ? best[1] : wrapG(s, maxW);
  }
  function spaced(s, x, y, sp, align = 'center') {   // текст с разрядкой (letterSpacing есть не везде)
    g.letterSpacing = sp + 'px'; const w = g.measureText(s).width; g.textAlign = 'left';
    g.fillText(s, align === 'center' ? x - w / 2 + sp / 2 : align === 'right' ? x - w : x, y); g.letterSpacing = '0px'; return w;
  }
  function subs(text, a) {
    g.save(); g.globalAlpha = a; const fs = (LAND ? 50 : 54) * U; g.font = `600 ${fs}px NSUI, Arial`; g.textBaseline = 'alphabetic';
    const maxW = LAND ? W * .7 : 780 * U, lines = wrap(text, maxW), lh = fs * 1.22, yb = LAND ? H - 78 * U : 1240 * U;
    lines.forEach((l, i) => { const y = yb - (lines.length - 1 - i) * lh; g.lineJoin = 'round'; g.strokeStyle = 'rgba(0,0,0,.85)'; g.lineWidth = 7 * U; g.textAlign = 'center';
      g.shadowColor = 'rgba(0,0,0,.8)'; g.shadowBlur = 14 * U; g.strokeText(l, W / 2, y); g.shadowBlur = 0; g.fillStyle = '#f4f1e8'; g.fillText(l, W / 2, y); });
    g.restore();
  }
  function logo(cx, cy, s, a, T, glitch) {   // HUSH / TILL SIX — как на титуле игры
    g.save(); g.globalAlpha = a; g.textAlign = 'center'; g.textBaseline = 'alphabetic';
    const f1 = 240 * s * U, f2 = 175 * s * U, y1 = cy - f2 * .1, y2 = y1 + f2 * .84 + 4 * U;
    const draw = (dx, col1, col2) => { g.font = `400 ${f1}px NSLogo, NSUI`; g.fillStyle = col1; spaced('HUSH', cx + dx, y1, 8 * s * U); g.font = `400 ${f2}px NSLogo, NSUI`; g.fillStyle = col2; spaced('TILL SIX', cx + dx, y2, 3 * s * U); };
    if (glitch > 0) { g.globalCompositeOperation = 'lighter'; g.globalAlpha = a * .55 * glitch; draw(-9 * U * glitch, 'rgba(255,40,60,1)', 'rgba(255,40,60,1)'); draw(9 * U * glitch, 'rgba(60,200,255,1)', 'rgba(60,200,255,1)'); g.globalCompositeOperation = 'source-over'; g.globalAlpha = a; }
    g.shadowColor = 'rgba(242,194,27,.35)'; g.shadowBlur = 0; draw(0, BONE, GOLD);
    g.restore(); return y2;
  }
  function badge18(x, y, r, a) { g.save(); g.globalAlpha = a; g.strokeStyle = RED; g.lineWidth = 5 * U; g.beginPath(); g.arc(x, y, r, 0, 7); g.stroke(); g.fillStyle = BONE; g.font = `700 ${r * .9}px NSUI, Arial`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('18+', x, y + r * .04); g.restore(); }
  // flicker: включается рывками, как плохая лампа
  const flick = (t, at) => { const x = t - at; if (x < 0) return 0; if (x > .9) return 1; const on = [[0, .06], [.13, .17], [.3, .42], [.5, .55], [.62, 2]]; return on.some(([a, b]) => x >= a && x < b) ? 1 : .12; };

  function card(cd, t, T, L) {
    const lt = t - cd.at;
    if (cd.kind === 'place') {
      const a = fade(t, cd.at, cd.dur, .5, .5); g.save(); g.globalAlpha = a; g.textBaseline = 'alphabetic'; g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 16 * U;
      const x = LAND ? 120 * U : W / 2, al = LAND ? 'left' : 'center', y = LAND ? H - 190 * U : 1090 * U;
      g.font = `700 ${(LAND ? 64 : 58) * U}px NSUI`; g.fillStyle = BONE; spaced(cd.lines[0], x, y, 6 * U + lt * 2 * U, al);
      g.font = `600 ${(LAND ? 34 : 34) * U}px NSUI`; g.fillStyle = GOLD; spaced(cd.lines[1], x, y + 56 * U, 5 * U, al); g.restore();
    } else if (cd.kind === 'rule') {
      const a = fade(t, cd.at, cd.dur, .12, .18), k = sm(0, .25, lt); g.save(); g.globalAlpha = a; g.textAlign = 'center'; g.shadowColor = 'rgba(0,0,0,.95)'; g.shadowBlur = 24 * U;
      g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, 0, W, H);
      const cy = LAND ? H * .47 : H * .42, fs = (LAND ? 104 : 96) * U;
      g.font = `600 ${34 * U}px NSUI`; g.fillStyle = GOLD; spaced((L === 'ru' ? 'ПРАВИЛО ' : 'RULE ') + cd.n, W / 2, cy - fs * 1.22, 10 * U);
      g.font = `700 ${fs}px NSUI`; g.fillStyle = BONE; const lines = wrap(cd.lines[0], LAND ? W * .8 : 860 * U);
      lines.forEach((l, i) => spaced(l, W / 2, cy + i * fs * 1.05 + (1 - k) * 14 * U, (3 + 2 * (1 - k)) * U));
      g.fillStyle = GOLD; g.fillRect(W / 2 - 70 * U * k, cy + (lines.length - 1) * fs * 1.05 + 38 * U, 140 * U * k, 4 * U); g.restore();
    } else if (cd.kind === 'big') {
      const a = fade(t, cd.at, cd.dur, .06, .15), jit = lt < .18 ? (1 - lt / .18) : 0; g.save(); g.globalAlpha = a; g.textAlign = 'center'; g.textBaseline = 'middle';
      const fs = (LAND ? 150 : 120) * U, lines = (g.font = `700 ${fs}px NSUI`, wrap(cd.lines[0], LAND ? W * .86 : 900 * U)), cy = H / 2 - (lines.length - 1) * fs * .55;
      lines.forEach((l, i) => { const y = cy + i * fs * 1.08;
        if (jit > 0) { g.globalCompositeOperation = 'lighter'; g.fillStyle = 'rgba(255,40,60,.6)'; spaced(l, W / 2 - 14 * U * jit, y, 6 * U); g.fillStyle = 'rgba(60,200,255,.5)'; spaced(l, W / 2 + 14 * U * jit, y, 6 * U); g.globalCompositeOperation = 'source-over'; }
        g.shadowColor = 'rgba(0,0,0,.95)'; g.shadowBlur = 30 * U; g.fillStyle = BONE; spaced(l, W / 2, y, 6 * U); });
      g.restore();
    } else if (cd.kind === 'hook') {
      const a = fade(t, cd.at, cd.dur, .25, .2); g.save(); g.globalAlpha = a; g.textAlign = 'center'; g.textBaseline = 'alphabetic'; g.shadowColor = 'rgba(0,0,0,.95)'; g.shadowBlur = 22 * U;
      const fs = 66 * U; g.font = `700 ${fs}px NSUI`; let y = 380 * U;
      for (const l of wrap(cd.lines[0], 820 * U)) { g.fillStyle = BONE; spaced(l, W / 2, y, 2 * U); y += fs * 1.12; }
      if (cd.lines2 && cd.lines2[1] && t >= (cd.at2 ?? cd.at)) { const a2 = sm(0, .25, t - (cd.at2 ?? cd.at)); g.globalAlpha = a * a2; g.font = `700 ${fs * 1.25}px NSUI`; g.fillStyle = GOLD; spaced(cd.lines2[1], W / 2, y + 34 * U, 3 * U); }
      g.restore();
    } else if (cd.kind === 'logo') {
      const a = flick(t, cd.at) * fade(t, cd.at, cd.dur, .01, .5), gl = lt < 1.2 ? Math.max(0, 1 - lt / 1.2) : ((T * 7) % 11 < .12 ? 1 : 0);
      const yb = logo(W / 2, LAND ? H * .5 : H * .44, LAND ? 1 : .95, a, T, gl);
      g.save(); g.globalAlpha = fade(t, cd.at + 1.4, cd.dur - 1.4, .8, .5); g.textAlign = 'center'; g.font = `600 ${(LAND ? 40 : 42) * U}px NSUI`; g.fillStyle = 'rgba(236,232,220,.86)';
      const tl = wrap(cd.tag.toUpperCase(), LAND ? W * .7 : 860 * U); tl.forEach((l, i) => spaced(l, W / 2, yb + 110 * U + i * 52 * U, 4 * U)); g.restore();
    } else if (cd.kind === 'phone') {
      const a = 1; g.save(); g.globalAlpha = a; const pw = (LAND ? 300 : 420) * U, ph = pw * 2, x = W / 2 - pw / 2, y = H / 2 - ph / 2 - (LAND ? 0 : 120 * U);
      const gr = g.createLinearGradient(0, y, 0, y + ph); gr.addColorStop(0, '#0d1418'); gr.addColorStop(1, '#05080a'); g.fillStyle = gr; g.strokeStyle = 'rgba(236,232,220,.18)'; g.lineWidth = 3 * U;
      g.beginPath(); g.roundRect(x, y, pw, ph, 40 * U); g.fill(); g.stroke();
      g.textAlign = 'center'; g.fillStyle = BONE; g.font = `600 ${pw * .13}px NSUI`; g.fillText(cd.name, W / 2, y + ph * .3);
      g.font = `400 ${pw * .065}px NSUI`; g.fillStyle = 'rgba(236,232,220,.6)'; const sec = Math.floor(t - cd.at0); g.fillText('00:' + String(Math.max(0, sec) + 1).padStart(2, '0'), W / 2, y + ph * .3 + pw * .12);
      g.fillStyle = RED; g.beginPath(); g.arc(W / 2, y + ph * .82, pw * .1, 0, 7); g.fill(); g.restore();
    } else if (cd.kind === 'end') {
      const a = fade(t, cd.at, cd.dur, .6, .01);
      g.save(); g.globalAlpha = a;
      const ly = LAND ? H * .33 : H * .3, yb = logo(W / 2, ly, LAND ? .62 : .8, 1, T, (T * 7) % 11 < .1 ? .7 : 0);
      const pulse = .85 + .15 * Math.sin(T * 3);
      g.textAlign = 'center'; g.textBaseline = 'alphabetic';
      const y1 = yb + (LAND ? 150 : 210) * U;
      g.font = `700 ${(LAND ? 70 : 74) * U}px NSUI`; g.fillStyle = BONE; g.shadowColor = 'rgba(0,0,0,.9)'; g.shadowBlur = 18 * U;
      const l1 = L === 'ru' ? 'ИГРАЙ СЕЙЧАС НА ITCH.IO' : 'PLAY NOW ON ITCH.IO'; const lines1 = wrap(l1, LAND ? W * .8 : 900 * U);
      lines1.forEach((l, i) => spaced(l, W / 2, y1 + i * 84 * U, 4 * U));
      const yf = y1 + (lines1.length - 1) * 84 * U + 58 * U; g.font = `600 ${(LAND ? 34 : 38) * U}px NSUI`; g.fillStyle = GOLD;
      spaced(L === 'ru' ? 'БЕСПЛАТНОЕ ДЕМО В БРАУЗЕРЕ' : 'FREE DEMO IN YOUR BROWSER', W / 2, yf, 5 * U);
      const y2 = yf + 34 * U; g.fillStyle = 'rgba(236,232,220,.35)'; g.globalAlpha = a * pulse; g.fillRect(W / 2 - 160 * U, y2, 320 * U, 3 * U); g.globalAlpha = a;
      g.font = `600 ${(LAND ? 40 : 44) * U}px NSUI`; g.fillStyle = 'rgba(236,232,220,.85)';
      const l2 = L === 'ru' ? 'СКОРО В STEAM И НА ANDROID' : 'COMING SOON TO STEAM & ANDROID'; const lines2 = wrap(l2, LAND ? W * .8 : 880 * U);
      lines2.forEach((l, i) => spaced(l, W / 2, y2 + 76 * U + i * 56 * U, 5 * U));
      const by = y2 + 76 * U + lines2.length * 56 * U + (LAND ? 50 : 80) * U; badge18(W / 2, by, (LAND ? 38 : 46) * U, a);
      g.restore();
    }
  }

  const fixQ = x => typeof x === 'string' ? x.replace(/[’‘]/g, "'") : x;   // в шрифте игры нет типографского апострофа
  window.__comp = async (o) => {
    for (const cd of o.cards) { if (cd.lines) cd.lines = cd.lines.map(fixQ); if (cd.lines2) cd.lines2 = cd.lines2.map(fixQ); cd.tag = fixQ(cd.tag); }
    for (const sb of o.subs) sb.text = fixQ(sb.text);
    // o: { t, T, seg, img (url|null), cards, subs, L, fx… }
    seed = 1000 + o.f * 7919;
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; g.filter = 'none';
    g.fillStyle = '#000'; g.fillRect(0, 0, W, H);
    const s = o.seg, tau = o.tau;
    if (o.bg) {   // фон карточки «где играть»: размытый кадр игры
      const im = await createImageBitmap(await (await fetch(o.bg)).blob()); const k = 1.08 + .04 * (tau / Math.max(1, s.dur));
      g.filter = 'blur(10px) brightness(.38) saturate(.7)'; g.drawImage(im, W / 2 - W * k / 2, H / 2 - H * k / 2, W * k, H * k); g.filter = 'none'; im.close();
    }
    if (o.stub) { const gr = g.createLinearGradient(0, 0, W, H); gr.addColorStop(0, '#3a4148'); gr.addColorStop(1, '#14181c'); g.fillStyle = gr; g.fillRect(0, 0, W, H); }
    if (o.img) {
      const im = await createImageBitmap(await (await fetch(o.img)).blob());
      let shx = 0, shy = 0;
      if (s.scareAt != null && tau >= s.scareAt) { const st = tau - s.scareAt, amp = (st < .05 ? 46 : 34 * Math.max(0, 1 - st / .7)) * U; shx = (rnd() - .5) * amp; shy = (rnd() - .5) * amp; }
      if (s.dz) {   // цифровой наезд камеры наблюдения на точку [cx, cy] (доли кадра): z0 → z1 за сегмент, точка плавно уходит в центр
        const [cx, cy, z0, z1] = s.dz, e = sm(0, 1, tau / Math.max(.1, s.dur)), k = z0 + (z1 - z0) * e, fx = cx * W, fy = cy * H;
        const X = cl(W / 2 + (fx - W / 2) * (1 - e) - fx * k, W - W * k, 0), Y = cl(H / 2 + (fy - H / 2) * (1 - e) - fy * k, H - H * k, 0);
        g.drawImage(im, X + shx, Y + shy, W * k, H * k);
      } else g.drawImage(im, shx, shy, W, H);
      im.close();
      const fx = s.fx || '';
      if (s.cctv) { if (o.tear) tear(); grain(.12); }
      if (fx === 'static') { grain(.42); g.fillStyle = `rgba(255,255,255,${.04 + .04 * Math.sin(o.T * 40)})`; g.fillRect(0, 0, W, H); }
      if (fx === 'flicker' && (o.T % 4) < .5) { g.fillStyle = `rgba(255,255,255,${.04 * Math.abs(Math.sin(o.T * 31))})`; g.fillRect(0, 0, W, H); }
      if (fx === 'dark') { g.fillStyle = `rgba(0,0,0,${Math.min(.85, tau / s.dur * .85)})`; g.fillRect(0, 0, W, H); }
      if (!fx || fx === 'rain') grain(.07);
      if (s.scareAt != null && tau >= s.scareAt) {   // бросок: двоится, вспышки-негатив, потом чернота
        const st = tau - s.scareAt;
        if (st < .6) grain(.5 * (1 - st / .6));
        if (st < .8) { g.save(); g.globalAlpha = .35 * (1 - st / .8); g.globalCompositeOperation = 'lighter'; g.drawImage(c, 16 + rnd() * 20, 0); g.drawImage(c, -16 - rnd() * 20, 6); g.restore(); }
        if ((st > .09 && st < .13) || (st > .3 && st < .33)) { g.save(); g.globalCompositeOperation = 'difference'; g.fillStyle = '#fff'; g.fillRect(0, 0, W, H); g.restore(); }
        if (st > .8) { g.fillStyle = '#000'; g.fillRect(0, 0, W, H); grain(.2); }
      }
      vignette(s.cctv ? .55 : .8);
      if (s.cctv) hud(s.cctv, o.T, s.clock || '03:00', LAND ? .75 : 1);
      if (s.flash && tau < .14) { g.save(); g.globalCompositeOperation = 'lighter'; g.fillStyle = `rgba(255,255,255,${s.flash * (1 - tau / .14)})`; g.fillRect(0, 0, W, H); g.restore(); if (tau < .05) { g.save(); g.globalAlpha = .5; g.globalCompositeOperation = 'lighter'; g.drawImage(c, 18 * U, 0); g.restore(); } }
    }
    if (s.fadeIn && tau < s.fadeIn) { g.fillStyle = `rgba(0,0,0,${1 - tau / s.fadeIn})`; g.fillRect(0, 0, W, H); }
    if (s.fadeOut && tau > s.dur - s.fadeOut) { g.fillStyle = `rgba(0,0,0,${(tau - (s.dur - s.fadeOut)) / s.fadeOut})`; g.fillRect(0, 0, W, H); }
    for (const cd of o.cards) card(cd, o.t, o.T, o.L);
    if (!o.img && !o.bg) grain(.05);
    for (const sb of o.subs) subs(sb.text, fade(o.t, sb.at, sb.dur, .08, .12));
    return o.png ? c.toDataURL('image/png') : c.toDataURL('image/jpeg', .92);
  };
}

(async () => {
  const b = await chromium.launch({ args: ['--disable-gpu'] });
  const p = await b.newPage({ viewport: { width: Math.min(W, 1920), height: Math.min(H, 1920) } });
  const errs = []; p.on('pageerror', e => errs.push(String(e))); p.on('console', m => m.type() === 'error' && errs.push(m.text()));
  await p.route('http://tr.local/**', r => { const f = decodeURIComponent(new URL(r.request().url()).pathname.slice(1)); try { r.fulfill({ body: fs.readFileSync(path.join(cache, f)), contentType: 'image/jpeg' }); } catch { r.fulfill({ status: 404 }); } });
  await p.goto('http://127.0.0.1:8771/nightshift/fonts/LICENSE.txt');
  await p.setContent(`<!doctype html><html><head><meta charset="utf-8"><base href="http://127.0.0.1:8771/nightshift/"><style>@font-face{font-family:NSLogo;src:url(fonts/ns-logo.woff)}@font-face{font-family:NSUI;src:url(fonts/ns-ui.woff)}body{margin:0;background:#000}</style></head><body><canvas id="c" width="${W}" height="${H}"></canvas></body></html>`);
  await p.evaluate(async () => { await Promise.all([document.fonts.load('400 100px NSLogo'), document.fonts.load('700 100px NSUI'), document.fonts.load('600 100px NSUI')]); });
  await p.evaluate(pageMain);
  const man = {}; for (const id in index) { const f = path.join(cache, index[id], 'done.json'); if (fs.existsSync(f)) man[id] = JSON.parse(fs.readFileSync(f)).files; }
  const bgSeg = TL.segs.find(s => s.id === (LAND ? 'lily' : 'far')) || TL.segs.find(s => s.shot);
  const bgUrl = man[bgSeg.id] ? `http://tr.local/${index[bgSeg.id]}/${man[bgSeg.id][man[bgSeg.id].length - 1]}` : null;
  const frames = AT ? AT.map(t => Math.round(t * FPS)) : null, f0 = +(opt('from') || 0), f1 = +(opt('to') || TL.frames);
  let ff = null;
  if (!PNG) { ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-maxrate', '16M', '-bufsize', '32M', '-pix_fmt', 'yuv420p', '-r', String(FPS), outMp4], { stdio: ['pipe', 'inherit', 'inherit'] }); }
  else fs.mkdirSync(PNG, { recursive: true });
  // склейки нот «телефон»: время начала разговора для таймера
  for (const cd of TL.cards) if (cd.kind === 'phone') cd.at0 = TL.cards.filter(x => x.kind === 'phone')[0].at;
  // отметки «рывок строк» камер наблюдения: раз в 2–6 с, детерминированно
  const tears = new Set(); { let s = 99, t = 1; const r = () => (s = (s * 16807) % 2147483647) / 2147483647; while (t < TL.dur) { tears.add(Math.round(t * FPS)); t += 2.2 + r() * 4.5; } }
  const t0 = Date.now(), list = frames || Array.from({ length: f1 - f0 }, (_, i) => f0 + i);
  for (const f of list) {
    const t = f / FPS, seg = TL.segs.find(s => f >= s.f0 && f < s.f0 + s.n) || TL.segs[TL.segs.length - 1], i = f - seg.f0, tau = i / FPS;
    const img = seg.shot && man[seg.id] ? `http://tr.local/${index[seg.id]}/${man[seg.id][Math.min(i, man[seg.id].length - 1)]}` : null;
    if (seg.shot && !img && !PNG) throw new Error('нет кадров сегмента ' + seg.id + ' — сначала render.js');   // в --png без кадров — серая заглушка (проверка надписей)
    const cards = TL.cards.filter(cd => t >= cd.at - .01 && t < cd.at + cd.dur), subs = TL.subs.filter(sb => t >= sb.at && t < sb.end);
    const o = { f, t, T: 100 + t, tau, seg, img, stub: !!(seg.shot && !img), bg: seg.id === 'end' ? bgUrl : null, cards, subs, L: TL.lang, tear: tears.has(f), png: !!PNG };
    const url = await p.evaluate(o => window.__comp(o), o);
    const buf = Buffer.from(url.split(',')[1], 'base64');
    if (PNG) fs.writeFileSync(path.join(PNG, `${TL.fmt}_${TL.lang}_${t.toFixed(2)}.png`), buf);
    else if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if (f % 240 === 0) process.stdout.write(`${f}/${TL.frames} (${((Date.now() - t0) / 1000).toFixed(0)} с) `);
  }
  if (ff) { ff.stdin.end(); await new Promise(r => ff.on('close', r)); }
  if (errs.length) console.log('\nERR', errs.slice(0, 5).join('\n'));
  console.log('\nготово', PNG || outMp4);
  await b.close();
})();
