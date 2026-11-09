# LANTERN relay (by Yompix)

Сервер-посредник для приложения LANTERN: друзья заходят в мир Minecraft (Java и Bedrock) по
коду комнаты. Relay только пересылает данные игры между хозяином и друзьями; ничего не хранит
на диске и не пишет содержимое в журнал. Протокол — `godot/playtogether/docs/PROTOCOL.md`.

## Запуск локально
```
cd relay && npm install && PORT=8080 node server.js
curl http://127.0.0.1:8080/healthz
npm test            # самопроверка: хост-эхо, два друга, TCP/UDP-потоки, ошибки
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
   `godot/playtogether/scripts/core/config.gd` (`DEFAULT_RELAY`) и пересобрать приложение,
   либо каждый игрок вставляет его в «Настройки → Сервер-посредник».
6. Health check path в Railway: `/healthz`.

Railway даёт HTTPS/WebSocket — этого достаточно: UDP Bedrock идёт внутри WebSocket-кадров.

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

Трафик: одна игра Java ≈ 50–300 КБ/с на игрока. Учитывайте лимиты тарифа Railway.
