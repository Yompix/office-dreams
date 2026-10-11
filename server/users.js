'use strict';
/* Users, game-state storage, referral capture. */
const crypto = require('crypto');
const E = require('../src/engine.js');

const DAY = 86400000;
const dayKey = (now = Date.now()) => Math.floor(now / DAY);
const sha = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');

function newRefCode(ctx) {
  for (let i = 0; i < 20; i++) {
    const c = crypto.randomBytes(6).toString('base64url').replace(/[^A-Za-z0-9]/g, '').toLowerCase().slice(0, 7);
    if (c.length >= 6 && !ctx.db.get('SELECT 1 AS x FROM users WHERE ref_code=?', c)) return c;
  }
  return crypto.randomBytes(8).toString('hex');
}
const parseRef = (sp) => {
  const m = /^ref_([A-Za-z0-9]{4,16})$/.exec(String(sp || ''));
  return m ? m[1].toLowerCase() : null;
};
const langOf = (code) => {
  const c = String(code || '').toLowerCase().slice(0, 2);
  if (c === 'uk') return 'uk';
  if (c === 'ru' || c === 'be' || c === 'kk') return 'ru';
  return 'en';
};
const hashIp = (ctx, ip) => sha(ctx.cfg.sessionSecret + '|ip|' + ip).slice(0, 16);
const hashDev = (ctx, d) => sha(ctx.cfg.sessionSecret + '|dev|' + d).slice(0, 16);

function getUser(ctx, id) {
  return ctx.db.get('SELECT * FROM users WHERE id=?', id) || null;
}
function loadState(ctx, uid) {
  const r = ctx.db.get('SELECT json FROM states WHERE uid=?', uid);
  if (!r) return null;
  try { return E.deserialize(r.json); } catch { return null; }
}
function saveState(ctx, uid, s, now = Date.now()) {
  const o = JSON.parse(JSON.stringify(s));
  delete o.sel; delete o.settings; delete o.off; // local-only preferences are never stored on the server
  ctx.db.run(
    'INSERT INTO states(uid,json,updated_at) VALUES(?,?,?) ON CONFLICT(uid) DO UPDATE SET json=excluded.json, updated_at=excluded.updated_at',
    uid, JSON.stringify(o), now
  );
}
// out-of-band changes to a player's game state (rewards, purchases)
function grantState(ctx, uid, g) {
  const s = loadState(ctx, uid);
  if (!s) return false;
  if (g.cash) { s.cash += g.cash; if (g.cash > 0) s.earned += g.cash; }
  if (g.gems) s.gems = Math.max(0, s.gems + g.gems);
  s.rev++;
  saveState(ctx, uid, s);
  return true;
}

function pendingRef(ctx, uid) {
  const r = ctx.db.get('SELECT code FROM pending_refs WHERE uid=?', uid);
  return r ? r.code : null;
}
function setPendingRef(ctx, uid, code, now = Date.now()) {
  if (getUser(ctx, uid)) return; // already registered: referral can no longer change
  ctx.db.run('INSERT INTO pending_refs(uid,code,ts) VALUES(?,?,?) ON CONFLICT(uid) DO UPDATE SET code=excluded.code, ts=excluded.ts', uid, code, now);
}

function recordFp(ctx, uid, kind, hash, now) {
  if (!hash) return;
  ctx.db.run('INSERT INTO fps(uid,kind,hash,ts) VALUES(?,?,?,?) ON CONFLICT(uid,kind,hash) DO UPDATE SET ts=excluded.ts', uid, kind, hash, now);
}

/* Create or refresh a user from validated Telegram data. */
function ensureUser(ctx, tu, o = {}) {
  const now = o.now || Date.now();
  const { db, cfg } = ctx;
  return db.tx(() => {
    let u = getUser(ctx, tu.id);
    let created = false;
    const name = String(tu.first_name || tu.username || 'Player').replace(/[\u0000-\u001f<>]/g, '').slice(0, 40);
    const username = tu.username ? String(tu.username).slice(0, 40) : null;
    if (!u) {
      created = true;
      let ref1 = null, ref2 = null, src = null;
      const code = parseRef(o.startParam) || pendingRef(ctx, tu.id);
      if (code) {
        const r = db.get('SELECT id, ref1, risk_state, deleted FROM users WHERE ref_code=?', code);
        if (r && r.id !== tu.id && !r.deleted && r.risk_state !== 'banned') { ref1 = r.id; ref2 = r.ref1 || null; src = code; }
      }
      db.run(
        `INSERT INTO users(id,username,name,lang,premium,platform,created_at,last_seen,ref_code,ref1,ref2,ref_src,wall_ref)
         VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)`,
        tu.id, username, name, langOf(tu.language_code), tu.is_premium ? 1 : 0, o.platform || '', now, now, newRefCode(ctx), ref1, ref2, src, now
      );
      const st = E.newState(crypto.randomBytes(4).readUInt32LE(0));
      if (ref1) { st.cash += cfg.eco.welcomeCash; st.rev++; }
      saveState(ctx, tu.id, st, now);
      db.run('DELETE FROM pending_refs WHERE uid=?', tu.id);
      u = getUser(ctx, tu.id);
    } else {
      db.run('UPDATE users SET username=?, name=?, premium=?, platform=COALESCE(NULLIF(?, \'\'), platform), last_seen=? WHERE id=?',
        username, name, tu.is_premium ? 1 : 0, o.platform || '', now, tu.id);
      u = getUser(ctx, tu.id);
    }
    if (o.ip) recordFp(ctx, tu.id, 'ip', hashIp(ctx, o.ip), now);
    if (o.dev) recordFp(ctx, tu.id, 'dev', hashDev(ctx, o.dev), now);
    return { user: u, created };
  });
}

module.exports = { DAY, dayKey, sha, newRefCode, parseRef, langOf, hashIp, hashDev, getUser, loadState, saveState, grantState, pendingRef, setPendingRef, recordFp, ensureUser };
