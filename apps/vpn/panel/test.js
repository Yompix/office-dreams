'use strict';
/* node test.js — the whole path: /start → trial → key → buy → pay → node configs → subscription. */
const assert = require('assert');
const http = require('http');
const Config = require('./lib/config.js');
const Store = require('./lib/store.js');
const Bot = require('./lib/bot.js');
const Links = require('./lib/links.js');
const NodeCfg = require('./lib/nodecfg.js');
const { createApp } = require('./server.js');

const calls = [];
const tg = { call: async (m, p) => { calls.push({ m, p }); return { ok: true }; } };
const last = (m) => [...calls].reverse().find((c) => c.m === m);
const cfg = Config.load({ BOT_TOKEN: '1:x', BOT_USERNAME: 'TestVpnBot', PUBLIC_URL: 'https://vpn.example.com', ADMIN_IDS: '1', ADMIN_KEY: 'k', BRAND: 'YURK' });
const store = Store.open(':memory:');
const quiet = { log() {}, error() {} };
const bot = Bot.create({ cfg, store, tg, log: quiet });
const now = Date.UTC(2026, 10, 15);
const DAY = Store.DAY;
const msg = (from, text) => ({ update_id: 1, message: { message_id: 1, from, chat: { id: from.id }, text } });
const cb = (from, data) => ({ update_id: 1, callback_query: { id: 'q', from, data, message: { chat: { id: from.id } } } });
const admin = { id: 1, first_name: 'Owner', language_code: 'ru' };
const alice = { id: 100, first_name: 'Alice', language_code: 'ru' };
const bob = { id: 200, first_name: 'Bob', language_code: 'en' };
let n = 0; const ok = (name) => { n++; console.log('  ✓', name); };

(async () => {
  // Reality keys look like what `xray x25519` prints
  const k = Store.realityKeys();
  assert.match(k.priv, /^[A-Za-z0-9_-]{43}$/); assert.match(k.pub, /^[A-Za-z0-9_-]{43}$/);
  ok('reality keys');

  // admin adds a node and gets the install command
  await bot.handle(msg(admin, '/addnode 🇳🇱NL 203.0.113.5'), now);
  const node = store.nodes()[0];
  assert.ok(node && node.host === '203.0.113.5' && node.sni === 'www.microsoft.com');
  assert.ok(last('sendMessage').p.text.includes(node.secret));
  ok('/addnode');

  // new user: menu with a trial button
  await bot.handle(msg(alice, '/start'), now);
  const kb = last('sendMessage').p.reply_markup.inline_keyboard.flat().map((b) => b.callback_data);
  assert.ok(kb.includes('trial') && kb.includes('buy'));
  ok('/start menu');

  // trial once
  await bot.handle(cb(alice, 'trial'), now);
  assert.strictEqual(store.user(100).expires, now + 3 * DAY);
  assert.ok(last('sendMessage').p.text.includes('/sub/' + store.user(100).token));
  await bot.handle(cb(alice, 'trial'), now);
  assert.strictEqual(store.user(100).expires, now + 3 * DAY);
  ok('trial is given once');

  // referral: bob comes from alice, english texts
  await bot.handle(msg(bob, '/start r100'), now);
  assert.strictEqual(store.user(200).ref, 100);
  assert.ok(last('sendMessage').p.text.includes('fast VPN'));
  await bot.handle(msg({ id: 300, first_name: 'Self' }, '/start r300'), now);
  assert.strictEqual(store.user(300).ref, 0);
  ok('referral link');

  // bob buys a month
  await bot.handle(cb(bob, 'plan:m1'), now);
  const inv = last('sendInvoice').p;
  assert.strictEqual(inv.currency, 'XTR'); assert.strictEqual(inv.prices[0].amount, 199); assert.strictEqual(inv.payload, 'm1:200:mid');
  await bot.handle({ update_id: 2, pre_checkout_query: { id: 'pc', from: bob, currency: 'XTR', total_amount: 199, invoice_payload: 'm1:200' } }, now);
  assert.strictEqual(last('answerPreCheckoutQuery').p.ok, true);
  await bot.handle({ update_id: 3, pre_checkout_query: { id: 'pc2', from: bob, currency: 'XTR', total_amount: 1, invoice_payload: 'm1:200' } }, now);
  assert.strictEqual(last('answerPreCheckoutQuery').p.ok, false);
  ok('invoice + pre-checkout (wrong price refused)');

  const paid = { update_id: 4, message: { message_id: 2, from: bob, chat: { id: 200 }, successful_payment: { currency: 'XTR', total_amount: 199, invoice_payload: 'm1:200', telegram_payment_charge_id: 'ch1' } } };
  const aliceBefore = store.user(100).expires;
  await bot.handle(paid, now);
  assert.strictEqual(store.user(200).expires, now + 30 * DAY);
  assert.strictEqual(store.user(100).expires, aliceBefore + 3 * DAY, 'inviter gets +3 days');
  assert.strictEqual(store.user(100).bal, Math.floor(Math.floor(199 * 0.013 * 100) * 0.2), 'inviter gets 20% of the net');
  await bot.handle(paid, now); // Telegram resent the same update
  assert.strictEqual(store.user(200).expires, now + 30 * DAY);
  assert.strictEqual(store.stats(now).stars, 199);
  ok('payment extends once, inviter rewarded');

  // second payment: days stack, no second referral bonus
  const a2 = store.user(100).expires;
  paid.message.successful_payment.telegram_payment_charge_id = 'ch2';
  await bot.handle(paid, now + DAY);
  assert.strictEqual(store.user(200).expires, now + 60 * DAY);
  assert.strictEqual(store.user(100).expires, a2);
  ok('renewal stacks days');

  // node configs contain exactly the active users
  const users = store.activeUsers(now);
  assert.deepStrictEqual(users.map((u) => u.id), [100, 200]);
  const xc = NodeCfg.xray(node, users);
  assert.strictEqual(xc.inbounds[0].settings.clients.length, 2);
  assert.strictEqual(xc.inbounds[0].streamSettings.realitySettings.privateKey, node.priv);
  const hy = NodeCfg.hysteria(node, users);
  assert.ok(hy.includes(`u200: ${store.user(200).pass}`));
  assert.ok(NodeCfg.hysteria(node, []).includes('nobody:'));
  ok('node configs');

  // HTTP: node agent, subscription, browser page, admin
  const app = createApp({ cfg, store, bot, log: quiet });
  const srv = http.createServer(app).listen(0);
  const base = `http://127.0.0.1:${srv.address().port}`;
  const pin = Array.from({ length: 32 }, () => 'AB').join(':');
  let r = await fetch(base + '/node/xray.json', { headers: { authorization: 'Bearer nope' } });
  assert.strictEqual(r.status, 403);
  r = await fetch(base + '/node/hysteria.yaml', { headers: { authorization: 'Bearer ' + node.secret, 'x-hy2-pin': pin } });
  assert.strictEqual(r.status, 200);
  assert.strictEqual(store.node(node.id).pin, pin);
  ok('node agent auth + pin report');

  const bobU = store.user(200);
  r = await fetch(base + '/sub/' + bobU.token, { headers: { 'user-agent': 'Happ/3.0' } });
  assert.strictEqual(r.status, 200);
  assert.match(r.headers.get('subscription-userinfo'), /expire=\d+/);
  const lines = Buffer.from(await r.text(), 'base64').toString().split('\n');
  assert.strictEqual(lines.length, 2);
  assert.ok(lines[0].startsWith(`vless://${bobU.uuid}@203.0.113.5:443?`) && lines[0].includes('pbk=' + node.pub) && lines[0].includes('flow=xtls-rprx-vision'));
  assert.ok(lines[1].startsWith(`hysteria2://u200:${bobU.pass}@203.0.113.5:443/`) && lines[1].includes('pinSHA256='));
  ok('subscription: VLESS Reality + Hysteria2');

  r = await fetch(base + '/sub/' + bobU.token, { headers: { 'user-agent': 'Mozilla/5.0', accept: 'text/html' } });
  const page = await r.text();
  assert.ok(page.includes('happ://add/https://vpn.example.com/sub/' + bobU.token));
  ok('browser page with one-tap buttons');

  // expired user gets a notice instead of servers
  const exp = Links.subscription({ ...bobU, expires: now - 1 }, store.enabledNodes(), cfg, now);
  assert.strictEqual(exp.lines.length, 1); assert.ok(exp.lines[0].includes('127.0.0.1'));
  ok('expired subscription shows a notice');

  // key reset: old link dies
  const oldTok = bobU.token;
  await bot.handle(cb(bob, 'reset'), now);
  assert.strictEqual((await fetch(base + '/sub/' + oldTok)).status, 404);
  assert.notStrictEqual(store.user(200).uuid, bobU.uuid);
  ok('key reset');

  r = await fetch(base + '/admin/stats?key=k');
  const st = await r.json();
  assert.strictEqual(st.paying, 1); assert.strictEqual(st.stars, 398);
  assert.strictEqual((await fetch(base + '/admin/stats?key=bad')).status, 403);
  r = await fetch(base + '/tg/hook', { method: 'POST', body: '{}' });
  assert.strictEqual(r.status, 403);
  ok('admin stats + webhook secret');
  srv.close();

  // reminder a day before the end, only once
  calls.length = 0;
  await bot.remind(store.user(100).expires - DAY / 2);
  await bot.remind(store.user(100).expires - DAY / 3);
  assert.strictEqual(calls.filter((c) => c.m === 'sendMessage' && c.p.chat_id === 100).length, 1);
  ok('expiry reminder once');

  // Railway node: WebSocket via the https domain + Reality via the TCP proxy, no Hysteria2
  const rn = store.railwayNode({ name: 'RW', host: 'node.up.railway.app', rhost: 'x.proxy.rlwy.net', rport: 12345, secret: 's'.repeat(40) });
  const rn2 = store.railwayNode({ name: 'RW', host: 'node.up.railway.app', rhost: 'x.proxy.rlwy.net', rport: 23456, secret: 's'.repeat(40) });
  assert.strictEqual(rn.id, rn2.id); assert.strictEqual(rn2.rport, 23456); assert.strictEqual(rn2.priv, rn.priv);
  const rc = NodeCfg.xray(rn2, store.activeUsers(now));
  assert.deepStrictEqual(rc.inbounds.map((i) => i.port), [8080, 9443]);
  assert.strictEqual(rc.inbounds[0].streamSettings.wsSettings.path, rn2.ws_path);
  store.setNodeEnabled(node.id, false);
  const rs = Links.subscription(store.user(200), store.enabledNodes(), cfg, now).lines;
  assert.strictEqual(rs.length, 2);
  assert.ok(rs[0].includes('@node.up.railway.app:443?') && rs[0].includes('type=ws') && rs[0].includes('security=tls'));
  assert.ok(rs[1].includes('@x.proxy.rlwy.net:23456?') && rs[1].includes('security=reality'));
  ok('railway node (ws + reality over tcp proxy)');

  // Mini App: Telegram login, trial, Stars / crypto / card payments
  const WebApp = require('./lib/webapp.js');
  const cbCalls = [];
  let paidItems = [];
  const fakeCb = { call: async (m, p) => { cbCalls.push({ m, p }); return m === 'createInvoice' ? { invoice_id: 7, bot_invoice_url: 'https://t.me/CryptoBot?start=IV7' } : { items: paidItems }; } };
  const wcfg = { ...cfg, cardToken: 'prov:1', cardCurrency: 'RUB', cardRate: 100 };
  const wbot = Bot.create({ cfg: wcfg, store, tg, log: quiet });
  const wa = WebApp.create({ cfg: wcfg, store, bot: wbot, tg, cb: fakeCb, log: quiet });
  const carol = { id: 400, first_name: 'Carol', language_code: 'ru' };
  const init = WebApp.signInitData(cfg.botToken, carol, now, { start_param: 'r100' });
  let [code, me] = await wa.api('me', 'hash=bad', {}, now);
  assert.strictEqual(code, 401);
  [code, me] = await wa.api('me', init, {}, now);
  assert.strictEqual(code, 200); assert.strictEqual(me.trial, true); assert.strictEqual(me.active, false);
  assert.deepStrictEqual(me.methods, { stars: true, crypto: true, card: true, balance: false });
  assert.strictEqual(store.user(400).ref, 100);
  assert.strictEqual((await wa.api('me', WebApp.signInitData(cfg.botToken, carol, now - 2 * DAY), {}, now))[0], 401);
  ok('mini app: login by initData, referral from start_param');

  [code, me] = await wa.api('trial', init, {}, now);
  assert.strictEqual(code, 200); assert.strictEqual(me.active, true); assert.strictEqual(me.daysLeft, 3); assert.ok(me.sub.includes('/sub/'));
  assert.strictEqual((await wa.api('trial', init, {}, now))[0], 409);
  ok('mini app: trial once');

  [, r] = await wa.api('pay', init, { plan: 'm1', method: 'stars' }, now);
  assert.strictEqual(r.kind, 'invoice');
  const il = last('createInvoiceLink').p;
  assert.strictEqual(il.currency, 'XTR'); assert.strictEqual(il.payload, 'm1:400:mid'); assert.strictEqual(il.prices[0].amount, 199);
  [, r] = await wa.api('pay', init, { plan: 'm3', method: 'card' }, now);
  const cl = last('createInvoiceLink').p;
  assert.strictEqual(cl.currency, 'RUB'); assert.strictEqual(cl.prices[0].amount, 74900); assert.strictEqual(cl.payload, 'card:m3:400:mid');
  const exp0 = store.user(400).expires;
  await wbot.handle({ update_id: 9, message: { message_id: 3, from: carol, chat: { id: 400 }, successful_payment: { currency: 'RUB', total_amount: 74900, invoice_payload: 'card:m3:400', telegram_payment_charge_id: 'card1' } } }, now);
  assert.strictEqual(store.user(400).expires, exp0 + 90 * DAY);
  ok('mini app: stars + card invoices, card payment credited');

  [, r] = await wa.api('pay', init, { plan: 'y1', method: 'crypto' }, now);
  assert.strictEqual(r.link, 'https://t.me/CryptoBot?start=IV7');
  const ci = cbCalls.find((c) => c.m === 'createInvoice').p;
  assert.strictEqual(ci.amount, '29.99'); assert.strictEqual(ci.payload, 'y1:400:mid'); assert.strictEqual(ci.fiat, 'USD');
  const exp1 = store.user(400).expires;
  paidItems = [{ invoice_id: 7, status: 'paid', amount: '29.99', payload: 'y1:400:mid' }, { invoice_id: 8, payload: 'zz:1' }];
  assert.strictEqual(await wa.pollCrypto(now), 1);
  assert.strictEqual(await wa.pollCrypto(now), 0);
  assert.strictEqual(store.user(400).expires, exp1 + 365 * DAY);
  assert.strictEqual((await wa.api('pay', init, { plan: 'm1', method: 'paypal' }, now))[0], 400);
  ok('mini app: crypto invoice paid once via CryptoBot poll');

  // regional prices: Iran (fa) gets the low tier, Germany (de) the high one; tier is checked on payment
  const dave = { id: 500, first_name: 'Dave', language_code: 'fa' };
  const initD = WebApp.signInitData(cfg.botToken, dave, now);
  [, me] = await wa.api('me', initD, {}, now);
  assert.strictEqual(me.tier, 'low'); assert.strictEqual(me.plans[0].stars, 149); assert.strictEqual(me.methods.card, false);
  assert.strictEqual((await wa.api('pay', initD, { plan: 'm1', method: 'card' }, now))[0], 400);
  await wa.api('pay', initD, { plan: 'm1', method: 'stars' }, now);
  assert.strictEqual(last('createInvoiceLink').p.prices[0].amount, 149);
  assert.strictEqual(last('createInvoiceLink').p.payload, 'm1:500:low');
  assert.match(last('createInvoiceLink').p.title, /ماه/);
  const de = { id: 600, first_name: 'Hans', language_code: 'de' };
  [, me] = await wa.api('me', WebApp.signInitData(cfg.botToken, de, now), {}, now);
  assert.strictEqual(me.tier, 'high'); assert.strictEqual(me.plans[0].usd, 4.99);
  const pre = async (payload, amount) => { await wbot.handle({ update_id: 20, pre_checkout_query: { id: 'x', from: dave, currency: 'XTR', total_amount: amount, invoice_payload: payload } }, now); return last('answerPreCheckoutQuery').p.ok; };
  assert.strictEqual(await pre('m1:500:low', 149), true);
  assert.strictEqual(await pre('m1:500:mid', 149), false);
  assert.strictEqual(await pre('m1:500:cheap', 1), false);
  await bot.handle(msg({ id: 700, first_name: 'Ali', language_code: 'tr' }, '/start'), now);
  assert.ok(last('sendMessage').p.text.includes('hızlı VPN'));
  ok('regional prices + languages');

  // traffic from the node + money report
  const { saveTraffic, dailyMoney } = require('./server.js');
  const Money = require('./lib/money.js');
  const GB = 1024 ** 3;
  assert.strictEqual(saveTraffic(store, JSON.stringify({ stat: [
    { name: 'user>>>u200>>>traffic>>>uplink', value: String(1 * GB) }, { name: 'user>>>u200>>>traffic>>>downlink', value: String(9 * GB) },
    { name: 'user>>>u100>>>traffic>>>downlink', value: 10 * GB }, { name: 'inbound>>>ws>>>traffic>>>downlink', value: '5' }] }), now), 2);
  const xs = NodeCfg.xray(node, []);
  assert.ok(xs.stats && xs.api.services.includes('StatsService') && xs.policy.levels[0].statsUserDownlink);
  assert.ok(!NodeCfg.xray(node, [], { stats: false }).stats);
  const rep = Money.report(store, cfg, now);
  assert.strictEqual(rep.gb, 20);
  assert.strictEqual(rep.egressNow, 1);
  assert.ok(rep.income > 0 && rep.costMonth >= 5 + 1);
  assert.strictEqual(rep.free, Math.round((rep.income - rep.costMonth - rep.partners) * 100) / 100);
  assert.ok(Money.text(rep).includes('Отложить на Railway'));
  calls.length = 0;
  await bot.handle(msg(admin, '/money'), now);
  assert.ok(last('sendMessage').p.text.includes('Финансы'));
  const morning = Date.UTC(2026, 10, 16, 7);
  assert.strictEqual(await dailyMoney(cfg, store, tg, morning), true);
  assert.strictEqual(await dailyMoney(cfg, store, tg, morning + 3600e3), false);
  assert.strictEqual(await dailyMoney(cfg, store, tg, Date.UTC(2026, 10, 17, 3)), false);
  ok('traffic counters + money report (/money, daily)');

  // traffic limits by plan: trial 3 GB, month 50 GB; over the limit -> off the node, notice in the app, warning once
  const Quota = require('./lib/quota.js');
  const eve = { id: 800, first_name: 'Eve', language_code: 'ru' };
  await bot.handle(msg(eve, '/start'), now);
  await bot.handle(cb(eve, 'trial'), now);
  assert.strictEqual(store.user(800).plan, 'trial');
  const today = new Date(now).toISOString().slice(0, 10);
  store.addTraffic(today, 800, 1 * GB, 1 * GB);
  let q = Quota.usage(store, cfg, store.user(800), now);
  assert.strictEqual(q.quotaGb, 3); assert.strictEqual(q.usedGb, 2); assert.strictEqual(q.over, false);
  assert.ok(Quota.allowedUsers(store, cfg, now).some((u) => u.id === 800));
  calls.length = 0;
  await bot.quotaWarn(now); await bot.quotaWarn(now);
  assert.strictEqual(calls.filter((c) => c.m === 'sendMessage' && c.p.chat_id === 800).length, 0); // 66% — quiet
  store.addTraffic(today, 800, 0, 1 * GB);
  q = Quota.usage(store, cfg, store.user(800), now);
  assert.strictEqual(q.over, true);
  assert.ok(!Quota.allowedUsers(store, cfg, now).some((u) => u.id === 800));
  await bot.quotaWarn(now); await bot.quotaWarn(now);
  assert.strictEqual(calls.filter((c) => c.m === 'sendMessage' && c.p.chat_id === 800).length, 1);
  const sub = Links.subscription(store.user(800), store.enabledNodes(), cfg, now, q);
  assert.strictEqual(sub.lines.length, 1); assert.ok(decodeURIComponent(sub.lines[0]).includes('Лимит трафика'));
  assert.match(sub.headers['subscription-userinfo'], new RegExp(`download=${3 * GB}; total=${3 * GB}`));
  // buying starts a new window with the plan's limit
  await wbot.fulfil({ uid: 800, plan: wbot.planById('m1'), charge: 'q1', stars: 199, method: 'stars' }, now + 3600e3);
  q = Quota.usage(store, cfg, store.user(800), now + 3600e3);
  assert.strictEqual(q.quotaGb, 50); assert.strictEqual(q.over, false);
  assert.ok(Quota.allowedUsers(store, cfg, now + 3600e3).some((u) => u.id === 800));
  ok('traffic limits by plan');

  // the owner (admin) has no traffic limit and no expiry
  store.ensureUser(admin, 0, now);
  store.addTraffic(today, 1, 500 * GB, 0);
  assert.strictEqual(Quota.usage(store, cfg, store.user(1), now).over, false);
  assert.ok(Quota.allowedUsers(store, cfg, now).some((u) => u.id === 1));
  ok('owner: unlimited');

  // partner program: 20% level 1, 5% level 2, every payment, balance -> subscription or USDT withdrawal approved by admin
  const pA = { id: 900, first_name: 'A', language_code: 'ru' }, pB = { id: 901, first_name: 'B', language_code: 'ru' }, pC = { id: 902, first_name: 'C', language_code: 'en' };
  await bot.handle(msg(pA, '/start'), now);
  await bot.handle(msg(pB, '/start r900'), now);
  await bot.handle(msg(pC, '/start r901'), now);
  const tcalls = [];
  const fakeCb2 = { call: async (m, p) => { tcalls.push({ m, p }); if (m === 'transfer' && p.amount === '999.00') throw new Error('INSUFFICIENT_FUNDS'); return { transfer_id: 1 }; } };
  const pbot = Bot.create({ cfg, store, tg, cb: fakeCb2, log: quiet });
  const aExp = store.user(900).expires;
  await pbot.fulfil({ uid: 902, plan: pbot.planById('m1'), charge: 'p1', usd: 2.99, method: 'crypto' }, now);
  const net = Math.floor(2.99 * 0.97 * 100); // 290 cents
  assert.strictEqual(store.user(901).bal, Math.floor(net * 0.2));   // B: level 1
  assert.strictEqual(store.user(900).bal, Math.floor(net * 0.05));  // A: level 2
  assert.strictEqual(store.user(900).expires, aExp, 'level 2 gets no days');
  assert.ok(store.user(901).expires > now, 'level 1 gets +3 days');
  await pbot.fulfil({ uid: 902, plan: pbot.planById('m1'), charge: 'p1', usd: 2.99, method: 'crypto' }, now); // same payment again
  assert.strictEqual(store.user(901).bal, Math.floor(net * 0.2));
  await pbot.fulfil({ uid: 902, plan: pbot.planById('m3'), charge: 'p2', usd: 7.49, method: 'crypto' }, now);
  assert.ok(store.user(901).bal > Math.floor(net * 0.2), 'commission on every payment');
  const rs2 = store.refStats(900);
  assert.strictEqual(rs2.l1, 1); assert.strictEqual(rs2.l2, 1);
  assert.ok(pbot.referral.text(store.user(901)).includes('20%'));
  // paying from the balance: no new commission
  store.credit(901, 1000, 'gift', 0, 'g1', now);
  const balA = store.user(900).bal, balB = store.user(901).bal;
  const wpa = WebApp.create({ cfg, store, bot: pbot, tg, cb: fakeCb2, log: quiet });
  const initB = WebApp.signInitData(cfg.botToken, pB, now);
  let [c2, rr2] = await wpa.api('pay', initB, { plan: 'm1', method: 'balance' }, now);
  assert.strictEqual(c2, 200); assert.strictEqual(rr2.kind, 'done');
  assert.strictEqual(store.user(901).bal, balB - 299);
  assert.strictEqual(store.user(900).bal, balA);
  // withdraw: minimum $5, admin approves -> CryptoBot transfer; decline -> money back
  store.credit(901, 1000, 'gift', 0, 'g2', now);
  const wbal = store.user(901).bal;
  [c2, rr2] = await wpa.api('withdraw', initB, {}, now);
  assert.strictEqual(c2, 200); assert.strictEqual(store.user(901).bal, 0);
  const wid = Number(/wd_ok:(\d+)/.exec(JSON.stringify(calls.filter((c) => c.m === 'sendMessage' && c.p.chat_id === 1).pop().p.reply_markup))[1]);
  await pbot.handle(cb(pB, `wd_ok:${wid}`), now); // not an admin: ignored
  assert.strictEqual(store.withdrawal(wid).status, 'pending');
  await pbot.handle(cb(admin, `wd_ok:${wid}`), now);
  assert.strictEqual(store.withdrawal(wid).status, 'paid');
  const tr = tcalls.find((c) => c.m === 'transfer').p;
  assert.strictEqual(tr.user_id, 901); assert.strictEqual(tr.asset, 'USDT'); assert.strictEqual(tr.amount, (wbal / 100).toFixed(2)); assert.strictEqual(tr.spend_id, `yurk-wd-${wid}`);
  assert.strictEqual((await wpa.api('withdraw', initB, {}, now))[0], 400); // nothing left
  store.credit(901, 700, 'gift', 0, 'g3', now);
  await wpa.api('withdraw', initB, {}, now);
  const wid2 = wid + 1;
  await pbot.handle(cb(admin, `wd_no:${wid2}`), now);
  assert.strictEqual(store.withdrawal(wid2).status, 'declined'); assert.strictEqual(store.user(901).bal, 700);
  assert.ok(Money.report(store, cfg, now).partners > 0);
  ok('partner program: 20% + 5%, balance, withdrawals');

  // the page itself is served
  const srv2 = http.createServer(createApp({ cfg, store, bot, webapp: wa, log: quiet })).listen(0);
  const b2 = `http://127.0.0.1:${srv2.address().port}`;
  const html = await (await fetch(b2 + '/app')).text();
  assert.ok(html.includes('telegram-web-app.js') && html.includes('YURK'));
  const rr = await fetch(b2 + '/api/me', { method: 'POST', headers: { 'x-init-data': init }, body: '{}' });
  assert.strictEqual(rr.status, 200);
  srv2.close();
  ok('mini app: page + api over http');

  console.log(`\n${n} tests passed`);
})().catch((e) => { console.error(e); process.exit(1); });
