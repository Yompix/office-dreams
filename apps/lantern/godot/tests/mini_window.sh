#!/usr/bin/env bash
# 1.7: окошко LANTERN поверх игры — НАСТОЯЩЕЕ окно системы, а не окно внутри LANTERN.
# Xvfb + «игра» (окно xev поверх LANTERN) + LANTERN (--autotest mini). Проверяем через xwininfo/xprop:
#   1) у окошка свой X-идентификатор, оно — отдельное окно верхнего уровня (родитель — корень), а не часть главного;
#   2) оно «поверх всех» (_NET_WM_STATE_ABOVE или override-redirect), нет рамки, нет WM_TRANSIENT_FOR (не привязано к главному окну);
#   3) после сворачивания главного окна окошко остаётся видимым (IsViewable).
# tests/mini_window.sh [папка скриншотов]      нужны: xvfb-run, x11-utils (xwininfo, xprop, xev)
SHOTS=${1:-}
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-mini.XXXXXX)
PORT=${RELAY_PORT:-$((18960 + RANDOM % 500))}
PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
export HERE DATA PORT SHOTS
xvfb-run -a -s "-screen 0 1920x1080x24" bash -c '
  env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 --resolution 1280x800 --position 100,100 \
    --path "$HERE" -- --autotest mini --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws --shots="$SHOTS" > "$DATA/app.log" 2>&1 & A=$!
  until [ -f "$DATA/mini-1.json" ] || ! kill -0 $A 2>/dev/null; do sleep 0.3; done
  xev -geometry 1600x900+50+50 > /dev/null 2>&1 & G=$!     # «игра» поверх LANTERN
  sleep 1
  OK=true
  if [ -f "$DATA/mini-1.json" ]; then
    MINI=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))[\"native\"])" "$DATA/mini-1.json")
    MAIN=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))[\"main_native\"])" "$DATA/mini-1.json")
    echo "окна X11: главное=$MAIN окошко=$MINI"
    [ "$MINI" != "0" ] && [ "$MINI" != "$MAIN" ] || { echo "ОШИБКА: у окошка нет своего окна"; OK=false; }
    echo "--- дерево окон верхнего уровня:"; xwininfo -root -children | grep -E "LANTERN|Event Tester"
    xwininfo -root -children | grep -q "$(printf "0x%x" $MINI)" || { echo "ОШИБКА: окошко не окно верхнего уровня"; OK=false; }
    echo "--- окошко:"; xwininfo -id $MINI | grep -E "Absolute|Width|Height|Map State|Override"
    xprop -id $MINI _NET_WM_STATE _MOTIF_WM_HINTS WM_TRANSIENT_FOR WM_HINTS _NET_WM_WINDOW_TYPE WM_NAME 2>&1 | sed "s/^/   /"
    # X11: окно «без фокуса» Godot делает override-redirect (менеджер окон его не трогает, оно всегда сверху);
    # Windows/Wine: WS_EX_TOPMOST → _NET_WM_STATE_ABOVE. Годится любое из двух.
    xprop -id $MINI _NET_WM_STATE | grep -q ABOVE || xwininfo -id $MINI | grep -q "Override Redirect State: yes" || { echo "ОШИБКА: окно не «поверх всех»"; OK=false; }
    xprop -id $MINI WM_TRANSIENT_FOR | grep -q "not found" || { echo "ОШИБКА: окошко привязано к главному окну (transient)"; OK=false; }
    xwininfo -id $MINI | grep -q "IsViewable" || { echo "ОШИБКА: окошко не видно"; OK=false; }
    [ -n "$SHOTS" ] && mkdir -p "$SHOTS" && import -window root "$SHOTS/mini_over_game_1.png" 2>/dev/null
    touch "$DATA/mini-1.ok"
    until [ -f "$DATA/mini-2.json" ] || ! kill -0 $A 2>/dev/null; do sleep 0.3; done
    echo "--- после сворачивания главного окна и события «друг зашёл»:"
    xwininfo -id $MAIN | grep -E "Map State"; xwininfo -id $MINI | grep -E "Map State|Height"
    xwininfo -id $MINI | grep -q "IsViewable" || { echo "ОШИБКА: окошко пропало вместе с главным окном"; OK=false; }
    [ -n "$SHOTS" ] && import -window root "$SHOTS/mini_over_game_2.png" 2>/dev/null
    touch "$DATA/mini-2.ok"
  else
    OK=false
  fi
  kill $G 2>/dev/null
  wait $A; RC=$?
  grep -E "autotest|SCRIPT ERROR|ERROR: |\[mini\]" "$DATA/app.log" | cut -c1-400 | head -30
  echo "exit=$RC ok=$OK"
  [ $RC = 0 ] && $OK
'
RC=$?
kill $R 2>/dev/null
echo "data: $DATA result=$RC"
exit $RC
