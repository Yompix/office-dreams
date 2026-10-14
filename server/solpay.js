'use strict';
/* Paying for shop items and donations with SOL (Solana Pay).
 *
 * Flow: the client asks for an order → gets {to, sol, reference, url "solana:…"} → the player pays from any
 * Solana wallet (the link / QR adds the reference key; a manual transfer is matched by its unique amount) →
 * the server reads the shop wallet's recent transactions (JSON-RPC) and grants the item once (idempotent by
 * signature). Nothing is trusted from the client: only the blockchain decides.
 */
const crypto = require('crypto');
const Eco = require('./economy.js');
const U = require('./users.js');
const Sol = require('./sol.js');
const Bot = require('./bot.js');

const ORDER_TTL = 48 * 3600 * 1000; // late transfers are still credited for 2 days
const LAMPORTS = 1e9;
const SOL_MINT = 'So11111111111111111111111111111111111111112';

function enabled(cfg) { return !!(cfg.solPay && cfg.solPay.wallet && Sol.isAddr(cfg.solPay.wallet)); }

/* SOL price in USD: refreshed from Jupiter by a timer, the config value until then */
let solUsdLive = 0, solUsdAt = 0;
function solUsd(cfg) { return solUsdLive || cfg.solPay.usd; }
async function refreshPrice(ctx, now = Date.now()) {
  if (now - solUsdAt < 10 * 60000) return solUsdLive;
  solUsdAt = now;
  try {
    const r = await fetch('https://lite-api.jup.ag/price/v3?ids=' + SOL_MINT, { signal: AbortSignal.timeout(8000) });
    const j = await r.json();
    const v = Number(j && j[SOL_MINT] && j[SOL_MINT].usdPrice);
    if (v > 1) solUsdLive = v;
  } catch (_) { /* keep the last known price */ }
  return solUsdLive;
}

// price in SOL: stars × (user price of a star) ÷ SOL price, rounded up to 0.0001 SOL
function solPrice(cfg, it) {
  const sol = (it.stars * cfg.solPay.starUsd) / solUsd(cfg);
  return Math.max(0.001, Math.ceil(sol * 10000) / 10000);
}
// smallest SOL donation = the Stars minimum at the shop's Star price
function donateMinSol(cfg) {
  return Math.max(0.001, Math.ceil(((cfg.donate.minStars * cfg.solPay.starUsd) / solUsd(cfg)) * 1000) / 1000);
}
const fmtSol = (lamports) => (Number(lamports) / LAMPORTS).toFixed(9).replace(/0+$/, '').replace(/\.$/, '');

/* every pending order gets a unique amount (+1…999 µSOL), so a plain transfer without the reference still matches */
function uniqueLamports(ctx, base, now) {
  const taken = new Set(ctx.db.all("SELECT lamports FROM sol_orders WHERE status='pending' AND ts>?", now - ORDER_TTL).map((r) => r.lamports));
  for (let i = 0; i < 50; i++) {
    const l = String(base + (1 + crypto.randomInt(999)) * 1000);
    if (!taken.has(l)) return l;
  }
  return null;
}
function newOrder(ctx, uid, sku, baseSol, now) {
  const recent = ctx.db.get('SELECT COUNT(*) AS n FROM sol_orders WHERE uid=? AND ts>?', uid, now - 3600 * 1000).n;
  if (recent >= 10) return { ok: false, err: 'rate' };
  const lamports = uniqueLamports(ctx, Math.round(baseSol * LAMPORTS), now);
  if (!lamports) return { ok: false, err: 'busy' };
  const id = crypto.randomBytes(5).toString('hex');
  const ref = Sol.randomKey();
  ctx.db.run("INSERT INTO sol_orders(id,uid,sku,lamports,ref,ts,status,tx) VALUES(?,?,?,?,?,?,'pending',NULL)", id, uid, sku, lamports, ref, now);
  const to = ctx.cfg.solPay.wallet, sol = fmtSol(lamports);
  const url = `solana:${to}?amount=${sol}&reference=${ref}&label=${encodeURIComponent('Office Dreams')}&message=${encodeURIComponent('Office Dreams · ' + sku)}&memo=OD-${id}`;
  return { ok: true, order: { id, sku, sol, lamports, to, ref, url, until: now + 30 * 60000 } };
}
function createOrder(ctx, uid, sku, now) {
  if (!enabled(ctx.cfg)) return { ok: false, err: 'not_configured' };
  const it = Eco.shopItem(sku);
  if (!it || !Eco.canBuy(ctx, uid, sku)) return { ok: false, err: 'unavailable' };
  return newOrder(ctx, uid, sku, solPrice(ctx.cfg, it), now);
}
function createDonation(ctx, uid, sol, now) {
  if (!enabled(ctx.cfg)) return { ok: false, err: 'not_configured' };
  const min = donateMinSol(ctx.cfg);
  if (!Number.isFinite(sol) || sol < min || sol > 10000) return { ok: false, err: 'amount', min };
  return newOrder(ctx, uid, 'donate', Math.round(sol * 1000) / 1000, now);
}

/* ---- reading the shop wallet: incoming SOL per transaction, parsed once and cached ---- */
async function incoming(ctx, now) {
  const c = ctx.cache.solTx || (ctx.cache.solTx = { at: 0, list: [], seen: new Map() });
  if (now - c.at < 5000) return c.list;
  const wallet = ctx.cfg.solPay.wallet;
  const sigs = await Sol.rpc(ctx, 'getSignaturesForAddress', [wallet, { limit: 50, commitment: 'confirmed' }]);
  for (const s of sigs || []) {
    if (s.err || c.seen.has(s.signature)) continue;
    const tx = await Sol.rpc(ctx, 'getTransaction', [s.signature, { encoding: 'json', commitment: 'confirmed', maxSupportedTransactionVersion: 0 }]);
    if (!tx || !tx.meta) continue; // not final yet: try again next time
    const msg = tx.transaction && tx.transaction.message;
    const keys = ((msg && msg.accountKeys) || []).map((k) => (typeof k === 'string' ? k : k.pubkey))
      .concat((tx.meta.loadedAddresses && tx.meta.loadedAddresses.writable) || [], (tx.meta.loadedAddresses && tx.meta.loadedAddresses.readonly) || []);
    const i = keys.indexOf(wallet);
    const got = i >= 0 && !tx.meta.err ? (tx.meta.postBalances[i] || 0) - (tx.meta.preBalances[i] || 0) : 0;
    c.seen.set(s.signature, got > 0 ? { sig: s.signature, lamports: got, keys: new Set(keys), from: keys[0], at: (tx.blockTime || s.blockTime || 0) * 1000 } : null);
  }
  if (c.seen.size > 2000) c.seen = new Map([...c.seen].slice(-1000));
  c.list = [...c.seen.values()].filter(Boolean);
  c.at = now;
  return c.list;
}

function settle(ctx, order, list, now) {
  const need = Number(order.lamports);
  const tx = list.find((x) => x.at >= order.ts - 120000 && ((x.keys.has(order.ref) && x.lamports >= need) || x.lamports === need)
    && !ctx.db.get('SELECT 1 AS y FROM sol_orders WHERE tx=?', x.sig));
  if (!tx) return null;
  const sol = tx.lamports / LAMPORTS, usd = sol * solUsd(ctx.cfg);
  const meta = JSON.stringify({ sol, usd, from: tx.from, order: order.id });
  let r;
  if (order.sku === 'donate') {
    // a donation counts what actually arrived (people may send more than they typed)
    r = Eco.fulfilDonation(ctx, order.uid, 'sol:' + tx.sig, Math.round(usd / ctx.cfg.solPay.starUsd), now, { usd, meta });
  } else {
    r = Eco.fulfil(ctx, order.uid, order.sku, 'sol:' + tx.sig, Eco.shopItem(order.sku).stars, now, { usd, meta });
  }
  ctx.db.run("UPDATE sol_orders SET status='paid', tx=? WHERE id=?", tx.sig, order.id);
  return r;
}

async function checkOrder(ctx, uid, id, now) {
  const o = ctx.db.get('SELECT * FROM sol_orders WHERE id=? AND uid=?', String(id), uid);
  if (!o) return { ok: false, err: 'unavailable' };
  if (o.status === 'paid') return { ok: true, paid: true };
  if (now - o.ts > ORDER_TTL) return { ok: true, paid: false, expired: true };
  let list;
  try { list = await incoming(ctx, now); } catch (e) { return { ok: true, paid: false, retry: true }; }
  const r = settle(ctx, o, list, now);
  return { ok: true, paid: !!(r && r.ok) };
}

// background: credits transfers even if the player closed the game before confirmation
async function sweep(ctx, now = ctx.now()) {
  if (!enabled(ctx.cfg)) return { paid: 0 };
  const pend = ctx.db.all("SELECT * FROM sol_orders WHERE status='pending' AND ts>? ORDER BY ts", now - ORDER_TTL);
  if (!pend.length) return { paid: 0 };
  const list = await incoming(ctx, now);
  let paid = 0;
  for (const o of pend) {
    const r = settle(ctx, o, list, now);
    if (r && r.ok && !r.dup) {
      paid++;
      const lang = (U.getUser(ctx, o.uid) || {}).lang || 'en';
      try { await Bot.send(ctx, o.uid, o.sku === 'donate' ? Bot.thanksTxt(lang, r.stars) : Bot.tx(lang).paid(Bot.shopTxt(lang, o.sku)[0])); } catch (_) { /* player may have blocked the bot */ }
    }
  }
  return { paid };
}

module.exports = { enabled, solPrice, donateMinSol, solUsd, refreshPrice, createOrder, createDonation, checkOrder, sweep, fmtSol };
