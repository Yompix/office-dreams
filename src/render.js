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
function P(gx, gy, z) {
  return [Math.round(SCN.OX + (gx - gy) * SCN.HW), Math.round(SCN.OY + (gx + gy) * SCN.HH - (z || 0))];
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
  const fh = SKYLINE.far[lx], nh = SKYLINE.near[lx];
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
    const strip = side === 'R' ? PAL.cyan : PAL.mag;
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
    const words = [['DREAM', PAL.cyan, 46], ['WORK', PAL.mag, 38], ['EARN', PAL.yel, 30], ['BUILD', PAL.grn, 22]];
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
        let col = mixC(C('#ff3fd0'), C('#3b1a8f'), t);
        if (!edge && v < v0 + 9 && Math.hypot(k - 16, v - (v0 + 7)) < 4.2) col = C('#ffd23f'); // sun
        if (!edge && v < v0 + 6 && v > v0 + 3 && (k + v) % 2 === 0 && Math.hypot(k - 16, v - (v0 + 7)) < 5) col = C('#ff8a3d');
        if (!edge && v >= v0 + 1 && v <= v0 + 7 && ((k * 7 + 3) % 5) === 0 && v < v0 + 4 + (k % 3)) col = C('#1a0f4d');
        wallPx(B, 'R', k, v, edge ? C('#e8e8ff') : col);
      }
  }
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

function drawFloor(B) {
  const N = SCN.N;
  // slab thickness
  const L = P(0, N, 0), Bt = P(N, N, 0), Rr = P(N, 0, 0);
  const T = 7;
  B.poly([L, Bt, [Bt[0], Bt[1] + T], [L[0], L[1] + T]], PAL.slab);
  B.poly([Bt, Rr, [Rr[0], Rr[1] + T], [Bt[0], Bt[1] + T]], shadeC(PAL.slab, 0.78));
  B.line(L[0], L[1] + 1, Bt[0], Bt[1] + 1, PAL.slabEdge);
  B.line(Bt[0], Bt[1] + 1, Rr[0], Rr[1] + 1, mixC(PAL.slabEdge, PAL.mag, 0.5));
  for (let i = 0; i < N; i++)
    for (let j = 0; j < N; j++) {
      const col = (i + j) & 1 ? PAL.floorA : PAL.floorB;
      isoQuad(B, i, j, i + 1, j + 1, 0, col);
    }
  for (let i = 0; i <= N; i++) {
    const a = P(i, 0, 0), b = P(i, N, 0), c = P(0, i, 0), d = P(N, i, 0);
    B.line(a[0], a[1], b[0], b[1], mixC(PAL.floorB, PAL.grid, 0.55));
    B.line(c[0], c[1], d[0], d[1], mixC(PAL.floorB, PAL.grid, 0.55));
  }
}

function drawRug(B) {
  isoQuad(B, 3.9, 3.9, 8.3, 7.5, 0, C('#2a1760'));
  isoQuad(B, 4.1, 4.1, 8.1, 7.3, 0, C('#3a1d7e'));
  const a = P(3.9, 3.9, 0), b = P(8.3, 3.9, 0), c = P(8.3, 7.5, 0), d = P(3.9, 7.5, 0);
  B.line(a[0], a[1], b[0], b[1], PAL.cyan);
  B.line(b[0], b[1], c[0], c[1], PAL.mag);
  B.line(c[0], c[1], d[0], d[1], PAL.mag);
  B.line(d[0], d[1], a[0], a[1], PAL.cyan);
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
  B.glow(SCN.OX, SCN.OY + 70, 52, PAL.mag, 0.10);
  B.glow(SCN.OX - 36, SCN.OY + 46, 40, PAL.cyan, 0.07);
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
function drawChairBack(B, s, accent) {
  const [cx, cy] = s.chair;
  if (s.side === 'R') isoBox(B, cx - 0.3, cy + 0.3, 9, 0.6, 0.1, 10, shadeC(accent, 1.2), accent, shadeC(accent, 0.6));
  else isoBox(B, cx + 0.3, cy - 0.3, 9, 0.1, 0.6, 10, shadeC(accent, 1.2), accent, shadeC(accent, 0.6));
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

/* ---------- dynamic layer ---------- */
function drawDynamic(B, st, t) {
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
        drawWorkerBack(B, a[0], a[1], look, typingGlobal, frame, t);
        drawChairBack(B, s, chairAccent);
        if (info.mood < 35) drawBadge(B, a[0] + 8, a[1] - 22, 'warn', t);
        else if (st.turbo) drawBadge(B, a[0] + 7, a[1] - 22, 'bolt', t);
        else if (!typingGlobal) drawBadge(B, a[0] + 6, a[1] - 22, 'zz', t);
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
    const blink = (t % 4.2) > 4.0;
    drawPlayerFront(B, a[0], a[1], t, typingGlobal || st.tapFlash > 0, frame, blink);
    // desk
    const H = 13;
    isoBox(B, 5.9, 4.4, 0, 0.1, 0.12, H - 3, PAL.woodL, PAL.woodL, PAL.woodR);
    isoBox(B, 5.9, 6.52, 0, 1.2, 0.12, H - 3, PAL.woodL, PAL.woodL, PAL.woodR);
    isoBox(B, 6.9, 4.4, 0, 0.1, 0.12, H - 3, PAL.woodL, PAL.woodL, PAL.woodR);
    isoBox(B, 5.9, 4.4, H - 3, 1.2, 2.24, 3, PAL.woodT, PAL.woodL, PAL.woodR, PAL.woodE);
    // laptop (lid facing +gx)
    isoBox(B, 6.05, 5.1, H, 0.45, 0.6, 1, C('#2b2f66'), C('#1a1d45'), C('#14163a'));
    isoBox(B, 6.45, 5.1, H, 0.07, 0.6, 9, C('#3a3f8a'), C('#2a2e72'), C('#1d2058'));
    const lp = P(6.52, 5.4, H + 5);
    B.px(lp[0], lp[1], t % 1.6 < 0.9 ? PAL.cyan : PAL.mag);
    B.px(lp[0] + 1, lp[1], t % 1.6 < 0.9 ? PAL.cyan : PAL.mag);
    B.glow(lp[0] - 9, lp[1] + 4, 14, PAL.cyan, 0.1);
    if (items.monitor2) drawMonitor2(B, t);
    // mug and cat
    isoBox(B, 6.1, 4.55, H, 0.2, 0.2, 3, C('#ffffff'), C('#d9d9ff'), C('#aaaadd'));
    const cp = P(6.5, 6.1, H);
    drawCat(B, cp[0], cp[1], t);
  });

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
  drawParticles(B, st, t);
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
function createScene() {
  const frame = new Buf(SCN.W, SCN.H), base = new Buf(SCN.W, SCN.H);
  let baseKey = -1;
  return {
    W: SCN.W,
    H: SCN.H,
    frame,
    render(st, t) {
      const key = Math.round(clampN(st.f || 0, 0, 1) * 120);
      if (key !== baseKey) {
        drawBase(base, key / 120);
        baseKey = key;
      }
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

function drawTower(B, floorsOwned, t) {
  // front-facing tower. floorsOwned: {b,f1..f5}. 80x120
  const W = B.w, H = B.h;
  for (let y = 0; y < H; y++) B.rect(0, y, W, 1, mixC(C('#0a0d2c'), C('#241b62'), y / H));
  for (let i = 0; i < 26; i++) B.px(Math.floor(hash01(i * 3 + 7) * W), Math.floor(hash01(i * 5 + 1) * 46), mixC(C('#0a0d2c'), C('#c9d2ff'), 0.4 + hash01(i) * 0.4));
  const bw = 52, bx = Math.floor((W - bw) / 2);
  const rows = 5, fh = 17, topY = 18;
  const groundY = topY + rows * fh;
  const ownedList = [floorsOwned.f5, floorsOwned.f4, floorsOwned.f3, floorsOwned.f2, floorsOwned.f1];
  const accents = [PAL.yel, PAL.cyan, PAL.mag, PAL.grn, PAL.blu];
  for (let r = 0; r < rows; r++) {
    const y = topY + r * fh;
    const own = !!ownedList[r];
    B.rect(bx, y, bw, fh, own ? C('#1d2a73') : C('#10143a'));
    B.rect(bx, y, bw, 1, own ? accents[r] : C('#262b66'));
    B.rect(bx, y, 1, fh, C('#0a0d2c')); B.rect(bx + bw - 1, y, 1, fh, C('#0a0d2c'));
    for (let w = 0; w < 5; w++) {
      const wx = bx + 4 + w * 9, wy = y + 4;
      let col = C('#161a4a');
      if (own) {
        const h = hash01(r * 17 + w * 5);
        col = h < 0.34 ? PAL.cyan : h < 0.67 ? PAL.mag : PAL.yel;
        if (hash01(r * 3 + w + Math.floor(t * 0.5)) < 0.12) col = mixC(col, C('#161a4a'), 0.6);
      }
      B.rect(wx, wy, 7, 8, col);
      if (own) B.rect(wx, wy + 7, 7, 1, shadeC(col, 0.6));
    }
    if (!own) {
      // lock
      const lx = bx + Math.floor(bw / 2) - 3, ly = y + 5;
      B.rect(lx, ly + 3, 7, 6, C('#5b63c7'));
      B.rect(lx + 1, ly, 5, 3, C('#5b63c7')); B.rect(lx + 2, ly + 1, 3, 2, C('#10143a'));
      B.rect(lx + 3, ly + 5, 1, 2, C('#10143a'));
    }
  }
  // roof
  B.rect(bx - 2, topY - 4, bw + 4, 4, C('#2a2f80'));
  B.rect(bx - 2, topY - 4, bw + 4, 1, floorsOwned.f5 ? PAL.yel : C('#4a50b0'));
  B.rect(bx + 10, topY - 8, bw - 20, 4, floorsOwned.f5 ? PAL.mag : C('#3a2060'));
  B.rect(bx + Math.floor(bw / 2), topY - 18, 1, 10, C('#8a90e0'));
  if (Math.floor(t * 1.4) % 2) B.rect(bx + Math.floor(bw / 2) - 1, topY - 20, 3, 3, PAL.red);
  // ground
  B.rect(0, groundY, W, 4, C('#1b1f55'));
  B.rect(0, groundY, W, 1, PAL.cyan);
  // basement
  const by = groundY + 4;
  B.rect(0, by, W, H - by, C('#120f2e'));
  const own = !!floorsOwned.b;
  B.rect(bx, by, bw, H - by, own ? C('#1a1650') : C('#0e0c26'));
  for (let i = 0; i < 4; i++) {
    const rx = bx + 4 + i * 12;
    B.rect(rx, by + 3, 9, H - by - 5, C('#262b66'));
    for (let l = 0; l < 4; l++) {
      const on = hash01(i * 5 + l + Math.floor(t * 2 + i)) > 0.4;
      B.px(rx + 2, by + 6 + l * 4, own && on ? PAL.grn : C('#1a1d45'));
      B.px(rx + 5, by + 6 + l * 4, own && on ? PAL.cyan : C('#1a1d45'));
    }
  }
  // door
  B.rect(bx + Math.floor(bw / 2) - 3, groundY - 7, 6, 7, C('#0a0d2c'));
}

if (typeof module !== 'undefined') {
  module.exports = { Buf, C, SCN, PAL, createScene, drawPortrait, drawCover, drawTower, mixC, shadeC, hash01, LOOK, GLYPH, skyAt, textWidth, eachGlyphPixel };
}
