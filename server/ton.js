'use strict';
/* TON helpers: address parsing, TON Connect `ton_proof` verification, minimal BOC (cell) reader.
 *
 * ton_proof (https://docs.ton.org/develop/dapps/ton-connect/sign):
 *   message   = "ton-proof-item-v2/" ++ int32BE(workchain) ++ addrHash(32) ++ uint32LE(domainLen) ++ domain ++ uint64LE(timestamp) ++ payload
 *   signature = Ed25519( sha256( 0xffff ++ "ton-connect" ++ sha256(message) ) )
 * Full verification additionally checks that the wallet's StateInit hashes to the address and contains the public key.
 * Until the token launches a failed proof never blocks the player: the address is simply stored as "unverified".
 */
const crypto = require('crypto');

const sha256 = (b) => crypto.createHash('sha256').update(b).digest();

/* ---- addresses ---- */
function crc16(buf) {
  let crc = 0;
  for (const b of buf) {
    crc ^= b << 8;
    for (let i = 0; i < 8; i++) crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
  }
  return crc;
}
function parseAddress(str) {
  str = String(str || '').trim();
  let m = /^(-?\d+):([0-9a-fA-F]{64})$/.exec(str);
  if (m) {
    const wc = Number(m[1]);
    if (wc !== 0 && wc !== -1) return null;
    return { wc, hash: Buffer.from(m[2], 'hex') };
  }
  if (/^[A-Za-z0-9_\-+/=]{48}$/.test(str)) {
    const b = Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    if (b.length !== 36) return null;
    if (crc16(b.subarray(0, 34)) !== b.readUInt16BE(34)) return null;
    const tag = b[0] & 0x7f;
    if (tag !== 0x11 && tag !== 0x51) return null;
    const wc = b.readInt8(1);
    if (wc !== 0 && wc !== -1) return null;
    return { wc, hash: Buffer.from(b.subarray(2, 34)), friendly: true, testnet: !!(b[0] & 0x80) };
  }
  return null;
}
const rawAddress = (a) => `${a.wc}:${a.hash.toString('hex')}`;
function toFriendly(a, { bounceable = false, testnet = false } = {}) {
  const b = Buffer.alloc(36);
  b[0] = (bounceable ? 0x11 : 0x51) | (testnet ? 0x80 : 0);
  b.writeInt8(a.wc, 1);
  a.hash.copy(b, 2);
  b.writeUInt16BE(crc16(b.subarray(0, 34)), 34);
  return b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_');
}

/* ---- BOC ---- */
function readUInt(buf, off, n) {
  let v = 0;
  for (let i = 0; i < n; i++) v = v * 256 + buf[off + i];
  return v;
}
function bocParse(buf) {
  if (buf.length < 10 || buf.readUInt32BE(0) !== 0xb5ee9c72) throw new Error('boc magic');
  const flags = buf[4];
  const hasIdx = !!(flags & 0x80);
  const size = flags & 7;
  const offBytes = buf[5];
  let p = 6;
  const cellsN = readUInt(buf, p, size); p += size;
  const rootsN = readUInt(buf, p, size); p += size;
  p += size; // absent
  const totSize = readUInt(buf, p, offBytes); p += offBytes;
  const roots = [];
  for (let i = 0; i < rootsN; i++) { roots.push(readUInt(buf, p, size)); p += size; }
  if (hasIdx) p += cellsN * offBytes;
  const end = p + totSize;
  if (end > buf.length || cellsN > 4096) throw new Error('boc size');
  const cells = [];
  for (let i = 0; i < cellsN; i++) {
    const d1 = buf[p++], d2 = buf[p++];
    const refsN = d1 & 7;
    const exotic = !!(d1 & 8);
    const dataLen = (d2 >> 1) + (d2 & 1);
    const data = Buffer.from(buf.subarray(p, p + dataLen)); p += dataLen;
    const refs = [];
    for (let r = 0; r < refsN; r++) { refs.push(readUInt(buf, p, size)); p += size; }
    cells.push({ d1, d2, data, refs, exotic });
  }
  // hashes bottom-up (children always have larger indexes than parents)
  for (let i = cells.length - 1; i >= 0; i--) {
    const c = cells[i];
    let depth = 0;
    const parts = [Buffer.from([c.d1, c.d2]), c.data];
    for (const r of c.refs) { if (r <= i) throw new Error('boc order'); depth = Math.max(depth, cells[r].depth + 1); }
    c.depth = depth;
    for (const r of c.refs) parts.push(Buffer.from([cells[r].depth >> 8, cells[r].depth & 255]));
    for (const r of c.refs) parts.push(cells[r].hash);
    c.hash = sha256(Buffer.concat(parts));
  }
  return { cells, roots };
}
// number of valid data bits in a cell (strip the completion tag)
function cellBits(c) {
  const full = !(c.d2 & 1);
  if (full) return c.data.length * 8;
  const last = c.data[c.data.length - 1];
  let tz = 0;
  while (tz < 8 && !((last >> tz) & 1)) tz++;
  return c.data.length * 8 - tz - 1;
}
function containsBits(c, key) {
  const bits = cellBits(c);
  const keyBits = key.length * 8;
  const bitAt = (buf, i) => (buf[i >> 3] >> (7 - (i & 7))) & 1;
  outer: for (let o = 0; o + keyBits <= bits; o++) {
    for (let i = 0; i < keyBits; i++) if (bitAt(c.data, o + i) !== bitAt(key, i)) continue outer;
    return true;
  }
  return false;
}
// test helper: serialise a tree {bits: Buffer(bytes, full), refs: [...]} into a BOC
function bocBuild(root) {
  const list = [];
  const walk = (n) => { n.idx = list.length; list.push(n); for (const r of n.refs || []) walk(r); };
  walk(root);
  const size = 1;
  const body = [];
  for (const n of list) {
    const refs = n.refs || [];
    body.push(Buffer.from([refs.length, n.data.length * 2]), n.data, Buffer.from(refs.map((r) => r.idx)));
  }
  const tot = Buffer.concat(body);
  return Buffer.concat([Buffer.from([0xb5, 0xee, 0x9c, 0x72, size, 2, list.length, 1, 0, tot.length >> 8, tot.length & 255, 0]), tot]);
}

/* ---- ton_proof payloads (server-issued nonces) ---- */
function makePayload(secret, now = Date.now()) {
  const body = crypto.randomBytes(8).toString('hex') + Math.floor(now / 1000).toString(16).padStart(8, '0');
  return body + crypto.createHmac('sha256', secret).update(body).digest('hex').slice(0, 16);
}
function checkPayload(secret, payload, maxAgeSec = 900, now = Date.now()) {
  if (!/^[0-9a-f]{40}$/.test(String(payload))) return false;
  const body = payload.slice(0, 24);
  const mac = crypto.createHmac('sha256', secret).update(body).digest('hex').slice(0, 16);
  if (mac !== payload.slice(24)) return false;
  const ts = parseInt(body.slice(16), 16);
  return Math.abs(now / 1000 - ts) <= maxAgeSec;
}

/* ---- verification ---- */
const ED25519_SPKI = Buffer.from('302a300506032b6570032100', 'hex');
function verifyTonProof(input, opts) {
  const { account, proof } = input || {};
  if (!account || !proof) return { ok: false, err: 'missing' };
  const now = opts.now || Date.now();
  const addr = parseAddress(account.address);
  if (!addr) return { ok: false, err: 'address' };
  if (!checkPayload(opts.secret, proof.payload, 900, now)) return { ok: false, err: 'payload', addr };
  const ts = Number(proof.timestamp);
  if (!ts || Math.abs(now / 1000 - ts) > 900) return { ok: false, err: 'timestamp', addr };
  const dom = proof.domain || {};
  if (opts.domain && dom.value !== opts.domain) return { ok: false, err: 'domain', addr };
  if (Buffer.byteLength(String(dom.value)) !== Number(dom.lengthBytes)) return { ok: false, err: 'domain_len', addr };
  let pub;
  try { pub = Buffer.from(String(account.publicKey), 'hex'); } catch { pub = null; }
  if (!pub || pub.length !== 32) return { ok: false, err: 'pubkey', addr };
  let sig;
  try { sig = Buffer.from(String(proof.signature), 'base64'); } catch { sig = null; }
  if (!sig || sig.length !== 64) return { ok: false, err: 'signature', addr };

  const wc = Buffer.alloc(4); wc.writeInt32BE(addr.wc);
  const tsb = Buffer.alloc(8); tsb.writeBigUInt64LE(BigInt(ts));
  const dl = Buffer.alloc(4); dl.writeUInt32LE(Number(dom.lengthBytes));
  const msg = Buffer.concat([Buffer.from('ton-proof-item-v2/'), wc, addr.hash, dl, Buffer.from(String(dom.value)), tsb, Buffer.from(String(proof.payload))]);
  const full = Buffer.concat([Buffer.from([0xff, 0xff]), Buffer.from('ton-connect'), sha256(msg)]);
  let sigOk = false;
  try {
    const key = crypto.createPublicKey({ key: Buffer.concat([ED25519_SPKI, pub]), format: 'der', type: 'spki' });
    sigOk = crypto.verify(null, sha256(full), key, sig);
  } catch { sigOk = false; }
  if (!sigOk) return { ok: false, err: 'bad_signature', addr };

  // does the key really belong to this address? (StateInit hash == address, public key inside the data cell)
  let level = 'sig_only';
  if (account.walletStateInit) {
    try {
      const boc = bocParse(Buffer.from(String(account.walletStateInit), 'base64'));
      const root = boc.cells[boc.roots[0]];
      if (root.hash.equals(addr.hash)) {
        const dataCell = root.refs.length >= 2 ? boc.cells[root.refs[1]] : null;
        if (dataCell && containsBits(dataCell, pub)) level = 'full';
      }
    } catch { /* keep sig_only */ }
  }
  return { ok: true, level, addr };
}

module.exports = { parseAddress, rawAddress, toFriendly, crc16, bocParse, bocBuild, cellBits, containsBits, makePayload, checkPayload, verifyTonProof, sha256 };
