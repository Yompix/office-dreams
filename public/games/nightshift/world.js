/* «Ночная смена» — 3D-мир. Всё построено кодом: геометрия, текстуры, персонажи, свет. Никаких готовых картинок.
   Стиль «ночная плёнка»: низкое разрешение, дрожащие вершины как на старых приставках, ступенчатое сглаживание цвета (дизеринг),
   натриевый жёлтый свет фонарей против холодного зелёного света мониторов.
   Снаружи: World.draw(g, shotId, o) рисует кадр на 2D-холст движка; o = { cx, cy, z, t, T, sx, sy, beat, talk: { who, v } }. */
(() => {
'use strict';
const T = window.THREE;
const RW = 270, RH = 480;                      // внутреннее разрешение кадра (9:16)
let R = null, RT = null, post = null;          // рендерер, буфер кадра, проход постобработки
const rnd = (() => { let s = 1337; return () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; })();
const lerp = (a, b, k) => a + (b - a) * k, clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/* ---------- материалы: дрожание вершин (PS1) ---------- */
const SNAP = { value: new T.Vector2(RW / 2.5, RH / 2.5) };
function ps1(m) {
  m.onBeforeCompile = sh => {
    sh.uniforms.uSnap = SNAP;
    sh.vertexShader = 'uniform vec2 uSnap;\n' + sh.vertexShader.replace('#include <project_vertex>',
      '#include <project_vertex>\n  gl_Position.xy = floor(gl_Position.xy / gl_Position.w * uSnap + .5) / uSnap * gl_Position.w;');
  };
  return m;
}
const MC = {};
function mat(color, map, o = {}) {
  const k = color + '|' + (map ? map.uuid : '') + '|' + JSON.stringify(o);
  if (MC[k]) return MC[k];
  const M = o.basic ? T.MeshBasicMaterial : T.MeshLambertMaterial;
  const m = new M(Object.assign({ color, map: map || null }, o.basic ? {} : { emissive: o.emissive || 0x000000 }, o.transparent ? { transparent: true, opacity: o.opacity ?? 1, depthWrite: false } : {}, o.side ? { side: o.side } : {}));
  if (o.fog === false) m.fog = false;
  return (MC[k] = ps1(m));
}

/* ---------- процедурные текстуры (рисуются на canvas) ---------- */
function ctex(w, h, fn, rx = 1, ry = 1) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const x = c.getContext('2d'); fn(x, w, h);
  const t = new T.CanvasTexture(c);
  t.magFilter = T.NearestFilter; t.minFilter = T.NearestMipmapLinearFilter; t.generateMipmaps = true;   // вдали без ряби
  t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(rx, ry); t.colorSpace = T.SRGBColorSpace; t.cv = c; t.cx = x;
  return t;
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
  TX.noise = ctex(64, 48, (x, w, h) => { speckle(x, w, h, 1, 0); }, 1, 1);
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

/* ---------- геометрия: простые кирпичики ---------- */
const GB = {};
function box(w, h, d, m, x = 0, y = 0, z = 0, parent) {
  const k = w + ',' + h + ',' + d; const g = GB[k] || (GB[k] = new T.BoxGeometry(w, h, d));
  const me = new T.Mesh(g, m); me.position.set(x, y, z); if (parent) parent.add(me); return me;
}
function plane(w, h, m, parent) { const me = new T.Mesh(new T.PlaneGeometry(w, h), m); if (parent) parent.add(me); return me; }
function cyl(r1, r2, h, n, m, parent) { const me = new T.Mesh(new T.CylinderGeometry(r1, r2, h, n), m); if (parent) parent.add(me); return me; }
// коробка-комната: стены внутрь
function room(w, h, d, mats, parent) {
  const g = new T.BoxGeometry(w, h, d); const me = new T.Mesh(g, mats); me.scale.set(-1, 1, 1); me.position.y = h / 2; parent.add(me); return me;
}
// текст на табличке (динамический, например табло лифта)
function label(w, h, pw, ph, draw, parent, basic = true) {
  const t = ctex(pw, ph, draw); const m = new T.MeshBasicMaterial({ map: t }); ps1(m);
  const me = plane(w, h, m, parent); me.userData.tex = t; me.userData.draw = draw; return me;
}
function relabel(me, ...a) { const t = me.userData.tex; t.cx.clearRect(0, 0, t.cv.width, t.cv.height); me.userData.draw(t.cx, t.cv.width, t.cv.height, ...a); t.needsUpdate = true; }

window.__W3core = { T, RW, RH, rnd, lerp, clamp, ps1, mat, ctex, speckle, TX, box, plane, cyl, room, label, relabel, makeTextures, SNAP };
})();
