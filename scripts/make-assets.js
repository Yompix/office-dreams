'use strict';
/* Generates the images Telegram / TON Connect need, straight from the game's own pixel renderer:
 *   public/icon-180.png      — app icon (TON Connect manifest, favicon)
 *   public/avatar-640.png    — bot profile photo (BotFather → /setuserpic)
 *   public/cover-640x360.png — Mini App cover (BotFather → /newapp asks for a 640×360 image)
 *   node scripts/make-assets.js
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const R = require('../src/render.js');

const OUT = path.join(__dirname, '..', 'public');
fs.mkdirSync(OUT, { recursive: true });

// --- minimal PNG encoder (RGBA) ---
const CRC = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
const crc32 = (buf) => { let c = -1; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, rgba) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
// nearest-neighbour upscale of a Buf into an RGBA buffer of (w*k)×(h*k), placed at (ox,oy) in a W×H canvas
function compose(W, H, layers, bg) {
  const out = Buffer.alloc(W * H * 4);
  const bgc = Buffer.from(bg);
  for (let i = 0; i < W * H; i++) bgc.copy(out, i * 4);
  for (const { buf, k, ox, oy } of layers) {
    const src = Buffer.from(buf.d.buffer);
    for (let y = 0; y < buf.h * k; y++) {
      const ty = oy + y; if (ty < 0 || ty >= H) continue;
      for (let x = 0; x < buf.w * k; x++) {
        const tx = ox + x; if (tx < 0 || tx >= W) continue;
        const si = ((Math.floor(y / k) * buf.w) + Math.floor(x / k)) * 4;
        if (src[si + 3] === 0) continue;
        src.copy(out, (ty * W + tx) * 4, si, si + 4);
      }
    }
  }
  return out;
}
function hero(t) {
  const sc = R.createScene();
  const fr = sc.render({
    f: 0.93, working: true, hype: true,
    staff: { prog: { lvl: 4, mood: 90 }, art: { lvl: 3, mood: 90 }, des: { lvl: 3, mood: 90 }, mkt: { lvl: 2, mood: 90 } },
    items: { coffee: 1, chairs: 1, monitor2: 1, lamp: 1, lounge: 1, rack: 1 },
  }, t || 1.2);
  const b = new R.Buf(R.SCN.W, R.SCN.H);
  b.d.set(fr.d);
  return b;
}
function title(text, col, glow) {
  const w = R.textWidth(text) + 4, h = 9;
  const b = new R.Buf(w, h);
  b.clear(0);
  R.eachGlyphPixel(text, (x, y) => { for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if (!b.get(x + 2 + dx, y + 2 + dy)) b.px(x + 2 + dx, y + 2 + dy, glow); });
  R.eachGlyphPixel(text, (x, y) => b.px(x + 2, y + 2, col));
  return b;
}
const MAG = R.C('#ff3fd0'), CY = R.C('#22e6ff'), WHITE = R.C('#ffffff'), DARK = R.C('#3a0f45'), DARKC = R.C('#0b2a4a');

// cover 640×360: hero scene ×2 (512×352) centered, title on top
{
  const W = 640, H = 360;
  const sc = hero();
  const t1 = title('OFFICE', WHITE, MAG), t2 = title('DREAMS', CY, DARKC);
  const k = 4;
  const img = compose(W, H, [
    { buf: sc, k: 2, ox: (W - 512) / 2, oy: 8 },
    { buf: t1, k, ox: Math.round((W - t1.w * k) / 2), oy: 236 },
    { buf: t2, k, ox: Math.round((W - t2.w * k) / 2), oy: 236 + 36 },
  ], [5, 7, 26, 255]);
  fs.writeFileSync(path.join(OUT, 'cover-640x360.png'), png(W, H, img));
}
// avatar 640×640: scene crop centered on the founder + title
{
  const W = 640, H = 640;
  const sc = hero();
  const t1 = title('OFFICE', WHITE, MAG), t2 = title('DREAMS', CY, DARKC);
  const img = compose(W, H, [
    { buf: sc, k: 4, ox: Math.round(W / 2 - 128 * 4), oy: -40 },
    { buf: t1, k: 5, ox: Math.round((W - t1.w * 5) / 2), oy: 470 },
    { buf: t2, k: 5, ox: Math.round((W - t2.w * 5) / 2), oy: 470 + 46 },
  ], [5, 7, 26, 255]);
  fs.writeFileSync(path.join(OUT, 'avatar-640.png'), png(W, H, img));
}
// icon 180×180: founder portrait ×7 on a neon tile
{
  const W = 180, H = 180;
  const p = new R.Buf(24, 24);
  R.drawPortrait(p, 'you', 90);
  const img = compose(W, H, [{ buf: p, k: 7, ox: 6, oy: 6 }], [11, 18, 80, 255]);
  // neon frame
  for (let i = 0; i < W; i++) for (const j of [0, 1, 2, W - 3, W - 2, W - 1]) {
    for (const [x, y] of [[i, j], [j, i]]) { const o = (y * W + x) * 4; img[o] = 34; img[o + 1] = 230; img[o + 2] = 255; img[o + 3] = 255; }
  }
  fs.writeFileSync(path.join(OUT, 'icon-180.png'), png(W, H, img));
}
// token logo 512×512: a pixel OFFICE coin (64×64 ×8) for the token metadata (wallets, Jupiter, DexScreener)
{
  const N = 64, b = new R.Buf(N, N);
  b.clear(0);
  const c = 31.5, gold = R.C('#ffd23f'), gold2 = R.C('#e0a21a'), gold3 = R.C('#9a6408'), hi = R.C('#fff3b0'), ink = R.C('#3a2204');
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const d = Math.hypot(x - c, y - c);
    if (d > 31.5) continue;
    if (d > 29.5) { b.px(x, y, ink); continue; }
    if (d > 26.5) { b.px(x, y, (x + y < 62) ? gold : gold3); continue; } // rim: lit top-left, shaded bottom-right
    if (d > 25.5) { b.px(x, y, gold3); continue; }
    const t = (x - y) / 52 + 0.5; // face: soft diagonal light
    b.px(x, y, t > 0.62 ? gold2 : gold);
  }
  for (let i = 0; i < 9; i++) { b.px(14 + i, 12 - Math.floor(i / 3), hi); b.px(14 + i, 13 - Math.floor(i / 3), hi); } // shine
  // "OD" in the middle: hand-drawn 9×13 letters ×2 with a dark outline
  const O = ['..#####..', '.##...##.', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '.##...##.', '..#####..'];
  const D = ['######...', '##...##..', '##....##.', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##.....##', '##....##.', '##...##..', '######...'];
  const k = 2, ox = Math.round((N - (9 * k * 2 + 4)) / 2), oy = Math.round((N - 13 * k) / 2);
  const pts = [];
  [[O, ox], [D, ox + 9 * k + 4]].forEach(([g, gx]) => g.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') for (let dy = 0; dy < k; dy++) for (let dx = 0; dx < k; dx++) pts.push([gx + x * k + dx, oy + y * k + dy]); })));
  for (const [x, y] of pts) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [2, 2]]) b.px(x + dx, y + dy, ink);
  for (const [x, y] of pts) b.px(x, y, R.C('#fff8d6'));
  const img = compose(512, 512, [{ buf: b, k: 8, ox: 0, oy: 0 }], [0, 0, 0, 0]);
  fs.writeFileSync(path.join(OUT, 'token-512.png'), png(512, 512, img));
}
for (const f of fs.readdirSync(OUT)) console.log('public/' + f, (fs.statSync(path.join(OUT, f)).size / 1024).toFixed(1) + ' KB');
