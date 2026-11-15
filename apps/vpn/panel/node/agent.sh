#!/usr/bin/env bash
# Агент узла: берёт с панели настройки Xray и Hysteria2 и перезапускает службу, только если что-то поменялось.
set -euo pipefail
. /etc/vpn-node.env
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
PIN=$(cat /etc/hysteria/pin 2>/dev/null || true)
get() { curl -fsS --max-time 20 -H "Authorization: Bearer $SECRET" -H "X-Hy2-Pin: $PIN" "$PANEL/node/$1" -o "$T/$1"; }
get xray.json
get hysteria.yaml
# traffic per user since the last run -> panel (for the money report)
if /usr/local/bin/xray api statsquery --server=127.0.0.1:10085 -reset > "$T/stats.json" 2>/dev/null; then
  curl -fsS --max-time 20 -X POST -H "Authorization: Bearer $SECRET" --data-binary @"$T/stats.json" "$PANEL/node/traffic" >/dev/null || true
fi

XC=/usr/local/etc/xray/config.json
if [ "${1:-}" = "--force" ] || ! cmp -s "$T/xray.json" "$XC"; then
  if /usr/local/bin/xray run -test -c "$T/xray.json" >/dev/null 2>&1; then
    install -m 644 "$T/xray.json" "$XC"
    systemctl restart xray
  elif curl -fsS --max-time 20 -H "Authorization: Bearer $SECRET" "$PANEL/node/xray.json?stats=0" -o "$T/xray.json" \
       && /usr/local/bin/xray run -test -c "$T/xray.json" >/dev/null 2>&1; then
    install -m 644 "$T/xray.json" "$XC"   # this Xray has no stats API: run without traffic counters
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
