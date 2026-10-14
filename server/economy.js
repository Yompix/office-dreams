'use strict';
/* Airdrop-points economy.
 *  - points are priced by the SERVER from verified progress (never from client claims)
 *  - diminishing returns inside a day:  eff = D * (1 - exp(-raw / D))  => soft daily cap ≈ D
 *  - referral bonuses are paid out of the same pie (10% L1 / 2% L2), capped per day
 *  - nothing here promises token amounts: points are shares of a fixed daily "pie" that is decided at launch
 */
const E = require('../src/engine.js');
const U = require('./users.js');

const CHECKIN = [10, 12, 15, 18, 22, 28, 40];
const RISK_MULT = { ok: 1, watch: 0.5, quarantine: 0, banned: 0 };
const r1 = (x) => Math.round(x * 10) / 10;
const r3 = (x) => Math.round(x * 1000) / 1000;

const USER_COLS = ['pts', 'pts_day', 'raw_today', 'eff_today', 'ref_today', 'ref_pts', 'streak', 'last_checkin', 'days_played', 'last_play_day', 'act',
  'wall_ref', 'seq', 'tk_total', 'risk', 'risk_hard', 'risk_at', 'risk_state', 'risk_note', 'human_until', 'challenge', 'ref_active', 'ref_active_at', 'wallet', 'wallet_ok', 'wallet_at', 'sol', 'sol_at',
  'pass_until', 'lang', 'last_seen', 'blocked', 'deleted', 'premium', 'username', 'name'];
const UPDATE_SQL = 'UPDATE users SET ' + USER_COLS.map((c) => c + '=?').join(',') + ' WHERE id=?';
function persistUser(ctx, u) {
  ctx.db.run(UPDATE_SQL, ...USER_COLS.map((c) => (u[c] === undefined ? null : u[c])), u.id);
}

function supporterUntil(ctx, uid) {
  const r = ctx.db.get('SELECT MAX(ts) AS t FROM purchases WHERE uid=?', uid);
  return r && r.t ? r.t + ctx.cfg.eco.supporterDays * U.DAY : 0;
}
const dailyD = (ctx, u, now) => ctx.cfg.eco.dailyD * (u.pass_until > now ? ctx.cfg.eco.passBoost : 1);
function rollDay(u, dk) {
  if (u.pts_day !== dk) { u.pts_day = dk; u.raw_today = 0; u.eff_today = 0; u.ref_today = 0; }
}
function ledgerAdd(ctx, uid, dk, kind, ref, raw, pts) {
  ctx.db.run(
    `INSERT INTO ledger(uid,day,kind,ref,raw,pts,n) VALUES(?,?,?,?,?,?,1)
     ON CONFLICT(uid,day,kind,ref) DO UPDATE SET raw=raw+excluded.raw, pts=pts+excluded.pts, n=n+1`,
    uid, dk, kind, ref, raw, pts
  );
}

/* ---- activity pricing ---- */
function actVector(s) {
  const t = s.stats;
  return { taps: t.taps, relW: t.relW, ups: t.ups, evs: t.evs, flrW: t.flrW, goalW: t.goalW, dqW: t.dqW };
}
function priceActivity(prev, cur) {
  const d = (k) => Math.max(0, (cur[k] || 0) - (prev[k] || 0));
  const W = E.PTS_W;
  const parts = {
    taps: d('taps') * W.tap,
    release: d('relW') * W.rel,
    upgrades: d('ups') * W.up,
    events: d('evs') * W.ev,
    floors: d('flrW'),
    goals: d('goalW'),
    daily: d('dqW'),
  };
  let raw = 0;
  for (const k in parts) raw += parts[k];
  return { raw, parts };
}

const isDev = (ctx, id) => !!(ctx.cfg.devIds && ctx.cfg.devIds.includes(Number(id)));
const DEV_CASH = 1e9, DEV_GEMS = 10000;
// developer account: top the game wallet up and keep it out of the airdrop
function devSetup(ctx, uid) {
  if (!isDev(ctx, uid)) return false;
  ctx.db.run('UPDATE users SET pts=0, ref_pts=0 WHERE id=?', uid);
  ctx.db.run('DELETE FROM ledger WHERE uid=?', uid);
  ctx.cache.lb = null; ctx.cache.ptsAll = null;
  const s = U.loadState(ctx, uid);
  if (s && (s.cash < DEV_CASH / 2 || s.gems < DEV_GEMS / 2)) {
    U.grantState(ctx, uid, { cash: Math.max(0, DEV_CASH - s.cash), gems: Math.max(0, DEV_GEMS - s.gems), bought: true, energy: true });
  }
  return true;
}

/* ---- crediting ---- */
function credit(ctx, u, kind, raw, o = {}) {
  if (!(raw > 0)) return 0;
  if (isDev(ctx, u.id)) return 0; // developer test account: no airdrop points
  const now = o.now || Date.now();
  const dk = U.dayKey(now);
  rollDay(u, dk);
  const mult = RISK_MULT[u.risk_state] === undefined ? 0 : RISK_MULT[u.risk_state];
  let eff = raw;
  if (!o.fixed) {
    const D = dailyD(ctx, u, now);
    const prev = u.raw_today, next = prev + raw;
    eff = D * (1 - Math.exp(-next / D)) - D * (1 - Math.exp(-prev / D));
    u.raw_today = next;
    u.eff_today += eff;
    if (supporterUntil(ctx, u.id) > now) eff *= ctx.cfg.eco.supporterBoost; // small thank-you for supporting the game
  }
  const pts = r3(eff * mult);
  if (pts > 0) {
    u.pts = r3(u.pts + pts);
    ledgerAdd(ctx, u.id, dk, kind, 0, raw, pts);
    if (!o.noRef) payReferrals(ctx, u, pts, now);
  }
  return pts;
}

function payReferrals(ctx, u, pts, now) {
  if (!u.ref_active) return;
  const { eco } = ctx.cfg;
  const dk = U.dayKey(now);
  for (const [lvl, rid, rate] of [[1, u.ref1, eco.refL1], [2, u.ref2, eco.refL2]]) {
    if (!rid || rid === u.id || isDev(ctx, rid)) continue;
    const r = U.getUser(ctx, rid);
    if (!r || r.deleted) continue;
    const m = RISK_MULT[r.risk_state] === undefined ? 0 : RISK_MULT[r.risk_state];
    if (m <= 0) continue;
    rollDay(r, dk);
    const room = Math.max(0, eco.refDailyCap - r.ref_today);
    const amt = r3(Math.min(pts * rate, room) * m);
    if (amt <= 0) continue;
    r.pts = r3(r.pts + amt);
    r.ref_pts = r3(r.ref_pts + amt);
    r.ref_today = r3(r.ref_today + amt);
    ctx.db.run('UPDATE users SET pts=?, ref_pts=?, ref_today=?, pts_day=?, raw_today=?, eff_today=? WHERE id=?', r.pts, r.ref_pts, r.ref_today, r.pts_day, r.raw_today, r.eff_today, r.id);
    ledgerAdd(ctx, r.id, dk, 'ref' + lvl, u.id, 0, amt);
  }
}

/* ---- daily check-in ---- */
function checkinInfo(u, now) {
  const dk = U.dayKey(now);
  const alive = u.last_checkin === dk || u.last_checkin === dk - 1;
  const streak = alive ? u.streak : 0;
  const nextStreak = u.last_checkin === dk ? streak : streak + 1;
  return { done: u.last_checkin === dk, streak, next: CHECKIN[(Math.max(1, nextStreak) - 1) % 7], table: CHECKIN };
}
function checkin(ctx, u, s, now) {
  const dk = U.dayKey(now);
  if (u.last_checkin === dk) return { ok: false, err: 'done' };
  if (s.stats.taps < 20) return { ok: false, err: 'play_first' };
  const streak = u.last_checkin === dk - 1 ? u.streak + 1 : 1;
  u.streak = streak;
  u.last_checkin = dk;
  const got = credit(ctx, u, 'checkin', CHECKIN[(streak - 1) % 7], { fixed: true, now, meta: { streak } });
  return { ok: true, pts: got, streak };
}

/* ---- referral ---- */
function friendCounts(ctx, uid) {
  const a = ctx.db.get('SELECT COUNT(*) AS n, COALESCE(SUM(ref_active),0) AS act FROM users WHERE ref1=?', uid);
  return { total: a.n, active: a.act };
}
function refLink(ctx, code) {
  const { botUsername, appShort } = ctx.cfg;
  if (!botUsername) return '';
  // the classic bot deep link: Telegram always delivers "/start ref_CODE" to the bot on every platform,
  // the bot remembers it and the Play button carries it into the game as well
  return `https://t.me/${botUsername}?start=ref_${code}`;
}
// a friend becomes "active" once they really played: released their first game. Only then the inviter is rewarded.
function checkReferralActivation(ctx, u, s, now) {
  if (u.ref_active || !u.ref1 || isDev(ctx, u.id)) return null;
  if (s.stats.released < 1 || s.stats.taps < 50 || u.risk_state !== 'ok' || u.risk >= 25) return null;
  const { eco } = ctx.cfg;
  const r = U.getUser(ctx, u.ref1);
  if (!r || r.deleted || r.risk_state === 'banned' || isDev(ctx, r.id)) return null;
  const dk = U.dayKey(now);
  u.ref_active = 1;
  u.ref_active_at = now;
  let bonus = 0;
  const todayN = ctx.db.get(`SELECT COUNT(*) AS n FROM users WHERE ref1=? AND ref_active_at>=?`, r.id, dk * U.DAY).n;
  const lifeN = ctx.db.get(`SELECT COUNT(*) AS n FROM users WHERE ref1=? AND ref_active=1`, r.id).n;
  if (todayN <= eco.refActivationsPerDay && lifeN <= eco.refActivationMax && (RISK_MULT[r.risk_state] || 0) > 0) {
    rollDay(r, dk);
    bonus = r3(eco.refActivationBonus * RISK_MULT[r.risk_state]);
    r.pts = r3(r.pts + bonus);
    r.ref_pts = r3(r.ref_pts + bonus);
    ctx.db.run('UPDATE users SET pts=?, ref_pts=? WHERE id=?', r.pts, r.ref_pts, r.id);
    ledgerAdd(ctx, r.id, dk, 'ref_act', u.id, 0, bonus);
    U.grantState(ctx, r.id, { cash: eco.inviteCash, gems: eco.inviteGems });
  }
  return { referrer: r.id, bonus };
}
function friendsList(ctx, uid) {
  const rows = ctx.db.all('SELECT id,name,username,ref_active,pts,created_at FROM users WHERE ref1=? AND deleted=0 ORDER BY created_at DESC LIMIT 60', uid);
  const earned = {};
  for (const e of ctx.db.all("SELECT ref, SUM(pts) AS p FROM ledger WHERE uid=? AND kind IN ('ref1','ref2','ref_act') GROUP BY ref", uid)) earned[e.ref] = r1(e.p);
  const l2 = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE ref2=? AND ref_active=1', uid).n;
  return {
    friends: rows.map((r) => ({ id: r.id, name: r.name, active: !!r.ref_active, earned: earned[r.id] || 0, since: r.created_at })),
    l2,
  };
}

/* ---- tasks ---- */
function taskList(ctx, u) {
  const c = ctx.cfg;
  const fr = friendCounts(ctx, u.id);
  const list = [];
  if (c.channel) list.push({ id: 'channel', pts: 40, url: c.channelUrl, verify: 'channel' });
  if (c.chatUrl) list.push({ id: 'chat', pts: 20, url: c.chatUrl, verify: 'visit' });
  if (c.xUrl) list.push({ id: 'x', pts: 20, url: c.xUrl, verify: 'visit' });
  list.push(
    { id: 'wallet', pts: 30, verify: 'auto', cur: u.sol ? 1 : 0, max: 1 },
    { id: 'play3', pts: 25, verify: 'auto', cur: Math.min(3, u.days_played), max: 3 },
    { id: 'streak3', pts: 15, verify: 'auto', cur: Math.min(3, u.streak), max: 3 },
    { id: 'streak7', pts: 50, verify: 'auto', cur: Math.min(7, u.streak), max: 7 },
    { id: 'play14', pts: 80, verify: 'auto', cur: Math.min(14, u.days_played), max: 14 },
    { id: 'invite1', pts: 20, verify: 'auto', cur: Math.min(1, fr.active), max: 1 },
    { id: 'invite3', pts: 60, verify: 'auto', cur: Math.min(3, fr.active), max: 3 },
    { id: 'invite10', pts: 200, verify: 'auto', cur: Math.min(10, fr.active), max: 10 }
  );
  return list;
}
function taskView(ctx, u, now) {
  const done = new Set(ctx.db.all('SELECT task FROM tasks_done WHERE uid=?', u.id).map((r) => r.task));
  const started = {};
  for (const r of ctx.db.all('SELECT task, ts FROM task_starts WHERE uid=?', u.id)) started[r.task] = r.ts;
  return taskList(ctx, u).map((t) => ({
    id: t.id, pts: t.pts, url: t.url || '', kind: t.verify,
    done: done.has(t.id),
    claimable: !done.has(t.id) && (t.verify === 'auto' ? t.cur >= t.max : t.verify === 'visit' ? !!started[t.id] && now - started[t.id] >= 8000 : t.verify === 'channel' ? false : true),
    started: !!started[t.id],
    cur: t.cur === undefined ? undefined : t.cur, max: t.max,
  }));
}
function taskStart(ctx, u, id, now) {
  const t = taskList(ctx, u).find((x) => x.id === id);
  if (!t || (t.verify !== 'visit' && t.verify !== 'channel')) return { ok: false, err: 'unavailable' };
  ctx.db.run('INSERT OR IGNORE INTO task_starts(uid,task,ts) VALUES(?,?,?)', u.id, id, now);
  return { ok: true };
}
async function taskClaim(ctx, uid, id, now) {
  const u = U.getUser(ctx, uid);
  if (!u) return { ok: false, err: 'unavailable' };
  const t = taskList(ctx, u).find((x) => x.id === id);
  if (!t) return { ok: false, err: 'unavailable' };
  if (ctx.db.get('SELECT 1 AS x FROM tasks_done WHERE uid=? AND task=?', uid, id)) return { ok: false, err: 'done' };
  if (t.verify === 'auto') {
    if (t.cur < t.max) return { ok: false, err: 'notyet' };
  } else if (t.verify === 'visit') {
    const st = ctx.db.get('SELECT ts FROM task_starts WHERE uid=? AND task=?', uid, id);
    if (!st || now - st.ts < 8000) return { ok: false, err: 'notyet' };
  } else if (t.verify === 'channel') {
    const r = await ctx.tg.call('getChatMember', { chat_id: ctx.cfg.channel, user_id: uid });
    const st = r && r.ok && r.result ? r.result.status : '';
    const member = ['creator', 'administrator', 'member'].includes(st) || (st === 'restricted' && r.result.is_member);
    if (!member && !(r && r.ok)) {
      // the bot cannot see the channel's members (not an admin there, wrong channel name, Telegram hiccup):
      // don't punish the player — fall back to "opened the channel and came back" like a visit task
      ctx.log && ctx.log('channel check failed:', ctx.cfg.channel, (r && r.description) || 'no response');
      ctx.cache.channelErr = { at: now, err: (r && r.description) || 'no response' };
      const s0 = ctx.db.get('SELECT ts FROM task_starts WHERE uid=? AND task=?', uid, id);
      if (!s0) { ctx.db.run('INSERT OR IGNORE INTO task_starts(uid,task,ts) VALUES(?,?,?)', uid, id, now); return { ok: false, err: 'not_member' }; }
      if (now - s0.ts < 3000) return { ok: false, err: 'not_member' };
    } else if (!member) return { ok: false, err: 'not_member' };
  }
  // commit with a FRESH user row (the awaited call above may have interleaved with a sync)
  return ctx.db.tx(() => {
    const res = ctx.db.run('INSERT OR IGNORE INTO tasks_done(uid,task,ts) VALUES(?,?,?)', uid, id, now);
    if (!res.changes) return { ok: false, err: 'done' };
    const fresh = U.getUser(ctx, uid);
    const got = credit(ctx, fresh, 'task', t.pts, { fixed: true, now, meta: { task: id } });
    persistUser(ctx, fresh);
    return { ok: true, pts: got };
  });
}

/* ---- leaderboard (cached 20 s) ---- */
function leaderboard(ctx, uid, now = Date.now()) {
  const c = ctx.cache.lb;
  let top;
  let countries;
  if (c && now - c.t < 20000) { top = c.top; countries = c.countries; }
  else {
    top = ctx.db.all("SELECT id,name,studio,username,pts,premium,country FROM users WHERE deleted=0 AND pts>0 AND risk_state IN ('ok','watch') ORDER BY pts DESC LIMIT 100");
    countries = ctx.db.all("SELECT country AS c, COUNT(*) AS n, SUM(pts) AS p FROM users WHERE deleted=0 AND pts>0 AND country IS NOT NULL AND risk_state IN ('ok','watch') GROUP BY country ORDER BY p DESC LIMIT 10");
    ctx.cache.lb = { t: now, top, countries };
  }
  const me = ctx.db.get('SELECT pts, risk_state FROM users WHERE id=?', uid);
  let rank = null;
  if (me && me.pts > 0 && (me.risk_state === 'ok' || me.risk_state === 'watch')) {
    rank = ctx.db.get("SELECT COUNT(*)+1 AS r FROM users WHERE deleted=0 AND pts>? AND risk_state IN ('ok','watch')", me.pts).r;
  }
  const total = ctx.db.get("SELECT COUNT(*) AS n FROM users WHERE deleted=0 AND pts>0 AND risk_state IN ('ok','watch')").n;
  return {
    top: top.slice(0, 50).map((r, i) => ({ rank: i + 1, name: r.studio || r.name, id: r.id === uid ? r.id : undefined, me: r.id === uid, pts: r1(r.pts), premium: !!r.premium, country: r.country || null })),
    countries: (countries || []).map((x, i) => ({ rank: i + 1, c: x.c, n: x.n, pts: r1(x.p) })),
    me: rank ? { rank, pts: r1(me.pts) } : null,
    total,
  };
}

/* ---- Telegram Stars shop ---- */
// coins are scarce: fixed packs, bigger packs give a bigger bonus, the first purchase of each pack is doubled
const SHOP = [
  { sku: 'cash1', stars: 50, coins: 2000, bonus: 0 },
  { sku: 'cash2', stars: 150, coins: 6000, bonus: 10 },
  { sku: 'cash3', stars: 500, coins: 20000, bonus: 25 },
  { sku: 'cash4', stars: 1000, coins: 40000, bonus: 40 },
  { sku: 'cash5', stars: 2500, coins: 100000, bonus: 60 },
  { sku: 'gems_s', stars: 25, gems: 15 },
  { sku: 'gems_m', stars: 100, gems: 70 },
  { sku: 'gems_l', stars: 500, gems: 400 },
  { sku: 'starter', stars: 50, gems: 40, cash: 3000, once: true },
  { sku: 'pass30', stars: 150, gems: 20, passDays: 30 },
];
const shopItem = (sku) => SHOP.find((x) => x.sku === sku) || null;
// VIP: lifetime support (Stars, SOL counted at its Stars price) raises the coin bonus on every pack — never airdrop points
const VIP = [
  { lvl: 0, stars: 0, bonus: 0 },
  { lvl: 1, stars: 100, bonus: 5 },
  { lvl: 2, stars: 500, bonus: 10 },
  { lvl: 3, stars: 1500, bonus: 15 },
  { lvl: 4, stars: 5000, bonus: 20 },
  { lvl: 5, stars: 15000, bonus: 30 },
];
function vipOf(ctx, uid) {
  const spent = ctx.db.get('SELECT COALESCE(SUM(stars),0) AS s FROM purchases WHERE uid=?', uid).s;
  let cur = VIP[0];
  for (const v of VIP) if (spent >= v.stars) cur = v;
  const next = VIP.find((v) => v.lvl === cur.lvl + 1) || null;
  return { lvl: cur.lvl, bonus: cur.bonus, spent, next: next ? { lvl: next.lvl, stars: next.stars, bonus: next.bonus, left: next.stars - spent } : null };
}
// how many coins a pack gives this player right now
function packCash(ctx, uid, it, vip, first) {
  if (!it.coins) return it.cash || 0;
  if (!vip) vip = vipOf(ctx, uid);
  if (first === undefined) first = !ctx.db.get('SELECT 1 AS x FROM purchases WHERE uid=? AND sku=?', uid, it.sku);
  return Math.round((it.coins * (1 + (it.bonus + vip.bonus) / 100) * (first ? 2 : 1)) / 10) * 10;
}
function shopView(ctx, u) {
  const bought = new Set(ctx.db.all('SELECT DISTINCT sku FROM purchases WHERE uid=?', u.id).map((r) => r.sku));
  const vip = vipOf(ctx, u.id);
  return SHOP.map((it) => ({ ...it, first: !!it.coins && !bought.has(it.sku), cash: packCash(ctx, u.id, it, vip, !bought.has(it.sku)), owned: !!it.once && bought.has(it.sku) }));
}
function canBuy(ctx, uid, sku) {
  const it = shopItem(sku);
  if (!it) return false;
  if (it.once && ctx.db.get('SELECT 1 AS x FROM purchases WHERE uid=? AND sku=?', uid, sku)) return false;
  return true;
}
// idempotent: Telegram may deliver successful_payment more than once
function fulfil(ctx, uid, sku, chargeId, stars, now = Date.now(), extra = {}) {
  const it = shopItem(sku);
  if (!it) return { ok: false, err: 'sku' };
  return ctx.db.tx(() => {
    if (ctx.db.get('SELECT 1 AS x FROM purchases WHERE charge_id=?', String(chargeId))) return { ok: true, dup: true };
    const coins = packCash(ctx, uid, it);
    const ins = ctx.db.run('INSERT OR IGNORE INTO purchases(charge_id,uid,sku,stars,ts,meta) VALUES(?,?,?,?,?,?)', String(chargeId), uid, sku, stars, now, extra.meta || null);
    if (!ins.changes) return { ok: true, dup: true };
    U.grantState(ctx, uid, { cash: coins, gems: it.gems || 0, bought: true });
    if (it.passDays) {
      const u = U.getUser(ctx, uid);
      const from = Math.max(now, u.pass_until || 0);
      ctx.db.run('UPDATE users SET pass_until=? WHERE id=?', from + it.passDays * U.DAY, uid);
    }
    const dk = U.dayKey(now);
    ctx.db.run(
      `INSERT INTO revenue(day,stars,usd,n) VALUES(?,?,?,1)
       ON CONFLICT(day) DO UPDATE SET stars=stars+excluded.stars, usd=usd+excluded.usd, n=n+1`,
      dk, extra.usd !== undefined ? 0 : stars, extra.usd !== undefined ? extra.usd : stars * ctx.cfg.starUsd
    );
    return { ok: true, item: it };
  });
}

/* ---- donations: support the road to the token launch (no items, no points — counts toward VIP & supporter) ---- */
function fulfilDonation(ctx, uid, chargeId, stars, now = Date.now(), extra = {}) {
  return ctx.db.tx(() => {
    const ins = ctx.db.run('INSERT OR IGNORE INTO purchases(charge_id,uid,sku,stars,ts,meta) VALUES(?,?,?,?,?,?)', String(chargeId), uid, 'donate', stars, now, extra.meta || null);
    if (!ins.changes) return { ok: true, dup: true };
    const usd = extra.usd !== undefined ? extra.usd : stars * ctx.cfg.starUsd;
    ctx.db.run(
      `INSERT INTO revenue(day,stars,usd,n) VALUES(?,?,?,1)
       ON CONFLICT(day) DO UPDATE SET stars=stars+excluded.stars, usd=usd+excluded.usd, n=n+1`,
      U.dayKey(now), extra.usd !== undefined ? 0 : stars, usd
    );
    ctx.cache.fund = null;
    return { ok: true, stars };
  });
}
// shared progress toward the launch fund (cached a few seconds)
function fundView(ctx, uid, now = Date.now()) {
  let f = ctx.cache.fund;
  if (!f || now - f.t > 5000) {
    const r = ctx.db.get("SELECT COALESCE(SUM(stars),0) AS s, COUNT(DISTINCT uid) AS d FROM purchases WHERE sku='donate'");
    const usd = ctx.db.get("SELECT COALESCE(SUM(CAST(json_extract(meta,'$.usd') AS REAL)),0) AS u FROM purchases WHERE sku='donate' AND meta IS NOT NULL").u;
    const starsOnly = ctx.db.get("SELECT COALESCE(SUM(stars),0) AS s FROM purchases WHERE sku='donate' AND (meta IS NULL OR json_extract(meta,'$.usd') IS NULL)").s;
    const top = ctx.db.all("SELECT p.uid, u.name, u.country, SUM(p.stars) AS s FROM purchases p JOIN users u ON u.id=p.uid WHERE p.sku='donate' AND u.deleted=0 GROUP BY p.uid ORDER BY s DESC LIMIT 10");
    f = { t: now, stars: r.s, donors: r.d, usd: Math.round((usd + starsOnly * ctx.cfg.starUsd) * 100) / 100, top };
    ctx.cache.fund = f;
  }
  const mine = uid ? ctx.db.get("SELECT COALESCE(SUM(stars),0) AS s FROM purchases WHERE sku='donate' AND uid=?", uid).s : 0;
  const d = ctx.cfg.donate;
  return { stars: f.stars, donors: f.donors, usd: f.usd, goalUsd: d.goalUsd, min: d.minStars, max: d.maxStars, mine, top: f.top.map((x, i) => ({ rank: i + 1, name: x.name, country: x.country, stars: x.s, me: x.uid === uid })) };
}

/* ---- rewarded ads ---- */
// cash reward scales with the studio's income so it stays meaningful but never dominates
function adCash(s) {
  return Math.round(Math.min(20000, Math.max(100, (s ? s.income3 : 0) * 0.3)) / 10) * 10;
}
function adInfo(ctx, uid, now) {
  const a = ctx.cfg.ads;
  if (!a.blockId) return null;
  const dk = U.dayKey(now);
  const r = ctx.db.get('SELECT COUNT(*) AS n, MAX(ts) AS last FROM ad_views WHERE uid=? AND day=?', uid, dk);
  const left = Math.max(0, a.perDay - r.n);
  return { left, max: a.perDay, next: r.last ? r.last + a.cooldownSec * 1000 : 0, pts: a.pts };
}
function adReward(ctx, uid, src, now) {
  const a = ctx.cfg.ads;
  if (!a.blockId) return { ok: false, err: 'ads_off' };
  return ctx.db.tx(() => {
    const u = U.getUser(ctx, uid);
    if (!u || u.deleted) return { ok: false, err: 'unavailable' };
    if (src !== 'client' && src !== 'cb' && ctx.db.get('SELECT 1 AS x FROM ad_views WHERE src=?', src)) return { ok: true, dup: true }; // postback retries
    const info = adInfo(ctx, uid, now);
    if (info.left <= 0) return { ok: false, err: 'ads_limit' };
    if (info.next > now) return { ok: false, err: 'ads_wait', next: info.next };
    const s = U.loadState(ctx, uid);
    const cash = adCash(s);
    ctx.db.run('INSERT INTO ad_views(uid,ts,day,src) VALUES(?,?,?,?)', uid, now, U.dayKey(now), src);
    U.grantState(ctx, uid, { cash, energy: true });
    const pts = credit(ctx, u, 'ad', a.pts, { fixed: true, now });
    persistUser(ctx, u);
    ctx.db.run(
      `INSERT INTO revenue(day,stars,usd,n) VALUES(?,0,?,0)
       ON CONFLICT(day) DO UPDATE SET usd=usd+excluded.usd`,
      U.dayKey(now), a.usdPerView
    );
    return { ok: true, cash, pts, ads: adInfo(ctx, uid, now) };
  });
}

/* ---- emission brake (used after the token launch; shown in the admin panel) ---- */
function pieSchedule(ctx, daysSinceLaunch) {
  const year = Math.floor(Math.max(0, daysSinceLaunch) / 365);
  return ctx.cfg.eco.pieYear1 / Math.pow(2, year);
}
function pieFor(ctx, daysSinceLaunch, yesterdayUsd, tokenUsd) {
  const sched = pieSchedule(ctx, daysSinceLaunch);
  const brake = tokenUsd > 0 ? (ctx.cfg.eco.pieShare * yesterdayUsd) / tokenUsd : 0;
  return { schedule: Math.round(sched), brake: Math.round(brake), pie: Math.round(Math.min(sched, brake)) };
}

/* ---- view for the client ---- */
function ecoView(ctx, u, now) {
  const dk = U.dayKey(now);
  rollDay(u, dk);
  const D = dailyD(ctx, u, now);
  const todayRows = ctx.db.all('SELECT kind, SUM(pts) AS p FROM ledger WHERE uid=? AND day=? GROUP BY kind', u.id, dk);
  const bySrc = {};
  let today = 0;
  for (const r of todayRows) { bySrc[r.kind] = r1(r.p); today += r.p; }
  const fr = friendCounts(ctx, u.id);
  const ci = checkinInfo(u, now);
  return {
    pts: r1(u.pts),
    today: r1(today),
    bySrc,
    D: Math.round(D),
    used: r3(Math.min(1, u.eff_today / D)),
    pass: u.pass_until > now ? u.pass_until : 0,
    sup: (() => { const t = supporterUntil(ctx, u.id); return t > now ? t : 0; })(),
    supBoost: ctx.cfg.eco.supporterBoost,
    ads: adInfo(ctx, u.id, now),
    checkin: ci,
    ref: { code: u.ref_code, link: refLink(ctx, u.ref_code), total: fr.total, active: fr.active, pts: r1(u.ref_pts), l1: ctx.cfg.eco.refL1, l2: ctx.cfg.eco.refL2, cashEach: ctx.cfg.eco.inviteCash, gemsEach: ctx.cfg.eco.inviteGems, ptsEach: ctx.cfg.eco.refActivationBonus, welcome: ctx.cfg.eco.welcomeCash },
    dev: isDev(ctx, u.id),
    risk: u.risk_state === 'ok' ? 0 : u.risk_state === 'watch' ? 1 : 2,
    wallet: u.wallet ? { addr: u.wallet, ok: !!u.wallet_ok } : null,
    sol: u.sol || null,
    airdrop: require('./airdrop.js').view(ctx, u),
    days: u.days_played,
    mult: RISK_MULT[u.risk_state] === undefined ? 0 : RISK_MULT[u.risk_state],
    ptsAll: ptsAll(ctx, now),
  };
}
function ptsAll(ctx, now) {
  const c = ctx.cache.ptsAll;
  if (c && now - c.t < 30000) return c.v;
  const v = r1(ctx.db.get("SELECT COALESCE(SUM(pts),0) AS s FROM users WHERE deleted=0 AND risk_state IN ('ok','watch')").s);
  ctx.cache.ptsAll = { t: now, v };
  return v;
}

module.exports = {
  isDev, devSetup,
  packCash, vipOf, VIP, fulfilDonation, fundView,
  CHECKIN, RISK_MULT, SHOP, supporterUntil, adCash, adInfo, adReward, persistUser, rollDay, dailyD, ledgerAdd, actVector, priceActivity, credit, payReferrals, checkin, checkinInfo,
  friendCounts, refLink, checkReferralActivation, friendsList, taskList, taskView, taskStart, taskClaim, leaderboard,
  shopItem, shopView, canBuy, fulfil, pieSchedule, pieFor, ecoView, r1, r3,
};
