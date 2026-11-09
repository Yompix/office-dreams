# What the Lake Keeps

Интерактивное кино на Godot 4.7: зимняя ночь, брат и сестра едут к озеру, пять глав, пять концовок, 2+ часа.
Выпуск: itch.io — 22 ноября, Steam — декабрь.

| Папка | Что внутри |
|---|---|
| `godot/` | проект игры: `scenes/`, `scripts/` (`game.gd`, `explore.gd`, главы), `data/ch1–ch5.json` (сценарий в данных), `snd/`, `assets/`, `docs/` (документы, план выпуска, отчёты), `export_presets.cfg` |
| `tools/story/` | сценарий: главы (`ch1.py`…), правки v2 (`script_v2.py` + `script_v2/`), отклики на выборы, таблицы |
| `tools/` | тесты маршрутов (`story_sim.py`, `story_tests.py`), аудит (`story_audit.py`), длина прохождения, голоса, звук, текстуры, сборки |
| `story/` | первые документы сюжета (библия, главы) |

Персонажи собираются общим `../../tools/chars/`, Godot — `../../tools/godot/env.sh`.

**Всё — из этой папки** (`cd games/what-the-lake-keeps`):
- сценарий v2 применён? `python3 tools/story/script_v2.py --check`
- маршруты: `GODOT=/путь/к/godot python3 tools/story_tests.py [имя]` (все 36 — около часа, `JOBS=3`)
- аудит сюжета: `python3 tools/story_audit.py check` (около 80 минут)
- длина игры: `python3 tools/playtime_breakdown.py`
- сборки: `godot --headless --path godot --export-release "Android" …`; Windows — с окном (`xvfb-run`), см. `godot/docs/`.
