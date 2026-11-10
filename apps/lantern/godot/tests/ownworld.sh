#!/usr/bin/env bash
# «Мой мир из Minecraft»: поддельный мир Bedrock (RakNet-понг + эхо) и Java («Открыть для сети» + TCP-эхо)
# → LANTERN находит мир → комната на relay → друг (тот же процесс) получает данные через relay.
# tests/ownworld.sh [папка скриншотов] [phone]   (phone — портрет 412×892, только Bedrock)
SHOTS=${1:-}
PHONE=${2:-}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-own.XXXXXX)
PORT=${RELAY_PORT:-$((18960 + RANDOM % 500))}
PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
node "$HERE/tests/fake_world.js" "$DATA" 19132 25599 > "$DATA/world.log" 2>&1 & W=$!
sleep 1
EXTRA=()
RES=1280x800
[ -n "$PHONE" ] && EXTRA=(--phone=1) && RES=412x892
if [ -n "$SHOTS" ]; then
  RUN=(xvfb-run -a -s "-screen 0 1920x1080x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution $RES)
else
  RUN=(${GODOT:-/home/user/tools/godot/godot} --headless)
fi
"${RUN[@]}" --path "$HERE" -- --autotest ownworld "${EXTRA[@]}" --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws \
  --join_java_port=25570 --join_bedrock_port=19142 --shots="$SHOTS" > "$DATA/app.log" 2>&1
RC=$?
kill $W $R 2>/dev/null
grep -E "autotest|SCRIPT ERROR|ERROR: " "$DATA/app.log" | grep -v "скриншот" | head -80
echo "--- world:"; tail -5 "$DATA/world.log"
echo "data: $DATA exit=$RC"
exit $RC
