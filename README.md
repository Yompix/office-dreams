# Pixel Casino — збірка

- `app.tsx` — сервер (Bun), збирається `server/build.py` з базового коду + `server/games.ts`.
- `web.html` — інтерфейс Mini App, збирається `web/build.py` з `web/src/*`.
- Завантажувач на Railway бере обидва файли з цієї гілки й перевіряє sha256.
