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

// Hysteria2 (QUIC/UDP): fastest on lossy mobile networks; self-signed cert pinned by its SHA-256
function hy2(u, n, brand) {
  const p = new URLSearchParams({ sni: n.sni, insecure: '1' });
  if (n.pin) p.set('pinSHA256', n.pin);
  return `hysteria2://u${u.id}:${u.pass}@${n.host}:${n.hy2_port}/?${p}#${label(brand, n)}%20%E2%9A%A1`;
}

// a link that leads nowhere, only so the client shows "subscription ended" in its server list
const notice = (text) => `vless://00000000-0000-0000-0000-000000000000@127.0.0.1:1?encryption=none&type=tcp#${encodeURIComponent(text)}`;

function subscription(u, nodes, cfg, now = Date.now()) {
  const active = u.expires > now && !u.banned;
  const lines = [];
  if (active) {
    for (const n of nodes) lines.push(vless(u, n, cfg.brand));
    for (const n of nodes) if (n.pin) lines.push(hy2(u, n, cfg.brand));
  }
  if (!active) lines.push(notice(u.lang === 'ru' || u.lang === 'uk' ? '⛔ Подписка закончилась — продлите в боте' : '⛔ Subscription ended — renew in the bot'));
  else if (!lines.length) lines.push(notice('⏳ Servers are being set up'));
  const headers = {
    'content-type': 'text/plain; charset=utf-8',
    'cache-control': 'no-store',
    'profile-title': 'base64:' + Buffer.from(cfg.brand).toString('base64'),
    'profile-update-interval': '6',
    'subscription-userinfo': `upload=0; download=0; total=0; expire=${Math.floor(u.expires / 1000)}`,
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

module.exports = { vless, hy2, subscription, appLinks };
