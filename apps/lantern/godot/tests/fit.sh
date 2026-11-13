#!/usr/bin/env bash
# 1.8.2: «всё помещается» — каждый экран, лист и состояние на каждом языке и на каждом размере экрана
# (телефоны 720×1600 @2, 1080×2400 @2.75, 1080×2340 @3, маленький 480×854 @1.5; ПК 1280×800 и 1920×1080).
# Проверка — по настоящим прямоугольникам (tests/fit_check.gd): вылезает за край окна/родителя, оболочка шире окна,
# обрезанные подписи, непереведённое, подсветка меню, маленькие кнопки (предупреждение).
#   tests/fit.sh                                — все размеры, все языки
#   SIZES="1080x2400@2.75" tests/fit.sh --langs=uk --only=quests,settings --shots=/tmp/fit --shotlang=uk
SIZES=${SIZES:-"720x1600@2.0 1080x2400@2.75 1080x2340@3.0 480x854@1.5 1280x800 1920x1080"}
HERE=$(cd "$(dirname "$0")/.." && pwd)
RC=0
for S in $SIZES; do
  RES=${S%@*}; DEN=""; [ "$RES" != "$S" ] && DEN=${S#*@}
  W=${RES%x*}; H=${RES#*x}
  DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-fit.XXXXXX)
  PORT=${RELAY_PORT:-$((18400 + RANDOM % 400))}
  mkdir -p "$DATA/cloud"
  QUIET=1 PORT=$PORT CLOUD_DIR="$DATA/cloud" node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
  sleep 1
  node "$HERE/tests/social_seed.js" ws://127.0.0.1:$PORT/ws "$DATA/autotest-tag.txt" > "$DATA/seed.log" 2>&1 & SD=$!
  EXTRA=()
  [ -n "$DEN" ] && EXTRA=(--phone=1 --density=$DEN --safe=28,16)
  xvfb-run -a -s "-screen 0 $((W+64))x$((H+64))x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 \
    --resolution ${W}x${H} --path "$HERE" -- --autotest fit "${EXTRA[@]}" --nosplash=1 --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws "$@" > "$DATA/app.log" 2>&1
  grep -q "\[autotest\] EXIT 0" "$DATA/app.log" || RC=1
  kill $SD $R 2>/dev/null
  echo "=== $S  (data: $DATA)"
  grep -E "FIT ИТОГ|FIT!|FIT\?|SCRIPT ERROR|USER SCRIPT ERROR|ERROR: .*(Invalid|null|Nil)|at: |\[autotest\] (EXIT|FAIL|не дождались)" "$DATA/app.log" | head -${FIT_LINES:-120}
done
exit $RC
