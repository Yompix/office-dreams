# Hush Till Six — страница на itch.io (что куда вписать)

Файлы: `dist/itch/hush-till-six-demo-web.zip` (бесплатная часть в браузере), `dist/itch/hush-till-six-full.zip` (полная игра, платно), `dist/itch/cover.png`, `dist/itch/screens/*.png`
(собрать: `python3 tools/itch/build.py`; обложка — `python3 tools/itch/og.py КАДР ШРИФТ`, см. шапку og.py; скриншоты — настоящая игра в «Кино» 1280×720).

## Настройки проекта (Create new project)
- **Title:** Hush Till Six
- **Project URL:** `hush-till-six`
- **Short description or tagline:** A night-shift horror. Don't answer. Don't open. Wait for six. Free first part in your browser.
- **Classification:** Games · **Kind of project:** HTML
- **Release status:** Released
- **Pricing:** Paid — **$3** (галочка «Allow people to pay more» по желанию)
- **Uploads:**
  1. `hush-till-six-demo-web.zip` — галочки **This file will be played in the browser** и **This file is a demo and can be downloaded for free**: бесплатная часть до 03:30, в конце — экран «Full game — $3» со ссылкой на эту страницу (кнопка Buy Now)
  2. `hush-till-six-full.zip` — платный файл, платформы Windows, macOS, Linux (внутри — вся игра, открывается в браузере без интернета)
- **Адрес страницы — именно `hush-till-six`:** кнопка в игре открывает `https://ВАШ_НИК.itch.io/hush-till-six`. Другой адрес — пересобрать: `python3 tools/itch/build.py '$3' 'https://…'`
- **Embed options:** Viewport 1280 × 720 · **Mobile friendly** (Orientation: Default) · **Fullscreen button** · **Click to launch in fullscreen** (на телефоне)
- **Genre:** Interactive Fiction
- **Tags:** horror, psychological-horror, interactive-fiction, multiple-endings, choices-matter, story-rich, atmospheric, 3d, singleplayer, creepy
- **AI generation disclosure:** голоса и звуки — ElevenLabs (сгенерированы); картинок, созданных ИИ, нет (3D — модели CC0 и код); текст и код писались с помощью Claude — как отметить, решает владелец
- **Average session:** A few hours · **Languages:** English, Russian · **Inputs:** Mouse, Keyboard, Touchscreen · **Accessibility:** Subtitles
- **Cover image:** `cover.png` (630 × 500) · **Screenshots:** `screens/01…06`

## Description (EN)

**Don't answer. Don't open. Wait for six.**

You take the night shift at the Horizon business center — an empty office tower that closed a month ago. The guard before you, Frank, sat in that booth for twenty years. A week ago he never handed over his shift: the lamp was on, the tea had gone cold, and Frank was gone. The cameras didn't catch a thing.

The day guard hands you the keys with a shaking hand and tells you to read the logbook before three. On the last page, in Frank's handwriting:

*DON'T ANSWER TO YOUR NAME. DON'T OPEN THE DOOR TO IT. DON'T GO TO IT YOURSELF — NOT TO THE LIGHT, NOT IN THE ELEVATOR.*

At three in the morning your little sister Lily is standing outside the glass doors. In the rain. Calling your name.

**Play the first part free right here in your browser** — the shift until 3:30 a.m., about 45 minutes. **The full game ($3)** is the whole night till six: what happens at the glass door, the basement, two more hours of the shift and four endings. It's a download that plays offline in your browser (Windows, macOS, Linux).

**Features**
- A 2–3 hour interactive horror story with timed choices, four endings and a dozen ways to die
- Between story scenes you work the shift: watch the CCTV cameras, log anything wrong, run the elevator and the lights — and don't stare at CAM 13
- Fully voiced in English and Russian, with subtitles
- Two looks: **Retro** (low-poly PS1-style) and **Cinema**
- Plays right in your browser — on PC or phone, portrait or landscape. Headphones recommended
- Original music composed in code

**Content warning:** 18+. Violence, blood and gore, jump scares, flashing lights.

**Credits:** story, code, scenes and music — original. Characters built with MakeHuman (CC0), animations by Quaternius (CC0), three.js (MIT), Oswald font (OFL). Voices and sound effects made with ElevenLabs.

## Описание (RU)

**Не отвечай. Не открывай. Дождись шести.**

Ты выходишь в ночную смену в бизнес-центр «Горизонт» — пустую башню, закрытую месяц назад. Охранник до тебя, Фрэнк, двадцать лет просидел в этой будке. Неделю назад он не сдал смену: лампа горит, чай остыл, Фрэнка нет. Камеры ничего не записали.

Сменщик отдаёт тебе ключи дрожащей рукой и велит прочитать журнал до трёх. На последней странице — почерком Фрэнка:

*НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.*

В три часа ночи за стеклянной дверью стоит твоя младшая сестра Лили. Под дождём. И зовёт тебя по имени.

**Первая часть — бесплатно прямо здесь, в браузере:** смена до 03:30, минут 45. **Полная версия ($3)** — вся ночь до шести: что будет у стеклянной двери, подвал, ещё два часа смены и четыре концовки. Скачивается и идёт в браузере без интернета (Windows, macOS, Linux).

- Интерактивный хоррор на 2–3 часа: выборы на время, четыре концовки и дюжина смертей
- Между сюжетными сценами — смена: камеры наблюдения, журнал, лифт и свет на этажах; на CAM 13 не засматривайся
- Полная озвучка на русском и английском, субтитры
- Две графики: «Ретро» (в духе PS1) и «Кино»
- Играется прямо в браузере — на ПК и телефоне, вертикально и горизонтально. Лучше в наушниках
- Своя музыка, написанная кодом

**Внимание:** 18+. Насилие, кровь, скримеры, мигающий свет.
