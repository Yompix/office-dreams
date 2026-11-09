# WHAT THE LAKE KEEPS — лицензии сторонних ассетов (свод)

Состояние на 6 ноября 2026, к выпуску 22 ноября (Steam, Google Play, itch.io). Собрано только по репозиторию и
локальным файлам, ничего не скачивалось. Папка `godot/assets/` в git не идёт, поэтому происхождение её файлов
записано в скриптах: `tools/fetch_assets.py` (Poly Haven, шрифты вывесок, общие с Hush Till Six модели),
`tools/build_chars.sh` + `tools/chars.json` (герои). Общие с Hush Till Six (HTS) условия лежат в
`../../games/hush-till-six/web/CREDITS.txt` и `../../games/hush-till-six/web/licenses/`.

**Атрибуция:** **обязательна** — этого требует лицензия; *по желанию* — не требуется (CC0 и т. п.), указываем из
благодарности; «—» — своё.
**В сборку не входят** (`godot/export_presets.cfg:11`): `assets/models/lily*`, `tenant*`, `deer_raw*`,
`assets/chars/test/*`, `scripts/dev/*`, `scenes/dev/*`, `export/*`, `build/*`. Их лицензии для выпуска не важны.

## 1. Сводная таблица

| № | Группа | Где лежит | Откуда | Лицензия | Атрибуция | Где подтверждено |
|---|---|---|---|---|---|---|
| 1 | Движок Godot 4.7.2 и его сторонние компоненты (102 компонента, 19 лицензий: Apache-2.0, BSD, Zlib, FTL, OFL, CC-BY-4.0 и др.) | сама программа (exe, apk, веб) | godotengine.org | MIT (Expat) + лицензии компонентов | **обязательна**: текст из `Engine.get_license_text()`, `Engine.get_copyright_info()`, `Engine.get_license_info()` (см. §4) | `godot/project.godot` (`config/features` 4.7), `../../tools/godot/env.sh:2` (4.7.2); список выведен из самого движка 6 ноя |
| 2 | Poly Haven: 12 моделей предметов, текстуры 2 деревьев, 23 текстуры (снег, асфальт, ткани, дерево, бетон, металл), небо HDRI — всего 38 (список — §2.1) | `godot/assets/ph/*`; запечены в `assets/models/{station,cafe,village,lake,sedan}_*1k*` и в ткани героев `assets/chars/*_1k.webp` | polyhaven.com (через api.polyhaven.com) | CC0 1.0 | *по желанию* | `tools/fetch_assets.py:2`, `:11–25`, `:95–96` → `assets/ph/LICENSE.txt` |
| 3 | Герои игры (14 героев, 16 файлов с `iris_v2`, `danny_v2`): тела, кожа, глаза, брови, ресницы, зубы, волосы, готовая одежда и обувь, лицевые формы ARKit и виземы | `godot/assets/chars/<имя>.glb` и `<имя>_*.webp` | MakeHuman / MPFB2 и ассеты сообщества MakeHuman, только CC0-пакеты: makehuman_system_assets, hair01, skins01, shirts01, suits01, shoes01, glasses01, dress01, skirts01, faceunits01, visemes02 | CC0 1.0 (два ассета под вопросом, см. §5 п. 2) | *по желанию*, авторы — §2.2 | `tools/chars.json:2` (`_doc`: «только пакеты CC0 … CC-BY не берём»), `tools/build_chars.sh:2`, HTS `CREDITS.txt:19–29`, HANDOFF «Что сделано», 31 окт (ночь) |
| 4 | Герои Hush Till Six в эпизодах: дальнобойщики `gus`, `frank`, `ethan` (`scripts/chapter2.gd:691–692`), полицейский `dayguard` (`chapter2.gd:824`, `chapter5.gd:1095`); `gus`/`frank` — запасные модели санитара и Хейла | `godot/assets/models/{gus,frank,ethan,dayguard}.glb` (жёсткие ссылки из `../../games/hush-till-six/web/models`) | MakeHuman CC0, собраны генератором HTS `../../tools/chars/gen.py` | CC0 1.0 (волосы `dayguard` — §5 п. 2) | *по желанию* | `fetch_assets.py:4–5`, `:97–107`; HTS `CREDITS.txt:19–29` |
| 5 | Своя одежда героев: куртки, худи, пуховик, пальто, жилеты, шапка, шарф; пуговицы и молнии | внутри `assets/chars/*.glb` (`*_m_img.webp` — нарисованы кодом) | своё: `tools/garments.py` (Blender); ткани — Poly Haven (п. 2) | своё + CC0 | — | `garments.py:1–5` |
| 6 | Анимации: 42 клипа Quaternius Universal Animation Library 1–2 | `godot/assets/models/anims.glb` | quaternius.com | CC0 1.0 | *по желанию* | `../../games/hush-till-six/web/models/LICENSE-Quaternius-CC0.txt:9–15` (= `assets/models/LICENSE-Quaternius-CC0.txt`; файл от Universal Base Characters, отдельного файла UAL нет), HTS `CREDITS.txt:31–33`, `docs/audit/REQUIRED_TOOLS.md:14` |
| 7 | Мокап Rokoko: 13 клипов `Rk_*` (разговор, сидя, телефон, ходьба), перенесены кодом на наш скелет; исходные FBX не распространяются | `godot/assets/models/anims2.glb` | бесплатные паки rokoko.com | «в любом проекте, включая коммерческий» (условия Rokoko) | не требуется, *по желанию* | `../../games/hush-till-six/web/licenses/Rokoko-free-mocap.txt:4–22`, HTS `CREDITS.txt:35–39` |
| 8 | Мокап CMU: 1 клип `Cmu_Mop_Loop` (14_13, мытьё пола) | там же, `anims2.glb` | mocap.cs.cmu.edu | «free for all uses», можно в продаваемых продуктах; нельзя продавать сами данные | **просьба CMU** — строка в титрах (§4) | `../../games/hush-till-six/web/licenses/CMU-mocap.txt:4–11` |
| 9 | Шрифты надписей в мире (вывески, щиты, объявления): Lobster, Oswald SemiBold/Medium, PT Sans Regular/Bold, PT Sans Narrow Bold, Caveat SemiBold — 7 TTF без изменений | `godot/assets/fonts/*.ttf` (`scripts/world/signs.gd`) | Google Fonts | SIL OFL 1.1 | **обязательна**: копирайт и текст OFL вместе с игрой | `fetch_assets.py:26–29`, `:89–94` → `assets/fonts/LICENSE-signs.txt` (строка Lobster неточна — §5 п. 6) |
| 10 | Шрифты интерфейса (подмножества woff2): Inter 300/500/600/700, Pangolin (рукописные надписи), IBM Plex Mono Bold | `godot/assets/fonts/*.woff2` (жёсткие ссылки на `../../games/hush-till-six/godot/ui/fonts/`; `fetch_assets.py` их не создаёт) | github.com/rsms/inter, googlefonts/pangolin, IBM/plex | SIL OFL 1.1 | **обязательна** (как п. 9) | `../../games/hush-till-six/godot/ui/fonts/LICENSE.txt:1–5` (в git; тот же текст — `godot/assets/fonts/LICENSE.txt`) |
| 11 | Звуки ElevenLabs Sound Effects (eleven_text_to_sound_v2): 43 файла (список — ключи `tools/sfx.json`) | `godot/snd/<ключ>.ogg` | ElevenLabs, платный тариф владельца | по условиям ElevenLabs результат принадлежит подписчику, коммерческое использование разрешено (на бесплатном тарифе — только некоммерческое) | не требуется | `tools/sfx.json:2` (ключ `"_"`), коммит `440646b`; условия — HTS `CREDITS.txt:50–54`, HANDOFF «Игра должна быть целиком владельца» (§5 п. 4) |
| 12 | Звуки кодом: 27 файлов (подводный гул, щелчок, сирена, стук, плач младенца, телефоны, пар, лягушки, лёд, насос, мотор, ремень, дыхание, стон, ткань, обёртка, монета, автомат и др.) | `godot/snd/*.ogg` | своё: `tools/synth.py` (numpy) | своё | — | `synth.py:1–19`; скрипт ничего не читает с диска — скачанных сэмплов нет |
| 13 | Музыка: 3 трека — тема меню `lake_theme`, тема конца главы `lake_end`, песня по радио `radio_song` | `godot/snd/{lake_theme,lake_end,radio_song}.ogg` | своё: `tools/music.py` на `../../tools/music/compose.py` | своё | — | `music.py:1–3`, `compose.py:1–3` («никаких сэмплов и чужих записей»; оба скрипта только пишут файлы) |
| 14 | Голоса ElevenLabs (eleven_v4): 4 файла — Дэнни RU и EN `s5_2`, Роман RU `s5_help_2`, `s5_lapse_1` (голоса библиотеки ElevenLabs «Teddy», «Alex») | `godot/snd/vo/{ru,en}/*.ogg` | ElevenLabs, платный тариф владельца | как п. 11 | не требуется | `tools/voice.py:2`, `:18–29`, пакеты `tools/vo_{ru,en}.json`; HANDOFF «Что сделано», 31 окт (§5 п. 3–4) |
| 15 | Голоса Qwen3-TTS: все остальные реплики (по 462 файла на язык вместе с п. 14) — голос по описанию (VoiceDesign 1.7B) или клон с готовой реплики (Base 0.6B, образцы `tools/refs/`) | `godot/snd/vo/{ru,en}/*.ogg`, `timing.json` | модели Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign и -0.6B-Base, локально на CPU | Apache-2.0 (лицензия модели; модель в игру не входит) | не требуется, *по желанию* | `tools/voice_qwen.py:1–3`, `:60–63`; `docs/AUDIO_AUDIT.md:42`; `docs/DECISION_LOG.md:46` |
| 16 | Олень: модель и текстуры | `godot/assets/models/deer.glb`, `deer_Image_0.jpg`, `deer_Image_1.png`; исходники — `tools/src/deer_hunyuan.glb`, `deer_ref.jpg` | Figma Weave → Hunyuan 3D 2.1 (ИИ: 3D по картинке); кости и веса — свой `tools/deer.py`, движения — `scripts/world/deer.gd` | **не записана** | ? | `deer.py:1–3`, HANDOFF «Что сделано», 31 окт — **§5 п. 1** |
| 17 | Места и машины: заправка «24», кафе, деревня, озеро, седан, фура, буханка | `godot/assets/models/{station,cafe,village,lake,sedan,truck,van}.glb` | своё: скрипты Blender `tools/{station,cafe,village,lake,car,truck,van}.py`, `bl.py`; текстуры — Poly Haven (п. 2) | своё + CC0 | — | шапки скриптов (`car.py:1–2`: «готовых CC0-машин нет … строим сами») |
| 18 | Рисунки Айрис и Ноя (в т. ч. «Я ПОМНЮ ЗА ТЕБЯ»), фото Анны и дома на стене | `godot/assets/textures/drawing_*.png`, `photo_*.png` | своё: `tools/drawings.py` (кодом); фото — кадры из самой игры (`tools/src/photos`) | своё | — | `drawings.py:1–5`; надпись растрирована системным DejaVu Sans Bold (`drawings.py:242`), сам шрифт в игру не входит |
| 19 | Иконка игры | `godot/icon.png` | не записано | ? | ? | **§5 п. 7** |
| 20 | Сюжет, реплики, данные глав, код, шейдеры, интерфейс | `godot/data/`, `scripts/`, `scenes/`, `story/` | своё (см. §4.3 про ИИ) | своё | — | сторонних фрагментов кода в `scripts/` нет (поиск по copyright, license, ссылкам) |

Вне таблицы:
* Встроенный шрифт движка (`ThemeDB.fallback_font`, `scripts/ui/touch_ctl.gd:156`, `scripts/world/signs.gd:46`) — часть движка, его лицензия входит в п. 1.
* Инструменты сборки (Blender 4.5 + MPFB2, ffmpeg, faster-whisper для нарезки голосов, glTF-Transform, fontTools/pyftsubset)
  в игру не входят.

## 2. Подробности

### 2.1 Poly Haven — 38 ассетов (`fetch_assets.py:11–25`)
* **Модели (12):** dead_tree_trunk, modular_electricity_poles, CashRegister_01, Television_01, Shelf_01, long_life_food,
  cardboard_box_01, metal_jerrycan, old_tyre, WetFloorSign_01, binder_notebook, stationery_supplies.
* **Деревья, только текстуры (2):** fir_tree_01 (+ атлас лап 2K), pine_tree_01.
* **Текстуры (23):** asphalt_snow, snow_02, brown_leather, knitted_fleece, wool_boucle, cotton_jersey,
  poly_wool_herringbone, denim_fabric, leather_red_02, stretch_poplin, bi_stretch, jogging_melange, caban,
  painted_plaster_wall, concrete_floor_02, floor_tiles_06, corrugated_iron, metal_plate, old_wood_floor,
  weathered_plank_siding, wooden_panels, wood_table_worn, kitchen_wood.
* **Небо (HDRI):** narrow_moonlit_road.
* `assets/ph/LICENSE.txt` в этом контейнере устарел: нет leather_red_02, stretch_poplin, bi_stretch, jogging_melange,
  caban. Файл перепишется при следующем запуске `fetch_assets.py`; полный список — в самом скрипте.

### 2.2 MakeHuman — чьи ассеты у кого (всё из CC0-пакетов)
Принадлежность к пакетам сверена по спискам пакетов MPFB, а лицензия в шапках файлов — по данным MPFB в контейнере
(`~/.config/blender/4.5/extensions/.../mpfb/data`). Это вне репозитория, в репозитории записано только общее «CC0».

| Автор | Ассеты | У кого в игре | Подтверждение |
|---|---|---|---|
| MakeHuman team (Data Collection AB, Joel Palmius, Jonas Hauquier) | тело, скелет game_engine, кожа (young/middleage/old caucasian), глаза, брови eyebrow001–012, ресницы, зубы, язык; волосы short01/02/04, bob01/02, ponytail01, braid01; одежда male_casualsuit03–06, female_casualsuit01, male_worksuit01, shoes02–04 | все | HTS `CREDITS.txt:24`; шапка файлов: «explicitly released as CC0 in september 2020» |
| Mika Suominen | лицевые формы ARKit (faceunits01), виземы (visemes02) | все | HTS `CREDITS.txt:23–25` |
| Margaret Toigo (MRT) | ботинки toigo_ankle_boots (жен./муж.), платье toigo_shift_dress, свитер toigo_fisherman_sweater, юбка toigo_long_full_skirt, костюм toigo_male_suit_3, балетки toigo_ballet_flats, причёска toigo_curled_under_bob, кожа toigo_light_skin_female_freckles; брюки toigo_wool_pants | Айрис, Дэнни, Зоя, Ева, Агнес, Хейл, Нина, санитар, Анна; Фрэнк (HTS) | `chars.json`; HTS `CREDITS.txt:25`; шапка файлов: «license CC0» |
| Cortu Johnstone | волосы cortu_short_messy_hair, cortu_shaggy_green_hair | Айрис v2, Дэнни v2, Зоя | `chars.json:178`, `:195`, `:400`, `:565`; шапка файлов: «Cortu Johnstone - CC0» |
| culturalibre | волосы culturalibre_hair_02 | Хейл, молодой Хейл, полицейский (`dayguard`, HTS) | `chars.json:813`, `:1231`; HTS `CREDITS.txt:26` — **§5 п. 2** |
| frankyaye | очки frankyaye_glasses_library_male | Хейл, молодой Хейл | `chars.json:817`, `:1235`; в CREDITS не записан — **§5 п. 2** |
| Namuhekam | рубашка-поло namuhekam_male_polo_shirt | Фрэнк (дальнобойщик, HTS) | HTS `CREDITS.txt:25`; шапка файла: «license: CC0» |

### 2.3 Голоса: чем озвучено
* **ElevenLabs eleven_v4** (31 окт, платный тариф): 4 файла — `ru/s5_2`, `en/s5_2` (Дэнни, голос «Teddy»
  `XjGYkUkzth8BPs29fmcV`), `ru/s5_help_2`, `ru/s5_lapse_1` (Роман, голос «Alex» `9AjtU6o19uipv7QL8dLL`). Остальные
  пакеты из `vo_{ru,en}.json` не были сгенерированы: кредиты кончились (HANDOFF, 31 окт).
* **Qwen3-TTS** — все остальные реплики: новые и эпизодические герои по описанию (`voice_qwen.py` `INSTRUCT`), а Ной,
  Айрис, Дэнни, Зоя, Ева, Агнес, Хейл, Анна, дальнобойщик и диктор — клоном с их готовой реплики
  (`voice_qwen.py:60`, `:81–85`). Образец Дэнни сделан из реплики ElevenLabs — §5 п. 3.
* Итог: 462 файла `.ogg` в `snd/vo/ru` и 462 в `snd/vo/en`.

## 3. Где это должно быть в сборке
Сейчас **ни титров, ни лицензий в сборке нет.** В меню только «Продолжить / Новая игра / Дерево выборов / Настройки /
Выход» (`scripts/ui/hud.gd:494–500`), а `.txt` в экспорт не попадают (`export_presets.cfg:10`: из не-ресурсов берутся только
`*.json`). До выпуска нужно:
1. **Экран «Авторы»** в меню (или титры после концовки) с текстом из §4. На Android это единственный способ: рядом с apk
   файлов нет.
2. **Файл `LICENSES.txt` рядом с игрой** (Windows: в архиве и депо Steam рядом с exe; веб и itch: в архиве или по ссылке).
   В него входят: §4 целиком, полный текст лицензий движка (скрипт ниже), полный текст SIL OFL 1.1 (можно взять из
   `../../games/hush-till-six/web/licenses/OFL-Oswald.txt`, начиная со строки «This Font Software is licensed…») и текст CC0
   по желанию (`../../games/hush-till-six/web/licenses/CC0-1.0.txt`).
3. **Страница магазина:** блок Credits — по желанию; раскрытие ИИ — для Steam обязательно (§4.3).

Текст лицензий движка в файл (проверено на 4.7.2, около 90 КБ):
```gdscript
# godot --headless --script dump_licenses.gd -- ENGINE_LICENSES.txt
extends SceneTree
func _init():
	var s := "This game uses Godot Engine, available under the following license:\n\n" + Engine.get_license_text()
	s += "\n\nThird-party components of Godot Engine:\n"
	for c in Engine.get_copyright_info():
		for p in c.parts:
			s += "\n%s\n  Copyright: %s\n  License: %s\n" % [c.name, "; ".join(PackedStringArray(p.copyright)), p.license]
	var lic := Engine.get_license_info()
	for k in lic:
		s += "\n\n===== %s =====\n\n%s\n" % [k, lic[k]]
	FileAccess.open(OS.get_cmdline_user_args()[0], FileAccess.WRITE).store_string(s)
	quit()
```

## 4. Титры (готовый текст)

**Обязательно** (требуют лицензии): Godot (MIT) и компоненты движка, строка FreeType (лицензия FTL требует
упоминания в документации), логотип Godot (CC BY 4.0: стандартная заставка Godot при запуске в `project.godot` не
отключена), копирайты шрифтов вместе с текстом OFL. **По просьбе автора:** строка CMU. **По желанию:** остальное.
Строку об олене добавить после выяснения (§5 п. 1).

### 4.1 EN (в игре и в магазинах)
```
WHAT THE LAKE KEEPS
© 2026 [правообладатель]. All rights reserved.

Made with Godot Engine (godotengine.org).
This game uses Godot Engine, available under the following license:
Copyright (c) 2014-present Godot Engine contributors (see AUTHORS.md).
Copyright (c) 2007-2014 Juan Linietsky, Ariel Manzur.
[full MIT license text from Engine.get_license_text()]
Portions of this software are copyright © 1996-2025 The FreeType Project (https://freetype.org). All rights reserved.
Godot Engine logo © 2017 Andrea Calabró, CC BY 4.0.
Licenses of the other third-party components of Godot Engine: see LICENSES.txt.

Fonts (SIL Open Font License 1.1, full text in LICENSES.txt):
Inter — Copyright 2016 The Inter Project Authors (https://github.com/rsms/inter)
Pangolin — Copyright 2016 The Pangolin Project Authors (https://github.com/googlefonts/pangolin)
IBM Plex Mono — Copyright 2017 IBM Corp.
Lobster — Copyright 2010 The Lobster Project Authors (https://github.com/impallari/The-Lobster-Font), with Reserved Font Name "Lobster"
Oswald — Copyright 2016 The Oswald Project Authors (https://github.com/googlefonts/OswaldFont)
PT Sans, PT Sans Narrow — Copyright 2009 ParaType Ltd.
Caveat — Copyright 2014 The Caveat Project Authors (https://github.com/googlefonts/caveat)

Characters made with MakeHuman / MPFB2 and MakeHuman community assets (CC0):
MakeHuman team (Data Collection AB, Joel Palmius, Jonas Hauquier), Mika Suominen, Margaret Toigo,
Cortu Johnstone, culturalibre, frankyaye, Namuhekam.
Textures, props and sky: Poly Haven (polyhaven.com), CC0.
Animations: Quaternius, Universal Animation Library (quaternius.com), CC0.
Motion capture: Rokoko free mocap packs (rokoko.com).
The data used in this project was obtained from mocap.cs.cmu.edu.
The database was created with funding from NSF EIA-0196217.
Voices: ElevenLabs; Qwen3-TTS (Apache-2.0).
Sound effects: ElevenLabs; synthesized in code. Music: composed and synthesized in code.
```

### 4.2 RU (русская версия титров)
```
WHAT THE LAKE KEEPS
© 2026 [правообладатель]. Все права защищены.

Движок — Godot Engine (godotengine.org), лицензия MIT:
Copyright (c) 2014-present Godot Engine contributors (see AUTHORS.md).
Copyright (c) 2007-2014 Juan Linietsky, Ariel Manzur.
Portions of this software are copyright © 1996-2025 The FreeType Project (https://freetype.org). All rights reserved.
Логотип Godot Engine © 2017 Andrea Calabró, CC BY 4.0.
Полные тексты лицензий движка и его компонентов — в файле LICENSES.txt.

Шрифты (SIL Open Font License 1.1): Inter, Pangolin, IBM Plex Mono, Lobster, Oswald, PT Sans, PT Sans Narrow, Caveat —
копирайты и текст лицензии в LICENSES.txt.
Персонажи — MakeHuman / MPFB2 и ассеты сообщества MakeHuman (CC0): MakeHuman team (Data Collection AB, Joel Palmius,
Jonas Hauquier), Mika Suominen, Margaret Toigo, Cortu Johnstone, culturalibre, frankyaye, Namuhekam.
Текстуры, предметы и небо — Poly Haven (polyhaven.com), CC0.
Анимации — Quaternius, Universal Animation Library (quaternius.com), CC0.
Захват движений — бесплатные паки Rokoko (rokoko.com).
The data used in this project was obtained from mocap.cs.cmu.edu.
The database was created with funding from NSF EIA-0196217.
Голоса — ElevenLabs; Qwen3-TTS (Apache-2.0). Звуки — ElevenLabs и синтез кодом. Музыка — своя, синтез кодом.
```

### 4.3 Раскрытие ИИ (Steam — обязательно, itch.io — то же в вопросе про ИИ-контент)
Steam просит описать всё, что сделано с помощью ИИ при разработке (арт, звук, код, текст): анкета контента в
Steamworks, описание показывается на странице игры. Черновик (формулировку о тексте и коде согласовать с владельцем:
по репозиторию сюжет, реплики и код сделаны в чатах Claude по заданиям владельца и редактора):
```
Pre-generated AI content: character voices were generated with text-to-speech (ElevenLabs; Qwen3-TTS run locally).
Most sound effects were generated with ElevenLabs Sound Effects; the other sounds and all music are synthesized in code.
The deer 3D model was generated from an image with Hunyuan 3D (via Figma Weave), then rigged and animated in code.
The story, dialogue and game code were developed with the help of an AI assistant (Claude) under the direction and
editing of the team. No generative AI is used while the game is running.
```

## 5. Нужно выяснить

1. **Олень** (`assets/models/deer.glb` и его текстуры, п. 16) — лицензия результата в репозитории не записана. Модель
   сделана в Figma Weave нейросетью Hunyuan 3D 2.1 по картинке `tools/src/deer_ref.jpg`. Выяснить:
   (а) условия Figma для результатов Weave (можно ли продавать, чьи права);
   (б) лицензию Tencent Hunyuan 3D 2.1: в том числе территориальное ограничение (ЕС, Великобритания, Южная Корея) и
   ограничения на использование результатов, и применяются ли они к результату через Weave;
   (в) откуда `deer_ref.jpg`: метаданных в файле нет; если это чужое фото — нельзя, если картинка сгенерирована — то
   чем и на каких условиях.
   Если не подтвердится — заменить оленя до выпуска (своя модель кодом в Blender, как машина, или CC0).
2. **Волосы и очки Хейла** — `culturalibre_hair_02` (Хейл, молодой Хейл и полицейский `dayguard` из HTS) и
   `frankyaye_glasses_library_male` (Хейл, молодой Хейл), `chars.json:813`, `:817`, `:1231`, `:1235`. Оба из пакетов,
   которые репозиторий считает CC0 (hair01, glasses01 — `chars.json:2`; локальный архив пакета —
   `/home/user/tools/mhpacks/glasses01_cc0.zip`). Но в шапке самих файлов стоит шаблонная строка MakeClothes
   «license AGPL3» с автором «MHteam» / «Unknown». Проверить лицензию этих двух ассетов на сайте MakeHuman и сохранить
   ссылку; если не CC0 — заменить на CC0. Автора очков (frankyaye) дописать в CREDITS.
3. **Голос Дэнни в главах 2–5** — это клон Qwen3-TTS, а образец `tools/refs/danny_{ru,en}.wav` собран из
   реплики ElevenLabs `s5_2` (голос «Teddy» из библиотеки ElevenLabs; `voice_qwen.py:81–85`, `voice.py:21`).
   Проверить, разрешают ли условия ElevenLabs и библиотеки голосов брать их результат образцом для клонирования
   другой моделью. Если нет — переозвучить Дэнни голосом по описанию (с «да» владельца).
4. **ElevenLabs: подтверждение тарифа.** В репозитории записано «платный тариф владельца» (`sfx.json:2`,
   `voice.py:2`, коммит `440646b`), но нет подтверждения подписки на даты генерации (31 окт для звуков и голосов).
   На бесплатном тарифе результат только некоммерческий (HANDOFF, «Игра должна быть целиком владельца»). Сохранить
   чек или скрин подписки. Там же проверить условия для голосов из библиотеки («Teddy», «Alex») в продаваемой игре.
5. **MiniMax через Figma Weave.** В репозитории есть план озвучки (`tools/vo_{ru,en}_mm.json`, `voice.py:98–100`).
   По HANDOFF генерации не было: бесплатные запуски Weave ушли на оленя, остальные голоса — Qwen3-TTS. Подтвердить, что
   в `snd/vo` таких реплик нет.
6. **Шрифты.**
   (а) Lobster: в `LICENSE-signs.txt` (его пишет `fetch_assets.py:91`) — «Copyright 2010 Pablo Impallari», а в самом
   файле шрифта — «Copyright 2010 The Lobster Project Authors (…), with Reserved Font Name "Lobster"». В титры идёт
   строка из шрифта (как в §4), `fetch_assets.py` поправить.
   (б) Шрифты интерфейса — урезанные подмножества (pyftsubset → woff2), а это по OFL «изменённые версии». Если в
   лицензии шрифта есть зарезервированное имя (Reserved Font Name), изменённой версии его оставлять нельзя.
   В `LICENSE.txt` RFN не указаны: сверить с оригинальными OFL.txt Inter, Pangolin и в первую очередь IBM Plex Mono.
   Если RFN есть — положить полный шрифт без изменений или переименовать подмножество.
7. **Иконка** `godot/icon.png` — происхождение не записано: генератора в репозитории нет, по виду это простые
   фигуры (луна, ели, прорубь). Записать, чем сделана; если ИИ или чужая — заменить.
8. **Устаревший комментарий** `tools/build_chars.sh:4–5` называет пакеты `shirts02`, `pants02`, `hats03`,
   `equipment03`. Их нет в списке CC0 (`chars.json:2`) и среди установленных пакетов MPFB, и в нынешних героях они не
   используются. Поправить комментарий, чтобы при пересборке не взять ассет не из CC0.

---
Как обновлять: новый сторонний ассет — сначала строка в §1 (и в `fetch_assets.py`, если он скачивается), потом в §4.
