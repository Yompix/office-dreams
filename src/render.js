/* ============================================================
   OFFICE DREAMS — pixel renderer (pure buffer code, no DOM)
   Isometric office scene, portraits, game covers, tower.
   ============================================================ */

const clampN = (v, a, b) => (v < a ? a : v > b ? b : v);
const _hexCache = {};
function C(h) {
  let v = _hexCache[h];
  if (v === undefined) {
    const n = parseInt(h.slice(1), 16);
    v = _hexCache[h] = (0xff000000 | ((n & 255) << 16) | (n & 0xff00) | ((n >> 16) & 255)) >>> 0;
  }
  return v;
}
function rgbC(r, g, b) {
  return (0xff000000 | (clampN(Math.round(b), 0, 255) << 16) | (clampN(Math.round(g), 0, 255) << 8) | clampN(Math.round(r), 0, 255)) >>> 0;
}
const cR = (v) => v & 255;
const cG = (v) => (v >> 8) & 255;
const cB = (v) => (v >> 16) & 255;
function mixC(a, b, t) {
  return rgbC(cR(a) + (cR(b) - cR(a)) * t, cG(a) + (cG(b) - cG(a)) * t, cB(a) + (cB(b) - cB(a)) * t);
}
function shadeC(a, f) {
  return rgbC(cR(a) * f, cG(a) * f, cB(a) * f);
}
function hash01(n) {
  n = n | 0;
  n = (n ^ 61) ^ (n >>> 16);
  n = (n + (n << 3)) | 0;
  n = n ^ (n >>> 4);
  n = Math.imul(n, 0x27d4eb2d);
  n = n ^ (n >>> 15);
  return (n >>> 0) / 4294967296;
}

class Buf {
  constructor(w, h) {
    this.w = w;
    this.h = h;
    this.d = new Uint32Array(w * h);
  }
  clear(c) {
    this.d.fill(c);
  }
  px(x, y, c) {
    if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.d[y * this.w + x] = c;
  }
  get(x, y) {
    return x >= 0 && y >= 0 && x < this.w && y < this.h ? this.d[y * this.w + x] : 0;
  }
  blend(x, y, c, a) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h || a <= 0) return;
    const i = y * this.w + x;
    this.d[i] = mixC(this.d[i], c, a > 1 ? 1 : a);
  }
  rect(x, y, w, h, c) {
    const x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(this.w, x + w), y1 = Math.min(this.h, y + h);
    for (let yy = y0; yy < y1; yy++) {
      const row = yy * this.w;
      for (let xx = x0; xx < x1; xx++) this.d[row + xx] = c;
    }
  }
  line(x0, y0, x1, y1, c) {
    let dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (let guard = 0; guard < 2000; guard++) {
      this.px(x0, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  }
  poly(pts, c) {
    const n = pts.length;
    let minY = Infinity, maxY = -Infinity;
    for (let i = 0; i < n; i++) {
      if (pts[i][1] < minY) minY = pts[i][1];
      if (pts[i][1] > maxY) maxY = pts[i][1];
    }
    const y0 = Math.max(0, Math.ceil(minY - 0.5));
    const y1 = Math.min(this.h - 1, Math.ceil(maxY - 0.5) - 1);
    const xs = [];
    for (let y = y0; y <= y1; y++) {
      const yc = y + 0.5;
      xs.length = 0;
      for (let i = 0; i < n; i++) {
        let ax = pts[i][0], ay = pts[i][1];
        let bx = pts[(i + 1) % n][0], by = pts[(i + 1) % n][1];
        if (ay === by) continue;
        if (ay > by) { let tx = ax; ax = bx; bx = tx; let ty = ay; ay = by; by = ty; }
        if (yc >= ay && yc < by) xs.push(ax + ((yc - ay) * (bx - ax)) / (by - ay));
      }
      xs.sort((p, q) => p - q);
      for (let k = 0; k + 1 < xs.length; k += 2) {
        const xa = Math.max(0, Math.ceil(xs[k] - 0.5));
        const xb = Math.min(this.w - 1, Math.ceil(xs[k + 1] - 0.5) - 1);
        const row = y * this.w;
        for (let x = xa; x <= xb; x++) this.d[row + x] = c;
      }
    }
  }
  glow(cx, cy, r, c, a) {
    const r2 = r * r;
    for (let y = -r; y <= r; y++) {
      for (let x = -r; x <= r; x++) {
        const d2 = x * x + y * y;
        if (d2 > r2) continue;
        const k = 1 - Math.sqrt(d2) / r;
        this.blend(cx + x, cy + y, c, a * k * k);
      }
    }
  }
  ell(cx, cy, rx, ry, c) {
    const y0 = Math.ceil(cy - ry), y1 = Math.floor(cy + ry);
    for (let y = y0; y <= y1; y++) {
      const dy = (y - cy) / ry, w = rx * Math.sqrt(Math.max(0, 1 - dy * dy));
      const x0 = Math.ceil(cx - w - 0.001), x1 = Math.floor(cx + w + 0.001);
      if (y < 0 || y >= this.h) continue;
      for (let x = Math.max(0, x0); x <= Math.min(this.w - 1, x1); x++) this.d[y * this.w + x] = c;
    }
  }
  arc(cx, cy, rx, ry, a0, a1, c, th) {
    const n = Math.ceil((rx + ry) * Math.abs(a1 - a0));
    for (let i = 0; i <= n; i++) {
      const a = a0 + ((a1 - a0) * i) / n, x = Math.round(cx + Math.cos(a) * rx), y = Math.round(cy - Math.sin(a) * ry);
      this.px(x, y, c); if (th > 1) { this.px(x, y + 1, c); this.px(x + 1, y, c); }
    }
  }
  sprite(x, y, rows, pal, flip) {
    for (let j = 0; j < rows.length; j++) {
      const row = rows[j];
      for (let i = 0; i < row.length; i++) {
        const ch = row[flip ? row.length - 1 - i : i];
        if (ch === '.' || ch === ' ') continue;
        const col = pal[ch];
        if (col !== undefined) this.px(x + i, y + j, col);
      }
    }
  }
}

/* ---------- 4x5 pixel font for neon sign ---------- */
const GLYPH = {
  D: ['111.', '1..1', '1..1', '1..1', '111.'],
  R: ['111.', '1..1', '111.', '1.1.', '1..1'],
  E: ['1111', '1...', '111.', '1...', '1111'],
  A: ['.11.', '1..1', '1111', '1..1', '1..1'],
  O: ['.11.', '1..1', '1..1', '1..1', '.11.'],
  K: ['1..1', '1.1.', '11..', '1.1.', '1..1'],
  N: ['1..1', '11.1', '1.11', '1..1', '1..1'],
  B: ['111.', '1..1', '111.', '1..1', '111.'],
  U: ['1..1', '1..1', '1..1', '1..1', '.11.'],
  I: ['111', '.1.', '.1.', '.1.', '111'],
  L: ['1...', '1...', '1...', '1...', '1111'],
  M: ['1...1', '11.11', '1.1.1', '1...1', '1...1'],
  W: ['1...1', '1...1', '1.1.1', '11.11', '1...1'],
  F: ['1111', '1...', '111.', '1...', '1...'],
  C: ['.111', '1...', '1...', '1...', '.111'],
  S: ['.111', '1...', '.11.', '...1', '111.'],
  ' ': ['..', '..', '..', '..', '..'],
  '!': ['1', '1', '1', '.', '1'],
  P: ['111.', '1..1', '111.', '1...', '1...'],
  T: ['111', '.1.', '.1.', '.1.', '.1.'],
  H: ['1..1', '1..1', '1111', '1..1', '1..1'],
  G: ['.111', '1...', '1.11', '1..1', '.111'],
  Y: ['1.1', '1.1', '.1.', '.1.', '.1.'],
  V: ['1...1', '1...1', '.1.1.', '.1.1.', '..1..'],
  X: ['1...1', '.1.1.', '..1..', '.1.1.', '1...1'],
};
function textWidth(str) {
  let w = 0;
  for (let i = 0; i < str.length; i++) w += GLYPH[str[i]][0].length + 1;
  return w - 1;
}
function eachGlyphPixel(str, cb) {
  let x = 0;
  for (let i = 0; i < str.length; i++) {
    const g = GLYPH[str[i]];
    for (let r = 0; r < g.length; r++)
      for (let q = 0; q < g[r].length; q++) if (g[r][q] === '1') cb(x + q, r);
    x += g[0].length + 1;
  }
}

/* ---------- scene geometry ---------- */
const SCN = { W: 256, H: 176, OX: 128, OY: 62, HW: 12, HH: 6, WALL: 58, N: 9 };
let PS = 1; // projection scale: 2 while drawing the HD layer
let HDQ = null; // when set, characters are queued here and drawn later at ×2 on the HD buffer
function P(gx, gy, z) {
  return [Math.round((SCN.OX + (gx - gy) * SCN.HW) * PS), Math.round((SCN.OY + (gx + gy) * SCN.HH - (z || 0)) * PS)];
}
// draws a low-res routine onto the ×2 buffer as chunky 2×2 pixels (badges and other tiny UI marks)
function up2(B) {
  return {
    w: B.w >> 1, h: B.h >> 1,
    px: (x, y, c) => B.rect(x * 2, y * 2, 2, 2, c),
    rect: (x, y, w, h, c) => B.rect(x * 2, y * 2, w * 2, h * 2, c),
    blend: (x, y, c, a) => { B.blend(x * 2, y * 2, c, a); B.blend(x * 2 + 1, y * 2, c, a); B.blend(x * 2, y * 2 + 1, c, a); B.blend(x * 2 + 1, y * 2 + 1, c, a); },
    glow: (x, y, r, c, a) => B.glow(x * 2, y * 2, r * 2, c, a),
  };
}
function isoQuad(B, x0, y0, x1, y1, z, col) {
  B.poly([P(x0, y0, z), P(x1, y0, z), P(x1, y1, z), P(x0, y1, z)], col);
}
function isoBox(B, gx, gy, z, dx, dy, dz, top, left, right, edge) {
  const a = P(gx, gy, z + dz), b = P(gx + dx, gy, z + dz), c = P(gx + dx, gy + dy, z + dz), d = P(gx, gy + dy, z + dz);
  const e = P(gx, gy + dy, z), f = P(gx + dx, gy + dy, z), g = P(gx + dx, gy, z);
  B.poly([d, c, f, e], left);
  B.poly([c, b, g, f], right);
  B.poly([a, b, c, d], top);
  if (edge) {
    B.line(d[0], d[1], c[0], c[1], edge);
    B.line(c[0], c[1], b[0], b[1], edge);
  }
}
function faceGY(B, gx0, gx1, gy, z0, z1, col) {
  B.poly([P(gx0, gy, z0), P(gx1, gy, z0), P(gx1, gy, z1), P(gx0, gy, z1)], col);
}
function faceGX(B, gy0, gy1, gx, z0, z1, col) {
  B.poly([P(gx, gy0, z0), P(gx, gy1, z0), P(gx, gy1, z1), P(gx, gy0, z1)], col);
}
function lineGY(B, gx0, gx1, gy, z, col) {
  const a = P(gx0, gy, z), b = P(gx1, gy, z);
  B.line(a[0], a[1], b[0], b[1], col);
}
function lineGX(B, gy0, gy1, gx, z, col) {
  const a = P(gx, gy0, z), b = P(gx, gy1, z);
  B.line(a[0], a[1], b[0], b[1], col);
}

/* ---------- palette ---------- */
const PAL = {
  bg0: C('#0a0d2c'), bg1: C('#05071a'),
  wallR: C('#38387f'), wallL: C('#2a2e6a'),
  floorA: C('#1c2456'), floorB: C('#232d68'), grid: C('#33418f'),
  slab: C('#0d1033'), slabEdge: C('#1d8fb0'),
  cyan: C('#22e6ff'), mag: C('#ff3fd0'), yel: C('#ffd23f'), grn: C('#2ee66b'), blu: C('#4ca3ff'), org: C('#ff8a3d'), red: C('#ff4d6a'),
  woodT: C('#c98a4b'), woodL: C('#9a6232'), woodR: C('#74461f'), woodE: C('#e3a86a'),
  bezel: C('#12163a'), bezelR: C('#0b0e28'), screenOff: C('#0a0c1f'),
  white: C('#ffffff'), ink: C('#07081c'),
};

/* ---------- office themes: every floor you move to looks different ---------- */
// tier 0 = basement (the original neon loft) … 5 = CEO penthouse
const THEMES = [
  { id: 'b', wallR: '#38387f', wallL: '#2a2e6a', stripR: 'cyan', stripL: 'mag', floorA: '#1c2456', floorB: '#232d68', grid: '#33418f', floor: 'check',
    slab: '#0d1033', slabEdge: '#1d8fb0', wood: ['#c98a4b', '#9a6232', '#74461f', '#e3a86a'], rug: ['#2a1760', '#3a1d7e', 'cyan', 'mag'],
    words: [['DREAM', 'cyan'], ['WORK', 'mag'], ['EARN', 'yel'], ['BUILD', 'grn']], poster: ['#ff3fd0', '#3b1a8f', '#ffd23f'], deco: 'none', view: 0, glow: ['mag', 'cyan'] },
  { id: 'f1', wallR: '#1f5a66', wallL: '#174652', stripR: 'grn', stripL: 'cyan', floorA: '#6b4527', floorB: '#7a5130', grid: '#4a2e18', floor: 'wood',
    slab: '#1a1208', slabEdge: '#2ee6a0', wood: ['#e0c08a', '#b8955e', '#8c6d40', '#f4dcae'], rug: ['#0f3d33', '#15574a', 'grn', 'cyan'],
    words: [['PLAN', 'grn'], ['SAVE', 'cyan'], ['COUNT', 'yel'], ['GROW', 'grn']], poster: ['#2ee6a0', '#0f3d4a', '#ffffff'], deco: 'binders', view: 4, glow: ['grn', 'cyan'] },
  { id: 'f2', wallR: '#6a3a86', wallL: '#552c6e', stripR: 'yel', stripL: 'mag', floorA: '#4b4486', floorB: '#58519a', grid: '#7b72c4', floor: 'check',
    slab: '#1f1640', slabEdge: '#ffd23f', wood: ['#f2f0ff', '#c9c4ec', '#a29bd6', '#ffffff'], rug: ['#7a3a10', '#b8601c', 'yel', 'org'],
    words: [['PIXEL', 'yel'], ['DRAW', 'mag'], ['COLOR', 'cyan'], ['STYLE', 'org']], poster: ['#ffd23f', '#ff3fd0', '#22e6ff'], deco: 'palette', view: 8, glow: ['yel', 'mag'] },
  { id: 'f3', wallR: '#7a3328', wallL: '#62281f', stripR: 'org', stripL: 'yel', floorA: '#3a1f3e', floorB: '#3a1f3e', grid: '#4a2850', floor: 'carpet',
    slab: '#1e0d1c', slabEdge: '#ff8a3d', wood: ['#3a3f5a', '#2a2e44', '#1e2133', '#6a7090'], rug: ['#5a0f22', '#8a1a35', 'red', 'org'],
    words: [['HYPE', 'org'], ['VIRAL', 'mag'], ['LIKES', 'red'], ['SHARE', 'yel']], poster: ['#ff4d6a', '#ff8a3d', '#ffffff'], deco: 'tv', view: 12, glow: ['org', 'red'] },
  { id: 'f4', wallR: '#163a32', wallL: '#102c26', stripR: 'grn', stripL: 'cyan', floorA: '#141a28', floorB: '#1a2234', grid: '#2ee66b', floor: 'metal',
    slab: '#05080f', slabEdge: '#2ee66b', wood: ['#2a2f3d', '#1d212c', '#14171f', '#2ee66b'], rug: ['#06222a', '#0b3340', 'cyan', 'grn'],
    words: [['CODE', 'grn'], ['PLAY', 'cyan'], ['LEVEL', 'yel'], ['BOSS', 'red']], poster: ['#2ee66b', '#06222a', '#22e6ff'], deco: 'invader', view: 16, glow: ['grn', 'cyan'] },
  { id: 'f5', wallR: '#2a2438', wallL: '#1f1a2c', stripR: 'yel', stripL: 'yel', floorA: '#d8d2ee', floorB: '#c4bce0', grid: '#ece6ff', floor: 'marble',
    slab: '#141018', slabEdge: '#ffd23f', wood: ['#5a2e1c', '#43200f', '#2e1508', '#ffd23f'], rug: ['#5a0a1a', '#7a1026', 'yel', 'yel'],
    words: [['EMPIRE', 'yel'], ['VISION', 'yel'], ['CEO', 'white'], ['WIN', 'yel']], poster: ['#ffd23f', '#1f1a2c', '#ffffff'], deco: 'trophy', view: 20, glow: ['yel', 'org'] },
];
let THEME = THEMES[0];
const pc = (v) => (typeof v === 'string' && v[0] !== '#' ? PAL[v] : C(v));
function themeTier(floors) {
  if (!floors) return 0;
  for (let i = 5; i >= 1; i--) if (floors['f' + i]) return i;
  return 0;
}
function applyTheme(tier) {
  THEME = THEMES[clampN(tier | 0, 0, 5)];
  const T = THEME;
  PAL.wallR = C(T.wallR); PAL.wallL = C(T.wallL);
  PAL.floorA = C(T.floorA); PAL.floorB = C(T.floorB); PAL.grid = C(T.grid);
  PAL.slab = C(T.slab); PAL.slabEdge = C(T.slabEdge);
  PAL.woodT = C(T.wood[0]); PAL.woodL = C(T.wood[1]); PAL.woodR = C(T.wood[2]); PAL.woodE = C(T.wood[3]);
}

/* ---------- sky ---------- */
const SKY_KEYS = [
  [0.0, C('#3a2f7a'), C('#ff8a6b'), 0.55],
  [0.2, C('#4d8fe8'), C('#ffc59a'), 0.18],
  [0.45, C('#3fa7ff'), C('#c4ecff'), 0.04],
  [0.75, C('#5a2fa8'), C('#ff6f91'), 0.4],
  [1.0, C('#0b0f33'), C('#262d86'), 0.92],
];
function skyAt(f) {
  f = clampN(f, 0, 1);
  for (let i = 0; i < SKY_KEYS.length - 1; i++) {
    const a = SKY_KEYS[i], b = SKY_KEYS[i + 1];
    if (f <= b[0]) {
      const t = (f - a[0]) / (b[0] - a[0]);
      return { top: mixC(a[1], b[1], t), bot: mixC(a[2], b[2], t), dens: a[3] + (b[3] - a[3]) * t, night: f > 0.82 ? (f - 0.82) / 0.18 : 0 };
    }
  }
  const l = SKY_KEYS[SKY_KEYS.length - 1];
  return { top: l[1], bot: l[2], dens: l[3], night: 1 };
}

/* skyline (window-local columns) */
const WIN = { k0: 30, k1: 102, v0: 18, v1: 50 };
const SKYLINE = (() => {
  const w = WIN.k1 - WIN.k0 + 1;
  const far = new Array(w).fill(0), near = new Array(w).fill(0), farId = new Array(w).fill(0), nearId = new Array(w).fill(0);
  let x = 0, id = 1, s = 11;
  const rnd = () => { s = (s * 1103515245 + 12345) & 0x7fffffff; return s / 0x7fffffff; };
  while (x < w) {
    const bw = 5 + Math.floor(rnd() * 7), bh = 8 + Math.floor(rnd() * 14);
    for (let i = 0; i < bw && x + i < w; i++) { far[x + i] = bh; farId[x + i] = id; }
    x += bw; id++;
  }
  x = 0;
  while (x < w) {
    const bw = 6 + Math.floor(rnd() * 8), bh = 3 + Math.floor(rnd() * 12);
    for (let i = 0; i < bw && x + i < w; i++) { near[x + i] = bh; nearId[x + i] = id; }
    x += bw; id++;
  }
  return { far, near, farId, nearId };
})();

function windowPixel(kx, v, sky) {
  const lx = kx - WIN.k0, ly = v - WIN.v0; // ly 0 at bottom of window
  const gh = WIN.v1 - WIN.v0; // glass height
  const y01 = 1 - ly / gh;
  let col = mixC(sky.top, sky.bot, clampN(y01 * 1.05, 0, 1));
  // stars
  if (sky.night > 0 && hash01(lx * 131 + ly * 17) < 0.035 * sky.night && ly > 16) col = mixC(col, PAL.white, 0.8);
  const fh = SKYLINE.far[lx] - THEME.view, nh = SKYLINE.near[lx] - THEME.view * 1.3;
  if (ly < fh + 6) {
    if (ly < fh + 6 && ly <= fh + 5 && ly < fh + 6 && ly < fh + 6) {
      /* far layer lives from 0..fh+6 */
    }
  }
  const farTop = fh + 5, nearTop = nh + 2;
  if (ly < farTop) {
    let bc = mixC(sky.bot, C('#2a2468'), 0.72);
    const wx = lx % 3 === 1, wy = ly % 3 === 1;
    if (wx && wy && ly > 1 && ly < farTop - 1 && hash01(lx * 31 + ly * 7 + SKYLINE.farId[lx] * 3) < sky.dens * 0.55) {
      const h = hash01(lx * 5 + ly * 9 + 3);
      bc = mixC(bc, h < 0.5 ? PAL.yel : h < 0.8 ? PAL.cyan : PAL.mag, 0.55);
    }
    col = bc;
  }
  if (ly < nearTop) {
    let bc = C('#0c0e2c');
    if (ly === nearTop - 1) bc = C('#1a1d4d');
    const wx = lx % 3 === 0, wy = ly % 3 === 1;
    if (wx && wy && ly > 0 && ly < nearTop - 1 && hash01(lx * 17 + ly * 11 + SKYLINE.nearId[lx] * 5) < sky.dens) {
      const h = hash01(lx * 3 + ly * 13 + 1);
      bc = h < 0.55 ? PAL.yel : h < 0.8 ? PAL.cyan : PAL.mag;
    }
    col = bc;
  }
  return col;
}

/* ---------- wall helpers ---------- */
function wallXY(side, k, v) {
  const yf = SCN.OY + ((k + 1) >> 1);
  return side === 'R' ? [SCN.OX + k, yf - v] : [SCN.OX - 1 - k, yf - v];
}
function wallPx(B, side, k, v, c) {
  const p = wallXY(side, k, v);
  B.px(p[0], p[1], c);
}
function wallBlend(B, side, k, v, c, a) {
  const p = wallXY(side, k, v);
  B.blend(p[0], p[1], c, a);
}
function wallGlyphs(B, side, k0, v0, str, col, glowCol) {
  // on the left wall columns run right-to-left, so mirror the text to keep it readable
  const tw = textWidth(str);
  const kx = (cx) => (side === 'L' ? k0 + (tw - 1 - cx) : k0 + cx);
  eachGlyphPixel(str, (cx, ry) => {
    const v = v0 + (4 - ry);
    if (glowCol) {
      wallBlend(B, side, kx(cx) - 1, v, glowCol, 0.28);
      wallBlend(B, side, kx(cx) + 1, v, glowCol, 0.28);
      wallBlend(B, side, kx(cx), v + 1, glowCol, 0.28);
      wallBlend(B, side, kx(cx), v - 1, glowCol, 0.28);
    }
  });
  eachGlyphPixel(str, (cx, ry) => wallPx(B, side, kx(cx), v0 + (4 - ry), col));
}

function drawWalls(B, f) {
  const sky = skyAt(f);
  const H = SCN.WALL;
  for (const side of ['R', 'L']) {
    const base = side === 'R' ? PAL.wallR : PAL.wallL;
    const strip = PAL[side === 'R' ? THEME.stripR : THEME.stripL];
    for (let k = 0; k < 108; k++) {
      for (let v = 0; v <= H + 1; v++) {
        let col;
        if (v >= H) col = mixC(base, PAL.white, 0.22);
        else if (v >= H - 1) col = C('#0a0c28');
        else if (v >= H - 3) col = strip;
        else if (v < 2) col = C('#141745');
        else if (v < 15) col = shadeC(base, 0.82);
        else if (v === 15) col = mixC(base, PAL.white, 0.14);
        else col = base;
        if (v >= 15 && v < H - 3 && k % 36 === 0) col = shadeC(col, 0.86);
        if (v >= H - 8 && v < H - 3) col = mixC(col, strip, 0.1 * (1 - (H - 4 - v) / 6));
        wallPx(B, side, k, v, col);
      }
    }
  }
  // window on right wall
  for (let k = WIN.k0 - 2; k <= WIN.k1 + 2; k++) {
    for (let v = WIN.v0 - 3; v <= WIN.v1 + 1; v++) {
      let col = null;
      const inGlass = k >= WIN.k0 + 2 && k <= WIN.k1 - 2 && v >= WIN.v0 + 1 && v <= WIN.v1 - 2;
      const inFrame = k >= WIN.k0 && k <= WIN.k1 && v >= WIN.v0 - 1 && v <= WIN.v1;
      if (inGlass) {
        col = windowPixel(k, v - 1, sky);
        const mull = (k - WIN.k0 - 2) % 23 === 22 || (k - WIN.k0 - 2) % 23 === 0;
        if (mull && k > WIN.k0 + 3) col = C('#0b0d2b');
        // soft glass shine
        if (((k + v * 2) % 29) < 2) col = mixC(col, PAL.white, 0.1);
      } else if (inFrame) col = C('#0b0d2b');
      else if (v >= WIN.v0 - 3 && v < WIN.v0 - 1 && k >= WIN.k0 - 2 && k <= WIN.k1 + 2) col = mixC(PAL.wallR, PAL.white, 0.3); // sill
      if (col !== null) wallPx(B, 'R', k, v, col);
    }
  }
  // window light spill onto the wall (soft)
  // neon sign on left wall
  {
    const k0 = 22, k1 = 60, v0 = 12, v1 = 52;
    for (let k = k0; k <= k1; k++)
      for (let v = v0; v <= v1; v++) {
        const edge = k === k0 || k === k1 || v === v0 || v === v1;
        wallPx(B, 'L', k, v, edge ? C('#3c3f94') : C('#0b0d2b'));
      }
    const words = THEME.words.map((w, i) => [w[0], pc(w[1]), 46 - i * 8]);
    for (const w of words) {
      const tw = textWidth(w[0]);
      const kk = k0 + 1 + Math.floor((k1 - k0 - 1 - tw) / 2);
      wallGlyphs(B, 'L', kk, w[2] - 1, w[0], w[1], w[1]);
    }
  }
  // poster on right wall near corner
  {
    const k0 = 8, k1 = 24, v0 = 22, v1 = 44;
    for (let k = k0; k <= k1; k++)
      for (let v = v0; v <= v1; v++) {
        const edge = k === k0 || k === k1 || v === v0 || v === v1;
        const t = (v - v0) / (v1 - v0);
        const pp = THEME.poster;
        let col = mixC(C(pp[0]), C(pp[1]), t);
        if (!edge && v < v0 + 9 && Math.hypot(k - 16, v - (v0 + 7)) < 4.2) col = C(pp[2]); // sun
        if (!edge && v < v0 + 6 && v > v0 + 3 && (k + v) % 2 === 0 && Math.hypot(k - 16, v - (v0 + 7)) < 5) col = C('#ff8a3d');
        if (!edge && v >= v0 + 1 && v <= v0 + 7 && ((k * 7 + 3) % 5) === 0 && v < v0 + 4 + (k % 3)) col = C('#1a0f4d');
        wallPx(B, 'R', k, v, edge ? C('#e8e8ff') : col);
      }
  }
  drawWallDeco(B);
  // wall clock plate (left wall) — hands drawn dynamically
  {
    const kc = 82, vc = 38;
    for (let dk = -6; dk <= 6; dk++)
      for (let dv = -6; dv <= 6; dv++) {
        const d = Math.hypot(dk, dv);
        if (d <= 6.2) wallPx(B, 'L', kc + dk, vc + dv, d > 5 ? C('#e8e8ff') : C('#10133a'));
      }
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * Math.PI * 2;
      wallPx(B, 'L', kc + Math.round(Math.sin(a) * 4.2), vc + Math.round(Math.cos(a) * 4.2), PAL.cyan);
    }
  }
}

function drawWallDeco(B) {
  const d = THEME.deco;
  const box = (k0, k1, v0, v1, fill, edge) => {
    for (let k = k0; k <= k1; k++) for (let v = v0; v <= v1; v++) wallPx(B, 'L', k, v, k === k0 || k === k1 || v === v0 || v === v1 ? edge : fill);
  };
  if (d === 'binders') {
    // accounting: a shelf of colourful binders and a rising chart
    box(92, 106, 16, 30, C('#0c2a30'), C('#e8f6ff'));
    const cols = [PAL.grn, PAL.cyan, PAL.yel, PAL.org, PAL.blu, PAL.mag];
    for (let i = 0; i < 6; i++) for (let v = 18; v <= 27 - (i % 2); v++) { wallPx(B, 'L', 94 + i * 2, v, cols[i]); }
    box(92, 106, 34, 50, C('#f2fff9'), C('#2a6a66'));
    const pts = [[94, 37], [97, 39], [100, 38], [103, 44], [105, 47]];
    for (let i = 0; i < pts.length - 1; i++) {
      const [ka, va] = pts[i], [kb, vb] = pts[i + 1];
      for (let k = ka; k <= kb; k++) wallPx(B, 'L', k, Math.round(va + ((vb - va) * (k - ka)) / (kb - ka)), PAL.grn);
    }
  } else if (d === 'palette') {
    // design: colour swatches + a painting
    box(92, 106, 36, 50, C('#fff8ec'), C('#ffd23f'));
    for (let k = 93; k <= 105; k++) for (let v = 37; v <= 49; v++) {
      const h = Math.hypot(k - 99, v - 43);
      if (h < 5) wallPx(B, 'L', k, v, h < 2.5 ? PAL.mag : PAL.cyan);
      else if (v < 40) wallPx(B, 'L', k, v, PAL.yel);
    }
    const sw = [PAL.red, PAL.org, PAL.yel, PAL.grn, PAL.cyan, PAL.blu, PAL.mag];
    sw.forEach((c, i) => { for (let v = 18; v <= 30; v++) { wallPx(B, 'L', 92 + i * 2, v, c); wallPx(B, 'L', 93 + i * 2, v, c); } });
  } else if (d === 'tv') {
    // marketing: a big screen with growth bars and a heart
    box(90, 107, 18, 48, C('#0a0c24'), C('#3a3f5a'));
    const bars = [6, 10, 8, 15, 21];
    bars.forEach((h, i) => { for (let v = 21; v < 21 + h; v++) { wallPx(B, 'L', 93 + i * 3, v, PAL.org); wallPx(B, 'L', 94 + i * 3, v, PAL.org); } });
    const heart = ['.11.11.', '1111111', '1111111', '.11111.', '..111..', '...1...'];
    heart.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === '1') wallPx(B, 'L', 92 + c, 45 - r, PAL.red); }));
  } else if (d === 'invader') {
    // game dev: a pixel space invader poster
    box(91, 107, 16, 50, C('#03140f'), C('#2ee66b'));
    const inv = ['..1.....1..', '...1...1...', '..1111111..', '.11.111.11.', '11111111111', '1.1111111.1', '1.1.....1.1', '...11.11...'];
    inv.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === '1') wallPx(B, 'L', 94 + c, 42 - r, PAL.grn); }));
    for (let k = 93; k <= 105; k += 2) wallPx(B, 'L', k, 22, PAL.cyan);
  } else if (d === 'trophy') {
    // CEO: a golden trophy in a frame and a gold-framed company portrait
    box(91, 107, 16, 50, C('#120e1c'), C('#ffd23f'));
    const cup = ['1111111', '1111111', '.11111.', '..111..', '...1...', '...1...', '..111..', '.11111.'];
    cup.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === '1') wallPx(B, 'L', 96 + c, 44 - r, r < 2 ? C('#fff1a8') : PAL.yel); }));
    wallPx(B, 'L', 95, 43, PAL.yel); wallPx(B, 'L', 94, 42, PAL.yel); wallPx(B, 'L', 103, 43, PAL.yel); wallPx(B, 'L', 104, 42, PAL.yel);
    for (let k = 93; k <= 105; k++) wallPx(B, 'L', k, 24, PAL.yel);
  }
}

function drawFloor(B) {
  const N = SCN.N;
  // slab thickness
  const L = P(0, N, 0), Bt = P(N, N, 0), Rr = P(N, 0, 0);
  const T = 7;
  B.poly([L, Bt, [Bt[0], Bt[1] + T], [L[0], L[1] + T]], PAL.slab);
  B.poly([Bt, Rr, [Rr[0], Rr[1] + T], [Bt[0], Bt[1] + T]], shadeC(PAL.slab, 0.78));
  B.line(L[0], L[1] + 1, Bt[0], Bt[1] + 1, PAL.slabEdge);
  B.line(Bt[0], Bt[1] + 1, Rr[0], Rr[1] + 1, mixC(PAL.slabEdge, PAL.mag, 0.5));
  const kind = THEME.floor;
  if (kind === 'wood') {
    // parquet planks running along gx, staggered seams
    for (let j = 0; j < N * 3; j++) {
      const off = (j % 3) * 0.33;
      for (let i = -1; i < N; i++) {
        const a = Math.max(0, i + off), b = Math.min(N, i + 1 + off);
        if (b <= a) continue;
        const h = hash01(i * 13 + j * 7);
        isoQuad(B, a, j / 3, b, (j + 1) / 3, 0, mixC(PAL.floorA, PAL.floorB, h));
        const p = P(a, j / 3, 0), q = P(a, (j + 1) / 3, 0);
        B.line(p[0], p[1], q[0], q[1], PAL.grid);
      }
      const p = P(0, j / 3, 0), q = P(N, j / 3, 0);
      B.line(p[0], p[1], q[0], q[1], mixC(PAL.grid, PAL.floorA, 0.4));
    }
    return;
  }
  for (let i = 0; i < N; i++)
    for (let j = 0; j < N; j++) {
      let col = (i + j) & 1 ? PAL.floorA : PAL.floorB;
      if (kind === 'carpet') col = mixC(PAL.floorA, C('#000000'), hash01(i * 5 + j * 11) * 0.12);
      isoQuad(B, i, j, i + 1, j + 1, 0, col);
    }
  if (kind === 'carpet') {
    // soft fibre speckle, no grid
    for (let n = 0; n < 900; n++) {
      const gx = hash01(n * 3 + 1) * N, gy = hash01(n * 7 + 2) * N;
      const p = P(gx, gy, 0);
      B.blend(p[0], p[1], hash01(n) < 0.5 ? PAL.white : C('#000000'), 0.07);
    }
    return;
  }
  const gcol = kind === 'metal' ? mixC(PAL.floorB, PAL.grid, 0.35) : kind === 'marble' ? PAL.grid : mixC(PAL.floorB, PAL.grid, 0.55);
  for (let i = 0; i <= N; i++) {
    const a = P(i, 0, 0), b = P(i, N, 0), c = P(0, i, 0), d = P(N, i, 0);
    B.line(a[0], a[1], b[0], b[1], gcol);
    B.line(c[0], c[1], d[0], d[1], gcol);
  }
  if (kind === 'marble') {
    // thin grey veins across the stone
    for (let v = 0; v < 14; v++) {
      let gx = hash01(v * 5 + 3) * N, gy = hash01(v * 9 + 1) * N;
      for (let s = 0; s < 22; s++) {
        const p = P(gx, gy, 0);
        B.blend(p[0], p[1], C('#8a83b0'), 0.35);
        gx += 0.09 + (hash01(v * 31 + s) - 0.5) * 0.08; gy += (hash01(v * 17 + s * 3) - 0.5) * 0.12;
      }
    }
  } else if (kind === 'metal') {
    for (let i = 0; i < N; i++) for (let j = 0; j < N; j++) {
      for (const [a, b] of [[0.15, 0.15], [0.85, 0.15], [0.15, 0.85], [0.85, 0.85]]) { const p = P(i + a, j + b, 0); B.px(p[0], p[1], C('#3a4560')); }
      if ((i * 3 + j) % 7 === 0) { const p = P(i + 0.5, j + 0.5, 0); B.glow(p[0], p[1], 5, PAL.grn, 0.12); }
    }
  }
}

function drawRug(B) {
  const r = THEME.rug, e1 = pc(r[2]), e2 = pc(r[3]);
  isoQuad(B, 3.9, 3.9, 8.3, 7.5, 0, C(r[0]));
  isoQuad(B, 4.1, 4.1, 8.1, 7.3, 0, C(r[1]));
  const a = P(3.9, 3.9, 0), b = P(8.3, 3.9, 0), c = P(8.3, 7.5, 0), d = P(3.9, 7.5, 0);
  B.line(a[0], a[1], b[0], b[1], e1);
  B.line(b[0], b[1], c[0], c[1], e2);
  B.line(c[0], c[1], d[0], d[1], e2);
  B.line(d[0], d[1], a[0], a[1], e1);
  if (THEME.id === 'f5') { // CEO rug: inner gold border
    const a2 = P(4.3, 4.3, 0), b2 = P(7.9, 4.3, 0), c2 = P(7.9, 7.1, 0), d2 = P(4.3, 7.1, 0);
    B.line(a2[0], a2[1], b2[0], b2[1], e1); B.line(b2[0], b2[1], c2[0], c2[1], e1); B.line(c2[0], c2[1], d2[0], d2[1], e1); B.line(d2[0], d2[1], a2[0], a2[1], e1);
  }
}

function drawBase(B, f) {
  // background
  for (let y = 0; y < B.h; y++) B.rect(0, y, B.w, 1, mixC(PAL.bg0, PAL.bg1, y / B.h));
  for (let i = 0; i < 70; i++) {
    const x = Math.floor(hash01(i * 3 + 1) * B.w), y = Math.floor(hash01(i * 5 + 2) * B.h);
    B.px(x, y, mixC(PAL.bg0, C('#6f7bd6'), 0.25 + hash01(i) * 0.4));
  }
  drawFloor(B);
  drawRug(B);
  drawWalls(B, f);
  // soft neon ambience on the floor
  B.glow(SCN.OX, SCN.OY + 70, 52, PAL[THEME.glow[0]], 0.10);
  B.glow(SCN.OX - 36, SCN.OY + 46, 40, PAL[THEME.glow[1]], 0.07);
}

/* ---------- characters ---------- */
const LOOK = {
  prog: { hair: C('#1b1433'), skin: C('#a96f48'), shirt: C('#22c8d8'), style: 'short', screen: C('#2ee6a0') },
  des: { hair: C('#6b4326'), skin: C('#e2aa7c'), shirt: C('#ffd23f'), style: 'spiky', screen: C('#ffd23f') },
  art: { hair: C('#ff7a3d'), skin: C('#f3cba6'), shirt: C('#ff3fd0'), style: 'long', screen: C('#ff5fd2') },
  mkt: { hair: C('#d8467a'), skin: C('#f7d6b6'), shirt: C('#4ca3ff'), style: 'long', screen: C('#4ca3ff') },
  you: { hair: C('#17102b'), skin: C('#7a4a2e'), shirt: C('#3b6bff'), style: 'short', screen: C('#22e6ff') },
  boss: { hair: C('#a3a9c4'), skin: C('#e2b18a'), shirt: C('#2b2f66'), style: 'short', screen: C('#ffd23f') },
  inv: { hair: C('#2b1a12'), skin: C('#d9a37a'), shirt: C('#1f8a5f'), style: 'short', screen: C('#2ee66b') },
  npc: { hair: C('#4a2f8a'), skin: C('#e8bf98'), shirt: C('#7a56e0'), style: 'spiky', screen: C('#ff3fd0') },
};
const STATION = {
  prog: { side: 'R', gx: 3.4, gy: 0.2, chair: [4.4, 2.2] },
  des: { side: 'R', gx: 6.0, gy: 0.2, chair: [7.0, 2.2] },
  art: { side: 'L', gx: 0.2, gy: 3.4, chair: [2.2, 4.4] },
  mkt: { side: 'L', gx: 0.2, gy: 6.0, chair: [2.2, 7.0] },
};

function drawDesk(B, s) {
  const len = 2.0, dep = 1.0, H = 13;
  const woodT = PAL.woodT, woodL = PAL.woodL, woodR = PAL.woodR;
  if (s.side === 'R') {
    // end panels
    isoBox(B, s.gx, s.gy, 0, 0.12, dep, H - 3, woodL, woodL, woodR);
    isoBox(B, s.gx + len - 0.12, s.gy, 0, 0.12, dep, H - 3, woodL, woodL, woodR);
    isoBox(B, s.gx, s.gy, 0, len, 0.08, H - 3, woodR, woodR, woodR); // modesty panel (back)
    isoBox(B, s.gx, s.gy, H - 3, len, dep, 3, woodT, woodL, woodR, PAL.woodE);
  } else {
    isoBox(B, s.gx, s.gy, 0, dep, 0.12, H - 3, woodL, woodL, woodR);
    isoBox(B, s.gx, s.gy + len - 0.12, 0, dep, 0.12, H - 3, woodL, woodL, woodR);
    isoBox(B, s.gx, s.gy, 0, 0.08, len, H - 3, woodR, woodR, woodR);
    isoBox(B, s.gx, s.gy, H - 3, dep, len, 3, woodT, woodL, woodR, PAL.woodE);
  }
}

function drawMonitor(B, s, look, on, t, seed, kind, glowOn) {
  const w = 1.1, z0 = 15, z1 = 26;
  const bz = 13;
  if (s.side === 'R') {
    const cx = s.gx + 1.0, gyB = s.gy + 0.34;
    isoBox(B, cx - 0.09, gyB - 0.02, bz, 0.18, 0.14, 2, PAL.bezel, PAL.bezel, PAL.bezelR);
    isoBox(B, cx - w / 2, gyB - 0.06, z0, w, 0.1, z1 - z0, PAL.bezel, PAL.bezel, PAL.bezelR);
    const gyF = gyB + 0.04, x0 = cx - w / 2 + 0.07, x1 = cx + w / 2 - 0.07;
    faceGY(B, x0, x1, gyF, z0 + 1, z1 - 1, on ? C('#0a1230') : PAL.screenOff);
    if (on) screenContent(B, 'GY', x0, x1, gyF, z0 + 1, z1 - 1, look.screen, t, seed, kind);
    // keyboard
    isoBox(B, cx - 0.4, gyB + 0.32, bz, 0.8, 0.26, 1, C('#2b2f66'), C('#1a1d45'), C('#14163a'));
    if (glowOn) {
      const p = P(cx, gyB + 0.7, 14);
      B.glow(p[0], p[1], 12, look.screen, 0.14);
    }
  } else {
    const cy = s.gy + 1.0, gxB = s.gx + 0.34;
    isoBox(B, gxB - 0.02, cy - 0.09, bz, 0.14, 0.18, 2, PAL.bezel, PAL.bezel, PAL.bezelR);
    isoBox(B, gxB - 0.06, cy - w / 2, z0, 0.1, w, z1 - z0, PAL.bezel, PAL.bezel, PAL.bezelR);
    const gxF = gxB + 0.04, y0 = cy - w / 2 + 0.07, y1 = cy + w / 2 - 0.07;
    faceGX(B, y0, y1, gxF, z0 + 1, z1 - 1, on ? C('#0a1230') : PAL.screenOff);
    if (on) screenContent(B, 'GX', y0, y1, gxF, z0 + 1, z1 - 1, look.screen, t, seed, kind);
    isoBox(B, gxB + 0.32, cy - 0.4, bz, 0.26, 0.8, 1, C('#2b2f66'), C('#1a1d45'), C('#14163a'));
    if (glowOn) {
      const p = P(gxB + 0.7, cy, 14);
      B.glow(p[0], p[1], 12, look.screen, 0.14);
    }
  }
}

function screenContent(B, plane, a0, a1, fixed, z0, z1, col, t, seed, kind) {
  const line = plane === 'GY' ? lineGY : lineGX;
  const step = Math.floor(t * 2.2);
  const span = a1 - a0;
  if (kind === 'mkt') {
    // bars
    for (let i = 0; i < 5; i++) {
      const hgt = 1.5 + hash01(seed * 7 + i * 13 + step) * (z1 - z0 - 3);
      const x0 = a0 + 0.08 + i * (span / 5.2), x1 = x0 + span / 8;
      if (plane === 'GY') faceGY(B, x0, x1, fixed, z0 + 0.6, z0 + 0.6 + hgt, i % 2 ? col : PAL.cyan);
      else faceGX(B, x0, x1, fixed, z0 + 0.6, z0 + 0.6 + hgt, i % 2 ? col : PAL.cyan);
    }
    return;
  }
  const rows = Math.max(2, Math.floor((z1 - z0) / 2));
  for (let i = 0; i < rows; i++) {
    const z = z1 - 1.2 - i * 2;
    if (z < z0 + 0.4) break;
    const len = (0.25 + hash01(seed * 11 + i * 17 + step * 3) * 0.7) * (span - 0.1);
    const ind = kind === 'prog' ? ((i + step) % 3) * 0.1 : 0;
    const c = kind === 'art' ? (i % 2 ? col : PAL.cyan) : kind === 'des' ? (i % 3 === 0 ? PAL.white : col) : i % 3 === 0 ? mixC(col, PAL.white, 0.5) : col;
    line(B, a0 + 0.05 + ind, a0 + 0.05 + ind + len, fixed, z, c);
  }
}

function drawChairParts(B, s, accent, seatOnly) {
  const [cx, cy] = s.chair;
  const seat = PAL.bezel;
  // shadow
  isoQuad(B, cx - 0.5, cy - 0.5, cx + 0.5, cy + 0.5, 0, C('#141845'));
  // post + seat
  isoBox(B, cx - 0.08, cy - 0.08, 0, 0.16, 0.16, 6, C('#252a66'), C('#1a1e4d'), C('#12163a'));
  isoBox(B, cx - 0.36, cy - 0.36, 6, 0.72, 0.72, 3, accent, shadeC(accent, 0.78), shadeC(accent, 0.6));
}
function drawChairBack(B, s, accent, dz) {
  const [cx, cy] = s.chair, h = dz || 10;
  if (s.side === 'R') isoBox(B, cx - 0.3, cy + 0.3, 9, 0.6, 0.1, h, shadeC(accent, 1.2), accent, shadeC(accent, 0.6));
  else isoBox(B, cx + 0.3, cy - 0.3, 9, 0.1, 0.6, h, shadeC(accent, 1.2), accent, shadeC(accent, 0.6));
}

function drawWorkerBack(B, ax, ay, look, typing, frame, t) {
  const sh = shadeC(look.shirt, 0.72), hs = shadeC(look.hair, 0.7);
  // torso
  B.rect(ax - 4, ay - 12, 8, 9, look.shirt);
  B.rect(ax - 5, ay - 12, 10, 2, shadeC(look.shirt, 1.12));
  B.rect(ax - 4, ay - 5, 8, 2, sh);
  // arms
  const af = typing ? frame : 0;
  B.rect(ax - 6, ay - 11, 2, 6, sh);
  B.rect(ax + 4, ay - 11, 2, 6, sh);
  B.rect(ax - 6, ay - 5 + (af ? 1 : 0), 2, 2, look.skin);
  B.rect(ax + 4, ay - 5 + (af ? 0 : 1), 2, 2, look.skin);
  // neck + head
  B.rect(ax - 1, ay - 14, 3, 2, shadeC(look.skin, 0.85));
  B.rect(ax - 3, ay - 20, 6, 6, look.hair);
  B.rect(ax - 3, ay - 15, 6, 1, hs);
  B.rect(ax - 4, ay - 18, 1, 3, look.skin); // ears
  B.rect(ax + 3, ay - 18, 1, 3, look.skin);
  if (look.style === 'long') {
    B.rect(ax - 4, ay - 19, 8, 3, look.hair);
    B.rect(ax - 4, ay - 16, 8, 4, look.hair);
    B.rect(ax - 4, ay - 13, 8, 1, hs);
  } else if (look.style === 'spiky') {
    B.px(ax - 3, ay - 21, look.hair); B.px(ax - 1, ay - 22, look.hair); B.px(ax + 1, ay - 21, look.hair); B.px(ax + 2, ay - 22, look.hair);
  } else {
    B.rect(ax - 3, ay - 21, 6, 1, look.hair);
  }
  B.px(ax - 2, ay - 19, mixC(look.hair, PAL.white, 0.25));
}

function drawPlayerFront(B, ax, ay, t, typing, frame, blink) {
  const L = LOOK.you;
  const jacket = L.shirt, js = shadeC(jacket, 0.72);
  B.rect(ax - 4, ay - 12, 8, 9, jacket);
  B.rect(ax - 5, ay - 12, 10, 2, shadeC(jacket, 1.15));
  B.rect(ax - 1, ay - 12, 2, 5, C('#ffffff')); // inner shirt
  B.rect(ax - 6, ay - 11, 2, 6, js);
  B.rect(ax + 4, ay - 11, 2, 6, js);
  const af = typing ? frame : 0;
  B.rect(ax - 5, ay - 5 + (af ? 1 : 0), 2, 2, L.skin);
  B.rect(ax + 3, ay - 5 + (af ? 0 : 1), 2, 2, L.skin);
  // head
  B.rect(ax - 3, ay - 20, 6, 7, L.skin);
  B.rect(ax - 4, ay - 22, 8, 3, L.hair);
  B.rect(ax - 4, ay - 19, 1, 3, L.hair);
  B.rect(ax + 3, ay - 19, 1, 3, L.hair);
  B.rect(ax - 1, ay - 13, 2, 1, shadeC(L.skin, 0.8));
  // headphones
  B.rect(ax - 5, ay - 19, 1, 4, PAL.cyan);
  B.rect(ax + 4, ay - 19, 1, 4, PAL.cyan);
  B.rect(ax - 4, ay - 23, 8, 1, PAL.cyan);
  // eyes + mouth
  if (blink) {
    B.px(ax - 2, ay - 17, shadeC(L.skin, 0.6)); B.px(ax + 1, ay - 17, shadeC(L.skin, 0.6));
  } else {
    B.px(ax - 2, ay - 17, PAL.ink); B.px(ax + 1, ay - 17, PAL.ink);
    B.px(ax - 2, ay - 18, PAL.white); B.px(ax + 1, ay - 18, PAL.white);
  }
  B.rect(ax - 1, ay - 15, 2, 1, C('#4a2616'));
}

/* ============================================================
   HD characters (drawn at ×2 on the upscaled buffer): more pixels for faces, hair, clothes
   and motion. Each role has its own look: the coder in a hoodie, the artist with a bun
   and a pencil, the designer in headphones, the marketer with a ponytail and a blazer.
   ============================================================ */
// seated legs as small iso boxes (drawn with PS = 2): thighs on the seat, shins down to the floor, sneakers;
// dir = the way the person faces in grid units ([0,-1] right-wall desks, [-1,0] left-wall desks, [1,0] player)
const PANTS = { prog: '#2b4a8a', art: '#3a2a55', des: '#2a2e44', mkt: '#1e2240', you: '#232a5a', npc: '#262b66' };
function drawLegsHD(B, cx, cy, dir, pants, t, seed, tap) {
  const pc0 = C(pants), pT = shadeC(pc0, 1.25), pL = pc0, pR = shadeC(pc0, 0.7);
  const shoe = C('#f2f0ff'), shoeL = C('#c9c4ec'), shoeR = C('#9a94c8'), sole = C('#3a3f8a');
  const [dx, dy] = dir, px = -dy, py = dx; // perpendicular
  for (const side of [-1, 1]) {
    const o = side * 0.13, w = 0.15;
    const ox = cx + px * o, oy = cy + py * o;
    const tapZ = tap && side === 1 && Math.floor(t * 6 + seed) % 2 ? 0.8 : 0;
    // thigh: from the seat centre forward 0.42
    const ax = Math.min(ox, ox + dx * 0.42) - (dx ? 0 : w / 2), ay = Math.min(oy, oy + dy * 0.42) - (dy ? 0 : w / 2);
    isoBox(B, ax, ay, 8.6, dx ? 0.42 : w, dy ? 0.42 : w, 2.2, pT, pL, pR);
    // shin straight down at the knee
    const kx = ox + dx * 0.42, ky = oy + dy * 0.42;
    isoBox(B, kx - (dx ? (dx > 0 ? 0 : 0.14) : w / 2), ky - (dy ? (dy > 0 ? 0 : 0.14) : w / 2), 1.2 + tapZ, dx ? 0.14 : w, dy ? 0.14 : w, 8 - tapZ, pL, pL, pR);
    // sneaker pointing forward
    const fx = kx + dx * 0.02, fy = ky + dy * 0.02;
    const sx = Math.min(fx, fx + dx * 0.24) - (dx ? 0 : (w + 0.02) / 2), sy = Math.min(fy, fy + dy * 0.24) - (dy ? 0 : (w + 0.02) / 2);
    isoBox(B, sx, sy, tapZ, dx ? 0.24 : w + 0.02, dy ? 0.24 : w + 0.02, 0.6, sole, sole, sole);
    isoBox(B, sx, sy, 0.6 + tapZ, dx ? 0.24 : w + 0.02, dy ? 0.24 : w + 0.02, 1.4, shoe, shoeL, shoeR);
  }
}
const HD_ROLE = { prog: 'hoodie', art: 'bun', des: 'phones', mkt: 'blazer' };
function hdShade(c, k) { return shadeC(c, k); }
function drawWorkerHD(B, X, Y, look, role, typing, t, seed, mood) {
  const kind = HD_ROLE[role] || 'hoodie';
  const sh = look.shirt, s1 = hdShade(sh, 0.78), s2 = hdShade(sh, 0.58), hl = mixC(sh, PAL.white, 0.28);
  const hair = look.hair, h1 = hdShade(hair, 0.72), h2 = mixC(hair, PAL.white, 0.3), skin = look.skin, sk1 = hdShade(skin, 0.8);
  const tk = typing ? Math.floor(t * 9 + seed) % 2 : 0;
  const breathe = Math.sin(t * 1.7 + seed) > 0.75 ? 1 : 0;
  const by = Y - breathe; // whole upper body rises a little when breathing
  // upper arms + elbows (hands are in front of the body, on the keyboard)
  B.rect(X - 13, by - 21, 4, 11, s1); B.rect(X + 9, by - 21, 4, 11, s2);
  B.ell(X - 11, by - 9 + tk, 2.5, 2, s2); B.ell(X + 11, by - 9 + (1 - tk), 2.5, 2, s2);
  // torso with rounded shoulders
  B.poly([[X - 10, by - 23], [X + 10, by - 23], [X + 9, by - 4], [X - 9, by - 4]], sh);
  B.ell(X - 9, by - 21, 3.5, 3, sh); B.ell(X + 9, by - 21, 3.5, 3, s1);
  B.poly([[X + 4, by - 23], [X + 10, by - 23], [X + 9, by - 4], [X + 4, by - 4]], s1);
  B.rect(X - 9, by - 24, 7, 1, hl); B.rect(X - 10, by - 22, 1, 6, hl);
  B.rect(X, by - 19, 1, 12, s2); // spine fold
  B.rect(X - 9, by - 6, 18, 2, s2); // waist
  if (kind === 'hoodie') {
    B.ell(X, by - 23, 7.5, 3.5, s2); B.ell(X, by - 23.5, 5.5, 2, s1);
    B.rect(X - 2, by - 16, 5, 1, hdShade(sh, 0.5)); // pocket seam
  } else if (kind === 'bun') {
    B.line(X - 8, by - 23, X + 6, by - 6, s2); B.line(X + 8, by - 23, X - 6, by - 6, s2); // apron straps
    B.ell(X + 7, by - 12, 1.5, 1.5, PAL.yel);
  } else if (kind === 'phones') {
    B.rect(X - 4, by - 17, 8, 6, hdShade(sh, 0.66)); B.rect(X - 3, by - 16, 6, 4, mixC(look.screen, sh, 0.35)); // print on the back
  } else {
    B.rect(X, by - 23, 1, 19, s2); B.rect(X - 1, by - 8, 3, 4, s2); // blazer seam + vent
    B.poly([[X - 6, by - 24], [X + 6, by - 24], [X + 3, by - 20], [X - 3, by - 20]], hdShade(sh, 0.5)); // collar
  }
  // neck
  B.rect(X - 2, by - 28, 5, 5, sk1);
  // head
  const hx = X, hy = by - 35;
  B.ell(hx - 6.5, hy + 1, 1.6, 2.6, skin); B.ell(hx + 6.5, hy + 1, 1.6, 2.6, sk1); // ears
  B.ell(hx, hy, 6.5, 7, hair);
  B.ell(hx + 2, hy + 1, 4, 5.5, h1); // shading on the far side
  B.ell(hx - 2, hy - 2, 3.5, 4, hair);
  if (kind === 'bun') {
    B.ell(hx, hy - 8, 3.5, 3, hair); B.ell(hx + 1, hy - 7.5, 2, 1.8, h1); B.rect(hx - 1, hy - 10, 2, 1, h2);
    B.line(hx - 4, hy - 12, hx + 4, hy - 5, PAL.yel); B.px(hx + 4, hy - 5, C('#ff8a3d')); // pencil in the bun
  } else if (kind === 'blazer') {
    const sw = Math.round(Math.sin(t * 2.2 + seed) * 1.2);
    B.ell(hx + sw, hy + 8, 2.5, 4, hair); B.ell(hx + sw + 1, hy + 9, 1.5, 3, h1); B.rect(hx - 1 + sw, hy + 4, 3, 2, PAL.mag); // ponytail + scrunchie
  } else if (look.style === 'spiky') {
    for (const [dx, h] of [[-5, 3], [-2, 5], [1, 4], [4, 5]]) B.poly([[hx + dx - 2, hy - 5], [hx + dx + 2, hy - 5], [hx + dx + 1, hy - 5 - h]], hair);
  }
  // hair highlights
  B.rect(hx - 4, hy - 5, 3, 1, h2); B.rect(hx - 5, hy - 3, 1, 3, h2); B.px(hx - 1, hy - 6, h2);
  if (kind === 'phones') {
    B.arc(hx, hy - 1, 7.5, 8, 0.15, Math.PI - 0.15, PAL.cyan, 2);
    B.ell(hx - 7.5, hy + 1, 2, 3, C('#141845')); B.ell(hx + 7.5, hy + 1, 2, 3, C('#141845'));
    B.px(hx - 8, hy, PAL.cyan); B.px(hx + 8, hy, PAL.cyan);
  }
  if (mood < 35) { B.px(hx + 7, hy - 6, C('#7ac8ff')); B.px(hx + 7, hy - 5, C('#7ac8ff')); } // sweat drop
}

function drawPlayerHD(B, X, Y, t, typing, blink, tap) {
  const L = LOOK.you, j = L.shirt, j1 = shadeC(j, 0.78), j2 = shadeC(j, 0.58), jh = mixC(j, PAL.white, 0.3);
  const skin = L.skin, sk1 = shadeC(skin, 0.82), sk2 = shadeC(skin, 0.68), hair = L.hair, hh = mixC(hair, PAL.white, 0.3);
  const tk = typing ? Math.floor(t * 9) % 2 : 0;
  const bob = tap ? 1 : 0;
  const y = Y - bob;
  // arms reaching to the laptop
  B.rect(X - 13, y - 21, 4, 12, j1); B.rect(X + 9, y - 21, 4, 12, j2);
  B.ell(X - 10, y - 8 + tk, 3, 2.2, skin); B.ell(X + 10, y - 8 + (1 - tk), 3, 2.2, sk1);
  // jacket, shirt, lanyard
  B.poly([[X - 10, y - 23], [X + 10, y - 23], [X + 9, y - 4], [X - 9, y - 4]], j);
  B.ell(X - 9, y - 21, 3.5, 3, j); B.ell(X + 9, y - 21, 3.5, 3, j1);
  B.poly([[X + 4, y - 23], [X + 10, y - 23], [X + 9, y - 4], [X + 4, y - 4]], j1);
  B.poly([[X - 3, y - 23], [X + 3, y - 23], [X + 1, y - 10], [X - 1, y - 10]], PAL.white);
  B.poly([[X - 6, y - 23], [X - 2, y - 23], [X - 1, y - 12], [X - 4, y - 18]], jh); // lapels
  B.poly([[X + 2, y - 23], [X + 6, y - 23], [X + 4, y - 18], [X + 1, y - 12]], j2);
  B.line(X - 3, y - 22, X, y - 13, PAL.cyan); B.line(X + 3, y - 22, X, y - 13, PAL.cyan); B.rect(X - 2, y - 13, 4, 3, PAL.cyan); B.rect(X - 1, y - 12, 2, 1, PAL.white); // badge
  // neck + head
  B.rect(X - 2, y - 27, 5, 4, sk1);
  const hx = X, hy = y - 35;
  B.ell(hx, hy, 6.5, 7.5, skin);
  B.ell(hx + 3, hy + 1, 3.5, 6, sk1);
  B.ell(hx - 6.5, hy + 1, 1.5, 2.5, skin); B.ell(hx + 6.5, hy + 1, 1.5, 2.5, sk1);
  // hair: top + fringe + sideburns
  B.ell(hx, hy - 5, 7, 4, hair); B.rect(hx - 7, hy - 5, 2, 6, hair); B.rect(hx + 5, hy - 5, 2, 6, hair);
  B.poly([[hx - 6, hy - 5], [hx + 1, hy - 5], [hx - 3, hy - 1]], hair);
  B.rect(hx - 3, hy - 8, 4, 1, hh); B.px(hx + 2, hy - 7, hh);
  // face
  B.rect(hx - 4, hy - 1, 3, 1, shadeC(hair, 1.2)); B.rect(hx + 1, hy - 1, 3, 1, shadeC(hair, 1.2)); // brows
  if (blink) { B.rect(hx - 4, hy + 1, 3, 1, sk2); B.rect(hx + 1, hy + 1, 3, 1, sk2); }
  else {
    B.rect(hx - 4, hy, 3, 3, PAL.white); B.rect(hx + 1, hy, 3, 3, PAL.white);
    B.rect(hx - 3, hy + 1, 2, 2, PAL.ink); B.rect(hx + 2, hy + 1, 2, 2, PAL.ink);
    B.px(hx - 3, hy + 1, PAL.white); B.px(hx + 2, hy + 1, PAL.white);
  }
  B.px(hx, hy + 3, sk2); B.px(hx, hy + 4, sk2); // nose
  B.blend(hx - 5, hy + 4, C('#ff6f91'), 0.35); B.blend(hx + 5, hy + 4, C('#ff6f91'), 0.35); // cheeks
  if (tap) { B.rect(hx - 2, hy + 5, 5, 2, C('#4a1616')); B.rect(hx - 1, hy + 5, 3, 1, PAL.white); }
  else { B.rect(hx - 2, hy + 6, 4, 1, C('#4a2616')); B.px(hx - 3, hy + 5, C('#4a2616')); B.px(hx + 2, hy + 5, C('#4a2616')); }
  // headphones with a soft glow
  B.arc(hx, hy - 1, 8, 9, 0.2, Math.PI - 0.2, PAL.cyan, 2);
  B.ell(hx - 8, hy + 1, 2, 3.2, C('#1a1e4d')); B.ell(hx + 8, hy + 1, 2, 3.2, C('#1a1e4d'));
  B.rect(hx - 9, hy, 1, 3, PAL.cyan); B.rect(hx + 8, hy, 1, 3, PAL.cyan);
  if (Math.floor(t * 2) % 2) B.glow(hx, hy - 9, 7, PAL.cyan, 0.18);
}

function drawWalkerHD(B, X, Y, t, pause) {
  const L = LOOK.npc, f = pause ? 0 : Math.floor(t * 8) % 4;
  const step = [0, 2, 0, -2][f], lift = [0, 1, 0, 1][f];
  // shadow
  for (let i = -7; i <= 7; i++) B.blend(X + i, Y, PAL.ink, 0.45 * (1 - Math.abs(i) / 8)), B.blend(X + i, Y + 1, PAL.ink, 0.25 * (1 - Math.abs(i) / 8));
  // legs + shoes
  const pants = C('#262b66'), shoe = C('#0f1130');
  B.rect(X - 4 + step, Y - 10 + lift, 3, 10 - lift, pants); B.rect(X + 1 - step, Y - 10 + (1 - lift), 3, 10 - (1 - lift), shadeC(pants, 0.8));
  B.rect(X - 5 + step, Y - 2, 5, 2, shoe); B.rect(X - step, Y - 2, 5, 2, shoe);
  // body
  B.poly([[X - 6, Y - 22], [X + 6, Y - 22], [X + 6, Y - 9], [X - 6, Y - 9]], L.shirt);
  B.rect(X + 2, Y - 22, 4, 13, shadeC(L.shirt, 0.78));
  // the parcel
  const by = Y - 20 + (f % 2);
  B.rect(X - 6, by, 13, 9, C('#c98a4b')); B.rect(X - 6, by, 13, 2, C('#e3a86a')); B.rect(X + 3, by, 4, 9, C('#9a6232'));
  B.rect(X - 1, by, 2, 9, C('#f4dcae')); B.rect(X - 6, by + 4, 13, 1, C('#f4dcae'));
  B.ell(X - 7, by + 5, 2, 2, L.skin); B.ell(X + 7, by + 5, 2, 2, shadeC(L.skin, 0.8)); // hands
  // head + cap
  const hx = X, hy = Y - 29;
  B.ell(hx, hy, 5, 5.5, L.skin); B.ell(hx + 2, hy + 1, 3, 4, shadeC(L.skin, 0.84));
  B.ell(hx, hy - 3, 5.5, 3, PAL.cyan); B.rect(hx - 7, hy - 2, 7, 2, shadeC(PAL.cyan, 0.7)); B.px(hx - 1, hy - 5, PAL.white);
  B.rect(hx - 3, hy, 2, 2, PAL.ink); B.rect(hx + 1, hy, 2, 2, PAL.ink); B.rect(hx - 1, hy + 3, 3, 1, C('#4a2616'));
  if (pause && Math.floor(t * 2) % 2) { // a little heart when he drops the parcel off
    B.ell(X + 11, Y - 40, 2.5, 2.5, PAL.mag); B.ell(X + 15, Y - 40, 2.5, 2.5, PAL.mag); B.poly([[X + 8.5, Y - 39], [X + 17.5, Y - 39], [X + 13, Y - 34]], PAL.mag);
  }
}

function drawCatHD(B, X, Y, t) {
  const k = C('#14142b'), k2 = C('#2a2a4d'), k3 = C('#3a3a66'), e = C('#ffe14d');
  const tail = Math.sin(t * 2.2) * 3;
  // tail curling up behind
  for (let i = 0; i < 9; i++) { const a = i / 8; B.ell(X + 8 + a * 3, Y - 2 - a * 8 + Math.sin(a * 3) * tail * a, 1.6, 1.6, k); }
  // body loaf
  B.ell(X, Y - 4, 8, 4.5, k); B.ell(X - 1, Y - 6, 6, 2, k2); B.rect(X - 6, Y - 1, 3, 1, k3); B.rect(X - 1, Y - 1, 3, 1, k3);
  // head
  const hx = X - 8, hy = Y - 10;
  B.ell(hx, hy, 5, 4.2, k);
  B.poly([[hx - 5, hy - 2], [hx - 2, hy - 3], [hx - 4.5, hy - 7]], k); B.poly([[hx + 1, hy - 3], [hx + 4.5, hy - 2], [hx + 3.5, hy - 7]], k);
  B.px(hx - 4, hy - 4, C('#ff6f91')); B.px(hx + 3, hy - 4, C('#ff6f91'));
  const blink = (t % 5.3) > 5.05;
  if (blink) { B.rect(hx - 3, hy, 2, 1, e); B.rect(hx + 1, hy, 2, 1, e); }
  else { B.rect(hx - 3, hy - 1, 2, 2, e); B.rect(hx + 1, hy - 1, 2, 2, e); B.px(hx - 2, hy - 1, PAL.ink); B.px(hx + 2, hy - 1, PAL.ink); B.px(hx - 2, hy, PAL.ink); B.px(hx + 2, hy, PAL.ink); }
  B.px(hx, hy + 2, C('#ff6f91'));
  B.rect(hx - 2, hy + 2, 5, 1, k3); // chest patch
}

/* ---------- props ---------- */
function drawPlant(B, gx, gy, t, seed, big) {
  const p = P(gx, gy, 0);
  isoBox(B, gx - 0.28, gy - 0.28, 0, 0.56, 0.56, 8, C('#d9d4ff'), C('#8b86d6'), C('#625cae'), C('#ffffff'));
  const sway = Math.round(Math.sin(t * 1.3 + seed) * 0.8);
  const g1 = C('#2fd36b'), g2 = C('#1fa352'), g3 = C('#7af59a');
  const bx = p[0], by = p[1] - 8;
  const h = big ? 14 : 10;
  B.rect(bx - 1, by - h, 2, h, g2);
  const leaves = [[-5, -h + 4, 4, 3], [2, -h + 3, 4, 3], [-4, -h + 8, 3, 3], [2, -h + 7, 4, 3], [-2, -h - 2, 4, 4], [-6, -h + 1, 3, 3], [4, -h + 1, 3, 3]];
  for (const l of leaves) {
    B.rect(bx + l[0] + (l[1] < -h + 2 ? sway : 0), by + l[1], l[2], l[3], g1);
    B.rect(bx + l[0] + (l[1] < -h + 2 ? sway : 0), by + l[1], l[2], 1, g3);
  }
}

function drawCoffee(B, t) {
  const gx = 1.1, gy = 0.15;
  isoBox(B, gx, gy, 0, 1.3, 0.8, 11, C('#8b86d6'), C('#5f5ab0'), C('#443f8a'), C('#c9c6ff'));
  isoBox(B, gx + 0.2, gy + 0.1, 11, 0.55, 0.5, 9, C('#3c3f94'), C('#2a2d70'), C('#1d2058'), C('#6f74e0'));
  isoBox(B, gx + 0.85, gy + 0.25, 11, 0.22, 0.22, 3, C('#ffffff'), C('#e6e6ff'), C('#b4b4e6'));
  const p = P(gx + 0.8, gy + 0.2, 16);
  for (let i = 0; i < 3; i++) {
    const ph = (t * 1.4 + i * 0.7) % 2;
    B.blend(p[0] + Math.round(Math.sin(t * 3 + i) * 1), p[1] - Math.floor(ph * 5), PAL.white, 0.7 - ph * 0.3);
  }
  const lp = P(gx + 0.4, gy + 0.62, 17);
  B.px(lp[0], lp[1], Math.floor(t * 2) % 2 ? PAL.grn : PAL.red);
}
function drawRack(B, t) {
  const gx = 0.15, gy = 1.3;
  isoBox(B, gx, gy, 0, 0.9, 1.2, 30, C('#2b2f66'), C('#1a1d45'), C('#12143a'), C('#5560c8'));
  // front face (+gx) is at gx+0.9
  for (let i = 0; i < 6; i++) {
    const z = 4 + i * 4.2;
    faceGX(B, gy + 0.12, gy + 1.08, gx + 0.9, z, z + 2.4, C('#0a0c24'));
    const led = hash01(i * 7 + Math.floor(t * (1.2 + i * 0.3))) > 0.5;
    const pp = P(gx + 0.9, gy + 0.2, z + 1.2);
    B.px(pp[0], pp[1], led ? PAL.grn : C('#0f5a2e'));
    const p2 = P(gx + 0.9, gy + 0.4, z + 1.2);
    B.px(p2[0], p2[1], hash01(i * 3 + Math.floor(t * 3)) > 0.7 ? PAL.cyan : C('#0e4f66'));
  }
}
function drawLamp(B, t) {
  const gx = 3.5, gy = 6.3;
  isoBox(B, gx - 0.07, gy - 0.07, 0, 0.14, 0.14, 26, C('#444a9a'), C('#2a2f70'), C('#1d2158'));
  isoBox(B, gx - 0.25, gy - 0.25, 26, 0.5, 0.5, 5, C('#ffe9a8'), C('#ff9bd6'), C('#d6489f'));
  const p = P(gx, gy, 28);
  B.glow(p[0], p[1] + 6, 24, C('#ff6fd2'), 0.2);
  const q = P(gx, gy, 0);
  B.glow(q[0], q[1], 18, C('#ff6fd2'), 0.12);
}
function drawLounge(B, t) {
  // mini arcade cabinet against the far right
  isoBox(B, 8.0, 3.9, 0, 0.8, 0.8, 24, C('#3b3f99'), C('#2a2e72'), C('#1b1e52'), C('#7f86f0'));
  faceGX(B, 3.95, 4.65, 8.8, 12, 21, C('#0a0e2e'));
  const pg = (z, a, c) => { const p = P(8.8, 4.0 + a, z); B.px(p[0], p[1], c); };
  const s = Math.floor(t * 4) % 4;
  pg(17, 0.1 + s * 0.12, PAL.yel);
  pg(14, 0.5 - s * 0.1, PAL.cyan);
  pg(19, 0.3, PAL.mag);
  // beanbags
  isoBox(B, 7.6, 5.1, 0, 0.9, 0.9, 6, C('#ff7ae0'), C('#cf3fb0'), C('#9b2a86'), C('#ffd1f4'));
  isoBox(B, 7.9, 6.3, 0, 0.9, 0.9, 6, C('#7ad8ff'), C('#3b9fe0'), C('#2a78b4'), C('#d4f3ff'));
}
function drawMonitor2(B, t) {
  // extra monitor on the player's desk
  isoBox(B, 6.45, 5.0, 13, 0.1, 0.55, 9, PAL.bezel, PAL.bezel, PAL.bezelR);
  faceGX(B, 5.04, 5.5, 6.59, 14.5, 21, C('#0a1230'));
  lineGX(B, 5.06, 5.45, 6.59, 19, PAL.cyan);
  lineGX(B, 5.06, 5.3, 6.59, 17, PAL.mag);
  lineGX(B, 5.06, 5.4, 6.59, 15.5, PAL.cyan);
}

function drawCat(B, x, y, t) {
  const k = C('#14142b'), k2 = C('#2a2a4d'), e = C('#ffe14d');
  // body
  B.rect(x - 4, y - 4, 8, 4, k);
  B.rect(x - 3, y - 5, 6, 1, k);
  B.rect(x - 3, y - 4, 4, 1, k2);
  // head
  B.rect(x - 6, y - 7, 5, 4, k);
  B.px(x - 6, y - 8, k); B.px(x - 2, y - 8, k);
  B.px(x - 5, y - 6, e); B.px(x - 3, y - 6, e);
  // tail
  const tw = Math.floor(t * 1.8) % 3;
  B.rect(x + 4, y - 2 - (tw === 1 ? 1 : 0), 2, 1, k);
  B.rect(x + 5, y - 3 - (tw === 1 ? 1 : 0) - (tw === 2 ? 1 : 0), 1, 2, k);
  // paws
  B.px(x - 5, y, k2); B.px(x - 2, y, k2);
}

function drawBadge(B, x, y, kind, t) {
  if (kind === 'plus') {
    if (Math.floor(t * 2.5) % 2) return;
    B.rect(x - 4, y - 4, 9, 9, PAL.ink);
    B.rect(x - 3, y - 3, 7, 7, PAL.grn);
    B.rect(x - 1, y - 2, 3, 5, PAL.white);
    B.rect(x - 2, y - 1, 5, 3, PAL.white);
  } else if (kind === 'warn') {
    if (Math.floor(t * 3) % 2) return;
    B.rect(x - 3, y - 6, 7, 10, PAL.ink);
    B.rect(x - 2, y - 5, 5, 8, PAL.red);
    B.rect(x, y - 4, 1, 4, PAL.white);
    B.rect(x, y + 1, 1, 1, PAL.white);
  } else if (kind === 'bolt') {
    const s = Math.floor(t * 4) % 2;
    B.rect(x, y - 5 - s, 2, 3, PAL.yel);
    B.rect(x - 1, y - 3 - s, 3, 2, PAL.yel);
    B.rect(x - 1, y - 2 - s, 2, 3, PAL.yel);
  } else if (kind === 'zz') {
    const s = Math.floor(t * 1.5) % 3;
    B.rect(x + s, y - 6 - s * 2, 3, 1, C('#c9c6ff'));
    B.px(x + 1 + s, y - 5 - s * 2, C('#c9c6ff'));
    B.rect(x + s, y - 4 - s * 2, 3, 1, C('#c9c6ff'));
  }
}

/* ---------- life outside the window & small ambient touches ---------- */
function inGlass(k, v) { return k >= WIN.k0 + 2 && k <= WIN.k1 - 2 && v >= WIN.v0 + 1 && v <= WIN.v1 - 2; }
function glassPx(B, k, v, c, a) { if (inGlass(k, v)) { if (a === undefined) wallPx(B, 'R', k, v, c); else wallBlend(B, 'R', k, v, c, a); } }
function drawWindowLife(B, st, t) {
  const f = clampN(st.f || 0, 0, 1);
  const night = f > 0.8 || f < 0.04;
  const k0 = WIN.k0 + 2, k1 = WIN.k1 - 2, span = k1 - k0;
  // a plane crossing the sky, its light blinking
  {
    const P = 47, ph = (t % P) / P;
    if (ph < 0.55) {
      const k = Math.round(k1 - (ph / 0.55) * (span + 10));
      const v = 45 + Math.round(Math.sin(ph * 6) * 0.5);
      const body = night ? C('#9aa4d8') : C('#f4f6ff');
      for (let i = 0; i < 5; i++) glassPx(B, k + i, v, body);
      glassPx(B, k + 3, v + 1, body); glassPx(B, k + 2, v - 1, body); glassPx(B, k + 4, v + 1, body);
      if (Math.floor(t * 2.5) % 2) glassPx(B, k + 5, v, PAL.red);
      for (let i = 1; i < 10; i++) glassPx(B, k + 5 + i, v, PAL.white, 0.25 - i * 0.022); // contrail
    }
  }
  // birds by day, shooting stars by night
  if (!night) {
    const P = 29, ph = (t % P) / P;
    if (ph < 0.6) {
      const base = k0 + (ph / 0.6) * (span + 16) - 8;
      for (let b = 0; b < 3; b++) {
        const k = Math.round(base - b * 5), v = 38 + b * 2 + Math.round(Math.sin(t * 3 + b) * 1);
        const flap = Math.floor(t * 6 + b) % 2;
        const col = C('#1d1f4a');
        glassPx(B, k, v, col); glassPx(B, k - 1, v + flap, col); glassPx(B, k + 1, v + flap, col);
      }
    }
  } else {
    const P = 13, ph = (t % P) / P;
    if (ph < 0.12) {
      const q = ph / 0.12, k = Math.round(k0 + 12 + q * 40), v = Math.round(48 - q * 10);
      for (let i = 0; i < 6; i++) glassPx(B, k - i, v + Math.round(i / 4), PAL.white, 0.9 - i * 0.14);
    }
  }
  // rain on the glass while the rainy track plays
  if (st.rain) {
    for (let i = 0; i < 26; i++) {
      const k = k0 + Math.floor(hash01(i * 7 + 1) * span);
      const v = WIN.v1 - 2 - Math.floor(((t * (14 + hash01(i) * 10) + hash01(i * 3) * 40) % 34));
      glassPx(B, k, v, C('#bfe6ff'), 0.55); glassPx(B, k, v + 1, C('#bfe6ff'), 0.3);
    }
  }
}
// neon sign hum: now and then one word flickers
function drawNeonFlicker(B, t) {
  const w = Math.floor(t / 3.7) % THEME.words.length;
  const on = (t % 3.7) < 0.18 && Math.floor(t * 20) % 2 === 0;
  if (!on) return;
  const k0 = 22, k1 = 60;
  const word = THEME.words[w][0];
  const tw = textWidth(word);
  const kk = k0 + 1 + Math.floor((k1 - k0 - 1 - tw) / 2);
  wallGlyphs(B, 'L', kk, 46 - w * 8 - 1, word, C('#1a1d4a'), null);
}
// golden dust floating in the window light by day
function drawDust(B, st, t) {
  const f = clampN(st.f || 0, 0, 1);
  if (f > 0.78) return;
  for (let i = 0; i < 14; i++) {
    const gx = 4.6 + hash01(i * 5) * 3.6, gy = 1.2 + hash01(i * 11) * 3.2;
    const z = 14 + ((t * (2 + hash01(i) * 3) + hash01(i * 2) * 30) % 30);
    const p = P(gx + Math.sin(t * 0.6 + i) * 0.15, gy, z);
    B.blend(p[0], p[1], C('#fff2c4'), 0.35 + 0.3 * Math.sin(t * 2 + i));
  }
}

/* ---------- dynamic layer ---------- */
function drawDynamic(B, st, t) {
  drawWindowLife(B, st, t);
  drawNeonFlicker(B, t);
  const items = st.items || {};
  const typingGlobal = !!st.working;
  const frame = Math.floor(t * 5) % 2;
  // clock hands (left wall)
  {
    const mins = Math.floor((st.f || 0) * 720) + 480;
    const hh = (mins / 60) % 12, mm = mins % 60;
    const kc = 82, vc = 38;
    const hand = (len, ang, col) => {
      for (let r = 0; r <= len; r++) wallPx(B, 'L', kc + Math.round(Math.sin(ang) * r), vc + Math.round(Math.cos(ang) * r), col);
    };
    hand(3, (hh / 12) * Math.PI * 2, PAL.white);
    hand(5, (mm / 60) * Math.PI * 2, PAL.mag);
  }
  const list = [];
  const add = (key, fn) => list.push({ key, fn });

  // stations
  const order = ['prog', 'art', 'des', 'mkt'];
  order.forEach((role, idx) => {
    const s = STATION[role];
    const info = (st.staff && st.staff[role]) || { lvl: 0, mood: 80 };
    const look = LOOK[role];
    const hired = info.lvl > 0;
    const key = (s.side === 'R' ? s.gx + 2 + s.chair[1] + 0.5 : s.gy + 2 + s.chair[0] + 0.5);
    add(key, () => {
      drawDesk(B, s);
      drawMonitor(B, s, look, hired, t, idx + 1, role, hired && typingGlobal);
      // mug
      if (s.side === 'R') isoBox(B, s.gx + 0.15, s.gy + 0.6, 13, 0.2, 0.2, 3, C('#ffffff'), C('#d9d9ff'), C('#aaaadd'));
      else isoBox(B, s.gx + 0.6, s.gy + 0.15, 13, 0.2, 0.2, 3, C('#ffffff'), C('#d9d9ff'), C('#aaaadd'));
      const chairAccent = items.chairs ? C('#22b8d6') : C('#3b3f99');
      drawChairParts(B, s, chairAccent);
      const a = P(s.chair[0], s.chair[1], 9);
      if (hired) {
        const badge = (BB) => {
          if (info.mood < 35) drawBadge(BB, a[0] + 8, a[1] - 22, 'warn', t);
          else if (st.turbo) drawBadge(BB, a[0] + 7, a[1] - 22, 'bolt', t);
          else if (!typingGlobal) drawBadge(BB, a[0] + 6, a[1] - 22, 'zz', t);
        };
        if (HDQ) {
          // a lower mesh backrest in HD so the shoulders and back of the worker show
          HDQ.push((H) => { PS = 2; drawLegsHD(H, s.chair[0], s.chair[1], s.side === 'R' ? [0, -1] : [-1, 0], PANTS[role], t, idx, typingGlobal); PS = 1; drawWorkerHD(H, a[0] * 2, a[1] * 2, look, role, typingGlobal, t, idx, info.mood); PS = 2; drawChairBack(H, s, shadeC(chairAccent, 0.6), 5.5); PS = 1; badge(up2(H)); });
        } else {
          drawWorkerBack(B, a[0], a[1], look, typingGlobal, frame, t);
          drawChairBack(B, s, chairAccent);
          badge(B);
        }
      } else {
        drawChairBack(B, s, chairAccent);
        if (st.canHire && st.canHire[role]) drawBadge(B, a[0], a[1] - 12, 'plus', t);
      }
    });
  });

  // player station (center)
  add(12.3, () => {
    const cx = 5.1, cy = 5.1;
    const chairAccent = items.chairs ? C('#22b8d6') : C('#3b3f99');
    isoQuad(B, cx - 0.5, cy - 0.5, cx + 0.5, cy + 0.5, 0, C('#241660'));
    isoBox(B, cx - 0.08, cy - 0.08, 0, 0.16, 0.16, 6, C('#252a66'), C('#1a1e4d'), C('#12163a'));
    // backrest (behind the player)
    isoBox(B, cx - 0.42, cy - 0.3, 9, 0.1, 0.6, 11, shadeC(chairAccent, 1.2), chairAccent, shadeC(chairAccent, 0.6));
    isoBox(B, cx - 0.36, cy - 0.36, 6, 0.72, 0.72, 3, chairAccent, shadeC(chairAccent, 0.78), shadeC(chairAccent, 0.6));
    const a = P(cx, cy, 9);
    const blink = (t % 4.2) > 4.0, typingP = typingGlobal || st.tapFlash > 0;
    const desk = (DB) => {
      const H = 13;
      isoBox(DB, 5.9, 4.4, 0, 0.1, 0.12, H - 3, PAL.woodL, PAL.woodL, PAL.woodR);
      isoBox(DB, 5.9, 6.52, 0, 1.2, 0.12, H - 3, PAL.woodL, PAL.woodL, PAL.woodR);
      isoBox(DB, 6.9, 4.4, 0, 0.1, 0.12, H - 3, PAL.woodL, PAL.woodL, PAL.woodR);
      isoBox(DB, 5.9, 4.4, H - 3, 1.2, 2.24, 3, PAL.woodT, PAL.woodL, PAL.woodR, PAL.woodE);
      // laptop (lid facing +gx)
      isoBox(DB, 6.05, 5.1, H, 0.45, 0.6, 1, C('#2b2f66'), C('#1a1d45'), C('#14163a'));
      isoBox(DB, 6.45, 5.1, H, 0.07, 0.6, 9, C('#3a3f8a'), C('#2a2e72'), C('#1d2058'));
      const lp = P(6.52, 5.4, H + 5);
      DB.rect(lp[0], lp[1], 2 * PS, PS, t % 1.6 < 0.9 ? PAL.cyan : PAL.mag);
      DB.glow(lp[0] - 9 * PS, lp[1] + 4 * PS, 14 * PS, PAL.cyan, 0.1);
      if (items.monitor2) drawMonitor2(DB, t);
      isoBox(DB, 6.1, 4.55, H, 0.2, 0.2, 3, C('#ffffff'), C('#d9d9ff'), C('#aaaadd'));
    };
    const cp = P(6.5, 6.1, 13);
    if (HDQ) {
      desk(B);
      HDQ.push((HB) => { PS = 2; drawLegsHD(HB, cx, cy, [1, 0], PANTS.you, t, 0, typingP); PS = 1; drawPlayerHD(HB, a[0] * 2, a[1] * 2, t, typingP, blink, st.tapFlash > 0); PS = 2; desk(HB); PS = 1; drawCatHD(HB, cp[0] * 2, cp[1] * 2, t); });
    } else {
      drawPlayerFront(B, a[0], a[1], t, typingP, frame, blink);
      desk(B);
      drawCat(B, cp[0], cp[1], t);
    }
  });

  // a courier walks around the office now and then (pure function of time)
  {
    const period = 34, ph = (t % period) / period;
    if (ph < 0.62) {
      const path = [[1.7, 8.35], [8.35, 8.35], [8.35, 3.2]];
      const q = ph / 0.62; // 0..1 along out-and-back
      const u = q < 0.5 ? q * 2 : (1 - q) * 2;
      const segA = Math.hypot(path[1][0] - path[0][0], path[1][1] - path[0][1]), segB = Math.hypot(path[2][0] - path[1][0], path[2][1] - path[1][1]);
      let d = u * (segA + segB), gx, gy;
      if (d < segA) { gx = path[0][0] + (path[1][0] - path[0][0]) * (d / segA); gy = path[0][1]; }
      else { d -= segA; gx = path[1][0]; gy = path[1][1] + (path[2][1] - path[1][1]) * (d / segB); }
      const pause = Math.abs(u - 1) < 0.04;
      add(gx + gy, () => {
        if (HDQ) { const p = P(gx, gy, 0); HDQ.push((H) => drawWalkerHD(H, p[0] * 2, p[1] * 2, t, pause)); }
        else drawWalker(B, gx, gy, pause ? 0 : Math.floor(t * 6) % 2, t, pause);
      });
    }
  }
  // plants
  add(3.0, () => drawPlant(B, 0.55, 0.6, t, 1, true));
  add(20, () => drawPlant(B, 8.55, 0.6, t, 2, true));
  add(21, () => drawPlant(B, 0.6, 8.5, t, 3, true));
  if (items.coffee) add(2.6, () => drawCoffee(B, t));
  if (items.rack) add(3.1, () => drawRack(B, t));
  if (items.lamp) add(9.9, () => drawLamp(B, t));
  if (items.lounge) add(14.5, () => drawLounge(B, t));

  list.sort((a, b) => a.key - b.key);
  for (const o of list) o.fn();
  drawDust(B, st, t);
  drawParticles(B, st, t);
}

function drawWalker(B, gx, gy, frame, t, pause) {
  const p = P(gx, gy, 0);
  const x = p[0], y = p[1];
  const L = LOOK.npc;
  // shadow
  B.blend(x - 3, y, PAL.ink, 0.35); B.blend(x - 2, y, PAL.ink, 0.45); B.blend(x - 1, y, PAL.ink, 0.5); B.blend(x, y, PAL.ink, 0.5); B.blend(x + 1, y, PAL.ink, 0.45); B.blend(x + 2, y, PAL.ink, 0.35);
  // legs
  const lo = frame ? 1 : 0;
  B.rect(x - 2, y - 4 + lo, 2, 4 - lo, C('#262b66'));
  B.rect(x + 1, y - 4 + (1 - lo), 2, 4 - (1 - lo), C('#262b66'));
  // body + box
  B.rect(x - 3, y - 10, 7, 6, L.shirt);
  B.rect(x - 2, y - 9, 6, 4, C('#c98a4b')); B.rect(x - 2, y - 9, 6, 1, C('#e3a86a')); B.px(x + 1, y - 8, C('#74461f'));
  // head
  B.rect(x - 2, y - 15, 5, 5, L.skin);
  B.rect(x - 2, y - 16, 5, 2, L.hair);
  B.px(x - 1, y - 13, PAL.ink); B.px(x + 1, y - 13, PAL.ink);
  // cap
  B.rect(x - 3, y - 16, 7, 1, PAL.cyan);
  if (pause && Math.floor(t * 2) % 2) { B.rect(x + 4, y - 22, 5, 5, PAL.white); B.px(x + 6, y - 21, PAL.mag); B.px(x + 5, y - 20, PAL.mag); B.px(x + 7, y - 20, PAL.mag); B.px(x + 6, y - 19, PAL.mag); }
}

/* ---------- ambient particles (pure function of time → no state) ---------- */
function drawParticles(B, st, t) {
  const cols = [PAL.cyan, PAL.mag, PAL.yel, PAL.grn];
  // code bits rising from the screens of employees who are working
  if (st.working) {
    ['prog', 'art', 'des', 'mkt'].forEach((role, ri) => {
      const info = st.staff && st.staff[role];
      if (!info || info.lvl <= 0) return;
      const s = STATION[role];
      const base = s.side === 'R' ? P(s.gx + 0.6, s.gy + 0.4, 24) : P(s.gx + 0.4, s.gy + 0.6, 24);
      const n = st.turbo ? 4 : 2;
      for (let i = 0; i < n; i++) {
        const ph = (t * (0.5 + 0.1 * i) + hash01(ri * 17 + i * 5)) % 1;
        const x = base[0] + Math.round((hash01(ri * 31 + i * 11 + Math.floor(t * 0.5 + i)) - 0.5) * 10);
        const y = base[1] - Math.round(ph * 20);
        const c = cols[(ri + i) % 4];
        B.blend(x, y, c, 1 - ph);
        B.blend(x + 1, y, c, (1 - ph) * 0.7);
        if (i % 2 === 0) B.blend(x, y - 1, c, (1 - ph) * 0.5);
      }
    });
  }
  // hype: golden coins float up across the floor
  if (st.hype) {
    for (let i = 0; i < 7; i++) {
      const ph = (t * 0.35 + hash01(i * 23)) % 1;
      const g = P(1 + hash01(i * 7) * 7, 1 + hash01(i * 13) * 7, Math.round(ph * 40));
      B.blend(g[0], g[1], PAL.yel, 1 - ph);
      B.blend(g[0] + 1, g[1], C('#c8841a'), 1 - ph);
      B.blend(g[0], g[1] + 1, C('#c8841a'), 1 - ph);
      B.blend(g[0] + 1, g[1] + 1, PAL.yel, (1 - ph) * 0.8);
    }
  }
  // tap: sparks around the founder's laptop
  if (st.tapFlash > 0) {
    const a = P(6.3, 5.4, 18);
    const k = st.tapFlash / 0.5;
    for (let i = 0; i < 6; i++) {
      const ang = i * 1.047 + t * 3;
      const r = 4 + (1 - k) * 10;
      B.blend(a[0] + Math.round(Math.cos(ang) * r), a[1] + Math.round(Math.sin(ang) * r * 0.6), cols[i % 4], k);
    }
    B.glow(a[0], a[1], 10, PAL.cyan, 0.25 * k);
  }
  // dust motes drifting in the window light
  for (let i = 0; i < 8; i++) {
    const ph = (t * 0.05 + hash01(i * 41)) % 1;
    const x = 140 + Math.round(hash01(i * 3) * 100 + Math.sin(t * 0.7 + i) * 3);
    const y = 40 + Math.round(ph * 70);
    B.blend(x, y, PAL.white, 0.25 * Math.sin(ph * Math.PI));
  }
}

/* ---------- scene object ---------- */
/* ============================================================
   HD pass: the room is drawn at 256×176, then upscaled ×2 with Scale2x (EPX) so diagonals
   and round shapes get smooth pixel-art edges, and lit: soft light from monitors, the window,
   lamps and neon, contact shadows, a vignette and a colour grade per office theme.
   ============================================================ */
function scale2x(S, D) {
  const w = S.w, h = S.h, s = S.d, d = D.d, W = D.w;
  for (let y = 0; y < h; y++) {
    const row = y * w, up = (y > 0 ? y - 1 : y) * w, dn = (y < h - 1 ? y + 1 : y) * w, o0 = 2 * y * W, o1 = o0 + W;
    for (let x = 0; x < w; x++) {
      const P0 = s[row + x], A = s[up + x], D0 = s[dn + x], Cl = s[row + (x > 0 ? x - 1 : x)], B0 = s[row + (x < w - 1 ? x + 1 : x)];
      const X = 2 * x;
      if (Cl !== B0 && A !== D0) {
        d[o0 + X] = Cl === A ? A : P0;
        d[o0 + X + 1] = A === B0 ? B0 : P0;
        d[o1 + X] = D0 === Cl ? Cl : P0;
        d[o1 + X + 1] = B0 === D0 ? D0 : P0;
      } else { d[o0 + X] = d[o0 + X + 1] = d[o1 + X] = d[o1 + X + 1] = P0; }
    }
  }
}
// light map (×2 resolution, values ~0..1.6): rebuilt only when the theme, hour or furniture change
function buildLight(L, W, H, st) {
  const sky = skyAt(st.f || 0), night = sky.night, day = 1 - Math.abs((st.f || 0) - 0.45) * 1.6;
  const amb = 0.62 + 0.22 * clampN(day, 0, 1) - 0.12 * night;
  L.fill(amb);
  const add = (cx, cy, rx, ry, k) => {
    const x0 = Math.max(0, Math.floor(cx - rx)), x1 = Math.min(W - 1, Math.ceil(cx + rx)), y0 = Math.max(0, Math.floor(cy - ry)), y1 = Math.min(H - 1, Math.ceil(cy + ry));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const dx = (x - cx) / rx, dy = (y - cy) / ry, q = 1 - (dx * dx + dy * dy); if (q <= 0) continue;
      L[y * W + x] += k * q * q;
    }
  };
  const P2 = (gx, gy, z) => { const p = P(gx, gy, z); return [p[0] * 2, p[1] * 2]; };
  // daylight through the window, falling on the floor near the right wall
  const w = wallXY('R', 66, 34);
  add(w[0] * 2, w[1] * 2, 170, 70, 0.32 * clampN(day, 0.15, 1) * (1 - night * 0.8));
  { const f = P2(5.2, 1.8, 0); add(f[0], f[1], 150, 60, 0.26 * clampN(day, 0, 1) * (1 - night)); }
  // desk monitors and the player's laptop
  for (const role of ['prog', 'art', 'des', 'mkt']) {
    const sn = STATION[role], hired = st.staff && st.staff[role] && st.staff[role].lvl > 0;
    const m = sn.side === 'R' ? P2(sn.gx + 1.0, sn.gy + 0.9, 16) : P2(sn.gx + 0.9, sn.gy + 1.0, 16);
    add(m[0], m[1], 64, 40, hired ? 0.26 + 0.18 * night : 0.06);
  }
  { const l = P2(6.2, 5.3, 16); add(l[0], l[1], 70, 44, 0.24 + 0.2 * night); }
  // neon words on the left wall and the strip lights
  { const n = wallXY('L', 40, 34); add(n[0] * 2, n[1] * 2, 110, 80, 0.16 + 0.22 * night); }
  if (st.items && st.items.lamp) { const l = P2(1.2, 6.8, 24); add(l[0], l[1], 120, 80, 0.3 + 0.25 * night); }
  if (st.items && st.items.coffee) { const c = P2(1.1, 1.2, 14); add(c[0], c[1], 60, 36, 0.14 + 0.12 * night); }
  // vignette
  const cx = W / 2, cy = H * 0.52;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const dx = (x - cx) / (W * 0.62), dy = (y - cy) / (H * 0.62);
    L[y * W + x] *= 1 - 0.42 * Math.min(1, dx * dx + dy * dy);
  }
}
function applyLight(D, L, tint) {
  const d = D.d, n = d.length, tr = cR(tint) / 255, tg = cG(tint) / 255, tb = cB(tint) / 255;
  for (let i = 0; i < n; i++) {
    const c = d[i], l = L[i];
    let r = c & 255, g = (c >> 8) & 255, b = (c >> 16) & 255;
    // bright pixels (screens, neon, lamps) glow on their own: light them less
    const lum = (r * 3 + g * 6 + b) / 2550, self = lum > 0.62 ? (lum - 0.62) * 2.2 : 0;
    const k = l + (1 - l) * Math.min(1, self);
    // shadows lean towards the theme's accent colour, lights towards warm white
    const sh = l < 1 ? (1 - l) * 0.18 : 0;
    r = r * k + 255 * tr * sh * 0.35; g = g * k + 255 * tg * sh * 0.35; b = b * k + 255 * tb * sh * 0.35;
    if (l > 1) { const h = (l - 1) * 40; r += h; g += h * 0.9; b += h * 0.75; }
    d[i] = (0xff000000 | ((b > 255 ? 255 : b) << 16) | ((g > 255 ? 255 : g) << 8) | (r > 255 ? 255 : r)) >>> 0;
  }
}

function createScene() {
  const frame = new Buf(SCN.W, SCN.H), base = new Buf(SCN.W, SCN.H);
  const hi = new Buf(SCN.W * 2, SCN.H * 2), light = new Float32Array(SCN.W * 2 * SCN.H * 2);
  let baseKey = -1, lightKey = '';
  return {
    W: SCN.W,
    H: SCN.H,
    frame,
    hi,
    renderHD(st, t) {
      const q = [];
      HDQ = q;
      try { this.render(st, t); } finally { HDQ = null; }
      scale2x(frame, hi);
      for (const fn of q) { try { fn(hi); } finally { PS = 1; } }
      const lk = themeTier(st.floors) + ':' + Math.round((st.f || 0) * 48) + ':' + JSON.stringify(st.items || {}) + ':' + ['prog', 'art', 'des', 'mkt'].map((r) => (st.staff && st.staff[r] && st.staff[r].lvl > 0 ? 1 : 0)).join('');
      if (lk !== lightKey) { buildLight(light, hi.w, hi.h, st); lightKey = lk; }
      applyLight(hi, light, PAL[THEME.glow[0]] || PAL.cyan);
      return hi;
    },
    render(st, t) {
      const tier = themeTier(st.floors);
      const fk = Math.round(clampN(st.f || 0, 0, 1) * 120);
      const key = tier * 1000 + fk;
      if (key !== baseKey) {
        applyTheme(tier);
        drawBase(base, fk / 120);
        baseKey = key;
      }
      applyTheme(tier);
      frame.d.set(base.d);
      drawDynamic(frame, st, t || 0);
      return frame;
    },
  };
}

/* ============================================================
   Portraits, covers, tower
   ============================================================ */
function drawPortrait(B, kind, mood) {
  // 24x24 bust on neon backdrop
  const L = LOOK[kind] || LOOK.prog;
  const W = B.w, H = B.h;
  const bgA = { prog: '#0e2b4a', art: '#3a0f45', des: '#3d3208', mkt: '#0f2c52', you: '#101a5a', boss: '#2a2308', inv: '#0b3a2a', npc: '#26104a' }[kind] || '#101a5a';
  const bgB = { prog: '#1a7a8f', art: '#b02a96', des: '#a8761a', mkt: '#2a6fcf', you: '#2a4be0', boss: '#a8761a', inv: '#1f9a6a', npc: '#7a3ad0' }[kind] || '#2a4be0';
  for (let y = 0; y < H; y++) B.rect(0, y, W, 1, mixC(C(bgA), C(bgB), (y / H) * 0.9));
  for (let i = 0; i < 6; i++) B.px(Math.floor(hash01(i * 7 + kind.length) * W), Math.floor(hash01(i * 11 + 3) * 10), mixC(C(bgB), PAL.white, 0.5));
  const cx = Math.floor(W / 2);
  const skin = L.skin, hair = L.hair, shirt = L.shirt;
  // shoulders
  B.rect(cx - 8, H - 7, 16, 7, shirt);
  B.rect(cx - 7, H - 8, 14, 1, shadeC(shirt, 1.15));
  B.rect(cx - 2, H - 8, 4, 3, shadeC(skin, 0.85));
  if (kind === 'you') B.rect(cx - 1, H - 7, 2, 5, PAL.white);
  if (kind === 'boss') { B.rect(cx - 1, H - 8, 2, 7, PAL.red); B.rect(cx - 2, H - 8, 4, 1, PAL.white); }
  if (kind === 'inv') B.rect(cx - 2, H - 8, 4, 1, PAL.white);
  // head
  B.rect(cx - 5, 5, 10, 11, skin);
  B.rect(cx - 6, 8, 1, 4, skin);
  B.rect(cx + 5, 8, 1, 4, skin);
  // hair
  if (L.style === 'long') {
    B.rect(cx - 6, 3, 12, 6, hair);
    B.rect(cx - 6, 8, 2, 11, hair);
    B.rect(cx + 4, 8, 2, 11, hair);
    B.rect(cx - 4, 5, 3, 3, hair);
  } else if (L.style === 'spiky') {
    B.rect(cx - 6, 4, 12, 4, hair);
    B.rect(cx - 5, 2, 2, 2, hair); B.rect(cx - 1, 1, 2, 3, hair); B.rect(cx + 3, 2, 2, 2, hair);
    B.rect(cx - 6, 7, 2, 3, hair); B.rect(cx + 4, 7, 2, 3, hair);
  } else {
    B.rect(cx - 6, 3, 12, 5, hair);
    B.rect(cx - 6, 7, 2, 3, hair); B.rect(cx + 4, 7, 2, 3, hair);
  }
  if (kind === 'you') { B.rect(cx - 7, 8, 1, 6, PAL.cyan); B.rect(cx + 6, 8, 1, 6, PAL.cyan); B.rect(cx - 6, 2, 12, 1, PAL.cyan); }
  // face
  const m = mood === undefined ? 80 : mood;
  B.rect(cx - 3, 10, 2, 2, PAL.white); B.rect(cx + 1, 10, 2, 2, PAL.white);
  B.rect(cx - 2, 10, 1, 2, PAL.ink); B.rect(cx + 2, 10, 1, 2, PAL.ink);
  if (m >= 60) { B.rect(cx - 2, 14, 4, 1, C('#5a2a1a')); B.px(cx - 3, 13, C('#5a2a1a')); B.px(cx + 2, 13, C('#5a2a1a')); }
  else if (m >= 35) B.rect(cx - 2, 14, 4, 1, C('#5a2a1a'));
  else { B.rect(cx - 2, 13, 4, 1, C('#5a2a1a')); B.px(cx - 3, 14, C('#5a2a1a')); B.px(cx + 2, 14, C('#5a2a1a')); }
  B.px(cx - 4, 13, mixC(skin, C('#ff6fa0'), 0.35)); B.px(cx + 3, 13, mixC(skin, C('#ff6fa0'), 0.35));
  if (kind === 'prog') { // glasses
    B.rect(cx - 4, 9, 4, 1, PAL.cyan); B.rect(cx, 9, 4, 1, PAL.cyan); B.rect(cx - 4, 12, 4, 1, PAL.cyan); B.rect(cx, 12, 4, 1, PAL.cyan);
  }
}

const GENRE_ART = {
  rpg: { a: '#2a0f5a', b: '#ff3fd0' },
  plat: { a: '#0f3a7a', b: '#22e6ff' },
  shoot: { a: '#3a0f1a', b: '#ff4d6a' },
  sim: { a: '#0f4a3a', b: '#2ee66b' },
  horror: { a: '#14102a', b: '#8a5cff' },
  puzzle: { a: '#4a3a0f', b: '#ffd23f' },
};
function drawCover(B, genre, seed) {
  const g = GENRE_ART[genre] || GENRE_ART.rpg;
  const W = B.w, H = B.h;
  const A = C(g.a), Bc = C(g.b);
  for (let y = 0; y < H; y++) B.rect(0, y, W, 1, mixC(A, Bc, (y / H) * 0.7));
  const r = (n) => hash01(seed * 31 + n);
  // ground silhouette
  const gy = Math.floor(H * 0.72);
  B.rect(0, gy, W, H - gy, shadeC(A, 0.45));
  B.rect(0, gy, W, 1, Bc);
  if (genre === 'rpg') {
    B.rect(Math.floor(W * 0.3), Math.floor(H * 0.35), Math.floor(W * 0.4), gy - Math.floor(H * 0.35), shadeC(A, 0.4));
    B.rect(Math.floor(W * 0.3), Math.floor(H * 0.3), 3, 4, shadeC(A, 0.4));
    B.rect(Math.floor(W * 0.5) - 1, Math.floor(H * 0.3), 3, 4, shadeC(A, 0.4));
    B.rect(Math.floor(W * 0.7) - 3, Math.floor(H * 0.3), 3, 4, shadeC(A, 0.4));
    B.rect(Math.floor(W * 0.46), Math.floor(H * 0.55), 4, gy - Math.floor(H * 0.55), C('#ffd23f'));
  } else if (genre === 'plat') {
    B.rect(Math.floor(W * 0.1), Math.floor(H * 0.55), Math.floor(W * 0.3), 2, PAL.yel);
    B.rect(Math.floor(W * 0.55), Math.floor(H * 0.42), Math.floor(W * 0.3), 2, PAL.yel);
    B.rect(Math.floor(W * 0.25), gy - 6, 4, 6, PAL.white);
    B.rect(Math.floor(W * 0.25) + 1, gy - 8, 2, 2, PAL.white);
  } else if (genre === 'shoot') {
    const cx = Math.floor(W / 2), cy = Math.floor(H * 0.42);
    B.rect(cx - 7, cy, 5, 1, PAL.white); B.rect(cx + 3, cy, 5, 1, PAL.white); B.rect(cx, cy - 7, 1, 5, PAL.white); B.rect(cx, cy + 3, 1, 5, PAL.white);
    B.rect(cx - 1, cy - 1, 3, 3, PAL.red);
  } else if (genre === 'sim') {
    for (let i = 0; i < 4; i++) {
      const bw = Math.floor(W / 6), bh = 6 + Math.floor(r(i) * 14);
      B.rect(Math.floor(W * 0.08) + i * (bw + 1), gy - bh, bw, bh, shadeC(Bc, 0.5 + r(i + 9) * 0.5));
      B.px(Math.floor(W * 0.08) + i * (bw + 1) + 2, gy - bh + 2, PAL.yel);
    }
  } else if (genre === 'horror') {
    B.glow(Math.floor(W * 0.7), Math.floor(H * 0.25), 9, PAL.white, 0.6);
    B.rect(Math.floor(W * 0.7) - 3, Math.floor(H * 0.25) - 3, 7, 7, C('#e9e6ff'));
    B.rect(Math.floor(W * 0.28), gy - 12, 8, 12, C('#cfcaf5'));
    B.rect(Math.floor(W * 0.28) + 2, gy - 9, 1, 2, PAL.ink); B.rect(Math.floor(W * 0.28) + 5, gy - 9, 1, 2, PAL.ink);
  } else {
    const cols = [PAL.mag, PAL.cyan, PAL.yel, PAL.grn];
    for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) {
      const bs = Math.floor(W / 5);
      B.rect(Math.floor(W * 0.18) + i * (bs + 1), Math.floor(H * 0.2) + j * (bs + 1), bs, bs, cols[Math.floor(r(i * 3 + j) * 4)]);
    }
  }
  B.rect(0, 0, W, 1, mixC(Bc, PAL.white, 0.4));
}

function drawTower(B, floorsOwned, t, build, flv) {
  // build: {row, k, reno} — the floor being built or renovated (row 0 = f5 … 4 = f1, 5 = basement), k 0..1 progress
  // front-facing tower. floorsOwned: {b,f1..f5}; flv: renovation stars per floor. 80x124
  const W = B.w, H = B.h;
  const lv = flv || {};
  for (let y = 0; y < H; y++) B.rect(0, y, W, 1, mixC(C('#0a0d2c'), C('#241b62'), y / H));
  // twinkling stars
  for (let i = 0; i < 26; i++) {
    const tw = 0.4 + hash01(i) * 0.4 + 0.2 * Math.sin(t * (1 + hash01(i + 9) * 2) + i);
    B.px(Math.floor(hash01(i * 3 + 7) * W), Math.floor(hash01(i * 5 + 1) * 46), mixC(C('#0a0d2c'), C('#c9d2ff'), Math.max(0.15, Math.min(1, tw))));
  }
  // drifting clouds
  for (let c = 0; c < 2; c++) {
    const cx = Math.floor(((t * (1.6 + c) + c * 47) % (W + 30)) - 15), cy = 10 + c * 17;
    for (let i = 0; i < 12; i++) B.blend(cx + i, cy + (i > 2 && i < 9 ? -1 : 0), C('#8f97e8'), 0.16);
    for (let i = 3; i < 9; i++) B.blend(cx + i, cy - 2, C('#8f97e8'), 0.12);
  }
  const bw = 52, bx = Math.floor((W - bw) / 2);
  const rows = 5, fh = 17, topY = 18;
  const groundY = topY + rows * fh;
  const ids = ['f5', 'f4', 'f3', 'f2', 'f1'];
  const ownedList = ids.map((id) => floorsOwned[id]);
  const accents = [PAL.yel, PAL.cyan, PAL.mag, PAL.grn, PAL.blu];
  const allStars = ['b', 'f1', 'f2', 'f3', 'f4', 'f5'].reduce((n, id) => n + (lv[id] || 0), 0);
  // searchlights once the top floor is open
  if (floorsOwned.f5) {
    for (let k = 0; k < 2; k++) {
      const ang = Math.sin(t * 0.7 + k * 2.4) * 0.6 + (k ? 0.5 : -0.5);
      const ox = bx + Math.floor(bw / 2) + (k ? 6 : -6), oy = topY - 8;
      for (let d = 2; d < 40; d++) {
        const x = Math.round(ox + Math.sin(ang) * d), y = Math.round(oy - Math.cos(ang) * d);
        if (y < 0) break;
        B.blend(x, y, k ? PAL.cyan : PAL.mag, 0.22 * (1 - d / 40));
        B.blend(x + 1, y, k ? PAL.cyan : PAL.mag, 0.12 * (1 - d / 40));
      }
    }
  }
  for (let r = 0; r < rows; r++) {
    const y = topY + r * fh;
    const id = ids[r];
    const st = lv[id] || 0;
    const building = build && build.row === r && !build.reno;
    const renoing = build && build.row === r && build.reno;
    const own = building ? build.k >= 0.15 : !!ownedList[r];
    const shownSt = renoing ? Math.max(0, st - (build.k < 0.8 ? 1 : 0)) : st;
    B.rect(bx, y, bw, fh, own ? (shownSt >= 3 ? C('#24338a') : C('#1d2a73')) : C('#10143a'));
    B.rect(bx, y, bw, 1, own ? accents[r] : C('#262b66'));
    B.rect(bx, y, 1, fh, C('#0a0d2c')); B.rect(bx + bw - 1, y, 1, fh, C('#0a0d2c'));
    for (let w = 0; w < 5; w++) {
      const wx = bx + 4 + w * 9, wy = y + 4;
      let col = C('#161a4a');
      if (building && build.k < 0.15 + (w + 1) * 0.15) { B.rect(wx, wy, 7, 8, C('#161a4a')); continue; }
      if (own) {
        const h = hash01(r * 17 + w * 5);
        col = h < 0.34 ? PAL.cyan : h < 0.67 ? PAL.mag : PAL.yel;
        // renovated floors are busier: fewer dark windows
        if (hash01(r * 3 + w + Math.floor(t * 0.5)) < 0.12 - shownSt * 0.02) col = mixC(col, C('#161a4a'), 0.6);
      }
      B.rect(wx, wy, 7, 8, col);
      if (own) B.rect(wx, wy + 7, 7, 1, shadeC(col, 0.6));
    }
    if (own && shownSt >= 3) {
      // people walking past the windows
      for (let p = 0; p < (shownSt >= 5 ? 2 : 1); p++) {
        const span = 44, px = bx + 4 + Math.floor((t * (5 + p * 3) + r * 13 + p * 20) % span);
        const dir = Math.floor((t * (5 + p * 3) + r * 13 + p * 20) / span) % 2;
        const x = dir ? bx + 4 + span - (px - bx - 4) : px;
        B.rect(x, y + 7, 2, 5, C('#0b0e30'));
        B.rect(x, y + 6, 2, 1, C('#0b0e30'));
      }
    }
    if (own && shownSt >= 2) {
      // neon trim under the floor, breathing
      const a = 0.55 + 0.45 * Math.sin(t * 2 + r);
      for (let i = 1; i < bw - 1; i++) B.blend(bx + i, y + fh - 1, accents[r], a);
    }
    if (own && shownSt >= 4) {
      // blinking side sign
      const on = Math.floor(t * 2 + r) % 3 !== 0;
      B.rect(bx + bw, y + 3, 2, fh - 6, on ? accents[(r + 2) % 5] : C('#2a2f66'));
      if (on) B.glow(bx + bw + 1, y + Math.floor(fh / 2), 3, accents[(r + 2) % 5], 0.35);
    }
    if (own && shownSt >= 5) {
      B.rect(bx, y, bw, 1, PAL.yel);
      if (hash01(Math.floor(t * 3) + r * 7) > 0.8) B.glow(bx + 2 + Math.floor(hash01(Math.floor(t * 3) + r) * (bw - 4)), y + 2, 3, PAL.yel, 0.8);
    }
    // renovation stars on the floor's left edge
    for (let i = 0; i < shownSt; i++) B.px(bx - 2, y + 2 + i * 3, PAL.yel);
    if (building && build.k < 1) {
      // scaffolding + sparks while the floor is being built
      for (let i = 0; i < bw; i += 4) B.px(bx + i, y + ((i / 4) % 2 ? 2 : fh - 3), C('#ffd23f'));
      B.rect(bx - 3, y, 1, fh, C('#c8841a')); B.rect(bx + bw + 2, y, 1, fh, C('#c8841a'));
      const sx = bx + Math.floor(hash01(Math.floor(t * 12)) * bw), sy = y + Math.floor(hash01(Math.floor(t * 12) + 7) * fh);
      B.glow(sx, sy, 4, PAL.yel, 0.9);
    }
    if (renoing && build.k < 1) {
      // a sweep of light across the renovated floor, sparkles behind it
      const sx = bx + Math.floor(build.k * bw);
      for (let yy = y; yy < y + fh; yy++) { B.blend(sx, yy, C('#ffffff'), 0.85); B.blend(sx - 1, yy, PAL.yel, 0.6); B.blend(sx - 2, yy, PAL.yel, 0.3); }
      for (let i = 0; i < 3; i++) {
        const gx = bx + Math.floor(hash01(Math.floor(t * 14) + i * 5) * Math.max(1, sx - bx)), gy = y + Math.floor(hash01(Math.floor(t * 14) + i * 9 + 3) * fh);
        B.glow(gx, gy, 3, i % 2 ? PAL.yel : PAL.cyan, 0.85);
      }
    }
    if (!own && !building) {
      // lock
      const lx = bx + Math.floor(bw / 2) - 3, ly = y + 5;
      B.rect(lx, ly + 3, 7, 6, C('#5b63c7'));
      B.rect(lx + 1, ly, 5, 3, C('#5b63c7')); B.rect(lx + 2, ly + 1, 3, 2, C('#10143a'));
      B.rect(lx + 3, ly + 5, 1, 2, C('#10143a'));
    }
  }
  // elevator running between the open floors
  const ownedRows = [];
  for (let r = 0; r < rows; r++) if (ownedList[r]) ownedRows.push(r);
  if (ownedRows.length >= 2) {
    const ex = bx + bw - 4;
    const top = topY + ownedRows[0] * fh + 3, bot = topY + (rows - 1) * fh + fh - 6;
    B.rect(ex, top, 2, bot - top + 4, C('#0d1140'));
    const ph = (Math.sin(t * 0.6) + 1) / 2;
    const ey = Math.round(top + (bot - top) * ph);
    B.rect(ex, ey, 2, 4, PAL.yel);
    B.blend(ex - 1, ey + 1, PAL.yel, 0.35); B.blend(ex + 2, ey + 1, PAL.yel, 0.35);
  }
  // roof
  B.rect(bx - 2, topY - 4, bw + 4, 4, C('#2a2f80'));
  B.rect(bx - 2, topY - 4, bw + 4, 1, floorsOwned.f5 ? PAL.yel : C('#4a50b0'));
  B.rect(bx + 10, topY - 8, bw - 20, 4, floorsOwned.f5 ? PAL.mag : C('#3a2060'));
  B.rect(bx + Math.floor(bw / 2), topY - 18, 1, 10, C('#8a90e0'));
  if (Math.floor(t * 1.4) % 2) B.rect(bx + Math.floor(bw / 2) - 1, topY - 20, 3, 3, PAL.red);
  if (allStars >= 30) {
    // "Tower legend": a golden crown on the roof and fireworks over the city
    const cx = bx + Math.floor(bw / 2);
    B.rect(cx - 6, topY - 12, 13, 3, PAL.yel);
    B.px(cx - 6, topY - 14, PAL.yel); B.px(cx - 6, topY - 13, PAL.yel); B.px(cx, topY - 15, PAL.yel); B.px(cx, topY - 14, PAL.yel); B.px(cx, topY - 13, PAL.yel); B.px(cx + 6, topY - 14, PAL.yel); B.px(cx + 6, topY - 13, PAL.yel);
    B.px(cx - 3, topY - 11, PAL.red); B.px(cx + 3, topY - 11, PAL.cyan);
    const fw = Math.floor(t / 1.6), fk = (t / 1.6) % 1;
    const fx = 8 + Math.floor(hash01(fw) * (W - 16)), fy = 6 + Math.floor(hash01(fw + 3) * 20);
    const fc = [PAL.mag, PAL.cyan, PAL.yel, PAL.grn][fw % 4];
    if (fk < 0.7) for (let i = 0; i < 8; i++) { const ang = (i / 8) * Math.PI * 2, d = 1 + fk * 9; B.blend(Math.round(fx + Math.cos(ang) * d), Math.round(fy + Math.sin(ang) * d), fc, 1 - fk); }
  }
  // ground
  B.rect(0, groundY, W, 4, C('#1b1f55'));
  B.rect(0, groundY, W, 1, PAL.cyan);
  // a car driving by on the street
  const carX = Math.floor(((t * 9) % (W + 20)) - 10);
  B.rect(carX, groundY + 1, 6, 2, PAL.mag); B.px(carX + 6, groundY + 2, PAL.yel); B.px(carX - 1, groundY + 2, PAL.red);
  // basement
  const by = groundY + 4;
  B.rect(0, by, W, H - by, C('#120f2e'));
  const own = !!floorsOwned.b;
  const bst = lv.b || 0;
  const bReno = build && build.row === 5 && build.reno;
  const bShown = bReno ? Math.max(0, bst - (build.k < 0.8 ? 1 : 0)) : bst;
  B.rect(bx, by, bw, H - by, own ? C('#1a1650') : C('#0e0c26'));
  for (let i = 0; i < 4; i++) {
    const rx = bx + 4 + i * 12;
    B.rect(rx, by + 3, 9, H - by - 5, C('#262b66'));
    for (let l = 0; l < 4; l++) {
      const on = hash01(i * 5 + l + Math.floor(t * (2 + bShown) + i)) > 0.4 - bShown * 0.05;
      B.px(rx + 2, by + 6 + l * 4, own && on ? PAL.grn : C('#1a1d45'));
      B.px(rx + 5, by + 6 + l * 4, own && on ? PAL.cyan : C('#1a1d45'));
    }
  }
  for (let i = 0; i < bShown; i++) B.px(bx - 2, by + 3 + i * 3, PAL.yel);
  if (bReno && build.k < 1) {
    const sx = bx + Math.floor(build.k * bw);
    for (let yy = by; yy < H; yy++) { B.blend(sx, yy, C('#ffffff'), 0.85); B.blend(sx - 1, yy, PAL.yel, 0.5); }
  }
  // door
  B.rect(bx + Math.floor(bw / 2) - 3, groundY - 7, 6, 7, C('#0a0d2c'));
  if (floorsOwned.f1) B.rect(bx + Math.floor(bw / 2) - 3, groundY - 7, 6, 1, PAL.cyan);
}

if (typeof module !== 'undefined') {
  module.exports = { Buf, C, SCN, PAL, THEMES, themeTier, applyTheme, createScene, drawPortrait, drawCover, drawTower, mixC, shadeC, hash01, LOOK, GLYPH, skyAt, textWidth, eachGlyphPixel };
}
