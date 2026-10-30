#!/usr/bin/env bash
# Подключает ассеты игры (модели, звук, шрифты) в проект Godot жёсткими ссылками: на диске не дублируются,
# в git не попадают (godot/nightshift/assets — в .gitignore). Запускать после клонирования и после новых ассетов.
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/public/games/nightshift"; DST="$ROOT/godot/nightshift/assets"
mkdir -p "$DST"
for d in models snd fonts; do rm -rf "$DST/$d"; cp -al "$SRC/$d" "$DST/$d"; done
cp -f "$SRC"/timing*.json "$DST/" 2>/dev/null || true
node "$ROOT/tools/godot/unpack_glb.mjs" "$SRC/models" "$DST/models"   # Godot не читает meshopt и квантование
echo "assets: $(du -sh "$DST" | cut -f1) в $DST"
