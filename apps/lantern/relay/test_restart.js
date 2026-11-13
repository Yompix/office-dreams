'use strict';
// 1.8.2: мягкий перезапуск relay внутри сайта (attach, как в server/index.js): при SIGTERM/close() все клиенты
// получают {t:'restart'} и закрытие кодом 4012 (не 1012: его Godot/wslay не принимает), /healthz → 503,
// новые WebSocket не принимаются; relay отвечает на ping приложения (pong).
process.env.QUIET = '1';
const assert = require('assert');
const http = require('http');
const crypto = require('crypto');
const WebSocket = require('ws');
const R = require('./server');

const PORT = 18971;
const URL = `ws://127.0.0.1:${PORT}/lantern/ws`;
const HTTP = `http://127.0.0.1:${PORT}/lantern`;
let relay = null;
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/lantern')) return relay.http(req, res, req.url.slice('/lantern'.length) || '/');
  res.writeHead(200); res.end('site');
});
relay = R.attach(server, { wsPath: '/lantern/ws' });
const open = () => new Promise((res, rej) => {
  const w = new WebSocket(URL); w.inbox = [];
  w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); });
  w.on('open', () => res(w)); w.on('error', rej);
});
const tx = (w, o) => w.send(JSON.stringify(o));
async function wait(w, pred, ms = 3000) {
  const t0 = Date.now();
  for (;;) {
    const i = w.inbox.findIndex(pred);
    if (i >= 0) return w.inbox.splice(i, 1)[0];
    if (Date.now() - t0 > ms) throw new Error('timeout; inbox=' + JSON.stringify(w.inbox.map((m) => m.t)));
    await new Promise((r) => setTimeout(r, 10));
  }
}
const closed = (w) => new Promise((res) => w.on('close', (code, reason) => res([code, String(reason)])));

server.listen(PORT, async () => {
  try {
    assert.equal(R.CLOSE_RESTART, 4012);
    let hz = await fetch(HTTP + '/healthz');
    assert.equal(hz.status, 200);
    // профиль (соцчасть) + хозяин комнаты + друг
    const s = await open();
    const tok = crypto.randomBytes(32).toString('hex');
    tx(s, { t: 'register', token: tok, nick: 'Рестарт' });
    await wait(s, (m) => m.t === 'welcome');
    tx(s, { t: 'ping', ts: 42 });
    assert.equal((await wait(s, (m) => m.t === 'pong')).ts, 42);
    const h = await open();
    tx(h, { t: 'host', v: 1, edition: 'java', name: 'Мир' });
    const room = await wait(h, (m) => m.t === 'room');
    const j = await open();
    tx(j, { t: 'join', v: 1, code: room.code });
    await wait(j, (m) => m.t === 'joined');
    console.log('  ✓ ping → pong, комната и друг на месте');
    // мягкий перезапуск
    const cs = [closed(s), closed(h), closed(j)];
    const t0 = Date.now();
    relay.close();
    const codes = await Promise.all(cs);
    for (const [code, reason] of codes) { assert.equal(code, 4012); assert.equal(reason, 'restart'); }
    for (const w of [s, h, j]) assert.ok(w.inbox.some((m) => m.t === 'restart'), 'нет сообщения restart');
    assert.ok(Date.now() - t0 < 1000, 'закрытие должно быть сразу');
    console.log('  ✓ перезапуск: все трое получили {t:restart} и код 4012 за', Date.now() - t0, 'мс');
    hz = await fetch(HTTP + '/healthz');
    assert.equal(hz.status, 503);
    assert.equal((await hz.json()).restarting, true);
    const refused = await new Promise((res) => { const w = new WebSocket(URL); w.on('open', () => res(false)); w.on('unexpected-response', (_q, r) => res(r.statusCode)); w.on('error', () => res(true)); });
    assert.ok(refused === 503 || refused === true, 'новые соединения во время перезапуска не принимаются: ' + refused);
    console.log('  ✓ во время перезапуска /healthz = 503, новые WebSocket не принимаются');
    server.close();
    console.log('relay restart test OK');
    process.exit(0);
  } catch (e) { console.error('relay restart test FAIL', e); process.exit(1); }
});
