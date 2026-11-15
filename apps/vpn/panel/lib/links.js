'use strict';
/* Client links and the subscription body that Happ / Hiddify / v2rayNG / Streisand import. */

const label = (brand, n) => encodeURIComponent(`${brand} ${n.name}`);

// VLESS + Reality + Vision: looks like a normal TLS visit to n.sni
function vless(u, n, brand) {
  const p = new URLSearchParams({
    encryption: 'none', flow: 'xtls-rprx-vision', security: 'reality', sni: n.sni,
    fp: 'chrome', pbk: n.pub, sid: n.sid, type: 'tcp',
  });
  return `vless://${u.uuid}@${n.host}:${n.port}?${p}#${label(brand, n)}`;
}

// VLESS over WebSocket behind the Railway https domain (real certificate, port 443)
function vlessWs(u, n, brand) {
  const p = new URLSearchParams({ encryption: 'none', security: 'tls', sni: n.host, fp: 'chrome', type: 'ws', host: n.host, path: n.ws_path });
  return `vless://${u.uuid}@${n.host}:443?${p}#${label(brand, n)}`;
}

// Reality through a Railway TCP proxy (random public port)
const vlessRailwayReality = (u, n, brand) => vless(u, { ...n, host: n.rhost, port: n.rport }, brand).replace(/#.*$/, `#${label(brand, n)}%20R`);

// Hysteria2 (QUIC/UDP): fastest on lossy mobile networks; self-signed cert pinned by its SHA-256
function hy2(u, n, brand) {
  const p = new URLSearchParams({ sni: n.sni, insecure: '1' });
  if (n.pin) p.set('pinSHA256', n.pin);
  return `hysteria2://u${u.id}:${u.pass}@${n.host}:${n.hy2_port}/?${p}#${label(brand, n)}%20%E2%9A%A1`;
}

// a link that leads nowhere, only so the client shows "subscription ended" in its server list
const notice = (text) => `vless://00000000-0000-0000-0000-000000000000@127.0.0.1:1?encryption=none&type=tcp#${encodeURIComponent(text)}`;

const OVER = { ru: '📊 Лимит трафика исчерпан — продлите в боте', uk: '📊 Ліміт трафіку вичерпано — продовжте в боті', fa: '📊 حجم ترافیک تمام شد — در ربات تمدید کنید', tr: '📊 Trafik limiti doldu — botta yenileyin', zh: '📊 流量已用完 — 请在机器人中续费', es: '📊 Límite de tráfico agotado — renueva en el bot' };
const ENDED = { ru: '⛔ Подписка закончилась — продлите в боте', uk: '⛔ Підписка закінчилася — продовжте в боті', fa: '⛔ اشتراک تمام شد — در ربات تمدید کنید', tr: '⛔ Abonelik bitti — botta yenileyin', zh: '⛔ 订阅已到期 — 请在机器人中续费', es: '⛔ Suscripción terminada — renueva en el bot' };

function subscription(u, nodes, cfg, now = Date.now(), q = null) {
  const over = !!(q && q.over);
  const active = u.expires > now && !u.banned && !over;
  const lines = [];
  if (active) {
    for (const n of nodes) {
      if (n.kind === 'railway') { lines.push(vlessWs(u, n, cfg.brand)); if (n.rport && n.rhost) lines.push(vlessRailwayReality(u, n, cfg.brand)); }
      else lines.push(vless(u, n, cfg.brand));
    }
    for (const n of nodes) if (n.pin && n.kind !== 'railway') lines.push(hy2(u, n, cfg.brand));
  }
  if (over) lines.push(notice(OVER[u.lang] || '📊 Traffic limit reached — renew in the bot'));
  else if (!active) lines.push(notice(ENDED[u.lang] || '⛔ Subscription ended — renew in the bot'));
  else if (!lines.length) lines.push(notice('⏳ Servers are being set up'));
  const headers = {
    'content-type': 'text/plain; charset=utf-8',
    'cache-control': 'no-store',
    'profile-title': 'base64:' + Buffer.from(cfg.brand).toString('base64'),
    'profile-update-interval': '6',
    'subscription-userinfo': `upload=0; download=${q ? q.used : 0}; total=${q ? q.quota : 0}; expire=${Math.floor(u.expires / 1000)}`,
  };
  if (cfg.botUsername) {
    headers['support-url'] = `https://t.me/${cfg.botUsername}`;
    headers['profile-web-page-url'] = `https://t.me/${cfg.botUsername}`;
  }
  return { body: Buffer.from(lines.join('\n')).toString('base64'), headers, lines };
}

// one-tap import into the popular clients (Telegram buttons only allow https, so these live on the /sub page)
function appLinks(url, brand) {
  const e = encodeURIComponent(url);
  return [
    { app: 'Happ', os: 'Android · iPhone · Windows · Mac', href: `happ://add/${url}` },
    { app: 'Hiddify', os: 'Android · iPhone · Windows · Mac', href: `hiddify://import/${url}#${encodeURIComponent(brand)}` },
    { app: 'v2RayTun', os: 'Android · iPhone', href: `v2raytun://import/${url}` },
    { app: 'Streisand', os: 'iPhone · Mac', href: `streisand://import/${url}#${encodeURIComponent(brand)}` },
    { app: 'v2rayNG', os: 'Android', href: `v2rayng://install-sub?url=${e}&name=${encodeURIComponent(brand)}` },
  ];
}

module.exports = { vless, vlessWs, hy2, subscription, appLinks };
