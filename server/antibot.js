'use strict';
/* Bot / farm filtering.
 * No single signal bans anybody. Soft signals are recomputed from stored behaviour statistics
 * (so a human who changes habits recovers); hard events (clock cheating, failed challenges) decay by 5/day.
 *   total risk  <40 ok · 40–69 watch (points x0.5 + human check) · 70–99 quarantine (points held) · >=100 banned
 * Admin decisions are sticky (risk_note starts with "admin").
 */
const crypto = require('crypto');
const U = require('./users.js');
const Eco = require('./economy.js');

const popcount = (n) => { let c = 0; while (n) { c += n & 1; n >>>= 1; } return c; };
const stateOf = (risk) => (risk >= 100 ? 'banned' : risk >= 70 ? 'quarantine' : risk >= 40 ? 'watch' : 'ok');
const isAdminLocked = (u) => !!(u.risk_note && String(u.risk_note).startsWith('admin'));

/* ---- behaviour statistics (updated on every sync) ---- */
function noteBatch(ctx, u, info, now) {
  const dk = U.dayKey(now);
  const hour = new Date(now).getUTCHours();
  let row = ctx.db.get('SELECT * FROM beh WHERE uid=? AND day=?', u.id, dk);
  if (!row) row = { uid: u.id, day: dk, hours: 0, taps: 0, n: 0, mean: 0, m2: 0, syncs: 0, actions: 0, secs: 0 };
  // statistics of tap-attempt intervals reported by the client -> Chan merge
  const tp = info.tp;
  if (tp && tp.n > 0) {
    const n = row.n + tp.n;
    const delta = tp.mean - row.mean;
    row.mean = row.mean + (delta * tp.n) / n;
    row.m2 = row.m2 + tp.m2 + (delta * delta * row.n * tp.n) / n;
    row.n = n;
  }
  row.taps += info.taps || 0;
  row.actions += info.actions || 0;
  row.syncs += 1;
  row.secs += info.secs || 0;
  if ((info.taps || 0) + (info.actions || 0) > 0) row.hours |= 1 << hour;
  ctx.db.run(
    `INSERT INTO beh(uid,day,hours,taps,n,mean,m2,syncs,actions,secs) VALUES(?,?,?,?,?,?,?,?,?,?)
     ON CONFLICT(uid,day) DO UPDATE SET hours=excluded.hours,taps=excluded.taps,n=excluded.n,mean=excluded.mean,m2=excluded.m2,syncs=excluded.syncs,actions=excluded.actions,secs=excluded.secs`,
    row.uid, row.day, row.hours, row.taps, row.n, row.mean, row.m2, row.syncs, row.actions, row.secs
  );
}

/* ---- signals ---- */
function signalsFor(ctx, u, now) {
  const dk = U.dayKey(now);
  const sig = {};
  const rows = ctx.db.all('SELECT * FROM beh WHERE uid=? AND day>=? ORDER BY day DESC', u.id, dk - 2);

  // 1. machine-like tapping rhythm (coefficient of variation of inter-tap time)
  let n = 0, mean = 0, m2 = 0;
  for (const r of rows) {
    if (!r.n) continue;
    const nn = n + r.n, delta = r.mean - mean;
    mean += (delta * r.n) / nn;
    m2 += r.m2 + (delta * delta * n * r.n) / nn;
    n = nn;
  }
  if (n >= 400 && mean > 0) {
    const cv = Math.sqrt(m2 / n) / mean;
    if (cv < 0.10) sig.tap_regular = 45;
    else if (cv < 0.18) sig.tap_regular = 20;
  }
  // 2. no sleep: active in (almost) every hour of the day, repeatedly
  const full = rows.filter((r) => popcount(r.hours) >= 22).length;
  const big = rows.filter((r) => popcount(r.hours) >= 18).length;
  if (full >= 2) sig.coverage24 = 40;
  else if (big >= 2) sig.long_days = 15;
  // 3. only taps, never plays the game
  const today = rows.find((r) => r.day === dk);
  if (today && today.taps >= 1500 && today.actions <= 1) sig.only_taps = 15;
  // a real client reports every tap attempt; a script that only posts action logs does not
  if (today && today.taps >= 300 && today.n < today.taps * 0.3) sig.no_tap_stats = 15;

  // 4. device / network clusters
  const dev = ctx.db.get(
    "SELECT COUNT(DISTINCT b.uid) AS n FROM fps a JOIN fps b ON b.kind=a.kind AND b.hash=a.hash AND b.uid!=a.uid WHERE a.uid=? AND a.kind='dev'", u.id
  ).n;
  const both = ctx.db.get(
    `SELECT COUNT(DISTINCT d.uid) AS n FROM fps a
       JOIN fps d ON d.kind='dev' AND d.hash=a.hash AND d.uid!=a.uid
       JOIN fps i2 ON i2.uid=d.uid AND i2.kind='ip'
       JOIN fps i1 ON i1.uid=a.uid AND i1.kind='ip' AND i1.hash=i2.hash
     WHERE a.uid=? AND a.kind='dev'`, u.id
  ).n;
  const ip = ctx.db.get(
    "SELECT COUNT(DISTINCT b.uid) AS n FROM fps a JOIN fps b ON b.kind=a.kind AND b.hash=a.hash AND b.uid!=a.uid WHERE a.uid=? AND a.kind='ip'", u.id
  ).n;
  if (both >= 6) sig.device_cluster = 50;
  else if (both >= 3) sig.device_cluster = 30;
  else if (dev >= 5) sig.device_shared = 20;
  if (ip >= 25) sig.ip_crowd = 10;

  // 5. referral farms
  if (u.ref1) {
    const sib = ctx.db.get(
      `SELECT COUNT(DISTINCT x.id) AS n FROM users x
         JOIN fps b ON b.uid=x.id JOIN fps a ON a.kind=b.kind AND a.hash=b.hash AND a.uid=?
       WHERE x.ref1=? AND x.id!=? AND a.kind IN ('dev','ip')`, u.id, u.ref1, u.id
    ).n;
    if (sib >= 8) sig.ref_cluster = 60;
    else if (sib >= 3) sig.ref_cluster = 35;
  }
  const kids = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE ref1=?', u.id).n;
  if (kids >= 5) {
    const farm = ctx.db.get(
      `SELECT COUNT(DISTINCT x.id) AS n FROM users x
         JOIN fps xa ON xa.uid=x.id AND xa.kind IN ('dev','ip')
         JOIN fps ya ON ya.kind=xa.kind AND ya.hash=xa.hash AND ya.uid!=x.id
         JOIN users y ON y.id=ya.uid AND y.ref1=?
       WHERE x.ref1=?`, u.id, u.id
    ).n;
    if (farm >= 5) sig.ref_farm = 40;
    // signups in bursts
    const burst = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE ref1=? AND created_at>?', u.id, now - 3600e3).n;
    if (burst >= 15) sig.ref_burst = 25;
  }

  // 6. account quality (weak, protective for Premium)
  if (u.premium) sig.premium = -10;
  else {
    if (!u.username) sig.no_username = 4;
  }
  if (!u.platform || u.platform === 'unknown') sig.platform = 8;
  return sig;
}

/* ---- risk bookkeeping ---- */
function logRisk(ctx, uid, kind, score, meta, now) {
  ctx.db.run('INSERT INTO risk_events(uid,ts,kind,score,meta) VALUES(?,?,?,?,?)', uid, now, kind, score, meta ? JSON.stringify(meta).slice(0, 500) : null);
}
function addHard(ctx, u, w, kind, meta, now) {
  if (isAdminLocked(u)) return;
  u.risk_hard = Math.max(0, Math.min(120, (u.risk_hard || 0) + w));
  logRisk(ctx, u.id, kind, w, meta, now);
}
// re-computes the score; u is mutated, caller persists
function evaluate(ctx, u, now) {
  if (isAdminLocked(u)) return u;
  const decayDays = Math.max(0, (now - (u.risk_at || now)) / U.DAY);
  u.risk_hard = Math.max(0, (u.risk_hard || 0) - 5 * decayDays);
  const sig = signalsFor(ctx, u, now);
  let soft = 0;
  for (const k in sig) soft += sig[k];
  soft = Math.max(0, soft);
  if (u.human_until > now) soft *= 0.5; // passed a human check recently
  const total = Math.max(0, Math.min(150, soft + u.risk_hard));
  const next = stateOf(total);
  if (next !== u.risk_state) {
    logRisk(ctx, u.id, 'state', total, { from: u.risk_state, to: next, sig }, now);
    u.risk_state = next;
  }
  u.risk = Math.round(total * 10) / 10;
  u.risk_at = now;
  u.risk_note = Object.keys(sig).filter((k) => sig[k] > 0).join(',').slice(0, 200);
  return u;
}

/* ---- human check ---- */
const POOLS = [['🍎', '🍏'], ['🐶', '🐱'], ['🌕', '🌑'], ['⭐', '🌟'], ['🚗', '🚕'], ['🔵', '🟣'], ['🍋', '🍊'], ['🎮', '🎲']];
const rint = (n) => crypto.randomInt(n);
function makeChallenge(ctx, u, now) {
  const id = crypto.randomBytes(5).toString('hex');
  let ch;
  if (rint(2) === 0) {
    const [a, b] = POOLS[rint(POOLS.length)];
    const odd = rint(12);
    const cells = Array.from({ length: 12 }, (_, i) => (i === odd ? b : a));
    ch = { id, kind: 'odd', cells, ans: odd };
  } else {
    const [a, b] = POOLS[rint(POOLS.length)];
    const total = 8 + rint(7);
    const k = 3 + rint(6);
    const idx = new Set();
    while (idx.size < k) idx.add(rint(total));
    const cells = Array.from({ length: total }, (_, i) => (idx.has(i) ? a : b));
    const opts = new Set([k]);
    while (opts.size < 4) opts.add(Math.max(1, k + rint(7) - 3));
    ch = { id, kind: 'count', target: a, cells, options: [...opts].sort((x, y) => x - y), ans: k };
  }
  const pub = { id: ch.id, kind: ch.kind, cells: ch.cells };
  if (ch.target) pub.target = ch.target;
  if (ch.options) pub.options = ch.options;
  if (ctx.cfg.turnstile.site) pub.turnstile = ctx.cfg.turnstile.site;
  u.challenge = JSON.stringify({ id, ans: ch.ans, exp: now + 10 * 60e3, fails: 0, pub });
  return pub;
}
function pendingChallenge(u, now) {
  if (!u.challenge) return null;
  try { const c = JSON.parse(u.challenge); return c.exp > now ? c : null; } catch { return null; }
}
async function verifyTurnstile(ctx, token, ip) {
  if (!ctx.cfg.turnstile.secret) return true;
  try {
    const body = new URLSearchParams({ secret: ctx.cfg.turnstile.secret, response: String(token || ''), remoteip: ip || '' });
    const r = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', { method: 'POST', body });
    const j = await r.json();
    return !!j.success;
  } catch { return false; }
}
async function answerChallenge(ctx, uid, id, ans, extra, now) {
  const u0 = U.getUser(ctx, uid);
  const c0 = u0 && pendingChallenge(u0, now);
  if (!c0 || c0.id !== id) return { ok: false, err: 'expired' };
  let good = Number(ans) === Number(c0.ans);
  if (good && ctx.cfg.turnstile.secret) good = await verifyTurnstile(ctx, extra && extra.ts, extra && extra.ip);
  // apply on a FRESH row (the await above may have interleaved with a sync)
  return ctx.db.tx(() => {
    const u = U.getUser(ctx, uid);
    const c = pendingChallenge(u, now);
    if (!c || c.id !== id) return { ok: false, err: 'expired' };
    let out;
    if (good) {
      u.challenge = null;
      u.human_until = now + 24 * 3600e3;
      addHard(ctx, u, -20, 'challenge_ok', null, now);
      out = { ok: true };
    } else {
      c.fails++;
      addHard(ctx, u, c.fails >= 3 ? 25 : 8, 'challenge_fail', { fails: c.fails }, now);
      u.challenge = null;
      out = { ok: false, err: 'wrong' };
    }
    evaluate(ctx, u, now);
    Eco.persistUser(ctx, u);
    out.eco = Eco.ecoView(ctx, u, now);
    return out;
  });
}
// returns a challenge to show (or null) — called from sync
function maybeChallenge(ctx, u, now) {
  const pend = pendingChallenge(u, now);
  if (pend) return pend.pub || null; // still unanswered: keep showing the same one
  if (u.human_until > now) return null;
  const watch = u.risk_state === 'watch';
  const sample = u.days_played >= 1 && Math.random() < 0.0008;
  if (!watch && !sample) return null;
  return makeChallenge(ctx, u, now);
}

/* ---- periodic sweep over recently active accounts ---- */
function sweep(ctx, now = Date.now(), limit = 1500) {
  const rows = ctx.db.all('SELECT * FROM users WHERE deleted=0 AND last_seen>? ORDER BY risk_at ASC LIMIT ?', now - 2 * 3600e3, limit);
  let changed = 0;
  for (const u of rows) {
    const before = u.risk_state;
    evaluate(ctx, u, now);
    Eco.persistUser(ctx, u);
    if (u.risk_state !== before) changed++;
  }
  return { checked: rows.length, changed };
}

module.exports = { stateOf, isAdminLocked, noteBatch, signalsFor, evaluate, addHard, logRisk, makeChallenge, pendingChallenge, answerChallenge, maybeChallenge, sweep };
