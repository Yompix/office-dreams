#!/usr/bin/env bash
# 2.0.1: 60 к/с, кадр в кадр (--fixed-fps 60: шаг анимаций ровно 1/60 с независимо от скорости записи).
# 2.0: ролик переходов нового оформления (заставка, пиксельное растворение, «Любые игры», листы) → mp4.
#   tests/brand_video.sh <out.mp4> [ширина высота плотность]
OUT=${1:-/tmp/lantern-brand.mp4}; W=${2:-720}; H=${3:-1600}; DEN=${4:-2.0}
HERE=$(cd "$(dirname "$0")/.." && pwd)
FR=$(mktemp -d -p "${FRAMES_TMP:-/dev/shm}" lantern-frames.XXXXXX)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-vid.XXXXXX)
PORT=$((18200 + RANDOM % 300))
QUIET=1 PORT=$PORT node "$HERE/../relay/server.js" > "$DATA/relay.log" 2>&1 & R=$!
sleep 1
xvfb-run -a -s "-screen 0 $((W+64))x$((H+64))x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 \
  --fixed-fps 60 --resolution ${W}x${H} --path "$HERE" -- --autotest brandvid --anim=1 --phone=1 --density=$DEN --safe=28,16 \
  --data="$DATA" --relay=ws://127.0.0.1:$PORT/ws --frames="$FR" --proplog="${PROPLOG:-$DATA/props.json}" > "$DATA/app.log" 2>&1
kill $R 2>/dev/null
grep "\[autotest\]" "$DATA/app.log" | grep "VID\|кадров\|EXIT\|SCRIPT ERROR"
ffmpeg -y -loglevel error -framerate 60 -i "$FR/f%05d.jpg" -vf "scale=540:-2" -c:v libx264 -pix_fmt yuv420p -crf 26 "$OUT"
rm -rf "$FR" "$DATA"
ls -la "$OUT"
