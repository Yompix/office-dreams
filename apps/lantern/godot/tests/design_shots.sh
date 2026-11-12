#!/usr/bin/env bash
# Дизайн-скриншоты всех экранов и сценариев: relay + боты + приложение (xvfb).
# tests/design_shots.sh <папка> <ширина> <высота> [плотность] [доп. аргументы]
#   телефон 1080×2400 @2.75:  tests/design_shots.sh /tmp/shots 1080 2400 2.75
#   ПК 1280×800:               tests/design_shots.sh /tmp/shots 1280 800
SHOTS=${1:-/tmp/lantern-design}; W=${2:-1080}; H=${3:-2400}; DEN=${4:-}; shift 4 2>/dev/null
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}")
PORT=${RELAY_PORT:-$((18960 + RANDOM % 500))}
mkdir -p "$DATA/cloud"
PORT=$PORT CLOUD_DIR="$DATA/cloud" node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
node "$HERE/tests/social_seed.js" ws://127.0.0.1:$PORT/ws "$DATA/autotest-tag.txt" > "$DATA/seed.log" 2>&1 & S=$!
EXTRA=()
[ -n "$DEN" ] && EXTRA=(--phone=1 --density=$DEN --safe=28,16)
xvfb-run -a -s "-screen 0 $((W+64))x$((H+64))x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution ${W}x${H} \
  --path "$HERE" -- --autotest tour "${EXTRA[@]}" "$@" --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws --shots="$SHOTS" > "$DATA/app.log" 2>&1
RC=$?
kill $S $R 2>/dev/null
grep -E "autotest|SCRIPT ERROR|ERROR: " "$DATA/app.log" | grep -v "скриншот" | grep -v "\] ок: " | head -150
echo "data: $DATA exit=$RC"
exit $RC
