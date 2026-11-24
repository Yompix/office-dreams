# YURK VPN → Google Play

## 0. Главное условие
Google Play принимает VPN-приложения (VpnService) **только от аккаунта организации** (Organization), не от личного.
Нужно: зарегистрированная организация или ИП/ФОП (в зависимости от страны) + номер **D-U-N-S** (бесплатно, dnb.com → «Get a D-U-N-S Number», 1–4 недели).
Варианты:
1. Создать новый аккаунт разработчика «Организация» ($25) на это юрлицо/ФОП → загрузить туда YURK.
2. Перевести нынешний аккаунт в организацию: Play Console → Настройки → Сведения об аккаунте разработчика → тип аккаунта (если доступно в вашей стране).
Аккаунт организации **не требует** закрытого теста 12 человек × 14 дней.

## 1. Файлы
- `YURK-1.0.0.aab` — загрузить в Play Console (Выпуск → Рабочая версия / Закрытое тестирование).
- `YURK-upload-key.zip` — ключ загрузки. **Хранить в двух местах.** Без него обновления не загрузить. Play App Signing: «Google управляет ключом подписи приложения» (по умолчанию).
- Пакет: `com.yompix.yurk`, minSdk 26 (Android 8+), targetSdk 35.

## 2. Страница в магазине
**Название (30):** `YURK VPN — fast & private`
**Краткое описание (80):** `One tap VPN. Fast, private, invisible to DPI. Servers in NL, US and Singapore.`
**Полное описание:**
```
YURK is a fast VPN made to just work: one tap and you are protected.

⚡ No speed limits — video without buffering
🥷 Modern protocols (VLESS Reality / TLS): your traffic looks like a normal website
🌍 Servers in the Netherlands, the USA and Singapore
📱 One subscription for phone, PC and Mac
🔒 No activity logs: we never store which sites or apps you use
🎁 3 days free, no card needed
💸 Invite friends and get 20% of every payment they make

How it works: sign in with Telegram, tap Connect, allow the VPN — done.

YURK uses Android's VpnService to create an encrypted tunnel from your device to our servers. This is the app's core function.
Privacy policy: https://yurk-vpn-production.up.railway.app/privacy
```
**Категория:** Инструменты (Tools). **Контакты:** e-mail разработчика, сайт — ссылка на бота или privacy.
**Графика:** иконка 512×512 (`play/icon-512.png`), баннер 1024×500 (`play/feature-1024x500.jpg`), скриншоты телефона (`play/shot-*.jpg`, 1080×2160).

## 3. Объявление VpnService (Policy → App content → VPN service)
```
YURK VPN is a VPN app: creating a secure encrypted tunnel is its core functionality.
The app uses VpnService to route the device's traffic through an encrypted tunnel
(VLESS over TLS / REALITY, Xray core) to the developer's own servers. Data between
the device and the tunnel endpoint is always encrypted. The app does not collect
personal or sensitive user data for monetization, does not manipulate ads, and does
not redirect or inject traffic. The connection starts only when the user taps
"Connect" and accepts the system VPN dialog; the user can disconnect at any time
from the app or the persistent notification.
```

## 4. Анкеты (App content)
- **Политика конфиденциальности:** https://yurk-vpn-production.up.railway.app/privacy
- **Реклама:** нет.
- **Доступ к приложению:** вход через Telegram. Для проверки дать Google тестовую ссылку входа:
  попросить Claude создать тестового пользователя с подпиской → `yurk://login/<token>` (инструкция для проверяющих: «Open the link on the device, then tap Connect»).
- **Возрастной рейтинг:** утилита, без насилия и т.п. → 3+.
- **Целевая аудитория:** 18+.
- **Безопасность данных:**
  - Собираются: «Идентификаторы пользователя» (Telegram id) — для работы приложения и управления аккаунтом; «Покупки» (история платежей) — для работы приложения.
  - Не собираются: местоположение, контакты, история браузера, сообщения, файлы.
  - Передача третьим лицам: нет. Шифрование при передаче: да. Удаление данных по запросу: да (написать «delete» в @yurkvpn_bot).
- **Финансовые функции / правительство / здоровье:** нет.
- **Платежи — см. раздел 6.** В версии для Google Play оплата только через Google Play Billing.

## 5. Страны
Россия: Google удаляет VPN-приложения по требованию РКН — в Play для РФ не рассчитывать, раздавать APK через бота.
Лучше всего Play работает для Ирана (через VPN-аккаунты), Турции, Индии, Индонезии, Европы.

## 6. Оплата в версии для Google Play
Правило Google: цифровые подписки внутри приложения из Play оплачиваются **только через Google Play Billing**
(Google берёт **15%** с подписок — меньше, чем Telegram со звёзд ~35%). Кнопки «Звёзды», «Крипта», ссылки на оплату
в Telegram в Play-версии **запрещены** (иначе приложение отклонят или удалят).
Поэтому две сборки из одного кода:
- **direct** (APK через бота, как сейчас): звёзды, крипта, баланс партнёрки.
- **play** (AAB для Google Play): подписки Google Play (1 мес / 3 мес / год), цены по странам задаёт Google;
  панель проверяет покупку через Google Play Developer API и включает подписку; Telegram не обязателен
  (аккаунт по устройству, вход через Telegram — по желанию). Партнёрка — только дни, без денег.
Что нужно от владельца для play-сборки: аккаунт организации (раздел 0), в Play Console создать 3 подписки
(`yurk_m1`, `yurk_m3`, `yurk_y1`) и сервисный аккаунт Google Cloud с доступом «Финансы» — ключ JSON в Railway.
Скриншоты в `play/` сейчас из direct-версии (со звёздами) — для Play переснять после play-сборки.
