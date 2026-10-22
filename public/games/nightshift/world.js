/* «Ночная смена» — 3D-мир. Всё построено кодом: геометрия, текстуры, персонажи, свет. Никаких готовых картинок.
   Стиль «ночная плёнка»: низкое разрешение, дрожащие вершины как на старых приставках, ступенчатое сглаживание цвета (дизеринг),
   натриевый жёлтый свет фонарей против холодного зелёного света мониторов.
   Снаружи: World.draw(g, shotId, o) рисует кадр на 2D-холст движка; o = { cx, cy, z, t, T, sx, sy, beat, talk: { who, v } }. */
(() => {
'use strict';
const T = window.THREE;
// внутреннее разрешение кадра. Два вида графики (меню «Графика», хранится в ns1_gfx, ?gfx=retro|cine — вручную):
//  «Ретро» — низкое разрешение, дрожь вершин, ступени цвета (телефон по умолчанию); на ПК вдвое чётче (?hd=0/1);
//  «Кино» — полное разрешение, мягкие тени, блики и отражения, объёмный свет, пыль в лучах, глубина резкости (ПК по умолчанию).
// Ориентация меняется на лету (World.setLand): 9:16 или 16:9; дрожь вершин, строки и рябь VHS считаются по базовой сетке.
const FINE = matchMedia('(pointer: fine)').matches;
const HD = (() => { const q = /[?&]hd=([01])/.exec(location.search); if (q) return q[1] === '1' ? 2 : 1;
  return FINE && innerWidth > innerHeight ? 2 : 1; })();
const GFX = (() => { const q = /[?&]gfx=(retro|cine)/.exec(location.search); if (q) return q[1];
  try { const v = JSON.parse(localStorage.getItem('ns1_gfx')); if (v === 'retro' || v === 'cine') return v; } catch {} return FINE ? 'cine' : 'retro'; })();
const CINE = GFX === 'cine';
const V = { land: false, BW: 270, BH: 480, RW: 270 * HD, RH: 480 * HD, HD, cine: CINE };
let R = null, RT = null, post = null;          // рендерер, буфер кадра, проход постобработки
const rnd = (() => { let s = 1337; return () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; })();
const lerp = (a, b, k) => a + (b - a) * k, clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/* ---------- материалы: дрожание вершин (PS1) ---------- */
const SNAP = { value: new T.Vector2(108, 192) };
function setLand(land) {
  V.land = !!land; V.BW = land ? 480 : 270; V.BH = land ? 270 : 480;
  if (CINE) { const L = (FINE ? 1920 : 1280) * (V.rs || 1) | 0, S = L * 9 / 16 | 0; V.RW = land ? L : S; V.RH = land ? S : L; }
  else { V.RW = V.BW * HD; V.RH = V.BH * HD; }
  if (CINE) SNAP.value.set(1e5, 1e5); else SNAP.value.set(V.BW / 2.5, V.BH / 2.5);   // в «Кино» вершины не дрожат
}
setLand(false);
function ps1(m) {
  m.onBeforeCompile = sh => {
    sh.uniforms.uSnap = SNAP;
    sh.vertexShader = 'uniform vec2 uSnap;\n' + sh.vertexShader.replace('#include <project_vertex>',
      '#include <project_vertex>\n  gl_Position.xy = floor(gl_Position.xy / gl_Position.w * uSnap + .5) / uSnap * gl_Position.w;');
  };
  return m;
}
const MC = {};
// «Кино»: у поверхностей есть шероховатость и блеск — мокрый мрамор и линолеум отражают лампы, металл блестит.
// Рельеф (bump) берётся из той же текстуры: швы плитки, щели панелей, фактура бетона дают тени от света
function pbr(map, o) {
  const k = map ? map.kind : '', R = { marble: .3, lino: .38, asphalt: .45, metal: .32, door: .62, panel: .55, wood: .55, rack: .4, tileSide: .5, curb: .8, carpet: .95, ceil: .9, wall: .82, concrete: .78 };
  const r = { roughness: o.rough ?? R[k] ?? .78, metalness: o.metal ?? (k === 'metal' ? .65 : k === 'rack' ? .3 : 0), envMapIntensity: o.env ?? .7 };
  if (map && map.bumpTex) { r.bumpMap = map.bumpTex; r.bumpScale = o.bump ?? (k === 'marble' || k === 'lino' || k === 'tileSide' ? 1.6 : k === 'ceil' ? 2.5 : 3); }
  return r;
}
// материал для людей и вещей с особыми шейдерами: в «Кино» — с бликами, в «Ретро» — матовый
function litMat(p, rough = .7, metal = 0) { return CINE ? new T.MeshStandardMaterial(Object.assign({ roughness: rough, metalness: metal, envMapIntensity: .5 }, p)) : new T.MeshLambertMaterial(p); }
// наклейка поверх другой поверхности (плинтус-панель на стене, табличка, светильник на потолке): чуть «ближе» по глубине,
// иначе на телефоне две плоскости дерутся за пиксель и рисунок мигает рваными пятнами (особенно с дрожью вершин PS1)
const DECAL = { polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 };
function mat(color, map, o = {}) {
  const k = color + '|' + (map ? map.uuid : '') + '|' + JSON.stringify(o);
  if (MC[k]) return MC[k];
  const M = o.basic ? T.MeshBasicMaterial : CINE ? T.MeshStandardMaterial : T.MeshLambertMaterial;
  const m = new M(Object.assign({ color, map: map || null }, o.basic ? {} : { emissive: o.emissive || 0x000000 }, !o.basic && CINE ? pbr(map, o) : {},
    o.transparent ? { transparent: true, opacity: o.opacity ?? 1, depthWrite: false } : {}, o.side ? { side: o.side } : {}, o.decal ? DECAL : {}));
  if (CINE && !o.basic && !o.transparent) { m.castShadow = true; }
  if (o.fog === false) m.fog = false;
  return (MC[k] = ps1(m));
}

/* ---------- процедурные текстуры (рисуются на canvas) ---------- */
// «Кино»: текстура рисуется вчетверо крупнее (до 1024 px) той же кистью, сверху — мелкая фактура (поры, царапины, пыль),
// фильтр сглаженный и анизотропный: вблизи нет квадратиков, под углом пол не мылится
const UP = CINE ? 4 : 1;
let fineN = null;
function fineNoise() {
  if (fineN) return fineN; fineN = document.createElement('canvas'); fineN.width = fineN.height = 256; const x = fineN.getContext('2d'), d = x.createImageData(256, 256);
  for (let i = 0; i < d.data.length; i += 4) { const v = 128 + (Math.random() - .5) * 120 * (Math.random() < .03 ? 2 : 1); d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 255; }
  x.putImageData(d, 0, 0); return fineN;
}
function ctex(w, h, fn, rx = 1, ry = 1, kind = '') {
  const up = kind === 'raw' ? 1 : Math.max(1, Math.min(UP, 1024 / Math.max(w, h)) | 0);
  const c = document.createElement('canvas'); c.width = w * up; c.height = h * up;
  const x = c.getContext('2d'); if (up > 1) x.scale(up, up); fn(x, w, h);
  const t = new T.CanvasTexture(c);
  if (up > 1) { t.magFilter = T.LinearFilter; t.minFilter = T.LinearMipmapLinearFilter; t.anisotropy = 8; }
  else { t.magFilter = T.NearestFilter; t.minFilter = T.NearestMipmapLinearFilter; }
  t.generateMipmaps = true;   // вдали без ряби
  t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(rx, ry); t.colorSpace = T.SRGBColorSpace; t.cv = c; t.cx = x; t.lw = w; t.lh = h; t.kind = kind === 'raw' ? '' : kind;
  return t;
}
// сплошная поверхность (пол, стены, двери): в «Кино» поверх — мелкая фактура; kind задаёт блеск и рельеф (pbr)
function surf(t, kind) {
  t.kind = kind; if (UP < 2) return t; const x = t.cx;
  x.save(); x.setTransform(1, 0, 0, 1, 0, 0); x.globalCompositeOperation = 'overlay'; x.globalAlpha = .22; x.fillStyle = x.createPattern(fineNoise(), 'repeat'); x.fillRect(0, 0, t.cv.width, t.cv.height); x.restore();
  t.needsUpdate = true; return t;
}
function speckle(x, w, h, n, a, light = true) { for (let i = 0; i < n; i++) { x.fillStyle = `rgba(${light ? '255,255,255' : '0,0,0'},${a * rnd()})`; x.fillRect(rnd() * w | 0, rnd() * h | 0, 1 + (rnd() * 2 | 0), 1); } }
const TX = {};
function makeTextures() {
  TX.marble = ctex(64, 64, (x, w, h) => {      // мрамор пола: крупная плитка с прожилками
    for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) { x.fillStyle = (i + j) % 2 ? '#3c3f44' : '#55585c'; x.fillRect(i * 32, j * 32, 32, 32); }
    speckle(x, w, h, 500, .12); speckle(x, w, h, 300, .2, false);
    x.strokeStyle = 'rgba(220,220,225,.18)'; for (let i = 0; i < 6; i++) { x.beginPath(); let px = rnd() * w, py = rnd() * h; x.moveTo(px, py); for (let k = 0; k < 5; k++) { px += (rnd() - .5) * 20; py += rnd() * 12; x.lineTo(px, py); } x.stroke(); }
    x.fillStyle = 'rgba(0,0,0,.5)'; x.fillRect(0, 0, w, 1); x.fillRect(0, 0, 1, h); x.fillRect(32, 0, 1, h); x.fillRect(0, 32, w, 1);
  }, 8, 6);
  TX.wall = ctex(64, 64, (x, w, h) => { x.fillStyle = '#4a5250'; x.fillRect(0, 0, w, h); speckle(x, w, h, 700, .08); speckle(x, w, h, 500, .14, false); x.fillStyle = 'rgba(0,0,0,.25)'; for (let i = 0; i < 3; i++) x.fillRect(rnd() * w, 0, 2 + rnd() * 4, h); }, 6, 2);
  TX.panel = ctex(64, 32, (x, w, h) => { x.fillStyle = '#3b2a1e'; x.fillRect(0, 0, w, h); x.strokeStyle = 'rgba(0,0,0,.45)'; for (let i = 0; i < w; i += 16) x.strokeRect(i + .5, .5, 16, h - 1); speckle(x, w, h, 200, .07); x.fillStyle = 'rgba(255,220,170,.06)'; for (let i = 0; i < 30; i++) x.fillRect(rnd() * w, rnd() * h, 6, 1); }, 8, 1);
  TX.ceil = ctex(32, 32, (x, w, h) => { x.fillStyle = '#6a6c66'; x.fillRect(0, 0, w, h); speckle(x, w, h, 200, .2, false); x.fillStyle = '#2b2d2a'; x.fillRect(0, 0, w, 1); x.fillRect(0, 0, 1, h); }, 16, 12);
  TX.metal = ctex(32, 64, (x, w, h) => { const gr = x.createLinearGradient(0, 0, w, 0); gr.addColorStop(0, '#7b8086'); gr.addColorStop(.5, '#a3a8ad'); gr.addColorStop(1, '#6c7176'); x.fillStyle = gr; x.fillRect(0, 0, w, h); for (let i = 0; i < 60; i++) { x.fillStyle = `rgba(255,255,255,${.05 * rnd()})`; x.fillRect(0, rnd() * h, w, 1); } x.fillStyle = '#2a2c2e'; x.fillRect(w / 2 - .5, 0, 1, h); });
  TX.asphalt = ctex(64, 64, (x, w, h) => { x.fillStyle = '#1b1d20'; x.fillRect(0, 0, w, h); speckle(x, w, h, 900, .12); for (let i = 0; i < 5; i++) { x.fillStyle = 'rgba(120,130,150,.18)'; x.beginPath(); x.ellipse(rnd() * w, rnd() * h, 6 + rnd() * 10, 3 + rnd() * 5, 0, 0, 7); x.fill(); } }, 10, 10);
  TX.curb = ctex(32, 16, (x, w, h) => { x.fillStyle = '#5b5d60'; x.fillRect(0, 0, w, h); speckle(x, w, h, 120, .2, false); x.fillStyle = '#333'; x.fillRect(0, h - 2, w, 2); }, 20, 1);
  TX.tileSide = ctex(32, 32, (x, w, h) => { x.fillStyle = '#5e605e'; x.fillRect(0, 0, w, h); speckle(x, w, h, 150, .15, false); x.fillStyle = 'rgba(0,0,0,.4)'; x.fillRect(0, 0, w, 1); x.fillRect(0, 0, 1, h); x.fillRect(16, 0, 1, h); x.fillRect(0, 16, w, 1); }, 20, 3);
  TX.facade = ctex(128, 256, (x, w, h) => {     // фасад бизнес-центра: сетка окон, кое-где горит свет
    x.fillStyle = '#16191d'; x.fillRect(0, 0, w, h);
    for (let r = 0; r < 16; r++) for (let c = 0; c < 8; c++) {
      const lit = rnd() < .12, warm = rnd() < .6;
      x.fillStyle = lit ? (warm ? '#d9b46a' : '#9fc4d0') : `rgb(${22 + rnd() * 14 | 0},${28 + rnd() * 14 | 0},${36 + rnd() * 16 | 0})`;
      x.fillRect(c * 16 + 3, r * 16 + 3, 10, 10);
      if (lit) { x.fillStyle = 'rgba(0,0,0,.35)'; x.fillRect(c * 16 + 3, r * 16 + 9, 10, 4); }
    }
    for (let r = 0; r < 16; r++) { x.fillStyle = '#0e1013'; x.fillRect(0, r * 16, w, 2); }
  }, 2, 2);
  TX.f7lit = null;
  TX.carpet = ctex(32, 32, (x, w, h) => { x.fillStyle = '#3a2224'; x.fillRect(0, 0, w, h); speckle(x, w, h, 300, .08); x.strokeStyle = 'rgba(200,150,90,.18)'; x.strokeRect(4.5, 4.5, 23, 23); }, 2, 14);
  TX.door = ctex(32, 64, (x, w, h) => { x.fillStyle = '#5a4030'; x.fillRect(0, 0, w, h); x.strokeStyle = 'rgba(0,0,0,.4)'; x.strokeRect(4.5, 4.5, w - 9, 24); x.strokeRect(4.5, 34.5, w - 9, 25); speckle(x, w, h, 120, .06); x.fillStyle = '#c9b27a'; x.fillRect(w - 7, 30, 3, 4); });
  TX.rack = ctex(32, 64, (x, w, h) => { x.fillStyle = '#15171a'; x.fillRect(0, 0, w, h); for (let r = 0; r < 16; r++) { x.fillStyle = '#23262b'; x.fillRect(2, r * 4 + 1, w - 4, 3); for (let k = 0; k < 4; k++) if (rnd() < .5) { x.fillStyle = rnd() < .8 ? '#3cff7a' : '#ffb03c'; x.fillRect(4 + k * 3, r * 4 + 2, 1, 1); } } });
  TX.concrete = ctex(64, 64, (x, w, h) => { x.fillStyle = '#3a3c3a'; x.fillRect(0, 0, w, h); speckle(x, w, h, 900, .1); speckle(x, w, h, 600, .16, false); x.fillStyle = 'rgba(30,20,10,.25)'; for (let i = 0; i < 4; i++) x.fillRect(rnd() * w, rnd() * h, 1, 10 + rnd() * 20); }, 4, 2);
  TX.salt = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h); x.strokeStyle = 'rgba(235,235,225,.95)'; x.lineWidth = 3; x.beginPath(); x.arc(32, 32, 26, 0, 7); x.stroke(); for (let i = 0; i < 200; i++) { const a = rnd() * 7, r = 26 + (rnd() - .5) * 6; x.fillStyle = 'rgba(240,240,230,.8)'; x.fillRect(32 + Math.cos(a) * r, 32 + Math.sin(a) * r, 1, 1); } });
  TX.glassRain = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h); for (let i = 0; i < 70; i++) { const px = rnd() * w, py = rnd() * h, l = 2 + rnd() * 8; x.fillStyle = 'rgba(210,225,240,.55)'; x.fillRect(px, py, 1, l); x.fillStyle = 'rgba(255,255,255,.8)'; x.fillRect(px, py + l, 1, 1); } }, 10, 3);
  TX.sign = ctex(256, 48, (x, w, h) => { x.fillStyle = '#101214'; x.fillRect(0, 0, w, h); x.fillStyle = '#c9cdd2'; x.font = '700 28px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('БЦ  «ГОРИЗОНТ»', w / 2, h / 2 + 1); });
  TX.signOut = ctex(256, 48, (x, w, h) => { x.fillStyle = '#0a0b0c'; x.fillRect(0, 0, w, h); x.fillStyle = '#e8eef5'; x.shadowColor = '#9fd0ff'; x.shadowBlur = 8; x.font = '700 30px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('ГОРИЗОНТ', w / 2, h / 2 + 1); });
  TX.guardSign = ctex(64, 16, (x, w, h) => { x.fillStyle = '#7a1414'; x.fillRect(0, 0, w, h); x.fillStyle = '#fff'; x.font = '700 11px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('ОХРАНА', w / 2, h / 2 + 1); });
  TX.exit = ctex(32, 16, (x, w, h) => { x.fillStyle = '#0f6b2c'; x.fillRect(0, 0, w, h); x.fillStyle = '#d8ffe0'; x.font = '700 10px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('ВЫХОД', w / 2, h / 2 + 1); });
  TX.poster = ctex(32, 48, (x, w, h) => { x.fillStyle = '#d8d2c0'; x.fillRect(0, 0, w, h); x.fillStyle = '#7a1414'; x.fillRect(0, 0, w, 10); x.fillStyle = '#222'; for (let i = 0; i < 8; i++) x.fillRect(4, 14 + i * 4, 10 + rnd() * 14, 1); x.fillStyle = '#333'; x.font = '700 6px Arial'; x.fillText('ПРАВИЛА', 4, 7); });
  TX.note = noteTex();
  TX.keys = null;
  TX.wood = ctex(32, 32, (x, w, h) => { x.fillStyle = '#4b3524'; x.fillRect(0, 0, w, h); for (let i = 0; i < 24; i++) { x.fillStyle = `rgba(${rnd() < .5 ? '0,0,0' : '255,210,160'},${.08 * rnd()})`; x.fillRect(0, rnd() * h, w, 1); } }, 2, 1);
  TX.sky = ctex(4, 64, (x, w, h) => { const gr = x.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#06080d'); gr.addColorStop(.7, '#121822'); gr.addColorStop(1, '#2a2620'); x.fillStyle = gr; x.fillRect(0, 0, w, h); });
  TX.skyDawn = ctex(4, 64, (x, w, h) => { const gr = x.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#2b3a55'); gr.addColorStop(.6, '#c77b52'); gr.addColorStop(1, '#f2c27a'); x.fillStyle = gr; x.fillRect(0, 0, w, h); });
  TX.noise = ctex(64, 48, (x, w, h) => { speckle(x, w, h, 1, 0); }, 1, 1, 'raw');
  makeTextures2();
  if (CINE) hiTextures();
  surf(TX.rack, 'rack'); TX.rack.bump = false;
}
// второй набор: стекло фасада (грязь, плёнка, надписи), уборщик (линолеум, кровь, табличка), часы, пропуск
function soft(x, cx, cy, rx, ry, a, col = '255,255,255') {   // мягкое пятно
  x.save(); x.translate(cx, cy); x.scale(1, ry / rx); const g = x.createRadialGradient(0, 0, 0, 0, 0, rx);
  g.addColorStop(0, `rgba(${col},${a})`); g.addColorStop(1, `rgba(${col},0)`); x.fillStyle = g; x.beginPath(); x.arc(0, 0, rx, 0, 7); x.fill(); x.restore();
}
function hand(x, cx, cy, s, a, rot = 0, mirror = false) {   // отпечаток ладони: ладонь + пальцы
  x.save(); x.translate(cx, cy); x.rotate(rot); x.scale(mirror ? -s : s, s); x.fillStyle = `rgba(255,255,255,${a})`;
  x.beginPath(); x.ellipse(0, 0, 4.2, 5, 0, 0, 7); x.fill();
  for (const [fx, fy, l, r] of [[-3, -5, 6.5, .1], [-1, -5.6, 7.5, .03], [1.2, -5.5, 7, -.05], [3.1, -4.6, 5.5, -.15]]) { x.save(); x.translate(fx, fy); x.rotate(r); x.beginPath(); x.ellipse(0, -l / 2, 1, l / 2, 0, 0, 7); x.fill(); x.restore(); }
  x.save(); x.translate(-4.4, 0); x.rotate(-.9); x.beginPath(); x.ellipse(0, -2.8, 1.2, 3.2, 0, 0, 7); x.fill(); x.restore();
  x.restore();
}
function makeTextures2() {
  // грязь на стекле дверей (x −2..2 м, y 0.4..2.4 м): жирные пятна, отпечатки ладоней, мазок тряпкой. Альфа = сколько грязи
  TX.grime = ctex(512, 256, (x, w, h) => {
    x.clearRect(0, 0, w, h);
    for (let i = 0; i < 26; i++) soft(x, rnd() * w, h * (.25 + rnd() * .7), 8 + rnd() * 26, 4 + rnd() * 14, .1 + rnd() * .16);
    x.strokeStyle = 'rgba(255,255,255,.08)'; x.lineWidth = 9; for (let k = 0; k < 3; k++) { x.beginPath(); x.arc(w * (.3 + k * .2), h * .9, 60 + k * 10, -2.6, -.6); x.stroke(); }   // разводы от тряпки
    // отпечатки (128 px на метр): у ручек и на уровне груди
    for (const [px, py, s, a, r, m] of [[.43, .62, 1.9, .3, .1, 0], [.47, .55, 1.8, .22, -.2, 1], [.62, .6, 2, .28, .15, 0], [.3, .45, 1.7, .18, 0, 1], [.69, .52, 1.9, .2, -.1, 1]]) hand(x, px * w, py * h, s, a, r, m);
    soft(x, w * .58, h * .3, 18, 9, .2);   // след лба
  });
  TX.grime.magFilter = T.LinearFilter; TX.grime.minFilter = T.LinearMipmapLinearFilter; TX.grime.wrapS = TX.grime.wrapT = T.ClampToEdgeWrapping;
  // плёночные буквы снаружи: изнутри читаются зеркально — так сразу видно, что между нами стекло
  TX.logo = ctex(512, 96, (x, w, h) => {
    x.clearRect(0, 0, w, h); x.fillStyle = 'rgba(236,240,244,.95)'; x.textAlign = 'center'; x.textBaseline = 'middle';
    x.font = '700 54px Arial'; x.fillText('ГОРИЗОНТ', w / 2, 38); x.font = '700 20px Arial'; x.fillText('Б И З Н Е С - Ц Е Н Т Р', w / 2, 80);
  });
  TX.logo.wrapS = TX.logo.wrapT = T.ClampToEdgeWrapping;
  TX.sticker = ctex(64, 32, (x, w, h) => { x.clearRect(0, 0, w, h); x.fillStyle = 'rgba(240,240,236,.92)'; x.fillRect(2, 4, w - 4, h - 8); x.fillStyle = '#9a1818'; x.font = '700 12px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('ОТ СЕБЯ', w / 2, h / 2 + 1); });
  TX.sticker.wrapS = TX.sticker.wrapT = T.ClampToEdgeWrapping;
  TX.fogBreath = ctex(64, 32, (x, w, h) => { x.clearRect(0, 0, w, h); soft(x, w / 2, h / 2, 30, 15, .95, '235,240,245'); for (let i = 0; i < 90; i++) { x.fillStyle = `rgba(255,255,255,${.3 * rnd()})`; x.fillRect(w / 2 + (rnd() - .5) * 44, h / 2 + (rnd() - .5) * 20, 1, 1); } });
  TX.fogBreath.magFilter = T.LinearFilter; TX.fogBreath.wrapS = TX.fogBreath.wrapT = T.ClampToEdgeWrapping;
  // пол седьмого этажа: офисный линолеум «под плитку» (уборщику есть что мыть)
  TX.lino = ctex(64, 64, (x, w, h) => {
    for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) { x.fillStyle = (i + j) % 2 ? '#5b564e' : '#615b52'; x.fillRect(i * 32, j * 32, 32, 32); }
    speckle(x, w, h, 500, .1); speckle(x, w, h, 400, .16, false);
    x.fillStyle = 'rgba(0,0,0,.35)'; x.fillRect(0, 0, w, 1); x.fillRect(0, 0, 1, h); x.fillRect(32, 0, 1, h); x.fillRect(0, 32, w, 1);
    x.fillStyle = 'rgba(40,30,20,.12)'; for (let i = 0; i < 6; i++) x.fillRect(rnd() * w, rnd() * h, 3 + rnd() * 6, 1);
  }, 2.2, 22);
  TX.puddle = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h); for (let i = 0; i < 9; i++) soft(x, w / 2 + (rnd() - .5) * 26, h / 2 + (rnd() - .5) * 26, 14 + rnd() * 14, 10 + rnd() * 12, .6); });
  TX.puddle.magFilter = T.LinearFilter; TX.puddle.wrapS = TX.puddle.wrapT = T.ClampToEdgeWrapping;
  // блик на мокром полу: отражения ламп — вытянутые светлые полосы вдоль коридора, рваные по краям лужи (рисуется сложением)
  TX.sheen = ctex(64, 128, (x, w, h) => {
    x.fillStyle = '#000'; x.fillRect(0, 0, w, h);
    for (let k = 0; k < 4; k++) { const cy = 14 + k * 33; soft(x, w / 2 + (rnd() - .5) * 6, cy, 9, 15, .75, '210,225,225'); soft(x, w / 2, cy, 20, 26, .18, '150,170,175'); }
    const mc = document.createElement('canvas'); mc.width = w; mc.height = h; const m = mc.getContext('2d');
    for (let i = 0; i < 14; i++) soft(m, w / 2 + (rnd() - .5) * 40, rnd() * h, 16 + rnd() * 16, 14 + rnd() * 20, 1);
    x.globalCompositeOperation = 'destination-in'; x.drawImage(mc, 0, 0); x.globalCompositeOperation = 'source-over';
  });
  TX.sheen.magFilter = T.LinearFilter; TX.sheen.wrapS = TX.sheen.wrapT = T.ClampToEdgeWrapping;
  // кровавый след волоком (вдоль v): густой у двери, рвётся к началу
  TX.smear = ctex(32, 128, (x, w, h) => {
    x.clearRect(0, 0, w, h);
    for (let y = 0; y < h; y++) { const k = y / h, a = .25 + .7 * k, wd = 7 + 9 * k + Math.sin(y * .3) * 2; if (rnd() < .2 * (1 - k)) continue;
      x.fillStyle = `rgba(${70 + rnd() * 30 | 0},${4 + rnd() * 6 | 0},${4 + rnd() * 6 | 0},${a})`; x.fillRect(w / 2 - wd / 2 + Math.sin(y * .07) * 3, y, wd, 1);
      if (rnd() < .3) { x.fillStyle = `rgba(40,2,2,${a})`; x.fillRect(w / 2 - wd / 2 + rnd() * wd, y, 1, 3); } }
  });
  TX.smear.wrapS = TX.smear.wrapT = T.ClampToEdgeWrapping;
  TX.wetSign = ctex(32, 48, (x, w, h) => {
    x.fillStyle = '#e8c018'; x.fillRect(0, 0, w, h); x.fillStyle = '#141414'; x.fillRect(0, 0, w, 2); x.fillRect(0, h - 2, w, 2);
    x.beginPath(); x.moveTo(16, 6); x.lineTo(28, 26); x.lineTo(4, 26); x.closePath(); x.lineWidth = 2; x.strokeStyle = '#141414'; x.stroke();
    x.fillRect(15, 12, 2, 8); x.fillRect(15, 22, 2, 2);
    x.font = '700 6px Arial'; x.textAlign = 'center'; x.fillText('ОСТОРОЖНО', 16, 35); x.fillText('МОКРЫЙ', 16, 41); x.fillText('ПОЛ', 16, 47);
  });
  TX.clock = ctex(64, 64, (x, w, h) => {
    x.fillStyle = '#0d0d0d'; x.beginPath(); x.arc(32, 32, 32, 0, 7); x.fill(); x.fillStyle = '#e6e2d6'; x.beginPath(); x.arc(32, 32, 28, 0, 7); x.fill();
    x.fillStyle = '#1a1a1a'; for (let i = 0; i < 12; i++) { const a = i / 12 * Math.PI * 2; x.save(); x.translate(32, 32); x.rotate(a); x.fillRect(-1, -26, 2, i % 3 ? 4 : 7); x.restore(); }
    x.font = '700 6px Arial'; x.textAlign = 'center'; x.fillText('СЛАВА', 32, 44);
  });
  TX.clock.wrapS = TX.clock.wrapT = T.ClampToEdgeWrapping;
  TX.badge = badgeTex(false); TX.badgeX = badgeTex(true);
}
// пропуск Михалыча: фото (седой, очки, борода) и фамилия; вторая версия — лицо на фото исцарапано
function badgeTex(scratched) {
  return ctex(64, 48, (x, w, h) => {
    x.fillStyle = '#e8e6df'; x.fillRect(0, 0, w, h); x.fillStyle = '#1d3a7a'; x.fillRect(0, 0, w, 10);
    x.fillStyle = '#fff'; x.font = '700 7px Arial'; x.fillText('ПРОПУСК', 4, 8);
    x.fillStyle = '#7d8a96'; x.fillRect(4, 14, 20, 26);
    x.fillStyle = '#d8c0b0'; x.beginPath(); x.ellipse(14, 26, 6, 8, 0, 0, 7); x.fill();
    x.fillStyle = '#e8e8e2'; x.fillRect(8, 17, 12, 3); x.fillRect(9, 29, 10, 6);
    x.fillStyle = '#222'; x.fillRect(9, 24, 4, 2); x.fillRect(15, 24, 4, 2); x.fillRect(13, 24, 2, 1);
    x.fillStyle = '#1b1b1b'; x.font = '700 7px Arial'; x.fillText('МИХАЙЛОВ', 27, 21); x.font = '6px Arial'; x.fillText('В. М.', 27, 29); x.fillText('охрана', 27, 37);
    if (scratched) { x.strokeStyle = 'rgba(20,20,20,.9)'; x.lineWidth = 1.5; for (let i = 0; i < 9; i++) { x.beginPath(); x.moveTo(4 + rnd() * 20, 14 + rnd() * 6); x.lineTo(4 + rnd() * 20, 34 + rnd() * 6); x.stroke(); } }
  });
}
// листок с правилами: печатные строки и приписка ручкой
function noteTex() {
  return ctex(192, 256, (x, w, h) => {
    x.fillStyle = '#e4dcc4'; x.fillRect(0, 0, w, h);
    for (let i = 0; i < 400; i++) { x.fillStyle = `rgba(120,100,60,${.08 * rnd()})`; x.fillRect(rnd() * w, rnd() * h, 2, 2); }
    x.strokeStyle = 'rgba(80,110,160,.25)'; for (let y = 34; y < h; y += 16) { x.beginPath(); x.moveTo(8, y + .5); x.lineTo(w - 8, y + .5); x.stroke(); }
    x.fillStyle = '#1d1d22'; x.font = '700 15px "Courier New", monospace'; x.fillText('ПРАВИЛА СМЕНЫ', 22, 24);
    x.font = '700 11px "Courier New", monospace';
    const L = ['1. В 03:00 НА 7 ЭТАЖЕ', '   ВКЛЮЧИТСЯ СВЕТ.', '   НЕ ПОДНИМАЙСЯ.', '2. ЛИФТ ПРИЕХАЛ САМ —', '   НЕ ЗАХОДИ.', '3. ПОСЛЕ ПОЛУНОЧИ', '   НИКОГО НЕ ВПУСКАЙ.', '4. КАМЕРУ 13', '   НЕ ВКЛЮЧАЙ.'];
    L.forEach((s, i) => x.fillText(s, 12, 46 + i * 16));
    x.save(); x.translate(20, 212); x.rotate(-.06); x.fillStyle = '#1b2f8a'; x.font = 'italic 700 14px "Comic Sans MS", cursive';
    x.fillText('Третье правило — ложь.', 0, 0); x.fillText('Впусти её.   — М.', 8, 18); x.restore();
    x.strokeStyle = 'rgba(160,20,20,.7)'; x.lineWidth = 2; x.beginPath(); x.moveTo(10, 140); x.lineTo(150, 142); x.stroke();
  });
}


/* ---------- «Кино»: текстуры высокого разрешения (рисуются по пикселям из шума, бесшовные) ---------- */
// у каждой своя карта высот (рельеф): швы плитки, поры штукатурки, волокна дерева ловят свет ламп
const NZ = {};
function lat(n, seed) { const a = new Float32Array(n); let s = (seed * 7919 + 13) % 233280; for (let i = 0; i < n; i++) { s = (s * 9301 + 49297) % 233280; a[i] = s / 233280; } return a; }
function vn2(u, v, fx, fy, seed = 0) {   // бесшовный шум: решётка fx×fy повторяется на [0,1)
  const k = fx + 'x' + fy + '|' + seed, a = NZ[k] || (NZ[k] = lat(fx * fy, seed + fx * 31 + fy * 17));
  const x = u * fx, y = v * fy, xi = Math.floor(x), yi = Math.floor(y); let tx = x - xi, ty = y - yi; tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
  const x0 = ((xi % fx) + fx) % fx, y0 = ((yi % fy) + fy) % fy, x1 = (x0 + 1) % fx, y1 = (y0 + 1) % fy;
  const A = a[y0 * fx + x0], B = a[y0 * fx + x1], C = a[y1 * fx + x0], D = a[y1 * fx + x1];
  return A + (B - A) * tx + (C - A) * ty + (A - B - C + D) * tx * ty;
}
function fbm(u, v, fx, fy, oct, seed = 0) { let s = 0, a = .5, n = 0; for (let i = 0; i < oct; i++) { s += a * vn2(u, v, fx << i, fy << i, seed + i * 3); n += a; a *= .5; } return s / n; }
const sm = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const hx = c => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
function hiTex(w, h, rx, ry, fn) {   // fn(u, v, px, py) → [r, g, b, высота 0..1]
  const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d'), d = x.createImageData(w, h);
  const hc = document.createElement('canvas'); hc.width = w; hc.height = h; const hx2 = hc.getContext('2d'), hd = hx2.createImageData(w, h);
  for (let py = 0; py < h; py++) for (let px = 0; px < w; px++) {
    const r = fn(px / w, py / h, px, py), i = (py * w + px) * 4;
    d.data[i] = r[0]; d.data[i + 1] = r[1]; d.data[i + 2] = r[2]; d.data[i + 3] = 255; const hv = clamp(r[3], 0, 1) * 255; hd.data[i] = hd.data[i + 1] = hd.data[i + 2] = hv; hd.data[i + 3] = 255;
  }
  x.putImageData(d, 0, 0); hx2.putImageData(hd, 0, 0);
  const mk = (cv, srgb) => { const t = new T.CanvasTexture(cv); t.magFilter = T.LinearFilter; t.minFilter = T.LinearMipmapLinearFilter; t.anisotropy = 8; t.generateMipmaps = true;
    t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(rx, ry); if (srgb) t.colorSpace = T.SRGBColorSpace; return t; };
  const t = mk(c, true); t.cv = c; t.cx = x; t.lw = w; t.lh = h; t.bumpTex = mk(hc, false); return t;
}
const mul = (c, k) => [c[0] * k, c[1] * k, c[2] * k];
function hiTextures() {
  // штукатурка, крашенная масляной краской: мягкие разводы, потёки, поры, редкие волосяные трещины
  TX.wall = hiTex(512, 512, 6, 2, (u, v) => {
    const n = fbm(u, v, 4, 4, 5, 1), st = fbm(u, v, 2, 2, 3, 9), dr = vn2(u, v, 48, 3, 5), po = vn2(u, v, 256, 256, 7);
    const gb = fbm(u, v, 8, 2, 3, 13), k = .86 + .22 * n - .16 * sm(.55, .8, st) - .1 * sm(.5, 1, dr) * sm(.2, 1, v) - .05 * sm(.88, .97, po) - .07 * sm(.75, 1, v) * (.5 + gb);   // низ стены грязнее
    return [...mul([74, 84, 81], k), .5 + .3 * n - .15 * sm(.88, .97, po)];
  });
  // акустическая плитка потолка: дырочки, фаска по краю, жёлтые пятна от протечек
  TX.ceil = hiTex(256, 256, 16, 12, (u, v, px, py) => {
    const e = Math.min(u, v, 1 - u, 1 - v), pit = vn2(u, v, 128, 128, 3) > .78 ? 1 : 0, wa = fbm(u, v, 2, 2, 3, 4);
    let c = mul([108, 110, 104], .92 + .12 * fbm(u, v, 16, 16, 3, 2) - .35 * pit); if (wa > .62) c = [c[0] * 1.02, c[1] * .96, c[2] * .82];
    if (e < .012) c = [40, 42, 40]; else if (e < .03) c = mul(c, .82 + 6 * e);
    return [...c, e < .012 ? 0 : .7 - pit * .35];
  });
  // мрамор пола: плиты 2×2 двух тонов, прожилки, полировка, тёмные швы
  TX.marble = hiTex(512, 512, 8, 6, (u, v) => {
    const q = (u < .5) !== (v < .5), base = q ? [60, 63, 68] : [85, 88, 92];
    const w = fbm(u + .35 * fbm(u, v, 2, 2, 3, 5), v + .2 * fbm(u, v, 3, 3, 2, 6), 2, 2, 5, q ? 11 : 12), r = Math.pow(1 - Math.abs(2 * w - 1), 24) * (.35 + .65 * sm(.4, .65, fbm(u, v, 2, 2, 2, 14)));
    let c = mul(base, .9 + .16 * fbm(u, v, 6, 6, 4, 3)); c = c.map(x => x + r * 55);
    const gu = Math.min(Math.abs(u - .5), u, 1 - u), gv = Math.min(Math.abs(v - .5), v, 1 - v), gr = Math.min(gu, gv);
    if (gr < .004) c = [22, 22, 24];
    return [...c, gr < .004 ? 0 : .8];
  });
  // линолеум «под плитку» на 7 этаже: крапинки, потёртости, швы
  TX.lino = hiTex(512, 512, 2.2, 22, (u, v) => {
    const q = (u < .5) !== (v < .5), fl = vn2(u, v, 200, 200, q ? 4 : 5);
    let c = mul(q ? [91, 86, 78] : [97, 91, 82], .92 + .14 * fbm(u, v, 6, 6, 4, 6));
    if (fl > .8) c = mul(c, 1.25); else if (fl < .15) c = mul(c, .72);
    const sc = vn2(u, v, 40, 4, 8); if (sc > .78) c = mul(c, .9);
    const gr = Math.min(Math.abs(u - .5), u, 1 - u, Math.abs(v - .5), v, 1 - v); if (gr < .003) c = [40, 36, 32];
    return [...c, gr < .003 ? 0 : .6];
  });
  // деревянные панели: доски, волокна, лак
  const grain = (u, v, boards, seed) => { const b = Math.floor(u * boards), bu = u * boards - b;
    const ww = fbm(u, v, 4, 2, 3, seed + b), ring = .5 + .5 * Math.sin((bu * 22 + ww * 2.5 + b * 3.7) * 3.1416);
    return { b, bu, ring: .6 * ring + .4 * vn2(u, v, 96, 3, seed + 5), fine: vn2(u, v, 256, 12, seed + 2) }; };
  TX.panel = hiTex(512, 256, 8, 1, (u, v) => {
    const g = grain(u, v, 4, 31); let c = mul([59, 42, 30], .78 + .3 * g.ring + .12 * g.fine);
    const gap = Math.min(g.bu, 1 - g.bu); if (gap < .012) c = mul(c, .35);
    return [...c, gap < .012 ? 0 : .5 + .25 * g.ring];
  });
  TX.door = hiTex(256, 512, 1, 1, (u, v) => {
    const g = grain(u, v, 1, 41); let c = mul([92, 66, 48], .8 + .25 * g.ring + .1 * g.fine), hgt = .5 + .2 * g.ring;
    const pan = (y0, y1) => { const dx = Math.min(u - .14, .86 - u), dy = Math.min(v - y0, y1 - v), e = Math.min(dx, dy); return e; };
    for (const [a, b2] of [[.07, .45], [.53, .93]]) { const e = pan(a, b2); if (e > -.012 && e < .012) { c = mul(c, e < 0 ? .7 : 1.12); hgt = e < 0 ? .2 : .9; } }
    if (Math.abs(u - .82) < .045 && Math.abs(v - .5) < .03) { c = [201, 178, 122]; hgt = 1; }
    return [...c, hgt];
  });
  TX.wood = hiTex(256, 256, 2, 1, (u, v) => { const g = grain(v, u, 1, 51); return [...mul([75, 53, 36], .8 + .25 * g.ring + .1 * g.fine), .5 + .2 * g.ring]; });
  // бетон: разводы, поры, ржавые потёки
  TX.concrete = hiTex(512, 512, 4, 2, (u, v) => {
    const n = fbm(u, v, 4, 4, 6, 61), po = vn2(u, v, 300, 300, 62), ru = vn2(u, v, 24, 2, 63);
    let c = mul([58, 60, 58], .8 + .35 * n - (po > .82 ? .25 : 0)); if (ru > .8) c = [c[0] * 1.1, c[1] * .98, c[2] * .85];
    return [...c, .3 + .5 * n - (po > .82 ? .3 : 0)];
  });
  TX.asphalt = hiTex(512, 512, 10, 10, (u, v) => {
    const ag = vn2(u, v, 256, 256, 71), n = fbm(u, v, 3, 3, 4, 72), wet = sm(.55, .7, fbm(u, v, 2, 2, 3, 73));
    let c = mul([27, 29, 32], .8 + .5 * ag * ag + .2 * n); c = c.map((x, i) => x * (1 - .35 * wet) + wet * [12, 14, 18][i]);
    return [...c, .3 + .6 * ag * ag * (1 - wet)];
  });
  TX.carpet = hiTex(256, 256, 2, 14, (u, v) => {
    const f = vn2(u, v, 256, 256, 81), n = fbm(u, v, 4, 4, 3, 82); let c = mul([58, 34, 36], .82 + .25 * f + .15 * n);
    const e = Math.min(u, v, 1 - u, 1 - v); if (e > .13 && e < .145) c = [120, 92, 60];
    return [...c, .4 + .5 * f];
  });
  // металл лифтовых дверей: шлифовка вдоль, отпечатки пальцев, шов посередине
  TX.metal = hiTex(256, 512, 1, 1, (u, v) => {
    const br = vn2(u, v, 3, 700, 91) * .5 + vn2(u, v, 7, 1400, 92) * .5, sm2 = fbm(u, v, 3, 6, 3, 93);
    const gr = .8 + .3 * Math.sin(u * 3.1416); let c = mul([140, 145, 150], gr * (.95 + .06 * br) - .08 * sm(.6, .8, sm2));
    if (Math.abs(u - .5) < .006) c = [42, 44, 46];
    return [...c, .5 + .1 * br];
  });
  TX.tileSide = hiTex(256, 256, 20, 3, (u, v) => {
    let c = mul([94, 96, 94], .88 + .2 * fbm(u, v, 8, 8, 4, 101)); const gr = Math.min(Math.abs(u - .5), u, 1 - u, Math.abs(v - .5), v, 1 - v);
    if (gr < .01) c = [40, 40, 40]; return [...c, gr < .01 ? 0 : .7];
  });
  TX.curb = hiTex(256, 128, 20, 1, (u, v) => { let c = mul([91, 93, 96], .8 + .3 * fbm(u, v, 8, 4, 4, 111)); if (v > .88) c = [51, 51, 51]; return [...c, v > .88 ? .1 : .6]; });
  for (const [k, t] of Object.entries({ wall: 'wall', ceil: 'ceil', marble: 'marble', lino: 'lino', panel: 'panel', door: 'door', wood: 'wood', concrete: 'concrete', asphalt: 'asphalt', carpet: 'carpet', metal: 'metal', tileSide: 'tileSide', curb: 'curb' })) TX[k].kind = t;
}

/* ---------- геометрия: простые кирпичики ---------- */
const GB = {};
function box(w, h, d, m, x = 0, y = 0, z = 0, parent) {
  const k = w + ',' + h + ',' + d; const g = GB[k] || (GB[k] = new T.BoxGeometry(w, h, d));
  const me = new T.Mesh(g, m); me.position.set(x, y, z); if (parent) parent.add(me); return me;
}
function plane(w, h, m, parent) { const me = new T.Mesh(new T.PlaneGeometry(w, h), m); if (parent) parent.add(me); return me; }
function cyl(r1, r2, h, n, m, parent) { const me = new T.Mesh(new T.CylinderGeometry(r1, r2, h, n), m); if (parent) parent.add(me); return me; }
// коробка-комната: видны только внутренние стены. Зеркалить scale −1 нельзя: three.js тогда разворачивает грани обратно,
// и наружная стенка коробки совпадает со стеной коридора — на телефоне это чёрные рваные пятна
function room(w, h, d, mats, parent) {
  const g = new T.BoxGeometry(w, h, d), back = m => { const c = m.clone(); c.side = T.BackSide; c.onBeforeCompile = m.onBeforeCompile; return c; };
  const me = new T.Mesh(g, Array.isArray(mats) ? mats.map(back) : back(mats)); me.position.y = h / 2; parent.add(me); return me;
}
// текст на табличке (динамический, например табло лифта)
function label(w, h, pw, ph, draw, parent, basic = true) {
  const t = ctex(pw, ph, draw); const m = new T.MeshBasicMaterial(Object.assign({ map: t }, DECAL)); ps1(m);
  const me = plane(w, h, m, parent); me.userData.tex = t; me.userData.draw = draw; return me;
}
function relabel(me, ...a) { const t = me.userData.tex; t.cx.clearRect(0, 0, t.lw, t.lh); me.userData.draw(t.cx, t.lw, t.lh, ...a); t.needsUpdate = true; }

window.__W3core = { T, V, setLand, CINE, FINE, litMat, HD, rnd, lerp, clamp, ps1, mat, ctex, speckle, TX, box, plane, cyl, room, label, relabel, makeTextures, SNAP };
})();
