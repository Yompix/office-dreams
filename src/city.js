/* ============================================================
   OFFICE DREAMS — the city outside the office.
   A side-scrolling neon street: the player walks (tap a building) to
   the coffee shop, the academy, the contract board and the game store.
   Every place does something real for the studio, on game-time timers
   (engine: CITY / coffeeBreak / startTraining / takeContract / storePromo).
   ============================================================ */
const CITY_H = 120; // logical pixels; the canvas is scaled up crisp
const CITY_W = 640; // world width
const CITY_B = [
  { id: 'office', x: 4, w: 70, h: 94, wall: '#181c58', trim: '#2b3290', sign: 'OFFICE', neon: '#3fd0ff' },
  { id: 'cafe', x: 90, w: 64, h: 62, wall: '#3a1d48', trim: '#5a2e6a', sign: 'CAFE', neon: '#ffb03f' },
  { id: 'academy', x: 170, w: 82, h: 86, wall: '#16384a', trim: '#22566e', sign: 'ACADEMY', neon: '#2ee66b' },
  { id: 'deals', x: 268, w: 66, h: 72, wall: '#3a2a18', trim: '#5a4226', sign: 'DEALS', neon: '#ffd23f' },
  { id: 'games', x: 350, w: 78, h: 66, wall: '#3e1638', trim: '#5e2456', sign: 'GAMES', neon: '#ff3fd0' },
  { id: 'mall', x: 444, w: 84, h: 78, wall: '#1d2350', trim: '#2e3880', sign: 'MALL', neon: '#22e6ff' },
  { id: 'arena', x: 544, w: 92, h: 90, wall: '#2a0f2e', trim: '#4a1a52', sign: 'ARENA', neon: '#ff4d6d' },
];
const CITY_SIDEWALK = 98;
const CT = { x: 39, tx: null, dir: 1, walk: 0, cam: 0, sel: null, inside: null, say: '', sayN: 0, t: 0, npcs: null, cars: null, sky: null, last: 0, street: false, catT: -9, steamT: -9, waveT: -9, flickT: -9, found: null };
// little secrets to discover: tapping things on the street and inside the buildings (cosmetic, just for fun)
const CITY_SECRETS = ['st.cat', 'st.kiosk', 'st.npc', 'st.car', 'st.moon', 'cafe.menu', 'cafe.machine', 'cafe.cup', 'cafe.cat', 'academy.board', 'academy.shelf', 'academy.desk',
  'deals.board', 'deals.window', 'deals.laptop', 'games.shelf', 'mall.items', 'arena.screen', 'arena.crowd'];
// how many variants each line has (keys city.x_<id>_<n>)
const CITY_X = { 'st.cat': 3, 'st.kiosk': 3, 'st.npc': 4, 'st.car': 2, 'st.moon': 1, 'cafe.menu': 2, 'cafe.machine': 2, 'cafe.cup': 1, 'cafe.cat': 2, 'academy.board': 3, 'academy.shelf': 3, 'academy.desk': 1,
  'deals.board': 3, 'deals.window': 1, 'deals.laptop': 1, 'arena.screen': 2, 'arena.crowd': 2 };
const CITY_CAT = 160, CITY_KIOSK = 338;
function cityFound() {
  if (!CT.found) { try { CT.found = new Set(JSON.parse(lsGet('od2_city_found') || '[]')); } catch (e) { CT.found = new Set(); } }
  return CT.found;
}
// where today's shiny find lies on the sidewalk (moves after every pick-up)
function cityFindX() { const n = cityS().fndN || 0; return 96 + ((n * 197 + 53) % 520); }
const cityFindReady = () => S && S.t >= (cityS().fnd || 0);

/* ---------- state helpers (engine city state, game-time seconds) ---------- */
function cityS() { return (S && S.city) || { cof: 0, promo: 0, trn: null, ctr: null, offer: null, ctrNext: 0, done: 0 }; }
const cityLeft = (until) => Math.max(0, Math.ceil(until - S.t));
function mmss(sec) { sec = Math.max(0, Math.ceil(sec)); const m = Math.floor(sec / 60), s = sec % 60; return m + ':' + String(s).padStart(2, '0'); }
function trainable() { return ROLES.filter((r) => S.staff[r].lvl > 0 && S.staff[r].lvl < MAX_LVL); }
// what is waiting for the player in the city (drives the office banner and the "!" over doors)
function cityReady() {
  const c = cityS();
  return {
    cafe: S.t >= c.cof,
    academy: !c.trn && trainable().some((r) => S.cash >= CITY.trnCost(r, S.staff[r].lvl)),
    deals: !!c.offer,
    games: S.t >= c.promo && S.games.some((g) => g.active) && S.cash >= promoCost(S),
    mall: ITEMS.some((it) => !S.items[it.id] && S.cash >= it.price),
    arena: lsGet('od2_pv_day') !== String(Math.floor(Date.now() / 86400000)),
  };
}
function citySig() { const r = cityReady(), c = cityS(); return Object.keys(r).map((k) => (r[k] ? 1 : 0)).join('') + (c.trn ? 't' : '') + (c.ctr ? 'c' : '') + (CT.inside || '') + CT.sayN; }

/* ---------- drawing ---------- */
function cityRand(seed) { let s = seed; return () => (s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff; }
function cityInit() {
  if (CT.sky) return;
  const r = cityRand(42);
  CT.sky = [];
  for (let i = 0; i < 40; i++) CT.sky.push([Math.floor(r() * CITY_W), Math.floor(r() * 50), r()]);
  CT.far = [];
  let x = -10;
  while (x < CITY_W + 40) { const w = 10 + Math.floor(r() * 18), h = 30 + Math.floor(r() * 46); CT.far.push([x, w, h, Math.floor(r() * 1e6)]); x += w + 2; }
  CT.npcs = [0, 1, 2].map((i) => ({ x: 60 + i * 140, v: (i % 2 ? -1 : 1) * (8 + i * 3), look: i, pause: 0 }));
  CT.cars = [0, 1].map((i) => ({ x: i * 260, v: i ? -46 : 38, col: ['#ff4d6d', '#3fd0ff'][i] }));
}
function px(g, x, y, w, h, c) { g.fillStyle = c; g.fillRect(x, y, w, h); }
function signText(g, str, x, y, col) {
  g.fillStyle = col;
  eachGlyphPixel(str, (gx, gy) => g.fillRect(x + gx, y + gy, 1, 1));
}
const CITY_LOOKS = [
  { skin: '#8a5a3c', hair: '#1a1018', top: '#2a4bd0', pants: '#1a1f3a', acc: '#3fd0ff' }, // the player: blue hoodie + headphones
  { skin: '#f0c9a0', hair: '#c96a2a', top: '#d04a8a', pants: '#2a2a4a', acc: null },
  { skin: '#c99a70', hair: '#2a1a10', top: '#2e9a5a', pants: '#3a2a1a', acc: null },
  { skin: '#e8b890', hair: '#e8e0d0', top: '#7a4ad0', pants: '#1a1a2a', acc: null },
  { skin: '#d8a880', hair: '#3a2a1a', top: '#2a2c44', pants: '#14141e', acc: null }, // a client in a suit
];
// a person at double size (inside buildings)
function cityPerson2(g, x, y, look, frame, dir, still) { g.save(); g.translate(Math.round(x), Math.round(y)); g.scale(2, 2); cityPerson(g, 0, 0, look, frame, dir, still); g.restore(); }
// a 9×20 pixel person standing on (x, y); frame 0..3 walk cycle, dir ±1
function cityPerson(g, x, y, look, frame, dir, still) {
  const L0 = CITY_LOOKS[look];
  x = Math.round(x); y = Math.round(y);
  const st = still ? 0 : [0, 1, 0, -1][frame & 3];
  px(g, x - 4, y, 9, 1, 'rgba(0,0,0,.35)'); // shadow
  // legs + shoes
  px(g, x - 2 + st, y - 6, 2, 5, L0.pants); px(g, x + 1 - st, y - 6, 2, 5, L0.pants);
  px(g, x - 2 + st, y - 1, 2 + (dir > 0 ? 1 : 0), 1, '#e8ecff'); px(g, x + 1 - st - (dir < 0 ? 1 : 0), y - 1, 2 + (dir < 0 ? 1 : 0), 1, '#e8ecff');
  // body + arms
  px(g, x - 3, y - 13, 7, 7, L0.top);
  px(g, x - 3, y - 7, 7, 1, 'rgba(0,0,0,.25)');
  px(g, x - 4, y - 12 + (still ? 0 : st), 1, 5, L0.top); px(g, x + 4, y - 12 - (still ? 0 : st), 1, 5, L0.top);
  px(g, x - 4, y - 7 + (still ? 0 : st), 1, 1, L0.skin); px(g, x + 4, y - 7 - (still ? 0 : st), 1, 1, L0.skin);
  // head
  px(g, x - 2, y - 19, 5, 6, L0.skin);
  px(g, x - 2, y - 20, 5, 2, L0.hair); px(g, x + (dir > 0 ? -2 : 2), y - 18, 1, 2, L0.hair);
  px(g, x + (dir > 0 ? 1 : -1), y - 17, 1, 1, '#0a0a18'); // eye
  if (L0.acc) { px(g, x - 3, y - 18, 1, 3, L0.acc); px(g, x + 3, y - 18, 1, 3, L0.acc); px(g, x - 2, y - 21, 5, 1, L0.acc); }
}
function cityDraw(now) {
  const cv = UI.cityCv, g = UI.cityCtx;
  if (!cv || !g || !S) return;
  if (artOn() && artDraw(now)) return;
  cityInit();
  const cw = cv.clientWidth || 360, ch = cv.clientHeight || 240;
  const VW = Math.max(120, Math.min(CITY_W, Math.round(CITY_H * cw / ch)));
  if (cv.width !== VW || cv.height !== CITY_H) { cv.width = VW; cv.height = CITY_H; }
  const t = now / 1000, dt = Math.min(0.1, Math.max(0, t - (CT.last || t)));
  CT.last = t; CT.t += dt;
  if (CT.inside) { cityInterior(g, VW, t); return; }
  CT.F = null;
  // walking
  if (CT.tx !== null) {
    const d = CT.tx - CT.x, step = (CT.spd || 46) * dt;
    if (Math.abs(d) <= step) { CT.x = CT.tx; CT.tx = null; cityArrive(); } else { CT.dir = d > 0 ? 1 : -1; CT.x += CT.dir * step; CT.walk += dt * 9; }
  }
  const camT = Math.max(0, Math.min(CITY_W - VW, CT.x - VW / 2));
  CT.cam += (camT - CT.cam) * Math.min(1, dt * 6);
  const cam = Math.round(CT.cam);
  // sky: night ↔ dusk by the game clock
  const f = S.clock / DAY_SEC, night = f > 0.62 || f < 0.08 ? 1 : f > 0.45 ? (f - 0.45) / 0.17 : 0.25;
  const sky = g.createLinearGradient(0, 0, 0, CITY_SIDEWALK);
  sky.addColorStop(0, night > 0.6 ? '#070a26' : '#1a1050');
  sky.addColorStop(1, night > 0.6 ? '#2a1250' : '#a0407a');
  g.fillStyle = sky; g.fillRect(0, 0, VW, CITY_H);
  for (const s of CT.sky) { const sx = ((s[0] - cam * 0.1) % CITY_W + CITY_W) % CITY_W; if (sx < VW && (Math.sin(t * 2 + s[2] * 9) > -0.6)) px(g, sx, s[1], 1, 1, s[2] > 0.8 ? '#ffd23f' : '#c8d0ff'); }
  // moon
  px(g, VW - 28, 8, 8, 8, '#f0e8c8'); px(g, VW - 26, 8, 6, 2, '#d8d0b0');
  // far skyline (parallax)
  for (const b of CT.far) {
    const bx = Math.round(b[0] - cam * 0.35);
    if (bx > VW || bx + b[1] < 0) continue;
    px(g, bx, CITY_SIDEWALK - b[2], b[1], b[2], '#120c3a');
    const wr = cityRand(b[3]);
    for (let wy = CITY_SIDEWALK - b[2] + 3; wy < CITY_SIDEWALK - 4; wy += 4) for (let wx = bx + 2; wx < bx + b[1] - 2; wx += 3) if (wr() < 0.22) px(g, wx, wy, 1, 2, wr() < 0.6 ? '#5a4a20' : '#20406a');
  }
  const ready = cityReady();
  // buildings
  for (const b of CITY_B) {
    const bx = b.x - cam, top = CITY_SIDEWALK - b.h;
    if (bx > VW || bx + b.w < 0) continue;
    const sel = CT.sel === b.id;
    px(g, bx, top, b.w, b.h, b.wall);
    px(g, bx, top, b.w, 2, b.trim); px(g, bx - 1, top + 2, b.w + 2, 2, b.trim);
    // windows
    const wr = cityRand(b.x + 7);
    for (let wy = top + 8; wy < CITY_SIDEWALK - 26; wy += 9) for (let wx = bx + 6; wx < bx + b.w - 8; wx += 10) {
      const lit = wr() < 0.7, flick = lit && Math.sin(t * 0.7 + wx) > 0.97;
      px(g, wx, wy, 6, 5, lit && !flick ? (wr() < 0.5 ? '#ffd27a' : '#7ad8ff') : '#0c0f2e');
      px(g, wx, wy + 5, 6, 1, b.trim);
    }
    // neon sign with a soft glow
    const sw = textWidth(b.sign), sx = Math.round(bx + (b.w - sw) / 2), sy = CITY_SIDEWALK - 25;
    const pulse = 0.55 + 0.25 * Math.sin(t * 3 + b.x);
    g.globalCompositeOperation = 'lighter'; g.globalAlpha = pulse * 0.3; px(g, sx - 4, sy - 4, sw + 8, 13, b.neon); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    px(g, sx - 2, sy - 2, sw + 4, 9, '#0a0a1e');
    signText(g, b.sign, sx, sy, b.neon);
    // door + light spill
    const dx = Math.round(bx + b.w / 2 - 6);
    px(g, dx, CITY_SIDEWALK - 15, 12, 15, '#0a0c22');
    px(g, dx + 1, CITY_SIDEWALK - 14, 10, 14, sel ? b.neon : '#2a2a5a');
    g.globalAlpha = 0.18; px(g, dx - 4, CITY_SIDEWALK, 20, 3, b.neon); g.globalAlpha = 1;
    // something waits inside: a bouncing "!"
    if (ready[b.id]) {
      const by = Math.round(top - 9 + Math.sin(t * 5) * 2);
      px(g, bx + b.w / 2 - 3, by - 1, 7, 10, '#0a0a1e');
      px(g, bx + b.w / 2 - 2, by, 5, 8, '#ffd23f');
      px(g, bx + b.w / 2, by + 1, 1, 4, '#0a0a1e'); px(g, bx + b.w / 2, by + 6, 1, 1, '#0a0a1e');
    }
  }
  // street lamps
  for (let lx = 82; lx < CITY_W; lx += 80) {
    const x = lx - cam;
    if (x < -20 || x > VW + 20) continue;
    px(g, x, CITY_SIDEWALK - 30, 1, 30, '#3a3f6a'); px(g, x - 1, CITY_SIDEWALK - 31, 4, 2, '#5a5f8a');
    // a warm cone of light down to the pavement
    g.globalCompositeOperation = 'lighter';
    g.globalAlpha = (CT.t - CT.flickT < 1 && Math.sin(CT.t * 40) > 0 ? 0.02 : 0.07) + 0.02 * Math.sin(t * 8 + lx);
    for (let i = 0; i < 29; i += 2) { const hw = 1 + Math.round(i * 0.38); px(g, x + 1 - hw, CITY_SIDEWALK - 28 + i, hw * 2 + 1, 2, '#ffc070'); }
    g.globalAlpha = 0.2; px(g, x - 9, CITY_SIDEWALK, 20, 2, '#ffc070');
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    px(g, x + 1, CITY_SIDEWALK - 29, 2, 1, '#fff2c0');
  }
  // sidewalk + road
  px(g, 0, CITY_SIDEWALK, VW, 6, '#2a2e5a'); px(g, 0, CITY_SIDEWALK, VW, 1, '#4a4f8a');
  for (let x = -((cam) % 12); x < VW; x += 12) px(g, x, CITY_SIDEWALK + 1, 1, 5, '#22264e');
  px(g, 0, CITY_SIDEWALK + 6, VW, CITY_H - CITY_SIDEWALK - 6, '#121430');
  for (let x = -((cam) % 24); x < VW; x += 24) px(g, x, CITY_SIDEWALK + 13, 10, 1, '#5a5a3a');
  // cars
  for (const c of CT.cars) {
    c.x += c.v * dt;
    if (c.x > CITY_W + 60) c.x = -60; if (c.x < -60) c.x = CITY_W + 60;
    const x = Math.round(c.x - cam), y = c.v > 0 ? CITY_SIDEWALK + 8 : CITY_SIDEWALK + 15;
    if (x < -40 || x > VW + 40) continue;
    px(g, x, y, 22, 5, c.col); px(g, x + 5, y - 3, 11, 3, c.col); px(g, x + 6, y - 2, 9, 2, '#1a2a4a');
    px(g, x + 3, y + 5, 4, 2, '#0a0a0a'); px(g, x + 15, y + 5, 4, 2, '#0a0a0a');
    // headlight beam: fades out in three steps
    g.globalCompositeOperation = 'lighter';
    for (let k = 0; k < 3; k++) { g.globalAlpha = 0.22 - k * 0.06; px(g, c.v > 0 ? x + 22 + k * 4 : x - 4 - k * 4, y + 1 - (k ? 1 : 0), 4, 2 + (k ? 2 : 0), '#fff2c0'); }
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    px(g, c.v > 0 ? x + 21 : x, y + 1, 1, 2, '#fff8e0');
  }
  // a vending machine / news kiosk between the buildings
  { const x = CITY_KIOSK - cam; if (x > -12 && x < VW + 12) {
    px(g, x, CITY_SIDEWALK - 20, 10, 20, '#2a3a8a'); px(g, x + 1, CITY_SIDEWALK - 19, 8, 9, '#0a1030');
    for (let k = 0; k < 3; k++) px(g, x + 2, CITY_SIDEWALK - 18 + k * 3, 6, 2, ['#ff4d6d', '#ffd23f', '#2ee66b'][(k + Math.floor(t * 2)) % 3]);
    px(g, x + 3, CITY_SIDEWALK - 7, 4, 2, '#0a0a1e'); cityGlow(g, x - 2, CITY_SIDEWALK - 22, 14, 24, '#3fd0ff', 0.08);
  } }
  // the street cat by the café: sits up and purrs when petted
  { const x = CITY_CAT - cam, pet = CT.t - CT.catT < 2.2; if (x > -10 && x < VW + 10) {
    const y = CITY_SIDEWALK + 2, tail = Math.round(Math.sin(t * (pet ? 9 : 2)) * 1.5);
    px(g, x - 3, y - (pet ? 5 : 3), 7, pet ? 5 : 3, '#ff9a3c'); px(g, x + 3, y - (pet ? 8 : 5), 4, 4, '#ff9a3c'); px(g, x + 3, y - (pet ? 9 : 6), 1, 1, '#ff9a3c'); px(g, x + 6, y - (pet ? 9 : 6), 1, 1, '#ff9a3c');
    px(g, x + 4, y - (pet ? 7 : 4), 1, 1, '#0a0a1e'); px(g, x - 5, y - 4 + tail, 2, 1, '#ff9a3c'); px(g, x - 4, y - 3, 1, 3, '#ff9a3c');
    if (pet) for (let k = 0; k < 3; k++) { const hy = y - 12 - ((CT.t - CT.catT) * 10 + k * 4) % 14; g.globalAlpha = 0.8; px(g, x + 2 + k * 3 - 3, Math.round(hy), 2, 2, '#ff4d6d'); g.globalAlpha = 1; }
  } }
  // something shiny on the sidewalk: tap it
  if (cityFindReady()) { const x = cityFindX() - cam, tw = Math.sin(t * 6) > 0; if (x > -6 && x < VW + 6) {
    px(g, x - 1, CITY_SIDEWALK + 2, 3, 3, '#ffd23f'); px(g, x, CITY_SIDEWALK + 3, 1, 1, '#fff8c0');
    if (tw) { px(g, x, CITY_SIDEWALK - 1, 1, 2, '#fff8c0'); px(g, x - 3, CITY_SIDEWALK + 3, 2, 1, '#fff8c0'); px(g, x + 3, CITY_SIDEWALK + 3, 2, 1, '#fff8c0'); }
    cityGlow(g, x - 4, CITY_SIDEWALK - 1, 9, 8, '#ffd23f', 0.18);
  } }
  // pedestrians
  for (const n of CT.npcs) {
    if (n.pause > 0) n.pause -= dt; else { n.x += n.v * dt; if (Math.random() < dt * 0.08) n.pause = 1 + Math.random() * 2; }
    if (n.x > CITY_W + 10) n.x = -10; if (n.x < -10) n.x = CITY_W + 10;
    const x = n.x - cam;
    if (x > -10 && x < VW + 10) cityPerson(g, x, CITY_SIDEWALK + 3, 1 + n.look, Math.floor(CT.t * 7 + n.look), n.v > 0 ? 1 : -1, n.pause > 0);
  }
  // the player
  cityPerson(g, CT.x - cam, CITY_SIDEWALK + 4, 0, Math.floor(CT.walk), CT.dir, CT.tx === null);
  // rain with the rainy lo-fi track
  if (MUSIC.on && TRACKS[MUSIC.track] && TRACKS[MUSIC.track].rain) {
    g.fillStyle = 'rgba(160,190,255,.35)';
    for (let i = 0; i < 40; i++) { const rx = (i * 37 + CT.t * 30) % VW, ry = (i * 53 + CT.t * 140) % CITY_H; g.fillRect(Math.round(rx), Math.round(ry), 1, 3); }
  }
}
/* ---------- input ---------- */
function cityTap(e) {
  const cv = UI.cityCv;
  if (!cv) return;
  if (artTap(e)) return;
  const r = cv.getBoundingClientRect();
  const lx = ((e.clientX - r.left) / r.width) * cv.width, ly = ((e.clientY - r.top) / r.height) * CITY_H;
  if (CT.inside) {
    // inside: the left edge is the door; things in the room react; anywhere else talks to the person behind the counter
    const fx = (e.clientX - r.left) / r.width;
    if (fx < 0.16) { cityAct('out'); return; }
    const h = cityHot(CT.inside, cv.width).find((o) => lx >= o.x && lx <= o.x + o.w && ly >= o.y && ly <= o.y + o.h);
    if (h) cityPoke(CT.inside + '.' + h.k, e); else cityTalk();
    return;
  }
  const wx = lx + Math.round(CT.cam);
  // the street: the shiny find, the cat, the kiosk, passers-by, cars, the moon
  if (cityFindReady() && Math.abs(wx - cityFindX()) <= 7 && ly >= CITY_SIDEWALK - 8) { cityPick(e); return; }
  if (Math.abs(wx - CITY_CAT) <= 8 && ly >= CITY_SIDEWALK - 14 && ly <= CITY_SIDEWALK + 6) { CT.catT = CT.t; sfx('meow'); cityPoke('st.cat', e, '❤️'); return; }
  if (wx >= CITY_KIOSK - 2 && wx <= CITY_KIOSK + 12 && ly >= CITY_SIDEWALK - 22 && ly <= CITY_SIDEWALK) { cityPoke('st.kiosk', e, '📰'); return; }
  if (lx >= cv.width - 31 && lx <= cv.width - 17 && ly <= 19) { cityPoke('st.moon', e, '🌙'); return; }
  const npc = CT.npcs.find((n) => Math.abs(wx - n.x) <= 6 && ly >= CITY_SIDEWALK - 20 && ly <= CITY_SIDEWALK + 5);
  if (npc) { npc.pause = 2.5; npc.v = -npc.v; cityPoke('st.npc', e, '💬'); return; }
  const car = CT.cars.find((c) => wx >= c.x - 4 && wx <= c.x + 26 && ly >= CITY_SIDEWALK + 4);
  if (car) { try { beep(620, 0.09, 'square', 0.05); beep(520, 0.12, 'square', 0.05, 0.12); } catch (x) {} cityPoke('st.car', e, '📯'); return; }
  if (ly < CITY_SIDEWALK - 26 && CITY_B.every((b) => wx < b.x || wx > b.x + b.w)) { CT.flickT = CT.t; sfx('blip'); return; }
  const b = CITY_B.find((x) => wx >= x.x - 4 && wx <= x.x + x.w + 4);
  if (b) cityGo(b.id); else { CT.tx = Math.max(6, Math.min(CITY_W - 6, wx)); CT.spd = Math.max(46, Math.abs(CT.tx - CT.x) / 1.4); CT.sel = null; }
}
function cityGo(id) {
  const b = CITY_B.find((x) => x.id === id);
  if (!b) return;
  CT.sel = null; CT.street = false;
  if (CT.inside) { CT.inside = null; CT.say = ''; }
  CT.tx = b.x + b.w / 2;
  CT.spd = Math.max(46, Math.abs(CT.tx - CT.x) / 1.4); // far buildings: a brisk run, never a long walk
  sfx('blip');
  if (Math.abs(CT.tx - CT.x) < 1) { CT.tx = null; cityArrive(); }
  render(true);
}
function cityArrive() {
  const b = CITY_B.find((x) => CT.x >= x.x && CT.x <= x.x + x.w);
  if (!b) return;
  if (b.id === 'office') { go('office'); return; }
  CT.sel = b.id;
  cityEnter(b.id);
  sfx('pop'); haptic('light');
  render(true);
  setTimeout(() => { const p = $('#citypanel'); if (p) p.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }, 60);
}

/* ---------- screen ---------- */
function cityPanel(id) {
  const c = cityS();
  if (id === 'cafe') {
    const left = cityLeft(c.cof);
    return `<h3 class="h3">☕ ${L('city.cafe')}</h3><p class="sub">${L('city.cafe_t', { min: CITY.cofCd / 60 })}</p>
      ${left > 0 ? `<button class="btn big wide" disabled>${L('city.wait')} <span data-t="cityleft:cof"></span></button>` : `<button class="btn big glow wide" data-act="city:cof" ${S.cash >= CITY.cofCost(S) ? '' : 'disabled'}>☕ ${L('city.cafe_btn')} · ${money(CITY.cofCost(S))}</button>`}`;
  }
  if (id === 'academy') {
    if (c.trn) {
      return `<h3 class="h3">🎓 ${L('city.academy')}</h3>
        <div class="ct-row">${imgTag(portrait(c.trn.role, S.staff[c.trn.role].mood), 'av')}<div><b>${L('role.' + c.trn.role + '.name')}</b><small>${L('city.trn_now', { a: S.staff[c.trn.role].lvl, b: S.staff[c.trn.role].lvl + 1 })}</small></div><b class="px grn" data-t="cityleft:trn"></b></div>
        <div class="bar"><i data-w="citytrn"></i></div>
        <button class="btn wide gem" data-act="city:trd" ${S.gems < CITY.trnGems ? 'disabled' : ''}>${L('city.trn_now_btn', { n: CITY.trnGems })} 💎</button>
        <p class="hint">${L('city.trn_hint')}</p>`;
    }
    const rs = trainable();
    return `<h3 class="h3">🎓 ${L('city.academy')}</h3><p class="sub">${L('city.academy_t')}</p>
      ${rs.length ? rs.map((r) => { const lv = S.staff[r].lvl, cost = CITY.trnCost(r, lv); return `<div class="ct-row">${imgTag(portrait(r, S.staff[r].mood), 'av')}<div><b>${L('role.' + r + '.name')}</b><small>LV ${lv} → ${lv + 1} · ${mmss(CITY.trnSec(lv))} · <s class="was">${money(upCost(r, lv))}</s></small></div><button class="btn sm ${S.cash >= cost ? 'glow' : ''}" data-act="city:trn:${r}" ${S.cash >= cost ? '' : 'disabled'}>${money(cost)}</button></div>`; }).join('') : `<p class="hint">${L('city.academy_none')}</p>`}`;
  }
  if (id === 'deals') {
    const k = c.ctr || c.offer;
    if (!k) return `<h3 class="h3">📋 ${L('city.deals')}</h3><p class="sub">${S.stats.released ? L('city.deals_wait') : L('city.deals_first')}</p>${S.stats.released ? `<button class="btn big wide" disabled>${L('city.wait')} <span data-t="cityleft:next"></span></button>` : ''}`;
    const req = L('city.deal_req', { genre: L('genre.' + k.genre), tier: L('tier.' + k.tier), min: k.min });
    return `<h3 class="h3">📋 ${L('city.deals')}</h3>
      <div class="ct-deal"><small>${L(c.ctr ? 'city.deal_active' : 'city.deal_offer')}</small><b>${req}</b>
        <div class="ct-rw"><span class="chip yel">${money(k.cash)}</span><span class="chip mag">+${k.gems} 💎</span><span class="chip cy">+⭐</span></div>
        ${c.ctr ? `<small class="mut">${L('city.deal_left')} <b data-t="cityleft:ctr"></b></small>` : `<small class="mut">${L('city.deal_time', { m: CITY.ctrSec / 60 })}</small>`}</div>
      ${c.ctr ? `<button class="btn big glow wide" data-act="city:make">${I('games', 16)} ${L('city.deal_make')}</button><button class="linkbtn" data-act="city:ctx">${L('city.deal_drop')}</button>`
        : `<button class="btn big glow wide" data-act="city:ctr">${L('city.deal_take')}</button>`}`;
  }
  if (id === 'games') {
    const g = S.games.find((x) => x.active), left = cityLeft(c.promo), cost = promoCost(S);
    return `<h3 class="h3">🎮 ${L('city.games')}</h3><p class="sub">${L('city.games_t')}</p>
      ${!g ? `<p class="hint">${L('city.games_none')}</p>` : left > 0 ? `<button class="btn big wide" disabled>${L('city.wait')} <span data-t="cityleft:promo"></span></button>`
        : `<div class="ct-row">${imgTag(coverURL(g.genre, g.name), 'cover sm')}<div><b>${esc(g.name)}</b><small>${L('city.games_boost')}</small></div></div><button class="btn big glow wide" data-act="city:prm" ${S.cash >= cost ? '' : 'disabled'}>${L('city.games_btn', { cost: money(cost) })}</button>`}`;
  }
  if (id === 'mall') {
    return `<h3 class="h3">🛋️ ${L('city.mall')}</h3><p class="sub">${L('city.mall_t')}</p>
      <h3 class="h3 sm">${L('empire.shop')}</h3><div class="items">${ITEMS.map(itemRow).join('')}</div>
      <h3 class="h3 sm">${L('empire.boosts')}</h3><div class="items">${BOOSTS.map(boostRow).join('')}</div>
      <p class="hint">${L('empire.gems_hint')}</p>`;
  }
  if (id === 'arena') {
    const fresh = cityReady().arena;
    return `<h3 class="h3">⚔️ ${L('city.arena')}</h3><p class="sub">${L('city.arena_t')}</p>
      <button class="btn big glow wide" data-act="pvp">⚔️ ${L('city.arena_btn')}</button>
      ${fresh ? `<p class="hint">🎁 ${L('city.arena_daily')}</p>` : ''}`;
  }
  return '';
}
function tabCity() {
  const r = cityReady(), c = cityS();
  const chips = CITY_B.filter((b) => b.id !== 'office').map((b) => {
    const st = b.id === 'academy' && c.trn ? `<b class="px" data-t="cityleft:trn"></b>` : b.id === 'deals' && c.ctr ? `<b class="px" data-t="cityleft:ctr"></b>` : r[b.id] ? '<i class="dot"></i>' : '';
    return `<button class="ct-chip ${CT.sel === b.id ? 'on' : ''}" data-act="city:go:${b.id}" style="--n:${b.neon}"><span>${L('city.ico_' + b.id)}</span><small>${L('city.' + b.id)}</small>${st}</button>`;
  }).join('');
  return `<section class="panel cityp">
    <div class="ct-head">${CT.inside ? `<button class="btn ghost sm" data-act="city:out">‹ ${L('city.out')}</button><h2 class="h2">${L('city.ico_' + CT.inside)} ${L('city.' + CT.inside)}</h2>` : `<button class="btn ghost sm" data-act="city:home">‹ ${L('city.back')}</button><h2 class="h2">🌆 ${L('city.title')}</h2>`}</div>
    <div class="ct-scene ${CT.inside ? 'in' : ''}"><canvas id="citycv" width="240" height="${CITY_H}" aria-label="${esc(L('city.title'))}"></canvas>${CT.inside || CT.sel ? '' : `<span class="ct-tip">${L('city.tip')}</span>`}</div>
    ${CT.say && (CT.inside || CT.street) ? `<button class="ct-say" data-act="city:talk"><span>💬</span>${esc(CT.say)}</button>` : ''}
    <div class="ct-chips">${chips}</div>
  </section>
  ${CT.sel ? `<section class="panel ct-panel" id="citypanel">${cityPanel(CT.sel)}</section>` : ''}
  <p class="hint pad">${L('city.hint')}</p>
  <p class="hint pad ct-found">🔍 ${L('city.found', { n: CITY_SECRETS.filter((k) => cityFound().has(k)).length, N: CITY_SECRETS.length })}</p>`;
}
// the city entry on the office screen: shows what is waiting outside
function cityBanner() {
  if (!S.stats.taps) return '';
  const r = cityReady(), c = cityS();
  const items = [];
  if (r.cafe) items.push('☕ ' + L('city.rdy_cafe'));
  if (c.trn) items.push(`🎓 <span data-t="cityleft:trn"></span>`);
  else if (r.academy) items.push('🎓 ' + L('city.rdy_academy'));
  if (c.ctr) items.push(`📋 <span data-t="cityleft:ctr"></span>`);
  else if (r.deals) items.push('📋 ' + L('city.rdy_deals'));
  if (r.games) items.push('🎮 ' + L('city.rdy_games'));
  if (r.mall) items.push('🛋️ ' + L('city.rdy_mall'));
  return `<button class="panel citybn ${items.length ? 'hot' : ''}" data-act="city:open"><span class="cb-ico">🌆</span><span class="cb-t"><b>${L('city.go')}</b><small>${items.length ? items.join(' · ') : L('city.go_s')}</small></span><b class="chev">›</b></button>`;
}
function cityAct(what, arg) {
  if (what === 'open') { go('city'); return; }
  if (what === 'home') { go('office'); return; }
  if (what === 'go') { if (artOn() && artImg(ART_ST.img)) artGo(arg); else cityGo(arg); return; }
  if (what === 'out') { CT.inside = null; CT.sel = null; CT.say = ''; CT.street = false; sfx('blip'); render(true); return; }
  if (what === 'talk') { if (CT.street && !CT.inside) { CT.say = ''; CT.street = false; render(true); return; } cityTalk(); return; }
  // from the office side bar: step out right at that building and go in
  if (what === 'visit') { const b = CITY_B.find((x) => x.id === arg); if (!b) return; if (ART_ST.b[arg]) { CT.ax = (ART_ST.b[arg][0] + ART_ST.b[arg][1]) / 2; CT.atx = null; } CT.x = b.x + b.w / 2; CT.tx = null; CT.cam = Math.max(0, CT.x - 80); go('city'); cityArrive(); return; }
  if (what === 'make') { const k = cityS().ctr; if (k) { S.sel.genre = k.genre; if (tierUnlocked(S, k.tier)) S.sel.tier = k.tier; } go('studio', 'games'); return; }
  const map = { cof: 'city.ok_cof', trn: 'city.ok_trn', trd: '', ctr: 'city.ok_ctr', ctx: '', prm: 'city.ok_prm' };
  if (!(what in map)) return;
  const r = arg ? netAct(what, arg) : netAct(what);
  if (r && r.ok) { if (map[what]) toast(L(map[what]), 'good'); haptic('medium'); if (what === 'cof' || what === 'prm') confettiSmall(); } else errToast(r && r.err);
  render(true);
}

/* ---------- secrets and little reactions ---------- */
function cityLine(key) {
  const [id, k] = key.split('.');
  if (key === 'games.shelf') {
    const gs = S.games.filter((x) => x.score);
    if (!gs.length) return L('city.x_games_none');
    const g = gs[Math.floor(Math.random() * gs.length)];
    return L('city.x_games_shelf', { name: g.name, score: g.score });
  }
  if (key === 'mall.items') {
    const it = ITEMS[Math.floor(Math.random() * ITEMS.length)];
    return L('item.' + it.id + '.name') + ' — ' + (S.items[it.id] ? L('city.x_mall_own') : L('item.' + it.id + '.perk') + ' · ' + money(it.price));
  }
  const n = CITY_X[key] || 1;
  return L('city.x_' + id + '_' + k + '_' + (1 + Math.floor(Math.random() * n)));
}
function cityPoke(key, e, ico) {
  const [id, k] = key.split('.');
  if (key === 'cafe.machine') CT.steamT = CT.t;
  if (key === 'arena.crowd') CT.waveT = CT.t;
  if (key === 'cafe.cat') { CT.catT = CT.t; sfx('meow'); } else if (!ico || ico !== '❤️') sfx('pop');
  haptic('light');
  CT.say = cityLine(key); CT.sayN++; CT.street = !CT.inside;
  if (e && e.clientX) floatText(e.clientX, e.clientY - 10, ico || { menu: '📋', machine: '☁️', cup: '☕', cat: '😺', board: '📝', shelf: '📚', desk: '✏️', window: '🌃', laptop: '💻', items: '🛋️', screen: '📺', crowd: '📣' }[k] || '✨', 'gold');
  const f = cityFound();
  if (!f.has(key) && CITY_SECRETS.includes(key)) {
    f.add(key); lsSet('od2_city_found', JSON.stringify([...f]));
    const n = CITY_SECRETS.filter((x) => f.has(x)).length;
    if (n === CITY_SECRETS.length) { toast(L('city.found_all'), 'good'); confetti(80); sfx('up'); }
    else toast(L('city.found_new', { n, N: CITY_SECRETS.length }), 'good');
  }
  render(true);
}
// pick up the shiny thing on the sidewalk (a real, small reward — the engine checks the cooldown)
function cityPick(e) {
  const before = { cash: S.cash, gems: S.gems };
  const r = netAct('fnd');
  if (!r || !r.ok) { errToast(r && r.err); return; }
  const gem = S.gems > before.gems;
  floatText(e.clientX, e.clientY - 10, gem ? '+1 💎' : '+' + money(S.cash - before.cash), 'gold');
  burst(e.clientX, e.clientY, 10, ['#ffd23f', '#fff8c0']);
  toast(gem ? L('city.find_gem') : L('city.find_cash', { cash: money(S.cash - before.cash) }), 'good');
  haptic('medium');
  render(true);
}
// tappable things inside each building, in canvas pixels (same geometry as cityInterior)
function cityHot(id, VW) {
  const cx = Math.round(VW * 0.64);
  if (id === 'cafe') return [{ k: 'menu', x: 30, y: 10, w: 54, h: 30 }, { k: 'machine', x: cx + 20, y: 42, w: 20, h: 26 }, { k: 'cup', x: cx - 33, y: 56, w: 11, h: 11 }, { k: 'cat', x: Math.round(VW * 0.3) - 9, y: 76, w: 20, h: 14 }];
  if (id === 'academy') { const bw = Math.min(120, VW - 70), bx = Math.round((VW - bw) / 2) + 10; return [{ k: 'board', x: bx - 2, y: 8, w: bw + 4, h: 40 }, { k: 'shelf', x: VW - 22, y: 20, w: 18, h: 64 }, { k: 'desk', x: 30, y: 88, w: VW - 100, h: 24 }]; }
  if (id === 'deals') { const bw = Math.min(90, Math.round(VW * 0.45)); return [{ k: 'board', x: 26, y: 10, w: bw, h: 44 }, { k: 'window', x: VW - 46, y: 12, w: 38, h: 30 }, { k: 'laptop', x: cx + 6, y: 60, w: 14, h: 12 }]; }
  if (id === 'games') return [{ k: 'shelf', x: 24, y: 20, w: VW - 48, h: 60 }];
  if (id === 'mall') return [{ k: 'items', x: 24, y: 50, w: VW - 40, h: 36 }];
  if (id === 'arena') { const sw = Math.min(110, VW - 60), sx = Math.round((VW - sw) / 2) + 8; return [{ k: 'screen', x: sx - 2, y: 6, w: sw + 4, h: 40 }, { k: 'crowd', x: 24, y: 92, w: VW - 28, h: 28 }]; }
  return [];
}
/* ---------- inside the buildings ---------- */
const CITY_SAY = { cafe: 3, academy: 3, deals: 3, games: 3, mall: 3, arena: 3 };
function cityEnter(id) {
  CT.inside = id;
  CT.sayI = Math.floor(Math.random() * (CITY_SAY[id] || 1));
  CT.say = L('city.say_' + id + '_' + (CT.sayI + 1));
  CT.sayN++;
}
function cityTalk() {
  const id = CT.inside;
  if (!id || !CITY_SAY[id]) return;
  CT.sayI = (CT.sayI + 1) % CITY_SAY[id];
  CT.say = L('city.say_' + id + '_' + (CT.sayI + 1));
  CT.sayN++; CT.talkT = CT.t;
  sfx('pop'); haptic('light');
  render(true);
}
function cityRoom(g, VW, wall, floor, trim) {
  px(g, 0, 0, VW, 86, wall);
  for (let x = 0; x < VW; x += 16) px(g, x, 0, 1, 86, 'rgba(0,0,0,.12)');
  px(g, 0, 84, VW, 3, trim);
  px(g, 0, 87, VW, CITY_H - 87, floor);
  for (let y = 92; y < CITY_H; y += 7) px(g, 0, y, VW, 1, 'rgba(0,0,0,.18)');
  // the door we came in through, on the left
  px(g, 2, 46, 18, 41, '#0a0c22'); px(g, 4, 48, 14, 39, '#1c2050'); px(g, 15, 66, 2, 3, '#ffd23f');
}
function cityGlow(g, x, y, w, h, col, a) { g.globalCompositeOperation = 'lighter'; g.globalAlpha = a; px(g, x, y, w, h, col); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; }
function cityLamp(g, x, t) {
  px(g, x, 0, 1, 14, '#5a5f8a'); px(g, x - 3, 14, 7, 3, '#ffd890');
  // a soft cone of warm light under the shade (no hard box)
  g.globalCompositeOperation = 'lighter'; g.globalAlpha = 0.06 + 0.015 * Math.sin(t * 6 + x);
  for (let i = 0; i < 26; i += 2) { const hw = 2 + Math.round(i * 0.45); px(g, x - hw, 17 + i, hw * 2 + 1, 2, '#ffc070'); }
  g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
}
function cityInterior(g, VW, t) {
  const id = CT.inside, cx = Math.round(VW * 0.64), fr = Math.floor(CT.t * 6);
  const talking = CT.talkT && CT.t - CT.talkT < 1.2;
  const bob = talking ? Math.round(Math.sin(CT.t * 14)) : 0;
  if (id === 'cafe') {
    cityRoom(g, VW, '#3a1d48', '#2a1430', '#5a2e6a');
    // menu board
    px(g, 30, 10, 54, 30, '#120a1a'); px(g, 30, 10, 54, 1, '#7a4a2a'); signText(g, 'MENU', 46, 14, '#ffb03f');
    for (let i = 0; i < 3; i++) { px(g, 35, 23 + i * 5, 26, 1, '#c8b0e0'); px(g, 72, 23 + i * 5, 7, 1, '#ffd23f'); }
    cityLamp(g, Math.round(VW * 0.45), t); cityLamp(g, Math.round(VW * 0.82), t);
    cityPerson2(g, cx, 78 + bob, 1, fr, -1, !talking); // barista behind the counter
    px(g, cx - 40, 66, 84, 4, '#a8683a'); px(g, cx - 40, 70, 84, 18, '#7a4a2a'); px(g, cx - 40, 70, 84, 1, '#5a321a');
    // coffee machine with steam
    const mx = cx + 22;
    px(g, mx, 48, 16, 18, '#9aa0c0'); px(g, mx + 1, 49, 14, 3, '#c8cce8'); px(g, mx + 6, 58, 4, 4, '#2a2a3a'); px(g, mx + 3, 53, 2, 2, Math.sin(t * 4) > 0 ? '#ff4d6d' : '#7a2a3a');
    for (let i = 0; i < 4; i++) { const sy = 44 - ((t * 12 + i * 5) % 18); g.globalAlpha = 0.5 - (44 - sy) / 40; px(g, mx + 7 + Math.round(Math.sin(t * 3 + i) * 2), Math.round(sy), 1, 2, '#ffffff'); }
    g.globalAlpha = 1;
    px(g, cx - 30, 61, 5, 5, '#f0e8d8'); px(g, cx - 29, 60, 3, 1, '#6a3a1a'); // a cup on the counter
    if (CT.t - CT.steamT < 1.6) for (let i = 0; i < 10; i++) { const k = (CT.t - CT.steamT) * 30 + i * 3; g.globalAlpha = Math.max(0, 0.7 - k / 50); px(g, mx + 4 + Math.round(Math.sin(i * 2.1 + k * 0.2) * (2 + k / 6)), Math.round(46 - k), 3, 3, '#ffffff'); }
    g.globalAlpha = 1;
    // the café cat asleep on a cushion
    const kx = Math.round(VW * 0.3), awake = CT.t - CT.catT < 2;
    px(g, kx - 9, 86, 20, 3, '#5a2e6a');
    px(g, kx - 6, 80, 12, 6, '#ff9a3c'); px(g, kx + 4, 77, 6, 6, '#ff9a3c'); px(g, kx + 4, 76, 1, 1, '#ff9a3c'); px(g, kx + 9, 76, 1, 1, '#ff9a3c');
    px(g, kx + 6, 79, awake ? 1 : 2, 1, '#0a0a1e'); px(g, kx - 8, 83 + Math.round(Math.sin(t * 2)), 3, 2, '#ff9a3c');
    if (!awake) { const zz = (t * 0.8) % 1; g.globalAlpha = 1 - zz; { const zx = kx + 10, zy = Math.round(70 - zz * 8); px(g, zx, zy, 4, 1, '#c8b0e0'); px(g, zx + 2, zy + 1, 1, 1, '#c8b0e0'); px(g, zx + 1, zy + 2, 1, 1, '#c8b0e0'); px(g, zx, zy + 3, 4, 1, '#c8b0e0'); } g.globalAlpha = 1; }
    else for (let k = 0; k < 3; k++) { const hy = 72 - ((CT.t - CT.catT) * 10 + k * 4) % 14; px(g, kx + k * 4, Math.round(hy), 2, 2, '#ff4d6d'); }
  } else if (id === 'academy') {
    cityRoom(g, VW, '#16384a', '#1a2a3a', '#22566e');
    const bw = Math.min(120, VW - 70), bx = Math.round((VW - bw) / 2) + 10;
    px(g, bx - 2, 8, bw + 4, 40, '#7a5a3a'); px(g, bx, 10, bw, 36, '#1c3a2a');
    signText(g, 'LEARN', bx + 8, 15, '#e8f0e8');
    for (let i = 0; i < 3; i++) px(g, bx + 8, 26 + i * 6, 20 + ((i * 37) % 40), 1, 'rgba(232,240,232,.7)');
    px(g, bx + bw - 30, 24, 18, 14, 'rgba(232,240,232,.25)'); // a sketch of a sprite
    // bookshelf
    px(g, VW - 22, 20, 18, 64, '#4a3020');
    const bc = ['#ff4d6d', '#3fd0ff', '#ffd23f', '#2ee66b', '#ff3fd0'];
    for (let r = 0; r < 4; r++) for (let k = 0; k < 4; k++) px(g, VW - 20 + k * 4, 24 + r * 15, 3, 11, bc[(r + k) % 5]);
    cityPerson2(g, bx + bw - 10, 88 + bob, 3, fr, -1, !talking); // the teacher
    for (let i = 0; i < 3; i++) { const dx = 36 + i * Math.round((VW - 90) / 3); px(g, dx, 96, 26, 3, '#a8683a'); px(g, dx + 2, 99, 2, 10, '#5a3a20'); px(g, dx + 22, 99, 2, 10, '#5a3a20'); px(g, dx + 8, 92, 10, 4, '#2a2e5a'); }
  } else if (id === 'deals') {
    cityRoom(g, VW, '#3a2a18', '#24180e', '#5a4226');
    // cork board with contracts pinned on it
    const bw = Math.min(90, Math.round(VW * 0.45));
    px(g, 26, 10, bw, 44, '#8a6a3a'); px(g, 26, 10, bw, 2, '#5a4226');
    const nc = ['#fff2a0', '#a0e8ff', '#ffb0d8', '#c0ffb0'], pins = Math.min(9, 3 + cityS().done);
    for (let i = 0; i < pins; i++) { const nx = 30 + (i % 4) * Math.floor((bw - 8) / 4), ny = 15 + Math.floor(i / 4) * 13; px(g, nx, ny, 14, 10, nc[i % 4]); px(g, nx + 2, ny + 3, 9, 1, '#8a8070'); px(g, nx + 2, ny + 6, 6, 1, '#8a8070'); px(g, nx + 6, ny - 1, 2, 2, '#ff4d6d'); }
    // a window with the city at night
    const wx = VW - 46; px(g, wx, 12, 38, 30, '#0a1030'); for (let i = 0; i < 8; i++) px(g, wx + 3 + i * 4, 26 + (i * 7) % 12, 3, 16, '#1a1650'); px(g, wx, 26, 38, 1, '#5a4226'); px(g, wx + 18, 12, 1, 30, '#5a4226');
    cityPerson2(g, cx, 82 + bob, 4, fr, -1, !talking); // the client
    px(g, cx - 34, 70, 70, 4, '#6a4a2a'); px(g, cx - 34, 74, 70, 14, '#4a3020');
    px(g, cx - 20, 66, 14, 4, '#f0f0f0'); px(g, cx + 8, 64, 10, 6, '#2a2e5a'); px(g, cx + 9, 65, 8, 4, '#3fd0ff');
  } else if (id === 'games') {
    cityRoom(g, VW, '#3e1638', '#24102a', '#5e2456');
    signText(g, 'GAMES', Math.round(VW / 2 - 12), 6, '#ff3fd0'); cityGlow(g, Math.round(VW / 2 - 16), 3, 32, 11, '#ff3fd0', 0.25);
    const cols = ['#3fd0ff', '#ff4d6d', '#ffd23f', '#2ee66b', '#ff3fd0', '#a070ff'];
    const own = Math.min(40, S.games.length);
    for (let r = 0; r < 3; r++) {
      const sy = 22 + r * 20; px(g, 24, sy + 14, VW - 48, 2, '#7a4a2a');
      for (let x = 28, i = 0; x < VW - 30; x += 7, i++) { const n = r * 30 + i, mine = n < own; px(g, x, sy + 2, 5, 12, mine ? cols[n % 6] : '#4a2a4a'); if (mine) px(g, x + 1, sy + 4, 3, 2, '#ffffff'); }
    }
    cityPerson2(g, cx + 14, 90 + bob, 2, fr, -1, !talking);
    px(g, cx - 10, 78, 48, 4, '#a8683a'); px(g, cx - 10, 82, 48, 10, '#7a4a2a');
  } else if (id === 'mall') {
    cityRoom(g, VW, '#1d2350', '#121638', '#2e3880');
    const items = ['chairs', 'monitor2', 'lamp', 'coffee', 'lounge', 'rack'], n = items.length, step = Math.floor((VW - 40) / n);
    items.forEach((it, i) => {
      const x = 28 + i * step, own = S.items[it], top = 58;
      px(g, x - 2, top + 14, 20, 12, '#2e3880'); px(g, x - 2, top + 14, 20, 1, '#4a56b0');
      if (it === 'chairs') { px(g, x + 4, top, 8, 9, '#ff4d6d'); px(g, x + 3, top + 8, 10, 3, '#c03a52'); px(g, x + 7, top + 11, 2, 3, '#888'); }
      else if (it === 'monitor2') { px(g, x + 1, top + 1, 14, 9, '#20243a'); px(g, x + 2, top + 2, 12, 7, '#3fd0ff'); px(g, x + 7, top + 10, 2, 4, '#888'); }
      else if (it === 'lamp') { px(g, x + 4, top, 8, 4, '#ffd23f'); px(g, x + 7, top + 4, 2, 10, '#aaa'); cityGlow(g, x, top + 3, 16, 10, '#ffd23f', 0.15); }
      else if (it === 'coffee') { px(g, x + 3, top + 2, 10, 12, '#9aa0c0'); px(g, x + 6, top + 9, 4, 3, '#2a2a3a'); }
      else if (it === 'lounge') { px(g, x, top + 6, 16, 6, '#2ee66b'); px(g, x, top + 3, 3, 9, '#1fa850'); px(g, x + 13, top + 3, 3, 9, '#1fa850'); }
      else { px(g, x + 3, top - 2, 10, 16, '#2a2e44'); for (let k = 0; k < 4; k++) px(g, x + 5, top + k * 4, 6, 1, Math.sin(t * 5 + k + i) > 0 ? '#2ee66b' : '#1a4a2a'); }
      if (own) { px(g, x + 12, top - 6, 6, 6, '#2ee66b'); px(g, x + 13, top - 3, 1, 1, '#04210f'); px(g, x + 14, top - 2, 1, 1, '#04210f'); px(g, x + 15, top - 4, 1, 2, '#04210f'); }
    });
    cityLamp(g, Math.round(VW * 0.3), t); cityLamp(g, Math.round(VW * 0.7), t);
    cityPerson2(g, Math.round(VW * 0.5), 112 + bob, 3, fr, -1, !talking);
  } else if (id === 'arena') {
    cityRoom(g, VW, '#0a0a20', '#120a24', '#ff4d6d');
    const sw = Math.min(110, VW - 60), sx = Math.round((VW - sw) / 2) + 8;
    px(g, sx - 2, 6, sw + 4, 40, '#2a2a40'); px(g, sx, 8, sw, 36, '#05061a');
    const flash = Math.sin(t * 3) > 0;
    signText(g, 'VS', sx + Math.round(sw / 2) - 4, 22, flash ? '#ffd23f' : '#ff4d6d');
    px(g, sx + 8, 18, 18, 18, '#3fd0ff'); px(g, sx + sw - 26, 18, 18, 18, '#ff3fd0');
    cityGlow(g, sx, 8, sw, 36, '#ff4d6d', 0.08);
    // spotlights
    for (let k = 0; k < 2; k++) { const lx = k ? VW - 30 : 30, sway = Math.round(Math.sin(t * 1.3 + k * 2) * 18); g.globalCompositeOperation = 'lighter'; g.globalAlpha = 0.07; for (let i = 0; i < 70; i += 3) { const hw = 1 + Math.round(i * 0.25); px(g, lx + Math.round(sway * i / 70) - hw, i, hw * 2, 3, k ? '#ff3fd0' : '#3fd0ff'); } g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; }
    // the crowd cheering
    const wave = CT.t - CT.waveT < 1.8; for (let x = 24; x < VW - 4; x += 9) { const hop = wave ? Math.max(0, Math.round(Math.sin((CT.t - CT.waveT) * 9 - x * 0.08) * 6)) : Math.max(0, Math.round(Math.sin(t * 7 + x * 0.7) * 2)); px(g, x, 104 - hop, 7, 16, '#1a1030'); px(g, x + 1, 99 - hop, 5, 5, '#2a1a40'); if ((x / 9) % 3 < 1 && hop) px(g, x + 2, 94 - hop, 1, 4, '#ffd23f'); }
    cityPerson2(g, cx, 92 + bob, 2, fr, -1, !talking);
  }
  // the player, standing by the door
  cityPerson2(g, 30, 116, 0, 0, 1, true);
}

/* ============================================================
   ART 2 — the illustrated city (premium pixel-art backgrounds).
   Test mode only for now: open the game with ?art=2 (remembered).
   The backgrounds are pictures; people, cars, the cat, lights and
   every tap target are drawn on top in the same coordinates
   (fractions of the picture, so any screen size works).
   ============================================================ */
const ART = { imgs: {}, on: null };
function artOn() {
  if (ART.on === null) {
    try {
      const q = new URLSearchParams(location.search).get('art');
      const sp = window.Telegram && Telegram.WebApp && Telegram.WebApp.initDataUnsafe && Telegram.WebApp.initDataUnsafe.start_param;
      if (q === '2' || sp === 'art2') lsSet('od2_art2', '1');
      if (q === '1' || sp === 'art1') lsSet('od2_art2', '');
    } catch (e) {}
    ART.on = lsGet('od2_art2') === '1';
    try { document.body.classList.toggle('art2', ART.on); } catch (e) {} // the art skin in style.css hangs off body.art2
    if (ART.on) for (const k in ART_FACE_IMG) artImg(ART_FACE_IMG[k]); // event faces are ready before the first event opens
  }
  return ART.on;
}
function artImg(name) {
  let im = ART.imgs[name];
  if (!im) { im = ART.imgs[name] = new Image(); im.decoding = 'async'; im.src = 'art/' + name + '.webp'; }
  return im.complete && im.naturalWidth ? im : null;
}
// the street picture: shop fronts, the feet line and the road lanes as fractions of the picture
const ART_ST = {
  img: 'street_empty', feet: 0.737, lanes: [0.83, 0.91],
  b: { office: [0, 0.03], cafe: [0.034, 0.18], academy: [0.191, 0.338], deals: [0.349, 0.49], games: [0.498, 0.639], mall: [0.648, 0.788], arena: [0.8, 0.957] },
  signs: { cafe: [0.105, 0.47, '#ffb03f'], academy: [0.263, 0.47, '#3fd0ff'], deals: [0.42, 0.47, '#ff3fd0'], games: [0.568, 0.47, '#3fd0ff'], mall: [0.718, 0.47, '#ff3fd0'], arena: [0.878, 0.47, '#ffb03f'] },
  lamps: [0.028, 0.346, 0.495, 0.656, 0.802, 0.968], moon: [0.282, 0.09, 0.04], cat: 0.186, kiosk: 0.356,
};
// interiors: picture + tap targets [x0, y0, x1, y1] + where the host stands (null = drawn in the picture)
const ART_IN = {
  cafe: { img: 'cafe', door: [0.12, 0.38, 0.26, 0.8], you: [0.2, 0.95], host: null, talk: [0.43, 0.44, 0.51, 0.65],
    hot: { menu: [0.36, 0.19, 0.61, 0.43], machine: [0.51, 0.5, 0.67, 0.69], cat: [0.74, 0.58, 0.89, 0.72], cup: [0.87, 0.83, 0.93, 0.91] }, steam: [0.6, 0.45], cat: [0.81, 0.6] },
  academy: { img: 'academy', door: [0, 0.3, 0.06, 0.8], you: [0.1, 0.97], host: [0.55, 0.8, 3], who: 'prod', talk: [0.5, 0.48, 0.6, 0.82],
    hot: { board: [0.14, 0.2, 0.52, 0.65], shelf: [0.54, 0.22, 0.71, 0.69], desk: [0.08, 0.7, 0.9, 1] } },
  deals: { img: 'deals', door: [0, 0.3, 0.06, 0.9], you: [0.16, 0.98], host: [0.74, 0.9, 4], who: 'mkt', talk: [0.69, 0.5, 0.79, 0.9],
    hot: { board: [0.39, 0.07, 0.79, 0.54], window: [0.11, 0.12, 0.31, 0.58], laptop: [0.41, 0.49, 0.51, 0.62] } },
  games: { img: 'games', door: [0, 0.3, 0.06, 0.9], you: [0.12, 0.98], host: [0.74, 0.74, 2, 0.67], who: 'des', talk: [0.69, 0.4, 0.79, 0.68],
    hot: { shelf: [0.17, 0.3, 0.87, 0.67] } },
  mall: { img: 'mall', door: [0, 0.3, 0.05, 0.9], you: [0.1, 0.98], host: [0.47, 0.84, 3], who: 'cm', talk: [0.43, 0.5, 0.51, 0.84],
    // every thing on display is the real item: first tap shows it, a second tap buys it; bought ones glow green
    items: { coffee: [0.075, 0.55, 0.16, 0.72], chairs: [0.2, 0.51, 0.285, 0.78], monitor2: [0.285, 0.51, 0.42, 0.78], lamp: [0.455, 0.42, 0.6, 0.61], lounge: [0.51, 0.61, 0.69, 0.79], rack: [0.75, 0.48, 0.86, 0.77] },
    hot: { items: [0.07, 0.5, 0.88, 0.8] } },
  arena: { img: 'arena', door: [0, 0.3, 0.05, 0.75], you: [0.1, 0.99], host: null, talk: [0.45, 0.55, 0.55, 0.75],
    hot: { screen: [0.31, 0.22, 0.69, 0.6], crowd: [0, 0.76, 1, 1] } },
};
function artFit(cv, im, focusX) {
  const cw = cv.width, ch = cv.height, ar = im.naturalWidth / im.naturalHeight;
  let w = ch * ar, h = ch;
  if (w < cw) { w = cw; h = cw / ar; }
  const x = Math.max(cw - w, Math.min(0, cw / 2 - focusX * w)), y = Math.min(0, (ch - h) * 0.6);
  return { x, y, w, h };
}
function artSize(cv) {
  const dpr = Math.min(3, window.devicePixelRatio || 1), cw = Math.round((cv.clientWidth || 360) * dpr), ch = Math.round((cv.clientHeight || 200) * dpr);
  if (cv.width !== cw || cv.height !== ch) { cv.width = cw; cv.height = ch; }
}
function artSprite(g, F, nx, ny, u, fn) { g.save(); g.translate(Math.round(F.x + nx * F.w), Math.round(F.y + ny * F.h)); g.scale(u, u); fn(); g.restore(); }
// soft light: a radial gradient baked once per colour, then stretched (cheaper than a new gradient every frame)
const ART_GLOW = {};
function glowSpr(col) {
  let c = ART_GLOW[col];
  if (!c) {
    c = ART_GLOW[col] = document.createElement('canvas'); c.width = c.height = 64;
    const g = c.getContext('2d'), gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, col); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
  }
  return c;
}
function artGlow(g, x, y, r, col, a, op) {
  const sm = g.imageSmoothingEnabled;
  g.imageSmoothingEnabled = true; g.globalCompositeOperation = op || 'lighter'; g.globalAlpha = Math.max(0, Math.min(1, a));
  g.drawImage(glowSpr(col), x - r, y - r, r * 2, r * 2);
  g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; g.imageSmoothingEnabled = sm;
}
// the opposite: a soft dark spot (a lamp switched off)
function artShade(g, x, y, r, a) { artGlow(g, x, y, r, '#000000', a, 'source-over'); }
function artDraw(now) {
  const cv = UI.cityCv, g = UI.cityCtx;
  const pic = CT.inside ? ART_IN[CT.inside] : ART_ST;
  if (!pic) return false;
  const im = artImg(pic.img);
  if (!im) return false;
  cityInit();
  artSize(cv);
  const t = now / 1000, dt = Math.min(0.1, Math.max(0, t - (CT.last || t)));
  CT.last = t; CT.t += dt;
  if (CT.ax === undefined) { CT.ax = 0.012; CT.atx = null; }
  g.imageSmoothingEnabled = true;
  if (CT.inside) return artInside(g, cv, im, pic, t);
  // walking along the street (fractions of the picture per second)
  if (CT.atx !== null) {
    const d = CT.atx - CT.ax, step = (CT.aspd || 0.2) * dt;
    if (Math.abs(d) <= step) { CT.ax = CT.atx; CT.atx = null; artArrive(); } else { CT.dir = d > 0 ? 1 : -1; CT.ax += CT.dir * step; CT.walk += dt * 9; }
  }
  const F = artFit(cv, im, CT.ax);
  CT.F = F;
  g.drawImage(im, F.x, F.y, F.w, F.h);
  const u = F.h * 0.0056, feet = ART_ST.feet, ready = cityReady();
  // living neon: signs breathe, lamps glow, the wet road shimmers
  for (const k in ART_ST.signs) { const s = ART_ST.signs[k]; artGlow(g, F.x + s[0] * F.w, F.y + s[1] * F.h, F.h * 0.11, s[2], 0.16 + 0.1 * Math.sin(t * 2.4 + s[0] * 9)); }
  ART_ST.lamps.forEach((lx, i) => { const off = CT.t - CT.flickT < 1 && Math.sin(CT.t * 40 + i) > 0; artGlow(g, F.x + lx * F.w, F.y + 0.5 * F.h, F.h * 0.07, '#ffc070', off ? 0.05 : 0.22 + 0.04 * Math.sin(t * 7 + i)); });
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 14; i++) { const sx = ((i * 0.137 + t * 0.012 * (i % 2 ? 1 : -1)) % 1 + 1) % 1, sy = 0.79 + (i % 5) * 0.04; g.globalAlpha = 0.05 + 0.04 * Math.sin(t * 3 + i); g.fillStyle = i % 3 ? '#3fd0ff' : '#ff3fd0'; g.fillRect(F.x + sx * F.w, F.y + sy * F.h, F.w * 0.05, Math.max(1, F.h * 0.004)); }
  g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
  // "!" over doors where something waits
  for (const id in ready) {
    if (!ready[id] || !ART_ST.b[id]) continue;
    const b = ART_ST.b[id], bx = (b[0] + b[1]) / 2, by = 0.2 + Math.sin(t * 5) * 0.01;
    artSprite(g, F, bx, by, u * 1.3, () => { px(g, -3, -1, 7, 10, '#0a0a1e'); px(g, -2, 0, 5, 8, '#ffd23f'); px(g, 0, 1, 1, 4, '#0a0a1e'); px(g, 0, 6, 1, 1, '#0a0a1e'); });
  }
  // the kiosk, the cat, today's find
  artGlow(g, F.x + ART_ST.kiosk * F.w, F.y + (feet - 0.07) * F.h, F.h * 0.1, '#ff3fd0', 0.16 + 0.05 * Math.sin(t * 2));
  { const vim = artImg('vending'); if (vim) artPerson(g, F, vim, ART_ST.kiosk, feet, 0.16, false, 0); else artBlit(g, F, sprKiosk(t), ART_ST.kiosk, feet, 0.13); }
  { const pet = CT.t - CT.catT < 2.2, tail = Math.round(Math.sin(t * (pet ? 9 : 2)) * 1.5);
    const cim = artImg(pet ? 'cat_stand' : 'cat_sit');
    if (cim) artPerson(g, F, cim, ART_ST.cat, feet, pet ? 0.065 : 0.07, pet && Math.sin(CT.t * 3) > 0, 0); else artBlit(g, F, sprCat(pet), ART_ST.cat, feet, 0.05);
    if (pet) for (let k = 0; k < 3; k++) { const hy = feet - 0.07 - (((CT.t - CT.catT) * 0.05 + k * 0.025) % 0.08); g.fillStyle = '#ff4d6d'; g.fillRect(F.x + (ART_ST.cat + (k - 1) * 0.008) * F.w, F.y + hy * F.h, u * 2, u * 2); } }
  if (cityFindReady()) { const fx = artFindX(), tw = Math.sin(t * 6) > 0;
    artGlow(g, F.x + fx * F.w, F.y + (feet + 0.012) * F.h, F.h * 0.03, '#ffd23f', 0.5);
    const coin = artImg('coin');
    if (coin) { g.save(); g.globalAlpha = 0.85 + 0.15 * Math.sin(t * 6); artPerson(g, F, coin, fx, feet + 0.015 + Math.sin(t * 3) * 0.004, 0.04, Math.sin(t * 2) > 0.9, 0); g.restore(); }
    else artSprite(g, F, fx, feet + 0.012, u, () => { px(g, -1, -1, 3, 3, '#ffd23f'); px(g, 0, 0, 1, 1, '#fff8c0'); if (tw) { px(g, 0, -4, 1, 2, '#fff8c0'); px(g, -4, 0, 2, 1, '#fff8c0'); px(g, 3, 0, 2, 1, '#fff8c0'); } }); }
  // cars with headlights on the wet road
  if (!CT.acars) CT.acars = [{ x: 0.1, v: 0.09, col: '#ff4d6d', lane: 0 }, { x: 0.7, v: -0.12, col: '#3fd0ff', lane: 1 }];
  for (const c of CT.acars) {
    c.x += c.v * dt; if (c.x > 1.15) c.x = -0.15; if (c.x < -0.15) c.x = 1.15;
    const cx = F.x + c.x * F.w, cy = F.y + ART_ST.lanes[c.lane] * F.h;
    artGlow(g, cx + (c.v > 0 ? 1 : -1) * F.h * 0.12, cy, F.h * 0.09, '#fff2c0', 0.25);
    const carim = artImg(c.lane ? 'car_cyan' : 'car_red');
    if (carim) artPerson(g, F, carim, c.x, ART_ST.lanes[c.lane] + 0.03, 0.085, c.v < 0, 0); else artBlit(g, F, sprCar(c.col), c.x, ART_ST.lanes[c.lane], 0.075, c.v < 0);
    artGlow(g, cx - (c.v > 0 ? 1 : -1) * F.h * 0.07, cy - F.h * 0.03, F.h * 0.025, '#ff2a40', 0.35);
  }
  // passers-by and the player
  // each passer-by has a look, a size and a pace; one who walks off the edge comes back as someone else
  if (!CT.anpcs) CT.anpcs = [0, 1, 2, 3, 4].map((i) => ({ x: 0.08 + i * 0.21, v: (i % 2 ? -1 : 1) * (0.016 + (i % 3) * 0.006), look: i * 2 + (i > 2 ? 1 : 0), sz: 0.13 + (i % 3) * 0.007, pause: 0, ph: i * 0.37 }));
  for (const n of CT.anpcs) {
    if (n.pause > 0) n.pause -= dt; else { n.x += n.v * dt; n.ph += dt * Math.abs(n.v) * 52; if (Math.random() < dt * 0.06) n.pause = 1 + Math.random() * 2.5; }
    if (n.x > 1.04 || n.x < -0.04) { n.x = n.x > 1 ? -0.03 : 1.03; n.look = (n.look + 5) % ART_LOOKS.length; n.sz = 0.128 + Math.random() * 0.02; n.v = Math.sign(n.v) * (0.014 + Math.random() * 0.014); }
    const nim = artLook(n.look);
    if (nim) artPerson(g, F, n.pause > 0 ? nim : artWalk(nim, n.ph), n.x, feet - (n.look % 3) * 0.004, n.sz, n.v > 0, 0);
    else artBlit(g, F, sprPerson(1 + (n.look % 3), Math.floor(CT.t * 7 + n.look), n.pause > 0), n.x, feet, 0.13, n.v < 0);
  }
  { const yim = artImg('stand_you'), moving = CT.atx !== null;
    if (yim) artPerson(g, F, moving ? artWalk(yim, CT.walk * 0.19) : yim, CT.ax, feet + 0.006, 0.145, CT.dir > 0, 0);
    else artBlit(g, F, sprPerson(0, Math.floor(CT.walk), CT.atx === null), CT.ax, feet + 0.006, 0.135, CT.dir < 0); }
  return true;
}
// an AI-drawn character (picture with alpha) standing on (nx, ny); h = height as a fraction of the picture
function artPerson(g, F, im, nx, ny, hFrac, flip, bob, tilt) {
  const h = hFrac * F.h, w = h * (im.naturalWidth || im.width) / (im.naturalHeight || im.height), X = F.x + nx * F.w, Y = F.y + ny * F.h;
  g.save(); g.globalAlpha = 0.35; g.fillStyle = '#000'; g.beginPath(); g.ellipse(X, Y, w * 0.4, h * 0.035, 0, 0, Math.PI * 2); g.fill(); g.restore();
  g.save(); g.imageSmoothingEnabled = true; g.translate(X, Y + (bob || 0)); if (tilt) g.rotate(tilt); if (flip) g.scale(-1, 1);
  g.drawImage(im, -w / 2, -h, w, h); g.restore();
}
// a walk cycle made from one standing picture (8 frames, cached): each leg swings from the hip
// (sheared its own way), the stepping foot lifts, the body dips when the legs are apart
const ART_WALK = new Map();
function artWalk(im, ph) {
  let fr = ART_WALK.get(im);
  if (!fr) {
    fr = []; ART_WALK.set(im, fr);
    const w = im.naturalWidth || im.width, h = im.naturalHeight || im.height, hip = Math.round(h * 0.6), mid = Math.round(w * 0.5), pad = Math.round(w * 0.12), N = 8;
    for (let i = 0; i < N; i++) {
      const c = document.createElement('canvas'); c.width = w + pad * 2; c.height = h;
      const g = c.getContext('2d'), a = Math.sin((i / N) * Math.PI * 2), b = Math.cos((i / N) * Math.PI * 2);
      const dip = Math.round(Math.abs(a) * h * 0.012), legH = h - hip;
      g.imageSmoothingEnabled = false;
      // legs apart twice per cycle; the leg that steps through is lifted and drawn behind, then the body over the hip seam
      const legs = [[0, mid, -1], [mid, w - mid, 1]];
      if (a < 0) legs.reverse();
      legs.forEach(([sx, sw, sgn], n) => {
        const k = 0.3 * Math.abs(a) * sgn, lift = n === 0 ? Math.abs(b) * h * 0.03 : 0;
        g.setTransform(1, 0, k, (legH - lift) / legH, pad - k * (hip + dip), hip + dip - (hip + dip) * (legH - lift) / legH);
        g.drawImage(im, sx, hip, sw, legH, sx, hip + dip, sw, legH - dip);
      });
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.drawImage(im, 0, 0, w, hip + 3, pad, dip, w, hip + 3);
      fr.push(c);
    }
  }
  return fr[Math.floor(((ph % 1) + 1) % 1 * fr.length) % fr.length];
}
// passers-by: the drawn characters with their clothes recoloured (skin, hair and dark tones stay), so the street is not
// full of your own staff. One canvas per look, made once.
// '@name' is a drawn passer-by (walk_<name>.webp); the rest are recoloured staff
const ART_LOOKS = [['@grandpa'], ['qa', 0], ['@biz'], ['prog', 110], ['@skater'], ['cm', 160], ['@courier'], ['mkt', 200], ['@rain'], ['des', 150], ['@leather'], ['snd', 60], ['@npc_journalist'], ['art', 90], ['@npc_streamer'], ['qa', 240]];
const ART_TINT = {};
function artLook(i) {
  const [role, hue] = ART_LOOKS[i % ART_LOOKS.length];
  if (role[0] === '@') return artImg(role.startsWith('@npc_') ? role.slice(1) : 'walk_' + role.slice(1));
  const key = role + hue, im = artImg('stand_' + role);
  if (!im) return null;
  if (!hue) return im;
  if (ART_TINT[key]) return ART_TINT[key];
  const c = document.createElement('canvas'); c.width = im.naturalWidth; c.height = im.naturalHeight;
  const g = c.getContext('2d'); g.drawImage(im, 0, 0);
  try {
    const d = g.getImageData(0, 0, c.width, c.height), p = d.data, top = Math.round(c.height * 0.3) * c.width * 4, hip = Math.round(c.height * 0.6) * c.width * 4; // the top: below the face, above the jeans
    for (let j = top; j < hip; j += 4) {
      const r = p[j] / 255, gg = p[j + 1] / 255, bb = p[j + 2] / 255, mx = Math.max(r, gg, bb), mn = Math.min(r, gg, bb), l = (mx + mn) / 2, dd = mx - mn;
      if (p[j + 3] < 10 || dd < 0.12 || l < 0.12) continue;
      let H = mx === r ? ((gg - bb) / dd) % 6 : mx === gg ? (bb - r) / dd + 2 : (r - gg) / dd + 4; H *= 60; if (H < 0) H += 360;
      if (H < 55 || H > 340) continue; // skin, lips, brown and orange tones stay
      H = (H + hue) % 360;
      const sat = dd / (1 - Math.abs(2 * l - 1) || 1), C = (1 - Math.abs(2 * l - 1)) * sat, X = C * (1 - Math.abs(((H / 60) % 2) - 1)), m = l - C / 2;
      const [R, G, B] = H < 60 ? [C, X, 0] : H < 120 ? [X, C, 0] : H < 180 ? [0, C, X] : H < 240 ? [0, X, C] : H < 300 ? [X, 0, C] : [C, 0, X];
      p[j] = (R + m) * 255; p[j + 1] = (G + m) * 255; p[j + 2] = (B + m) * 255;
    }
    g.putImageData(d, 0, 0);
  } catch (e) { return im; }
  return (ART_TINT[key] = c);
}
function artFindX() { const n = cityS().fndN || 0; return 0.06 + ((n * 0.397 + 0.13) % 0.88); }
function artInside(g, cv, im, pic, t) {
  const F = artFit(cv, im, 0.5);
  CT.F = F;
  g.drawImage(im, F.x, F.y, F.w, F.h);
  const u = F.h * 0.009, talking = CT.talkT && CT.t - CT.talkT < 1.2;
  // a warm breathing light over the whole room
  artGlow(g, F.x + F.w * 0.5, F.y + F.h * 0.15, F.h * 0.8, '#ffc070', 0.05 + 0.02 * Math.sin(t * 1.5));
  if (pic.steam) { const sx = F.x + pic.steam[0] * F.w, sy = F.y + pic.steam[1] * F.h, burst = CT.t - CT.steamT < 1.6;
    for (let i = 0; i < (burst ? 12 : 4); i++) { const k = ((t * (burst ? 40 : 14)) + i * 9) % 60; g.globalAlpha = Math.max(0, 0.45 - k / 130); g.fillStyle = '#ffffff'; g.fillRect(sx + Math.sin(i * 2.1 + k * 0.1) * (2 + k / 5) * u * 0.4, sy - k * u * 0.35, u * 1.2, u * 1.2); }
    g.globalAlpha = 1; }
  if (pic.cat && CT.t - CT.catT < 2) for (let k = 0; k < 3; k++) { const hy = pic.cat[1] - ((CT.t - CT.catT) * 0.06 + k * 0.03) % 0.12; g.fillStyle = '#ff4d6d'; g.fillRect(F.x + (pic.cat[0] + k * 0.02) * F.w, F.y + hy * F.h, u * 1.4, u * 1.4); }
  if (pic.host) {
    // behind a counter: only what is above its top edge (pic.host[3]) is visible
    if (pic.host[3]) { g.save(); g.beginPath(); g.rect(0, 0, cv.width, F.y + pic.host[3] * F.h); g.clip(); }
    const him = pic.who && artImg('stand_' + pic.who);
    if (him) artPerson(g, F, him, pic.host[0], pic.host[1], 0.34, false, talking ? Math.sin(CT.t * 14) * F.h * 0.004 : Math.sin(t * 1.6) * F.h * 0.002);
    else artBlit(g, F, sprPerson(pic.host[2], Math.floor(CT.t * 6), !talking), pic.host[0], pic.host[1] + (talking ? Math.sin(CT.t * 14) * 0.004 : 0), 0.3, true);
    if (pic.host[3]) g.restore();
  }
  if (pic.items) for (const id in pic.items) {
    const b = pic.items[id], cx = F.x + (b[0] + b[2]) / 2 * F.w, cy = F.y + (b[1] + b[3]) / 2 * F.h, sel = CT.mallSel === id && CT.t - CT.mallT < 5;
    if (S.items[id]) { artGlow(g, cx, F.y + b[3] * F.h, F.h * 0.08, '#5dff9c', 0.22); artSprite(g, F, (b[0] + b[2]) / 2, b[1] + 0.02, u * 0.7, () => { px(g, -4, -4, 9, 9, '#0a2a14'); px(g, -3, -3, 7, 7, '#5dff9c'); px(g, -2, 0, 1, 1, '#0a2a14'); px(g, -1, 1, 1, 1, '#0a2a14'); px(g, 0, 0, 1, 1, '#0a2a14'); px(g, 1, -1, 1, 1, '#0a2a14'); px(g, 2, -2, 1, 1, '#0a2a14'); }); }
    else if (sel) artGlow(g, cx, cy, F.h * 0.16, '#ffd23f', 0.3 + 0.12 * Math.sin(t * 8));
    else if (S.cash >= ITEMS.find((x) => x.id === id).price) artGlow(g, cx, F.y + b[3] * F.h, F.h * 0.06, '#3fd0ff', 0.12 + 0.08 * Math.sin(t * 3 + cx));
  }
  if (talking) { const tk = pic.talk; artGlow(g, F.x + (tk[0] + tk[2]) / 2 * F.w, F.y + tk[1] * F.h, F.h * 0.06, '#ffffff', 0.25); }
  { const yim = artImg('stand_you'); if (yim) artPerson(g, F, yim, pic.you[0], pic.you[1], 0.36, true, Math.sin(t * 1.8) * F.h * 0.002); else artBlit(g, F, sprPerson(0, 0, true), pic.you[0], pic.you[1], 0.32, false); }
  return true;
}
// taps on the illustrated city; returns false to fall back to the classic handler
function artTap(e) {
  const cv = UI.cityCv, F = CT.F;
  if (!artOn() || !F) return false;
  const pic = CT.inside ? ART_IN[CT.inside] : ART_ST;
  if (!pic || !artImg(pic.img)) return false;
  const r = cv.getBoundingClientRect(), k = cv.width / r.width;
  const nx = ((e.clientX - r.left) * k - F.x) / F.w, ny = ((e.clientY - r.top) * k - F.y) / F.h;
  const inR = (b) => nx >= b[0] && nx <= b[2] && ny >= b[1] && ny <= b[3];
  if (CT.inside) {
    if (inR(pic.door) || (e.clientX - r.left) / r.width < 0.1) { cityAct('out'); return true; }
    if (pic.items) for (const id in pic.items) if (inR(pic.items[id])) { artMallItem(id, e); return true; }
    for (const h in pic.hot) if (inR(pic.hot[h])) { cityPoke(CT.inside + '.' + h, e); return true; }
    cityTalk(); return true;
  }
  const feet = ART_ST.feet;
  if (cityFindReady() && Math.abs(nx - artFindX()) < 0.025 && Math.abs(ny - feet) < 0.06) { cityPick(e); return true; }
  if (Math.abs(nx - ART_ST.cat) < 0.025 && ny > feet - 0.07 && ny < feet + 0.04) { CT.catT = CT.t; sfx('meow'); cityPoke('st.cat', e, '❤️'); return true; }
  if (Math.abs(nx - ART_ST.kiosk) < 0.02 && ny > feet - 0.13 && ny < feet + 0.02) { cityPoke('st.kiosk', e, '📰'); return true; }
  const m = ART_ST.moon; if (Math.hypot(nx - m[0], (ny - m[1]) * F.h / F.w) < m[2]) { cityPoke('st.moon', e, '🌙'); return true; }
  const npc = (CT.anpcs || []).find((n) => Math.abs(nx - n.x) < 0.02 && ny > feet - 0.12 && ny < feet + 0.02);
  if (npc) { npc.pause = 2.5; npc.v = -npc.v; cityPoke('st.npc', e, '💬'); return true; }
  const car = (CT.acars || []).find((c) => Math.abs(nx - c.x) < 0.05 && Math.abs(ny - ART_ST.lanes[c.lane]) < 0.05);
  if (car) { try { beep(620, 0.09, 'square', 0.05); beep(520, 0.12, 'square', 0.05, 0.12); } catch (x) {} cityPoke('st.car', e, '📯'); return true; }
  if (ART_ST.lamps.some((lx) => Math.abs(nx - lx) < 0.015) && ny < feet) { CT.flickT = CT.t; sfx('blip'); return true; }
  for (const id in ART_ST.b) { const b = ART_ST.b[id]; if (nx >= b[0] && nx <= b[1] && ny < feet + 0.05) { artGo(id); return true; } }
  CT.atx = Math.max(0.01, Math.min(0.99, nx)); CT.aspd = Math.max(0.15, Math.abs(CT.atx - CT.ax) / 1.4); CT.sel = null; CT.street = false;
  return true;
}
// a thing in the Mall: what it is and costs; tapped again within a few seconds → bought (the usual buy action)
function artMallItem(id, e) {
  const it = ITEMS.find((x) => x.id === id), name = L('item.' + id + '.name');
  if (!it) return;
  if (!S.items[id] && CT.mallSel === id && CT.t - CT.mallT < 5) {
    CT.mallSel = null;
    ACT.buy(id);
    if (S.items[id]) { CT.say = name + ' — ' + L('city.x_mall_own'); CT.sayN++; confettiSmall(); }
    render(true);
    return;
  }
  cityPoke('mall.items', e, S.items[id] ? '✅' : '🛍️');
  if (S.items[id]) { CT.mallSel = null; CT.say = name + ' — ' + L('city.x_mall_own'); }
  else { CT.mallSel = id; CT.mallT = CT.t; CT.sayN++; CT.say = L('city.x_mall_buy', { item: name, perk: L('item.' + id + '.perk'), price: money(it.price) }); }
  render(true);
}
function artGo(id) {
  const b = ART_ST.b[id];
  if (!b) return;
  CT.sel = null; CT.street = false;
  if (CT.inside) { CT.inside = null; CT.say = ''; }
  CT.atx = (b[0] + b[1]) / 2;
  CT.aspd = Math.max(0.15, Math.abs(CT.atx - CT.ax) / 1.4);
  sfx('blip');
  if (Math.abs(CT.atx - CT.ax) < 0.003) { CT.atx = null; artArrive(); }
  render(true);
}
function artArrive() {
  const id = Object.keys(ART_ST.b).find((k) => CT.ax >= ART_ST.b[k][0] - 0.005 && CT.ax <= ART_ST.b[k][1] + 0.005);
  if (!id) return;
  if (id === 'office') { go('office'); return; }
  CT.sel = id;
  // buildings without an illustrated interior yet keep the classic room
  cityEnter(id);
  sfx('pop'); haptic('light');
  render(true);
  setTimeout(() => { const p = $('#citypanel'); if (p) p.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }, 60);
}

/* ---------- ART 2 sprites: detailed pixel people, cars, kiosk, cat (built once, cached) ---------- */
const SPR = {};
// draw a sprite into an offscreen canvas, then add a dark outline around it
function sprMake(key, w, h, draw) {
  if (SPR[key]) return SPR[key];
  const c = document.createElement('canvas'); c.width = w + 2; c.height = h + 2;
  const g = c.getContext('2d');
  g.translate(1, 1); draw(g); g.setTransform(1, 0, 0, 1, 0, 0);
  const d = g.getImageData(0, 0, c.width, c.height), a = d.data, W = c.width, H = c.height, o = new Uint8ClampedArray(a);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 4;
    if (a[i + 3]) continue;
    const n = (dx, dy) => { const xx = x + dx, yy = y + dy; return xx >= 0 && yy >= 0 && xx < W && yy < H && a[((yy * W + xx) * 4) + 3] > 0; };
    if (n(1, 0) || n(-1, 0) || n(0, 1) || n(0, -1)) { o[i] = 16; o[i + 1] = 10; o[i + 2] = 34; o[i + 3] = 235; }
  }
  g.putImageData(new ImageData(o, W, H), 0, 0);
  return (SPR[key] = c);
}
const PH = { w: 16, h: 34 };
const LOOK2 = [
  { skin: '#a8714a', skinD: '#7e4f32', hair: '#1c1220', hairL: '#3a2a40', top: '#2f55e0', topD: '#1e3aa8', topL: '#5a7cff', pants: '#232a4e', pantsD: '#151a33', shoe: '#e8ecff', acc: '#3fd0ff' }, // the player
  { skin: '#f2cda6', skinD: '#d2a27c', hair: '#c9622a', hairL: '#e8844a', top: '#d0468a', topD: '#9c2e66', topL: '#f070ac', pants: '#2c2c52', pantsD: '#1c1c38', shoe: '#2a2a3a' },
  { skin: '#c99a70', skinD: '#a07450', hair: '#2a1a10', hairL: '#4a3220', top: '#2e9a5a', topD: '#1f6e40', topL: '#4ccf80', pants: '#3a2c1c', pantsD: '#261c10', shoe: '#e0d0b0' },
  { skin: '#ecbe96', skinD: '#c89670', hair: '#e8e0d0', hairL: '#ffffff', top: '#7a4ad0', topD: '#5530a0', topL: '#a070ff', pants: '#1c1c2c', pantsD: '#10101c', shoe: '#2a2a3a' },
  { skin: '#d8a880', skinD: '#b08058', hair: '#3a2a1a', hairL: '#5a4230', top: '#2a2c44', topD: '#1a1c30', topL: '#44486a', pants: '#14141e', pantsD: '#0a0a12', shoe: '#5a3a20', tie: '#ff4d6d' },
];
function sprPerson(look, frame, still) {
  const f = still ? 0 : frame & 3;
  return sprMake('p' + look + f + (still ? 's' : ''), PH.w, PH.h, (g) => {
    const L0 = LOOK2[look], P = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
    const sw = [0, 2, 0, -2][f], bob = f % 2 ? -1 : 0;
    // legs + shoes (walk cycle)
    P(4 + (sw > 0 ? 1 : 0), 22 + bob, 3, 9, L0.pants); P(4 + (sw > 0 ? 1 : 0), 22 + bob, 1, 9, L0.pantsD);
    P(9 - (sw > 0 ? 1 : 0), 22 + bob, 3, 9, L0.pants); P(11 - (sw > 0 ? 1 : 0), 22 + bob, 1, 9, L0.pantsD);
    P(3 + Math.max(0, sw), 31, 5, 2, L0.shoe); P(8 + Math.min(0, sw) + (sw < 0 ? 0 : 1), 31, 5, 2, L0.shoe);
    // torso with light from the left
    P(3, 12 + bob, 10, 11, L0.top); P(3, 12 + bob, 2, 11, L0.topL); P(11, 12 + bob, 2, 11, L0.topD); P(3, 21 + bob, 10, 2, L0.topD);
    P(6, 12 + bob, 4, 2, L0.topD); // collar
    if (L0.tie) { P(7, 13 + bob, 2, 7, L0.tie); P(7, 12 + bob, 2, 1, '#ffffff'); }
    if (look === 0) P(7, 15 + bob, 2, 6, L0.topD); // hoodie zip
    // arms swinging + hands
    P(1, 13 + bob + (sw > 0 ? 1 : 0), 2, 8, L0.topD); P(13, 13 + bob - (sw > 0 ? 1 : 0), 2, 8, L0.top);
    P(1, 21 + bob + (sw > 0 ? 1 : 0), 2, 2, L0.skin); P(13, 21 + bob - (sw > 0 ? 1 : 0), 2, 2, L0.skinD);
    // neck + head
    P(6, 10 + bob, 4, 2, L0.skinD);
    P(4, 2 + bob, 8, 9, L0.skin); P(10, 3 + bob, 2, 8, L0.skinD); P(5, 10 + bob, 6, 1, L0.skinD);
    // hair with a highlight
    P(3, 1 + bob, 10, 3, L0.hair); P(3, 3 + bob, 2, 4, L0.hair); P(11, 3 + bob, 2, 3, L0.hair); P(5, 1 + bob, 4, 1, L0.hairL);
    // eyes, cheeks, mouth
    P(6, 6 + bob, 1, 2, '#0a0a18'); P(9, 6 + bob, 1, 2, '#0a0a18'); P(6, 6 + bob, 1, 1, '#ffffff');
    P(5, 8 + bob, 1, 1, 'rgba(255,120,120,.5)'); P(10, 8 + bob, 1, 1, 'rgba(255,120,120,.5)'); P(7, 9 + bob, 2, 1, L0.skinD);
    if (L0.acc) { P(2, 4 + bob, 2, 4, L0.acc); P(12, 4 + bob, 2, 4, L0.acc); P(3, 0 + bob, 10, 1, L0.acc); } // headphones
  });
}
function sprCar(col) {
  return sprMake('car' + col, 40, 15, (g) => {
    const P = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
    P(2, 6, 36, 6, col); P(2, 6, 36, 1, 'rgba(255,255,255,.35)'); P(2, 11, 36, 1, 'rgba(0,0,0,.35)');
    P(10, 1, 18, 5, col); P(12, 2, 6, 4, '#1a2a4a'); P(19, 2, 7, 4, '#1a2a4a'); P(13, 2, 2, 1, '#6a8ac0'); P(20, 2, 2, 1, '#6a8ac0');
    P(37, 7, 2, 2, '#fff8e0'); P(1, 7, 2, 2, '#ff2a40');
    for (const wx of [7, 28]) { P(wx, 10, 6, 5, '#0a0a12'); P(wx + 2, 12, 2, 1, '#8a8aa0'); }
  });
}
function sprKiosk(t) {
  const f = Math.floor(t * 2) % 3;
  return sprMake('kiosk' + f, 14, 28, (g) => {
    const P = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
    P(0, 0, 14, 28, '#2a3a9a'); P(0, 0, 2, 28, '#4a5ad0'); P(12, 0, 2, 28, '#1a2470'); P(0, 0, 14, 3, '#3fd0ff');
    P(2, 5, 9, 12, '#0a1030'); for (let r = 0; r < 3; r++) for (let k = 0; k < 2; k++) P(3 + k * 4, 6 + r * 4, 3, 3, ['#ff4d6d', '#ffd23f', '#2ee66b'][(r + k + f) % 3]);
    P(3, 20, 8, 3, '#0a0a1e'); P(11, 8, 1, 6, '#ffd23f');
  });
}
function sprCat(up) {
  return sprMake('cat' + (up ? 1 : 0), 16, 12, (g) => {
    const P = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); };
    const y0 = up ? 0 : 3;
    P(2, y0 + 5, 9, 6 - (up ? 0 : 1), '#ff9a3c'); P(2, y0 + 5, 9, 1, '#ffb866'); P(4, y0 + 7, 1, 3, '#d8742a'); P(7, y0 + 7, 1, 3, '#d8742a');
    P(10, y0 + 1, 5, 5, '#ff9a3c'); P(10, y0, 1, 1, '#ff9a3c'); P(14, y0, 1, 1, '#ff9a3c'); P(12, y0 + 3, 1, 1, '#0a0a18'); P(14, y0 + 3, 1, 1, '#0a0a18'); P(13, y0 + 4, 1, 1, '#ff6a8a');
    P(0, y0 + 4, 2, 2, '#ff9a3c'); P(0, y0 + 2, 1, 2, '#ff9a3c');
  });
}
// blit a sprite standing on (nx, ny) of the picture, h = its height as a fraction of the picture
function artBlit(g, F, spr, nx, ny, hFrac, flip) {
  const s = (hFrac * F.h) / spr.height, w = spr.width * s, h = spr.height * s;
  const X = Math.round(F.x + nx * F.w), Y = Math.round(F.y + ny * F.h);
  g.save(); g.imageSmoothingEnabled = false; g.translate(X, Y); if (flip) g.scale(-1, 1);
  g.globalAlpha = 0.35; g.fillStyle = '#000'; g.beginPath(); g.ellipse(0, 0, w * 0.42, Math.max(1.5, h * 0.035), 0, 0, Math.PI * 2); g.fill(); g.globalAlpha = 1;
  g.drawImage(spr, -w / 2, -h, w, h); g.restore();
}
