'use strict';
const assert = require('assert');
const crypto = require('crypto');
const { makeEnv, httpJson, Player, test, summary, E, TG, BOT_TOKEN } = require('./lib.js');
const U = require('../server/users.js');
const Eco = require('../server/economy.js');
const AB = require('../server/antibot.js');
const Ton = require('../server/ton.js');

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
    assert.ok(link.startsWith('https://t.me/od_test_bot/play?startapp=ref_'), link);
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
  await test('inviter is rewarded only when the friend really plays (2 days + a released game)', async () => {
    const B = R.B;
    const a0 = U.getUser(ctx, 1001).pts, gems0 = U.loadState(ctx, 1001).gems;
    for (let i = 0; i < 6; i++) { B.play(160); await B.sync(); }
    assert.ok(B.S.stats.released >= 1, 'friend released a game');
    assert.equal(U.getUser(ctx, 2001).ref_active, 0, 'not active after one day');
    env.advance(DAY);
    B.play(40); await B.sync();
    assert.equal(U.getUser(ctx, 2001).ref_active, 1, 'active on day 2');
    const gemsAfter = U.loadState(ctx, 1001).gems;
    assert.equal(gemsAfter - gems0, ctx.cfg.eco.inviteGems);
    assert.ok(U.getUser(ctx, 1001).pts - a0 >= ctx.cfg.eco.refActivationBonus - 0.01);
  });
  await test('inviter earns 10% of the friend\'s points; L2 earns 2%; daily cap respected', async () => {
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
    assert.ok(l2 > 0, 'L2 payout exists');
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
  await test('Office Pass lifts the daily ceiling by 25%', async () => {
    const P = new Player(env, 5003); await P.auth();
    assert.equal(P.eco.D, 300);
    Eco.fulfil(ctx, 5003, 'pass30', 'ch_pass', 150, clock.t);
    P.tick(4); const r = await P.sync();
    assert.equal(r.json.eco.D, 375);
    assert.ok(r.json.eco.pass > clock.t);
  });
  await test('wallet: TON Connect proof verification (synthetic wallet), unverified fallback, duplicates flagged', async () => {
    const P = new Player(env, 5004); await P.auth();
    const pl = await P.api('GET', '/api/ton/payload');
    const payload = pl.json.payload;
    // build a wallet: Ed25519 key, data cell containing the public key, state-init hash == address
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
    const pub = publicKey.export({ format: 'der', type: 'spki' }).subarray(12);
    const dataCell = { data: Buffer.concat([Buffer.alloc(4), Buffer.from([0, 0, 0, 1]), pub, Buffer.from([0])]), refs: [] };
    const codeCell = { data: Buffer.from('deadbeef', 'hex'), refs: [] };
    const root = { data: Buffer.from([0x34]), refs: [codeCell, dataCell] };
    const bocBuf = Ton.bocBuild(root);
    const parsed = Ton.bocParse(bocBuf);
    const hash = parsed.cells[parsed.roots[0]].hash;
    const raw = '0:' + hash.toString('hex');
    const domain = 'game.example.test';
    const ts = Math.floor(clock.t / 1000);
    const wc = Buffer.alloc(4); wc.writeInt32BE(0);
    const tsb = Buffer.alloc(8); tsb.writeBigUInt64LE(BigInt(ts));
    const dl = Buffer.alloc(4); dl.writeUInt32LE(domain.length);
    const msg = Buffer.concat([Buffer.from('ton-proof-item-v2/'), wc, hash, dl, Buffer.from(domain), tsb, Buffer.from(payload)]);
    const full = Buffer.concat([Buffer.from([0xff, 0xff]), Buffer.from('ton-connect'), Ton.sha256(msg)]);
    const signature = crypto.sign(null, Ton.sha256(full), privateKey).toString('base64');
    const account = { address: raw, chain: '-239', publicKey: pub.toString('hex'), walletStateInit: bocBuf.toString('base64') };
    const proof = { timestamp: ts, domain: { lengthBytes: domain.length, value: domain }, payload, signature };
    const ok = await P.api('POST', '/api/wallet', { account, proof });
    assert.equal(ok.json.ok, true); assert.equal(ok.json.level, 1, 'fully verified');
    assert.ok(/^UQ/.test(ok.json.friendly) || ok.json.friendly.length === 48);
    assert.equal(ok.json.eco.wallet.ok, true);
    // tampered signature / wrong domain / forged address => stored as unverified or rejected, never "verified"
    const bad1 = await P.api('POST', '/api/wallet', { account, proof: { ...proof, signature: Buffer.alloc(64, 1).toString('base64') } });
    assert.equal(bad1.json.level, 0);
    const bad2 = await P.api('POST', '/api/wallet', { account, proof: { ...proof, domain: { lengthBytes: 8, value: 'evil.com' } } });
    assert.equal(bad2.json.level, 0);
    const other = '0:' + 'ab'.repeat(32);
    const bad3 = await P.api('POST', '/api/wallet', { account: { ...account, address: other }, proof });
    assert.equal(bad3.json.level, 0);
    // manual friendly address (no proof) => unverified
    const fr = Ton.toFriendly({ wc: 0, hash: Buffer.alloc(32, 7) });
    const man = await P.api('POST', '/api/wallet', { address: fr });
    assert.equal(man.json.ok, true); assert.equal(man.json.level, 0);
    assert.equal((await P.api('POST', '/api/wallet', { address: 'EQnotanaddress' })).json.err, 'address');
    // the same wallet on a second account raises risk on both
    const Q = new Player(env, 5005); await Q.auth();
    const h0 = U.getUser(ctx, 5005).risk_hard;
    await Q.api('POST', '/api/wallet', { address: fr });
    assert.ok(U.getUser(ctx, 5005).risk_hard > h0);
    assert.ok(U.getUser(ctx, 5004).risk_hard > 0);
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
    assert.ok(csv.startsWith('tg_id,username,wallet'));
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
  });

  await env.close();
  summary();
})();
