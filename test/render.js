const fs = require('fs');
const zlib = require('zlib');
const path = require('path');
const R = require('../src/render.js');

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, u32, scale) {
  const W = w * scale, H = h * scale;
  const raw = Buffer.alloc((W * 4 + 1) * H);
  const px = new Uint8Array(u32.buffer, u32.byteOffset, u32.byteLength);
  for (let y = 0; y < H; y++) {
    raw[y * (W * 4 + 1)] = 0;
    for (let x = 0; x < W; x++) {
      const si = ((Math.floor(y / scale)) * w + Math.floor(x / scale)) * 4;
      const di = y * (W * 4 + 1) + 1 + x * 4;
      raw[di] = px[si]; raw[di + 1] = px[si + 1]; raw[di + 2] = px[si + 2]; raw[di + 3] = 255;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const out = path.join(__dirname, 'out');
fs.mkdirSync(out, { recursive: true });
const scene = R.createScene();
const full = {
  f: 0.9, working: true, turbo: false,
  staff: { prog: { lvl: 3, mood: 85 }, art: { lvl: 2, mood: 80 }, des: { lvl: 1, mood: 30 }, mkt: { lvl: 0, mood: 70 } },
  items: { coffee: 1, chairs: 1, monitor2: 1, lamp: 1, lounge: 1, rack: 1 },
  canHire: { mkt: true }, tapFlash: 0,
};
const empty = { f: 0.1, working: false, staff: { prog: { lvl: 0 }, art: { lvl: 0 }, des: { lvl: 0 }, mkt: { lvl: 0 } }, items: {}, canHire: { prog: true } };
const t0 = Date.now();
let fr = scene.render(full, 1.3);
console.log('render ms', Date.now() - t0);
fs.writeFileSync(path.join(out, 'scene_full.png'), png(fr.w, fr.h, fr.d, 4));
fr = scene.render(empty, 0.7);
fs.writeFileSync(path.join(out, 'scene_empty.png'), png(fr.w, fr.h, fr.d, 4));
fr = scene.render({ ...full, f: 0.45 }, 2.1);
fs.writeFileSync(path.join(out, 'scene_day.png'), png(fr.w, fr.h, fr.d, 4));
// timing of 100 frames
const t1 = Date.now();
for (let i = 0; i < 100; i++) scene.render({ ...full, f: 0.9 }, i * 0.05);
console.log('100 frames ms', Date.now() - t1);

// portraits
const kinds = ['you', 'prog', 'art', 'des', 'mkt'];
const pb = new R.Buf(24 * kinds.length + 4 * kinds.length, 24);
kinds.forEach((k, i) => {
  const b = new R.Buf(24, 24);
  R.drawPortrait(b, k, 85);
  for (let y = 0; y < 24; y++) for (let x = 0; x < 24; x++) pb.px(i * 28 + x, y, b.d[y * 24 + x]);
});
fs.writeFileSync(path.join(out, 'portraits.png'), png(pb.w, pb.h, pb.d, 6));
// covers
const gs = ['rpg', 'plat', 'shoot', 'sim', 'horror', 'puzzle'];
const cb = new R.Buf(36 * gs.length, 36);
gs.forEach((g, i) => {
  const b = new R.Buf(32, 32);
  R.drawCover(b, g, 5 + i);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) cb.px(i * 36 + x, y, b.d[y * 32 + x]);
});
fs.writeFileSync(path.join(out, 'covers.png'), png(cb.w, cb.h, cb.d, 5));
// tower
const tb = new R.Buf(80, 124);
R.drawTower(tb, { b: 1, f1: 1, f2: 1, f3: 0, f4: 0, f5: 0 }, 1);
fs.writeFileSync(path.join(out, 'tower.png'), png(tb.w, tb.h, tb.d, 4));
console.log('ok');
