# Hush Till Six — страница на itch.io (что куда вписать)

Файлы: `dist/itch/hush-till-six-demo-web.zip` (бесплатная часть в браузере), `dist/itch/hush-till-six-full.zip` (полная игра для браузера без интернета, любая ОС),
`HushTillSix-Windows-v<версия>.zip` и `HushTillSix-Linux-v<версия>.zip` (ПК-версия без браузера, `tools/desktop/build.js win linux`), `dist/itch/cover.png`, `dist/itch/screens/*.png`
(собрать: `python3 tools/itch/build.py`; обложка — `python3 tools/itch/og.py КАДР ШРИФТ`, см. шапку og.py; скриншоты — настоящая игра в «Кино» 1280×720).
Загружает владелец со своего аккаунта itch.io (ключа API у нас нет).

## Настройки проекта (Upload new project / Edit game)
- **Title:** Hush Till Six
- **Project URL:** `hush-till-six`
- **Short description or tagline:** A night-shift horror. Don't answer. Don't open. Wait for six. Free first part in your browser.
- **Classification:** Games · **Kind of project:** HTML
- **Release status:** Released
- **Pricing:** Paid — **$3** (галочка «Allow people to pay more» по желанию)
- **Uploads** (старые файлы удалить):
  1. `hush-till-six-demo-web.zip` — галочки **This file will be played in the browser** и **This file is a demo and can be downloaded for free**: бесплатная часть до 03:30, в конце — экран «Full game — $3» со ссылкой на эту страницу (кнопка Buy Now)
  2. `hush-till-six-full.zip` — платный файл, платформы Windows, macOS, Linux (внутри — вся игра, открывается в браузере без интернета); название «Full game — offline in browser (any OS)»
  3. `HushTillSix-Windows-v<версия>.zip` — Windows, «Full game — Windows»
  4. `HushTillSix-Linux-v<версия>.zip` — Linux, «Full game — Linux»
- **Адрес страницы — именно `hush-till-six`:** кнопка в игре открывает `https://ВАШ_НИК.itch.io/hush-till-six`. Другой адрес — пересобрать: `python3 tools/itch/build.py '$3' 'https://…'`
- **Embed options:** Embed in page, Viewport 1280 × 720 · **Mobile friendly** (Orientation: Default) · **Fullscreen button**
- **Genre:** Interactive Fiction
- **Tags:** horror, psychological-horror, interactive-fiction, multiple-endings, choices-matter, story-rich, atmospheric, 3d, singleplayer, creepy
- **AI generation disclosure:** голоса и звуки — ElevenLabs (сгенерированы); картинок, созданных ИИ, нет (3D — модели CC0 и код); текст и код писались с помощью Claude — как отметить, решает владелец
- **Average session:** A few hours · **Languages:** 13 (English, Russian, Spanish, Portuguese, German, Ukrainian, French, Italian, Polish, Turkish, Japanese, Korean, Chinese) · **Inputs:** Mouse, Keyboard, Touchscreen, Xbox controller · **Accessibility:** Subtitles
- **Cover image:** `cover.png` (630 × 500) · **Screenshots:** `screens/01…06` · **Trailer:** ссылка на ролик YouTube
- **Visibility:** сначала Draft → проверить страницу (демо запускается, звук, полный экран, список загрузок) → Public
- **Деньги:** Settings → Payment settings (куда платить) и налоговая анкета (W-8BEN для не-США) — без неё itch не выплачивает

## Description (EN)

**Don't answer. Don't open. Wait for six.**

You take the night shift at the Horizon business center — an empty office tower that closed a month ago. The guard before you, Frank, sat in that booth for twenty years. A week ago he never handed over his shift: the lamp was on, the tea had gone cold, and Frank was gone. The cameras didn't catch a thing.

The day guard hands you the keys with a shaking hand and tells you to read the logbook before three. On the last page, in Frank's handwriting:

*DON'T ANSWER TO YOUR NAME. DON'T OPEN THE DOOR TO IT. DON'T GO TO IT YOURSELF — NOT TO THE LIGHT, NOT IN THE ELEVATOR.*

At three in the morning your little sister Lily is standing outside the glass doors. In the rain. Calling your name.

**Play the first part free right here in your browser** — the shift until 3:30 a.m., about 45 minutes. **The full game ($3)** is the whole night till six: what happens at the glass door, the basement, two more hours of the shift and four endings. Download it for Windows or Linux, or as an offline browser version for any system, including macOS.

**Features**
- A 2–3 hour interactive horror story with timed choices, four endings and 14 ways to die
- Between story scenes you work the shift: watch the CCTV cameras, log anything wrong, run the elevator and the lights — and don't stare at CAM 13
- Voiced in English, Russian, Spanish, Portuguese and German; subtitles in 13 languages (also Ukrainian, French, Italian, Polish, Turkish, Japanese, Korean and Chinese)
- Three looks: **Retro** (low-poly PS1 style), **Cinema** and **Ultra**
- Plays on PC or phone, portrait or landscape; mouse, keyboard, touch or gamepad (Steam Deck friendly). Headphones recommended
- Original music composed in code

**Content warning:** 18+. Violence, blood and gore, jump scares, flashing lights (there is a "fewer flashes" option in the settings).

**Credits:** story, code, scenes and music — original. Characters built with MakeHuman (CC0), animations by Quaternius (CC0), motion capture from Rokoko free packs and the CMU Motion Capture Database, three.js (MIT), Oswald and Noto Sans CJK fonts (OFL). Voices and sound effects made with ElevenLabs.

## Описание (RU)

**Не отвечай. Не открывай. Дождись шести.**

Ты выходишь в ночную смену в бизнес-центр «Горизонт» — пустую башню, закрытую месяц назад. Охранник до тебя, Фрэнк, двадцать лет просидел в этой будке. Неделю назад он не сдал смену: лампа горит, чай остыл, Фрэнка нет. Камеры ничего не записали.

Сменщик отдаёт тебе ключи дрожащей рукой и велит прочитать журнал до трёх. На последней странице — почерком Фрэнка:

*НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.*

В три часа ночи за стеклянной дверью стоит твоя младшая сестра Лили. Под дождём. И зовёт тебя по имени.

**Первая часть — бесплатно прямо здесь, в браузере:** смена до 03:30, минут 45. **Полная версия ($3)** — вся ночь до шести: что будет у стеклянной двери, подвал, ещё два часа смены и четыре концовки. Скачай для Windows или Linux — или версию для браузера без интернета (любая система, в том числе macOS).

- Интерактивный хоррор на 2–3 часа: выборы на время, четыре концовки и 14 смертей
- Между сюжетными сценами — смена: камеры наблюдения, журнал, лифт и свет на этажах; на CAM 13 не засматривайся
- Озвучка на русском, английском, испанском, португальском и немецком; субтитры на 13 языках
- Три графики: «Ретро» (в духе PS1), «Кино» и «Ультра»
- На ПК и телефоне, вертикально и горизонтально; мышь, клавиатура, касание или геймпад (Steam Deck). Лучше в наушниках
- Своя музыка, написанная кодом

**Внимание:** 18+. Насилие, кровь, скримеры, мигающий свет (в настройках есть «меньше вспышек»).
