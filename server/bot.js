'use strict';
/* Telegram bot: /start (+ referral deep links), Stars payments, webhook or long polling. */
const U = require('./users.js');
const Eco = require('./economy.js');

const esc = (s) => String(s == null ? '' : s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));

const T = {
  en: {
    start: '🏢 <b>Office Dreams</b>\n\nBuild a neon game studio: hire a team, ship hit games, climb floor by floor and collect airdrop points.\n\n👥 Invite friends — you both get bonuses and you earn a share of their points.\n\nTap <b>Play</b> to begin!',
    play: '🎮 Play', invite: '👥 Invite friends', share: '📤 Share link',
    inviteText: '🏢 Come build a neon game studio with me in Office Dreams! We both get bonuses:',
    inviteMsg: (link) => `👥 <b>Your invite link</b>\n\n<code>${link}</code>\n\nFor every friend who really plays you get bonus coins and ${'10%'} of their points.`,
    help: '❓ <b>Help</b>\n\n/start — open the game\n/invite — your invite link\n/help — this message\n\nPoints are earned by playing, daily check-ins, tasks and inviting friends. Bots are filtered automatically.',
    joined: (n) => `🎉 Your friend <b>${esc(n)}</b> joined Office Dreams! Bonuses arrive when they start playing for real.`,
    paid: (t) => `✅ Payment received: <b>${esc(t)}</b>. Enjoy!`,
    cmdStart: 'Open the game', cmdInvite: 'Invite friends', cmdHelp: 'Help',
    desc: 'Neon game-studio tycoon. Hire a team, ship games, climb 5 floors, invite friends and collect airdrop points. Play right here in Telegram!',
    short: 'Build a neon game studio · play, invite, earn airdrop points',
  },
  ru: {
    start: '🏢 <b>Office Dreams</b>\n\nСтрой неоновую студию игр: нанимай команду, выпускай хиты, поднимайся с этажа на этаж и собирай поинты для аирдропа.\n\n👥 Приглашай друзей — бонусы получаете оба, а ты ещё и долю их очков.\n\nЖми <b>Играть</b>!',
    play: '🎮 Играть', invite: '👥 Пригласить друзей', share: '📤 Поделиться',
    inviteText: '🏢 Присоединяйся ко мне в Office Dreams — строим неоновую студию игр! Бонусы получим оба:',
    inviteMsg: (link) => `👥 <b>Твоя ссылка-приглашение</b>\n\n<code>${link}</code>\n\nЗа каждого друга, который реально играет, ты получаешь бонусные монеты и 10% его очков.`,
    help: '❓ <b>Помощь</b>\n\n/start — открыть игру\n/invite — ссылка для друзей\n/help — это сообщение\n\nОчки дают игра, ежедневный вход, задания и друзья. Боты отсеиваются автоматически.',
    joined: (n) => `🎉 Твой друг <b>${esc(n)}</b> зашёл в Office Dreams! Бонусы придут, когда он начнёт играть по-настоящему.`,
    paid: (t) => `✅ Оплата получена: <b>${esc(t)}</b>. Приятной игры!`,
    cmdStart: 'Открыть игру', cmdInvite: 'Пригласить друзей', cmdHelp: 'Помощь',
    desc: 'Неоновый тайкун игровой студии. Нанимай команду, выпускай игры, покоряй 5 этажей, приглашай друзей и собирай поинты для аирдропа. Играй прямо в Telegram!',
    short: 'Строй неоновую студию игр · играй, приглашай, собирай поинты',
  },
  uk: {
    start: '🏢 <b>Office Dreams</b>\n\nБудуй неонову студію ігор: наймай команду, випускай хіти, піднімайся з поверху на поверх і збирай поінти для аірдропу.\n\n👥 Запрошуй друзів — бонуси отримуєте обоє, а ти ще й частку їхніх очок.\n\nНатисни <b>Грати</b>!',
    play: '🎮 Грати', invite: '👥 Запросити друзів', share: '📤 Поділитися',
    inviteText: '🏢 Приєднуйся до мене в Office Dreams — будуємо неонову студію ігор! Бонуси отримаємо обоє:',
    inviteMsg: (link) => `👥 <b>Твоє посилання-запрошення</b>\n\n<code>${link}</code>\n\nЗа кожного друга, який справді грає, ти отримуєш бонусні монети та 10% його очок.`,
    help: '❓ <b>Допомога</b>\n\n/start — відкрити гру\n/invite — посилання для друзів\n/help — це повідомлення\n\nОчки дають гра, щоденний вхід, завдання та друзі. Боти відсіюються автоматично.',
    joined: (n) => `🎉 Твій друг <b>${esc(n)}</b> зайшов в Office Dreams! Бонуси прийдуть, коли він почне грати по-справжньому.`,
    paid: (t) => `✅ Оплату отримано: <b>${esc(t)}</b>. Гарної гри!`,
    cmdStart: 'Відкрити гру', cmdInvite: 'Запросити друзів', cmdHelp: 'Допомога',
    desc: 'Неоновий тайкун ігрової студії. Наймай команду, випускай ігри, підкорюй 5 поверхів, запрошуй друзів і збирай поінти для аірдропу. Грай прямо в Telegram!',
    short: 'Будуй неонову студію ігор · грай, запрошуй, збирай поінти',
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
  en: (n) => ['Support the launch', `${n} ⭐ toward the OFFICE token launch. Thank you!`],
  ru: (n) => ['Поддержка запуска', `${n} ⭐ на запуск токена OFFICE. Спасибо!`],
  uk: (n) => ['Підтримка запуску', `${n} ⭐ на запуск токена OFFICE. Дякуємо!`],
};
const donateTxt = (lang, n) => (DONATE_TXT[lang] || DONATE_TXT.en)(n);
const THANKS = {
  en: (n) => `💖 Thank you for supporting Office Dreams! <b>${n} ⭐</b> went to the launch fund. Your VIP level has grown.`,
  ru: (n) => `💖 Спасибо за поддержку Office Dreams! <b>${n} ⭐</b> ушли в фонд запуска. Твой VIP-уровень вырос.`,
  uk: (n) => `💖 Дякуємо за підтримку Office Dreams! <b>${n} ⭐</b> пішли у фонд запуску. Твій VIP-рівень зріс.`,
};
const thanksTxt = (lang, n) => (THANKS[lang] || THANKS.en)(n);
const shopTxt = (lang, sku, cash) => {
  const c = CASH_TXT[lang] || CASH_TXT.en;
  if (c[sku]) return [c[sku], c.d(nfmt(cash || 0))];
  return (SHOP_TXT[lang] || SHOP_TXT.en)[sku] || [sku, sku];
};
const appUrl = (ctx) => ctx.cfg.publicUrl + '/';

async function send(ctx, chatId, text, markup) {
  const p = { chat_id: chatId, text, parse_mode: 'HTML', disable_web_page_preview: true };
  if (markup) p.reply_markup = markup;
  return ctx.tg.call('sendMessage', p);
}
function playMarkup(ctx, lang) {
  const t = tx(lang);
  const rows = [];
  if (/^https:/.test(ctx.cfg.publicUrl)) rows.push([{ text: t.play, web_app: { url: appUrl(ctx) } }]);
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
  if (name === 'start') {
    const code = U.parseRef(arg);
    if (code) U.setPendingRef(ctx, from.id, code, ctx.now());
    await send(ctx, m.chat.id, t.start, playMarkup(ctx, lang));
  } else if (name === 'invite') {
    const u = U.getUser(ctx, from.id);
    if (!u) return send(ctx, m.chat.id, t.start, playMarkup(ctx, lang));
    const link = Eco.refLink(ctx, u.ref_code);
    if (!link) return;
    await send(ctx, m.chat.id, t.inviteMsg(link), shareMarkup(ctx, lang, link));
  } else if (name === 'help') {
    await send(ctx, m.chat.id, t.help);
  } else if (name === 'stats' && ctx.cfg.adminTgIds.includes(from.id)) {
    const n = ctx.db.get('SELECT COUNT(*) AS n FROM users').n;
    const dau = ctx.db.get('SELECT COUNT(*) AS n FROM users WHERE last_seen>?', Date.now() - 86400e3).n;
    await send(ctx, m.chat.id, `Users: <b>${n}</b>\nDAU: <b>${dau}</b>`);
  }
}

async function handlePayment(ctx, m) {
  const sp = m.successful_payment;
  const parts = String(sp.invoice_payload || '').split(':');
  const uid = Number(parts[0]), sku = parts[1];
  if (sku === 'donate') {
    const n = Number(parts[2]);
    if (!uid || uid !== m.from.id || sp.currency !== 'XTR' || sp.total_amount !== n) return;
    const r = Eco.fulfilDonation(ctx, uid, sp.telegram_payment_charge_id, n, ctx.now());
    if (r.ok && !r.dup) await send(ctx, m.chat.id, thanksTxt((U.getUser(ctx, uid) || {}).lang || 'en', n));
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
      const good = goodDonate || it && Number(parts[0]) === q.from.id && q.currency === 'XTR' && q.total_amount === it.stars && Eco.canBuy(ctx, q.from.id, it.sku);
      await ctx.tg.call('answerPreCheckoutQuery', good ? { pre_checkout_query_id: q.id, ok: true } : { pre_checkout_query_id: q.id, ok: false, error_message: 'This item is not available.' });
    } else if (up.message) {
      await handleMessage(ctx, up.message);
    } else if (up.my_chat_member) {
      const c = up.my_chat_member;
      if (c.chat.type === 'private') {
        const blocked = ['kicked', 'left'].includes(c.new_chat_member.status) ? 1 : 0;
        ctx.db.run('UPDATE users SET blocked=? WHERE id=?', blocked, c.from.id);
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
    await tg.call('setMyCommands', { commands: [{ command: 'start', description: t.cmdStart }, { command: 'invite', description: t.cmdInvite }, { command: 'help', description: t.cmdHelp }], ...extra });
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

module.exports = { T, tx, shopTxt, donateTxt, thanksTxt, notifyRefActive, handleUpdate, notifyReferral, setup, startPolling, send };
