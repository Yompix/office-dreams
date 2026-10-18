/* Места действия (холл с будкой и лифтами, улица, 7 этаж, серверная), кадры для каждой сцены и сам показ кадра. */
(() => {
'use strict';
const { T, RW, RH, rnd, lerp, clamp, ps1, mat, ctex, TX, box, plane, cyl, label, relabel, makeTextures } = window.__W3core;
const PI = Math.PI;
let A = null;          // актёры
const L = {};          // места действия

/* ---------- холл бизнес-центра + улица перед ним ---------- */
function buildLobby() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(0x05070a); S.fog = new T.Fog(0x05070a, 9, 34);
  o.amb = new T.AmbientLight(0x6a7a90, .95); S.add(o.amb);
  o.hemi = new T.HemisphereLight(0x6f86a0, 0x1a1410, .25); S.add(o.hemi);
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
  o.flick = plane(1.1, .5, mat(0xe8f0e0, null, { basic: true }), S); o.flick.rotation.x = PI / 2; o.flick.position.set(-4.8, 4.18, 3.4);
  o.l1 = new T.PointLight(0xc8d8d0, 1.6, 16, 1.4); o.l1.position.set(-1, 3.8, 0); S.add(o.l1);
  o.l3 = new T.PointLight(0xb8c8d8, 1.3, 9, 1.5); o.l3.position.set(-.8, 3.6, -4.2); S.add(o.l3);
  o.l2 = new T.PointLight(0xffd9a0, .9, 12, 1.6); o.l2.position.set(-4.8, 3.7, 3.4); S.add(o.l2);
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
  // камеры наблюдения (коробочки на стенах)
  for (const [x, y, z] of [[7.6, 3.95, 5.6], [-7.6, 3.95, -5.6]]) box(.18, .12, .3, mat(0xd0d0d0), x, y, z, S);
  // фасад: стекло, двери, рамы
  const glassM = mat(0x8fb0c4, null, { transparent: true, opacity: .16 });
  const glass = plane(16, 3.5, glassM, S); glass.position.set(0, 1.75, 6);
  o.rainGlass = plane(16, 3.5, new T.MeshBasicMaterial({ map: TX.glassRain, transparent: true, opacity: .4, depthWrite: false }), S); o.rainGlass.position.set(0, 1.75, 6.02); ps1(o.rainGlass.material);
  const frM = mat(0x202326);
  for (let x = -8; x <= 8.01; x += 2) box(.1, 3.5, .14, frM, x, 1.75, 6, S);
  box(16, .14, .16, frM, 0, 3.5, 6, S); box(16, .1, .16, frM, 0, .05, 6, S);
  box(16, .7, .3, mat(0xffffff, TX.wall), 0, 3.85, 6, S);
  box(.06, 2.4, .1, frM, -.02, 1.2, 6.01, S);   // стык дверей
  for (const x of [-.3, .26]) box(.04, .4, .04, mat(0xb0b0b0), x, 1.1, 5.94, S);   // ручки
  // снаружи: тротуар, бордюр, дорога, фонарь, машина, дома напротив, небо
  const walk = plane(40, 5, mat(0xffffff, TX.tileSide), S); walk.rotation.x = -PI / 2; walk.position.set(0, .001, 8.5);
  box(40, .15, .3, mat(0xffffff, TX.curb), 0, .075, 11, S);
  const road = plane(60, 20, mat(0xffffff, TX.asphalt), S); road.rotation.x = -PI / 2; road.position.set(0, -.05, 21);
  for (let x = -24; x < 24; x += 6) { const ln = plane(2.4, .15, mat(0xb8b8a8), S); ln.rotation.x = -PI / 2; ln.position.set(x, -.04, 17); }
  // навес над входом
  box(5, .2, 2.4, mat(0x1a1c1f), 0, 3.4, 7.2, S); o.canopyLamp = box(.6, .04, .3, mat(0xfff0d0, null, { basic: true }), 0, 3.29, 7.2, S);
  o.canopy = new T.PointLight(0xffe2b0, .9, 6, 1.6); o.canopy.position.set(0, 3.1, 7.2); S.add(o.canopy);
  const sgn = plane(3, .55, mat(0xffffff, TX.signOut, { basic: true }), S); sgn.position.set(0, 3.95, 6.17);
  // фонарь
  const pole = cyl(.06, .08, 6, 6, mat(0x222428), S); pole.position.set(3.8, 3, 10.4);
  box(1.2, .06, .06, mat(0x222428), 3.3, 5.95, 10.4, S); o.streetBulb = box(.4, .1, .2, mat(0xffb35a, null, { basic: true }), 2.8, 5.88, 10.4, S);
  o.street = new T.PointLight(0xff9a3a, 2.2, 16, 1.4); o.street.position.set(2.8, 5.6, 10.4); S.add(o.street);
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
  o.rain = new T.LineSegments(rg, new T.LineBasicMaterial({ color: 0x9fb4c8, transparent: true, opacity: .45 })); o.rain.frustumCulled = false; S.add(o.rain);
  // молния: вспышка неба
  o.flash = 0;
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.reset = () => {
    o.faceL.intensity = 0; o.rain.material.opacity = .45;
    for (const l of o.lifts) setLift(o, l, 0, '1');
    o.cabLight.intensity = 0; relabel(o.cabInd, '1');
    o.lamp.intensity = 1.6; o.lampBulb.visible = true; o.boothLight.intensity = .6; o.l1.intensity = 1.6; o.amb.color.set(0x6a7a90); o.amb.intensity = .95; o.l3.intensity = 1.3;
    o.sky.material.map = TX.sky; S.fog.color.set(0x05070a); S.background.set(0x05070a); o.street.intensity = 2.2; o.rain.visible = true; o.rainGlass.visible = true;
    o.dawn = 0;
  };
  o.tick = (t, dt) => {
    const p = o.rain.geometry.attributes.position.array;
    for (let i = 0; i < o.drops.length; i++) { const d = o.drops[i]; d[1] -= d[3] * dt; if (d[1] < 0) d[1] += 12; p[i * 6] = d[0]; p[i * 6 + 1] = d[1]; p[i * 6 + 2] = d[2]; p[i * 6 + 3] = d[0] + .03; p[i * 6 + 4] = d[1] - .35; p[i * 6 + 5] = d[2]; }
    o.rain.geometry.attributes.position.needsUpdate = true;
    TX.glassRain.offset.y = (t * .04) % 1;
    const fl = (Math.sin(t * 13) > .7 && Math.sin(t * 2.1) > .3) ? 0 : 1; o.flick.material.color.setHex(fl ? 0xe8f0e0 : 0x30322e); o.l2.intensity = .9 * fl;
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
  const fog = new T.Color(0x05070a).lerp(new T.Color(0x8a6a5a), k * .8); o.S.fog.color.copy(fog); o.S.background.copy(fog);
  o.amb.color.set(0xffc8a0); o.amb.intensity = .95 + k * .7; o.street.intensity = 2.2 * (1 - k); o.rain.visible = !k; o.rainGlass.visible = !k;
}

/* ---------- 7 этаж: длинный коридор, двери, лампы, кабинет 713 ---------- */
function buildCorridor() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(0x020203); S.fog = new T.Fog(0x020203, 4, 26);
  o.amb = new T.AmbientLight(0x4a5868, .7); S.add(o.amb);
  const len = 26, wd = 2.6;
  const fl = plane(wd, len, mat(0xffffff, TX.carpet), S); fl.rotation.x = -PI / 2; fl.position.z = -len / 2;
  const ce = plane(wd, len, mat(0xffffff, TX.ceil), S); ce.rotation.x = PI / 2; ce.position.set(0, 3, -len / 2);
  const wm = mat(0xffffff, TX.wall);
  for (const s of [-1, 1]) { const w = plane(len, 3, wm, S); w.rotation.y = -s * PI / 2; w.position.set(s * wd / 2, 1.5, -len / 2); const p = plane(len, .9, mat(0xffffff, TX.panel), S); p.rotation.y = -s * PI / 2; p.position.set(s * (wd / 2 - .005), .45, -len / 2); }
  const end = plane(wd, 3, wm, S); end.position.set(0, 1.5, -len);
  const st = plane(wd, 3, wm, S); st.rotation.y = PI; st.position.set(0, 1.5, 0);
  // двери по бокам с табличками
  let n = 701;
  for (let z = -2.5; z > -len + 2; z -= 3.1) for (const s of [-1, 1]) {
    const d = box(.06, 2.1, .95, mat(0xffffff, TX.door), s * (wd / 2 - .03), 1.05, z, S);
    const num = n++; const pl = label(.2, .08, 32, 12, (c, w, h) => { c.fillStyle = '#c9b27a'; c.fillRect(0, 0, w, h); c.fillStyle = '#1b1b1b'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(num), w / 2, h / 2 + 1); }, S);
    pl.position.set(s * (wd / 2 - .065), 1.75, z - .62); pl.rotation.y = -s * PI / 2;
  }
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
  o.blood = new T.Mesh(new T.CircleGeometry(.9, 18), new T.MeshLambertMaterial({ color: 0x2a0303 })); o.blood.rotation.x = -PI / 2; o.blood.scale.set(1, 1.6, 1); o.blood.position.set(0, .006, -12); S.add(o.blood);
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.reset = () => { o.faceL.intensity = 0; o.blood.visible = false; o.lamps[3].li.distance = 7; setLamps(o, 0); o.slit.visible = true; o.slitLight.intensity = 1.2; o.slitLight.color.setHex(0xffe0a0); o.amb.intensity = .7; o.srv.visible = false; };
  o.tick = () => {};
  return o;
}
function setLamps(o, n, fromNear = false) {   // n ламп горит: от дальней (по умолчанию) или от ближней
  o.lamps.forEach((l, i) => { const idx = fromNear ? i : o.lamps.length - 1 - i; const on = idx < n ? 1 : 0; l.li.intensity = on * 2.4; l.tube.material.color.setHex(on ? 0xe8f0e8 : 0x1c1e1c); });
}

/* ---------- серверная / комната тринадцатой камеры ---------- */
function buildServer() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(0x020202); S.fog = new T.Fog(0x020202, 3, 14);
  o.amb = new T.AmbientLight(0x4a5050, .6); S.add(o.amb);
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
  o.reset = () => { o.faceL.intensity = 0; o.salt.visible = true; o.candle.intensity = 1.2; o.red.intensity = 1.4; o.amb.intensity = .6; o.cold.intensity = 1.2; o.cold2.intensity = 1.0; o.rim.intensity = 2.6; };
  o.tick = (t) => { o.candle.intensity = 1.1 + Math.sin(t * 17) * .12 + Math.sin(t * 7) * .08; o.red.intensity = 1.0 + .6 * (Math.sin(t * 2.4) > 0 ? 1 : 0); };
  return o;
}

/* ---------- кадры ---------- */
// act: [актёр, x, y, z, поворот, поза, куда смотрит ('cam' | [x,y,z] | null)]
const lin = (a, b, k) => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
// свет для сцены «Жилец ест»: горит одна лампа прямо над ними и подмигивает
function eatLight(o, t) { setLamps(o, 0); const l = o.lamps[3], on = Math.sin(t * 9) > .85 && Math.sin(t * 1.7) > 0 ? .25 : 1; l.li.intensity = 4.2 * on; l.li.distance = 9; o.amb.intensity = 1.1; l.tube.material.color.setHex(on > .5 ? 0xe8f0e8 : 0x30322e); o.blood.visible = true; o.slitLight.intensity = 0; o.slit.visible = false; A.monster.gore = 1; A.victim.gore = 2; }
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
const SH = {
  hall_cam: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o, t) => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['monster', -3.3, 0, -5.1, .5, 'monster', 'cam']] },
  hall_cam_empty: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]]] },
  hall_cam_near: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['monster', -1.2, 0, -2.4, .8, 'monster', 'cam']] },
  hall_cam_nearer: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['monster', 2.2, 0, -.6, 1.2, 'monster', 'cam']] },
  hall_dawn: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o) => { setDawn(o, 1); return [['smenshik', 3.2, 0, 2.8, -2.3, 'walk', null]]; } },
  exterior: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o, t) => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]]] },
  exterior_dawn: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o) => { setDawn(o, 1); return []; } },
  exterior_two: { loc: 'lobby', pos: [2.2, 1.5, 15], look: [0, 2.4, 6], fov: 55, set: (o) => { setDawn(o, 1); return [['temych', -.6, 0, 9.5, 2.9, 'stand', 'cam'], ['mihalych', .5, 0, 9.4, -2.9, 'stand', [-.6, 1.6, 9.5]]]; } },
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
  door_katya: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 6.5], fov: 50, set: () => [['katya', .62, 0, 6.42, PI, 'knock', 'cam']] },
  door_back: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 7], fov: 50, set: (o, t) => [['katyaSmile', .62, 0, 6.42 + Math.max(0, t - .6) * .55, PI, t > .6 ? 'back' : 'stand', 'cam']] },
  entrance_cam: { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [.5, .8, 6.2], fov: 64, set: (o) => { o.rain.material.opacity = .18; return [['katya', .62, 0, 6.42, PI, 'knock', [.6, 1.5, 4]]]; } },
  katya_face: { loc: 'lobby', pos: [.6, 1.49, 5.4], look: [.62, 1.47, 6.5], fov: 34, set: () => [['katya', .62, 0, 6.42, PI, 'knock', 'cam']] },
  katya_smile: { loc: 'lobby', pos: [.6, 1.49, 5.4], look: [.62, 1.47, 6.5], fov: 34, set: () => [['katyaSmile', .62, 0, 6.42, PI, 'stand', 'cam']] },
  katya_inside: { loc: 'lobby', pos: [.5, 1.6, 3.7], look: [.2, 1.42, 4.75], fov: 44, set: () => [['katya', .2, 0, 4.75, PI + .15, 'reach', 'cam']] },
  katya_inside_still: { loc: 'lobby', pos: [.3, 1.5, 3.95], look: [.2, 1.47, 4.75], fov: 36, set: () => { A.katya.talk = 0; return [['katyaSmile', .2, 0, 4.7, PI, 'stand', 'cam']]; } },
  corridor_cam: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t) => { setLamps(o, [1.2, 2.3, 3.4, 4.5].filter(x => t >= x).length * 2 - 1); return []; } },
  corridor_lit: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o) => { setLamps(o, 7); return []; } },
  corridor_off: { loc: 'corridor', pos: [0, 1.6, -1.2], look: [0, 1.4, -25], fov: 56, set: (o, t) => { setLamps(o, 7 - [.5, 1.0, 1.4, 1.7].filter(x => t >= x).length * 2); const z = t < 1.7 ? null : t < 2.25 ? -18 : t < 2.75 ? -13 : t < 3.15 ? -8.5 : -5;   // не идёт — оказывается ближе после каждой вспышки
    return z != null && (t % .5) > .06 ? [['monster', 0, 0, z, 0, 'monster', 'cam']] : []; } },
  corridor_713: { loc: 'corridor', pos: [0, 1.6, -4], look: [0, 1.0, -26], fov: 52, set: (o) => { setLamps(o, 1); return []; } },
  corridor_dark: { loc: 'corridor', pos: [0, 1.6, -2], look: [0, 1.0, -26], fov: 56, set: (o) => { setLamps(o, 0); o.slitLight.intensity = 0; o.slit.visible = false; return []; } },
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
  smenshik_leave: { loc: 'lobby', pos: [5.6, 1.78, -.9], look: [2.0, 1.3, 5], fov: 56, set: (o, t) => { const k = clamp(t / 2.4, 0, 1), p = lin([4.2, 0, 1.6], [.6, 0, 5.7], k); return [['smenshik', p[0], 0, p[2], Math.atan2(.6 - 4.2, 5.7 - 1.6), k < 1 ? 'walk' : 'stand', null]]; } },
  entrance_empty: { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [.5, .8, 6.2], fov: 64, set: (o) => { o.rain.material.opacity = .18; return []; } },
  door_smile: { loc: 'lobby', pos: [2.2, 1.5, 2.8], look: [.6, 1.35, 6.5], fov: 50, set: () => [['katyaSmile', .62, 0, 6.42, PI, 'knock', 'cam']] },
  corridor_monster: { loc: 'corridor', pos: [0, 2.0, -20.3], look: [0, 2.02, -21.5], fov: 44, set: (o) => { setLamps(o, 1); return [['monster', 0, 0, -21.5, 0, 'reach', 'cam']]; } },
  monster_booth: { loc: 'lobby', pos: [5.3, 1.15, -.6], look: [5.1, 1.85, .8], fov: 52, set: (o) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .3; return [['monster', 5.1, 0, .95, PI, 'reach', 'cam']]; } },
  hall_cam_katya: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', 5.2, 0, -.85, 0, 'sit', [5, 1, 3]], ['katyaSmile', -.6, 0, -1.8, .7, 'stand', 'cam']] },
  elevator_13: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 2.35, -6], fov: 46, set: (o, t) => { setLift(o, o.lifts[0], 1, '13'); o.cabLight.intensity = (Math.sin(t * 19) > -.7) ? 2.4 : .5; return []; } },
  cam13_turn: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 66, set: (o, t) => { o.salt.visible = false; const k = clamp((t - 2.6) / 1.4, 0, 1), e = k * k * (3 - 2 * k); return [['monster', .1, 0, -.5, 3.0, 'monster', lin([.5, 1.9, -3.5], [3.6, 2.7, 3.1], e)]]; } },
  guard_dawn_fear: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: (o, t) => { setDawn(o, 1); A.smenshik.faceSet = 'fear'; return [['smenshik', 5.12, 0, .85 + Math.max(0, t - .8) * .22, PI, t > .8 ? 'back' : 'stand', 'cam']]; } },
  booth_reflect: { loc: 'lobby', pos: [5.3, 1.6, -.55], look: [5.12, 1.6, .9], fov: 38, set: (o) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false; A.temych.faceSet = 'smile'; return [['temych', 5.12, 0, .9, PI, 'stand', 'cam']]; } },
  exterior_alone: { loc: 'lobby', pos: [3.2, 1.4, 17.8], look: [2.0, 2.2, 13], fov: 54, set: () => [['temych', 2.3, 0, 15.6, .25, 'stand', 'cam']] },
  basement_door: { loc: 'corridor', pos: [.15, 1.55, -23.5], look: [0, 1.05, -26], fov: 54, set: (o, t) => { setLamps(o, (Math.sin(t * 11) > .55 && Math.sin(t * 2.3) > -.2) ? 1 : 0); o.srv.visible = true; o.slitLight.color.setHex(0xffb060); o.slitLight.intensity = 1.6 + Math.sin(t * 7) * .3; o.amb.intensity = .35; return []; } },
  cam7_eat: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [0, .7, -12], fov: 30, set: (o, t) => { eatLight(o, t); return [['victim', 0, 0, -12.4, PI * .5, 'lay', null], ['monster', .05, 0, -11.3, PI, 'eat', t < 4 ? [0, .2, -12.3] : [1.0, 2.75, -.5]]]; } },
  eat_close: { loc: 'corridor', pos: [-.7, 1.05, -14.3], look: [.05, .7, -11.8], fov: 54, set: (o, t) => { eatLight(o, t); return [['victim', 0, 0, -12.4, PI * .5, 'lay', null], ['monster', .05, 0, -11.3, PI, 'eat', t < 2.2 ? [0, .2, -12.3] : [-.7, 1.05, -14.3]]]; } },
  cover_door: { loc: 'lobby', pos: [.95, 1.55, 3.7], look: [.5, 1.6, 6.5], fov: 40, set: () => [['katya', .62, 0, 6.42, PI + .1, 'stand', 'cam'], ['monster', -.25, 0, 8.4, PI - .15, 'monster', 'cam']] },
  lunge_server: { loc: 'server', pos: [.35, 1.55, 2.7], look: [0, 1.45, -3], fov: 58, set: (o, t, sh) => { o.salt.visible = false; o.amb.intensity = .12; o.cold.intensity = .3; o.cold2.intensity = .1; o.rim.intensity = 0; o.red.intensity = .35; o.candle.intensity = t < 1.4 ? .5 : 0; return lunge(o, t, sh, { at: 1.4, far: 3.6, glow: .35, show: .5 }); } },
  lunge_booth: { loc: 'lobby', pos: [5.3, 1.2, -.6], look: [5.1, 1.45, .9], fov: 56, set: (o, t, sh) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .2; o.l3.intensity = .1; o.amb.intensity = .2; o.boothLight.intensity = .15; return lunge(o, t, sh, { at: 1.3, far: 2.2, glow: .3, show: .45, blink: true }); } },
  lunge_corridor: { loc: 'corridor', pos: [0, 1.6, -3], look: [0, 1.5, -26], fov: 56, set: (o, t, sh) => { setLamps(o, (t % .5) < .1 && t < 1.35 ? 0 : 7); o.amb.intensity = .35; return lunge(o, t, sh, { at: 1.35, far: 15, steps: [[.6, 10], [.85, 6.5], [1.1, 3.5]], glow: .2, show: .35, blink: true }); } },
  lunge_back: { loc: 'corridor', pos: [0, 1.6, -15], look: [0, 1.5, 0], fov: 58, set: (o, t, sh) => { setLamps(o, t < 1.2 ? 2 : 1, true); o.slit.visible = false; o.slitLight.intensity = 0; return lunge(o, t, sh, { at: 1.2, far: 6, steps: [[.7, 3.2]], glow: .25, show: .4, blink: true }); } },
  lunge_lift: { loc: 'lobby', pos: [-2.2, 1.5, -7.5], look: [-2.2, 1.6, -5.8], fov: 66, set: (o, t, sh) => { relabel(o.cabInd, '13'); o.cabLight.intensity = t < 1.4 ? ((t % .7) < .08 && t > .5 ? 1.2 : 0) : 0; return lunge(o, t, sh, { at: 1.4, far: 1.15, glow: .15, show: .55, blink: true, near: .5 }); } },
  cam7_jump: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t, sh) => { setLamps(o, 0); o.lamps[0].li.intensity = t > .1 ? 1.8 : 0; return lunge(o, t, sh, { at: .08, far: 4.5, near: .5, dur: .2, glow: 0 }); } },
  corridor_cam_dark: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, set: (o, t) => { setLamps(o, 0); const f = Math.sin(t * 13) > .2 || Math.sin(t * 3.1) > .6 ? 1 : 0; o.lamps[6].li.intensity = 1.6 * f; o.lamps[6].tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c); o.amb.intensity = .45; return []; } },
  katya_slam: { loc: 'lobby', pos: [.6, 1.5, 5.45], look: [.62, 1.5, 6.5], fov: 40, set: (o, t) => t < .25 ? [] : [['katya', .62, 0, 6.3, PI, 'knock', 'cam']] },
  run_lit: { loc: 'corridor', pos: [0, 1.55, -2], look: [0, 1.4, -26], fov: 62, set: (o, t, sh) => { setLamps(o, 7); sh.pos[2] = -2 - (t * 2.2) % 12; sh.pos[1] = 1.55 + Math.abs(Math.sin(t * 8)) * .05; sh.look[2] = sh.pos[2] - 24; return []; } },
  run_dark: { loc: 'corridor', pos: [0, 1.5, -3], look: [0, 1.2, -26], fov: 62, set: (o, t) => { setLamps(o, 7 - Math.floor(t * 1.5) % 8, true); return [['mihalych', .3, 0, -5.2 - t * .4, PI, 'walk', null]]; } },
};
// старые имена кадров из первой версии → новые
const ALIAS = { f01: 'hall_cam', f02: 'exterior', f03: 'guard', f04: 'desk_note', f05: 'note', f06: 'monitors', f07: 'corridor_cam', f08: 'elevator_open', f09: 'cam13_monster', f10: 'door_katya', f11: 'entrance_cam', f12: 'katya_face' };

/* ---------- показ ---------- */
let canvas = null, scenesReady = false;
const cam = new T.PerspectiveCamera(50, RW / RH, .05, 120), feedCam = new T.PerspectiveCamera(60, 4 / 3, .05, 80);
let postScene, postCam, postMat, feedRT = [], feedT = 0;
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
  // буфер кадра и постобработка: цветокор + дизеринг + ступени цвета
  window.__W3core.RT = new T.WebGLRenderTarget(RW, RH, { magFilter: T.NearestFilter, minFilter: T.NearestFilter });
  postMat = new T.ShaderMaterial({
    uniforms: { tD: { value: window.__W3core.RT.texture }, uLv: { value: 22 }, uCctv: { value: 0 }, uGam: { value: 1.25 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }',
    fragmentShader: `uniform sampler2D tD; uniform float uLv, uCctv, uGam; varying vec2 vUv;
      float b2(vec2 a){ a = floor(a); return fract(dot(a, vec2(.5, a.y * .75))); }
      float b4(vec2 a){ return b2(.5 * a) * .25 + b2(a); }
      void main(){
        vec3 c = texture2D(tD, vUv).rgb;
        c = mix(c * 12.92, 1.055 * pow(max(c, vec3(.0031308)), vec3(1. / 2.4)) - .055, step(.0031308, c));   // буфер линейный → sRGB
        c = pow(c, vec3(uGam));   // ночь: тени глубже, но без пятен
        float l = dot(c, vec3(.299, .587, .114));
        c = mix(c * vec3(.86, 1.02, 1.08) + vec3(0., .012, .024), c * vec3(1.06, .98, .88), smoothstep(.25, .8, l));   // холодные тени, тёплый свет
        c = mix(c, vec3(l) * vec3(.8, 1., .86), uCctv);
        c = floor(c * uLv + b4(gl_FragCoord.xy) * .95) / uLv;
        gl_FragColor = vec4(c, 1.);
      }` });
  postScene = new T.Scene(); postCam = new T.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  postScene.add(new T.Mesh(new T.PlaneGeometry(2, 2), postMat));
  for (let i = 0; i < 4; i++) feedRT.push(new T.WebGLRenderTarget(96, 72, { magFilter: T.NearestFilter, minFilter: T.NearestFilter }));
  L.lobby.screens.forEach((s, i) => { s.material.map = feedRT[i].texture; s.material.needsUpdate = true; });
  feedRT[3].texture.colorSpace = T.SRGBColorSpace;
  scenesReady = true; return true;
}
const NAME = { 'ТЁМЫЧ': ['temych'], 'СМЕНЩИК': ['smenshik'], 'КАТЯ': ['katya', 'katyaSmile'], 'КАТЯ?': ['katyaSmile', 'katya'], '???': ['monster'], 'МИХАЛЫЧ': ['mihalych'] };
const _v = new T.Vector3(), _d = new T.Vector3();
function aim(c, pos, look, fov, cx, cy, z, sx, sy) {
  c.position.set(pos[0], pos[1], pos[2]); c.fov = fov; c.aspect = RW / RH; c.updateProjectionMatrix(); c.lookAt(look[0], look[1], look[2]); c.updateMatrixWorld();
  // точка (cx, cy) кадра → направление; зум сужает угол обзора вокруг неё (как настоящий объектив)
  _v.set((cx - .5) * 2, -(cy - .5) * 2, .5).unproject(c); _d.copy(_v).sub(c.position).normalize();
  c.lookAt(c.position.x + _d.x, c.position.y + _d.y, c.position.z + _d.z);
  c.fov = 2 * Math.atan(Math.tan(fov * PI / 360) / Math.max(.3, z)) * 180 / PI; c.updateProjectionMatrix();
  c.rotateY(-sx * .00012); c.rotateX(-sy * .00012);
}
let lastT = 0, curLoc = null;
function stage(id, t) {
  const sh = SH[ALIAS[id] || id] || SH.hall_cam, o = L[sh.loc];
  if (curLoc && curLoc !== o) curLoc.reset();
  curLoc = o; o.reset();
  for (const k in A) { A[k].faceSet = 'base'; A[k].gore = 0; A[k].scream = 0; }
  const acts = sh.set(o, t, sh) || [];
  const used = new Set();
  for (const [k, x, y, z, ry, pose, look] of acts) {
    const C = A[k]; used.add(k);
    if (C.root.parent !== o.S) o.S.add(C.root);
    if (C.lastShot !== id) { C.lastShot = id; C.lookDir = null; }
    C.root.position.set(x, y, z); C.root.rotation.y = ry; C.pose = pose;
    C.look = look === 'cam' ? new T.Vector3(...sh.pos) : look ? new T.Vector3(...look) : null;
  }
  for (const k in A) if (!used.has(k) && A[k].root.parent) A[k].root.parent.remove(A[k].root);
  return { sh, o, used };
}
function renderFeeds(R, T0) {
  // мониторы в будке: 0 — холл, 1 — вход, 2 — седьмой этаж, 3 — помехи (камера 13)
  const lob = L.lobby, cor = L.corridor;
  const views = [[lob.S, [7.45, 3.85, 5.45], [-2.2, .9, -5]], [lob.S, [-.9, 3.25, 8.1], [.15, .8, 6.2]], [cor.S, [1.0, 2.75, -.5], [-.1, .9, -25]]];
  lob.screens.forEach(s => (s.visible = false));   // экраны не должны снимать сами себя
  views.forEach(([S, p, l], i) => { feedCam.position.set(...p); feedCam.lookAt(...l); R.setRenderTarget(feedRT[i]); R.render(S, feedCam); });
  lob.screens.forEach(s => (s.visible = true));
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
  const { sh, o: loc, used } = stage(id, t);
  // кто говорит: рот двигается по громкости голоса
  for (const k in A) A[k].talk = 0;
  if (o.talk && o.talk.who) for (const k of NAME[o.talk.who] || []) if (used.has(k)) { A[k].talk = o.talk.v; break; }
  for (const k of used) A[k].update(T0, dt);
  loc.tick(T0, dt);
  let crept = false;
  // на мониторах в будке: лицо вплотную к камере холла
  if (o.creep && sh.feeds && !used.has('monster') && L.lobby === loc) { const C = A.monster; if (C.root.parent !== loc.S) loc.S.add(C.root); C.root.position.set(5.6, 0, 3.0); C.root.rotation.y = .75; C.pose = 'monster'; C.look = new T.Vector3(7.45, 3.85, 5.45); C.update(T0, dt); crept = true; }
  if (sh.feeds && (crept || ++feedT % 3 === 0)) renderFeeds(R, T0);
  aim(cam, sh.pos, sh.look, sh.fov, o.cx ?? .5, o.cy ?? .5, o.z ?? 1, o.sx || 0, o.sy || 0);
  if (o.creep && !crept && !used.has('monster') && placeCreep(loc, o.creep, R)) A.monster.update(T0, dt);
  postMat.uniforms.uCctv.value = o.cctv ? 1 : 0;
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
window.World = { draw, init, warm, thumb, shots: SH, alias: ALIAS, get ok() { return scenesReady; } };
})();
