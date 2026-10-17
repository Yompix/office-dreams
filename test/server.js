'use strict';
const assert = require('assert');
const crypto = require('crypto');
const { makeEnv, httpJson, Player, test, summary, E, TG, BOT_TOKEN } = require('./lib.js');
const U = require('../server/users.js');
const Eco = require('../server/economy.js');
const AB = require('../server/antibot.js');

(async () => {
  const env = await makeEnv();
  const { base, ctx, clock } = env;
  const DAY = 86400e3;

  console.log('Basics');
  await test('health + public config', async () => {
    const h = await httpJson(base, 'GET', '/healthz');
    assert.equal(h.json.ok, true);
    const c = await httpJson(base, 'GET', '/api/config');
    assert.equal(c.json.bot, 'od_test_bot');
    assert.equal(c.json.tickMs, 250);
    assert.ok(Array.isArray(c.json.shop) && c.json.shop.length >= 4);
  });
  await test('auth rejects forged / expired / missing initData', async () => {
    const user = { id: 11, first_name: 'X' };
    const good = TG.signInitData(BOT_TOKEN, user, {}, clock.t);
    assert.equal((await httpJson(base, 'POST', '/api/auth', { initData: good.replace(/hash=[0-9a-f]{64}/, 'hash=' + '0'.repeat(64)) })).status, 401);
    assert.equal((await httpJson(base, 'POST', '/api/auth', { initData: TG.signInitData('999:other-token', user, {}, clock.t) })).status, 401);
    const old = TG.signInitData(BOT_TOKEN, user, {}, clock.t - 2 * DAY);
    assert.equal((await httpJson(base, 'POST', '/api/auth', { initData: old })).json.err, 'initdata_expired');
    assert.equal((await httpJson(base, 'POST', '/api/auth', {})).status, 401);
    // tampered user field
    const tampered = good.replace(/user=[^&]+/, 'user=' + encodeURIComponent(JSON.stringify({ id: 12, first_name: 'X' })));
    assert.equal((await httpJson(base, 'POST', '/api/auth', { initData: tampered })).status, 401);
  });
  await test('sessions: no token / bad token rejected', async () => {
    assert.equal((await httpJson(base, 'POST', '/api/sync', {})).status, 401);
    assert.equal((await httpJson(base, 'POST', '/api/sync', {}, { authorization: 'Bearer abc.def' })).status, 401);
  });

  console.log('Auth & sync');
  const A = new Player(env, 1001, { name: 'Alice', premium: true, username: 'alice' });
  await test('first login creates a player with a fresh state', async () => {
    const r = await A.auth();
    assert.equal(r.status, 200);
    assert.equal(r.json.created, true);
    assert.ok(r.json.token && r.json.state && r.json.eco);
    assert.equal(r.json.state.v, 2);
    assert.equal(r.json.eco.ref.code.length >= 6, true);
    const again = await new Player(env, 1001).auth();
    assert.equal(again.json.created, false);
    await A.auth(); // fresh session (a re-login bumps the sequence number, as a second device would)
  });
  await test('server replay == client prediction (state hash equal) over many syncs', async () => {
    let same = 0, total = 0;
    for (let round = 0; round < 12; round++) {
      A.play(80); // 20 s of play
      const r = await A.sync();
      assert.equal(r.json.ok, true, JSON.stringify(r.json).slice(0, 200));
      assert.equal(r.json.hash, E.stateHash(A.cl.S), 'client and server diverged at round ' + round);
      total++; if (r.json.same) same++;
    }
    assert.ok(same >= 8, `expected mostly cheap "same" answers, got ${same}/${total}`);
    assert.ok(A.eco.pts > 0, 'points were credited');
    assert.ok(A.S.stats.taps > 100);
  });
  await test('points are priced on the server from verified progress only', async () => {
    const before = A.eco.pts;
    // a forged client claims huge stats in the state it sends: nothing in the protocol accepts a state, only actions
    const r = await A.sync({ mutate: (b) => { b.stats = { taps: 1e9 }; b.state = { stats: { taps: 1e9 } }; } });
    assert.ok(r.json.eco.pts - before < 5);
  });
  await test('forged action logs are rejected or ineffective', async () => {
    const P = new Player(env, 1002); await P.auth();
    P.tick(8);
    for (const bad of [
      { seq: P.cl.seq, batch: [[0, 'cheat', 1]], tail: 0 },
      { seq: P.cl.seq, batch: [[-5, 'tap']], tail: 0 },
      { seq: P.cl.seq, batch: [[0, 'tap']].concat(Array(700).fill([0, 'tap'])), tail: 0 },
      { seq: P.cl.seq, batch: [[0, 'tap', { a: 1 }]], tail: 0 },
      { seq: P.cl.seq, batch: 'x', tail: 0 },
      { seq: P.cl.seq, batch: [], tail: 1e9 },
    ]) {
      const r = await P.api('POST', '/api/sync', bad);
      assert.equal(r.status, 400, JSON.stringify(bad).slice(0, 60));
    }
    // purchases without money simply fail inside the replay and change nothing
    const r = await P.api('POST', '/api/sync', { seq: P.cl.seq, batch: [[0, 'flr', 'f5'], [0, 'ups', 'prog'], [0, 'bst', 'turbo'], [0, 'buy', 'rack']], tail: 8 });
    assert.equal(r.json.ok, true);
    assert.equal(r.json.state.floors.f5, 0);
    assert.equal(r.json.state.staff.prog.lvl, 0);
    assert.equal(r.json.state.items.rack, 0);
    assert.equal(r.json.state.gems, 3);
    assert.equal(r.json.fails, 4);
  });
  await test('city actions are replayed by the server', async () => {
    const P = new Player(env, 1009); await P.auth();
    P.tick(8);
    P.cl.act('tap'); P.cl.act('tap');
    P.tick(4);
    const r0 = P.cl.act('cof');
    assert.equal(r0.ok, true);
    const r = await P.sync();
    assert.equal(r.json.ok, true, JSON.stringify(r.json).slice(0, 200));
    assert.ok(r.json.state ? r.json.state.city.cof > 0 : true);
    assert.equal(r.json.hash, E.stateHash(P.cl.S));
    assert.ok(U.loadState(ctx, 1009).city.cof > 0, 'server stored the coffee cooldown');
  });
  await test('time cannot be sped up: claiming more ticks than wall time is rejected and logged', async () => {
    const P = new Player(env, 1003); await P.auth();
    P.tick(40);
    const riskBefore = U.getUser(ctx, 1003).risk_hard;
    const r = await P.api('POST', '/api/sync', { seq: P.cl.seq, batch: [], tail: 4000 }); // 1000 s claimed, 10 s passed
    assert.equal(r.json.ok, false);
    assert.equal(r.json.err, 'clock');
    assert.ok(U.getUser(ctx, 1003).risk_hard > riskBefore);
    assert.equal(r.json.state.tk, 0, 'authoritative state was not advanced');
  });
  await test('stale sequence (second device / lost response) returns the authoritative state', async () => {
    const P = new Player(env, 1004); await P.auth();
    P.play(40);
    const r1 = await P.sync();
    assert.equal(r1.json.ok, true);
    P.play(40);
    const r2 = await P.sync({ mutate: (b) => { b.seq = b.seq - 1; } });
    assert.equal(r2.json.err, 'stale');
    assert.ok(r2.json.state);
    assert.equal(P.cl.queue.length, 0);
  });
  await test('away from the game: passive income is credited (capped), no points for idling', async () => {
    const P = new Player(env, 1005); await P.auth();
    P.play(400); // build some sales
    await P.sync();
    const pts0 = P.eco.pts, cash0 = P.S.cash, games = P.S.games.length;
    assert.ok(games > 0, 'player has released games');
    env.advance(2 * 3600e3);
    const r = await P.sync();
    assert.ok(r.json.off >= 7000, 'offline seconds credited: ' + r.json.off);
    assert.ok(P.S.cash > cash0 || P.S.earned > 0);
    assert.ok(r.json.eco.pts - pts0 < 1, 'idle time earns no airdrop points');
    env.advance(30 * 3600e3);
    const r2 = await P.sync();
    assert.ok(r2.json.off <= E.OFFLINE_CAP, 'offline credit is capped: ' + r2.json.off);
  });

  console.log('Economy');
  await test('daily curve: heavy play saturates near D, never above', async () => {
    const P = new Player(env, 1006, { premium: true }); await P.auth();
    let last = 0;
    for (let h = 0; h < 16; h++) {
      for (let k = 0; k < 6; k++) { P.play(360); await P.sync(); }
      const t = P.eco.today;
      assert.ok(t >= last - 0.01, 'monotone within a day');
      last = t;
      if (U.dayKey(clock.t) !== U.dayKey(clock.t - 3600e3)) break;
    }
    const D = ctx.cfg.eco.dailyD;
    assert.ok(last <= D, `today ${last} must stay within D=${D}`);
    assert.ok(last > 20, 'but a real session earns something: ' + last);
  });
  await test('new day resets the daily curve and unlocks check-in', async () => {
    const P = new Player(env, 1007); await P.auth();
    P.play(200); await P.sync();
    const ci = await P.api('POST', '/api/checkin');
    assert.equal(ci.json.ok, true);
    assert.equal(ci.json.streak, 1);
    assert.equal((await P.api('POST', '/api/checkin')).json.err, 'done');
    env.advance(DAY);
    P.play(8); await P.sync();
    const ci2 = await P.api('POST', '/api/checkin');
    assert.equal(ci2.json.streak, 2);
    assert.equal(ci2.json.pts, Eco.CHECKIN[1]);
    env.advance(3 * DAY);
    P.play(8); await P.sync();
    assert.equal((await P.api('POST', '/api/checkin')).json.streak, 1, 'missed days reset the streak');
  });
  await test('check-in needs a real first session', async () => {
    const P = new Player(env, 1008); await P.auth();
    assert.equal((await P.api('POST', '/api/checkin')).json.err, 'play_first');
  });

  console.log('Referrals');
  const R = {};
  await test('invite link: friend joins with start_param, gets a welcome bonus', async () => {
    const link = A.eco.ref.link;
    assert.ok(link.startsWith('https://t.me/od_test_bot?start=ref_'), link);
    const code = A.eco.ref.code;
    R.B = new Player(env, 2001, { name: 'Bob', fp: 'bob-phone' });
    const r = await R.B.auth('ref_' + code);
    assert.equal(r.json.created, true);
    assert.equal(U.getUser(ctx, 2001).ref1, 1001);
    assert.equal(r.json.state.cash, 250 + ctx.cfg.eco.welcomeCash);
    // referral cannot be changed or self-assigned later
    const again = await new Player(env, 2001).auth('ref_' + Eco.ref);
    assert.equal(U.getUser(ctx, 2001).ref1, 1001);
    const self = new Player(env, 1001); const sr = await self.auth('ref_' + code);
    assert.equal(U.getUser(ctx, 1001).ref1, null);
  });
  await test('inviter is rewarded only when the friend really plays (first released game)', async () => {
    const B = R.B;
    const a0 = U.getUser(ctx, 1001).pts, gems0 = U.loadState(ctx, 1001).gems;
    B.play(40); await B.sync();
    assert.equal(U.getUser(ctx, 2001).ref_active, 0, 'not active before the first game');
    for (let i = 0; i < 6 && !B.S.stats.released; i++) { B.play(160); await B.sync(); }
    assert.ok(B.S.stats.released >= 1, 'friend released a game');
    B.play(40); await B.sync();
    assert.equal(U.getUser(ctx, 2001).ref_active, 1, 'active after the first released game');
    const gemsAfter = U.loadState(ctx, 1001).gems;
    assert.equal(gemsAfter - gems0, ctx.cfg.eco.inviteGems);
    assert.ok(U.getUser(ctx, 1001).pts - a0 >= ctx.cfg.eco.refActivationBonus - 0.01);
  });
  await test('inviter earns 10% of the friend\'s points; no second level by default; daily cap respected', async () => {
    const B = R.B;
    const before = U.getUser(ctx, 1001);
    const bBefore = U.getUser(ctx, 2001).pts;
    for (let i = 0; i < 5; i++) { B.play(240); await B.sync(); }
    const gainB = U.getUser(ctx, 2001).pts - bBefore;
    const gainA = U.getUser(ctx, 1001).ref_pts - before.ref_pts;
    assert.ok(gainB > 5, 'friend earned: ' + gainB);
    assert.ok(Math.abs(gainA - gainB * 0.1) < 0.05 + gainB * 0.01, `L1 share ${gainA} vs ${gainB * 0.1}`);
    // L2: C invited by B
    const C = new Player(env, 3001, { fp: 'carl-phone' });
    await C.auth('ref_' + U.getUser(ctx, 2001).ref_code);
    assert.equal(U.getUser(ctx, 3001).ref2, 1001);
    for (let d = 0; d < 2; d++) { for (let i = 0; i < 6; i++) { C.play(160); await C.sync(); } env.advance(DAY); }
    C.play(40); await C.sync();
    assert.equal(U.getUser(ctx, 3001).ref_active, 1);
    const a1 = U.getUser(ctx, 1001).ref_pts;
    const cB = U.getUser(ctx, 3001).pts;
    C.play(240); await C.sync();
    const cGain = U.getUser(ctx, 3001).pts - cB;
    const l2 = Eco && ctx.db.get("SELECT SUM(pts) AS p FROM ledger WHERE uid=1001 AND kind='ref2'").p;
    assert.ok(!l2, 'no L2 payout by default (one simple rule)');
    assert.ok(cGain > 1);
    const cap = ctx.cfg.eco.refDailyCap;
    assert.ok(U.getUser(ctx, 1001).ref_today <= cap + 0.01);
  });
  await test('friends list shows earnings per friend', async () => {
    const r = await A.api('GET', '/api/friends');
    assert.ok(r.json.friends.length >= 1);
    const bob = r.json.friends.find((f) => f.id === 2001);
    assert.ok(bob && bob.active && bob.earned > 0);
    assert.ok(!('username' in bob));
  });
  await test('pending referral from the bot /start is applied at first login', async () => {
    const code = A.eco.ref.code;
    const upd = { update_id: 1, message: { message_id: 1, from: { id: 4001, first_name: 'Dan', language_code: 'uk' }, chat: { id: 4001, type: 'private' }, text: '/start ref_' + code } };
    const w = await httpJson(base, 'POST', '/tg/' + ctx.cfg.webhookSecret, upd, { 'x-telegram-bot-api-secret-token': ctx.cfg.webhookSecret });
    assert.equal(w.status, 200);
    await new Promise((r) => setTimeout(r, 60));
    assert.ok(env.mock.calls.some((c) => c.method === 'sendMessage' && c.body.chat_id === 4001 && /Office Dreams/.test(c.body.text)));
    const D = new Player(env, 4001, { lang: 'uk' }); await D.auth(); // opened through the menu button: no start_param
    assert.equal(U.getUser(ctx, 4001).ref1, 1001);
    assert.equal(U.getUser(ctx, 4001).lang, 'uk');
    const bad = await httpJson(base, 'POST', '/tg/' + ctx.cfg.webhookSecret, upd, { 'x-telegram-bot-api-secret-token': 'nope' });
    assert.equal(bad.status, 403);
  });

  console.log('Tasks, shop, wallet');
  await test('tasks: auto + channel membership via getChatMember', async () => {
    const P = new Player(env, 5001); await P.auth();
    const t = await P.api('GET', '/api/tasks');
    const ids = t.json.tasks.map((x) => x.id);
    assert.ok(ids.includes('channel') && ids.includes('wallet') && ids.includes('invite1'));
    assert.equal((await P.api('POST', '/api/tasks/claim', { id: 'wallet' })).json.err, 'notyet');
    assert.equal((await P.api('POST', '/api/tasks/claim', { id: 'channel' })).json.err, 'not_member');
    env.mock.members.add(5001);
    const c = await P.api('POST', '/api/tasks/claim', { id: 'channel' });
    assert.equal(c.json.ok, true);
    assert.equal(c.json.pts, 40);
    assert.equal((await P.api('POST', '/api/tasks/claim', { id: 'channel' })).json.err, 'done');
    assert.ok(env.mock.calls.some((x) => x.method === 'getChatMember' && x.body.chat_id === '@od_news'));
  });
  await test('Stars shop: invoice, pre-checkout validation, fulfilment, idempotency, revenue ledger', async () => {
    const P = new Player(env, 5002); await P.auth();
    const inv = await P.api('POST', '/api/shop/invoice', { sku: 'gems_m' });
    assert.equal(inv.json.ok, true);
    const call = env.mock.calls.filter((c) => c.method === 'createInvoiceLink').pop().body;
    assert.equal(call.currency, 'XTR'); assert.equal(call.prices[0].amount, 100); assert.equal(call.provider_token, '');
    const payload = call.payload;
    const wh = (u) => httpJson(base, 'POST', '/tg/' + ctx.cfg.webhookSecret, u, { 'x-telegram-bot-api-secret-token': ctx.cfg.webhookSecret });
    await wh({ update_id: 5, pre_checkout_query: { id: 'q1', from: { id: 5002 }, currency: 'XTR', total_amount: 100, invoice_payload: payload } });
    await wh({ update_id: 6, pre_checkout_query: { id: 'q2', from: { id: 5002 }, currency: 'XTR', total_amount: 1, invoice_payload: payload } });
    await wh({ update_id: 7, pre_checkout_query: { id: 'q3', from: { id: 9999 }, currency: 'XTR', total_amount: 100, invoice_payload: payload } });
    await new Promise((r) => setTimeout(r, 80));
    const answers = env.mock.calls.filter((c) => c.method === 'answerPreCheckoutQuery').map((c) => [c.body.pre_checkout_query_id, c.body.ok]);
    assert.deepEqual(answers.slice(-3), [['q1', true], ['q2', false], ['q3', false]]);
    const gems0 = U.loadState(ctx, 5002).gems;
    const pay = { update_id: 8, message: { message_id: 3, from: { id: 5002, first_name: 'P' }, chat: { id: 5002, type: 'private' }, successful_payment: { currency: 'XTR', total_amount: 100, invoice_payload: payload, telegram_payment_charge_id: 'ch_1', provider_payment_charge_id: 'x' } } };
    await wh(pay); await wh(pay);
    await new Promise((r) => setTimeout(r, 80));
    assert.equal(U.loadState(ctx, 5002).gems - gems0, 70, 'credited exactly once');
    const rev = ctx.db.get('SELECT * FROM revenue WHERE day=?', U.dayKey(clock.t));
    assert.equal(rev.stars, 100);
    assert.ok(Math.abs(rev.usd - 100 * ctx.cfg.starUsd) < 1e-9);
    // the client sees the new gems on its next sync (full state adoption)
    const r = await P.sync();
    assert.equal(P.S.gems, gems0 + 70);
    // one-time offer
    assert.equal((await P.api('POST', '/api/shop/invoice', { sku: 'starter' })).json.ok, true);
    const pl2 = env.mock.calls.filter((c) => c.method === 'createInvoiceLink').pop().body.payload;
    await wh({ update_id: 9, message: { message_id: 4, from: { id: 5002 }, chat: { id: 5002, type: 'private' }, successful_payment: { currency: 'XTR', total_amount: 50, invoice_payload: pl2, telegram_payment_charge_id: 'ch_2' } } });
    await new Promise((r) => setTimeout(r, 60));
    assert.equal((await P.api('POST', '/api/shop/invoice', { sku: 'starter' })).status, 409, 'starter pack is one-time');
    assert.equal((await P.api('POST', '/api/shop/invoice', { sku: 'hack' })).status, 409);
    await wh({ update_id: 10, message: { message_id: 5, from: { id: 5002 }, chat: { id: 5002, type: 'private' }, successful_payment: { currency: 'XTR', total_amount: 100, invoice_payload: '5002:pass30:x', telegram_payment_charge_id: 'ch_3' } } });
    await new Promise((r) => setTimeout(r, 60));
    assert.equal(ctx.db.get('SELECT 1 AS x FROM purchases WHERE charge_id=?', 'ch_3'), undefined, 'amount mismatch is ignored');
  });
  await test('verification: any contribution (donation, shop purchase, old TON payment) verifies; the claim fee and the developer account do not', async () => {
    const Ad = require('../server/airdrop.js');
    const ids = [6201, 6202, 6203, 6204];
    for (const id of ids) { const P = new Player(env, id); await P.auth(); }
    assert.equal(Ad.entered(ctx, 6201), false, 'nothing paid → not verified');
    Eco.fulfilDonation(ctx, 6201, 'v_don', 100, clock.t);
    assert.equal(Ad.entered(ctx, 6201), true, 'a Stars donation verifies');
    Eco.fulfil(ctx, 6202, 'gems_s', 'v_shop', 25, clock.t);
    assert.equal(Ad.entered(ctx, 6202), true, 'a shop purchase verifies');
    ctx.db.run("INSERT INTO purchases(charge_id,uid,sku,stars,ts,meta) VALUES('ton:old1',6203,'donate',0,?,'{\"usd\":2}')", clock.t);
    assert.equal(Ad.entered(ctx, 6203), true, 'a payment from the TON days verifies');
    ctx.db.run("INSERT INTO purchases(charge_id,uid,sku,stars,ts,meta) VALUES('sol:fee1',6204,'adfee',0,?,'{}')", clock.t);
    assert.equal(Ad.entered(ctx, 6204), false, 'the claim fee alone does not');
    const dev0 = ctx.cfg.devIds; ctx.cfg.devIds = [6201];
    assert.equal(Ad.entered(ctx, 6201), false, 'the developer test account is never verified');
    ctx.cfg.devIds = dev0;
  });
  await test('airdrop entry in Stars: invoice, pre-checkout, verified once, counts toward the fund, no second entry', async () => {
    const P = new Player(env, 5009); await P.auth();
    const Ad = require('../server/airdrop.js');
    const n = Ad.entryStars(ctx.cfg);
    assert.ok(n >= 5 && n % 5 === 0);
    assert.equal(P.eco.airdrop.entry.stars, n);
    const inv = await P.api('POST', '/api/airdrop/stars', {});
    assert.equal(inv.json.ok, true, JSON.stringify(inv.json));
    const call = env.mock.calls.filter((c) => c.method === 'createInvoiceLink').pop().body;
    assert.equal(call.currency, 'XTR'); assert.equal(call.prices[0].amount, n);
    const wh = (u) => httpJson(base, 'POST', '/tg/' + ctx.cfg.webhookSecret, u, { 'x-telegram-bot-api-secret-token': ctx.cfg.webhookSecret });
    await wh({ update_id: 51, pre_checkout_query: { id: 'e1', from: { id: 5009 }, currency: 'XTR', total_amount: n, invoice_payload: call.payload } });
    await wh({ update_id: 52, pre_checkout_query: { id: 'e2', from: { id: 5009 }, currency: 'XTR', total_amount: 1, invoice_payload: call.payload } });
    await new Promise((r) => setTimeout(r, 80));
    const answers = env.mock.calls.filter((c) => c.method === 'answerPreCheckoutQuery').map((c) => [c.body.pre_checkout_query_id, c.body.ok]);
    assert.deepEqual(answers.slice(-2), [['e1', true], ['e2', false]]);
    const fund0 = Eco.fundView(ctx, 0).usd;
    const pay = { update_id: 53, message: { message_id: 9, from: { id: 5009, first_name: 'P' }, chat: { id: 5009, type: 'private' }, successful_payment: { currency: 'XTR', total_amount: n, invoice_payload: call.payload, telegram_payment_charge_id: 'ent_1' } } };
    await wh(pay); await wh(pay);
    await new Promise((r) => setTimeout(r, 80));
    assert.equal(ctx.db.get("SELECT COUNT(*) AS c FROM purchases WHERE uid=5009 AND sku='adentry'").c, 1, 'recorded once');
    assert.equal(Ad.entered(ctx, 5009), true);
    ctx.cache.fund = null;
    assert.ok(Eco.fundView(ctx, 0).usd - fund0 >= ctx.cfg.airdrop.entryUsd * 0.99, 'counts toward the launch fund');
    const rep = require('../server/bot.js').fundReport(ctx);
    assert.ok(rep.includes('$' + Eco.fundView(ctx, 0).usd.toFixed(2)), '/fund total matches the fund: ' + rep.split('\n')[0]);
    assert.match(rep, /верификация · ⭐/);
    assert.equal((await P.api('POST', '/api/airdrop/stars', {})).json.err, 'ad_entered');
    assert.equal((await P.api('GET', '/api/friends')).json.eco.airdrop.entry.done, true);
  });
  await test('owner /dev: buttons for the new look and the sandbox; other players get nothing', async () => {
    const wh = (u) => httpJson(base, 'POST', '/tg/' + ctx.cfg.webhookSecret, u, { 'x-telegram-bot-api-secret-token': ctx.cfg.webhookSecret });
    const msg = (id, uid) => ({ update_id: id, message: { message_id: id, from: { id: uid, first_name: 'O' }, chat: { id: uid, type: 'private' }, text: '/dev' } });
    const pu0 = ctx.cfg.publicUrl, dev0 = ctx.cfg.devIds; ctx.cfg.publicUrl = 'https://od.example'; ctx.cfg.devIds = [6301];
    const n0 = env.mock.calls.length;
    await wh(msg(61, 6302)); await new Promise((r) => setTimeout(r, 60));
    assert.equal(env.mock.calls.slice(n0).filter((c) => c.method === 'sendMessage').length, 0, 'not for players');
    await wh(msg(62, 6301)); await new Promise((r) => setTimeout(r, 60));
    const sent = env.mock.calls.slice(n0).filter((c) => c.method === 'sendMessage').pop();
    ctx.cfg.publicUrl = pu0; ctx.cfg.devIds = dev0;
    assert.ok(sent, 'owner gets the menu');
    const urls = sent.body.reply_markup.inline_keyboard.flat().map((b) => b.web_app.url);
    assert.deepEqual(urls, ['https://od.example/?art=2', 'https://od.example/?art=1', 'https://od.example/?sandbox=1&art=2', 'https://od.example/?sandbox=new&art=2']);
  });
  await test('Office Pass lifts the daily ceiling by 25%', async () => {
    const P = new Player(env, 5003); await P.auth();
    assert.equal(P.eco.D, 300);
    Eco.fulfil(ctx, 5003, 'pass30', 'ch_pass', 150, clock.t);
    P.tick(4); const r = await P.sync();
    assert.equal(r.json.eco.D, 375);
    assert.ok(r.json.eco.pass > clock.t);
  });
  await test('supporters (a Stars purchase in the last 30 days) get +10% on gameplay points only', async () => {
    const A = new Player(env, 5101); await A.auth();
    const B = new Player(env, 5102); await B.auth();
    Eco.fulfil(ctx, 5102, 'gems_s', 'ch_sup', 25, clock.t);
    const ua = U.getUser(ctx, 5101), ub = U.getUser(ctx, 5102);
    const ga = Eco.credit(ctx, ua, 'play', 100, { now: clock.t });
    const gb = Eco.credit(ctx, ub, 'play', 100, { now: clock.t });
    assert.ok(Math.abs(gb / ga - 1.1) < 0.001, `boost ${gb / ga}`);
    const fa = Eco.credit(ctx, ua, 'task', 20, { now: clock.t, fixed: true });
    const fb = Eco.credit(ctx, ub, 'task', 20, { now: clock.t, fixed: true });
    assert.equal(fa, fb); // tasks / check-ins are not boosted
    assert.ok(Eco.ecoView(ctx, ub, clock.t).sup > clock.t);
    const later = clock.t + 31 * 86400e3;
    assert.equal(Eco.ecoView(ctx, ub, later).sup, 0); // expires
  });
  await test('solana wallet: base58 address is saved for the airdrop, bad ones rejected, a shared address raises risk', async () => {
    const P = new Player(env, 5011); await P.auth();
    const sol = '9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM';
    assert.equal((await P.api('POST', '/api/sol', { address: 'UQCVnGKCqV4RlcKVppaFi6CZic2oB3JTpgW4ZF7ifPO0UTsG' })).json.err, 'sol_address');
    assert.equal((await P.api('POST', '/api/sol', { address: '0OIl' + sol.slice(4) })).json.err, 'sol_address');
    const ok = await P.api('POST', '/api/sol', { address: ' ' + sol + ' ' });
    assert.equal(ok.json.ok, true); assert.equal(ok.json.eco.sol, sol);
    const Q = new Player(env, 5012); await Q.auth();
    const h0 = U.getUser(ctx, 5012).risk_hard;
    await Q.api('POST', '/api/sol', { address: sol });
    assert.ok(U.getUser(ctx, 5012).risk_hard > h0);
    const off = await P.api('POST', '/api/sol', { disconnect: true });
    assert.equal(off.json.eco.sol, null);
  });

  console.log('Anti-bot');
  await test('a normal player stays "ok"', async () => {
    const P = new Player(env, 6001, { premium: true, username: 'human' }); await P.auth();
    for (let i = 0; i < 10; i++) { P.play(240); await P.sync(); }
    AB.evaluate(ctx, U.getUser(ctx, 6001), clock.t);
    assert.equal(U.getUser(ctx, 6001).risk_state, 'ok');
  });
  await test('metronome auto-clicker is flagged (tap rhythm)', async () => {
    const P = new Player(env, 6002, { regular: true, platform: 'unknown' }); await P.auth();
    for (let i = 0; i < 20; i++) { P.play(240); await P.sync(); }
    const u = U.getUser(ctx, 6002);
    AB.evaluate(ctx, u, clock.t); Eco.persistUser(ctx, u);
    const sig = AB.signalsFor(ctx, u, clock.t);
    assert.ok(sig.tap_regular >= 20, 'tap rhythm signal: ' + JSON.stringify(sig));
    assert.ok(U.getUser(ctx, 6002).risk_state !== 'ok', 'state: ' + U.getUser(ctx, 6002).risk_state + ' risk ' + u.risk);
  });
  await test('24/7 activity (never sleeps) is flagged', async () => {
    const P = new Player(env, 6003); await P.auth();
    const u0 = U.getUser(ctx, 6003);
    const dk = U.dayKey(clock.t);
    for (let d = 0; d < 3; d++) ctx.db.run('INSERT OR REPLACE INTO beh(uid,day,hours,taps,n,mean,m2,syncs,actions,secs) VALUES(?,?,?,?,?,?,?,?,?,?)', 6003, dk - d, 0xffffff, 4000, 3000, 400, 300000, 800, 30, 80000);
    const sig = AB.signalsFor(ctx, U.getUser(ctx, 6003), clock.t);
    assert.ok(sig.coverage24 >= 40, JSON.stringify(sig));
  });
  await test('many accounts on one device+network are flagged; referral farms are flagged', async () => {
    const farmer = new Player(env, 6100, { fp: 'farm-device' }); await farmer.auth();
    const kids = [];
    for (let i = 0; i < 10; i++) {
      const k = new Player(env, 6101 + i, { fp: 'farm-device' }); // same device fingerprint, same loopback IP
      await k.auth('ref_' + farmer.eco.ref.code);
      kids.push(k);
    }
    const sigK = AB.signalsFor(ctx, U.getUser(ctx, 6105), clock.t);
    assert.ok(sigK.device_cluster >= 30, JSON.stringify(sigK));
    assert.ok(sigK.ref_cluster >= 35, JSON.stringify(sigK));
    const sigF = AB.signalsFor(ctx, U.getUser(ctx, 6100), clock.t);
    assert.ok(sigF.ref_farm >= 40 || sigF.device_cluster, JSON.stringify(sigF));
    // flagged kids earn nothing for the farmer even when they "play"
    for (const k of kids.slice(0, 3)) { for (let i = 0; i < 3; i++) { k.play(160); await k.sync(); } }
    env.advance(DAY);
    for (const k of kids.slice(0, 3)) { k.play(40); await k.sync(); }
    for (const k of kids.slice(0, 3)) assert.equal(U.getUser(ctx, k.id).ref_active, 0, 'no activation for flagged accounts');
    assert.equal(U.getUser(ctx, 6100).ref_pts, 0);
  });
  await test('quarantine pays zero, watch pays half; challenge flow restores trust', async () => {
    const P = new Player(env, 6200, { premium: true }); await P.auth();
    const u = () => U.getUser(ctx, 6200);
    ctx.db.run("UPDATE users SET risk_hard=50, risk_state='watch' WHERE id=?", 6200);
    P.play(240); const r = await P.sync();
    assert.ok(r.json.challenge, 'watch state triggers a human check');
    const ch = r.json.challenge;
    assert.ok(ch.id && ch.cells && ch.cells.length >= 8);
    assert.ok(!('ans' in ch), 'the answer is not leaked');
    const stored = JSON.parse(u().challenge);
    const wrong = await P.api('POST', '/api/challenge', { id: ch.id, a: -1 });
    assert.equal(wrong.json.ok, false);
    assert.ok(u().risk_hard > 50, 'wrong answer raises risk');
    ctx.db.run("UPDATE users SET risk_hard=50, challenge=NULL WHERE id=?", 6200);
    P.play(8); const r2 = await P.sync();
    const ch2 = r2.json.challenge; const st2 = JSON.parse(u().challenge);
    const right = await P.api('POST', '/api/challenge', { id: ch2.id, a: st2.ans });
    assert.equal(right.json.ok, true);
    assert.ok(u().human_until > clock.t);
    assert.ok(u().risk_hard < 50);
    // quarantine => zero credit
    ctx.db.run("UPDATE users SET risk_state='quarantine', risk_note='admin:quarantine' WHERE id=?", 6200);
    const p0 = u().pts;
    P.play(400); await P.sync();
    assert.equal(u().pts, p0, 'quarantined accounts earn nothing');
    ctx.db.run("UPDATE users SET risk_state='ok', risk_note='admin:clear' WHERE id=?", 6200);
    const p1 = u().pts;
    P.play(400); await P.sync();
    assert.ok(u().pts > p1, 'cleared account earns again');
  });

  console.log('Admin & misc');
  await test('admin API: key required, stats, ban/clear, snapshot csv, lookup', async () => {
    assert.equal((await httpJson(base, 'GET', '/admin/api/stats')).status, 401);
    const H = { 'x-admin-key': 'adminkey123' };
    const st = await httpJson(base, 'GET', '/admin/api/stats', undefined, H);
    assert.equal(st.json.ok, true);
    assert.ok(st.json.users > 20 && st.json.top.length > 0);
    const q = await httpJson(base, 'GET', '/admin/api/risk?state=watch', undefined, H);
    assert.equal(q.json.ok, true);
    const ban = await httpJson(base, 'POST', '/admin/api/user', { id: 6101, action: 'ban' }, H);
    assert.equal(ban.json.state, 'banned');
    assert.equal(U.getUser(ctx, 6101).risk_state, 'banned');
    AB.evaluate(ctx, U.getUser(ctx, 6101), clock.t);
    assert.equal(U.getUser(ctx, 6101).risk_state, 'banned', 'admin decisions are sticky');
    const d = await httpJson(base, 'GET', '/admin/api/user?id=1001', undefined, H);
    assert.equal(d.json.user.id, 1001);
    const snap = await fetch(base + '/admin/api/snapshot?fmt=csv', { headers: H });
    const csv = await snap.text();
    assert.ok(csv.startsWith('tg_id,username,country,solana_wallet'));
    assert.ok(!/^6101,/m.test(csv), 'banned players are not in the snapshot');
    assert.ok(/^1001,/m.test(csv));
    const page = await fetch(base + '/admin'); assert.equal(page.status, 200);
    const pie = await httpJson(base, 'GET', '/admin/api/stats?price=0.001', undefined, H);
    assert.ok(pie.json.pie.pie >= 0);
  });
  await test('leaderboard hides flagged accounts and returns my rank', async () => {
    const lb = await A.api('GET', '/api/leaderboard');
    assert.equal(lb.json.ok, true);
    assert.ok(lb.json.top.length > 3);
    assert.ok(lb.json.top.every((r, i, a) => i === 0 || a[i - 1].pts >= r.pts));
    assert.ok(lb.json.me && lb.json.me.rank >= 1);
    assert.ok(!lb.json.top.some((r) => r.name === 'P6101'));
  });
  await test('rate limiting kicks in', async () => {
    const P = new Player(env, 7001); await P.auth();
    let limited = 0;
    for (let i = 0; i < 60; i++) { const r = await P.api('POST', '/api/sync', { seq: P.cl.seq, batch: [], tail: 0 }); if (r.status === 429) limited++; }
    assert.ok(limited > 0);
  });
  await test('"forget me" removes the player\'s data', async () => {
    const P = new Player(env, 7002); await P.auth();
    assert.equal((await P.api('POST', '/api/forget', {})).status, 400);
    assert.equal((await P.api('POST', '/api/forget', { confirm: true })).json.ok, true);
    assert.equal(ctx.db.get('SELECT json FROM states WHERE uid=?', 7002), undefined);
    assert.equal((await P.api('POST', '/api/sync', { seq: 0, batch: [], tail: 0 })).status, 404);
  });
  await test('input hygiene: oversized / malformed bodies and unknown routes', async () => {
    const P = new Player(env, 7003); await P.auth();
    const big = await fetch(base + '/api/sync', { method: 'POST', headers: { 'content-type': 'application/json', ...P.headers() }, body: 'x'.repeat(200000) });
    assert.ok([400, 413].includes(big.status));
    const junk = await fetch(base + '/api/sync', { method: 'POST', headers: { 'content-type': 'application/json', ...P.headers() }, body: '{nope' });
    assert.equal(junk.status, 400);
    assert.equal((await httpJson(base, 'GET', '/api/nothing', undefined, P.headers())).status, 404);
    assert.equal((await fetch(base + '/../../etc/passwd')).status, 404);
    const art = await fetch(base + '/art/cafe.webp'); assert.equal(art.status, 200, 'illustrated backgrounds are served'); assert.equal(art.headers.get('content-type'), 'image/webp');
    assert.equal((await fetch(base + '/art/../server/config.js')).status, 404);
    assert.equal((await fetch(base + '/art/sub/cafe.webp')).status, 404);
  });

  await test('admin: channel post with a Play button (drafts + publish)', async () => {
    const h = { 'x-admin-key': 'adminkey123' };
    const d = await httpJson(base, 'GET', '/admin/api/drafts', undefined, h);
    assert.ok(d.json.drafts.length >= 5 && d.json.channel === '@od_news');
    const r = await httpJson(base, 'POST', '/admin/api/post', { text: d.json.drafts[0].text, button: true, photo: true }, h);
    assert.ok(r.json.ok, JSON.stringify(r.json));
    const call = env.mock.calls.filter((c) => c.method === 'sendPhoto').pop();
    assert.equal(call.body.chat_id, '@od_news');
    assert.equal(call.body.reply_markup.inline_keyboard[0][0].url, 'https://t.me/od_test_bot/play');
    assert.equal((await httpJson(base, 'POST', '/admin/api/post', { text: 'x' })).status, 401);
    const page = await (await fetch(base + '/admin')).text();
    assert.ok(page.includes('Publish to channel'));
  });

  await env.close();

  console.log('Rewarded ads');
  {
    const env2 = await makeEnv({ ADSGRAM_BLOCK_ID: '1234' });
    await test('ads: reward energy + cash + a few points, cooldown and daily cap', async () => {
      const P = new Player(env2, 8001); await P.auth();
      const cfg = await httpJson(env2.base, 'GET', '/api/config');
      assert.equal(cfg.json.ads.block, '1234');
      assert.equal(P.eco.ads.left, 8);
      const st0 = U.loadState(env2.ctx, 8001); st0.energy = 0; U.saveState(env2.ctx, 8001, st0, env2.clock.t);
      let r = await httpJson(env2.base, 'POST', '/api/ads/claim', {}, P.headers());
      assert.ok(r.json.ok, JSON.stringify(r.json));
      assert.equal(r.json.pts, 4);
      assert.ok(r.json.cash >= 100);
      const st = U.loadState(env2.ctx, 8001);
      assert.equal(st.energy, E.maxEnergy(st));
      r = await httpJson(env2.base, 'POST', '/api/ads/claim', {}, P.headers());
      assert.equal(r.json.err, 'ads_wait');
      let got = 1;
      for (let i = 0; i < 12; i++) {
        env2.advance(46e3);
        r = await httpJson(env2.base, 'POST', '/api/ads/claim', {}, P.headers());
        if (r.json.ok) got++;
      }
      assert.equal(got, 8);
      assert.equal(r.json.err, 'ads_limit');
      const u = U.getUser(env2.ctx, 8001);
      assert.ok(u.pts <= 8 * 4 + 1, 'ads cannot farm unlimited points: ' + u.pts);
    });
    await env2.close();
    const env3 = await makeEnv({ ADSGRAM_BLOCK_ID: '1234', ADS_CALLBACK_KEY: 'cbsecret123' });
    await test('ads callback mode: only the ad network callback with the key grants rewards', async () => {
      const P = new Player(env3, 8101); await P.auth();
      let r = await httpJson(env3.base, 'POST', '/api/ads/claim', {}, P.headers());
      assert.equal(r.json.err, 'ads_callback');
      r = await httpJson(env3.base, 'GET', '/api/ads/cb?key=wrong-key!&uid=8101');
      assert.equal(r.status, 403);
      r = await httpJson(env3.base, 'GET', '/api/ads/cb?key=cbsecret123&uid=8101');
      assert.ok(r.json.ok);
    });
    await env3.close();
    const env4 = await makeEnv({ MONETAG_ZONE: '777001', MONETAG_POSTBACK_KEY: 'mtsecret999' });
    await test('Monetag: config, postback rewards only valued impressions, once per ymid', async () => {
      const P = new Player(env4, 8201); await P.auth();
      const cfg = await httpJson(env4.base, 'GET', '/api/config');
      assert.equal(cfg.json.ads.provider, 'monetag');
      assert.equal(cfg.json.ads.block, '777001');
      assert.ok(cfg.json.ads.sdk.startsWith('https://'));
      const pb = (q) => httpJson(env4.base, 'GET', '/api/ads/monetag?key=mtsecret999&' + q);
      let r = await pb('ymid=8201-abc123&rt=non_valued&et=impression');
      assert.ok(r.json.skipped);
      r = await pb('ymid=8201-abc123&rt=valued&et=impression');
      assert.ok(r.json.ok && r.json.pts === 4, JSON.stringify(r.json));
      r = await pb('ymid=8201-abc123&rt=valued&et=impression');
      assert.ok(r.json.dup, 'retry is not paid twice');
      assert.equal((await httpJson(env4.base, 'GET', '/api/ads/monetag?key=nope&ymid=8201-x1y2z3&rt=valued')).status, 403);
      assert.equal((await pb('ymid=evil&rt=valued')).status, 400);
    });
    await env4.close();

    const shopWallet = require('../server/sol.js').randomKey();
    // a fake Solana chain: transfers into the shop wallet, served through getSignaturesForAddress / getTransaction
    const chain = [];
    const solTx = (lamports, ref, sig) => chain.unshift({ sig, lamports, ref, from: 'Sender1111111111111111111111111111111111111', t: Math.floor(env5.clock.t / 1000) });
    const env5 = await makeEnv({ SOL_PAY_WALLET: shopWallet, SOL_USD: '100', SOL_STAR_USD: '0.016' });
    env5.ctx.solRpc = async (method, params) => {
      if (method === 'getSignaturesForAddress') return chain.map((x) => ({ signature: x.sig, err: null, blockTime: x.t }));
      const x = chain.find((y) => y.sig === params[0]);
      const keys = [x.from, shopWallet].concat(x.ref ? [x.ref] : []);
      return { blockTime: x.t, meta: { err: null, preBalances: [5e9, 1e9, 0], postBalances: [5e9 - x.lamports - 5000, 1e9 + x.lamports, 0] }, transaction: { message: { accountKeys: keys } } };
    };
    await test('SOL payments (Solana Pay): order → transfer with the reference or the exact amount → item granted once', async () => {
      const SolPay = require('../server/solpay.js');
      const P = new Player(env5, 8301); await P.auth();
      const cfg = await httpJson(env5.base, 'GET', '/api/config');
      assert.equal(cfg.json.solPay, true);
      const shop = await P.api('GET', '/api/shop');
      const gs = shop.json.items.find((x) => x.sku === 'gems_s');
      assert.equal(gs.sol, 0.004, '25 stars × $0.016 / $100 = 0.004 SOL');
      const o = (await P.api('POST', '/api/shop/sol', { sku: 'gems_s' })).json;
      assert.ok(o.ok && o.order.to === shopWallet && /^solana:/.test(o.order.url) && o.order.url.includes('reference=' + o.order.ref), JSON.stringify(o));
      const lam = Number(o.order.lamports);
      assert.ok(lam > 4000000 && lam < 5000000 && lam % 1000 === 0, 'base price + a unique µSOL tail');
      const gems0 = U.loadState(env5.ctx, 8301).gems;
      const check = async (Pl, id) => { env5.advance(6000); return (await (Pl || P).api('POST', '/api/shop/sol/check', { id: id || o.order.id })).json; };
      assert.equal((await check()).paid, false);
      solTx(lam - 1000, null, 'sig_low'); solTx(lam + 1000, null, 'sig_other');
      assert.equal((await check()).paid, false, 'a different amount without the reference does not pay');
      solTx(lam, o.order.ref, 'sig_ok');
      assert.equal((await check()).paid, true);
      assert.equal(U.loadState(env5.ctx, 8301).gems - gems0, 15);
      assert.equal((await check()).paid, true);
      assert.equal(U.loadState(env5.ctx, 8301).gems - gems0, 15, 'never granted twice');
      // another player cannot check someone else's order
      const Q = new Player(env5, 8302); await Q.auth();
      assert.equal((await check(Q)).err, 'unavailable');
      // a plain transfer of the exact amount (no reference) is matched by the background sweep
      const o2 = (await Q.api('POST', '/api/shop/sol', { sku: 'starter' })).json;
      solTx(Number(o2.order.lamports), null, 'sig_q');
      env5.advance(6000);
      const r = await SolPay.sweep(env5.ctx, env5.clock.t);
      assert.equal(r.paid, 1);
      assert.equal((await Q.api('POST', '/api/shop/sol', { sku: 'starter' })).json.err, 'unavailable', 'one-time item cannot be bought again');
      assert.ok(Eco.supporterUntil(env5.ctx, 8302, env5.clock.t) > env5.clock.t);
      // one transaction never pays two orders
      const o4 = (await Q.api('POST', '/api/shop/sol', { sku: 'gems_s' })).json;
      assert.equal((await check(Q, o4.order.id)).paid, false, 'old transfers do not pay a new order');
      // coin packs: fixed amounts, bigger pack = bigger bonus, first buy of a pack is doubled, VIP adds a bonus
      let sh = (await Q.api('GET', '/api/shop')).json;
      assert.equal(sh.items[0].sku, 'cash1');
      assert.equal(sh.vip.lvl, 0);
      assert.equal(sh.vip.next.left, 50, 'starter (50⭐ via SOL) counts toward VIP');
      const c3 = sh.items.find((x) => x.sku === 'cash3');
      assert.ok(c3.first && c3.cash === 50000, JSON.stringify(c3));
      const buy = async (sku, h) => {
        const before = U.loadState(env5.ctx, 8302);
        const o3 = (await Q.api('POST', '/api/shop/sol', { sku })).json;
        solTx(Number(o3.order.lamports) + 7, o3.order.ref, h);
        assert.equal((await check(Q, o3.order.id)).paid, true);
        const after = U.loadState(env5.ctx, 8302);
        assert.equal(after.earned, before.earned, 'bought coins do not unlock earning goals');
        return after.cash - before.cash;
      };
      assert.equal(await buy('cash3', 'sig_c1'), 50000, '20 000 +25% ×2 first purchase');
      sh = (await Q.api('GET', '/api/shop')).json;
      assert.equal(sh.vip.lvl, 2, '550⭐ → VIP 2');
      assert.equal(sh.items.find((x) => x.sku === 'cash3').cash, 27000, 'second purchase: 20 000 × (1 + 25% + VIP 10%)');
      assert.equal(await buy('cash3', 'sig_c2'), 27000);
    });
    await test('countries: detected from the device time zone, shown in admin stats', async () => {
      const A = new Player(env5, 8401); await A.auth('', { tz: 'Europe/Kiev' });
      const B = new Player(env5, 8402); await B.auth('', { tz: 'Europe/Berlin' });
      const C = new Player(env5, 8403); await C.auth('', { tz: '../../etc/passwd' });
      assert.equal(U.getUser(env5.ctx, 8401).country, 'UA');
      assert.equal(U.getUser(env5.ctx, 8402).country, 'DE');
      assert.equal(U.getUser(env5.ctx, 8403).country, null);
      const st = await httpJson(env5.base, 'GET', '/admin/api/stats', undefined, { 'x-admin-key': 'adminkey123' });
      const ua = st.json.countries.find((r) => r.c === 'UA');
      assert.ok(ua && ua.n === 1, JSON.stringify(st.json.countries));
      assert.ok(st.json.countries.some((r) => r.c === '?'));
    });
    await test('channel task: if the bot cannot see channel members, opening the channel is enough (no stuck task)', async () => {
      const P = new Player(env5, 8501); await P.auth();
      P.play(600); await P.sync();
      const prev = env5.ctx.tg.call;
      env5.ctx.tg.call = async (m, b) => (m === 'getChatMember' ? { ok: false, description: 'Bad Request: member list is inaccessible' } : prev(m, b));
      let r = await P.api('POST', '/api/tasks/claim', { id: 'channel' });
      assert.equal(r.json.err, 'not_member', 'not opened yet');
      assert.ok((await P.api('POST', '/api/tasks/start', { id: 'channel' })).json.ok);
      env5.advance(9000);
      r = await P.api('POST', '/api/tasks/claim', { id: 'channel' });
      assert.ok(r.json.ok, JSON.stringify(r.json));
      env5.ctx.tg.call = prev;
    });
    await test('closed regions: India / Indonesia cannot sign in (time zone or app language)', async () => {
      const A = new Player(env5, 8601); assert.equal((await A.auth('', { tz: 'Asia/Kolkata' })).status, 403);
      assert.equal(A.last.err, 'region');
      assert.ok(!U.getUser(env5.ctx, 8601), 'no account is created');
      const B = new Player(env5, 8602); assert.equal((await B.auth('', { tz: 'Asia/Jakarta' })).status, 403);
      const C = new Player(env5, 8603); C.user.language_code = 'id'; assert.equal((await C.auth('', { tz: 'Europe/Kyiv' })).status, 403);
      const D = new Player(env5, 8604); assert.equal((await D.auth('', { tz: 'Europe/Kyiv' })).status, 200);
      const st = await httpJson(env5.base, 'GET', '/admin/api/stats', undefined, { 'x-admin-key': 'adminkey123' });
      assert.deepEqual(st.json.blocked.countries, ['IN', 'ID', 'PK', 'BD', 'NG']);
      assert.ok(st.json.blocked.attempts >= 3);
    });
    await test('admin live table: online players with nick, ID and country; search by nick / ID / country', async () => {
      const H = { 'x-admin-key': 'adminkey123' };
      const P = new Player(env5, 8701, { username: 'neo_ua' }); await P.auth('', { tz: 'Europe/Kyiv' });
      let r = await httpJson(env5.base, 'GET', '/admin/api/live', undefined, H);
      assert.ok(r.json.ok && r.json.total > 0);
      r = await httpJson(env5.base, 'GET', '/admin/api/live?mode=all&q=@neo', undefined, H);
      assert.equal(r.json.rows.length, 1);
      assert.equal(r.json.rows[0].country, 'UA');
      assert.equal(r.json.rows[0].username, 'neo_ua');
      r = await httpJson(env5.base, 'GET', '/admin/api/live?mode=all&q=8701', undefined, H);
      assert.equal(r.json.rows[0].id, 8701);
      r = await httpJson(env5.base, 'GET', '/admin/api/live?mode=all&q=ua', undefined, H);
      assert.ok(r.json.rows.every((x) => x.country === 'UA') && r.json.rows.length >= 1);
      assert.equal((await httpJson(env5.base, 'GET', '/admin/api/live')).status, 401);
    });
    await test('donations: Stars (min 100, any amount via repeat) and TON; fund progress, VIP grows, no points', async () => {
      const P = new Player(env5, 8801, { username: 'donor' }); await P.auth('', { tz: 'Europe/Kyiv' });
      const pts0 = U.getUser(env5.ctx, 8801).pts;
      assert.equal((await P.api('POST', '/api/donate/invoice', { stars: 50 })).json.err, 'amount');
      const inv = await P.api('POST', '/api/donate/invoice', { stars: 300 });
      assert.ok(inv.json.ok && inv.json.url, JSON.stringify(inv.json));
      const call = env5.mock.calls.filter((c) => c.method === 'createInvoiceLink').pop().body;
      assert.equal(call.prices[0].amount, 300);
      // Telegram confirms the payment
      const Bot = require('../server/bot.js');
      await Bot.handleUpdate(env5.ctx, { pre_checkout_query: { id: 'pq1', from: { id: 8801 }, currency: 'XTR', total_amount: 300, invoice_payload: call.payload } });
      assert.ok(env5.mock.calls.some((c) => c.method === 'answerPreCheckoutQuery' && c.body.pre_checkout_query_id === 'pq1' && c.body.ok));
      const pay = { message_id: 1, from: { id: 8801 }, chat: { id: 8801, type: 'private' }, successful_payment: { currency: 'XTR', total_amount: 300, invoice_payload: call.payload, telegram_payment_charge_id: 'don-1' } };
      await Bot.handleUpdate(env5.ctx, { message: pay });
      await Bot.handleUpdate(env5.ctx, { message: pay }); // duplicate delivery
      let f = (await P.api('GET', '/api/donate')).json;
      assert.equal(f.mine, 300);
      assert.ok(f.stars >= 300 && f.donors >= 1);
      assert.equal(f.top[0].name !== undefined, true);
      // SOL donation: whatever actually arrived counts
      const o = (await P.api('POST', '/api/donate/sol', { sol: f.solMin })).json;
      assert.ok(o.ok, JSON.stringify(o));
      assert.equal((await P.api('POST', '/api/donate/sol', { sol: 0.0001 })).json.err, 'amount');
      solTx(1e8, o.order.ref, 'sig_don'); // 0.1 SOL = $10 = 625 ⭐
      env5.advance(6000);
      assert.equal((await P.api('POST', '/api/shop/sol/check', { id: o.order.id })).json.paid, true);
      env5.advance(6000);
      f = (await P.api('GET', '/api/donate')).json;
      assert.ok(f.mine > 300 + 400, 'SOL counted by the amount received: ' + f.mine);
      assert.equal(Eco.vipOf(env5.ctx, 8801).lvl >= 2, true, 'VIP grew');
      assert.equal(U.getUser(env5.ctx, 8801).pts, pts0, 'donations never buy points');
    });
    await test('late invite: a friend who opened the game before tapping the link is still attached (48 h)', async () => {
      const A = new Player(env5, 8901); await A.auth();
      const code = U.getUser(env5.ctx, 8901).ref_code;
      const B = new Player(env5, 8902, { fp: 'b-own-phone' }); await B.auth();
      assert.equal(U.getUser(env5.ctx, 8902).ref1, null);
      const cash0 = U.loadState(env5.ctx, 8902).cash;
      await B.auth('ref_' + code);
      assert.equal(U.getUser(env5.ctx, 8902).ref1, 8901, 'attached late');
      assert.equal(U.loadState(env5.ctx, 8902).cash - cash0, env5.ctx.cfg.eco.welcomeCash, 'welcome bonus');
      // no cycles, no re-binding, no binding after 48 h
      await A.auth('ref_' + U.getUser(env5.ctx, 8902).ref_code);
      assert.equal(U.getUser(env5.ctx, 8901).ref1, null, 'inviter cannot become their friend\'s friend');
      const C = new Player(env5, 8903); await C.auth();
      env5.advance(49 * 3600e3);
      await C.auth('ref_' + code);
      assert.equal(U.getUser(env5.ctx, 8903).ref1, null, 'too late');
      const st = await httpJson(env5.base, 'GET', '/admin/api/stats', undefined, { 'x-admin-key': 'adminkey123' });
      assert.ok(st.json.refRecent.some((r) => r.id === 8902 && r.inv_id === 8901), 'admin sees the referral');
    });
    await test('invite: bot deep link (/start ref_CODE) and the start param the page saw both attach the friend', async () => {
      const A = new Player(env5, 9001); await A.auth();
      const code = U.getUser(env5.ctx, 9001).ref_code;
      // Telegram dropped start_param from initData, the page still saw it
      const B = new Player(env5, 9002, { fp: 'b-phone' });
      const initData = TG.signInitData(BOT_TOKEN, B.user, {}, env5.clock.t);
      const r = await httpJson(env5.base, 'POST', '/api/auth', { initData, platform: 'ios', fp: 'b-phone', start: 'ref_' + code });
      assert.equal(r.status, 200);
      assert.equal(U.getUser(env5.ctx, 9002).ref1, 9001);
      // the invite link is the bot deep link; /start ref_CODE is remembered and carried by the Play button
      assert.equal(Eco.refLink(env5.ctx, code), 'https://t.me/od_test_bot?start=ref_' + code);
      const Bot = require('../server/bot.js');
      await Bot.handleUpdate(env5.ctx, { message: { message_id: 5, from: { id: 9004, first_name: 'Iphone' }, chat: { id: 9004, type: 'private' }, text: '/start ref_' + code } });
      const msg = env5.mock.calls.filter((c) => c.method === 'sendMessage' && c.body.chat_id === 9004).pop();
      assert.ok(msg && JSON.stringify(msg.body.reply_markup).includes('start=ref_' + code), 'Play button carries the code');
      const D = new Player(env5, 9004, { fp: 'd-phone' }); await D.auth(); // opened via the Play button, no start_param at all
      assert.equal(U.getUser(env5.ctx, 9004).ref1, 9001, 'attached through /start');
    });
    await env5.close();
  }
  {
    const env6 = await makeEnv({ DEV_IDS: '7777' });
    await test('developer account: lots of coins, no airdrop points, not in the top, gives no referral points', async () => {
      const D = new Player(env6, 7777); await D.auth();
      env6.ctx.db.run('UPDATE users SET pts=55 WHERE id=7777');
      await D.auth();
      const s = U.loadState(env6.ctx, 7777);
      assert.ok(s.cash >= 1e9 - 1 && s.gems >= 10000, 'coins topped up');
      const u = U.getUser(env6.ctx, 7777);
      assert.equal(u.pts, 0, 'points wiped');
      assert.equal(Eco.credit(env6.ctx, u, 'play', 100), 0, 'never earns points');
      assert.ok(!Eco.leaderboard(env6.ctx, 7777).top.some((r) => r.me), 'not in the top');
      const N = new Player(env6, 7778); await N.auth();
      assert.equal(Eco.isDev(env6.ctx, 7778), false, 'only the listed id');
    });
    await env6.close();
  }
  {
    console.log('Studio Battle (PvP)');
    const env7 = await makeEnv({});
    const PvP = require('../server/pvp.js');
    const A = new Player(env7, 7101, { name: 'Ann' }); await A.auth();
    const B = new Player(env7, 7102, { name: 'Bob' }); await B.auth();
    await test('pvp: two searching players are matched live, taps are capped by time, the winner gets trophies and coins', async () => {
      let r = await A.api('POST', '/api/pvp/find', {});
      assert.equal(r.json.st, 'search');
      r = await B.api('POST', '/api/pvp/find', {});
      assert.equal(r.json.st, 'battle', 'matched');
      assert.equal(r.json.op.kind, 'live');
      const id = r.json.id;
      env7.advance(PvP.P.COUNTDOWN + 10000);
      r = await A.api('POST', '/api/pvp/tick', { b: id, n: 99999 });
      assert.ok(r.json.me.n <= 15 * 10 + 8, 'impossible tap count is capped: ' + r.json.me.n);
      await B.api('POST', '/api/pvp/tick', { b: id, n: 40, e: 1 });
      r = await A.api('POST', '/api/pvp/tick', { b: id, n: 120 });
      assert.equal(r.json.op.e.i, 1, 'emote delivered');
      const cashA = U.loadState(env7.ctx, 7101).cash;
      env7.advance(PvP.P.DUR);
      await A.api('POST', '/api/pvp/tick', { b: id, n: 300 });
      env7.advance(PvP.P.GRACE + 50);
      r = await A.api('POST', '/api/pvp/tick', { b: id, n: 300 });
      assert.equal(r.json.st, 'result');
      assert.equal(r.json.res.win, 1, 'Ann won');
      assert.ok(r.json.res.dTr > 0 && r.json.res.cash > 0 && r.json.res.gems === 2, JSON.stringify(r.json.res));
      assert.ok(U.loadState(env7.ctx, 7101).cash > cashA, 'coins granted to the game state');
      const rb = await B.api('POST', '/api/pvp/tick', { b: id, n: 40 });
      assert.equal(rb.json.res.win, -1);
      assert.equal(rb.json.res.dTr <= 0, true);
      const info = await A.api('GET', '/api/pvp');
      assert.equal(info.json.me.w, 1);
      assert.equal(info.json.me.left, 4);
      assert.ok(info.json.top[0].me && /start=duel_/.test(info.json.link), 'top + duel link');
    });
    await test('pvp: nobody online → a recorded run of a real player (labelled), daily reward limit', async () => {
      const C = new Player(env7, 7103, { name: 'Cid' }); await C.auth();
      let r = await C.api('POST', '/api/pvp/find', {});
      assert.equal(r.json.st, 'search');
      env7.advance(PvP.P.GHOST_AFTER + 100);
      r = await C.api('POST', '/api/pvp/tick', {});
      assert.equal(r.json.st, 'battle');
      assert.equal(r.json.op.kind, 'rec', 'recorded opponent, honestly labelled');
      env7.advance(PvP.P.COUNTDOWN + 15000);
      r = await C.api('POST', '/api/pvp/tick', { n: 50 });
      assert.ok(r.json.op.n > 0, 'the recording plays back');
      env7.advance(PvP.P.DUR);
      r = await C.api('POST', '/api/pvp/tick', { n: 60 });
      env7.advance(PvP.P.GRACE + 10);
      r = await C.api('POST', '/api/pvp/tick', {});
      assert.equal(r.json.st, 'result');
      // abandoned battles are finished by the sweeper
      const D = new Player(env7, 7104, { name: 'Dan' }); await D.auth();
      await D.api('POST', '/api/pvp/find', {});
      env7.advance(PvP.P.GHOST_AFTER + 100);
      r = await D.api('POST', '/api/pvp/tick', {});
      assert.equal(r.json.st, 'battle');
      env7.advance(PvP.P.COUNTDOWN + PvP.P.DUR + 10000);
      PvP.sweep(env7.ctx, env7.clock.t);
      r = await D.api('POST', '/api/pvp/tick', {});
      assert.equal(r.json.st, 'result');
      assert.equal(r.json.res.dTr <= 0, true, 'AFK never gains trophies');
    });
    await test('pvp: friend challenge plays against the challenger\'s best run and notifies them', async () => {
      const code = U.getUser(env7.ctx, 7101).ref_code;
      const F = new Player(env7, 7105, { name: 'Fox' }); await F.auth('duel_' + code);
      assert.equal(U.getUser(env7.ctx, 7105).ref1, 7101, 'a duel link is also an invite');
      let r = await F.api('POST', '/api/pvp/find', { duel: 'duel_' + code });
      assert.equal(r.json.st, 'battle');
      assert.equal(r.json.op.kind, 'friend');
      assert.equal(r.json.op.name, 'Ann');
      env7.advance(PvP.P.COUNTDOWN + PvP.P.DUR - 100);
      await F.api('POST', '/api/pvp/tick', { n: 400 });
      env7.advance(PvP.P.GRACE + 200);
      r = await F.api('POST', '/api/pvp/tick', {});
      assert.equal(r.json.st, 'result');
      await new Promise((ok) => setTimeout(ok, 50));
      const msg = env7.mock.calls.filter((c) => c.method === 'sendMessage' && c.body.chat_id === 7101).pop();
      assert.ok(msg && /Fox/.test(msg.body.text), 'challenger notified');
      const self = await A.api('POST', '/api/pvp/find', { duel: code });
      assert.equal(self.json.err, 'self');
    });
    await test('studio names are unique (case, spaces, look-alike letters), shown in the battle top', async () => {
      let r = await A.api('POST', '/api/studio', { name: '  Ola  ' });
      assert.equal(r.json.ok, true); assert.equal(r.json.studio, 'Ola');
      for (const n of ['OLA', 'O la', 'О-la', 'ola!']) {
        r = await B.api('POST', '/api/studio', { name: n });
        assert.equal(r.json.err, 'taken', n);
      }
      r = await B.api('POST', '/api/studio', { name: 'x' }); assert.equal(r.json.err, 'len');
      r = await B.api('POST', '/api/studio', { name: 'Office Dreams' }); assert.equal(r.json.err, 'taken');
      r = await B.api('POST', '/api/studio', { name: 'Bob Games' }); assert.equal(r.json.ok, true);
      r = await A.api('POST', '/api/studio', { name: 'Ola' }); assert.equal(r.json.ok, true, 'keeping your own name is fine');
      const me = await A.auth();
      assert.equal(me.json.me.studio, 'Ola');
      const info = await A.api('GET', '/api/pvp');
      assert.ok(info.json.top.some((x) => x.name === 'Ola'), 'battle top shows the studio');
      const lb = await A.api('GET', '/api/leaderboard');
      assert.ok(lb.status === 200);
    });
    await env7.close();
  }
  {
    console.log('OFFICE airdrop');
    const { web3, spl } = require('../server/vendor/solana.js');
    const dist = web3.Keypair.generate(), mint = web3.Keypair.generate().publicKey.toBase58();
    const Sol = require('../server/sol.js');
    const env8 = await makeEnv({ DONATE_GOAL_USD: '100', TOKEN_ADDRESS: mint, AIRDROP_SECRET: Sol.b58encode(dist.secretKey), AIRDROP_ENTRY_USD: '0', AIRDROP_CLAIM_FEE_SOL: '0' });
    const Ad = require('../server/airdrop.js');
    const sent = [];
    env8.ctx.fastAnnounce = true;
    env8.ctx.solRpc = async (method, params) => {
      if (method === 'getAccountInfo') return { value: { owner: spl.TOKEN_2022_PROGRAM_ID.toBase58() } };
      if (method === 'getLatestBlockhash') return { value: { blockhash: web3.Keypair.generate().publicKey.toBase58(), lastValidBlockHeight: 10 } };
      if (method === 'sendTransaction') { sent.push(params[0]); return 'ok'; }
      if (method === 'getSignatureStatuses') return { value: [{ err: null, confirmationStatus: 'confirmed' }] };
      return null;
    };
    await test('airdrop: starts by itself at the fund goal, splits 90M by points, announces, each player claims once', async () => {
      const A = new Player(env8, 8601), B = new Player(env8, 8602), C = new Player(env8, 8603);
      await A.auth(); await B.auth(); await C.auth();
      env8.ctx.db.run('UPDATE users SET pts=? WHERE id=?', 300, 8601);
      env8.ctx.db.run('UPDATE users SET pts=? WHERE id=?', 100, 8602);
      env8.ctx.db.run("UPDATE users SET pts=?, risk_state='quarantine' WHERE id=?", 500, 8603); // a bot: excluded
      // below the goal nothing happens
      Eco.fulfilDonation(env8.ctx, 8601, 'd1', 1000, env8.clock.t, { usd: 60, meta: JSON.stringify({ usd: 60 }) });
      assert.equal((await Ad.tick(env8.ctx, env8.clock.t)).st, 'season');
      assert.equal((await A.api('POST', '/api/airdrop/claim', {})).json.err, 'airdrop_notyet');
      let v = (await A.api('GET', '/api/friends')).json.eco.airdrop;
      assert.equal(v.st, 'season'); assert.equal(v.goal, 100);
      // the goal is reached → snapshot + claim open (token and distributor are configured)
      Eco.fulfilDonation(env8.ctx, 8602, 'd2', 1000, env8.clock.t, { usd: 50, meta: JSON.stringify({ usd: 50 }) });
      env8.mock.calls.length = 0;
      assert.equal((await Ad.tick(env8.ctx, env8.clock.t)).st, 'claim');
      // the announcement is sent in the background: wait for it (up to 3 s)
      for (let i = 0; i < 60 && !env8.mock.calls.some((c) => c.method === 'sendMessage' && c.body.chat_id === 8601); i++) await new Promise((r) => setTimeout(r, 50));
      const msgs = env8.mock.calls.filter((c) => c.method === 'sendMessage');
      assert.ok(msgs.some((m) => m.body.chat_id === 8601 && /67\s?500\s?000/.test(m.body.text)), 'player told their share');
      assert.ok(msgs.some((m) => m.body.chat_id === '@od_news'), 'channel announcement');
      v = (await A.api('GET', '/api/friends')).json.eco.airdrop;
      assert.equal(v.st, 'claim'); assert.equal(v.amount, 67500000, '300 of 400 points → 67.5M of 90M');
      assert.equal((await B.api('GET', '/api/friends')).json.eco.airdrop.amount, 22500000);
      assert.equal((await C.api('GET', '/api/friends')).json.eco.airdrop.amount, 0, 'quarantined account gets nothing');
      // claim: needs a Solana wallet, then one signed transfer from the distributor
      assert.equal((await A.api('POST', '/api/airdrop/claim', {})).json.err, 'sol_need');
      const wallet = web3.Keypair.generate().publicKey.toBase58();
      await A.api('POST', '/api/sol', { address: wallet });
      const r = (await A.api('POST', '/api/airdrop/claim', {})).json;
      assert.equal(r.ok, true, JSON.stringify(r)); assert.equal(r.eco.airdrop.claim, 'sent');
      assert.equal(sent.length, 1);
      const tx = web3.Transaction.from(Buffer.from(sent[0], 'base64'));
      assert.ok(tx.verifySignatures(), 'signed by the distributor');
      assert.equal(tx.feePayer.toBase58(), dist.publicKey.toBase58());
      assert.equal(tx.instructions.length, 2, 'create the token account (idempotent) + transfer');
      const dst = spl.getAssociatedTokenAddressSync(new web3.PublicKey(mint), new web3.PublicKey(wallet), true, spl.TOKEN_2022_PROGRAM_ID);
      assert.ok(tx.instructions[1].keys.some((k) => k.pubkey.equals(dst)), 'to the player wallet');
      assert.equal(tx.instructions[1].data.readBigUInt64LE(1), 67500000n * 1000000n, 'exact share in base units');
      // never twice
      const again = (await A.api('POST', '/api/airdrop/claim', {})).json;
      assert.ok(again.ok && again.already); assert.equal(sent.length, 1);
      assert.equal((await C.api('POST', '/api/airdrop/claim', {})).json.err, 'sol_need');
      // admin view
      const adm = await httpJson(env8.base, 'GET', '/admin/api/airdrop', undefined, { 'x-admin-key': 'adminkey123' });
      assert.equal(adm.json.sent, 1); assert.equal(adm.json.distributor, dist.publicKey.toBase58());
    });
    await env8.close();
    // entry deposit (anti-bot, counts toward the fund) and the claim fee, both paid in SOL to the shop wallet
    const shop = Sol.randomKey();
    const env9 = await makeEnv({ DONATE_GOAL_USD: '100', TOKEN_ADDRESS: mint, AIRDROP_SECRET: Sol.b58encode(dist.secretKey), SOL_PAY_WALLET: shop, SOL_USD: '100', AIRDROP_ENTRY_USD: '1', AIRDROP_CLAIM_FEE_SOL: '0.01' });
    const chain9 = [];
    const pay9 = (lamports, ref, sig) => chain9.unshift({ sig, lamports, ref, t: Math.floor(env9.clock.t / 1000) });
    env9.ctx.fastAnnounce = true;
    env9.ctx.solRpc = async (method, params) => {
      if (method === 'getSignaturesForAddress') return chain9.map((x) => ({ signature: x.sig, err: null, blockTime: x.t }));
      if (method === 'getTransaction') { const x = chain9.find((y) => y.sig === params[0]); return { blockTime: x.t, meta: { err: null, preBalances: [5e9, 0, 0], postBalances: [5e9 - x.lamports, x.lamports, 0] }, transaction: { message: { accountKeys: ['Sender1111111111111111111111111111111111111', shop, x.ref] } } }; }
      if (method === 'getAccountInfo') return { value: { owner: spl.TOKEN_2022_PROGRAM_ID.toBase58() } };
      if (method === 'getLatestBlockhash') return { value: { blockhash: web3.Keypair.generate().publicKey.toBase58(), lastValidBlockHeight: 10 } };
      if (method === 'sendTransaction') return 'ok';
      if (method === 'getSignatureStatuses') return { value: [{ err: null, confirmationStatus: 'confirmed' }] };
      return null;
    };
    await test('airdrop entry deposit: only players who paid it share the 100M; claiming needs the fee; deposits fill the fund', async () => {
      const A = new Player(env9, 8701), B = new Player(env9, 8702);
      await A.auth(); await B.auth();
      env9.ctx.db.run('UPDATE users SET pts=? WHERE id IN (8701, 8702)', 100);
      let v = (await A.api('GET', '/api/friends')).json.eco.airdrop;
      assert.equal(v.entry.done, false); assert.equal(v.entry.sol, 0.01, '$1 at $100/SOL');
      const o = (await A.api('POST', '/api/airdrop/order', { kind: 'adentry' })).json;
      assert.ok(o.ok, JSON.stringify(o));
      pay9(Number(o.order.lamports), o.order.ref, 'e1'); env9.advance(6000);
      assert.equal((await A.api('POST', '/api/shop/sol/check', { id: o.order.id })).json.paid, true);
      assert.equal((await A.api('POST', '/api/airdrop/order', { kind: 'adentry' })).json.err, 'ad_entered');
      assert.equal(Eco.supporterUntil(env9.ctx, 8701, env9.clock.t), 0, 'the deposit gives no points bonus');
      assert.equal(U.getUser(env9.ctx, 8701).pts, 100, 'and no points');
      assert.ok(Eco.fundView(env9.ctx, 0).usd >= 1, 'counts toward the launch fund');
      // B never entered → no share; A gets everything
      Ad.snapshot(env9.ctx, env9.clock.t);
      await Ad.tick(env9.ctx, env9.clock.t);
      assert.equal((await A.api('GET', '/api/friends')).json.eco.airdrop.amount, 90000000);
      assert.equal((await B.api('GET', '/api/friends')).json.eco.airdrop.amount, 0);
      assert.equal((await B.api('POST', '/api/airdrop/order', { kind: 'adentry' })).json.err, 'ad_closed', 'no deposits after the snapshot');
      await A.api('POST', '/api/sol', { address: web3.Keypair.generate().publicKey.toBase58() });
      assert.equal((await A.api('POST', '/api/airdrop/claim', {})).json.err, 'ad_fee', 'fee first');
      const f = (await A.api('POST', '/api/airdrop/order', { kind: 'adfee' })).json;
      assert.ok(Number(f.order.lamports) >= 10000000 && Number(f.order.lamports) < 11000000);
      pay9(Number(f.order.lamports), f.order.ref, 'f1'); env9.advance(6000);
      assert.equal((await A.api('POST', '/api/shop/sol/check', { id: f.order.id })).json.paid, true);
      const r = (await A.api('POST', '/api/airdrop/claim', {})).json;
      assert.equal(r.ok, true, JSON.stringify(r));
    });
    await env9.close();
    // one-time mint of the whole supply by the server (the distributor holds the mint authority)
    const env10 = await makeEnv({ TOKEN_ADDRESS: mint, AIRDROP_SECRET: Sol.b58encode(dist.secretKey), AIRDROP_MINT: '1' });
    let supply = '0', sentTx = [];
    env10.ctx.solRpc = async (method, params) => {
      if (method === 'getAccountInfo') return { value: { owner: spl.TOKEN_2022_PROGRAM_ID.toBase58(), data: { parsed: { info: { mintAuthority: supply === '0' ? dist.publicKey.toBase58() : null, supply, decimals: 6 } } } } };
      if (method === 'getLatestBlockhash') return { value: { blockhash: web3.Keypair.generate().publicKey.toBase58() } };
      if (method === 'sendTransaction') { sentTx.push(params[0]); supply = '100000000000000'; return 'ok'; }
      if (method === 'getSignatureStatuses') return { value: [{ err: null, confirmationStatus: 'confirmed' }] };
      return null;
    };
    await test('token mint: the server mints exactly 100M to the distributor once and disables minting', async () => {
      const r = await Ad.autoMint(env10.ctx);
      assert.equal(r.ok, true, JSON.stringify(r));
      const tx = web3.Transaction.from(Buffer.from(sentTx[0], 'base64'));
      assert.ok(tx.verifySignatures());
      assert.equal(tx.instructions.length, 3);
      assert.equal(tx.instructions[1].data.readBigUInt64LE(1), 100000000n * 1000000n, 'exactly 100M');
      assert.equal(tx.instructions[2].data[0], 6, 'SetAuthority');
      assert.equal(tx.instructions[2].data[2], 0, 'new authority: none');
      env10.ctx.cache.minted = false;
      const again = await Ad.autoMint(env10.ctx);
      assert.equal(again.ok, false); assert.equal(sentTx.length, 1, 'never twice');
    });
    await env10.close();
    const env11 = await makeEnv({ AIRDROP_GENKEY: '1' });
    await test('distributor wallet generated by the server: one key, kept, address public in the config', async () => {
      const a1 = Ad.distributorAddr(env11.ctx);
      assert.ok(Sol.isAddr(a1));
      const cfg = await httpJson(env11.base, 'GET', '/api/config');
      assert.equal(cfg.json.airdropWallet, a1);
      assert.ok(!JSON.stringify(cfg.json).includes(env11.ctx.db.get('SELECT secret FROM airdrop_key').secret), 'the secret never leaves the server');
      assert.equal(env11.ctx.db.get('SELECT COUNT(*) AS n FROM airdrop_key').n, 1);
    });
    await env11.close();
  }
  {
    console.log('Sponsor giveaway');
    const envG = await makeEnv({ GIVEAWAY_ON: '1', GIVEAWAY_KEY: 'g-test', GIVEAWAY_DAYS: '3', GIVEAWAY_PRIZES: '20,10,5', AIRDROP_GENKEY: '1', SOL_USD: '100' });
    const Give = require('../server/giveaway.js');
    const ctxG = envG.ctx; ctxG.fastAnnounce = true;
    const ids = [8101, 8102, 8103, 8104, 8105];
    for (const id of ids) { const P = new Player(envG, id, { name: 'P' + id }); await P.auth(); }
    await test('giveaway: only verified players rank, by points earned during the window; the end freezes winners', async () => {
      await Give.tick(ctxG, envG.clock.t);
      const g = Give.current(ctxG);
      assert.ok(g && g.status === 'live', 'started from config');
      // points earned after the start; 8105 is the most active but never verified
      const add = { 8101: 50, 8102: 120, 8103: 80, 8104: 10, 8105: 500 };
      for (const id of ids) ctxG.db.run('UPDATE users SET pts=pts+? WHERE id=?', add[id], id);
      for (const id of [8101, 8102, 8103, 8104]) ctxG.db.run("INSERT INTO purchases(charge_id,uid,sku,stars,ts) VALUES(?,?,?,?,?)", 'g' + id, id, 'donate', 100, envG.clock.t);
      ctxG.cache.gwBoard = null;
      const v = Give.view(ctxG, 8103, envG.clock.t);
      assert.deepEqual(v.top.map((r) => r.name), ['P8102', 'P8103', 'P8101', 'P8104']);
      assert.equal(v.me.rank, 2);
      assert.equal(Give.view(ctxG, 8105, envG.clock.t).verified, false, 'not verified: not in the ranking');
      const bm = require('../server/bot.js').giveawayMsg(ctxG, 8103, 'uk', envG.clock.t);
      assert.match(bm.text, /Розіграш від спонсора/); assert.match(bm.text, /місце <b>#2<\/b>/); assert.match(bm.text, /✅ верифікація/);
      envG.advance(3 * 86400e3 + 1000);
      await Give.tick(ctxG, envG.clock.t);
      const done = Give.current(ctxG);
      assert.equal(done.status, 'done');
      assert.deepEqual(done.winners.map((w) => [w.uid, w.prize]), [[8102, 20], [8103, 10], [8101, 5]]);
      ctxG.db.run('UPDATE users SET pts=pts+1000 WHERE id=8104');
      ctxG.cache.gwBoard = null;
      assert.equal(Give.view(ctxG, 8104, envG.clock.t).top[0].name, 'P8102', 'results are frozen after the end');
    });
    await test('giveaway: prizes go out in SOL to connected wallets; a winner without a wallet gets it after connecting', async () => {
      const sent = [];
      ctxG.solRpc = async (method, params) => {
        if (method === 'getBalance') return { value: 5e9 };
        if (method === 'getLatestBlockhash') return { value: { blockhash: '11111111111111111111111111111111' } };
        if (method === 'sendTransaction') { sent.push(params[0]); return 'sig'; }
        if (method === 'getSignatureStatuses') return { value: [{ err: null, confirmationStatus: 'confirmed' }] };
        throw new Error('unexpected ' + method);
      };
      ctxG.db.run('UPDATE users SET sol=? WHERE id IN (8102, 8103)', '9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM');
      await Give.payTick(ctxG, envG.clock.t);
      const st = (id) => ctxG.db.get('SELECT status, lamports FROM giveaway_pay WHERE uid=?', id);
      assert.equal(st(8102).status, 'sent'); assert.equal(st(8102).lamports, 200000000, '$20 at $100/SOL = 0.2 SOL');
      assert.equal(st(8103).status, 'sent');
      assert.equal(st(8101).status, 'wait', 'no wallet yet');
      assert.equal(sent.length, 2);
      await Give.payTick(ctxG, envG.clock.t);
      assert.equal(sent.length, 2, 'never paid twice');
      ctxG.db.run('UPDATE users SET sol=? WHERE id=8101', '9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM');
      await Give.payTick(ctxG, envG.clock.t);
      assert.equal(st(8101).status, 'sent');
      assert.equal(Give.view(ctxG, 8101, envG.clock.t).pay.st, 'sent');
      ctxG.solRpc = null;
    });
    await envG.close();
  }
  {
    const envS = await makeEnv({ GIVEAWAY_ON: '1', GIVEAWAY_KEY: 's-test', GIVEAWAY_PRIZES: '20,10,5' });
    const Give = require('../server/giveaway.js');
    const ctxS = envS.ctx; ctxS.fastAnnounce = true;
    const ids = [8201, 8202, 8203];
    for (const id of ids) { const P = new Player(envS, id, { name: 'S' + id }); await P.auth(); }
    await test('giveaway: when the bot is an admin of the channel, only subscribers take part (checked live at the end)', async () => {
      await Give.tick(ctxS, envS.clock.t);
      const g = Give.current(ctxS);
      ctxS.db.kvSet('gw_chat', '-100777');
      for (const id of ids) { ctxS.db.run('UPDATE users SET pts=pts+? WHERE id=?', 100 - id % 10, id); ctxS.db.run("INSERT INTO purchases(charge_id,uid,sku,stars,ts) VALUES(?,?,?,?,?)", 's' + id, id, 'donate', 100, envS.clock.t); }
      envS.mock.members.add(8201); envS.mock.members.add(8203);
      assert.equal(Give.view(ctxS, 8202, envS.clock.t).sub, false, 'not checked yet = not subscribed');
      for (const id of ids) await Give.checkSub(ctxS, g, id, envS.clock.t);
      assert.deepEqual(Give.view(ctxS, 8202, envS.clock.t).top.map((r) => r.name), ['S8201', 'S8203']);
      envS.mock.members.delete(8203); // left the channel before the end
      envS.advance(3 * 86400e3 + 1000);
      await Give.tick(ctxS, envS.clock.t);
      assert.deepEqual(Give.current(ctxS).winners.map((w) => w.uid), [8201]);
    });
    await envS.close();
  }
  {
    const envN = await makeEnv({ GIVEAWAY_ON: '1', GIVEAWAY_KEY: 'n-test', GIVEAWAY_PRIZES: '20,10,5' });
    const Give = require('../server/giveaway.js');
    const ctxN = envN.ctx; ctxN.fastAnnounce = true;
    const ids = [8301, 8302, 8303, 8304];
    for (const id of ids) { const P = new Player(envN, id, { name: 'N' + id }); await P.auth(); }
    await test('giveaway: if Telegram cannot tell who is subscribed at the end, the top players still win (only a clear "left" drops one)', async () => {
      await Give.tick(ctxN, envN.clock.t);
      const g = Give.current(ctxN);
      ctxN.db.kvSet('gw_chat', '-100778');
      for (const id of ids) { ctxN.db.run('UPDATE users SET pts=pts+? WHERE id=?', 100 - id % 10, id); ctxN.db.run("INSERT INTO purchases(charge_id,uid,sku,stars,ts) VALUES(?,?,?,?,?)", 'n' + id, id, 'donate', 100, envN.clock.t); }
      envN.mock.members.add(8301); envN.mock.members.add(8303);
      await Give.checkSub(ctxN, g, 8301, envN.clock.t); await Give.checkSub(ctxN, g, 8302, envN.clock.t);
      const prev = ctxN.tg.call;
      ctxN.tg.call = async (m, b) => (m === 'getChatMember' ? { ok: false, description: 'Bad Request: member list is inaccessible' } : prev(m, b));
      envN.advance(3 * 86400e3 + 1000);
      await Give.tick(ctxN, envN.clock.t);
      ctxN.tg.call = prev;
      // 8301 subscribed (checked earlier), 8302 was seen NOT subscribed, 8303 and 8304 could not be checked
      assert.deepEqual(Give.current(ctxN).winners.map((w) => w.uid), [8301, 8303, 8304]);
    });
    await envN.close();
  }
  {
    // the end of a giveaway with real prizes: nobody can swap the sponsor channel, the end runs once, a prize is paid once
    const envE = await makeEnv({ GIVEAWAY_ON: '1', GIVEAWAY_KEY: 'end', GIVEAWAY_PRIZES: '20,10,5', AIRDROP_GENKEY: '1', SOL_USD: '100' });
    const ctxE = envE.ctx; ctxE.fastAnnounce = true;
    const Give = require('../server/giveaway.js');
    const ids = [8501, 8502, 8503, 8504];
    for (const id of ids) { const P = new Player(envE, id, { name: 'E' + id }); await P.auth(); }
    await Give.tick(ctxE, envE.clock.t);
    ctxE.db.kvSet('gw_chat', '-100777');
    ids.forEach((id, i) => {
      ctxE.db.run('UPDATE users SET pts=pts+?, sol=? WHERE id=?', id === 8504 ? 1 : 100 - i * 10, '9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM', id);
      ctxE.db.run('INSERT INTO purchases(charge_id,uid,sku,stars,ts) VALUES(?,?,?,?,?)', 'e' + id, id, 'donate', 100, envE.clock.t);
    });
    await test('giveaway: a stranger who makes the bot an admin of his own channel cannot replace the sponsor channel', async () => {
      const up = { update_id: 501, my_chat_member: { chat: { id: -100666, type: 'channel', title: 'Mine' }, from: { id: 8504, first_name: 'X' }, date: 1, old_chat_member: { status: 'left', user: { id: 1 } }, new_chat_member: { status: 'administrator', user: { id: 1 } } } };
      await httpJson(envE.base, 'POST', '/tg/' + ctxE.cfg.webhookSecret, up, { 'x-telegram-bot-api-secret-token': ctxE.cfg.webhookSecret });
      await new Promise((r) => setTimeout(r, 80));
      assert.equal(Give.subChat(ctxE), '-100777');
    });
    await test('giveaway: two overlapping end ticks finish it once (3 prize rows, 3 winner messages, 1 post)', async () => {
      for (const id of [8501, 8502, 8503]) envE.mock.members.add(id);
      const real = ctxE.tg.call;
      let dms = 0, posts = 0;
      ctxE.tg.call = async (m, b) => {
        if (m === 'sendMessage') { if (String(b.chat_id).startsWith('@')) posts++; else if (/#\d/.test(b.text)) dms++; }
        if (m === 'getChatMember') await new Promise((r) => setTimeout(r, 60)); // slow Telegram: the next tick starts meanwhile
        return real(m, b);
      };
      ctxE.solRpc = async (method) => {
        if (method === 'getBalance') return { value: 5e9 };
        if (method === 'getLatestBlockhash') return { value: { blockhash: '11111111111111111111111111111111' } };
        if (method === 'sendTransaction') return 'sig';
        if (method === 'getSignatureStatuses') return { value: [{ err: null, confirmationStatus: 'confirmed' }] };
        throw new Error('unexpected ' + method);
      };
      envE.advance(3 * 86400e3 + 1000);
      const A = Give.tick(ctxE, envE.clock.t);
      await new Promise((r) => setTimeout(r, 30));
      await Promise.all([A, Give.tick(ctxE, envE.clock.t + 60000)]);
      ctxE.tg.call = real;
      const pay = ctxE.db.all('SELECT uid, usd FROM giveaway_pay ORDER BY rank');
      assert.deepEqual(pay.map((r) => [r.uid, r.usd]), [[8501, 20], [8502, 10], [8503, 5]]);
      assert.deepEqual([dms, posts], [3, 1]);
    });
    await test('giveaway: a transfer whose send call timed out is looked up, not sent a second time', async () => {
      ctxE.db.run("UPDATE giveaway_pay SET status='wait', tx=NULL WHERE uid IN (8501,8502,8503)");
      const sent = [];
      let n = 0;
      ctxE.solRpc = async (method, params) => {
        if (method === 'getBalance') return { value: 5e9 };
        if (method === 'getLatestBlockhash') return { value: { blockhash: '1111111111111111111111111111111' + (++n) } };
        if (method === 'sendTransaction') { sent.push(params[0]); if (sent.length === 1) throw new Error('The operation was aborted due to timeout'); return 'sig'; }
        if (method === 'getSignatureStatuses') return { value: [{ err: null, confirmationStatus: 'confirmed' }] }; // the first one landed
        throw new Error('unexpected ' + method);
      };
      ctxE.db.run("UPDATE giveaway_pay SET status='sent' WHERE uid IN (8502,8503)");
      await Give.payTick(ctxE, envE.clock.t + 120000);
      assert.equal(ctxE.db.get('SELECT status FROM giveaway_pay WHERE uid=8501').status, 'sending');
      await Give.payTick(ctxE, envE.clock.t + 180000);
      assert.equal(ctxE.db.get('SELECT status FROM giveaway_pay WHERE uid=8501').status, 'sent');
      assert.equal(sent.length, 1, 'one transfer only');
    });
    await envE.close();
  }
  {
    const envB = await makeEnv({ BLOCKED_COUNTRIES: 'RU,BR' });
    await test('closed countries: existing players are reset and banned; new sign-ins are refused', async () => {
      const P = new Player(envB, 8401); await P.auth();
      const ctxB = envB.ctx;
      ctxB.db.run("UPDATE users SET country='BR', pts=500, ref_pts=20 WHERE id=8401");
      const Q = new Player(envB, 8402); await Q.auth();
      ctxB.db.run("UPDATE users SET country='UA', pts=300 WHERE id=8402");
      const Eco = require('../server/economy.js');
      assert.equal(Eco.purgeBlocked(ctxB), 1);
      const u = ctxB.db.get('SELECT pts, ref_pts, risk_state FROM users WHERE id=8401');
      assert.deepEqual([u.pts, u.ref_pts, u.risk_state], [0, 0, 'banned']);
      assert.equal(ctxB.db.get('SELECT pts FROM users WHERE id=8402').pts, 300, 'other countries untouched');
      assert.equal(Eco.purgeBlocked(ctxB), 0, 'idempotent');
      const M = new Player(envB, 8403); assert.equal((await M.auth('', { tz: 'Europe/Moscow' })).status, 403);
      assert.equal(M.last.err, 'region');
      const B = new Player(envB, 8404); assert.equal((await B.auth('', { tz: 'America/Sao_Paulo' })).status, 403);
      const K = new Player(envB, 8405); assert.equal((await K.auth('', { tz: 'Europe/Kyiv' })).status, 200, 'Ukraine plays');
    });
    await envB.close();
  }
  summary();
})();
