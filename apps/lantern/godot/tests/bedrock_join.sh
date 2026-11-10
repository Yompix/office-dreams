#!/usr/bin/env bash
# Вход нового Bedrock (26.x) на сервер через LANTERN: «Серверы» → 127.0.0.1:<порт> = HTTP-вход (TCP, /v1/join)
# + WebRTC (NetherNet) и старый путь RakNet. Сервер — тестовый gophertunnel ($NNCLIENT srv) на 19132
# (как Geyser: TCP 19132 — HTTP, UDP 19132 — RakNet) → LANTERN «Мой мир» → relay → друг (тот же процесс, 19142)
# → клиент gophertunnel заходит обоими путями и появляется в мире.
# tests/bedrock_join.sh   (нужен NNCLIENT — сборка tools из /tmp/claude-0/nn/client с режимами srv/http/rak)
HERE=$(cd "$(dirname "$0")/.." && pwd)
NNCLIENT=${NNCLIENT:-nnclient}
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-bj.XXXXXX)
PORT=${RELAY_PORT:-$((18960 + RANDOM % 500))}
PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
"$NNCLIENT" srv 0.0.0.0:19132 > "$DATA/server.log" 2>&1 & S=$!
sleep 1
${GODOT:-/home/user/tools/godot/godot} --headless --path "$HERE" -- --autotest ownreal --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws \
  --join_bedrock_port=19142 --join_nn_port=17551 > "$DATA/app.log" 2>&1 & A=$!
until [ -f "$DATA/autotest-ready.json" ] || ! kill -0 $A 2>/dev/null; do sleep 0.5; done
H=false; K=false
if [ -f "$DATA/autotest-ready.json" ]; then
  timeout 60 "$NNCLIENT" http 127.0.0.1:19142 PhoneHttp > "$DATA/client_http.log" 2>&1 && H=true
  timeout 60 "$NNCLIENT" rak 127.0.0.1:19142 PhoneRak > "$DATA/client_rak.log" 2>&1 && K=true
fi
OK=false; $H && $K && OK=true
echo "{\"ok\":$OK,\"http\":$H,\"raknet\":$K}" > "$DATA/autotest-done.json"
wait $A; RC=$?
kill $R $S 2>/dev/null
grep -E "autotest|SCRIPT ERROR|ERROR: " "$DATA/app.log" | grep -v "скриншот" | cut -c1-300 | tail -15
echo "--- server:"; grep -E "PLAYER_SPAWNED|START_ERROR" "$DATA/server.log"
echo "--- client http:"; grep -E "RESULT|CONNECTED" "$DATA/client_http.log"
echo "--- client raknet:"; grep -E "RESULT|CONNECTED" "$DATA/client_rak.log"
echo "data: $DATA exit=$RC http=$H raknet=$K"
[ $RC = 0 ] && $OK
