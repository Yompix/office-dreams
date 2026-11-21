#!/usr/bin/env bash
# 2.0.3: ролик витрины пламени (10 фонарей) → mp4 и png.  tests/flame_video.sh <out.mp4> [секунд]
OUT=${1:-/tmp/lantern-flames.mp4}; SEC=${2:-4}; W=1280; H=600
HERE=$(cd "$(dirname "$0")/.." && pwd)
FR=$(mktemp -d -p "${FRAMES_TMP:-/dev/shm}" lantern-flames.XXXXXX)
DATA=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-fl.XXXXXX)
xvfb-run -a -s "-screen 0 $((W+64))x$((H+64))x24" env LIBGL_ALWAYS_SOFTWARE=1 ${GODOT:-/home/user/tools/godot/godot} --rendering-driver opengl3 \
  --fixed-fps 30 --resolution ${W}x${H} --path "$HERE" -- --autotest flameshow --anim=1 --nosplash=1 --sec=$SEC \
  --data="$DATA" --relay=ws://127.0.0.1:9/ws --frames="$FR" --shots="$(dirname "$OUT")" > "$DATA/app.log" 2>&1
grep -E "\[autotest\]|SCRIPT ERROR|SHADER ERROR|shader" "$DATA/app.log" | head -20
ffmpeg -y -loglevel error -framerate 30 -i "$FR/f%05d.jpg" -c:v libx264 -pix_fmt yuv420p -crf 22 "$OUT"
rm -rf "$FR" "$DATA"
ls -la "$OUT"
