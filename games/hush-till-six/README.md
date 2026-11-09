# Hush Till Six

Интерактивный хоррор про ночного охранника бизнес-центра «Горизонт». 13 языков, озвучка, 14 смертей, 4 концовки.

| Папка | Что внутри |
|---|---|
| `web/` | игра в браузере (three.js): `index.html`, `engine.js`, `scenes.js`, `world.js`, `chars2.js`, модели, звук, шрифты; `legacy/` — копии скриптов для ТВ-приставок и старых браузеров |
| `godot/` | перенос игры на Godot 4.7 (в работе) |
| `tools/story/` | кадры и листы всех сцен (`beats.js`, `sheet.js`), озвучка, проверки сюжета |
| `tools/android/`, `tools/desktop/`, `tools/itch/`, `tools/games-apk/` | сборки: Google Play, ПК (Electron, Steam), itch.io |
| `tools/trailer/`, `tools/i18n/`, `tools/anim/`, `tools/sfx/`, `tools/tv/` | трейлеры, переводы, анимации, звуки, ТВ-приставки |
| `tools/games-pages.sh` | копия `web/` на https://yompix.github.io |
| `docs/` | `LOOK.md` (как должна выглядеть картинка), `port/` — план и карты переноса на Godot |

Персонажи собираются общим `../../tools/chars/`, музыка — `../../tools/music/compose.py`.

**Запуск для проверки:** из этой папки `python3 -m http.server 8771 --bind 127.0.0.1` → http://127.0.0.1:8771/web/
(режим проверки — `?qa=1`). Кадр сцены: `node tools/story/beats.js /tmp/k L cine ru "intro:5:2.0"`.
Тесты (из корня репозитория): `npm test` проверяет и копии `web/legacy/`.

**Выкладка:** Railway раздаёт `web/` по адресу `/games/nightshift/` (после push в `main`);
короткий адрес — `tools/games-pages.sh <клон yompix.github.io>`, затем commit и push в том репозитории.
