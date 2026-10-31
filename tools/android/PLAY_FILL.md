# Hush Till Six — что вписать в Play Console (по разделам, готовые ответы)

Тексты страницы на 13 языках — `tools/android/listing/` (`ALL.md` — всё в одном файле, `<язык>.txt` — по языку; собираются
`python3 tools/android/listing.py`). Графика — `dist/play/` (значок, баннер, скриншоты). Общий порядок и про аккаунт — `PLAY.md`.

## 0. Создание приложения (Home → Create app)
- **App name:** `Hush Till Six`
- **Default language:** English (United States) – en-US
- **App or game:** Game
- **Free or paid:** решает владелец. Бесплатную потом нельзя сделать платной, платную бесплатной — можно. Для платной нужен
  платёжный профиль продавца (Setup → Payments profile) и налоговые данные.
- Галочки: Developer Program Policies и US export laws — поставить.

## 1. App content (Policy and programs → App content)
| Раздел | Ответ |
|---|---|
| **Privacy policy** | `https://yompix.github.io/privacy.html` |
| **App access** | All functionality in my app is available without any access restrictions (входа и паролей нет) |
| **Ads** | No, my app does not contain ads |
| **Content rating** | анкета ниже |
| **Target audience and content** | Age groups: только **18 and over**. Appeal to children: **No** |
| **News apps** | No |
| **Data safety** | анкета ниже — «данные не собираются и не передаются» |
| **Government apps** | No |
| **Financial features** | My app doesn't provide any financial features |
| **Health apps** | My app does not have any health features |
| **Advertising ID** | No — приложение не использует рекламный идентификатор |
| **Photo and video permissions, Foreground service, Full-screen intent** | не спрашивают: у игры одно разрешение — вибрация |

### Content rating (анкета IARC)
- Email для сертификата — почта разработчика. Category: **Game**.
- **Violence** — Yes. Реалистичное насилие над людьми — Yes. Кровь — Yes. Расчленение, увечья (gore) — Yes.
  Игрок сам совершает насилие или его награждают за него — **No**: герой — жертва, убивает монстр.
- **Fear / horror** — Yes, сильные пугающие сцены (скримеры, смерти героя).
- **Sexuality / nudity** — No.
- **Language** (мат, грубый юмор) — No.
- **Controlled substances:** табак — **Yes** (персонаж закуривает сигарету, одна сцена); алкоголь — No; наркотики — No.
- **Gambling** (азартные игры, ставки, лутбоксы) — No.
- **Users interact / share content / chat** — No. **Shares location** — No. **Digital purchases** — No.
  **Unrestricted internet / web browser** — No.
- Ожидаемый итог: PEGI 18, ESRB Mature 17+, USK 18. После Submit рейтинг применяется сразу.

### Data safety
1. Data collection and security → **Does your app collect or share any of the required user data types? — No.**
2. Дальше Play сам ставит «No data collected» и «No data shared» → Save → Submit.
   Почему так: у приложения нет разрешения на интернет, аналитики и рекламы нет, сохранения лежат только на телефоне.

## 2. Store settings (Grow users → Store presence → Store settings)
- **App category:** Game → **Adventure**
- **Tags** (до 5): Horror, Mystery, Story, Offline, Single player (выбрать из списка Play, что есть)
- **Contact details:** Email — почта для игроков (видна в магазине, обязательно); Website — `https://yompix.github.io`;
  Phone — можно не указывать.
- **External marketing:** оставить включённым.

## 3. Main store listing (Store presence → Main store listing)
- **App name / Short description / Full description** — из `listing/en-US.txt`.
- **Переводы:** Manage translations → Add translations → отметить языки: ru-RU, uk, es-ES, es-419, pt-BR, de-DE, fr-FR, it-IT,
  pl-PL, tr-TR, ja-JP, ko-KR, zh-CN → в каждом языке вставить три поля из `listing/<язык>.txt` (Google не переводит сам,
  если вписан наш текст).
- **App icon:** `dist/play/icon-512.png` (512×512 PNG)
- **Feature graphic:** `dist/play/feature-1024x500.png` (1024×500)
- **Phone screenshots:** 7 штук `dist/play/screenshots/*_en_phone.png` (1920×1080, порядок по номеру) — в английскую страницу;
  в русскую — `*_ru_phone.png`; в остальные языки можно не загружать (покажутся английские).
- **Tablet screenshots (7" и 10"):** те же английские — подходят по размеру; без них нет пометки «для планшетов».
- **Video:** ссылка на трейлер на YouTube (если выложен), можно пусто.

## 4. Closed testing (Testing → Closed testing)
1. Create track (или Alpha) → **Testers** → Create email list → Upload CSV → `testers.csv` (12 почт) → Save →
   Feedback URL or email — почта разработчика.
2. **Countries / regions:** Add countries → выбрать все.
3. **Create new release** → загрузить `HushTillSix-v1.2.7.aab` → Release name оставить как предлагает (1.2.7) →
   **Release notes:** английский и русский — поле «Release notes» из `listing/en-US.txt` и `listing/ru-RU.txt`
   (можно все 13 языков, формат `<en-US> … </en-US>`) → Next → Save → **Send for review**.
4. После одобрения: на вкладке Testers скопировать **ссылку для участия** и отправить тестировщикам. Каждый открывает её
   с той же почтой Google, жмёт «Стать тестировщиком» и ставит игру из Play. Нужно 12 человек 14 дней подряд, лучше 15.
5. Через 14 дней: Dashboard → **Apply for production** → ответить на вопросы о тесте → ждать ответа Google (до 7 дней).

## 5. Pricing and distribution (Monetize → … или Setup → Pricing)
- Цена — по решению владельца (Free или Paid).
- Countries — все. **Contains ads:** No.
- **Device categories:** телефоны, планшеты; Chromebook и Android TV — по желанию (игра идёт и с геймпадом).
