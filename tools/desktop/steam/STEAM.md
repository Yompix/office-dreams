# Hush Till Six в Steam — что делать по шагам

Здесь всё, чтобы выпустить игру в Steam. Часть уже сделана (сборки, достижения, геймпад, картинки, тексты), часть — только вы сами: аккаунт, оплата, загрузка.

> **Пароль от Steam и коды Steam Guard никому не давайте — ни Claude, ни «помощникам».** Claude они не нужны. Нужны только номера приложения и депо (App ID, Depot ID) — они не секретные, их можно прислать в чат.

## Что уже готово

- **Сборки для Steam:** папка `steam-windows` (Windows) и `steam-linux` (Linux/SteamOS) — содержимое двух депо. Собирает Claude: `node tools/desktop/build.js win linux --steam --out ПАПКА`.
- **Достижения** — 21 штука (таблица ниже). Игра открывает их сама, когда игрок дошёл до концовки или нашёл смерть. Без Steam игра работает так же, просто без достижений.
- **Геймпад и Steam Deck:** Xbox, PlayStation, Steam Deck — всё меню, выборы, смена, мини-игры, пауза. Текст читается на экране Deck 1280×800.
- **Сохранения в облаке (Steam Cloud):** игра пишет копию сохранений в файл `cloud/save.json` — Steam переносит его на другой компьютер (настройка — шаг 3).
- **Картинки** для страницы и библиотеки — нарисованы движком игры, без ИИ (список в шаге 4).
- **Тексты** страницы на английском и русском (шаг 3).

## Шаг 1. Аккаунт Steamworks (делаете вы)

1. Откройте https://partner.steamgames.com → **Join Steamworks** (Присоединиться) и войдите своим аккаунтом Steam. Steam Guard в аккаунте должен быть включён.
2. Заполните анкету: имя или название (как вы оформлены: физлицо, ИП, компания), адрес, **банковские реквизиты** для выплат, **налоговую анкету** (для физлиц не из США — форма W-8BEN, заполняется прямо на сайте, по шагам), подтверждение личности.
3. Оплатите **Steam Direct — $100 за игру.** Деньги вернутся с продаж, когда игра заработает $1000.
4. После оплаты Steam создаст приложение и даст **App ID** (число, например 3456780). Выпустить игру можно не раньше чем через 30 дней после оплаты, а страница «Скоро выйдет» должна провисеть минимум 2 недели.

## Шаг 2. Депо и запуск (Steamworks → ваше приложение)

1. **SteamPipe → Depots:** создайте два депо — «Hush Till Six Windows» (OS: Windows) и «Hush Till Six Linux» (OS: Linux + SteamOS). Запишите их номера (обычно App ID + 1 и + 2).
2. **Installation → General → Launch Options** — два варианта запуска:
   - Executable: `Hush Till Six.exe`, Operating System: **Windows**.
   - Executable: `hush-till-six`, Arguments: `--no-sandbox`, Operating System: **Linux + SteamOS**.
3. **Installation → Linux Runtime:** «Steam Linux Runtime 3.0 (sniper)».
4. После любых изменений: **Publish → Prepare for Publishing → Publish to Steam**.

## Шаг 3. Страница в магазине (Store Page Admin)

**Название:** Hush Till Six (на всех языках одинаково).

**Короткое описание (EN):** A night-shift horror story. Watch the cameras, follow the rules, and whatever calls your name — don't answer. Four endings, fourteen ways to die. Wait for six.

**Короткое описание (RU):** Хоррор про ночную смену. Смотри камеры, соблюдай правила и не отзывайся, если тебя зовут по имени. Четыре концовки, четырнадцать смертей. Дождись шести.

**Об игре (EN):**

> **Don't answer. Don't open. Wait for six.**
>
> You take the night shift at the Horizon business center — an empty office tower that closed a month ago. The guard before you, Frank, sat in that booth for twenty years. A week ago he never handed over his shift: the lamp was on, the tea had gone cold, and Frank was gone. The cameras didn't catch a thing.
>
> The day guard hands you the keys with a shaking hand and tells you to read the logbook before three. On the last page, in Frank's handwriting: *DON'T ANSWER TO YOUR NAME. DON'T OPEN THE DOOR TO IT. DON'T GO TO IT YOURSELF — NOT TO THE LIGHT, NOT IN THE ELEVATOR.*
>
> At three in the morning your little sister Lily is standing outside the glass doors. In the rain. Calling your name.
>
> - A 2–3 hour interactive horror story with timed choices, four endings and fourteen ways to die
> - Between story scenes you work the shift: watch the CCTV cameras, log anything wrong, run the elevator and the lights — and don't stare at CAM 13
> - Fully voiced in English and Russian, subtitles in Ukrainian
> - Two looks: Cinema and Retro (PS1-style)
> - Full controller support, Steam Deck friendly. Headphones recommended
> - Original music composed in code
>
> **Content warning:** 18+. Violence, blood and gore, jump scares, flashing lights.

**Об игре (RU):**

> **Не отвечай. Не открывай. Дождись шести.**
>
> Ты выходишь в ночную смену в бизнес-центр «Горизонт» — пустую башню, закрытую месяц назад. Охранник до тебя, Фрэнк, двадцать лет просидел в этой будке. Неделю назад он не сдал смену: лампа горит, чай остыл, Фрэнка нет. Камеры ничего не записали.
>
> Сменщик отдаёт тебе ключи дрожащей рукой и велит прочитать журнал до трёх. На последней странице — почерком Фрэнка: *НЕ ОТЗЫВАЙСЯ НА ИМЯ. НЕ ОТКРЫВАЙ ЕМУ ДВЕРЬ. САМ К НЕМУ НЕ ХОДИ — НИ НА СВЕТ, НИ В ЛИФТ.*
>
> В три часа ночи за стеклянной дверью стоит твоя младшая сестра Лили. Под дождём. И зовёт тебя по имени.
>
> - Интерактивный хоррор на 2–3 часа: выборы на время, четыре концовки и четырнадцать смертей
> - Между сюжетными сценами — смена: камеры наблюдения, журнал, лифт и свет на этажах; на CAM 13 не засматривайся
> - Полная озвучка на русском и английском, субтитры на украинском
> - Две графики: «Кино» и «Ретро» (в духе PS1)
> - Полная поддержка геймпада и Steam Deck. Лучше в наушниках
> - Своя музыка, написанная кодом
>
> **Внимание:** 18+. Насилие, кровь, скримеры, мигающий свет.

**Языки:** English — интерфейс, озвучка, субтитры; Русский — интерфейс, озвучка, субтитры; Українська — интерфейс и субтитры.

**Жанры:** Adventure, Indie. **Метки (tags):** Horror, Psychological Horror, Interactive Fiction, Multiple Endings, Choices Matter, Story Rich, Atmospheric, Singleplayer, Mystery, Dark, Gore, Short, 3D.

**Системные требования (Windows):**
- Минимум: Windows 10 64-bit, процессор 2 ядра 2 ГГц, 4 ГБ памяти, видеокарта с WebGL 2 (Intel HD 520 / GeForce GTX 750 и новее), 500 МБ на диске. Графика «Ретро».
- Рекомендуется: Windows 10/11 64-bit, 4 ядра, 8 ГБ памяти, GeForce GTX 1050 / Radeon RX 560 и новее. Графика «Кино».
- Linux / SteamOS: 64-bit, остальное так же.

**Анкета о содержании (Mature Content Survey) — 18+:**
- Frequent Violence or Gore — **да** (кровь, смерти героя, монстр).
- General Mature Content — **да**.
- Nudity / Sexual Content — **нет**.
- Описание (EN): *Graphic violence and blood, death scenes, jump scares and flashing lights.* (RU): *Жестокость и кровь, сцены смерти, скримеры и мигающий свет.*

**Геймпад:** Store Page Admin → «Controller support»: **Full Controller Support**, отметить Xbox и PlayStation. Steamworks → **Steam Input**: «Default configuration» — **Gamepad** (стандартная раскладка), Steam Input API не нужен.

**Steam Cloud** (Steamworks → Application → Steam Cloud):
- Byte quota per user: `1048576`, Number of files per user: `10`.
- **Auto-Cloud**, Root Paths: Root `WinAppDataRoaming`, Subdirectory `Hush Till Six/cloud`, Pattern `*.json`, OS: Windows.
- Root Overrides: Original Root `WinAppDataRoaming` → New Root `LinuxXdgConfigHome`, OS: Linux (путь тот же: `Hush Till Six/cloud`).
- Steam Deck с Windows-сборкой (Proton) берёт путь Windows — отдельно ничего не нужно.

## Шаг 4. Картинки (Store Page Admin → Graphical Assets и Library Assets)

Все картинки нарисованы движком игры (кадры «Кино») и шрифтом игры — ИИ не участвовал. Пересобрать: `node tools/desktop/steam/kit.js ПАПКА frames shots`, затем `python3 tools/desktop/steam/kit.py ПАПКА`.

| Файл | Размер | Куда |
|---|---|---|
| `store/header_capsule.jpg` | 920×430 | Header Capsule |
| `store/small_capsule.jpg` | 462×174 | Small Capsule |
| `store/main_capsule.jpg` | 1232×706 | Main Capsule |
| `store/vertical_capsule.jpg` | 748×896 | Vertical Capsule |
| `library/library_capsule.jpg` | 600×900 | Library Capsule |
| `library/library_hero.jpg` | 3840×1240 | Library Hero (без надписей) |
| `library/library_logo.png` | прозрачный | Library Logo (поставить слева внизу) |
| `screenshots/*_en.png`, `*_ru.png` | 1920×1080 | Screenshots (минимум 5; английские — для всех, русские — для русской страницы) |
| `achievements/*.jpg` | 256×256 | иконки достижений (`*_locked.jpg` — «не получено») |
| `icons/community_icon.jpg` | 184×184 | Community Icon |
| `icons/client_icon.ico` | 16/32 | Client Icon |

Трейлеры у вас уже есть (YouTube 16:9, RU/EN) — их можно загрузить в Store Page Admin → Trailers. В финальной карточке трейлера стоит «Скоро в Steam» — когда будет ссылка, Claude пересоберёт.

## Шаг 5. Достижения (Steamworks → Stats & Achievements → Achievements)

Добавьте 21 достижение **с такими API Name** (их ждёт игра, менять нельзя). «Скрытое» — описание видно только после получения (чтобы не было спойлеров). Описание смерти — та же подсказка, что игра пишет на экране смерти.

| API Name | Название RU / EN | Описание RU / EN | Скрытое |
|---|---|---|---|
| `END_INSIDE` | Внутри / Inside | Концовка «Внутри». / Reach the ending “Inside”. | да |
| `END_SISTER` | Сестра / Sister | Концовка «Сестра». / Reach the ending “Sister”. | да |
| `END_DAWN` | Рассвет / Dawn | Концовка «Рассвет». / Reach the ending “Dawn”. | да |
| `END_NEWENTRY` | Новая запись / New Entry | Концовка «Новая запись». / Reach the ending “New Entry”. | да |
| `DEATH_01` | Ты посмотрел ему в лицо / You Looked It in the Face | Не смотри в тринадцатую камеру. / Don't look at camera thirteen. | да |
| `DEATH_02` | Ты пошёл на свет / You Went to the Light | Сам к нему не ходи — ни на свет, ни в лифт. / Don't go to it yourself — not to the light, not in the elevator. | да |
| `DEATH_03` | Ты отозвался / You Answered the Radio | Гас не знал твоего имени. А оно знает. / Gus didn't know your name. It does. | да |
| `DEATH_04` | Оно обернулось / It Turned Around | Тринадцатая включилась сама — выдёргивай шнур, не смотри. / Thirteen turned on by itself — pull the cord, don't look. | да |
| `DEATH_05` | Оно услышало / It Heard You | Оно ищет на звук. Пока шаги рядом — не дыши. / It hunts by sound. While the footsteps are near — don't breathe. | да |
| `DEATH_06` | Темнота догнала / The Dark Caught Up | Остановишься — догонит. / Stop, and it catches you. | да |
| `DEATH_07` | Ты пошёл на зов / You Followed the Call | Оно умеет звать. Стучать — нет. / It knows how to call. Not how to knock. | да |
| `DEATH_08` | Фрэнк ушёл на голос / Frank Went to the Voice | Держи его, пока голос не стихнет. / Hold him until the voice fades. | да |
| `DEATH_09` | Ты ответил / You Answered the Call | Свои до утра подождут. / Your own can wait till morning. | да |
| `DEATH_10` | Оно пришло / It Came | Три странности за час остались без внимания. / Three anomalies in an hour went unnoticed. | да |
| `DEATH_11` | Ты засмотрелся / You Stared Too Long | Там, где помехи, кто-то стоит. / Someone is standing where the static is. | да |
| `DEATH_12` | Ты назвался / You Said Your Name | Оно знает твоё имя. Ему нужно только, чтобы ты ответил. / It knows your name. All it needs is for you to answer. | да |
| `DEATH_13` | Ты открыл ей / You Let Her In | Посторонних не впускать. / No unauthorized visitors. | да |
| `DEATH_14` | Ты взял трубку / You Picked Up | Звонили не тебе. Но ответил — ты. / The call wasn't for you. But you're the one who answered. | да |
| `ALL_ENDINGS` | Все концовки / Every Ending | Открой все четыре концовки. / Unlock all four endings. | нет |
| `ALL_DEATHS` | Четырнадцать способов умереть / Fourteen Ways to Die | Найди все 14 смертей. / Find all 14 deaths. | нет |
| `NIGHT_DONE` | Конец смены / End of Shift | Доживи до утра — до любой концовки. / Make it through the night to any ending. | нет |

Иконки: `achievements/<API Name>.jpg` (получено) и `achievements/<API Name>_locked.jpg` (не получено). После добавления — **Publish**.

Номер смерти — её место в списке смертей игры (`S.overs` в `story.js`); если список изменится, таблицу и `ACH` в `tools/desktop/app/main.js` нужно поправить вместе.

## Шаг 6. Загрузить сборку (делаете вы, со своего компьютера)

1. Скачайте **Steamworks SDK** (partner.steamgames.com → SDK → Download) и распакуйте.
2. В папке `sdk\tools\ContentBuilder\`:
   - в `scripts\` положите `app_build.vdf`, `depot_build_windows.vdf`, `depot_build_linux.vdf` из этой папки и замените в них номера `1234560` (App ID), `1234561` (депо Windows), `1234562` (депо Linux) на свои — или пришлите номера Claude, он пришлёт готовые файлы;
   - в `content\windows\` — всё содержимое папки `steam-windows`, в `content\linux\` — содержимое `steam-linux`.
3. Запустите `ContentBuilder\builder\steamcmd.exe`. В его окне наберите `login ВАШ_ЛОГИН` — пароль и код Steam Guard вводите **сами** в этом окне. Потом: `run_app_build ..\scripts\app_build.vdf`, дождитесь «Success», затем `quit`.
   (Можно и без команд: в SDK есть **SteamPipe GUI** — `sdk\tools\SteamPipeGUI.zip`: указать App ID, депо, папки и нажать Upload.)
4. Steamworks → **SteamPipe → Builds**: появится новая сборка. Сначала поставьте её на ветку `beta` или проверьте как разработчик: в своей библиотеке Steam установите игру и проверьте — запуск, звук, геймпад, достижение (умереть один раз — должно всплыть уведомление), Shift+Tab (оверлей), выход и повторный вход (сохранения).
5. Всё хорошо — **Set build live** на ветке `default`.

Для проверки у себя без загрузки: положите рядом с `Hush Till Six.exe` файл `steam_appid.txt` с вашим App ID, запустите Steam, затем игру. В папке `%APPDATA%\Hush Till Six` появится `steam.log` — там написано, подключился ли Steam. Перед загрузкой этот файл не нужен (сборщик его и так пропустит).

## Шаг 7. Steam Deck

Steam проверяет игры на Deck сам (Steamworks → **Steam Deck Compatibility** → можно запросить проверку). Что проверяют и что у нас:
- **Управление:** всё работает с геймпада — меню, выборы, смена (LB/RB — камеры, X — записать, Y — пульт, View — журнал), мини-игры, пауза (Start). На экране геймпада подсказки и рамка выбора.
- **Экран:** 1280×800 — текст читается, интерфейс помещается (проверено снимками).
- **Без лишнего:** нет лаунчера, сразу игра на весь экран; клавиатура не нужна.
- **Система:** Windows-сборка идёт через Proton, есть и родная Linux-сборка.

Если на Deck будет тормозить «Кино» — игра сама предложит «Ретро».

## Шаг 8. Цена и выпуск

- **Цена:** на itch.io игра стоит $3. В Steam для истории на 2–3 часа обычно ставят **$3.99–4.99**; региональные цены Steam подставит сам (для России, Украины, Турции и т. д. дешевле). На первую неделю — скидка 10–20 % (Steamworks → Discounts).
- **Порядок:** заполнить страницу → отправить её на проверку (2–5 рабочих дней) → **Coming Soon** (минимум 2 недели, собирает «желаемое») → загрузить сборку и отправить на проверку → в день выхода нажать **Release**.
- Можно сделать **бесплатную демо-версию** в Steam (отдельное приложение «Hush Till Six Demo», та же логика, что на itch: до 03:30) — скажите Claude, он соберёт.

## Что прислать Claude

- App ID и номера двух депо (не секрет) — Claude впишет их в `*.vdf` и поменяет в трейлере «Скоро в Steam» на ссылку.
- Решения: цена, дата выхода, нужна ли демо-версия.
- **Не присылать:** пароль, коды Steam Guard, данные банка и налоговые формы — это только вы и только на сайте Steam.

## Для Claude: сборка и проверка

```
cd tools/desktop && npm ci
node build.js win --out OUT            # OUT/Hush Till Six/ + HushTillSix-Windows-v1.0.0.zip + «Hush Till Six Setup 1.0.0.exe» (itch.io и сайт)
node build.js linux --out OUT          # OUT/hush-till-six-linux/ + HushTillSix-Linux-v1.0.0.zip
node build.js win linux --steam --out OUT   # OUT/steam-windows, OUT/steam-linux — содержимое депо Steam (со steamworks.js)
node build.js linux --test --steam --out T && xvfb-run -a -s "-screen 0 1920x1200x24" node check.js T/steam-linux SHOTS
```

- Steam включается, только если игру запустил Steam (переменная `SteamAppId`) или рядом с .exe лежит `steam_appid.txt`; иначе — обычная игра. Ошибка подключения — в `steam.log` профиля.
- Достижения: игра (`engine.js`) зовёт `DESK.ach('END_…' / 'DEATH_NN' / 'NIGHT_DONE')`; «все концовки» и «все смерти» считает `main.js` по своей копии `achievements.json` (полученные без Steam досылаются при первом запуске со Steam).
- Сохранения: `preload.js` копирует `ns1_*` из localStorage в `cloud/save.json` (раз в 15 с и при выходе); при запуске файл новее своей последней записи — берётся он (пришёл через Steam Cloud).
