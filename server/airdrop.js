'use strict';
/*
 * OFFICE airdrop: the whole supply is split between players by Season 0 points.
 *
 *  season   → points are collected; the airdrop starts by itself once the launch fund reaches its goal ($5000)
 *  snapshot → points are frozen into fixed shares (bots, closed regions and the developer account get nothing);
 *             the bot announces it to every player and to the channel
 *  claim    → the token and the distributor wallet are configured: a player presses "Claim" in the game and the
 *             server sends their share from the distributor wallet to their Solana wallet (once, idempotent)
 *
 * The distributor is a separate wallet that holds the 100M OFFICE and a little SOL for fees; its secret key is in
 * AIRDROP_SECRET. Nothing here ever touches the owner's main wallet.
 */
const Eco = require('./economy.js');
const U = require('./users.js');
const Sol = require('./sol.js');
const Bot = require('./bot.js');

const ready = new WeakSet();
function init(ctx) {
  if (ready.has(ctx.db)) return; ready.add(ctx.db);
  ctx.db.raw.exec(`CREATE TABLE IF NOT EXISTS airdrop_state (id INTEGER PRIMARY KEY CHECK (id = 1), status TEXT, at INTEGER, pts REAL, n INTEGER, announced INTEGER DEFAULT 0);
    CREATE TABLE IF NOT EXISTS airdrop_alloc (uid INTEGER PRIMARY KEY, pts REAL, amount TEXT, status TEXT DEFAULT '', to_addr TEXT, tx TEXT, tx_at INTEGER, err TEXT);
    INSERT OR IGNORE INTO airdrop_state(id, status, at, pts, n) VALUES (1, 'season', 0, 0, 0);`);
}
const state = (ctx) => { init(ctx); return ctx.db.get('SELECT * FROM airdrop_state WHERE id=1'); };

/* ---- the distributor wallet (lazy: the Solana libraries load only when the airdrop is used) ---- */
let LIB = null;
function lib() {
  if (LIB) return LIB;
  const w = console.warn; console.warn = () => {}; // the bundle warns once about optional native bindings
  try { LIB = require('./vendor/solana.js'); } finally { console.warn = w; }
  return LIB;
}
function distributor(cfg) {
  const s = cfg.airdrop && cfg.airdrop.secret;
  if (!s) return null;
  try {
    const bytes = s.trim().startsWith('[') ? JSON.parse(s) : Sol.b58decode(s.trim());
    if (!bytes || bytes.length !== 64) return null;
    return lib().web3.Keypair.fromSecretKey(Uint8Array.from(bytes));
  } catch (e) { return null; }
}
const tokenReady = (cfg) => !!(cfg.token && Sol.isAddr(cfg.token.addr) && distributor(cfg));

/* ---- start: snapshot of points → fixed shares ---- */
function snapshot(ctx, now) {
  init(ctx);
  const st = state(ctx);
  if (st.status !== 'season') return { ok: false, err: 'already' };
  const rows = ctx.db.all("SELECT id, pts, country FROM users WHERE deleted=0 AND pts>0 AND risk_state IN ('ok','watch')")
    .filter((r) => !ctx.cfg.blockedCountries.includes(r.country) && !Eco.isDev(ctx, r.id));
  const total = rows.reduce((t, r) => t + r.pts, 0);
  const units = BigInt(Math.round(ctx.cfg.airdrop.supply)) * 10n ** BigInt(ctx.cfg.airdrop.decimals);
  const SCALE = 1000000n; // points have one decimal; scale them to integers for exact BigInt shares
  const totalI = BigInt(Math.round(total * 10)) * SCALE;
  ctx.db.tx(() => {
    ctx.db.run('DELETE FROM airdrop_alloc');
    for (const r of rows) {
      const amount = totalI > 0n ? (units * BigInt(Math.round(r.pts * 10)) * SCALE) / totalI : 0n;
      if (amount > 0n) ctx.db.run('INSERT INTO airdrop_alloc(uid, pts, amount) VALUES (?,?,?)', r.id, r.pts, amount.toString());
    }
    ctx.db.run("UPDATE airdrop_state SET status=?, at=?, pts=?, n=? WHERE id=1", tokenReady(ctx.cfg) ? 'claim' : 'snapshot', now, total, rows.length);
  });
  ctx.log && ctx.log('airdrop snapshot', { players: rows.length, pts: total });
  return { ok: true, players: rows.length, pts: total };
}

/* ---- automatic: runs every minute and after donations ---- */
async function tick(ctx, now = Date.now()) {
  const st = state(ctx);
  if (st.status === 'season') {
    if (!ctx.cfg.airdrop.auto) return { st: st.status };
    const f = Eco.fundView(ctx, 0, now);
    if (f.usd < f.goalUsd) return { st: st.status };
    snapshot(ctx, now);
  }
  const s2 = state(ctx);
  if (s2.status === 'snapshot' && tokenReady(ctx.cfg)) ctx.db.run("UPDATE airdrop_state SET status='claim' WHERE id=1");
  if (!s2.announced) { ctx.db.run('UPDATE airdrop_state SET announced=1 WHERE id=1'); announce(ctx).catch((e) => ctx.log && ctx.log('airdrop announce', e.message)); }
  return { st: state(ctx).status };
}

/* ---- announcement: channel post + a message to every player (Telegram allows ~30 messages a second) ---- */
const ANN = {
  en: (sym, amt) => `🎉 <b>The ${sym} airdrop has started!</b>\n\nThe launch fund reached its goal, so Season 0 points are now turned into ${sym}: all 100,000,000 go to players.\n\n${amt ? `🎁 Your share: <b>${amt} ${sym}</b>\n\n` : ''}Open the game → Airdrop → Token, add your Solana wallet and press «Claim».`,
  ru: (sym, amt) => `🎉 <b>Аирдроп ${sym} начался!</b>\n\nФонд запуска собран, поэтому очки Сезона 0 превращаются в ${sym}: все 100 000 000 получают игроки.\n\n${amt ? `🎁 Твоя доля: <b>${amt} ${sym}</b>\n\n` : ''}Открой игру → Аирдроп → Токен, добавь Solana-кошелёк и нажми «Забрать».`,
  uk: (sym, amt) => `🎉 <b>Аірдроп ${sym} почався!</b>\n\nФонд запуску зібрано, тож бали Сезону 0 перетворюються на ${sym}: усі 100 000 000 отримують гравці.\n\n${amt ? `🎁 Твоя частка: <b>${amt} ${sym}</b>\n\n` : ''}Відкрий гру → Аірдроп → Токен, додай Solana-гаманець і натисни «Забрати».`,
};
const human = (ctx, amount) => {
  const v = Number(BigInt(amount || 0)) / 10 ** ctx.cfg.airdrop.decimals;
  return v >= 100 ? Math.floor(v).toLocaleString('en-US').replace(/,/g, ' ') : v.toFixed(2);
};
async function announce(ctx) {
  const sym = ctx.cfg.tokenSymbol || 'OFFICE';
  if (ctx.cfg.channel) { try { await Bot.send(ctx, ctx.cfg.channel, ANN.ru(sym, '')); } catch (_) { /* channel may be misconfigured */ } }
  const users = ctx.db.all('SELECT u.id, u.lang, a.amount FROM users u LEFT JOIN airdrop_alloc a ON a.uid=u.id WHERE u.deleted=0 AND u.blocked=0');
  let sent = 0;
  for (const u of users) {
    try { await Bot.send(ctx, u.id, (ANN[u.lang] || ANN.en)(sym, u.amount ? human(ctx, u.amount) : ''), Bot.playMarkup(ctx, u.lang)); sent++; } catch (_) { /* blocked the bot */ }
    if (!ctx.fastAnnounce) await new Promise((r) => setTimeout(r, 40));
  }
  ctx.log && ctx.log('airdrop announced', { sent });
  return sent;
}

/* ---- what the player sees ---- */
function view(ctx, u) {
  const st = state(ctx);
  const f = Eco.fundView(ctx, 0);
  const a = st.status === 'season' ? null : ctx.db.get('SELECT * FROM airdrop_alloc WHERE uid=?', u.id);
  return {
    st: st.status, usd: f.usd, goal: f.goalUsd, at: st.at, players: st.n,
    amount: a ? Number(BigInt(a.amount)) / 10 ** ctx.cfg.airdrop.decimals : 0,
    claim: a ? a.status || '' : '', tx: a && a.status === 'sent' ? a.tx : null,
  };
}

/* ---- claim: send the share from the distributor wallet to the player's Solana wallet ---- */
async function claim(ctx, uid, now = Date.now()) {
  const st = state(ctx);
  if (st.status !== 'claim') return { ok: false, err: st.status === 'season' ? 'airdrop_notyet' : 'airdrop_soon' };
  const u = U.getUser(ctx, uid);
  if (!u || !u.sol) return { ok: false, err: 'sol_need' };
  const a = ctx.db.get('SELECT * FROM airdrop_alloc WHERE uid=?', uid);
  if (!a || BigInt(a.amount) <= 0n) return { ok: false, err: 'airdrop_none' };
  if (a.status === 'sent') return { ok: true, tx: a.tx, already: true };
  // a transfer is in flight: check how it ended before allowing another one
  if (a.status === 'sending' && a.tx) {
    const s = (await Sol.rpc(ctx, 'getSignatureStatuses', [[a.tx], { searchTransactionHistory: true }]).catch(() => null));
    const v = s && s.value && s.value[0];
    if (v && !v.err && (v.confirmationStatus === 'confirmed' || v.confirmationStatus === 'finalized')) {
      ctx.db.run("UPDATE airdrop_alloc SET status='sent' WHERE uid=?", uid);
      return { ok: true, tx: a.tx };
    }
    if (!v && now - (a.tx_at || 0) < 120000) return { ok: false, err: 'airdrop_wait' };
    if (v && !v.err) return { ok: false, err: 'airdrop_wait' };
  }
  // take the lock in one statement: only one request can move '' / 'failed' / stale 'sending' → 'sending'
  const lock = ctx.db.run("UPDATE airdrop_alloc SET status='sending', to_addr=?, tx=NULL, tx_at=?, err=NULL WHERE uid=? AND (status IN ('', 'failed') OR (status='sending' AND tx_at < ?))", u.sol, now, uid, now - 120000);
  if (!lock.changes) return { ok: false, err: 'airdrop_wait' };
  try {
    const { web3, spl } = lib();
    const from = distributor(ctx.cfg);
    const mint = new web3.PublicKey(ctx.cfg.token.addr.trim());
    const owner = new web3.PublicKey(u.sol);
    const info = await Sol.rpc(ctx, 'getAccountInfo', [mint.toBase58(), { encoding: 'base64' }]);
    const prog = new web3.PublicKey((info && info.value && info.value.owner) || spl.TOKEN_2022_PROGRAM_ID.toBase58());
    const src = spl.getAssociatedTokenAddressSync(mint, from.publicKey, false, prog);
    const dst = spl.getAssociatedTokenAddressSync(mint, owner, true, prog);
    const tx = new web3.Transaction().add(
      spl.createAssociatedTokenAccountIdempotentInstruction(from.publicKey, dst, owner, mint, prog),
      spl.createTransferCheckedInstruction(src, mint, dst, from.publicKey, BigInt(a.amount), ctx.cfg.airdrop.decimals, [], prog),
    );
    tx.feePayer = from.publicKey;
    const bh = await Sol.rpc(ctx, 'getLatestBlockhash', [{ commitment: 'confirmed' }]);
    tx.recentBlockhash = bh.value.blockhash;
    tx.sign(from);
    const sig = Sol.b58encode(tx.signature);
    ctx.db.run('UPDATE airdrop_alloc SET tx=?, tx_at=? WHERE uid=?', sig, now, uid);
    await Sol.rpc(ctx, 'sendTransaction', [tx.serialize().toString('base64'), { encoding: 'base64', preflightCommitment: 'confirmed' }]);
    for (let i = 0; i < 30; i++) {
      const s = await Sol.rpc(ctx, 'getSignatureStatuses', [[sig]]).catch(() => null);
      const v = s && s.value && s.value[0];
      if (v && v.err) throw new Error('rejected: ' + JSON.stringify(v.err));
      if (v && (v.confirmationStatus === 'confirmed' || v.confirmationStatus === 'finalized')) {
        ctx.db.run("UPDATE airdrop_alloc SET status='sent' WHERE uid=?", uid);
        return { ok: true, tx: sig };
      }
      if (!ctx.solRpc) await new Promise((r) => setTimeout(r, 2000));
    }
    return { ok: false, err: 'airdrop_wait' }; // still in flight; the next press checks it
  } catch (e) {
    ctx.db.run("UPDATE airdrop_alloc SET status='failed', err=? WHERE uid=?", String(e.message || e).slice(0, 300), uid);
    ctx.log && ctx.log('airdrop claim failed', uid, e.message);
    return { ok: false, err: 'airdrop_failed' };
  }
}

/* ---- admin ---- */
function adminView(ctx) {
  const st = state(ctx);
  const c = ctx.db.get("SELECT COUNT(*) AS n, SUM(CASE WHEN status='sent' THEN 1 ELSE 0 END) AS sent, SUM(CASE WHEN status='failed' THEN 1 ELSE 0 END) AS failed FROM airdrop_alloc");
  const d = distributor(ctx.cfg);
  return { ok: true, state: st, allocs: c.n, sent: c.sent || 0, failed: c.failed || 0, mint: (ctx.cfg.token && ctx.cfg.token.addr) || null, distributor: d ? d.publicKey.toBase58() : null, fund: Eco.fundView(ctx, 0) };
}

module.exports = { tick, snapshot, view, claim, adminView, state, announce, tokenReady };
