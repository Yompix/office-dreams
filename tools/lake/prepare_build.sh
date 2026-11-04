#!/bin/bash
# WHAT THE LAKE KEEPS — подготовка перед ЛЮБОЙ сборкой (Windows, Android, сайт).
# Файлы .import из godot/lake/assets в git не идут (папка assets/ целиком вне git), а без этих настроек текстуры лежат
# в видеопамяти без сжатия (≈3,3 ГБ вместо ≈0,4 ГБ) — так был вылет на встроенной Radeon (3 ноя).
# Порядок: сжатие для видеокарты (vram_textures.py) → высокое качество сжатия у героев (tex_quality.py) → переимпорт.
# Повторный запуск ничего не меняет (оба скрипта пишут «изменено …: 0»), переимпорт тогда быстрый.
#   tools/lake/prepare_build.sh      # потом экспорт как обычно (godot --export-release …)
# Пока идёт этот скрипт, не запускать другие копии Godot на этом проекте (переимпорт пишет в .godot/imported).
set -euo pipefail
cd "$(dirname "$0")/../.."
GODOT="${GODOT:-/home/user/tools/godot/godot}"
python3 tools/lake/vram_textures.py
python3 tools/lake/tex_quality.py
"$GODOT" --headless --path godot/lake --import
echo "готово: настройки импорта текстур применены — можно собирать"
