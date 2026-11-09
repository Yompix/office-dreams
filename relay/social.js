'use strict';
// LANTERN: профили без паролей, друзья, чаты, публичные комнаты, «Фонарь» (часы игры вместе).
// Хранилище — SQLite (node:sqlite, встроен в Node 22). Путь — DB_PATH (на Railway — том, см. README).
// Всё общение — те же WebSocket-соединения /ws (команда {"t":"hello"} открывает социальную сессию).

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const _emit = process.emitWarning;
process.emitWarning = (w, ...a) => (String(w).includes('SQLite') ? undefined : _emit.call(process, w, ...a));
let DatabaseSync = null;
try { ({ DatabaseSync } = require('node:sqlite')); } catch { /* старый Node: работаем в памяти */ }

const LIMITS = {
  nickMin: 3, nickMax: 16, msgMax: 300, chatKeep: 100, chatTtlMs: 24 * 3600_000,
  msgPerMin: 20, msgBurst: 5, friendReqPerHour: 30, friendsMax: 200, reportsPerHour: 10,
  titleMax: 40, tagsMax: 4,
};
const ROOM_TAGS = ['выживание', 'творчество', 'pvp', 'мини-игры', 'новоград', 'приключения', 'моды'];
// Пороги «Фонаря» в часах игры вместе → уровни 1..9 (цвет пламени — на клиенте)
const LANTERN_HOURS = [0, 1, 3, 6, 10, 20, 40, 70, 100];

// ---------------------------------------------------------------- фильтр слов
// Корни нецензурной лексики (рус/укр/англ) с учётом замен букв. Совпадение → слово заменяется звёздочками.
const BAD_ROOTS = ['хуй', 'хуе', 'хуё', 'хую', 'хуя', 'пизд', 'пезд', 'ебат', 'ебан', 'ебал', 'ебуч', 'ебну', 'еблан', 'заеб', 'уеб', 'выеб',
  'бляд', 'блят', 'бля', 'сука', 'суки', 'сучк', 'мудак', 'мудил', 'гандон', 'пидор', 'пидар', 'педик', 'шлюх', 'залуп', 'дроч',
  'fuck', 'shit', 'bitch', 'cunt', 'dick', 'nigg', 'fag', 'whore', 'slut', 'porn', 'порн', 'секс', 'sex', 'нацист', 'nazi', 'hitler', 'гитлер'];
const LEET = { '0': 'о', '3': 'з', '4': 'ч', '6': 'б', '@': 'а', '$': 's', 'a': 'а', 'e': 'е', 'o': 'о', 'p': 'р', 'c': 'с', 'x': 'х', 'y': 'у', 'k': 'к', 'm': 'м', 'b': 'в', 'h': 'н', 't': 'т' };
function skeleton(s) {
  // латиница-двойники → кириллица, убрать разделители и повторы
  let out = '';
  for (const ch of s.toLowerCase().replace(/ё/g, 'е')) out += LEET[ch] || ch;
  return out.replace(/[^a-zа-яіїєґ]/g, '').replace(/(.)\1+/g, '$1');
}
const BAD_SKEL = BAD_ROOTS.map((r) => skeleton(r));
function isBad(word) {
  const raw = word.toLowerCase();
  const sk = skeleton(word);
  return BAD_ROOTS.some((r) => raw.includes(r)) || BAD_SKEL.some((r) => r.length >= 3 && sk.includes(r));
}
function cleanText(text) {
  let t = String(text || '').replace(/[\u0000-\u0008\u000b-\u001f\u007f​-‏‪-‮⁦-⁩]/g, '').trim();
  t = t.replace(/\s{3,}/g, '  ').slice(0, LIMITS.msgMax);
  // ссылки не пропускаем совсем
  t = t.replace(/\b((https?|ftp):\/\/|www\.)\S+/gi, '[ссылка]').replace(/\b[\w-]+\.(ru|com|net|org|gg|io|me|xyz|su|ua|рф|tk|ly|link|site|online|shop)\b\S*/gi, '[ссылка]');
  t = t.replace(/(t\.me|discord\.gg|vk\.com)\/\S*/gi, '[ссылка]');
  t = t.split(/(\s+)/).map((w) => (w.trim() && isBad(w) ? '*'.repeat(Math.min(w.length, 8)) : w)).join('');
  return t;
}
function validNick(n) {
  const s = String(n || '').trim();
  if (s.length < LIMITS.nickMin || s.length > LIMITS.nickMax) return 'Ник — от 3 до 16 символов';
  if (!/^[A-Za-zА-Яа-яЁёІіЇїЄєҐґ0-9_]+$/.test(s)) return 'Только буквы, цифры и «_»';
  if (isBad(s)) return 'Такой ник нельзя';
  if (/^(admin|админ|moder|модер|yompix|lantern|support|поддержка)/i.test(s)) return 'Этот ник занят';
  return '';
}

// ---------------------------------------------------------------- хранилище
class Store {
  constructor(file) {
    this.mem = !DatabaseSync || file === ':memory:';
    if (DatabaseSync) {
      if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
      this.db = new DatabaseSync(file);
      this.db.exec(`
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, token_hash TEXT UNIQUE, nick TEXT, num INTEGER, created INTEGER, last_seen INTEGER, minutes INTEGER DEFAULT 0, banned INTEGER DEFAULT 0);
        CREATE UNIQUE INDEX IF NOT EXISTS users_tag ON users(nick COLLATE NOCASE, num);
        CREATE TABLE IF NOT EXISTS friends(a INTEGER, b INTEGER, since INTEGER, PRIMARY KEY(a,b));
        CREATE TABLE IF NOT EXISTS requests(src INTEGER, dst INTEGER, created INTEGER, PRIMARY KEY(src,dst));
        CREATE TABLE IF NOT EXISTS blocks(src INTEGER, dst INTEGER, PRIMARY KEY(src,dst));
        CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY, chat TEXT, src INTEGER, text TEXT, ts INTEGER);
        CREATE INDEX IF NOT EXISTS messages_chat ON messages(chat, ts);
        CREATE TABLE IF NOT EXISTS reports(id INTEGER PRIMARY KEY, src INTEGER, dst INTEGER, reason TEXT, msg TEXT, ts INTEGER);
      `);
    }
  }
  q(sql) { return this.db.prepare(sql); }
}

function hashToken(t) { return crypto.createHash('sha256').update('lantern:' + t).digest('hex'); }

function lanternLevel(minutes) {
  const h = minutes / 60;
  let lvl = 1;
  for (let i = 0; i < LANTERN_HOURS.length; i++) if (h >= LANTERN_HOURS[i]) lvl = i + 1;
  const next = LANTERN_HOURS[lvl] !== undefined ? LANTERN_HOURS[lvl] * 60 : null;
  return { level: lvl, minutes, next };
}

// ---------------------------------------------------------------- социальная часть
class Social {
  constructor(opts = {}) {
    this.store = new Store(opts.dbPath || process.env.DB_PATH || ':memory:');
    this.sessions = new Map();   // userId -> Set(ws)
    this.presence = new Map();   // userId -> {activity, world, edition, version, code, ts}
    this.roomChats = new Map();  // room code -> [{id, from, nick, text, ts}]
    this.rate = new Map();       // userId -> {tokens, ts, reqs:[...], reports:[...]}
    this.nextMsg = 1;
    setInterval(() => this.cleanup(), 10 * 60_000).unref();
  }

  user(id) { return this.store.q('SELECT * FROM users WHERE id=?').get(id); }
  pub(u) {
    if (!u) return null;
    return { id: u.id, nick: u.nick, tag: `${u.nick}#${String(u.num).padStart(4, '0')}`, lantern: lanternLevel(u.xp || 0), title: u.title || '', equip: u.equip || '', online: this.sessions.has(u.id) };
  }
  userByToken(token) { return token ? this.store.q('SELECT * FROM users WHERE token_hash=?').get(hashToken(String(token))) : null; }

  send(ws, o) { if (ws && ws.readyState === 1) ws.send(JSON.stringify(o)); }
  push(uid, o) { for (const ws of this.sessions.get(uid) || []) this.send(ws, o); }
  err(ws, t, error) { this.send(ws, { t: t + '_err', error }); }

  limited(uid, kind) {
    const now = Date.now();
    let r = this.rate.get(uid);
    if (!r) { r = { tokens: LIMITS.msgBurst, ts: now, reqs: [], reports: [] }; this.rate.set(uid, r); }
    if (kind === 'msg') {
      r.tokens = Math.min(LIMITS.msgBurst, r.tokens + (now - r.ts) * LIMITS.msgPerMin / 60000);
      r.ts = now;
      if (r.tokens < 1) return true;
      r.tokens -= 1;
      return false;
    }
    const list = kind === 'req' ? r.reqs : r.reports;
    while (list.length && now - list[0] > 3600_000) list.shift();
    if (list.length >= (kind === 'req' ? LIMITS.friendReqPerHour : LIMITS.reportsPerHour)) return true;
    list.push(now);
    return false;
  }

  friendsOf(uid) {
    return this.store.q('SELECT b AS id FROM friends WHERE a=?').all(uid).map((r) => r.id);
  }
  areFriends(a, b) { return !!this.store.q('SELECT 1 FROM friends WHERE a=? AND b=?').get(a, b); }
  blocked(a, b) { return !!this.store.q('SELECT 1 FROM blocks WHERE (src=? AND dst=?) OR (src=? AND dst=?)').get(a, b, b, a); }

  // ---- сессия
  handle(ws, msg) {
    const t = msg.t;
    if (t === 'hello') return this.hello(ws, msg);
    if (t === 'register') return this.register(ws, msg);
    if (t === 'rooms') return this.send(ws, { t: 'rooms', rooms: this.publicRooms(msg) });
    const uid = ws._uid;
    if (!uid) return this.send(ws, { t: 'error', error: 'need_hello' });
    switch (t) {
      case 'presence': return this.setPresence(uid, msg);
      case 'friend_add': return this.friendAdd(ws, uid, msg);
      case 'friend_accept': return this.friendAccept(ws, uid, msg.id >>> 0);
      case 'friend_decline': this.store.q('DELETE FROM requests WHERE src=? AND dst=?').run(msg.id >>> 0, uid); return this.sendState(uid);
      case 'friend_remove': return this.friendRemove(uid, msg.id >>> 0);
      case 'block': return this.block(uid, msg.id >>> 0);
      case 'unblock': this.store.q('DELETE FROM blocks WHERE src=? AND dst=?').run(uid, msg.id >>> 0); return this.sendState(uid);
      case 'report': return this.report(ws, uid, msg);
      case 'dm': return this.dm(ws, uid, msg);
      case 'history': return this.history(ws, uid, msg);
      case 'invite': return this.invite(ws, uid, msg);
      case 'room_enter': return this.roomEnter(ws, msg);
      case 'room_chat': return this.roomChat(ws, uid, msg);
      case 'state': return this.sendState(uid);
      default: if (this.eco && this.ecoCmds.has(t)) return this.eco.handle(ws, uid, msg, (w, o) => this.send(w, o));
    }
  }

  hello(ws, msg) {
    const u = this.userByToken(msg.token);
    if (!u) return this.send(ws, { t: 'need_register' });
    if (u.banned) return this.send(ws, { t: 'error', error: 'banned' });
    this.attach(ws, u);
  }

  register(ws, msg) {
    const token = String(msg.token || '');
    if (!/^[0-9a-f]{32,128}$/.test(token)) return this.err(ws, 'register', 'bad_token');
    const nick = String(msg.nick || '').trim();
    const bad = validNick(nick);
    if (bad) return this.err(ws, 'register', bad);
    const existing = this.userByToken(token);
    if (existing) {
      this.store.q('UPDATE users SET nick=? WHERE id=?').run(nick, existing.id);
      return this.attach(ws, this.user(existing.id));
    }
    let num = 0;
    for (let i = 0; i < 40; i++) {
      const n = 1000 + crypto.randomInt(9000);
      if (!this.store.q('SELECT 1 FROM users WHERE nick=? COLLATE NOCASE AND num=?').get(nick, n)) { num = n; break; }
    }
    if (!num) return this.err(ws, 'register', 'Ник слишком популярный, выберите другой');
    const now = Date.now();
    const r = this.store.q('INSERT INTO users(token_hash,nick,num,created,last_seen) VALUES(?,?,?,?,?)').run(hashToken(token), nick, num, now, now);
    this.attach(ws, this.user(Number(r.lastInsertRowid)));
  }

  attach(ws, u) {
    ws._uid = u.id;
    let set = this.sessions.get(u.id);
    const wasOnline = !!set;
    if (!set) { set = new Set(); this.sessions.set(u.id, set); }
    set.add(ws);
    this.store.q('UPDATE users SET last_seen=? WHERE id=?').run(Date.now(), u.id);
    if (this.eco) { this.eco.touch(u.id); this.eco.checkAchievements(u.id); }
    this.send(ws, { t: 'welcome', me: this.pub(u), limits: { msgMax: LIMITS.msgMax, nickMax: LIMITS.nickMax }, tags: ROOM_TAGS });
    this.sendState(u.id, ws);
    if (!wasOnline) this.notifyFriends(u.id);
  }

  detach(ws) {
    const uid = ws._uid;
    if (!uid) return;
    const set = this.sessions.get(uid);
    if (set) { set.delete(ws); if (!set.size) { this.sessions.delete(uid); this.presence.delete(uid); this.notifyFriends(uid); } }
  }

  friendView(fid, viewer) {
    const p = this.pub(this.user(fid));
    if (!p) return null;
    const pr = this.presence.get(fid);
    p.activity = p.online ? (pr || { activity: 'idle' }) : null;
    if (p.activity && this.blocked(viewer, fid)) p.activity = { activity: 'idle' };
    return p;
  }

  sendState(uid, only) {
    const friends = this.friendsOf(uid).map((f) => this.friendView(f, uid)).filter(Boolean);
    const incoming = this.store.q('SELECT src FROM requests WHERE dst=?').all(uid).map((r) => this.pub(this.user(r.src))).filter(Boolean);
    const outgoing = this.store.q('SELECT dst FROM requests WHERE src=?').all(uid).map((r) => this.pub(this.user(r.dst))).filter(Boolean);
    const blocked = this.store.q('SELECT dst FROM blocks WHERE src=?').all(uid).map((r) => this.pub(this.user(r.dst))).filter(Boolean);
    const o = { t: 'state', me: this.pub(this.user(uid)), friends, incoming, outgoing, blocked };
    if (only) this.send(only, o); else this.push(uid, o);
  }

  notifyFriends(uid) { for (const f of this.friendsOf(uid)) if (this.sessions.has(f)) this.push(f, { t: 'friend', friend: this.friendView(uid, f) }); }

  setPresence(uid, msg) {
    const a = ['idle', 'hosting', 'playing'].includes(msg.activity) ? msg.activity : 'idle';
    const p = { activity: a, ts: Date.now() };
    if (a !== 'idle') {
      p.world = cleanText(msg.world).slice(0, LIMITS.titleMax);
      p.edition = ['java', 'bedrock', 'both'].includes(msg.edition) ? msg.edition : '';
      p.version = String(msg.version || '').replace(/[^0-9a-zA-Z.\- ]/g, '').slice(0, 16);
      if (typeof msg.code === 'string' && /^[2-9A-HJKMNP-Z]{6}$/.test(msg.code)) p.code = msg.code;
    }
    this.presence.set(uid, p);
    this.notifyFriends(uid);
  }

  findByTag(tag) {
    const m = /^(.{3,16})#(\d{4})$/.exec(String(tag || '').trim());
    if (!m) return null;
    return this.store.q('SELECT * FROM users WHERE nick=? COLLATE NOCASE AND num=?').get(m[1], Number(m[2]));
  }

  friendAdd(ws, uid, msg) {
    const target = msg.id ? this.user(msg.id >>> 0) : this.findByTag(msg.tag);
    if (!target) return this.err(ws, 'friend_add', 'Игрок с таким тегом не найден');
    if (target.id === uid) return this.err(ws, 'friend_add', 'Это вы :)');
    if (this.areFriends(uid, target.id)) return this.err(ws, 'friend_add', 'Вы уже друзья');
    if (this.blocked(uid, target.id)) return this.err(ws, 'friend_add', 'Нельзя добавить этого игрока');
    if (this.friendsOf(uid).length >= LIMITS.friendsMax) return this.err(ws, 'friend_add', 'Слишком много друзей');
    if (this.limited(uid, 'req')) return this.err(ws, 'friend_add', 'Слишком много заявок, подождите');
    // встречная заявка — сразу дружба
    if (this.store.q('SELECT 1 FROM requests WHERE src=? AND dst=?').get(target.id, uid)) return this.friendAccept(ws, uid, target.id);
    this.store.q('INSERT OR IGNORE INTO requests(src,dst,created) VALUES(?,?,?)').run(uid, target.id, Date.now());
    this.send(ws, { t: 'friend_add_ok', user: this.pub(target) });
    this.sendState(uid);
    this.sendState(target.id);
    this.push(target.id, { t: 'notice', kind: 'request', from: this.pub(this.user(uid)) });
  }

  friendAccept(ws, uid, src) {
    if (!this.store.q('SELECT 1 FROM requests WHERE src=? AND dst=?').get(src, uid)) return this.err(ws, 'friend_accept', 'Заявка не найдена');
    const now = Date.now();
    this.store.q('DELETE FROM requests WHERE (src=? AND dst=?) OR (src=? AND dst=?)').run(src, uid, uid, src);
    this.store.q('INSERT OR IGNORE INTO friends(a,b,since) VALUES(?,?,?)').run(uid, src, now);
    this.store.q('INSERT OR IGNORE INTO friends(a,b,since) VALUES(?,?,?)').run(src, uid, now);
    if (this.eco) { this.eco.checkAchievements(uid); this.eco.checkAchievements(src); }
    this.sendState(uid);
    this.sendState(src);
    this.push(src, { t: 'notice', kind: 'accepted', from: this.pub(this.user(uid)) });
  }

  friendRemove(uid, fid) {
    this.store.q('DELETE FROM friends WHERE (a=? AND b=?) OR (a=? AND b=?)').run(uid, fid, fid, uid);
    this.store.q('DELETE FROM requests WHERE (src=? AND dst=?) OR (src=? AND dst=?)').run(uid, fid, fid, uid);
    this.sendState(uid);
    this.sendState(fid);
  }

  block(uid, target) {
    if (!target || target === uid) return;
    this.friendRemove(uid, target);
    this.store.q('INSERT OR IGNORE INTO blocks(src,dst) VALUES(?,?)').run(uid, target);
    this.sendState(uid);
  }

  report(ws, uid, msg) {
    if (this.limited(uid, 'report')) return this.err(ws, 'report', 'Слишком много жалоб');
    const reason = String(msg.reason || '').slice(0, 200);
    let text = '';
    if (msg.msg_id) { const m = this.store.q('SELECT text FROM messages WHERE id=?').get(msg.msg_id >>> 0); text = m ? m.text : ''; }
    this.store.q('INSERT INTO reports(src,dst,reason,msg,ts) VALUES(?,?,?,?,?)').run(uid, msg.id >>> 0, reason, text, Date.now());
    if (msg.block) this.block(uid, msg.id >>> 0);
    this.send(ws, { t: 'report_ok' });
  }

  chatId(a, b) { return a < b ? `dm:${a}:${b}` : `dm:${b}:${a}`; }

  dm(ws, uid, msg) {
    const to = msg.to >>> 0;
    if (!this.areFriends(uid, to)) return this.err(ws, 'dm', 'Писать можно только друзьям');
    if (this.blocked(uid, to)) return this.err(ws, 'dm', 'Нельзя написать этому игроку');
    if (this.limited(uid, 'msg')) return this.err(ws, 'dm', 'Слишком часто, подождите немного');
    const text = cleanText(msg.text);
    if (!text) return;
    const chat = this.chatId(uid, to);
    const ts = Date.now();
    const r = this.store.q('INSERT INTO messages(chat,src,text,ts) VALUES(?,?,?,?)').run(chat, uid, text, ts);
    this.store.q('DELETE FROM messages WHERE chat=? AND id NOT IN (SELECT id FROM messages WHERE chat=? ORDER BY id DESC LIMIT ?)').run(chat, chat, LIMITS.chatKeep);
    const me = this.user(uid);
    const m = { t: 'dm', id: Number(r.lastInsertRowid), from: uid, to, nick: me.nick, text, ts };
    this.push(uid, m);
    this.push(to, m);
  }

  history(ws, uid, msg) {
    const other = msg.with >>> 0;
    if (!this.areFriends(uid, other)) return this.send(ws, { t: 'history', with: other, messages: [] });
    const rows = this.store.q('SELECT id, src, text, ts FROM messages WHERE chat=? AND ts>? ORDER BY id DESC LIMIT ?').all(this.chatId(uid, other), Date.now() - LIMITS.chatTtlMs, LIMITS.chatKeep);
    this.send(ws, { t: 'history', with: other, messages: rows.reverse().map((r) => ({ id: r.id, from: r.src, text: r.text, ts: r.ts })) });
  }

  invite(ws, uid, msg) {
    const to = msg.to >>> 0;
    if (!this.areFriends(uid, to) || this.blocked(uid, to)) return this.err(ws, 'invite', 'Приглашать можно только друзей');
    if (this.limited(uid, 'msg')) return this.err(ws, 'invite', 'Слишком часто');
    if (!/^[2-9A-HJKMNP-Z]{6}$/.test(String(msg.code))) return this.err(ws, 'invite', 'Нет кода комнаты');
    this.push(to, { t: 'invite', from: this.pub(this.user(uid)), code: msg.code, world: cleanText(msg.world).slice(0, LIMITS.titleMax), edition: msg.edition });
    this.send(ws, { t: 'invite_ok' });
    if (this.eco) this.eco.event(uid, 'invite');
  }

  // ---- комнаты: лобби-чат и публичный список (комнаты живут в relay)
  bindRooms(rooms) { this.rooms = rooms; }

  roomEnter(ws, msg) {
    const code = String(msg.code || '').toUpperCase();
    if (!this.rooms || !this.rooms.has(code)) return this.err(ws, 'room_enter', 'Комната не найдена');
    ws._chatRoom = code;
    this.send(ws, { t: 'room_history', code, messages: (this.roomChats.get(code) || []).slice(-50) });
  }

  roomChat(ws, uid, msg) {
    const code = ws._chatRoom;
    if (!code || !this.rooms || !this.rooms.has(code)) return this.err(ws, 'room_chat', 'Вы не в комнате');
    if (this.limited(uid, 'msg')) return this.err(ws, 'room_chat', 'Слишком часто, подождите немного');
    const text = cleanText(msg.text);
    if (!text) return;
    const u = this.user(uid);
    const m = { id: this.nextMsg++, from: uid, nick: u.nick, lantern: lanternLevel(u.xp || 0).level, text, ts: Date.now() };
    if (this.eco) this.eco.event(uid, 'room_chat');
    const list = this.roomChats.get(code) || [];
    list.push(m);
    while (list.length > LIMITS.chatKeep) list.shift();
    this.roomChats.set(code, list);
    for (const set of this.sessions.values()) for (const s of set) {
      if (s._chatRoom === code && !this.blocked(s._uid, uid)) this.send(s, { t: 'room_chat', code, msg: m });
    }
  }

  roomClosed(code) { this.roomChats.delete(code); }

  // публичная информация о комнате, которую прислал хозяин в {"t":"host", public:{...}}
  static roomMeta(pub) {
    if (!pub || typeof pub !== 'object') return null;
    const tags = (Array.isArray(pub.tags) ? pub.tags : []).map((x) => String(x).toLowerCase()).filter((x) => ROOM_TAGS.includes(x)).slice(0, LIMITS.tagsMax);
    const title = cleanText(pub.title).slice(0, LIMITS.titleMax);
    if (!title) return null;
    return { title, tags, version: String(pub.version || '').replace(/[^0-9a-zA-Z.\- ]/g, '').slice(0, 16), slots: Math.max(1, Math.min(100, pub.slots >>> 0 || 10)) };
  }

  publicRooms(f = {}) {
    if (!this.rooms) return [];
    const out = [];
    for (const r of this.rooms.values()) {
      if (!r.meta || !r.host) continue;
      if (f.edition && f.edition !== 'any' && !(r.edition === f.edition || r.edition === 'both')) continue;
      if (f.tag && !r.meta.tags.includes(f.tag)) continue;
      if (f.q && !r.meta.title.toLowerCase().includes(String(f.q).toLowerCase())) continue;
      const owner = r.ownerId ? this.pub(this.user(r.ownerId)) : null;
      out.push({ code: r.code, title: r.meta.title, tags: r.meta.tags, version: r.meta.version, slots: r.meta.slots,
        edition: r.edition, players: r.joiners.size, host: owner ? { nick: owner.nick, tag: owner.tag, lantern: owner.lantern } : null,
        likes: r.likes ? r.likes.size : 0, age: Math.round((Date.now() - r.created) / 60000) });
    }
    out.sort((a, b) => b.players - a.players || a.age - b.age);
    return out.slice(0, 100);
  }

  // «Фонарь»: каждую минуту +1 минута хозяину и каждому другу-аккаунту, пока в комнате идёт игра
  tickPlaytime() {
    if (!this.rooms) return;
    for (const r of this.rooms.values()) {
      if (!r.host || !r.ownerId) continue;
      const active = [...r.joiners].filter((j) => j.userId && j.streams.size > 0 && j.userId !== r.ownerId);
      if (!active.length) continue;
      const ids = new Set([r.ownerId, ...active.map((j) => j.userId)]);
      for (const id of ids) {
        if (this.eco) this.eco.event(id, 'together_min', 1);
        else this.store.q('UPDATE users SET minutes=minutes+1 WHERE id=?').run(id);
      }
    }
  }

  cleanup() {
    this.store.q('DELETE FROM messages WHERE ts<?').run(Date.now() - LIMITS.chatTtlMs);
    this.store.q('DELETE FROM requests WHERE created<?').run(Date.now() - 30 * 24 * 3600_000);
  }
}

module.exports = { Social, cleanText, validNick, isBad, lanternLevel, ROOM_TAGS, LANTERN_HOURS, hashToken };
