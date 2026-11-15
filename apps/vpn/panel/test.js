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
  assert.strictEqual(inv.currency, 'XTR'); assert.strictEqual(inv.prices[0].amount, 150); assert.strictEqual(inv.payload, 'm1:200');
  await bot.handle({ update_id: 2, pre_checkout_query: { id: 'pc', from: bob, currency: 'XTR', total_amount: 150, invoice_payload: 'm1:200' } }, now);
  assert.strictEqual(last('answerPreCheckoutQuery').p.ok, true);
  await bot.handle({ update_id: 3, pre_checkout_query: { id: 'pc2', from: bob, currency: 'XTR', total_amount: 1, invoice_payload: 'm1:200' } }, now);
  assert.strictEqual(last('answerPreCheckoutQuery').p.ok, false);
  ok('invoice + pre-checkout (wrong price refused)');

  const paid = { update_id: 4, message: { message_id: 2, from: bob, chat: { id: 200 }, successful_payment: { currency: 'XTR', total_amount: 150, invoice_payload: 'm1:200', telegram_payment_charge_id: 'ch1' } } };
  const aliceBefore = store.user(100).expires;
  await bot.handle(paid, now);
  assert.strictEqual(store.user(200).expires, now + 30 * DAY);
  assert.strictEqual(store.user(100).expires, aliceBefore + 7 * DAY, 'inviter gets +7 days');
  await bot.handle(paid, now); // Telegram resent the same update
  assert.strictEqual(store.user(200).expires, now + 30 * DAY);
  assert.strictEqual(store.stats(now).stars, 150);
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
  assert.strictEqual(st.paying, 1); assert.strictEqual(st.stars, 300);
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

  console.log(`\n${n} tests passed`);
})().catch((e) => { console.error(e); process.exit(1); });
