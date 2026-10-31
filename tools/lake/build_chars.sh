#!/usr/bin/env bash
# Герои WHAT THE LAKE KEEPS: MakeHuman/MPFB2 (CC0) → godot/lake/assets/chars/<имя>.glb (кости как у клипов Quaternius,
# лицевые формы ARKit + виземы). Описание героев — tools/lake/chars.json, генератор — tools/chars/gen.py (общий с Hush Till Six).
# Нужны Blender 4.5 с MPFB2 и пакеты ассетов MakeHuman (makehuman_system_assets, faceunits01, visemes02, hair01, shirts02,
# pants02, hats03, shoes02, shoes03, equipment03 …) в папке данных MPFB.
# Запуск: BLENDER=/home/user/tools/blender45/blender tools/lake/build_chars.sh [имя ...]
set -euo pipefail
cd "$(dirname "$0")/../.."
BLENDER=${BLENDER:-/home/user/tools/blender45/blender}
OUT=godot/lake/assets/chars; mkdir -p "$OUT"
NAMES=${*:-noah iris danny roman}
for n in $NAMES; do
  CHARS_JSON=tools/lake/chars.json "$BLENDER" -b --python tools/chars/gen.py -- "$n" "$PWD/$OUT/$n.glb" 2>&1 | grep -E "DONE|RETINT|TARGET|Error|Traceback|not found|WARNING.*mhclo" || true
  rm -f "$OUT/$n.blend" "$OUT/$n.blend1"
  ls -la "$OUT/$n.glb" 2>/dev/null || echo "$n: не собран"
done
