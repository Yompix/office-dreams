'use strict';
// LANTERN 2.0 «Любые игры»: виртуальная локальная сеть поверх relay (как Hamachi / Radmin VPN / ZeroTier).
// Каждый участник сети получает адрес 10.88.<O>.<n>/24 (O — свой для каждой сети). Приложение ставит виртуальный
// адаптер (Windows — Wintun через помощник lantern-net.exe, Android — VpnService, Linux — /dev/net/tun) и шлёт сюда
// сырые IPv4-пакеты бинарными кадрами WebSocket. Relay читает ТОЛЬКО заголовок IPv4 и пересылает пакет:
//   * адресату внутри этой же сети (10.88.O.n — только существующему участнику);
//   * всем остальным участникам — широковещательные 10.88.O.255 / 255.255.255.255 и групповые 224.0.0.0/4
//     (так игры находят друг друга в «LAN»).
// Всё остальное (интернет, чужие сети, подделанный адрес отправителя, IPv6) отбрасывается: relay — не прокси в интернет.
// Прямое соединение (P2P): relay только передаёт между участниками одной сети «сигналы» (их адреса для пробоя NAT)
// и общий ключ сети для подписи пакетов. Протокол: godot/docs/VLAN.md.

const crypto = require('crypto');

const env = (k, d) => (process.env[k] !== undefined ? Number(process.env[k]) : d);
const VCFG = {
  maxNets: env('VNET_MAX', 300),
  maxMembers: env('VNET_MAX_MEMBERS', 16),       // участников в сети (Plus — до 32, см. plusMembers)
  plusMembers: env('VNET_PLUS_MEMBERS', 32),
  maxPkt: 1500,                                   // MTU адаптера 1380 + запас; больше — отбрасываем
  memberBps: env('VNET_MEMBER_BPS', 4 * 1024 * 1024),   // байт/с от одного участника
  memberPps: env('VNET_MEMBER_PPS', 4000),        // пакетов/с от одного участника
  bcastPps: env('VNET_BCAST_PPS', 80),            // широковещательных пакетов/с от одного участника (их размножаем на всех)
  netBps: env('VNET_NET_BPS', 16 * 1024 * 1024),  // байт/с на всю сеть (после размножения)
  sigPerMin: 120, chatPerMin: 30, pingPerSec: 8, listPerMin: 30,
  resumeMs: env('VNET_RESUME_MS', 5 * 60_000),    // сеть ждёт, пока кто-нибудь вернётся (перезапуск relay, обрыв)
  netMaxMs: env('VNET_MAX_MS', 24 * 3600_000),
  dropWater: 4 * 1024 * 1024,                     // получатель не забирает данные — пакеты ему просто теряются (как в сети)
};
const NET_PREFIX = [10, 88];
const REACTIONS = new Set(['👍', '❤️', '😂', '🔥', '🎮', '👋', '😮', '🎉']);
const { PIXEL_REACTIONS } = require('./economy');
const GAMES = /^[a-z0-9_]{1,32}$/;

function ipStr(b, o = 0) { return b[o] + '.' + b[o + 1] + '.' + b[o + 2] + '.' + b[o + 3]; }
function cleanName(s, d) {
  const t = String(s || '').replace(/[\u0000-\u001f\u007f<>]/g, '').trim().slice(0, 32);
  return t || d;
}

// Разбор заголовка IPv4: { ok, src:[4], dst:[4], kind:'unicast'|'bcast'|'mcast'|'drop', why }
function classify(buf, octet) {
  if (buf.length < 20 || buf.length > VCFG.maxPkt) return { ok: false, why: 'size' };
  const v = buf[0] >> 4, ihl = (buf[0] & 15) * 4;
  if (v !== 4) return { ok: false, why: 'not_ipv4' };
  if (ihl < 20 || ihl > buf.length) return { ok: false, why: 'header' };
  const total = buf.readUInt16BE(2);
  if (total < ihl || total > buf.length) return { ok: false, why: 'length' };
  const src = buf.subarray(12, 16), dst = buf.subarray(16, 20);
  if (dst[0] === 255 && dst[1] === 255 && dst[2] === 255 && dst[3] === 255) return { ok: true, src, dst, kind: 'bcast' };
  if (dst[0] >= 224 && dst[0] <= 239) return { ok: true, src, dst, kind: 'mcast' };
  if (dst[0] === NET_PREFIX[0] && dst[1] === NET_PREFIX[1] && dst[2] === octet) {
    if (dst[3] === 255) return { ok: true, src, dst, kind: 'bcast' };
    if (dst[3] === 0) return { ok: false, why: 'net_addr' };
    return { ok: true, src, dst, kind: 'unicast' };
  }
  return { ok: false, why: 'outside' };   // интернет и чужие сети — не пересылаем
}

function bucket(rate) { return { tokens: rate, ts: Date.now(), rate }; }
function take(b, n) {
  const now = Date.now();
  b.tokens = Math.min(b.rate, b.tokens + (now - b.ts) * b.rate / 1000);
  b.ts = now;
  if (b.tokens < n) return false;
  b.tokens -= n;
  return true;
}
function slide(list, ms, max) {
  const now = Date.now();
  while (list.length && now - list[0] > ms) list.shift();
  if (list.length >= max) return false;
  list.push(now);
  return true;
}

function createVnet(h) {
  // h: { send, log, social, secret(), codeTaken(c), numTaken(n), makeCode(), makeNum(), joinRate(ip) -> bool }
  const nets = new Map();     // code -> net
  const nums = new Map();     // 8 цифр -> net
  const stats = { created: 0, joins: 0, pkts: 0, bytes: 0, dropped: 0 };

  function token(net) {
    return crypto.createHmac('sha256', h.secret()).update(['vnet', net.code, net.num, net.octet, net.ownerUid, net.ownerPeer].join('|')).digest('hex').slice(0, 32);
  }

  // 2.0.3: пламя участника (неоновый ник и т. п. у всех) — только из проверенной экипировки на сервере
  const eco = () => (h.social && h.social.eco) || null;
  const flameOf = (uid) => (uid && eco() ? eco().flameOf(uid) : '');
  function pubMember(m) { return { peer: m.peer, ip: ipStr(m.ip), name: m.name, uid: m.uid || 0, owner: m.peer === m.net.ownerPeer, ms: m.ws._rtt || 0, plus: !!m.plus, flame: flameOf(m.uid) }; }

  function bcast(net, obj, except) {
    for (const m of net.members.values()) if (m !== except) h.send(m.ws, obj);
  }

  function freeIp(net, want) {
    const used = new Set([...net.members.values()].map((m) => m.ip[3]));
    if (want && want >= 1 && want <= 254 && !used.has(want)) return want;
    for (let i = 1; i <= 254; i++) if (!used.has(i)) return i;
    return 0;
  }

  function newNet(code, num, octet, opts) {
    const net = { code, num, octet, name: cleanName(opts.name, 'Сеть'), game: GAMES.test(String(opts.game || '')) ? String(opts.game) : 'any',
      public: !!opts.public, cap: Math.max(2, Math.min(VCFG.plusMembers, (opts.cap >>> 0) || VCFG.maxMembers)), passHash: '',
      members: new Map(), ownerUid: opts.ownerUid || 0, ownerPeer: opts.ownerPeer, key: crypto.randomBytes(32).toString('hex'),
      bucket: bucket(VCFG.netBps), created: Date.now(), closeTimer: null, bans: new Set(), banIps: new Set(), chatSeq: 0, closed: false };
    if (opts.password) net.passHash = crypto.createHash('sha256').update('lantern-net:' + String(opts.password).slice(0, 32)).digest('hex');
    net.token = token(net);
    nets.set(code, net);
    nums.set(num, net);
    stats.created++;
    return net;
  }

  function closeNet(net, why) {
    if (net.closed) return;
    net.closed = true;
    clearTimeout(net.closeTimer);
    nets.delete(net.code);
    if (nums.get(net.num) === net) nums.delete(net.num);
    for (const m of net.members.values()) {
      h.send(m.ws, { t: 'error', error: why || 'net_closed' });
      m.ws._vm = null; m.ws._role = '';
      try { m.ws.close(4000, 'net closed'); } catch {}
    }
    net.members.clear();
    h.log('vnet closed', net.code, why);
  }

  function capFor(net) {
    const owner = [...net.members.values()].find((m) => m.peer === net.ownerPeer);
    const plusCap = owner && owner.plus ? VCFG.plusMembers : VCFG.maxMembers;
    return Math.min(net.cap, plusCap);
  }

  function admit(ws, net, msg, user, wantPeer, wantIp) {
    const peer = /^[0-9a-f]{6}$/.test(String(wantPeer || '')) && !net.members.has(String(wantPeer)) ? String(wantPeer) : crypto.randomBytes(3).toString('hex');
    const n = freeIp(net, wantIp);
    if (!n) return h.send(ws, { t: 'error', error: 'full' });
    const plus = !!(user && h.social && h.social.pro && h.social.pro.isPro(user.id));
    const m = { ws, net, peer, ip: Buffer.from([NET_PREFIX[0], NET_PREFIX[1], net.octet, n]), uid: user ? user.id : 0,
      name: user ? user.nick + (user.num != null ? '#' + String(user.num).padStart(4, '0') : '') : cleanName(msg.nick, 'Игрок'), plus,   // 2.0.2: в строке БД нет tag — был «#undefined»
      bytes: bucket(VCFG.memberBps), pkts: bucket(VCFG.memberPps), bc: bucket(VCFG.bcastPps),
      sig: [], chat: [], pings: bucket(VCFG.pingPerSec), dropped: 0 };
    if (!net.ownerPeer) net.ownerPeer = peer;
    net.members.set(peer, m);
    clearTimeout(net.closeTimer); net.closeTimer = null;
    ws._role = 'vnet'; ws._vm = m;
    stats.joins++;
    h.send(ws, { t: 'vnet', v: 1, code: net.code, num: net.num, token: net.token, octet: net.octet, ip: ipStr(m.ip), mask: 24,
      net: `${NET_PREFIX[0]}.${NET_PREFIX[1]}.${net.octet}.0`, bcast: `${NET_PREFIX[0]}.${NET_PREFIX[1]}.${net.octet}.255`, mtu: 1380,
      key: net.key, peer, owner: net.ownerPeer, owner_uid: net.ownerUid, name: net.name, game: net.game, public: net.public, cap: capFor(net),
      locked: !!net.passHash, pass_hash: net.passHash, members: [...net.members.values()].map(pubMember) });
    bcast(net, { t: 'vnet_peer', on: true, ...pubMember(m) }, m);
    return m;
  }

  function create(ws, msg) {
    if (nets.size >= VCFG.maxNets) return h.send(ws, { t: 'error', error: 'busy' });
    const user = h.social ? h.social.userByToken(msg.token) : null;
    const code = h.makeCode(), num = h.makeNum();
    if (!code || !num) return h.send(ws, { t: 'error', error: 'busy' });
    const peer = crypto.randomBytes(3).toString('hex');
    const net = newNet(code, num, 1 + crypto.randomInt(254), { ...msg, ownerUid: user ? user.id : 0, ownerPeer: peer });
    h.log('vnet', code, net.game);
    admit(ws, net, msg, user, peer, 1);
  }

  function join(ws, msg) {
    if (!h.joinRate(ws._ip)) return h.send(ws, { t: 'error', error: 'rate' });
    const c = String(msg.code || '').toUpperCase().replace(/[\s-]/g, '');
    let net = /^\d{8}$/.test(c) ? nums.get(c) : nets.get(c);
    const user = h.social ? h.social.userByToken(msg.token) : null;
    if (!net && msg.resume && /^[2-9A-Z]{3,8}$/.test(c) && /^\d{8}$/.test(String(msg.num || '')) && nets.size < VCFG.maxNets) {
      // relay перезапустился: сеть помнят участники. Подпись проверяем — подделать чужую сеть нельзя.
      const octet = msg.octet >>> 0, num = String(msg.num);
      const probe = { code: c, num, octet, ownerUid: msg.owner_uid >>> 0, ownerPeer: String(msg.owner || '') };
      const want = token(probe);
      if (octet >= 1 && octet <= 254 && !h.codeTaken(c) && !h.numTaken(num) && String(msg.resume).length === want.length &&
          crypto.timingSafeEqual(Buffer.from(String(msg.resume)), Buffer.from(want))) {
        net = newNet(c, num, octet, { ...msg, ownerUid: probe.ownerUid, ownerPeer: probe.ownerPeer, password: '' });
        if (msg.pass_hash && /^[0-9a-f]{64}$/.test(String(msg.pass_hash))) net.passHash = String(msg.pass_hash);
        net.token = want;
        h.log('vnet restored', c);
      }
    }
    if (!net || net.closed) return h.send(ws, { t: 'error', error: 'not_found' });
    const isOwner = (user && net.ownerUid && user.id === net.ownerUid) || (msg.peer && msg.peer === net.ownerPeer && msg.resume === net.token);
    if ((user && net.bans.has(user.id)) || net.banIps.has(ws._ip)) return h.send(ws, { t: 'error', error: 'banned_by_host' });
    const back = msg.resume === net.token && /^[0-9a-f]{6}$/.test(String(msg.peer || ''));   // свой, переподключается
    if (!isOwner && !back && net.members.size >= capFor(net)) return h.send(ws, { t: 'error', error: 'full' });
    if (net.passHash && !isOwner && !back) {
      const p = String(msg.password || '');
      if (!p) return h.send(ws, { t: 'error', error: 'need_password' });
      const hh = crypto.createHash('sha256').update('lantern-net:' + p.slice(0, 32)).digest('hex');
      if (!crypto.timingSafeEqual(Buffer.from(hh), Buffer.from(net.passHash))) return h.send(ws, { t: 'error', error: 'bad_password' });
    }
    const wantIp = back ? Number(String(msg.ip || '').split('.')[3]) : 0;
    const old = back ? net.members.get(String(msg.peer)) : null;
    if (old) { old.ws._vm = null; old.ws._role = ''; try { old.ws.close(4004, 'replaced'); } catch {} net.members.delete(old.peer); }
    admit(ws, net, msg, user, back ? msg.peer : '', wantIp);
  }

  function leave(m, lost) {
    const net = m.net;
    if (!net.members.has(m.peer) || net.members.get(m.peer) !== m) return;
    net.members.delete(m.peer);
    m.ws._vm = null;
    bcast(net, { t: 'vnet_peer', on: false, peer: m.peer, ip: ipStr(m.ip), name: m.name, lost: !!lost });
    if (!net.members.size) {
      // пусто: ждём, вдруг вернутся (обрыв связи, перезапуск); потом закрываем
      net.closeTimer = setTimeout(() => closeNet(net, 'empty'), lost ? VCFG.resumeMs : 1000);
      if (net.closeTimer.unref) net.closeTimer.unref();
    }
  }

  function onText(ws, msg) {
    const t = msg.t;
    if (t === 'vnet_list') {
      ws._vlist = ws._vlist || [];
      if (!slide(ws._vlist, 60_000, VCFG.listPerMin)) return h.send(ws, { t: 'vnet_list', nets: [], error: 'rate' });
      return h.send(ws, { t: 'vnet_list', game: msg.game || '', nets: list(msg.game) });
    }
    if (!ws._role) {
      if (t === 'vnet_create') return create(ws, msg);
      if (t === 'vnet_join') return join(ws, msg);
      return;
    }
    const m = ws._vm;
    if (!m || ws._role !== 'vnet') return;
    const net = m.net;
    const owner = m.peer === net.ownerPeer;
    switch (t) {
      case 'vnet_sig': {
        const to = net.members.get(String(msg.to || ''));
        if (!to || to === m || !msg.d || typeof msg.d !== 'object') return;
        if (JSON.stringify(msg.d).length > 1024 || !slide(m.sig, 60_000, VCFG.sigPerMin)) return;
        return h.send(to.ws, { t: 'vnet_sig', from: m.peer, d: msg.d });
      }
      case 'vnet_ping': case 'vnet_pong': {
        const to = net.members.get(String(msg.to || ''));
        if (!to || to === m || !take(m.pings, 1)) return;
        return h.send(to.ws, { t, from: m.peer, ts: Number(msg.ts) || 0, p2p: msg.p2p ? 1 : 0 });
      }
      case 'vnet_chat': {
        const text = String(msg.text || '').replace(/[\u0000-\u0008\u000b-\u001f]/g, '').trim().slice(0, 300);
        if (!text || !slide(m.chat, 60_000, VCFG.chatPerMin)) return;
        const id = ++net.chatSeq;
        return bcast(net, { t: 'vnet_chat', id, from: m.peer, name: m.name, flame: flameOf(m.uid), text, ts: Date.now() });
      }
      case 'vnet_react': {
        // 2.0.3: «Пиксельное пламя» — ещё 4 реакции (проверка по экипировке на сервере)
        const pixel = eco() && eco().flamePerk(m.uid, 'pixel_react') && PIXEL_REACTIONS.includes(String(msg.e));
        if ((!REACTIONS.has(String(msg.e)) && !pixel) || !slide(m.chat, 60_000, VCFG.chatPerMin * 2)) return;
        return bcast(net, { t: 'vnet_react', id: msg.id >>> 0, from: m.peer, e: String(msg.e) });
      }
      case 'vnet_leave': leave(m, false); ws._role = ''; return;
      case 'vnet_kick': {
        if (!owner) return;
        const v = net.members.get(String(msg.peer || ''));
        if (!v || v === m) return;
        if (msg.ban) { if (v.uid) net.bans.add(v.uid); else net.banIps.add(v.ws._ip); }
        h.send(v.ws, { t: 'error', error: msg.ban ? 'banned_by_host' : 'kicked' });
        leave(v, false); v.ws._role = '';
        try { v.ws.close(4000, msg.ban ? 'banned_by_host' : 'kicked'); } catch {}
        return;
      }
      case 'vnet_meta': {
        if (!owner) return;
        if (msg.name !== undefined) net.name = cleanName(msg.name, net.name);
        if (msg.public !== undefined) net.public = !!msg.public;
        if (msg.game !== undefined && GAMES.test(String(msg.game))) net.game = String(msg.game);
        if (msg.password !== undefined) net.passHash = msg.password ? crypto.createHash('sha256').update('lantern-net:' + String(msg.password).slice(0, 32)).digest('hex') : '';
        return bcast(net, { t: 'vnet_meta', name: net.name, public: net.public, game: net.game, locked: !!net.passHash, pass_hash: net.passHash });
      }
      case 'vnet_end': if (owner) closeNet(net, 'host_ended'); return;
    }
  }

  function deliver(net, to, data) {
    if (!to.ws || to.ws.readyState !== 1 || to.ws.bufferedAmount > VCFG.dropWater) return false;
    if (!take(net.bucket, data.length)) return false;
    to.ws.send(data);
    return true;
  }

  function onBinary(ws, data) {
    const m = ws._vm;
    if (!m) return;
    const net = m.net;
    const c = classify(data, net.octet);
    if (!c.ok || !c.src.equals(m.ip) || !take(m.pkts, 1) || !take(m.bytes, data.length)) { m.dropped++; stats.dropped++; return; }
    stats.pkts++; stats.bytes += data.length;
    if (c.kind === 'unicast') {
      for (const x of net.members.values()) if (x !== m && x.ip.equals(c.dst)) { deliver(net, x, data); return; }
      m.dropped++; stats.dropped++;
      return;
    }
    if (!take(m.bc, 1)) { m.dropped++; stats.dropped++; return; }
    for (const x of net.members.values()) if (x !== m) deliver(net, x, data);
  }

  function list(game) {
    const g = GAMES.test(String(game || '')) ? String(game) : '';
    const out = [];
    for (const n of nets.values()) {
      if (!n.public || n.closed || !n.members.size || (g && g !== 'any' && n.game !== g)) continue;
      const owner = n.members.get(n.ownerPeer);
      out.push({ num: n.num, name: n.name, game: n.game, n: n.members.size, cap: capFor(n), locked: !!n.passHash, owner: owner ? owner.name : '', flame: owner ? flameOf(owner.uid) : '' });
      if (out.length >= 50) break;
    }
    return out.sort((a, b) => b.n - a.n);
  }

  // раз в 15 с: пинг каждого участника до relay — всем участникам (для списка и графика связи)
  function tick() {
    const now = Date.now();
    for (const n of nets.values()) {
      if (now - n.created > VCFG.netMaxMs) { closeNet(n, 'expired'); continue; }
      if (!n.members.size) continue;
      const p = {};
      for (const m of n.members.values()) p[m.peer] = m.ws._rtt || 0;
      bcast(n, { t: 'vnet_pings', p });
    }
  }

  function onClose(ws) { if (ws._vm) leave(ws._vm, true); }

  // для страницы /j/<код>: та же форма, что у комнаты Minecraft
  function find(c) {
    const n = /^\d{8}$/.test(c) ? nums.get(c) : nets.get(c);
    if (!n || n.closed) return null;
    return { kind: 'vnet', code: n.code, num: n.num, name: n.name, edition: 'any', game: n.game, closed: false, joiners: { size: Math.max(0, n.members.size - 1) },
      cap: capFor(n), passHash: n.passHash, ownerId: n.ownerUid, meta: { title: n.name }, host: n.members.size > 0, mode: 'other' };
  }

  return { onText, onBinary, onClose, tick, list, find, nets, nums, stats, has: (c) => nets.has(c), hasNum: (n) => nums.has(n), closeAll: () => { for (const n of [...nets.values()]) closeNet(n, 'restart'); } };
}

const VNET_CMDS = new Set(['vnet_create', 'vnet_join', 'vnet_list', 'vnet_sig', 'vnet_ping', 'vnet_pong', 'vnet_chat', 'vnet_react', 'vnet_leave', 'vnet_kick', 'vnet_meta', 'vnet_end']);

module.exports = { createVnet, classify, VNET_CMDS, VCFG, REACTIONS };
