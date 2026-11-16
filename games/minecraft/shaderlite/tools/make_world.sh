#!/bin/bash
# Делает тестовый мир (сид 8678942899319966093: лес у спавна, река, пещеры) выделенным сервером
# и кладёт его в $SL_CACHE/world_template (с включёнными командами).
set -e
C=${SL_CACHE:?SL_CACHE}; D=$(cd "$(dirname "$0")/.." && pwd)
[ -f $C/server.jar ] || python3 -c "import json;print(json.load(open('$C/mc/version.json'))['downloads']['server']['url'])" | xargs curl -s -o $C/server.jar
rm -rf $C/srv && mkdir -p $C/srv && cd $C/srv && echo eula=true > eula.txt
printf 'level-seed=8678942899319966093\nonline-mode=false\nspawn-protection=0\nview-distance=6\nsimulation-distance=4\n' > server.properties
python3 $D/tools/srvdrive.py ../server.jar "forceload add 0 384 128 512" "forceload add -128 -128 96 96" \
  "gamerule doDaylightCycle false" "gamerule doWeatherCycle false" "gamerule doMobSpawning false" "weather clear" \
  "forceload remove all" "save-all flush" | grep -E "Done|Marked|Saved" || true
python3 - <<'PY'
import gzip
p = 'world/level.dat'; d = gzip.decompress(open(p, 'rb').read()); k = b'\x01\x00\x0dallowCommands'; i = d.find(k) + len(k)
open(p, 'wb').write(gzip.compress(d[:i] + b'\x01' + d[i + 1:]))
PY
rm -rf $C/world_template && mv world $C/world_template && rm -f $C/world_template/session.lock
cd $C && rm -rf srv server.jar && echo "world: $C/world_template"
