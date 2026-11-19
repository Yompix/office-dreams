#!/bin/bash
# Запуск клиента «как у игрока»: обфусцированный Minecraft + intermediary, Fabric Loader из релиза, моды — jar из папки
# (в dev-среде Sodium не работает: его mixin не переносится на имена yarn). Нужен хотя бы один ./gradlew runClient
# (берём библиотеки из build/loom-cache/argFiles/runClient) и xvfb-run.
#
#   tools/prod_client.sh <папка игры с mods/> <лог> [-Dnovograd.autotest=uiinv ...]
#
# Пример: распаковать files/mods из Novograd-Full-<версия>.zip в /tmp/x/mods, ссылку saves/Island на мир — и
#   tools/prod_client.sh /tmp/x /tmp/x/uiinv.log -Dnovograd.autotest=uiinv
set -e
GD=$(realpath "$1"); LOG=$2; shift 2
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LOADER=${NOVOGRAD_LOADER:-0.17.2}
ARGS=$ROOT/build/loom-cache/argFiles/runClient
[ -f "$ARGS" ] || { echo "нет $ARGS — сначала ./gradlew runClient"; exit 1; }
G=$HOME/.gradle/caches
CP=$(grep -o '[^:"]*\.jar' "$ARGS" | sort -u | grep -v 'build/\|mappings.jar\|minecraft-clientOnly\|minecraft-common\|remapped_mods\|/fabric-loader-' | paste -sd:)
CP=$CP:$(find "$G/modules-2/files-2.1/net.fabricmc/fabric-loader/$LOADER" -name "fabric-loader-$LOADER.jar" | head -1)
CP=$CP:$(find "$G/modules-2/files-2.1/net.fabricmc/intermediary/1.21.1" -name '*.jar' ! -name '*sources*' | head -1)
CP=$CP:$G/fabric-loom/1.21.1/minecraft-client.jar
cd "$GD"
timeout 900 xvfb-run -a -s "-screen 0 1280x720x24" java -Xmx3G "$@" -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --gameDir "$GD" --assetsDir "$G/fabric-loom/assets" --assetIndex 1.21.1-17 --version 1.21.1 --accessToken 0 --username Dev \
  --width 1280 --height 720 > "$LOG" 2>&1
