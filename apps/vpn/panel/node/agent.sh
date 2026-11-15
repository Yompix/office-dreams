#!/usr/bin/env bash
# Агент узла: берёт с панели настройки Xray и Hysteria2 и перезапускает службу, только если что-то поменялось.
set -euo pipefail
. /etc/vpn-node.env
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
PIN=$(cat /etc/hysteria/pin 2>/dev/null || true)
get() { curl -fsS --max-time 20 -H "Authorization: Bearer $SECRET" -H "X-Hy2-Pin: $PIN" "$PANEL/node/$1" -o "$T/$1"; }
get xray.json
get hysteria.yaml

XC=/usr/local/etc/xray/config.json
if [ "${1:-}" = "--force" ] || ! cmp -s "$T/xray.json" "$XC"; then
  if /usr/local/bin/xray run -test -c "$T/xray.json" >/dev/null 2>&1; then
    install -m 644 "$T/xray.json" "$XC"
    systemctl restart xray
  else
    echo "xray config rejected, keeping the old one" >&2
  fi
fi

HC=/etc/hysteria/config.yaml
if [ "${1:-}" = "--force" ] || ! cmp -s "$T/hysteria.yaml" "$HC"; then
  install -m 644 "$T/hysteria.yaml" "$HC"
  systemctl restart hysteria-server
fi
