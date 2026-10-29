/* Персонажи: настоящие 3D-люди со скелетом и анимациями.
   v3 (основные): люди MakeHuman (CC0) — models/<имя>.glb, собираются tools/chars/build.sh: кожа, глаза, волосы, одежда
   и 53 лицевые формы (моргание, взгляд, брови, улыбка, страх, виземы речи). Клипы движений — из models/male.glb (Quaternius, CC0).
   v2 (запасные, если v3 не загрузились): модели Quaternius, одежда шейдером по телу.
   Одежду рисуем шейдером прямо по телу (рубашка, брюки, плащ, ботинки по зонам тела), лицо живое: рот открывается по голосу,
   глаза моргают, у Жильца пустые глаза и улыбка до ушей. Анимации: стоять, говорить, сидеть, идти, пятиться, зомби-стойка;
   руки для «стучит», «отдаёт ключи», «держит листок», «тянется» доворачиваются кодом поверх анимации.
   Снаружи тот же интерфейс, что у старых персонажей (chars.js): { root, pose, look, talk, faceSet, update(t, dt) }.
   Если модели не загрузились, остаются старые персонажи из блоков. */
(() => {
'use strict';
const tr = window.tr || (s => s);
const { T, rnd, lerp, clamp, mat, box, cyl, SNAP, CINE, litMat } = window.__W3core;
const FILES = ['male', 'female', 'Hair_Beard', 'Hair_Long', 'Hair_SimpleParted', 'Hair_Buzzed'];
const G = {}, CLIP = {};
const HUMANS = ['ethan', 'frank', 'gus', 'dayguard', 'lily', 'tenant'];
let V3 = false, QW = null;   // v3 загрузились; мировые повороты костей Quaternius в покое

// XHR, а не fetch: так модели читаются и в Android-приложении (file://)
const get = url => new Promise((ok, no) => { const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = 'arraybuffer'; x.onload = () => (x.status === 200 || x.status === 0) && x.response ? ok(x.response) : no(); x.onerror = no; x.send(); });
async function load(onFile) {
  if (!window.THREE_GLTFLoader || !window.THREE_SkeletonUtils) return false;
  const L = new window.THREE_GLTFLoader();
  if (window.MeshoptDecoder) { try { await window.MeshoptDecoder.ready; L.setMeshoptDecoder(window.MeshoptDecoder); } catch (e) { console.warn('meshopt', e); } }
  // каждый файл — со второй попыткой: один сбой сети не должен откатывать всех людей к запасным
  const url = n => 'models/' + n + '.glb?v=' + (HUMANS.includes(n) ? 4 : n === 'anims2' ? 2 : 1);   // anims2 — поднимать при пересборке мокапа
  const fetch1 = n => get(url(n)).catch(() => get(url(n))), parse = (n, b) => new Promise((ok, no) => L.parse(b, '', g => { G[n] = g; if (onFile) onFile(); ok(); }, no));
  const one = n => fetch1(n).then(b => parse(n, b));
  const raw2 = window.MeshoptDecoder ? fetch1('anims2').catch(() => null) : null;   // мокап качается вместе с остальными, а не после них (на 4G — секунда-две раньше меню)
  // anims.glb — только скелет male.glb и 41 клип Quaternius UAL 1–2 (CC0), перенесённые tools/anim/retarget.py
  try { await Promise.all(['anims', ...HUMANS].map(one)); V3 = !!window.MeshoptDecoder; }
  catch (e) { console.warn('models v3', e); V3 = false; }
  // anims2.glb — живые движения с пальцами (мокап Rokoko и CMU, перенесён на этот же скелет: tools/anim/mocap.py); без него игра идёт на клипах Quaternius
  if (V3) { try { const b = await raw2; if (!b) throw new Error('no file'); await parse('anims2', b); } catch (e) { console.warn('anims2', e); } }
  if (!V3) { try { await Promise.all(FILES.filter(n => !G[n]).map(one)); } catch (e) { console.warn('models', e); return false; } }
  for (const c of [...(G.anims || G.male).animations, ...(G.anims2 ? G.anims2.animations : [])]) {
    // только повороты костей (+ высота таза): так одна анимация подходит всем — и мужчине, и женщине, и вытянутому Жильцу
    c.tracks = c.tracks.filter(t => t.name.endsWith('.quaternion') || t.name === 'pelvis.position');
    CLIP[c.name] = c;
  }
  if (V3) { const sk = G.anims.scene; sk.updateMatrixWorld(true); QW = {}; sk.traverse(n => { if (n.name) QW[n.name] = n.getWorldQuaternion(new T.Quaternion()); });
    QW.pelvisZ = sk.getObjectByName('pelvis').position.z; }
  window.__W3chars.cast = cast; window.__W3chars.v2 = true; window.__W3chars.v3 = V3;
  return true;
}

/* ---------- материал тела: одежда по зонам + лицо ---------- */
// зоны считаются по точке тела в позе «Т» (метры, рост мужской модели 1.81): так одежда не съезжает при движении
const BODY_FS = `
uniform vec3 uShirt, uPants, uShoes, uSkin, uCoat, uTrim; uniform sampler2D uDecal; uniform float uSkirt, uKind, uFlat, uS, uCoatY, uTalk, uBlink, uSmile, uNaked, uBadge, uFem, uWet, uGrey, uGore, uScream, uSocket;
uniform vec2 uMouth, uEyeL, uEyeR;
varying vec3 vB;
float gH = 0., gR = -1.;   // «Кино»: микрорельеф (поры, волокна ткани, вены) и шероховатость точки — их подхватывает свет
vec3 bumpN(vec3 sp, vec3 sn, float hh) {
  vec2 dh = vec2(dFdx(hh), dFdy(hh)); vec3 sx = dFdx(sp), sy = dFdy(sp), r1 = cross(sy, sn), r2 = cross(sn, sx);
  float det = dot(sx, r1); vec3 nn = abs(det) * sn - sign(det) * (dh.x * r1 + dh.y * r2); return dot(nn, nn) > 1e-24 ? normalize(nn) : sn; }   // нулевой вектор → NaN (чёрные точки)
float h3(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float vn(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
  return mix(mix(mix(h3(i), h3(i + vec3(1., 0., 0.)), f.x), mix(h3(i + vec3(0., 1., 0.)), h3(i + vec3(1., 1., 0.)), f.x), f.y),
             mix(mix(h3(i + vec3(0., 0., 1.)), h3(i + vec3(1., 0., 1.)), f.x), mix(h3(i + vec3(0., 1., 1.)), h3(i + vec3(1., 1., 1.)), f.x), f.y), f.z); }
// переплетение ткани (период ~2 мм) и ворс: когда узор мельче пикселя, он гаснет — иначе вдали рябь, муар и «лесенка» на свитерах и куртках
float weaveH() { float pw = length(fwidth(vB));
  return ((sin(vB.x * 2600.) * sin(vB.y * 2600.) * .5 + .5) * .5 - .25) * (1. - smoothstep(.5, 1.5, pw * 2600.)) + .25 + (vn(vB * 300.) - .5) * .5 * (1. - smoothstep(.5, 1.5, pw * 300.)) + .25; }
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
    float u = ax, th = atan(p.z + .068, p.y - 1.455 + 1e-5);   // u — вдоль руки (плечо .215 → запястье .665), th: 0 — сверху, ±π — снизу
    float eq = (u - .45) / .05, el = exp(-eq * eq);   // не pow(x, 2.): у отрицательного x на видеокартах pow — NaN (чёрные точки)
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
    sh *= 1. - .14 * exp(-((p.y - .52) / .05) * ((p.y - .52) / .05)) * smoothstep(.2, .9, sin(p.y * 140. + sin(ax * 60.) * 2.));   // колени
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
      if (p.y < .98) sh *= 1. - .12 * smoothstep(.5, 1., sin(atan(p.x, p.z + .04 + 1e-5) * 9. + mo * 2.)) * smoothstep(.95, .6, p.y);   // складки полы
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
    // мёртвая бледная кожа: синеватые вены просвечивают, трупные пятна, гниль, мокрая грязь; рёбра; тело тонет в темноте
    float ax0 = abs(p.x), mo1 = vn(p * 7.), mo2 = vn(p * 26. + 3.);
    float vv = pow(max(0., 1. - abs(vn(p * 34.) * 2. - 1.)), 18.) * smoothstep(.25, .7, vn(p * 3.5 + 7.)) + pow(max(0., 1. - abs(vn(p * 13. + 9.) * 2. - 1.)), 30.) * .8;
    float vf = pow(max(0., 1. - abs(vn(p * 42. + 5.) * 2. - 1.)), 14.) * .6;
    vec3 sc = vec3(.66, .68, .63) * (.78 + .3 * mo2);
    sc = mix(sc, vec3(.2, .26, .36), clamp(vv * 1.3 + vf * smoothstep(1.4, 1.8, p.y), 0., 1.) * .85);
    sc = mix(sc, vec3(.4, .27, .36), smoothstep(.6, .85, mo1) * .55);
    sc = mix(sc, vec3(.28, .3, .2), smoothstep(.62, .9, vn(p * 3. + 11.)) * .45);
    skin = sc * (.5 + .7 * lum) * uSkin;
    if (p.y > 1.1 && p.y < 1.45 && ax0 > .04 && ax0 < .16) skin *= 1. - .35 * smoothstep(.55, .85, sin(p.y * 120. + ax0 * 25. + sin(ax0 * 60.)) * .5 + .5) * smoothstep(.04, .07, ax0);
    skin *= mix(.3, 1., smoothstep(1.5, 1.72, p.y));
    gH = vv * 1.2 + vf * .6 + mo2 * .35 + vn(p * 140.) * .25; gR = mix(.62, .32, smoothstep(.55, .8, vn(p * 5. + 2.)));   // кожа сухая, местами мокрая
  }
  vec4 cl = cloth(p, lum);
  vec3 col = mix(skin, cl.rgb, step(.2, cl.w));
  if (uNaked < .5) { if (cl.w > .2) { gH = weaveH(); gR = .86; }
    else { float pw = length(fwidth(vB)); gH = (vn(vB * 600.) - .5) * .7 * (1. - smoothstep(.5, 1.5, pw * 600.)) + vn(vB * 90.) * .3; gR = .5 + .14 * vn(vB * 40.);
      // живая кожа: ночная смена — тени под глазами, кожа чуть менее «игрушечно» насыщенная, неровный тон
      vec2 uv0 = vMapUv; float eb = min(length((uv0 - uEyeL - vec2(0., .02)) / vec2(.03, .013)), length((uv0 - uEyeR - vec2(0., .02)) / vec2(.03, .013)));
      col *= mix(vec3(.8, .74, .78), vec3(1.), smoothstep(.35, 1., eb)) * (.94 + .1 * vn(vB * 25.));
      col = mix(vec3(dot(col, vec3(.3, .59, .11))), col, .82); } }
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
  if (uSmile > 1.2 && mr < 1. && md.y < -.2 && md.y > -.65) col = vec3(.62, .58, .5) * (.75 + .25 * step(.5, fract(md.x * 7.)));
  if (uNaked > .5 && uSmile > .5 && mr < 1.) {   // Жилец: оскал — частокол тонких жёлтых зубов-игл в чёрной дёсне, кровь в углах
    float tx = abs(fract(md.x * 13.) - .5) * 2.;
    col = vec3(.03, .005, .005);
    if (md.y < .1 && tx < (md.y + .95) * 1.5) col = vec3(.6, .55, .4) * (.55 + .45 * (1. - tx));
    if (md.y > .1 && tx < (.95 - md.y) * 1.2) col = vec3(.52, .47, .34) * (.5 + .5 * (1. - tx));
    col = mix(col, vec3(.25, .02, .02), smoothstep(.55, .95, abs(md.x)) * .8); gR = .1;
  }   // зубы (только у оскала; улыбка «Кати» — тёмная щель без зубов)
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
  // Жилец: из глазниц текут чёрные слёзы — мокрые, блестят
  if (uNaked > .5) for (int e = 0; e < 2; e++) {
    vec2 ec = e == 0 ? uEyeL : uEyeR, d = uv - ec; float len = e == 0 ? .075 : .058;
    float w = .0045 * (1. - d.y / len) + .0012, x0 = sin(d.y * 140. + float(e) * 2.) * .0025;
    if (d.y > .004 && d.y < len && abs(d.x - x0) < w) { col = mix(col, vec3(.01, .008, .008), .92); gR = .05; gH += .5; }
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
function bodyMat(src, U, shell, side, rough = .62) {
  const m = litMat({ map: src.map, side: side || T.FrontSide }, shell ? .78 : rough);
  m.onBeforeCompile = sh => {
    Object.assign(sh.uniforms, U); sh.uniforms.uSnap = SNAP;
    sh.vertexShader = 'uniform vec2 uSnap; uniform float uS, uCoatY, uNaked, uFlat, uThin, uScream;\nvarying vec3 vB;\n' + (shell ? 'attribute vec3 aB;\n' : '') + sh.vertexShader
      .replace('#include <begin_vertex>', '#include <begin_vertex>\n' + (shell ? ' vB = aB;' : ' vB = position;' + FLAT))
      .replace('#include <beginnormal_vertex>', '#include <beginnormal_vertex>\n' + (shell ? '' : ' if (uFlat > .5) { vec3 q = position / uS; if (q.y > 1.12 && q.y < 1.48 && q.z > .02) objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .5, objectNormal.y * .3, 1.), .75)); if (q.y > .76 && q.y < 1.1 && q.z < -.05) objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .5, 0., -1.), .7)); }'))
      .replace('#include <project_vertex>', SNAPV);
    sh.fragmentShader = BODY_FS + sh.fragmentShader.replace('#include <map_fragment>', shell
      ? '#include <map_fragment>\n{ vec3 q = vB / uS; vec4 cl = cloth(q, dot(diffuseColor.rgb, vec3(.3, .59, .11))); if (cl.w < .5) discard; gH = weaveH(); gR = uKind > .5 && uKind < 1.5 ? .32 : .86; if (uSkirt > .5 && q.y < .985 && q.y > uCoatY - .01 && !(abs(q.x) > .215 && q.y > 1.32)) discard; diffuseColor.rgb = cl.rgb; }'
      : BODY_PAINT)
      .replace('#include <roughnessmap_fragment>', '#include <roughnessmap_fragment>\n  if (gR >= 0.) roughnessFactor = gR;')
      .replace('#include <normal_fragment_maps>', '#include <normal_fragment_maps>\n  normal = bumpN(-vViewPosition, normal, gH * (uNaked > .5 ? .0022 : .00016));')
      // блеск мокрого дождевика: светлый край по контуру и блик сверху (от ламп на потолке); у ткани — лёгкий бархатный край
      .replace('#include <emissivemap_fragment>', '#include <emissivemap_fragment>\n' + (shell ? `{ vec3 vd = normalize(vViewPosition); float fr = pow(max(0., 1. - abs(dot(normal, vd))), 3.);
        vec3 up = normalize((viewMatrix * vec4(0., 1., 0., 0.)).xyz); float top = pow(max(0., dot(normal, up)), 6.);
        float pvc = step(.5, uKind) * step(uKind, 1.5);
        totalEmissiveRadiance += diffuseColor.rgb * (fr * mix(.12, .55, pvc) + top * .45 * pvc) + vec3(.05, .05, .045) * fr * pvc; }` : ''));
  };
  m.customProgramCacheKey = () => (shell ? 'shell4' : 'body4') + (CINE ? 'c' : '');
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
  g.font = 'bold 52px Arial, Helvetica, sans-serif'; g.fillText(tr('ОХРАНА'), 128, 34, 240); g.font = 'bold 44px Arial, Helvetica, sans-serif'; g.fillText(tr('КЛИНИНГ'), 128, 96, 196);
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
  const m = new T.SkinnedMesh(sm.geometry, ps1(litMat(buzz ? { color, side: T.DoubleSide, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -6 } : { map: sm.material.map, color, alphaTest: .45, side: T.DoubleSide }, .55)));
  m.frustumCulled = false; if (CINE && !buzz) m.material.alphaToCoverage = true;   // «Кино»: края прядей сглаживает MSAA, а не рваный порог
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
      else if (nm.includes('eye')) { n.material = o.voidEyes || o.deadEyes != null ? ps1(CINE ? Object.assign(litMat({ color: o.deadEyes ?? 0x000000 }, .03), { envMapIntensity: .9 }) : new T.MeshBasicMaterial({ color: o.deadEyes ?? 0x000000 })) : ps1(litMat({ map: n.material.map && n.material.map.clone(), color: o.eyeC ?? 0xffffff }, .08)); if (n.material.map) n.material.map.needsUpdate = true; C.eyes = n; }
      else { C.U = uniforms(o); const src = n.material; n.material = bodyMat(src, C.U, false, undefined, o.monster ? .5 : .6); if (o.monster && n.material.envMapIntensity != null) n.material.envMapIntensity = .15; C.mesh = n;
        if (!o.naked) { const cg = clothGeo(n, o), sh = new T.SkinnedMesh(cg, bodyMat(src, C.U, true)); sh.frustumCulled = false; C.shellOf = [n, sh]; C.clothMat = sh.material; C.skirtProf = cg.userData.skirt; } }
    }
  });
  if (C.shellOf) { const [n, sh] = C.shellOf; n.parent.add(sh); sh.bind(n.skeleton, n.bindMatrix); }
  if (o.hairPart) C.hair = attach(C, o.hairPart, o.hair);
  if (o.beard) C.beard = attach(C, 'Hair_Beard', o.beardC ?? o.hair);
  if (o.monster) { C.brows.visible = false; }
  // пропорции: рост, худоба, длинные руки
  const s = (o.h || 1.8) / (o.fem ? 1.77 : 1.81);
  C.ss = s; inner.scale.setScalar(s);   // только равномерно: иначе кости перекашиваются; худобу делает шейдер (uThin)
  if (o.arm) for (const b of ['upperarm_l', 'upperarm_r']) C.bone[b].scale.setScalar(o.arm);
  if (o.armL) for (const b of ['lowerarm_l', 'lowerarm_r']) C.bone[b].scale.setScalar(o.armL);
  if (o.handS) for (const b of ['hand_l', 'hand_r']) C.bone[b].scale.setScalar(o.handS);
  if (o.neck) { C.bone.neck_01.scale.setScalar(o.neck); C.bone.Head.scale.setScalar((o.headS || 1) / o.neck); }
  addProps(C);
  // анимации
  C.mixer = new T.AnimationMixer(sc); C.act = null; C.clip = '';
  C.addFace = (nm, ov) => { C.faceSets[nm] = ov; };
  C.update = (t, dt) => updateSkinned(C, t, dt);
  fitProps(C);
  return C;
}

// кепка, очки, пряди, предметы в руке, швабра — общие для v2 и v3
function addProps(C) {
  const o = C.o, root = C.root;
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
  // юбка платья (Марта на фото): расширяется от бёдер к коленям, висит на кости таза
  if (o.dress) { const pr = [[.15, .12], [.174, .06], [.195, 0], [.225, -.16], [.26, -.34], [.275, -.41]].map(([r, y]) => new T.Vector2(r, y));
    const dg = new T.Mesh(new T.LatheGeometry(pr, 24), ps1(litMat({ color: o.dress, side: T.DoubleSide }, .85))); dg.scale.z = .92; C.dressG = dg; }
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
  if (o.sheet) {   // лист с надписью маркером (Фрэнк показывает его в камеру): держит двумя руками, текст к зрителю
    const c = document.createElement('canvas'); c.width = 512; c.height = 360; const x = c.getContext('2d');
    x.fillStyle = '#ece6d6'; x.fillRect(0, 0, 512, 360); x.strokeStyle = 'rgba(80,110,160,.25)'; for (let y = 40; y < 360; y += 32) { x.beginPath(); x.moveTo(0, y); x.lineTo(512, y); x.stroke(); }
    x.fillStyle = '#141414'; x.textAlign = 'center'; x.font = '900 64px Impact, "Arial Black", Arial, sans-serif';
    o.sheet.forEach((t, i) => x.fillText(tr(t), 256, 140 + i * 100, 480));
    const tx = new T.CanvasTexture(c); tx.colorSpace = T.SRGBColorSpace; tx.anisotropy = 4;
    const sh = new T.Mesh(new T.PlaneGeometry(.3, .21), litMat({ map: tx, side: T.DoubleSide }, .9)); sh.visible = false; root.add(sh); C.sheet = sh;
  }
  if (o.mop) {   // швабра уборщика (в осях персонажа: низ — тряпка на полу, черенок идёт к рукам)
    const mp = new T.Group(); root.add(mp);
    const st = cyl(.013, .013, 1.45, 6, mat(0x8a8f96), mp); st.position.y = .74;
    box(.36, .035, .1, mat(0x3a3f6a), 0, .03, 0, mp);
    for (let i = 0; i < 9; i++) { const f = box(.035, .025, .16, mat(0xb8b4a8), -.16 + i * .04, .012, .02, mp); f.rotation.y = (rnd() - .5) * .5; }
    mp.visible = false; C.mop = mp;
  }
}

// где держать предметы и как ставить голову/кепку/очки — подгоняется один раз по позе «Т» (первый кадр)
function fitProps(C) {
  if (C.fitted) return; C.fitted = true;
  C.root.updateWorldMatrix(true, true);
  const head = C.bone.Head, hw = new T.Vector3(), q = new T.Quaternion();
  head.getWorldQuaternion(q); const inv = q.clone().invert();
  const toLocal = (wx, wy, wz) => { const v = new T.Vector3(wx, wy, wz); head.worldToLocal(v); return v; };
  head.getWorldPosition(hw);
  const s = C.ss;
  if (C.capG && C.v3) { C.capG.position.copy(toLocal(hw.x, C.H - .045, (C.eyeZ ?? hw.z + .09) - .088)); C.capG.quaternion.copy(inv).multiply(C.root.getWorldQuaternion(new T.Quaternion())); }
  else if (C.capG) { C.capG.position.copy(toLocal(hw.x, hw.y + .2 * s, hw.z + .005 * s)); C.capG.quaternion.copy(inv).multiply(C.root.getWorldQuaternion(new T.Quaternion())); }
  const rootQ = C.root.getWorldQuaternion(new T.Quaternion());
  const put = (obj, bone, wx, wy, wz) => { const b = C.bone[bone]; b.add(obj); const v = new T.Vector3(wx, wy, wz); b.worldToLocal(v); obj.position.copy(v); obj.quaternion.copy(b.getWorldQuaternion(new T.Quaternion()).invert().multiply(rootQ)); };
  const ss = C.ss;
  if (C.skirt) skinSkirt(C, ss * (C.o.fem ? .978 : 1));
  if (C.dressG) { const pw = C.bone.pelvis.getWorldPosition(new T.Vector3()); put(C.dressG, 'pelvis', pw.x, pw.y - .02, pw.z + .03); }
  if (C.hood) put(C.hood, 'Head', hw.x, hw.y + .085 * ss, hw.z - .01 * ss);
  if (C.veilG) put(C.veilG, 'Head', hw.x, hw.y + .175 * ss, hw.z + .1 * ss);
  if (C.glassG) { C.glassG.position.copy(toLocal(hw.x, hw.y + .095 * s, hw.z + .105 * s)); C.glassG.quaternion.copy(inv).multiply(C.root.getWorldQuaternion(new T.Quaternion())); }
  C.headFwd = new T.Vector3(0, 0, 1).applyQuaternion(inv);
  C.headUp = new T.Vector3(0, 1, 0).applyQuaternion(inv); C.headLeft = new T.Vector3(1, 0, 0).applyQuaternion(inv);
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
  const m = ps1(litMat({ map, color, alphaTest: .45 }, .7)), snap = m.onBeforeCompile; C.browU = { value: new T.Vector2() };
  m.onBeforeCompile = sh => { snap(sh); sh.uniforms.uBrow = C.browU;
    sh.vertexShader = 'uniform vec2 uBrow;\n' + sh.vertexShader.replace('#include <begin_vertex>', `#include <begin_vertex>
      { float inr = 1. - smoothstep(.012, .06, abs(position.x));
        transformed.y += uBrow.x * .009 + uBrow.y * .0085 * inr - max(-uBrow.y, 0.) * .0035 * (1. - inr);
        transformed.x -= sign(position.x) * max(-uBrow.y, 0.) * .0035 * inr; transformed.z -= uBrow.x * .0015; }`); };
  return m;
}

/* ---------- сигарета: хлопает по карманам → достаёт сигарету → в зубы → зажигалка → затяжка → выдох ---------- */
// t — время действия (с начала реплики «хлопал по карманам»); 99 — уже курит: держит у груди, изредка затягивается
let SMK = null;
function smokeTex() {
  if (SMK) return SMK; const c = document.createElement('canvas'); c.width = c.height = 64; const x = c.getContext('2d');
  for (let i = 0; i < 14; i++) { const g = x.createRadialGradient(32 + (rnd() - .5) * 20, 32 + (rnd() - .5) * 20, 0, 32, 32, 30); g.addColorStop(0, 'rgba(225,228,232,.16)'); g.addColorStop(1, 'rgba(225,228,232,0)'); x.fillStyle = g; x.fillRect(0, 0, 64, 64); }
  SMK = new T.CanvasTexture(c); return SMK;
}
function smokeProps(C) {
  if (C.cig) return;
  const cig = new T.Group(); cyl(.0042, .0042, .075, 8, mat(0xe8e4da), cig).rotation.x = Math.PI / 2;
  const f = cyl(.0043, .0043, .022, 8, mat(0xc8935a), cig); f.rotation.x = Math.PI / 2; f.position.z = -.045;   // фильтр
  C.tip = cyl(.0045, .0045, .006, 8, new T.MeshBasicMaterial({ color: 0x401008 }), cig); C.tip.rotation.x = Math.PI / 2; C.tip.position.z = .039;
  const lt = new T.Group(); box(.022, .055, .012, mat(0x8a1a1a), 0, 0, 0, lt); box(.02, .008, .012, mat(0xa0a0a0), 0, .031, 0, lt);
  C.flame = new T.Mesh(new T.ConeGeometry(.007, .03, 8), new T.MeshBasicMaterial({ color: 0xffb040, transparent: true, opacity: .9, blending: T.AdditiveBlending, depthWrite: false })); C.flame.position.y = .052; lt.add(C.flame);
  C.puffs = []; const sm = smokeTex();
  for (let i = 0; i < 16; i++) { const sp = new T.Sprite(new T.SpriteMaterial({ map: sm, transparent: true, depthWrite: false, opacity: 0, color: 0xd8dce0 })); sp.visible = false; C.root.add(sp); C.puffs.push(sp); }
  C.root.add(cig, lt); C.cig = cig; C.lighter = lt;
}
const _m1 = new T.Vector3(), _m2 = new T.Vector3(), _m3 = new T.Vector3();
function mouthPt(C) {   // рот в осях персонажа
  if (C.mouthL) return C.root.worldToLocal(C.bone.Head.localToWorld(C.mouthL.clone()));
  const h = C.bone.Head, q = h.getWorldQuaternion(new T.Quaternion()), p = h.getWorldPosition(new T.Vector3()), s = C.ss;
  const fw = C.headFwd.clone().applyQuaternion(q), up = new T.Vector3(0, 1, 0).applyQuaternion(q);
  p.addScaledVector(fw, .095 * s).addScaledVector(up, .035 * s); return C.root.worldToLocal(p);
}
function smokeArms(C, t, tt) {
  smokeProps(C); const ss = C.ss, m = mouthPt(C), lit = t > 4.45 || t >= 99;
  const lerp3 = (a, b, k) => [lerp(a[0], b[0], k), lerp(a[1], b[1], k), lerp(a[2], b[2], k)], sk = k => k * k * (3 - 2 * k);
  const pockR = [-.2 * ss, .95 * ss, .07 * ss], chest = [-.1 * ss, 1.33 * ss, .16 * ss], pockL = [.2 * ss, .95 * ss, .07 * ss], rest = [-.21 * ss, .88 * ss, .08 * ss], restL = [.21 * ss, .88 * ss, .06 * ss];
  const atMouth = [m.x - .02, m.y - .03, m.z + .02], hold = [-.1 * ss, 1.22 * ss, .27 * ss];
  let R = rest, L = restL, cigAt = 'none', light = 0;
  if (t < 99) {
    if (t < 2.5) {   // хлопает по карманам: брюки, грудь, другой бок
      const ph = t / .62 | 0, pat = Math.abs(Math.sin(t * 11)) * .025 * ss, sp = [pockR, chest, pockL, pockR][ph % 4];
      if (sp === pockL) L = [sp[0], sp[1] + pat, sp[2]]; else R = [sp[0], sp[1] + pat, sp[2]];
    } else if (t < 3.5) { const k = sk(clamp((t - 2.6) / .8, 0, 1)); R = lerp3(pockR, atMouth, k); cigAt = t > 2.75 ? 'hand' : 'none'; }   // нашёл — несёт ко рту
    else if (t < 4.9) {   // сигарета в зубах, правая опускается; левая с зажигалкой у лица, огонёк
      cigAt = 'mouth'; R = lerp3(atMouth, rest, sk(clamp((t - 3.5) / .5, 0, 1)));
      const kl = sk(clamp((t - 3.55) / .45, 0, 1)) * (1 - sk(clamp((t - 4.6) / .3, 0, 1))); L = lerp3(restL, [m.x + .03, m.y - .13, m.z + .1], kl); light = t > 3.95 && t < 4.6 ? 1 : 0;
    } else { const k = sk(clamp((t - 4.9) / .5, 0, 1)); cigAt = t < 5.15 ? 'mouth' : 'hand'; R = t < 5.15 ? lerp3(rest, atMouth, sk(clamp((t - 4.9) / .25, 0, 1))) : lerp3(atMouth, hold, k); }
  } else {   // уже курит: у груди; раз в 6 с затяжка
    const ph = tt % 6, up = ph < .6 ? sk(ph / .6) : ph < 1.6 ? 1 : ph < 2.2 ? 1 - sk((ph - 1.6) / .6) : 0;
    R = lerp3(hold, atMouth, up); cigAt = 'hand';
  }
  ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', R[0], R[1], R[2], [-.9, -.5, -.3]); ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', L[0], L[1], L[2], [.9, -.5, -.3]);
  fist(C, 'r', cigAt === 'hand' ? .55 : t < 2.5 && t < 99 ? .2 : .4); fist(C, 'l', light || (t > 3.5 && t < 4.9) ? .75 : .25);
  // сигарета: в пальцах (между указательным и средним) или в зубах — смотрит вперёд-вниз
  const cg = C.cig; cg.visible = cigAt !== 'none';
  if (cigAt === 'hand') { const a = C.root.worldToLocal(C.bone.index_02_r.getWorldPosition(_m1)), b = C.root.worldToLocal(C.bone.middle_02_r.getWorldPosition(_m2)), h = gripPt(C, 'r');
    cg.position.copy(a).lerp(b, .5); cg.lookAt(_m3.copy(cg.position).sub(h).add(cg.position).applyMatrix4(C.root.matrixWorld)); cg.position.lerp(h, -.25); }
  else if (cigAt === 'mouth') { cg.position.copy(m).add(_m1.set(0, -.012, .035)); cg.rotation.set(.3, 0, 0); }
  cg.updateMatrixWorld(true);
  C.lighter.visible = t > 3.5 && t < 4.9 && t < 99; if (C.lighter.visible) { C.lighter.position.copy(gripPt(C, 'l')).add(_m1.set(0, .02, 0)); C.lighter.rotation.set(0, 0, 0); }
  C.flame.visible = !!light; if (light) { C.flame.scale.set(1, .8 + .4 * Math.abs(Math.sin(tt * 37)), 1); }
  C.lighterOn = light; C.root.updateWorldMatrix(false, true);
  C.tip.material.color.setHex(lit ? (Math.sin(tt * 5) > .6 || (t > 4.3 && t < 4.9) ? 0xff6a20 : 0xc03410) : 0x2a1a14);
  // дым: тонкая струйка с кончика; выдох облаком изо рта
  const tipW = C.tip.getWorldPosition(_m2), tipL = C.root.worldToLocal(tipW.clone()), exh = t < 99 ? t - 5.3 : ((tt % 6) - 2.3);
  C.puffs.forEach((sp, i) => {
    if (i < 8) { if (!lit || !cg.visible) { sp.visible = false; return; } const a = ((tt * .35 + i / 8) % 1); sp.visible = true;
      sp.position.set(tipL.x + Math.sin(tt * 2 + i) * .02 * a, tipL.y + a * .35, tipL.z + Math.cos(tt * 1.5 + i) * .02 * a); sp.scale.setScalar(.03 + a * .12); sp.material.opacity = .5 * (1 - a) * Math.min(1, a * 6); }
    else { const a = (exh - (i - 8) * .06) / 1.6; if (a <= 0 || a >= 1 || !lit) { sp.visible = false; return; } sp.visible = true;
      sp.position.set(m.x + (i - 12) * .015 + a * .05, m.y - .02 + a * .25, m.z + .05 + a * .3); sp.scale.setScalar(.05 + a * .28); sp.material.opacity = .28 * (1 - a) * Math.min(1, a * 5); }
  });
}

/* ---------- позы ---------- */
const REACT = { yes: 'Yes', no: 'Idle_No_Loop', arms: 'Idle_FoldArms_Loop' };   // реакции тела по реплике (beat.react в story.js)
const POSE = {
  stand: 'Idle_Loop', talk: 'Idle_Talking_Loop', sit: 'Sitting_Idle_Loop', sittalk: 'Sitting_Talking_Loop', sitfloor: 'Fixing_Kneeling',
  knock: 'Idle_Loop', press: 'Idle_Loop', give: 'Idle_Loop', paper: 'Idle_Loop', monster: 'Idle_Loop', reach: 'Idle_Loop',
  walk: 'Walk_Loop', back: 'Walk_Loop', push: 'Push_Loop', hug: 'Idle_Loop', eat: 'Crouch_Idle_Loop', lay: 'LayToIdle', mop: 'Idle_Loop', slam: 'Idle_Loop',
  sitpaper: 'Fixing_Kneeling', smoke: 'Idle_Loop', embrace: 'Idle_Loop', weak: 'Walk_Loop',
};
const _ld = new T.Vector3(), _a = new T.Vector3(), _b = new T.Vector3(), _c = new T.Vector3(), _q = new T.Quaternion(), _q2 = new T.Quaternion(), _pq = new T.Quaternion();
// повернуть кость так, чтобы она смотрела в направлении dir (в осях персонажа: +z вперёд, +y вверх, +x влево)
function footFloor(C) {   // носок не под полом: если подушечка стопы ниже пола, стопа в голеностопе поворачивается вверх ровно до пола
  C.root.updateWorldMatrix(true, true); const fl = C.root.getWorldPosition(new T.Vector3()).y, iq = C.root.getWorldQuaternion(new T.Quaternion()).invert();
  for (const sd of ['l', 'r']) { const ft = C.bone['foot_' + sd], bl = C.bone['ball_' + sd]; if (!ft || !bl) continue;
    const fp = ft.getWorldPosition(new T.Vector3()), bp = bl.getWorldPosition(new T.Vector3()); if (bp.y > fl + .01) continue;
    const L = fp.distanceTo(bp) || .1, s = clamp((fl + .015 - fp.y) / L, -1, 1), d = bp.sub(fp).applyQuaternion(iq), hz = Math.hypot(d.x, d.z) || 1, c = Math.sqrt(1 - s * s);
    aimBone(C, 'foot_' + sd, 'ball_' + sd, [d.x / hz * c, s, d.z / hz * c], 1); }
}
function heelFloor(C, fl) {   // вытянутая нога сидящего: пятка (≈8 см под голеностопом) не ниже пола fl — нога приподнимается от бедра ровно настолько
  C.root.updateWorldMatrix(true, true); const iq = C.root.getWorldQuaternion(new T.Quaternion()).invert();
  for (const sd of ['l', 'r']) { const th = C.bone['thigh_' + sd], ft = C.bone['foot_' + sd]; if (!th || !ft) continue;
    const fp = ft.getWorldPosition(new T.Vector3()), up = fl + .084 * (C.ss || 1) - fp.y; if (up <= 0) continue;
    const hp = th.getWorldPosition(new T.Vector3()), d = fp.add(new T.Vector3(0, up, 0)).sub(hp).applyQuaternion(iq);
    aimBone(C, 'thigh_' + sd, 'foot_' + sd, [d.x, d.y, d.z], 1); }
}
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
// на коленях, сидя на пятках: таз опущен, колени на полу впереди, голени лежат на полу, стопы под ягодицами (только v3)
function kneel(C, tt) {
  const ss = C.ss, pel = C.bone.pelvis;
  C.root.updateWorldMatrix(true, true);
  const want = C.root.localToWorld(new T.Vector3(0, .36 * ss + Math.sin(tt * 1.3) * .004, -.04 * ss));
  pel.parent.updateWorldMatrix(true, false); pel.position.copy(pel.parent.worldToLocal(want)); pel.updateWorldMatrix(false, true);
  for (const [sd, sx] of [['l', 1], ['r', -1]]) {
    ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, sx * .11 * ss, .06 * ss, -.2 * ss, [sx * .1, -1, 1.4]);
    aimBone(C, 'foot_' + sd, 'ball_' + sd, [0, -.3, -1], 1);   // стопа подъёмом на полу, носок назад
  }
}
// ступня сохраняет поворот в мире (после поправки ноги подошва остаётся на полу)
function keepWorld(C, name, q) { const b = C.bone[name]; b.parent.getWorldQuaternion(_Q3); b.quaternion.copy(_Q3.invert().multiply(q)); b.updateWorldMatrix(false, true); }
// точка хвата в ладони (в осях персонажа): между запястьем и основанием среднего пальца, чуть к ладони
const _UP = new T.Vector3(0, 1, 0), _twQ = new T.Quaternion(), _flA = new T.Vector3(), _flB = new T.Vector3(), _flL = new T.Vector3(), _flQ = new T.Quaternion();
// опорная стопа не едет по полу: пока человек идёт (корень движется) и нога стоит (лодыжка ниже другой не больше чем на 2 см, у пола), место стопы
// на полу запоминается, и нога доводится туда IK; стопа отрывается — плавно отпускаем к клипу. Уехала дальше 16 см — отпускаем, а не тянем ногу.
// Состояние меняется только при dt > 0: повторный расчёт позы в том же кадре (твёрдый мир, dt = 0) даёт ту же позу
function footLock(C, dt) {
  const ss = C.ss || 1, rp = C.root.position, F = C.flk || (C.flk = { l: { on: false, p: new T.Vector3(), w: 0 }, r: { on: false, p: new T.Vector3(), w: 0 }, prev: null, v: 0 });
  if (dt > 0) { if (F.prev) { const v = Math.hypot(rp.x - F.prev.x, rp.z - F.prev.z) / dt; F.v += (Math.min(v, 4) - F.v) * Math.min(1, dt * 10); } (F.prev || (F.prev = new T.Vector3())).copy(rp); }
  const fl = C.bone.foot_l, fr = C.bone.foot_r; if (!fl || !fr) return;
  const moving = F.v > .25; if (dt > 0) F.still = moving ? 0 : (F.still || 0) + dt; const active = moving || F.still < .6;   // остановился — ещё 0,6 с стоит на тех же местах, потом стопы мягко уходят в стойку
  C.root.updateWorldMatrix(true, true); const hl = fl.getWorldPosition(_flL).y - rp.y, hr = fr.getWorldPosition(_flA).y - rp.y, lo = Math.min(hl, hr);
  for (const sd of ['l', 'r']) { const S = F[sd], ft = C.bone['foot_' + sd], wp = ft.getWorldPosition(_flA), h = wp.y - rp.y, down = h <= lo + .02 * ss && h < .17 * ss;
    if (dt > 0) { if (moving && down && !S.on) { S.on = true; S.p.copy(wp); } else if (S.on && !(active && down)) S.on = false;
      S.w = S.on ? Math.min(1, S.w + dt * 14) : Math.max(0, S.w - dt * (moving ? 9 : 3.5)); }
    if (S.w <= 0) continue;
    _flB.set(S.p.x, wp.y, S.p.z); if (Math.hypot(_flB.x - wp.x, _flB.z - wp.z) > .16 * ss) { if (dt > 0) S.on = false; continue; }
    _flB.lerp(wp, 1 - S.w); const fq = ft.getWorldQuaternion(_flQ), lp = C.root.worldToLocal(_flB);
    ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, lp.x, lp.y, lp.z, [sd === 'l' ? .1 : -.1, 0, 1]); keepWorld(C, 'foot_' + sd, fq); }
}
function gripPt(C, s) { C.root.updateWorldMatrix(true, true); const h = C.bone['hand_' + s].getWorldPosition(new T.Vector3()), m = C.bone['middle_01_' + s].getWorldPosition(new T.Vector3());
  return C.root.worldToLocal(h.lerp(m, .62)); }
// небольшой поворот кости вокруг её оси (для судорог, кивков): ax — ось в осях кости
function wiggle(C, n, ax, a) { const b = C.bone[n]; if (!b) return; _Q3.setFromAxisAngle(_D.set(ax[0], ax[1], ax[2]), a); b.quaternion.multiply(_Q3); }
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
const _e3 = new T.Euler(), _q3 = new T.Quaternion();
/* ---------- v3: постановка — слои клипов, пальцы, предметы в руках, касания ----------
   Кадр (scenes.js) отдаёт вместо имени позы объект:
     clips: [[клип, время, вес], …]   время клипа задаёт сам кадр — сцену можно перематывать, всё повторяется точно
     fing:  { r: ['grip', 1] }         поза пальцев поверх клипа: relax grip open flat point phone cup fist claw pinch
     arm:   { r: { at: [x, y, z], by: 'palm', fdir, pdir, pole, w } }   касание: точка кисти «by» (palm — ладонь, knuck — костяшки,
            tip — кончик указательного, pinch — щепоть) ставится ровно в точку мира; fdir — куда пальцы, pdir — куда ладонь
     spine: { to: [x, y, z], k }       корпус тянется к точке
     hold:  { r: 'keys' }              предмет в руке; переходит из руки в руку без скачка (от того места, где был)
     legs:  'feet'                     ступни сводятся под бёдра (у стойки Quaternius ноги широко)
   Всё в осях мира. */
const HANDP = {}, RESTL = {};
const _hq = new T.Quaternion(), _hv = new T.Vector3(), _hs = new T.Vector3(1, 1, 1);
function fingerSample(clip, time, sd) {
  const c = CLIP[clip], out = {}; if (!c) return out;
  for (const tk of c.tracks) {
    const i = tk.name.lastIndexOf('.'), bn = tk.name.slice(0, i);
    if (tk.name.slice(i + 1) !== 'quaternion' || !/^(thumb|index|middle|ring|pinky)_0[123]_[lr]$/.test(bn) || !bn.endsWith('_' + sd)) continue;
    const v = tk.createInterpolant().evaluate(Math.min(time, c.duration)); out[bn] = new T.Quaternion(v[0], v[1], v[2], v[3]).normalize();
  }
  return out;
}
const mirrorBone = n => n.replace(/_([lr])$/, (m, s) => '_' + (s === 'l' ? 'r' : 'l'));
function qpow(q, rest, k) {   // поворот от покоя, усиленный в k раз
  const d = rest.clone().invert().multiply(q); if (d.w < 0) { d.x = -d.x; d.y = -d.y; d.z = -d.z; d.w = -d.w; }
  const a = 2 * Math.acos(clamp(d.w, -1, 1)); if (a < 1e-4) return q.clone();
  const s = Math.sin(a / 2); return rest.clone().multiply(new T.Quaternion().setFromAxisAngle(_hv.set(d.x / s, d.y / s, d.z / s), a * k));
}
function buildHands() {
  if (HANDP.relax || !CLIP.Idle_Loop) return;
  if (G.anims) G.anims.scene.traverse(n => { if (n.name) RESTL[n.name] = n.quaternion.clone(); });
  const pick = (clip, f, sd) => { const c = CLIP[clip], h = fingerSample(clip, c ? c.duration * f : 0, sd), o = { l: {}, r: {} };
    for (const k in h) { o[sd][k] = h[k]; o[sd === 'l' ? 'r' : 'l'][mirrorBone(k)] = new T.Quaternion(h[k].x, -h[k].y, -h[k].z, h[k].w); }
    return o; };
  HANDP.relax = pick('Idle_Loop', 0, 'r');
  HANDP.grip = pick('Idle_Torch_Loop', .25, 'l');      // хват черенка, фонаря
  HANDP.open = pick('Farm_Harvest', .25, 'r');         // раскрытая — тянется взять
  HANDP.flat = pick('Push_Loop', .25, 'r');            // ладонь плашмя — на стекле, на плече
  HANDP.point = pick('Interact', .5, 'l');             // указательный вытянут — нажать, писать по стеклу
  HANDP.phone = pick('Idle_TalkingPhone_Loop', .1, 'r');
  HANDP.cup = pick('Consume', .5, 'l');
  const R0 = n => RESTL[n] || new T.Quaternion();
  for (const [name, f] of [['fist', (bn, q) => qpow(q, R0(bn), bn.startsWith('thumb') ? 1.2 : 1.3)],          // кулак — туже обычного
                           ['claw', (bn, q) => /_01_/.test(bn) ? qpow(q, R0(bn), .2) : qpow(q, R0(bn), 1.1)],   // «когти»: ровные костяшки, согнутые кончики
                           ['hook', (bn, q) => qpow(q, R0(bn), bn.startsWith('thumb') ? .3 : /_01_/.test(bn) ? .28 : /_02_/.test(bn) ? .42 : .5)],   // «крюк» Жильца: пальцы чуть согнуты — когти смотрят вперёд, а не в ладонь
                           ['pinch', (bn, q) => bn.startsWith('index') ? qpow(q, R0(bn), .62) : bn.startsWith('thumb') ? qpow(q, R0(bn), 1.25) : q.clone()]]) {   // щепоть
    HANDP[name] = { l: {}, r: {} };
    for (const sd of ['l', 'r']) for (const bn in HANDP.relax[sd]) HANDP[name][sd][bn] = f(bn, HANDP.relax[sd][bn]);
  }
  // мягкая кисть: пальцы чуть согнуты, большой расслаблен — так руки висят у живого человека (кадр Idle_Loop сжимал их в кулак: все стояли «со злостью»)
  HANDP.soft = { l: {}, r: {} };
  for (const sd of ['l', 'r']) for (const bn in HANDP.relax[sd]) HANDP.soft[sd][bn] = qpow(HANDP.relax[sd][bn], R0(bn), bn.startsWith('thumb') ? .55 : /_01_/.test(bn) ? .38 : .5);
  // есть мокап — мягкая кисть берётся у живого человека: средняя поза пальцев, пока руки свободно висят (пальцы согнуты лесенкой — мизинец сильнее указательного, большой расслаблен)
  if (CLIP.Rk_Watching_Loop) { const c = CLIP.Rk_Watching_Loop;
    for (const sd of ['l', 'r']) { const acc = {};
      for (let i = 0; i < 32; i++) { const h = fingerSample('Rk_Watching_Loop', c.duration * (i + .5) / 32, sd);
        for (const k in h) { const q = h[k]; if (!acc[k]) acc[k] = new T.Quaternion(0, 0, 0, 0); const a = acc[k], sg = a.x * q.x + a.y * q.y + a.z * q.z + a.w * q.w < 0 ? -1 : 1; a.x += sg * q.x; a.y += sg * q.y; a.z += sg * q.z; a.w += sg * q.w; } }
      for (const k in acc) HANDP.soft[sd][k] = acc[k].normalize(); } }
  // указательный: вытянут и чуть согнут, остальные поджаты, большой лежит на среднем (кадр из Interact давал неестественный палец — владелец 24 окт)
  HANDP.point = { l: {}, r: {} };
  for (const sd of ['l', 'r']) for (const bn in HANDP.relax[sd]) { const q = HANDP.relax[sd][bn], r0 = R0(bn);
    HANDP.point[sd][bn] = bn.startsWith('index') ? qpow(q, r0, /_01_/.test(bn) ? .1 : .3) : bn.startsWith('thumb') ? qpow(q, r0, 1.15) : qpow(q, r0, 1.4); }
}
// кисть Жильца «крюком»: пальцы чуть согнуты и растопырены веером от среднего (указательный, безымянный, мизинец — в стороны)
function hookHand(C, sd) {
  setFingers(C, sd, 'hook', 1); C.root.updateWorldMatrix(true, true);
  const W = n => C.bone[n + '_' + sd].getWorldPosition(new T.Vector3()), pm = W('middle_01');
  for (const [f, a] of [['index', .14], ['ring', .1], ['pinky', .24]]) {
    const b = C.bone[f + '_01_' + sd], p = W(f + '_01'), d = W(f + '_02').sub(p).normalize(), sv = p.clone().sub(pm); sv.addScaledVector(d, -sv.dot(d));
    if (sv.lengthSq() < 1e-10) continue;
    setWorldQ(b, new T.Quaternion().setFromAxisAngle(d.clone().cross(sv.normalize()).normalize(), a).multiply(b.getWorldQuaternion(new T.Quaternion())));
  }
}
function setFingers(C, sd, pose, w) {
  const H = HANDP[pose]; if (!H || !(w > 0)) return;
  for (const bn in H[sd]) { const b = C.bone[bn]; if (b) b.quaternion.slerp(H[sd][bn], Math.min(1, w)); }
}
// точки кисти в мире
function tipOf(C, a, b, k = .85) { const pa = C.bone[a].getWorldPosition(new T.Vector3()), pb = C.bone[b].getWorldPosition(new T.Vector3()); return pb.clone().addScaledVector(pb.sub(pa), k); }
function palmN(C, sd) {   // нормаль ладони — наружу из ладони
  const w = n => C.bone[n + '_' + sd].getWorldPosition(new T.Vector3());
  const h = w('hand'), m = w('middle_01'), i = w('index_01'), p = w('pinky_01');
  const n = i.sub(p).cross(m.sub(h)).normalize(); return sd === 'l' ? n.negate() : n;
}
function handPt(C, sd, by) {
  C.root.updateWorldMatrix(true, true);
  const w = n => C.bone[n + '_' + sd].getWorldPosition(new T.Vector3());
  if (by === 'tip') return tipOf(C, 'index_02_' + sd, 'index_03_' + sd);
  if (by === 'thumb') return tipOf(C, 'thumb_02_' + sd, 'thumb_03_' + sd);
  if (by === 'pinch') return tipOf(C, 'index_02_' + sd, 'index_03_' + sd).add(tipOf(C, 'thumb_02_' + sd, 'thumb_03_' + sd)).multiplyScalar(.5);
  if (by === 'knuck') { const a = w('index_02').add(w('middle_02')).multiplyScalar(.5), f = w('middle_02').sub(w('middle_01')).normalize(); return a.addScaledVector(f, .011 * C.ss); }
  if (by === 'wrist') return w('hand');
  return w('hand').lerp(w('middle_01'), .55).addScaledVector(palmN(C, sd), .016 * C.ss);   // palm
}
function setWorldQ(b, wq) { b.parent.updateWorldMatrix(true, false); b.parent.getWorldQuaternion(_hq).invert(); b.quaternion.copy(_hq.multiply(wq)); b.updateWorldMatrix(false, true); }
// поворот кисти: 60% закрутки берёт предплечье (вращается вокруг своей оси — как у живого человека), кисти остаётся 40% — кожа запястья не скручивается
function twistShare(C, sd, want) {
  const lo = C.bone['lowerarm_' + sd], hb = C.bone['hand_' + sd];
  const a = hb.getWorldPosition(new T.Vector3()).sub(lo.getWorldPosition(new T.Vector3())).normalize();
  const r = want.clone().multiply(hb.getWorldQuaternion(new T.Quaternion()).invert());   // от текущей к нужной (мир)
  const p = a.dot(_hv.set(r.x, r.y, r.z)); let ang = 2 * Math.atan2(p, r.w); ang = Math.atan2(Math.sin(ang), Math.cos(ang));
  const lw = lo.getWorldQuaternion(new T.Quaternion()).premultiply(new T.Quaternion().setFromAxisAngle(a, ang * .6)); setWorldQ(lo, lw);
}
// развернуть кисть: пальцы по fdir, ладонь по pdir (мир). Итог считается целиком, потом закрутка делится с предплечьем
function orientHand(C, sd, fdir, pdir, k = 1) {   // k < 1 — кисть поворачивается к цели вместе с рукой (без рывка запястья в начале движения)
  const hb = C.bone['hand_' + sd]; C.root.updateWorldMatrix(true, true);
  const orig = hb.getWorldQuaternion(new T.Quaternion()), inv = orig.clone().invert();
  const h = hb.getWorldPosition(new T.Vector3()), m = C.bone['middle_01_' + sd].getWorldPosition(new T.Vector3());
  const F = new T.Vector3(fdir[0], fdir[1], fdir[2]).normalize();
  const wq = orig.clone().premultiply(new T.Quaternion().setFromUnitVectors(m.sub(h).normalize(), F));
  if (pdir) {
    const n = palmN(C, sd).applyQuaternion(inv).applyQuaternion(wq), want = new T.Vector3(pdir[0], pdir[1], pdir[2]);
    want.addScaledVector(F, -want.dot(F)); n.addScaledVector(F, -n.dot(F));
    if (want.lengthSq() > 1e-6 && n.lengthSq() > 1e-6) { want.normalize(); n.normalize();
      let a = Math.acos(clamp(n.dot(want), -1, 1)); if (n.clone().cross(want).dot(F) < 0) a = -a; wq.premultiply(new T.Quaternion().setFromAxisAngle(F, a)); }
  }
  if (k < 1) wq.copy(orig.clone().slerp(wq, Math.max(0, k)));
  twistShare(C, sd, wq); setWorldQ(hb, wq);
}
// рука к точке мира так, чтобы туда пришлась именно точка кисти «by» (кисть повёрнута — точка касания сместилась: 3 прохода)
function reachArm(C, sd, a) {
  const k = a.w ?? 1; if (!(k > 0)) return;
  const pole = a.pole || (sd === 'r' ? [-.7, -1, -.35] : [.7, -1, -.35]);
  if (typeof a.at === 'function') C.root.updateWorldMatrix(true, true);
  const at = typeof a.at === 'function' ? a.at(C) : a.at;   // функция — точка от тела этого кадра (ухо, рот, грудь): мокап сидит и стоит по-своему, рука идёт за ним
  const want = new T.Vector3(at[0], at[1], at[2]), wrist = want.clone();
  if (a.by && a.by !== 'wrist') { const p = handPt(C, sd, a.by), hw = C.bone['hand_' + sd].getWorldPosition(new T.Vector3()); wrist.add(hw.sub(p)); }
  const arm = ['upperarm_', 'lowerarm_', 'hand_'].map(n => C.bone[n + sd]), q0 = k < 1 && a.by && a.by !== 'wrist' ? arm.map(b => b.quaternion.clone()) : null;   // рука только тянется (вес < 1): кончик пальца считаем от кисти в конечной позе, а не от позы клипа — иначе палец пролетал цель (сквозь стекло)
  for (let it = 0; it < 3; it++) {
    const lp = C.root.worldToLocal(wrist.clone());
    ik2(C, 'upperarm_' + sd, 'lowerarm_' + sd, 'hand_' + sd, lp.x, lp.y, lp.z, pole, q0 ? 1 : k);
    if (a.fdir) orientHand(C, sd, a.fdir, a.pdir, q0 ? 1 : k);
    if (!a.by || a.by === 'wrist' || (k < 1 && !q0)) break;
    wrist.add(want.clone().sub(handPt(C, sd, a.by)));
  }
  if (q0) { arm.forEach((b, i) => b.quaternion.copy(q0[i])); const lp = C.root.worldToLocal(wrist.clone());   // и уже к найденной точке — с весом
    ik2(C, 'upperarm_' + sd, 'lowerarm_' + sd, 'hand_' + sd, lp.x, lp.y, lp.z, pole, k); if (a.fdir) orientHand(C, sd, a.fdir, a.pdir, k); }
}
function leanTo(C, to, k) {   // корпус к точке: поясница и грудь поровну
  C.root.updateWorldMatrix(true, true);
  for (const [a, b, f] of [['spine_01', 'spine_02', .5], ['spine_02', 'spine_03', .5]]) {
    const pa = C.root.worldToLocal(C.bone[a].getWorldPosition(new T.Vector3())), pt = C.root.worldToLocal(new T.Vector3(to[0], to[1], to[2]));
    const d = pt.sub(pa).normalize(); aimBone(C, a, b, [d.x, d.y, d.z], k * f);
  }
}
/* ---------- предметы в руках (свои модели из простых тел) ---------- */
const PROPS3 = {};
function propMesh(name) {
  const g = new T.Group(); g.name = 'prop_' + name;
  const steel = mat(0xb9bcc0, null, { metal: .9 }), brass = mat(0xc9a44a, null, { metal: .9 }), blk = mat(0x111214), red = mat(0x9a1c1c);
  if (name === 'keys') {   // связка: кольцо, четыре ключа, пластиковая бирка; начало — кольцо, ключи висят вниз (−Y)
    const ring = new T.Mesh(new T.TorusGeometry(.016, .0022, 6, 18), steel); g.add(ring);
    [[-.5, brass, .07], [-.15, steel, .062], [.2, brass, .055], [.55, steel, .066]].forEach(([a, m, L], i) => {
      const k = new T.Group(); k.position.set(Math.sin(a) * .016, -Math.cos(a) * .016, (i - 1.5) * .002); k.rotation.z = a * .6; g.add(k);
      box(.022, .02, .003, m, 0, -.012, 0, k);                       // головка
      box(.007, L, .002, m, 0, -.022 - L / 2, 0, k);                 // стержень
      for (let j = 0; j < 4; j++) box(.004, .005, .002, m, .005, -.03 - j * L * .2, 0, k);   // бородка
    });
    const tag = new T.Group(); tag.position.set(.012, -.018, -.004); tag.rotation.z = .4; g.add(tag); box(.022, .045, .003, red, 0, -.03, 0, tag);
  } else if (name === 'phone') {   // начало — середина телефона; экран смотрит в +Z. Корпус светлый, экран чуть светится — иначе у уха на тёмной куртке его не видно
    box(.072, .152, .008, mat(0xd6d9dc, null, { roughness: .35, metalness: .6 }), 0, 0, 0, g);
    const scr = new T.Mesh(new T.PlaneGeometry(.066, .144), mat(0x101820, null, { emissive: 0x2a4a66, basic: false })); scr.position.z = .0042; g.add(scr); g.userData.screen = scr;
    const cam = box(.016, .016, .003, blk, -.022, .058, -.005, g); cam.castShadow = false;   // глазок камеры на спинке
  } else if (name === 'cig') {    // сигарета: начало — фильтр (в губах/пальцах), горит конец (+X)
    const p = cyl(.0042, .0042, .062, 8, mat(0xeeeae0), g); p.rotation.z = Math.PI / 2; p.position.x = .031 + .022;
    const f = cyl(.0043, .0043, .022, 8, mat(0xc98a42), g); f.rotation.z = Math.PI / 2; f.position.x = .011;
    const e = cyl(.004, .004, .004, 8, mat(0xff5a10, null, { basic: true }), g); e.rotation.z = Math.PI / 2; e.position.x = .086; g.userData.ember = e;
  } else if (name === 'lighter') {   // зажигалка: начало — середина корпуса, огонёк сверху (+Y)
    box(.024, .05, .012, red, 0, 0, 0, g); box(.024, .012, .012, steel, 0, .031, 0, g);
    const fl = new T.Mesh(new T.ConeGeometry(.005, .022, 8), new T.MeshBasicMaterial({ color: 0xffc060, transparent: true, opacity: .85, blending: T.AdditiveBlending, depthWrite: false }));
    fl.position.y = .05; g.add(fl); g.userData.flame = fl;
  } else if (name === 'mop') {
    const al = mat(0x9aa0a8, null, { metal: .8, rough: .35 }), pl = mat(0x2a3a7a, null, { rough: .6 });
    cyl(.012, .012, 1.42, 8, al, g).position.y = .73; cyl(.016, .016, .12, 8, mat(0x1a1a1a, null, { rough: .8 }), g).position.y = 1.38;   // черенок и ручка
    box(.42, .035, .1, pl, 0, .045, 0, g); cyl(.02, .02, .07, 6, pl, g).position.y = .09;                                                // колодка
    const fm = mat(0xd8d2c4, null, { rough: .95 }), fm2 = mat(0xa8a294, null, { rough: .95 });
    for (let i = 0; i < 40; i++) { const f = box(.026, .022, .2 + rnd() * .07, i % 3 ? fm : fm2, -.21 + (i % 20) * .022, .011, (i < 20 ? .06 : -.06), g); f.rotation.y = (rnd() - .5) * .5; }   // тряпка — жгуты
  } else if (name === 'splint') {   // картон по бокам голени, обмотан серым скотчем (начало — середина голени, вдоль +Y)
    const cb = mat(0x9a7a50, null, { rough: .9 }), tp = mat(0x8c9094, null, { rough: .45 });
    for (const s of [-1, 1]) box(.012, .38, .11, cb, s * .062, 0, 0, g);
    for (const y of [-.15, -.05, .06, .15]) { const b = box(.14, .035, .13, tp, 0, y, 0, g); b.rotation.y = .05 * y; }
  } else if (name === 'apple') {   // яблоко с дачи: центр — в начале, хвостик вверх
    const a = new T.Mesh(new T.SphereGeometry(.038, 14, 10), mat(0xa83a22, null, { rough: .45 })); a.scale.set(1, .92, 1); g.add(a);
    const y = new T.Mesh(new T.SphereGeometry(.03, 10, 8), mat(0xb8a030, null, { rough: .5 })); y.position.set(.012, .01, .016); y.scale.set(.9, .8, .9); g.add(y);   // жёлтый бок
    const st = new T.Mesh(new T.CylinderGeometry(.002, .0025, .02, 5), mat(0x3a2a14)); st.position.y = .04; st.rotation.z = .3; g.add(st);
  } else if (name === 'pen') {   // шариковая ручка: начало — кончик стержня, корпус вдоль +Y
    const c = new T.Mesh(new T.CylinderGeometry(.0045, .0045, .13, 8), mat(0x1c2a6a, null, { rough: .4 })); c.position.y = .075; g.add(c);
    const tip = new T.Mesh(new T.ConeGeometry(.0045, .012, 8), mat(0xc8c8c8, null, { metal: .8 })); tip.rotation.x = Math.PI; tip.position.y = .006; g.add(tip);
    box(.003, .04, .006, mat(0xc8c8c8, null, { metal: .8 }), .005, .12, 0, g);
  } else if (name === 'sheet') {   // мятый лист с надписью маркером (рисуется в chars2: o.sheet) — здесь пустой
  } else if (name === 'radio') {
    box(.058, .12, .034, blk, 0, 0, 0, g); cyl(.005, .005, .07, 6, blk, g).position.set(.018, .095, 0); box(.03, .04, .002, mat(0x1d2a20), 0, .02, .018, g);
  } else if (name === 'torch') {   // фонарик: начало — середина рукояти, светит в +X
    const b = cyl(.016, .016, .16, 10, blk, g); b.rotation.z = Math.PI / 2; const h = cyl(.024, .017, .04, 10, blk, g); h.rotation.z = Math.PI / 2; h.position.x = .1;
    const lens = cyl(.02, .02, .002, 10, mat(0xfff6d8, null, { basic: true }), g); lens.rotation.z = Math.PI / 2; lens.position.x = .121;
  }
  g.traverse(m => { if (m.isMesh) { m.castShadow = true; } });
  g.visible = false; return g;
}
function prop3(name) { if (!PROPS3[name]) { PROPS3[name] = { name, obj: propMesh(name), C: null, sd: null, from: null, k: 1 }; PROPS3[name].obj.userData.solid = false; } return PROPS3[name]; }   // предмет в руках — не преграда (твёрдый мир, scenes.js)
// хват: [точка кисти, смещение (вдоль пальцев X, из ладони Y, поперёк Z), поворот в осях кисти, висит ли по отвесу]
const GRIP3 = {
  keys: ['pinch', [0, .002, 0], [0, 0, 0], true],
  keys_palm: ['palm', [.01, .012, 0], [Math.PI / 2, 0, .3], false],
  phone: ['palm', [.012, .014, 0], [-Math.PI / 2, 0, 0], false],   // у уха: длинная сторона поперёк ладони (от уха ко рту), экран к голове — края видны над пальцами и у щеки
  cig: ['pinch', [0, 0, 0], [0, Math.PI / 2, 0], false],
  lighter: ['palm', [.01, .016, 0], [0, 0, 0], false],
  radio: ['palm', [.01, .02, 0], [0, 0, 0], false],
  torch: ['palm', [-.01, .016, 0], [0, 0, Math.PI / 2], false],
  apple: ['palm', [.02, .036, 0], [0, 0, 0], false],
};
function handFrame(C, sd, by) {   // оси кисти: X — вдоль пальцев, Y — из ладони, Z — поперёк; начало — точка by
  const p = handPt(C, sd, by), h = C.bone['hand_' + sd].getWorldPosition(new T.Vector3()), m = C.bone['middle_01_' + sd].getWorldPosition(new T.Vector3());
  const X = m.sub(h).normalize(), Y = palmN(C, sd); Y.addScaledVector(X, -Y.dot(X)).normalize(); const Z = X.clone().cross(Y);
  return new T.Matrix4().makeBasis(X, Y, Z).setPosition(p);
}
const _pa = new T.Vector3(), _pb = new T.Vector3(), _qa3 = new T.Quaternion(), _qb3 = new T.Quaternion(), _M3 = new T.Matrix4();
function holdProp(C, sd, spec, dt) {
  const [name, gname] = Array.isArray(spec) ? spec : [spec, spec];
  const pr = prop3(name), g = GRIP3[gname] || GRIP3[name] || ['palm', [0, 0, 0], [0, 0, 0]], scene = C.root.parent; if (!scene) return;
  if (pr.obj.parent !== scene) scene.add(pr.obj);
  const F = handFrame(C, sd, g[0]);
  const canon = new T.Matrix4().compose(new T.Vector3(...g[1]).multiplyScalar(C.ss), new T.Quaternion().setFromEuler(new T.Euler(...g[2])), _hs);
  if (pr.C !== C || pr.sd !== sd || pr.g !== gname) {   // перешёл в другую руку (или перехватили): плавно от того места, где был
    pr.from = pr.was ? F.clone().invert().multiply(pr.obj.matrix) : canon.clone(); pr.C = C; pr.sd = sd; pr.g = gname; pr.k = pr.was ? 0 : 1; }
  pr.k = Math.min(1, pr.k + (dt || 0) / .22);
  const e = pr.k * pr.k * (3 - 2 * pr.k);
  pr.from.decompose(_pa, _qa3, _hv); canon.decompose(_pb, _qb3, _hv);
  _M3.compose(_pa.lerp(_pb, e), _qa3.slerp(_qb3, e), _hs);
  F.multiply(_M3).decompose(pr.obj.position, pr.obj.quaternion, _hv);
  if (g[3]) {   // висит по отвесу: связка ключей качается, но вниз
    const down = new T.Quaternion().setFromUnitVectors(new T.Vector3(0, -1, 0).applyQuaternion(pr.obj.quaternion), new T.Vector3(0, -1, 0));
    pr.obj.quaternion.premultiply(down.slerp(new T.Quaternion(), .15));
  }
  pr.obj.visible = true; pr.seen = true;
}
// кадр сам кладёт предмет в мир (на стол, на пол): где и как
function propAt(name, scene, pos, rot) { const pr = prop3(name); if (pr.obj.parent !== scene) scene.add(pr.obj); pr.obj.position.set(...pos); pr.obj.rotation.set(...(rot || [0, 0, 0])); pr.obj.visible = true; pr.C = null; pr.seen = true; }
// в начале кадра все предметы прячутся; видны только те, что кто-то держит или кадр положил (pr.was — был ли виден в прошлом кадре)
function propAim(name, scene, pos, dir, yaw = 0) { const pr = prop3(name); if (pr.obj.parent !== scene) scene.add(pr.obj); pr.obj.position.set(...pos);
  pr.obj.quaternion.setFromUnitVectors(new T.Vector3(0, 1, 0), new T.Vector3(...dir).normalize()).multiply(new T.Quaternion().setFromAxisAngle(new T.Vector3(0, 1, 0), yaw)); pr.obj.visible = true; pr.C = null; }
function propsFrame() { for (const k in PROPS3) { const pr = PROPS3[k]; pr.was = pr.obj.visible; pr.obj.visible = false; pr.obj.updateMatrix(); } }
// тело на полу: сустав, ушедший ниже пола (судороги, клип «лёжа»), кость над ним поднимает — поворотом в суставе вокруг горизонтальной оси, сверху вниз по цепочке
const LAY_CH = [['spine_02', 'neck_01', .1], ['neck_01', 'Head', .1], ['thigh_l', 'calf_l', .07], ['calf_l', 'foot_l', .05], ['thigh_r', 'calf_r', .07], ['calf_r', 'foot_r', .05],
  ['upperarm_l', 'lowerarm_l', .05], ['lowerarm_l', 'hand_l', .04], ['upperarm_r', 'lowerarm_r', .05], ['lowerarm_r', 'hand_r', .04]];
const _lf = [new T.Vector3(), new T.Vector3(), new T.Vector3(), new T.Vector3(0, 1, 0), new T.Quaternion(), new T.Quaternion()];
function layFloor(C) {
  const fy = C.root.position.y < .2 ? 0 : C.root.position.y - .085, [pa, pb, ax, UP, q, wq] = _lf;   // пол: лежит на полу — y 0, на возвышении — под корнем
  for (const [a, b, r] of LAY_CH) { const A = C.bone[a], B = C.bone[b]; if (!A || !B) continue;
    A.getWorldPosition(pa); B.getWorldPosition(pb); const d = fy + r - pb.y; if (d <= 0) continue;
    const vx = pb.x - pa.x, vy = pb.y - pa.y, vz = pb.z - pa.z, L = Math.hypot(vx, vy, vz); if (L < .02) continue;
    ax.set(vx, 0, vz).cross(UP); if (ax.lengthSq() < 1e-8) continue; ax.normalize();
    const ang = Math.asin(clamp((vy + d) / L, -1, 1)) - Math.asin(clamp(vy / L, -1, 1));
    q.setFromAxisAngle(ax, ang); if (pb.sub(pa).applyQuaternion(q).y < vy) q.setFromAxisAngle(ax, -ang);   // в какую сторону «вверх» — проверкой
    A.getWorldQuaternion(wq).premultiply(q); A.parent.updateWorldMatrix(true, false); A.quaternion.copy(A.parent.getWorldQuaternion(_lf[4]).invert().multiply(wq)); A.updateWorldMatrix(false, true); }
}
// ступни под бёдрами, подошва на полу (у стойки клипов ноги широко, как у борца)
function feetIn(C, P) {
  const ss = C.ss;
  for (const [sd, sx] of [['l', 1], ['r', -1]]) {
    const ft = C.bone['foot_' + sd]; ft.updateWorldMatrix(true, false); const fq = ft.getWorldQuaternion(new T.Quaternion());
    const fp = C.root.worldToLocal(ft.getWorldPosition(new T.Vector3()));
    ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, lerp(fp.x, sx * (C.o.fem ? .085 : .115) * ss, .8), fp.y, fp.z, [sx * .15, 0, 1]);
    keepWorld(C, 'foot_' + sd, fq);
  }
}
// three.js пишет в кость, только если значение клипа изменилось с прошлого кадра. Когда время кадра стоит (ждём тапа в конце реплики),
// правки поверх клипа (наклон корпуса, IK рук и ног, таз, поворот, взгляд) накапливались кадр за кадром — человек сгибался и «ложился».
// Поэтому перед клипом кости возвращаются к чистой позе прошлого кадра, а сразу после клипа чистая поза запоминается заново.
function bonesRestore(C) { if (C.clean) for (const e of C.clean) { e[0].quaternion.copy(e[1]); e[0].position.copy(e[2]); } }
function bonesSnap(C) {
  if (!C.clean) C.clean = [...new Set(Object.values(C.bone))].map(b => [b, new T.Quaternion(), new T.Vector3()]);
  for (const e of C.clean) { e[1].copy(e[0].quaternion); e[2].copy(e[0].position); }
}
// живые движения (мокап Rokoko с пальцами, anims2.glb): нейтральная стойка и «говорит руками» Quaternius (стойка бойца: ноги широко,
// колени согнуты, руки вперёд) у людей заменяются мокапом — у каждого свой клип и своя фаза, чтобы двое не двигались одинаково
const NAT = { temych: ['Rk_Watching_Loop', 'Rk_Chatting_Loop', 0, 'Rk_SitIdle_Loop', 'Rk_SitTalk2_Loop'], ethanThing: ['Rk_Watching_Loop', 'Rk_Chatting_Loop', 0, 'Rk_SitIdle_Loop', 'Rk_SitTalk2_Loop'], smenshik: ['Rk_LookAround_Loop', 'Rk_Chatting2_Loop', 7.3],
  mihalych: ['Rk_Watching_Loop', 'Rk_Conversation_Loop', 11.7], cleaner: ['Rk_LookAround_Loop', 'Rk_Conversation_Loop', 4.1],
  katya: ['Rk_Watching_Loop', 'Rk_Chatting_Loop', 9.2], katyaSmile: ['Rk_Watching_Loop', 'Rk_Chatting_Loop', 9.2], martha: ['Rk_Watching_Loop', 'Rk_Chatting_Loop', 2.6] };
const WALK_K = .74;   // Rk_Walk_Loop: опорная стопа едет назад 1,25 м/с времени клипа (у Walk_Loop — 1,04): на тот же путь время клипа ×0,74 (подобрано по скольжению стопы)
function natClip(C, nm) {   // → [клип, сдвиг фазы, масштаб времени]
  const n = !C.o.monster && G.anims2 && !window.__NOMOCAP && NAT[C.o.name]; if (!n) return [nm, 0];
  if (nm === 'Walk_Loop' && CLIP.Rk_Walk_Loop) return ['Rk_Walk_Loop', 0, window.__WALK_K ?? WALK_K];   // шаг идёт от пройденного пути — фазу не сдвигаем
  if (nm === 'Idle_Loop' && CLIP[n[0]]) return [n[0], n[2]];
  if (nm === 'Idle_Talking_Loop' && CLIP[n[1]]) return [n[1], n[2]];
  if (nm === 'Sitting_Idle_Loop' && n[3] && CLIP[n[3]]) return [n[3], n[2]];
  if (nm === 'Sitting_Talking_Loop' && n[4] && CLIP[n[4]]) return [n[4], n[2]];
  return [nm, 0];
}
// сидя на мокапе: актёр сидел на стуле выше нашего — ступни ушли бы под пол; ставим их на пол (бедро и голень сгибаются сами), подошва — как в клипе.
// Таз — на 4,5 см выше, чем в клипе: у мокапа ягодица и опущенная кисть уходили в сиденье на 3–4 см (World.clips); k — доля сидячего клипа в смеси
const SIT_UP = .045;
function seatFeet(C, k = 1) {
  { const pel = C.bone.pelvis; C.root.updateWorldMatrix(true, true); const wp = pel.getWorldPosition(new T.Vector3()); wp.y += SIT_UP * k * (C.ss || 1);
    pel.parent.updateWorldMatrix(true, false); pel.position.copy(pel.parent.worldToLocal(wp)); pel.updateWorldMatrix(false, true); }
  C.root.updateWorldMatrix(true, true); const fl = C.root.getWorldPosition(new T.Vector3()).y, want = fl + .1 * (C.ss || 1);
  for (const sd of ['l', 'r']) { const ft = C.bone['foot_' + sd]; if (!ft) continue; const wp = ft.getWorldPosition(new T.Vector3()); if (wp.y >= want) continue;
    const wq = ft.getWorldQuaternion(new T.Quaternion()); wp.y = want; const lp = C.root.worldToLocal(wp);
    ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, lp.x, lp.y, lp.z, [sd === 'l' ? .15 : -.15, .4, 1]); keepWorld(C, 'foot_' + sd, wq); }
  footFloor(C);
}
const SOFTC = new Set(['Idle_Loop', 'Idle_Talking_Loop', 'Walk_Loop', 'Sitting_Idle_Loop', 'Sitting_Talking_Loop', 'Crouch_Idle_Loop', 'Crouch_Fwd_Loop', 'Jog_Fwd_Loop', 'Sprint_Loop']);
// фоновые петли (стоит, говорит руками, сидит, моет): у них свои часы. Время кадра стоит, пока игра ждёт нажатия в конце реплики, —
// раньше все в этот момент замирали статуями (живыми оставались только глаза). Склейка без смены ракурса (вопрос → ответ, тот же план ближе)
// начинает время кадра с нуля — фон идёт дальше, поза не прыгает. Позы, у которых время стоит с самого начала кадра (STILL, «замер»), не трогаем
const AMB = /^(Idle_Loop|Idle_Talking_Loop|Sitting_Idle_Loop|Sitting_Talking_Loop|Crouch_Idle_Loop|Idle_TalkingPhone_Loop|Idle_Torch_Loop|Idle_FoldArms_Loop|Cmu_Mop_Loop)$/;
function ambClock(C, list, dT, cut) {
  const c0 = list.find(c => AMB.test(c[0])); if (!c0) { C.ambP = null; return list; }
  if (cut || C.ambP == null) { C.ambO = 0; C.ambMv = false; }
  else { const d = c0[1] - C.ambP;   // время кадра и часы игры идут от одних часов: в обычном кадре d = dT
    if (Math.abs(d - dT) < .02) { if (d > 1e-4) C.ambMv = true; }   // время кадра идёт
    else if (C.ambMv) C.ambO += dT - d; }                            // встало (ждём нажатия) или прыгнуло (склейка того же ракурса) — фон не замирает
  C.ambP = c0[1];
  return C.ambO ? list.map(c => AMB.test(c[0]) ? [c[0], c[1] + C.ambO, c[2], c[3]] : c) : list;
}
function act3Body(C, P, tt, dt, dT = dt, cut = false) {
  if (C.clip !== '§') { C.mixer.stopAllAction(); C.acts3 = C.acts3 || {}; for (const k in C.acts3) C.acts3[k].on3 = false; C.act = null; C.clip = '§'; }
  buildHands();
  const CL = C.clips || CLIP, used = new Set();
  let list = P.clips || [['Idle_Loop', tt, 1]];
  if (!P.still && !C.o.monster && !window.__NOLIFE) list = ambClock(C, list, dT, cut);
  if (P.talk) {   // говорит — стойка плавно переходит в «говорит руками» и обратно (паузы между словами не сбивают)
    const on = tt < (C.talkT || -1) ? 1 : 0; C.talkW = lerp(C.talkW ?? on, on, Math.min(1, (dt || 0) * 3));
    const TK = { Idle_Loop: 'Idle_Talking_Loop', Sitting_Idle_Loop: 'Sitting_Talking_Loop' }, tw = C.talkW * (P.talkK ?? 1);   // talkK < 1 — руки заняты делом (показывает, держит): «говорит руками» тише
    list = list.flatMap(c => TK[c[0]] ? [[c[0], c[1], (c[2] ?? 1) * (1 - tw)], [TK[c[0]], c[1], (c[2] ?? 1) * tw]] : [c]);
  }
  { const rc = C.reactBody && !(alive(C) && HEADREACT.has(C.reactBody)) && REACT[C.reactBody];   // реакция по реплике вплетается в позу: кивок («Yes» — один раз, потом держим), «нет» и руки скрещены — петлёй (у живых «да»/«нет» — головой, headLife)
    C.reactW = lerp(C.reactW ?? 0, rc ? 1 : 0, Math.min(1, (dt || 0) * 4)); if (rc) { if (C.reactClip !== rc) C.reactT0 = tt; C.reactClip = rc; }
    if (C.reactW > .01 && C.reactClip) list = [...list.map(c => [c[0], c[1], (c[2] ?? 1) * (1 - C.reactW)]), [C.reactClip, /_Loop$/.test(C.reactClip) ? tt : tt - (C.reactT0 ?? tt), C.reactW]];
    else C.reactClip = null; }
  list = list.map(c => { const [n2, off, k = 1] = natClip(C, c[0]); return n2 === c[0] ? c : [n2, c[1] * k + off, c[2], c[3]]; });
  let ws = 0; for (const c of list) ws += c[2] ?? 1;
  for (const [nm, time, w = 1, loop] of list) {
    const clip = CL[nm]; if (!clip) continue;
    let a = C.acts3[nm]; if (!a) a = C.acts3[nm] = C.mixer.clipAction(clip);
    if (!a.on3) { a.reset(); a.play(); a.on3 = true; }
    a.enabled = true; a.setEffectiveTimeScale(0); a.setEffectiveWeight((w || 0) / (ws || 1));
    const d = clip.duration; a.time = (loop ?? /_Loop$/.test(nm)) ? ((time % d) + d) % d : clamp(time, 0, d - 1e-3);
    used.add(nm);
  }
  for (const nm in C.acts3) if (!used.has(nm) && C.acts3[nm].on3) { C.acts3[nm].stop(); C.acts3[nm].on3 = false; }
  bonesRestore(C); C.mixer.update(0); bonesSnap(C);
  const sitW = list.reduce((s, c) => s + (/^Rk_Sit/.test(c[0]) ? c[2] ?? 1 : 0), 0);
  if (sitW > 0) seatFeet(C, sitW / (ws || 1));
  else if (list.some(c => /^(Rk_|Cmu_)/.test(c[0]) && (c[2] ?? 1) > .05)) footFloor(C);   // мокап: носок опорной ноги не под полом
  if (P.legs === 'feet') feetIn(C, P);
  if (P.pelvis || P.legs === 'plant') {   // таз сдвигается (присесть, перенести вес), ступни остаются где стояли — ноги сгибаются сами (IK)
    C.root.updateWorldMatrix(true, true);
    const feet = ['l', 'r'].map(sd => { const f = C.bone['foot_' + sd]; return [sd, f.getWorldPosition(new T.Vector3()), f.getWorldQuaternion(new T.Quaternion())]; });
    if (P.pelvis) { const pel = C.bone.pelvis, wp = pel.getWorldPosition(new T.Vector3()).add(_hv.set(P.pelvis[0], P.pelvis[1], P.pelvis[2]));
      pel.parent.updateWorldMatrix(true, false); pel.position.copy(pel.parent.worldToLocal(wp)); pel.updateWorldMatrix(false, true); }
    for (const [sd, wpos, wq] of feet) { const lp = C.root.worldToLocal(wpos.clone()); ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, lp.x, lp.y, lp.z, [sd === 'l' ? .15 : -.15, 0, 1]); keepWorld(C, 'foot_' + sd, wq); }
  }
  if (!P.still) breathe(C, tt, dT, P.br);   // до касаний рук: ладони останутся там, где их поставил кадр
  if (P.pain) painLife(C, dT, P.pain);
  if (!C.o.monster && list.every(c => SOFTC.has(c[0])))   // обычная стойка, речь, ходьба, сидя: кисти мягкие (если кадр не задал пальцы и в руке ничего нет)
    for (const sd of ['l', 'r']) if (!(P.fing && P.fing[sd]) && !(P.hold && P.hold[sd])) { setFingers(C, sd, 'soft', 1); if (!P.still) fingerLife(C, sd, tt); }
  if (C.o.monster) { buildHands(); for (const sd of ['l', 'r']) if (!(P.fing && P.fing[sd]) && !(P.hold && P.hold[sd])) hookHand(C, sd); }   // Жилец: пальцы всегда крючьями, когтями вперёд (клипы Quaternius сжимали их в кулаки — «боксёр»)
  if (P.fing) for (const sd of ['l', 'r']) if (P.fing[sd]) setFingers(C, sd, P.fing[sd][0], P.fing[sd][1] ?? 1);
  if (P.wig) for (const [bn, ax, a] of P.wig) wiggle(C, bn, ax, a);
  if (P.twist) { C.root.updateWorldMatrix(true, true); const up = new T.Vector3(0, 1, 0).applyQuaternion(C.root.getWorldQuaternion(new T.Quaternion()));
    for (const [bn, f] of [['spine_01', .25], ['spine_02', .35], ['spine_03', .4]]) { const b = C.bone[bn]; const wq = b.getWorldQuaternion(new T.Quaternion()).premultiply(new T.Quaternion().setFromAxisAngle(up, P.twist * f)); setWorldQ(b, wq); } }
  if (P.spine) leanTo(C, P.spine.to, P.spine.k ?? .4);
  if (P.gest) gestBeat(C, P, tt, dT);
  if (P.arm) for (const sd of ['l', 'r']) if (P.arm[sd]) reachArm(C, sd, P.arm[sd]);
  if (P.heels != null) heelFloor(C, P.heels);   // heels — высота пола (корень сидящего бывает приподнят над полом)
  if (P.after) P.after(C, tt);
  if (P.hold) for (const sd of ['l', 'r']) if (P.hold[sd]) holdProp(C, sd, P.hold[sd], dt);
  if (P.wear) for (const name in P.wear) wearProp(C, name, P.wear[name]);
}
// предмет на теле (рация на нагруднике, фонарь на поясе): [кость, смещение в осях кости, поворот]
function wearProp(C, name, [bone, off, rot]) {
  const pr = prop3(name), b = C.bone[bone], scene = C.root.parent; if (!b || !scene) return;
  if (pr.obj.parent !== scene) scene.add(pr.obj);
  b.updateWorldMatrix(true, false);
  const M = b.matrixWorld.clone().multiply(new T.Matrix4().compose(new T.Vector3(...off), new T.Quaternion().setFromEuler(new T.Euler(...(rot || [0, 0, 0]))), _hs));
  M.decompose(pr.obj.position, pr.obj.quaternion, _hv); pr.obj.visible = true; pr.C = C; pr.sd = 'wear'; pr.g = name;
}

/* ---------- живое поверх клипа: дыхание, пальцы, голова, взгляд (tools/anim/ANIMATION.md) ---------- */
// кто живой: Жилец, тело и «подделки» с серой кожей не дышат и не ёрзают — их неподвижность и есть улика
const alive = C => !window.__NOLIFE && !C.o.monster && !C.o.dead && !(C.o.grey > .3) && !C.o.jerk;   // window.__NOLIFE = 1 — сравнить без живого слоя
const _bx = new T.Vector3(), _bq = new T.Quaternion(), _bq2 = new T.Quaternion(), _be = new T.Vector3();
// повернуть кость на угол вокруг оси в осях персонажа (x — влево, y — вверх, z — вперёд). Ось переводится в оси кости — матрицы потомков
// не пересчитываются здесь (их пересчитают касания рук, IK и рендер), так дешевле на слабом телефоне
function rotChar(C, bn, ax, ay, az, ang) {
  const b = C.bone[bn]; if (!b || !ang) return;
  C.root.getWorldQuaternion(_bq2); _bx.set(ax, ay, az).applyQuaternion(_bq2);
  b.getWorldQuaternion(_bq); _bx.applyQuaternion(_bq.invert());
  b.quaternion.multiply(_bq2.setFromAxisAngle(_bx, ang));
}
// дыхание: на вдохе грудь чуть раскрывается, плечи поднимаются. Спокойно ~14 вдохов в минуту, в страхе — чаще и глубже,
// затаил дыхание («тсс», оцепенел) — едва-едва; пока говорит — мельче (воздух уходит на слова)
function breathe(C, tt, dT, br) {   // br = [глубина, частота] из кадра: раненый Фрэнк дышит тяжело и часто
  if (!alive(C)) return;
  const fs = C.fsNow || {}, scared = !!(fs.wide || fs.dart || fs.scream), held = !!fs.press;
  const amp = (held ? .2 * (1 - clamp(fs.press, 0, 1)) : scared ? 1.6 : 1) * (C.talk > .06 ? .5 : 1) * (br ? br[0] : 1);   // «тсс», оцепенел (press 1) — не дышит совсем
  C.brPh = (C.brPh ?? rnd() * 6.3) + dT * (held ? .1 : scared ? .42 : .23) * (br ? br[1] : 1) * 2 * Math.PI;
  C.brA = C.brA == null ? amp : lerp(C.brA, amp, Math.min(1, dT * 1.5));
  const s = Math.sin(C.brPh), inh = (s > 0 ? s : s * .7) * C.brA;   // вдох короче и заметнее выдоха
  if (Math.abs(inh) < 1e-3) return;
  rotChar(C, 'spine_02', 1, 0, 0, -.006 * inh); rotChar(C, 'spine_03', 1, 0, 0, -.014 * inh);
  rotChar(C, 'clavicle_l', 0, 0, 1, .03 * inh); rotChar(C, 'clavicle_r', 0, 0, 1, -.03 * inh);
}
// боль: раз в 3–6 с (пока молчит) — короткая судорога: корпус сгибается, голова уходит вниз, плечо поднимается, за ~1,4 с отпускает
function painLife(C, dT, k) {
  if (!alive(C)) return;
  const g = C.pn || (C.pn = { t: 9, d: 1.4, a: 0, next: 1 + rnd() * 2, w: 0 });
  g.w += dT; if (g.t >= g.d && g.w > g.next && C.talk < .06) { g.t = 0; g.d = 1.1 + rnd() * .6; g.a = (.6 + rnd() * .4) * k; g.w = 0; g.next = 3 + rnd() * 3; }
  if (g.t >= g.d) return; g.t += dT;
  const u = Math.min(g.t / g.d, 1), e = (u < .22 ? ease3(u / .22) : 1 - ease3((u - .22) / .78)) * g.a;
  rotChar(C, 'spine_02', 1, 0, 0, .05 * e); rotChar(C, 'spine_03', 1, 0, 0, .06 * e); rotChar(C, 'neck_01', 1, 0, 0, .1 * e);
  rotChar(C, 'clavicle_r', 0, 0, 1, -.06 * e); rotChar(C, 'spine_03', 0, 0, 1, .03 * e);
}
// пальцы не каменные: свободная кисть чуть «дышит» — каждый палец медленно сгибается на 1–3° сам по себе
function fingerLife(C, sd, tt) {
  if (!alive(C)) return; const ph = C.t0 * 3.1 + (sd === 'l' ? 1.7 : 0);
  for (let i = 0; i < 4; i++) { const a = .035 * Math.sin(tt * (.31 + i * .07) + ph + i * 1.3) + .018 * Math.sin(tt * .83 + i * 2.1 + ph);
    for (let j = 2; j <= 3; j++) { const b = C.bone[FING[i] + '_0' + j + '_' + sd]; if (b) b.quaternion.multiply(_Q3.setFromAxisAngle(_D.set(1, 0, 0), a)); } }
}
// жест на ударном слоге для свободной руки в позе без разговорного клипа (сидит на полу, на корточках): P.gest = 'r' | 'l' | 'lr'.
// Кисть приподнимается и подаётся к собеседнику на 5–9 см за 0,15 с и возвращается за ~0,5 с; поворот кисти в мире сохраняется
const _gh = new T.Vector3(), _gq = new T.Quaternion();
function gestBeat(C, P, tt, dT) {
  const g = C.gst || (C.gst = { t: 9, d: .6, a: 0, sd: 'r', next: 0 });
  if (C.onset && tt > g.next && rnd() < .55) { const s = P.gest.length > 1 ? (rnd() < .5 ? 'l' : 'r') : P.gest; g.t = 0; g.d = .55 + rnd() * .25; g.a = .6 + rnd() * .4; g.sd = s; g.next = tt + g.d + .2 + rnd() * .5; }
  g.t += dT; if (g.t >= g.d || !alive(C)) return;
  const u = g.t, e = (u < .15 ? ease3(u / .15) : 1 - ease3((u - .15) / (g.d - .15))) * g.a, sd = g.sd, sx = sd === 'l' ? 1 : -1;
  const hb = C.bone['hand_' + sd]; if (!hb) return; C.root.updateWorldMatrix(true, true);
  hb.getWorldQuaternion(_gq); const h = C.root.worldToLocal(hb.getWorldPosition(_gh));
  ik2(C, 'upperarm_' + sd, 'lowerarm_' + sd, 'hand_' + sd, h.x + sx * .025 * e, h.y + .055 * e, h.z + .07 * e, [sx * .8, -1, -.3]);
  keepWorld(C, 'hand_' + sd, _gq);
}
const ease3 = k => k * k * (3 - 2 * k);
// направление, повёрнутое по горизонтали (yaw) и вертикали (pitch) — в осях мира
function offDir(v, yaw, pitch) {
  if (!yaw && !pitch) return v;
  const h = Math.hypot(v.x, v.z) || 1e-6, a = Math.atan2(v.x, v.z) + yaw, e = clamp(Math.atan2(v.y, h) + pitch, -1.45, 1.45), c = Math.cos(e);
  return v.set(Math.sin(a) * c, Math.sin(e), Math.cos(a) * c);
}
// критически затухающая пружина (точно при любом шаге): голова трогается мягко и приходит без рывка — не «щелчок, потом медленно», как у lerp
function spring3(x, v, to, w, dt) {
  const e = Math.exp(-w * dt);
  for (const a of ['x', 'y', 'z']) { const c1 = x[a] - to[a], c2 = v[a] + w * c1; x[a] = to[a] + (c1 + c2 * dt) * e; v[a] = (c2 - w * (c1 + c2 * dt)) * e; }
}
// взгляд: перевёл глаза на далёкую цель — обычно моргает; говорит — начав фразу, часто отводит глаза (вспоминает, подбирает слова) и возвращает;
// слушает — смотрит на говорящего; молчит один — изредка уводит взгляд и возвращает. В страхе и «замер» — смотрит не отрываясь
function gazeLife(C, tt, dT, talking, hearing, cut, tgt) {
  const g = C.gz || (C.gz = { avT: 0, avD: 1, y: 0, p: 0, next: 1 + rnd() * 3, was: false, tgt: null });
  if (cut || !g.tgt) { g.avT = 0; g.next = .8 + rnd() * 2.5; g.tgt = tgt.clone(); }
  else { if (tgt.angleTo(g.tgt) > .35 && rnd() < .75) C.blinkReq = 1; g.tgt.copy(tgt); }
  const fs = C.fsNow || {}, stare = !!(fs.wide || fs.press || fs.dead || (C.pose && C.pose.stare));
  const avert = d => { g.avT = g.avD = d; g.y = (rnd() < .5 ? -1 : 1) * (talking ? .08 + rnd() * .1 : .1 + rnd() * .16); g.p = (rnd() < .65 ? -1 : 1) * (.025 + rnd() * .05); };
  if (talking && !g.was) { if (!stare && rnd() < .45) avert(.5 + rnd() * .8); if (rnd() < .3) C.blinkReq = 1; }
  if (!talking && g.was && rnd() < .5) C.blinkReq = 1;   // договорил — моргнул
  g.was = talking; g.next -= dT;
  if (g.next <= 0) { g.next = talking ? 2.5 + rnd() * 3.5 : 3.5 + rnd() * 5; if (!stare && !hearing && rnd() < .5) avert(talking ? .4 + rnd() * .6 : .6 + rnd() * 1.1); }
  if (g.avT > 0) { g.avT -= dT; const k = clamp(g.avT / .12, 0, 1) * clamp((g.avD - g.avT) / .08, 0, 1); C.avY = g.y * k; C.avP = g.p * k; } else C.avY = C.avP = 0;
  if (stare) C.avY = C.avP = 0;
}
// голова поверх клипа: медленный дрейф (не манекен); говорит — на ударе слова кивок, громче — голова чуть выше;
// слушает — в паузах говорящего иногда кивает («угу»), моргает, голова чуть склонена
function headLife(C, tt, dT, talking, hearing) {
  const g = C.hl || (C.hl = { ph: [0, 1, 2, 3, 4].map(() => rnd() * 9), env: 0, a: 1, hr: 0, nT: 9, nA: 0, nD: .4, nNext: 0, hLen: 0, hGap: 0 });
  const nod = (a, d) => { g.nT = 0; g.nA = a; g.nD = d; g.nNext = tt + d + .25 + rnd() * .5; };
  if (C.onset && tt > g.nNext && rnd() < .6) { nod((.02 + .04 * Math.min(1, g.env * 1.4)) * (rnd() < .2 ? -.7 : 1), .3 + rnd() * .14); if (rnd() < .25) C.brF = 1; }   // ударный слог — кивок, иногда бровь
  g.env += ((talking ? C.talk : 0) - g.env) * Math.min(1, dT * (C.talk > g.env ? 10 : 2.5));
  if (C.hear > .06) { g.hLen += dT; g.hGap = 0; }   // слушает: фраза говорящего кончилась (пауза после ≥ 0,9 с речи) — «угу»
  else if (g.hLen > 0) { g.hGap += dT; if (g.hGap > .25) { if (g.hLen > .9 && !talking) { const ok = C.hearOk !== false && !(C.fsNow || {}).wide && !(C.fsNow || {}).press;   // голосу из темноты и в страхе не поддакивают
      if (ok && rnd() < .5) nod(.028 + rnd() * .025, .45 + rnd() * .15); if (rnd() < .55) C.blinkReq = 1; if (ok && rnd() < .2) C.brF = 1; } g.hLen = 0; } }
  const frozen = 1 - .85 * clamp((C.fsNow || {}).press || 0, 0, 1);   // затаился, оцепенел — голова почти не шевелится
  g.a = lerp(g.a, (talking ? 1.6 + g.env : hearing ? 1.15 : 1) * frozen, Math.min(1, dT * 2)); g.hr = lerp(g.hr, hearing ? .03 : 0, Math.min(1, dT * 1.5));
  const p = g.ph; let yaw = g.a * (.016 * Math.sin(tt * .53 + p[0]) + .008 * Math.sin(tt * 1.31 + p[1]));
  let pit = g.a * (.011 * Math.sin(tt * .71 + p[2]) + .006 * Math.sin(tt * 1.87 + p[3])) + .035 * g.env;
  g.nT += dT; if (g.nT < g.nD) pit -= g.nA * Math.sin(Math.PI * g.nT / g.nD) ** 2;
  // реакция «нет» / «да» по реплике (react) — головой, на своей позе: два-три покачивания или два кивка и успокоился
  // (раньше всё тело на это время переходило в клип Quaternius — стойка бойца, сутулая спина — и обратно)
  const rb = HEADREACT.has(C.reactBody) ? C.reactBody : null; if (rb !== g.rb) { g.rb = rb; g.rT = 0; } else g.rT = (g.rT || 0) + dT;
  if (rb) { const u = g.rT, env = clamp(u / .15, 0, 1) * (1 - clamp((u - 1.15) / .5, 0, 1));
    if (rb === 'no') yaw += .2 * Math.sin(2 * Math.PI * 2.1 * u) * env; else pit -= .085 * (1 - Math.cos(2 * Math.PI * 1.8 * u)) * .5 * env; }
  return [yaw, pit, (.008 + .012 * g.a) * Math.sin(tt * .37 + p[4]) + g.hr];
}
const HEADREACT = new Set(['no', 'yes']);   // у живых людей «нет» и «да» — головой (headLife), а не клипом всего тела

function updateSkinned(C, t, dt) {
  // склейка (C.cut из draw) или персонаж снова появился после перерыва (Жилец мелькнул в глубине кадра): поза, взгляд и шаг — сразу, не перетекают из прошлого появления
  const tt = t + C.t0, P = C.pose, U = C.U, cut = C.cut || C.lastT == null || t - C.lastT > (window.__CUTGAP || .5) || t < C.lastT;   // __CUTGAP — для проб на медленной машине (tools/anim/*_probe.js)
  const dT = cut ? 0 : Math.max(0, t - C.lastT);   // сколько прошло на самом деле (на паузе — 0; больше 0,5 с — это уже склейка)
  C.cut = false; C.lastT = t; C.cutNow = cut;
  if (cut) { C.lookDir = null; C.prevP = null; C.walkV = null; C.flk = null; C.twist = null; }
  // реакция по реплике (beat.react в story.js): тело (кивок, «нет», руки скрещены) и/или лицо — 'no+worry', 'sad'
  { const rp = typeof C.react === 'string' ? C.react.split('+') : []; C.reactBody = rp.find(x => REACT[x]) || null; C.reactFace = rp.find(x => !REACT[x] && C.faceSets[x]) || null; }
  C.fsNow = C.faceSets[C.reactFace || C.faceSet] || {};
  if (C.talk > .06) C.talkT = tt + .7;   // паузы между словами не сбивают анимацию «говорит»
  const talking = tt < (C.talkT || -1);
  if ((C.hear || 0) > .06) C.hearT = tt + .6;   // слушает: говорит кто-то другой (в кадре, по телефону, по рации)
  const hearing = !talking && tt < (C.hearT || -1);
  C.talkingNow = talking; C.hearingNow = hearing;
  // ударный слог: слово началось после паузы (буквы реплики — движок знает, какое слово звучит) или голос стал громким после тишины
  C.onset = false;
  if (talking) { const sil = C.vis ? C.vis === 'sil' : C.talk < .1; if (!sil && (C.silT ?? 1) > .09) C.onset = true; C.silT = sil ? (C.silT ?? 1) + dT : 0; } else C.silT = 1;
  const OBJ = !!(C.v3 && P && typeof P === 'object');   // поза-объект: постановка act3Body (клипы по времени кадра, пальцы, касания, предметы)
  if (OBJ && P.look) C.look = new T.Vector3(...P.look);
  let want = OBJ ? C.clip : POSE[P] || 'Idle_Loop';
  if (C.v3 && (P === 'sitfloor' || P === 'sitpaper' || P === 'kneel')) want = 'Idle_Loop';   // на коленях: ноги и таз ставит kneel()
  if (talking && P === 'stand') want = POSE.talk;
  if (talking && P === 'sit') want = POSE.sittalk;
  if (!OBJ && C.reactBody && !(alive(C) && HEADREACT.has(C.reactBody)) && (P === 'stand' || P === 'talk')) want = REACT[C.reactBody];   // реакция по реплике: кивок, «нет», руки скрещены
  if (C.o.monster && P === 'walk') want = 'Zombie_Walk_Fwd_Loop';
  if (want !== C.clip) {
    const CL = C.clips || CLIP, a = C.mixer.clipAction(CL[natClip(C, want)[0]] || CL[want] || CL.Idle_Loop);
    a.reset().setEffectiveWeight(1).setEffectiveTimeScale(1); a.on3 = false;
    if (C.act && dt < .2 && !cut) { a.play(); C.act.crossFadeTo(a, .35, false); } else { C.mixer.stopAllAction(); a.play(); }   // на склейке — сразу новая поза (плавный переход в начале кадра выглядел как «встаёт в полный рост»)
    if (!C.act) a.time = (C.t0 * 1.7) % (a.getClip().duration || 1);
    if (P === 'lay') { C.mixer.stopAllAction(); a.play(); a.time = 0; a.timeScale = 0; }   // тело: первый кадр «лёжа», без движения
    C.act = a; C.clip = want;
  }
  // Жилец двигается рывками, как покадровая анимация: поза меняется ~7 раз в секунду
  let sdt = dt, still = false;
  if (C.o.stopmo) { C.smAcc = (C.smAcc || 0) + dt; if (C.smAcc < C.o.stopmo && C.smDone) still = true; else { sdt = C.smAcc; C.smAcc = 0; C.smDone = true; } }
  // покадровый Жилец между сменами позы стоит, но предметы каждый кадр прячутся (propsFrame) — то, что у него в руке (рация Гаса), ставим на место заново
  if (still && OBJ && P.hold) for (const sd of ['l', 'r']) if (P.hold[sd]) holdProp(C, sd, P.hold[sd], 0);
  if (!still) {
  // шаг под скорость: ноги идут ровно с той скоростью, с какой человек движется по полу (иначе «плывёт» или шагает на месте)
  let stride = 1;
  if ((P === 'walk' || P === 'back' || P === 'weak') && !C.o.monster) {
    const p = C.root.position, base = 1.03 * C.ss;
    if (C.prevP && dt > 0) { const v = Math.hypot(p.x - C.prevP.x, p.z - C.prevP.z) / dt; C.walkV = C.walkV == null ? v : C.walkV + (v - C.walkV) * Math.min(1, dt * 8); }
    (C.prevP || (C.prevP = new T.Vector3())).copy(p);
    stride = C.walkV == null ? 1 : clamp(C.walkV / base, 0, 1.9);
  }
  if (OBJ) act3Body(C, P, tt, sdt, C.o.stopmo ? sdt : dT, cut);
  else {
  if (P !== 'lay') C.act.timeScale = (P === 'back' ? -stride : P === 'monster' ? .3 : P === 'reach' ? .55 : C.o.monster ? .8 : stride) * (natClip(C, want)[2] || 1);
  // пальцы — в исходную позу до анимации: клип их почти не трогает, и кулак от швабры/стука иначе оставался навсегда
  for (const sd of ['l', 'r']) { for (const f of FING) for (let j = 1; j <= 3; j++) { const n = f + '_0' + j + '_' + sd; if (C.bone[n]) C.bone[n].quaternion.copy(C.rest[n]); }
    for (let j = 1; j <= 3; j++) { const n = 'thumb_0' + j + '_' + sd; if (C.bone[n]) C.bone[n].quaternion.copy(C.rest[n]); } }
  bonesRestore(C); C.mixer.update(Math.min(sdt, .2)); bonesSnap(C);
  if (/^Rk_Sit/.test(natClip(C, want)[0])) seatFeet(C);
  if (P !== 'lay') breathe(C, tt, dT);
  if (C.o.monster) { buildHands(); for (const sd of ['l', 'r']) hookHand(C, sd); }   // Жилец: пальцы крючьями, когтями вперёд (клип сжимал их в кулак)
  if (!C.o.monster && SOFTC.has(natClip(C, want)[0])) { buildHands(); for (const sd of ['l', 'r']) { setFingers(C, sd, 'soft', 1); fingerLife(C, sd, tt); } }   // кисти мягкие: клип Quaternius сжимал их в кулак
  }
  // ноги: у базовой анимации стойка «борца» (ступни широко) — сводим ступни под бёдра, подошва остаётся на полу
  const STAT = { stand: 1, talk: 1, knock: 1, press: 1, give: 1, paper: 1, reach: 1, hug: 1, mop: 1, slam: 1 };
  if (STAT[P] && !C.o.monster) {
    const ss = C.ss;
    for (const [sd, sx] of [['l', 1], ['r', -1]]) {
      const ft = C.bone['foot_' + sd]; ft.updateWorldMatrix(true, false); const fq = ft.getWorldQuaternion(new T.Quaternion());
      const fp = C.root.worldToLocal(ft.getWorldPosition(new T.Vector3()));
      const want = sx * (C.o.fem ? .085 : .12) * ss + (P === 'knock' ? sx * .015 : 0);
      ik2(C, 'thigh_' + sd, 'calf_' + sd, 'foot_' + sd, lerp(fp.x, want, .85), fp.y, fp.z + (sd === 'r' && P === 'knock' ? -.08 * ss : 0), [sx * .15, 0, 1]);
      keepWorld(C, 'foot_' + sd, fq);
    }
  }
  if (C.v3 && (P === 'sitfloor' || P === 'sitpaper' || P === 'kneel')) kneel(C, tt);
  if (C.v3 && !C.o.monster && !(OBJ && P.still)) footLock(C, dt);   // идёт: опорная стопа стоит на полу, а не едет (было ~26 % скорости шага)
  // руки поверх анимации
  if (P === 'knock' || P === 'slam' || P === 'press') {   // press — ладони прижаты к стеклу, без ударов (оно не стучит — только зовёт)
    // стук в стекло: левая ладонь лежит на стекле, правая рука кулаком стучит сериями по три удара, корпус чуть к стеклу
    const ss = C.ss, gz = (C.glassD ?? .42), ph = tt % 1.9;
    let hit = 0; for (const p0 of [0, .24, .48]) hit = Math.max(hit, Math.exp(-Math.pow((ph - p0 - .05) / .055, 2)));
    if (P === 'slam') hit = Math.min(1, .5 + .5 * Math.sin(tt * 2.3));
    if (P === 'press') hit = .1 + .1 * Math.sin(tt * 1.1);
    aimBone(C, 'spine_02', 'spine_03', [0, 1, .14], .7);
    if (P === 'slam' || P === 'press') {   // обе ладони на стекле
      ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', .22 * ss, 1.5 * ss, gz - .05, [.8, -1, -.3]); palm(C, 'l'); aimBone(C, 'hand_l', 'middle_01_l', [.2, 1, .3], 1);
      ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', -.22 * ss, 1.47 * ss, gz - .05 - hit * .03, [-.8, -1, -.3]); palm(C, 'r'); aimBone(C, 'hand_r', 'middle_01_r', [-.2, 1, .3], 1);
    } else {
      ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', .21 * ss, 1.36 * ss, gz - .05, [.8, -1, -.3]);
      palm(C, 'l'); aimBone(C, 'hand_l', 'middle_01_l', [.15, 1, .3], 1);   // ладонь прижата к стеклу, пальцы вверх
      ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', -.17 * ss, 1.44 * ss, gz - .16 + hit * .12, [-.9, -1, -.4]);
      aimBone(C, 'hand_r', 'middle_01_r', [-.1, .55, 1], .8);
      fist(C, 'r', 1);
    }
    C.knockHit = P === 'press' ? 0 : hit;
  } else if (P === 'mop') {
    // моет пол шваброй: корпус наклонён, руки на черенке, швабра ходит дугой перед ногами
    const ss = C.ss, sw = Math.sin(tt * 2.1), sw2 = Math.cos(tt * 2.1);
    aimBone(C, 'spine_01', 'spine_02', [sw * .12, 1, .32], .8); aimBone(C, 'spine_02', 'spine_03', [sw * .1, 1, .25], .7);
    const hx = sw * .45, hz = .78 + sw2 * .07;   // где тряпка на полу
    const D = new T.Vector3(-.1 - hx, 1.1 * ss - .02, .22 - hz).normalize();   // черенок от тряпки к правой руке
    const top = [hx + D.x * 1.22, .02 + D.y * 1.22, hz + D.z * 1.22], mid = [hx + D.x * .8, .02 + D.y * .8, hz + D.z * .8];
    ik2(C, 'upperarm_r', 'lowerarm_r', 'hand_r', top[0], top[1], top[2], [-.8, -.6, -.4]);
    ik2(C, 'upperarm_l', 'lowerarm_l', 'hand_l', mid[0], mid[1], mid[2], [.8, -.8, -.2]);
    fist(C, 'r', .9); fist(C, 'l', .9);
    // черенок проходит ровно через обе ладони (а не через запястья): точку хвата берём между запястьем и основанием среднего пальца
    if (C.mop) { C.mop.visible = true; const g = C.mop, pr = gripPt(C, 'r'), pl = gripPt(C, 'l'), d = pr.clone().sub(pl).normalize();
      const k = d.y > .05 ? (pl.y - .02) / d.y : 0, base = pl.clone().addScaledVector(d, -k);
      g.position.copy(base); g.quaternion.setFromUnitVectors(_UP, d); }
  } else if (P === 'give') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.1, -.6, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [0, -.1, 1], 1); fist(C, 'r', .55);
  } else if (P === 'sitpaper' && C.v3) {   // на коленях, поднял лист к камере обеими руками
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.22, -.3, .9], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.45, .55, .7], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.22, -.3, .9], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.45, .55, .7], 1);
  } else if ((P === 'sitfloor' || P === 'kneel') && C.v3) {   // на коленях, ладони на бёдрах
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.18, -.9, .3], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.05, -.35, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.18, -.9, .3], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.05, -.35, 1], 1);
  } else if (P === 'paper' || P === 'sitpaper') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.1, -.85, .45], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.45, .35, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.1, -.85, .45], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.45, .35, 1], 1);
  } else if (P === 'reach') {
    const sw = Math.sin(tt * 1.3) * .08;
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.15, .05 + sw, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [-.05, .1, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.15, .05 - sw, 1], 1); aimBone(C, 'lowerarm_l', 'hand_l', [.05, .1, 1], 1);
  } else if (P === 'eat') {
    // рвёт и тащит ко рту: руки к земле перед собой, рывками вверх
    const go = 1 - (C.eatHold || 0);   // eatHold — перестал есть (замер над телом, голова поднимается к объективу)
    const tr = go * Math.max(0, Math.sin(tt * 4.2)) ** 5, tl = go * Math.max(0, Math.sin(tt * 4.2 + 2.1)) ** 5;
    const bite = go * Math.pow(Math.max(0, Math.sin(tt * 2.6)), 6), yank = go * Math.pow(Math.max(0, Math.sin(tt * 2.6 - .9)), 8);   // клюёт в тело и рывком отдирает кусок
    aimBone(C, 'spine_02', 'spine_03', [Math.sin(tt * 7) * .08 * yank, .55 - bite * .35 + yank * .3, 1], .8);
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.2, -.75, .75], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.1, -.75 + tr * 1.1, .7 - tr * .3], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.2, -.75, .75], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.1, -.75 + tl * 1.1, .7 - tl * .3], 1);
    footFloor(C);   // на корточках носок задней ноги уходил под пол на 10 см
  } else if (P === 'embrace') {
    // кинулась на шею: руки вверх и в стороны, мимо камеры, локти согнуты — обнимает того, кто смотрит
    const k = clamp((C.actT ?? 9) / .45, 0, 1), sq = Math.sin(tt * 1.7) * .05;
    aimBone(C, 'spine_02', 'spine_03', [0, 1, .25 * k], .7);
    // плечо — вперёд-вверх и чуть в сторону, предплечье загибается внутрь, за шею того, кто смотрит (а не руки в стороны буквой «Т»)
    aimBone(C, 'upperarm_r', 'lowerarm_r', [lerp(-.2, -.5, k), lerp(-.8, .42 + sq, k), lerp(.3, .8, k)], 1); aimBone(C, 'lowerarm_r', 'hand_r', [lerp(0, .6, k), lerp(-.8, .22, k), lerp(.5, .55, k)], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [lerp(.2, .5, k), lerp(-.8, .42 - sq, k), lerp(.3, .8, k)], 1); aimBone(C, 'lowerarm_l', 'hand_l', [lerp(0, -.6, k), lerp(-.8, .22, k), lerp(.5, .55, k)], 1);
    fist(C, 'r', .35 * k); fist(C, 'l', .35 * k);
  } else if (P === 'smoke') {
    smokeArms(C, C.actT ?? 99, tt);
  } else if (P === 'hug') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [.2, -.1, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.9, 0, .4], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [-.2, -.1, 1], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.9, 0, .4], 1);
  }
  // тело на полу бьётся: руки молотят, ноги дёргаются, спина выгибается; потом всё реже — и только пальцы
  if (P === 'lay' && C.struggle > 0) {
    const s = C.struggle, f = tt * 9;
    wiggle(C, 'upperarm_l', [0, 0, 1], s * (Math.sin(f) * .9 - .5)); wiggle(C, 'upperarm_r', [0, 0, 1], s * (Math.sin(f * 1.13 + 2) * .9 + .5));
    wiggle(C, 'lowerarm_l', [0, 0, 1], s * Math.sin(f * 1.7) * .7); wiggle(C, 'lowerarm_r', [0, 0, 1], s * Math.sin(f * 1.5 + 1) * .7);
    wiggle(C, 'thigh_l', [1, 0, 0], s * Math.sin(f * .8) * .45); wiggle(C, 'thigh_r', [1, 0, 0], s * Math.sin(f * .8 + 2.6) * .45);
    wiggle(C, 'calf_l', [1, 0, 0], s * Math.abs(Math.sin(f * .8)) * .6); wiggle(C, 'calf_r', [1, 0, 0], s * Math.abs(Math.sin(f * .8 + 2.6)) * .6);
    wiggle(C, 'spine_02', [1, 0, 0], s * Math.sin(f * .55) * .25); wiggle(C, 'neck_01', [0, 1, 0], s * Math.sin(f * .9) * .5);
    fist(C, 'r', .6 + .4 * Math.sin(f * 2)); fist(C, 'l', .6 + .4 * Math.sin(f * 2.3));
  }
  if (P === 'lay' && C.twitch > 0) { const w = C.twitch * Math.pow(Math.max(0, Math.sin(tt * 3.1)), 12); wiggle(C, 'hand_r', [0, 0, 1], w * .6); fist(C, 'r', w); }
  if (P === 'lay') layFloor(C);   // судороги не уводят руки, ноги и спину под пол
  // ноги не держат: идёт, оседая на каждом шаге, корпус ведёт в сторону
  if (P === 'weak') { aimBone(C, 'spine_01', 'spine_02', [Math.sin(tt * 2.2) * .25, 1, .35], .8); wiggle(C, 'calf_l', [1, 0, 0], .35 + .25 * Math.sin(tt * 4.4)); wiggle(C, 'calf_r', [1, 0, 0], .35 - .25 * Math.sin(tt * 4.4)); }
  // голова смотрит на цель (обычно в камеру): доворачиваем «нос» головы к цели поверх анимации, поровну шее и голове
  const k = Math.min(1, sdt * (C.o.stopmo ? 3 : 7)), hb = C.bone.Head, life = alive(C) && !(OBJ && P.still), eyesLive = life && !C.o.noBlink;
  hb.updateWorldMatrix(true, false); hb.getWorldPosition(_a);
  C.root.getWorldQuaternion(_q2);
  if (C.look) _b.copy(C.look).sub(_a).normalize();
  else _b.set(Math.sin(tt * .4) * .06, -.05, 1).normalize().applyQuaternion(_q2);
  if (C.o.jerk && !C.noTilt && Math.sin(tt * 1.3) > .985) _b.x += (rnd() - .5) * .8;
  // глаза отводит (вспоминает, подбирает слова) — глаза целиком, голова — на треть
  if (eyesLive) gazeLife(C, tt, dT, talking, hearing, cut, _b); else C.avY = C.avP = 0;
  _be.copy(_b); if (C.avY || C.avP) { offDir(_be, C.avY, C.avP); offDir(_b, C.avY * .35, C.avP * .35); }
  // не дальше, чем позволяет шея: ограничиваем угол от направления тела
  _c.set(0, 0, 1).applyQuaternion(_q2);
  const ang = _b.angleTo(_c), hm = C.o.headMax || 1.15; if (ang > hm) { _q.setFromUnitVectors(_c, _b); _q.slerp(_pq.identity(), 1 - hm / ang); _b.copy(_c).applyQuaternion(_q); }
  // голова доворачивается пружиной (мягко трогается и приходит без рывка); глаза — впереди головы: на новую цель переходят сразу (саккада),
  // голова догоняет, и глаза возвращаются к центру — как у людей. У Жильца — прежний рывковый доворот
  if (!C.lookDir) { C.lookDir = _b.clone(); (C.lookV || (C.lookV = new T.Vector3())).set(0, 0, 0); }
  else if (!window.__NOLIFE && !C.o.stopmo && !C.o.jerk) { spring3(C.lookDir, C.lookV || (C.lookV = new T.Vector3()), _b, C.o.headW || 8, sdt); C.lookDir.normalize(); }
  else C.lookDir.lerp(_b, k).normalize();
  if (!C.eyeDir || cut) C.eyeDir = _be.clone(); else C.eyeDir.lerp(_be, 1 - Math.exp(-sdt * 40)).normalize();
  // голова не стоит как у манекена: дрейф, кивки на ударных словах, «угу» в паузах говорящего, лёгкий наклон
  const LD = _ld.copy(C.lookDir); C.rollNow = 0;
  if (life) { const [hy, hp, hr] = headLife(C, tt, dT, talking, hearing); offDir(LD, hy, hp); C.rollNow = hr; }
  else if (talking && !C.o.monster) { LD.y += Math.sin(tt * 3.1) * .035 + Math.sin(tt * 1.3) * .02; LD.x += Math.sin(tt * .9) * .025; LD.normalize(); }
  if (C.no > 0) { _q.setFromAxisAngle(_UP, Math.sin(tt * 6.5) * .42 * C.no); LD.applyQuaternion(_q); }   // мотает головой: «нет, нет, нет»
  // корпус помогает голове: взгляд в сторону — грудь доворачивается на треть (не больше ~24°), шея и голова — остальное (раньше крутилась одна шея).
  // Не трогаем, если руки держат что-то или тянутся к точке (плечи увели бы кисти с цели)
  if (life && !(P && (P.arm || P.hold)) && C.bone.spine_02 && C.bone.spine_03) {
    let yw = Math.atan2(LD.x, LD.z) - Math.atan2(_c.x, _c.z); yw = Math.atan2(Math.sin(yw), Math.cos(yw));
    const tw = clamp(yw * .3, -.42, .42); C.twist = cut || C.twist == null ? tw : C.twist + (tw - C.twist) * Math.min(1, sdt * 4);
    if (Math.abs(C.twist) > .004) for (const [bn, f] of [['spine_02', .4], ['spine_03', .6]]) { const b = C.bone[bn]; b.updateWorldMatrix(true, false);
      const bw = b.getWorldQuaternion(_twQ).premultiply(_q3.setFromAxisAngle(_UP, C.twist * f)); b.parent.getWorldQuaternion(_pq); b.quaternion.copy(_pq.invert().multiply(bw)); _pq.identity(); b.updateWorldMatrix(false, true); }
  }
  for (const [bn, f] of [['neck_01', .45], ['Head', .9]]) {
    const b = C.bone[bn]; b.updateWorldMatrix(true, false);
    const bw = b.getWorldQuaternion(new T.Quaternion());
    const cur = C.headFwd.clone().applyQuaternion(hb.getWorldQuaternion(new T.Quaternion()));
    _q.setFromUnitVectors(cur, LD); _q.slerp(_pq.identity(), 1 - f);
    // Жилец: голова набок, и время от времени шея резко «ломается» почти горизонтально (на скримере — прямо, лицом в лицо)
    const tl = C.tiltNow ?? C.o.tilt;   // tiltNow — наклон головы, заданный кадром (голова медленно ложится набок, не сводя глаз)
    if (tl && bn === 'Head' && !C.noTilt) { const snap = C.o.jerk && P !== 'eat' ? Math.pow(Math.max(0, Math.sin(tt * .9 + 1)), 24) * 1.1 : 0; const r = new T.Quaternion().setFromAxisAngle(LD, (Math.sin(tt * .3) * .05 + tl + snap) * (C.o.flip ? -1 : 1)); _q.premultiply(r); }
    else if (C.rollNow && bn === 'Head') _q.premultiply(_q3.setFromAxisAngle(LD, C.rollNow));   // живой: голова чуть склонена и покачивается
    bw.premultiply(_q); b.parent.getWorldQuaternion(_pq); b.quaternion.copy(_pq.invert().multiply(bw)); _pq.identity();
    b.updateWorldMatrix(false, true);
  }
  if (C.o.monster) C.headBase = (C.headBase || new T.Quaternion()).copy(C.bone.Head.quaternion);
  }
  // Жилец: голова иногда бьётся в мелкой бешеной тряске (как на засвеченной плёнке) — каждый кадр, мимо покадровой рывковости;
  // на броске сцена задаёт тряску сама (C.shake)
  if (C.o.monster && C.headBase) {
    const burst = P !== 'eat' && P !== 'lay' && (tt % 6.7) < .38 ? .35 : 0, a = Math.max(C.shake || 0, burst);
    C.bone.Head.quaternion.copy(C.headBase);
    if (a > 0) { _e3.set((rnd() - .5) * a, (rnd() - .5) * a * 1.8, (rnd() - .5) * a * .8); C.bone.Head.quaternion.multiply(_q3.setFromEuler(_e3)); }
    C.bone.Head.updateWorldMatrix(false, true);
  }
  if (C.strands) C.strands.forEach((st, i) => { st.rotation.x = .08 + Math.sin(tt * 1.1 + i) * .05; st.rotation.z = Math.sin(tt * .8 + i * 2) * .04; });
  // лицо
  C.blinkT -= dt; let blink = 0; if (C.blinkT < 0) { blink = 1; if (C.blinkT < -.12) C.blinkT = 2 + rnd() * 4; }
  const fs = C.fsNow;
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
      C.brT = (C.brT ?? 2 + rnd() * 4) - dt; if (C.brT < 0) { C.brT = talking ? 3 + rnd() * 4 : 6 + rnd() * 8; C.brF = 1; }   // молча брови «думают» реже, чем на словах
      if (C.brF > 0) { up += Math.sin((1 - C.brF) * Math.PI) * .45; C.brF -= dt * 2.2; }
    }
    const B = C.browU.value, kb = cut ? 1 : Math.min(1, dt * 9); B.x += (up - B.x) * kb; B.y += (inn - B.y) * kb;
  }
  U.uScream.value = C.scream || fs.scream || 0;
  U.uGore.value = C.gore || 0;
  if (C.cig && P !== 'smoke') { C.cig.visible = C.lighter.visible = false; C.puffs.forEach(s => (s.visible = false)); C.lighterOn = 0; }
  C.keys.visible = P === 'give'; C.paper.visible = (P === 'paper' || P === 'sitpaper') && !C.sheet; if (C.mop && P !== 'mop') C.mop.visible = false;
  if (C.sheet) {   // лист между ладонями, развёрнут к тому, кому показывают (C.look — обычно камера)
    C.sheet.visible = P === 'sitpaper' || (OBJ && !!P.sheet);   // поза-объект: P.sheet — куда развернуть лист (обычно камера)
    if (C.sheet.visible) { const a = gripPt(C, 'l'), b = gripPt(C, 'r'); C.sheet.position.copy(a).add(b).multiplyScalar(.5).add(_m1.set(0, .07 * C.ss, .03));
      C.root.updateWorldMatrix(true, false); const at = OBJ && Array.isArray(P.sheet) ? _m1.set(P.sheet[0], P.sheet[1], P.sheet[2]) : C.look; if (at) C.sheet.lookAt(at); C.sheet.rotateZ(Math.sin(tt * 1.7) * .03); }
  }
  if (OBJ && P.post) P.post(C, tt);   // поверх всего (и взгляда): голова за пределом человеческого поворота, тело вверх ногами на потолке и т. п.
  if (C.v3) faceV3(C, tt, dt);
}

/* ---------- люди v3 (MakeHuman, CC0): материалы, сборка, мимика ---------- */
// материал части тела: текстура из модели (в «Кино» — PBR с картой нормалей у ткани и волос).
// Кожа и ткань знают мокрость (темнее и блестит), серость (неживое), кровь и тёмные глазницы.
// vB — точка тела в позе покоя в метрах (uQ переводит сжатые координаты meshopt в метры), поэтому пятна не «плывут» при движении
const LUMA = 'dot(diffuseColor.rgb, vec3(.3, .59, .11))';
// «Кино»: кожа, глаза, волосы и ткань без пластика (правила — tools/art/LOOK.md). Свет каждой лампы для этих частей считается по-своему:
// кожа — свет заходит за терминатор (рассеяние под кожей, красный — дальше всех), блик слабее пластика (F0 кожи ≈ .028) плюс узкий «жирный»;
// волосы — блик вдоль прядей полосой (Kajiya–Kay: первый белый, второй в цвет волос), мягкий спад света; ткань — ворс светится по краю.
const HUM_NOISE = `float vn3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
  return mix(mix(mix(hh3(i), hh3(i + vec3(1., 0., 0.)), f.x), mix(hh3(i + vec3(0., 1., 0.)), hh3(i + vec3(1., 1., 0.)), f.x), f.y),
    mix(mix(hh3(i + vec3(0., 0., 1.)), hh3(i + vec3(1., 0., 1.)), f.x), mix(hh3(i + vec3(0., 1., 1.)), hh3(i + vec3(1., 1., 1.)), f.x), f.y), f.z); }
`;
// кожа: лоб и нос жирнее (ниже шероховатость), по лицу пятнами ±; поры — рельеф в 1 мм, гаснет, когда пиксель крупнее поры (без ряби)
const SKIN_ROUGH = `  { vec3 q = vB - (uEyeL3 + uEyeR3) * .5;
    float tz = smoothstep(.045, .02, abs(q.x)) * smoothstep(.012, .025, q.y) * smoothstep(.085, .06, q.y) + smoothstep(.018, .008, abs(q.x)) * smoothstep(-.06, -.045, q.y) * smoothstep(.01, -.005, q.y);
    roughnessFactor = clamp(roughnessFactor - .15 * tz * smoothstep(-.045, -.015, q.z) + .14 * (vn3(vB * 55.) - .5), .3, .85); }
`;
const SKIN_PORES = `
  { float mpp = length(fwidth(vB)) * 1000., hp = vn3(vB * 950.) * .65 + vn3(vB * 2600.) * .35;   // mpp — мм на пиксель; высота пор ~0,15 мм
    vec2 dh = vec2(dFdx(hp), dFdy(hp)) * 1.5e-4 * smoothstep(1.5, .45, mpp);
    vec3 sx = dFdx(-vViewPosition), sy = dFdy(-vViewPosition), r1 = cross(sy, normal), r2 = cross(normal, sx); float det = dot(sx, r1);
    vec3 nn = abs(det) * normal - sign(det) * (dh.x * r1 + dh.y * r2); normal = dot(nn, nn) > 1e-24 ? normalize(nn) : normal; }
`;
// саван Жильца (поза покоя модели tenant, рост 2,43 м: шея ~2.04, плечи 1.92, грудь ~1.75–1.88, бёдра 1.24, колени .66): силуэт — грудь плоская, ткань свободнее книзу,
// внутренние стороны бёдер сведены — подол, а не штанины; руки и голени голые
const SHROUD_VS = `
  { float qs = length(uQ[0].xyz), body = mix(smoothstep(.27, .23, abs(vB.x)), smoothstep(.4, .34, abs(vB.x)), smoothstep(1.32, 1.22, vB.y));
    float inG = body * smoothstep(2.04, 1.95, vB.y) * smoothstep(.46, .6, vB.y);
    float bust = smoothstep(1.62, 1.7, vB.y) * smoothstep(1.92, 1.82, vB.y) * smoothstep(.2, .1, abs(vB.x));
    transformed.z -= bust * max(0., vB.z - .14) * .9 / qs;
    transformed += normalize(normal) * inG * mix(.014, .05, smoothstep(1.28, .62, vB.y)) / qs;
    transformed.x -= sign(vB.x) * smoothstep(1.2, 1.02, vB.y) * smoothstep(.46, .7, vB.y) * smoothstep(0., .5, -sign(vB.x) * normal.x) * .05 / qs; }
`;
const SHROUD_VN = `
  { vec3 b0 = (uQ * vec4(position, 1.)).xyz; float bust = smoothstep(1.6, 1.7, b0.y) * smoothstep(1.94, 1.82, b0.y) * smoothstep(.21, .1, abs(b0.x)) * step(0., objectNormal.z);
    objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .3, objectNormal.y * .3, 1.), bust * .85)); }   // и в свете грудь плоская
`;
// складки рубахи — рельефом в нормали: свет ложится полосами, ткань читается тканью, а не кожей
const SHROUD_N = `
  { vec3 px = dFdx(vB), py = dFdy(vB); float aa = 1. - smoothstep(.06, .2, length(px) + length(py)), h0 = shH(vB, aa);   // рельеф по разности высот в соседних пикселях,
    vec2 dh = vec2(shH(vB + px, aa) - h0, shH(vB + py, aa) - h0) * gCloth;                                                 // а не dFdx(высоты): та одна на квадрат 2×2 — «лесенка» вблизи
${CINE ? `    float ak = 1. - smoothstep(.0012, .004, length(px) + length(py)), k0 = skH(vB, ak);
    dh += vec2(skH(vB + px, ak) - k0, skH(vB + py, ak) - k0) * (1. - gCloth);
` : ''}    vec3 sx = dFdx(-vViewPosition), sy = dFdy(-vViewPosition), r1 = cross(sy, normal), r2 = cross(normal, sx); float det = dot(sx, r1);
    vec3 nn = abs(det) * normal - sign(det) * (dh.x * r1 + dh.y * r2); normal = dot(nn, nn) > 1e-24 ? normalize(nn) : normal; }
`;
// высота складок рубахи: крупные, неровные, книзу глубже (14 по кругу — висит тканью; 30 читались вельветом и рябили муаром); мелкий шум ткани гаснет вдали (aa)
const SHROUD_H = `float shH(vec3 p, float aa) { return sin(atan(p.x, p.z + .3 + 1e-5) * 14. + vn3(p * 6.) * 5.) * (.005 + .009 * smoothstep(1.5, .6, p.y)) + vn3(vec3(p.x * 60., p.y * 6., p.z * 60.)) * .0025 * aa; }
`;
// ткань: грязно-белая, складки вниз, пятна, рваный подол с тёмной мокрой каймой; на голых руках и ногах — жилы и вены
const SHROUD_FS = `float gCloth, gWet; vec3 gClothC;
  { float body = mix(smoothstep(.27, .23, abs(vB.x)), smoothstep(.4, .34, abs(vB.x)), smoothstep(1.32, 1.22, vB.y));
    float hemY = .56 + .07 * (vn3(vec3(vB.x * 26., 0., vB.z * 26.)) - .5) + .03 * (hh3(vec3(floor(vB.x * 60.), 3., floor(vB.z * 60.))) - .5);
    gCloth = body * smoothstep(1.98, 1.95, vB.y + .03 * cos(vB.x * 16.)) * smoothstep(hemY - .012, hemY + .012, vB.y);
    float fph = atan(vB.x, vB.z + .3 + 1e-5) * 14. + vn3(vB * 6.) * 5., fold = sin(fph) * (.08 + .14 * smoothstep(1.5, .6, vB.y)) * (1. - smoothstep(.6, 1.6, fwidth(fph)));   // вдали складки сливаются в тон, без муара
    float dirt = smoothstep(.55, .78, vn3(vB * 8.5)) * .55 + smoothstep(1.1, .55, vB.y) * .35;
    gWet = smoothstep(hemY + .2, hemY + .02, vB.y) * gCloth;
    vec3 cloth = vec3(.36, .34, .3) * (1. + fold) * (1. - .4 * dirt) * mix(vec3(1.), vec3(.74, .6, .42), dirt * .85);
    cloth *= 1. - .55 * gWet; gClothC = mix(cloth, vec3(.06, .055, .05), .4 * gWet * smoothstep(.4, .7, vn3(vB * 30.)));
    float arm = smoothstep(.34, .44, abs(vB.x)) * smoothstep(1.78, 1.86, vB.y);   // руки в позе «Т» лежат вдоль x: жилы на руках идут вдоль руки, на ногах и шее — вдоль y
    float vnv = vn3(mix(vec3(vB.x * 38., vB.y * 9., vB.z * 38.), vec3(vB.x * 9., vB.y * 38., vB.z * 38.), arm)), vw = fwidth(vnv);
    float vein = (1. - smoothstep(0., .03 + vw, abs(vnv - .5))) * smoothstep(.09, .03, vw) * smoothstep(.42, .62, vn3(vB * 4. + 9.)) * (1. - gCloth);   // обрывками (не замкнутые кольца, как изолинии на карте); вдали гаснут — без ряби
    diffuseColor.rgb *= 1. - .34 * vein * vec3(1., .8, .7); }
`;
// кожа Жильца в «Кино»: сухая, бугристая, мелкие трещинки пятнами (крупная сетка на кистях читалась узором «мозг»; рельеф — в SHROUD_N); ak гасит трещины, когда пиксель крупнее их
const SKIN_MH = `float skH(vec3 p, float ak) { float cr = 1. - abs(vn3(p * 170.) * 2. - 1.); return -pow(max(cr, 0.), 10.) * .00024 * ak * smoothstep(.3, .7, vn3(p * 7. + 5.)) + (vn3(p * 22.) - .5) * .0012; }
`;
const RE_ARGS = 'const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight';
const HUM_RE = {
  skin: `void RE_Direct_Hum( ${RE_ARGS} ) {
	float nl = dot( geometryNormal, directLight.direction );
	vec3 w = vec3( .5, .24, .16 ), wrapD = pow( clamp( ( nl + w ) / ( 1. + w ), 0., 1. ), vec3( 1. ) + w );
	vec3 irr = saturate( nl ) * directLight.color;
	PhysicalMaterial m2 = material; m2.roughness = max( material.roughness * .5, .14 );
	reflectedLight.directSpecular += irr * ( BRDF_GGX( directLight.direction, geometryViewDir, geometryNormal, material ) + .22 * BRDF_GGX( directLight.direction, geometryViewDir, geometryNormal, m2 ) );
	reflectedLight.directDiffuse += wrapD * directLight.color * BRDF_Lambert( material.diffuseColor );
}`,
  hair: `float gHairS = 0.;
void RE_Direct_Hum( ${RE_ARGS} ) {
	float nl = dot( geometryNormal, directLight.direction );
	reflectedLight.directDiffuse += saturate( nl * .7 + .3 ) * saturate( nl + .4 ) * directLight.color * BRDF_Lambert( material.diffuseColor );
	vec3 tv = ( viewMatrix * vec4( 0., -1., 0., 0. ) ).xyz; tv -= geometryNormal * dot( geometryNormal, tv );   // пряди — вниз; на макушке (нормаль вверх) направление не определено —
	float tl = length( tv ); tv /= max( tl, 1e-3 );                                                                   // там блик полосой гаснет (иначе пятна квадратами)
	vec3 h = normalize( directLight.direction + geometryViewDir );
	float t1 = dot( tv, h ) + gHairS, t2 = t1 - .12;
	float s1 = pow( sqrt( max( 0., 1. - t1 * t1 ) ), 110. ), s2 = pow( sqrt( max( 0., 1. - t2 * t2 ) ), 26. );
	float up = dot( geometryNormal, normalize( ( viewMatrix * vec4( 0., 1., 0., 0. ) ).xyz ) );   // макушка и верх головы — без полосы (карточки там лежат как попало, был пёстрый блик)
	reflectedLight.directSpecular += directLight.color * smoothstep( -.15, .35, nl ) * smoothstep( .35, .8, tl ) * ( 1. - smoothstep( .3, .65, up ) ) * ( s1 * .045 + s2 * .06 * material.diffuseColor );
}`,
  cloth: `void RE_Direct_Hum( ${RE_ARGS} ) {
	float nl = dot( geometryNormal, directLight.direction ), nv = saturate( dot( geometryNormal, geometryViewDir ) );
	vec3 irr = saturate( nl ) * directLight.color;
	reflectedLight.directSpecular += irr * BRDF_GGX( directLight.direction, geometryViewDir, geometryNormal, material );
	reflectedLight.directDiffuse += ( irr + directLight.color * saturate( nl + .35 ) * pow( 1. - nv, 4. ) * .45 ) * BRDF_Lambert( material.diffuseColor );   // ворс по краю
}`,
};
function humanMat(C, n, kind, Q) {
  const o = C.o, src = n.material, cut = kind === 'brows' || kind === 'lashes' || kind === 'hair' || (kind === 'eyes' && o.deadEyes == null), cloth = kind.startsWith('cloth');
  const R = { skin: CINE ? .6 : .64, eyes: .06, brows: .85, lashes: .85, teeth: .3, tongue: .4, hair: CINE ? .68 : .55 }[kind] ?? .86;   // в «Кино» волосы матовее: широкий блик делал из них лакированную шапку; блеск — прядями (hh3 ниже) и контровым светом
  let tint = null; for (const k in (o.tint || {})) if (kind === k || kind.includes(k)) tint = o.tint[k];
  const p = { map: src.map || null };
  if (typeof tint === 'number') p.color = tint;
  if (kind === 'eyes' && o.eyeC != null) p.color = o.eyeC;
  if (kind === 'eyes' && o.deadEyes != null) { p.map = null; p.color = o.deadEyes; }
  if (kind === 'teeth') p.color = o.monster ? 0x8a8070 : 0xd8d0c2;
  if (CINE && src.normalMap && (cloth || kind === 'hair')) p.normalMap = src.normalMap;
  if (cut) Object.assign(p, kind === 'eyes' ? { alphaTest: .3 } : { alphaTest: kind === 'hair' ? .18 : .25, side: T.DoubleSide });   // волосы одинаково в обоих видах: в «Ретро» порог .3 срезал тонкие пряди — Лили была лысой   // в «Кино» у волос остаются тонкие пряди по краю — линия лба мягче   // глаз: внешняя роговица прозрачная — иначе радужку не видно
  const m = ps1(litMat(p, R));
  if (CINE && cut && kind !== 'eyes') m.alphaToCoverage = true;
  if (CINE && kind === 'eyes') m.envMapIntensity = .9;
  if (CINE && (kind === 'skin' || cloth)) m.envMapIntensity = .22;   // иначе на контровом свете кожа светится ореолом
  if (CINE && kind === 'hair') { m.envMapIntensity = .1; if (m.normalMap) m.normalScale.set(.45, .45); }   // лампы потолка отражались в макушке золотым «обручем» (пробор у Лили); блеск волос — прядями, не зеркалом, рельеф карты мягче
  if (CINE && (kind === 'teeth' || kind === 'tongue')) { m.envMapIntensity = .06; m.roughness = .55; }   // во рту нет бликов от комнаты
  const re = Array.isArray(tint) ? tint : null, U = Object.assign({}, C.HU, { uQ: { value: Q }, uTint: { value: new T.Color(re ? re[0] : 0xffffff) }, uGain: { value: re ? re[1] : 1 } });
  const hem = /pants|jeans|trousers/.test(kind) && C.hemY != null ? C.hemY : null;
  const infl = /sweater|shirt|hoodie/.test(kind) && C.hemY != null ? .007 / Q.getMaxScaleOnAxis() : 0;   // низ кофты чуть наружу — лежит поверх брюк и на шаге
  const shroud = kind === 'skin' && o.shroud;   // Жилец: длинная грязная рваная рубаха-саван от шеи ниже колен (была голая серая фигура)
  const hre0 = CINE && (!o.monster || kind === 'hair') ? HUM_RE[kind === 'skin' ? 'skin' : kind === 'hair' ? 'hair' : cloth ? 'cloth' : ''] : null;
  const hre = hre0 && o.monster ? hre0.replace('( s1 * .045 + s2 * .06 * material.diffuseColor )', '( s1 * .045 + s2 * .06 * material.diffuseColor ) * .3') : hre0;   // у Жильца блик прядей слабее: кольцом по макушке читался как обруч   // у Жильца своя кожа (мёртвая, мокрая) — как было; волосы — прядями, как у всех (обычный блик делал из мокрых чёрных прядей белые пластиковые ленты)
  const claws = o.monster && kind === 'skin';   // Жилец: ногти-когти и почерневшие кончики пальцев (aNail — clawHands)
  if (shroud) m.extensions = Object.assign({}, m.extensions, { derivatives: true });   // складки рубахи — dFdx (WebGL1 в «Ретро»)
  const snap = m.onBeforeCompile;
  m.onBeforeCompile = sh => {
    snap(sh); Object.assign(sh.uniforms, U);
    sh.vertexShader = 'uniform mat4 uQ;\nvarying vec3 vB;\n' + (claws ? 'attribute vec2 aNail;\nvarying vec2 vNail;\n' : '') + sh.vertexShader.replace('#include <beginnormal_vertex>', '#include <beginnormal_vertex>' + (shroud ? SHROUD_VN : '')).replace('#include <begin_vertex>', '#include <begin_vertex>\n  vB = (uQ * vec4(position, 1.)).xyz;' + (claws ? '\n  vNail = aNail;' : '')
      + (infl ? `\n  transformed += normalize(normal) * ${infl.toExponential(3)} * smoothstep(${(C.hemY + .16).toFixed(3)}, ${(C.hemY - .02).toFixed(3)}, vB.y);` : '')
      + (shroud ? SHROUD_VS : ''));
    sh.fragmentShader = `uniform vec3 uTint, uGoreP, uEyeL3, uEyeR3, uRipP, uRipR; uniform float uGain, uGrey, uWet, uGore, uSocket, uH, uMouthZ, uRip, uRipK;
varying vec3 vB;
float hh3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
${CINE || shroud ? HUM_NOISE : ''}${shroud ? SHROUD_H + (CINE ? SKIN_MH : '') : ''}${claws ? 'varying vec2 vNail;\n' : ''}` + sh.fragmentShader.replace('#include <map_fragment>', `#include <map_fragment>
  ${hem != null ? `if (vB.y > ${hem.toFixed(3)}) discard;   // под подолом кофты` : ''}
  ${re ? `diffuseColor.rgb = mix(vec3(${LUMA}) * uTint * uGain, uTint, ${(re[2] || 0).toFixed(2)});` : ''}
  ${kind === 'skin' ? `{ float s = smoothstep(.04, .012, length(vB - uEyeL3)) + smoothstep(.04, .012, length(vB - uEyeR3)); diffuseColor.rgb *= 1. - .9 * uSocket * clamp(s, 0., 1.); }` : ''}
  ${shroud ? SHROUD_FS : ''}
  ${kind === 'hair' && CINE ? `diffuseColor.rgb *= .86 + .28 * vn3(vec3(vB.x * 260., vB.y * 9., vB.z * 260.));   // пряди: соседние полоски (вдоль — сверху вниз) чуть разной яркости — волосы не сплошная шапка; без клеток (на макушке были квадраты)
  ${hre ? 'gHairS = (vn3(vec3(vB.x * 300., vB.y * 7., vB.z * 300.) + 17.) - .5) * .14 + (vn3(vB * 40.) - .5) * .06;   // блик вдоль прядей у каждой пряди чуть выше или ниже' : ''}` : ''}
  ${kind === 'eyes' && CINE && o.deadEyes == null ? `{ vec3 q = vB - (vB.x > 0. ? uEyeL3 : uEyeR3);   // белок не белее бумаги (чуть тёплый); верхнее веко и уголки затеняют глаз — глаз сидит в глазнице, а не наклейка
    float scl = smoothstep(.45, .7, min(diffuseColor.r, min(diffuseColor.g, diffuseColor.b)));
    diffuseColor.rgb = mix(diffuseColor.rgb, diffuseColor.rgb * vec3(.82, .77, .74), scl);
    diffuseColor.rgb *= 1. - clamp(smoothstep(-.002, .009, q.y) * .55 + smoothstep(.005, .011, abs(q.x)) * .3, 0., .7); }` : ''}
  ${kind === 'teeth' || kind === 'tongue' ? `float mouthD; { float dz = clamp((uMouthZ - vB.z) / .026, 0., 1.), pink = clamp((diffuseColor.r - diffuseColor.g) * 2.5, 0., 1.);   // рот внутри в тени: чем глубже, тем темнее; дёсны и язык — приглушённые
    ${kind === 'teeth' ? `diffuseColor.rgb = mix(diffuseColor.rgb, vec3(.84, .81, .76), .5 * (1. - pink));   // зубы ровнее по цвету: тёмные бороздки текстуры издали делали ряд похожим на решётку` : ''}
    mouthD = ${kind === 'tongue' ? 'max(dz, .5)' : 'dz'};
    diffuseColor.rgb *= mix(1., .16, dz) * (1. - .55 * pink) * ${kind === 'tongue' ? '.5' : '1.'}; }` : ''}
  ${(() => { for (const k in (o.dim || {})) if (kind.includes(k)) return `diffuseColor.rgb *= ${o.dim[k].toFixed(2)};`; return ''; })()}
  float shK = 1.;   // своя тень на лице: карта теней грубая — нос и скулы дают на щеке рваный клин; на голове тень ослабляем
  ${kind === 'skin' ? `shK = 1. - .8 * smoothstep(.17, .1, length(vB - ((uEyeL3 + uEyeR3) * .5 + vec3(0., .02, -.08))));` : ''}
  diffuseColor.rgb = mix(diffuseColor.rgb, vec3(${LUMA}) * vec3(.8, .85, .9), uGrey);
  diffuseColor.rgb *= 1. - .22 * uWet;
  ${claws ? `diffuseColor.rgb *= 1. - .6 * smoothstep(.3, 1., vNail.y);   // кончики пальцев почернели
  diffuseColor.rgb = mix(diffuseColor.rgb, vec3(.06, .05, .035), smoothstep(.15, .65, vNail.x));   // когти: толстые, жёлто-чёрные` : ''}
  ${claws && CINE ? `{ float mo = vn3(vB * 5. + 3.); diffuseColor.rgb *= mix(vec3(1.), vec3(.84, .74, .88), smoothstep(.52, .8, mo) * .75) * (.88 + .24 * vn3(vB * 17.)); }   // трупные пятна — сиреневые, тон неровный` : ''}
  ${shroud ? 'diffuseColor.rgb = mix(diffuseColor.rgb, gClothC, gCloth);   // ткань — после серости кожи: грязно-белая, не в тон мёртвой коже' : ''}
  float gore = 0.;
  if (uGore > 0.) {
    vec3 d = (vB - uGoreP) / uH; float nz = hh3(floor(vB * 90.));
    float blob = smoothstep(.16, .05, length(d * vec3(1., .6, 1.5)) + (nz - .5) * .05);
    float drip = step(.7, hh3(vec3(floor(vB.x * 50.), 1., floor(vB.z * 50.)))) * step(d.y, 0.) * smoothstep(-.45, -.1, d.y) * smoothstep(.2, .1, length(d.xz));
    gore = uGore * max(blob, drip);
    diffuseColor.rgb = mix(diffuseColor.rgb, vec3(.2, .012, .012), clamp(gore * 1.4, 0., .95)); }
  float rip = 0.;
  if (uRip > 0.) {   // рана как у студий (RE, TLOU): рваная кожа → жёлтый жир → мышца по краю; внутри — своё по виду раны; кровь впитывается в ткань и стекает
    vec3 q = (vB - uRipP) / uRipR; float front = smoothstep(-1.1, -.15, q.z); q.y /= .3 + .7 * uRip;   // разрыв — щель, раскрывается с силой раны (не круг)
    float nz = ${CINE ? 'vn3(vB * 70.)' : 'hh3(floor(vB * 70.))'}, nz2 = ${CINE ? 'vn3(vB * 23. + 7.)' : 'hh3(floor(vB * 23.))'};
    float e = length(q.xy) + (nz2 - .5) * .5 + (nz - .5) * .16, R0 = .5 + .12 * uRip;   // рваный, неровный край (лоскуты)
    float hole = (1. - smoothstep(R0 - .04, R0 + .02, e)) * front * step(.02, uRip);
    float rw = .1 + .14 * nz2, rim = clamp((1. - smoothstep(R0 + .02, R0 + rw, e)) * front * step(.02, uRip) - hole, 0., 1.);   // край разной ширины
    float soak = (1. - smoothstep(R0 + .1, R0 + .9 + max(0., -q.y) * 1.9 + nz * .3, e)) * front * min(1., uRip * 1.6);
    diffuseColor.rgb = mix(diffuseColor.rgb, vec3(.19, .01, .012) * (.7 + .6 * nz), clamp(soak, 0., .93));
    float dl = clamp((e - R0) / rw, 0., 1.);
    vec3 lay = dl < .5 ? vec3(.3, .02, .03) : dl < .7 ? vec3(.46, .36, .17) : vec3(.42, .25, .22);   // мышца шире, жир — тонкая полоска, рваная кожа
    diffuseColor.rgb = mix(diffuseColor.rgb, lay * (.72 + .5 * nz), rim);
    vec2 u = q.xy / max(R0, .06); float lu = length(u), dp = clamp(1. - lu, 0., 1.);
    vec3 inn;
    if (uRipK < .5) { float c = sin((u.x * 3.1 + sin(u.y * 4.3 + nz2 * 3.) * .9) * 6.3) * .5 + .5; inn = mix(vec3(.16, .015, .02), vec3(.5, .2, .18), pow(max(c, 0.), 1.7)); }   // кишки: петли с бликом
    else if (uRipK < 1.5) { float rb = smoothstep(.55, .82, sin(u.y * 9.5) * .5 + .5); inn = mix(vec3(.22, .02, .03), vec3(.66, .58, .48), rb); }   // рёбра и лёгкое
    else if (uRipK < 2.5) { float tr = smoothstep(.5, .86, sin(u.y * 15.) * .5 + .5) * smoothstep(.62, .22, abs(u.x)); inn = mix(vec3(.18, .01, .02), vec3(.58, .44, .4), tr); }   // трахея
    else if (uRipK < 3.5) { float th = step(-.4, u.y) * step(u.y, -.08) * smoothstep(.42, .5, fract(u.x * 5.5)); inn = mix(vec3(.3, .02, .03), vec3(.74, .7, .6), th); }   // зубы и кость
    else if (uRipK < 4.5) { inn = vec3(.07, .0, .0); }   // глазницы
    else { float fb = sin(u.x * 30. + nz * 4.) * .5 + .5, bn = smoothstep(.3, .14, abs(u.x)) * smoothstep(.0, .3, dp); inn = mix(mix(vec3(.3, .02, .03), vec3(.42, .06, .06), fb), vec3(.7, .64, .54), bn); }   // мышца и кость
    inn *= mix(.32, 1., smoothstep(0., .4, dp)) * (1. - .55 * smoothstep(.72, 1., lu));   // провал: к середине темнее, под кромкой тень
    diffuseColor.rgb = mix(diffuseColor.rgb, inn, hole);
    rip = max(hole, max(rim, soak * .8)); }`)
      .replace('#include <roughnessmap_fragment>', '#include <roughnessmap_fragment>\n' + (hre && kind === 'skin' ? SKIN_ROUGH : '') + (shroud ? '  roughnessFactor = mix(roughnessFactor, mix(.92, .45, gWet), gCloth);   // саван: ткань матовая, мокрый подол блестит\n' : '') + (claws ? '  roughnessFactor = mix(roughnessFactor, .55, smoothstep(.15, .65, vNail.x));   // когти — роговые, чуть блестят (глаже .55 в тумане коридора светились белым)\n' : '') + '  roughnessFactor = mix(roughnessFactor, ' + (kind === 'hair' ? '.25' : '.42') + ', max(uWet * .75, gore));\n  roughnessFactor = mix(roughnessFactor, .32, rip);   // рана и кровь мокрые (глаже — блики горели неоном под светом места)')
      .replace('#include <normal_fragment_maps>', '#include <normal_fragment_maps>' + (hre && kind === 'skin' ? SKIN_PORES : '') + (shroud ? SHROUD_N : ''))
      .replace('#include <lights_physical_pars_fragment>', '#include <lights_physical_pars_fragment>' + (hre ? '\n' + hre + '\n#undef RE_Direct\n#define RE_Direct RE_Direct_Hum\n' : ''))
      .replace('#include <lights_physical_fragment>', '#include <lights_physical_fragment>' + (hre && kind === 'skin' ? '\n  material.specularColor *= .7;   // F0 кожи ≈ .028, не .04 пластика' : ''))
      .replace('#include <lights_fragment_begin>', T.ShaderChunk.lights_fragment_begin.split('? getPointShadow(').join('? (1. - shK) + shK * getPointShadow(').split('? getShadow(').join('? (1. - shK) + shK * getShadow('))
      // глубже во рту свет не бликует: нёбо в «Кино» блестело серебром (или рыжим от тёплого света) — там выход не ярче самого цвета
      .replace('#include <opaque_fragment>', '#include <opaque_fragment>' + (kind === 'teeth' || kind === 'tongue' ? '\n  gl_FragColor.rgb = mix(gl_FragColor.rgb, min(gl_FragColor.rgb, diffuseColor.rgb * .8), smoothstep(.08, .6, mouthD));' : '')
        + (kind === 'skin' && CINE ? '\n  { float lm = dot(gl_FragColor.rgb, vec3(.3, .59, .11)); gl_FragColor.rgb += vec3(.04, .01, .0025) * (1. - smoothstep(.03, .32, lm)) * diffuseColor.r * (1. - uGrey); }' : ''));   // кожа: в тенях чуть тёплого (кровь под кожей), не серый пластик; на свету тёплый край даёт RE_Direct_Hum
  };
  // ключ программы: код шейдера у зубов, языка и «приглушённой» одежды свой — иначе three.js отдаёт им чужую готовую программу с тем же набором текстур
  const dimK = (() => { for (const k in (o.dim || {})) if (kind.includes(k)) return 'd' + o.dim[k]; return ''; })();
  m.customProgramCacheKey = () => 'hum3' + (shroud ? 'S' : '') + (claws ? 'k' : '') + (o.monster ? 'M' : '') + (hre ? 'R' : '') + (kind === 'eyes' ? 'e' + (o.deadEyes == null ? 1 : 0) : '') + (cloth ? 'C' : '') + (hem != null ? 'p' + hem.toFixed(3) : '') + (infl ? 'i' + C.hemY.toFixed(3) : '') + (kind === 'skin' ? 's' : '') + (kind === 'hair' ? 'h' : '') + (kind === 'teeth' ? 't' : kind === 'tongue' ? 'g' : '') + dimK + (re ? 'r' + (re[2] || 0) : '') + (CINE ? 'c' : '');
  return m;
}

// виземы речи: открытые (гласные) и закрытые/узкие (согласные)
const VOPEN = ['viseme_aa', 'viseme_O', 'viseme_E', 'viseme_aa', 'viseme_I', 'viseme_U', 'viseme_E'];
const VSHUT = ['viseme_PP', 'viseme_FF', 'viseme_SS', 'viseme_DD', 'viseme_nn', 'viseme_kk', 'viseme_TH', 'viseme_CH', 'viseme_RR'];
// насколько открыт рот на каждой виземе (челюсть): гласные — шире, «п/б/м» — губы сомкнуты
const JAW = { viseme_aa: .26, viseme_O: .2, viseme_E: .16, viseme_I: .1, viseme_U: .1, viseme_PP: 0, viseme_FF: .03, viseme_TH: .06, viseme_DD: .08, viseme_kk: .11, viseme_CH: .07, viseme_SS: .04, viseme_nn: .06, viseme_RR: .09 };
// насколько звук виден на губах: п/б/м, ф/в, гласные — целиком; т/д/н/к/г делает язык — губы почти не меняются (иначе рот «стрекочет» на каждой букве)
const VW = { viseme_SS: .65, viseme_TH: .55, viseme_DD: .5, viseme_kk: .45, viseme_nn: .45, viseme_RR: .6, viseme_I: .85, viseme_E: .9 };
const CLIPS3 = {};   // клипы с высотой таза под рост модели
function clipsFor(k) {
  const key = k.toFixed(3); if (CLIPS3[key]) return CLIPS3[key];
  const out = {};
  for (const nm in CLIP) {
    const c = CLIP[nm]; if (!c.tracks.some(t => t.name === 'pelvis.position')) { out[nm] = c; continue; }
    const cc = c.clone(); for (const t of cc.tracks) if (t.name === 'pelvis.position') for (let i = 0; i < t.values.length; i++) t.values[i] *= k;
    out[nm] = cc;
  }
  return (CLIPS3[key] = out);
}
const _qa = new T.Quaternion(), _ma = new T.Matrix4(), _va = new T.Vector3();
function makeHuman(o) {
  const sc = window.THREE_SkeletonUtils.clone(G[o.model].scene);
  const root = new T.Group(); root.name = o.name;
  const inner = new T.Group(); inner.add(sc); root.add(inner);
  const C = { root, inner, o, bone: {}, pose: 'stand', look: null, talk: 0, blinkT: 1 + rnd() * 3, t0: rnd() * 10, faceSet: 'base', faceSets: { base: 1 }, rest: {}, v3: true, M: [] };
  root.updateMatrixWorld(true);
  const meshes = [], bones = [];
  sc.traverse(n => { if (n.isSkinnedMesh) meshes.push(n); if (n.isBone) { if (n.name === 'head') n.name = 'Head'; if (n.name === 'Root') n.name = 'root'; bones.push(n); } });
  // матрицы кожи в покое (кость × обратная привязки) — после «переодевания» скелета сетка должна остаться ровно такой же
  const rest = meshes.map(n => n.skeleton.bones.map((b, i) => b.matrixWorld.clone().multiply(n.skeleton.boneInverses[i])));
  // кости MakeHuman получают повороты покоя костей Quaternius — и клипы Quaternius встают как есть (оси пальцев, кулак, стук — тоже)
  // суставы остаются на месте: при повороте кости её дети иначе уехали бы вместе с её осями (пальцы гнулись бы вокруг чужой точки и рвали кожу)
  const wp = new Map(bones.map(b => [b, b.getWorldPosition(new T.Vector3())]));
  for (const b of bones) {
    const w = QW[b.name];
    if (w) { b.parent.getWorldQuaternion(_qa).invert(); b.quaternion.copy(_qa.multiply(w)); b.updateMatrixWorld(true); }
    for (const c of b.children) if (wp.has(c)) { c.position.copy(b.worldToLocal(wp.get(c).clone())); c.updateMatrixWorld(true); }
  }
  meshes.forEach((n, j) => { n.skeleton.boneInverses = n.skeleton.bones.map((b, i) => b.matrixWorld.clone().invert().multiply(rest[j][i])); });
  for (const b of bones) { C.bone[b.name] = b; C.rest[b.name] = b.quaternion.clone(); }
  // мерки в позе покоя (метры): рост, глаза, рот
  C.HU = { uGrey: { value: o.grey || 0 }, uWet: { value: o.wet || 0 }, uGore: { value: 0 }, uSocket: { value: o.socket || 0 },
    uGoreP: { value: new T.Vector3() }, uEyeL3: { value: new T.Vector3() }, uEyeR3: { value: new T.Vector3() }, uH: { value: 1 }, uMouthZ: { value: 9 },
    uRip: { value: 0 }, uRipK: { value: 0 }, uRipP: { value: new T.Vector3(0, 1.08, .17) }, uRipR: { value: new T.Vector3(.11, .075, .12) } };   // рваная рана (смерти 18+): сила, центр и полуоси в позе покоя (м)
  const toM = n => rest[meshes.indexOf(n)][0].clone().multiply(n.bindMatrix);   // сжатые координаты → метры
  const part = {};
  // подол кофты/рубашки (низ в позе покоя, метры): брюки выше него всё равно под ней — их там не рисуем, иначе на шаге бедро протыкает подол тёмным клином
  { const top = meshes.find(n => /sweater|shirt|hoodie/.test(n.name)), bot = meshes.find(n => /pants|jeans|trousers/.test(n.name));
    if (top && bot) { const g = top.geometry; if (!g.boundingBox) g.computeBoundingBox(); C.hemY = g.boundingBox.clone().applyMatrix4(toM(top)).min.y + .035; } }
  for (const n of meshes) {
    n.frustumCulled = false;
    const kind = n.name; part[kind.startsWith('cloth') ? kind : kind] = n;
    const Q = toM(n);
    n.material = humanMat(C, n, kind, Q);
    if (n.morphTargetInfluences) { n.morphTargetInfluences.fill(0); C.M.push(n); }
    n.userData.Q = Q;
  }
  C.mesh = part.skin; C.brows = part.brows; C.lashes = part.lashes; C.hairM = part.hair; C.eyeM = part.eyes && part.eyes.material;   // eyeM: глаза ловят ИК-подсветку камеры (кадр задаёт свечение)
  const bbox = n => n.geometry.boundingBox || (n.geometry.computeBoundingBox(), n.geometry.boundingBox);
  const sb = bbox(C.mesh).clone().applyMatrix4(C.mesh.userData.Q); C.H = sb.max.y;
  if (part.eyes) {   // центры глаз (для тёмных глазниц) — среднее вершин слева и справа
    const pa = part.eyes.geometry.attributes.position, Qe = part.eyes.userData.Q, L = new T.Vector3(), Rr = new T.Vector3(); let nl = 0, nr = 0;
    for (let i = 0; i < pa.count; i++) { _va.fromBufferAttribute(pa, i).applyMatrix4(Qe); if (_va.x > 0) { L.add(_va); nl++; } else { Rr.add(_va); nr++; } }
    C.HU.uEyeL3.value.copy(L.divideScalar(nl || 1)); C.HU.uEyeR3.value.copy(Rr.divideScalar(nr || 1));
    C.eyeZ = (C.HU.uEyeL3.value.z + C.HU.uEyeR3.value.z) / 2; C.eyeY = (C.HU.uEyeL3.value.y + C.HU.uEyeR3.value.y) / 2;
  }
  if (part.teeth) { const tb = bbox(part.teeth).clone().applyMatrix4(part.teeth.userData.Q);   // рот: перед верхних зубов
    const mw = new T.Vector3((tb.min.x + tb.max.x) / 2, tb.min.y + (tb.max.y - tb.min.y) * .55, tb.max.z + .006);
    C.mouthW = mw.clone(); C.mouthL = C.bone.Head.worldToLocal(mw.clone()); C.HU.uMouthZ.value = tb.max.z; }
  C.HU.uH.value = C.H / 1.8;
  // кровь: у жертвы — горло и грудь; у Жильца — рот, подбородок, грудь
  const neck = C.bone.neck_01.getWorldPosition(new T.Vector3());
  C.HU.uGoreP.value.copy(o.monster && C.mouthW ? C.mouthW.clone().add(new T.Vector3(0, -.08, -.02)) : neck.add(new T.Vector3(0, -.09, .04)));
  // рост: модель уже в настоящий размер; Жилец — вытягиваем до o.h
  const sc0 = o.h ? o.h / C.H : 1; inner.scale.setScalar(sc0);
  C.ss = (o.h || C.H) / (o.fem ? 1.77 : 1.81);
  C.clips = clipsFor(C.bone.pelvis.position.z / QW.pelvisZ);
  if (o.arm) for (const b of ['upperarm_l', 'upperarm_r']) C.bone[b].scale.setScalar(o.arm);
  if (o.armL) for (const b of ['lowerarm_l', 'lowerarm_r']) C.bone[b].scale.setScalar(o.armL);
  if (o.handS) for (const b of ['hand_l', 'hand_r']) C.bone[b].scale.setScalar(o.handS);
  if (o.neck) { C.bone.neck_01.scale.setScalar(o.neck); C.bone.Head.scale.setScalar((o.headS || 1) / o.neck); }
  if (o.monster) { if (C.brows) C.brows.visible = false; if (C.lashes) C.lashes.visible = false; }
  C.U = Object.assign({ uTalk: { value: 0 }, uBlink: { value: 0 }, uSmile: { value: 0 }, uScream: { value: 0 } }, C.HU);
  C.browU = { value: new T.Vector2() };
  addProps(C);
  C.mixer = new T.AnimationMixer(sc); C.act = null; C.clip = '';
  C.addFace = (nm, ov) => { C.faceSets[nm] = ov; };
  C.update = (t, dt) => updateSkinned(C, t, dt);
  fitProps(C);
  if (o.monster) clawHands(C.mesh);
  if (o.gown) skinGown(C);
  return C;
}

// Жилец: пальцы тоньше, на концах — когти. Вершины кожи сдвигаются в позе покоя (до скелета), кости не трогаем: клипы и касания работают как прежде.
// Руки модели tenant в позе «Т» лежат вдоль оси x ладонью вниз — ноготь сверху (+y) последней фаланги, коготь растёт вдоль пальца и загибается вниз.
// aNail: x — коготь (шейдер красит в жёлто-чёрный, блестит), y — почерневший кончик пальца. Сетка общая у всех копий модели — правим один раз.
function clawHands(n) {
  const g = n && n.geometry; if (!g || g.userData.claws) return; g.userData.claws = 1;
  const pa = g.attributes.position, si = g.attributes.skinIndex, sw = g.attributes.skinWeight, Q = n.userData.Q, Qi = Q.clone().invert();
  const names = n.skeleton.bones.map(b => b.name), FG = /^(index|middle|ring|pinky|thumb)_0([123])_([lr])$/;
  const N = pa.count, P = new Float32Array(N * 3), who = new Array(N), F = {}, nail = new Float32Array(N * 2);
  for (let i = 0; i < N; i++) {
    let bi = 0, bw = -1; for (let k = 0; k < 4; k++) { const w = sw.getComponent(i, k); if (w > bw) { bw = w; bi = si.getComponent(i, k); } }
    _va.fromBufferAttribute(pa, i).applyMatrix4(Q); P[i * 3] = _va.x; P[i * 3 + 1] = _va.y; P[i * 3 + 2] = _va.z;
    const m = FG.exec(names[bi]); if (!m) continue;
    const f = F[m[1] + m[3]] || (F[m[1] + m[3]] = { thumb: m[1] === 'thumb', c: [0, 1, 2].map(() => [new T.Vector3(), 0]) });
    who[i] = [f, +m[2]]; f.c[m[2] - 1][0].add(_va); f.c[m[2] - 1][1]++;
  }
  const up = new T.Vector3(0, 1, 0), v = new T.Vector3(), foot = new T.Vector3(), r = new T.Vector3();
  for (const k in F) { const f = F[k]; if (f.c.some(c => !c[1])) { f.bad = 1; continue; }
    f.c = f.c.map(c => c[0].divideScalar(c[1])); f.d = f.c[2].clone().sub(f.c[0]).normalize();
    f.u = up.clone().addScaledVector(f.d, -up.dot(f.d)).normalize(); f.s0 = 1e9; f.s1 = -1e9; }
  for (let i = 0; i < N; i++) { const w = who[i]; if (!w || w[0].bad) continue; const f = w[0], s = v.fromArray(P, i * 3).sub(f.c[0]).dot(f.d);
    if (w[1] === 1) f.s0 = Math.min(f.s0, s); if (w[1] === 3) f.s1 = Math.max(f.s1, s); }
  for (let i = 0; i < N; i++) {
    const w = who[i]; if (!w || w[0].bad) continue; const f = w[0];
    v.fromArray(P, i * 3); const s = v.clone().sub(f.c[0]).dot(f.d); foot.copy(f.c[0]).addScaledVector(f.d, s); r.copy(v).sub(foot);
    const along = clamp((s - f.s0) / (f.s1 - f.s0), 0, 1), th = (f.thumb ? .14 : .24) * T.MathUtils.smoothstep(along, 0, .3);   // тоньше; у ладони — плавно, без ступеньки
    v.copy(foot).addScaledVector(r, 1 - th);
    const ph = r.dot(f.u) * (1 - th), tipK = w[1] === 3 ? T.MathUtils.smoothstep(s, f.s1 - .02, f.s1 - .004) : 0;
    let nx = 0;
    if (!f.thumb && tipK > 0) {   // коготь: верх кончика тянется вдоль пальца на 1,8 см и загибается вниз
      const top = T.MathUtils.smoothstep(ph, -.002, .006), k2 = T.MathUtils.smoothstep(s, f.s1 - .012, f.s1) * top;
      v.addScaledVector(f.d, .018 * k2).addScaledVector(f.u, -ph * .6 * k2 * k2);
      nx = tipK * T.MathUtils.smoothstep(ph, -.001, .004);
    } else if (f.thumb) nx = tipK * .55;
    nail[i * 2] = nx; nail[i * 2 + 1] = T.MathUtils.smoothstep(along, .45, 1);
    v.applyMatrix4(Qi); pa.setXYZ(i, v.x, v.y, v.z);
  }
  pa.needsUpdate = true;
  g.setAttribute('aNail', new T.BufferAttribute(nail, 2));
}

// рубаха-саван Жильца — своя свободная сетка со скелетом (как полы плаща у v2), от плеч ниже колен.
// Строится один раз в позе покоя по самому телу: на каждой высоте — эллипс вокруг туловища (руки в позе «Т» отрезаны), ниже бёдер —
// вокруг обеих ног сразу и не уже бёдер (подол, а не штанины), книзу свободнее, по низу складки. Кости: грудь → поясница → таз → бёдра
// (подол идёт за бедром своей стороны: шаг, присед, бросок). Ткань — gownMat: грязно-серо-белая, складки рельефом, пятна, рваный мокрый подол
function skinGown(C) {
  const sk = C.mesh, par = sk.parent; C.root.updateWorldMatrix(true, true);
  const P = n => C.bone[n].getWorldPosition(new T.Vector3()), base = C.root.getWorldPosition(new T.Vector3());
  const neck = P('neck_01'), pel = P('pelvis'), thL = P('thigh_l'), cfL = P('calf_l'), arm = P('upperarm_l'), sp3 = P('spine_03'), sp1 = P('spine_01');
  const side = Math.sign(thL.x - pel.x) || 1, yTop = neck.y - .05 * C.ss, yHip = pel.y, yBot = cfL.y - .2 * C.ss, armCut = Math.abs(arm.x - base.x) * .92;
  const pts = [], pa = sk.geometry.attributes.position, v = new T.Vector3(), step = Math.max(1, Math.floor(pa.count / 6000));   // точки тела в позе покоя (мир)
  for (let i = 0; i < pa.count; i += step) { sk.getVertexPosition(i, v); v.applyMatrix4(sk.matrixWorld); pts.push([v.x, v.y, v.z]); }
  const NR = 16, NA = 32, rows = [], ySh = arm.y;   // верхнее кольцо — вокруг шеи, второе — на высоте плечевых суставов: между ними плечи (руки выходят по бокам)
  const band = (y, dy, cut) => { let x0 = 9, x1 = -9, z0 = 9, z1 = -9;
    for (const [px, py, pz] of pts) { if (Math.abs(py - y) > dy || Math.abs(px - base.x) > cut) continue; x0 = Math.min(x0, px); x1 = Math.max(x1, px); z0 = Math.min(z0, pz); z1 = Math.max(z1, pz); }
    if (x1 < x0) { x0 = base.x - .15; x1 = base.x + .15; z0 = base.z - .1; z1 = base.z + .1; }
    return { cx: (x0 + x1) / 2, cz: (z0 + z1) / 2, rx: (x1 - x0) / 2, rz: (z1 - z0) / 2 }; };
  rows.push(Object.assign({ y: neck.y + .015 * C.ss, f: 0, neckRow: 1 }, band(neck.y + .015 * C.ss, .03 * C.ss, .1 * C.ss)));
  for (let j = 1; j <= NR; j++) { const f = (j - 1) / (NR - 1), y = ySh + (yBot - ySh) * f;
    rows.push(Object.assign({ y, f }, band(y, Math.abs(yBot - ySh) / NR * .8, y > yHip + .1 * C.ss ? armCut : 9))); }
  let hip = null;   // ниже бёдер не уже бёдер; книзу свободнее
  for (const r of rows) { if (r.y <= yHip && !hip) hip = { rx: r.rx, rz: r.rz, cx: r.cx, cz: r.cz }; if (hip && r.y < yHip) { r.rx = Math.max(r.rx, hip.rx * .98); r.rz = Math.max(r.rz, hip.rz * 1.05); r.cx = hip.cx; r.cz = r.cz * .5 + hip.cz * .5; }
    if (r.neckRow) { r.rx += .022 * C.ss; r.rz += .022 * C.ss; continue; }   // ворот — у самой шеи
    const loose = .05 + .3 * Math.pow(r.f, 1.4); r.rx = r.rx * (1 + loose) + .02 * C.ss; r.rz = r.rz * (1 + loose * 1.2) + .03 * C.ss; }
  const pos = [], aB = [], aF = [], aS = [], si = [], sw = [], idx = [];
  const bones = ['spine_03', 'spine_02', 'spine_01', 'pelvis', 'thigh_l', 'thigh_r'].filter(n => C.bone[n]), bi = n => bones.indexOf(n), yS3 = sp3.y, yS1 = sp1.y;
  for (const r of rows) for (let i = 0; i <= NA; i++) {
    const a = i / NA * Math.PI * 2, sa = Math.sin(a), ca = Math.cos(a), wave = 1 + .05 * r.f * Math.sin(a * 9 + .6) + .022 * r.f * Math.sin(a * 23);
    const ell = 1 / Math.sqrt(Math.pow(sa / r.rx, 2) + Math.pow(ca / r.rz, 2)), w = new T.Vector3(r.cx + sa * ell * wave, r.y, r.cz + ca * ell * wave);
    aB.push(w.x - base.x, w.y - base.y, w.z - base.z); aF.push(r.f); aS.push(sa, ca);
    par.worldToLocal(w); pos.push(w.x, w.y, w.z);
    let W;   // веса: грудь → поясница → таз; под тазом подол переходит на бедро своей стороны
    if (r.y >= yS3) W = { spine_03: 1 };
    else if (r.y >= yS1) { const k = (yS3 - r.y) / Math.max(.01, yS3 - yS1); W = { spine_03: 1 - k, spine_01: k }; }
    else if (r.y >= yHip) { const k = (yS1 - r.y) / Math.max(.01, yS1 - yHip); W = { spine_01: 1 - k, pelvis: k }; }
    else { const g = Math.min(1, Math.pow((yHip - r.y) / Math.max(.01, yHip - yBot), 1.15)) * .82, l = T.MathUtils.smoothstep(sa * side, -.55, .55); W = { pelvis: 1 - g, thigh_l: g * l, thigh_r: g * (1 - l) }; }
    const ks = Object.keys(W).filter(n => bi(n) >= 0); while (ks.length < 4) ks.push(null);
    for (const n of ks) { si.push(n ? bi(n) : 0); sw.push(n ? W[n] : 0); }
  }
  for (let j = 0; j < NR; j++) for (let i = 0; i < NA; i++) { const a = j * (NA + 1) + i, b = a + NA + 1; idx.push(a, b, a + 1, b, b + 1, a + 1); }
  const geo = new T.BufferGeometry(); geo.setAttribute('position', new T.Float32BufferAttribute(pos, 3)); geo.setAttribute('aB', new T.Float32BufferAttribute(aB, 3));
  geo.setAttribute('aF', new T.Float32BufferAttribute(aF, 1)); geo.setAttribute('aS', new T.Float32BufferAttribute(aS, 2)); geo.setIndex(idx);
  geo.setAttribute('skinIndex', new T.Uint16BufferAttribute(si, 4)); geo.setAttribute('skinWeight', new T.Float32BufferAttribute(sw, 4)); geo.computeVertexNormals();
  const m = new T.SkinnedMesh(geo, gownMat(C)); m.name = 'gown'; m.frustumCulled = false; par.add(m); m.updateWorldMatrix(true, false);
  m.bind(new T.Skeleton(bones.map(n => C.bone[n]))); C.gown = m;
}
function gownMat(C) {
  const m = litMat({ color: 0xffffff, side: T.DoubleSide }, .92);
  if (!CINE) m.extensions = Object.assign({}, m.extensions, { derivatives: true });
  m.onBeforeCompile = sh => {
    Object.assign(sh.uniforms, { uWet: C.HU.uWet, uGore: C.HU.uGore });
    sh.vertexShader = 'attribute vec3 aB; attribute float aF; attribute vec2 aS; varying vec3 vG; varying float vF; varying vec2 vS;\n' + sh.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\n  vG = aB; vF = aF; vS = aS;');
    sh.fragmentShader = `uniform float uWet, uGore; varying vec3 vG; varying float vF; varying vec2 vS;
float hh3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
${HUM_NOISE}float gWet = 0., gA = 0., gN = 0.;
float gH(vec2 sv, vec3 g, float f, float aF, float aN) { return sin(atan(sv.x, sv.y + 1e-5) * 11. + vn3(g * 7.) * 4.) * (.004 + .009 * f) * aF + vn3(vec3(g.x * 50., g.y * 5., g.z * 50.)) * .003 * aN; }
` + sh.fragmentShader.replace('#include <map_fragment>', `#include <map_fragment>
  gA = atan(vS.x, vS.y + 1e-5); gN = vn3(vG * 7.);   // +1e-5: atan(0, 0) на части видеокарт — NaN
  if (vF > .93 - .08 * vn3(vec3(vG.x * 22., 0., vG.z * 22.)) - .04 * hh3(vec3(floor(gA * 9.), 1., 2.))) discard;   // рваный подол
  if (vF > .62 && vn3(vG * 13.) > .8) discard;   // дыры у подола
  float fold = sin(gA * 11. + gN * 4.) * (.1 + .16 * vF), dirt = smoothstep(.55, .78, vn3(vG * 8.5)) * .55 + vF * vF * .5; gWet = smoothstep(.62, .92, vF);
  vec3 cloth = vec3(.44, .42, .37) * (1. + fold) * (1. - .4 * dirt) * mix(vec3(1.), vec3(.76, .63, .46), dirt * .8);
  cloth *= 1. - .55 * gWet; cloth = mix(cloth, vec3(.05, .045, .04), .45 * gWet * smoothstep(.4, .7, vn3(vG * 30.)));
  if (uGore > 0.) cloth = mix(cloth, vec3(.2, .012, .012), uGore * smoothstep(.62, .78, vn3(vG * 4. + 3.)) * (1. - vF));   // кровь на груди, когда ест
  diffuseColor.rgb = cloth * (1. - .15 * uWet);`)
      .replace('#include <normal_fragment_maps>', `#include <normal_fragment_maps>
  { float aaF = 1. - smoothstep(.15, .6, fwidth(gA * 11.)), aaN = 1. - smoothstep(.02, .06, length(fwidth(vG)) * 50.);   // вдали мелкий рельеф гаснет — без муара
    float h0 = gH(vS, vG, vF, aaF, aaN);   // складки рельефом: разность высот в соседних пикселях (dFdx высоты один на квадрат 2×2 — «лесенка» вблизи)
    vec2 dh = vec2(gH(vS + dFdx(vS), vG + dFdx(vG), vF + dFdx(vF), aaF, aaN) - h0, gH(vS + dFdy(vS), vG + dFdy(vG), vF + dFdy(vF), aaF, aaN) - h0);
    vec3 sx = dFdx(-vViewPosition), sy = dFdy(-vViewPosition), r1 = cross(sy, normal), r2 = cross(normal, sx); float det = dot(sx, r1);
    vec3 nn = abs(det) * normal - sign(det) * (dh.x * r1 + dh.y * r2); normal = dot(nn, nn) > 1e-24 ? normalize(nn) : normal; }`)
      .replace('#include <roughnessmap_fragment>', '#include <roughnessmap_fragment>\n  roughnessFactor = mix(.93, .5, gWet);');
  };
  m.customProgramCacheKey = () => 'gown2' + (CINE ? 'c' : '');
  return m;
}

// лицо v3 каждый кадр: моргание, взгляд, брови, речь, улыбка, страх, крик → веса лицевых форм на всех частях (кожа, глаза, ресницы, брови, зубы)
function faceV3(C, tt, dt) {
  const o = C.o, U = C.U, fs = C.fsNow || C.faceSets[C.faceSet] || {}, W = C.W || (C.W = {});
  for (const k in W) W[k] = 0;
  const add = (k, v) => { W[k] = (W[k] || 0) + v; };
  const live = !o.monster && !o.noBlink && !o.dead;
  // выражение лица меняется посреди кадра (реакция на реплику) — перетекает за ~0,3 с, а не щёлкает; на склейке — сразу
  const E = C.ex || (C.ex = {}), ke = C.cutNow || o.monster ? 1 : 1 - Math.exp(-dt * 7), ev = (k, v) => (E[k] = E[k] == null ? v : E[k] + (v - E[k]) * ke);
  // моргание: веко закрывается за 70 мс, открывается за 120 мс. Говорит — чаще (раз в 2–4 с), слушает — реже, моргает в паузах говорящего;
  // в страхе смотрит не моргая; перевёл взгляд далеко, начал или кончил фразу — часто моргает (C.blinkReq); каждое пятое — неполное
  if (live) {
    C.bT = (C.bT ?? 1 + rnd() * 3) - dt;
    if ((C.bT < 0 || C.blinkReq) && C.bK == null) { C.bK = 0; C.bAmp = rnd() < .2 ? .6 + rnd() * .25 : 1; }
    C.blinkReq = 0;
    if (C.bK != null) { C.bK += dt; if (C.bK > .19) { C.bK = null; C.bT = rnd() < .12 ? .22 : fs.wide || fs.press ? 4 + rnd() * 5 : C.talkingNow ? 1.8 + rnd() * 2.4 : C.hearingNow ? 2.8 + rnd() * 3.7 : 2.2 + rnd() * 3.8; } }
    const b = (C.bK == null ? 0 : C.bK < .07 ? C.bK / .07 : Math.max(0, 1 - (C.bK - .07) / .12)) * (C.bAmp || 1);
    add('eyeBlinkLeft', b); add('eyeBlinkRight', b);
  }
  if (o.dead) { add('eyeBlinkLeft', .7); add('eyeBlinkRight', .62); add('jawOpen', .18); }
  // взгляд: голова почти довернулась к цели — глаза доводят остаток. Живой взгляд держится на цели,
  // изредка — едва заметный скачок (1–2°); в тревоге — чаще и шире, но без беготни по сторонам
  if (!o.dead && C.lookDir && C.headFwd) {
    C.bone.Head.getWorldQuaternion(_qa).invert(); _va.copy(C.eyeDir || C.lookDir).applyQuaternion(_qa);   // глаза — на цель (впереди головы), а не туда, куда успела повернуться голова
    const f = _va.dot(C.headFwd), left = _va.dot(C.headLeft), up = _va.dot(C.headUp);
    let yaw = Math.atan2(left, f), pitch = Math.atan2(up, f);
    if (live) {   // микросаккады: слушает — переводит взгляд с глаза на глаз и изредка на рот; говорит — чуть шире; в тревоге — бегает
      const dart = fs.dart ? 1 : 0;
      C.sT = (C.sT ?? .5) - dt;
      if (C.sT < 0) {
        if (dart) { C.sT = .45 + rnd() * .9; C.sY = (rnd() - .5) * .16; C.sP = (rnd() - .5) * .07; }
        else if (C.hearingNow) { const r = rnd(); C.sT = .35 + rnd() * .8; C.sY = r < .42 ? .02 : r < .84 ? -.02 : 0; C.sP = r < .84 ? .004 : -.04; }
        else if (C.talkingNow) { C.sT = .6 + rnd() * 1.6; C.sY = (rnd() - .5) * .07; C.sP = (rnd() - .5) * .035; }
        else { C.sT = 1 + rnd() * 2.4; C.sY = (rnd() - .5) * .05; C.sP = (rnd() - .5) * .025; }
      }
      const k = 1 - Math.exp(-dt * 45); C.cY = lerp(C.cY || 0, C.sY || 0, k); C.cP = lerp(C.cP || 0, C.sP || 0, k);
      yaw += C.cY; pitch += C.cP;
    }
    const Y = clamp(yaw / .6, -.85, .85), P = clamp(pitch / .45, -.8, .8);
    if (Y > 0) { add('eyeLookOutLeft', Y); add('eyeLookInRight', Y); } else { add('eyeLookInLeft', -Y); add('eyeLookOutRight', -Y); }
    if (P > 0) { add('eyeLookUpLeft', P); add('eyeLookUpRight', P); } else { add('eyeLookDownLeft', -P); add('eyeLookDownRight', -P); }
  }
  // брови: x — подняты, y — домиком (+, тревога, мольба) или сведены (−)
  const B = C.browU.value;
  add('browInnerUp', clamp(B.x * .55 + Math.max(0, B.y) * .95, 0, 1));
  add('browOuterUpLeft', clamp(B.x * .7, 0, 1)); add('browOuterUpRight', clamp(B.x * .7, 0, 1));
  add('browDownLeft', clamp(-B.y * .9, 0, 1)); add('browDownRight', clamp(-B.y * .9, 0, 1));
  // речь: громкость голоса открывает челюсть, виземы меняются слогами (гласная — открытая, согласная — узкая/сомкнутая)
  const m = U.uTalk.value;
  if (C.vis) {   // губы по буквам реплики: движок знает, какое слово звучит и какая в нём буква сейчас
    const v = C.vis === 'sil' ? null : C.vis;
    if (v !== C.vCur) { C.vPrev = C.vCur; C.vCur = v; C.vK = 0; }
    C.vK = Math.min(1, (C.vK || 0) + dt / .06);   // соседние звуки перетекают (коартикуляция), а не щёлкают каждую букву
    const g = clamp(.4 + m * .9, 0, .85);   // даже шёпот — губы работают; громче — шире
    if (C.vPrev) add(C.vPrev, (1 - C.vK) * g * (VW[C.vPrev] ?? 1));
    if (C.vCur) add(C.vCur, C.vK * g * (VW[C.vCur] ?? 1));
    add('jawOpen', lerp(JAW[C.vPrev] || 0, JAW[C.vCur] || 0, C.vK) * (.6 + .5 * Math.min(1, m * 1.5)));
  } else if (m > .02) {   // нет текста (крик, плач, текст без озвучки) — по громкости
    C.vT = (C.vT ?? 0) - dt;
    if (C.vT <= 0) { C.vT = .065 + rnd() * .085; C.vPrev = C.vCur; C.vCur = m > .3 ? VOPEN[rnd() * VOPEN.length | 0] : VSHUT[rnd() * VSHUT.length | 0]; C.vK = 0; }
    C.vK = Math.min(1, (C.vK || 0) + dt / .055);
    const g = Math.min(1, m * 1.5);
    if (C.vPrev) add(C.vPrev, (1 - C.vK) * g);
    if (C.vCur) add(C.vCur, C.vK * g);
    add('jawOpen', m * .4);
  } else { C.vPrev = C.vCur = null; }
  // улыбка; у «Лили» за стеклом — шире, чем бывает у людей
  // fs.dead — «мёртвая» улыбка: растёт ровно настолько, насколько задано (может начаться с нуля), а глаза в ней не участвуют — не щурятся, а распахнуты
  const dead = ev('dead', fs.dead || 0), sm = ev('sm', fs.dead ? +fs.smile || 0 : Math.max(o.smile || 0, fs.smile ? +fs.smile : 0)), sq = fs.dead ? 0 : 1;
  if (sm) { add('mouthSmileLeft', Math.min(1, sm)); add('mouthSmileRight', Math.min(1, sm)); add('cheekSquintLeft', .35 * sm * sq); add('cheekSquintRight', .35 * sm * sq);
    if (sm > 1) {   // слишком широкая: губы растянуты до предела и разжаты — видны оба ряда зубов, щёки подпирают глаза
      const e = sm - 1, st = dead ? 2.9 : 2.4; add('mouthStretchLeft', e * st); add('mouthStretchRight', e * st); add('mouthUpperUpLeft', e * 1.8); add('mouthUpperUpRight', e * 1.8);
      add('mouthLowerDownLeft', e * 1.5); add('mouthLowerDownRight', e * 1.5); add('jawOpen', e * .35); add('cheekSquintLeft', e * sq); add('cheekSquintRight', e * sq); } }
  if (dead) { add('eyeWideLeft', .95 * dead); add('eyeWideRight', .95 * dead); add('browInnerUp', .22 * dead); }
  { const w = ev('wide', fs.wide ? (fs.wide === true ? .7 : +fs.wide) : 0); if (w > .001) { add('eyeWideLeft', w); add('eyeWideRight', w); } }
  { const v = ev('tense', fs.tense || 0); if (v > .001) { add('mouthStretchLeft', v); add('mouthStretchRight', v); } }   // губы оттянуты от страха
  { const v = ev('mouth', fs.mouth || 0); if (v > .001) add('jawOpen', v * .55); }
  { const v = ev('frown', fs.frown || 0); if (v > .001) { add('mouthFrownLeft', v); add('mouthFrownRight', v); } }
  { const v = ev('squint', fs.squint || 0); if (v > .001) { add('eyeSquintLeft', v); add('eyeSquintRight', v); } }
  { const v = ev('press', fs.press && !(m > .02) ? fs.press : 0); if (v > .001) { add('mouthPressLeft', v); add('mouthPressRight', v); add('viseme_PP', .35 * v); } }   // затаил дыхание: губы сжаты
  // договорил фразу — на миг сжал губы (рот закрывается не «отпусканием» челюсти, а живым смыканием)
  if (live) { if (m > .05) { C.qT = 0; C.spk = 1; } else { C.qT = (C.qT || 0) + dt;
    if (C.spk && C.qT > .12 && C.qT < .45) { const v = .28 * Math.sin(Math.PI * (C.qT - .12) / .33); add('mouthPressLeft', v); add('mouthPressRight', v); } if (C.qT >= .45) C.spk = 0; } }
  // крик / бросок: челюсть до упора (у Жильца — дальше, чем может человек), губы растянуты, нос сморщен
  const scr = U.uScream.value;
  if (scr) {
    add('jawOpen', scr * (o.monster ? 1.75 : 1)); add('mouthStretchLeft', scr * .8); add('mouthStretchRight', scr * .8);
    add('eyeWideLeft', scr); add('eyeWideRight', scr); add('browInnerUp', scr * .8);
    if (o.monster) { add('noseSneerLeft', scr * .7); add('noseSneerRight', scr * .7); add('mouthUpperUpLeft', scr * .8); add('mouthUpperUpRight', scr * .8); }
  }
  if (o.monster && !scr) { add('mouthStretchLeft', .25); add('mouthStretchRight', .25); add('eyeWideLeft', .5); add('eyeWideRight', .5); }
  // язык лежит на дне рта: опускается вместе с челюстью и губами (своих форм звуков почти не берёт — иначе торчал вперёд и заполнял рот)
  const tOpen = Math.min(1, (W.jawOpen || 0) + .6 * Math.max(W.viseme_aa || 0, .85 * (W.viseme_O || 0), .7 * (W.viseme_E || 0), .5 * (W.viseme_U || 0), .45 * (W.viseme_I || 0)));
  for (const n of C.M) { const d = n.morphTargetDictionary, inf = n.morphTargetInfluences;
    if (/tongue/i.test(n.name)) { for (const k in d) inf[d[k]] = k === 'jawOpen' ? tOpen : k === 'viseme_TH' || k === 'viseme_DD' ? .3 * (W[k] || 0) : 0; continue; }
    for (const k in d) inf[d[k]] = W[k] || 0; }
}

/* ---------- актёры «Ночной смены» ---------- */
// v3: те же роли (ключи прежние — на них ссылаются кадры scenes.js), лица и тела — люди MakeHuman
function castV3() {
  const A = {}, H = makeHuman;
  const POLO = c => ({ polo: [c, 2.1], pants: [0x1b1d22, 1.7] });
  A.temych = H({ name: 'temych', model: 'ethan', tint: { hair: [0x6b4a30, 2.4], brows: [0x4a3220, 2] } });                                                         // Итан: своя стёганая куртка (цвет — при сборке модели)
  A.smenshik = H({ name: 'smenshik', model: 'dayguard', cap: 0x15203a });                                  // дневной охранник: тёмно-синяя куртка, кепка
  A.mihalych = H({ name: 'mihalych', model: 'frank', sheet: ['НЕ ЗОВИ МЕНЯ.', 'СТУЧИ ТРИ РАЗА'], tint: Object.assign(POLO(0x2b3036), { hair: [0xb8b6b0, 1.6, .55], brows: [0xa8a6a0, 1.6, .5] }) });   // Фрэнк
  const lily = { model: 'lily', fem: true, wet: 1, tint: { sweater: [0xf0c020, 3.4, .3], hair: [0x3a2618, 1.7] } };   // жёлтый свитер — видно и ночью под дождём
  A.katya = H(Object.assign({ name: 'katya', noBlink: true, lipLag: .12 }, lily));                       // «Лили» за стеклом
  A.katyaSmile = H(Object.assign({ name: 'katyaSmile', noBlink: true, lipLag: .22, grey: .45, smile: 1.5, eyeC: 0x707070, socket: .55 }, lily));
  // «не её лицо»: то же тело в жёлтой куртке, но глаза чёрные, кожа серая, улыбка шире человеческой, голова дёргается
  A.katyaThing = H(Object.assign({ name: 'katyaThing', noBlink: true, grey: .85, smile: 2.1, deadEyes: 0x000000, socket: 1, tilt: .22, jerk: true }, lily));
  // Оно: выше двери, истощённое, серая сухая кожа (не блестит), чёрные глаза, длинные пальцы и шея; мокрые чёрные волосы
  A.monster = H({ name: 'monster', model: 'tenant', monster: true, shroud: true, gown: true, h: 2.4, arm: 1.1, armL: 1.08, handS: 1.25, neck: 1.18, headS: .92, grey: 1, wet: .25,
    deadEyes: 0x000000, socket: 1, tilt: .45, jerk: true, headMax: 2.3, stopmo: 1 / 7, tint: { skin: 0xa9b0a6, hair: [0x0a0a0a, 1, .6] } });
  A.monster.addFace('grin', { smile: 1.15, mouth: .5 });
  A.monster.addFace('scream', { scream: 1 });
  // двойник Итана (смерть «Ты назвался»): та же куртка, но кожа серая, глаза чёрные, не моргает; голова поворачивается дальше человеческого
  A.ethanThing = H({ name: 'ethanThing', model: 'ethan', noBlink: true, grey: .55, deadEyes: 0x000000, socket: .8, tilt: .12, jerk: true, headMax: 2.8, tint: { hair: [0x6b4a30, 2.4], brows: [0x4a3220, 2] } });
  A.ethanThing.addFace('grin', { smile: 2.0, mouth: .3 });
  const gusHair = { hair: [0x9c9893, 1.5, .5], brows: [0x8a8680, 1.5, .45] };   // Гас — старик (тридцать лет здесь): седой
  A.cleaner = H({ name: 'cleaner', model: 'gus', mop: true, dim: { worksuit: .74 }, tint: gusHair });   // белая футболка под комбинезоном не светится под лампами                                             // Гас
  // Марта, жена Гаса: только на старом фото (Новый год на седьмом). Красное платье: свитер и юбка в тон, тёмные колготки, седые волосы
  A.martha = H({ name: 'martha', model: 'lily', fem: true, dress: 0x9a1c1c, tint: { sweater: [0x9a1c1c, 2.4, .35], pants: [0x2a2224, 1.2, .2], hair: [0x8c8680, 1.5, .45] } });
  A.victim = H({ name: 'victim', model: 'gus', dead: true, grey: .55, dim: { worksuit: .74 }, tint: gusHair });
  A.smenshik.addFace('smile', { smile: .8, browUp: .3 });
  A.smenshik.addFace('fear', { mouth: .45, wide: true, browUp: 1, browIn: .8, dart: 1 });
  A.temych.addFace('smile', { smile: 1.4, wide: true, browUp: .5 });
  for (const k of ['mihalych', 'cleaner', 'martha']) A[k].addFace('photo', { smile: .85, browUp: .25 });   // улыбаются в объектив (новогоднее фото)
  for (const k of ['temych', 'smenshik', 'katya', 'mihalych', 'cleaner']) {
    const F = A[k].faceSets;
    F.worry = F.worry || { browIn: .8, browUp: .1, dart: 1 };
    F.plead = F.plead || { browIn: 1, browUp: .35, mouth: .12 };
    F.alert = F.alert || { browUp: .9, wide: true, dart: 1 };
    F.fear = F.fear || { mouth: .4, wide: true, browUp: 1, browIn: .8, dart: 1 };
    F.hush = F.hush || { press: 1, wide: 1, browUp: 1, browIn: 1, frown: .5, tense: .5, dart: 1 };   // не дышит от ужаса: глаза распахнуты, губы сжаты и оттянуты, уголки вниз (одно «сжаты» читалось как полуулыбка)
    F.gasp = F.gasp || { mouth: .2, wide: true, browUp: 1, browIn: .9, dart: 1 };   // ахнул: рот чуть приоткрыт (зубы почти не видны)
    F.terror = F.terror || { press: 1, wide: 1, browUp: 1, browIn: 1, frown: .45, tense: .35, dart: 1 };   // оцепенел от ужаса: глаза распахнуты, губы сжаты, уголки вниз
    F.calm = F.calm || { smile: .3, browUp: .1 };
    F.sad = F.sad || { browIn: .75, browUp: .1, frown: .35, squint: .12 };   // горе: брови домиком, уголки губ вниз, веки чуть прикрыты
    F.grim = F.grim || { browIn: -.45, frown: .25, squint: .22 };             // мрачно, сосредоточенно: брови сведены, взгляд тяжёлый
    F.warm = F.warm || { smile: .45, browUp: .2, squint: .1 };                // тепло, по-доброму (не оскал): улыбка с прищуром
    F.dread = F.dread || { mouth: .1, wide: .55, browUp: .8, browIn: 1, tense: .3, dart: 1 };   // понял, что натворил: глаза распахнуты, рот чуть приоткрыт, губы оттянуты (не крик)
  }
  for (const k in A) A[k].update(0, 1 / 30);
  return A;
}
function cast() {
  if (V3) return castV3();
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
  A.katyaThing = makeSkinned(Object.assign({ name: 'katyaThing', noBlink: true }, kat, { skin: 0xd8d4d0, skinK: 1.0, grey: .85, smile: true, deadEyes: 0x000000, socket: 1, tilt: .22, jerk: true }));
  A.ethanThing = makeSkinned(Object.assign({ name: 'ethanThing', h: 1.8, skin: 0xd6ccc8, skinK: 1.0, hairPart: 'Hair_SimpleParted', hair: 0x3a2a1c, brow: 0x2a1c12, noBlink: true, grey: .55, deadEyes: 0x000000, socket: .8, headMax: 2.8 }, guard));
  A.ethanThing.addFace('grin', { smile: true, mouth: .3 });
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
  A.martha = makeSkinned(Object.assign({ name: 'martha' }, kat, { coat: 0x9a1c1c, trim: 0x6a1010, hair: 0x8c8680, wet: false }));
  for (const k of ['mihalych', 'cleaner', 'martha']) A[k].addFace('photo', { smile: true, browUp: .25 });
  for (const k in A) A[k].update(0, 1 / 30);
  return A;
}

Object.assign(window.__W3chars, { load, makeSkinned, propAt, propAim, propsFrame, HANDP, prop3, handPt, reachArm, hasClip: n => !!CLIP[n] && !window.__NOMOCAP });
})();
