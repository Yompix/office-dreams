> **Важно (9 ноя):** сервер Pixel Casino на Railway при запуске берёт `app.tsx` и `web.html` с GitHub — из ветки `pixel-casino` (или `APP_REF`) через `raw.githubusercontent.com`. Поэтому ветки `pixel-casino` и `pixel-casino-fx` **не удалять и не менять**; здесь — копия `pixel-casino-fx` для порядка. Когда репозиторий станет закрытым, обновления с GitHub загрузчик скачать не сможет (запустит прошлую версию из своей базы) — для новых версий нужен токен или другой адрес.

# Pixel Casino — збірка

- `app.tsx` — сервер (Bun), збирається `server/build.py` з базового коду + `server/games.ts`.
- `web.html` — інтерфейс Mini App, збирається `web/build.py` з `web/src/*`.
- Завантажувач на Railway бере обидва файли з цієї гілки й перевіряє sha256.
