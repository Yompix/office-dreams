'use strict';
/* Telegram helpers: Mini App initData validation, signed sessions, Bot API client. */
const crypto = require('crypto');

const hmac = (key, data, enc) => crypto.createHmac('sha256', key).update(data).digest(enc);

/* ---- Mini App initData (https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app) ---- */
function validateInitData(initData, botToken, maxAgeSec = 86400, now = Date.now()) {
  if (!initData || typeof initData !== 'string' || initData.length > 4096) return { ok: false, err: 'format' };
  let params;
  try { params = new URLSearchParams(initData); } catch { return { ok: false, err: 'format' }; }
  const hash = params.get('hash');
  if (!hash || !/^[0-9a-f]{64}$/i.test(hash)) return { ok: false, err: 'nohash' };
  params.delete('hash');
  const pairs = [];
  for (const [k, v] of params.entries()) pairs.push(k + '=' + v);
  pairs.sort();
  const secret = hmac('WebAppData', botToken);
  const calc = hmac(secret, pairs.join('\n'), 'hex');
  const a = Buffer.from(calc, 'hex'), b = Buffer.from(hash.toLowerCase(), 'hex');
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return { ok: false, err: 'hash' };
  const authDate = Number(params.get('auth_date')) || 0;
  if (!authDate || now / 1000 - authDate > maxAgeSec || authDate - now / 1000 > 300) return { ok: false, err: 'expired' };
  let user = null;
  try { user = JSON.parse(params.get('user') || 'null'); } catch { /* ignore */ }
  if (!user || !Number.isSafeInteger(user.id) || user.id <= 0) return { ok: false, err: 'nouser' };
  return { ok: true, user, startParam: params.get('start_param') || '', authDate, chatType: params.get('chat_type') || '' };
}

// Test helper / dev tool: builds a correctly signed initData string.
function signInitData(botToken, user, extra = {}, now = Date.now()) {
  const params = new URLSearchParams();
  params.set('auth_date', String(Math.floor(now / 1000)));
  params.set('query_id', 'AAH' + crypto.randomBytes(6).toString('hex'));
  params.set('user', JSON.stringify(user));
  for (const [k, v] of Object.entries(extra)) if (v !== undefined && v !== '') params.set(k, String(v));
  const pairs = [];
  for (const [k, v] of params.entries()) pairs.push(k + '=' + v);
  pairs.sort();
  params.set('hash', hmac(hmac('WebAppData', botToken), pairs.join('\n'), 'hex'));
  return params.toString();
}

/* ---- signed session tokens (stateless) ---- */
const b64u = (buf) => Buffer.from(buf).toString('base64url');
function makeToken(secret, uid, ttlSec = 12 * 3600, now = Date.now()) {
  const body = b64u(JSON.stringify({ u: uid, e: Math.floor(now / 1000) + ttlSec, n: crypto.randomBytes(4).toString('hex') }));
  return body + '.' + hmac(secret, body, 'base64url');
}
function readToken(secret, token, now = Date.now()) {
  if (typeof token !== 'string' || token.length > 400) return null;
  const i = token.indexOf('.');
  if (i < 1) return null;
  const body = token.slice(0, i), sig = token.slice(i + 1);
  const exp = hmac(secret, body, 'base64url');
  const a = Buffer.from(exp), b = Buffer.from(sig);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  try {
    const o = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
    if (!o || !Number.isSafeInteger(o.u) || o.e < now / 1000) return null;
    return o;
  } catch { return null; }
}

/* ---- Bot API client ---- */
function botApi(cfg) {
  const base = () => `${cfg.tgApi}/bot${cfg.botToken}`;
  async function call(method, params = {}, timeoutMs = 15000) {
    if (!cfg.botToken) return { ok: false, description: 'BOT_TOKEN is not set' };
    const ctl = new AbortController();
    const to = setTimeout(() => ctl.abort(), timeoutMs);
    try {
      const res = await fetch(`${base()}/${method}`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(params),
        signal: ctl.signal,
      });
      const j = await res.json().catch(() => ({ ok: false, description: 'bad json (' + res.status + ')' }));
      return j;
    } catch (e) {
      return { ok: false, description: String(e && e.message || e), network: true };
    } finally {
      clearTimeout(to);
    }
  }
  return { call };
}

module.exports = { validateInitData, signInitData, makeToken, readToken, botApi, hmac };
