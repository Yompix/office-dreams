/* Персонажи: собраны из блоков, лица рисуются кодом. Умеют дышать, моргать, поворачивать голову, говорить (рот по громкости голоса)
   и принимать позы: стоять, сидеть, стучать в стекло, протягивать ключи, сидеть на полу, пятиться. */
(() => {
'use strict';
const { T, rnd, lerp, clamp, mat, ctex, box } = window.__W3core;

// лицо 32x40 пикселей: кожа, брови, глаза, рот (0 закрыт, 1 приоткрыт, 2 открыт), моргание
function drawFace(x, w, h, o, mouth, blink) {
  x.fillStyle = o.skin; x.fillRect(0, 0, w, h);
  const sh = x.createLinearGradient(0, 0, w, 0); sh.addColorStop(0, 'rgba(0,0,0,.22)'); sh.addColorStop(.2, 'rgba(0,0,0,0)'); sh.addColorStop(.8, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(0,0,0,.22)');
  x.fillStyle = sh; x.fillRect(0, 0, w, h);
  if (o.hairFront) { x.fillStyle = o.hair; x.fillRect(0, 0, w, o.hairFront); }
  const ey = o.eyeY || 17;
  // брови
  x.fillStyle = o.brow || '#2a1d14';
  if (o.sad) { x.fillRect(7, ey - 4, 6, 1); x.fillRect(7, ey - 5, 3, 1); x.fillRect(19, ey - 4, 6, 1); x.fillRect(22, ey - 5, 3, 1); }
  else { x.fillRect(7, ey - 4, 7, 2); x.fillRect(18, ey - 4, 7, 2); }
  // глаза
  if (o.voidEyes) { x.fillStyle = '#000'; x.fillRect(7, ey - 2, 7, 5); x.fillRect(18, ey - 2, 7, 5); if (!blink) { x.fillStyle = '#d8d8c8'; x.fillRect(10, ey, 1, 1); x.fillRect(21, ey, 1, 1); } }
  else if (blink) { x.fillStyle = 'rgba(0,0,0,.6)'; x.fillRect(8, ey + 1, 6, 1); x.fillRect(18, ey + 1, 6, 1); }
  else {
    x.fillStyle = '#eee8dc'; x.fillRect(8, ey - 1, 6, 4); x.fillRect(18, ey - 1, 6, 4);
    x.fillStyle = o.iris || '#3b2a1a'; x.fillRect(10, ey - 1, 3, 4); x.fillRect(20, ey - 1, 3, 4);
    x.fillStyle = '#000'; x.fillRect(11, ey, 1, 2); x.fillRect(21, ey, 1, 2);
  }
  if (o.bags) { x.fillStyle = 'rgba(60,30,40,.35)'; x.fillRect(8, ey + 3, 6, 1); x.fillRect(18, ey + 3, 6, 1); }
  if (o.glasses) { x.strokeStyle = '#151515'; x.lineWidth = 1; x.strokeRect(6.5, ey - 2.5, 9, 6); x.strokeRect(16.5, ey - 2.5, 9, 6); x.fillStyle = '#151515'; x.fillRect(15, ey - 1, 2, 1); }
  // нос
  x.fillStyle = 'rgba(0,0,0,.18)'; x.fillRect(15, ey + 2, 2, 6); x.fillRect(14, ey + 7, 4, 1);
  // морщины
  if (o.old) { x.fillStyle = 'rgba(0,0,0,.14)'; x.fillRect(9, 7, 14, 1); x.fillRect(11, 10, 10, 1); x.fillRect(6, ey + 6, 1, 6); x.fillRect(25, ey + 6, 1, 6); }
  if (o.tears) { x.fillStyle = 'rgba(160,190,220,.55)'; x.fillRect(9, ey + 3, 1, 9); x.fillRect(23, ey + 3, 1, 7); }
  // рот
  const my = o.mouthY || 31;
  if (o.smile) {   // слишком широкая улыбка
    x.fillStyle = '#2a0404'; x.fillRect(4, my - 2, 24, 5 + mouth * 2); x.fillRect(2, my - 4, 3, 4); x.fillRect(27, my - 4, 3, 4); x.fillRect(0, my - 6, 3, 3); x.fillRect(29, my - 6, 3, 3);
    x.fillStyle = '#efe9d6'; for (let i = 5; i < 27; i += 2) { x.fillRect(i, my - 2, 1, 2); x.fillRect(i, my + 2 + mouth * 2, 1, 1); }
    x.fillStyle = 'rgba(0,0,0,.25)'; x.fillRect(5, my + 4 + mouth * 2, 22, 1);
  } else if (mouth === 0) { x.fillStyle = o.lip || '#6a3a32'; x.fillRect(11, my, 10, 1); }
  else { x.fillStyle = '#250808'; x.fillRect(12, my - 1, 8, mouth === 1 ? 2 : 4); x.fillStyle = o.lip || '#6a3a32'; x.fillRect(11, my - 2, 10, 1); }
  if (o.mustache) { x.fillStyle = o.beardC; x.fillRect(9, my - 3, 14, 2); x.fillRect(9, my - 1, 2, 2); x.fillRect(21, my - 1, 2, 2); }
  if (o.beard) { x.fillStyle = o.beardC; x.fillRect(4, my + 2, 24, h - my - 2); x.fillRect(4, my - 4, 3, 8); x.fillRect(25, my - 4, 3, 8); x.fillRect(9, my - 3, 14, 2); for (let i = 0; i < 40; i++) { x.fillStyle = 'rgba(0,0,0,.15)'; x.fillRect(4 + rnd() * 24, my + 2 + rnd() * (h - my - 2), 1, 1); } }
  if (o.hairStrands) { x.fillStyle = o.hair; for (const sx of [2, 5, 9, 24, 27]) x.fillRect(sx, 0, 2, h - 4 - (sx % 3) * 4); x.fillRect(13, 0, 2, ey + 5); }
  if (o.wet) { x.fillStyle = 'rgba(200,220,240,.35)'; for (let i = 0; i < 8; i++) x.fillRect(rnd() * w, rnd() * h, 1, 2); }
}

// текстура всей головы (обёртка вокруг сферы): лицо спереди, волосы сверху и сзади
function drawHead(x, W, H, o, f, mouth, blink) {
  x.fillStyle = f.skin; x.fillRect(0, 0, W, H);
  const hair = f.hair || '#111';
  x.fillStyle = hair;
  if (o.bald) x.fillRect(0, 0, W, 6); else x.fillRect(0, 0, W, 20);
  if (o.longHair) { x.fillRect(54, 0, W - 54, H); x.fillRect(0, 0, 10, H); }
  else if (!o.bald) { x.fillRect(54, 0, W - 54 - 6, 38); x.fillRect(46, 14, 8, 10); x.fillRect(10, 14, 8, 10); }
  for (let i = 0; i < 60; i++) { x.fillStyle = 'rgba(255,255,255,.05)'; x.fillRect(Math.random() * W, Math.random() * 20, 3, 1); }
  if (f.beard) { x.fillStyle = f.beardC; x.fillRect(10, 46, 8, 18); x.fillRect(46, 46, 8, 18); }
  x.save(); x.translate(12, 19); x.scale(1.25, 1.07); x.beginPath(); x.rect(0, 0, 32, 42); x.clip(); drawFace(x, 32, 42, f, mouth, blink); x.restore();
  // плавный переход лица в бока головы
  x.fillStyle = 'rgba(0,0,0,.12)'; x.fillRect(10, 20, 2, 44); x.fillRect(52, 20, 2, 44);
}
function makeHuman(o) {
  const H = o.h || 1.75, s = H / 1.75, th = o.thin || 1, al = o.arm || 1;
  const root = new T.Group(); root.name = o.name;
  const skinM = mat(o.skin), clothM = mat(o.cloth), pantsM = mat(o.pants || o.cloth), shoeM = mat(o.shoes || 0x151515), hairM = mat(o.hairC || 0x1a120c);
  const legL = .86 * s, torsoH = .56 * s, tw = .42 * s * th, td = .23 * s * th;
  const hips = new T.Group(); hips.position.y = legL; root.add(hips);
  const chest = new T.Group(); hips.add(chest);
  box(tw, torsoH, td, clothM, 0, torsoH / 2, 0, chest);
  if (o.coatLen) box(tw * 1.04, o.coatLen, td * 1.08, clothM, 0, -o.coatLen / 2 + .02, 0, chest);
  if (o.badge) box(.05, .05, .01, mat(0xc9a43a, null, { emissive: 0x2a2000 }), tw * .25, torsoH * .78, td / 2 + .006, chest);
  if (o.collar) box(tw * .6, .06, td * .9, mat(o.collar), 0, torsoH, 0, chest);
  const neck = box(.08 * s, .07 * s, .08 * s, skinM, 0, torsoH + .03 * s, 0, chest);
  const head = new T.Group(); head.position.y = torsoH + .06 * s; chest.add(head);
  const hw = .2 * s, hh = .25 * s, hd = .22 * s;
  // лицо: 6 состояний (рот 0..2 × моргание)
  const genFaces = fo => { const f = []; for (let b = 0; b < 2; b++) for (let mo = 0; mo < 3; mo++) { const t = ctex(128, 64, (x, w, h) => drawHead(x, w, h, o, fo, mo, b)); t.wrapS = T.ClampToEdgeWrapping; f.push(t); } return f; };
  const faces = genFaces(o.face);
  const faceM = new T.MeshLambertMaterial({ map: faces[0] }); window.__W3core.ps1(faceM);
  const headMesh = new T.Mesh(HEADG, faceM); headMesh.scale.set(hw, hh, hd); headMesh.position.y = hh / 2; head.add(headMesh);
  box(.032 * s, .05 * s, .035 * s, skinM, 0, hh * .4, hd / 2, head);                                              // нос
  if (!o.longHair) for (const sx of [-1, 1]) box(.02, .05 * s, .035 * s, skinM, sx * hw / 2, hh * .48, -.005, head);   // уши
  if (!o.bald) { const cap = new T.Mesh(CAPG, hairM); cap.scale.set(hw * 1.07, hh * 1.05, hd * 1.08); cap.position.set(0, hh / 2 + .004, -.012); cap.rotation.x = -.32; head.add(cap); }   // объём волос
  if (o.longHair) { box(hw * 1.05, o.longHair, .05, hairM, 0, hh * .8 - o.longHair / 2, -hd * .38, head); for (const sx of [-1, 1]) box(.035, o.longHair * .8, hd * .62, hairM, sx * hw * .52, hh * .82 - o.longHair * .4, -hd * .08, head); }

  if (o.cap) { box(hw * 1.15, .07, hd * 1.15, mat(o.cap), 0, hh + .02, 0, head); box(hw * 1.1, .015, .09, mat(o.cap), 0, hh - .005, hd / 2 + .04, head); box(.04, .03, .01, mat(0xc9a43a), 0, hh + .03, hd / 2 + .08, head); }
  if (o.hood) box(hw * 1.3, .1, hd * 1.2, clothM, 0, -.02, -hd * .45, head);
  function limb(len, wdt, m, m2, x, y, endM) {
    const piv = new T.Group(); piv.position.set(x, y, 0);
    box(wdt, len / 2, wdt, m, 0, -len / 4, 0, piv);
    const j = new T.Group(); j.position.y = -len / 2; piv.add(j);
    box(wdt * .92, len / 2, wdt * .92, m2, 0, -len / 4, 0, j);
    const end = box(wdt * 1.05, wdt * 1.1, endM === shoeM ? wdt * 2 : wdt * .9, endM, 0, -len / 2 - wdt * .5, endM === shoeM ? wdt * .4 : 0, j);
    return { piv, j, end };
  }
  const aLen = .64 * s * al, aW = .085 * s * th;
  const armL = limb(aLen, aW, clothM, o.sleeve ? mat(o.sleeve) : clothM, -tw / 2 - aW / 2 - .005, torsoH - .04, skinM);
  const armR = limb(aLen, aW, clothM, o.sleeve ? mat(o.sleeve) : clothM, tw / 2 + aW / 2 + .005, torsoH - .04, skinM);
  chest.add(armL.piv, armR.piv);
  const lW = .12 * s * th;
  const legLft = limb(legL - .04, lW, pantsM, pantsM, -.1 * s * th, 0, shoeM), legRgt = limb(legL - .04, lW, pantsM, pantsM, .1 * s * th, 0, shoeM);
  hips.add(legLft.piv, legRgt.piv);
  // предмет в руке (ключи, листок)
  const prop = new T.Group(); armR.end.add(prop);
  const keys = new T.Group(); box(.05, .006, .03, mat(0xb8a060, null, { emissive: 0x201800 }), 0, 0, 0, keys); box(.008, .05, .008, mat(0xc0c0c0), .01, -.03, 0, keys); box(.008, .04, .008, mat(0xc0c0c0), -.01, -.025, .005, keys); keys.position.set(0, -.04, .03); keys.visible = false; prop.add(keys);
  const paper = box(.16, .2, .005, mat(0xe0d8c0), 0, -.06, .06, prop); paper.visible = false;

  const C = { root, hips, chest, head, headMesh, armL, armR, legL: legLft, legR: legRgt, faces, faceM, keys, paper, o,
    pose: 'stand', look: null, talk: 0, blinkT: 1 + rnd() * 3, t0: rnd() * 10, legLen: legL, faceSets: { base: faces }, faceSet: 'base' };
  C.addFace = (nm, ov) => { C.faceSets[nm] = genFaces(Object.assign({}, o.face, ov)); };
  C.update = (t, dt) => updateHuman(C, t, dt);
  return C;
}

const HEADG = new T.SphereGeometry(.5, 12, 9), CAPG = new T.SphereGeometry(.5, 12, 5, 0, Math.PI * 2, 0, Math.PI * .42);
const _v = new T.Vector3(), _q = new T.Quaternion();
function setRot(g, x, y, z, k) { g.rotation.x = lerp(g.rotation.x, x, k); g.rotation.y = lerp(g.rotation.y, y, k); g.rotation.z = lerp(g.rotation.z, z, k); }
function updateHuman(C, t, dt) {
  const k = Math.min(1, dt * 8), tt = t + C.t0, P = C.pose;
  let hy = C.legLen, hrx = 0, lHip = 0, rHip = 0, lKnee = 0, rKnee = 0, lSh = [.05, 0, -.08], rSh = [.05, 0, .08], lEl = -.12, rEl = -.12, chestX = 0, rootY = 0;
  const breath = Math.sin(tt * 1.7) * .012;
  if (P === 'sit') { hy = .48; lHip = rHip = -1.5; lKnee = rKnee = 1.5; lSh = [-.5, 0, -.1]; rSh = [-.6, 0, .1]; lEl = -.9; rEl = -.8; }
  else if (P === 'sitfloor') { hy = .2; lHip = rHip = -1.45; lKnee = rKnee = 2.5; lSh = [-.4, 0, -.15]; rSh = [-.9, 0, .1]; lEl = -1.2; rEl = -1.1; chestX = .15; }
  else if (P === 'knock') { lSh = [-1.35, 0, -.25]; lEl = -.5; const kn = Math.max(0, Math.sin(tt * 9)) ** 4; rSh = [-1.5 + kn * .2, 0, .2]; rEl = -1.3 + kn * .5; chestX = .06; }
  else if (P === 'give') { rSh = [-1.25, 0, .05]; rEl = -.25; chestX = .08; }
  else if (P === 'paper') { lSh = [-1, 0, -.1]; rSh = [-1, 0, .1]; lEl = -.9; rEl = -.9; }
  else if (P === 'monster') { lSh = [.08 + Math.sin(tt * .7) * .03, 0, -.12]; rSh = [.1, 0, .14 + Math.sin(tt * .9) * .03]; lEl = -.05; rEl = -.08; chestX = .25 + Math.sin(tt * .5) * .04; }
  else if (P === 'reach') { lSh = [-1.4, 0, -.1]; rSh = [-1.5, 0, .1]; lEl = -.1; rEl = -.15; chestX = .3; }
  else if (P === 'walk' || P === 'back') { const w = Math.sin(tt * 5.5) * (P === 'back' ? .35 : .5); lHip = w; rHip = -w; lKnee = Math.max(0, -w) * 1.2; rKnee = Math.max(0, w) * 1.2; lSh = [-w * .6, 0, -.08]; rSh = [w * .6, 0, .08]; rootY = Math.abs(Math.cos(tt * 5.5)) * .03; }
  else if (P === 'hug') { lSh = [-1.4, .5, -.2]; rSh = [-1.4, -.5, .2]; lEl = -1.2; rEl = -1.2; chestX = .1; }
  C.hips.position.y = lerp(C.hips.position.y, hy + rootY, k);
  setRot(C.chest, chestX + breath, 0, 0, k);
  setRot(C.legL.piv, lHip, 0, 0, k); setRot(C.legR.piv, rHip, 0, 0, k); setRot(C.legL.j, lKnee, 0, 0, k); setRot(C.legR.j, rKnee, 0, 0, k);
  setRot(C.armL.piv, lSh[0], lSh[1], lSh[2], k); setRot(C.armR.piv, rSh[0], rSh[1], rSh[2], k); setRot(C.armL.j, lEl, 0, 0, k); setRot(C.armR.j, rEl, 0, 0, k);
  // голова: смотрит на цель (обычно в камеру) в пределах шеи
  let yaw = Math.sin(tt * .4) * .08, pitch = 0;
  if (C.look) {
    C.head.parent.updateWorldMatrix(true, false); C.head.getWorldPosition(_v);
    const dx = C.look.x - _v.x, dy = C.look.y - (_v.y + .12), dz = C.look.z - _v.z;
    const ry = C.root.rotation.y;
    let a = Math.atan2(dx, dz) - ry; a = Math.atan2(Math.sin(a), Math.cos(a)); yaw = clamp(a, -1.1, 1.1); pitch = clamp(-Math.atan2(dy, Math.hypot(dx, dz)), -.6, .6) - C.chest.rotation.x;
  }
  if (C.o.tilt) C.head.rotation.z = Math.sin(tt * .3) * .05 + C.o.tilt;
  if (C.o.jerk && Math.sin(tt * 1.3) > .985) yaw += (rnd() - .5) * .6;
  C.head.rotation.y = lerp(C.head.rotation.y, yaw, k); C.head.rotation.x = lerp(C.head.rotation.x, pitch, k);
  // моргание и рот
  C.blinkT -= dt; let blink = 0; if (C.blinkT < 0) { blink = 1; if (C.blinkT < -.12) C.blinkT = 2 + rnd() * 4; }
  const tv = C.talk; const mo = tv > .55 ? 2 : tv > .18 ? 1 : 0;
  const f = (C.faceSets[C.faceSet] || C.faces)[blink * 3 + mo]; if (C.faceM.map !== f) { C.faceM.map = f; C.faceM.needsUpdate = true; }
  C.keys.visible = P === 'give'; C.paper.visible = P === 'paper';
}

// актёры «Ночной смены»
function cast() {
  const A = {};
  A.temych = makeHuman({ name: 'temych', h: 1.8, skin: 0xc49a7c, cloth: 0x1b1d22, pants: 0x14161a, hairC: 0x18110b, badge: true, collar: 0x2a2d33,
    face: { skin: '#c49a7c', hair: '#18110b', hairFront: 4, iris: '#3a2a1c', bags: true } });
  A.smenshik = makeHuman({ name: 'smenshik', h: 1.72, skin: 0xb88c72, cloth: 0x1d2a44, pants: 0x1a2236, hairC: 0x8a8a86, cap: 0x15203a, badge: true,
    face: { skin: '#b88c72', hair: '#8a8a86', iris: '#4a5a60', old: true, mustache: true, beardC: '#9a9a96', brow: '#8f8f8a', bags: true } });
  A.katya = makeHuman({ name: 'katya', h: 1.63, skin: 0xd8b49c, cloth: 0xc9a227, sleeve: 0xc9a227, pants: 0x223050, hairC: 0x120c0a, longHair: .42, coatLen: .25, hood: true,
    face: { skin: '#d8b49c', hair: '#120c0a', hairFront: 6, iris: '#5a3a1a', lip: '#9a4a4a', wet: true, sad: true, tears: true, brow: '#1a110c' } });
  A.katyaSmile = makeHuman({ name: 'katyaSmile', h: 1.63, skin: 0xcfb2a2, cloth: 0xc9a227, sleeve: 0xc9a227, pants: 0x223050, hairC: 0x120c0a, longHair: .42, coatLen: .25, hood: true,
    face: { skin: '#cfb2a2', hair: '#120c0a', hairFront: 6, iris: '#222', lip: '#7a3a3a', wet: true, smile: true, brow: '#1a110c' } });
  A.mihalych = makeHuman({ name: 'mihalych', h: 1.78, skin: 0xb08670, cloth: 0x34383c, pants: 0x26292c, hairC: 0xbdbdb8, bald: false, badge: true,
    face: { skin: '#b08670', hair: '#bdbdb8', iris: '#4a4a4a', old: true, beard: true, beardC: '#c9c9c4', brow: '#b5b5b0', glasses: true, bags: true } });
  A.monster = makeHuman({ name: 'monster', h: 2.35, thin: .62, arm: 1.45, skin: 0xb4b8b0, cloth: 0x2a2c2a, pants: 0x1e201e, shoes: 0xb4b8b0, hairC: 0x050505, longHair: .7, veil: .14, tilt: .22, jerk: true,
    face: { skin: '#b4b8b0', hair: '#050505', voidEyes: true, smile: true, hairStrands: true, eyeY: 15, mouthY: 29, brow: '#5f625f' } });
  A.victim = makeHuman({ name: 'victim', h: 1.75, skin: 0x9a948c, cloth: 0x24406e, pants: 0x24406e, hairC: 0x2a2420,
    face: { skin: '#9a948c', hair: '#2a2420', iris: '#333', old: true } });
  A.smenshik.addFace('smile', { smile: true, mustache: false });
  A.temych.addFace('scared', { sad: true });
  for (const k in A) A[k].update(0, 1);
  return A;
}

window.__W3chars = { makeHuman, cast };
})();
