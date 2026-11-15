#!/bin/sh
# Нужны переменные: PANEL_URL (лучше внутренний http://yurk-vpn.railway.internal:8080) и NODE_SECRET.
set -u
CFG=/tmp/config.json
NEW=/tmp/new.json
PID=""
fetch() { curl -fsS --max-time 20 -H "Authorization: Bearer $NODE_SECRET" "$PANEL_URL/node/xray.json" -o "$NEW"; }
start() { /usr/local/bin/xray run -c "$CFG" & PID=$!; echo "xray started ($PID)"; }

until fetch; do echo "panel is not answering yet"; sleep 10; done
cp "$NEW" "$CFG"
start
while sleep 60; do
  if fetch && ! cmp -s "$NEW" "$CFG"; then
    if /usr/local/bin/xray run -test -c "$NEW" >/dev/null 2>&1; then
      cp "$NEW" "$CFG"
      kill "$PID" 2>/dev/null; wait "$PID" 2>/dev/null
      start
    else
      echo "new config rejected, keeping the old one"
    fi
  fi
  kill -0 "$PID" 2>/dev/null || start
done
