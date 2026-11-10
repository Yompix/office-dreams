# LANTERN — соответствие правилам Mojang/Microsoft и Google Play

Сводка решений для версии 1.5.0 (ноябрь 2026). Источники: Minecraft Usage Guidelines (https://www.minecraft.net/usage-guidelines),
Minecraft EULA (https://aka.ms/MinecraftEULA), политика Google Play об интеллектуальной собственности и «Impersonation».

## 1. Название, иконка, магазин
- Название приложения — только **LANTERN** (без «Minecraft», «Craft», «Mine» и т. п.). В заголовке Play — «LANTERN: игра с друзьями по коду».
- Слово Minecraft — только как фактическое описание совместимости: «для игры с друзьями в Minecraft: Java Edition и Bedrock Edition».
- Иконка перерисована (tools/make_art.py): гладкий фонарь своего силуэта, без пиксельных текстур, кубов и блоков.
- Описание проекта (project.godot), описание exe (export_presets.cfg) — без «Minecraft» в названии.

## 2. Обязательная оговорка
«НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С MOJANG ИЛИ MICROSOFT.»
/ «NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.»

Где стоит:
- приложение: «Настройки → О программе» (RU + EN + «Minecraft — товарный знак Mojang Synergies AB»), первый экран приветствия (внизу) — текст в `Cfg.DISCLAIMER`;
- сайт: страница скачивания `/lantern/download`, `/lantern/privacy`, `/lantern/terms`, `/lantern/delete` (relay/legal.js);
- Google Play: последний абзац полного описания (docs/PLAY_RELEASE.md);
- `docs/Прочитай.txt` — положить в zip для Windows.

## 3. Графика и интерфейс
- Удалены `card_java.png`, `card_bedrock.png` (травяные/алмазные блоки — похожи на текстуры Minecraft; к тому же не использовались),
  `mini_grass.png`, `mini_stone.png` (заменены векторными значками LANTERN «компьютер»/«телефон»).
- `scripts/ui/mc_mock.gd` (копировал цвета и вид меню Bedrock: серые кнопки 313233/48494a, зелёный 3c8527) заменён на
  `scripts/ui/menu_hint.gd` (`MenuHint`): схемы в стиле LANTERN — наши цвета/шрифты/скругления, названия меню игры только текстом,
  нужная кнопка — акцентной рамкой и стрелкой. Инструкция «какое меню → какая кнопка» сохранена.
- Шрифты: Montserrat, JetBrains Mono, Noto Sans Symbols 2 (SIL OFL). Шрифтов Minecraft нет.
- Нет логотипов Minecraft/Mojang/Microsoft/Xbox, персонажей (Steve, Alex, Creeper и т. п.), скриншотов игры.
- Примеры в интерфейсе: «Например STEVE» → «LUNA7»; ник тестового профиля для скриншотов — «Ночной_Лис».
  Остаток: id косметики `sticker_creeper` (подпись «БУМ! ✦», рисунок — звезда, не персонаж) живёт на сервере в economy.js и в
  сохранённых покупках игроков; переименовывать id не стали, чтобы не сломать инвентарь. Картинки Creeper нет.

## 4. Пиратство
- Убраны все упоминания TLauncher и «без лицензии». Опция сервера `online-mode` осталась, но названа нейтрально:
  «Проверка аккаунтов: Включена (рекомендуется) / Выключена» — без советов, как играть без покупки.
- Подсказка доктора сервера при ошибке входа больше не предлагает «Пускать игроков TLauncher» — только объясняет, что нужен аккаунт.
- В условиях использования: «Для игры нужна своя законно приобретённая копия Minecraft».

## 5. Файлы Mojang и сторонних проектов
- LANTERN **не распространяет** файлы Minecraft. По запросу пользователя приложение само скачивает с официальных сайтов:
  Paper (fill.papermc.io), Geyser/Floodgate (download.geysermc.org), Java (api.adoptium.net, Eclipse Temurin),
  Bedrock Dedicated Server (net-secondary.web.minecraft-services.net — официальный API Mojang).
- Перед запуском сервера — шаг согласия с Minecraft EULA (и Политикой конфиденциальности Microsoft для Bedrock) с кнопками
  «Прочитать EULA» / «Конфиденциальность»; без галочки сервер не запускается. `eula.txt` пишется только после согласия.
- Relay пересылает игровой трафик и ничего не хранит из него.

## 6. Google Play
- Платные функции (Pro, наборы искр, премиум-путь сезона) и реклама (AdMob) не подключены → в 1.5.0 скрыты флагом
  `Cfg.PAID := false` (код остался; для автотестов `--paid=1`). Искры зарабатываются только в приложении и ничего не стоят.
- Удаление профиля в приложении («Настройки → Профиль и данные → Удалить профиль») + веб-страница `/lantern/delete`.
  Сервер (`delete_account`) стирает пользователя, друзей, заявки, блокировки, ЛС (обе стороны), сообщения в чатах комнат,
  жалобы, экономику, свой код и файлы облачных копий.
- Политика конфиденциальности `/lantern/privacy` и условия `/lantern/terms` (RU + EN). Адрес поддержки — переменная окружения
  `LANTERN_SUPPORT_EMAIL` на Railway (пока не задана, на страницах видно «[TODO: e-mail поддержки]»).
- Чат с незнакомцами (чат комнаты, ЛС с друзьями): фильтр мата и ссылок на сервере (всегда включён), «Пожаловаться» /
  «Заблокировать» в меню сообщения и в карточке друга, лимиты сообщений и заявок.
- Разрешения: INTERNET, ACCESS_NETWORK_STATE, CHANGE_WIFI_MULTICAST_STATE (поиск миров в локальной сети), FOREGROUND_SERVICE +
  FOREGROUND_SERVICE_SPECIAL_USE (служба «мир открыт для друзей»), POST_NOTIFICATIONS, WAKE_LOCK, SYSTEM_ALERT_WINDOW (кнопка поверх игры).
- targetSdk 36, compileSdk 36 (android/build/config.gradle шаблона Godot 4.7).
