/* Места действия (холл с будкой и лифтами, улица, 7 этаж, серверная), кадры для каждой сцены и сам показ кадра. */
(() => {
'use strict';
const { T, RW, RH, rnd, lerp, clamp, ps1, mat, ctex, TX, box, plane, cyl, room, label, relabel, makeTextures } = window.__W3core;
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
  const wallM = mat(0xffffff, TX.wall), panelM = mat(0xffffff, TX.panel);
  const back = plane(16, 4.2, wallM, S); back.position.set(0, 2.1, -6);
  const left = plane(12, 4.2, wallM, S); left.rotation.y = PI / 2; left.position.set(-8, 2.1, 0);
  const right = plane(12, 4.2, wallM, S); right.rotation.y = -PI / 2; right.position.set(8, 2.1, 0);
  for (const [w, x, z, ry] of [[16, 0, -5.99, 0], [12, -7.99, 0, PI / 2], [12, 7.99, 0, -PI / 2]]) { const p = plane(w, 1.1, panelM, S); p.position.set(x, .55, z); p.rotation.y = ry; }
  // потолочные светильники (часть не горит)
  o.ceilLights = [];
  for (let i = -2; i <= 2; i++) for (let j = -1; j <= 1; j++) {
    const on = !(i === -2 && j === 1) && !(i === 1 && j === -1);
    const p = plane(1.1, .5, mat(on ? 0xe8f0e0 : 0x30322e, null, { basic: true }), S); p.rotation.x = PI / 2; p.position.set(i * 3.2, 4.19, j * 3.4); o.ceilLights.push(p);
  }
  o.flick = plane(1.1, .5, ps1(new T.MeshBasicMaterial({ color: 0xe8f0e0 })), S);   // свой материал: иначе мигали бы все панели o.flick.rotation.x = PI / 2; o.flick.position.set(-4.8, 4.18, 3.4);
  o.l1 = new T.PointLight(0xc8d8d0, 1.9, 13, 1.5); o.l1.position.set(-1, 3.8, 0); S.add(o.l1);
  o.l3 = new T.PointLight(0xb8c8d8, 1.3, 9, 1.5); o.l3.position.set(-.8, 3.6, -4.2); S.add(o.l3);
  o.l2 = new T.PointLight(0xffd9a0, .9, 9, 1.6); o.l2.position.set(-4.8, 3.7, 3.4); S.add(o.l2);   // ненадёжная лампа холла
  o.l4 = new T.PointLight(0xc8d4dc, 1.25, 7.5, 1.5); o.l4.position.set(.6, 3.7, 3.4); S.add(o.l4);   // над входом изнутри: свет до стекла
  // колонны
  for (const [x, z] of [[-4.5, 1], [2.3, 2.6]]) { box(.7, 4.2, .7, mat(0xffffff, TX.wall), x, 2.1, z, S); box(.74, 1.1, .74, panelM, x, .55, z, S); }
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
  const panel = box(.02, .5, .2, mat(0x222428), .68, 1.2, .5, cab);
  o.cabInd = label(.22, .1, 40, 18, (c, w, h, s = '1') => { c.fillStyle = '#060303'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff3a2a'; c.font = '700 15px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(s, w / 2, h / 2 + 1); }, cab);
  o.cabInd.position.set(0, 2.25, .78); o.cabInd.rotation.y = PI;
  o.cabLight = new T.PointLight(0xe8eedd, 0, 5, 1.5); o.cabLight.position.set(0, 2.2, 0); cab.add(o.cabLight);
  o.cab = cab;
  // табличка и плакат
  const sg = plane(2.6, .5, mat(0xffffff, TX.sign), S); sg.position.set(-.8, 3.35, -5.97);
  const ps = plane(.5, .75, mat(0xffffff, TX.poster), S); ps.position.set(2.6, 1.6, -5.97);
  const ex = plane(.5, .25, mat(0xffffff, TX.exit, { basic: true }), S); ex.rotation.y = PI / 2; ex.position.set(-7.97, 2.6, -3.5);
  box(.06, 2.2, 1.0, mat(0xffffff, TX.door), -7.96, 1.1, -3.5, S);
  // часы между лифтами (стрелки двигает кадр: время сцены или «встали в три»)
  const clk = new T.Group(); clk.position.set(-.8, 2.72, -5.96); S.add(clk);
  const cf = new T.Mesh(new T.CircleGeometry(.21, 16), mat(0xffffff, TX.clock)); clk.add(cf);
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
  const gs = plane(.7, .17, mat(0xffffff, TX.guardSign), S); gs.position.set(5.1, .9, .51);
  box(2.6, .05, .8, mat(0xffffff, TX.wood), 5.1, .78, -.05, S); for (const x of [3.9, 6.3]) box(.05, .78, .7, mat(0x1b1b1b), x, .39, -.05, S);
  // мониторы: два ряда, экраны развёрнуты к охраннику
  o.screens = [];
  const bank = new T.Group(); bank.position.set(4.5, 0, .12); bank.rotation.y = PI - .6; S.add(bank);
  box(1.0, .08, .3, mat(0x1a1b1d), 0, .84, -.05, bank);
  for (let r = 0; r < 2; r++) for (let c2 = 0; c2 < 2; c2++) {
    const g = new T.Group(); g.position.set((c2 - .5) * .48, 1.03 + r * .34, 0); bank.add(g);
    box(.5, .36, .12, mat(0x1a1b1d), 0, 0, -.06, g);
    const scr = plane(.44, .3, new T.MeshBasicMaterial({ color: 0xffffff }), g); scr.position.z = .001; ps1(scr.material);
    o.screens.push(scr);
  }
  // лампа
  const lampBase = cyl(.08, .1, .03, 8, mat(0x1f3a2a), S); lampBase.position.set(6.15, .81, -.15);
  box(.02, .38, .02, mat(0x1f3a2a), 6.15, 1.0, -.15, S);
  const shade = cyl(.06, .14, .14, 8, mat(0x1f5a3a, null, { emissive: 0x081a10 }), S); shade.position.set(6.05, 1.18, -.12); shade.rotation.z = .5;
  o.lampBulb = box(.05, .03, .05, mat(0xffe8a0, null, { basic: true }), 6.0, 1.12, -.12, S);
  o.lamp = new T.PointLight(0xffc46a, 1.6, 3.2, 1.5); o.lamp.position.set(5.95, 1.05, -.12); S.add(o.lamp);
  o.boothLight = new T.PointLight(0x9fe0c0, .6, 5, 1.6); o.boothLight.position.set(4.6, 1.4, -.2); S.add(o.boothLight);
  o.note = plane(.21, .28, mat(0xffffff, TX.note), S); o.note.rotation.x = -PI / 2; o.note.rotation.z = PI + .12; o.note.position.set(5.35, .807, -.2);
  o.keys = new T.Group(); box(.06, .008, .035, mat(0xb8a060, null, { emissive: 0x201800 }), 0, 0, 0, o.keys); box(.01, .006, .06, mat(0xbbbbbb), .015, 0, .04, o.keys); box(.01, .006, .05, mat(0xbbbbbb), -.012, 0, .035, o.keys);
  o.keys.position.set(5.68, .81, -.28); o.keys.rotation.y = .6; S.add(o.keys);
  box(.12, .1, .08, mat(0x2a2a2a), 5.9, .85, .15, S);   // рация
  const cup = cyl(.035, .03, .09, 8, mat(0xd8d0c0), S); cup.position.set(4.95, .85, -.3);
  const chair = new T.Group(); chair.position.set(5.2, 0, -.85); S.add(chair);
  box(.5, .07, .5, mat(0x161616), 0, .47, 0, chair); box(.5, .6, .06, mat(0x161616), 0, .8, -.24, chair); box(.05, .45, .05, mat(0x333333), 0, .22, 0, chair);
  o.chair = chair;
  // второй стул у пульта: на спинке так и висит куртка Михалыча («куртка на стуле осталась»)
  const ch2 = new T.Group(); ch2.position.set(6.25, 0, -.62); ch2.rotation.y = -.5; S.add(ch2);
  box(.46, .07, .46, mat(0x161616), 0, .47, 0, ch2); box(.46, .56, .06, mat(0x161616), 0, .78, -.22, ch2); box(.05, .45, .05, mat(0x333333), 0, .22, 0, ch2);
  const jk = new T.Group(); jk.position.set(0, 0, -.22); ch2.add(jk);
  const jm = mat(0x24324c), jt = mat(0x3a4a64);
  box(.5, .14, .16, jm, 0, 1.08, 0, jk); box(.5, .55, .05, jm, 0, .8, -.06, jk); box(.46, .4, .05, jm, 0, .87, .06, jk);   // плечи на спинке, спина и перед
  for (const s of [-1, 1]) { const sl = box(.11, .5, .1, jm, s * .27, .78, .02, jk); sl.rotation.z = s * .08; box(.11, .04, .1, jt, s * .285, .52, .02, jk); }   // рукава висят
  box(.16, .04, .005, mat(0xc8c8c8), .1, .97, .09, jk);   // нашивка
  o.jacket = jk;
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
  o.badgeCard = plane(.11, .082, mat(0xffffff, TX.badge), o.badge); o.badgeCard.position.y = -.03; box(.115, .087, .003, mat(0x9aa0a6), 0, -.03, -.003, o.badge);
  // камеры наблюдения (коробочки на стенах)
  for (const [x, y, z] of [[7.6, 3.95, 5.6], [-7.6, 3.95, -5.6]]) box(.18, .12, .3, mat(0xd0d0d0), x, y, z, S);
  // фасад: стекло (ночью отражает холл), плёнка, грязь, капли, рамы, двери
  buildFacade(o, S);
  // снаружи: тротуар, бордюр, дорога, фонарь, машина, дома напротив, небо
  const walk = plane(40, 5, mat(0xffffff, TX.tileSide), S); walk.rotation.x = -PI / 2; walk.position.set(0, .001, 8.5);
  box(40, .15, .3, mat(0xffffff, TX.curb), 0, .075, 11, S);
  const road = plane(60, 20, mat(0xffffff, TX.asphalt), S); road.rotation.x = -PI / 2; road.position.set(0, -.05, 21);
  for (let x = -24; x < 24; x += 6) { const ln = plane(2.4, .15, mat(0xb8b8a8), S); ln.rotation.x = -PI / 2; ln.position.set(x, -.04, 17); }
  // навес над входом
  box(5, .2, 2.4, mat(0x1a1c1f), 0, 3.4, 7.2, S); o.canopyLamp = box(.6, .04, .3, mat(0xfff0d0, null, { basic: true }), 0, 3.29, 7.2, S);
  o.canopy = new T.PointLight(0xffe2b0, 1.5, 6, 1.6); o.canopy.position.set(0, 3.1, 7.2); S.add(o.canopy);   // свет сверху: глазницы в тени
  const sgn = plane(3, .55, mat(0xffffff, TX.signOut, { basic: true }), S); sgn.position.set(0, 3.95, 6.17);
  // фонарь
  const pole = cyl(.06, .08, 6, 6, mat(0x222428), S); pole.position.set(3.8, 3, 10.4);
  box(1.2, .06, .06, mat(0x222428), 3.3, 5.95, 10.4, S); o.streetBulb = box(.4, .1, .2, mat(0xffb35a, null, { basic: true }), 2.8, 5.88, 10.4, S);
  o.street = new T.PointLight(0xff9a3a, 1.2, 16, 1.4); o.street.position.set(2.8, 5.6, 10.4); S.add(o.street);
  const pool = plane(4, 4, new T.MeshBasicMaterial({ color: 0xff9a3a, transparent: true, opacity: .12, depthWrite: false }), S); pool.rotation.x = -PI / 2; pool.position.set(2.8, .01, 10.6); ps1(pool.material);
  // машина у тротуара
  const car = new T.Group(); car.position.set(-4.6, 0, 13); car.rotation.y = PI / 2; S.add(car);
  box(1.7, .7, 4.2, mat(0x2a3440), 0, .55, 0, car); box(1.5, .55, 2.1, mat(0x1b222a), 0, 1.15, -.2, car); box(1.52, .45, 2.0, mat(0x0a0d10, null, { emissive: 0x05080c }), 0, 1.15, -.2, car);
  for (const [x, z] of [[-.8, 1.3], [.8, 1.3], [-.8, -1.3], [.8, -1.3]]) { const wh = cyl(.32, .32, .2, 8, mat(0x0d0d0d), car); wh.rotation.z = PI / 2; wh.position.set(x, .32, z); }
  box(.3, .1, .02, mat(0xff2a2a, null, { basic: true }), -.55, .7, -2.11, car); box(.3, .1, .02, mat(0xff2a2a, null, { basic: true }), .55, .7, -2.11, car);
  // наше здание (над холлом) и дома напротив
  const fac = plane(30, 36, mat(0xffffff, TX.facade, { emissive: 0x050608 }), S); fac.position.set(0, 4.2 + 18, 6.01);
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
    o.badgeCard.material = mat(0xffffff, P.badge === 'scratched' ? TX.badgeX : TX.badge);
    const [h, m] = String(P.clock || (o.dawn ? '06:00' : '02:57')).split(':').map(Number); o.setClock(h, m || 0);
  };
  o.reset = () => {
    o.faceL.intensity = 0; o.rain.material.opacity = .38; o.boothPane.visible = false;
    for (const l of o.lifts) setLift(o, l, 0, '1');
    o.cabLight.intensity = 0; relabel(o.cabInd, '1');
    o.lamp.intensity = 1.6; o.lampBulb.visible = true; o.boothLight.intensity = .6; o.l1.intensity = 1.9; o.amb.color.set(0x6a7a90); o.amb.intensity = .34; o.hemi.intensity = .1; o.l3.intensity = 1.3; o.l4.intensity = 1.25;
    o.canopy.intensity = 1.5; o.canopyLamp.visible = true;
    o.sky.material.map = TX.sky; S.fog.color.set(FOGC); S.fog.density = o.fogD; S.background.set(FOGC); o.street.intensity = 1.2; o.rain.visible = true;
    o.dawn = 0; o.tubeOK = 1; o.flickOn = true;
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
const GLASS_FS = `uniform sampler2D tRef, tGrime; uniform float uRef, uLit, uT, uFogD, uPrint, uRain; uniform vec2 uPrintP; uniform vec3 uDropC;
  varying vec3 vW; varying vec4 vR; varying float vD;
  float h1(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float cap(vec2 q, vec2 a, vec2 b, float r){ vec2 pa = q - a, ba = b - a; float k = clamp(dot(pa, ba) / dot(ba, ba), 0., 1.); return length(pa - ba * k) - r; }
  float palmD(vec2 q){   // отпечаток левой ладони (смотрим изнутри: большой палец справа по x)
    float d = (length(q / vec2(.043, .052)) - 1.) * .043;
    d = min(d, cap(q, vec2(-.03, .035), vec2(-.04, .098), .0085)); d = min(d, cap(q, vec2(-.01, .045), vec2(-.012, .12), .009));
    d = min(d, cap(q, vec2(.011, .045), vec2(.015, .124), .009)); d = min(d, cap(q, vec2(.03, .036), vec2(.042, .1), .008));
    return min(d, cap(q, vec2(.04, -.01), vec2(.076, .034), .0105));
  }
  float dots(vec2 p, float y0, float y1, float fw){
    vec2 q = fract(p / .04) - .5; float a = 1. - smoothstep(.25 - fw, .25 + fw, length(q));
    return step(y0, p.y) * step(p.y, y1) * mix(a, .2, clamp(fw * 1.6 - .2, 0., 1.));
  }
  void main(){
    vec2 p = vW.xy; float fw = (fwidth(p.x) + fwidth(p.y)) / .04;
    vec3 V = normalize(cameraPosition - vW);
    float fr = .14 + .56 * pow(1. - abs(V.z), 3.);
    vec3 ref = texture2DProj(tRef, vR).rgb * fr * uRef; float rl = dot(ref, vec3(.3, .55, .15));
    vec2 gu = vec2((p.x + 2.) / 4., (p.y - .4) / 2.);
    float sm = (gu.x > 0. && gu.x < 1. && gu.y > 0. && gu.y < 1.) ? texture2D(tGrime, gu).a : 0.;
    vec3 col = ref * (1. + sm * 3.) + vec3(.62, .66, .7) * sm * (.015 + .06 * uLit + rl * 1.6);
    float a = .05 + sm * .06;
    float dv = max(dots(p, .9, .99, fw), dots(p, 1.25, 1.32, fw)) * step(abs(p.x), 7.9);
    vec3 vin = vec3(.6, .64, .66) * (.05 + .32 * uLit) + ref * .5;
    col = col * (1. - dv * .8) + vin * dv * .8; a += dv * .8 * (1. - a);
    if (uRain > 0.) {
      vec2 cg = p / .013, ci = floor(cg), cf = fract(cg); float fd = fwidth(cg.x) + fwidth(cg.y);
      float r = .07 + .17 * h1(ci + 1.3); vec2 c0 = vec2(h1(ci + 3.1), h1(ci + 7.7)) * .5 + .25; float dd = length(cf - c0);
      float drop = step(h1(ci), .38) * (1. - smoothstep(r - fd * .5, r + fd * .5, dd));
      float hl = drop * smoothstep(r * .55, 0., length(cf - c0 - vec2(0., -r * .5)));
      float cx = floor(p.x / .05), cr = h1(vec2(cx, 9.1)), on = step(cr, .62);
      float sp = .04 + .1 * cr, len = .08 + .3 * h1(vec2(cx, 4.4)), hy = 3.6 - mod(uT * sp + cr * 17.3, 4.4);
      float xc = (cx + .25 + .5 * h1(vec2(cx, 2.))) * .05 + sin(p.y * 11. + cr * 6.) * .003;
      float dx = abs(p.x - xc), up = p.y - hy, wd = .0022 + fwidth(p.x) * .7;
      float trail = on * step(0., up) * step(up, len) * (1. - up / len) * (1. - smoothstep(wd, wd * 1.8, dx));
      float head = on * (1. - smoothstep(.004, .004 + fwidth(p.x) * 1.5, length(vec2(dx, up * .8))));
      drop *= 1. - on * step(0., up) * step(up, len + .02) * step(dx, .012);
      float rain = drop * (.12 + hl * .9) + trail * .32 + head * .7;
      rain = mix(rain, .06, clamp(fd * 1.3 - .4, 0., 1.));
      if (uPrint > 0.) { float inP = (1. - smoothstep(-.002, .003, palmD(p - uPrintP))) * uPrint; rain *= 1. - inP; col += vec3(.55, .57, .55) * inP * (.09 + .18 * uLit); a += inP * .12; }
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
  o.glass = new T.Mesh(new T.PlaneGeometry(16, 3.36), gm); o.glass.position.set(0, 1.78, 6); S.add(o.glass);
  // пар от дыхания Тёмыча на стекле изнутри (её сторона остаётся чистой — улика)
  o.breath = plane(.2, .1, new T.MeshBasicMaterial({ map: TX.fogBreath, transparent: true, opacity: 0, depthWrite: false, color: 0xc8d0d8 }), S);
  o.breath.rotation.y = PI; o.breath.position.set(.6, 1.4, 5.992); o.breath.visible = false;
  // плёнка: «ГОРИЗОНТ» на фрамуге (снаружи читается, изнутри зеркально) и «ОТ СЕБЯ» на створках (изнутри)
  const lg = plane(1.9, .36, new T.MeshLambertMaterial({ map: TX.logo, transparent: true, depthWrite: false, side: T.DoubleSide, emissive: 0x15181b }), S); lg.position.set(0, 2.72, 6.004); ps1(lg.material);
  for (const x of [-.55, .55]) { const st = plane(.13, .065, mat(0xffffff, TX.sticker, { transparent: true }), S); st.rotation.y = PI; st.position.set(x, 1.22, 5.995); }
  // рамы: толстый тёмный профиль, двери — две створки между x = −1 и 1
  const frM = mat(0x24272b), stM = mat(0x9aa0a6), kickM = mat(0x5a5f64);
  for (let x = -8; x <= 8.01; x += 2) if (Math.abs(x) > .5) box(.1, 3.5, .16, frM, x, 1.75, 6, S);
  box(16, .14, .18, frM, 0, 3.47, 6, S); box(16, .12, .18, frM, 0, .06, 6, S);
  box(16, .7, .3, mat(0xffffff, TX.wall), 0, 3.85, 6, S);
  for (const x of [-1.04, 1.04]) box(.09, 3.5, .18, frM, x, 1.75, 6, S);          // коробка дверей
  box(2.1, .1, .18, frM, 0, 2.4, 6, S);                                            // фрамуга над дверьми
  box(2, .015, .3, kickM, 0, .008, 6, S);                                          // порог
  for (const s of [-1, 1]) {
    const cx = s * .5;
    box(.07, 2.3, .08, frM, s * .04, 1.2, 6, S); box(.07, 2.3, .08, frM, s * .96, 1.2, 6, S);   // стойки створки
    box(.92, .07, .08, frM, cx, 2.32, 6, S); box(.92, .22, .085, kickM, cx, .17, 6, S);          // верх, нижняя филёнка
    // изнутри — горизонтальная штанга «от себя», снаружи — вертикальная скоба
    box(.62, .032, .032, stM, cx, 1.02, 5.89, S); for (const d of [-.29, .29]) box(.028, .028, .085, stM, cx + d, 1.02, 5.935, S);
    box(.032, .5, .032, stM, s * .14, 1.12, 6.11, S); for (const d of [-.22, .22]) box(.028, .028, .085, stM, s * .14, 1.12 + d, 6.065, S);
    box(.3, .06, .07, mat(0x3a3d40), cx, 2.24, 5.9, S);                                          // доводчик
  }
}
function resetFacade(o) {
  const U = o.glassU; U.uRef.value = 1.7; U.uPrint.value = 0; U.uRain.value = 1; o.breath.visible = false; o.glass.visible = true; o.reflBoost = 1;
}

/* ---------- 7 этаж: длинный коридор, двери, лампы, кабинет 713 ---------- */
function buildCorridor() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .05; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x4a5868, .36); S.add(o.amb);
  const len = 26, wd = 2.6;
  // пол — офисный линолеум с лёгким бликом (по нему ходит швабра уборщика)
  const flM = ps1(new T.MeshPhongMaterial({ color: 0xffffff, map: TX.lino, specular: 0x1e1e1e, shininess: 30 }));
  const fl = plane(wd, len, flM, S); fl.rotation.x = -PI / 2; fl.position.z = -len / 2;
  const ce = plane(wd, len, mat(0xffffff, TX.ceil), S); ce.rotation.x = PI / 2; ce.position.set(0, 3, -len / 2);
  const wm = mat(0xffffff, TX.wall), pm = mat(0xffffff, TX.panel);
  // стены; в левой — проём двери 707 (она приоткрыта, за ней темнота)
  const D7 = o.D7 = -11.8, dw = .95, dh = 2.1, zA = D7 + dw / 2, zB = D7 - dw / 2;
  const wall = (s, z0, z1, y0, y1, m) => { const w = plane(z0 - z1, y1 - y0, m, S); w.rotation.y = -s * PI / 2; w.position.set(s * (wd / 2 - (m === pm ? .005 : 0)), (y0 + y1) / 2, (z0 + z1) / 2); return w; };
  wall(1, 0, -len, 0, 3, wm); wall(1, 0, -len, 0, .9, pm);
  wall(-1, 0, zA, 0, 3, wm); wall(-1, zB, -len, 0, 3, wm); wall(-1, zA, zB, dh, 3, wm);
  wall(-1, 0, zA, 0, .9, pm); wall(-1, zB, -len, 0, .9, pm);
  const end = plane(wd, 3, wm, S); end.position.set(0, 1.5, -len);
  const st = plane(wd, 3, wm, S); st.rotation.y = PI; st.position.set(0, 1.5, 0);
  // комната за дверью 707: почти чёрная
  const rmM = mat(0x0c0c0d); const rm = room(3, 3, 3, rmM, S); rm.position.set(-wd / 2 - 1.5, 1.5, D7);
  const trM = mat(0x3a2a20);
  box(.03, dh + .07, .07, trM, -wd / 2 + .015, (dh + .07) / 2, zA + .035, S); box(.03, dh + .07, .07, trM, -wd / 2 + .015, (dh + .07) / 2, zB - .035, S); box(.03, .07, dw + .14, trM, -wd / 2 + .015, dh + .035, D7, S);
  box(.14, dh, .04, trM, -wd / 2 - .07, dh / 2, zA - .02, S);   // торец коробки, за который хватаются пальцы
  o.door7 = new T.Group(); o.door7.position.set(-wd / 2 - .02, 0, zB); S.add(o.door7);
  box(.05, dh - .02, dw - .03, mat(0xffffff, TX.door), -.025, (dh - .02) / 2, (dw - .03) / 2, o.door7);
  // двери по бокам с табличками
  let n = 701;
  for (let z = -2.5; z > -len + 2; z -= 3.1) for (const s of [-1, 1]) {
    if (!(s === -1 && Math.abs(z - D7) < .01)) box(.06, 2.1, .95, mat(0xffffff, TX.door), s * (wd / 2 - .03), 1.05, z, S);
    const num = n++; const pl = label(.2, .08, 32, 12, (c, w, h) => { c.fillStyle = '#c9b27a'; c.fillRect(0, 0, w, h); c.fillStyle = '#1b1b1b'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(num), w / 2, h / 2 + 1); }, S);
    pl.position.set(s * (wd / 2 - .065), 1.75, z - .62); pl.rotation.y = -s * PI / 2;
  }
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
  o.srv = label(.5, .12, 96, 22, (c, w, h) => { c.fillStyle = '#7a7d80'; c.fillRect(0, 0, w, h); c.fillStyle = '#111'; c.font = '700 13px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('СЕРВЕРНАЯ  −2', w / 2, h / 2 + 1); }, S); o.srv.position.set(0, 1.78, -len + .09);
  // лужа под телом (сцена «Жилец ест»)
  // лужа неровная: край — сумма синусов, плюс отдельные брызги (круг выглядел как красный коврик)
  const bl = new T.Shape(); for (let i = 0; i <= 48; i++) { const a = i / 48 * PI * 2, r = .82 * (1 + .22 * Math.sin(3 * a + 1) + .13 * Math.sin(7 * a + 2.3) + .07 * Math.sin(13 * a + .4));
    i ? bl.lineTo(Math.cos(a) * r, Math.sin(a) * r) : bl.moveTo(Math.cos(a) * r, Math.sin(a) * r); }
  const bg = [new T.ShapeGeometry(bl)]; for (const [x, y, r] of [[1.05, .35, .09], [-.95, -.5, .06], [.4, -1.02, .07], [1.25, -.2, .045], [-.3, .98, .05]]) bg.push(new T.CircleGeometry(r, 7).translate(x, y, 0));
  o.blood = new T.Mesh(T.BufferGeometryUtils ? T.BufferGeometryUtils.mergeGeometries(bg) : bg[0], new T.MeshLambertMaterial({ color: 0x2a0303, polygonOffset: true, polygonOffsetFactor: -4 }));
  if (!T.BufferGeometryUtils) for (const g2 of bg.slice(1)) o.blood.add(new T.Mesh(g2, o.blood.material)); o.blood.rotation.x = -PI / 2; o.blood.scale.set(1, 1.6, 1); o.blood.position.set(0, .006, -12); S.add(o.blood);
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  // по умолчанию: уборщика ещё не было — ни ведра, ни следов; дверь 707 приоткрыта
  o.cleanSet = (c = {}) => {
    o.bucket.visible = !!c.bucket; if (c.bucket) { const [x, z, ry, tip] = c.bucket; o.bucket.position.set(x, 0, z); o.bucket.rotation.set(0, ry, 0); o.bucket.userData.tip(tip || 0); }
    o.mopProp.visible = !!c.mop; if (c.mop) { const [x, z, ry, k] = c.mop; o.mopProp.position.set(x, 0, z); o.mopProp.rotation.set(0, ry, 0); o.mopProp.userData.fall(k ?? 1); }
    o.sign.visible = !!c.sign; if (c.sign) { o.sign.position.set(c.sign[0], 0, c.sign[1]); o.sign.rotation.set(0, c.sign[2] || 0, c.sign[3] || 0); if (c.sign[3]) o.sign.position.y = .1; }
    o.wet.visible = !!c.wet; if (c.wet) { o.wet.position.set(c.wet[0], .004, c.wet[1]); const lm = o.lamps; o.sheen.material.opacity = .55 * clamp((lm[2].li.intensity + lm[3].li.intensity) / 4.8, 0, 1); }
    o.spill.visible = !!c.spill; if (c.spill) o.spill.position.set(c.spill[0], .005, c.spill[1]);
    o.smear.visible = !!c.smear; if (c.smear) { const [x0, z0, x1, z1] = c.smear, L = Math.hypot(x1 - x0, z1 - z0); o.smear.position.set((x0 + x1) / 2, .007, (z0 + z1) / 2); o.smear.scale.set(1, L / 2.4, 1); o.smear.rotation.y = Math.atan2(x0 - x1, z0 - z1); }
    o.door7.rotation.y = -(c.door7 ?? .42);
    o.setFingers(c.fingers || 0);
  };
  o.reset = () => { o.faceL.intensity = 0; o.blood.visible = false; o.blood.position.set(0, .006, -12); o.blood.scale.set(1, 1.6, 1); o.lamps[3].li.distance = 7; setLamps(o, 0); o.slit.visible = true; o.slitLight.intensity = 1.2; o.slitLight.color.setHex(0xffe0a0); o.amb.intensity = .36; o.srv.visible = false; S.fog.density = o.fogD; o.flickOn = true; o.tubeOK = 1; o.cleanSet(); };
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
  o.salt = plane(2.2, 2.2, new T.MeshBasicMaterial({ map: TX.salt, transparent: true, depthWrite: false }), S); o.salt.rotation.x = -PI / 2; o.salt.position.y = .012; ps1(o.salt.material);
  for (let i = 0; i < 5; i++) { const c = cyl(.02, .025, .12, 6, mat(0xe8e0c8, null, { emissive: 0x302010 }), S); const a = i / 5 * PI * 2; c.position.set(Math.cos(a) * 1.25, .06, Math.sin(a) * 1.25); }   // свечи
  o.candle = new T.PointLight(0xffb060, 1.2, 5, 1.6); o.candle.position.set(0, .5, .4); S.add(o.candle);
  o.red = new T.PointLight(0xff2020, .9, 9, 1.5); o.red.position.set(0, 2.8, 2.6); S.add(o.red);
  box(.25, .12, .08, mat(0xff2020, null, { basic: true }), 0, 2.92, 3.4, S);
  o.door = box(1, 2.1, .06, mat(0xffffff, TX.door), 2, 1.05, 3.47, S);
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

/* ---------- кадры ---------- */
// act: [актёр, x, y, z, поворот, поза, куда смотрит ('cam' | [x,y,z] | null)]
const lin = (a, b, k) => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
// свет для сцены «Жилец ест»: горит одна лампа прямо над ними и подмигивает
function eatLight(o, t) { setLamps(o, 0); o.flickOn = false; const l = o.lamps[3], on = Math.sin(t * 9) > .85 && Math.sin(t * 1.7) > 0 ? .25 : 1; l.li.intensity = 4.2 * on; l.li.distance = 9; o.amb.intensity = .42; l.tube.material.color.setHex(on > .5 ? 0xe8f0e8 : 0x30322e); o.blood.visible = true; o.blood.position.set(-.75, .006, -11.8); o.blood.scale.set(1.25, 1, 1); o.slitLight.intensity = 0; o.slit.visible = false; A.monster.gore = 1; A.victim.gore = 2;
  o.cleanSet(AFTER); }
// седьмой этаж после уборщика: швабра на полу, ведро опрокинуто, вода, кровавый след волоком к приоткрытой 707
const CLEAN = { bucket: [.6, -10.35, .3], sign: [-.85, -7.6, .5], wet: [-.1, -9.9], door7: .42 }, MOPLEAN = [.43, -10.08, .25, 0];
const AFTER = { mop: [.43, -10.08, .25, 1], bucket: [.6, -10.35, .3, 1], spill: [.82, -10.3], sign: [-.85, -7.6, .5], smear: [.0, -9.6, -1.05, -11.7], door7: .55 };
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
  const C = A.monster; C.faceSet = 'scream'; C.scream = clamp((t - at + .05) / .16, 0, 1);
  o.faceL.position.set(P[0] + ux * .12, P[1] - .22, P[2] + uz * .12); o.faceL.distance = Math.max(2.2, d + 1.2);
  o.faceL.intensity = t < at ? (cf.glow || 0) : (Math.sin(t * 57) > -.35 ? 3.4 : .9);
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
  hall_dawn: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o) => { setDawn(o, 1); return [['smenshik', 3.2, 0, 2.8, -2.3, 'walk', null]]; } },
  exterior: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o, t) => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]]] },
  exterior_dawn: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o) => { setDawn(o, 1); return []; } },
  exterior_two: { loc: 'lobby', pos: [2.2, 1.5, 15], look: [0, 2.2, 6], fov: 55, set: (o) => { setDawn(o, 1); return [['temych', -.55, 0, 8.3, 1.05, 'stand', [.5, 1.62, 8.2]], ['mihalych', .55, 0, 8.2, -.75, 'stand', [-.55, 1.7, 8.3]]]; } },   // вышли под навес: стоят друг к другу вполоборота
  guard: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: () => [['smenshik', 5.12, 0, .85, PI, 'stand', 'cam']] },
  guard_keys: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.4, .9], fov: 48, set: (o) => { o.keys.visible = false; return [['smenshik', 5.12, 0, .8, PI, 'give', 'cam']]; } },
  guard_dawn: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: (o) => { setDawn(o, 1); A.smenshik.faceSet = 'smile'; return [['smenshik', 5.12, 0, .85, PI, 'stand', 'cam']]; } },
  desk_note: { loc: 'lobby', pos: [5.3, 1.3, -.62], look: [5.45, .78, -.12], fov: 55, set: () => [] },
  note: { loc: 'lobby', pos: [5.36, 1.13, -.44], look: [5.35, .8, -.17], fov: 44, set: () => [] },
  monitors: { loc: 'lobby', pos: [5.55, 1.42, -1.15], look: [4.5, 1.18, .12], fov: 48, feeds: true, set: () => [] },
  booth_dark: { loc: 'lobby', pos: [5.55, 1.42, -1.15], look: [4.5, 1.18, .12], fov: 54, feeds: true, set: (o) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .15; o.l3.intensity = .1; o.amb.intensity = .25; return []; } },
  elevator_open: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 1.25, -6], fov: 46, set: (o) => { setLift(o, o.lifts[0], 1, '1'); o.cabLight.intensity = 2.4; return []; } },
  elevator_floors: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 1.7, -6], fov: 46, set: (o, t) => { const f = Math.min(7, 1 + Math.floor(t / .6)); setLift(o, o.lifts[0], 0, String(f), f < 7); return []; } },
  elevator_inside: { loc: 'lobby', pos: [-2.2, 1.5, -7.5], look: [-2.2, 1.75, -5.8], fov: 66, set: (o, t) => { o.cabLight.intensity = (Math.sin(t * 23) > -.6) ? 1.6 : .3; relabel(o.cabInd, '13'); return []; } },
  door_katya: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 6.5], fov: 50, set: () => atGlass('katya', 'knock', 'cam') },
  door_back: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 7], fov: 50, set: (o, t) => { o.glassU.uPrint.value = clamp((t - .9) / .6, 0, 1);   // на стекле остаётся сухой отпечаток её ладони
    return [['katyaSmile', .62, 0, 6.3 + Math.max(0, t - .6) * .55, PI, t > .6 ? 'back' : 'stand', 'cam']]; } },
  entrance_cam: { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [.5, .8, 6.2], fov: 64, cctv: 1, set: (o) => { o.rain.material.opacity = .18; o.reflBoost = .6; return atGlass('katya', 'knock', [.6, 1.5, 4]); } },
  katya_face: { loc: 'lobby', pos: [.6, 1.49, 5.4], look: [.62, 1.47, 6.5], fov: 34, set: (o, t) => { breath(o, t); return atGlass('katya', 'knock', 'cam'); } },
  katya_smile: { loc: 'lobby', pos: [.6, 1.49, 5.4], look: [.62, 1.47, 6.5], fov: 34, set: (o, t) => { breath(o, t); return [['katyaSmile', .62, 0, 6.3, PI, 'stand', 'cam']]; } },
  katya_inside: { loc: 'lobby', pos: [.5, 1.6, 3.7], look: [.2, 1.42, 4.75], fov: 44, set: () => [['katya', .2, 0, 4.75, PI + .15, 'reach', 'cam']] },
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
  door713: { loc: 'corridor', pos: [.15, 1.55, -23.7], look: [0, 1.0, -26], fov: 52, set: (o) => { setLamps(o, 1); return []; } },
  corridor_katya: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 40, set: (o, t) => { setLamps(o, 7); return [['katya', 0, 0, -19, lerp(PI * .98, PI * .02, clamp((t - 1) / 2, 0, 1)) + PI, 'stand', null]]; } },
  corridor_face: { loc: 'corridor', pos: [.05, 1.52, -18.75], look: [0, 1.5, -20], fov: 34, set: (o) => { setLamps(o, 7); return [['katyaSmile', 0, 0, -19.4, 0, 'stand', 'cam']]; } },
  cam13_monster: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 66, set: (o) => { o.salt.visible = false; return [['monster', .1, 0, -.5, -.6, 'monster', 'cam']]; } },
  monster_close: { loc: 'server', pos: [-.05, 2.2, .95], look: [-.13, 2.16, 0], fov: 40, set: (o, t) => { A.monster.faceSet = 'grin'; o.amb.intensity = .08; o.cold.intensity = .15; o.cold2.intensity = 0; o.rim.intensity = 1.8; o.red.intensity = .5; o.candle.intensity = 2.2 * (Math.sin(t * 23) > -.6 ? 1 : .3); return [['monster', 0, 0, 0, 0, 'monster', 'cam']]; } },   // свет снизу от свечи и контур — лицо выходит из темноты
  server_mih: { loc: 'server', pos: [.7, 1.45, 2.7], look: [0, .65, 0], fov: 52, set: () => [['mihalych', 0, 0, 0, 0, 'sitfloor', 'cam']] },
  server_mih_close: { loc: 'server', pos: [.25, .95, 1.15], look: [0, .9, 0], fov: 44, set: () => [['mihalych', 0, 0, 0, 0, 'sitfloor', 'cam']] },
  server_mih_stand: { loc: 'server', pos: [.7, 1.6, 2.7], look: [0, 1.4, 0], fov: 50, set: () => [['mihalych', 0, 0, 0, 0, 'stand', 'cam']] },
  server_door: { loc: 'server', pos: [2.4, 1.6, 2.6], look: [-1, 1.2, -1], fov: 55, set: () => [['mihalych', 0, 0, 0, .3, 'sitfloor', [2.4, 1.6, 2.6]]] },
  cam13_mih: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 60, set: (o) => [['mihalych', 0, 0, 0, -.6, 'paper', 'cam']] },
  cam13_mih_monster: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.3, -.6], fov: 60, set: (o, t) => [['mihalych', 0, 0, 0, -.6, 'paper', 'cam']].concat(t > 3.35 ? [['monster', .25, 0, -1.25, -.6, 'monster', 'cam']] : []) },
  smenshik_leave: { loc: 'lobby', pos: [6.3, 1.72, -.75], look: [1.5, 1.15, 5.4], fov: 52, set: (o, t) => { const k = clamp(t / 3, 0, 1), p = lin([4.3, 0, 1.5], [.5, 0, 5.55], k); return [['smenshik', p[0], 0, p[2], Math.atan2(.5 - 4.3, 5.55 - 1.5), k < 1 ? 'walk' : 'reach', null]]; } },
  entrance_empty: { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [.5, .8, 6.2], fov: 64, set: (o) => { o.rain.material.opacity = .18; return []; } },
  // один раз за ночь: пока она улыбается за стеклом, в отражении за спиной Тёмыча стоит Жилец — полсекунды, без звука
  door_smile: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 6.5], fov: 50, set: (o, t) => { o.reflBoost = 1.5; const m = t > 3.1 && t < 3.65; if (m) { o.faceL.position.set(-.2, 2.3, 2.6); o.faceL.distance = 2.6; o.faceL.intensity = 1.6; }
    return atGlass('katyaSmile', 'knock', 'cam').concat(m ? [['monster', -.35, 0, 1.55, .05, 'monster', [2.2, 1.6, 9.2], 'refl']] : []); } },
  corridor_monster: { loc: 'corridor', pos: [0, 2.0, -19.1], look: [0, 1.9, -21.5], fov: 48, set: (o) => { lampSet(o, i => i === 5); o.flickOn = false; return [['monster', 0, 0, -21.5, 0, 'reach', 'cam']]; } },
  monster_booth: { loc: 'lobby', pos: [5.3, 1.15, -.6], look: [5.1, 1.85, .8], fov: 52, set: (o) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .3; return [['monster', 5.1, 0, .95, PI, 'reach', 'cam']]; } },
  hall_cam_katya: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['katyaSmile', -.6, 0, -1.8, .7, 'stand', 'cam']] },
  elevator_13: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 2.35, -6], fov: 46, set: (o, t) => { setLift(o, o.lifts[0], 1, '13'); o.cabLight.intensity = (Math.sin(t * 19) > -.7) ? 2.4 : .5; return []; } },
  cam13_turn: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 66, set: (o, t) => { o.salt.visible = false; const k = clamp((t - 2.6) / 1.4, 0, 1), e = k * k * (3 - 2 * k); return [['monster', .1, 0, -.5, 3.0, 'monster', lin([.5, 1.9, -3.5], [3.6, 2.7, 3.1], e)]]; } },
  guard_dawn_fear: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: (o, t) => { setDawn(o, 1); A.smenshik.faceSet = 'fear'; return [['smenshik', 5.12, 0, .85 + Math.max(0, t - .8) * .22, PI, t > .8 ? 'back' : 'stand', 'cam']]; } },
  // «Я глянул в стекло будки. Я улыбался»: смотрим в стекло перегородки — в нём отражение Тёмыча (грязь, блик, рама)
  booth_reflect: { loc: 'lobby', pos: [5.3, 1.6, -.55], look: [5.12, 1.6, .9], fov: 38, fog: .16, set: (o) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false; o.amb.intensity = .8; o.boothPane.visible = true; A.temych.faceSet = 'smile'; return [['temych', 5.12, 0, .9, PI, 'stand', 'cam']]; } },
  exterior_alone: { loc: 'lobby', pos: [3.2, 1.4, 17.8], look: [2.0, 2.2, 13], fov: 54, set: () => [['temych', 2.3, 0, 15.6, .25, 'stand', 'cam']] },
  basement_door: { loc: 'corridor', pos: [.15, 1.55, -23.5], look: [0, 1.05, -26], fov: 54, set: (o, t) => { setLamps(o, (Math.sin(t * 11) > .55 && Math.sin(t * 2.3) > -.2) ? 1 : 0); o.srv.visible = true; o.slitLight.color.setHex(0xffb060); o.slitLight.intensity = 1.6 + Math.sin(t * 7) * .3; o.amb.intensity = .35; return []; } },
  // тело там, куда его утащили: голова на пороге 707, ноги в коридоре; Жилец сидит над грудью с дальней стороны — лицом к камере
  cam7_eat: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.35, .6, -11.8], fov: 30, cctv: 1, set: (o, t) => { eatLight(o, t); return [['victim', -.4, 0, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 4 ? [-.75, .2, -11.75] : [1.0, 2.75, -.5]]]; } },
  eat_close: { loc: 'corridor', pos: [.62, 1.12, -9.4], look: [-.62, .62, -12.1], fov: 54, set: (o, t) => { eatLight(o, t); return [['victim', -.4, 0, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 2.2 ? [-.75, .2, -11.75] : [.62, 1.12, -9.4]]]; } },
  // уборщик (03:00, седьмой этаж): сначала обычная работа — потом его забирают в темноту за приоткрытой 707
  cleaner_cam: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { setLamps(o, 7);
    o.cleanSet(CLEAN); return [['cleaner', -.1, 0, -9.45, .2, 'mop', null]]; } },
  cleaner_close: { loc: 'corridor', pos: [.82, 1.45, -6.75], look: [-.5, 1.05, -10.9], fov: 50, hum: 'cleaner', set: (o, t) => { setLamps(o, 7);
    o.cleanSet(CLEAN); return [['cleaner', -.1, 0, -9.45, .33, 'mop', [.8, 1.2, -6.8]]]; } },
  // остановился, швабру прислонил к ведру; лампы дальше по коридору захлёбываются и гаснут; из темноты 707 показываются пальцы
  cleaner_stop: { loc: 'corridor', pos: [1.15, 1.5, -6.1], look: [-.25, 1.42, -10.9], fov: 44, set: (o, t) => {
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
    const got = t > 1.0, gone = t > 1.97;
    o.cleanSet({ bucket: [.6, -10.35, .3, clamp((t - 1.05) / .25, 0, 1)], spill: got ? AFTER.spill : null, sign: CLEAN.sign, wet: CLEAN.wet,
      door7: gone ? .55 : t > .9 ? .95 : .42, mop: [MOPLEAN[0], MOPLEAN[1], MOPLEAN[2], clamp((t - 1.08) / .4, 0, 1)], smear: gone ? AFTER.smear : null, fingers: t < .7 ? 1 : 0 });
    if (!got) return [['cleaner', .3, 0, -9.25, 2.75, 'stand', [-1.3, 1.6, -11.4]]];
    // его тащат: за три кадра вспышки он отъезжает к двери, ноги почти не касаются пола
    if (fA) { const k = clamp((t - 1.0) / .3, 0, 1); return [['cleaner', lerp(-.45, -.78, k), .03 * k, lerp(-10.45, -10.88, k), .55, 'reach', [1.0, 2.75, -.5]], ['monster', lerp(-1.0, -1.22, k), 0, lerp(-11.25, -11.55, k), .5, 'reach', [-.45, 1.4, -10.45]]]; }
    if (t < 1.97) { const k = clamp((t - 1.3) / .67, 0, 1); return [['cleaner', lerp(-1.1, -1.62, k), 0, lerp(-11.2, -11.78, k), .95, 'reach', [1.0, 2.75, -.5]]]; }
    return [];
  } },
  cleaner_gone: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => {
    setLamps(o, 7); o.flickOn = false; const l = o.lamps[3], f = Math.sin(t * 7.3) * Math.sin(t * 2.2) > -.6 ? 1 : 0; l.li.intensity = 2.4 * f; l.tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c);
    o.cleanSet(Object.assign({ wet: [-.1, -9.9] }, AFTER)); return []; } },
  cover_door: { loc: 'lobby', pos: [.95, 1.55, 3.7], look: [.5, 1.6, 6.5], fov: 40, set: () => [['katya', .62, 0, 6.32, PI + .1, 'stand', 'cam'], ['monster', -.25, 0, 8.4, PI - .15, 'monster', 'cam']] },
  lunge_server: { loc: 'server', pos: [.35, 1.55, 2.7], look: [0, 1.45, -3], fov: 58, set: (o, t, sh) => { o.salt.visible = false; o.amb.intensity = .12; o.cold.intensity = .3; o.cold2.intensity = .1; o.rim.intensity = 0; o.red.intensity = .35; o.candle.intensity = t < 1.4 ? .5 : 0; return lunge(o, t, sh, { at: 1.4, far: 3.6, glow: .35, show: .5 }); } },
  lunge_booth: { loc: 'lobby', pos: [5.3, 1.2, -.6], look: [5.1, 1.45, .9], fov: 56, set: (o, t, sh) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .2; o.l3.intensity = .1; o.amb.intensity = .2; o.boothLight.intensity = .15; return lunge(o, t, sh, { at: 1.3, far: 2.2, glow: .3, show: .45, blink: true }); } },
  lunge_corridor: { loc: 'corridor', pos: [0, 1.6, -3], look: [0, 1.5, -26], fov: 56, set: (o, t, sh) => { setLamps(o, (t % .5) < .1 && t < 1.35 ? 0 : 7); o.amb.intensity = .35; return lunge(o, t, sh, { at: 1.35, far: 15, steps: [[.6, 10], [.85, 6.5], [1.1, 3.5]], glow: .2, show: .35, blink: true }); } },
  lunge_back: { loc: 'corridor', pos: [0, 1.6, -15], look: [0, 1.5, 0], fov: 58, set: (o, t, sh) => { setLamps(o, t < 1.2 ? 2 : 1, true); o.slit.visible = false; o.slitLight.intensity = 0; return lunge(o, t, sh, { at: 1.2, far: 6, steps: [[.7, 3.2]], glow: .25, show: .4, blink: true }); } },
  lunge_lift: { loc: 'lobby', pos: [-2.2, 1.5, -7.5], look: [-2.2, 1.6, -5.8], fov: 66, set: (o, t, sh) => { relabel(o.cabInd, '13'); o.cabLight.intensity = t < 1.4 ? ((t % .7) < .08 && t > .5 ? 1.2 : 0) : 0; return lunge(o, t, sh, { at: 1.4, far: 1.15, glow: .15, show: .55, blink: true, near: .5 }); } },
  cam7_jump: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t, sh) => { setLamps(o, 0); o.lamps[0].li.intensity = t > .1 ? 1.8 : 0; return lunge(o, t, sh, { at: .08, far: 4.5, near: .5, dur: .2, glow: 0 }); } },
  corridor_cam_dark: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t) => { setLamps(o, 0); const f = Math.sin(t * 13) > .2 || Math.sin(t * 3.1) > .6 ? 1 : 0; o.lamps[6].li.intensity = 1.6 * f; o.lamps[6].tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c); o.amb.intensity = .66; return []; } },
  katya_slam: { loc: 'lobby', pos: [.6, 1.48, 4.95], look: [.62, 1.42, 6.5], fov: 46, set: (o, t) => t < .25 ? [] : atGlass('katya', 'slam', 'cam', 6.24) },
  run_lit: { loc: 'corridor', pos: [0, 1.55, -2], look: [0, 1.4, -26], fov: 62, set: (o, t, sh) => { setLamps(o, 7); sh.pos[2] = -2 - (t * 2.2) % 12; sh.pos[1] = 1.55 + Math.abs(Math.sin(t * 8)) * .05; sh.look[2] = sh.pos[2] - 24; return []; } },
  run_dark: { loc: 'corridor', pos: [0, 1.5, -3], look: [0, 1.2, -26], fov: 62, set: (o, t) => { setLamps(o, 7 - Math.floor(t * 1.5) % 8, true); return [['mihalych', .3, 0, -5.2 - t * .4, PI, 'walk', null]]; } },
};
// старые имена кадров из первой версии → новые
const ALIAS = { f01: 'hall_cam', f02: 'exterior', f03: 'guard', f04: 'desk_note', f05: 'note', f06: 'monitors', f07: 'corridor_cam', f08: 'elevator_open', f09: 'cam13_monster', f10: 'door_katya', f11: 'entrance_cam', f12: 'katya_face' };

/* ---------- показ ---------- */
let canvas = null, scenesReady = false;
const cam = new T.PerspectiveCamera(50, RW / RH, .05, 120), feedCam = new T.PerspectiveCamera(60, 4 / 3, .05, 80);
let postScene, postCam, postMat, feedRT = [], feedT = 0, reflRT = null, reflN = 0, curProps = null;
const rcam = new T.PerspectiveCamera(50, RW / RH, .05, 120);
const PROPS = { jacket: 1, badge: 1, clock: null };   // «нормы» холла; сюжет может передать o.props = { jacket: 0, badge: 'scratched', clock: '03:00' }
let VHS = .7;
function init() {
  if (scenesReady) return true;
  try {
    canvas = document.createElement('canvas'); canvas.width = RW; canvas.height = RH;
    window.__W3core.R = new T.WebGLRenderer({ canvas, antialias: false, alpha: false, powerPreference: 'high-performance' });
  } catch (e) { return false; }
  const R = window.__W3core.R; R.setPixelRatio(1); R.setSize(RW, RH, false); R.useLegacyLights = true; R.outputColorSpace = T.SRGBColorSpace;
  makeTextures(); A = window.__W3chars.cast();
  L.lobby = buildLobby(); L.corridor = buildCorridor(); L.server = buildServer();
  for (const k in L) L[k].name = k;
  // буфер кадра и постобработка: цветокор + дизеринг + ступени цвета + плёнка VHS (дрожь строк, полоса внизу, редкий залом,
  // цветная кайма в 1 пиксель, строки, лёгкая бочка); у камер наблюдения бочка сильнее и пересвет
  window.__W3core.RT = new T.WebGLRenderTarget(RW, RH, { magFilter: T.NearestFilter, minFilter: T.NearestFilter });
  postMat = new T.ShaderMaterial({
    uniforms: { tD: { value: window.__W3core.RT.texture }, uLv: { value: 22 }, uCctv: { value: 0 }, uGam: { value: 1.25 }, uT: { value: 0 }, uVhs: { value: .7 }, uScare: { value: 0 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }',
    fragmentShader: `uniform sampler2D tD; uniform float uLv, uCctv, uGam, uT, uVhs, uScare; varying vec2 vUv;
      float b2(vec2 a){ a = floor(a); return fract(dot(a, vec2(.5, a.y * .75))); }
      float b4(vec2 a){ return b2(.5 * a) * .25 + b2(a); }
      float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
      void main(){
        vec2 uv = vUv, d = uv - .5;
        uv += d * dot(d, d) * (.03 + .1 * uCctv);                                   // бочка (у камер — «рыбий глаз»)
        float row = floor(uv.y * ${RH}.), fr = floor(uT * 30.);
        uv.x += (h(vec2(row, fr)) - .5) * uVhs * 1.1 / ${RW}.;                       // дрожь строк, до полпикселя
        float band = smoothstep(.986, 1., 1. - uv.y) * uVhs;                        // полоса переключения головок внизу
        uv.x += band * (h(vec2(row, fr * .37)) - .3) * 7. / ${RW}.;
        float ph = mod(uT + 4., 13.), cy = (ph - 12.2) * 1.5;                       // залом ленты: раз в 13 с пробегает вверх
        float cr = (cy > 0. && cy < 1.) ? smoothstep(.02, 0., abs(uv.y - cy)) * uVhs : 0.;
        uv.x -= cr * (2. + 2. * h(vec2(row, fr))) / ${RW}.;
        float ca = (.85 + 2.6 * uScare) / ${RW}.;                                     // цветная кайма: 1 px, на скримере 3–4
        vec3 c = vec3(texture2D(tD, uv + vec2(ca, 0.)).r, texture2D(tD, uv).g, texture2D(tD, uv - vec2(ca, 0.)).b);
        c = mix(c * 12.92, 1.055 * pow(max(c, vec3(.0031308)), vec3(1. / 2.4)) - .055, step(.0031308, c));   // буфер линейный → sRGB
        c = pow(c, vec3(uGam));   // ночь: тени глубже, но без пятен
        float l = dot(c, vec3(.299, .587, .114));
        c = mix(c * vec3(.86, 1.02, 1.08) + vec3(0., .012, .024), c * vec3(1.06, .98, .88), smoothstep(.25, .8, l));   // холодные тени, тёплый свет
        c = mix(c, vec3(l) * vec3(.88, 1., .9), uCctv * .3) + max(c - .62, 0.) * uCctv * .9;                         // камера: блеклая, свет «горит»
        c *= 1. - (.06 + .07 * uCctv) * step(1., mod(gl_FragCoord.y, 2.));           // строки
        c += cr * .22 * h(vec2(gl_FragCoord.x, fr)) + band * .05 * h(gl_FragCoord.xy + fr);
        if (uv.x < 0. || uv.x > 1. || uv.y < 0. || uv.y > 1.) c = vec3(0.);
        c = floor(c * uLv + b4(gl_FragCoord.xy) * .95) / uLv;
        gl_FragColor = vec4(c, 1.);
      }` });
  reflRT = new T.WebGLRenderTarget(RW / 2, RH / 2, { magFilter: T.LinearFilter, minFilter: T.LinearFilter });
  L.lobby.glassU.tRef.value = reflRT.texture; classifyLobby(L.lobby);
  window.__dbgA = A; window.__dbgL = L;
  postScene = new T.Scene(); postCam = new T.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  postScene.add(new T.Mesh(new T.PlaneGeometry(2, 2), postMat));
  for (let i = 0; i < 4; i++) feedRT.push(new T.WebGLRenderTarget(96, 72, { magFilter: T.NearestFilter, minFilter: T.NearestFilter }));
  L.lobby.screens.forEach((s, i) => { s.material.map = feedRT[i].texture; s.material.needsUpdate = true; });
  feedRT[3].texture.colorSpace = T.SRGBColorSpace;
  scenesReady = true; return true;
}
const NAME = { 'ТЁМЫЧ': ['temych'], 'СМЕНЩИК': ['smenshik'], 'КАТЯ': ['katya', 'katyaSmile'], 'КАТЯ?': ['katyaSmile', 'katya'], '???': ['monster'], 'МИХАЛЫЧ': ['mihalych'], 'УБОРЩИК': ['cleaner'] };
const _v = new T.Vector3(), _d = new T.Vector3();
function aim(c, pos, look, fov, cx, cy, z, sx, sy) {
  c.position.set(pos[0], pos[1], pos[2]); c.fov = fov; c.aspect = RW / RH; c.updateProjectionMatrix(); c.lookAt(look[0], look[1], look[2]); c.updateMatrixWorld();
  // точка (cx, cy) кадра → направление; зум сужает угол обзора вокруг неё (как настоящий объектив)
  _v.set((cx - .5) * 2, -(cy - .5) * 2, .5).unproject(c); _d.copy(_v).sub(c.position).normalize();
  c.lookAt(c.position.x + _d.x, c.position.y + _d.y, c.position.z + _d.z);
  c.fov = 2 * Math.atan(Math.tan(fov * PI / 360) / Math.max(.3, z)) * 180 / PI; c.updateProjectionMatrix();
  c.rotateY(-sx * .00012); c.rotateX(-sy * .00012);
}
let lastT = 0, curLoc = null, lastId = null;
function stage(id, t, opt = {}) {
  const sh = SH[ALIAS[id] || id] || SH.hall_cam, o = L[sh.loc];
  curProps = Object.assign({}, PROPS, window.World && World.props, opt.props);
  if (curLoc && curLoc !== o) curLoc.reset();
  curLoc = o; o.reset(); if (o.props) o.props(curProps);
  for (const k in A) { A[k].faceSet = 'base'; A[k].gore = 0; A[k].scream = 0; A[k].reflOnly = false; A[k].root.visible = true; }
  const acts = sh.set(o, t, sh) || [];
  if (sh.fog != null) o.S.fog.density = sh.fog;
  const used = new Set();
  for (const [k, x, y, z, ry, pose, look, only] of acts) {
    const C = A[k]; used.add(k);
    if (C.root.parent !== o.S) o.S.add(C.root);
    if (C.lastShot !== id) { C.lastShot = id; C.lookDir = null; }
    C.root.position.set(x, y, z); C.root.rotation.y = ry; C.pose = pose;
    C.look = look === 'cam' ? new T.Vector3(...sh.pos) : look ? new T.Vector3(...look) : null;
    C.reflOnly = only === 'refl';   // виден только в отражении на стекле
  }
  for (const k in A) if (!used.has(k) && A[k].root.parent) A[k].root.parent.remove(A[k].root);
  return { sh, o, used };
}
// какие предметы холла по какую сторону стекла (для отражения прячем ту сторону, где стоит камера)
function classifyLobby(o) {
  o.outObjs = [o.glass, o.rain]; o.inObjs = [o.glass]; const bb = new T.Box3();
  for (const ch of o.S.children) {
    if (ch.isLight || ch === o.glass || ch === o.rain || ch === o.sky) continue;
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
const _p = new T.Vector3();
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
  for (const k of used) A[k].update(T0, dt);
  loc.tick(T0, dt);
  let crept = false;
  // на мониторах в будке: лицо вплотную к камере холла
  if (o.creep && sh.feeds && !used.has('monster') && L.lobby === loc) { const C = A.monster; if (C.root.parent !== loc.S) loc.S.add(C.root); C.root.position.set(5.6, 0, 3.0); C.root.rotation.y = .75; C.pose = 'monster'; C.look = new T.Vector3(7.45, 3.85, 5.45); C.update(T0, dt); crept = true; }
  if (sh.feeds && (crept || ++feedT % 3 === 0)) renderFeeds(R, T0);
  aim(cam, sh.pos, sh.look, sh.fov, o.cx ?? .5, o.cy ?? .5, o.z ?? 1, o.sx || 0, o.sy || 0);
  if (o.creep && !crept && !used.has('monster') && placeCreep(loc, o.creep, R)) A.monster.update(T0, dt);
  const cctv = o.cctv ?? sh.cctv ?? 0;
  const PU = postMat.uniforms; PU.uCctv.value = cctv ? 1 : 0; PU.uT.value = T0; PU.uScare.value = clamp(o.scare || 0, 0, 1); PU.uVhs.value = o.vhs ?? VHS;
  if (loc === L.lobby) {
    const U = loc.glassU, lit = clamp((loc.l1.intensity / 1.9) * .55 + (loc.l4.intensity / 1.25) * .25 + loc.amb.intensity * .5, 0, 1.4);
    U.uLit.value = lit; U.uT.value = T0; U.uFogD.value = loc.S.fog.density; U.uRef.value *= loc.reflBoost ?? 1;
    U.uDropC.value.setRGB(.42, .46, .52).multiplyScalar(.5 + .5 * clamp(loc.canopy.intensity / 1.5 + loc.street.intensity / 2.4, 0, 1.4));
    cam.updateMatrixWorld(); renderRefl(R, loc, used, newShot);
  }
  for (const k of used) if (A[k].reflOnly) A[k].root.visible = false;
  R.setRenderTarget(window.__W3core.RT); R.render(loc.S, cam);
  R.setRenderTarget(null); R.render(postScene, postCam);
  g.imageSmoothingEnabled = false; g.drawImage(canvas, 0, 0, W, H); g.imageSmoothingEnabled = true;
  return true;
}
// прогрев: по кадру из каждого места, чтобы все шейдеры собрались во время загрузки
function warm() {
  const c = document.createElement('canvas'); c.width = 54; c.height = 96; const g = c.getContext('2d');
  for (const id of ['hall_cam', 'monitors', 'katya_face', 'corridor_cam', 'server_mih', 'cam13_mih_monster', 'exterior']) draw(g, 54, 96, id, { t: 1, T: 1 });
}
const thumbs = {};
function thumb(id, o = {}) {   // маленькая картинка кадра (плитки камер в обходе)
  if (thumbs[id]) return thumbs[id];
  const c = document.createElement('canvas'); c.width = 135; c.height = 240;
  if (!draw(c.getContext('2d'), 135, 240, id, Object.assign({ t: 2, T: 2 }, o))) return '';
  return (thumbs[id] = c.toDataURL('image/jpeg', .7));
}
// World.props — «нормы» холла (куртка, пропуск, часы); World.vhs — сила плёнки 0..1 (для пункта меню); World.tube — горит ли сейчас ненадёжная лампа (для звука)
window.World = { draw, init, warm, thumb, shots: SH, alias: ALIAS, props: null, get ok() { return scenesReady; }, get tube() { return curLoc ? curLoc.tubeOK ?? 1 : 1; }, set vhs(v) { VHS = clamp(+v || 0, 0, 1); }, get vhs() { return VHS; } };
})();
