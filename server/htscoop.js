'use strict';
// Hush Till Six — «Смена на двоих» (кооператив, 27 ноя). Комнаты на двоих без базы и без зависимостей:
//   POST /hts/coop/new  {name}        → {room, id, seed}          — создать комнату (хост)
//   POST /hts/coop/join {room, name}  → {room, id, seed, peers}   — войти по коду
//   GET  /hts/coop/ev?room=&id=       → поток событий (text/event-stream): {t:'peer'|'left'|'msg', ...}
//   POST /hts/coop/send {room, id, m} → разослать m второму игроку
// Комната живёт в памяти: пустая — 10 минут, всего — не больше 6 часов. Сообщения — маленький JSON (≤ 4 КБ), не чаще 30 в секунду.
const crypto = require('crypto');

const ROOMS = new Map(), MAX_ROOMS = 2000, MAX_MSG = 4096, IDLE_MS = 10 * 60e3, LIFE_MS = 6 * 3600e3;
const ABC = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';   // без 0/O и 1/I — код читают вслух на стриме

const code = () => { let s = ''; const b = crypto.randomBytes(5); for (let i = 0; i < 5; i++) s += ABC[b[i] % ABC.length]; return s; };
const pid = () => crypto.randomBytes(9).toString('base64url');
const clean = s => String(s || '').replace(/[\u0000-\u001f<>]/g, '').trim().slice(0, 20) || 'Guard';

function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(obj));
}
function body(req) {
  return new Promise((ok, no) => { let n = 0; const ch = [];
    req.on('data', c => { n += c.length; if (n > MAX_MSG * 2) { no(new Error('big')); req.destroy(); } else ch.push(c); });
    req.on('end', () => { try { ok(JSON.parse(Buffer.concat(ch).toString('utf8') || '{}')); } catch (e) { no(e); } }); req.on('error', no); });
}
function push(p, obj) { if (p.res) try { p.res.write(`data: ${JSON.stringify(obj)}\n\n`); } catch (e) { p.res = null; } }
function others(room, id) { return [...room.players.values()].filter(p => p.id !== id); }
function sweep() {
  const t = Date.now();
  for (const [k, r] of ROOMS) { const live = [...r.players.values()].some(p => p.res);
    if (t - r.born > LIFE_MS || (!live && t - r.seen > IDLE_MS)) { for (const p of r.players.values()) if (p.res) try { p.res.end(); } catch (e) {} ROOMS.delete(k); } }
}
setInterval(sweep, 60e3).unref();
// пинг раз в 20 с — прокси Railway не закрывает «молчащий» поток
setInterval(() => { for (const r of ROOMS.values()) for (const p of r.players.values()) if (p.res) try { p.res.write(': ping\n\n'); } catch (e) { p.res = null; } }, 20e3).unref();

async function handle(req, res, sub) {
  if (req.method === 'OPTIONS') { res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Max-Age': '86400' }); return res.end(); }
  const u = new URL(sub, 'http://x');
  try {
    if (u.pathname === '/new' && req.method === 'POST') {
      const b = await body(req); sweep(); if (ROOMS.size >= MAX_ROOMS) return send(res, 503, { err: 'busy' });
      let k; do k = code(); while (ROOMS.has(k));
      const p = { id: pid(), name: clean(b.name), host: true, res: null };
      const r = { code: k, seed: crypto.randomBytes(4).readUInt32LE(0), born: Date.now(), seen: Date.now(), players: new Map([[p.id, p]]), rate: { t: 0, n: 0 } };
      ROOMS.set(k, r); return send(res, 200, { room: k, id: p.id, seed: r.seed, host: true });
    }
    if (u.pathname === '/join' && req.method === 'POST') {
      const b = await body(req), r = ROOMS.get(String(b.room || '').toUpperCase().trim());
      if (!r) return send(res, 404, { err: 'no_room' });
      if (r.players.size >= 2) return send(res, 409, { err: 'full' });
      const p = { id: pid(), name: clean(b.name), host: false, res: null }; r.players.set(p.id, p); r.seen = Date.now();
      for (const o of others(r, p.id)) push(o, { t: 'peer', name: p.name });
      return send(res, 200, { room: r.code, id: p.id, seed: r.seed, host: false, peers: others(r, p.id).map(o => o.name) });
    }
    if (u.pathname === '/ev' && req.method === 'GET') {
      const r = ROOMS.get(String(u.searchParams.get('room') || '').toUpperCase()), p = r && r.players.get(u.searchParams.get('id') || '');
      if (!p) return send(res, 404, { err: 'no_room' });
      if (p.res) try { p.res.end(); } catch (e) {}
      res.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-store', 'Connection': 'keep-alive', 'Access-Control-Allow-Origin': '*', 'X-Accel-Buffering': 'no' });
      res.write('retry: 2000\n\n'); p.res = res; r.seen = Date.now();
      push(p, { t: 'hello', peers: others(r, p.id).map(o => ({ name: o.name, on: !!o.res })) });
      for (const o of others(r, p.id)) push(o, { t: 'peer', name: p.name, on: true });
      req.on('close', () => { if (p.res === res) { p.res = null; r.seen = Date.now(); for (const o of others(r, p.id)) push(o, { t: 'left', name: p.name }); } });
      return;
    }
    if (u.pathname === '/send' && req.method === 'POST') {
      const b = await body(req), r = ROOMS.get(String(b.room || '').toUpperCase()), p = r && r.players.get(b.id || '');
      if (!p) return send(res, 404, { err: 'no_room' });
      const s = JSON.stringify(b.m || null); if (s.length > MAX_MSG) return send(res, 413, { err: 'big' });
      const t = Date.now(); if (t - r.rate.t > 1000) { r.rate.t = t; r.rate.n = 0; } if (++r.rate.n > 30) return send(res, 429, { err: 'slow' });
      r.seen = t; for (const o of others(r, p.id)) push(o, { t: 'msg', m: b.m });
      return send(res, 200, { ok: 1 });
    }
    return send(res, 404, { err: 'not_found' });
  } catch (e) { return send(res, 400, { err: 'bad' }); }
}

module.exports = { handle, _rooms: ROOMS };
