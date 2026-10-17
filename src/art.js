/* ============================================================
   OFFICE DREAMS — ART 2: the illustrated office (test mode, ?art=2).
   The room is a picture; the team is drawn on top: up to four people
   at the desks (seen from behind, typing), the rest standing around,
   the cat on the beanbag. Ideas, bugs, chat bubbles and taps use the
   same helpers as the classic scene through a "virtual scene" space:
   picture pixels / ART_K, so radii and offsets keep their meaning.
   ============================================================ */
const ART_OF = { img: 'office_empty', W: 1456, H: 1088 };
const ART_K = ART_OF.W / 188; // picture px per virtual scene unit (the classic camera is 188 units wide)
// desks: chair (feet/seat point), monitor centre — picture pixels
// seat = where the chair wheels touch the floor; flip = the person faces left (towards a monitor on the left)
const ART_DESKS = [
  { seat: [705, 520], mon: [610, 370], flip: true },
  { seat: [1090, 602], mon: [1190, 450], flip: false },
  { seat: [538, 812], mon: [607, 675], flip: false },
  { seat: [1188, 900], mon: [1090, 755], flip: true },
];
const ART_HERO = [842, 700]; // the boss stands in the middle of the room
const ART_STANDS = [[985, 760], [262, 690], [1300, 712], [905, 930], [700, 640]];
// AI-drawn characters (cut out of generated sprite sheets): seat_<role>.webp and stand_<role|you>.webp
const ART_H = { seat: 0.13, stand: 0.165 };
function artChar(kind, role) { return artImg(kind + '_' + role); }
const ART_CAT = [392, 562];
const ART_SIGN = [292, 183];
const ART_LAMPS = [[386, 370], [1030, 345], [727, 730], [1253, 950], [270, 588], [687, 340], [1067, 427], [1213, 713], [91, 340]];
// things to tap in the picture (picture px, radius). Each one reacts; the first tap on each kind is an office secret
// (cosmetic, kept on this device only, like the city secrets)
const ART_HOT = [
  { k: 'neon', x: 292, y: 183, r: 95 }, { k: 'rack', x: 146, y: 467, r: 85 }, { k: 'pc', x: 735, y: 353, r: 35, sy: 250 },
  { k: 'window', x: 1020, y: 230, r: 120 }, { k: 'window', x: 1360, y: 360, r: 100 },
  { k: 'art', x: 542, y: 218, r: 80 }, { k: 'art', x: 657, y: 195, r: 55 }, { k: 'art', x: 1178, y: 280, r: 55 },
  { k: 'books', x: 338, y: 465, r: 65 }, { k: 'books', x: 950, y: 380, r: 60 }, { k: 'books', x: 1300, y: 990, r: 80 },
  { k: 'plant', x: 780, y: 280, r: 65 }, { k: 'plant', x: 1390, y: 770, r: 70 }, { k: 'plant', x: 150, y: 860, r: 70 }, { k: 'plant', x: 810, y: 990, r: 70 }, { k: 'plant', x: 40, y: 550, r: 50 },
  { k: 'stairs', x: 290, y: 975, r: 90 }, { k: 'rug', x: 900, y: 665, r: 110 },
];
const ART_SECRETS = ['neon', 'rack', 'pc', 'window', 'art', 'books', 'plant', 'lamp', 'stairs', 'rug'];
const ART_X = { neon: 2, rack: 2, pc: 2, window: 2, art: 2, books: 3, plant: 2, stairs: 1, rug: 1, coffee: 2, lounge: 2, flamp: 1, rackon: 1 }; // variants of of.<k>_<n>
const ART_ICO = { neon: '✨', rack: '☁️', pc: '💻', window: '🌠', art: '🖼️', books: '📚', plant: '💧', lamp: '💡', stairs: '⬆️', rug: '🧶', coffee: '☕', lounge: '🛋️', flamp: '💡', rackon: '☁️' };
const ART_NEON = [['#ff3fd0', '#3fd0ff'], ['#ffd23f', '#ff4d6d'], ['#5dff9c', '#3fd0ff'], ['#a06aff', '#ff3fd0']];
// things bought in the Mall show up in the room: a picture (base point in picture px, height as a fraction of the picture)
// or a light; tapping them reacts, an empty spot hints where to buy
const ART_ITEMS = {
  coffee: { img: 'item_coffee', x: 460, y: 520, h: 0.13, r: 60 },
  lounge: { img: 'item_sofa', x: 900, y: 525, h: 0.095, r: 95 },
  lamp: { img: 'item_flamp', x: 1000, y: 540, h: 0.21, r: 70, flip: true },
};
// what is going on right now (performance.now() seconds of the last tap on each thing)
const AFX = { steam: -9, neon: -9, neonC: 0, rack: -9, pc: -9, star: -9, starAt: null, spark: -9, sparkAt: null, drops: -9, dropAt: null, dust: -9, dustAt: null, lampOff: {}, found: null };
function artFound() {
  if (!AFX.found) { try { AFX.found = new Set(JSON.parse(lsGet('od2_office_found') || '[]')); } catch (e) { AFX.found = new Set(); } }
  return AFX.found;
}
function artOfficeOn() { return artOn() && !!artImg(ART_OF.img); }
// role colours for the team sprites
const ROLE_LOOK = {
  you: LOOK2[0],
  prog: { skin: '#e8b890', skinD: '#c08a62', hair: '#2a1a10', hairL: '#4a3220', top: '#2f6ae0', topD: '#1e45a8', topL: '#5a8cff', pants: '#232a4e', pantsD: '#151a33', shoe: '#e8ecff' },
  art: { skin: '#f2cda6', skinD: '#d2a27c', hair: '#ff6aa0', hairL: '#ff9cc4', top: '#7a52b8', topD: '#553690', topL: '#a07ae0', pants: '#2c2c52', pantsD: '#1c1c38', shoe: '#2a2a3a' },
  des: { skin: '#c99a70', skinD: '#a07450', hair: '#1a1018', hairL: '#3a2a40', top: '#5a6a8a', topD: '#3a4866', topL: '#7a8aaa', pants: '#1c1c2c', pantsD: '#10101c', shoe: '#e0d0b0', acc: '#ffd23f' },
  mkt: { skin: '#ecbe96', skinD: '#c89670', hair: '#c96a2a', hairL: '#e8844a', top: '#3f58b0', topD: '#2a3c80', topL: '#5a78d8', pants: '#14141e', pantsD: '#0a0a12', shoe: '#5a3a20', tie: '#ffd23f' },
  qa: { skin: '#a8714a', skinD: '#7e4f32', hair: '#0e0a14', hairL: '#2a2030', top: '#2e9a5a', topD: '#1f6e40', topL: '#4ccf80', pants: '#3a2c1c', pantsD: '#261c10', shoe: '#e8ecff' },
  snd: { skin: '#f0c9a0', skinD: '#c89a74', hair: '#7a3ad0', hairL: '#a06aff', top: '#d0468a', topD: '#9c2e66', topL: '#f070ac', pants: '#1c1c2c', pantsD: '#10101c', shoe: '#2a2a3a', acc: '#ff3fd0' },
  prod: { skin: '#d8a880', skinD: '#b08058', hair: '#e8e0d0', hairL: '#ffffff', top: '#2a2c44', topD: '#1a1c30', topL: '#44486a', pants: '#14141e', pantsD: '#0a0a12', shoe: '#5a3a20', tie: '#ff4d6d' },
  cm: { skin: '#f2cda6', skinD: '#d2a27c', hair: '#3a2a1a', hairL: '#5a4230', top: '#ff9a3c', topD: '#c86a1a', topL: '#ffbe70', pants: '#232a4e', pantsD: '#151a33', shoe: '#e8ecff' },
};
// someone seen from behind at a desk, hands on the keyboard (typing frames 0/1)
function sprSeat(role, f) {
  return sprMake('seat' + role + f, 16, 24, (g) => {
    const L0 = ROLE_LOOK[role] || ROLE_LOOK.prog, P = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
    // shoulders + back
    P(2, 9, 12, 9, L0.top); P(2, 9, 2, 9, L0.topL); P(12, 9, 2, 9, L0.topD); P(7, 10, 1, 7, L0.topD);
    // arms reaching forward (typing)
    P(0, 10 + f, 2, 6, L0.topD); P(14, 11 - f, 2, 6, L0.topD);
    // neck, head from behind, ears
    P(6, 7, 4, 3, L0.skinD);
    P(4, 0, 8, 8, L0.hair); P(5, 0, 4, 1, L0.hairL); P(3, 3, 1, 2, L0.skin); P(12, 3, 1, 2, L0.skinD);
    if (role === 'art') { P(6, -2, 4, 3, L0.hair); P(7, -2, 2, 1, L0.hairL); }
    if (L0.acc) { P(3, 1, 1, 4, L0.acc); P(12, 1, 1, 4, L0.acc); P(4, -1, 8, 1, L0.acc); }
    // the chair back in front of the lower body
    P(3, 16, 10, 7, '#23263e'); P(3, 16, 10, 1, '#3a3e62'); P(3, 16, 1, 7, '#30345a'); P(7, 22, 2, 2, '#14162a');
  });
}
function sprStand(role, f, still) {
  const L0 = ROLE_LOOK[role] || ROLE_LOOK.prog;
  const key = 'st' + role;
  if (!LOOK2[key]) { LOOK2[key] = L0; }
  return sprPerson(key, f, still);
}
// people standing around now and then stroll to a nearby spot on the open floor and come back
const ART_STROLL = [[1080, 690], [335, 625], [1365, 655], [985, 862], [820, 600]];
function artStroll(i, t) {
  const P = 17 + i * 4, ph = ((t + i * 5.3) % P) / P, A = ART_STANDS[i], B = ART_STROLL[i];
  const ease = (k) => k * k * (3 - 2 * k);
  let k = 0, dir = 0;
  if (ph < 0.45) k = 0;
  else if (ph < 0.58) { k = ease((ph - 0.45) / 0.13); dir = 1; }
  else if (ph < 0.85) k = 1;
  else { k = 1 - ease((ph - 0.85) / 0.15); dir = -1; }
  const right = B[0] > A[0];
  return { x: A[0] + (B[0] - A[0]) * k, y: A[1] + (B[1] - A[1]) * k, walk: dir !== 0, flip: dir ? (dir > 0) !== right : k > 0.5 ? right : i % 2 === 1 };
}
// who sits where: the first four hires at the desks, the rest stand (and stroll); the boss stands in the middle
function artPlaces() {
  const out = { you: { seat: false, x: ART_HERO[0], y: ART_HERO[1], hero: true } };
  const t = performance.now() / 1000;
  let d = 0, s = 0;
  for (const r of ROLES) {
    if (!S.staff[r] || S.staff[r].lvl <= 0) continue;
    if (d < ART_DESKS.length) { const D = ART_DESKS[d]; out[r] = { seat: true, x: D.seat[0], y: D.seat[1], desk: d, flip: D.flip }; d++; }
    else if (s < ART_STANDS.length) { out[r] = Object.assign({ seat: false }, artStroll(s, t)); s++; }
  }
  return out;
}
// picture placement inside the scene canvas (cover)
function artOfficeFit(cv) {
  const im = artImg(ART_OF.img);
  return artFit(cv, im, 0.5);
}
function artSceneMap() {
  const cv = UI.sceneCv;
  if (!cv || !cv.isConnected) return null;
  const r = cv.getBoundingClientRect();
  if (!r.width) return null;
  const F = artOfficeFit(cv), kx = r.width / cv.width, ky = r.height / cv.height, s = F.w / ART_OF.W; // canvas px per picture px
  return {
    r,
    toCss: (x, y) => [(F.x + x * ART_K * s) * kx, (F.y + y * ART_K * s) * ky],
    toScene: (cx, cy) => [((cx - r.left) / kx - F.x) / s / ART_K, ((cy - r.top) / ky - F.y) / s / ART_K],
    scale: s * ART_K * kx,
  };
}
// head position of someone (virtual scene units), for ideas, bubbles and taps
function artSpot(kind) {
  if (kind === 'cat') return [ART_CAT[0] / ART_K, (ART_CAT[1] - 30) / ART_K];
  if (kind === 'laptop') return [ART_HERO[0] / ART_K, (ART_HERO[1] - 0.08 * ART_OF.H) / ART_K];
  const p = artPlaces()[kind];
  if (!p) return null;
  const head = p.seat ? ART_H.seat * 0.82 : ART_H.stand * 0.86;
  return [p.x / ART_K, (p.y - head * ART_OF.H) / ART_K];
}
function artBugSpot() {
  if (!S.bug) return null;
  const k = 1 - Math.max(0, S.bug.ttl) / BUG_TTL, lane = S.bug.lane || 0, dir = lane === 1 ? -1 : 1;
  const x0 = 14, x1 = 188 - 14, x = dir > 0 ? x0 + (x1 - x0) * k : x1 - (x1 - x0) * k;
  const y = [700, 820, 940][lane] / ART_K + Math.sin(k * 40) * 3;
  return [x, y, dir];
}
// draw the illustrated office into the scene canvas
function artOfficeDraw(ctx, cv, st, t) {
  const im = artImg(ART_OF.img);
  const F = artOfficeFit(cv), s = F.w / ART_OF.W, X = (x) => F.x + x * s, Y = (y) => F.y + y * s;
  ctx.imageSmoothingEnabled = true;
  ctx.clearRect(0, 0, cv.width, cv.height);
  ctx.drawImage(im, F.x, F.y, F.w, F.h);
  const working = st.working;
  // living light: the neon sign, desk lamps, monitors (brighter while the team works)
  const pal = ART_NEON[AFX.neonC], flick = t - AFX.neon < 1.2;
  artGlow(ctx, X(ART_SIGN[0]), Y(ART_SIGN[1]), F.h * 0.16, Math.sin(t * 1.3) > 0 ? pal[0] : pal[1], flick ? (Math.sin(t * 57) > 0 ? 0.5 : 0.04) : 0.12 + 0.06 * Math.sin(t * 3));
  if (AFX.neonC) artGlow(ctx, X(ART_SIGN[0]), Y(ART_SIGN[1]), F.h * 0.09, pal[0], 0.22);
  ART_LAMPS.forEach((l, i) => {
    if (AFX.lampOff[i]) artShade(ctx, X(l[0]), Y(l[1]), F.h * 0.1, 0.6);
    else artGlow(ctx, X(l[0]), Y(l[1]), F.h * 0.07, '#ffc070', 0.16 + 0.04 * Math.sin(t * 5 + i * 1.7));
  });
  const mon2 = S.items && S.items.monitor2 ? 1.5 : 1;
  ART_DESKS.forEach((d, i) => artGlow(ctx, X(d.mon[0]), Y(d.mon[1]), F.h * 0.1 * (mon2 > 1 ? 1.2 : 1), '#3fd0ff', ((working ? 0.16 : 0.07) + (working ? 0.06 * Math.sin(t * 9 + i * 2) : 0)) * mon2));
  if (st.turbo || st.hype) artGlow(ctx, F.x + F.w / 2, F.y + F.h / 2, F.h * 0.7, st.turbo ? '#ff4d6d' : '#ffd23f', 0.06 + 0.04 * Math.sin(t * 6));
  artItems(ctx, F, s, X, Y, t);
  artOfficeFx(ctx, F, s, X, Y, t);
  // the team, back to front
  const places = artPlaces(), list = Object.keys(places).map((k) => Object.assign({ k }, places[k])).sort((a, b) => a.y - b.y);
  const blit = (spr, x, y, hFrac, flip) => {
    const sc = (hFrac * F.h) / spr.height, w = spr.width * sc, h = spr.height * sc;
    ctx.save(); ctx.imageSmoothingEnabled = false; ctx.translate(Math.round(X(x)), Math.round(Y(y))); if (flip) ctx.scale(-1, 1);
    ctx.drawImage(spr, -w / 2, -h, w, h); ctx.restore();
  };
  // an AI-drawn character: soft shadow, then the picture; seated people type (quick bob), standing ones breathe
  const person = (im, x, y, hFrac, flip, bob, sx, tilt) => {
    const h = hFrac * F.h * (1 + (sx || 0)), w = h * (im.naturalWidth || im.width) / (im.naturalHeight || im.height);
    ctx.save(); ctx.globalAlpha = 0.35; ctx.fillStyle = '#000'; ctx.beginPath(); ctx.ellipse(X(x), Y(y), w * 0.42, h * 0.05, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore();
    ctx.save(); ctx.imageSmoothingEnabled = true; ctx.translate(X(x), Y(y) + bob); if (tilt) ctx.rotate(tilt); if (flip) ctx.scale(-1, 1);
    ctx.drawImage(im, -w / 2, -h, w, h); ctx.restore();
  };
  for (const p of list) {
    const im = artChar(p.seat ? 'seat' : 'stand', p.k);
    if (p.seat) {
      const typing = working && Math.sin(t * 1.3 + p.desk * 2) > -0.4;
      const bob = typing ? (Math.floor(t * 9 + p.desk) % 2) * F.h * 0.002 : Math.sin(t * 1.6 + p.desk) * F.h * 0.0015;
      if (im) person(im, p.x, p.y, ART_H.seat, p.flip, bob);
      else blit(sprSeat(p.k, 0), p.x, p.y, 0.1);
    } else {
      const breathe = p.walk ? 0 : Math.sin(t * 1.8 + p.k.charCodeAt(0)) * 0.006;
      const tap = p.hero && st.tapFlash > 0 ? -F.h * 0.006 : 0;
      if (im) person(p.walk ? artWalk(im, t * 1.5 + p.k.length * 0.3) : im, p.x, p.y, ART_H.stand, p.flip, tap, breathe);
      else blit(sprStand(p.k, 0, true), p.x, p.y, 0.15, p.flip);
    }
  }
  // the cat on the beanbag
  const petted = t - (UI.catPetT || -9) < 2.5, cim = artImg(petted ? 'cat_sit' : 'cat_sleep');
  if (cim) person(cim, ART_CAT[0], ART_CAT[1] + 6, petted ? 0.075 : 0.05, false, petted ? 0 : Math.sin(t * 1.2) * F.h * 0.0012);
  else blit(sprCat(false), ART_CAT[0], ART_CAT[1], 0.045, Math.sin(t * 0.3) > 0.7);
}
// the Mall's things in the room: coffee machine (steams now and then), sofa, floor lamp, gaming-chair lights, the busy server
function artItems(ctx, F, s, X, Y, t) {
  const it = (S && S.items) || {};
  const pic = (k) => {
    const o = ART_ITEMS[k], im = artImg(o.img);
    if (!im) return;
    const h = o.h * F.h, w = h * im.naturalWidth / im.naturalHeight;
    ctx.save(); ctx.globalAlpha = 0.35; ctx.fillStyle = '#000'; ctx.beginPath(); ctx.ellipse(X(o.x), Y(o.y), w * 0.45, h * 0.07, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore();
    ctx.save(); ctx.translate(X(o.x), Y(o.y)); if (o.flip) ctx.scale(-1, 1); ctx.drawImage(im, -w / 2, -h, w, h); ctx.restore();
    return { w, h };
  };
  if (it.lounge) pic('lounge');
  if (it.coffee) {
    pic('coffee');
    const o = ART_ITEMS.coffee, burst = t - AFX.steam < 2, sx = X(o.x - 30), sy = Y(o.y - o.h * ART_OF.H * 0.62);
    ctx.fillStyle = '#ffffff';
    for (let i = 0; i < (burst ? 10 : 3); i++) {
      const k = ((t * (burst ? 30 : 9)) + i * 11) % 50;
      ctx.globalAlpha = Math.max(0, (burst ? 0.55 : 0.3) - k / 100);
      ctx.fillRect(sx + Math.sin(i * 2.1 + k * 0.12) * (2 + k / 4) * s, sy - k * s * 1.2, 5 * s, 5 * s);
    }
    ctx.globalAlpha = 1;
    artGlow(ctx, X(o.x), Y(o.y - o.h * ART_OF.H * 0.7), F.h * 0.04, '#5dff9c', 0.14);
  }
  if (it.lamp) {
    // the arc floor lamp leans over the sofa; its shade glows unless switched off
    const o = ART_ITEMS.lamp, d = pic('lamp'), off = AFX.lampOff.floor;
    if (d && !off) {
      const hx = X(o.x) + (o.flip ? -1 : 1) * d.w * 0.31, hy = Y(o.y) - d.h * 0.8;
      artGlow(ctx, hx, hy, F.h * 0.1, '#ffc070', 0.32 + 0.03 * Math.sin(t * 3)); artGlow(ctx, hx, Y(o.y), F.h * 0.09, '#ffc070', 0.14);
    } else if (d) artShade(ctx, X(o.x) + (o.flip ? -1 : 1) * d.w * 0.31, Y(o.y) - d.h * 0.8, F.h * 0.04, 0.5);
  }
  if (it.chairs) ART_DESKS.forEach((d, i) => artGlow(ctx, X(d.seat[0]), Y(d.seat[1] - 12), F.h * 0.045, i % 2 ? '#ff3fd0' : '#22d6f0', 0.32 + 0.1 * Math.sin(t * 2 + i)));
  if (it.rack) {
    // your own server: lights run all the time
    const tick = Math.floor(t * 5), cols = ['#3fd0ff', '#5dff9c', '#ff3fd0'];
    for (let j = 0; j < 28; j++) { if ((j * 37 + tick * 13) % 100 > 30) continue; ctx.fillStyle = cols[(j + tick) % 3]; ctx.globalAlpha = 0.8; ctx.fillRect(X(98 + (j % 4) * 28), Y(372 + Math.floor(j / 4) * 31), 9 * s, 4 * s); }
    ctx.globalAlpha = 1; artGlow(ctx, X(146), Y(470), F.h * 0.12, '#3fd0ff', 0.1 + 0.04 * Math.sin(t * 4));
  } else artShade(ctx, X(146), Y(480), F.h * 0.12, 0.25);
}
// the little reactions to taps: blinking server, a shooting star, sparkles, water drops, dust, the build bar
function artOfficeFx(ctx, F, s, X, Y, t) {
  const d = (k) => t - AFX[k];
  if (d('rack') < 3) {
    const tick = Math.floor(t * 12), cols = ['#3fd0ff', '#5dff9c', '#ff3fd0', '#ffd23f'];
    artGlow(ctx, X(146), Y(470), F.h * 0.14, '#3fd0ff', 0.25 * (1 - d('rack') / 3));
    for (let j = 0; j < 28; j++) {
      if ((j * 73 + tick * 31) % 100 > 55) continue;
      ctx.fillStyle = cols[(j + tick) % 4];
      ctx.fillRect(X(98 + (j % 4) * 28), Y(372 + Math.floor(j / 4) * 31), 9 * s, 4 * s);
    }
  }
  if (d('pc') < 3) {
    const k = Math.min(0.99, d('pc') / 1.4), x = X(690), y = Y(292), w = 90 * s, h = 12 * s;
    ctx.fillStyle = 'rgba(8,10,30,0.85)'; ctx.fillRect(x - 2 * s, y - 2 * s, w + 4 * s, h + 4 * s);
    ctx.fillStyle = '#3fd0ff'; ctx.fillRect(x, y, w * k, h);
    ctx.fillStyle = '#fff'; ctx.font = 'bold ' + Math.max(8, Math.round(11 * s)) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'bottom';
    ctx.fillText(Math.floor(k * 100) + '%', x + w / 2, y - 3 * s); ctx.textAlign = 'start'; ctx.textBaseline = 'alphabetic';
  }
  if (d('star') < 1.4 && AFX.starAt) {
    const h = AFX.starAt, k = d('star') / 1.4, x0 = h.x - h.r * 0.75, y0 = h.y - h.r * 0.8, x1 = h.x + h.r * 0.7, y1 = h.y + h.r * 0.1;
    const hx = x0 + (x1 - x0) * k, hy = y0 + (y1 - y0) * k, tx = hx - (x1 - x0) * 0.35, ty = hy - (y1 - y0) * 0.35;
    ctx.save(); ctx.beginPath(); ctx.rect(X(h.x - h.r), Y(h.y - h.r), 2 * h.r * s, 2 * h.r * s); ctx.clip();
    const gr = ctx.createLinearGradient(X(tx), Y(ty), X(hx), Y(hy)); gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(1, 'rgba(255,255,255,0.95)');
    ctx.strokeStyle = gr; ctx.lineWidth = Math.max(1.5, 4 * s); ctx.lineCap = 'round'; ctx.globalAlpha = 1 - k * 0.6;
    ctx.beginPath(); ctx.moveTo(X(tx), Y(ty)); ctx.lineTo(X(hx), Y(hy)); ctx.stroke(); ctx.restore();
    artGlow(ctx, X(hx), Y(hy), F.h * 0.025, '#ffffff', 0.8 * (1 - k));
  }
  if (d('spark') < 1.6 && AFX.sparkAt) {
    const h = AFX.sparkAt, k = d('spark') / 1.6;
    ctx.fillStyle = '#fff8c0';
    for (let j = 0; j < 7; j++) {
      const a = j * 0.9 + 0.4, z = Math.max(0, Math.sin((k * 3 + j * 0.37) * Math.PI)) * 7 * s * (1 - k * 0.5);
      const x = X(h.x + Math.cos(a) * h.r * 0.85), y = Y(h.y + Math.sin(a) * h.r * 0.85);
      ctx.fillRect(x - z, y - z * 0.2, z * 2, z * 0.4); ctx.fillRect(x - z * 0.2, y - z, z * 0.4, z * 2);
    }
  }
  if (d('drops') < 1.3 && AFX.dropAt) {
    const h = AFX.dropAt;
    ctx.fillStyle = '#7ae0ff';
    for (let j = 0; j < 7; j++) {
      const k = d('drops') / 1.3 - j * 0.06;
      if (k < 0 || k > 1) continue;
      ctx.globalAlpha = 1 - k; ctx.beginPath();
      ctx.ellipse(X(h.x + (j - 3) * h.r * 0.18), Y(h.y - h.r * 0.9 + k * h.r * 1.2), 3 * s, 5 * s, 0, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
  if (d('dust') < 1 && AFX.dustAt) {
    const h = AFX.dustAt, k = d('dust');
    ctx.fillStyle = '#c8c0e0';
    for (let j = 0; j < 6; j++) {
      const a = j * 1.05; ctx.globalAlpha = 0.45 * (1 - k); ctx.beginPath();
      ctx.arc(X(h.x + Math.cos(a) * h.r * 0.5 * (0.4 + k)), Y(h.y + h.r * 0.2 + Math.sin(a) * h.r * 0.18 * (0.4 + k)), (6 + 10 * k) * s, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
}
// a tap on the illustrated office (picture px): people first, then lamps, then things, then the desks
function artOfficeTap(e, px, py) {
  if (Math.hypot(px - ART_CAT[0], py - (ART_CAT[1] - 25)) < 75) return petCat(e);
  const P = artPlaces();
  let best = null;
  for (const k in P) {
    const p = P[k], im = artChar(p.seat ? 'seat' : 'stand', k), h = (p.seat ? ART_H.seat : ART_H.stand) * ART_OF.H;
    const w = h * (im ? im.naturalWidth / im.naturalHeight : p.seat ? 0.8 : 0.45);
    if (Math.abs(px - p.x) < w / 2 + 14 && py < p.y + 18 && py > p.y - h - 14) {
      const dd = Math.hypot(px - p.x, py - (p.y - h / 2));
      if (!best || dd < best.d) best = { k, d: dd };
    }
  }
  if (best) return best.k === 'you' ? onWorkDown(e) : talk(best.k);
  for (let i = 0; i < ART_LAMPS.length; i++) if (Math.hypot(px - ART_LAMPS[i][0], py - ART_LAMPS[i][1]) < 34) return artPoke('lamp', e, { x: ART_LAMPS[i][0], y: ART_LAMPS[i][1], r: 30, i });
  // a desk: its monitor or chair → whoever works there, or "hire someone" when it is free
  for (let i = 0; i < ART_DESKS.length; i++) {
    const D = ART_DESKS[i];
    if (Math.hypot(px - D.mon[0], py - D.mon[1]) > 75 && Math.hypot(px - D.seat[0], py - D.seat[1] + 50) > 70) continue;
    const who = Object.keys(P).find((k) => P[k].desk === i);
    if (who) return talk(who);
    toast(L('of.desk_free')); sfx('blip'); haptic('light');
    UI.sel = ROLES.find((r) => !(S.staff[r].lvl > 0)) || UI.sel;
    return go('studio', 'team');
  }
  // the Mall's things (or the empty spot where they would go)
  const it = S.items || {};
  for (const k in ART_ITEMS) {
    const o = ART_ITEMS[k], cy = o.y - o.h * ART_OF.H / 2, cx = o.x;
    if (Math.hypot(px - cx, py - cy) > o.r) continue;
    if (k === 'lamp') { if (!it.lamp) continue; AFX.lampOff.floor = !AFX.lampOff.floor; return artPoke('flamp', e, { x: cx, y: cy, r: 40 }); }
    if (it[k]) return artPoke(k, e, { x: o.x, y: cy, r: o.r });
    sfx('blip'); haptic('light'); sayAt(o.x / ART_K, (cy - 20) / ART_K, esc(L('of.empty_' + k))); return;
  }
  if (it.rack && Math.hypot(px - 146, py - 467) < 85) return artPoke('rackon', e, { x: 146, y: 467, r: 85 });
  let hit = null;
  for (const h of ART_HOT) { const dd = Math.hypot(px - h.x, py - h.y) / h.r; if (dd < 1 && (!hit || dd < hit.d)) hit = { h, d: dd }; }
  if (hit) return artPoke(hit.h.k, e, hit.h);
}
function artPoke(k, e, h) {
  const t = performance.now() / 1000;
  let line;
  if (k === 'lamp') { const off = (AFX.lampOff[h.i] = !AFX.lampOff[h.i]); line = L(off ? 'of.lamp_off' : 'of.lamp_on'); sfx('blip'); }
  else { line = L('of.' + k + '_' + (1 + Math.floor(Math.random() * (ART_X[k] || 1)))); sfx(k === 'neon' ? 'blip' : 'pop'); }
  if (k === 'neon') { AFX.neon = t; AFX.neonC = (AFX.neonC + 1) % ART_NEON.length; }
  else if (k === 'rack' || k === 'pc') AFX[k] = t;
  else if (k === 'coffee') AFX.steam = t;
  else if (k === 'lounge' || k === 'rackon') { AFX.spark = t; AFX.sparkAt = h; }
  else if (k === 'window') { AFX.star = t; AFX.starAt = h; }
  else if (k === 'art') { AFX.spark = t; AFX.sparkAt = h; }
  else if (k === 'plant') { AFX.drops = t; AFX.dropAt = h; }
  else if (k === 'rug' || k === 'books' || k === 'stairs') { AFX.dust = t; AFX.dustAt = h; }
  haptic('light');
  if (e && e.clientX) floatText(e.clientX, e.clientY - 10, ART_ICO[k] || '✨', 'gold');
  sayAt(h.x / ART_K, (h.sy || h.y - h.r * 0.55) / ART_K, esc(line));
  const f = artFound();
  if (!f.has(k) && ART_SECRETS.includes(k)) {
    f.add(k); lsSet('od2_office_found', JSON.stringify([...f]));
    const n = ART_SECRETS.filter((x) => f.has(x)).length;
    if (n === ART_SECRETS.length) { toast(L('of.found_all'), 'good'); confetti(80); sfx('up'); }
    else toast(L('of.found_new', { n, N: ART_SECRETS.length }), 'good');
  }
}

/* ---------- the Empire tower, drawn to match the street (Art 2) ----------
   The night skyline is cut from the street picture; the tower in front is brick and neon like the office:
   open floors are lit (people pass the windows from ★3), ★2 adds neon trim, ★4 a side sign, ★5 gold;
   the top floor brings searchlights, the whole tower at ★5 a crown. Units: 120×186, drawn ×3. Tap a floor → its card. */
const ART_TW = { k: 3, W: 120, H: 186, x: 30, w: 60, top: 42, fh: 21, lobby: 147, ground: 170 };
function artTowerDraw(cv, g, t) {
  const sky = artImg('street_empty');
  if (!sky) return false;
  const T = ART_TW, K = T.k;
  if (cv.width !== T.W * K) { cv.width = T.W * K; cv.height = T.H * K; }
  const fl = S.floors || {}, lv = S.flv || {}, ids = ['f5', 'f4', 'f3', 'f2', 'f1'];
  const R = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
  g.setTransform(K, 0, 0, K, 0, 0); g.imageSmoothingEnabled = false;
  // the sky and skyline from the street picture (its tallest towers), dimmed so ours stands out
  g.drawImage(sky, 930, 0, 330 * T.W / T.H, 330, 0, 0, T.W, T.H);
  R(0, 0, T.W, T.H, 'rgba(8,6,30,0.38)');
  // the wet street
  R(0, T.ground, T.W, T.H - T.ground, '#120f22'); R(0, T.ground, T.W, 1, '#3a2c52');
  for (let i = 0; i < 9; i++) { g.globalAlpha = 0.25 + 0.15 * Math.sin(t * 2 + i); R((i * 17 + t * 3) % T.W, T.ground + 4 + (i % 3) * 4, 8, 1, i % 2 ? '#3fd0ff' : '#ff3fd0'); }
  g.globalAlpha = 1;
  const allSt = ['b', 'f1', 'f2', 'f3', 'f4', 'f5'].reduce((n, id) => n + (lv[id] || 0), 0), acc = ['#ffd23f', '#3fd0ff', '#ff3fd0', '#5dff9c', '#a06aff'];
  // searchlights once the top floor is open
  if (fl.f5) for (let k = 0; k < 2; k++) {
    const a = Math.sin(t * 0.7 + k * 2.4) * 0.5 + (k ? 0.45 : -0.45), ox = T.x + T.w / 2 + (k ? 8 : -8), oy = T.top - 14;
    const gr = g.createLinearGradient(ox, oy, ox + Math.sin(a) * 90, oy - Math.cos(a) * 90);
    gr.addColorStop(0, k ? 'rgba(63,208,255,0.35)' : 'rgba(255,63,208,0.35)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.beginPath(); g.moveTo(ox, oy); g.lineTo(ox + Math.sin(a - 0.08) * 90, oy - Math.cos(a - 0.08) * 90); g.lineTo(ox + Math.sin(a + 0.08) * 90, oy - Math.cos(a + 0.08) * 90); g.closePath(); g.fill();
  }
  // the side face (depth) and the brick front
  R(T.x + T.w, T.top - 2, 6, T.ground - T.top + 2, '#1c1018'); R(T.x + T.w, T.top - 2, 1, T.ground - T.top + 2, '#2c1a24');
  R(T.x, T.top, T.w, T.ground - T.top, '#3a222c');
  for (let y = T.top; y < T.ground; y += 3) { R(T.x, y, T.w, 1, '#2a1820'); for (let x = T.x + ((y / 3) % 2 ? 2 : 5); x < T.x + T.w; x += 7) R(x, y, 1, 3, '#2a1820'); }
  R(T.x, T.top, 1, T.ground - T.top, '#4c3038');
  ids.forEach((id, r) => {
    const y = T.top + r * T.fh, own = !!fl[id], st = lv[id] || 0, ac = acc[r];
    R(T.x - 1, y + T.fh - 2, T.w + 2, 2, own && st >= 5 ? '#ffd23f' : '#4a3440'); // ledge
    for (let w = 0; w < 3; w++) {
      const wx = T.x + 5 + w * 18, wy = y + 3, ww = 14, wh = 13;
      R(wx - 1, wy - 1, ww + 2, wh + 2, '#140c12');
      if (own) {
        const warm = (r + w) % 3 !== 1, flick = Math.sin(t * 0.8 + r * 3 + w * 5) > 0.97 - st * 0.01;
        const gr = g.createLinearGradient(0, wy, 0, wy + wh);
        gr.addColorStop(0, warm ? '#ffcf8a' : '#7ae8ff'); gr.addColorStop(1, warm ? '#c06a3a' : '#2a6ac0');
        g.fillStyle = gr; g.fillRect(wx, wy, ww, wh);
        if (flick) R(wx, wy, ww, wh, 'rgba(20,10,30,0.5)');
        // a desk and a glowing monitor inside
        R(wx + 2, wy + 9, ww - 4, 1, '#3a2018'); R(wx + 4, wy + 5, 5, 4, '#10182a'); R(wx + 5, wy + 6, 3, 2, (r + w) % 2 ? '#5dff9c' : '#3fd0ff');
        R(wx + ww / 2 - 0.5, wy, 1, wh, 'rgba(20,10,20,0.55)'); R(wx, wy + 6, ww, 1, 'rgba(20,10,20,0.4)');
      } else {
        R(wx, wy, ww, wh, '#141228'); g.globalAlpha = 0.25; R(wx + 2, wy + wh - 4, 3, 1, '#8a90e0'); R(wx + 4, wy + wh - 6, 3, 1, '#8a90e0'); g.globalAlpha = 1;
      }
    }
    if (own && st >= 3) for (let p = 0; p < (st >= 5 ? 2 : 1); p++) {
      const span = T.w - 12, q = (t * (6 + p * 3) + r * 13 + p * 20) % (span * 2), x = T.x + 6 + (q < span ? q : span * 2 - q);
      R(x, y + 8, 3, 6, '#160c18'); R(x + 0.5, y + 6, 2, 2, '#160c18');
    }
    if (own && st >= 2) { g.globalAlpha = 0.6 + 0.4 * Math.sin(t * 2 + r); R(T.x, y + T.fh - 3, T.w, 1, ac); g.globalAlpha = 1; artGlow(g, T.x + T.w / 2, y + T.fh - 3, 20, ac, 0.12); }
    if (own && st >= 4) { const on = Math.floor(t * 2 + r) % 3 !== 0; R(T.x + T.w + 2, y + 4, 2, T.fh - 9, on ? acc[(r + 2) % 5] : '#2a1a2a'); if (on) artGlow(g, T.x + T.w + 3, y + T.fh / 2, 8, acc[(r + 2) % 5], 0.4); }
    for (let i = 0; i < st; i++) R(T.x - 4, y + 2 + i * 3.6, 2, 2, '#ffd23f');
    if (!own) { const lx = T.x + T.w / 2 - 3, ly = y + 6; R(lx, ly + 3, 7, 6, '#8a7aa0'); R(lx + 1, ly, 5, 3, '#8a7aa0'); R(lx + 2, ly + 1, 3, 2, '#141228'); R(lx + 3, ly + 5, 1, 2, '#141228'); }
  });
  // the lobby: warm glass doors under a neon awning
  const ly = T.lobby + 2;
  R(T.x, T.lobby, T.w, T.ground - T.lobby, '#2a1820');
  R(T.x + 6, ly + 6, T.w - 12, T.ground - ly - 6, '#140c12');
  const lg = g.createLinearGradient(0, ly + 7, 0, T.ground); lg.addColorStop(0, '#ffd8a0'); lg.addColorStop(1, '#b0602e');
  g.fillStyle = lg; g.fillRect(T.x + 7, ly + 7, T.w - 14, T.ground - ly - 7);
  R(T.x + T.w / 2 - 0.5, ly + 7, 1, T.ground - ly - 7, '#3a2018');
  g.globalAlpha = 0.75 + 0.25 * Math.sin(t * 3); R(T.x + 3, ly + 2, T.w - 6, 2, '#ff3fd0'); g.globalAlpha = 1;
  artGlow(g, T.x + T.w / 2, ly + 3, 26, '#ff3fd0', 0.22); artGlow(g, T.x + T.w / 2, T.ground, 30, '#ffc070', 0.18);
  // the roof: a neon sign, the antenna with its red light
  R(T.x - 2, T.top - 4, T.w + 4, 4, '#2a1820'); R(T.x - 2, T.top - 4, T.w + 4, 1, fl.f5 ? '#ffd23f' : '#5a4050');
  const sx = T.x + 12, sw = T.w - 24, sy = T.top - 16;
  R(sx, sy, sw, 11, '#140c18'); g.lineWidth = 1; g.strokeStyle = fl.f5 ? '#ff3fd0' : '#6a3a6a'; g.strokeRect(sx + 0.5, sy + 0.5, sw - 1, 10);
  g.fillStyle = fl.f5 ? '#3fd0ff' : '#3a5a7a'; g.font = '6px "Press Start 2P", monospace'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('OD', sx + sw / 2, sy + 6); g.textAlign = 'start'; g.textBaseline = 'alphabetic';
  if (fl.f5) artGlow(g, sx + sw / 2, sy + 5, 22, '#ff3fd0', 0.25 + 0.08 * Math.sin(t * 2.5));
  R(T.x + T.w - 10, T.top - 26, 1, 22, '#8a90a0');
  if (Math.floor(t * 1.4) % 2) { R(T.x + T.w - 11, T.top - 28, 3, 3, '#ff4d6d'); artGlow(g, T.x + T.w - 9.5, T.top - 26.5, 6, '#ff4d6d', 0.6); }
  if (allSt >= 30) {
    // "Tower legend": a golden crown over the sign
    const cx = T.x + T.w / 2, cy = sy - 6;
    R(cx - 7, cy, 15, 3, '#ffd23f'); R(cx - 7, cy - 3, 2, 3, '#ffd23f'); R(cx - 1, cy - 4, 2, 4, '#ffd23f'); R(cx + 6, cy - 3, 2, 3, '#ffd23f');
    artGlow(g, cx, cy, 16, '#ffd23f', 0.35 + 0.15 * Math.sin(t * 3));
  }
  g.setTransform(1, 0, 0, 1, 0, 0);
  return true;
}
// a tap on the tower: the floor under the finger lights up in the list below
function artTowerTap(e) {
  const cv = e.target, r = cv.getBoundingClientRect(), y = (e.clientY - r.top) / r.height * ART_TW.H;
  const row = y >= ART_TW.lobby ? 5 : Math.floor((y - ART_TW.top) / ART_TW.fh);
  if (row < 0 || row > 5) return;
  const el = document.querySelectorAll('.floors .floor')[row];
  sfx('blip'); haptic('light');
  if (!el) return;
  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash');
}
