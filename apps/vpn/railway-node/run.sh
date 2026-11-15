#!/bin/sh
# Нужны переменные: PANEL_URL (лучше внутренний http://yurk-vpn.railway.internal:8080) и NODE_SECRET.
set -u
CFG=/tmp/config.json
NEW=/tmp/new.json
PID=""
STATS=1
get() { curl -fsS --max-time 20 -H "Authorization: Bearer $NODE_SECRET" "$PANEL_URL/node/xray.json$1" -o "$NEW"; }
ok() { /usr/local/bin/xray run -test -c "$NEW" >/dev/null 2>&1; }
# new config from the panel; if this Xray rejects the traffic counters, take the config without them
fetch() {
  get "" || return 1
  ok && { STATS=1; return 0; }
  echo "config with stats rejected, trying without"
  get "?stats=0" && ok && { STATS=0; return 0; }
  return 1
}
start() { /usr/local/bin/xray run -c "$CFG" & PID=$!; echo "xray started ($PID)"; }
# bytes per user since the last call (counters reset) -> panel
report() {
  [ "$STATS" = 1 ] || return 0
  /usr/local/bin/xray api statsquery --server=127.0.0.1:10085 -reset 2>/dev/null > /tmp/stats.json || return 0
  curl -fsS --max-time 20 -X POST -H "Authorization: Bearer $NODE_SECRET" --data-binary @/tmp/stats.json "$PANEL_URL/node/traffic" >/dev/null || true
}

until fetch; do echo "panel is not answering yet"; sleep 10; done
cp "$NEW" "$CFG"
start
while sleep 60; do
  report
  if fetch && ! cmp -s "$NEW" "$CFG"; then
    report
    cp "$NEW" "$CFG"
    kill "$PID" 2>/dev/null; wait "$PID" 2>/dev/null
    start
  fi
  kill -0 "$PID" 2>/dev/null || start
done
