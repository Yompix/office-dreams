#!/usr/bin/env bash
# Окошко поверх игры в Windows-сборке под Wine 11: отдельное окно верхнего уровня, «поверх всех», не привязано к главному.
S=${1:-${TMPDIR:-/tmp}}   # папка для данных и снимков; нужны Wine 11 (/opt/wine-stable), xvfb, x11-utils, jwm
EXE=/home/user/office-dreams/godot/playtogether/export/windows/LANTERN.exe
RELAY_DIR=/tmp/claude-0/wt-main2/apps/lantern/relay
PORT=18977
D=$S/wmini; rm -rf "$D"; mkdir -p "$D"
export LANG=C.UTF-8 LC_ALL=C.UTF-8 PATH=/opt/wine-stable/bin:$PATH WINEPREFIX=/tmp/claude-0/lantern2/wine11 WINEDEBUG=-all WINEDLLOVERRIDES="mscoree,mshtml="
PORT=$PORT node "$RELAY_DIR/server.js" > "$D/relay.log" 2>&1 & R=$!
sleep 1
export D EXE PORT
xvfb-run -a -s "-screen 0 1920x1080x24" bash -c '
  jwm >/dev/null 2>&1 & sleep 1
  wine "$EXE" --rendering-driver opengl3 --resolution 1280x800 -- --autotest mini --data="Z:$D" --relay=ws://127.0.0.1:$PORT/ws --shots="Z:$D/shots" > "$D/app.log" 2>&1 & A=$!
  for i in $(seq 1 240); do [ -f "$D/mini-1.json" ] && break; sleep 0.5; done
  xev -geometry 1600x900+50+50 > /dev/null 2>&1 & G=$!
  sleep 1
  cat "$D/mini-1.json"; echo
  echo "--- окна X11 верхнего уровня (от Wine):"
  xwininfo -root -tree | grep -E "LANTERN|Event Tester" | head
  for w in $(xwininfo -root -tree | grep -E "\"LANTERN" | awk "{print \$1}"); do
    echo "== $w"; xwininfo -id $w | grep -E "Absolute|Width|Height|Map State|Override"; xprop -id $w _NET_WM_STATE WM_TRANSIENT_FOR _MOTIF_WM_HINTS _NET_WM_WINDOW_TYPE WM_HINTS 2>&1 | head -12
  done
  import -window root "$D/over1.png" 2>/dev/null
  touch "$D/mini-1.ok"
  for i in $(seq 1 120); do [ -f "$D/mini-2.json" ] && break; sleep 0.5; done
  echo "--- после сворачивания главного окна:"; cat "$D/mini-2.json"; echo
  xwininfo -root -tree | grep -E "LANTERN" | head
  for w in $(xwininfo -root -tree | grep -E "\"LANTERN" | awk "{print \$1}"); do echo "== $w"; xwininfo -id $w | grep -E "Height|Map State"; xprop -id $w WM_STATE _NET_WM_STATE 2>&1 | head -4; done
  echo "--- порядок окон (снизу вверх):"; xprop -root _NET_CLIENT_LIST_STACKING; xprop -root _NET_ACTIVE_WINDOW
  import -window root "$D/over2.png" 2>/dev/null
  touch "$D/mini-2.ok"
  kill $G 2>/dev/null
  wait $A; echo "wine exit=$?"
  grep -E "autotest|SCRIPT ERROR|\[mini\]" "$D/app.log" | cut -c1-300 | head -20
'
kill $R 2>/dev/null
