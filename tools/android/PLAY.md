# Hush Till Six → Google Play (что делает владелец, по шагам)

Сборка: `node tools/android/build.js` → `tools/android/out/HushTillSix-v<версия>.aab` (загружать в Play Console) и `.apk` (поставить на свой телефон для проверки).
Значок и баннер: `python3 tools/android/assets.py` → `dist/play/icon-512.png`, `dist/play/feature-1024x500.png`. Скриншоты — `dist/itch/screens/*.png` (1280×720) или новые снимки.

- **Пакет (ID приложения): `com.yompix.hushtillsix`** — после первой загрузки в Play его поменять нельзя.
- **Версия** — как у ПК-версии (`tools/desktop/package.json`), номер сборки 1.2.5 → 10205. Каждая новая загрузка — с номером больше: поднять версию и пересобрать.
- **Android:** минимум 8.0 (API 26), target API 36 (Android 16 — Google Play требует его с 31.08.2026). Нужен OpenGL ES 3.0 (WebGL 2). Разрешение одно — вибрация; интернета у приложения нет.
- **Ключ подписи.** AAB подписан *ключом загрузки* (upload key) `hush-upload.p12` (пароль — в `hush-upload.p12.pass`). Его хранит владелец: в надёжном месте (менеджер паролей, флешка), никому не отправлять. Для следующих сборок в облаке — секреты среды `ANDROID_KEYSTORE_B64` (файл в base64) и `ANDROID_KEYSTORE_PASS`, в чат не присылать. В Play включено «Подписание приложений Google Play»: телефоны получают приложение с ключом Google, а ключ загрузки, если пропадёт, можно заменить через поддержку Play (Setup → App signing → Request upload key reset).

## 1. Аккаунт разработчика
1. https://play.google.com/console — регистрация (разово $25), подтверждение личности по документу и телефону.
2. **Новый личный аккаунт** (создан после 13.11.2023): перед выпуском для всех нужен **закрытый тест — не меньше 12 тестировщиков 14 дней подряд** (Testing → Closed testing; тестировщики — по списку почт Google или группе Google, они ставят игру по ссылке-приглашению). Аккаунт организации (с D-U-N-S) от этого правила освобождён.
3. Платная игра: Setup → Payments profile (платёжный профиль продавца) и налоговые данные — заполняет владелец сам.

## 2. Создать приложение
Create app → App name **Hush Till Six**, Default language English (US), **Game**, Free или Paid (платную потом сделать бесплатной можно, бесплатную платной — нельзя).

## 3. App content (Policy → App content)
- **Privacy policy:** `https://yompix.github.io/privacy.html`
- **Ads:** No ads. **App access:** All functionality available without special access.
- **Content rating** (анкета IARC): категория Game; насилие — реалистичное, кровь и расчленение (gore) — да; страх/хоррор — да; азартных игр, наркотиков, общения между игроками, покупок внутри — нет. Ожидаемый рейтинг — 18+ (PEGI 18 / Mature 17+).
- **Target audience:** 18 and over. **News app:** No. **Government app:** No.
- **Data safety:** No data collected, no data shared (сохранения только на устройстве, сети нет). Encryption in transit — not applicable (no network). Deletion — not applicable.
- **Health, financial features:** none.

## 4. Store listing (Grow → Store presence → Main store listing)
- **Short description (EN, ≤ 80):** A night-shift horror. Don't answer. Don't open. Wait for six.
- **Short description (RU):** Ночная смена и хоррор. Не отвечай. Не открывай. Дождись шести.
- **Full description (EN):**

  Don't answer. Don't open. Wait for six.

  You take the night shift at the Horizon business center — an empty office tower that closed a month ago. The guard before you, Frank, sat in that booth for twenty years. A week ago he never handed over his shift: the lamp was on, the tea had gone cold, and Frank was gone. The cameras didn't catch a thing.

  The day guard hands you the keys with a shaking hand and tells you to read the logbook before three. On the last page, in Frank's handwriting: DON'T ANSWER TO YOUR NAME. DON'T OPEN THE DOOR TO IT. DON'T GO TO IT YOURSELF.

  At three in the morning your little sister Lily is standing outside the glass doors. In the rain. Calling your name.

  • A 2–3 hour interactive horror story with timed choices, four endings and 14 ways to die
  • Between story scenes you work the shift: watch the CCTV cameras, log anything wrong, run the elevator and the lights — and don't stare at CAM 13
  • Voiced in English, Russian, Spanish, Portuguese and German; subtitles in 13 languages
  • Three looks: Retro (low-poly PS1 style), Cinema and Ultra; the game picks what your phone can run
  • Portrait or landscape, touch or gamepad. Works offline. Headphones recommended
  • No ads, no accounts, no data collection

  Content warning: 18+. Violence, blood and gore, jump scares, flashing lights ("fewer flashes" option in settings).

- **Полное описание (RU):** как в `tools/itch/page.md` (раздел «Описание (RU)»), без строк про браузер и демо; список — как в EN выше.
- **Графика:** значок `dist/play/icon-512.png` (512×512), баннер `dist/play/feature-1024x500.png`, скриншоты телефона — 4–8 шт. (16:9 или 9:16, сторона от 1080 px).
- **Category:** Games → Adventure (или Casual/Role Playing — на выбор). **Tags:** Horror, Story, Interactive story. **Contact:** почта разработчика (видна в магазине).

## 5. Загрузка сборки
1. Testing → **Internal testing** → Create release → загрузить `HushTillSix-v1.2.5.aab`. Play предложит «Google-managed app signing key» — согласиться.
2. Проверить на своём телефоне по ссылке внутреннего теста (до этого APK из сборки, поставленный вручную, нужно удалить: подписи разные).
3. Testing → **Closed testing** → тот же AAB, добавить 12+ тестировщиков, 14 дней.
4. После закрытого теста: Production → Apply for production → ответы о тесте → релиз. Проверка Google — от нескольких часов до нескольких дней.

## Что проверено в сборке (29 окт 2026)
bundletool validate — ок; AAB подписан (jarsigner -verify), APK — схема v2 (apksigner verify); targetSdk 36, minSdk 26, разрешение только VIBRATE; в приложении 1562 файла игры, без режима проверки (qa.js) и страниц сайта. Мост приложения (выход, пауза при сворачивании, «Назад») проверен в мобильном Chromium; на настоящем телефоне — см. HANDOFF.
