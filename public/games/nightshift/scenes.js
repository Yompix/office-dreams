/* Места действия (холл с будкой и лифтами, улица, 7 этаж, серверная), кадры для каждой сцены и сам показ кадра. */
(() => {
'use strict';
const tr = window.tr || (s => s);
const { T, V, CINE, FINE, litMat, HD, rnd, lerp, clamp, ps1, mat, ctex, TX, box, plane, cyl, room, label, relabel, makeTextures } = window.__W3core;
const PI = Math.PI;
let A = null;          // актёры
const L = {};          // места действия
const FOGC = 0x0b0f14;   // туман чуть светлее чёрного: тёмные силуэты отделяются от него
const hash = n => { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
// ненадёжная лампа: обычно горит; раз в 8–25 с — серия коротких провалов (40–120 мс), иногда гаснет на полторы секунды
function unreliable(t, seed) {
  const P = 15, k = Math.floor(t / P), ph = t - k * P, st = 2 + hash(k * 7.13 + seed) * (P - 5), d = ph - st;
  if (d < 0 || d > 1.9) return 1;
  if (hash(k * 3.7 + seed * 1.3) < .15) return d < .06 || (d > .22 && d < .27) || d > 1.5 ? 1 : 0;
  const n = 2 + Math.floor(hash(k * 1.7 + seed) * 5);
  for (let i = 0; i < n; i++) { const s = i * .2 + hash(k * 3 + i + seed) * .08, l = .04 + hash(k * 5 + i + seed) * .08; if (d >= s && d < s + l) return 0; }
  return 1;
}

/* ---------- холл бизнес-центра + улица перед ним ---------- */
function buildLobby() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .055; S.fog = new T.FogExp2(FOGC, o.fogD);
  // заполняющий свет слабый: свет — это лужи под лампами, между ними настоящая темнота
  o.amb = new T.AmbientLight(0x6a7a90, .34); S.add(o.amb);
  o.hemi = new T.HemisphereLight(0x6f86a0, 0x1a1410, .1); S.add(o.hemi);
  // пол, потолок, стены
  const floor = plane(16, 12, mat(0xffffff, TX.marble), S); floor.rotation.x = -PI / 2;
  const ceil = plane(16, 12, mat(0xffffff, TX.ceil), S); ceil.rotation.x = PI / 2; ceil.position.y = 4.2;
  const wallM = mat(0xffffff, TX.wall), panelM = mat(0xffffff, TX.panel, { decal: 1 });
  const back = plane(16, 4.2, wallM, S); back.position.set(0, 2.1, -6);
  const left = plane(12, 4.2, wallM, S); left.rotation.y = PI / 2; left.position.set(-8, 2.1, 0);
  const right = plane(12, 4.2, wallM, S); right.rotation.y = -PI / 2; right.position.set(8, 2.1, 0);
  for (const [w, x, z, ry] of [[16, 0, -5.99, 0], [12, -7.99, 0, PI / 2], [12, 7.99, 0, -PI / 2]]) { const p = plane(w, 1.1, panelM, S); p.position.set(x, .55, z); p.rotation.y = ry; }
  // потолочные светильники (часть не горит)
  o.ceilLights = [];
  for (let i = -2; i <= 2; i++) for (let j = -1; j <= 1; j++) {
    const on = !(i === -2 && j === 1) && !(i === 1 && j === -1);
    const p = plane(1.1, .5, mat(on ? 0xe8f0e0 : 0x30322e, null, { basic: true, decal: 1 }), S); p.rotation.x = PI / 2; p.position.set(i * 3.2, 4.19, j * 3.4); o.ceilLights.push(p);
  }
  o.ceilOn = mat(0xe8f0e0, null, { basic: true, decal: 1 });   // общий материал горящих панелей (смена гасит холл)
  o.flick = plane(1.1, .5, ps1(new T.MeshBasicMaterial({ color: 0xe8f0e0, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 })), S);   // свой материал: иначе мигали бы все панели o.flick.rotation.x = PI / 2; o.flick.position.set(-4.8, 4.18, 3.4);
  o.l1 = new T.PointLight(0xc8d8d0, 1.9, 13, 1.5); o.l1.position.set(-1, 3.8, 0); S.add(o.l1);
  o.l3 = new T.PointLight(0xb8c8d8, 1.3, 9, 1.5); o.l3.position.set(-.8, 3.6, -4.2); S.add(o.l3);
  o.l2 = new T.PointLight(0xffd9a0, .9, 9, 1.6); o.l2.position.set(-4.8, 3.7, 3.4); S.add(o.l2);   // ненадёжная лампа холла
  o.l4 = new T.PointLight(0xc8d4dc, 1.25, 7.5, 1.5); o.l4.position.set(.6, 3.7, 3.4); S.add(o.l4);   // над входом изнутри: свет до стекла
  // колонны
  for (const [x, z] of [[-4.5, 1], [2.3, 2.6]]) { box(.7, 4.2, .7, mat(0xffffff, TX.wall), x, 2.1, z, S); box(.74, 1.1, .74, panelM, x, .55, z, S); }
  // турникеты перед лифтами: четыре стойки из нержавейки, между ними проходы со стеклянными створками и считыватели пропусков
  { const stM = mat(0xb4b8bc, null, { metal: .85, rough: .28 }), capM = mat(0x0c0d0f, null, { rough: .15 }), rdM = mat(0x101112),
      flapM = ps1(new T.MeshBasicMaterial({ color: 0xa8c0d0, transparent: true, opacity: .18, depthWrite: false, side: T.DoubleSide }));
    for (const x of [-1.75, -1.05, -.35, .35]) {
      const g = new T.Group(); g.position.set(x, 0, 1.0); S.add(g);
      box(.16, .98, .9, stM, 0, .49, 0, g); box(.17, .02, .91, capM, 0, .99, 0, g);
      box(.07, .012, .1, rdM, 0, 1.006, .28, g); box(.014, .006, .014, mat(0x30ff70, null, { basic: true }), 0, 1.014, .28, g);
      for (const s of [-1, 1]) { if ((x < -1.7 && s < 0) || (x > .3 && s > 0)) continue; const f = plane(.21, .5, flapM, g); f.position.set(s * .185, .76, 0); }
    }
  }
  // лифты на задней стене
  o.lifts = [];
  for (const x of [-2.2, .6]) {
    const fr = mat(0x2a2c2e);
    box(1.5, .12, .14, fr, x, 2.36, -5.93, S); box(.12, 2.4, .14, fr, x - .69, 1.2, -5.93, S); box(.12, 2.4, .14, fr, x + .69, 1.2, -5.93, S);
    const dl = box(.62, 2.3, .05, mat(0xffffff, TX.metal), x - .31, 1.15, -5.95, S), dr = box(.62, 2.3, .05, mat(0xffffff, TX.metal), x + .31, 1.15, -5.95, S);
    const ind = label(.36, .16, 48, 22, (c, w, h, s = '1', up = 0) => { c.fillStyle = '#080404'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff3a2a'; c.font = '700 18px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText((up ? '▲' : ' ') + s, w / 2, h / 2 + 1); }, S);
    ind.position.set(x, 2.62, -5.94);
    const btn = box(.08, .16, .03, mat(0x222222), x + 1.0, 1.15, -5.95, S); box(.03, .03, .02, mat(0xffaa44, null, { basic: true }), x + 1.0, 1.18, -5.93, S);
    o.lifts.push({ x, dl, dr, ind, open: 0, shown: '' });
  }
  // кабина первого лифта (видна, когда двери открыты)
  const cab = new T.Group(); cab.position.set(-2.2, 0, -6.85); S.add(cab);
  const cm = mat(0xffffff, TX.metal);
  box(1.4, .02, 1.6, mat(0x2a2624), 0, .01, 0, cab); box(1.4, .02, 1.6, mat(0xd8dccf, null, { basic: true }), 0, 2.4, 0, cab);
  box(.04, 2.4, 1.6, cm, -.7, 1.2, 0, cab); box(.04, 2.4, 1.6, cm, .7, 1.2, 0, cab); box(1.4, 2.4, .04, cm, 0, 1.2, -.8, cab);
  box(1.0, 1.2, .01, mat(0x9aa6ad, null, { emissive: 0x111518 }), 0, 1.4, -.77, cab);       // зеркало
  box(.03, .9, .03, mat(0x888888), 0, .95, -.76, cab);
  const panel = box(.02, .5, .2, mat(0x222428, null, { decal: 1 }), .68, 1.2, .5, cab);
  o.cabInd = label(.22, .1, 40, 18, (c, w, h, s = '1') => { c.fillStyle = '#060303'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff3a2a'; c.font = '700 15px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(s, w / 2, h / 2 + 1); }, cab);
  o.cabInd.position.set(0, 2.25, .78); o.cabInd.rotation.y = PI;
  o.cabLight = new T.PointLight(0xe8eedd, 0, 5, 1.5); o.cabLight.position.set(0, 2.2, 0); cab.add(o.cabLight);
  o.cab = cab;
  // табличка и плакат
  const sg = plane(2.6, .5, mat(0xffffff, TX.sign, { decal: 1 }), S); sg.position.set(-.8, 3.35, -5.97);
  const ps = plane(.5, .75, mat(0xffffff, TX.poster, { decal: 1 }), S); ps.position.set(2.6, 1.6, -5.97);
  const ex = plane(.5, .25, mat(0xffffff, TX.exit, { basic: true }), S); ex.rotation.y = PI / 2; ex.position.set(-7.97, 2.6, -3.5);
  box(.06, 2.2, 1.0, mat(0xffffff, TX.door), -7.96, 1.1, -3.5, S);
  // пальма в кадке у первого лифта и её тень на стене: на камере 04 её легко принять за человека («тень от кадки с пальмой»)
  { const pg = new T.Group(); pg.position.set(-3.5, 0, -5.42); S.add(pg); o.palm = pg;
    cyl(.24, .18, .46, 12, mat(0x2b2a28, null, { rough: .5 }), pg).position.y = .23; cyl(.23, .23, .03, 12, mat(0x2a2016), pg).position.y = .45;
    const tk = mat(0x5a4632, null, { rough: .9 }); for (let i = 0; i < 6; i++) { const s = cyl(.045 - i * .004, .05 - i * .004, .24, 7, tk, pg); s.position.set(Math.sin(i * .7) * .02, .58 + i * .22, 0); }
    const lf = mat(0x2c4a26, null, { rough: .7, side: T.DoubleSide });
    for (let i = 0; i < 9; i++) { const a = i / 9 * PI * 2, fr = new T.Group(); fr.position.y = 1.86; fr.rotation.set(0, a, 0); pg.add(fr);
      for (let j = 0; j < 6; j++) { const seg = box(.14 - j * .016, .008, .2, lf, 0, -j * j * .018, .1 + j * .17, fr); seg.rotation.x = .25 + j * .14; } }
    const sh = ctex(128, 256, (x, w, h) => { x.clearRect(0, 0, w, h); x.fillStyle = '#000'; x.filter = 'blur(3px)';
      x.beginPath(); x.ellipse(w / 2, h * .2, w * .17, h * .1, 0, 0, 7); x.fill();   // «голова» — крона
      x.beginPath(); x.moveTo(w * .36, h * .27); x.quadraticCurveTo(w * .5, h * .32, w * .64, h * .27); x.lineTo(w * .7, h * .62); x.lineTo(w * .56, h * .98); x.lineTo(w * .44, h * .98); x.lineTo(w * .3, h * .62); x.closePath(); x.fill(); });
    const shd = plane(.95, 2.1, new T.MeshBasicMaterial({ map: sh, transparent: true, opacity: .55, depthWrite: false, color: 0x000000, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -4 }), S);
    shd.position.set(-3.95, 1.25, -5.985); o.palmShadow = shd; }
  // часы между лифтами (стрелки двигает кадр: время сцены или «встали в три»)
  const clk = new T.Group(); clk.position.set(-.8, 2.72, -5.96); S.add(clk);
  const cf = new T.Mesh(new T.CircleGeometry(.21, 16), mat(0xffffff, TX.clock, { decal: 1 })); clk.add(cf);
  const hm = mat(0x111111);
  o.clockH = new T.Group(); box(.018, .1, .006, hm, 0, .045, .006, o.clockH); clk.add(o.clockH);
  o.clockM = new T.Group(); box(.012, .16, .006, hm, 0, .07, .01, o.clockM); clk.add(o.clockM);
  o.setClock = (h, m) => { o.clockM.rotation.z = -m / 60 * PI * 2; o.clockH.rotation.z = -((h % 12) + m / 60) / 12 * PI * 2; };
  // скамейка и растение
  box(.5, .08, 2, mat(0xffffff, TX.wood), -7.4, .45, 1, S); for (const z of [.2, 1.8]) box(.45, .45, .06, mat(0x222222), -7.4, .22, z, S);
  cyl(.25, .2, .5, 8, mat(0x3a2a20), S).position.set(-7.3, .25, 4.6);
  for (let i = 0; i < 7; i++) { const lf = box(.08, .9, .3, mat(0x1f3a22), -7.3, .9, 4.6, S); lf.rotation.set((rnd() - .5) * .8, rnd() * PI, (rnd() - .5) * .8); }
  // будка охраны: стойка, стол, мониторы, лампа, листок, ключи, стул
  const counterM = mat(0xffffff, TX.panel), topM = mat(0x2a2420);
  box(2.9, 1.15, .1, counterM, 5.1, .575, .45, S); box(3.0, .05, .4, topM, 5.1, 1.17, .45, S);
  box(.1, 1.15, 2.0, counterM, 3.62, .575, -.55, S);
  const gs = plane(.7, .17, mat(0xffffff, TX.guardSign, { decal: 1 }), S); gs.position.set(5.1, .9, .51);
  box(2.6, .05, .8, mat(0xffffff, TX.wood), 5.1, .78, -.05, S); for (const x of [3.9, 6.3]) box(.05, .78, .7, mat(0x1b1b1b), x, .39, -.05, S);
  // мониторы: два ряда, экраны развёрнуты к охраннику
  o.screens = [];
  const bank = new T.Group(); bank.position.set(4.5, 0, .12); bank.rotation.y = PI - .6; S.add(bank);
  box(1.0, .08, .3, mat(0x1a1b1d), 0, .84, -.05, bank);
  for (let r = 0; r < 2; r++) for (let c2 = 0; c2 < 2; c2++) {
    const g = new T.Group(); g.position.set((c2 - .5) * .48, 1.03 + r * .34, 0); bank.add(g);
    box(.5, .36, .12, mat(0x1a1b1d), 0, 0, -.06, g);
    const scr = plane(.44, .3, new T.MeshBasicMaterial({ color: 0xffffff, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), g); scr.position.z = .001; ps1(scr.material);
    o.screens.push(scr);
  }
  // лампа
  const lampBase = cyl(.08, .1, .03, 8, mat(0x1f3a2a), S); lampBase.position.set(6.15, .81, -.15);
  box(.02, .38, .02, mat(0x1f3a2a), 6.15, 1.0, -.15, S);
  const shade = cyl(.06, .14, .14, 8, mat(0x1f5a3a, null, { emissive: 0x081a10 }), S); shade.position.set(6.05, 1.18, -.12); shade.rotation.z = .5;
  o.lampBulb = box(.05, .03, .05, mat(0xffe8a0, null, { basic: true }), 6.0, 1.12, -.12, S);
  o.lamp = new T.PointLight(0xffc46a, 1.6, 3.2, 1.5); o.lamp.position.set(5.95, 1.05, -.12); S.add(o.lamp);
  o.boothLight = new T.PointLight(0x9fe0c0, .6, 5, 1.6); o.boothLight.position.set(4.6, 1.4, -.2); S.add(o.boothLight);
  o.note = plane(.42, .28, mat(0xffffff, TX.note, { decal: 1 }), S); o.note.rotation.x = -PI / 2; o.note.rotation.z = PI + .12; o.note.position.set(5.35, .807, -.27);   // раскрытый журнал смен
  { const cv = plane(.44, .30, mat(0x2a1e16), S); cv.rotation.x = -PI / 2; cv.rotation.z = PI + .12; cv.position.set(5.35, .804, -.27); }   // обложка под разворотом
  o.keys = new T.Group(); box(.06, .008, .035, mat(0xb8a060, null, { emissive: 0x201800 }), 0, 0, 0, o.keys); box(.01, .006, .06, mat(0xbbbbbb), .015, 0, .04, o.keys); box(.01, .006, .05, mat(0xbbbbbb), -.012, 0, .035, o.keys);
  o.keys.position.set(5.68, .81, -.28); o.keys.rotation.y = .6; S.add(o.keys);
  box(.12, .1, .08, mat(0x2a2a2a), 5.9, .85, .15, S);   // рация
  const cup = cyl(.035, .03, .09, 8, mat(0xd8d0c0), S); cup.position.set(4.95, .85, -.3);
  const chair = new T.Group(); chair.position.set(5.2, 0, -.74); S.add(chair);
  box(.5, .07, .5, mat(0x161616), 0, .47, 0, chair); box(.5, .6, .06, mat(0x161616), 0, .8, -.24, chair); box(.05, .45, .05, mat(0x333333), 0, .22, 0, chair);
  o.chair = chair;
  // второй стул у пульта: на спинке так и висит куртка Фрэнка («куртка на стуле»)
  const ch2 = new T.Group(); ch2.position.set(6.25, 0, -.62); ch2.rotation.y = -.5; S.add(ch2);
  box(.46, .07, .46, mat(0x161616), 0, .47, 0, ch2); box(.46, .56, .06, mat(0x161616), 0, .78, -.22, ch2); box(.05, .45, .05, mat(0x333333), 0, .22, 0, ch2);
  const jk = new T.Group(); jk.position.set(0, 0, -.22); ch2.add(jk);
  const jm = mat(0x24324c, null, { decal: 1 }), jt = mat(0x3a4a64, null, { decal: 1 });
  box(.5, .14, .16, jm, 0, 1.08, 0, jk); box(.5, .55, .05, jm, 0, .8, -.06, jk); box(.46, .4, .05, jm, 0, .87, .06, jk);   // плечи на спинке, спина и перед
  for (const s of [-1, 1]) { const sl = box(.11, .5, .1, jm, s * .27, .78, .02, jk); sl.rotation.z = s * .08; box(.118, .04, .108, jt, s * .285, .52, .02, jk); }   // рукава висят
  box(.16, .04, .005, mat(0xc8c8c8), .1, .97, .09, jk);   // нашивка
  o.jacket = jk;
  // стёкла будки на стойке: слева и справа, посередине окно — через него передают ключи; уходя, сменщик стучит в левое
  { const gmL = new T.MeshBasicMaterial({ color: 0x9fb0bc, transparent: true, opacity: .1, depthWrite: false, blending: T.AdditiveBlending });
    const gmD = new T.MeshBasicMaterial({ map: TX.grime, transparent: true, opacity: .16, depthWrite: false, color: 0x8c98a0 }); ps1(gmD);
    const frM = mat(0x2b2d30, null, { metal: .6, rough: .45 });
    o.boothGlass = [];
    for (const [x0, x1] of [[3.68, 4.62], [5.62, 6.56]]) {
      const w = x1 - x0, cx = (x0 + x1) / 2, pg = new T.Group(); pg.position.set(cx, 1.65, .45); S.add(pg);
      plane(w, .86, gmL, pg); const d = plane(w, .86, gmD, pg); d.position.z = .001;
      box(w + .04, .03, .04, frM, 0, .445, 0, pg); box(.03, .9, .04, frM, -w / 2, 0, 0, pg); box(.03, .9, .04, frM, w / 2, 0, 0, pg);
      o.boothGlass.push(pg);
    }
    // будка закрытая: над левой стойкой тоже стекло, сзади перегородка (панель + стекло), справа дверь с замком и глухая панель до стены
    for (const [x, z, w, ry] of [[3.62, -.55, 1.96, PI / 2], [5.81, -1.55, 4.38, 0]]) {
      const pg = new T.Group(); pg.position.set(x, 0, z); pg.rotation.y = ry; S.add(pg);
      if (z < -1) box(w, 1.15, .06, counterM, 0, .575, 0, pg);
      const gl = plane(w - .04, .86, gmL, pg); gl.position.y = 1.65; const d = plane(w - .04, .86, gmD, pg); d.position.set(0, 1.65, .001);
      box(w, .03, .04, frM, 0, 2.095, 0, pg); box(w, .03, .05, frM, 0, 1.2, 0, pg); o.boothGlass.push(pg);
    }
    const dM = mat(0xffffff, TX.door), hM = mat(0xb8bcc0, null, { metal: .85, rough: .3 });
    box(.06, 2.1, .1, frM, 6.6, 1.05, .45, S); box(.06, 2.1, .1, frM, 7.52, 1.05, .45, S); box(.98, .06, .1, frM, 7.06, 2.13, .45, S);
    box(.46, 2.16, .06, counterM, 7.77, 1.08, .45, S);                                   // глухая панель до стены
    o.boothDoor = new T.Group(); o.boothDoor.position.set(6.63, 0, .45); S.add(o.boothDoor);   // петли у стойки, дверь открывается внутрь будки
    box(.86, 2.04, .04, dM, .44, 1.03, 0, o.boothDoor); const dw = plane(.36, .5, gmL, o.boothDoor); dw.position.set(.44, 1.5, -.021); dw.rotation.y = PI;
    box(.035, .2, .03, hM, .8, 1.0, -.04, o.boothDoor); box(.12, .025, .03, hM, .76, 1.08, -.05, o.boothDoor);   // накладка замка и ручка изнутри
    o.boothLock = [7.43, 1.0, .385];   // скважина изнутри (куда вставляют ключ)
  }
  // стекло перегородки будки (видно только в кадре с отражением): рама, грязь, блик
  o.boothPane = new T.Group(); o.boothPane.position.set(5.2, 1.62, .25); S.add(o.boothPane);
  const bp = plane(1.3, .95, new T.MeshBasicMaterial({ map: TX.grime, transparent: true, opacity: .22, depthWrite: false, color: 0x8c98a0 }), o.boothPane); bp.rotation.y = PI; bp.scale.set(-1.6, 1.3, 1); ps1(bp.material);
  const gl = plane(1.3, .95, new T.MeshBasicMaterial({ color: 0x8090a0, transparent: true, opacity: .12, depthWrite: false, blending: T.AdditiveBlending }), o.boothPane); gl.rotation.y = PI; gl.position.z = .002;
  const glare = plane(.09, 1.1, new T.MeshBasicMaterial({ color: 0xd0dce6, transparent: true, opacity: .1, depthWrite: false, blending: T.AdditiveBlending }), o.boothPane); glare.rotation.set(0, PI, .5); glare.position.set(.3, 0, .004);
  for (const [w, h, x, y] of [[1.36, .04, 0, .49], [1.36, .04, 0, -.49], [.04, 1.02, .66, 0], [.04, 1.02, -.66, 0]]) box(w, h, .03, mat(0x2a2c2e), x, y, 0, o.boothPane);
  o.boothPane.visible = false;
  // пропуск Михалыча висит над пультом на шнурке
  o.badge = new T.Group(); o.badge.position.set(-.1, 1.7, .04); o.badge.rotation.set(.08, .1, .03); bank.add(o.badge);
  box(.004, .2, .004, mat(0x2a4a8a), 0, .1, 0, o.badge);
  o.badgeCard = plane(.11, .082, mat(0xffffff, TX.badge, { decal: 1 }), o.badge); o.badgeCard.position.y = -.03; box(.115, .087, .003, mat(0x9aa0a6), 0, -.03, -.003, o.badge);
  // камеры наблюдения (коробочки на стенах)
  for (const [x, y, z] of [[7.6, 3.95, 5.6], [-7.6, 3.95, -5.6]]) box(.18, .12, .3, mat(0xd0d0d0), x, y, z, S);
  // фасад: стекло (ночью отражает холл), плёнка, грязь, капли, рамы, двери
  buildFacade(o, S);
  // снаружи: тротуар, бордюр, дорога, фонарь, машина, дома напротив, небо
  const walk = plane(40, 5, mat(0xffffff, TX.tileSide), S); walk.rotation.x = -PI / 2; walk.position.set(0, .001, 8.5);
  box(40, .15, .3, mat(0xffffff, TX.curb), 0, .075, 11, S);
  const road = plane(60, 20, mat(0xffffff, TX.asphalt), S); road.rotation.x = -PI / 2; road.position.set(0, -.05, 21);
  for (let x = -24; x < 24; x += 6) { const ln = plane(2.4, .15, mat(0xb8b8a8, null, { decal: 1 }), S); ln.rotation.x = -PI / 2; ln.position.set(x, -.04, 17); }
  // навес над входом
  box(5, .2, 2.4, mat(0x1a1c1f), 0, 3.4, 7.2, S); o.canopyLamp = box(.6, .04, .3, mat(0xfff0d0, null, { basic: true }), 0, 3.29, 7.2, S);
  o.canopy = new T.PointLight(0xffe2b0, 1.5, 6, 1.6); o.canopy.position.set(0, 3.1, 7.2); S.add(o.canopy);   // свет сверху: глазницы в тени
  const sgn = plane(3, .55, mat(0xffffff, TX.signOut, { basic: true }), S); sgn.position.set(0, 3.95, 6.17);
  // фонарь
  const pole = cyl(.06, .08, 6, 6, mat(0x222428), S); pole.position.set(3.8, 3, 10.4);
  box(1.2, .06, .06, mat(0x222428), 3.3, 5.95, 10.4, S); o.streetBulb = box(.4, .1, .2, mat(0xffb35a, null, { basic: true }), 2.8, 5.88, 10.4, S);
  o.street = new T.PointLight(0xff9a3a, 1.2, 16, 1.4); o.street.position.set(2.8, 5.6, 10.4); S.add(o.street);
  const pool = plane(4, 4, new T.MeshBasicMaterial({ color: 0xff9a3a, transparent: true, opacity: .12, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), S); pool.rotation.x = -PI / 2; pool.position.set(2.8, .01, 10.6); ps1(pool.material);
  // машина у тротуара
  // мусорные баки справа от входа и кошка у них (смена: Фрэнк двадцать лет записывал её в журнал)
  { const bm = mat(0x24382c, null, { rough: .6 }), lid = mat(0x18241c, null, { rough: .5 });
    for (const [x, z, r] of [[5.3, 8.9, .05], [6.5, 8.95, -.08]]) { const g2 = new T.Group(); g2.position.set(x, 0, z); g2.rotation.y = r; S.add(g2);
      box(1.1, .95, .8, bm, 0, .55, 0, g2); box(1.14, .06, .84, lid, 0, 1.05, 0, g2); for (const dx of [-.4, .4]) cyl(.07, .07, .05, 10, mat(0x111111), g2).position.set(dx, .07, .32); } }
  o.cat = new T.Group(); o.cat.position.set(3.1, 0, 8.6); o.cat.rotation.y = -1.3; o.cat.scale.setScalar(1.15); S.add(o.cat); o.cat.visible = false;
  { const cm = mat(0x0c0c0e, null, { rough: .9 }), body = new T.Mesh(new T.SphereGeometry(.1, 10, 8), cm); body.scale.set(1, .85, 2.0); body.position.set(0, .2, 0); o.cat.add(body);
    const hd = new T.Mesh(new T.SphereGeometry(.07, 10, 8), cm); hd.position.set(0, .32, .2); o.cat.add(hd); o.catHead = hd;
    for (const s of [-1, 1]) { const e = new T.Mesh(new T.ConeGeometry(.028, .06, 6), cm); e.position.set(s * .038, .055, -.005); e.rotation.z = -s * .25; hd.add(e);
      const ey = new T.Mesh(new T.SphereGeometry(.011, 6, 6), mat(0x9fe060, null, { basic: true })); ey.position.set(s * .027, .012, .06); hd.add(ey); }
    for (const [x, z] of [[-.05, .12], [.05, .12], [-.05, -.12], [.05, -.12]]) { const l = cyl(.016, .014, .16, 6, cm, o.cat); l.position.set(x, .08, z); }
    o.catTail = new T.Group(); o.catTail.position.set(0, .22, -.19); o.cat.add(o.catTail);
    for (let i = 0; i < 5; i++) { const sgm = cyl(.014, .012, .06, 6, cm, o.catTail); sgm.position.set(0, .03 + i * .045, -i * .02); sgm.rotation.x = -.35 + i * .12; } }
  const car = new T.Group(); car.position.set(-4.6, 0, 13); car.rotation.y = PI / 2; S.add(car);
  box(1.7, .7, 4.2, mat(0x2a3440), 0, .55, 0, car); box(1.5, .55, 2.1, mat(0x1b222a), 0, 1.15, -.2, car); box(1.52, .45, 2.0, mat(0x0a0d10, null, { emissive: 0x05080c }), 0, 1.15, -.2, car);
  for (const [x, z] of [[-.8, 1.3], [.8, 1.3], [-.8, -1.3], [.8, -1.3]]) { const wh = cyl(.32, .32, .2, 8, mat(0x0d0d0d), car); wh.rotation.z = PI / 2; wh.position.set(x, .32, z); }
  box(.3, .1, .02, mat(0xff2a2a, null, { basic: true }), -.55, .7, -2.11, car); box(.3, .1, .02, mat(0xff2a2a, null, { basic: true }), .55, .7, -2.11, car);
  // наше здание (над холлом) и дома напротив
  const fac = o.fac = plane(30, 36, mat(0xffffff, TX.facade, { emissive: 0x050608 }), S); fac.position.set(0, 4.2 + 18, 6.01);
  o.facLit = fac.material; o.facDark = mat(0xffffff, TX.facadeDark, { emissive: 0x030405 });
  // окно над крышей: тёплый свет в воздухе, в раме — как будто там ещё один этаж
  o.ghostWin = new T.Group(); o.ghostWin.position.set(-3.1, 41.6, 6.0); S.add(o.ghostWin); o.ghostWin.visible = false;
  plane(1.5, 1.05, mat(0xffd9a0, null, { basic: true, fog: false }), o.ghostWin).position.z = .01;
  for (const [w, h, x, y] of [[1.7, .1, 0, .57], [1.7, .1, 0, -.57], [.1, 1.2, -.8, 0], [.1, 1.2, .8, 0], [.06, 1.1, 0, 0]]) box(w, h, .06, mat(0x0d0f12, null, { fog: false }), x, y, .03, o.ghostWin);
  for (const [x, w, h] of [[-14, 9, 22], [-3, 10, 30], [9, 11, 18], [20, 9, 26]]) { const b = box(w, h, 6, mat(0xffffff, TX.facade, { emissive: 0x040506 }), x, h / 2, 34, S); }
  o.sky = new T.Mesh(new T.SphereGeometry(80, 12, 8), new T.MeshBasicMaterial({ map: TX.sky, side: T.BackSide, fog: false })); S.add(o.sky);
  // дождь
  const N = 700, pos = new Float32Array(N * 6); o.drops = [];
  for (let i = 0; i < N; i++) o.drops.push([lerp(-14, 14, rnd()), rnd() * 12, lerp(6.25, 24, rnd()), 6 + rnd() * 5]);
  const rg = new T.BufferGeometry(); rg.setAttribute('position', new T.BufferAttribute(pos, 3));
  o.rain = new T.LineSegments(rg, new T.LineBasicMaterial({ color: 0x9fb4c8, transparent: true, opacity: .38 })); o.rain.frustumCulled = false; S.add(o.rain);
  // молния: вспышка неба
  o.flash = 0;
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  // предметы-«нормы»: сюжет может их тихо менять (куртка пропала, часы встали, фото на пропуске исцарапано)
  o.props = (P = PROPS) => {
    o.jacket.visible = !!P.jacket; o.badge.visible = !!P.badge;
    o.badgeCard.material = mat(0xffffff, P.badge === 'scratched' ? TX.badgeX : TX.badge, { decal: 1 });
    o.note.material = mat(0xffffff, P.log === 2 ? TX.note2 : TX.note, { decal: 1 });
    const [h, m] = String(P.clock || (o.dawn ? '06:00' : '02:57')).split(':').map(Number); o.setClock(h, m || 0);
  };
  o.reset = () => {
    o.faceL.intensity = 0; o.rain.material.opacity = .38; o.boothPane.visible = false;
    for (const l of o.lifts) setLift(o, l, 0, '1');
    o.cabLight.intensity = 0; relabel(o.cabInd, '1');
    o.lamp.intensity = 1.6; o.lampBulb.visible = true; o.boothLight.intensity = .6; o.l1.intensity = 1.9; o.amb.color.set(0x6a7a90); o.amb.intensity = .34; o.hemi.intensity = .1; o.l3.intensity = 1.3; o.l4.intensity = 1.25;
    o.canopy.intensity = 1.5; o.canopyLamp.visible = true;
    o.sky.material.map = TX.sky; S.fog.color.set(FOGC); S.fog.density = o.fogD; S.background.set(FOGC); o.street.intensity = 1.2; o.rain.visible = true;
    o.dawn = 0; o.tubeOK = 1; o.flickOn = true; o.fac.material = o.facLit; o.ghostWin.visible = false; o.ghostWin.scale.setScalar(1); o.cat.visible = false; if (o.nmask) o.nmask.forEach(m => (m.visible = false));
    o.streetBulb.visible = true; o.wetM.opacity = .42; o.wetM.color.setHex(0x101418); o.palm.position.set(-3.5, 0, -5.42); o.palmShadow.visible = true; o.ceilOn.color.setHex(0xe8f0e0);
    resetFacade(o); o.props();
  };
  o.tick = (t, dt) => {
    const p = o.rain.geometry.attributes.position.array;
    for (let i = 0; i < o.drops.length; i++) { const d = o.drops[i]; d[1] -= d[3] * dt; if (d[1] < 0) d[1] += 12; p[i * 6] = d[0]; p[i * 6 + 1] = d[1]; p[i * 6 + 2] = d[2]; p[i * 6 + 3] = d[0] + .03; p[i * 6 + 4] = d[1] - .35; p[i * 6 + 5] = d[2]; }
    o.rain.geometry.attributes.position.needsUpdate = true;
    // ненадёжная лампа холла (у левой колонны)
    const fl = o.flickOn ? unreliable(t, 1) : 0; o.tubeOK = fl; o.flick.material.color.setHex(fl ? 0xe8f0e0 : 0x30322e); o.l2.intensity = .9 * fl;
  };
  return o;
}
function setLift(o, l, open, floor, up = 0) {
  l.open = open; const e = open * .6;
  l.dl.position.x = l.x - .31 - e; l.dr.position.x = l.x + .31 + e;
  const key = floor + '|' + up; if (l.shown !== key) { l.shown = key; relabel(l.ind, floor, up); }
}
function setDawn(o, k) {
  o.dawn = k; o.sky.material.map = k ? TX.skyDawn : TX.sky;
  const fog = new T.Color(FOGC).lerp(new T.Color(0x8a6a5a), k * .8); o.S.fog.color.copy(fog); o.S.background.copy(fog);
  o.amb.color.set(0xffc8a0); o.amb.intensity = .95 + k * .7; o.hemi.intensity = .25; o.street.intensity = 1.2 * (1 - k); o.rain.material.opacity = .38 - k * .2; o.glassU.uRain.value = 1 - k * .45;
  o.canopy.intensity = 1.5 * (1 - k); o.canopyLamp.visible = !k; o.S.fog.density = o.fogD * .7;
  if (!(curProps && curProps.clock)) o.setClock(6, 0);
}

/* ---------- стекло фасада ----------
   Ночью стекло освещённого холла — зеркало: в нём виден сам холл (отдельный маленький проход с отражённой камерой).
   Сверху — то, по чему глаз узнаёт стекло: плёнка в точку на уровне пояса и груди, буквы «ГОРИЗОНТ» (изнутри зеркально),
   жирные пятна и отпечатки, капли и бегущие струйки снаружи, толстые рамы, ручки-штанги. */
const GLASS_VS = `uniform mat4 uTM; varying vec3 vW; varying vec4 vR; varying float vD;
  void main(){ vec4 w = modelMatrix * vec4(position, 1.); vW = w.xyz; vR = uTM * w; vec4 mv = viewMatrix * w; vD = -mv.z; gl_Position = projectionMatrix * mv; }`;
const GLASS_FS = (CINE ? '#define CINE\n' : '') + `uniform sampler2D tRef, tGrime; uniform float uRef, uLit, uT, uFogD, uPrint, uRain; uniform vec2 uPrintP; uniform vec3 uDropC;
  varying vec3 vW; varying vec4 vR; varying float vD;
  float h1(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float cap(vec2 q, vec2 a, vec2 b, float r){ vec2 pa = q - a, ba = b - a; float k = clamp(dot(pa, ba) / dot(ba, ba), 0., 1.); return length(pa - ba * k) - r; }
  float palmD(vec2 q){   // отпечаток левой ладони (смотрим изнутри: большой палец справа по x)
    float d = (length(q / vec2(.043, .052)) - 1.) * .043;
    d = min(d, cap(q, vec2(-.03, .035), vec2(-.04, .098), .0085)); d = min(d, cap(q, vec2(-.01, .045), vec2(-.012, .12), .009));
    d = min(d, cap(q, vec2(.011, .045), vec2(.015, .124), .009)); d = min(d, cap(q, vec2(.03, .036), vec2(.042, .1), .008));
    return min(d, cap(q, vec2(.04, -.01), vec2(.076, .034), .0105));
  }
  void main(){
    vec2 p = vW.xy; float fw = (fwidth(p.x) + fwidth(p.y)) / .04;
    vec3 V = normalize(cameraPosition - vW);
    float fr = .14 + .56 * pow(1. - abs(V.z), 3.);
    vec3 ref = texture2DProj(tRef, vR).rgb; ref = ref / (1. + ref * 1.4) * fr * uRef; float rl = dot(ref, vec3(.3, .55, .15));
    vec2 gu = vec2((p.x + 2.) / 4., (p.y - .4) / 2.);
    float sm = (gu.x > 0. && gu.x < 1. && gu.y > 0. && gu.y < 1.) ? texture2D(tGrime, gu).a : 0.;
    vec3 col = ref * (1. + sm * 3.) + vec3(.62, .66, .7) * sm * (.015 + .06 * uLit + rl * 1.6);
    float a = .05 + sm * .06;
    // матовая полоса-разметка на стекле двери (вместо ряда кругов — они читались как «дыры» в кадре)
    float dv = smoothstep(.935, .945, p.y) * (1. - smoothstep(1.005, 1.015, p.y)) * step(abs(p.x), 7.9) * .32;
    vec3 vin = vec3(.6, .64, .66) * (.05 + .32 * uLit) + ref * .5;
    col = col * (1. - dv * .8) + vin * dv * .8; a += dv * .8 * (1. - a);
    if (uRain > 0.) {
      // капли: прозрачные линзы неровной формы (чуть сплющены, разного размера). Линза затемняет и размывает то, что за ней,
      // сверху — крошечный блик, снизу — светлый серп преломлённого света. Полного светлого кольца нет: оно и давало «пузыри-кружки»
      vec2 cg = p / .013, ci = floor(cg), cf = fract(cg); float fd = fwidth(cg.x) + fwidth(cg.y);
      float r = .05 + .13 * h1(ci + 1.3) * h1(ci + 5.9); vec2 c0 = vec2(h1(ci + 3.1), h1(ci + 7.7)) * .5 + .25;
      vec2 dq = (cf - c0) * vec2(1., 1.18 + .25 * h1(ci + 2.2)); float wob = 1. + .12 * sin(atan(dq.y, dq.x) * 3. + h1(ci) * 6.28);
      float dd = length(dq) * wob;
      float drop = step(h1(ci), .26) * (1. - smoothstep(r - fd * .6, r + fd * .6, dd));
      float hl = drop * smoothstep(r * .3, 0., length(dq - vec2(-r * .3, r * .38)));
      float rim = drop * smoothstep(r * .45, r, dd) * smoothstep(-.1 * r, -.6 * r, dq.y);   // только нижний серп
      col *= 1. - drop * .22;
      float cx = floor(p.x / .05), cr = h1(vec2(cx, 9.1)), on = step(cr, .62);
      float sp = .04 + .1 * cr, len = .08 + .3 * h1(vec2(cx, 4.4)), hy = 3.6 - mod(uT * sp + cr * 17.3, 4.4);
      float xc = (cx + .25 + .5 * h1(vec2(cx, 2.))) * .05 + sin(p.y * 11. + cr * 6.) * .003;
      float dx = abs(p.x - xc), up = p.y - hy, wd = .0022 + fwidth(p.x) * .7;
      float trail = on * step(0., up) * step(up, len) * (1. - up / len) * (1. - smoothstep(wd, wd * 1.8, dx));
      float head = on * (1. - smoothstep(.004, .004 + fwidth(p.x) * 1.5, length(vec2(dx, up * .8))));
      drop *= 1. - on * step(0., up) * step(up, len + .02) * step(dx, .012);
      float rain = drop * .02 + rim * .1 + hl * .45 + trail * .16 + head * .3;
      rain = mix(rain, .06, clamp(fd * 1.3 - .4, 0., 1.));
      if (uPrint > 0.) { float inP = (1. - smoothstep(-.002, .003, palmD(p - uPrintP))) * uPrint; rain *= 1. - inP; inP *= .55 + .45 * h1(floor(p * 900.)); col += vec3(.5, .52, .5) * inP * (.04 + .08 * uLit); a += inP * .05; }
      rain *= uRain; col += uDropC * rain * (.3 + .7 * uLit); a += rain * .1;
    }
    float k = exp(-uFogD * uFogD * vD * vD);
    gl_FragColor = vec4(col * k, clamp(a, 0., 1.) * k);
  }`;
function buildFacade(o, S) {
  const U = o.glassU = { tRef: { value: null }, uTM: { value: new T.Matrix4() }, uRef: { value: 1 }, uLit: { value: 1 }, uT: { value: 0 }, tGrime: { value: TX.grime },
    uFogD: { value: 0 }, uPrint: { value: 0 }, uPrintP: { value: new T.Vector2(.41, 1.33) }, uRain: { value: 1 }, uDropC: { value: new T.Color(.55, .6, .68) } };
  const gm = new T.ShaderMaterial({ uniforms: U, vertexShader: GLASS_VS, fragmentShader: GLASS_FS, transparent: true, depthWrite: false, side: T.DoubleSide,
    blending: T.CustomBlending, blendSrc: T.OneFactor, blendDst: T.OneMinusSrcAlphaFactor, extensions: { derivatives: true } });
  // стекло фасада — без проёма дверей (у створок своё стекло, оно ходит вместе с ними)
  o.glass = new T.Mesh(new T.ShapeGeometry(new T.Shape([[-8, -1.68], [-1, -1.68], [-1, .58], [1, .58], [1, -1.68], [8, -1.68], [8, 1.68], [-8, 1.68]].map(([x, y]) => new T.Vector2(x, y)))), gm);
  o.glass.position.set(0, 1.78, 6); S.add(o.glass);
  // пар от дыхания Тёмыча на стекле изнутри (её сторона остаётся чистой — улика)
  o.breath = plane(.2, .1, new T.MeshBasicMaterial({ map: TX.fogBreath, transparent: true, opacity: 0, depthWrite: false, color: 0xc8d0d8 }), S);
  o.breath.rotation.y = PI; o.breath.position.set(.6, 1.4, 5.992); o.breath.visible = false;
  // запотевшее стекло изнутри (у двери, на уровне лица): по нему Итан выводит пальцем «Как звали собаку?» — сквозь буквы видно улицу
  { const c = document.createElement('canvas'); c.width = 512; c.height = 256; const tx = new T.CanvasTexture(c); tx.colorSpace = T.SRGBColorSpace;
    o.fogW = plane(.8, .4, ps1(new T.MeshBasicMaterial({ map: tx, transparent: true, depthWrite: false, color: 0x6a7278 })), S); o.fogW.rotation.y = PI; o.fogW.position.set(.62, 1.42, 5.988); o.fogW.visible = false;
    o.fogW.userData = { c, x: c.getContext('2d'), tx, k: -9 }; }
  o.doorLock = [-.1, 1.05, 5.925];   // замок — на левой створке у притвора (сама личинка — в buildFacade ниже, вместе со створкой)
  // мокрые следы босых ног: от двери к первому лифту (видны только по сюжету)
  { const ft = ctex(32, 64, (x, w, h) => { x.clearRect(0, 0, w, h); x.fillStyle = 'rgba(0,0,0,1)'; x.filter = 'blur(1.5px)';
      x.beginPath(); x.ellipse(16, 46, 7, 11, 0, 0, 7); x.fill(); x.beginPath(); x.ellipse(15, 24, 9, 12, -.1, 0, 7); x.fill();
      [[7, 9, 3.2], [12, 6, 3], [17, 6, 2.8], [22, 8, 2.5], [26, 12, 2.2]].forEach(([cx, cy, r]) => { x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fill(); }); });
    const fm = ps1(new T.MeshBasicMaterial({ map: ft, transparent: true, opacity: .42, depthWrite: false, color: 0x101418, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -4 }));
    o.wetSteps = new T.Group(); S.add(o.wetSteps); o.wetSteps.visible = false; o.wetM = fm;
    const path = new T.CubicBezierCurve(new T.Vector2(.4, 5.6), new T.Vector2(-.2, 2.5), new T.Vector2(-1.9, -.5), new T.Vector2(-2.2, -5.2));
    for (let i = 0; i < 26; i++) { const u = i / 25, p = path.getPointAt(u), d = path.getTangentAt(Math.min(.999, u)), n = new T.Vector2(d.y, -d.x).multiplyScalar(i % 2 ? .09 : -.09);
      const f = plane(.11, .24, fm, o.wetSteps); f.rotation.x = -PI / 2; f.rotation.z = Math.atan2(d.x, d.y) + PI; f.position.set(p.x + n.x, .006, p.y + n.y); f.scale.x = i % 2 ? -1 : 1; } }
  // плёнка: «ГОРИЗОНТ» на фрамуге (снаружи читается, изнутри зеркально) и «ОТ СЕБЯ» на створках (изнутри)
  const lg = plane(1.9, .36, new T.MeshLambertMaterial({ map: TX.logo, transparent: true, depthWrite: false, side: T.DoubleSide, emissive: 0x15181b }), S); lg.position.set(0, 2.72, 6.004); ps1(lg.material);
  // рамы: толстый тёмный профиль, двери — две створки между x = −1 и 1
  const frM = mat(0x24272b), stM = mat(0x9aa0a6), kickM = mat(0x5a5f64);
  for (let x = -8; x <= 8.01; x += 2) if (Math.abs(x) > .5) box(.1, 3.5, .16, frM, x, 1.75, 6, S);
  box(16, .14, .18, frM, 0, 3.47, 6, S); box(16, .12, .18, frM, 0, .06, 6, S);
  box(16, .7, .3, mat(0xffffff, TX.wall), 0, 3.85, 6, S);
  for (const x of [-1.04, 1.04]) box(.09, 3.5, .18, frM, x, 1.75, 6, S);          // коробка дверей
  box(2.1, .1, .18, frM, 0, 2.4, 6, S);                                            // фрамуга над дверьми
  box(2, .015, .3, kickM, 0, .008, 6, S);                                          // порог
  // створки — группы на петлях (x = ±1), открываются наружу: o.leaves[i].rotation.y = s · угол (s = −1 левая, 1 правая)
  o.leaves = []; o.leafGlass = [];
  for (const s of [-1, 1]) {
    const lf = new T.Group(); lf.position.set(s, 0, 6); S.add(lf); o.leaves.push(lf); const X = x => x - s, cx = X(s * .5);   // мировой x → x створки
    box(.07, 2.3, .08, frM, X(s * .04), 1.2, 0, lf); box(.07, 2.3, .08, frM, X(s * .96), 1.2, 0, lf);   // стойки створки
    box(.92, .07, .08, frM, cx, 2.32, 0, lf); box(.92, .22, .085, kickM, cx, .17, 0, lf);              // верх, нижняя филёнка
    // изнутри — горизонтальная штанга «от себя», снаружи — вертикальная скоба
    box(.62, .032, .032, stM, cx, 1.02, -.11, lf); for (const d of [-.29, .29]) box(.028, .028, .085, stM, cx + d, 1.02, -.065, lf);
    box(.032, .5, .032, stM, X(s * .14), 1.12, .11, lf); for (const d of [-.22, .22]) box(.028, .028, .085, stM, X(s * .14), 1.12 + d, .065, lf);
    box(.3, .06, .07, mat(0x3a3d40), cx, 2.24, -.1, lf);                                              // доводчик
    const st = plane(.13, .065, mat(0xffffff, TX.sticker, { transparent: true }), lf); st.rotation.y = PI; st.position.set(X(s * .55), 1.22, -.005);
    const g = new T.Mesh(new T.PlaneGeometry(.9, 2.02), gm); g.position.set(cx, 1.28, 0); lf.add(g); o.leafGlass.push(g);
    if (s < 0) { const lk = cyl(.022, .022, .03, 12, mat(0xb8bcc0, null, { metal: .9, rough: .3 }), lf); lk.rotation.x = PI / 2; lk.position.set(X(-.1), 1.05, -.06); }
  }
}
function resetFacade(o) {
  const U = o.glassU; U.uRef.value = 1.7; U.uPrint.value = 0; U.uRain.value = 1; o.breath.visible = false; o.glass.visible = true; o.leaves.forEach(l => (l.rotation.y = 0)); o.reflBoost = 1; if (o.fogW) o.fogW.visible = false; if (o.wetSteps) o.wetSteps.visible = false;
}

/* ---------- 7 этаж: длинный коридор, двери, лампы, кабинет 713 ---------- */
function buildCorridor() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .05; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x4a5868, .36); S.add(o.amb);
  const len = 26, wd = 2.6;
  // пол — офисный линолеум с лёгким бликом (по нему ходит швабра уборщика)
  const flM = ps1(CINE ? litMat({ color: 0xffffff, map: TX.lino, bumpMap: TX.lino.bumpTex || null, bumpScale: 1.6 }, .3) : new T.MeshPhongMaterial({ color: 0xffffff, map: TX.lino, specular: 0x1e1e1e, shininess: 30 }));
  const fl = plane(wd, len, flM, S); fl.rotation.x = -PI / 2; fl.position.z = -len / 2;
  const ce = plane(wd, len, mat(0xffffff, TX.ceil), S); ce.rotation.x = PI / 2; ce.position.set(0, 3, -len / 2);
  const wm = mat(0xffffff, TX.wall), pm = mat(0xffffff, TX.panel, { decal: 1 });
  // стены; в левой — проём двери 707 (она приоткрыта, за ней темнота)
  const D7 = o.D7 = -11.8, dw = .95, dh = 2.1, zA = D7 + dw / 2, zB = D7 - dw / 2;
  // текстура по координатам мира: у кусков стены один масштаб рисунка, швов не видно
  const wall = (s, z0, z1, y0, y1, m) => { const w = plane(z0 - z1, y1 - y0, m, S); w.rotation.y = -s * PI / 2; w.position.set(s * (wd / 2 - (m === pm ? .005 : 0)), (y0 + y1) / 2, (z0 + z1) / 2);
    const uv = w.geometry.attributes.uv, H = m === pm ? .9 : 3; for (let i = 0; i < uv.count; i++) { const u = uv.getX(i), v = uv.getY(i); uv.setXY(i, -(s < 0 ? lerp(z0, z1, u) : lerp(z1, z0, u)) / len, lerp(y0, y1, v) / H); }
    return w; };
  wall(1, 0, -len, 0, 3, wm); wall(1, 0, -len, 0, .9, pm);
  // левая стена из кусков без Т-стыков (вершина одного куска посреди ребра другого даёт на телефоне светлую/тёмную щель до потолка)
  wall(-1, 0, zA, 0, dh, wm); wall(-1, 0, zA, dh, 3, wm); wall(-1, zA, zB, dh, 3, wm); wall(-1, zB, -len, dh, 3, wm); wall(-1, zB, -len, 0, dh, wm);
  wall(-1, 0, zA, 0, .9, pm); wall(-1, zB, -len, 0, .9, pm);
  const end = plane(wd, 3, wm, S); end.position.set(0, 1.5, -len);
  const st = plane(wd, 3, wm, S); st.rotation.y = PI; st.position.set(0, 1.5, 0);
  // комната за дверью 707: почти чёрная
  const rmM = mat(0x0c0c0d); const rm = room(3, 3, 3, rmM, S); rm.position.set(-wd / 2 - 1.5, 1.5, D7);
  const trM = mat(0x3a2a20);
  box(.03, dh + .07, .07, trM, -wd / 2 + .015, (dh + .07) / 2, zA + .035, S); box(.03, dh + .07, .07, trM, -wd / 2 + .015, (dh + .07) / 2, zB - .035, S); box(.03, .07, dw + .14, trM, -wd / 2 + .015, dh + .035, D7, S);
  box(.14, dh, .04, trM, -wd / 2 - .07, dh / 2, zA - .02, S);   // торец коробки, за который хватаются пальцы
  o.door7 = new T.Group(); o.door7.position.set(-wd / 2 - .02, 0, zB); S.add(o.door7);
  box(.05, dh - .02, dw - .03, mat(0x5c5c5c, TX.door), -.025, (dh - .02) / 2, (dw - .03) / 2, o.door7);   // створка внутри тёмной комнаты — в тени
  // двери по бокам с табличками
  // таблички с номерами: один коридор служит двумя этажами (7 и 10) — номера меняются (o.setFloor)
  o.plates = { 7: new T.Group(), 10: new T.Group() }; S.add(o.plates[7], o.plates[10]); o.plates[10].visible = false;
  let n = 1;
  for (let z = -2.5; z > -len + 2; z -= 3.1) for (const s of [-1, 1]) {
    if (!(s === -1 && Math.abs(z - D7) < .01)) { const d = box(.06, 2.1, .95, mat(0xffffff, TX.door), s * (wd / 2 - .03), 1.05, z, S); if (s === 1 && Math.abs(z + 5.6) < .01) o.doorR10 = d; }
    const i = n++;
    for (const fl of [7, 10]) { const num = fl * 100 + i; const pl = label(.2, .08, 32, 12, (c, w, h) => { c.fillStyle = '#c9b27a'; c.fillRect(0, 0, w, h); c.fillStyle = '#1b1b1b'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(num), w / 2, h / 2 + 1); }, o.plates[fl]);
      pl.position.set(s * (wd / 2 - .065), 1.75, z - .62); pl.rotation.y = -s * PI / 2; }
  }
  // номер этажа краской на торце у лифта (виден камере)
  o.floorSign = {}; for (const fl of [7, 10]) { const sg = label(.6, .5, 48, 40, (c, w, h) => { c.clearRect(0, 0, w, h); c.fillStyle = '#d8d4c6'; c.font = '800 30px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(fl), w / 2, h / 2 + 2); }, S);
    sg.rotation.y = PI; sg.position.set(.75, 1.75, -.012); sg.visible = fl === 7; o.floorSign[fl] = sg; }
  o.setFloor = fl => { for (const k of [7, 10]) { o.plates[k].visible = k === fl; o.floorSign[k].visible = k === fl; } };
  // офис на 10-м: одна дверь бывает приоткрыта (смена) — створка поворачивается внутрь, за ней свет или темнота
  o.door10 = new T.Group(); o.door10.position.set(wd / 2 - .03, 0, -5.6 - .475); S.add(o.door10); o.door10.visible = false;
  box(.05, 2.1, .95, mat(0xffffff, TX.door), 0, 1.05, .475, o.door10);
  o.hole10 = plane(.95, 2.1, mat(0x050506, null, { basic: true }), S); o.hole10.rotation.y = -PI / 2; o.hole10.position.set(wd / 2 - .004, 1.05, -5.6); o.hole10.visible = false;   // тёмный проём за створкой
  o.ajar10 = k => { o.doorR10.visible = !k; o.door10.visible = o.hole10.visible = !!k; o.door10.rotation.y = -.55 * (k || 0); o.hole10.material = o.holeDark; };
  o.holeDark = o.hole10.material; o.holeLit = new T.MeshBasicMaterial({ color: 0x9a8a68 }); ps1(o.holeLit);   // в офисе за дверью горит свет
  // длинные серые пальцы обхватывают косяк 707 изнутри (видны только в сцене с уборщиком)
  o.fingers = new T.Group(); o.fingers.position.set(-wd / 2, 0, zA); S.add(o.fingers); o.fingerF = [];
  // пальцы Жильца: длинные, тонкие, костлявые, разной длины и наклона; три фаланги, костяшки, тёмные ногти — обхватывают косяк по одному
  const skM = mat(0x767b70), shM = mat(0x1e201c), nlM = mat(0x0c0a08), knM = mat(0x62665d);
  const seg = (par, r1, r2, len, ax, m0 = skM) => { const m = cyl(r2, r1, len, 8, m0, par); if (ax === 'x') { m.rotation.z = -PI / 2; m.position.x = len / 2; } else { m.rotation.x = PI / 2; m.position.z = len / 2; } return m; };
  for (let i = 0; i < 4; i++) {
    const L1 = [.105, .128, .122, .096][i], L2 = [.082, .1, .095, .076][i], L3 = [.058, .07, .066, .052][i], r = [.0085, .0095, .0095, .008][i];
    const f = new T.Group(); f.position.set(-.085, 1.66 + [0, .052, .1, .142][i], -.006); f.rotation.y = (i - 1.5) * .05; f.rotation.z = [.14, .05, -.04, -.13][i]; o.fingers.add(f);
    seg(f, r * 1.25, r * 1.1, .13, 'x', shM); box(r * 2.6, r * 2.5, r * 2.6, shM, .125, 0, 0, f);   // в проёме — в тени; костяшка на ребре косяка
    const j1 = new T.Group(); j1.position.set(.13, 0, 0); j1.rotation.x = [.08, -.03, .04, -.1][i]; f.add(j1); seg(j1, r * 1.05, r * .85, L1, 'z');
    const j2 = new T.Group(); j2.position.set(0, 0, L1); j1.add(j2); box(r * 2, r * 1.9, r * 1.9, knM, 0, 0, 0, j2); seg(j2, r * .85, r * .7, L2, 'z');
    const j3 = new T.Group(); j3.position.set(0, 0, L2); j2.add(j3); box(r * 1.6, r * 1.5, r * 1.5, knM, 0, 0, 0, j3); seg(j3, r * .7, r * .45, L3, 'z');
    box(r * 1.15, r * .7, .026, nlM, 0, r * .25, L3 + .006, j3);
    o.fingerF.push({ f, j1, j2, j3 });
  }
  o.setFingers = k => {   // 0 — спрятаны в темноте, 1 — обхватили косяк (каждый палец ложится по очереди, с лёгким «доворотом»)
    o.fingers.visible = k > 0;
    o.fingerF.forEach(({ f, j1, j2, j3 }, i) => { const kk = clamp(k * 1.35 - [0, .22, .1, .3][i], 0, 1), ov = Math.sin(kk * PI) * .14;
      f.position.x = -.085 - (1 - kk) * .26; j1.rotation.y = -(1 - kk) * 1.2 - .02; j2.rotation.y = -.35 - [.62, .55, .7, .5][i] * kk - ov; j3.rotation.y = -.3 - [.55, .6, .5, .65][i] * kk - ov * .7; j2.rotation.x = [.06, -.04, .05, -.07][i] * kk; });
  };
  // уборщик: ведро с отжимом, табличка «мокрый пол», мокрое пятно, швабра на полу, кровавый след в проём
  o.bucket = makeBucket(); S.add(o.bucket);
  o.mopProp = makeMop(); S.add(o.mopProp);
  // офисное кресло: на 10-м ночью оказывается посреди коридора (странность смены)
  o.chair10 = new T.Group(); S.add(o.chair10); o.chair10.visible = false;
  { const cm = mat(0x5a5d64, null, { rough: .7 }), mm = mat(0x3a3c40, null, { metal: .6, rough: .4 }), wm = mat(0x0c0c0c);
    box(.5, .08, .48, cm, 0, .5, 0, o.chair10); const bk = box(.46, .58, .07, cm, 0, .86, -.24, o.chair10); bk.rotation.x = -.12;
    cyl(.025, .025, .4, 6, mm, o.chair10).position.y = .28;
    for (let i = 0; i < 5; i++) { const a = i / 5 * PI * 2, leg = box(.035, .03, .3, mm, Math.sin(a) * .15, .06, Math.cos(a) * .15, o.chair10); leg.rotation.y = a; box(.045, .045, .045, wm, Math.sin(a) * .29, .03, Math.cos(a) * .29, o.chair10); }
    for (const sd of [-1, 1]) box(.05, .04, .3, cm, sd * .26, .7, -.02, o.chair10); }
  o.sign = new T.Group(); S.add(o.sign);
  for (const s of [-1, 1]) { const pnl = plane(.3, .62, mat(0xffffff, TX.wetSign, { side: T.DoubleSide }), o.sign); pnl.position.set(0, .3, s * .085); pnl.rotation.x = s * -.28; pnl.rotation.y = s < 0 ? PI : 0; }
  o.wet = new T.Mesh(new T.PlaneGeometry(2.2, 3.2), ps1(new T.MeshLambertMaterial({ color: 0x0e0e10, map: TX.puddle, transparent: true, opacity: .4, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2 })));
  o.wet.rotation.x = -PI / 2; S.add(o.wet);
  o.sheen = new T.Mesh(new T.PlaneGeometry(1.6, 3.2), ps1(new T.MeshBasicMaterial({ map: TX.sheen, transparent: true, opacity: .5, depthWrite: false, blending: T.AdditiveBlending, polygonOffset: true, polygonOffsetFactor: -3 })));
  o.sheen.rotation.x = -PI / 2; o.wet.add(o.sheen); o.sheen.rotation.x = 0; o.sheen.position.z = .001;
  o.spill = new T.Mesh(new T.PlaneGeometry(1.1, .9), o.wet.material); o.spill.rotation.x = -PI / 2; S.add(o.spill);
  o.smear = new T.Mesh(new T.PlaneGeometry(.36, 2.4), ps1(new T.MeshLambertMaterial({ map: TX.smear, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -3 })));
  o.smear.rotation.order = 'YXZ'; o.smear.rotation.x = -PI / 2; S.add(o.smear);
  // дверь 713 в торце, полоска света под ней
  box(1, 2.1, .06, mat(0xffffff, TX.door), 0, 1.05, -len + .03, S);
  const p7 = label(.24, .1, 32, 12, (c, w, h) => { c.fillStyle = '#c9b27a'; c.fillRect(0, 0, w, h); c.fillStyle = '#5a0a0a'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('713', w / 2, h / 2 + 1); }, S); p7.position.set(0, 1.78, -len + .07);
  o.slit = box(.95, .025, .02, mat(0xfff0c0, null, { basic: true }), 0, .012, -len + .07, S);
  o.slitLight = new T.PointLight(0xffe0a0, .7, 2.5, 2); o.slitLight.position.set(0, .15, -len + .4); S.add(o.slitLight);
  // лампы под потолком
  o.lamps = [];
  for (let i = 0; i < 7; i++) {
    const z = -1.8 - i * 3.6;
    const m = new T.MeshBasicMaterial({ color: 0xe8f0e8 }); ps1(m);
    const tube = box(.9, .05, .25, m, 0, 2.97, z, S);
    const li = new T.PointLight(0xd8e8e0, 0, 7, 1.3); li.position.set(0, 2.7, z); S.add(li);
    o.lamps.push({ tube, li, on: 0 });
  }
  // двери лифта в начале коридора (за спиной камеры) и зелёная табличка
  const ex = plane(.5, .25, mat(0xffffff, TX.exit, { basic: true }), S); ex.position.set(0, 2.55, -len + .08); ex.position.x = .9;
  // табличка «СЕРВЕРНАЯ» поверх 713 — этот же коридор играет минус второй этаж
  o.srv = label(.5, .12, 96, 22, (c, w, h) => { c.fillStyle = '#7a7d80'; c.fillRect(0, 0, w, h); c.fillStyle = '#111'; c.font = '700 13px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(tr('СЕРВЕРНАЯ  −2'), w / 2, h / 2 + 1); }, S); o.srv.position.set(0, 1.78, -len + .09);
  // под фонарём табличка видна, только когда на неё светят (обычная — светится сама)
  o.srvBasic = o.srv.material; o.srvLit = ps1(new T.MeshLambertMaterial({ map: o.srv.material.map, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }));
  // лужа под телом (сцена «Жилец ест»)
  // лужа неровная: край — сумма синусов, плюс отдельные брызги (круг выглядел как красный коврик)
  const bl = new T.Shape(); for (let i = 0; i <= 48; i++) { const a = i / 48 * PI * 2, r = .82 * (1 + .22 * Math.sin(3 * a + 1) + .13 * Math.sin(7 * a + 2.3) + .07 * Math.sin(13 * a + .4));
    i ? bl.lineTo(Math.cos(a) * r, Math.sin(a) * r) : bl.moveTo(Math.cos(a) * r, Math.sin(a) * r); }
  const bg = [new T.ShapeGeometry(bl)]; for (const [x, y, r] of [[1.05, .35, .09], [-.95, -.5, .06], [.4, -1.02, .07], [1.25, -.2, .045], [-.3, .98, .05]]) bg.push(new T.CircleGeometry(r, 7).translate(x, y, 0));
  o.blood = new T.Mesh(T.BufferGeometryUtils ? T.BufferGeometryUtils.mergeGeometries(bg) : bg[0], new T.MeshLambertMaterial({ color: 0x2a0303, polygonOffset: true, polygonOffsetFactor: -4 }));
  if (!T.BufferGeometryUtils) for (const g2 of bg.slice(1)) o.blood.add(new T.Mesh(g2, o.blood.material)); o.blood.rotation.x = -PI / 2; o.blood.scale.set(1, 1.6, 1); o.blood.position.set(0, .006, -12); S.add(o.blood);
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.irL = new T.PointLight(0xffffff, 0, 16, 1.2); S.add(o.irL);   // ИК-подсветка камеры смены (темно — камера видит в ч/б)
  // кишки: цепочка влажных петель от живота тела к руке Жильца (концы ставит o.post — по костям после анимации)
  const gutM = ps1(new T.MeshPhongMaterial({ color: 0x7a1418, specular: 0x5a2a2a, shininess: 70, emissive: 0x1a0204 })), gutG = new T.SphereGeometry(.034, 6, 4);
  o.guts = new T.Group(); o.gutS = []; S.add(o.guts); o.guts.visible = false;
  for (let i = 0; i < 20; i++) { const m = new T.Mesh(gutG, gutM); m.scale.set(1, .85, 1.9); o.guts.add(m); o.gutS.push(m); }
  o.setGuts = (a, b, sag, t) => { const n = o.gutS.length;
    for (let i = 0; i < n; i++) { const u = i / (n - 1), m = o.gutS[i];
      m.position.set(lerp(a.x, b.x, u) + Math.sin(u * 9 + t * 3) * .03, Math.max(.035, lerp(a.y, b.y, u) - Math.sin(u * PI) * sag), lerp(a.z, b.z, u) + Math.cos(u * 7 + t * 2.4) * .03);
      if (i) m.lookAt(o.gutS[i - 1].position); } };
  // брызги крови при рывке: капли летят по дуге из груди тела и падают на пол (время — от начала кадра, без состояния)
  o.spray = new T.InstancedMesh(new T.BoxGeometry(.018, .018, .018), mat(0x4a0505, null, { basic: true }), 36); o.spray.visible = false; o.spray.frustumCulled = false; S.add(o.spray); o.bursts = null;
  const _sm = new T.Matrix4(), _sv = new T.Vector3();
  o.sprayTick = (from, t) => { const B = o.bursts; o.spray.visible = !!B; if (!B) return; let n = 0;
    for (const b of B) { const dt = t - b; if (dt < 0 || dt > .7) continue;
      for (let i = 0; i < 12 && n < 36; i++, n++) { const h1 = hash(b * 13 + i), h2 = hash(b * 7 + i * 3.1), h3 = hash(b + i * 9.7);
        const vx = (h1 - .5) * 1.6, vy = 1.2 + h2 * 1.6, vz = (h3 - .5) * 1.6, y = Math.max(.01, from.y + vy * dt - 4.9 * dt * dt);
        _sv.set(from.x + vx * dt, y, from.z + vz * dt); _sm.makeScale(1, y > .02 ? 1.8 : .3, 1).setPosition(_sv); o.spray.setMatrixAt(n, _sm); } }
    for (; n < 36; n++) { _sm.makeScale(0, 0, 0); o.spray.setMatrixAt(n, _sm); } o.spray.instanceMatrix.needsUpdate = true; };
  // капли крови с подбородка Жильца
  o.drips = []; const dM = mat(0x3a0406, null, { basic: true });
  for (let i = 0; i < 7; i++) { const d = box(.012, .03, .012, dM, 0, 0, 0, S); d.visible = false; o.drips.push(d); }
  // брызги на стене у 707
  const spT = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h);
    for (let i = 0; i < 46; i++) { const r = 1 + rnd() * rnd() * 7; x.fillStyle = `rgba(${70 + rnd() * 40 | 0},4,6,${.6 + rnd() * .4})`; x.beginPath(); x.arc(32 + (rnd() - .5) * 56 * rnd(), 28 + (rnd() - .5) * 44 * rnd(), r, 0, 7); x.fill(); }
    for (let i = 0; i < 10; i++) { x.fillStyle = 'rgba(64,4,6,.85)'; x.fillRect(12 + rnd() * 40, 22 + rnd() * 16, 1 + rnd() * 2, 10 + rnd() * 30); } });
  o.splat = plane(1.1, 1.3, ps1(new T.MeshLambertMaterial({ map: spT, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 })), S);
  o.splat.rotation.y = PI / 2; o.splat.position.set(-wd / 2 + .004, 1.15, zA + .45); o.splat.visible = false;
  const _h = new T.Vector3(), _bb = new T.Vector3();
  o.post = (A, used, t) => {
    const M = A.monster, eat = used.has('monster') && M.pose === 'eat';
    o.guts.visible = !!(eat && used.has('victim') && o.gutsOn);
    if (o.bursts && used.has('victim')) { A.victim.bone.spine_03.getWorldPosition(_bb); o.sprayTick(_bb, o.sprayT || 0); } else o.spray.visible = false;
    if (o.guts.visible) { M.bone.hand_r.getWorldPosition(_h); A.victim.bone.spine_01.getWorldPosition(_bb); o.setGuts(_bb, _h, .1, t); }
    const dr = eat || (used.has('monster') && M.gore > 0);
    if (dr) { M.bone.Head.getWorldPosition(_h); const ry = M.root.rotation.y; _h.x += Math.sin(ry) * .07; _h.z += Math.cos(ry) * .07; _h.y -= .1; }
    o.drips.forEach((d, i) => { d.visible = !!dr; if (!dr) return; const ph = (t * 1.1 + i * .143) % 1;
      d.position.set(_h.x + Math.sin(i * 2.1) * .04, Math.max(.01, _h.y - ph * _h.y), _h.z + Math.cos(i * 1.7) * .03); d.scale.y = 1 + ph * 2; });
  };
  // по умолчанию: уборщика ещё не было — ни ведра, ни следов; дверь 707 приоткрыта
  o.cleanSet = (c = {}) => {
    o.bucket.visible = !!c.bucket; if (c.bucket) { const [x, z, ry, tip] = c.bucket; o.bucket.position.set(x, 0, z); o.bucket.rotation.set(0, ry, 0); o.bucket.userData.tip(tip || 0); }
    o.mopProp.visible = !!c.mop; if (c.mop) { const [x, z, ry, k] = c.mop; o.mopProp.position.set(x, 0, z); o.mopProp.rotation.set(0, ry, 0); o.mopProp.userData.fall(k ?? 1); }
    o.sign.visible = !!c.sign; if (c.sign) { o.sign.position.set(c.sign[0], 0, c.sign[1]); o.sign.rotation.set(0, c.sign[2] || 0, c.sign[3] || 0); if (c.sign[3]) o.sign.position.y = .1; }
    o.wet.visible = !!c.wet; if (c.wet) { o.wet.position.set(c.wet[0], .004, c.wet[1]); const lm = o.lamps; o.sheen.material.opacity = .55 * clamp((lm[2].li.intensity + lm[3].li.intensity) / 4.8, 0, 1); }
    o.spill.visible = !!c.spill; if (c.spill) o.spill.position.set(c.spill[0], .005, c.spill[1]);
    o.smear.visible = !!c.smear; if (c.smear) { const [x0, z0, x1, z1] = c.smear, L = Math.hypot(x1 - x0, z1 - z0); o.smear.position.set((x0 + x1) / 2, .007, (z0 + z1) / 2); o.smear.scale.set(1, L / 2.4, 1); o.smear.rotation.y = Math.atan2(x0 - x1, z0 - z1); }
    o.door7.rotation.y = -(c.door7 ?? .72);
    o.setFingers(c.fingers || 0);
    o.splat.visible = !!c.splat;
    if (c.blood) { o.blood.visible = true; o.blood.position.set(-.75, .006, -11.8); o.blood.scale.set(1.1, 1, .9); }
  };
  o.reset = () => { o.bursts = null; o.faceL.intensity = 0; o.setFloor(7); o.ajar10(0); o.chair10.visible = false; o.blood.visible = false; o.blood.position.set(0, .006, -12); o.blood.scale.set(1, 1.6, 1); o.lamps[3].li.distance = 7; setLamps(o, 0); o.slit.visible = true; o.slitLight.intensity = 1.2; o.slitLight.color.setHex(0xffe0a0); o.amb.intensity = .36; o.srv.visible = false; o.srv.material = o.srvBasic; S.fog.density = o.fogD; o.flickOn = true; o.tubeOK = 1; o.cleanSet(); };
  o.tick = (t) => {   // одна ненадёжная лампа (пятая от лифта), если горит
    const l = o.lamps[4]; if (!o.flickOn || !l.on) { o.tubeOK = 1; return; }
    const f = unreliable(t, 3); o.tubeOK = f; l.li.intensity = l.base * f; l.tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c);
  };
  return o;
}
// ведро уборщика: жёлтое, с отжимом и колёсиками; tip(k) — опрокинуто
function makeBucket() {
  const g = new T.Group(), b = new T.Group(); g.add(b);
  const yM = mat(0xc9a21c), gM = mat(0x5a5e62), dM = mat(0x1a1c1e);
  box(.42, .3, .32, yM, 0, .2, 0, b); box(.44, .03, .34, mat(0xb08c14), 0, .36, 0, b);
  const wtr = box(.38, .01, .28, mat(0x2a3438, null, { emissive: 0x05080a }), 0, .3, 0, b);
  box(.16, .2, .26, gM, .12, .44, 0, b); box(.03, .4, .03, gM, .2, .62, .1, b); box(.03, .4, .03, gM, .2, .62, -.1, b); box(.03, .03, .23, gM, .2, .82, 0, b);   // отжим и ручка
  for (const [x, z] of [[-.17, .12], [.17, .12], [-.17, -.12], [.17, -.12]]) box(.04, .05, .04, dM, x, .025, z, b);
  g.userData.tip = k => { b.rotation.z = k * 1.45; b.position.set(k * .2, k * .14, 0); wtr.visible = k < .5; };
  return g;
}
// швабра на полу; fall(k): 0 — стоит, прислонённая; 1 — лежит
function makeMop() {
  const g = new T.Group(), m = new T.Group(); g.add(m);
  const st = cyl(.013, .013, 1.45, 6, mat(0x8a8f96), m); st.position.y = .74;
  box(.36, .035, .1, mat(0x3a3f6a), 0, .03, 0, m);
  for (let i = 0; i < 9; i++) { const f = box(.035, .025, .16, mat(0xb8b4a8), -.16 + i * .04, .012, .02, m); f.rotation.y = (rnd() - .5) * .5; }
  g.userData.fall = k => { const e = k * k; m.rotation.x = -e * (PI / 2 - .03) - (1 - e) * .12; m.position.y = e * .02; };
  return g;
}
function setLamps(o, n, fromNear = false) {   // n ламп горит: от дальней (по умолчанию) или от ближней
  o.lamps.forEach((l, i) => { const idx = fromNear ? i : o.lamps.length - 1 - i; const on = idx < n ? 1 : 0; l.on = on; l.base = on * 2.4; l.li.intensity = on * 2.4; l.tube.material.color.setHex(on ? 0xe8f0e8 : 0x1c1e1c); });
}

/* ---------- серверная / комната тринадцатой камеры ---------- */
function buildServer() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .085; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x4a5050, .24); S.add(o.amb);
  o.cold = new T.PointLight(0x7fd0b0, 1.2, 7, 1.4); o.cold.position.set(-2.4, 2.4, 0); S.add(o.cold);
  o.cold2 = new T.PointLight(0x7fd0b0, 1.0, 7, 1.4); o.cold2.position.set(2.4, 2.4, -1); S.add(o.cold2);
  const fl = plane(8, 7, mat(0xffffff, TX.concrete), S); fl.rotation.x = -PI / 2;
  const ce = plane(8, 7, mat(0x2a2a2a), S); ce.rotation.x = PI / 2; ce.position.y = 3;
  const wm = mat(0xffffff, TX.concrete);
  for (const [w, x, z, ry] of [[8, 0, -3.5, 0], [7, -4, 0, PI / 2], [7, 4, 0, -PI / 2], [8, 0, 3.5, PI]]) { const p = plane(w, 3, wm, S); p.position.set(x, 1.5, z); p.rotation.y = ry; }
  const rm = mat(0xffffff, TX.rack, { emissive: 0x0a140c });
  for (let i = 0; i < 6; i++) { box(.8, 2.1, .9, rm, -3.4, 1.05, -2.6 + i * 1.0, S); box(.8, 2.1, .9, rm, 3.4, 1.05, -2.6 + i * 1.0, S); }
  for (let i = 0; i < 4; i++) box(.9, 2.1, .8, rm, -1.5 + i * 1.0, 1.05, -3.1, S);
  for (let i = 0; i < 12; i++) { const c = box(.03, .03, 3 + rnd() * 2, mat(0x101010), lerp(-3, 3, rnd()), 2.95 - rnd() * .2, 0, S); c.rotation.y = rnd() * PI; }
  o.salt = plane(2.2, 2.2, new T.MeshBasicMaterial({ map: TX.salt, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), S); o.salt.rotation.x = -PI / 2; o.salt.position.y = .012; ps1(o.salt.material);
  for (let i = 0; i < 5; i++) { const c = cyl(.02, .025, .12, 6, mat(0xe8e0c8, null, { emissive: 0x302010 }), S); const a = i / 5 * PI * 2; c.position.set(Math.cos(a) * 1.25, .06, Math.sin(a) * 1.25); }   // свечи
  o.candle = new T.PointLight(0xffb060, 1.2, 5, 1.6); o.candle.position.set(0, .5, .4); S.add(o.candle);
  o.red = new T.PointLight(0xff2020, .9, 9, 1.5); o.red.position.set(0, 2.8, 2.6); S.add(o.red);
  box(.25, .12, .08, mat(0xff2020, null, { basic: true }), 0, 2.92, 3.4, S);
  o.door = box(.94, 2.0, .06, mat(0x6a6e70, TX.metal, { metal: .55, rough: .5 }), 2, 1.0, 3.47, S);   // железная — как со стороны подвала
  o.rim = new T.PointLight(0xa0c0d0, 2.6, 5, 1.3); o.rim.position.set(1.4, 2.7, .9); S.add(o.rim);
  box(.18, .12, .3, mat(0xd0d0d0), 3.7, 2.8, 3.2, S);   // сама камера 13
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.reset = () => { o.faceL.intensity = 0; o.salt.visible = true; o.candle.intensity = 1.2; o.red.intensity = 1.4; o.amb.intensity = .24; o.cold.intensity = 1.35; o.cold2.intensity = 1.1; o.rim.intensity = 2.6; S.fog.density = o.fogD; o.flickOn = true; o.tubeOK = 1; o.cold2Base = null; };
  o.tick = (t) => {
    o.candle.intensity = 1.1 + Math.sin(t * 17) * .12 + Math.sin(t * 7) * .08; o.red.intensity = 1.0 + .6 * (Math.sin(t * 2.4) > 0 ? 1 : 0);
    if (o.cold2Base == null) o.cold2Base = o.cold2.intensity;   // ненадёжная холодная лампа над стойками справа
    const f = o.flickOn ? unreliable(t, 5) : 1; o.tubeOK = f; o.cold2.intensity = o.cold2Base * (f ? 1 : .08);
  };
  return o;
}

// UV по размеру в метрах: рисунок одной плотности на кусках любого размера (у текстуры своя повторяемость rx×ry)
function uvM(me, w, h, tile = 1.6) { const uv = me.geometry.attributes.uv, m = me.material.map, rx = m ? m.repeat.x : 1, ry = m ? m.repeat.y : 1;
  for (let i = 0; i < uv.count; i++) uv.setXY(i, uv.getX(i) * w / (tile * rx), uv.getY(i) * h / (tile * ry)); uv.needsUpdate = true; return me; }
function wplane(w, h, m, S, x, y, z, ry = 0, tile) { const p = uvM(plane(w, h, m, S), w, h, tile); p.position.set(x, y, z); p.rotation.y = ry; return p; }
// ---- лестница (CAM 04; Б1 — вниз с фонариком, наверх с Фрэнком): три уровня по 3 м, марши по 10 ступеней, площадки, перила ----
// уровень k: площадка у двери этажа (z 0…−1.4) на высоте 3k; левый марш вверх к средней площадке (z −4.4…−5.9, +1.5); правый — к следующему уровню
function buildStairs() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .06; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x505a60, .3); S.add(o.amb);
  const cm = mat(0xffffff, TX.concrete), wm = mat(0xc8d0c8, TX.wall), paint = mat(0x3c5048, TX.wall), rail = mat(0x2a2c2e, null, { metal: .6, rough: .45 }), N = 3, W = 2.8, X0 = .12;
  for (const [w, x, z, ry] of [[5.9, -W / 2, -2.95, PI / 2], [5.9, W / 2, -2.95, -PI / 2], [W, 0, -5.9, 0], [W, 0, 0, PI]]) {
    wplane(w, 9.4, wm, S, x, 4.4, z, ry, 2.2);
    for (let k = 0; k < N; k++) { const b = wplane(w, 1.25, paint, S, x, 3 * k + .62, z, ry, 2.2); b.position.x += Math.sign(-x) * .004; b.position.z += Math.sign(-z || 0) * .004; }   // панель масляной краски
  }
  const ceil = wplane(W, 5.9, mat(0x2a2a2a), S, 0, 9.1, -2.95); ceil.rotation.x = PI / 2;
  const fl = wplane(W, 5.9, cm, S, 0, -.001, -2.95); fl.rotation.x = -PI / 2;
  const slope = Math.atan2(1.5, 3), sl = Math.hypot(1.5, 3);
  o.doors = []; o.signs = []; o.bulbs = [];
  for (let k = 0; k < N; k++) {
    const y0 = 3 * k;
    if (k) box(W, .2, 1.4, cm, 0, y0 - .1, -.7, S);                          // площадка у двери этажа
    box(W, .2, 1.5, cm, 0, y0 + 1.4, -5.15, S);                              // средняя площадка
    for (const [sx, up] of [[-1, 0], [1, 1]]) {                               // левый марш — с площадки этажа вверх, правый — от средней вверх
      if (k === N - 1 && up) continue;
      const xc = sx * (X0 + (W / 2 - X0) / 2), w = W / 2 - X0 - .02, ya = y0 + 1.5 * up;
      for (let i = 0; i < 10; i++) { const yt = ya + (i + 1) * .15, z = up ? -4.4 + (i + .5) * .3 : -1.4 - (i + .5) * .3; box(w, .3, .3, cm, xc, yt - .15, z, S); }
      const sof = box(w, .12, sl, cm, xc, ya + .75 - .2, -2.9, S); sof.rotation.x = up ? -slope : slope;   // косоур снизу (без «висящих» ступеней)
      // перила по внутреннему краю: стойки и поручень вдоль марша
      const xr = sx * (X0 + .03);
      for (let i = 0; i <= 6; i++) { const u = i / 6, z = up ? lerp(-4.4, -1.4, u) : lerp(-1.4, -4.4, u), y = ya + 1.5 * u; box(.025, .9, .025, rail, xr, y + .45, z, S); }
      const hr = cyl(.022, .022, sl + .1, 8, rail, S); hr.position.set(xr, ya + .75 + .92, -2.9); hr.rotation.x = up ? PI / 2 - slope : slope - PI / 2;
    }
    // дверь этажа на передней стене (справа), коробка, номер этажа краской слева от двери
    const dr = box(.9, 2.05, .05, mat(0x5a6a66, TX.door), .75, y0 + 1.025, -.03, S); o.doors.push(dr);
    box(1.0, .07, .08, rail, .75, y0 + 2.08, -.04, S); for (const dx of [-.48, .48]) box(.06, 2.1, .08, rail, .75 + dx, y0 + 1.05, -.04, S);
    const sg = label(.5, .4, 50, 40, (c, w, h, s = String(k - 2)) => { c.clearRect(0, 0, w, h); c.fillStyle = '#d8d4c6'; c.font = '800 30px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(s, w / 2, h / 2 + 2); }, S);
    sg.rotation.y = PI; sg.position.set(-.35, y0 + 1.65, -.012); o.signs.push(sg);
    // лампа-«бочка» над средней площадкой и над дверью
    for (const [lx, ly, lz] of [[0, y0 + 3.9, -5.85], [.75, y0 + 2.45, -.06]]) {
      const sh = box(.2, .12, .08, mat(0xfff2d0, null, { basic: true }), lx, ly, lz, S);
      const li = new T.PointLight(0xffe2b0, 1.5, 7, 1.5); li.position.set(lx, ly - .12, lz + (lz < -3 ? .3 : -.3)); S.add(li); o.bulbs.push({ li, sh, base: 1.5 });
    }
  }
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.irL = new T.PointLight(0xffffff, 0, 16, 1.2); S.add(o.irL);
  o.setFloor = (k, n) => relabel(o.signs[k], String(n));   // какой этаж написан на уровне k (для разных камер)
  o.reset = () => { o.faceL.intensity = 0; o.amb.intensity = .3; S.fog.density = o.fogD; o.bulbs.forEach(l => { l.li.intensity = l.base; l.sh.material.color.setHex(0xfff2d0); }); o.flick = null; o.signs.forEach((s, k) => o.setFloor(k, k - 2)); };
  o.tick = t => { if (o.flick) o.bulbs.forEach((l, i) => { const on = o.flick(t, i); l.li.intensity = l.base * on; }); };
  return o;
}
// ---- подвал (CAM −2; Б1): бетонный коридор, трубы, лампы в сетке; в конце — железная дверь серверной, под ней полоска тёплого света.
// за дверью — кусок серверной у порога: круг соли, свеча (сама серверная — локация server, для её камеры)
function buildBasement() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .075; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x48504c, .22); S.add(o.amb);
  const cm = mat(0xffffff, TX.concrete), wl = mat(0x9aa098, TX.wall, { bump: .6 }), paint = mat(0x34424c, TX.wall, { bump: .6 }), len = 14, wd = 2.4, hgt = 2.6, D = -len;
  const fl = wplane(wd, len, cm, S, 0, 0, -len / 2); fl.rotation.x = -PI / 2;
  const ce = wplane(wd, len, mat(0x34342f, TX.wall, { bump: .4 }), S, 0, hgt, -len / 2, 0, 2.4); ce.rotation.x = PI / 2;
  for (const s of [-1, 1]) { wplane(len, hgt, wl, S, s * wd / 2, hgt / 2, -len / 2, -s * PI / 2, 2.4); const b = wplane(len, 1.1, paint, S, s * (wd / 2 - .004), .55, -len / 2, -s * PI / 2, 2.4); }
  const back = wplane(wd, hgt, wl, S, 0, hgt / 2, 0, PI, 2.4);
  // лужи
  for (const [x, z, s] of [[-.4, -3.2, 1.2], [.5, -8.6, .9], [-.2, -11.4, 1.4]]) { const p = plane(s, s * .7, mat(0xffffff, TX.puddle, { transparent: true, opacity: .5, decal: 1 }), S); p.rotation.x = -PI / 2; p.position.set(x, .004, z); }
  // трубы и лоток с кабелями под потолком
  const pm = mat(0x3a3e40, null, { metal: .5, rough: .5 }), rust = mat(0x5a4636, null, { rough: .8 });
  for (const [x, y, r, m] of [[-.85, 2.36, .07, pm], [-.6, 2.44, .045, rust], [-1.02, 2.12, .055, pm]]) { const c = cyl(r, r, len, 10, m, S); c.rotation.x = PI / 2; c.position.set(x, y, -len / 2); }
  box(.32, .04, len, mat(0x4a4c4e, null, { metal: .4 }), .85, 2.32, -len / 2, S);
  // боковые кладовки (закрыты)
  for (const z of [-4.2, -9.6]) { box(.05, 2.0, .85, mat(0x4c4a44, TX.door), -wd / 2 + .03, 1.0, z, S); }
  // лампы в сетке: средняя ненадёжная
  o.bulbs = [];
  for (const z of [-2.4, -7.2, -12.0]) {
    const bulb = box(.08, .1, .08, mat(0xffe8b8, null, { basic: true }), 0, hgt - .14, z, S);
    for (let i = 0; i < 4; i++) { const w = box(.012, .14, .012, mat(0x202020), Math.cos(i * PI / 2) * .06, hgt - .12, z + Math.sin(i * PI / 2) * .06, S); }
    const li = new T.PointLight(0xffd8a0, 1.6, 6.5, 1.6); li.position.set(0, hgt - .25, z); S.add(li); o.bulbs.push({ li, bulb, base: 1.6 });
  }
  // торцевая стена с проёмом двери (за ней — кусок серверной)
  wplane(wd / 2 - .52, hgt, wl, S, -(wd / 4 + .26), hgt / 2, D, 0, 2.4); wplane(wd / 2 - .52, hgt, wl, S, wd / 4 + .26, hgt / 2, D, 0, 2.4); wplane(1.04, hgt - 2.1, wl, S, 0, (hgt + 2.1) / 2, D, 0, 2.4);
  // дверь серверной: железо, открывается наружу (в коридор), петли справа; засов изнутри. Табличка над дверью
  const fr = mat(0x2a2c2e, null, { metal: .6 });
  box(1.04, .08, .1, fr, 0, 2.06, D + .02, S); for (const dx of [-.52, .52]) box(.08, 2.1, .1, fr, dx, 1.05, D + .02, S);
  o.sdoor = new T.Group(); o.sdoor.position.set(.47, 0, D + .02); S.add(o.sdoor);
  const dm = mat(0x6a6e70, TX.metal, { metal: .55, rough: .5 });
  box(.94, 2.0, .05, dm, -.47, 1.0, 0, o.sdoor); box(.12, .025, .05, fr, -.85, 1.02, .05, o.sdoor);   // полотно, ручка
  for (const y of [.25, 1.75]) box(.7, .06, .012, mat(0x50565a, null, { metal: .5 }), -.47, y, .03, o.sdoor);   // рёбра жёсткости
  const sg = label(.7, .16, 70, 16, (c, w, h) => { c.fillStyle = '#1e3a6e'; c.fillRect(0, 0, w, h); c.fillStyle = '#e8eef8'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(tr('СЕРВЕРНАЯ'), w / 2, h / 2 + 1); }, S);
  sg.rotation.y = 0; sg.position.set(0, 2.25, D + .08);
  // полоска света под дверью: свеча за дверью (её свет на полу коридора — мягкое пятно)
  o.slit = plane(.86, .014, mat(0xffb060, null, { basic: true }), S); o.slit.position.set(0, .008, D + .045);
  o.glow = plane(1.2, .6, new T.MeshBasicMaterial({ map: TX.fogBreath, transparent: true, opacity: .55, depthWrite: false, blending: T.AdditiveBlending, color: 0xff9a40 }), S);
  o.glow.rotation.x = -PI / 2; o.glow.position.set(0, .006, D + .3);
  // за дверью: пол, стены, стойки, круг соли у порога, свеча
  const rm = room(3.4, hgt, 3.6, mat(0x1c1e1e, TX.concrete), S); rm.position.set(0, 0, D - 1.8);
  for (const x of [-1.2, 1.2]) box(.6, 2.0, .8, mat(0xffffff, TX.rack, { emissive: 0x081008 }), x, 1.0, D - 2.6, S);
  o.salt = plane(1.9, 1.9, new T.MeshBasicMaterial({ map: TX.salt, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), S);
  o.salt.rotation.x = -PI / 2; o.salt.position.set(0, .012, D - .95); ps1(o.salt.material);
  const cnd = cyl(.022, .026, .14, 8, mat(0xe8e0c8, null, { emissive: 0x302010 }), S); cnd.position.set(-.55, .07, D - .55);
  o.candle = new T.PointLight(0xffb060, 1.0, 4, 1.6); o.candle.position.set(-.55, .32, D - .55); S.add(o.candle);
  o.prints = new T.Group(); S.add(o.prints); o.prints.visible = false;
  { const hm = mat(0xd8d6cc, TX.hand, { transparent: true, opacity: .55, decal: 1 });   // руки в соли из круга — следы белёсые
    for (let i = 0; i < 16; i++) { const u = i / 15, z = lerp(D - 1.0, D + 3.6, u), x = Math.sin(i * 1.7) * .12 + (i % 2 ? .13 : -.13);
      const h = plane(.15, .17, hm, o.prints); h.rotation.x = -PI / 2; h.rotation.z = PI + (i % 2 ? -.25 : .25) + Math.sin(i) * .15; h.position.set(x, .007 + i * .0001, z); h.scale.x = i % 2 ? -1 : 1; } }
  o.etorch = new T.SpotLight(0xfff0d8, 0, 9, .34, .55, 1.2); S.add(o.etorch, o.etorch.target);   // фонарь в руке Итана (кадры со стороны)
  o.roomL = new T.PointLight(0xffd8b0, 0, 4.5, 1.6); o.roomL.position.set(-.45, 1.1, D - 1.1); S.add(o.roomL);   // свечи в серверной: свет на Фрэнка у порога
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.irL = new T.PointLight(0xffffff, 0, 16, 1.2); S.add(o.irL);
  o.reset = () => { o.faceL.intensity = 0; o.amb.intensity = .22; S.fog.density = o.fogD; o.sdoor.rotation.y = 0; o.prints.visible = false; o.etorch.intensity = 0; o.roomL.intensity = 0; o.roomL.distance = 4.5; o.roomL.position.set(-.45, 1.1, D - 1.1); o.glow.scale.set(1, 1, 1); o.slit.visible = o.glow.visible = true; o.salt.visible = true; o.candle.intensity = 1;
    o.bulbs.forEach(l => { l.li.intensity = l.base; l.bulb.material.color.setHex(0xffe8b8); }); o.flickOn = true; };
  o.tick = t => { o.candle.intensity = .9 + Math.sin(t * 17) * .1 + Math.sin(t * 6.3) * .07; o.glow.material.opacity = .45 + .08 * Math.sin(t * 9);
    if (o.flickOn) { const f = unreliable(t, 9); const l = o.bulbs[1]; l.li.intensity = l.base * (f ? 1 : .05); l.bulb.material.color.setHex(f ? 0xffe8b8 : 0x2a261c); } };
  return o;
}
/* ---------- кадры ---------- */
// act: [актёр, x, y, z, поворот, поза, куда смотрит ('cam' | [x,y,z] | null)]
const lin = (a, b, k) => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
// идти по плавной дуге (точки [x, z]): v м/с, через t секунд; поворот корпуса — по касательной, на старте и в конце мягкий
const PATHS = {};
function walkPath(key, pts, t, v) {
  const C = PATHS[key] || (PATHS[key] = new T.CubicBezierCurve(...pts.map(p => new T.Vector2(p[0], p[1]))));
  const L = C.getLength(), s0 = v * t, u = clamp(s0 / L, 0, 1), p = C.getPointAt(u), d = C.getTangentAt(Math.min(u, .999));
  return { x: p.x, z: p.y, ry: Math.atan2(d.x, d.y), done: s0 >= L, s: Math.min(s0, L), L };
}
// свет для сцены «Жилец ест»: горит одна лампа прямо над ними и подмигивает
function torchSet(o, mon) { setLamps(o, 0); o.slitLight.intensity = 0; o.slit.visible = false; o.amb.intensity = .08; o.srv.visible = true; o.srv.material = o.srvLit; return mon ? [['monster', .15, 0, -18.2, 0, 'monster', 'cam']] : []; }
// когда Жилец ест: тело ещё вздрагивает, на рывках — брызги и тряска головы (как собака треплет добычу)
function eatGore(o, t) { o.bursts = [.9, 3.3, 5.7, 8.1].filter(b => t > b - .1 && t < b + .8); if (!o.bursts.length) o.bursts = null; o.sprayT = t; A.victim.twitch = .5;
  A.monster.shake = [.9, 3.3, 5.7, 8.1].some(b => t > b - .05 && t < b + .35) ? .3 : 0; }
function eatLight(o, t) { setLamps(o, 0); o.flickOn = false; const l = o.lamps[3], on = Math.sin(t * 9) > .85 && Math.sin(t * 1.7) > 0 ? .25 : 1; l.li.intensity = 4.2 * on; l.li.distance = 9; o.amb.intensity = .42; l.tube.material.color.setHex(on > .5 ? 0xe8f0e8 : 0x30322e); o.blood.visible = true; o.blood.position.set(-.75, .006, -11.8); o.blood.scale.set(1.25, 1, 1); o.gutsOn = true; o.splat.visible = true; o.slitLight.intensity = 0; o.slit.visible = false; A.monster.gore = 1; A.victim.gore = 2;
  o.cleanSet(AFTER); }
// седьмой этаж после уборщика: швабра на полу, ведро опрокинуто, вода, кровавый след волоком к приоткрытой 707
const CLEAN = { bucket: [.6, -10.35, .3], sign: [-.85, -7.6, .5], wet: [-.1, -9.9], door7: .72 }, MOPLEAN = [.43, -10.08, .25, 0];
const AFTER = { mop: [.43, -10.08, .25, 1], bucket: [.6, -10.35, .3, 1], spill: [.82, -10.3], sign: [-.85, -7.6, .5], smear: [.0, -9.6, -1.05, -11.7], door7: .85, splat: 1, blood: 1 };
function stopLamps(o, t) { o.flickOn = false; lampSet(o, i => i < 3 || (i === 3 ? (t < .9 || (t < 2.4 ? stut(t, 1) : Math.sin(t * 13) > .7)) : (t < .9 || (t < 2.1 && stut(t, i * 3))))); }
// лампы коридора по одной: fn(i) → горит ли i-я (0 — у лифта, 6 — у 713)
function lampSet(o, fn) { o.lamps.forEach((l, i) => { const on = fn(i) ? 1 : 0; l.on = on; l.base = on * 2.4; l.li.intensity = on * 2.4; l.tube.material.color.setHex(on ? 0xe8f0e8 : 0x1c1e1c); }); }
const stut = (t, s) => Math.sin(t * 37 + s) * Math.sin(t * 11.3 + s * 2.1) > -.25 ? 1 : 0;   // лампа «захлёбывается»
// бросок Жильца в лицо: стоит вдали в темноте (или подходит рывками между миганиями света) → за доли секунды оказывается
// вплотную к объективу, пасть раскрывается, лицо выхватывает холодный свет — как в FNAF/Outlast, только без «бума» для детей
function lunge(o, t, sh, cf) {
  const at = cf.at ?? 1.4, P = sh.pos, dx = sh.look[0] - P[0], dz = sh.look[2] - P[2], hl = Math.hypot(dx, dz), ux = dx / hl, uz = dz / hl;
  let d = cf.far ?? 3.5, k = 0;
  for (const [ts, dd] of cf.steps || []) if (t >= ts) d = dd;
  if (t >= at) { const e = clamp((t - at) / (cf.dur || .22), 0, 1); k = e * e; d = lerp(d, cf.near ?? .5, k); }
  const headY = lerp(cf.headY ?? 2.25, P[1] - .02, Math.min(1, k * 1.15));
  const C = A.monster; C.faceSet = 'scream'; C.scream = clamp((t - at + .05) / .16, 0, 1); C.shake = t > at - .45 && t < at + .06 ? .7 : t >= at + .06 ? .12 : 0; C.noTilt = t >= at - .15; C.aim = { k: Math.min(1, k * 1.15), on: t >= at - .15 };
  o.faceL.position.set(P[0] + ux * .12, P[1] - .22, P[2] + uz * .12); o.faceL.distance = Math.max(2.2, d + 1.2);
  o.faceL.intensity = t < at ? (cf.glow || 0) : (Math.sin(t * 57) > -.35 ? 3.4 : .9);
  if (CINE && t >= at + .04) { if (o.lamps) setLamps(o, 0); o.amb.intensity *= .35; }   // «Кино»: в момент броска свет гаснет — из темноты только лицо
  if (t < (cf.show ?? 0) || (cf.blink && t < at && (t % .5) < .13)) return [];
  const jit = t >= at ? (Math.sin(t * 83) + Math.sin(t * 47)) * .05 : 0;
  return [['monster', P[0] + ux * d, headY - 2.25, P[2] + uz * d, Math.atan2(-ux, -uz) + jit, 'reach', 'cam']];
}
// у стекла: ступни в z от стекла (z = 6), ладонь и кулак ровно на стекле (glassD — от ступней до стекла, пальцы выходят на 2–3 см дальше запястья)
function atGlass(k, pose, look, z = 6.27) { A[k].glassD = z - 6 - .03; return [[k, .62, 0, z, PI, pose, look]]; }
// пар от дыхания Тёмыча на стекле изнутри: вдох-выдох раз в 3.6 с; с её стороны стекло чистое
function breath(o, t) { const ph = (t + .9) % 3.6, a = ph < .4 ? ph / .4 : Math.max(0, 1 - (ph - .4) / 2.5); o.breath.visible = a > 0; o.breath.material.opacity = .55 * a; o.breath.scale.setScalar(.85 + .25 * Math.min(1, ph / .7)); }
const SH = {
  hall_cam: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o, t) => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['monster', -3.3, 0, -5.1, .5, 'monster', 'cam']] },
  hall_cam_empty: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]]] },
  hall_cam_near: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['monster', -1.2, 0, -2.4, .8, 'monster', 'cam']] },
  hall_cam_nearer: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['monster', 2.2, 0, -.6, 1.2, 'monster', 'cam']] },
  // сменщик приходит в шесть: от входа к будке, огибая колонну
  hall_dawn: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o, t) => { setDawn(o, 1); const w = walkPath('dawn', [[.3, 5.3], [2.0, 4.9], [3.6, 3.9], [4.4, 1.6]], t + 2.6, 1.1);
    return [['smenshik', w.x, 0, w.z, w.ry, w.done ? 'stand' : 'walk', w.done ? [5.2, 1.5, -.3] : null]]; } },
  exterior: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o, t) => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]]] },
  exterior_dawn: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o) => { setDawn(o, 1); return []; } },
  // курит: крупно, на рассвете под козырьком; огонёк зажигалки на миг освещает лицо
  exterior_smoke: { loc: 'lobby', pos: [-.35, 1.52, 9.45], look: [.5, 1.4, 8.2], fov: 38, set: (o, t) => { setDawn(o, 1); A.mihalych.actT = t - .15;
    const tt = t - .15; if (tt > 3.95 && tt < 4.6) { o.faceL.position.set(.36, 1.55, 8.42); o.faceL.color.setHex(0xffa040); o.faceL.distance = 1.6; o.faceL.intensity = 1.4 + .4 * Math.sin(t * 40); }
    return [['mihalych', .55, 0, 8.2, -.6, 'smoke', null]]; } },
  exterior_two_smoke: { loc: 'lobby', pos: [2.2, 1.5, 15], look: [0, 2.2, 6], fov: 55, set: (o) => { setDawn(o, 1); A.mihalych.actT = 99; return [['temych', -.55, 0, 8.3, 1.05, 'stand', [.5, 1.62, 8.2]], ['mihalych', .55, 0, 8.2, -.75, 'smoke', [-.55, 1.7, 8.3]]]; } },
  exterior_two: { loc: 'lobby', pos: [2.2, 1.5, 15], look: [0, 2.2, 6], fov: 55, set: (o) => { setDawn(o, 1); return [['temych', -.55, 0, 8.3, 1.05, 'stand', [.5, 1.62, 8.2]], ['mihalych', .55, 0, 8.2, -.75, 'stand', [-.55, 1.7, 8.3]]]; } },   // вышли под навес: стоят друг к другу вполоборота
  guard: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, face: { smenshik: 'worry' }, set: () => [['smenshik', 5.12, 0, .85, PI, 'stand', 'cam']] },
  guard_keys: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.4, .9], fov: 48, face: { smenshik: 'worry' }, set: (o) => { o.keys.visible = false; return [['smenshik', 5.12, 0, .8, PI, 'give', 'cam']]; } },
  guard_dawn: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: (o) => { setDawn(o, 1); A.smenshik.faceSet = 'smile'; return [['smenshik', 5.12, 0, .85, PI, 'stand', 'cam']]; } },
  desk_note: { loc: 'lobby', pos: [5.3, 1.3, -.62], look: [5.45, .78, -.12], fov: 55, set: () => [] },
  note: { loc: 'lobby', pos: [5.36, 1.13, -.44], look: [5.35, .8, -.17], fov: 44, set: () => [] },
  monitors: { loc: 'lobby', pos: [5.55, 1.42, -1.15], look: [4.5, 1.18, .12], fov: 48, feeds: true, set: () => [] },
  booth_dark: { loc: 'lobby', pos: [5.55, 1.42, -1.15], look: [4.5, 1.18, .12], fov: 54, feeds: true, set: (o) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .15; o.l3.intensity = .1; o.amb.intensity = .25; return []; } },
  elevator_open: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 1.25, -6], fov: 46, set: (o) => { setLift(o, o.lifts[0], 1, '1'); o.cabLight.intensity = 2.4; return []; } },
  elevator_floors: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 1.7, -6], fov: 46, set: (o, t) => { const f = Math.min(7, 1 + Math.floor(t / .6)); setLift(o, o.lifts[0], 0, String(f), f < 7); return []; } },
  elevator_inside: { loc: 'lobby', pos: [-2.2, 1.5, -7.5], look: [-2.2, 1.75, -5.8], fov: 66, set: (o, t) => { o.cabLight.intensity = (Math.sin(t * 23) > -.6) ? 1.6 : .3; relabel(o.cabInd, '13'); return []; } },
  door_katya: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 6.5], fov: 50, face: { katya: 'plead' }, set: () => atGlass('katya', 'press', 'cam') },
  door_back: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 7], fov: 50, set: (o, t) => { o.glassU.uPrint.value = clamp((t - .9) / .6, 0, 1);   // на стекле остаётся сухой отпечаток её ладони
    return [['katyaSmile', .62, 0, 6.3 + Math.max(0, t - .6) * .55, PI, t > .6 ? 'back' : 'stand', 'cam']]; } },
  entrance_cam: { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [.5, .8, 6.2], fov: 64, cctv: 1, set: (o) => { o.rain.material.opacity = .18; o.reflBoost = .6; return atGlass('katya', 'press', [.6, 1.5, 4]); } },
  katya_face: { loc: 'lobby', pos: [.6, 1.49, 5.4], look: [.62, 1.47, 6.5], fov: 34, face: { katya: 'plead' }, set: (o, t) => { breath(o, t); return atGlass('katya', 'press', 'cam'); } },
  katya_smile: { loc: 'lobby', pos: [.6, 1.49, 5.4], look: [.62, 1.47, 6.5], fov: 34, set: (o, t) => { breath(o, t); return [['katyaSmile', .62, 0, 6.3, PI, 'stand', 'cam']]; } },
  // кинулась на шею: подбегает вплотную, руки обхватывают того, кто смотрит (уходят за края кадра)
  katya_inside: { loc: 'lobby', pos: [.5, 1.6, 3.7], look: [.3, 1.45, 4.6], fov: 50, face: { katya: 'plead' }, set: (o, t) => { const k = clamp(t / .45, 0, 1); A.katya.actT = t;
    return [['katya', lerp(.2, .42, k), 0, lerp(4.9, 4.28, k * k), PI + .1, 'embrace', 'cam']]; } },
  katya_inside_still: { loc: 'lobby', pos: [.3, 1.5, 3.95], look: [.2, 1.47, 4.75], fov: 36, set: () => { A.katya.talk = 0; return [['katyaSmile', .2, 0, 4.7, PI, 'stand', 'cam']]; } },
  corridor_cam: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t) => { setLamps(o, [1.2, 2.3, 3.4, 4.5].filter(x => t >= x).length * 2 - 1); o.amb.intensity = .62; return []; } },
  corridor_lit: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, cctv: 1, set: (o) => { setLamps(o, 7); return []; } },
  corridor_lit_after: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, cctv: 1, set: (o) => { setLamps(o, 7); o.cleanSet(AFTER); return []; } },   // то же после уборщика: швабра, ведро, след
  corridor_off: { loc: 'corridor', pos: [0, 1.6, -1.2], look: [0, 1.4, -25], fov: 56, set: (o, t) => { setLamps(o, 7 - [.5, 1.0, 1.4, 1.7].filter(x => t >= x).length * 2); const z = t < 1.7 ? null : t < 2.25 ? -18 : t < 2.75 ? -13 : t < 3.15 ? -8.5 : -5;   // не идёт — оказывается ближе после каждой вспышки
    if (z != null) { o.flickOn = false; o.amb.intensity = .44; const ts = [1.7, 2.25, 2.75, 3.15].filter(x => t >= x).pop(), fl = t - ts < .18 || (t - ts > .3 && t - ts < .36);   // вспышка лампы над ним — и снова темно
      const li = clamp(Math.round((-z - 1.8) / 3.6), 0, 6); if (fl) lampSet(o, i => i === li); else { const l = o.lamps[li]; l.li.intensity = .7; l.tube.material.color.setHex(0x3a3d38); } }   // между вспышками трубка тлеет — силуэт читается
    return z != null && (t % .5) > .06 ? [['monster', 0, 0, z, 0, 'monster', 'cam']] : []; } },
  corridor_713: { loc: 'corridor', pos: [0, 1.6, -4], look: [0, 1.0, -26], fov: 52, set: (o) => { setLamps(o, 1); o.amb.intensity = .62; return []; } },
  corridor_dark: { loc: 'corridor', pos: [0, 1.6, -2], look: [0, 1.0, -26], fov: 56, fog: .07, set: (o) => { setLamps(o, 0); o.slitLight.intensity = 0; o.slit.visible = false; o.amb.intensity = .75; return []; } },
  // фонарик: тот же тёмный коридор, в конце стоит Жилец (мини-игра «фонарик», engine.js)
  // фонарик (мини-игра «фонарик», engine.js): тот же коридор, свет только от фонаря; во втором — в конце стоит Жилец
  torch_corr: { loc: 'corridor', pos: [-.2, 1.6, -11], look: [0, 1.2, -26], fov: 56, fog: .05, set: (o) => torchSet(o, false) },
  torch_corr_mon: { loc: 'corridor', pos: [-.2, 1.6, -11], look: [0, 1.2, -26], fov: 56, fog: .05, set: (o) => torchSet(o, true) },
  torch_corr_near: { loc: 'corridor', pos: [-.2, 1.6, -11], look: [0, 1.2, -26], fov: 56, fog: .05, set: (o, t, sh) => { torchSet(o, false); return lunge(o, t, sh, { at: .45, far: 2.4, near: .55, glow: .7, show: .06 }); } },
  door713: { loc: 'corridor', pos: [.15, 1.55, -23.7], look: [0, 1.0, -26], fov: 52, set: (o) => { setLamps(o, 1); return []; } },
  corridor_katya: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 40, set: (o, t) => { setLamps(o, 7); return [['katya', 0, 0, -19, lerp(PI * .98, PI * .02, clamp((t - 1) / 2, 0, 1)) + PI, 'stand', null]]; } },
  corridor_face: { loc: 'corridor', pos: [.05, 1.52, -18.75], look: [0, 1.5, -20], fov: 34, set: (o) => { setLamps(o, 7); return [['katyaThing', 0, 0, -19.4, 0, 'stand', 'cam']]; } },
  cam13_monster: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 66, set: (o) => { o.salt.visible = false; return [['monster', .1, 0, -.5, -.6, 'monster', 'cam']]; } },
  monster_close: { loc: 'server', pos: [-.05, 2.2, .95], look: [-.13, 2.16, 0], fov: 40, set: (o, t) => { A.monster.faceSet = 'grin'; o.amb.intensity = .08; o.cold.intensity = .15; o.cold2.intensity = 0; o.rim.intensity = 1.8; o.red.intensity = .5; o.candle.intensity = 2.2 * (Math.sin(t * 23) > -.6 ? 1 : .3); return [['monster', 0, 0, 0, 0, 'monster', 'cam']]; } },   // свет снизу от свечи и контур — лицо выходит из темноты
  server_mih: { loc: 'server', pos: [.7, 1.45, 2.7], look: [0, .65, 0], fov: 52, face: { mihalych: 'worry' }, set: () => [['mihalych', 0, 0, 0, 0, 'sitfloor', 'cam']] },
  server_mih_close: { loc: 'server', pos: [.25, .95, 1.15], look: [0, .9, 0], fov: 44, face: { mihalych: 'worry' }, set: () => [['mihalych', 0, 0, 0, 0, 'sitfloor', 'cam']] },
  server_mih_stand: { loc: 'server', pos: [.7, 1.6, 2.7], look: [0, 1.4, 0], fov: 50, face: { mihalych: 'worry' }, set: () => [['mihalych', 0, 0, 0, 0, 'stand', 'cam']] },
  server_door: { loc: 'server', pos: [2.4, 1.6, 2.6], look: [-1, 1.2, -1], fov: 55, face: { mihalych: 'fear' }, set: () => [['mihalych', 0, 0, 0, .3, 'sitfloor', [2.4, 1.6, 2.6]]] },
  // Михалыч на полу в круге мотает головой: «не отвечай», «не входи»
  server_door_no: { loc: 'server', pos: [2.4, 1.6, 2.6], look: [-1, 1.2, -1], fov: 55, face: { mihalych: 'fear' }, set: () => { A.mihalych.no = 1; return [['mihalych', 0, 0, 0, .3, 'sitfloor', [2.4, 1.6, 2.6]]]; } },
  // выходит из круга: ноги не держат, оседает, шатается к камере
  server_mih_weak: { loc: 'server', pos: [.9, 1.55, 2.9], look: [0, 1.1, .2], fov: 52, face: { mihalych: 'worry' }, set: (o, t) => { const k = clamp(t / 4.5, 0, 1);
    return [['mihalych', lerp(0, .35, k), 0, lerp(.1, 1.25, k), .25 + Math.sin(t * 2.2) * .12, k < 1 ? 'weak' : 'stand', 'cam']]; } },
  cam13_mih: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.0, -.4], fov: 60, face: { mihalych: 'worry' }, set: (o) => [['mihalych', 0, 0, 0, -.6, 'sitpaper', 'cam']] },
  cam13_mih_monster: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.3, -.6], fov: 60, face: { mihalych: 'worry' }, set: (o, t) => [['mihalych', 0, 0, 0, -.6, 'sitpaper', 'cam']].concat(t > 3.35 ? [['monster', .25, 0, -1.25, -.6, 'monster', 'cam']] : []) },
  // «почти выбежал»: быстрым шагом по дуге за колонной (не сквозь неё) к дверям и толкает створку
  smenshik_leave: { loc: 'lobby', pos: [6.3, 1.72, -.75], look: [1.5, 1.15, 5.4], fov: 52, set: (o, t) => { const w = walkPath('leave', [[4.0, 2.0], [3.7, 3.5], [2.3, 4.9], [.45, 5.4]], t, 1.75);
    return [['smenshik', w.x, 0, w.z, w.done ? PI * .02 : w.ry, w.done ? 'push' : 'walk', null]]; } },
  entrance_empty: { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [.5, .8, 6.2], fov: 64, set: (o) => { o.rain.material.opacity = .18; return []; } },
  // один раз за ночь: пока она улыбается за стеклом, в отражении за спиной Тёмыча стоит Жилец — полсекунды, без звука
  door_smile: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 6.5], fov: 50, set: (o, t) => { o.reflBoost = 1.5; const m = t > 3.1 && t < 3.65; if (m) { o.faceL.position.set(-.2, 2.3, 2.6); o.faceL.distance = 2.6; o.faceL.intensity = 1.6; }
    return atGlass('katyaSmile', 'press', 'cam').concat(m ? [['monster', -.35, 0, 1.55, .05, 'monster', [2.2, 1.6, 9.2], 'refl']] : []); } },
  corridor_monster: { loc: 'corridor', pos: [0, 2.0, -19.1], look: [0, 1.9, -21.5], fov: 48, set: (o) => { lampSet(o, i => i === 5); o.flickOn = false; return [['monster', 0, 0, -21.5, 0, 'reach', 'cam']]; } },
  monster_booth: { loc: 'lobby', pos: [5.3, 1.15, -.6], look: [5.1, 1.85, .8], fov: 52, set: (o) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .3; return [['monster', 5.1, 0, .95, PI, 'reach', 'cam']]; } },
  hall_cam_katya: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['katyaThing', -.6, 0, -1.8, .7, 'stand', 'cam']] },
  elevator_13: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 2.35, -6], fov: 46, set: (o, t) => { setLift(o, o.lifts[0], 1, '13'); o.cabLight.intensity = (Math.sin(t * 19) > -.7) ? 2.4 : .5; return []; } },
  cam13_turn: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 66, set: (o, t) => { o.salt.visible = false; const k = clamp((t - 2.6) / 1.4, 0, 1), e = k * k * (3 - 2 * k); return [['monster', .1, 0, -.5, 3.0, 'monster', lin([.5, 1.9, -3.5], [3.6, 2.7, 3.1], e)]]; } },
  guard_dawn_fear: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: (o, t) => { setDawn(o, 1); A.smenshik.faceSet = 'fear'; return [['smenshik', 5.12, 0, .85 + Math.max(0, t - .8) * .22, PI, t > .8 ? 'back' : 'stand', 'cam']]; } },
  // «Я глянул в стекло будки. Я улыбался»: смотрим в стекло перегородки — в нём отражение Тёмыча (грязь, блик, рама)
  booth_reflect: { loc: 'lobby', pos: [5.3, 1.6, -.55], look: [5.12, 1.6, .9], fov: 38, fog: .16, set: (o) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false; o.amb.intensity = .8; o.boothPane.visible = true; A.temych.faceSet = 'smile'; return [['temych', 5.12, 0, .9, PI, 'stand', 'cam']]; } },
  exterior_alone: { loc: 'lobby', pos: [3.2, 1.4, 17.8], look: [2.0, 2.2, 13], fov: 54, set: () => [['temych', 2.3, 0, 15.6, .25, 'stand', 'cam']] },
  basement_door: { loc: 'corridor', pos: [.15, 1.55, -23.5], look: [0, 1.05, -26], fov: 54, set: (o, t) => { setLamps(o, (Math.sin(t * 11) > .55 && Math.sin(t * 2.3) > -.2) ? 1 : 0); o.srv.visible = true; o.slitLight.color.setHex(0xffb060); o.slitLight.intensity = 1.6 + Math.sin(t * 7) * .3; o.amb.intensity = .35; return []; } },
  // тело там, куда его утащили: голова на пороге 707, ноги в коридоре; Жилец сидит над грудью с дальней стороны — лицом к камере
  cam7_eat: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.35, .6, -11.8], fov: 30, cctv: 1, set: (o, t) => { eatLight(o, t); eatGore(o, t); return [['victim', -.4, 0, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 4 ? [-.75, .2, -11.75] : [1.0, 2.75, -.5]]]; } },
  eat_close: { loc: 'corridor', pos: [.62, 1.12, -9.4], look: [-.62, .62, -12.1], fov: 54, set: (o, t) => { eatLight(o, t); eatGore(o, t); return [['victim', -.4, 0, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 2.2 ? [-.75, .2, -11.75] : [.62, 1.12, -9.4]]]; } },
  // уборщик (03:00, седьмой этаж): сначала обычная работа — потом его забирают в темноту за приоткрытой 707
  cleaner_cam: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { setLamps(o, 7);
    o.cleanSet(CLEAN); return [['cleaner', -.1, 0, -9.45, .2, 'mop', null]]; } },
  cleaner_close: { loc: 'corridor', pos: [.82, 1.45, -6.75], look: [-.5, 1.05, -10.9], fov: 50, face: { cleaner: 'calm' }, set: (o, t) => { setLamps(o, 7);
    o.cleanSet(CLEAN); return [['cleaner', -.1, 0, -9.45, .33, 'mop', [.8, 1.2, -6.8]]]; } },
  // остановился, швабру прислонил к ведру; лампы дальше по коридору захлёбываются и гаснут; из темноты 707 показываются пальцы
  cleaner_stop: { loc: 'corridor', pos: [1.15, 1.5, -6.1], look: [-.25, 1.42, -10.9], fov: 44, face: { cleaner: 'alert' }, set: (o, t) => {
    stopLamps(o, t); o.cleanSet(Object.assign({}, CLEAN, { mop: MOPLEAN, fingers: clamp((t - 1.5) / 1.4, 0, 1) }));
    const turn = clamp((t - .7) / .9, 0, 1), e = turn * turn * (3 - 2 * turn);
    return [['cleaner', .3, 0, -9.25, lerp(.45, 2.7, e), 'stand', t < .6 ? [1.15, 1.5, -6.1] : [0, 1.5, -25]]]; } },
  cleaner_fingers: { loc: 'corridor', pos: [-.3, 1.72, -9.95], look: [-1.3, 1.78, -11.32], fov: 40, set: (o, t) => {
    stopLamps(o, t + 1.2); o.cleanSet(Object.assign({}, CLEAN, { mop: MOPLEAN, fingers: clamp((t - .3) / 1.8, 0, 1) })); return []; } },
  cleaner_grab: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.35, .9, -10.6], fov: 40, cctv: 1, set: (o, t) => {
    o.flickOn = false;
    const fA = t > 1.0 && t < 1.3, fB = t > 1.72 && t < 1.97, back = t > 2.5, dark = t > .7 && !fA && !fB && !back;
    lampSet(o, i => dark ? 0 : fA || fB ? i === 2 : back ? i < 3 && !(i === 2 && Math.sin(t * 9) > .8) : i < 3 || (i === 3 && stut(t, 2)));
    o.amb.intensity = dark ? .16 : .3;
    const got = t > 1.0, gone = t > 4.9;
    o.cleanSet({ bucket: [.6, -10.35, .3, clamp((t - 1.05) / .25, 0, 1)], spill: got ? AFTER.spill : null, sign: CLEAN.sign, wet: CLEAN.wet,
      door7: gone ? .85 : t > .9 ? 1.2 : .72, mop: [MOPLEAN[0], MOPLEAN[1], MOPLEAN[2], clamp((t - 1.08) / .4, 0, 1)], smear: gone ? AFTER.smear : null, fingers: t < .7 ? 1 : 0 });
    if (!got) return [['cleaner', .3, 0, -9.25, 2.75, 'stand', [-1.3, 1.6, -11.4]]];
    // его тащат: за три кадра вспышки он отъезжает к двери, ноги почти не касаются пола
    if (fA) { const k = clamp((t - 1.0) / .3, 0, 1); return [['cleaner', lerp(-.45, -.78, k), .03 * k, lerp(-10.45, -10.88, k), .55, 'reach', [1.0, 2.75, -.5]], ['monster', lerp(-1.0, -1.22, k), 0, lerp(-11.25, -11.55, k), .5, 'reach', [-.45, 1.4, -10.45]]]; }
    // повалил и тащит к порогу за ворот: тело на полу бьётся, мокрые следы рук на линолеуме
    if (t < 1.97) { const k = clamp((t - 1.3) / .67, 0, 1); A.cleaner.struggle = 1;
      return [['cleaner', lerp(.05, -.3, k), 0, lerp(-11.45, -11.72, k), PI * .5 + .15 * (1 - k), 'lay', null], ['monster', lerp(-1.0, -1.25, k), 0, -12.0, PI * .45, 'eat', [-.6, .2, -11.7]]]; }
    // прямо на пороге: валит и рвёт его под мигающей лампой, тянет кишки; тело ещё дёргается; в конце Жилец поднимает голову к камере
    if (t < 4.9) {
      o.cleanSet({ bucket: [.6, -10.35, .3, 1], spill: AFTER.spill, sign: CLEAN.sign, wet: CLEAN.wet, door7: 1.2, mop: [MOPLEAN[0], MOPLEAN[1], MOPLEAN[2], 1], splat: t > 2.1 });
      eatLight(o, t); const g = clamp((t - 1.97) / 2.2, .15, 1); o.blood.scale.set(1.25 * g, 1, g); o.gutsOn = t > 2.45; o.splat.visible = t > 2.1;
      const tw = t < 3.4 ? Math.sin(t * 37) * Math.pow(Math.max(0, Math.sin(t * 5.3)), 3) : 0;   // предсмертные судороги
      // сначала бьётся всем телом, потом слабее — и уже только пальцы; на каждом рывке Жильца — брызги и тряска головы
      A.victim.struggle = clamp(1 - (t - 1.97) / 1.1, 0, 1); A.victim.twitch = t > 2.6 && t < 4.3 ? .8 : 0;
      o.bursts = [2.15, 2.75, 3.3, 3.9]; o.sprayT = t; A.monster.shake = o.bursts.some(b => t > b - .05 && t < b + .3) ? .3 : 0;
      return [['victim', -.4, .01 * Math.abs(tw), -11.78, PI * .5 + tw * .08, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 4.2 ? [-.75, .2, -11.75] : [1.0, 2.75, -.5]]];
    }
    return [];
  } },
  cleaner_gone: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => {
    setLamps(o, 7); o.flickOn = false; const l = o.lamps[3], f = Math.sin(t * 7.3) * Math.sin(t * 2.2) > -.6 ? 1 : 0; l.li.intensity = 2.4 * f; l.tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c);
    o.cleanSet(Object.assign({ wet: [-.1, -9.9] }, AFTER)); return []; } },
  cover_door: { loc: 'lobby', pos: [.95, 1.55, 3.7], look: [.5, 1.6, 6.5], fov: 40, face: { katya: 'fear' }, set: () => [['katya', .62, 0, 6.32, PI + .1, 'stand', 'cam'], ['monster', -.25, 0, 8.4, PI - .15, 'monster', 'cam']] },
  lunge_server: { loc: 'server', pos: [.35, 1.55, 2.7], look: [0, 1.45, -3], fov: 58, set: (o, t, sh) => { o.salt.visible = false; o.amb.intensity = .12; o.cold.intensity = .3; o.cold2.intensity = .1; o.rim.intensity = 0; o.red.intensity = .35; o.candle.intensity = t < 1.4 ? .5 : 0; return lunge(o, t, sh, { at: 1.4, far: 3.6, glow: .35, show: .5 }); } },
  lunge_booth: { loc: 'lobby', pos: [5.3, 1.2, -.6], look: [5.1, 1.45, .9], fov: 56, set: (o, t, sh) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .2; o.l3.intensity = .1; o.amb.intensity = .2; o.boothLight.intensity = .15; return lunge(o, t, sh, { at: 1.3, far: 2.2, glow: .3, show: .45, blink: true }); } },
  lunge_corridor: { loc: 'corridor', pos: [0, 1.6, -3], look: [0, 1.5, -26], fov: 56, set: (o, t, sh) => { setLamps(o, (t % .5) < .1 && t < 1.35 ? 0 : 7); o.amb.intensity = .35; return lunge(o, t, sh, { at: 1.35, far: 15, steps: [[.6, 10], [.85, 6.5], [1.1, 3.5]], glow: .2, show: .35, blink: true }); } },
  lunge_back: { loc: 'corridor', pos: [0, 1.6, -15], look: [0, 1.5, 0], fov: 58, set: (o, t, sh) => { setLamps(o, t < 1.2 ? 2 : 1, true); o.slit.visible = false; o.slitLight.intensity = 0; return lunge(o, t, sh, { at: 1.2, far: 6, steps: [[.7, 3.2]], glow: .25, show: .4, blink: true }); } },
  lunge_lift: { loc: 'lobby', pos: [-2.2, 1.5, -7.5], look: [-2.2, 1.6, -5.8], fov: 66, set: (o, t, sh) => { relabel(o.cabInd, '13'); o.cabLight.intensity = t < 1.4 ? ((t % .7) < .08 && t > .5 ? 1.2 : 0) : 0; return lunge(o, t, sh, { at: 1.4, far: 1.15, glow: .15, show: .55, blink: true, near: .5 }); } },
  cam7_jump: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t, sh) => { setLamps(o, 0); o.lamps[0].li.intensity = t > .1 ? 1.8 : 0; return lunge(o, t, sh, { at: .08, far: 4.5, near: .5, dur: .2, glow: 0 }); } },
  corridor_cam_dark: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t) => { setLamps(o, 0); const f = Math.sin(t * 13) > .2 || Math.sin(t * 3.1) > .6 ? 1 : 0; o.lamps[6].li.intensity = 1.6 * f; o.lamps[6].tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c); o.amb.intensity = .66; return []; } },
  katya_slam: { loc: 'lobby', pos: [.6, 1.48, 4.95], look: [.62, 1.42, 6.5], fov: 46, face: { katya: 'fear' }, set: (o, t) => t < .25 ? [] : atGlass('katya', 'slam', 'cam', 6.24) },
  run_lit: { loc: 'corridor', pos: [0, 1.55, -2], look: [0, 1.4, -26], fov: 62, set: (o, t, sh) => { setLamps(o, 7); sh.pos[2] = -2 - (t * 2.2) % 12; sh.pos[1] = 1.55 + Math.abs(Math.sin(t * 8)) * .05; sh.look[2] = sh.pos[2] - 24; return []; } },
  run_dark: { loc: 'corridor', pos: [0, 1.5, -3], look: [0, 1.2, -26], fov: 62, set: (o, t) => { setLamps(o, 7 - Math.floor(t * 1.5) % 8, true); return [['mihalych', .3, 0, -5.2 - t * 1.2, PI, 'walk', null]]; } },
};
// ---- пролог v3: сменщик ждёт у турникета, Итан входит, ключи из руки в руку, стук в стекло снаружи ----
// путь с разгоном и торможением: где человек через t секунд (s — пройдено, sp — доля скорости 0..1)
function stride(L, v, t, acc = .5, dec = .55) {
  const t1 = 2 * acc / v, t2 = t1 + (L - acc - dec) / v, t3 = t2 + 2 * dec / v;
  if (t <= 0) return { s: 0, sp: 0, done: false };
  if (t < t1) return { s: v * t * t / (2 * t1), sp: t / t1, done: false };
  if (t < t2) return { s: acc + v * (t - t1), sp: 1, done: false };
  if (t < t3) { const u = t - t2; return { s: L - dec + v * u - v * u * u / (2 * (t3 - t2)), sp: 1 - u / (t3 - t2), done: false }; }
  return { s: L, sp: 0, done: true };
}
function polyAt(pts, s) {   // точка ломаной на расстоянии s, курс сглажен у изломов
  let acc = 0;
  for (let i = 1; i < pts.length; i++) { const a = pts[i - 1], b = pts[i], L = Math.hypot(b[0] - a[0], b[1] - a[1]);
    if (s <= acc + L || i === pts.length - 1) { const k = clamp((s - acc) / L, 0, 1); let ry = Math.atan2(b[0] - a[0], b[1] - a[1]);
      if (i < pts.length - 1 && s > acc + L - .4) { const c = pts[i + 1], r2 = Math.atan2(c[0] - b[0], c[1] - b[1]); let d = r2 - ry; d = Math.atan2(Math.sin(d), Math.cos(d)); ry += d * clamp((s - (acc + L - .4)) / .8, 0, .5); }
      if (i > 1 && s < acc + .4) { const z = pts[i - 2], r0 = Math.atan2(a[0] - z[0], a[1] - z[1]); let d = r0 - ry; d = Math.atan2(Math.sin(d), Math.cos(d)); ry += d * clamp(.5 - (s - acc) / .8, 0, .5); }
      return { x: lerp(a[0], b[0], k), z: lerp(a[1], b[1], k), ry }; }
    acc += L; }
}
const polyLen = pts => pts.slice(1).reduce((a, b, i) => a + Math.hypot(b[0] - pts[i][0], b[1] - pts[i][1]), 0);
// идёт по ломаной: фаза шага — от пройденного пути (ноги не скользят), к концу — плавно в стойку
function walker(name, pts, t, v, extra = {}, ryEnd) {
  const C = A[name], L = polyLen(pts), st = stride(L, v, t), p = polyAt(pts, st.s), cyc = 1.3 * C.ss;
  let ry = p.ry; if (ryEnd != null && st.sp < .6) { let d = ryEnd - ry; d = Math.atan2(Math.sin(d), Math.cos(d)); ry += d * (1 - st.sp / .6) * (st.done ? 1 : .7); }
  const w = clamp(st.sp * 1.6, 0, 1);
  return [name, p.x, 0, p.z, ry, Object.assign({ clips: [['Walk_Loop', st.s / cyc * 1.333, w], ['Idle_Loop', t, 1 - w]], legs: w < .1 ? 'feet' : undefined }, extra), null];
}
// короткий переход (полшага–два шага): позиция по ease, шаги — по пройденному пути
function shuffle(name, a, b, t, t0, dur, ry, extra = {}) {
  const C = A[name], u = clamp((t - t0) / dur, 0, 1), k = ease(u), s = k * Math.hypot(b[0] - a[0], b[1] - a[1]), w = Math.sin(PI * u);
  return [name, lerp(a[0], b[0], k), 0, lerp(a[1], b[1], k), ry, Object.assign({ clips: [['Walk_Loop', s / (1.3 * C.ss) * 1.333, w], ['Idle_Loop', t, 1 - w]], legs: w < .1 ? 'feet' : undefined }, extra), null];
}
// ---- двери фасада: створки на петлях (o.leaves), ходит левая — у притвора на ней замок. Снаружи тянут скобу на себя: створка уходит на улицу ----
function leafAng(o, i, a) { const l = o.leaves[i]; l.rotation.y = (i ? 1 : -1) * a; l.updateMatrixWorld(true); }
const leafP = (o, x, y, z) => { const p = o.leaves[0].localToWorld(new T.Vector3(x, y, z)); return [p.x, p.y, p.z]; };
const leafV = (o, x, y, z) => { const v = new T.Vector3(x, y, z).applyQuaternion(o.leaves[0].quaternion); return [v.x, v.y, v.z]; };
// хват левой рукой за наружную скобу левой створки (ладонь вправо, пальцы обхватывают скобу)
const leafGrip = (o, w) => ({ at: leafP(o, .844, 1.12, .11), by: 'palm', fdir: leafV(o, 0, 0, -1), pdir: leafV(o, 1, 0, 0), w, pole: [1, -.6, -.2] });
// угол створки: рывок на себя с u0 (за .75 с до 1.35 рад), доводчик закрывает с u1 (за 3.2 с)
const leafPull = (u, u0, u1 = 99) => 1.35 * ease(clamp((u - u0) / .75, 0, 1)) * (1 - ease(clamp((u - u1) / 3.2, 0, 1)));
// с улицы: дошёл по path до скобы (с правой стороны от неё), рванул створку на себя, шагнул внутрь по inPath
function doorIn(o, name, t, path, v, inPath, v2, extra = {}) {
  const tA = (polyLen(path) + 1.05) / v, u = t - tA, end = path[path.length - 1];
  leafAng(o, 0, leafPull(u, .15, 2.6));
  const g = clamp((u + .4) / .4, 0, 1) * (1 - clamp((u - .55) / .25, 0, 1)), hand = leafGrip(o, g);
  if (u < .75) {
    const W = u < 0 ? walker(name, path, t, v, extra, PI) : [name, end[0], 0, end[1], PI, Object.assign({ clips: [['Idle_Loop', t, 1]], legs: 'feet' }, extra), null];
    W[5] = Object.assign({}, W[5], { arm: { l: hand }, fing: { l: ['grip', g] }, spine: { to: hand.at, k: .1 * g } });
    return W;
  }
  return walker(name, inPath, u - .75, v2, extra);
}
const PG = [.95, 1.55], PE = [.9, 2.42], PH = [.93, 1.13, 1.99];   // где ждёт сменщик, где встаёт Итан, где встречаются руки
const PEYE_E = [PE[0], 1.68, PE[1]], PEYE_G = [PG[0], 1.66, PG[1]];
function gKeys(t, offer, rel) {   // сменщик: связка в опущенной руке → протягивает (offer — с какой секунды) → отпускает (rel)
  const up = offer == null ? 0 : seg(t, offer, offer + .8), dn = rel == null ? 0 : seg(t, rel, rel + .6), w = up * (1 - dn);
  const tr = (Math.sin(t * 41) * .0022 + Math.sin(t * 23.7) * .0016) * up;   // рука дрожит
  const has = rel == null || t < rel;
  return { clips: [['Idle_Loop', t + 3.1, 1]], talk: 1, legs: 'feet', spine: { to: PH, k: .15 * w },
    arm: { r: { at: [PH[0] + tr, PH[1] + .095 + tr, PH[2] - .015], by: 'pinch', fdir: [.2, -.3, 1], pdir: [.55, -1, 0], w } },
    fing: { r: [has ? 'pinch' : 'relax', has ? 1 : 1 - seg(t, rel, rel + .3)] }, hold: has ? { r: 'keys' } : null, look: w > .5 && has ? [PH[0], PH[1] + .05, PH[2]] : PEYE_E };
}
function eKeys(t, t0) {   // Итан берёт связку: ладонь под связку (t0 — начало), пальцы сжимаются, связка у него; рука к груди и вниз
  const r = t0 == null ? 0 : seg(t, t0, t0 + .7), back = t0 == null ? 0 : seg(t, t0 + 1.15, t0 + 1.95), got = t0 != null && t > t0 + .9;
  const at = [lerp(PH[0], PE[0] - .06, back), lerp(PH[1] - .02, 1.02, back), lerp(PH[2] + .015, PE[1] - .28, back)];
  return { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', spine: { to: PH, k: .12 * r * (1 - back) },
    arm: { r: { at, by: 'palm', fdir: [lerp(.1, .5, back), lerp(.12, -.1, back), lerp(-1, -.6, back)], pdir: [0, 1, lerp(0, .3, back)], w: r * (1 - seg(t, t0 + 2.2, t0 + 2.9)) } },
    fing: { r: [got ? 'grip' : 'open', got ? .55 + .45 * seg(t, t0 + .85, t0 + 1.05) : r] }, hold: got ? { r: ['keys', 'keys_palm'] } : null,
    look: got && back > .3 ? null : r > .3 ? [PH[0], PH[1], PH[2]] : PEYE_G };
}
SH.pro_hall = { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t), null]] };
SH.pro_ext = { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o, t) => [doorIn(o, 'temych', t, [[3.6, 14.2], [1.6, 10.5], [.5, 7.4], [.16, 6.62]], 1.05, [[.16, 6.62], [-.22, 6.3], [-.42, 5.85], [-.3, 4.9]], 1.0)] };
SH.pro_enter = { loc: 'lobby', pos: [1.75, 1.6, -.35], look: [.45, 1.35, 5], fov: 50, set: (o, t) => { leafAng(o, 0, .95 * (1 - ease(clamp(t / 2.6, 0, 1))));   // створка за ним дозакрывается
  return [['smenshik', PG[0], 0, PG[1], 0, gKeys(t), PEYE_E], walker('temych', [[-.36, 5.5], [.2, 4.2], [.55, 3.3], [PE[0], PE[1]]], t, 1.05, { look: PEYE_G }, PI)]; } };
SH.pro_two = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.32, 2.0], fov: 44, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t), PEYE_G]] };
SH.pro_offer = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.3, 2.0], fov: 44, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t, 0), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t), PEYE_G]] };
SH.pro_take = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.3, 2.0], fov: 44, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t + 9, 0, 9 + 1.05), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t, .1), PEYE_G]] };
SH.pro_keys = { loc: 'lobby', pos: [1.62, 1.36, 1.86], look: [.92, 1.13, 2.0], fov: 38, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t + 9, 0, 9 + 1.05), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t, .1), PEYE_G]] };
SH.pro_palm = { loc: 'lobby', pos: [1.25, 1.42, 1.72], look: [.86, 1.05, 2.15], fov: 36, set: (o, t) => [['temych', PE[0], 0, PE[1], PI, eKeys(t + 9, 0), null]] };
// p13: связка в ладони перед грудью, Итан смотрит на неё; пальцы чуть сжимают
function eLook(t) { const R = locR(PE[0], PE[1], PI), k = seg(t, 0, .6);
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: R.p(.02, 1.08, .3), by: 'palm', fdir: R.v(.45, .05, 1), pdir: [0, 1, 0], w: 1 } },
    fing: { r: ['grip', .5 + .15 * Math.sin(t * 1.3)] }, hold: { r: ['keys', 'keys_palm'] }, look: R.p(.02, 1.06, .32), spine: { to: R.p(0, .9, .6), k: .12 * k } }; }
SH.pro_palm = { loc: 'lobby', pos: [1.22, 1.5, 1.62], look: [.88, 1.1, 2.12], fov: 40, set: (o, t) => [['temych', PE[0], 0, PE[1], PI, eLook(t), null]] };
// p14: «Журнал на столе» — сменщик кивает на будку левой рукой и снова смотрит на Итана
function gPoint(t) { const R = locR(PG[0], PG[1], 0), up = seg(t, .3, .9) * (1 - seg(t, 2.1, 2.7)), booth = [5.0, 1.2, -.1];
  const sh = R.p(.2, 1.42, 0), d = nrm([booth[0] - sh[0], booth[1] - sh[1], booth[2] - sh[2]]);
  return { clips: [['Idle_Loop', t + 3.1, 1]], talk: 1, legs: 'feet', arm: { l: { at: [sh[0] + d[0] * .5, sh[1] + d[1] * .5 - .05, sh[2] + d[2] * .5], by: 'wrist', fdir: d, pdir: [0, -1, 0], w: up } },
    fing: { l: ['point', up] }, look: t > .5 && t < 1.6 ? booth : PEYE_E }; }
SH.pro_point = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.32, 2.0], fov: 44, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gPoint(t), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eLook(t + 5), PEYE_G]] };
// p15: уже снаружи, под козырьком: три раза костяшками в стекло фасада (z = 6), смотрит на Итана, уходит под дождь
const KN = [1.32, 6.43], KP = [1.27, 1.42, 6.025];   // где стоит, куда приходятся костяшки (наружная сторона стекла)
function gKnock(t) {
  const hits = [.55, .82, 1.09]; let away = 1; for (const h of hits) away = Math.min(away, Math.abs(t - h) / .13);
  const lift = seg(t, .1, .45) * (1 - seg(t, 1.35, 1.8)), z = KP[2] + .085 * Math.min(1, away);
  return { clips: [['Idle_Loop', t + 3.1, 1]], legs: 'feet', arm: { r: { at: [KP[0], KP[1], z], by: 'knuck', fdir: [-.1, .15, -1], pdir: [-.8, -.6, 0], w: lift } },
    fing: { r: ['fist', lift] } };
}
// глазами Итана: он стоит у турникета, сменщик за стеклом стучит, смотрит прямо на него и уходит под дождь
SH.pro_knock = { loc: 'lobby', pos: [.82, 1.66, 4.55], look: [1.28, 1.5, 6.4], fov: 44, set: (o, t) => {
  const out = []; leafAng(o, 0, .55 * (1 - ease(clamp(t / 1.8, 0, 1))));
  if (t < 2.4) out.push(['smenshik', KN[0], 0, KN[1], PI, gKnock(t), 'cam']);
  else out.push(walker('smenshik', [[KN[0], KN[1]], [KN[0] + .6, KN[1] + .5], [KN[0] + 3.5, KN[1] + 1.2]], t - 2.4, 1.15, {}));
  return out; } };
// ---- журнал (02:50): Итан сидит за столом в будке и ведёт пальцем по строке, которую читает ----
const SEAT = [5.2, -.74];
function logPt(px, py, lift = .006) {   // точка страницы журнала (пиксели текстуры 384×256) → мир (журнал лежит на столе, повёрнут на .12)
  const u = (px / 384 - .5) * .42, v = (.5 - py / 256) * .28, c = Math.cos(PI + .12), s = Math.sin(PI + .12);
  return [5.35 + u * c - v * s, .807 + lift, -.27 + (u * s + v * c) * -1];
}
const LOGL = i => 27 + 17 * (i + 1) - 1;   // где под строкой i ведёт палец (чуть ниже базовой линии)
// rows: [[страница 'l'|'r', строка, доля времени], …] — палец идёт по строкам по очереди
function eRead(t, rows, dur) {
  let a = null, k = clamp(t / dur, 0, 1);
  if (rows) { const n = rows.length, i = Math.min(n - 1, Math.floor(k * n)), f = k * n - i, [pg, ln] = rows[i]; const x0 = pg === 'l' ? 14 : 202, x1 = pg === 'l' ? 175 : 352;
    a = logPt(lerp(x0, x1, ease(clamp(f * 1.15, 0, 1))), LOGL(ln)); }
  return { clips: [['Sitting_Idle_Loop', t + 2, 1]], spine: { to: [5.33, .8, -.27], k: .42 },
    arm: Object.assign({ l: { at: [5.62, .812, -.33], by: 'palm', fdir: [-.2, 0, 1], pdir: [0, -1, 0], w: 1 } }, a ? { r: { at: a, by: 'tip', fdir: [.15, -.55, 1], pdir: [0, -1, .3], w: 1 } } : {}),
    fing: { r: [a ? 'point' : 'relax', 1], l: ['flat', .7] }, look: a || [5.33, .81, -.27] };
}
SH.jr_desk = { loc: 'lobby', pos: [6.55, 1.55, -.05], look: [5.25, .95, -.55], fov: 46, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eRead(t, null, 1), null]] };
const jrPage = (rows, dur, look = [5.35, .8, -.17]) => ({ loc: 'lobby', pos: [5.36, 1.16, -.5], look, fov: 40, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eRead(t, rows, dur), null]] });
SH.jr_p12 = jrPage([['l', 4], ['l', 5]], 4.5, [5.42, .8, -.2]);
SH.jr_p15 = jrPage([['r', 0], ['r', 1], ['r', 2]], 6, [5.27, .8, -.13]);
SH.jr_p18 = jrPage([['r', 3], ['r', 4]], 5.5, [5.27, .8, -.17]);
SH.jr_p20 = jrPage([['r', 5], ['r', 6], ['r', 7], ['r', 8]], 9, [5.27, .8, -.2]);
SH.jr_p21 = jrPage([['r', 9], ['r', 10]], 5.5, [5.27, .8, -.26]);
// stub: следующие кадры пролога — ниже
// ---- Гас моет пол: тряпка ходит восьмёркой перед ним, корпус поворачивается за шваброй, руки на черенке (правая сверху), вес с ноги на ногу ----
function locR(x, z, ry) { const c = Math.cos(ry), s = Math.sin(ry);   // оси персонажа (x — влево, z — вперёд) → мир
  return { p: (lx, ly, lz) => [x + lx * c + lz * s, ly, z - lx * s + lz * c], v: (lx, ly, lz) => [lx * c + lz * s, ly, -lx * s + lz * c] }; }
const nrm = v => { const L = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / L, v[1] / L, v[2] / L]; }, crs = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
function mopGus(o, x, z, ry, t, k = 1) {   // k — насколько моет (0 — швабра стоит, руки на черенке)
  const ph = t * 2 * PI / 1.75, sx = Math.sin(ph) * k, cz = Math.cos(2 * ph) * k, R = locR(x, z, ry);
  const head = R.p(sx * .38, 0, .8 + .05 * cz), piv = R.p(sx * .1 - .05, .97, .18);
  const u = nrm([piv[0] - head[0], piv[1] - head[1], piv[2] - head[2]]), at = d => [head[0] + u[0] * d, head[1] + u[1] * d, head[2] + u[2] * d];
  window.__W3chars.propAim('mop', o.S, head, u, ry);
  const side = R.v(1, 0, 0), pr = nrm(crs(u, crs(side, u))), pl = pr.map(v => -v);   // ладонь правой — к черенку слева, левой — справа
  return { clips: [['Idle_Loop', t + 2, 1]], legs: 'plant', pelvis: R.v(sx * .03, -.07, .02), spine: { to: R.p(sx * .25, .1, 1.0), k: .32 },
    arm: { r: { at: at(1.1), by: 'palm', fdir: crs(pr, u), pdir: pr, pole: R.v(-1, -.4, -.4) }, l: { at: at(.7), by: 'palm', fdir: crs(u, pl), pdir: pl, pole: R.v(1, -.8, .1) } },
    fing: { r: ['grip', 1], l: ['grip', 1] }, look: head };
}
// ---- седьмой этаж (02:59–03:00): Гас моет и напевает; в три перестаёт, выпрямляется, поворачивается к закрытой 707 ----
const G7 = [-.15, -10.3], G7RY = .45, G7DOOR = Math.atan2(-1.3 - G7[0], -11.8 - G7[1]);   // где моет; курс к двери 707
const RADIO_G = ['spine_03', [-.075, .06, .16], [0, 0, 0]];   // рация на нагруднике комбинезона
function gusStand(o, t, ry, look, hand) {   // стоит, швабра в правой руке торчком, левая — на рации (если hand)
  const R = locR(G7[0], G7[1], ry), head = R.p(-.3, 0, .22), u = nrm(R.v(.05, 1, -.08));
  window.__W3chars.propAim('mop', o.S, head, u, ry);
  const top = [head[0] + u[0] * 1.02, head[1] + u[1] * 1.02, head[2] + u[2] * 1.02];
  return { clips: [['Idle_Loop', t + 4, 1]], talk: 1, legs: 'feet', arm: Object.assign({ r: { at: top, by: 'palm', fdir: R.v(.9, 0, .3), pdir: R.v(1, 0, -.2), w: 1 } },
    hand ? { l: { at: hand, by: 'palm', fdir: R.v(-.3, .2, 1), pdir: R.v(0, 0, -1), w: 1 } } : {}), fing: { r: ['grip', 1], l: [hand ? 'flat' : 'relax', 1] },
    wear: { radio: RADIO_G }, look };
}
SH.f7_cam = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.25, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  return [['cleaner', G7[0], 0, G7[1], G7RY, Object.assign(mopGus(o, G7[0], G7[1], G7RY, t), { wear: { radio: RADIO_G } }), null]]; } };
SH.f7_close = { loc: 'corridor', pos: [.9, 1.42, -7.6], look: [-.3, 1.0, -10.6], fov: 46, face: { cleaner: 'calm' }, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  const P = mopGus(o, G7[0], G7[1], G7RY, t); P.wear = { radio: RADIO_G }; P.talk = 1; return [['cleaner', G7[0], 0, G7[1], G7RY, P, null]]; } };
// перестал мыть → выпрямился со шваброй → повернулся к 707 (мелкими шагами), левая рука легла на рацию
SH.f7_stop = { loc: 'corridor', pos: [1.0, 1.5, -7.2], look: [-.45, 1.25, -10.9], fov: 46, face: { cleaner: 'alert' }, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  const doorP = [-1.3, 1.55, -11.8];
  if (t < 1.0) { const P = mopGus(o, G7[0], G7[1], G7RY, t + 5, 1 - seg(t, 0, .9)); P.wear = { radio: RADIO_G }; P.look = t > .6 ? doorP : P.look; return [['cleaner', G7[0], 0, G7[1], G7RY, P, null]]; }
  const k = seg(t, 1.3, 2.6), ry = lerp(G7RY, G7DOOR + 2 * PI * (G7DOOR < G7RY - PI ? 1 : 0), k), P = gusStand(o, t, ry, doorP, null);
  if (k > 0 && k < 1) P.clips = [['Idle_Loop', t + 4, .45], ['Walk_Loop', t * .9, .55]];   // переступает на месте, пока поворачивается
  return [['cleaner', G7[0], 0, G7[1], ry, P, null]]; } };
const radioPt = (ry) => { const R = locR(G7[0], G7[1], ry); return R.p(-.08, 1.33, .19); };
SH.f7_radio = { loc: 'corridor', pos: [-.95, 1.4, -10.75], look: [-.12, 1.3, -10.35], fov: 38, face: { cleaner: 'alert' }, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, door7: 0 });
  const ry = G7DOOR, hp = radioPt(ry), k = seg(t, .2, .8); hp[0] += (1 - k) * .05; hp[1] -= (1 - k) * .25;
  return [['cleaner', G7[0], 0, G7[1], ry, gusStand(o, t, ry, [-1.3, 1.55, -11.8], hp), null]]; } };
SH.f7_cam_door = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.35, .9, -10.8], fov: 40, cctv: 1, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, door7: 0 });
  return [['cleaner', G7[0], 0, G7[1], G7DOOR, gusStand(o, t, G7DOOR, [-1.3, 1.55, -11.8], radioPt(G7DOOR)), null]]; } };
SH.f7_cam_after = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.25, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  window.__W3chars.propAim('mop', o.S, [.3, .015, -10.45], [-.93, .025, .36], 1.2); return []; } };   // швабра лежит, Гаса нет
// Итан в будке отвечает по рации: сидит, рация в правой руке у рта
function eRadio(t) { const R = locR(SEAT[0], SEAT[1], 0), up = seg(t, .1, .6);
  return { clips: [['Sitting_Idle_Loop', t + 2, 1]], talk: 1, arm: { r: { at: R.p(-.07, 1.22, .2), by: 'palm', fdir: R.v(.3, 1, .25), pdir: R.v(1, 0, .2), w: up } },
    fing: { r: ['grip', up] }, hold: up > .2 ? { r: 'radio' } : null, look: R.p(-.6, 1.2, 2) }; }
SH.booth_radio = { loc: 'lobby', pos: [6.35, 1.48, .05], look: [5.15, 1.15, -.75], fov: 42, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eRadio(t), null]] };
// ---- стеклянная дверь (03:30): «Лили» снаружи, Итан внутри ----
let fogBase = null;
function fogWrite(o, k) {   // туман на стекле; k — сколько надписи выведено (0..1); возвращает, где сейчас кончик пальца (мир)
  const F = o.fogW, d = F.userData, x = d.x, L = [tr('Как звали'), tr('собаку?')], x0 = 44, ys = [118, 200];
  F.visible = true; x.font = '700 72px "Comic Sans MS", "Segoe Print", Arial';
  if (!fogBase) { fogBase = document.createElement('canvas'); fogBase.width = 512; fogBase.height = 256; const f = fogBase.getContext('2d'); let s = 3; const R = () => ((s = (s * 16807) % 2147483647) / 2147483647);
    f.save(); f.scale(1, .55); const gr = f.createRadialGradient(256, 233, 10, 256, 233, 245); gr.addColorStop(0, 'rgba(235,240,245,.62)'); gr.addColorStop(.6, 'rgba(235,240,245,.4)'); gr.addColorStop(1, 'rgba(235,240,245,0)'); f.fillStyle = gr; f.fillRect(0, 0, 512, 470); f.restore();   // пятно дыхания — овал без краёв
    for (let i = 0; i < 40; i++) { const x = 60 + R() * 392, y = 40 + R() * 176, r = 10 + R() * 40, g2 = f.createRadialGradient(x, y, 0, x, y, r); g2.addColorStop(0, `rgba(240,244,248,${.12 * R()})`); g2.addColorStop(1, 'rgba(240,244,248,0)'); f.fillStyle = g2; f.fillRect(x - r, y - r, 2 * r, 2 * r); }
    for (let i = 0; i < 2500; i++) { const x = R() * 512, y = R() * 256, d = Math.hypot((x - 256) / 256, (y - 128) / 128); if (d < 1) { f.fillStyle = `rgba(255,255,255,${.22 * R() * (1 - d)})`; f.fillRect(x, y, 2, 2); } } }
  if (Math.abs(d.k - k) > .002) { d.k = k; x.clearRect(0, 0, 512, 256); x.drawImage(fogBase, 0, 0);
    x.globalCompositeOperation = 'destination-out'; x.lineWidth = 10; x.lineJoin = 'round'; x.lineCap = 'round'; x.strokeStyle = x.fillStyle = '#000';
    L.forEach((s, i) => { const f = clamp(k * 2 - i, 0, 1); if (f <= 0) return; const w = x.measureText(s).width; x.save(); x.beginPath(); x.rect(x0 - 10, ys[i] - 80, (w + 20) * f, 100); x.clip(); x.strokeText(s, x0, ys[i]); x.fillText(s, x0, ys[i]); x.restore(); });
    x.globalCompositeOperation = 'source-over'; d.tx.needsUpdate = true; }
  const i = Math.min(1, Math.floor(k * 2)), f = clamp(k * 2 - i, 0, 1), w = x.measureText(L[i]).width, px = x0 + w * f, py = ys[i] - 26 + Math.sin(f * 46) * 16;
  return [.62 - (px / 512 - .5) * .8, 1.42 + (.5 - py / 256) * .4, 5.986];
}
const LZ = 6.3, LX = .62, EYE_IN = [.78, 1.66, 5.52];   // где стоит «Лили» снаружи; где глаза Итана у двери
// поза у стекла: mode 'press' — ладони на стекле, всхлипывает; 'look' — оглянулась назад; 'slam' — бьёт ладонями; 'scratch' — ногти по стеклу сверху вниз; 'stand' — руки опущены
function lilyGlass(t, mode, o2 = {}) {
  const R = locR(LX, LZ, PI), gz = LZ - 6.014, sob = mode === 'press' || mode === 'slam' ? Math.pow(Math.max(0, Math.sin(t * 3.3)), 6) : 0;
  let hit = 0; if (mode === 'slam') for (const h of [.3, .62, 1.6, 1.9]) hit = Math.max(hit, Math.exp(-Math.pow((t - h) / .07, 2)));
  const away = mode === 'slam' ? .06 * (1 - hit) * seg(t, 0, .25) : 0, k = mode === 'stand' || mode === 'look' ? 0 : 1;
  const ly = mode === 'scratch' ? lerp(1.62, 1.08, seg(t, .3, 3.6)) : 1.38;
  const hand = (lx, dy) => ({ at: R.p(lx, ly + dy, gz - away), by: mode === 'scratch' ? 'tip' : 'palm', fdir: [0, 1, -.15], pdir: [0, 0, -1], w: k });
  return { clips: [['Idle_Loop', t + 1.7, 1]], talk: 1, legs: 'feet', spine: { to: R.p(0, 1.3, 1.2), k: .22 * k + .05 },
    arm: { l: hand(.21, -.02), r: hand(-.2, .01) }, fing: { l: [mode === 'scratch' ? 'claw' : 'flat', k], r: [mode === 'scratch' ? 'claw' : 'flat', k] },
    wig: [['spine_03', [1, 0, 0], sob * .05], ['neck_01', [1, 0, 0], sob * .04]], twist: mode === 'look' ? -.85 * seg(t, .1, .7) : 0,
    look: mode === 'look' && t > .15 ? [LX - 2.5, 1.5, LZ + 4] : o2.look === 'cam' ? undefined : o2.look || EYE_IN };
}
// Итан у двери внутри: смотрит на неё; пишет пальцем по туману (k), поворачивает ключ (key)
function ethanDoor(o, t, mode, k) {
  const R = locR(.78, 5.55, 0), base = { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', look: [LX, 1.52, LZ] };
  if (mode === 'write') { const p = fogWrite(o, k); return Object.assign(base, { arm: { r: { at: p, by: 'tip', fdir: [.05, .35, 1], pdir: [.35, -.5, .4], w: seg(t, 0, .5) } }, fing: { r: ['point', 1] }, spine: { to: p, k: .12 } }); }
  return base;
}
SH.gl_wide = { loc: 'lobby', pos: [1.95, 1.62, 3.95], look: [.62, 1.42, 6.3], fov: 46, set: (o, t) => [['temych', .78, 0, 5.55, 0, ethanDoor(o, t), null], ['katya', LX, 0, LZ, PI, lilyGlass(t, 'press'), null]] };
SH.gl_face = { loc: 'lobby', pos: [.66, 1.56, 5.45], look: [.62, 1.5, 6.5], fov: 34, face: { katya: 'plead' }, set: (o, t) => { breath(o, t); return [['katya', LX, 0, LZ, PI, lilyGlass(t, 'press', { look: 'cam' }), 'cam']]; } };
SH.gl_look = { loc: 'lobby', pos: [1.5, 1.55, 4.7], look: [.62, 1.45, 6.4], fov: 40, face: { katya: 'fear' }, set: (o, t) => [['katya', LX, 0, LZ, PI, t < 1.5 ? lilyGlass(t, 'look') : lilyGlass(t - 1.5, 'slam'), null]] };
SH.gl_write = { loc: 'lobby', pos: [.4, 1.74, 5.08], look: [.64, 1.4, 6.05], fov: 46, set: (o, t) => [['temych', .78, 0, 5.55, 0, ethanDoor(o, t, 'write', clamp((t - .6) / 4.2, 0, 1)), null], ['katya', LX, 0, LZ, PI, lilyGlass(t, 'press'), null]] };
SH.gl_scratch = { loc: 'lobby', pos: [.66, 1.52, 5.3], look: [.62, 1.38, 6.5], fov: 40, set: (o, t) => [['katyaSmile', LX, 0, LZ, PI, lilyGlass(t, 'scratch', { look: 'cam' }), 'cam']] };
// ---- ветка А (открыл): ключ в замке, кинулась на шею, пустой холл и мокрые следы к лифту ----
function ethanKey(o, t, lock, rot) {   // ключ в замке: рука держит связку за ключ, поворачивает (rot — когда поворот)
  const k = seg(t, 0, .6), turn = seg(t, rot, rot + .35);
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', spine: { to: lock, k: .15 * k },
    arm: { r: { at: lock, by: 'pinch', fdir: [0, -.2, 1], pdir: [lerp(-1, -.2, turn), lerp(-.3, -1, turn), 0], w: k } }, fing: { r: ['pinch', k] }, hold: { r: 'keys' }, look: lock };
}
SH.a_key = { loc: 'lobby', pos: [-.62, 1.55, 5.0], look: [-.05, 1.1, 5.95], fov: 38, set: (o, t) => [['temych', .1, 0, 5.4, 0, ethanKey(o, t, o.doorLock, 1.0), null], ['katya', LX, 0, LZ, PI, lilyGlass(t, 'press'), null]] };
// a02: ключ повернулся — она шагнула к скобе, рванула створку на себя и вошла; бросилась на шею. Итан отступил на шаг
const HUG_E = [-.26, 5.02], HUG_L = [-.13, 5.25];   // она чуть левее его (по его левую руку): головы расходятся, щека к плечу
function hugArms(t, k, ek) {   // руки в объятии: её — на его плечах, кисти сцеплены за шеей (локти вниз), его — у неё на спине
  const E = A.temych, Lc = A.katya, nk = E.bone.neck_01.getWorldPosition(new T.Vector3()), sp = Lc.bone.spine_03.getWorldPosition(new T.Vector3());
  const lily = { arm: { l: { at: [nk.x - .045, nk.y - .05, nk.z - .07], by: 'palm', fdir: [1, -.3, 0], pdir: [0, .2, 1], w: k, pole: [1, -.8, .35] },
                        r: { at: [nk.x + .05, nk.y - .06, nk.z - .065], by: 'palm', fdir: [-1, -.3, 0], pdir: [0, .2, 1], w: k, pole: [-1, -.8, .35] } },
    fing: { l: ['relax', 1 - k * .6], r: ['relax', 1 - k * .6] }, spine: { to: [nk.x, nk.y - .1, nk.z], k: .2 * k }, look: k > .5 ? [nk.x + .55, nk.y - .12, nk.z - .55] : [nk.x, nk.y + .12, nk.z] };
  const eth = { arm: { r: { at: [sp.x - .03, sp.y - .03, sp.z + .1], by: 'palm', fdir: [1, -.1, 0], pdir: [0, 0, -1], w: ek, pole: [-1, -.45, .3] },
                       l: { at: [sp.x + .06, sp.y - .17, sp.z + .095], by: 'palm', fdir: [-1, -.1, 0], pdir: [0, 0, -1], w: ek, pole: [1, -.45, .3] } },
    fing: { l: ['flat', ek * .7], r: ['pinch', 1] }, hold: { r: 'keys' }, spine: { to: [sp.x, sp.y + .1, sp.z], k: .1 * ek }, look: ek > .3 ? [sp.x - .55, 1.58, sp.z + .7] : [sp.x, 1.55, sp.z] };
  return [lily, eth];
}
function hugPair(o, t) {
  leafAng(o, 0, leafPull(t, .55));
  // она: к скобе (0–.5), рывок (.55–1.3), внутрь (с 1.1), руки на шею (к 2.5)
  let L; const g = seg(t, .25, .5) * (1 - seg(t, .95, 1.15)), gr = leafGrip(o, g);
  if (t < 1.1) { L = shuffle('katya', [LX, LZ], [.16, 6.6], t, 0, .5, PI); L[5].arm = { l: gr }; L[5].fing = { l: ['grip', g] }; L[5].spine = { to: gr.at, k: .1 * g }; L[5].look = gr.at; }
  else L = walker('katya', [[.16, 6.6], [-.22, 6.28], [-.36, 5.9], HUG_L], t - 1.1, 1.8, {}, PI);
  // он: держит ключ (связка в пальцах), отступает от двери
  const E = shuffle('temych', [.1, 5.4], HUG_E, t, .7, .6, 0, { talk: 1, look: t < 1.1 ? [.16, 1.5, 6.6] : [-.3, 1.5, 5.6] });
  E[5].fing = { r: ['pinch', 1] }; E[5].hold = { r: 'keys' };
  const k = seg(t, 2.05, 2.6), ek = seg(t, 2.45, 3.0);
  if (k > 0) { const [lp, ep] = hugArms(t, k, ek); Object.assign(L[5], lp); if (ek > 0) Object.assign(E[5], ep); L[5].legs = 'feet'; }
  return [E, L];
}
SH.a_hug = { loc: 'lobby', pos: [1.55, 1.5, 4.55], look: [-.15, 1.3, 5.75], fov: 48, face: { katya: 'plead' }, set: (o, t) => hugPair(o, t) };
SH.a_hug_side = { loc: 'lobby', pos: [1.25, 1.52, 5.12], look: [-.2, 1.38, 5.14], fov: 40, face: { katya: 'plead' }, set: (o, t) => hugPair(o, t + 4) };
SH.a_hug_face = { loc: 'lobby', pos: [.2, 1.63, 4.55], look: [-.1, 1.5, 5.24], fov: 34, set: (o, t) => { A.katya.noTilt = true; return hugPair(o, t + 4); } };
// a07: она стоит рядом, руки пустые; у него в руке звонит телефон
SH.a_beside = { loc: 'lobby', pos: [1.6, 1.42, 4.0], look: [.6, 1.1, 5.0], fov: 46, set: (o, t) => {
  const R = locR(.55, 4.95, 0), ph = { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: R.p(-.12, 1.12, .32), by: 'palm', fdir: R.v(.3, .3, 1), pdir: [0, 1, .3], w: 1 } }, fing: { r: ['phone', 1] }, hold: { r: 'phone' }, look: R.p(-.12, 1.1, .35) };
  return [['temych', .55, 0, 4.95, 0, ph, null], ['katya', .95, 0, 5.15, PI + .9, { clips: [['Idle_Loop', 2, 1]], legs: 'feet', look: [.55, 1.66, 4.95] }, null]]; } };
SH.a_steps = { loc: 'lobby', pos: [.55, 1.66, 5.2], look: [-1.4, .4, -3], fov: 58, set: (o, t) => { o.wetSteps.visible = true; return []; } };
SH.a_floors = { loc: 'lobby', pos: [-1.6, 2.1, -2.9], look: [-2.2, 2.62, -5.94], fov: 26, set: (o, t) => { const f = ['5', '9', '12', '13'][Math.min(3, Math.floor(t / .9))]; setLift(o, o.lifts[0], 0, f, true); return []; } };
SH.a_floors_down = { loc: 'lobby', pos: [-1.6, 2.1, -2.9], look: [-2.2, 2.62, -5.94], fov: 26, set: (o, t) => { const f = ['13', '12', '11', '10', '9'][Math.min(4, Math.floor(t / .7))]; setLift(o, o.lifts[0], 0, f, false); return []; } };
SH.e_face = { loc: 'lobby', pos: [.6, 1.64, 5.55], look: [.55, 1.62, 4.95], fov: 34, face: { temych: 'fear' }, set: (o, t) => [['temych', .55, 0, 4.95, 0, { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet' }, 'cam']] };
// ---- А1: будка. Запер дверь будки, выключил лампу, сполз под стойку; утром сменщик пятится; отражение в стекле ----
SH.h_lock = { loc: 'lobby', pos: [6.2, 1.6, -.75], look: [7.35, 1.05, .4], fov: 40, set: (o, t) => [['temych', 7.12, 0, -.05, .35, ethanKey(o, t, o.boothLock, .9), null]] };
SH.h_lamp = { loc: 'lobby', pos: [6.75, 1.25, -.85], look: [6.12, .85, -.12], fov: 40, set: (o, t) => { const off = t > .75; o.lamp.intensity = off ? 0 : 1.6; o.lampBulb.visible = !off;
  const R = locR(6.35, -.75, -.6), k = seg(t, 0, .55) * (1 - seg(t, 1.0, 1.5));
  return [['temych', 6.35, 0, -.75, -.6, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', spine: { to: [6.15, .85, -.1], k: .25 * k }, arm: { r: { at: [6.16, .835, -.07], by: 'tip', fdir: [0, -1, .3], w: k } }, fing: { r: ['point', k] }, look: [6.1, .85, -.1] }, null]]; } };
// сполз под стойку: сидит на корточках, спиной к передней стойке
const CROUCH = [5.75, .05];
function eCrouch(t, cover) { const R = locR(CROUCH[0], CROUCH[1], PI);
  return { clips: [['Crouch_Idle_Loop', t + .7, 1]], arm: cover ? { r: { at: R.p(-.13, .5, .2), by: 'palm', fdir: R.v(.2, -.3, 1), pdir: R.v(0, -1, 0), w: seg(t, 0, .4) } } : null, fing: { r: ['flat', 1] } }; }
SH.h_crouch = { loc: 'lobby', pos: [6.35, .9, -.9], look: [5.75, .6, .05], fov: 50, set: (o, t) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .25; o.amb.intensity = .22; return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), null]]; } };
SH.h_pocket = { loc: 'lobby', pos: [5.35, .75, -.55], look: [5.75, .5, .05], fov: 44, set: (o, t) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .25; o.amb.intensity = .22; return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t, true), null]]; } };
SH.h_face = { loc: 'lobby', pos: [5.72, .98, -.42], look: [5.75, .98, .05], fov: 34, face: { temych: 'fear' }, set: (o, t) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .25; o.amb.intensity = .22; return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), 'cam']]; } };
// утро: сменщик входит, подходит к будке, смотрит вниз на Итана — и пятится, не поворачиваясь спиной
SH.h_guard = { loc: 'lobby', pos: [5.6, 1.0, -.95], look: [6.1, 1.5, .9], fov: 50, set: (o, t) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false;
  return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), null], ['smenshik', 6.15, 0, .95, PI, { clips: [['Idle_Loop', t + 3.1, 1]], talk: 1, legs: 'feet', look: [5.75, .75, .05] }, null]]; } };
function backAway(name, from, to, t, v, look) {   // пятится: идёт спиной вперёд, лицом к look
  const L = Math.hypot(to[0] - from[0], to[1] - from[1]), st = stride(L, v, t, .3, .4), k = st.s / L, C = A[name], cyc = 1.3 * C.ss, w = clamp(st.sp * 1.6, 0, 1);
  const x = lerp(from[0], to[0], k), z = lerp(from[1], to[1], k), ry = Math.atan2(from[0] - to[0], from[1] - to[1]);
  return [name, x, 0, z, ry, { clips: [['Walk_Loop', -st.s / cyc * 1.333, w], ['Idle_Loop', t, 1 - w]], legs: w < .1 ? 'feet' : undefined, look }, null];
}
SH.h_guard_back = { loc: 'lobby', pos: [5.6, 1.0, -.95], look: [5.6, 1.4, 2.5], fov: 54, face: { smenshik: 'fear' }, set: (o, t) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false;
  return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), null], backAway('smenshik', [6.15, .95], [5.4, 3.6], t, .55, [5.75, .9, .05])]; } };
// отражение: Итан встал и смотрит в стекло будки — кадр снят через стекло и отзеркален, поверх грязь стекла
SH.h_reflect = { loc: 'lobby', pos: [5.12, 1.62, 1.02], look: [5.12, 1.6, -.2], fov: 30, mirror: true, set: (o, t) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false; A.temych.faceSet = 'smile';
  return [['temych', 5.12, 0, .05, 0, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet' }, 'cam']]; } };
// ---- А2: бег по служебному коридору — лампы гаснут у него за спиной; под дождём; звонок ----
SH.x_run = { loc: 'corridor', pos: [0, 1.5, -2.5], look: [0, 1.35, -14], fov: 56, set: (o, t, sh) => { const z = -14 + t * 3.2; sh.pos[2] = z + 4.2 + Math.sin(t * 9) * .02; sh.look[2] = z;
  lampSet(o, i => -1.8 - i * 3.6 > z - 3.0 - t * .2); o.amb.intensity = .3;   // гаснут те, что уже позади него
  return [['temych', Math.sin(t * 1.3) * .1, 0, z, 0, { clips: [['Sprint_Loop', t * 1.25, 1]], look: [0, 1.5, z + 6] }, null]]; } };
// ---- серверная (Б): круг соли у самого порога, свеча; Фрэнк сидит в круге, нога в картоне ----
const SRV_C = [1.75, 2.75], FRANK = [1.85, 2.9];   // центр круга, где сидит Фрэнк (спиной к двери)
function srvSet(o) { o.salt.position.set(SRV_C[0], .012, SRV_C[1]); o.candle.position.set(1.35, .3, 2.55); }
const SPLINT = ['calf_l', [0, .2, 0], [0, 0, 0]];
// сидит на полу (кадр из клипа «встаёт с пола»: опирается на руку, ноги вытянуты); sheet — держит лист к камере; reach — тянется на голос
function frankSit(t, o2 = {}) {
  const P = { clips: [['LayToIdle', o2.sheet ? .62 : o2.u ?? .45, 1]], wear: { splint: SPLINT }, talk: 1, look: o2.look };
  if (o2.sheet) { P.fing = { l: ['pinch', 1], r: ['pinch', 1] }; }
  if (o2.no) P.wig = [['neck_01', [0, 1, 0], Math.sin(t * 7) * .35 * o2.no]];
  return P;
}
SH.srv_cam = { loc: 'server', pos: [-3.3, 2.65, -2.6], look: [1.6, .7, 2.6], fov: 50, cctv: 1, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankSit(t, { sheet: true }), [-3.3, 2.65, -2.6]]]; } };
SH.srv_look = { loc: 'server', pos: [-3.3, 2.65, -2.6], look: [1.6, .7, 2.6], fov: 50, cctv: 1, face: { mihalych: 'fear' }, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankSit(t), t > .4 ? [-2.5, 1.0, -1.5] : [-3.3, 2.65, -2.6]]]; } };
SH.srv_leg = { loc: 'server', pos: [1.3, .65, 2.05], look: [1.6, .2, 2.55], fov: 42, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankSit(t), [.2, 1.25, 1.2]]]; } };
// ---- CAM 13: коридор, которого нет (тот же 7-й, тусклый и длинный); оно в дальнем конце ----
function c13Set(o, n = 4) { setLamps(o, n); o.S.fog.density = .045; o.amb.intensity = .3; }
function faceLit(o, p, k = 1.6, c = 0xc8d4e8) { o.faceL.position.set(...p); o.faceL.intensity = k; o.faceL.distance = 3.5; o.faceL.color.setHex(c); }
const C13P = [1.0, 2.75, -.5];
// поворот на месте: сначала голова через плечо, потом корпус — мелкими шажками (не скольжением)
function turnRound(name, x, z, t, t0, dur, clip = 'Idle_Loop', extra = {}) {
  const k = clamp((t - t0) / dur, 0, 1), hk = ease(clamp(k * 2.4, 0, 1)), bk = ease(clamp((k - .3) / .7, 0, 1)), st = Math.sin(PI * clamp((k - .3) / .7, 0, 1));
  const ry = PI * (1 - bk);
  return [name, x, 0, z, ry, Object.assign({ clips: [[clip, t + 2, 1 - st * .45], ['Walk_Loop', k * 1.6, st * .45]], twist: -.9 * hk * (1 - bk), legs: st < .08 ? 'feet' : undefined }, extra), hk > .4 ? 'cam' : null];
}
SH.c13_back = { loc: 'corridor', pos: C13P, look: [-.1, 1.2, -20], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 3); return [['monster', 0, 0, -19.5, PI, { clips: [['Zombie_Idle_Loop', t, 1]], legs: 'feet' }, null]]; } };
SH.c13_turn = { loc: 'corridor', pos: C13P, look: [-.1, 1.4, -20], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 3); faceLit(o, [.3, 2.2, -18.2], 1.2); return [turnRound('monster', 0, -19.5, t, .2, 2.8, 'Zombie_Idle_Loop')]; } };
SH.c13_face = { loc: 'corridor', pos: C13P, look: [.4, 2.0, -3.3], fov: 44, cctv: 1, face: { monster: 'grin' }, set: (o, t) => { c13Set(o, 4); faceLit(o, [.8, 2.5, -2.0], 2.2);
  return [['monster', .45, 0, -3.3, Math.atan2(C13P[0] - .45, C13P[2] + 3.3), { clips: [['Zombie_Idle_Loop', t, 1]], legs: 'feet' }, 'cam']]; } };
SH.c13_lily = { loc: 'corridor', pos: C13P, look: [-.1, 1.2, -18], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 3); faceLit(o, [.3, 1.9, -16.8], 1.4, 0xe8dcc8); return [turnRound('katyaThing', 0, -18, t, 1.6, 3.4)]; } };
SH.c13_lily_face = { loc: 'corridor', pos: C13P, look: [.2, 1.5, -8.5], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 4); faceLit(o, [.5, 1.8, -7.3], 1.8, 0xe8dcc8);
  return [['katyaThing', .2, 0, -8.5, Math.atan2(C13P[0] - .2, C13P[2] + 8.5), { clips: [['Idle_Loop', t + 1, 1]], legs: 'feet' }, 'cam']]; } };
// s02–s04: CAM 13 показывает седьмой: Гас лежит в дверях 707, над грудью у него сидит оно; потом поднимает голову к объективу
function gusEat(o, t, up) {
  eatLight(o, t); o.cleanSet(AFTER); if (!up) eatGore(o, t); else { o.bursts = null; A.victim.twitch = 0; }
  const k = up ? ease(clamp((t - .4) / 2.2, 0, 1)) : 0, chest = [-.72, .3, -11.78];
  const M = { clips: [['Crouch_Idle_Loop', t + 1, 1]], spine: { to: chest, k: .55 * (1 - k * .7) }, legs: 'feet',
    arm: { l: { at: [-.6, .26, -11.66], by: 'palm', fdir: [-1, 0, .2], pdir: [0, -1, 0], w: 1 - k * .5 }, r: { at: [-.88, .24, -11.92], by: 'palm', fdir: [-1, 0, -.2], pdir: [0, -1, 0], w: 1 - k * .5 } },
    fing: { l: ['claw', 1], r: ['claw', 1] }, look: k > .05 ? lin(chest, C13P, k) : chest };
  return [['victim', -.4, 0, -11.78, PI * .5, 'lay', null], ['monster', -.62, 0, -12.45, .06, M, null]];
}
SH.c13_gus = { loc: 'corridor', pos: C13P, look: [-.45, .6, -11.8], fov: 30, cctv: 1, set: (o, t) => gusEat(o, t, false) };
SH.c13_gus_up = { loc: 'corridor', pos: C13P, look: [-.5, .9, -11.9], fov: 30, cctv: 1, face: { monster: 'grin' }, set: (o, t) => gusEat(o, t, true) };
// ---- улица ----
// x03: выбежал через аварийный выход — бежит под дождём вдоль фасада
SH.x_out = { loc: 'lobby', pos: [5.2, 1.25, 12.8], look: [-2.2, 1.15, 9.3], fov: 50, set: (o, t) => { const x = -8 + t * 3.1;
  return [['temych', x, 0, 9.4 + Math.sin(t * 1.1) * .1, PI / 2, { clips: [['Sprint_Loop', t * 1.25, 1]], look: [x + 6, 1.5, 9.6] }, null]]; } };
// x04: «Горизонт» тёмный; над крышей, где этажей уже нет, светится окно
function lobbyOff(o) { o.l1.intensity = .15; o.l3.intensity = .1; o.l4.intensity = .1; o.lamp.intensity = 0; o.lampBulb.visible = false; o.boothLight.intensity = .05; o.amb.intensity = .12; o.canopy.intensity = .35; }
SH.x_building = { loc: 'lobby', pos: [2.6, 1.5, 21.5], look: [-1.0, 22.5, 6], fov: 66, set: (o, t) => { o.fac.material = o.facDark; o.ghostWin.visible = true; o.ghostWin.scale.setScalar(1.5); lobbyOff(o);
  o.S.fog.density = .012; o.hemi.intensity = .55; o.sky.material.map = TX.skyCity;
  return [['temych', 1.6, 0, 23.2, PI - .08, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', look: [-3.1, 41.6, 6] }, null]]; } };
// x05–x07: стоит под дождём, телефон у уха; за спиной — тёмное здание
SH.x_rain = { loc: 'lobby', pos: [2.35, 1.6, 17.6], look: [.9, 1.6, 15.0], fov: 44, set: (o, t) => { o.fac.material = o.facDark; o.ghostWin.visible = true; lobbyOff(o); o.sky.material.map = TX.skyCity;
  const ry = 1.0, R = locR(1.15, 15.6, ry);   // лицом почти к камере, телефон у правого уха: ладонь к щеке, пальцы вперёд, локоть вниз (клип клал руку поперёк лица)
  return [['temych', 1.15, 0, 15.6, ry, { clips: [['Idle_TalkingPhone_Loop', t + 1, 1]], legs: 'feet', look: R.p(-.3, 1.62, 3),
    arm: { r: { at: R.p(-.105, 1.53, -.06), by: 'palm', fdir: R.v(0, .25, 1), pdir: R.v(1, 0, 0), w: 1, pole: R.p(-.35, 1.0, .05) } }, fing: { r: ['phone', 1] }, hold: { r: 'phone' } }, null]]; } };
// Б1, рассвет: вдвоём под козырьком; Фрэнк держится за плечо Итана, нога в картоне
const XE = [-.1, 7.45], XF = [-.62, 7.55];
function frankOut(t, extra = {}) {
  const E = A.temych, sh = E.bone.upperarm_r.getWorldPosition(new T.Vector3());
  return Object.assign({ clips: [['Idle_Loop', t + 4, 1]], legs: 'plant', wear: { splint: SPLINT }, talk: 1,
    arm: { r: { at: [sh.x + .02, sh.y + .06, sh.z], by: 'palm', fdir: [1, 0, .2], pdir: [0, -1, 0], w: 1 } }, fing: { r: ['grip', .6] }, spine: { to: [sh.x, 1.2, sh.z], k: .08 } }, extra);
}
SH.x_two = { loc: 'lobby', pos: [1.75, 1.5, 10.3], look: [-.4, 1.35, 7.5], fov: 44, set: (o, t) => [['temych', XE[0], 0, XE[1], -.15, { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', look: [1.5, 1.6, 12] }, null],
  ['mihalych', XF[0], 0, XF[1], .2, frankOut(t, { look: [XE[0], 1.62, XE[1]] }), null]] };
SH.x_no = { loc: 'lobby', pos: [.75, 1.62, 8.9], look: [-.2, 1.58, 7.45], fov: 38, set: (o, t) => [['temych', XE[0], 0, XE[1], -.75, { clips: [['Idle_No_Loop', t + .2, 1]], legs: 'feet', look: [XF[0], 1.6, XF[1]] }, null],
  ['mihalych', XF[0], 0, XF[1], .2, frankOut(t, { look: [XE[0], 1.62, XE[1]] }), null]] };
// d27: закурил — зажигалка у сигареты, огонёк, затяжка; сигарета остаётся в пальцах
function mouthOf(C) { const h = C.bone.Head.getWorldPosition(new T.Vector3()), m = C.mouthL ? C.bone.Head.localToWorld(C.mouthL.clone()) : h.clone().add(new T.Vector3(0, -.08, .1));
  const f = m.clone().sub(h); f.y *= .25; return [m, f.normalize()]; }
SH.x_smoke = { loc: 'lobby', pos: [.55, 1.62, 9.0], look: [-.55, 1.55, 7.6], fov: 36, set: (o, t) => {
  const up = seg(t, 0, .5) * (1 - seg(t, 1.6, 2.1)), flame = t > .45 && t < 1.6, take = seg(t, 2.4, 2.9), puff = Math.max(0, Math.sin((t - 6) * .8));
  const F = frankOut(t, { look: [.55, 1.62, 9.0] }); delete F.spine;
  const C = A.mihalych, [m, f] = mouthOf(C), tip = [m.x + f.x * .085, m.y + f.y * .085 - .01, m.z + f.z * .085];
  F.arm.l = { at: [tip[0], tip[1] - .03, tip[2]], by: 'tip', fdir: [f.x, .9, f.z], pdir: [-f.z, 0, f.x], w: up };
  F.fing.l = ['grip', up]; F.hold = { l: up > .3 ? 'lighter' : null };
  if (take > 0) { const R = locR(XF[0], XF[1], .2), lo = R.p(-.14, 1.15, .28), at = lin(lo, tip, puff * .9 + (1 - take)); F.arm.r = { at, by: 'pinch', fdir: [f.x, 0, f.z], pdir: [0, -1, 0], w: 1 }; F.fing.r = ['pinch', 1]; F.hold = Object.assign(F.hold || {}, { r: 'cig' }); }
  F.after = () => { const lt = window.__W3chars.prop3('lighter').obj.userData.flame; if (lt) lt.visible = flame;
    if (take <= 0) { const [m2, f2] = mouthOf(C); propX('cig', o.S, [m2.x + f2.x * .005, m2.y - .006, m2.z + f2.z * .005], [f2.x, -.12, f2.z]); } };
  return [['mihalych', XF[0], 0, XF[1], .35, F, null], ['temych', XE[0], 0, XE[1], -.5, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', look: [XF[0], 1.6, XF[1]] }, null]]; } };
function propX(name, scene, pos, dir) { const pr = window.__W3chars.prop3(name); if (pr.obj.parent !== scene) scene.add(pr.obj); pr.obj.position.set(...pos);
  pr.obj.quaternion.setFromUnitVectors(new T.Vector3(1, 0, 0), new T.Vector3(...dir).normalize()); pr.obj.visible = true; pr.C = null; }
// ---- ветка Б у стекла ----
SH.b_smile = { loc: 'lobby', pos: [.66, 1.56, 5.45], look: [.62, 1.52, 6.5], fov: 32, set: (o, t) => { breath(o, t); return [['katyaSmile', LX, 0, LZ, PI, lilyGlass(t, 'stand', { look: 'cam' }), 'cam']]; } };
SH.b_back = { loc: 'lobby', pos: [.72, 1.62, 5.35], look: [.75, 1.35, 8.6], fov: 46, set: (o, t) => [backAway('katyaSmile', [LX, LZ], [LX + .4, LZ + 4.6], t, .42, EYE_IN)] };
SH.b_print = { loc: 'lobby', pos: [.6, 1.5, 4.95], look: [.41, 1.33, 6.05], fov: 42, set: (o, t) => { o.glassU.uPrint.value = 1; return []; } };
// ---- серверная: лист к камере, темнота за кругом, тянется на голос, держим, выполз ----
const FR = () => locR(FRANK[0], FRANK[1], PI + .55);
SH.srv_sheet = { loc: 'server', pos: [-3.3, 2.65, -2.6], look: [1.6, .8, 2.6], fov: 50, cctv: 1, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o); const R = FR();
  const P = frankSit(t, { sheet: true, look: [-3.3, 2.65, -2.6] }); P.sheet = [-3.3, 2.65, -2.6];
  P.arm = { l: { at: R.p(.1, .98, .36), by: 'pinch', fdir: R.v(-.2, .3, 1), pdir: R.v(-1, 0, 0), w: 1 }, r: { at: R.p(-.1, .98, .36), by: 'pinch', fdir: R.v(.2, .3, 1), pdir: R.v(1, 0, 0), w: 1 } };
  return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, P, [-3.3, 2.65, -2.6]]]; } };
const EIN = [2.28, 2.42];   // где Итан сидит на корточках в круге рядом с Фрэнком (справа — камеры его не загораживают)
SH.srv_in = { loc: 'server', pos: [.25, 1.2, 1.3], look: [1.7, .6, 2.75], fov: 48, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o);
  return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankSit(t, { look: [EIN[0], 1.0, EIN[1]] }), null],
    ['temych', EIN[0], 0, EIN[1], -.75, { clips: [['Crouch_Idle_Loop', t + .5, 1]], look: [FRANK[0], .95, FRANK[1]] }, null]]; } };
function srvDim(o) { srvSet(o); o.cold.intensity = .12; o.cold2.intensity = .08; o.red.intensity = .25; o.rim.intensity = .6; o.flickOn = false; }
SH.srv_dark = { loc: 'server', pos: [2.45, 1.05, 3.3], look: [-1.8, .9, -2.3], fov: 50, set: (o, t) => { srvDim(o);
  return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankSit(t, { look: [-1.6, 1.1, -2] }), null], ['temych', EIN[0], 0, EIN[1], PI + .3, { clips: [['Crouch_Idle_Loop', t + .5, 1]], look: [-1.6, 1.1, -2] }, null]]; } };
function frankReach(t, k, strain = 0) { const R = FR(), P = frankSit(t, { look: [-1.4, 1.0, -2.2], u: lerp(.45, .86, k) });
  P.spine = { to: R.p(0, .9, 1.3), k: .16 * k }; P.arm = { r: { at: R.p(-.14, 1.0 + .05 * Math.sin(t * 3), .62), by: 'palm', fdir: R.v(0, .35, 1), pdir: R.v(0, -.5, .8), w: k } }; P.fing = { r: ['open', k] };
  if (strain) P.wig = [['spine_02', [1, 0, 0], Math.sin(t * 9) * .05 * strain], ['neck_01', [0, 1, 0], Math.sin(t * 5.3) * .1 * strain]];
  return P; }
SH.srv_reach = { loc: 'server', pos: [.6, 1.15, 3.1], look: [1.35, .7, 2.2], fov: 46, face: { mihalych: 'plead' }, set: (o, t) => { srvDim(o);
  return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankReach(t, seg(t, .5, 2.2)), null], ['temych', EIN[0], 0, EIN[1], -.75, { clips: [['Crouch_Idle_Loop', t + .5, 1]], look: [FRANK[0], .95, FRANK[1]] }, null]]; } };
// держит Фрэнка за плечи сзади (мини-игра): тот рвётся к голосу
SH.srv_hold = { loc: 'server', pos: [.35, 1.25, 2.3], look: [1.65, .75, 2.75], fov: 48, face: { mihalych: 'plead', temych: 'fear' }, set: (o, t) => { srvDim(o);
  const R = FR(), F = A.mihalych, sl = F.bone.upperarm_l.getWorldPosition(new T.Vector3()), sr = F.bone.upperarm_r.getWorldPosition(new T.Vector3()), eb = R.p(0, 0, -.42);
  return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, frankReach(t, 1, 1), null],
    ['temych', eb[0], 0, eb[2], PI + .55, { clips: [['Crouch_Idle_Loop', t + .5, 1]], spine: { to: [FRANK[0], .8, FRANK[1]], k: .2 }, legs: 'feet',
      arm: { l: { at: [sl.x, sl.y + .05, sl.z], by: 'palm', fdir: R.v(0, -.2, 1), pdir: [0, -1, 0], w: 1 }, r: { at: [sr.x, sr.y + .05, sr.z], by: 'palm', fdir: R.v(0, -.2, 1), pdir: [0, -1, 0], w: 1 } },
      fing: { l: ['grip', .8], r: ['grip', .8] }, look: [FRANK[0], 1.0, FRANK[1]] }, null]]; } };
// k8a: выполз из круга — на четвереньках, руки по очереди вперёд; темнота смыкается
function crawlP(t, R) { const a = Math.sin(t * 2 * PI / 1.5);
  return { clips: [['Crouch_Idle_Loop', t, 1]], spine: { to: R.p(0, .2, 1.4), k: .8 }, pelvis: R.v(0, -.12, 0), legs: 'plant',
    arm: { l: { at: R.p(.2, .03, .62 + .1 * a), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: 1 }, r: { at: R.p(-.2, .03, .62 - .1 * a), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: 1 } },
    fing: { l: ['flat', 1], r: ['flat', 1] }, wear: { splint: SPLINT } }; }
SH.srv_crawl = { loc: 'server', pos: [2.4, 1.3, 3.25], look: [.2, .5, .6], fov: 50, set: (o, t) => { srvDim(o); o.candle.intensity = Math.max(.15, 1.1 - t * .25);
  const k = clamp(t / 5, 0, 1), x = lerp(1.6, .1, k), z = lerp(2.5, .5, k), ry = Math.atan2(.1 - 1.6, .5 - 2.5);
  return [['mihalych', x, 0, z, ry, crawlP(t, locR(x, z, ry)), null]]; } };
// ---- лестница и подвал ----
function stDim(o, k = .15) { o.bulbs.forEach(b => { b.li.intensity = b.base * k; b.sh.material.color.setHex(k > .3 ? 0xfff2d0 : 0x3a3428); }); }
SH.st_down = { loc: 'stairs', pos: [-.7, 6.1, -4.9], look: [-.7, 4.0, -1.2], fov: 60, torch: { x: .5, y: .62 }, set: (o, t, sh) => { stDim(o, .12);
  const k = clamp(t / 5.5, 0, 1), y = lerp(4.5, 3.0, k) + 1.6 + Math.abs(Math.sin(t * 4.2)) * .035, z = lerp(-4.7, -1.5, k);
  sh.pos = [-.7 + Math.sin(t * 2.1) * .03, y, z]; sh.look = [-.68, y - 1.6, z + 3.2]; return []; } };
// d22 (CAM 04): Итан ведёт Фрэнка по средней площадке, рука Фрэнка у него на плече
SH.st_up = { loc: 'stairs', pos: [1.25, 7.3, -.3], look: [-.1, 4.5, -5.0], fov: 58, cctv: 1, set: (o, t) => { const k = clamp(t / 7, 0, 1), x = lerp(-.75, .7, k), y = 4.5;
  const E = A.temych, sh = E.bone.upperarm_l.getWorldPosition(new T.Vector3()), cyc = (x + .75) / (1.3 * E.ss) * 1.333;   // шаг — по пройденному
  return [['temych', x + .22, y, -5.05, PI / 2, { clips: [['Walk_Loop', cyc, 1]] }, null],
    ['mihalych', x - .2, y, -5.15, PI / 2, { clips: [['Walk_Loop', cyc + .3, 1]], wear: { splint: SPLINT }, arm: { l: { at: [sh.x, sh.y + .06, sh.z], by: 'palm', fdir: [1, 0, 0], pdir: [0, -1, 0], w: 1 } }, fing: { l: ['grip', .6] } }, null]]; } };
function bsDark(o, k = 0) { o.flickOn = false; o.bulbs.forEach(b => { b.li.intensity = b.base * k; b.bulb.material.color.setHex(k ? 0xffe8b8 : 0x1c1a14); }); }
SH.bs_dark = { loc: 'basement', pos: [0, 1.6, -.6], look: [0, 1.0, -14], fov: 56, set: (o, t) => { bsDark(o); return []; } };
SH.bs_walk = { loc: 'basement', pos: [0, 1.6, -3], look: [0, 1.1, -14], fov: 56, torch: { x: .5, y: .66 }, set: (o, t, sh) => { bsDark(o);
  const z = -3 - t * .85; sh.pos = [Math.sin(t * 2.1) * .03, 1.6 + Math.abs(Math.sin(t * 4.2)) * .03, z]; sh.look = [0, 1.0, z - 6]; return []; } };
SH.bs_door = { loc: 'basement', pos: [.22, 1.55, -11.9], look: [0, .95, -14], fov: 48, torch: { x: .52, y: .55 }, set: (o, t) => { bsDark(o); return []; } };
// у двери: тусклая ближняя лампа, фонарь в левой руке Итана светит на дверь; когда дверь открыта — свет свечи из серверной
function bsLight(o, open, E) { bsDark(o); const b = o.bulbs[2]; b.li.intensity = b.base * .35; b.bulb.material.color.setHex(0x8a7a58); o.amb.intensity = .3;
  if (open) { o.candle.intensity = 1.6; o.candle.distance = 5; o.roomL.intensity = 2.2; }
  if (E) { o.etorch.intensity = 7; o.etorch.position.set(E[0] + .12, 1.12, E[1] - .2); o.etorch.target.position.set(0, .9, -14.6); o.etorch.target.updateMatrixWorld(); } }
const ETORCH = { l: 'torch' }, ETF = { l: ['grip', 1] };
// d07: три раза костяшками в железную дверь
function eKnockDoor(t) { const hits = [.55, .82, 1.09]; let away = 1; for (const h of hits) away = Math.min(away, Math.abs(t - h) / .13);
  const lift = seg(t, .1, .45) * (1 - seg(t, 1.35, 1.8)), z = -13.952 + .085 * Math.min(1, away);
  return { clips: [['Idle_Torch_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: [-.08, 1.32, z], by: 'knuck', fdir: [-.1, .15, -1], pdir: [-.8, -.6, 0], w: lift } }, fing: { r: ['fist', lift], l: ['grip', 1] }, hold: ETORCH, look: [-.1, 1.3, -14] }; }
SH.bs_knock = { loc: 'basement', pos: [-.85, 1.55, -12.55], look: [.05, 1.2, -13.95], fov: 46, set: (o, t) => { bsLight(o, false, [.14, -13.5]); return [['temych', .14, 0, -13.5, PI, eKnockDoor(t), null]]; } };
// d08–d09: засов изнутри, дверь открывается в коридор; Фрэнк на колене в круге у порога, рукой толкает дверь
const BF = [0, -14.85];
SH.bs_open = { loc: 'basement', pos: [-.9, 1.5, -12.35], look: [.15, .95, -14.6], fov: 52, face: { mihalych: 'worry' }, set: (o, t) => {
  const k = ease(clamp((t - .25) / .9, 0, 1)); o.sdoor.rotation.y = 1.35 * k; o.sdoor.updateMatrixWorld(true); bsLight(o, k > .1, [.3, -13.2]);
  const dp = o.sdoor.localToWorld(new T.Vector3(-.8, 1.0, -.03)), reach = 1 - seg(t, .7, 1.0);
  return [['mihalych', BF[0], 0, BF[1], 0, { clips: [['Fixing_Kneeling', .8, 1]], wear: { splint: SPLINT }, talk: 1, arm: { r: { at: [dp.x, dp.y, dp.z], by: 'palm', fdir: [0, 1, 0], pdir: [0, 0, 1], w: reach } }, fing: { r: ['flat', reach] }, look: [.3, 1.6, -13.2] }, null],
    shuffle('temych', [.14, -13.5], [.3, -13.15], t, .3, .5, PI, { look: [0, .9, -14.8], hold: ETORCH, fing: ETF })]; } };
SH.bs_frank = { loc: 'basement', pos: [-.24, 1.0, -13.45], look: [.06, .5, -14.9], fov: 48, face: { mihalych: 'worry' }, set: (o, t) => { o.sdoor.rotation.y = 1.35; bsLight(o, true, [.3, -13.2]); o.etorch.intensity = CINE ? 3 : 5; o.faceL.position.set(.3, 1.0, -14.1); o.faceL.intensity = CINE ? .45 : .8; o.faceL.color.setHex(0xc9cfd8);   // лицо Фрэнка не пересвечено (в «Кино» было рыжим)
  return [['mihalych', BF[0], 0, BF[1], 0, frankSit(t, { look: [-.24, 1.0, -13.45] }), null]]; } };
SH.bs_no = { loc: 'basement', pos: [-.22, 1.58, -13.1], look: [.05, .72, -14.9], fov: 46, face: { mihalych: 'fear' }, set: (o, t) => { o.sdoor.rotation.y = 1.35; bsLight(o, true, [.3, -13.2]); o.etorch.intensity = CINE ? 3 : 5; o.faceL.position.set(.3, 1.1, -14.1); o.faceL.intensity = CINE ? .45 : .8; o.faceL.color.setHex(0xc9cfd8);   // лицо Фрэнка не пересвечено (в «Кино» было рыжим)
  return [['mihalych', BF[0], 0, BF[1], 0, frankSit(t, { no: 1, look: [-.22, 1.58, -13.1] }), null]]; } };
// w09: утро — дверь распахнута, круг пуст, из него в коридор тянутся следы ладоней
SH.bs_after = { loc: 'basement', pos: [.45, 1.6, -9.6], look: [0, .2, -13.8], fov: 50, set: (o, t) => { o.sdoor.rotation.y = 1.4; o.prints.visible = true; o.candle.intensity = 0; o.slit.visible = o.glow.visible = false; return []; } };
// w10: Итан пишет в журнале — ручка в пальцах, строка появляется под ручкой (маска из чистой страницы уходит вправо)
function noteMask(o) {
  if (o.nmask) return o.nmask;
  const mk = []; for (let i = 0; i < 2; i++) { const g = new T.PlaneGeometry(1, 1), m = new T.Mesh(g, new T.MeshBasicMaterial({ map: TX.note, polygonOffset: true, polygonOffsetFactor: -3, polygonOffsetUnits: -6 })); ps1(m.material); o.note.add(m); mk.push(m); }
  return (o.nmask = mk);
}
function maskTo(m, x0, x1, y0, y1) { m.visible = x1 > x0 + .5;   // прямоугольник страницы в пикселях текстуры 384×256 → кусок той же текстуры поверх
  const u0 = x0 / 384, u1 = x1 / 384, v0 = 1 - y1 / 256, v1 = 1 - y0 / 256, g = m.geometry, p = g.attributes.position, uv = g.attributes.uv;
  const X = u => (u - .5) * .42, Y = v => (v - .5) * .28;
  [[u0, v1], [u1, v1], [u0, v0], [u1, v0]].forEach(([u, v], i) => { p.setXYZ(i, X(u), Y(v), .0008); uv.setXY(i, u, v); }); p.needsUpdate = uv.needsUpdate = true; g.computeBoundingSphere(); }
SH.jr_write = { loc: 'lobby', pos: [5.62, 1.22, -.45], look: [5.25, .8, -.34], fov: 38, set: (o, t) => {
  o.note.material = mat(0xffffff, TX.note2, { decal: 1 }); const mk = noteMask(o), lines = [[.5, 2.6, 228], [2.9, 5.0, 245]]; let tipPx = null;
  lines.forEach(([a, b, bl], i) => { const k = clamp((t - a) / (b - a), 0, 1), x = 200 + 172 * k; maskTo(mk[i], x, 374, bl - 13, bl + 5); if (t >= a - .3 && t <= b + .2) tipPx = [x + Math.sin(t * 27) * 1.5, bl - 4 + Math.sin(t * 33) * 3]; });
  const tip = tipPx ? logPt(tipPx[0], tipPx[1], .001) : logPt(374, 250, .02), dir = nrm([-.32, .88, -.36]), pin = [tip[0] + dir[0] * .085, tip[1] + dir[1] * .085, tip[2] + dir[2] * .085];
  const P = eRead(t, null, 1); P.arm.r = { at: pin, by: 'pinch', fdir: [.1, -.6, 1], pdir: [-.5, -.5, .2], w: 1 }; P.fing.r = ['pinch', 1]; P.look = tip;
  P.after = () => window.__W3chars.propAim('pen', o.S, tip, dir);
  return [['temych', SEAT[0], 0, SEAT[1], 0, P, null]]; } };
SH.dbg_frank = { loc: 'server', pos: [.2, 1.1, 1.0], look: [1.8, .55, 2.9], fov: 50, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, { clips: [['LayToIdle', t, 1]], wear: { splint: SPLINT } }, null]]; } };
SH.dbg_frank2 = { loc: 'server', pos: [.2, 1.1, 1.0], look: [1.8, .55, 2.9], fov: 50, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], 0, FRANK[1], PI + .55, { clips: [['Sitting_Idle_Loop', 1, 1]], pelvis: [0, -t * .1, 0], legs: 'feet', wear: { splint: SPLINT } }, null]]; } };
SH.dbg_bf = { loc: 'basement', pos: [-.35, 2.1, -12.6], look: [0, .3, -15.2], fov: 60, set: (o, t) => { o.sdoor.rotation.y = 1.35; bsLight(o, true, [.3, -13.2]); o.amb.intensity = 1.2;
  return [['mihalych', BF[0], 0, BF[1], 0, frankSit(t, {}), null]]; } };
SH.dbg_gus = { loc: 'corridor', pos: [.9, 1.3, -9.6], look: [-.6, .5, -12.1], fov: 50, set: (o, t) => gusEat(o, t, false) };
// ---- 01:00, второй обход: Итан поднимается на седьмой; Гас даёт ему яблоко (из руки в руку, по-настоящему) ----
const R2G = [-.15, -9.62], R2E = [-.12, -8.84], R2H = [-.12, 1.07, -9.23], R2C = () => locR(R2E[0], R2E[1], PI).p(-.15, 1.12, .17);   // R2C — яблоко у груди Итана
function gApple(t, offer, rel) {   // Гас: яблоко на ладони → протягивает → отдаёт, рука вниз
  const up = seg(t, offer, offer + .8), dn = rel == null ? 0 : seg(t, rel, rel + .6), w = up * (1 - dn), has = rel == null || t < rel;
  const R = locR(R2G[0], R2G[1], 0), lo = R.p(-.2, .95, .12);
  return { clips: [['Idle_Loop', t + 2, 1]], talk: 1, legs: 'feet', spine: { to: R2H, k: .12 * w },
    arm: { r: { at: lin(lo, [R2H[0], R2H[1] - .035, R2H[2]], up), by: 'palm', fdir: [0, .1, 1], pdir: [0, 1, 0], w: has ? 1 : w } },
    fing: { r: [has ? 'cup' : 'relax', 1] }, hold: has ? { r: 'apple' } : null, look: w > .4 && has ? R2H : [R2E[0], 1.62, R2E[1]] };
}
function eApple(t, t0) {   // Итан: ладонью сверху на яблоко, пальцы обхватывают; к груди
  const r = seg(t, t0, t0 + .6), got = t > t0 + .6, back = seg(t, t0 + 1.0, t0 + 1.7), R = locR(R2E[0], R2E[1], PI), chest = R2C();   // к груди — в локоть, а не на вытянутой руке
  const at = lin([R2H[0], R2H[1] + .036, R2H[2]], chest, back);
  return { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', spine: { to: R2H, k: .1 * r * (1 - back) },
    arm: { r: { at: r < 1 && !got ? lin(R.p(-.2, .95, .1), [R2H[0], R2H[1] + .036, R2H[2]], r) : at, by: 'palm', fdir: [0, -.2, -1], pdir: [0, -1, 0], w: 1 } },
    fing: { r: [got ? 'cup' : 'open', 1] }, hold: got ? { r: 'apple' } : null, look: back < .5 ? R2H : [R2G[0], 1.6, R2G[1]] };
}
SH.r2_meet = { loc: 'corridor', pos: [.85, 1.6, -5.3], look: [-.12, 1.25, -9.5], fov: 48, face: { cleaner: 'smile' }, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN);
  const k = 1 - seg(t, .6, 1.8), E = walker('temych', [[.35, -6.0], [.1, -7.6], R2E], t, .95, { look: [R2G[0], 1.6, R2G[1]] }, PI);
  return [E, ['cleaner', R2G[0], 0, R2G[1], lerp(.2, 0, 1 - k), Object.assign(mopGus(o, R2G[0], R2G[1], lerp(.2, 0, 1 - k), t, k), { look: [E[1], 1.62, E[3]] }), null]]; } };
SH.r2_apple = { loc: 'corridor', pos: [1.12, 1.42, -9.05], look: [-.15, 1.15, -9.15], fov: 44, face: { cleaner: 'smile' }, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN);
  window.__W3chars.propAim('mop', o.S, [-1.05, 0, -10.05], nrm([-.25, 1, .05]));   // швабра прислонена к стене
  return [['cleaner', R2G[0], 0, R2G[1], 0, gApple(t, .1, 1.45), null], ['temych', R2E[0], 0, R2E[1], PI, eApple(t, .85), null]]; } };
SH.r2_talk = { loc: 'corridor', pos: [.42, 1.66, -7.85], look: [-.15, 1.5, -9.62], fov: 40, face: { cleaner: 'smile' }, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN);
  window.__W3chars.propAim('mop', o.S, [-1.05, 0, -10.05], nrm([-.25, 1, .05]));
  const R = locR(R2E[0], R2E[1], PI);
  return [['cleaner', R2G[0], 0, R2G[1], 0, { clips: [['Idle_Talking_Loop', t + 2, 1]], talk: 1, legs: 'feet', look: [R2E[0], 1.62, R2E[1]] }, null],
    ['temych', R2E[0], 0, R2E[1], PI, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: R2C(), by: 'palm', fdir: [0, -.2, -1], pdir: [0, -1, 0], w: 1 } }, fing: { r: ['cup', 1] }, hold: { r: 'apple' }, look: [R2G[0], 1.6, R2G[1]] }, null]]; } };
// ---- камеры смены (пульт в будке). Что на них сейчас — curProps.sh (движок: пульт, кто где, странности) ----
// странности (s.*): cat, door01, streetOff, lily01 · wet, frank02, hallDark, palm · lift/liftOpen, lift13, clockSpin, palmMove ·
// stDark, guard04 · gus, l7, door7, bucket7 · l10, door10, chair10 · mOff, sdoor. Помехи («он») рисует движок поверх кадра.
const SHS = () => (curProps && curProps.sh) || {};
function irOn(o, p, k = 1.5) { o.ir = 1; o.irL.position.set(p[0], p[1], p[2]); o.irL.intensity = k; }   // темно — камера переходит в ИК: подсветка у объектива, ч/б
function shClock(o, s, t) { if (s.clk == null) return; const v = s.clockSpin ? s.clk - t * 40 : s.clk; o.setClock(Math.floor(v / 60), ((v % 60) + 60) % 60); }   // часы холла идут по времени смены (или крутятся назад)
const STILL = (clip = 'Idle_Loop', at = 1.2) => ({ clips: [[clip, at, 1]], legs: 'feet' });   // стоит и не шевелится
SH.cam01 = { loc: 'lobby', pos: [-1.2, 3.3, 8.3], look: [2.6, .6, 8.4], fov: 74, cctv: 1, set: (o, t) => { const s = SHS(); o.rain.material.opacity = .18;
  o.cat.visible = !!s.cat; if (s.cat) { o.catHead.rotation.y = Math.sin(t * .4) * .5; o.catTail.rotation.z = Math.sin(t * 1.7) * .3; }
  if (s.door01) { leafAng(o, 0, 1.15); leafAng(o, 1, .35); }   // вход распахнут в дождь
  if (s.streetOff) { o.street.intensity = 0; o.streetBulb.visible = false; o.canopy.intensity = 0; o.canopyLamp.visible = false; }   // погас свет над входом
  if (s.lilyDoor) return [['katya', .35, 0, 6.45, PI + .25, STILL('Idle_Loop', 1.6), [-1.2, 3.3, 8.3]]];   // у самой двери, смотрит в камеру
  return s.lily01 ? [['katya', 8.6, 0, 11.2, -1.9, STILL(), [-1.2, 3.3, 8.3]]] : []; } };
SH.cam02 = { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, cctv: 1, set: (o, t) => { const s = SHS();
  if (s.palm) { o.palmShadow.position.x = -3.95 + .9 * s.palm; o.palmShadow.scale.setScalar(1 + .15 * s.palm); }
  o.wetSteps.visible = !!s.wet; if (s.wet) { o.wetM.opacity = .6; o.wetM.color.setHex(0xa8b8c4); }   // на камере мокрые следы блестят
  if (s.hallDark) { o.ceilOn.color.setHex(0x30322e); o.l1.intensity = .25; o.l3.intensity = .2; o.l4.intensity = .2; o.flickOn = false; }
  const A2 = [['temych', SEAT[0], 0, SEAT[1], 0, { clips: [['Sitting_Idle_Loop', t + 2, 1]], look: [4.5, 1.18, .12] }, null]];
  if (s.frank02) A2.push(['mihalych', -3.6, 0, -4.4, .84, STILL('Idle_Loop', 2.1), [7.45, 3.85, 5.45]]);   // в глубине холла, лицом к камере
  return A2; } };
SH.cam03 = { loc: 'lobby', pos: [.2, 3.2, -1.6], look: [-1.5, 1.25, -6], fov: 58, cctv: 1, set: (o, t) => { const s = SHS();
  setLift(o, o.lifts[0], s.liftOpen ? 1 : 0, String(s.lift || '1')); o.cabLight.intensity = s.liftOpen ? 2.4 : 0;
  if (s.lift13) setLift(o, o.lifts[1], 0, '13');
  shClock(o, s, t);
  if (s.palmMove) { o.palm.position.set(-.82, 0, -5.3); o.palmShadow.visible = false; }
  return []; } };
SH.cam04 = { loc: 'stairs', pos: [1.25, 7.3, -.3], look: [-.1, 4.5, -5.0], fov: 62, cctv: 1, set: (o, t) => { const s = SHS(); o.setFloor(1, 4); o.setFloor(0, 3); o.setFloor(2, 5);
  if (s.stDark) { stDim(o, 0); irOn(o, [1.2, 7.1, -.5], 1.7); } else if (!s.st) o.bulbs.forEach(b => { b.li.intensity = b.base * .4; });
  return s.guard04 ? [['smenshik', -.3, 4.5, -5.5, PI, STILL('Idle_Loop', .7), null]] : []; } };
// 7-й: Гас моет (s.gus), свет горит, пока он там; без него — темно, камера в ИК
SH.cam07 = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { const s = SHS();
  const C = s.gus ? Object.assign({}, CLEAN) : {}; C.door7 = 0;
  if (s.bucket7) { C.bucket = [-.3, -8.1, .7]; C.mop = [.35, -8.9, 1.25, 1]; }
  o.cleanSet(C); o.ajar10(s.door7 ? 1.4 : 0);
  if (s.gus) { setLamps(o, 7); const z = -9.45 + Math.sin(t * .05) * 1.5; return [['cleaner', -.1, 0, z, .2, mopGus(o, -.1, z, .2, t), null]]; }
  setLamps(o, s.l7 ? 7 : 0); o.amb.intensity = s.l7 ? .36 : .1; if (!s.l7) irOn(o, [1.0, 2.6, -.7], 1.4);
  return []; } };
SH.cam10 = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .9, -12], fov: 46, cctv: 1, set: (o, t) => { const s = SHS(); o.setFloor(10);
  setLamps(o, s.l10 ? 7 : 1, true); o.amb.intensity = s.l10 ? .36 : .12; o.ajar10(s.door10 ? 1 : 0);
  if (s.door10) { o.hole10.material = o.holeLit; o.faceL.position.set(.95, 1.3, -5.6); o.faceL.color.setHex(0xffd9a0); o.faceL.intensity = 1.6; o.faceL.distance = 3.2; }
  if (s.chair10) { o.chair10.visible = true; o.chair10.position.set(-.15, 0, -5.3); o.chair10.rotation.y = 2.5; }
  return []; } };
SH.cam_m2 = { loc: 'basement', pos: [.85, 2.32, -.4], look: [0, .8, -14], fov: 56, cctv: 1, set: (o, t) => { const s = SHS();
  if (s.mOff) { bsDark(o, 0); irOn(o, [.85, 2.2, -.6], 1.5); }
  if (s.sdoor) { o.sdoor.rotation.y = 1.25; o.roomL.intensity = 4; o.roomL.distance = 8; o.roomL.position.set(0, 1.2, -13.6); o.glow.material.opacity = 1; o.glow.scale.set(2.2, 3, 1); }   // дверь серверной открыта, внутри — свечи
  return []; } };
SH.dbg_eface0 = { loc: 'lobby', pos: [.6, 1.64, 5.55], look: [.55, 1.62, 4.95], fov: 34, set: (o, t) => [['temych', .55, 0, 4.95, 0, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet' }, 'cam']] };
SH.dbg_eface1 = { loc: 'lobby', pos: [.6, 1.64, 5.55], look: [.55, 1.62, 4.95], fov: 34, set: (o, t) => { A.temych.root.traverse(m => { if (m.isMesh) m.receiveShadow = false; }); return [['temych', .55, 0, 4.95, 0, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet' }, 'cam']]; } };
SH.dbg_stairs = { loc: 'stairs', pos: [1.15, 5.2, -5.6], look: [.2, 3.4, -.6], fov: 62, set: () => [] };
SH.dbg_stairs2 = { loc: 'stairs', pos: [-.8, 4.6, -.4], look: [.0, 3.4, -4.5], fov: 62, set: () => [] };
SH.dbg_base = { loc: 'basement', pos: [.8, 2.3, -.5], look: [0, .8, -14], fov: 56, set: () => [] };
SH.dbg_base2 = { loc: 'basement', pos: [.2, 1.6, -11.6], look: [0, 1.0, -14], fov: 50, set: o => { o.sdoor.rotation.y = 1.2; return []; } };
SH.dbg_booth_top = { loc: 'lobby', pos: [5.6, 6.5, -.3], look: [5.6, 0, -.29], fov: 60, set: () => [] };
SH.test_mop = { loc: 'corridor', pos: [.82, 1.45, -6.75], look: [-.3, 1.0, -10.2], fov: 50, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN); return [['cleaner', -.1, 0, -9.45, .33, mopGus(o, -.1, -9.45, .33, t), null]]; } };
SH.test_mop_side = { loc: 'corridor', pos: [1.35, 1.2, -9.1], look: [-.1, .85, -9.6], fov: 55, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN); return [['cleaner', -.1, 0, -9.45, .33, mopGus(o, -.1, -9.45, .33, t), null]]; } };
// ---- постановка v3: передача ключей (сменщик протягивает связку через окно будки, Итан берёт её в ладонь) ----
const ease = k => k * k * (3 - 2 * k), seg = (t, a, b) => ease(clamp((t - a) / (b - a), 0, 1));
const KEYH = [5.1, 1.29, .3];   // где встречаются руки: над внутренним краем стойки, в окне между стёклами
function guardGive(t, rel) {   // rel — с какой секунды отпускает связку и убирает руку (null — держит)
  const up = seg(t, .1, .9), down = rel == null ? 0 : seg(t, rel, rel + .55), w = up * (1 - down);
  const tr = Math.sin(t * 41) * .0018 + Math.sin(t * 23.7) * .0014;   // рука дрожит
  return { clips: [['Idle_Loop', t + 3.1, 1]], legs: 'feet', spine: { to: KEYH, k: .3 * w },
    arm: { r: { at: [KEYH[0] + .01 + tr, KEYH[1] + .1 + tr, KEYH[2] + .02], by: 'pinch', fdir: [0, -.35, -1], pdir: [1, -.4, 0], w } },
    fing: { r: ['pinch', up * (rel == null || t < rel ? 1 : 1 - seg(t, rel, rel + .25))] }, hold: rel == null || t < rel ? { r: 'keys' } : null, look: [5.1, 1.62, -.5] };
}
function ethanTake(t, t0) {   // t0 — когда начинает тянуться; ключи переходят в ладонь на t0 + .95
  const r = seg(t, t0, t0 + .75), back = seg(t, t0 + 1.15, t0 + 1.9), got = t > t0 + .95;
  const at = [lerp(KEYH[0], 5.0, back), lerp(KEYH[1] - .005, 1.08, back), lerp(KEYH[2], -.2, back)];
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', spine: { to: KEYH, k: .4 * r * (1 - back) },
    arm: { r: { at, by: 'palm', fdir: [lerp(0, .3, back), lerp(.15, -.2, back), lerp(1, .6, back)], pdir: [0, 1, 0], w: r * (1 - seg(t, t0 + 2.1, t0 + 2.7)) } },
    fing: { r: [got ? 'grip' : 'open', got ? seg(t, t0 + .8, t0 + 1.0) : r] }, hold: got ? { r: ['keys', 'keys_palm'] } : null, look: got ? null : KEYH };
}
SH.tg_offer = { loc: 'lobby', pos: [7.25, 1.6, .15], look: [5.1, 1.3, .2], fov: 46, set: (o, t) => [['smenshik', 5.1, 0, .98, PI, guardGive(t, null), null], ['temych', 5.08, 0, -.48, 0, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', look: KEYH }, null]] };
SH.tg_take = { loc: 'lobby', pos: [7.25, 1.6, .15], look: [5.1, 1.3, .2], fov: 46, set: (o, t) => [['smenshik', 5.1, 0, .98, PI, guardGive(t + 2, 2 + 1.0), null], ['temych', 5.08, 0, -.48, 0, ethanTake(t, .05), null]] };
SH.tg_close = { loc: 'lobby', pos: [5.75, 1.5, .02], look: [5.1, 1.32, .3], fov: 40, set: (o, t) => [['smenshik', 5.1, 0, .98, PI, guardGive(t + 2, 2 + 1.0), null], ['temych', 5.08, 0, -.48, 0, ethanTake(t, .05), null]] };
// старые имена кадров из первой версии → новые
const ALIAS = { f01: 'hall_cam', f02: 'exterior', f03: 'guard', f04: 'desk_note', f05: 'note', f06: 'monitors', f07: 'corridor_cam', f08: 'elevator_open', f09: 'cam13_monster', f10: 'door_katya', f11: 'entrance_cam', f12: 'katya_face' };

/* ---------- показ ---------- */
let canvas = null, scenesReady = false;
const cam = new T.PerspectiveCamera(50, V.RW / V.RH, .05, 120), feedCam = new T.PerspectiveCamera(60, 4 / 3, .05, 80);
let postScene, postCam, postMat, feedRT = [], feedT = 0, reflRT = null, reflN = 0, curProps = null;
const rcam = new T.PerspectiveCamera(50, V.RW / V.RH, .05, 120);

/* ---------- «Кино»: тени, отражения, объёмный свет, пыль, постобработка ---------- */
// тени: в каждом месте тени отбрасывают две самые заметные для камеры лампы (число постоянное — шейдеры не пересобираются)
const SHADOW_N = 2;
// лучи света под лампами: мягкий конус, ярче у лампы, тает к полу и в тумане; дышит пылью
const BEAM_VS = `varying vec3 vN, vV, vW; varying float vY; uniform float uH;
  void main(){ vY = position.y / uH + .5; vec4 w = modelMatrix * vec4(position, 1.); vW = w.xyz; vec4 mv = viewMatrix * w; vV = -mv.xyz; vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * mv; }`;
const BEAM_FS = `varying vec3 vN, vV, vW; varying float vY; uniform vec3 uC; uniform float uI, uT, uFog;
  float h(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
  float n3(vec3 p){ vec3 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
    return mix(mix(mix(h(i), h(i + vec3(1,0,0)), f.x), mix(h(i + vec3(0,1,0)), h(i + vec3(1,1,0)), f.x), f.y), mix(mix(h(i + vec3(0,0,1)), h(i + vec3(1,0,1)), f.x), mix(h(i + vec3(0,1,1)), h(i + vec3(1,1,1)), f.x), f.y), f.z); }
  void main(){ float f = abs(dot(normalize(vN), normalize(vV))); float d = length(vV);
    float a = pow(f, 2.5) * pow(vY, 1.4) * uI * exp(-d * uFog * 1.4) * smoothstep(.15, 1.2, d);
    a *= .65 + .7 * n3(vW * 2.2 + vec3(0., -uT * .15, uT * .07));
    gl_FragColor = vec4(uC * a * .22, 1.); }`;
// пылинки: висят вокруг камеры (сетка повторяется), светятся только в лучах ламп
const DUST_VS = `uniform vec3 uCam; uniform float uT, uPx; uniform vec4 uL[6]; varying float vA;
  void main(){ vec3 p = position + vec3(sin(uT * .13 + position.y * 3.) * .3, -uT * .025, cos(uT * .11 + position.x * 2.) * .3);
    p = uCam + mod(p - uCam + 4., 8.) - 4.;
    float a = 0.; for (int i = 0; i < 6; i++) { vec3 d = p - uL[i].xyz; float r = .25 + max(0., -d.y) * .42; a += uL[i].w * smoothstep(r, r * .3, length(d.xz)) * step(d.y, .1) * smoothstep(-4., -.2, d.y); }
    vec4 mv = viewMatrix * vec4(p, 1.); float z = -mv.z; vA = min(1., a) * smoothstep(.3, 1., z) * smoothstep(7., 3., z) * (.45 + .55 * fract(sin(dot(position, vec3(12.9, 78.2, 37.7))) * 4375.));
    gl_PointSize = uPx * (.012 + .012 * fract(position.x * 91.)) / max(z, .1); gl_Position = projectionMatrix * mv; }`;
const DUST_FS = `varying float vA; void main(){ vec2 d = gl_PointCoord - .5; float a = smoothstep(.5, .0, length(d)) * vA; gl_FragColor = vec4(vec3(1., .93, .82) * a * .7, 1.); }`;
let pmrem = null;
function cineLoc(R, o) {
  const S = o.S, skip = new Set([o.rim, o.faceL, o.slitLight, o.lamp, o.boothLight, o.candle, o.torch]);
  S.traverse(m => {
    if (!m.isMesh) return;
    const mt = Array.isArray(m.material) ? m.material[0] : m.material, g = m.geometry;
    if (!g.boundingSphere) g.computeBoundingSphere();
    const solid = !mt.isMeshBasicMaterial && !mt.transparent && !mt.isShaderMaterial;
    m.receiveShadow = solid;
    m.castShadow = solid && mt.side !== T.BackSide && g.boundingSphere.radius * Math.max(m.scale.x, m.scale.y, m.scale.z) < 2.4;
  });
  o.casters = []; o.beams = [];
  S.traverse(l => { if (!l.isPointLight) return;
    l.shadow.mapSize.set(FINE ? 512 : 256, FINE ? 512 : 256); l.shadow.bias = -.003; l.shadow.normalBias = .025; l.shadow.camera.near = .08; l.shadow.camera.far = l.distance || 20;
    if (!skip.has(l)) o.casters.push(l);
    if (!skip.has(l) && l.position.y > 2.3) {
      const wp = new T.Vector3(); l.getWorldPosition(wp); const h = wp.y - .02;
      const m = new T.Mesh(new T.CylinderGeometry(.2, .2 + h * (o.name === 'corridor' ? .26 : .42), h, 28, 1, true), new T.ShaderMaterial({ vertexShader: BEAM_VS, fragmentShader: BEAM_FS, transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.DoubleSide,
        uniforms: { uH: { value: h }, uC: { value: l.color }, uI: { value: 0 }, uT: { value: 0 }, uFog: { value: .05 } } }));
      m.position.set(l.position.x, l.position.y - h / 2 - .02, l.position.z); m.renderOrder = 5; l.parent.add(m); o.beams.push({ l, m, base: Math.max(.5, l.intensity) });
    } });
  o.casters.slice(0, SHADOW_N).forEach(l => (l.castShadow = true));
  // пыль
  const n = FINE ? 2200 : 900, pos = new Float32Array(n * 3); for (let i = 0; i < n * 3; i++) pos[i] = Math.random() * 8;
  const g = new T.BufferGeometry(); g.setAttribute('position', new T.BufferAttribute(pos, 3));
  o.dustU = { uCam: { value: new T.Vector3() }, uT: { value: 0 }, uPx: { value: 900 }, uL: { value: Array.from({ length: 6 }, () => new T.Vector4()) } };
  o.dust = new T.Points(g, new T.ShaderMaterial({ vertexShader: DUST_VS, fragmentShader: DUST_FS, uniforms: o.dustU, transparent: true, depthWrite: false, blending: T.AdditiveBlending }));
  o.dust.frustumCulled = false; o.dust.renderOrder = 6; S.add(o.dust);
  // отражения: окружение места снимается один раз со всеми лампами — блики ламп в мокром полу, металле, глазах
  pmrem = pmrem || new T.PMREMGenerator(R);
  const at = { lobby: [0, 1.6, 2], corridor: [0, 1.6, -12], server: [0, 1.5, 0], stairs: [0, 4.6, -3], basement: [0, 1.4, -7] }[o.name] || [0, 1.6, 0];
  if (o.lamps) setLamps(o, 7);
  o.dust.visible = false; o.beams.forEach(b => (b.m.visible = false));
  S.position.set(-at[0], -at[1], -at[2]); S.updateMatrixWorld(true);
  const env = pmrem.fromScene(S, .02, .05, 60); S.environment = env.texture;
  S.position.set(0, 0, 0); S.updateMatrixWorld(true);
  o.dust.visible = true; o.beams.forEach(b => (b.m.visible = true));
  if (o.reset) o.reset();
}
const _lv = new T.Vector3();
function cineTick(o, T0) {
  // кто отбрасывает тени: самые яркие лампы рядом с камерой
  const sc = l => l.intensity * (l.visible ? 1 : 0) / (1 + l.getWorldPosition(_lv).distanceToSquared(cam.position) * .06);
  const best = o.casters.slice().sort((a, b) => sc(b) - sc(a)).slice(0, SHADOW_N);
  for (const l of o.casters) l.castShadow = best.includes(l);
  const fogD = o.S.fog ? o.S.fog.density : .05, L6 = o.dustU.uL.value; let k = 0;
  const lit = o.beams.slice().sort((a, b) => b.l.intensity / (1 + b.l.position.distanceToSquared(cam.position)) - a.l.intensity / (1 + a.l.position.distanceToSquared(cam.position)));
  for (const b of o.beams) { const U = b.m.material.uniforms; U.uI.value = clamp(b.l.intensity / b.base, 0, 1.6) * (b.l.visible ? 1 : 0); U.uT.value = T0; U.uFog.value = fogD; }
  for (let i = 0; i < 6; i++) { const b = lit[i]; if (b) { b.l.getWorldPosition(_lv); L6[i].set(_lv.x, _lv.y, _lv.z, clamp(b.l.intensity / b.base, 0, 1.4)); } else L6[i].set(0, -99, 0, 0); k++; }
  o.dustU.uCam.value.copy(cam.position); o.dustU.uT.value = T0; o.dustU.uPx.value = V.RH * .9;
}
// кадр: сцена в HDR-буфер со сглаживанием → четверть кадра размывается (свечение ламп и мягкие края объектива) → итог:
// плёночная кривая (ACES), холодные тени / тёплый свет, ореол вокруг ярких ламп, хроматизм к краям, виньетка, мелкое зерно
let PB = null;
const POST_VS = 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }';
function cinePost() {
  const q = () => new T.WebGLRenderTarget(Math.max(8, V.RW >> 2), Math.max(8, V.RH >> 2), { type: T.HalfFloatType, magFilter: T.LinearFilter, minFilter: T.LinearFilter });
  PB = { a: q(), b: q() };
  PB.down = new T.ShaderMaterial({ uniforms: { tD: { value: null }, uPx: { value: new T.Vector2() } }, vertexShader: POST_VS,
    fragmentShader: `uniform sampler2D tD; uniform vec2 uPx; varying vec2 vUv; void main(){ vec3 c = vec3(0.);
      for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) c += texture2D(tD, vUv + vec2(float(i), float(j)) * uPx * 1.5).rgb; gl_FragColor = vec4(c / 9., 1.); }` });
  PB.blur = new T.ShaderMaterial({ uniforms: { tD: { value: null }, uDir: { value: new T.Vector2() } }, vertexShader: POST_VS,
    fragmentShader: `uniform sampler2D tD; uniform vec2 uDir; varying vec2 vUv; void main(){
      vec3 c = texture2D(tD, vUv).rgb * .227; c += (texture2D(tD, vUv + uDir * 1.385).rgb + texture2D(tD, vUv - uDir * 1.385).rgb) * .316; c += (texture2D(tD, vUv + uDir * 3.23).rgb + texture2D(tD, vUv - uDir * 3.23).rgb) * .07;
      gl_FragColor = vec4(c, 1.); }` });
  PB.comp = new T.ShaderMaterial({ uniforms: { tD: { value: null }, tBl: { value: null }, uT: { value: 0 }, uCctv: { value: 0 }, uIr: { value: 0 }, uVhs: { value: .7 }, uScare: { value: 0 }, uAsp: { value: 1 }, uExp: { value: .95 } }, vertexShader: POST_VS,
    fragmentShader: `uniform sampler2D tD, tBl; uniform float uT, uCctv, uIr, uVhs, uScare, uAsp, uExp; varying vec2 vUv;
      float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
      vec3 aces(vec3 x){ return clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0., 1.); }
      void main(){
        vec2 uv = vUv, d = uv - .5; float r = length(d * vec2(uAsp, 1.)) / max(uAsp, 1.) * 1.6;
        uv += d * dot(d, d) * (.02 + .12 * uCctv);                                             // объектив (у камер наблюдения — «рыбий глаз»)
        float row = floor(uv.y * 540.), fr = floor(uT * 24.);
        uv.x += (h(vec2(row, fr)) - .5) * uVhs * uCctv * .002;                                  // запись с камеры чуть дрожит строками
        uv += d * uScare * .05 * (h(vec2(fr, 3.)) - .5);                                        // скример: картинку рвёт к центру
        float ca = (.0012 + .012 * uScare) * (.25 + r * r * 1.4);
        vec3 c = vec3(texture2D(tD, uv + d * ca).r, texture2D(tD, uv).g, texture2D(tD, uv - d * ca).b);
        if (uScare > .02) {   // скример: рывок к центру смазывает картинку, свет режет красным
          vec3 z = c; for (int i = 1; i < 7; i++) z += texture2D(tD, uv - d * float(i) * .018 * uScare).rgb; c = mix(c, z / 7., min(1., uScare * 1.3));
          c *= vec3(1. + .5 * uScare, 1. - .25 * uScare, 1. - .3 * uScare);
        }
        vec3 bl = texture2D(tBl, uv).rgb;
        c = mix(c, bl, smoothstep(.62, 1.25, r) * .7 * (1. - uCctv));                          // глубина резкости: края кадра мягче
        c += max(bl - .38, 0.) * 1.35 + bl * vec3(.07, .035, .02);                              // свечение ламп и ореол
        c = aces(c * uExp);
        c = mix(c * 12.92, 1.055 * pow(max(c, vec3(.0031308)), vec3(1. / 2.4)) - .055, step(.0031308, c));
        c = pow(c, vec3(1.18));                                                                  // ночь: тени глубже
        float l = dot(c, vec3(.299, .587, .114));
        c = mix(c * vec3(.86, 1.0, 1.07) + vec3(0., .008, .018), c * vec3(1.06, .99, .9), smoothstep(.18, .7, l));   // холодные тени, тёплый свет
        c = mix(vec3(l), c, .88);
        c = mix(c, vec3(l) * vec3(.86, 1., .9), uCctv * .55) + max(c - .6, 0.) * uCctv * .8;
        c = mix(c, vec3(pow(l, .78) * 1.2) * vec3(.9, 1., .94), uIr);                          // ИК-режим камеры: ч/б, светлее у объектива
        c *= 1. - uCctv * .09 * step(1., mod(floor(gl_FragCoord.y / 2.), 2.));
        c *= 1. - smoothstep(.55, 1.5, r) * .6;                                                   // виньетка
        c += (h(gl_FragCoord.xy + fract(uT * 7.13) * 731.) - .5) * (.035 + uCctv * .05 + uVhs * .015);   // зерно
        if (uv.x < 0. || uv.x > 1. || uv.y < 0. || uv.y > 1.) c = vec3(0.);
        gl_FragColor = vec4(c, 1.); }` });
}
function cineRender(R, T0, cctv, scare, vhs) {
  const quad = postScene.children[0], RT = window.__W3core.RT;
  const pass = (m, to) => { quad.material = m; R.setRenderTarget(to); R.render(postScene, postCam); };
  PB.down.uniforms.tD.value = RT.texture; PB.down.uniforms.uPx.value.set(1 / V.RW, 1 / V.RH); pass(PB.down, PB.a);
  const bx = 1 / PB.a.width, by = 1 / PB.a.height, B = PB.blur.uniforms;
  for (const s of [1, 2.2]) { B.tD.value = PB.a.texture; B.uDir.value.set(bx * s, 0); pass(PB.blur, PB.b); B.tD.value = PB.b.texture; B.uDir.value.set(0, by * s); pass(PB.blur, PB.a); }
  const U = PB.comp.uniforms; U.uExp.value = curLoc && curLoc.name === 'corridor' ? .68 : curLoc && curLoc.name === 'server' ? 1.05 : .92; U.tD.value = RT.texture; U.tBl.value = PB.a.texture; U.uT.value = T0; U.uCctv.value = cctv; U.uScare.value = scare; U.uVhs.value = vhs; U.uAsp.value = V.RW / V.RH;
  pass(PB.comp, null); quad.material = postMat;
}
// «Кино» следит за скоростью: если кадр рисуется дольше ~38 мс, внутреннее разрешение падает ступенями (до 55%), если быстро — растёт обратно
let perfT = 0, perfN = 0, perfS = 0, perfCool = 0;
function perfTick(d) {   // d — мс между кадрами движка
  if (!CINE || !scenesReady || d > 250) return;   // пауза/вкладка в фоне — не считаем
  perfS += d; if (++perfN < 45) return; const avg = perfS / perfN; perfN = perfS = 0; if (perfCool-- > 0) return;
  const rs = V.rs || 1, nr = avg > 38 ? Math.max(.55, rs - .15) : avg < 21 && rs < 1 ? Math.min(1, rs + .1) : rs;
  if (nr !== rs) { V.rs = nr; perfCool = 2; setLand(V.land, true); }
}
// ориентация на лету: буферы под новый размер
function setLand(land, force) {
  if (land === V.land && scenesReady && !force) return;
  window.__W3core.setLand(land);
  if (!scenesReady) return;
  const R = window.__W3core.R; R.setSize(V.RW, V.RH, false);
  window.__W3core.RT.setSize(V.RW, V.RH); reflRT.setSize(V.RW >> 1, V.RH >> 1);
  if (PB) { PB.a.setSize(Math.max(8, V.RW >> 2), Math.max(8, V.RH >> 2)); PB.b.setSize(Math.max(8, V.RW >> 2), Math.max(8, V.RH >> 2)); }
  postMat.uniforms.uB.value.set(V.BW, V.BH);
}
const PROPS = { jacket: 1, badge: 1, clock: null };   // «нормы» холла; сюжет может передать o.props = { jacket: 0, badge: 'scratched', clock: '03:00' }
let VHS = .7;
function init() {
  if (scenesReady) return true;
  try {
    canvas = document.createElement('canvas'); canvas.width = V.RW; canvas.height = V.RH;
    window.__W3core.R = new T.WebGLRenderer({ canvas, antialias: false, alpha: false, powerPreference: 'high-performance', preserveDrawingBuffer: false });
  } catch (e) { return false; }
  const R = window.__W3core.R; R.setPixelRatio(1); R.setSize(V.RW, V.RH, false); R.useLegacyLights = true; R.outputColorSpace = T.SRGBColorSpace;
  if (CINE) { R.shadowMap.enabled = true; R.shadowMap.type = T.PCFSoftShadowMap; }
  makeTextures(); A = window.__W3chars.cast();
  if (CINE) { R.shadowMap.autoUpdate = false; for (const k in A) A[k].root.traverse(m => { if (m.isMesh) { m.castShadow = true; m.receiveShadow = true; } }); }
  L.lobby = buildLobby(); L.corridor = buildCorridor(); L.server = buildServer(); L.stairs = buildStairs(); L.basement = buildBasement();
  for (const k in L) L[k].name = k;
  if (CINE) for (const k in L) cineLoc(R, L[k]);
  // буфер кадра и постобработка: цветокор + дизеринг + ступени цвета + плёнка VHS (дрожь строк, полоса внизу, редкий залом,
  // цветная кайма в 1 пиксель, строки, лёгкая бочка); у камер наблюдения бочка сильнее и пересвет
  window.__W3core.RT = new T.WebGLRenderTarget(V.RW, V.RH, CINE ? { type: T.HalfFloatType, samples: 4 } : { magFilter: T.NearestFilter, minFilter: T.NearestFilter });
  if (CINE) cinePost();
  postMat = new T.ShaderMaterial({
    uniforms: { tD: { value: window.__W3core.RT.texture }, uB: { value: new T.Vector2(V.BW, V.BH) }, uHD: { value: HD }, uLv: { value: 22 }, uCctv: { value: 0 }, uIr: { value: 0 }, uGam: { value: 1.25 }, uT: { value: 0 }, uVhs: { value: .7 }, uScare: { value: 0 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }',
    fragmentShader: `uniform sampler2D tD; uniform vec2 uB; uniform float uHD, uLv, uCctv, uIr, uGam, uT, uVhs, uScare; varying vec2 vUv;
      float b2(vec2 a){ a = floor(a); return fract(dot(a, vec2(.5, a.y * .75))); }
      float b4(vec2 a){ return b2(.5 * a) * .25 + b2(a); }
      float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
      void main(){
        vec2 uv = vUv, d = uv - .5;
        uv += d * dot(d, d) * (.03 + .1 * uCctv);                                   // бочка (у камер — «рыбий глаз»)
        float row = floor(uv.y * uB.y), fr = floor(uT * 30.);
        uv.x += (h(vec2(row, fr)) - .5) * uVhs * 1.1 / uB.x;                       // дрожь строк, до полпикселя
        float band = smoothstep(.986, 1., 1. - uv.y) * uVhs;                        // полоса переключения головок внизу
        uv.x += band * (h(vec2(row, fr * .37)) - .3) * 7. / uB.x;
        float ph = mod(uT + 4., 13.), cy = (ph - 12.2) * 1.5;                       // залом ленты: раз в 13 с пробегает вверх
        float cr = (cy > 0. && cy < 1.) ? smoothstep(.02, 0., abs(uv.y - cy)) * uVhs : 0.;
        uv.x -= cr * (2. + 2. * h(vec2(row, fr))) / uB.x;
        float ca = (.85 + 2.6 * uScare) / uB.x;                                     // цветная кайма: 1 px, на скримере 3–4
        vec3 c = vec3(texture2D(tD, uv + vec2(ca, 0.)).r, texture2D(tD, uv).g, texture2D(tD, uv - vec2(ca, 0.)).b);
        c = mix(c * 12.92, 1.055 * pow(max(c, vec3(.0031308)), vec3(1. / 2.4)) - .055, step(.0031308, c));   // буфер линейный → sRGB
        c = pow(c, vec3(uGam));   // ночь: тени глубже, но без пятен
        float l = dot(c, vec3(.299, .587, .114));
        c = mix(c * vec3(.86, 1.02, 1.08) + vec3(0., .012, .024), c * vec3(1.06, .98, .88), smoothstep(.25, .8, l));   // холодные тени, тёплый свет
        c = mix(c, vec3(l) * vec3(.88, 1., .9), uCctv * .3) + max(c - .62, 0.) * uCctv * .9;                         // камера: блеклая, свет «горит»
        c = mix(c, vec3(pow(l, .78) * 1.2) * vec3(.9, 1., .94), uIr);                                                 // ИК-режим камеры
        c *= 1. - (.06 + .07 * uCctv) * step(1., mod(floor(gl_FragCoord.y / uHD), 2.));           // строки
        c += cr * .22 * h(vec2(gl_FragCoord.x, fr)) + band * .05 * h(gl_FragCoord.xy + fr);
        if (uv.x < 0. || uv.x > 1. || uv.y < 0. || uv.y > 1.) c = vec3(0.);
        c = floor(c * uLv + b4(gl_FragCoord.xy) * .95) / uLv;
        gl_FragColor = vec4(c, 1.);
      }` });
  reflRT = new T.WebGLRenderTarget(V.RW / 2, V.RH / 2, { magFilter: T.LinearFilter, minFilter: T.LinearFilter });
  L.lobby.glassU.tRef.value = reflRT.texture; classifyLobby(L.lobby);
  window.__dbgA = A; window.__dbgL = L;
  postScene = new T.Scene(); postCam = new T.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  postScene.add(new T.Mesh(new T.PlaneGeometry(2, 2), postMat));
  for (let i = 0; i < 4; i++) feedRT.push(new T.WebGLRenderTarget(96, 72, { magFilter: T.NearestFilter, minFilter: T.NearestFilter }));
  L.lobby.screens.forEach((s, i) => { s.material.map = feedRT[i].texture; s.material.needsUpdate = true; });
  feedRT[3].texture.colorSpace = T.SRGBColorSpace;
  scenesReady = true; return true;
}
const NAME = { 'ИТАН': ['temych'], 'СМЕНЩИК': ['smenshik'], 'ЛИЛИ': ['katya', 'katyaSmile', 'katyaThing'], '???': ['monster'], 'ФРЭНК': ['mihalych'], 'ГАС': ['cleaner'] };   // кто говорит → чей рот двигается
const _v = new T.Vector3(), _d = new T.Vector3();
function aim(c, pos, look, fov, cx, cy, z, sx, sy) {
  c.position.set(pos[0], pos[1], pos[2]); c.fov = fov; c.aspect = V.RW / V.RH; c.updateProjectionMatrix(); c.lookAt(look[0], look[1], look[2]); c.updateMatrixWorld();
  // точка (cx, cy) кадра → направление; зум сужает угол обзора вокруг неё (как настоящий объектив)
  _v.set((cx - .5) * 2, -(cy - .5) * 2, .5).unproject(c); _d.copy(_v).sub(c.position).normalize();
  c.lookAt(c.position.x + _d.x, c.position.y + _d.y, c.position.z + _d.z);
  c.fov = 2 * Math.atan(Math.tan(fov * PI / 360) / Math.max(.3, z)) * 180 / PI; c.updateProjectionMatrix();
  c.rotateY(-sx * .00012); c.rotateX(-sy * .00012);
}
let lastT = 0, curLoc = null, lastId = null;
function stage(id, t, opt = {}) {
  const sh = SH[ALIAS[id] || id] || SH.hall_cam, o = L[sh.loc];
  if (window.__W3chars && window.__W3chars.propsFrame) window.__W3chars.propsFrame();   // предметы в руках: видны только те, что держат в этом кадре
  curProps = Object.assign({}, PROPS, window.World && World.props, opt.props);
  if (curLoc && curLoc !== o) curLoc.reset();
  curLoc = o; o.reset(); o.ir = 0; if (o.irL) o.irL.intensity = 0; if (o.props) o.props(curProps);
  for (const k in A) { A[k].faceSet = 'base'; A[k].gore = 0; A[k].scream = 0; A[k].reflOnly = false; A[k].root.visible = true; A[k].aim = null; A[k].noTilt = false; A[k].shake = 0; A[k].struggle = 0; A[k].twitch = 0; A[k].no = 0; A[k].actT = undefined; }
  if (o.faceL) o.faceL.color.setHex(0xdde6ff);
  if (sh.face) for (const k in sh.face) A[k].faceSet = sh.face[k];   // выражение лица на весь кадр (set() ниже может уточнить)
  const acts = sh.set(o, t, sh) || [];
  if (sh.fog != null) o.S.fog.density = sh.fog;
  const used = new Set();
  for (const [k, x, y, z, ry, pose, look, only] of acts) {
    const C = A[k]; used.add(k);
    if (C.root.parent !== o.S) o.S.add(C.root);
    if (C.lastShot !== id) { C.lastShot = id; C.lookDir = null; C.prevP = null; C.walkV = null; }
    C.root.position.set(x, y, z); C.root.rotation.y = ry; C.pose = pose;
    C.look = look === 'cam' ? new T.Vector3(...sh.pos) : look ? new T.Vector3(...look) : null;
    C.reflOnly = only === 'refl';   // виден только в отражении на стекле
  }
  for (const k in A) if (!used.has(k) && A[k].root.parent) A[k].root.parent.remove(A[k].root);
  return { sh, o, used };
}
// какие предметы холла по какую сторону стекла (для отражения прячем ту сторону, где стоит камера)
function classifyLobby(o) {
  o.outObjs = [o.glass, o.rain, ...o.leafGlass]; o.inObjs = [o.glass, ...o.leafGlass]; const bb = new T.Box3();
  for (const ch of o.S.children) {
    if (ch.isLight || ch === o.glass || ch === o.rain || ch === o.sky || o.leaves.includes(ch)) continue;
    bb.setFromObject(ch); if (bb.isEmpty()) continue;
    if (bb.min.z > 5.85) o.outObjs.push(ch); else if (bb.max.z < 6.15) o.inObjs.push(ch);
  }
}
// отражение в стекле фасада: та же камера, отражённая в плоскости z = 6; рисуем в маленький буфер раз в два кадра
const _f = new T.Vector3(), _u = new T.Vector3(), BIAS = new T.Matrix4().set(.5, 0, 0, .5, 0, .5, 0, .5, 0, 0, .5, .5, 0, 0, 0, 1);
function renderRefl(R, o, used, force) {
  if (!force && (reflN++ % 2)) return;
  const inside = cam.position.z < 6, e = cam.matrixWorld.elements;
  _f.set(-e[8], -e[9], -e[10]); _u.set(e[4], e[5], e[6]);
  rcam.position.set(cam.position.x, cam.position.y, 12 - cam.position.z); rcam.up.set(_u.x, _u.y, -_u.z);
  rcam.lookAt(rcam.position.x + _f.x, rcam.position.y + _f.y, rcam.position.z - _f.z);
  rcam.projectionMatrix.copy(cam.projectionMatrix); rcam.projectionMatrixInverse.copy(cam.projectionMatrixInverse); rcam.updateMatrixWorld();
  const hid = [];
  for (const m of inside ? o.outObjs : o.inObjs) if (m.visible) { m.visible = false; hid.push(m); }
  for (const k in A) { const C = A[k]; if (C.root.parent !== o.S) continue; const z = C.root.position.z;
    if (C.reflOnly) C.root.visible = true; else if ((inside ? z > 5.9 : z < 6.1) && C.root.visible) { C.root.visible = false; hid.push(C.root); } }
  R.setRenderTarget(reflRT); R.render(o.S, rcam);
  for (const m of hid) m.visible = true;
  for (const k in A) if (A[k].reflOnly) A[k].root.visible = false;
  o.glassU.uTM.value.copy(BIAS).multiply(rcam.projectionMatrix).multiply(rcam.matrixWorldInverse);
}
function renderFeeds(R, T0) {
  // мониторы в будке: 0 — холл, 1 — вход, 2 — седьмой этаж, 3 — помехи (камера 13)
  const lob = L.lobby, cor = L.corridor;
  const views = [[lob.S, [7.45, 3.85, 5.45], [-2.2, .9, -5]], [lob.S, [-.9, 3.25, 8.1], [.15, .8, 6.2]], [cor.S, [1.0, 2.75, -.5], [-.1, .9, -25]]];
  lob.screens.forEach(s => (s.visible = false));   // экраны не должны снимать сами себя
  const gr = lob.glassU.uRef.value; lob.glassU.uRef.value = 0;   // отражение посчитано для главной камеры — на мониторах его нет
  views.forEach(([S, p, l], i) => { feedCam.position.set(...p); feedCam.lookAt(...l); R.setRenderTarget(feedRT[i]); R.render(S, feedCam); });
  lob.screens.forEach(s => (s.visible = true)); lob.glassU.uRef.value = gr;
  // помехи
  const nt = TX.noise; const c = nt.cx; const d = c.createImageData(64, 48);
  for (let i = 0; i < d.data.length; i += 4) { const v = Math.random() * 105; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; }
  c.putImageData(d, 0, 0); nt.needsUpdate = true; lob.screens[3].material.map = nt;
  R.setRenderTarget(null);
}
// главный вызов: нарисовать кадр shotId в 2D-контекст g размером W×H
// где может мелькнуть Жилец (случайное появление): за стеклом на улице, между колоннами, у лифтов, в конце коридора, за стойками
const CREEP = {
  lobby: [[-3.2, 7.6], [-.9, 8.4], [1.8, 7.4], [4.2, 8.8], [-5.2, 1.6], [3.0, 3.3], [-1.2, -4.6], [1.6, -4.4], [-6.5, -2.5]],
  corridor: [[0, -23.5], [.6, -16], [-.6, -11], [0, -7.5]],
  server: [[-2.6, -2.9], [2.6, -2.9], [-2.9, 2.6], [.4, -3.0], [2.9, 1.5]],
};
const _p = new T.Vector3(), _hp = new T.Vector3(), _hd = new T.Vector3();
function placeCreep(loc, seed, R) {
  const C = A.monster, list = CREEP[loc.name] || [], ok = [];
  cam.updateMatrixWorld();
  for (const [x, z] of list) {
    _p.set(x, 1.9, z); const d = _p.distanceTo(cam.position); if (d < 2.4 || d > 19) continue;
    _p.project(cam); if (Math.abs(_p.x) < .78 && _p.y > -.6 && _p.y < .9 && _p.z < 1) ok.push([x, z]);
  }
  if (!ok.length) return false;
  const [x, z] = ok[seed % ok.length];
  if (C.root.parent !== loc.S) loc.S.add(C.root);
  C.root.position.set(x, 0, z); C.root.rotation.y = Math.atan2(cam.position.x - x, cam.position.z - z);
  C.pose = 'monster'; C.look = cam.position.clone(); C.talk = 0;
  return true;
}
// «Кино» на некоторых видеокартах рисует освещённое чёрным (пол, колонны): титульный кадр сверяем с эталоном по яркости низа кадра
// (у нас ~0,24, у сломанного — почти 0). Дважды подряд втрое темнее — включаем «Ретро» с перезагрузкой; меню скажет об этом.
const gfxChk = { n: 0, bad: 0, done: false, c: null };
function gfxCheck(src) {
  if (++gfxChk.n < 60 || gfxChk.n % 30) return;   // после прогрева: шейдеры собраны, свет выставлен
  try {
    if (localStorage.getItem('ns1_gfxAuto') === '2') { gfxChk.done = true; return; }   // игрок сам вернул «Кино» — не спорим
    const c = gfxChk.c || (gfxChk.c = Object.assign(document.createElement('canvas'), { width: 24, height: 8 })), x = c.getContext('2d', { willReadFrequently: true });
    x.drawImage(src, 0, src.height * .72, src.width, src.height * .26, 0, 0, 24, 8);
    const d = x.getImageData(0, 0, 24, 8).data; let sum = 0; for (let i = 0; i < d.length; i += 4) sum += .299 * d[i] + .587 * d[i + 1] + .114 * d[i + 2];
    const L = sum / (d.length / 4) / 255; window.__gfxL = L;
    if (L >= .085) { gfxChk.done = true; return; }
    if (++gfxChk.bad >= 2) { gfxChk.done = true; localStorage.setItem('ns1_gfx', '"retro"'); localStorage.setItem('ns1_gfxAuto', '1'); location.reload(); }
  } catch (e) { gfxChk.done = true; }
}
function draw(g, W, H, id, o) {
  if (!init()) return false;
  const R = window.__W3core.R;
  const t = o.t || 0, T0 = o.T || 0, dt = Math.min(.05, Math.max(0, T0 - lastT)) || .016; lastT = T0;
  const newShot = id !== lastId; lastId = id;
  const { sh, o: loc, used } = stage(id, t, o);
  // кто говорит: рот двигается по громкости голоса
  for (const k in A) A[k].talk = 0;
  if (o.talk && o.talk.who) for (const k of NAME[o.talk.who] || []) if (used.has(k)) { A[k].talk = o.talk.v; break; }
  if (sh.hum && used.has(sh.hum) && !(o.talk && o.talk.who)) A[sh.hum].talk = .1 + .12 * Math.abs(Math.sin(t * 4.3)) * (Math.sin(t * 1.1) > -.5 ? 1 : 0);   // мурлычет себе под нос
  if (newShot) for (const k of used) A[k].smDone = false;   // после склейки покадровый Жилец сразу встаёт в новую позу (иначе миг виден старый кадр)
  for (const k of used) A[k].update(T0, dt);
  loc.tick(T0, dt);
  if (loc.post) loc.post(A, used, T0);
  // скример строго в лицо: голову Жильца ставим на ось камеры (поза рук и корпуса уводили её влево)
  const M = A.monster;
  if (M.aim && M.aim.on && used.has('monster')) { M.bone.Head.updateWorldMatrix(true, false); M.bone.Head.getWorldPosition(_hp); const P = sh.pos; _hd.set(sh.look[0] - P[0], 0, sh.look[2] - P[2]).normalize();
    const rx = _hp.x - P[0], rz = _hp.z - P[2], al = rx * _hd.x + rz * _hd.z;
    M.root.position.x -= rx - _hd.x * al; M.root.position.z -= rz - _hd.z * al; M.root.position.y += (P[1] - .04 - _hp.y) * M.aim.k; M.root.updateMatrixWorld(true); }
  let crept = false;
  // на мониторах в будке: лицо вплотную к камере холла
  if (o.creep && sh.feeds && !used.has('monster') && L.lobby === loc) { const C = A.monster; if (C.root.parent !== loc.S) loc.S.add(C.root); C.root.position.set(5.6, 0, 3.0); C.root.rotation.y = .75; C.pose = 'monster'; C.look = new T.Vector3(7.45, 3.85, 5.45); C.update(T0, dt); crept = true; }
  if (sh.feeds && (crept || ++feedT % 3 === 0)) renderFeeds(R, T0);
  aim(cam, sh.pos, sh.look, sh.fov, o.cx ?? .5, o.cy ?? .5, o.z ?? 1, o.sx || 0, o.sy || 0);
  // фонарь в руке: луч туда, куда показывает палец (o.torch = {x, y} — доли экрана)
  if (!loc.torch && /^(corridor|stairs|basement)$/.test(loc.name)) { const l = loc.torch = new T.SpotLight(0xfff0d8, 0, 0, .28, .5, 1); loc.S.add(l, l.target); }
  const tor = o.torch || sh.torch;   // кадр сам может держать фонарь (sh.torch = {x, y}) — например, спуск по лестнице
  if (loc.torch) { const l = loc.torch; l.intensity = tor ? 9 : 0;
    if (tor) { cam.updateMatrixWorld(); l.position.set(.18, -.25, 0).applyMatrix4(cam.matrixWorld); _p.set(tor.x * 2 - 1, 1 - tor.y * 2, .5).unproject(cam).sub(cam.position).normalize().multiplyScalar(10).add(cam.position); l.target.position.copy(_p); l.target.updateMatrixWorld(); } }
  if (o.creep && !crept && !used.has('monster') && placeCreep(loc, o.creep, R)) A.monster.update(T0, dt);
  const cctv = o.cctv ?? sh.cctv ?? 0;
  const PU = postMat.uniforms; PU.uCctv.value = cctv ? 1 : 0; PU.uIr.value = loc.ir ? 1 : 0; if (PB && PB.comp) PB.comp.uniforms.uIr.value = loc.ir ? 1 : 0; PU.uT.value = T0; PU.uScare.value = clamp(o.scare || 0, 0, 1); PU.uVhs.value = o.vhs ?? VHS;
  if (loc === L.lobby) {
    const U = loc.glassU, lit = clamp((loc.l1.intensity / 1.9) * .55 + (loc.l4.intensity / 1.25) * .25 + loc.amb.intensity * .5, 0, 1.4);
    U.uLit.value = lit; U.uT.value = T0; U.uFogD.value = loc.S.fog.density; U.uRef.value *= loc.reflBoost ?? 1;
    U.uDropC.value.setRGB(.42, .46, .52).multiplyScalar(.5 + .5 * clamp(loc.canopy.intensity / 1.5 + loc.street.intensity / 2.4, 0, 1.4));
    cam.updateMatrixWorld(); renderRefl(R, loc, used, newShot);
  }
  for (const k of used) if (A[k].reflOnly) A[k].root.visible = false;
  if (CINE) { cineTick(loc, T0); R.shadowMap.needsUpdate = true; }
  R.setRenderTarget(window.__W3core.RT); R.render(loc.S, cam);
  if (CINE) cineRender(R, T0, cctv ? 1 : 0, clamp(o.scare || 0, 0, 1), o.vhs ?? VHS);
  else { R.setRenderTarget(null); R.render(postScene, postCam); }
  g.imageSmoothingEnabled = CINE;
  if (sh.mirror) { g.save(); g.translate(W, 0); g.scale(-1, 1); g.drawImage(canvas, 0, 0, W, H); g.restore(); } else g.drawImage(canvas, 0, 0, W, H);
  if (CINE && id === 'door_katya' && !gfxChk.done) gfxCheck(canvas);   // титул: не рисует ли «Кино» чёрным на этой видеокарте
  g.imageSmoothingEnabled = true;
  return true;
}
// прогрев: по кадру из каждого места, чтобы все шейдеры собрались во время загрузки
function warm() {
  const c = document.createElement('canvas'); c.width = 54; c.height = 96; const g = c.getContext('2d');
  for (const id of ['hall_cam', 'monitors', 'katya_face', 'corridor_cam', 'server_mih', 'cam13_mih_monster', 'exterior']) draw(g, 54, 96, id, { t: 1, T: 1 });
}
const thumbs = {};
function thumb(id, o = {}) {   // маленькая картинка кадра (плитки камер в обходе)
  const key = id + (V.land ? '|L' : ''); if (thumbs[key]) return thumbs[key];
  const c = document.createElement('canvas'); c.width = V.land ? 240 : 135; c.height = V.land ? 135 : 240;
  if (!draw(c.getContext('2d'), c.width, c.height, id, Object.assign({ t: 2, T: 2 }, o))) return '';
  return (thumbs[key] = c.toDataURL('image/jpeg', .7));
}
// World.props — «нормы» холла (куртка, пропуск, часы); World.vhs — сила плёнки 0..1 (для пункта меню); World.tube — горит ли сейчас ненадёжная лампа (для звука)
window.World = { draw, init, warm, setLand, perf: perfTick, get land() { return V.land; }, get cine() { return CINE; }, thumb, shots: SH, alias: ALIAS, props: null, get ok() { return scenesReady; }, get tube() { return curLoc ? curLoc.tubeOK ?? 1 : 1; }, get ir() { return !!(curLoc && curLoc.ir); }, set vhs(v) { VHS = clamp(+v || 0, 0, 1); }, get vhs() { return VHS; } };
})();
