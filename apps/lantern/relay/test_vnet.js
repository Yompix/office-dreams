'use strict';
// 2.0 «Любые игры»: виртуальная сеть через relay. Три участника: создание/вход по номеру и паролю, широковещательный
// и групповой UDP всем, личный пакет одному, отбрасывание подделок/интернета/IPv6/больших пакетов, сигналы P2P,
// пинг, чат и реакции, публичный список, выгнать, восстановление сети после перезапуска relay.
process.env.PORT = process.env.PORT || '18937';
process.env.QUIET = '1';
const assert = require('assert');
const WebSocket = require('ws');
const srv = require('./server');
const { server } = srv.standalone(Number(process.env.PORT));
const { classify } = require('./vnet');
const URL = `ws://127.0.0.1:${process.env.PORT}/ws`;
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.on('open', () => res(w)); w.on('error', rej); });
const next = (w, pred, ms = 3000) => new Promise((res, rej) => {
  const t = setTimeout(() => { w.off('message', h); rej(new Error('timeout')); }, ms);
  const h = (d, bin) => { const m = bin ? d : JSON.parse(d); if (!pred || pred(m, bin)) { clearTimeout(t); w.off('message', h); res(m); } };
  w.on('message', h);
});
const none = (w, pred, ms = 300) => new Promise((res, rej) => {
  const h = (d, bin) => { const m = bin ? d : JSON.parse(d); if (!pred || pred(m, bin)) { w.off('message', h); rej(new Error('unexpected ' + (bin ? 'binary' : JSON.stringify(m)))); } };
  w.on('message', h); setTimeout(() => { w.off('message', h); res(); }, ms);
});
const tx = (w, o) => w.send(JSON.stringify(o));
const ip4 = (s) => s.split('.').map(Number);

// IPv4 + UDP пакет (контрольные суммы не проверяются relay — ставим 0)
function udp(src, dst, sport, dport, payload) {
  const p = Buffer.from(payload);
  const b = Buffer.alloc(28 + p.length);
  b[0] = 0x45; b.writeUInt16BE(b.length, 2); b[8] = 64; b[9] = 17;
  Buffer.from(ip4(src)).copy(b, 12); Buffer.from(ip4(dst)).copy(b, 16);
  b.writeUInt16BE(sport, 20); b.writeUInt16BE(dport, 22); b.writeUInt16BE(8 + p.length, 24);
  p.copy(b, 28);
  return b;
}
const bin = (m, b) => b;

(async () => {
  // разбор заголовков
  assert.equal(classify(udp('10.88.5.1', '10.88.5.2', 1, 2, 'x'), 5).kind, 'unicast');
  assert.equal(classify(udp('10.88.5.1', '10.88.5.255', 1, 2, 'x'), 5).kind, 'bcast');
  assert.equal(classify(udp('10.88.5.1', '255.255.255.255', 1, 2, 'x'), 5).kind, 'bcast');
  assert.equal(classify(udp('10.88.5.1', '224.0.2.60', 1, 2, 'x'), 5).kind, 'mcast');
  assert.equal(classify(udp('10.88.5.1', '8.8.8.8', 1, 2, 'x'), 5).why, 'outside');
  assert.equal(classify(udp('10.88.5.1', '10.88.6.2', 1, 2, 'x'), 5).why, 'outside');
  assert.equal(classify(Buffer.alloc(10), 5).why, 'size');
  const v6 = Buffer.alloc(60); v6[0] = 0x60; assert.equal(classify(v6, 5).why, 'not_ipv4');

  const A = await open(), B = await open(), C = await open();
  tx(A, { t: 'vnet_create', name: 'Terraria вечер', game: 'terraria', public: true, password: 'сыр', nick: 'Аня' });
  const na = await next(A, (m) => m.t === 'vnet');
  assert.match(na.num, /^\d{8}$/); assert.match(na.ip, /^10\.88\.\d+\.1$/); assert.equal(na.owner, na.peer);
  assert.equal(na.members.length, 1); assert.equal(na.mask, 24); assert.equal(na.key.length, 64);

  // пароль
  tx(B, { t: 'vnet_join', code: na.num, nick: 'Боря' });
  assert.equal((await next(B, (m) => m.t === 'error')).error, 'need_password');
  tx(B, { t: 'vnet_join', code: na.num, password: 'нет' });
  assert.equal((await next(B, (m) => m.t === 'error')).error, 'bad_password');
  const pa = next(A, (m) => m.t === 'vnet_peer' && m.on);
  tx(B, { t: 'vnet_join', code: na.code.toLowerCase(), password: 'сыр', nick: 'Боря' });
  const nb = await next(B, (m) => m.t === 'vnet');
  assert.equal(nb.octet, na.octet); assert.notEqual(nb.ip, na.ip); assert.equal(nb.key, na.key); assert.equal(nb.members.length, 2);
  assert.equal((await pa).name, 'Боря');
  tx(C, { t: 'vnet_join', code: na.num, password: 'сыр', nick: 'Вика' });
  const nc = await next(C, (m) => m.t === 'vnet');

  // публичный список по игре
  tx(C, { t: 'vnet_list', game: 'terraria' });
  const ls = await next(C, (m) => m.t === 'vnet_list');
  assert.equal(ls.nets.length, 1); assert.equal(ls.nets[0].n, 3); assert.equal(ls.nets[0].locked, true); assert.equal(ls.nets[0].code, undefined);
  tx(C, { t: 'vnet_list', game: 'minecraft' });
  assert.equal((await next(C, (m) => m.t === 'vnet_list')).nets.length, 0);

  // широковещательный UDP (как поиск игры в LAN) — получают все, кроме отправителя
  const bc = udp(na.ip, '255.255.255.255', 7777, 7777, 'кто тут играет?');
  const gb = next(B, bin), gc = next(C, bin), noA = none(A, bin);
  A.send(bc);
  assert.ok((await gb).equals(bc)); assert.ok((await gc).equals(bc)); await noA;
  // широковещательный по сети и групповой
  const sb = udp(nb.ip, nb.bcast, 19132, 19132, 'RakNet ping');
  const g2 = next(A, bin); B.send(sb); assert.ok((await g2).equals(sb));
  const mc = udp(nc.ip, '224.0.2.60', 4445, 4445, '[MOTD]Мир[/MOTD][AD]25565[/AD]');
  const g3 = next(B, bin); C.send(mc); assert.ok((await g3).equals(mc));
  // личный пакет — только адресату
  const uc = udp(nb.ip, nc.ip, 5000, 6000, 'только Вике');
  const g4 = next(C, bin), noA2 = none(A, bin); B.send(uc);
  assert.ok((await g4).equals(uc)); await noA2;
  // подделанный отправитель, интернет, IPv6, огромный пакет, несуществующий адресат — отбрасываются
  const bad = [udp(nc.ip, na.ip, 1, 2, 'я — Вика'), udp(na.ip, '1.1.1.1', 1, 53, 'dns'), v6, udp(na.ip, nb.ip, 1, 2, 'x'.repeat(1600)), udp(na.ip, na.ip.replace(/\.1$/, '.200'), 1, 2, 'нет')];
  const noB = none(B, bin, 400), noC = none(C, bin, 400);
  for (const p of bad) A.send(p);
  await noB; await noC;
  assert.ok(srv.vnet.stats.dropped >= 5);

  // сигналы P2P и пинг между участниками
  tx(A, { t: 'vnet_sig', to: nb.peer, d: { c: [['203.0.113.5', 40000]] } });
  const sg = await next(B, (m) => m.t === 'vnet_sig'); assert.equal(sg.from, na.peer); assert.equal(sg.d.c[0][1], 40000);
  tx(A, { t: 'vnet_sig', to: nb.peer, d: { junk: 'x'.repeat(2000) } }); await none(B, (m) => m.t === 'vnet_sig');
  tx(B, { t: 'vnet_ping', to: nc.peer, ts: 123 });
  const pg = await next(C, (m) => m.t === 'vnet_ping'); assert.equal(pg.from, nb.peer);
  tx(C, { t: 'vnet_pong', to: nb.peer, ts: 123 }); assert.equal((await next(B, (m) => m.t === 'vnet_pong')).ts, 123);

  // чат и реакции
  const ch = next(C, (m) => m.t === 'vnet_chat'); tx(A, { t: 'vnet_chat', text: 'Заходите <b>в мир</b>' });
  const cm = await ch; assert.equal(cm.name, 'Аня'); assert.equal(cm.text, 'Заходите <b>в мир</b>');
  const rc = next(A, (m) => m.t === 'vnet_react'); tx(B, { t: 'vnet_react', id: cm.id, e: '🔥' }); assert.equal((await rc).e, '🔥');
  tx(B, { t: 'vnet_react', id: cm.id, e: 'НЕ-ЭМОДЗИ' }); await none(A, (m) => m.t === 'vnet_react');

  // только хозяин выгоняет
  tx(B, { t: 'vnet_kick', peer: nc.peer }); await none(C, (m) => m.t === 'error');
  const kc = next(C, (m) => m.t === 'error'); const left = next(A, (m) => m.t === 'vnet_peer' && !m.on);
  tx(A, { t: 'vnet_kick', peer: nc.peer, ban: true });
  assert.equal((await kc).error, 'banned_by_host'); assert.equal((await left).peer, nc.peer);

  // восстановление после перезапуска relay: сети в памяти нет, участник приходит с подписью
  srv.vnet.nets.delete(na.code); srv.vnet.nums.delete(na.num);
  const D = await open();
  tx(D, { t: 'vnet_join', code: na.code, resume: nb.token, peer: nb.peer, ip: nb.ip, num: nb.num, octet: nb.octet, owner: nb.owner, owner_uid: nb.owner_uid, name: nb.name, game: nb.game, public: true, pass_hash: nb.pass_hash });
  const nd = await next(D, (m) => m.t === 'vnet');
  assert.equal(nd.ip, nb.ip); assert.equal(nd.peer, nb.peer); assert.equal(nd.num, na.num); assert.equal(nd.owner, na.peer); assert.equal(nd.game, 'terraria');
  // поддельная подпись
  srv.vnet.nets.delete(na.code); srv.vnet.nums.delete(na.num);
  const E = await open();
  tx(E, { t: 'vnet_join', code: na.code, resume: 'f'.repeat(32), peer: 'aaaaaa', num: nb.num, octet: nb.octet, owner: nb.owner, owner_uid: 0 });
  assert.equal((await next(E, (m) => m.t === 'error')).error, 'not_found');
  // без сети — пакеты некому
  for (const w of [A, B, C, D, E]) w.close();
  server.close();
  console.log('vnet: all passed');
  process.exit(0);
})().catch((e) => { console.error('vnet FAIL', e); process.exit(1); });
