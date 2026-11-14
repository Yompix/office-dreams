#!/bin/bash
# WHAT THE LAKE KEEPS — LICENSES.txt рядом с игрой (после экспорта). Внутри сборки он и так есть на всех платформах
# (export_presets.cfg: include_filter "credits/*" → экран «Авторы» → «Лицензии»), а здесь — отдельным файлом:
# Windows — рядом с exe (архив, депо Steam), сайт и itch — рядом с index.html (в архиве). Android: только внутри apk.
# Текст — godot/credits/LICENSES.txt (делает tools/licenses_txt.py из docs/LICENSES.md).
#   tools/export_licenses.sh [папка сборки …]   # по умолчанию godot/build/windows и godot/build/web, какие есть
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=godot/credits/LICENSES.txt
[ -f "$SRC" ] || { echo "нет $SRC — сначала python3 tools/licenses_txt.py"; exit 1; }
DIRS=("$@"); [ ${#DIRS[@]} -gt 0 ] || DIRS=(godot/build/windows godot/build/web)
for d in "${DIRS[@]}"; do
  [ -d "$d" ] || continue
  cp "$SRC" "$d/LICENSES.txt" && echo "LICENSES.txt → $d"
done
