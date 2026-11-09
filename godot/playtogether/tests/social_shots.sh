#!/usr/bin/env bash
# Скриншоты соцчасти: relay + боты + приложение (xvfb). tests/social_shots.sh <папка скриншотов>
SHOTS=${1:-/tmp/lantern-shots}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d)
PORT=${RELAY_PORT:-18950}
PORT=$PORT node "$HERE/../../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
node "$HERE/tests/social_seed.js" ws://127.0.0.1:$PORT/ws "$DATA/autotest-tag.txt" > "$DATA/seed.log" 2>&1 & S=$!
xvfb-run -a -s "-screen 0 1920x1080x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution 1280x800 \
  --path "$HERE" -- --autotest social --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws --shots="$SHOTS" > "$DATA/app.log" 2>&1
RC=$?
kill $S $R 2>/dev/null
grep -E "autotest|SCRIPT ERROR" "$DATA/app.log" | head -60
echo "data: $DATA exit=$RC"
exit $RC
