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
const PvP = require('./pvp.js');
const Studio = require('./studio.js');
const Eco = require('./economy.js');
const AB = require('./antibot.js');
const Sync = require('./sync.js');
const Bot = require('./bot.js');
const Admin = require('./admin.js');
const SolPay = require('./solpay.js');
const Airdrop = require('./airdrop.js');
const Market = require('./market.js');
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
const MIME = { '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.json': 'application/json', '.js': 'text/javascript; charset=utf-8', '.webp': 'image/webp', '.jpg': 'image/jpeg' };
const GAME_MIME = { ...MIME, '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.mp3': 'audio/mpeg', '.mp4': 'video/mp4', '.webmanifest': 'application/manifest+json', '.apk': 'application/vnd.android.package-archive' };
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
    // /games/: the games page and the browser games (public/games/**), plain files only
    if (p === '/games') { res.writeHead(301, { location: '/games/', ...SEC_HEADERS }); return res.end(); }
    if (p.startsWith('/games/')) {
      const games = path.join(pub, 'games');
      let rel; try { rel = decodeURIComponent(p.slice(7)); } catch { return sendText(res, 400, 'bad url'); }
      if (rel === '' || rel.endsWith('/')) rel += 'index.html';
      const file = path.join(games, path.normalize(rel));
      if (!file.startsWith(games + path.sep) || !/^[\w.\-/]+$/.test(rel)) return sendText(res, 404, 'Not found');
      // a folder without the trailing slash: /games/nightshift -> /games/nightshift/
      if (!path.extname(rel) && fs.existsSync(path.join(file, 'index.html'))) { res.writeHead(301, { location: p + '/', ...SEC_HEADERS }); return res.end(); }
      const type = GAME_MIME[path.extname(file).toLowerCase()];
      let st; try { st = type && fs.statSync(file); } catch { st = null; }
      if (!st || !st.isFile()) return sendText(res, 404, 'Not found');
      // html, js and json are re-checked on every visit so an update never pairs a new page with an old cached script
      const fresh = /^(text\/html|text\/javascript|application\/json)/.test(type);
      res.writeHead(200, { 'content-type': type, 'content-length': st.size, 'cache-control': fresh ? 'no-cache' : 'public, max-age=3600', ...SEC_HEADERS });
      return req.method === 'HEAD' ? res.end() : fs.createReadStream(file).pipe(res);
    }
    const name = path.basename(p);
    // public/ files, plus the illustrated backgrounds in public/art/
    const sub = p === '/art/' + name ? 'art' : '';
    if (/^[\w.\-]+$/.test(name) && (p === '/' + name || sub) && MIME[path.extname(name).toLowerCase()]) {
      const file = path.join(pub, sub, name);
      if (file.startsWith(pub) && fs.existsSync(file)) {
        // images may be loaded by wallets, explorers and DEX sites (the token logo): allow cross-origin use
        const img = /\.(png|jpg|webp|svg)$/i.test(name);
        res.writeHead(200, { 'content-type': MIME[path.extname(name).toLowerCase()], 'cache-control': name === 'token.png' ? 'public, max-age=3600' : 'public, max-age=86400', ...(img ? { 'access-control-allow-origin': '*', 'cross-origin-resource-policy': 'cross-origin' } : {}), ...SEC_HEADERS });
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
    dev: c.devAuth,
    links: { channel: c.channelUrl, chat: c.chatUrl, x: c.xUrl, site: /^https:/.test(c.publicUrl) ? c.publicUrl + '/site' : '' },
    eco: { D: c.eco.dailyD, l1: c.eco.refL1, l2: c.eco.refL2, cashEach: c.eco.inviteCash, gemsEach: c.eco.inviteGems, pass: c.eco.passBoost },
    shop: Eco.SHOP,
    solPay: SolPay.enabled(c),
    airdropWallet: Airdrop.distributorAddr(ctx),
    market: Market.enabled(c),
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
    // some Telegram clients drop start_param from the signed data — accept the copy the page saw (only used for invites)
    startParam = v.startParam || (typeof body.start === 'string' ? body.start.slice(0, 64) : '');
    if (startParam) ctx.log('auth start param', tu.id, startParam);
  }
  // closed regions: decided before an account is created
  const tzIn = typeof body.tz === 'string' && /^[A-Za-z0-9_+\-/]{1,64}$/.test(body.tz) ? body.tz : null;
  const cfIn = String(req.headers['cf-ipcountry'] || '').toUpperCase();
  const ctryIn = (tzIn && TZC[tzIn]) || (/^[A-Z]{2}$/.test(cfIn) ? cfIn : null);
  const langIn = String(tu.language_code || '').toLowerCase().split('-')[0];
  if ((ctryIn && cfg.blockedCountries.includes(ctryIn)) || (langIn && cfg.blockedLangs.includes(langIn))) {
    ctx.cache.blocked = (ctx.cache.blocked || 0) + 1;
    const ex = U.getUser(ctx, tu.id);
    if (ex && !ex.country && ctryIn) ctx.db.run('UPDATE users SET country=? WHERE id=?', ctryIn, ex.id);
    return bad(res, 403, 'region');
  }
  const platform = ['android', 'ios', 'tdesktop', 'macos', 'weba', 'webk', 'webz', 'android_x', 'web'].includes(body.platform) ? body.platform : 'unknown';
  const fp = typeof body.fp === 'string' ? body.fp.slice(0, 300) : '';
  const { user, created, lateBound } = U.ensureUser(ctx, tu, { startParam, platform, ip, dev: fp, now });
  if (user.deleted) return bad(res, 403, 'deleted');
  {
    const tz = typeof body.tz === 'string' && /^[A-Za-z0-9_+\-/]{1,64}$/.test(body.tz) ? body.tz : null;
    const cf = String(req.headers['cf-ipcountry'] || '').toUpperCase();
    const country = (tz && TZC[tz]) || (/^[A-Z]{2}$/.test(cf) && cf !== 'XX' && cf !== 'T1' ? cf : null);
    ctx.db.run('UPDATE users SET tz=COALESCE(?,tz), country=COALESCE(?,country), last_seen=? WHERE id=?', tz, country, now, user.id);
  }
  Eco.devSetup(ctx, user.id);
  const r = Sync.doSync(ctx, user.id, {}, now);
  const token = TG.makeToken(cfg.sessionSecret, user.id, 12 * 3600, now);
  if ((created || lateBound) && user.ref1) Bot.notifyReferral(ctx, user.ref1, user.name).catch(() => {});
  if ((created || lateBound) && user.ref1) {
    // a new account that came through an invite: evaluate the cluster signals immediately
    const fresh = U.getUser(ctx, user.id);
    AB.evaluate(ctx, fresh, now);
    Eco.persistUser(ctx, fresh);
  }
  return sendJson(res, 200, Object.assign({}, r.json, {
    token,
    created,
    me: { id: user.id, name: user.name, lang: user.lang, studio: user.studio || null },
  }));
}

async function handleApi(ctx, req, res, url, ip) {
  const { cfg } = ctx;
  const method = req.method;
  const p = url.pathname;
  const now = ctx.now();
  if (method === 'GET' && p === '/api/config') return sendJson(res, 200, publicConfig(ctx));
  if (method === 'GET' && p === '/api/market') return sendJson(res, 200, Market.view(ctx, now, String(url.searchParams.get('range') || '24h')));
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
  if (p.startsWith('/api/pvp')) {
    // Studio Battle polls a few times a second while a fight is on: its own, roomier limit
    if (!hit(ctx, 'pvp:' + uid, 420, 60000, now)) return bad(res, 429, 'rate');
    const pu = U.getUser(ctx, uid);
    if (!pu || pu.deleted) return bad(res, 404, 'nouser');
    if (method === 'GET' && p === '/api/pvp') return sendJson(res, 200, Object.assign(PvP.info(ctx, uid, now), { link: Eco.refLink(ctx, pu.ref_code).replace('start=ref_', 'start=duel_') }));
    if (method === 'POST' && p === '/api/pvp/find') {
      return sendJson(res, 200, PvP.find(ctx, uid, body, now, {
        onDuel: (bt, me, from) => {
          const r = bt.res && bt.res[me];
          if (r) Bot.notifyDuel(ctx, from, Studio.shown(pu), r.win > 0, r.my, r.op).catch(() => {});
        },
      }));
    }
    if (method === 'POST' && p === '/api/pvp/tick') return sendJson(res, 200, PvP.tick(ctx, uid, body, now));
    if (method === 'POST' && p === '/api/pvp/cancel') return sendJson(res, 200, PvP.cancel(ctx, uid));
    return bad(res, 404, 'route');
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
  if (method === 'GET' && p === '/api/market/bal') return sendJson(res, 200, await Market.balance(ctx, u0, now));
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
    const solOn = SolPay.enabled(cfg);
    return sendJson(res, 200, { ok: true, sol: solOn, vip: Eco.vipOf(ctx, uid), items: Eco.shopView(ctx, u0).map((it) => { const t = Bot.shopTxt(lang, it.sku, it.cash); return { ...it, sol: solOn ? SolPay.solPrice(cfg, it) : null, title: t[0], desc: t[1] }; }) });
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
  if (method === 'GET' && p === '/api/donate') return sendJson(res, 200, Object.assign({ ok: true, sol: SolPay.enabled(cfg), solMin: SolPay.enabled(cfg) ? SolPay.donateMinSol(cfg) : null, solPerStar: cfg.solPay.starUsd / SolPay.solUsd(cfg) }, Eco.fundView(ctx, uid, now)));
  if (method === 'POST' && p === '/api/donate/invoice') {
    const stars = Math.floor(Number(body.stars));
    if (!Number.isFinite(stars) || stars < cfg.donate.minStars || stars > cfg.donate.maxStars) return bad(res, 400, 'amount');
    const T = Bot.donateTxt(u0.lang, stars);
    const r = await ctx.tg.call('createInvoiceLink', {
      title: T[0], description: T[1], payload: `${uid}:donate:${stars}:${crypto.randomBytes(4).toString('hex')}`,
      provider_token: '', currency: 'XTR', prices: [{ label: T[0], amount: stars }],
    });
    if (!r.ok) return bad(res, 502, 'invoice');
    return sendJson(res, 200, { ok: true, url: r.result });
  }
  if (method === 'POST' && p === '/api/donate/sol') return sendJson(res, 200, SolPay.createDonation(ctx, uid, Number(body.sol), now));
  if (method === 'POST' && p === '/api/shop/sol') return sendJson(res, 200, SolPay.createOrder(ctx, uid, String(body.sku || ''), now));
  if (method === 'POST' && p === '/api/shop/sol/check') {
    const r = await SolPay.checkOrder(ctx, uid, String(body.id || ''), now);
    return sendJson(res, 200, Object.assign(r, { eco: Eco.ecoView(ctx, U.getUser(ctx, uid), now) }));
  }
  if (method === 'POST' && p === '/api/ads/claim') {
    if (cfg.ads.callbackKey) return sendJson(res, 200, { ok: false, err: 'ads_callback', eco: Eco.ecoView(ctx, u0, now) });
    const r = Eco.adReward(ctx, uid, 'client', now);
    return sendJson(res, 200, Object.assign(r, { eco: Eco.ecoView(ctx, U.getUser(ctx, uid), now) }));
  }
  if (method === 'POST' && p === '/api/sol') return handleSol(ctx, res, uid, body, now);
  if (method === 'POST' && p === '/api/airdrop/stars') {
    // the airdrop entry deposit paid in Telegram Stars
    const blk = Airdrop.entryBlock(ctx, uid);
    if (blk) return bad(res, 409, blk);
    const stars = Airdrop.entryStars(cfg);
    const T = Bot.entryTxt(u0.lang, stars);
    const r = await ctx.tg.call('createInvoiceLink', {
      title: T[0], description: T[1], payload: `${uid}:adentry:${stars}:${crypto.randomBytes(4).toString('hex')}`,
      provider_token: '', currency: 'XTR', prices: [{ label: T[0], amount: stars }],
    });
    if (!r.ok) return bad(res, 502, 'invoice');
    return sendJson(res, 200, { ok: true, url: r.result });
  }
  if (method === 'POST' && p === '/api/giveaway/check') {
    const Give = require('./giveaway.js');
    const g = Give.current(ctx);
    if (!g || g.status !== 'live') return sendJson(res, 200, { ok: false, err: 'unavailable' });
    if (!hit(ctx, 'gwsub:' + uid, 6, 60000, now)) return sendJson(res, 200, { ok: false, err: 'rate' });
    const sub = await Give.checkSub(ctx, g, uid, now);
    return sendJson(res, 200, { ok: true, sub, giveaway: Give.view(ctx, uid, now) });
  }
  if (method === 'POST' && p === '/api/airdrop/order') return sendJson(res, 200, SolPay.createAirdropOrder(ctx, uid, String(body.kind || ''), now));
  if (method === 'POST' && p === '/api/airdrop/claim') {
    if (!hit(ctx, 'adclaim:' + uid, 6)) return sendJson(res, 200, { ok: false, err: 'airdrop_wait' });
    const r = await Airdrop.claim(ctx, uid, now);
    return sendJson(res, 200, Object.assign(r, { eco: Eco.ecoView(ctx, U.getUser(ctx, uid), now) }));
  }
  if (method === 'POST' && p === '/api/studio') {
    if (body.check) return sendJson(res, 200, Studio.isFree(ctx, uid, body.name));
    if (!hit(ctx, 'studio:' + uid, 10, 3600000, now)) return bad(res, 429, 'rate');
    return sendJson(res, 200, Studio.setStudio(ctx, uid, body.name));
  }
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
      ctx.db.run("UPDATE users SET deleted=1, username=NULL, name='deleted', wallet=NULL, wallet_ok=0, sol=NULL WHERE id=?", uid);
    });
    return sendJson(res, 200, { ok: true });
  }
  return bad(res, 404, 'route');
}

/* Solana wallet for the token airdrop and the balance in the market card (an address, no signature) */
function handleSol(ctx, res, uid, body, now) {
  return ctx.db.tx(() => {
    const u = U.getUser(ctx, uid);
    if (body.address === null || body.disconnect) {
      u.sol = null; u.sol_at = now;
      Eco.persistUser(ctx, u);
      return sendJson(res, 200, { ok: true, eco: Eco.ecoView(ctx, u, now) });
    }
    const addr = String(body.address || '').trim();
    if (!Market.isSolAddr(addr)) return sendJson(res, 200, { ok: false, err: 'sol_address' });
    if (addr !== u.sol) {
      const others = ctx.db.all('SELECT id FROM users WHERE sol=? AND id!=? AND deleted=0', addr, uid);
      if (others.length) {
        AB.addHard(ctx, u, 15, 'wallet_dup', { sol: 1, dup: others.length }, now);
        for (const o of others) { const ou = U.getUser(ctx, o.id); AB.addHard(ctx, ou, 5, 'wallet_dup', { sol: 1, with: uid }, now); Eco.persistUser(ctx, ou); }
      }
    }
    u.sol = addr; u.sol_at = now;
    AB.evaluate(ctx, u, now);
    Eco.persistUser(ctx, u);
    return sendJson(res, 200, { ok: true, eco: Eco.ecoView(ctx, u, now) });
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
      // Solana JSON-RPC relay for the launch page (mainnet from the browser); read calls + sending already-signed transactions only
      if (req.method === 'POST' && p === '/api/solrpc') {
        if (!hit(ctx, 'solrpc:' + ip, 120)) return sendJson(res, 429, { jsonrpc: '2.0', id: null, error: { code: 429, message: 'rate' } });
        let j;
        try { j = JSON.parse(await readBody(req, 65536)); } catch { return sendJson(res, 400, { jsonrpc: '2.0', id: null, error: { code: -32700, message: 'parse' } }); }
        const OK = ['getBalance', 'getLatestBlockhash', 'getMinimumBalanceForRentExemption', 'sendTransaction', 'getSignatureStatuses', 'getAccountInfo', 'simulateTransaction', 'getTokenAccountsByOwner', 'getTokenAccountBalance', 'getTokenSupply', 'getMultipleAccounts', 'getFeeForMessage'];
        if (!j || Array.isArray(j) || !OK.includes(j.method)) return sendJson(res, 400, { jsonrpc: '2.0', id: (j && j.id) || null, error: { code: -32601, message: 'method not allowed' } });
        try {
          const r = await fetch(cfg.token.rpc || 'https://api.mainnet-beta.solana.com', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: j.id, method: j.method, params: j.params || [] }), signal: AbortSignal.timeout(15000) });
          res.writeHead(r.status, { 'content-type': 'application/json', ...SEC_HEADERS });
          return res.end(await r.text());
        } catch (e) { return sendJson(res, 502, { jsonrpc: '2.0', id: j.id, error: { code: 502, message: 'upstream' } }); }
      }
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
      // token metadata (the URI written into the OFFICE mint) and the launch page (the owner signs everything in Phantom)
      // public aggregate numbers (no personal data): verified players and the launch fund
      if (p === '/stats.json') {
        const f = Eco.fundView(ctx, 0);
        res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'public, max-age=60', 'access-control-allow-origin': '*', ...SEC_HEADERS });
        return res.end(JSON.stringify({ verified: Airdrop.verifiedCount(ctx), fundUsd: f.usd, goalUsd: f.goalUsd, solUsd: Math.round(require('./solpay.js').solUsd(cfg) * 100) / 100 }));
      }
      if (p === '/token.json') {
        const base = cfg.publicUrl || `http://${req.headers.host}`;
        const app = cfg.botUsername ? `https://t.me/${cfg.botUsername}${cfg.appShort ? '/' + cfg.appShort : ''}` : base;
        res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'public, max-age=300', 'access-control-allow-origin': '*', ...SEC_HEADERS });
        return res.end(JSON.stringify({ name: 'OFFICE', symbol: cfg.tokenSymbol || 'OFFX', description: 'OFFX powers Office Dreams, a neon pixel tycoon in Telegram: build your own game studio from a basement desk to a skyscraper, hire a team, ship hits and battle other studios. 90% of the supply goes to players by their progress; fixed 100,000,000 supply, minting disabled. Play: t.me/officedream1_bot/play', image: base + '/token.png', external_url: app,
          // Metaplex-style fields: some indexers look for the logo here
          properties: { category: 'image', files: [{ uri: base + '/token.png', type: 'image/png' }] },
          extensions: { website: base + '/site', telegram: app } }));
      }
      // the project's website (for people who find OFFX outside Telegram: Jupiter, DexScreener, X)
      if (p === '/site' || p === '/about') {
        const base = cfg.publicUrl || `https://${req.headers.host}`;
        const play = cfg.botUsername ? `https://t.me/${cfg.botUsername}${cfg.appShort ? '/' + cfg.appShort : ''}` : base;
        const mint = cfg.token && cfg.token.addr ? cfg.token.addr : '';
        const ch = cfg.channelUrl || (cfg.channel && /^@\w+$/.test(cfg.channel) ? 'https://t.me/' + cfg.channel.slice(1) : '');
        const esc = (v) => String(v).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
        const html = fs.readFileSync(path.join(cfg.root, 'public', 'site.html'), 'utf8')
          .replace(/\{\{PLAY\}\}/g, esc(play)).replace(/\{\{BASE\}\}/g, esc(base)).replace(/\{\{MINT\}\}/g, esc(mint))
          .replace(/\{\{BUY\}\}/g, esc(mint ? 'https://jup.ag/swap/SOL-' + mint : play))
          .replace(/\{\{CHART\}\}/g, esc(mint ? 'https://dexscreener.com/solana/' + mint : play))
          .replace('{{CHANNEL_BTN}}', ch ? `<a class="btn ghost" href="${esc(ch)}">📣 Channel</a>` : '')
          .replace('{{CHANNEL_SM}}', ch ? `<a class="btn ghost sm" href="${esc(ch)}">Channel</a>` : '');
        res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'public, max-age=300', ...SEC_HEADERS });
        return res.end(html);
      }
      if (p === '/pay') {
        const f = path.join(cfg.root, 'public', 'pay.html');
        res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache', ...SEC_HEADERS });
        return fs.createReadStream(f).pipe(res);
      }
      if (p === '/launch' || p === '/lp') {
        // owner tools: everything is signed in the owner's own wallet, the server never holds these keys
        const f = path.join(cfg.root, 'public', p === '/lp' ? 'lp.html' : 'launch.html');
        res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache', ...SEC_HEADERS });
        return fs.createReadStream(f).pipe(res);
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
