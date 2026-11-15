'use strict';
/* Configs the node agent downloads: Xray (VLESS Reality on TCP 443) and Hysteria2 (UDP 443). */

function xray(n, users) {
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

module.exports = { xray, hysteria };
