# LANTERN — подключение Google Play Billing и AdMob (Android)

Сейчас покупки и реклама работают **только в ДЕМО** (без денег). Чтобы включить настоящие:

## 1. Плагины (сборка Gradle)
Android-сборка `Android AAB` уже собирается через Gradle (`android/build`). Добавить плагины Godot 4 (Android plugin v2):
* **Google Play Billing** — официальный `GodotGooglePlayBilling` (github.com/godot-sdk-integrations/godot-google-play-billing),
  версия для Godot 4.x: положить `addons/GodotGooglePlayBilling/` в проект и включить в «Проект → Настройки → Плагины».
* **AdMob** — Poing Studios `godot-admob-plugin` (github.com/poingstudios/godot-admob-plugin), ветка/релиз под Godot 4.7:
  `addons/admob/` + Android-библиотеки из релиза; включить плагин.
  (В облаке, где собиралась эта версия, GitHub-релизы этих репозиториев были недоступны, поэтому .aar не вложены.
  Повторная попытка 2026-11-09: github.com/releases, api.github.com и codeload — 403 (политика сети), в Maven Central
  этих плагинов нет (там только `org.godotengine:godot*`), Asset Library отдаёт ссылки на те же GitHub-архивы.
  Исходники billing-плагина клонируются через git, но собирать чужой код Gradle в облаке не разрешено — собирайте у себя.)

  **Как поставить у себя (10 минут):**
  1. Скачать релиз `GodotGooglePlayBilling-*.zip` (ветка master, Godot 4.2+) → распаковать в `godot/playtogether/addons/GodotGooglePlayBilling/`.
     Или собрать: `git clone https://github.com/godot-sdk-integrations/godot-google-play-billing && cd … && ./gradlew assemble`
     → папка `addons/GodotGooglePlayBilling` появится в корне клона.
  2. Скачать релиз Poing `godot-admob-plugin` для Godot 4.x (Android) → `addons/admob/` (+ его `android`-библиотеки по README).
  3. `project.godot` → `[editor_plugins] enabled=` добавить `res://addons/GodotGooglePlayBilling/plugin.cfg` и
     `res://addons/admob/plugin.cfg` (рядом уже есть `res://addons/lantern_android/plugin.cfg` — его не убирать).
  4. В пресетах Android `exclude_filter` содержит `addons/*` — это исключает только **скрипты редактора** из pck;
     .aar подключаются экспорт-плагинами на этапе Gradle и попадут в сборку. Если плагину нужны свои .gd во время игры
     (BillingClient.gd), уберите `addons/*` из `exclude_filter` Android-пресетов.
  5. Пока `config/ads.cfg` выключен и в приложении включён ДЕМО (Настройки), реальные покупки и реклама не вызываются:
     `scripts/social/store.gd` проверяет `Engine.has_singleton(...)` и флаги.
Код уже готов: `scripts/social/store.gd` — ищет синглтоны `GodotGooglePlayBilling` и `PoingGodotAdMob`;
пока их нет — кнопки показывают «скоро» или ДЕМО.

## 2. Товары в Play Console
Создать: `sparks_500`, `sparks_1200` (управляемые, расходуемые), `lantern_pass_s1` (управляемый, разовый),
подписку `lantern_pro_month` (базовый план «ежемесячно»). Цены — см. `docs/ECONOMY.md`.
Аудитория — подростки: включить «Семейные» настройки, запретить покупки без подтверждения родителя (Play это делает сам).

## 3. Проверка покупок на сервере
Relay принимает `{"t":"purchase","product":…, "token":…}` и проверяет формат токена. Настоящая проверка — через
Google Play Developer API (`purchases.products.get` / `purchases.subscriptionsv2.get`):
1. Google Cloud → сервисный аккаунт с доступом к Play Console (роль «Финансы»), ключ JSON.
2. Railway → сервис relay → **Variables** → `GOOGLE_SERVICE_ACCOUNT_JSON` = содержимое ключа (секрет; **в репозиторий не класть**).
3. Дописать вызов API в `relay/economy.js → verifyPurchase` (место помечено TODO) и начисление.
4. Для релиза: `DEMO_PURCHASES=0` в переменных relay.

## 4. AdMob
Файл `config/ads.cfg` (образец — `config/ads.cfg.example`): `enabled=true`, `app_id`, `rewarded`, `interstitial`.
Пустые поля = официальные **тестовые** ID Google. Для детей: `tagForChildDirectedTreatment=true`, `maxAdContentRating=G`,
неперсонализированная реклама, форма согласия UMP. App ID также вписывается в манифест (делает плагин).

## 5. Подпись
Ключ загрузки создан локально: `/tmp/claude-0/lantern-keys/lantern-upload.jks` (алиас `lantern-upload`, пароль —
`upload-keystore-password.txt` рядом). **Сохраните оба файла в надёжном месте** (не в git). В Play Console включите
**Play App Signing** — тогда при потере ключа загрузки его можно заменить через поддержку Google.
Экспорт: переменные `GODOT_ANDROID_KEYSTORE_RELEASE_PATH/_USER/_PASSWORD`, затем
`godot --headless --path godot/playtogether --export-release "Android AAB" export/android/LANTERN.aab`.
