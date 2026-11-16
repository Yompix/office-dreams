# Лёгкие шейдеры (ShaderLite) — Minecraft Java 1.21.1, Fabric

Сборка для слабых ПК: Sodium + Iris + оптимизации, свой шейдерпак **ShaderLite** (с нуля) и свой мод
**«Адаптивное качество»**. Инструкция для игроков — `README_RU.txt`. Готовые архивы в git не кладём (как и в соседних
проектах): `.mrpack`, ZIP модов, ZIP шейдера и jar мода отдаются владельцу файлом.

| Папка | Что это |
|---|---|
| `shaders/ShaderLite/shaders/` | шейдерпак: `program/*.glsl` — код, `lib/` — общие функции, `*.vsh/*.fsh` и `world-1/`, `world1/` — короткие заглушки (делает `tools/gen_shader_stubs.py`), `shaders.properties` — профили МИНИМУМ/НИЗКИЙ/СРЕДНИЙ/ВЫСОКИЙ, `lang/` — русские названия |
| `mod/` | мод «Адаптивное качество» (Fabric, без миксинов): держит целевой FPS, меняя облака/дальность/частицы/сущности |
| `pack/mods.json` | список модов сборки: версии, sha512, ссылки Modrinth, лицензии, можно ли класть jar в ZIP |
| `pack/overrides/` | конфиги сборки (options.txt, Iris, Sodium, наш мод) |
| `tools/` | сборка и проверка (см. ниже); `tools/autotest/` — тестовый мод для снимков и замеров (игрокам не нужен) |
| `docs/LOG.md` | журнал: что сделано, замеры, что не проверено |

## Сборка (без Gradle — экономит ~1 ГБ диска)
```
export SL_CACHE=/путь/к/кэшу            # туда скачается Minecraft (~100 МБ без звуков)
python3 tools/setup_client.py $SL_CACHE/mc
# один раз: yarn-маппинги + tiny-remapper + Minecraft с именами yarn — см. tools/prepare_build.sh
tools/prepare_build.sh
tools/build_mod.sh mod $SL_CACHE/shaderlite-adaptive-1.0.0.jar 1.0.0
python3 tools/build_release.py <папка_выпуска> $SL_CACHE/shaderlite-adaptive-1.0.0.jar $SL_CACHE/mods
```
`$SL_CACHE/mods` — скачанные jar из `pack/mods.json` (`tools/prepare_build.sh` качает и их).

## Проверка (xvfb + Mesa llvmpipe)
`tools/run_client.sh <имя> <сценарий.txt> <моды через :> 1280x720` — запускает клиент, сценарий выполняет тестовый мод
(`wait`, `cmd`, `look`, `shot`, `bench`, `cave`, `screen`, `log`, `quit`), в лог пишутся строки `SLBENCH`/`SLSHOT`.
Сценарии — `tools/scenarios/`. Мир для проверки делается сервером (`tools/make_world.sh`).
