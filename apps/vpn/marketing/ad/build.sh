#!/usr/bin/env bash
# YURK ad #1 «ЮРКНИ»: frames → MP4 1080x1920 for TikTok / Shorts / Reels.
#   bash build.sh WORK_DIR [MY_VOICE.(wav|mp3|m4a)]
# Without a voice: a silent video — voice it and add music in CapCut / TikTok (Commercial Music Library).
# With your recording of the text from ../SCRIPTS.md (22 s, one take): it is put on the video, cleaned up a bit.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
W="${1:?work dir}"; VOICE="${2:-}"
mkdir -p "$W"; cd "$W"
# with the voice: frames timed to the recording (VOICE map in scene.html)
if [ -n "$VOICE" ]; then F=frames_voice; [ -f $F/f00660.jpg ] || VOICE_SYNC=1 node "$HERE/render.js" $F 30; else F=frames; [ -f $F/f00660.jpg ] || node "$HERE/render.js" $F 30; fi
enc=(-c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -profile:v high -movflags +faststart)
if [ -n "$VOICE" ]; then
  ffmpeg -v error -y -framerate 30 -i $F/f%05d.jpg -i "$VOICE" "${enc[@]}" \
    -af "highpass=f=80,acompressor=threshold=-20dB:ratio=3:makeup=3,loudnorm=I=-14:TP=-1" -c:a aac -b:a 192k -ar 44100 -t 22 yurk_ad.mp4
  echo "ready: $W/yurk_ad.mp4"
else
  ffmpeg -v error -y -framerate 30 -i $F/f%05d.jpg -f lavfi -i anullsrc=r=44100:cl=stereo "${enc[@]}" -c:a aac -shortest yurk_ad_novoice.mp4
  echo "ready: $W/yurk_ad_novoice.mp4"
fi
