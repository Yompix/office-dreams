/* Персонажи v2: настоящие 3D-люди со скелетом и анимациями (модели Quaternius, лицензия CC0, папка models/).
   Одежду рисуем шейдером прямо по телу (рубашка, брюки, плащ, ботинки по зонам тела), лицо живое: рот открывается по голосу,
   глаза моргают, у Жильца пустые глаза и улыбка до ушей. Анимации: стоять, говорить, сидеть, идти, пятиться, зомби-стойка;
   руки для «стучит», «отдаёт ключи», «держит листок», «тянется» доворачиваются кодом поверх анимации.
   Снаружи тот же интерфейс, что у старых персонажей (chars.js): { root, pose, look, talk, faceSet, update(t, dt) }.
   Если модели не загрузились, остаются старые персонажи из блоков. */
(() => {
'use strict';
const { T, rnd, lerp, clamp, mat, box, SNAP } = window.__W3core;
const FILES = ['male', 'female', 'Hair_Beard', 'Hair_Long', 'Hair_SimpleParted', 'Hair_Buzzed', 'Eyebrows_Regular', 'Eyebrows_Female'];
const G = {}, CLIP = {};

// XHR, а не fetch: так модели читаются и в Android-приложении (file://)
const get = url => new Promise((ok, no) => { const x = new XMLHttpRequest(); x.open('GET', url); x.responseType = 'arraybuffer'; x.onload = () => (x.status === 200 || x.status === 0) && x.response ? ok(x.response) : no(); x.onerror = no; x.send(); });
async function load() {
  if (!window.THREE_GLTFLoader || !window.THREE_SkeletonUtils) return false;
  const L = new window.THREE_GLTFLoader();
  try {
    await Promise.all(FILES.map(n => get('models/' + n + '.glb?v=1').then(b => new Promise((ok, no) => L.parse(b, '', g => { G[n] = g; ok(); }, no)))));
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
uniform vec3 uShirt, uPants, uShoes, uSkin, uCoat, uTrim; uniform float uFlat, uS, uCoatY, uTalk, uBlink, uSmile, uNaked, uBadge, uFem, uWet, uGrey;
uniform vec2 uMouth, uEyeL, uEyeR;
varying vec3 vB;
float h3(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
// одежда по точке тела: 0 — кожа, иначе цвет ткани (w = 1)
vec4 cloth(vec3 p, float lum) {
  float ax = abs(p.x);
  if (uNaked > .5) return vec4(0.);
  float fold = mix(1., clamp(lum / .42, .55, 1.45), uFlat > .5 ? .18 : .4);
  float grain = .94 + .12 * h3(floor(vB * 260.));
  bool hand = ax > .665, foot = p.y < .12;
  bool neck = p.y > 1.6 || (p.y > 1.49 && ax < min((p.y - 1.49) * .9, .04) && p.z > .03) || (p.y > 1.565 && ax < .07);
  if (hand || neck) return vec4(0.);
  if (foot) return vec4(uShoes * (.75 + .4 * lum), 1.);
  if (p.y < .985 && ax < .3) {
    vec3 c = uPants * fold * grain;
    if (p.y > .955 && uCoatY == 0.) c = uTrim * .6;
    if (uCoatY > 0. && p.y > uCoatY) c = uCoat * fold * grain;
    return vec4(c, 1.);
  }
  vec3 c = (uCoatY > 0. ? uCoat : uShirt) * fold * grain;
  if (uCoatY == 0. && p.z > .05 && ax < .006 && p.y < 1.43) c *= .55;                                      // планка
  if (uBadge > .5 && p.z > .05 && p.x > .07 && p.x < .115 && p.y > 1.3 && p.y < 1.35) c = vec3(.8, .64, .25);  // жетон
  if (uBadge > .5 && p.x < -.24 && p.x > -.33 && p.y > 1.37 && p.z > -.02) c = uTrim;                        // шеврон
  if (p.y > 1.5 && ax < .12) c = mix(c, uTrim, .55);                                                         // воротник
  if (uCoatY > 0. && ax > .6) c *= .8;                                                                       // манжеты
  if (uCoatY > 0. && p.z > .03 && ax < .007) c *= .6;                                                       // застёжка плаща
  return vec4(c, 1.);
}
`;
const BODY_PAINT = `
#include <map_fragment>
{
  vec3 p = vB / uS;
  float lum = dot(diffuseColor.rgb, vec3(.3, .59, .11));
  vec3 skin = mix(diffuseColor.rgb, vec3(lum * 1.15), max(uGrey, .3)) * uSkin;
  if (uNaked > .5) skin *= mix(1., .3, step(lum, .12));              // тёмное бельё текстуры у Жильца = лохмотья
  vec4 cl = cloth(p, lum);
  vec3 col = mix(skin, cl.rgb, cl.w);
  // лицо: рот и глаза по развёртке текстуры
  vec2 uv = vMapUv;
  vec2 md = (uv - (uMouth + vec2(0., .001))) / vec2(.016 + .004 * uTalk + .045 * uSmile, .0015 + .0075 * uTalk + .005 * uSmile);
  md.y += uSmile * 1.7 * md.x * md.x;                                 // уголки вверх
  float mr = length(md);
  if (mr < 1. && (uTalk > .08 || uSmile > .5)) { col = vec3(.09, .03, .03) * (1.3 - mr); if (uSmile < .5 && md.y < -.45 && uTalk > .3) col = vec3(.55, .5, .45); }
  if (uSmile > .5 && mr < 1. && md.y < -.2 && md.y > -.65) col = vec3(.62, .58, .5) * (.75 + .25 * step(.5, fract(md.x * 7.)));   // зубы
  if (uBlink > .5) {
    vec2 e1 = (uv - uEyeL) / vec2(.02, .011), e2 = (uv - uEyeR) / vec2(.02, .011);
    if (min(length(e1), length(e2)) < 1.) col = texture2D(map, uv + vec2(0., -.028)).rgb * uSkin * (min(abs(e1.y), abs(e2.y)) < .18 ? .45 : 1.);
  }
  col *= mix(1., .82, uWet * step(1.5, p.y));
  diffuseColor.rgb = col;
}`;
// оболочка одежды: та же сетка, чуть раздутая по нормали там, где есть ткань, — у одежды появляется объём, у плаща полы
const SHELL_VS = `
  vec3 pb = position / uS; float axb = abs(pb.x);
  float th = 0.;
  bool nk = pb.y > 1.6 || (pb.y > 1.49 && axb < min((pb.y - 1.49) * .9, .04) && pb.z > .03) || (pb.y > 1.565 && axb < .07);
  if (uNaked < .5 && axb < .665 && !nk) {
    th = pb.y < .12 ? .022 : pb.y < .985 ? .011 : axb > .2 ? .016 : .024;
    if (uCoatY > 0. && pb.y > .12) th = pb.y < .985 ? (pb.y > uCoatY ? .026 + (.985 - pb.y) * .05 : .012) : axb > .2 ? .02 : .026;
  }
  transformed += objectNormal * th * uS;`;
function uniforms(o) {
  const sk = new T.Color(o.skin ?? 0xffffff); if (o.skinK) sk.multiplyScalar(o.skinK);
  return {
    uShirt: { value: new T.Color(o.shirt ?? 0x222222) }, uPants: { value: new T.Color(o.pants ?? 0x1a1a1a) }, uShoes: { value: new T.Color(o.shoes ?? 0x111111) },
    uSkin: { value: sk }, uCoat: { value: new T.Color(o.coat ?? 0x000000) }, uTrim: { value: new T.Color(o.trim ?? 0x111111) },
    uS: { value: o.fem ? 1.77 / 1.81 : 1 }, uCoatY: { value: o.coatY || 0 }, uTalk: { value: 0 }, uBlink: { value: 0 }, uSmile: { value: o.smile ? 1 : 0 },
    uNaked: { value: o.naked ? 1 : 0 }, uBadge: { value: o.badge ? 1 : 0 }, uFem: { value: o.fem ? 1 : 0 }, uWet: { value: o.wet ? 1 : 0 }, uGrey: { value: o.grey || 0 }, uFlat: { value: o.coatY && o.fem ? 1 : 0 }, uThin: { value: o.thin || 1 },
    uMouth: { value: new T.Vector2(.1855, .2637) }, uEyeL: { value: o.fem ? new T.Vector2(.130, .179) : new T.Vector2(.1367, .1758) }, uEyeR: { value: o.fem ? new T.Vector2(.238, .179) : new T.Vector2(.2344, .1758) },
  };
}
// под плащом фигура проще: грудь и бёдра прижимаем (в позе «Т», до скелета)
const FLAT = `
  if (uThin < 1.) { vec3 q = position / uS; transformed.z *= uThin; if (q.y > .95 && abs(q.x) < .24) transformed.x *= mix(uThin, 1., smoothstep(.12, .24, abs(q.x))); }
  if (uFlat > .5) { vec3 q = position / uS;
    if (q.y > 1.12 && q.y < 1.48 && q.z > .03) transformed.z = (.03 + (q.z - .03) * .25) * uS;
    if (q.y > .78 && q.y < 1.08 && q.z < -.07) transformed.z = (-.07 + (q.z + .07) * .35) * uS; }`;
const SNAPV = '#include <project_vertex>\n  gl_Position.xy = floor(gl_Position.xy / gl_Position.w * uSnap + .5) / uSnap * gl_Position.w;';
function bodyMat(src, U, shell) {
  const m = new T.MeshLambertMaterial({ map: src.map });
  m.onBeforeCompile = sh => {
    Object.assign(sh.uniforms, U); sh.uniforms.uSnap = SNAP;
    sh.vertexShader = 'uniform vec2 uSnap; uniform float uS, uCoatY, uNaked, uFlat, uThin;\nvarying vec3 vB;\n' + sh.vertexShader
      .replace('#include <begin_vertex>', '#include <begin_vertex>\n vB = position;' + FLAT + (shell ? SHELL_VS : ''))
      .replace('#include <beginnormal_vertex>', '#include <beginnormal_vertex>\n if (uFlat > .5) { vec3 q = position / uS; if (q.y > 1.12 && q.y < 1.48 && q.z > .02) objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .5, objectNormal.y * .3, 1.), .75)); if (q.y > .76 && q.y < 1.1 && q.z < -.05) objectNormal = normalize(mix(objectNormal, vec3(objectNormal.x * .5, 0., -1.), .7)); }')
      .replace('#include <project_vertex>', SNAPV);
    sh.fragmentShader = BODY_FS + sh.fragmentShader.replace('#include <map_fragment>', shell
      ? '#include <map_fragment>\n{ vec4 cl = cloth(vB / uS, dot(diffuseColor.rgb, vec3(.3, .59, .11))); if (cl.w < .5) discard; diffuseColor.rgb = cl.rgb; }'
      : BODY_PAINT);
  };
  m.customProgramCacheKey = () => shell ? 'shell2' : 'body2';
  return m;
}
function ps1(m) { window.__W3core.ps1(m); return m; }

/* ---------- сборка персонажа ---------- */
function attach(C, part, color) {   // волосы/борода из отдельного файла садятся на скелет персонажа по именам костей
  let sm = null; G[part].scene.traverse(n => { if (n.isSkinnedMesh) sm = n; });
  if (!sm) return null;
  const bones = sm.skeleton.bones.map(b => C.bone[b.name]);
  const m = new T.SkinnedMesh(sm.geometry, ps1(new T.MeshLambertMaterial({ map: sm.material.map, color, alphaTest: .45, side: T.DoubleSide })));
  m.frustumCulled = false;
  C.mesh.parent.add(m); m.bind(new T.Skeleton(bones, sm.skeleton.boneInverses), C.mesh.bindMatrix);
  return m;
}
function makeSkinned(o) {
  const src = G[o.fem ? 'female' : 'male'];
  const sc = window.THREE_SkeletonUtils.clone(src.scene);
  const root = new T.Group(); root.name = o.name;
  const inner = new T.Group(); inner.add(sc); root.add(inner);
  const C = { root, inner, o, bone: {}, pose: 'stand', look: null, talk: 0, blinkT: 1 + rnd() * 3, t0: rnd() * 10, faceSet: 'base', faceSets: { base: 1 } };
  sc.traverse(n => {
    if (n.isBone) C.bone[n.name] = n;
    if (n.isSkinnedMesh) {
      n.frustumCulled = false;
      const nm = n.name.toLowerCase();
      if (nm.includes('eyebrow')) { n.material = ps1(new T.MeshLambertMaterial({ map: n.material.map, color: o.brow ?? o.hair ?? 0x1a120c, alphaTest: .45 })); C.brows = n; }
      else if (nm.includes('eye')) { n.material = o.voidEyes ? ps1(new T.MeshBasicMaterial({ color: 0x000000 })) : ps1(new T.MeshLambertMaterial({ map: n.material.map, color: o.eyeC ?? 0xffffff })); C.eyes = n; }
      else { C.U = uniforms(o); const src = n.material; n.material = bodyMat(src, C.U, false); C.mesh = n;
        if (!o.naked) { const sh = new T.SkinnedMesh(n.geometry, bodyMat(src, C.U, true)); sh.frustumCulled = false; C.shellOf = [n, sh]; } }
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
  // кепка и очки — простые детали на кости головы
  const head = C.bone.Head;
  if (o.cap) { const cp = new T.Group(); head.add(cp); box(.21, .07, .23, mat(o.cap), 0, 0, 0, cp); box(.2, .012, .09, mat(o.cap), 0, -.03, .14, cp); box(.035, .03, .008, mat(0xc9a43a), 0, .0, .117, cp); C.capG = cp; }
  if (o.glasses) { const gl = new T.Group(); head.add(gl); const gm = mat(0x151515); for (const sx of [-1, 1]) { const x = sx * .033; box(.046, .004, .004, gm, x, .013, 0, gl); box(.046, .004, .004, gm, x, -.013, 0, gl); box(.004, .03, .004, gm, x - .022, 0, 0, gl); box(.004, .03, .004, gm, x + .022, 0, 0, gl); box(.004, .004, .07, gm, sx * .058, .01, -.035, gl); } box(.022, .004, .004, gm, 0, .008, 0, gl); C.glassG = gl; }
  // полы плаща и капюшон: жёсткие детали на тазе и голове (под плащом не видно форм тела)
  if (o.coatY) {
    const cm = ps1(new T.MeshLambertMaterial({ color: o.coat, side: T.DoubleSide }));
    const sk = new T.Mesh(new T.CylinderGeometry(.19, .26, .5, 10, 1, true), cm); sk.scale.z = .78; C.skirt = sk;
    const gap = 1.7, hd = new T.Mesh(new T.SphereGeometry(.135, 10, 7, Math.PI / 2 + gap / 2, Math.PI * 2 - gap, 0, Math.PI * .68), cm); hd.scale.set(1.08, 1.2, 1.1); C.hood = hd;
  }
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
  if (C.skirt) put(C.skirt, 'pelvis', 0, (.985 - .25) * ss * (C.o.fem ? .978 : 1), -.01 * ss);
  if (C.hood) put(C.hood, 'Head', hw.x, hw.y + .085 * ss, hw.z - .01 * ss);
  if (C.veilG) put(C.veilG, 'Head', hw.x, hw.y + .175 * ss, hw.z + .1 * ss);
  if (C.glassG) { C.glassG.position.copy(toLocal(hw.x, hw.y + .095 * s, hw.z + .105 * s)); C.glassG.quaternion.copy(inv).multiply(C.root.getWorldQuaternion(new T.Quaternion())); }
  C.headFwd = new T.Vector3(0, 0, 1).applyQuaternion(inv);
  const hand = C.bone.hand_r, hq = new T.Quaternion(); hand.getWorldQuaternion(hq);
  C.prop.quaternion.copy(hq.invert()).multiply(C.root.getWorldQuaternion(new T.Quaternion()));
  C.prop.position.set(0, 0, 0);
}

/* ---------- позы ---------- */
const POSE = {
  stand: 'Idle_Loop', talk: 'Idle_Talking_Loop', sit: 'Sitting_Idle_Loop', sittalk: 'Sitting_Talking_Loop', sitfloor: 'Fixing_Kneeling',
  knock: 'Idle_Loop', give: 'Idle_Loop', paper: 'Idle_Loop', monster: 'Idle_Loop', reach: 'Idle_Loop',
  walk: 'Walk_Loop', back: 'Walk_Loop', hug: 'Idle_Loop',
};
const _a = new T.Vector3(), _b = new T.Vector3(), _c = new T.Vector3(), _q = new T.Quaternion(), _q2 = new T.Quaternion(), _pq = new T.Quaternion();
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
function updateSkinned(C, t, dt) {
  const tt = t + C.t0, P = C.pose, U = C.U;
  const talking = C.talk > .06;
  let want = POSE[P] || 'Idle_Loop';
  if (talking && P === 'stand') want = POSE.talk;
  if (talking && P === 'sit') want = POSE.sittalk;
  if (C.o.monster && P === 'walk') want = 'Zombie_Walk_Fwd_Loop';
  if (want !== C.clip) {
    const a = C.mixer.clipAction(CLIP[want] || CLIP.Idle_Loop);
    a.reset(); a.timeScale = P === 'back' ? -.8 : P === 'monster' ? .3 : P === 'reach' ? .55 : C.o.monster ? .8 : 1;
    if (C.act && dt < .2) { a.play(); C.act.crossFadeTo(a, .35, false); } else { C.mixer.stopAllAction(); a.play(); }
    if (!C.act) a.time = (C.t0 * 1.7) % (a.getClip().duration || 1);
    C.act = a; C.clip = want;
  }
  C.mixer.update(Math.min(dt, .1));
  // руки поверх анимации
  if (P === 'knock') {
    const kn = Math.max(0, Math.sin(tt * 9)) ** 4;
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.3, -.75, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.15, .55 - kn * .35, .8 + kn * .3], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.35, -.8, .8], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.2, .45, .8], 1);
  } else if (P === 'give') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.1, -.6, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [0, -.1, 1], 1);
  } else if (P === 'paper') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.1, -.85, .45], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.45, .35, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.1, -.85, .45], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.45, .35, 1], 1);
  } else if (P === 'reach') {
    const sw = Math.sin(tt * 1.3) * .08;
    aimBone(C, 'upperarm_r', 'lowerarm_r', [-.15, .05 + sw, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [-.05, .1, 1], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [.15, .05 - sw, 1], 1); aimBone(C, 'lowerarm_l', 'hand_l', [.05, .1, 1], 1);
  } else if (P === 'hug') {
    aimBone(C, 'upperarm_r', 'lowerarm_r', [.2, -.1, 1], 1); aimBone(C, 'lowerarm_r', 'hand_r', [.9, 0, .4], 1);
    aimBone(C, 'upperarm_l', 'lowerarm_l', [-.2, -.1, 1], 1); aimBone(C, 'lowerarm_l', 'hand_l', [-.9, 0, .4], 1);
  }
  // голова смотрит на цель (обычно в камеру): доворачиваем «нос» головы к цели поверх анимации, поровну шее и голове
  const k = Math.min(1, dt * 7), hb = C.bone.Head;
  hb.updateWorldMatrix(true, false); hb.getWorldPosition(_a);
  C.root.getWorldQuaternion(_q2);
  if (C.look) _b.copy(C.look).sub(_a).normalize();
  else _b.set(Math.sin(tt * .4) * .06, -.05, 1).normalize().applyQuaternion(_q2);
  if (C.o.jerk && Math.sin(tt * 1.3) > .985) _b.x += (rnd() - .5) * .8;
  // не дальше, чем позволяет шея: ограничиваем угол от направления тела
  _c.set(0, 0, 1).applyQuaternion(_q2);
  const ang = _b.angleTo(_c); if (ang > 1.15) { _q.setFromUnitVectors(_c, _b); _q.slerp(_pq.identity(), 1 - 1.15 / ang); _b.copy(_c).applyQuaternion(_q); }
  C.lookDir = C.lookDir ? C.lookDir.lerp(_b, k).normalize() : _b.clone();
  for (const [bn, f] of [['neck_01', .45], ['Head', 1]]) {
    const b = C.bone[bn]; b.updateWorldMatrix(true, false);
    const bw = b.getWorldQuaternion(new T.Quaternion());
    const cur = C.headFwd.clone().applyQuaternion(hb.getWorldQuaternion(new T.Quaternion()));
    _q.setFromUnitVectors(cur, C.lookDir); _q.slerp(_pq.identity(), 1 - f);
    if (C.o.tilt && bn === 'Head') { const r = new T.Quaternion().setFromAxisAngle(C.lookDir, (Math.sin(tt * .3) * .05 + C.o.tilt) * (C.o.flip ? -1 : 1)); _q.premultiply(r); }
    bw.premultiply(_q); b.parent.getWorldQuaternion(_pq); b.quaternion.copy(_pq.invert().multiply(bw)); _pq.identity();
    b.updateWorldMatrix(false, true);
  }
  if (C.strands) C.strands.forEach((st, i) => { st.rotation.x = .08 + Math.sin(tt * 1.1 + i) * .05; st.rotation.z = Math.sin(tt * .8 + i * 2) * .04; });
  // лицо
  C.blinkT -= dt; let blink = 0; if (C.blinkT < 0) { blink = 1; if (C.blinkT < -.12) C.blinkT = 2 + rnd() * 4; }
  if (C.o.monster) blink = 0;
  U.uBlink.value = blink; if (C.eyes) C.eyes.visible = !blink;
  U.uTalk.value = lerp(U.uTalk.value, clamp(C.talk * 1.3, 0, 1), Math.min(1, dt * 18));
  const fs = C.faceSets[C.faceSet]; U.uSmile.value = C.o.smile || (fs && fs.smile) ? 1 : 0;
  C.keys.visible = P === 'give'; C.paper.visible = P === 'paper';
}

/* ---------- актёры «Ночной смены» ---------- */
function cast() {
  const A = {};
  const guard = { shirt: 0x1c1f26, pants: 0x15171c, shoes: 0x0c0c0c, trim: 0x3a3f4a, badge: true };
  A.temych = makeSkinned(Object.assign({ name: 'temych', h: 1.8, skin: 0xffe8dc, skinK: 1.25, hairPart: 'Hair_SimpleParted', hair: 0x3a2a1c, brow: 0x2a1c12 }, guard));
  A.smenshik = makeSkinned(Object.assign({ name: 'smenshik', h: 1.72, skin: 0xf0d6c8, skinK: 1.2, hairPart: 'Hair_Buzzed', hair: 0x9a9a96, brow: 0x8a8a86, cap: 0x15203a }, guard, { shirt: 0x1d2a44, pants: 0x1a2236 }));
  A.smenshik.addFace('smile', { smile: true });
  const kat = { fem: true, h: 1.64, skin: 0xfff0ea, skinK: 1.3, hairPart: 'Hair_Long', hair: 0x4a3226, brow: 0x1a110c, coat: 0xd2a52a, coatY: .62, pants: 0x223050, shoes: 0x1a1414, trim: 0x8a6a10, wet: true };
  A.katya = makeSkinned(Object.assign({ name: 'katya' }, kat));
  A.katyaSmile = makeSkinned(Object.assign({ name: 'katyaSmile' }, kat, { skin: 0xe6dcd8, skinK: 1.25, grey: .5, smile: true, eyeC: 0x555555 }));
  A.mihalych = makeSkinned(Object.assign({ name: 'mihalych', h: 1.76, skin: 0xead0c0, skinK: 1.2, hairPart: 'Hair_SimpleParted', hair: 0xc8c8c2, brow: 0xb8b8b2, beard: true, beardC: 0xd0d0ca, glasses: true }, guard, { shirt: 0x3a3e40, pants: 0x2a2d30, trim: 0x55595c }));
  A.monster = makeSkinned({ name: 'monster', monster: true, naked: true, h: 2.25, thin: .64, arm: 1.22, armL: 1.15, skin: 0x9aa39e, grey: 1, hairPart: 'Hair_Long', hair: 0x050505, voidEyes: true, smile: true, tilt: .25, jerk: true, veil: true });
  for (const k in A) A[k].update(0, 1 / 30);
  return A;
}

Object.assign(window.__W3chars, { load, makeSkinned });
})();
