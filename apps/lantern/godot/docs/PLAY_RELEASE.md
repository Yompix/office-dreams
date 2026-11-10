# LANTERN 1.5.1 → Google Play: пошагово для владельца

Файлы:
- AAB для Play: `/home/user/office-dreams/godot/playtogether/export/android/LANTERN.aab` (versionCode 7, versionName 1.5.1, targetSdk 36)
- APK для сайта: `.../export/android/LANTERN.apk`
- Графика: `/tmp/claude-0/lantern5/play/` — `icon_512.png`, `feature_1024x500.png`, `phone_*.png` (1080×1920)
- Ключ загрузки (upload key) — тот же, что для 1.4 (`lantern-upload.jks`). Храните его и пароль отдельно, в Play включите «Подписание приложений Google Play».

Перед началом (один раз, на Railway): задать переменную `LANTERN_SUPPORT_EMAIL` = ваш e-mail поддержки
(сейчас на страницах `/lantern/privacy`, `/terms`, `/delete` вместо адреса видно «[TODO: e-mail поддержки]»).

## 1. Создать приложение
Play Console → «Все приложения» → «Создать приложение».
- Название: **LANTERN: игра с друзьями по коду**
- Язык по умолчанию: русский
- Приложение / Игра: **Приложение** · Бесплатное
- Отметить согласия (правила для разработчиков, экспортные законы США).

## 2. Внутреннее тестирование (сначала!)
«Тестирование → Внутреннее тестирование» → «Создать релиз» → загрузить `LANTERN.aab`.
- Название релиза: `1.5.1 (7)`
- Что нового: `Список открытых игр, создание игры с паролем и местами, удаление профиля, новая иконка.`
- «Тестировщики» → создать список (свои Google-аккаунты, до 100) → сохранить → скопировать ссылку для участия и открыть её на телефонах.
- Новые личные аккаунты разработчика (после 13.11.2023): перед «Рабочей версией» нужно **закрытое тестирование: не меньше 12 тестировщиков 14 дней подряд**. Тогда сразу создайте «Закрытое тестирование» с тем же AAB.

## 3. Страница в Google Play («Рост → Основная страница»)
Графика: значок `icon_512.png`, баннер `feature_1024x500.png`, скриншоты телефона `phone_*.png` (4–8 шт.).
Категория: **Инструменты** (или «Социальные»). Теги: мультиплеер, друзья. E-mail — ваш. Сайт: `https://office-dreams-production.up.railway.app/lantern/download`.
Политика конфиденциальности: `https://office-dreams-production.up.railway.app/lantern/privacy`

### RU
Название (≤30): `LANTERN: игра с друзьями по коду`
Краткое описание (≤80): `Играйте с друзьями по коду комнаты — без VPN и настройки роутера.`
Полное описание:
```
LANTERN соединяет вас с друзьями, где бы вы ни были: откройте свой мир — друзья зайдут по короткому коду или из списка игр. Без VPN, без проброса портов и без настройки роутера.

Для игры с друзьями в Minecraft: Java Edition и Bedrock Edition.

• Список открытых игр: режим, версия, связь, места — заходите в одно нажатие
• Создайте игру: название, режим, число мест, пароль, видна всем или только по коду
• Свой мир с телефона: откройте мир в игре — LANTERN найдёт его сам
• На компьютере — ещё и отдельный сервер: LANTERN скачает его с официальных сайтов (после вашего согласия с EULA)
• Кнопка поверх игры: кто зашёл, выгнать, пригласить друзей
• Друзья по тегу, приглашения, чат с фильтром мата и ссылок, жалобы и блокировка
• Без рекламы и без покупок

Для игры нужна своя копия Minecraft. LANTERN не содержит файлов игры.

НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С MOJANG ИЛИ MICROSOFT.
Minecraft — товарный знак Mojang Synergies AB.
```

### UK
Назва: `LANTERN: гра з друзями за кодом`
Короткий опис: `Грайте з друзями за кодом кімнати — без VPN і налаштування роутера.`
Повний опис:
```
LANTERN з'єднує вас із друзями, де б ви не були: відкрийте свій світ — друзі зайдуть за коротким кодом або зі списку ігор. Без VPN, без прокидання портів і без налаштування роутера.

Для гри з друзями в Minecraft: Java Edition і Bedrock Edition.

• Список відкритих ігор: режим, версія, зв'язок, місця — заходьте одним дотиком
• Створіть гру: назва, режим, кількість місць, пароль, видима всім або лише за кодом
• Свій світ із телефона: відкрийте світ у грі — LANTERN знайде його сам
• На комп'ютері — ще й окремий сервер: LANTERN завантажить його з офіційних сайтів (після вашої згоди з EULA)
• Кнопка поверх гри: хто зайшов, вигнати, запросити друзів
• Друзі за тегом, запрошення, чат із фільтром лайки та посилань, скарги й блокування
• Без реклами та без покупок

Для гри потрібна своя копія Minecraft. LANTERN не містить файлів гри.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
НЕ Є ОФІЦІЙНИМ ПРОДУКТОМ MINECRAFT. НЕ СХВАЛЕНО MOJANG АБО MICROSOFT І НЕ ПОВ'ЯЗАНО З НИМИ.
```

### EN
Title: `LANTERN: Play with Friends`
Short: `Play with friends using a room code — no VPN, no router setup.`
Full:
```
LANTERN connects you with friends wherever they are: open your world and friends join with a short code or from the game list. No VPN, no port forwarding, no router setup.

For playing with friends in Minecraft: Java Edition and Bedrock Edition.

• Open game list: mode, version, connection quality, slots — join with one tap
• Create a game: name, mode, slots, optional password, public or code-only
• Host from your phone: open a world in the game — LANTERN finds it
• On PC, also a dedicated server, downloaded from official sites after you accept the EULA
• In-game overlay: who joined, kick, invite friends
• Friends by tag, invites, chat with profanity/link filter, report and block
• No ads, no purchases

You need your own copy of Minecraft. LANTERN does not include any game files.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
Minecraft is a trademark of Mojang Synergies AB.
```

## 4. «Политика → Содержание приложения»
**Политика конфиденциальности:** URL выше.
**Доступ к приложению:** «Все функции доступны без особого доступа» (входа по паролю нет; ник создаётся в приложении).
**Реклама:** «Нет, приложение не содержит рекламы».
**Целевая аудитория:** 13–15, 16–17, 18+ (не для детей до 13). «Привлекает ли детей» — Нет.
**Новостное приложение:** Нет. **Госприложение:** Нет. **Финансовые функции:** Нет. **Здоровье:** Нет.

### Возрастной рейтинг (анкета IARC)
Категория: «Социальные сети / общение» (или «Все остальные типы приложений»), e-mail ваш.
- Насилие, страх, секс, нецензурная лексика, наркотики, азартные игры — **Нет** (в самом приложении такого контента нет).
- Пользователи могут общаться / обмениваться контентом — **Да** (чат с друзьями и в комнате).
- Делится ли местоположение с другими — **Нет**. Покупки цифровых товаров — **Нет**.
- Неограниченный доступ в интернет (браузер) — **Нет**.
Ожидаемо: PEGI 12 / ESRB Teen / «12+» из-за общения пользователей.

### Безопасность данных (Data safety)
- Собирает или передаёт данные? — **Да**. Все данные шифруются при передаче — **Да**. Можно запросить удаление — **Да** (URL `/lantern/delete`).
- Типы данных:
  - **Личная информация → Имя пользователя / ID** («Другие идентификаторы» — ник и тег): собирается, не передаётся третьим лицам; обязательно; цель — функциональность приложения, управление аккаунтом.
  - **Сообщения → Другие сообщения в приложении** (чат): собирается, не передаётся; хранится 24 ч; цель — функциональность.
  - **Действия в приложении → Другой пользовательский контент** (название игры, список друзей, жалобы): функциональность, безопасность/модерация.
  - **Идентификаторы устройства или другие ID** — случайный ключ устройства (хранится только хеш): функциональность, управление аккаунтом.
  - **Файлы и документы** — только при облачных копиях Pro (в 1.5.0 выключено) → **не отмечать**.
- Местоположение, контакты, фото, финансы, здоровье, аналитика, реклама — **не собираются**.
- Обработка эфемерная? — сообщения нет (24 ч), остальное нет.

### Разрешение FOREGROUND_SERVICE_SPECIAL_USE («Разрешения → Сервисы переднего плана»)
Тип: **Особое использование (specialUse)**. Текст обоснования (EN, вставить как есть):
```
LANTERN relays the user's own Minecraft LAN world to the friends they invited, over the internet, while the user is playing in the Minecraft app (LANTERN is in the background). The relay must keep a WebSocket connection and local UDP/TCP sockets alive for the whole play session; if the system stops it, friends are disconnected from the world. The service starts only when the user taps "Open for friends", shows an ongoing notification with the room code and a "Close room" button, and stops when the user closes the room. No other foreground service type fits: it is not media playback, location, data sync or a phone call, but a user-initiated real-time network relay for a game session.
```
Видео-демонстрация (просит Play): запишите экран 30–60 с: LANTERN → «+» → «Создать» → переход в Minecraft → уведомление «Мир открыт для друзей» → друг заходит → «Закрыть комнату». Загрузите на YouTube (по ссылке) и вставьте URL.

### SYSTEM_ALERT_WINDOW
Используется для кнопки поверх игры (кто зашёл, выгнать, пригласить). Запрашивается только по нажатию «Разрешить»; без него всё работает через уведомление. В декларации, если спросят: «Overlay button over the game so the host can see who joined and kick players without leaving the game.»

## 5. Выпуск в рабочую версию
После теста: «Рабочая версия → Создать релиз» → «Добавить из библиотеки» (тот же AAB 6) → страны (Украина, Казахстан и др.; Россия — Play там недоступен для оплаты, но бесплатные приложения публикуются) → «Отправить на проверку». Проверка обычно 1–7 дней.

## 6. Если Play отклонит
- «Impersonation / IP» → убедитесь, что в названии и иконке нет Minecraft (их нет); в ответе приложите ссылку на docs/LEGAL.md-формулировки: неофициальное, оговорка в описании и в приложении, файлов игры нет.
- «Foreground service» → дополните видео и текст выше.
- «Account deletion» → URL `/lantern/delete` и «Настройки → Профиль и данные → Удалить профиль».
