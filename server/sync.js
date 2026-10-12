'use strict';
/* Authoritative game sync: the client sends its action log, the server replays it on the stored state
 * with the very same engine, prices the verified progress into airdrop points and returns the result.
 *
 * Protocol (POST /api/sync):
 *   { seq, batch: [[dTicks, 'tap'|'hire'|..., ...args], ...], tail: idleTicksAfterLastAction, ck: clientStateHash }
 * Time is never trusted: the number of ticks the client may claim is bounded by the server wall clock.
 */
const E = require('../src/engine.js');
const U = require('./users.js');
const Eco = require('./economy.js');
const AB = require('./antibot.js');

const TICK_MS = E.TICK * 1000;
const MAX_ACTIONS = 600;
const MAX_TICKS = 24000; // 100 min per batch
const SLACK_MS = 12000;
const OFFLINE_MIN_MS = 30000;
const ACTION_NAMES = new Set(['tap', 'hire', 'ups', 'upy', 'buy', 'flr', 'bst', 'new', 'auto', 'clm', 'dly', 'ev', 'ipo', 'idea', 'bug', 'reno']);

function cleanTp(tp) {
  // client-reported statistics of ALL tap attempts in this batch: [count, mean ms, M2]
  if (!Array.isArray(tp) || tp.length !== 3) return null;
  const [n, mean, m2] = tp;
  if (!Number.isInteger(n) || n < 0 || n > 20000) return null;
  if (!Number.isFinite(mean) || mean < 0 || mean > 5000 || !Number.isFinite(m2) || m2 < 0 || m2 > 1e12) return null;
  return n > 0 ? { n, mean, m2 } : null;
}
function sanitize(batch, tail) {
  if (batch === undefined) batch = [];
  if (!Array.isArray(batch) || batch.length > MAX_ACTIONS) return { ok: false, err: 'batch' };
  const t = tail === undefined ? 0 : tail;
  if (!Number.isInteger(t) || t < 0 || t > MAX_TICKS) return { ok: false, err: 'tail' };
  let ticks = t;
  const out = [];
  for (const e of batch) {
    if (!Array.isArray(e) || e.length < 2 || e.length > 6) return { ok: false, err: 'entry' };
    const d = e[0];
    if (!Number.isInteger(d) || d < 0 || d > MAX_TICKS) return { ok: false, err: 'd' };
    if (typeof e[1] !== 'string' || !ACTION_NAMES.has(e[1])) return { ok: false, err: 'action' };
    for (let i = 2; i < e.length; i++) {
      const a = e[i];
      if (!(typeof a === 'number' || typeof a === 'boolean' || (typeof a === 'string' && a.length <= 40))) return { ok: false, err: 'arg' };
    }
    ticks += d;
    if (ticks > MAX_TICKS * 2) return { ok: false, err: 'ticks' };
    out.push(e);
  }
  return { ok: true, batch: out, tail: t, ticks };
}

// statistics about taps for the anti-bot module
function tapInfo(batch) {
  const intervals = [];
  let taps = 0, actions = 0, tk = 0, prev = null;
  for (const e of batch) {
    tk += e[0];
    if (e[1] === 'tap') {
      taps++;
      const ms = Number.isInteger(e[2]) && e[2] >= 0 && e[2] < TICK_MS ? e[2] : 0;
      const at = tk * TICK_MS + ms;
      if (prev !== null) {
        const dt = at - prev;
        if (dt >= 15 && dt <= 5000) intervals.push(dt);
      }
      prev = at;
    } else actions++;
  }
  return { taps, actions, intervals };
}

function view(ctx, u, s, now, extra = {}) {
  return Object.assign({
    ok: true,
    seq: u.seq,
    hash: E.stateHash(s),
    eco: Eco.ecoView(ctx, u, now),
    srv: { now, dk: U.dayKey(now) },
  }, extra);
}

function publicState(s) {
  const o = JSON.parse(JSON.stringify(s));
  delete o.off;
  return o;
}

function doSync(ctx, uid, body, now = Date.now()) {
  return ctx.db.tx(() => {
    const u = U.getUser(ctx, uid);
    if (!u || u.deleted) return { status: 404, json: { ok: false, err: 'nouser' } };
    let s = U.loadState(ctx, uid);
    if (!s) s = E.newState(require('crypto').randomBytes(4).readUInt32LE(0));
    body = body || {};
    const dk = U.dayKey(now);

    // another device / lost response / double submit
    if (body.seq !== undefined && Number(body.seq) !== u.seq) {
      E.syncDay(s, dk);
      return { status: 200, json: view(ctx, u, s, now, { ok: false, err: 'stale', state: publicState(s) }) };
    }
    const clean = sanitize(body.batch, body.tail);
    if (!clean.ok) return { status: 400, json: { ok: false, err: 'bad_' + clean.err } };

    E.syncDay(s, dk);
    const gap = now - u.wall_ref;
    const rep = clean.ticks * TICK_MS;
    if (rep > gap + SLACK_MS) {
      // the client claims more play time than has really passed
      AB.addHard(ctx, u, 6, 'clock', { claimedMs: rep, gapMs: gap }, now);
      AB.evaluate(ctx, u, now);
      Eco.persistUser(ctx, u);
      return { status: 200, json: view(ctx, u, s, now, { ok: false, err: 'clock', state: publicState(s) }) };
    }
    // time away from the game => capped passive income (no events, no mood penalties)
    const idle = gap - rep;
    let offlineSec = 0;
    if (idle > OFFLINE_MIN_MS) {
      offlineSec = E.advanceOffline(s, idle / 1000);
      u.wall_ref = now - rep;
    }

    const fails = E.replay(s, clean.batch, clean.tail);
    u.wall_ref += rep;
    if (u.wall_ref > now) u.wall_ref = now;

    // price verified progress
    const cur = Eco.actVector(s);
    let prev;
    try { prev = JSON.parse(u.act || '{}'); } catch { prev = {}; }
    const pr = Eco.priceActivity(prev, cur);
    Eco.rollDay(u, dk);
    if (u.last_play_day !== dk && (clean.ticks > 0 || clean.batch.length > 0)) { u.days_played++; u.last_play_day = dk; }
    let gain = 0;
    if (pr.raw > 0) gain = Eco.credit(ctx, u, 'play', pr.raw, { now });
    u.act = JSON.stringify(cur);

    // behaviour + risk
    const ti = tapInfo(clean.batch);
    AB.noteBatch(ctx, u, { taps: ti.taps, actions: ti.actions, tp: cleanTp(body.tp), secs: rep / 1000 }, now);
    // invited players about to unlock the inviter's bonus get a fresh risk check first (no stale "ok")
    const aboutToActivate = u.ref1 && !u.ref_active && s.stats.released >= 1;
    if (aboutToActivate || now - (u.risk_at || 0) > 5 * 60e3) AB.evaluate(ctx, u, now);
    const refAct = Eco.checkReferralActivation(ctx, u, s, now);
    if (refAct && refAct.bonus > 0) require('./bot.js').notifyRefActive(ctx, refAct.referrer, u.name, refAct.bonus).catch(() => {});
    const challenge = AB.maybeChallenge(ctx, u, now);

    u.seq += 1;
    u.tk_total += clean.ticks;
    u.last_seen = now;
    Eco.persistUser(ctx, u);
    U.saveState(ctx, uid, s, now);

    const hash = E.stateHash(s);
    const same = !!body.ck && body.ck === hash && offlineSec === 0 && u.seq % 6 !== 0 && !body.full;
    return {
      status: 200,
      json: view(ctx, u, s, now, {
        same,
        state: same ? undefined : publicState(s),
        gain: Eco.r1(gain),
        off: offlineSec,
        fails,
        challenge: challenge || undefined,
        refAct: refAct ? true : undefined,
      }),
    };
  });
}

module.exports = { cleanTp, sanitize, tapInfo, doSync, view, publicState, MAX_TICKS, SLACK_MS };
