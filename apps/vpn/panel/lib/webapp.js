'use strict';
/* Mini App API: Telegram login (initData), status, trial, payments (Stars, CryptoBot, cards), key reset. */
const crypto = require('crypto');
const { tierOf, TIERS } = require('./regions.js');
const { pick } = require('./texts.js');
const Quota = require('./quota.js');

const hmac = (key, data, enc) => crypto.createHmac('sha256', key).update(data).digest(enc);

// https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
function checkInitData(initData, botToken, now = Date.now(), maxAgeSec = 86400) {
  if (!initData || typeof initData !== 'string' || initData.length > 4096) return null;
  let params;
  try { params = new URLSearchParams(initData); } catch { return null; }
  const hash = params.get('hash') || '';
  if (!/^[0-9a-f]{64}$/i.test(hash)) return null;
  params.delete('hash');
  const pairs = [...params.entries()].map(([k, v]) => `${k}=${v}`).sort();
  const calc = hmac(hmac('WebAppData', botToken), pairs.join('\n'), 'hex');
  if (!crypto.timingSafeEqual(Buffer.from(calc, 'hex'), Buffer.from(hash.toLowerCase(), 'hex'))) return null;
  const age = now / 1000 - Number(params.get('auth_date') || 0);
  if (!(age < maxAgeSec && age > -300)) return null;
  let user = null;
  try { user = JSON.parse(params.get('user') || 'null'); } catch { /* ignore */ }
  if (!user || !Number.isSafeInteger(user.id) || user.id <= 0) return null;
  return { user, startParam: params.get('start_param') || '' };
}

// test helper: a correctly signed initData string
function signInitData(botToken, user, now = Date.now(), extra = {}) {
  const p = new URLSearchParams({ auth_date: String(Math.floor(now / 1000)), user: JSON.stringify(user), ...extra });
  const pairs = [...p.entries()].map(([k, v]) => `${k}=${v}`).sort();
  p.set('hash', hmac(hmac('WebAppData', botToken), pairs.join('\n'), 'hex'));
  return p.toString();
}

const cardAmount = (cfg, plan) => Math.round(plan.usd * cfg.cardRate) * 100; // minor units (kopecks)

/* ---- CryptoBot (Crypto Pay API, https://help.crypt.bot/crypto-pay-api) ---- */
function cryptoBot(cfg, fetchImpl = fetch) {
  const base = cfg.cryptoTestnet ? 'https://testnet-pay.crypt.bot/api' : 'https://pay.crypt.bot/api';
  async function call(method, params = {}) {
    const r = await fetchImpl(`${base}/${method}`, {
      method: 'POST', headers: { 'content-type': 'application/json', 'Crypto-Pay-API-Token': cfg.cryptoToken },
      body: JSON.stringify(params), signal: AbortSignal.timeout(15000),
    });
    const j = await r.json().catch(() => ({}));
    if (!j.ok) throw new Error(`cryptobot ${method}: ${JSON.stringify(j.error || r.status)}`);
    return j.result;
  }
  return { call };
}

function create({ cfg, store, bot, tg, cb = cfg.cryptoToken ? cryptoBot(cfg) : null, log = console }) {
  const subUrl = (u) => `${cfg.publicUrl}/sub/${u.token}`;

  const cardOk = (u) => !!cfg.cardToken && cfg.cardLangs.includes(String(u.lang).slice(0, 2));

  function view(u, now = Date.now()) {
    if (cfg.adminIds.includes(u.id)) u = { ...u, expires: Math.max(u.expires, now + 3650 * 86400000) }; // the owner: always on
    const active = u.expires > now && !u.banned;
    const tier = tierOf(u.lang);
    return {
      brand: cfg.brand, bot: cfg.botUsername, lang: u.lang, name: u.name,
      active, expires: u.expires, daysLeft: active ? Math.ceil((u.expires - now) / 86400000) : 0,
      trial: !u.trial && !active && cfg.trialDays > 0, trialDays: cfg.trialDays,
      sub: active ? subUrl(u) : null,
      usage: (() => { const q = Quota.usage(store, cfg, u, now); return { used: q.usedGb, quota: q.quotaGb, over: q.over }; })(),
      ref: cfg.botUsername ? `https://t.me/${cfg.botUsername}?start=r${u.id}` : null, refDays: cfg.refDays,
      partner: bot.referral.info(store.user(u.id) || u),
      tier, plans: bot.plansFor(tier).map((p) => ({ id: p.id, days: p.days, stars: p.stars, usd: p.usd, gb: cfg.quotas[p.id] || 0, card: cardOk(u) ? cardAmount(cfg, p) / 100 : null })),
      methods: { stars: true, crypto: !!cb, card: cardOk(u), balance: (store.user(u.id) || u).bal > 0 }, cardCurrency: cfg.cardCurrency,
      nodes: store.enabledNodes().map((n) => n.name),
      nodeList: store.enabledNodes().map((n) => ({ id: n.id, name: n.name, reality: n.kind !== 'railway' || !!n.rport })),
      token: u.token, // the app keeps it to fetch its VPN config
      apk: cfg.apk.fileId ? { version: cfg.apk.version } : null,
      support: cfg.support || null,
    };
  }

  // paid CryptoBot invoices -> subscription days. Called every minute and when the app asks for its status.
  let polling = null;
  async function pollCrypto(now = Date.now()) {
    if (!cb) return 0;
    if (polling) return polling;
    polling = (async () => {
      let n = 0;
      const res = await cb.call('getInvoices', { status: 'paid', count: 100 });
      for (const inv of res.items || []) {
        const [pid, uid, tier = 'mid'] = String(inv.payload || '').split(':');
        const plan = TIERS.includes(tier) && bot.planById(pid, tier);
        if (!plan || !store.user(Number(uid))) continue;
        if (await bot.fulfil({ uid: Number(uid), plan, charge: `cb:${inv.invoice_id}`, usd: Number(inv.amount) || plan.usd, method: 'crypto' }, now)) n++;
      }
      return n;
    })().finally(() => { polling = null; });
    return polling;
  }

  async function pay(u, planId, method) {
    const tier = tierOf(u.lang);
    const plan = bot.planById(planId, tier);
    if (!plan) return { error: 'plan' };
    const t = pick(u.lang);
    const title = t.invTitle(cfg.brand, plan);
    const description = t.invDesc(plan);
    if (method === 'stars') {
      const link = await tg.call('createInvoiceLink', { title, description, payload: `${plan.id}:${u.id}:${tier}`, currency: 'XTR', prices: [{ label: title, amount: plan.stars }] });
      return { kind: 'invoice', link };
    }
    if (method === 'card') {
      if (!cardOk(u)) return { error: 'method' };
      const link = await tg.call('createInvoiceLink', {
        title, description, payload: `card:${plan.id}:${u.id}:${tier}`, provider_token: cfg.cardToken,
        currency: cfg.cardCurrency, prices: [{ label: title, amount: cardAmount(cfg, plan) }],
      });
      return { kind: 'invoice', link };
    }
    if (method === 'balance') {
      const cents = Math.round(plan.usd * 100);
      const charge = `bal:${u.id}:${Date.now()}`;
      if (!store.debit(u.id, cents, 'spend', charge)) return { error: 'balance' };
      await bot.fulfil({ uid: u.id, plan, charge, usd: plan.usd, method: 'balance' });
      return { kind: 'done' };
    }
    if (method === 'crypto') {
      if (!cb) return { error: 'method' };
      const inv = await cb.call('createInvoice', {
        currency_type: 'fiat', fiat: 'USD', amount: String(plan.usd), accepted_assets: cfg.cryptoAssets,
        description, payload: `${plan.id}:${u.id}:${tier}`, expires_in: 3600,
        paid_btn_name: 'callback', paid_btn_url: `https://t.me/${cfg.botUsername}`,
      });
      return { kind: 'link', link: inv.bot_invoice_url || inv.mini_app_invoice_url || inv.pay_url };
    }
    return { error: 'method' };
  }

  // POST /api/<action>, body JSON, header X-Init-Data
  async function api(action, initData, body = {}, now = Date.now(), appToken = '') {
    let user;
    if (appToken) {
      // the Android app: logged in with the subscription token it got from the bot
      user = /^[A-Za-z0-9_-]{16,64}$/.test(appToken) ? store.userByToken(appToken) : null;
      if (!user || user.banned) return [401, { error: 'auth' }];
    } else {
      const auth = checkInitData(initData, cfg.botToken, now);
      if (!auth) return [401, { error: 'auth' }];
      const ref = /^r(\d+)$/.exec(auth.startParam);
      user = store.ensureUser(auth.user, ref ? Number(ref[1]) : 0, now).user;
    }
    if (action === 'me') {
      if (body.check) await pollCrypto(now).catch((e) => log.error(e.message));
      return [200, view(store.user(user.id), now)];
    }
    if (action === 'trial') {
      const ok = cfg.trialDays > 0 && store.startTrial(user.id, cfg.trialDays, now);
      return [ok ? 200 : 409, ok ? view(store.user(user.id), now) : { error: 'trial' }];
    }
    if (action === 'reset') return [200, view(store.resetKey(user.id), now)];
    if (action === 'withdraw') { const r = await bot.referral.requestWithdraw(store.user(user.id), now); return [r.error ? 400 : 200, r.error ? r : { ...view(store.user(user.id), now), paidOut: !!r.paid }]; }
    if (action === 'pay') {
      const r = await pay(user, String(body.plan || ''), String(body.method || ''));
      return [r.error ? 400 : 200, r];
    }
    return [404, { error: 'action' }];
  }

  return { api, pollCrypto, view };
}

module.exports = { create, checkInitData, signInitData, cardAmount, cryptoBot };
