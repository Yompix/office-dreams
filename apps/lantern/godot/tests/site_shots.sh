#!/usr/bin/env bash
# 2.0.1: 4 скриншота ПК 1280×800 для сайта (демо-данные: боты social_seed + 3 участника сети — отдельные LANTERN).
#   tests/site_shots.sh <папка>
OUT=${1:-/tmp/lantern-site}; W=1280; H=800
HERE=$(cd "$(dirname "$0")/.." && pwd)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-site.XXXXXX)
PORT=$((19100 + RANDOM % 400))
mkdir -p "$DATA/cloud" "$OUT"
DB_PATH="$DATA/relay.db" PORT=$PORT CLOUD_DIR="$DATA/cloud" QUIET=1 node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
node "$HERE/tests/social_seed.js" ws://127.0.0.1:$PORT/ws "$DATA/main/autotest-tag.txt" > "$DATA/seed.log" 2>&1 & S=$!
G=${GODOT:-/home/user/tools/godot/godot}
CODE="$DATA/vnet-code.txt"; DONE="$DATA/main/vnet-done"
J=()
i=0
for spec in "Алекс|Есть! Захожу через «Мультиплеер → Присоединиться»|👍|3" "Маша_Кубик|Я с гитарой и зельями 😎|👾|5" "Кирилл|10.88 — пинг 18 мс, летает|🔥|7"; do
  IFS='|' read -r nick say re at <<< "$spec"
  mkdir -p "$DATA/j$i"
  $G --headless --path "$HERE" -- --autotest sitejoin --nosplash=1 --data="$DATA/j$i" --relay=ws://127.0.0.1:$PORT/ws \
    --vnet_code="$CODE" --done="$DONE" --nick="$nick" --say="$say" --react="$re" --say_at="$at" --delay=$i > "$DATA/j$i.log" 2>&1 &
  J+=($!); i=$((i+1))
done
mkdir -p "$DATA/main"
# 2.0.3: демо-пламя (тестовая БД): хозяин — всё, друзья и участники сети — по одному с фишкой
ALL=flame_rainbow,flame_orange,flame_rose,flame_violet,flame_ice,flame_emerald,flame_s1
( for k in $(seq 1 120); do node "$HERE/tests/demo_flames.js" "$DATA/relay.db" "Ночной_Лис=$ALL" "Алекс=flame_rose" "Маша_Кубик=flame_orange" \
    "Кирилл=flame_emerald" "Нюта=flame_violet" > /dev/null 2>&1; sleep 1; done ) & G2=$!
xvfb-run -a -s "-screen 0 $((W+64))x$((H+64))x24" env LIBGL_ALWAYS_SOFTWARE=1 $G --rendering-driver opengl3 --resolution ${W}x${H} \
  --path "$HERE" -- --autotest sitepc --nosplash=1 --data="$DATA/main" --relay=ws://127.0.0.1:$PORT/ws --shots="$OUT" --vnet_code="$CODE" > "$DATA/app.log" 2>&1
RC=$?
touch "$DONE"; sleep 1
kill ${J[@]} $S $R $G2 2>/dev/null
grep -E "\[autotest\]|SCRIPT ERROR" "$DATA/app.log" | grep -v "FIT" | head -40
echo "data: $DATA exit=$RC"
