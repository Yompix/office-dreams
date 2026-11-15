#!/usr/bin/env bash
# Ставит узел VPN на чистый Ubuntu/Debian (root): Xray (VLESS Reality, TCP 443) + Hysteria2 (UDP 443)
# и агент, который раз в минуту забирает с панели список пользователей.
#   bash i.sh https://ПАНЕЛЬ СЕКРЕТ_УЗЛА
set -euo pipefail

main() {
  local PANEL="${1:?usage: bash i.sh PANEL_URL NODE_SECRET}" SECRET="${2:?usage: bash i.sh PANEL_URL NODE_SECRET}"
  PANEL="${PANEL%/}"
  [ "$(id -u)" = 0 ] || { echo "Нужен root (sudo -i)"; exit 1; }
  export DEBIAN_FRONTEND=noninteractive

  echo "== пакеты"
  apt-get update -qq </dev/null
  apt-get install -y -qq curl openssl ca-certificates </dev/null >/dev/null

  echo "== проверка панели"
  curl -fsS --max-time 20 -H "Authorization: Bearer $SECRET" "$PANEL/node/xray.json" -o /dev/null \
    || { echo "Панель не отвечает или неверный секрет узла"; exit 1; }

  echo "== ускорение сети (BBR, буферы UDP)"
  cat > /etc/sysctl.d/90-vpn.conf <<'SYS'
net.core.default_qdisc=fq
net.ipv4.tcp_congestion_control=bbr
net.core.rmem_max=16777216
net.core.wmem_max=16777216
SYS
  sysctl --system >/dev/null 2>&1 || true

  echo "== Xray"
  bash -c "$(curl -fsSL https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" @ install </dev/null
  echo "== Hysteria2"
  bash <(curl -fsSL https://get.hy2.sh/) </dev/null

  mkdir -p /etc/hysteria
  if [ ! -s /etc/hysteria/cert.pem ]; then
    openssl req -x509 -nodes -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -days 3650 \
      -keyout /etc/hysteria/key.pem -out /etc/hysteria/cert.pem -subj "/CN=www.bing.com" 2>/dev/null
  fi
  if id hysteria >/dev/null 2>&1; then chown hysteria:hysteria /etc/hysteria/key.pem /etc/hysteria/cert.pem; else chmod 644 /etc/hysteria/key.pem; fi
  openssl x509 -noout -fingerprint -sha256 -in /etc/hysteria/cert.pem | cut -d= -f2 > /etc/hysteria/pin

  echo "== агент"
  ( umask 077; printf 'PANEL=%q\nSECRET=%q\n' "$PANEL" "$SECRET" > /etc/vpn-node.env )
  curl -fsSL "$PANEL/node/agent.sh" -o /usr/local/bin/vpn-node-agent
  chmod 755 /usr/local/bin/vpn-node-agent
  cat > /etc/systemd/system/vpn-node-agent.service <<'UNIT'
[Unit]
Description=VPN node agent (sync users from panel)
After=network-online.target
[Service]
Type=oneshot
ExecStart=/usr/local/bin/vpn-node-agent
UNIT
  cat > /etc/systemd/system/vpn-node-agent.timer <<'UNIT'
[Unit]
Description=Sync VPN users every minute
[Timer]
OnBootSec=20s
OnUnitActiveSec=60s
[Install]
WantedBy=timers.target
UNIT
  systemctl daemon-reload
  systemctl enable -q xray hysteria-server
  /usr/local/bin/vpn-node-agent --force
  systemctl enable -q --now vpn-node-agent.timer

  if command -v ufw >/dev/null && ufw status | grep -q active; then ufw allow 443/tcp >/dev/null; ufw allow 443/udp >/dev/null; fi

  echo
  systemctl is-active -q xray && echo "✅ Xray работает (TCP 443)" || echo "⚠️ Xray не запустился: journalctl -u xray -n 50"
  systemctl is-active -q hysteria-server && echo "✅ Hysteria2 работает (UDP 443)" || echo "⚠️ Hysteria2 не запустился: journalctl -u hysteria-server -n 50"
  echo "Готово. Проверьте узел в боте: /nodes"
}

main "$@"
