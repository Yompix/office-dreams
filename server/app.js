'use strict';
/* HTTP application: static files, JSON API, Telegram webhook, admin. */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');

const E = require('../src/engine.js');
const DB = require('./db.js');
const TG = require('./tg.js');
const U = require('./users.js');
const Eco = require('./economy.js');
const AB = require('./antibot.js');
const Sync = require('./sync.js');
const Ton = require('./ton.js');
const Bot = require('./bot.js');
const Admin = require('./admin.js');
const TonPay = require('./tonpay.js');
const TZC = require('./tzcountry.json');

function createContext(cfg, opts = {}) {
  const db = DB.open(opts.dbPath || cfg.dbPath);
  const ctx = {
    cfg,
    db,
    cache: {},
    rate: new Map(),
    now: opts.now || (() => Date.now()),
    log: opts.log || ((...a) => console.log(new Date().toISOString(), ...a)),
    tg: opts.tg || TG.botApi(cfg),
  };
  return ctx;
}

/* ---------- small helpers ---------- */
function clientIp(ctx, req) {
  if (ctx.cfg.trustProxy) {
    const cf = req.headers['cf-connecting-ip'];
    if (cf) return String(cf);
    const xff = req.headers['x-forwarded-for'];
    if (xff) { const parts = String(xff).split(',').map((s) => s.trim()).filter(Boolean); if (parts.length) return parts[parts.length - 1]; }
  }
  return (req.socket && req.socket.remoteAddress) || '';
}
function hit(ctx, key, max, win = 60000, now = Date.now()) {
  let e = ctx.rate.get(key);
  if (!e || now - e.t > win) { e = { t: now, n: 0 }; ctx.rate.set(key, e); }
  e.n++;
  return e.n <= max;
}
function readBody(req, limit = 65536) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) { if (!req.__big) { req.__big = true; reject(Object.assign(new Error('too large'), { status: 413 })); } return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}
const SEC_HEADERS = {
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
};
function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...SEC_HEADERS });
  res.end(body);
}
function sendText(res, status, text, type = 'text/plain; charset=utf-8', extra = {}) {
  res.writeHead(status, { 'content-type': type, ...SEC_HEADERS, ...extra });
  res.end(text);
}

/* ---------- static ---------- */
const MIME = { '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.json': 'application/json', '.webp': 'image/webp', '.jpg': 'image/jpeg' };
function makeStatic(cfg) {
  const distIndex = path.join(cfg.root, 'dist', 'index.html');
  let cached = null;
  const pub = path.join(cfg.root, 'public');
  function index() {
    let st;
    try { st = fs.statSync(distIndex); } catch { return null; }
    if (!cached || cached.mtime !== st.mtimeMs) {
      const raw = fs.readFileSync(distIndex);
      cached = { mtime: st.mtimeMs, raw, gz: zlib.gzipSync(raw, { level: 9 }) };
    }
    return cached;
  }
  return function serve(req, res, p) {
    if (p === '/' || p === '/index.html') {
      const f = index();
      if (!f) return sendText(res, 503, 'Game bundle not built. Run: npm run build');
      const gz = /\bgzip\b/.test(req.headers['accept-encoding'] || '');
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache', 'content-encoding': gz ? 'gzip' : 'identity', vary: 'accept-encoding', ...SEC_HEADERS });
      return res.end(gz ? f.gz : f.raw);
    }
    const name = path.basename(p);
    if (/^[\w.\-]+$/.test(name) && p === '/' + name && MIME[path.extname(name).toLowerCase()]) {
      const file = path.join(pub, name);
      if (file.startsWith(pub) && fs.existsSync(file)) {
        res.writeHead(200, { 'content-type': MIME[path.extname(name).toLowerCase()], 'cache-control': 'public, max-age=86400', ...SEC_HEADERS });
        return fs.createReadStream(file).pipe(res);
      }
    }
    return sendText(res, 404, 'Not found');
  };
}

/* ---------- API ---------- */
function publicConfig(ctx) {
  const c = ctx.cfg;
  return {
    v: 2,
    bot: c.botUsername,
    app: c.appShort,
    season: c.season,
    symbol: c.tokenSymbol,
    tonconnect: { cdn: c.tonconnectCdn, manifest: c.publicUrl ? c.publicUrl + '/tonconnect-manifest.json' : '' },
    dev: c.devAuth,
    links: { channel: c.channelUrl, chat: c.chatUrl, x: c.xUrl },
    eco: { D: c.eco.dailyD, l1: c.eco.refL1, l2: c.eco.refL2, cashEach: c.eco.inviteCash, gemsEach: c.eco.inviteGems, pass: c.eco.passBoost },
    shop: Eco.SHOP,
    tonPay: TonPay.enabled(c),
    tickMs: E.TICK * 1000,
    turnstile: c.turnstile.site || '',
    checkin: Eco.CHECKIN,
    ads: c.ads.blockId ? { provider: c.ads.provider, block: c.ads.blockId, sdk: c.ads.provider === 'monetag' ? c.ads.sdk : '', perDay: c.ads.perDay, cooldown: c.ads.cooldownSec, pts: c.ads.pts, callback: !!c.ads.callbackKey } : null,
  };
}
function authUid(ctx, req) {
  const h = String(req.headers.authorization || '');
  const m = /^Bearer (.+)$/.exec(h);
  if (!m) return null;
  const t = TG.readToken(ctx.cfg.sessionSecret, m[1]);
  return t ? t.u : null;
}
const bad = (res, status, err) => sendJson(res, status, { ok: false, err });

async function handleAuth(ctx, req, res, body, ip, now) {
  const { cfg } = ctx;
  if (!hit(ctx, 'auth:' + ip, cfg.limits.authPerMinIp, 60000, now)) return bad(res, 429, 'rate');
  let tu, startParam = '';
  if (cfg.devAuth && body.dev && Number.isSafeInteger(body.dev.id) && body.dev.id > 0) {
    tu = { id: body.dev.id, first_name: String(body.dev.name || 'Dev ' + body.dev.id), language_code: body.dev.lang || 'en', is_premium: !!body.dev.premium, username: body.dev.username };
    startParam = String(body.dev.start || '');
  } else {
    if (!cfg.botToken) return bad(res, 503, 'not_configured');
    const v = TG.validateInitData(body.initData, cfg.botToken, 86400, now);
    if (!v.ok) return bad(res, 401, 'initdata_' + v.err);
    tu = v.user;
    startParam = v.startParam;
  }
  const platform = ['android', 'ios', 'tdesktop', 'macos', 'weba', 'webk', 'webz', 'android_x', 'web'].includes(body.platform) ? body.platform : 'unknown';
  const fp = typeof body.fp === 'string' ? body.fp.slice(0, 300) : '';
  const { user, created } = U.ensureUser(ctx, tu, { startParam, platform, ip, dev: fp, now });
  if (user.deleted) return bad(res, 403, 'deleted');
  {
    const tz = typeof body.tz === 'string' && /^[A-Za-z0-9_+\-/]{1,64}$/.test(body.tz) ? body.tz : null;
    const cf = String(req.headers['cf-ipcountry'] || '').toUpperCase();
    const country = (tz && TZC[tz]) || (/^[A-Z]{2}$/.test(cf) && cf !== 'XX' && cf !== 'T1' ? cf : null);
    if (tz || country) ctx.db.run('UPDATE users SET tz=COALESCE(?,tz), country=COALESCE(?,country) WHERE id=?', tz, country, user.id);
  }
  const r = Sync.doSync(ctx, user.id, {}, now);
  const token = TG.makeToken(cfg.sessionSecret, user.id, 12 * 3600, now);
  if (created && user.ref1) Bot.notifyReferral(ctx, user.ref1, user.name).catch(() => {});
  if (created && user.ref1) {
    // a new account that came through an invite: evaluate the cluster signals immediately
    const fresh = U.getUser(ctx, user.id);
    AB.evaluate(ctx, fresh, now);
    Eco.persistUser(ctx, fresh);
  }
  return sendJson(res, 200, Object.assign({}, r.json, {
    token,
    created,
    me: { id: user.id, name: user.name, lang: user.lang },
  }));
}

async function handleApi(ctx, req, res, url, ip) {
  const { cfg } = ctx;
  const method = req.method;
  const p = url.pathname;
  const now = ctx.now();
  if (method === 'GET' && p === '/api/config') return sendJson(res, 200, publicConfig(ctx));
  let body = {};
  if (method === 'POST') {
    let raw = '';
    try { raw = await readBody(req); } catch (e) { res.once('finish', () => req.destroy()); return bad(res, e.status || 400, 'body'); }
    if (raw) { try { body = JSON.parse(raw); } catch { return bad(res, 400, 'json'); } }
    if (!body || typeof body !== 'object') body = {};
  }
  if (method === 'POST' && p === '/api/auth') return handleAuth(ctx, req, res, body, ip, now);
  // ad network server-to-server callback (only when ADS_CALLBACK_KEY is set): /api/ads/cb?key=...&uid=<telegram id>
  if (p === '/api/ads/cb') {
    const key = url.searchParams.get('key') || '';
    if (!cfg.ads.callbackKey || key.length !== cfg.ads.callbackKey.length || !crypto.timingSafeEqual(Buffer.from(key), Buffer.from(cfg.ads.callbackKey))) return bad(res, 403, 'key');
    const cuid = Number(url.searchParams.get('uid') || url.searchParams.get('userid') || url.searchParams.get('userId'));
    if (!Number.isSafeInteger(cuid) || cuid <= 0) return bad(res, 400, 'uid');
    return sendJson(res, 200, Eco.adReward(ctx, cuid, 'cb', now));
  }
  // Monetag postback: /api/ads/monetag?key=...&ymid={ymid}&rt={reward_event_type}&et={event_type}
  if (p === '/api/ads/monetag') {
    const key = url.searchParams.get('key') || '';
    if (!cfg.ads.callbackKey || key.length !== cfg.ads.callbackKey.length || !crypto.timingSafeEqual(Buffer.from(key), Buffer.from(cfg.ads.callbackKey))) return bad(res, 403, 'key');
    const ymid = String(url.searchParams.get('ymid') || '');
    const m = /^(\d{1,15})-([a-z0-9]{4,20})$/.exec(ymid);
    if (!m) return bad(res, 400, 'ymid');
    const rt = url.searchParams.get('rt') || url.searchParams.get('reward_event_type') || '';
    const et = url.searchParams.get('et') || url.searchParams.get('event_type') || 'impression';
    if (rt !== 'valued' || et !== 'impression') return sendJson(res, 200, { ok: true, skipped: true }); // unpaid / fraud traffic gets nothing
    return sendJson(res, 200, Eco.adReward(ctx, Number(m[1]), 'mt:' + ymid, now));
  }

  const uid = authUid(ctx, req);
  if (!uid) return bad(res, 401, 'auth');
  if (p === '/api/sync') {
    if (!hit(ctx, 'sync:' + uid, cfg.limits.syncPerMin, 60000, now)) return bad(res, 429, 'rate');
    const r = Sync.doSync(ctx, uid, body, now);
    return sendJson(res, r.status, r.json);
  }
  if (!hit(ctx, 'api:' + uid, cfg.limits.apiPerMin, 60000, now)) return bad(res, 429, 'rate');
  const u0 = U.getUser(ctx, uid);
  if (!u0 || u0.deleted) return bad(res, 404, 'nouser');

  if (method === 'POST' && p === '/api/checkin') {
    const out = ctx.db.tx(() => {
      const u = U.getUser(ctx, uid);
      const s = U.loadState(ctx, uid);
      const r = Eco.checkin(ctx, u, s, now);
      Eco.persistUser(ctx, u);
      return Object.assign(r, { eco: Eco.ecoView(ctx, u, now) });
    });
    return sendJson(res, 200, out);
  }
  if (method === 'GET' && p === '/api/tasks') return sendJson(res, 200, { ok: true, tasks: Eco.taskView(ctx, u0, now) });
  if (method === 'POST' && p === '/api/tasks/start') return sendJson(res, 200, Eco.taskStart(ctx, u0, String(body.id || ''), now));
  if (method === 'POST' && p === '/api/tasks/claim') {
    const r = await Eco.taskClaim(ctx, uid, String(body.id || ''), now);
    const u = U.getUser(ctx, uid);
    return sendJson(res, 200, Object.assign(r, { tasks: Eco.taskView(ctx, u, now), eco: Eco.ecoView(ctx, u, now) }));
  }
  if (method === 'GET' && p === '/api/friends') return sendJson(res, 200, Object.assign({ ok: true }, Eco.friendsList(ctx, uid), { eco: Eco.ecoView(ctx, u0, now) }));
  if (method === 'GET' && p === '/api/leaderboard') return sendJson(res, 200, Object.assign({ ok: true }, Eco.leaderboard(ctx, uid, now)));
  if (method === 'GET' && p === '/api/shop') {
    const lang = u0.lang;
    const tonOn = TonPay.enabled(cfg);
    return sendJson(res, 200, { ok: true, ton: tonOn, vip: Eco.vipOf(ctx, uid), items: Eco.shopView(ctx, u0).map((it) => { const t = Bot.shopTxt(lang, it.sku, it.cash); return { ...it, ton: tonOn ? TonPay.tonPrice(cfg, it) : null, title: t[0], desc: t[1] }; }) });
  }
  if (method === 'POST' && p === '/api/shop/invoice') {
    const sku = String(body.sku || '');
    const it = Eco.shopItem(sku);
    if (!it || !Eco.canBuy(ctx, uid, sku)) return bad(res, 409, 'unavailable');
    const [title, desc] = Bot.shopTxt(u0.lang, sku, Eco.packCash(ctx, uid, it));
    const r = await ctx.tg.call('createInvoiceLink', {
      title, description: desc, payload: `${uid}:${sku}:${crypto.randomBytes(4).toString('hex')}`,
      provider_token: '', currency: 'XTR', prices: [{ label: title, amount: it.stars }],
    });
    if (!r.ok) return bad(res, 502, 'invoice');
    return sendJson(res, 200, { ok: true, url: r.result });
  }
  if (method === 'POST' && p === '/api/shop/ton') return sendJson(res, 200, TonPay.createOrder(ctx, uid, String(body.sku || ''), now));
  if (method === 'POST' && p === '/api/shop/ton/check') {
    const r = await TonPay.checkOrder(ctx, uid, String(body.id || ''), now);
    return sendJson(res, 200, Object.assign(r, { eco: Eco.ecoView(ctx, U.getUser(ctx, uid), now) }));
  }
  if (method === 'POST' && p === '/api/ads/claim') {
    if (cfg.ads.callbackKey) return sendJson(res, 200, { ok: false, err: 'ads_callback', eco: Eco.ecoView(ctx, u0, now) });
    const r = Eco.adReward(ctx, uid, 'client', now);
    return sendJson(res, 200, Object.assign(r, { eco: Eco.ecoView(ctx, U.getUser(ctx, uid), now) }));
  }
  if (method === 'GET' && p === '/api/ton/payload') return sendJson(res, 200, { ok: true, payload: Ton.makePayload(cfg.sessionSecret, now) });
  if (method === 'POST' && p === '/api/wallet') return handleWallet(ctx, res, uid, body, now);
  if (method === 'POST' && p === '/api/lang') {
    const lang = ['en', 'ru', 'uk'].includes(body.lang) ? body.lang : null;
    if (!lang) return bad(res, 400, 'lang');
    ctx.db.run('UPDATE users SET lang=? WHERE id=?', lang, uid);
    return sendJson(res, 200, { ok: true });
  }
  if (p === '/api/challenge') {
    if (method === 'GET') {
      const c = AB.pendingChallenge(u0, now);
      return sendJson(res, 200, { ok: true, challenge: c ? c.pub : null });
    }
    if (method === 'POST') {
      const out = await AB.answerChallenge(ctx, uid, String(body.id || ''), body.a, { ts: body.ts, ip }, now);
      return sendJson(res, 200, out);
    }
  }
  if (method === 'POST' && p === '/api/forget') {
    if (body.confirm !== true) return bad(res, 400, 'confirm');
    ctx.db.tx(() => {
      ctx.db.run('DELETE FROM states WHERE uid=?', uid);
      ctx.db.run('DELETE FROM fps WHERE uid=?', uid);
      ctx.db.run('DELETE FROM beh WHERE uid=?', uid);
      ctx.db.run('DELETE FROM pending_refs WHERE uid=?', uid);
      ctx.db.run("UPDATE users SET deleted=1, username=NULL, name='deleted', wallet=NULL, wallet_ok=0 WHERE id=?", uid);
    });
    return sendJson(res, 200, { ok: true });
  }
  return bad(res, 404, 'route');
}

function handleWallet(ctx, res, uid, body, now) {
  const { cfg } = ctx;
  return ctx.db.tx(() => {
    const u = U.getUser(ctx, uid);
    if (body.address === null || body.disconnect) {
      u.wallet = null; u.wallet_ok = 0; u.wallet_at = now;
      Eco.persistUser(ctx, u);
      return sendJson(res, 200, { ok: true, eco: Eco.ecoView(ctx, u, now) });
    }
    let addr, level = 0;
    if (body.proof && body.account) {
      let domain = '';
      try { domain = new URL(cfg.publicUrl).host; } catch { /* dev */ }
      const r = Ton.verifyTonProof({ account: body.account, proof: body.proof }, { secret: cfg.sessionSecret, domain, now });
      addr = r.addr || Ton.parseAddress(body.account.address);
      if (r.ok) level = r.level === 'full' ? 1 : 2;
    } else {
      addr = Ton.parseAddress(body.address);
    }
    if (!addr) return sendJson(res, 200, { ok: false, err: 'address' });
    const raw = Ton.rawAddress(addr);
    const dup = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE wallet=? AND id!=? AND deleted=0', raw, uid).n;
    if (dup > 0) {
      AB.addHard(ctx, u, 15, 'wallet_dup', { dup }, now);
      for (const o of ctx.db.all('SELECT id FROM users WHERE wallet=? AND id!=?', raw, uid)) {
        const ou = U.getUser(ctx, o.id);
        AB.addHard(ctx, ou, 5, 'wallet_dup', { with: uid }, now);
        Eco.persistUser(ctx, ou);
      }
    }
    u.wallet = raw; u.wallet_ok = level; u.wallet_at = now;
    AB.evaluate(ctx, u, now);
    Eco.persistUser(ctx, u);
    return sendJson(res, 200, { ok: true, level, friendly: Ton.toFriendly(addr, { bounceable: false, testnet: !!addr.testnet }), eco: Eco.ecoView(ctx, u, now) });
  });
}

/* ---------- application ---------- */
function createApp(cfg, opts = {}) {
  const ctx = createContext(cfg, opts);
  const serve = makeStatic(cfg);
  const admin = Admin.create(ctx);

  async function handler(req, res) {
    const ip = clientIp(ctx, req);
    let url;
    try { url = new URL(req.url, 'http://local'); } catch { return sendText(res, 400, 'bad url'); }
    const p = url.pathname;
    try {
      if (p === '/healthz') return sendJson(res, 200, { ok: true, up: Math.round(process.uptime()) });
      if (p.startsWith('/api/')) return await handleApi(ctx, req, res, url, ip);
      if (p.startsWith('/admin')) return await admin(req, res, url, ip);
      if (req.method === 'POST' && p === '/tg/' + cfg.webhookSecret) {
        if (req.headers['x-telegram-bot-api-secret-token'] !== cfg.webhookSecret) return sendText(res, 403, 'forbidden');
        let up;
        try { up = JSON.parse(await readBody(req, 262144)); } catch { return sendText(res, 400, 'bad'); }
        sendText(res, 200, 'ok');
        Bot.handleUpdate(ctx, up);
        return;
      }
      if (p === '/tonconnect-manifest.json') {
        return sendJson(res, 200, { url: cfg.publicUrl || `http://${req.headers.host}`, name: 'Office Dreams', iconUrl: (cfg.publicUrl || `http://${req.headers.host}`) + '/icon-180.png' });
      }
      if (req.method !== 'GET' && req.method !== 'HEAD') return sendText(res, 405, 'method');
      return serve(req, res, p);
    } catch (e) {
      ctx.log('request error', req.method, p, e && e.stack || e);
      if (!res.headersSent) sendJson(res, 500, { ok: false, err: 'server' });
      else res.end();
    }
  }
  return { ctx, handler };
}

module.exports = { createApp, createContext, publicConfig, hit };
