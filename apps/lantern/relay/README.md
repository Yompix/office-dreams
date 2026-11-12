# LANTERN relay (by Yompix)

Сервер-посредник для приложения LANTERN: друзья заходят в мир Minecraft (Java и Bedrock) по
коду комнаты. Relay только пересылает данные игры между хозяином и друзьями; ничего не хранит
на диске и не пишет содержимое в журнал. Протокол — `apps/lantern/godot/docs/PROTOCOL.md`.

## Запуск локально
```
cd relay && npm install && PORT=8080 node server.js
curl http://127.0.0.1:8080/healthz
npm test            # relay (хост-эхо, TCP/UDP-потоки) + симуляция 3 игроков: друзья, чаты, задания, магазин, сезон + Pro (свой код, облако)
```

## Где работает сейчас
Relay подключён к серверу сайта Office Dreams (`server/index.js` → `attach(server, { wsPath: '/lantern/ws' })`)
и выкладывается вместе с ним после push в `main`, без отдельного сервиса:
- приложение: `wss://office-dreams-production.up.railway.app/lantern/ws`;
- проверка: `https://office-dreams-production.up.railway.app/lantern/healthz`, открытые миры — `/lantern/rooms`;
- база профилей и друзей — `lantern.sqlite` рядом с базой сайта; выключить relay — переменная `LANTERN=off`.

## Railway отдельным сервисом (если трафика станет много)
1. Railway → проект → **New → GitHub Repo** → этот репозиторий (существующий сервис Office Dreams не трогать).
2. В настройках нового сервиса: **Root Directory = `relay`**, **Start Command = `node server.js`**
   (Build Command не нужен — Railway сам выполнит `npm install`). Node ≥ 20.
3. **Networking → Generate Domain** (HTTP). Порт берётся из `$PORT` автоматически.
4. Проверка: `https://<домен>/healthz` → `{"ok":true,"app":"lantern-relay",...}`.
5. Адрес для приложения: `wss://<домен>/ws`. Его нужно вписать в
   `apps/lantern/godot/scripts/core/config.gd` (`DEFAULT_RELAY`) и пересобрать приложение,
   либо каждый игрок вставляет его в «Настройки → Сервер-посредник».
6. Health check path в Railway: `/healthz`.

Railway даёт HTTPS/WebSocket — этого достаточно: UDP Bedrock идёт внутри WebSocket-кадров.

### Профили, друзья, задания — нужен том (Volume)
Аккаунты, друзья, сообщения (24 ч), задания, покупки косметики хранятся в SQLite (`node:sqlite`, нужен Node ≥ 22.13).
Railway → сервис relay → **Volumes → New Volume**, Mount Path `/data`. Relay сам найдёт `RAILWAY_VOLUME_MOUNT_PATH`
и создаст `/data/lantern.db` (или задайте `DB_PATH`). **Без тома** всё работает, но после каждого перезапуска профили обнуляются.
Для релиза в Google Play: `DEMO_PURCHASES=0` (выключить демо-покупки) и `GOOGLE_SERVICE_ACCOUNT_JSON` (секрет, см.
`apps/lantern/godot/docs/MONETIZATION.md`).

### LANTERN Pro: облачные копии миров — тот же том
Если том подключён, relay хранит zip-копии миров в `<том>/cloud` (или `CLOUD_DIR`). Без тома облако выключено,
приложение так и пишет; свой код комнаты (тоже Pro) работает и без тома, но резервы обнулятся при перезапуске.
Оценка места: `CLOUD_SLOTS × CLOUD_MAX_MB` на игрока (по умолчанию 3 × 100 МБ), общий потолок `CLOUD_TOTAL_MB`
(4 ГБ). Размер тома в Railway подберите под число Pro-подписчиков (Hobby — до 5 ГБ, Pro — больше).

## Переменные окружения (необязательно)
| имя | по умолчанию | смысл |
|---|---|---|
| `MAX_ROOMS` | 300 | комнат одновременно |
| `MAX_JOINERS` | 20 | друзей в комнате |
| `MAX_STREAMS` | 64 | соединений у одного друга |
| `MAX_CONN_PER_IP` | 40 | WebSocket-подключений с одного IP |
| `JOIN_PER_MIN` | 30 | попыток входа по коду с IP в минуту (защита от перебора) |
| `ROOM_BYTES_PER_SEC` | 4194304 | трафик комнаты, больше — притормаживание |
| `IDLE_MS` | 90000 | отключать молчащих |
| `RESUME_MS` | 300000 | сколько держать код после обрыва хозяина |
| `QUIET` | — | `1` — не писать журнал |
| `CLOUD_DIR` | `<том>/cloud` | где хранить облачные копии (пусто и нет тома — облако выключено) |
| `CLOUD_MAX_MB` | 100 | максимум одной копии |
| `CLOUD_SLOTS` | 3 | копий на игрока (старые заменяются) |
| `CLOUD_TOTAL_MB` | 4096 | всего на relay |
| `CLOUD_KEEP_DAYS` | 30 | сколько хранить после окончания Pro |

Трафик: одна игра Java ≈ 50–300 КБ/с на игрока. Учитывайте лимиты тарифа Railway.

## Страница-ссылка на игру (1.7)

`GET /j/<код или номер комнаты>` (снаружи — `/lantern/j/<код>`) — маленькая мобильная страница для приглашений: название игры, хозяин,
игроков x/y, «Открыть в LANTERN» и «Скачать LANTERN». Код — `join_page.js`, проверка — `node test_join_page.js` (входит в `npm test`).
