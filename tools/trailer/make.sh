#!/bin/bash
# «Hush Till Six» — трейлер целиком: монтаж → кадры движка → звук → сборка → MP4 (H.264 + AAC, −14 LUFS).
#   bash tools/trailer/make.sh yt|tt ru|en OUTDIR [CACHE]
# Нужен сервер статики: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1
# Английская версия берёт из русской кадры без надписей и без говорящих в кадре (probe.js) — сначала собрать русскую.
set -e
FMT=$1; L=$2; OUT=$3; CACHE=${4:-$OUT/cache}
D=$(cd "$(dirname "$0")" && pwd); mkdir -p "$OUT" "$CACHE"
TL=$OUT/tl_${FMT}_${L}.json
node "$D/edl.js" "$FMT" "$L" > "$TL"
EXTRA=""
if [ "$L" = en ] && [ -f "$CACHE/${FMT}_ru.json" ]; then
  node "$D/edl.js" "$FMT" ru > "$OUT/tl_${FMT}_ru.json"
  [ -f "$OUT/probe_$FMT.json" ] || node "$D/probe.js" "$OUT/tl_${FMT}_ru.json" "$TL" "$OUT/probe_$FMT.json"
  EXTRA="--reuse $CACHE/${FMT}_ru.json --probe $OUT/probe_$FMT.json"
fi
node "$D/render.js" "$TL" "$CACHE" $EXTRA
python3 "$D/audio.py" "$TL" "$OUT/a_${FMT}_${L}.wav"
node "$D/comp.js" "$TL" "$CACHE" "$OUT/v_${FMT}_${L}.mp4"
NAME=$([ "$FMT" = yt ] && echo youtube || echo tiktok)
ffmpeg -v error -y -i "$OUT/v_${FMT}_${L}.mp4" -i "$OUT/a_${FMT}_${L}.wav" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart "$OUT/HushTillSix_trailer_${NAME}_${L}.mp4"
echo "$OUT/HushTillSix_trailer_${NAME}_${L}.mp4"
