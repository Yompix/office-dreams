#!/usr/bin/env bash
# YURK ad #1 «ЮРКНИ»: frames + own music + voice → MP4 1080x1920 for TikTok / Shorts / Reels.
#   bash build.sh WORK_DIR [VOICE.wav]
# Without VOICE: a temporary robot voice (tools/tts.py, ru-RU-DmitryNeural) is used.
# With VOICE (your own recording of the whole text, 22 s, see ../SCRIPTS.md): it replaces the robot.
# Also writes ad_novoice.mp4 (music only) — for voicing over in CapCut.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../../../.." && pwd)"
W="${1:?work dir}"; MYVOICE="${2:-}"
mkdir -p "$W"; cd "$W"

python3 "$HERE/music.py" music.wav 22
[ -f frames/f00660.jpg ] || node "$HERE/render.js" frames 30

# line | start (s) | longest it may take (s)
LINES=(
  "Сайт не грузится? Видео тормозит?|0.10|2.45"
  "Хватит ждать.|2.62|0.70"
  "Юркни!|3.38|1.20"
  "Одна кнопка — и всё открывается.|5.00|2.00"
  "Быстро. Незаметно. На всех устройствах.|10.35|4.05"
  "Три дня — бесплатно.|14.70|1.35"
  "А за друзей — получаешь деньги.|16.10|2.10"
  "Юрк ВПН. Ищи в Телеграме.|18.90|2.90"
)
inputs=(); filt=""; n=0
if [ -n "$MYVOICE" ]; then
  inputs+=(-i "$MYVOICE"); filt="[1:a]aresample=44100,pan=stereo|c0=c0|c1=c0,acompressor=threshold=-18dB:ratio=3,volume=1.6[vo];"
else
  for L in "${LINES[@]}"; do
    IFS='|' read -r txt at max <<<"$L"; n=$((n+1))
    [ -f "v$n.wav" ] || {
      python3 "$ROOT/tools/tts.py" --voice ru-RU-DmitryNeural --rate=+10% --text "$txt" --write-media "v$n.mp3" >/dev/null
      ffmpeg -v error -y -i "v$n.mp3" -af "silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse" -ar 44100 -ac 2 "v$n.wav"
    }
    d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "v$n.wav")
    tempo=$(python3 -c "print(max(1.0, min(1.6, $d / $max)))")
    inputs+=(-i "v$n.wav")
    ms=$(python3 -c "print(int($at * 1000))")
    filt+="[$n:a]atempo=$tempo,adelay=${ms}|${ms},volume=1.9[v$n];"
  done
  mixin=""; for i in $(seq 1 $n); do mixin+="[v$i]"; done
  filt+="${mixin}amix=inputs=$n:normalize=0,acompressor=threshold=-20dB:ratio=3:makeup=2[vo];"
fi
# music ducks under the voice
filt+="[vo]asplit=2[vo1][vo2];[0:a]volume=0.75[mu];[mu][vo1]sidechaincompress=threshold=0.04:ratio=6:attack=15:release=350[duck];[duck][vo2]amix=inputs=2:normalize=0,alimiter=limit=0.95[a]"
ffmpeg -v error -y -i music.wav "${inputs[@]}" -filter_complex "$filt" -map "[a]" -ar 44100 audio.wav

enc=(-c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -profile:v high -movflags +faststart)
ffmpeg -v error -y -framerate 30 -i frames/f%05d.jpg -i audio.wav "${enc[@]}" -c:a aac -b:a 192k -shortest ad.mp4
ffmpeg -v error -y -framerate 30 -i frames/f%05d.jpg -i music.wav "${enc[@]}" -c:a aac -b:a 192k -shortest ad_novoice.mp4
echo "ready: $W/ad.mp4 and $W/ad_novoice.mp4"
