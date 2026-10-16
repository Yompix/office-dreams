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
const ART_LAMPS = [[386, 370], [1030, 345], [698, 742], [1236, 945], [175, 560]];
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
// who sits where: the player at the front desk, the first three hires at the other desks, the rest stand
function artPlaces() {
  const out = { you: { seat: false, x: ART_HERO[0], y: ART_HERO[1], hero: true } };
  let d = 0, s = 0;
  for (const r of ROLES) {
    if (!S.staff[r] || S.staff[r].lvl <= 0) continue;
    if (d < ART_DESKS.length) { const D = ART_DESKS[d]; out[r] = { seat: true, x: D.seat[0], y: D.seat[1], desk: d, flip: D.flip }; d++; }
    else if (s < ART_STANDS.length) { out[r] = { seat: false, x: ART_STANDS[s][0], y: ART_STANDS[s][1], flip: s % 2 === 1 }; s++; }
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
  artGlow(ctx, X(ART_SIGN[0]), Y(ART_SIGN[1]), F.h * 0.16, Math.sin(t * 1.3) > 0 ? '#ff3fd0' : '#3fd0ff', 0.12 + 0.06 * Math.sin(t * 3));
  ART_LAMPS.forEach((l, i) => artGlow(ctx, X(l[0]), Y(l[1]), F.h * 0.07, '#ffc070', 0.16 + 0.04 * Math.sin(t * 5 + i * 1.7)));
  ART_DESKS.forEach((d, i) => artGlow(ctx, X(d.mon[0]), Y(d.mon[1]), F.h * 0.1, '#3fd0ff', (working ? 0.16 : 0.07) + (working ? 0.06 * Math.sin(t * 9 + i * 2) : 0)));
  if (st.turbo || st.hype) artGlow(ctx, F.x + F.w / 2, F.y + F.h / 2, F.h * 0.7, st.turbo ? '#ff4d6d' : '#ffd23f', 0.06 + 0.04 * Math.sin(t * 6));
  // the team, back to front
  const places = artPlaces(), list = Object.keys(places).map((k) => Object.assign({ k }, places[k])).sort((a, b) => a.y - b.y);
  const blit = (spr, x, y, hFrac, flip) => {
    const sc = (hFrac * F.h) / spr.height, w = spr.width * sc, h = spr.height * sc;
    ctx.save(); ctx.imageSmoothingEnabled = false; ctx.translate(Math.round(X(x)), Math.round(Y(y))); if (flip) ctx.scale(-1, 1);
    ctx.drawImage(spr, -w / 2, -h, w, h); ctx.restore();
  };
  // an AI-drawn character: soft shadow, then the picture; seated people type (quick bob), standing ones breathe
  const person = (im, x, y, hFrac, flip, bob, sx) => {
    const h = hFrac * F.h * (1 + (sx || 0)), w = h * im.naturalWidth / im.naturalHeight;
    ctx.save(); ctx.globalAlpha = 0.35; ctx.fillStyle = '#000'; ctx.beginPath(); ctx.ellipse(X(x), Y(y), w * 0.42, h * 0.05, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore();
    ctx.save(); ctx.imageSmoothingEnabled = true; ctx.translate(X(x), Y(y) + bob); if (flip) ctx.scale(-1, 1);
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
      const breathe = Math.sin(t * 1.8 + p.x) * 0.006;
      const tap = p.hero && st.tapFlash > 0 ? -F.h * 0.006 : 0;
      if (im) person(im, p.x, p.y, ART_H.stand, p.flip, tap, breathe);
      else blit(sprStand(p.k, 0, true), p.x, p.y, 0.15, p.flip);
    }
  }
  // the cat on the beanbag
  const petted = t - (UI.catPetT || -9) < 2.5, cim = artImg(petted ? 'cat_sit' : 'cat_sleep');
  if (cim) person(cim, ART_CAT[0], ART_CAT[1] + 6, petted ? 0.075 : 0.05, false, petted ? 0 : Math.sin(t * 1.2) * F.h * 0.0012);
  else blit(sprCat(false), ART_CAT[0], ART_CAT[1], 0.045, Math.sin(t * 0.3) > 0.7);
}
