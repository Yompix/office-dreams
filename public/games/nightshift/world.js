/* «Ночная смена» — 3D-мир. Всё построено кодом: геометрия, текстуры, персонажи, свет. Никаких готовых картинок.
   Стиль «ночная плёнка»: низкое разрешение, дрожащие вершины как на старых приставках, ступенчатое сглаживание цвета (дизеринг),
   натриевый жёлтый свет фонарей против холодного зелёного света мониторов.
   Снаружи: World.draw(g, shotId, o) рисует кадр на 2D-холст движка; o = { cx, cy, z, t, T, sx, sy, beat, talk: { who, v } }. */
(() => {
'use strict';
const tr = window.tr || (s => s);
const T = window.THREE;
// свет в шейдерах. three.js разворачивает цикл по точечным лампам: в каждом шейдере «Кино» было 12 копий расчёта света (77 КБ кода).
// Слабая видеокарта или программный SwiftShader собирали такую программу до 20 с, сторож процесса GPU его убивал — контекст терялся,
// и у всех программ в очереди «Shader Error … VALIDATE_STATUS false» с пустым логом (ПК-версия, 25 окт). Теперь развёрнуты только лампы
// с тенью (массив карт теней индексируется константой — как в three.js), остальные — обычный цикл, и лампа, до которой свет не достаёт
// (или выключенная), пропускается. Картинка та же; сборка программы на четверть дешевле, а на пикселях вне ламп меньше работы.
// Выборки теней не внутри if: на D3D (ANGLE) выборка текстуры в ветвлении — риск, как и в three.js — через «?:»
{ const a = T.ShaderChunk.lights_fragment_begin, i0 = a.indexOf('#if ( NUM_POINT_LIGHTS > 0 ) && defined( RE_Direct )'), i1 = a.indexOf('#if ( NUM_SPOT_LIGHTS > 0 ) && defined( RE_Direct )');
  if (i0 >= 0 && i1 > i0 && a.includes('pointLightShadow.shadowCameraFar ) : 1.0;')) T.ShaderChunk.lights_fragment_begin = a.slice(0, i0) + `#if ( NUM_POINT_LIGHTS > 0 ) && defined( RE_Direct )
	PointLight pointLight;
	#if defined( USE_SHADOWMAP ) && NUM_POINT_LIGHT_SHADOWS > 0
	PointLightShadow pointLightShadow;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_POINT_LIGHT_SHADOWS; i ++ ) {
		pointLight = pointLights[ i ];
		getPointLightInfo( pointLight, geometryPosition, directLight );
		pointLightShadow = pointLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getPointShadow( pointShadowMap[ i ], pointLightShadow.shadowMapSize, pointLightShadow.shadowBias, pointLightShadow.shadowRadius, vPointShadowCoord[ i ], pointLightShadow.shadowCameraNear, pointLightShadow.shadowCameraFar ) : 1.0;
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
	for ( int i = NUM_POINT_LIGHT_SHADOWS; i < NUM_POINT_LIGHTS; i ++ ) {
	#else
	for ( int i = 0; i < NUM_POINT_LIGHTS; i ++ ) {
	#endif
		pointLight = pointLights[ i ];
		getPointLightInfo( pointLight, geometryPosition, directLight );
		if ( directLight.visible ) RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
#endif
` + a.slice(i1).replace('#endif\n\t\tRE_Direct( directLight,', '#endif\n\t\tif ( directLight.visible ) RE_Direct( directLight,'); }   // прожектор (фонарь) вне конуса — тоже без расчёта
// Чёрный круг на настоящих видеокартах (Steam Deck, ноутбуки; 25 окт): кадр «Кино» и карта отражений — в half float (максимум 65504).
// Точечный блик (глаз, мокрый пол, металл под лампой вплотную) давал там бесконечность или NaN; свечение размывало такую точку
// в чёрный диск, а в отражениях — чёрные пятна (программная графика переполнение не показывает). Каждый материал на выходе:
// NaN → 0, яркость 0…256 (ACES насыщается к ~16, на вид без разницы). __VERSION__ — isnan есть только в WebGL 2.
T.ShaderChunk.dithering_fragment += `
#if __VERSION__ >= 300
	if ( isnan( gl_FragColor.r + gl_FragColor.g + gl_FragColor.b ) ) gl_FragColor.rgb = vec3( 0. );
#endif
	gl_FragColor.rgb = clamp( gl_FragColor.rgb, 0., 256. );`;
// внутреннее разрешение кадра. Два вида графики (меню «Графика», хранится в ns1_gfx, ?gfx=retro|cine — вручную):
//  «Ретро» — низкое разрешение, дрожь вершин, ступени цвета (запасной вид: слабый телефон или выбор игрока); на ПК вдвое чётче (?hd=0/1);
//  «Кино» — полное разрешение, мягкие тени, блики и отражения, объёмный свет, пыль в лучах, глубина резкости (по умолчанию везде;
//   на телефоне разрешение 75 % и тени попроще, а если и так медленно — движок сам переключает на «Ретро», см. perfTick).
// Ориентация меняется на лету (World.setLand): 9:16 или 16:9; дрожь вершин, строки и рябь VHS считаются по базовой сетке.
const FINE = matchMedia('(pointer: fine)').matches || !!window.nsDesktop;   // ПК-версия — всегда графика ПК (на Steam Deck главный «указатель» — сенсорный экран)
const HD = (() => { const q = /[?&]hd=([01])/.exec(location.search); if (q) return q[1] === '1' ? 2 : 1;
  return FINE && innerWidth > innerHeight ? 2 : 1; })();
// Видеокарта по имени (WEBGL_debug_renderer_info) — графика по железу, пока игрок сам её не выбрал (первый запуск, ПК-версия и браузер):
//  0 — программная (SwiftShader, llvmpipe: ПК без драйвера, виртуалка) → «Ретро»: «Кино» шло бы 1–2 кадра в секунду, а сборка его шейдеров роняла процесс GPU;
//  1 — слабая встроенная (Intel HD/UHD, Radeon R2–R5, старые Mali/Adreno/PowerVR; или нет WebGL 2) → на ПК «Ретро»;
//  2 — средняя (Steam Deck, Intel Iris Xe, Radeon Vega/680M/780M, GTX 750–960, MX) → «Кино» с 85 % разрешения;
//  3 — остальные (GTX 1050 и новее, Radeon RX 5500 и старше, Arc A3/A5) → «Кино» 100 %;
//  4 — мощные (RTX, Radeon RX 5700/6000/7000+, Arc A7, Apple M Pro/Max) → на ПК «Ультра»: родное разрешение экрана до 4K, тени 1024, отражения
//  в половину кадра, анизотропия 16 (вместо DLSS — его в WebGL нет). Дальше perfTick подстраивает разрешение по кадрам и на совсем
//  медленной отрисовке сам переходит на «Ретро». Имя видеокарты — внизу экрана настроек (для отчётов об ошибках).
const GPU = (() => {
  try { const c = document.createElement('canvas'), g2 = c.getContext('webgl2'), gl = g2 || c.getContext('webgl'); if (!gl) return { name: '', tier: 0 };
    const e = gl.getExtension('WEBGL_debug_renderer_info'), r = String(gl.getParameter(e ? e.UNMASKED_RENDERER_WEBGL : gl.RENDERER) || '');
    const lc = gl.getExtension('WEBGL_lose_context'); if (lc) lc.loseContext();
    let tier = 3;
    if (/swiftshader|llvmpipe|softpipe|software|basic render/i.test(r)) tier = 0;
    else if (/intel\(r\) (hd|uhd)|intel.*\b(hd|uhd) graphics|\bgma\b|mali-(4|t[6-8])|adreno.*\b[2-5]\d\d\b|radeon r[2-5]\b|powervr/i.test(r)) tier = 1;
    else if (/vangogh|custom gpu 0405|iris|radeon.*(vega|\(tm\) graphics|radeon graphics|6[1-8]0m|7[4-8]0m)|gtx (7[5-9]0|9[0-8]0)|\bmx ?[1-5]\d0|adreno|mali|apple gpu/i.test(r)) tier = 2;
    else if (/\brtx\b|rx ?(5[67]00|[6-9]\d{3})\b|arc\(tm\) a7|apple m\d (pro|max|ultra)/i.test(r)) tier = 4;
    return { name: r.replace(/^ANGLE \((.*)\)$/, '$1').slice(0, 90), tier: g2 ? tier : Math.min(tier, 1) };
  } catch (e) { return { name: '', tier: 2 }; }
})();
const GFX = (() => { const q = /[?&]gfx=(retro|cine|ultra)/.exec(location.search); if (q) return q[1];
  try { const v = JSON.parse(localStorage.getItem('ns1_gfx')); if (v === 'retro' || v === 'cine' || (v === 'ultra' && FINE)) return v; } catch {}
  return GPU.tier === 0 ? 'retro' : FINE && GPU.tier === 4 ? 'ultra' : 'cine'; })();   // «Ретро» сам — только без видеокарты (программная графика); слабым «Кино» сбавит авто-качество (владелец: «Кино и Ультра стали хуже»)
const CINE = GFX === 'cine' || GFX === 'ultra', ULTRA = GFX === 'ultra';
const RS = (() => { const q = /[?&]rs=([\d.]+)/.exec(location.search); if (q) return Math.max(.2, Math.min(2, +q[1]));
  try { const v = +JSON.parse(localStorage.getItem('ns1_res')); if (v > 0) return Math.max(.5, Math.min(ULTRA ? 2 : 1, v)); } catch {} return 0; })();   // настройка «Разрешение»: 0 — авто (подстройка по кадрам); 150–200 % — суперсэмплинг в «Ультра»   // ?rs=0.4 — доля разрешения «Кино» вручную и без подстройки (быстрые проверки кадров)
const V = { land: false, BW: 270, BH: 480, RW: 270 * HD, RH: 480 * HD, HD, cine: CINE, rs: RS || (FINE ? 1 : .75), rsPin: !!RS };   // rs — доля разрешения «Кино» (телефон начинает с 75 %, средняя видеокарта ПК — с 85 %, perfTick подстраивает)
let R = null, RT = null, post = null;          // рендерер, буфер кадра, проход постобработки
const rnd = (() => { let s = 1337; return () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; })();
const lerp = (a, b, k) => a + (b - a) * k, clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/* ---------- материалы: дрожание вершин (PS1) ---------- */
const SNAP = { value: new T.Vector2(108, 192) };
// длинная сторона настоящего экрана в пикселях: «Кино» не рисует больше, чем экран покажет (Steam Deck 1280×800 — 1280, а не 1920: вдвое меньше точек)
const scrL = () => { try { return Math.max(960, Math.round(Math.max(screen.width, screen.height) * (devicePixelRatio || 1))); } catch { return 1920; } };
function setLand(land) {
  V.land = !!land; V.BW = land ? 480 : 270; V.BH = land ? 270 : 480;
  if (CINE) { const L = Math.min(5120, Math.min(ULTRA ? 3840 : FINE ? 1920 : 1280, scrL()) * (V.rs || 1)) | 0, S = L * 9 / 16 | 0; V.RW = land ? L : S; V.RH = land ? S : L; }   // «Ультра» — родное разрешение экрана (до 4K)
  else { V.RW = V.BW * HD; V.RH = V.BH * HD; }
  SNAP.value.set(1e5, 1e5);   // вершины не дрожат ни в «Кино», ни в «Ретро» (24 окт: дрожь вершин PS1 владелец видел как «текстуры мигают/колбасит»)
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
  if (map && map.bumpTex) { r.bumpMap = map.bumpTex; r.bumpScale = o.bump ?? (k === 'marble' || k === 'lino' || k === 'tileSide' ? 1.6 : k === 'ceil' ? 1 : 3); }   // потолок: при 2.5 дырочки плитки издали сливались в «мятую фольгу»
  if (map && map.roughTex && o.rough == null) { r.roughnessMap = map.roughTex; r.roughness = 1; }   // шероховатость гуляет: потёртости на мраморе, лужи на асфальте
  return r;
}
// материал для людей и вещей с особыми шейдерами: в «Кино» — с бликами, в «Ретро» — матовый
function litMat(p, rough = .7, metal = 0) { return CINE ? new T.MeshStandardMaterial(Object.assign({ roughness: rough, metalness: metal, envMapIntensity: .5 }, p)) : new T.MeshLambertMaterial(p); }
// наклейка поверх другой поверхности (плинтус-панель на стене, табличка, светильник на потолке): чуть «ближе» по глубине,
// иначе на телефоне две плоскости дерутся за пиксель и рисунок мигает рваными пятнами (особенно с дрожью вершин PS1)
const DECAL = { polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -4 };
// источник света в кадре (трубка, лампа, плафон): в HDR-буфере «Кино» он в k раз ярче белого — свечение (bloom) берётся только от ламп,
// а не от белой рубашки или стены рядом (раньше вокруг светлой одежды был молочный «нимб»). Цвет по-прежнему меняют setHex (вкл/выкл)
function glow(m, k = 3) {
  if (!CINE) return m; const prev = m.onBeforeCompile, ck = m.customProgramCacheKey;
  m.onBeforeCompile = (sh, r) => { if (prev) prev(sh, r); sh.fragmentShader = sh.fragmentShader.replace('#include <opaque_fragment>', '#include <opaque_fragment>\n  gl_FragColor.rgb *= ' + k.toFixed(2) + ';'); };
  m.customProgramCacheKey = () => ck.call(m) + '|glow' + k; return m;
}
function mat(color, map, o = {}) {
  const k = color + '|' + (map ? map.uuid : '') + '|' + JSON.stringify(o);
  if (MC[k]) return MC[k];
  const M = o.basic ? T.MeshBasicMaterial : CINE ? T.MeshStandardMaterial : T.MeshLambertMaterial;
  const m = new M(Object.assign({ color, map: map || null }, o.basic ? {} : { emissive: o.emissive || 0x000000 }, !o.basic && CINE ? pbr(map, o) : {},
    o.transparent ? { transparent: true, opacity: o.opacity ?? 1, depthWrite: false } : {}, o.side ? { side: o.side } : {}, o.decal ? DECAL : {}));
  if (CINE && !o.basic && !o.transparent) { m.castShadow = true; }
  if (o.fog === false) m.fog = false;
  ps1(m); if (o.glow) glow(m, o.glow);
  return (MC[k] = m);
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
  if (up > 1) { t.magFilter = T.LinearFilter; t.minFilter = T.LinearMipmapLinearFilter; t.anisotropy = ULTRA ? 16 : 8; }
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
  // фасад бизнес-центра: этажи по 3 м (на плоскости 36 м — двенадцать, тринадцатого нет), окна в рамах с импостом, тёмное стекло отражает небо;
  // кое-где горит свет — тёплый или холодный, где-то жалюзи, внизу силуэты перегородок. Горящие окна светятся сами (facadeEm — карта свечения)
  const facLay = litP => { const L = []; for (let r = 0; r < 6; r++) for (let c = 0; c < 8; c++) L.push({ r, c, lit: rnd() < litP, warm: rnd() < .62, blind: rnd() < .4, k: .75 + rnd() * .5, v: 22 + rnd() * 10 }); return L; };
  const facDraw = (x, L, em) => { const cw = 16, ch = 256 / 6;
    for (const { r, c, lit, warm, blind, k, v } of L) {
      const X = c * cw, Y = r * ch, wx = X + 2.2, wy = Y + ch * .2, ww = cw - 4.4, wh = ch * .64;
      if (!em) { x.fillStyle = '#121519'; x.fillRect(X, Y, cw, ch); x.fillStyle = 'rgba(255,255,255,.05)'; x.fillRect(X, Y + ch * .16, cw, .7); }   // междуэтажный пояс и его кромка
      if (lit) { const g = x.createLinearGradient(0, wy, 0, wy + wh), a = (warm ? [217, 180, 106] : [159, 196, 208]).map(q => q * (em ? .8 : 1));
        g.addColorStop(0, `rgb(${a.map(q => q * k | 0)})`); g.addColorStop(.75, `rgb(${a.map(q => q * k * .62 | 0)})`); g.addColorStop(1, `rgb(${a.map(q => q * k * .25 | 0)})`);
        x.fillStyle = g; x.fillRect(wx, wy, ww, wh); x.fillStyle = 'rgba(0,0,0,.6)'; x.fillRect(wx, wy + wh * .78, ww, wh * .22);   // перегородки и столы
        if (blind) { x.fillStyle = 'rgba(0,0,0,.35)'; for (let i = 0; i < 9; i++) x.fillRect(wx, wy + i * wh / 9, ww, .55); } }
      else if (!em) { const g = x.createLinearGradient(0, wy, 0, wy + wh);
        g.addColorStop(0, `rgb(${v + 6 | 0},${v + 12 | 0},${v + 22 | 0})`); g.addColorStop(1, `rgb(${v * .55 | 0},${v * .62 | 0},${v * .75 | 0})`); x.fillStyle = g; x.fillRect(wx, wy, ww, wh); }
      else continue;
      x.fillStyle = em ? '#000' : '#0b0d10'; x.fillRect(wx + ww / 2 - .4, wy, .8, wh);   // импост
      if (!em) { x.strokeStyle = 'rgba(70,76,84,.55)'; x.lineWidth = .6; x.strokeRect(wx, wy, ww, wh); }
    } };
  const LAY = facLay(.12);
  TX.facade = ctex(128, 256, x => facDraw(x, LAY, false), 2, 2);
  TX.facadeEm = ctex(128, 256, x => { x.fillStyle = '#000'; x.fillRect(0, 0, 128, 256); facDraw(x, LAY, true); }, 2, 2);
  TX.f7lit = null;
  TX.carpet = ctex(32, 32, (x, w, h) => { x.fillStyle = '#3a2224'; x.fillRect(0, 0, w, h); speckle(x, w, h, 300, .08); x.strokeStyle = 'rgba(200,150,90,.18)'; x.strokeRect(4.5, 4.5, 23, 23); }, 2, 14);
  TX.door = ctex(32, 64, (x, w, h) => { x.fillStyle = '#5a4030'; x.fillRect(0, 0, w, h); x.strokeStyle = 'rgba(0,0,0,.4)'; x.strokeRect(4.5, 4.5, w - 9, 24); x.strokeRect(4.5, 34.5, w - 9, 25); speckle(x, w, h, 120, .06); x.fillStyle = '#c9b27a'; x.fillRect(w - 7, 30, 3, 4); });
  TX.facadeDark = ctex(128, 256, x => facDraw(x, facLay(0), false), 2, 2);   // тот же фасад, все окна тёмные (здание ночью без света)
  TX.hand = ctex(64, 64, (x, w, h) => {   // след ладони: мокрый, грязный (подвал — из круга в коридор)
    x.clearRect(0, 0, w, h); x.fillStyle = 'rgba(255,255,255,.9)'; x.filter = 'blur(1px)';
    x.beginPath(); x.ellipse(32, 40, 13, 15, 0, 0, 7); x.fill();
    [[18, 20, 4, 11, -.35], [26, 14, 4, 13, -.12], [34, 13, 4, 13, .05], [42, 17, 3.6, 11, .25], [47, 34, 4, 9, .9]].forEach(([cx, cy, rx, ry, a]) => { x.beginPath(); x.ellipse(cx, cy, rx, ry, a, 0, 7); x.fill(); });
  });
  // серверная стойка: блоки с решёткой вентиляции и корзинами дисков, мелкие светодиоды (зелёные, реже жёлтые и синие) — светятся сами (rackEm)
  const RL = []; for (let r = 0; r < 16; r++) { const leds = []; for (let k = 0; k < 6; k++) if (rnd() < .55) leds.push([3.2 + k * 1.15 + (k > 2 ? 1.2 : 0), rnd() < .78 ? [60, 255, 122] : rnd() < .7 ? [255, 176, 60] : [80, 150, 255]]); RL.push({ leds, bays: rnd() < .6, k: .85 + rnd() * .3 }); }
  const rackDraw = (x, em) => { x.fillStyle = em ? '#000' : '#121416'; x.fillRect(0, 0, 32, 64);
    RL.forEach(({ leds, bays, k }, r) => { const y = r * 4;
      if (!em) { x.fillStyle = `rgb(${30 * k | 0},${33 * k | 0},${38 * k | 0})`; x.fillRect(1.5, y + .5, 29, 3.2); x.fillStyle = 'rgba(0,0,0,.55)';
        for (let i = 0; i < 6; i++) x.fillRect(12 + i * 2.6, y + 1, 1.6, 2.2);   // решётка
        if (bays) { x.fillStyle = 'rgba(255,255,255,.07)'; for (let i = 0; i < 3; i++) x.fillRect(2.2 + i * 3.1, y + 2.3, 2.7, 1); }
        x.fillStyle = 'rgba(255,255,255,.08)'; x.fillRect(1.5, y + .5, 29, .3); }
      for (const [lx, c] of leds) { x.fillStyle = `rgba(${c},${em ? .25 : .15})`; x.beginPath(); x.arc(lx, y + 1.3, .7, 0, 7); x.fill();
        x.fillStyle = `rgb(${c})`; x.beginPath(); x.arc(lx, y + 1.3, UP > 1 ? .28 : .55, 0, 7); x.fill(); } }); };   // в «Ретро» (без увеличения) диод крупнее — иначе пропадает
  TX.rack = ctex(32, 64, x => rackDraw(x, false)); TX.rackEm = ctex(32, 64, x => rackDraw(x, true));
  TX.concrete = ctex(64, 64, (x, w, h) => { x.fillStyle = '#3a3c3a'; x.fillRect(0, 0, w, h); speckle(x, w, h, 900, .1); speckle(x, w, h, 600, .16, false); x.fillStyle = 'rgba(30,20,10,.25)'; for (let i = 0; i < 4; i++) x.fillRect(rnd() * w, rnd() * h, 1, 10 + rnd() * 20); }, 4, 2);
  TX.salt = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h);   // насыпанный круг соли: неровный валик (толще и тоньше), по краям россыпь мелких кристаллов
    for (let i = 0; i < 260; i++) { const a = i / 260 * 6.2832, r = 26 + Math.sin(a * 7 + 1) * .6 + Math.sin(a * 19) * .35, wd = 1.6 + .9 * Math.sin(a * 5 + 2) + rnd() * .6;
      x.fillStyle = `rgba(232,230,220,${.55 + rnd() * .3})`; x.beginPath(); x.arc(32 + Math.cos(a) * r, 32 + Math.sin(a) * r, wd, 0, 7); x.fill(); }
    for (let i = 0; i < 520; i++) { const a = rnd() * 6.2832, r = 26 + (rnd() - .5) * (rnd() < .8 ? 5 : 11); x.fillStyle = `rgba(245,244,236,${.5 + rnd() * .5})`;
      x.beginPath(); x.arc(32 + Math.cos(a) * r, 32 + Math.sin(a) * r, .25 + rnd() * .4, 0, 7); x.fill(); } });
  TX.glassRain = ctex(64, 64, (x, w, h) => { x.clearRect(0, 0, w, h); for (let i = 0; i < 70; i++) { const px = rnd() * w, py = rnd() * h, l = 2 + rnd() * 8; x.fillStyle = 'rgba(210,225,240,.55)'; x.fillRect(px, py, 1, l); x.fillStyle = 'rgba(255,255,255,.8)'; x.fillRect(px, py + l, 1, 1); } }, 10, 3);
  TX.sign = ctex(256, 48, (x, w, h) => { x.fillStyle = '#101214'; x.fillRect(0, 0, w, h); x.fillStyle = '#c9cdd2'; x.font = '700 28px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(tr('БЦ  «ГОРИЗОНТ»'), w / 2, h / 2 + 1); });
  TX.signOut = ctex(256, 48, (x, w, h) => { x.fillStyle = '#0a0b0c'; x.fillRect(0, 0, w, h); x.fillStyle = '#e8eef5'; x.shadowColor = '#9fd0ff'; x.shadowBlur = 8; x.font = '700 30px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(tr('ГОРИЗОНТ'), w / 2, h / 2 + 1); });
  TX.guardSign = ctex(64, 16, (x, w, h) => { x.fillStyle = '#7a1414'; x.fillRect(0, 0, w, h); x.fillStyle = '#fff'; x.font = '700 11px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(tr('ОХРАНА'), w / 2, h / 2 + 1); });
  TX.exit = ctex(32, 16, (x, w, h) => { x.fillStyle = '#0f6b2c'; x.fillRect(0, 0, w, h); x.fillStyle = '#d8ffe0'; x.font = '700 10px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(tr('ВЫХОД'), w / 2, h / 2 + 1, w - 3); });
  TX.poster = ctex(32, 48, (x, w, h) => { x.fillStyle = '#d8d2c0'; x.fillRect(0, 0, w, h); x.fillStyle = '#7a1414'; x.fillRect(0, 0, w, 10); x.fillStyle = '#222'; for (let i = 0; i < 8; i++) x.fillRect(4, 14 + i * 4, 10 + rnd() * 14, 1); x.fillStyle = '#333'; x.font = '700 6px Arial'; x.fillText(tr('ПРАВИЛА'), 4, 7); });
  TX.note = noteTex(false); TX.note2 = noteTex(true);
  TX.keys = null;
  TX.wood = ctex(32, 32, (x, w, h) => { x.fillStyle = '#4b3524'; x.fillRect(0, 0, w, h); for (let i = 0; i < 24; i++) { x.fillStyle = `rgba(${rnd() < .5 ? '0,0,0' : '255,210,160'},${.08 * rnd()})`; x.fillRect(0, rnd() * h, w, 1); } }, 2, 1);
  TX.sky = ctex(4, 64, (x, w, h) => { const gr = x.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#06080d'); gr.addColorStop(.7, '#121822'); gr.addColorStop(1, '#2a2620'); x.fillStyle = gr; x.fillRect(0, 0, w, h); });
  TX.skyCity = ctex(4, 64, (x, w, h) => { const gr = x.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#121824'); gr.addColorStop(.55, '#232a38'); gr.addColorStop(.85, '#3c3a3a'); gr.addColorStop(1, '#5a4a3c'); x.fillStyle = gr; x.fillRect(0, 0, w, h); });   // засветка города: здание — силуэтом на небе
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
function makeTextures2() {
  // грязь на стекле дверей (x −2..2 м, y 0.4..2.4 м): жирные пятна, отпечатки ладоней, мазок тряпкой. Альфа = сколько грязи
  TX.grime = ctex(512, 256, (x, w, h) => {
    x.clearRect(0, 0, w, h);
    for (let i = 0; i < 26; i++) soft(x, rnd() * w, h * (.25 + rnd() * .7), 8 + rnd() * 26, 4 + rnd() * 14, .1 + rnd() * .16);
    x.strokeStyle = 'rgba(255,255,255,.08)'; x.lineWidth = 9; for (let k = 0; k < 3; k++) { x.beginPath(); x.arc(w * (.3 + k * .2), h * .9, 60 + k * 10, -2.6, -.6); x.stroke(); }   // разводы от тряпки
    // у ручек — размытые жирные пятна от ладоней (чёткие отпечатки читались как «лапы»; настоящий отпечаток появляется по сюжету — uPrint)
    for (const [px, py] of [[.43, .62], [.47, .56], [.62, .6]]) soft(x, px * w, py * h, 16 + rnd() * 8, 10 + rnd() * 6, .07 + rnd() * .05);
  });
  TX.grime.magFilter = T.LinearFilter; TX.grime.minFilter = T.LinearMipmapLinearFilter; TX.grime.wrapS = TX.grime.wrapT = T.ClampToEdgeWrapping;
  // плёночные буквы снаружи: изнутри читаются зеркально — так сразу видно, что между нами стекло
  TX.logo = ctex(512, 96, (x, w, h) => {
    x.clearRect(0, 0, w, h); x.fillStyle = 'rgba(236,240,244,.95)'; x.textAlign = 'center'; x.textBaseline = 'middle';
    x.font = '700 54px Arial'; x.fillText(tr('ГОРИЗОНТ'), w / 2, 38); x.font = '700 20px Arial'; x.fillText(tr('Б И З Н Е С - Ц Е Н Т Р'), w / 2, 80);
  });
  TX.logo.wrapS = TX.logo.wrapT = T.ClampToEdgeWrapping;
  TX.sticker = ctex(64, 32, (x, w, h) => { x.clearRect(0, 0, w, h); x.fillStyle = 'rgba(240,240,236,.92)'; x.fillRect(2, 4, w - 4, h - 8); x.fillStyle = '#9a1818'; x.font = '700 12px Arial'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText(tr('ОТ СЕБЯ'), w / 2, h / 2 + 1); });
  TX.sticker.wrapS = TX.sticker.wrapT = T.ClampToEdgeWrapping;
  TX.fogBreath = ctex(64, 32, (x, w, h) => { x.clearRect(0, 0, w, h); soft(x, w / 2, h / 2, 23, 11, .95, '235,240,245'); for (let i = 0; i < 90; i++) { const a = rnd() * 6.28, r = Math.sqrt(rnd()); x.fillStyle = `rgba(255,255,255,${.3 * rnd() * (1 - r)})`; x.fillRect(w / 2 + Math.cos(a) * r * 20, h / 2 + Math.sin(a) * r * 9, 1, 1); } });
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
    x.font = '700 6px Arial'; x.textAlign = 'center'; x.fillText(tr('ОСТОРОЖНО'), 16, 35); x.fillText(tr('МОКРЫЙ'), 16, 41); x.fillText(tr('ПОЛ'), 16, 47);
  });
  TX.clock = ctex(64, 64, (x, w, h) => {
    x.fillStyle = '#0d0d0d'; x.beginPath(); x.arc(32, 32, 32, 0, 7); x.fill(); x.fillStyle = '#e6e2d6'; x.beginPath(); x.arc(32, 32, 28, 0, 7); x.fill();
    x.fillStyle = '#1a1a1a'; for (let i = 0; i < 12; i++) { const a = i / 12 * Math.PI * 2; x.save(); x.translate(32, 32); x.rotate(a); x.fillRect(-1, -26, 2, i % 3 ? 4 : 7); x.restore(); }
    
  });
  TX.clock.wrapS = TX.clock.wrapT = T.ClampToEdgeWrapping;
  TX.badge = badgeTex(false); TX.badgeX = badgeTex(true);
  // «маска» фонарика (карта прожектора): горячее пятно в центре, мягкий спад, кольцо от отражателя, пыль и царапины на стекле, мягкий край —
  // луч настоящего фонаря, а не ровный круг сцены
  TX.torch = ctex(64, 64, (x, w, h) => { const d = x.createImageData(w, h);
    for (let py = 0; py < h; py++) for (let px = 0; px < w; px++) { const u = (px + .5) / w - .5, v = (py + .5) / h - .5, r = Math.hypot(u, v) * 2, a = Math.atan2(v, u);
      const hot = Math.exp(-r * r * 9) * .5, body = clamp(1 - r * r * .9, 0, 1) * .66, ring = Math.exp(-Math.pow((r - .62) / .07, 2)) * .24, dirt = 1 - .1 * (Math.sin(a * 7 + r * 13) * .5 + .5) * r;
      const k = (hot + body + ring) * dirt * (1 - sm(.78, .98, r)), i = (py * w + px) * 4;
      d.data[i] = 255 * clamp(k * 1.02, 0, 1); d.data[i + 1] = 255 * clamp(k, 0, 1); d.data[i + 2] = 255 * clamp(k * .9, 0, 1); d.data[i + 3] = 255; }
    x.putImageData(d, 0, 0); }, 1, 1, 'raw');
  TX.torch.wrapS = TX.torch.wrapT = T.ClampToEdgeWrapping; TX.torch.magFilter = T.LinearFilter; TX.torch.minFilter = T.LinearFilter; TX.torch.generateMipmaps = false; TX.torch.colorSpace = T.NoColorSpace;   // множитель света — линейный
}
// пропуск Фрэнка: фото (седой) и фамилия; вторая версия — лицо на фото исцарапано
function badgeTex(scratched) {
  return ctex(64, 48, (x, w, h) => {
    x.fillStyle = '#e8e6df'; x.fillRect(0, 0, w, h); x.fillStyle = '#1d3a7a'; x.fillRect(0, 0, w, 10);
    x.fillStyle = '#fff'; x.font = '700 7px Arial'; x.fillText(tr('ПРОПУСК'), 4, 8);
    x.fillStyle = '#7d8a96'; x.fillRect(4, 14, 20, 26);
    x.fillStyle = '#d8c0b0'; x.beginPath(); x.ellipse(14, 26, 6, 8, 0, 0, 7); x.fill();
    x.fillStyle = '#c9c9c4'; x.fillRect(8, 16, 12, 4); x.fillRect(7, 18, 2, 6); x.fillRect(19, 18, 2, 6);   // седые волосы
    x.fillStyle = '#3a2a24'; x.fillRect(10, 25, 3, 1); x.fillRect(15, 25, 3, 1); x.fillStyle = '#8a6a5a'; x.fillRect(11, 31, 6, 1);
    x.fillStyle = '#1b1b1b'; x.font = '700 7px Arial'; x.fillText(tr('ДОЙЛ'), 27, 21); x.font = '6px Arial'; x.fillText(tr('Фрэнк'), 27, 29); x.fillText(tr('охрана'), 27, 37);
    if (scratched) { x.strokeStyle = 'rgba(20,20,20,.9)'; x.lineWidth = 1.5; for (let i = 0; i < 9; i++) { x.beginPath(); x.moveTo(4 + rnd() * 20, 14 + rnd() * 6); x.lineTo(4 + rnd() * 20, 34 + rnd() * 6); x.stroke(); } }
  });
}
// журнал ночных смен (разворот): слева — двадцать лет одинаковых записей, справа — последние недели Фрэнка.
// Вариант 2 — с припиской Итана другими чернилами (концовка «Новая запись»)
const LOG_L = ['03.10  Обход. Лампа на 4-м мигает.', '05.10  Обход. Кошка у баков.', '08.10  Обход. Без происшествий.', '10.10  Обход. Без происшествий.',
  '12.10  Лифт ночью ездит сам.', '       Техник: «датчик».'];
// k: 0 — ровный почерк, 1 — дрожащий, 2 — с нажимом (бумага прорвана)
const LOG_R = [['15.10  Гаса зовут с 7-го. По имени.', 0], ['       Голосом Марты.', 0], ['       Марта три года как умерла.', 0],
  ['18.10  На пульте CAM 13.', 0], ['       Такой камеры нет. Не смотри.', 0],
  ['20.10  НЕ ОТЗЫВАЙСЯ НА ИМЯ.', 2], ['       НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ.', 2], ['       САМ К НЕМУ НЕ ХОДИ —', 2], ['       НИ НА СВЕТ, НИ В ЛИФТ.', 2],
  ['21.10  Мама позвала. Я ответил.', 1], ['       Соль держит. Иду вниз.', 1]];
const LOG_E = ['28.10  ФРЭНК БЫЛ ЖИВ.', '       Я НЕ СПУСТИЛСЯ.  — И.'];
function noteTex(v2) {
  return ctex(384, 256, (x, w, h) => {
    x.fillStyle = '#e6dfca'; x.fillRect(0, 0, w, h);
    for (let i = 0; i < 700; i++) { x.fillStyle = `rgba(120,100,60,${.07 * rnd()})`; x.fillRect(rnd() * w, rnd() * h, 2, 2); }
    const sh = x.createLinearGradient(w / 2 - 14, 0, w / 2 + 14, 0); sh.addColorStop(0, 'rgba(0,0,0,0)'); sh.addColorStop(.5, 'rgba(60,45,25,.35)'); sh.addColorStop(1, 'rgba(0,0,0,0)');
    x.fillStyle = sh; x.fillRect(w / 2 - 14, 0, 28, h);   // корешок
    x.strokeStyle = 'rgba(80,110,160,.28)'; for (let y = 30; y < h; y += 17) { x.beginPath(); x.moveTo(8, y + .5); x.lineTo(w / 2 - 10, y + .5); x.moveTo(w / 2 + 10, y + .5); x.lineTo(w - 8, y + .5); x.stroke(); }
    x.strokeStyle = 'rgba(190,60,60,.35)'; x.beginPath(); x.moveTo(52, 0); x.lineTo(52, h); x.moveTo(w / 2 + 44, 0); x.lineTo(w / 2 + 44, h); x.stroke();
    x.fillStyle = '#23262e'; x.font = '700 11px Georgia, "Times New Roman", serif'; x.fillText(tr('ЖУРНАЛ НОЧНЫХ СМЕН'), 12, 18);
    const pen = (sz, b) => `italic ${b ? 700 : 500} ${sz}px Georgia, "Times New Roman", serif`;
    x.fillStyle = '#1c2a6a'; x.font = pen(11);
    LOG_L.forEach((t, i) => x.fillText(tr(t), 10, 27 + 17 * (i + 1) - 3));
    LOG_R.forEach(([t, k], i) => {   // k: 1 — дрожащий почерк, 2 — с нажимом (бумага прорвана)
      x.save(); x.translate(w / 2 + 8, 27 + 17 * (i + 1) - 3);
      if (k === 1) x.rotate((rnd() - .5) * .05);
      x.font = pen(k === 2 ? 12 : 11, k === 2); x.fillStyle = k === 2 ? '#101a50' : '#1c2a6a'; x.fillText(tr(t), 0, 0);
      if (k === 2) { x.fillText(tr(t), .6, .3);   // с нажимом: ручка прорвала бумагу — рваная щель под строкой
        if (/ИМЯ/.test(t)) { x.fillStyle = 'rgba(25,18,10,.75)'; x.beginPath(); x.moveTo(36, 3); for (let s = 0; s <= 24; s++) x.lineTo(36 + s * 5, 3.2 + Math.sin(s * 2.7) * 1.1 + (s % 3 ? .4 : -.5));   // по русскому ключу: в переводе слова «имя» может не быть (ES «NO RESPONDAS»)
          for (let s = 24; s >= 0; s--) x.lineTo(36 + s * 5, 4.4 + Math.sin(s * 1.9) * .8); x.closePath(); x.fill(); } }
      x.restore();
    });
    if (v2) { x.fillStyle = '#123d1a'; x.font = pen(11, true); LOG_E.forEach((t, i) => x.fillText(tr(t), w / 2 + 8, 27 + 17 * (LOG_R.length + i + 1) - 3)); }
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
function hiTex(w, h, rx, ry, fn) {   // fn(u, v, px, py) → [r, g, b, высота 0..1, шероховатость 0..1 — необязательно]
  const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d'), d = x.createImageData(w, h);
  const hc = document.createElement('canvas'); hc.width = w; hc.height = h; const hx2 = hc.getContext('2d'), hd = hx2.createImageData(w, h);
  let rc = null, rd = null;
  for (let py = 0; py < h; py++) for (let px = 0; px < w; px++) {
    const r = fn(px / w, py / h, px, py), i = (py * w + px) * 4;
    d.data[i] = r[0]; d.data[i + 1] = r[1]; d.data[i + 2] = r[2]; d.data[i + 3] = 255; const hv = clamp(r[3], 0, 1) * 255; hd.data[i] = hd.data[i + 1] = hd.data[i + 2] = hv; hd.data[i + 3] = 255;
    if (r.length > 4) { if (!rc) { rc = document.createElement('canvas'); rc.width = w; rc.height = h; rd = rc.getContext('2d').createImageData(w, h); }
      rd.data[i] = rd.data[i + 1] = rd.data[i + 2] = clamp(r[4], 0, 1) * 255; rd.data[i + 3] = 255; }
  }
  x.putImageData(d, 0, 0); hx2.putImageData(hd, 0, 0); if (rc) rc.getContext('2d').putImageData(rd, 0, 0);
  const mk = (cv, srgb) => { const t = new T.CanvasTexture(cv); t.magFilter = T.LinearFilter; t.minFilter = T.LinearMipmapLinearFilter; t.anisotropy = ULTRA ? 16 : 8; t.generateMipmaps = true;
    t.wrapS = t.wrapT = T.RepeatWrapping; t.repeat.set(rx, ry); if (srgb) t.colorSpace = T.SRGBColorSpace; return t; };
  const t = mk(c, true); t.cv = c; t.cx = x; t.lw = w; t.lh = h; t.bumpTex = mk(hc, false); if (rc) t.roughTex = mk(rc, false); return t;
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
  TX.marble = hiTex(512, 512, 8, 6, (u, v) => {   // плиты двух тонов (не «шахматы»: разница мягче), прожилки; полировка с потёртостями — шероховатость пятнами
    const q = (u < .5) !== (v < .5), base = q ? [63, 66, 71] : [81, 84, 88];
    const w = fbm(u + .35 * fbm(u, v, 2, 2, 3, 5), v + .2 * fbm(u, v, 3, 3, 2, 6), 2, 2, 5, q ? 11 : 12), r = Math.pow(1 - Math.abs(2 * w - 1), 24) * (.35 + .65 * sm(.4, .65, fbm(u, v, 2, 2, 2, 14)));
    let c = mul(base, .9 + .16 * fbm(u, v, 6, 6, 4, 3)); c = c.map(x => x + r * 55);
    const gu = Math.min(Math.abs(u - .5), u, 1 - u), gv = Math.min(Math.abs(v - .5), v, 1 - v), gr = Math.min(gu, gv);
    const scuff = sm(.55, .8, fbm(u, v, 3, 3, 4, 15)) * .5 + sm(.7, .9, vn2(u, v, 40, 6, 16)) * .25;
    if (gr < .004) c = [22, 22, 24];
    return [...c, gr < .004 ? 0 : .8, gr < .004 ? .7 : .2 + scuff * .45 + .06 * vn2(u, v, 64, 64, 17)];
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
  TX.asphalt = hiTex(512, 512, 10, 10, (u, v) => {   // мокрый асфальт: в лужах почти зеркало — в них горят фонарь и вход
    const ag = vn2(u, v, 256, 256, 71), n = fbm(u, v, 3, 3, 4, 72), wet = sm(.55, .7, fbm(u, v, 2, 2, 3, 73));
    let c = mul([27, 29, 32], .8 + .5 * ag * ag + .2 * n); c = c.map((x, i) => x * (1 - .35 * wet) + wet * [12, 14, 18][i]);
    return [...c, .3 + .6 * ag * ag * (1 - wet), Math.max(.1, .8 - .2 * ag - .62 * wet)];
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
  TX.tileSide = hiTex(256, 256, 20, 3, (u, v) => {   // плитка тротуара под дождём: мокрые пятна темнее и блестят, в швах вода
    let c = mul([94, 96, 94], .88 + .2 * fbm(u, v, 8, 8, 4, 101)); const gr = Math.min(Math.abs(u - .5), u, 1 - u, Math.abs(v - .5), v, 1 - v);
    const wet = sm(.42, .62, fbm(u, v, 2, 2, 3, 103)); c = mul(c, 1 - .3 * wet);
    if (gr < .01) c = [40, 40, 40]; return [...c, gr < .01 ? 0 : .7, gr < .01 ? .3 : Math.max(.14, .6 - .42 * wet)];
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
function label(w, h, pw, ph, draw, parent, basic = true) {   // basic — светится сам (табло, экран); false — освещается лампами (таблички, надписи краской)
  const t = ctex(pw, ph, draw); const m = basic ? new T.MeshBasicMaterial(Object.assign({ map: t }, DECAL)) : litMat(Object.assign({ map: t }, DECAL), .7); ps1(m);
  const me = plane(w, h, m, parent); me.userData.tex = t; me.userData.draw = draw; return me;
}
function relabel(me, ...a) { const t = me.userData.tex; t.cx.clearRect(0, 0, t.lw, t.lh); me.userData.draw(t.cx, t.lw, t.lh, ...a); t.needsUpdate = true; }

window.__W3core = { T, V, GPU, setLand, CINE, ULTRA, FINE, litMat, HD, rnd, lerp, clamp, ps1, glow, mat, ctex, speckle, TX, box, plane, cyl, room, label, relabel, makeTextures, SNAP };
})();
