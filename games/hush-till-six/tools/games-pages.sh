#!/usr/bin/env bash
# Короткий бесплатный адрес игры: копия web в репозиторий Yompix/yompix.github.io (GitHub Pages) → https://yompix.github.io
# Запуск: tools/games-pages.sh <клон yompix.github.io>, потом в той папке git add -A && git commit && git push.
# После правок игры в web — запустить снова, иначе на коротком адресе останется старая версия.
set -euo pipefail
SRC="$(cd "$(dirname "$0")/.." && pwd)/web"
DST="${1:?укажи папку с клоном yompix.github.io}"
SITE=https://yompix.github.io
mkdir -p "$DST"
find "$DST" -mindepth 1 -maxdepth 1 ! -name .git ! -name README.md -exec rm -rf {} +   # старая копия (кроме .git и README)
node "$(dirname "$0")/tv/legacy.js" --check || { echo "сначала: node tools/tv/legacy.js"; exit 1; }   # копии скриптов для ТВ-приставок и старых браузеров (legacy/) свежие
cp -a "$SRC/." "$DST/"
find "$DST" -name '_*' ! -path '*/.git/*' -exec rm -rf {} +                    # черновики вроде _preview.html не выкладываем
touch "$DST/.nojekyll"                                   # отдавать файлы как есть, без Jekyll
sed -i "s#https://office-dreams-production.up.railway.app/games/nightshift/#$SITE/#g" "$DST/index.html"   # картинка для превью ссылки
cat > "$DST/404.html" <<'HTML'
<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Hush Till Six</title><meta http-equiv="refresh" content="0; url=/"><script>location.replace('/')</script></head><body style="background:#050607"></body></html>
HTML
[ -f "$DST/README.md" ] || cat > "$DST/README.md" <<'MD'
# Hush Till Six (Молчи до шести)

Бесплатная интерактивная хоррор-игра в браузере: https://yompix.github.io

Файлы копируются из `web` репозитория office-dreams скриптом `tools/games-pages.sh` — правки делать там.
MD
echo "готово: $DST"
