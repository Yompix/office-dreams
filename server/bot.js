'use strict';
/* Telegram bot: /start (+ referral deep links), Stars payments, webhook or long polling. */
const U = require('./users.js');
const Eco = require('./economy.js');
const Sp = require('./sponsors.js');

const esc = (s) => String(s == null ? '' : s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));

const T = {
  en: {
    start: '🏢 <b>Office Dreams</b>\n\nBuild a neon game studio: hire a team, ship hit games, climb floor by floor and walk the 🏙 city — café, academy, deals, games, mall and arena.\n\n🪙 Play → earn airdrop points → <b>90,000,000 OFFX</b> (Solana) are split between verified players by points.\n✅ Verification: one $1 deposit (SOL or Stars) — it also enters you into giveaways.\n👥 Invite friends — bonuses for both.\n\nPoints are not money; the token price is set by the market.\n💬 Admin: @vtmjs\n\nTap <b>Play</b>!',
    site: '🌐 Website', play: '🎮 Play', invite: '👥 Invite friends', share: '📤 Share link',
    inviteText: '🏢 Come build a neon game studio with me in Office Dreams! We both get bonuses:',
    inviteMsg: (link) => `👥 <b>Your invite link</b>\n\n<code>${link}</code>\n\nFor every friend who really plays you get bonus coins and ${'10%'} of their points.`,
    help: '❓ <b>Commands</b>\n\n/play — open the game\n/giveaway — giveaway: prizes, time left, your place\n/invite — your invite link\n/site — project website\n/help — this message\n\n🪙 Points come from playing, daily check-ins, tasks, the city and friends. Bots and farms are filtered automatically.\n✅ Airdrop and giveaways need verification: a one-time $1 deposit (Airdrop tab).\n💬 Questions: @vtmjs',
    joined: (n) => `🎉 Your friend <b>${esc(n)}</b> joined Office Dreams! Bonuses arrive when they start playing for real.`,
    paid: (t) => `✅ Payment received: <b>${esc(t)}</b>. Enjoy!`,
    cmdStart: 'Start', cmdPlay: 'Open the game', cmdGiveaway: 'Giveaway: prizes and your place', cmdInvite: 'Invite friends', cmdSite: 'Project website', cmdHelp: 'Help and commands',
    desc: '🏢 Office Dreams — a neon game-studio tycoon right in Telegram.\n\n🎮 Hire a team, ship games, climb the floors and explore the city.\n🪙 Earn airdrop points: 90,000,000 OFFX (Solana) are split between verified players.\n🎁 Giveaways with real prizes for active players.\n👥 Invite friends — bonuses for both.\n\nPoints are not money; the market sets the token price.\n💬 Admin: @vtmjs',
    short: 'Neon game-studio tycoon · city, giveaways, OFFX airdrop on Solana · admin @vtmjs',
  },
  ru: {
    start: '🏢 <b>Office Dreams</b>\n\nСтрой неоновую студию игр: нанимай команду, выпускай хиты, поднимайся по этажам и гуляй по 🏙 городу — кафе, академия, сделки, игры, торговый центр и арена.\n\n🪙 Играешь → копишь очки аирдропа → <b>90 000 000 OFFX</b> (Solana) делятся между верифицированными игроками по очкам.\n✅ Верификация: разовый депозит $1 (SOL или звёзды) — он же даёт участие в розыгрышах.\n👥 Приглашай друзей — бонусы обоим.\n\nОчки — не деньги, цену токена определяет рынок.\n💬 Админ: @vtmjs\n\nЖми <b>Играть</b>!',
    site: '🌐 Сайт', play: '🎮 Играть', invite: '👥 Пригласить друзей', share: '📤 Поделиться',
    inviteText: '🏢 Присоединяйся ко мне в Office Dreams — строим неоновую студию игр! Бонусы получим оба:',
    inviteMsg: (link) => `👥 <b>Твоя ссылка-приглашение</b>\n\n<code>${link}</code>\n\nЗа каждого друга, который реально играет, ты получаешь бонусные монеты и 10% его очков.`,
    help: '❓ <b>Команды</b>\n\n/play — открыть игру\n/giveaway — розыгрыш: призы, сколько осталось, твоё место\n/invite — ссылка для друзей\n/site — сайт проекта\n/help — это сообщение\n\n🪙 Очки дают игра, ежедневный вход, задания, город и друзья. Боты и фермы отсеиваются автоматически.\n✅ Для аирдропа и розыгрышей нужна верификация: разовый депозит $1 (вкладка Аирдроп).\n💬 Вопросы: @vtmjs',
    joined: (n) => `🎉 Твой друг <b>${esc(n)}</b> зашёл в Office Dreams! Бонусы придут, когда он начнёт играть по-настоящему.`,
    paid: (t) => `✅ Оплата получена: <b>${esc(t)}</b>. Приятной игры!`,
    cmdStart: 'Старт', cmdPlay: 'Открыть игру', cmdGiveaway: 'Розыгрыш: призы и твоё место', cmdInvite: 'Пригласить друзей', cmdSite: 'Сайт проекта', cmdHelp: 'Помощь и команды',
    desc: '🏢 Office Dreams — неоновый тайкун игровой студии прямо в Telegram.\n\n🎮 Нанимай команду, выпускай игры, покоряй этажи и гуляй по городу.\n🪙 Копи очки аирдропа: 90 000 000 OFFX (Solana) делятся между верифицированными игроками.\n🎁 Розыгрыши с реальными призами для активных.\n👥 Приглашай друзей — бонусы обоим.\n\nОчки — не деньги, цену токена определяет рынок.\n💬 Админ: @vtmjs',
    short: 'Неоновый тайкун студии · город, розыгрыши, аирдроп OFFX на Solana · админ @vtmjs',
  },
  uk: {
    start: '🏢 <b>Office Dreams</b>\n\nБудуй неонову студію ігор: наймай команду, випускай хіти, піднімайся поверхами й гуляй 🏙 містом — кафе, академія, угоди, ігри, торговий центр та арена.\n\n🪙 Граєш → збираєш бали аірдропу → <b>90 000 000 OFFX</b> (Solana) діляться між верифікованими гравцями за балами.\n✅ Верифікація: разовий депозит $1 (SOL або зірки) — він же дає участь у розіграшах.\n👥 Запрошуй друзів — бонуси обом.\n\nБали — не гроші, ціну токена визначає ринок.\n💬 Адмін: @vtmjs\n\nНатисни <b>Грати</b>!',
    site: '🌐 Сайт', play: '🎮 Грати', invite: '👥 Запросити друзів', share: '📤 Поділитися',
    inviteText: '🏢 Приєднуйся до мене в Office Dreams — будуємо неонову студію ігор! Бонуси отримаємо обоє:',
    inviteMsg: (link) => `👥 <b>Твоє посилання-запрошення</b>\n\n<code>${link}</code>\n\nЗа кожного друга, який справді грає, ти отримуєш бонусні монети та 10% його очок.`,
    help: '❓ <b>Команди</b>\n\n/play — відкрити гру\n/giveaway — розіграш: призи, скільки лишилось, твоє місце\n/invite — посилання для друзів\n/site — сайт проєкту\n/help — це повідомлення\n\n🪙 Бали дають гра, щоденний вхід, завдання, місто та друзі. Боти й ферми відсіюються автоматично.\n✅ Для аірдропу й розіграшів потрібна верифікація: разовий депозит $1 (вкладка Аірдроп).\n💬 Питання: @vtmjs',
    joined: (n) => `🎉 Твій друг <b>${esc(n)}</b> зайшов в Office Dreams! Бонуси прийдуть, коли він почне грати по-справжньому.`,
    paid: (t) => `✅ Оплату отримано: <b>${esc(t)}</b>. Гарної гри!`,
    cmdStart: 'Старт', cmdPlay: 'Відкрити гру', cmdGiveaway: 'Розіграш: призи і твоє місце', cmdInvite: 'Запросити друзів', cmdSite: 'Сайт проєкту', cmdHelp: 'Допомога і команди',
    desc: '🏢 Office Dreams — неоновий тайкун ігрової студії просто в Telegram.\n\n🎮 Наймай команду, випускай ігри, підкорюй поверхи й гуляй містом.\n🪙 Збирай бали аірдропу: 90 000 000 OFFX (Solana) діляться між верифікованими гравцями.\n🎁 Розіграші з реальними призами для активних.\n👥 Запрошуй друзів — бонуси обом.\n\nБали — не гроші, ціну токена визначає ринок.\n💬 Адмін: @vtmjs',
    short: 'Неоновий тайкун студії · місто, розіграші, аірдроп OFFX у Solana · адмін @vtmjs',
  },
};
const SHOP_TXT = {
  en: { gems_s: ['Handful of gems', '15 gems for boosts'], gems_m: ['Bag of gems', '70 gems for boosts'], gems_l: ['Chest of gems', '400 gems for boosts'], starter: ['Starter pack', '40 gems + $3,000 cash (one time)'], pass30: ['Office Pass · 30 days', '+25% daily points ceiling and 20 gems'] },
  ru: { gems_s: ['Горсть кристаллов', '15 кристаллов для бустов'], gems_m: ['Мешок кристаллов', '70 кристаллов для бустов'], gems_l: ['Сундук кристаллов', '400 кристаллов для бустов'], starter: ['Стартовый набор', '40 кристаллов + $3 000 (один раз)'], pass30: ['Office Pass · 30 дней', '+25% к дневному потолку очков и 20 кристаллов'] },
  uk: { gems_s: ['Жменя кристалів', '15 кристалів для бустів'], gems_m: ['Мішок кристалів', '70 кристалів для бустів'], gems_l: ['Скриня кристалів', '400 кристалів для бустів'], starter: ['Стартовий набір', '40 кристалів + $3 000 (один раз)'], pass30: ['Office Pass · 30 днів', '+25% до денної стелі очок і 20 кристалів'] },
};
const tx = (lang) => T[lang] || T.en;
const CASH_TXT = {
  en: { cash1: 'Handful of coins', cash2: 'Stack of coins', cash3: 'Case of coins', cash4: 'Safe of coins', cash5: 'Bank vault', d: (n) => `$${n} to hire, upgrade and buy floors` },
  ru: { cash1: 'Горсть монет', cash2: 'Пачка монет', cash3: 'Кейс монет', cash4: 'Сейф монет', cash5: 'Банковское хранилище', d: (n) => `$${n} на найм, прокачку и этажи` },
  uk: { cash1: 'Жменя монет', cash2: 'Пачка монет', cash3: 'Кейс монет', cash4: 'Сейф монет', cash5: 'Банківське сховище', d: (n) => `$${n} на найм, прокачку і поверхи` },
};
const nfmt = (n) => String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
const DONATE_TXT = {
  en: (n) => ['Support the launch', `${n} ⭐ toward the OFFX token launch. Thank you!`],
  ru: (n) => ['Поддержка запуска', `${n} ⭐ на запуск токена OFFX. Спасибо!`],
  uk: (n) => ['Підтримка запуску', `${n} ⭐ на запуск токена OFFX. Дякуємо!`],
};
const donateTxt = (lang, n) => (DONATE_TXT[lang] || DONATE_TXT.en)(n);
const THANKS = {
  en: (n) => `💖 Thank you for supporting Office Dreams! <b>${n} ⭐</b> went to the launch fund. Your VIP level has grown.`,
  ru: (n) => `💖 Спасибо за поддержку Office Dreams! <b>${n} ⭐</b> ушли в фонд запуска. Твой VIP-уровень вырос.`,
  uk: (n) => `💖 Дякуємо за підтримку Office Dreams! <b>${n} ⭐</b> пішли у фонд запуску. Твій VIP-рівень зріс.`,
};
const thanksTxt = (lang, n) => (THANKS[lang] || THANKS.en)(n);
// the airdrop entry deposit paid in Stars
const ENTRY_TXT = {
  en: (n) => ['OFFX airdrop entry', `One-time ${n} ⭐ deposit: confirms you are a real player and goes to the launch fund. Not refunded, gives no points.`],
  ru: (n) => ['Участие в аирдропе OFFX', `Разовый депозит ${n} ⭐: подтверждает, что ты реальный игрок, и идёт в фонд запуска. Не возвращается и не даёт очков.`],
  uk: (n) => ['Участь в аірдропі OFFX', `Разовий депозит ${n} ⭐: підтверджує, що ти справжній гравець, і йде у фонд запуску. Не повертається й не дає балів.`],
};
const entryTxt = (lang, n) => (ENTRY_TXT[lang] || ENTRY_TXT.en)(n);
const VERIFIED = {
  en: '✅ <b>You are verified!</b> The deposit is received — you take part in the OFFX airdrop. Now just play: your share depends on your points.',
  ru: '✅ <b>Ты верифицирован!</b> Депозит получен — ты участвуешь в аирдропе OFFX. Дальше просто играй: твоя доля зависит от очков.',
  uk: '✅ <b>Ти верифікований!</b> Депозит отримано — ти береш участь в аірдропі OFFX. Далі просто грай: твоя частка залежить від балів.',
};
const shopTxt = (lang, sku, cash) => {
  const c = CASH_TXT[lang] || CASH_TXT.en;
  if (c[sku]) return [c[sku], c.d(nfmt(cash || 0))];
  return (SHOP_TXT[lang] || SHOP_TXT.en)[sku] || [sku, sku];
};
// /giveaway for players: prizes, time left, the conditions and their own place
const GW_TXT = {
  en: { none: '🎁 No giveaway right now. Giveaways run when a sponsor joins us — the next one is announced in the channel and here in the bot.', title: 'Sponsor giveaway', left: (h, m) => `⏳ ${h}h ${m}m left`, done: '🏁 Finished — results below.', prizes: 'Prizes', cond: 'Conditions', ver: 'verification ($1 deposit, Airdrop tab)', sub: 'subscription to the sponsor channel', me: (s, r) => `📈 Your points in the giveaway: <b>${s}</b>${r ? ` · place <b>#${r}</b>` : ''}`, top: 'Top', empty: 'Nobody qualifies yet — be the first!', how: 'Points are counted from the start of the giveaway: play, do tasks and walk the city. Prizes are sent in SOL to the wallet connected in the game.', subBtn: '📢 Subscribe to the channel' },
  ru: { none: '🎁 Сейчас розыгрыша нет. Розыгрыши проходят, когда у нас есть спонсор — о следующем объявим в канале и здесь, в боте.', title: 'Розыгрыш от спонсора', left: (h, m) => `⏳ Осталось ${h} ч ${m} мин`, done: '🏁 Завершён — итоги ниже.', prizes: 'Призы', cond: 'Условия', ver: 'верификация (депозит $1, вкладка Аирдроп)', sub: 'подписка на канал спонсора', me: (s, r) => `📈 Твои очки в розыгрыше: <b>${s}</b>${r ? ` · место <b>#${r}</b>` : ''}`, top: 'Топ', empty: 'Пока никто не прошёл условия — будь первым!', how: 'Очки считаются с начала розыгрыша: играй, выполняй задания, гуляй по городу. Призы приходят в SOL на кошелёк, подключённый в игре.', subBtn: '📢 Подписаться на канал' },
  uk: { none: '🎁 Зараз розіграшу немає. Розіграші проходять, коли в нас є спонсор — про наступний оголосимо в каналі й тут, у боті.', title: 'Розіграш від спонсора', left: (h, m) => `⏳ Лишилось ${h} год ${m} хв`, done: '🏁 Завершено — підсумки нижче.', prizes: 'Призи', cond: 'Умови', ver: 'верифікація (депозит $1, вкладка Аірдроп)', sub: 'підписка на канал спонсора', me: (s, r) => `📈 Твої бали в розіграші: <b>${s}</b>${r ? ` · місце <b>#${r}</b>` : ''}`, top: 'Топ', empty: 'Поки ніхто не виконав умови — будь першим!', how: 'Бали рахуються з початку розіграшу: грай, виконуй завдання, гуляй містом. Призи приходять у SOL на гаманець, підключений у грі.', subBtn: '📢 Підписатися на канал' },
};
function giveawayMsg(ctx, uid, lang, now = Date.now()) {
  const g = GW_TXT[lang] || GW_TXT.en;
  const v = require('./giveaway.js').view(ctx, uid, now);
  if (!v) return { text: g.none };
  const left = Math.max(0, v.end - now), h = Math.floor(left / 3600e3), mi = Math.floor((left % 3600e3) / 60e3);
  const mark = (ok) => (ok ? '✅' : '❌');
  const medal = ['🥇', '🥈', '🥉'];
  const top = v.top.slice(0, 5).map((r) => `${medal[r.rank - 1] || r.rank + '.'} ${esc(r.name)} — ${nfmt(r.score)}${r.prize ? ' · $' + r.prize : ''}${r.me ? ' ←' : ''}`).join('\n') || g.empty;
  const lines = [
    `🎁 <b>${g.title}</b>`, v.done ? g.done : g.left(h, mi), '',
    `🏆 ${g.prizes}: ${v.prizes.map((p, i) => medal[i] + ' $' + p).join('  ')}`, '',
    `<b>${g.cond}:</b>`, `${mark(v.verified)} ${g.ver}`,
  ];
  if (v.sub !== null) lines.push(`${mark(v.sub)} ${g.sub}`);
  lines.push('', g.me(nfmt(v.me.score), v.me.rank), '', `<b>${g.top}:</b>`, top, '', g.how);
  const rows = [];
  if (v.url && v.sub !== true) rows.push([{ text: g.subBtn, url: v.url }]);
  const pm = playMarkup(ctx, lang);
  if (pm) rows.push(pm.inline_keyboard[0]);
  return { text: lines.join('\n'), markup: rows.length ? { inline_keyboard: rows } : undefined };
}
const THANK_TXT = {
  en: (n) => `💖 <b>${n ? n + ', t' : 'T'}hank you so much!</b>\n\nYour contribution to the Office Dreams launch fund is the biggest of all — it truly helps the project grow. We really appreciate your support!\n\n— @vtmjs, creator of Office Dreams`,
  ru: (n) => `💖 <b>${n ? n + ', с' : 'С'}пасибо огромное!</b>\n\nТвой вклад в фонд запуска Office Dreams — самый большой. Это по-настоящему помогает проекту расти. Мы очень ценим твою поддержку!\n\n— @vtmjs, создатель Office Dreams`,
  uk: (n) => `💖 <b>${n ? n + ', щ' : 'Щ'}иро дякуємо!</b>\n\nТвій внесок у фонд запуску Office Dreams — найбільший. Це по-справжньому допомагає проєкту рости. Ми дуже цінуємо твою підтримку!\n\n— @vtmjs, творець Office Dreams`,
};
// owner: where the launch fund comes from — every counted payment, newest first, with totals per method
function fundReport(ctx) {
  const rows = ctx.db.all("SELECT p.charge_id AS id, p.uid, p.sku, p.stars, p.ts, p.meta, u.name, u.username FROM purchases p LEFT JOIN users u ON u.id=p.uid WHERE p.sku IN ('donate','adentry') ORDER BY p.ts DESC");
  const tot = { sol: [0, 0], stars: [0, 0] };
  const e = (v) => String(v == null ? '' : v).replace(/[<>&]/g, '');
  const lines = rows.map((r) => {
    let m = null; try { m = r.meta ? JSON.parse(r.meta) : null; } catch (x) {}
    const sol = String(r.id).startsWith('sol:');
    const usd = m && m.usd != null ? Number(m.usd) : r.sku === 'donate' ? r.stars * ctx.cfg.starUsd : 0;
    const k = sol ? 'sol' : 'stars'; tot[k][0] += usd; tot[k][1]++;
    const what = sol ? `◎ ${(m && m.sol ? Number(m.sol) : 0).toFixed(4)} SOL` : `⭐ ${r.stars || (m && m.stars) || 0}`;
    const d = new Date(r.ts).toISOString().slice(5, 16).replace('T', ' ');
    return `${d} · ${r.sku === 'adentry' ? 'верификация' : 'донат'} · ${what} = <b>$${usd.toFixed(2)}</b> · ${e(r.name)}${r.username ? ' @' + e(r.username) : ''}`;
  });
  const sum = tot.sol[0] + tot.stars[0];
  return `💰 <b>Фонд: $${sum.toFixed(2)}</b>\n⭐ Звёзды: $${tot.stars[0].toFixed(2)} (${tot.stars[1]} опл.)\n◎ SOL: $${tot.sol[0].toFixed(2)} (${tot.sol[1]} опл.)\n\n${lines.slice(0, 30).join('\n') || 'Оплат пока нет.'}${lines.length > 30 ? `\n… и ещё ${lines.length - 30}` : ''}`;
}
const appUrl = (ctx) => ctx.cfg.publicUrl + '/';

function devText() {
  return '🛠 <b>Проверка для разработчика</b>\n\n'
    + '🎨 <b>Новая графика</b> — твой настоящий аккаунт в стиле «Арт 2» (видишь только ты, остальным не включается).\n'
    + '🕹 <b>Старая графика</b> — вернуть обычный вид.\n'
    + '🧪 <b>Песочница</b> — отдельная тестовая игра с $5M и 500 💎: покупай, нажимай, ломай. Ничего не уходит на сервер, очки не настоящие.\n'
    + '♻️ <b>Новая песочница</b> — начать песочницу с нуля.';
}
function devMarkup(ctx) {
  if (!/^https:/.test(ctx.cfg.publicUrl)) return undefined;
  const b = (text, q) => ({ text, web_app: { url: appUrl(ctx) + q } });
  return { inline_keyboard: [[b('🎨 Новая графика', '?art=2'), b('🕹 Старая графика', '?art=1')], [b('🧪 Песочница', '?sandbox=1&art=2')], [b('♻️ Новая песочница', '?sandbox=new&art=2')]] };
}
const isOwner = (ctx, id) => (ctx.cfg.adminTgIds || []).includes(id) || (ctx.cfg.devIds || []).includes(Number(id));
async function send(ctx, chatId, text, markup) {
  const p = { chat_id: chatId, text, parse_mode: 'HTML', disable_web_page_preview: true };
  if (markup) p.reply_markup = markup;
  return ctx.tg.call('sendMessage', p);
}
function playMarkup(ctx, lang, start) {
  const t = tx(lang);
  const rows = [];
  if (/^https:/.test(ctx.cfg.publicUrl)) {
    rows.push([{ text: t.play, web_app: { url: appUrl(ctx) + (start ? '?start=' + encodeURIComponent(start) : '') } }]);
    rows.push([{ text: t.site, url: ctx.cfg.publicUrl + '/site' + (lang === 'en' ? '' : '?lang=' + lang) }]);
  }
  return rows.length ? { inline_keyboard: rows } : undefined;
}
function shareMarkup(ctx, lang, link) {
  const t = tx(lang);
  const url = 'https://t.me/share/url?url=' + encodeURIComponent(link) + '&text=' + encodeURIComponent(t.inviteText);
  return { inline_keyboard: [[{ text: t.share, url }]] };
}

async function handleMessage(ctx, m) {
  const from = m.from;
  if (!from || from.is_bot) return;
  const lang = U.langOf(from.language_code);
  const t = tx(lang);
  const text = String(m.text || '');
  if (m.successful_payment) return handlePayment(ctx, m);
  if (m.chat.type !== 'private') return;
  const cmd = /^\/(\w+)(?:@\w+)?(?:\s+(.*))?$/.exec(text);
  if (!cmd) return;
  const name = cmd[1].toLowerCase(), arg = (cmd[2] || '').trim();
  if (name === 'start' && arg === 'games') {
    // from the games site: Stars donation buttons for YOMPIX GAMES
    const mk = await Sp.invoiceMarkup(ctx, from.id);
    return send(ctx, m.chat.id, '🎮 <b>YOMPIX GAMES</b>\n\nСпасибо, что хочешь поддержать игры! Выбери сумму в звёздах Telegram ⭐\n\nКаждый, кто поддержит, попадает в список спонсоров на сайте и в титрах «Ночной смены». Имя в списке можно поменять командой /sponsor Имя', mk);
  } else if (name === 'sponsor') {
    return send(ctx, m.chat.id, Sp.setOwn(ctx, from.id, arg));
  } else if (name === 'sponsors' && isOwner(ctx, from.id)) {
    return send(ctx, m.chat.id, Sp.ownerReport(ctx));
  } else if ((name === 'sponsorhide' || name === 'sponsorshow') && isOwner(ctx, from.id)) {
    const id = Number(arg);
    if (!Number.isInteger(id) || id <= 0) return send(ctx, m.chat.id, 'Формат: /' + name + ' id (id из списка /sponsors)');
    Sp.setHidden(ctx, id, name === 'sponsorhide');
    return send(ctx, m.chat.id, Sp.ownerReport(ctx));
  } else if (name === 'start') {
    const code = U.parseRef(arg);
    if (code) U.setPendingRef(ctx, from.id, code, ctx.now());
    ctx.log && ctx.log('bot /start', from.id, arg || '(no param)');
    // a duel link is also an invite: the Play button carries it into the game
    await send(ctx, m.chat.id, t.start, playMarkup(ctx, lang, code ? (/^duel_/i.test(arg) ? 'duel_' : 'ref_') + code : ''));
  } else if (name === 'invite') {
    const u = U.getUser(ctx, from.id);
    if (!u) return send(ctx, m.chat.id, t.start, playMarkup(ctx, lang));
    const link = Eco.refLink(ctx, u.ref_code);
    if (!link) return;
    await send(ctx, m.chat.id, t.inviteMsg(link), shareMarkup(ctx, lang, link));
  } else if (name === 'help') {
    await send(ctx, m.chat.id, t.help, playMarkup(ctx, lang));
  } else if (name === 'play') {
    await send(ctx, m.chat.id, '🎮 Office Dreams', playMarkup(ctx, lang));
  } else if (name === 'site') {
    const pm = playMarkup(ctx, lang);
    await send(ctx, m.chat.id, '🌐 ' + ctx.cfg.publicUrl + '/site' + (lang === 'en' ? '' : '?lang=' + lang), pm && { inline_keyboard: [pm.inline_keyboard[1]] });
  } else if (name === 'giveaway' && !(isOwner(ctx, from.id) && arg !== 'me')) {
    const r = giveawayMsg(ctx, from.id, lang);
    await send(ctx, m.chat.id, r.text, r.markup);
  } else if (name === 'thank' && isOwner(ctx, from.id)) {
    // owner: /thank @username [own text] — a personal thank-you from the bot, in the player's language
    const mm = /^@?(\w{3,32})\s*([\s\S]*)$/.exec(arg);
    if (!mm) return send(ctx, m.chat.id, 'Формат: /thank @username [свой текст]');
    const u = ctx.db.get('SELECT id, name, lang FROM users WHERE lower(username)=lower(?) AND deleted=0', mm[1]);
    if (!u) return send(ctx, m.chat.id, `Игрок @${esc(mm[1])} не найден (он должен хотя бы раз открыть игру).`);
    const text = mm[2].trim() ? esc(mm[2].trim()) + '\n\n— @vtmjs, Office Dreams' : (THANK_TXT[u.lang] || THANK_TXT.en)(esc(u.name || ''));
    const r = await send(ctx, u.id, text, playMarkup(ctx, u.lang || 'en')).catch((e) => ({ ok: false, description: String(e && e.message) }));
    return send(ctx, m.chat.id, r && r.ok ? `✅ Отправлено @${esc(mm[1])}:\n\n${text}` : `⚠️ Не удалось отправить @${esc(mm[1])} (возможно, бот заблокирован): ${esc((r && r.description) || '')}`);
  } else if ((name === 'dev' || name === 'test') && isOwner(ctx, from.id)) {
    // owner: buttons to try what is new — the new look on the real account, or a sandbox (local demo with money, nothing reaches the server)
    return send(ctx, m.chat.id, devText(), devMarkup(ctx));
  } else if (name === 'posts' && isOwner(ctx, from.id)) {
    return send(ctx, m.chat.id, require('./posts.js').ownerReport(ctx, ctx.now()));
  } else if (name === 'postskip' && isOwner(ctx, from.id)) {
    const r = require('./posts.js').skip(ctx, arg, ctx.now());
    return send(ctx, m.chat.id, r === 'ok' ? `🚫 Пост <code>${esc(arg)}</code> отменён, в канал не уйдёт.` : r === 'sent' ? 'Этот пост уже опубликован.' : 'Формат: /postskip id (id из списка /posts)');
  } else if (name === 'fund' && isOwner(ctx, from.id)) {
    return send(ctx, m.chat.id, fundReport(ctx));
  } else if ((name === 'stats' || name === 'giveaway') && isOwner(ctx, from.id)) {
    // owner-only commands (ADMIN_TG_IDS or the developer accounts in DEV_IDS)
    if (name === 'stats') {
      const n = ctx.db.get('SELECT COUNT(*) AS n FROM users').n;
      const dau = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE last_seen>?', Date.now() - 86400e3).n;
      const ver = require('./airdrop.js').verifiedCount(ctx);
      const f = Eco.fundView(ctx, 0);
      return send(ctx, m.chat.id, `📊 <b>Office Dreams</b>\nPlayers: <b>${n}</b>\nActive 24h: <b>${dau}</b>\nVerified: <b>${ver}</b>\nFund: <b>$${f.usd}</b> / $${f.goalUsd}`);
    }
    const a = require('./giveaway.js').adminView(ctx);
    if (!a.giveaway) return send(ctx, m.chat.id, 'No giveaway running.');
    const g = a.giveaway, e = (v) => String(v == null ? '' : v).replace(/[<>&]/g, '');
    const left = Math.max(0, g.end - Date.now()), h = Math.floor(left / 3600e3), mi = Math.floor((left % 3600e3) / 60e3);
    const rows = a.top.length ? a.top.map((r) => `${r.rank}. <b>${e(r.name)}</b>${r.username ? ' @' + e(r.username) : ''} <code>${r.uid}</code> — ${r.score} pts${r.prize ? ' → $' + r.prize : ''} ${r.sol ? '👛' : '⚠️ no wallet'}${r.pay ? ' · ' + e(r.pay) : ''}`).join('\n') : 'Nobody qualifies yet (verified + subscribed).';
    return send(ctx, m.chat.id, `🎁 <b>Giveaway</b> · ${g.status}${g.status === 'live' ? ` · ${h}h ${mi}m left` : ''}\nPrizes: $${g.prizes.join(' / $')}\n\n${rows}\n\nPrize wallet: <code>${e(a.payWallet || '—')}</code>`);
  }
}

async function handlePayment(ctx, m) {
  const sp = m.successful_payment;
  const parts = String(sp.invoice_payload || '').split(':');
  const uid = Number(parts[0]), sku = parts[1];
  if (sku === Sp.SKU) {
    const n = Number(parts[2]);
    if (!uid || uid !== m.from.id || sp.currency !== 'XTR' || sp.total_amount !== n) return;
    const r = Sp.record(ctx, m.from, sp.telegram_payment_charge_id, n, ctx.now());
    if (!r.dup) await send(ctx, m.chat.id, `❤️ Спасибо за ${n} ⭐! Ты в списке спонсоров YOMPIX GAMES как «${esc(r.name)}».\n\nПоменять имя: /sponsor Имя\nСкрыть себя: /sponsor -`);
    return;
  }
  if (sku === 'donate') {
    const n = Number(parts[2]);
    if (!uid || uid !== m.from.id || sp.currency !== 'XTR' || sp.total_amount !== n) return;
    const r = Eco.fulfilDonation(ctx, uid, sp.telegram_payment_charge_id, n, ctx.now());
    if (r.ok && !r.dup) await send(ctx, m.chat.id, thanksTxt((U.getUser(ctx, uid) || {}).lang || 'en', n));
    return;
  }
  if (sku === 'adentry') {
    // paid already: the entry is recorded even if the snapshot happened meanwhile (the Stars were charged)
    const n = Number(parts[2]);
    if (!uid || uid !== m.from.id || sp.currency !== 'XTR' || sp.total_amount !== n) return;
    const r = require('./airdrop.js').fulfilEntryStars(ctx, uid, sp.telegram_payment_charge_id, n, ctx.now());
    if (r.ok && !r.dup) { const lang = (U.getUser(ctx, uid) || {}).lang || 'en'; await send(ctx, m.chat.id, VERIFIED[lang] || VERIFIED.en); }
    return;
  }
  if (!uid || uid !== m.from.id || !Eco.shopItem(sku) || sp.currency !== 'XTR') return;
  const it = Eco.shopItem(sku);
  if (sp.total_amount !== it.stars) return;
  const r = Eco.fulfil(ctx, uid, sku, sp.telegram_payment_charge_id, sp.total_amount, ctx.now());
  if (r.ok && !r.dup) {
    const lang = (U.getUser(ctx, uid) || {}).lang || 'en';
    await send(ctx, m.chat.id, tx(lang).paid(shopTxt(lang, sku)[0]));
  }
}

async function handleUpdate(ctx, up) {
  try {
    if (up.pre_checkout_query) {
      const q = up.pre_checkout_query;
      const parts = String(q.invoice_payload || '').split(':');
      const it = Eco.shopItem(parts[1]);
      const dn = parts[1] === 'donate' ? Number(parts[2]) : 0;
      const goodDonate = dn > 0 && Number(parts[0]) === q.from.id && q.currency === 'XTR' && q.total_amount === dn && dn >= ctx.cfg.donate.minStars && dn <= ctx.cfg.donate.maxStars;
      const AD = parts[1] === 'adentry' ? require('./airdrop.js') : null;
      const goodEntry = !!AD && Number(parts[0]) === q.from.id && q.currency === 'XTR' && q.total_amount === Number(parts[2]) && q.total_amount === AD.entryStars(ctx.cfg) && !AD.entryBlock(ctx, q.from.id);
      const good = goodDonate || goodEntry || Sp.validCheckout(q, parts) || it && Number(parts[0]) === q.from.id && q.currency === 'XTR' && q.total_amount === it.stars && Eco.canBuy(ctx, q.from.id, it.sku);
      await ctx.tg.call('answerPreCheckoutQuery', good ? { pre_checkout_query_id: q.id, ok: true } : { pre_checkout_query_id: q.id, ok: false, error_message: 'This item is not available.' });
    } else if (up.message) {
      await handleMessage(ctx, up.message);
    } else if (up.my_chat_member) {
      const c = up.my_chat_member;
      if (c.chat.type === 'private') {
        const blocked = ['kicked', 'left'].includes(c.new_chat_member.status) ? 1 : 0;
        ctx.db.run('UPDATE users SET blocked=? WHERE id=?', blocked, c.from.id);
      } else if (c.chat.type === 'channel' || c.chat.type === 'supergroup') {
        ctx.log('bot membership in', c.chat.id, c.chat.title, c.chat.username || '', '→', c.new_chat_member.status);
        if (c.new_chat_member.status === 'administrator') require('./giveaway.js').learnChat(ctx, c.chat, c.from && c.from.id).catch(() => {});
      }
    }
  } catch (e) {
    ctx.log('bot update error:', e && e.stack || e);
  }
}

async function notifyReferral(ctx, referrerId, friendName) {
  const r = U.getUser(ctx, referrerId);
  if (!r || r.blocked || r.deleted) return;
  const n = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE ref1=? AND created_at>?', referrerId, Date.now() - 86400e3).n;
  if (n > 20) return; // never spam
  await send(ctx, referrerId, tx(r.lang).joined(friendName));
}

const ACT_TXT = {
  en: (n, p) => `🎉 <b>${esc(n)}</b> released their first game — your invite counts! You got <b>+${p} points</b>, coins and gems.`,
  ru: (n, p) => `🎉 <b>${esc(n)}</b> выпустил свою первую игру — приглашение засчитано! Тебе <b>+${p} очков</b>, монеты и кристаллы.`,
  uk: (n, p) => `🎉 <b>${esc(n)}</b> випустив свою першу гру — запрошення зараховано! Тобі <b>+${p} балів</b>, монети й кристали.`,
};
// a friend accepted your Studio Battle challenge
const DUEL_TXT = {
  en: (n, won, a, b) => `⚔️ <b>${n}</b> accepted your Studio Battle challenge and ${won ? 'beat you' : 'lost'}: <b>${a} : ${b}</b>. ${won ? 'Time for a rematch!' : 'Your studio held on! 💪'}`,
  ru: (n, won, a, b) => `⚔️ <b>${n}</b> принял твой вызов в Битве студий и ${won ? 'победил' : 'проиграл'}: <b>${a} : ${b}</b>. ${won ? 'Пора на реванш!' : 'Твоя студия выстояла! 💪'}`,
  uk: (n, won, a, b) => `⚔️ <b>${n}</b> прийняв твій виклик у Битві студій і ${won ? 'переміг' : 'програв'}: <b>${a} : ${b}</b>. ${won ? 'Час на реванш!' : 'Твоя студія вистояла! 💪'}`,
};
async function notifyDuel(ctx, challengerId, friendName, friendWon, friendScore, myScore) {
  const r = U.getUser(ctx, challengerId);
  if (!r || r.blocked || r.deleted) return;
  const esc = (x) => String(x).replace(/[<>&]/g, '');
  await send(ctx, challengerId, (DUEL_TXT[r.lang] || DUEL_TXT.en)(esc(friendName), friendWon, friendScore, myScore), playMarkup(ctx, r.lang));
}
async function notifyRefActive(ctx, referrerId, friendName, pts) {
  const r = U.getUser(ctx, referrerId);
  if (!r || r.blocked || r.deleted) return;
  await send(ctx, referrerId, (ACT_TXT[r.lang] || ACT_TXT.en)(friendName, Math.round(pts)));
}

/* one-time setup: commands, menu button, descriptions, webhook */
async function setup(ctx) {
  const { cfg, tg } = ctx;
  const out = {};
  if (!cfg.botToken) return { error: 'BOT_TOKEN is not set' };
  out.me = await tg.call('getMe');
  for (const lang of ['en', 'ru', 'uk']) {
    const t = tx(lang);
    const extra = lang === 'en' ? {} : { language_code: lang };
    await tg.call('setMyCommands', { commands: [['play', t.cmdPlay], ['giveaway', t.cmdGiveaway], ['invite', t.cmdInvite], ['site', t.cmdSite], ['help', t.cmdHelp], ['start', t.cmdStart]].map(([command, description]) => ({ command, description })), ...extra });
    await tg.call('setMyDescription', { description: t.desc, ...extra });
    await tg.call('setMyShortDescription', { short_description: t.short, ...extra });
  }
  if (/^https:/.test(cfg.publicUrl)) {
    out.menu = await tg.call('setChatMenuButton', { menu_button: { type: 'web_app', text: 'Play', web_app: { url: appUrl(ctx) } } });
  }
  if (cfg.botMode === 'webhook' && /^https:/.test(cfg.publicUrl)) {
    out.webhook = await tg.call('setWebhook', {
      url: `${cfg.publicUrl}/tg/${cfg.webhookSecret}`,
      secret_token: cfg.webhookSecret,
      allowed_updates: ['message', 'pre_checkout_query', 'my_chat_member'],
      drop_pending_updates: false,
    });
  } else if (cfg.botMode === 'polling') {
    out.webhook = await tg.call('deleteWebhook', { drop_pending_updates: false });
  }
  return out;
}

/* long polling (works without a public HTTPS address — handy for testing) */
function startPolling(ctx) {
  let stop = false;
  let offset = Number(ctx.db.kvGet('tg_offset', 0));
  (async () => {
    while (!stop) {
      const r = await ctx.tg.call('getUpdates', { offset, timeout: 25, allowed_updates: ['message', 'pre_checkout_query', 'my_chat_member'] }, 40000);
      if (!r.ok) { await new Promise((res) => setTimeout(res, r.network ? 3000 : 1500)); continue; }
      for (const up of r.result) {
        offset = up.update_id + 1;
        await handleUpdate(ctx, up);
      }
      if (r.result.length) ctx.db.kvSet('tg_offset', offset);
    }
  })().catch((e) => ctx.log('polling crashed', e));
  return { stop() { stop = true; } };
}

module.exports = { devMarkup, fundReport, giveawayMsg, playMarkup, T, tx, shopTxt, donateTxt, thanksTxt, entryTxt, notifyRefActive, notifyDuel, handleUpdate, notifyReferral, setup, startPolling, send };
