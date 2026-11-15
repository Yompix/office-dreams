'use strict';
/* Bot texts. ru is used for ru/uk/be/kk, en for everyone else. Add a language = add a block. */
const T = {
  ru: {
    hello: (b, nodes) => `🛡 <b>${b}</b> — быстрый VPN, который работает там, где другие блокируют.\n\n⚡ VLESS Reality и Hysteria2: трафик выглядит как обычный сайт, DPI его не видит.\n🌍 Серверы: ${nodes || 'скоро'}\n📱 Android, iPhone, Windows, Mac — одна подписка на все устройства.`,
    active: (d) => `✅ Подписка активна до <b>${d}</b>`,
    none: '❌ Подписки нет.',
    bApp: '🚀 Открыть YURK', bTrial: (n) => `🎁 Бесплатно ${n} дн.`, bBuy: '💳 Купить / продлить', bKey: '🔑 Мой ключ', bHelp: '📲 Как подключить',
    bRef: '👥 Пригласить друга', bSupport: '💬 Поддержка', bBack: '« Назад', bConnect: '📲 Подключить в один клик', bReset: '🔄 Сменить ключ',
    trialOk: (d) => `🎁 Пробный период включён до <b>${d}</b>.\nЖмите «Мой ключ» и подключайтесь.`,
    trialUsed: 'Пробный период уже был. Выберите тариф 👇',
    plans: '💳 <b>Тарифы</b>\nОплата звёздами Telegram, без карт. Дни добавляются к текущей подписке.',
    plan: (p) => `${planName(p, 'ru')} — ${p.stars} ⭐`,
    invTitle: (b, p) => `${b}: ${planName(p, 'ru')}`, invDesc: (p) => `VPN на ${p.days} дн. Все серверы и устройства.`,
    paid: (d) => `✅ Оплата получена, спасибо! Подписка до <b>${d}</b>.`,
    noSub: 'Подписки нет — сначала включите пробный период или купите тариф.',
    key: (url, d) => `🔑 <b>Ваша подписка</b> (до ${d})\n\n1. Нажмите «Подключить в один клик» и выберите приложение.\n2. Или скопируйте ссылку и добавьте её в приложении как «подписку»:\n<code>${url}</code>\n\n⚠️ Не делитесь ссылкой: по ней подключается любой.`,
    reset: '🔄 Ключ сменён. Старая ссылка перестанет работать через минуту — добавьте новую в приложение.',
    help: '📲 <b>Как подключить</b>\n\n<b>1. Установите приложение</b>\n• Android — Happ, v2RayTun или Hiddify (Google Play)\n• iPhone / iPad — Happ, v2RayTun или Streisand (App Store)\n• Windows / Mac — Happ или Hiddify\n\n<b>2.</b> Нажмите «🔑 Мой ключ» → «Подключить в один клик».\n<b>3.</b> Включите VPN в приложении. Если один сервер не работает — выберите другой (⚡ — Hysteria2, быстрее на мобильном).',
    ref: (link, n) => `👥 <b>Приглашайте друзей</b>\nЗа каждого друга, который оплатит подписку, вы получите <b>+${n} дней</b>.\n\nВаша ссылка:\n${link}`,
    refBonus: (n) => `🎉 Ваш друг оплатил подписку — вам +${n} дней!`,
    warn: (d) => `⏰ Подписка заканчивается ${d}. Продлите, чтобы VPN не отключился.`,
  },
  en: {
    hello: (b, nodes) => `🛡 <b>${b}</b> — a fast VPN that works where others get blocked.\n\n⚡ VLESS Reality and Hysteria2: your traffic looks like a normal website, DPI can't see it.\n🌍 Servers: ${nodes || 'soon'}\n📱 Android, iPhone, Windows, Mac — one subscription for all devices.`,
    active: (d) => `✅ Subscription active until <b>${d}</b>`,
    none: '❌ No subscription.',
    bApp: '🚀 Open YURK', bTrial: (n) => `🎁 Free ${n} days`, bBuy: '💳 Buy / renew', bKey: '🔑 My key', bHelp: '📲 How to connect',
    bRef: '👥 Invite a friend', bSupport: '💬 Support', bBack: '« Back', bConnect: '📲 One-tap connect', bReset: '🔄 New key',
    trialOk: (d) => `🎁 Free trial is on until <b>${d}</b>.\nTap "My key" to connect.`,
    trialUsed: 'You already used the free trial. Pick a plan 👇',
    plans: '💳 <b>Plans</b>\nPay with Telegram Stars, no card needed. Days are added to your current subscription.',
    plan: (p) => `${planName(p, 'en')} — ${p.stars} ⭐`,
    invTitle: (b, p) => `${b}: ${planName(p, 'en')}`, invDesc: (p) => `VPN for ${p.days} days. All servers and devices.`,
    paid: (d) => `✅ Payment received, thank you! Subscription until <b>${d}</b>.`,
    noSub: 'No subscription yet — start the free trial or buy a plan first.',
    key: (url, d) => `🔑 <b>Your subscription</b> (until ${d})\n\n1. Tap "One-tap connect" and pick your app.\n2. Or copy the link and add it in the app as a "subscription":\n<code>${url}</code>\n\n⚠️ Don't share this link: anyone can connect with it.`,
    reset: '🔄 Key changed. The old link stops working within a minute — add the new one to your app.',
    help: '📲 <b>How to connect</b>\n\n<b>1. Install an app</b>\n• Android — Happ, v2RayTun or Hiddify (Google Play)\n• iPhone / iPad — Happ, v2RayTun or Streisand (App Store)\n• Windows / Mac — Happ or Hiddify\n\n<b>2.</b> Tap "🔑 My key" → "One-tap connect".\n<b>3.</b> Turn the VPN on. If one server fails, pick another (⚡ = Hysteria2, faster on mobile).',
    ref: (link, n) => `👥 <b>Invite friends</b>\nFor every friend who buys a subscription you get <b>+${n} days</b>.\n\nYour link:\n${link}`,
    refBonus: (n) => `🎉 Your friend bought a subscription — you get +${n} days!`,
    warn: (d) => `⏰ Your subscription ends ${d}. Renew so the VPN keeps working.`,
  },
};

function planName(p, lang) {
  const ru = lang === 'ru';
  if (p.days % 365 === 0) { const y = p.days / 365; return ru ? (y === 1 ? '1 год' : `${y} года`) : (y === 1 ? '1 year' : `${y} years`); }
  if (p.days % 30 === 0) { const m = p.days / 30; return ru ? `${m} мес.` : (m === 1 ? '1 month' : `${m} months`); }
  return ru ? `${p.days} дн.` : `${p.days} days`;
}

const RU_LIKE = new Set(['ru', 'uk', 'be', 'kk', 'ky', 'uz', 'tg', 'tk', 'az', 'hy', 'ka']);
const pick = (lang) => (RU_LIKE.has(lang) ? T.ru : T.en);

module.exports = { T, pick, planName };
