'use strict';
// Relay LANTERN (by Yompix): комнаты Minecraft поверх WebSocket.
// Хост держит одно WS-соединение, каждый друг — своё. Relay пересылает бинарные кадры
// [u32 sid][payload] между другом и хостом, переводя номера потоков. Содержимое кадров не читается и не пишется в логи.
// Протокол: godot/playtogether/docs/PROTOCOL.md

const http = require('http');
const crypto = require('crypto');
const path = require('path');
const { WebSocketServer } = require('ws');
const { Social } = require('./social');

const env = (k, d) => (process.env[k] !== undefined ? Number(process.env[k]) : d);
const CFG = {
  port: env('PORT', 8080),
  maxRooms: env('MAX_ROOMS', 300),
  maxJoiners: env('MAX_JOINERS', 20),          // друзей в одной комнате
  maxStreams: env('MAX_STREAMS', 64),          // потоков у одного друга
  maxConnPerIp: env('MAX_CONN_PER_IP', 40),
  joinPerMin: env('JOIN_PER_MIN', 30),         // попыток входа по коду с одного IP в минуту
  roomBytesPerSec: env('ROOM_BYTES_PER_SEC', 8 * 1024 * 1024),   // 1.8: было 4 МБ/с — вход в мир с дальней прорисовкой упирался в лимит
  maxFrame: env('MAX_FRAME', 64 * 1024 + 4),
  idleMs: env('IDLE_MS', 90_000),              // нет ни данных, ни ping — закрываем
  resumeMs: env('RESUME_MS', 5 * 60_000),      // код держится, пока хост переподключается
  roomMaxMs: env('ROOM_MAX_MS', 24 * 3600_000),
  highWater: 8 * 1024 * 1024,
  dropWater: 24 * 1024 * 1024,                 // друг не успевает забирать данные — отключаем его (он переподключится сам)
};
const PROTO = 1;
const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ'; // без 0/O, 1/I/L
const EDITIONS = new Set(['java', 'bedrock', 'both']);

const rooms = new Map();      // code -> room
const roomNums = new Map();   // номер комнаты (8 цифр, показываем в списке игр) -> room
const MODES = ['survival', 'creative', 'minigame', 'pvp', 'roleplay', 'other'];
const ipConns = new Map();    // ip -> count
const ipJoins = new Map();    // ip -> [timestamps]
let nextSid = 1;
let stats = { started: Date.now(), roomsCreated: 0, joins: 0 };
const { Economy, ECO_CMDS } = require('./economy');
let social = null;            // создаётся в attach(): путь к базе задаёт тот, кто запускает relay
let wss = null;

const { Pro, PRO_CMDS } = require('./pro');
const { Downloads } = require('./download');
const { Reports } = require('./reports');
const fs = require('fs');
const legal = require('./legal');
const joinPage = require('./join_page');
let downloads = new Downloads('');
let reports = new Reports('');
// 1.6: секрет комнат — токен возврата = HMAC(код|номер). После перезапуска relay (выкладка на Railway) хозяин
// возвращается с прежним кодом, даже если новый процесс о комнате не знал. Друзья переподключаются сами.
let roomSecret = crypto.randomBytes(32);
function roomToken(code, num) { return crypto.createHmac('sha256', roomSecret).update(code + '|' + num).digest('hex').slice(0, 32); }
let publicBase = '';

function makeSocial(dbPath, opts = {}) {
  const s = new Social({ dbPath });
  s.bindRooms(rooms);
  s.eco = new Economy(s);
  s.ecoCmds = ECO_CMDS;
  s.pro = new Pro(s, rooms, { cloudDir: opts.cloudDir, totalMb: opts.cloudTotalMb });
  s.proCmds = PRO_CMDS;
  return s;
}
const SOCIAL_CMDS = new Set(['hello', 'register', 'presence', 'friend_add', 'friend_accept', 'friend_decline', 'friend_remove',
  'block', 'unblock', 'report', 'dm', 'history', 'invite', 'room_enter', 'room_chat', 'rooms', 'state', 'delete_account']);

function log(...a) { if (process.env.QUIET !== '1') console.log(new Date().toISOString(), ...a); }

function makeCode() {
  for (let tries = 0; tries < 50; tries++) {
    const b = crypto.randomBytes(6);
    let s = '';
    for (let i = 0; i < 6; i++) s += ALPHABET[b[i] % ALPHABET.length];
    if (!rooms.has(s) && !social.pro.reserved(s)) return s;
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
  if (dst._role === 'joiner' && dst.bufferedAmount > CFG.dropWater) {
    // друг завис (телефон потерял сеть, а сокет ещё не закрыт): не тормозим из-за него хозяина и остальных
    log('slow joiner dropped', room.code, dst.bufferedAmount);
    dst.terminate();
    return;
  }
  const now = Date.now();
  room.tokens = Math.min(CFG.roomBytesPerSec, room.tokens + (now - room.tokenTs) * CFG.roomBytesPerSec / 1000);
  room.tokenTs = now;
  room.tokens -= data.length;
  room.bytes += data.length;
  dst.send(data);
  if (room.tokens < 0 || (dst.bufferedAmount > CFG.highWater && src._role === 'joiner')) throttle(src, dst, room);
}

function throttle(src, dst, room) {
  if (src._paused || !src._socket) return;
  src._paused = true;
  src._socket.pause();
  const check = () => {
    if (src.readyState !== 1) return;
    const now = Date.now();
    src._seen = now; // пока мы сами не читаем сокет, пинги не доходят — это не «молчание» клиента
    room.tokens = Math.min(CFG.roomBytesPerSec, room.tokens + (now - room.tokenTs) * CFG.roomBytesPerSec / 1000);
    room.tokenTs = now;
    if (room.tokens >= 0 && (!dst || dst.readyState !== 1 || dst.bufferedAmount < CFG.highWater / 2)) {
      src._paused = false;
      src._socket.resume();
    } else setTimeout(check, 4);
  };
  setTimeout(check, 4);   // 1.8: было 25 мс — поток шёл рывками
}

// ---- 1.8: «Прямое соединение по Wi-Fi». Адреса устройств через relay НЕ ходят вообще: relay только выдаёт другу
// и хозяину общий одноразовый ключ и номера портов (если у обоих один внешний IP — вероятно, один роутер).
// Друг ищет хозяина широковещательной пробой в своей домашней сети; ответить на неё может только хозяин с этим ключом.
// Игрок из другой сети (даже за тем же NAT оператора) до хозяина пробой не дотянется и ничего о нём не узнает.
const port = (p) => { const n = Number(p) >>> 0; return n > 0 && n < 65536 ? n : 0; };
function cleanLan(l) {
  if (!l || typeof l !== 'object' || process.env.LANTERN_LAN === '0') return null;
  const o = { tcp: port(l.tcp), udp: port(l.udp), probe: port(l.probe) };
  return o.probe && (o.tcp || o.udp) ? o : null;
}
function lanOffer(j) {
  const room = j.room, ws = j.ws;
  if (!j.lanWant || !room.lan || !room.host || !ws._netIp || ws._netIp !== room.host._netIp) return null;
  const l = { tcp: room.lan.tcp, udp: room.lan.udp, probe: room.lan.probe, k: crypto.randomBytes(16).toString('hex') };
  send(room.host, { t: 'lan', peer: j.id, k: l.k });
  return l;
}

function closeRoom(room, why) {
  if (room.closed) return;
  room.closed = true;
  rooms.delete(room.code);
  if (roomNums.get(room.num) === room) roomNums.delete(room.num);
  social.roomClosed(room.code);
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
  for (const [hsid, s] of room.streams) { send(s.joiner.ws, { t: 'close', sid: s.lsid, lost: true }); s.joiner.streams.delete(s.lsid); }
  room.streams.clear();
  for (const j of room.joiners) send(j.ws, { t: 'host_gone' });
  room.resumeTimer = setTimeout(() => closeRoom(room, 'host_left'), CFG.resumeMs);
}

// Настройки комнаты (1.5): режим, места, пароль, размер мира. Старые клиенты их не шлют — всё по умолчанию.
function applySettings(room, s) {
  if (!s || typeof s !== 'object') return;
  if (MODES.includes(s.mode)) room.mode = s.mode;
  if (s.cap !== undefined) room.cap = Math.max(2, Math.min(CFG.maxJoiners + 1, s.cap >>> 0 || 6));
  if (s.password !== undefined) {
    const p = String(s.password || '').slice(0, 32);
    room.passHash = p ? crypto.createHash('sha256').update('lantern-room:' + p).digest('hex') : '';
  }
  if (s.size_mb !== undefined) room.sizeMb = Math.max(0, Math.min(100000, Number(s.size_mb) || 0));
}

function makeNum() {
  for (let i = 0; i < 50; i++) {
    const n = String(10000000 + crypto.randomInt(90000000));
    if (!roomNums.has(n)) return n;
  }
  return '';
}

// Качество связи хозяина с relay: по пингу WebSocket (ping → pong), без участия клиента
function quality(ms) { return !ms ? '' : ms < 90 ? 'great' : ms < 200 ? 'good' : 'poor'; }

function onHost(ws, msg) {
  if (!EDITIONS.has(msg.edition)) return send(ws, { t: 'error', error: 'bad_edition' });
  const name = String(msg.name || 'Мир').slice(0, 48);
  let room = null;
  const owner = social.userByToken(msg.token);
  if (msg.resume && msg.code) {
    const c = normCode(msg.code);
    const r = rooms.get(c);
    if (r && r.token === String(msg.resume)) {
      if (r.host && r.host !== ws) { const old = r.host; r.host = null; old._role = ''; old.close(4004, 'replaced'); hostGone(r); }
      room = r;
    } else if (!r && /^[2-9A-Z]{3,8}$/.test(c)) {
      // relay перезапустился: комнату помнит только хозяин — проверяем подпись и открываем её заново с тем же кодом
      const num = /^\d{8}$/.test(String(msg.num || '')) && !roomNums.has(String(msg.num)) ? String(msg.num) : '';
      const want = roomToken(c, String(msg.num || ''));
      if (String(msg.resume).length === want.length && crypto.timingSafeEqual(Buffer.from(String(msg.resume)), Buffer.from(want)) && rooms.size < CFG.maxRooms) {
        room = newRoom(c, msg, owner, num);
        log('room restored', c);
      }
    }
  }
  if (room) {
    clearTimeout(room.resumeTimer);
    room.resumeTimer = null;
    room.host = ws;
    room.name = name;
    room.meta = Social.roomMeta(msg.public);
    room.lan = cleanLan(msg.lan);
    applySettings(room, msg.settings);
    for (const j of room.joiners) { const l = lanOffer(j); send(j.ws, l ? { t: 'host_back', lan: l } : { t: 'host_back' }); }
    setBans(room, msg.bans);
  } else {
    if (rooms.size >= CFG.maxRooms) return send(ws, { t: 'error', error: 'busy' });
    // LANTERN Pro: свой код комнаты (если просили, Pro активна и код сейчас не занят открытой комнатой)
    const vanity = msg.vanity && owner ? social.pro.codeForHost(owner.id) : '';
    const code = vanity || makeCode();
    if (!code) return send(ws, { t: 'error', error: 'busy' });
    room = newRoom(code, msg, owner, '');
    room.host = ws;
    stats.roomsCreated++;
    if (owner) {
      social.eco.addStat(owner.id, 'hosts', 1);
      social.eco.addStat(owner.id, 'hosts_' + msg.edition, 1);
      social.eco.checkAchievements(owner.id);
    }
    log('room', code, msg.edition);
  }
  ws._role = 'host';
  ws._room = room;
  send(ws, { t: 'room', code: room.code, num: room.num, token: room.token, v: PROTO, vanity: !!(msg.vanity && owner && social.pro.vanityOf(owner.id) === room.code),
    limits: { joiners: CFG.maxJoiners, streams: CFG.maxStreams, bytesPerSec: CFG.roomBytesPerSec } });
  // кто уже в комнате (после переподключения хозяина)
  for (const j of room.joiners) send(ws, peerMsg(j, true));
}

function newRoom(code, msg, owner, num) {
  const room = { code, edition: msg.edition, name: String(msg.name || 'Мир').slice(0, 48), host: null,
    joiners: new Set(), streams: new Map(), tokens: CFG.roomBytesPerSec, tokenTs: Date.now(), bytes: 0,
    created: Date.now(), closed: false, ownerId: owner ? owner.id : 0, meta: Social.roomMeta(msg.public),
    bans: new Set(), banIps: new Set() };
  setBans(room, msg.bans);
  room.num = num || makeNum();
  room.token = roomToken(code, room.num);
  room.mode = 'other'; room.cap = 0; room.passHash = ''; room.sizeMb = 0;
  room.lan = cleanLan(msg.lan);
  applySettings(room, msg.settings);
  rooms.set(code, room);
  if (room.num) roomNums.set(room.num, room);
  return room;
}

// Хозяин мира: кто зашёл/вышел, выгнать, закрыть вход (по аккаунту LANTERN; без аккаунта — по IP, только в этой комнате)
function setBans(room, list) {
  if (!Array.isArray(list)) return;
  room.bans = new Set(list.slice(0, 500).map((x) => Number(x) >>> 0).filter((x) => x > 0));
}

function peerMsg(j, on) {
  const u = on && j.userId ? social.pub(social.user(j.userId)) : null;
  return { t: 'peer', peer: j.id, on, user: u ? { id: u.id, nick: u.nick, tag: u.tag } : null };
}

function kickPeer(room, msg) {
  const j = [...room.joiners].find((x) => x.id === String(msg.peer || ''));
  if (!j) return;
  if (msg.ban) {
    if (j.userId) room.bans.add(j.userId); else room.banIps.add(j.ws._ip);
  }
  send(j.ws, { t: 'error', error: msg.ban ? 'banned_by_host' : 'kicked' });
  dropJoiner(j);
  j.ws.close(4000, msg.ban ? 'banned_by_host' : 'kicked'); // причина и в коде закрытия: сообщение могло не успеть
}

function onJoin(ws, msg) {
  const ip = ws._ip;
  const now = Date.now();
  const list = (ipJoins.get(ip) || []).filter((t) => now - t < 60_000);
  list.push(now);
  ipJoins.set(ip, list);
  if (list.length > CFG.joinPerMin) return send(ws, { t: 'error', error: 'rate' });
  const nc = normCode(msg.code);
  const room = /^\d{8}$/.test(nc) ? roomNums.get(nc) : rooms.get(nc);
  if (!room) return send(ws, { t: 'error', error: 'not_found' });
  const ju = social.userByToken(msg.token);
  const owner = ju && room.ownerId === ju.id;
  // места: cap — всего игроков вместе с хозяином
  if (room.joiners.size >= CFG.maxJoiners || (room.cap && !owner && room.joiners.size + 1 >= room.cap)) return send(ws, { t: 'error', error: 'full' });
  if ((ju && room.bans && room.bans.has(ju.id)) || (room.banIps && room.banIps.has(ip))) return send(ws, { t: 'error', error: 'banned_by_host' });
  if (room.passHash && !owner) {
    const p = String(msg.password || '');
    if (!p) return send(ws, { t: 'error', error: 'need_password' });
    const h = crypto.createHash('sha256').update('lantern-room:' + p.slice(0, 32)).digest('hex');
    if (!crypto.timingSafeEqual(Buffer.from(h), Buffer.from(room.passHash))) return send(ws, { t: 'error', error: 'bad_password' });
  }
  // 1.6: друг переподключился (обрыв связи, перезапуск relay) — прежний id, чтобы хозяин не видел «вышел/зашёл»
  const prev = /^[0-9a-f]{6}$/.test(String(msg.peer || '')) && ![...room.joiners].some((x) => x.id === msg.peer) ? String(msg.peer) : '';
  const j = { ws, room, streams: new Map(), id: prev || crypto.randomBytes(3).toString('hex'), userId: ju ? ju.id : 0 };
  room.joiners.add(j);
  ws._role = 'joiner';
  ws._joiner = j;
  stats.joins++;
  if (j.userId) {
    social.eco.addStat(j.userId, 'joins', 1);
    if (room.meta && room.ownerId !== j.userId) social.eco.event(j.userId, 'join_public', 1);
    social.eco.visit(room.ownerId, j.userId);
    social.eco.checkAchievements(j.userId);
  }
  const jo = { t: 'joined', v: PROTO, code: room.code, num: room.num, edition: room.edition, name: room.name, host: !!room.host, peer: j.id };
  send(room.host, peerMsg(j, true));
  j.lanWant = !!msg.lan;
  const lan = lanOffer(j);
  if (lan) jo.lan = lan;
  send(ws, jo);
}

function likeRoom(ws) {
  const j = ws._joiner;
  if (!j.userId) return send(ws, { t: 'like', error: 'Нужен профиль' });
  const e = social.eco.like(j.room.ownerId, j.userId);
  if (!e) { j.room.likes = j.room.likes || new Set(); j.room.likes.add(j.userId); }
  send(ws, { t: 'like', error: e });
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
  const o = { t: 'open', sid: hsid, kind: msg.kind, peer: j.id };
  // 1.6: ключ потока — после обрыва связи друг открывает поток заново с тем же ключом, хозяин продолжает прежний
  if (typeof msg.key === 'string' && /^[0-9a-f]{8,32}$/.test(msg.key)) { o.key = msg.key; o.rx = Number(msg.rx) || 0; if (msg.resume) o.resume = 1; }
  send(room.host, o);
}

function joinerClose(j, lsid, lost) {
  const s = j.streams.get(lsid);
  if (!s) return;
  j.streams.delete(lsid);
  j.room.streams.delete(s.hsid);
  send(j.room.host, lost ? { t: 'close', sid: s.hsid, lost: true } : { t: 'close', sid: s.hsid });
}

// Подтверждение «получено n байт» (и ответ на возобновление потока) — пересылаем другой стороне, меняя номер потока.
function ackMsg(sid, msg) {
  const o = { t: 'ack', sid, n: Number(msg.n) || 0 };
  if (msg.r) o.r = 1;
  return o;
}

function dropJoiner(j, lost) {
  if (!j.room.joiners.has(j)) return;
  for (const lsid of [...j.streams.keys()]) joinerClose(j, lsid, lost);
  j.room.joiners.delete(j);
  send(j.room.host, peerMsg(j, false));
}

function onText(ws, text) {
  let msg;
  try { msg = JSON.parse(text); } catch { return ws.close(4002, 'bad json'); }
  if (!msg || typeof msg.t !== 'string') return;
  if (msg.t === 'ping') return send(ws, { t: 'pong', ts: msg.ts });
  if (SOCIAL_CMDS.has(msg.t) || ECO_CMDS.has(msg.t) || PRO_CMDS.has(msg.t)) return social.handle(ws, msg);
  if (msg.t === 'like' && ws._role === 'joiner') return likeRoom(ws);
  if (msg.t === 'room_meta' && ws._role === 'host') {
    if ('public' in msg) ws._room.meta = Social.roomMeta(msg.public);
    applySettings(ws._room, msg.settings);
    return;
  }
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
    if (msg.t === 'ack') { const s = j.streams.get(msg.sid >>> 0); if (s && j.room.host) send(j.room.host, ackMsg(s.hsid, msg)); return; }
    if (msg.t === 'lan_on') { j.direct = !!msg.on; return send(j.room.host, { t: 'lan', peer: j.id, on: j.direct }); }
  } else if (ws._role === 'host') {
    const room = ws._room;
    if (msg.t === 'close') {
      const s = room.streams.get(msg.sid >>> 0);
      if (!s) return;
      room.streams.delete(s.hsid);
      s.joiner.streams.delete(s.lsid);
      send(s.joiner.ws, { t: 'close', sid: s.lsid });
    } else if (msg.t === 'ack') {
      const s = room.streams.get(msg.sid >>> 0);
      if (s) send(s.joiner.ws, ackMsg(s.lsid, msg));
    } else if (msg.t === 'kick') kickPeer(room, msg);
    else if (msg.t === 'bans') setBans(room, msg.bans);
    else if (msg.t === 'end') closeRoom(room, 'host_ended');
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

// HTTP: /healthz, /rooms, /download, /privacy, /terms, /delete (url — уже без префикса)
function onHttp(req, res, url) {
  if (downloads.http(req, res, url, publicBase)) return;
  if (reports.http(req, res, url, String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() || (req.socket && req.socket.remoteAddress))) return;
  if (legal.http(req, res, url, publicBase)) return;
  if (joinPage.http(req, res, url, publicBase, (c) => (/^\d{8}$/.test(c) ? roomNums.get(c) : rooms.get(c)), social)) return;
  if (social.pro.http(req, res, url)) return;
  if (url === '/healthz' || url === '/') {
    let joiners = 0;
    for (const r of rooms.values()) joiners += r.joiners.size;
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    // 1.8: регион выкладки (не секрет) и время сервера — приложение показывает пинг до relay, а в отчётах видно, где он стоит
    return res.end(JSON.stringify({ ok: true, app: 'lantern-relay', v: PROTO, rooms: rooms.size, joiners,
      uptime: Math.round((Date.now() - stats.started) / 1000),
      region: String(process.env.RAILWAY_REPLICA_REGION || process.env.RAILWAY_REGION || '').slice(0, 40), ts: Date.now() }));
  }
  if (url.startsWith('/rooms')) {
    const u = new URL(url, 'http://x');
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ rooms: social.publicRooms({ edition: u.searchParams.get('edition'), tag: u.searchParams.get('tag'), q: u.searchParams.get('q'),
      mode: u.searchParams.get('mode'), version: u.searchParams.get('version') }) }));
  }
  res.writeHead(404); res.end();
}

function onConnection(ws, req) {
  const fwd = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  ws._ip = fwd || req.socket.remoteAddress || '?';
  // для «той же сети» берём адрес, который подставил наш прокси (последний в цепочке), а не то, что прислал клиент
  const chain = String(req.headers['x-forwarded-for'] || '').split(',').map((x) => x.trim()).filter(Boolean);
  ws._netIp = String(req.headers['x-real-ip'] || '').trim() || chain[chain.length - 1] || req.socket.remoteAddress || '';
  if (req.socket && req.socket.setNoDelay) req.socket.setNoDelay(true);
  const n = (ipConns.get(ws._ip) || 0) + 1;
  if (n > CFG.maxConnPerIp) { ws.close(4003, 'too many connections'); return; }
  ipConns.set(ws._ip, n);
  ws._seen = Date.now();
  ws.on('message', (data, isBinary) => {
    ws._seen = Date.now();
    if (isBinary) onBinary(ws, data); else onText(ws, data.toString());
  });
  ws.on('pong', () => {
    ws._seen = Date.now();
    if (ws._pingAt) {
      const ms = ws._seen - ws._pingAt; ws._rtt = ws._rtt ? Math.round(ws._rtt * 0.6 + ms * 0.4) : ms; ws._pingAt = 0;
      if (ws._role === 'host') send(ws, { t: 'net', ping: ws._rtt, quality: quality(ws._rtt) });
    }
  });
  ws.on('close', () => {
    const c = (ipConns.get(ws._ip) || 1) - 1;
    if (c <= 0) ipConns.delete(ws._ip); else ipConns.set(ws._ip, c);
    if (ws._role === 'host' && ws._room && ws._room.host === ws && !ws._room.closed) hostGone(ws._room);
    if (ws._role === 'joiner') dropJoiner(ws._joiner, true);
    social.detach(ws);
  });
  ws.on('error', () => {});
}

// Секрет комнат: переменная LANTERN_ROOM_SECRET, иначе файл рядом с базой (на томе Railway), иначе случайный.
function loadSecret(opts) {
  if (process.env.LANTERN_ROOM_SECRET) return crypto.createHash('sha256').update(process.env.LANTERN_ROOM_SECRET).digest();
  const db = opts.dbPath && opts.dbPath !== ':memory:' ? opts.dbPath : '';
  if (!db) return crypto.randomBytes(32);
  const f = path.join(path.dirname(db), 'lantern-room.key');
  try { const b = fs.readFileSync(f); if (b.length === 32) return b; } catch {}
  const b = crypto.randomBytes(32);
  try { fs.writeFileSync(f, b, { mode: 0o600 }); } catch {}
  return b;
}

/**
 * Подключить relay к уже работающему HTTP-серверу (например, к сайту Office Dreams).
 * Забирает только WebSocket-запросы на opts.wsPath; остальные upgrade-запросы не трогает.
 * Возвращает { http(req, res, url), close() } — http отвечает на /healthz и /rooms.
 */
function attach(httpServer, opts = {}) {
  if (wss) throw new Error('relay already attached');
  const wsPath = opts.wsPath || '/ws';
  social = makeSocial(opts.dbPath || ':memory:', opts);
  downloads = new Downloads(opts.downloadDir || '');
  reports = new Reports(opts.reportDir || '');
  roomSecret = loadSecret(opts);
  publicBase = opts.publicBase || '';
  wss = new WebSocketServer({ noServer: true, maxPayload: CFG.maxFrame, perMessageDeflate: false });
  wss.on('connection', onConnection);
  httpServer.on('upgrade', (req, socket, head) => {
    const p = String(req.url || '').split('?')[0];
    if (p !== wsPath) return; // чужой путь — не наше дело
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, req));
  });
  const t1 = setInterval(() => {
    const now = Date.now();
    for (const ws of wss.clients) {
      if (now - ws._seen > CFG.idleMs) { log('idle terminate', ws._role || '-', ws._room ? ws._room.code : ''); ws.terminate(); continue; }
      if (ws.readyState === 1) { ws._pingAt = Date.now(); ws.ping(); }
    }
    for (const r of rooms.values()) if (now - r.created > CFG.roomMaxMs) closeRoom(r, 'expired');
    for (const [ip, l] of ipJoins) if (!l.length || now - l[l.length - 1] > 60_000) ipJoins.delete(ip);
  }, 15_000);
  t1.unref();
  const t2 = setInterval(() => social.tickPlaytime(), 60_000);
  t2.unref();
  log(`lantern relay attached, ws path ${wsPath}, db ${opts.dbPath ? 'file' : 'memory'}`);
  return {
    http: onHttp,
    // 1.6: при перезапуске (выкладка) комнаты не закрываем — хозяева и друзья переподключатся к новому процессу
    // и продолжат с тем же кодом (код 1012 = «сервис перезапускается»).
    close() { clearInterval(t1); clearInterval(t2); for (const ws of wss.clients) { try { ws.close(1012, 'restart'); } catch {} } },
  };
}

/** Отдельный сервер relay (свой сервис на Railway или тесты). */
function standalone(port = CFG.port) {
  const vol = process.env.RAILWAY_VOLUME_MOUNT_PATH;
  const dbPath = process.env.LANTERN_DB_PATH || process.env.DB_PATH || (vol ? path.join(vol, 'lantern.db') : ':memory:');
  let relay = null;
  const server = http.createServer((req, res) => relay.http(req, res, req.url));
  relay = attach(server, { wsPath: '/ws', dbPath, reportDir: process.env.LANTERN_REPORT_DIR || (vol ? path.join(vol, 'lantern-reports') : '') });
  server.listen(port, () => log(`relay listening on :${port} path /ws`));
  return { server, social, relay };
}

if (require.main === module) {
  const { server, relay } = standalone();
  const shutdown = () => { relay.close(); server.close(); setTimeout(() => process.exit(0), 300).unref(); };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

module.exports = { attach, standalone, rooms, roomNums, quality, MODES, CFG, normCode, ALPHABET, get social() { return social; } };
