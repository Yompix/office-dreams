'use strict';
// LANTERN Pro: свой код комнаты (резерв, уникальность, фильтр, окончание Pro) и облачные копии миров.
const os = require('os');
const fs = require('fs');
const path = require('path');
process.env.PORT = process.env.PORT || '18942';
process.env.QUIET = '1';
process.env.CLOUD_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'lantern-cloud-'));
process.env.CLOUD_MAX_MB = '1';
process.env.CLOUD_SLOTS = '2';
const assert = require('assert');
const crypto = require('crypto');
const http = require('http');
const WebSocket = require('ws');
const R = require('./server'); const { server, social } = R.standalone(Number(process.env.PORT)); const { rooms } = R;
const { validVanity } = require('./pro');
const PORT = Number(process.env.PORT);
const URL = `ws://127.0.0.1:${PORT}/ws`;
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.inbox = []; w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); }); w.on('open', () => res(w)); w.on('error', rej); });
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
function req(method, p, token, body) {
  return new Promise((resolve, reject) => {
    const r = http.request({ host: '127.0.0.1', port: PORT, method, path: p, headers: { authorization: 'Bearer ' + token, ...(body ? { 'content-length': body.length } : {}) } }, (res) => {
      const parts = [];
      res.on('data', (c) => parts.push(c));
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(parts) }));
    });
    r.on('error', reject);
    if (body) r.end(body); else r.end();
  });
}
const zip = (n) => Buffer.concat([Buffer.from([0x50, 0x4b, 0x03, 0x04]), crypto.randomBytes(n)]);
const log = (...a) => console.log('  ✓', ...a);

(async () => {
  for (const [c, ok] of [['STEVE', true], ['YOMPIX', false], ['AB', false], ['K0M2QX', false], ['SUKA22', false], ['FUCKME', false], ['222', false], ['КОД', false], ['MY-CRAFT', false], ['CRAFT7', true]]) {
    assert.equal(validVanity(c) === '', ok, c + ' → ' + validVanity(c));
  }
  log('проверка кода: длина, алфавит, мат (в т.ч. транслитом), зарезервированные слова');
  const tok = () => crypto.randomBytes(32).toString('hex');
  const [A, B] = [await open(), await open()];
  const tA = tok(), tB = tok();
  tx(A, { t: 'register', token: tA, nick: 'Стив' }); await wait(A, (m) => m.t === 'welcome');
  tx(B, { t: 'register', token: tB, nick: 'Алекс' }); await wait(B, (m) => m.t === 'welcome');
  tx(A, { t: 'vanity_set', code: 'steve' }); assert.match((await wait(A, (m) => m.t === 'vanity')).error, /Pro/);
  log('без Pro свой код не дают');
  tx(A, { t: 'demo_purchase', product: 'lantern_pro_month' }); await wait(A, (m) => m.t === 'demo_purchase');
  tx(B, { t: 'demo_purchase', product: 'lantern_pro_month' }); await wait(B, (m) => m.t === 'demo_purchase');
  tx(A, { t: 'vanity_set', code: 'ste-ve' }); let v = await wait(A, (m) => m.t === 'vanity'); assert.equal(v.error, ''); assert.equal(v.code, 'STEVE');
  tx(B, { t: 'vanity_set', code: 'Steve' }); assert.match((await wait(B, (m) => m.t === 'vanity')).error, /занят/);
  tx(B, { t: 'vanity_set', code: 'hitler22' }); assert.match((await wait(B, (m) => m.t === 'vanity')).error, /нельзя/);
  tx(A, { t: 'eco' }); assert.equal((await wait(A, (m) => m.t === 'eco')).profile.vanity, 'STEVE');
  log('код STEVE закреплён за Стивом; Алексу — «занят»; профиль показывает код');
  // хост с vanity
  const h1 = await open(); tx(h1, { t: 'host', v: 1, edition: 'java', name: 'Мир', token: tA, vanity: true });
  const r1 = await wait(h1, (m) => m.t === 'room'); assert.equal(r1.code, 'STEVE'); assert.equal(r1.vanity, true);
  const g = await open(); tx(g, { t: 'join', v: 1, code: 'steve', token: tB }); assert.equal((await wait(g, (m) => m.t === 'joined')).code, 'STEVE');
  log('комната открыта с кодом STEVE, друг зашёл по «steve»');
  const h2 = await open(); tx(h2, { t: 'host', v: 1, edition: 'java', name: 'Второй', token: tA, vanity: true });
  const r2 = await wait(h2, (m) => m.t === 'room'); assert.notEqual(r2.code, 'STEVE'); assert.equal(r2.code.length, 6); assert.equal(r2.vanity, false);
  log('вторая комната того же хозяина — случайный код', r2.code);
  // друг с присутствием на vanity-коде
  tx(A, { t: 'presence', activity: 'hosting', world: 'Мир', edition: 'java', code: 'STEVE' });
  h1.close(); h2.close(); g.close();
  await new Promise((r) => setTimeout(r, 100));
  for (const r of rooms.values()) if (r.code === 'STEVE' || r.code === r2.code) { r.closed = true; rooms.delete(r.code); }
  // Pro закончилась → код освобождается
  social.store.db.prepare('UPDATE users SET pro_until=? WHERE nick=?').run(Date.now() - 1000, 'Стив');
  tx(B, { t: 'vanity_set', code: 'STEVE' }); v = await wait(B, (m) => m.t === 'vanity'); assert.equal(v.error, ''); assert.equal(v.code, 'STEVE');
  log('Pro у Стива закончилась — код STEVE смог взять Алекс');
  const h3 = await open(); tx(h3, { t: 'host', v: 1, edition: 'java', name: 'Мир', token: tA, vanity: true });
  assert.notEqual((await wait(h3, (m) => m.t === 'room')).code, 'STEVE'); h3.close();
  social.pro.cleanup();
  assert.equal(social.pro.vanityOf(1), '');
  log('без Pro комната получает случайный код; cleanup снимает резерв');
  // ------------------------------------------------------------ облако
  social.store.db.prepare('UPDATE users SET pro_until=? WHERE nick=?').run(Date.now() + 86400_000, 'Стив');
  const C = await open(); const tC = tok(); tx(C, { t: 'register', token: tC, nick: 'Гость' }); await wait(C, (m) => m.t === 'welcome');
  let r = await req('PUT', '/cloud/upload?name=w.zip', tC, zip(1000)); assert.equal(r.status, 403);
  r = await req('PUT', '/cloud/upload?name=w.zip', 'nope', zip(1000)); assert.equal(r.status, 401);
  r = await req('PUT', '/cloud/upload?name=w.zip', tA, Buffer.concat([Buffer.from('hello'), crypto.randomBytes(100)])); assert.equal(r.status, 415);
  r = await req('PUT', '/cloud/upload?name=big.zip', tA, zip(1.2 * 1048576)); assert.equal(r.status, 413);
  log('облако: без профиля 401, без Pro 403, не-zip 415, больше лимита 413');
  const files = [zip(5000), zip(7000), zip(9000)];
  const ids = [];
  for (let i = 0; i < 3; i++) {
    r = await req('PUT', `/cloud/upload?name=${encodeURIComponent('Мир ' + i + '.zip')}`, tA, files[i]);
    assert.equal(r.status, 200, r.body.toString()); ids.push(JSON.parse(r.body).item.id);
    await new Promise((res) => setTimeout(res, 5));
  }
  tx(A, { t: 'cloud_list' }); let cl = await wait(A, (m) => m.t === 'cloud');
  assert.equal(cl.enabled, true); assert.equal(cl.list.length, 2); assert.deepEqual(cl.list.map((x) => x.id), [ids[2], ids[1]]);
  assert.equal(cl.list[0].name, 'Мир 2.zip');
  log('3 загрузки при лимите 2 копии — самая старая заменена:', cl.list.map((x) => x.name).join(', '));
  r = await req('GET', '/cloud/file/' + ids[2], tA); assert.equal(r.status, 200); assert(r.body.equals(files[2]));
  r = await req('GET', '/cloud/file/' + ids[2], tB); assert.equal(r.status, 404);
  log('скачать свою копию можно, чужую — нет');
  tx(A, { t: 'cloud_share', id: ids[2] }); cl = await wait(A, (m) => m.t === 'cloud'); assert.match(cl.shared, /^[2-9A-Z]{6}$/);
  r = await req('GET', '/cloud/share/' + cl.shared.toLowerCase(), tC); assert.equal(r.status, 200); assert(r.body.equals(files[2]));
  r = await req('GET', '/cloud/share/ZZZZZZ', tC); assert.equal(r.status, 404);
  log('код для друга/второго устройства', cl.shared, '— скачано другим профилем, байты совпали');
  tx(A, { t: 'cloud_delete', id: ids[1] }); cl = await wait(A, (m) => m.t === 'cloud'); assert.equal(cl.list.length, 1);
  assert(!fs.existsSync(path.join(process.env.CLOUD_DIR, ids[1] + '.zip')));
  social.store.db.prepare('UPDATE users SET pro_until=? WHERE nick=?').run(Date.now() - 31 * 86400_000, 'Стив');
  social.pro.cleanup();
  assert.equal(fs.readdirSync(process.env.CLOUD_DIR).length, 0);
  log('удаление работает; через 30 дней после конца Pro копии стираются');
  for (const w of [A, B, C]) w.close();
  server.close();
  fs.rmSync(process.env.CLOUD_DIR, { recursive: true, force: true });
  console.log('PRO OK');
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
