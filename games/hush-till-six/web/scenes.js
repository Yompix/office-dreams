/* Места действия (холл с будкой и лифтами, улица, 7 этаж, серверная), кадры для каждой сцены и сам показ кадра. */
(() => {
'use strict';
const tr = window.tr || (s => s);
const { T, V, CINE, ULTRA, FINE, litMat, HD, rnd, lerp, clamp, ps1, glow, mat, ctex, TX, box, plane, cyl, room, label, relabel, makeTextures } = window.__W3core;
const PI = Math.PI;
let A = null;          // актёры
const LAYY = .085;      // лежащее тело (поза 'lay'): корень на 8,5 см выше пола, иначе спина и бока уходили под пол на 10 см (World.clips)
const L = {};          // места действия
const FOGC = 0x0b0f14;   // туман чуть светлее чёрного: тёмные силуэты отделяются от него
const hash = n => { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
// ненадёжная лампа: обычно горит; раз в 8–25 с — серия коротких провалов (40–120 мс), иногда гаснет на полторы секунды
// предметы-сканы Poly Haven (CC0, models/props/<имя>.glb, сборка tools/props/pack.mjs) — только «Кино». Пока файл не пришёл или не смог —
// стоит прежний предмет из коробок; пришёл — прежние детали host скрываются (кроме o.stay), модель встаёт основанием в o.at, высотой o.h
let propL = null; const propG = {};
const PROP_SCANS = new Set(['wall_clock', 'cctv']);
function propGlb(name, host, o = {}) {
  if (!CINE || !window.THREE_GLTFLoader || !window.MeshoptDecoder) return;
  // фотосканы выбиваются из стиля игры (владелец, 18 ноя: «как будто с других игр»): табличка с английским CAUTION, ржавый бак, огнетушитель — свои, из коробок.
  // Остаются только часы и камеры (?scans=all — вернуть все, ?scans=0 — убрать все)
  const SC = (location.search.match(/[?&]scans=([a-z0-9]+)/) || [])[1];
  if (SC === '0' || (SC !== 'all' && !PROP_SCANS.has(name))) return;
  const old = host.children.slice();
  propG[name] = propG[name] || (async () => {
    if (!propL) { await window.MeshoptDecoder.ready; propL = new window.THREE_GLTFLoader(); propL.setMeshoptDecoder(window.MeshoptDecoder); }
    return new Promise((ok, no) => propL.load('models/props/' + name + '.glb?v=1', g => ok(g.scene), undefined, no)); })();
  propG[name].then(src => {
    const m = src.clone(true);
    if (o.keep) { const kill = []; m.traverse(n => { if (n !== m && (n.isMesh || n.children.length) && !o.keep(n.name)) kill.push(n); }); kill.forEach(n => n.parent && n.parent.remove(n)); }
    m.updateMatrixWorld(true); const b = new T.Box3().setFromObject(m), sz = b.getSize(new T.Vector3()), k = o.h ? o.h / sz.y : o.k || 1;
    const w = new T.Group(); if (!o.raw) m.position.set(-(b.min.x + b.max.x) / 2, -b.min.y, -(b.min.z + b.max.z) / 2);
    w.add(m); w.scale.setScalar(k); w.rotation.set(o.rx || 0, o.ry || 0, 0, 'YXZ'); if (o.at) w.position.set(o.at[0], o.at[1], o.at[2]);
    m.traverse(n => { if (n.isMesh) { ps1(n.material); n.castShadow = n.receiveShadow = !n.material.transparent; } });
    for (const c of old) if (!(o.stay && o.stay.includes(c))) c.visible = false;
    host.add(w); if (o.done) o.done(w);
  }).catch(e => console.warn('propGlb', name, e));
}
function unreliable(t, seed) {
  const P = 15, k = Math.floor(t / P), ph = t - k * P, st = 2 + hash(k * 7.13 + seed) * (P - 5), d = ph - st;
  if (d < 0 || d > 1.9) return 1;
  if (hash(k * 3.7 + seed * 1.3) < .15) return d < .06 || (d > .22 && d < .27) || d > 1.5 ? 1 : 0;
  const n = 2 + Math.floor(hash(k * 1.7 + seed) * 5);
  for (let i = 0; i < n; i++) { const s = i * .2 + hash(k * 3 + i + seed) * .08, l = .04 + hash(k * 5 + i + seed) * .08; if (d >= s && d < s + l) return 0; }
  return 1;
}

// контактная тень вдоль стены: полоса с тёмной серединой лежит на линии стены (половина спрятана под стеной) — пол темнее у стены, стена — у пола и у потолка.
// Дёшево даёт глубину углам, которых нет без запечённого освещения. w — длина, (x, z) — линия стены, ry — поворот стены, ceilY — высота потолка
let AO_M = null;
function aoEdge(S, w, x, z, ry, ceilY) {
  if (!AO_M) { const t = ctex(8, 64, (c, cw, ch) => { const g = c.createLinearGradient(0, 0, 0, ch); g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(.5, 'rgba(0,0,0,.62)'); g.addColorStop(1, 'rgba(0,0,0,0)'); c.clearRect(0, 0, cw, ch); c.fillStyle = g; c.fillRect(0, 0, cw, ch); });
    AO_M = new T.MeshBasicMaterial({ map: t, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -3 }); }
  const dx = ry ? (ry > 0 ? .006 : -.006) : 0, dz = ry ? 0 : (Math.abs(ry) === PI ? -.006 : .006);
  const f = plane(w, .9, AO_M, S); f.rotation.set(-PI / 2, 0, ry); f.position.set(x, .005, z);
  const v = plane(w, .7, AO_M, S); v.rotation.y = ry; v.position.set(x + dx, 0, z + dz);
  f.userData.solid = v.userData.solid = false;   // тень на стене и полу — не преграда (стена сама твёрдая)
  if (ceilY) { const c = plane(w, .7, AO_M, S); c.rotation.y = ry; c.position.set(x + dx, ceilY, z + dz); c.userData.solid = false; }
}
/* ---------- холл бизнес-центра + улица перед ним ---------- */
function buildLobby() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .055; S.fog = new T.FogExp2(FOGC, o.fogD);
  // заполняющий свет слабый: свет — это лужи под лампами, между ними настоящая темнота
  o.amb = new T.AmbientLight(0x6a7a90, CINE ? .25 : .34); S.add(o.amb);   // «Кино»: заполнение слабее — свет лужами под лампами, стены уходят в темноту (было ровно, как вечером в офисе)
  o.hemi = new T.HemisphereLight(0x6f86a0, 0x1a1410, CINE ? .07 : .1); S.add(o.hemi);
  // пол, потолок, стены
  const floor = plane(16, 12, mat(0xffffff, TX.marble), S); floor.rotation.x = -PI / 2;
  const ceil = plane(16, 12, mat(0xffffff, TX.ceil), S); ceil.rotation.x = PI / 2; ceil.position.y = 4.2;
  const wallM = mat(0xffffff, TX.wall), panelM = mat(0xffffff, TX.panel, { decal: 1 });
  // задняя стена — с проёмом под первый лифт: за открытыми дверями видна кабина, а не стена холла (UV как у целой плоскости)
  const holed = (w, h, m, holes, y1) => { const sh = new T.Shape(); sh.moveTo(-w / 2, -h / 2); sh.lineTo(w / 2, -h / 2); sh.lineTo(w / 2, h / 2); sh.lineTo(-w / 2, h / 2); sh.closePath();
    const yt = Math.min(h / 2, y1 - h / 2);
    for (const [x0, x1] of holes) { const hl = new T.Path(); hl.moveTo(x0, -h / 2); hl.lineTo(x1, -h / 2); hl.lineTo(x1, yt); hl.lineTo(x0, yt); hl.closePath(); sh.holes.push(hl); }
    const gm = new T.ShapeGeometry(sh), uv = gm.attributes.uv; for (let i = 0; i < uv.count; i++) uv.setXY(i, (uv.getX(i) + w / 2) / w, (uv.getY(i) + h / 2) / h);
    const me = new T.Mesh(gm, m); S.add(me); me.userData.solid = [];   // твёрдая — кроме проёмов
    let x = -w / 2; for (const [x0, x1] of holes.concat([[w / 2, w / 2]])) { if (x0 - x > .01) me.userData.solid.push([(x + x0) / 2, 0, -.15, x0 - x, h, .3, 'стена', 1]); x = x1; }
    if (h / 2 - yt > .01) for (const [x0, x1] of holes) me.userData.solid.push([(x0 + x1) / 2, (yt + h / 2) / 2, -.15, x1 - x0, h / 2 - yt, .3, 'стена', 1]);
    return me; };
  const LH = [[-2.84, -1.56], [-.04, 1.24]];   // проёмы обоих лифтов (второй раньше открывался в стену холла — владелец, 12 ноя)
  const back = holed(16, 4.2, wallM, LH, 2.3); back.position.set(0, 2.1, -6);
  const left = plane(12, 4.2, wallM, S); left.rotation.y = PI / 2; left.position.set(-8, 2.1, 0);
  const right = plane(12, 4.2, wallM, S); right.rotation.y = -PI / 2; right.position.set(8, 2.1, 0);
  for (const [w, x, z, ry] of [[12, -7.99, 0, PI / 2], [12, 7.99, 0, -PI / 2]]) { const p = plane(w, 1.1, panelM, S); p.position.set(x, .55, z); p.rotation.y = ry; }
  holed(16, 1.1, panelM, LH, 1.1).position.set(0, .55, -5.99);   // панели задней стены — тоже с проёмом
  // потолочные светильники (часть не горит)
  o.ceilLights = [];
  for (let i = -2; i <= 2; i++) for (let j = -1; j <= 1; j++) {
    const on = !(i === -2 && j === 1) && !(i === 1 && j === -1);
    const p = plane(1.1, .5, mat(on ? 0xe8f0e0 : 0x30322e, null, on ? { basic: true, decal: 1, glow: 3 } : { basic: true, decal: 1 }), S); p.rotation.x = PI / 2; p.position.set(i * 3.2, 4.19, j * 3.4); o.ceilLights.push(p);
  }
  o.ceilOn = mat(0xe8f0e0, null, { basic: true, decal: 1, glow: 3 });   // общий материал горящих панелей (смена гасит холл)
  o.flick = plane(1.1, .5, glow(ps1(new T.MeshBasicMaterial({ color: 0xe8f0e0, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 })), 3), S);   // свой материал: иначе мигали бы все панели o.flick.rotation.x = PI / 2; o.flick.position.set(-4.8, 4.18, 3.4);
  // свет — ровно под своими панелями (тестер, 18 ноя: «свет не там, где сам источник» — лампы висели в метре от панелей, и конус света начинался с пустого потолка)
  o.l1 = new T.PointLight(0xc8d8d0, 1.9, 13, 1.5); o.l1.position.set(0, 3.8, 0); S.add(o.l1);
  o.l3 = new T.PointLight(0xb8c8d8, 1.3, 9, 1.5); o.l3.position.set(0, 3.7, -3.4); S.add(o.l3);
  o.l2 = new T.PointLight(0xffd9a0, .9, 9, 1.6); o.l2.position.set(-4.8, 3.7, 3.4); S.add(o.l2);   // ненадёжная лампа холла (мигающая панель o.flick)
  o.l4 = new T.PointLight(0xc8d4dc, 1.25, 7.5, 1.5); o.l4.position.set(0, 3.7, 3.4); S.add(o.l4);   // над входом изнутри: свет до стекла
  for (const l of [o.l1, o.l2, o.l3, o.l4]) l.userData.beamTop = 4.18;   // конус света — от самой панели на потолке
  // колонны
  for (const [x, z] of [[-4.5, 1], [2.3, 2.6]]) { box(.7, 4.2, .7, mat(0xffffff, TX.wall), x, 2.1, z, S); box(.74, 1.1, .74, panelM, x, .55, z, S); }
  // турникеты перед лифтами: четыре стойки из нержавейки, между ними проходы со стеклянными створками и считыватели пропусков
  { const stM = mat(0xb4b8bc, null, { metal: .85, rough: .28 }), capM = mat(0x0c0d0f, null, { rough: .15 }), rdM = mat(0x101112),
      flapM = ps1(new T.MeshBasicMaterial({ color: 0xa8c0d0, transparent: true, opacity: .18, depthWrite: false, side: T.DoubleSide }));
    for (const x of [-1.75, -1.05, -.35, .35]) {
      const g = new T.Group(); g.position.set(x, 0, 1.0); S.add(g);
      box(.16, .98, .9, stM, 0, .49, 0, g); box(.17, .02, .91, capM, 0, .99, 0, g);
      box(.07, .012, .1, rdM, 0, 1.006, .28, g); box(.014, .006, .014, mat(0x30ff70, null, { basic: true }), 0, 1.014, .28, g);
      for (const s of [-1, 1]) { if ((x < -1.7 && s < 0) || (x > .3 && s > 0)) continue; const f = plane(.21, .5, flapM, g); f.position.set(s * .185, .76, 0); f.userData.solid = false; }   // створки турникета распахиваются перед проходящим — не преграда
    }
  }
  // лифты на задней стене
  o.lifts = [];
  for (const x of [-2.2, .6]) {
    const fr = mat(0x2a2c2e);
    box(1.5, .12, .14, fr, x, 2.36, -5.93, S); box(.12, 2.4, .14, fr, x - .69, 1.2, -5.93, S); box(.12, 2.4, .14, fr, x + .69, 1.2, -5.93, S);
    const dl = box(.62, 2.3, .05, mat(0xffffff, TX.metal), x - .31, 1.15, -5.95, S), dr = box(.62, 2.3, .05, mat(0xffffff, TX.metal), x + .31, 1.15, -5.95, S);
    { const rb = mat(0x0c0d0e, null, { rough: .8 }), NS = m => { m.userData.solid = false; m.userData.noShadow = true; return m; };   // 20 ноя: резиновые кромки створок (между створками читается щель) и порог-направляющая с пазами
      for (const [d, sx] of [[dl, 1], [dr, -1]]) NS(box(.006, 2.3, .056, rb, sx * .308, 0, 0, d));
      const sg = new T.Group(); sg.position.set(x, 0, -5.94); sg.userData.solid = false; S.add(sg);
      NS(box(1.26, .008, .16, mat(0x9a9ea2, null, { metal: .8, rough: .35 }), 0, .004, 0, sg)); for (const z of [.024, .052]) NS(box(1.24, .003, .008, rb, 0, .008, z, sg)); bake(sg); }
    const ind = label(.36, .16, 48, 22, (c, w, h, s = '1', up = 0) => { c.fillStyle = '#080404'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff3a2a'; c.font = '700 18px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText((up ? '▲' : ' ') + s, w / 2, h / 2 + 1); }, S);
    ind.position.set(x, 2.62, -5.94);
    { const bp = new T.Group(); bp.position.set(x + 1.0, 1.3, -6); S.add(bp); bp.userData.solid = false;   // вызывная панель: шлифованная пластина на винтах, кнопка «вверх» в хромированном ободе, подсвечена
      rbox(.09, .2, .012, .004, mat(0xa8adb2, null, { metal: .85, rough: .38 }), 0, 0, .006, bp); for (const sy of [-1, 1]) { const sc = lathe([[0, 0], [.004, 0], [.0035, .002], [0, .002]], 8, mat(0x7a7f84, null, { metal: .9, rough: .3 }), bp, 0, sy * .085, .012); sc.rotation.x = PI / 2; }
      const rim = lathe([[0, 0], [.022, 0], [.022, .006], [.018, .008], [0, .008]], 16, mat(0xc8ccd0, null, { metal: .9, rough: .2 }), bp, 0, .02, .012); rim.rotation.x = PI / 2;
      const kb = lathe([[0, 0], [.016, 0], [.016, .006], [.012, .009], [0, .009]], 14, mat(0xffaa44, null, { basic: true, glow: 1.6 }), bp, 0, .02, .014); kb.rotation.x = PI / 2;
      const ar = new T.Mesh(PGEO.arr || (PGEO.arr = new T.ShapeGeometry(new T.Shape([[-.008, -.004], [.008, -.004], [0, .008]].map(([a, b]) => new T.Vector2(a, b))))), mat(0x3a2408, null, { basic: true })); ar.position.set(0, .021, .0235); bp.add(ar); }
    o.lifts.push({ x, dl, dr, ind, open: 0, shown: '' });
  }
  // 21 ноя: электрощиток у лифтов (обход пешком: «Включить свет»), лампочка горит зелёным, когда свет в порядке
  { const B = new T.Group(); B.position.set(2.75, 1.45, -5.97); S.add(B); const gr = mat(0x7a7f82, null, { metal: .5, rough: .5 });
    box(.42, .56, .12, gr, 0, 0, .06, B); box(.4, .54, .01, mat(0x8a8f92, null, { metal: .5, rough: .45 }), 0, 0, .125, B); box(.03, .08, .02, mat(0x222222), .16, 0, .135, B);
    box(.4, .06, .011, mat(0xc9a400, null, { rough: .6 }), 0, .2, .131, B);   // жёлтая полоса «осторожно»
    o.brLed = cyl(.012, .012, .01, 10, mat(0x3dff6a, null, { basic: true, glow: 2 }), B); o.brLed.rotation.x = PI / 2; o.brLed.position.set(-.15, -.2, .135); }
  // кабина первого лифта (видна, когда двери открыты)
  const cab = new T.Group(); cab.position.set(-2.2, 0, -6.85); S.add(cab);
  // внутри — светлые панели под дерево, тёмный резиновый пол, поручень и зеркало: видно, что это кабина, а не стена холла
  const cm = mat(0xc9b48f, TX.wood), cabFloor = mat(0x2a2a2c);
  box(1.4, .02, 1.6, cabFloor, 0, .01, 0, cab); box(1.4, .02, 1.6, mat(0xe8ecdf, null, { basic: true }), 0, 2.4, 0, cab);
  box(1.3, .02, 1.5, mat(0x3a3a3e), 0, .025, 0, cab);                                                         // коврик
  box(.04, 2.4, 1.6, cm, -.7, 1.2, 0, cab); box(.04, 2.4, 1.6, cm, .7, 1.2, 0, cab); box(1.4, 2.4, .04, cm, 0, 1.2, -.8, cab);
  box(1.4, .12, .04, mat(0x8a8d90, TX.metal), 0, 2.34, -.79, cab); box(.04, .12, 1.6, mat(0x8a8d90, TX.metal), -.69, 2.34, 0, cab); box(.04, .12, 1.6, mat(0x8a8d90, TX.metal), .69, 2.34, 0, cab);   // кант под потолком
  box(1.0, 1.2, .01, mat(0x9aa6ad, null, { emissive: 0x111518 }), 0, 1.4, -.77, cab);       // зеркало
  for (const [x, z, w, d] of [[0, -.75, 1.2, .03], [-.67, 0, .03, 1.4], [.67, 0, .03, 1.4]]) { const r = box(w, .035, d, mat(0xb8bcc0, TX.metal), x, .95, z, cab); r.castShadow = false; }   // поручень
  box(.9, .06, .3, mat(0xfff6dc, null, { basic: true, glow: 2.5 }), 0, 2.38, 0, cab);                                   // плафон
  const panel = box(.02, .5, .2, mat(0x222428, null, { decal: 1 }), .68, 1.2, .5, cab);
  o.cabInd = label(.22, .1, 40, 18, (c, w, h, s = '1') => { c.fillStyle = '#060303'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff3a2a'; c.font = '700 15px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(s, w / 2, h / 2 + 1); }, cab);
  o.cabInd.position.set(0, 2.25, .78); o.cabInd.rotation.y = PI;
  o.cabLight = new T.PointLight(0xfff0d0, 0, 4.5, 1.2); o.cabLight.position.set(0, 2.1, .1); cab.add(o.cabLight);
  o.cab = cab;
  // кабина второго лифта: такая же, но свет в ней не горит (странность «оба лифта открыты»: во втором темно)
  { const c2 = new T.Group(); c2.position.set(.6, 0, -6.85); S.add(c2); const dk = mat(0x6a5e4a, TX.wood);
    box(1.4, .02, 1.6, cabFloor, 0, .01, 0, c2); box(1.4, .02, 1.6, mat(0x1a1b1c), 0, 2.4, 0, c2); box(1.3, .02, 1.5, mat(0x3a3a3e), 0, .025, 0, c2);
    box(.04, 2.4, 1.6, dk, -.7, 1.2, 0, c2); box(.04, 2.4, 1.6, dk, .7, 1.2, 0, c2); box(1.4, 2.4, .04, dk, 0, 1.2, -.8, c2);
    box(1.0, 1.2, .01, mat(0x3a4248, null, { emissive: 0x050607 }), 0, 1.4, -.77, c2);   // зеркало в темноте
    for (const [x, z, w, d] of [[0, -.75, 1.2, .03], [-.67, 0, .03, 1.4], [.67, 0, .03, 1.4]]) box(w, .035, d, mat(0x70757a, TX.metal), x, .95, z, c2).castShadow = false;
    box(.9, .06, .3, mat(0x2a2a28), 0, 2.38, 0, c2); o.cab2 = c2; }
  // табличка и плакат
  const sg = plane(2.6, .5, mat(0xffffff, TX.sign, { decal: 1 }), S); sg.position.set(-.8, 3.35, -5.97);
  const ps = plane(.5, .75, mat(0xffffff, TX.poster, { decal: 1 }), S); ps.position.set(2.6, 1.6, -5.97);
  const ex = plane(.5, .25, mat(0xffffff, TX.exit, { basic: true }), S); ex.rotation.y = PI / 2; ex.position.set(-7.97, 2.6, -3.5);
  { const sdr = box(.06, 2.2, 1.0, mat(0xffffff, TX.door), -7.96, 1.1, -3.5, S); doorHW(sdr, 1, .03, -1, .4, .47);   // дверь на лестницу: была плашкой на стене без ручки и коробки
    const tg = new T.Group(); tg.position.set(-8, 0, -3.5); tg.rotation.y = PI / 2; tg.userData.solid = false; S.add(tg);
    doorTrim(tg, -.5, .5, 2.2, .07, { fr: mat(0x2a1e14, null, { rough: .6 }), gap: mat(0x0b0908, null, { rough: .9 }), sill: mat(0x8a8e92, null, { metal: .7, rough: .4 }) }); bake(tg); }
  // пальма в кадке у первого лифта и её тень на стене: на камере 04 её легко принять за человека («тень от кадки с пальмой»)
  { const pg = new T.Group(); pg.position.set(-3.5, 0, -5.42); S.add(pg); o.palm = pg; pg.userData.solid = [[0, .23, 0, .48, .46, .48, 'кадка с пальмой'], [0, 1.15, 0, .12, 1.4, .12, 'ствол пальмы'], [0, 1.72, 0, 1.3, .36, 1.3, 'крона пальмы']];   // листья — тонкие пластинки во все стороны: твёрдыми не годятся, твёрдая — гуща кроны
    cyl(.24, .18, .46, 12, mat(0x2b2a28, null, { rough: .5 }), pg).position.y = .23; cyl(.23, .23, .03, 12, mat(0x2a2016), pg).position.y = .45;
    const tk = mat(0x5a4632, null, { rough: .9 }); for (let i = 0; i < 6; i++) { const s = cyl(.045 - i * .004, .05 - i * .004, .24, 7, tk, pg); s.position.set(Math.sin(i * .7) * .02, .58 + i * .22, 0); }
    // листья (11 ноя, фото владельца: «лесенка» из брусков): каждая ветвь — изогнутая лента с рисунком листа (черешок и перья, края вырезаны),
    // два яруса, у каждой свой изгиб и длина; слабым («Ретро») — тот же рисунок, меньше сегментов
    const ltx = ctex(96, 256, (x, w, h) => { x.clearRect(0, 0, w, h); x.lineCap = 'round';
      for (let i = 0; i < 26; i++) { const y = h * (.06 + i * .035), k = Math.sin(Math.min(1, i / 22) * PI) * .9 + .12, L = w * .47 * k, g = 62 + ((i * 37) % 30);
        x.strokeStyle = `rgb(${28 + (i % 3) * 6},${g},${24 + (i % 4) * 4})`; x.lineWidth = 5.2 - i * .1;
        for (const sd of [-1, 1]) { x.beginPath(); x.moveTo(w / 2, y); x.quadraticCurveTo(w / 2 + sd * L * .55, y - h * .012, w / 2 + sd * L, y - h * .05 - (i % 2) * 2); x.stroke(); } }
      x.strokeStyle = '#3a4a22'; x.lineWidth = 3; x.beginPath(); x.moveTo(w / 2, h); x.lineTo(w / 2, h * .03); x.stroke(); });
    const lf = mat(0xffffff, ltx, { rough: .62, side: T.DoubleSide, alphaTest: .35, transparent: false });
    const frond = (len, wid, droop, twist) => { const N = CINE ? 9 : 5, g = new T.PlaneGeometry(wid, len, 2, N), P = g.attributes.position;
      for (let i = 0; i < P.count; i++) { const u = clamp(P.getY(i) / len + .5, 0, 1), xx = P.getX(i);   // u: 0 — у ствола, 1 — кончик
        const out = u * len * Math.cos(u * droop * .9), dn = -Math.pow(u, 1.9) * len * Math.sin(droop) * .95 + Math.sin(u * PI) * len * .16;
        P.setXYZ(i, xx * (1 - .25 * u) , dn - Math.abs(xx) * (.35 + .5 * u) + twist * xx * u, out); }
      g.computeVertexNormals(); return g; };
    for (let i = 0; i < 15; i++) { const top = i < 6, a = i * 2.39996 + (top ? .4 : 0), len = top ? .62 + (i % 3) * .07 : .86 + (i % 4) * .08;
      const m = new T.Mesh(frond(len, top ? .26 : .34, top ? .55 + (i % 2) * .2 : 1.05 + (i % 3) * .16, ((i * 7) % 5 - 2) * .12), lf);
      m.position.y = 1.84 + (top ? .05 : 0); m.rotation.set(top ? -.75 : -.28 - (i % 3) * .08, a, 0, 'YXZ'); m.userData.noShadow = true; pg.add(m); }   // листья не бросают тень: фестончатые пятна на полу и тротуаре игроки принимали за странность (владелец, 12 ноя)
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
  propGlb('wall_clock', clk, { raw: 1, k: 1.3, stay: [o.clockH, o.clockM], keep: n => !/hand/.test(n), done: () => { o.clockH.position.z = o.clockM.position.z = .03; } });
  o.setClock = (h, m) => { o.clockM.rotation.z = -m / 60 * PI * 2; o.clockH.rotation.z = -((h % 12) + m / 60) / 12 * PI * 2; };
  // скамейка и растение
  // 19 ноя: скамейка — четыре деревянных бруска на двух гнутых стальных опорах (была доска на двух чёрных плитах);
  // растение — сансевиерия в керамическом кашпо: жёсткие листы-мечи с поперечными полосами и жёлтой каймой (были семь зелёных брусков)
  { const bg = new T.Group(); bg.position.set(-7.4, 0, 1); S.add(bg); bg.userData.solid = [[0, .44, 0, .5, .06, 2, 'скамейка'], [0, .2, -.8, .45, .4, .06, 'скамейка'], [0, .2, .8, .45, .4, .06, 'скамейка']];
    const fm = mat(0x222426, null, { rough: .45, metal: .6 }), wd = mat(0xffffff, TX.wood, { rough: .55 });
    for (const z of [-.8, .8]) { rbox(.46, .035, .06, .01, fm, 0, .405, z, bg); rbox(.48, .03, .07, .01, fm, 0, .015, z, bg); for (const x of [-.19, .19]) rbox(.04, .4, .05, .01, fm, x, .21, z, bg); }
    for (const x of [-.18, -.06, .06, .18]) rbox(.105, .035, 2, .01, wd, x, .44, 0, bg); }
  { const pg = new T.Group(); pg.position.set(-7.3, 0, 4.6); S.add(pg); pg.userData.solid = [[0, .25, 0, .5, .5, .5, 'кашпо'], [0, .85, 0, .4, .7, .4, 'растение']];
    lathe([[0, 0], [.19, 0], [.2, .02], [.235, .44], [.25, .47], [.25, .5], [.235, .5], [.225, .47], [0, .47]], 18, mat(0x2b2f33, null, { rough: .25 }), pg);
    lathe([[0, .455], [.228, .455], [0, .456]], 18, mat(0x231a12, null, { rough: 1 }), pg);   // земля
    const st = ctex(32, 128, (x, w, h) => { x.clearRect(0, 0, w, h); x.beginPath(); x.moveTo(w / 2, 0); x.quadraticCurveTo(w * .98, h * .55, w * .82, h); x.lineTo(w * .18, h); x.quadraticCurveTo(w * .02, h * .55, w / 2, 0); x.closePath(); x.save(); x.clip();
      x.fillStyle = '#c8b43a'; x.fillRect(0, 0, w, h); x.fillStyle = '#1e3a20'; x.fillRect(w * .14, 0, w * .72, h);
      for (let i = 0; i < 22; i++) { x.fillStyle = i % 2 ? 'rgba(120,150,90,.35)' : 'rgba(10,25,12,.4)'; x.beginPath(); x.ellipse(w / 2, 6 + i * 5.6 + Math.sin(i) * 2, w * .34, 1.4 + (i % 3) * .5, Math.sin(i * 1.7) * .15, 0, 7); x.fill(); } x.restore(); });
    const lm = mat(0xffffff, st, { rough: .45, side: T.DoubleSide, alphaTest: .4 });
    for (let i = 0; i < 13; i++) { const L = .55 + hash(i * 3.1) * .45, W = .1 + hash(i * 1.3) * .04, g2 = new T.PlaneGeometry(W, L, 1, 5), P = g2.attributes.position;
      for (let k = 0; k < P.count; k++) { const u = P.getY(k) / L + .5, xx = P.getX(k); P.setXYZ(k, xx * (1 - .3 * u) + Math.sin(u * 2.2) * .02, u * L, Math.pow(u, 2) * L * (.12 + hash(i) * .18) + Math.abs(xx) * .35); }
      g2.computeVertexNormals(); const m = new T.Mesh(g2, lm); const a = i * 2.39996, r = .04 + hash(i * 7) * .1; m.position.set(Math.cos(a) * r, .45, Math.sin(a) * r); m.rotation.set(0, -a + PI / 2, 0); m.userData.noShadow = true; pg.add(m); }
    for (let i = 0; i < 21; i++) rnd(); }   // прежний расход случайных чисел: дальше по холлу всё стоит как было
  // мелочи холла: плинтусы, контактные тени вдоль стен (пол темнее у стены, стена — у пола и у потолка), датчики дыма и вентрешётка,
  // доска объявлений, план эвакуации, огнетушитель в красном ящике, «ВЫХОД» над входом изнутри, торговый автомат у скамейки, урна у лифтов
  { const trim = mat(0x2a1e14, null, { rough: .6 });
    for (const [w, x, z, ry] of [[16, 0, -5.975, 0], [12, -7.975, 0, PI / 2], [12, 7.975, 0, -PI / 2]]) for (const [y, h, d] of [[1.125, .05, .03], [.035, .07, .02]]) { const b = box(w, h, d, trim, x, y, z, S); b.rotation.y = ry; }
    for (const [w, x, z, ry] of [[16, 0, -6, 0], [12, -8, 0, PI / 2], [12, 8, 0, -PI / 2]]) aoEdge(S, w, x, z, ry, 4.2);
    for (const [x, z] of [[-5.5, 2.2], [1.5, -2.4], [5.5, 3.2]]) { const d = cyl(.06, .06, .03, 12, mat(0xe8e8e4), S); d.position.set(x, 4.185, z); box(.012, .006, .012, mat(0xff2020, null, { basic: true }), x + .03, 4.168, z, S); }
    box(.6, .02, .3, mat(0x9a9ea2, null, { metal: .5, rough: .45 }), 3.6, 4.19, 1.2, S); for (let i = 0; i < 6; i++) box(.56, .006, .02, mat(0x2a2c2e), 3.6, 4.178, 1.08 + i * .048, S);
    // доска объявлений (пробка, приколотые листки) — у будки; план эвакуации — на левой стене; огнетушитель — на правой
    const nb = ctex(192, 128, (x, w, h) => { x.fillStyle = '#b08a56'; x.fillRect(0, 0, w, h); for (let i = 0; i < 900; i++) { x.fillStyle = `rgba(${60 + rnd() * 60 | 0},${40 + rnd() * 40 | 0},20,${.08 + rnd() * .1})`; x.fillRect(rnd() * w, rnd() * h, 2, 2); }
      x.fillStyle = '#4a3418'; x.fillRect(0, 0, w, 6); x.fillRect(0, h - 6, w, 6); x.fillRect(0, 0, 6, h); x.fillRect(w - 6, 0, 6, h);
      for (const [px, py, pw, ph, rot] of [[14, 14, 54, 70, -.04], [76, 12, 56, 40, .03], [140, 16, 40, 56, -.02], [80, 60, 50, 54, .05], [16, 92, 60, 28, .02]]) { x.save(); x.translate(px + pw / 2, py + ph / 2); x.rotate(rot); x.fillStyle = rnd() < .3 ? '#efe4b8' : '#ece9e1'; x.fillRect(-pw / 2, -ph / 2, pw, ph);
        x.fillStyle = '#555'; for (let l = 0; l < ph / 7 - 1; l++) x.fillRect(-pw / 2 + 5, -ph / 2 + 7 + l * 7, pw * (.5 + rnd() * .4), 2); x.fillStyle = '#c42a2a'; x.beginPath(); x.arc(0, -ph / 2 + 3, 2.2, 0, 7); x.fill(); x.restore(); } });
    plane(1.15, .78, mat(0xffffff, nb, { decal: 1 }), S).position.set(5.3, 1.95, -5.965);
    const ev = ctex(64, 96, (x, w, h) => { x.fillStyle = '#e9efe6'; x.fillRect(0, 0, w, h); x.fillStyle = '#2d8a4a'; x.fillRect(0, 0, w, 14); x.fillStyle = '#fff'; x.font = '700 8px Arial'; x.textAlign = 'center'; x.fillText(tr('ПЛАН ЭВАКУАЦИИ'), w / 2, 10, w - 4);
      x.strokeStyle = '#333'; x.lineWidth = 1.5; x.strokeRect(8, 22, 48, 60); for (let i = 0; i < 5; i++) x.strokeRect(10 + (i % 3) * 15, 24 + (i > 2 ? 30 : 0), 13, 28); x.fillStyle = '#2d8a4a'; x.fillRect(30, 76, 4, 10); x.fillStyle = '#d33'; x.beginPath(); x.arc(32, 60, 2.5, 0, 7); x.fill(); });
    const evp = plane(.4, .6, mat(0xffffff, ev, { decal: 1 }), S); evp.rotation.y = PI / 2; evp.position.set(-7.965, 1.75, 2.2);
    { const eg = new T.Group(); eg.position.set(7.997, 1.3, -2.6); eg.rotation.y = -PI / 2; S.add(eg); eg.userData.solid = [[0, 0, .1, .34, .64, .2, 'шкаф огнетушителя']];   // шкаф: стальной короб, рамка дверцы со стеклом и ручкой, внутри — огнетушитель (была красная коробка)
      const rm2 = mat(0xc41e1e, null, { rough: .4, metal: .3 }), ch3 = mat(0xb8bcc0, null, { rough: .25, metal: .9 });
      rbox(.34, .64, .012, .004, mat(0x6a1010, null, { rough: .6 }), 0, 0, .006, eg); for (const s of [-1, 1]) { rbox(.016, .64, .2, .004, rm2, s * .162, 0, .1, eg); rbox(.34, .016, .2, .004, rm2, 0, s * .312, .1, eg); }
      for (const s of [-1, 1]) { rbox(.03, .64, .02, .006, rm2, s * .155, 0, .205, eg); rbox(.34, .03, .02, .006, rm2, 0, s * .305, .205, eg); }
      const gp = plane(.28, .58, mat(0xb8d0e0, null, { transparent: true, opacity: .14, rough: .05 }), eg); gp.position.z = .203; gp.renderOrder = 2;
      rbox(.012, .07, .018, .004, ch3, .155, 0, .222, eg);
      const ex = makeExt(new T.Group()); ex.position.set(0, -.304, .105); ex.rotation.y = -.25; eg.add(ex); }
    const fs = ctex(40, 40, (x, w, h) => { x.fillStyle = '#c41e1e'; x.fillRect(0, 0, w, h); x.fillStyle = '#fff'; x.fillRect(17, 8, 6, 24); x.fillRect(14, 6, 12, 5); }); const fsp = plane(.2, .2, mat(0xffffff, fs, { decal: 1 }), S); fsp.rotation.y = -PI / 2; fsp.position.set(7.965, 1.85, -2.6);
    const exi = plane(.5, .25, mat(0xffffff, TX.exit, { basic: true }), S); exi.rotation.y = PI; exi.position.set(.3, 3.35, 5.9);
    // торговый автомат: тёмный корпус, подсвеченная витрина с рядами банок, лампа внутри моргает (o.vendL, в o.tick)
    { const vm = new T.Group(); vm.position.set(-7.55, 0, -2.1); vm.rotation.y = PI / 2; S.add(vm); o.vend = vm;
      box(.95, 1.95, .8, mat(0x24262a, null, { metal: .3, rough: .5 }), 0, .975, 0, vm); box(.97, .12, .82, mat(0x18191c), 0, 1.98, 0, vm);   // крышка чуть шире корпуса: бока не в одной плоскости (иначе мерцают)
      const vt = ctex(96, 160, (x, w, h) => { x.fillStyle = '#0b0d12'; x.fillRect(0, 0, w, h); for (let r = 0; r < 5; r++) for (let c = 0; c < 5; c++) { const cols = ['#d12a2a', '#2a63d1', '#e2b422', '#2aa352', '#d8d8d8']; x.fillStyle = cols[(r * 2 + c) % 5]; x.fillRect(8 + c * 17, 10 + r * 30, 11, 22); x.fillStyle = 'rgba(255,255,255,.25)'; x.fillRect(9 + c * 17, 12 + r * 30, 3, 18); }
        for (let r = 0; r <= 5; r++) { x.fillStyle = '#3a3d44'; x.fillRect(4, 6 + r * 30, w - 8, 3); } x.fillStyle = 'rgba(120,160,220,.08)'; x.fillRect(0, 0, w, h); });
      const win = plane(.62, 1.3, new T.MeshBasicMaterial({ map: vt, color: 0xbfd0e8 }), vm); win.position.set(-.1, 1.15, .401); ps1(win.material); o.vendWin = win;
      const lg = ctex(96, 20, (x, w, h) => { x.fillStyle = '#e8402a'; x.fillRect(0, 0, w, h); x.fillStyle = '#fff'; x.font = '800 12px Arial'; x.textAlign = 'center'; x.fillText(tr('НАПИТКИ'), w / 2, 15, w - 6); });
      const lp = plane(.8, .14, new T.MeshBasicMaterial({ map: lg }), vm); lp.position.set(0, 1.82, .403);   // поверх верхнего края витрины (в её плоскости мерцала) ps1(lp.material);
      box(.22, .5, .02, mat(0x101114), .33, 1.2, .4, vm); box(.14, .1, .03, mat(0x101114, null, { emissive: 0x203040 }), .33, 1.35, .41, vm); box(.5, .22, .03, mat(0x111214), -.1, .3, .405, vm);   // панель выбора, купюроприёмник, лоток выдачи
      { const kb2 = mat(0x8a9096, null, { rough: .3, metal: .8 }), NS = m => { m.userData.solid = false; return m; };   // 19 ноя: кнопки выбора, монетоприёмник, клапан лотка
        for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) NS(rbox(.03, .022, .012, .004, kb2, .3 + c * .035, 1.3 - r * .03, .413, vm));
        NS(rbox(.05, .07, .014, .005, kb2, .33, 1.05, .413, vm)); NS(box(.004, .03, .004, mat(0x050505), .33, 1.06, .421, vm));
        NS(rbox(.44, .14, .012, .02, mat(0x1c1d20, null, { rough: .3 }), -.1, .3, .424, vm)); }
      o.vendL = new T.PointLight(0xc8d8ff, .7, 2.6, 1.6); o.vendL.position.set(-.1, 1.2, .9); vm.add(o.vendL); }
    { const bg = new T.Group(); bg.position.set(1.95, 0, -5.5); bg.rotation.y = .5; S.add(bg); bg.userData.solid = [[0, .34, 0, .36, .68, .36, 'урна']];   // урна: шлифованная сталь, купол с откидной заслонкой, пластиковое основание
      const st2 = mat(0x8a8f94, null, { metal: .85, rough: .35 }); lathe([[0, 0], [.16, 0], [.165, .012], [.15, .04], [0, .04]], 16, mat(0x1c1d1f, null, { rough: .6 }), bg);
      lathe([[0, .04], [.15, .04], [.165, .05], [.17, .56], [.175, .57], [.175, .585], [0, .585]], 16, st2, bg);
      lathe([[0, .58], [.172, .58], [.17, .6], [.15, .64], [.1, .67], [.05, .68], [0, .68]], 16, st2, bg);
      const fl2 = rbox(.13, .085, .012, .006, mat(0x2a2c2f, null, { metal: .7, rough: .4 }), 0, .625, .14, bg); fl2.rotation.x = -.62; fl2.userData.solid = false; }   // заслонка
  }
  // будка охраны: стойка, стол, мониторы, лампа, листок, ключи, стул
  const counterM = mat(0xffffff, TX.panel), topM = mat(0x2a2420);
  box(2.9, 1.15, .1, counterM, 5.1, .575, .45, S);
  // поручень стойки: внутренний свес (15 см) прерывается там, где к стойке придвинут блок мониторов — раньше доска проходила сквозь левую пару экранов (владелец, 12 ноя, «мониторы застряли в текстурах»)
  box(3.0, .05, .25, topM, 5.1, 1.17, .525, S); box(.68, .05, .15, topM, 3.94, 1.17, .325, S); box(1.5, .05, .15, topM, 5.85, 1.17, .325, S);
  box(.1, 1.15, 2.0, counterM, 3.62, .575, -.55, S);
  const gs = plane(.7, .17, mat(0xffffff, TX.guardSign, { decal: 1 }), S); gs.position.set(5.1, .9, .51);
  box(2.6, .05, .8, mat(0xffffff, TX.wood), 5.1, .78, -.05, S); for (const x of [3.9, 6.3]) box(.05, .78, .7, mat(0x1b1b1b), x, .39, -.05, S);
  // мониторы: два ряда, экраны развёрнуты к охраннику
  o.screens = [];
  const bank = new T.Group(); bank.position.set(4.5, 0, .12); bank.rotation.y = PI - .6; S.add(bank);
  // 19 ноя: были плита с четырьмя экранами — теперь четыре монитора в стойке: рамка экрана со скруглёнными краями, корпус сзади с решёткой,
  // подбородок с диодом питания; по бокам — стойки рамы на основании, сверху перекладина (на её крючке висит пропуск)
  const monM = mat(0x1a1b1d, null, { rough: .45 }), monB = mat(0x232528, null, { rough: .6 }), rackM = mat(0x2b2d30, null, { metal: .6, rough: .4 });
  rbox(1.06, .08, .3, .012, monM, 0, .84, -.05, bank);
  for (const s of [-1, 1]) rbox(.03, .72, .08, .008, rackM, s * .495, 1.22, -.06, bank).userData.solid = false; rbox(1.02, .03, .08, .008, rackM, 0, 1.565, -.06, bank).userData.solid = false;   // рама — не преграда (руки тянутся к экранам)
  for (let r = 0; r < 2; r++) for (let c2 = 0; c2 < 2; c2++) {
    const g = new T.Group(); g.position.set((c2 - .5) * .48, 1.03 + r * .34, 0); bank.add(g);
    rbox(.47, .34, .05, .014, monM, 0, 0, -.025, g); rbox(.36, .25, .07, .02, monB, 0, .01, -.08, g);   // рамка, корпус
    box(.008, .006, .003, mat(0x40ff60, null, { basic: true, glow: 1.5 }), .2, -.159, .001, g).userData.solid = false;   // диод питания
    const scr = plane(.44, .3, new T.MeshBasicMaterial({ color: 0xffffff, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), g); scr.position.z = .001; crt(ps1(scr.material));
    o.screens.push(scr);
  }
  // к чему подключены мониторы (владелец, 12 ноя: «раз есть мониторы — они до чего-то подключены?»): видеорегистратор на столе у левой стенки,
  // от каждого экрана к нему идёт кабель, от регистратора — питание за край стола вниз, к удлинителю на полу
  { const wireM = mat(0x0c0c0d, null, { rough: .8 }), caseM = mat(0x232527, null, { rough: .55, metal: .4 });
    const wire = (pts, r = .006) => { const m = new T.Mesh(new T.TubeGeometry(new T.CatmullRomCurve3(pts.map(p => new T.Vector3(p[0], p[1], p[2]))), pts.length * 6, r, 5, false), wireM); m.castShadow = false; m.userData.solid = false; S.add(m); return m; };
    const dvr = new T.Group(); dvr.position.set(4.0, .805, -.3); dvr.rotation.y = -.6; S.add(dvr);   // справа от экранов, лицом к охраннику
    box(.34, .075, .26, caseM, 0, .045, 0, dvr); for (const x of [-.14, .14]) for (const z of [-.1, .1]) box(.03, .008, .03, mat(0x0a0a0a), x, .004, z, dvr);   // корпус на ножках
    box(.3, .05, .004, mat(0x111214, null, { rough: .3 }), 0, .045, -.131, dvr);   // лицевая панель
    box(.1, .018, .003, mat(0x0a0c0e, null, { rough: .2 }), -.08, .05, -.134, dvr);   // лоток диска
    const led = (c, x) => { const l = box(.008, .008, .003, mat(c, null, { basic: true, glow: 2 }), x, .045, -.134, dvr); l.castShadow = false; return l; };
    led(0x38e060, .06); o.dvrRec = led(0xff3030, .085); led(0xffb030, .11);   // питание, запись (мигает), диск
    const lb = plane(.06, .014, mat(0xd8d8d0, null, { basic: true }), dvr); lb.position.set(-.08, .068, -.1345); lb.rotation.y = PI;   // наклейка «DVR-4»
    const back = dvr.localToWorld.bind(dvr); dvr.updateMatrixWorld(true);
    // четыре видеокабеля: от задней стенки каждого экрана вниз к столу и по столу к регистратору
    bank.updateMatrixWorld(true);
    [[.24, 1.37], [-.24, 1.37], [.24, 1.03], [-.24, 1.03]].forEach(([bx, by], i) => {
      const a = bank.localToWorld(new T.Vector3(bx, by, -.125)), d = bank.localToWorld(new T.Vector3(bx * .5, .9, -.2)), e = back(new T.Vector3(-.1 + i * .05, .05, .135));
      wire([[a.x, a.y, a.z], [d.x, by * .5 + .5, d.z], [d.x, .83, d.z], [4.2 + .02 * i, .812, .2 - .015 * i], [3.98 + .012 * i, .812, .02 - .01 * i], [e.x - .03, .83, e.z + .05], [e.x, e.y, e.z]]); });
    // питание: из регистратора за край стола, вниз вдоль стенки, по полу к удлинителю под столом
    const pw = back(new T.Vector3(.13, .05, .135));
    wire([[pw.x, pw.y, pw.z], [pw.x - .05, .83, pw.z + .04], [3.79, .8, -.1], [3.75, .5, -.06], [3.73, .06, 0], [3.95, .012, .08], [4.3, .012, .05]], .007);
    const strip = new T.Group(); strip.position.set(4.42, 0, .02); strip.rotation.y = .3; S.add(strip);
    box(.26, .035, .055, mat(0xd9d6cc, null, { rough: .6 }), 0, .018, 0, strip); for (let i = 0; i < 4; i++) box(.03, .004, .03, mat(0x1a1a1a), -.09 + i * .06, .037, 0, strip);
    box(.012, .006, .02, mat(0xff4020, null, { basic: true, glow: 1.5 }), .115, .038, 0, strip);   // клавиша с подсветкой
    wire([[4.55, .02, .03], [4.7, .012, .12], [4.9, .012, .3], [5.0, .012, .38]], .007);   // шнур удлинителя — к розетке у стойки
  }
  // лампа
  // настольная лампа (19 ноя): тяжёлое литое основание с выключателем, латунная стойка с изгибом, эмалевый абажур (внутри светлый), лампочка, шнур за стол
  { const lg = new T.Group(); S.add(lg); lg.userData.solid = [[6.15, .822, -.15, .17, .035, .17, 'настольная лампа'], [6.15, 1.0, -.15, .02, .32, .02, 'настольная лампа']];   // основание и стойка (за лампу тянется рука — h_lamp)
    const enM = mat(0x1f5a3a, null, { rough: .3, metal: .2 }), brM = mat(0xb08a4a, null, { rough: .3, metal: .85 });
    lathe([[0, 0], [.085, 0], [.085, .008], [.078, .018], [.05, .026], [.016, .03], [.014, .04], [0, .04]], 18, enM, lg, 6.15, .805, -.15);
    box(.012, .01, .02, mat(0x141414, null, { rough: .4 }), 6.09, .825, -.1, lg).rotation.y = .5;   // выключатель
    cable([[6.15, .84, -.15], [6.15, 1.03, -.15], [6.14, 1.16, -.14], [6.105, 1.236, -.13]], .007, brM, lg, 8);
    const sh = new T.Group(); sh.position.set(6.07, 1.17, -.13); sh.rotation.z = -.5; lg.add(sh);
    const SP = [[.13, -.07], [.128, -.064], [.1, -.03], [.06, .03], [.04, .066], [.022, .078], [0, .08]];
    lathe(SP, 16, enM, sh); lathe(SP.map(([r, y]) => [r * .97, y - .002]), 16, mat(0xe8e0c8, null, { rough: .6, side: T.BackSide, emissive: 0x3a3020 }), sh);
    tor(.129, .004, brM, sh, PI * 2, 4, 20).rotation.x = PI / 2; sh.children[sh.children.length - 1].position.y = -.068;   // латунный кант по краю
    o.lampBulb = lathe([[0, -.03], [.02, -.022], [.026, -.005], [.016, .012], [.01, .02], [.01, .03], [0, .03]], 10, mat(0xffe8a0, null, { basic: true, glow: 3 }), sh, 0, -.035, 0);
    cable([[6.2, .807, -.12], [6.3, .808, .1], [6.34, .807, .33], [6.35, .7, .37], [6.36, .02, .38], [6.5, .006, .38]], .0035, mat(0x111214, null, { rough: .6 }), lg); }
  o.lamp = new T.PointLight(0xffc46a, 1.6, 3.2, 1.5); o.lamp.position.set(5.95, 1.05, -.12); S.add(o.lamp);
  o.boothLight = new T.PointLight(0x9fe0c0, .6, 5, 1.6); o.boothLight.position.set(4.6, 1.4, -.2); S.add(o.boothLight);
  o.note = plane(.42, .28, mat(0xffffff, TX.note, { decal: 1 }), S); o.note.rotation.x = -PI / 2; o.note.rotation.z = PI + .12; o.note.position.set(5.35, .8085, -.27);   // раскрытый журнал смен
  { const cv = plane(.44, .30, mat(0x2a1e16), S); cv.rotation.x = -PI / 2; cv.rotation.z = PI + .12; cv.position.set(5.35, .8062, -.27); o.noteCv = cv; }   // обложка под разворотом — над столешницей (было на 1 мм под ней)   // обложка под разворотом
  // связка ключей (19 ноя, вид — там же и того же размера, за неё берутся руки): латунная бирка с номером, кольцо, три ключа — головка с дыркой и бородка с зубцами
  o.keys = new T.Group(); rbox(.06, .008, .035, .003, mat(0xb8a060, null, { emissive: 0x201800, rough: .35, metal: .8 }), 0, 0, 0, o.keys);
  { const ks = mat(0xc0c2c4, null, { rough: .3, metal: .9 }), br = mat(0xb89a50, null, { rough: .3, metal: .9 }); const rg = tor(.011, .0013, ks, o.keys, PI * 2, 4, 16); rg.rotation.x = PI / 2; rg.position.set(0, .001, .026);
    for (const [x, z, a, m, L] of [[.015, .04, .1, ks, .06], [-.012, .035, -.25, br, .05], [.002, .045, -.05, ks, .055]]) { const k = new T.Group(); k.position.set(x * .4, 0, .026); k.rotation.y = a; o.keys.add(k);
      const hd = lathe([[0, -.0015], [.009, -.0015], [.01, 0], [.009, .0015], [0, .0015]], 10, m, k, 0, 0, .014); hd.scale.z = .8;
      box(.007, .003, L - .02, m, 0, 0, .019 + (L - .02) / 2, k); for (let i = 0; i < 4; i++) box(.003, .0028, .004, m, .005, 0, .03 + i * (L - .03) / 4 + (i % 2) * .002, k); } }
  o.keys.position.set(5.68, .81, -.28); o.keys.rotation.y = .6; S.add(o.keys);
  { const rg = new T.Group(); rg.position.set(5.9, .805, .15); rg.rotation.y = PI - .25; S.add(rg); rg.userData.solid = [[0, .05, 0, .12, .1, .08, 'рация']];   // рация в зарядном стакане (было — чёрный брусок того же размера)
    const bk = mat(0x1e1f21, null, { rough: .55 }), gr = mat(0x2c2e31, null, { rough: .7 });
    rbox(.12, .035, .08, .01, bk, 0, .0175, 0, rg); box(.01, .004, .004, mat(0x30ff60, null, { basic: true, glow: 1.5 }), .04, .022, .0405, rg);   // стакан, диод зарядки
    rbox(.058, .105, .032, .009, gr, 0, .085, 0, rg); rbox(.046, .05, .004, .003, mat(0x141516, null, { rough: .8 }), 0, .075, .016, rg);   // корпус, решётка динамика
    lathe([[0, 0], [.006, 0], [.0055, .02], [.004, .06], [.003, .075], [0, .077]], 8, bk, rg, -.017, .137, 0);   // антенна
    lathe([[0, 0], [.007, 0], [.007, .012], [0, .012]], 10, mat(0x3a3c3e, null, { rough: .4 }), rg, .012, .137, 0);   // ручка громкости
    box(.008, .022, .006, mat(0xb03020, null, { rough: .4 }), -.03, .11, 0, rg); }   // тангента сбоку
  { const cup = new T.Group(); cup.name = 'кружка'; cup.position.set(4.95, .805, -.3); cup.rotation.y = 2.2; S.add(cup);   // кружка Фрэнка: полая, ободок, ручка, остывший чай
    const cm = mat(0xe4ded2, null, { rough: .35 }); cm.side = T.DoubleSide;
    const body = new T.Mesh(new T.CylinderGeometry(.037, .033, .092, 24, 1, true), cm); body.position.y = .046; cup.add(body);
    const bot = new T.Mesh(new T.CircleGeometry(.033, 24), cm); bot.rotation.x = -PI / 2; bot.position.y = .002; cup.add(bot);
    const rim = new T.Mesh(new T.TorusGeometry(.037, .0028, 6, 28), cm); rim.rotation.x = PI / 2; rim.position.y = .092; cup.add(rim);
    const tea = new T.Mesh(new T.CircleGeometry(.035, 24), mat(0x2a1608, null, { rough: .15 })); tea.rotation.x = -PI / 2; tea.position.y = .072; cup.add(tea);
    const hd = new T.Mesh(new T.TorusGeometry(.024, .0065, 8, 18, PI * 1.15), cm); hd.position.set(.046, .05, 0); hd.rotation.z = -PI / 2 - PI * .075; cup.add(hd);
    const band = new T.Mesh(new T.CylinderGeometry(.0372, .0362, .012, 24, 1, true), mat(0x8a2a24)); band.position.y = .066; cup.add(band);   // красная полоска
    for (const m of [body, bot, rim, hd, band]) m.castShadow = true; }
  // стул стоит под тазом сидящего: в позе «сидя» (Sitting_Idle_Loop) таз и спина Итана на 35–49 см позади его корня (SEAT) — стул на 25 см дальше SEAT, иначе спинка проходила сквозь спину (проверка — World.clips, tools/story/sheet.js)
  const chair = new T.Group(); chair.name = 'стул в будке'; chair.position.set(5.2, 0, -.99); S.add(chair);
  officeChair(chair, .5, .445, .8, .6, -.3); chair.userData.solid = [[0, .445, 0, .5, .07, .5, 'стул в будке'], [0, .8, -.3, .5, .6, .06, 'стул в будке'], [0, .21, 0, .05, .42, .05, 'стул в будке']];   // коллайдеры — прежние
  o.chair = chair;
  // второй стул у пульта: на спинке так и висит куртка Фрэнка («куртка на стуле»)
  const ch2 = new T.Group(); ch2.position.set(6.25, 0, -.62); ch2.rotation.y = -.5; S.add(ch2); o.ch2 = ch2;   // финалы смертей в будке отодвигают его (оно стоит между стульями)
  { const cg = new T.Group(); ch2.add(cg); officeChair(cg, .46, .47, .78, .56, -.22); cg.userData.solid = [[0, .47, 0, .46, .07, .46, 'стул у пульта'], [0, .78, -.22, .46, .56, .06, 'стул у пульта'], [0, .22, 0, .05, .45, .05, 'стул у пульта']]; }
  const jk = new T.Group(); jk.position.set(0, 0, -.22); ch2.add(jk); jk.userData.solid = false;   // куртка на спинке — ткань: рука лезет в её карман (fb_jacket); сам стул твёрдый
  const jm = mat(0x24324c, null, { decal: 1 }), jt = mat(0x3a4a64, null, { decal: 1 });
  // 20 ноя (тестер: «плоская синяя картонка с прямыми углами, не висит как ткань»; владелец: вещи не должны быть декорацией): куртка — полотно, перекинутое через
  // спинку: спереди короче, сзади длиннее, плечи покатые, по полотну складки, подол волной; рукава — мягкие сплюснутые трубы, висят с плеч и чуть расходятся
  { const jc = mat(0x24324c, null, { rough: .92 }); jc.side = T.DoubleSide;
    const g = new T.PlaneGeometry(1, 1, 14, 22), P = g.attributes.position;
    for (let i = 0; i < P.count; i++) { const u = P.getX(i) + .5, v = P.getY(i) + .5, sx = 2 * u - 1;   // v: 0 — подол спереди, .42….58 — через верх спинки, 1 — подол сзади
      const top = clamp((v - .42) / .16, 0, 1), a = top * PI, front = v < .42, back = v > .58, dn = front ? (.42 - v) / .42 : back ? (v - .58) / .42 : 0;
      let y = front ? 1.075 - dn * .43 : back ? 1.075 - dn * .58 : 1.075 + .028 * Math.sin(a), z = front ? .068 : back ? -.068 : .068 * Math.cos(a);
      const w = .25 - .02 * dn + .012 * Math.sin(a), fold = dn * (.014 * Math.sin(u * PI * 5 + (back ? 1.3 : 0)) + .008 * Math.sin(u * PI * 11 + 2));
      y -= .05 * sx * sx * (1 - .6 * dn) + .012 * dn * Math.sin(u * 9 + (back ? 2 : 0));   // покатые плечи, подол неровный
      z += (front ? 1 : back ? -1 : 0) * (fold + .01 * dn);
      P.setXYZ(i, sx * w, y, z); }
    g.computeVertexNormals(); jk.add(new T.Mesh(g, jc));
    for (const sd of [-1, 1]) { const sg = new T.CylinderGeometry(.062, .05, .5, 9, 6, true), Q = sg.attributes.position;
      for (let i = 0; i < Q.count; i++) { const yy = Q.getY(i), k = .5 - yy / .5 * .5 - .0;   // k: 0 у плеча … 1 у манжеты
        Q.setXYZ(i, Q.getX(i) * (1 + .12 * Math.sin(k * 7 + sd)) + sd * .035 * k * k, yy, Q.getZ(i) * (.5 + .12 * Math.sin(k * 5 + 1)) + .03 * Math.sin(k * 2.4)); }
      sg.computeVertexNormals(); const sl = new T.Mesh(sg, jc); sl.position.set(sd * .262, .775, .012); sl.rotation.z = sd * .07; jk.add(sl);
      const cf = new T.Mesh(new T.CylinderGeometry(.053, .053, .035, 9, 1, true), (() => { const m = jt.clone(); m.side = T.DoubleSide; return m; })()); cf.scale.z = .55; cf.position.set(sd * (.262 + .035 + .017), .535, .012 + .02); cf.rotation.z = sd * .07; jk.add(cf); }
    { const cg = new T.CylinderGeometry(.085, .1, .05, 10, 1, true, -PI * .62, PI * 1.24), cl = new T.Mesh(cg, jc); cl.scale.z = .5; cl.position.set(0, 1.105, .03); cl.rotation.x = .5; jk.add(cl); }   // воротник лежит на плечах
    box(.16, .04, .004, mat(0xc8c8c8), .1, .97, .083, jk); }   // нашивка
  o.jacket = jk;
  // 21:06, осмотр будки (сцена booth): из кармана куртки торчит пачка соли, крупинки — на сиденье и на полу
  // 21 ноя (владелец: «соль должна реально быть в кармане, а не как-то за ним»): карман — объёмный, с боками и дном; пачка стоит в нём, наружу торчит только верх
  { const pk = new T.Group(); pk.position.set(.12, .73, .108); jk.add(pk); box(.15, .12, .008, jt, 0, 0, 0, pk);   // накладной карман: перед
    for (const sx of [-1, 1]) box(.008, .12, .034, jt, sx * .071, 0, -.017, pk); box(.15, .008, .034, jt, 0, -.056, -.017, pk);   // бока и дно
    o.saltPack = new T.Group(); o.saltPack.position.set(.008, .035, -.018); o.saltPack.rotation.set(-.06, .08, .07); pk.add(o.saltPack);
    box(.072, .1, .03, mat(0xdfe5ec), 0, 0, 0, o.saltPack);
    const sp = ctex(36, 50, (x, w, h) => { x.fillStyle = '#e4e9ef'; x.fillRect(0, 0, w, h); x.fillStyle = '#1f4f96'; x.fillRect(0, h * .56, w, h * .44);
      x.font = '800 10px Arial'; x.textAlign = 'center'; x.fillText(tr('СОЛЬ'), w / 2, h * .42); x.fillStyle = '#e8eef6'; x.font = '700 6px Arial'; x.fillText(tr('крупная'), w / 2, h * .8); });
    const sf = plane(.068, .096, mat(0xffffff, sp, { decal: 1 }), o.saltPack); sf.position.z = .016;
    o.saltGr = new T.Group(); ch2.add(o.saltGr);   // горсть на сиденье: горка и россыпь вокруг, немного просыпалось на пол
    const sm = mat(0xdedbd2, null, { rough: .9 }), heap = cyl(.004, .045, .018, 9, sm, o.saltGr); heap.position.set(.06, .514, .1); heap.scale.z = .8;
    const gr = new T.InstancedMesh(new T.BoxGeometry(.006, .004, .006), sm, 160); gr.frustumCulled = false; o.saltGr.add(gr);
    const m4 = new T.Matrix4(), q = new T.Quaternion(), e = new T.Euler();
    for (let i = 0; i < 160; i++) { const fl = i >= 120, a = rnd() * PI * 2, r = (fl ? .16 : .035 + .09 * Math.pow(rnd(), 1.6)) * (fl ? Math.sqrt(rnd()) : 1);
      e.set(0, rnd() * PI, 0); q.setFromEuler(e); m4.compose(new T.Vector3(.06 + Math.cos(a) * r, fl ? .003 : .507, (fl ? .34 : .1) + Math.sin(a) * r * .75), q, new T.Vector3(1, 1, 1)); gr.setMatrixAt(i, m4); }
    o.saltPack.visible = o.saltGr.visible = false; }
  // стекло на столе, под ним фото (Новый год: Фрэнк, Гас и Марта); узкий ящик под столешницей; телефон Фрэнка на зарядке
  { const ph = ctex(60, 42, (x, w, h) => { x.fillStyle = '#efe9dc'; x.fillRect(0, 0, w, h); x.fillStyle = '#5a4a3a'; x.fillRect(3, 3, w - 6, h - 6);
      const g = x.createLinearGradient(0, 3, 0, h - 3); g.addColorStop(0, '#8a7a62'); g.addColorStop(1, '#3a3028'); x.fillStyle = g; x.fillRect(3, 3, w - 6, h - 6);
      for (const [cx, c, hc] of [[16, '#2b3036', '#c8c4bc'], [30, '#3e5a72', '#3a3430'], [44, '#9a1c1c', '#8c8680']]) { x.fillStyle = c; x.fillRect(cx - 5, 17, 10, 22); x.fillStyle = '#d8b8a0'; x.fillRect(cx - 3, 9, 6, 7); x.fillStyle = hc; x.fillRect(cx - 3, 8, 6, 3); }
      x.fillStyle = 'rgba(236,230,214,.9)'; x.fillRect(41, 10, 6, 6); });   // у неё вместо лица — стёртое пятно
    o.deskPhoto = plane(.14, .098, mat(0xffffff, ph, { decal: 1 }), S); o.deskPhoto.rotation.set(-PI / 2, 0, PI - .08); o.deskPhoto.position.set(4.78, .8062, -.33);
    o.deskGlass = plane(.62, .36, new T.MeshBasicMaterial({ color: 0x9fb0bc, transparent: true, opacity: .07, depthWrite: false, blending: T.AdditiveBlending }), S); o.deskGlass.rotation.x = -PI / 2; o.deskGlass.position.set(4.82, .8085, -.26);
    o.drawer = new T.Group(); o.drawer.position.set(4.78, .717, -.44); S.add(o.drawer);   // открывается к стулу (−z)
    const dmw = mat(0xffffff, TX.wood), dmi = mat(0x2a2218);
    box(.46, .072, .018, dmw, 0, 0, -.009, o.drawer); box(.1, .012, .012, mat(0xb8bcc0, null, { metal: .8, rough: .3 }), 0, 0, -.024, o.drawer);   // лицевая панель, ручка
    box(.43, .008, .38, dmi, 0, -.03, .19, o.drawer); for (const sx of [-1, 1]) box(.008, .05, .38, dmi, sx * .21, -.008, .19, o.drawer); box(.43, .05, .008, dmi, 0, -.008, .38, o.drawer);
    box(.12, .002, .17, mat(0xe8e2d2), -.08, -.025, .12, o.drawer); const pen = cyl(.004, .004, .13, 6, mat(0x1a3a8a), o.drawer); pen.rotation.set(PI / 2, 0, .4); pen.position.set(.1, -.022, .1);   // бумаги и ручка
    o.fphone = new T.Group(); o.fphone.position.set(4.6, .8125, -.36); o.fphone.rotation.y = .25; S.add(o.fphone);
    // телефон Фрэнка — старый кнопочный, тёмно-красный (владелец, 12 ноя: Итан говорит по телефону, а на столе лежит такой же — «где логика?»; теперь видно, что это чужой)
    box(.05, .014, .118, mat(0x5a1418, null, { metal: .1, rough: .55 }), 0, .002, 0, o.fphone); box(.044, .002, .05, mat(0x1a1c1e, null, { rough: .35 }), 0, .0095, -.028, o.fphone);
    for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) box(.011, .002, .008, mat(0xd8d2c4, null, { rough: .6 }), -.014 + c * .014, .0095, .012 + r * .012, o.fphone).castShadow = false;
    box(.004, .03, .004, mat(0x1a1c1e), .019, .012, -.062, o.fphone);   // антенна-штырёк
    o.fphoneScr = label(.064, .136, 32, 68, (c, w, h) => { c.fillStyle = '#050607'; c.fillRect(0, 0, w, h); c.strokeStyle = '#d23030'; c.lineWidth = 2; c.strokeRect(9, 26, 14, 22); c.fillStyle = '#d23030'; c.fillRect(13, 23, 6, 3); c.fillRect(11, 44, 10, 3);
      c.fillStyle = '#f2f2f2'; c.beginPath(); c.moveTo(18, 29); c.lineTo(12, 38); c.lineTo(16, 38); c.lineTo(14, 46); c.lineTo(20, 36); c.lineTo(16, 36); c.closePath(); c.fill(); }, o.fphone);   // батарея на нуле, молния — заряжается
    o.fphoneScr.rotation.x = -PI / 2; o.fphoneScr.position.set(0, .0108, -.028); o.fphoneScr.scale.set(.62, .34, 1);   // маленький экранчик над кнопками
    const cb = mat(0xe8e8e8); box(.006, .005, .2, cb, 0, -.002, .17, o.fphone); box(.18, .005, .006, cb, -.09, -.002, .27, o.fphone);   // провод зарядки к розетке за мониторами
    o.fphone.visible = false;
    // журнал Фрэнка до трёх часов: закрыт и перевязан бечёвкой, на обложке его рукой «До трёх. Весь.»
    o.book = new T.Group(); o.book.position.set(5.35, .821, -.27); o.book.rotation.y = .12; S.add(o.book);
    const bc = ctex(128, 176, (x, w, h) => { x.fillStyle = '#3a2a1e'; x.fillRect(0, 0, w, h); for (let i = 0; i < 260; i++) { x.fillStyle = `rgba(0,0,0,${.12 * rnd()})`; x.fillRect(rnd() * w, rnd() * h, 2, 2); }
      x.fillStyle = '#ddd4bc'; x.fillRect(18, 34, 92, 50); x.fillStyle = '#1c2a6a'; x.font = 'italic 700 17px Georgia, "Times New Roman", serif'; x.textAlign = 'center'; const L = tr('До трёх.|Весь.').split('|'); L.forEach((t, i) => x.fillText(t, 64, 56 + i * 21)); });
    const cvm = mat(0x2e2218); box(.2, .022, .288, mat(0xe2dccb), .004, 0, 0, o.book);   // срез страниц
    for (const y of [-.0125, .0125]) box(.215, .003, .3, cvm, 0, y, 0, o.book); box(.006, .028, .3, cvm, -.105, 0, 0, o.book);   // обложка и корешок
    const bk = plane(.21, .29, mat(0xffffff, bc, { decal: 1 }), o.book); bk.rotation.set(-PI / 2, 0, PI); bk.position.y = .0148;   // надпись на 0,8 мм над обложкой (было 0,2 — мигала)
    const tw = mat(0xb8a27a), TZ = -.035; box(.225, .004, .006, tw, 0, .0176, TZ, o.book); box(.006, .004, .31, tw, -.03, .0178, 0, o.book); box(.03, .006, .012, tw, -.03, .0192, TZ, o.book);   // бечёвка целиком над надписью   // бечёвка крест-накрест под надписью, узел
    for (const sx of [-1, 1]) { box(.006, .032, .006, tw, sx * .1105, 0, TZ, o.book); box(.006, .032, .006, tw, -.03, 0, sx * .153, o.book); }
    o.book.visible = false; }
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
    // следы снаружи на стекле будки (смерть «Ты ответил»): отпечаток ладони, мазок вниз, пятно дыхания
    { const mk = fn => new T.MeshBasicMaterial({ map: ctex(64, 96, fn, 1, 1, 'raw'), transparent: true, depthWrite: false, opacity: 0, color: 0xdde4e8, side: T.DoubleSide });
      const blob = (x, cx, cy, rx, ry, a) => { for (let k = 4; k > 0; k--) { x.globalAlpha = a * .3; x.beginPath(); x.ellipse(cx, cy, rx * (.7 + k * .1), ry * (.7 + k * .1), 0, 0, 7); x.fill(); } };
      o.bPalm = plane(.19, .27, mk((x, w, h) => { x.clearRect(0, 0, w, h); x.fillStyle = '#fff'; blob(x, w * .5, h * .68, w * .3, h * .2, .9);
        [[.18, .45, .07, .18], [.35, .3, .07, .23], [.52, .26, .07, .25], [.68, .31, .065, .22], [.88, .58, .06, .14]].forEach(([fx, fy, rw, rh]) => blob(x, w * fx, h * fy, w * rw, h * rh, .9)); }), S);
      o.bSmear = plane(.17, 1, mk((x, w, h) => { x.clearRect(0, 0, w, h); for (let i = 0; i < 9; i++) { const g = x.createLinearGradient(0, 0, 0, h); g.addColorStop(0, 'rgba(255,255,255,.75)'); g.addColorStop(1, 'rgba(255,255,255,.1)'); x.fillStyle = g; x.fillRect(w * (.08 + i * .1), 0, w * (.03 + (i % 3) * .015), h); } }), S);
      o.bFog = plane(.32, .24, mk((x, w, h) => { x.clearRect(0, 0, w, h); const g = x.createRadialGradient(w / 2, h / 2, 2, w / 2, h / 2, w / 2); g.addColorStop(0, 'rgba(255,255,255,.9)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); }), S);
      for (const m of [o.bPalm, o.bSmear, o.bFog]) { m.rotation.y = PI; m.visible = false; m.renderOrder = 3; m.userData.solid = false; } }   // следы на стекле — не преграда (само стекло — да)
    // будка закрытая: над левой стойкой тоже стекло, сзади перегородка (панель + стекло), справа дверь с замком и глухая панель до стены
    for (const [x, z, w, ry] of [[3.62, -.55, 1.96, PI / 2], [5.81, -1.55, 4.38, 0]]) {
      const pg = new T.Group(); pg.position.set(x, 0, z); pg.rotation.y = ry; S.add(pg);
      if (z < -1) box(w, 1.148, .06, counterM, 0, .574, 0, pg);   // верх на 2 мм ниже поручня стены (в одной плоскости с ним мерцал)
      const gl = plane(w - .04, .86, gmL, pg); gl.position.y = 1.65; const d = plane(w - .04, .86, gmD, pg); d.position.set(0, 1.65, .001);
      box(w, .03, .04, frM, 0, 2.095, 0, pg); box(w, .03, .05, frM, 0, 1.2, 0, pg); o.boothGlass.push(pg);
    }
    const dM = mat(0xffffff, TX.door), hM = mat(0xb8bcc0, null, { metal: .85, rough: .3 });
    box(.06, 2.1, .1, frM, 6.6, 1.05, .45, S); box(.06, 2.1, .1, frM, 7.52, 1.05, .45, S); box(.98, .06, .1, frM, 7.06, 2.13, .45, S);
    box(.46, 2.16, .06, counterM, 7.77, 1.08, .45, S);                                   // глухая панель до стены
    o.boothDoor = new T.Group(); o.boothDoor.position.set(6.63, 0, .45); S.add(o.boothDoor);   // петли у стойки, дверь открывается внутрь будки
    box(.86, 2.04, .04, dM, .44, 1.03, 0, o.boothDoor); const dw = plane(.36, .5, gmL, o.boothDoor); dw.position.set(.44, 1.5, -.021); dw.rotation.y = PI;
    box(.035, .2, .03, hM, .8, 1.0, -.04, o.boothDoor);   // накладка замка
    o.boothHandle = new T.Group(); o.boothHandle.position.set(.81, 1.08, -.05); o.boothDoor.add(o.boothHandle); rbox(.12, .022, .026, .009, hM, -.05, 0, 0, o.boothHandle);   // нажимная ручка (размер и место прежние) на круглой розетке
    { const rs3 = lathe([[0, 0], [.024, 0], [.022, .01], [.013, .02], [0, .02]], 12, hM, o.boothDoor, .81, 1.08, -.02); rs3.rotation.x = -PI / 2; rs3.userData.solid = false; }   // ручка изнутри: rotation.z > 0 — нажата вниз
    { const og = new T.Group(); og.userData.solid = false; o.boothDoor.add(og); const NSh = m => { m.userData.noShadow = true; return m; };   // 20 ноя: снаружи у двери не было ни ручки, ни окна
      // (окно — бледный блик только с внутренней стороны, на дереве): теперь с обеих сторон матовое стекло в штапике, снаружи накладка с ручкой, внутри (куда дверь открывается) — петли
      NSh(rbox(.035, .2, .012, .004, hM, .8, 1.0, .026, og)); NSh(lathe([[0, 0], [.024, 0], [.022, .01], [.013, .02], [0, .02]], 12, hM, og, .81, 1.08, .02)).rotation.x = PI / 2; NSh(rbox(.12, .022, .026, .009, hM, .76, 1.08, .05, og));
      for (const y of [.3, 1.75]) NSh(cyl(.011, .011, .11, 10, hM, og)).position.set(.012, y, -.03);
      const frost = mat(0x56646a, null, { rough: .3, metal: .1, decal: 1 });
      for (const sz of [-1, 1]) { const pn = NSh(plane(.38, .52, frost, og)); pn.position.set(.44, 1.5, sz * .0203); if (sz < 0) pn.rotation.y = PI;
        for (const dy of [-.26, .26]) NSh(box(.42, .022, .012, frM, .44, 1.5 + dy, sz * .024, og)); for (const dx of [-.199, .199]) NSh(box(.022, .5, .012, frM, .44 + dx, 1.5, sz * .024, og)); }
      bake(og);
      const sl = box(.86, .006, .09, mat(0x8a8e92, null, { metal: .7, rough: .4 }), 7.06, .003, .45, S); sl.userData.solid = false; sl.userData.noShadow = true; }   // накладка порога
    o.boothLock = [7.43, 1.0, .385];   // скважина изнутри (куда вставляют ключ)
    // 21 ноя: кабинет охраны обжит (владелец: «не проработан до конца, что внутри»): шкаф с папками, столик с чайником и кружками
    // (Гас спрашивает про чайник), ключница со связками, часы, календарь, огнетушитель, урна. Всё — в правой части за спиной Итана, вне хода двери
    { const K = new T.Group(); S.add(K); const grey = mat(0x6d7275, null, { metal: .4, rough: .5 }), dk = mat(0x2c2f33, null, { rough: .7 }), wood = mat(0x5a3f2a, null, { rough: .8 }),
        red = mat(0x9c1c1c, null, { rough: .45 }), wht = mat(0xd8d6cf, null, { rough: .6 }), stl = mat(0xa8adb2, null, { metal: .8, rough: .3 });
      // шкаф с документами: 3 ящика, ручки, на нём коробка и папки
      box(.46, 1.32, .5, grey, 7.68, .66, -1.24, K); for (const y of [.25, .68, 1.1]) { box(.4, .005, .01, dk, 7.68, y + .19, -.985, K); box(.12, .02, .02, stl, 7.68, y, -.98, K); }
      box(.34, .24, .3, mat(0x8c7a5e, null, { rough: .9 }), 7.66, 1.44, -1.26, K);
      [0x23466e, 0x7a2222, 0x2f5d3a, 0x3a3a3a].forEach((c, i) => box(.05, .3, .24, mat(c, null, { rough: .7 }), 7.5 + i * .055, 1.47, -1.0, K));
      // столик: чайник, две кружки, коробка соли (как у Фрэнка), сахар
      box(.6, .04, .42, wood, 6.95, .74, -1.3, K); for (const [dx, dz] of [[-.27, -.18], [.27, -.18], [-.27, .18], [.27, .18]]) box(.035, .72, .035, dk, 6.95 + dx, .36, -1.3 + dz, K);
      const ket = new T.Group(); ket.position.set(6.82, .76, -1.32); K.add(ket); cyl(.075, .085, .2, 16, wht, ket).position.y = .1; cyl(.03, .075, .04, 16, wht, ket).position.y = .22;
      box(.02, .1, .1, dk, -.1, .14, 0, ket); box(.06, .018, .018, wht, .1, .13, 0, ket).rotation.z = .6; cyl(.09, .09, .015, 16, dk, ket).position.y = .005;
      for (const [x, z, c] of [[7.05, -1.2, 0xc0392b], [7.14, -1.38, 0x2e4057]]) { const m = cyl(.035, .03, .09, 12, mat(c, null, { rough: .5 }), K); m.position.set(x, .805, z); }
      box(.06, .1, .04, wht, 7.18, .81, -1.18, K);
      // ключница на правой стене: ящик со стеклом, связки на крючках
      box(.05, .42, .34, wood, 7.975, 1.55, -.55, K); for (let i = 0; i < 6; i++) { const kk = cyl(.012, .012, .05, 8, stl, K); kk.rotation.z = PI / 2; kk.position.set(7.94, 1.45 + (i % 2) * .16, -.67 + Math.floor(i / 2) * .12); }
      // часы над ключницей, календарь рядом
      const ck = cyl(.14, .14, .03, 24, wht, K); ck.rotation.z = PI / 2; ck.position.set(7.98, 2.05, -.55); const rim = cyl(.15, .15, .025, 24, dk, K); rim.rotation.z = PI / 2; rim.position.set(7.985, 2.05, -.55);
      box(.006, .1, .012, dk, 7.96, 2.08, -.55, K).rotation.x = .5; box(.006, .07, .012, dk, 7.96, 2.03, -.53, K).rotation.x = -1.2;
      const cal = plane(.3, .42, mat(0xffffff, ctex(96, 134, (x, w, h) => { x.fillStyle = '#efece4'; x.fillRect(0, 0, w, h); x.fillStyle = '#b02020'; x.fillRect(0, 0, w, 26);
        x.fillStyle = '#fff'; x.font = 'bold 18px sans-serif'; x.textAlign = 'center'; x.fillText('10', w / 2, 19); x.fillStyle = '#333';
        for (let i = 0; i < 31; i++) { const cx = 10 + (i % 7) * 12.5, cy = 42 + Math.floor(i / 7) * 18; x.font = '9px sans-serif'; x.fillText(String(i + 1), cx, cy); if (i >= 20 && i <= 27) { x.strokeStyle = '#a00'; x.beginPath(); x.moveTo(cx - 5, cy - 7); x.lineTo(cx + 5, cy + 2); x.stroke(); } } }), { rough: .9 }), K);
      cal.rotation.y = -PI / 2; cal.position.set(7.97, 1.55, -1.0);   // октябрь: дни Фрэнка зачёркнуты до 28-го
      // огнетушитель в углу у двери (вне хода створки) и урна под столом
      const fx = new T.Group(); fx.position.set(7.85, 0, -.35); K.add(fx); cyl(.075, .075, .48, 14, red, fx).position.y = .26; cyl(.04, .06, .06, 12, dk, fx).position.y = .53; box(.12, .02, .03, dk, .03, .57, 0, fx);
      const bin = cyl(.13, .11, .32, 14, dk, K); bin.position.set(4.1, .16, -1.25);
      bake(K); }
  }
  // стекло перегородки будки (видно только в кадре с отражением): рама, грязь, блик
  o.boothPane = new T.Group(); o.boothPane.position.set(5.2, 1.62, .25); S.add(o.boothPane);
  const bp = plane(1.3, .95, new T.MeshBasicMaterial({ map: TX.grime, transparent: true, opacity: .22, depthWrite: false, color: 0x8c98a0 }), o.boothPane); bp.rotation.y = PI; bp.scale.set(-1.6, 1.3, 1); ps1(bp.material);
  const gl = plane(1.3, .95, new T.MeshBasicMaterial({ color: 0x8090a0, transparent: true, opacity: .12, depthWrite: false, blending: T.AdditiveBlending }), o.boothPane); gl.rotation.y = PI; gl.position.z = .002;
  const glare = plane(.09, 1.1, new T.MeshBasicMaterial({ color: 0xd0dce6, transparent: true, opacity: .1, depthWrite: false, blending: T.AdditiveBlending }), o.boothPane); glare.rotation.set(0, PI, .5); glare.position.set(.3, 0, .004);
  for (const [w, h, x, y] of [[1.36, .04, 0, .49], [1.36, .04, 0, -.49], [.04, 1.02, .66, 0], [.04, 1.02, -.66, 0]]) box(w, h, .03, mat(0x2a2c2e), x, y, 0, o.boothPane);
  o.boothPane.visible = false;
  // пропуск Фрэнка висит на шнурке на крючке сбоку корпуса мониторов (раньше шнурок ни за что не держался — пропуск висел в воздухе над экранами: владелец, 12 ноя)
  box(.075, .012, .012, mat(0x2b2d30, null, { metal: .6, rough: .4 }), .535, 1.563, .02, bank);
  o.badge = new T.Group(); o.badge.position.set(.562, 1.363, .02); o.badge.rotation.set(.05, .08, .025); bank.add(o.badge);
  box(.004, .2, .004, mat(0x2a4a8a), 0, .1, 0, o.badge);
  o.badgeCard = plane(.11, .082, mat(0xffffff, TX.badge, { decal: 1 }), o.badge); o.badgeCard.position.y = -.03; box(.115, .087, .003, mat(0x9aa0a6), 0, -.03, -.003, o.badge);
  // камеры наблюдения (коробочки на стенах)
  for (const [x, y, z] of [[7.6, 3.95, 5.6], [-7.6, 3.95, -5.6]]) { const g2 = new T.Group(); g2.position.set(x, y, z); S.add(g2);
    { const cg = new T.Group(); cg.rotation.set(.3, x > 0 ? PI * .3 + PI : PI * .3, 0, 'YXZ'); g2.add(cg); g2.userData.solid = false; const wm3 = mat(0xd8dadc, null, { rough: .4 });   // «Ретро» (и пока скан не пришёл): своя камера — корпус, козырёк, объектив, кронштейн
      rbox(.09, .08, .22, .015, wm3, 0, 0, 0, cg); rbox(.105, .008, .25, .004, wm3, 0, .048, .01, cg); const ln2 = lathe([[0, 0], [.032, 0], [.032, .012], [.024, .016], [0, .016]], 12, mat(0x151618, null, { rough: .3 }), cg, 0, 0, .11); ln2.rotation.x = PI / 2;
      rbox(.03, .1, .03, .008, wm3, 0, .09, -.06, cg); }
    propGlb('cctv', g2, { k: 1, at: [0, -.12, 0], ry: x > 0 ? -PI * .7 : PI * .3, rx: .3 }); }
  // фасад: стекло (ночью отражает холл), плёнка, грязь, капли, рамы, двери
  buildFacade(o, S);
  // снаружи: тротуар, бордюр, дорога, фонарь, машина, дома напротив, небо
  const walk = plane(40, 5, mat(0xffffff, TX.tileSide), S); walk.rotation.x = -PI / 2; walk.position.set(0, .001, 8.5);
  if (TX.puddles) { const pd = o.pud = plane(40, 5, mat(0xffffff, TX.puddles, { transparent: true, opacity: .85, rough: .02, metal: 0, decal: 1 }), S); pd.material.alphaTest = .3; pd.rotation.x = -PI / 2; pd.position.set(0, .004, 8.5); pd.userData.solid = false; }   // лужи — зеркало: в них фонарь и свет входа; растут за ночь (wetAt)
  box(40, .15, .3, mat(0xffffff, TX.curb), 0, .075, 11, S);
  const road = plane(60, 20, mat(0xffffff, TX.asphalt), S); road.rotation.x = -PI / 2; road.position.set(0, -.05, 21);
  for (let x = -24; x < 24; x += 6) { const ln = plane(2.4, .15, mat(0xb8b8a8, null, { decal: 1 }), S); ln.rotation.x = -PI / 2; ln.position.set(x, -.04, 17); }
  // 21 ноя: та сторона улицы (из холла и с обхода видно сквозь стекло; была пустая темнота): тротуар, дома с окнами — ночь, горит редкое окно, фонари
  { const far = new T.Group(); far.userData.solid = false; S.add(far); let sd = 7; const rnd = () => (sd = (sd * 16807) % 2147483647) / 2147483647;
    const curb2 = box(60, .15, .3, mat(0xffffff, TX.curb), 0, .075, 25, far); curb2.userData.noShadow = true;
    const walk2 = plane(60, 4, mat(0x3a3c3e, null, { rough: .9 }), far); walk2.rotation.x = -PI / 2; walk2.position.set(0, .02, 27.2);
    const winTex = ctex(256, 128, (x, w, h) => { x.fillStyle = '#16181b'; x.fillRect(0, 0, w, h);
      for (let fy = 0; fy < 6; fy++) for (let fx = 0; fx < 16; fx++) { const r = rnd(), lit = r < .13, warm = rnd() < .75;
        x.fillStyle = lit ? (warm ? '#d9a35a' : '#9fb8d0') : (r < .5 ? '#22262b' : '#1b1e22'); x.fillRect(4 + fx * 16, 6 + fy * 21, 9, 13); } }, 1, 1, 'raw');
    for (const [x0, w, h] of [[-26, 14, 16], [-11, 10, 22], [0, 12, 13], [13, 9, 19], [24, 12, 15]]) {
      const tx = winTex.clone(); tx.needsUpdate = true; tx.repeat.set(w / 14, h / 14); tx.wrapS = tx.wrapT = T.RepeatWrapping; tx.offset.set(rnd(), rnd());
      const f = plane(w, h, new T.MeshBasicMaterial({ map: tx, fog: true }), far); f.rotation.y = PI; f.position.set(x0, h / 2, 29.3);
      box(w, h, .4, mat(0x15171a, null, { rough: 1 }), x0, h / 2, 29.55, far); }
    for (const x of [-14, -2, 10]) { const pole = cyl(.06, .08, 5.2, 8, mat(0x2a2c2e, null, { metal: .5, rough: .5 }), far); pole.position.set(x, 2.6, 25.5);
      const arm = box(1.1, .06, .06, mat(0x2a2c2e), x, 5.15, 25.0, far); arm.rotation.y = PI / 2;
      const bulb = box(.35, .08, .2, new T.MeshBasicMaterial({ color: 0xffb35a, fog: true }), x, 5.08, 24.5, far); bulb.userData.noShadow = true; }
    bake(far); }
  // навес над входом. Его задняя грань — за рамами (z = 6.1), а не в плоскости стекла (z = 6): там стекло и навес «спорили» по глубине,
  // и полоса стекла под верхней рамой мигала при любом движении камеры (владелец 29 окт: «мигает текстура» над EXIT)
  box(5, .2, 2.3, mat(0x1a1c1f), 0, 3.4, 7.25, S); o.canopyLamp = box(.6, .04, .3, mat(0xfff0d0, null, { basic: true, glow: 3 }), 0, 3.29, 7.2, S);
  o.canopy = new T.PointLight(0xffe2b0, 1.5, 6, 1.6); o.canopy.position.set(0, 3.1, 7.2); S.add(o.canopy);   // свет сверху: глазницы в тени
  const sgn = plane(3, .55, mat(0xffffff, TX.signOut, { basic: true }), S); sgn.position.set(0, 3.95, 6.17);
  // фонарь
  // 19 ноя: конусная стальная опора на фланце с болтами, лючок, гнутый кронштейн, корпус светильника с рассеивателем снизу (были шестигранный столб и брусок)
  { const lg = new T.Group(); S.add(lg); lg.userData.solid = [[3.8, 3, 10.4, .2, 6, .2, 'фонарь']]; const pm = mat(0x2a2d31, null, { rough: .45, metal: .6 });
    lathe([[0, 0], [.16, 0], [.16, .02], [.11, .03], [.095, .06], [.085, .3], [.05, 5.8], [.045, 5.9], [0, 5.9]], 12, pm, lg, 3.8, 0, 10.4);
    for (let i = 0; i < 4; i++) { const a = i * PI / 2 + PI / 4; lathe([[0, 0], [.018, 0], [.018, .012], [.008, .03], [0, .03]], 6, pm, lg, 3.8 + Math.cos(a) * .12, .02, 10.4 + Math.sin(a) * .12); }
    rbox(.08, .2, .02, .004, pm, 3.8, .7, 10.31, lg);   // лючок
    cable([[3.8, 5.6, 10.4], [3.8, 5.9, 10.4], [3.65, 6.08, 10.4], [3.3, 6.02, 10.4], [3.05, 5.96, 10.4]], .035, pm, lg, 8);
    const hd = new T.Group(); hd.position.set(2.85, 5.93, 10.4); hd.rotation.z = .06; lg.add(hd);
    rbox(.55, .1, .26, .04, pm, 0, .02, 0, hd); rbox(.5, .02, .2, .02, mat(0x1a1b1d, null, { rough: .4 }), 0, -.03, 0, hd);
    o.streetBulb = rbox(.44, .02, .16, .008, mat(0xffb35a, null, { basic: true, glow: 3 }), 0, -.045, 0, hd); o.streetBulb.userData.solid = false; }
  o.street = new T.PointLight(0xff9a3a, 1.2, 16, 1.4); o.street.position.set(2.8, 5.6, 10.4); S.add(o.street);
  // пятно света фонаря на тротуаре — круглое, края тают (была квадратная плашка 4×4 с резкими краями — владелец 13 ноя: «квадрат»)
  const poolTex = ctex(128, 128, (x, w, h) => { const g = x.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2); g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.45, 'rgba(255,255,255,.55)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); });
  const pool = plane(5, 5, new T.MeshBasicMaterial({ color: 0xff9a3a, map: poolTex, transparent: true, opacity: .16, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), S); pool.rotation.x = -PI / 2; pool.position.set(2.8, .01, 10.6); ps1(pool.material);
  // машина у тротуара
  // мусорные баки справа от входа и кошка у них (смена: Фрэнк двадцать лет записывал её в журнал)
  { const bm = mat(0x24382c, null, { rough: .6 }), lid = mat(0x18241c, null, { rough: .5 });
    for (const [x, z, r] of [[5.3, 8.9, .05], [6.5, 8.95, -.08]]) { const g2 = new T.Group(); g2.position.set(x, 0, z); g2.rotation.y = r; S.add(g2);
      g2.userData.solid = [[0, .55, 0, 1.12, 1.1, .84, 'мусорный контейнер']]; makeDumpster(g2, bm, lid, r > 0 ? .12 : 0); } }
  { const g2 = new T.Group(); g2.position.set(4.35, 0, 8.95); S.add(g2); propGlb('trash_can', g2, { h: .86, ry: .6, keep: n => /rust/.test(n) }); }   // оцинкованный бак с крышкой рядом с контейнерами (только «Кино»)
  o.cat = new T.Group(); o.cat.position.set(3.1, 0, 8.6); o.cat.rotation.y = -1.3; o.cat.scale.setScalar(1.15); S.add(o.cat); o.cat.visible = false;
  { const cm = mat(0x0c0c0e, null, { rough: .9 }), body = new T.Mesh(new T.SphereGeometry(.1, 10, 8), cm); body.scale.set(1, .85, 2.0); body.position.set(0, .2, 0); o.cat.add(body);
    const hd = new T.Mesh(new T.SphereGeometry(.07, 10, 8), cm); hd.position.set(0, .32, .2); o.cat.add(hd); o.catHead = hd;
    for (const s of [-1, 1]) { const e = new T.Mesh(new T.ConeGeometry(.028, .06, 6), cm); e.position.set(s * .038, .055, -.005); e.rotation.z = -s * .25; hd.add(e);
      const ey = new T.Mesh(new T.SphereGeometry(.011, 6, 6), mat(0x9fe060, null, { basic: true })); ey.position.set(s * .027, .012, .06); hd.add(ey); }
    for (const [x, z] of [[-.05, .12], [.05, .12], [-.05, -.12], [.05, -.12]]) { const l = cyl(.016, .014, .16, 6, cm, o.cat); l.position.set(x, .08, z); }
    o.catTail = new T.Group(); o.catTail.position.set(0, .22, -.19); o.cat.add(o.catTail);
    for (let i = 0; i < 5; i++) { const sgm = cyl(.014, .012, .06, 6, cm, o.catTail); sgm.position.set(0, .03 + i * .045, -i * .02); sgm.rotation.x = -.35 + i * .12; } }
  const car = new T.Group(); car.position.set(-4.6, 0, 13); car.rotation.y = PI / 2; S.add(car);
  makeCar(car);
  // наше здание (над холлом) и дома напротив
  const fac = o.fac = plane(30, 36, mat(0xffffff, TX.facade, { emissive: 0x050608 }), S); fac.position.set(0, 4.2 + 18, 6.01);
  o.facLit = fac.material; o.facDark = mat(0xffffff, TX.facadeDark, { emissive: 0x030405 });
  o.facLit.emissiveMap = TX.facadeEm; o.facLit.emissive.setHex(0xffffff);   // горящие окна светятся сами, а не только там, куда достаёт фонарь
  // окно над крышей: тёплый свет в воздухе, в раме — как будто там ещё один этаж
  o.ghostWin = new T.Group(); o.ghostWin.position.set(-3.1, 41.6, 6.0); S.add(o.ghostWin); o.ghostWin.visible = false;
  plane(1.5, 1.05, mat(0xffd9a0, null, { basic: true, fog: false }), o.ghostWin).position.z = .01;
  for (const [w, h, x, y] of [[1.7, .1, 0, .57], [1.7, .1, 0, -.57], [.1, 1.2, -.8, 0], [.1, 1.2, .8, 0], [.06, 1.1, 0, 0]]) box(w, h, .06, mat(0x0d0f12, null, { fog: false }), x, y, .03, o.ghostWin);
  for (const [x, w, h] of [[-14, 9, 22], [-3, 10, 30], [9, 11, 18], [20, 9, 26]]) { const b = box(w, h, 6, mat(0xffffff, TX.facade, { emissive: 0x040506 }), x, h / 2, 34, S); b.material.emissiveMap = TX.facadeEm; b.material.emissive.setHex(0xb0b0b0); }
  o.sky = new T.Mesh(new T.SphereGeometry(80, 12, 8), new T.MeshBasicMaterial({ map: TX.sky, side: T.BackSide, fog: false })); S.add(o.sky);
  // дождь
  const N = 700, pos = new Float32Array(N * 6); o.drops = [];
  // под навесом (|x| < 2,5, z < 8,4) дождя нет: капли оттуда переставлены на его кромку — получается завеса с края козырька.
  // Раньше дождь шёл сквозь навес, и в концовке «Рассвет» Фрэнк курил под дождём (владелец, 12 ноя)
  for (let i = 0; i < N; i++) { const d = [lerp(-14, 14, rnd()), rnd() * 12, lerp(6.25, 24, rnd()), 6 + rnd() * 5];
    if (Math.abs(d[0]) < 2.55 && d[2] < 8.45) { if (rnd() < .5) d[2] = 8.45 + rnd() * .12; else { d[0] = (d[0] < 0 ? -1 : 1) * (2.55 + rnd() * .1); } d.push(1); }   // d[4] — капля с кромки: падает только ниже навеса
    o.drops.push(d); }
  const rg = new T.BufferGeometry(); rg.setAttribute('position', new T.BufferAttribute(pos, 3));
  o.rain = new T.LineSegments(rg, new T.LineBasicMaterial({ color: 0x9fb4c8, transparent: true, opacity: .38 })); o.rain.frustumCulled = false; S.add(o.rain);
  // молния: вспышка неба
  o.flash = 0;
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  // предметы-«нормы»: сюжет может их тихо менять (куртка пропала, часы встали, фото на пропуске исцарапано)
  o.props = (P = PROPS) => {
    // лужи растут за ночь: в 21:00 дождь только начался — вода в самых низких местах; к часу ночи — разлилась (P.night — минут с 21:00; нет — средне)
    if (o.pud) { const w = .15 + .85 * clamp((P.night ?? 120) / 240, 0, 1); o.pud.material.alphaTest = .85 * (1 - .9 * w); }
    o.jacket.visible = !!P.jacket; o.badge.visible = !!P.badge;
    o.badgeCard.material = mat(0xffffff, P.badge === 'scratched' ? TX.badgeX : TX.badge, { decal: 1 });
    o.note.material = mat(0xffffff, P.log === 2 ? TX.note2 : TX.note, { decal: 1 });
    o.book.visible = !!P.book; o.note.visible = o.noteCv.visible = !P.book;   // до 02:50 журнал закрыт и перевязан (engine.js: propsNow)
    if (P.fphone) o.fphone.visible = true;   // телефон Фрэнка Итан поставил на зарядку (21:06) — так и лежит на столе с проводом
    const [h, m] = String(P.clock || (o.dawn ? '06:00' : '02:57')).split(':').map(Number); o.setClock(h, m || 0);
  };
  o.reset = () => {
    o.faceL.intensity = 0; o.rain.material.opacity = .38; o.boothPane.visible = false; o.chair.position.set(5.2, 0, -.99); if (o.ch2) o.ch2.position.set(6.25, 0, -.62); o.boothDoor.rotation.y = 0; o.boothHandle.rotation.z = 0; o.bPalm.visible = o.bSmear.visible = o.bFog.visible = false;
    o.book.visible = o.saltPack.visible = o.saltGr.visible = o.fphone.visible = false; o.note.visible = o.noteCv.visible = true; o.drawer.position.z = -.44;
    if (o.fbRest) o.fbRest();   // журнал, пачка соли и телефон Фрэнка — на свои места (в осмотре будки их берёт в руки Итан: boothFb)
    for (const l of o.lifts) setLift(o, l, 0, '1');
    o.cabLight.intensity = 0; relabel(o.cabInd, '1');
    o.lamp.intensity = 1.6; o.lampBulb.visible = true; o.boothLight.intensity = .6; o.l1.intensity = 1.9; o.amb.color.set(0x6a7a90); o.amb.intensity = CINE ? .25 : .34; o.hemi.intensity = CINE ? .07 : .1; o.l3.intensity = 1.3; o.l4.intensity = 1.25;
    o.canopy.intensity = 1.5; o.canopyLamp.visible = true;
    o.sky.material.map = TX.sky; S.fog.color.set(FOGC); S.fog.density = o.fogD; S.background.set(FOGC); o.street.intensity = 1.2; o.rain.visible = true;
    o.dawn = 0; o.tubeOK = 1; o.flickOn = true; o.fac.material = o.facLit; o.ghostWin.visible = false; o.ghostWin.scale.setScalar(1); o.cat.visible = false; if (o.nmask) o.nmask.forEach(m => (m.visible = false));
    o.streetBulb.visible = true; o.wetM.opacity = .42; o.wetM.color.setHex(0x101418); o.palm.position.set(-3.5, 0, -5.42); o.palmShadow.visible = true; o.ceilOn.color.setHex(0xe8f0e0);
    resetFacade(o); o.props();
  };
  o.tick = (t, dt) => {
    const p = o.rain.geometry.attributes.position.array;
    for (let i = 0; i < o.drops.length; i++) { const d = o.drops[i]; d[1] -= d[3] * dt; if (d[1] < 0) d[1] += d[4] ? 3.3 : 12; p[i * 6] = d[0]; p[i * 6 + 1] = d[1]; p[i * 6 + 2] = d[2]; p[i * 6 + 3] = d[0] + .03; p[i * 6 + 4] = d[1] - .35; p[i * 6 + 5] = d[2]; }
    o.rain.geometry.attributes.position.needsUpdate = true;
    // ненадёжная лампа холла (у левой колонны)
    const fl = o.flickOn ? unreliable(t, 1) : 0; o.tubeOK = fl; o.flick.material.color.setHex(fl ? 0xe8f0e0 : 0x30322e); o.l2.intensity = .9 * fl;
  };
  return o;
}
function setLift(o, l, open, floor, up = 0) {
  l.open = open; const e = open * .6;
  const w = Math.min(.62, .74 - e); l.dl.scale.x = l.dr.scale.x = w / .62; l.dl.position.x = l.x - e - w / 2; l.dr.position.x = l.x + e + w / 2;   // 20 ноя: открытая створка уходит за стойку портала (выезжала плитой на стену рядом с лифтом); закрытая — как была
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
    float fr = .14 + .56 * pow(max(0., 1. - abs(V.z)), 3.);
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
      vec2 dq = (cf - c0) * vec2(1., 1.18 + .25 * h1(ci + 2.2)); float wob = 1. + .12 * sin(atan(dq.y, dq.x + 1e-5) * 3. + h1(ci) * 6.28);   // atan(0, 0) не определён (на части видеокарт — NaN, чёрная точка в центре капли)
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
#if __VERSION__ >= 300
    if (isnan(gl_FragColor.r + gl_FragColor.g + gl_FragColor.b + gl_FragColor.a)) gl_FragColor = vec4(0.);   // битая точка стекла — прозрачная, а не чёрная
#endif
  }`;
function buildFacade(o, S) {
  const U = o.glassU = { tRef: { value: null }, uTM: { value: new T.Matrix4() }, uRef: { value: 1 }, uLit: { value: 1 }, uT: { value: 0 }, tGrime: { value: TX.grime },
    uFogD: { value: 0 }, uPrint: { value: 0 }, uPrintP: { value: new T.Vector2(.41, 1.33) }, uRain: { value: 1 }, uDropC: { value: new T.Color(.55, .6, .68) } };
  const gm = new T.ShaderMaterial({ uniforms: U, vertexShader: GLASS_VS, fragmentShader: GLASS_FS, transparent: true, depthWrite: false, side: T.DoubleSide,
    blending: T.CustomBlending, blendSrc: T.OneFactor, blendDst: T.OneMinusSrcAlphaFactor, extensions: { derivatives: true } });
  // стекло фасада — без проёма дверей (у створок своё стекло, оно ходит вместе с ними)
  o.glass = new T.Mesh(new T.ShapeGeometry(new T.Shape([[-8, -1.68], [-1, -1.68], [-1, .58], [1, .58], [1, -1.68], [8, -1.68], [8, 1.68], [-8, 1.68]].map(([x, y]) => new T.Vector2(x, y)))), gm);
  o.glass.position.set(0, 1.78, 6); S.add(o.glass);
  o.glass.userData.solid = [[-4.5, 0, 0, 7, 3.36, .02, 'стекло фасада'], [4.5, 0, 0, 7, 3.36, .02, 'стекло фасада'], [0, 1.13, 0, 2, 1.1, .02, 'стекло фасада']];   // твёрдое — кроме проёма дверей (у створок своё стекло)
  // пар от дыхания Тёмыча на стекле изнутри (её сторона остаётся чистой — улика)
  o.breath = plane(.2, .1, new T.MeshBasicMaterial({ map: TX.fogBreath, transparent: true, opacity: 0, depthWrite: false, color: 0xc8d0d8 }), S);
  o.breath.rotation.y = PI; o.breath.position.set(.6, 1.4, 5.992); o.breath.visible = false; o.breath.userData.solid = false;
  // запотевшее стекло изнутри (у двери, на уровне лица): по нему Итан выводит пальцем «Как звали собаку?» — сквозь буквы видно улицу
  { const c = document.createElement('canvas'); c.width = 512; c.height = 256; const tx = new T.CanvasTexture(c); tx.colorSpace = T.SRGBColorSpace;
    o.fogW = plane(.8, .4, ps1(new T.MeshBasicMaterial({ map: tx, transparent: true, depthWrite: false, color: 0x6a7278 })), S); o.fogW.rotation.y = PI; o.fogW.position.set(.62, 1.42, 5.988); o.fogW.visible = false; o.fogW.userData.solid = false;
    o.fogW.userData = { c, x: c.getContext('2d'), tx, k: -9, solid: false }; }
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
  const lg = plane(1.9, .36, new T.MeshLambertMaterial({ map: TX.logo, transparent: true, depthWrite: false, side: T.DoubleSide, emissive: 0x15181b }), S); lg.position.set(0, 2.72, 6.004); ps1(lg.material); lg.userData.solid = false;
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
    const st = plane(.13, .065, mat(0xffffff, TX.sticker, { transparent: true }), lf); st.rotation.y = PI; st.position.set(X(s * .55), 1.22, -.005); st.userData.solid = false;
    const g = new T.Mesh(new T.PlaneGeometry(.9, 2.02), gm); g.position.set(cx, 1.28, 0); lf.add(g); o.leafGlass.push(g);
    if (s < 0) { const lk = cyl(.022, .022, .03, 12, mat(0xb8bcc0, null, { metal: .9, rough: .3 }), lf); lk.rotation.x = PI / 2; lk.position.set(X(-.1), 1.05, -.06); }
  }
  // 21 ноя (тестер и текст x01: «аварийный выход — в конце служебного коридора»; раньше Итан выбегал в парадные стеклянные двери): левее витрины — глухая
  // бетонная стена служебного крыла и в ней железная аварийная дверь (открывается наружу, изнутри — штанга «нажми»), над ней табличка ВЫХОД и лампа в колпаке.
  // За дверью — тёмный служебный коридор. Створка — o.exitLeaf (петли со стороны x = −9.78), угол ставит x_out
  { const cM = mat(0x55585c, TX.wall || null), stM = mat(0x5b6066, null, { metal: .55, rough: .5 }), frM2 = mat(0x3d4146, null, { metal: .5, rough: .55 });
    const W0 = -11.2, W1 = -8.0, DX = -9.3, DW = .95, DH = 2.1;
    box(DX - DW / 2 - .06 - W0, 3.6, .3, cM, (W0 + DX - DW / 2 - .06) / 2, 1.8, 6.15, S);   // стена слева от двери
    box(W1 - (DX + DW / 2 + .06), 3.6, .3, cM, (W1 + DX + DW / 2 + .06) / 2, 1.8, 6.15, S);   // справа — до витрины
    box(DW + .12, 3.6 - DH - .06, .3, cM, DX, DH + .06 + (3.6 - DH - .06) / 2, 6.15, S);   // над дверью
    for (const sx of [-1, 1]) box(.06, DH + .06, .32, frM2, DX + sx * (DW / 2 + .03), (DH + .06) / 2, 6.15, S);   // коробка
    box(DW + .12, .06, .32, frM2, DX, DH + .03, 6.15, S);
    box(DW + .3, .04, .5, mat(0x6a6d70), DX, .02, 6.5, S).userData.solid = false;   // бетонный порожек
    const dark = box(1.6, 2.4, 3.0, mat(0x08090a), DX, 1.2, 4.55, S); dark.material.side = T.BackSide; dark.userData.solid = false;   // служебный коридор за дверью
    const lf = o.exitLeaf = new T.Group(); lf.position.set(DX - DW / 2, 0, 6.27); S.add(lf); lf.userData.solid = false;
    box(DW - .02, DH - .02, .05, stM, DW / 2, DH / 2, 0, lf);
    box(DW - .2, .05, .05, mat(0x9aa0a6, null, { metal: .8, rough: .35 }), DW / 2 + .02, 1.0, -.06, lf);   // штанга изнутри
    for (const d of [.12, DW - .1]) box(.04, .1, .06, mat(0x9aa0a6, null, { metal: .8, rough: .35 }), d, 1.0, -.04, lf);
    box(.3, .06, .07, mat(0x3a3d40), DW / 2, DH - .12, -.06, lf);   // доводчик
    for (const y of [.3, 1.0, 1.8]) box(.03, .12, .06, frM2, .01, y, .02, lf);   // петли
    const sg = plane(.42, .2, mat(0xffffff, TX.exit, { basic: true }), S); sg.position.set(DX, DH + .38, 6.305); sg.userData.solid = false;   // ВЫХОД
    const lp = cyl(.09, .11, .1, 10, mat(0xfff1c8, null, { basic: true }), S); lp.position.set(DX, DH + .7, 6.38); lp.userData.solid = false;
    o.exitLamp = new T.PointLight(0xffe8c0, 1.4, 4.5, 2); o.exitLamp.position.set(DX, DH + .55, 6.7); S.add(o.exitLamp); }
}
function resetFacade(o) {
  const U = o.glassU; U.uRef.value = 1.7; U.uPrint.value = 0; U.uRain.value = 1; o.breath.visible = false; o.glass.visible = true; o.leaves.forEach(l => (l.rotation.y = 0)); if (o.exitLeaf) o.exitLeaf.rotation.y = 0; o.reflBoost = 1; if (o.fogW) o.fogW.visible = false; if (o.wetSteps) o.wetSteps.visible = false; if (o.lockKey) o.lockKey.visible = false;
}

// экран монитора охраны: почти ч/б и зеленоватый, строки и затемнённые углы кинескопа; в «Кино» ярче белого — светится в темноте будки
function crt(m) {
  const prev = m.onBeforeCompile;
  m.onBeforeCompile = (sh, r) => { if (prev) prev(sh, r); sh.fragmentShader = sh.fragmentShader.replace('#include <map_fragment>', `#include <map_fragment>
    #ifdef USE_MAP
    { float l = dot(diffuseColor.rgb, vec3(.299, .587, .114)); vec2 q = vMapUv - .5;
      diffuseColor.rgb = mix(vec3(l), diffuseColor.rgb, .3) * vec3(.8, 1., .88) * (1. - dot(q, q) * 1.5) * (.84 + .16 * sin(vMapUv.y * 452.4)) * ${CINE ? '1.45' : '1.1'}; }
    #endif`); };
  m.customProgramCacheKey = () => 'crt'; return m;
}
/* ---------- 7 этаж: длинный коридор, двери, лампы, кабинет 713 ---------- */
function buildCorridor() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .05; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x4a5868, .36); S.add(o.amb);
  const len = 26, wd = 2.6;
  // пол — офисный линолеум с лёгким бликом (по нему ходит швабра уборщика)
  const flM = ps1(CINE ? litMat({ color: 0xffffff, map: TX.lino, bumpMap: TX.lino.bumpTex || null, bumpScale: 1.6 }, .3) : new T.MeshPhongMaterial({ color: 0xffffff, map: TX.lino, specular: 0x1e1e1e, shininess: 30 }));
  const fl = plane(wd, len, flM, S); fl.rotation.x = -PI / 2; fl.position.z = -len / 2; o.flM = flM;
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
  // второй проём слева — кабинет (владелец, 18 ноя: уборщик заходит в кабинет — в дверь, а не сквозь стену); камера видит левую стену лицом
  const D5 = o.D5 = -8.7, lA = D5 + dw / 2, lB = D5 - dw / 2;
  wall(-1, 0, lA, 0, dh, wm); wall(-1, lB, zA, 0, dh, wm); wall(-1, zB, -len, 0, dh, wm);
  wall(-1, 0, lA, dh, 3, wm); wall(-1, lA, lB, dh, 3, wm); wall(-1, lB, zA, dh, 3, wm); wall(-1, zA, zB, dh, 3, wm); wall(-1, zB, -len, dh, 3, wm);
  wall(-1, 0, lA, 0, .9, pm); wall(-1, lB, zA, 0, .9, pm); wall(-1, zB, -len, 0, .9, pm);
  const end = plane(wd, 3, wm, S); end.position.set(0, 1.5, -len);
  const st = plane(wd, 3, wm, S); st.rotation.y = PI; st.position.set(0, 1.5, 0);
  aoEdge(S, len, wd / 2, -len / 2, -PI / 2, 3); aoEdge(S, len, -wd / 2, -len / 2, PI / 2, 3); aoEdge(S, wd, 0, -len, 0, 3);
  // комната за дверью 707: почти чёрная
  const rmM = mat(0x0c0c0d); const rm = room(3, 3, 3, rmM, S); rm.position.set(-wd / 2 - 1.5, 1.5, D7);
  const trM = mat(0x3a2a20);
  // наличники 707 и 705 — в общей обвязке дверей ниже (o.trim): там же остались их коллайдеры
  box(.14, dh, .04, trM, -wd / 2 - .07, dh / 2, zA - .02, S);   // торец коробки, за который хватаются пальцы
  o.door7 = new T.Group(); o.door7.position.set(-wd / 2 - .02, 0, zB); S.add(o.door7);
  doorHW(box(.05, dh - .02, dw - .03, mat(0x5c5c5c, TX.door), -.025, (dh - .02) / 2, (dw - .03) / 2, o.door7), 1, .025, 1, .38, .45);   // створка внутри тёмной комнаты — в тени; 20 ноя: ручка и петли, как у остальных (была гладкая плашка)
  // кабинет слева: створка на петлях открывается внутрь, за ней — комната со столом, шкафом и настольной лампой (лампа горит, только когда там Гас)
  { const tr5 = mat(0x3a2a20), X = -wd / 2;
    o.door5 = new T.Group(); o.door5.position.set(X + .03, 0, lA); S.add(o.door5);   // петли — с ближней к камере стороны: открытая створка не загораживает комнату
    { const lf5 = box(.06, dh - .02, dw - .03, mat(0xffffff, TX.door), 0, (dh - .02) / 2, -(dw - .03) / 2, o.door5); doorHW(lf5, 1, .03, -1, .38, .45); doorHW(lf5, -1, .03, -1, .38, .45); lf5.userData.solid = false; }   // створка — не преграда: Гас сам её толкает и тянет (твёрдая отпихивала его назад — «заваливался», тестер 20 ноя); закрытую держит стена   // ручки с обеих сторон: Гас берётся за них рукой (H5)
    const r5 = room(3, 3, 3, mat(0x8a8474, TX.wall), S); r5.position.set(X - 1.5, 1.5, D5);
    const wd5 = mat(0x5a4632, null, { rough: .7 }), gr5 = mat(0x4a4e52, null, { rough: .6, metal: .3 });
    box(.65, .05, 1.2, wd5, X - 2.3, .74, D5 + .55, S); for (const [dx, dz] of [[-.28, -.55], [.28, -.55], [-.28, .55], [.28, .55]]) box(.04, .72, .04, gr5, X - 2.3 + dx, .36, D5 + .55 + dz, S);   // стол
    box(.45, 1.6, .5, gr5, X - 2.6, .8, D5 - 1.05, S); for (let i = 0; i < 4; i++) { const dy = .2 + i * .4; rbox(.012, .36, .44, .004, gr5, X - 2.37, dy, D5 - 1.05, S).userData.solid = false; rbox(.02, .02, .14, .006, mat(0xb8bcc0, null, { rough: .3, metal: .9 }), X - 2.355, dy + .1, D5 - 1.05, S).userData.solid = false; }   // ящики с ручками; шкаф для бумаг
    { const mm2 = mat(0xc8c4b8, null, { rough: .5 }); rbox(.07, .3, .36, .02, mm2, X - 2.35, .93, D5 + .7, S); rbox(.2, .23, .27, .04, mm2, X - 2.48, .93, D5 + .7, S);   // старый монитор-«кинескоп»: рамка, выпуклый корпус, стекло, подставка
      const sc2 = plane(.3, .23, mat(0x101418, null, { rough: .1, metal: .3 }), S); sc2.position.set(X - 2.314, .935, D5 + .7); sc2.rotation.y = PI / 2; sc2.userData.solid = false;
      lathe([[0, 0], [.1, 0], [.1, .012], [.04, .03], [0, .03]], 12, mm2, S, X - 2.42, .765, D5 + .7).userData.solid = false; }   // старый монитор
    const lb = box(.08, .1, .08, mat(0xffe6b0, null, { basic: true, glow: 2 }), X - 2.15, 1.0, D5 + .2, S); o.lamp5Bulb = lb; lb.visible = false;
    o.room5L = new T.PointLight(0xffd9a0, 0, 6.5, 1.3); o.room5L.position.set(X - 1.6, 1.6, D5 + .3); S.add(o.room5L);
    o.open5 = k => { o.door5.rotation.y = 1.45 * k; };   // внутрь кабинета
  }
  // двери по бокам с табличками
  // таблички с номерами: один коридор служит двумя этажами (7 и 10) — номера меняются (o.setFloor)
  o.plates = { 7: new T.Group(), 10: new T.Group() }; S.add(o.plates[7], o.plates[10]); o.plates[10].visible = false;
  let n = 1;
  for (let z = -2.5; z > -len + 2; z -= 3.1) for (const s of [-1, 1]) {
    if (!(s === -1 && Math.abs(z - D7) < .01) && !(s === -1 && Math.abs(z - D5) < .01)) { const d = box(.06, 2.1, .95, mat(0xffffff, TX.door), s * (wd / 2 - .03), 1.05, z, S); if (s === 1 && Math.abs(z + 5.6) < .01) o.doorR10 = d;
      doorHW(d, -s, .03, s, .38, .45); }   // 19 ноя: нажимная ручка на накладке с цилиндром замка и петли — объёмные (ручка была только нарисована)
    const i = n++;
    for (const fl of [7, 10]) { const num = fl * 100 + i; const pl = label(.2, .08, 32, 12, (c, w, h) => { c.fillStyle = '#c9b27a'; c.fillRect(0, 0, w, h); c.fillStyle = '#1b1b1b'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(num), w / 2, h / 2 + 1); }, o.plates[fl], false);
      pl.position.set(s * (wd / 2 - .011), 1.75, z - .72); pl.rotation.y = -s * PI / 2; }   // на стене сразу за наличником (висела в воздухе в 6 см от стены)
  }
  // обвязка дверей (20 ноя): двери были плашками на плоскости стены. Полотно выступает из стены на 6 см — вокруг него коробка (выше полотна на 12 мм: полотно
  // сидит в ней утопленным), за ней ступенькой наличник, тёмная щель притвора, на полу накладка порога. У 705 — ещё откосы в толщину стены (видны, когда
  // створка открыта) и добор со стороны замка (между створкой и стеной была щель 3 см в комнату). 707 открывается внутрь и стоит за плоскостью стены —
  // у неё плоский наличник (по нему ложатся пальцы). 713 в торце — так же, с ручкой и петлями. Всё склеено: одна деталь на материал; не преграда,
  // кроме прежних коллайдеров наличников 705 и 707
  { const tg = o.trim = new T.Group(); S.add(tg);
    tg.userData.solid = [[D7, zA, zB], [D5, lA, lB]].flatMap(([zc, a, b]) => [[-wd / 2 + .015, (dh + .07) / 2, a + .035, .03, dh + .07, .07, 'наличник'], [-wd / 2 + .015, (dh + .07) / 2, b - .035, .03, dh + .07, .07, 'наличник'], [-wd / 2 + .015, dh + .035, zc, .03, .07, dw + .14, 'наличник']]);
    const TM = { fr: trM, gap: mat(0x0b0908, null, { rough: .9 }), sill: mat(0x8a8e92, null, { metal: .7, rough: .4 }) };
    for (let z = -2.5; z > -len + 2; z -= 3.1) for (const s of [-1, 1]) { const g = new T.Group(); g.position.set(s * wd / 2, 0, z); g.rotation.y = -s * PI / 2; tg.add(g);
      if (s === -1 && Math.abs(z - D7) < .01) { portal([[0, 0], [0, .03], [.05, .03], [.054, .024], [.108, .016], [.112, 0]], -dw / 2, dw / 2, dh, TM.fr, g); continue; }
      const is5 = s === -1 && Math.abs(z - D5) < .01;
      doorTrim(g, -dw / 2, is5 ? dw / 2 - .03 : dw / 2, is5 ? dh - .02 : dh, .06, TM);
      if (is5) for (const b of [box(.03, dh, .12, TM.fr, dw / 2 - .015, dh / 2, -.06, g), box(.012, dh, .12, TM.fr, -dw / 2 + .006, dh / 2, -.06, g), box(dw, .02, .12, TM.fr, 0, dh - .01, -.06, g)]) b.userData.noShadow = true; }
    { const g = new T.Group(); g.position.set(0, 0, -len); tg.add(g); doorTrim(g, -.5, .5, 2.1, .06, TM, false);
      const pv = new T.Group(); pv.position.set(0, 1.05, -len + .03); pv.rotation.y = -PI / 2; pv.userData.solid = false; S.add(pv); doorHW(pv, 1, .03, -1, .4, .47); }
    // плинтус и рейка над панелями (стена сходилась с полом голым стыком): по обеим стенам и в торце, с разрывом в проёмах 705, 707 и двери, что открывается на 10-м; за остальными дверями прячутся под наличник
    const NSb = b => { b.userData.noShadow = true; return b; };
    for (const [s, a, b] of [[1, 0, -5.6 + dw / 2 + .112], [1, -5.6 - dw / 2 - .112, -len], [-1, 0, lA + .112], [-1, lB - .08, zA + .112], [-1, zB - .112, -len]]) for (const [y, h] of [[.04, .08], [.905, .035]]) NSb(box(.012, h, a - b, TM.fr, s * (wd / 2 - .006), y, (a + b) / 2, tg));
    for (const sx of [-1, 1]) NSb(box(wd / 2 - .612, .08, .012, TM.fr, sx * (wd / 2 + .612) / 2, .04, -len + .006, tg));
    bake(tg); }
  // номер этажа краской на торце у лифта (виден камере)
  o.floorSign = {}; for (const fl of [7, 10]) { const sg = label(.6, .5, 48, 40, (c, w, h) => { c.clearRect(0, 0, w, h); c.fillStyle = '#d8d4c6'; c.font = '800 30px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(String(fl), w / 2, h / 2 + 2); }, S, false);
    sg.material.alphaTest = .5; sg.rotation.y = PI; sg.position.set(.75, 1.75, -.012); sg.visible = fl === 7; o.floorSign[fl] = sg; }   // краска на стене: только цифры (без чёрной подложки), освещается лампами
  // десятый — не копия седьмого (владелец, 11 ноя): этаж пустует под ремонт. Двери затянуты плёнкой, на полу защитный картон, стремянка,
  // вёдра с краской, коробки съехавших арендаторов, шпаклёвка пятнами, снятые плитки потолка с проводами, дверь крест-накрест лентой.
  // Всё у стен (|x| > .8): посреди коридора стоят люди и стул.
  { const F = o.f10 = new T.Group(); S.add(F); F.visible = false; const NS = m => { m.userData.solid = false; return m; };
    const film = mat(0xc8d4d8, null, { transparent: true, opacity: .16, rough: .12, side: T.DoubleSide }), tape = mat(0x8c9094, null, { rough: .5 }), card = mat(0x9a7c54, null, { rough: .92 }), card2 = mat(0x84683f, null, { rough: .92 });
    for (const [sd, z] of [[-1, -8.7], [1, -8.7], [-1, -18], [1, -21.1]]) {   // плёнка на дверях: висит складками, прихвачена скотчем сверху
      for (let i = 0; i < 5; i++) { const p2 = NS(plane(.24, 2.25, film, F)); p2.position.set(sd * (wd / 2 - .078 - (i % 2) * .018), 1.03, z - .48 + i * .24); p2.rotation.y = -sd * PI / 2 + (i % 2 ? .22 : -.22); }
      NS(box(.012, .05, 1.0, tape, sd * (wd / 2 - .079), 2.125, z, F)); }   // скотч — по верхней перекладине коробки
    const paper = NS(plane(1.5, 19, mat(0xa08a66, null, { rough: .95, decal: 1 }), F)); paper.rotation.x = -PI / 2; paper.position.set(0, .004, -11.5);   // защитный картон по полу
    for (const z of [-5, -9.6, -14.2, -18.8]) { const st = NS(plane(1.5, .06, mat(0x6e6e70, null, { rough: .5, decal: 1 }), F)); st.rotation.x = -PI / 2; st.position.set(0, .006, z); }   // стыки листов — серый скотч
    const al = mat(0xa8acb0, null, { metal: .7, rough: .4 }); const ld = new T.Group(); ld.position.set(-.93, 0, -6.6); ld.rotation.y = .12; F.add(ld);   // стремянка
    for (const sx of [-1, 1]) { const a1 = box(.03, 1.75, .05, al, sx * .22, .84, .27, ld); a1.rotation.x = .3; const a2 = box(.03, 1.75, .04, al, sx * .2, .84, -.27, ld); a2.rotation.x = -.3; }
    for (let i = 0; i < 5; i++) box(.44, .025, .11, al, 0, .28 + i * .3, .43 - i * .087, ld); box(.5, .03, .26, mat(0x2a2c30, null, { rough: .6 }), 0, 1.68, 0, ld);
    const wht = mat(0xd8d6cc, null, { rough: .5 }), drip = mat(0xe8e6dc, null, { rough: .35 });   // вёдра краски, одно открыто, валик на крышке
    for (const [x, z, r] of [[-1.02, -4.2, .14], [-.86, -4.55, .14], [-1.04, -4.9, .11]]) { const b2 = cyl(r, r * .88, r * 1.9, 16, wht, F); b2.position.set(x, r * .95, z); cyl(r * .9, r * .9, .012, 16, drip, F).position.set(x, r * 1.9 - .03, z); }
    const rl = cyl(.028, .028, .22, 10, mat(0x58606a, null, { rough: .9 }), F); rl.rotation.z = PI / 2; rl.position.set(-.86, .3, -4.55); NS(box(.012, .012, .3, al, -.86, .3, -4.4, F));
    for (const [x, y, z, w, h, d, r, m] of [[.96, .2, -13.4, .5, .4, .6, .05, card], [.98, .57, -13.45, .42, .34, .5, -.12, card2], [.93, .17, -14.15, .44, .34, .44, .3, card2], [.99, .88, -13.4, .34, .26, .4, .2, card], [-.98, .19, -15.6, .46, .38, .56, -.08, card]]) {
      const bx = box(w, h, d, m, x, y, z, F); bx.rotation.y = r; const tp = NS(box(w + .004, .004, .06, mat(0xb8a070, null, { rough: .6 }), x, y + h / 2 + .002, z, F)); tp.rotation.y = r; }
    const spk = mat(0xc4c0b2, null, { rough: .95, decal: 1 });   // шпаклёвка: светлые заплаты на стенах
    for (const [sd, y, z, w, h] of [[-1, 1.5, -4.1, .7, .5], [-1, 2.1, -10.2, 1.1, .35], [1, 1.3, -7.1, .5, .8], [1, 1.9, -16.4, .9, .6], [-1, 1.2, -20.2, .6, .9], [1, 2.3, -11.2, .4, .3]]) { const p2 = NS(plane(w, h, spk, F)); p2.position.set(sd * (wd / 2 - .012), y, z); p2.rotation.y = -sd * PI / 2; }
    const hole = mat(0x050505, null, { basic: 1 }), wire = mat(0x111111, null, { rough: .6 });   // снятые плитки потолка, из дыр свисают провода (у стены, выше голов)
    for (const [x, z] of [[-.75, -7.5], [.7, -15.2], [-.7, -22]]) { const h2 = NS(plane(.6, .6, hole, F)); h2.rotation.x = PI / 2; h2.position.set(x, 2.994, z);
      for (let i = 0; i < 3; i++) { const w2 = NS(cyl(.006, .006, .3 + i * .16, 5, wire, F)); w2.position.set(x + (i - 1) * .12, 3 - (.3 + i * .16) / 2, z + (i - 1) * .1); w2.rotation.z = (i - 1) * .12; } }
    const warn = mat(0xc8a018, null, { rough: .5, side: T.DoubleSide });   // сигнальная лента крест-накрест на двери справа
    for (const r of [-1, 1]) { const t2 = NS(plane(1.5, .07, warn, F)); t2.position.set(wd / 2 - .07, 1.1, -14.9); t2.rotation.y = -PI / 2; t2.rotation.z = r * .95; t2.rotation.order = 'YXZ'; }
  }
  o.setFloor = fl => { for (const k of [7, 10]) { o.plates[k].visible = k === fl; o.floorSign[k].visible = k === fl; } o.f10.visible = fl === 10; };
  // мелочи на стенах (коридор не голый): огнетушитель на кронштейне, шкаф пожарного крана, кнопка пожарной тревоги, выключатели у дверей
  { const red = mat(0x8a1414, null, { rough: .45 }), blk = mat(0x141414, null, { rough: .6 }), wht = mat(0xd8d6cc, null, { rough: .55 }), gl = mat(0x8fa0a8, null, { rough: .1, metal: .2 });
    const ext = new T.Group(); ext.position.set(wd / 2 - .11, 0, -16.45); S.add(ext);
    // 19 ноя: огнетушитель — настоящий, свой (makeExt) — висит на настенном кронштейне за горловину, хомут поперёк баллона; шкаф пожарного крана —
    // короб с рамкой, стеклом и ручкой, внутри катушка шланга и вентиль (были цилиндр и плоские коробки)
    ext.userData.solid = [[0, .66, 0, .17, .6, .17, 'огнетушитель']];
    rbox(.02, .5, .06, .006, blk, .1, .62, 0, ext); rbox(.09, .03, .05, .008, blk, .06, .86, 0, ext); rbox(.07, .025, .05, .008, blk, .065, .52, 0, ext);   // кронштейн: планка, крюк, упор
    { const eg2 = makeExt(new T.Group()); eg2.position.set(-.005, .37, 0); eg2.rotation.y = -PI / 2; ext.add(eg2); }
    const cab = new T.Group(); cab.position.set(-wd / 2 + .06, 1.25, -19.55); S.add(cab); cab.userData.solid = [[0, 0, 0, .12, .62, .5, 'пожарный шкаф']];
    { const cg2 = new T.Group(); cg2.rotation.y = PI / 2; cab.add(cg2);   // в осях cg2 лицо — +z (к коридору)
      rbox(.5, .62, .01, .003, mat(0x5a0e0e, null, { rough: .6 }), 0, 0, -.055, cg2); for (const s of [-1, 1]) { rbox(.014, .62, .12, .004, red, s * .243, 0, 0, cg2); rbox(.5, .014, .12, .004, red, 0, s * .303, 0, cg2); }
      for (const s of [-1, 1]) { rbox(.03, .62, .018, .006, red, s * .235, 0, .06, cg2); rbox(.5, .03, .018, .006, red, 0, s * .295, .06, cg2); }
      const gp2 = plane(.44, .56, mat(0xb8d0e0, null, { transparent: true, opacity: .16, rough: .05 }), cg2); gp2.position.z = .058; gp2.renderOrder = 2;
      rbox(.012, .06, .016, .004, mat(0xb8bcc0, null, { rough: .25, metal: .9 }), .21, 0, .076, cg2);
      for (let i = 0; i < 4; i++) { const hr = tor(.15 - i * .022, .014, mat(0xd8d2c0, null, { rough: .85 }), cg2, PI * 2, 6, 22); hr.position.set(-.02, -.02, -.02 + (i % 2) * .012); }   // скатка шланга
      lathe([[0, 0], [.03, 0], [.03, .01], [.012, .016], [0, .016]], 10, mat(0xb03020, null, { rough: .4 }), cg2, .17, .2, -.04).rotation.x = PI / 2;   // маховик вентиля
      const lbl = label(.3, .04, 60, 8, (c, w, h) => { c.fillStyle = '#ece8dc'; c.fillRect(0, 0, w, h); c.fillStyle = '#a01010'; c.fillRect(2, 2, 10, 4); c.fillRect(w - 12, 2, 10, 4); }, cg2, false); lbl.position.set(0, .29, .071); lbl.userData.solid = false; }
    box(.04, .1, .1, red, -wd / 2 + .02, 1.45, -7.15, S); box(.01, .06, .06, wht, -wd / 2 + .041, 1.45, -7.15, S);   // кнопка тревоги
    for (let z = -2.5; z > -len + 2; z -= 3.1) for (const sd of [-1, 1]) { if (sd === -1 && Math.abs(z - D7) < .01) continue; box(.012, .08, .05, wht, sd * (wd / 2 - .006), 1.08, z + .68 * sd, S); } }
  // офис на 10-м: одна дверь бывает приоткрыта (смена) — створка поворачивается внутрь, за ней свет или темнота
  o.door10 = new T.Group(); o.door10.position.set(wd / 2 - .03, 0, -5.6 - .475); S.add(o.door10); o.door10.visible = false;
  doorHW(box(.05, 2.1, .95, mat(0xffffff, TX.door), 0, 1.05, .475, o.door10), -1, .025, 1, .38, .45);   // у приоткрытой створки та же ручка и петли, что у закрытой (пропадали вместе с ней)
  o.hole10 = plane(.95, 2.1, mat(0x050506, null, { basic: true }), S); o.hole10.rotation.y = -PI / 2; o.hole10.position.set(wd / 2 - .004, 1.05, -5.6); o.hole10.visible = false; o.hole10.userData.solid = false;   // тёмный проём за створкой
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
  o.wd = wd; o.mopProp = makeMop(); S.add(o.mopProp);
  // офисное кресло: на 10-м ночью оказывается посреди коридора (странность смены)
  o.chair10 = new T.Group(); S.add(o.chair10); o.chair10.visible = false;
  { const cm = mat(0x5a5d64, null, { rough: .7 }), mm = mat(0x3a3c40, null, { metal: .6, rough: .4 }), wm = mat(0x0c0c0c);
    // 19 ноя: собрано как настоящее кресло (officeChair) с подлокотниками; коллайдеры — прежние коробки
    o.chair10.userData.solid = [[0, .5, 0, .5, .08, .48, 'кресло'], [0, .86, -.24, .46, .58, .07, 'кресло'], [0, .28, 0, .05, .4, .05, 'кресло'], [-.26, .7, -.02, .05, .04, .3, 'кресло'], [.26, .7, -.02, .05, .04, .3, 'кресло']];
    officeChair(o.chair10, .5, .5, .86, .58, -.24, cm);
    for (const sd of [-1, 1]) { rbox(.06, .035, .26, .015, mat(0x1a1b1d, null, { rough: .6 }), sd * .26, .7, -.02, o.chair10); rbox(.03, .2, .04, .008, mm, sd * .26, .59, -.06, o.chair10); } }
  o.sign = new T.Group(); S.add(o.sign); o.sign.userData.solid = [[0, .3, 0, .3, .6, .34, 'табличка «мокрый пол»']];   // домик из двух пластин — сквозь него не пройти
  for (const s of [-1, 1]) { const pnl = plane(.3, .62, mat(0xffffff, TX.wetSign, { side: T.DoubleSide }), o.sign); pnl.position.set(0, .3, s * .085); pnl.rotation.x = s * -.28; pnl.rotation.y = s < 0 ? PI : 0;
    rbox(.31, .63, .012, .005, mat(0xe0b414, null, { rough: .45 }), 0, 0, -.0075, pnl); }   // 19 ноя: у пластин есть толщина — жёлтый пластик со скруглёнными краями (были листы бумаги)
  { const sm2 = mat(0xe0b414, null, { rough: .45 }); rbox(.3, .035, .06, .015, sm2, 0, .615, 0, o.sign); rbox(.12, .018, .03, .008, mat(0x1a1a1a, null, { rough: .6 }), 0, .64, 0, o.sign); }   // шарнир-ручка сверху
  propGlb('wet_sign', o.sign, { h: .64 });
  o.wet = new T.Mesh(new T.PlaneGeometry(2.2, 3.2), ps1(new T.MeshLambertMaterial({ color: 0x0e0e10, map: TX.puddle, transparent: true, opacity: .4, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2 })));
  o.wet.rotation.x = -PI / 2; S.add(o.wet);
  o.sheen = new T.Mesh(new T.PlaneGeometry(1.6, 3.2), ps1(new T.MeshBasicMaterial({ map: TX.sheen, transparent: true, opacity: .5, depthWrite: false, blending: T.AdditiveBlending, polygonOffset: true, polygonOffsetFactor: -3 })));
  o.sheen.rotation.x = -PI / 2; o.wet.add(o.sheen); o.sheen.rotation.x = 0; o.sheen.position.z = .001;
  o.spill = new T.Mesh(new T.PlaneGeometry(1.1, .9), o.wet.material); o.spill.rotation.x = -PI / 2; S.add(o.spill);
  o.smear = new T.Mesh(new T.PlaneGeometry(.36, 2.4), ps1(new T.MeshLambertMaterial({ map: TX.smear, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -3 })));
  o.smear.rotation.order = 'YXZ'; o.smear.rotation.x = -PI / 2; S.add(o.smear);
  // дверь 713 в торце, полоска света под ней
  box(1, 2.1, .06, mat(0xffffff, TX.door), 0, 1.05, -len + .03, S);
  const p7 = label(.24, .1, 32, 12, (c, w, h) => { c.fillStyle = '#c9b27a'; c.fillRect(0, 0, w, h); c.fillStyle = '#5a0a0a'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('713', w / 2, h / 2 + 1); }, S, false); p7.position.set(0, 1.78, -len + .07);
  o.slit = box(.95, .025, .02, mat(0xfff0c0, null, { basic: true, glow: 2 }), 0, .012, -len + .07, S);
  o.slitLight = new T.PointLight(0xffe0a0, .7, 2.5, 2); o.slitLight.position.set(0, .15, -len + .4); S.add(o.slitLight);
  // лампы под потолком
  o.lamps = [];
  for (let i = 0; i < 7; i++) {
    const z = -1.8 - i * 3.6;
    const m = new T.MeshBasicMaterial({ color: 0xe8f0e8 }); ps1(m); glow(m, 3);
    const tube = box(.9, .05, .25, m, 0, 2.97, z, S);
    { const hm = mat(0xc8ccd0, null, { rough: .35, metal: .6 }); rbox(1.0, .035, .31, .01, hm, 0, 2.985, z, S).userData.solid = false; for (const s of [-1, 1]) rbox(.03, .06, .27, .008, hm, s * .465, 2.965, z, S).userData.solid = false; }   // корпус светильника с торцевыми крышками
    const li = new T.PointLight(0xd8e8e0, 0, 7, 1.3); li.position.set(0, 2.7, z); S.add(li); li.userData.beamTop = 2.95;
    o.lamps.push({ tube, li, on: 0 });
  }
  // двери лифта в начале коридора (за спиной камеры) и зелёная табличка
  const ex = plane(.72, .18, mat(0xffffff, TX.stairs, { basic: true }), S); ex.position.set(0, 2.55, -len + .012); ex.position.x = .9;   // на стене (висела в 8 см от неё)
  // табличка «СЕРВЕРНАЯ» поверх 713 — этот же коридор играет минус второй этаж
  o.srv = label(.5, .12, 96, 22, (c, w, h) => { c.fillStyle = '#7a7d80'; c.fillRect(0, 0, w, h); c.fillStyle = '#111'; c.font = '700 13px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(tr('СЕРВЕРНАЯ  −2'), w / 2, h / 2 + 1); }, S); o.srv.position.set(0, 1.78, -len + .09);
  // под фонарём табличка видна, только когда на неё светят (обычная — светится сама)
  o.srvBasic = o.srv.material; o.srvLit = ps1(new T.MeshLambertMaterial({ map: o.srv.material.map, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }));
  // лужа под телом (сцена «Жилец ест»)
  // лужа неровная: край — сумма синусов, плюс отдельные брызги (круг выглядел как красный коврик)
  const bl = new T.Shape(); for (let i = 0; i <= 48; i++) { const a = i / 48 * PI * 2, r = .82 * (1 + .22 * Math.sin(3 * a + 1) + .13 * Math.sin(7 * a + 2.3) + .07 * Math.sin(13 * a + .4));
    i ? bl.lineTo(Math.cos(a) * r, Math.sin(a) * r) : bl.moveTo(Math.cos(a) * r, Math.sin(a) * r); }
  const bg = [new T.ShapeGeometry(bl)]; for (const [x, y, r] of [[1.05, .35, .09], [-.95, -.5, .06], [.4, -1.02, .07], [1.25, -.2, .045], [-.3, .98, .05]]) bg.push(new T.CircleGeometry(r, 16).translate(x, y, 0));
  o.blood = new T.Mesh(T.BufferGeometryUtils ? T.BufferGeometryUtils.mergeGeometries(bg) : bg[0], CINE ? new T.MeshStandardMaterial({ color: 0x240202, roughness: .14, envMapIntensity: .8, polygonOffset: true, polygonOffsetFactor: -4 }) : new T.MeshLambertMaterial({ color: 0x2a0303, polygonOffset: true, polygonOffsetFactor: -4 }));   // «Кино»: кровь мокрая — в луже блестят лампы
  if (!T.BufferGeometryUtils) for (const g2 of bg.slice(1)) o.blood.add(new T.Mesh(g2, o.blood.material)); o.blood.rotation.x = -PI / 2; o.blood.scale.set(1, 1.6, 1); o.blood.position.set(0, .006, -12); S.add(o.blood);
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.irL = new T.PointLight(0xffffff, 0, 16, 1.2); S.add(o.irL);   // ИК-подсветка камеры смены (темно — камера видит в ч/б)
  // старое новогоднее фото (Фрэнк, Гас и Марта на седьмом): гирлянда по стенам, мишура
  o.garland = new T.Group(); S.add(o.garland); o.garland.visible = false;
  { const cols = [0xff3030, 0xffd040, 0x40c060, 0x4080ff, 0xff60c0].map(c => mat(c, null, { basic: true })), tin = mat(0xc8a030, null, { emissive: 0x3a2a08, metal: .7, rough: .3 });
    for (const sx of [-1, 1]) for (let i = 0; i <= 28; i++) { const u = i / 28, z = lerp(-3.6, -11.2, u), y = 2.42 - .2 * Math.abs(Math.sin(u * PI * 4));
      box(.03, .04, .03, cols[(i + (sx > 0 ? 2 : 0)) % 5], sx * 1.27, y, z, o.garland); box(.012, .012, .3, tin, sx * 1.285, y + .03, z, o.garland); } }
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
  // брызги — как от удара: рваное пятно, вытянутые капли от центра с «хвостиками», потёки вниз с каплей на конце; цвет тёмный, к краям — запёкшийся
  const spT = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h); const cx = 30, cy = 26, col = a => `rgba(${48 + rnd() * 30 | 0},${2 + rnd() * 4 | 0},${3 + rnd() * 4 | 0},${a})`;
    for (let i = 0; i < 70; i++) { const a = rnd() * 6.283, d = Math.pow(rnd(), 1.8) * 9; x.fillStyle = col(.75 + rnd() * .25); x.beginPath(); x.arc(cx + Math.cos(a) * d * 1.3, cy + Math.sin(a) * d, 1.2 + rnd() * 2.6, 0, 7); x.fill(); }   // ядро
    for (let i = 0; i < 38; i++) { const a = rnd() * 6.283, d = 9 + Math.pow(rnd(), .7) * 22, L = 1 + rnd() * 3.5, r = .4 + rnd() * 1.3;   // разлёт: капля вытянута от центра, хвостик назад
      x.save(); x.translate(cx + Math.cos(a) * d * 1.25, cy + Math.sin(a) * d * .9); x.rotate(a); x.fillStyle = col(.7 + rnd() * .3);
      x.beginPath(); x.ellipse(0, 0, r * (1 + L * .4), r, 0, 0, 7); x.fill(); x.fillRect(-r * L * 1.6, -r * .3, r * L * 1.6, r * .6); x.restore(); }
    for (let i = 0; i < 9; i++) { let px = cx - 8 + rnd() * 16, py = cy + 2 + rnd() * 5, wd = .8 + rnd() * 1.4; const len = 10 + rnd() * 26; x.fillStyle = col(.85);   // потёки
      for (let k = 0; k < len; k++) { x.fillRect(px - wd / 2, py + k, wd, 1.2); px += (rnd() - .5) * .35; wd = Math.max(.5, wd * (.985 + rnd() * .02)); }
      x.beginPath(); x.arc(px, py + len, wd * .9, 0, 7); x.fill(); } });
  o.splat = plane(1.1, 1.3, ps1(new T.MeshLambertMaterial({ map: spT, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 })), S);
  o.splat.rotation.y = PI / 2; o.splat.position.set(-wd / 2 + .004, 1.15, zA + .45); o.splat.visible = false;
  const _h = new T.Vector3(), _bb = new T.Vector3();
  o.post = (A, used, t) => {
    const M = A.monster, eat = used.has('monster') && M.pose === 'eat';
    const gut = !!(eat && used.has('victim') && o.gutsOn); o.guts.visible = false;   // бусины кишок заменены трубками (gutsDraw)
    if (o.bursts && used.has('victim')) { A.victim.bone.spine_03.getWorldPosition(_bb); o.sprayTick(_bb, o.sprayT || 0); } else o.spray.visible = false;
    if (gut) { const B = bodyPts(A.victim), h = M.bone.hand_r.getWorldPosition(new T.Vector3()), mo = mouthPt(M);   // из вспоротого живота — в когти, из когтей — в пасть; петля вывалилась на пол
      gutsDraw(o, [[B.belly, h, .12, .02], [h, mo, .05, .017], [B.belly, B.belly.clone().addScaledVector(B.lr, .32).setY(.05), .02, .022]], t); wound(A.victim, 'belly', 1); }
    else if (o.gutT) gutsDraw(o, [], t);
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
  o.reset = () => { o.door5.rotation.y = 0; o.room5L.intensity = 0; o.lamp5Bulb.visible = false; o.bursts = null; o.faceL.intensity = 0; o.garland.visible = false; if (o.flM.roughness != null) o.flM.roughness = .3; o.setFloor(7); o.ajar10(0); o.chair10.visible = false; o.blood.visible = false; o.blood.position.set(0, .006, -12); o.blood.scale.set(1, 1.6, 1); o.lamps[3].li.distance = 7; setLamps(o, 0); o.slit.visible = true; o.slitLight.intensity = 1.2; o.slitLight.color.setHex(0xffe0a0); o.amb.intensity = .36; o.srv.visible = false; o.srv.material = o.srvBasic; S.fog.density = o.fogD; o.flickOn = true; o.tubeOK = 1; o.cleanSet(); };
  o.tick = (t) => {   // одна ненадёжная лампа
    if (o.vendL) o.vendL.intensity = .62 + (Math.sin(t * 2.3) > .7 ? .18 * Math.sin(t * 37) : 0);   // лампа торгового автомата изредка дрожит (пятая от лифта), если горит
    const l = o.lamps[4]; if (!o.flickOn || !l.on) { o.tubeOK = 1; return; }
    const f = unreliable(t, 3); o.tubeOK = f; l.li.intensity = l.base * f; l.tube.material.color.setHex(f ? 0xe8f0e8 : 0x1c1e1c);
  };
  return o;
}
// ---- детали предметов (19 ноя, владелец: «кулер не проработан, просто текстурка — сделать это повсюду»): тела вращения, коробки со скруглёнными
// рёбрами, кабели. Геометрия кэшируется по размерам (PGEO) — одинаковые детали не плодят буферы. Lathe/Extrude/Tube не коллайдеры (solidCols):
// у собранного предмета коллайдер задаётся группе (userData.solid = [[x, y, z, w, h, d, имя]]), как раньше давали его коробки
const PGEO = {};
function lathe(pts, n, m, parent, x = 0, y = 0, z = 0) {   // pts: [[радиус, высота], …] снизу вверх
  const k = 'L' + n + '|' + pts.join(';'), g = PGEO[k] || (PGEO[k] = new T.LatheGeometry(pts.map(p => new T.Vector2(p[0], p[1])), n));
  const me = new T.Mesh(g, m); me.position.set(x, y, z); if (parent) parent.add(me);
  let rm = 0, y0 = 1e9, y1 = -1e9; for (const [r, yy] of pts) { rm = Math.max(rm, r); y0 = Math.min(y0, yy); y1 = Math.max(y1, yy); } if (Math.max(2 * rm, y1 - y0) < .06) me.userData.noShadow = true;   // мелочь тень не бросает (телефоны: меньше проходов теней)
  return me;
}
function rbox(w, h, d, r, m, x = 0, y = 0, z = 0, parent) {   // коробка w×h×d со скруглёнными рёбрами r: скруглённый прямоугольник, выдавленный с фаской (наружу ещё на r)
  const k = 'R' + [w, h, d, r].join(','); let g = PGEO[k];
  if (!g) { r = Math.min(r, w / 2.2, h / 2.2, d / 2.2); const a = w / 2 - r, b = h / 2 - r, c = Math.min(r, a, b) * .9, s = new T.Shape();
    s.moveTo(-a + c, -b); s.lineTo(a - c, -b); s.quadraticCurveTo(a, -b, a, -b + c); s.lineTo(a, b - c); s.quadraticCurveTo(a, b, a - c, b);
    s.lineTo(-a + c, b); s.quadraticCurveTo(-a, b, -a, b - c); s.lineTo(-a, -b + c); s.quadraticCurveTo(-a, -b, -a + c, -b);
    g = PGEO[k] = new T.ExtrudeGeometry(s, { depth: d - 2 * r, bevelEnabled: true, bevelThickness: r, bevelSize: r, bevelSegments: 2, curveSegments: 3 }); g.translate(0, 0, -(d - 2 * r) / 2); }
  const me = new T.Mesh(g, m); me.position.set(x, y, z); me.userData.solid = [[0, 0, 0, w, h, d]]; if (Math.max(w, h, d) < .06) me.userData.noShadow = true; if (parent) parent.add(me); return me;   // твёрдая, как коробка (внутри собранного предмета решает коллайдер группы)
}
function cable(pts, r, m, parent, n = 5) {   // шнур по точкам (плавная кривая); не преграда
  const c = new T.CatmullRomCurve3(pts.map(p => new T.Vector3(p[0], p[1], p[2]))), me = new T.Mesh(new T.TubeGeometry(c, Math.max(8, pts.length * 6), r, n, false), m);
  me.userData.solid = false; if (r < .01) me.userData.noShadow = true; if (parent) parent.add(me); return me;
}
function tor(R, r, m, parent, arc = PI * 2, ns = 6, nr = 18) { const k = 'T' + [R, r, arc, ns, nr], g = PGEO[k] || (PGEO[k] = new T.TorusGeometry(R, r, ns, nr, arc)); const me = new T.Mesh(g, m); if (R < .03) me.userData.noShadow = true; if (parent) parent.add(me); return me; }
// обвод проёма профилем — коробка и наличник одной деталью (20 ноя, владелец: «дверь должна стоять в помещении, а не быть прицеплена на стену»).
// Профиль [[u, v], …]: u — наружу от кромки проёма, v — от стены к зрителю; идёт по левой стойке, перекладине и правой стойке, углы сходятся «на ус».
// Проём — x0…x1 по ширине, y0…y1 по высоте, в осях родителя: x вдоль стены, y вверх, z от стены. Не преграда, тень не бросает (на карте теней — каша)
function portal(prof, x0, x1, y1, m, parent, y0 = 0) {
  const k = 'P' + [prof, x0, x1, y1, y0].join(); let g = PGEO[k];
  if (!g) { const pos = [], uv = [], path = u => [[x0 - u, y0], [x0 - u, y1 + u], [x1 + u, y1 + u], [x1 + u, y0]]; let s = 0;
    for (let i = 0; i < prof.length - 1; i++) { const [ua, va] = prof[i], [ub, vb] = prof[i + 1], A = path(ua), B = path(ub), d = Math.hypot(ub - ua, vb - va); let l = 0;
      for (let j = 0; j < 3; j++) { const a0 = A[j], a1 = A[j + 1], b0 = B[j], b1 = B[j + 1], L = Math.hypot(a1[0] - a0[0], a1[1] - a0[1]);
        pos.push(a0[0], a0[1], va, a1[0], a1[1], va, b1[0], b1[1], vb, a0[0], a0[1], va, b1[0], b1[1], vb, b0[0], b0[1], vb);
        uv.push(s, l, s, l + L, s + d, l + L, s, l, s + d, l + L, s + d, l); l += L; }
      s += d; }
    g = PGEO[k] = new T.BufferGeometry(); g.setAttribute('position', new T.Float32BufferAttribute(pos, 3)); g.setAttribute('uv', new T.Float32BufferAttribute(uv, 2)); g.computeVertexNormals(); }
  const me = new T.Mesh(g, m); me.userData.solid = false; me.userData.noShadow = true; if (parent) parent.add(me); return me;
}
// обвязка двери, полотно которой выступает из стены на p (так стоят двери коридора): коробка выше полотна на 12 мм — полотно сидит в ней утопленным,
// за коробкой ступенькой наличник; тёмная щель притвора по кромке полотна и под ним, на полу — накладка порога. g — группа в плоскости стены (z — к зрителю);
// low = false — без щели и порога внизу (под дверью свой свет). Материалы M: { fr — коробка, gap — щель, sill — порог }
function doorTrim(g, x0, x1, h, p, M, low = true, cas = .016) {
  const NS = m => { m.userData.solid = false; m.userData.noShadow = true; return m; }, w = x1 - x0, cx = (x0 + x1) / 2;
  portal([[0, 0], [0, p + .012], [.038, p + .012], [.046, p + .004], [.05, cas + .008], [.108, cas], [.112, 0]], x0, x1, h, M.fr, g);
  portal([[-.008, p + .003], [0, p + .003]], x0, x1, h, M.gap, g);
  if (low) { NS(box(w, .012, .002, M.gap, cx, .006, p + .003, g)); NS(box(w + .07, .006, .05, M.sill, cx, .003, p + .014, g)); }
}
// склейка неподвижного сборного предмета: все его детали с одним материалом становятся одним мешем (в осях группы). Детальные предметы (19 ноя) —
// это десятки мелких мешей; каждый рисовался отдельно и ещё по разу на каждую грань куба теней каждой лампы: отрисовок за кадр стало втрое больше.
// Только для групп со своим коллайдером (userData.solid задан — детей твёрдый мир не смотрит) и без подвижных частей; деталь с userData.keep, с несколькими
// материалами или прозрачная без записи глубины остаётся как есть.
function bake(g) {
  if (g.userData.solid === undefined || window.__NOBAKE) return g;
  g.updateMatrixWorld(true); const inv = new T.Matrix4().copy(g.matrixWorld).invert(), by = new Map(), M = new T.Matrix4(), N = new T.Matrix3(), v = new T.Vector3();
  g.traverse(m => { if (!m.isMesh || m.isInstancedMesh || m.isSkinnedMesh || Array.isArray(m.material) || m.children.length || m.userData.keep || !m.visible) return;
    for (let q = m.parent; q && q !== g; q = q.parent) if (q.userData.keep || !q.visible) return;
    const mt = m.material; if (mt.transparent && !mt.depthWrite) return;
    const k = mt.uuid + '|' + (m.userData.noShadow ? 1 : 0) + '|' + m.renderOrder; (by.get(k) || by.set(k, []).get(k)).push(m); });
  for (const list of by.values()) { if (list.length < 2) continue; const pos = [], nor = [], uv = [];
    for (const m of list) { const gm = m.geometry.index ? m.geometry.toNonIndexed() : m.geometry, P = gm.attributes.position, Nn = gm.attributes.normal, U = gm.attributes.uv;
      M.multiplyMatrices(inv, m.matrixWorld); N.getNormalMatrix(M); const flip = M.determinant() < 0;
      for (let t = 0; t < P.count; t += 3) for (const i of flip ? [t, t + 2, t + 1] : [t, t + 1, t + 2]) { v.fromBufferAttribute(P, i).applyMatrix4(M); pos.push(v.x, v.y, v.z);
        if (Nn) { v.fromBufferAttribute(Nn, i).applyMatrix3(N).normalize(); nor.push(v.x, v.y, v.z); } else nor.push(0, 1, 0); if (U) uv.push(U.getX(i), U.getY(i)); else uv.push(0, 0); }
      if (gm !== m.geometry) gm.dispose(); m.parent.remove(m); }
    const bg = new T.BufferGeometry(); bg.setAttribute('position', new T.Float32BufferAttribute(pos, 3)); bg.setAttribute('normal', new T.Float32BufferAttribute(nor, 3)); bg.setAttribute('uv', new T.Float32BufferAttribute(uv, 2));
    const me = new T.Mesh(bg, list[0].material); me.userData.solid = false; me.renderOrder = list[0].renderOrder; if (list.every(m => m.userData.noShadow)) me.userData.noShadow = true; g.add(me); }
  return g;
}
// серверная стойка: лицо — панель с блоками и диодами (rm), бока и верх — крашеная сталь (диоды были на всех гранях), по углам — стойки рамы,
// сверху крышка, снизу цоколь на винтовых ножках, на дверце — ручка-защёлка и наклейка со штрихкодом. В осях стойки лицо смотрит на +z; ry поворачивает
let RACK_M = null;
function makeRack(S, rm, x, z, w, d, ry, h = 2.1) {
  const g = new T.Group(); g.position.set(x, 0, z); g.rotation.y = ry; S.add(g); g.userData.solid = [[0, h / 2, 0, w, h, d, 'серверная стойка']];
  if (!RACK_M) RACK_M = { side: mat(0x1c1e21, null, { rough: .5, metal: .55 }), fr: mat(0x0d0e10, null, { rough: .4, metal: .6 }), chr: mat(0xa4aab0, null, { rough: .25, metal: .9 }), ft: mat(0x2a2c2e, null, { rough: .5, metal: .7 }),
    lab: mat(0xffffff, ctex(32, 12, (c, cw, ch) => { c.fillStyle = '#e8e6de'; c.fillRect(0, 0, cw, ch); c.fillStyle = '#16181a'; for (let i = 2; i < 30; i += 1 + (i * 7 % 3)) c.fillRect(i, 2, i % 4 ? 1 : 2, 6); c.fillRect(2, 9, 18, 1); }), { decal: 1 }) };
  const M = RACK_M, bh = h - .14, body = new T.Mesh(new T.BoxGeometry(w - .03, bh, d - .03), [M.side, M.side, M.side, M.side, rm, M.side]); body.position.y = .08 + bh / 2; g.add(body);
  for (const sx of [-1, 1]) rbox(.04, h - .1, .04, .006, M.fr, sx * (w / 2 - .02), .05 + (h - .1) / 2, d / 2 - .02, g);   // стойки рамы по краям лица (задние у стены не видны — не рисуем)
  rbox(w, .05, d, .01, M.side, 0, h - .025, 0, g); rbox(w - .02, .07, d - .02, .006, M.fr, 0, .085, 0, g);   // крышка, цоколь
  for (const sx of [-1, 1]) lathe([[0, 0], [.022, 0], [.022, .01], [.008, .012], [.008, .05], [0, .05]], 10, M.ft, g, sx * (w / 2 - .06), 0, d / 2 - .06);   // передние ножки
  rbox(.022, .17, .022, .006, M.chr, w / 2 - .065, 1.12, d / 2 - .004, g); rbox(.03, .05, .012, .004, M.fr, w / 2 - .065, 1.24, d / 2 - .01, g);   // ручка и замок
  const lb = plane(.09, .034, M.lab, g); lb.position.set(-w / 2 + .1, h - .1, d / 2 - .014); lb.userData.solid = false;
  return bake(g);
}
// огнетушитель порошковый (в группу g, основанием в 0, лицом на +z; высота ~0,53 м): баллон с донным кольцом, этикетка с пиктограммами,
// запорная головка, рычаг и ручка для переноски, манометр, шланг с раструбом в держателе на боку, чека с пломбой
let EXT_M = null;
function makeExt(g) {
  if (!EXT_M) EXT_M = { red: mat(0xc01a1a, null, { rough: .3, metal: .15 }), blk: mat(0x141414, null, { rough: .55 }), chr: mat(0xb8bcc0, null, { rough: .25, metal: .9 }),
    lab: mat(0xffffff, ctex(96, 64, (x, w, h) => { x.fillStyle = '#ece8dc'; x.fillRect(0, 0, w, h); x.fillStyle = '#c01a1a'; x.fillRect(0, 0, w, 9); x.fillRect(0, h - 5, w, 5);
      for (const [cx, c] of [[18, '#c87a1a'], [42, '#2a5ab0'], [66, '#d0a020']]) { x.fillStyle = c; x.fillRect(cx - 9, 16, 18, 18); x.fillStyle = '#fff'; x.fillRect(cx - 4, 21, 8, 8); }
      x.fillStyle = '#333'; for (let i = 0; i < 5; i++) x.fillRect(8, 40 + i * 4, 40 + (i * 17 % 30), 1.6); x.fillStyle = '#c01a1a'; x.font = '700 10px Arial'; x.fillText('4 kg', 56, 53); }), { rough: .5 }) };
  const M = EXT_M;
  lathe([[0, 0], [.062, 0], [.066, .006], [.066, .02], [0, .02]], 16, M.blk, g);   // донное кольцо
  lathe([[0, .015], [.066, .015], [.072, .03], [.073, .36], [.068, .395], [.048, .42], [.024, .432], [0, .434]], 16, M.red, g);
  const lb = new T.Mesh(PGEO.extL || (PGEO.extL = new T.CylinderGeometry(.0735, .0735, .17, 16, 1, true, -1.1, 2.2)), M.lab); lb.position.y = .2; g.add(lb);
  lathe([[0, .43], [.022, .43], [.022, .47], [.018, .48], [0, .48]], 12, M.chr, g);   // горловина и головка
  rbox(.03, .03, .05, .006, M.blk, 0, .49, -.005, g);
  const lv = rbox(.022, .012, .1, .004, M.blk, 0, .53, -.03, g); lv.rotation.x = .12; const hd = rbox(.02, .01, .09, .004, M.blk, 0, .505, -.03, g); hd.rotation.x = -.05;   // рычаг и ручка
  const ga = lathe([[0, 0], [.016, 0], [.016, .01], [0, .01]], 14, M.chr, g, 0, .475, .022); ga.rotation.x = PI / 2;
  const gf = new T.Mesh(PGEO.gauge || (PGEO.gauge = new T.CircleGeometry(.013, 14)), mat(0xf4f2ea, null, { rough: .3 })); gf.position.set(0, .475, .0325); g.add(gf);
  box(.002, .009, .001, mat(0xc01a1a), .002, .477, .033, g).rotation.z = -.6;
  const pin = tor(.008, .0015, mat(0xd8c020, null, { metal: .8, rough: .3 }), g, PI * 2, 4, 10); pin.position.set(.02, .505, -.02); pin.rotation.y = PI / 2;
  cable([[-.018, .47, -.01], [-.06, .46, -.02], [-.078, .38, -.01], [-.078, .2, 0], [-.076, .1, .01]], .007, M.blk, g, 6);   // шланг
  lathe([[0, 0], [.012, 0], [.009, .05], [.008, .06], [0, .06]], 10, M.blk, g, -.076, .07, .01);   // раструб
  rbox(.012, .02, .03, .004, M.blk, -.07, .3, 0, g);   // держатель шланга
  return bake(g);
}
// седан у тротуара (в группу g, нос на +z, 4,4 × 1,72 м): кузов — выдавленный боковой профиль с арками колёс и скосами, сверху стеклянная кабина
// с крышей и стойками, колёса — шина с протектором-бортом и литой диск, фары, задние фонари (горят, как раньше), бампера, ручки, зеркала, номер
function makeCar(g) {
  g.userData.solid = [[0, .7, 0, 1.75, 1.35, 4.45, 'машина']];
  const paint = mat(0x2a3440, null, { rough: .22, metal: .55 }), glass = mat(0x0a0d10, null, { rough: .05, metal: .4, emissive: 0x04070a }), blk = mat(0x0e0f10, null, { rough: .6 }), chr = mat(0xb4b8bc, null, { rough: .2, metal: .9 });
  const ext = (pts, w, m, b = .04) => { const s = new T.Shape(pts.map(([a, c]) => new T.Vector2(a, c))), ge = new T.ExtrudeGeometry(s, { depth: w - 2 * b, bevelEnabled: true, bevelThickness: b, bevelSize: b * .8, bevelSegments: 2, curveSegments: 6 });
    ge.translate(0, 0, -(w - 2 * b) / 2); const me = new T.Mesh(ge, m); me.rotation.y = -PI / 2; g.add(me); return me; };
  const s = new T.Shape(); s.moveTo(-2.1, .3); s.lineTo(-2.2, .52); s.lineTo(-2.17, .74); s.lineTo(-1.9, .8); s.lineTo(-1.3, .84); s.lineTo(1.15, .84); s.lineTo(1.95, .76); s.lineTo(2.18, .64); s.lineTo(2.2, .4); s.lineTo(2.12, .28);
  s.lineTo(1.73, .28); s.absarc(1.35, .3, .38, 0, PI, false); s.lineTo(-.97, .28); s.absarc(-1.35, .3, .38, 0, PI, false); s.lineTo(-2.1, .3);
  const bg = new T.ExtrudeGeometry(s, { depth: 1.62, bevelEnabled: true, bevelThickness: .05, bevelSize: .04, bevelSegments: 2, curveSegments: 8 }); bg.translate(0, 0, -.81);
  const body = new T.Mesh(bg, paint); body.rotation.y = -PI / 2; g.add(body);
  ext([[-1.3, .83], [-.98, 1.22], [.1, 1.28], [.52, 1.245], [1.16, .83]], 1.48, glass, .03);   // стеклянная кабина
  ext([[-1.02, 1.2], [-.97, 1.255], [.1, 1.315], [.53, 1.28], [.6, 1.23], [.1, 1.27], [-.96, 1.21]], 1.4, paint, .02);   // крыша
  for (const sx of [-1, 1]) { const bp = rbox(.07, .44, .04, .01, blk, sx * .735, 1.05, -.05, g); bp.rotation.x = -.06;   // средняя стойка
    for (const z of [.25, -.65]) rbox(.02, .025, .12, .008, chr, sx * .87, .82, z, g);   // ручки дверей
    const mr = rbox(.06, .1, .16, .02, paint, sx * .9, .95, .95, g); mr.rotation.y = sx * .3;   // зеркала
    for (const z of [1.35, -1.35]) { const wh = new T.Group(); wh.position.set(sx * .74, .32, z); wh.rotation.z = -sx * PI / 2; g.add(wh);   // ось колеса — поперёк машины, диск — наружу
      lathe([[.2, -.1], [.27, -.105], [.31, -.09], [.32, -.05], [.32, .05], [.31, .09], [.27, .105], [.2, .1]], 16, blk, wh);
      lathe([[0, .085], [.19, .085], [.2, .095], [.16, .1], [.06, .102], [0, .1]], 14, mat(0x8a9096, null, { rough: .3, metal: .85 }), wh);
      for (let i = 0; i < 5; i++) { const sp = rbox(.035, .012, .15, .005, mat(0x4a4e52, null, { rough: .4, metal: .8 }), 0, .1, 0, wh); sp.rotation.y = i * PI * 2 / 5; sp.position.set(Math.sin(i * PI * 2 / 5) * .08, .1, Math.cos(i * PI * 2 / 5) * .08); } } }
  for (const sx of [-1, 1]) { const hl = lathe([[0, 0], [.09, 0], [.1, .02], [0, .03]], 12, mat(0xc8d0d8, null, { rough: .05, metal: .3, emissive: 0x101418 }), g, sx * .58, .62, 2.19); hl.rotation.x = PI / 2; hl.scale.set(1.6, 1, .7);
    rbox(.36, .1, .03, .015, mat(0xff2a2a, null, { basic: true }), sx * .55, .7, -2.19, g); }   // фары, задние фонари
  rbox(1.0, .14, .03, .02, blk, 0, .52, 2.2, g); for (let i = 0; i < 4; i++) box(.9, .008, .01, chr, 0, .47 + i * .03, 2.215, g);   // решётка радиатора
  for (const z of [2.18, -2.18]) rbox(1.76, .16, .1, .04, blk, 0, .34, z, g);   // бампера
  const pl = label(.52, .11, 104, 22, (c, w, h) => { c.fillStyle = '#eef0f2'; c.fillRect(0, 0, w, h); c.strokeStyle = '#111'; c.strokeRect(1, 1, w - 2, h - 2); c.fillStyle = '#111'; c.font = '700 16px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('A 713 KX', w / 2 - 8, h / 2 + 1); }, g, false);
  pl.position.set(0, .48, -2.24); pl.rotation.y = PI; pl.userData.solid = false;
  bake(g);
}
// мусорный контейнер на 1100 л (в группу g, лицом на +z): короб сужается книзу, кант по верху, крышка на петлях (open — приоткрыта, рад),
// рёбра и гребёнка спереди, цапфы по бокам, ручки, четыре поворотных колеса
function makeDumpster(g, bm, lm, open = 0) {
  const k = 'dump'; if (!PGEO[k]) { const s = new T.Shape([[-.32, .12], [.32, .12], [.4, 1.0], [-.4, 1.0]].map(([a, b]) => new T.Vector2(a, b)));
    PGEO[k] = new T.ExtrudeGeometry(s, { depth: 1.0, bevelEnabled: true, bevelThickness: .03, bevelSize: .025, bevelSegments: 2, curveSegments: 2 }); PGEO[k].translate(0, 0, -.5); }
  const body = new T.Mesh(PGEO[k], bm); body.rotation.y = -PI / 2; g.add(body);
  const blk = mat(0x111213, null, { rough: .6 }), st = mat(0x5a5e62, null, { rough: .45, metal: .7 });
  rbox(1.12, .05, .87, .015, bm, 0, 1.01, 0, g);   // кант
  const ld = new T.Group(); ld.position.set(0, 1.035, -.43); ld.rotation.x = -open; g.add(ld);
  rbox(1.13, .045, .88, .02, lm, 0, .022, .44, ld); rbox(.98, .05, .7, .03, lm, 0, .06, .45, ld); rbox(.3, .025, .05, .01, lm, 0, .03, .89, ld);   // крышка, купол, ручка
  for (const x of [-.3, 0, .3]) { const r2 = rbox(.07, .72, .03, .01, bm, x, .58, .375, g); r2.rotation.x = -.09; }   // рёбра
  rbox(1.0, .06, .05, .015, st, 0, .9, .43, g);   // гребёнка под захват мусоровоза
  for (const s of [-1, 1]) { const t2 = lathe([[0, 0], [.045, 0], [.045, .07], [0, .07]], 10, st, g, s * .53, .82, 0); t2.rotation.z = -s * PI / 2;
    cable([[s * .52, .9, -.2], [s * .58, .9, -.15], [s * .58, .9, .15], [s * .52, .9, .2]], .012, blk, g, 6); }
  for (const x of [-.42, .42]) for (const z of [-.24, .24]) { rbox(.1, .015, .1, .004, st, x, .118, z, g); rbox(.03, .07, .06, .006, st, x, .08, z, g);
    const w = cyl(.065, .065, .045, 12, blk, g); w.rotation.z = PI / 2; w.position.set(x, .065, z + .02); }
  bake(g);
}
// фурнитура двери-коробки (в осях полотна, толщина по x): на лицевой стороне nx (±1, полутолщина t) — накладка с нажимной ручкой и цилиндром замка
// у края hz·sz (ручка смотрит к петлям), на другом краю — две петли. Ручка на высоте 1 м от пола (полотно высотой 2,1 с центром на 1,05)
let HW_M = null;
function doorHW(d, nx, t, sz, hz, hinge) {
  if (!HW_M) HW_M = { st: mat(0xb8bcc0, null, { rough: .28, metal: .9 }), dk: mat(0x3a3c3e, null, { rough: .35, metal: .8 }) };
  const g = new T.Group(); g.position.set(nx * t, -.05, sz * hz); g.rotation.y = nx > 0 ? PI / 2 : -PI / 2; d.add(g); g.userData.solid = false;   // в осях g лицо — +z
  rbox(.045, .22, .01, .004, HW_M.st, 0, -.04, .005, g);
  const rs = lathe([[0, 0], [.022, 0], [.02, .012], [.012, .016], [0, .016]], 12, HW_M.st, g, 0, 0, .008); rs.rotation.x = PI / 2;
  const lv = rbox(.13, .018, .022, .008, HW_M.st, 0, 0, .05, g); const k = sz * nx; lv.position.x = k * .055;   // ручка — от края к середине полотна
  const cy = lathe([[0, 0], [.009, 0], [.009, .008], [0, .008]], 10, HW_M.dk, g, 0, -.11, .008); cy.rotation.x = PI / 2;
  for (const y of [.65, -.75]) { const h = cyl(.009, .009, .1, 8, HW_M.dk, g); h.position.set(sz * (hz + hinge) * (nx > 0 ? 1 : -1), y + .05, .006); }
  g.traverse(m => { if (m.isMesh) m.userData.noShadow = true; }); bake(g);   // 20 ноя: фурнитура одной двери — два меша (сталь, тёмное) вместо шести; тень от неё на карте теней всё равно не читалась
}
// офисный стул (в группу g, лицом на +z): крестовина на пять лучей с роликами, газлифт в чехле, механизм под сиденьем, мягкое сиденье и спинка
// со скруглёнными краями, гнутая стойка спинки. w — ширина, sy — высота сиденья, by/bh — центр и высота спинки, bz — где спинка по z
let CH_M = null;
function officeChair(g, w, sy, by, bh, bz, fab) {
  if (!CH_M) CH_M = { fab: mat(0x18191b, null, { rough: .95 }), pl: mat(0x0f1011, null, { rough: .5 }), chr: mat(0x9a9fa4, null, { rough: .25, metal: .85 }), wh: mat(0x1a1a1a, null, { rough: .6 }) };
  const M = fab ? Object.assign({}, CH_M, { fab }) : CH_M, R = .27;
  lathe([[0, .045], [.045, .045], [.05, .06], [.03, .09], [.024, .1], [.024, .26], [.02, .27], [.02, sy - .07], [0, sy - .07]], 12, M.pl, g);   // ступица и газлифт
  cyl(.026, .026, .12, 10, M.chr, g).position.y = .2;   // хромированная гильза
  for (let i = 0; i < 5; i++) { const a = i / 5 * PI * 2 + .3, l = new T.Group(); l.rotation.y = a; g.add(l);
    const leg = rbox(.045, .03, R - .03, .01, M.pl, 0, .07, (R - .03) / 2 + .02, l); leg.rotation.x = .1;
    const c = new T.Group(); c.position.set(0, 0, R); l.add(c); rbox(.02, .035, .035, .006, M.pl, 0, .05, 0, c);   // вилка ролика
    for (const s of [-1, 1]) { const wl = cyl(.024, .024, .012, 12, M.wh, c); wl.rotation.z = PI / 2; wl.position.set(s * .009, .024, -.008); } }
  rbox(.2, .04, .22, .01, M.pl, 0, sy - .065, 0, g); rbox(w - .06, .025, w - .06, .012, M.pl, 0, sy - .04, 0, g);   // механизм, чаша сиденья
  rbox(w, .07, w, .03, M.fab, 0, sy, 0, g); rbox(w - .02, bh, .06, .028, M.fab, 0, by, bz, g);   // сиденье, спинка
  const st = rbox(.06, .5 * (by - sy) + .1, .018, .006, M.pl, 0, sy + .02 + (by - sy) * .3, bz - .04, g); st.rotation.x = .08;   // стойка спинки — за спинкой
  rbox(.06, .018, Math.abs(bz) + .02, .006, M.pl, 0, sy - .055, (bz - .04) / 2, g);
}
// свеча на полу: оплывший воск (тело вращения с наплывом у подножия и лужицей), фитиль, язычок пламени (светится сам; свет даёт лампа места)
function makeCandle(S, x, z, h = .12, seed = 0) {
  const g = new T.Group(); g.position.set(x, 0, z); g.rotation.y = seed * 1.7; S.add(g); g.userData.solid = [[0, h / 2, 0, .05, h, .05, 'свеча']];
  const wax = mat(0xe8e0c8, null, { rough: .55, emissive: 0x2a1a0a });
  lathe([[0, 0], [.045, 0], [.04, .003], [.03, .006], [.024, .012], [.021, .02], [.02, h - .012], [.0185, h - .004], [.012, h], [0, h - .004]], 12, wax, g);
  const dr = lathe([[0, 0], [.006, 0], [.005, .03], [0, .034]], 6, wax, g, .019, h - .04, 0); dr.rotation.z = -.15;   // потёк воска
  box(.002, .012, .002, mat(0x141010), 0, h + .002, 0, g);
  const fl = lathe([[0, 0], [.005, .004], [.006, .01], [.0035, .02], [0, .03]], 8, mat(0xffc870, null, { basic: true, glow: 3 }), g, 0, h + .004, 0); fl.userData.solid = false; fl.userData.noShadow = true;
  return g;
}
// розетка на стене: рамка, гнездо, вилка со шнуром вниз; (x, y, z) — точка на стене, ry — куда смотрит лицо (0 — на +z)
function socket(S, x, y, z, ry, plug = true) {
  const g = new T.Group(); g.position.set(x, y, z); g.rotation.y = ry; S.add(g); g.userData.solid = false;
  const w = mat(0xe6e4dc, null, { rough: .4 }); rbox(.08, .08, .012, .006, w, 0, 0, .006, g); const nest = lathe([[.026, 0], [.024, .004], [0, .004]], 14, mat(0xd2d0c8, null, { rough: .45 }), g); nest.rotation.x = PI / 2; nest.position.z = .012;
  if (plug) { const p = mat(0x18191b, null, { rough: .5 }); const b = lathe([[.019, 0], [.019, .012], [.016, .03], [.008, .036], [0, .036]], 12, p, g); b.rotation.x = PI / 2; b.position.z = .016; }
  return g;
}
// ведро уборщика: жёлтое, с отжимом и колёсиками; tip(k) — опрокинуто
// ведро-тележка уборщика (19 ноя, владелец: «ведро не проработано, и тележка или что это»): как настоящее — овальное пластиковое ведро с бортиком
// и мутной водой, рама на четырёх поворотных колёсиках, на одном конце — отжим (корзина, прижимная пластина, рычаг с чёрной ручкой), на другом — ручка
// для катания. Точки, за которые берётся Гас (gusDip): вода (−0,08; 0,26; 0), щель отжима (0,12; 0,40; 0), ручка рычага (0,20; 0,82; 0) — в осях ведра
function makeBucket() {
  const g = new T.Group(), b = new T.Group(); g.add(b);
  const yM = mat(0xd8a812, null, { rough: .45 }), yD = mat(0xa88210, null, { rough: .5 }), gM = mat(0x6e7378, null, { rough: .35, metal: .6 }), dM = mat(0x161819, null, { rough: .8 }), rM = mat(0x2a2c2e, null, { rough: .9 });
  // ванна: овал (цилиндр, сжатый по z), стенки чуть сужаются ко дну; внутри тёмная стенка, сверху бортик
  const tub = cyl(.215, .19, .3, 20, yM, b); tub.scale.set(1.12, 1, .78); tub.position.set(-.02, .2, 0);
  const inner = cyl(.2, .185, .02, 20, mat(0x7a6414, null, { rough: .6 }), b); inner.scale.set(1.12, 1, .78); inner.position.set(-.02, .345, 0);
  const rim = new T.Mesh(new T.TorusGeometry(.212, .014, 6, 24), yD); rim.rotation.x = PI / 2; rim.scale.set(1.12, .78, 1); rim.position.set(-.02, .352, 0); b.add(rim);
  const wtr = cyl(.198, .198, .006, 20, mat(0x4a5a5c, null, { rough: .08, metal: .2, emissive: 0x060a0b }), b); wtr.scale.set(1.12, 1, .78); wtr.position.set(-.02, .3, 0);   // мутная вода
  for (const z of [-.15, .15]) { const ri = box(.36, .05, .012, yD, -.02, .2, z * 1.02, b); ri.rotation.x = z > 0 ? -.06 : .06; }   // рёбра жёсткости на боках
  // рама и колёсики
  box(.5, .025, .36, dM, 0, .055, 0, b);
  for (const [x, z] of [[-.21, .15], [.21, .15], [-.21, -.15], [.21, -.15]]) { box(.03, .03, .03, gM, x, .033, z, b); const w = cyl(.022, .022, .018, 10, rM, b); w.rotation.x = PI / 2; w.position.set(x, .022, z); }
  // отжим: корзина над краем, прижимная пластина, рычаг вверх с ручкой
  box(.17, .14, .27, gM, .13, .42, 0, b); box(.15, .02, .25, dM, .13, .35, 0, b);
  const plate = box(.02, .13, .24, gM, .05, .43, 0, b); plate.rotation.z = .12;
  for (const z of [-.1, .1]) { const a = cyl(.009, .009, .42, 6, gM, b); a.position.set(.2, .62, z); }
  const bar = cyl(.012, .012, .23, 6, gM, b); bar.rotation.x = PI / 2; bar.position.set(.2, .83, 0);
  const grip = cyl(.018, .018, .13, 8, dM, b); grip.rotation.x = PI / 2; grip.position.set(.2, .83, 0);
  // ручка для катания на другом конце
  for (const z of [-.11, .11]) { const a = cyl(.008, .008, .55, 6, gM, b); a.position.set(-.26, .33, z); a.rotation.z = -.12; }
  const hb = cyl(.011, .011, .24, 6, gM, b); hb.rotation.x = PI / 2; hb.position.set(-.293, .6, 0);
  g.userData.tip = k => { b.rotation.z = k * 1.45; b.position.set(k * .2, k * .14, 0); wtr.visible = k < .5; };
  return g;
}
// швабра на полу; fall(k): 0 — стоит, прислонённая; 1 — лежит
const _X1 = new T.Vector3(1, 0, 0);
function makeMop() {
  const g = new T.Group(), m = new T.Group(); g.add(m);
  const st = cyl(.013, .013, 1.45, 6, mat(0x8a8f96), m); st.position.y = .74;
  // колодка на шарнире: швабра прислонена или упала — колодка лежит на полу ровно (раньше вставала с черенком на ребро и уходила в пол на 3 см)
  const hd = new T.Group(); m.add(hd);
  // 19 ноя: колодка — пластиковая рамка со скруглёнными краями и шарнир-вилка, ворс — мохнатые жгуты разной длины; на черенке — заглушка и резиновая рукоять (размеры прежние)
  rbox(.36, .035, .1, .012, mat(0x3a3f6a, null, { rough: .5 }), 0, .03, 0, hd); rbox(.05, .04, .04, .01, mat(0x2a2e4e, null, { rough: .5 }), 0, .06, 0, hd);
  for (let i = 0; i < 9; i++) { const f = box(.035, .025, .16, mat(0xb8b4a8, null, { rough: 1 }), -.16 + i * .04, .0135, .02, hd); f.rotation.y = (rnd() - .5) * .5; }
  for (let i = 0; i < 8; i++) { const f = rbox(.022, .02, .13 + hash(i) * .06, .009, mat(0xa8a498, null, { rough: 1 }), -.15 + i * .043, .012, -.03 + hash(i * 3) * .02, hd); f.rotation.y = (hash(i * 7) - .5) * .7; }
  lathe([[0, 0], [.016, 0], [.017, .02], [.016, .17], [.014, .18], [.006, .19], [0, .19]], 10, mat(0x1c1d1f, null, { rough: .8 }), m, 0, 1.3, 0);
  const off = new T.Vector3();
  g.userData.fall = k => { const e = k * k; m.rotation.x = -e * (PI / 2 - .03) - (1 - e) * .12; m.position.y = e * .02;
    hd.rotation.x = -m.rotation.x; hd.position.copy(off.set(0, -m.position.y + .001, 0).applyAxisAngle(_X1, -m.rotation.x)); };
  return g;
}
function setLamps(o, n, fromNear = false) {   // n ламп горит: от дальней (по умолчанию) или от ближней
  o.lamps.forEach((l, i) => { const idx = fromNear ? i : o.lamps.length - 1 - i; const on = idx < n ? 1 : 0; l.on = on; l.base = on * 2.4; l.li.intensity = on * 2.4; l.tube.material.color.setHex(on ? 0xe8f0e8 : 0x1c1e1c); });
}

/* ---------- серверная / комната тринадцатой камеры ---------- */
const _tubeC = new T.Color(0xcfeee2);
function buildServer() {
  const S = new T.Scene(), o = { S }; S.background = new T.Color(FOGC); o.fogD = .085; S.fog = new T.FogExp2(FOGC, o.fogD);
  o.amb = new T.AmbientLight(0x4a5050, .24); S.add(o.amb);
  o.cold = new T.PointLight(0x7fd0b0, 1.2, 7, 1.4); o.cold.position.set(-2.4, 2.4, 0); S.add(o.cold);
  o.cold2 = new T.PointLight(0x7fd0b0, 1.0, 7, 1.4); o.cold2.position.set(2.4, 2.4, -1); S.add(o.cold2);
  // у холодного света есть источник: подвесные люминесцентные трубки на тросиках (вторая — ненадёжная, гаснет вместе со своей лампой)
  o.coldTubes = [o.cold, o.cold2].map(l => { const g = new T.Group(); g.position.set(l.position.x, 2.5, l.position.z); S.add(g); const tm = glow(ps1(new T.MeshBasicMaterial({ color: 0xcfeee2 })), 3);
    box(.2, .05, 1.25, mat(0x2a2e2e, null, { metal: .4, rough: .5 }), 0, .045, 0, g); box(.07, .035, 1.15, tm, 0, 0, 0, g);
    for (const dz of [-.5, .5]) box(.006, .44, .006, mat(0x111111), 0, .29, dz, g); return tm; });
  const fl = plane(8, 7, mat(0xffffff, TX.concrete), S); fl.rotation.x = -PI / 2;
  const ce = plane(8, 7, mat(0x2a2a2a), S); ce.rotation.x = PI / 2; ce.position.y = 3;
  const wm = mat(0xffffff, TX.concrete);
  for (const [w, x, z, ry] of [[8, 0, -3.5, 0], [7, -4, 0, PI / 2], [7, 4, 0, -PI / 2], [8, 0, 3.5, PI]]) { const p = plane(w, 3, wm, S); p.position.set(x, 1.5, z); p.rotation.y = ry; }
  for (const [w, x, z, ry] of [[8, 0, -3.5, 0], [7, -4, 0, PI / 2], [7, 4, 0, -PI / 2], [8, 0, 3.5, PI]]) aoEdge(S, w, x, z, ry, 3);
  const rm = mat(0xffffff, TX.rack, { emissive: 0x0a140c }); rm.emissiveMap = TX.rackEm; rm.emissive.setHex(0xffffff);   // светодиоды светятся сами
  // стойки (19 ноя): были коробки с диодами на всех гранях — теперь лицо с блоками, бока и верх из крашеной стали, рама, ножки, ручка дверцы;
  // над рядами — кабельные лотки на шпильках к потолку, из лотка в крышку каждой стойки спускаются жгуты (раньше провода лежали брусками поперёк потолка)
  for (let i = 0; i < 6; i++) { makeRack(S, rm, -3.4, -2.6 + i * 1.0, .9, .8, PI / 2); makeRack(S, rm, 3.4, -2.6 + i * 1.0, .9, .8, -PI / 2); }
  for (let i = 0; i < 4; i++) makeRack(S, rm, -1.5 + i * 1.0, -3.1, .9, .8, 0);
  { const tm = mat(0x6a6e72, null, { rough: .4, metal: .7 }), cm = mat(0x111214, null, { rough: .7 }), cb = mat(0x1d3550, null, { rough: .7 }), NS = m => { m.userData.solid = false; return m; };
    const tray = (x0, z0, x1, z1) => { const L = Math.hypot(x1 - x0, z1 - z0), g = new T.Group(); g.position.set((x0 + x1) / 2, 2.5, (z0 + z1) / 2); g.rotation.y = Math.atan2(x1 - x0, z1 - z0); g.userData.solid = false; S.add(g);
      for (const s of [-1, 1]) box(.006, .06, L, tm, s * .15, 0, 0, g);   // борта лестничного лотка
      for (let k = 0; k <= L / .6; k++) box(.3, .008, .025, tm, 0, -.026, -L / 2 + k * .6, g);   // перекладины
      for (let k = 0; k < 3; k++) { const c = cyl(.028 - k * .004, .028 - k * .004, L, 8, k === 1 ? cb : cm, g); c.rotation.x = PI / 2; c.position.set(-.07 + k * .065, -.002, 0); }   // жгуты в лотке
      for (let k = 0; k < Math.ceil(L / 1.5) + 1; k++) { const z = -L / 2 + k * Math.min(1.5, L); for (const s of [-1, 1]) box(.008, .5, .008, tm, s * .15, .25, Math.min(z, L / 2), g); box(.34, .02, .03, tm, 0, -.04, Math.min(z, L / 2), g); }   // шпильки и траверсы
      return bake(g); };
    tray(-3.4, -3.1, -3.4, 2.4); tray(3.4, -3.1, 3.4, 2.4); tray(-3.25, -3.1, 3.25, -3.1);
    // из лотка в крышку стойки — по два жгута с провисом
    for (let i = 0; i < 6; i++) for (const sx of [-1, 1]) { const z = -2.6 + i, x = sx * 3.4; for (const d of [-.06, .06]) cable([[x + d, 2.47, z + .1], [x + d, 2.3, z + .14], [x + d, 2.1, z + .12]], .014, i % 3 ? cm : cb, S, 6); }
    for (let i = 0; i < 4; i++) { const x = -1.5 + i; cable([[x, 2.47, -3.1], [x + .04, 2.3, -3.06], [x + .02, 2.1, -3.08]], .016, cm, S, 6); } }
  o.salt = plane(2.2, 2.2, CINE ? new T.MeshStandardMaterial({ map: TX.salt, emissiveMap: TX.salt, emissive: 0x8e8c84, roughness: .85, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }) : new T.MeshBasicMaterial({ map: TX.salt, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), S); o.salt.rotation.x = -PI / 2; o.salt.position.y = .012; ps1(o.salt.material);   // «Кино»: соль освещается свечами (светилась сама, как неон), но и в полутьме круг виден — он важен по сюжету
  for (let i = 0; i < 5; i++) { const a = i / 5 * PI * 2; makeCandle(S, Math.cos(a) * 1.25, Math.sin(a) * 1.25, .12 - (i % 3) * .02, i); }   // свечи: оплывшие, разной высоты
  o.candle = new T.PointLight(0xffb060, 1.2, 5, 1.6); o.candle.position.set(0, .5, .4); S.add(o.candle);
  o.red = new T.PointLight(0xff2020, .9, 9, 1.5); o.red.position.set(0, 2.8, 2.6); S.add(o.red);
  { const rl = new T.Group(); rl.position.set(0, 2.92, 3.5); S.add(rl); rl.userData.solid = false;   // аварийная лампа над дверью: колпак на кронштейне (была красная плашка)
    rbox(.14, .1, .03, .008, mat(0x2a2c2e, null, { rough: .5, metal: .4 }), 0, 0, -.015, rl); const cap = lathe([[.05, 0], [.05, .02], [.046, .05], [.03, .07], [0, .075]], 14, mat(0xff2020, null, { basic: true, glow: 2 }), rl, 0, 0, -.03); cap.rotation.x = -PI / 2;
    tor(.051, .006, mat(0x1a1b1c, null, { rough: .5 }), rl, PI * 2, 4, 16).position.z = -.031; }
  o.door = box(.94, 2.0, .06, mat(0x6a6e70, TX.metal, { metal: .55, rough: .5 }), 2, 1.0, 3.47, S);   // железная — как со стороны подвала
  { const fr = mat(0x2a2c2e, null, { metal: .6, rough: .45 }), hw = mat(0xa8adb2, null, { metal: .85, rough: .3 });   // коробка, петли, ручка-скоба, засов, рёбра — изнутри
    box(1.06, .07, .06, fr, 2, 2.035, 3.47, S); for (const dx of [-.5, .5]) box(.06, 2.07, .06, fr, 2 + dx, 1.035, 3.47, S);
    for (const y of [-.7, .7]) { const hg = cyl(.012, .012, .1, 10, hw, o.door); hg.position.set(-.47, y, -.035); }
    const hd = new T.Group(); hd.position.set(.36, .02, -.03); o.door.add(hd); rbox(.04, .16, .012, .005, fr, 0, 0, -.006, hd);
    cable([[0, .055, -.012], [0, .055, -.05], [0, -.055, -.05], [0, -.055, -.012]], .009, hw, hd, 8);
    rbox(.26, .03, .02, .006, hw, .2, .25, -.04, o.door); for (const y of [-.7, .7]) box(.8, .05, .012, mat(0x5a5e62, null, { metal: .5, rough: .5 }), 0, y + (y > 0 ? -.15 : .1), -.034, o.door); }
  { const tg = new T.Group(); tg.position.set(2, 0, 3.5); tg.rotation.y = PI; tg.userData.solid = false; S.add(tg);   // 20 ноя: коробка была заподлицо с полотном (плита на стене) — обвод выше полотна, щель притвора, порог
    doorTrim(tg, -.47, .47, 2.0, .06, { fr: mat(0x2a2c2e, null, { metal: .6, rough: .45 }), gap: mat(0x070808, null, { rough: .9 }), sill: mat(0x5a5e62, null, { metal: .6, rough: .5 }) }); bake(tg); }
  o.rim = new T.PointLight(0xa0c0d0, 2.6, 5, 1.3); o.rim.position.set(1.4, 2.7, .9); S.add(o.rim);
  { const cg = new T.Group(); cg.position.set(3.78, 2.84, 3.29); S.add(cg); cg.lookAt(0, 1.1, -.4); cg.userData.solid = false;   // сама камера 13: корпус с козырьком на кронштейне в углу, объектив смотрит в комнату (за спиной своего кадра)
    const wm2 = mat(0xd8dadc, null, { rough: .4 }), dk2 = mat(0x151618, null, { rough: .3 });
    rbox(.085, .075, .2, .014, wm2, 0, 0, -.02, cg); rbox(.1, .008, .23, .004, wm2, 0, .046, -.01, cg);   // корпус, козырёк
    const ln = lathe([[0, 0], [.03, 0], [.03, .012], [.022, .016], [0, .016]], 14, dk2, cg, 0, 0, .08); ln.rotation.x = PI / 2;
    const gl2 = new T.Mesh(new T.CircleGeometry(.018, 12), mat(0x203040, null, { rough: .05, metal: .3 })); gl2.position.z = .0965; cg.add(gl2);
    cable([[3.78, 2.84, 3.29], [3.86, 2.9, 3.36], [3.97, 2.93, 3.42]], .012, wm2, S, 6); rbox(.1, .1, .02, .01, wm2, 3.99, 2.93, 3.43, S).rotation.y = -PI / 4;   // кронштейн и пятка на стене
    box(.012, .012, .03, mat(0xff2020, null, { basic: true }), 0, -.03, .075, cg); }
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  // у круга Фрэнка (обход 04:30): кулер с водой у двери, пачка сухарей и пустая обёртка, листы скотчем на ближней стойке
  // кулер (19 ноя, владелец: «кулер не проработан, просто текстурка»): напольный, как настоящий — корпус со скруглёнными рёбрами: внизу шкафчик, над ним
  // ниша с двумя краниками (синий — холодная, красный — горячая, у каждого рычажок) и поддоном-решёткой, сверху голова с индикаторами и приёмником;
  // в приёмник вставлена горлышком вниз бутыль 19 л (горлышко с кольцом, плечи, рёбра жёсткости, дно), вода в ней до уровня, над водой — воздух,
  // от горлышка поднимаются пузыри; на решётке стаканчик с водой; сзади шнур по полу к розетке. Стоит в 4 см от стены лицом в комнату (раньше краники смотрели в стену)
  { const cl = new T.Group(); cl.name = 'кулер'; cl.position.set(.35, 0, 3.3); cl.rotation.y = PI; S.add(cl); o.cooler = cl;
    cl.userData.solid = [[0, .7, 0, .31, 1.4, .32, 'кулер']];   // один коллайдер на весь предмет
    const wh = mat(0xd9dcdf, null, { rough: .38 }), gy = mat(0x6c7176, null, { rough: .45 }), dk = mat(0x26292c, null, { rough: .55 }), chr = mat(0xc4c8cc, null, { rough: .22, metal: .85 });
    rbox(.285, .045, .295, .01, dk, 0, .0225, 0, cl);                     // цоколь
    rbox(.30, .5, .31, .016, wh, 0, .3, 0, cl);                          // шкафчик
    rbox(.30, .12, .31, .016, wh, 0, .855, 0, cl);                       // голова
    rbox(.30, .25, .2, .01, wh, 0, .672, -.055, cl);                     // задняя стенка ниши
    for (const s of [-1, 1]) rbox(.026, .29, .31, .008, wh, s * .137, .672, 0, cl);   // щёки ниши (заходят в шкафчик и голову — без щелей)
    rbox(.246, .24, .01, .003, gy, 0, .672, .049, cl);                   // тёмная панель в глубине ниши
    // лицо шкафчика: шов дверцы, решётка вентиляции внизу, наклейка-капля
    const face = label(.27, .46, 54, 92, (c, w, h) => { c.clearRect(0, 0, w, h); c.strokeStyle = 'rgba(40,44,48,.55)'; c.lineWidth = 1; c.strokeRect(4.5, 5.5, w - 9, h - 22);
      c.fillStyle = 'rgba(30,34,38,.7)'; for (let i = 0; i < 7; i++) c.fillRect(10 + i * 5.2, h - 11, 3, 6);
      c.fillStyle = '#2f6fc0'; c.beginPath(); c.moveTo(w / 2, 14); c.quadraticCurveTo(w / 2 + 7, 25, w / 2 + 6, 29); c.arc(w / 2, 29, 6, 0, PI); c.quadraticCurveTo(w / 2 - 7, 25, w / 2, 14); c.fill();
      c.fillStyle = 'rgba(255,255,255,.6)'; c.fillRect(w / 2 - 3, 25, 2, 4); c.fillStyle = 'rgba(40,44,48,.35)'; c.fillRect(w - 12, h / 2 - 8, 3, 16); }, cl, false);
    face.material.transparent = true; face.material.depthWrite = false; face.position.set(0, .3, .1565); face.userData.solid = false;
    // индикаторы на голове: снежинка и капля, под ними светятся «охлаждение» и «нагрев»
    const ic = label(.12, .03, 48, 12, (c, w, h) => { c.clearRect(0, 0, w, h); c.strokeStyle = '#2f6fc0'; c.lineWidth = 1.2; for (let i = 0; i < 3; i++) { const a = i * PI / 3; c.beginPath(); c.moveTo(12 - Math.cos(a) * 4.5, 6 - Math.sin(a) * 4.5); c.lineTo(12 + Math.cos(a) * 4.5, 6 + Math.sin(a) * 4.5); c.stroke(); }
      c.fillStyle = '#c03030'; c.beginPath(); c.moveTo(36, 1.5); c.quadraticCurveTo(40, 6, 39.5, 7.5); c.arc(36, 7.5, 3.5, 0, PI); c.quadraticCurveTo(32, 6, 36, 1.5); c.fill(); }, cl, false);
    ic.material.transparent = true; ic.material.depthWrite = false; ic.position.set(0, .885, .1565); ic.userData.solid = false;
    for (const [x, c] of [[-.03, 0x40ff70], [.03, 0xff4a30]]) box(.009, .005, .003, mat(c, null, { basic: true, glow: 2 }), x, .862, .1565, cl);
    // краники под головой: корпус цвета воды, хромированный носик, рычажок спереди; у горячего — красная кнопка-блокиратор сверху
    for (const [s, col] of [[-1, 0x2a6ad0], [1, 0xc42a2a]]) { const tp = new T.Group(); tp.position.set(s * .055, .795, .1); cl.add(tp); const cm = mat(col, null, { rough: .3 });
      lathe([[0, -.045], [.009, -.045], [.013, -.035], [.016, -.01], [.016, 0]], 12, cm, tp);
      lathe([[0, -.06], [.0035, -.06], [.0055, -.058], [.006, -.044]], 10, chr, tp);
      const lv = rbox(.03, .042, .008, .003, cm, 0, -.028, .022, tp); lv.rotation.x = -.22;
      if (s > 0) box(.014, .006, .01, mat(0xd83030, null, { rough: .35 }), 0, .12, .05, tp); }
    // поддон: короб и решётка из прутков
    rbox(.21, .022, .1, .006, dk, 0, .561, .1, cl); for (let i = 0; i < 8; i++) box(.005, .005, .086, gy, -.0875 + i * .025, .574, .1, cl);
    // приёмник бутыли на голове: воротник
    lathe([[.082, .914], [.084, .92], [.076, .935], [.05, .94], [.044, .925]], 18, wh, cl);
    // бутыль 19 л горлышком вниз: профиль от горлышка (y = 0) к дну (0,49 м); воду рисуем раньше бутыли (renderOrder), обе прозрачные без записи глубины
    const BP = [[.0285, 0], [.0285, .052], [.036, .056], [.036, .066], [.031, .07], [.033, .078], [.06, .096], [.098, .119], [.124, .143], [.134, .168], [.135, .2], [.135, .232], [.128, .243], [.128, .262], [.135, .273],
      [.135, .33], [.135, .358], [.128, .369], [.128, .388], [.135, .399], [.135, .44], [.13, .463], [.116, .479], [.08, .489], [0, .492]];
    const bt = new T.Group(); bt.position.y = .9; cl.add(bt); const YW = .315;   // уровень воды — от горлышка; выше — пузырь воздуха
    const shell = lathe(BP, 18, mat(0x9cc8f0, null, { transparent: true, opacity: .2, rough: .05 }), bt); shell.renderOrder = 3;
    const back = lathe(BP, 18, mat(0x7fb0dc, null, { transparent: true, opacity: .12, rough: .1, side: T.BackSide }), bt); back.renderOrder = 1;
    const rAt = y => { for (let i = 1; i < BP.length; i++) if (BP[i][1] >= y) { const [r0, y0] = BP[i - 1], [r1, y1] = BP[i]; return r0 + (r1 - r0) * (y - y0) / Math.max(1e-4, y1 - y0); } return 0; };
    const WP = [[0, .01]].concat(BP.filter(p => p[1] > .01 && p[1] < YW).map(([r, y]) => [r * .955, y]), [[rAt(YW) * .955, YW], [0, YW]]);
    const wtr = lathe(WP, 18, mat(0x4f8fd0, null, { transparent: true, opacity: .34, rough: .04 }), bt); wtr.renderOrder = 2;
    const ml = tor(rAt(YW) * .955, .002, mat(0xd8ecff, null, { transparent: true, opacity: .55, rough: .1 }), bt, PI * 2, 4, 24); ml.rotation.x = PI / 2; ml.position.y = YW; ml.renderOrder = 2;   // кромка воды у стенки
    const bm = mat(0xe6f2ff, null, { transparent: true, opacity: .45, rough: .1 });
    for (const [x, y, z, r] of [[.004, .09, 0, .007], [-.01, .15, .006, .01], [.012, .21, -.01, .013], [-.006, .272, .012, .016]]) { const b = new T.Mesh(PGEO.bub || (PGEO.bub = new T.SphereGeometry(1, 8, 6)), bm); b.scale.setScalar(r); b.scale.y = r * .8; b.position.set(x, y, z); b.renderOrder = 2; bt.add(b); }
    // стаканчик на решётке под холодным краником, в нём вода
    const cup = lathe([[0, 0], [.023, 0], [.0235, .003], [.032, .082], [.0335, .084]], 14, mat(0xf2f4f5, null, { transparent: true, opacity: .55, rough: .3, side: T.DoubleSide }), cl, -.055, .5775, .1); cup.renderOrder = 3;
    const cw = lathe([[0, .003], [.0225, .003], [.0285, .05], [0, .05]], 14, mat(0x6aa4d8, null, { transparent: true, opacity: .4, rough: .05 }), cl, -.055, .5775, .1); cw.renderOrder = 2;
    // шнур: из задней стенки вниз, по полу вдоль стены к розетке
    cable([[.27, .12, 3.455], [.272, .05, 3.47], [.3, .007, 3.478], [.5, .006, 3.482], [.68, .008, 3.468], [.715, .14, 3.43], [.72, .27, 3.43], [.72, .298, 3.444], [.72, .3, 3.452]], .0035, mat(0x111214, null, { rough: .6 }), S);
    socket(S, .72, .3, 3.5, PI);
    box(.13, .045, .09, mat(0xe0c89a, null, { decal: 1 }), 1.42, .035, 2.38, S); box(.12, .01, .08, mat(0xc8b088), 1.26, .014, 2.52, S);   // сухари: пачка и пустая обёртка
    const txt = ['НЕ ЗОВИ МЕНЯ.', 'СТУЧИ ТРИ РАЗА', 'ОНО ГОВОРИТ МАМИНЫМ ГОЛОСОМ', 'КАМЕРУ ЗАВЕСЬ. ОНО СМОТРИТ']; o.sheets = [];   // четвёртый лист (этап 1, 19 ноя): почему камера серверной всю ночь была чёрной
    txt.forEach((tx, i) => { const sh = label(.21, .3, 96, 136, (c, w, h) => { c.fillStyle = '#ece6d6'; c.fillRect(0, 0, w, h); c.fillStyle = '#1c1c2c'; c.font = '700 15px "Comic Sans MS", "Segoe Print", cursive'; c.textAlign = 'center';
        const cj = /^(zh|ja)$/.test(window.NS_LANG), ws = cj ? [...tr(tx)] : tr(tx).split(' '); let ln = '', y = 46; for (const wd of ws) { if (cj && !ln && wd === ' ') continue; const t2 = ln ? ln + (cj ? '' : ' ') + wd : wd; if (c.measureText(t2).width > w - 10 && ln) { c.fillText(ln, w / 2, y); ln = wd; y += 20; } else ln = t2; } if (ln) c.fillText(ln, w / 2, y);
        c.fillStyle = 'rgba(190,190,180,.85)'; c.fillRect(w / 2 - 10, 2, 20, 9); }, S, true);   // полоска скотча сверху (листы читаются и в полутьме — светятся, как было)
      sh.position.set(2.995, i < 3 ? 1.55 - i * .36 : 1.22, i < 3 ? 2.05 + (i % 2) * .22 : 2.52); sh.rotation.y = -PI / 2; sh.rotation.z = i < 3 ? (i - 1) * .06 : -.05; o.sheets.push(sh); });
  bake(cl); }
  o.reset = () => { o.faceL.intensity = 0; o.salt.visible = true; o.candle.intensity = 1.2; o.red.intensity = 1.4; o.amb.intensity = .24; o.cold.intensity = 1.35; o.cold2.intensity = 1.1; o.rim.intensity = 2.6; S.fog.density = o.fogD; o.flickOn = true; o.tubeOK = 1; o.cold2Base = null; o.candleK = o.redK = 1; };
  o.tick = (t) => {   // свеча и красная лампа мерцают каждый кадр поверх кадра — притушить их кадр может только множителем candleK / redK
    o.candle.intensity = (1.1 + Math.sin(t * 17) * .12 + Math.sin(t * 7) * .08) * (o.candleK ?? 1); o.red.intensity = (1.0 + .6 * (Math.sin(t * 2.4) > 0 ? 1 : 0)) * (o.redK ?? 1);
    if (o.cold2Base == null) o.cold2Base = o.cold2.intensity;   // ненадёжная холодная лампа над стойками справа
    const f = o.flickOn ? unreliable(t, 5) : 1; o.tubeOK = f; o.cold2.intensity = o.cold2Base * (f ? 1 : .08);
    o.coldTubes.forEach((m, i) => m.color.setScalar(clamp((i ? o.cold2 : o.cold).intensity / 1.1, .03, 1.2)).multiply(_tubeC));
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
    for (let k = 0; k < N; k++) { const b = wplane(w, 1.25, paint, S, x, 3 * k + .62, z, ry, 2.2); b.position.x += Math.sign(-x) * .006; b.position.z += Math.sign(-2.95 - z) * .006; }   // панель масляной краски — на 6 мм к середине клетки (у стены z = 0 сдвиг был нулевым: панель спорила со стеной полосами)
  }
  const ceil = wplane(W, 5.9, mat(0x2a2a2a), S, 0, 9.1, -2.95); ceil.rotation.x = PI / 2;
  const fl = wplane(W, 5.9, cm, S, 0, -.001, -2.95); fl.rotation.x = -PI / 2;
  const slope = Math.atan2(1.5, 3), sl = Math.hypot(1.5, 3);
  o.doors = []; o.signs = []; o.bulbs = [];
  const stg = new T.Group(), stM = { gap: mat(0x0a0a09, null, { rough: .9 }), sill: mat(0x6a6e70, null, { metal: .6, rough: .5 }) }; stg.userData.solid = false; S.add(stg);   // щели притвора и пороги всех трёх дверей — одной склейкой
  for (let k = 0; k < N; k++) {
    const y0 = 3 * k;
    if (k) box(W, .2, 1.4, cm, 0, y0 - .1, -.7, S);                          // площадка у двери этажа
    box(W, .2, 1.5, cm, 0, y0 + 1.4, -5.15, S);                              // средняя площадка
    for (const [sx, up] of [[-1, 0], [1, 1]]) {                               // левый марш — с площадки этажа вверх, правый — от средней вверх
      if (k === N - 1 && up) continue;
      const xc = sx * (X0 + (W / 2 - X0) / 2), w = W / 2 - X0 - .02, ya = y0 + 1.5 * up;
      for (let i = 0; i < 10; i++) { const yt = ya + (i + 1) * .15, z = up ? -4.4 + (i + .5) * .3 : -1.4 - (i + .5) * .3; box(w, .3, .3, cm, xc, yt - .15, z, S); }
      const sof = box(w - .012, .12, sl, cm, xc, ya + .75 - .2, -2.9, S); sof.rotation.x = up ? -slope : slope;   // косоур снизу (без «висящих» ступеней); на 6 мм уже ступеней — бока не спорят полосами
      // перила по внутреннему краю: стойки и поручень вдоль марша
      const xr = sx * (X0 + .03);
      for (let i = 0; i <= 6; i++) { const u = i / 6, z = up ? lerp(-4.4, -1.4, u) : lerp(-1.4, -4.4, u), y = ya + 1.5 * u; box(.025, .9, .025, rail, xr, y + .45, z, S); }
      const hr = cyl(.022, .022, sl + .1, 8, rail, S); hr.position.set(xr, ya + .75 + .92, -2.9); hr.rotation.x = up ? PI / 2 - slope : slope - PI / 2;
    }
    // дверь этажа на передней стене (справа), коробка, номер этажа краской слева от двери
    const dr = box(.9, 2.05, .05, mat(0x5a6a66, TX.door), .75, y0 + 1.025, -.03, S); o.doors.push(dr);
    box(1.0, .07, .08, rail, .75, y0 + 2.08, -.04, S); for (const dx of [-.48, .48]) box(.06, 2.1, .08, rail, .75 + dx, y0 + 1.05, -.04, S);
    { const pv = new T.Group(); pv.position.set(.75, y0 + 1.025, -.03); pv.rotation.y = PI / 2; pv.userData.solid = false; S.add(pv); doorHW(pv, 1, .025, -1, .36, .42);   // 20 ноя: у двери этажа не было ни ручки, ни петель, ни щели притвора, ни порога
      const g = new T.Group(); g.position.set(.75, y0, 0); g.rotation.y = PI; stg.add(g); portal([[-.008, .057], [0, .057]], -.45, .45, 2.05, stM.gap, g);
      for (const b of [box(.9, .012, .002, stM.gap, 0, .006, .057, g), box(.96, .006, .06, stM.sill, 0, .003, .06, g)]) b.userData.noShadow = true; }
    const sg = label(.5, .4, 50, 40, (c, w, h, s = String(k - 2)) => { c.clearRect(0, 0, w, h); c.fillStyle = '#d8d4c6'; c.font = '800 30px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(s, w / 2, h / 2 + 2); }, S, false);
    sg.material.alphaTest = .5; sg.rotation.y = PI; sg.position.set(-.35, y0 + 1.65, -.012); o.signs.push(sg);
    // лампа-«бочка» над средней площадкой и над дверью
    for (const [lx, ly, lz] of [[0, y0 + 3.9, -5.85], [.75, y0 + 2.45, -.06]]) {
      const sh = rbox(.2, .12, .07, .03, mat(0xfff2d0, null, { basic: true, glow: 3, own: 'st' + k + lz }), lx, ly, lz + (lz < -3 ? .005 : -.005), S); sh.userData.solid = false;   // 19 ноя: плафон «бочка» — скруглённый рассеиватель в корпусе с решёткой
      { const fm2 = mat(0x2a2c2e, null, { rough: .5, metal: .4 }), dz2 = lz < -3 ? -1 : 1; rbox(.24, .16, .03, .012, fm2, lx, ly, lz + dz2 * .036, S).userData.solid = false;
        for (const dy of [-.03, .03]) box(.22, .008, .008, fm2, lx, ly + dy, lz - dz2 * .04, S).userData.solid = false; box(.008, .13, .008, fm2, lx, ly, lz - dz2 * .04, S).userData.solid = false; }
      const li = new T.PointLight(0xffe2b0, 1.5, 7, 1.5); li.position.set(lx, ly - .12, lz + (lz < -3 ? .3 : -.3)); S.add(li); o.bulbs.push({ li, sh, base: 1.5 });
    }
  }
  bake(stg);
  o.faceL = new T.PointLight(0xdde6ff, 0, 3.2, 2); S.add(o.faceL);
  o.irL = new T.PointLight(0xffffff, 0, 16, 1.2); S.add(o.irL);
  o.setFloor = (k, n) => relabel(o.signs[k], String(n));   // какой этаж написан на уровне k (для разных камер)
  o.reset = () => { o.faceL.intensity = 0; o.amb.intensity = .3; S.fog.density = o.fogD; o.bulbs.forEach(l => { l.li.intensity = l.base; l.sh.material.color.setHex(0xfff2d0); }); o.flick = null; o.signs.forEach((s, k) => o.setFloor(k, k - 2)); };
  o.tick = t => { if (o.flick) o.bulbs.forEach((l, i) => { const on = o.flick(t, i); l.li.intensity = l.base * on; l.sh.material.color.setHex(on > .55 ? 0xfff2d0 : on > .2 ? 0x8a7c62 : 0x3a3428); }); };   // плафон гаснет вместе со своей лампой
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
  for (const [x, z, s] of [[-.4, -3.2, 1.2], [.5, -8.6, .9], [-.2, -11.4, 1.4]]) { const p = plane(s, s * .7, mat(0x0e1012, TX.puddle, { transparent: true, opacity: .55, decal: 1, rough: .1 }), S); p.rotation.x = -PI / 2; p.position.set(x, .004, z); }
  // трубы и лоток с кабелями под потолком
  const pm = mat(0x3a3e40, null, { metal: .5, rough: .5 }), rust = mat(0x5a4636, null, { rough: .8 });
  for (const [x, y, r, m] of [[-.85, 2.36, .07, pm], [-.6, 2.44, .045, rust], [-1.02, 2.12, .055, pm]]) { const c = cyl(r, r, len, 10, m, S); c.rotation.x = PI / 2; c.position.set(x, y, -len / 2); }
  box(.32, .04, len, mat(0x4a4c4e, null, { metal: .4 }), .85, 2.32, -len / 2, S);
  // 19 ноя: трубы держатся на хомутах со шпильками к потолку, на стыках — фланцы с болтами, на толстой трубе — задвижка с маховиком;
  // в лотке — кабели; на правой стене — электрощит (дверца, замок, знак «высокое напряжение»), от него вверх — гофра в лоток
  { const cl2 = mat(0x2a2c2e, null, { metal: .6, rough: .5 }), NS = m => { m.userData.solid = false; return m; };
    for (let z = -1; z > -len; z -= 2.2) { for (const x of [-1.13, -.5]) NS(box(.012, hgt - 2.05, .012, cl2, x, (hgt + 2.05) / 2, z, S)); NS(box(.67, .03, .04, cl2, -.815, 2.05, z, S));   // траверса на двух шпильках
      for (const [x, y, r] of [[-.85, 2.36, .07], [-.6, 2.44, .045], [-1.02, 2.12, .055]]) { const t2 = NS(tor(r + .006, .008, cl2, S, PI * 2, 4, 14)); t2.position.set(x, y, z); NS(box(.02, y - r - 2.065, .008, cl2, x, (y - r + 2.065) / 2, z, S)); } }   // хомут и лента до траверсы
    for (const z of [-3.6, -8.2, -12.6]) for (const [x, y, r] of [[-.85, 2.36, .07], [-1.02, 2.12, .055]]) { const f = NS(cyl(r + .025, r + .025, .03, 12, cl2, S)); f.rotation.x = PI / 2; f.position.set(x, y, z);
      for (let k = 0; k < 6; k++) { const a = k / 6 * PI * 2, b = NS(cyl(.006, .006, .05, 6, cl2, S)); b.rotation.x = PI / 2; b.position.set(x + Math.cos(a) * (r + .015), y + Math.sin(a) * (r + .015), z); } }
    { const vz = -5.6; const vb = NS(cyl(.09, .09, .16, 12, cl2, S)); vb.rotation.x = PI / 2; vb.position.set(-.85, 2.36, vz); NS(cyl(.018, .018, .14, 8, cl2, S)).position.set(-.85, 2.24, vz);
      const hw2 = NS(tor(.07, .009, mat(0xa02020, null, { rough: .5 }), S, PI * 2, 5, 16)); hw2.rotation.x = PI / 2; hw2.position.set(-.85, 2.16, vz); for (const a of [0, PI / 2]) { const sp = NS(box(.14, .01, .01, mat(0xa02020), -.85, 2.16, vz, S)); sp.rotation.y = a; } }
    for (let k = 0; k < 4; k++) { const c3 = NS(cyl(.012, .012, len, 6, k % 2 ? mat(0x1a1a1a, null, { rough: .7 }) : mat(0x3a2a20, null, { rough: .7 }), S)); c3.rotation.x = PI / 2; c3.position.set(.75 + k * .06, 2.355, -len / 2); }
    const eb = new T.Group(); eb.position.set(wd / 2, 0, -6.1); eb.rotation.y = -PI / 2; S.add(eb); eb.userData.solid = [[0, 1.35, .09, .6, .8, .18, 'электрощит']];
    const gm2 = mat(0x8a9088, null, { rough: .5, metal: .4 }); rbox(.6, .8, .16, .012, gm2, 0, 1.35, .08, eb); rbox(.56, .76, .015, .006, gm2, 0, 1.35, .165, eb);
    rbox(.02, .06, .02, .006, mat(0x1a1a1a), .24, 1.35, .18, eb); for (const y of [1.0, 1.7]) rbox(.03, .08, .02, .006, mat(0x5a5e5a, null, { metal: .7, rough: .4 }), -.29, y, .16, eb);
    const wn = label(.16, .14, 32, 28, (c, w, h) => { c.clearRect(0, 0, w, h); c.fillStyle = '#f0c420'; c.strokeStyle = '#111'; c.lineWidth = 2; c.beginPath(); c.moveTo(w / 2, 2); c.lineTo(w - 2, h - 2); c.lineTo(2, h - 2); c.closePath(); c.fill(); c.stroke();
      c.fillStyle = '#111'; c.beginPath(); c.moveTo(18, 8); c.lineTo(12, 17); c.lineTo(16, 17); c.lineTo(13, 25); c.lineTo(20, 14); c.lineTo(16, 14); c.closePath(); c.fill(); }, eb, false);
    wn.material.transparent = true; wn.material.depthWrite = false; wn.position.set(0, 1.5, .174); wn.userData.solid = false;
    for (const x of [-.18, 0, .18]) { const cd = NS(cyl(.016, .016, hgt - 1.75, 8, mat(0x2a2c2e, null, { rough: .7 }), eb)); cd.position.set(x, (hgt + 1.75) / 2, .06); } }
  // боковые кладовки (закрыты)
  for (const z of [-4.2, -9.6]) { const sd2 = box(.05, 2.0, .85, mat(0x4c4a44, TX.door), -wd / 2 + .03, 1.0, z, S); doorHW(sd2, 1, .025, 1, .34, .4); }
  { const tg = new T.Group(); tg.userData.solid = false; S.add(tg);   // 20 ноя: двери кладовок были плашками на стене — стальная коробка с наличником, щель притвора, порог
    const TM = { fr: mat(0x2c302e, null, { metal: .4, rough: .6 }), gap: mat(0x0a0a09, null, { rough: .9 }), sill: mat(0x5a5e5e, null, { metal: .6, rough: .5 }) };
    for (const z of [-4.2, -9.6]) { const g = new T.Group(); g.position.set(-wd / 2, 0, z); g.rotation.y = PI / 2; tg.add(g); doorTrim(g, -.425, .425, 2.0, .055, TM); }
    bake(tg); }
  // лампы в сетке: средняя ненадёжная
  o.bulbs = [];
  for (const z of [-2.4, -7.2, -12.0]) {
    // 19 ноя: светильник-«бочка»: чугунное основание на потолке, стеклянный колпак (светится), сетка из колец и прутьев
    const bulb = lathe([[0, -.09], [.03, -.088], [.05, -.07], [.058, -.04], [.058, 0], [0, 0]], 12, mat(0xffe8b8, null, { basic: true, glow: 3, own: 'bs' + z }), S, 0, hgt - .06, z);   // свой материал у каждой: иначе гасла и мигала бы не одна лампа, а все
    bulb.userData.solid = false;
    { const cm3 = mat(0x202020, null, { rough: .5, metal: .5 }); lathe([[0, 0], [.08, 0], [.08, .025], [.07, .04], [.065, .06], [0, .06]], 12, cm3, S, 0, hgt - .06, z).userData.solid = false;
      for (const [y, r] of [[hgt - .09, .064], [hgt - .13, .062]]) { const rg2 = tor(r, .004, cm3, S, PI * 2, 4, 14); rg2.rotation.x = PI / 2; rg2.position.set(0, y, z); rg2.userData.solid = false; }
      for (let i = 0; i < 4; i++) { const a = i * PI / 2 + PI / 4, w = cable([[Math.cos(a) * .066, hgt - .06, z + Math.sin(a) * .066], [Math.cos(a) * .065, hgt - .12, z + Math.sin(a) * .065], [Math.cos(a) * .03, hgt - .16, z + Math.sin(a) * .03], [0, hgt - .165, z]], .0035, cm3, S, 4); } }
    const li = new T.PointLight(0xffd8a0, 1.6, 6.5, 1.6); li.position.set(0, hgt - .25, z); S.add(li); o.bulbs.push({ li, bulb, base: 1.6 });
  }
  // торцевая стена с проёмом двери (за ней — кусок серверной)
  wplane(wd / 2 - .52, hgt, wl, S, -(wd / 4 + .26), hgt / 2, D, 0, 2.4); wplane(wd / 2 - .52, hgt, wl, S, wd / 4 + .26, hgt / 2, D, 0, 2.4); wplane(1.04, hgt - 2.1, wl, S, 0, (hgt + 2.1) / 2, D, 0, 2.4);
  // дверь серверной: железо, открывается наружу (в коридор), петли справа; засов изнутри. Табличка над дверью
  const fr = mat(0x2a2c2e, null, { metal: .6 });
  box(1.04, .08, .1, fr, 0, 2.06, D + .02, S); for (const dx of [-.52, .52]) box(.08, 2.1, .1, fr, dx, 1.05, D + .02, S);
  o.sdoor = new T.Group(); o.sdoor.position.set(.47, 0, D + .02); S.add(o.sdoor);
  const dm = mat(0x6a6e70, TX.metal, { metal: .55, rough: .5 });
  box(.94, 2.0, .05, dm, -.47, 1.0, 0, o.sdoor); rbox(.12, .022, .022, .008, mat(0x9a9fa4, null, { metal: .85, rough: .3 }), -.85, 1.02, .062, o.sdoor);   // полотно, ручка (там же, где была: за неё берутся)
  { const hm2 = mat(0x9a9fa4, null, { metal: .85, rough: .3 }); rbox(.05, .24, .012, .005, hm2, -.895, .97, .031, o.sdoor); const rs2 = lathe([[0, 0], [.02, 0], [.018, .012], [.011, .02], [0, .02]], 10, hm2, o.sdoor, -.895, 1.02, .031); rs2.rotation.x = PI / 2;
    const cy2 = lathe([[0, 0], [.009, 0], [.009, .008], [0, .008]], 10, mat(0x3a3c3e, null, { metal: .8, rough: .35 }), o.sdoor, -.895, .9, .037); cy2.rotation.x = PI / 2;
    for (const y of [.35, 1.65]) { const hg = cyl(.012, .012, .12, 10, fr, o.sdoor); hg.position.set(-.005, y, .03); } }   // накладка, розетка ручки, цилиндр замка, петли
  for (const y of [.25, 1.75]) box(.7, .06, .012, mat(0x50565a, null, { metal: .5 }), -.47, y, .03, o.sdoor);   // рёбра жёсткости
  const sg = label(.7, .16, 70, 16, (c, w, h) => { c.fillStyle = '#1e3a6e'; c.fillRect(0, 0, w, h); c.fillStyle = '#e8eef8'; c.font = '700 10px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(tr('СЕРВЕРНАЯ'), w / 2, h / 2 + 1); }, S, false);
  sg.rotation.y = 0; sg.position.set(0, 2.25, D + .08);
  // полоска света под дверью: свеча за дверью (её свет на полу коридора — мягкое пятно)
  o.slit = plane(.86, .014, mat(0xffb060, null, { basic: true }), S); o.slit.position.set(0, .008, D + .047);   // на 2 мм перед полотном: в его плоскости полоска мерцала
  o.glow = plane(1.2, .6, new T.MeshBasicMaterial({ map: TX.fogBreath, transparent: true, opacity: .55, depthWrite: false, blending: T.AdditiveBlending, color: 0xff9a40 }), S);
  o.glow.rotation.x = -PI / 2; o.glow.position.set(0, .006, D + .3);
  // обход в 22:05: соль насыпана изнутри по всему порогу (видна в щель, когда смотришь у самого пола); тень проходит за дверью мимо щели
  { const sl = ctex(128, 16, (x, w, h) => { x.clearRect(0, 0, w, h); for (let i = 0; i < 900; i++) { const y = h / 2 + (rnd() - .5) * h * (rnd() < .8 ? .45 : .95); x.fillStyle = `rgba(${236 + rnd() * 19 | 0},${232 + rnd() * 20 | 0},${222 + rnd() * 20 | 0},${.5 + rnd() * .5})`; x.fillRect(rnd() * w, y, 1 + rnd() * 1.5, 1 + rnd()); } });
    o.saltLine = new T.Group(); o.saltLine.position.set(0, 0, D - .012); S.add(o.saltLine);   // насыпанный валик соли поперёк порога и россыпь по краям
    const sm = mat(0xe4e0d4, null, { rough: .95 }); const rid = box(.86, .016, .045, sm, 0, .008, 0, o.saltLine); rid.scale.y = 1;
    const top = plane(.86, .085, new T.MeshLambertMaterial({ map: sl, transparent: true, depthWrite: false, emissive: 0x1a140c }), o.saltLine); top.rotation.x = -PI / 2; top.position.y = .0165; ps1(top.material);
    const gr = new T.InstancedMesh(new T.BoxGeometry(.006, .005, .006), sm, 120); gr.frustumCulled = false; o.saltLine.add(gr);
    { const m4 = new T.Matrix4(), q = new T.Quaternion(); for (let i = 0; i < 120; i++) { q.setFromAxisAngle(new T.Vector3(0, 1, 0), rnd() * PI);
      m4.compose(new T.Vector3((rnd() - .5) * .9, .0025, (rnd() < .5 ? -1 : 1) * (.025 + Math.pow(rnd(), 2) * .07)), q, new T.Vector3(1, 1, 1)); gr.setMatrixAt(i, m4); } }
    o.slitShadow = plane(.3, .016, mat(0x000000, null, { basic: true }), S); o.slitShadow.position.set(0, .0085, D + .049); o.slitShadow.visible = false; }
  // за дверью: пол, стены, стойки, круг соли у порога, свеча
  const rm = room(3.4, hgt, 3.6, mat(0x1c1e1e, TX.concrete), S); rm.position.set(0, 0, D - 1.8);
  for (const x of [-1.2, 1.2]) { const m = mat(0xffffff, TX.rack, { emissive: 0x081008 }); m.emissiveMap = TX.rackEm; m.emissive.setHex(0xd0d0d0); box(.6, 2.0, .8, m, x, 1.0, D - 2.6, S); }
  o.salt = plane(1.9, 1.9, CINE ? new T.MeshStandardMaterial({ map: TX.salt, emissiveMap: TX.salt, emissive: 0x8e8c84, roughness: .85, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }) : new T.MeshBasicMaterial({ map: TX.salt, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 }), S);   // как в серверной: освещается
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
  o.reset = () => { o.faceL.intensity = 0; o.faceL.color.setHex(0xdde6ff); o.amb.intensity = .22; S.fog.density = o.fogD; o.sdoor.rotation.y = 0; o.sdoor.position.y = 0; o.saltLine.visible = true; o.slitShadow.visible = false; o.prints.visible = false; o.etorch.intensity = 0; o.roomL.intensity = 0; o.roomL.distance = 4.5; o.roomL.position.set(-.45, 1.1, D - 1.1); o.glow.scale.set(1, 1, 1); o.slit.visible = o.glow.visible = true; o.salt.visible = true; o.candle.intensity = 1;
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
// свет над телом: одна лампа мигает; линолеум в «Кино» здесь матовее — иначе лампа отражалась в полу слепящим овалом между камерой и телом
function eatLight(o, t) { setLamps(o, 0); o.flickOn = false; const l = o.lamps[3], on = Math.sin(t * 9) > .85 && Math.sin(t * 1.7) > 0 ? .25 : 1; l.li.intensity = 2.8 * on; l.li.distance = 8; if (o.flM && o.flM.roughness != null) o.flM.roughness = .62; o.amb.intensity = .42; l.tube.material.color.setHex(on > .5 ? 0xe8f0e8 : 0x30322e); o.blood.visible = true; o.blood.position.set(-.75, .006, -11.8); o.blood.scale.set(1.25, 1, 1); o.gutsOn = true; o.splat.visible = true; o.slitLight.intensity = 0; o.slit.visible = false; A.monster.gore = 1; A.victim.gore = 2;
  o.cleanSet(AFTER); }
// седьмой этаж после уборщика: швабра на полу, ведро опрокинуто, вода, кровавый след волоком к приоткрытой 707
const CLEAN = { bucket: [.6, -10.35, .3], sign: [-.85, -6.55, .5], wet: [-.1, -9.9], door7: .72 }, MOPLEAN = [.43, -10.08, .25, 0];
const AFTER = { mop: [.43, -10.08, .25, 1], bucket: [.6, -10.35, .3, 1], spill: [.82, -10.3], sign: [-.85, -6.55, .5], smear: [.0, -9.6, -1.05, -11.7], door7: .85, splat: 1, blood: 1 };
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
  hall_cam: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]], ['monster', -2.35, 0, -4.55, .5, 'monster', 'cam']] },   // у пальмы, но не в кроне (на 1,4 м от ствола: листья проходили сквозь него)
  hall_cam_empty: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]]] },
  hall_cam_near: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]], ['monster', -1.2, 0, -2.4, .8, 'monster', 'cam']] },
  hall_cam_nearer: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]], ['monster', 2.2, 0, -.6, 1.2, 'monster', 'cam']] },
  // сменщик приходит в шесть: от входа к будке, огибая колонну
  hall_dawn: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: (o, t) => { setDawn(o, 1); const w = walkPath('dawn', [[.3, 5.3], [2.0, 4.9], [3.6, 3.9], [4.4, 1.6]], t + 2.6, 1.1);
    return [['smenshik', w.x, 0, w.z, w.ry, w.done ? 'stand' : 'walk', w.done ? [5.2, 1.5, -.3] : null]]; } },
  exterior: { loc: 'lobby', pos: [2.6, 1.45, 17.5], look: [-.2, 4.6, 6], fov: 66, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]]] },
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
  // ещё два скримера смерти (engine.js выбирает по смерти): фон тонет в тумане — не видно, что это серверная (смерть бывает и в будке, и в коридоре)
  death_scream2: { loc: 'server', pos: [.22, 1.84, .5], look: [.02, 2.12, .02], fov: 46, fog: .9, set: (o, t) => { const C = A.monster; C.faceSet = 'scream'; C.tiltNow = .55 + .1 * Math.sin(t * 31);
    o.amb.intensity = .03; o.cold.intensity = Math.sin(t * 67) > -.2 ? 2.4 : .5; o.cold2.intensity = .8; o.rim.intensity = 1.6; o.red.intensity = .3; o.candle.intensity = 1.6; return [['monster', 0, 0, 0, .25, 'monster', 'cam']]; } },
  death_scream3: { loc: 'server', pos: [-.07, 2.17, .4], look: [.03, 2.1, 0], fov: 46, fog: 1.2, set: (o, t) => { const C = A.monster; C.faceSet = 'scream'; C.tiltNow = -.18;
    const fl = Math.sin(t * 83) + Math.sin(t * 47) > -.4 ? 1 : .08; o.amb.intensity = .02; o.cold.intensity = 0; o.cold2.intensity = 0; o.rim.intensity = 1.2 * fl; o.red.intensity = 1.6 * fl; o.candle.intensity = 1.4 * fl; return [['monster', 0, 0, 0, 0, 'monster', 'cam']]; } },
  death_scream: { loc: 'server', pos: [.02, 2.14, .95], look: [.02, 2.12, 0], fov: 40, set: (o, t) => { A.monster.faceSet = 'scream'; o.amb.intensity = .08; o.cold.intensity = .3; o.cold2.intensity = 0; o.rim.intensity = 2.2; o.red.intensity = .9; o.candle.intensity = 2.6; return [['monster', 0, 0, 0, 0, 'monster', 'cam']]; } },   // скример смерти: пасть раскрыта, свет снизу, красный контур
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
  hall_cam_katya: { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, set: () => [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]], ['katyaThing', -.6, 0, -1.8, .7, 'stand', 'cam']] },
  elevator_13: { loc: 'lobby', pos: [-1.3, 1.5, 2.4], look: [-2.2, 2.35, -6], fov: 46, set: (o, t) => { setLift(o, o.lifts[0], 1, '13'); o.cabLight.intensity = (Math.sin(t * 19) > -.7) ? 2.4 : .5; return []; } },
  cam13_turn: { loc: 'server', pos: [3.6, 2.7, 3.1], look: [0, 1.1, -.4], fov: 66, set: (o, t) => { o.salt.visible = false; const k = clamp((t - 2.6) / 1.4, 0, 1), e = k * k * (3 - 2 * k); return [['monster', .1, 0, -.5, 3.0, 'monster', lin([.5, 1.9, -3.5], [3.6, 2.7, 3.1], e)]]; } },
  guard_dawn_fear: { loc: 'lobby', pos: [5.35, 1.25, -.7], look: [5.12, 1.52, .9], fov: 44, set: (o, t) => { setDawn(o, 1); A.smenshik.faceSet = 'fear'; return [['smenshik', 5.12, 0, .85 + Math.max(0, t - .8) * .22, PI, t > .8 ? 'back' : 'stand', 'cam']]; } },
  // «Я глянул в стекло будки. Я улыбался»: смотрим в стекло перегородки — в нём отражение Тёмыча (грязь, блик, рама)
  booth_reflect: { loc: 'lobby', pos: [5.3, 1.6, -.55], look: [5.12, 1.6, .9], fov: 38, fog: .16, set: (o) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false; o.amb.intensity = .8; o.boothPane.visible = true; A.temych.faceSet = 'smile'; return [['temych', 5.12, 0, .9, PI, 'stand', 'cam']]; } },
  exterior_alone: { loc: 'lobby', pos: [3.2, 1.4, 17.8], look: [2.0, 2.2, 13], fov: 54, set: () => [['temych', 2.3, 0, 15.6, .25, 'stand', 'cam']] },
  basement_door: { loc: 'corridor', pos: [.15, 1.55, -23.5], look: [0, 1.05, -26], fov: 54, set: (o, t) => { setLamps(o, (Math.sin(t * 11) > .55 && Math.sin(t * 2.3) > -.2) ? 1 : 0); o.srv.visible = true; o.slitLight.color.setHex(0xffb060); o.slitLight.intensity = 1.6 + Math.sin(t * 7) * .3; o.amb.intensity = .35; return []; } },
  // тело там, куда его утащили: голова на пороге 707, ноги в коридоре; Жилец сидит над грудью с дальней стороны — лицом к камере
  cam7_eat: { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.35, .6, -11.8], fov: 30, cctv: 1, set: (o, t) => { eatLight(o, t); eatGore(o, t); return [['victim', -.4, LAYY, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 4 ? [-.75, .2, -11.75] : [1.0, 2.75, -.5]]]; } },
  eat_close: { loc: 'corridor', pos: [.62, 1.12, -9.4], look: [-.62, .62, -12.1], fov: 54, set: (o, t) => { eatLight(o, t); eatGore(o, t); return [['victim', -.4, LAYY, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 2.2 ? [-.75, .2, -11.75] : [.62, 1.12, -9.4]]]; } },
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
      return [['victim', -.4, LAYY + .01 * Math.abs(tw), -11.78, PI * .5 + tw * .08, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', t < 4.2 ? [-.75, .2, -11.75] : [1.0, 2.75, -.5]]];
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
  const C = A[name], u = clamp((t - t0) / dur, 0, 1), k = ease(u), d = Math.hypot(b[0] - a[0], b[1] - a[1]), s = k * d, w = Math.sin(PI * u);
  const back = (b[0] - a[0]) * Math.sin(ry) + (b[1] - a[1]) * Math.cos(ry) < -.7 * d ? -1 : 1;   // отходит спиной — шаги назад (клип ходьбы задом наперёд), а не шаг вперёд с ногами, едущими по полу
  return [name, lerp(a[0], b[0], k), 0, lerp(a[1], b[1], k), ry, Object.assign({ clips: [['Walk_Loop', back * s / (1.3 * C.ss) * 1.333, w], ['Idle_Loop', t, 1 - w]], legs: w < .1 ? 'feet' : undefined }, extra), null];
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
  const has = rel == null || t < rel, u = offer == null ? 0 : t - offer;
  // протянул связку — глянул на неё и говорит, глядя Итану в лицо; изредка снова косится на ключи (а не весь разговор в пол)
  const atKeys = w > .5 && has && (u < 1.5 || (u % 6.5) > 5.7 || (rel != null && t > rel - .5));
  return { clips: [['Idle_Loop', t + 3.1, 1]], talk: 1, talkK: 1 - .4 * w, legs: 'feet', spine: { to: PH, k: .15 * w },
    arm: { r: { at: [PH[0] + tr, PH[1] + .095 + tr, PH[2] - .015], by: 'pinch', fdir: [.2, -.3, 1], pdir: [.55, -1, 0], w } },
    fing: { r: [has ? 'pinch' : 'relax', has ? 1 : 1 - seg(t, rel, rel + .3)] }, hold: has ? { r: 'keys' } : null, look: atKeys ? [PH[0], PH[1] + .05, PH[2]] : PEYE_E };
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
// 20 ноя (тестер: «Итана закрывает спина сменщика — он "вырастает" из-за плеча; сменщик 5 с стоит столбом»): камера — сбоку-сзади от сменщика, слева от колонны:
// видно дверь, как Итан входит и идёт через холл; сменщик ждёт — смотрит на часы на руке, услышал дверь — опустил руку, поднял глаза на Итана
SH.pro_enter = { loc: 'lobby', pos: [1.75, 1.6, -.35], look: [.45, 1.35, 5], fov: 50,   /* 22 ноя (владелец): прежний ракурс — из-за стойки на дверь */ set: (o, t) => { leafAng(o, 0, .95 * (1 - ease(clamp(t / 2.6, 0, 1))));   // створка за ним дозакрывается
  const R = locR(PG[0], PG[1], 0), wk = 1 - seg(t, .9, 1.6), G = gKeys(t), wr = R.p(.1, 1.2, .3);
  G.arm = Object.assign({}, G.arm, { l: { at: wr, by: 'wrist', fdir: R.v(-.9, .15, .35), pdir: R.v(.1, -.6, -.8), w: wk, pole: [.6, -1, .2] } }); G.fing = Object.assign({}, G.fing, { l: ['relax', 1] });
  G.look = t < 1.0 ? [wr[0] - .05, wr[1], wr[2]] : null; G.spine = { to: wr, k: .1 * wk };
  const E = walker('temych', [[-.36, 5.5], [.2, 4.2], [.55, 3.3], [PE[0], PE[1]]], t, 1.05, { look: PEYE_G }, PI);
  return [['smenshik', PG[0], 0, PG[1], 0, G, t < 1.0 ? null : [E[1], 1.66, E[3]]], E]; } };
SH.pro_two = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.32, 2.0], fov: 44, face: { smenshik: 'worry' }, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t), PEYE_G]] };
SH.pro_offer = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.3, 2.0], fov: 44, face: { smenshik: 'worry' }, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t, 0), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t), PEYE_G]] };
// keys_ask (p10–p12): на вопрос «Как это — не сдал?» сменщик опускает протянутую связку (не держит руку на весу 15 с), рассказывает, глянув на будку
// на «будка» и в пол на «А Фрэнка нет»; после «камеры ничего не записали» (качает головой) — снова протягивает ключи: следующий кадр — передача (pro_take)
function gTell(t) {
  const q = wordT('p10', /^(сдал|hand|здав|entreg|übergeb|rendu|consegn|oddał)/i, 1.1), bo = wordT('p11', /^(будка|booth|будка|cabina|kabine|cabine|loge|budka)$/i, 2.4, 1.84);
  const no = wordT('p11', /^(нет|gone|нема|немає|no|weg|parti|sparito|nie|ma)$/i, 9.5, 1.84), cam = wordT('p12', /^(камеры|cameras|камери|cámaras|câmeras|kameras|caméras|telecamere|kamery)$/i, 13.9, 10.9);
  const dn = seg(t, q + .1, q + 1.1), up = seg(t, cam + 1.3, cam + 2.1), w = Math.max(1 - dn, up);   // к концу реплики рука снова протянута: следующий кадр (pro_take) начинается с неё
  const P = gKeys(t + 9, 0), tr = (Math.sin(t * 41) * .0022 + Math.sin(t * 23.7) * .0016) * w;
  P.arm.r = Object.assign({}, P.arm.r, { at: [PH[0] + tr, PH[1] + .095 + tr, PH[2] - .015], w }); P.spine = { to: PH, k: .15 * w }; P.talkK = 1 - .4 * w;
  P.look = t > bo - .1 && t < bo + 1.1 ? [5.0, 1.25, -.1] : t > no - .3 && t < no + 1.2 ? [PG[0] - .3, .3, PG[1] + .9] : w > .5 && t > cam + 1.4 && t < cam + 2.4 ? [PH[0], PH[1] + .05, PH[2]] : PEYE_E;
  return P;
}
SH.pro_tell = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.3, 2.0], fov: 44, face: { smenshik: 'worry' }, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gTell(t), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t), PEYE_G]] };
SH.pro_take = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.3, 2.0], fov: 44, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t + 9, 0, 9 + 1.25), PEYE_E] /* отпускает через .25 с после того, как Итан сжал пальцы (было одновременно): связку держат оба, потом сменщик разжимает пальцы */, ['temych', PE[0], 0, PE[1], PI, eKeys(t, .1), PEYE_G]] };
SH.pro_keys = { loc: 'lobby', pos: [1.62, 1.36, 1.86], look: [.92, 1.13, 2.0], fov: 38, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gKeys(t + 9, 0, 9 + 1.05), PEYE_E], ['temych', PE[0], 0, PE[1], PI, eKeys(t, .1), PEYE_G]] };
SH.pro_palm = { loc: 'lobby', pos: [1.25, 1.42, 1.72], look: [.86, 1.05, 2.15], fov: 36, set: (o, t) => [['temych', PE[0], 0, PE[1], PI, eKeys(t + 9, 0), null]] };
// p13: связка в ладони перед грудью, Итан смотрит на неё; пальцы чуть сжимают
function eLook(t) { const R = locR(PE[0], PE[1], PI), k = seg(t, 0, .6);
  const wgh = .012 * Math.sin(t * 2.4) * seg(t, .5, 1.1) * (1 - seg(t, 3.2, 3.8));   // «тёплая» — чуть подбрасывает связку на ладони, взвешивает
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: R.p(.02, 1.08 + wgh, .3), by: 'palm', fdir: R.v(.45, .05, 1), pdir: [0, 1, 0], w: 1 } },
    fing: { r: ['grip', .5 + .15 * Math.sin(t * 1.3)] }, hold: { r: ['keys', 'keys_palm'] }, look: R.p(.02, 1.06, .32), spine: { to: R.p(0, .9, .6), k: .12 * k } }; }
SH.pro_palm = { loc: 'lobby', pos: [1.22, 1.5, 1.62], look: [.88, 1.1, 2.12], fov: 40, set: (o, t) => [['temych', PE[0], 0, PE[1], PI, eLook(t), null]] };
// p14: «Журнал на столе» — сменщик кивает на будку левой рукой и снова смотрит на Итана
function gPoint(t) { const R = locR(PG[0], PG[1], 0), up = seg(t, .3, .9) * (1 - seg(t, 2.1, 2.7)), booth = [5.0, 1.2, -.1];
  const sh = R.p(.2, 1.42, 0), d = nrm([booth[0] - sh[0], booth[1] - sh[1], booth[2] - sh[2]]);
  return { clips: [['Idle_Loop', t + 3.1, 1]], talk: 1, talkK: 1 - .8 * up, legs: 'feet', arm: { l: { at: [sh[0] + d[0] * .42, sh[1] + d[1] * .42 - .13, sh[2] + d[2] * .42], by: 'wrist', fdir: d, pdir: [0, -1, 0], w: up } },   // показывает левой — правая не машет
    fing: { l: ['point', up] }, look: t > .5 && t < 1.6 ? booth : PEYE_E }; }
// Итан слушает: поднял глаза от связки на сменщика, проследил, куда тот кивнул (будка), и снова на него
// 20 ноя (тестер: «пять секунд стоит с раскрытой ладонью у живота, ключи на ней, как на подносе»): сжал связку в кулаке и опустил руку вдоль тела — ключи теперь его
function ePocket(t) { const R = locR(PE[0], PE[1], PI), P = eLook(t + 5), fist = seg(t, .15, .6), dn = seg(t, .5, 1.5);
  P.fing = { r: ['grip', lerp(.6, 1, fist)] }; P.arm.r = Object.assign({}, P.arm.r, { at: lin(R.p(.02, 1.08, .3), R.p(-.24, .86, .1), dn), fdir: lin(R.v(.45, .05, 1), R.v(.1, -1, .25), dn), pdir: lin([0, 1, 0], R.v(1, 0, .1), dn) });
  P.spine = { to: R.p(0, .9, .6), k: .12 * (1 - dn) }; P.talk = 1;
  P.look = t < .25 ? R.p(.02, 1.06, .32) : t > .75 && t < 1.9 ? [5.0, 1.25, -.1] : PEYE_G; return P; }
SH.pro_point = { loc: 'lobby', pos: [2.85, 1.55, 1.72], look: [.92, 1.32, 2.0], fov: 44, set: (o, t) => [['smenshik', PG[0], 0, PG[1], 0, gPoint(t), PEYE_E],
  ['temych', PE[0], 0, PE[1], PI, ePocket(t), PEYE_G]] };
// p15: уже снаружи, под козырьком: три раза костяшками в стекло фасада (z = 6), смотрит на Итана, уходит под дождь
const KN = [1.32, 6.43], KP = [1.27, 1.42, 6.025];   // где стоит, куда приходятся костяшки (наружная сторона стекла)
// секунда слова реплики в кадре — по таймингу языка игры, как react/cutAt в движке (первая реплика звучит с .15 с). Без движка (инструменты) — dflt
function wordT(k, re, dflt, lead = .15) { const TM = window.__ns && window.__ns.tim, e = TM && TM[k], w = e && (e.ew || e.w), h = w && w.find(x => re.test(x[1])); return h ? lead + h[0] : dflt; }
// стук костяшками: разгон к стеклу → касание → отскок (а не ровная «пила» туда-обратно); off — сколько до стекла
function knockOff(t, hits, gap = .075) { let off = gap;
  for (const h of hits) { const d = t - h; if (d > -.13 && d < 0) off = Math.min(off, gap * (1 - Math.pow(1 + d / .13, 2))); else if (d >= 0 && d < .16) off = Math.min(off, gap * (1 - Math.pow(1 - d / .16, 2))); }
  return off; }
// p15 (19 ноя, владелец: «никто не стоит статуей»): раньше постучал и через секунду ушёл, а голос ещё 5 с говорит «посмотрел на меня, будто хотел что-то добавить».
// Теперь: глянул на стекло — три удара (под звук) — поднял глаза на Итана и смотрит; на «будто хотел что-то добавить» вдохнул, губы разомкнулись,
// правая рука пошла вверх — и остановилась; на «И не добавил» рот закрылся, глаза вниз, рука опустилась — повернулся на месте (переступая) и ушёл под дождь
function gKnock(t, hes, gone) {
  const lift = seg(t, .1, .42) * (1 - seg(t, 1.3, 1.95)), off = knockOff(t, [.55, .82, 1.09]);
  const R = locR(KN[0], KN[1], PI), want = seg(t, hes + .1, hes + .6) * (1 - seg(t, hes + 1.15, hes + 1.75));   // рука «хотел сказать»: поднялась к груди — и вниз
  const lean = .07 * lift + .06 * seg(t, hes, hes + .5) * (1 - seg(t, hes + 1.2, hes + 1.8));   // к стеклу, пока стучит; чуть вперёд на вдохе «хотел добавить»
  const P = { clips: [['Idle_Loop', t + 3.1, 1]], legs: 'feet', spine: { to: lift > .05 ? KP : R.p(0, 1.3, 1.5), k: lean },
    arm: { r: lift > .001 ? { at: [KP[0], KP[1], KP[2] + off], by: 'knuck', fdir: [-.1, .15, -1], pdir: [-.8, -.6, 0], w: lift }
      : { at: R.p(-.13, 1.17, .34), by: 'palm', fdir: R.v(-.25, .55, .8), pdir: R.v(.35, .7, -.2), w: .85 * want, pole: [-.3, -1, -.2] } },
    fing: { r: lift > .001 ? ['fist', lift] : ['open', .35 * want] },
    look: t < .7 ? [KP[0] + .02, KP[1], KP[2]] : t > gone - .5 ? [KN[0] + .15, .25, KN[1] - 1.1] : null,   // на стекло — в глаза Итану — в пол
    br: [1 + .9 * seg(t, hes - .1, hes + .3) * (1 - seg(t, hes + .5, hes + 1.2)), 1] };   // вдох перед словами, которые так и не сказал
  return P;
}
function knockFace(t, hes, gone) { A.smenshik.faceSet = t < hes ? 'worry' : t < gone - .45 ? 'unsaid' : 'sad'; }
// глазами Итана: он стоит у турникета, сменщик за стеклом стучит, смотрит прямо на него и уходит под дождь
// пролог: Итан вошёл с дождя — куртка и волосы ещё влажные (wetPass)
for (const id of ['pro_enter', 'pro_two', 'pro_offer', 'pro_tell', 'pro_take', 'pro_keys', 'pro_palm', 'pro_point']) if (SH[id]) SH[id].wet = Object.assign({ temych: .5 }, SH[id].wet);
SH.pro_knock = { loc: 'lobby', pos: [1.78, 1.66, 4.6], look: [1.3, 1.5, 6.4], fov: 44, set: (o, t) => {   // 20 ноя: камера правее — стойка двери закрывала сменщика наполовину (тестер)
  leafAng(o, 0, .55 * (1 - ease(clamp(t / 1.8, 0, 1))));
  const hes = wordT('p15', /^(будто|as|ніби|como|als|comme|come|jakby)$/i, 5.05), no = wordT('p15', /^(не|didn.t|no|não|non|nie)$/i, hes + 1.95), gone = Math.max(hes + 1.5, no - .3);
  const path = [[KN[0], KN[1]], [KN[0] + .6, KN[1] + .5], [KN[0] + 3.5, KN[1] + 1.2]], ryW = Math.atan2(.6, .5), tw = .85;
  knockFace(t, hes, gone);
  if (t < gone) return [['smenshik', KN[0], 0, KN[1], PI, gKnock(t, hes, gone), 'cam']];
  if (t < gone + tw) { const ts = turnSteps(KN[0], KN[1], PI, ryW, t, gone, gone + tw);   // поворот на месте: переступает, а не крутится на пятках
    return [['smenshik', KN[0], 0, KN[1], ts.ry, { clips: [['Idle_Loop', t + 3.1, 1]], feet: ts.feet, look: [KN[0] + 2.5, 1.5, KN[1] + 1.2] }, null]]; }
  return [walker('smenshik', path, t - gone - tw, 1.15, { look: [KN[0] + 6, 1.55, KN[1] + 2] })]; } };
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
    arm: { l: { at: [5.62, .812, -.33], by: 'palm', fdir: [-.2, 0, 1], pdir: [0, -1, 0], w: 1 },   // не ведёт по строкам — правая ладонь тоже на столе, справа от журнала (у мокапа опущенная кисть уходила в сиденье)
      r: a ? { at: a, by: 'tip', fdir: [.15, -.55, 1], pdir: [0, -1, .3], w: 1 } : { at: [4.98, .835, -.42], by: 'palm', fdir: [.2, 0, 1], pdir: [0, -1, 0], w: 1 } },
    fing: { r: [a ? 'point' : 'flat', a ? 1 : .95], l: ['flat', .7] }, look: a || [5.33, .81, -.27] };
}
// j01–j02 (19 ноя): раньше сидел над журналом не шевелясь. Теперь «всё откладывал» — смотрит мимо, на мониторы; «сел за него» — выпрямился, подался к журналу,
// палец лёг на первую строку; j02 (тот же кадр, время идёт дальше) — палец идёт по строкам, как в jr_p*
function eDesk(t) {
  if (t > 4.2) return eRead(t - 4.2, [['l', 0], ['l', 1], ['l', 2], ['l', 3]], 10);
  const P = eRead(t, null, 1), lean = seg(t, 1.7, 3.3), go = seg(t, 2.9, 4.2), st = logPt(14, LOGL(0));
  P.spine = { to: [5.33, .8, -.27], k: lerp(.14, .42, lean) };
  P.arm.r = { at: lin([4.98, .835, -.42], st, go), by: 'tip', fdir: lin([.2, -.3, 1], [.15, -.55, 1], go), pdir: lin([0, -1, .1], [0, -1, .3], go), w: 1 };
  P.fing.r = [go > .4 ? 'point' : 'flat', go > .4 ? 1 : .95];
  P.look = t < 1.7 ? [4.5, 1.2, .1] : t < 2.9 ? [5.33, .81, -.27] : st;
  return P;
}
SH.jr_desk = { loc: 'lobby', pos: [6.55, 1.55, -.05], look: [5.25, .95, -.55], fov: 46, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eDesk(t), null]] };
const jrPage = (rows, dur, look = [5.35, .8, -.17]) => ({ loc: 'lobby', pos: [5.36, 1.16, -.5], look, fov: 40, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eRead(t, rows, dur), null]] });
SH.jr_p12 = jrPage([['l', 4], ['l', 5]], 4.5, [5.42, .8, -.2]);
SH.jr_p15 = jrPage([['r', 0], ['r', 1], ['r', 2]], 6, [5.27, .8, -.13]);
SH.jr_p18 = jrPage([['r', 3], ['r', 4]], 5.5, [5.27, .8, -.17]);
SH.jr_p20 = jrPage([['r', 5], ['r', 6], ['r', 7], ['r', 8]], 9, [5.27, .8, -.2]);
SH.jr_p21 = jrPage([['r', 9], ['r', 10]], 5.5, [5.27, .8, -.26]);
// j08 «Я решил, что старик сошёл с ума…» (19 ноя): раньше тот же кадр, что j01, — Итан сидел над журналом, как над первой строкой. Теперь откинулся от журнала,
// зажал пальцами переносицу (устал, глаза закрыты), выдохнул; рука вернулась на стол, взгляд — мимо журнала, на мониторы (следующий кадр — свет на седьмом)
function headPt(C, side, up, fwd) { const hb = C.bone.Head, h = hb.getWorldPosition(new T.Vector3()), q = hb.getWorldQuaternion(new T.Quaternion()), f = C.headFwd.clone().applyQuaternion(q).normalize(),
  s = new T.Vector3(0, 1, 0).cross(f).normalize(), u = f.clone().cross(s).normalize(); return { p: h.addScaledVector(s, side).addScaledVector(u, up).addScaledVector(f, fwd), f, s, u }; }
function eBack(t) {
  const P = eRead(t, null, 1), back = seg(t, .15, 1.1) * (1 - .45 * seg(t, 3.9, 5.0)), rub = seg(t, .55, 1.25) * (1 - seg(t, 2.75, 3.4)), desk = [4.98, .835, -.42];
  P.clips = [['Sitting_Idle_Loop', t + 2, 1]];
  P.spine = { to: lin([5.33, .8, -.27], [5.2, 2.4, -1.25], back), k: lerp(.42, .13, back) };
  P.arm.r = { by: rub > .5 ? 'pinch' : 'palm', w: 1, pole: [-.4, -1, -.1],
    at: C => { const F = headPt(C, 0, -.045, .1); return lin(desk, F.p.toArray(), rub); },
    fdir: C => { const F = headPt(C, 0, 0, 0); return lin([.2, 0, 1], F.u.clone().multiplyScalar(.75).addScaledVector(F.f, -.65).toArray(), rub); },
    pdir: C => { const F = headPt(C, 0, 0, 0); return lin([0, -1, 0], F.f.clone().multiplyScalar(-1).toArray(), rub); } };
  P.fing.r = rub > .3 ? ['pinch', rub] : ['flat', .95];
  P.look = rub > .2 ? [5.25, .86, -.5] : t < 1 ? [5.33, .81, -.27] : [4.55, 1.2, .1];
  P.br = [1 + 1.2 * seg(t, .3, .9) * (1 - seg(t, 1.4, 2.4)), .8];   // глубокий выдох
  A.temych.faceSet = rub > .35 ? 'tired' : 'grim';
  return P;
}
SH.jr_back = { loc: 'lobby', pos: [6.55, 1.55, -.05], look: [5.25, .95, -.55], fov: 46, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eBack(t), null]] };
// ---- 21:06, будка Фрэнка: осмотр (сцена booth). Взгляд от левого края стола: куртка на стуле, журнал, фото под стеклом, ящик — порознь; журнал ещё перевязан ----
function boothFb(o) { o.note.visible = o.noteCv.visible = false; o.book.visible = true;
  if (!o.fbRest) { const keep = [o.book, o.saltPack, o.fphone].map(m => [m, m.position.clone(), m.quaternion.clone()]);   // как лежали при постройке
    o.fbRest = () => { for (const [m, p, q] of keep) { m.position.copy(p); m.quaternion.copy(q); } o.fphone.children.forEach(c => { c.visible = true; }); }; } }
// 21 ноя: кабинет охраны от первого лица (смена, кнопка «Кабинет»): глаза Итана над его стулом, голову крутит игрок — window.__offYaw
// (0 — через стекло в холл, к лифтам; +0,6 — мониторы; +1,6 — стол с лампой; +2,6 — дверь и куртка Фрэнка). Холл живой: всё, что выпало на CAM 01/02, видно своими глазами
SH.office = { loc: 'lobby', pos: [SEAT[0], 1.2, SEAT[1]], look: [SEAT[0] - 1, 1.1, SEAT[1]], fov: 72, set: (o, t, sh) => {
  boothGlassK(o, 1); const a = window.__offYaw || 0, b = Math.sin(t * 1.55) * .006;   // дыхание — голова чуть ходит
  sh.pos = [SEAT[0], 1.2 + b, SEAT[1]]; sh.look = [SEAT[0] - Math.cos(a), 1.1 + (window.__offPitch || 0) + b, SEAT[1] + Math.sin(a)];
  const A = []; for (const k of ['cam02', 'cam01']) for (const x of SH[k].set(o, t, sh) || []) if (x[0] !== 'temych') A.push(x);   // себя не видим
  return A; } };
// стекло кабинета: изнутри — как было; снаружи (обход пешком) под углом слой грязи + аддитивный блик давали белый лист — приглушаем
function boothGlassK(o, k) { for (const pg of o.boothGlass || []) pg.traverse(m => { const M = m.material; if (!M || M.depthWrite !== false || !M.transparent) return;
  if (M.userData.op0 == null) M.userData.op0 = M.opacity; M.opacity = M.userData.op0 * k; }); }
// 21 ноя: обход пешком от первого лица — window.__walk = { x, z, yaw, pitch, bob, floor }: холл, коридор 7/10 этажа, подвал (−2)
const walkShot = (loc, cams) => ({ loc, pos: [0, 1.62, -3], look: [0, 1.55, -4], fov: 74, set: (o, t, sh) => {
  if (loc === 'lobby') { boothGlassK(o, .3); if (o.brLed) o.brLed.material.color.setHex(SHS().hallDark ? 0xff3020 : 0x3dff6a); }
  const w = window.__walk || { x: 0, z: -3, yaw: -PI / 2, pitch: 0, bob: 0 }, y = 1.62 + Math.sin(w.bob) * .025;
  sh.pos = [w.x, y, w.z]; sh.look = [w.x - Math.cos(w.yaw), y - .06 + w.pitch, w.z + Math.sin(w.yaw)];
  const A = []; for (const k of (typeof cams === 'function' ? cams() : cams)) for (const x of SH[k].set(o, t, sh) || []) if (x[0] !== 'temych') A.push(x);
  window.__walkActors = A.map(x => [x[0], x[1], x[3]]);   // люди и «она» твёрдые: обход не проходит сквозь них
  return A; } });
SH.walk = walkShot('lobby', ['cam02', 'cam01']);
SH.walk_c = walkShot('corridor', () => [(window.__walk || {}).floor === 10 ? 'cam10' : 'cam07']);
SH.walk_b = walkShot('basement', ['cam_m2']);
SH.fb_view = { loc: 'lobby', pos: [4.0, 1.5, -1.02], look: [5.3, .82, -.44], fov: 48, set: (o) => { boothFb(o); o.boothLight.intensity = 1.1; o.saltPack.visible = o.saltGr.visible = true; return []; } };
// этап 1 (19 ноя): пропуск Фрэнка на шнурке у края пульта (o.badge) — крупно, глазами Итана («лицо с пропуска над моим столом», s06)
SH.fb_badge = { loc: 'lobby', pos: [4.32, 1.37, -.6], look: [4.05, 1.32, -.21], fov: 30, set: (o) => { boothFb(o); o.boothLight.intensity = 1.1; return []; } };
// ---- осмотр будки (19 ноя, владелец: «Итан в будке берёт и кладёт предметы»). Раньше fb02/fb04/fb05 были пустыми крупными планами вещей «его глазами»,
// а голос говорил «я поставил его на зарядку», «я отложил его». Теперь Итан в кадре сам достаёт пачку соли из кармана, телефон из ящика, берёт журнал —
// и кладёт обратно; вещь идёт из руки на место плавно (без скачка), на месте лежит ровно там, где её потом видно в будке
const _fbq = new T.Quaternion();
function handAxes(C, sd) {   // оси кисти в мире: X — вдоль пальцев, Y — из ладони, Z — поперёк (как handFrame в chars2.js)
  C.root.updateWorldMatrix(true, true); const w = n => C.bone[n + '_' + sd].getWorldPosition(new T.Vector3());
  const h = w('hand'), m = w('middle_01'), i = w('index_01'), p = w('pinky_01'), X = m.clone().sub(h).normalize();
  const Y = i.sub(p).cross(m.sub(h)).normalize(); if (sd === 'l') Y.negate(); Y.addScaledVector(X, -Y.dot(X)).normalize(); return { X, Y, Z: X.clone().cross(Y) }; }
// вещь сцены (не prop3) — в руке или на месте: k = 0 — лежит (rest: мировые позиция и поворот), 1 — в кисти (inHand(H, P) → {p, q} в мире)
function carryObj(obj, rest, C, sd, by, inHand, k) {
  let p = rest.p.clone(), q = rest.q.clone();
  if (k > 0) { const H = handAxes(C, sd), P0 = window.__W3chars.handPt(C, sd, by), h = inHand(H, P0); p.lerp(h.p, k); q.slerp(h.q, k); }
  const par = obj.parent; par.updateWorldMatrix(true, false); obj.position.copy(par.worldToLocal(p)); obj.quaternion.copy(par.getWorldQuaternion(_fbq).invert().multiply(q)); obj.updateMatrixWorld(true); }
const restOf = obj => { obj.updateWorldMatrix(true, false); return { p: obj.getWorldPosition(new T.Vector3()), q: obj.getWorldQuaternion(new T.Quaternion()) }; };
const basisQ = (x, y, z) => new T.Quaternion().setFromRotationMatrix(new T.Matrix4().makeBasis(x, y, z));
// ключевые точки руки: [секунда, точка, fdir, pdir] — между ними плавно (ease)
function armKeys(t, K) { let i = 0; while (i < K.length - 1 && t >= K[i + 1][0]) i++; const a = K[i], b = K[Math.min(i + 1, K.length - 1)], k = b === a ? 0 : seg(t, a[0], b[0]);
  return { at: lin(a[1], b[1], k), fdir: lin(a[2], b[2], k), pdir: lin(a[3], b[3], k) }; }
// fb02: Итан у второго стула — правой достаёт из кармана куртки пачку соли, разглядывает, встряхивает (полупустая), кладёт обратно;
// на «горсть» — сиденье крупно, его глазами
const FBJ = [5.8, -.72], FBJ_R = 1.5;   // сбоку от сиденья (угол сиденья — в 14 см от колена), спиной к стене будки
function fbJacket(o, t) {
  const R = locR(FBJ[0], FBJ[1], FBJ_R), pk = o.saltPack.parent; pk.updateWorldMatrix(true, false); o.ch2.updateWorldMatrix(true, false);
  const pocket = pk.localToWorld(new T.Vector3(.01, .075, -.004)).toArray(), heap = o.ch2.localToWorld(new T.Vector3(.06, .53, .1)).toArray();
  const hand = R.p(-.07, 1.0, .4);   // пачку разглядывает пониже, над сиденьем: её видно в кадре у кармана
  const hn = wordT('fb02', /^(горсть|handful|жменя|handvoll|puñado|poignée|manciata|garść|punhado|avuç)$/i, 5.08);
  const shake = Math.sin(clamp((t - 2.75) / .5, 0, 1) * PI * 2) * .035;
  // левой рукой: камера справа от Итана, правая (ближняя) рука закрывала бы пачку
  const K = [[0, R.p(.2, .95, .12), R.v(0, -.3, 1), R.v(1, 0, 0)], [.35, R.p(.2, .95, .12), R.v(0, -.3, 1), R.v(1, 0, 0)],
    [.95, [pocket[0], pocket[1] + .04, pocket[2]], [0, -1, .1], R.v(1, 0, .3)], [1.45, [pocket[0], pocket[1] + .1, pocket[2]], [0, -.6, .3], R.v(1, 0, .3)],
    [2.0, hand, R.v(-.3, .6, .2), R.v(.2, .2, -1)], [3.4, [hand[0], hand[1] + shake, hand[2]], R.v(-.3, .6, .2), R.v(.2, .2, -1)],
    [4.0, [pocket[0], pocket[1] + .05, pocket[2]], [0, -1, .1], R.v(1, 0, .3)], [4.4, R.p(.15, 1.0, .25), R.v(0, -.5, 1), R.v(1, 0, 0)],
    [Math.max(4.9, hn), R.p(.2, .93, .1), R.v(0, -.4, 1), R.v(1, 0, 0)]];   // убрал пачку — рука вниз, взгляд на сиденье (дальше «горсть» — его глазами, fb_salt)
  const A0 = armKeys(t, K), hold = t > 1.25 && t < 3.95;
  const lean = .18 + .24 * (seg(t, .4, .95) * (1 - seg(t, 1.45, 2.0)) + seg(t, 3.5, 4.0) * (1 - seg(t, 4.0, 4.5)) + seg(t, hn - .2, hn + .5));
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'plant', pelvis: R.v(0, -.05 * lean, -.06 * lean), spine: { to: heap, k: lean },
    arm: { l: Object.assign({ by: 'palm', w: seg(t, .1, .5) * (1 - .6 * seg(t, 4.4, 5.0)), pole: [.4, -1, -.3] }, A0) },
    fing: { l: hold ? ['grip', .55] : ['open', .5 * (1 - seg(t, 4.2, 4.8))] },
    look: t < .9 ? pocket : t < 3.95 ? hand : t < 4.4 ? pocket : heap,
    post: () => carryObj(o.saltPack, o.fbPack, A.temych, 'l', 'palm', (H, P0) => ({ p: P0.clone().addScaledVector(H.Y, .022).addScaledVector(H.X, .045), q: basisQ(H.Z, H.X, H.Y) }),
      seg(t, 1.15, 1.35) * (1 - seg(t, 3.85, 4.05))) };
}
function fbJ(o, t) { boothFb(o); o.boothLight.intensity = 1.1; o.saltPack.visible = o.saltGr.visible = true; if (!o.fbPack) o.fbPack = restOf(o.saltPack);
  const P = fbJacket(o, t), post = P.post; delete P.post; o.fbPost = post; return [['temych', FBJ[0], 0, FBJ[1], FBJ_R, P, null]]; }
SH.fb_jacket = { loc: 'lobby', pos: [5.82, 1.26, -.24], look: [6.3, .9, -.7], fov: 46, set: (o, t) => fbJ(o, t), post: o => o.fbPost && o.fbPost() };   // с той же точки, что и раньше (на карман, лампа за кадром): Итан стоит слева у края кадра, тянется к карману левой
SH.fb_salt = { loc: 'lobby', pos: [5.72, 1.0, -.9], look: [6.27, .52, -.52], fov: 36, set: (o, t) => { fbJ(o, t); return []; }, post: o => o.fbPost && o.fbPost() };   // «горсть соли»: его глазами, присел к сиденью (сиденье наполовину под столом — сбоку его не снять)
// fb04: сидит за столом — выдвинул ящик, достал телефон Фрэнка, глянул на экран (разряжен), положил на стол и подключил зарядку, задвинул ящик
const FPH = [4.6, .8125, -.36];
function fbPhone(o, t) {
  const put = wordT('fb04', /^(поставил|put|поставив|steckte|puse|mis|misi|podłączyłem|coloquei|taktım)$/i, 3.73);
  const dz = -.44 - .22 * seg(t, .5, 1.0) * (1 - seg(t, put + 1.35, put + 1.9)); o.drawer.position.z = dz;
  // телефон берёт щепотью (большой и указательный — за края), корпус идёт вперёд от кончиков пальцев: его видно, а не прячет ладонь
  const hdl = [4.78, .712, dz - .03], F0 = [.25, 0, .97], inD = [4.668, .707, dz + .052], V = [4.98, 1.0, -.6], onD = [FPH[0] - .0125, FPH[1] + .01, FPH[2] - .0485];
  const K = [[0, [4.98, .835, -.42], [.2, 0, 1], [0, -1, 0]], [.45, hdl, [0, -.5, 1], [0, -.8, -.4]], [1.0, [hdl[0], hdl[1], hdl[2]], [0, -.5, 1], [0, -.8, -.4]],
    [1.5, inD, F0, [0, -1, 0]], [2.1, V, [0, .55, .85], [0, -.85, .5]], [put - .15, [V[0] + .02, V[1] - .02, V[2]], [0, .55, .85], [0, -.85, .5]],
    [put + .5, onD, F0, [0, -1, 0]], [put + .8, [onD[0], onD[1] + .04, onD[2] - .04], F0, [0, -1, 0]],
    [put + 1.35, [4.78, .7, -.73], [.1, -1, .1], [0, 0, 1]], [put + 1.9, [4.78, .7, -.51], [.1, -1, .1], [0, 0, 1]], [put + 2.5, [4.98, .835, -.42], [.2, 0, 1], [0, -1, 0]]];
  const A0 = armKeys(t, K), carryK = seg(t, 1.45, 1.6) * (1 - seg(t, put + .45, put + .6)), P = eRead(t, null, 1);
  P.spine = { to: [4.8, .72, -.5], k: .2 + .2 * (seg(t, .2, .6) * (1 - seg(t, 1.6, 2.1)) + seg(t, put + 1.1, put + 1.4) * (1 - seg(t, put + 1.9, put + 2.4))) };
  P.arm.r = Object.assign({ by: 'pinch', w: 1, pole: [-.5, -1, -.2] }, A0);
  P.fing.r = carryK > .3 ? ['pinch', 1] : t < 1.05 && t > .4 ? ['grip', .7] : ['flat', .9];
  P.look = t < 1.5 ? [4.75, .7, dz + .05] : t < put + .6 ? A0.at : t < put + 1.9 ? [4.78, .7, dz] : FPH;
  const inDr = { p: new T.Vector3(inD[0], .697, inD[2]), q: new T.Quaternion().setFromAxisAngle(new T.Vector3(0, 1, 0), .25) }, onDesk = o.fbPhoneRest;
  P.post = () => { o.fphone.visible = true; const cab = t > put + .55; o.fphone.children.slice(-2).forEach(c => { c.visible = cab; });   // провод — когда подключил
    carryObj(o.fphone, t < 2 ? inDr : onDesk, A.temych, 'r', 'pinch', (H, P0) => ({ p: P0.clone().addScaledVector(H.X, .05), q: basisQ(H.Z, H.Y.clone().negate(), H.X) }), carryK); };
  return P;
}
SH.fb_phone = { loc: 'lobby', pos: [5.3, 1.64, .22], look: [4.86, .92, -.62], fov: 50, set: (o, t) => { boothFb(o); if (!o.fbPhoneRest) o.fbPhoneRest = restOf(o.fphone);
  const P = fbPhone(o, t); o.fbPost = P.post; delete P.post; return [['temych', SEAT[0], 0, SEAT[1], 0, P, null]]; }, post: o => o.fbPost && o.fbPost() };
// fb05: берёт журнал двумя руками за края, подносит, читает обложку «До трёх. Весь.» (план через плечо), на «отложил» кладёт на место и отпускает
function fbLog(o, t) {
  const put = wordT('fb05', /^(отложил|aside|відклав|legte|dejé|mis|misi|odłożyłem|deixei|kenara)$/i, 5.79), lift = seg(t, .4, 1.3) * (1 - seg(t, put - .1, put + .6));
  const rest = o.fbBookRest, c = rest.p.clone().lerp(new T.Vector3(5.26, .96, -.53), lift), q = new T.Quaternion().setFromEuler(new T.Euler(-.55 * lift, .12 * (1 - lift), 0, 'YXZ'));   // наклон ~30°: обложка к глазам, книга не «ставится на ребро»
  const bx = new T.Vector3(1, 0, 0).applyQuaternion(q), bz = new T.Vector3(0, 0, 1).applyQuaternion(q), grab = seg(t, 0, .4) * (1 - seg(t, put + .65, put + 1.1));
  const P = eRead(t, null, 1), L0 = P.arm.l, R0 = P.arm.r, edge = s => c.clone().addScaledVector(bx, .118 * s).addScaledVector(bz, -.03).toArray();
  P.arm.l = { by: 'palm', w: 1, at: lin(L0.at, edge(1), grab), fdir: lin(L0.fdir, bz.toArray(), grab), pdir: lin(L0.pdir, bx.clone().negate().toArray(), grab) };
  P.arm.r = { by: 'palm', w: 1, at: lin(R0.at, edge(-1), grab), fdir: lin(R0.fdir, bz.toArray(), grab), pdir: lin(R0.pdir, bx.toArray(), grab) };
  P.fing = { l: ['grip', .35 + .3 * grab], r: ['grip', .35 + .3 * grab] }; P.spine = { to: c.toArray(), k: .3 - .12 * lift };
  P.look = t < put + 1.1 ? c.toArray() : [4.55, 1.2, .1];
  o.book.position.copy(c); o.book.quaternion.copy(q);
  return P;
}
function fbL(o, t) { boothFb(o); if (!o.fbBookRest) o.fbBookRest = restOf(o.book); return [['temych', SEAT[0], 0, SEAT[1], 0, fbLog(o, t), null]]; }
SH.fb_log = { loc: 'lobby', pos: [5.75, 1.55, .22], look: [5.25, .95, -.62], fov: 46, set: (o, t) => fbL(o, t) };   // спереди-справа, через стол (лампа — за кадром)
SH.fb_logc = { loc: 'lobby', pos: [4.86, 1.36, -1.1], look: [5.26, .93, -.52], fov: 38, set: (o, t) => fbL(o, t) };   // через правое плечо: обложка «До трёх. Весь.» в его руках
// 22:00: звонок дневного — Итан за столом, телефон у левого уха (камера справа, у стойки)
// ладонь у уха — от самой головы этого кадра: бок, верх и «вперёд» головы (C.headFwd), а не от корпуса. sd: 'l' | 'r'
function earArm(sd, pole) { const s = sd === 'l' ? 1 : -1, fr = C => { const hb = C.bone.Head, h = hb.getWorldPosition(new T.Vector3()), f = C.headFwd.clone().applyQuaternion(hb.getWorldQuaternion(new T.Quaternion())).normalize(),
    side = new T.Vector3(0, 1, 0).cross(f).normalize(), up = f.clone().cross(side).normalize(); return { h, f, side, up }; }, mix = (F, a, b, c) => F.side.clone().multiplyScalar(a * s).addScaledVector(F.up, b).addScaledVector(F.f, c);
  return { ear: 1, by: 'palm', w: 1, pole, at: C => { const F = fr(C); return F.h.add(mix(F, .097, -.078, .024)).toArray(); }, fdir: C => mix(fr(C), 0, .75, -.66).toArray(), pdir: C => mix(fr(C), -1, 0, 0).toArray() }; }
function ePhoneSit(t) { const R = locR(SEAT[0], SEAT[1], 0);   // ладонь у уха, запястье у скулы перед ухом, пальцы назад вокруг телефона, локоть вниз-вперёд (замер tools/anim/phone_probe.js: локоть на 4 см ниже плеча; было на 19 см выше)
  return { clips: [['Sitting_Idle_Loop', t + 2, 1]], talk: 1, arm: { l: earArm('l', [.45, -1, .35]) },   /* локоть вниз, чуть в сторону и вперёд (pole — направление в осях тела; раньше сюда попадала точка мира, и локоть задирался вверх-назад: владелец, 12 ноя) */   // talk — сам говорит в трубку: корпус и свободная правая «говорят»
    fing: { l: ['phone', 1] }, hold: { l: ['phone', 'phone_ear'] }, look: R.p(.6, 1.05, 2) }; }
// 19 ноя («никто не стоит статуей»): слушает — свободная правая ложится на стол и пальцы перебирают по столешнице, взгляд то сквозь стекло, то на стол;
// заговорил сам — рука уходит со стола и «говорит» (клип), как раньше. Переход ~0,4 с (A.temych.talkingNow — с прошлого кадра)
function ePhoneLive(t) { const P = ePhoneSit(t), C = A.temych, lt = C._pt ?? t, dt = clamp(t - lt, 0, .1); C._pt = t;
  C._dk = t < lt - .2 ? 1 : lerp(C._dk ?? 1, C.talkingNow ? 0 : 1, Math.min(1, dt * 3)); const k = ease(C._dk);
  if (k > .01) { const slide = .025 * Math.sin(t * .37), drum = (i) => { const u = ((t * 1.1 + i * .13) % 3.1) / .22; return u < 1 ? Math.sin(u * PI) : 0; };
    P.arm.r = { at: [5.0 + slide, .835, -.43], by: 'palm', fdir: [.25, 0, 1], pdir: [0, -1, 0], w: k, pole: [-.4, -1, -.2] };
    P.fing.r = ['flat', .85 * k]; P.wig = ['index', 'middle', 'ring', 'pinky'].map((f, i) => [f + '_01_r', [1, 0, 0], -.35 * k * drum(3 - i)]);
    const away = Math.sin(t * .29 + 1.3) > .35; P.look = away ? [5.3, .84, -.35] : P.look; }
  return P; }
SH.booth_call = { loc: 'lobby', pos: [6.35, 1.48, .05], look: [5.15, 1.15, -.75], fov: 42, set: (o, t) => { boothFb(o); return [['temych', SEAT[0], 0, SEAT[1], 0, ePhoneLive(t), null]]; } };
// stub: следующие кадры пролога — ниже
// ---- Гас моет пол: тряпка ходит восьмёркой перед ним, корпус поворачивается за шваброй, руки на черенке (правая сверху), вес с ноги на ногу ----
function locR(x, z, ry) { const c = Math.cos(ry), s = Math.sin(ry);   // оси персонажа (x — влево, z — вперёд) → мир
  return { p: (lx, ly, lz) => [x + lx * c + lz * s, ly, z - lx * s + lz * c], v: (lx, ly, lz) => [lx * c + lz * s, ly, -lx * s + lz * c] }; }
const nrm = v => { const L = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / L, v[1] / L, v[2] / L]; }, crs = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
// цель руки у тела (ухо, рот, грудь). Цели писались от корня под позу Quaternius; мокап сидит и стоит иначе (голова ниже или выше, корпус ровнее, покачивается),
// поэтому цель едет с костью этого кадра: кость + (цель − где эта кость у Quaternius). ref — та кость в осях персонажа (x — влево, z — вперёд) в позе Quaternius
const _bw = new T.Vector3();
const bodyAt = (R, bone, ref, at) => C => { const b = C.bone[bone]; if (!b) return R.p(at[0], at[1], at[2]); b.getWorldPosition(_bw); const d = R.v(at[0] - ref[0], at[1] - ref[1], at[2] - ref[2]); return [_bw.x + d[0], _bw.y + d[1], _bw.z + d[2]]; };
const E_SIT_HEAD = [.003, 1.218, -.249], G_HEAD = [-.006, 1.538, -.06], G_S3 = [.007, 1.114, -.116];   // замерено: голова Итана сидя (Sitting_Idle_Loop), голова и грудь Гаса стоя (Idle_Loop)
function mopGus(o, x, z, ry, t, k = 1) {   // k — насколько моет (0 — швабра стоит, руки на черенке)
  if (window.__W3chars.hasClip('Cmu_Mop_Loop')) return mopGusMocap(o, x, z, ry, t, k);
  const ph = t * 2 * PI / 1.75, sx = Math.sin(ph) * k, cz = Math.cos(2 * ph) * k, R = locR(x, z, ry);
  const head = R.p(sx * .38, MOP_Y, .8 + .05 * cz), piv = R.p(sx * .1 - .05, .97, .18);
  const u = nrm([piv[0] - head[0], piv[1] - head[1], piv[2] - head[2]]), at = d => [head[0] + u[0] * d, head[1] + u[1] * d, head[2] + u[2] * d];
  window.__W3chars.propAim('mop', o.S, head, u, ry);
  const side = R.v(1, 0, 0), pr = nrm(crs(u, crs(side, u))), pl = pr.map(v => -v);   // ладонь правой — к черенку слева, левой — справа
  return { clips: [['Idle_Loop', t + 2, 1]], legs: 'plant', pelvis: R.v(sx * .03, -.07, .02), spine: { to: R.p(sx * .25, .1, 1.0), k: .32 },
    arm: { r: { at: at(1.1), by: 'palm', fdir: crs(pr, u), pdir: pr, pole: R.v(-1, -.4, -.4) }, l: { at: at(.7), by: 'palm', fdir: crs(u, pl), pdir: pl, pole: R.v(1, -.8, .1) } },
    fing: { r: ['grip', 1], l: ['grip', 1] }, look: head };
}
// моет по-настоящему: мокап уборщика со шваброй (CMU 14_13 — ходит со шваброй взад-вперёд, корпус и руки работают).
// Черенок этого же кадра идёт через середину между кистями, вниз к полу: наклон — как у рук в мокапе, но не положе, чем нужно, чтобы черенка хватило
// до верхней (левой) руки (40–72°); обе кисти берутся за свои ближайшие точки черенка — сдвиг на сантиметры. Раньше черенок шёл от левой руки под 55°,
// а правая тянулась к точке на 46 см ниже по линии прошлого кадра — туда рука не доставала (от плеча 0,63–0,76 м при длине руки 0,54 м), кисть висела
// в 7–15 см от черенка. Колодка — на полу ровно (шарнир, propAim). k < 1 — руки уходят к стоящей швабре (поза «перестал мыть»)
const MOP_Y = .004, MOP_LEN = 1.52;
// прислонена к стене коридора (x = −1,3): верх черенка (1,58 м) касается стены; лежит на полу (камера 07 после 03:00): шарнир на верху колодки, черенок к полу
const MOPWALL = [-1.05, MOP_Y, -10.05], MOPWALL_D = nrm([-.147, .989, .031]), MOPDOWN = [.3, .05, -10.45], MOPDOWN_D = nrm([-.93, -.026, .36]);
   // высота колодки (тряпка касается пола, не в одной плоскости с ним); дальше 1,38 м по черенку от колодки руке держаться не за что
// стоит со шваброй (руки не моют): черенок в правой руке сбоку, у бедра (кисть до плеча ~0,5 м — достаёт), колодка на полу сбоку от правой стопы, вдоль неё;
// черенок отвесный, верх — чуть вперёд, в 27 см от плеча (раньше верх клонился к телу: локоть, плечо, а у двери и голова были на черенке)
const GUS_SIDE = R => { const head = R.p(-.42, MOP_Y, .16), top = R.p(-.38, 1.02, .22); return { head, top, u: nrm([top[0] - head[0], top[1] - head[1], top[2] - head[2]]) }; };   // колодка вдоль стопы (yaw + 90°)
function mopClear(o, h) {   // колодка (0,36 × 0,1) остаётся в коридоре и не входит в ведро и табличку
  if (o.wd) h[0] = clamp(h[0], -o.wd / 2 + .22, o.wd / 2 - .22);
  for (const [ob, r] of [[o.bucket, .4], [o.sign, .42]]) if (ob && ob.visible) { const dx = h[0] - ob.position.x, dz = h[2] - ob.position.z, d = Math.hypot(dx, dz); if (d < r) { const q = d > .01 ? r / d : 0; h[0] = ob.position.x + (q ? dx * q : 0); h[2] = ob.position.z + (q ? dz * q : r); } }
  if (o.wd) { h[0] = clamp(h[0], -o.wd / 2 + .22, o.wd / 2 - .22);   // прижало к стене рядом с предметом — уходит вдоль стены
    for (const [ob, r] of [[o.bucket, .4], [o.sign, .42]]) if (ob && ob.visible) { const dx = h[0] - ob.position.x, dz = h[2] - ob.position.z; if (Math.hypot(dx, dz) < r) h[2] = ob.position.z + (dz < 0 ? -1 : 1) * Math.sqrt(r * r - dx * dx); } }
  return h; }
function mopGusMocap(o, x, z, ry, t, k) {
  const R = locR(x, z, ry), W3 = window.__W3chars, w = 1 - k, side = R.v(1, 0, 0), SD = GUS_SIDE(R);
  const P = { clips: [['Cmu_Mop_Loop', t, Math.max(k, .001)], ['Idle_Loop', t + 2, w]], fing: { r: ['grip', 1], l: ['grip', k > .5 ? 1 : k * 2] }, look: R.p(0, 0, 1.2), arm: {},   // arm {} — держит черенок: грудь за головой не доворачивается
    after: (C) => { const L = W3.handPt(C, 'l', 'palm'), Rh = W3.handPt(C, 'r', 'palm');
      let hx = Rh.x - L.x, hz = Rh.z - L.z, n = Math.hypot(hx, hz); if (n < .05) { const f = R.v(0, 0, 1); hx = f[0]; hz = f[2]; n = 1; }
      const lo = Math.asin(clamp((L.y - MOP_Y + .03) / MOP_LEN, 0, 1)), sl = clamp(Math.atan2(L.y - Rh.y, n), Math.max(40 * PI / 180, lo), 72 * PI / 180);
      const dm = [hx / n * Math.cos(sl), -Math.sin(sl), hz / n * Math.cos(sl)], M = [(L.x + Rh.x) / 2, (L.y + Rh.y) / 2, (L.z + Rh.z) / 2], dM = (M[1] - MOP_Y) / Math.sin(sl);
      // черенок: k = 1 — линия мытья (через середину между кистями мокапа), k = 0 — стоит в правой руке сбоку; между ними — дугой снаружи
      const bow = Math.sin(PI * k) * .1, bo = R.v(-bow, 0, bow * .6);
      // колодка — не дальше, чем хватает черенка (0,9 м по полу от кистей), не в стене коридора и не в ведре/табличке: упирается и скользит вдоль (12 ноя: уходила за стену до 70 см)
      const hr = Math.min(dM * Math.cos(sl), .9), HM = mopClear(o, [M[0] + hx / n * hr, MOP_Y, M[2] + hz / n * hr]), um = nrm([M[0] - HM[0], M[1] - HM[1], M[2] - HM[2]]);
      const head = mopClear(o, (h => { h[0] += bo[0]; h[2] += bo[2]; return h; })(lin(SD.head, HM, k)));
      const up = nrm(lin(SD.u, um, k)), dn = up.map(v => -v);
      const on = q => { const s = (q[0] - head[0]) * up[0] + (q[1] - head[1]) * up[1] + (q[2] - head[2]) * up[2]; return [head[0] + up[0] * s, head[1] + up[1] * s, head[2] + up[2] * s]; };
      const pr = nrm(crs(up, crs(side, up))), pl = pr.map(v => -v);
      if (k > .001) W3.reachArm(C, 'l', { at: on([L.x, L.y, L.z]), by: 'palm', fdir: crs(up, pl), pdir: pl, pole: R.v(1, -.4, -.4), w: k });   // левая — на черенок, пока моет
      W3.reachArm(C, 'r', { at: on(lin(SD.top, [Rh.x, Rh.y, Rh.z], k)), by: 'palm', fdir: k > .5 ? crs(pr, up) : R.v(.9, 0, .3), pdir: k > .5 ? pr : R.v(1, 0, -.2), pole: R.v(-1, -.6, -.3), w: 1 });
      W3.propAim('mop', o.S, head, up, ry + PI / 2 * w); } };
  return P;
}
// ---- седьмой этаж (02:59–03:00): Гас моет и напевает; в три перестаёт, выпрямляется, поворачивается к закрытой 707 ----
const G7 = [-.15, -10.3], G7RY = .45, G7DOOR = Math.atan2(-1.3 - G7[0], -11.8 - G7[1]);   // где моет; курс к двери 707
// рация в кулаке (chars2.js GRIP3.radio, 20 ноя): антенна торчит со стороны большого пальца. u — куда антенна, pd — куда ладонь → куда пальцы
const radioF = (sd, u, pd) => { const c = [pd[1] * u[2] - pd[2] * u[1], pd[2] * u[0] - pd[0] * u[2], pd[0] * u[1] - pd[1] * u[0]]; return sd === 'r' ? c : [-c[0], -c[1], -c[2]]; };
const RADIO_G = ['spine_03', [-.075, .06, .16], [0, 0, 0]];   // рация на нагруднике комбинезона
function gusStand(o, t, ry, look, hand, b = 1, at = G7, ry0 = G7RY) {   // стоит, швабра в правой руке торчком, левая — на рации (если hand); b < 1 — ещё подтягивает швабру к боку; at, ry0 — где и куда лицом мыл
  // швабра в правой руке сбоку (GUS_SIDE) — там же, где её оставляет mopGus при k = 0: переход от мытья к стойке без скачка
  const R = locR(at[0], at[1], ry), SD = GUS_SIDE(R), head = SD.head, top = SD.top, u = SD.u;
  window.__W3chars.propAim('mop', o.S, head, u, ry + PI / 2);
  // hand — рация в левой руке, прижата к груди (зажал кнопку — «рация зашипела»): в ладони, а не на нагруднике и не рукой в воздухе
  return Object.assign({ clips: [['Idle_Loop', t + 4, 1]], talk: 1, legs: 'feet', arm: Object.assign({ r: { at: top, by: 'palm', fdir: R.v(.9, 0, .3), pdir: R.v(1, 0, -.2), w: 1 } },
    hand ? { l: { at: hand, by: 'palm', fdir: radioF('l', R.v(-.15, 1, .05), R.v(.05, -.1, -1)), pdir: R.v(.05, -.1, -1), w: 1 } } : {}), fing: { r: ['grip', 1], l: [hand ? 'grip' : 'relax', 1] }, look },
    hand ? { hold: { l: 'radio' } } : { wear: { radio: RADIO_G } }, b < 1 ? { legs: 'plant', pelvis: R.v(0, -.07 * (1 - b), .02 * (1 - b)), spine: { to: R.p(0, .1, 1.0), k: .32 * (1 - b) } } : {});   // выпрямляется плавно
}
SH.f7_cam = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.25, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  return [['cleaner', G7[0], 0, G7[1], G7RY, Object.assign(mopGus(o, G7[0], G7[1], G7RY, t), { wear: { radio: RADIO_G } }), null]]; } };
SH.f7_close = { loc: 'corridor', pos: [.9, 1.42, -7.6], look: [-.3, 1.0, -10.6], fov: 46, face: { cleaner: 'calm' }, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  const P = mopGus(o, G7[0], G7[1], G7RY, t); P.wear = { radio: RADIO_G }; P.talk = 1; return [['cleaner', G7[0], 0, G7[1], G7RY, P, null]]; } };
// разворот на месте шагами (было: клип ходьбы поверх стойки при неподвижном корне — ступни скользили по полу ~0,3 м/с): корень поворачивается плавно,
// ступни переставляются по очереди — первой со стороны поворота; опорная стоит на месте, шагающая идёт по дуге с подъёмом 5 см; последний шаг —
// приставить отставшую. st — лодыжки в осях корня (стойка Гаса Idle_Loop + feet: левая на 15 см сзади). → { ry, feet } (P.feet в chars2.js)
const G_STANCE = { l: [.108, .094, -.149], r: [-.136, .089, -.027] };
function turnSteps(x, z, ry0, ry1, t, ta, tb, st = G_STANCE) {
  const d = ry1 - ry0, n = Math.max(2, Math.ceil(Math.abs(d) / .7)), m = n + 1, u = clamp((t - ta) / (tb - ta), 0, 1) * m, si = Math.floor(u), f = u - si;
  const ease = v => v * v * (3 - 2 * v), yawAt = sx => ry0 + d * Math.min(1, (sx + 1) / n), ry = ry0 + d * ease(clamp(u / n, 0, 1)), lead = d > 0 ? 'l' : 'r', feet = {};
  for (const sd of ['l', 'r']) {
    const first = sd === lead ? 0 : 1; let prev = -1; for (let sx = first; sx <= Math.min(si - 1, m - 1); sx += 2) prev = sx;   // последний свой законченный шаг
    const y0 = prev < 0 ? ry0 : yawAt(prev), go = si < m && si >= first && (si - first) % 2 === 0;
    const ya = go ? lerp(y0, yawAt(si), ease(f)) : y0, [lx, ly, lz] = st[sd], c = Math.cos(ya), sn = Math.sin(ya);
    // опорная стоит на месте, но проворачивается на подушечке вслед за корпусом (наполовину), как у живого человека
    feet[sd] = { at: [x + lx * c + lz * sn, ly + (go ? .05 * Math.sin(PI * f) : 0), z - lx * sn + lz * c], yaw: go ? ya - ry : (ya - ry) * .5 };
  }
  return { ry, feet };
}
// у закрытой 707 прислушивается: корпус чуть к двери, вес вперёд, голова ухом к двери и склонена, взгляд ниже, дышит мелко и редко
// (P.listen в chars2.js: заговорил — почти лицом к двери). k — насколько (плавно входит). Дверь не трогает и к ней не идёт — она остаётся закрытой (сюжет)
const DOOR7 = [-1.3, 1.5, -11.8];
function gusListen(P, k, ry, at = G7) {
  if (k <= .001) return P;
  const R = locR(at[0], at[1], ry);
  P.spine = { to: DOOR7, k: .16 * k }; P.listen = { k, yaw: -.35, pitch: .1, tilt: .1 };   // голова — влево, от швабры (она в правой руке) P.br = [lerp(1, .45, k), lerp(1, .7, k)];
  P.look = lin(P.look || DOOR7, [DOOR7[0], 1.05, DOOR7[2]], .6 * k);   // глаза — к низу двери, не в одну точку
  if (!P.legs || P.legs === 'feet') { P.legs = 'plant'; P.pelvis = R.v(0, -.012 * k, .03 * k); }
  return P;
}
// перестал мыть → выпрямился со шваброй → повернулся к 707 шагами → замер и прислушивается
// 20 ноя (тестер: «разворачивается спиной к камере и смотрит вдоль коридора, а не на дверь»): камера — сбоку, от правой стены: Гас в профиль, 707-я перед ним в кадре
SH.f7_stop = { loc: 'corridor', pos: [1.1, 1.55, -11.45], look: [-1.0, 1.3, -10.75], fov: 50, face: { cleaner: 'alert' }, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  const doorP = [-1.3, 1.55, -11.8];
  if (t < 1.0) { const P = mopGus(o, G7[0], G7[1], G7RY, t + 5, 1 - seg(t, 0, .9)); P.wear = { radio: RADIO_G }; P.look = t > .6 ? doorP : P.look; return [['cleaner', G7[0], 0, G7[1], G7RY, P, null]]; }
  const TS = turnSteps(G7[0], G7[1], G7RY, G7DOOR + 2 * PI * (G7DOOR < G7RY - PI ? 1 : 0), t, 1.3, 3.2), ry = TS.ry, P = gusStand(o, t, ry, doorP, null, seg(t, 1.0, 1.45));
  const fw = seg(t, 1.15, 1.3) * (1 - seg(t, 3.2, 3.45)); if (fw > 0) { P.feet = TS.feet; P.feet.l.w = P.feet.r.w = fw; }
  gusListen(P, seg(t, 3.2, 4.0), ry);
  return [['cleaner', G7[0], 0, G7[1], ry, P, null]]; } };
const radioPt = (ry, at = G7, dy = 0) => bodyAt(locR(at[0], at[1], ry), 'spine_03', G_S3, [.05, 1.25 + dy, .24]);   // левая рука с рацией у груди (dy — ещё ниже: только сняла с нагрудника)
SH.f7_radio = { loc: 'corridor', pos: [-.95, 1.5, -10.75], look: [-.12, 1.46, -10.35], fov: 42, face: { cleaner: 'alert' }, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, door7: 0 });
  const ry = G7DOOR, k = seg(t, .2, .8), hp = radioPt(ry, G7, -(1 - k) * .2);   // снял рацию с нагрудника и прижал к груди
  return [['cleaner', G7[0], 0, G7[1], ry, gusListen(gusStand(o, t, ry, [-1.3, 1.55, -11.8], hp), 1, ry), null]]; } };
SH.f7_cam_door = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.35, .9, -10.8], fov: 40, cctv: 1, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, door7: 0 });
  return [['cleaner', G7[0], 0, G7[1], G7DOOR, gusListen(gusStand(o, t, G7DOOR, [-1.3, 1.55, -11.8], radioPt(G7DOOR)), 1, G7DOOR), null]]; } };
SH.f7_cam_after = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.25, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { setLamps(o, 7); o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 });
  window.__W3chars.propAim('mop', o.S, MOPDOWN, MOPDOWN_D, 1.2, MOP_Y); salt7(o); return []; } };   // швабра лежит, Гаса нет; у ведра — рассыпанная соль (этап 1)
// Итан в будке отвечает по рации: сидит, рация в правой руке у рта
function eRadio(t) { return eRadioK(t, seg(t, .1, .6)); }
function eRadioK(t, up) { const R = locR(SEAT[0], SEAT[1], 0);
  return { clips: [['Sitting_Idle_Loop', t + 2, 1]], talk: 1, arm: { r: { at: bodyAt(R, 'Head', E_SIT_HEAD, [-.08, 1.13, -.1]), by: 'palm', fdir: radioF('r', R.v(.3, 1, .25), R.v(1, 0, .2)), pdir: R.v(1, 0, .2), w: up } },   // сидя голова на 27 см позади корня: рация у рта, а не на вытянутой руке
    fing: { r: ['grip', up] }, hold: up > .2 ? { r: 'radio' } : null, look: R.p(-.6, 1.2, 2) }; }
SH.booth_radio = { loc: 'lobby', pos: [6.35, 1.48, .05], look: [5.15, 1.15, -.75], fov: 42, set: (o, t) => { A.temych.faceSet = 'fear'; return [['temych', SEAT[0], 0, SEAT[1], 0, eRadioHold(t, seg(t, .15, .75), 0), null]]; } };
// r03 «Итан, ты меня слышишь?.. ответь…» — рация зовёт его: схватил её со стола и держит перед грудью, смотрит на неё и молчит (ко рту — это уже
// ответ, смерть k3; раньше он подносил рацию ко рту, будто отвечает). r04 — щелчок: выключил (0,1 с), опускает на стол, рука остаётся на ней, глаза вверх
function eRadioHold(t, up, down) { const R = locR(SEAT[0], SEAT[1], 0), desk = R.p(-.2, .84, .3), chest = R.p(-.07, .95, .1), k = up * (1 - down);
  return { clips: [['Sitting_Idle_Loop', t + 2, 1]], arm: { r: { at: lin(desk, chest, k), by: 'palm', fdir: radioF('r', R.v(.3, 1, .25 - .3 * (1 - k)), R.v(1, 0, .2)), pdir: R.v(1, 0, .2), w: 1 } },
    fing: { r: ['grip', 1] }, hold: { r: 'radio' }, look: down > .7 ? R.p(-.6, 1.25, 2) : lin(R.p(-.2, .84, .3), chest, k), br: [1.3, 1.4] }; }
SH.booth_radio_off = Object.assign({}, SH.booth_radio, { face: { temych: 'dread' }, set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, eRadioHold(t, 1, seg(t, .6, 1.4)), null]] });
// смерть «отозвался»: рация у него в кулаке с первого кадра — поднимает её ко рту (было: рука шла ко рту пустой, рация появлялась на полпути)
SH.k3_answer = Object.assign({}, SH.booth_radio, { set: (o, t) => [['temych', SEAT[0], 0, SEAT[1], 0, Object.assign(eRadio(t), { hold: { r: 'radio' }, fing: { r: ['grip', 1] } }), null]] });
// ---- стеклянная дверь (03:30): «Лили» снаружи, Итан внутри ----
let fogBase = null;
function fogWrite(o, k) {   // туман на стекле; k — сколько надписи выведено (0..1); возвращает, где сейчас кончик пальца (мир)
  const F = o.fogW, d = F.userData, x = d.x, L = [tr('Как звали'), tr('собаку?')], x0 = 66, ys = [118, 200];
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
  return [.62 - (px / 512 - .5) * .8, 1.42 + (.5 - py / 256) * .4, 5.976];
}
const LZ = 6.3, LX = .62, EYE_IN = [.78, 1.66, 5.52];   // где стоит «Лили» снаружи; где глаза Итана у двери
// поза у стекла: mode 'press' — ладони на стекле, всхлипывает; 'look' — оглянулась назад; 'slam' — бьёт ладонями; 'scratch' — ногти по стеклу сверху вниз; 'stand' — руки опущены
function lilyGlass(t, mode, o2 = {}) {
  const R = locR(LX, LZ, PI), gz = LZ - 6.03, sob = (mode === 'press' || mode === 'slam') && !(t > o2.stop) ? Math.pow(Math.max(0, Math.sin(t * 3.3)), 6) : 0;   // o2.stop — с этой секунды всхлипы обрываются
  let hit = 0; if (mode === 'slam') for (const h of [.3, .62, 1.6, 1.9]) hit = Math.max(hit, Math.exp(-Math.pow((t - h) / .07, 2)));
  // 20 ноя (тестер: «скребёт 2 секунды, потом 4 секунды стоит с опущенными руками, пока звучит "Итан. Итан. Итан"»): скребёт снова и снова —
  // медленно вниз (2,7 с), отрывает ногти от стекла, поднимает руки (0,8 с) и опять вниз, пока идёт кадр
  const sp = mode === 'scratch' ? ((Math.max(0, t - .3)) % 3.5) : 0, sUp = seg(sp, 2.7, 3.5), sLift = mode === 'scratch' ? Math.sin(PI * sUp) : 0;
  const away = mode === 'slam' ? .06 * (1 - hit) * seg(t, 0, .25) : .05 * sLift, k = mode === 'stand' || mode === 'look' ? 0 : 1;
  const ly = mode === 'scratch' ? lerp(lerp(1.62, 1.08, seg(sp, 0, 2.7)), 1.62, ease(sUp)) : 1.38;
  const hand = (lx, dy) => ({ at: R.p(lx, ly + dy, gz - away), by: mode === 'scratch' ? 'tip' : 'palm', fdir: [0, 1, -.15], pdir: [0, 0, -1], w: k });
  return { clips: [['Idle_Loop', t + 1.7, 1]], talk: 1, legs: 'feet', spine: { to: R.p(0, 1.3, 1.2), k: .22 * k + .05 },
    arm: { l: hand(.21, -.02), r: hand(-.2, .01) }, fing: { l: [mode === 'scratch' ? 'claw' : 'press', k], r: [mode === 'scratch' ? 'claw' : 'press', k] },   // ладони на стекле — пальцы прямые (у 'flat' кончики загибались в стекло)
    wig: [['spine_03', [1, 0, 0], sob * .1], ['neck_01', [1, 0, 0], sob * .09], ['clavicle_l', [0, 0, 1], sob * .06], ['clavicle_r', [0, 0, 1], -sob * .06]],   // всхлипы: плечи вздрагивают twist: mode === 'look' ? -.85 * seg(t, .1, .7) : 0,
    look: mode === 'look' && t > .15 ? [LX - 2.5, 1.5, LZ + 4] : o2.look === 'cam' ? undefined : o2.look || EYE_IN };
}
// Итан у двери внутри: смотрит на неё; пишет пальцем по туману (k), поворачивает ключ (key)
function ethanDoor(o, t, mode, k) {
  const R = locR(.78, 5.55, 0), base = { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', look: [LX, 1.52, LZ] };
  // пишет — смотрит на палец и буквы; дописал (k = 1) — рука уходит вниз, а глаза — ей в лицо: «она даже не взглянула на буквы»
  if (mode === 'write') { const p = fogWrite(o, k), dn = seg(t, 5.0, 5.7); return Object.assign(base, { arm: { r: { at: p, by: 'tip', fdir: [.05, .35, 1], pdir: [.35, -.5, .4], w: seg(t, 0, .5) * (1 - dn) } },
    fing: { r: ['point', 1 - dn] }, spine: { to: p, k: .12 * (1 - dn) }, look: k < 1 || t < 4.9 ? p : [LX, 1.52, LZ] }); }
  return base;
}
// 20 ноя (тестер: «Лили почти не видно — Итан стоит спиной к камере ровно перед ней; Итан — столб»): камера слева-сбоку — Итан в профиль справа, Лили за стеклом
// лицом к нам; Итан подаётся к стеклу и нерешительно поднимает руку к двери — и не дотрагивается
SH.gl_wide = { loc: 'lobby', pos: [-.9, 1.6, 4.9], look: [.72, 1.45, 6.0], fov: 46,
  set: (o, t) => { const R = locR(.78, 5.55, 0), P = ethanDoor(o, t), up = seg(t, 1.1, 2.3) * (1 - seg(t, 3.4, 4.3));
    P.arm = { r: { at: R.p(-.2, 1.12, .3), by: 'palm', fdir: R.v(.1, .5, 1), pdir: R.v(.2, -.3, 1), w: .75 * up } }; P.fing = { r: ['open', .5 * up] }; P.spine = { to: [LX, 1.4, LZ], k: .1 * seg(t, .4, 1.6) };
    return [['temych', .78, 0, 5.55, 0, P, null], ['katya', LX, 0, LZ, PI, lilyGlass(t, 'press'), null]]; } };
SH.gl_face = { loc: 'lobby', pos: [.66, 1.56, 5.45], look: [.62, 1.5, 6.5], fov: 34, face: { katya: 'plead' }, set: (o, t) => { breath(o, t); return [['katya', LX, 0, LZ, PI, lilyGlass(t, 'press', { look: 'cam' }), 'cam']]; } };
SH.gl_cry = Object.assign({}, SH.gl_face, { face: { katya: 'cry' } });   // плачет у стекла (до «перестала плакать»)
// b01 «Я не открыл. Она перестала плакать. Сразу»: плачет — и на «перестала плакать» (2,0 с) всхлипы и лицо обрываются разом, без затухания:
// пустое лицо, неподвижный взгляд в него; ладони остаются на стекле (раньше весь кадр было одно «умоляющее» лицо и всхлипы)
SH.gl_stop = Object.assign({}, SH.gl_face, { face: { katya: 'cry' }, set: (o, t) => { breath(o, t); if (t > 2.0) A.katya.faceSet = 'base';
  return [['katya', LX, 0, LZ, PI, lilyGlass(t, 'press', { look: 'cam', stop: 2.0 }), 'cam']]; } });
// поворот кости в мире (после взгляда — в P.post): ось ax, угол a
function boneTurnW(C, bn, ax, a) { const b = C.bone[bn]; if (!b || !a) return; b.updateWorldMatrix(true, false);
  const wq = b.getWorldQuaternion(new T.Quaternion()), pq = b.parent.getWorldQuaternion(new T.Quaternion());
  b.quaternion.copy(pq.invert().multiply(new T.Quaternion().setFromAxisAngle(new T.Vector3(ax[0], ax[1], ax[2]).normalize(), a).multiply(wq))); b.updateMatrixWorld(true); }
// g10 «она прижималась к нему лицом»: шагнула вплотную, ладони ползут вверх по стеклу по бокам лица, щека и висок ложатся на стекло;
// плачет (всхлипы плечами), глаза вниз — не на него; медленно трётся щекой. Стекло запотевает только с его стороны (breath)
const LZP = LZ - .1;
function lilyPress(t) {
  const GP = window.__GP || GP0, R = locR(LX, LZP, PI), gz = LZP - 6.03, k = ease(seg(t, .2, 1.5)), sob = Math.pow(Math.max(0, Math.sin(t * 3.3)), 6) * (.6 + .4 * k);
  const hand = (lx, y0, y1, ph) => ({ at: R.p(lerp(lx, lx * 1.3, k), lerp(y0, y1, k) + .005 * Math.sin(t * 1.3 + ph) * k, gz), by: 'palm', fdir: [lx > 0 ? -.12 : .12, 1, -.1], pdir: [0, 0, -1], w: 1 });
  return { clips: [['Idle_Loop', t + 1.7, 1]], talk: 1, legs: 'feet', pelvis: R.v(0, -.02 * k, .05 * k), spine: { to: R.p(0, 1.45, 1.2), k: lerp(.27, GP.lean, k) },
    arm: { l: hand(.21, 1.36, 1.44, 0), r: hand(-.2, 1.39, 1.41, 2) }, fing: { l: ['press', 1], r: ['press', 1] },
    wig: [['spine_03', [1, 0, 0], sob * .08], ['clavicle_l', [0, 0, 1], sob * .06], ['clavicle_r', [0, 0, 1], -sob * .06]],
    look: R.p(-.6, 1.3, .9),   // глаза чуть вниз и вбок
    post: C => { const fw = R.v(0, 0, 1), rub = Math.sin(t * .8) * .04 * k;
      boneTurnW(C, 'neck_01', [0, 1, 0], GP.yaw * .4 * k); boneTurnW(C, 'Head', [0, 1, 0], GP.yaw * .6 * k + rub);
      boneTurnW(C, 'Head', fw, GP.roll * k); boneTurnW(C, 'neck_01', R.v(1, 0, 0), GP.nod * k + sob * .05); } };
}
const GP0 = { lean: .34, yaw: .8, roll: .2, nod: -.06 };
SH.gl_press = Object.assign({}, SH.gl_face, { pos: [.68, 1.5, 5.45], look: [.62, 1.4, 6.5], face: { katya: 'cry' }, set: (o, t) => { breath(o, t); return [['katya', LX, 0, LZP, PI, lilyPress(t), null]]; } });
SH.gl_none = Object.assign({}, SH.gl_face, { face: null, set: (o, t) => { breath(o, t); return []; } });   // протираешь стекло — за ним пустая улица под дождём (мини-игра «Кто там?»)
SH.gl_slam = Object.assign({}, SH.gl_face, { face: { katya: 'fear' }, set: (o, t) => { breath(o, t); return [['katya', LX, 0, LZ, PI, lilyGlass(t, 'slam', { look: 'cam' }), 'cam']]; } });   // и тут же — удар ладонями в стекло
SH.gl_look = { loc: 'lobby', pos: [1.5, 1.55, 4.7], look: [.62, 1.45, 6.4], fov: 40, face: { katya: 'fear' }, set: (o, t) => [['katya', LX, 0, LZ, PI, t < 1.5 ? lilyGlass(t, 'look') : lilyGlass(t - 1.5, 'slam'), null]] };
SH.gl_write = { loc: 'lobby', pos: [.4, 1.74, 5.08], look: [.64, 1.4, 6.05], fov: 46, set: (o, t) => [['temych', .78, 0, 5.55, 0, ethanDoor(o, t, 'write', clamp((t - .6) / 4.2, 0, 1)), null], ['katya', LX, 0, LZ, PI, lilyGlass(t, 'press'), null]] };
SH.gl_scratch = { loc: 'lobby', pos: [.66, 1.52, 5.3], look: [.62, 1.38, 6.5], fov: 40, set: (o, t) => [['katyaSmile', LX, 0, LZ, PI, lilyGlass(t, 'scratch', { look: 'cam' }), 'cam']] };
// ---- ветка А (открыл): ключ в замке, кинулась на шею, пустой холл и мокрые следы к лифту ----
// 20 ноя (тестер: «ключ в скважину не вставлен, поворота кисти нет, связка болтается в кулаке»): теперь ключ виден в замке — рука подносит его к личинке, вставляет,
// поворачивает на пол-оборота (кисть крутится вместе с ключом), связка висит на нём и качается; камера — крупно на замок сбоку, а не из-за спины Итана
function lockKeyAt(o, lock, ins, turn) {   // ключ сцены: головка у пальцев, бородка в личинке (ins — насколько вставлен, turn — поворот)
  if (!o.lockKey) { const g = o.lockKey = new T.Group(), m = mat(0xb4ad98, null, { metal: .8, rough: .35 }); box(.0035, .009, .03, m, 0, 0, .015, g); box(.003, .024, .02, m, 0, 0, -.01, g); o.S.add(g); }
  o.lockKey.visible = true; o.lockKey.position.set(lock[0], lock[1], lock[2] - .03 * (1 - ins)); o.lockKey.rotation.set(0, 0, -turn * PI * .5);
}
function ethanKey(o, t, lock, rot) {   // ключ в замке: подносит (0–.55) → вставляет (.55–.85) → поворачивает (rot…rot+.4) → держит
  const k = seg(t, 0, .55), ins = seg(t, .55, .85), turn = ease(seg(t, rot, rot + .4));
  lockKeyAt(o, lock, ins, turn);
  const at = [lock[0], lock[1] - .012 * turn, lock[2] - .016 - .03 * (1 - ins)];   // щепоть — на головке ключа; связка (hold keys) висит под ней на кольце
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', spine: { to: lock, k: .15 * k },
    arm: { r: { at, by: 'pinch', fdir: [0, -.2, 1], pdir: [lerp(-1, -.15, turn), lerp(-.25, -1, turn), 0], w: 1 } }, fing: { r: ['pinch', 1] }, hold: { r: 'keys' }, look: lock };
}
SH.a_key = { loc: 'lobby', pos: [-.66, 1.22, 5.5], look: [-.08, 1.05, 5.9], fov: 34, set: (o, t) => [['temych', .1, 0, 5.4, 0, ethanKey(o, t, o.doorLock, 1.0), null], ['katya', LX, 0, LZ, PI, lilyGlass(t, 'press'), null]] };
// a02: ключ повернулся — она шагнула к скобе, рванула створку на себя и вошла; бросилась на шею. Итан отступил на шаг
const HUG_E = [-.26, 5.02], HUG_L = [-.13, 5.2], HUG_NAPE = .125, HUG_BACK = .145;   // она чуть левее его (по его левую руку): головы расходятся, щека к плечу
function hugArms(t, k, ek, sob = 0, comfort = 0, k2 = 1) {   // руки в объятии: её — на его плечах, кисти сцеплены за шеей (локти вниз), его — у неё на спине
  // sob — уткнулась лицом ему в левое плечо и плачет (а не смотрит мимо него в зал); comfort — он гладит её по спине и склонил к ней голову («Всё, всё…»)
  const E = A.temych, Lc = A.katya, nk = E.bone.neck_01.getWorldPosition(new T.Vector3()), sp = Lc.bone.spine_03.getWorldPosition(new T.Vector3()), lh = Lc.bone.Head.getWorldPosition(new T.Vector3());
  const bury = sob * k, sk = Math.sin(t * 2 * PI / 1.9), st = comfort * ek * (.5 - .5 * Math.cos(t * 2 * PI / 1.9));   // гладит: ладонь медленно вниз и вверх по спине
  // 20 ноя (владелец: «Лили обнимает Итана — с руками проблема»): локти у неё смотрели вниз, и руки шли к его шее сквозь его плечи и грудь, а кисти тонули в спине
  // куртки двумя пятнами. Теперь она обнимает за шею по-настоящему: локти подняты и разведены, предплечья лежат поверх его плеч, ладони — одна на другой
  // у него на загривке, на воротнике (на поверхности куртки, а не в ней)
  // до загривка рука дотягивается не всегда (тела дышат и покачиваются, она ниже ростом): тогда ладонь ложится ближе — ему на плечо сзади, у основания шеи.
  // Иначе вытянутая до предела рука не доходила до цели и кисть оставалась внутри его воротника
  // куда ложится ладонь: на загривок (q = 0) или ему на плечо сверху-сзади (q = 1). На плечи — когда она подняла голову (bury → 0: объятие ослабло) и когда до
  // загривка не достать. Раньше выбор шёл только по дотягиванию и руки рывком съезжали по спине (тестер, 20 ноя)
  const hugAt = (sd, nape, es) => { const B = Lc.bone, u = B['upperarm_' + sd].getWorldPosition(new T.Vector3()), e = B['lowerarm_' + sd].getWorldPosition(new T.Vector3()), h = B['hand_' + sd].getWorldPosition(new T.Vector3());
    const len = u.distanceTo(e) + e.distanceTo(h) + .05, sh = E.bone['upperarm_' + es].getWorldPosition(new T.Vector3()), top = [sh.x + (es === 'r' ? .07 : -.07), sh.y + .085, sh.z - .06];
    const need = Math.hypot(nape[0] - u.x, nape[1] - u.y, nape[2] - u.z), q = Math.max(1 - bury, clamp((need - .9 * len) / (.1 * len), 0, 1)), sx = sd === 'l' ? 1 : -1;
    // подход: на бегу руки вскинуты перед собой (way), у него — ложатся на место; а не тянутся прямыми палками через весь холл
    const fin = lin(nape, top, q), way = [sp.x - sx * .22, sp.y + .3, sp.z - .3], at = lin(way, fin, k2);   // way — руки вскинуты перед собой, локти согнуты (от её груди, пока бежит)
    return { at, by: 'palm', fdir: nrm(lin([sx, -.12, -.1], [sx * .25, -.5, -1], q)), pdir: nrm(lin([0, .15, 1], [0, -.85, .45], q)), w: k, pole: [sx, .45, .1] }; };
  const lily = { arm: { l: hugAt('l', [nk.x - .05, nk.y + .005, nk.z - HUG_NAPE], 'r'), r: hugAt('r', [nk.x + .06, nk.y - .035, nk.z - HUG_NAPE - .012], 'l') },
    fing: bury > .15 ? { l: ['grip', .28 * bury * k], r: ['grip', .28 * bury * k] } : { l: ['flat', .5 * k], r: ['flat', .5 * k] },   // плачет — пальцы чуть сжали воротник
    spine: { to: [nk.x, nk.y - .1, nk.z], k: (.32 + .03 * bury) * k },   // корпус прижат к нему и когда подняла голову (было .2 — отклонялась назад, руки не дотягивались до загривка и кисти тонули у него в шее)
    look: k > .5 ? lin([nk.x + .55, nk.y - .12, nk.z - .55], [nk.x + .34, nk.y - .16, nk.z - .22], bury) : [nk.x, nk.y + .12, nk.z] };   // уткнулась щекой в плечо, а не носом в куртку
  const eth = { arm: { r: { at: [sp.x - .03, sp.y - .03 - .09 * st, sp.z + HUG_BACK], by: 'palm', fdir: [1, -.1, 0], pdir: [0, 0, -1], w: ek, pole: [-1, -.45, .3] },   // ладони — поверх её волос на спине, а не в них
                       l: { at: [sp.x + .06, sp.y - .17, sp.z + HUG_BACK - .005], by: 'palm', fdir: [-1, -.1, 0], pdir: [0, 0, -1], w: ek, pole: [1, -.45, .3] } },
    fing: { l: ['flat', ek * .7], r: ['pinch', 1] }, hold: { r: 'keys' }, spine: { to: [sp.x, sp.y + .1, sp.z], k: (.1 + .08 * comfort) * ek },
    look: ek > .3 ? lin([lh.x - .25, lh.y + .12, lh.z + .5], [lh.x + .02 * sk, lh.y + .06, lh.z], comfort) : [lh.x, lh.y, lh.z] };   // смотрит на неё (раньше — поверх её головы в стену); утешает — вниз, ей в макушку
  return [lily, eth];
}
function hugPair(o, t, sob = 0, comfort = 0, bury = sob) {   // sob — плачет у него на плече (всхлипы плечами), до «перестала плакать»; bury — лицом в плечо; comfort — он её гладит
  leafAng(o, 0, leafPull(t, .4, 1.25));   // отпустила скобу — доводчик закрывает створку за 3 с (раньше дверь оставалась распахнутой в дождь либо «закрывалась» рывком)
  // она «бросилась» (a02: «бросилась мне на шею» — 0,4–1,4 с): к скобе (0–.35), рывок (.4–1.15), внутрь бегом (с .9), руки на шею (1,6–2,05);
  // он: держит ключ, отступает от двери; её руки уже на шее — замер, обнимает не сразу (2,1–2,65). Раньше она входила шагом и обнимала на 2,6 с
  let L; const g = seg(t, .12, .35) * (1 - seg(t, .75, .9)), gr = leafGrip(o, g);
  if (t < .9) { L = shuffle('katya', [LX, LZ], [.16, 6.6], t, 0, .4, PI); L[5].arm = { l: gr }; L[5].fing = { l: ['grip', g] }; L[5].spine = { to: gr.at, k: .1 * g }; L[5].look = gr.at; }
  else L = walker('katya', [[.16, 6.6], [-.22, 6.28], [-.36, 5.9], HUG_L], t - .9, 2.4, {}, PI);
  const E = shuffle('temych', [.1, 5.4], HUG_E, t, .55, .6, 0, { talk: t < 1.2 ? 1 : 0, look: t < .9 ? [.16, 1.5, 6.6] : [L[1], 1.45, L[3]] });   // смотрит на неё; в объятии не «говорит телом» (отстранялся на полметра)
  E[5].fing = { r: ['pinch', 1] }; E[5].hold = { r: 'keys' };
  const k = seg(t, 1.2, 1.65), k2 = seg(t, 1.5, 1.95), ek = seg(t, 1.45, 2.05);   // руки она вскидывает ещё на бегу, он раскрывает свои навстречу
  if (k > 0) { const [lp, ep] = hugArms(t, k, ek, bury, comfort, k2); Object.assign(L[5], lp); if (ek > 0) Object.assign(E[5], ep); L[5].legs = 'feet'; }
  if (sob) { const sb = Math.pow(Math.max(0, Math.sin(t * 3.3)), 6) * sob; L[5].wig = [['spine_03', [1, 0, 0], sb * .1], ['neck_01', [1, 0, 0], sb * .08], ['clavicle_l', [0, 0, 1], sb * .06], ['clavicle_r', [0, 0, 1], -sb * .06]]; }
  return [E, L];
}
SH.a_hug = { loc: 'lobby', pos: [1.55, 1.5, 4.55], look: [-.15, 1.3, 5.75], fov: 48, face: { katya: 'cry' }, set: (o, t) => hugPair(o, t, 1) };
SH.a_hug_side = { loc: 'lobby', pos: [1.25, 1.52, 5.12], look: [-.2, 1.38, 5.14], fov: 40, face: { katya: 'cry', temych: 'sad' }, set: (o, t) => hugPair(o, t + 4, 1, seg(t, .1, .8)) };   // «Всё, всё. Ты со мной» — гладит её по спине
// a04 «Она перестала плакать. Сразу»: первые полсекунды ещё всхлипывает у него на плече — и обрывается (без затухания), лицо пустеет,
// голова медленно поднимается с плеча, глаза открыты и смотрят мимо него в объектив. a05 — тот же план дальше (sameView)
SH.a_hug_face = { loc: 'lobby', pos: [.2, 1.63, 4.55], look: [-.1, 1.5, 5.24], fov: 34, set: (o, t) => { A.katya.noTilt = true; const on = t < .85;
  A.katya.faceSet = on ? 'cry' : 'base'; return hugPair(o, t + 4, on ? 1 : 0, .6 * (1 - seg(t, 1.0, 2.2)), 1 - seg(t, .9, 2.0)); } };
SH.a_hug_face2 = Object.assign({}, SH.a_hug_face, { set: (o, t) => { A.katya.noTilt = true; return hugPair(o, t + 9, 0, 0, 0); } });   // a05: уже не плачет — смотрит в объектив, он замер
// a07: она стоит рядом, руки пустые; у него в руке звонит телефон
// 20 ноя (тестер: «телефона не видно, оба как в строю, Итану срезало голову»): камера была за спиной Итана — телефон закрывал он сам. Теперь спереди-сбоку:
// Итан лицом к камере с телефоном в руке, Лили рядом в профиль
SH.a_beside = { loc: 'lobby', pos: [-.3, 1.5, 5.8], look: [.7, 1.32, 5.02], fov: 46, set: (o, t) => {
  // a07: смотрит на экран («Лили») → переводит взгляд на неё: руки пустые (2,2–3,4) → ей в лицо → снова на звонящий телефон; телефон в руке дрожит от вибрации
  const R = locR(.55, 4.95, 0), bz = Math.sin(t * 70) * .0025 * (Math.sin(t * 2 * PI / 1.6) > 0 ? 1 : 0), scr = R.p(-.12, 1.1, .35);
  const lk = t < 2.0 ? scr : t < 3.3 ? [.92, .85, 5.05] : t < 4.0 ? [.95, 1.5, 5.15] : scr;   // экран → её пустые руки → лицо → экран
  if (t > 2.0) A.temych.faceSet = 'dread';
  const ph = { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: R.p(-.12 + bz, 1.12, .32 + bz), by: 'palm', fdir: R.v(.3, .3, 1), pdir: [0, 1, .3], w: 1 } }, fing: { r: ['phone', 1] }, hold: { r: 'phone' }, look: lk };
  return [['temych', .55, 0, 4.95, 0, ph, null], ['katya', .95, 0, 5.15, PI + .9, { clips: [['Idle_Loop', 2, 1]], legs: 'feet', look: [.55, 1.66, 4.95] }, null]]; } };
SH.a_steps = { loc: 'lobby', pos: [.55, 1.66, 5.2], look: [-1.4, .4, -3], fov: 58, set: (o, t) => { o.wetSteps.visible = true; return []; } };
SH.a_floors = { loc: 'lobby', pos: [-1.6, 2.1, -2.9], look: [-2.2, 2.62, -5.94], fov: 26, set: (o, t) => { const f = ['5', '9', '12', '13'][Math.min(3, Math.floor(t / .9))]; setLift(o, o.lifts[0], 0, f, true); return []; } };
SH.a_floors_down = { loc: 'lobby', pos: [-1.6, 2.1, -2.9], look: [-2.2, 2.62, -5.94], fov: 26, set: (o, t) => { const f = ['13', '12', '11', '10', '9'][Math.min(4, Math.floor(t / .7))]; setLift(o, o.lifts[0], 0, f, false); return []; } };
// «Я положил трубку. Руки тряслись»: смотрит на свои ладони перед грудью — мелкая дрожь (8–10 Гц, 3–6 мм), пальцы полусогнуты
function eShake(t) { const R = locR(.55, 4.95, 0), w = seg(t, 0, .7), tr = (f, s) => (.0035 * Math.sin(t * f + s) + .002 * Math.sin(t * f * 1.73 + s * 2)) * w;
  const L = R.p(.1, 1.27, .3), Rr = R.p(-.1, 1.27, .3);
  return { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', look: R.p(0, 1.25, .31), spine: { to: R.p(0, 1.1, .6), k: .08 * w },
    arm: { l: { at: [L[0] + tr(53, 0), L[1] + tr(61, 1), L[2] + tr(47, 2)], by: 'palm', fdir: R.v(.3, .25, 1), pdir: R.v(-.2, .75, -.6), w },
           r: { at: [Rr[0] + tr(57, 3), Rr[1] + tr(49, 4), Rr[2] + tr(59, 5)], by: 'palm', fdir: R.v(-.3, .25, 1), pdir: R.v(.2, .75, -.6), w } },
    fing: { l: ['relax', 1], r: ['relax', 1] }, wig: [['index_02_l', [1, 0, 0], tr(71, 6) * 12], ['middle_02_r', [1, 0, 0], tr(67, 7) * 12]] }; }
// b12 «Я положил трубку. Руки тряслись»: сначала телефон перед грудью — большой палец жмёт «отбой» (0,2–0,5), рука с телефоном уходит
// в карман (0,6–1,15) — и только потом обе ладони поднимаются перед глазами, а дрожь нарастает. Раньше руки дрожали с первого кадра, без телефона
function eHangUp(t) { const R = locR(.55, 4.95, 0), P = eShake(t - 1.2);
  if (t < 1.25) { const dn = seg(t, .6, 1.15), at = lin(R.p(-.1, 1.15, .3), R.p(-.2, .9, .06), dn);
    P.arm = { r: { at, by: 'palm', fdir: lin(R.v(.3, .3, 1), R.v(0, -1, .2), dn), pdir: lin([0, 1, .3], R.v(1, 0, 0), dn), w: 1 - seg(t, 1.0, 1.25) } };
    P.fing = { r: ['phone', 1], l: ['relax', 1] }; P.hold = { r: t < 1.05 ? 'phone' : null }; P.look = lin(R.p(-.1, 1.13, .32), R.p(0, 1.2, .5), dn); }
  return P; }
SH.e_hands = { loc: 'lobby', pos: [.95, 1.72, 6.05], look: [.55, 1.36, 5.15], fov: 40, face: { temych: 'dread' }, set: (o, t) => [['temych', .55, 0, 4.95, 0, eHangUp(t), null]] };
SH.e_face = { loc: 'lobby', pos: [.6, 1.64, 5.55], look: [.55, 1.62, 4.95], fov: 34, face: { temych: 'dread' }, set: (o, t) => [['temych', .55, 0, 4.95, 0, { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet' }, 'cam']] };
// ---- А1: будка. Запер дверь будки, выключил лампу, сполз под стойку; утром сменщик пятится; отражение в стекле ----
SH.h_lock = { loc: 'lobby', pos: [6.2, 1.6, -.75], look: [7.35, 1.05, .4], fov: 40, set: (o, t) => [['temych', 7.12, 0, -.05, .35, ethanKey(o, t, o.boothLock, .9), null]] };
SH.h_lamp = { loc: 'lobby', pos: [5.55, 1.2, -.8], look: [6.5, 1.3, -.3], fov: 50, set: (o, t) => { const off = t > .75; o.lamp.intensity = off ? 0 : 1.6; o.lampBulb.visible = !off;
  const k = seg(t, 0, .55) * (1 - seg(t, 1.0, 1.5));   // стоит справа от второго стула (на нём куртка Фрэнка — раньше Итан стоял прямо в нём), камера — из-за стола: лицо в свете лампы, щелчок — темно
  return [['temych', 6.68, 0, -.36, -1.1, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', spine: { to: [6.15, .85, -.1], k: .25 * k }, arm: { r: { at: [6.16, .835, -.07], by: 'tip', fdir: [0, -1, .3], w: k } }, fing: { r: ['point', k] }, look: [6.1, .85, -.1] }, null]]; } };
// сполз под стойку: сидит на корточках, спиной к передней стойке
const CROUCH = [5.75, -.1];   // сидит на корточках за стойкой: таз и спина на 32–48 см позади корня — точка на 15 см дальше от панели стойки (раньше спина проходила сквозь неё; проверка — World.clips)
// под столом: стол 0,78 м — на корточках голова была выше столешницы и торчала сквозь неё; поэтому таз ниже, корпус сжат вперёд (голова ~0,52)
function eCrouch(t, cover = 0) { const R = locR(CROUCH[0], CROUCH[1], PI);   // cover 0…1 — левая ладонь с колена на карман с телефоном
  // 20 ноя (тестер: «сложился пополам, рука висит до пола — читается как человек, стоящий согнувшись»): руки больше не висят — обхватил поднятое колено
  // (ладони на голени под коленом, локти в стороны): сжался в комок. Точки — от костей ног этого кадра (какое колено выше — то и держит)
  // «накрыл телефон ладонью» (h_pocket) — левой, ближней к камере рукой: правая с дальней стороны, за телом, её движения не было видно (тестер)
  const hug = sx => ({ at: C => crouchKnee(C, R, sx * .035), by: 'palm', fdir: R.v(-sx * .8, -.25, .35), pdir: R.v(0, .1, -1), w: 1, pole: R.v(sx, -.5, .2) }), L = hug(1);
  if (cover > 0) { const pk = C => { const a = C.bone.thigh_l.getWorldPosition(new T.Vector3()), b = C.bone.calf_l.getWorldPosition(new T.Vector3()), d = b.clone().sub(a).normalize(); a.lerp(b, .3); return [[a.x - .085, a.y + .03, a.z], [d.x, d.y, d.z]]; }, at0 = L.at, f0 = L.fdir, p0 = L.pdir, q0 = L.pole;
    L.at = C => lin(at0(C), pk(C)[0], cover); L.fdir = C => lin(f0, pk(C)[1], cover); L.pdir = lin(p0, [1, -.25, 0], cover); L.pole = lin(q0, [-1, -.2, .6], cover); }
  return { clips: [['Crouch_Idle_Loop', t + .7, 1]], legs: 'plant', pelvis: [0, -.2, .03], spine: { to: [CROUCH[0], .35, CROUCH[1] - .75], k: .35 },
    arm: { l: L, r: hug(-1) }, fing: { l: [cover > .5 ? 'flat' : 'relax', 1], r: ['relax', 1] } }; }
function crouchKnee(C, R, dx) { const a = C.bone.calf_l.getWorldPosition(new T.Vector3()), b = C.bone.calf_r.getWorldPosition(new T.Vector3()), up = a.y > b.y ? 'l' : 'r', k = up === 'l' ? a : b,
  f = C.bone['foot_' + up].getWorldPosition(new T.Vector3()); k.lerp(f, .28); const v = R.v(dx, 0, .075); return [k.x + v[0], k.y + .01, k.z + v[2]]; }
SH.h_crouch = { loc: 'lobby', pos: [5.12, .86, -1.13], look: [5.75, .42, -.13], fov: 50,   // из будки сбоку-спереди: сжался под столом (раньше камера стояла в панели — был чёрный кадр)
  set: (o, t) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .25; o.amb.intensity = .22; return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), null]]; } };
// h02 «Двери лифта. Шаги. Босые, мокрые. Ближе»: вздрогнул на звонок лифта (0,2 с), глаза — туда, откуда звук (к лифту, сквозь стойку);
// на шагах (2,2 с) зажимает себе рот левой ладонью и перестаёт дышать — так и остаётся (дальше — мини-игра «Не дыши»)
SH.h_crouch_in = Object.assign({}, SH.h_crouch, { face: { temych: 'fear' }, set: (o, t) => { SH.h_crouch.set(o, t); const R = locR(CROUCH[0], CROUCH[1], PI), P = eCrouch(t);
  const jolt = Math.exp(-Math.pow((t - .32) / .13, 2)), cov = seg(t, 2.3, 2.85);
  if (t > 2.3) A.temych.faceSet = 'hush';
  P.wig = [['spine_03', [1, 0, 0], -.08 * jolt], ['neck_01', [1, 0, 0], -.12 * jolt], ['clavicle_l', [0, 0, 1], .06 * jolt], ['clavicle_r', [0, 0, 1], -.06 * jolt]];
  P.look = t < .3 ? R.p(0, .3, 1) : R.p(1.6, .9, 1.4); P.br = [lerp(1.4, .15, cov), lerp(1.5, .5, cov)];
  { const L = P.arm.l, at0 = L.at, f0 = L.fdir, p0 = L.pdir, q0 = L.pole;   // до 2,3 с обе руки на колене; потом левая идёт от колена ко рту (а не из виса)
    L.at = C => { const [m, f] = mouthOf(C); return lin(at0(C), [m.x + f.x * .03, m.y - .01, m.z + f.z * .03], cov); }; L.fdir = lin(f0, R.v(-.55, .83, 0), cov);
    L.pdir = C => { const [, f] = mouthOf(C); return lin(p0, [-f.x, 0, -f.z], cov); }; L.pole = lin(q0, [.6, -1, .3], cov); }
  P.fing = Object.assign({}, P.fing, { l: ['relax', cov] });
  return [['temych', CROUCH[0], 0, CROUCH[1], PI, P, null]]; } });
// h03 «Телефон в кармане завибрировал. Я накрыл его ладонью. Поздно»: вздрогнул на вибрацию (0,1–0,5), глаза вниз, на карман; ладонь накрывает
// карман на «накрыл» (1,8–2,3), а не с первого кадра; на «Поздно» поднимает глаза — туда, где шаги
SH.h_pocket = { loc: 'lobby', pos: [5.35, .62, -.77], look: [5.78, .34, -.1], fov: 44, face: { temych: 'hush' }, set: (o, t) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .25; o.amb.intensity = .22;
  const P = eCrouch(t, seg(t, 1.8, 2.3)), R = locR(CROUCH[0], CROUCH[1], PI), jolt = Math.exp(-Math.pow((t - .22) / .12, 2));
  P.wig = [['spine_03', [1, 0, 0], -.07 * jolt], ['neck_01', [1, 0, 0], -.1 * jolt], ['clavicle_l', [0, 0, 1], .05 * jolt], ['clavicle_r', [0, 0, 1], -.05 * jolt]];
  P.look = t < .55 ? R.p(0, .75, 1.5) : t < 3.2 ? R.p(.2, .36, .15) : R.p(.6, 1.2, 1.6);
  return [['temych', CROUCH[0], 0, CROUCH[1], PI, P, null]]; } };
SH.h_face = { loc: 'lobby', pos: [5.7, .64, -.75], look: [5.72, .6, -.31], fov: 34, face: { temych: 'hush' }, set: (o, t) => { o.lamp.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .25; o.amb.intensity = .22; return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), 'cam']]; } };
SH.h_face_open = Object.assign({}, SH.h_face, { face: { temych: 'gasp' } });   // рот приоткрыт: отозвался шёпоту (h06) или нашла (k5a)
// утро: сменщик входит, подходит к будке, смотрит вниз на Итана — и пятится, не поворачиваясь спиной
// утро: Итан всё ещё под столом (из этой точки его не видно — и не должно: раньше голова торчала сквозь столешницу); сменщик смотрит вниз, на него
const CROUCH_AM = CROUCH;
// h07–h08: «хлопнула дверь, пришёл дневной охранник» — входит в кадр, подходит к будке, останавливается, наклоняется заглянуть под стойку и спрашивает
// «— Не отвечал?» (раньше с первой секунды стоял столбом у будки, хотя только что вошёл)
const GUARD_AT = [6.15, .95], E_UNDER = [5.72, .45, -.3];
SH.h_guard = { loc: 'lobby', pos: [5.6, 1.0, -.95], look: [6.1, 1.5, .9], fov: 50, face: { smenshik: 'worry' }, set: (o, t) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false;
  const G = walker('smenshik', [[5.2, 3.3], [5.5, 2.3], GUARD_AT], t - .3, 1.3, { talk: 1 }, PI), arr = seg(t, 3.0, 3.7);
  G[5].look = t < 2.4 ? [6.0, 1.5, -1.2] : E_UNDER;   // идёт — смотрит на будку; подошёл — вниз, под стойку
  if (arr > 0) { const R = locR(GUARD_AT[0], GUARD_AT[1], PI); G[5].spine = { to: E_UNDER, k: .22 * arr }; G[5].pelvis = R.v(0, -.02 * arr, -.04 * arr); G[5].legs = 'plant'; }   // наклон: таз назад, корпус к нему
  return [['temych', CROUCH_AM[0], 0, CROUCH_AM[1], PI, eCrouch(t), null], G]; } };
function backAway(name, from, to, t, v, look) {   // пятится: идёт спиной вперёд, лицом к look
  const L = Math.hypot(to[0] - from[0], to[1] - from[1]), st = stride(L, v, t, .3, .4), k = st.s / L, C = A[name], cyc = 1.3 * C.ss, w = clamp(st.sp * 1.6, 0, 1);
  const x = lerp(from[0], to[0], k), z = lerp(from[1], to[1], k), ry = Math.atan2(from[0] - to[0], from[1] - to[1]);
  return [name, x, 0, z, ry, { clips: [['Walk_Loop', -st.s / cyc * 1.333, w], ['Idle_Loop', t, 1 - w]], legs: w < .1 ? 'feet' : undefined, look }, null];
}
SH.h_guard_back = { loc: 'lobby', pos: [5.6, 1.0, -.95], look: [5.6, 1.4, 2.5], fov: 54, face: { smenshik: 'fear' }, set: (o, t) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false;
  // h09 «Он посмотрел на меня. И попятился. Медленно, не поворачиваясь спиной»: 0–1 с — смотрит вниз, на него (ещё наклонён, как в h08), лицо
  // каменеет; ~1 с — отшатнулся корпусом назад, руки сами поднялись перед собой ладонями к нему; с 1,35 — пятится короткими медленными шагами,
  // глаз не сводит, голова чуть опущена, дышит часто. Раньше пятился с первого кадра и бодрым шагом (0,55 м/с), руки висели
  const look = seg(t, 0, .9), rec = seg(t, .85, 1.4);
  A.smenshik.faceSet = t < .7 ? 'worry' : 'fear';
  const G = backAway('smenshik', GUARD_AT, [5.75, 2.75], t - 1.35, .36, E_UNDER), P = G[5], R = locR(G[1], G[3], G[4]);
  P.spine = { to: rec > 0 ? R.p(0, 2.2, -.6) : E_UNDER, k: rec > 0 ? .12 * rec : .22 * (1 - .3 * look) };   // наклон к нему → отшатнулся
  if (t < 1.35) { P.pelvis = R.v(0, -.02 * (1 - rec), -.04 * (1 - rec)); P.legs = 'plant'; }
  P.arm = { r: { at: R.p(-.17, 1.42, .3), by: 'palm', fdir: R.v(.1, 1, .2), pdir: R.v(0, .1, 1), w: .8 * rec, pole: [-.8, -1, -.3] },   // ладонь к нему на уровне груди (видна над стойкой): «тихо, тихо»
            l: { at: R.p(.2, 1.05, .22), by: 'palm', fdir: R.v(-.1, 1, .35), pdir: R.v(-.2, 0, 1), w: .55 * rec, pole: [.8, -1, -.3] } };
  P.fing = { r: ['open', rec], l: ['open', .7 * rec] }; P.br = [1.6, 1.7];   // дышит часто и глубже
  return [['temych', CROUCH_AM[0], 0, CROUCH_AM[1], PI, eCrouch(t), null], G]; } };
// отражение: Итан встал и смотрит в стекло будки — кадр снят через стекло и отзеркален, поверх грязь стекла
// «ВНУТРИ»: отражение Итана. Раньше он с первого кадра по-доброму улыбался — выходило смешно (владелец, 11 ноя). Теперь: стоит неживо, не дышит
// и не моргает, смотрит в себя; улыбка ползёт медленно и становится шире человеческой, глаза в ней не участвуют (как у «Лили» у стекла),
// голова медленно клонится набок, камера всё это время подползает к лицу
// «Внутри»: отражение в стекле будки. Первые секунды — Итан как Итан, только слишком неподвижный. Потом свет на миг гаснет — и в стекле уже не он:
// серая кожа, чёрные глаза без белков, тёмные глазницы, улыбка шире лица, которая продолжает расти, голова рывками ложится набок, камера наезжает.
// (владелец, 12 ноя: «сделать страшнее лицо, как у Лили, но ещё пострашнее» — раньше это был обычный Итан с широкой улыбкой, выглядело смешно)
SH.h_reflect = { loc: 'lobby', pos: [5.12, 1.62, 1.02], look: [5.12, 1.6, -.2], fov: 30, mirror: true, noRim: 1, set: (o, t, sh) => { setDawn(o, 1); o.lamp.intensity = 0; o.lampBulb.visible = false;
  const T0 = 2.5, thing = t >= T0 + .09, k = seg(t, T0, T0 + 3.5), k2 = seg(t, T0 + 1.5, T0 + 6), C = thing ? A.ethanThing : A.temych;
  if (thing) { C.faceSets.h_in = { smile: 1.05 + .6 * k2, mouth: 0, wide: 1.25 + .35 * k2, browUp: .2 + .6 * k2 }; C.faceSet = 'h_in'; C.tiltNow = .12 + .38 * k2; }   // улыбка, а не крик: рот почти сомкнут, растёт в ширину (текст: «улыбался шире, чем умею»)
  else { C.faceSets.h_in = { dead: .7, smile: .25 * seg(t, 1.2, T0) }; C.faceSet = 'h_in'; C.tiltNow = 0; }
  sh.fov = 30 - 4 * seg(t, .3, T0) - 6 * seg(t, T0, T0 + 6.5); o.amb.intensity = .8 - .5 * k2;
  if (t > T0 - .02 && t < T0 + .17 || (t > T0 + .3 && t < T0 + .36)) for (const n of ['amb', 'hemi', 'l1', 'l3', 'l4', 'street', 'canopy', 'boothLight']) if (o[n]) o[n].intensity = 0;   // свет моргнул — и в стекле другой
  return [[thing ? 'ethanThing' : 'temych', 5.12, 0, .05, 0, { clips: [['Idle_Loop', 1.3, 1]], still: 1, legs: 'feet' }, 'cam']]; } };
// ---- А2: бег по служебному коридору — лампы гаснут у него за спиной; под дождём; звонок ----
// 21 ноя (владелец: «Итан бежит по пустоте, текстура коридора не доделана»): раньше он бежал к камере, и за 8,5 с камера уезжала за начало коридора (z > 0) —
// в пустоту. Теперь бежит от камеры вглубь, к аварийной двери в торце (z = −26); камера держится за ним, лампы гаснут у него за спиной (x02)
SH.x_run = { loc: 'corridor', pos: [0, 1.5, -.5], look: [0, 1.35, -14], fov: 56, set: (o, t, sh) => { const z = -3.8 - Math.min(t, 8.4) * 2.4;
  sh.pos[2] = z + 3.4 + Math.sin(t * 9) * .02; sh.look[2] = z - 7;
  lampSet(o, i => -1.8 - i * 3.6 < z + .6); o.amb.intensity = .3;   // гаснут те, что уже позади него
  return [['temych', Math.sin(t * 1.3) * .1, 0, z, PI, { clips: [['Sprint_Loop', t * 1.25, 1]], look: [0, 1.5, z - 6] }, null]]; } };
// ---- серверная (Б): круг соли у самого порога, свеча; Фрэнк сидит в круге, нога в картоне ----
const SRV_C = [1.75, 2.75], FRANK = [1.85, 2.67], FRANKY = .07, FRANK_HEAD = [1.95, .62, 3.15];   // FRANK_HEAD — где лицо сидящего Фрэнка (на него смотрит Итан)   // FRANKY — на 7 см выше пола: в этой позе таз и левая ступня уходили под пол на 10 см; FRANK — на 23 см от двери: в позе (LayToIdle) он откинут назад на 50–75 см, спиной к двери, а не сквозь неё   // центр круга, где сидит Фрэнк (спиной к двери)
function srvSet(o) { o.salt.position.set(SRV_C[0], .012, SRV_C[1]); o.candle.position.set(1.35, .3, 2.55); }
const SPLINT = ['calf_l', [0, .2, 0], [0, 0, 0]];
// сидит на полу (кадр из клипа «встаёт с пола»: опирается на руку, ноги вытянуты); sheet — держит лист к камере; reach — тянется на голос
function frankSit(t, o2 = {}) {
  const P = { clips: [['LayToIdle', o2.sheet ? .62 : o2.u ?? .45, 1]], wear: { splint: SPLINT }, talk: 1, look: o2.look, heels: 0, br: [2.2, 1.5], pain: o2.sheet ? .5 : 1 };   // сломана нога: дышит тяжело, иногда сводит от боли   // heels: пол серверной (y = 0) — пятка вытянутой ноги на полу, а не под ним
  if (o2.sheet) { P.fing = { l: ['pinch', 1], r: ['pinch', 1] }; }
  if (o2.no) P.wig = [['neck_01', [0, 1, 0], Math.sin(t * 7) * .35 * o2.no]];
  return P;
}
// этап 1 (19 ноя): всю ночь камера серверной была завешена тряпкой (оно смотрит через камеры — «всю ночь там было черно», s05). В 04:10 Фрэнк сдёргивает её
// за шнур: первые полсекунды кадр чёрный, потом тряпка сползает с объектива вниз
function srvRag(o, t) {
  if (!o.rag) { o.rag = plane(1.3, 1.3, new T.MeshBasicMaterial({ color: 0x0b0a09, side: T.DoubleSide, fog: false }), o.S); o.rag.userData.solid = false; (o.xtra = o.xtra || []).push(o.rag); }
  if (t > 1.3) return;
  const C = [-3.3, 2.65, -2.6], d = nrm([1.6 - C[0], .7 - C[1], 2.37 - C[2]]), f = clamp((t - .45) / .75, 0, 1), dn = 1.5 * f * f;
  o.rag.visible = true; o.rag.position.set(C[0] + d[0] * .32, C[1] + d[1] * .32 - dn, C[2] + d[2] * .32); o.rag.lookAt(C[0], C[1] - dn * .5, C[2]); o.rag.rotateZ(.45 * f);
}
SH.srv_cam = { loc: 'server', pos: [-3.3, 2.65, -2.6], look: [1.6, .7, 2.37], fov: 50, cctv: 1, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o); srvRag(o, t); return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t, { sheet: true }), [-3.3, 2.65, -2.6]]]; } };
SH.srv_look = { loc: 'server', pos: [-3.3, 2.65, -2.6], look: [1.6, .7, 2.37], fov: 50, cctv: 1, face: { mihalych: 'fear' }, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t), t > .4 ? [-2.5, 1.0, -1.5] : [-3.3, 2.65, -2.6]]]; } };
SH.srv_leg = { loc: 'server', pos: [1.3, .65, 1.82], look: [1.6, .2, 2.32], fov: 42, set: (o, t) => { srvSet(o); return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t), [.2, 1.25, 1.2]]]; } };
// ---- CAM 13: коридор, которого нет (тот же 7-й, тусклый и длинный); оно в дальнем конце ----
function c13Set(o, n = 4) { setLamps(o, n); o.S.fog.density = .045; o.amb.intensity = .3; }
function faceLit(o, p, k = 1.6, c = 0xc8d4e8) { o.faceL.position.set(...p); o.faceL.intensity = k; o.faceL.distance = 3.5; o.faceL.color.setHex(c); }
const C13P = [1.0, 2.75, -.5];
const C13G = [.85, 2.72, -9.95];   // «тринадцатая» в 04:10: камера, которой в здании нет, — под потолком у самой 707, Гас и оно прямо под ней
// поворот на месте: сначала голова через плечо, потом корпус — мелкими шажками (не скольжением)
function turnRound(name, x, z, t, t0, dur, clip = 'Idle_Loop', extra = {}) {
  const k = clamp((t - t0) / dur, 0, 1), hk = ease(clamp(k * 2.4, 0, 1)), bk = ease(clamp((k - .3) / .7, 0, 1)), st = Math.sin(PI * clamp((k - .3) / .7, 0, 1));
  const ry = PI * (1 - bk);
  return [name, x, 0, z, ry, Object.assign({ clips: [[clip, t + 2, 1 - st * .45], ['Walk_Loop', k * 1.6, st * .45]], twist: -.9 * hk * (1 - bk), legs: st < .08 ? 'feet' : undefined }, extra), hk > .4 ? 'cam' : null];
}
SH.c13_back = { loc: 'corridor', pos: C13P, look: [-.1, 1.2, -20], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 3); return [['monster', 0, 0, -19.5, PI, { clips: [['Zombie_Idle_Loop', t, 1]], legs: 'feet' }, null]]; } };
SH.c13_turn = { loc: 'corridor', pos: C13P, look: [-.1, 1.4, -20], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 3); faceLit(o, [.3, 2.2, -18.2], 1.2); return [turnRound('monster', 0, -19.5, t, .2, 2.8, 'Zombie_Idle_Loop')]; } };
// смерти «посмотрел в лицо» / «засмотрелся»: оно стоит посреди коридора во весь рост лицом к объективу и не шевелится — ждало. Лицо — выше субтитров
// (было: сидело в зомби-позе внизу слева, под субтитрами его не было видно, а наезд уводил его из кадра)
const C13M = [.3, -4.6], C13F = [.3, 2.12, -4.6];   // где стоит оно; его лицо (на него целится наезд — cams в story.js)
const c13Wait = (o, t, talk) => { c13Set(o, 4); faceLit(o, [.55, 2.65, -3.0], 1.7);
  return [['monster', C13M[0], 0, C13M[1], Math.atan2(C13P[0] - C13M[0], C13P[2] - C13M[1]), Object.assign(STILL('Idle_Loop', 1.7), talk ? { post: C => { C.talk = talk(t); } } : {}), 'cam']]; };
SH.c13_face = { loc: 'corridor', pos: C13P, look: [.3, 1.9, -4.6], fov: 44, cctv: 1, set: (o, t) => c13Wait(o, t) };
// «Оно сказало моё имя»: губы складывают два слога — «И-тан» (без звука: голос — в рассказе Итана)
SH.c13_name = { loc: 'corridor', pos: C13P, look: [.3, 1.9, -4.6], fov: 44, cctv: 1, set: (o, t) => c13Wait(o, t, t => t > .25 && t < .5 ? .35 : t > .62 && t < .98 ? .6 : 0) };
SH.c13_lily = { loc: 'corridor', pos: C13P, look: [-.1, 1.2, -18], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 3); faceLit(o, [.3, 1.9, -16.8], 1.4, 0xe8dcc8); return [turnRound('katyaThing', 0, -18, t, 6.4, 2.8, 'Idle_Loop', { toes: 1 })]; } };   // a10: оборачивается на «Она медленно обернулась» (6,9 с), а не на «включился сам» (было с 1,6 с)
SH.c13_lily_face = { loc: 'corridor', pos: C13P, look: [.2, 1.5, -8.5], fov: 40, cctv: 1, set: (o, t) => { c13Set(o, 4); faceLit(o, [.5, 1.8, -7.3], 1.8, 0xe8dcc8);
  return [['katyaThing', .2, 0, -8.5, Math.atan2(C13P[0] - .2, C13P[2] + 8.5), { clips: [['Idle_Loop', t + 1, 1]], legs: 'feet' }, 'cam']]; } };
// s02–s04: CAM 13 показывает седьмой: Гас лежит в дверях 707, над грудью у него сидит оно; потом поднимает голову к объективу
// Над Гасом сидит оно и ест: рвёт, тащит ко рту, рывками (поза 'eat' в chars2: брызги, потёки изо рта, кишки к руке); тело вздрагивает.
// up: перестаёт есть, замирает и медленно поднимает голову к объективу — лицо в крови, глаза ловят ИК-подсветку камеры и загораются белым
// ---- жесть 18+ (владелец: «вспарывает живот, вытягивает органы и ест»; смерти — у каждой своя): кишки трубками, рана, точки тела ----
const GUT_M = () => ps1(new T.MeshPhongMaterial({ color: 0xffffff, vertexColors: true, specular: 0x6a3a3a, shininess: 70, emissive: 0x0e0102 }));   // кишки: розово-серые, мокрые, в крови пятнами (цвет — по вершинам)
const _gc = new T.CatmullRomCurve3(Array.from({ length: 6 }, () => new T.Vector3())), _gv = new T.Vector3(), _gw = new T.Vector3();
function gutsDraw(loc, list, t) {   // list: [[a, b, провис, радиус], …] — точки в мире после поз; лишние трубки прячутся
  const G = loc.gutT || (loc.gutT = []);
  while (G.length < list.length) { const m = new T.Mesh(new T.BufferGeometry(), loc.gutM || (loc.gutM = GUT_M())); m.frustumCulled = false; loc.S.add(m); G.push(m); }
  G.forEach((m, i) => { const L = list[i]; m.visible = !!L; if (!L) return;
    const [a, b, sag, r] = L, P = _gc.points;
    for (let j = 0; j < 6; j++) { const u = j / 5, w = Math.sin(u * PI);
      P[j].set(lerp(a.x, b.x, u) + Math.sin(u * 8 + t * 3.1 + i) * .09 * w, Math.max(.03, lerp(a.y, b.y, u) - w * sag), lerp(a.z, b.z, u) + Math.cos(u * 7 + t * 2.6 + i * 2) * .09 * w); }   // петлями, не прямой трубой
    const geo = new T.TubeGeometry(_gc, 36, r, 8, false), pa = geo.attributes.position;
    for (let q = 0; q <= 36; q++) { const f = 1 + .17 * Math.sin(q * 2.6) * Math.sin(q * .37 + 1);   // перехваты: кольца то шире, то уже — кишка, а не шланг
      _gv.set(0, 0, 0); for (let k = 0; k <= 8; k++) _gv.add(_gw.fromBufferAttribute(pa, q * 9 + k)); _gv.multiplyScalar(1 / 9);
      for (let k = 0; k <= 8; k++) { const ix = q * 9 + k; _gw.fromBufferAttribute(pa, ix).sub(_gv).multiplyScalar(f).add(_gv); pa.setXYZ(ix, _gw.x, _gw.y, _gw.z); } }
    const col = new Float32Array(pa.count * 3);
    for (let q = 0; q <= 36; q++) { const bl = smoothstep(.35, .75, _fr(Math.sin(q * 3.7 + i * 17.3) * 9137.1) * .6 + _fr(Math.sin(q * .9 + i * 5.1) * 4211.7) * .4);
      for (let k = 0; k <= 8; k++) { const ix = (q * 9 + k) * 3, sh = .85 + .25 * _fr(Math.sin((q * 9 + k) * 1.3) * 777.7);
        col[ix] = lerp(.6, .3, bl) * sh; col[ix + 1] = lerp(.3, .02, bl) * sh; col[ix + 2] = lerp(.28, .03, bl) * sh; } }
    geo.setAttribute('color', new T.BufferAttribute(col, 3));
    geo.computeVertexNormals(); m.geometry.dispose(); m.geometry = geo; });
}
const smoothstep = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
// точки тела в мире по костям (после позы): живот, грудь, горло, лицо; fw — куда смотрит грудь, lr — влево
function bodyPts(C) {
  const W = n => C.bone[n].getWorldPosition(new T.Vector3()), s1 = W('spine_01'), s2 = W('spine_02'), s3 = W('spine_03'), nk = W('neck_01'), hd = W('Head');
  const up = s3.clone().sub(s1).normalize(), lr = W('thigh_l').sub(W('thigh_r')).normalize(), fw = new T.Vector3().crossVectors(lr, up).normalize(), k = C.ss || 1;
  return { fw, up, lr, belly: s1.clone().lerp(s2, .5).addScaledVector(fw, .15 * k), chest: s3.clone().addScaledVector(fw, .13 * k), throat: nk.clone().addScaledVector(fw, .05 * k), face: hd.clone().addScaledVector(fw, .1 * k).addScaledVector(up, .03 * k) };
}
const mouthPt = C => mouthOf(C)[0];   // рот в мире (mouthOf ниже отдаёт [рот, куда смотрит])
// рана на теле (шейдер humanMat: uRip): живот, грудь, горло, лицо, глаза; k — насколько раскрыта
function wound(C, kind, k) {
  if (!C || !C.HU || !C.HU.uRip) return; const H = C.HU, ey = C.eyeY || 1.64, ez = C.eyeZ || .03;
  const W = { belly: [0, 1.06, .17, .11, .08, .12], chest: [0, 1.33, .16, .12, .1, .12], throat: [0, ey - .14, -.01, .06, .045, .1], face: [0, ey - .03, ez + .02, .065, .08, .1], eyes: [0, ey, ez + .005, .07, .025, .1], thigh: [.1, .62, .08, .08, .13, .14] }[kind] || [0, 1.06, .17, .11, .08, .12];   // лицо плоское: нос всего на z ≈ .067
  H.uRip.value = clamp(k, 0, 1); H.uRipP.value.set(W[0], W[1], W[2]); H.uRipR.value.set(W[3] * 1.3, W[4] * 1.3, W[5] * 1.15);   // рана на треть крупнее if (H.uRipK) H.uRipK.value = { belly: 0, chest: 1, throat: 2, face: 3, eyes: 4, thigh: 5 }[kind] || 0;
}
// ---- финалы смертей 18+: после скримера 2,6 с видно, как оно убивает — у каждой смерти свой вид (engine.js GORE_KIND) ----
// Кадр строится из последнего кадра смерти: камера на той же линии взгляда (она свободна — оттуда смотрели), ближе к месту, где стояло оно.
// Виды: eat — вспарывает живот и ест кишки; drag — тащит за ноги, кишки волочатся; chest — когти сквозь грудь, внутренности вываливаются;
// neck — сворачивает голову; throat — вырывает горло; face — отгрызает лицо; eyes — когти в глаза. Хвост L — «Лили», D — двойник, F — жертва Фрэнк.
const GORE = {}, _fr = x => x - Math.floor(x), _gm4 = new T.Matrix4();
function goreFx(loc) {
  if (loc.gfx) return loc.gfx; const f = loc.gfx = {}, S = loc.S;
  const pm = ps1(new T.MeshPhongMaterial({ color: 0x3a0306, specular: 0x5a2020, shininess: 110, polygonOffset: true, polygonOffsetFactor: -3, polygonOffsetUnits: -3 }));
  f.pool = new T.Mesh(new T.CircleGeometry(1, 32), pm); f.pool.rotation.x = -PI / 2; f.pool.visible = false; S.add(f.pool);
  f.trail = new T.Mesh(new T.PlaneGeometry(1, 1), pm); f.trail.rotation.x = -PI / 2; f.trail.visible = false; S.add(f.trail);
  f.spray = new T.InstancedMesh(new T.SphereGeometry(.0105, 10, 6), mat(0x3a0303, null, { basic: true }), 240); f.spray.frustumCulled = false; f.spray.visible = false; S.add(f.spray);   // капли (были кубики — «квадратики»)
  f.heart = new T.Mesh(new T.SphereGeometry(.055, 10, 8), loc.gutM || (loc.gutM = GUT_M())); f.heart.scale.set(1, 1.25, .9); f.heart.visible = false; S.add(f.heart);
  return f;
}
const _dq = new T.Quaternion(), _dv = new T.Vector3(), _dp = new T.Vector3(), _ds = new T.Vector3(), _dy = new T.Vector3(0, 1, 0);
function sprayAt(f, from, dir, t, bursts) {   // брызги дугой из раны и на пол: капли вытянуты по полёту, разного размера; на полу — плоские; время — от начала кадра, без состояния
  let n = 0;
  // жести больше (владелец, 11 ноя): капель втрое больше и крупнее; упавшие не исчезают через .9 с, а остаются на полу — к концу сцены пол вокруг забрызган
  for (const b of bursts) { const dt0 = t - b; if (dt0 < 0) continue;
    for (let i = 0; i < 44 && n < 240; i++, n++) { const h1 = _fr(Math.sin(i * 12.9898 + b * 78.233) * 43758.5453), h2 = _fr(Math.sin(i * 39.346 + b * 11.135) * 24634.634), h3 = _fr(Math.sin(i * 73.156 + b * 52.235) * 12874.313);
      const vx = dir.x * 1.5 + (h1 - .5) * 2.1, vy = .9 + h2 * 1.9, vz = dir.z * 1.5 + (h3 - .5) * 2.1, tl = (vy + Math.sqrt(vy * vy + 19.6 * Math.max(0, from.y - .012))) / 9.8, dt = Math.min(dt0, tl), y = from.y + vy * dt - 4.9 * dt * dt, down = dt0 >= tl;
      _dp.set(from.x + vx * dt, Math.max(.008, y), from.z + vz * dt); _dv.set(vx, vy - 9.8 * dt, vz); const sp = _dv.length() || 1, k = .6 + 1.1 * h3;
      if (down) { _dq.identity(); _ds.set(k * 1.3 * (1 + .6 * h1), .1, k * 1.3 * (1 + .6 * h2)); }   // упавшая капля — небольшое пятно (крупные читались красными шестиугольниками) else { _dq.setFromUnitVectors(_dy, _dv.multiplyScalar(1 / sp)); _ds.set(k, k * (1 + Math.min(3, sp * .55)), k); }   // летит — вытянута; упала — пятнышко
      _gm4.compose(_dp, _dq, _ds); f.spray.setMatrixAt(n, _gm4); } }
  for (; n < 240; n++) { _gm4.makeScale(0, 0, 0); f.spray.setMatrixAt(n, _gm4); }
  f.spray.instanceMatrix.needsUpdate = true; f.spray.visible = true;
}
function twistHead(C, up, ang) {   // голова за предел: шея и голова поворачиваются вокруг оси тела (в мире)
  for (const [bn, k] of [['neck_01', .45], ['Head', .55]]) { const b = C.bone[bn]; if (!b) continue; b.updateWorldMatrix(true, false);
    const wq = b.getWorldQuaternion(new T.Quaternion()).premultiply(new T.Quaternion().setFromAxisAngle(up, ang * k));
    b.parent.updateWorldMatrix(true, false); b.quaternion.copy(b.parent.getWorldQuaternion(new T.Quaternion()).invert().multiply(wq)); b.updateWorldMatrix(false, true); }
}
const goreWho = kind => ({ V: /F$/.test(kind) ? 'mihalych' : 'temych', K: /L$/.test(kind) ? 'katyaThing' : /D$/.test(kind) ? 'ethanThing' : 'monster', base: kind.replace(/[LDF]$/, '') });
// финал смерти — на свободном месте: там, куда смотрел кадр, бывает стол, стена, тесная будка (тела уходили в стену, камера — за спину). Ищем рядом пятак
// по коллайдерам твёрдого мира: тела и обе камеры (общий и крупный план) стоят в пустом, между камерой и телами нет стены, место видно с исходной камеры
// (та же комната); не нашлось — поворачиваем взгляд. Одно место на смерть — у общего и крупного плана одинаковое
const GFIT = {};
const goreCamD = (lay, dist, shift) => Math.max(lay ? 1.6 : 2.05, dist - shift);   // общий план: стоя — не ближе 2 м (иначе голова срезана краем кадра), лёжа — 1,6 м
// следы предметов на полу по коллайдерам твёрдого мира: dF — сколько от точки до ближайшего (low — только низкие, лежащему стол сверху не мешает),
// sight — отрезок взгляда не пересекает высокий предмет
function gSolid(loc) {
  const o = L[loc], F = [], FT = [];   // следы предметов на полу; FT — высокие (заслоняют взгляд)
  o.reset(); o.S.updateMatrixWorld(true);
  for (const K of solidCols(o)) { solidNow(K); if (!K.on) continue;
    const ey = K.kind === 'cyl' ? Math.abs(K.u[1].y) * K.e[1] + Math.sqrt(Math.max(0, 1 - K.u[1].y * K.u[1].y)) * K.e[0] : Math.abs(K.u[0].y) * K.e[0] + Math.abs(K.u[1].y) * K.e[1] + Math.abs(K.u[2].y) * K.e[2], lo = K.c.y - ey, hi = K.c.y + ey;
    if (hi < .15 || lo > 1.9) continue;   // под ногами или над головой
    let f; if (K.kind === 'cyl' && Math.abs(K.u[1].y) > .9) f = [K.c.x, K.c.z, K.e[0]];
    else { const best = [0, 1, 2].sort((i, j) => Math.hypot(K.u[j].x, K.u[j].z) * K.e[j] - Math.hypot(K.u[i].x, K.u[i].z) * K.e[i])[0], h = Math.hypot(K.u[best].x, K.u[best].z) || 1, ax = K.u[best].x / h, az = K.u[best].z / h;
      const ext = (x, z) => [0, 1, 2].reduce((a, i) => a + Math.abs(K.u[i].x * x + K.u[i].z * z) * (K.kind === 'cyl' && i !== 1 ? K.e[0] : K.e[i]), 0);
      f = [K.c.x, K.c.z, ax, az, ext(ax, az), ext(-az, ax)]; }
    f.lo = lo; F.push(f); if (hi > 1.45) FT.push(f); }
  const dF = (x, z, low) => { let m = 1e9; for (const f of F) { if (low && f.lo > .4) continue; let d;   // лежащему телу стол сверху не мешает
    if (f.length === 3) d = Math.hypot(x - f[0], z - f[1]) - f[2];
    else { const qx = x - f[0], qz = z - f[1], qa = Math.abs(qx * f[2] + qz * f[3]) - f[4], qb = Math.abs(-qx * f[3] + qz * f[2]) - f[5]; d = qa > 0 || qb > 0 ? Math.hypot(Math.max(qa, 0), Math.max(qb, 0)) : Math.max(qa, qb); }
    if (d < m) m = d; } return m; };
  const sight = (ax, az, bx, bz) => {   // отрезок взгляда не пересекает высокий предмет (точно, а не по точкам: стекло тонкое — точки его перепрыгивали)
    for (const f of FT) {
      if (f.length === 3) { const dx = bx - ax, dz = bz - az, L2 = dx * dx + dz * dz || 1, k = clamp(((f[0] - ax) * dx + (f[1] - az) * dz) / L2, 0, 1); if (Math.hypot(ax + dx * k - f[0], az + dz * k - f[1]) < f[2]) return false; continue; }
      const A = (x, z) => [(x - f[0]) * f[2] + (z - f[1]) * f[3], -(x - f[0]) * f[3] + (z - f[1]) * f[2]], p = A(ax, az), q = A(bx, bz); let t0 = 0, t1 = 1, hit = true;
      for (const [a, b, e] of [[p[0], q[0], f[4] + .03], [p[1], q[1], f[5] + .03]]) { const d = b - a; if (Math.abs(d) < 1e-9) { if (Math.abs(a) > e) { hit = false; break; } continue; }
        let u = (-e - a) / d, v = (e - a) / d; if (u > v) [u, v] = [v, u]; t0 = Math.max(t0, u); t1 = Math.min(t1, v); if (t0 > t1) { hit = false; break; } }
      if (hit) return false; }
    return true; };
  return { dF, sight };
}
function goreFit(base, kind, lay, P, dir, shift, dist) {
  const key = base.loc + '|' + base.pos.join(',') + '|' + kind; if (key in GFIT) return GFIT[key];
  const bk = goreWho(kind).base, side = bk === 'face' || goreWho(kind).K !== 'monster', why = GFIT['?' + key] = {}, { dF, sight } = gSolid(base.loc);
  const ok = (px, pz, dx, dz) => { const rx = dz, rz = -dx, at = (f, r) => [px + dx * f + rx * r, pz + dz * f + rz * r], body = [];
    if (bk === 'drag') for (const f of [.7, .25, -.2, -.65, -1.1, -1.55, -1.9]) body.push([...at(f, 0), .32, 0]);
    else if (lay) { for (const r of [-.85, -.42, 0, .42, .85]) body.push([...at(.12, r), .26, 1]); body.push([...(bk === 'limb' ? at(-.45, -.5) : at(-.5, .15)), .42, 0]); }
    else body.push([...at(.15, 0), .3, 0], [...(side ? at(.05, .5) : at(-.24, .04)), .36, 0]);
    const no = w => { why[w] = (why[w] || 0) + 1; return false; };   // почему не подошло (для проверки: World.gfit)
    for (const [x, z, r, low] of body) if (dF(x, z, low) < r) return no('тело');
    const cams = [at(lay ? 1.05 : 1.15, 0), at(goreCamD(lay, dist, shift), 0)];
    for (const [x, z] of cams) { if (dF(x, z) < .28) return no('камера в предмете'); if (!sight(x, z, px, pz)) return no('камере заслоняет'); }
    return !room || sight(base.pos[0], base.pos[2], px, pz) || no('не та комната'); };   // с места исходной камеры видно — та же комната, а не за стеной
  let room = true;   // сперва — в той же комнате (видно с исходной камеры); в тесной будке места нет — тогда рядом за стеклом, в холле (вытащило наружу)
  for (const pass of [0, 1]) { room = !pass;
  for (const da of [0, .3, -.3, .6, -.6, .95, -.95, 1.3, -1.3, 1.75, -1.75, PI]) { const c = Math.cos(da), sn = Math.sin(da), dx = dir.x * c + dir.z * sn, dz = -dir.x * sn + dir.z * c;
    for (const r of [0, .2, .4, .6, .85, 1.1, 1.4, 1.8, 2.2, 2.7]) for (let j = 0, n = r ? 16 : 1; j < n; j++) { const px = P.x + Math.cos(j / n * PI * 2) * r, pz = P.z + Math.sin(j / n * PI * 2) * r;
      if (ok(px, pz, dx, dz)) return (GFIT[key] = { P: new T.Vector3(px, 0, pz), dir: new T.Vector3(dx, 0, dz) }); } } }
  return (GFIT[key] = null);   // тесно везде — как было
}
// скример смерти — в том же месте, где потом идёт убийство (владелец, 12 ноя: «почему скример в разных комнатах, если он убивает Итана там, где Итан был?»).
// Раньше лицо Жильца для всех смертей снималось в серверной. Теперь он встаёт перед камерой сцены убийства, вплотную; три ракурса, у каждой смерти — свой
function scareAt(gid, v0 = 0) { const id = 'scare@' + gid + '#' + v0; if (SH[id]) return id; const G = goreShot(gid); if (!G || !G.pos || !G.look) return null;
  const v = v0 === 0 ? 1 : v0, mir = v0 === 0 ? -1 : 1;   // ракурс «в лоб» выходил тёмным в любом месте — вместо него зеркальный «снизу-сбоку» (кадры 12 ноя)
  const dx = G.look[0] - G.pos[0], dz = G.look[2] - G.pos[2], L = Math.hypot(dx, dz) || 1, ux = dx / L, uz = dz / L, d = [.95, .62, .42][v] ?? .95, M = [G.pos[0] + ux * d, G.pos[2] + uz * d], ry = Math.atan2(-ux, -uz);
  const cam = v === 1 ? [G.pos[0] - uz * .2 * mir, 1.84, G.pos[2] + ux * .2 * mir] : [G.pos[0], v === 2 ? 2.17 : 2.14, G.pos[2]];
  SH[id] = { loc: G.loc, pos: cam, look: [M[0], 2.12, M[1]], fov: v ? 46 : 40, fog: .2, noDolly: 1, set: (o, t) => { const C = A.monster; C.faceSet = 'scream'; C.tiltNow = v === 1 ? mir * (.55 + .1 * Math.sin(t * 31)) : v === 2 ? -.18 : 0;
    const fl = v === 2 ? (Math.sin(t * 83) + Math.sin(t * 47) > -.4 ? 1 : .08) : v === 1 ? (Math.sin(t * 67) > -.2 ? 1 : .25) : 1;
    for (const n of ['amb', 'hemi', 'l1', 'l3', 'l4', 'lamp', 'boothLight', 'cold', 'cold2', 'street', 'canopy']) if (o[n] && o[n].intensity != null) o[n].intensity *= .12;   // место то же, но света почти нет: видно лицо
    if (o.lampBulb) o.lampBulb.visible = false;
    if (o.faceL) { faceLit(o, [cam[0] - ux * .05, cam[1] - .16, cam[2] - uz * .05], (v ? 3.2 : 5) * fl, v === 2 ? 0xff6a50 : 0xe8dccc); o.faceL.distance = 2.6; }   // лампа у самой камеры, чуть ниже объектива (дальше она оказывалась внутри его груди — лицо оставалось тёмным)   // свет снизу, от камеры
    return [['monster', M[0], 0, M[1], ry, 'monster', 'cam']]; } };
  return id; }
function goreShot(id) {
  if (GORE[id]) return GORE[id]; const m = /^gore@([a-z0-9_]+):([a-zA-Z]+)(\|c)?$/.exec(id); if (!m) return null;
  const base = SH[ALIAS[m[1]] || m[1]]; if (!base || !base.pos || !base.look) return null;
  const kind = m[2], close = !!m[3], { base: bk } = goreWho(kind), lay = /^(eat|drag|limb|heart)$/.test(bk), from = new T.Vector3(base.pos[0], 0, base.pos[2]), P = new T.Vector3(base.look[0], 0, base.look[2]);
  const d0 = from.distanceTo(P); if (d0 > 4.5) P.lerp(from, 1 - 3.5 / d0);   // смотрели далеко (камера наблюдения) — место ближе к камере
  let dir = from.clone().sub(P).normalize(); const dist = clamp(from.distanceTo(P), 1.5, 2.4), shift = Math.min(.5, from.distanceTo(P) * .25);
  P.addScaledVector(dir, shift);   // люди — на полметра ближе к камере (там, куда смотрели, бывает стена или проём); камера — где была
  if (GPLACE[m[1]] === 'hide' && bk === 'drag') { if (close) return (GORE[id] = goreShot(id.slice(0, -2)));   // под стойкой: вытащили за лодыжку (hideSet)
    const TL = Object.assign({}, GTH);
    return (GORE[id] = { loc: 'lobby', pos: [5.75, .5, -.22], look: [5.82, .58, -1.2], fov: 50, gore: kind, tl: TL, noDolly: 1,
      set: (o, t, sh) => hideSet(o, t, kind, TL, sh), post: (o, t, used) => hidePost(o, t, kind, TL, used) }); }
  if (GPLACE[m[1]] === 'seat' && GTS[bk]) { if (close) return (GORE[id] = goreShot(id.slice(0, -2)));   // в будке за пультом (seatSet)
    const TL = Object.assign({}, GTS[bk]);
    return (GORE[id] = { loc: 'lobby', pos: [4.66, 1.31, .04], look: [5.3, 1.42, -.68], fov: 46, gore: kind, tl: TL, noDolly: 1,
      set: (o, t, sh) => seatSet(o, t, kind, TL, sh), post: (o, t, used) => seatPost(o, t, kind, TL, used) }); }
  const TL = gtl(kind);   // смерть-сцена по сценарию (GTL): своя камера внутри кадра, крупного «|c» нет
  if (TL) { if (close) return (GORE[id] = goreShot(id.slice(0, -2)));
    const g = gFit(base, kind, P, dir); if (g) { const S = gStage(g.P, g.dir, g.m, goreWho(kind).K);
      return (GORE[id] = { loc: base.loc, pos: [g.P.x + g.dir.x * 3, 1.42, g.P.z + g.dir.z * 3], look: [g.P.x, 1.18, g.P.z], fov: 42, gore: kind, tl: TL, noDolly: 1,
        set: (o, t, sh) => gSet(o, t, kind, S, TL, sh), post: (o, t, used) => gPost(o, t, kind, S, TL, used) }); } }
  const fit = goreFit(base, kind, lay, P, dir, shift, dist); if (fit) { P.copy(fit.P); dir = fit.dir; }   // а если и там тесно — ближайший свободный пятак (goreFit)
  const wh = { chest: 1.3, hang: 1.75, neck: 1.6, throat: 1.5, face: 1.62, eyes: 1.65 }[bk] || 1.3;   // высота раны (крупный план)
  const cam = close ? (lay ? [P.x + dir.x * 1.05, .95, P.z + dir.z * 1.05] : [P.x + dir.x * 1.15, wh + .05, P.z + dir.z * 1.15])
    : [P.x + dir.x * goreCamD(lay, dist, shift), lay ? clamp(base.pos[1], .9, 1.6) : clamp(base.pos[1], 1.3, 1.8), P.z + dir.z * goreCamD(lay, dist, shift)];
  return (GORE[id] = { loc: base.loc, pos: cam, look: close ? [P.x, lay ? .22 : wh - .05, P.z] : [P.x, lay ? .3 : 1.32, P.z], fov: close ? 38 : lay ? 52 : 46, gore: kind,
    set: (o, t) => goreSet(o, t, kind, P, dir), post: (o, t, used) => gorePost(o, t, kind, P, dir, used) });
}
const stg = (t, a, b) => clamp((t - a) / (b - a), 0, 1), V3 = a => new T.Vector3(a[0], a[1], a[2]);
// раскадровка (как у студий — «парные» убийства): 1) захват, жертва вырывается, хватает его за руку; 2) когти входят, рана раскрывается, брызги;
// 3) рывок — внутренности наружу; 4) ест / добивает, жертва в агонии. Финал 3,4 с (GORE_D), середина — крупным планом (engine.js)
function goreSet(o, t, kind, P, dir) {
  const { V, K, base } = goreWho(kind), a = Math.atan2(dir.x, dir.z), rt = new T.Vector3(dir.z, 0, -dir.x);
  const at = (f, r) => [P.x + dir.x * f + rt.x * r, P.z + dir.z * f + rt.z * r];
  A[K].gore = 1; if (A[K].o.monster) A[K].faceSet = t < 1.2 ? 'scream' : 'grin';
  if (o.faceL) { const fl = Math.sin(t * 23) > -.7 ? 1 : .35;   // свет над местом убийства (свой «лицевой» свет локации — новые лампы пересобрали бы все шейдеры)
    o.faceL.color.setHex(0xe6ecff); o.faceL.position.set(P.x + dir.x * .7, 2.35, P.z + dir.z * .7); o.faceL.distance = 6; o.faceL.intensity = (CINE ? 3.2 : 2.4) * fl; }
  const C = A[V]; C.struggle = 1 - stg(t, .9, 2.0); C.twitch = t > 1.6 && t < 3.0 ? .8 : 0;   // бьётся, потом судороги
  if (base === 'eat' || base === 'heart' || base === 'limb') { const [vx, vz] = at(.12, 0), [mx, mz] = base === 'limb' ? at(-.45, -.5) : at(-.5, .15);
    return [[V, vx, LAYY, vz, a + PI * .5, 'lay', null], [K, mx, 0, mz, base === 'limb' ? a + .4 : a, 'eat', base === 'limb' ? [vx - rt.x * .45, .15, vz - rt.z * .45] : [vx, .22, vz]]]; }
  if (base === 'drag') { const s = Math.min(1, t / 2.6), [vx, vz] = at(.7 - s * 1.3, 0), [mx, mz] = at(-.45 - s * 1.3, 0);
    return [[V, vx, LAYY, vz, a, 'lay', null], [K, mx, 0, mz, a + PI, 'back', null]]; }
  // стоя: жертва лицом к камере, оно сзади (выше на голову) или сбоку (лицо; «Лили» и двойник ниже — их сзади не видно)
  const [vx, vz] = at(.15, 0), side = base === 'face' || K !== 'monster';
  const lift = base === 'hang' ? .55 * stg(t, .25, 1.2) : base === 'chest' || base === 'neck' ? .16 * stg(t, .5, 1.6) : 0, [mx, mz] = side ? at(.05, .5) : at(-.24, .04), hy = 1.62 + lift;
  const pt = (f, r, y) => [vx + dir.x * f + rt.x * r, y, vz + dir.z * f + rt.z * r];
  const head = pt(.06, 0, hy), neck = pt(.07, 0, hy - .13), chest = pt(.16, 0, hy - .32), shL = pt(0, .2, hy - .2), shR = pt(0, -.2, hy - .2);
  const s2 = stg(t, .45, 1.1), s3 = stg(t, 1.2, 1.9);   // когти входят → рывок наружу
  const thru = base === 'chest' ? pt(.16 + .16 * s2 - .06 * s3, -.02, hy - .32 - .1 * s3) : chest;   // кисть проходит грудь насквозь и выдирает
  const arm = base === 'hang' ? { r: { at: neck, by: 'palm' }, l: { at: pt(.06, 0, hy + .06), by: 'palm' } }
    : base === 'chest' ? { r: { at: thru, by: 'palm' }, l: { at: shL, by: 'palm' } }
    : base === 'neck' ? { r: { at: pt(.06, -.1, hy), by: 'palm' }, l: { at: pt(.06, .1, hy), by: 'palm' } }
    : base === 'eyes' ? { r: { at: pt(.1 - .04 * s2, -.035, hy + .03), by: 'tip' }, l: { at: pt(.1 - .04 * s2, .035, hy + .03), by: 'tip' } }
    : { r: { at: shR, by: 'palm' }, l: { at: shL, by: 'palm' } };
  const bite = base === 'throat' ? pt(.07 - .03 * s2, 0, hy - .13) : base === 'face' ? head : pt(.06, 0, hy + .05);
  // жертва: сначала хватает его за руку (пытается оторвать), потом руки падают, колени подгибаются
  const grab = t < 1.4 && base !== 'drag', vk = stg(t, .4, 2.2), wr = pt(.12, -.05, hy - .3);
  return [[V, vx, lift, vz, a, { clips: [['Idle_Loop', 0, 1]], legs: base === 'hang' ? null : 'plant', pelvis: [0, -.12 * vk, 0], spine: { to: [vx + dir.x, .9, vz + dir.z], k: .3 * vk },
      arm: grab ? { r: { at: base === 'eyes' ? pt(.12, -.05, hy) : wr, by: 'palm' }, l: { at: base === 'eyes' ? pt(.12, .05, hy) : pt(.12, .06, hy - .28), by: 'palm' } } : null, look: base === 'face' ? [mx, 1.7, mz] : null }, null],
    [K, mx, 0, mz, side ? a - PI * .5 : a, { clips: [['Idle_Loop', 0, 1]], legs: 'plant', spine: { to: bite, k: .75 }, arm, look: bite }, null]];
}
function gorePost(o, t, kind, P, dir, used) {
  const { V, K, base } = goreWho(kind), C = A[V], M = A[K]; if (!used.has(V) || !C.bone || !M.bone) return;
  const f = goreFx(o), B = bodyPts(C), lay = base === 'eat' || base === 'drag' || base === 'limb' || base === 'heart';
  const pc = lay ? B.belly : C.root.getWorldPosition(new T.Vector3()); f.pool.visible = true; f.pool.position.set(pc.x, .006, pc.z); f.pool.scale.setScalar(.15 + 1.5 * stg(t, .4, 3.2));
  const rip = stg(t, .45, 1.15); if (C.HU && C.HU.uGore) C.HU.uGore.value = Math.max(C.HU.uGore.value, 2 * rip);   // кровь заливает жертву: пятно от раны шире, подтёки длиннее (шейдер тела, uGore > 1)
  const RA = window.__W3chars && window.__W3chars.reachArm;   // рана раскрывается на 2-м этапе
  if (base === 'eat' || base === 'heart') {
    wound(C, base === 'heart' ? 'chest' : 'belly', rip); const W0 = base === 'heart' ? B.chest : B.belly, mo = mouthPt(M);
    // кисть: в живот → рывок вверх с кишками → ко рту (поверх позы 'eat')
    const up = W0.clone().addScaledVector(B.up, .05).add(new T.Vector3(0, .38, 0)), tgt = t < 1.15 ? W0.clone().add(new T.Vector3(0, .03 + .04 * Math.sin(t * 19), 0)) : t < 2.1 ? W0.clone().lerp(up, stg(t, 1.15, 1.8)) : up.clone().lerp(mo, stg(t, 2.1, 2.6));
    if (RA) { RA(M, 'r', { at: [tgt.x, tgt.y, tgt.z], by: 'palm' }); M.root.updateWorldMatrix(true, true); }
    const h = M.bone.hand_r.getWorldPosition(new T.Vector3()), out = t > 1.1;
    if (base === 'heart') { f.heart.visible = out; f.heart.position.copy(h).y += .03; f.heart.scale.setScalar(1 + .14 * Math.max(0, Math.sin(t * 7.5)) ** 4); }
    gutsDraw(o, out ? (base === 'heart' ? [[W0, h, .1, .011], [W0, h, .14, .009], [B.belly, B.belly.clone().addScaledVector(B.lr, .3).setY(.05), .02, .02]]
      : [[W0, h, .3, .02], [W0, h.clone().lerp(W0, .35).addScaledVector(B.lr, .12), .22, .018], ...(t > 2.3 ? [[h, mo, .08, .017]] : []), [W0, W0.clone().addScaledVector(B.lr, .3).setY(.05), .02, .022]]) : [], t);   // две петли провисают из живота к когтям
    sprayAt(f, W0, B.up, t, [.5, .9, 1.25, 2.0]); }
  else if (base === 'limb') { const W = n => C.bone[n].getWorldPosition(new T.Vector3()), th = W('thigh_l').lerp(W('calf_l'), .5), mo = mouthPt(M);   // бедро разорвано, голень вывернута назад
    wound(C, 'thigh', rip); const cb = C.bone.calf_l;
    if (cb) { const d = W('foot_l').sub(W('calf_l')), sd = Math.sign(new T.Vector3(0, 1, 0).cross(d).dot(B.lr)) || 1;   // голень вывернута вбок по полу (вывернутая назад уходила под пол на полметра)
      cb.updateWorldMatrix(true, false); const wq = cb.getWorldQuaternion(new T.Quaternion()).premultiply(new T.Quaternion().setFromAxisAngle(new T.Vector3(0, 1, 0), 1.35 * sd * stg(t, .8, 1.2)));
      cb.parent.updateWorldMatrix(true, false); cb.quaternion.copy(cb.parent.getWorldQuaternion(new T.Quaternion()).invert().multiply(wq)); cb.updateWorldMatrix(false, true); }
    gutsDraw(o, t > 1.0 ? [[th, mo, .04, .012], [th, th.clone().addScaledVector(B.lr, .25).setY(.04), .0, .014]] : [], t); sprayAt(f, th, B.up, t, [.55, 1.0, 1.8]); }
  else if (base === 'drag') { wound(C, 'belly', .9); const st = new T.Vector3(P.x + dir.x * .7, 0, P.z + dir.z * .7), ln = Math.max(.05, st.distanceTo(new T.Vector3(B.belly.x, 0, B.belly.z)));
    f.trail.visible = true; f.trail.position.set((st.x + B.belly.x) / 2, .005, (st.z + B.belly.z) / 2); f.trail.rotation.set(-PI / 2, 0, Math.atan2(dir.x, dir.z)); f.trail.scale.set(.32, ln, 1);
    gutsDraw(o, [[B.belly, B.belly.clone().addScaledVector(dir, .6).setY(.04), .0, .02], [B.belly, B.belly.clone().addScaledVector(dir, .35).addScaledVector(B.lr, .15).setY(.04), .0, .018]], t); }
  else if (base === 'chest') { wound(C, 'chest', rip); const h = M.bone.hand_r.getWorldPosition(new T.Vector3());
    gutsDraw(o, t > 1.2 ? [[B.chest, h, .05, .018], [B.belly, B.belly.clone().addScaledVector(B.fw, .22).setY(.04), .03, .022]] : [], t); sprayAt(f, B.chest, B.fw, t, [.6, 1.0, 1.5, 2.2]); }
  else if (base === 'neck') { const tw = stg(t, 1.2, 1.45); if (tw > 0) twistHead(C, B.up, 2.9 * tw); wound(C, 'throat', tw * .8); sprayAt(f, B.throat, B.fw, t, tw > 0 ? [1.3, 1.9] : []); }
  else if (base === 'hang') { wound(C, 'throat', rip); sprayAt(f, B.throat, B.fw, t, [.6, 1.1, 1.7, 2.4]); }
  else if (base === 'throat') { wound(C, 'throat', rip); sprayAt(f, B.throat, B.fw, t, [.55, .95, 1.4, 2.0, 2.6]); }   // артерия бьёт толчками
  else if (base === 'face') { wound(C, 'face', rip); sprayAt(f, B.face, B.fw, t, [.55, 1.1, 1.8]); }
  else if (base === 'eyes') { wound(C, 'eyes', rip); if (C.HU.uSocket) C.HU.uSocket.value = rip; sprayAt(f, B.face, B.fw, t, [.7, 1.4]); }
}
// ==== смерть как сцена, а не поза (владелец, 29 окт: «в смертях больше именно анимации, а не непонятно что», «на профессиональном уровне») ====
// У вида свой сценарий по секундам (GTL): предвестие → удар (касание ровно на кадре звука) → борьба → смерть → падение → оно над телом.
// Двое — лицом друг к другу поперёк кадра, камера сбоку (так снимают драку в кино: оба силуэта и обе руки читаются), крупно — в миг смерти,
// потом тело на полу. Руки идут по ключам дугами (keyAt), касания — по костям другого уже после поз (gPost), падение — клип Death01
// с самого начала: в миг смерти колени подгибаются сами, через 0,8 с валится на спину — от того, кто убил). hit — касание (звук ровно здесь), kill — смерть,
// drop — оно отпускает (тело уже оседает, на полу — через ~1,3 с после смерти);
// kp — что крупно: [от жертвы к нему, высота]; cam — склейки [секунда, 'w' общий | 'c' крупно | 'f' падает | 'e' конец: тело на переднем плане, оно сидит
// над ним (склейка прячет, как оно туда перебралось)]; sfx — [звук, секунда, громкость, {rate}]
const GTL = {
  neck: { d: 5.9, hit: .55, kill: 1.6, drop: 2.3, kp: [.3, 1.8], cam: [[0, 'w'], [1.25, 'c'], [2.3, 'f'], [3.35, 'e']],
    sfx: [['breath_close', .05, .75], ['skin_stretch', .55, .9], ['breath_close', .95, .55, { rate: 1.25 }], ['neck_crack', 1.66, 1], ['neck_crack', 1.72, .75, { rate: .8 }], ['boom', 2.9, .5, { rate: .55 }]] },
};
GTL.hang = { d: 6.2, hit: .55, kill: 2.0, drop: 2.6, lift: .42, kp: [.2, 1.95], cam: [[0, 'w'], [1.45, 'c'], [2.6, 'f'], [3.6, 'e']],   // поднял за горло: душит на весу, ноги бьются; хруст — обмяк; бросил
  sfx: [['breath_close', .05, .75], ['skin_stretch', .55, .9], ['breath_close', .95, .6, { rate: 1.45 }], ['breath_close', 1.45, .5, { rate: 1.7 }], ['neck_crack', 2.02, 1], ['neck_crack', 2.08, .7, { rate: .85 }], ['boom', 2.84, .75, { rate: .6 }]] };
GTL.chest = { d: 5.9, hit: .55, kill: 1.35, drop: 2.05, kp: [.16, 1.32], cam: [[0, 'w'], [1.1, 'c'], [2.05, 'f'], [3.3, 'e']], splat: 1.38,   // левой — за плечо, правая — замах назад и насквозь; рывок обратно
  sfx: [['breath_close', .05, .75], ['skin_stretch', .55, .8], ['boom', 1.35, .7], ['skin_stretch', 1.35, 1], ['eat_wet', 1.4, 1], ['eat_wet', 1.95, .85, { rate: .85 }], ['boom', 2.95, .5, { rate: .55 }]] };
const gtl = kind => GTL[goreWho(kind).base] || null;
// где Итан, когда проиграл (по кадру-основе финала, engine.js GORE_BASE): seat — за пультом в будке; остальное — стоя (gStage)
const GPLACE = { hush_face: 'seat', k5_drag: 'hide' };
// ключи [[секунда, значение, ход], …] → значение в секунду t; ход участка: плавно (по умолчанию), 'in' — разгон (бросок), 'out' — торможение, 'lin' — ровно
function keyAt(t, K) {
  const n = K.length; if (t <= K[0][0]) return K[0][1]; if (t >= K[n - 1][0]) return K[n - 1][1];
  let i = 0; while (t > K[i + 1][0]) i++;
  const [t0, a] = K[i], [t1, b, e] = K[i + 1]; let u = (t - t0) / (t1 - t0 || 1);
  u = e === 'in' ? u * u * u : e === 'out' ? 1 - (1 - u) ** 3 : e === 'lin' ? u : u * u * (3 - 2 * u);
  return typeof a === 'number' ? a + (b - a) * u : a.map((v, j) => v + (b[j] - v) * u);
}
// где кто: жертва и оно лицом друг к другу поперёк кадра (m = 1 — оно справа); тело падает на спину — от него, в сторону −ax
function gStage(P, dir, m, K) {
  const ax = new T.Vector3(dir.z * m, 0, -dir.x * m), D = K === 'monster' ? .36 : .27, V = P.clone().addScaledVector(ax, -.3), torso = V.clone().addScaledVector(ax, -.85);
  return { P, dir, ax, m, V, M: P.clone().addScaledVector(ax, D), ryV: Math.atan2(ax.x, ax.z), ryM: Math.atan2(-ax.x, -ax.z), body: V.clone().addScaledVector(ax, -.5), torso,
    E: torso.clone().addScaledVector(dir, -.52).addScaledVector(ax, .12), ryE: Math.atan2(dir.x, dir.z) };   // E — где оно сидит над телом в конце (за телом, лицом к камере)
}
// свободное место под сцену: двое, место падения (тело ляжет на 1,5 м за жертвой) и три камеры (общий, крупно, тело на полу) — не в предметах и видят место
function gFit(base, kind, P0, dir0) {
  const key = 'g|' + base.loc + '|' + base.pos.join(',') + '|' + kind; if (key in GFIT) return GFIT[key];
  const { dF, sight } = gSolid(base.loc), D = goreWho(kind).K === 'monster' ? .36 : .27;
  let room = true;
  const ok = (px, pz, dx, dz, m) => { const ax = [dz * m, -dx * m], at = (f, a) => [px + dx * f + ax[0] * a, pz + dz * f + ax[1] * a];
    for (const [x, z, r, low] of [[...at(0, -.3), .3, 0], [...at(0, D), .36, 0], [...at(.05, -.8), .32, 1], [...at(.05, -1.25), .5, 1], [...at(.05, -1.6), .45, 1], [...at(-.52, -1.03), .38, 0]]) if (dF(x, z, low) < r) return false;   // у изголовья шире: руки раскинуты на ±0,45 м (уходили в стену)
    for (const [f, a] of [[3.0, .25], [1.45, .1], [3.1, -.25], [2.0, -1.15]]) { const [x, z] = at(f, a); if (dF(x, z) < .3 || !sight(x, z, px, pz)) return false; }
    return !room || sight(base.pos[0], base.pos[2], px, pz); };
  for (const pass of [0, 1]) { room = !pass;
    for (const da of [0, .3, -.3, .6, -.6, .95, -.95, 1.3, -1.3, 1.75, -1.75, PI]) { const c = Math.cos(da), sn = Math.sin(da), dx = dir0.x * c + dir0.z * sn, dz = -dir0.x * sn + dir0.z * c;
      for (const r of [0, .2, .4, .6, .85, 1.1, 1.4, 1.8, 2.2, 2.7]) for (let j = 0, n = r ? 16 : 1; j < n; j++) { const px = P0.x + Math.cos(j / n * PI * 2) * r, pz = P0.z + Math.sin(j / n * PI * 2) * r;
        for (const m of [1, -1]) if (ok(px, pz, dx, dz, m)) return (GFIT[key] = { P: new T.Vector3(px, 0, pz), dir: new T.Vector3(dx, 0, dz), m }); } } }
  return (GFIT[key] = null);
}
// камера по сценарию: общий план двоих сбоку (чуть со стороны его — видно лицо жертвы), крупно — головы в миг смерти, потом тело на полу.
// В каждом плане — медленный наезд и чуть «с рук»; склейка — в начале действия (на броске, на рывке), а не посреди него
function gCam(sh, S, TL, t) {
  const c = TL.cam.reduce((a, k) => t >= k[0] ? k : a, TL.cam[0]), lt = t - c[0], d = S.dir, ax = S.ax;
  let pos, look, fov;
  if (c[1] === 'c') { const kp = S.V.clone().addScaledVector(ax, TL.kp[0]); kp.y = TL.kp[1];
    pos = kp.clone().addScaledVector(d, 1.45).addScaledVector(ax, .1); pos.y += .04; look = kp; fov = 40; }
  else if (c[1] === 'f') { const C0 = S.P.clone().addScaledVector(ax, -.45); pos = C0.clone().addScaledVector(d, 3.1).addScaledVector(ax, .2); pos.y = 1.3; look = C0; look.y = .9; fov = 44; }
  else if (c[1] === 'e') { const B = S.torso; pos = B.clone().addScaledVector(d, 2.0).addScaledVector(ax, -.15); pos.y = 1.35; look = B.clone().addScaledVector(d, -.22); look.y = .5; fov = 46; }
  else { pos = S.P.clone().addScaledVector(d, 3.0).addScaledVector(ax, .25); pos.y = 1.42; look = S.P.clone().addScaledVector(ax, -.04); look.y = 1.18; fov = 42; }
  const dl = pos.distanceTo(look); pos.lerp(look, Math.min(.22, lt * .08) / dl);   // медленный наезд
  pos.x += Math.sin(t * 1.3) * .006; pos.y += Math.sin(t * 1.9 + 1) * .005; pos.z += Math.cos(t * 1.1) * .006;   // с рук: чуть дышит
  sh.pos = [pos.x, pos.y, pos.z]; sh.look = [look.x, look.y, look.z]; sh.fov = fov;
}
// свет над местом: свой «лицевой» свет локации (как в старом финале), моргает
function gLight(o, t, S) {
  if (!o.faceL) return; const fl = Math.sin(t * 23) > -.7 ? 1 : .35;
  o.faceL.color.setHex(0xe6ecff); o.faceL.position.set(S.P.x + S.dir.x * .8, 2.35, S.P.z + S.dir.z * .8); o.faceL.distance = 6.5; o.faceL.intensity = (CINE ? 3.2 : 2.4) * fl;
}
// жертва стоя: отпрянула (руки вперёд — защититься) → удар (вздрогнула) → борется, держит его за руки → смерть (обмякла, колени подгибаются) → падает на спину
function gVic(S, TL, t, C, base) {
  const hang = base === 'hang', dk = hang ? TL.drop - .45 : base === 'chest' ? TL.kill + .6 : TL.kill + .08;   // на весу — падает, когда бросили (клип с 0,45 — колени уже согнуты); насквозь — когда выдернул руку
  const R = locR(S.V.x, S.V.z, S.ryV), fr = stg(t, dk, dk + .2), dc = Math.max(0, t - dk) * .82;   // Death01 с начала: оседает → падает (0,6–1,2 с клипа) → лежит
  const rec = seg(t, .02, .3) * (1 - seg(t, TL.hit - .05, TL.hit + .2)), hw = t > TL.hit ? Math.exp(-(t - TL.hit) * 6) * Math.min(1, (t - TL.hit) * 30) : 0;
  const sg = stg(t, TL.hit + .04, TL.hit + .22) * (1 - stg(t, TL.kill, TL.kill + .12)), limp = stg(t, TL.kill, TL.kill + .35);
  const P = { clips: [['Idle_Loop', 1.4 + t * .6, 1 - fr], ['Death01', dc, fr]], look: limp < .3 ? [S.M.x, 2.05, S.M.z] : null, fing: { l: ['grip', sg], r: ['grip', sg] }, heels: 0, toes: 1 };
  const up = hang ? gLiftY(TL, t) : 0;
  if (hang && t > TL.hit + .1) { P.wig = t < TL.kill ? [['thigh_l', [1, 0, 0], .45 * Math.sin(t * 12)], ['thigh_r', [1, 0, 0], .45 * Math.sin(t * 10 + 2)], ['calf_l', [1, 0, 0], .5 * Math.abs(Math.sin(t * 14 + 1))], ['calf_r', [1, 0, 0], .5 * Math.abs(Math.sin(t * 11 + 3))]] : null; }   // ноги бьются в воздухе, потом висят
  if (fr < 1 && !(hang && up > .02)) {
    P.legs = 'plant';
    P.pelvis = R.v(0, -.035 * rec - .045 * sg, -.06 * rec - .05 * hw);
    const lb = R.p(0, 2.3, -1.4), fw = R.p(0, 1.75, 1.2), w = rec + sg + 1e-3;   // назад-вверх (отпрянул), к нему (тянет)
    P.spine = { to: [0, 1, 2].map(i => (lb[i] * rec + fw[i] * sg) / w), k: .14 * rec + .18 * sg + .2 * hw };
    P.twist = .24 * Math.sin((t - TL.hit) * 9.5) * sg;   // вырывается
    const gu = seg(t, .05, .28) * (1 - stg(t, TL.hit, TL.hit + .12));   // руки вперёд, ладонями к нему
    if (gu > 0) P.arm = { l: { at: R.p(.2, 1.38, .34), by: 'palm', w: gu }, r: { at: R.p(-.2, 1.42, .36), by: 'palm', w: gu } };
  } else if (fr >= 1) P.wig = dc > 1.3 ? [['hand_r', [0, 0, 1], .5 * Math.max(0, Math.sin(t * 13)) ** 8 * (dc < 2.9 ? 1 : 0)], ['calf_l', [1, 0, 0], .25 * Math.max(0, Math.sin(t * 7 + 1)) ** 10 * (dc < 2.5 ? 1 : 0)]] : null;   // судороги на полу
  C.faceSets.g_v = limp > .5 ? { dead: 1, mouth: .45 } : { mouth: .25 + .6 * sg + .2 * rec, wide: 1.35, browUp: 1, browIn: 1, tense: .5, scream: .55 * sg }; C.faceSet = 'g_v';
  return P;
}
const gLiftY = (TL, t) => TL.lift ? TL.lift * seg(t, TL.hit + .1, TL.hit + .65) * (1 - Math.pow(stg(t, TL.drop, TL.drop + .22), 2)) : 0;   // на весу: поднял за 0,55 с, бросил — падает за 0,22 с
// оно стоя: замирает и вырастает (предвестие) → бросок (шаг к нему, корпус вперёд, руки — к голове) → держит → отпускает → идёт за телом и садится над ним
function gMon(S, TL, t, C) {
  const te = (TL.cam.find(k => k[1] === 'e') || [99])[0], B = S.torso;
  if (t >= te) {   // конец: сидит за телом и склонился над ним; потом медленно поднимает голову — к нам
    const up = seg(t, TL.d - 1.4, TL.d - .7), R = locR(S.E.x, S.E.z, S.ryE);
    C.faceSet = 'grin';
    return [S.E, S.ryE, { clips: [['Crouch_Idle_Loop', 1.0, 1]], legs: 'plant', pelvis: R.v(0, -.04, .05), spine: { to: [B.x, .1, B.z], k: .55 * (1 - up) + .1 }, look: up > .5 ? null : [B.x, .25, B.z] }];
  }
  const at = seg(t, 0, TL.hit - .22), sk = stg(t, TL.hit - .2, TL.hit), fo = seg(t, TL.drop, TL.drop + .6);   // fo — провожает тело вниз: колени и корпус
  const step = keyAt(t, [[TL.hit - .2, 0], [TL.hit, .12, 'in'], [TL.drop, .12], [TL.drop + .6, .2]]), Mx = S.M.clone().addScaledVector(S.ax, -step), R = locR(Mx.x, Mx.z, S.ryM);
  const P = { clips: [['Idle_Loop', 1.7, 1]], legs: 'plant',
    pelvis: R.v(0, .035 * at * (1 - sk) - .1 * sk - .08 * fo, -.04 * fo),
    spine: { to: t > TL.drop ? [S.body.x, .3, S.body.z] : [S.V.x, 1.55, S.V.z], k: .12 * at + .42 * sk * (1 - .5 * fo) + .2 * fo },
    look: t > TL.drop + .15 ? [S.body.x, .3, S.body.z] : [S.V.x, 1.62, S.V.z] };
  C.faceSet = t > TL.hit - .3 && t < TL.kill + .25 ? 'scream' : 'grin';
  return [Mx, S.ryM, P];
}
function gSet(o, t, kind, S, TL, sh) {
  const { V, K } = goreWho(kind), C = A[V], M = A[K];
  M.gore = 1; gLight(o, t, S); gCam(sh, S, TL, t);
  const ci = TL.cam.reduce((a, k, i) => t >= k[0] ? i : a, 0);   // склейка внутри кадра — позы сразу новые (и у покадрового Жильца), как на настоящей склейке
  if (S.ci !== ci) { if (S.ci != null && t > .01) for (const X of [C, M]) { X.smDone = false; X.cut = true; } S.ci = ci; }
  const base = goreWho(kind).base, vp = gVic(S, TL, t, C, base), [mp, mry, mpose] = gMon(S, TL, t, M);
  return [[V, S.V.x, gLiftY(TL, t), S.V.z, S.ryV, vp, null], [K, mp.x, 0, mp.z, mry, mpose, t > TL.d - 1.05 ? 'cam' : null]];
}
// лежащее тело не уходит под пол: суставы не ниже своей «толщины» над полом (клип Death01 кладёт спину и затылок на 11 см под пол)
const G_LIFT = [['pelvis', .11], ['spine_02', .12], ['spine_03', .12], ['neck_01', .08], ['Head', .1], ['upperarm_l', .07], ['upperarm_r', .07], ['thigh_l', .08], ['thigh_r', .08], ['calf_l', .06], ['calf_r', .06]];
function gLift(C) {
  C.root.updateWorldMatrix(true, true); let d = 0;
  for (const [bn, r] of G_LIFT) { const b = C.bone[bn]; if (b) d = Math.max(d, r * (C.ss || 1) - b.getWorldPosition(_gv).y); }
  if (d > 0) { C.root.position.y += d; C.root.updateMatrixWorld(true); }
}
// касания после поз: руки Жильца — к голове жертвы (предвестие → бросок дугой → держит → отпускает и провожает тело вниз), руки жертвы — на его предплечьях
function gPost(o, t, kind, S, TL, used) {
  const { V, K, base } = goreWho(kind), C = A[V], M = A[K]; if (!used.has(V) || !used.has(K) || !C.bone || !M.bone) return;
  const W3 = window.__W3chars, RA = W3 && W3.reachArm; if (!RA) return;
  if (t > TL.kill + .9) { if (W3.layFloor) W3.layFloor(C); gLift(C); }   // упал (Death01 ≥ 0,65 с)
  C.root.updateMatrixWorld(true);
  const B = bodyPts(C), H = C.bone.Head.getWorldPosition(new T.Vector3()), N = C.bone.neck_01.getWorldPosition(new T.Vector3());
  const cl = H.clone().addScaledVector(B.lr, .095).addScaledVector(B.up, .02), cr = H.clone().addScaledVector(B.lr, -.095).addScaledVector(B.up, .02);   // виски жертвы: левый, правый
  if (base === 'neck') { const ang = -S.m * 2.75 * seg(t, TL.kill - .04, TL.kill + .14);   // рывок 0,18 с — лицом через нас (в кадре видно, как голова уходит за предел); руки уходят за ней наполовину (соскальзывают)
    if (ang) { twistHead(C, B.up, ang); const q = new T.Quaternion().setFromAxisAngle(B.up, ang * .45); for (const c of [cl, cr]) c.sub(N).applyQuaternion(q).add(N); } }   // было ang > 0 — при m = 1 рывка не было
  const R = locR(M.root.position.x, M.root.position.z, S.ryM), sk = stg(t, TL.hit - .2, TL.hit), f = goreFx(o), V3a = v => [v.x, v.y, v.z];
  const path = (a, c) => { if (t >= TL.hit) return [c.x, c.y, c.z]; const k = sk * sk, p = lin(a, [c.x, c.y, c.z], k); p[1] += Math.sin(PI * k) * .12; return p; };   // дугой сверху
  const wM = t >= (TL.cam.find(k => k[1] === 'e') || [99])[0] ? 0 : keyAt(t, [[0, .2], [.3, .85], [TL.hit, 1], [TL.drop, 1], [TL.drop + .3, .5, 'out'], [TL.drop + .65, 0]]);
  const sg = stg(t, TL.hit + .04, TL.hit + .22) * (1 - stg(t, TL.kill, TL.kill + .12));
  const fa = sd => { M.root.updateMatrixWorld(true); return M.bone['lowerarm_' + sd].getWorldPosition(new T.Vector3()).lerp(M.bone['hand_' + sd].getWorldPosition(new T.Vector3()), .55); };
  if (base === 'hang') {   // правой — за горло (рука идёт за горлом вверх), левая поднята, когти растопырены; жертва обеими руками — за его запястье
    const th = B.throat.clone().addScaledVector(B.fw, .03); RA(M, 'r', { at: path(R.p(-.36, 1.9, .15), th), by: 'palm', w: wM, pole: [-1, -.6, -.2] });
    RA(M, 'l', { at: R.p(.46, 1.85 + .1 * stg(t, TL.hit, TL.hit + .4), .32), by: 'palm', w: .85 * wM, pole: [1, -.5, -.3] });
    wound(C, 'throat', .55 * stg(t, TL.kill, TL.kill + .15));
    const w2 = Math.max(sg, stg(t, TL.hit + .04, TL.hit + .22) * (1 - stg(t, TL.kill + .1, TL.kill + .35))); if (w2 > 0) { const r = fa('r'); RA(C, 'l', { at: V3a(r), by: 'palm', w: w2 }); RA(C, 'r', { at: V3a(r.clone().addScaledVector(B.up, -.05)), by: 'palm', w: w2 }); }
    return; }
  if (base === 'chest') {   // левой — за его правое плечо; правая: замах назад → насквозь (кисть выходит из спины) → держит → рывок обратно
    RA(M, 'l', { at: path(R.p(.28, 1.95, .22), N.clone().addScaledVector(B.lr, -.17).addScaledVector(B.up, -.08)), by: 'palm', w: wM, pole: [1, -.5, -.3] });   // замах уже: в узком подвале рука уходила в стену
    const th = keyAt(t, [[TL.hit, .45], [TL.kill - .18, .5], [TL.kill, -.16, 'in'], [TL.kill + .45, -.16], [TL.kill + .62, .5, 'out']]);
    const cock = t < TL.kill - .18 ? 1 - stg(t, TL.kill - .5, TL.kill - .18) : 0, hp = B.chest.clone().addScaledVector(B.fw, th);   // замах: рука уходит назад за плечо
    RA(M, 'r', { at: cock > 0 ? lin(R.p(-.38, 1.65, -.28), V3a(hp), 1 - cock) : V3a(hp), by: 'palm', w: Math.max(wM, t > TL.kill - .2 && t < TL.kill + .7 ? 1 : 0), pole: [-1, -.4, -.4] });
    wound(C, 'chest', stg(t, TL.kill - .02, TL.kill + .15)); sprayAt(f, B.chest, B.fw, t, [TL.kill + .02, TL.kill + .3, TL.kill + .62]);
    if (t > TL.kill + .5 && t < TL.kill + 1.2) { const h = M.bone.hand_r.getWorldPosition(new T.Vector3()); gutsDraw(o, [[B.chest, h, .04, .014]], t); } else gutsDraw(o, [], t);
    if (sg > 0) { const r = fa('r'), l = fa('l'); RA(C, 'l', { at: V3a(r), by: 'palm', w: sg }); RA(C, 'r', { at: V3a(l), by: 'palm', w: sg }); }
    const cw = stg(t, TL.kill + .05, TL.kill + .2) * (1 - stg(t, TL.kill + .45, TL.kill + .7)); if (cw > 0) { const r = fa('r'); RA(C, 'l', { at: V3a(r), by: 'palm', w: cw }); RA(C, 'r', { at: V3a(r.clone().addScaledVector(B.lr, -.06)), by: 'palm', w: cw }); }   // хватается за руку в груди
    return; }
  RA(M, 'l', { at: path(R.p(.42, 2.2, .12), cr), by: 'palm', w: wM, pole: [1, -.5, -.3] });   // его левая — к правому виску жертвы
  RA(M, 'r', { at: path(R.p(-.42, 2.2, .12), cl), by: 'palm', w: wM, pole: [-1, -.5, -.3] });
  if (sg > 0) { const l = fa('r'), r = fa('l'); RA(C, 'l', { at: V3a(l), by: 'palm', w: sg }); RA(C, 'r', { at: V3a(r), by: 'palm', w: sg }); }
}
// ---- в будке (владелец: «смерти должны быть логичны, смотря где проиграл»; «главное, чтоб был эффект — неприятно и страшно»): Итан за пультом — там его
// и убивают. Оно за левым плечом, где стояло в «тсс». Сначала тихо: дыхание у уха, длинные пальцы ползут по его лицу, он замер и часто дышит (touch);
// потом хватка (hit) — Итан бьётся на стуле, держит его за руки, ноги бьют под столом; смерть (kill) — оно держит ещё полсекунды; отпускает (rel) —
// тело валится лицом на пульт, пальцы ещё дёргаются. Конец — с камеры будки над дверью (CAM 00): оно выпрямляется над телом и медленно поворачивает
// голову к объективу — знает, что смотрят. Спереди — с места мониторов, как в «тсс»: его лицо у самой щеки Итана
const SEAT_MS = [5.72, -.93], SEAT_M = () => [HUSH_M[0] + .15, HUSH_M[1] + .06];   // «Лили» и двойник ростом с человека — ближе к спинке, чем Жилец; Жилец — на 8 см от спинки стула и стены (наклоняясь, задевал обе); функция — HUSH_M объявлен ниже
// чем держит и что делает в миг смерти: head — за виски, eyes — большими пальцами в глаза, throat — лоб назад и в горло, chest — за плечо и рукой сквозь спину, belly — когтями в живот
const GSK = { neck: ['head', 'twist'], face: ['head', 'bite'], eyes: ['eyes', 'gouge'], throat: ['throat', 'bite'], chest: ['chest', 'punch'], heart: ['chest', 'heart'], eat: ['belly', 'rip'] };
// touch — пальцы на лице, hit — хватка (звук ровно здесь), kill — смерть, rel — отпускает и тело валится на пульт; cam: w — спереди, c — крупно, e — камера будки
const GTS = {
  neck: { d: 5.8, touch: .35, hit: .9, kill: 1.75, rel: 2.35, cam: [[0, 'w'], [1.45, 'c'], [2.35, 'w'], [3.1, 'e']], kp: [5.24, 1.36, -.6],
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .9], ['breath_close', 1.2, .55, { rate: 1.35 }], ['neck_crack', 1.8, 1], ['neck_crack', 1.86, .8, { rate: .8 }], ['boom', 2.75, .45, { rate: .7 }]] },
  face: { d: 5.9, touch: .35, hit: .9, kill: 1.6, rel: 2.35, cam: [[0, 'w'], [1.3, 'c'], [2.35, 'w'], [3.1, 'e']], kp: [5.26, 1.38, -.62], splat: 1.66,
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .9], ['breath_close', 1.25, .6, { rate: .8 }], ['eat_wet', 1.62, 1], ['skin_stretch', 1.7, 1, { rate: .75 }], ['eat_wet', 2.0, .75, { rate: 1.1 }], ['eat_wet', 4.1, .5, { rate: 1.2 }], ['boom', 2.75, .45, { rate: .7 }]] },
  eyes: { d: 5.8, touch: .35, hit: .9, kill: 1.55, rel: 2.4, cam: [[0, 'w'], [1.25, 'c'], [2.4, 'w'], [3.1, 'e']], kp: [5.22, 1.4, -.56], splat: 1.62,
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .8], ['eat_wet', 1.57, .9, { rate: 1.3 }], ['skin_stretch', 1.6, .9, { rate: 1.2 }], ['eat_wet', 1.95, .6, { rate: 1.5 }], ['boom', 2.8, .45, { rate: .7 }]] },
  throat: { d: 5.9, touch: .35, hit: .9, kill: 1.6, rel: 2.5, cam: [[0, 'w'], [1.3, 'c'], [2.5, 'w'], [3.2, 'e']], kp: [5.22, 1.25, -.52], splat: 1.68,
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .9], ['eat_wet', 1.62, 1], ['skin_stretch', 1.66, 1, { rate: .7 }], ['eat_wet', 2.05, .8, { rate: .8 }], ['eat_wet', 2.45, .6, { rate: .9 }], ['boom', 2.9, .45, { rate: .7 }]] },
  chest: { d: 5.8, touch: .35, hit: .9, kill: 1.55, rel: 2.35, cam: [[0, 'w'], [1.25, 'c'], [2.35, 'w'], [3.1, 'e']], kp: [5.22, 1.2, -.5], kf: 22, splat: 1.6,
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .8], ['boom', 1.55, .7], ['skin_stretch', 1.55, 1], ['eat_wet', 1.6, 1], ['eat_wet', 2.1, .8, { rate: .85 }], ['boom', 2.75, .45, { rate: .7 }]] },
  heart: { d: 6.1, touch: .35, hit: .9, kill: 1.55, rel: 2.5, cam: [[0, 'w'], [1.25, 'c'], [2.5, 'w'], [3.2, 'e']], kp: [5.22, 1.2, -.5], kf: 22, splat: 1.6,
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .8], ['boom', 1.55, .7], ['skin_stretch', 1.55, 1], ['eat_wet', 1.6, 1], ['heart', 1.85, .8], ['skin_stretch', 2.15, .9, { rate: .8 }], ['eat_wet', 4.6, .8]] },
  eat: { d: 6.1, touch: .35, hit: .9, kill: 1.55, rel: 2.7, cam: [[0, 'w'], [1.25, 'c'], [2.7, 'w'], [3.4, 'e']], kp: [5.22, 1.08, -.5], kf: 24, kc: [4.92, 1.5, -.08], splat: null,
    sfx: [['breath_close', .05, .9], ['skin_stretch', .4, .35, { rate: 1.4 }], ['skin_stretch', .9, .8], ['skin_stretch', 1.55, 1], ['eat_wet', 1.6, 1], ['skin_stretch', 1.95, .9, { rate: .85 }], ['eat_wet', 2.05, 1, { rate: .9 }], ['eat_wet', 4.3, .8], ['eat_wet', 4.9, .7, { rate: 1.1 }]] },
};
function seatCam(sh, TL, t) {
  const c = TL.cam.reduce((a, k) => t >= k[0] ? k : a, TL.cam[0]), lt = t - c[0];
  let pos, look, fov;
  if (c[1] === 'e') { pos = [6.42, 2.32, .32]; look = [5.25, 1.05, -.66]; fov = 54; }
  else if (c[1] === 'c') { pos = TL.kc || [4.66, 1.31, .04]; look = TL.kp; fov = TL.kf || 27; }   // kc — живот: камера выше, над столом (иначе крупно — стол и колени)
  else { pos = [4.66, 1.31, .04]; look = [5.3, 1.4, -.66]; fov = 38; }
  const k = Math.min(.14, lt * .05), d = [look[0] - pos[0], look[1] - pos[1], look[2] - pos[2]], L = Math.hypot(...d) || 1;   // медленный наезд
  sh.pos = [pos[0] + d[0] / L * k + Math.sin(t * 1.3) * .004, pos[1] + d[1] / L * k + Math.sin(t * 1.9 + 1) * .003, pos[2] + d[2] / L * k]; sh.look = look; sh.fov = fov;
}
function seatLight(o, t, TL) {
  hushSet(o); if (o.ch2) o.ch2.position.set(6.78, 0, -.98);   // стул придвинут к пульту, холодный свет мониторов в лицо — как в «тсс»; второй стул отлетел в угол (оно стояло между стульями и задевало его)
  const fl = t > TL.kill && t < TL.kill + .6 && Math.sin(t * 40) > .2 ? .35 : 1, dim = 1 - .45 * seg(t, TL.rel, TL.rel + 1);   // лампа захлёбывается на смерти, потом тусклее
  o.lamp.intensity = .7 * fl * dim; o.boothLight.intensity = .22 * fl * dim; o.amb.intensity = .13;
}
// жертва на стуле: замер у монитора, часто дышит (пальцы ползут по лицу) → рывок, когда схватили → бьётся (выгибается назад, ноги под столом) → смерть →
// обмякает и валится лицом на пульт; пальцы правой ещё дёргаются
function seatVic(TL, t, C, act) {
  const fz = stg(t, TL.touch, TL.touch + .15) * (1 - stg(t, TL.hit, TL.hit + .1));   // замер: не шевелится, глаза в сторону его руки
  const sg = stg(t, TL.hit + .03, TL.hit + .2) * (1 - stg(t, TL.kill, TL.kill + .12)), jolt = t > TL.hit ? Math.exp(-(t - TL.hit) * 7) * Math.min(1, (t - TL.hit) * 30) : 0;
  const cv = stg(t, TL.kill, TL.kill + .1) * (1 - seg(t, TL.rel - .15, TL.rel + .3)), sl = seg(t, TL.rel, TL.rel + .55), back = act === 'bite' || act === 'gouge' ? stg(t, TL.hit, TL.hit + .3) * (1 - sl) : 0;
  const P = { clips: [['Sitting_Idle_Loop', 2, 1]], heels: 0, toes: 1, br: fz > 0 ? [1.25, 2.1] : undefined, look: sl > .2 || t > TL.kill + .15 ? null : t < TL.touch ? [4.62, 1.22, .05] : t < TL.hit ? [5.55, 1.3, -.4] : [5.62, 1.95, -.95],
    fing: { l: ['grip', Math.max(sg, .6 * fz)], r: ['grip', Math.max(sg, .6 * fz)] } };
  const to = sl > 0 ? [5.18, .8, -.08] : act === 'punch' || act === 'heart' || act === 'rip' ? [5.2, 2.4, .15] : [5.2, 2.6, -1.0];   // вперёд на столешницу (голова ложится на пульт) / удар сзади — бросает вперёд / его тянут за голову — вверх и чуть назад (круче назад — спиной в спинку стула)
  P.spine = { to, k: sl > 0 ? .9 * sl : .1 * sg + .14 * jolt + .1 * back };
  P.twist = .18 * Math.sin((t - TL.hit) * 10) * sg;
  const kick = sg + .55 * cv;   // ноги бьются под столом — и после смерти ещё секунду (судороги)
  const up = x => .5 + .5 * x;   // бьёт ногами вверх-вперёд под столом (вниз — в пол)
  P.wig = kick > 0 ? [['thigh_l', [1, 0, 0], -.32 * up(Math.sin(t * 13)) * kick], ['thigh_r', [1, 0, 0], -.32 * up(Math.sin(t * 11 + 2)) * kick], ['calf_l', [1, 0, 0], .28 * Math.sin(t * 15 + 1) * kick], ['calf_r', [1, 0, 0], .28 * Math.sin(t * 12 + 3) * kick],
    ['hand_l', [0, 0, 1], .4 * Math.sin(t * 17) * cv], ['hand_r', [0, 0, 1], .4 * Math.sin(t * 19 + 1) * cv]]
    : sl > .9 ? [['hand_r', [0, 0, 1], .55 * Math.max(0, Math.sin(t * 13)) ** 8 * (t < TL.d - .9 ? 1 : 0)], ['index_01_r', [1, 0, 0], .5 * Math.max(0, Math.sin(t * 9 + 2)) ** 6]] : null;
  if (sl > 0) P.arm = { l: { at: [5.47, .87, -.3], by: 'palm', fdir: nrm([.15, -.05, 1]), pdir: [0, -1, 0], w: .9 * sl }, r: { at: [4.86, .5, -.68], by: 'palm', w: .9 * sl } };   // левая — на пульт (ладонь на столешнице, а не в ней), правая висит сбоку от сиденья (висела сквозь него)
  C.faceSets.g_v = t > TL.kill + .15 ? { dead: 1, mouth: .45 + .25 * cv, wide: .9 } : fz > .3 ? { mouth: .18, wide: 1.5, browUp: 1, browIn: 1, tense: .8 } : { mouth: .25 + .6 * sg, wide: 1.4, browUp: 1, browIn: 1, tense: .55, scream: .6 * sg + .3 * jolt }; C.faceSet = 'g_v';
  return P;
}
// оно за левым плечом: склоняется к самому уху, лицом у его щеки (как в «тсс») → хватка → для укуса — голова вниз, к лицу или горлу, и рывок назад → отпускает →
// конец: выпрямляется над телом во весь рост и на камере будки медленно поворачивает голову к объективу
function seatMon(TL, t, C, K, act, te) {
  const MP = K === 'monster' ? SEAT_M() : SEAT_MS, big = K === 'monster' ? 1 : .55;
  const bite = act === 'bite' ? keyAt(t, [[TL.kill - .14, 0], [TL.kill, 1, 'in'], [TL.kill + .12, 1], [TL.kill + .34, .3, 'out'], [TL.rel, .2]]) : 0;
  const up = seg(t, te - .05, te + .3);   // на склейке на камеру будки уже выпрямился во весь рост — тело на пульте видно
  const lean = (keyAt(t, [[0, .45], [TL.touch, .8], [TL.hit, 1], [TL.rel, 1], [TL.rel + .5, .55]]) + .35 * bite) * (1 - .85 * up);
  const bp = C.gThroat ? [5.18, 1.16, -.46] : [5.2, 1.35, -.5];   // куда кусает: горло или лицо
  const P = { clips: [['Idle_Loop', 1.7, 1]], legs: 'plant', pelvis: [.04 * lean * big, -.22 * lean * big, .06 * lean * big],
    spine: { to: bite > .05 ? bp : [5.46, .55 + .3 * (1 - lean), -.58], k: (.38 + .3 * lean) * (K === 'monster' ? 1 : .7) },
    look: t >= te + .9 ? null : bite > .05 ? bp : t > TL.rel ? [5.2, .9, -.2] : [5.2, 1.3, -.5] };
  C.faceSet = t > TL.hit - .1 && t < TL.kill + .35 ? 'scream' : 'grin';
  C.gore = act === 'bite' ? .28 * stg(t, TL.kill, TL.kill + .15) : 0;   // кровь у рта — только после укуса и немного (раньше лицо было красной маской с начала)
  return [MP, P];
}
function seatSet(o, t, kind, TL, sh) {
  const { V, K, base } = goreWho(kind), C = A[V], M = A[K], [, act] = GSK[base], te = (TL.cam.find(k => k[1] === 'e') || [99])[0];
  M.gThroat = base === 'throat'; seatLight(o, t, TL); seatCam(sh, TL, t);
  const ci = TL.cam.reduce((a, k, i) => t >= k[0] ? i : a, 0); if (TL.ci !== ci) { if (TL.ci != null && t > .01) for (const X of [C, M]) { X.smDone = false; X.cut = true; } TL.ci = ci; }
  const [mp, mpose] = seatMon(TL, t, M, K, act, te);
  return [[V, HUSH_E[0], 0, HUSH_E[1], 0, seatVic(TL, t, C, act), null], [K, mp[0], 0, mp[1], HUSH_MRY, mpose, t > te + .9 ? 'cam' : null]];
}
// касания после поз (как в gPost): руки его — сначала кончики пальцев ползут по лицу, потом хватка за виски / глаза / горло / плечо и сквозь спину / в живот;
// руки Итана — за его предплечья, после смерти — к ране
function seatPost(o, t, kind, TL, used) {
  const { V, K, base } = goreWho(kind), C = A[V], M = A[K]; if (!used.has(V) || !used.has(K) || !C.bone || !M.bone) return;
  const W3 = window.__W3chars, RA = W3 && W3.reachArm; if (!RA) return;
  const [grip, act] = GSK[base], f = goreFx(o); C.root.updateMatrixWorld(true);
  const B = bodyPts(C), H = C.bone.Head.getWorldPosition(new T.Vector3()), N = C.bone.neck_01.getWorldPosition(new T.Vector3()), V3a = v => [v.x, v.y, v.z];
  const P = (o2, fw = 0, lr = 0, up = 0) => o2.clone().addScaledVector(B.fw, fw).addScaledVector(B.lr, lr).addScaledVector(B.up, up);
  let cl = P(H, 0, .095, .02), cr = P(H, 0, -.095, .02);   // виски жертвы
  if (act === 'twist') { const ang = -2.75 * seg(t, TL.kill - .04, TL.kill + .14);   // рывок 0,18 с: голова уходит за предел — тело к нам, а лицо смотрит назад
    if (ang) { twistHead(C, B.up, ang); const q = new T.Quaternion().setFromAxisAngle(B.up, ang * .45); for (const c of [cl, cr]) c.sub(N).applyQuaternion(q).add(N); } }
  const te = (TL.cam.find(k => k[1] === 'e') || [99])[0], rel = stg(t, TL.rel - .05, TL.rel + .3), wM = (1 - rel) * keyAt(t, [[0, .8], [TL.touch, .9], [TL.hit, 1]]);   // руки подняты с первого кадра (опущенная правая проходила спинку стула)
  const R = locR(M.root.position.x, M.root.position.z, HUSH_MRY), big = K === 'monster' ? 1 : .8;
  const from = { l: R.p(.36 * big, 1.85 * big, .25), r: R.p(-.3 * big, 1.85 * big, .1) };   // руки подняты у его головы
  const cheek = { l: V3a(P(H, .05, .085, -.07 + .06 * stg(t, TL.touch, TL.hit))), r: V3a(P(H, .05, -.085, -.07 + .06 * stg(t, TL.touch, TL.hit))) };   // кончики пальцев ползут по щекам вверх
  const go = (sd, tgt, by, extra) => { const ks = t >= TL.hit ? 1 : 0, kt = stg(t, TL.touch - .25, TL.touch), kg = stg(t, TL.hit - .12, TL.hit);
    let p = t < TL.touch ? lin(from[sd], cheek[sd], kt * kt) : lin(cheek[sd], tgt, kg * kg); if (t < TL.touch) p[1] += Math.sin(PI * kt) * .06;
    RA(M, sd, Object.assign({ at: ks ? tgt : p, by: t < TL.hit - .06 ? 'tip' : by || 'palm', w: wM, pole: sd === 'l' ? [1, -.4, -.4] : [-1, -.4, -.4] }, extra)); };
  let wound0 = null, wk = 0, sprayP = null, sprayD = null, bursts = [];
  if (grip === 'head') { go('l', V3a(cl)); go('r', V3a(cr)); if (act === 'bite') { wound0 = 'face'; wk = stg(t, TL.kill, TL.kill + .25); sprayP = B.face; sprayD = B.fw; bursts = [TL.kill + .02, TL.kill + .4]; } }
  else if (grip === 'eyes') { const push = .03 * seg(t, TL.kill - .06, TL.kill + .1); go('l', V3a(P(H, .085 - push, .033, .035)), 'thumb'); go('r', V3a(P(H, .085 - push, -.033, .035)), 'thumb');
    wound0 = 'eyes'; wk = stg(t, TL.kill, TL.kill + .3); if (C.HU && C.HU.uSocket) C.HU.uSocket.value = Math.max(C.HU.uSocket.value, wk); sprayP = B.face; sprayD = B.fw; bursts = [TL.kill + .03, TL.kill + .45]; }
  else if (grip === 'throat') { go('l', V3a(P(H, .09, .02, .06))); go('r', V3a(P(N, -.02, -.17, -.08)));
    const tb = stg(t, TL.hit, TL.hit + .3) * (1 - stg(t, TL.rel - .1, TL.rel + .2));   // голову оттянул назад — горло открыто
    if (tb > 0) { const hb = C.bone.Head; hb.updateWorldMatrix(true, false); const wq = hb.getWorldQuaternion(new T.Quaternion()).premultiply(new T.Quaternion().setFromAxisAngle(B.lr, -.45 * tb)); hb.parent.updateWorldMatrix(true, false); hb.quaternion.copy(hb.parent.getWorldQuaternion(new T.Quaternion()).invert().multiply(wq)); hb.updateWorldMatrix(false, true); }
    wound0 = 'throat'; wk = stg(t, TL.kill, TL.kill + .2); sprayP = B.throat; sprayD = B.fw; bursts = [TL.kill + .04, TL.kill + .42, TL.kill + .8, TL.kill + 1.2]; }
  else if (grip === 'chest') { go('l', V3a(P(N, -.03, .17, -.1)));   // левой держит за плечо
    if (t < TL.hit) go('r', V3a(cr));   // сначала, как у всех, пальцы на его лице
    else { const wind = V3a(P(B.chest, -.24, -.02, .46)), thru = V3a(P(B.chest, .2, -.02, 0)), back = V3a(P(B.chest, -.38, -.02, .12));   // замах над спинкой стула (не сквозь неё) → вниз и насквозь, кисть выходит из груди → рывок обратно
      const k1 = seg(t, TL.hit, TL.kill - .2), k2 = stg(t, TL.kill - .16, TL.kill), k3 = seg(t, TL.rel - .35, TL.rel);
      RA(M, 'r', { at: k3 > 0 ? lin(thru, back, k3) : k2 > 0 ? lin(wind, thru, k2 * k2) : lin(V3a(cr), wind, k1), by: 'palm', w: Math.max(wM, 1 - rel), pole: [-1, -.4, -.4] }); }
    wound0 = 'chest'; wk = stg(t, TL.kill - .02, TL.kill + .15); sprayP = B.chest; sprayD = B.fw; bursts = [TL.kill + .02, TL.kill + .35, TL.rel - .3];
    if (act === 'heart') { const h = M.bone.hand_r.getWorldPosition(new T.Vector3()); f.heart.visible = t > TL.kill + .05; f.heart.position.copy(h); f.heart.position.y += .02; f.heart.scale.setScalar(1 + .14 * Math.max(0, Math.sin(t * 7.5)) ** 4 * (t < TL.rel + .8 ? 1 : 0)); } }
  else if (grip === 'belly') { const dig = seg(t, TL.kill - .1, TL.kill + .08), rip = seg(t, TL.kill + .3, TL.kill + .6);
    go('l', V3a(P(B.belly, .02 + .06 * dig, .16 - .1 * dig + .14 * rip, .02 * rip))); go('r', V3a(P(B.belly, .02 + .06 * dig, -.16 + .1 * dig - .14 * rip, .02 * rip)));
    wound0 = 'belly'; wk = stg(t, TL.kill, TL.kill + .5); sprayP = B.belly; sprayD = B.fw; bursts = [TL.kill + .05, TL.kill + .45]; }
  if (wound0) wound(C, wound0, wk);
  if (t >= te) { const sh = act === 'heart' ? mouthPt(M).clone().add(new T.Vector3(0, -.08, 0)) : P(N, -.04, .16, -.06); RA(M, 'r', { at: V3a(sh), by: 'palm', w: seg(t, te, te + .4), pole: [-1, -.4, -.4] }); if (K === 'monster') RA(M, 'l', { at: [5.42, 1.17, -1.12], by: 'palm', w: seg(t, te, te + .4), pole: [1, -.4, -.4] }); }   // «Лили» и двойник ниже: их рука до спинки шла сквозь неё   // конец: ладонь на плече мёртвого, другая — на спинке стула (опущенная рука проходила соседнее кресло)
  // руки Итана: замер — сжал подлокотники (кулаки), схватили — за его предплечья; после смерти — к ране (горло, лицо, глаза), пока не упал
  const sg = stg(t, TL.hit + .03, TL.hit + .2) * (1 - stg(t, TL.kill, TL.kill + .12)), cw = (act === 'bite' || act === 'gouge') ? stg(t, TL.kill + .1, TL.kill + .25) * (1 - seg(t, TL.rel - .15, TL.rel + .25)) : 0;
  if (sg > 0) { M.root.updateMatrixWorld(true); const fa = sd => M.bone['lowerarm_' + sd].getWorldPosition(new T.Vector3()).lerp(M.bone['hand_' + sd].getWorldPosition(new T.Vector3()), .5);
    const l = fa('l'), r = fa('r'); RA(C, 'l', { at: V3a(l), by: 'palm', w: sg }); RA(C, 'r', { at: V3a(r), by: 'palm', w: sg }); }
  if (cw > 0) { const wp = grip === 'throat' ? B.throat : B.face; RA(C, 'l', { at: V3a(P(wp, .06, .05, 0)), by: 'palm', w: cw }); RA(C, 'r', { at: V3a(P(wp, .06, -.05, 0)), by: 'palm', w: cw }); }
  // кишки и кровь
  if (grip === 'belly' && t > TL.kill + .35) { const hl = M.bone.hand_l.getWorldPosition(new T.Vector3()), hr = M.bone.hand_r.getWorldPosition(new T.Vector3());
    gutsDraw(o, [[B.belly, hr, .14, .02], [B.belly, hl, .1, .018], [B.belly, P(B.belly, .25, .05, -.35), .05, .022]], t); }
  else if (grip === 'chest' && t > TL.rel - .3 && t < TL.rel + .6) { const h = M.bone.hand_r.getWorldPosition(new T.Vector3()); gutsDraw(o, [[B.chest, h, .04, .014]], t); }
  else gutsDraw(o, [], t);
  if (sprayP) sprayAt(f, sprayP, sprayD, t, bursts);
  if (t > TL.kill) { f.pool.visible = true; f.pool.position.set(HUSH_E[0], .006, HUSH_E[1] + .05); f.pool.scale.setScalar(.08 + .55 * stg(t, TL.kill + .3, TL.d)); }   // под стулом натекает
}
// ---- под стойкой («Оно услышало», k5/k5b): Итан сидит на корточках под столом; в просвете — лицо «Лили», рука выстреливает к нему (последний кадр смерти);
// дальше: хватает за лодыжку и рывком тащит из-под стола — он падает на спину и цепляется пальцами за пол; ещё рывок; она садится на него и рвёт живот,
// тянет кишки ко рту. Конец — с камеры будки (CAM 00): из-под стола торчат его ноги, над ним сидит она и поднимает голову к объективу
const GTH = { d: 6.0, hit: .45, yank: .55, kill: 2.0, cam: [[0, 'u'], [.55, 'e']],
  sfx: [['breath_close', .05, .7], ['skin_stretch', .45, .9], ['boom', .6, .75, { rate: .7 }], ['glass_scratch', .95, .55, { rate: .6 }], ['boom', 1.3, .5, { rate: .8 }], ['glass_scratch', 1.45, .45, { rate: .5 }],
    ['skin_stretch', 2.0, 1], ['eat_wet', 2.05, 1], ['skin_stretch', 2.5, .9, { rate: .8 }], ['eat_wet', 2.6, 1, { rate: .9 }], ['eat_wet', 3.6, .85], ['eat_wet', 4.4, .75, { rate: 1.1 }]] };
const HIDE_RY = PI - 1.0, HIDE_F = [Math.sin(HIDE_RY), Math.cos(HIDE_RY)], HIDE_P = { x: 5.96, z: -.95, d: .1 }, HIDE_K0 = [6.55, -1.2], HIDE_K = [5.28, -.86];   // лежит наискосок (голова под столом, ноги к углу будки); тянут рывками на 22 см; она — у ног, потом сбоку у живота (стул отлетел к стене)
function hideCam(sh, TL, t) {
  const c = TL.cam.reduce((a, k) => t >= k[0] ? k : a, TL.cam[0]), lt = t - c[0];
  if (c[1] === 'u') { sh.pos = [5.75, .5, -.22]; sh.look = [5.82, .58, -1.2]; sh.fov = 50; return; }   // его глазами из-под стола: её лицо в просвете, рука к нам
  const k = Math.min(.18, lt * .035); sh.pos = [6.42 - .45 * k, 2.32 - .9 * k, .32 - .6 * k]; sh.look = [5.78, .32, -.72]; sh.fov = 52;   // камера будки, медленный цифровой наезд
}
function hideSet(o, t, kind, TL, sh) {
  const { V, K } = goreWho(kind), C = A[V], M = A[K];
  lobbyBlack(o); o.l1.intensity = .22; o.amb.intensity = .1; o.flickOn = false; o.chair.position.set(4.55, 0, -1.2); if (o.ch2) o.ch2.position.set(7.2, 0, -1.28);   // стулья сшибли к стенам
  const fl = t > TL.yank && Math.sin(t * 31) > .55 && t < TL.kill + .6 ? .55 : 1;   // лампа будки захлёбывается, пока его тащат
  if (t < TL.yank) { o.faceL.position.set(6.35, .6, -.5); o.faceL.color.setHex(0xa8b8cc); o.faceL.distance = 1.7; o.faceL.intensity = 1.1; }   // как в кадре смерти: холодный свет сбоку — на её лицо в просвете
  else { o.faceL.position.set(5.95, 1.55, -.62); o.faceL.color.setHex(0xc4d0dc); o.faceL.distance = 3.2; o.faceL.intensity = 1.9 * fl; o.lamp.intensity = .35 * fl; }   // над телом: видно, как его тащат
  hideCam(sh, TL, t);
  const ci = TL.cam.reduce((a, k, i) => t >= k[0] ? i : a, 0); if (TL.ci !== ci) { if (TL.ci != null && t > .01) for (const X of [C, M]) { X.smDone = false; X.cut = true; } TL.ci = ci; }
  if (t < TL.yank) {   // под столом: сжался, смотрит на неё; она у просвета, голова набок, рука уже у его ноги
    const dn = seg(t, 0, .25);
    return [[K, K5[0], 0, K5[1], 0, { clips: [['Crouch_Idle_Loop', 1.0, 1]], legs: 'plant', pelvis: [0, -.06 * dn, 0], spine: { to: [5.78, .45, -.3], k: .48 },   // его глазами: сам он не в кадре (камера — его глаза)
        fing: { r: ['claw', .85] }, post: X => headTwist(X, 0, 1.1) }, [5.75, .5, -.22]]];
  }
  const yk = keyAt(t, [[TL.yank, 0], [TL.yank + .18, .55, 'in'], [1.25, .6], [1.42, 1, 'in']]), px = HIDE_P.x + HIDE_F[0] * HIDE_P.d * yk, z = HIDE_P.z + HIDE_F[1] * HIDE_P.d * yk;   // два рывка за лодыжку
  const on = seg(t, 1.55, 1.95), rip = stg(t, TL.kill, TL.kill + .5);   // села на него; рвёт
  C.faceSets.g_v = t > TL.kill + .4 ? { dead: 1, mouth: .5 } : { mouth: .7, wide: 1.5, browUp: 1, browIn: 1, tense: .6, scream: .8 }; C.faceSet = 'g_v';
  const kick = t < TL.kill + .4 ? 1 : 0, arms = (1 - on * .5) * kick;   // бьётся, руками гребёт по полу под столом
  const vp = { clips: [['Death01', 2.4, 1]], heels: 0, fing: { l: ['claw', .9], r: ['claw', .9] },
    wig: [['upperarm_l', [0, 0, 1], .35 * Math.sin(t * 9) * arms], ['upperarm_r', [0, 0, 1], .35 * Math.sin(t * 8 + 2) * arms], ['lowerarm_l', [1, 0, 0], .4 * Math.sin(t * 13) * arms], ['lowerarm_r', [1, 0, 0], .4 * Math.sin(t * 11 + 1) * arms],
      ['thigh_l', [1, 0, 0], -.25 * Math.max(0, Math.sin(t * 10)) * kick], ['calf_r', [1, 0, 0], .3 * Math.sin(t * 12 + 2) * kick], ['hand_r', [0, 0, 1], t > TL.kill + .4 ? .5 * Math.max(0, Math.sin(t * 13)) ** 8 : 0]] };
  // она: держит за лодыжку и тянет (сидит у его ног) → переползает к животу и садится над ним (поза 'eat') → в конце поднимает голову к объективу
  const kp = lin([HIDE_K0[0], HIDE_K0[1]], HIDE_K, on), ry = lerp(HIDE_RY + PI, .98, on);   // у ног — лицом к нему; у живота — лицом к животу
  M.eatHold = t > TL.d - 1.6 ? seg(t, TL.d - 1.6, TL.d - .9) : 0;
  const mp = on > .5 ? 'eat' : { clips: [['Crouch_Idle_Loop', 1.0, 1]], legs: 'plant', pelvis: [0, -.08, 0], spine: { to: [px - HIDE_F[0] * .4, .3, z - HIDE_F[1] * .4], k: .45 }, fing: { r: ['claw', 1], l: ['claw', 1] } };
  return [[V, px, 0, z, HIDE_RY, vp, null], [K, kp[0], 0, kp[1], ry, mp, t > TL.d - 1.4 ? 'cam' : [px - HIDE_F[0] * .5, .25, z - HIDE_F[1] * .5]]];
}
function hidePost(o, t, kind, TL, used) {
  const { V, K } = goreWho(kind), C = A[V], M = A[K];
  const W3 = window.__W3chars, RA = W3 && W3.reachArm; if (!RA || !used.has(K) || !M.bone) return; const f = goreFx(o);
  if (t < TL.yank) { const k = stg(t, TL.hit - .22, TL.hit); if (k > 0) RA(M, 'r', { at: lin([5.7, .14, -.86], [5.76, .5, -.36], k * k), by: 'palm', w: Math.max(.5, k), pole: [-.7, -1, -.3] }); return; }   // рука выстреливает к нему — к объективу снизу (как в кадре смерти)
  if (!used.has(V) || !C.bone) return;
  if (W3.layFloor) W3.layFloor(C); gLift(C);
  C.root.updateMatrixWorld(true); const B = bodyPts(C), V3a = v => [v.x, v.y, v.z];
  const ank = C.bone.foot_r.getWorldPosition(new T.Vector3()), on = seg(t, 1.55, 1.95);
  if (t < TL.yank) return;
  if (on < .5) { RA(M, 'r', { at: V3a(ank.clone().add(new T.Vector3(0, .04, 0))), by: 'palm', w: 1 - on * 2, pole: [-.7, -1, -.3] }); RA(M, 'l', { at: V3a(C.bone.foot_l.getWorldPosition(new T.Vector3()).add(new T.Vector3(0, .04, 0))), by: 'palm', w: .8 * (1 - on * 2) }); }   // держит обеими за ноги
  // рвёт живот: когти в живот → рывок вверх с кишками → ко рту (как в старом «ест», только на ней и под столом)
  const rip = stg(t, TL.kill - .1, TL.kill + .5), mo = mouthPt(M), up = B.belly.clone().add(new T.Vector3(0, .34, 0));
  if (t > TL.kill - .3) { const tgt = t < TL.kill + .45 ? B.belly.clone().add(new T.Vector3(0, .03 + .04 * Math.sin(t * 19), 0)) : t < TL.kill + 1.4 ? B.belly.clone().lerp(up, stg(t, TL.kill + .45, TL.kill + 1.0)) : up.clone().lerp(mo, stg(t, TL.kill + 1.4, TL.kill + 1.9));
    RA(M, 'r', { at: V3a(tgt), by: 'palm', w: on }); wound(C, 'belly', rip);
    const h = M.bone.hand_r.getWorldPosition(new T.Vector3());
    gutsDraw(o, t > TL.kill + .4 ? [[B.belly, h, .3, .02], [B.belly, h.clone().lerp(B.belly, .35).addScaledVector(B.lr, .12), .22, .018], [B.belly, B.belly.clone().addScaledVector(B.lr, .3).setY(.05), .02, .022]] : [], t);
    sprayAt(f, B.belly, B.up, t, [TL.kill + .02, TL.kill + .5, TL.kill + 1.1]); }
  else gutsDraw(o, [], t);
  // след: по полу из-под стола — туда, куда его вытащили; лужа — под ним
  { const ax = 5.6, az = -.35, bx = C.root.position.x, bz = C.root.position.z, ln = Math.max(.05, Math.hypot(bx - ax, bz - az));   // след: от-под стола — туда, куда его вытащили
    f.trail.visible = true; f.trail.position.set((ax + bx) / 2, .005, (az + bz) / 2); f.trail.rotation.set(-PI / 2, 0, Math.atan2(bx - ax, bz - az)); f.trail.scale.set(.3, ln, 1); }
  f.pool.visible = t > TL.kill; f.pool.position.set(B.belly.x, .006, B.belly.z); f.pool.scale.setScalar(.1 + .5 * stg(t, TL.kill, TL.d));
}
function gusEat(o, t, up) {
  eatLight(o, t); o.cleanSet(AFTER); o.gutsOn = true; const M = A.monster, chest = [-.72, .22, -11.78];
  if (!up) eatGore(o, t); else { o.bursts = null; A.victim.twitch = t < 1.2 ? .25 : 0; M.eatHold = seg(t, .15, .8); }
  const k = up ? seg(t, .5, 2.6) : 0, g = up ? seg(t, 1.9, 2.5) : 0;
  if (g && M.eyeM && M.eyeM.emissive) M.eyeM.emissive.setRGB(1.6 * g, 1.6 * g, 1.5 * g);
  return [['victim', -.4, LAYY, -11.78, PI * .5, 'lay', null], ['monster', -.72, 0, -12.5, .08, 'eat', k > .02 ? lin(chest, C13G, k) : chest]];
}
SH.c13_gus = { loc: 'corridor', pos: C13G, look: [-.62, .38, -12.0], fov: 52, cctv: 1, set: (o, t) => gusEat(o, t, false) };
SH.c13_gus_up = { loc: 'corridor', pos: C13G, look: [-.66, .8, -12.3], fov: 52, cctv: 1, face: { monster: 'grin' }, set: (o, t) => gusEat(o, t, true) };
// ---- улица ----
// x03: выбежал через аварийный выход — бежит под дождём вдоль фасада
// x03 «Я вывалился под дождь»: раньше Итан бежал вдоль фасада уже снаружи, и было непонятно, откуда он взялся (владелец, 12 ноя: «как он с улицы убегал, если был в помещении?»).
// Теперь камера стоит на улице напротив входа: створки распахиваются изнутри, Итан вылетает из холла под дождь, пробегает мимо камеры — она поворачивается ему вслед; двери медленно закрываются сами
SH.x_out = { loc: 'lobby', pos: [-5.0, 1.35, 10.6], look: [-9.3, 1.3, 6.2], fov: 52, set: (o, t, sh) => {
  // 21 ноя: аварийный выход служебного крыла (buildFacade, o.exitLeaf), как в тексте x01, — а не парадные двери. Бежит из тёмного коридора, на бегу бьёт
  // обеими ладонями в штангу — железная створка распахивается наружу и с ударом встаёт, доводчик потом медленно тянет её назад; Итан выскакивает под дождь и
  // уходит мимо камеры к улице, камера ведёт его
  const P = [[-9.3, 3.3], [-9.3, 7.1], [-8.7, 9.6], [-7.9, 15.5]], d = Math.max(0, t - .1) * 4.2;
  let a = d, k = 0; while (k < P.length - 2 && a > Math.hypot(P[k + 1][0] - P[k][0], P[k + 1][1] - P[k][1])) { a -= Math.hypot(P[k + 1][0] - P[k][0], P[k + 1][1] - P[k][1]); k++; }
  const A0 = P[k], B0 = P[k + 1], L = Math.hypot(B0[0] - A0[0], B0[1] - A0[1]), f = Math.min(a / L, 1.4), x = A0[0] + (B0[0] - A0[0]) * f, z = A0[1] + (B0[1] - A0[1]) * f, ry = Math.atan2(B0[0] - A0[0], B0[1] - A0[1]);
  const open = 1.75 * seg(t, .6, .8) - .15 * seg(t, .8, 1.0) - 1.2 * seg(t, 2.0, 5.5);
  if (o.exitLeaf) o.exitLeaf.rotation.y = -Math.max(0, open);
  const push = seg(t, .38, .56) * (1 - seg(t, .74, .98)), hz = Math.min(z + .42, 6.2 + .55 * seg(t, .6, .8));
  const hand = sx => ({ at: [-9.3 + sx * .18, 1.02, hz], by: 'palm', fdir: [sx * .15, 1, .2], pdir: [0, .1, 1], w: push });
  const fl = seg(z, 7.2, 10.0); sh.look[0] = lerp(-9.3, x, fl); sh.look[1] = lerp(1.3, 1.15, fl); sh.look[2] = lerp(6.2, z, fl);
  return [['temych', x, 0, z, ry, { clips: [['Sprint_Loop', t * 1.25, 1]], look: [x + Math.sin(ry) * 3, 1.5, z + Math.cos(ry) * 3], arm: push > .01 ? { l: hand(1), r: hand(-1) } : null, fing: { l: ['open', push], r: ['open', push] } }, null]]; } };
// x04: «Горизонт» тёмный; над крышей, где этажей уже нет, светится окно
function lobbyOff(o) { o.l1.intensity = .15; o.l3.intensity = .1; o.l4.intensity = .1; o.lamp.intensity = 0; o.lampBulb.visible = false; o.boothLight.intensity = .05; o.amb.intensity = .12; o.canopy.intensity = .35; }
SH.x_building = { loc: 'lobby', pos: [2.6, 1.5, 21.5], look: [-1.0, 22.5, 6], fov: 66, set: (o, t) => { o.fac.material = o.facDark; o.ghostWin.visible = true; o.ghostWin.scale.setScalar(1.5); lobbyOff(o);
  o.S.fog.density = .012; o.hemi.intensity = .55; o.sky.material.map = TX.skyCity;
  return [['temych', 1.6, 0, 23.2, PI - .08, { clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', look: [-3.1, 41.6, 6] }, null]]; } };
// x05–x07: стоит под дождём, телефон у уха; за спиной — тёмное здание
SH.x_rain = { loc: 'lobby', pos: [2.35, 1.6, 17.6], look: [.9, 1.6, 15.0], fov: 44, set: (o, t) => { o.fac.material = o.facDark; o.ghostWin.visible = true; lobbyOff(o); o.sky.material.map = TX.skyCity;
  const ry = 1.0, R = locR(1.15, 15.6, ry);   // лицом почти к камере, телефон у правого уха: ладонь к щеке, пальцы вперёд, локоть вниз (клип клал руку поперёк лица)
  // x06: слушает её, не понимая; на «сказал, что стоишь у меня под окнами» (2,1 с) — замирает: лицо каменеет, дыхание встаёт, взгляд в никуда
  const fr = seg(t, 2.1, 2.6); A.temych.faceSet = t < 2.1 ? 'worry' : 'dread';
  return [['temych', 1.15, 0, 15.6, ry, { clips: [['Idle_TalkingPhone_Loop', t + 1, 1 - .7 * fr], ['Idle_Loop', 1.4, .7 * fr]], legs: 'feet', look: t < 2.1 ? R.p(-.4, 1.35, 2.5) : R.p(-.15, 1.6, 4),
    toes: 1, br: [lerp(1, .35, fr), lerp(1, .6, fr)], arm: { r: earArm('r', [-.45, -1, .35]) }, fing: { r: ['phone', 1] }, hold: { r: ['phone', 'phone_ear'] } }, null]]; } };
// x07 «Я стоял под дождём у „Горизонта“. В трёхстах километрах от её окон»: медленно оглядывается через левое плечо вверх, на тёмное здание
// (0,3–2,2), возвращает взгляд — и на «трёхстах километрах» свободная левая рука сама поднимается ко рту; телефон так и у уха
SH.x_rain2 = Object.assign({}, SH.x_rain, { set: (o, t) => { SH.x_rain.set(o, 4); const ry = 1.0, R = locR(1.15, 15.6, ry);
  const up = seg(t, .3, 1.5) * (1 - seg(t, 2.0, 2.7)), cov = seg(t, 2.5, 3.2); A.temych.faceSet = t < 2.6 ? 'dread' : 'fear';
  const lk = lin(R.p(-.15, 1.6, 4), R.p(2.2, 4.2, .4), up);
  const mouth = { at: C => { const [m, f] = mouthOf(C); return [m.x + f.x * .035, m.y - .01, m.z + f.z * .035]; }, by: 'palm', fdir: R.v(-.55, .83, .05), pdir: R.v(0, 0, -1), w: cov, pole: [.7, -1, .1] };
  return [['temych', 1.15, 0, 15.6, ry, { clips: [['Idle_TalkingPhone_Loop', t + 5, .3], ['Idle_Loop', 1.4, .7]], legs: 'feet', toes: 1, look: lk, br: [.5, .7],
    arm: { r: earArm('r', [-.45, -1, .35]), l: mouth }, fing: { r: ['phone', 1], l: ['relax', 1] }, hold: { r: ['phone', 'phone_ear'] } }, null]]; } });
// Б1, рассвет: вдвоём под козырьком. Фрэнк (левая нога в картоне) висит на Итане: левая рука через его плечи, кисть на дальнем плече;
// Итан правой держит его сзади за пояс. Оси Итана: x — влево, z — вперёд (корпус в стойке Idle сидит на 11 см позади точки ног)
const XE = [-.1, 7.45], XER = -.1, XRE = () => locR(XE[0], XE[1], XER), XF = XRE().p(-.45, 0, -.04), XFR = .12;
// рука Фрэнка через плечи Итана (RE — оси Итана, y — высота пола) и рука Итана сзади у Фрэнка на поясе (RF — оси Фрэнка)
// рука Фрэнка через плечи Итана: ладонь обхватывает дальнее плечо спереди-сверху, пальцы вниз по плечу, локоть лежит на ближнем плече —
// раньше кисть торчала обрубком за шеей Итана (владелец, 12 ноя, кадр «Я покачал головой»)
// Фрэнк опирается на Итана: левая ладонь лежит сверху на его ближнем (правом) плече, пальцы спереди через плечо, локоть вниз.
// Раньше рука тянулась через всю спину к дальнему плечу: не доставала, шла прямой палкой, а кисть свисала спереди у груди Итана, как третья рука (владелец, 12 ноя)
const armOver = (RE, y = 0) => { const at = RE.p(-.175, 1.475, -.015); at[1] += y; return { at, by: 'palm', fdir: RE.v(.12, -.42, .9), pdir: RE.v(0, -1, -.12), pole: [.25, -.9, -.35], w: 1 }; };
const armWaist = (RF, y = 0) => { const at = RF.p(.02, .99, -.25); at[1] += y; return { at, by: 'palm', fdir: RF.v(-1, -.15, .1), pdir: RF.v(0, 0, 1), pole: [-.4, -.6, -1], w: 1 }; };
function frankOut(t, extra = {}) {
  const R = XRE(), F = locR(XF[0], XF[2], XFR);
  // 21 ноя (тестер: «Фрэнк со сломанной ногой стоит ровно на обеих ногах и почти не опирается на Итана»): левая нога в шине на землю не встаёт — согнута
  // в колене, стопа на весу носком вниз; весь вес на правой, таз ушёл на неё и просел, корпус привалился к Итану (он слева, рука Фрэнка у него на плечах)
  const sway = .008 * Math.sin(t * 1.7);
  return Object.assign({ clips: [['Idle_Loop', t + 4, 1]], legs: 'plant', wear: { splint: SPLINT }, talk: 1, pain: .35,
    feet: { l: { at: F.p(.11, .15 + sway, -.07), yaw: .1, w: 1 }, r: { at: F.p(-.11, .09, -.02), w: 1 } }, pelvis: F.v(-.06, -.025, 0),
    arm: { l: armOver(R) }, fing: { l: ['grip', .45] }, spine: { to: R.p(0, 1.3, -.1), k: .16 } }, extra);
}
function ethanProp(t, extra = {}) {
  return Object.assign({ clips: [['Idle_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: armWaist(locR(XF[0], XF[2], XFR)) }, fing: { r: ['grip', .3] } }, extra);
}
// сигарета в правой: k = 0 — у груди (пальцы вперёд, ладонь вниз), 1 — у губ (пальцы вверх буквой V, ладонь к лицу, фильтр в губах)
// затяжка — отдельным движением, а не качелями: рука идёт к губам 1,1 с, держит .8 с, опускается 1,2 с; между затяжками 9 с сигарета внизу, у пояса.
// Пока Фрэнк говорит — не курит: рука с сигаретой опущена (владелец, 11 ноя: «когда говорит, сигарету не должен курить»)
const cigDrag = (t, t0, per = 9) => { if (t < t0) return 0; const u = (t - t0) % per; return seg(u, 0, 1.1) * (1 - seg(u, 1.9, 3.1)); };
function cigTalk(C, k) { C.cigQ = lerp(C.cigQ || 0, C.talkingNow ? 1 : 0, C.talkingNow ? .12 : .03); return k * (1 - clamp(C.cigQ * 1.6, 0, 1)); }
// 20 ноя (тестер: «между затяжками рука с сигаретой торчит прямая в сторону, как шлагбаум»): нижняя точка была задана от места, где стоит Фрэнк, а он висит на Итане —
// плечо уехало, и рука вытягивалась в сторону. Теперь — от его собственного плеча в этом кадре: кисть у пояса перед собой, локоть согнут и прижат
function frankCig(F, C, k, w = 1) { const [m, f] = mouthOf(C), R = locR(XF[0], XF[2], XFR), sh = C.bone.upperarm_r.getWorldPosition(new T.Vector3()), lv = R.v(-.03, -.4, .2), lo = [sh.x + lv[0], sh.y + lv[1], sh.z + lv[2]], lip = [m.x + f.x * .012, m.y - .004, m.z + f.z * .012], e = k * k * (3 - 2 * k);
  // рука идёт ко рту дугой перед грудью, локоть — вбок и вперёд: по прямой и с локтем назад предплечье проходило сквозь туловище до 11 см
  // (владелец видел в игре, замер tools/anim/arm_body_probe.js, 12 ноя)
  const cv = R.v(-.1, 0, .2), mid = [(lo[0] + lip[0]) / 2 + cv[0], (lo[1] + lip[1]) / 2, (lo[2] + lip[2]) / 2 + cv[2]], bz = (a, b, c) => (1 - e) * (1 - e) * a + 2 * e * (1 - e) * b + e * e * c;
  F.arm.r = { at: [bz(lo[0], mid[0], lip[0]), bz(lo[1], mid[1], lip[1]), bz(lo[2], mid[2], lip[2])], pole: lin(R.v(-.5, -1, -.3), [-.55, -.45, .9], e), by: 'pinch', fdir: lin([f.x, -.15, f.z], [f.x * .35, .9, f.z * .35], e), pdir: lin([0, -1, 0], R.v(1, 0, 0), e), w }; F.fing.r = ['pinch', 1]; }
SH.x_two = { loc: 'lobby', pos: [1.75, 1.5, 10.3], look: [-.4, 1.35, 7.5], fov: 44, set: (o, t) => {
  const F = frankOut(t, { look: [XE[0], 1.62, XE[1]] });
  if (curProps && curProps.cig) { frankCig(F, A.mihalych, cigTalk(A.mihalych, cigDrag(t, 1.4))); F.hold = { r: 'cig' }; }
  return [['temych', XE[0], 0, XE[1], XER, ethanProp(t, { talk: 1, look: [1.5, 1.6, 12] }), null], ['mihalych', XF[0], 0, XF[2], XFR, F, null]]; } };
// «Я покачал головой»: качает головой на своей стойке (react 'no' в story.js) — не переходит всем телом в клип Quaternius
// d26 «Он ведь сюда ради неё и ходил. Послушать»: Фрэнк отводит глаза вниз, в мокрый асфальт, голова опускается; Итан смотрит на него
SH.x_two_sad = Object.assign({}, SH.x_two, { set: (o, t) => { const r = SH.x_two.set(o, t), E = r.find(a => a[0] === 'temych'), F = r.find(a => a[0] === 'mihalych');
  F[5].look = t < .5 ? [XE[0], 1.62, XE[1]] : [XF[0] + .5, .25, XF[2] + 1.3];
  E[5].look = [XF[0], 1.55, XF[2]]; return r; } });
SH.x_no = { loc: 'lobby', pos: [-1.15, 1.62, 8.75], look: [-.15, 1.58, 7.45], fov: 38, /* камера с другой стороны: Итан поворачивается к Фрэнку — раньше в кадре был его затылок, а не лицо */ set: (o, t) => [['temych', XE[0], 0, XE[1], XER, ethanProp(t, { look: [XF[0], 1.55, XF[2]] }), null],
  ['mihalych', XF[0], 0, XF[2], XFR, frankOut(t, { look: [XE[0], 1.62, XE[1]] }), null]] };
// d27: закурил одной правой — зажигалка у сигареты, огонёк, затяжка; сигарета остаётся в пальцах. Левая так и лежит на плечах Итана
function mouthOf(C) { const h = C.bone.Head.getWorldPosition(new T.Vector3()), m = C.mouthL ? C.bone.Head.localToWorld(C.mouthL.clone()) : h.clone().add(new T.Vector3(0, -.08, .1));
  const f = m.clone().sub(h); f.y *= .25; return [m, f.normalize()]; }
SH.x_smoke = { loc: 'lobby', pos: [.55, 1.62, 9.0], look: [-.55, 1.55, 7.6], fov: 36, set: (o, t) => {
  // 0–2.1 зажигалка у кончика и обратно; 2.3–2.75 рука к губам, с 2.8 сигарета в пальцах — от губ к груди; дальше затяжки
  const lit = !!(curProps && curProps.cig);   // уже курит (следующий кадр той же сцены: Фрэнк говорит) — зажигалка второй раз не достаётся
  const up = seg(t, 0, .5) * (1 - seg(t, 1.6, 2.1)), flame = t > .45 && t < 1.6, rise = seg(t, 2.3, 2.75), took = t >= 2.8, puff = cigDrag(t, 7);
  const F = frankOut(t, { look: [.55, 1.62, 9.0] });
  if (lit) { frankCig(F, A.mihalych, cigTalk(A.mihalych, cigDrag(t, 2.5))); F.hold = { r: 'cig' };
    return [['mihalych', XF[0], 0, XF[2], XFR, F, null], ['temych', XE[0], 0, XE[1], XER, ethanProp(t, { look: [XF[0], 1.55, XF[2]] }), null]]; }
  const C = A.mihalych, [m, f] = mouthOf(C), cdn = nrm([f.x * .62 - f.z * .72, -.24, f.z * .62 + f.x * .72]), tip = [m.x - f.z * .013 + cdn[0] * .08, m.y - .005 + cdn[1] * .08, m.z + f.x * .013 + cdn[2] * .08];   // кончик сигареты в уголке рта — туда подносит зажигалку
  if (rise > 0) { frankCig(F, C, took ? Math.max(1 - seg(t, 2.8, 3.4), puff) : 1, rise); F.hold = { r: took ? 'cig' : null }; }
  else { F.arm.r = { at: [tip[0], tip[1] - .03, tip[2]], by: 'tip', fdir: [f.x, .9, f.z], pdir: [f.z, 0, -f.x], w: up }; F.fing.r = ['grip', up]; F.hold = { r: up > .3 ? 'lighter' : null }; }
  F.after = () => { const lt = window.__W3chars.prop3('lighter').obj.userData.flame; if (lt) lt.visible = flame; };
  // сигарета в губах ставится после взгляда и поворота головы (post) — раньше ставилась до них и при повороте головы висела в воздухе у щеки
  // сигарета в уголке рта, под углом вбок и чуть вниз: торчащая прямо вперёд, на камеру, спереди читалась белой палочкой, висящей с губы (владелец играл и видел, 12 ноя)
  F.post = () => { if (!took) { C.root.updateWorldMatrix(true, true); const [m2, f2] = mouthOf(C), sx = -f2.z, sz = f2.x;
    propX('cig', o.S, [m2.x + f2.x * .004 + sx * .013, m2.y - .005, m2.z + f2.z * .004 + sz * .013], [f2.x * .62 + sx * .72, -.24, f2.z * .62 + sz * .72]); } };
  return [['mihalych', XF[0], 0, XF[2], XFR, F, null], ['temych', XE[0], 0, XE[1], XER, ethanProp(t, { look: [XF[0], 1.55, XF[2]] }), null]]; } };
function propX(name, scene, pos, dir) { const pr = window.__W3chars.prop3(name); if (pr.obj.parent !== scene) scene.add(pr.obj); pr.obj.position.set(...pos);
  pr.obj.quaternion.setFromUnitVectors(new T.Vector3(1, 0, 0), new T.Vector3(...dir).normalize()); pr.obj.visible = true; pr.C = null; }
// ---- ветка Б у стекла ----
// «И улыбнулась. Слишком широко»: она перестала плакать и смотрит не мигая; улыбка ползёт медленно и шире, чем бывает у людей, глаза в ней не участвуют
// (распахнуты, глазницы темнеют), голова ложится набок, на миг дёргается. Свет холла сквозь стекло снизу, отражение холла приглушено — за ней улица под дождём
SH.b_smile = { loc: 'lobby', pos: [.66, 1.56, 5.45], look: [.62, 1.52, 6.5], fov: 32, set: (o, t) => { breath(o, t); o.reflBoost = .15;
  const C = A.katyaSmile, k = seg(t, .5, 3.2); C.faceSets.creep = { smile: 2.05 * k, dead: Math.min(1, .35 + k) }; C.faceSet = 'creep';
  if (C.HU) C.HU.uSocket.value = lerp(.3, .95, k); C.tiltNow = .34 * seg(t, 1.1, 3.6) + (t > 2.75 && t < 2.86 ? .14 : 0);
  o.faceL.color.setHex(0xc8d4e0); o.faceL.position.set(LX + .05, .95, LZ - .55); o.faceL.distance = 2; o.faceL.intensity = .9 * k;   // свет снизу — лицо чужое
  return [['katyaSmile', LX, 0, LZ, PI, lilyGlass(t, 'stand', { look: 'cam' }), 'cam']]; } };
SH.b_back = { loc: 'lobby', pos: [.72, 1.62, 5.35], look: [.75, 1.35, 8.6], fov: 46, set: (o, t) => { o.reflBoost = .12;   // отражение холла приглушено: видно, что она уходит по улице, а не по холлу
  const C = A.katyaSmile; C.faceSets.creep = { smile: 2.05, dead: 1 }; C.faceSet = 'creep'; C.tiltNow = .34;
  return [backAway('katyaSmile', [LX, LZ], [LX + .4, LZ + 4.6], t, .42, EYE_IN)]; } };
SH.b_print = { loc: 'lobby', pos: [.6, 1.5, 4.95], look: [.41, 1.33, 6.05], fov: 42, set: (o, t) => { o.glassU.uPrint.value = 1; return []; } };
// ---- серверная: лист к камере, темнота за кругом, тянется на голос, держим, выполз ----
const FR = () => locR(FRANK[0], FRANK[1], PI + .55);
SH.srv_sheet = { loc: 'server', pos: [-3.3, 2.65, -2.6], look: [1.6, .8, 2.37], fov: 50, cctv: 1, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o); const R = FR();
  const P = frankSit(t, { sheet: true, look: [-3.3, 2.65, -2.6] }); P.sheet = [-3.3, 2.65, -2.6];
  // s08 «Он поднял к камере мятый лист»: лист поднимается с колен к груди на «поднял» (0,25–1,2 с) и дрожит в руках (а не висел перед ним с первого кадра)
  const up = seg(t, .25, 1.2), sh = .004 * Math.sin(t * 23) * up, y = lerp(.72, .98, up) + sh, zf = lerp(.3, .36, up);
  P.arm = { l: { at: R.p(.1, y, zf), by: 'pinch', fdir: R.v(-.2, .3, 1), pdir: R.v(-1, 0, 0), w: 1 }, r: { at: R.p(-.1, y + sh, zf), by: 'pinch', fdir: R.v(.2, .3, 1), pdir: R.v(1, 0, 0), w: 1 } };
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, P, [-3.3, 2.65, -2.6]]]; } };
const EIN = [2.28, 2.19];   // где Итан сидит на корточках в круге рядом с Фрэнком (справа — камеры его не загораживают)
SH.srv_in = { loc: 'server', pos: [.25, 1.2, 1.3], look: [1.7, .6, 2.52], fov: 48, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o);
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t, { look: [EIN[0], 1.0, EIN[1]] }), null],
    ['temych', EIN[0], 0, EIN[1], -.75, { clips: [['Crouch_Idle_Loop', t + .5, 1]], gest: 'r', arm: { l: kneeRest('l') }, fing: { l: ['relax', 1] }, look: FRANK_HEAD }, null]]; } };   // спрашивает — правая с колена чуть поднимается на ударных словах
// 04:30, обход серверной (explore): круг соли, кулер у двери, листы на стойке — от входа; и листы крупно
SH.srv_room = { loc: 'server', pos: [-.9, 1.4, .4], look: [1.6, .7, 2.8], fov: 62, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o);
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t, { look: [-.2, 1.3, 1.0] }), null]]; } };
SH.srv_sheets = { loc: 'server', pos: [2.05, 1.35, 2.25], look: [3.0, 1.2, 2.2], fov: 40, noRim: 1, set: (o, t) => { srvSet(o);
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t, { look: [2.05, 1.35, 2.25] }), null]]; } };
function srvDim(o) { srvSet(o); o.cold.intensity = .12; o.cold2.intensity = .08; o.red.intensity = .25; o.rim.intensity = .6; o.flickOn = false; }
SH.srv_dark = { loc: 'server', pos: [2.45, 1.05, 3.3], look: [-1.8, .9, -2.3], fov: 50, set: (o, t) => { srvDim(o);
  // d15 «Фрэнки… Сыночек…» (с ~6 с): Фрэнк подаётся корпусом на голос, Итан переводит глаза с темноты на него
  const F = frankSit(t, { look: [-1.6, 1.1, -2] }); F.spine = { to: [-1.6, .9, -2], k: .14 * seg(t, 6.0, 7.6) };
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, F, null], ['temych', EIN[0], 0, EIN[1], PI + .3, { clips: [['Crouch_Idle_Loop', t + .5, 1]], arm: kneeArms(), fing: { l: ['relax', 1], r: ['relax', 1] }, look: t < 6.7 ? [-1.6, 1.1, -2] : FRANK_HEAD }, null]]; } };
function frankReach(t, k, strain = 0) { const R = FR(), P = frankSit(t, { look: [-1.4, 1.0, -2.2], u: lerp(.45, .52, k) });   // не встаёт (нога в лубке): подаётся вперёд корпусом, сидя
  P.spine = { to: R.p(0, .75, 1.3), k: .34 * k }; P.arm = { r: { at: R.p(-.14, 1.0 + .05 * Math.sin(t * 3), .62), by: 'palm', fdir: R.v(0, .35, 1), pdir: R.v(0, -.5, .8), w: k } }; P.fing = { r: ['open', k] };
  if (strain) P.wig = [['spine_02', [1, 0, 0], Math.sin(t * 9) * .05 * strain], ['neck_01', [0, 1, 0], Math.sin(t * 5.3) * .1 * strain]];
  return P; }
SH.srv_reach = { loc: 'server', pos: [1.2, .82, 1.95], look: [1.95, .72, 2.95], fov: 50, face: { mihalych: 'plead' }, set: (o, t) => { srvDim(o);
  // d16–d17: «Мама?..» — и тянется на голос (с 0,8 с); «к краю круга» — Итан хватает его за протянутую руку у плеча (3,0–3,6), дальше мини-игра «Держи Фрэнка»
  A.temych.faceSet = t < 2.6 ? 'worry' : 'fear';
  const grab = { at: C => { const F = A.mihalych.bone, e = C.root.position, a = F.upperarm_l.getWorldPosition(new T.Vector3()), b = F.upperarm_r.getWorldPosition(new T.Vector3()), p = a.distanceTo(e) < b.distanceTo(e) ? a : b; return [p.x, p.y + .03, p.z]; },
    by: 'palm', w: .9 * seg(t, 3.0, 3.6), pole: [-.6, -1, -.2] };
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankReach(t, seg(t, .8, 2.6)), null], ['temych', EIN[0], 0, EIN[1], -.75, { clips: [['Crouch_Idle_Loop', t + .5, 1]], look: FRANK_HEAD, arm: { r: grab }, fing: { r: ['grip', .6 * seg(t, 3.3, 3.7)] } }, null]]; } };
// srv_reach — из темноты, куда он тянется: лицо Фрэнка и рука к нам, Итан за ним
// держит Фрэнка за плечи сзади (мини-игра): тот рвётся к голосу
SH.srv_hold = { loc: 'server', pos: [.35, 1.25, 2.07], look: [1.65, .75, 2.52], fov: 48, face: { mihalych: 'plead', temych: 'fear' }, set: (o, t) => { srvDim(o);
  const R = FR(), F = A.mihalych, sl = F.bone.upperarm_l.getWorldPosition(new T.Vector3()), sr = F.bone.upperarm_r.getWorldPosition(new T.Vector3()), eb = R.p(-.06, 0, -.46);
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankReach(t, 1, 1), null],   // Итан привстал за спиной (голова выше Фрэнка — видно, кто кого держит)
    ['temych', eb[0], 0, eb[2], PI + .55, { clips: [['Crouch_Idle_Loop', t + .5, 1]], pelvis: [0, .2, 0], spine: { to: R.p(0, 1.15, .1), k: .15 }, legs: 'plant',
      arm: { l: { at: [sl.x, sl.y + .05, sl.z], by: 'palm', fdir: R.v(0, -.2, 1), pdir: [0, -1, 0], w: 1 }, r: { at: [sr.x, sr.y + .05, sr.z], by: 'palm', fdir: R.v(0, -.2, 1), pdir: [0, -1, 0], w: 1 } },
      fing: { l: ['grip', .8], r: ['grip', .8] }, look: FRANK_HEAD }, null]]; } };
// k8a: выполз из круга — на четвереньках, руки по очереди вперёд; темнота смыкается
function crawlP(t, R) { const a = Math.sin(t * 2 * PI / 1.5);
  return { clips: [['Crouch_Idle_Loop', t, 1]], spine: { to: R.p(0, .2, 1.4), k: .8 }, pelvis: R.v(0, -.12, 0), legs: 'plant',
    arm: { l: { at: R.p(.2, .03, .62 + .1 * a), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: 1 }, r: { at: R.p(-.2, .03, .62 - .1 * a), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: 1 } },
    fing: { l: ['flat', 1], r: ['flat', 1] }, wear: { splint: SPLINT } }; }
function crawlFrank(t, from, to, speed = .24, per = 1.15) {   // ползёт: тело движется ровно, каждая ладонь встаёт на пол и стоит, пока тело не проползёт, потом переставляется вперёд
  const L = Math.hypot(to[0] - from[0], to[1] - from[1]), dx = (to[0] - from[0]) / L, dz = (to[1] - from[1]) / L, s = Math.min(L, t * speed);
  const x = from[0] + dx * s, z = from[1] + dz * s, ry = Math.atan2(dx, dz), R = locR(x, z, ry), side = R.v(1, 0, 0);
  const hand = (sd, off) => { const q = t / per + off, n = Math.floor(q), ph = q - n, at = m => Math.min(L + .5, m * per * speed + .5);
    let ps = at(n), lift = 0; if (ph > .62) { const k = (ph - .62) / .38; ps = lerp(at(n), at(n + 1), ease(k)); lift = Math.sin(k * PI) * .1; }
    return [from[0] + dx * ps + side[0] * .19 * sd, .025 + lift, from[1] + dz * ps + side[2] * .19 * sd]; };
  const pull = Math.sin(t / per * 2 * PI) * .5 + .5;   // рывок корпусом при каждой перестановке
  return { x, z, ry, P: { clips: [['Crouch_Fwd_Loop', t * .75, 1]], spine: { to: R.p(0, .1, 1.5), k: .8 + .08 * pull }, pelvis: R.v(0, -.2, -.04 * pull),
    arm: { l: { at: hand(1, 0), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: 1 }, r: { at: hand(-1, .5), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: 1 } },
    fing: { l: ['flat', 1], r: ['flat', 1] }, wear: { splint: SPLINT }, look: R.p(0, .25, 3) } };
}
SH.srv_crawl = { loc: 'server', pos: [2.55, 1.25, 3.3], look: [.9, .35, 1.5], fov: 50, set: (o, t) => { srvDim(o); o.candleK = Math.max(.2, 1.2 - t * .2) / 1.2;   // свеча тускнеет (раньше тик перезаписывал)
  const c = crawlFrank(t, [1.6, 2.35], [.35, .75]); return [['mihalych', c.x, .02, c.z, c.ry, c.P, null]]; } };
// ---- лестница и подвал ----
function stDim(o, k = .15) { o.bulbs.forEach(b => { b.li.intensity = b.base * k; b.sh.material.color.setHex(k > .3 ? 0xfff2d0 : 0x3a3428); }); }
SH.st_down = { loc: 'stairs', pos: [-.7, 6.1, -4.9], look: [-.7, 4.0, -1.2], fov: 60, torch: { x: .5, y: .62 }, set: (o, t, sh) => { stDim(o, .12);
  const k = clamp(t / 5.5, 0, 1), y = lerp(4.5, 3.0, k) + 1.6 + Math.abs(Math.sin(t * 4.2)) * .035, z = lerp(-4.7, -1.5, k);
  sh.pos = [-.7 + Math.sin(t * 2.1) * .03, y, z]; sh.look = [-.68, y - 1.6, z + 3.2]; return []; } };
// d22 (CAM 04): Итан ведёт Фрэнка по средней площадке — рука Фрэнка через его плечи, как у входа на рассвете
SH.st_up = { loc: 'stairs', pos: [1.25, 7.3, -.3], look: [-.1, 4.5, -5.0], fov: 58, cctv: 1, set: (o, t) => {
  // Итан ведёт Фрэнка: медленно (16 см/с), короткими шагами. Фрэнк хромает на левую (шина): на больную ногу наступает коротко и проседает,
  // корпусом висит на Итане, голова опущена; Итан под весом клонится к нему. Раньше оба шли одинаковым бодрым шагом, рука Фрэнка торчала над головами
  const k = clamp(t / 9, 0, 1), x = lerp(-.75, .7, k * k * (3 - 2 * k) * .5 + k * .5), y = 4.5;
  const E = A.temych, u = (x + .75) / (.95 * E.ss), cyc = u * 1.333, TAU = PI * 2;   // шаг короче обычного (.95 м на цикл вместо 1,3)
  const lame = u + .085 * Math.sin(TAU * u), dip = Math.max(0, Math.sin(TAU * u + .6)), bob = Math.sin(TAU * 2 * u) * .012;
  const RE = locR(x + .1, -5.35, PI / 2), fp = RE.p(-.43, 0, -.12), RF = locR(fp[0], fp[2], PI / 2);
  const over = { at: (() => { const a = RE.p(.1, 1.43, -.13); a[1] += y; return a; })(), by: 'palm', fdir: RE.v(.6, -.6, .3), pdir: RE.v(-.1, -1, .1), pole: [0, .4, -1], w: 1 };
  const lean = RE.p(0, 1.25, .05); lean[1] += y; const leanE = RF.p(0, 1.2, .1); leanE[1] += y;
  return [['temych', x + .1, y + Math.max(0, bob * .5), -5.35, PI / 2, { clips: [['Walk_Loop', cyc, .8], ['Idle_Loop', t, .2]], arm: { r: armWaist(RF, y) }, fing: { r: ['grip', .4] }, spine: { to: leanE, k: .1 }, look: [x + 1.6, y + 1.2, -5.4] }, null],
    ['mihalych', fp[0], y + Math.max(0, bob), fp[2], PI / 2, { clips: [['Walk_Loop', lame * 1.333 + .3, .72], ['Idle_Loop', t + 2, .28]], wear: { splint: SPLINT }, arm: { l: over }, fing: { l: ['grip', .5], r: ['grip', .35] },
      spine: { to: lean, k: .2 + .06 * dip }, look: [x + .9, y + .35, -5.3] }, null]]; } };
function bsDark(o, k = 0) { o.flickOn = false; o.bulbs.forEach(b => { b.li.intensity = b.base * k; b.bulb.material.color.setHex(k ? 0xffe8b8 : 0x1c1a14); }); }
SH.bs_dark = { loc: 'basement', pos: [0, 1.6, -.6], look: [0, 1.0, -14], fov: 56, set: (o, t) => { bsDark(o); return []; } };
SH.bs_walk = { loc: 'basement', pos: [0, 1.6, -3], look: [0, 1.1, -14], fov: 56, torch: { x: .5, y: .66 }, set: (o, t, sh) => { bsDark(o);
  const z = -3 - t * .85; sh.pos = [Math.sin(t * 2.1) * .03, 1.6 + (1 - Math.cos(t * 8.4)) * .014, z]; sh.look = [0, 1.0, z - 6]; return []; } };   // шаг — плавная волна (|sin| давал рывок на каждом шаге)
SH.bs_door = { loc: 'basement', pos: [.22, 1.55, -11.9], look: [0, .95, -14], fov: 48, torch: { x: .52, y: .55 }, set: (o, t) => { bsDark(o); return []; } };
// у двери: тусклая ближняя лампа, фонарь в левой руке Итана светит на дверь; когда дверь открыта — свет свечи из серверной
function bsLight(o, open, E) { bsDark(o); const b = o.bulbs[2]; b.li.intensity = b.base * .35; b.bulb.material.color.setHex(0x8a7a58); o.amb.intensity = .3;
  if (open) { o.candle.intensity = 1.6; o.candle.distance = 5; o.roomL.intensity = 2.2; }
  if (E) { o.etorch.intensity = 7; o.etorch.position.set(E[0] + .12, 1.12, E[1] - .2); o.etorch.target.position.set(0, .9, -14.6); o.etorch.target.updateMatrixWorld(); } }
const ETORCH = { l: 'torch' }, ETF = { l: ['grip', 1] };
// d07: три раза костяшками в железную дверь
function eKnockDoor(t, hits = [.55, .82, 1.09]) { let away = 1; for (const h of hits) away = Math.min(away, Math.abs(t - h) / .13);
  const e = hits[hits.length - 1], lift = seg(t, .1, .45) * (1 - seg(t, e + .26, e + .71)), z = -13.952 + .085 * Math.min(1, away);
  const R = locR(.14, -13.5, PI);   // левая рука с фонарём — у груди, светит в дверь (не у плеча вбок)
  return { clips: [['Idle_Torch_Loop', t + 1.3, 1]], legs: 'feet', arm: { r: { at: [-.08, 1.32, z], by: 'knuck', fdir: [-.1, .15, -1], pdir: [-.8, -.6, 0], w: lift },
    l: { at: R.p(.13, 1.08, .3), by: 'palm', fdir: R.v(-1, .15, .2), pdir: R.v(.1, 1, -.15), w: 1 } }, fing: { r: ['fist', lift], l: ['grip', 1] }, hold: ETORCH, look: [-.1, 1.3, -14] }; }
function torchFromHand(o, tgt) {   // свет фонаря — из руки Итана в цель (пятно на двери там, куда смотрит фонарь), а не из точки внутри руки
  const E = A.temych; if (!E || !E.bone || !E.bone.hand_l) return; const hp = E.bone.hand_l.getWorldPosition(new T.Vector3()), tv = new T.Vector3(...tgt), d = tv.clone().sub(hp).normalize();
  o.etorch.intensity = 7; o.etorch.position.copy(hp).addScaledVector(d, .14); o.etorch.target.position.copy(tv); o.etorch.target.updateMatrixWorld();
  // 20 ноя (тестер: «кадр почти чёрный, ни фонаря, ни луча»): фонарь в 15 см от двери даёт пятно с ладонь — его закрывает сама рука. Добавлен отсвет от двери:
  // тёплый рассеянный свет между дверью и грудью Итана (так светит в упор освещённое железо) — видно дверь, руку и лицо снизу
  o.faceL.color.setHex(0xffe2b8); o.faceL.distance = 3.2; o.faceL.intensity = CINE ? 1.1 : 1.6; o.faceL.position.set(tv.x * .5 + hp.x * .5, tv.y + .12, tv.z + .22); }
SH.bs_knock = { loc: 'basement', pos: [-.85, 1.55, -12.55], look: [.05, 1.2, -13.95], fov: 46, set: (o, t) => { bsLight(o, false); torchFromHand(o, [.12, 1.0, -13.97]); return [['temych', .14, 0, -13.5, PI, eKnockDoor(t), null]]; } };
// 22:05: просто «тук-тук» — два раза (на три стука Фрэнк открыл бы); изнутри отвечают тремя
// 20 ноя (тестер: «постучал и застыл на 8 секунд, пока идёт текст про ответные удары»): после стука опускает руку и слушает, наклонив голову к двери; на каждый
// из трёх ответных ударов (3,3 · 4,15 · 5,0 — как в story.js) вздрагивает, после первого отдёргивает голову и отступает корпусом, фонарь в руке дрожит
SH.bm_knock = { loc: 'basement', pos: [-.85, 1.55, -12.55], look: [.05, 1.2, -13.95], fov: 46, face: { temych: 'fear' }, set: (o, t) => { bsLight(o, false); torchFromHand(o, [.12, 1.0, -13.97]);
  const P = eKnockDoor(t, [.55, .82]), R = locR(.14, -13.5, PI); let jolt = 0; for (const h of [3.3, 4.15, 5.0]) jolt = Math.max(jolt, Math.exp(-Math.pow((t - h - .08) / .12, 2)));
  const hear = seg(t, 1.5, 2.3) * (1 - seg(t, 3.3, 3.6)), back = seg(t, 3.35, 3.9), tr = back * (Math.sin(t * 37) * .004 + Math.sin(t * 23) * .003);
  A.temych.faceSet = t < 3.3 ? 'worry' : 'fear';
  P.spine = { to: back > 0 ? R.p(0, 1.5, -1) : [-.1, 1.3, -14], k: .1 * hear + .12 * back };
  P.listen = hear > .01 ? { k: hear, yaw: .3, pitch: .08, tilt: .1 } : null;
  P.wig = [['spine_03', [1, 0, 0], -.07 * jolt], ['neck_01', [1, 0, 0], -.1 * jolt], ['clavicle_l', [0, 0, 1], .05 * jolt], ['clavicle_r', [0, 0, 1], -.05 * jolt]];
  P.arm.l = Object.assign({}, P.arm.l, { at: R.p(.13 + tr, 1.08 + tr, .3 - .06 * back) }); P.br = [1 + .6 * back, 1 + .5 * back];
  P.look = t > 5.6 ? [-.38, 1.02, -13.93] : [-.1, 1.3, -14];
  return [['temych', .14, 0, -13.5, PI, P, null]]; } };
// d08–d09: засов изнутри, дверь открывается в коридор; Фрэнк на колене в круге у порога, рукой толкает дверь
const BF = [0, -14.85];
SH.bs_open = { loc: 'basement', pos: [-.9, 1.5, -12.35], look: [.15, .95, -14.6], fov: 52, face: { mihalych: 'worry' }, set: (o, t) => {
  const k = ease(clamp((t - .25) / .9, 0, 1)); o.sdoor.rotation.y = 1.35 * k; o.sdoor.updateMatrixWorld(true); bsLight(o, k > .1, [-.25, -13.05]);
  const dp = o.sdoor.localToWorld(new T.Vector3(-.8, 1.0, -.03)), reach = 1 - seg(t, .7, 1.0);
  return [['mihalych', BF[0], .045, BF[1], 0, { clips: [['Fixing_Kneeling', .8, 1]], wear: { splint: SPLINT }, talk: 1, arm: { r: { at: [dp.x, dp.y, dp.z], by: 'palm', fdir: [0, 1, 0], pdir: [0, 0, 1], w: reach } }, fing: { r: ['flat', reach] }, look: [.3, 1.6, -13.2] }, null],
    shuffle('temych', [-.12, -13.55], [-.25, -13.05], t, .12, .5, PI, { look: [0, .9, -14.8], hold: ETORCH, fing: ETF })]; } };   // створка (петли справа, x .47, полотно .94) метёт четверть круга к Итану: он отступает за её край, пока она не дошла (у мокапа корпус ровно над точкой ног, у Quaternius был на 10 см позади — створка проходила сквозь голову и кисть)
SH.bs_frank = { loc: 'basement', pos: [-.24, 1.0, -13.45], look: [.06, .5, -14.9], fov: 48, face: { mihalych: 'worry' }, set: (o, t) => { o.sdoor.rotation.y = 1.35; bsLight(o, true, [-.05, -13.2]); o.etorch.intensity = CINE ? 3 : 5; o.faceL.position.set(.3, 1.0, -14.1); o.faceL.intensity = CINE ? .45 : .8; o.faceL.color.setHex(0xc9cfd8);   // лицо Фрэнка не пересвечено (в «Кино» было рыжим)
  return [['mihalych', BF[0], FRANKY, BF[1], 0, frankSit(t, { look: [-.24, 1.0, -13.45] }), null]]; } };
SH.bs_no = { loc: 'basement', pos: [-.22, 1.58, -13.1], look: [.05, .72, -14.9], fov: 46, face: { mihalych: 'fear' }, set: (o, t) => { o.sdoor.rotation.y = 1.35; bsLight(o, true, [-.05, -13.2]); o.etorch.intensity = CINE ? 3 : 5; o.faceL.position.set(.3, 1.1, -14.1); o.faceL.intensity = CINE ? .45 : .8; o.faceL.color.setHex(0xc9cfd8);   // лицо Фрэнка не пересвечено (в «Кино» было рыжим)
  return [['mihalych', BF[0], FRANKY, BF[1], 0, frankSit(t, { no: 1, look: [-.22, 1.58, -13.1] }), null]]; } };
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
// «Ты не спустился», последняя сцена. Раньше — один сверхкрупный план: кулак с карандашом во весь экран над закрытым перевязанным журналом, хотя текст говорит
// «открыл журнал на последней странице и дописал строчку» (владелец, 12 ноя: «выглядит, как будто школьник сделал»). Теперь два плана в одном кадре:
// до 5,3 с — средний, от лампы: Итан пишет в раскрытом журнале (кисть мелкая, как и должна быть); дальше — страница сверху: обе строчки дописаны, карандаш лежит рядом, рук в кадре нет
SH.jr_write = { loc: 'lobby', pos: [6.05, 1.24, .12], look: [5.3, .93, -.5], fov: 40, set: (o, t, sh) => {
  o.book.visible = false; o.note.visible = o.noteCv.visible = true;   // журнал раскрыт всегда (на этой ветке его не развязывали — кадр показывал перевязанную книгу)
  o.note.material = mat(0xffffff, TX.note2, { decal: 1 }); const mk = noteMask(o), lines = [[.5, 2.6, 228], [2.9, 5.0, 245]]; let tipPx = null; const close = t > 5.3;
  lines.forEach(([a, b, bl], i) => { const k = clamp((t - a) / (b - a), 0, 1), x = 200 + 172 * k; maskTo(mk[i], x, 374, bl - 13, bl + 5); if (t >= a - .3 && t <= b + .2) tipPx = [x + Math.sin(t * 27) * 1.5, bl - 4 + Math.sin(t * 33) * 3]; });
  const tip = tipPx ? logPt(tipPx[0], tipPx[1], .001) : logPt(374, 250, .02), dir = nrm([-.5, .68, -.55]), pin = [tip[0] + dir[0] * .05, tip[1] + dir[1] * .05, tip[2] + dir[2] * .05];   // 20 ноя (тестер: «ручка зажата вертикально, как стамеска»): наклон 47° к себе и вправо, пальцы в 5 см от кончика (было 28° и 8,5 см)
  const pg = logPt(286, 236, 0), k = seg(t, 5.3, 9.5);
  if (close) { sh.pos[0] = pg[0] + .01; sh.pos[1] = 1.2 - .06 * k; sh.pos[2] = pg[2] - .17; sh.look[0] = pg[0]; sh.look[1] = .8; sh.look[2] = pg[2] + .01; sh.fov = 30; }
  else { sh.pos[0] = 6.05 - .06 * seg(t, 0, 5.3); sh.pos[1] = 1.24; sh.pos[2] = .12; sh.look[0] = 5.3; sh.look[1] = .93; sh.look[2] = -.5; sh.fov = 40; }
  const P = eRead(t, null, 1);
  if (!close) { P.arm.r = { at: pin, by: 'pinch', fdir: [.1, -.6, 1], pdir: [-.5, -.5, .2], w: 1 }; P.fing.r = ['write', 1]; P.look = tip; P.after = () => window.__W3chars.propAim('pen', o.S, tip, dir); }
  else { const a = logPt(330, 262, .004), d = nrm([-.9, 0, .25]); window.__W3chars.propAim('pen', o.S, a, d); }   // карандаш положен под строчкой
  return close ? [] : [['temych', SEAT[0], 0, SEAT[1], 0, P, null]]; } };
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
  const r = seg(t, t0, t0 + .6), got = t > t0 + .6, back = seg(t, t0 + 1.0, t0 + 1.7), chest = R2C();   // к груди — в локоть, а не на вытянутой руке
  const at = lin([R2H[0], R2H[1] + .036, R2H[2]], chest, back);
  return { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', spine: { to: R2H, k: .1 * r * (1 - back) },
    arm: { r: { at, by: 'palm', fdir: [0, -.2, -1], pdir: [0, -1, 0], w: got ? 1 : r } },   // до протягивания рука висит как в клипе, потом плавно к яблоку
    fing: { r: [got ? 'cup' : 'open', got ? 1 : r] }, hold: got ? { r: 'apple' } : null, look: back < .5 ? R2H : [R2G[0], 1.6, R2G[1]] };
}
// 19 ноя: камера едет за Итаном вправо-вперёд — раньше он подходил по линии камеры и к «Держи яблоко» целиком закрывал Гаса спиной
SH.r2_meet = { loc: 'corridor', pos: [.85, 1.6, -5.3], look: [-.12, 1.25, -9.5], fov: 48, face: { cleaner: 'warm' }, set: (o, t, sh) => { setLamps(o, 7); o.cleanSet(CLEAN);
  { const k = seg(t, .3, 2.9); sh.pos = lin([.85, 1.6, -5.3], [1.02, 1.6, -7.35], k); sh.look = lin([-.12, 1.25, -9.5], [-.22, 1.32, -9.45], k); }
  const k = 1 - seg(t, .6, 1.8), E = walker('temych', [[.35, -6.0], [.1, -7.6], R2E], t, .95, { look: [R2G[0], 1.6, R2G[1]] }, PI);
  return [E, ['cleaner', R2G[0], 0, R2G[1], lerp(.2, 0, 1 - k), Object.assign(mopGus(o, R2G[0], R2G[1], lerp(.2, 0, 1 - k), t, k), { look: [E[1], 1.62, E[3]] }), null]]; } };
SH.r2_apple = { loc: 'corridor', pos: [1.15, 1.45, -9.1], look: [-.15, 1.3, -9.2], fov: 54, face: { cleaner: 'warm' }, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN);
  window.__W3chars.propAim('mop', o.S, MOPWALL, MOPWALL_D);   // швабра прислонена к стене
  return [['cleaner', R2G[0], 0, R2G[1], 0, gApple(t, .1, 1.45), null], ['temych', R2E[0], 0, R2E[1], PI, eApple(t, .85), null]]; } };
SH.r2_talk = { loc: 'corridor', pos: [.98, 1.62, -8.3], look: [-.17, 1.48, -9.45], fov: 40, face: { cleaner: 'warm' }, set: (o, t) => { setLamps(o, 7); o.cleanSet(CLEAN);
  window.__W3chars.propAim('mop', o.S, MOPWALL, MOPWALL_D);
  const R = locR(R2E[0], R2E[1], PI);
  return [['cleaner', R2G[0], 0, R2G[1], 0, { clips: [['Idle_Loop', t + 2, 1]], talk: 1, legs: 'feet', look: [R2E[0], 1.62, R2E[1]] }, null], r2Ethan(t)]; } };
// 20 ноя (тестер: «голова Итана закрывает Гаса», «головы срезаны»): камеры разговора — из-за плеча Итана сбоку (x ≈ 1), а не почти ему в затылок; r2_apple — шире и выше
// ---- 01:00, разговор с Гасом (сцена round2): Итан спрашивает (Гас слушает), Гас отвечает; на «семьсот седьмой» он оглядывается на дверь ----
// база — стойка: «говорит руками» включается только под его голос (talk), молча Гас не жестикулирует; вопрос → ответ — одна и та же стойка без рывка
const R2POSE = (t, talk, look) => ({ clips: [['Idle_Loop', t + 2, 1]], talk: 1, legs: 'feet', look: look || [R2E[0], 1.62, R2E[1]] });
function r2Set(o) { setLamps(o, 7); o.cleanSet(CLEAN); window.__W3chars.propAim('mop', o.S, MOPWALL, MOPWALL_D); }
const r2Ethan = t => ['temych', R2E[0], 0, R2E[1], PI, { clips: [['Idle_Loop', t + 1.3, 1]], talk: 1, legs: 'feet', arm: { r: { at: R2C(), by: 'palm', fdir: [0, -.2, -1], pdir: [0, -1, 0], w: 1 } }, fing: { r: ['cup', 1] }, hold: { r: 'apple' }, look: [R2G[0], 1.6, R2G[1]] }, null];   // спрашивает — свободная левая говорит, в правой яблоко
SH.gs_ask = { loc: 'corridor', pos: [.98, 1.62, -8.3], look: [-.17, 1.48, -9.45], fov: 40, face: { cleaner: 'calm' }, set: (o, t) => { r2Set(o); return [['cleaner', R2G[0], 0, R2G[1], 0, R2POSE(t, false), null], r2Ethan(t)]; } };
SH.gs_ans = { loc: 'corridor', pos: [.98, 1.62, -8.3], look: [-.17, 1.48, -9.45], fov: 40, face: { cleaner: 'calm' }, set: (o, t) => { r2Set(o); return [['cleaner', R2G[0], 0, R2G[1], 0, R2POSE(t, true), null], r2Ethan(t)]; } };
SH.gs_sad = { loc: 'corridor', pos: [.92, 1.62, -8.5], look: [-.2, 1.5, -9.68], fov: 36, face: { cleaner: 'sad' }, set: (o, t) => { r2Set(o); return [['cleaner', R2G[0], 0, R2G[1], 0, R2POSE(t, true), null], r2Ethan(t)]; } };
// 20 ноя (тестер: «голова вывернута на 180°, как у совы»): 707-я у Гаса за спиной (152° от взгляда на Итана). Теперь он оборачивается, как человек: переступает
// и разворачивается боком (95°), остальное — шея и глаза; потом так же шагами возвращается к Итану
const GS7_RY = -1.66;
SH.gs_707 = { loc: 'corridor', pos: [.92, 1.62, -8.5], look: [-.2, 1.5, -9.68], fov: 36, face: { cleaner: 'calm' }, set: (o, t) => { r2Set(o); const k = seg(t, 1.2, 2.2) * (1 - seg(t, 5.2, 6.2));
  const back = t >= 4.6, TS = back ? turnSteps(R2G[0], R2G[1], GS7_RY, 0, t, 4.9, 6.2) : turnSteps(R2G[0], R2G[1], 0, GS7_RY, t, .9, 2.2);
  const P = R2POSE(t, true, lin([R2E[0], 1.62, R2E[1]], [-1.3, 1.45, -11.8], k));
  const fw = back ? seg(t, 4.75, 4.9) * (1 - seg(t, 6.2, 6.45)) : seg(t, .75, .9) * (1 - seg(t, 2.2, 2.45)); if (fw > 0) { P.feet = TS.feet; P.feet.l.w = P.feet.r.w = fw; }
  return [['cleaner', R2G[0], 0, R2G[1], TS.ry, P, null], r2Ethan(t)]; } };
// ---- 21:06, фото под стеклом (fb03): Новый год на седьмом — Фрэнк, Гас и Марта; вспышка в лицо, гирлянда. Движок делает из кадра старый снимок (fx 'photo') ----
SH.ny_photo = { loc: 'corridor', pos: [.05, 1.52, -5.35], look: [0, 1.32, -7.7], fov: 50, face: { mihalych: 'photo', cleaner: 'photo', martha: 'photo' }, set: (o, t) => { setLamps(o, 7); o.cleanSet(); o.garland.visible = true;
  o.faceL.position.set(.05, 1.6, -5.2); o.faceL.color.setHex(0xfff2dc); o.faceL.distance = 7; o.faceL.intensity = 2.4;
  const st = (dx) => ({ clips: [['Idle_Loop', 2.4 + dx, 1]], legs: 'feet' });
  return [['mihalych', -.6, 0, -7.68, .12, st(.3), [.05, 1.52, -5.35]], ['cleaner', 0, 0, -7.78, 0, st(.9), [.05, 1.52, -5.35]], ['martha', .55, 0, -7.66, -.1, st(1.6), [.05, 1.52, -5.35]]]; } };
// ---- 22:05, обход: дверь серверной (сцена round1) ----
// обход: дверь серверной целиком — ручка, полотно, щель под дверью (точки обхода стоят на них)
SH.bm_view = { loc: 'basement', pos: [.15, 1.7, -10.71], look: [0, 1.1, -14], fov: 48, torch: { x: .5, y: .56 }, set: (o, t) => { bsDark(o); return []; } };
// bm02: у самого пола — щель под дверью, за ней по всему порогу белая полоса соли
SH.bm_under = { loc: 'basement', pos: [.06, .07, -13.52], look: [0, .025, -14.1], fov: 24, torch: { x: .5, y: .6 }, set: (o, t) => { bsDark(o); o.sdoor.position.y = .055; o.slit.visible = o.glow.visible = false;
  o.roomL.intensity = 1.4; o.roomL.distance = 3; o.roomL.position.set(-.25, .2, -14.55); return []; } };   // дверь чуть приподнята — щель видна; за ней свеча освещает валик соли
// bm04: «свет под дверью дрогнул»: за дверью мимо щели проходит тень
SH.bm_slit = { loc: 'basement', pos: [.12, .78, -12.85], look: [0, .06, -14], fov: 34, torch: { x: .5, y: .45 }, set: (o, t) => { bsDark(o); const k = clamp((t - 2.3) / .8, 0, 1);
  o.slitShadow.visible = k > 0 && k < 1; o.slitShadow.position.x = lerp(-.5, .5, k); o.glow.scale.set(1 - .45 * Math.sin(PI * k), 1, 1); return []; } };
// bm05 / n05: обратно к лестнице (фонарь, шаги)
SH.bm_back = { loc: 'basement', pos: [0, 1.6, -12.4], look: [0, 1.1, -2], fov: 56, torch: { x: .5, y: .66 }, set: (o, t, sh) => { bsDark(o);
  const z = -12.4 + t * .85; sh.pos = [Math.sin(t * 2.1) * .03, 1.6 + Math.abs(Math.sin(t * 4.2)) * .03, z]; sh.look = [0, 1.0, z + 6]; return []; } };
// ---- камеры смены (пульт в будке). Что на них сейчас — curProps.sh (движок: пульт, кто где, странности) ----
// странности (s.*): cat, door01, streetOff, lily01 · wet, frank02, hallDark, palm · lift/liftOpen, lift13, clockSpin, palmMove ·
// stDark, guard04 · gus, l7, door7, bucket7 · l10, door10, chair10 · mOff, sdoor · (11 ноя) handprint · selfStare · liftBoth · lily04 · mopAlone · frank10, gusTwin · crawl_m2. Помехи («он») рисует движок поверх кадра.
const SHS = () => (curProps && curProps.sh) || {};
function irOn(o, p, k = 1.5) { o.ir = 1; o.irL.position.set(p[0], p[1], p[2]); o.irL.intensity = k; }   // темно — камера переходит в ИК: подсветка у объектива, ч/б
function shClock(o, s, t) { if (s.clk == null) return; const v = s.clockSpin ? s.clk - t * 40 : s.clk; o.setClock(Math.floor(v / 60), ((v % 60) + 60) % 60); }   // часы холла идут по времени смены (или крутятся назад)
const STILL = (clip = 'Idle_Loop', at = 1.2) => ({ clips: [[clip, at, 1]], legs: 'feet', still: 1 });   // стоит и не шевелится (странность на камере: не дышит, не моргает телом)
SH.cam01 = { loc: 'lobby', pos: [-1.2, 3.3, 8.3], look: [2.6, .6, 8.4], fov: 74, cctv: 1, set: (o, t) => { const s = SHS(); o.rain.material.opacity = .18;
  o.cat.visible = !!s.cat; if (s.cat) { o.catHead.rotation.y = Math.sin(t * .4) * .5; o.catTail.rotation.z = Math.sin(t * 1.7) * .3; }
  if (s.door01) { leafAng(o, 0, 1.15); leafAng(o, 1, .35); }   // вход распахнут в дождь
  if (s.streetOff) { o.street.intensity = 0; o.streetBulb.visible = false; o.canopy.intensity = 0; o.canopyLamp.visible = false; }   // погас свет над входом
  if (s.handprint) o.glassU.uPrint.value = 1;   // отпечаток ладони на стекле входа
  if (s.lilyDoor) return [['katya', .35, 0, 6.45, PI + .25, STILL('Idle_Loop', 1.6), [-1.2, 3.3, 8.3]]];   // у самой двери, смотрит в камеру
  return s.lily01 ? [['katya', 8.6, 0, 11.55, -1.9, STILL(), [-1.2, 3.3, 8.3]]] : []; } };   // z 11,55 — на дороге за бордюром (было 11,2: стояла ступнями в бордюре на 8 см)
SH.cam02 = { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, cctv: 1, set: (o, t) => { const s = SHS();
  if (s.palm) { o.palmShadow.position.x = -3.95 + .9 * s.palm; o.palmShadow.scale.setScalar(1 + .15 * s.palm); }
  o.wetSteps.visible = !!s.wet; if (s.wet) { o.wetM.opacity = .6; o.wetM.color.setHex(0xa8b8c4); }   // на камере мокрые следы блестят
  if (s.hallDark) { o.ceilOn.color.setHex(0x30322e); o.l1.intensity = .25; o.l3.intensity = .2; o.l4.intensity = .2; o.flickOn = false; }
  setLift(o, o.lifts[0], s.liftOpen ? 1 : 0, String(s.lift || '1')); o.cabLight.intensity = s.liftOpen ? 2.4 : 0; if (s.lift13) setLift(o, o.lifts[1], 0, '13'); if (s.liftBoth) setLift(o, o.lifts[1], 1, '1');   // лифты видно и отсюда — то же состояние, что на CAM 03
  shClock(o, s, t); if (s.palmMove) { o.palm.position.set(-.82, 0, -5.3); o.palmShadow.visible = false; }
  if (s.selfStare && !s.noEthan) return [['temych', 3.4, 0, 1.6, .55, STILL('Idle_Loop', 1.4), [7.45, 3.85, 5.45]]];   // «ты» не сидишь в будке, а стоишь в холле и смотришь в камеру
  const A2 = s.noEthan ? [] : [['temych', SEAT[0], 0, SEAT[1], 0, s.ethR > 0 ? eRadioK(t, s.ethR) : { clips: [['Sitting_Idle_Loop', t + 2, 1]], look: [4.5, 1.18, .12] }, null]];   // отвечает Гасу по рации — рация у рта
  if (s.frank02) A2.push(['mihalych', -3.6, 0, -4.4, .84, STILL('Idle_Loop', 2.1), [7.45, 3.85, 5.45]]);   // в глубине холла, лицом к камере
  return A2; } };
SH.cam03 = { loc: 'lobby', pos: [.2, 3.2, -1.6], look: [-1.5, 1.25, -6], fov: 58, cctv: 1, set: (o, t) => { const s = SHS();
  setLift(o, o.lifts[0], s.liftOpen ? 1 : 0, String(s.lift || '1')); o.cabLight.intensity = s.liftOpen ? 2.4 : 0;
  if (s.lift13) setLift(o, o.lifts[1], 0, '13');
  if (s.liftBoth) setLift(o, o.lifts[1], 1, '1');   // второй тоже открыт — в нём темно
  shClock(o, s, t);
  if (s.palmMove) { o.palm.position.set(-.82, 0, -5.3); o.palmShadow.visible = false; }
  o.wetSteps.visible = !!s.wet; if (s.wet) { o.wetM.opacity = .6; o.wetM.color.setHex(0xa8b8c4); }
  if (s.hallDark) { o.ceilOn.color.setHex(0x30322e); o.l1.intensity = .25; o.l3.intensity = .2; o.l4.intensity = .2; o.flickOn = false; }   // холл один — то же, что видно на CAM 02
  return s.frank02 ? [['mihalych', -3.6, 0, -4.4, .84, STILL('Idle_Loop', 2.1), [7.45, 3.85, 5.45]]] : []; } };
SH.cam04 = { loc: 'stairs', pos: [1.25, 7.3, -.3], look: [-.1, 4.5, -5.0], fov: 62, cctv: 1, set: (o, t) => { const s = SHS(); o.setFloor(1, 4); o.setFloor(0, 3); o.setFloor(2, 5);
  if (s.stDark) { stDim(o, 0); irOn(o, [1.2, 7.1, -.5], 1.7); } else if (!s.st) o.bulbs.forEach(b => { b.li.intensity = b.base * .4; });
  if (s.lily04) return [['katya', .25, 4.5, -5.3, PI, STILL('Idle_Loop', 2.3), null]];   // на площадке, спиной к камере, лицом в стену
  return s.guard04 ? [['smenshik', -.3, 4.5, -5.5, PI, STILL('Idle_Loop', .7), null]] : []; } };
// Гас на камере 07 говорит с Итаном по рации (доли от движка, radioTalk): m — ещё моет, b — швабра у бока, h — рация в руке, u — рация у рта.
// Перестал мыть → отставил швабру к боку → левой снял рацию с нагрудника → поднёс ко рту, пока говорит; Итан отвечает — рация у груди; потом всё обратно
const _wv = new T.Vector3();
function wornPt(C, [bone, off]) { const b = C && C.bone && C.bone[bone]; if (!b) return null; b.updateWorldMatrix(true, false); _wv.set(off[0], off[1], off[2]).applyMatrix4(b.matrixWorld); return [_wv.x, _wv.y, _wv.z]; }
function gusRadioCam(o, t, x, z, ry, g, km = 1) {   // km — насколько он мыл, когда зашипела рация (распорядок: мог как раз переходить)
  if (g.b <= 0) { const P = mopGus(o, x, z, ry, t, Math.min(g.m, km)); P.wear = { radio: RADIO_G }; return P; }
  const R = locR(x, z, ry), chest = bodyAt(R, 'spine_03', G_S3, [.07, 1.25, .15]), mouth = bodyAt(R, 'Head', G_HEAD, [.05, 1.46, .15]), clip = wornPt(A.cleaner, RADIO_G) || R.p(.07, 1.24, .17);
  const P = gusStand(o, t, ry, R.p(0, 1.4, 3), null, g.b, [x, z], ry);
  if (g.h > 0) { const w = seg(g.h, 0, .45), inH = g.h >= .45, k1 = seg(g.h, .45, 1), at = inH ? (C => lin(lin(clip, chest(C), k1), mouth(C), g.u)) : clip;
    P.arm.l = { at, by: 'palm', fdir: radioF('l', R.v(-.15, 1, .05), R.v(.05, -.1, -1)), pdir: R.v(.05, -.1, -1), w }; P.fing.l = ['grip', w];
    if (inH) { delete P.wear; P.hold = { l: 'radio' }; } }
  P.talk = g.u > .6 ? 1 : 0;
  return P;
}
// Гас на камере 07 моет как живой (часы швабры tg): участок перед собой 14 с → перестаёт → отступает назад (не по мокрому; шаги назад — shuffle, швабра
// едет с ним) → моет следующий; пройдя полосу из трёх участков, идёт вперёд к началу. Раньше он мыл, «уезжая» вдоль коридора на ±1,5 м без шагов
// (ступни скользили). Полоса — посередине коридора между ведром и табличкой; ближе 1,4 м к 707 он не подходит (дверь не трогает — сюжет)
const G7Z = [-8.65, -9.5, -10.35], G7X = -.1, G7R = .2, G7MOP = 14;
function gusRoutine(tg) {
  const A1 = G7MOP + .9 + 1.6 + .9, A3 = G7MOP + .9 + 2.6 + .9, P = 2 * A1 + A3, u = ((tg % P) + P) % P, i = u < A1 ? 0 : u < 2 * A1 ? 1 : 2;
  let v = u - i * A1; const z0 = G7Z[i], z1 = G7Z[(i + 1) % 3], mv = i < 2 ? 1.6 : 2.6;
  if (v < G7MOP) return { z: z0, k: 1 };   // моет
  v -= G7MOP; if (v < .9) return { z: z0, k: 1 - v / .9 };   // перестаёт: руки к стоящей швабре
  v -= .9; if (v < mv) return { z: z0, k: 0, move: [z0, z1, v, mv] };   // переходит
  return { z: z1, k: (v - mv) / .9 };   // снова моет
}
// ---- Гас живёт своей ночью (владелец, 18 ноя: «уборщик должен иногда новое — заходить в двери комнат, проводить там время, выходить, сесть посидеть, отдохнуть»).
// Распорядок случайный на каждый час: моет в разных местах коридора; ставит швабру к стене и уходит в кабинет 705 (дверь справа, внутри горит свет), потом выходит;
// садится на стул у стены передохнуть (иногда поднимает голову к камере); стоит, разминает спину и оглядывается. Пока он моет — прежняя анимация (gusRoutine/mopGus).
// gusLife возвращает актёров кадра или null — «моет сейчас» (тогда работает старый код, в том числе разговор по рации).
// 20 ноя (владелец: «Гас силой мысли открывает двери»): всё делает руками. Швабру ставит к стене в двух шагах от себя, а не «переносит взглядом» за метр;
// ручку двери нажимает левой и толкает створку, входя; выходя, берётся правой за наружную ручку (открытая створка стоит к проходу именно этой стороной) и
// тянет дверь за собой, пока она не закроется у него за спиной; к стулу идёт уже без швабры.
const GL = { door: [-.85, -9.1], sill: [-1.3, -8.88], inside: [-2.3, -8.78], chairC: [-1.0, -7.26], seat: [-.76, -7.26], chairStand: [-.45, -7.26], chairPark: [-.72, -7.72], chairVia: [-.4, -7.62],
  mopDoor: [-1.08, MOP_Y, -9.6], mopChair: [-1.08, MOP_Y, -8.03] };   // у стула швабра — на простенке между стулом и дверью 705 (с другой стороны стоит табличка «мокрый пол»)   // швабра у стены — сбоку от места, где он стоит (правая рука дотягивается: 0,6 м)
// ручка двери 705 в мире при угле створки th (петли — z −8,225, створка открывается внутрь кабинета): s = 1 — со стороны коридора, −1 — изнутри
const H5 = (th, s = 1) => [-1.27 + s * .08 * Math.cos(th) - .785 * Math.sin(th), .99, -8.225 - s * .08 * Math.sin(th) - .785 * Math.cos(th)];
const pwl = (x, K) => { if (x <= K[0][0]) return K[0][1]; for (let i = 1; i < K.length; i++) if (x <= K[i][0]) return K[i - 1][1] + (K[i][1] - K[i - 1][1]) * (x - K[i - 1][0]) / (K[i][0] - K[i - 1][0]); return K[K.length - 1][1]; };
const angTo = (a, b, k) => { let d = (b - a) % (2 * PI); if (d > PI) d -= 2 * PI; if (d < -PI) d += 2 * PI; return a + d * k; };
// ходьба по ломаной (через проём): k 0…1 — доля пути; курс у излома поворачивается плавно. → [x, z, курс, пройдено м]
function polyWalk(pts, k) {
  const L = []; let tot = 0; for (let i = 1; i < pts.length; i++) { const d = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); L.push(d); tot += d; }
  let s = clamp(k, 0, 1) * tot, i = 0; while (i < L.length - 1 && s > L[i]) { s -= L[i]; i++; }
  const q = L[i] ? s / L[i] : 0, a = pts[i], b = pts[i + 1], hd = n => Math.atan2(pts[n + 1][0] - pts[n][0], pts[n + 1][1] - pts[n][1]); let ry = hd(i);
  if (i < L.length - 1 && L[i] - s < .25) ry = angTo(ry, hd(i + 1), .5 * (1 - (L[i] - s) / .25)); else if (i > 0 && s < .25) ry = angTo(hd(i - 1), ry, .5 + .5 * s / .25);
  return [a[0] + (b[0] - a[0]) * q, a[1] + (b[1] - a[1]) * q, ry, clamp(k, 0, 1) * tot];
}
let gusPlanA = null, gusPlanT = -1;
function gusPlanMake() {
  const R = Math.random, P = []; let t = 0, at = [G7X, G7Z[0]];
  let bz = G7Z[0] - BK_DZ;   // ведро (19 ноя, владелец: «проработай ведро — неестественно моет»): стоит у участка, катится с Гасом к следующему
  const add = (k, d, x) => { P.push(Object.assign({ k, t0: t, d, bz0: bz, bz1: bz }, x)); t += d; };
  const unpark = b => { if (P.length && P[P.length - 1].k === 'take') add('turnc', .9, { at: at.slice(), ry0: -PI / 2, ry1: Math.atan2(b[0] - at[0], b[1] - at[1]) }); };   // взял швабру у стены — поворачивается туда, куда пойдёт
  const walkP = (pts, mop, x) => { unpark(pts[1]); let d = 0; for (let i = 1; i < pts.length; i++) d += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); add('walk', d / .62 + .5, Object.assign({ a: pts[0], b: pts[pts.length - 1], pts, mop }, x)); at = pts[pts.length - 1].slice(); };
  const walk = (b, mop, x) => { const d = Math.hypot(b[0] - at[0], b[1] - at[1]); if (d > .05) { const fc = P.length && P[P.length - 1].k === 'take' ? 1 : 0; unpark(b); add('walk', d / .62 + .5 + (mop === 2 ? 2 * PUSH_T : 0), Object.assign({ a: at.slice(), b: b.slice(), mop, fromCarry: fc }, x));
      if (mop === 2) { P[P.length - 1].bz1 = bz = b[1] + (b[1] > at[1] ? 1 : -1) * BK_DZ; } } at = b.slice(); };   // ведро он толкает перед собой шваброй — встаёт оно впереди по ходу   // к новому участку — толкает ведро перед собой
  add('dip', BK_DIP, { z: G7Z[0] });   // перед мытьём — макнул швабру и отжал
  add('mop', 26 + R() * 14, { z: G7Z[0] });   // в начале часа — моет (Итан говорит «уборщик моет пол»)
  let last = 'mop';
  while (t < 900) {
    const r = R(); let k = r < .42 ? 'mop' : r < .64 ? 'office' : r < .86 ? 'rest' : 'stretch';
    if (k === last && k !== 'mop') k = 'mop';
    if (k === 'mop') { const z = G7Z[R() * 3 | 0] + (R() - .5) * .5; walk([G7X, z], 2); add('dip', BK_DIP, { z }); add('mop', 10 + R() * 12, { z }); }
    else if (k === 'office') { walkP([at.slice(), [GL.door[0] + .35, GL.door[1]], GL.door], 1); add('park', 1.4, { at: GL.door, ry: -PI / 2, mopTo: 'door' }); add('open', 1.0, {});
      add('in', 2.8, {}); add('inside', 12 + R() * 20, {}); add('out', 3.3, {}); add('close', 1.2, {}); add('take', 1.2, { at: GL.door, ry: -PI / 2, mopTo: 'door' }); at = GL.door.slice(); }
    else if (k === 'rest') { const go = [GL.chairPark, GL.chairVia, GL.chairStand], back = go.slice().reverse(), hd = (a, b) => Math.atan2(b[0] - a[0], b[1] - a[1]);   // к стулу и обратно — в обход его угла
      walkP([at.slice(), [GL.chairPark[0] + .35, GL.chairPark[1]], GL.chairPark], 1); add('park', 1.4, { at: GL.chairPark, ry: -PI / 2, mopTo: 'chair' }); add('turn', .9, { at: GL.chairPark, ry0: -PI / 2, ry1: hd(go[0], go[1]) });
      walkP(go, 0, { mopTo: 'chair' }); add('turn', 1.0, { at: GL.chairStand, ry0: hd(go[1], go[2]), ry1: PI / 2 });
      add('sitdown', 1.4, {}); add('sit', 12 + R() * 16, { peek: R() < .5 ? 3 + R() * 6 : -1 }); add('standup', 1.4, {}); add('turn', .9, { at: GL.chairStand, ry0: PI / 2, ry1: hd(back[0], back[1]) });
      walkP(back, 0, { mopTo: 'chair' }); add('turn', .9, { at: GL.chairPark, ry0: hd(back[1], back[2]), ry1: -PI / 2 }); add('take', 1.2, { at: GL.chairPark, ry: -PI / 2, mopTo: 'chair' }); at = GL.chairPark.slice(); }
    else { add('stretch', 5 + R() * 4, { at: at.slice() }); }
    last = k;
  }
  return P;
}
// макает и отжимает: повернулся к ведру → колодку в воду, два-три раза вверх-вниз → в отжим, левой давит рычаг → обратно к полу перед собой
const BK_X = .44, BK_DZ = .3, BK_RY = PI + .25, BK_DIP = 3.6;   // отжим и рычаг — со стороны Гаса
function gusDip(o, x, z, t, v, d, bk) {
  const ryB = Math.atan2(bk[0] - x, bk[1] - z), tn = Math.min(seg(v, 0, .6), 1 - seg(v, d - .6, d)), ry = lerp(G7R, ryB, tn), R = locR(x, z, ry);
  const cB = Math.cos(BK_RY), sB = Math.sin(BK_RY), bw = (lx, y, lz) => [bk[0] + lx * cB + lz * sB, y, bk[1] - lx * sB + lz * cB];   // точка ведра (его оси) → мир
  const floor = R.p(0, MOP_Y, .78), water = bw(-.08, .26 - .09 * Math.abs(Math.sin((v - .7) * PI / .45)) * seg(v, .7, .9) * (1 - seg(v, 1.9, 2.0)), 0), wring = bw(.02, .26, 0);   // колодка уходит под отжим, в ведро (снаружи не торчит)
  const kIn = seg(v, .45, .8) * (1 - seg(v, d - .75, d - .4)), kWr = seg(v, 2.0, 2.3) * (1 - seg(v, 3.0, 3.25)), press = seg(v, 2.3, 2.6) * (1 - seg(v, 2.8, 3.0));
  const head = lin(lin(floor, water, kIn), wring, kWr), piv = R.p(-.24 + .1 * kWr, 1.0, .3);   // черенок проходит справа от корпуса, у плеча — не через голову
  const u = nrm([piv[0] - head[0], piv[1] - head[1], piv[2] - head[2]]), at = q => [head[0] + u[0] * q, head[1] + u[1] * q, head[2] + u[2] * q];
  window.__W3chars.propAim('mop', o.S, head, u, ry + PI / 2 * Math.max(kIn, kWr));   // в ведре колодка поворачивается вдоль длинной стороны ведра — тряпка не торчит сквозь стенки
  { const hd = window.__W3chars.prop3('mop').obj.userData.head; if (hd) hd.visible = Math.max(kIn, kWr) < .7; }   // колодка под водой/в отжиме — её не видно (иначе тряпка проходит сквозь стенки ведра)
  const side = R.v(1, 0, 0), pr = nrm(crs(u, crs(side, u))), pl = pr.map(w => -w), lever = bw(.2, .82 - .2 * press, 0);
  const P = { ry, clips: tn > 0 && tn < 1 ? [['Walk_Loop', v * .9, Math.sin(PI * tn) * .5], ['Idle_Loop', t + 2, 1 - Math.sin(PI * tn) * .5]] : [['Idle_Loop', t + 2, 1]], legs: tn > 0 && tn < 1 ? undefined : 'plant',
    pelvis: R.v(0, -.03 - .03 * kIn, .0), spine: { to: lin(R.p(0, .2, 1.0), [head[0], 1.0, head[2]], .4), k: .12 + .08 * kIn }, look: kIn > .3 ? head : null,
    arm: { r: { at: at(1.38), by: 'palm', fdir: crs(pr, u), pdir: pr, pole: R.v(-1, -.4, -.4) },   // при макании обе руки у верха черенка — так достаёт
           l: kWr > .5 ? { at: lever, by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], pole: R.v(1, -.6, 0) } : { at: at(1.02 + .1 * (1 - kIn)), by: 'palm', fdir: crs(u, pl), pdir: pl, pole: R.v(1, -.8, .1) } },
    fing: { r: ['grip', 1], l: ['grip', 1] } };
  return P;
}
function gusMopAt(o, where) { const W = window.__W3chars; if (where === 'door') W.propAim('mop', o.S, GL.mopDoor, MOPWALL_D, 1.2, MOP_Y); else W.propAim('mop', o.S, GL.mopChair, MOPWALL_D, 1.2, MOP_Y); }
// везёт ведро к следующему участку (20 ноя: раньше ведро на колёсиках ехало рядом само): ставит колодку швабры в отжим, толкает ведро перед собой черенком,
// обе руки на черенке; доехал — вынимает швабру. Ведро на ходу поворачивается отжимом к нему (туда, откуда толкают)
const PUSH_T = .7;
function gusPush(o, it, v, k, t, bz) {
  const a = it.a, b = it.b, hd0 = Math.atan2(b[0] - a[0], b[1] - a[1]), x = lerp(a[0], b[0], k), z = lerp(a[1], b[1], k), R = locR(x, z, hd0), C = A.cleaner;
  const kinA = seg(v, .1, PUSH_T - .05), kinB = 1 - seg(v, it.d - PUSH_T + .05, it.d - .1), kin = kinA * kinB, first = v < it.d / 2, mv = seg(v, PUSH_T, PUSH_T + .4) * (1 - seg(v, it.d - PUSH_T - .4, it.d - PUSH_T));   // швабра в отжиме; ведро развёрнуто к нему
  const ryB = angTo(BK_RY, Math.atan2(-(z - bz), x - BK_X), mv); if (o.bucket) o.bucket.rotation.set(0, ryB, 0);
  const cB = Math.cos(ryB), sB = Math.sin(ryB), W = [BK_X + .02 * cB, .27, bz - .02 * sB], side = BK_X > x ? -1 : 1;   // side: ведро справа (−1) или слева от него (оси персонажа: x — влево)
  const sd = (BK_X - x) * Math.cos(hd0) - (bz - z) * Math.sin(hd0) > 0 ? 1 : -1, floor = R.p(0, MOP_Y, .78), CR = GUS_CARRY(R), fc = it.fromCarry && first,   // только что взял швабру у стены — в отжим она идёт из руки у бока, а не с пола
    head = lin(fc ? CR.head : floor, W, kin), piv = lin(fc ? CR.top : R.p(-.05, .97, .18), R.p(sd * .15, 1.05, .32), kin);
  const u = nrm([piv[0] - head[0], piv[1] - head[1], piv[2] - head[2]]), at = q => [head[0] + u[0] * q, head[1] + u[1] * q, head[2] + u[2] * q];
  window.__W3chars.propAim('mop', o.S, head, u, hd0 + (ryB - hd0 + PI / 2) * kin);
  { const hd = window.__W3chars.prop3('mop').obj.userData.head; if (hd) hd.visible = kin < .7; }   // колодка в отжиме — не видна (как при макании)
  const sdv = R.v(1, 0, 0), pr = nrm(crs(u, crs(sdv, u))), pl = pr.map(w => -w), w = Math.sin(PI * k), s = Math.hypot(b[0] - a[0], b[1] - a[1]) * k;
  return ['cleaner', x, 0, z, hd0, { clips: [['Walk_Loop', s / (1.3 * C.ss) * 1.333, w], ['Idle_Loop', t + 2, 1 - w]], legs: w < .1 ? 'plant' : undefined, wear: { radio: RADIO_G },
    twist: -sd * .3 * kin, spine: { to: [W[0], .9, W[2]], k: .1 * kin }, look: kin > .3 ? [W[0], .5, W[2]] : head,
    arm: { r: { at: at(lerp(1.1, 1.06, kin)), by: 'palm', fdir: crs(pr, u), pdir: pr, pole: R.v(-1, -.4, -.4) }, l: { at: at(lerp(.7, .8, kin)), by: 'palm', fdir: crs(u, pl), pdir: pl, pole: R.v(1, -.8, .1), w: fc ? kinA : 1 } },
    fing: { r: ['grip', 1], l: ['grip', 1] } }, null];
}
// несёт швабру: в правой руке у бока, колодка приподнята над полом и чуть позади, черенок наклонён вперёд (тестер, 20 ноя: «несёт древком флага перед собой и наступает на тряпку»)
const GUS_CARRY = R => { const head = R.p(-.36, .13, -.22), top = R.p(-.3, 1.08, .2); return { head, top, u: nrm([top[0] - head[0], top[1] - head[1], top[2] - head[2]]) }; };
function gusCarry(o, x, z, ry) { const R = locR(x, z, ry), SD = GUS_CARRY(R); window.__W3chars.propAim('mop', o.S, SD.head, SD.u, ry + PI / 2, MOP_Y);
  return { arm: { r: { at: SD.top, by: 'palm', fdir: R.v(.9, 0, .3), pdir: R.v(1, 0, -.2), w: 1 } }, fing: { r: ['grip', 1] }, wear: { radio: RADIO_G } }; }
// ставит швабру к стене / берёт её: k 0 — в руке у бока (как несёт, GUS_CARRY), 1 — прислонена к стене; рука ведёт черенок всё время, пока hold > 0
function gusPark(o, t, at, ry, k, hold, wall) {
  const R = locR(at[0], at[1], ry), SD = GUS_CARRY(R), tw = [wall[0] + MOPWALL_D[0] * 1.02, wall[1] + MOPWALL_D[1] * 1.02, wall[2] + MOPWALL_D[2] * 1.02];
  const head = lin(SD.head, wall, k), top = lin(SD.top, tw, k), u = nrm([top[0] - head[0], top[1] - head[1], top[2] - head[2]]);
  window.__W3chars.propAim('mop', o.S, head, u, lerp(ry + PI / 2, 1.2, k), MOP_Y);
  return { clips: [['Idle_Loop', t + 4, 1]], legs: 'feet', look: hold > .3 ? top : null, spine: { to: [top[0], 1.1, top[2]], k: .16 * k * hold },
    arm: hold > 0 ? { r: { at: top, by: 'palm', fdir: R.v(.9, 0, .3), pdir: R.v(1, 0, -.2), w: hold } } : undefined, fing: { r: [hold > .5 ? 'grip' : 'relax', 1] }, wear: { radio: RADIO_G } };
}
// идёт (ломаная через проём): клип ходьбы по пройденному пути, как shuffle; extra — руки и взгляд поверх
function gusPath(pts, k, u, t, extra) { const [x, z, ry, s] = polyWalk(pts, k), C = A.cleaner, w = Math.sin(PI * clamp(u, 0, 1));
  return ['cleaner', x, 0, z, ry, Object.assign({ clips: [['Walk_Loop', s / (1.3 * C.ss) * 1.333, w], ['Idle_Loop', t, 1 - w]], legs: w < .1 ? 'feet' : undefined, wear: { radio: RADIO_G } }, extra), null]; }
// рация в любой позе распорядка (владелец, 19 ноя: «сидит — а рацию не достаёт»): та же схема, что gusRadioCam — снял с нагрудника, ко рту, пока говорит
function gusRadioOn(tu, g) {
  if (!tu || !g || !(g.h > 0) || typeof tu[5] !== 'object') return tu;
  const [, x, , z, ry, P] = tu, R = locR(x, z, ry), chest = bodyAt(R, 'spine_03', G_S3, [.07, 1.25, .15]), mouth = bodyAt(R, 'Head', G_HEAD, [.05, 1.46, .15]), clip = wornPt(A.cleaner, RADIO_G) || R.p(.07, 1.24, .17);
  const w = seg(g.h, 0, .45), inH = g.h >= .45, k1 = seg(g.h, .45, 1), at = inH ? (C => lin(lin(clip, chest(C), k1), mouth(C), g.u)) : clip;
  P.arm = Object.assign({}, P.arm, { l: { at, by: 'palm', fdir: radioF('l', R.v(-.15, 1, .05), R.v(.05, -.1, -1)), pdir: R.v(.05, -.1, -1), w } }); P.fing = Object.assign({}, P.fing, { l: ['grip', w] });
  if (inH) { delete P.wear; P.hold = Object.assign({}, P.hold, { l: 'radio' }); }
  P.talk = g.u > .6 ? 1 : 0; return tu;
}
function gusLife(o, tg, t, g) {   // g — разговор по рации (доли из radioTalk) или null
  const L = gusLife0(o, tg, t, g); if (L && g) for (const tu of L) if (!(tu && tu[5] && tu[5].radioDone)) gusRadioOn(tu, g); return L;
}
function gusLife0(o, tg, t, radio) {   // radio — доли разговора по рации или null
  if (!gusPlanA || tg < gusPlanT - 3) window.__gusPlan = gusPlanA = gusPlanMake();   // новый час (часы швабры пошли с нуля) — новый распорядок
  gusPlanT = tg;
  const P = gusPlanA, it = P.find(x => tg >= x.t0 && tg < x.t0 + x.d) || P[P.length - 1], v = tg - it.t0, u = clamp(v / it.d, 0, 1), e = ease(u);
  o.faceL.intensity = 0; o.chair10.visible = true; o.chair10.position.set(GL.chairC[0], 0, GL.chairC[1]); o.chair10.rotation.y = PI / 2;   // стул у стены (на нём Гас отдыхает)
  const door = (k, lit = k) => { o.open5(k); o.room5L.intensity = 3.2 * lit; o.lamp5Bulb.visible = lit > .3; };
  const kPush = it.k === 'walk' && it.mop === 2 ? ease(clamp((v - PUSH_T) / (it.d - 2 * PUSH_T), 0, 1)) : 0;
  const bzNow = lerp(it.bz0, it.bz1, it.k === 'walk' ? (it.mop === 2 ? kPush : e) : 0); if (o.bucket) { o.bucket.position.set(BK_X, 0, bzNow); o.bucket.rotation.set(0, BK_RY, 0); }
  if (it.k === 'dip') { const P2 = gusDip(o, G7X, it.z, t, v, it.d, [BK_X, bzNow]); P2.wear = { radio: RADIO_G }; return [['cleaner', G7X, 0, it.z, P2.ry, P2, null]]; }
  if (it.k === 'mop') { if (v < .9 && P.indexOf(it) > 0) { const P2 = mopGus(o, G7X, it.z, G7R, t, e); P2.wear = { radio: RADIO_G }; return [['cleaner', G7X, 0, it.z, G7R, P2, null]]; }
    if (radio) { const P2 = gusRadioCam(o, t, G7X, it.z, G7R, radio, 1); P2.radioDone = 1; return [['cleaner', G7X, 0, it.z, G7R, P2, null]]; }   // там же, где мыл (раньше — прыжок на место старого распорядка)
    const k = v > it.d - .9 ? (it.d - v) / .9 : 1, P2 = mopGus(o, G7X, it.z, G7R, t, k); P2.wear = { radio: RADIO_G }; return [['cleaner', G7X, 0, it.z, G7R, P2, null]]; }
  if (it.k === 'walk') { const pts = it.pts || [it.a, it.b], [x, z, ry] = polyWalk(pts, e);
    if (it.mop === 0) { gusMopAt(o, it.mopTo); return [gusPath(pts, e, u, t, {})]; }   // швабра осталась у стены — идёт с пустыми руками
    if (it.mop === 2) return [gusPush(o, it, v, kPush, t, bzNow)];   // толкает ведро шваброй
    return [gusPath(pts, e, u, t, gusCarry(o, x, z, ry))]; }   // к двери / к стулу — несёт швабру у бока
  if (it.k === 'turnc') { const ry = angTo(it.ry0, it.ry1, e); return [['cleaner', it.at[0], 0, it.at[1], ry, Object.assign({ clips: [['Walk_Loop', v * .9, Math.sin(PI * u) * .6], ['Idle_Loop', t + 3, 1 - Math.sin(PI * u) * .6]] }, gusCarry(o, it.at[0], it.at[1], ry)), null]]; }
  if (it.k === 'park' || it.k === 'take') {   // ставит швабру к стене / берёт её — рукой: наклонил к стене, отпустил (и наоборот)
    const q = it.k === 'park' ? u : 1 - u, wall = it.mopTo === 'door' ? GL.mopDoor : GL.mopChair;
    return [['cleaner', it.at[0], 0, it.at[1], it.ry, gusPark(o, t, it.at, it.ry, seg(q, .15, .6), 1 - seg(q, .68, .92), wall), null]]; }
  const inOffice = ['open', 'in', 'inside', 'out', 'close'].includes(it.k), inRest = ['turn', 'sitdown', 'sit', 'standup'].includes(it.k);
  if (inOffice) { gusMopAt(o, 'door'); const R5 = locR(GL.door[0], GL.door[1], -PI / 2);
    if (it.k === 'open') {   // левая рука — на ручку, нажал, створка подалась
      const th = .09 * seg(v, .75, 1.0), w = seg(v, .08, .5), h = H5(th); h[1] -= .03 * seg(v, .5, .7) * (1 - seg(v, .85, 1.0)); door(th / 1.45, 0);
      return [['cleaner', GL.door[0], 0, GL.door[1], -PI / 2, { clips: [['Idle_Loop', t + 3, 1]], legs: 'feet', wear: { radio: RADIO_G }, look: h, spine: { to: h, k: .12 * w },
        arm: { l: { at: h, by: 'palm', fdir: R5.v(.2, 0, 1), pdir: [0, -1, 0], w, pole: R5.v(1, -.7, -.2) } }, fing: { l: ['grip', w] } }, null]]; }
    if (it.k === 'in') {   // толкает створку и идёт следом; у порога отпускает — дверь доходит сама
      const k = ease(u), [x] = polyWalk([GL.door, GL.sill, GL.inside], k), kR = .3;
      const th = k < kR ? pwl(x, [[-1.25, .52], [-.85, .09]]) : .52 + (1.45 - .52) * (1 - (1 - clamp((k - kR) / .3, 0, 1)) ** 2), w = 1 - seg(k, kR - .03, kR + .07), h = H5(th);
      door(th / 1.45, seg(k, .55, .75));   // вошёл — щёлкнул свет
      return [gusPath([GL.door, GL.sill, GL.inside], k, u, t, { look: w > .3 ? h : null, spine: { to: h, k: .14 * w }, arm: w > 0 ? { l: { at: h, by: 'palm', fdir: [-1, 0, .2], pdir: [0, -1, 0], w, pole: [.3, -.7, 1] } } : undefined, fing: { l: [w > .5 ? 'grip' : 'relax', 1] } })]; }
    if (it.k === 'inside') { door(1); return []; }   // в кабинете: дверь открыта, внутри свет. Пусто — и тихо
    if (it.k === 'out') {   // погасил свет, взялся правой за наружную ручку и тянет дверь за собой, выходя; закрылась — отпустил
      const T0 = .55, T1 = it.d - .22, k = ease(clamp((v - T0) / (T1 - T0), 0, 1)), pts = [GL.inside, [-1.35, -8.88], [-.9, -9.1]], [x] = polyWalk(pts, k);
      const th = pwl(x, [[-2.0, 1.45], [-1.5, .8], [-1.2, .52], [-.9, 0]]), w = seg(v, .1, .5) * (1 - seg(v, T1 - .15, T1 + .12)), h = H5(th);
      door(th / 1.45, 1 - seg(v, 0, .3));
      return [gusPath(pts, k, clamp((v - T0) / (T1 - T0), 0, 1), t, { look: k < .15 || k > .8 ? h : null, twist: -.45 * seg(x, -1.7, -1.0) * w,
        arm: w > 0 ? { r: { at: h, by: 'palm', fdir: k < .3 ? [.3, 0, 1] : [-1, 0, -.2], pdir: [0, -1, 0], w, pole: [.2, -.6, 1] } } : undefined, fing: { r: [w > .5 ? 'grip' : 'relax', 1] } })]; }
    // close: дверь закрыта — поворачивается к стене, к швабре
    door(0, 0); const h0 = Math.atan2(-.9 + 1.35, -9.1 + 8.88), ry = angTo(h0, 3 * PI / 2, e);
    return [['cleaner', lerp(-.9, GL.door[0], e), 0, GL.door[1], ry, { clips: [['Walk_Loop', v * .9, Math.sin(PI * u) * .6], ['Idle_Loop', t + 3, 1 - Math.sin(PI * u) * .6]], wear: { radio: RADIO_G } }, null]]; }
  if (inRest) { gusMopAt(o, 'chair');
    if (it.k === 'turn') { const ry = angTo(it.ry0, it.ry1, e); return [['cleaner', it.at[0], 0, it.at[1], ry, { clips: [['Walk_Loop', v * .9, Math.sin(PI * u) * .6], ['Idle_Loop', t + 3, 1 - Math.sin(PI * u) * .6]], wear: { radio: RADIO_G } }, null]]; }
    const sit = it.k === 'sitdown' ? e : it.k === 'standup' ? 1 - e : 1, x = lerp(GL.chairStand[0], GL.seat[0], sit);
    let look = null; if (it.k === 'sit') { look = it.peek >= 0 && v > it.peek && v < it.peek + 2.6 ? 'cam' : [x + 1.5, 1.0 + .3 * Math.sin(t * .3), GL.seat[1] - 1.2 + Math.sin(t * .21) * 2]; }   // сидит, смотрит перед собой; иногда — в камеру
    const bend = it.k === 'sit' ? 0 : Math.sin(PI * u);   // садится и встаёт с наклоном вперёд (садился «лифтом», корпус отвесно — тестер, 20 ноя)
    return [['cleaner', x, 0, GL.seat[1], PI / 2, { clips: [['Sitting_Idle_Loop', t, Math.max(sit, .001)], ['Idle_Loop', t + 3, 1 - sit]], talk: radio ? 1 : 0, wear: { radio: RADIO_G }, look: look === 'cam' ? [1.0, 2.75, -.5] : bend > .2 ? [x + .9, .5, GL.seat[1]] : look,
      spine: bend > 0 ? { to: [x + .7, .45, GL.seat[1]], k: .34 * bend } : undefined }, null]]; }
  if (it.k === 'stretch') {   // стоит со шваброй, разминает спину, оглядывается
    const P2 = gusStand(o, t, G7R, [it.at[0] + Math.sin(t * .7) * 1.6, 1.5, it.at[1] - 3], null, 1, it.at, G7R); P2.wear = { radio: RADIO_G };
    P2.spine = { to: [it.at[0], 2.2, it.at[1] - .3 - Math.sin(v * 1.3) * .2], k: .25 * Math.sin(PI * u) }; return [['cleaner', it.at[0], 0, it.at[1], G7R, P2, null]]; }
  return null;
}
// 7-й: Гас моет (s.gus), свет горит, пока он там; без него — темно, камера в ИК.
// После 03:00 (s.gusGone, часы S5/S6/A1) Гаса нет: ведро, табличка, лужа и брошенная швабра — как на записи в 03:00 (f7_cam_after); свет — с пульта
SH.cam07 = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .8, -10.2], fov: 44, cctv: 1, set: (o, t) => { const s = SHS();
  if (s.gusGone) { o.cleanSet({ bucket: [.6, -10.9, .3], sign: CLEAN.sign, wet: [-.1, -10.6], door7: 0 }); o.ajar10(s.door7 ? 1.4 : 0);
    window.__W3chars.propAim('mop', o.S, MOPDOWN, MOPDOWN_D, 1.2, MOP_Y); salt7(o);
    setLamps(o, s.l7 ? 7 : 0); o.amb.intensity = s.l7 ? .36 : .1; if (!s.l7) irOn(o, [1.0, 2.6, -.7], 1.4);
    return []; }
  const C = s.gus ? Object.assign({}, CLEAN) : {}; C.door7 = 0;
  if (s.bucket7) { C.bucket = [-.3, -8.1, .7]; C.mop = [.35, -8.9, 1.25, 1]; }
  if (s.mopAlone) window.__W3chars.propAim('mop', o.S, [0, MOP_Y, -7.2], [0, 1, 0], 0);   // швабра стоит торчком посреди коридора, никто не держит
  o.cleanSet(C); o.ajar10(s.door7 ? 1.4 : 0);
  if (s.gus) { setLamps(o, 7); const L = gusLife(o, s.gz ?? t, t, s.gusR || null); if (L) return L;   // распорядок: моет, заходит в кабинет, сидит, разминается
    const G = gusRoutine(s.gz ?? t);   // s.gz — часы швабры: пока говорит по рации, стоят
    if (s.gusR) return [['cleaner', G7X, 0, G.z, G7R, gusRadioCam(o, t, G7X, G.z, G7R, s.gusR, G.k), null]];
    if (G.move) { const [z0, z1, v, mv] = G.move, zz = lerp(z0, z1, ease(clamp(v / mv, 0, 1))), P = mopGus(o, G7X, zz, G7R, t, 0); delete P.clips;   // идёт со шваброй: шаги — shuffle
      P.wear = { radio: RADIO_G }; return [shuffle('cleaner', [G7X, z0], [G7X, z1], v, 0, mv, G7R, P)]; }
    const P = mopGus(o, G7X, G.z, G7R, t, G.k); P.wear = { radio: RADIO_G }; return [['cleaner', G7X, 0, G.z, G7R, P, null]]; }
  setLamps(o, s.l7 ? 7 : 0); o.amb.intensity = s.l7 ? .36 : .1; if (!s.l7) irOn(o, [1.0, 2.6, -.7], 1.4);
  return []; } };
SH.cam10 = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.2, .9, -12], fov: 46, cctv: 1, set: (o, t) => { const s = SHS(); o.setFloor(10);
  setLamps(o, s.l10 ? 7 : 1, true); o.amb.intensity = s.l10 ? .36 : .12; o.ajar10(s.door10 ? 1 : 0);
  if (s.door10) { o.hole10.material = o.holeLit; o.faceL.position.set(.95, 1.3, -5.6); o.faceL.color.setHex(0xffd9a0); o.faceL.intensity = 1.6; o.faceL.distance = 3.2; }
  if (s.chair10) { o.chair10.visible = true; o.chair10.position.set(-.15, 0, -5.3); o.chair10.rotation.y = 2.5; }
  if (s.frank10) return [['mihalych', -.1, 0, -11.2, 0, STILL('Idle_Loop', 2.7), [1.0, 2.75, -.5]]];
  if (s.gusTwin) return [['cleaner', .2, 0, -8.6, 0, STILL('Idle_Loop', .9), [1.0, 2.75, -.5]]];   // «Гас» стоит на десятом, пока настоящий моет седьмой
  return []; } };
SH.cam_m2 = { loc: 'basement', pos: [.85, 2.32, -.4], look: [0, .8, -14], fov: 56, cctv: 1, set: (o, t) => { const s = SHS();
  if (s.mOff) { bsDark(o, 0); irOn(o, [.85, 2.2, -.6], 1.5); }
  if (s.crawl_m2 && !s.mOff) { const A3 = [['smenshik', .1, .1, -8.4, PI * .5, 'lay', null]];   /* y .1: лежал на 11 см в полу */ if (s.sdoor) { o.sdoor.rotation.y = 1.25; } return A3; }
  if (s.sdoor) { o.sdoor.rotation.y = 1.25; o.roomL.intensity = 4; o.roomL.distance = 8; o.roomL.position.set(0, 1.2, -13.6); o.glow.material.opacity = 1; o.glow.scale.set(2.2, 3, 1); }   // дверь серверной открыта, внутри — свечи
  return []; } };
// «Помехи» на камерах смены (странность stare): там стоит Жилец — тонкий, бледный, лицом в объектив. Рябь по самой фигуре рисует движок по её проекции (figGlitch в engine.js);
// пока игрок смотрит, движок шагами (stareN) подвигает точку к объективу. at = [x, пол, z], near/steps — куда он подходит (описание странности в story.js)
// Шаги (stareN) — как близко он подошёл: 0–1 стоит не шевелясь, 2 — дёргается, 3 — у самого объектива тянет к нему руки. Корпусом всегда к камере
function stareFig(s, id, cp) { const at = s['stareAt' + id], n = s['stareN' + id] || 0; if (s['stare' + id] == null || !at) return null;
  return ['monster', at[0], at[1] || 0, at[2], Math.atan2(cp[0] - at[0], cp[2] - at[2]), n < 2 ? STILL('Idle_Loop', 1.7) : n < 3 ? 'monster' : s['stareP' + id] || 'reach', 'cam']; }
for (const id of ['01', '02', '03', '04', '07', '10', 'm2']) { const sh = SH[id === 'm2' ? 'cam_m2' : 'cam' + id]; if (!sh) continue; const f = sh.set;
  sh.set = (o, t, sh2) => { const acts = f(o, t, sh2) || [], s = SHS(), fig = stareFig(s, id, sh.pos);
    if (id === '07' && s.gus && s.gusDark && !s.gusGone) gusDarkCam(o, acts);
    if (s.gusIn && (id === '01' || id === '02' || id === '03')) { const g = gusArrive(o, s.gusInT || 0); if (g) acts.push(g); }
    return fig ? acts.concat([fig]) : acts; }; }
// этап 1 (19 ноя): с 02:40 на седьмом не горит свет (s.gusDark) — Гас домывает при фонаре: лампы погашены, вокруг него тёплое пятно света.
// В 02:59 свет «загорается» (j09, сцена floor7)
function gusDarkCam(o, acts) {
  setLamps(o, 0); o.amb.intensity = .1; if (o.sheen) o.sheen.material.opacity *= .25;
  const g = acts.find(a => a && a[0] === 'cleaner'); if (!g) return;   // зашёл в кабинет 705 — свет оттуда
  const ry = g[4]; o.faceL.position.set(g[1] + Math.sin(ry) * .6, .95, g[3] + Math.cos(ry) * .6); o.faceL.color.setHex(0xffdca8); o.faceL.distance = 3.6; o.faceL.intensity = 1.9;
}
// этап 1: после трёх у ведра на седьмом — рассыпанная соль (Гас носил её в кармане — Фрэнк насыпал; круга он не насыпал)
function salt7(o) {
  if (!o.salt7) { const tx = ctex(48, 32, (x, w, h) => { x.clearRect(0, 0, w, h);
      for (let i = 0; i < 420; i++) { const a = rnd() * 6.2832, r = Math.pow(rnd(), 1.3); x.fillStyle = `rgba(244,242,232,${.6 + rnd() * .4})`; x.beginPath(); x.arc(w / 2 + Math.cos(a) * r * w * .46, h / 2 + Math.sin(a) * r * h * .44, .5 + rnd() * 1.0, 0, 7); x.fill(); } });
    const P = { map: tx, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 };
    o.salt7 = plane(.9, 1.0, CINE ? new T.MeshStandardMaterial(Object.assign({ emissiveMap: tx, emissive: 0x8e8c84, roughness: .85 }, P)) : new T.MeshBasicMaterial(P), o.S); ps1(o.salt7.material);
    o.salt7.rotation.set(-PI / 2, 0, .4); o.salt7.position.set(.72, .008, -10.15); o.salt7.userData.solid = false; (o.xtra = o.xtra || []).push(o.salt7); }
  o.salt7.visible = true;
}
// этап 1 (19 ноя): 21:36 — Гас приходит на смену. С улицы в дверь, через турникет; посреди холла поднимает глаза на камеру и машет рукой; вызывает лифт и уезжает наверх.
// T — секунды с начала события (движок: странность gusIn, clock). Он один и тот же на CAM 01 (вход), 02 (холл) и 03 (лифты)
const GIN = { out: [[3.6, 14.2], [1.6, 10.5], [.5, 7.4], [.16, 6.62]], in1: [[.16, 6.62], [-.22, 6.3], [-.42, 5.85], [-.55, 3.9]], in2: [[-.55, 3.9], [-.7, 1.7], [-.7, .3], [-1.2, -1.6], [-1.45, -3.9], [-1.45, -5.5]],   // к вызывной панели левого лифта (она в метре справа от дверей)
    // in1 — от двери до середины холла (там машет: камере 02 его не заслоняет колонна), in2 — через турникет к левому лифту
  wave: 2.6, turn: .9 };
function gusArrive(o, T) {
  const X = { wear: { radio: RADIO_G } }, CAM2 = [7.45, 3.85, 5.45], tA = (polyLen(GIN.out) + 1.05) / 1.05, u = T - tA;
  leafAng(o, 0, leafPull(u, .15, 2.6));   // створка: рванул на себя, доводчик закрывает
  if (u < .75) return doorIn(o, 'cleaner', T, GIN.out, 1.05, GIN.in1, 1.0, X);
  const v = 1.0, t1 = u - .75, d1 = polyLen(GIN.in1) / v + 1.05, end1 = GIN.in1[GIN.in1.length - 1], ryC = Math.atan2(CAM2[0] - end1[0], CAM2[2] - end1[1]);
  if (t1 < d1) return walker('cleaner', GIN.in1, t1, v, X, ryC);   // к концу пути разворачивается к камере
  const t2 = t1 - d1;
  if (t2 < GIN.wave) {   // стоит лицом к камере, поднял правую руку и качнул ею
    const w = seg(t2, .15, .7) * (1 - seg(t2, GIN.wave - .7, GIN.wave - .1)), R = locR(end1[0], end1[1], ryC), sw = Math.sin((t2 - .6) * 7) * .07 * w;
    return ['cleaner', end1[0], 0, end1[1], ryC, Object.assign({ clips: [['Idle_Loop', T + 2, 1]], legs: 'feet', look: CAM2,
      arm: { r: { at: R.p(-.3 + sw, 1.72, .16), by: 'palm', fdir: nrm(R.v(-sw * 2, 1, .05)), pdir: R.v(0, 0, 1), w, pole: R.p(-.7, 1.0, -.2) } }, fing: { r: ['open', w] } }, X), null]; }
  const t3 = t2 - GIN.wave, ry2 = Math.atan2(GIN.in2[1][0] - end1[0], GIN.in2[1][1] - end1[1]);
  if (t3 < GIN.turn) { const k = t3 / GIN.turn, dr = Math.atan2(Math.sin(ry2 - ryC), Math.cos(ry2 - ryC));   // поворачивается на месте к лифтам — шажками
    return ['cleaner', end1[0], 0, end1[1], ryC + dr * ease(k), Object.assign({ clips: [['Walk_Loop', t3 * .9, Math.sin(PI * k) * .6], ['Idle_Loop', T + 2, 1 - Math.sin(PI * k) * .6]] }, X), null]; }
  const t4 = t3 - GIN.turn, d4 = polyLen(GIN.in2) / v + 1.05, L = o.lifts[0], at = GIN.in2[GIN.in2.length - 1];
  if (t4 < d4) return walker('cleaner', GIN.in2, t4, v, X, PI);
  // 20 ноя (тестер: «кнопку не нажимает, двери разъезжаются сами; вошёл и стоит лицом в стенку»): подошёл к панели, нажал кнопку пальцем, шагнул к дверям,
  // двери разошлись — вошёл, развернулся лицом к выходу, двери закрылись, лифт пошёл вверх
  const t5 = t4 - d4, open = seg(t5, 1.0, 1.8) * (1 - seg(t5, 4.7, 5.5)), BTN = [L.x + 1.0, 1.3, -5.975], front = [L.x, -5.6], cab = [L.x, -6.75];
  setLift(o, L, open, t5 > 5.7 ? String(Math.min(7, 2 + Math.floor((t5 - 5.7) / .7))) : '1', t5 > 5.7 ? 1 : 0); o.cabLight.intensity = 2.4 * Math.min(1, open * 2);
  if (t5 < 1.0) { const w = seg(t5, .1, .45) * (1 - seg(t5, .7, .95)), pr = seg(t5, .45, .55) * (1 - seg(t5, .6, .7));
    return ['cleaner', at[0], 0, at[1], PI, Object.assign({ clips: [['Idle_Loop', T + 2, 1]], legs: 'feet', look: BTN, spine: { to: BTN, k: .1 * w },
      arm: { r: { at: [BTN[0], BTN[1], BTN[2] - .012 * pr], by: 'tip', fdir: [0, 0, -1], pdir: [0, -1, 0], w, pole: [-.7, -.7, .2] } }, fing: { r: ['point', w] } }, X), null]; }
  if (t5 < 2.4) return shuffle('cleaner', at, front, t5, 1.0, 1.3, PI, Object.assign({ look: [L.x, 1.4, -6.2] }, X));   // к дверям, пока они расходятся
  if (t5 < 4.0) return shuffle('cleaner', front, cab, t5, 2.4, 1.5, PI, X);
  if (t5 < 5.4) { const k = clamp((t5 - 4.0) / .9, 0, 1), ry = PI + PI * ease(k);   // в кабине разворачивается лицом к дверям
    return ['cleaner', cab[0], 0, cab[1], ry, Object.assign({ clips: [['Walk_Loop', (t5 - 4.0) * .9, Math.sin(PI * k) * .6], ['Idle_Loop', T + 2, 1 - Math.sin(PI * k) * .6]] }, X), null]; }
  return null;
}
// ---- «Ты засмотрелся» (over_stare): не бросок в камеру, а «тсс». Монитор сам переключается на камеру будки (CAM 00): Жилец стоит за спиной Итана, ладони у него на плечах;
// потом спереди (с места мониторов): он наклоняется к самому уху и, глядя в объектив, подносит длинный палец к губам — «тссс», — и гаснет свет
const HUSH_E = [5.2, -.55], HUSH_M = [5.66, -.98], HUSH_MRY = -.5;   // Итан придвинулся к мониторам (стул на 22 см ближе обычного); Жилец — за его левым плечом, сбоку от спинки (между спинкой и перегородкой 39 см — он стоял в стуле и в перегородке)
const HUSH_SH = { l: [5.37, 1.13, -.92], r: [5.04, 1.13, -.94] }, HUSH_LIPS = [5.3, 1.378, -.612], HUSH_UP = [.18, .91, .36], HUSH_IN = [.67, .16, -.73];   // кончик пальца — чуть выше губ Жильца, палец вдоль его лица (оси головы замерены)   // верх плеч Итана, губы Жильца в наклоне, ухо Итана (замерено по костям)
function hushSet(o) { o.chair.position.z = HUSH_E[1] - .25; o.lamp.intensity = .8; o.lampBulb.visible = true; o.boothLight.intensity = .25; o.l1.intensity = .4; o.l3.intensity = .25; o.l4.intensity = .25; o.amb.intensity = .16; o.flickOn = false;
  o.faceL.position.set(4.85, 1.25, -.15); o.faceL.color.setHex(0xa8c0dc); o.faceL.distance = 2.6; o.faceL.intensity = 1.1; }   // холодный свет мониторов в лицо
const hushEthan = () => ({ clips: [['Sitting_Idle_Loop', 2, 1]], look: [4.62, 1.22, .05] });   // замер и смотрит в монитор, не дышит
function hushMon(lean, k) {   // lean — насколько наклонился к уху; k: 0 — обе ладони на плечах, 1 — левая у губ, указательный палец вверх («тссс»)
  const b = (a, m, c, u) => [0, 1, 2].map(i => (1 - u) * (1 - u) * a[i] + 2 * (1 - u) * u * m[i] + u * u * c[i]);   // рука идёт дугой снаружи, лицо Итана не закрывает
  const lAt = b(HUSH_SH.l, [5.6, 1.28, -.78], HUSH_LIPS, k);
  return { clips: [['Idle_Loop', 1.7, 1]], legs: 'plant', pelvis: [.04 * lean, -.22 * lean, .06 * lean], spine: { to: [5.46, .55 + .3 * (1 - lean), -.58], k: .38 + .3 * lean },
    arm: { r: { at: HUSH_SH.r, by: 'palm', fdir: nrm([-.1, -.45, 1]), pdir: [0, -1, 0], pole: [4.65, 1.5, -1.25] },
      l: { at: lAt, by: k > .5 ? 'tip' : 'palm', fdir: nrm(lin([.1, -.45, 1], HUSH_UP, k)), pdir: nrm(lin([0, -1, 0], HUSH_IN, k)), pole: [5.85, .95, -.9] } },
    fing: { r: ['flat', .55], l: k > .3 ? ['point', 1] : ['flat', .55] } };   // длинные пальцы лежат на плече
}
SH.hush_cam = { loc: 'lobby', pos: [6.42, 2.32, .32], look: [5.18, 1.3, -1.02], fov: 66, cctv: 1, face: { temych: 'terror' }, set: (o) => { hushSet(o);   // «CAM 00 — будка»: камера над дверью будки, о которой Итан не знал
  return [['temych', HUSH_E[0], 0, HUSH_E[1], 0, hushEthan(), null], ['monster', HUSH_M[0], 0, HUSH_M[1], HUSH_MRY, hushMon(.15, 0), 'cam']]; } };   // глядит прямо в камеру: знает, что ты смотришь
// взгляд посередине между лицом Итана и пальцем у губ Жильца: в вертикальном кадре видны оба
SH.hush_face = { loc: 'lobby', pos: [4.66, 1.31, .04], look: [5.265, 1.35, -.69], fov: 34, set: (o, t) => { hushSet(o);
  A.temych.faceSets.k_frozen = { press: 1, wide: 1.3, browUp: 1, browIn: 1, frown: .65, tense: .7 }; A.temych.faceSet = 'k_frozen';   // застыл, не дышит: глаза распахнуты до предела (было: спокойное лицо)
  o.lamp.intensity = .12; o.boothLight.intensity = .04; o.l1.intensity = .1; o.l3.intensity = o.l4.intensity = .05; o.amb.intensity = .085; o.faceL.distance = 2.5; o.faceL.position.set(4.85, 1.0, -.2); o.boothLight.intensity = .12;   // шея и плечи Жильца читаются — голова не «сама по себе»   // свет мониторов снизу   // спереди светят только мониторы: лица и палец видно, тело за спиной уходит в тень (было ровно освещено целиком)
  if (t > 2.25) { o.lamp.intensity = o.boothLight.intensity = o.l1.intensity = o.l3.intensity = o.l4.intensity = o.faceL.intensity = 0; o.amb.intensity = .015; o.lampBulb.visible = false; o.hemi.intensity = 0; }   // после «тссс» гаснет свет
  return [['temych', HUSH_E[0], 0, HUSH_E[1], 0, Object.assign(hushEthan(), { still: 1, look: [4.45, 1.02, .12] }), null], ['monster', HUSH_M[0], 0, HUSH_M[1], HUSH_MRY, hushMon(.4 + .6 * seg(t, 0, .7), seg(t, .35, .95)), 'cam']]; } };   // Итан не в объектив, а мимо — остекленело в экран, где видит его за спиной   // лицо у уха и палец у губ — почти с первого кадра (было: обе ладони на плечах, «семейное фото»)
// ================= смерти: у каждой свой финал =================
// владелец 24 окт: «монстр только «тссс», разнообразия нет», «в игре пишет 14 смертей» — раньше 7 из 14 кончались одним и тем же броском в будке
const _UPV = new T.Vector3(0, 1, 0), _tq = new T.Quaternion(), _tq2 = new T.Quaternion(), _tv = new T.Vector3();
// голова за пределом человеческого (из P.post — поверх взгляда): yaw — вокруг вертикали (как сова), roll — набок до хруста (вокруг оси тела «вперёд»)
function headTwist(C, yaw, roll = 0) {
  const h = C.bone.Head; if (!h) return; C.root.updateWorldMatrix(true, true);
  const wq = h.getWorldQuaternion(new T.Quaternion()), fwd = _tv.set(0, 0, 1).applyQuaternion(C.root.getWorldQuaternion(_tq2));
  wq.premultiply(_tq.setFromAxisAngle(_UPV, yaw).multiply(_tq2.setFromAxisAngle(fwd, roll)));
  h.parent.getWorldQuaternion(_tq2); h.quaternion.copy(_tq2.invert().multiply(wq)); h.updateWorldMatrix(false, true);
}
const step = (t, J) => { let v = J[0][1]; for (const [ts, x] of J) if (t >= ts) v = x; return v; };   // рывками: [[с какой секунды, значение], …]
// рывок Жильца, а не телепорт: держит позу, потом за d секунд дёргается к следующей с перелётом ~10 % и возвратом (глаз успевает прочесть движение)
const backOut = u => 1 + 2.2 * (u - 1) ** 3 + 1.2 * (u - 1) ** 2;
const jerky = (t, J, d = .07) => { let v = J[0][1]; for (let i = 1; i < J.length && t >= J[i][0]; i++) v += (J[i][1] - v) * backOut(clamp((t - J[i][0]) / d, 0, 1)); return v; };
// Итан — это камера: отшатнулся — рывок за up секунд, отдача пружиной, остаётся вжатым на hold (0–1). Камеру ставим заново каждый кадр от base
const recoil = (t, t0, up = .1, hold = .55) => t < t0 ? 0 : t < t0 + up ? Math.sin((t - t0) / up * PI / 2) : hold + (1 - hold) * Math.exp(-(t - t0 - up) * 6) * Math.cos((t - t0 - up) * 11);
const camOff = (sh, base, d, k) => { for (let i = 0; i < 3; i++) sh.pos[i] = base[i] + d[i] * k; };
function lobbyBlack(o) { o.lamp.intensity = o.boothLight.intensity = o.l1.intensity = o.l3.intensity = o.l4.intensity = o.faceL.intensity = 0; o.lampBulb.visible = false; o.amb.intensity = .012; o.hemi.intensity = 0; o.canopy.intensity = 0; o.street.intensity = 0; }
// 1. «Оно пришло»: на пульте камеры гаснут одна за другой (помехи, потом чёрный), лампа будки захлёбывается и гаснет
const COME_OFF = [.55, 1.2, 1.85];
SH.come_mon = { loc: 'lobby', pos: [5.55, 1.42, -1.15], look: [4.5, 1.18, .12], fov: 50, feeds: true,
  feedMap: (i, t) => t > 2.75 ? 'black' : i < 3 && t > COME_OFF[i] ? 'noise' : null,
  set: (o, t) => { const fl = t < 2.2 ? 1 : t < 2.75 ? (Math.sin(t * 61) * Math.sin(t * 23) > 0 ? .8 : .05) : 0;
    o.lamp.intensity = 1.6 * fl; o.lampBulb.visible = fl > .3; o.boothLight.intensity = .6 * fl; o.flickOn = false;
    if (t > 2.75) { o.l1.intensity = .3; o.l3.intensity = .15; o.l4.intensity = .15; o.amb.intensity = .08; }
    return []; } };
// …потом дверь будки изнутри: по двери сверху вниз скребут ногти (дверь мелко дрожит; этап 1, 19 ноя: было три стука — а «оно не стучит», три стука — знак человека),
// ручка медленно идёт вниз, дверь приоткрывается внутрь, в щель по кромке
// заходят длинные серые пальцы и обхватывают её; дверь подаётся шире — в щели его глаз. Темнота.
// (было: три секунды почти чёрного кадра, ручку не видно; рука — синяя «перчатка» висела в воздухе, не держась за дверь)
const COME_K = [.35], COME_SCR = [.3, 2.5];   // скрежет по двери — как в story.js (glass_scratch с 0,3 с): Итан вздрагивает один раз, когда он начался
const CD_E = [6.82, 1.3, -.42];
SH.come_door = { loc: 'lobby', pos: CD_E.slice(), look: [7.46, 1.14, .38], fov: 46, set: (o, t, sh) => {
  camOff(sh, CD_E, [-.03, .006, -.035], COME_K.reduce((s, k) => s + .25 * recoil(t, k + .02, .07, .3), 0) + .7 * seg(t, 3.3, 4.2));   // Итан вздрагивает, когда по двери заскребли; пальцы в щели — пятится от двери
  lobbyBlack(o); o.boothLight.intensity = .14; o.l1.intensity = .45; o.l4.intensity = .35; o.amb.intensity = .09; o.flickOn = false;
  const knock = .5 * seg(t, COME_SCR[0], COME_SCR[0] + .15) * (1 - seg(t, COME_SCR[1] - .4, COME_SCR[1])) * (.5 + .5 * Math.sin(t * 47) * Math.sin(t * 13));   // пока скребёт — дверь мелко дрожит
  o.boothHandle.rotation.z = seg(t, 2.0, 2.9) * .75; const th = seg(t, 2.85, 3.5) * .25 + seg(t, 3.9, 4.4) * .2; o.boothDoor.rotation.y = th + .008 * knock;
  const ex = 6.63 + .86 * Math.cos(th), ez = .45 - .86 * Math.sin(th), r = seg(t, 3.25, 4.0), peek = seg(t, 3.95, 4.35);   // кромка двери
  // свет: сначала блик на ручке; дверь пошла — холодная полоса из холла ложится на кромку, пальцы и лицо в щели (раньше пальцы и лицо тонули в темноте:
  // на кадрах 3,4 и 4,3 с в щели не было видно ничего — финал читался как «дверь и всё»)
  { const g = clamp(th / .26, 0, 1); o.faceL.position.set(lerp(7.25, ex - .22, g), lerp(1.3, 1.34, g), lerp(.05, ez - .3, g)); o.faceL.color.setHex(0xc4d2de); o.faceL.distance = 1.9; o.faceL.intensity = .5 + 2.3 * g + 1.2 * peek; }   // блик на ручке; потом — свет холла из щели на кромку и пальцы
  if (th < .02) return [];
  return [['monster', 7.5, 0, .6, PI + .35, { clips: [['Idle_Loop', 1.7, 1]], legs: 'plant', pelvis: [0, -.22, 0], look: [6.82, 1.3, -.42], spine: { to: [7.42, 1.62, .12], k: .3 * r + .12 * peek },   // заглядывает в щель (сильнее — корпус проходил сквозь косяк); x 7,5 — не в стене холла (было 7,68: плечо и спина в стене на 20 см)
    arm: { l: { at: [ex - .02 - .07 * r + .035 * Math.sin(th), lerp(1.36, 1.3, r), ez - .02 - .02 * r + .035 * Math.cos(th)], by: 'tip', fdir: nrm([-Math.cos(th), -.12, Math.sin(th)]), pdir: nrm([Math.sin(th), 0, Math.cos(th)]), w: r, pole: [8.2, .8, 1.4] } }, fing: { l: ['claw', .45] } }, [6.82, 1.3, -.42]]];
} };
// 11б. «Засмотрелся», второй вариант (по очереди с «тсс»): на седьмом он идёт к объективу рывками — каждый рывок прячет вспышка помех (snow в story.js), между ними стоит как статуя;
// у объектива поднимает лицо в камеру и медленно закрывает её ладонью — темно. (было: между рывками покачивался, помехи звучали сплошным шипением)
SH.stare_lens = { loc: 'corridor', pos: [1.0, 2.75, -.5], look: [-.1, .9, -25], fov: 58, cctv: 1, set: (o, t) => {
  setLamps(o, 5); o.setFloor(7); o.amb.intensity = .25;
  const z = step(t, [[0, -21], [.7, -15.5], [1.3, -10], [1.85, -5.5], [2.35, -1.05]]), up = seg(t, 2.55, 3.2), lens = [.98, 2.66, -.7], x = z > -3 ? .72 : .45;
  faceLit(o, [.95, 2.45, -.6], z > -3 ? 1.5 : 0, 0xc0ccd8);   // у самого объектива его лицо и ладонь видно (свет из-за камеры; раньше ладонь была чёрным силуэтом)
  const P = Object.assign(STILL('Idle_Loop', 1.7), z > -3 ? { arm: { l: { at: lin([.85, 1.95, -.85], lens, up), by: 'palm', fdir: nrm([-.1, 1, .2]), pdir: nrm([.05, .3, 1]), w: up, pole: [1.6, 1.6, -2.2] } }, fing: { l: ['open', 1] } } : {});
  return [['monster', x, 0, z, Math.atan2(1.0 - x, -.5 - z), P, 'cam']];
} };
// 12. «Ты назвался»: в трубке твой же голос повторил «Да. Это я.» — а на камере холла у стекла будки стоит второй Итан с телефоном у уха. Он поворачивает голову к объективу
SH.dbl_cam = { loc: 'lobby', pos: [7.45, 3.85, 5.45], look: [-2.2, .9, -5], fov: 72, cctv: 1, set: (o, t) => {
  const turn = seg(t, 1.6, 2.6); A.ethanThing.faceSets.k_dbl = { dead: 1, smile: 1.05 * seg(t, 2.3, 3.0) }; A.ethanThing.faceSet = 'k_dbl';   // улыбка чуть шире человеческой, а не маска-ухмылка
  o.faceL.position.set(5.3, 2.1, 1.6); o.faceL.color.setHex(0xc0ccdc); o.faceL.distance = 2.4; o.faceL.intensity = .9;   // лицо двойника различимо на камере
  return [['temych', SEAT[0], 0, SEAT[1], 0, 'sit', [5, 1, 3]],
    ['ethanThing', 5.12, 0, 1.0, PI, { clips: [['Idle_Loop', 1.5, 1]], legs: 'feet', fing: { r: ['phone', 1] }, hold: { r: 'phone' },
      arm: { r: { at: C => { const h = C.bone.Head.getWorldPosition(_tv); return [h.x + .1, h.y - .03, h.z + .03]; }, by: 'palm', fdir: [0, .35, -.95], pdir: [-1, 0, 0], w: 1, pole: [5.5, .9, 1.3] } },
      post: C => headTwist(C, -2.4 * turn, .25 * turn) }, [5.15, 1.3, -.6]]];
} };
// 13. «Ты открыл ей»: CAM 01 — замок щёлкнул, створка ушла наружу, она шагнула в дверь, обернулась к объективу и медленно улыбнулась — шире, чем можно.
// (было: на записи она так и стояла снаружи, прижавшись к стеклу, хотя Итан говорит «вошла»)
const DOOR_W = [[.62, 6.6], [-.42, 6.5], [-.1, 5.3]];   // снаружи у правой створки → в проём левой → внутрь (там её не закрывает открытая створка)
function doorWalk(t) {   // путь по прямым с постоянной скоростью: [x, z, ry, идёт?]
  const v = 1.5, L1 = Math.hypot(DOOR_W[1][0] - DOOR_W[0][0], DOOR_W[1][1] - DOOR_W[0][1]), L2 = Math.hypot(DOOR_W[2][0] - DOOR_W[1][0], DOOR_W[2][1] - DOOR_W[1][1]);
  const s = clamp((t - 1.05) * v, 0, L1 + L2), [a, b] = s < L1 ? [DOOR_W[0], DOOR_W[1]] : [DOOR_W[1], DOOR_W[2]], k = s < L1 ? s / L1 : (s - L1) / L2;
  return [lerp(a[0], b[0], k), lerp(a[1], b[1], k), Math.atan2(b[0] - a[0], b[1] - a[1]), s > 0 && s < L1 + L2];
}
const kGrow = k => { A.katyaThing.faceSets.k_grow = { dead: 1, smile: 2.2 * k }; A.katyaThing.faceSet = 'k_grow'; };   // «мёртвая» улыбка растёт с нуля — шире человеческой
SH.door_in = { loc: 'lobby', pos: [-.9, 3.25, 8.1], look: [-.1, 1.15, 6.0], fov: 60, cctv: 1, set: (o, t) => { o.rain.material.opacity = .18; o.reflBoost = .6;
  leafAng(o, 0, 1.25 * seg(t, .25, 1.0));
  const [x, z, wr, walk] = doorWalk(t), turn = seg(t, 2.25, 2.9), ry = lerp(wr, Math.atan2(-.9 - x, 8.1 - z), turn); kGrow(seg(t, 2.6, 3.7));
  return [['katyaThing', x, 0, z, ry, walk ? 'walk' : Object.assign(STILL('Idle_Loop', 1.2), { arm: hangArms(x, z, ry, .9) }), turn > .4 ? 'cam' : null]];
} };
// …а потом — из будки: она стоит посреди холла и улыбается; голова медленно ложится набок до хруста. Гаснет свет у входа — когда моргнёт обратно, она ближе;
// гаснет посреди холла — ещё ближе; остаётся только лампа будки — и её лицо уже в окне будки, в метре. Лампа гаснет.
// (было: смотрели не глазами Итана, а с середины холла; поза «боец» с руками вперёд)
const DT_P = [[0, [3.85, 2.75]], [2.2, [4.3, 1.8]], [2.72, [4.62, 1.2]], [3.22, [4.88, .8]]];   // от середины холла к будке; последняя — в окне будки, где передают ключи (стекла там нет)
const DT_E = [5.75, 1.68, -.62];   // Итан встал и шагнул вбок от мониторов: она видна целиком, в окне будки (было: стоял за блоком мониторов — из-за него торчала только голова, «ку-ку», смешно)
SH.door_tilt = { loc: 'lobby', pos: DT_E.slice(), look: [4.3, 1.45, 1.75], fov: 30, set: (o, t, sh) => {   // Итан встал: глаза над мониторами (сидя мониторы закрывали холл)
  camOff(sh, DT_E, [.035, .012, -.06], .35 * recoil(t, 2.2, .1, .4) + .35 * recoil(t, 2.72, .1, .5) + recoil(t, 3.22, .1, .6));   // каждый раз, как свет возвращается и она ближе, — вздрагивает; лицо в окне — отшатнулся
  o.reflBoost = .5; o.flickOn = false; o.lamp.intensity = .6; o.lampBulb.visible = true; o.boothLight.intensity = .15; o.l1.intensity = .9; o.l3.intensity = .3; o.l4.intensity = 1.0; o.amb.intensity = .12;
  const off = (a, b) => t > a && t < b;   // свет моргнул: в темноте она переходит
  if (t > 2.05) o.l4.intensity = 0; if (off(2.05, 2.2) || t > 2.58) { o.l1.intensity = t > 2.58 ? 0 : .1; o.l3.intensity = .05; }
  if (off(2.58, 2.72) || t > 3.08) { o.amb.intensity = .03; o.canopy.intensity = 0; o.street.intensity = 0; o.l3.intensity = 0; }
  if (off(3.08, 3.22)) { o.lamp.intensity = 0; o.lampBulb.visible = false; }
  if (t > 3.6) lobbyBlack(o);
  const P = step(t, DT_P), ry = Math.atan2(DT_E[0] - P[0], DT_E[2] - P[1]);
  o.faceL.position.set(lerp(P[0], DT_E[0], .2), 1.75, lerp(P[1], DT_E[2], .2)); o.faceL.color.setHex(0xb8c6d8); o.faceL.distance = 2.0; o.faceL.intensity = t > 3.08 ? .85 : 1.0;   // лицо — последнее, что видно
  kGrow(1);
  return [['katyaThing', P[0], 0, P[1], ry, Object.assign(STILL('Idle_Loop', 1.2), { arm: hangArms(P[0], P[1], ry, .9), post: C => headTwist(C, 0, 1.45 * seg(t, .55, 1.65)) }), 'cam']];
} };
// 14. «Ты взял трубку»: звонили Фрэнку, ответил ты. Дыхание в трубке становится дыханием за спиной: Итан медленно оборачивается через плечо —
// в свете экрана телефона, вплотную, лицо. Держит взгляд; голова чуть ложится набок. Экран гаснет — темнота.
// (было: в конце лицо срезано краем кадра снизу слева и сразу гасло — не успевало прочитаться)
const FM_EYE = [5.2, 1.21, -.8];   // глаза Итана: стул придвинут к пульту, как в «тсс» (HUSH_E) — за спинкой как раз встаёт Жилец
const FM_FACE = [5.37, 1.7, -.71];   // куда упирается взгляд в конце: чуть ниже его лица — лицо в верхней половине кадра
SH.fmom_turn = { loc: 'lobby', pos: FM_EYE.slice(), look: [5.15, 1.15, .5], fov: 50, set: (o, t, sh) => {
  o.chair.position.z = HUSH_E[1] - .25; o.lamp.intensity = o.boothLight.intensity = 0; o.lampBulb.visible = false; o.l1.intensity = .35; o.l3.intensity = .2; o.l4.intensity = .2; o.amb.intensity = .07; o.flickOn = false; o.fphone.visible = true;
  const k = ease(clamp((t - .5) / 1.5, 0, 1)), a = PI * .72 * k, d = [Math.sin(a) * .9, -.03 + .1 * k, Math.cos(a)];   // поворот головы через левое плечо — он стоит за левым, нагнулся над ним (за спинкой стула ему места нет: перегородка)
  const f = seg(t, 1.55, 2.0), L = lin([FM_EYE[0] + d[0], FM_EYE[1] + d[1], FM_EYE[2] + d[2]], FM_FACE, f);   // в конце поворота взгляд упирается в его лицо
  // глаза ходят по дуге вокруг шеи (а не крутятся на месте, как штатив); нашёл его лицо в 10 см — дёрнулся назад и замер, вжав голову в плечи
  const fl = recoil(t, 1.62, .12, .6), pr = .1 * (1 - .7 * k);   // к концу поворота дуга меньше: иначе глаза уезжали за его плечо и плечо закрывало лицо
  sh.pos[0] = FM_EYE[0] + pr * Math.sin(a) - .05 * fl; sh.pos[1] = FM_EYE[1] - .015 * fl; sh.pos[2] = FM_EYE[2] - .1 + pr * Math.cos(a) + .1 * (1 - Math.cos(a)) * .5 * k + .02 * fl;
  sh.look[0] = L[0] - .05 * fl; sh.look[1] = L[1]; sh.look[2] = L[2] + .02 * fl;
  o.faceL.position.set(5.22, 1.14, -.9); o.faceL.color.setHex(0xc6d6ff); o.faceL.distance = 1.2; o.faceL.intensity = t > 2.92 ? 0 : .9;   // свет экрана телефона у лица — гаснет
  if (t > 2.92) lobbyBlack(o);
  A.monster.faceSets.k_fm = { dead: 1, smile: 1.3 * seg(t, 2.05, 2.85) }; A.monster.faceSet = 'k_fm';   // держит взгляд — и медленно растёт мёртвая улыбка (было: спокойное лицо до конца)
  return [['monster', HUSH_M[0], 0, HUSH_M[1], HUSH_MRY, { clips: [['Idle_Loop', 1.7, 1]], legs: 'plant', pelvis: [.03, -.18, .05], spine: { to: [5.3, 1.25, -.66], k: .55 }, post: C => headTwist(C, 0, .35 * seg(t, 2.3, 2.8)) }, FM_EYE]];
} };
// 9. «Ты ответил»: её голос — не из трубки, из холла. Тёмное правое стекло будки, за ним никого — тишина — и удар: ладонь и лицо у стекла, в метре от него.
// Улыбка шире человеческой, глаза чёрные, дышит на стекло. Ладонь медленно сползает, оставляя мазок, — лицо не двигается. Лампа гаснет.
// (было: стояла ниже стойки и будто махала рукой — удар не читался, лицо мелко у края стекла)
const K9 = [6.05, .76];   // где стоит: вплотную к правому стеклу (z .45)
// удар с весом (было: появлялась и стояла как приклеенная, Итан не вздрагивал): кадр контакта 1,25 с — лицо и ладонь у стекла, по инерции её чуть отбрасывает
// от стекла и снова прижимает (затухающая отдача), Итан отшатывается от стекла и так и остаётся вжатым в спинку
const K9_E = [5.35, 1.3, -.55];
SH.k9_glass = { loc: 'lobby', pos: K9_E.slice(), look: [6.04, 1.4, .45], fov: 31, set: (o, t, sh) => {   // уже (было 40°): лицо и ладонь крупнее — удар читается (лицо было мелким у края стекла)
  camOff(sh, K9_E, [-.025, .02, -.09], recoil(t, 1.25, .08, .55));
  lobbyBlack(o); o.lamp.intensity = .7; o.lampBulb.visible = true; o.l1.intensity = .15; o.amb.intensity = .05; o.flickOn = false;
  o.faceL.position.set(5.72, 1.22, -.05); o.faceL.color.setHex(0xa8b8d0); o.faceL.distance = 2.7; o.faceL.intensity = 1.25; o.amb.intensity = .085;   // свет шире и ниже: видны шея, плечи, грудь и рука целиком — раньше в темноте висела одна голова с ладонью (владелец, 11 ноя)   // свет мониторов — на её лицо за стеклом
  if (t < 1.25) return [];
  // ладонь сразу плашмя на стекле у самого лица, на высоте рта (рука сбоку выше головы читалась как «привет»); сползает, не ниже края стекла
  const PX = 6.13, Y0 = 1.24, y = lerp(Y0, 1.17, seg(t, 2.3, 3.3));   // сползает, но не ниже края стекла   // ладонь ниже, у подбородка, пальцы наклонены к лицу: «вдавлена в стекло», а не машет (вскинутая у виска читалась как «привет»)
  o.bPalm.visible = false; o.bPalm.scale.set(.7, .7, 1); o.bPalm.position.set(PX, Y0 + .035, .456); o.bPalm.material.opacity = .09;   // отпечаток еле виден (при .22 читался как вторая, призрачная рука над настоящей — владелец, 11 ноя); ровно под ладонью и её размера (был в полтора раза больше руки)
  const L = Y0 - y; o.bSmear.visible = L > .02; o.bSmear.scale.set(.7, Math.max(.01, L), 1); o.bSmear.position.set(PX, Y0 - .02 - L / 2, .457); o.bSmear.material.opacity = .14;
  { const hb = A.katyaThing.bone.Head, hp = hb && t > 1.3 ? hb.getWorldPosition(new T.Vector3()) : null; o.bFog.visible = true; o.bFog.position.set(hp ? hp.x : 6.03, hp ? hp.y - .1 : 1.41, .456); }   // пятно дыхания — у её рта (голова с прошлого кадра), а не в стороне от лица o.bFog.material.opacity = .24 * (.55 + .45 * Math.sin(t * 2.7)) * seg(t, 1.3, 1.9);   // дышит на стекло
  return [['katyaThing', K9[0], 0, K9[1], PI, { clips: [['Idle_Loop', 1.2, 1]], legs: 'feet', spine: { to: [6.02, 1.5, .3], k: .5 - .08 * Math.exp(-(t - 1.25) * 7) * Math.abs(Math.sin((t - 1.25) * 13)) },   // отдача от стекла, не сквозь него
    arm: { r: { at: [PX, y, .475], by: 'palm', fdir: nrm([-.4, 1, .05]), pdir: [0, 0, -1], w: 1, pole: [6.75, .75, 1.0] } }, fing: { r: ['flat', 1] } }, 'cam']];
} };
// 1. «Ты посмотрел ему в лицо»: крупно — лицо Итана в мертвенном свете тринадцатой (помехи мерцают); губы сами шевелятся — он отвечает, не понимая, что отвечает.
// Сигнал рвётся (snow в story.js) — и на CAM 13, там, где стояло оно, теперь стоит Итан: серый, глаза чёрные, лицом в объектив. Ждёт, когда посмотрят на него.
// (было: общий план в тёплом свете, подмена «маской» с ухмылкой — выглядело дёшево)
SH.k1_face = { loc: 'lobby', pos: [5.12, 1.25, -.38], look: [5.2, 1.21, -.99], fov: 30, face: { temych: 'terror' }, set: (o, t) => {
  lobbyBlack(o); o.amb.intensity = .05; o.flickOn = false;
  const surge = seg(t, 1.25, 1.42);   // перед обрывом помехи вспыхивают ярче
  o.faceL.position.set(5.0, 1.12, -.3); o.faceL.color.setHex(0xb8c8dc); o.faceL.distance = 1.6; o.faceL.intensity = (.75 + .35 * Math.sin(t * 47) * Math.sin(t * 13)) * (1 + 1.3 * surge);   // свет снизу-спереди, от монитора
  A.temych.faceSets.k_dread = { mouth: .2 + .25 * seg(t, .5, .8), wide: 1.3, browUp: 1, browIn: 1, tense: .45, frown: .3 }; A.temych.faceSet = 'k_dread';   // ужас на лице с первого кадра (было почти спокойное)
  return [['temych', SEAT[0], 0, SEAT[1], 0, { clips: [['Sitting_Idle_Loop', 2, 1]], still: 1, look: [4.6, 1.15, .1], post: C => { if (t > .7 && t < 1.3) C.talk = .28 + .2 * Math.abs(Math.sin(t * 19)); } }, null]];
} };
// руки висят вдоль тела (у мокап-стойки в этот миг одна рука была отведена в сторону): запястья у бёдер
const hangArms = (x, z, ry, h = 1) => { const R = locR(x, z, ry);
  return { l: { at: R.p(.2 * h, .86 * h, .02), by: 'wrist', w: 1, pole: [.3, 0, -1] }, r: { at: R.p(-.2 * h, .86 * h, .02), by: 'wrist', w: 1, pole: [-.3, 0, -1] } }; };
SH.k1_taken = { loc: 'corridor', pos: C13P, look: [.3, 1.55, -4.6], fov: 44, cctv: 1, set: (o, t) => { c13Set(o, 4); faceLit(o, [.55, 2.3, -3.2], 1.6);
  const ry = Math.atan2(C13P[0] - C13M[0], C13P[2] - C13M[1]);
  return [['ethanThing', C13M[0], 0, C13M[1], ry, Object.assign(STILL('Idle_Loop', 1.7), { arm: hangArms(C13M[0], C13M[1], ry), fing: { l: ['relax', 1], r: ['relax', 1] } }), 'cam']]; } };
// 2. «Ты пошёл на свет»: горит одна лампа — над Итаном. Впереди темнота, никого (глаз ищет). Лампа моргает — и перед ним уже стоит оно: видно только тело,
// лицо выше кадра. Итан поднимает глаза — оно смотрит на него сверху, из-под потолка. Потом медленно складывается к нему, взгляд идёт следом —
// лицо к лицу, в ладони. Лампа гаснет. (было: сразу на весь кадр в ровном свете, в зомби-позе, «кланялось» лицом ему в грудь)
const K2_A = [0, 1.66, -6], K2_B = [0, 2.2, -2.25], K2_C = [0, 1.66, -1.75];   // куда смотрит Итан: прямо → вверх на его лицо → вниз, за лицом
// (было: складывалось плавно, как на лифте; Итан стоял камнем.) Теперь — рывками с паузами: дёрнулось — замерло — дёрнулось; Итан, увидев его, вздрагивает
// и отшатывается, а пока лицо опускается к нему — отклоняется назад и сжимается (не может ни отвернуться, ни уйти)
const K2_E = [0, 1.6, -1.2];
SH.k2_last = { loc: 'corridor', pos: K2_E.slice(), look: K2_A.slice(), fov: 50, set: (o, t, sh) => {
  o.flickOn = false; const blink = t > 1.2 && t < 1.34;   // лампа моргнула — а когда зажглась, оно уже стоит
  lampSet(o, i => i === 0 && !blink); o.lamps[0].li.distance = 4.2; o.amb.intensity = .035; o.S.fog.density = .06;
  const b = jerky(t, [[0, 0], [2.95, .22], [3.18, .42], [3.36, .68], [3.55, .86], [3.7, 1]], .09);   // складывается к нему рывками
  camOff(sh, K2_E, [0, .012, .07], recoil(t, 1.36, .1, .5)); sh.pos[1] -= .04 * b; sh.pos[2] += .05 * b;   // вздрогнул и отшатнулся; потом отклоняется от лица
  const L = lin(lin(K2_A, K2_B, seg(t, 1.85, 2.6)), K2_C, seg(t, 2.95, 3.75)); sh.look[0] = L[0]; sh.look[1] = L[1] - .04 * b; sh.look[2] = L[2] + .05 * b;
  if (t < 1.27) return [];
  o.faceL.position.set(0, 1.28, -1.32); o.faceL.color.setHex(0xb8c4cc); o.faceL.distance = 1.1; o.faceL.intensity = 1.3 * b;   // когда наклоняется, лампа светит ему в затылок — лицо подсвечено снизу (свет от пола и куртки Итана), иначе чёрный силуэт
  return [['monster', .04, 0, -2.25, 0, { clips: [['Idle_Loop', 1.7, 1]], legs: 'feet', spine: { to: [0, 1.6, -1.25], k: .9 * b }, pelvis: [0, -.12 * b, -.1 * b], post: C => headTwist(C, 0, .18 * seg(t, 2.55, 2.95)) }, 'cam']];
} };
// 3. «Ты отозвался»: «Спасибо, Итан» — и склейка на правое стекло будки: вплотную за ним, согнувшись к стеклу, стоит оно и держит у рта рацию Гаса.
// Снизу его лицо освещает лампа будки, на стекле от дыхания мутное пятно. Медленно опускает рацию — губы ещё шевелятся. Лампа гаснет.
// (было: маленькая фигура посреди кадра за стойкой, рацию не разглядеть, полкадра закрывали спинки мониторов и абажур)
const K3 = [6.02, 1.06];   // где стоит: за правым стеклом (x 5.62–6.56, z .45), лицо внутри стекла по высоте
SH.k3_radio = { loc: 'lobby', pos: [5.25, 1.22, -.92], look: [6.0, 1.76, .45], fov: 34, set: (o, t) => {
  lobbyBlack(o); o.lamp.intensity = .45; o.lampBulb.visible = true; o.amb.intensity = .04; o.l1.intensity = .06; o.flickOn = false;
  o.faceL.position.set(5.98, 1.42, .28); o.faceL.color.setHex(0xffc890); o.faceL.distance = .95; o.faceL.intensity = 1.0;   // тёплый свет лампы будки снизу — на лицо за стеклом; тело в тени
  const down = seg(t, 1.45, 2.2), hp = A.monster.bone.Head ? A.monster.bone.Head.getWorldPosition(new T.Vector3()) : null;   // голова с прошлого кадра (стоит неподвижно) — пятно дыхания у рта
  o.bFog.visible = !!hp; if (hp) { o.bFog.position.set(hp.x, hp.y - .06, .456); o.bFog.material.opacity = .2 * (.5 + .5 * Math.sin(t * 2.4)) * seg(t, .1, .7); }   // дышит на стекло
  if (t > 2.95) { lobbyBlack(o); o.bFog.visible = false; }
  return [['monster', K3[0], 0, K3[1], PI, { clips: [['Idle_Loop', 1.7, 1]], legs: 'feet', pelvis: [0, -.1, 0], spine: { to: [K3[0], 1.62, .3], k: .55 }, hold: { l: 'radio' }, fing: { l: ['grip', 1] },
    arm: { l: { at: C => { const h = C.bone.Head.getWorldPosition(_tv); return [h.x - .08, h.y - .2 - .36 * down, h.z - .1 + .04 * down]; }, by: 'palm', fdir: radioF('l', [.15, 1, -.2], [.35, -.1, -1]), pdir: [.35, -.1, -1], w: 1, pole: [5.45, .95, 1.45] } },   // рация у рта сбоку: лицо видно
    post: C => { if (down > .6 && t < 2.95) C.talk = .22 + .18 * Math.abs(Math.sin(t * 9)); } }, [5.25, 1.22, -.92]]];
} };
// 4. «Оно обернулось»: тот, в дальнем конце тринадцатой, уже повернулся к объективу (так кончилась мини-игра со шнуром) — лица нет, только рот. Наезд, пока Итан рассказывает
SH.k4_far = { loc: 'corridor', pos: C13P, look: [-.1, 1.6, -13], fov: 40, cctv: 1, face: { monster: 'grin' }, set: (o, t) => { c13Set(o, 4); faceLit(o, [.3, 2.5, -11.6], 1.7);
  return [['monster', 0, 0, -13, 0, STILL('Idle_Loop', 1.7), 'cam']]; } };
// финал: рывок помех — оно уже ближе. Тело рывками отворачивается от объектива, а голова остаётся лицом к нему (за человеческий предел — хруст);
// потом оно идёт к камере спиной вперёд, лицо глядит поверх лопаток; у самого объектива — помехи и обрыв.
// (было: в первом кадре оно стояло лицом к камере, а в финале снова спиной — склейка противоречила; шло к объективу, не переставляя ног)
const K4_TURN = [[.55, 0], [.82, .75], [1.08, 1.5], [1.34, 2.3], [1.6, PI]];
SH.k4_owl = { loc: 'corridor', pos: C13P, look: [-.05, 1.6, -6], fov: 44, cctv: 1, face: { monster: 'grin' }, set: (o, t) => { c13Set(o, 5);
  const ry = jerky(t, K4_TURN, .06), w = clamp((t - 2.05) / 1.5, 0, 1), z = lerp(-7.2, -2.2, w * w * (3 - 2 * w)); faceLit(o, [.25, 2.45, z + 1.2], 1.9);   // поворот — рывок за 60 мс с перелётом, а не телепорт: читается глазом, хруст — на каждом рывке
  const walk = w > 0 && w < 1;
  return [['monster', 0, 0, z, ry, { clips: [[walk ? 'Zombie_Walk_Fwd_Loop' : 'Idle_Loop', walk ? -(z + 7.2) * .55 : 1.7, 1]], legs: walk ? undefined : 'feet', post: C => headTwist(C, -ry, .12 * ry / PI) }, null]];
} };
// 5. «Оно услышало»: взгляд Итана из-под стола — пол будки, ножка стула, темно, тихо. В просвет под столешницей медленно опускается её лицо:
// присела и заглядывает, голова ложится набок дальше, чем может человек, улыбка. Держит взгляд — рука отходит назад и выстреливает к нему:
// обрыв в черноту, слышно, как его волокут. (было: «лягушкой» в кадре с первой секунды при ровном свете, руки не видно, рывок камеры читался как сбой)
const K5 = [5.84, -.95];
// бросок — всем телом, не одной рукой (было: рука выстреливала, а корпус и голова стояли): на замахе плечо и корпус уходят назад, голова ещё сильнее набок;
// в броске корпус подаётся за рукой к нему, голова выпрямляется по инерции. Итан: увидел лицо — вжался назад под столешницей; кадр контакта — его рвёт к ней
const K5_E = [5.75, .48, -.24];
SH.k5_drag = { loc: 'lobby', pos: K5_E.slice(), look: [5.8, .6, -1.2], fov: 50, set: (o, t, sh) => {
  lobbyBlack(o); o.l1.intensity = .2; o.amb.intensity = .06; o.flickOn = false;
  o.faceL.position.set(6.35, .6, -.5); o.faceL.color.setHex(0xa8b8cc); o.faceL.distance = 1.7; o.faceL.intensity = 1.1;   // холодный свет холла сбоку — на её лицо в просвете
  const dn = seg(t, .45, 1.3), back = seg(t, 1.42, 1.6), hit = clamp((t - 1.6) / .22, 0, 1);   // сначала видно только ноги (стоит у стола); присела к просвету; рука отошла — и выстрелила (без смягчения в конце)
  const shrink = seg(t, .95, 1.35), yank = clamp((t - 1.74) / .09, 0, 1) ** 2;   // вжимается назад, пока опускается лицо; схватила — дёрнуло к ней
  camOff(sh, K5_E, [-.01, .015, .06], shrink); sh.pos[0] += .03 * yank; sh.pos[1] -= .05 * yank; sh.pos[2] -= .16 * yank;
  sh.look[0] = 5.8 + .08 * yank; sh.look[1] = .6 - .02 * shrink - .25 * yank; sh.look[2] = -1.2;
  // рука упёрта в пол когтями (ниже кадра), перед броском поджимается — и выстреливает снизу в объектив (было: раскрытая ладонь у лица — «привет», смешно)
  return [['katyaThing', K5[0], 0, K5[1], 0, { clips: [['Idle_Loop', 1.2, 1 - dn], ['Crouch_Idle_Loop', 1.0, dn]], legs: 'plant', pelvis: [0, -.06 * dn, -.04 * back + .1 * hit], spine: { to: lin([5.78, .45, -.3], [5.76, .4, -.1], hit), k: .1 + .38 * dn - .1 * back + .3 * hit },
    arm: { r: { at: lin(lin([5.66, .05, -.72], [5.7, .14, -.86], back), [5.76, .5, -.33], hit * hit), by: 'palm', fdir: nrm(lin([0, -.1, 1], [0, 1, .25], hit)), pdir: nrm(lin([0, -1, 0], [0, -.15, 1], hit)), w: Math.max(dn, hit) } },
    fing: { r: ['claw', .85 - .3 * hit * (1 - hit) * 4 + .1 * hit] },   // когти раскрываются в броске и смыкаются на нём в кадре контакта (было: раскрытая ладонь в объектив — снова «привет»)
    post: C => headTwist(C, 0, 1.1 * dn + .18 * back - .45 * hit) }, [5.75, .48, -.24]]];
} };
// 6. «Темнота догнала»: упал. Камера на полу, голова чуть набок (коридор читается); лампы гаснут с дальнего конца к нему. В последнем свете из темноты
// слышны босые шаги, лампа моргает — и босые серые ступни уже стоят перед лицом. Оно медленно присаживается: в кадр сверху опускается лицо, голова набок. Последняя лампа гаснет.
// (было: камера завалена почти на 90°, поза — клубок рук и ног, ступни не читались)
// падение с весом (было: с первого кадра уже лежит, камера неподвижна): голова летит вниз с высоты глаз с ускорением, удар об пол (boom) — отскок на пару сантиметров,
// голову по инерции перекатывает набок дальше, чем она остаётся, и она возвращается; потом лежит, дышит (лёгкое покачивание), а когда сверху опускается лицо — вжимается щекой в пол
const K6_E = [.15, .2, -5.2], K6_HIT = .14;
const k6Roll = t => { if (t < K6_HIT) return lerp(-.08, .12, t / K6_HIT); const u = t - K6_HIT; return .3 + .32 * Math.exp(-u * 5) * Math.cos(u * 9) - .2 * Math.exp(-u * 14) + .02 * Math.sin(t * 2) + .06 * recoil(t, 3.25, .12, .7); };
SH.k6_fall = { loc: 'corridor', pos: K6_E.slice(), look: [.05, .45, -12], fov: 54, roll: k6Roll, set: (o, t, sh) => {
  { const g = clamp(t / K6_HIT, 0, 1), u = Math.max(0, t - K6_HIT), y = t < K6_HIT ? lerp(.95, .2, g * g) : .2 + .045 * Math.exp(-u * 7) * Math.abs(Math.sin(u * 15));   // падает с ускорением, отскок затухает
    const br = .004 * Math.sin(t * 5.2) * clamp(u * 2, 0, 1), fl = .035 * recoil(t, 3.25, .12, .7);   // часто дышит щекой у пола; лицо опустилось — вжался
    sh.pos[0] = K6_E[0] - .1 * (1 - g) + fl; sh.pos[1] = y + br - .5 * fl; sh.pos[2] = K6_E[2] + .35 * (1 - g);   // падал вперёд, лицом по ходу бега
    sh.look[0] = .05; sh.look[1] = t < K6_HIT ? lerp(-1.6, .45, g) : .45 + .9 * Math.exp(-u * 8) * Math.sin(u * 10) * .3; sh.look[2] = -12; }   // в падении взгляд в пол, после удара голову подбрасывает и опускает
  o.flickOn = false; const n = 7 - [.35, .7, 1.05, 1.4, 1.75].filter(x => t >= x).length;   // от дальнего конца остаются две ближние лампы; та, что за ним, гаснет следом
  const fl = (t > 2.12 && t < 2.42) || (t > 2.58 && t < 2.64);   // последняя лампа моргнула: в темноте — босые шаги; свет вернулся — оно уже стоит перед лицом
  lampSet(o, i => i < n && !(i === 0 && t > 2.05) && !(i === 1 && fl) && t < 3.8); o.amb.intensity = fl ? .015 : .04;
  if (t < 2.12) return [];
  const cr = seg(t, 2.8, 3.55);   // (было: шло к камере, и в низком ракурсе колено задиралось к груди — читалось как пинок)
  o.faceL.position.set(.15, .45, -5.45); o.faceL.color.setHex(0xb8c4cc); o.faceL.distance = 1.2; o.faceL.intensity = fl ? 0 : 1.2 * cr;   // лампа за ним — лицо подсвечено снизу, иначе чёрный силуэт
  return [['monster', .12, 0, -6.1, 0, { clips: [['Idle_Loop', 1.7, 1 - cr], ['Crouch_Idle_Loop', 1.0, cr]], legs: 'plant',
    pelvis: [0, -.2 * cr, 0], spine: { to: [.15, .1, -5.2], k: .85 * cr }, post: C => headTwist(C, 0, -.9 * cr) }, [.15, .2, -5.2]]];
} };
// 7. «Ты пошёл на зов»: голос был за спиной. Итан резко оборачивается — луч фонаря выхватывает его вплотную: стоит, наклонившись к лицу, рот закрыт.
// Пауза. Рот медленно раскрывается шире человеческого — и подаётся вперёд: удар, обрыв в черноту (дверь серверной за спиной так и заперта).
// (было: рот сразу раскрыт до упора (лицо «крик» с первого кадра), две секунды неподвижной картинки, хлопок двери не совпадал ни с чем)
// Атака по фазам (было: в конце тело съезжало на 8 см вперёд — бросок не читался, руки висели): увидел — Итан отшатывается (камера дёргается назад);
// пауза, рот раскрывается; замах — оно чуть выпрямляется и отводит корпус назад, когти поднимаются; бросок с ускорением — корпус и лицо в объектив,
// когти смыкаются у его плеч (кадр контакта ~2,8 с), Итана отбрасывает назад — и хлопает дверь, обрыв
const K7_E = [-.25, 1.58, -13.05];
SH.k7_behind = { loc: 'basement', pos: K7_E.slice(), look: [.05, 1.2, -14.9], fov: 52, set: (o, t, sh) => {
  const k = ease(clamp((t - .25) / .85, 0, 1)), a = PI * k, d = [Math.sin(a) * .9 + .3 * (1 - k), -.35 + .55 * k, -Math.cos(a) * 1.85];   // оборачивается через левое плечо (быстро); в конце взгляд и фонарь — на его лицо, а не на грудь
  const rear = seg(t, 2.3, 2.6), go = clamp((t - 2.6) / .2, 0, 1) ** 2, hit = recoil(t, 2.72, .08, .8);   // замах → бросок с ускорением (без торможения) → удар
  const jolt = recoil(t, .78, .12, .55);   // увидел его вплотную — дёрнулся назад и так и остался
  camOff(sh, K7_E, [.02, -.015, -.06], jolt); sh.pos[1] -= .05 * hit; sh.pos[2] -= .1 * hit; sh.pos[0] += .03 * hit;   // в момент удара голову отбрасывает назад и вниз
  sh.look[0] = sh.pos[0] + d[0] + .06 * hit; sh.look[1] = sh.pos[1] + d[1]; sh.look[2] = sh.pos[2] + d[2];
  { const fk = seg(t, .85, 1.25), hb = A.monster.bone.Head; if (fk > 0 && hb) { const hp = hb.getWorldPosition(new T.Vector3());   // нашёл лицо — глаза держат его (голова с прошлого кадра): лицо в кадре и в броске, а не срезано верхним краем
    sh.look[0] = lerp(sh.look[0], hp.x + .06 * hit, fk); sh.look[1] = lerp(sh.look[1], hp.y - .12, fk); sh.look[2] = lerp(sh.look[2], hp.z, fk); } }
  o.sdoor.rotation.y = 0; o.sdoor.updateMatrixWorld(true); bsLight(o, false, null);   // этап 1 (19 ноя): дверь серверной заперта изнутри — Итан её не открывал, он ответил голосу (было: распахнута и захлопывалась в финале)
  o.etorch.intensity = (CINE ? 3 : 5) * (1 - .3 * k);   // вплотную фонарь слепил бы грудь — чуть слабее
  o.etorch.position.set(sh.pos[0] + .12, 1.12, sh.pos[2] + .05); o.etorch.target.position.set(sh.look[0], sh.look[1] - .1, sh.look[2]); o.etorch.target.updateMatrixWorld();   // фонарь у пояса светит туда, куда он смотрит — лицо освещено снизу (раньше эта строка стояла в комментарии)
  if (k < .55) return [];
  A.monster.scream = .82 * seg(t, 1.75, 2.55);
  A.monster.faceSets.k_k7 = { dead: 1, smile: 1.4 * seg(t, .8, 1.7) }; A.monster.faceSet = 'k_k7';   // в паузе медленно растёт мёртвая улыбка, потом челюсть отваливается (было: спокойное лицо — «бабушка на фото»)
  const cl = Math.max(.35 * seg(t, 2.35, 2.6), go);   // когти: на замахе приподнимаются, в броске — к его плечам
  const claw = (sx) => ({ at: [K7_E[0] + sx * lerp(.32, .2, go), lerp(1.05, 1.5, cl), lerp(-12.5, -12.82, go)], by: 'tip', fdir: nrm([-sx * .2, .25, -1]), pdir: nrm([-sx * .7, -.3, -.5]), w: cl, pole: [K7_E[0] + sx * .9, 1.15, -12.3] });   // когти по краям кадра, в 20 см перед лицом
  return [['monster', -.27, 0, -12.36, PI, { clips: [['Idle_Loop', 1.7, 1]], legs: 'plant', pelvis: [0, -.2 + .05 * rear - .12 * go, .02 + .06 * rear - .22 * go], spine: { to: [-.25, 1.45, -13.0], k: .55 - .14 * rear + .35 * go },
    arm: { l: claw(-1), r: claw(1) }, fing: { l: ['claw', .5 + .4 * go], r: ['claw', .5 + .4 * go] },
    post: C => { headTwist(C, 0, .38 * seg(t, .8, 1.9) - .12 * rear * (1 - go));
      if (go > 0) { C.root.position.z -= .3 * go; C.root.position.y -= .08 * go; C.root.updateMatrixWorld(true); } } }, [-.25, 1.58, -13.05]]];   // бросок всем телом: лицо летит в объектив (наклон корпуса один поднимал голову выше кадра)
} };
// 8. «Фрэнк ушёл на голос»: луч фонаря поднимается по стойкам — на потолке над кругом, вниз головой, висит оно; голос Фрэнка — из его рта.
// Замолкает, голова медленно ложится набок — и оно срывается с потолка вниз, на Итана: обрыв в черноту на лету. (было: свет просто медленно гас)
// (было: тело вниз головой сложено «акробатом», нога торчит вверх, саван облепил фигуру — смешно. Меньше значит больше: почти темнота,
// фонарь выхватывает только лицо и свисающие волосы, руки тянутся вниз; тело и ноги — в черноте под потолком)
const K8_YAW = .56, k8Pre = (r, lx, wy, lz) => { const c = Math.cos(K8_YAW), s = Math.sin(K8_YAW), x = -lx;   // цель руки после переворота (локально: x вбок, z к камере, wy — высота в мире) →
  return [r[0] + x * c + lz * s, r[1] - (wy - r[1]), r[2] - x * s + lz * c]; };   //   точка до переворота (IK решается до P.post, который переворачивает тело)
// срыв по фазам (было: руки висели одинаково до самого конца, тело просто съезжало вниз): голова ложится набок → замах — оно поджимается к потолку,
// руки уходят назад, к телу → срывается с ускорением, руки выбрасывает вперёд к нему → Итан отшатывается (камера дёргается назад и вниз) — обрыв
const K8_E = [2.45, 1.15, 3.2];
SH.k8_ceiling = { loc: 'server', pos: K8_E.slice(), look: [1.4, 1.2, 1.5], fov: 38, set: (o, t, sh) => {   // уже: лицо крупнее, остальное — темнота
  const up = seg(t, .3, 1.4), jolt = recoil(t, 3.44, .07, .9); camOff(sh, K8_E, [.05, -.07, .07], jolt); sh.look[1] = 1.2 + .6 * up + .15 * jolt;   // взгляд поднимается к лицу; тело выше — в черноте
  srvDim(o); o.cold.intensity = o.cold2.intensity = 0; o.rim.intensity = 0; o.amb.intensity = .008; o.candleK = lerp(.3, .03, seg(t, .3, 1.2)); o.redK = .12 * (1 - seg(t, .3, 1.0));   // свеча и красная гаснут — тело в черноте
  const tl = seg(t, 2.95, 3.3), coil = seg(t, 3.1, 3.36) * (t < 3.38 ? 1 : 0), f = clamp((t - 3.38) / .2, 0, 1), dp = lin([1.45, 3.0 + .1 * coil, 1.6 - .05 * coil], [2.05, 1.9, 2.6], f * f);   // поджалось — сорвалось — летит на камеру с ускорением
  o.faceL.position.set(dp[0] + .25, dp[1] - 1.6, dp[2] + .35); o.faceL.color.setHex(0xfff0d8); o.faceL.distance = 1.9; o.faceL.intensity = 2.6 * seg(t, .7, 1.4); o.amb.intensity = .03;   // луч шире: шея, плечи и свисающие руки — тело, а не одна голова в черноте (владелец, 11 ноя)   // луч фонаря находит только лицо и руки
  const reach = seg(t, 1.5, 2.6) + 1.6 * f, hy = dp[1] - 1.75 - .15 * reach;   // замах — телом (поджимается к потолку); руки не подтягиваем — перекрещивались у лица   // руки: тянутся → на замахе подтягиваются к телу → выброшены вперёд
  return [['monster', dp[0], dp[1], dp[2], K8_YAW, { clips: [['Crouch_Idle_Loop', 1.0, 1]],   // сложенные ноги («акробат») теперь в черноте — видно лицо, волосы и руки
    arm: { l: { at: k8Pre(dp, .26 + .1 * f, hy, .25 + .2 * reach), by: 'tip', w: .85 }, r: { at: k8Pre(dp, -.26 - .1 * f, hy, .25 + .2 * reach), by: 'tip', w: .85 } }, fing: { l: ['claw', .4 + .5 * f], r: ['claw', .4 + .5 * f] },   // руки свисают и тянутся к нему; в броске когти растопырены
    post: C => { C.root.rotation.z = PI; C.root.updateMatrixWorld(true); headTwist(C, 0, .7 * tl); if (t > 1.7 && t < 2.9) C.talk = .2 + .25 * Math.abs(Math.sin(t * 8));
      if (C.headFwd) {   // перевёрнутая голова смотрела макушкой вниз — снизу была видна только макушка; лицо (вверх ногами) поворачиваем к Итану
        const h = C.bone.Head, wq = h.getWorldQuaternion(new T.Quaternion()), f = C.headFwd.clone().applyQuaternion(wq), want = new T.Vector3(...sh.pos).sub(h.getWorldPosition(new T.Vector3())).normalize();
        wq.premultiply(new T.Quaternion().setFromUnitVectors(f, want).slerp(new T.Quaternion(), .2)); h.parent.getWorldQuaternion(_tq2); h.quaternion.copy(_tq2.invert().multiply(wq)); h.updateWorldMatrix(false, true); } } }, null]];
} };
// ---- те же смерти в часы, когда Итана нет в будке (S.shift.hourDeath в story.js): S6 — он в серверной, на корточках в круге соли рядом с Фрэнком;
// вместо будки — камера серверной (CAM −2, как srv_cam). A1 — прячется под стойкой: на камере холла его не видно
const SRV_CAM = [-3.3, 2.65, -2.6];
// 20 ноя (тестер: в серверной Итан на корточках, «рука висит до пола»): ладонь лежит на своём колене (точка — от костей ног этого кадра), а не болтается
const kneeRest = sd => ({ at: C => { const k = C.bone['calf_' + sd].getWorldPosition(new T.Vector3()), th = C.bone['thigh_' + sd].getWorldPosition(new T.Vector3()); k.lerp(th, .14); return [k.x, k.y + .05, k.z]; },
  fdir: C => { const k = C.bone['calf_' + sd].getWorldPosition(new T.Vector3()), th = C.bone['thigh_' + sd].getWorldPosition(new T.Vector3()), d = k.sub(th).normalize(); return [d.x, d.y * .3 - .15, d.z]; }, by: 'palm', pdir: [0, -1, 0], w: 1 });
const kneeArms = () => ({ l: kneeRest('l'), r: kneeRest('r') });
const srvPair = (t, look) => [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t), null], ['temych', EIN[0], 0, EIN[1], -.75, { clips: [['Crouch_Idle_Loop', t + .5, 1]], arm: kneeArms(), fing: { l: ['relax', 1], r: ['relax', 1] }, look: look || FRANK_HEAD }, null]];
// «Посмотрел в лицо»: на камере серверной Итан поднимает лицо к объективу, на лице мерцает свет помех, губы сами шевелятся — отвечает
SH.k1_srv = { loc: 'server', pos: SRV_CAM, look: [1.9, .85, 2.4], fov: 46, cctv: 1, face: { temych: 'gasp', mihalych: 'fear' }, set: (o, t) => { srvSet(o);
  o.faceL.position.set(1.7, 1.15, 1.5); o.faceL.color.setHex(0xb8c8dc); o.faceL.distance = 2.2; o.faceL.intensity = 1.1 * (.65 + .35 * Math.sin(t * 47) * Math.sin(t * 13));
  A.temych.faceSets.k_dread = { mouth: .2 + .25 * seg(t, .5, .8), wide: 1.3, browUp: 1, browIn: 1, tense: .45, frown: .3 }; A.temych.faceSet = 'k_dread';
  const P = srvPair(t, t > .45 ? SRV_CAM : FRANK_HEAD); P[1][5].post = C => { if (t > .7 && t < 1.3) C.talk = .3 + .2 * Math.abs(Math.sin(t * 19)); }; return P; } };
// «Оно пришло»: камера серверной рвётся помехами (story.js snow); когда картинка вернулась — свечи погасли, круг соли разомкнут в трёх местах,
// за спиной Итана у прорехи стоит оно, смотрит в объектив, потом наклоняется в прореху к нему
const COME_M = [2.6, 1.32];
SH.come_srv_a = { loc: 'server', pos: SRV_CAM, look: [1.9, .9, 2.2], fov: 46, cctv: 1, face: { mihalych: 'worry' }, set: (o, t) => { srvSet(o); return srvPair(t); } };
// ---- этап 1 (19 ноя): в серверной соль держит («пока круг цел — оно только говорит», sv10). Смерти часа S6 больше не проходят сквозь целый круг:
//   «оно пришло» — три пропуска = три прорехи в круге (saltBroken), оно стоит у прорехи и наклоняется в неё;
//   «посмотрел в лицо» и «засмотрелся» — Итан ответил и сам встаёт и шагает за черту (srv_out), там его и берут;
//   «Фрэнк ушёл на голос» — Итан бросается за ним: ладони и грудь — за чертой (k8_reach).
const SALT_GAPS = [-1.14, -2.55, 1.9];   // углы прорех (от центра круга: cos — по x, sin — по z); первая — там, где стоит оно
function saltBroken(o) {
  if (!o.saltGapTx) { const src = TX.salt.image; if (!src || !src.width) return;
    const cv = document.createElement('canvas'); cv.width = src.width; cv.height = src.height; const x = cv.getContext('2d'), w = cv.width, R = w * 26 / 64;
    x.drawImage(src, 0, 0); x.globalCompositeOperation = 'destination-out';
    for (const a of SALT_GAPS) { x.beginPath(); x.moveTo(w / 2, w / 2); x.arc(w / 2, w / 2, w * .5, a - .17, a + .17); x.closePath(); x.fill(); }
    x.globalCompositeOperation = 'source-over';   // соль из прорех размётана внутрь круга
    for (const a of SALT_GAPS) for (let i = 0; i < 30; i++) { const b = a + (rnd() - .5) * .55, r = R * (.6 + rnd() * .32); x.fillStyle = `rgba(240,238,230,${.4 + rnd() * .4})`; x.beginPath(); x.arc(w / 2 + Math.cos(b) * r, w / 2 + Math.sin(b) * r, w / 64 * (.3 + rnd() * .45), 0, 7); x.fill(); }
    const tx = new T.CanvasTexture(cv); for (const k of ['magFilter', 'minFilter', 'wrapS', 'wrapT', 'encoding', 'colorSpace', 'anisotropy', 'generateMipmaps']) if (TX.salt[k] !== undefined) tx[k] = TX.salt[k]; tx.needsUpdate = true; o.saltGapTx = tx;
    o.xreset = () => { const m = o.salt.material; if (m.map !== TX.salt) { m.map = TX.salt; if (m.emissiveMap) m.emissiveMap = TX.salt; } }; }
  const m = o.salt.material; m.map = o.saltGapTx; if (m.emissiveMap) m.emissiveMap = o.saltGapTx;
}
SH.come_srv_b = { loc: 'server', pos: SRV_CAM, look: [1.9, .9, 2.2], fov: 46, cctv: 1, face: { mihalych: 'fear' }, set: (o, t) => { srvDim(o); saltBroken(o); o.candleK = .1;   // свечи погасли (раньше тик серверной зажигал их обратно)
  faceLit(o, [2.0, 2.4, .4], 1.3, 0xc0ccd8); const lean = jerky(t, [[0, 0], [1.5, .35], [1.78, .6], [2.02, .85], [2.22, 1]], .08);   // наклоняется через черту рывками, с паузами (было: плавно, как кран)
  const M = [COME_M[0] - .2, COME_M[1]];   // на 20 см от стойки: наклоняясь к Итану, плечом входил в неё
  return srvPair(t).concat([['monster', M[0], 0, M[1], Math.atan2(EIN[0] - M[0], EIN[1] - M[1]), Object.assign(STILL('Idle_Loop', 1.7), { pelvis: [0, -.15 * lean, 0], spine: { to: [EIN[0], 1.0, EIN[1]], k: .6 * lean },
    post: C => headTwist(C, 0, .32 * seg(t, .5, 1.4)) }), 'cam']]); } };   // пока стоит и смотрит в объектив — голова медленно ложится набок
// «Посмотрел в лицо» / «засмотрелся» в серверной (этап 1): Итан ответил — и сам идёт на зов. Встаёт с корточек и шагает через черту в темноту; Фрэнк тянется удержать и мотает головой
const SRV_OUT = [2.2, 1.35];   // где Итан останавливается — в 1,5 м от центра круга (радиус круга 0,89)
SH.srv_out = { loc: 'server', pos: SRV_CAM, look: [1.9, .9, 2.2], fov: 46, cctv: 1, face: { mihalych: 'fear' }, set: (o, t) => { srvDim(o);
  A.temych.faceSets.k_frozen = { press: 1, wide: 1.2, browUp: .8, tense: .5 }; A.temych.faceSet = 'k_frozen';   // лицо пустое: идёт не сам
  const t0 = .2, up = seg(t, t0, t0 + .9), ry = Math.atan2(SRV_OUT[0] - EIN[0], SRV_OUT[1] - EIN[1]), look = [SRV_OUT[0] - .6, 1.55, SRV_OUT[1] - 3];
  const E = t < t0 + .85 ? ['temych', EIN[0], 0, EIN[1], lerp(-.75, ry, seg(t, t0 + .1, t0 + .85)), { clips: [['Crouch_Idle_Loop', t + .5, 1 - up], ['Idle_Loop', 1.4, up]], legs: 'plant', look }, null]
    : shuffle('temych', EIN, SRV_OUT, t, t0 + .85, 1.7, ry, { look });
  const R = FR(), k = seg(t, .7, 1.5), F = frankSit(t, { look: [E[1], 1.3, E[3]], no: .5 * seg(t, 1.2, 1.8) });   // тянется правой вслед и мотает головой: «нет»
  F.arm = { r: { at: [lerp(FRANK[0], E[1], .5), .85, lerp(FRANK[1], E[3], .5)], by: 'palm', fdir: nrm([E[1] - FRANK[0], .1, E[3] - FRANK[1]]), pdir: [0, -1, 0], w: .85 * k } }; F.fing = { r: ['open', k] };
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, F, null], E]; } };
// «Засмотрелся»: Итан уже за чертой (srv_out) — оно стоит у него за плечом, ладонь на плече, наклоняется к уху и подносит палец к губам, глядя в объектив («тссс»), — камера гаснет
// (было до этапа 1: Итан сидел в целом круге, а оно клало ему ладонь на плечо сквозь соль)
const HUSH_SM = [SRV_OUT[0] + .2, SRV_OUT[1] + .37];
SH.hush_srv = { loc: 'server', pos: SRV_CAM, look: [2.3, 1.45, 1.5], fov: 40, cctv: 1, face: { temych: 'terror', mihalych: 'fear' }, set: (o, t) => { srvDim(o);
  faceLit(o, [1.7, 2.4, .2], 1.3, 0xc0ccd8); const lean = seg(t, .35, 1.2), sh = seg(t, 1.2, 1.75), ryE = Math.atan2(SRV_CAM[0] - SRV_OUT[0], SRV_CAM[2] - SRV_OUT[1]), ryM = Math.atan2(SRV_CAM[0] - HUSH_SM[0], SRV_CAM[2] - HUSH_SM[1]);
  const lips = C => { const h = C.bone.Head.getWorldPosition(_tv); return lin([h.x - .1, h.y - .5, h.z - .1], [h.x - .08, h.y - .07, h.z - .06], sh); };   // от груди — к губам, к объективу
  const hu = seg(t, .95, 1.2);   // ладонь легла на плечо — Итан сжимается: плечи вверх, голова в плечи, замер и не дышит
  const onSh = () => { const B = A.temych.bone, a = B.upperarm_l.getWorldPosition(new T.Vector3()), b = B.upperarm_r.getWorldPosition(new T.Vector3()), q = a.distanceToSquared(new T.Vector3(HUSH_SM[0], a.y, HUSH_SM[1])) < b.distanceToSquared(new T.Vector3(HUSH_SM[0], b.y, HUSH_SM[1])) ? a : b; return [q.x, q.y + .05, q.z]; };
  const E = ['temych', SRV_OUT[0], 0, SRV_OUT[1], ryE, Object.assign(STILL('Idle_Loop', 1.4), { look: SRV_CAM, wig: [['clavicle_l', [0, 0, 1], .14 * hu], ['clavicle_r', [0, 0, 1], -.14 * hu], ['neck_01', [1, 0, 0], .16 * hu], ['spine_03', [1, 0, 0], .06 * hu]] }), null];
  return [['mihalych', FRANK[0], FRANKY, FRANK[1], PI + .55, frankSit(t, { look: [SRV_OUT[0], 1.4, SRV_OUT[1]] }), null], E,
    ['monster', HUSH_SM[0], 0, HUSH_SM[1], ryM, Object.assign(STILL('Idle_Loop', 1.7), { pelvis: [0, -.2 * lean, 0], spine: { to: [SRV_OUT[0], 1.5, SRV_OUT[1]], k: .5 * lean },
      arm: { r: { at: onSh, by: 'palm', fdir: nrm([-.3, -.2, -1]), pdir: [0, -1, 0], w: lean }, l: { at: lips, by: 'tip', fdir: nrm(lin([.3, -.2, -.9], [0, 1, 0], sh)), pdir: nrm([.6, 0, -.8]) } },
      fing: { r: ['flat', .55], l: sh > .3 ? ['point', 1] : ['flat', .55] } }), 'cam']]; } };
// «Фрэнк ушёл на голос» (этап 1): из темноты голос Фрэнка — «Итан?..»; Итан отзывается «Фрэнк?!» и бросается следом — падает на ладони уже за чертой соли
// (было: Итан оставался в целом круге, а оно падало на него с потолка сквозь соль)
const K8_A = [1.78, 2.12], K8_B = [1.6, 1.94];
SH.k8_reach = { loc: 'server', pos: [2.55, 1.25, 3.3], look: [.9, .35, 1.5], fov: 50, face: { temych: 'fear' }, set: (o, t) => { srvDim(o); o.candleK = .2;
  const go = seg(t, 1.4, 2.0), ry = Math.atan2(.35 - K8_A[0], .75 - K8_A[1]), x = lerp(K8_A[0], K8_B[0], go), z = lerp(K8_A[1], K8_B[1], go), R = locR(x, z, ry), sh = Math.sin(t * 31) * .004 * go;
  return [['temych', x, 0, z, ry, { clips: [['Crouch_Idle_Loop', t + .5, 1]], legs: 'plant', pelvis: R.v(0, -.1 * go, 0), spine: { to: R.p(0, .2, 1.4), k: .7 * go },
    arm: { l: { at: R.p(.2, .03, .6), by: 'palm', fdir: R.v(0, 0, 1), pdir: [0, -1, 0], w: go }, r: { at: R.p(-.2 + sh, .03 + .32 * seg(t, 2.0, 2.5), .7 + .12 * seg(t, 2.0, 2.5)), by: 'palm', fdir: R.v(0, .1, 1), pdir: [0, -1, 0], w: go } },
    fing: { l: ['flat', go], r: ['open', go] }, look: [.35, .35, .75] }, null]]; } };
// «Назвался» под стойкой: та же камера холла, только Итана за столом нет (он под стойкой, её не видно) — у окна будки стоит второй
SH.dbl_cam_hide = Object.assign({}, SH.dbl_cam, { set: (o, t, sh) => SH.dbl_cam.set(o, t, sh).filter(a => a[0] !== 'temych') });
// A1 — Итан прячется под стойкой (CROUCH, как в сцене hide): «посмотрел в лицо» — крупно его лицо под столешницей в мертвенном свете помех, губы сами отвечают;
// «засмотрелся» — на CAM 00 оно стоит в будке над столом, рукой лезет под столешницу к нему и, глядя в объектив, подносит палец к губам
SH.k1_hide = { loc: 'lobby', pos: [5.7, .64, -.75], look: [5.72, .6, -.31], fov: 34, face: { temych: 'gasp' }, set: (o, t) => {
  lobbyBlack(o); o.amb.intensity = .07; o.flickOn = false; const surge = seg(t, 1.25, 1.42);
  o.faceL.position.set(5.66, .42, -.52); o.faceL.color.setHex(0xb8c8dc); o.faceL.distance = 1.3; o.faceL.intensity = (.7 + .35 * Math.sin(t * 47) * Math.sin(t * 13)) * (1 + 1.2 * surge);   // свет помех снизу (экран у него в руках)
  const P = eCrouch(t); P.look = [5.6, .72, -.72];   // смотрит чуть вверх, на свет экрана — лицо к нам, а не подбородком
  P.still = 1; P.post = C => { if (t > .7 && t < 1.3) C.talk = .28 + .2 * Math.abs(Math.sin(t * 19)); };   // губы сами отвечают (раньше строка стояла в комментарии и не работала)
  A.temych.faceSets.k_dread = { mouth: .2 + .25 * seg(t, .5, .8), wide: 1.3, browUp: 1, browIn: 1, tense: .45, frown: .3 }; A.temych.faceSet = 'k_dread';
  return [['temych', CROUCH[0], 0, CROUCH[1], PI, P, null]]; } };
SH.hush_hide = { loc: 'lobby', pos: [6.42, 2.32, .32], look: [5.65, .95, -.7], fov: 60, cctv: 1, set: (o, t) => {
  if (o.ch2) o.ch2.position.set(6.55, 0, -.62);   // второй стул на 30 см к стене: подол наклонившегося над столом Жильца задевал его
  o.lamp.intensity = 0; o.lampBulb.visible = false; o.boothLight.intensity = .2; o.l1.intensity = .3; o.amb.intensity = .14; o.flickOn = false;
  o.faceL.position.set(6.0, 1.9, -.2); o.faceL.color.setHex(0xc0ccd8); o.faceL.distance = 2.2; o.faceL.intensity = 1.0;   // лицо к объективу
  const lean = seg(t, .35, 1.2), sh = seg(t, 1.2, 1.75), lips = C => { const h = C.bone.Head.getWorldPosition(_tv); return lin([5.98, .83, -.47], [h.x + .07, h.y - .07, h.z + .08], sh); };   // пальцы упёрты в стол — потом к губам, к объективу (от пояса читалось как «рука на бедре»)
  return [['temych', CROUCH[0], 0, CROUCH[1], PI, eCrouch(t), null], ['monster', 5.8, 0, -.97, 0, Object.assign(STILL('Idle_Loop', 1.7), { pelvis: [0, -.18 * lean, 0], spine: { to: [5.8, .7, -.2], k: .5 * lean },
    arm: { r: { at: [5.62, .55, -.4], by: 'palm', fdir: [0, -.25, 1], pdir: [0, -1, 0], w: lean, pole: [5.3, .9, -1.1] }, l: { at: lips, by: 'tip', fdir: nrm(lin([0, -.6, 1], [0, 1, 0], sh)), pdir: nrm(lin([0, -1, -.3], [-.5, 0, .85], sh)), w: lean } },
    fing: { r: ['claw', .4], l: sh > .3 ? ['point', 1] : ['flat', .55] } }), 'cam']]; } };
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
const SHADOW_N = 1;   // 24 ноя (владелец: «всё равно лагает»): тень отбрасывает одна лампа (ближайшая яркая) — точечная тень = 6 проходов сцены
// лучи света под лампами: мягкий конус, ярче у лампы, тает к полу и в тумане; дышит пылью
const BEAM_VS = `varying vec3 vN, vV, vW; varying float vY; uniform float uH;
  void main(){ vY = position.y / uH + .5; vec4 w = modelMatrix * vec4(position, 1.); vW = w.xyz; vec4 mv = viewMatrix * w; vV = -mv.xyz; vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * mv; }`;
const BEAM_FS = `varying vec3 vN, vV, vW; varying float vY; uniform vec3 uC; uniform float uI, uT, uFog;
  float h(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
  float n3(vec3 p){ vec3 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
    return mix(mix(mix(h(i), h(i + vec3(1,0,0)), f.x), mix(h(i + vec3(0,1,0)), h(i + vec3(1,1,0)), f.x), f.y), mix(mix(h(i + vec3(0,0,1)), h(i + vec3(1,0,1)), f.x), mix(h(i + vec3(0,1,1)), h(i + vec3(1,1,1)), f.x), f.y), f.z); }
  void main(){ float f = clamp(abs(dot(normalize(vN), normalize(vV))), 0., 1.); float d = length(vV);
    // vY у самого пола интерполируется чуть ниже нуля: pow(-0.000001, 1.4) на видеокартах — NaN, обработка кадра делала из него чёрную точку,
    // и по основанию каждого конуса шёл чёрный пунктирный круг (на полу 7-го этажа — «петли», 29 окт). Программная графика pow считает по модулю
    float a = pow(f, 2.5) * pow(clamp(vY, 0., 1.), 1.4) * uI * exp(-d * uFog * 1.4) * smoothstep(.15, 1.2, d);
    a *= .65 + .7 * n3(vW * 2.2 + vec3(0., -uT * .15, uT * .07));
    #if __VERSION__ >= 300
    if (isnan(a) || isinf(a)) a = 0.;
    #endif
    gl_FragColor = vec4(uC * max(a, 0.) * .22, 1.); }`;
// пылинки: висят вокруг камеры (сетка повторяется), светятся только в лучах ламп
const DUST_VS = `uniform vec3 uCam; uniform float uT, uPx; uniform vec4 uL[6]; varying float vA;
  void main(){ vec3 p = position + vec3(sin(uT * .13 + position.y * 3.) * .3, -uT * .025, cos(uT * .11 + position.x * 2.) * .3);
    p = uCam + mod(p - uCam + 4., 8.) - 4.;
    float a = 0.; for (int i = 0; i < 6; i++) { vec3 d = p - uL[i].xyz; float r = .25 + max(0., -d.y) * .42; a += uL[i].w * smoothstep(r, r * .3, length(d.xz)) * step(d.y, .1) * smoothstep(-4., -.2, d.y); }
    vec4 mv = viewMatrix * vec4(p, 1.); float z = -mv.z; vA = min(1., a) * smoothstep(.3, 1., z) * smoothstep(7., 3., z) * (.45 + .55 * fract(sin(dot(position, vec3(12.9, 78.2, 37.7))) * 4375.));
    gl_PointSize = uPx * (.012 + .012 * fract(position.x * 91.)) / max(z, .1); gl_Position = projectionMatrix * mv; }`;
const DUST_FS = `varying float vA; void main(){ vec2 d = gl_PointCoord - .5; float a = smoothstep(.5, .0, length(d)) * vA; gl_FragColor = vec4(vec3(1., .93, .82) * a * .7, 1.); }`;
// одинаковый набор ламп во всех местах. Число ламп входит в ключ программы three.js: при разном числе каждый материал (люди, предметы)
// собирался заново для каждого места и ещё раз, когда посреди сцены появлялись контровой свет или фонарь (297 программ на игру).
// Теперь контровой свет и фонарь создаются сразу, недостающие лампы — пустые (сила 0, радиус 1 мм: шейдер их пропускает),
// лампы внутри групп (кабина лифта, автомат) — прямо в сцене: отражение в стекле прячет группы, и число ламп не прыгает
const TV_P = 4;
function lightRig(L) {
  const cnt = o => { const n = { p: 0, m: 0, s: 0, h: 0 }; o.S.traverse(l => { if (l.isPointLight) n.p++; else if (l.isSpotLight) n[l.map ? 'm' : 's']++; else if (l.isHemisphereLight) n.h++; }); return n; };
  for (const k in L) { const o = L[k], S = o.S, nest = [];
    S.traverse(l => { if (l.isLight && l.parent !== S) nest.push(l); }); nest.forEach(l => S.attach(l));
    if (!o.rimC) { const l = o.rimC = new T.SpotLight(0xbfd4ff, 0, 3.2, .5, .7, 1.6); l.castShadow = false; S.add(l, l.target); }   // контровой — прожектор из-за головы на голову: стена за актёром не светится «нимбом»
    if (!o.torch && /^(corridor|stairs|basement)$/.test(k)) { const l = o.torch = new T.SpotLight(0xfff0d8, 0, 0, .3, .35, 1); S.add(l, l.target); }
    for (const l of [o.torch, o.etorch]) if (l) l.map = TX.torch; }   // фонарь — с «маской»: горячее пятно, кольцо отражателя, мягкий край
  // ТВ-приставка: в каждом месте не больше TV_P точечных ламп (каждая лампа — расчёт в каждой точке кадра; 11 ламп на слабой видеокарте — 1 кадр/с).
  // Остаются именные (настольная лампа, будка, свеча, свет на лицо…) и самые яркие; остальные просто не светят (картинка чуть темнее по углам)
  if (window.nsTV) for (const k in L) { const o = L[k], keep = new Set([o.rim, o.faceL, o.slitLight, o.lamp, o.boothLight, o.candle].filter(Boolean)), pts = [];
    o.S.traverse(l => { if (l.isPointLight && !keep.has(l)) pts.push(l); });
    pts.sort((a, b) => b.intensity - a.intensity).slice(Math.max(0, TV_P - keep.size)).forEach(l => { l.parent.remove(l); l.visible = false; }); }
  const N = { p: 0, m: 0, s: 0, h: 0 }; for (const k in L) { const n = cnt(L[k]); for (const q in N) N[q] = Math.max(N[q], n[q]); }
  for (const k in L) { const o = L[k], S = o.S, n = cnt(o); o.pad = new Set();
    const spot = map => { const l = new T.SpotLight(0, 0, .001, .1, 0, 1); l.map = map; l.position.set(0, -60, 0); S.add(l, l.target); o.pad.add(l); };
    for (; n.p < N.p; n.p++) { const l = new T.PointLight(0, 0, .001, 1); l.position.set(0, -60, 0); S.add(l); o.pad.add(l); }
    for (; n.m < N.m; n.m++) spot(TX.torch);
    for (; n.s < N.s; n.s++) spot(null);
    for (; n.h < N.h; n.h++) { const l = new T.HemisphereLight(0, 0, 0); S.add(l); o.pad.add(l); } }
  return N;
}
let pmrem = null, envStub = null;
function cineLoc(R, o) {
  const S = o.S, skip = new Set([o.rim, o.faceL, o.slitLight, o.lamp, o.boothLight, o.candle, o.torch, o.rimC, ...(o.pad || [])]);
  S.traverse(m => {
    if (!m.isMesh) return;
    const mt = Array.isArray(m.material) ? m.material[0] : m.material, g = m.geometry;
    if (!g.boundingSphere) g.computeBoundingSphere();
    const solid = !mt.isMeshBasicMaterial && !mt.transparent && !mt.isShaderMaterial;
    m.receiveShadow = solid;
    const br = g.boundingSphere.radius * Math.max(m.scale.x, m.scale.y, m.scale.z);
    // мелочь (меньше ~18 см: винты, ручки, диоды, ножки, кнопки) тень не бросает: на карте теней 256–512 точек она давала кашу в пару пикселей, а каждый
    // такой предмет рисовался ещё по разу на каждую грань куба каждой лампы — после детализации 19 ноя отрисовок за кадр стало втрое больше (серверная 406 → 1273)
    m.castShadow = solid && !m.userData.noShadow && mt.side !== T.BackSide && br < 2.4 && br >= .09;
  });
  o.casters = []; o.beams = [];
  S.traverse(l => { if (!l.isPointLight) return;
    l.shadow.mapSize.set(ULTRA ? 1024 : FINE ? 512 : 256, ULTRA ? 1024 : FINE ? 512 : 256); l.shadow.bias = -.003; l.shadow.normalBias = .025; l.shadow.camera.near = .08; l.shadow.camera.far = l.distance || 20;   // «Ультра» — тени вдвое чётче
    if (!skip.has(l)) o.casters.push(l);
    if (!skip.has(l) && l.position.y > 2.3) {
      const wp = new T.Vector3(); l.getWorldPosition(wp); const top = l.userData.beamTop ?? l.position.y, h = (l.userData.beamTop ?? wp.y) - .02;
      const m = new T.Mesh(new T.CylinderGeometry(.2, .2 + h * (o.name === 'corridor' ? .26 : .42), h, 28, 1, true), new T.ShaderMaterial({ vertexShader: BEAM_VS, fragmentShader: BEAM_FS, transparent: true, depthWrite: false, blending: T.AdditiveBlending, side: T.DoubleSide,
        uniforms: { uH: { value: h }, uC: { value: l.color }, uI: { value: 0 }, uT: { value: 0 }, uFog: { value: .05 } } }));
      m.position.set(l.position.x, top - h / 2 - .02, l.position.z); m.renderOrder = 5; l.parent.add(m); o.beams.push({ l, m, base: Math.max(.5, l.intensity) });
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
  // съёмка окружения — теми же программами, что и игра: с пустой картой окружения того же вида (иначе каждый материал собирался ещё раз — без неё)
  envStub = envStub || pmrem.fromScene(new T.Scene()).texture; S.environment = envStub;
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
let PB = null, shadowN = 0;
const POST_VS = 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0., 1.); }';
// чистая выборка из HDR-буфера: NaN → 0, бесконечность и отрицательное — в 0…256 (иначе свечение раздувает битую точку в чёрный круг)
const FIN = `vec3 fin(vec3 c){
#if __VERSION__ >= 300
  if (isnan(c.r + c.g + c.b)) return vec3(0.);
#endif
  return clamp(c, 0., 256.); }`;
function cinePost() {
  const q = () => new T.WebGLRenderTarget(Math.max(8, V.RW >> 2), Math.max(8, V.RH >> 2), { type: T.HalfFloatType, magFilter: T.LinearFilter, minFilter: T.LinearFilter });
  PB = { a: q(), b: q() };
  if (ULTRA && window.__SSAO) {   // ВЫКЛЮЧЕНО (владелец: «эта версия не работает»): глубина из MSAA-кадра ломала кадр на настоящих видеокартах; вернуть через отдельный проход глубины. «Ультра»: мягкие контактные тени (SSAO) по буферу глубины — в углах, под предметами, у ног и лиц; в половину разрешения, потом размытие
    const hq = () => new T.WebGLRenderTarget(Math.max(8, V.RW >> 1), Math.max(8, V.RH >> 1), { type: T.HalfFloatType, magFilter: T.LinearFilter, minFilter: T.LinearFilter });
    PB.o1 = hq(); PB.o2 = hq();
    PB.ao = new T.ShaderMaterial({ uniforms: { tZ: { value: null }, uPI: { value: new T.Matrix4() }, uP: { value: new T.Matrix4() }, uPx: { value: new T.Vector2() } }, vertexShader: POST_VS,
      fragmentShader: `uniform sampler2D tZ; uniform mat4 uPI, uP; uniform vec2 uPx; varying vec2 vUv;
        vec3 vp(vec2 uv) { vec4 p = uPI * vec4(uv * 2. - 1., texture2D(tZ, uv).r * 2. - 1., 1.); return p.xyz / p.w; }
        float h(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
        void main() {
          if (texture2D(tZ, vUv).r >= .99999) { gl_FragColor = vec4(1.); return; }
          vec3 p = vp(vUv), pr = vp(vUv + vec2(uPx.x, 0.)), pl = vp(vUv - vec2(uPx.x, 0.)), pu = vp(vUv + vec2(0., uPx.y)), pd = vp(vUv - vec2(0., uPx.y));
          vec3 n = cross(abs(pr.z - p.z) < abs(p.z - pl.z) ? pr - p : p - pl, abs(pu.z - p.z) < abs(p.z - pd.z) ? pu - p : p - pd);   // нормаль — с той стороны, где перепад глубины меньше: на краю фигуры не «загибается» в фон (не было бы обводки)
          n = dot(n, n) > 1e-12 ? normalize(n) : vec3(0., 0., 1.);
          vec3 tg = normalize(abs(n.z) < .9 ? cross(n, vec3(0., 0., 1.)) : cross(n, vec3(0., 1., 0.))), bt = cross(n, tg);
          float R = .5, occ = 0., a0 = h(gl_FragCoord.xy) * 6.2832;
          for (int i = 0; i < 12; i++) {
            float fi = float(i), t = (fi + .5) / 12., ang = a0 + fi * 2.39996, sc = mix(.12, 1., t * t);
            vec3 q = p + (tg * cos(ang) * sqrt(1. - t) + bt * sin(ang) * sqrt(1. - t) + n * sqrt(t)) * R * sc;
            vec4 c = uP * vec4(q, 1.); vec2 su = c.xy / c.w * .5 + .5;
            float sz = vp(su).z;
            occ += step(q.z + .015, sz) * (1. - smoothstep(R * .6, R * 1.6, abs(p.z - sz)));   // заслоняет только близкое: без ореола вокруг фигур на фоне дальней стены
          }
          gl_FragColor = vec4(vec3(clamp(1. - occ / 12. * 1.4, 0., 1.)), 1.); }` });
  }
  PB.down = new T.ShaderMaterial({ uniforms: { tD: { value: null }, uPx: { value: new T.Vector2() } }, vertexShader: POST_VS,
    fragmentShader: `uniform sampler2D tD; uniform vec2 uPx; varying vec2 vUv; ${FIN} void main(){ vec3 c = vec3(0.);
      for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) c += fin(texture2D(tD, vUv + vec2(float(i), float(j)) * uPx * 1.5).rgb); gl_FragColor = vec4(c / 9., 1.); }` });
  PB.blur = new T.ShaderMaterial({ uniforms: { tD: { value: null }, uDir: { value: new T.Vector2() } }, vertexShader: POST_VS,
    fragmentShader: `uniform sampler2D tD; uniform vec2 uDir; varying vec2 vUv; void main(){
      vec3 c = texture2D(tD, vUv).rgb * .227; c += (texture2D(tD, vUv + uDir * 1.385).rgb + texture2D(tD, vUv - uDir * 1.385).rgb) * .316; c += (texture2D(tD, vUv + uDir * 3.23).rgb + texture2D(tD, vUv - uDir * 3.23).rgb) * .07;
      gl_FragColor = vec4(c, 1.); }` });
  PB.comp = new T.ShaderMaterial({ uniforms: { tD: { value: null }, tBl: { value: null }, tAO: { value: null }, uAO: { value: 0 }, uHQ: { value: ULTRA ? 1 : 0 }, uGr: { value: 1 }, uT: { value: 0 }, uCctv: { value: 0 }, uIr: { value: 0 }, uVhs: { value: .7 }, uScare: { value: 0 }, uAsp: { value: 1 }, uExp: { value: .95 }, uSharp: { value: 0 }, uTx: { value: new T.Vector2() } }, vertexShader: POST_VS,
    fragmentShader: `uniform sampler2D tD, tBl, tAO; uniform float uAO, uHQ, uGr, uT, uCctv, uIr, uVhs, uScare, uAsp, uExp, uSharp; uniform vec2 uTx; varying vec2 vUv; ${FIN}
      float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
      vec3 aces(vec3 x){ return clamp((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0., 1.); }
      void main(){
        vec2 uv = vUv, d = uv - .5; float r = length(d * vec2(uAsp, 1.)) / max(uAsp, 1.) * 1.6;
        uv += d * dot(d, d) * (.02 + .12 * uCctv);                                             // объектив (у камер наблюдения — «рыбий глаз»)
        float row = floor(uv.y * 540.), fr = floor(uT * 24.);
        uv.x += (h(vec2(row, fr)) - .5) * uVhs * uCctv * .002;                                  // запись с камеры чуть дрожит строками
        uv += d * uScare * .05 * (h(vec2(fr, 3.)) - .5);                                        // скример: картинку рвёт к центру
        float ca = (mix(.0012, .0006, uHQ) + .012 * uScare) * (.25 + r * r * 1.4);   // «Ультра»: цветная кайма объектива слабее — картинка чище
        vec3 c = fin(vec3(texture2D(tD, uv + d * ca).r, texture2D(tD, uv).g, texture2D(tD, uv - d * ca).b));
        if (uSharp > .01) {   // кадр рисуется меньше экрана (perfTick, Steam Deck): резкость с ограничением по соседям, как FidelityFX CAS, — без ореолов
          vec3 n1 = fin(texture2D(tD, uv + vec2(uTx.x, 0.)).rgb), n2 = fin(texture2D(tD, uv - vec2(uTx.x, 0.)).rgb), n3 = fin(texture2D(tD, uv + vec2(0., uTx.y)).rgb), n4 = fin(texture2D(tD, uv - vec2(0., uTx.y)).rgb);
          c = clamp(c + (c - (n1 + n2 + n3 + n4) * .25) * uSharp, min(c, min(min(n1, n2), min(n3, n4))), max(c, max(max(n1, n2), max(n3, n4)))); }
        if (uAO < -.5) { gl_FragColor = vec4(vec3(texture2D(tAO, uv).r), 1.); return; }   // window.__AOVIS = 1 — показать карту контактных теней
        if (uAO > 0.) c *= mix(1., texture2D(tAO, uv).r, uAO * (1. - smoothstep(1., 3., max(c.r, max(c.g, c.b)))));   // «Ультра»: контактные тени (лампы не темнеют)
        if (uCctv > .5) { vec2 px = uAsp > 1. ? vec2(1.6 / 960., 1.6 / 540.) : vec2(1.6 / 540., 1.6 / 960.);   // камера наблюдения — дешёвая оптика и сжатие: картинка мягче «кино»
          c = mix(c, fin(texture2D(tD, uv + px).rgb + texture2D(tD, uv - px).rgb + texture2D(tD, uv + vec2(px.x, -px.y)).rgb + texture2D(tD, uv - vec2(px.x, -px.y)).rgb) * .25, mix(.6, .15, uHQ)); }   // «Ультра» — камера как современная HD: мягче лишь чуть
        if (uScare > .02) {   // скример: рывок к центру смазывает картинку, свет режет красным
          vec3 z = c; for (int i = 1; i < 7; i++) z += fin(texture2D(tD, uv - d * float(i) * .018 * uScare).rgb); c = mix(c, z / 7., min(1., uScare * 1.3));
          c *= vec3(1. + .5 * uScare, 1. - .25 * uScare, 1. - .3 * uScare);
        }
        vec3 bl = fin(texture2D(tBl, uv).rgb);
        c = mix(c, bl, smoothstep(.62, 1.25, r) * mix(.55, .22, uHQ) * (1. - uCctv));                          // глубина резкости: края кадра мягче
        vec3 glw = max(bl - .6, 0.);   // светятся только лампы (они в HDR ярче белого — glow() в world.js), а не белая рубашка рядом
        c += glw * 1.15 + glw * vec3(.4, .14, .05) * (1. - uCctv) + bl * vec3(.04, .022, .014);  // свечение ламп; тёплая кайма вокруг ярких мест (халация плёнки)
        c = aces(c * uExp);
        c = mix(c * 12.92, 1.055 * pow(max(c, vec3(.0031308)), vec3(1. / 2.4)) - .055, step(.0031308, c));
        c = pow(max(c, vec3(0.)), vec3(1.15)) * .988 + .012;                                                   // ночь: тени глубже, но чёрное — не «дыра» (чёрная точка ~.012)
        float l = dot(c, vec3(.299, .587, .114));
        float sk = smoothstep(.012, .07, c.r - c.g) * smoothstep(-.01, .04, c.g - c.b) * smoothstep(.03, .14, l);   // тёплые тона (кожа, дерево, лампы): холодный сдвиг теней их не трогает — лицо не зеленеет
        c = mix(c * mix(vec3(.86, 1.0, 1.07), vec3(.97, .99, 1.), sk) + vec3(0., .008, .018) * (1. - .7 * sk), c * vec3(1.06, .99, .9), smoothstep(.18, .7, l));   // холодные тени, тёплый свет
        c = mix(vec3(l), c, .88 + .07 * sk);
        c = mix(c, vec3(l) * vec3(.86, 1., .9), uCctv * mix(.55, .38, uHQ)) + max(c - .6, 0.) * uCctv * .8;
        c = mix(c, vec3(pow(max(l, 0.), .78) * 1.2) * vec3(.9, 1., .94), uIr);                          // ИК-режим камеры: ч/б, светлее у объектива
        c *= 1. - uCctv * mix(.09, .035, uHQ) * step(1., mod(floor(gl_FragCoord.y / 2.), 2.));
        c *= 1. - smoothstep(.55, 1.5, r) * .6;                                                   // виньетка
        c += (h(gl_FragCoord.xy + fract(uT * 7.13) * 731.) - .5) * (.018 * (.45 + 2.2 * l * (1. - l)) + uCctv * mix(.05, .022, uHQ) + uVhs * (.004 + .011 * uCctv)) * uGr;   // 24 ноя: зерно «кино» вдвое тише (владелец: «моментами глаза режет») — камеры наблюдения как были   // зерно: сильнее в полутонах, слабее в чёрном и белом
        if (uv.x < 0. || uv.x > 1. || uv.y < 0. || uv.y > 1.) c = vec3(0.);
        gl_FragColor = vec4(c, 1.); }` });
}
function cineRender(R, T0, cctv, scare, vhs) {
  const quad = postScene.children[0], RT = window.__W3core.RT;
  const pass = (m, to) => { quad.material = m; R.setRenderTarget(to); R.render(postScene, postCam); };
  PB.down.uniforms.tD.value = RT.texture; PB.down.uniforms.uPx.value.set(1 / V.RW, 1 / V.RH); pass(PB.down, PB.a);
  const bx = 1 / PB.a.width, by = 1 / PB.a.height, B = PB.blur.uniforms;
  if (PB.ao && RT.depthTexture) { const A = PB.ao.uniforms; A.tZ.value = RT.depthTexture; A.uPI.value.copy(cam.projectionMatrixInverse); A.uP.value.copy(cam.projectionMatrix); A.uPx.value.set(2 / V.RW, 2 / V.RH); pass(PB.ao, PB.o1);
    B.tD.value = PB.o1.texture; B.uDir.value.set(1 / PB.o1.width, 0); pass(PB.blur, PB.o2); B.tD.value = PB.o2.texture; B.uDir.value.set(0, 1 / PB.o1.height); pass(PB.blur, PB.o1); }
  for (const s of [1, 2.2]) { B.tD.value = PB.a.texture; B.uDir.value.set(bx * s, 0); pass(PB.blur, PB.b); B.tD.value = PB.b.texture; B.uDir.value.set(0, by * s); pass(PB.blur, PB.a); }
  const U = PB.comp.uniforms; U.uExp.value = curLoc && curLoc.name === 'corridor' ? .68 : curLoc && curLoc.name === 'server' ? 1.05 : .92; U.tD.value = RT.texture; U.tBl.value = PB.a.texture; U.tAO.value = PB.o1 ? PB.o1.texture : null; U.uAO.value = PB.ao && RT.depthTexture && !window.__NOAO ? (window.__AOVIS ? -1 : .85) : 0;   // window.__NOAO = 1 — сравнить без контактных теней U.uT.value = T0; U.uCctv.value = cctv; U.uScare.value = scare; U.uVhs.value = vhs; U.uAsp.value = V.RW / V.RH; U.uTx.value.set(1 / V.RW, 1 / V.RH); U.uSharp.value = FINE && (V.rs || 1) < .99 ? Math.min(.3, (1 - V.rs) * 1.2) : 0; U.uGr.value = clamp(((V.rs || 1) - .45) / .55, .25, 1);   // 24 ноя: кадр меньше экрана — зерно тише (растянутое зерно мельтешит крупными точками и «режет глаза»)   // резкость — только «Ультра», если авто-качество сбавило разрешение («Кино» как было)
  pass(PB.comp, null); quad.material = postMat;
}
// «Кино» следит за скоростью: если кадр рисуется дольше ~38 мс, внутреннее разрешение падает ступенями (до 55%), если быстро — растёт обратно
let perfT = 0, perfN = 0, perfS = 0, perfCool = 0, perfGood = 0;
// счётчик кадров для проверки на своём ПК: F3 (или ?fps=1) — кадры в секунду, мс на кадр, доля разрешения и размер кадра «Кино», видеокарта
let fpsOn = /[?&]fps=1\b/.test(location.search), fpsEl = null;
function fpsShow(avg) { if (!fpsOn) { if (fpsEl) fpsEl.style.display = 'none'; return; }
  if (!fpsEl) { fpsEl = document.createElement('div'); fpsEl.style.cssText = 'position:fixed;left:6px;top:6px;z-index:99999;font:600 12px/1.35 monospace;color:#9f9;background:rgba(0,0,0,.65);padding:4px 7px;border-radius:4px;pointer-events:none;white-space:pre'; document.body.appendChild(fpsEl); }
  fpsEl.style.display = 'block'; fpsEl.textContent = `${Math.round(1000 / avg)} FPS · ${avg.toFixed(1)} мс\n${CINE ? 'Кино' : 'Ретро'} ${Math.round((V.rs || 1) * 100)}% · ${V.RW}×${V.RH}\n${(window.__W3core.GPU || {}).name || ''}`; }
document.addEventListener('keydown', e => { if (e.code === 'F3') { e.preventDefault(); fpsOn = !fpsOn; if (!fpsOn && fpsEl) fpsEl.style.display = 'none'; } });
function perfTick(d) {   // d — мс между кадрами движка
  if (!CINE || !scenesReady || d > 250 || V.rsPin) return;   // пауза/вкладка в фоне — не считаем; ?rs= — разрешение задано вручную
  perfS += d; if (++perfN < 45) return; const avg = perfS / perfN; perfN = perfS = 0; fpsShow(avg); if (perfCool-- > 0) return;
  // 24 ноя: держим ~50–60 кадров: ниже ~42 кадров (24 мс) разрешение сразу на 10 % меньше (на совсем медленном — на 15 %); назад — только после 3 быстрых окон подряд (без «качелей»)
  const rs = V.rs || 1; perfGood = avg < 18 ? perfGood + 1 : 0;
  const nr = avg > 38 ? Math.max(.55, rs - .15) : avg > 24 ? Math.max(.55, rs - .1) : perfGood >= 3 && rs < 1 ? Math.min(1, rs + .1) : rs; if (nr > rs) perfGood = 0;
  if (nr !== rs) { V.rs = nr; perfCool = 2; setLand(V.land, true); perfSlow = 0; return; }
  // совсем слабое устройство: уже на 55 % кадр дольше 48 мс три окна подряд → «Ретро» (с продолжением с того же места); если игрок сам выбрал «Кино» — не спорим
  if (rs <= .551 && avg > 48) { if (++perfSlow >= 3 && localStorage.getItem('ns1_gfxAuto') !== '2' && window.nsGfxFallback) { perfSlow = -1e9; window.nsGfxFallback(4); } } else perfSlow = 0;
}
let perfSlow = 0;
// ориентация на лету: буферы под новый размер
function setLand(land, force) {
  if (land === V.land && scenesReady && !force) return;
  window.__W3core.setLand(land);
  if (!scenesReady) return;
  const R = window.__W3core.R; R.setSize(V.RW, V.RH, false);
  window.__W3core.RT.setSize(V.RW, V.RH); const rd = FINE ? 3 : 4; reflRT.setSize(Math.round(V.RW / rd), Math.round(V.RH / rd));   // отражения: «Ультра» — в половину, ПК — в треть (каждый кадр), телефон — в четверть
  if (PB) { PB.a.setSize(Math.max(8, V.RW >> 2), Math.max(8, V.RH >> 2)); PB.b.setSize(Math.max(8, V.RW >> 2), Math.max(8, V.RH >> 2)); }
  if (PB && PB.o1) { PB.o1.setSize(Math.max(8, V.RW >> 1), Math.max(8, V.RH >> 1)); PB.o2.setSize(Math.max(8, V.RW >> 1), Math.max(8, V.RH >> 1)); }
  postMat.uniforms.uB.value.set(V.BW, V.BH);
}
const PROPS = { jacket: 1, badge: 1, clock: null };   // «нормы» холла; сюжет может передать o.props = { jacket: 0, badge: 'scratched', clock: '03:00' }
let VHS = .7;
function init() {
  if (scenesReady) return true;
  try {
    // видеокарта может отказать именно «мощному» режиму (ноутбук на встроенной AMD, 12 ноя) — пробуем три варианта; причину отказа браузер сообщает событием, её видно на экране и в отчёте
    let R0 = null;
    for (const pp of ['high-performance', 'default', 'low-power']) {
      canvas = document.createElement('canvas'); canvas.width = V.RW; canvas.height = V.RH;
      canvas.addEventListener('webglcontextcreationerror', e => { window.__glErr = String(e.statusMessage || '').slice(0, 160); });
      try { R0 = new T.WebGLRenderer({ canvas, antialias: false, alpha: false, powerPreference: pp, preserveDrawingBuffer: false }); break; }
      catch (e) { window.__glErr = window.__glErr || String(e && e.message || e).slice(0, 160); }
    }
    if (!R0) return false; window.__glErr = ''; window.__W3core.R = R0;
  } catch (e) { return false; }
  const R = window.__W3core.R; R.setPixelRatio(1); R.setSize(V.RW, V.RH, false); R.useLegacyLights = true; R.outputColorSpace = T.SRGBColorSpace;
  R.debug.onShaderError = (gl, prog, vs, fs) => {   // материал не собрался (видеокарте не хватило ресурсов?) — какой и почему: в консоль и в отчёт проверки
    const nm = (gl.getShaderSource(fs).match(/#define SHADER_NAME (.*)/) || [])[1] || '?', lg = sh => (gl.getShaderInfoLog(sh) || '').trim().slice(0, 240);
    (window.__shaderErr = window.__shaderErr || []).push(nm);
    console.error(`shader ${nm}: ${(gl.getProgramInfoLog(prog) || '').trim().slice(0, 240)} | vs: ${lg(vs)} | fs: ${lg(fs)} | units ${gl.getParameter(gl.MAX_TEXTURE_IMAGE_UNITS)}`);
  };
  if (CINE) { R.shadowMap.enabled = true; R.shadowMap.type = FINE ? T.PCFSoftShadowMap : T.PCFShadowMap; }   // на телефоне тени попроще
  const ST = window.__ldStep || (() => {});   // шаг сборки — в строку загрузки и в память (ТВ-приставка зависла — при следующем запуске видно где)
  ST('текстуры'); makeTextures(); ST('герои'); A = window.__W3chars.cast();
  if (CINE) { R.shadowMap.autoUpdate = false; for (const k in A) A[k].root.traverse(m => { if (m.isMesh) { m.castShadow = true; m.receiveShadow = true; } }); }
  ST('холл'); L.lobby = buildLobby(); ST('коридор'); L.corridor = buildCorridor(); ST('серверная'); L.server = buildServer(); ST('лестница'); L.stairs = buildStairs(); ST('подвал'); L.basement = buildBasement(); ST('свет');
  for (const k in L) L[k].name = k;
  window.__lightN = lightRig(L);
  if (CINE) for (const k in L) cineLoc(R, L[k]);
  // «Кино» потеряло контекст на виду (процесс GPU упал или его убил сторож на сборке шейдеров) — карты окружения и буферы уже пустые,
  // и на этой машине упадёт снова: «Ретро» с продолжением с того же места. Свёрнутая вкладка на телефоне (hidden) — не повод
  if (CINE) canvas.addEventListener('webglcontextlost', () => { window.__ctxLost = (window.__ctxLost || 0) + 1;   // ?gfx= в адресе (проверки) — без перезагрузки
    if (document.visibilityState === 'visible' && localStorage.getItem('ns1_gfxAuto') !== '2' && !/[?&]gfx=/.test(location.search) && window.nsGfxFallback) window.nsGfxFallback(5); });
  // буфер кадра и постобработка: цветокор + дизеринг + ступени цвета + плёнка VHS (дрожь строк, полоса внизу, редкий залом,
  // цветная кайма в 1 пиксель, строки, лёгкая бочка); у камер наблюдения бочка сильнее и пересвет
  window.__W3core.RT = new T.WebGLRenderTarget(V.RW, V.RH, CINE ? { type: T.HalfFloatType, samples: FINE ? 2 : 4 } : { magFilter: T.NearestFilter, minFilter: T.NearestFilter });
  if (CINE) cinePost();
  ST('плёнка');
  postMat = new T.ShaderMaterial({
    uniforms: { tD: { value: window.__W3core.RT.texture }, uB: { value: new T.Vector2(V.BW, V.BH) }, uHD: { value: HD }, uLv: { value: 22 }, uCctv: { value: 0 }, uIr: { value: 0 }, uGam: { value: window.nsTV ? 1.08 : 1.25 }, uT: { value: 0 }, uVhs: { value: .7 }, uScare: { value: 0 } },
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
        c = pow(max(c, vec3(0.)), vec3(uGam));   // ночь: тени глубже, но без пятен
        float l = dot(c, vec3(.299, .587, .114));
        c = mix(c * vec3(.86, 1.02, 1.08) + vec3(0., .012, .024), c * vec3(1.06, .98, .88), smoothstep(.25, .8, l));   // холодные тени, тёплый свет
        c = mix(c, vec3(l) * vec3(.88, 1., .9), uCctv * .3) + max(c - .62, 0.) * uCctv * .9;                         // камера: блеклая, свет «горит»
        c = mix(c, vec3(pow(max(l, 0.), .78) * 1.2) * vec3(.9, 1., .94), uIr);                                                 // ИК-режим камеры
        c *= 1. - (.06 + .07 * uCctv) * step(1., mod(floor(gl_FragCoord.y / uHD), 2.));           // строки
        c += cr * .22 * h(vec2(gl_FragCoord.x, fr)) + band * .05 * h(gl_FragCoord.xy + fr);
        if (uv.x < 0. || uv.x > 1. || uv.y < 0. || uv.y > 1.) c = vec3(0.);
        c = floor(c * uLv + b4(gl_FragCoord.xy) * .95) / uLv;
        gl_FragColor = vec4(c, 1.);
      }` });
  { const rd = FINE ? 3 : 4; reflRT = new T.WebGLRenderTarget(Math.round(V.RW / rd), Math.round(V.RH / rd), { magFilter: T.LinearFilter, minFilter: T.LinearFilter }); }
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
// вертикальный экран: кадры поставлены под 16:9, при том же вертикальном угле в 9:16 по бокам остаётся треть кадра — двое в кадре не помещаются.
// Поэтому в вертикали угол шире (по горизонтали ~47° вместо ~26° при fov 44): больше пола и потолка, но герои и предметы в кадре
const PORT_K = 1.9;
function aim(c, pos, look, fov, cx, cy, z, sx, sy, dk = 0) {
  if (!V.land) fov = 2 * Math.atan(Math.tan(fov * PI / 360) * PORT_K) * 180 / PI;
  c.position.set(pos[0], pos[1], pos[2]); c.fov = fov; c.aspect = V.RW / V.RH; c.updateProjectionMatrix(); c.lookAt(look[0], look[1], look[2]); c.updateMatrixWorld();
  // точка (cx, cy) кадра → направление; зум сужает угол обзора вокруг неё (как настоящий объектив)
  _v.set((cx - .5) * 2, -(cy - .5) * 2, .5).unproject(c); _d.copy(_v).sub(c.position).normalize();
  c.lookAt(c.position.x + _d.x, c.position.y + _d.y, c.position.z + _d.z);
  if (dk > 0 && z > 1.001) {   // наезд — наполовину настоящий проезд камеры (фон смещается относительно героя, есть глубина), наполовину объективом; герой на плане того же размера
    const D = Math.max(.6, _v.set(look[0] - pos[0], look[1] - pos[1], look[2] - pos[2]).dot(_d)), m = Math.min(.3, dk * D * (1 - 1 / Math.min(z, 1.35)));   // не дальше 30 см: в тесной будке не въехать в предмет
    c.position.addScaledVector(_d, m); z *= (D - m) / D; }
  c.fov = 2 * Math.atan(Math.tan(fov * PI / 360) / Math.max(.3, z)) * 180 / PI; c.updateProjectionMatrix();
  c.rotateY(-sx * .00012); c.rotateX(-sy * .00012);
}
let lastT = 0, curLoc = null, lastId = null;
// склейка без смены ракурса: тот же план ближе или вопрос → ответ с той же точки. Здесь поза и взгляд продолжаются, а не начинаются заново
// (иначе видно, как человек «перескакивает» в другую фазу стойки — классическая ошибка монтажа)
function sameView(a, b) { if (!a || !b || a.loc !== b.loc) return false;
  const da = [a.look[0] - a.pos[0], a.look[1] - a.pos[1], a.look[2] - a.pos[2]], db = [b.look[0] - b.pos[0], b.look[1] - b.pos[1], b.look[2] - b.pos[2]];
  const c = (da[0] * db[0] + da[1] * db[1] + da[2] * db[2]) / (Math.hypot(...da) * Math.hypot(...db) || 1);
  return Math.hypot(a.pos[0] - b.pos[0], a.pos[1] - b.pos[1], a.pos[2] - b.pos[2]) < .6 && c > Math.cos(.25); }
const hardCut = C => C.o.monster || C.o.jerk || C.o.dead || C.o.grey > .3;   // Жилец и «подделки» на склейке всегда ставятся заново (покадровая рывковость)
function stage(id, t, opt = {}, cont = false) {
  const sh = SH[ALIAS[id] || id] || goreShot(id) || SH.hall_cam, o = L[sh.loc];
  if (window.__W3chars && window.__W3chars.propsFrame) window.__W3chars.propsFrame();   // предметы в руках: видны только те, что держат в этом кадре
  curProps = Object.assign({}, PROPS, window.World && World.props, opt.props);
  if (curLoc && curLoc !== o) curLoc.reset();
  curLoc = o; o.reset(); if (o.xtra) for (const m of o.xtra) m.visible = false; if (o.xreset) o.xreset();   /* этап 1: предметы, которые ставит сам кадр (тряпка на камере серверной, соль у ведра, прорехи в круге) */ if (o.gfx) o.gfx.pool.visible = o.gfx.trail.visible = o.gfx.spray.visible = o.gfx.heart.visible = false; if (o.gutT && !sh.gore) o.gutT.forEach(m => { m.visible = false; }); o.ir = 0; if (o.irL) o.irL.intensity = 0; if (o.props) o.props(curProps);
  for (const k in A) if (A[k].HU && A[k].HU.uRip) { A[k].HU.uRip.value = 0; if (A[k].HU.uSocket) A[k].HU.uSocket.value = A[k].o.socket || 0; }   // раны ставит кадр (wound); глазницы — как у персонажа
  for (const k in A) { A[k].faceSet = 'base'; A[k].gore = 0; A[k].scream = 0; A[k].reflOnly = false; A[k].root.visible = true; A[k].aim = null; A[k].noTilt = false; A[k].shake = 0; A[k].struggle = 0; A[k].twitch = 0; A[k].no = 0; A[k].actT = undefined; A[k].tiltNow = undefined; if (A[k].HU) A[k].HU.uSocket.value = A[k].o.socket || 0; A[k].eatHold = 0; if (A[k].eyeM && A[k].eyeM.emissive) A[k].eyeM.emissive.setRGB(0, 0, 0); }
  if (o.faceL) o.faceL.color.setHex(0xdde6ff);
  if (sh.face) for (const k in sh.face) A[k].faceSet = sh.face[k];   // выражение лица на весь кадр (set() ниже может уточнить)
  const acts = sh.set(o, t, sh) || [];
  if (sh.fog != null) o.S.fog.density = sh.fog;
  const used = new Set();
  for (const [k, x, y, z, ry, pose, look, only] of acts) {
    const C = A[k]; used.add(k);
    if (C.root.parent !== o.S) o.S.add(C.root);
    if (C.lastShot !== id) { C.lastShot = id; if (!cont || hardCut(C)) C.lookDir = null; C.prevP = null; C.walkV = null; C.solD = null; C.solSm = false; }
    C.root.position.set(x, y, z); C.root.rotation.set(0, ry, 0); C.pose = pose;
    if (C.solD) { C.root.position.x += C.solD[0]; C.root.position.z += C.solD[1]; }   // твёрдый мир: в этом кадре его уже отодвигали от стены — начинаем оттуда (solidPass)   // наклон корпуса (вверх ногами и т. п.) кадр задаёт сам — в P.post, каждый кадр заново
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
// отражение в стекле фасада: та же камера, отражённая в плоскости z = 6. На ПК — каждый кадр в буфер в треть кадра (раньше — в половину,
// но раз в два кадра: при 30 кадрах в секунду отражение в стекле холла дёргалось за камерой — «окружение лагает» в прологе со сменщиком);
// на телефоне — четверть кадра раз в два кадра
const _f = new T.Vector3(), _u = new T.Vector3(), BIAS = new T.Matrix4().set(.5, 0, 0, .5, 0, .5, 0, .5, 0, 0, .5, .5, 0, 0, 0, 1);
function renderRefl(R, o, used, force) {
  if (!force && !FINE && (reflN++ % (window.nsTV ? 8 : 2))) return;   // ТВ-приставка: отражение в стекле холла — раз в 8 кадров (это второй полный кадр сцены)
  const inside = cam.position.z < 6, e = cam.matrixWorld.elements;
  _f.set(-e[8], -e[9], -e[10]); _u.set(e[4], e[5], e[6]);
  rcam.position.set(cam.position.x, cam.position.y, 12 - cam.position.z); rcam.up.set(_u.x, _u.y, -_u.z);
  rcam.lookAt(rcam.position.x + _f.x, rcam.position.y + _f.y, rcam.position.z - _f.z);
  rcam.projectionMatrix.copy(cam.projectionMatrix); rcam.projectionMatrixInverse.copy(cam.projectionMatrixInverse); rcam.updateMatrixWorld();
  const hid = [];
  for (const m of inside ? o.outObjs : o.inObjs) if (m.visible) { m.visible = false; hid.push(m); }
  if (o.blobs) for (const m of o.blobs) if (m.visible) { m.visible = false; hid.push(m); }   // контактные тени в отражении не нужны (а у спрятанных людей остались бы пятна без людей)
  for (const k in A) { const C = A[k]; if (C.root.parent !== o.S) continue; const z = C.root.position.z;
    if (C.reflOnly) C.root.visible = true; else if ((inside ? z > 5.9 : z < 6.1) && C.root.visible) { C.root.visible = false; hid.push(C.root); } }
  R.setRenderTarget(reflRT); R.render(o.S, rcam);
  for (const m of hid) m.visible = true;
  for (const k in A) if (A[k].reflOnly) A[k].root.visible = false;
  o.glassU.uTM.value.copy(BIAS).multiply(rcam.projectionMatrix).multiply(rcam.matrixWorldInverse);
}
function renderFeeds(R, T0, sh, t, one = null) {
  // мониторы в будке: 0 — холл, 1 — вход, 2 — седьмой этаж, 3 — помехи (камера 13)
  const lob = L.lobby, cor = L.corridor;
  const views = [[lob.S, [7.45, 3.85, 5.45], [-2.2, .9, -5]], [lob.S, [-.9, 3.25, 8.1], [.15, .8, 6.2]], [cor.S, [1.0, 2.75, -.5], [-.1, .9, -25]]];
  lob.screens.forEach(s => (s.visible = false));   // экраны не должны снимать сами себя
  const gr = lob.glassU.uRef.value; lob.glassU.uRef.value = 0;   // отражение посчитано для главной камеры — на мониторах его нет
  views.forEach(([S, p, l], i) => { if (one != null && one !== i) return; feedCam.position.set(...p); feedCam.lookAt(...l); R.setRenderTarget(feedRT[i]); R.render(S, feedCam); });
  lob.screens.forEach(s => (s.visible = true)); lob.glassU.uRef.value = gr;
  // помехи
  const nt = TX.noise; const c = nt.cx; const d = c.createImageData(64, 48);
  for (let i = 0; i < d.data.length; i += 4) { const v = Math.random() * 105; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; }
  c.putImageData(d, 0, 0); nt.needsUpdate = true; lob.screens[3].material.map = nt;
  // кадр гасит экраны: sh.feedMap(i, t) → 'noise' | 'black' | null (как обычно)
  lob.screens.forEach((s, i) => { const m = sh && sh.feedMap ? sh.feedMap(i, t) : null; s.material.map = m === 'noise' || (i === 3 && m !== 'black') ? nt : feedRT[i].texture; s.material.color.setScalar(m === 'black' ? 0 : 1); });
  R.setRenderTarget(null);
}
// контактные тени: под каждой ступнёй — тёмное мягкое пятно (тем светлее, чем выше нога над полом), под телом — пятно пошире,
// у лежащего — вытянутое вдоль тела. Свет ламп даёт тени далеко не везде (точечные лампы без карт теней), а без пятна у ног фигура «висит».
// Пол — уровень корня персонажа (лестница, подъём); кто под потолком или только в отражении — без пятен
let blobTex = null; const _bf = new T.Vector3(), _bb = new T.Vector3(), _bh = new T.Vector3();
function contactShadows(loc, used) {
  if (!blobTex) { const c = document.createElement('canvas'); c.width = c.height = 64; const x = c.getContext('2d'), gr = x.createRadialGradient(32, 32, 0, 32, 32, 32);
    for (let i = 0; i <= 8; i++) { const r = i / 8; gr.addColorStop(r, `rgba(255,255,255,${(Math.exp(-r * r * 3.2) - Math.exp(-3.2)) / (1 - Math.exp(-3.2))})`); }
    x.fillStyle = gr; x.fillRect(0, 0, 64, 64); blobTex = new T.CanvasTexture(c); }
  const pool = loc.blobs || (loc.blobs = []); let n = 0; if (window.__NOCS) { pool.forEach(m => { m.visible = false; }); return; }   // window.__NOCS = 1 — сравнить без контактных теней
  const put = (x, y, z, sx, sz, ang, a) => { if (a < .02) return; let m = pool[n];
    if (!m) { m = new T.Mesh(new T.PlaneGeometry(1, 1), new T.MeshBasicMaterial({ color: 0x000000, alphaMap: blobTex, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -6 }));
      m.rotation.order = 'YXZ'; m.renderOrder = 1; m.userData.solid = false; pool.push(m); }
    if (m.parent !== loc.S) loc.S.add(m);
    m.position.set(x, y + .004, z); m.rotation.set(-PI / 2, ang, 0); m.scale.set(sx, sz, 1); m.material.opacity = a; m.visible = true; n++; };
  for (const k of used) { const C = A[k]; if (!C.bone || !C.bone.pelvis || !C.root.visible || C.reflOnly || C.o.ghostOnly) continue;
    const fy = C.root.position.y; if (fy > 1.4) continue; const ss = C.ss || 1;
    C.bone.pelvis.getWorldPosition(_bb); if (C.bone.Head) C.bone.Head.getWorldPosition(_bh); else _bh.copy(_bb).y += .6;
    const lay = _bh.y - fy < .7 && _bb.y - fy < .5;
    if (lay) { const dx = _bh.x - _bb.x, dz = _bh.z - _bb.z, L = Math.hypot(dx, dz); put((_bb.x + _bh.x) / 2, fy, (_bb.z + _bh.z) / 2, .55 * ss, L + .7 * ss, Math.atan2(dx, dz), .42); }
    else put(_bb.x, fy, _bb.z, .66 * ss, .66 * ss, 0, .3 * (1 - clamp((_bb.y - fy - 1.1 * ss) / .6, 0, 1)));
    for (const sd of ['l', 'r']) { const f = C.bone['foot_' + sd], b = C.bone['ball_' + sd]; if (!f) continue; f.getWorldPosition(_bf); if (b) b.getWorldPosition(_bb); else _bb.copy(_bf);
      const h = Math.min(_bf.y, _bb.y) - fy, dx = _bb.x - _bf.x, dz = _bb.z - _bf.z;
      put((_bf.x + _bb.x) / 2, fy, (_bf.z + _bb.z) / 2, .16 * ss, .32 * ss, Math.atan2(dx, dz), .62 * (1 - clamp((h - .03) / .17, 0, 1))); } }
  for (let i = n; i < pool.length; i++) pool[i].visible = false;
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
    if (localStorage.getItem('ns1_gfxAuto') === '2' && !ULTRA) { gfxChk.done = true; return; }   // игрок сам вернул «Кино» — не спорим; «Ультра» проверяем всегда: битый кадр на этой видеокарте → «Кино»
    const c = gfxChk.c || (gfxChk.c = Object.assign(document.createElement('canvas'), { width: 24, height: 8 })), x = c.getContext('2d', { willReadFrequently: true });
    x.drawImage(src, 0, src.height * .72, src.width, src.height * .26, 0, 0, 24, 8);
    const d = x.getImageData(0, 0, 24, 8).data; let sum = 0; for (let i = 0; i < d.length; i += 4) sum += .299 * d[i] + .587 * d[i + 1] + .114 * d[i + 2];
    const L = sum / (d.length / 4) / 255; window.__gfxL = L;
    if (L >= .085) { gfxChk.done = true; return; }
    if (++gfxChk.bad >= 2) { gfxChk.done = true; if (ULTRA) localStorage.setItem('ns1_hq', '0'); else { localStorage.setItem('ns1_gfx', '"retro"'); localStorage.setItem('ns1_gfxAuto', '1'); } location.reload(); }
  } catch (e) { gfxChk.done = true; }
}
let released = false;   // мир отпущен перед перезагрузкой страницы — больше не рисуем (иначе three.js спотыкается о потерянный контекст)
function draw(g, W, H, id, o) {
  if (released) return false;
  if (!init()) return false;
  const R = window.__W3core.R;
  const t = o.t || 0, T0 = o.T || 0, dt = Math.min(.05, Math.max(0, T0 - lastT)) || .016; lastT = T0;
  const CO = o.compile || 0;   // фоновый прогрев (warmMore): всё как перед рисованием, но без поз; 1 — только собрать шейдеры, 2 — кадр в буфер (тени), на экран не идёт
  const newShot = id !== lastId, cont = newShot && !!lastId && sameView(SH[ALIAS[lastId] || lastId], SH[ALIAS[id] || id]); lastId = id;
  const { sh, o: loc, used } = stage(id, t, o, cont);
  const LIVE = !CO || !(o.live && [...used].some(k => o.live.has(k)));   // прогрев: позы считаем, только если этих людей сейчас нет на экране (иначе собьём им взгляд и шаг)
  // кто говорит: рот двигается по громкости голоса
  for (const k in A) { A[k].talk = 0; A[k].vis = null; A[k].react = null; A[k].hear = 0; }
  if (o.react) for (const who in o.react) for (const k of NAME[who] || []) if (used.has(k)) A[k].react = o.react[who];   // кивок/«нет»/руки скрещены, лицо — по реплике (движок, beat.react)
  if (o.talk && o.talk.who) { let sp = null;
    for (const k of NAME[o.talk.who] || []) if (used.has(k)) { A[k].talk = o.talk.v; A[k].vis = o.talk.vis || null; sp = k; break; }   // vis — форма губ под букву (движок)
    // остальные в кадре слушают (и того, кто говорит по телефону или рации): кивают в паузах, смотрят на говорящего. Голос из темноты, за дверью,
    // за стеклом, «не свой» (оно подражает) — слушают замерев, без «угу»
    const fx = (window.__ns && window.__ns.talkFx) || '', eerie = /^(fake|muffle|glass|phonefake|near)$/.test(fx) || o.talk.who === '???' || o.talk.who === 'ГОЛОС';
    for (const k of used) if (k !== sp) { A[k].hear = o.talk.v; A[k].hearOk = !eerie; } }
  if (sh.hum && used.has(sh.hum) && !(o.talk && o.talk.who)) A[sh.hum].talk = .1 + .12 * Math.abs(Math.sin(t * 4.3)) * (Math.sin(t * 1.1) > -.5 ? 1 : 0);   // мурлычет себе под нос
  if (LIVE) {
  if (newShot || o.settle) for (const k of used) { A[k].smDone = false; if (o.settle || !cont || hardCut(A[k])) A[k].cut = true; }   // после склейки — сразу новая поза (и покадровый Жилец тоже), без перехода из прошлого кадра; тот же ракурс — продолжаем
  // перемотка инструментов (__ns.seek): 0,9 с движения без рисования — снимок показывает позу и взгляд такими, какими их видит игрок, а не на полпути
  if (o.settle) for (let i = 9; i > 0; i--) for (const k of used) A[k].update(T0 - i * .1, .1);
  for (const k of used) A[k].update(T0, dt);
  solidPass(loc, used, sh, T0, id);   // твёрдый мир: кто вошёл в стену или предмет — отходит, поза заново
  }
  loc.tick(T0, dt);
  if (LIVE) {
  if (loc.post) loc.post(A, used, T0);
  if (sh.post) sh.post(loc, t, used);   // кадр смерти: кишки, раны, лужи — по костям после поз
  wetPass(sh, used);   // кто стоит под дождём — одежда и волосы мокрые
  contactShadows(loc, used);   // мягкая тень под ступнями и телом — человек стоит на полу, а не висит над ним
  // скример строго в лицо: голову Жильца ставим на ось камеры (поза рук и корпуса уводили её влево)
  const M = A.monster;
  if (M.aim && M.aim.on && used.has('monster')) { M.bone.Head.updateWorldMatrix(true, false); M.bone.Head.getWorldPosition(_hp); const P = sh.pos; _hd.set(sh.look[0] - P[0], 0, sh.look[2] - P[2]).normalize();
    const rx = _hp.x - P[0], rz = _hp.z - P[2], al = rx * _hd.x + rz * _hd.z;
    M.root.position.x -= rx - _hd.x * al; M.root.position.z -= rz - _hd.z * al; M.root.position.y += (P[1] - .04 - _hp.y) * M.aim.k; M.root.updateMatrixWorld(true); }
  }
  let crept = false;
  // на мониторах в будке: лицо вплотную к камере холла
  if (o.creep && !CO && sh.feeds && !used.has('monster') && L.lobby === loc) { const C = A.monster; if (C.root.parent !== loc.S) loc.S.add(C.root); C.root.position.set(5.6, 0, 3.0); C.root.rotation.y = .75; C.pose = 'monster'; C.look = new T.Vector3(7.45, 3.85, 5.45); C.update(T0, dt); crept = true; }
  if (CO) ;
  else if (sh.feeds && (crept || window.nsTV ? (crept || ++feedT % 6 === 0) : true)) renderFeeds(R, T0, sh, t, crept || window.nsTV ? null : feedT++ % 3);   // 24 ноя: по одной камере за кадр (каждая — раз в 3 кадра, как было), а не все три разом — без рывка каждый третий кадр   // ТВ: картинка мониторов будки — реже
  else if (!sh.feeds && loc === L.lobby && (newShot || ++feedT % 20 === 0)) renderFeeds(R, T0, null, t, newShot ? null : (feedT / 20 | 0) % 3);   // мониторы видны и в других кадрах будки: не чёрные, а камеры (по одной раз в ~2 с)
  aim(cam, sh.pos, sh.look, sh.fov, o.cx ?? .5, o.cy ?? .5, o.z ?? 1, o.sx || 0, o.sy || 0, (o.cctv ?? sh.cctv) || o.scare || sh.noDolly ? 0 : .5);   // камеры наблюдения и бросок — только объективом
  if (sh.roll) cam.rotateZ(typeof sh.roll === 'function' ? sh.roll(t) : sh.roll);   // завал горизонта: упал на пол, голова набок
  if (o.sr) cam.rotateZ(o.sr * .0004);   // лёгкий крен камеры с рук (движок: handCam)
  // фонарь в руке: луч туда, куда показывает палец (o.torch = {x, y} — доли экрана)
  if (!loc.torch && /^(corridor|stairs|basement)$/.test(loc.name)) { const l = loc.torch = new T.SpotLight(0xfff0d8, 0, 0, .3, .35, 1); l.map = TX.torch; loc.S.add(l, l.target); }
  // крупный план: контровой свет из-за головы (холодный, сверху-сзади) — волосы и плечи отделяются от тёмного фона, лицо объёмнее
  { if (!loc.rimC) { const l = loc.rimC = new T.SpotLight(0xbfd4ff, 0, 3.2, .5, .7, 1.6); l.castShadow = false; loc.S.add(l, l.target); }
    let best = null, bd = 1.3; for (const k of used) { const C = A[k]; if (!C.bone || !C.bone.Head || C.o.monster) continue; C.bone.Head.getWorldPosition(_hp); const d = _hp.distanceTo(cam.position); if (d < bd) { bd = d; best = _hp.clone(); } }
    const l = loc.rimC; if (best && !sh.noRim) { _hd.copy(best).sub(cam.position); _hd.y = 0; _hd.normalize(); l.position.copy(best).addScaledVector(_hd, .9).add(_p.set(0, .7, 0)); l.target.position.copy(best); l.target.updateMatrixWorld(); l.intensity += (((CINE ? .7 : .9) * (sh.rim ?? 1)) - l.intensity) * .3; } else l.intensity *= .7;   // подальше и повыше: ободок, а не блик-пятно на макушке
    if (CINE && best && sh.fog == null && loc.S.fog) loc.S.fog.density *= 1.45;   // «Кино», крупный план: фон глубже уходит в темноту — лицо отделяется от фона (как отрицательная заливка на площадке)
    // «Кино», крупный план: слабый свет у объектива (как маленький прибор у камеры на площадке) — блик в глазах и чуть заполнения; кадр, где лицо задумано тёмным, — sh.noRim
    if (CINE && best && !sh.noRim && loc.faceL && !loc.faceL.intensity) { const l = loc.faceL; cam.updateMatrixWorld(); l.position.set(.22, .12, 0).applyMatrix4(cam.matrixWorld); l.intensity = .22; l.distance = 2.6; l.color.setHex(0xfff2e6); } }
  const tor = o.torch || sh.torch;   // кадр сам может держать фонарь (sh.torch = {x, y}) — например, спуск по лестнице
  if (loc.torch) { const l = loc.torch; l.intensity = tor ? 9 : 0;
    if (tor) { cam.updateMatrixWorld(); l.position.set(.18, -.25, 0).applyMatrix4(cam.matrixWorld); _p.set(tor.x * 2 - 1, 1 - tor.y * 2, .5).unproject(cam).sub(cam.position).normalize().multiplyScalar(10).add(cam.position);
      // луч ведёт рука, а не камера: направление сглажено — тряска кадра и шаги не дёргают пятно света
      if (newShot || !loc.torchAim) loc.torchAim = _p.clone(); else loc.torchAim.lerp(_p, Math.min(1, dt * 7));
      l.target.position.copy(loc.torchAim); l.target.updateMatrixWorld(); } }
  if (o.creep && !CO && !crept && !used.has('monster') && placeCreep(loc, o.creep, R)) A.monster.update(T0, dt);
  const cctv = o.cctv ?? sh.cctv ?? 0;
  const PU = postMat.uniforms; PU.uCctv.value = cctv ? 1 : 0; PU.uIr.value = loc.ir ? 1 : 0; if (PB && PB.comp) PB.comp.uniforms.uIr.value = loc.ir ? 1 : 0; PU.uT.value = T0; PU.uScare.value = clamp(o.scare || 0, 0, 1); PU.uVhs.value = o.vhs ?? VHS;
  if (loc === L.lobby) {
    const U = loc.glassU, lit = clamp((loc.l1.intensity / 1.9) * .55 + (loc.l4.intensity / 1.25) * .25 + loc.amb.intensity * .5, 0, 1.4);
    U.uLit.value = lit; U.uT.value = T0; U.uFogD.value = loc.S.fog.density; U.uRef.value *= loc.reflBoost ?? 1;
    U.uDropC.value.setRGB(.42, .46, .52).multiplyScalar(.5 + .5 * clamp(loc.canopy.intensity / 1.5 + loc.street.intensity / 2.4, 0, 1.4));
    cam.updateMatrixWorld(); if (!CO) renderRefl(R, loc, used, newShot);
  }
  for (const k of used) if (A[k].reflOnly) A[k].root.visible = false;
  if (CINE) { cineTick(loc, T0); if (!CO) R.shadowMap.needsUpdate = newShot || (++shadowN & 1) === 0; }   // 24 ноя: и на ПК тени через кадр (2 лампы × 6 сторон = 12 проходов сцены — самое дорогое в «Кино»); на склейке — сразу   // на телефоне тени обновляются через кадр: это самая дорогая часть «Кино»
  if (CO === 1) return compileOnly(R, loc.S);
  if (CO) { const R0 = R.getRenderTarget(); R.shadowMap.needsUpdate = true; R.setRenderTarget(warmRT()); R.render(loc.S, cam); R.setRenderTarget(R0); return null; }   // 24 ноя: прогрев рисует в крошечный буфер того же вида (шейдеры те же), а не в полный кадр — меню не лагает   // тени ламп (их шейдеры собираются только при рисовании)
  R.setRenderTarget(window.__W3core.RT); R.render(loc.S, cam);
  if (CINE) cineRender(R, T0, cctv ? 1 : 0, clamp(o.scare || 0, 0, 1), o.vhs ?? VHS);
  else { R.setRenderTarget(null); R.render(postScene, postCam); }
  g.imageSmoothingEnabled = CINE;
  if (sh.mirror) { g.save(); g.translate(W, 0); g.scale(-1, 1); g.drawImage(canvas, 0, 0, W, H); g.restore(); } else g.drawImage(canvas, 0, 0, W, H);
  lastMirror = !!sh.mirror;
  if (CINE && id === 'door_katya' && !gfxChk.done) gfxCheck(canvas);   // титул: не рисует ли «Кино» чёрным на этой видеокарте
  g.imageSmoothingEnabled = true;
  return true;
}
// прогрев: по кадру из каждого места, чтобы все шейдеры собрались до того, как понадобятся
// (стук в серверную «долго грузился» — сборка шейдеров подвала и серверной посреди сцены). Шейдеры под каждую расстановку света.
// warm(true) при загрузке — только титул, пролог и будка (меню появляется сразу: раньше все 29 мест собирались до меню, на слабом ПК — десятки секунд);
// остальное — warmMore() в фоне, пока игрок в меню и в прологе: без рисования, драйвер собирает шейдеры в своих потоках (KHR_parallel_shader_compile)
const WARM = ['door_katya', 'exterior', 'pro_ext', 'pro_two', 'pro_offer', 'hall_cam', 'monitors', 'booth_call', 'katya_face', 'jr_desk', 'cam02', 'corridor_cam', 'server_mih',
  'cam13_mih_monster', 'ny_photo', 'srv_room', 'bm_view', 'bs_knock', 'bs_open', 'bs_frank', 'srv_in', 'srv_dark', 'srv_hold', 'srv_crawl', 'c13_gus', 'gl_face', 'h_crouch',
  'f7_close', 'lunge_server', 'cam_m2'], WARM1 = 5;   // первые WARM1 — до меню: титул, улица и сменщик в прологе (будка и мониторы — первыми в фоне)
let warmQ = null, warmBusy = null;
const warmIds = () => warmQ || (warmQ = WARM.filter(id => SH[ALIAS[id] || id]));
function warm(first, n) {   // n — сколько мест собрать сейчас (ТВ: по одному, между ними кадр загрузки)
  if (first && navigator.webdriver && !/[?&]warm=bg/.test(location.search) && !n) first = false;   // автотесты и снимки кадров (Playwright): всё до меню, как раньше — в медленном программном рендере сборка посреди сцены сбивает их часы
  const c = document.createElement('canvas'); c.width = 54; c.height = 96; const g = c.getContext('2d'), q = warmIds();
  for (const id of q.splice(0, n || (first ? WARM1 : q.length))) { if (window.__ldStep) window.__ldStep('прогрев ' + id); draw(g, 54, 96, id, { t: 1, T: 1 }); }
  return q.length;
}
// один кадр из очереди прогрева; обещание — когда его шейдеры собраны (null — очередь пуста). Персонажи, камера и «прошлый кадр» остаются как были:
// титул и идущая сцена не замечают, что между их кадрами собирался чужой
// full — ещё и кадр в буфер (тени ламп): только в меню и на паузе, в игре это лишний кадр работы видеокарты (шейдеры теней маленькие — соберутся при первом кадре места)
function warmMore(full) {
  if (warmBusy) return warmBusy; const q = warmIds(); if (!q.length || !init()) return null;
  const id = q.shift();
  return (warmBusy = Promise.resolve(warmRun(id, 1)).catch(() => {}).then(() => full ? warmRun(id, 2) : null).then(() => { warmBusy = null; }));
}
function warmRun(id, k) {
  const keep = [lastId, lastT, curLoc], ak = {}, cp = cam.position.clone(), cq = cam.quaternion.clone(), cf = [cam.fov, cam.aspect];   // камера — как в последнем кадре (по ней World.project ставит точки обхода)
  for (const n in A) { const C = A[n]; ak[n] = [C.lastShot, C.lookDir, C.prevP, C.walkV, C.solD, C.root.parent]; }
  const live = new Set(Object.keys(A).filter(n => A[n].root.parent));   // кто сейчас в кадре
  let p = null; try { p = draw(null, 54, 96, id, { t: 1, T: 1, compile: k, live }); } catch (e) { console.warn('warm', id, e); }
  [lastId, lastT] = keep;
  for (const n in A) { const C = A[n], a = ak[n]; [C.lastShot, C.lookDir, C.prevP, C.walkV, C.solD] = a; if (C.root.parent !== a[5]) { if (a[5]) a[5].add(C.root); else if (C.root.parent) C.root.parent.remove(C.root); } }
  if (keep[2] && curLoc !== keep[2]) { curLoc.reset(); curLoc = keep[2]; }
  cam.position.copy(cp); cam.quaternion.copy(cq); [cam.fov, cam.aspect] = cf; cam.updateProjectionMatrix(); cam.updateMatrixWorld();
  return p;
}
// собрать шейдеры сцены, не рисуя: как в R.render в буфер кадра (те же тон и цвет на выходе — иначе соберутся не те шейдеры)
let wRT = null;
function warmRT() { if (!wRT) { const M = window.__W3core.RT; wRT = new T.WebGLRenderTarget(64, 36, CINE ? { type: T.HalfFloatType, samples: M.samples || 0 } : { magFilter: T.NearestFilter, minFilter: T.NearestFilter }); } return wRT; }
function compileOnly(R, S) {
  const R0 = R.getRenderTarget(); R.setRenderTarget(warmRT()); let mats = null;
  try { mats = R.compile(S, cam); } finally { R.setRenderTarget(R0); }
  if (!mats || !mats.size || !R.properties) return Promise.resolve();
  return new Promise(res => { const t0 = performance.now(), tick = () => {
    for (const m of mats) { let ok = true; try { const pr = R.properties.get(m).currentProgram; ok = !pr || pr.isReady(); } catch (e) {} if (ok) mats.delete(m); }
    if (!mats.size || performance.now() - t0 > 20000) res(); else setTimeout(tick, 16); }; tick(); });
}
// точка мира → место на кадре (доли ширины и высоты) по камере последнего кадра: точки обхода стоят на предметах
const _pj = new T.Vector3(); let lastMirror = false;
function project(p) { cam.updateMatrixWorld(); _pj.set(p[0], p[1], p[2]).project(cam); const x = (_pj.x + 1) / 2;
  return [lastMirror ? 1 - x : x, (1 - _pj.y) / 2, _pj.z < 1 && Math.abs(_pj.x) < 1.1 && Math.abs(_pj.y) < 1.1]; }
// точка мира → место на общем плане кадра id (без зума): так план «наезд на лицо» целится в само лицо в любой ориентации экрана
const _pc = new T.PerspectiveCamera();
function projectAt(id, p) { const sh = SH[ALIAS[id] || id]; if (!sh) return [.5, .5, false]; _pc.aspect = V.RW / V.RH; aim(_pc, sh.pos, sh.look, sh.fov, .5, .5, 1, 0, 0);
  _pc.updateMatrixWorld(); _pj.set(p[0], p[1], p[2]).project(_pc); return [(_pj.x + 1) / 2, (1 - _pj.y) / 2, _pj.z < 1]; }
const thumbs = {};
function thumb(id, o = {}) {   // маленькая картинка кадра (плитки камер в обходе)
  const key = id + (V.land ? '|L' : ''); if (thumbs[key]) return thumbs[key];
  const c = document.createElement('canvas'); c.width = V.land ? 240 : 135; c.height = V.land ? 135 : 240;
  if (!draw(c.getContext('2d'), c.width, c.height, id, Object.assign({ t: 2, T: 2 }, o))) return '';
  return (thumbs[key] = c.toDataURL('image/jpeg', .7));
}
// World.props — «нормы» холла (куртка, пропуск, часы); World.vhs — сила плёнки 0..1 (для пункта меню); World.tube — горит ли сейчас ненадёжная лампа (для звука)
// для автопроверки кадров (tools/story/sheet.js): где на последнем кадре головы актёров — [x, y доли кадра, в кадре ли]; NAME — кто говорит → чьё лицо
const _hq = new T.Vector3();
function heads() { const r = {}; if (!A) return r; for (const k in A) { const C = A[k]; if (!C.root.parent || !C.root.visible || C.reflOnly) continue; const hb = C.bone && C.bone.Head;
  if (hb) hb.getWorldPosition(_hq); else { C.root.getWorldPosition(_hq); _hq.y += 1.6; } r[k] = project([_hq.x, _hq.y + .06, _hq.z]); } return r; }
/* ---------- твёрдый мир: тела не проходят сквозь стены и предметы ----------
   Как в играх: у предметов места — простые коллайдеры (коробка, цилиндр; стена — плита 30 см за плоскостью; стекло, табличка, наклейка — тонкая пластина),
   у тела — шары по костям, их размер подобран по самой модели (один раз на персонажа). Каждый кадр после поз: вошёл в стену или предмет — персонаж отходит
   по полу ровно настолько, чтобы выйти, и поза пересчитывается на новом месте (рука на ручке двери, предметы в руках — где надо). Вверх не толкаем: сидит
   на стуле, ладонь на столе, нога на ступеньке — это опора. «Снаружи» — там, где таз: тонкую дверь, стекло и табличку тело насквозь не проскакивает.
   Те же коллайдеры видит детектор проникновений (clips). Кадр: sh.ghost = true | ['monster'] — кто проходит сквозь (видение, а не тело) */
const SOL_TOL = { core: .012, head: .012, leg: .015, foot: .02, arm: .02, hand: .03 };   // касание до стольких метров — не проникновение (ладонь на стене, плечо у косяка)
const SOL_SEG = [['pelvis', 'spine_01', 'core', 'thigh'], ['spine_01', 'spine_02', 'core', 'thigh'], ['spine_02', 'spine_03', 'core', 'upperarm'], ['spine_03', 'neck_01', 'core', 'upperarm'], ['neck_01', 'Head', 'core'],
  ['upperarm_l', 'lowerarm_l', 'arm'], ['lowerarm_l', 'hand_l', 'arm'], ['hand_l', 'middle_01_l', 'hand'], ['middle_01_l', 'middle_03_l', 'hand'],
  ['upperarm_r', 'lowerarm_r', 'arm'], ['lowerarm_r', 'hand_r', 'arm'], ['hand_r', 'middle_01_r', 'hand'], ['middle_01_r', 'middle_03_r', 'hand'],
  ['thigh_l', 'calf_l', 'leg'], ['calf_l', 'foot_l', 'leg'], ['foot_l', 'ball_l', 'foot'], ['thigh_r', 'calf_r', 'leg'], ['calf_r', 'foot_r', 'leg'], ['foot_r', 'ball_r', 'foot']];   // кость → следующая, группа, ось ширины (корпус шире, чем толще)
// 21 ноя (владелец, тестер: «герои сухие под дождём»): мокрые одежда и волосы — темнее и с блеском, кожа — с влажным бликом. Кто мокрый: в холле (lobby)
// тот, кто стоит снаружи (z > 6,1) не под навесом (|x| < 2,5, z < 8,4 — там сухо); Лили (katya…) снаружи всегда мокрая — она пришла с улицы;
// sh.wet = { актёр: 0…1 } — свой уровень (Итан в прологе вошёл с дождя — куртка влажная). Цвет — от исходного, запомненного у материала; сухой актёр
// возвращается к исходному один раз и дальше не трогается (у кожи цвет не меняем вовсе — только блеск)
const WET_SKIP = /eyebrow|eyelash|high-poly|teeth|tongue/, WET_SKIN = /\.body$/, WET_HAIR = /hair|short0|long0|buzz|parted|beard/i;
function wetOf(k, C, sh) {
  if (sh.wet && sh.wet[k] != null) return sh.wet[k];
  if (sh.loc !== 'lobby' || !C.root) return 0;
  const p = C.root.position, out = seg(p.z, 6.1, 6.9);
  if (/^katya/.test(k) && p.z > 6.05) return 1;
  return Math.abs(p.x) < 2.5 && p.z < 8.4 ? 0 : out;
}
function wetPass(sh, used) {
  for (const k of used) { const C = A[k]; if (!C || !C.root) continue; const w = wetOf(k, C, sh);
    if (!(w > 0) && !C.wetW) continue; C.wetW = w > 0 ? w : 0;
    if (!C.wetM) { C.wetM = []; C.root.traverse(n => { if (n.isMesh) for (const m of [].concat(n.material)) if (m && m.color && !WET_SKIP.test(m.name || '') && !C.wetM.includes(m)) C.wetM.push(m); }); }
    for (const m of C.wetM) { const b = m.userData.wb || (m.userData.wb = { c: m.color.clone(), r: m.roughness, s: m.shininess }), skin = WET_SKIN.test(m.name || ''), hair = WET_HAIR.test(m.name || '');
      if (!skin) m.color.copy(b.c).multiplyScalar(1 - (hair ? .3 : .4) * w);
      if (b.r != null) m.roughness = lerp(b.r, Math.min(b.r, skin ? .4 : hair ? .28 : .38), w);
      if (b.s != null) m.shininess = lerp(b.s, Math.max(b.s, skin ? 40 : 70), w); } }
}
function solidCols(o) {   // коллайдеры места: список один раз (сцена без актёров и предметов в руках), положение — каждый кадр (solidNow)
  if (o._sol) return o._sol;
  const out = [], roots = new Set(Object.values(A).map(C => C.root));
  const add = (m, kind, lc, lh, x) => out.push(Object.assign({ m, kind, lc, lh, c: new T.Vector3(), c0: null, u: [new T.Vector3(), new T.Vector3(), new T.Vector3()], e: [0, 0, 0], dP: new T.Vector3(1, 0, 0), br: 0, on: false, small: false, thin: false, ax: 0, sg: 1, sup: false, pq: -1, pa: [0, 0] }, x));
  (function walk(n) {
    if (roots.has(n) || n.userData.solid === false || n.isLight) return;
    const sd = n.userData.solid;
    if (Array.isArray(sd)) { for (const b of sd) add(n, 'box', new T.Vector3(b[0], b[1], b[2]), [b[3] / 2, b[4] / 2, b[5] / 2], { name: b[6] || n.name, wall: !!b[7] }); return; }   // свой коллайдер у сложного предмета (табличка-домик, стена с проёмом) — детей не смотрим
    if (n.isMesh && !n.isSkinnedMesh && !n.isInstancedMesh && n.geometry) {
      const P = n.geometry.parameters || {}, ty = n.geometry.type, mt = [].concat(n.material);
      const back = mt.some(x => x && x.side === T.BackSide), clear = mt.every(x => x && x.transparent && !x.depthWrite);   // комната, видимая изнутри (за дверью 707), — не предмет
      if (ty === 'BoxGeometry' && !back && !clear) add(n, 'box', new T.Vector3(), [P.width / 2, P.height / 2, P.depth / 2], {});
      else if (ty === 'CylinderGeometry' && !back && !clear) add(n, 'cyl', new T.Vector3(), [Math.max(P.radiusTop, P.radiusBottom), P.height / 2, 0], {});
      else if (ty === 'PlaneGeometry' && !back && !mt.every(x => x && x.polygonOffset)) {   // стена — плита за лицом; стекло (прозрачное), двустороннее и мелкое — тонкая пластина; наклейка (polygonOffset: табличка, тень, плакат) лежит на чужой поверхности — не коллайдер
        const two = clear || mt.some(x => x && x.side === T.DoubleSide), wall = !two && P.width >= 1 && P.height >= 1.2, D = wall ? .3 : .02;
        add(n, 'box', new T.Vector3(0, 0, two ? 0 : -D / 2), [P.width / 2, P.height / 2, D / 2], { plane: true, wall, glass: clear });
      }
    }
    for (const ch of n.children) walk(ch);
  })(o.S);
  return (o._sol = out);
}
function solidNow(K, full) {   // где коллайдер сейчас: центр, оси, полуразмеры в мире; выключен — невидим, лежит (пол, лист на столе), мелочь, на полу
  K.on = false; for (let q = K.m; q; q = q.parent) if (!q.visible) return;
  K.m.updateWorldMatrix(true, false); const M = K.m.matrixWorld, e = M.elements, s = [0, 0, 0];
  for (let i = 0; i < 3; i++) { const u = K.u[i].set(e[i * 4], e[i * 4 + 1], e[i * 4 + 2]); s[i] = u.length() || 1; u.multiplyScalar(1 / s[i]); K.e[i] = K.lh[i] * s[i]; }
  if (K.kind === 'cyl') { K.e[0] = K.lh[0] * Math.max(s[0], s[2]); K.e[2] = K.e[0]; }
  K.c.copy(K.lc).applyMatrix4(M); (K.c0 || (K.c0 = new T.Vector3())).copy(K.c);
  if (K.plane && Math.abs(K.u[2].y) > .5) return;   // лежащая плоскость (пол, лужа, лист на столе) — не преграда
  const ey = K.kind === 'cyl' ? Math.abs(K.u[1].y) * K.e[1] + Math.sqrt(Math.max(0, 1 - K.u[1].y * K.u[1].y)) * K.e[0] : Math.abs(K.u[0].y) * K.e[0] + Math.abs(K.u[1].y) * K.e[1] + Math.abs(K.u[2].y) * K.e[2];
  if (K.c.y + ey < .08) return;   // на полу (коврик, порог, нижний плинтус): по нему ходят
  const mx = 2 * Math.max(K.e[0], K.e[1], K.e[2]); if (mx < .15) return;   // мелочь (кружка, ключи, кнопка) тело не держит
  K.small = mx < .4; K.thin = K.kind === 'cyl' && K.e[0] < .1; K.br = Math.hypot(K.e[0], K.e[1], K.e[2]); K.on = true;
}
const _sq = new T.Vector3(), _sh = [0, 0], _cq = new T.Vector3();
function solidSide(K, P) {   // с какой стороны предмета таз персонажа — туда и выталкиваем; ось почти отвесна — это опора (сиденье, стол, ступень)
  _sq.subVectors(P, K.c);
  if (K.kind === 'cyl') { const a = K.u[1], h = _sq.dot(a), rx = _sq.x - a.x * h, ry = _sq.y - a.y * h, rz = _sq.z - a.z * h, rl = Math.hypot(rx, ry, rz);
    if (rl <= K.e[0] && Math.abs(h) > K.e[1]) { K.ax = 1; K.sg = Math.sign(h) || 1; K.sup = Math.abs(a.y) > .6; return; }   // над торцом (сидит на табурете) или под ним
    K.ax = -1; K.sup = false; if (rl > 1e-4) K.dP.set(rx / rl, ry / rl, rz / rl); return; }
  let bi = 0, bo = -1e9, mi = 0, mn = 1e9, inside = true, ti = -1;
  for (let i = 0; i < 3; i++) { const o = Math.abs(_sq.dot(K.u[i])) - K.e[i]; if (o > bo) { bo = o; bi = i; } if (o > 0) inside = false; if (-o < mn) { mn = -o; mi = i; } if (K.e[i] <= .03 && (ti < 0 || K.e[i] < K.e[ti])) ti = i; }
  const i = ti >= 0 ? ti : inside ? mi : bi; K.ax = i; K.sg = Math.sign(_sq.dot(K.u[i])) || 1; K.sup = Math.abs(K.u[i].y) > .6;   // тонкая пластина (столешница, стекло, дверь) — только по толщине: локти на столе — опора, а не «сквозь стол вдоль»
  K.pq = Math.abs(_sq.dot(K.u[i])); K.pa[0] = _sq.dot(K.u[(i + 1) % 3]); K.pa[1] = _sq.dot(K.u[(i + 2) % 3]);   // где таз относительно пластины: для проверки «насквозь или мимо края» (solidPen)
}
function solidPen(K, p, r, depth) {   // насколько шар (r = 0 — точка) вошёл в предмет со стороны таза; _sh — куда выталкивать по полу. depth — просто глубина (детектор, и для опор)
  _sq.subVectors(p, K.c);
  if (K.kind === 'cyl') {
    const a = K.u[1], h = _sq.dot(a);
    if (K.ax === 1) { const rx = _sq.x - a.x * h, ry = _sq.y - a.y * h, rz = _sq.z - a.z * h; if (Math.hypot(rx, ry, rz) > K.e[0] + r * .7) return 0; const pen = K.e[1] + r - h * K.sg; return depth && pen > 0 ? pen : 0; }
    if (Math.abs(h) > K.e[1] + r * .7) return 0;
    const rx = _sq.x - a.x * h, ry = _sq.y - a.y * h, rz = _sq.z - a.z * h; let pen, dx, dy, dz;
    if (K.thin) { const sd = rx * K.dP.x + ry * K.dP.y + rz * K.dP.z; if (Math.hypot(rx - K.dP.x * sd, ry - K.dP.y * sd, rz - K.dP.z * sd) > K.e[0] + r * .7) return 0; pen = K.e[0] + r - sd; dx = K.dP.x; dy = K.dP.y; dz = K.dP.z; }   // столбик, перила, черенок: насквозь не пройти
    else { const rl = Math.hypot(rx, ry, rz); pen = K.e[0] + r - rl; if (rl > 1e-4) { dx = rx / rl; dy = ry / rl; dz = rz / rl; } else { dx = K.dP.x; dy = K.dP.y; dz = K.dP.z; } }
    if (pen <= 0) return 0; if (depth) return pen;
    const hl = Math.hypot(dx, dz); if (hl < .8) return 0;   // сверху или снизу трубы — опора
    _sh[0] = dx / hl; _sh[1] = dz / hl; return pen / hl;
  }
  const i = K.ax, u = K.u[i];
  for (let j = 0; j < 3; j++) if (j !== i && Math.abs(_sq.dot(K.u[j])) > K.e[j] + r * .7) return 0;
  let side = false; if (!depth) for (let j = 0; j < 3; j++) if (j !== i && Math.abs(_sq.dot(K.u[j])) > K.e[j]) side = true;
  if (side && Math.abs(_sq.dot(u)) <= K.e[i] + r) {   // центр шара у боковой грани или ребра (рука скользит по крышке, бедро у края спинки): выталкиваем от ближайшей точки предмета, а не со стороны таза
    _cq.copy(K.c); for (let j = 0; j < 3; j++) _cq.addScaledVector(K.u[j], clamp(_sq.dot(K.u[j]), -K.e[j], K.e[j]));
    _cq.subVectors(p, _cq); const dl = _cq.length(), pn = r - dl; if (pn <= 0 || dl < 1e-5) return 0;
    const hl = Math.hypot(_cq.x, _cq.z) / dl; if (hl < .8) return 0;   // сверху на крышке — опора
    _sh[0] = _cq.x / (hl * dl); _sh[1] = _cq.z / (hl * dl); return pn / hl;
  }
  const q = _sq.dot(u) * K.sg; if (q < -K.e[i] - r && (K.e[i] > .03 || K.sup)) return 0;   // за дальней гранью толстого предмета — обнял колонну, а не прошёл сквозь; над столешницей, когда таз под ней (сидит за столом), — тоже не сквозь
  let pen = K.e[i] + r - q, edge = false;
  if (q < 0 && K.sup) pen = K.e[i] + r + q;   // опора, а таз с другой стороны (сидит за столом, пальцы на столешнице): глубина от той грани, на которой лежит рука
  else if (q < 0 && K.e[i] <= .03 && K.pq >= 0) {   // за тонкой пластиной: насквозь, только если прямая от таза идёт через саму пластину; мимо края (стоит сбоку от спинки стула, нога за ней) — лишь касание с той стороны
    const t = K.pq / Math.max(1e-6, K.pq - q), j = (i + 1) % 3, l = (i + 2) % 3;
    if (Math.abs(K.pa[0] + (_sq.dot(K.u[j]) - K.pa[0]) * t) > K.e[j] + r || Math.abs(K.pa[1] + (_sq.dot(K.u[l]) - K.pa[1]) * t) > K.e[l] + r) { pen = K.e[i] + r + q; edge = true; }
  }
  if (pen <= 0) return 0; if (depth) return pen; if (edge) return 0;
  const hx = u.x * K.sg, hz = u.z * K.sg, hl = Math.hypot(hx, hz); if (hl < .8) return 0;
  _sh[0] = hx / hl; _sh[1] = hz / hl; return pen / hl;
}
function solidFit(C) {   // шары тела по самой модели: для каждого сегмента — радиус по вершинам, которые держит его кость (один раз на персонажа)
  C.root.updateMatrixWorld(true);
  const B = C.bone, ss = C.ss || 1, seg = SOL_SEG.map(() => ({ d: [], la: [], de: [] })), head = [], segOf = {};
  SOL_SEG.forEach((s, i) => { segOf[s[0]] = i; });
  const idx = nm => { if (segOf[nm] != null) return segOf[nm]; if (/^clavicle_/.test(nm)) return segOf.spine_03; let m = /^thumb_0\d_([lr])$/.exec(nm); if (m) return segOf['hand_' + m[1]];
    m = /^(index|middle|ring|pinky)_0\d_([lr])$/.exec(nm); if (m) return segOf['middle_01_' + m[2]]; m = /^ball_([lr])$/.exec(nm); return m ? segOf['foot_' + m[1]] : -1; };
  const fr = SOL_SEG.map(s => { const a = B[s[0]], b = B[s[1]]; if (!a || !b) return null; const A0 = new T.Vector3().setFromMatrixPosition(a.matrixWorld), d = new T.Vector3().setFromMatrixPosition(b.matrixWorld).sub(A0), len = d.length(); d.multiplyScalar(1 / (len || 1));
    let L = null; if (s[3] && B[s[3] + '_l'] && B[s[3] + '_r']) { L = new T.Vector3().setFromMatrixPosition(B[s[3] + '_l'].matrixWorld).sub(new T.Vector3().setFromMatrixPosition(B[s[3] + '_r'].matrixWorld)); L.addScaledVector(d, -L.dot(d)).normalize(); }
    return { A0, d, len, L, D: L ? d.clone().cross(L) : null }; });
  const v = new T.Vector3(), w = new T.Vector3(), hinv = B.Head ? B.Head.matrixWorld.clone().invert() : null;
  C.root.traverse(sm => {
    if (!sm.isSkinnedMesh || !sm.visible) return;
    const g = sm.geometry, si = g.attributes.skinIndex, sw = g.attributes.skinWeight, n = g.attributes.position.count, step = Math.max(1, Math.floor(n / 3000)), bones = sm.skeleton.bones;
    if (!si || !sw) return;
    for (let i = 0; i < n; i += step) {
      let bj = 0, bw = -1; for (let j = 0; j < 4; j++) { const x = sw.getComponent(i, j); if (x > bw) { bw = x; bj = si.getComponent(i, j); } }
      const nm = bones[bj] && bones[bj].name; if (!nm) continue;
      sm.getVertexPosition(i, v); v.applyMatrix4(sm.matrixWorld);
      if (nm === 'Head') { head.push(v.clone()); continue; }
      const k = idx(nm); if (k < 0 || !fr[k]) continue; const F = fr[k];
      w.subVectors(v, F.A0); w.addScaledVector(F.d, -clamp(w.dot(F.d), 0, F.len));
      if (F.L) { seg[k].la.push(Math.abs(w.dot(F.L))); seg[k].de.push(Math.abs(w.dot(F.D))); } else seg[k].d.push(w.length());
    }
  });
  const q = (a, f) => { if (!a.length) return 0; a.sort((x, y) => x - y); return a[Math.min(a.length - 1, Math.floor(a.length * f))]; };
  const DEF = { core: .11, arm: .045, hand: .025, leg: .06, foot: .04 };
  C.solF = { seg: seg.map((S, k) => { if (!fr[k]) return null; const g = SOL_SEG[k][2];
    if (fr[k].L) { const rd = q(S.de, .8) || DEF.core * ss, rl = q(S.la, .9) || rd; return { r: Math.min(.3 * ss, rd * .92), wo: Math.max(0, rl - rd) }; }
    return { r: Math.min(.2 * ss, (q(S.d, g === 'leg' ? .5 : .7) || DEF[g] * ss) * .92), wo: 0 }; }) };
  if (head.length && hinv) { const c = new T.Vector3(); for (const p of head) c.add(p); c.multiplyScalar(1 / head.length); C.solF.hr = Math.min(.2 * ss, q(head.map(p => p.distanceTo(c)), .75) * .92); C.solF.hc = c.applyMatrix4(hinv); }
  C.solS = []; for (let i = 0; i < 200; i++) C.solS.push({ p: new T.Vector3(), r: 0, g: 'core' });
}
const _bA = new T.Vector3(), _bB = new T.Vector3(), _bd = new T.Vector3(), _bL = new T.Vector3(), _bR = new T.Vector3(), SOL_W3 = [-1, 0, 1], SOL_W1 = [0];
function solidBody(C) {   // шары тела в мире по текущей позе → C.solS[0 … C.solN)
  if (!C.solF) solidFit(C);
  const B = C.bone, F = C.solF, S = C.solS; let n = 0;
  C.root.updateMatrixWorld(true);
  for (let k = 0; k < SOL_SEG.length; k++) {
    const sg = SOL_SEG[k], f = F.seg[k]; if (!f) continue;
    _bA.setFromMatrixPosition(B[sg[0]].matrixWorld); _bB.setFromMatrixPosition(B[sg[1]].matrixWorld); _bd.subVectors(_bB, _bA); const len = _bd.length();
    let wo = f.wo; if (wo > .02 && sg[3]) { _bL.setFromMatrixPosition(B[sg[3] + '_l'].matrixWorld).sub(_bR.setFromMatrixPosition(B[sg[3] + '_r'].matrixWorld)); if (len > 1e-4) _bL.addScaledVector(_bd, -_bL.dot(_bd) / (len * len)); _bL.normalize(); } else wo = 0;
    const m = Math.max(2, 1 + Math.ceil(len / (1.2 * Math.max(.02, f.r))));
    for (let j = 0; j < m; j++) for (const o of wo ? SOL_W3 : SOL_W1) { if (n >= S.length) break; const s = S[n++]; s.p.copy(_bA).addScaledVector(_bd, j / (m - 1)); if (o) s.p.addScaledVector(_bL, o * wo); s.r = f.r; s.g = sg[2]; }
  }
  if (F.hc && B.Head && n < S.length) { const s = S[n++]; s.p.copy(F.hc).applyMatrix4(B.Head.matrixWorld); s.r = F.hr; s.g = 'head'; }
  C.solN = n; return S;
}
const _pel = new T.Vector3(), _sp = new T.Vector3(), SOL_Z = [0, 0];
const SOLID_ON = true, SOL_MAX = .15;   // твёрдый мир: мелкие касания (до 15 см) поправляются сами; window.__SOLID = 0 — сравнить без него
const _sw = [0, 0];
function solidWorst(near, S, nS, dx, dz) {   // самое глубокое проникновение шаров тела (сдвинутых на dx, dz) и шаг наружу из него (_sw)
  let best = .001; _sw[0] = _sw[1] = 0;
  for (const K of near) for (let i = 0; i < nS; i++) { const s = S[i]; if (K.small && (s.g === 'hand' || s.g === 'arm')) continue;   // мелкий предмет рука может держать и трогать
    _sp.set(s.p.x + dx, s.p.y, s.p.z + dz); const pen = solidPen(K, _sp, s.r) - SOL_TOL[s.g]; if (pen > best) { best = pen; _sw[0] = _sh[0] * pen; _sw[1] = _sh[1] * pen; } }
  return best;
}
function solidPass(loc, used, sh, T0, id) {   // после поз: каждого, кто вошёл в стену или предмет, — на шаг в сторону, и поза заново
  if (!(window.__SOLID ?? SOLID_ON) || !used.size) return;
  const cols = solidCols(loc), gh = sh.ghost; let any = false;
  for (const k of used) A[k].root.getWorldPosition(A[k].solR || (A[k].solR = new T.Vector3()));
  for (const K of cols) { if (K.c0) { let near = false; for (const k of used) if (K.c0.distanceTo(A[k].solR) < K.br + 4) { near = true; break; } if (!near) { K.on = false; continue; } } solidNow(K); }   // далёкие от всех — не пересчитываем
  for (const k of used) {
    const C = A[k], d0 = C.solD || SOL_Z;
    if (gh === true || (gh && gh.includes(k)) || C.reflOnly || !C.root.visible || !C.bone || !C.bone.pelvis) { if (C.solD) { C.root.position.x -= d0[0]; C.root.position.z -= d0[1]; C.solD = null; any = true; } continue; }
    const S = solidBody(C), nS = C.solN; C.bone.pelvis.getWorldPosition(_pel); _pel.x -= d0[0]; _pel.z -= d0[1];
    for (let i = 0; i < nS; i++) { S[i].p.x -= d0[0]; S[i].p.z -= d0[1]; }   // считаем от места, где персонажа поставил кадр
    const near = []; for (const K of cols) if (K.on && K.c.distanceTo(_pel) < K.br + 1.3 * C.ss) { solidSide(K, _pel); if (!K.sup) near.push(K); }
    let dx = 0, dz = 0; const w0 = near.length ? solidWorst(near, S, nS, 0, 0) : 0;
    for (let it = 0, w = w0; it < 4 && w > .001; it++) {   // самое глубокое проникновение — первым; угол двух стен — за два шага
      dx += _sw[0]; dz += _sw[1]; w = solidWorst(near, S, nS, dx, dz);
      if (it === 3 || w <= .001) { if (w > w0 * .8) dx = dz = 0; break; }   // сдвиг не помог (из одного предмета — в другой): пусть стоит, как поставил кадр
    }
    let L = Math.hypot(dx, dz);
    if (window.__SOLLOG && L > .02) window.__SOLLOG.push([id, k, +dx.toFixed(3), +dz.toFixed(3)]);   // для проверки: кого и насколько отодвинули
    if (L > SOL_MAX) { dx = dz = L = 0; }   // глубже 15 см — это ошибка постановки (стоит в стуле, в перегородке): сдвиг сломал бы кадр, правится руками по отчёту детектора
    // сдвиг от предмета — плавно (22 см/с), а не за один кадр: рука сменщика в разговоре задевала стойку будки, и всё тело прыгало на 8 см
    // (замер 11 ноя: рывок 60 м/с² у обеих рук разом). Первый кадр ракурса и склейки — сразу на место, как раньше
    { const dtS = C.solT != null ? T0 - C.solT : -1; C.solT = T0;
      if (C.solSm && dtS >= 0 && dtS < .25) { const ex = dx - d0[0], ez = dz - d0[1], e = Math.hypot(ex, ez), st = .22 * dtS; if (e > st) { dx = d0[0] + (e > 0 ? ex / e * st : 0); dz = d0[1] + (e > 0 ? ez / e * st : 0); L = Math.hypot(dx, dz); } }
      C.solSm = true; }
    if (Math.hypot(dx - d0[0], dz - d0[1]) > .003) { C.root.position.x += dx - d0[0]; C.root.position.z += dz - d0[1]; C.solD = L > 1e-4 ? [dx, dz] : null; any = true; }
  }
  if (any) for (const k of used) A[k].update(T0, 0);   // поза на новом месте: касания руками и предметы в руках — заново
  if (window.__LIMBS ?? true) solidLimbs(cols, used, sh, id);
}
/* ---------- твёрдый мир для рук и ног (19 ноя) ----------
   solidPass отодвигает от стены всё тело целиком, но руки и ноги жили сами: кисть в жесте проходила сквозь столешницу, предплечье — сквозь
   спинку стула, ступни сидящего уходили под пол (владелец, 19 ноя: «проходят сквозь стены, как привидения»). Теперь после позы каждая рука
   проверяется против предметов: кисть, запястье или предплечье, вошедшие в стол, стену или шкаф, выносятся на поверхность с той стороны,
   откуда рука пришла (где локоть), и рука пересобирается (локоть в своей плоскости, кисть не поворачивается, предмет в руке едет с ней).
   Рука, лежащая на столе, не трогается (зазор до кости — толщина ладони). Ступня стоящего или сидящего не опускается ниже пола.
   Жилец и «призраки» кадра — как были. Кадр может отключить: sh.limbs = false. window.__LIMBS = 0 — сравнить без этого; window.__LIMBLOG = [] — журнал. */
const _Lq = new T.Vector3(), _Le = new T.Vector3(), _Lv = new T.Vector3(), _Lb = new T.Vector3(), _Lw = new T.Vector3(), _Lel = new T.Vector3(), _Lsh = new T.Vector3(), _Lmid = new T.Vector3(), _Lpm = new T.Vector3();
const LIMB_F = ['index', 'middle', 'pinky'], _Ltip = [new T.Vector3(), new T.Vector3(), new T.Vector3()];
function limbPush(K, p, cl, el) {   // точка руки p ближе cl к поверхности предмета K (или внутри) → глубина; _Lv — вектор наружу. el — локоть: рука пришла с его стороны
  _Lq.subVectors(p, K.c); _Le.subVectors(el, K.c);
  if (K.kind === 'cyl') { const a = K.u[1], h = _Lq.dot(a), rx = _Lq.x - a.x * h, ry = _Lq.y - a.y * h, rz = _Lq.z - a.z * h, rl = Math.hypot(rx, ry, rz);
    const dr = K.e[0] + cl - rl, da = K.e[1] + cl - Math.abs(h); if (dr <= 0 || da <= 0) return 0;
    const he = _Le.dot(a); if (Math.abs(he) > K.e[1] && (he > 0) === (h > 0) || (da < dr && rl < 1e-4)) { _Lv.copy(a).multiplyScalar((he > 0 ? 1 : -1) * da); return da; }   // локоть за торцом — наружу через торец
    if (rl < 1e-4) return 0; _Lv.set(rx / rl * dr, ry / rl * dr, rz / rl * dr); return dr; }
  let bj = -1, bd = 1e9, bs = 1, fj = 0, fd = 1e9, fs = 1;
  for (let j = 0; j < 3; j++) { const q = _Lq.dot(K.u[j]), d = K.e[j] + cl - Math.abs(q); if (d <= 0) return 0; if (d < fd) { fd = d; fj = j; fs = q < 0 ? -1 : 1; }
    const qe = _Le.dot(K.u[j]); if (Math.abs(qe) > K.e[j]) { const sg = qe < 0 ? -1 : 1, de = K.e[j] + cl - sg * q; if (de < bd) { bd = de; bj = j; bs = sg; } } }
  if (bj < 0) { bj = fj; bd = fd; bs = fs; }   // и локоть внутри (стоит в предмете — ошибка постановки): ближайшая грань
  _Lv.copy(K.u[bj]).multiplyScalar(bs * bd); return bd;
}
function solidLimbs(cols, used, sh, id) {
  const W3 = window.__W3chars; if (!W3 || !W3.limbTo || sh.limbs === false) return; const gh = sh.ghost, LOG = window.__LIMBLOG;
  for (const k of used) { const C = A[k], B = C.bone;
    if (gh === true || (gh && gh.includes(k)) || C.reflOnly || !C.root.visible || !B || !B.pelvis || !B.hand_l || (C.o && C.o.monster)) continue;
    const ss = C.ss || 1; C.root.updateWorldMatrix(true, true); B.pelvis.getWorldPosition(_pel);
    const near = []; for (const K of cols) if (K.on && !K.small && K.c.distanceTo(_pel) < K.br + 1.3 * ss) near.push(K);
    if (near.length) for (const sd of ['l', 'r']) { const U = B['upperarm_' + sd], Lo = B['lowerarm_' + sd], H = B['hand_' + sd], M = B['middle_01_' + sd]; if (!U || !Lo || !H) continue;
      let moved = 0, nm = '';
      for (let it = 0; it < 3; it++) {
        U.getWorldPosition(_Lsh); Lo.getWorldPosition(_Lel); H.getWorldPosition(_Lw); _Lmid.lerpVectors(_Lel, _Lw, .55); if (M) M.getWorldPosition(_Lpm).lerp(_Lw, .45); else _Lpm.copy(_Lw);
        let best = .0015, K0 = null; _Lb.set(0, 0, 0);
        const pts = [[_Lpm, .012 * ss, _Lel], [_Lw, .02 * ss, _Lel], [_Lmid, .03 * ss, _Lsh]];
        for (let f = 0; f < 3; f++) { const b3 = B[LIMB_F[f] + '_03_' + sd], b2 = B[LIMB_F[f] + '_02_' + sd]; if (!b3 || !b2) continue;   // кончики пальцев: согнутые пальцы не уходят в столешницу (касание кончиком — можно)
          b3.getWorldPosition(_Ltip[f]); _Ltip[f].addScaledVector(_Lq.copy(_Ltip[f]).sub(b2.getWorldPosition(_Le)), .85); pts.push([_Ltip[f], .003 * ss, _Lel]); }
        for (const K of near) for (const [p, cl, el] of pts) { const d = limbPush(K, p, cl, el); if (d > best) { best = d; _Lb.copy(_Lv); K0 = K; } }
        if (!K0 || best > .3) break;   // глубже 30 см — ошибка постановки, руками (как у solidPass)
        W3.limbTo(C, sd, false, _Lw.x + _Lb.x, _Lw.y + _Lb.y, _Lw.z + _Lb.z); moved = Math.max(moved, best); nm = solidName(K0); }
      if (LOG && moved > .01) LOG.push([id, k, 'рука ' + sd, nm, Math.round(moved * 1000)]); }
    // ступни: стоит или сидит (таз высоко) — голеностоп не ниже 7,5 см над полом, носок и пальцы стопы не под полом
    const fl = Math.min(0, C.root.position.y);
    if (_pel.y - fl > .38 * ss && sh.feet !== false) { let lift = 0;
      for (const sd of ['l', 'r']) { const F = B['foot_' + sd], Bl = B['ball_' + sd]; if (!F) continue; F.getWorldPosition(_Lw); if (Bl) Bl.getWorldPosition(_Lpm);
        const up = fl + .075 * ss - _Lw.y;   // пятка в 7,5 см под голеностопом: и стопа плашмя, и носок вверх (садится/встаёт — пятка уходила в пол на 4 см)
        if (up > .008 && up < .3) { W3.limbTo(C, sd, true, _Lw.x, _Lw.y + up, _Lw.z); lift = Math.max(lift, up); } }
      W3.footFloor(C, fl); W3.toeFloor(C, fl);   // носок не под полом; на носке пальцы стопы сгибаются и ложатся на пол
      if (lift && LOG && lift > .01) LOG.push([id, k, 'ступни', 'пол', Math.round(lift * 1000)]); }
  }
}
function solidName(K) { const m = K.m; return K.name || m.name || (m.parent && m.parent.name) || (K.wall ? 'стена' : K.glass ? 'стекло' : K.plane ? 'пластина' : K.kind === 'cyl' ? 'цилиндр' : 'коробка'); }
// проникновения тел в стены и предметы (для проверок: tools/story/sheet.js, tools/anim/clip_check.js): вершины тела (каждая N-я, с учётом скелета и мимики)
// в коллайдерах твёрдого мира — глубина со стороны таза, с допуском tol (касание — не проникновение). → [{ k, prop, n вершин, d глубина м, at точка мира, size, scr }]
const _cw = new T.Vector3(), _cp = new T.Vector3();
function clips(tol = .02) {
  const out = []; if (!A || !curLoc || !curLoc.S) return out;
  const cols = solidCols(curLoc); for (const K of cols) solidNow(K);
  for (const k in A) {
    const C = A[k]; if (!C.root.parent || !C.root.visible || C.reflOnly) continue;
    C.root.updateMatrixWorld(true); if (C.bone && C.bone.pelvis) C.bone.pelvis.getWorldPosition(_cp); else C.root.getWorldPosition(_cp).y += .9;
    const near = cols.filter(K => K.on && K.c.distanceTo(_cp) < K.br + 1.4 * (C.ss || 1)); near.forEach(K => solidSide(K, _cp));
    const hit = new Map();
    if (near.length) C.root.traverse(sm => {
      if (!sm.isSkinnedMesh || !sm.visible) return; const n = sm.geometry.attributes.position.count, step = Math.max(1, Math.floor(n / 400));
      for (let i = 0; i < n; i += step) {
        sm.getVertexPosition(i, _cw); _cw.applyMatrix4(sm.matrixWorld);
        for (const K of near) { const d = solidPen(K, _cw, 0, true); if (d <= tol) continue;
          let h = hit.get(K); if (!h) hit.set(K, h = { n: 0, d: 0, at: null }); h.n++; if (d > h.d) { h.d = d; h.at = [+_cw.x.toFixed(3), +_cw.y.toFixed(3), +_cw.z.toFixed(3)]; } }
      }
    });
    for (const [K, h] of hit) out.push({ k, prop: solidName(K), size: [+(2 * K.e[0]).toFixed(2), +(2 * K.e[1]).toFixed(2), +(2 * K.e[2]).toFixed(2)], n: h.n, d: +h.d.toFixed(3), at: h.at, scr: project(h.at), sup: K.sup, wall: !!K.wall });
    { const y0 = Math.min(0, C.root.position.y); let n = 0, d = 0, at = null;   // ниже пола (пол — y 0; корень может стоять выше — сидит/лежит на подъёме): ступня или таз ушли под пол
      C.root.traverse(sm => { if (!sm.isSkinnedMesh || !sm.visible) return; const cnt = sm.geometry.attributes.position.count, step = Math.max(1, Math.floor(cnt / 400));
        for (let i = 0; i < cnt; i += step) { sm.getVertexPosition(i, _cw); _cw.applyMatrix4(sm.matrixWorld); const dd = y0 - _cw.y; if (dd > tol) { n++; if (dd > d) { d = dd; at = [+_cw.x.toFixed(3), +_cw.y.toFixed(3), +_cw.z.toFixed(3)]; } } } });
      if (n) out.push({ k, prop: 'пол', size: [0, 0, 0], n, d: +d.toFixed(3), at, scr: project(at) }); }
  }
  return out.sort((x, y) => y.d - x.d);
}
// для отладки: почему твёрдый мир двигает персонажа k — по каждому предмету самый глубокий шар тела (как в solidPass, от места, где его поставил кадр)
function solidWhy(k) {
  const C = A[k]; if (!C || !curLoc || !C.bone || !C.bone.pelvis) return [];
  const cols = solidCols(curLoc), d0 = C.solD || SOL_Z, S = solidBody(C), out = []; for (const K of cols) solidNow(K);
  C.bone.pelvis.getWorldPosition(_pel); _pel.x -= d0[0]; _pel.z -= d0[1];
  for (const K of cols) { if (!K.on || K.c.distanceTo(_pel) >= K.br + 1.3 * C.ss) continue; solidSide(K, _pel); if (K.sup) continue;
    let best = 0, bs = null; for (let i = 0; i < C.solN; i++) { const s = S[i]; if (K.small && (s.g === 'hand' || s.g === 'arm')) continue;
      _sp.set(s.p.x - d0[0], s.p.y, s.p.z - d0[1]); const pen = solidPen(K, _sp, s.r) - SOL_TOL[s.g]; if (pen > best) { best = pen; bs = s; } }
    if (bs) out.push({ prop: solidName(K), c: [K.c.x, K.c.y, K.c.z].map(x => +x.toFixed(2)), size: K.e.map(x => +(2 * x).toFixed(2)), pen: +best.toFixed(3), g: bs.g, r: +bs.r.toFixed(3), at: [bs.p.x - d0[0], bs.p.y, bs.p.z - d0[1]].map(x => +x.toFixed(2)), push: [+_sh[0].toFixed(2), +_sh[1].toFixed(2)] }); }
  return out.sort((x, y) => y.pen - x.pen);
}
// для отладки: включённые коллайдеры места (что «твёрдое» в текущем кадре)
function solids() { if (!curLoc) return []; return solidCols(curLoc).map(K => (solidNow(K), K)).filter(K => K.on).map(K => ({ prop: solidName(K), kind: K.kind, wall: !!K.wall, glass: !!K.glass, small: K.small, c: [+K.c.x.toFixed(2), +K.c.y.toFixed(2), +K.c.z.toFixed(2)], size: K.e.map(x => +(2 * x).toFixed(2)) })); }
window.World = { scareAt, release: () => { const R = window.__W3core.R; released = true; if (!R) return; try { R.forceContextLoss(); } catch (e) {} try { R.dispose(); } catch (e) {} },
  dbgShot: (id, base, d) => { SH[id] = Object.assign({}, SH[base], d); },   /* отладочный ракурс того же кадра (tools/anim/phone_view.js) */
  draw, init, warm, warmMore, get warmLeft() { return warmQ ? warmQ.length : -1; }, get warmBusy() { return !!warmBusy; }, setLand, project, projectAt, heads, clips, solids, solidWhy, gfit: GFIT, goreTL: id => { const g = goreShot(id); return (g && g.tl) || null; }, names: NAME, perf: perfTick, get actors() { return A; }, get land() { return V.land; }, get cine() { return CINE; }, get ultra() { return ULTRA; }, thumb, shots: SH, alias: ALIAS, props: null, get ok() { return scenesReady; }, get tube() { return curLoc ? curLoc.tubeOK ?? 1 : 1; }, get ir() { return !!(curLoc && curLoc.ir); }, set vhs(v) { VHS = clamp(+v || 0, 0, 1); }, get vhs() { return VHS; } };
})();
