/* Персонажи v2: настоящие 3D-люди со скелетом и анимациями (модели Quaternius, лицензия CC0, папка models/).
   Одежду рисуем шейдером прямо по телу (рубашка, брюки, плащ, ботинки по зонам тела), лицо живое: рот открывается по голосу,
   глаза моргают, у Жильца пустые глаза и улыбка до ушей. Анимации: стоять, говорить, сидеть, идти, пятиться, зомби-стойка;
   руки для «стучит», «отдаёт ключи», «держит листок», «тянется» доворачиваются кодом поверх анимации.
   Снаружи тот же интерфейс, что у старых персонажей (chars.js): { root, pose, look, talk, faceSet, update(t, dt) }.
   Если модели не загрузились, остаются старые персонажи из блоков. */
(() => {
'use strict';
const { T, rnd, lerp, clamp, mat, box, cyl, SNAP } = window.__W3core;
const FILES = ['male', 'female', 'Hair_Beard', 'Hair_Long', 'Hair_SimpleParted', 'Hair_Buzzed'];
const G = {}, CLIP = {};

// XHR, а не fetch: так модели читаются и в Android-приложении (file://)
const get = url => new Promise((ok, no) => { const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = 'arraybuffer'; x.onload = () => (x.status === 200 || x.status === 0) && x.response ? ok(x.response) : no(); x.onerror = no; x.send(); });
async function load(onFile) {
  if (!window.THREE_GLTFLoader || !window.THREE_SkeletonUtils) return false;
  const L = new window.THREE_GLTFLoader();
  try {
    // каждый файл — со второй попыткой: один сбой сети не должен откатывать всех людей к блочным
    const url = n => 'models/' + n + '.glb?v=1';
    await Promise.all(FILES.map(n => get(url(n)).catch(() => get(url(n))).then(b => new Promise((ok, no) => L.parse(b, '', g => { G[n] = g; if (onFile) onFile(); ok(); }, no)))));
  } catch (e) { console.warn('models', e); return false; }
  for (const c of G.male.animations) {
    // только повороты костей (+ высота таза): так одна анимация подходит и мужчине, и женщине, и вытянутому Жильцу
    c.tracks = c.tracks.filter(t => t.name.endsWith('.quaternion') || t.name === 'pelvis.position');
    CLIP[c.name] = c;
  }
  window.__W3chars.cast = cast; window.__W3chars.v2 = true;
  return true;
}

/* ---------- материал тела: одежда по зонам + лицо ---------- */
// зоны считаются по точке тела в позе «Т» (метры, рост мужской модели 1.81): так одежда не съезжает при движении
const BODY_FS = `
uniform vec3 uShirt, uPants, uShoes, uSkin, uCoat, uTrim; uniform sampler2D uDecal; uniform float uSkirt, uKind, uFlat, uS, uCoatY, uTalk, uBlink, uSmile, uNaked, uBadge, uFem, uWet, uGrey, uGore, uScream, uSocket;
uniform vec2 uMouth, uEyeL, uEyeR;
varying vec3 vB;
float h3(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float vn(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
  return mix(mix(mix(h3(i), h3(i + vec3(1., 0., 0.)), f.x), mix(h3(i + vec3(0., 1., 0.)), h3(i + vec3(1., 1., 0.)), f.x), f.y),
             mix(mix(h3(i + vec3(0., 0., 1.)), h3(i + vec3(1., 0., 1.)), f.x), mix(h3(i + vec3(0., 1., 1.)), h3(i + vec3(1., 1., 1.)), f.x), f.y), f.z); }
float ln(float d, float w) { return 1. - smoothstep(w * .5, w, abs(d)); }   // тонкая линия (шов, кант)
float rct(vec2 p, vec2 a, vec2 b) { return step(a.x, p.x) * step(p.x, b.x) * step(a.y, p.y) * step(p.y, b.y); }
float edge(vec2 p, vec2 a, vec2 b, float w) { return rct(p, a, b) * (1. - rct(p, a + w, b - w)); }
// одежда по точке тела в позе «Т» (метры, мужской рост 1.81): 0 — кожа, иначе цвет ткани (w = 1).
// Ткань «нарисована» как в играх PS1: складки, швы, карманы, тени в сгибах — свет от тела (мышцы) сюда не попадает.
// uKind: 0 — форма охранника (рубашка, ремень, брюки), 1 — дождевик, 2 — рабочая куртка уборщика
vec4 cloth(vec3 p, float lum) {
  float ax = abs(p.x);
  if (uNaked > .5) return vec4(0.);
  float co = uKind > .5 && uKind < 1.5 ? 1. : 0., nkTop = co > .5 ? 1.55 : 1.535, vS = co > .5 ? 9. : 1.405;
  bool hand = ax > .665, foot = p.y < .12;
  bool neck = p.y > nkTop || (p.z > .02 && p.y > vS && ax < (p.y - vS) * (co > .5 ? .75 : .42)) || (p.y > 1.565 && ax < .07);
  if (hand) return vec4(0.);
  if (neck) return vec4(vec3(.035, .035, .04) * (.9 + .2 * h3(floor(vB * 240.))), co < .5 && p.y < nkTop - .004 ? .25 : 0.);   // в вырезе формы — тёмная футболка
  float n1 = h3(floor(vB * 240.)), mo = vn(p * 9.), weave = .94 + .08 * n1;
  if (foot) {   // ботинки: подошва, носок светлее, шнуровка
    vec3 c = uShoes * (.75 + .55 * smoothstep(.015, .1, p.y)) * (.9 + .2 * mo);
    if (p.y < .017) c = uShoes * .3 + .015;
    if (p.z > .06 && p.y > .05 && ax > .09 && ax < .14 && fract(p.z * 55.) < .3) c *= .55;
    return vec4(c, 1.);
  }
  bool arm = ax > .215 && p.y > 1.32;
  bool legs = !arm && p.y < .975;
  bool coat = co > .5 && (p.y > uCoatY || !legs);
  vec3 c = coat ? uCoat : legs ? uPants : uShirt;
  float sh = 1.;   // складки и тени в сгибах
  if (arm) {
    float u = ax, th = atan(p.z + .068, p.y - 1.455);   // u — вдоль руки (плечо .215 → запястье .665), th: 0 — сверху, ±π — снизу
    float el = exp(-pow((u - .45) / .05, 2.));
    sh *= 1. - .25 * el * smoothstep(.2, .9, sin(u * 150. + sin(th * 2. + u * 30.) * 2.2)) * smoothstep(-.3, .8, sin(th));   // складки в локтевом сгибе
    sh *= 1. - .12 * smoothstep(.45, .9, sin(th * 3. + u * 40.)) * smoothstep(.3, .62, u);                                    // продольные заломы рукава
    sh *= 1. - .3 * ln(3.1416 - abs(th), .09);                                                                                  // шов снизу рукава
    sh *= 1. - .35 * ln(u - .224, .008);                                                                                        // плечевой шов
    if (u > .615) { sh *= .84; sh *= 1. - .35 * ln(u - .617, .005); }                                                           // манжета
    sh *= 1. - .2 * smoothstep(.55, .62, u) * smoothstep(.3, .9, sin(u * 420.)) * (1. - step(.615, u));                       // сборка над манжетой
    sh *= mix(1., .72, smoothstep(.25, .215, u) * smoothstep(1.4, 2.2, abs(th)));                                             // тень подмышкой
    if (uBadge > .5 && p.x < 0.) {   // шеврон-щит на правом рукаве
      vec2 q = vec2((u - .3) / .034, th / .5);
      float shield = step(abs(q.y), 1.) * step(q.x, 1.) * step(-1., q.x) * step(abs(q.y), 1. - max(0., q.x - .2) * 1.2);
      float rim = shield * (1. - step(abs(q.y), .8) * step(q.x, .8) * step(-.82, q.x) * step(abs(q.y), .8 - max(0., q.x - .1) * 1.2));
      if (shield > .5) { c = uTrim * 1.15; sh = 1.; if (rim > .5) c = vec3(.75, .6, .22); if (abs(q.y) < .25 && abs(q.x + .2) < .25) c = vec3(.7, .7, .66); }
    }
    if (uKind > 1.5 && abs(u - .5) < .013) { c = vec3(.78, .8, .76); sh = 1.; }                                                // светоотражающая полоса
  } else if (legs && !coat) {
    float lx = ax - .114, lz = p.z + .03;
    sh *= 1. + .16 * ln(lx, .006) * step(.0, lz) * step(p.y, .9);                                   // стрелка на брюках
    sh *= 1. - .3 * ln(lz, .008) * step(.03, lx);                                                    // боковой шов
    sh *= 1. - .14 * exp(-pow((p.y - .52) / .05, 2.)) * smoothstep(.2, .9, sin(p.y * 140. + sin(ax * 60.) * 2.));   // колени
    sh *= 1. - .16 * smoothstep(.27, .14, p.y) * smoothstep(.25, .9, sin(p.y * 105. + p.x * 40. + sin(p.z * 70.)));   // сборка над ботинком
    sh *= mix(1., .7, smoothstep(.9, .97, p.y) * smoothstep(.1, .03, ax));                           // тень в паху
    sh *= 1. - .2 * smoothstep(.93, .87, p.y) * smoothstep(.06, .015, ax) * step(.0, p.z);
    if (p.y > .86 && p.z > 0.) sh *= 1. - .4 * ln((ax - .125) - (p.y - .86) * .5, .006) * step(p.y, .95);   // косые карманы
    if (p.z < -.09) sh *= 1. - .35 * edge(vec2(ax, p.y), vec2(.045, .8), vec2(.14, .9), .006);             // задние карманы
    if (p.y < .14) sh *= .85;
    if (uKind < .5 || uKind > 1.5) {   // ремень с пряжкой и шлёвками
      if (p.y > .948) { c = vec3(.045, .04, .035) * (.8 + .4 * n1); sh = 1. - .4 * ln(p.y - .95, .004) - .4 * ln(p.y - .973, .004);
        if (p.z > .05 && ax < .026) { c = vec3(.5, .48, .42); sh = ax > .018 || abs(p.y - .961) > .008 ? 1. : .6; } }
      if (p.y > .94 && p.y < .978 && abs(fract(p.x * 9.5 + .5) - .5) < .04) sh *= .7;
    }
    if (uKind > 1.5 && abs(p.y - .37) < .012) { c = vec3(.78, .8, .76); sh = 1.; }
  } else {
    // торс (рубашка, куртка или плащ)
    float zf = p.z - .045;
    sh *= 1. - .3 * ln(p.z + .02, .008) * step(.11, ax);                                                         // боковые швы
    sh *= 1. - .1 * smoothstep(.55, 1., sin((p.x * 2.6 + p.y) * 38. + mo * 3.)) * smoothstep(1.32, 1.05, p.y) * (1. - co);   // складки от груди к поясу
    sh *= mix(1., .7, smoothstep(.17, .215, ax) * smoothstep(1.4, 1.32, p.y) * smoothstep(1.2, 1.3, p.y));      // подмышки
    sh *= 1. - .25 * ln(p.y - (nkTop - .035), .006);                                                               // низ воротника
    // воротник: стойка вокруг шеи и уголки спереди
    if (p.y > nkTop - .035) sh *= 1.08;
    if (co < .5 && p.z > .03 && p.y > 1.41 && ax < (p.y - vS) * .42 + .05 && ax > (p.y - vS) * .42) { sh *= 1.12; sh *= 1. - .35 * ln(ax - (p.y - vS) * .42 - .05, .006); }
    if (co < .5) {
      if (p.z > .04) {   // планка с пуговицами
        sh *= 1. - .3 * ln(p.x - .013, .005) - .2 * ln(p.x + .013, .005);
        float by = fract((p.y - 1.03) / .1 + .5) - .5;
        if (ax < .007 && abs(by) < .07 && p.y < 1.42 && p.y > .99) { c = uShirt * 2.2 + .05; sh = 1.; }
        // карманы с клапанами
        vec2 q = vec2(ax, p.y);
        if (rct(q, vec2(.045, 1.24), vec2(.125, 1.34)) > .5) { sh *= 1. - .35 * edge(q, vec2(.045, 1.24), vec2(.125, 1.34), .005); }
        if (rct(q, vec2(.043, 1.315), vec2(.127, 1.355)) > .5) { sh *= 1.06; sh *= 1. - .45 * ln(p.y - 1.317, .006); if (length(q - vec2(.085, 1.325)) < .006) sh *= .6; }
        if (uBadge > .5 && p.x > .05 && p.x < .115 && p.y > 1.365 && p.y < 1.4) { c = vec3(.8, .64, .25) * (.8 + .3 * step(.5, fract(p.x * 120.))); sh = 1.; }   // жетон
      }
      if (ax > .07 && ax < .215 && p.y > 1.455 && abs(p.z + .045) < .025) { sh *= 1.1; sh *= 1. - .4 * ln(abs(p.z + .045) - .022, .006); }   // погоны
      sh *= 1. - .18 * smoothstep(1.06, .985, p.y) * smoothstep(.3, .9, sin(p.x * 75. + sin(p.x * 13.) * 3.));                // рубашка заправлена: напуск над ремнём
      if (p.z < -.08 && ax < .13 && p.y > 1.2 && p.y < 1.32) {   // надпись на спине
        float row = uKind > 1.5 ? .23 : .73;
        float d = texture2D(uDecal, vec2((.13 - p.x) / .26, row + (p.y - 1.26) / .12 * .36)).r;
        c = mix(c, uKind > 1.5 ? vec3(.85, .85, .8) : vec3(.82, .7, .3), d); sh = mix(sh, 1., d);
      }
      if (uKind > 1.5 && abs(p.y - 1.13) < .014) { c = vec3(.78, .8, .76); sh = 1.; }
      sh *= 1. - .25 * ln(p.y - 1.395, .006) * step(p.z, -.06);                                                               // кокетка на спине
    } else {
      // дождевик: застёжка сбоку от центра с кнопками, кокетка-накидка на груди и спине, клапаны карманов
      sh *= 1. - .45 * ln(p.x + .018, .006) * step(.03, p.z);
      if (p.z > .03 && abs(p.x + .033) < .009 && abs(fract((p.y - .6) / .095) - .5) < .07 && p.y < 1.42) sh *= .55;
      sh *= 1. - .4 * ln(p.y - (1.445 - ax * .2), .007) * step(ax, .215);
      if (p.y < 1.445 - ax * .2 && p.y > 1.43 - ax * .2) sh *= .85;   // тень под кокеткой
      vec2 q = vec2(ax, p.y);
      if (p.z > .02 && p.y < .9) { sh *= 1. - .4 * edge(q, vec2(.07, .68), vec2(.19, .79), .006); if (rct(q, vec2(.068, .76), vec2(.192, .8)) > .5) { sh *= 1.08; sh *= 1. - .45 * ln(p.y - .762, .006); } }
      if (p.y < uCoatY + .03) sh *= .78;                                                                                          // подшитый низ
      sh *= 1. - .14 * smoothstep(.72, 1., vn(vec3(p.x * 34., p.y * 2.5, p.z * 34.)));                                         // мокрые потёки
      if (p.y < .98) sh *= 1. - .12 * smoothstep(.5, 1., sin(atan(p.x, p.z + .04) * 9. + mo * 2.)) * smoothstep(.95, .6, p.y);   // складки полы
    }
  }
  if (coat && arm) sh *= 1. - .12 * smoothstep(.72, 1., vn(vec3(p.x * 6., p.y * 30., p.z * 30.)));
  return vec4(c * sh * weave * (.9 + .2 * mo), 1.);
}
`;
const BODY_PAINT = `
#include <map_fragment>
{
  vec3 p = vB / uS;
  float lum = dot(diffuseColor.rgb, vec3(.3, .59, .11));
  vec3 skin = mix(diffuseColor.rgb, vec3(lum * 1.15), max(uGrey, .3)) * uSkin;
  if (uNaked > .5) {
    // Жилец: истощённое тело тонет в темноте, из неё проступает только бледное лицо; рёбра, пятна
    skin = vec3(lum * .55 + .2) * uSkin;
    float ax0 = abs(p.x);
    if (p.y > 1.1 && p.y < 1.45 && ax0 > .04 && ax0 < .16) skin *= 1. - .3 * smoothstep(.55, .85, sin(p.y * 120. + ax0 * 25. + sin(ax0 * 60.)) * .5 + .5) * smoothstep(.04, .07, ax0);
    skin *= .75 + .35 * h3(floor(vB * 22.));
    skin *= mix(.32, 1., smoothstep(1.5, 1.72, p.y));
  }
  vec4 cl = cloth(p, lum);
  vec3 col = mix(skin, cl.rgb, step(.2, cl.w));
  // лицо: рот и глаза по развёртке текстуры
  vec2 uv = vMapUv;
  // рот: щель между губами раскрывается плавно (без «чёрной дыры»), ширина чуть сужается — как у живого рта
  vec2 md = (uv - (uMouth + vec2(0., .001))) / vec2(.0165 - .003 * uTalk + .045 * uSmile, .0012 + .0052 * uTalk + .005 * uSmile);
  md.y += uSmile * 1.7 * md.x * md.x;                                 // уголки вверх
  float mr = length(md), mo = max(smoothstep(.03, .2, uTalk), step(.5, uSmile));
  if (mr < 1. && mo > 0.) {
    vec3 inner = mix(vec3(.17, .06, .05), vec3(.035, .012, .012), smoothstep(.2, .95, 1. - abs(md.y)));
    if (uSmile < .5 && md.y > .35 && uTalk > .35) inner = vec3(.62, .58, .52) * (.85 + .15 * (1. - mr));   // верхние зубы
    col = mix(col, inner, mo * smoothstep(1., .75, mr));
  }
  if (uSmile > 1.2 && mr < 1. && md.y < -.2 && md.y > -.65) col = vec3(.62, .58, .5) * (.75 + .25 * step(.5, fract(md.x * 7.)));   // зубы (только у оскала; улыбка «Кати» — тёмная щель без зубов)
  // крик Жильца: челюсть отваливается вниз (сдвиг вершин), растянутая щель между губами — чёрная пасть с острыми зубами по краям
  if (uScream > .05) {
    vec2 e = (uv - uMouth - vec2(0., .003)) / vec2(.02 * (.65 + .35 * uScream), .0105);
    float er = length(e);
    if (er < 1.) {
      vec3 hole = vec3(.025, .006, .006) * (1. - er * .5);
      float tx = abs(fract(e.x * 4.5 + .25) - .5) * 2.;
      if (e.y < -.45 && tx < (e.y + 1.05) * -1.9 + 1.15) hole = vec3(.5, .46, .36) * (.6 + .4 * (1. - tx));   // верхние зубы
      if (e.y > .5 && tx < (1.05 - e.y) * 1.9) hole = vec3(.46, .42, .33) * (.6 + .4 * (1. - tx));            // нижние
      col = mix(col, hole, smoothstep(1., .85, er) * min(1., uScream * 2.));
    }
  }
  // запавшие глазницы: вокруг глаз кожа уходит в черноту
  if (uSocket > 0.) {
    float sl = length((uv - uEyeL) / vec2(.034, .024)), sr = length((uv - uEyeR) / vec2(.034, .024));
    col *= mix(1., .12, uSocket * smoothstep(1., .4, min(sl, sr)));
  }
  if (uBlink > 1.5) {   // (старое веко на текстуре — не используется: веком теперь служит сам глаз)
    vec2 e1 = (uv - uEyeL) / vec2(.027, .017), e2 = (uv - uEyeR) / vec2(.027, .017);
    // веко: ровный цвет кожи со щеки (не кусок текстуры со складками), снизу — тёмная линия ресниц
    vec3 lid = mix(texture2D(map, uMouth + vec2(.035, .03)).rgb, vec3(lum), .0) * uSkin * .92;
    float ed = min(length(e1), length(e2)), ey = abs(length(e1) < length(e2) ? e1.y + .25 : e2.y + .25);
    if (ed < 1.) col = mix(mix(lid, lid * .35, smoothstep(.16, .03, ey)), col, smoothstep(.75, 1., ed));
  }
  col *= mix(1., .82, uWet * step(1.5, p.y));
  // кровь: кисти и подбородок (у Жильца после еды), пятна на груди и шее (у тела)
  if (uGore > 0.) {
    float n = h3(floor(vB * 90.)), bl = 0.;
    if (abs(p.x) > .64) bl = smoothstep(.64, .74, abs(p.x)) * (.55 + .45 * n);
    vec2 cd = (uv - uMouth - vec2(0., -.012)) / vec2(.05, .028); if (length(cd) < 1. && cd.y < .6) bl = max(bl, (1. - length(cd)) * 1.6 * (.6 + .4 * n));
    if (uGore > 1.5 && p.y > 1.15 && p.y < 1.62 && abs(p.x) < .2 && p.z > -.02) bl = max(bl, smoothstep(.35, .7, h3(floor(vB * 14.)) + (1.62 - p.y) * -.2 + .25));
    col = mix(col, vec3(.16, .015, .01) * (.7 + .5 * n), clamp(bl, 0., 1.) * min(uGore, 1.));
  }
  diffuseColor.rgb = col;
}`;
function uniforms(o) {
  const sk = new T.Color(o.skin ?? 0xffffff); if (o.skinK) sk.multiplyScalar(o.skinK);
  return {
    uShirt: { value: new T.Color(o.shirt ?? 0x222222) }, uPants: { value: new T.Color(o.pants ?? 0x1a1a1a) }, uShoes: { value: new T.Color(o.shoes ?? 0x111111) },
    uSkin: { value: sk }, uCoat: { value: new T.Color(o.coat ?? 0x000000) }, uTrim: { value: new T.Color(o.trim ?? 0x111111) },
    uS: { value: o.fem ? 1.77 / 1.81 : 1 }, uCoatY: { value: o.coatY || 0 }, uTalk: { value: 0 }, uBlink: { value: 0 }, uSmile: { value: o.smile ? 1 : 0 },
    uNaked: { value: o.naked ? 1 : 0 }, uBadge: { value: o.badge ? 1 : 0 }, uFem: { value: o.fem ? 1 : 0 }, uGore: { value: 0 }, uWet: { value: o.wet ? 1 : 0 }, uGrey: { value: o.grey || 0 }, uFlat: { value: o.coatY && o.fem ? 1 : 0 }, uThin: { value: o.thin || 1 },
    uScream: { value: 0 }, uSocket: { value: o.socket || 0 }, uKind: { value: o.kind ?? (o.coatY ? 1 : 0) }, uSkirt: { value: o.coatY ? 1 : 0 }, uDecal: { value: decal() }, uMouth: { value: new T.Vector2(.1855, .2637) }, uEyeL: { value: o.fem ? new T.Vector2(.130, .179) : new T.Vector2(.1367, .1758) }, uEyeR: { value: o.fem ? new T.Vector2(.238, .179) : new T.Vector2(.2344, .1758) },
  };
}
// под плащом фигура проще: грудь и бёдра прижимаем (в позе «Т», до скелета)
const FLAT = `
  if (uScream > 0.) { vec3 q = position / uS;   // челюсть Жильца: всё ниже нижней губы съезжает вниз и чуть вперёд
    float w = smoothstep(1.622, 1.606, q.y) * smoothstep(1.53, 1.575, q.y) * (1. - smoothstep(.035, .072, abs(q.x))) * smoothstep(-.01, .04, q.z);
    transformed.y -= uScream * .078 * w * uS; transformed.z += uScream * .014 * w * uS; }
  if (uThin < 1.) { vec3 q = position / uS; transformed.z *= mix(uThin, 1., smoothstep(1.47, 1.56, q.y));   // голову не сплющиваем: иначе глаза торчат из лица
    if (q.y > .95 && abs(q.x) < .24) transformed.x *= mix(mix(uThin, 1., smoothstep(.12, .24, abs(q.x))), 1., smoothstep(1.47, 1.56, q.y)); }
  if (uFlat > .5) { vec3 q = position / uS;
    if (q.y > 1.12 && q.y < 1.48 && q.z > .03) transformed.z = (.03 + (q.z - .03) * .25) * uS;
    if (q.y > .78 && q.y < 1.08 && q.z < -.07) transformed.z = (-.07 + (q.z + .07) * .35) * uS; }`;
const SNAPV = '#include <project_vertex>\n  gl_Position.xy = floor(gl_Position.xy / gl_Position.w * uSnap + .5) / uSnap * gl_Position.w;';
function bodyMat(src, U, shell, side) {
  const m = new T.MeshLambertMaterial({ map: src.map, side: side || T.FrontSide });
  m.onBeforeCompile = sh => {
    Object.assign(sh.uniforms, U); sh.uniforms.uSnap = SNAP;
    sh.vertexShader = 'uniform vec2 uSnap; uniform float uS, uCoatY, uNaked, uFlat, uThin, uScream;\nvarying vec3 vB;\n' + (shell ? 'attribute vec3 aB;\n' : '') + sh.vertexShader
      .replace('#include <begin_vertex>', '#include <begin_vertex>\n' + (shell ? ' vB = aB;' : ' vB = position;' + FLAT))
      .replace('#include <beginnormal_vertex>', '#include <beginnormal_vertex>\n' + (shell ? '' : ' if (uFlat > .5) { vec3 q = position / uS; if (q.y > 1.12 && q.y < 1.48 && q.z > .02) objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .5, objectNormal.y * .3, 1.), .75)); if (q.y > .76 && q.y < 1.1 && q.z < -.05) objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .5, 0., -1.), .7)); }'))
      .replace('#include <project_vertex>', SNAPV);
    sh.fragmentShader = BODY_FS + sh.fragmentShader.replace('#include <map_fragment>', shell
      ? '#include <map_fragment>\n{ vec3 q = vB / uS; vec4 cl = cloth(q, dot(diffuseColor.rgb, vec3(.3, .59, .11))); if (cl.w < .5) discard; if (uSkirt > .5 && q.y < .985 && q.y > uCoatY - .01 && !(abs(q.x) > .215 && q.y > 1.32)) discard; diffuseColor.rgb = cl.rgb; }'
      : BODY_PAINT)
      // блеск мокрого дождевика: светлый край по контуру и блик сверху (от ламп на потолке); у ткани — лёгкий бархатный край
      .replace('#include <emissivemap_fragment>', '#include <emissivemap_fragment>\n' + (shell ? `{ vec3 vd = normalize(vViewPosition); float fr = pow(1. - abs(dot(normal, vd)), 3.);
        vec3 up = normalize((viewMatrix * vec4(0., 1., 0., 0.)).xyz); float top = pow(max(0., dot(normal, up)), 6.);
        float pvc = step(.5, uKind) * step(uKind, 1.5);
        totalEmissiveRadiance += diffuseColor.rgb * (fr * mix(.12, .55, pvc) + top * .45 * pvc) + vec3(.05, .05, .045) * fr * pvc; }` : ''));
  };
  m.customProgramCacheKey = () => shell ? 'shell3' : 'body3';
  return m;
}

/* ---------- ткань: отдельная сетка одежды ---------- */
// Базовая модель — «супергерой» (мышцы, грудь, ягодицы). Если красить одежду прямо по телу, выходит трико.
// Поэтому одежда — своя сетка: копия тела, где каждая точка ткани отходит от тела по нормали ровно настолько,
// чтобы ткань натянулась между выпуклостями, как настоящая (впадины талии, между мышцами и т.п. заполняются),
// а рукава и штанины становятся прямыми трубами с запасом к манжете и низу. Считается один раз на тип одежды.
const CLOTH = {};
const PART = n => /^(upperarm|lowerarm)_l/.test(n) ? 1 : /^(upperarm|lowerarm)_r/.test(n) ? 2 : /^(thigh|calf)_l/.test(n) ? 3 : /^(thigh|calf)_r/.test(n) ? 4 : /^(foot|ball)/.test(n) ? 5 : /^(hand|index|middle|ring|pinky|thumb)/.test(n) ? 6 : /^(neck|Head)/.test(n) ? 7 : 0;
function zoneJS(x, y, z, co) {   // то же, что cloth() в шейдере: 0 — кожа, 1 — ткань, 2 — ботинок
  const ax = Math.abs(x), nkTop = co ? 1.55 : 1.535, vS = co ? 9 : 1.405;
  if (ax > .665) return 0;
  if (y > nkTop || (z > .02 && y > vS && ax < (y - vS) * (co ? .75 : .42)) || (y > 1.565 && ax < .07)) return 0;
  return y < .12 ? 2 : 1;
}
function clothGeo(mesh, o) {
  const co = !!o.coatY && (o.kind ?? 1) === 1, key = (o.fem ? 'f' : 'm') + (co ? 'c' : 'g');
  if (CLOTH[key]) return CLOTH[key];
  const g = mesh.geometry, pa = g.attributes.position, na = g.attributes.normal, si = g.attributes.skinIndex, sw = g.attributes.skinWeight, n = pa.count, uS = o.fem ? 1.77 / 1.81 : 1;
  const names = mesh.skeleton.bones.map(b => b.name);
  const bp = nm => { const i = names.indexOf(nm); return new T.Vector3().setFromMatrixPosition(mesh.skeleton.boneInverses[i].clone().invert()); };
  // сварка: на швах развёртки точки продублированы — объединяем совпадающие
  const id = new Int32Array(n), seen = new Map(), P0 = [], N0 = [], part = [], zone = [];
  let W = 0;
  for (let i = 0; i < n; i++) {
    const x = pa.getX(i), y = pa.getY(i), z = pa.getZ(i), k = Math.round(x * 2e4) + ',' + Math.round(y * 2e4) + ',' + Math.round(z * 2e4);
    let w = seen.get(k);
    if (w === undefined) {
      w = W++; seen.set(k, w); P0.push(x, y, z); N0.push(0, 0, 0);
      const ws = [sw.getX(i), sw.getY(i), sw.getZ(i), sw.getW(i)], is = [si.getX(i), si.getY(i), si.getZ(i), si.getW(i)];
      let b = 0; for (let j = 1; j < 4; j++) if (ws[j] > ws[b]) b = j;
      part.push(PART(names[is[b]])); zone.push(zoneJS(x / uS, y / uS, z / uS, co));
    }
    id[i] = w; N0[w * 3] += na.getX(i); N0[w * 3 + 1] += na.getY(i); N0[w * 3 + 2] += na.getZ(i);
  }
  for (let w = 0; w < W; w++) { const l = Math.hypot(N0[w * 3], N0[w * 3 + 1], N0[w * 3 + 2]) || 1; N0[w * 3] /= l; N0[w * 3 + 1] /= l; N0[w * 3 + 2] /= l; }
  const ix = g.index.array, nb = Array.from({ length: W }, () => []);
  for (let t = 0; t < ix.length; t += 3) {
    const a = id[ix[t]], b = id[ix[t + 1]], c = id[ix[t + 2]];
    nb[a].push(b, c); nb[b].push(a, c); nb[c].push(a, b);
  }
  // толщина ткани; край ткани (у шеи, кистей) прижат к телу
  const th = new Float32Array(W), free = new Uint8Array(W), H = new Float32Array(W), P = new Float32Array(W * 3), TG = new Float32Array(W * 3);
  for (let w = 0; w < W; w++) {
    const z = zone[w], pt = part[w];
    th[w] = z === 0 ? 0 : z === 2 ? .011 : co ? (pt === 0 ? .016 : pt <= 2 ? .012 : pt === 7 ? .01 : .007) : (pt === 0 ? .007 : pt <= 2 ? .006 : pt === 7 ? .006 : .007);
    free[w] = z > 0 && nb[w].every(u => zone[u] === z) ? 1 : 0;
    for (let j = 0; j < 3; j++) P[w * 3 + j] = P0[w * 3 + j] + N0[w * 3 + j] * th[w];
  }
  // 1) огибающая: ткань не повторяет каждую мышцу — по каждому сечению берём самую выступающую точку рядом
  //    (выше по телу — дальше: ткань свисает с груди/плеч вниз), и всё сечение отодвигаем до неё
  const v3 = new T.Vector3(), clampi = (x, a, b) => Math.max(a, Math.min(b, x));
  function envelope(ws, ax, nf, na, lo, hi, aw, extra) {
    const F = new Float32Array(nf * na), Q = [];
    for (const w of ws) { const q = ax(w); if (!q) continue; const fi = clampi(Math.floor(q.f * nf), 0, nf - 1), ai = (Math.floor((q.a / (Math.PI * 2) + 1) * na)) % na; F[fi * na + ai] = Math.max(F[fi * na + ai], q.r); Q.push([w, q]); }
    const E = new Float32Array(nf * na);
    for (let fi = 0; fi < nf; fi++) for (let ai = 0; ai < na; ai++) { let m = 0; for (let d = -lo; d <= hi; d++) { const f2 = fi + d; if (f2 < 0 || f2 >= nf) continue; for (let da = -aw; da <= aw; da++) m = Math.max(m, F[f2 * na + (ai + da + na) % na]); } E[fi * na + ai] = m; }
    const E2 = new Float32Array(nf * na);   // скруглить по кругу
    for (let fi = 0; fi < nf; fi++) for (let ai = 0; ai < na; ai++) E2[fi * na + ai] = (E[fi * na + (ai + na - 1) % na] + 2 * E[fi * na + ai] + E[fi * na + (ai + 1) % na]) / 4;
    const at = (fx, fa) => { const f0 = clampi(Math.floor(fx), 0, nf - 1), f1 = Math.min(nf - 1, f0 + 1), tf = clampi(fx - f0, 0, 1), a0 = ((Math.floor(fa) % na) + na) % na, a1 = (a0 + 1) % na, ta = fa - Math.floor(fa);
      return (E2[f0 * na + a0] * (1 - ta) + E2[f0 * na + a1] * ta) * (1 - tf) + (E2[f1 * na + a0] * (1 - ta) + E2[f1 * na + a1] * ta) * tf; };
    for (const [w, q] of Q) {
      const want = at(q.f * nf - .5, (q.a / (Math.PI * 2) + 1) * na - .5) * q.k + extra(q) + th[w] * .5;
      if (want > q.r) { const d = want - q.r; P[w * 3] += q.ux * d; P[w * 3 + 1] += q.uy * d; P[w * 3 + 2] += q.uz * d; }
    }
  }
  const tube = (a, b, perp1) => { const A = bp(a), B = bp(b), d = B.clone().sub(A), L2 = d.lengthSq(), dn = d.clone().normalize(), e1 = perp1.clone(), e2 = new T.Vector3().crossVectors(dn, e1).normalize();
    return w => { v3.set(P[w * 3], P[w * 3 + 1], P[w * 3 + 2]).sub(A); const f = v3.dot(d) / L2; v3.addScaledVector(d, -f); const r = v3.length() || 1e-6;
      return { f: clampi(f, 0, .999), a: Math.atan2(v3.dot(e2), v3.dot(e1)), r, ux: v3.x / r, uy: v3.y / r, uz: v3.z / r, k: 1 }; }; };
  const of = (pp, extraCond) => { const r = []; for (let w = 0; w < W; w++) if (zone[w] === 1 && pp.includes(part[w]) && (!extraCond || extraCond(w))) r.push(w); return r; };
  const yN = w => P0[w * 3 + 1] / uS;
  const of2 = (pp, cond) => { const r = []; for (let w = 0; w < W; w++) if (zone[w] === 2 && part[w] === pp && cond(w)) r.push(w); return r; };
  // торс: ось вертикальная; рубашка свисает ~8 см с выпуклостей, дождевик — ~20 см (грудь и плечи не обрисовываются)
  envelope(of([0], w => Math.abs(P0[w * 3]) / uS < .21 && yN(w) > .86 && yN(w) < 1.5), w => { const y = yN(w); v3.set(P[w * 3], 0, P[w * 3 + 2] + .02 * uS); const r = v3.length() || 1e-6;
    return { f: (y - .86) / .64, a: Math.atan2(v3.x, v3.z), r, ux: v3.x / r, uy: 0, uz: v3.z / r, k: 1 }; }, 32, 24, co ? 6 : 2, co ? 12 : 7, 1, () => 0);
  // рукава и штанины — трубы: сечение по самой толстой части рядом (локоть и икры не видны)
  for (const [pp, a, b] of [[1, 'upperarm_l', 'hand_l'], [2, 'upperarm_r', 'hand_r']]) envelope(of([pp]), tube(a, b, new T.Vector3(0, 1, 0)), 16, 16, 2, 5, 1, () => 0);
  for (const [pp, a, b] of [[3, 'thigh_l', 'foot_l'], [4, 'thigh_r', 'foot_r']]) envelope(of([pp]), tube(a, b, new T.Vector3(1, 0, 0)), 18, 16, 2, 4, 1, () => 0);
  // воротник: расходится от шеи кверху (стойка, а не водолазка)
  for (let w = 0; w < W; w++) if (zone[w] === 1 && part[w] === 7) { v3.set(P0[w * 3], 0, P0[w * 3 + 2] + .03 * uS); const r = v3.length() || 1; const e = (.004 + Math.max(0, yN(w) - 1.48) * (co ? .14 : .1)) * uS; P[w * 3] += v3.x / r * e; P[w * 3 + 2] += v3.z / r * e; }
  // 2) натяжение: остаток впадин заполняется, ткань тянется к соседям, но внутрь тела не уходит
  for (let w = 0; w < W; w++) { let h = 0; for (let j = 0; j < 3; j++) h += (P[w * 3 + j] - P0[w * 3 + j]) * N0[w * 3 + j]; H[w] = h; for (let j = 0; j < 3; j++) TG[w * 3 + j] = P[w * 3 + j] - P0[w * 3 + j] - N0[w * 3 + j] * h; }
  const link = (w, u) => part[u] === part[w] || (zone[u] === zone[w] && ((part[w] === 0) !== (part[u] === 0)) && (part[w] >= 3 || part[u] >= 3 || yN(w) > 1.45));
  const upd = () => { for (let w = 0; w < W; w++) for (let j = 0; j < 3; j++) P[w * 3 + j] = P0[w * 3 + j] + TG[w * 3 + j] + N0[w * 3 + j] * H[w]; };
  for (let it = 0; it < 40; it++) {
    for (let w = 0; w < W; w++) {
      if (!free[w]) continue;
      let sx = 0, sy = 0, sz = 0, c = 0;
      for (const u of nb[w]) { if (!link(w, u)) continue; sx += P[u * 3]; sy += P[u * 3 + 1]; sz += P[u * 3 + 2]; c++; }
      if (!c) continue;
      const h = (sx / c - P0[w * 3] - TG[w * 3]) * N0[w * 3] + (sy / c - P0[w * 3 + 1] - TG[w * 3 + 1]) * N0[w * 3 + 1] + (sz / c - P0[w * 3 + 2] - TG[w * 3 + 2]) * N0[w * 3 + 2];
      H[w] = Math.min(.09, Math.max(th[w], H[w] + (h - H[w]) * .6));
    }
    upd();
  }
  // свободный крой: рукава и штанины шире к низу (труба, а не обтяжка)
  const seg = (a, b, sl0, sl1) => { const A = bp(a), B = bp(b), d = B.clone().sub(A), L2 = d.lengthSq(), v = new T.Vector3();
    return w => { v.set(P[w * 3], P[w * 3 + 1], P[w * 3 + 2]); const f = Math.min(1, Math.max(0, v.clone().sub(A).dot(d) / L2)); const r = v.sub(A.clone().addScaledVector(d, f)); const rl = r.length() || 1;
      const e = (sl0 + (sl1 - sl0) * f * f) * uS; P[w * 3] += r.x / rl * e; P[w * 3 + 1] += r.y / rl * e; P[w * 3 + 2] += r.z / rl * e; }; };
  const fx = { 1: seg('upperarm_l', 'hand_l', co ? .006 : .004, co ? .022 : .012), 2: seg('upperarm_r', 'hand_r', co ? .006 : .004, co ? .022 : .012),
    3: seg('thigh_l', 'foot_l', .003, .016), 4: seg('thigh_r', 'foot_r', .003, .016) };
  for (let w = 0; w < W; w++) if (zone[w] === 1 && fx[part[w]]) fx[part[w]](w);
  // нормали по готовой ткани (сглаженные, без мышечного рельефа)
  const NN = new Float32Array(W * 3), e1 = new T.Vector3(), e2 = new T.Vector3(), pA = new T.Vector3(), pB = new T.Vector3(), pC = new T.Vector3();
  for (let t = 0; t < ix.length; t += 3) {
    const a = id[ix[t]], b = id[ix[t + 1]], c = id[ix[t + 2]];
    pA.fromArray(P, a * 3); pB.fromArray(P, b * 3); pC.fromArray(P, c * 3);
    e1.subVectors(pB, pA); e2.subVectors(pC, pA); e1.cross(e2);
    for (const v of [a, b, c]) { NN[v * 3] += e1.x; NN[v * 3 + 1] += e1.y; NN[v * 3 + 2] += e1.z; }
  }
  for (let pass = 0; pass < 2; pass++) {   // ещё чуть сгладить нормали — мягкие складки вместо граней
    const M = NN.slice();
    for (let w = 0; w < W; w++) { if (!zone[w]) continue; let x = M[w * 3] * 2, y = M[w * 3 + 1] * 2, z = M[w * 3 + 2] * 2; for (const u of nb[w]) if (part[u] === part[w]) { const l = Math.hypot(M[u * 3], M[u * 3 + 1], M[u * 3 + 2]) || 1; const lw = Math.hypot(M[w * 3], M[w * 3 + 1], M[w * 3 + 2]) || 1; x += M[u * 3] / l * lw; y += M[u * 3 + 1] / l * lw; z += M[u * 3 + 2] / l * lw; } NN[w * 3] = x; NN[w * 3 + 1] = y; NN[w * 3 + 2] = z; }
  }
  const pos = new Float32Array(n * 3), nor = new Float32Array(n * 3), ab = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) {
    const w = id[i], l = Math.hypot(NN[w * 3], NN[w * 3 + 1], NN[w * 3 + 2]) || 1;
    for (let j = 0; j < 3; j++) { pos[i * 3 + j] = P[w * 3 + j]; nor[i * 3 + j] = NN[w * 3 + j] / l; }
    ab[i * 3] = pa.getX(i); ab[i * 3 + 1] = pa.getY(i); ab[i * 3 + 2] = pa.getZ(i);
  }
  const sg = new T.BufferGeometry();
  if (co) {   // профиль пол плаща: по каждому сечению ниже пояса — самая дальняя точка ткани (бёдра, ягодицы), книзу только шире
    const NA = 28, rows = [], cz = -.047 * uS; let prev = null;
    for (let j = 0; j <= 9; j++) {
      const y = (.99 + (o.coatY - .99) * j / 9) * uS, row = new Float32Array(NA);
      for (let w = 0; w < W; w++) { if (zone[w] !== 1 || part[w] === 1 || part[w] === 2 || Math.abs(P[w * 3 + 1] - y) > (j ? .045 : .02)) continue;
        const x = P[w * 3], z = P[w * 3 + 2] - cz, r = Math.hypot(x, z), ai = (Math.floor((Math.atan2(x, z) / (Math.PI * 2) + 1) * NA)) % NA; row[ai] = Math.max(row[ai], r); }
      const sm = new Float32Array(NA);
      for (let i = 0; i < NA; i++) { let m = 0; for (let d = -2; d <= 2; d++) m = Math.max(m, row[(i + d + NA) % NA]); sm[i] = m / uS + .002 + .04 * Math.pow(j / 9, 1.3); if (prev) sm[i] = Math.max(sm[i], prev[i] + .004); }
      rows.push(sm); prev = sm;
    }
    sg.userData.skirt = { rows, cz: -.047, NA };
  }
  sg.setAttribute('position', new T.BufferAttribute(pos, 3)); sg.setAttribute('normal', new T.BufferAttribute(nor, 3)); sg.setAttribute('aB', new T.BufferAttribute(ab, 3));
  sg.setAttribute('uv', g.attributes.uv); sg.setAttribute('skinIndex', si); sg.setAttribute('skinWeight', sw); sg.setIndex(g.index);
  sg.boundingSphere = g.boundingSphere; sg.boundingBox = g.boundingBox;
  return (CLOTH[key] = sg);
}
// надписи на спине формы: «ОХРАНА» у охранников, «КЛИНИНГ» у уборщика
let DECAL = null;
function decal() {
  if (DECAL) return DECAL;
  const c = document.createElement('canvas'); c.width = 256; c.height = 128; const g = c.getContext('2d');
  g.fillStyle = '#000'; g.fillRect(0, 0, 256, 128); g.fillStyle = '#fff'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.font = 'bold 52px Arial, Helvetica, sans-serif'; g.fillText('ОХРАНА', 128, 34, 240); g.font = 'bold 44px Arial, Helvetica, sans-serif'; g.fillText('КЛИНИНГ', 128, 96, 196);
  DECAL = new T.CanvasTexture(c); DECAL.magFilter = T.NearestFilter;
  return DECAL;
}
function ps1(m) { window.__W3core.ps1(m); return m; }

/* ---------- сборка персонажа ---------- */
function attach(C, part, color) {   // волосы/борода из отдельного файла садятся на скелет персонажа по именам костей
  let sm = null; G[part].scene.traverse(n => { if (n.isSkinnedMesh) sm = n; });
  if (!sm) return null;
  const bones = sm.skeleton.bones.map(b => C.bone[b.name]);
  // короткая стрижка — сплошная (сквозь редкую карту волос просвечивала кожа пятнами)
  const buzz = part === 'Hair_Buzzed';
  const m = new T.SkinnedMesh(sm.geometry, ps1(new T.MeshLambertMaterial(buzz ? { color, side: T.DoubleSide, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -6 } : { map: sm.material.map, color, alphaTest: .45, side: T.DoubleSide })));
  m.frustumCulled = false;
  C.mesh.parent.add(m); m.bind(new T.Skeleton(bones, sm.skeleton.boneInverses), C.mesh.bindMatrix);
  return m;
}
function makeSkinned(o) {
  const src = G[o.fem ? 'female' : 'male'];
  const sc = window.THREE_SkeletonUtils.clone(src.scene);
  const root = new T.Group(); root.name = o.name;
  const inner = new T.Group(); inner.add(sc); root.add(inner);
  const C = { root, inner, o, bone: {}, pose: 'stand', look: null, talk: 0, blinkT: 1 + rnd() * 3, t0: rnd() * 10, faceSet: 'base', faceSets: { base: 1 }, rest: {} };
  sc.traverse(n => {
    if (n.isBone) { C.bone[n.name] = n; C.rest[n.name] = n.quaternion.clone(); }
    if (n.isSkinnedMesh) {
      n.frustumCulled = false;
      const nm = n.name.toLowerCase();
      if (nm.includes('eyebrow')) { n.material = browMat(C, n.material.map, o.brow ?? o.hair ?? 0x1a120c); C.brows = n; }
      else if (nm.includes('eye')) { n.material = o.voidEyes || o.deadEyes != null ? ps1(new T.MeshBasicMaterial({ color: o.deadEyes ?? 0x000000 })) : ps1(new T.MeshLambertMaterial({ map: n.material.map && n.material.map.clone(), color: o.eyeC ?? 0xffffff })); if (n.material.map) n.material.map.needsUpdate = true; C.eyes = n; }
      else { C.U = uniforms(o); const src = n.material; n.material = bodyMat(src, C.U, false); C.mesh = n;
        if (!o.naked) { const cg = clothGeo(n, o), sh = new T.SkinnedMesh(cg, bodyMat(src, C.U, true)); sh.frustumCulled = false; C.shellOf = [n, sh]; C.clothMat = sh.material; C.skirtProf = cg.userData.skirt; } }
    }
  });
  if (C.shellOf) { const [n, sh] = C.shellOf; n.parent.add(sh); sh.bind(n.skeleton, n.bindMatrix); }
  if (o.hairPart) C.hair = attach(C, o.hairPart, o.hair);
  if (o.beard) C.beard = attach(C, 'Hair_Beard', o.beardC ?? o.hair);
  if (o.monster) { C.brows.visible = false; }
  // пропорции: рост, худоба, длинные руки
  const s = (o.h || 1.8) / (o.fem ? 1.77 : 1.81);
  inner.scale.setScalar(s);   // только равномерно: иначе кости перекашиваются; худобу делает шейдер (uThin)
  if (o.arm) for (const b of ['upperarm_l', 'upperarm_r']) C.bone[b].scale.setScalar(o.arm);
  if (o.armL) for (const b of ['lowerarm_l', 'lowerarm_r']) C.bone[b].scale.setScalar(o.armL);
  if (o.handS) for (const b of ['hand_l', 'hand_r']) C.bone[b].scale.setScalar(o.handS);
  if (o.neck) { C.bone.neck_01.scale.setScalar(o.neck); C.bone.Head.scale.setScalar((o.headS || 1) / o.neck); }
  // кепка и очки — простые детали на кости головы
  const head = C.bone.Head;
  if (o.cap) {   // фуражка охранника: тулья шире околыша, чёрный козырёк, кокарда
    const cp = new T.Group(); head.add(cp); const cm = mat(o.cap), dk = mat(0x0b0b0c);
    const band = cyl(.098, .1, .045, 12, dk, cp); band.scale.z = 1.12; band.position.y = -.02;
    const crown = cyl(.128, .1, .05, 12, cm, cp); crown.scale.z = 1.15; crown.position.set(0, .025, -.008);
    const top = cyl(.128, .128, .006, 12, cm, cp); top.scale.z = 1.15; top.position.set(0, .05, -.008);
    const vis = new T.Mesh(new T.CylinderGeometry(.1, .1, .008, 12, 1, false, -Math.PI / 2, Math.PI), mat(0x050505)); vis.scale.set(1, 1, .62); vis.position.set(0, -.04, .06); vis.rotation.x = .22; cp.add(vis);
    box(.03, .028, .006, mat(0xc9a43a, null, { emissive: 0x2a2008 }), 0, .005, .118, cp);
    C.capG = cp; }
  if (o.glasses) { const gl = new T.Group(); head.add(gl); const gm = mat(0x151515); for (const sx of [-1, 1]) { const x = sx * .033; box(.046, .004, .004, gm, x, .013, 0, gl); box(.046, .004, .004, gm, x, -.013, 0, gl); box(.004, .03, .004, gm, x - .022, 0, 0, gl); box(.004, .03, .004, gm, x + .022, 0, 0, gl); box(.004, .004, .07, gm, sx * .058, .01, -.035, gl); } box(.022, .004, .004, gm, 0, .008, 0, gl); C.glassG = gl; }
  // полы плаща: своя сетка со скелетом (строится в fitProps, когда известна поза «Т»)
  if (o.coatY) C.skirt = true;
  // пряди волос Жильца свисают перед лицом
  if (o.veil) { const vg = new T.Group(); const hm = mat(0x050505); C.strands = [];
    for (const [x, len, z, w] of [[-.062, .17, .0, .012], [-.035, .13, .01, .008], [.05, .15, .006, .01], [.072, .19, -.004, .012]]) {
      const st = new T.Group(); st.position.set(x, 0, z); box(w, len, .004, hm, 0, -len / 2, 0, st); box(w * .6, len * .35, .004, hm, w * .3, -len - len * .15, -.003, st); vg.add(st); C.strands.push(st); }
    C.veilG = vg; }
  // предметы в правой руке
  const hand = C.bone.hand_r; const prop = new T.Group(); hand.add(prop);
  const keys = new T.Group(); box(.05, .006, .03, mat(0xb8a060, null, { emissive: 0x201800 }), 0, 0, 0, keys); box(.008, .05, .008, mat(0xc0c0c0), .01, -.03, 0, keys); keys.visible = false; prop.add(keys);
  const paper = box(.16, .2, .005, mat(0xe0d8c0), 0, 0, 0, prop); paper.visible = false;
  C.keys = keys; C.paper = paper; C.prop = prop;
  if (o.mop) {   // швабра уборщика (в осях персонажа: низ — тряпка на полу, черенок идёт к рукам)
    const mp = new T.Group(); root.add(mp);
    const st = cyl(.013, .013, 1.45, 6, mat(0x8a8f96), mp); st.position.y = .74;
    box(.36, .035, .1, mat(0x3a3f6a), 0, .03, 0, mp);
    for (let i = 0; i < 9; i++) { const f = box(.035, .025, .16, mat(0xb8b4a8), -.16 + i * .04, .012, .02, mp); f.rotation.y = (rnd() - .5) * .5; }
    mp.visible = false; C.mop = mp;
  }
  // анимации
  C.mixer = new T.AnimationMixer(sc); C.act = null; C.clip = '';
  C.addFace = (nm, ov) => { C.faceSets[nm] = ov; };
  C.update = (t, dt) => updateSkinned(C, t, dt);
  fitProps(C);
  return C;
}

// где держать предметы и как ставить голову/кепку/очки — подгоняется один раз по позе «Т» (первый кадр)
function fitProps(C) {
  if (C.fitted) return; C.fitted = true;
  C.root.updateWorldMatrix(true, true);
  const head = C.bone.Head, hw = new T.Vector3(), q = new T.Quaternion();
  head.getWorldQuaternion(q); const inv = q.clone().invert();
  const toLocal = (wx, wy, wz) => { const v = new T.Vector3(wx, wy, wz); head.worldToLocal(v); return v; };
  head.getWorldPosition(hw);
  const s = C.inner.scale.y;
  if (C.capG) { C.capG.position.copy(toLocal(hw.x, hw.y + .2 * s, hw.z + .005 * s)); C.capG.quaternion.copy(inv).multiply(C.root.getWorldQuaternion(new T.Quaternion())); }
  const rootQ = C.root.getWorldQuaternion(new T.Quaternion());
  const put = (obj, bone, wx, wy, wz) => { const b = C.bone[bone]; b.add(obj); const v = new T.Vector3(wx, wy, wz); b.worldToLocal(v); obj.position.copy(v); obj.quaternion.copy(b.getWorldQuaternion(new T.Quaternion()).invert().multiply(rootQ)); };
  const ss = C.inner.scale.y;
  if (C.skirt) skinSkirt(C, ss * (C.o.fem ? .978 : 1));
  if (C.hood) put(C.hood, 'Head', hw.x, hw.y + .085 * ss, hw.z - .01 * ss);
  if (C.veilG) put(C.veilG, 'Head', hw.x, hw.y + .175 * ss, hw.z + .1 * ss);
  if (C.glassG) { C.glassG.position.copy(toLocal(hw.x, hw.y + .095 * s, hw.z + .105 * s)); C.glassG.quaternion.copy(inv).multiply(C.root.getWorldQuaternion(new T.Quaternion())); }
  C.headFwd = new T.Vector3(0, 0, 1).applyQuaternion(inv);
  const hand = C.bone.hand_r, hq = new T.Quaternion(); hand.getWorldQuaternion(hq);
  C.prop.quaternion.copy(hq.invert()).multiply(C.root.getWorldQuaternion(new T.Quaternion()));
  C.prop.position.set(0, 0, 0);
}

// полы плаща — своя сетка со скелетом: верх держится за таз, низ идёт за бёдрами (не болтается жёстким цилиндром).
// Сечение — овал чуть шире бёдер, книзу расширяется (А-силуэт), по низу — мягкие складки; та же ткань, что у плаща.
function skinSkirt(C, k) {
  const par = C.mesh.parent; par.updateWorldMatrix(true, true);
  const P = n => C.bone[n].getWorldPosition(new T.Vector3());
  const pel = P('pelvis'), tl = P('thigh_l'), base = C.root.getWorldPosition(new T.Vector3());
  const SP = C.skirtProf, top = .99, bot = C.o.coatY, R = SP.NA, H = SP.rows.length - 1, side = Math.sign(tl.x - pel.x) || 1;
  const pos = [], si = [], sw = [], idx = [];
  for (let j = 0; j <= H; j++) {
    const f = j / H, y = top + (bot - top) * f, row = SP.rows[j];
    for (let i = 0; i <= R; i++) {
      // радиус — по профилю ткани (центр сектора), мягкие складки по низу
      const a = i / R * Math.PI * 2, ai = (i + R - .5) % R, r0 = row[Math.floor(ai)] * (1 - ai % 1) + row[(Math.floor(ai) + 1) % R] * (ai % 1);
      const r = r0 * (1 + .045 * f * Math.sin(a * 9 + .6) + .018 * f * Math.sin(a * 23));
      const w = new T.Vector3(base.x + Math.sin(a) * r * k, base.y + y * k, base.z + (SP.cz + Math.cos(a) * r) * k); par.worldToLocal(w); pos.push(w.x, w.y, w.z);
      const g = Math.pow(f, 1.3) * .75, l = T.MathUtils.smoothstep(Math.sin(a) * side, -.7, .7);
      si.push(0, 1, 2, 0); sw.push(1 - g, g * l, g * (1 - l), 0);
    }
  }
  for (let j = 0; j < H; j++) for (let i = 0; i < R; i++) { const a = j * (R + 1) + i, b = a + R + 1; idx.push(a, b, a + 1, b, b + 1, a + 1); }
  const geo = new T.BufferGeometry(); geo.setAttribute('position', new T.Float32BufferAttribute(pos, 3)); geo.setAttribute('aB', new T.Float32BufferAttribute(pos, 3)); geo.setIndex(idx);
  geo.setAttribute('uv', new T.Float32BufferAttribute(new Float32Array(pos.length / 3 * 2), 2));
  geo.setAttribute('skinIndex', new T.Uint16BufferAttribute(si, 4)); geo.setAttribute('skinWeight', new T.Float32BufferAttribute(sw, 4)); geo.computeVertexNormals();
  const m = new T.SkinnedMesh(geo, bodyMat({ map: C.clothMat.map }, Object.assign({}, C.U, { uSkirt: { value: 0 } }), true, T.DoubleSide)); m.frustumCulled = false; par.add(m); m.updateWorldMatrix(true, false);
  m.bind(new T.Skeleton(['pelvis', 'thigh_l', 'thigh_r'].map(n => C.bone[n])));
  C.skirt = m;
}

const SAC = window.__SAC || .02, _black = new T.Color(0);
// брови двигаются (в осях модели, до скелета): x — поднять обе, y — «домиком» (+, тревога, мольба) или сведены (−, злость).
// У переносицы ход больше, у виска почти ноль — так и выглядит живое лицо
function browMat(C, map, color) {
  const m = ps1(new T.MeshLambertMaterial({ map, color, alphaTest: .45 })), snap = m.onBeforeCompile; C.browU = { value: new T.Vector2() };
  m.onBeforeCompile = sh => { snap(sh); sh.uniforms.uBrow = C.browU;
    sh.vertexShader = 'uniform vec2 uBrow;\n' + sh.vertexShader.replace('#include <begin_vertex>', `#include <begin_vertex>
      { float inr = 1. - smoothstep(.012, .06, abs(position.x));
        transformed.y += uBrow.x * .009 + uBrow.y * .0085 * inr - max(-uBrow.y, 0.) * .0035 * (1. - inr);
        transformed.x -= sign(position.x) * max(-uBrow.y, 0.) * .0035 * inr; transformed.z -= uBrow.x * .0015; }`); };
  return m;
}
/* ---------- позы ---------- */
const POSE = {
  stand: 'Idle_Loop', talk: 'Idle_Talking_Loop', sit: 'Sitting_Idle_Loop', sittalk: 'Sitting_Talking_Loop', sitfloor: 'Fixing_Kneeling',
  knock: 'Idle_Loop', give: 'Idle_Loop', paper: 'Idle_Loop', monster: 'Idle_Loop', reach: 'Idle_Loop',
  walk: 'Walk_Loop', back: 'Walk_Loop', push: 'Push_Loop', hug: 'Idle_Loop', eat: 'Crouch_Idle_Loop', lay: 'LayToIdle', mop: 'Idle_Loop', slam: 'Idle_Loop',
};
const _ld = new T.Vector3(), _a = new T.Vector3(), _b = new T.Vector3(), _c = new T.Vector3(), _q = new T.Quaternion(), _q2 = new T.Quaternion(), _pq = new T.Quaternion();
// повернуть кость так, чтобы она смотрела в направлении dir (в осях персонажа: +z вперёд, +y вверх, +x влево)
function aimBone(C, name, child, dir, k) {
  const b = C.bone[name], c = C.bone[child]; if (!b || !c) return;
  b.updateWorldMatrix(true, true);
  b.getWorldPosition(_a); c.getWorldPosition(_b); _b.sub(_a).normalize();
  C.root.getWorldQuaternion(_q2); _c.set(dir[0], dir[1], dir[2]).normalize().applyQuaternion(_q2);
  _q.setFromUnitVectors(_b, _c);
  _q.slerp(_pq.identity(), 1 - k);
  b.parent.getWorldQuaternion(_pq);
  const bw = b.getWorldQuaternion(new T.Quaternion()); bw.premultiply(_q);
  b.quaternion.copy(_pq.invert().multiply(bw));
}
// две кости (плечо-предплечье, бедро-голень) ставят конец точно в цель (в осях персонажа, метры от его ног); колено/локоть смотрят в pole
const _S = new T.Vector3(), _E = new T.Vector3(), _H = new T.Vector3(), _D = new T.Vector3(), _P = new T.Vector3(), _Q3 = new T.Quaternion();
function ik2(C, up, lo, end, tx, ty, tz, pole, k = 1) {
  const U = C.bone[up], L = C.bone[lo], N = C.bone[end]; if (!U || !L || !N) return;
  U.updateWorldMatrix(true, true);
  const loc = (b, v) => C.root.worldToLocal(b.getWorldPosition(v));
  loc(U, _S); loc(L, _E); loc(N, _H);
  const a = _S.distanceTo(_E), b = _E.distanceTo(_H);
  _D.set(tx, ty, tz).sub(_S); if (k < 1) _D.lerp(_H.clone().sub(_S), 1 - k);
  const d = clamp(_D.length(), Math.abs(a - b) + 1e-3, a + b - 1e-3); _D.normalize();
  const along = (a * a + d * d - b * b) / (2 * d), h = Math.sqrt(Math.max(0, a * a - along * along));
  _P.set(pole[0], pole[1], pole[2]).addScaledVector(_D, -_P.set(pole[0], pole[1], pole[2]).dot(_D)).normalize();
  const ex = _S.x + _D.x * along + _P.x * h, ey = _S.y + _D.y * along + _P.y * h, ez = _S.z + _D.z * along + _P.z * h;
  aimBone(C, up, lo, [ex - _S.x, ey - _S.y, ez - _S.z], 1);
  aimBone(C, lo, end, [_S.x + _D.x * d - ex, _S.y + _D.y * d - ey, _S.z + _D.z * d - ez], 1);
}
// ступня сохраняет поворот в мире (после поправки ноги подошва остаётся на полу)
function keepWorld(C, name, q) { const b = C.bone[name]; b.parent.getWorldQuaternion(_Q3); b.quaternion.copy(_Q3.invert().multiply(q)); b.updateWorldMatrix(false, true); }
// открытая ладонь: пальцы возвращаются в исходную прямую позу (ладонь на стекле)
function palm(C, s) {
  for (const f of FING) for (let j = 1; j <= 3; j++) { const n = f + '_0' + j + '_' + s, b = C.bone[n]; if (b && C.rest[n]) b.quaternion.copy(C.rest[n]); }
  for (let j = 1; j <= 3; j++) { const n = 'thumb_0' + j + '_' + s, b = C.bone[n]; if (b && C.rest[n]) b.quaternion.slerp(C.rest[n], .7); }
}
// пальцы в кулак (k = 0..1)
const FING = ['index', 'middle', 'ring', 'pinky'];
function fist(C, s, k) {
  const ax = window.__FAX || [1, 0, 0], sg = (window.__FSG || 1) * (s === 'l' ? (window.__FSL || 1) : 1);
  for (const f of FING) for (let j = 1; j <= 3; j++) { const b = C.bone[f + '_0' + j + '_' + s]; if (!b) continue; _Q3.setFromAxisAngle(_D.set(ax[0], ax[1], ax[2]), sg * k * (j === 1 ? 1.25 : 1.45)); b.quaternion.multiply(_Q3); }
  const th = C.bone['thumb_02_' + s]; if (th) { _Q3.setFromAxisAngle(_D.set(ax[0], ax[1], ax[2]), sg * k * .6); th.quaternion.multiply(_Q3); }
}
function updateSkinned(C, t, dt) {
  const tt = t + C.t0, P = C.pose, U = C.U;
  if (C.talk > .06) C.talkT = tt + .7;   // паузы между словами не сбивают анимацию «говорит»
  const talking = tt < (C.talkT || -1);
  let want = POSE[P] || 'Idle_Loop';
  if (talking && P === 'stand') want = POSE.talk;
  if (talking && P === 'sit') want = POSE.sittalk;
  if (C.o.monster && P === 'walk') want = 'Zombie_Walk_Fwd_Loop';
  if (want !== C.clip) {
    const a = C.mixer.clipAction(CLIP[want] || CLIP.Idle_Loop);
    a.reset();
    if (C.act && dt < .2) { a.play(); C.act.crossFadeTo(a, .35, false); } else { C.mixer.stopAllAction(); a.play(); }
    if (!C.act) a.time = (C.t0 * 1.7) % (a.getClip().duration || 1);
    if (P === 'lay') { C.mixer.stopAllAction(); a.play(); a.time = 0; a.timeScale = 0; }   // тело: первый кадр «лёжа», без движения
    C.act = a; C.clip = want;
  }
  // Жилец двигается рывками, как покадровая анимация: поза меняется ~7 раз в секунду
  let sdt = dt, still = false;
  if (C.o.stopmo) { C.smAcc = (C.smAcc || 0) + dt; if (C.smAcc < C.o.stopmo && C.smDone) still = true; else { sdt = C.smAcc; C.smAcc = 0; C.smDone = true; } }
  if (!still) {
  // шаг под скорость: ноги идут ровно с той скоростью, с какой человек движется по полу (иначе «плывёт» или шагает на месте)
  let stride = 1;
  if ((P === 'walk' || P === 'back') && !C.o.monster) {
    const p = C.root.position, base = 1.03 * C.inner.scale.y;
    if (C.prevP && dt > 0) { const v = Math.hypot(p.x - C.prevP.x, p.z - C.prevP.z) / dt; C.walkV = C.walkV == null ? v : C.walkV + (v - C.walkV) * Math.min(1, dt * 8); }
    (C.prevP || (C.prevP = new T.Vector3())).copy(p);
    stride = C.walkV == null ? 1 : clamp(C.walkV / base, 0, 1.9);
  }
  if (P !== 'lay') C.act.timeScale = P === 'back' ? -stride : P === 'monster' ? .3 : P === 'reach' ? .55 : C.o.monster ? .8 : stride;
  // пальцы — в исходную позу до анимации: клип их почти не трогает, и кулак от швабры/стука иначе оставался навсегда
  for (const sd of ['l', 'r']) { for (const f of FING) for (let j = 1; j <= 3; j++) { const n = f + '_0' + j + '_' + sd; if (C.bone[n]) C.bone[n].quaternion.copy(C.rest[n]); }
    for (let j = 1; j <= 3; j++) { const n = 'thumb_0' + j + '_' + sd; if (C.bone[n]) C.bone[n].quaternion.copy(C.rest[n]); } }
  C.mixer.update(Math.min(sdt, .2));
  // ноги: у базовой анимации стойка «борца» (ступни широко) — сводим ступни под бёдра, подошва остаётся на полу
  const STAT = { stand: 1, talk: 1, knock: 1, give: 1, paper: 1, reach: 1, hug: 1, mop: 1, slam: 1 };
  if (STAT[P] && !C.o.monster) {
    const ss = C.inner.scale.y;
    for (const [sd, sx] of [['l', 1], ['r', -1]]) {
      const ft = C.bone['foot_' + sd]; ft.updateWorldMatrix(true, false); const fq = ft.getWorldQuaternion(new T.Quaternion());
      const fp = C.root.worldToLocal(ft.getWorldPosition(new T.Vector3()));
      const want = sx * (C.o.fem ? .085 : .12) * ss + (P === 'knock' ? sx * .015 : 0);
      ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, lerp(fp.x, want, .85), fp.y, fp.z + (sd === 'r' && P === 'knock' ? -.08 * ss : 0), [sx * .15, 0, 1]);
      keepWorld(C, 'foot_' + sd, fq);
    }
  }
  // руки поверх анимации
  if (P === 'knock' || P === 'slam') {
    // стук в стекло: левая ладонь лежит на стекле, правая рука кулаком стучит сериями по три удара, корпус чуть к стеклу
    const ss = C.inner.scale.y, gz = (C.glassD ?? .42), ph = tt % 1.9;
    let hit = 0; for (const p0 of [0, .24, .48]) hit = Math.max(hit, Math.exp(-Math.pow((ph - p0 - .05) / .055, 2)));
    if (P === 'slam') hit = Math.min(1, .5 + .5 * Math.sin(tt * 2.3));
    aimBone(C, 'spine_02', 'spine_03', [0, 1, .14], .7);
    if (P === 'slam') {   // обе ладони на стекле
      ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', .22 * ss, 1.5 * ss, gz - .05, [.8, -1, -.3]); palm(C, 'l'); aimBone(C, 'hand_l', 'middle_01_l', [.2, 1, .3], 1);
      ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', -.22 * ss, 1.47 * ss, gz - .05 - hit * .03, [-.8, -1, -.3]); palm(C, 'r'); aimBone(C, 'hand_r', 'middle_01_r', [-.2, 1, .3], 1);
    } else {
      ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', .21 * ss, 1.36 * ss, gz - .05, [.8, -1, -.3]);
      palm(C, 'l'); aimBone(C, 'hand_l', 'middle_01_l', [.15, 1, .3], 1);   // ладонь прижата к стеклу, пальцы вверх
      ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', -.17 * ss, 1.44 * ss, gz - .16 + hit * .12, [-.9, -1, -.4]);
      aimBone(C, 'hand_r', 'middle_01_r', [-.1, .55, 1], .8);
      fist(C, 'r', 1);
    }
    C.knockHit = hit;
  } else if (P === 'mop') {
    // моет пол шваброй: корпус наклонён, руки на черенке, швабра ходит дугой перед ногами
    const ss = C.inner.scale.y, sw = Math.sin(tt * 2.1), sw2 = Math.cos(tt * 2.1);
    aimBone(C, 'spine_01', 'spine_02', [sw * .12, 1, .32], .8); aimBone(C, 'spine_02', 'spine_03', [sw * .1, 1, .25], .7);
    const hx = sw * .45, hz = .78 + sw2 * .07;   // где тряпка на полу
    const D = new T.Vector3(-.1 - hx, 1.1 * ss - .02, .22 - hz).normalize();   // черенок от тряпки к правой руке
    const top = [hx + D.x * 1.22, .02 + D.y * 1.22, hz + D.z * 1.22], mid = [hx + D.x * .8, .02 + D.y * .8, hz + D.z * .8];
    ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', top[0], top[1], top[2], [-.8, -.6, -.4]);
    ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', mid[0], mid[1], mid[2], [.8, -.8, -.2]);
    fist(C, 'r', .9); fist(C, 'l', .9);
    if (C.mop) { C.mop.visible = true; const g = C.mop; g.position.set(hx, .02, hz);
      const d = new T.Vector3(top[0] - hx, top[1] - .02, top[2] - hz).normalize(); g.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), d); }
  } else if (P === 'give') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.1, -.6, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [0, -.1, 1], 1); fist(C, 'r', .55);
  } else if (P === 'paper') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.1, -.85, .45], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.45, .35, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.1, -.85, .45], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.45, .35, 1], 1);
  } else if (P === 'reach') {
    const sw = Math.sin(tt * 1.3) * .08;
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.15, .05 + sw, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [-.05, .1, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.15, .05 - sw, 1], 1); aimBone(C, 'lowerarm_l', 'hand_l', [.05, .1, 1], 1);
  } else if (P === 'eat') {
    // рвёт и тащит ко рту: руки к земле перед собой, рывками вверх
    const tr = Math.max(0, Math.sin(tt * 4.2)) ** 5, tl = Math.max(0, Math.sin(tt * 4.2 + 2.1)) ** 5;
    const bite = Math.pow(Math.max(0, Math.sin(tt * 2.6)), 6), yank = Math.pow(Math.max(0, Math.sin(tt * 2.6 - .9)), 8);   // клюёт в тело и рывком отдирает кусок
    aimBone(C, 'spine_02', 'spine_03', [Math.sin(tt * 7) * .08 * yank, .55 - bite * .35 + yank * .3, 1], .8);
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.2, -.75, .75], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.1, -.75 + tr * 1.1, .7 - tr * .3], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.2, -.75, .75], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.1, -.75 + tl * 1.1, .7 - tl * .3], 1);
  } else if (P === 'hug') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [.2, -.1, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.9, 0, .4], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [-.2, -.1, 1], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.9, 0, .4], 1);
  }
  // голова смотрит на цель (обычно в камеру): доворачиваем «нос» головы к цели поверх анимации, поровну шее и голове
  const k = Math.min(1, sdt * (C.o.stopmo ? 3 : 7)), hb = C.bone.Head;
  hb.updateWorldMatrix(true, false); hb.getWorldPosition(_a);
  C.root.getWorldQuaternion(_q2);
  if (C.look) _b.copy(C.look).sub(_a).normalize();
  else _b.set(Math.sin(tt * .4) * .06, -.05, 1).normalize().applyQuaternion(_q2);
  if (C.o.jerk && !C.noTilt && Math.sin(tt * 1.3) > .985) _b.x += (rnd() - .5) * .8;
  // не дальше, чем позволяет шея: ограничиваем угол от направления тела
  _c.set(0, 0, 1).applyQuaternion(_q2);
  const ang = _b.angleTo(_c), hm = C.o.headMax || 1.15; if (ang > hm) { _q.setFromUnitVectors(_c, _b); _q.slerp(_pq.identity(), 1 - hm / ang); _b.copy(_c).applyQuaternion(_q); }
  C.lookDir = C.lookDir ? C.lookDir.lerp(_b, k).normalize() : _b.clone();
  // при разговоре голова чуть кивает и покачивается, а не стоит как у манекена
  const LD = _ld.copy(C.lookDir);
  if (talking && !C.o.monster) { LD.y += Math.sin(tt * 3.1) * .035 + Math.sin(tt * 1.3) * .02; LD.x += Math.sin(tt * .9) * .025; LD.normalize(); }
  for (const [bn, f] of [['neck_01', .45], ['Head', .9]]) {
    const b = C.bone[bn]; b.updateWorldMatrix(true, false);
    const bw = b.getWorldQuaternion(new T.Quaternion());
    const cur = C.headFwd.clone().applyQuaternion(hb.getWorldQuaternion(new T.Quaternion()));
    _q.setFromUnitVectors(cur, LD); _q.slerp(_pq.identity(), 1 - f);
    // Жилец: голова набок, и время от времени шея резко «ломается» почти горизонтально (на скримере — прямо, лицом в лицо)
    if (C.o.tilt && bn === 'Head' && !C.noTilt) { const snap = C.o.jerk && P !== 'eat' ? Math.pow(Math.max(0, Math.sin(tt * .9 + 1)), 24) * 1.1 : 0; const r = new T.Quaternion().setFromAxisAngle(LD, (Math.sin(tt * .3) * .05 + C.o.tilt + snap) * (C.o.flip ? -1 : 1)); _q.premultiply(r); }
    bw.premultiply(_q); b.parent.getWorldQuaternion(_pq); b.quaternion.copy(_pq.invert().multiply(bw)); _pq.identity();
    b.updateWorldMatrix(false, true);
  }
  }
  if (C.strands) C.strands.forEach((st, i) => { st.rotation.x = .08 + Math.sin(tt * 1.1 + i) * .05; st.rotation.z = Math.sin(tt * .8 + i * 2) * .04; });
  // лицо
  C.blinkT -= dt; let blink = 0; if (C.blinkT < 0) { blink = 1; if (C.blinkT < -.12) C.blinkT = 2 + rnd() * 4; }
  const fs = C.faceSets[C.faceSet] || {};
  if (C.o.monster || C.o.noBlink || fs.wide) blink = 0;
  if (C.o.dead) blink = 1;   // Жилец и «Катя» не моргают — это улика
  U.uBlink.value = blink;
  // моргание: глазное яблоко на миг становится цветом кожи — выглядит как закрытое веко, без дыры в глазнице
  if (C.eyes && C.eyes.material.emissive) { const em = C.eyes.material; if (!C.lidC) C.lidC = new T.Color(C.o.skin ?? 0xffe0d0).multiply(new T.Color(0xa88a7a)).multiplyScalar((C.o.skinK || 1) * .4);
    if (blink !== C.blinked) { C.blinked = blink; em.color.setScalar(blink ? 0 : 1); if (C.o.eyeC != null && !blink) em.color.set(C.o.eyeC); em.emissive.copy(blink ? C.lidC : _black); } }
  // глаза живые: мелкие быстрые скачки взгляда (саккады), у Жильца и «Кати» — нет, взгляд застывший
  if (C.eyes && C.eyes.material.map && !C.o.monster && !C.o.noBlink && !C.o.dead) {
    const dart = fs.dart ? 2.2 : 1;   // тревога: взгляд бегает чаще и шире
    C.sacT = (C.sacT ?? 1) - dt; if (C.sacT < 0) { C.sacT = (.4 + rnd() * 1.8) / (dart * dart); C.sac = [(rnd() - .5) * SAC * dart, (rnd() - .5) * SAC * .5 * dart]; }
    const m = C.eyes.material.map.offset, tg = C.sac || [0, 0], kk = Math.min(1, dt * 30); m.x += (tg[0] - m.x) * kk; m.y += (tg[1] - m.y) * kk;
  }
  // у «Кати» рот чуть опаздывает за голосом — едва заметно, но тревожно (как плохой дубляж)
  let tk = C.talk;
  if (C.o.lipLag) { const h = C.talkHist || (C.talkHist = []); h.push([tt, C.talk]); while (h.length > 2 && h[1][0] < tt - C.o.lipLag) h.shift(); tk = h[0][1]; }
  { const tg = clamp(Math.max(tk * 1.15, fs.mouth || 0), 0, .9), cur = U.uTalk.value; U.uTalk.value = lerp(cur, tg, Math.min(1, dt * (tg > cur ? 22 : 9))); }
  U.uSmile.value = C.o.smile ? 1 : fs.smile ? +fs.smile : 0;
  // брови: эмоция кадра + подъём на ударных слогах + редкое «движение мысли» (у Жильца и подделки — ничего, лицо застывшее)
  if (C.browU) {
    let up = fs.browUp || 0, inn = fs.browIn || 0;
    if (!C.o.noBlink && !C.o.dead) {
      up += clamp((tk - .45) * 1.5, 0, .6);
      C.brT = (C.brT ?? 2 + rnd() * 4) - dt; if (C.brT < 0) { C.brT = 3 + rnd() * 5; C.brF = 1; }
      if (C.brF > 0) { up += Math.sin((1 - C.brF) * Math.PI) * .45; C.brF -= dt * 2.2; }
    }
    const B = C.browU.value, kb = Math.min(1, dt * 12); B.x += (up - B.x) * kb; B.y += (inn - B.y) * kb;
  }
  U.uScream.value = C.scream || fs.scream || 0;
  U.uGore.value = C.gore || 0;
  C.keys.visible = P === 'give'; C.paper.visible = P === 'paper'; if (C.mop && P !== 'mop') C.mop.visible = false;
}

/* ---------- актёры «Ночной смены» ---------- */
function cast() {
  const A = {};
  const guard = { shirt: 0x1c1f26, pants: 0x15171c, shoes: 0x0c0c0c, trim: 0x3a3f4a, badge: true };
  A.temych = makeSkinned(Object.assign({ name: 'temych', h: 1.8, skin: 0xffe8dc, skinK: 1.25, hairPart: 'Hair_SimpleParted', hair: 0x3a2a1c, brow: 0x2a1c12 }, guard));
  A.smenshik = makeSkinned(Object.assign({ name: 'smenshik', h: 1.72, skin: 0xf0d6c8, skinK: 1.2, hairPart: 'Hair_Buzzed', hair: 0x9a9a96, brow: 0x8a8a86, cap: 0x15203a }, guard, { shirt: 0x1d2a44, pants: 0x1a2236 }));
  A.smenshik.addFace('smile', { smile: true, browUp: .3 });
  A.smenshik.addFace('fear', { mouth: .45, wide: true, browUp: 1, browIn: .8, dart: 1 });
  A.temych.addFace('smile', { smile: 1.45, wide: true, browUp: .5 });
  const kat = { fem: true, h: 1.64, skin: 0xfff0ea, skinK: 1.3, hairPart: 'Hair_Long', hair: 0x4a3226, brow: 0x1a110c, coat: 0xd8a820, coatY: .5, pants: 0x223050, shoes: 0x1a1414, trim: 0x8a6a10, wet: true };
  A.katya = makeSkinned(Object.assign({ name: 'katya', noBlink: true, lipLag: .12 }, kat));
  A.katyaSmile = makeSkinned(Object.assign({ name: 'katyaSmile', lipLag: .22 }, kat, { skin: 0xe6dcd8, skinK: 1.25, grey: .5, smile: true, noBlink: true, eyeC: 0x3a3a3a, socket: .45 }));
  A.mihalych = makeSkinned(Object.assign({ name: 'mihalych', h: 1.76, skin: 0xead0c0, skinK: 1.2, hairPart: 'Hair_SimpleParted', hair: 0xc8c8c2, brow: 0xb8b8b2, beard: true, beardC: 0xd0d0ca, glasses: true }, guard, { shirt: 0x3a3e40, pants: 0x2a2d30, trim: 0x55595c }));
  // Жилец: выше двери, узкий, длинные шея и руки, маленькая голова; лицо почти нормальное — и от этого хуже
  A.monster = makeSkinned({ name: 'monster', monster: true, naked: true, h: 2.45, thin: .52, arm: 1.2, armL: 1.12, handS: 1.3, neck: 1.22, headS: .9, skin: 0xb8bcb4, grey: 1, hairPart: 'Hair_Long', hair: 0x050505, deadEyes: 0x000000, socket: .85, tilt: .45, jerk: true, veil: true, headMax: 2.3, stopmo: 1 / 7 });
  A.monster.addFace('grin', { smile: 1.1, mouth: .55 });
  A.monster.addFace('scream', { scream: 1 });
  // тело ночного уборщика (сцена, где Жилец ест)
  const clean = { kind: 2, h: 1.75, skin: 0xe8cbb8, skinK: 1.15, hairPart: 'Hair_Buzzed', hair: 0x2a2420, brow: 0x2a2420, shirt: 0x2f5378, pants: 0x27405c, shoes: 0x111111, trim: 0x8a2020, cap: 0x2f5378 };
  // ночной уборщик: сначала живой, моет пол в коридоре; потом — тело (сцена, где Жилец ест)
  A.cleaner = makeSkinned(Object.assign({ name: 'cleaner', mop: true }, clean, { cap: null }));
  // общие выражения лица: тревога (брови домиком, взгляд бегает), мольба, настороженность, страх, спокойная полуулыбка
  for (const k of ['temych', 'smenshik', 'katya', 'mihalych', 'cleaner']) {
    const F = A[k].faceSets;
    F.worry = F.worry || { browIn: .8, browUp: .1, dart: 1 };
    F.plead = F.plead || { browIn: 1, browUp: .35, mouth: .12 };
    F.alert = F.alert || { browUp: .9, wide: true, dart: 1 };
    F.fear = F.fear || { mouth: .4, wide: true, browUp: 1, browIn: .8, dart: 1 };
    F.calm = F.calm || { smile: .3, browUp: .1 };
  }
  A.victim = makeSkinned(Object.assign({ name: 'victim', dead: true }, clean, { skin: 0xc8c4bc, skinK: 1.0, grey: .7, cap: null }));
  for (const k in A) A[k].update(0, 1 / 30);
  return A;
}

Object.assign(window.__W3chars, { load, makeSkinned });
})();
