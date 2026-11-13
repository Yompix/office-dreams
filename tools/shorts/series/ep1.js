#!/usr/bin/env node
// «THE POLL», серия 1 — 3D-ролик для Shorts (1080×1920). Сценарий: ep1.md, реплики: ep1_lines.json.
//   node tools/shorts/series/ep1.js still ПАПКА --vo ПАПКА_ГОЛОСА     — по кадру из каждого плана (посмотреть глазами)
//   node tools/shorts/series/ep1.js video OUT.mp4 --vo ПАПКА_ГОЛОСА   — ролик без звука + OUT.json (события звука для hush-till-six/tools/shorts/mix.py)
// Рисует движок Hush Till Six (люди, губы, свет), но игра не меняется: локация и герои добавляются здесь, на лету
// (в отдаваемые странице scenes.js и chars2.js вставляется по одной строке-крючку). Нужен сервер: cd games/hush-till-six && python3 -m http.server 8771
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs'), path = require('path'), { spawn, execFileSync } = require('child_process');
const [mode, out] = process.argv.slice(2), VO = process.argv[process.argv.indexOf('--vo') + 1];
const WEB = path.join(__dirname, '../../../games/hush-till-six/web'), FPS = 24, PORT = process.env.PORT || 8771;
const LINES = JSON.parse(fs.readFileSync(path.join(__dirname, 'ep1_lines.json'), 'utf8'));
const TXT = {}; LINES.lines.forEach(l => { TXT[l[0]] = l[2]; });

// громкость реплики по времени (48 отсчётов в секунду) — по ней открывается рот
function envelope(f) {
  const pcm = execFileSync('ffmpeg', ['-loglevel', 'error', '-i', f, '-ac', '1', '-ar', '8000', '-f', 's16le', '-'], { maxBuffer: 1 << 26 });
  const n = Math.floor(pcm.length / 2), step = 8000 / 48, e = [];
  for (let i = 0; i + step <= n; i += step) { let s = 0; for (let j = 0; j < step; j++) { const v = pcm.readInt16LE(2 * Math.floor(i + j)) / 32768; s += v * v; } e.push(Math.sqrt(s / step)); }
  const mx = Math.max(.01, ...e); return { e: e.map(v => +Math.min(1, v / mx * 1.3).toFixed(2)), d: n / 8000 };
}
const VOX = {}; for (const k of Object.keys(TXT)) VOX[k] = envelope(path.join(VO, k + '.wav'));
// слова со временем (tools/shorts/series/align.py → ПАПКА_ГОЛОСА/words.json): губы складываются под буквы, как в игре; без файла — только по громкости
const WORDS = fs.existsSync(path.join(VO, 'words.json')) ? JSON.parse(fs.readFileSync(path.join(VO, 'words.json'), 'utf8')) : {};

// ----- монтаж: планы по порядку; время считается от длины реплик -----
const PLAN = [
  { shot: 'p_wide', pre: .7, lines: ['leo1', 'mara1'], post: .3, scr: { m: 'poll', v: 0 }, st: 'a' },
  { shot: 'p_scr', pre: .5, lines: ['leo2'], post: .9, scr: { m: 'poll', v: 0 }, st: 'a' },
  { shot: 'p_two', pre: .3, lines: ['mara2', 'leo3'], post: .25, scr: { m: 'poll', v: 0 }, st: 'b' },
  { shot: 'p_scr', pre: .2, lines: ['leo4'], post: .6, scr: { m: 'poll', tick: [.35, 1.25, 2.1] }, st: 'b', pd: .9 },
  { shot: 'p_mara', pre: .15, lines: ['mara3'], post: .2, scr: { m: 'poll', v: 3 }, st: 'b' },
  { shot: 'p_cctv', pre: .5, lines: ['leo5'], post: .5, scr: { m: 'poll', v: 4 }, st: 'b' },
  { shot: 'p_door', pre: .9, lines: ['mara4'], post: .2, scr: { m: 'poll', v: 6 }, st: 'c', sfx: [['door_metal', .15, .9], ['door_metal', 1.0, .7]] },
  { shot: 'p_scr', pre: .4, lines: ['leo6'], post: .5, scr: { m: 'lock', v: 9 }, st: 'c' },
  { shot: 'p_back', pre: 0, lines: [], post: 2.0, scr: { m: 'lock', v: 17 }, st: 'd', sfx: [['glass_scratch', .3, 1.0]] },
  { shot: 'p_pov', pre: .5, lines: ['mara5'], post: .3, scr: { m: 'poll', run: [41, 212] }, st: 'd' },
  { shot: 'p_leo', pre: .15, lines: ['leo7'], post: .5, scr: { m: 'poll', run: [212, 388] }, st: 'd' },
  { shot: 'p_pov', pre: 0, lines: [], post: 6.0, end: 1, scr: { m: 'poll', run: [388, 1046] }, st: 'd' },
];
function spec() {
  let T = 0; const seg = [], ev = [{ f: 'fluor_flicker', at: 0, g: .16, loop: 1 }, { f: 'drone_dread', at: 0, g: .0, loop: 1 }];
  for (const p of PLAN) {
    const s = { shot: p.shot, t0: T, say: [], scr: p.scr, st: p.st, end: p.end || 0 }; let t = T + p.pre;
    for (const k of p.lines) { const add = k === p.lines[0] && p.pd ? p.pd : 0; t += add; s.say.push({ k, t0: t, d: VOX[k].d, who: k.replace(/\d+$/, '') }); ev.push({ f: 'vo:' + k, at: t, g: 1 }); t += VOX[k].d + .2; }
    t += p.post; s.t1 = t;
    for (const [f, at, g] of p.sfx || []) ev.push({ f, at: T + at, g });
    if (p.scr.tick) for (const a of p.scr.tick) ev.push({ f: 'click', at: T + a, g: .8 });
    if (p.end) { ev.push({ f: 'boom', at: T + .05, g: .7 }); ev.push({ f: 'heart', at: T + .3, g: .55, d: p.post - .6 }); }
    seg.push(s); T = t;
  }
  ev[1] = { f: 'drone_dread', at: seg[3].t0, g: .32, loop: 1 };
  return { dur: T, seg, ev, vox: VOX, txt: TXT, words: WORDS };
}

// ----- страница: локация, герои, планы, надписи -----
function pageMain(SP, FPS) {
  const W = 1080, H = 1920, c3 = document.getElementById('c'), g3 = c3.getContext('2d'), o = document.getElementById('o'), g = o.getContext('2d');
  const G = window.__G, PI = Math.PI;
  const segAt = T => SP.seg.find(s => T < s.t1) || SP.seg[SP.seg.length - 1];
  // буква → форма губ (то же, что VIS_CH/visemeAt в web/engine.js)
  const VC = {}; for (const [s, v] of [['a', 'viseme_aa'], ['o', 'viseme_O'], ['uw', 'viseme_U'], ['iy', 'viseme_I'], ['e', 'viseme_E'], ['pbm', 'viseme_PP'], ['fv', 'viseme_FF'], ['sz', 'viseme_SS'], ['jc', 'viseme_CH'], ['td', 'viseme_DD'], ['nl', 'viseme_nn'], ['kgqxh', 'viseme_kk'], ['r', 'viseme_RR']]) for (const c of s) VC[c] = v;
  const visAt = (w, tl) => { if (!w || !w.length) return null; let i = -1; for (let j = 0; j < w.length && w[j][0] <= tl; j++) i = j; if (i < 0) return 'sil';
    const Lt = [...String(w[i][1]).toLowerCase()].filter(c => VC[c]); if (!Lt.length) return 'sil';
    const st = w[i][0], nx = i + 1 < w.length ? w[i + 1][0] : st + 9, en = Math.min(nx - .02, st + .075 * Lt.length + .1); if (tl > en) return 'sil';
    return VC[Lt[Math.min(Lt.length - 1, Math.floor((tl - st) / Math.max(.05, en - st) * Lt.length))]]; };
  window.__state = T => { const s = segAt(T); G.T = T; G.s = s; G.say = s.say.find(q => T >= q.t0 && T < q.t0 + q.d) || null;
    G.amp = G.say ? (SP.vox[G.say.k].e[Math.floor((T - G.say.t0) * 48)] || 0) : 0;
    G.vis = G.say ? visAt(SP.words[G.say.k], T - G.say.t0) : null;
    const sc = s.scr, t = T - s.t0; let v = sc.v || 0;
    if (sc.tick) v = sc.tick.filter(a => t >= a).length; if (sc.run) v = Math.round(sc.run[0] + (sc.run[1] - sc.run[0]) * Math.min(1, t / (s.t1 - s.t0)) ** 1.6);
    G.votes = v; G.scr = sc.m; return s; };
  const text = (s, x, y, px, col, a = 1, al = 'center', font = 'NSUI') => { g.globalAlpha = a; g.font = '700 ' + px + 'px ' + font + ',Arial'; g.textAlign = al; g.textBaseline = 'middle'; g.lineWidth = px * .16; g.strokeStyle = 'rgba(0,0,0,.85)'; g.lineJoin = 'round'; g.strokeText(s, x, y); g.fillStyle = col; g.fillText(s, x, y); g.globalAlpha = 1; };
  const wrap = (s, px, max) => { g.font = '700 ' + px + 'px NSUI,Arial'; const r = []; let l = ''; for (const w of s.split(' ')) { if (l && g.measureText(l + ' ' + w).width > max) { r.push(l); l = w; } else l = l ? l + ' ' + w : w; } r.push(l); return r; };
  let lastShot = null;
  window.__frame = (T, q = .9) => {
    const s = window.__state(T), t = T - s.t0, fresh = lastShot !== s; lastShot = s;
    if (fresh) for (let w = 0; w < 5; w++) World.draw(g3, c3.width, c3.height, s.shot, { t, T: 100 + T, settle: w === 0 });
    World.draw(g3, c3.width, c3.height, s.shot, { t, T: 100 + T });
    g.imageSmoothingQuality = 'high'; g.drawImage(c3, 0, 0, W, H);
    // края темнее, сверху и снизу мягкая тень под надписи
    let gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, 'rgba(0,0,0,.45)'); gr.addColorStop(.14, 'rgba(0,0,0,0)'); gr.addColorStop(.66, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,.6)'); g.fillStyle = gr; g.fillRect(0, 0, W, H);
    if (T < 3.2) text('THE POLL', W / 2, 150, 76, '#f4efe6', Math.min(1, T / .5, (3.2 - T) / .5), 'center', 'NSLogo'), text('EPISODE 1', W / 2, 232, 34, '#d33', Math.min(1, T / .5, (3.2 - T) / .5));
    if (s.shot === 'p_pov' ) { text('● REC   CAM 03', 60, 120, 30, '#f33', .5 + .5 * (Math.floor(T * 2) % 2), 'left'); text('VOTES: ' + G.votes, W - 60, 120, 34, '#f4efe6', .95, 'right'); }
    if (G.say && !s.end) { const who = G.say.who.toUpperCase(), col = who === 'LEO' ? '#8fd0ff' : '#ffd36a', ls = wrap(SP.txt[G.say.k], 50, W - 150), y0 = 1500 - (ls.length - 1) * 32;
      text(who, W / 2, y0 - 70, 32, col); ls.forEach((l, i) => text(l, W / 2, y0 + i * 64, 50, '#fff')); }
    if (s.end) { const a = Math.min(1, t / .6); g.fillStyle = 'rgba(4,5,7,' + (.72 * a) + ')'; g.fillRect(0, 0, W, H);
      const u = Math.min(1, Math.max(0, (t - .3) / .5)), e = 1 - (1 - u) ** 3, pulse = .5 + .5 * Math.sin(T * 5);
      text('YOU ARE THE VOTE', W / 2, 330, 78, '#f4efe6', a, 'center', 'NSLogo'); text('Should they open the storage door?', W / 2, 440, 40, '#c9c4ba', a);
      const card = (x, col, L, s1, s2) => { g.globalAlpha = e; g.fillStyle = 'rgba(12,13,16,.92)'; g.fillRect(x, 560 + (1 - e) * 80, 470, 560); g.strokeStyle = col; g.lineWidth = 6; g.strokeRect(x, 560 + (1 - e) * 80, 470, 560); g.globalAlpha = 1;
        text(L, x + 235, 760 + (1 - e) * 80, 250, col, e, 'center', 'NSLogo'); text(s1, x + 235, 960 + (1 - e) * 80, 46, '#fff', e); text(s2, x + 235, 1030 + (1 - e) * 80, 46, '#fff', e); };
      card(50, '#e23b3b', 'A', 'OPEN', 'THE DOOR'); card(560, '#3bb0e2', 'B', 'LEAVE', 'IT SHUT');
      text('VOTES: ' + G.votes, W / 2, 1220, 54, '#f4efe6', a * (.75 + .25 * pulse));
      if (t > 1.6) text('Comment  A  or  B', W / 2, 1350, 62, '#ffd36a', Math.min(1, (t - 1.6) / .4));
      if (t > 2.8) text('Episode 2 obeys the result.', W / 2, 1450, 42, '#fff', Math.min(1, (t - 2.8) / .4));
      if (t > 3.6) { text('SUBSCRIBE', W / 2, 1610, 64, '#e23b3b', Math.min(1, (t - 3.6) / .4), 'center', 'NSLogo'); text('They stay locked in until you decide.', W / 2, 1690, 36, '#c9c4ba', Math.min(1, (t - 3.6) / .4)); } }
    if (T < .5) { g.fillStyle = 'rgba(0,0,0,' + (1 - T / .5) + ')'; g.fillRect(0, 0, W, H); }
    return o.toDataURL('image/jpeg', q);
  };
}

// выполняется в странице ДО World.init: крючок, который движок вызовет при сборке мест
function pageHook() {
  const G = window.__G = { T: 0, s: null, say: null, amp: 0, votes: 0, scr: 'poll' }, PI = Math.PI;
  window.__SERIES = ({ T, L, A, SH, mat, box, plane, cyl, label, ctex, TX, CINE }) => {
    const H = window.__W3chars.makeHuman;
    // ----- герои серии — свои модели (tools/shorts/series/chars.json → models/leo.glb, mara.glb), не из игры -----
    A.leo = H({ name: 'leo', model: 'leo', tint: { hair: [0x1a1612, 1.3], brows: [0x1a1612, 1.3], cloth: 0x9a9fa6 } });
    A.mara = H({ name: 'mara', model: 'mara', fem: true, tint: { hair: [0x1c130d, 1.2], brows: [0x1c130d, 1.2], cloth: 0x3f6f58 } });
    for (const k of ['leo', 'mara']) { const F = A[k].faceSets;
      F.worry = { browIn: .8, browUp: .1, dart: 1 }; F.alert = { browUp: .9, wide: true, dart: 1 }; F.fear = { mouth: .25, wide: true, browUp: 1, browIn: .8, dart: 1 }; F.plead = { browIn: 1, browUp: .35, mouth: .12 }; F.calm = { smile: .15 };
      A[k].update(0, 1 / 30); if (CINE) A[k].root.traverse(m => { if (m.isMesh) { m.castShadow = true; m.receiveShadow = true; } }); }
    // ----- прачечная в подвале: 4,4 × 6 м, потолок 2,6 -----
    const S = new T.Scene(), o = { S }, FOG = 0x0a0d10; S.background = new T.Color(FOG); o.fogD = .03; S.fog = new T.FogExp2(FOG, o.fogD);
    o.amb = new T.AmbientLight(0x56605e, .3); S.add(o.amb);
    const wd = 4.4, len = 6, hg = 2.6, wall = mat(0xa9aea6, TX.wall, { bump: .6 }), paint = mat(0x3d5a52, TX.wall, { bump: .6 }), fl = mat(0xd8d8d2, TX.concrete);
    const wp = (w, h, m, x, y, z, ry) => { const p = plane(w, h, m, S); p.position.set(x, y, z); p.rotation.y = ry; return p; };
    { const f = plane(wd, len, fl, S); f.rotation.x = -PI / 2; f.position.set(0, 0, -len / 2); const c = plane(wd, len, mat(0x3a3b38, TX.wall), S); c.rotation.x = PI / 2; c.position.set(0, hg, -len / 2); }
    for (const s of [-1, 1]) { wp(len, hg, wall, s * wd / 2, hg / 2, -len / 2, -s * PI / 2); wp(len, 1.15, paint, s * (wd / 2 - .005), .575, -len / 2, -s * PI / 2); }
    wp(wd, hg, wall, 0, hg / 2, -len, 0); wp(wd, 1.15, paint, 0, .575, -len + .005, 0); wp(wd, hg, wall, 0, hg / 2, 0, PI); wp(wd, 1.15, paint, 0, .575, -.005, PI);
    // стиральные машины вдоль левой стены, одна крутится (лампочка)
    const white = mat(0xe6e8e6, null, { rough: .35 }), dark = mat(0x14171a, null, { rough: .2, metal: .4 }), steel = mat(0x8a9094, TX.metal, { metal: .6, rough: .45 });
    o.drums = [];
    for (let i = 0; i < 5; i++) { const z = -1.0 - i * .78, x = -wd / 2 + .34; box(.64, .88, .7, white, x, .44, z, S); box(.02, .1, .6, mat(0xc9cdd0), x + .325, .8, z, S);
      const d = cyl(.2, .2, .03, 24, dark, S); d.rotation.z = PI / 2; d.position.set(x + .33, .42, z); const r = cyl(.24, .24, .02, 24, steel, S); r.rotation.z = PI / 2; r.position.set(x + .322, .42, z);
      const led = box(.012, .025, .04, mat(i === 1 ? 0x38ff70 : 0x203020, null, { basic: true, own: 'wl' + i }), x + .335, .8, z + .2, S); o.drums.push(led); }
    box(.5, .02, 3.6, mat(0x6b5a44, null, { rough: .8 }), -wd / 2 + .27, 1.55, -2.6, S);   // полка над машинами
    for (const z of [-1.4, -2.2, -3.5]) box(.22, .28, .16, mat([0x2a5fa8, 0xd8d0c0, 0xb03030][Math.round(-z) % 3], null, { rough: .6 }), -wd / 2 + .25, 1.7, z, S);   // коробки порошка
    // корзина с бельём у машин, скамья у задней стены
    { const b = cyl(.24, .19, .34, 14, mat(0x3a6ea8, null, { rough: .7 }), S); b.position.set(-.4, .17, -2.0); const c = cyl(.22, .22, .08, 14, mat(0xe8e2d6, null, { rough: .9 }), S); c.position.set(-.4, .35, -2.0); }
    box(1.2, .05, .34, mat(0x5a4a38, null, { rough: .8 }), -1.2, .45, -len + .25, S); for (const dx of [-.5, .5]) box(.05, .45, .3, dark, -1.2 + dx, .225, -len + .25, S);
    // терминал оплаты на правой стене: корпус, экран (рисуется каждый кадр), щель для карты
    const TXW = wd / 2;
    box(.07, .62, .46, mat(0x202428, null, { metal: .5, rough: .4 }), TXW - .035, 1.38, -3.0, S); box(.02, .03, .12, dark, TXW - .075, 1.13, -3.0, S);
    const sc = document.createElement('canvas'); sc.width = 384; sc.height = 448; o.scx = sc.getContext('2d'); o.scT = new T.CanvasTexture(sc); if (T.sRGBEncoding) o.scT.encoding = T.sRGBEncoding;
    o.scr = plane(.38, .44, new T.MeshBasicMaterial({ map: o.scT, toneMapped: false }), S); o.scr.rotation.y = -PI / 2; o.scr.position.set(TXW - .072, 1.43, -3.0);
    o.scrL = new T.PointLight(0x9fd0ff, .55, 2.6, 1.8); o.scrL.position.set(TXW - .45, 1.45, -3.0); S.add(o.scrL);
    // выход (передняя стена) и железная дверь кладовой (задняя стена), над ней табличка
    const door = (x, z, ry, col, sign) => { const gD = new T.Group(); gD.position.set(x, 0, z); gD.rotation.y = ry; S.add(gD); const fr = mat(0x2a2c2e, null, { metal: .6 });
      box(1.04, .08, .08, fr, 0, 2.08, .03, gD); for (const dx of [-.52, .52]) box(.08, 2.1, .08, fr, dx, 1.05, .03, gD);
      box(.94, 2.02, .05, mat(col, TX.metal, { metal: .5, rough: .5 }), 0, 1.02, .02, gD); box(.03, .14, .06, mat(0xb8bcc0, null, { metal: .8, rough: .3 }), .36, 1.02, .08, gD); box(.12, .03, .04, mat(0xb8bcc0, null, { metal: .8, rough: .3 }), .31, 1.06, .1, gD);
      const sg = label(.62, .15, 124, 30, (c, w, h) => { c.fillStyle = sign[1]; c.fillRect(0, 0, w, h); c.fillStyle = '#f2f2ee'; c.font = '700 19px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(sign[0], w / 2, h / 2 + 1); }, gD, false); sg.position.set(0, 2.28, .03); return gD; };
    door(0, -.02, PI, 0x5d6468, ['EXIT', '#1c6a34']); o.stor = door(0, -len + .02, 0, 0x4a3b34, ['STORAGE', '#5a1c1c']);
    o.slit = plane(.9, .02, mat(0x000000, null, { basic: true }), S); o.slit.position.set(0, .011, -len + .06);
    // камера наблюдения в правом дальнем углу, красный огонёк
    { const cg = new T.Group(); cg.position.set(1.95, 2.42, -5.72); cg.lookAt(-.2, 1.2, -3.0); S.add(cg); box(.1, .1, .24, mat(0xd8dadc, null, { rough: .4 }), 0, 0, 0, cg); const ln = cyl(.035, .035, .03, 16, dark, cg); ln.rotation.x = PI / 2; ln.position.set(0, 0, .13);
      o.led = box(.018, .018, .01, mat(0xff2020, null, { basic: true, glow: 3, own: 'cled' }), .03, .035, .125, cg); box(.03, .16, .03, mat(0x9a9c9e), 0, .1, -.08, cg); }
    o.ledL = new T.PointLight(0xff2a2a, 0, 1.6, 2); o.ledL.position.set(1.8, 2.3, -5.5); S.add(o.ledL);
    // лампы: две люминесцентные, дальняя (у кладовой) ненадёжная
    o.lamps = []; for (const z of [-1.7, -4.4]) { const tube = box(.12, .04, 1.1, mat(0xf2fbff, null, { basic: true, glow: 2.5, own: 'lt' + z }), 0, hg - .05, z, S); const li = new T.PointLight(0xdcecff, 1.5, 7, 1.5); li.position.set(0, hg - .3, z); S.add(li); o.lamps.push({ li, tube, base: 1.5 }); }
    o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
    const drawScr = () => { const c = o.scx, w = 384, h = 448, t = G.T; c.fillStyle = '#071018'; c.fillRect(0, 0, w, h); c.textAlign = 'center'; c.textBaseline = 'middle';
      if (G.scr === 'lock') { c.fillStyle = '#e23b3b'; c.font = '700 44px Arial'; c.fillText('DOORS', w / 2, 110); c.fillText('UNLOCK', w / 2, 165); c.fillStyle = '#e8f2ff'; c.font = '700 34px Arial'; c.fillText('WHEN VOTING', w / 2, 240); c.fillText('CLOSES', w / 2, 284);
        c.fillStyle = '#7fb6e6'; c.font = '700 26px Arial'; c.fillText('VOTES: ' + G.votes, w / 2, 370); }
      else { c.fillStyle = '#7fb6e6'; c.font = '700 30px Arial'; c.fillText('POLL #1', w / 2, 44); c.fillStyle = '#e8f2ff'; c.font = '700 31px Arial'; c.fillText('SHOULD THEY OPEN', w / 2, 104); c.fillText('THE STORAGE DOOR?', w / 2, 142);
        c.font = '700 30px Arial'; c.fillStyle = '#e23b3b'; c.fillText('A: OPEN', w / 2, 212); c.fillStyle = '#3bb0e2'; c.fillText('B: LEAVE IT', w / 2, 256);
        c.fillStyle = '#fff'; c.font = '700 46px Arial'; c.fillText('VOTES: ' + G.votes, w / 2, 334);
        const left = Math.max(0, 86400 - Math.floor(t)), p2 = n => String(n).padStart(2, '0'); c.fillStyle = '#7fb6e6'; c.font = '700 24px Arial'; c.fillText('CLOSES IN ' + p2(Math.floor(left / 3600)) + ':' + p2(Math.floor(left / 60) % 60) + ':' + p2(left % 60), w / 2, 404); }
      c.fillStyle = 'rgba(255,255,255,.05)'; for (let y = (t * 40 % 4) | 0; y < h; y += 4) c.fillRect(0, y, w, 1); o.scT.needsUpdate = true; };
    o.reset = () => { o.amb.intensity = .3; S.fog.density = o.fogD; o.faceL.intensity = 0; o.lamps.forEach(l => { l.li.intensity = l.base; }); };
    o.tick = t => { drawScr(); const bl = G.votes > 0 ? (Math.sin(G.T * 9) > 0 ? 1 : .15) : .0; o.led.material.color.setRGB(bl, .02, .02); o.ledL.intensity = .5 * bl;
      const n = Math.sin(t * 31) * Math.sin(t * 7.3), f = n > .82 ? .15 : 1, l = o.lamps[1]; l.li.intensity = l.base * f * (G.s && G.s.st === 'd' ? .7 : 1); l.tube.material.color.setHex(f < 1 ? 0x56605e : 0xf2fbff);
      o.scrL.intensity = .5 + .08 * Math.sin(t * 13); };
    L.laundry = o;
    // ----- планы -----
    const TERM = [TXW - .07, 1.43, -3.0], CAMP = [1.95, 2.42, -5.72], STOR = [0, 1.2, -len];
    const base = (k, t, who, more) => Object.assign({ clips: [['Idle_Loop', t + k, 1]], legs: 'feet', talk: G.say && G.say.who === who ? 1 : 0, post: C => { const me = G.say && G.say.who === who; C.talk = me ? .2 + .6 * G.amp : 0; C.vis = me ? G.vis : null; } }, more);
    const face = (leo, mara) => { A.leo.faceSet = leo; A.mara.faceSet = mara; };
    const ry = (x, z, tx, tz) => Math.atan2(tx - x, tz - z);
    const P = { a: { leo: [1.5, -3.0], mara: [.2, -2.0] }, b: { leo: [1.45, -3.42], mara: [1.4, -2.5] }, c: { leo: [1.45, -3.2], mara: [.05, -.72] }, d: { leo: [.85, -2.3], mara: [-.25, -3.2] } };
    Object.assign(SH, {
      p_wide: { loc: 'laundry', pos: [-.3, 1.6, -.3], look: [.9, 1.1, -3.0], fov: 52, set: (o, t) => { face('calm', 'base'); const p = P.a;
        return [['leo', p.leo[0], 0, p.leo[1], PI / 2, base(1.3, t, 'leo'), G.say && G.say.who === 'mara' ? [p.mara[0], 1.5, p.mara[1]] : TERM],
          ['mara', p.mara[0], 0, p.mara[1], G.say && G.say.who === 'mara' ? ry(...p.mara, ...p.leo) : -PI / 2, base(2.9, t, 'mara'), G.say && G.say.who === 'mara' ? [p.leo[0], 1.55, p.leo[1]] : [-.35, .4, -2.0]]]; } },
      p_scr: { loc: 'laundry', pos: [1.2, 1.44, -3.0], look: TERM, fov: 30, set: () => [] },
      p_two: { loc: 'laundry', pos: [-1.3, 1.5, -2.96], look: [1.6, 1.38, -2.96], fov: 40, set: (o, t) => { face('worry', 'worry'); const p = P.b;
        return [['leo', p.leo[0], 0, p.leo[1], ry(...p.leo, 1.9, -3.0), base(1.3, t, 'leo'), G.say && G.say.who === 'leo' ? [p.mara[0], 1.56, p.mara[1]] : TERM],
          ['mara', p.mara[0], 0, p.mara[1], ry(...p.mara, 1.9, -3.0), base(2.9, t, 'mara'), G.say && G.say.who === 'leo' ? [p.leo[0], 1.6, p.leo[1]] : TERM]]; } },
      p_mara: { loc: 'laundry', pos: [1.2, 1.5, -3.5], look: [1.4, 1.47, -2.5], fov: 28, set: (o, t) => { face('worry', 'alert'); const p = P.b; o.faceL.position.set(1.0, 1.7, -3.2); o.faceL.intensity = .5;
        return [['mara', p.mara[0], 0, p.mara[1], ry(...p.mara, 1.2, -3.5), base(2.9, t, 'mara'), t < .9 ? [1.2, 1.56, -3.5] : [-.5, 1.3, -1]]]; } },
      p_cctv: { loc: 'laundry', pos: [.95, 1.5, -3.9], look: [1.9, 2.36, -5.65], fov: 34, set: (o, t) => { face('alert', 'alert'); return []; } },
      p_door: { loc: 'laundry', pos: [1.35, 1.38, -2.1], look: [.05, 1.22, -.5], fov: 44, set: (o, t) => { face('alert', 'fear'); const p = P.c, j = Math.max(0, Math.sin(t * 9)) * (t < 2.6 ? 1 : .3);
        return [['mara', p.mara[0], 0, p.mara[1], t > 3.2 ? ry(...p.mara, ...p.leo) : 0, base(2.9, t, 'mara', t > 3.2 ? {} : { arm: { r: { at: [-.31, 1.06, -.16 - .07 * j], by: 'palm', w: 1 } }, pelvis: [0, 0, -.04 * j] }), t > 3.2 ? [p.leo[0], 1.6, p.leo[1]] : [-.3, 1.05, 0]]]; } },
      p_back: { loc: 'laundry', pos: [.25, 1.35, -.9], look: [0, 1.15, -6], fov: 46, set: (o, t) => { face('fear', 'fear'); const p = P.d, k = Math.min(1, t / .7), tl = (a, b) => a + (b - a) * k;
        return [['leo', p.leo[0], 0, p.leo[1], tl(ry(...p.leo, ...p.mara), ry(...p.leo, 0, -6)), base(1.3, t, 'leo'), STOR], ['mara', p.mara[0], 0, p.mara[1], tl(ry(...p.mara, ...p.leo), ry(...p.mara, 0, -6)), base(2.9, t, 'mara'), STOR]]; } },
      p_pov: { loc: 'laundry', pos: [1.68, 2.3, -5.36], look: [-.1, 1.15, -3.0], fov: 50, cctv: 1, set: (o, t) => { face('fear', 'plead'); const p = P.d;
        return [['mara', p.mara[0], 0, p.mara[1], ry(...p.mara, CAMP[0], CAMP[2]), base(2.9, t, 'mara'), 'cam'], ['leo', p.leo[0], 0, p.leo[1], ry(...p.leo, ...p.mara), base(1.3, t, 'leo'), G.s && G.s.end ? 'cam' : [p.mara[0], 1.5, p.mara[1]]]]; } },
      p_leo: { loc: 'laundry', pos: [.35, 1.52, -3.15], look: [.85, 1.5, -2.3], fov: 27, set: (o, t) => { face('fear', 'plead'); const p = P.d; o.faceL.position.set(.3, 1.8, -2.9); o.faceL.intensity = .45;
        return [['leo', p.leo[0], 0, p.leo[1], ry(...p.leo, .35, -3.15), base(1.3, t, 'leo'), t > 2.2 ? CAMP : [p.mara[0], 1.5, p.mara[1]]]]; } },
    });
  };
}

(async () => {
  const SP = spec(); console.log('длина', SP.dur.toFixed(1), 'с');
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await br.newPage({ viewport: { width: 1280, height: 800 } });
  p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 500))); p.on('console', m => { if (m.type() === 'error') console.log('консоль:', m.text().slice(0, 300)); });
  const RW = +(process.env.RW || 540), RH = RW * 16 / 9;
  const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>@font-face{font-family:NSLogo;src:url(fonts/ns-logo.woff)}@font-face{font-family:NSUI;src:url(fonts/ns-ui.woff)}</style></head><body style="margin:0;background:#000"><script src="lib/three.min.js"></script><script src="lib/gltf.min.js"></script><script src="lib/meshopt_decoder.js"></script><script src="i18n.js"></script><script src="world.js"></script><script src="chars.js"></script><script src="chars2.js"></script><script src="scenes.js"></script><canvas id="c" width="${RW}" height="${RH}"></canvas><canvas id="o" width="1080" height="1920"></canvas></body></html>`;
  await p.route(/\/web\/_poll\.html/, r => r.fulfill({ contentType: 'text/html', body: HTML }));
  const patch = (file, from, to) => p.route(new RegExp('/web/' + file.replace('.', '\\.')), r => { const s = fs.readFileSync(path.join(WEB, file), 'utf8'); if (!s.includes(from)) throw new Error('крючок не встал: ' + file); r.fulfill({ contentType: 'application/javascript', body: s.replace(from, to) }); });
  await patch('scenes.js', "L.basement = buildBasement(); ST('свет');", "L.basement = buildBasement(); if (window.__SERIES) window.__SERIES({ T, L, A, SH, mat, box, plane, cyl, label, ctex, TX, CINE }); ST('свет');");
  await p.route(/\/web\/chars2\.js/, r => { let s = fs.readFileSync(path.join(WEB, 'chars2.js'), 'utf8'); const a = 'Object.assign(window.__W3chars, { load, makeSkinned,', b = "const HUMANS = ['ethan',"; if (!s.includes(a) || !s.includes(b)) throw new Error('крючок не встал: chars2.js');
    r.fulfill({ contentType: 'application/javascript', body: s.replace(a, 'Object.assign(window.__W3chars, { load, makeHuman, makeSkinned,').replace(b, "const HUMANS = ['leo', 'mara', 'ethan',") }); });
  await p.route(/\/web\/models\/(leo|mara)\.glb/, r => r.fulfill({ contentType: 'model/gltf-binary', body: fs.readFileSync(path.join(__dirname, 'models', /(leo|mara)\.glb/.exec(r.request().url())[0])) }));
  await p.goto('http://127.0.0.1:' + PORT + '/web/_poll.html?gfx=' + (process.env.GFX || 'cine') + '&lang=en');
  await p.waitForFunction(() => window.World && window.__W3chars && window.__W3chars.load, null, { timeout: 300000 });
  await p.evaluate('(' + pageHook.toString() + ')()');
  await p.evaluate(async () => { window.__W3core.V.rsPin = true; window.__W3core.V.rs = 1; await window.__W3chars.load(); World.init(); if (World.setLand) World.setLand(false); await Promise.all([document.fonts.load('40px NSLogo'), document.fonts.load('40px NSUI')]); });
  await p.evaluate('(' + pageMain.toString() + ')(' + JSON.stringify(SP) + ',' + FPS + ')');
  const shot = async (T, q) => Buffer.from((await p.evaluate(([T, q]) => window.__frame(T, q), [T, q])).split(',')[1], 'base64');
  if (mode === 'still') {
    fs.mkdirSync(out, { recursive: true });
    const only = process.env.ONLY ? process.env.ONLY.split(',').map(Number) : null;
    for (let i = 0; i < SP.seg.length; i++) { if (only && !only.includes(i)) continue; const s = SP.seg[i], T = process.env.AT ? s.t0 + +process.env.AT : s.say[0] ? s.say[0].t0 + s.say[0].d * .5 : (s.t0 + s.t1) / 2;
      for (let k = 12; k >= 0; k--) await shot(Math.max(s.t0, T - k / 12), .5); await p.waitForTimeout(600);
      fs.writeFileSync(path.join(out, String(i).padStart(2, '0') + '_' + s.shot + '.jpg'), await shot(T, .85)); console.log('кадр', i, s.shot, T.toFixed(1)); }
  } else {
    fs.writeFileSync(out.replace(/\.mp4$/, '') + '.json', JSON.stringify({ dur: SP.dur, ev: SP.ev }, null, 1));
    const ff = spawn('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), out], { stdio: ['pipe', 'inherit', 'inherit'] });
    const N = Math.round(SP.dur * FPS), t0 = Date.now();
    for (let i = 0; i < N; i++) { const b = await shot(i / FPS, .92); if (!ff.stdin.write(b)) await new Promise(r => ff.stdin.once('drain', r)); if (i % 96 === 0) console.log('кадр', i, '/', N, ((Date.now() - t0) / 1000 | 0) + ' с'); }
    ff.stdin.end(); await new Promise(r => ff.on('close', r)); console.log('готово', out, SP.dur.toFixed(1) + ' с');
  }
  await br.close();
})();
