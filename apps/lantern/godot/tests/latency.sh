#!/usr/bin/env bash
# Замер задержки, которую добавляет LANTERN: эхо-сервер ↔ мост хозяина ↔ локальный relay ↔ мост друга ↔ клиент.
# 1000 кругов маленьких TCP-пакетов и UDP-датаграмм: «добавлено» = через мост − напрямую (p50/p95/p99, мс),
# потом скорость (МБ/с, как загрузка чанков при входе). Цель на localhost: p50 < 2 мс, p99 < 10 мс, ≥ 5 МБ/с.
# tests/latency.sh [N=1000] [МБ=40]     LAN=1 — проверить «Прямое соединение по Wi-Fi» (в обход relay)
N=${1:-1000}; MB=${2:-40}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-lat.XXXXXX)
PORT=${RELAY_PORT:-$((19500 + RANDOM % 400))}
ET=$((PORT + 1000)); EU=$((PORT + 1001)); JT=$((PORT + 1002)); JU=$((PORT + 1003))
QUIET=1 PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
node "$HERE/tests/echo_server.js" $ET $EU > "$DATA/echo.log" 2>&1 & E=$!
sleep 1
${GODOT:-/home/user/tools/godot/godot} --headless --path "$HERE" -- --autotest bridge --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws \
  --echo_tcp=$ET --echo_udp=$EU --join_java_port=$JT --join_bedrock_port=$JU --lan=${LAN:-0} > "$DATA/app.log" 2>&1 & A=$!
for i in $(seq 1 100); do [ -f "$DATA/autotest-ready.json" ] && break; sleep 0.2; done
if [ ! -f "$DATA/autotest-ready.json" ]; then echo "мост не поднялся"; tail -20 "$DATA/app.log"; kill $A $E $R 2>/dev/null; exit 1; fi
node "$HERE/tests/latency_client.js" $JT $JU $ET $EU $N $MB | tee "$DATA/result.json"; RC=${PIPESTATUS[0]}
touch "$DATA/autotest-done.json"
wait $A
grep -E "\[autotest\] (мост|LAN)" "$DATA/app.log" | head -5
kill $E $R 2>/dev/null
if [ -n "$KEEP" ]; then echo "data: $DATA"; else rm -f "$DATA/relay.log" "$DATA/echo.log" "$DATA/app.log" "$DATA/result.json" "$DATA/autotest-ready.json" "$DATA/autotest-done.json"; fi
exit $RC
