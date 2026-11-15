'use strict';
/* VPN panel: Telegram bot, subscription links, configs for nodes. No npm packages, Node >= 22.13. */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const Config = require('./lib/config.js');
const Store = require('./lib/store.js');
const Links = require('./lib/links.js');
const NodeCfg = require('./lib/nodecfg.js');
const Bot = require('./lib/bot.js');
const Tg = require('./lib/tg.js');
const WebApp = require('./lib/webapp.js');
const Money = require('./lib/money.js');
const Quota = require('./lib/quota.js');
const APP_HTML = path.join(__dirname, 'public', 'app.html');

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const same = (a, b) => { const x = Buffer.from(String(a)), y = Buffer.from(String(b)); return x.length === y.length && crypto.timingSafeEqual(x, y); };

// browsers get a page with one-tap buttons; VPN apps get the base64 list
const isBrowser = (req) => /mozilla/i.test(req.headers['user-agent'] || '') && /text\/html/.test(req.headers.accept || '');

function subPage(cfg, u, url, now) {
  const ru = ['ru', 'uk', 'be', 'kk'].includes(u.lang);
  const active = u.expires > now;
  const till = Bot.fmtDate(u.expires);
  const apps = Links.appLinks(url, cfg.brand).map((a) => `<a class="app" href="${esc(a.href)}"><b>${esc(a.app)}</b><span>${esc(a.os)}</span></a>`).join('');
  return `<!doctype html><html lang="${ru ? 'ru' : 'en'}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(cfg.brand)}</title><meta name="robots" content="noindex">
<style>:root{color-scheme:dark}body{margin:0;font:16px/1.45 system-ui,sans-serif;background:#0b0f17;color:#e8edf5}main{max-width:460px;margin:0 auto;padding:24px 16px}
h1{font-size:24px;margin:0 0 4px}.st{margin:0 0 20px;color:${active ? '#5be49b' : '#ff7a7a'}}.app{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:14px 16px;margin:8px 0;border-radius:12px;background:#151c2a;color:inherit;text-decoration:none;border:1px solid #233048}
.app span{color:#8a97ad;font-size:13px;text-align:right}.app:active{background:#1d2738}code{display:block;word-break:break-all;padding:12px;border-radius:10px;background:#151c2a;font-size:13px}
button{width:100%;margin:10px 0;padding:14px;border:0;border-radius:12px;background:#3b82f6;color:#fff;font:600 16px system-ui}p.s{color:#8a97ad;font-size:14px}</style></head>
<body><main><h1>🛡 ${esc(cfg.brand)}</h1><p class="st">${active ? (ru ? '✅ Активна до ' : '✅ Active until ') + till : (ru ? '❌ Подписка закончилась — продлите в боте' : '❌ Subscription ended — renew in the bot')}</p>
<p>${ru ? 'Установите приложение и нажмите на него — подписка добавится сама:' : 'Install an app and tap it — the subscription is added automatically:'}</p>${apps}
<p class="s">${ru ? 'Или скопируйте ссылку и добавьте её в приложении как подписку:' : 'Or copy the link and add it in your app as a subscription:'}</p><code id="u">${esc(url)}</code>
<button onclick="navigator.clipboard.writeText(document.getElementById('u').textContent).then(()=>this.textContent='✓')">${ru ? 'Копировать ссылку' : 'Copy link'}</button>
<p class="s">${ru ? 'Не делитесь ссылкой — по ней подключается любой.' : "Don't share this link — anyone can connect with it."}</p></main></body></html>`;
}

function createApp({ cfg, store, bot, webapp, log = console }) {
  return async function app(req, res) {
    const url = new URL(req.url, 'http://x');
    const p = url.pathname;
    const send = (code, body, headers = {}) => { res.writeHead(code, { 'content-type': 'text/plain; charset=utf-8', ...headers }); res.end(body); };
    try {
      if (p === '/health') return send(200, 'ok');

      if (p === '/app') return send(200, fs.readFileSync(APP_HTML), { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache' });
      let a = /^\/api\/(me|trial|pay|reset)$/.exec(p);
      if (a && req.method === 'POST' && webapp) {
        let body = {};
        try { body = JSON.parse((await readBody(req, 1e4)) || '{}'); } catch { /* empty */ }
        const [code, out] = await webapp.api(a[1], String(req.headers['x-init-data'] || ''), body);
        return send(code, JSON.stringify(out), { 'content-type': 'application/json', 'cache-control': 'no-store' });
      }

      if (p === '/tg/hook' && req.method === 'POST') {
        if (!cfg.hookSecret || !same(req.headers['x-telegram-bot-api-secret-token'] || '', cfg.hookSecret)) return send(403, 'no');
        const body = await readBody(req, 1e6);
        send(200, 'ok');
        let upd; try { upd = JSON.parse(body); } catch { return; }
        return bot.handle(upd);
      }

      let m = /^\/sub\/([A-Za-z0-9_-]{16,64})$/.exec(p);
      if (m && req.method === 'GET') {
        const u = store.userByToken(m[1]);
        if (!u) return send(404, 'not found');
        const now = Date.now();
        const full = `${cfg.publicUrl}/sub/${u.token}`;
        if (isBrowser(req)) return send(200, subPage(cfg, u, full, now), { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
        const s = Links.subscription(u, store.enabledNodes(), cfg, now, Quota.usage(store, cfg, u, now));
        return send(200, s.body, s.headers);
      }

      // node agent: Bearer <node secret>; it also reports its Hysteria2 certificate fingerprint
      m = /^\/node\/(xray\.json|hysteria\.yaml)$/.exec(p);
      if (m) {
        const tok = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
        const n = tok && store.nodeBySecret(tok);
        if (!n) return send(403, 'no');
        const pin = String(req.headers['x-hy2-pin'] || '').trim();
        store.touchNode(n.id, /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/i.test(pin) ? pin.toUpperCase() : '');
        const users = n.enabled ? Quota.allowedUsers(store, cfg) : [];
        if (m[1] === 'xray.json') return send(200, JSON.stringify(NodeCfg.xray(n, users, { stats: url.searchParams.get('stats') !== '0' }), null, 1), { 'content-type': 'application/json' });
        return send(200, NodeCfg.hysteria(n, users), { 'content-type': 'text/yaml' });
      }
      // node agent reports `xray api statsquery -reset` output: bytes per user since the last report
      if (p === '/node/traffic' && req.method === 'POST') {
        const tok = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
        if (!tok || !store.nodeBySecret(tok)) return send(403, 'no');
        const n = saveTraffic(store, await readBody(req, 2e6));
        return send(200, String(n));
      }
      m = /^\/node\/(install\.sh|agent\.sh)$/.exec(p);
      if (m) return send(200, fs.readFileSync(path.join(__dirname, 'node', m[1])), { 'content-type': 'text/x-shellscript' });

      if (p === '/admin/stats') {
        if (!cfg.adminKey || !same(url.searchParams.get('key') || req.headers['x-admin-key'] || '', cfg.adminKey)) return send(403, 'no');
        const nodes = store.nodes().map((n) => ({ id: n.id, name: n.name, host: n.host, enabled: !!n.enabled, seen: n.seen, hy2: !!n.pin }));
        return send(200, JSON.stringify({ ...store.stats(), nodeList: nodes }, null, 1), { 'content-type': 'application/json' });
      }

      if (p === '/') return send(200, cfg.botUsername ? `${cfg.brand}: https://t.me/${cfg.botUsername}` : cfg.brand);
      return send(404, 'not found');
    } catch (e) {
      log.error('http:', e.message);
      if (!res.headersSent) send(500, 'error');
    }
  };
}

// {"stat":[{"name":"user>>>u123>>>traffic>>>uplink","value":"456"}]} -> traffic table
function saveTraffic(store, body, now = Date.now()) {
  let j; try { j = JSON.parse(body || '{}'); } catch { return 0; }
  const per = new Map();
  for (const s of j.stat || []) {
    const mm = /^user>>>u(\d+)>>>traffic>>>(uplink|downlink)$/.exec(s.name || '');
    const v = Number(s.value) || 0;
    if (!mm || v <= 0) continue;
    const o = per.get(mm[1]) || { up: 0, down: 0 };
    o[mm[2] === 'uplink' ? 'up' : 'down'] += v;
    per.set(mm[1], o);
  }
  const day = new Date(now).toISOString().slice(0, 10);
  for (const [uid, o] of per) store.addTraffic(day, Number(uid), o.up, o.down);
  return per.size;
}

// daily money report to admins at 06:xx UTC (09:xx Moscow / Kyiv summer)
async function dailyMoney(cfg, store, tg, now = Date.now()) {
  const d = new Date(now);
  const today = d.toISOString().slice(0, 10);
  if (d.getUTCHours() < 6 || store.kvGet('moneyDay') === today) return false;
  store.kvSet('moneyDay', today);
  const txt = Money.text(Money.report(store, cfg, now));
  for (const a of cfg.adminIds) await tg.call('sendMessage', { chat_id: a, text: txt, parse_mode: 'HTML' }).catch(() => {});
  return true;
}

function readBody(req, max) {
  return new Promise((resolve, reject) => {
    let n = 0; const parts = [];
    req.on('data', (c) => { n += c.length; if (n > max) { reject(new Error('too big')); req.destroy(); } else parts.push(c); });
    req.on('end', () => resolve(Buffer.concat(parts).toString('utf8')));
    req.on('error', reject);
  });
}

async function startBot(cfg, tg, bot, log) {
  await tg.call('setMyCommands', { commands: [
    { command: 'start', description: 'Меню' }, { command: 'key', description: 'Мой ключ' },
    { command: 'buy', description: 'Купить / продлить' }, { command: 'help', description: 'Как подключить' },
  ] }).catch((e) => log.error(e.message));
  if (cfg.publicUrl.startsWith('https://')) {
    await tg.call('setChatMenuButton', { menu_button: { type: 'web_app', text: cfg.brand, web_app: { url: `${cfg.publicUrl}/app` } } }).catch((e) => log.error(e.message));
  }
  const updates = ['message', 'callback_query', 'pre_checkout_query'];
  if (cfg.publicUrl.startsWith('https://')) {
    await tg.call('setWebhook', { url: `${cfg.publicUrl}/tg/hook`, secret_token: cfg.hookSecret, allowed_updates: updates, drop_pending_updates: false });
    log.log('bot: webhook', `${cfg.publicUrl}/tg/hook`);
    return;
  }
  // no public https address (running on a PC): long polling
  await tg.call('deleteWebhook', {});
  log.log('bot: long polling');
  let offset = 0;
  for (;;) {
    try {
      const list = await tg.call('getUpdates', { offset, timeout: 50, allowed_updates: updates });
      for (const u of list) { offset = u.update_id + 1; await bot.handle(u); }
    } catch (e) { log.error('poll:', e.message); await new Promise((r) => setTimeout(r, 3000)); }
  }
}

if (require.main === module) {
  const cfg = Config.load();
  const store = Store.open(cfg.dbPath);
  for (const rn of cfg.railwayNodes) { const n = store.railwayNode(rn); console.log(`railway node #${n.id} ${n.name} ${n.host}${n.rport ? ' + ' + n.rhost + ':' + n.rport : ''}`); }
  const tg = cfg.botToken ? Tg.client(cfg.botToken) : { call: async () => { throw new Error('BOT_TOKEN is not set'); } };
  const bot = Bot.create({ cfg, store, tg });
  const webapp = WebApp.create({ cfg, store, bot, tg });
  http.createServer(createApp({ cfg, store, bot, webapp })).listen(cfg.port, () => console.log(`${cfg.brand} panel on :${cfg.port}, db ${cfg.dbPath}`));
  if (cfg.botToken) startBot(cfg, tg, bot, console).catch((e) => console.error('bot start:', e.message));
  else console.warn('BOT_TOKEN is not set: the bot is off');
  setInterval(() => bot.remind().catch(() => {}), 3600e3).unref();
  setInterval(() => bot.quotaWarn().catch((e) => console.error('quota:', e.message)), 600e3).unref();
  setInterval(() => dailyMoney(cfg, store, tg).catch((e) => console.error('money:', e.message)), 600e3).unref();
  if (cfg.cryptoToken) setInterval(() => webapp.pollCrypto().catch((e) => console.error(e.message)), 60e3).unref();
}

module.exports = { createApp, subPage, saveTraffic, dailyMoney };
