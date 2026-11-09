'use strict';
// Симуляция 3 игроков: профили, друзья, чаты, публичные комнаты, «Фонарь», задания, магазин, сезон, демо-покупки.
process.env.PORT = process.env.PORT || '18941';
process.env.QUIET = '1';
const assert = require('assert');
const crypto = require('crypto');
const WebSocket = require('ws');
const { server, social } = require('./server');
const URL = `ws://127.0.0.1:${process.env.PORT}/ws`;
const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.inbox = []; w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); }); w.on('open', () => res(w)); w.on('error', rej); });
const tx = (w, o) => w.send(JSON.stringify(o));
async function wait(w, pred, ms = 3000) {
  const t0 = Date.now();
  for (;;) {
    const i = w.inbox.findIndex(pred);
    if (i >= 0) return w.inbox.splice(i, 1)[0];
    if (Date.now() - t0 > ms) throw new Error('timeout waiting; inbox=' + JSON.stringify(w.inbox.map((m) => m.t)));
    await new Promise((r) => setTimeout(r, 10));
  }
}
const log = (...a) => console.log('  ✓', ...a);
(async () => {
  const tok = () => crypto.randomBytes(32).toString('hex');
  const [A, B, C] = [await open(), await open(), await open()];
  const tA = tok(), tB = tok(), tC = tok();
  tx(A, { t: 'hello', token: tA }); await wait(A, (m) => m.t === 'need_register');
  tx(A, { t: 'register', token: tA, nick: 'бля123' }); assert.match((await wait(A, (m) => m.t === 'register_err')).error, /нельзя/);
  tx(A, { t: 'register', token: tA, nick: 'Yompix' }); assert.match((await wait(A, (m) => m.t === 'register_err')).error, /занят/);
  tx(A, { t: 'register', token: tA, nick: 'Стив' }); const wa = await wait(A, (m) => m.t === 'welcome');
  tx(B, { t: 'register', token: tB, nick: 'Алекс_2012' }); const wb = await wait(B, (m) => m.t === 'welcome');
  tx(C, { t: 'register', token: tC, nick: 'Griefer' }); const wc = await wait(C, (m) => m.t === 'welcome');
  log('профили:', wa.me.tag, wb.me.tag, wc.me.tag, '(мат и «Yompix» отклонены)');
  // повторный вход по токену
  const A2 = await open(); tx(A2, { t: 'hello', token: tA }); assert.equal((await wait(A2, (m) => m.t === 'welcome')).me.tag, wa.me.tag); A2.close();
  log('вход без пароля по токену устройства');
  tx(A, { t: 'friend_add', tag: wb.me.tag }); await wait(A, (m) => m.t === 'friend_add_ok');
  await wait(B, (m) => m.t === 'notice' && m.kind === 'request');
  tx(B, { t: 'friend_accept', id: wa.me.id });
  const st = await wait(A, (m) => m.t === 'state' && m.friends.length === 1);
  assert.equal(st.friends[0].nick, 'Алекс_2012'); assert.equal(st.friends[0].online, true);
  log('дружба по тегу: заявка → принята; онлайн-статус виден');
  tx(B, { t: 'presence', activity: 'hosting', world: 'Новоград', edition: 'java', version: '1.21.11', code: 'K7M2QX' });
  const fu = await wait(A, (m) => m.t === 'friend' && m.friend.activity && m.friend.activity.activity === 'hosting');
  log('статус друга:', JSON.stringify(fu.friend.activity));
  tx(A, { t: 'dm', to: wb.me.id, text: 'Привет! заходи https://evil.example.com/x и t.me/spam, сука' });
  const dm = await wait(B, (m) => m.t === 'dm');
  assert(!/http|t\.me|сука/.test(dm.text), dm.text);
  log('личное сообщение отфильтровано:', JSON.stringify(dm.text));
  tx(C, { t: 'dm', to: wa.me.id, text: 'hi' }); assert.match((await wait(C, (m) => m.t === 'dm_err')).error, /только друзьям/);
  for (let i = 0; i < 8; i++) tx(A, { t: 'dm', to: wb.me.id, text: 'спам ' + i });
  await wait(A, (m) => m.t === 'dm_err' && /часто/.test(m.error));
  log('чужим писать нельзя; лимит частоты сообщений срабатывает');
  tx(A, { t: 'history', with: wb.me.id }); const h = await wait(A, (m) => m.t === 'history'); assert(h.messages.length >= 2);
  log('история ЛС хранится (последние 100, 24 ч):', h.messages.length, 'сообщ.');
  // публичная комната: хост A, гость B
  const host = await open(); tx(host, { t: 'host', v: 1, edition: 'java', name: 'Мир Стива', token: tA, public: { title: 'Выживание у Стива', tags: ['выживание', 'pvp', 'казино'], version: '1.21.11', slots: 10 } });
  const room = await wait(host, (m) => m.t === 'room');
  const guest = await open(); tx(guest, { t: 'join', v: 1, code: room.code, token: tB }); await wait(guest, (m) => m.t === 'joined');
  tx(guest, { t: 'open', sid: 2, kind: 'tcp' });
  await new Promise((r) => setTimeout(r, 100));
  tx(C, { t: 'rooms', edition: 'java' }); const rl = await wait(C, (m) => m.t === 'rooms');
  assert.equal(rl.rooms[0].title, 'Выживание у Стива'); assert.deepEqual(rl.rooms[0].tags, ['выживание', 'pvp']); assert.equal(rl.rooms[0].players, 1);
  log('публичный список комнат:', JSON.stringify(rl.rooms[0]));
  tx(A, { t: 'room_enter', code: room.code }); tx(B, { t: 'room_enter', code: room.code }); tx(C, { t: 'room_enter', code: room.code });
  await wait(A, (m) => m.t === 'room_history'); await wait(B, (m) => m.t === 'room_history'); await wait(C, (m) => m.t === 'room_history');
  tx(A, { t: 'block', id: wc.me.id }); await new Promise((r) => setTimeout(r, 50));
  tx(C, { t: 'room_chat', text: 'я гриферю' }); tx(B, { t: 'room_chat', text: 'Всем привет!' });
  const rc = await wait(A, (m) => m.t === 'room_chat' && m.msg.text === 'Всем привет!');
  assert(!A.inbox.some((m) => m.t === 'room_chat' && m.msg.nick === 'Griefer'));
  log('чат комнаты работает; заблокированный игрок не виден:', rc.msg.nick, rc.msg.text);
  tx(guest, { t: 'like' }); assert.equal((await wait(guest, (m) => m.t === 'like')).error, '');
  tx(B, { t: 'report', id: wc.me.id, reason: 'оскорбления' }); await wait(B, (m) => m.t === 'report_ok');
  log('лайк комнате и жалоба приняты');
  // «Фонарь» и задания: 30 минут игры вместе (ускоренно)
  for (let i = 0; i < 30; i++) social.tickPlaytime();
  tx(B, { t: 'eco' }); const eco = await wait(B, (m) => m.t === 'eco');
  log('Фонарь Б после 30 мин:', JSON.stringify(eco.profile.lantern), 'серия', eco.profile.streak, 'искры', eco.profile.sparks);
  const doneQ = [...eco.quests.daily, ...eco.quests.weekly].filter((q) => q.progress >= q.target);
  log('задания дня:', eco.quests.daily.map((q) => `${q.title} ${q.progress}/${q.target}`).join(' | '));
  for (const q of doneQ) { tx(B, { t: 'quest_claim', id: q.id, period: q.period }); const r = await wait(B, (m) => m.t === 'eco'); assert.equal(r.error, ''); }
  tx(B, { t: 'streak_claim' }); const e2 = await wait(B, (m) => m.t === 'eco'); assert.equal(e2.error, '');
  log('получены награды за', doneQ.length, 'заданий и серию; искры:', e2.profile.sparks);
  tx(B, { t: 'achievements' }); const ach = await wait(B, (m) => m.t === 'achievements');
  log('достижения Б:', ach.list.filter((a) => a.done).map((a) => a.name).join(', '), '| титул:', ach.title, '| всего', ach.list.length);
  tx(B, { t: 'shop' }); const shop = await wait(B, (m) => m.t === 'shop');
  tx(B, { t: 'buy', item: 'flame_orange' }); const b1 = await wait(B, (m) => m.t === 'shop');
  log('покупка «Оранжевое пламя»:', b1.error || 'ок', '— искр', b1.sparks, '(товаров в магазине:', shop.items.length + ')');
  tx(B, { t: 'demo_purchase', product: 'sparks_1200' }); const d1 = await wait(B, (m) => m.t === 'demo_purchase');
  tx(B, { t: 'buy', item: 'flame_emerald' }); const b2 = await wait(B, (m) => m.t === 'shop'); assert.equal(b2.error, '');
  tx(B, { t: 'wear', item: 'flame_emerald' }); const w2 = await wait(B, (m) => m.t === 'shop'); assert.equal(w2.equip.flame, 'flame_emerald');
  log('ДЕМО: +1200 искр →', d1.profile.sparks, '; куплено и надето изумрудное пламя');
  tx(B, { t: 'demo_purchase', product: 'lantern_pass_s1' }); await wait(B, (m) => m.t === 'demo_purchase');
  tx(B, { t: 'demo_purchase', product: 'lantern_pro_month' }); const d3 = await wait(B, (m) => m.t === 'demo_purchase'); assert.equal(d3.profile.pro, true);
  tx(B, { t: 'season' }); const se = await wait(B, (m) => m.t === 'season');
  tx(B, { t: 'season_claim', tier: 1, track: 'premium' }); const sc = await wait(B, (m) => m.t === 'season');
  log('сезон:', se.title, 'очки', se.points, 'ступень', se.tier, '| премиум:', se.premium, '| награда ступени 1 премиум:', sc.error || 'получена');
  tx(B, { t: 'ad_reward' }); const ad = await wait(B, (m) => m.t === 'ad_reward'); assert.equal(ad.error, '');
  log('реклама за награду (демо): искры', ad.profile.sparks, ', осталось сегодня', ad.profile.ads_left);
  tx(B, { t: 'purchase', product: 'sparks_500', token: 'x'.repeat(40) }); const pu = await wait(B, (m) => m.t === 'purchase');
  log('реальная покупка без ключа Google → ', pu.error);
  tx(B, { t: 'demo_wipe' }); const wipe = await wait(B, (m) => m.t === 'eco'); assert.equal(wipe.profile.pro, false);
  log('демо выключено → демо-искры, Pro и премиум-пропуск стёрты; искры', wipe.profile.sparks);
  tx(C, { t: 'top' }); const top = await wait(C, (m) => m.t === 'top');
  log('топ миров недели:', JSON.stringify(top.hosts.map((x) => [x.tag, x.visits, x.likes])));
  for (const w of [A, B, C, host, guest]) w.close();
  server.close();
  console.log('SOCIAL TEST OK');
  process.exit(0);
})().catch((e) => { console.error('SOCIAL TEST FAIL', e); process.exit(1); });
