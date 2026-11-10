# LANTERN — протокол v1 (by Yompix)

Один формат для ПК (Windows/Linux) и Android. Общий код клиента — `scripts/join/*.gd`
(`RelayLink`, `RelayHost`, `RelayJoin`, `RoomCode`, `Invite`), сервер — `relay/server.js`.

## 1. Код комнаты

* 6 символов из алфавита `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (нет 0/O и 1/I/L — не путаются).
* Показывается как `K7M-2QX`; при вводе регистр, пробелы и дефисы игнорируются.
* Код выдаёт relay при открытии комнаты; живёт, пока хозяин подключён, и ещё 5 минут после
  обрыва (хозяин может вернуться с `resume`-токеном и тем же кодом). Максимум жизни — 24 ч.
* **Свой код (LANTERN Pro)**: 3–8 символов из `A–Z` и `2–9` (без 0 и 1), хотя бы одна буква, например `STEVE`.
  Резервируется командой `vanity_set`, уникален, проходит фильтр мата (в т.ч. транслитом) и список запрещённых
  (`ADMIN…`, `LANTERN…`, `YOMPIX…`, `MOJANG…` и т.п.). Хозяин просит его полем `"vanity": true` в `{"t":"host"}`;
  relay выдаёт его, только если Pro активна и комната с этим кодом сейчас не открыта (иначе — случайный код,
  в ответе `room.vanity=false`). Когда Pro заканчивается, резерв снимается (код может занять другой).
  Случайные коды никогда не совпадают с зарезервированными. Свой код легко угадать — это его смысл.

## 2. Приглашение (QR / ссылка)

```
lantern://join?v=1&c=K7M2QX&e=both&n=<имя мира, URL-кодировано>[&r=<wss-адрес relay>]
```

| поле | смысл |
|---|---|
| `v` | версия формата (сейчас 1). Если больше поддерживаемой — «обновите приложение» |
| `c` | код комнаты |
| `e` | `java`, `bedrock` или `both` (Java-сервер + Geyser: заходят и Java, и Bedrock) |
| `n` | название мира |
| `r` | адрес relay, только если не стандартный |

Добавить в друзья (QR на экране «Друзья»): `lantern://friend?v=1&tag=<Ник#1234, URL-кодировано>`.

Android открывает обе ссылки в LANTERN (intent-filter `lantern://join`, `lantern://friend` — добавляет
`addons/lantern_android` при Gradle-сборке; ссылка читается через `AndroidRuntime` из Intent при запуске и при
возврате приложения, `scripts/join/deeplink.gd`). На ПК ссылку можно передать аргументом командной строки.

Поле ввода «Зайти к другу» принимает: код, ссылку `lantern://`, ссылку `minecraft://?addExternalServer=Имя|host:port`
и обычный адрес `host[:port]` (игра в одной сети без relay).

## 3. Relay: WebSocket `wss://<домен>/ws`

* Текстовые кадры — JSON-команды, бинарные — данные: `[u32 sid, big-endian][payload]`.
* UDP (Bedrock/Geyser): **один бинарный кадр = одна датаграмма** (границы сохраняются).
* TCP (Java): кадры — куски потока по порядку.
* `GET /healthz` → `{"ok":true,"app":"lantern-relay","v":1,"rooms":N,"joiners":N,"uptime":s}`.

### Хозяин мира
```
→ {"t":"host","v":1,"edition":"java|bedrock|both","name":"Мир"[,"code":"K7M2QX","resume":"<token>"]}
← {"t":"room","code":"K7M2QX","token":"<32 hex>","v":1,"limits":{...}}
← {"t":"open","sid":17,"kind":"tcp|udp","peer":"a1b2c3"}   новый поток от друга
       tcp → подключиться к 127.0.0.1:25565 (Paper); udp → 127.0.0.1:19132 (BDS или Geyser)
↔ бинарные кадры с этим sid
← {"t":"close","sid":17}     друг закрыл поток    → {"t":"close","sid":17}  локальное соединение закрылось
→ {"t":"end"}                закрыть комнату
```
### Друг
```
→ {"t":"join","v":1,"code":"K7M2QX"}
← {"t":"joined","v":1,"code":"K7M2QX","edition":"both","name":"Мир","host":true}
← {"t":"error","error":"not_found|full|rate|bad_version|busy"}
→ {"t":"open","sid":5,"kind":"tcp|udp"}      sid выбирает друг (свои номера, relay переводит)
↔ бинарные кадры
→/← {"t":"close","sid":5}
← {"t":"host_gone"} / {"t":"host_back"}       хозяин переподключается
```
Обе стороны: `{"t":"ping","ts":…}` → `{"t":"pong","ts":…}` (раз в 20 с); relay шлёт WS-ping каждые 15 с,
молчание дольше 90 с — отключение.

### Что делает приложение друга (локально)
* **Java**: TCP-вход на 127.0.0.1:25565 (если занят — любой свободный порт) + LAN-объявление
  `[MOTD]Мир — LANTERN[/MOTD][AD]<порт>[/AD]` на мультикаст `224.0.2.60:4445` раз в 1,5 с →
  мир появляется в «Сетевой игре». Каждое TCP-подключение игры = поток `tcp`.
* **Bedrock**: UDP `0.0.0.0:19132`. Поток `sid=1` (udp) зарезервирован под RakNet-пинги: раз в 2 с
  приложение шлёт `Unconnected Ping (0x01)` через relay, получает настоящий `Unconnected Pong (0x1C)`
  сервера, подменяет GUID и порты (поля 7, 11, 12 строки MOTD) и само отвечает игре на LAN-пинги →
  мир появляется во вкладке «Друзья». Каждый адрес:порт игры = отдельный поток `udp`.

### Лимиты relay (переменные окружения)
`MAX_ROOMS=300`, `MAX_JOINERS=20` (друзей в комнате), `MAX_STREAMS=64` (потоков у друга),
`MAX_CONN_PER_IP=40`, `JOIN_PER_MIN=30` (попыток входа с IP), `ROOM_BYTES_PER_SEC=4194304`
(при превышении relay притормаживает отправителя), `MAX_FRAME=65540`, `IDLE_MS=90000`,
`RESUME_MS=300000`, `ROOM_MAX_MS=86400000`. Содержимое кадров не читается и не логируется.

## 4. Совместимость
* Изменения, ломающие формат, — только с повышением `v`. Relay отвечает `bad_version` клиенту новее себя.
* Android-сборка использует те же скрипты `scripts/join/*` и `scripts/qr/qr.gd` без изменений.

## 5. Профили, друзья, чаты, экономика (то же соединение `/ws`, JSON)

Хранилище — SQLite (`node:sqlite`, файл `DB_PATH` или `$RAILWAY_VOLUME_MOUNT_PATH/lantern.db`; без тома — в памяти,
после перезапуска всё сбрасывается). Код: `relay/social.js`, `relay/economy.js`; тесты: `node relay/test_social.js`.

**Профиль без пароля.** Клиент один раз создаёт токен устройства (32 случайных байта, hex) и хранит его в `user://device.cfg`.
Сервер хранит только `sha256("lantern:"+токен)`.
```
→ {"t":"hello","token":"…"}            ← {"t":"welcome","me":{id,nick,tag,lantern,title,equip,online}}  или {"t":"need_register"}
→ {"t":"register","token":"…","nick":"Стив"}   ← welcome | {"t":"register_err","error":"Такой ник нельзя"}
```
Ник: 3–16 символов (буквы, цифры, «_»), фильтр мата, запрет служебных имён. Тег — `Ник#1234`.
Сервер шлёт `{"t":"state", me, friends[], incoming[], outgoing[], blocked[]}` и `{"t":"friend", friend}` при смене статуса.

| команда | поля | смысл |
|---|---|---|
| `presence` | `activity: idle/hosting/playing, world, edition, version, code` | статус для друзей (код комнаты видят только друзья) |
| `friend_add` | `tag` | заявка (встречная — сразу дружба); лимит 30/час |
| `friend_accept` / `friend_decline` / `friend_remove` | `id` | |
| `block` / `unblock` | `id` | блок скрывает сообщения и статус, удаляет из друзей |
| `report` | `id, reason, msg_id?, block?` | жалоба (таблица `reports`), лимит 10/час |
| `dm` | `to, text` | только друзьям; ≤300 символов, ссылки → «[ссылка]», мат → «***», 20/мин |
| `history` | `with` | последние 100 сообщений за 24 ч |
| `invite` | `to, code, world, edition` | друг получает `{"t":"invite",…}` |
| `room_enter` / `room_chat` | `code` / `text` | чат комнаты (в памяти, 100 последних, удаляется с комнатой) |
| `rooms` | `edition, tag, q` | открытые комнаты (также `GET /rooms?edition=&tag=&q=`) |
| `eco`, `quest_claim{id,period}`, `streak_claim`, `achievements`, `set_title{title}` | | задания, серия, достижения |
| `shop`, `buy{item}`, `wear{item}`, `season`, `season_claim{tier,track}` | | косметика и сезон |
| `referral{tag}`, `top`, `ad_reward` | | рефералы, топ недели, награда за рекламу (≤3/день) |
| `purchase{product,token}` | | проверка покупки Google Play (заготовка) |
| `demo_purchase{product}`, `demo_wipe` | | ДЕМО-режим (выключается `DEMO_PURCHASES=0`) |

Хозяин может передать в `{"t":"host"}` поля `token` (чей мир — для заданий и «Фонаря») и
`public: {title, tags[], version, slots}` — тогда комната попадает в список открытых. Друг передаёт `token` в `{"t":"join"}`;
`{"t":"like"}` из соединения друга — лайк комнате (1 раз в день).
Время «вместе» считает relay: раз в минуту +1 хозяину и каждому другу-аккаунту, если у друга есть живые потоки.

### LANTERN Pro: свой код и облачные копии (`relay/pro.js`)

| команда | поля | ответ |
|---|---|---|
| `vanity_set` | `code` | `{"t":"vanity", code, error}` |
| `vanity_clear` | | `{"t":"vanity", code:""}` |
| `cloud_list` | | `{"t":"cloud", enabled, pro, list:[{id,name,size,ts,share,share_until}], max_mb, slots}` |
| `cloud_share` | `id` | то же + `shared` — код из 6 символов, действует 24 ч |
| `cloud_delete` | `id` | то же |

HTTP (тот же домен, заголовок `Authorization: Bearer <токен устройства>`):
* `PUT /cloud/upload?name=<имя.zip>` — тело zip (проверяется сигнатура `PK\3\4`), только с Pro; ≤ `CLOUD_MAX_MB`;
  больше `CLOUD_SLOTS` копий — самая старая удаляется. Ответ `{ok, item}` или `{ok:false, error}` (401/403/411/413/415/429/507).
* `GET /cloud/file/<id>` — своя копия.
* `GET /cloud/share/<код>` — копия по коду друга (любой профиль LANTERN; Pro не нужна).
Без тома (`RAILWAY_VOLUME_MOUNT_PATH`/`CLOUD_DIR`) облако выключено (`enabled:false`, HTTP 503).
Копии удаляются через `CLOUD_KEEP_DAYS` (30) дней после окончания Pro.
