#!/usr/bin/env bash
# Полный e2e LANTERN на Linux: relay (локально) + сервер (Java/Bedrock) + друг по коду + внешний клиент.
# tests/e2e.sh java|bedrock [папка данных] [папка скриншотов]
set -u
MODE=${1:-java}
DATA=${2:-/tmp/lantern-e2e-$MODE}
SHOTS=${3:-}
HERE=$(cd "$(dirname "$0")/.." && pwd)
RELAY_DIR=${RELAY_DIR:-$(cd "$HERE/../relay" 2>/dev/null || cd "$HERE/../../relay"; pwd)}
GODOT=${GODOT:-/home/user/tools/godot/godot}
PORT=${RELAY_PORT:-18930}
mkdir -p "$DATA"; rm -f "$DATA/autotest-ready.json" "$DATA/autotest-done.json"
PORT=$PORT node "$RELAY_DIR/server.js" > "$DATA/relay.log" 2>&1 & RELAY=$!
sleep 1
EXTRA=()
# В облачной песочнице нет IPv6, а BDS без него не стартует — подкладываем тестовую заглушку (только BDS, только тест)
if [ "$MODE" = bedrock ] && ! python3 -c "import socket; socket.socket(socket.AF_INET6)" 2>/dev/null; then
  mkdir -p /tmp/lantern-v6shim && gcc -shared -fPIC -O2 -o /tmp/lantern-v6shim/v6shim.so "$HERE/tests/v6shim.c" -ldl && export LANTERN_TEST_BDS_PRELOAD=/tmp/lantern-v6shim/v6shim.so
fi
[ "$MODE" = java ] && EXTRA+=(--mc=${MC:-latest} --crossplay=${CROSSPLAY:-1})
if [ -n "$SHOTS" ]; then
  RUN=(xvfb-run -a -s "-screen 0 1920x1080x24" env LIBGL_ALWAYS_SOFTWARE=1 "$GODOT" --rendering-driver opengl3 --resolution 1280x800)
else
  RUN=("$GODOT" --headless)
fi
"${RUN[@]}" --path "$HERE" -- --autotest "$MODE" --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws \
  --join_java_port=25570 --join_bedrock_port=19142 --shots="$SHOTS" "${EXTRA[@]}" > "$DATA/app.log" 2>&1 & APP=$!
NODE_PATH=${E2E_NODE_PATH:-/tmp/claude-0/bot/node_modules:/tmp/claude-0/bp/node_modules} node "$HERE/tests/e2e_client.js" "$DATA" "$MODE" > "$DATA/client.log" 2>&1 & CL=$!
wait $APP; RC=$?
# приложение упало до готовности — клиенту ждать нечего; сервер, оставшийся без приложения, гасим
[ -f "$DATA/autotest-ready.json" ] || kill $CL 2>/dev/null
pkill -f "$DATA/runtime/" 2>/dev/null
wait $CL; CRC=$?
kill $RELAY 2>/dev/null; wait $RELAY 2>/dev/null
echo "app exit=$RC client exit=$CRC"
exit $(( RC != 0 || CRC != 0 ))
