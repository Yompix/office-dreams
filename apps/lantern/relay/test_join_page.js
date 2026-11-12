'use strict';
// 1.7: страница-ссылка на игру /j/<код> и поиск комнат по названию, номеру и режиму.
process.env.PORT = process.env.PORT || '18957';
process.env.QUIET = '1';
const assert = require('assert');
const http = require('http');
const crypto = require('crypto');
const WebSocket = require('ws');
const { server } = require('./server').standalone(Number(process.env.PORT));
const { codeFromPath, esc } = require('./join_page');
const { roomMatches } = require('./social');
const URL_WS = `ws://127.0.0.1:${process.env.PORT}/ws`;
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL_WS); w.inbox = []; w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); }); w.on('open', () => res(w)); w.on('error', rej); });
const tx = (w, o) => w.send(JSON.stringify(o));
async function wait(w, pred, ms = 3000) {
  const t0 = Date.now();
  for (;;) {
    const i = w.inbox.findIndex(pred);
    if (i >= 0) return w.inbox.splice(i, 1)[0];
    if (Date.now() - t0 > ms) throw new Error('timeout; inbox=' + JSON.stringify(w.inbox));
    await new Promise((r) => setTimeout(r, 10));
  }
}
const get = (p, headers = {}) => new Promise((res, rej) => http.get({ host: '127.0.0.1', port: process.env.PORT, path: p, headers }, (r) => {
  let b = ''; r.setEncoding('utf8'); r.on('data', (d) => { b += d; }); r.on('end', () => res({ status: r.statusCode, headers: r.headers, body: b })); }).on('error', rej));
(async () => {
  // разбор пути
  assert.equal(codeFromPath('/j/k7m-2qx'), 'K7M2QX');
  assert.equal(codeFromPath('/j/35821626'), '35821626');
  assert.equal(codeFromPath('/j/'), '');
  assert.equal(codeFromPath('/j/<script>'), '');
  assert.equal(codeFromPath('/j/AB/../../x'), '');
  assert.equal(esc('<b a="1">&\''), '&lt;b a=&quot;1&quot;&gt;&amp;&#39;');
  // комната с «опасным» названием: на странице всё экранировано
  const tok = crypto.randomBytes(32).toString('hex');
  const reg = await open(); tx(reg, { t: 'register', token: tok, nick: 'Хозяин' }); await wait(reg, (m) => m.t === 'welcome'); reg.close();
  const host = await open();
  tx(host, { t: 'host', v: 1, edition: 'both', name: 'Мир', token: tok, public: { title: 'Замок <img src=x onerror=1> "друзей"', slots: 8 }, settings: { mode: 'pvp', cap: 8, password: '' } });
  const room = await wait(host, (m) => m.t === 'room');
  const g = await open(); tx(g, { t: 'join', v: 1, code: room.code }); await wait(g, (m) => m.t === 'joined');
  let r = await get('/j/' + room.code);
  assert.equal(r.status, 200); assert.match(r.headers['content-type'], /text\/html/); assert.equal(r.headers['cache-control'], 'no-store');
  assert.ok(r.body.includes('Замок &lt;img src=x onerror=1&gt; &quot;друзей&quot;'), 'название экранировано');
  assert.ok(!r.body.includes('<img src=x'), 'нет сырой разметки из названия');
  assert.ok(r.body.includes('Хозяин: Хозяин')); assert.ok(r.body.includes('2/8'), 'игроков x/y'); assert.ok(r.body.includes('PvP'));
  assert.ok(r.body.includes(`lantern://join?code=${room.code}`), 'ссылка для ПК');
  assert.ok(r.body.includes(`intent://join?code=${room.code}#Intent;scheme=lantern;package=com.yompix.lantern;S.browser_fallback_url=`), 'ссылка для Android');
  assert.ok(r.body.includes('/download'), 'кнопка «Скачать»');
  assert.ok(r.body.includes('НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT'), 'оговорка Mojang');
  // по номеру комнаты и с дефисом/строчными
  r = await get('/j/' + room.num); assert.equal(r.status, 200); assert.ok(r.body.includes('2/8'));
  r = await get('/j/' + room.code.toLowerCase().slice(0, 3) + '-' + room.code.slice(3)); assert.equal(r.status, 200); assert.ok(r.body.includes('2/8'));
  // нет такой игры — страница всё равно открывается (кнопки «Открыть» и «Скачать»), но без данных
  r = await get('/j/ZZZZZZ'); assert.equal(r.status, 200); assert.ok(r.body.includes('Игра сейчас закрыта')); assert.ok(r.body.includes('lantern://join?code=ZZZZZZ'));
  r = await get('/j/%3Cscript%3E'); assert.equal(r.status, 404);
  r = await get('/j/'); assert.equal(r.status, 404);
  // поиск: название, номер (и его начало), режим по-русски и по-английски
  const fake = { meta: { title: 'Замок друзей' }, num: '35821626', code: 'K7M2QX', mode: 'pvp' };
  for (const q of ['замок', '35821626', '3582', '№ 35821626', 'пвп', 'PVP', 'k7m-2qx']) assert.ok(roomMatches(fake, q), 'найдено: ' + q);
  for (const q of ['выживание', '9999', 'замки']) assert.ok(!roomMatches(fake, q), 'не найдено: ' + q);
  assert.ok(roomMatches({ meta: { title: 'X' }, num: '1', mode: 'survival' }, 'выжив'));
  const cl = await open(); tx(cl, { t: 'rooms', q: 'пвп' });
  assert.equal((await wait(cl, (m) => m.t === 'rooms')).rooms.length, 1);
  tx(cl, { t: 'rooms', q: room.num.slice(0, 4) });
  assert.equal((await wait(cl, (m) => m.t === 'rooms')).rooms.length, 1);
  tx(cl, { t: 'rooms', q: 'творчество' });
  assert.equal((await wait(cl, (m) => m.t === 'rooms')).rooms.length, 0);
  console.log('join page test: OK');
  for (const w of [host, g, cl]) w.close();
  server.close(); setTimeout(() => process.exit(0), 100);
})().catch((e) => { console.error('FAIL', e); process.exit(1); });
