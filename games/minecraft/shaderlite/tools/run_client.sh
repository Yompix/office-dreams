#!/bin/bash
# Запуск клиента под Xvfb + Mesa llvmpipe (программная видеокарта) для снимков и замеров.
# Использование: tools/run_client.sh <имя_прогона> <сценарий.txt> <моды через :> [ШxВ]
#   SL_CACHE — папка с Minecraft (tools/setup_client.py), там же saves-шаблон world_template.
#   IRIS_PACK — имя шейдерпака (папка/zip в $SL_CACHE/game/shaderpacks) или пусто = без шейдеров.
#   IRIS_OPTS — файл с опциями шейдерпака (станет shaderpacks/<pack>.txt).
#   OPTIONS_EXTRA — строки, дописываемые в options.txt.
set -e
NAME=$1; SCRIPT=$(readlink -f "$2"); MODS=$3; RES=${4:-1280x720}
C=${SL_CACHE:?SL_CACHE}; G=$C/game
W=${RES%x*}; H=${RES#*x}
mkdir -p $G/mods $G/config $G/shaderpacks $G/screenshots $C/logs
rm -rf $G/saves/test && mkdir -p $G/saves && cp -r $C/world_template $G/saves/test
rm -f $G/screenshots/*.png
cat > $G/options.txt <<OPT
onboardAccessibility:false
skipMultiplayerWarning:true
tutorialStep:none
joinedFirstServer:true
narrator:0
soundCategory_master:0.0
renderDistance:${VIEW:-8}
simulationDistance:5
maxFps:260
enableVsync:false
fov:0.0
guiScale:2
graphicsMode:1
renderClouds:"true"
pauseOnLostFocus:false
fullscreen:false
lang:ru_ru
${OPTIONS_EXTRA}
OPT
if [ -n "$IRIS_PACK" ]; then
  printf 'shaderPack=%s\nenableShaders=true\ndisableUpdateMessage=true\n' "$IRIS_PACK" > $G/config/iris.properties
  [ -n "$IRIS_OPTS" ] && cp "$IRIS_OPTS" "$G/shaderpacks/$IRIS_PACK.txt" || rm -f "$G/shaderpacks/$IRIS_PACK.txt"
else
  printf 'enableShaders=false\ndisableUpdateMessage=true\n' > $G/config/iris.properties
fi
MAIN=$(python3 -c "import json;print(json.load(open('$C/mc/launch.txt'))['main'])")
AI=$(python3 -c "import json;print(json.load(open('$C/mc/launch.txt'))['assetIndex'])")
LOG=$C/logs/$NAME.log
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=${LP_NUM_THREADS:-3} MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450
cd $G
timeout ${TIMEOUT:-900} xvfb-run -a -s "-screen 0 ${W}x${H}x24 +extension GLX" \
  java -Xmx3G -Xss4M -Dfabric.addMods="$MODS" -Dshaderlite.autotest="$SCRIPT" -Dorg.lwjgl.util.NoChecks=true \
  -cp "$(cat $C/mc/classpath.txt)" $MAIN --username Tester --version 1.21.1 --gameDir $G --assetsDir $C/mc/assets \
  --assetIndex $AI --accessToken 0 --userType legacy --width $W --height $H --quickPlaySingleplayer test > $LOG 2>&1 || true
grep -E "SLBENCH|SLSHOT|SLTEST|ShaderLite\]|ERROR|Exception|shader|Shader" $LOG | grep -v "^\s*at " | head -${GREPN:-60}
mkdir -p $C/shots/$NAME && cp $G/screenshots/*.png $C/shots/$NAME/ 2>/dev/null || true
