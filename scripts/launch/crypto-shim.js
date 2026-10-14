// browser stand-in for node's crypto.createHash('sha256'), used by @solana/spl-type-length-value
import { sha256 } from '@noble/hashes/sha256';
import { Buffer } from 'buffer';
export function createHash(alg) {
  if (alg !== 'sha256') throw new Error('only sha256');
  const parts = [];
  return {
    update(d) { parts.push(typeof d === 'string' ? new TextEncoder().encode(d) : new Uint8Array(d)); return this; },
    digest() { const n = parts.reduce((a, c) => a + c.length, 0), b = new Uint8Array(n); let o = 0; for (const c of parts) { b.set(c, o); o += c.length; } return Buffer.from(sha256(b)); },
  };
}
export default { createHash };
