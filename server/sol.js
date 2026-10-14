'use strict';
/* Solana helpers: base58 addresses and JSON-RPC calls (ctx.solRpc replaces the network in tests). */
const crypto = require('crypto');

const B58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58decode(s) {
  if (typeof s !== 'string' || !s || s.length > 128) return null;
  let n = 0n;
  for (const ch of s) { const i = B58.indexOf(ch); if (i < 0) return null; n = n * 58n + BigInt(i); }
  const out = [];
  while (n > 0n) { out.unshift(Number(n & 255n)); n >>= 8n; }
  for (const ch of s) { if (ch !== '1') break; out.unshift(0); }
  return out;
}
function b58encode(bytes) {
  let n = 0n;
  for (const b of bytes) n = n * 256n + BigInt(b);
  let s = '';
  while (n > 0n) { s = B58[Number(n % 58n)] + s; n /= 58n; }
  for (const b of bytes) { if (b !== 0) break; s = '1' + s; }
  return s;
}
/* a Solana address (wallet, mint) is 32 bytes in base58 */
const isAddr = (s) => { s = String(s || '').trim(); const b = s.length >= 32 && s.length <= 44 ? b58decode(s) : null; return !!b && b.length === 32; };
/* a random 32-byte key: the Solana Pay "reference" that marks one order's transaction */
const randomKey = () => b58encode(crypto.randomBytes(32));

async function rpc(ctx, method, params) {
  if (ctx.solRpc) return ctx.solRpc(method, params);
  const url = (ctx.cfg.token && ctx.cfg.token.rpc) || 'https://api.mainnet-beta.solana.com';
  const r = await fetch(url, {
    method: 'POST', headers: { 'content-type': 'application/json' }, signal: AbortSignal.timeout(10000),
    body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }),
  });
  if (!r.ok) throw new Error('rpc http ' + r.status);
  const j = await r.json();
  if (j.error) throw new Error('rpc ' + (j.error.message || j.error.code));
  return j.result;
}

module.exports = { b58decode, b58encode, isAddr, randomKey, rpc };
