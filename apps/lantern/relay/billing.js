'use strict';
// LANTERN 2.0: проверка покупок Google Play на сервере (Google Play Developer API, androidpublisher v3).
// Приложение присылает {t:'purchase', product, token}; сервер спрашивает Google, действительно ли покупка оплачена,
// подтверждает её (acknowledge) и только потом начисляет. Без ключа сервисного аккаунта ничего не начисляется.
//
// Переменные окружения (значения НИКОГДА не печатаются и не пишутся в журнал):
//   GOOGLE_SERVICE_ACCOUNT_JSON — содержимое JSON-ключа сервисного аккаунта (Play Console → «Доступ к API»)
//   LANTERN_PACKAGE             — имя пакета, по умолчанию com.yompix.lantern
//   LANTERN_PLAY_API            — адрес API (только для тестов: поддельный сервер); по умолчанию Google
// Инструкция владельцу: apps/lantern/godot/docs/PLAY_BILLING.md

const crypto = require('crypto');

const SUBS = new Set(['lantern_pro_month']);
const INAPP = new Set(['lantern_pass_s1', 'sparks_500', 'sparks_1200']);
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';

let cached = { token: '', exp: 0 };

function config() {
  const raw = process.env.GOOGLE_SERVICE_ACCOUNT_JSON || '';
  if (!raw) return null;
  try {
    const j = JSON.parse(raw);
    if (!j.client_email || !j.private_key) return null;
    return { email: j.client_email, key: j.private_key, tokenUri: j.token_uri || 'https://oauth2.googleapis.com/token',
      api: (process.env.LANTERN_PLAY_API || 'https://androidpublisher.googleapis.com').replace(/\/$/, ''),
      pkg: process.env.LANTERN_PACKAGE || 'com.yompix.lantern' };
  } catch { return null; }
}

function configured() { return !!config(); }

const b64u = (b) => Buffer.from(b).toString('base64').replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');

// OAuth 2.0 для сервисного аккаунта: подписанный RS256 JWT → токен доступа (живёт час, держим в памяти)
async function accessToken(c) {
  if (cached.token && Date.now() < cached.exp - 60_000) return cached.token;
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const body = b64u(JSON.stringify({ iss: c.email, scope: SCOPE, aud: c.tokenUri, iat: now, exp: now + 3600 }));
  const sig = b64u(crypto.createSign('RSA-SHA256').update(head + '.' + body).sign(c.key));
  const r = await fetch(c.tokenUri, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${head}.${body}.${sig}` }) });
  if (!r.ok) throw new Error('oauth ' + r.status);
  const j = await r.json();
  cached = { token: j.access_token, exp: Date.now() + (Number(j.expires_in) || 3600) * 1000 };
  return cached.token;
}

async function api(c, method, path) {
  const t = await accessToken(c);
  const r = await fetch(c.api + path, { method, headers: { authorization: 'Bearer ' + t, 'content-type': 'application/json' }, body: method === 'POST' ? '{}' : undefined });
  const text = await r.text();
  let j = {};
  try { j = text ? JSON.parse(text) : {}; } catch { j = {}; }
  return { status: r.status, json: j };
}

/**
 * Проверить покупку. → { ok, kind:'sub'|'inapp', until?:ms, error? }
 * Подписка: активна (или в льготном периоде) — until = дата окончания. Разовая: purchaseState 0 (оплачено).
 */
async function verify(product, token) {
  const c = config();
  if (!c) return { ok: false, error: 'billing_not_configured' };
  const pkg = encodeURIComponent(c.pkg), tok = encodeURIComponent(token), id = encodeURIComponent(product);
  try {
    if (SUBS.has(product)) {
      const r = await api(c, 'GET', `/androidpublisher/v3/applications/${pkg}/purchases/subscriptionsv2/tokens/${tok}`);
      if (r.status !== 200) return { ok: false, error: 'google_' + r.status };
      const st = String(r.json.subscriptionState || '');
      const li = (r.json.lineItems || []).find((x) => x.productId === product);
      if (!li) return { ok: false, error: 'wrong_product' };
      if (!['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'].includes(st)) return { ok: false, error: 'not_active' };
      if (r.json.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') {
        await api(c, 'POST', `/androidpublisher/v3/applications/${pkg}/purchases/subscriptions/${id}/tokens/${tok}:acknowledge`);
      }
      return { ok: true, kind: 'sub', until: Date.parse(li.expiryTime) || Date.now() + 30 * 86400_000, order: String(r.json.latestOrderId || '') };
    }
    if (INAPP.has(product)) {
      const r = await api(c, 'GET', `/androidpublisher/v3/applications/${pkg}/purchases/products/${id}/tokens/${tok}`);
      if (r.status !== 200) return { ok: false, error: 'google_' + r.status };
      if (r.json.purchaseState !== 0) return { ok: false, error: 'not_paid' };     // 1 — отменена, 2 — ожидает оплаты
      if (r.json.acknowledgementState !== 1) {
        await api(c, 'POST', `/androidpublisher/v3/applications/${pkg}/purchases/products/${id}/tokens/${tok}:acknowledge`);
      }
      return { ok: true, kind: 'inapp', order: String(r.json.orderId || ''), consumable: product.startsWith('sparks_') };
    }
    return { ok: false, error: 'unknown_product' };
  } catch (e) {
    return { ok: false, error: 'google_unreachable' };
  }
}

function _reset() { cached = { token: '', exp: 0 }; }

module.exports = { verify, configured, SUBS, INAPP, _reset };
