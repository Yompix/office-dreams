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
  console.log('relay test OK: code', room.code, 'health', JSON.stringify(h));
  for (const w of [a, b, bad]) w.close();
  server.close(); process.exit(0);
})().catch((e) => { console.error('relay test FAIL', e); process.exit(1); });
