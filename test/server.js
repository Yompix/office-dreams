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
    assert.ok(csv.startsWith('tg_id,username,country,wallet'));
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

    const shopWallet = Ton.toFriendly({ wc: 0, hash: crypto.createHash('sha256').update('shop').digest() }, { bounceable: false });
    const env5 = await makeEnv({ TON_PAY_WALLET: shopWallet, TON_USD: '2', TON_STAR_USD: '0.016' });
    await test('TON payments: order → on-chain transfer with comment → item granted once', async () => {
      let chain = [];
      env5.ctx.tonApi = async () => chain;
      const P = new Player(env5, 8301); await P.auth();
      const cfg = await httpJson(env5.base, 'GET', '/api/config');
      assert.equal(cfg.json.tonPay, true);
      const shop = await P.api('GET', '/api/shop');
      const gs = shop.json.items.find((x) => x.sku === 'gems_s');
      assert.equal(gs.ton, 0.2, '25 stars × $0.016 / $2 = 0.2 TON');
      const o = (await P.api('POST', '/api/shop/ton', { sku: 'gems_s' })).json;
      assert.ok(o.ok && o.order.to === shopWallet && o.order.amount === '200000000' && /^OD-[0-9a-f]{10}$/.test(o.order.comment), JSON.stringify(o));
      const gems0 = U.loadState(env5.ctx, 8301).gems;
      const check = async () => { env5.advance(6000); return (await P.api('POST', '/api/shop/ton/check', { id: o.order.id })).json; };
      assert.equal((await check()).paid, false);
      const tx = (val, comment, hash) => ({ transaction_id: { lt: '1', hash }, in_msg: { source: 'EQsender', value: val, message: comment } });
      chain = [tx('199000000', o.order.comment, 'h_low'), tx('500000000', 'OD-0000000000', 'h_other')];
      assert.equal((await check()).paid, false, 'too little or wrong comment does not pay');
      chain.push({ transaction_id: { lt: '2', hash: 'h_ok' }, in_msg: { source: 'EQsender', value: '200000000', msg_data: { '@type': 'msg.dataText', text: Buffer.from(o.order.comment).toString('base64') } } });
      assert.equal((await check()).paid, true);
      assert.equal(U.loadState(env5.ctx, 8301).gems - gems0, 15);
      assert.equal((await check()).paid, true);
      assert.equal(U.loadState(env5.ctx, 8301).gems - gems0, 15, 'never granted twice');
      // another player cannot check someone else's order
      const Q = new Player(env5, 8302); await Q.auth();
      assert.equal((await Q.api('POST', '/api/shop/ton/check', { id: o.order.id })).json.err, 'unavailable');
      // background sweep credits an order the player never checked, and the supporter bonus turns on
      const o2 = (await Q.api('POST', '/api/shop/ton', { sku: 'starter' })).json;
      chain.push(tx('600000000', o2.order.comment, 'h_q'));
      env5.advance(6000);
      const r = await require('../server/tonpay.js').sweep(env5.ctx, env5.clock.t);
      assert.equal(r.paid, 1);
      assert.equal((await Q.api('POST', '/api/shop/ton', { sku: 'starter' })).json.err, 'unavailable', 'one-time item cannot be bought again');
      assert.ok(Eco.supporterUntil(env5.ctx, 8302, env5.clock.t) > env5.clock.t);
      // coin packs: fixed amounts, bigger pack = bigger bonus, first buy of a pack is doubled, VIP adds a bonus
      let sh = (await Q.api('GET', '/api/shop')).json;
      assert.equal(sh.items[0].sku, 'cash1');
      assert.equal(sh.vip.lvl, 0);
      assert.equal(sh.vip.next.left, 50, 'starter (50⭐ via TON) counts toward VIP');
      const c3 = sh.items.find((x) => x.sku === 'cash3');
      assert.ok(c3.first && c3.cash === 50000, JSON.stringify(c3));
      const buy = async (sku, h) => {
        const before = U.loadState(env5.ctx, 8302);
        const o3 = (await Q.api('POST', '/api/shop/ton', { sku })).json;
        chain.push(tx(o3.order.amount, o3.order.comment, h));
        env5.advance(6000);
        assert.equal((await Q.api('POST', '/api/shop/ton/check', { id: o3.order.id })).json.paid, true);
        const after = U.loadState(env5.ctx, 8302);
        assert.equal(after.earned, before.earned, 'bought coins do not unlock earning goals');
        return after.cash - before.cash;
      };
      assert.equal(await buy('cash3', 'h_c1'), 50000, '20 000 +25% ×2 first purchase');
      sh = (await Q.api('GET', '/api/shop')).json;
      assert.equal(sh.vip.lvl, 2, '550⭐ → VIP 2');
      assert.equal(sh.items.find((x) => x.sku === 'cash3').cash, 27000, 'second purchase: 20 000 × (1 + 25% + VIP 10%)');
      assert.equal(await buy('cash3', 'h_c2'), 27000);
    });
    await test('TON comment BOC built by the client parses as a text comment', async () => {
      const src = require('fs').readFileSync(require('path').join(__dirname, '..', 'src', 'net.js'), 'utf8');
      const fn = new Function(src.match(/function commentBoc\(text\) \{[\s\S]*?\n\}/)[0] + '; return commentBoc;')();
      const boc = Buffer.from(fn('OD-0123456789'), 'base64');
      const parsed = Ton.bocParse(boc);
      const root = parsed.cells ? parsed.cells[parsed.roots[0]] : parsed[0];
      const data = Buffer.from(root.data || root.bytes);
      assert.equal(data.readUInt32BE(0), 0);
      assert.equal(data.subarray(4).toString('utf8'), 'OD-0123456789');
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
      // TON donation: whatever actually arrived counts
      const o = (await P.api('POST', '/api/donate/ton', { ton: f.tonMin })).json;
      assert.ok(o.ok, JSON.stringify(o));
      assert.equal((await P.api('POST', '/api/donate/ton', { ton: 0.1 })).json.err, 'amount');
      const prev = env5.ctx.tonApi;
      env5.ctx.tonApi = async () => [{ transaction_id: { lt: '9', hash: 'h_don' }, in_msg: { source: 'EQx', value: '5000000000', message: o.order.comment } }];
      env5.advance(6000);
      assert.equal((await P.api('POST', '/api/shop/ton/check', { id: o.order.id })).json.paid, true);
      env5.ctx.tonApi = prev;
      env5.advance(6000);
      f = (await P.api('GET', '/api/donate')).json;
      assert.ok(f.mine > 300 + 400, 'TON counted by the amount received: ' + f.mine);
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
  summary();
})();
