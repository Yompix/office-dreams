'use strict';
/* Paying for shop items with TON.
 *
 * Flow: the client asks for an order → gets {to, amount, comment "OD-<id>"} → sends the transfer
 * through TON Connect → the server watches the shop wallet (toncenter API) for an incoming transfer
 * with that exact comment and at least that amount, then grants the item once (idempotent by tx hash).
 * Nothing is trusted from the client: only the blockchain decides.
 */
const crypto = require('crypto');
const Eco = require('./economy.js');
const U = require('./users.js');
const Ton = require('./ton.js');
const Bot = require('./bot.js');

const ORDER_TTL = 48 * 3600 * 1000; // late transfers are still credited for 2 days
const NANO = 1000000000n;

function enabled(cfg) { return !!(cfg.tonPay && cfg.tonPay.wallet && Ton.parseAddress(cfg.tonPay.wallet)); }

// price in TON: stars × (user price of a star) ÷ TON price, rounded up to 0.05 TON
function tonPrice(cfg, it) {
  const tp = cfg.tonPay;
  const ton = Math.ceil(((it.stars * tp.starUsd) / tp.usd) * 20) / 20;
  return Math.max(0.05, Math.round(ton * 100) / 100);
}
const toNano = (ton) => (BigInt(Math.round(ton * 100)) * NANO) / 100n;

function createOrder(ctx, uid, sku, now) {
  if (!enabled(ctx.cfg)) return { ok: false, err: 'not_configured' };
  const it = Eco.shopItem(sku);
  if (!it || !Eco.canBuy(ctx, uid, sku)) return { ok: false, err: 'unavailable' };
  const recent = ctx.db.get('SELECT COUNT(*) AS n FROM ton_orders WHERE uid=? AND ts>?', uid, now - 3600 * 1000).n;
  if (recent >= 10) return { ok: false, err: 'rate' };
  const id = crypto.randomBytes(5).toString('hex');
  const ton = tonPrice(ctx.cfg, it);
  const nano = toNano(ton).toString();
  ctx.db.run("INSERT INTO ton_orders(id,uid,sku,nano,ts,status,tx) VALUES(?,?,?,?,?,'pending',NULL)", id, uid, sku, nano, now);
  return { ok: true, order: { id, sku, ton, amount: nano, to: ctx.cfg.tonPay.wallet, comment: 'OD-' + id, validUntil: Math.floor(now / 1000) + 15 * 60 } };
}

/* ---- reading the shop wallet ---- */
async function defaultFetchTx(ctx) {
  const tp = ctx.cfg.tonPay;
  const url = `${tp.api}/getTransactions?address=${encodeURIComponent(tp.wallet)}&limit=60&archival=true`;
  const r = await fetch(url, { headers: tp.key ? { 'X-API-Key': tp.key } : {}, signal: AbortSignal.timeout(8000) });
  const j = await r.json();
  if (!j || !j.ok || !Array.isArray(j.result)) throw new Error('toncenter');
  return j.result;
}
function commentOf(m) {
  if (!m) return '';
  if (typeof m.message === 'string' && m.message) return m.message.trim();
  const d = m.msg_data;
  if (d && d['@type'] === 'msg.dataText' && d.text) { try { return Buffer.from(d.text, 'base64').toString('utf8').trim(); } catch (_) { return ''; } }
  return '';
}
// incoming transfers, cached for a few seconds so many players checking at once make one request
async function incoming(ctx, now) {
  const c = ctx.cache.tonTx;
  if (c && now - c.at < 5000) return c.list;
  const fetchTx = ctx.tonApi || defaultFetchTx;
  const raw = await fetchTx(ctx);
  const list = [];
  for (const t of raw) {
    const m = t.in_msg;
    if (!m || !m.source || !m.value) continue; // external or empty messages are not payments
    const comment = commentOf(m);
    if (!/^OD-[0-9a-f]{10}$/.test(comment)) continue;
    list.push({ comment, value: BigInt(m.value), hash: (t.transaction_id && t.transaction_id.hash) || '', lt: (t.transaction_id && t.transaction_id.lt) || '', from: m.source });
  }
  ctx.cache.tonTx = { at: now, list };
  return list;
}

async function settle(ctx, order, list, now) {
  const tx = list.find((x) => x.comment === 'OD-' + order.id && x.value >= BigInt(order.nano));
  if (!tx) return null;
  const it = Eco.shopItem(order.sku);
  const ton = Number(BigInt(order.nano) * 100n / NANO) / 100;
  const r = Eco.fulfil(ctx, order.uid, order.sku, 'ton:' + (tx.hash || tx.lt), it.stars, now, { usd: ton * ctx.cfg.tonPay.usd, meta: JSON.stringify({ ton, from: tx.from, order: order.id }) });
  ctx.db.run("UPDATE ton_orders SET status='paid', tx=? WHERE id=?", tx.hash || tx.lt, order.id);
  return r;
}

async function checkOrder(ctx, uid, id, now) {
  const o = ctx.db.get('SELECT * FROM ton_orders WHERE id=? AND uid=?', String(id), uid);
  if (!o) return { ok: false, err: 'unavailable' };
  if (o.status === 'paid') return { ok: true, paid: true };
  if (now - o.ts > ORDER_TTL) return { ok: true, paid: false, expired: true };
  let list;
  try { list = await incoming(ctx, now); } catch (e) { return { ok: true, paid: false, retry: true }; }
  const r = await settle(ctx, o, list, now);
  return { ok: true, paid: !!(r && r.ok) };
}

// background: credits transfers even if the player closed the game before confirmation
async function sweep(ctx, now = ctx.now()) {
  if (!enabled(ctx.cfg)) return { paid: 0 };
  const pend = ctx.db.all("SELECT * FROM ton_orders WHERE status='pending' AND ts>?", now - ORDER_TTL);
  if (!pend.length) return { paid: 0 };
  const list = await incoming(ctx, now);
  let paid = 0;
  for (const o of pend) {
    const r = await settle(ctx, o, list, now);
    if (r && r.ok && !r.dup) {
      paid++;
      const lang = (U.getUser(ctx, o.uid) || {}).lang || 'en';
      try { await Bot.send(ctx, o.uid, Bot.tx(lang).paid(Bot.shopTxt(lang, o.sku)[0])); } catch (_) { /* player may have blocked the bot */ }
    }
  }
  return { paid };
}

function walletValid(addr) { return !!Ton.parseAddress(addr); }

module.exports = { enabled, tonPrice, createOrder, checkOrder, sweep, walletValid, commentOf };
