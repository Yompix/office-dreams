#!/usr/bin/env bash
# 1.8 «нерабочих кнопок нет»: приложение открывает каждый экран, нажимает каждую видимую кнопку / переключатель / карточку
# и проверяет, что что-то произошло (переход, лист, сообщение, настройка, буфер обмена, команда серверу/relay…).
# tests/deadbuttons.sh [phone|pc] [доп. аргументы автотеста, напр. --only=home,settings --verbose=1]
MODE=${1:-pc}; shift
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-dead.XXXXXX)
PORT=${RELAY_PORT:-$((18200 + RANDOM % 500))}
mkdir -p "$DATA/cloud"
QUIET=1 PORT=$PORT CLOUD_DIR="$DATA/cloud" node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
node "$HERE/tests/social_seed.js" ws://127.0.0.1:$PORT/ws "$DATA/autotest-tag.txt" > "$DATA/seed.log" 2>&1 & S=$!
RES=1280x800; EXTRA=()
[ "$MODE" = phone ] && RES=393x873 && EXTRA=(--phone=1)
xvfb-run -a -s "-screen 0 1920x1080x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution $RES \
  --path "$HERE" -- --autotest deadbuttons "${EXTRA[@]}" --nosplash=1 --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws "$@" > "$DATA/app.log" 2>&1
RC=$?; grep -q "\[autotest\] EXIT 0" "$DATA/app.log" && RC=0 || RC=1
kill $S $R 2>/dev/null
grep -E "\[autotest\]|SCRIPT ERROR|at: " "$DATA/app.log" | grep -v "ок: " | head -150
echo "data: $DATA exit=$RC"
exit $RC
