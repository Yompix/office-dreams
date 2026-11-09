'use strict';
// Relay «Играем вместе» (by Yompix): комнаты Minecraft поверх WebSocket.
// Хост держит одно WS-соединение, каждый друг — своё. Relay пересылает бинарные кадры
// [u32 sid][payload] между другом и хостом, переводя номера потоков. Содержимое кадров не читается и не пишется в логи.
// Протокол: godot/playtogether/docs/PROTOCOL.md

const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const env = (k, d) => (process.env[k] !== undefined ? Number(process.env[k]) : d);
const CFG = {
  port: env('PORT', 8080),
  maxRooms: env('MAX_ROOMS', 300),
  maxJoiners: env('MAX_JOINERS', 20),          // друзей в одной комнате
  maxStreams: env('MAX_STREAMS', 64),          // потоков у одного друга
  maxConnPerIp: env('MAX_CONN_PER_IP', 40),
  joinPerMin: env('JOIN_PER_MIN', 30),         // попыток входа по коду с одного IP в минуту
  roomBytesPerSec: env('ROOM_BYTES_PER_SEC', 4 * 1024 * 1024),
  maxFrame: env('MAX_FRAME', 64 * 1024 + 4),
  idleMs: env('IDLE_MS', 90_000),              // нет ни данных, ни ping — закрываем
  resumeMs: env('RESUME_MS', 5 * 60_000),      // код держится, пока хост переподключается
  roomMaxMs: env('ROOM_MAX_MS', 24 * 3600_000),
  highWater: 8 * 1024 * 1024,
};
const PROTO = 1;
const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ'; // без 0/O, 1/I/L
const EDITIONS = new Set(['java', 'bedrock', 'both']);

const rooms = new Map();      // code -> room
const ipConns = new Map();    // ip -> count
const ipJoins = new Map();    // ip -> [timestamps]
let nextSid = 1;
let stats = { started: Date.now(), roomsCreated: 0, joins: 0 };

function log(...a) { if (process.env.QUIET !== '1') console.log(new Date().toISOString(), ...a); }

function makeCode() {
  for (let tries = 0; tries < 50; tries++) {
    const b = crypto.randomBytes(6);
    let s = '';
    for (let i = 0; i < 6; i++) s += ALPHABET[b[i] % ALPHABET.length];
    if (!rooms.has(s)) return s;
  }
  return null;
}

function normCode(c) {
  return String(c || '').toUpperCase().replace(/[\s-]/g, '');
}

function send(ws, obj) {
  if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj));
}

function frame(sid, payload) {
  const b = Buffer.allocUnsafe(4 + payload.length);
  b.writeUInt32BE(sid >>> 0, 0);
  payload.copy(b, 4);
  return b;
}

// Поток данных → с учётом лимита комнаты и переполнения получателя.
function forward(room, src, dst, data) {
  if (!dst || dst.readyState !== 1) return;
  const now = Date.now();
  room.tokens = Math.min(CFG.roomBytesPerSec, room.tokens + (now - room.tokenTs) * CFG.roomBytesPerSec / 1000);
  room.tokenTs = now;
  room.tokens -= data.length;
  room.bytes += data.length;
  dst.send(data);
  if (room.tokens < 0 || dst.bufferedAmount > CFG.highWater) throttle(src, dst, room);
}

function throttle(src, dst, room) {
  if (src._paused || !src._socket) return;
  src._paused = true;
  src._socket.pause();
  const check = () => {
    if (src.readyState !== 1) return;
    const now = Date.now();
    room.tokens = Math.min(CFG.roomBytesPerSec, room.tokens + (now - room.tokenTs) * CFG.roomBytesPerSec / 1000);
    room.tokenTs = now;
    if (room.tokens >= 0 && (!dst || dst.readyState !== 1 || dst.bufferedAmount < CFG.highWater / 2)) {
      src._paused = false;
      src._socket.resume();
    } else setTimeout(check, 25);
  };
  setTimeout(check, 25);
}

function closeRoom(room, why) {
  if (room.closed) return;
  room.closed = true;
  rooms.delete(room.code);
  clearTimeout(room.resumeTimer);
  for (const j of room.joiners) {
    send(j.ws, { t: 'error', error: why || 'room_closed' });
    j.ws.close(4000, 'room closed');
  }
  if (room.host) room.host.close(4000, 'room closed');
  log('room closed', room.code, why, 'bytes', room.bytes);
}

function hostGone(room) {
  room.host = null;
  for (const [hsid, s] of room.streams) { send(s.joiner.ws, { t: 'close', sid: s.lsid }); s.joiner.streams.delete(s.lsid); }
  room.streams.clear();
  for (const j of room.joiners) send(j.ws, { t: 'host_gone' });
  room.resumeTimer = setTimeout(() => closeRoom(room, 'host_left'), CFG.resumeMs);
}

function onHost(ws, msg) {
  if (!EDITIONS.has(msg.edition)) return send(ws, { t: 'error', error: 'bad_edition' });
  const name = String(msg.name || 'Мир').slice(0, 48);
  let room = null;
  if (msg.resume && msg.code) {
    const r = rooms.get(normCode(msg.code));
    if (r && !r.host && r.token === String(msg.resume)) room = r;
  }
  if (room) {
    clearTimeout(room.resumeTimer);
    room.host = ws;
    room.name = name;
    for (const j of room.joiners) send(j.ws, { t: 'host_back' });
  } else {
    if (rooms.size >= CFG.maxRooms) return send(ws, { t: 'error', error: 'busy' });
    const code = makeCode();
    if (!code) return send(ws, { t: 'error', error: 'busy' });
    room = { code, token: crypto.randomBytes(16).toString('hex'), edition: msg.edition, name, host: ws,
      joiners: new Set(), streams: new Map(), tokens: CFG.roomBytesPerSec, tokenTs: Date.now(), bytes: 0,
      created: Date.now(), closed: false };
    rooms.set(code, room);
    stats.roomsCreated++;
    log('room', code, msg.edition);
  }
  ws._role = 'host';
  ws._room = room;
  send(ws, { t: 'room', code: room.code, token: room.token, v: PROTO,
    limits: { joiners: CFG.maxJoiners, streams: CFG.maxStreams, bytesPerSec: CFG.roomBytesPerSec } });
}

function onJoin(ws, msg) {
  const ip = ws._ip;
  const now = Date.now();
  const list = (ipJoins.get(ip) || []).filter((t) => now - t < 60_000);
  list.push(now);
  ipJoins.set(ip, list);
  if (list.length > CFG.joinPerMin) return send(ws, { t: 'error', error: 'rate' });
  const room = rooms.get(normCode(msg.code));
  if (!room) return send(ws, { t: 'error', error: 'not_found' });
  if (room.joiners.size >= CFG.maxJoiners) return send(ws, { t: 'error', error: 'full' });
  const j = { ws, room, streams: new Map(), id: crypto.randomBytes(3).toString('hex') };
  room.joiners.add(j);
  ws._role = 'joiner';
  ws._joiner = j;
  stats.joins++;
  send(ws, { t: 'joined', v: PROTO, code: room.code, edition: room.edition, name: room.name, host: !!room.host });
}

function joinerOpen(j, msg) {
  const room = j.room;
  const lsid = msg.sid >>> 0;
  if (!room.host) return send(j.ws, { t: 'close', sid: lsid, error: 'host_gone' });
  if (j.streams.size >= CFG.maxStreams || j.streams.has(lsid)) return send(j.ws, { t: 'close', sid: lsid, error: 'limit' });
  if (msg.kind !== 'tcp' && msg.kind !== 'udp') return send(j.ws, { t: 'close', sid: lsid, error: 'bad_kind' });
  nextSid = (nextSid % 0xFFFFFFF0) + 1;
  const hsid = nextSid;
  const s = { hsid, lsid, joiner: j, kind: msg.kind };
  j.streams.set(lsid, s);
  room.streams.set(hsid, s);
  send(room.host, { t: 'open', sid: hsid, kind: msg.kind, peer: j.id });
}

function joinerClose(j, lsid) {
  const s = j.streams.get(lsid);
  if (!s) return;
  j.streams.delete(lsid);
  j.room.streams.delete(s.hsid);
  send(j.room.host, { t: 'close', sid: s.hsid });
}

function dropJoiner(j) {
  for (const lsid of [...j.streams.keys()]) joinerClose(j, lsid);
  j.room.joiners.delete(j);
}

function onText(ws, text) {
  let msg;
  try { msg = JSON.parse(text); } catch { return ws.close(4002, 'bad json'); }
  if (!msg || typeof msg.t !== 'string') return;
  if (msg.t === 'ping') return send(ws, { t: 'pong', ts: msg.ts });
  if (!ws._role) {
    if (Number(msg.v || 1) > PROTO) return send(ws, { t: 'error', error: 'bad_version', v: PROTO });
    if (msg.t === 'host') return onHost(ws, msg);
    if (msg.t === 'join') return onJoin(ws, msg);
    return;
  }
  if (ws._role === 'joiner') {
    const j = ws._joiner;
    if (msg.t === 'open') return joinerOpen(j, msg);
    if (msg.t === 'close') return joinerClose(j, msg.sid >>> 0);
  } else if (ws._role === 'host') {
    const room = ws._room;
    if (msg.t === 'close') {
      const s = room.streams.get(msg.sid >>> 0);
      if (!s) return;
      room.streams.delete(s.hsid);
      s.joiner.streams.delete(s.lsid);
      send(s.joiner.ws, { t: 'close', sid: s.lsid });
    } else if (msg.t === 'end') closeRoom(room, 'host_ended');
  }
}

function onBinary(ws, data) {
  if (data.length < 4) return;
  const sid = data.readUInt32BE(0);
  if (ws._role === 'joiner') {
    const j = ws._joiner;
    const s = j.streams.get(sid);
    if (!s || !j.room.host) return;
    data.writeUInt32BE(s.hsid, 0);
    forward(j.room, ws, j.room.host, data);
  } else if (ws._role === 'host') {
    const room = ws._room;
    const s = room.streams.get(sid);
    if (!s) return;
    data.writeUInt32BE(s.lsid, 0);
    forward(room, ws, s.joiner.ws, data);
  }
}

const server = http.createServer((req, res) => {
  if (req.url === '/healthz' || req.url === '/') {
    let joiners = 0;
    for (const r of rooms.values()) joiners += r.joiners.size;
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ ok: true, app: 'igraem-vmeste-relay', v: PROTO, rooms: rooms.size, joiners,
      uptime: Math.round((Date.now() - stats.started) / 1000) }));
  }
  res.writeHead(404); res.end();
});

const wss = new WebSocketServer({ server, path: '/ws', maxPayload: CFG.maxFrame, perMessageDeflate: false });

wss.on('connection', (ws, req) => {
  const fwd = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  ws._ip = fwd || req.socket.remoteAddress || '?';
  const n = (ipConns.get(ws._ip) || 0) + 1;
  if (n > CFG.maxConnPerIp) { ws.close(4003, 'too many connections'); return; }
  ipConns.set(ws._ip, n);
  ws._seen = Date.now();
  ws.on('message', (data, isBinary) => {
    ws._seen = Date.now();
    if (isBinary) onBinary(ws, data); else onText(ws, data.toString());
  });
  ws.on('pong', () => { ws._seen = Date.now(); });
  ws.on('close', () => {
    const c = (ipConns.get(ws._ip) || 1) - 1;
    if (c <= 0) ipConns.delete(ws._ip); else ipConns.set(ws._ip, c);
    if (ws._role === 'host' && ws._room && ws._room.host === ws && !ws._room.closed) hostGone(ws._room);
    if (ws._role === 'joiner') dropJoiner(ws._joiner);
  });
  ws.on('error', () => {});
});

setInterval(() => {
  const now = Date.now();
  for (const ws of wss.clients) {
    if (now - ws._seen > CFG.idleMs) { ws.terminate(); continue; }
    if (ws.readyState === 1) ws.ping();
  }
  for (const r of rooms.values()) if (now - r.created > CFG.roomMaxMs) closeRoom(r, 'expired');
  for (const [ip, l] of ipJoins) if (!l.length || now - l[l.length - 1] > 60_000) ipJoins.delete(ip);
}, 15_000).unref();

server.listen(CFG.port, () => log(`relay listening on :${CFG.port} path /ws`));

function shutdown() { for (const r of [...rooms.values()]) closeRoom(r, 'server_restart'); server.close(); setTimeout(() => process.exit(0), 300).unref(); }
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

module.exports = { server, rooms, CFG, normCode, ALPHABET };
