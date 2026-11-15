'use strict';
/* Mini App API: Telegram login (initData), status, trial, payments (Stars, CryptoBot, cards), key reset. */
const crypto = require('crypto');

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

  function view(u, now = Date.now()) {
    const active = u.expires > now && !u.banned;
    return {
      brand: cfg.brand, bot: cfg.botUsername, lang: u.lang, name: u.name,
      active, expires: u.expires, daysLeft: active ? Math.ceil((u.expires - now) / 86400000) : 0,
      trial: !u.trial && !active && cfg.trialDays > 0, trialDays: cfg.trialDays,
      sub: active ? subUrl(u) : null,
      ref: cfg.botUsername ? `https://t.me/${cfg.botUsername}?start=r${u.id}` : null, refDays: cfg.refDays,
      plans: cfg.plans.map((p) => ({ id: p.id, days: p.days, stars: p.stars, usd: p.usd, card: cfg.cardToken ? cardAmount(cfg, p) / 100 : null })),
      methods: { stars: true, crypto: !!cb, card: !!cfg.cardToken }, cardCurrency: cfg.cardCurrency,
      nodes: store.enabledNodes().map((n) => n.name),
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
        const [pid, uid] = String(inv.payload || '').split(':');
        const plan = bot.planById(pid);
        if (!plan || !store.user(Number(uid))) continue;
        if (await bot.fulfil({ uid: Number(uid), plan, charge: `cb:${inv.invoice_id}`, usd: Number(inv.amount) || plan.usd, method: 'crypto' }, now)) n++;
      }
      return n;
    })().finally(() => { polling = null; });
    return polling;
  }

  async function pay(u, planId, method) {
    const plan = bot.planById(planId);
    if (!plan) return { error: 'plan' };
    const t = u.lang === 'ru' || u.lang === 'uk' ? 'ru' : 'en';
    const title = `${cfg.brand}: ${plan.days} ${t === 'ru' ? 'дн.' : 'days'}`;
    const description = t === 'ru' ? `VPN на ${plan.days} дн. Все серверы и устройства.` : `VPN for ${plan.days} days. All servers and devices.`;
    if (method === 'stars') {
      const link = await tg.call('createInvoiceLink', { title, description, payload: `${plan.id}:${u.id}`, currency: 'XTR', prices: [{ label: title, amount: plan.stars }] });
      return { kind: 'invoice', link };
    }
    if (method === 'card') {
      if (!cfg.cardToken) return { error: 'method' };
      const link = await tg.call('createInvoiceLink', {
        title, description, payload: `card:${plan.id}:${u.id}`, provider_token: cfg.cardToken,
        currency: cfg.cardCurrency, prices: [{ label: title, amount: cardAmount(cfg, plan) }],
      });
      return { kind: 'invoice', link };
    }
    if (method === 'crypto') {
      if (!cb) return { error: 'method' };
      const inv = await cb.call('createInvoice', {
        currency_type: 'fiat', fiat: 'USD', amount: String(plan.usd), accepted_assets: cfg.cryptoAssets,
        description, payload: `${plan.id}:${u.id}`, expires_in: 3600,
        paid_btn_name: 'callback', paid_btn_url: `https://t.me/${cfg.botUsername}`,
      });
      return { kind: 'link', link: inv.bot_invoice_url || inv.mini_app_invoice_url || inv.pay_url };
    }
    return { error: 'method' };
  }

  // POST /api/<action>, body JSON, header X-Init-Data
  async function api(action, initData, body = {}, now = Date.now()) {
    const auth = checkInitData(initData, cfg.botToken, now);
    if (!auth) return [401, { error: 'auth' }];
    const ref = /^r(\d+)$/.exec(auth.startParam);
    const { user } = store.ensureUser(auth.user, ref ? Number(ref[1]) : 0, now);
    if (action === 'me') {
      if (body.check) await pollCrypto(now).catch((e) => log.error(e.message));
      return [200, view(store.user(user.id), now)];
    }
    if (action === 'trial') {
      const ok = cfg.trialDays > 0 && store.startTrial(user.id, cfg.trialDays, now);
      return [ok ? 200 : 409, ok ? view(store.user(user.id), now) : { error: 'trial' }];
    }
    if (action === 'reset') return [200, view(store.resetKey(user.id), now)];
    if (action === 'pay') {
      const r = await pay(user, String(body.plan || ''), String(body.method || ''));
      return [r.error ? 400 : 200, r];
    }
    return [404, { error: 'action' }];
  }

  return { api, pollCrypto, view };
}

module.exports = { create, checkInitData, signInitData, cardAmount, cryptoBot };
