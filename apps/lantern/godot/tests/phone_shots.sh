#!/usr/bin/env bash
# Телефонный вид (портрет 412×892) + relay с облаком + боты: tests/phone_shots.sh <папка скриншотов>
SHOTS=${1:-/tmp/lantern-phone}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d)
PORT=${RELAY_PORT:-18955}
mkdir -p "$DATA/cloud"
PORT=$PORT CLOUD_DIR="$DATA/cloud" node "$HERE/../../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
node "$HERE/tests/social_seed.js" ws://127.0.0.1:$PORT/ws "$DATA/autotest-tag.txt" > "$DATA/seed.log" 2>&1 & S=$!
xvfb-run -a -s "-screen 0 1920x1080x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution 412x892 \
  --path "$HERE" -- --autotest phone --phone=1 --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws --shots="$SHOTS" > "$DATA/app.log" 2>&1
RC=$?
kill $S $R 2>/dev/null
grep -E "autotest|SCRIPT ERROR" "$DATA/app.log" | head -80
echo "data: $DATA exit=$RC"
exit $RC
