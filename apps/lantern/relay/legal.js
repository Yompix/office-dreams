'use strict';
// Страницы для Google Play и сайта: /privacy (политика конфиденциальности), /terms (условия), /delete (удаление профиля).
// RU + EN на одной странице. Адрес поддержки — переменная LANTERN_SUPPORT_EMAIL (пока не задана — видно TODO).

const DISCLAIMER_RU = 'НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С MOJANG ИЛИ MICROSOFT.';
const DISCLAIMER_EN = 'NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.';
const UPDATED = '10.11.2026';

function esc(s) { return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
function contact() {
  const e = String(process.env.LANTERN_SUPPORT_EMAIL || '').trim();
  return e ? `<a href="mailto:${esc(e)}">${esc(e)}</a>` : '<b>[TODO: e-mail поддержки / support e-mail]</b>';
}

const CSS = `body{margin:0;background:#14110d;color:#f3eadb;font:16px/1.6 system-ui,sans-serif;display:flex;justify-content:center}
main{max-width:720px;width:100%;padding:32px 16px 48px;box-sizing:border-box}h1{margin:0 0 4px;font-size:28px;color:#ffb648}
h2{margin:32px 0 8px;font-size:20px;color:#ffcf7a}h3{margin:20px 0 4px;font-size:16px}p,li{color:#d9ccb6}a{color:#ffb648}
.m{color:#8d806c;font-size:14px}.d{margin-top:32px;padding:16px;border:1px solid #3a2f22;border-radius:12px;color:#b9ab95;font-size:13px}
nav a{margin-right:16px;font-size:14px}hr{border:0;border-top:1px solid #3a2f22;margin:32px 0}`;

function shell(title, body, base) {
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>${CSS}</style></head><body><main>
<nav><a href="${base}/privacy">Конфиденциальность</a><a href="${base}/terms">Условия</a><a href="${base}/delete">Удалить профиль</a><a href="${base}/download">Скачать</a></nav>
${body}
<div class="d">${DISCLAIMER_RU}<br>${DISCLAIMER_EN}<br>Minecraft — товарный знак Mojang Synergies AB. / Minecraft is a trademark of Mojang Synergies AB.</div>
</main></body></html>`;
}

function privacy(base) {
  return shell('LANTERN — политика конфиденциальности', `
<h1>Политика конфиденциальности LANTERN</h1><p class="m">Обновлено ${UPDATED} · разработчик Yompix · приложение LANTERN (Android, Windows)</p>
<p>LANTERN помогает играть с друзьями по коду комнаты: соединяет ваш мир и устройства друзей через наш сервер-ретранслятор. Мы собираем минимум данных, нужный для работы, и не продаём их.</p>
<h2>Что хранится на сервере</h2><ul>
<li><b>Профиль:</b> ник, тег (ник#4 цифры), дата создания и последнего входа, время в игре и игровые «искры», задания, достижения, косметика. Пароля нет: вход по случайному ключу устройства, на сервере хранится только его хеш.</li>
<li><b>Друзья:</b> список друзей, заявки, блокировки.</li>
<li><b>Сообщения:</b> личные сообщения между друзьями хранятся 24 часа и затем удаляются; чат комнаты — только в памяти, пока открыта комната.</li>
<li><b>Комнаты:</b> пока комната открыта — код, название мира, издание и версия игры, число игроков. После закрытия не хранятся.</li>
<li><b>Жалобы:</b> если вы пожаловались на игрока, сохраняется жалоба и сообщение, на которое она подана, — для модерации.</li>
<li><b>Облачные копии миров</b> (только при подписке Pro, если она будет доступна): zip-файлы ваших миров. Удаляются через 30 дней после окончания подписки или вместе с профилем.</li>
</ul>
<h2>Что не собираем</h2><ul>
<li>Нет рекламы, аналитики и трекеров, нет сторонних SDK для сбора данных.</li>
<li>Не собираем имя, e-mail, телефон, контакты, местоположение, фото.</li>
<li>IP-адрес используется только в памяти сервера для защиты от перегрузки (ограничение числа подключений) и не записывается в журналы и базу.</li>
<li>Игровой трафик (данные Minecraft между вами и друзьями) только пересылается и не сохраняется.</li>
</ul>
<h2>Передача третьим лицам</h2><p>Мы не продаём и не передаём данные третьим лицам. Сервер работает на хостинге Railway (как обработчик данных). Данные передаются по защищённому соединению (TLS).</p>
<h2>Удаление</h2><p>Профиль удаляется в приложении: «Ещё» → «Настройки» → «Удалить профиль». Удаляются профиль, друзья, сообщения, жалобы, игровые данные и облачные копии. Подробнее: <a href="${base}/delete">${base}/delete</a>.</p>
<h2>Дети</h2><p>Приложение не предназначено для детей младше 13 лет. В приложении есть чат с другими игроками: мат и ссылки фильтруются, есть кнопки «Пожаловаться» и «Заблокировать».</p>
<h2>Контакты</h2><p>${contact()}</p>
<hr>
<h1>LANTERN Privacy Policy</h1><p class="m">Updated ${UPDATED} · developer Yompix</p>
<p>LANTERN lets you play with friends using a room code by relaying game traffic through our server. We collect the minimum needed to run the service and never sell it.</p>
<h2>Stored on our server</h2><ul>
<li><b>Profile:</b> nickname, tag (nick#4 digits), created/last-seen dates, playtime and in-app “sparks”, quests, achievements, cosmetics. No password: sign-in uses a random device key, only its hash is stored.</li>
<li><b>Friends:</b> friend list, requests, blocks.</li>
<li><b>Messages:</b> direct messages between friends are kept for 24 hours, then deleted; room chat lives in memory only while the room is open.</li>
<li><b>Rooms:</b> while a room is open — code, world name, game edition and version, player count. Not kept after closing.</li>
<li><b>Reports:</b> if you report a player, the report and the reported message are kept for moderation.</li>
<li><b>Cloud world backups</b> (Pro subscription only, if offered): zip files of your worlds, deleted 30 days after the subscription ends or with the profile.</li>
</ul>
<h2>Not collected</h2><ul><li>No ads, analytics or trackers; no third-party data SDKs.</li><li>No name, e-mail, phone, contacts, location or photos.</li>
<li>IP addresses are used in server memory only for rate limiting and are not logged or stored.</li><li>Game traffic is relayed, not stored.</li></ul>
<h2>Sharing</h2><p>We do not sell or share data with third parties. The server is hosted on Railway (as a processor). Data is encrypted in transit (TLS).</p>
<h2>Deletion</h2><p>Delete your profile in the app: More → Settings → Delete profile. See <a href="${base}/delete">${base}/delete</a>.</p>
<h2>Children</h2><p>Not directed to children under 13. Chat has a profanity/link filter, Report and Block.</p>
<h2>Contact</h2><p>${contact()}</p>`, base);
}

function terms(base) {
  return shell('LANTERN — условия использования', `
<h1>Условия использования LANTERN</h1><p class="m">Обновлено ${UPDATED}</p><ul>
<li>LANTERN бесплатен и предоставляется «как есть». Мы стараемся, чтобы сервер работал, но не гарантируем это.</li>
<li>Для игры нужна своя законно приобретённая копия Minecraft. LANTERN не содержит и не распространяет игру или её файлы. Серверное ПО (Paper, Geyser, Bedrock Dedicated Server, Java) скачивается по вашему запросу с официальных сайтов и подчиняется их лицензиям, включая <a href="https://aka.ms/MinecraftEULA">Minecraft EULA</a>.</li>
<li>Запрещено: оскорбления, спам, мошенничество, обход блокировок, попытки взлома сервера, публикация чужих личных данных. За нарушения профиль может быть заблокирован.</li>
<li>Вы отвечаете за то, кого пускаете в свой мир, и за содержимое своих миров.</li>
<li>Удалить профиль можно в любой момент: <a href="${base}/delete">как удалить</a>. Конфиденциальность: <a href="${base}/privacy">политика</a>.</li></ul>
<hr><h1>LANTERN Terms of Use</h1><ul>
<li>LANTERN is free and provided “as is”, without warranty of uptime.</li>
<li>You need your own legally purchased copy of Minecraft. LANTERN does not include or distribute the game or its files. Server software (Paper, Geyser, Bedrock Dedicated Server, Java) is downloaded on your request from official sites under their own licenses, including the <a href="https://aka.ms/MinecraftEULA">Minecraft EULA</a>.</li>
<li>No harassment, spam, fraud, ban evasion, attacks on the service or sharing others’ personal data. Violations may lead to a ban.</li>
<li>You are responsible for whom you let into your world and for your worlds’ content.</li>
<li>You can delete your profile at any time: <a href="${base}/delete">how</a>. Privacy: <a href="${base}/privacy">policy</a>.</li></ul>
<p>Контакты / Contact: ${contact()}</p>`, base);
}

function del(base) {
  return shell('LANTERN — удалить профиль', `
<h1>Удаление профиля LANTERN</h1><p class="m">Разработчик Yompix · приложение LANTERN</p>
<h2>В приложении (сразу)</h2><ol><li>Откройте LANTERN.</li><li>«Ещё» → «Настройки».</li><li>Внизу — «Удалить профиль» → подтвердите.</li></ol>
<p>Нужен интернет. Удаление происходит сразу и навсегда.</p>
<h2>Без приложения</h2><p>Напишите на ${contact()} с темой «Удалить профиль» и укажите ваш тег (Ник#1234 — он виден в профиле). Мы удалим профиль в течение 7 дней и ответим.</p>
<h2>Что удаляется</h2><ul><li>профиль (ник, тег, ключ устройства), друзья, заявки и блокировки;</li><li>личные сообщения (с обеих сторон переписки) и ваши сообщения в чатах комнат;</li>
<li>жалобы, искры, задания, достижения, косметика, сезон;</li><li>свой код комнаты и облачные копии миров (файлы стираются).</li></ul>
<p>Ничего не хранится после удаления, кроме обезличенных счётчиков (число комнат на сервере). Миры на вашем устройстве не удаляются — это ваши файлы.</p>
<hr><h1>Delete your LANTERN profile</h1>
<h2>In the app (instant)</h2><ol><li>Open LANTERN.</li><li>More → Settings.</li><li>At the bottom tap “Удалить профиль” (Delete profile) and confirm.</li></ol>
<h2>Without the app</h2><p>E-mail ${contact()} with subject “Delete profile” and your tag (Nick#1234). We delete it within 7 days.</p>
<h2>What is deleted</h2><p>Profile (nick, tag, device key), friends, requests, blocks, direct messages (both sides), your room-chat messages, reports, sparks, quests, achievements, cosmetics, season progress, custom room code and cloud world backups (files erased). Nothing is retained except anonymous counters. Worlds stored on your own device are not touched.</p>`, base);
}

const PAGES = { '/privacy': privacy, '/terms': terms, '/delete': del };

// url — без префикса /lantern; base — как этот путь виден снаружи
function http(req, res, url, base) {
  const p = new URL(url, 'http://x').pathname.replace(/\/$/, '');
  const f = PAGES[p];
  if (!f) return false;
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'public, max-age=600' });
  res.end(req.method === 'HEAD' ? '' : f(base || ''));
  return true;
}

module.exports = { http, DISCLAIMER_RU, DISCLAIMER_EN };
