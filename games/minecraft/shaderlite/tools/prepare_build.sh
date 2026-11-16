#!/bin/bash
# Готовит $SL_CACHE для сборки мода и выпуска: моды из pack/mods.json, yarn-маппинги, tiny-remapper,
# Minecraft и нужные модули Fabric API / Mod Menu с именами yarn (только для компиляции).
set -e
C=${SL_CACHE:?SL_CACHE}; D=$(cd "$(dirname "$0")/.." && pwd)
[ -f $C/mc/classpath.txt ] || python3 $D/tools/setup_client.py $C/mc
mkdir -p $C/mods $C/tools
python3 - "$D/pack/mods.json" "$C/mods" <<'PY'
import json, sys, os, hashlib, urllib.request
for m in json.load(open(sys.argv[1])):
    p = os.path.join(sys.argv[2], m['file'])
    if os.path.exists(p): continue
    data = urllib.request.urlopen(urllib.request.Request(m['url'], headers={'User-Agent': 'shaderlite-build/1.0'})).read()
    assert hashlib.sha512(data).hexdigest() == m['sha512'], m['file']
    open(p, 'wb').write(data); print('downloaded', m['file'])
PY
cd $C/tools
[ -f tiny-remapper-0.11.1-fat.jar ] || curl -sfO https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar
[ -f mappings/mappings.tiny ] || { curl -sfO "https://maven.fabricmc.net/net/fabricmc/yarn/1.21.1+build.3/yarn-1.21.1+build.3-mergedv2.jar"; unzip -o -q yarn-1.21.1+build.3-mergedv2.jar mappings/mappings.tiny; }
RM="java -jar tiny-remapper-0.11.1-fat.jar"
[ -f mc-named.jar ] || $RM $C/mc/versions/1.21.1/1.21.1.jar mc-named.jar mappings/mappings.tiny official named $(tr ':' '\n' < $C/mc/classpath.txt | grep -v versions/)
FAPI=$(ls $C/mods/fabric-api-*.jar); mkdir -p fapi; (cd fapi && unzip -o -q $FAPI 'META-INF/jars/fabric-api-base*' 'META-INF/jars/fabric-lifecycle-events-v1*' 'META-INF/jars/fabric-rendering-v1*')
for j in fapi/META-INF/jars/*.jar $(ls $C/mods/modmenu-*.jar); do
  b=$(basename $j .jar); [ -f named-$b.jar ] || $RM $j named-$b.jar mappings/mappings.tiny intermediary named
done
echo "ready: $C"
