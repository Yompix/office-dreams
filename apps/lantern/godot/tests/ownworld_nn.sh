#!/usr/bin/env bash
# «Мой мир» на новом Bedrock (NetherNet, UDP 7551): настоящий BDS с transport=nethernet и enable-lan-visibility=true
# уже запущен на этом компьютере → LANTERN находит мир → комната → друг (тот же процесс, поиск на JOIN_NN_PORT)
# → клиент NetherNet (gophertunnel, $NNCLIENT join 127.0.0.1:JOIN_NN_PORT) заходит в мир через relay.
# tests/ownworld_nn.sh [папка скриншотов]
SHOTS=${1:-}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-nn.XXXXXX)
PORT=${RELAY_PORT:-$((18960 + RANDOM % 500))}
JNN=${JOIN_NN_PORT:-17551}
PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
if [ -n "$SHOTS" ]; then
  RUN=(xvfb-run -a -s "-screen 0 1920x1080x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution 412x892)
  EXTRA=(--phone=1)
else
  RUN=(${GODOT:-/home/user/tools/godot/godot} --headless)
  EXTRA=()
fi
"${RUN[@]}" --path "$HERE" -- --autotest ownreal "${EXTRA[@]}" --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws \
  --join_bedrock_port=19142 --join_nn_port=$JNN --shots="$SHOTS" > "$DATA/app.log" 2>&1 & A=$!
until [ -f "$DATA/autotest-ready.json" ] || ! kill -0 $A 2>/dev/null; do sleep 0.5; done
OK=false
if [ -f "$DATA/autotest-ready.json" ]; then
  timeout 60 ${NNCLIENT:-nnclient} join 127.0.0.1:$JNN LanternNN > "$DATA/client.log" 2>&1 && OK=true
fi
echo "{\"ok\":$OK}" > "$DATA/autotest-done.json"
wait $A; RC=$?
kill $R 2>/dev/null
grep -E "autotest|SCRIPT ERROR|ERROR: " "$DATA/app.log" | grep -v "скриншот" | cut -c1-400 | head -40
echo "--- client:"; grep -v mDNS "$DATA/client.log" | tail -5
echo "data: $DATA exit=$RC"
exit $RC
