'use strict';
/* Sponsors of the browser games (YOMPIX GAMES): donations in Telegram Stars through the bot, shown as a public sponsors list.
   Flow: the games site links to t.me/<bot>?start=games -> the bot sends Stars invoices -> a paid donation lands in
   (table game_sponsors — not purchases, so it never verifies anyone or touches the Office Dreams fund/airdrop) -> GET /api/games/sponsors lists them. */
const crypto = require('crypto');

const AMOUNTS = [50, 100, 250, 500, 1000];
const SKU = 'gdonate';

const cleanName = (s) => String(s || '').replace(/[^\p{L}\p{N} ._\-]/gu, '').replace(/\s+/g, ' ').trim().slice(0, 24);
const kvGet = (ctx, k) => { const r = ctx.db.get('SELECT v FROM kv WHERE k=?', k); return r ? r.v : null; };
const kvSet = (ctx, k, v) => (v == null ? ctx.db.run('DELETE FROM kv WHERE k=?', k) : ctx.db.run('INSERT INTO kv(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v', k, String(v)));

function donateLink(cfg) { return cfg.botUsername ? `https://t.me/${cfg.botUsername}?start=games` : ''; }

// the invoice buttons the bot sends after /start games
async function invoiceMarkup(ctx, uid) {
  const rows = [];
  for (const n of AMOUNTS) {
    const r = await ctx.tg.call('createInvoiceLink', {
      title: 'Поддержать YOMPIX GAMES', description: `${n} ⭐ на развитие игры «Night Shift». Твоё имя появится в списке спонсоров игры.`,
      payload: `${uid}:${SKU}:${n}:${crypto.randomBytes(4).toString('hex')}`,
      provider_token: '', currency: 'XTR', prices: [{ label: 'Поддержка', amount: n }],
    });
    if (r && r.ok) rows.push({ text: `⭐ ${n}`, url: r.result });
  }
  const kb = [];
  for (let i = 0; i < rows.length; i += 3) kb.push(rows.slice(i, i + 3));
  return kb.length ? { inline_keyboard: kb } : null;
}

const validCheckout = (q, parts) => parts[1] === SKU && Number(parts[0]) === q.from.id && q.currency === 'XTR' && AMOUNTS.includes(q.total_amount) && q.total_amount === Number(parts[2]);

// idempotent: Telegram may deliver successful_payment more than once
function record(ctx, from, chargeId, stars, now = Date.now()) {
  const name = cleanName(kvGet(ctx, 'gsp_name:' + from.id) || from.first_name || from.username) || 'Аноним';
  const ins = ctx.db.run('INSERT OR IGNORE INTO game_sponsors(charge_id,uid,stars,ts,name) VALUES(?,?,?,?,?)', String(chargeId), from.id, stars, now, name);
  ctx.cache.gsponsors = null;
  return { ok: true, dup: !ins.changes, name };
}

// the donor's own settings: /sponsor <name>, /sponsor - (hide), /sponsor + (show again)
function setOwn(ctx, uid, arg) {
  if (arg === '-') { kvSet(ctx, 'gsp_hide:' + uid, 1); ctx.cache.gsponsors = null; return 'Готово: тебя не видно в списке спонсоров. Вернуть: /sponsor +'; }
  if (arg === '+') { kvSet(ctx, 'gsp_hide:' + uid, null); ctx.cache.gsponsors = null; return 'Готово: ты снова в списке спонсоров.'; }
  const n = cleanName(arg);
  if (!n) return 'Напиши имя для списка спонсоров: /sponsor Имя\nСкрыть себя: /sponsor -';
  kvSet(ctx, 'gsp_name:' + uid, n); ctx.cache.gsponsors = null;
  return `Готово: в списке спонсоров ты будешь как «${n}».`;
}

// owner moderation: hide or show a sponsor by Telegram id
function setHidden(ctx, uid, hidden) { kvSet(ctx, 'gsp_ban:' + uid, hidden ? 1 : null); ctx.cache.gsponsors = null; }

function rows(ctx) {
  return ctx.db.all(`SELECT uid, SUM(stars) AS stars, MIN(ts) AS since, (SELECT name FROM game_sponsors g2 WHERE g2.uid=g.uid ORDER BY ts DESC LIMIT 1) AS name
    FROM game_sponsors g GROUP BY uid ORDER BY stars DESC, since ASC`).map((r) => {
    const name = kvGet(ctx, 'gsp_name:' + r.uid) || r.name;
    return { uid: r.uid, stars: r.stars, name: cleanName(name) || 'Аноним', hidden: !!kvGet(ctx, 'gsp_hide:' + r.uid), banned: !!kvGet(ctx, 'gsp_ban:' + r.uid) };
  });
}

// public list (cached a minute): names and stars only, no ids
function publicView(ctx, now = Date.now()) {
  const c = ctx.cache.gsponsors;
  if (c && now - c.at < 60e3) return c.v;
  const all = rows(ctx);
  const list = all.filter((r) => !r.hidden && !r.banned).slice(0, 200).map((r) => ({ name: r.name, stars: r.stars }));
  const v = { ok: true, donate: donateLink(ctx.cfg), list, total: all.reduce((s, r) => s + r.stars, 0), count: all.length };
  ctx.cache.gsponsors = { at: now, v };
  return v;
}

function ownerReport(ctx) {
  const all = rows(ctx);
  if (!all.length) return 'Спонсоров игр пока нет.\nСсылка для донатов: ' + (donateLink(ctx.cfg) || '—');
  const total = all.reduce((s, r) => s + r.stars, 0);
  return `⭐ <b>Спонсоры игр</b>: ${all.length}, всего ${total} ⭐\n\n` + all.slice(0, 50).map((r, i) => `${i + 1}. ${r.name} — ${r.stars} ⭐ <code>${r.uid}</code>${r.banned ? ' 🚫' : r.hidden ? ' (скрыт сам)' : ''}`).join('\n')
    + '\n\nСкрыть: /sponsorhide id · вернуть: /sponsorshow id';
}

module.exports = { AMOUNTS, SKU, donateLink, invoiceMarkup, validCheckout, record, setOwn, setHidden, publicView, ownerReport, cleanName };
