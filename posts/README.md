# Channel posts (Office Dreams)

The owner asked Claude to run the project channel: write English posts about **Office Dreams** and queue them here.
The server publishes them by itself (`server/posts.js`): no bot token or admin key is needed.

## How it works
- `posts/queue.json` is a JSON array of posts. Every minute the server publishes the posts whose time has come to the channel (`CHANNEL`, the bot is its admin).
- A post goes out **once** (remembered by `id` in the database, reserved before the send, so it never doubles). A post more than **12 h** overdue is dropped. Two posts are at least **2 h** apart. If Telegram refuses, it is retried after 10, 20, 40… min (6 tries) and the owner gets a message in the bot; a send that timed out is not repeated (the owner checks the channel).
- The owner sees what is coming with `/posts` in the bot and cancels one with `/postskip <id>`.
- `npm test` checks the file: valid JSON, every entry valid (including Telegram HTML: only its tags, all closed, no bare `<` `>` `&`), **no Cyrillic letters** in the text or the button, every picture exists. A broken file never reaches `main`.

```json
{ "id": "2026-10-19-city", "at": "2026-10-19T16:00:00Z", "text": "<b>Title</b>\n\nBody…", "photo": "shot-city.jpg" }
```
- `id`: `YYYY-MM-DD-short-slug` (lowercase, digits, dashes), unique forever: never reuse an id.
- `at`: UTC time. One post a day, around **16:00 UTC** (evening in Europe/Kyiv, morning in the US). Never two posts on the same day.
- `text`: Telegram HTML only: `<b>`, `<i>`, `<u>`, `<s>`, `<a href="…">`, `<code>`, `<blockquote>`, `<tg-spoiler>`. No `<br>`/`<p>`: use `\n` for new lines. Write `&lt;` `&gt;` `&amp;` for < > &. **With a photo the text is a caption: at most 1024 characters** (keep it under 900). Without a photo, under 1500.
- `photo` (optional): a file in `public/` or `public/posts/` (other folders are not served). Ready ones: `cover-640x360.png`, `shot-office.jpg`, `shot-team.jpg`, `shot-city.jpg`, `shot-tower.jpg` (classic look, tall phone screenshots). New pictures go to `public/posts/<id>.jpg` (JPEG, under 300 KB, 1280×720 or 1080×1080).
- `button` (optional): the text of the link button under the post (default `▶️ Play Office Dreams`, opens the game); `false` = no button.
- Posts that went out can be removed from the file after two weeks (keeps it short); the database still remembers their ids.

## Rules (from the owner; never break them)
- **English only.** Telegram translates for readers. No versions in other languages.
- **About Office Dreams only** (@officedream1_bot). Not about Night Shift or other YOMPIX games.
- **Honest:** points are not money; nobody can promise a token price or profit; the OFFX price is set by the market. Any post that mentions points, the airdrop, the token or its price ends with a short risk line, e.g. `<i>Points are not money. Crypto is risky: the OFFX price is set by the market.</i>`
- **Real facts only.** Features must exist in the code today (`src/`, `server/`). Numbers only from live data: `https://office-dreams-production.up.railway.app/stats.json` (verified players, launch fund) and `/token.json`. No made-up player counts, winners, quotes or "limited time" pressure.
- Never mention a "restart" of the project. No hidden bots, no fake activity.
- **Art 2** (the new illustrated look) is a test mode: do not show it or announce it until the owner switches it on for everyone (see HANDOFF.md).
- Do not tease features that are not built yet, except ones the owner announced himself.
- Contacts: channel @officedream, questions @vtmjs, game https://t.me/officedream1_bot/play. Warn about fakes now and then: the official bot is @officedream1_bot only.

## What to post (rotate, do not repeat a topic within a week)
- **Feature spotlight:** one thing in the game, what it is and why it is fun (the City and its buildings, Studio Battle, floors of the tower and renovation stars, IPO, events and quests, daily tasks, hiring the 8 roles, secrets of the city).
- **How-to / tip:** a concrete trick that helps players (e.g. coffee before a long session, the Academy is 60% cheaper, contracts at the Deals exchange).
- **Update notes:** what changed in Office Dreams since the last update post (`git log -- src server` since then), in player words, no file names.
- **Airdrop / fund progress:** the real number from `/stats.json` and how the airdrop works (play → points; at $5,000 in the fund OFFX is shared by points among verified players; verification is a one-time $1 or any purchase). With the risk line.
- **Community:** a question to readers, a poll-like post ("Which building should come next?"), a reminder about the official bot.

Style: short (3–8 lines), a bold first line, a few emojis, one call to action at the end. Plain words, no hype words like "massive", "huge gains", "to the moon".

## Workflow for a session that writes posts
1. `git fetch origin main && git merge origin/main`; read HANDOFF.md ("Сейчас в работе", "Просьбы").
2. Look at `posts/queue.json`: which days already have a post. Fill the next days that have none (usually 3), one post a day at 16:00 UTC.
3. Check every fact against the code and live data. Re-read each post against the rules above.
4. `npm test`, commit, `git push origin HEAD:main` (Railway deploys; the server picks up the new queue).
5. Add a line to HANDOFF.md "Что сделано" only for notable changes (not for every routine batch).
