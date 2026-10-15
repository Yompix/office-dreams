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
    CREATE TABLE IF NOT EXISTS giveaway_base (gid INTEGER, uid INTEGER, pts REAL, PRIMARY KEY (gid, uid));`);
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
const verified = (ctx, uid) => !Eco.isDev(ctx, uid) && !!ctx.db.get("SELECT 1 AS y FROM purchases WHERE uid=? AND sku<>'adfee'", uid);
function board(ctx, g, now = Date.now()) {
  if (g.winners) return g.winners;
  const c = ctx.cache.gwBoard;
  if (c && c.gid === g.id && now - c.t < 20000) return c.rows;
  const rows = ctx.db.all(
    `SELECT u.id, COALESCE(u.studio, u.name) AS name, u.country, u.pts - COALESCE(b.pts, 0) AS sc
     FROM users u LEFT JOIN giveaway_base b ON b.gid=? AND b.uid=u.id
     WHERE u.deleted=0 AND u.risk_state IN ('ok','watch') AND u.id IN (SELECT DISTINCT uid FROM purchases WHERE sku<>'adfee')
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
    me: { score: mine ? mine.score : myScore(ctx, g, uid), rank: mine ? mine.rank : null },
    top: rows.slice(0, 10).map((r) => ({ rank: r.rank, name: r.name, country: r.country, score: r.score, prize: r.prize || g.prizes[r.rank - 1] || 0, me: r.uid === uid })),
    n: rows.length,
  };
}
/* ---- the end: freeze the winners, tell them, post the result ---- */
const WIN_TXT = {
  en: (p, r) => `🏆 <b>You won the sponsor giveaway!</b>\nPlace <b>#${r}</b> — prize <b>$${p}</b>.\n\nWrite to the admin @vtmjs to receive it.`,
  ru: (p, r) => `🏆 <b>Ты выиграл розыгрыш от спонсора!</b>\nМесто <b>#${r}</b> — приз <b>$${p}</b>.\n\nНапиши админу @vtmjs, чтобы получить его.`,
  uk: (p, r) => `🏆 <b>Ти виграв розіграш від спонсора!</b>\nМісце <b>#${r}</b> — приз <b>$${p}</b>.\n\nНапиши адміну @vtmjs, щоб отримати його.`,
};
const START_TXT = {
  en: (sum, d) => `🎁 <b>Sponsor giveaway: $${sum} for 3 players!</b>\n\nFor ${d} days the most active <b>verified</b> players win. Just play: releases, upgrades, quests — every point counts.\n\nNot verified yet? Any contribution (a $1 deposit, Stars or a purchase) — and you are in.`,
  ru: (sum, d) => `🎁 <b>Розыгрыш от спонсора: $${sum} на 3 игроков!</b>\n\n${d} дня побеждают самые активные <b>верифицированные</b> игроки. Просто играй: релизы, улучшения, квесты — каждое очко на счету.\n\nЕщё не верифицирован? Любой вклад ($1 депозит, звёзды или покупка) — и ты в игре.`,
  uk: (sum, d) => `🎁 <b>Розіграш від спонсора: $${sum} на 3 гравців!</b>\n\n${d} дні перемагають найактивніші <b>верифіковані</b> гравці. Просто грай: релізи, покращення, квести — кожен бал на рахунку.\n\nЩе не верифікований? Будь-який внесок ($1 депозит, зірки чи покупка) — і ти в грі.`,
};
async function tick(ctx, now = Date.now()) {
  const g = ensure(ctx, now);
  if (!g) return null;
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
    const rows = board(ctx, g, now);
    const win = rows.slice(0, g.prizes.length).map((r, i) => Object.assign({}, r, { prize: g.prizes[i] }));
    ctx.db.run("UPDATE giveaway SET status='done', winners=? WHERE id=?", JSON.stringify(win), g.id);
    ctx.cache.gwBoard = null;
    ctx.log && ctx.log('giveaway finished', win.map((w) => w.uid + ':' + w.prize));
    for (const w of win) {
      const u = ctx.db.get('SELECT lang FROM users WHERE id=?', w.uid);
      try { await Bot.send(ctx, w.uid, (WIN_TXT[(u && u.lang)] || WIN_TXT.en)(w.prize, w.rank)); } catch (_) { /* blocked the bot */ }
    }
    if (ctx.cfg.channel && win.length) {
      const medal = ['🥇', '🥈', '🥉'];
      const list = win.map((w, i) => `${medal[i] || '🏅'} <b>${String(w.name).replace(/[<>&]/g, '')}</b> — $${w.prize}`).join('\n');
      try { await Bot.send(ctx, ctx.cfg.channel, `🏆 <b>Итоги розыгрыша от спонсора</b>\n\n${list}\n\nПоздравляем! Призы выдаёт админ @vtmjs.`); } catch (_) { /* ignore */ }
    }
  }
  return current(ctx);
}
function adminView(ctx) {
  const g = current(ctx);
  if (!g) return { ok: true, giveaway: null };
  const rows = board(ctx, g).slice(0, 10).map((r) => {
    const u = ctx.db.get('SELECT username, sol FROM users WHERE id=?', r.uid) || {};
    return Object.assign({}, r, { username: u.username || null, sol: u.sol || null, prize: r.prize || g.prizes[r.rank - 1] || 0 });
  });
  return { ok: true, giveaway: { key: g.gkey, start: g.start, end: g.end_at, status: g.status, prizes: g.prizes, url: g.url }, top: rows };
}

module.exports = { init, ensure, current, view, tick, board, adminView };
