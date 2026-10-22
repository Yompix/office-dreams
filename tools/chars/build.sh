#!/usr/bin/env bash
# Сборка персонажей Night Shift: MakeHuman/MPFB2 (CC0) → glb с лицевыми формами → сжатие meshopt → public/games/nightshift/models/
# Нужны: Blender 4.5 с MPFB2 и пакетами ассетов (см. gen.py), glTF-Transform CLI (npm @gltf-transform/cli).
# Запуск: BLENDER=/путь/к/blender GT=/путь/к/gltf-transform tools/chars/build.sh [имя ...]
set -euo pipefail
cd "$(dirname "$0")/../.."
BLENDER=${BLENDER:-blender}; GT=${GT:-gltf-transform}; TMP=$(mktemp -d)
NAMES=${*:-ethan frank gus dayguard lily tenant}
for n in $NAMES; do
  "$BLENDER" -b --python tools/chars/gen.py -- "$n" "$TMP/$n.glb" 2>&1 | grep -E "DONE|RETINT|Error|Traceback" || true
  "$GT" meshopt "$TMP/$n.glb" "public/games/nightshift/models/$n.glb" --level high >/dev/null
  ls -la "public/games/nightshift/models/$n.glb"
done
rm -rf "$TMP"
