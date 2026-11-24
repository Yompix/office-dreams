'use strict';
/* Xray client config for the YURK Android app: the phone's whole traffic comes in through the system VPN (tun)
   and leaves through one of our nodes. The app excludes itself from the VPN, so Xray's own sockets go out directly. */

const PRIVATE = ['10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16', '127.0.0.0/8', '169.254.0.0/16', '100.64.0.0/10', 'fc00::/7', 'fe80::/10'];

// mode: 'ws' (Railway https domain, port 443), 'reality' (VPS, or Railway TCP proxy)
function outbound(u, n, mode) {
  const user = { id: u.uuid, encryption: 'none', level: 0 };
  if (n.kind === 'railway' && (mode !== 'reality' || !n.rport)) {
    return {
      tag: 'proxy', protocol: 'vless',
      settings: { vnext: [{ address: n.host, port: 443, users: [user] }] },
      streamSettings: { network: 'ws', security: 'tls', tlsSettings: { serverName: n.host, fingerprint: 'chrome', alpn: ['http/1.1'] }, wsSettings: { path: n.ws_path, headers: { Host: n.host } } },
    };
  }
  const host = n.kind === 'railway' ? n.rhost : n.host;
  const port = n.kind === 'railway' ? n.rport : n.port;
  return {
    tag: 'proxy', protocol: 'vless',
    settings: { vnext: [{ address: host, port, users: [{ ...user, flow: 'xtls-rprx-vision' }] }] },
    streamSettings: { network: 'tcp', security: 'reality', realitySettings: { serverName: n.sni, fingerprint: 'chrome', publicKey: n.pub, shortId: n.sid } },
  };
}

function config(u, n, mode = 'ws') {
  return {
    log: { loglevel: 'warning', access: 'none', dnsLog: false },
    dns: { servers: ['1.1.1.1', '8.8.8.8'], queryStrategy: 'UseIPv4' },
    inbounds: [{
      tag: 'tun', port: 0, protocol: 'tun',
      settings: { name: 'xray0', MTU: 1500, userLevel: 0 },
      sniffing: { enabled: true, destOverride: ['http', 'tls', 'quic'], routeOnly: true },
    }],
    outbounds: [outbound(u, n, mode), { tag: 'direct', protocol: 'freedom' }, { tag: 'block', protocol: 'blackhole' }],
    routing: {
      domainStrategy: 'AsIs',
      rules: [
        { type: 'field', ip: PRIVATE, outboundTag: 'direct' },
        { type: 'field', protocol: ['bittorrent'], outboundTag: 'block' },
      ],
    },
    policy: { levels: { 0: { handshake: 4, connIdle: 300, uplinkOnly: 1, downlinkOnly: 1 } } },
  };
}

module.exports = { config, outbound };
