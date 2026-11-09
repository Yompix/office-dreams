'use strict';
// LANTERN Pro: свой код комнаты и облачные копии миров.
//  * Свой код (vanity): 3–8 символов A–Z и 2–9 (без 0/1, чтобы не путать с O/I), уникален, проходит фильтр слов,
//    действует, пока активна Pro. Когда Pro закончилась — код освобождается (cleanup) и комнаты снова получают случайный.
//  * Облако: zip-копии миров на томе Railway (CLOUD_DIR или <том>/cloud). Загрузка/скачивание — HTTP PUT/GET
//    с токеном устройства, список/удаление/код для друга — команды WebSocket. Без тома облако выключено.
//    Лимиты: CLOUD_MAX_MB на файл (100), CLOUD_SLOTS копий на игрока (3, самая старая заменяется),
//    CLOUD_TOTAL_MB на весь relay (4096). Копии удаляются через CLOUD_KEEP_DAYS (30) после окончания Pro.
//    Код для друга/второго устройства: 6 символов, живёт 24 часа, скачать может любой профиль LANTERN.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { isBad } = require('./social');

const DAY = 24 * 3600_000;
const VANITY_RE = /^[A-Z2-9]{3,8}$/;
const RESERVED = /^(ADMIN|ADMN|MODER|MOD|LANTERN|LANTRN|YOMPIX|SUPPORT|HELP|STAFF|OFFICIAL|MOJANG|MICROSOFT|XBOX|MINECRAFT|GOOGLE|TEST)/;
// Мат латиницей (транслит), который фильтр ников не ловит в коротких кодах
const TRANSLIT_BAD = ['SUKA', 'SUKI', 'BLYA', 'BLYAT', 'BLAT', 'HUI', 'HUY', 'HUE', 'XUY', 'XUI', 'PIZD', 'PEZD', 'EBAT', 'EBAN', 'EBAL', 'MUDAK', 'PIDOR', 'PIDAR',
  'GANDON', 'ZALUP', 'SHLUH', 'DROCH', 'NAZI', 'HITLER', 'KKK', 'RAPE', 'SEX', 'PORN', 'FUCK', 'FUK', 'FCK', 'SHIT', 'DICK', 'CUNT', 'NIGG'];
const SHARE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const num = (k, d) => (process.env[k] !== undefined ? Number(process.env[k]) : d);

function normVanity(c) { return String(c || '').toUpperCase().replace(/[\s-]/g, ''); }

function validVanity(code) {
  if (!VANITY_RE.test(code)) return 'Код — от 3 до 8 символов: латинские буквы и цифры 2–9';
  if (/^[2-9]+$/.test(code)) return 'Добавьте хотя бы одну букву';
  if (RESERVED.test(code)) return 'Этот код зарезервирован';
  if (isBad(code) || isBad(code.toLowerCase()) || TRANSLIT_BAD.some((w) => code.includes(w))) return 'Такой код нельзя';
  return '';
}

class Pro {
  constructor(social, rooms, opts = {}) {
    this.social = social;
    this.rooms = rooms;
    this.db = social.store.db;
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS vanity(code TEXT PRIMARY KEY, uid INTEGER UNIQUE, ts INTEGER);
      CREATE TABLE IF NOT EXISTS cloud(id TEXT PRIMARY KEY, uid INTEGER, name TEXT, size INTEGER, sha256 TEXT, ts INTEGER, share TEXT, share_until INTEGER DEFAULT 0);
      CREATE INDEX IF NOT EXISTS cloud_uid ON cloud(uid);
    `);
    const vol = process.env.RAILWAY_VOLUME_MOUNT_PATH;
    this.cloudDir = process.env.CLOUD_DIR || opts.cloudDir || (vol ? path.join(vol, 'cloud') : '');
    // на общем диске с сайтом всегда оставляем запас под его базу
    this.minFree = num('CLOUD_MIN_FREE_MB', 1024) * 1048576;
    if (this.cloudDir) fs.mkdirSync(this.cloudDir, { recursive: true });
    this.lim = { maxBytes: num('CLOUD_MAX_MB', 100) * 1048576, slots: num('CLOUD_SLOTS', 3), total: num('CLOUD_TOTAL_MB', opts.totalMb || 4096) * 1048576, keepDays: num('CLOUD_KEEP_DAYS', 30) };
    this.uploading = new Set();
    setInterval(() => this.cleanup(), 30 * 60_000).unref();
  }
  q(sql) { return this.db.prepare(sql); }
  isPro(uid) { const u = uid && this.social.user(uid); return !!u && (u.pro_until || 0) > Date.now(); }

  // ---------------------------------------------------------------- свой код комнаты
  vanityOf(uid) { const r = this.q('SELECT code FROM vanity WHERE uid=?').get(uid); return r ? r.code : ''; }

  // Код занят кем-то с действующей Pro (или открыта комната с таким кодом)
  vanityTaken(code, exceptUid = 0) {
    const r = this.q('SELECT uid FROM vanity WHERE code=?').get(code);
    if (r && r.uid !== exceptUid && this.isPro(r.uid)) return true;
    const room = this.rooms.get(code);
    return !!(room && room.ownerId !== exceptUid);
  }

  setVanity(uid, raw) {
    if (!this.isPro(uid)) return { error: 'Свой код — функция LANTERN Pro' };
    const code = normVanity(raw);
    const bad = validVanity(code);
    if (bad) return { error: bad };
    if (this.vanityTaken(code, uid)) return { error: 'Этот код уже занят — попробуйте другой' };
    this.q('DELETE FROM vanity WHERE code=? OR uid=?').run(code, uid);
    this.q('INSERT INTO vanity(code,uid,ts) VALUES(?,?,?)').run(code, uid, Date.now());
    return { code };
  }

  clearVanity(uid) { this.q('DELETE FROM vanity WHERE uid=?').run(uid); }

  // Для {"t":"host", vanity:true}: код, если Pro активна и комната с ним сейчас не открыта
  codeForHost(uid) {
    if (!uid || !this.isPro(uid)) return '';
    const code = this.vanityOf(uid);
    if (!code || this.rooms.has(code)) return '';
    return code;
  }

  // Случайный код не должен совпасть с чьим-то зарезервированным
  reserved(code) { return !!this.q('SELECT 1 FROM vanity WHERE code=?').get(code); }

  // ---------------------------------------------------------------- облако
  freeBytes() { try { const st = fs.statfsSync(this.cloudDir); return st.bavail * st.bsize; } catch { return Infinity; } }
  cloudEnabled() { return !!this.cloudDir; }
  file(id) { return path.join(this.cloudDir, id + '.zip'); }
  usedTotal() { return this.q('SELECT COALESCE(SUM(size),0) AS s FROM cloud').get().s; }
  cloudList(uid) {
    return this.q('SELECT id,name,size,ts,share,share_until FROM cloud WHERE uid=? ORDER BY ts DESC').all(uid)
      .map((r) => ({ id: r.id, name: r.name, size: r.size, ts: r.ts, share: r.share_until > Date.now() ? r.share : '', share_until: r.share_until > Date.now() ? r.share_until : 0 }));
  }
  cloudView(uid) {
    return { enabled: this.cloudEnabled(), pro: this.isPro(uid), list: this.cloudList(uid), max_mb: Math.round(this.lim.maxBytes / 1048576), slots: this.lim.slots };
  }
  cloudDelete(uid, id) {
    const r = this.q('SELECT id FROM cloud WHERE id=? AND uid=?').get(String(id), uid);
    if (!r) return 'Копия не найдена';
    this.q('DELETE FROM cloud WHERE id=?').run(r.id);
    fs.rmSync(this.file(r.id), { force: true });
    return '';
  }
  cloudShare(uid, id) {
    const r = this.q('SELECT id FROM cloud WHERE id=? AND uid=?').get(String(id), uid);
    if (!r) return { error: 'Копия не найдена' };
    let code = '';
    for (let i = 0; i < 20 && !code; i++) {
      const b = crypto.randomBytes(6);
      let s = '';
      for (let k = 0; k < 6; k++) s += SHARE_ALPHABET[b[k] % SHARE_ALPHABET.length];
      if (!this.q('SELECT 1 FROM cloud WHERE share=? AND share_until>?').get(s, Date.now())) code = s;
    }
    const until = Date.now() + DAY;
    this.q('UPDATE cloud SET share=?, share_until=? WHERE id=?').run(code, until, r.id);
    return { code, until };
  }

  // HTTP: PUT /cloud/upload?name=…, GET /cloud/file/<id>, GET /cloud/share/<код>. Возвращает true, если запрос наш.
  http(req, res, url = req.url) {
    const u = new URL(url, 'http://x');
    if (!u.pathname.startsWith('/cloud/')) return false;
    const json = (code, o) => { res.writeHead(code, { 'content-type': 'application/json', 'cache-control': 'no-store' }); res.end(JSON.stringify(o)); };
    if (!this.cloudEnabled()) { json(503, { ok: false, error: 'Облако выключено на этом сервере' }); req.resume(); return true; }
    const auth = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '');
    const user = this.social.userByToken(auth);
    if (!user) { json(401, { ok: false, error: 'Нужен профиль LANTERN' }); req.resume(); return true; }
    const send = (file, name, size) => {
      res.writeHead(200, { 'content-type': 'application/zip', 'content-length': size, 'content-disposition': `attachment; filename="${encodeURIComponent(name)}"`, 'cache-control': 'no-store' });
      fs.createReadStream(file).on('error', () => res.destroy()).pipe(res);
    };
    if (req.method === 'GET' && u.pathname.startsWith('/cloud/file/')) {
      const r = this.q('SELECT * FROM cloud WHERE id=? AND uid=?').get(u.pathname.slice(12), user.id);
      if (!r) return json(404, { ok: false, error: 'Копия не найдена' }), true;
      send(this.file(r.id), r.name, r.size);
      return true;
    }
    if (req.method === 'GET' && u.pathname.startsWith('/cloud/share/')) {
      const code = normVanity(u.pathname.slice(13));
      const r = /^[2-9A-Z]{6}$/.test(code) && this.q('SELECT * FROM cloud WHERE share=? AND share_until>?').get(code, Date.now());
      if (!r) return json(404, { ok: false, error: 'Код не найден или устарел (действует 24 часа)' }), true;
      send(this.file(r.id), r.name, r.size);
      return true;
    }
    if (req.method === 'PUT' && u.pathname === '/cloud/upload') {
      this.upload(req, user, u.searchParams.get('name') || 'world.zip', json);
      return true;
    }
    json(404, { ok: false, error: 'not_found' });
    req.resume();
    return true;
  }

  upload(req, user, rawName, json) {
    const fail = (code, error) => { req.resume(); json(code, { ok: false, error }); };
    if (!this.isPro(user.id)) return fail(403, 'Облачные копии — функция LANTERN Pro');
    const size = Number(req.headers['content-length'] || -1);
    if (!(size > 22)) return fail(411, 'Нужен размер файла');
    if (size > this.lim.maxBytes) return fail(413, `Копия больше ${Math.round(this.lim.maxBytes / 1048576)} МБ — облако её не примет`);
    if (this.usedTotal() + size > this.lim.total || this.freeBytes() - size < this.minFree) return fail(507, 'Облако LANTERN сейчас заполнено, попробуйте позже');
    if (this.uploading.has(user.id)) return fail(429, 'Уже идёт загрузка');
    this.uploading.add(user.id);
    const name = String(rawName).replace(/[^\w.\-а-яА-ЯёЁіїєґІЇЄҐ ]/g, '_').slice(0, 80) || 'world.zip';
    const id = crypto.randomBytes(12).toString('hex');
    const tmp = this.file(id) + '.part';
    const out = fs.createWriteStream(tmp);
    const hash = crypto.createHash('sha256');
    let got = 0;
    let head = null;
    let done = false;
    const finish = (code, o) => {
      if (done) return;
      done = true;
      this.uploading.delete(user.id);
      if (!o.ok) fs.rm(tmp, { force: true }, () => {});
      json(code, o);
    };
    req.on('data', (c) => {
      if (!head) head = c.subarray(0, 4);
      got += c.length;
      if (got > size) { req.destroy(); out.destroy(); return finish(400, { ok: false, error: 'Размер не совпал' }); }
      hash.update(c);
    });
    req.on('aborted', () => { out.destroy(); finish(400, { ok: false, error: 'Загрузка прервалась' }); });
    req.on('error', () => { out.destroy(); finish(400, { ok: false, error: 'Загрузка прервалась' }); });
    req.pipe(out);
    out.on('error', () => finish(500, { ok: false, error: 'Не удалось сохранить копию' }));
    out.on('finish', () => {
      if (done) return;
      if (got !== size) return finish(400, { ok: false, error: 'Загрузка прервалась' });
      if (!head || head.readUInt32LE(0) !== 0x04034b50) return finish(415, { ok: false, error: 'Это не zip-копия мира' });
      fs.renameSync(tmp, this.file(id));
      // самые старые копии сверх лимита — удаляем
      const mine = this.q('SELECT id FROM cloud WHERE uid=? ORDER BY ts DESC').all(user.id);
      for (const r of mine.slice(Math.max(0, this.lim.slots - 1))) this.cloudDelete(user.id, r.id);
      const ts = Date.now();
      this.q('INSERT INTO cloud(id,uid,name,size,sha256,ts) VALUES(?,?,?,?,?,?)').run(id, user.id, name, size, hash.digest('hex'), ts);
      finish(200, { ok: true, item: { id, name, size, ts } });
    });
  }

  cleanup() {
    const now = Date.now();
    // свой код освобождается, когда Pro закончилась
    for (const r of this.q('SELECT v.code, v.uid, u.pro_until FROM vanity v LEFT JOIN users u ON u.id=v.uid').all()) {
      if (!r.pro_until || r.pro_until <= now) this.q('DELETE FROM vanity WHERE code=?').run(r.code);
    }
    if (!this.cloudEnabled()) return;
    const keep = this.lim.keepDays * DAY;
    for (const r of this.q('SELECT c.id, c.uid, u.pro_until FROM cloud c LEFT JOIN users u ON u.id=c.uid').all()) {
      if (!r.pro_until || r.pro_until + keep < now) { this.q('DELETE FROM cloud WHERE id=?').run(r.id); fs.rmSync(this.file(r.id), { force: true }); }
    }
    try { for (const f of fs.readdirSync(this.cloudDir)) if (f.endsWith('.part') && now - fs.statSync(path.join(this.cloudDir, f)).mtimeMs > DAY) fs.rmSync(path.join(this.cloudDir, f), { force: true }); } catch { /* нет папки */ }
  }

  handle(ws, uid, msg, send) {
    switch (msg.t) {
      case 'vanity_set': { const r = this.setVanity(uid, msg.code); return send(ws, { t: 'vanity', error: r.error || '', code: r.code || this.vanityOf(uid) }); }
      case 'vanity_clear': this.clearVanity(uid); return send(ws, { t: 'vanity', error: '', code: '' });
      case 'cloud_list': return send(ws, { t: 'cloud', ...this.cloudView(uid) });
      case 'cloud_delete': { const e = this.cloudDelete(uid, msg.id); return send(ws, { t: 'cloud', error: e, ...this.cloudView(uid) }); }
      case 'cloud_share': { const r = this.cloudShare(uid, msg.id); return send(ws, { t: 'cloud', error: r.error || '', shared: r.code || '', ...this.cloudView(uid) }); }
    }
  }
}

const PRO_CMDS = new Set(['vanity_set', 'vanity_clear', 'cloud_list', 'cloud_delete', 'cloud_share']);
module.exports = { Pro, PRO_CMDS, validVanity, normVanity, VANITY_RE };
