/* Office Dreams — "zap" into the OFFX/SOL Raydium CPMM pool.
   One transaction, signed by the user's own wallet (no keys on the server):
   wrap SOL → swap part of it into OFFX through the same pool → deposit both
   sides as liquidity → unwrap whatever SOL is left. Works in the browser
   (lp.html) and in Node (tests), given SOLLIB.web3 / SOLLIB.spl. */
(function (root) {
  const CPMM = 'CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C';
  const POOL = '7315XeWTfSnSmVutW2jKNzxnDzCEqLqQ14UzKPmniuhK';
  const DISC = { deposit: [242, 35, 198, 137, 82, 225, 242, 182], swap: [143, 190, 90, 218, 196, 30, 51, 222] };
  const u64 = (b, o) => { let v = 0n; for (let i = 7; i >= 0; i--) v = (v << 8n) | BigInt(b[o + i]); return v; };
  const le = (v) => { const out = new Uint8Array(8); for (let i = 0; i < 8; i++) { out[i] = Number(v & 0xffn); v >>= 8n; } return out; };
  const data = (disc, nums) => { const a = new Uint8Array(8 + nums.length * 8); a.set(disc, 0); nums.forEach((n, i) => a.set(le(n), 8 + i * 8)); return a; };
  const b64 = (s) => (typeof atob === 'function' ? Uint8Array.from(atob(s), (c) => c.charCodeAt(0)) : new Uint8Array(Buffer.from(s, 'base64')));

  async function accounts(conn, keys) {
    const r = await conn.getMultipleAccountsInfo(keys, 'confirmed');
    return r.map((a) => (a ? new Uint8Array(a.data) : null));
  }
  // the pool as it is right now: mints, vaults, reserves (minus accrued fees), LP supply, fee rate
  async function readPool(W, conn) {
    const pool = new W.PublicKey(POOL);
    const [pd] = await accounts(conn, [pool]);
    if (!pd) throw new Error('pool not found');
    const pk = (o) => new W.PublicKey(pd.slice(o, o + 32));
    const P = { pool, ammConfig: pk(8), vault0: pk(72), vault1: pk(104), lpMint: pk(136), mint0: pk(168), mint1: pk(200), prog0: pk(232), prog1: pk(264), observation: pk(296) };
    P.dec0 = pd[331]; P.dec1 = pd[332];
    P.lpSupply = u64(pd, 333);
    let fee0 = u64(pd, 341) + u64(pd, 357), fee1 = u64(pd, 349) + u64(pd, 365);
    if (pd.length >= 413) { fee0 += u64(pd, 397); fee1 += u64(pd, 405); } // creator fees (newer pools)
    const [cfg, v0, v1] = await accounts(conn, [P.ammConfig, P.vault0, P.vault1]);
    P.tradeFee = u64(cfg, 12); // per 1,000,000
    P.r0 = u64(v0, 64) - fee0; P.r1 = u64(v1, 64) - fee1;
    P.authority = W.PublicKey.findProgramAddressSync([new TextEncoder().encode('vault_and_lp_mint_auth_seed')], new W.PublicKey(CPMM))[0];
    return P;
  }
  // how much of `total` to swap so that what is left matches the pool ratio after the swap
  function splitFor(P, total) {
    const f = Number(P.tradeFee) / 1e6, r = Number(P.r0), A = Number(total);
    const s = (Math.sqrt(r * r * (2 - f) * (2 - f) + 4 * (1 - f) * r * A) - r * (2 - f)) / (2 * (1 - f));
    return BigInt(Math.max(1, Math.floor(s)));
  }
  function quote(P, lamports, slipBps) {
    const total = BigInt(lamports);
    const sIn = splitFor(P, total);
    const fee = (sIn * P.tradeFee + 999999n) / 1000000n;
    const after = sIn - fee;
    const out = (after * P.r1) / (P.r0 + after);
    const minOut = (out * BigInt(10000 - slipBps)) / 10000n;
    const a0 = total - sIn;
    const r0n = P.r0 + sIn, r1w = P.r1 - minOut; // worst case for the deposit maths
    const lpA = (a0 * P.lpSupply) / r0n, lpB = (minOut * P.lpSupply) / r1w;
    const lp = ((lpA < lpB ? lpA : lpB) * 995n) / 1000n;
    const priceNow = Number(P.r0) / 10 ** P.dec0 / (Number(P.r1) / 10 ** P.dec1);
    const priceAfter = Number(P.r0 + sIn) / 10 ** P.dec0 / (Number(P.r1 - out) / 10 ** P.dec1);
    return { total, sIn, out, minOut, a0, lp, max0: a0, max1: minOut, priceNow, priceAfter, share: Number(lp) / Number(P.lpSupply + lp) };
  }
  async function buildZap(W, S, conn, owner, lamports, slipBps) {
    const P = await readPool(W, conn);
    const Q = quote(P, lamports, slipBps || 100);
    if (Q.lp <= 0n) throw new Error('amount too small');
    const me = new W.PublicKey(owner);
    const T = S.TOKEN_PROGRAM_ID, T22 = S.TOKEN_2022_PROGRAM_ID;
    const ata0 = S.getAssociatedTokenAddressSync(P.mint0, me, false, P.prog0);
    const ata1 = S.getAssociatedTokenAddressSync(P.mint1, me, false, P.prog1);
    const ataLp = S.getAssociatedTokenAddressSync(P.lpMint, me, false, T);
    if (!P.mint0.equals(S.NATIVE_MINT)) throw new Error('unexpected pool layout');
    const prog = new W.PublicKey(CPMM);
    const m = (pubkey, isWritable, isSigner) => ({ pubkey, isWritable: !!isWritable, isSigner: !!isSigner });
    const ix = [
      W.ComputeBudgetProgram.setComputeUnitLimit({ units: 300000 }),
      W.ComputeBudgetProgram.setComputeUnitPrice({ microLamports: 50000 }),
      S.createAssociatedTokenAccountIdempotentInstruction(me, ata0, me, P.mint0, P.prog0),
      W.SystemProgram.transfer({ fromPubkey: me, toPubkey: ata0, lamports: Q.total }),
      S.createSyncNativeInstruction(ata0, P.prog0),
      S.createAssociatedTokenAccountIdempotentInstruction(me, ata1, me, P.mint1, P.prog1),
      S.createAssociatedTokenAccountIdempotentInstruction(me, ataLp, me, P.lpMint, T),
      new W.TransactionInstruction({ programId: prog, data: data(DISC.swap, [Q.sIn, Q.minOut]), keys: [
        m(me, 1, 1), m(P.authority), m(P.ammConfig), m(P.pool, 1), m(ata0, 1), m(ata1, 1), m(P.vault0, 1), m(P.vault1, 1),
        m(P.prog0), m(P.prog1), m(P.mint0), m(P.mint1), m(P.observation, 1)] }),
      new W.TransactionInstruction({ programId: prog, data: data(DISC.deposit, [Q.lp, Q.max0, Q.max1]), keys: [
        m(me, 0, 1), m(P.authority), m(P.pool, 1), m(ataLp, 1), m(ata0, 1), m(ata1, 1), m(P.vault0, 1), m(P.vault1, 1),
        m(T), m(T22), m(P.mint0), m(P.mint1), m(P.lpMint, 1)] }),
      S.createCloseAccountInstruction(ata0, me, me, [], P.prog0), // unwrap the SOL that is left
    ];
    const bh = await conn.getLatestBlockhash('confirmed');
    const msg = new W.TransactionMessage({ payerKey: me, recentBlockhash: bh.blockhash, instructions: ix }).compileToV0Message();
    return { tx: new W.VersionedTransaction(msg), bh, pool: P, q: Q, ataLp };
  }
  async function lpBalance(W, S, conn, owner) {
    const P = await readPool(W, conn);
    const ata = S.getAssociatedTokenAddressSync(P.lpMint, new W.PublicKey(owner), false, S.TOKEN_PROGRAM_ID);
    const [a] = await accounts(conn, [ata]);
    const lp = a ? u64(a, 64) : 0n;
    return { lp, share: Number(lp) / Number(P.lpSupply), sol: Number((lp * P.r0) / P.lpSupply) / 1e9, offx: Number((lp * P.r1) / P.lpSupply) / 10 ** P.dec1, pool: P };
  }
  root.ODLP = { POOL, readPool, quote, buildZap, lpBalance, b64 };
})(typeof window !== 'undefined' ? window : globalThis);
