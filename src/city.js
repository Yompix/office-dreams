/* ============================================================
   OFFICE DREAMS — the city outside the office.
   A side-scrolling neon street: the player walks (tap a building) to
   the coffee shop, the academy, the contract board and the game store.
   Every place does something real for the studio, on game-time timers
   (engine: CITY / coffeeBreak / startTraining / takeContract / storePromo).
   ============================================================ */
const CITY_H = 120; // logical pixels; the canvas is scaled up crisp
const CITY_W = 432; // world width
const CITY_B = [
  { id: 'office', x: 4, w: 70, h: 94, wall: '#181c58', trim: '#2b3290', sign: 'OFFICE', neon: '#3fd0ff' },
  { id: 'cafe', x: 90, w: 64, h: 62, wall: '#3a1d48', trim: '#5a2e6a', sign: 'CAFE', neon: '#ffb03f' },
  { id: 'academy', x: 170, w: 82, h: 86, wall: '#16384a', trim: '#22566e', sign: 'ACADEMY', neon: '#2ee66b' },
  { id: 'deals', x: 268, w: 66, h: 72, wall: '#3a2a18', trim: '#5a4226', sign: 'DEALS', neon: '#ffd23f' },
  { id: 'games', x: 350, w: 78, h: 66, wall: '#3e1638', trim: '#5e2456', sign: 'GAMES', neon: '#ff3fd0' },
];
const CITY_SIDEWALK = 98;
const CT = { x: 39, tx: null, dir: 1, walk: 0, cam: 0, sel: null, t: 0, npcs: null, cars: null, sky: null, last: 0 };

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
  };
}
function citySig() { const r = cityReady(), c = cityS(); return (r.cafe ? 1 : 0) + '' + (r.academy ? 1 : 0) + (r.deals ? 1 : 0) + (r.games ? 1 : 0) + (c.trn ? 't' : '') + (c.ctr ? 'c' : ''); }

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
];
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
  cityInit();
  const cw = cv.clientWidth || 360, ch = cv.clientHeight || 240;
  const VW = Math.max(120, Math.min(CITY_W, Math.round(CITY_H * cw / ch)));
  if (cv.width !== VW || cv.height !== CITY_H) { cv.width = VW; cv.height = CITY_H; }
  const t = now / 1000, dt = Math.min(0.1, Math.max(0, t - (CT.last || t)));
  CT.last = t; CT.t += dt;
  // walking
  if (CT.tx !== null) {
    const d = CT.tx - CT.x, step = 46 * dt;
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
    g.globalAlpha = 0.07 + 0.02 * Math.sin(t * 8 + lx);
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
  const r = cv.getBoundingClientRect();
  const wx = ((e.clientX - r.left) / r.width) * cv.width + Math.round(CT.cam);
  const b = CITY_B.find((x) => wx >= x.x - 4 && wx <= x.x + x.w + 4);
  if (b) cityGo(b.id); else { CT.tx = Math.max(6, Math.min(CITY_W - 6, wx)); CT.sel = null; }
}
function cityGo(id) {
  const b = CITY_B.find((x) => x.id === id);
  if (!b) return;
  CT.sel = null;
  CT.tx = b.x + b.w / 2;
  sfx('blip');
  if (Math.abs(CT.tx - CT.x) < 1) { CT.tx = null; cityArrive(); }
  render(true);
}
function cityArrive() {
  const b = CITY_B.find((x) => CT.x >= x.x && CT.x <= x.x + x.w);
  if (!b) return;
  if (b.id === 'office') { go('office'); return; }
  CT.sel = b.id;
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
      ${left > 0 ? `<button class="btn big wide" disabled>${L('city.wait')} <span data-t="cityleft:cof"></span></button>` : `<button class="btn big glow wide" data-act="city:cof">☕ ${L('city.cafe_btn')}</button>`}`;
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
  return '';
}
function tabCity() {
  const r = cityReady(), c = cityS();
  const chips = CITY_B.filter((b) => b.id !== 'office').map((b) => {
    const st = b.id === 'academy' && c.trn ? `<b class="px" data-t="cityleft:trn"></b>` : b.id === 'deals' && c.ctr ? `<b class="px" data-t="cityleft:ctr"></b>` : r[b.id] ? '<i class="dot"></i>' : '';
    return `<button class="ct-chip ${CT.sel === b.id ? 'on' : ''}" data-act="city:go:${b.id}" style="--n:${b.neon}"><span>${L('city.ico_' + b.id)}</span><small>${L('city.' + b.id)}</small>${st}</button>`;
  }).join('');
  return `<section class="panel cityp">
    <div class="ct-head"><button class="btn ghost sm" data-act="city:home">‹ ${L('city.back')}</button><h2 class="h2">🌆 ${L('city.title')}</h2></div>
    <div class="ct-scene"><canvas id="citycv" width="240" height="${CITY_H}" aria-label="${esc(L('city.title'))}"></canvas>${CT.sel ? '' : `<span class="ct-tip">${L('city.tip')}</span>`}</div>
    <div class="ct-chips">${chips}</div>
  </section>
  ${CT.sel ? `<section class="panel ct-panel" id="citypanel">${cityPanel(CT.sel)}</section>` : ''}
  <p class="hint pad">${L('city.hint')}</p>`;
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
  return `<button class="panel citybn ${items.length ? 'hot' : ''}" data-act="city:open"><span class="cb-ico">🌆</span><span class="cb-t"><b>${L('city.go')}</b><small>${items.length ? items.join(' · ') : L('city.go_s')}</small></span><b class="chev">›</b></button>`;
}
function cityAct(what, arg) {
  if (what === 'open') { go('city'); return; }
  if (what === 'home') { go('office'); return; }
  if (what === 'go') { cityGo(arg); return; }
  if (what === 'make') { const k = cityS().ctr; if (k) { S.sel.genre = k.genre; if (tierUnlocked(S, k.tier)) S.sel.tier = k.tier; } go('studio', 'games'); return; }
  const map = { cof: 'city.ok_cof', trn: 'city.ok_trn', trd: '', ctr: 'city.ok_ctr', ctx: '', prm: 'city.ok_prm' };
  if (!(what in map)) return;
  const r = arg ? netAct(what, arg) : netAct(what);
  if (r && r.ok) { if (map[what]) toast(L(map[what]), 'good'); haptic('medium'); if (what === 'cof' || what === 'prm') confettiSmall(); } else errToast(r && r.err);
  render(true);
}
