'use strict';
/* SQLite storage (built into Node 22): users, payments, nodes. */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { DatabaseSync } = require('node:sqlite');

const DAY = 86400000;
const newToken = () => crypto.randomBytes(16).toString('base64url');
const newPass = () => crypto.randomBytes(12).toString('hex');

// Reality keys are X25519; Xray wants them as raw base64url without padding (what JWK already uses)
function realityKeys() {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('x25519');
  return { priv: privateKey.export({ format: 'jwk' }).d, pub: publicKey.export({ format: 'jwk' }).x };
}

function open(file) {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT);
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY, name TEXT DEFAULT '', lang TEXT DEFAULT 'ru', created INTEGER NOT NULL,
      expires INTEGER NOT NULL DEFAULT 0, token TEXT UNIQUE NOT NULL, uuid TEXT NOT NULL, pass TEXT NOT NULL,
      ref INTEGER DEFAULT 0, trial INTEGER DEFAULT 0, paid INTEGER DEFAULT 0, warned INTEGER DEFAULT 0, banned INTEGER DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS payments (
      charge TEXT PRIMARY KEY, uid INTEGER NOT NULL, plan TEXT NOT NULL, stars INTEGER NOT NULL, days INTEGER NOT NULL, ts INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS nodes (
      id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, host TEXT NOT NULL, secret TEXT UNIQUE NOT NULL,
      priv TEXT NOT NULL, pub TEXT NOT NULL, sid TEXT NOT NULL, sni TEXT NOT NULL,
      port INTEGER DEFAULT 443, hy2_port INTEGER DEFAULT 443, pin TEXT DEFAULT '', seen INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1
    );
  `);
  // columns added after the first release
  const cols = db.prepare('PRAGMA table_info(nodes)').all().map((c) => c.name);
  const pcols = db.prepare('PRAGMA table_info(payments)').all().map((c) => c.name);
  if (!pcols.includes('method')) db.exec("ALTER TABLE payments ADD COLUMN method TEXT DEFAULT 'stars'");
  if (!pcols.includes('usd')) db.exec('ALTER TABLE payments ADD COLUMN usd REAL DEFAULT 0');
  for (const [c, def] of [['kind', "TEXT DEFAULT 'vps'"], ['ws_path', "TEXT DEFAULT ''"], ['rhost', "TEXT DEFAULT ''"], ['rport', 'INTEGER DEFAULT 0']]) {
    if (!cols.includes(c)) db.exec(`ALTER TABLE nodes ADD COLUMN ${c} ${def}`);
  }

  const q = (sql) => db.prepare(sql);
  const s = {
    getUser: q('SELECT * FROM users WHERE id = ?'),
    byToken: q('SELECT * FROM users WHERE token = ?'),
    insUser: q('INSERT INTO users (id, name, lang, created, token, uuid, pass, ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'),
    updName: q('UPDATE users SET name = ?, lang = ? WHERE id = ?'),
    setExp: q('UPDATE users SET expires = ? WHERE id = ?'),
    active: q('SELECT id, uuid, pass FROM users WHERE expires > ? AND banned = 0 ORDER BY id'),
    insPay: q('INSERT OR IGNORE INTO payments (charge, uid, plan, stars, days, ts, method, usd) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'),
    insNode: q('INSERT INTO nodes (name, host, secret, priv, pub, sid, sni) VALUES (?, ?, ?, ?, ?, ?, ?)'),
    nodes: q('SELECT * FROM nodes ORDER BY id'),
  };

  const store = {
    db,
    user: (id) => s.getUser.get(id) || null,
    userByToken: (t) => s.byToken.get(t) || null,

    // creates the user on first contact; ref is only taken for brand-new users and never from themselves
    ensureUser(from, ref = 0, now = Date.now()) {
      const name = [from.first_name, from.last_name].filter(Boolean).join(' ').slice(0, 64) || from.username || '';
      const lang = (from.language_code || 'ru').slice(0, 2);
      let u = store.user(from.id);
      if (u) {
        if (u.name !== name || u.lang !== lang) s.updName.run(name, lang, from.id);
        return { user: store.user(from.id), created: false };
      }
      const r = ref && ref !== from.id && store.user(ref) ? ref : 0;
      s.insUser.run(from.id, name, lang, now, newToken(), crypto.randomUUID(), newPass(), r);
      return { user: store.user(from.id), created: true };
    },

    // adds days on top of what is left (or from now if expired); returns the new expiry
    extend(id, days, now = Date.now()) {
      const u = store.user(id);
      if (!u) return 0;
      const exp = Math.max(u.expires, now) + Math.round(days * DAY);
      s.setExp.run(exp, id);
      return exp;
    },

    startTrial(id, days, now = Date.now()) {
      const u = store.user(id);
      if (!u || u.trial || u.expires > now) return 0;
      db.prepare('UPDATE users SET trial = 1 WHERE id = ?').run(id);
      return store.extend(id, days, now);
    },

    // new UUID, password and link: the old ones stop working on the next node sync
    resetKey(id) {
      db.prepare('UPDATE users SET token = ?, uuid = ?, pass = ? WHERE id = ?').run(newToken(), crypto.randomUUID(), newPass(), id);
      return store.user(id);
    },

    activeUsers: (now = Date.now()) => s.active.all(now),

    // true only the first time a charge id is seen (Telegram may resend an update)
    recordPayment(charge, uid, plan, stars, days, now = Date.now(), method = 'stars', usd = 0) {
      const r = s.insPay.run(charge, uid, plan, stars, days, now, method, usd);
      if (!r.changes) return false;
      db.prepare('UPDATE users SET paid = paid + 1 WHERE id = ?').run(uid);
      return true;
    },

    addNode(name, host, sni = 'www.microsoft.com') {
      const k = realityKeys();
      const secret = crypto.randomBytes(24).toString('hex');
      const r = s.insNode.run(name, host, secret, k.priv, k.pub, crypto.randomBytes(4).toString('hex'), sni);
      return store.node(Number(r.lastInsertRowid));
    },
    // a node that runs as a Railway service (no own server): WebSocket via the Railway https domain
    // + optional Reality via a Railway TCP proxy. Found by its secret, created on first sight.
    railwayNode({ name, host, rhost = '', rport = 0, secret }) {
      let n = store.nodeBySecret(secret);
      const wsPath = '/' + crypto.createHash('sha256').update('ws:' + secret).digest('hex').slice(0, 20);
      if (!n) {
        const k = realityKeys();
        const r = db.prepare("INSERT INTO nodes (name, host, secret, priv, pub, sid, sni, kind, ws_path) VALUES (?, ?, ?, ?, ?, ?, ?, 'railway', ?)")
          .run(name, host, secret, k.priv, k.pub, crypto.randomBytes(4).toString('hex'), 'www.microsoft.com', wsPath);
        n = store.node(Number(r.lastInsertRowid));
      }
      db.prepare("UPDATE nodes SET name = ?, host = ?, rhost = ?, rport = ?, kind = 'railway', ws_path = ? WHERE id = ?")
        .run(name, host, rhost, Number(rport) || 0, wsPath, n.id);
      return store.node(n.id);
    },
    node: (id) => db.prepare('SELECT * FROM nodes WHERE id = ?').get(id) || null,
    nodeBySecret: (secret) => db.prepare('SELECT * FROM nodes WHERE secret = ?').get(secret) || null,
    nodes: () => s.nodes.all(),
    enabledNodes: () => s.nodes.all().filter((n) => n.enabled),
    setNodeEnabled: (id, on) => db.prepare('UPDATE nodes SET enabled = ? WHERE id = ?').run(on ? 1 : 0, id).changes > 0,
    touchNode(id, pin, now = Date.now()) {
      if (pin) db.prepare('UPDATE nodes SET seen = ?, pin = ? WHERE id = ?').run(now, pin, id);
      else db.prepare('UPDATE nodes SET seen = ? WHERE id = ?').run(now, id);
    },

    // users whose subscription ends within `ahead` ms and who were not warned about this expiry yet
    toWarn(now, ahead) {
      return db.prepare('SELECT * FROM users WHERE expires > ? AND expires <= ? AND warned != expires AND banned = 0').all(now, now + ahead);
    },
    markWarned: (id, exp) => db.prepare('UPDATE users SET warned = ? WHERE id = ?').run(exp, id),

    kvGet: (k) => (db.prepare('SELECT v FROM kv WHERE k = ?').get(k) || {}).v ?? null,
    kvSet: (k, v) => db.prepare('INSERT INTO kv (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v').run(k, String(v)),

    stats(now = Date.now()) {
      const one = (sql, ...a) => Object.values(db.prepare(sql).get(...a))[0] || 0;
      return {
        users: one('SELECT COUNT(*) FROM users'),
        active: one('SELECT COUNT(*) FROM users WHERE expires > ?', now),
        paying: one('SELECT COUNT(*) FROM users WHERE paid > 0'),
        new24h: one('SELECT COUNT(*) FROM users WHERE created > ?', now - DAY),
        payments: one('SELECT COUNT(*) FROM payments'),
        stars: one('SELECT SUM(stars) FROM payments'),
        stars30d: one('SELECT SUM(stars) FROM payments WHERE ts > ?', now - 30 * DAY),
        usd: Math.round(one('SELECT SUM(usd) FROM payments') * 100) / 100,
        nodes: one('SELECT COUNT(*) FROM nodes WHERE enabled = 1'),
      };
    },
  };
  return store;
}

module.exports = { open, realityKeys, DAY };
