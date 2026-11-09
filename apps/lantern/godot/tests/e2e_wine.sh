#!/usr/bin/env bash
# Нужен Wine 11 (WineHQ: пакеты wine-stable-amd64 + бинарники wine-stable в /opt/wine-stable). Wine 9 из Ubuntu падает
# на старте Godot 4.7 (VirtualProtect с NULL). Пример: tests/e2e_wine.sh java "C:/users/Иван Петров/LANTERN data" "$WINEPREFIX/drive_c/users/Иван Петров/LANTERN data" Z:/tmp/shots
# e2e Windows-сборки LANTERN под Wine 11: relay (Linux) + LANTERN.exe (wine) + внешний клиент (Linux)
# e2e_wine.sh java|bedrock <windows-data-dir> <linux path of same> [shots]
set -u
MODE=${1:-java}; WDATA=$2; LDATA=$3; SHOTS=${4:-}
HERE=/home/user/office-dreams/godot/playtogether
RELAY_DIR=/home/user/office-dreams/relay
EXE=${EXE:-$HERE/export/windows/LANTERN.exe}
PORT=${RELAY_PORT:-18960}
export LANG=C.UTF-8 LC_ALL=C.UTF-8 PATH=/opt/wine-stable/bin:$PATH WINEPREFIX=/tmp/claude-0/lantern2/wine11 WINEDEBUG=-all WINEDLLOVERRIDES="mscoree,mshtml="
mkdir -p "$LDATA"; rm -f "$LDATA/autotest-ready.json" "$LDATA/autotest-done.json"
PORT=$PORT node "$RELAY_DIR/server.js" > "$LDATA/relay.log" 2>&1 & RELAY=$!
sleep 1
EXTRA=()
[ "$MODE" = java ] && EXTRA+=(--mc=${MC:-latest} --crossplay=${CROSSPLAY:-1})
JTO="${JAVA_TOOL_OPTIONS//\/etc\/ssl\/certs\/java\/cacerts/Z:/etc/ssl/certs/java/cacerts}"
if [ -n "$SHOTS" ]; then
  RUN=(xvfb-run -a -s "-screen 0 1920x1080x24" wine "$EXE" --rendering-driver opengl3 --resolution 1280x800)
else
  RUN=(wine "$EXE" --headless)
fi
env SSL_CERT_FILE=Z:/root/.ccr/ca-bundle.crt JAVA_TOOL_OPTIONS="$JTO" "${RUN[@]}" -- --autotest "$MODE" --data="$WDATA" --relay=ws://127.0.0.1:$PORT/ws \
  --join_java_port=25570 --join_bedrock_port=19142 --shots="$SHOTS" "${EXTRA[@]}" > "$LDATA/app.log" 2>&1 & APP=$!
NODE_PATH=/tmp/claude-0/bot/node_modules:/tmp/claude-0/bp/node_modules node "$HERE/tests/e2e_client.js" "$LDATA" "$MODE" > "$LDATA/client.log" 2>&1 & CL=$!
wait $APP; RC=$?
wait $CL; CRC=$?
kill $RELAY 2>/dev/null; wait $RELAY 2>/dev/null
echo "app exit=$RC client exit=$CRC"
