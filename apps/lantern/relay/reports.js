'use strict';
// «Сообщить о проблеме» (LANTERN 1.6): приложение присылает журнал (свой + хвост журнала сервера Minecraft),
// relay сохраняет его под номером и отвечает номером — игрок пересылает номер разработчику.
// POST /report — отправить (JSON, до 2 МБ, не чаще REPORTS_PER_HOUR с одного IP; IP не сохраняется).
// GET /report — список, GET /report/<номер> — сам отчёт: только с заголовком x-upload-key (как PUT /download).
const fs = require('fs');
const path = require('path');
const { keyOk } = require('./download');

const MAX_BYTES = 2 * 1048576;
const MAX_FILES = 500;
const PER_HOUR = Number(process.env.REPORTS_PER_HOUR || 6);
const FIRST_ID = 1000;

// Второй рубеж (приложение уже вычищает сам): токены, пароли, ICE-пароли, чужие IP.
function scrub(text) {
  return String(text)
    .replace(/XBL3\.0 x=[^\s"]+/g, 'XBL3.0 ***')
    .replace(/\b(Bearer|Basic)\s+[A-Za-z0-9._~+\/=-]+/g, '$1 ***')
    .replace(/(authorization|x-upload-key|cookie|token|password|passwd|secret|xbl3\.0[^"\s]*)(["']?\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s,;}]+)/gi, '$1$2***')
    .replace(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}/g, '<jwt>')
    .replace(/a=ice-pwd:\S+/g, 'a=ice-pwd:***')
    .replace(/a=ice-ufrag:\S+/g, 'a=ice-ufrag:***')
    .replace(/a=fingerprint:\S+ \S+/g, 'a=fingerprint:***')
    // IPv4 (кроме своих/локальных; версии Minecraft вроде 1.21.130.1 не трогаем — первое число < 30)
    .replace(/\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b/g, (m, a, b, c, d) => {
      const n = [a, b, c, d].map(Number);
      if (n.some((x) => x > 255) || n[0] < 30 || n[0] === 127 || n[0] >= 224 || (n[0] === 192 && n[1] === 168) || (n[0] === 172 && n[1] >= 16 && n[1] <= 31)) return m;
      return `${a}.x.x.${d}`;
    });
}

class Reports {
  constructor(dir) {
    this.dir = dir || '';
    this.hits = new Map(); // ip -> [ts]
    this.next = FIRST_ID;
    if (this.dir) {
      fs.mkdirSync(this.dir, { recursive: true });
      for (const f of this.files()) this.next = Math.max(this.next, f.id + 1);
    }
  }
  files() {
    if (!this.dir) return [];
    return fs.readdirSync(this.dir).filter((f) => /^\d+\.json$/.test(f))
      .map((f) => ({ id: Number(f.slice(0, -5)), file: path.join(this.dir, f) })).sort((a, b) => a.id - b.id);
  }
  rate(ip) {
    const now = Date.now();
    const l = (this.hits.get(ip) || []).filter((t) => now - t < 3600_000);
    if (l.length >= PER_HOUR) { this.hits.set(ip, l); return false; }
    l.push(now); this.hits.set(ip, l);
    if (this.hits.size > 5000) for (const [k, v] of this.hits) if (!v.length || now - v[v.length - 1] > 3600_000) this.hits.delete(k);
    return true;
  }
  save(obj) {
    const id = this.next++;
    const rep = { id, received: new Date().toISOString() };
    const deep = (v, d = 0) => typeof v === 'string' ? scrub(v) : d > 6 || v === null || typeof v !== 'object' ? v
      : Array.isArray(v) ? v.slice(0, 200).map((x) => deep(x, d + 1)) : Object.fromEntries(Object.entries(v).slice(0, 200).map(([k, x]) => [k, deep(x, d + 1)]));
    for (const k of ['app', 'os', 'device', 'kind', 'note', 'nick', 'context', 'minecraft', 'room', 'state']) if (obj[k] !== undefined) rep[k] = deep(obj[k]);
    rep.app_log = scrub(String(obj.app_log || '')).slice(-1_200_000);
    rep.server_log = scrub(String(obj.server_log || '')).slice(-600_000);
    const text = JSON.stringify(rep, null, 1);
    fs.writeFileSync(path.join(this.dir, id + '.json'), text);
    const all = this.files();
    for (const f of all.slice(0, Math.max(0, all.length - MAX_FILES))) fs.rmSync(f.file, { force: true });
    return id;
  }
  // url — без префикса /lantern
  http(req, res, url, ip) {
    const u = new URL(url, 'http://x');
    if (u.pathname !== '/report' && !u.pathname.startsWith('/report/')) return false;
    const json = (code, o) => { res.writeHead(code, { 'content-type': 'application/json', 'cache-control': 'no-store' }); res.end(JSON.stringify(o)); };
    if (!this.dir) { req.resume(); json(503, { error: 'reports_off' }); return true; }
    if (req.method === 'POST' && u.pathname === '/report') {
      if (!this.rate(ip || '?')) { req.resume(); json(429, { error: 'rate' }); return true; }
      const chunks = []; let n = 0, over = false;
      req.on('data', (c) => { n += c.length; if (n > MAX_BYTES) { if (!over) { over = true; json(413, { error: 'too_big' }); req.destroy(); } return; } chunks.push(c); });
      req.on('end', () => {
        if (over) return;
        let obj;
        try { obj = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return json(400, { error: 'bad_json' }); }
        if (!obj || typeof obj !== 'object') return json(400, { error: 'bad_json' });
        try { json(200, { ok: true, id: this.save(obj) }); } catch (e) { json(500, { error: 'write' }); }
      });
      return true;
    }
    if (req.method === 'GET') {
      if (!keyOk(req.headers['x-upload-key'])) { json(403, { error: 'forbidden' }); return true; }
      if (u.pathname === '/report') {
        const list = this.files().slice(-100).reverse().map((f) => {
          let head = {};
          try { const r = JSON.parse(fs.readFileSync(f.file, 'utf8')); head = { received: r.received, app: r.app, os: r.os, device: r.device, kind: r.kind, note: r.note, nick: r.nick }; } catch {}
          return { id: f.id, size: fs.statSync(f.file).size, ...head };
        });
        json(200, { reports: list });
        return true;
      }
      const id = u.pathname.slice('/report/'.length).replace(/\.json$/, '');
      if (!/^\d+$/.test(id)) { json(404, { error: 'not_found' }); return true; }
      const file = path.join(this.dir, id + '.json');
      if (!fs.existsSync(file)) { json(404, { error: 'not_found' }); return true; }
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
      fs.createReadStream(file).pipe(res);
      return true;
    }
    req.resume(); json(405, { error: 'method' });
    return true;
  }
}

module.exports = { Reports, scrub };
