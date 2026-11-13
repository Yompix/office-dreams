'use strict';
// 1.6: перезапуск relay (выкладка) не рвёт игру; ключи потоков, подтверждения, «Сообщить о проблеме».
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const WebSocket = require('ws');
const PORT = 18951;
const URL = `ws://127.0.0.1:${PORT}/ws`;
const HTTP = `http://127.0.0.1:${PORT}`;
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lantern-resume-'));
const KEY = 'test-upload-key';
const env = { ...process.env, PORT: String(PORT), QUIET: '1', LANTERN_ROOM_SECRET: 'test-secret', LANTERN_REPORT_DIR: path.join(dir, 'reports'),
  LANTERN_UPLOAD_SHA256: require('crypto').createHash('sha256').update(KEY).digest('hex'), REPORTS_PER_HOUR: '2' };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const start = async () => {
  const p = spawn(process.execPath, [path.join(__dirname, 'server.js')], { env, stdio: 'inherit' });
  for (let i = 0; i < 50; i++) { try { await fetch(HTTP + '/healthz'); return p; } catch { await sleep(100); } }
  throw new Error('relay did not start');
};
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.on('open', () => res(w)); w.on('error', rej); });
const next = (w, pred) => new Promise((res) => { const h = (d, bin) => { const m = bin ? d : JSON.parse(d); if (!pred || pred(m, bin)) { w.off('message', h); res(m); } }; w.on('message', h); });
const closed = (w) => new Promise((res) => w.on('close', (code) => res(code)));
const fr = (sid, s) => { const p = Buffer.from(s); const b = Buffer.alloc(4 + p.length); b.writeUInt32BE(sid); p.copy(b, 4); return b; };

let relay = null;
process.on('exit', () => { try { relay && relay.kill('SIGKILL'); } catch {} try { fs.rmSync(dir, { recursive: true, force: true }); } catch {} });
(async () => {
  relay = await start();
  // --- комната, друг, поток с ключом и подтверждение
  const host = await open();
  host.send(JSON.stringify({ t: 'host', v: 1, edition: 'java', name: 'Мир' }));
  const room = await next(host);
  const a = await open();
  a.send(JSON.stringify({ t: 'join', v: 1, code: room.code }));
  const ja = await next(a);
  assert.match(ja.peer, /^[0-9a-f]{6}$/);
  const op = next(host, (m, bin) => !bin && m.t === 'open');
  a.send(JSON.stringify({ t: 'open', sid: 5, kind: 'tcp', key: 'abcdef0123456789', rx: 0 }));
  const o1 = await op;
  assert.equal(o1.key, 'abcdef0123456789'); assert.equal(o1.rx, 0);
  const ack = next(a, (m, bin) => !bin && m.t === 'ack');
  host.send(JSON.stringify({ t: 'ack', sid: o1.sid, n: 1234, r: 1 }));
  const ak = await ack; assert.equal(ak.sid, 5); assert.equal(ak.n, 1234); assert.equal(ak.r, 1);
  const ack2 = next(host, (m, bin) => !bin && m.t === 'ack');
  a.send(JSON.stringify({ t: 'ack', sid: 5, n: 77 }));
  assert.equal((await ack2).n, 77);
  console.log('  ✓ ключ потока и подтверждения доходят в обе стороны');
  // друг потерял связь → хозяин получает close с lost
  const lostMsg = next(host, (m, bin) => !bin && m.t === 'close');
  a.terminate();
  assert.equal((await lostMsg).lost, true);
  // друг вернулся с прежним id
  const a2 = await open();
  a2.send(JSON.stringify({ t: 'join', v: 1, code: room.code, peer: ja.peer }));
  assert.equal((await next(a2)).peer, ja.peer);
  console.log('  ✓ обрыв друга: хозяин видит lost, друг возвращается с тем же id');
  // --- перезапуск relay (SIGTERM): клиенты получают {t:'restart'} и код 4012, хозяин возвращает тот же код
  const hc = closed(host), ac = closed(a2);
  relay.kill('SIGTERM');
  assert.equal(await hc, 4012); assert.equal(await ac, 4012);
  await sleep(300);
  relay = await start();
  const h2 = await open();
  h2.send(JSON.stringify({ t: 'host', v: 1, edition: 'java', name: 'Мир', code: room.code, num: room.num, resume: room.token }));
  const r2 = await next(h2);
  assert.equal(r2.code, room.code); assert.equal(r2.num, room.num);
  const fake = await open();
  fake.send(JSON.stringify({ t: 'host', v: 1, edition: 'java', name: 'x', code: 'QQQQQQ', num: '12345678', resume: '0'.repeat(32) }));
  assert.notEqual((await next(fake)).code, 'QQQQQQ');
  const a3 = await open();
  a3.send(JSON.stringify({ t: 'join', v: 1, code: room.num }));
  assert.equal((await next(a3)).t, 'joined');
  a3.send(fr(0, 'x'));
  console.log('  ✓ перезапуск relay: код', room.code, 'и номер', room.num, 'сохранились; чужая подпись не проходит');
  // --- отчёты
  const body = { app: 'LANTERN 1.6.0', os: 'Test', kind: 'test', app_log: 'line Authorization: Bearer abc.def\nfriend 95.31.2.7\n', server_log: 'x' };
  const rr = await (await fetch(HTTP + '/report', { method: 'POST', body: JSON.stringify(body) })).json();
  assert(rr.ok && rr.id >= 1000, JSON.stringify(rr));
  assert.equal((await fetch(HTTP + '/report')).status, 403);
  const list = await (await fetch(HTTP + '/report', { headers: { 'x-upload-key': KEY } })).json();
  assert.equal(list.reports[0].id, rr.id);
  const one = await (await fetch(HTTP + '/report/' + rr.id, { headers: { 'x-upload-key': KEY } })).json();
  assert(!/abc\.def/.test(one.app_log) && /95\.x\.x\.7/.test(one.app_log), one.app_log);
  const big = await fetch(HTTP + '/report', { method: 'POST', body: 'x'.repeat(2 * 1048576 + 10) }).catch(() => ({ status: 413 }));
  assert.equal(big.status, 413);
  assert.equal((await fetch(HTTP + '/report', { method: 'POST', body: '{}' })).status, 429);
  console.log('  ✓ отчёт №' + rr.id + ': сохранён, токены и чужие IP вычищены, список только с ключом, 2 МБ, лимит в час');
  for (const w of [h2, fake, a3]) w.close();
  relay.kill('SIGTERM');
  fs.rmSync(dir, { recursive: true, force: true });
  console.log('relay resume/report test OK');
  process.exit(0);
})().catch((e) => { console.error('relay resume test FAIL', e); process.exit(1); });
