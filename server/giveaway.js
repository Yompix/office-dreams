'use strict';
/* Sponsor giveaway: a few $ prizes for the most active verified players over a short window.
 *  - eligible: verified players (any contribution: SOL deposit, Stars, a shop purchase), not dev, not flagged by the anti-bot
 *  - score: Season points earned during the window (users.pts now − users.pts at the start), so it is the same
 *    server-priced, anti-bot-checked progress as the airdrop — nothing the client can fake
 *  - when the window ends the server freezes the top places, messages the winners and posts the result
 * Config: GIVEAWAY_ON=1, GIVEAWAY_KEY (a new key starts a new giveaway), GIVEAWAY_DAYS, GIVEAWAY_PRIZES ("20,10,5" in $), GIVEAWAY_URL (sponsor).
 */
const Eco = require('./economy.js');

const inited = new WeakSet();
function init(ctx) {
  if (inited.has(ctx.db)) return;
  ctx.db.raw.exec(`CREATE TABLE IF NOT EXISTS giveaway (id INTEGER PRIMARY KEY AUTOINCREMENT, gkey TEXT UNIQUE, start INTEGER, end_at INTEGER, prizes TEXT, url TEXT, status TEXT, winners TEXT, announced INTEGER DEFAULT 0);
    CREATE TABLE IF NOT EXISTS giveaway_base (gid INTEGER, uid INTEGER, pts REAL, PRIMARY KEY (gid, uid));
    CREATE TABLE IF NOT EXISTS giveaway_sub (gid INTEGER, uid INTEGER, ok INTEGER, at INTEGER, PRIMARY KEY (gid, uid));
    CREATE TABLE IF NOT EXISTS giveaway_pay (gid INTEGER, uid INTEGER, rank INTEGER, usd REAL, lamports INTEGER, to_addr TEXT, status TEXT DEFAULT 'wait', tx TEXT, tx_at INTEGER, err TEXT, told INTEGER DEFAULT 0, PRIMARY KEY (gid, uid));`);
  inited.add(ctx.db);
}
const conf = (ctx) => ctx.cfg.giveaway || {};
function current(ctx) {
  init(ctx);
  const g = ctx.db.get('SELECT * FROM giveaway ORDER BY id DESC LIMIT 1');
  if (!g) return null;
  g.prizes = JSON.parse(g.prizes || '[]');
  g.winners = g.winners ? JSON.parse(g.winners) : null;
  return g;
}
// creates the configured giveaway once (its start is the moment the server first sees the key)
function ensure(ctx, now = Date.now()) {
  const c = conf(ctx);
  if (!c.on || !c.key) return current(ctx);
  init(ctx);
  if (ctx.db.get('SELECT 1 AS y FROM giveaway WHERE gkey=?', c.key)) return current(ctx);
  ctx.db.tx(() => {
    const r = ctx.db.run('INSERT INTO giveaway(gkey,start,end_at,prizes,url,status) VALUES(?,?,?,?,?,?)', c.key, now, now + c.days * 86400e3, JSON.stringify(c.prizes), c.url || '', 'live');
    const gid = Number(r.lastInsertRowid);
    // everyone's points at the start: only what is earned from now on counts
    ctx.db.run('INSERT OR IGNORE INTO giveaway_base(gid,uid,pts) SELECT ?, id, pts FROM users', gid);
  });
  ctx.cache.gwBoard = null;
  ctx.log && ctx.log('giveaway started', c.key);
  return current(ctx);
}
/* ---- channel subscription (required): checked with getChatMember — the bot must be an admin of the channel ---- */
function subChat(ctx) { return (conf(ctx).chat || ctx.db.kvGet('gw_chat', '') || '').trim() || null; }
function subCached(ctx, g, uid) { const r = ctx.db.get('SELECT ok, at FROM giveaway_sub WHERE gid=? AND uid=?', g.id, uid); return r || null; }
async function checkSub(ctx, g, uid, now = Date.now()) {
  const chat = subChat(ctx);
  if (!chat) return null; // cannot be checked yet
  let ok = 0;
  try {
    const r = await ctx.tg.call('getChatMember', { chat_id: chat, user_id: uid });
    if (!r || !r.ok) { const prev = subCached(ctx, g, uid); return prev ? !!prev.ok : null; } // e.g. the bot is not an admin there
    const m = r.result, st = m && m.status;
    ok = st === 'creator' || st === 'administrator' || st === 'member' || (st === 'restricted' && m.is_member) ? 1 : 0;
  } catch (e) { const prev = subCached(ctx, g, uid); return prev ? !!prev.ok : null; }
  ctx.db.run('INSERT INTO giveaway_sub(gid,uid,ok,at) VALUES(?,?,?,?) ON CONFLICT(gid,uid) DO UPDATE SET ok=excluded.ok, at=excluded.at', g.id, uid, ok, now);
  ctx.cache.gwBoard = null;
  return !!ok;
}
// the sponsor channel is private (an invite link): find its id among the channels where the bot is an admin
const linkKey = (u) => String(u || '').trim().replace(/^https?:\/\//, '').replace(/^telegram\.me\//, 't.me/').replace(/\/$/, '').toLowerCase();
async function learnChat(ctx, chat) {
  const want = linkKey(conf(ctx).url);
  let match = false;
  try {
    const r = await ctx.tg.call('getChat', { chat_id: chat.id });
    const inv = r && r.ok && r.result && r.result.invite_link;
    const un = r && r.ok && r.result && r.result.username;
    match = !!want && ((inv && linkKey(inv) === want) || (un && want === 't.me/' + String(un).toLowerCase()));
  } catch (_) { /* ignore */ }
  const own = ctx.cfg.channel && chat.username && ('@' + chat.username).toLowerCase() === String(ctx.cfg.channel).toLowerCase();
  // a channel that is not the project's own is taken as the sponsor's; our own channel only if its link is the giveaway link
  if (match || !own) {
    ctx.db.kvSet('gw_chat', String(chat.id));
    ctx.db.kvSet('gw_chat_title', String(chat.title || ''));
    ctx.cache.gwBoard = null;
    ctx.log && ctx.log('giveaway subscription check uses', chat.id, chat.title, match ? '(link matches)' : '');
  }
}
// on start: maybe the bot was made an admin earlier — try the project channel
async function probeChat(ctx) {
  if (subChat(ctx) || !ctx.cfg.channel) return;
  try {
    const r = await ctx.tg.call('getChat', { chat_id: ctx.cfg.channel });
    if (r && r.ok && r.result) {
      const want = linkKey(conf(ctx).url), inv = r.result.invite_link;
      if (want && inv && linkKey(inv) === want) { ctx.db.kvSet('gw_chat', String(r.result.id)); ctx.log && ctx.log('giveaway subscription check uses the project channel', r.result.id); }
    }
  } catch (_) { /* ignore */ }
}
const verified = (ctx, uid) => !Eco.isDev(ctx, uid) && !!ctx.db.get("SELECT 1 AS y FROM purchases WHERE uid=? AND sku<>'adfee'", uid);
function board(ctx, g, now = Date.now()) {
  if (g.winners) return g.winners;
  const c = ctx.cache.gwBoard;
  if (c && c.gid === g.id && now - c.t < 20000) return c.rows;
  const rows = ctx.db.all(
    `SELECT u.id, COALESCE(u.studio, u.name) AS name, u.country, u.pts - COALESCE(b.pts, 0) AS sc
     FROM users u LEFT JOIN giveaway_base b ON b.gid=? AND b.uid=u.id
     WHERE u.deleted=0 AND u.risk_state IN ('ok','watch') AND u.id IN (SELECT DISTINCT uid FROM purchases WHERE sku<>'adfee')
     ${subChat(ctx) ? 'AND u.id IN (SELECT uid FROM giveaway_sub WHERE gid=' + Number(g.id) + ' AND ok=1)' : ''}
     ORDER BY sc DESC LIMIT 60`, g.id
  ).filter((r) => r.sc > 0.05 && !Eco.isDev(ctx, r.id)).slice(0, 50)
    .map((r, i) => ({ rank: i + 1, uid: r.id, name: r.name || '—', country: r.country || null, score: Math.round(r.sc * 10) / 10 }));
  ctx.cache.gwBoard = { gid: g.id, t: now, rows };
  return rows;
}
function myScore(ctx, g, uid) {
  const u = ctx.db.get('SELECT pts FROM users WHERE id=?', uid);
  const b = ctx.db.get('SELECT pts FROM giveaway_base WHERE gid=? AND uid=?', g.id, uid);
  return u ? Math.max(0, Math.round((u.pts - (b ? b.pts : 0)) * 10) / 10) : 0;
}
/* ---- what the player sees ---- */
function view(ctx, uid, now = Date.now()) {
  const g = current(ctx);
  if (!g) return null;
  if (g.status === 'done' && now - g.end_at > 7 * 86400e3) return null; // results stay visible for a week
  const rows = board(ctx, g, now);
  const mine = rows.find((r) => r.uid === uid);
  return {
    id: g.id, start: g.start, end: g.end_at, prizes: g.prizes, url: g.url, done: g.status === 'done',
    verified: verified(ctx, uid),
    sub: (() => { if (!subChat(ctx)) return null; const c = subCached(ctx, g, uid); return c ? !!c.ok : false; })(),
    me: { score: mine ? mine.score : myScore(ctx, g, uid), rank: mine ? mine.rank : null },
    pay: (() => { const p = ctx.db.get('SELECT usd, status, tx FROM giveaway_pay WHERE gid=? AND uid=?', g.id, uid); return p ? { usd: p.usd, st: p.status, tx: p.tx } : null; })(),
    top: rows.slice(0, 10).map((r) => ({ rank: r.rank, name: r.name, country: r.country, score: r.score, prize: r.prize || g.prizes[r.rank - 1] || 0, me: r.uid === uid })),
    n: rows.length,
  };
}
/* ---- the end: freeze the winners, tell them, post the result ---- */
const WIN_TXT = {
  en: (p, r, w) => `🏆 <b>You won the sponsor giveaway!</b>\nPlace <b>#${r}</b> — prize <b>$${p}</b>.\n\n${w ? 'The prize is being sent in SOL to your Solana wallet automatically.' : '👛 Connect a Solana wallet in the game (Airdrop → Token) — the prize will arrive there in SOL automatically.'}`,
  ru: (p, r, w) => `🏆 <b>Ты выиграл розыгрыш от спонсора!</b>\nМесто <b>#${r}</b> — приз <b>$${p}</b>.\n\n${w ? 'Приз уже отправляется в SOL на твой Solana-кошелёк — автоматически.' : '👛 Подключи Solana-кошелёк в игре (Аирдроп → Токен) — приз придёт туда в SOL автоматически.'}`,
  uk: (p, r, w) => `🏆 <b>Ти виграв розіграш від спонсора!</b>\nМісце <b>#${r}</b> — приз <b>$${p}</b>.\n\n${w ? 'Приз уже надсилається в SOL на твій Solana-гаманець — автоматично.' : '👛 Підключи Solana-гаманець у грі (Аірдроп → Токен) — приз прийде туди в SOL автоматично.'}`,
};
const PAID_TXT = {
  en: (p, sol, tx) => `✅ <b>Your prize is sent!</b> $${p} = <b>${sol} SOL</b> is in your wallet.\n<a href="https://solscan.io/tx/${tx}">View the transaction</a>`,
  ru: (p, sol, tx) => `✅ <b>Приз отправлен!</b> $${p} = <b>${sol} SOL</b> уже на твоём кошельке.\n<a href="https://solscan.io/tx/${tx}">Посмотреть транзакцию</a>`,
  uk: (p, sol, tx) => `✅ <b>Приз надіслано!</b> $${p} = <b>${sol} SOL</b> уже на твоєму гаманці.\n<a href="https://solscan.io/tx/${tx}">Переглянути транзакцію</a>`,
};
const RESULT_TXT = {
  en: (list) => `🏆 <b>Sponsor giveaway results</b>\n\n${list}\n\nCongratulations! Prizes are sent in SOL automatically.`,
  ru: (list) => `🏆 <b>Итоги розыгрыша от спонсора</b>\n\n${list}\n\nПоздравляем! Призы отправляются в SOL автоматически.`,
  uk: (list) => `🏆 <b>Підсумки розіграшу від спонсора</b>\n\n${list}\n\nВітаємо! Призи надсилаються в SOL автоматично.`,
};
const START_TXT = {
  en: (sum, d) => `🎁 <b>Sponsor giveaway: $${sum} for 3 players!</b>\n\nFor ${d} days the most active <b>verified</b> players win. Just play: releases, upgrades, quests — every point counts.\n\nNot verified yet? Any contribution (a $1 deposit, Stars or a purchase) — and you are in.`,
  ru: (sum, d) => `🎁 <b>Розыгрыш от спонсора: $${sum} на 3 игроков!</b>\n\n${d} дня побеждают самые активные <b>верифицированные</b> игроки. Просто играй: релизы, улучшения, квесты — каждое очко на счету.\n\nЕщё не верифицирован? Любой вклад ($1 депозит, звёзды или покупка) — и ты в игре.`,
  uk: (sum, d) => `🎁 <b>Розіграш від спонсора: $${sum} на 3 гравців!</b>\n\n${d} дні перемагають найактивніші <b>верифіковані</b> гравці. Просто грай: релізи, покращення, квести — кожен бал на рахунку.\n\nЩе не верифікований? Будь-який внесок ($1 депозит, зірки чи покупка) — і ти в грі.`,
};
let probed = false;
async function tick(ctx, now = Date.now()) {
  const g = ensure(ctx, now);
  if (!g) return null;
  if (!probed && g.status === 'live') {
    probed = true;
    await probeChat(ctx);
    const chat = subChat(ctx);
    if (chat) { // say in the log whether the bot can see the channel (it must be an admin there)
      const r = await ctx.tg.call('getChat', { chat_id: chat }).catch(() => null);
      ctx.log && ctx.log('giveaway channel', chat, r && r.ok ? 'ok: ' + (r.result.title || '') : 'NOT reachable: ' + ((r && r.description) || '?'));
    }
  }
  const Bot = require('./bot.js');
  // tell everyone once when it starts (the bot never spams: one message per giveaway)
  if (!g.announced && g.status === 'live') {
    ctx.db.run('UPDATE giveaway SET announced=1 WHERE id=?', g.id);
    const sum = g.prizes.reduce((a, b) => a + b, 0), days = Math.round((g.end_at - g.start) / 86400e3);
    const markup = (lang) => { const m = Bot.playMarkup(ctx, lang) || { inline_keyboard: [] }; if (g.url) m.inline_keyboard.push([{ text: '🤝 Sponsor', url: g.url }]); return m.inline_keyboard.length ? m : undefined; };
    if (ctx.cfg.channel) { try { await Bot.send(ctx, ctx.cfg.channel, START_TXT.ru(sum, days), markup('ru')); } catch (_) { /* channel may be misconfigured */ } }
    const users = ctx.db.all('SELECT id, lang FROM users WHERE deleted=0 AND blocked=0');
    for (const u of users) {
      try { await Bot.send(ctx, u.id, (START_TXT[u.lang] || START_TXT.en)(sum, days), markup(u.lang)); } catch (_) { /* blocked the bot */ }
      if (!ctx.fastAnnounce) await new Promise((r) => setTimeout(r, 40));
    }
  }
  if (g.status === 'live' && now >= g.end_at) {
    ctx.cache.gwBoard = null;
    let rows = board(ctx, g, now);
    if (subChat(ctx)) {
      const keep = [];
      for (const r of rows) { if (keep.length >= g.prizes.length) break; if (await checkSub(ctx, g, r.uid, now)) keep.push(r); }
      rows = keep;
    }
    const win = rows.slice(0, g.prizes.length).map((r, i) => Object.assign({}, r, { rank: i + 1, prize: g.prizes[i] }));
    ctx.db.run("UPDATE giveaway SET status='done', winners=? WHERE id=?", JSON.stringify(win), g.id);
    ctx.cache.gwBoard = null;
    ctx.log && ctx.log('giveaway finished', win.map((w) => w.uid + ':' + w.prize));
    for (const w of win) {
      ctx.db.run('INSERT OR IGNORE INTO giveaway_pay(gid,uid,rank,usd) VALUES(?,?,?,?)', g.id, w.uid, w.rank, w.prize);
      const u = ctx.db.get('SELECT lang, sol FROM users WHERE id=?', w.uid) || {};
      try { await Bot.send(ctx, w.uid, (WIN_TXT[u.lang] || WIN_TXT.en)(w.prize, w.rank, !!u.sol)); } catch (_) { /* blocked the bot */ }
    }
    if (ctx.cfg.channel && win.length) {
      const medal = ['🥇', '🥈', '🥉'];
      const list = win.map((w, i) => `${medal[i] || '🏅'} <b>${String(w.name).replace(/[<>&]/g, '')}</b> — $${w.prize}`).join('\n');
      try { await Bot.send(ctx, ctx.cfg.channel, ['uk', 'ru', 'en'].map((l) => RESULT_TXT[l](list)).join('\n\n— — —\n\n')); } catch (_) { /* ignore */ }
    }
  }
  await payTick(ctx, now);
  return current(ctx);
}
/* ---- prizes: sent in SOL from the server wallet to the winner's connected Solana wallet ---- */
const FEE_LAMPORTS = 10000;
async function payOne(ctx, row, now) {
  const Sol = require('./sol.js'), SolPay = require('./solpay.js'), Airdrop = require('./airdrop.js'), Bot = require('./bot.js');
  const u = ctx.db.get('SELECT lang, sol FROM users WHERE id=?', row.uid) || {};
  if (!u.sol) { if (row.status !== 'wait') ctx.db.run("UPDATE giveaway_pay SET status='wait' WHERE gid=? AND uid=?", row.gid, row.uid); return 'wait'; }
  // a transfer is already out: see how it ended
  if (row.status === 'sending' && row.tx) {
    const st = await Sol.rpc(ctx, 'getSignatureStatuses', [[row.tx], { searchTransactionHistory: true }]).catch(() => null);
    const v = st && st.value && st.value[0];
    if (v && !v.err && (v.confirmationStatus === 'confirmed' || v.confirmationStatus === 'finalized')) return done(row, u);
    if (!v || !v.err) { if (now - (row.tx_at || 0) < 180000) return 'sending'; }
  }
  const from = Airdrop.distributor(ctx);
  if (!from) return 'no_key';
  const lamports = row.lamports || Math.round((row.usd / SolPay.solUsd(ctx.cfg)) * 1e9);
  const bal = await Sol.rpc(ctx, 'getBalance', [from.publicKey.toBase58(), { commitment: 'confirmed' }]).catch(() => null);
  const have = bal && typeof bal.value === 'number' ? bal.value : -1;
  if (have >= 0 && have < lamports + FEE_LAMPORTS + 900000) { // keep a little for the airdrop transfer fees
    ctx.db.run("UPDATE giveaway_pay SET status='no_funds', lamports=? WHERE gid=? AND uid=?", lamports, row.gid, row.uid);
    return 'no_funds';
  }
  const lock = ctx.db.run("UPDATE giveaway_pay SET status='sending', lamports=?, to_addr=?, tx=NULL, tx_at=?, err=NULL WHERE gid=? AND uid=? AND status<>'sent' AND (status<>'sending' OR tx IS NULL OR tx_at < ?)", lamports, u.sol, now, row.gid, row.uid, now - 180000);
  if (!lock.changes) return 'sending';
  try {
    const { web3 } = Airdrop.lib();
    const tx = new web3.Transaction().add(web3.SystemProgram.transfer({ fromPubkey: from.publicKey, toPubkey: new web3.PublicKey(u.sol), lamports }));
    tx.feePayer = from.publicKey;
    const bh = await Sol.rpc(ctx, 'getLatestBlockhash', [{ commitment: 'confirmed' }]);
    tx.recentBlockhash = bh.value.blockhash;
    tx.sign(from);
    const sig = Sol.b58encode(tx.signature);
    ctx.db.run('UPDATE giveaway_pay SET tx=? WHERE gid=? AND uid=?', sig, row.gid, row.uid);
    await Sol.rpc(ctx, 'sendTransaction', [tx.serialize().toString('base64'), { encoding: 'base64', skipPreflight: true, maxRetries: 5 }]);
    for (let i = 0; i < 20; i++) {
      const st = await Sol.rpc(ctx, 'getSignatureStatuses', [[sig]]).catch(() => null);
      const v = st && st.value && st.value[0];
      if (v && v.err) throw new Error('rejected: ' + JSON.stringify(v.err));
      if (v && (v.confirmationStatus === 'confirmed' || v.confirmationStatus === 'finalized')) return done(Object.assign({}, row, { tx: sig, lamports }), u);
      if (!ctx.solRpc) await new Promise((r) => setTimeout(r, 2000));
    }
    return 'sending'; // the next tick checks it
  } catch (e) {
    ctx.db.run("UPDATE giveaway_pay SET status='failed', err=? WHERE gid=? AND uid=?", String(e.message || e).slice(0, 300), row.gid, row.uid);
    ctx.log && ctx.log('giveaway payout failed', row.uid, e.message);
    return 'failed';
  }
  function done(r, usr) {
    const upd = ctx.db.run("UPDATE giveaway_pay SET status='sent', told=1 WHERE gid=? AND uid=? AND status<>'sent'", r.gid, r.uid);
    if (upd.changes) {
      const sol = (Number(r.lamports) / 1e9).toFixed(4).replace(/0+$/, '').replace(/\.$/, '');
      Bot.send(ctx, r.uid, (PAID_TXT[usr.lang] || PAID_TXT.en)(r.usd, sol, r.tx)).catch(() => {});
      ctx.log && ctx.log('giveaway prize sent', r.uid, r.usd, r.tx);
    }
    return 'sent';
  }
}
let paying = false;
async function payTick(ctx, now = Date.now()) {
  if (paying) return;
  paying = true;
  try {
    init(ctx);
    const rows = ctx.db.all("SELECT * FROM giveaway_pay WHERE status<>'sent' ORDER BY rank");
    for (const r of rows) await payOne(ctx, r, now);
  } finally { paying = false; }
}
function adminView(ctx) {
  const g = current(ctx);
  if (!g) return { ok: true, giveaway: null };
  const rows = board(ctx, g).slice(0, 10).map((r) => {
    const u = ctx.db.get('SELECT username, sol FROM users WHERE id=?', r.uid) || {};
    const pay = ctx.db.get('SELECT status, tx FROM giveaway_pay WHERE gid=? AND uid=?', g.id, r.uid);
    return Object.assign({}, r, { username: u.username || null, sol: u.sol || null, prize: r.prize || g.prizes[r.rank - 1] || 0, pay: pay ? pay.status : null, tx: pay ? pay.tx : null });
  });
  let wallet = null;
  try { const d = require('./airdrop.js').distributor(ctx); wallet = d ? d.publicKey.toBase58() : null; } catch (_) { /* no key */ }
  return { ok: true, giveaway: { key: g.gkey, start: g.start, end: g.end_at, status: g.status, prizes: g.prizes, url: g.url }, top: rows, payWallet: wallet };
}

module.exports = { init, ensure, current, view, tick, board, adminView, payTick, checkSub, subChat, learnChat };
