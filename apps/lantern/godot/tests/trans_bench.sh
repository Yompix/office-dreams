#!/usr/bin/env bash
# 2.0.1: замер плавности переходов (время кадров, цена go(), рывки свойств) → JSON и таблица в журнале.
#   tests/trans_bench.sh <out.json> [ширина высота плотность]
OUT=${1:-/tmp/lantern-trans.json}; W=${2:-720}; H=${3:-1600}; DEN=${4:-2.0}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-tb.XXXXXX)
PORT=$((18500 + RANDOM % 300))
QUIET=1 PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
PH="--phone=1 --density=$DEN --safe=28,16"; [ "${PC:-0}" = 1 ] && PH=""
# HEADLESS=1 — без отрисовки: время кадра = работа самого приложения (постройка экранов, твины, раскладка,
# текст), без шума программного GL. Это эталон для «рывков»; с xvfb — то, что видно на экране.
if [ "${HEADLESS:-0}" = 1 ]; then RUN="nice -n ${NICE:--10} ${GODOT:-/home/user/tools/godot/godot} --headless"
else RUN="nice -n ${NICE:--10} xvfb-run -a -s \"-screen 0 $((W+64))x$((H+64))x24\" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3"; fi
eval $RUN \
  --resolution ${W}x${H} --path "$HERE" -- --autotest transbench --anim=1 $PH ${TB_ARGS:-} \
  --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws --out="$OUT" > "$DATA/app.log" 2>&1
kill $R 2>/dev/null
grep "\[autotest\] TB\|SCRIPT ERROR" "$DATA/app.log"
rm -rf "$DATA"
