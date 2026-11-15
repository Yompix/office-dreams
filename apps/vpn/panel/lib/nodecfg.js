'use strict';
/* Configs the node agent downloads: Xray (VLESS Reality on TCP 443) and Hysteria2 (UDP 443). */

const RAILWAY_WS_PORT = 8080;   // Railway https domain -> this port (TLS ends at Railway's edge)
const RAILWAY_REALITY_PORT = 9443; // Railway TCP proxy -> this port

function realityInbound(n, users, port) {
  return {
    listen: '0.0.0.0', port, protocol: 'vless', tag: 'reality',
    settings: { clients: users.map((u) => ({ id: u.uuid, flow: 'xtls-rprx-vision', email: `u${u.id}` })), decryption: 'none' },
    streamSettings: {
      network: 'tcp', security: 'reality',
      realitySettings: { show: false, dest: `${n.sni}:443`, xver: 0, serverNames: [n.sni], privateKey: n.priv, shortIds: [n.sid] },
    },
    sniffing: { enabled: true, destOverride: ['http', 'tls', 'quic'] },
  };
}

// traffic counters per user (the agent reads them with `xray api statsquery` and sends them to the panel)
function withStats(c) {
  c.stats = {};
  c.api = { tag: 'api', listen: '127.0.0.1:10085', services: ['StatsService'] };
  c.policy = { levels: { 0: { statsUserUplink: true, statsUserDownlink: true } } };
  return c;
}

function xray(n, users, opts = {}) {
  const c = xrayBase(n, users);
  return opts.stats === false ? c : withStats(c);
}

function xrayBase(n, users) {
  const base = xrayVps(n, users);
  if (n.kind !== 'railway') return base;
  base.inbounds = [{
    listen: '0.0.0.0', port: RAILWAY_WS_PORT, protocol: 'vless', tag: 'ws',
    settings: { clients: users.map((u) => ({ id: u.uuid, email: `u${u.id}` })), decryption: 'none' },
    streamSettings: { network: 'ws', wsSettings: { path: n.ws_path } },
    sniffing: { enabled: true, destOverride: ['http', 'tls', 'quic'] },
  }];
  if (n.rport) base.inbounds.push(realityInbound(n, users, RAILWAY_REALITY_PORT));
  return base;
}

function xrayVps(n, users) {
  return {
    log: { loglevel: 'warning' },
    inbounds: [{
      listen: '0.0.0.0', port: n.port, protocol: 'vless', tag: 'reality',
      settings: { clients: users.map((u) => ({ id: u.uuid, flow: 'xtls-rprx-vision', email: `u${u.id}` })), decryption: 'none' },
      streamSettings: {
        network: 'tcp', security: 'reality',
        realitySettings: { show: false, dest: `${n.sni}:443`, xver: 0, serverNames: [n.sni], privateKey: n.priv, shortIds: [n.sid] },
      },
      sniffing: { enabled: true, destOverride: ['http', 'tls', 'quic'] },
    }],
    outbounds: [{ protocol: 'freedom', tag: 'direct' }, { protocol: 'blackhole', tag: 'block' }],
    routing: {
      rules: [
        // torrents bring abuse letters and get the server banned by the host
        { type: 'field', protocol: ['bittorrent'], outboundTag: 'block' },
        { type: 'field', ip: ['geoip:private'], outboundTag: 'block' },
      ],
    },
  };
}

// user names and passwords are [a-z0-9] only, so plain YAML is safe here
function hysteria(n, users) {
  const L = [
    `listen: :${n.hy2_port}`,
    'tls:',
    '  cert: /etc/hysteria/cert.pem',
    '  key: /etc/hysteria/key.pem',
    'auth:',
    '  type: userpass',
    '  userpass:',
  ];
  if (!users.length) L.push('    nobody: ' + 'x'.repeat(32)); // hysteria refuses an empty user list
  for (const u of users) L.push(`    u${u.id}: ${u.pass}`);
  L.push(
    'masquerade:',
    '  type: proxy',
    '  proxy:',
    `    url: https://${n.sni}/`,
    '    rewriteHost: true',
    '',
  );
  return L.join('\n');
}

module.exports = { xray, hysteria, RAILWAY_WS_PORT, RAILWAY_REALITY_PORT };
