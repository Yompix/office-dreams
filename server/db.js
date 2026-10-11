'use strict';
/* SQLite storage (node:sqlite — built into Node 22.13+, no npm packages). */
const fs = require('fs');
const path = require('path');

let DatabaseSync;
try {
  ({ DatabaseSync } = require('node:sqlite'));
} catch (e) {
  console.error('Office Dreams needs Node.js 22.13 or newer (built-in node:sqlite). Current: ' + process.version);
  process.exit(1);
}

const SCHEMA = `
PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
PRAGMA busy_timeout=5000;
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY,
  username TEXT, name TEXT, lang TEXT DEFAULT 'en', premium INTEGER DEFAULT 0, platform TEXT,
  created_at INTEGER NOT NULL, last_seen INTEGER NOT NULL,
  ref_code TEXT UNIQUE,
  ref1 INTEGER, ref2 INTEGER, ref_src TEXT,
  ref_active INTEGER DEFAULT 0, ref_active_at INTEGER,
  risk REAL DEFAULT 0, risk_hard REAL DEFAULT 0, risk_at INTEGER DEFAULT 0, risk_state TEXT DEFAULT 'ok', risk_note TEXT, human_until INTEGER DEFAULT 0, challenge TEXT,
  wallet TEXT, wallet_ok INTEGER DEFAULT 0, wallet_at INTEGER,
  pts REAL DEFAULT 0, pts_day INTEGER DEFAULT 0, raw_today REAL DEFAULT 0, eff_today REAL DEFAULT 0, ref_today REAL DEFAULT 0,
  ref_pts REAL DEFAULT 0,
  streak INTEGER DEFAULT 0, last_checkin INTEGER DEFAULT 0,
  days_played INTEGER DEFAULT 0, last_play_day INTEGER DEFAULT 0,
  act TEXT DEFAULT '{}',
  wall_ref INTEGER DEFAULT 0, seq INTEGER DEFAULT 0, tk_total REAL DEFAULT 0,
  pass_until INTEGER DEFAULT 0, blocked INTEGER DEFAULT 0, deleted INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS users_pts ON users(pts DESC);
CREATE INDEX IF NOT EXISTS users_ref1 ON users(ref1);
CREATE INDEX IF NOT EXISTS users_risk ON users(risk_state);
CREATE TABLE IF NOT EXISTS states (uid INTEGER PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER);
CREATE TABLE IF NOT EXISTS ledger (uid INTEGER, day INTEGER, kind TEXT, ref INTEGER DEFAULT 0, raw REAL DEFAULT 0, pts REAL DEFAULT 0, n INTEGER DEFAULT 0, PRIMARY KEY (uid, day, kind, ref));
CREATE INDEX IF NOT EXISTS ledger_day ON ledger(day);
CREATE TABLE IF NOT EXISTS tasks_done (uid INTEGER, task TEXT, ts INTEGER, PRIMARY KEY (uid, task));
CREATE TABLE IF NOT EXISTS task_starts (uid INTEGER, task TEXT, ts INTEGER, PRIMARY KEY (uid, task));
CREATE TABLE IF NOT EXISTS purchases (charge_id TEXT PRIMARY KEY, uid INTEGER, sku TEXT, stars INTEGER, ts INTEGER, meta TEXT);
CREATE INDEX IF NOT EXISTS purchases_uid ON purchases(uid, ts);
CREATE TABLE IF NOT EXISTS revenue (day INTEGER PRIMARY KEY, stars INTEGER DEFAULT 0, usd REAL DEFAULT 0, n INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS risk_events (id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, ts INTEGER, kind TEXT, score REAL, meta TEXT);
CREATE INDEX IF NOT EXISTS risk_u ON risk_events(uid, id DESC);
CREATE TABLE IF NOT EXISTS fps (uid INTEGER, kind TEXT, hash TEXT, ts INTEGER, PRIMARY KEY (uid, kind, hash));
CREATE INDEX IF NOT EXISTS fps_h ON fps(kind, hash);
CREATE TABLE IF NOT EXISTS beh (uid INTEGER, day INTEGER, hours INTEGER DEFAULT 0, taps INTEGER DEFAULT 0, n INTEGER DEFAULT 0, mean REAL DEFAULT 0, m2 REAL DEFAULT 0, syncs INTEGER DEFAULT 0, actions INTEGER DEFAULT 0, secs REAL DEFAULT 0, PRIMARY KEY (uid, day));
CREATE TABLE IF NOT EXISTS pending_refs (uid INTEGER PRIMARY KEY, code TEXT, ts INTEGER);
CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT);
`;

function open(file) {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec(SCHEMA);
  const cache = new Map();
  const prep = (sql) => {
    let st = cache.get(sql);
    if (!st) { st = db.prepare(sql); cache.set(sql, st); }
    return st;
  };
  let depth = 0;
  return {
    raw: db,
    run: (sql, ...p) => prep(sql).run(...p),
    get: (sql, ...p) => prep(sql).get(...p),
    all: (sql, ...p) => prep(sql).all(...p),
    // synchronous transaction (nestable). Node is single-threaded, so a sync tx cannot interleave with another request.
    tx(fn) {
      if (depth > 0) { depth++; try { return fn(); } finally { depth--; } }
      db.exec('BEGIN IMMEDIATE');
      depth = 1;
      try {
        const r = fn();
        db.exec('COMMIT');
        return r;
      } catch (e) {
        try { db.exec('ROLLBACK'); } catch (_) { /* ignore */ }
        throw e;
      } finally {
        depth = 0;
      }
    },
    kvGet(k, d = null) { const r = prep('SELECT v FROM kv WHERE k=?').get(k); return r ? r.v : d; },
    kvSet(k, v) { prep('INSERT INTO kv(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v').run(k, String(v)); },
    close() { try { db.close(); } catch (_) { /* ignore */ } },
  };
}

module.exports = { open };
