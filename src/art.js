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
// the desks' outline in the picture (with monitors and lamps) and the floor line in front of them: someone standing
// behind a desk (feet above zy) is drawn first, then the desk is laid over again, so they stand behind it, not on it
const ART_FRONT = [
  { d: 0, zy: 510, poly: [[475, 445], [500, 385], [560, 355], [610, 325], [655, 325], [670, 340], [700, 330], [760, 330], [785, 375], [790, 470], [720, 525], [560, 585], [470, 565]] },
  { d: 1, zy: 595, poly: [[1000, 465], [1070, 420], [1080, 385], [1120, 375], [1135, 400], [1225, 420], [1285, 440], [1305, 470], [1305, 545], [1290, 610], [1240, 625], [1020, 565], [995, 540]] },
  { d: 2, zy: 800, poly: [[430, 690], [470, 635], [520, 615], [615, 640], [700, 670], [705, 700], [745, 700], [790, 775], [790, 860], [700, 885], [430, 885]] },
  { d: 3, zy: 880, poly: [[925, 850], [1000, 770], [1005, 728], [1060, 700], [1170, 678], [1220, 688], [1245, 700], [1285, 750], [1285, 820], [1200, 905], [1160, 965], [925, 965]] },
];
const ART_HERO = [842, 700]; // the boss stands in the middle of the room
const ART_STANDS = [[1010, 690], [262, 690], [1320, 700], [905, 930], [805, 615]];
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
// ---------- the team's life: people standing around go about their day ----------
// they walk (around the desks) to the coffee machine, the cat, the window, the books, the sofa, or to each other for a chat,
// stay a while with a little emote over the head, then go back to their spot.
// floor the feet can't cross (desks, beanbag, server rack, shelves, the stairs), picture px [x0, y0, x1, y1]
const ART_BLOCK = [[470, 400, 800, 570], [990, 440, 1310, 630], [430, 670, 790, 830], [920, 720, 1290, 960], [325, 525, 495, 620], [40, 320, 250, 640], [850, 300, 1080, 450], [0, 700, 560, 1088], [1150, 880, 1456, 1088], [1370, 600, 1456, 880]];
// open floor to walk through when the straight way is blocked
const ART_HUBS = [[460, 645], [820, 600], [900, 700], [870, 880], [300, 660], [1330, 680], [850, 470], [1100, 680]];
// places to go: where to stand, which way to look (1 = right), the emote, how long, what has to be bought first
const ART_SPOTS = {
  coffee: { p: [548, 600], face: -1, emo: '☕', dur: 4.5, need: 'coffee' },
  cat: { p: [298, 650], face: 1, emo: '😻', dur: 4 },
  window: { p: [1345, 560], face: 1, emo: '🌃', dur: 5 },
  books: { p: [930, 470], face: -1, emo: '📖', dur: 5 },
  plant: { p: [1340, 770], face: 1, emo: '💧', dur: 3.5 },
  rack: { p: [275, 600], face: -1, emo: '🛠️', dur: 4 },
  rug: { p: [960, 650], face: 1, emo: '🙆', dur: 3, stretch: true },
  sofa: { p: [900, 500], face: 1, emo: '😌', dur: 8, need: 'lounge', sit: true },
};
const ART_LIFE = { t: 0, a: {}, hop: {} };
const ART_SPEED = 115; // picture px per second
function artClear(a, b) {
  const n = Math.max(2, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / 12));
  for (let i = 1; i < n; i++) {
    const x = a[0] + (b[0] - a[0]) * i / n, y = a[1] + (b[1] - a[1]) * i / n;
    if (ART_BLOCK.some((r) => x > r[0] + 3 && x < r[2] - 3 && y > r[1] + 3 && y < r[3] - 3)) return false;
  }
  return true;
}
// the shortest way from a to b through the open-floor points (a tiny Dijkstra; a handful of nodes)
function artPath(a, b) {
  if (artClear(a, b)) return [b];
  const N = [a, ...ART_HUBS, b], dist = N.map(() => Infinity), prev = N.map(() => -1), done = N.map(() => false);
  dist[0] = 0;
  for (;;) {
    let u = -1;
    for (let i = 0; i < N.length; i++) if (!done[i] && dist[i] < Infinity && (u < 0 || dist[i] < dist[u])) u = i;
    if (u < 0 || u === N.length - 1) break;
    done[u] = true;
    for (let v = 0; v < N.length; v++) {
      if (done[v] || v === u || !artClear(N[u], N[v])) continue;
      const d = dist[u] + Math.hypot(N[v][0] - N[u][0], N[v][1] - N[u][1]);
      if (d < dist[v]) { dist[v] = d; prev[v] = u; }
    }
  }
  if (prev[N.length - 1] < 0) return [b];
  const out = [];
  for (let v = N.length - 1; v > 0; v = prev[v]) out.unshift(N[v]);
  return out;
}
function artGoTo(a, p, then) { a.path = artPath([a.x, a.y], p); a.mode = 'walk'; a.then = then; }
function artEmote(a, emo, t, dur) { a.emo = emo; a.emoT = t; a.emoD = dur || 3; }
// one step of everyone's day
function artLifeStep(t, keys) {
  const L0 = ART_LIFE, dt = Math.min(0.1, Math.max(0, t - L0.t));
  if (dt === 0 && L0.keys === keys.join()) return;
  L0.t = t; L0.keys = keys.join();
  for (const k in L0.a) if (!keys.includes(k)) delete L0.a[k];
  keys.forEach((k, i) => {
    if (!L0.a[k]) { const H = ART_STANDS[i]; L0.a[k] = { k, x: H[0], y: H[1], home: i, mode: 'idle', until: t + 2 + Math.random() * 6, flip: i % 2 === 1, path: [], look: t + 3 }; }
    L0.a[k].home = i;
  });
  const it = (S && S.items) || {}, all = Object.values(L0.a);
  for (const a of all) {
    if (a.stopT && t < a.stopT) continue;
    const H = ART_STANDS[a.home];
    if (a.mode === 'walk') {
      let step = ART_SPEED * dt;
      while (step > 0 && a.path.length) {
        const p = a.path[0], dx = p[0] - a.x, dy = p[1] - a.y, d = Math.hypot(dx, dy);
        if (Math.abs(dx) > 2) a.flip = dx > 0;
        if (d <= step) { a.x = p[0]; a.y = p[1]; a.path.shift(); step -= d; } else { a.x += dx / d * step; a.y += dy / d * step; step = 0; }
      }
      a.ph = (a.ph || 0) + dt * 1.6;
      if (!a.path.length) {
        const th = a.then; a.then = null;
        if (th && th.spot) {
          const sp = ART_SPOTS[th.spot];
          a.mode = 'act'; a.act = th.spot; a.until = t + sp.dur; a.flip = sp.face > 0; artEmote(a, sp.emo, t, sp.dur);
          if (th.spot === 'coffee') AFX.steam = t;
          else if (th.spot === 'cat') UI.catPetT = t + 0.3;
          else if (th.spot === 'plant') { AFX.drops = t; AFX.dropAt = { x: 1390, y: 770, r: 70 }; }
          else if (th.spot === 'books') { AFX.dust = t; AFX.dustAt = { x: 950, y: 380, r: 60 }; }
        } else if (th && th.chat) {
          const b = L0.a[th.chat];
          if (b && b.mode === 'wait') { a.mode = 'act'; a.act = 'chat'; a.with = b.k; a.until = t + 5; a.flip = b.x > a.x; b.flip = a.x > b.x; artEmote(a, '💬', t, 2.4); b.emoAt = t + 2.5; }
          else { a.mode = 'idle'; a.until = t + 2; }
        } else { a.mode = 'idle'; a.until = t + 6 + Math.random() * 9; a.flip = a.home % 2 === 1; }
      }
    } else if (a.mode === 'act') {
      if (a.act === 'chat') { const b = L0.a[a.with]; if (b && b.emoAt && t > b.emoAt) { artEmote(b, ['😂', '👍', '💡', '😮'][Math.floor(Math.random() * 4)], t, 2.3); b.emoAt = 0; } }
      if (t > a.until) {
        if (a.act === 'chat') { const b = L0.a[a.with]; if (b) { b.mode = 'idle'; b.until = t + 3 + Math.random() * 5; } }
        a.act = null; a.with = null; artGoTo(a, H, null);
      }
    } else if (a.mode === 'wait') {
      if (t > a.waitUntil) { a.mode = 'idle'; a.until = t + 3; }
    } else if (a.mode === 'idle') {
      // standing about: look around now and then
      if (t > a.look) { a.flip = !a.flip; a.look = t + 2.5 + Math.random() * 4; }
      if (t > a.until) {
        const busy = new Set(all.map((x) => x.act || (x.then && x.then.spot)).filter(Boolean));
        const opts = Object.keys(ART_SPOTS).filter((k) => !busy.has(k) && (!ART_SPOTS[k].need || it[ART_SPOTS[k].need]));
        const mates = all.filter((x) => x !== a && x.mode === 'idle');
        if (mates.length && Math.random() < 0.35) {
          const b = mates[Math.floor(Math.random() * mates.length)];
          b.mode = 'wait'; b.emo = null; b.waitUntil = t + 15;
          const side = b.x > 700 ? -1 : 1, p = [b.x + side * 85, b.y + 6];
          if (ART_BLOCK.some((r) => p[0] > r[0] && p[0] < r[2] && p[1] > r[1] && p[1] < r[3])) p[0] = b.x - side * 85;
          artGoTo(a, p, { chat: b.k });
        } else if (opts.length) {
          const k = opts[Math.floor(Math.random() * opts.length)];
          artGoTo(a, ART_SPOTS[k].p, { spot: k });
        } else a.until = t + 4;
      }
    }
  }
}
// who sits where: the first four hires at the desks, the rest stand (and live); the boss stands in the middle
function artPlaces() {
  const t = performance.now() / 1000;
  const out = { you: { seat: false, x: ART_HERO[0], y: ART_HERO[1], hero: true, flip: Math.sin(t * 0.21) > 0.55 } };
  let d = 0;
  const stand = [];
  for (const r of ROLES) {
    if (!S.staff[r] || S.staff[r].lvl <= 0) continue;
    if (d < ART_DESKS.length) { const D = ART_DESKS[d]; out[r] = { seat: true, x: D.seat[0], y: D.seat[1], desk: d, flip: D.flip }; d++; }
    else if (stand.length < ART_STANDS.length) stand.push(r);
  }
  artLifeStep(t, stand);
  for (const r of stand) {
    const a = ART_LIFE.a[r];
    out[r] = { seat: false, x: a.x, y: a.y, walk: a.mode === 'walk' && !(a.stopT > t), flip: a.flip, ph: a.ph || 0, sit: a.act === 'sofa' && a.mode === 'act', stretch: a.act === 'rug' && a.mode === 'act', life: a };
  }
  return out;
}
// the office's light at a picture point: the strongest nearby lamp, monitor, neon or window, and which side it is on
function artLightAt(x, y) {
  let best = null;
  const add = (lx, ly, col, k) => { const s = k / (Math.hypot(lx - x, (ly - y) * 1.3) + 60); if (!best || s > best.s) best = { s, lx, col }; };
  ART_LAMPS.forEach((l, i) => { if (!AFX.lampOff[i]) add(l[0], l[1], '#ffb860', 1); });
  ART_DESKS.forEach((d) => add(d.mon[0], d.mon[1], '#48d8ff', 1.25));
  add(ART_SIGN[0], ART_SIGN[1], ART_NEON[AFX.neonC][0], 1.6);
  add(1020, 230, '#8aa0ff', 0.9); add(1360, 360, '#8aa0ff', 0.9);
  if (S && S.items && S.items.lamp && !AFX.lampOff.floor) add(ART_ITEMS.lamp.x - 60, ART_ITEMS.lamp.y - 190, '#ffb860', 1.6);
  return { side: best.lx < x ? -1 : 1, col: best.col };
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
  artAmbient(ctx, F, s, X, Y, t);
  artOfficeFx(ctx, F, s, X, Y, t);
  // the team, back to front
  const places = artPlaces(), list = Object.keys(places).map((k) => Object.assign({ k }, places[k])).sort((a, b) => a.y - b.y);
  const blit = (spr, x, y, hFrac, flip) => {
    const sc = (hFrac * F.h) / spr.height, w = spr.width * sc, h = spr.height * sc;
    ctx.save(); ctx.imageSmoothingEnabled = false; ctx.translate(Math.round(X(x)), Math.round(Y(y))); if (flip) ctx.scale(-1, 1);
    ctx.drawImage(spr, -w / 2, -h, w, h); ctx.restore();
  };
  // an AI-drawn character in the room's light (artLit: night tone + a rim of the nearest light), held down by its shadow.
  // o: { flip, bob, sx (stretch), sy (squash), tilt, walk (phase), top (draw only the top part: sitting on the sofa) }
  const person = (im, x, y, hFrac, o) => {
    o = o || {};
    const L1 = artLightAt(x, y - hFrac * ART_OF.H * 0.6);
    im = artLit(im, o.flip ? -L1.side : L1.side, L1.col);
    if (o.walk != null) im = artWalk(im, o.walk);
    const h = hFrac * F.h * (1 + (o.sx || 0)), w = h * (im.naturalWidth || im.width) / (im.naturalHeight || im.height) * (1 + (o.sy || 0));
    if (!o.top) artFeet(ctx, X(x), Y(y), w, h * (o.air ? 0.7 : 1));
    ctx.save(); ctx.imageSmoothingEnabled = true; ctx.translate(X(x), Y(y) + (o.bob || 0)); if (o.tilt) ctx.rotate(o.tilt); if (o.flip) ctx.scale(-1, 1);
    if (o.top) { const sh = (im.naturalHeight || im.height) * o.top; ctx.drawImage(im, 0, 0, im.naturalWidth || im.width, sh, -w / 2, -h * o.top, w, h * o.top); }
    else ctx.drawImage(im, -w / 2, -h, w, h);
    ctx.restore();
  };
  // a tap makes someone hop (a quick squash before and after)
  const hop = (k) => {
    const d = t - (ART_LIFE.hop[k] || -9);
    if (d > 0.55) return { bob: 0, sy: 0, air: false };
    const q = d / 0.55, up = Math.sin(Math.min(1, Math.max(0, (q - 0.12) / 0.76)) * Math.PI);
    return { bob: -up * F.h * 0.03, sy: q < 0.12 || q > 0.88 ? 0.08 : -0.03, air: up > 0.1 };
  };
  // a desk laid over again (only when someone is behind it), with its monitors' glow
  const fronts = ART_FRONT.map((f) => ({ f, behind: false, done: false }));
  const front = (o) => {
    o.done = true;
    if (!o.behind) return;
    ctx.save(); ctx.beginPath(); o.f.poly.forEach((q, i) => (i ? ctx.lineTo(X(q[0]), Y(q[1])) : ctx.moveTo(X(q[0]), Y(q[1])))); ctx.closePath(); ctx.clip();
    ctx.drawImage(im, F.x, F.y, F.w, F.h);
    const d = ART_DESKS[o.f.d]; artGlow(ctx, X(d.mon[0]), Y(d.mon[1]), F.h * 0.1, '#3fd0ff', (working ? 0.16 : 0.07) * mon2);
    ctx.restore();
  };
  for (const p of list) {
    for (const o of fronts) {
      if (o.done) continue;
      if (p.y >= o.f.zy) front(o);
      else if (!p.seat && p.y > Math.min(...o.f.poly.map((q) => q[1])) && p.x > Math.min(...o.f.poly.map((q) => q[0])) - 40 && p.x < Math.max(...o.f.poly.map((q) => q[0])) + 40) o.behind = true;
    }
    const im = artChar(p.seat ? 'seat' : 'stand', p.k), hp = hop(p.k);
    if (p.seat) {
      const typing = working && Math.sin(t * 1.3 + p.desk * 2) > -0.4;
      // now and then someone at a desk leans back and stretches
      const sp = (t + p.desk * 7.3) % (19 + p.desk * 4), stretch = !typing && sp < 1.6 ? Math.sin(sp / 1.6 * Math.PI) : 0;
      const bob = typing ? (Math.floor(t * 9 + p.desk) % 2) * F.h * 0.002 : Math.sin(t * 1.6 + p.desk) * F.h * 0.0015;
      if (im) person(im, p.x, p.y, ART_H.seat, { flip: p.flip, bob: bob + hp.bob * 0.5, sx: stretch * 0.05, sy: hp.sy, tilt: stretch * (p.flip ? 0.05 : -0.05) });
      else blit(sprSeat(p.k, 0), p.x, p.y, 0.1);
    } else {
      const breathe = p.walk ? 0 : Math.sin(t * 1.8 + p.k.charCodeAt(0)) * 0.006;
      const tap = p.hero && st.tapFlash > 0 ? -F.h * 0.006 : 0;
      // idle weight shift: a slow sway from the feet
      const sway = p.walk || p.sit ? 0 : Math.sin(t * 0.9 + p.k.length) * 0.012;
      const str = p.stretch ? Math.max(0, Math.sin((t % 1.5) / 1.5 * Math.PI)) * 0.05 : 0;
      if (im && p.sit) person(im, p.x, p.y - ART_H.stand * ART_OF.H * 0.02, ART_H.stand, { flip: p.flip, top: 0.6, bob: hp.bob + Math.sin(t * 1.2) * F.h * 0.001 });
      else if (im) person(im, p.x, p.y, ART_H.stand, { flip: p.flip, bob: tap + hp.bob, sx: breathe + str, sy: hp.sy, tilt: sway, walk: p.walk ? p.ph : null, air: hp.air });
      else blit(sprStand(p.k, 0, true), p.x, p.y, 0.15, p.flip);
    }
  }
  fronts.forEach((o) => { if (!o.done) front(o); });
  // little emotes over people's heads (coffee, the cat, a chat...)
  for (const p of list) {
    const a = p.life;
    if (!a || !a.emo || t - a.emoT > a.emoD) continue;
    const k = t - a.emoT, pop = Math.min(1, k / 0.18) * (k > a.emoD - 0.25 ? (a.emoD - k) / 0.25 : 1), r = F.h * 0.028 * pop;
    if (r <= 1) continue;
    const bx = X(p.x) + (p.flip ? 1 : -1) * F.h * 0.02, by = Y(p.y) - ART_H.stand * F.h * (p.sit ? 0.62 : 1.06) + Math.sin(t * 3) * F.h * 0.003;
    ctx.save(); ctx.fillStyle = 'rgba(16,12,36,0.88)'; ctx.strokeStyle = 'rgba(255,255,255,0.55)'; ctx.lineWidth = Math.max(1, F.h * 0.003);
    ctx.beginPath(); ctx.arc(bx, by, r, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(bx - r * 0.3, by + r * 0.8); ctx.lineTo(bx, by + r * 1.35); ctx.lineTo(bx + r * 0.25, by + r * 0.85); ctx.fill();
    ctx.font = Math.round(r * 1.3) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(a.emo, bx, by + r * 0.05);
    ctx.restore();
  }
  // the cat on the beanbag
  const petted = t - (UI.catPetT || -9) < 2.5, cim = artImg(petted ? 'cat_sit' : 'cat_sleep');
  if (cim) person(cim, ART_CAT[0], ART_CAT[1] + 6, petted ? 0.075 : 0.05, { bob: petted ? 0 : Math.sin(t * 1.2) * F.h * 0.0012 });
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
// the room breathes: dust floating in the lamps' light, the city behind the windows (lights going on and off, a plane)
const ART_WIN = [[[918, 45], [1125, 95], [1125, 300], [918, 290]], [[1255, 180], [1456, 215], [1456, 500], [1258, 455]]];
let ART_CITYL = null;
function artAmbient(ctx, F, s, X, Y, t) {
  if (!ART_CITYL) {
    // fixed city lights inside the window panes (a tiny seeded random, the same every time)
    let q = 7;
    const rnd = () => ((q = (q * 16807) % 2147483647) / 2147483647);
    ART_CITYL = [];
    ART_WIN.forEach((w) => { for (let i = 0; i < 26; i++) { const u = rnd(), v = 0.35 + rnd() * 0.6, top = w[0][1] + (w[1][1] - w[0][1]) * u, bot = w[3][1] + (w[2][1] - w[3][1]) * u; ART_CITYL.push({ x: w[0][0] + (w[1][0] - w[0][0]) * u, y: top + (bot - top) * v, p: 3 + rnd() * 9, o: rnd() * 20, c: rnd() < 0.7 ? '#ffd890' : '#9fe0ff' }); } });
  }
  ctx.save(); ctx.globalCompositeOperation = 'lighter';
  for (const l of ART_CITYL) {
    const ph = (t + l.o) % l.p;
    if (ph < 0.8) continue; // this window just went dark for a moment
    ctx.globalAlpha = 0.45 + 0.25 * Math.sin(t * 2 + l.o); ctx.fillStyle = l.c; ctx.fillRect(X(l.x), Y(l.y), Math.max(1, 3 * s), Math.max(1, 3 * s));
  }
  // a plane crossing the left window now and then, its lights blinking
  const pk = (t % 34) / 9;
  if (pk < 1) {
    const w = ART_WIN[0], x = w[0][0] + 10 + (w[1][0] - w[0][0] - 20) * pk, y = w[0][1] + 55 + (w[1][1] - w[0][1]) * pk - pk * 20;
    ctx.globalAlpha = Math.sin(t * 9) > 0.3 ? 0.95 : 0.15; ctx.fillStyle = '#ff4d6d'; ctx.fillRect(X(x), Y(y), Math.max(1.5, 4 * s), Math.max(1.5, 4 * s));
    ctx.globalAlpha = Math.sin(t * 9 + 2) > 0.6 ? 0.9 : 0.1; ctx.fillStyle = '#ffffff'; ctx.fillRect(X(x - 14), Y(y + 1), Math.max(1.5, 3 * s), Math.max(1.5, 3 * s));
  }
  // dust in the lamps' light
  ctx.fillStyle = '#ffe2a8';
  ART_LAMPS.slice(0, 6).forEach((lp, i) => {
    if (AFX.lampOff[i]) return;
    for (let j = 0; j < 5; j++) {
      const k = i * 5 + j, a = t * (0.15 + (k % 3) * 0.05) + k * 1.7;
      const x = lp[0] + Math.sin(a) * 34 + Math.sin(a * 2.3 + k) * 10, y = lp[1] + 25 + Math.cos(a * 0.8 + k) * 30;
      ctx.globalAlpha = 0.25 + 0.3 * Math.max(0, Math.sin(t * 1.3 + k * 2.1));
      ctx.fillRect(X(x), Y(y), Math.max(1, 2.2 * s), Math.max(1, 2.2 * s));
    }
  });
  ctx.restore();
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
  if (best) {
    // a tap: they hop, stop for a moment and turn to you, with an emote
    const t = performance.now() / 1000, a = ART_LIFE.a[best.k];
    if (best.k !== 'you' && t - (ART_LIFE.hop[best.k] || -9) > 0.5) ART_LIFE.hop[best.k] = t;
    if (a) { a.stopT = t + 1.8; a.flip = a.x < ART_HERO[0]; artEmote(a, ['😄', '👋', '❗', '🙌', '😅'][Math.floor(Math.random() * 5)], t, 1.8); }
    return best.k === 'you' ? onWorkDown(e) : talk(best.k);
  }
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
