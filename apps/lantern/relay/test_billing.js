'use strict';
// 2.0: проверка покупок Google Play на сервере — против ПОДДЕЛЬНОГО Google (локальный HTTP-сервер и одноразовый
// RSA-ключ, созданный тестом; настоящих ключей здесь нет). Подписка → pro_until до даты окончания у Google,
// искры начисляются один раз на токен, чужой токен не принимается, неоплаченное не начисляется, без ключа — отказ.
process.env.PORT = process.env.PORT || '18947';
process.env.QUIET = '1';
const assert = require('assert');
const http = require('http');
const crypto = require('crypto');
const WebSocket = require('ws');

const { privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const pem = privateKey.export({ type: 'pkcs8', format: 'pem' });
const calls = [];
const until = Date.now() + 29 * 86400_000;
const google = http.createServer((req, res) => {
  let body = ''; req.on('data', (d) => (body += d)); req.on('end', () => {
    calls.push(req.method + ' ' + req.url.split('?')[0]);
    const out = (code, j) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(j)); };
    if (req.url === '/token') {
      const a = new URLSearchParams(body).get('assertion') || '';
      const [h, p, s] = a.split('.');
      const ok = crypto.createVerify('RSA-SHA256').update(h + '.' + p).verify(crypto.createPublicKey(privateKey), Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64'));
      return ok ? out(200, { access_token: 'test-access', expires_in: 3600 }) : out(401, {});
    }
    if (req.headers.authorization !== 'Bearer test-access') return out(401, {});
    let m;
    if ((m = req.url.match(/subscriptionsv2\/tokens\/([^/:?]+)$/))) {
      if (m[1].startsWith('sub-ok')) return out(200, { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING', latestOrderId: 'GPA.1', lineItems: [{ productId: 'lantern_pro_month', expiryTime: new Date(until).toISOString() }] });
      return out(200, { subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED', lineItems: [{ productId: 'lantern_pro_month', expiryTime: new Date().toISOString() }] });
    }
    if ((m = req.url.match(/products\/([^/]+)\/tokens\/([^/:?]+)$/))) {
      if (m[2].startsWith('pending')) return out(200, { purchaseState: 2, acknowledgementState: 0 });
      return out(200, { purchaseState: 0, acknowledgementState: 0, orderId: 'GPA.2' });
    }
    if (req.url.endsWith(':acknowledge')) return out(200, {});
    out(404, {});
  });
});

(async () => {
  await new Promise((r) => google.listen(0, '127.0.0.1', r));
  const gp = google.address().port;
  // сначала — без ключа: отказ
  const { server, social } = require('./server').standalone(Number(process.env.PORT));
  const URL = `ws://127.0.0.1:${process.env.PORT}/ws`;
  const open = () => new Promise((res, rej) => { const w = new WebSocket(URL); w.inbox = []; w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); }); w.on('open', () => res(w)); w.on('error', rej); });
  const tx = (w, o) => w.send(JSON.stringify(o));
  const wait = async (w, pred, ms = 4000) => { const t0 = Date.now(); for (;;) { const i = w.inbox.findIndex(pred); if (i >= 0) return w.inbox.splice(i, 1)[0]; if (Date.now() - t0 > ms) throw new Error('timeout ' + JSON.stringify(w.inbox.map((x) => x.t))); await new Promise((r) => setTimeout(r, 10)); } };
  const A = await open(), B = await open();
  const tA = crypto.randomBytes(32).toString('hex'), tB = crypto.randomBytes(32).toString('hex');
  tx(A, { t: 'register', token: tA, nick: 'Покупатель' }); await wait(A, (m) => m.t === 'welcome');
  tx(B, { t: 'register', token: tB, nick: 'Другой' }); await wait(B, (m) => m.t === 'welcome');
  const buy = async (w, product, token) => { tx(w, { t: 'purchase', product, token }); return wait(w, (m) => m.t === 'purchase' && m.token === token); };
  assert.equal((await buy(A, 'sparks_500', 'x'.repeat(30))).error, 'billing_not_configured');

  process.env.GOOGLE_SERVICE_ACCOUNT_JSON = JSON.stringify({ client_email: 'test@example.invalid', private_key: pem, token_uri: `http://127.0.0.1:${gp}/token` });
  process.env.LANTERN_PLAY_API = `http://127.0.0.1:${gp}`;
  const uid = social.userByToken(tA).id;
  const sparks0 = social.user(uid).sparks;
  // подписка
  const s1 = await buy(A, 'lantern_pro_month', 'sub-ok-' + 'a'.repeat(30));
  assert.equal(s1.ok, true); assert.ok(Math.abs(s1.until - until) < 1000);
  assert.ok(social.user(uid).pro_until >= until - 1000);
  assert.ok(calls.some((c) => c.includes('subscriptions/lantern_pro_month/tokens/') && c.endsWith(':acknowledge')));
  assert.equal((await buy(A, 'lantern_pro_month', 'sub-old-' + 'a'.repeat(30))).error, 'not_active');
  // искры: один раз на токен; повтор — без начисления, но можно «израсходовать»
  const tk = 'ok-sparks-' + 'b'.repeat(30);
  const p1 = await buy(A, 'sparks_500', tk); assert.equal(p1.ok, true); assert.equal(p1.consume, true);
  const p2 = await buy(A, 'sparks_500', tk); assert.equal(p2.ok, true); assert.equal(p2.already, true);
  assert.equal(social.user(uid).sparks, sparks0 + 500);
  // чужой токен
  assert.equal((await buy(B, 'sparks_500', tk)).error, 'token_used');
  // ещё не оплачено
  assert.equal((await buy(A, 'sparks_1200', 'pending-' + 'c'.repeat(30))).error, 'not_paid');
  // мусор
  assert.equal((await buy(A, 'free_money', 'x'.repeat(30))).error, 'unknown_product');
  assert.equal((await buy(A, 'sparks_500', 'short')).error, 'bad_token');
  console.log('billing: all passed');
  A.close(); B.close(); server.close(); google.close();
  process.exit(0);
})().catch((e) => { console.error('billing FAIL', e); process.exit(1); });
