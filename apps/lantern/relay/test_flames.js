'use strict';
// 2.0.3: фишки пламени на сервере. Надеть можно только купленное; «Золотая корона» — +10% искр за задания;
// «Пиксельное» — 4 доп. реакции в чате сети (без пламени — отказ); пламя участника и хозяина сети — с сервера;
// коллекция: 3 пламени — «коллекционер» в событии входа гостя, достижение flames_3.
process.env.PORT = process.env.PORT || '18951';
process.env.QUIET = '1';
const assert = require('assert');
const crypto = require('crypto');
const WebSocket = require('ws');

(async () => {
  const { server, social } = require('./server').standalone(Number(process.env.PORT));
  const URL = `ws://127.0.0.1:${process.env.PORT}/ws`;
  const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.inbox = []; w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); }); w.on('open', () => res(w)); w.on('error', rej); });
  const tx = (w, o) => w.send(JSON.stringify(o));
  const wait = async (w, pred, ms = 4000) => { const t0 = Date.now(); for (;;) { const i = w.inbox.findIndex(pred); if (i >= 0) return w.inbox.splice(i, 1)[0]; if (Date.now() - t0 > ms) throw new Error('timeout ' + JSON.stringify(w.inbox.map((x) => x.t))); await new Promise((r) => setTimeout(r, 10)); } };
  const A = await open(), B = await open();
  const tA = crypto.randomBytes(32).toString('hex'), tB = crypto.randomBytes(32).toString('hex');
  tx(A, { t: 'register', token: tA, nick: 'Огонёк' }); await wait(A, (m) => m.t === 'welcome');
  tx(B, { t: 'register', token: tB, nick: 'Гость' }); await wait(B, (m) => m.t === 'welcome');
  const eco = social.eco;
  const ua = social.userByToken(tA).id, ub = social.userByToken(tB).id;

  // надеть некупленное нельзя; flameOf не верит equip без покупки
  tx(A, { t: 'wear', item: 'flame_emerald' });
  const w1 = await wait(A, (m) => m.t === 'shop');
  assert.ok(w1.error, 'надеть некупленное — отказ');
  social.store.db.prepare('UPDATE users SET equip=? WHERE id=?').run(JSON.stringify({ flame: 'flame_emerald' }), ua);
  assert.equal(eco.flameOf(ua), 'flame_amber', 'подменённый equip без покупки не даёт пламя');

  // покупка за искры и бонус +10% за задания
  social.store.db.prepare('UPDATE users SET sparks=5000 WHERE id=?').run(ua);
  for (const it of ['flame_emerald', 'flame_orange', 'flame_rose']) {
    tx(A, { t: 'buy', item: it });
    const r = await wait(A, (m) => m.t === 'shop');
    assert.equal(r.error, '', 'покупка ' + it);
  }
  tx(A, { t: 'wear', item: 'flame_emerald' }); assert.equal((await wait(A, (m) => m.t === 'shop')).error, '');
  assert.equal(eco.flameOf(ua), 'flame_emerald');
  assert.equal(eco.flamesOwned(ua), 3);
  assert.ok(eco.collector(ua));
  assert.ok(social.store.db.prepare("SELECT 1 FROM ach WHERE uid=? AND id='flames_3'").get(ua), 'достижение «Коллекция огня»');
  const q = require('./economy').QUESTS_DAILY[0];
  const day = 'd' + require('./economy').dayKey();
  const claim = (uid) => {
    social.store.db.prepare('INSERT OR REPLACE INTO quests(uid,period,qid,progress,claimed) VALUES(?,?,?,?,0)').run(uid, day, q.id, q.target);
    const s0 = social.user(uid).sparks;
    assert.equal(eco.claimQuest(uid, q.id, day), '');
    return social.user(uid).sparks - s0;
  };
  const withCrown = claim(ua);
  assert.equal(withCrown, q.sparks + Math.max(1, Math.round(q.sparks * 0.1)), 'корона: +10%');
  assert.equal(claim(ub), q.sparks, 'без короны — как было');

  // сеть: пламя участника и реакции «Пиксельного»
  tx(A, { t: 'vnet_create', v: 1, token: tA, nick: 'Огонёк', name: 'Тест', game: 'terraria', public: true });
  const na = await wait(A, (m) => m.t === 'vnet');
  tx(B, { t: 'vnet_join', v: 1, token: tB, nick: 'Гость', code: String(na.num) });
  const nb = await wait(B, (m) => m.t === 'vnet');
  const me = nb.members.find((m) => m.uid === ua);
  assert.equal(me.flame, 'flame_emerald', 'пламя хозяина у гостя');
  tx(A, { t: 'vnet_list', game: '' });
  const ls = await wait(A, (m) => m.t === 'vnet_list' || (m.t === 'vnets'));
  const it = (ls.nets || []).find((n) => String(n.num) === String(na.num));
  if (it) assert.equal(it.flame, 'flame_emerald', 'пламя хозяина в «Найти игроков»');
  tx(A, { t: 'vnet_chat', text: 'привет' });
  const c = await wait(B, (m) => m.t === 'vnet_chat');
  assert.equal(c.flame, 'flame_emerald');
  tx(A, { t: 'vnet_react', id: c.id, e: '👾' });           // корона — пиксельных реакций нет
  await new Promise((r) => setTimeout(r, 200));
  assert.ok(!B.inbox.find((m) => m.t === 'vnet_react'), 'без «Пиксельного» — нет 👾');
  tx(A, { t: 'wear', item: 'flame_orange' }); await wait(A, (m) => m.t === 'shop');
  tx(A, { t: 'vnet_react', id: c.id, e: '👾' });
  const re = await wait(B, (m) => m.t === 'vnet_react');
  assert.equal(re.e, '👾', '«Пиксельное» — 👾 доходит до всех');

  console.log('flames: all passed');
  server.close();
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
