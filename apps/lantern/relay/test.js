'use strict';
// Самопроверка relay: хост-эхо + два друга, TCP- и UDP-потоки, лимиты, неверный код.
process.env.PORT = process.env.PORT || '18931';
process.env.QUIET = '1';
const assert = require('assert');
const WebSocket = require('ws');
const { server } = require('./server').standalone(Number(process.env.PORT));
const URL = `ws://127.0.0.1:${process.env.PORT}/ws`;
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.on('open', () => res(w)); w.on('error', rej); });
const next = (w, pred) => new Promise((res) => { const h = (d, bin) => { const m = bin ? d : JSON.parse(d); if (!pred || pred(m, bin)) { w.off('message', h); res(m); } }; w.on('message', h); });
const fr = (sid, s) => { const p = Buffer.from(s); const b = Buffer.alloc(4 + p.length); b.writeUInt32BE(sid); p.copy(b, 4); return b; };
(async () => {
  const host = await open();
  host.send(JSON.stringify({ t: 'host', v: 1, edition: 'both', name: 'Тест' }));
  const room = await next(host);
  assert.equal(room.t, 'room'); assert.match(room.code, /^[2-9A-HJKMNP-Z]{6}$/);
  host.on('message', (d, bin) => { if (bin) { const sid = d.readUInt32BE(0); host.send(fr(sid, 'echo:' + d.subarray(4).toString())); } });
  const bad = await open(); bad.send(JSON.stringify({ t: 'join', v: 1, code: 'ZZZZZZ' }));
  assert.equal((await next(bad)).error, 'not_found');
  const a = await open(), b = await open();
  a.send(JSON.stringify({ t: 'join', v: 1, code: room.code.toLowerCase().slice(0, 3) + '-' + room.code.slice(3) }));
  b.send(JSON.stringify({ t: 'join', v: 1, code: room.code }));
  const ja = await next(a), jb = await next(b);
  assert.equal(ja.t, 'joined'); assert.equal(ja.edition, 'both'); assert.equal(jb.name, 'Тест');
  const opens = [];
  host.on('message', (d, bin) => { if (!bin) { const m = JSON.parse(d); if (m.t === 'open') opens.push(m); } });
  a.send(JSON.stringify({ t: 'open', sid: 1, kind: 'tcp' }));
  b.send(JSON.stringify({ t: 'open', sid: 1, kind: 'udp' }));
  await new Promise((r) => setTimeout(r, 100));
  assert.equal(opens.length, 2); assert.notEqual(opens[0].sid, opens[1].sid);
  a.send(fr(1, 'привет-A')); b.send(fr(1, 'привет-B'));
  const ra = await next(a, (m, bin) => bin), rb = await next(b, (m, bin) => bin);
  assert.equal(ra.readUInt32BE(0), 1); assert.equal(ra.subarray(4).toString(), 'echo:привет-A');
  assert.equal(rb.subarray(4).toString(), 'echo:привет-B');
  // хост закрывает поток A
  host.send(JSON.stringify({ t: 'close', sid: opens[0].sid }));
  const ca = await next(a, (m, bin) => !bin && m.t === 'close'); assert.equal(ca.sid, 1);
  // хост ушёл → друзья узнают
  const g = next(b, (m, bin) => !bin && m.t === 'host_gone');
  host.close();
  await g;
  const h = await (await fetch(`http://127.0.0.1:${process.env.PORT}/healthz`)).json();
  assert.equal(h.ok, true); assert.equal(h.rooms, 1);
  // 1.5: номер комнаты, режим, места, пароль, публичный список
  const h2 = await open();
  h2.send(JSON.stringify({ t: 'host', v: 1, edition: 'java', name: 'Пароль', public: { title: 'Закрытый мир', version: '1.21.11', slots: 3 },
    settings: { mode: 'creative', cap: 2, password: 'кот', size_mb: 42 } }));
  const r2 = await next(h2); assert.match(r2.num, /^\d{8}$/);
  const p1 = await open(); p1.send(JSON.stringify({ t: 'join', v: 1, code: r2.num })); assert.equal((await next(p1)).error, 'need_password');
  const p2 = await open(); p2.send(JSON.stringify({ t: 'join', v: 1, code: r2.num, password: 'пёс' })); assert.equal((await next(p2)).error, 'bad_password');
  const p3 = await open(); p3.send(JSON.stringify({ t: 'join', v: 1, code: r2.num, password: 'кот' })); const j3 = await next(p3);
  assert.equal(j3.t, 'joined'); assert.equal(j3.num, r2.num);
  const p4 = await open(); p4.send(JSON.stringify({ t: 'join', v: 1, code: r2.code, password: 'кот' })); assert.equal((await next(p4)).error, 'full');
  const list = await (await fetch(`http://127.0.0.1:${process.env.PORT}/rooms?mode=creative&version=1.21`)).json();
  const pr = list.rooms.find((x) => x.num === r2.num);
  assert(pr && pr.locked && pr.cap === 2 && pr.mode === 'creative' && pr.size_mb === 42 && pr.players === 1, JSON.stringify(list));
  assert.equal((await (await fetch(`http://127.0.0.1:${process.env.PORT}/rooms?mode=pvp`)).json()).rooms.length, 0);
  h2.send(JSON.stringify({ t: 'room_meta', settings: { password: '', cap: 6 } }));
  await new Promise((r) => setTimeout(r, 50));
  const p5 = await open(); p5.send(JSON.stringify({ t: 'join', v: 1, code: r2.num })); assert.equal((await next(p5)).t, 'joined');
  console.log('  ✓ номер комнаты', r2.num, '· пароль · места · режим/версия в списке · смена настроек на ходу');
  for (const w of [h2, p1, p2, p3, p4, p5]) w.close();
  console.log('relay test OK: code', room.code, 'health', JSON.stringify(h));
  for (const w of [a, b, bad]) w.close();
  server.close(); process.exit(0);
})().catch((e) => { console.error('relay test FAIL', e); process.exit(1); });
