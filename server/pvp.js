'use strict';
/* Studio Battle (PvP): two studios race for 30 seconds — every tap builds your game jam entry,
 * the studio's team multiplies it. Live matchmaking between online players; when nobody is
 * searching, the opponent is a recorded run of a real player (shown as a recording) or a training bot.
 *
 * Everything is decided on the server: tap counts are capped by elapsed time, the clock is the
 * server's, rewards are granted once. State of live battles lives in memory (one instance);
 * results, trophies and recorded runs are stored in the database.
 */
const crypto = require('crypto');
const E = require('../src/engine.js');
const U = require('./users.js');
const Studio = require('./studio.js');

const P = {
  COUNTDOWN: 3500, // ms from match to the start whistle
  DUR: 30000, // fight length
  SLOT: 500, // recording resolution
  GRACE: 900, // late ticks after the whistle still count
  MAX_RATE: 15, // taps per second a human finger can sustain
  BURST: 8,
  GHOST_AFTER: 10000, // search time before a recorded opponent is offered
  DAILY: 5, // rewarded battles per day
  KEEP: 120000, // finished battles stay readable for this long
};
const LEAGUES = [0, 150, 400, 800, 1400]; // bronze, silver, gold, platinum, neon
const leagueOf = (tr) => { let i = 0; while (i + 1 < LEAGUES.length && tr >= LEAGUES[i + 1]) i++; return i; };
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const SLOTS = P.DUR / P.SLOT;

function mem(ctx) {
  if (!ctx.pvp) ctx.pvp = { queue: new Map(), battles: new Map(), byUser: new Map() };
  return ctx.pvp;
}
function ensureSchema(ctx) {
  if (ctx.pvpSchema) return;
  const db = ctx.db;
  db.raw.exec(`CREATE TABLE IF NOT EXISTS pvp_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, name TEXT, country TEXT, tr INTEGER, mult REAL, taps TEXT, n INTEGER, score INTEGER, ts INTEGER);
    CREATE INDEX IF NOT EXISTS pvp_runs_u ON pvp_runs(uid, ts);
    CREATE INDEX IF NOT EXISTS pvp_runs_t ON pvp_runs(ts);
    CREATE TABLE IF NOT EXISTS pvp_stats (uid INTEGER PRIMARY KEY, tr INTEGER DEFAULT 0, w INTEGER DEFAULT 0, l INTEGER DEFAULT 0, d INTEGER DEFAULT 0, best INTEGER DEFAULT 0, day INTEGER DEFAULT 0, n INTEGER DEFAULT 0, wins_day INTEGER DEFAULT 0, ts INTEGER DEFAULT 0);
    CREATE INDEX IF NOT EXISTS pvp_stats_tr ON pvp_stats(tr DESC);`);
  ctx.pvpSchema = true;
}
function statsOf(ctx, uid) {
  ensureSchema(ctx);
  return ctx.db.get('SELECT * FROM pvp_stats WHERE uid=?', uid) || { uid, tr: 0, w: 0, l: 0, d: 0, best: 0, day: 0, n: 0, wins_day: 0, ts: 0, fresh: true };
}
function saveStats(ctx, st) {
  ctx.db.run(`INSERT INTO pvp_stats(uid,tr,w,l,d,best,day,n,wins_day,ts) VALUES(?,?,?,?,?,?,?,?,?,?)
    ON CONFLICT(uid) DO UPDATE SET tr=excluded.tr,w=excluded.w,l=excluded.l,d=excluded.d,best=excluded.best,day=excluded.day,n=excluded.n,wins_day=excluded.wins_day,ts=excluded.ts`,
  st.uid, st.tr, st.w, st.l, st.d, st.best, st.day, st.n, st.wins_day, st.ts);
}
function rollDay(st, now) {
  const dk = U.dayKey(now);
  if (st.day !== dk) { st.day = dk; st.n = 0; st.wins_day = 0; }
}
// the team multiplies taps: a strong studio counts up to ×2
function multOf(s) {
  if (!s) return 1;
  const wp = E.wpTotal(s);
  return Math.round((1 + Math.min(1, Math.log2(1 + Math.max(0, wp)) / 6)) * 100) / 100;
}
function cashReward(s) {
  if (!s) return 150;
  return Math.round(clamp(E.tapPower(s).cash * 150, 150, 250000));
}
function side(ctx, uid) {
  const u = U.getUser(ctx, uid);
  const s = U.loadState(ctx, uid);
  const st = statsOf(ctx, uid);
  return { uid, name: Studio.shown(u), country: (u && u.country) || null, tr: st.tr, mult: multOf(s), n: 0, rec: new Array(SLOTS).fill(0), emote: null, kind: 'live' };
}

/* ---- opponents that are not online: recordings and bots ---- */
function botTimeline(seed, rate) {
  let x = seed >>> 0 || 7;
  const rnd = () => { x = (Math.imul(x, 1103515245) + 12345) >>> 0; return x / 4294967296; };
  const out = [];
  let n = 0;
  for (let i = 0; i < SLOTS; i++) {
    const t = i * P.SLOT;
    const r = t < 900 ? 0 : rate * (0.75 + rnd() * 0.5) * (i > SLOTS - 8 ? 1.15 : 1); // a slow start, a sprint at the end
    n += Math.round((r * P.SLOT) / 1000);
    out.push(n);
  }
  return out;
}
function ghostSide(ctx, uid, me, opts = {}) {
  ensureSchema(ctx);
  let run = null;
  if (opts.from) {
    run = ctx.db.get('SELECT * FROM pvp_runs WHERE uid=? ORDER BY score DESC, ts DESC LIMIT 1', opts.from);
    if (!run) return null;
  } else {
    const rows = ctx.db.all('SELECT * FROM pvp_runs WHERE uid<>? AND ts>? ORDER BY ABS(tr-?) LIMIT 25', uid, ctx.now() - 14 * U.DAY, me.tr);
    if (rows.length) run = rows[crypto.randomInt(rows.length)];
  }
  if (run) {
    let taps = [];
    try { taps = JSON.parse(run.taps); } catch (_) { taps = []; }
    if (!Array.isArray(taps) || taps.length !== SLOTS) taps = botTimeline(run.id, 6);
    return { uid: run.uid, name: run.name, country: run.country, tr: run.tr, mult: run.mult, n: 0, timeline: taps, emote: null, kind: opts.from ? 'friend' : 'rec', runId: run.id };
  }
  const seed = crypto.randomInt(1, 2 ** 31);
  const rate = 4.2 + (me.tr / 800) * 2.5 + (seed % 100) / 60; // the bot gets quicker as you climb
  return { uid: 0, name: 'Bot', country: null, tr: me.tr, mult: me.mult, n: 0, timeline: botTimeline(seed, Math.min(9.5, rate)), emote: null, kind: 'bot', seed };
}

function newBattle(ctx, a, b, now, extra = {}) {
  const m = mem(ctx);
  const id = crypto.randomBytes(5).toString('hex');
  const bt = { id, a, b, start: now + P.COUNTDOWN, end: now + P.COUNTDOWN + P.DUR, done: false, res: null, ...extra };
  m.battles.set(id, bt);
  for (const s of [a, b]) if (s.kind === 'live') { m.byUser.set(s.uid, id); m.queue.delete(s.uid); }
  return bt;
}
function tryMatch(ctx, uid, now) {
  const m = mem(ctx);
  const q = m.queue.get(uid);
  if (!q) return null;
  let best = null, bestD = Infinity;
  for (const [ouid, o] of m.queue) {
    if (ouid === uid) continue;
    const wait = Math.max(now - q.since, now - o.since) / 1000;
    const d = Math.abs(o.tr - q.tr);
    if (d <= 200 + 40 * wait && d < bestD) { best = ouid; bestD = d; }
  }
  if (best !== null) return newBattle(ctx, side(ctx, uid), side(ctx, best), now);
  if (now - q.since >= P.GHOST_AFTER) {
    const me = side(ctx, uid);
    return newBattle(ctx, me, ghostSide(ctx, uid, me), now);
  }
  return null;
}

/* ---- progress of a side at a given moment ---- */
function ghostN(sd, el) {
  if (el <= 0) return 0;
  const i = Math.min(SLOTS - 1, Math.floor(el / P.SLOT));
  const prev = i > 0 ? sd.timeline[i - 1] : 0;
  const f = Math.min(1, (el - i * P.SLOT) / P.SLOT);
  return Math.round(prev + (sd.timeline[i] - prev) * f);
}
function nOf(sd, bt, now) {
  if (sd.timeline) return ghostN(sd, Math.min(now, bt.end) - bt.start);
  return sd.n;
}
const scoreOf = (n, mult) => Math.round(n * mult * 10);

function finish(ctx, bt, now) {
  if (bt.done) return;
  bt.done = true;
  bt.a.n = nOf(bt.a, bt, bt.end); bt.b.n = nOf(bt.b, bt, bt.end);
  const sa = scoreOf(bt.a.n, bt.a.mult), sb = scoreOf(bt.b.n, bt.b.mult);
  bt.res = {};
  ctx.db.tx(() => {
    for (const [me, op, sm, so] of [[bt.a, bt.b, sa, sb], [bt.b, bt.a, sb, sa]]) {
      if (me.kind !== 'live') continue;
      const win = sm > so ? 1 : sm < so ? -1 : 0;
      const st = statsOf(ctx, me.uid);
      rollDay(st, now);
      let dTr = 0;
      if (win > 0) dTr = Math.round(clamp(20 + (op.tr - st.tr) / 20, 8, 32));
      else if (win < 0) dTr = -Math.round(clamp(12 + (st.tr - op.tr) / 25, 5, 20));
      if (me.n < 10) dTr = Math.min(dTr, 0); // walking away never wins trophies
      st.tr = Math.max(0, st.tr + dTr);
      if (win > 0) st.w++; else if (win < 0) st.l++; else st.d++;
      st.best = Math.max(st.best, sm);
      st.ts = now;
      let cash = 0, gems = 0;
      const rewarded = st.n < P.DAILY && me.n >= 10;
      if (rewarded) {
        st.n++;
        const s = U.loadState(ctx, me.uid);
        cash = Math.round(cashReward(s) * (win > 0 ? 1 : win === 0 ? 0.6 : 0.35));
        if (win > 0 && st.wins_day === 0) gems = 2; // first win of the day
        U.grantState(ctx, me.uid, { cash, gems, bought: true });
      }
      if (win > 0) st.wins_day++;
      saveStats(ctx, st);
      bt.res[me.uid] = { win, dTr, tr: st.tr, league: leagueOf(st.tr), cash, gems, rewarded, left: Math.max(0, P.DAILY - st.n), my: sm, op: so };
      // a real run becomes a recording other players may meet later
      if (me.n >= 20) {
        const u = U.getUser(ctx, me.uid);
        ctx.db.run('INSERT INTO pvp_runs(uid,name,country,tr,mult,taps,n,score,ts) VALUES(?,?,?,?,?,?,?,?,?)',
          me.uid, u ? Studio.shown(u) : me.name, (u && u.country) || null, st.tr, me.mult, JSON.stringify(me.rec), me.n, sm, now);
      }
    }
    if (Math.random() < 0.02) ctx.db.run('DELETE FROM pvp_runs WHERE id NOT IN (SELECT id FROM pvp_runs ORDER BY id DESC LIMIT 3000)');
  });
  if (bt.onFinish) { try { bt.onFinish(bt); } catch (_) { /* notifications are best-effort */ } }
}

function sweep(ctx, now = ctx.now()) {
  const m = mem(ctx);
  for (const [id, bt] of m.battles) {
    if (!bt.done && now > bt.end + P.GRACE + 4000) finish(ctx, bt, now);
    if (bt.done && now > bt.end + P.KEEP) {
      m.battles.delete(id);
      for (const s of [bt.a, bt.b]) if (s.kind === 'live' && m.byUser.get(s.uid) === id) m.byUser.delete(s.uid);
    }
  }
  for (const [uid, q] of m.queue) if (now - q.seen > 15000) m.queue.delete(uid); // closed the app while searching
}

/* ---- what the client sees ---- */
function pubSide(sd, bt, now, self) {
  const n = self ? sd.n : nOf(sd, bt, now);
  return { name: sd.kind === 'bot' ? '' : sd.name, country: sd.country, kind: sd.kind, tr: sd.tr, league: leagueOf(sd.tr), mult: sd.mult, n, score: scoreOf(n, sd.mult), e: sd.emote };
}
function view(ctx, uid, now) {
  const m = mem(ctx);
  const id = m.byUser.get(uid);
  const bt = id && m.battles.get(id);
  if (bt) {
    if (!bt.done && now > bt.end + P.GRACE) finish(ctx, bt, now);
    const me = bt.a.uid === uid && bt.a.kind === 'live' ? bt.a : bt.b;
    const op = me === bt.a ? bt.b : bt.a;
    return { ok: true, st: bt.done ? 'result' : 'battle', id: bt.id, now, start: bt.start, end: bt.end, me: pubSide(me, bt, now, true), op: pubSide(op, bt, now, false), res: bt.done ? bt.res[uid] || null : null };
  }
  const q = m.queue.get(uid);
  if (q) return { ok: true, st: 'search', now, since: q.since, ghostIn: Math.max(0, P.GHOST_AFTER - (now - q.since)) };
  return { ok: true, st: 'idle', now };
}

function find(ctx, uid, body, now, hooks = {}) {
  sweep(ctx, now);
  const m = mem(ctx);
  const cur = m.byUser.get(uid);
  const curBt = cur && m.battles.get(cur);
  if (curBt && !curBt.done) return view(ctx, uid, now);
  if (curBt) m.byUser.delete(uid);
  const duel = typeof body.duel === 'string' ? body.duel.toLowerCase().replace(/^duel_/, '') : '';
  if (duel) {
    const r = /^[a-z0-9]{4,16}$/.test(duel) ? ctx.db.get('SELECT id, name FROM users WHERE ref_code=? AND deleted=0', duel) : null;
    if (!r) return { ok: false, err: 'unavailable' };
    if (r.id === uid) return { ok: false, err: 'self' };
    const me = side(ctx, uid);
    const op = ghostSide(ctx, uid, me, { from: r.id });
    if (!op) return { ok: false, err: 'norun', name: r.name };
    const bt = newBattle(ctx, me, op, now, { duelFrom: r.id });
    if (hooks.onDuel) bt.onFinish = (b) => hooks.onDuel(b, uid, r.id);
    return view(ctx, uid, now);
  }
  if (!m.queue.has(uid)) m.queue.set(uid, { since: now, seen: now, tr: statsOf(ctx, uid).tr });
  tryMatch(ctx, uid, now);
  return view(ctx, uid, now);
}
function tick(ctx, uid, body, now) {
  const m = mem(ctx);
  const q = m.queue.get(uid);
  if (q) { q.seen = now; tryMatch(ctx, uid, now); }
  const id = m.byUser.get(uid);
  const bt = id && m.battles.get(id);
  if (bt && !bt.done && (!body.b || body.b === bt.id)) {
    const me = bt.a.uid === uid && bt.a.kind === 'live' ? bt.a : bt.b;
    const el = Math.min(now, bt.end + P.GRACE) - bt.start;
    if (el > 0 && now <= bt.end + P.GRACE) {
      const n = Math.floor(Number(body.n));
      if (Number.isFinite(n) && n > me.n) {
        const cap = Math.floor((P.MAX_RATE * Math.min(el, P.DUR)) / 1000) + P.BURST;
        me.n = Math.min(n, cap);
        const slot = Math.min(SLOTS - 1, Math.floor(Math.min(el, P.DUR - 1) / P.SLOT));
        for (let i = slot; i < SLOTS; i++) me.rec[i] = me.n;
      }
    }
    const e = Math.floor(Number(body.e));
    if (Number.isInteger(e) && e >= 0 && e < 6 && now < bt.end && (!me.emote || now - me.emote.at > 1200)) me.emote = { i: e, at: now, k: (me.emote ? me.emote.k : 0) + 1 };
    // the training bot reacts now and then
    const op = me === bt.a ? bt.b : bt.a;
    if (op.kind === 'bot' && now > bt.start && now < bt.end && (!op.emote || now - op.emote.at > 7000) && crypto.randomInt(100) < 6) op.emote = { i: crypto.randomInt(6), at: now, k: (op.emote ? op.emote.k : 0) + 1 };
  }
  return view(ctx, uid, now);
}
function cancel(ctx, uid) {
  mem(ctx).queue.delete(uid);
  return { ok: true, st: 'idle' };
}

function info(ctx, uid, now) {
  ensureSchema(ctx);
  sweep(ctx, now);
  const st = statsOf(ctx, uid);
  rollDay(st, now);
  const s = U.loadState(ctx, uid);
  const m = mem(ctx);
  const top = ctx.db.all('SELECT p.uid, p.tr, p.w, u.name, u.studio, u.country FROM pvp_stats p JOIN users u ON u.id=p.uid WHERE u.deleted=0 AND p.tr>0 ORDER BY p.tr DESC, p.w DESC LIMIT 20');
  const rank = st.tr > 0 ? ctx.db.get('SELECT COUNT(*)+1 AS r FROM pvp_stats WHERE tr>?', st.tr).r : null;
  let fighting = 0;
  for (const bt of m.battles.values()) if (!bt.done) fighting += (bt.a.kind === 'live') + (bt.b.kind === 'live');
  const hasRun = !!ctx.db.get('SELECT 1 AS x FROM pvp_runs WHERE uid=? LIMIT 1', uid);
  return {
    ok: true,
    me: { tr: st.tr, league: leagueOf(st.tr), next: LEAGUES[leagueOf(st.tr) + 1] || null, w: st.w, l: st.l, d: st.d, best: st.best, left: Math.max(0, P.DAILY - st.n), daily: P.DAILY, mult: multOf(s), reward: cashReward(s), firstWin: st.wins_day === 0, rank, hasRun },
    online: { searching: m.queue.size, fighting },
    top: top.map((r, i) => ({ rank: i + 1, name: Studio.shown(r), country: r.country, tr: r.tr, w: r.w, league: leagueOf(r.tr), me: r.uid === uid })),
    leagues: LEAGUES,
  };
}

module.exports = { P, LEAGUES, leagueOf, multOf, cashReward, find, tick, cancel, info, view, sweep, finish, botTimeline, ensureSchema, statsOf, mem };
