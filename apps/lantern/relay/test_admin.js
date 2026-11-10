'use strict';
// Хозяин мира: видит, кто зашёл/вышел, выгоняет и закрывает вход (по аккаунту LANTERN и списку из приложения).
process.env.PORT = process.env.PORT || '18951';
process.env.QUIET = '1';
const assert = require('assert');
const crypto = require('crypto');
const WebSocket = require('ws');
const { server } = require('./server').standalone(Number(process.env.PORT));
const URL = `ws://127.0.0.1:${process.env.PORT}/ws`;
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.inbox = []; w.closedP = new Promise((r) => w.on('close', r)); w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); }); w.on('open', () => res(w)); w.on('error', rej); });
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
(async () => {
  const tok = () => crypto.randomBytes(32).toString('hex');
  const tH = tok(), tA = tok(), tB = tok();
  const reg = async (t, nick) => { const w = await open(); tx(w, { t: 'register', token: t, nick }); const m = await wait(w, (x) => x.t === 'welcome'); w.close(); return m.me; };
  const H = await reg(tH, 'Хозяин'), A = await reg(tA, 'Алекс'), B = await reg(tB, 'Борис');
  const host = await open();
  tx(host, { t: 'host', v: 1, edition: 'bedrock', name: 'Мир', token: tH, bans: [B.id] });
  const room = await wait(host, (m) => m.t === 'room');
  // Алекс заходит → хозяин видит ник
  const a = await open(); tx(a, { t: 'join', v: 1, code: room.code, token: tA });
  await wait(a, (m) => m.t === 'joined');
  const pa = await wait(host, (m) => m.t === 'peer' && m.on);
  assert.equal(pa.user.nick, 'Алекс'); assert.equal(pa.user.id, A.id);
  // Борис в списке запрета из приложения → не пускают
  const b = await open(); tx(b, { t: 'join', v: 1, code: room.code, token: tB });
  assert.equal((await wait(b, (m) => m.t === 'error')).error, 'banned_by_host');
  // гость без аккаунта заходит, хозяин его выгоняет — гость может вернуться
  const g = await open(); tx(g, { t: 'join', v: 1, code: room.code });
  await wait(g, (m) => m.t === 'joined');
  const pg = await wait(host, (m) => m.t === 'peer' && m.on); assert.equal(pg.user, null);
  tx(host, { t: 'kick', peer: pg.peer });
  assert.equal((await wait(g, (m) => m.t === 'error')).error, 'kicked');
  await g.closedP;
  assert.equal((await wait(host, (m) => m.t === 'peer' && !m.on)).peer, pg.peer);
  const g2 = await open(); tx(g2, { t: 'join', v: 1, code: room.code });
  await wait(g2, (m) => m.t === 'joined'); await wait(host, (m) => m.t === 'peer' && m.on);
  // Алекс открыл поток, хозяин его банит → поток закрыт у хозяина, вернуться нельзя
  tx(a, { t: 'open', sid: 1, kind: 'udp' });
  const op = await wait(host, (m) => m.t === 'open');
  tx(host, { t: 'kick', peer: pa.peer, ban: true });
  assert.equal((await wait(a, (m) => m.t === 'error')).error, 'banned_by_host');
  assert.equal((await wait(host, (m) => m.t === 'close')).sid, op.sid);
  await wait(host, (m) => m.t === 'peer' && !m.on && m.peer === pa.peer);
  const a2 = await open(); tx(a2, { t: 'join', v: 1, code: room.code, token: tA });
  assert.equal((await wait(a2, (m) => m.t === 'error')).error, 'banned_by_host');
  // хозяин снял запрет со всех
  tx(host, { t: 'bans', bans: [] });
  await new Promise((r) => setTimeout(r, 50));
  const a3 = await open(); tx(a3, { t: 'join', v: 1, code: room.code, token: tA });
  await wait(a3, (m) => m.t === 'joined');
  // переподключение хозяина: relay сразу присылает, кто уже в мире
  host.close(); await host.closedP;
  const h2 = await open(); tx(h2, { t: 'host', v: 1, edition: 'bedrock', name: 'Мир', token: tH, code: room.code, resume: room.token });
  assert.equal((await wait(h2, (m) => m.t === 'room')).code, room.code);
  await wait(h2, (m) => m.t === 'peer' && m.on && m.user && m.user.id === A.id);
  await wait(h2, (m) => m.t === 'peer' && m.on && m.user === null);
  console.log('relay admin test OK: peers, kick, ban, bans list, resume');
  server.close(); process.exit(0);
})().catch((e) => { console.error('relay admin test FAIL', e); process.exit(1); });
