'use strict';
/* Two-level partner program.
   Level 1 (who invited the buyer) gets ref1Pct %, level 2 (who invited the inviter) gets ref2Pct %
   of what the owner really received for every paid subscription (after Telegram / CryptoBot / card fees).
   Plus refDays free days to level 1 on the friend's first payment.
   Money goes to an in-app balance (cents): spend it on the subscription or withdraw USDT via @CryptoBot (owner approves). */

const R = {
  ru: {
    ref: (o) => `👥 <b>Партнёрка YURK</b>\n\n💸 <b>${o.p1}%</b> с каждой оплаты приглашённого друга — навсегда\n🔗 <b>${o.p2}%</b> с оплат друзей ваших друзей\n🎁 <b>+${o.days} дня</b> подписки за первую оплату друга\n\nДрузей: <b>${o.l1}</b> · их друзей: <b>${o.l2}</b>\nЗаработано: <b>$${o.earned}</b> · баланс: <b>$${o.bal}</b>\n\nБаланс можно потратить на подписку или вывести в USDT от $${o.min} (в приложении 🚀).\n\nВаша ссылка:\n${o.link}`,
    earn: (a, lvl) => `💸 +$${a} на баланс — ${lvl === 1 ? 'ваш друг' : 'друг вашего друга'} оплатил подписку!`,
    days: (n) => `🎁 Ваш друг впервые оплатил подписку — вам +${n} дня!`,
    wdReq: (a) => `⏳ Заявка на вывод $${a} в USDT принята. Деньги придут в @CryptoBot после проверки.`,
    wdPaid: (a) => `✅ $${a} USDT отправлены вам в @CryptoBot.`,
    wdNo: (a) => `↩️ Вывод $${a} отклонён, деньги вернулись на баланс. Напишите в поддержку, если есть вопросы.`,
  },
  uk: {
    ref: (o) => `👥 <b>Партнерка YURK</b>\n\n💸 <b>${o.p1}%</b> з кожної оплати запрошеного друга — назавжди\n🔗 <b>${o.p2}%</b> з оплат друзів ваших друзів\n🎁 <b>+${o.days} дні</b> підписки за першу оплату друга\n\nДрузів: <b>${o.l1}</b> · їхніх друзів: <b>${o.l2}</b>\nЗароблено: <b>$${o.earned}</b> · баланс: <b>$${o.bal}</b>\n\nБаланс можна витратити на підписку або вивести в USDT від $${o.min} (у застосунку 🚀).\n\nВаше посилання:\n${o.link}`,
    earn: (a, lvl) => `💸 +$${a} на баланс — ${lvl === 1 ? 'ваш друг' : 'друг вашого друга'} оплатив підписку!`,
    days: (n) => `🎁 Ваш друг уперше оплатив підписку — вам +${n} дні!`,
    wdReq: (a) => `⏳ Заявку на виведення $${a} у USDT прийнято. Гроші прийдуть у @CryptoBot після перевірки.`,
    wdPaid: (a) => `✅ $${a} USDT надіслано вам у @CryptoBot.`,
    wdNo: (a) => `↩️ Виведення $${a} відхилено, гроші повернулися на баланс.`,
  },
  en: {
    ref: (o) => `👥 <b>YURK partner program</b>\n\n💸 <b>${o.p1}%</b> of every payment of a friend you invited — forever\n🔗 <b>${o.p2}%</b> of payments of your friends' friends\n🎁 <b>+${o.days} days</b> of subscription for a friend's first payment\n\nFriends: <b>${o.l1}</b> · their friends: <b>${o.l2}</b>\nEarned: <b>$${o.earned}</b> · balance: <b>$${o.bal}</b>\n\nSpend the balance on your subscription or withdraw USDT from $${o.min} (in the app 🚀).\n\nYour link:\n${o.link}`,
    earn: (a, lvl) => `💸 +$${a} to your balance — ${lvl === 1 ? 'your friend' : "your friend's friend"} bought a subscription!`,
    days: (n) => `🎁 Your friend made the first payment — you get +${n} days!`,
    wdReq: (a) => `⏳ Withdrawal of $${a} USDT requested. It arrives in @CryptoBot after a check.`,
    wdPaid: (a) => `✅ $${a} USDT sent to you in @CryptoBot.`,
    wdNo: (a) => `↩️ Withdrawal of $${a} declined, the money is back on your balance.`,
  },
  fa: {
    ref: (o) => `👥 <b>برنامه همکاری YURK</b>\n\n💸 <b>${o.p1}٪</b> از هر پرداخت دوست دعوت‌شده — برای همیشه\n🔗 <b>${o.p2}٪</b> از پرداخت‌های دوستانِ دوستان شما\n🎁 <b>${o.days}+ روز</b> اشتراک برای اولین پرداخت دوست\n\nدوستان: <b>${o.l1}</b> · دوستانِ آن‌ها: <b>${o.l2}</b>\nدرآمد: <b>$${o.earned}</b> · موجودی: <b>$${o.bal}</b>\n\nموجودی را برای اشتراک خرج کنید یا از $${o.min} به USDT برداشت کنید (در اپ 🚀).\n\nلینک شما:\n${o.link}`,
    earn: (a, lvl) => `💸 $${a}+ به موجودی شما — ${lvl === 1 ? 'دوست شما' : 'دوستِ دوست شما'} اشتراک خرید!`,
    days: (n) => `🎁 دوست شما اولین پرداخت را انجام داد — ${n}+ روز برای شما!`,
    wdReq: (a) => `⏳ درخواست برداشت $${a} USDT ثبت شد. پس از بررسی به @CryptoBot می‌رسد.`,
    wdPaid: (a) => `✅ $${a} USDT به @CryptoBot شما ارسال شد.`,
    wdNo: (a) => `↩️ برداشت $${a} رد شد و مبلغ به موجودی برگشت.`,
  },
  tr: {
    ref: (o) => `👥 <b>YURK ortaklık programı</b>\n\n💸 Davet ettiğiniz arkadaşın her ödemesinin <b>%${o.p1}</b>'i — süresiz\n🔗 Arkadaşlarınızın arkadaşlarının ödemelerinin <b>%${o.p2}</b>'i\n🎁 Arkadaşın ilk ödemesinde <b>+${o.days} gün</b> abonelik\n\nArkadaş: <b>${o.l1}</b> · onların arkadaşları: <b>${o.l2}</b>\nKazanç: <b>$${o.earned}</b> · bakiye: <b>$${o.bal}</b>\n\nBakiyeyi abonelikte kullanın veya $${o.min}'dan itibaren USDT olarak çekin (uygulamada 🚀).\n\nBağlantınız:\n${o.link}`,
    earn: (a, lvl) => `💸 Bakiyenize +$${a} — ${lvl === 1 ? 'arkadaşınız' : 'arkadaşınızın arkadaşı'} abonelik aldı!`,
    days: (n) => `🎁 Arkadaşınız ilk ödemesini yaptı — size +${n} gün!`,
    wdReq: (a) => `⏳ $${a} USDT çekim talebi alındı. Kontrolden sonra @CryptoBot'a gelir.`,
    wdPaid: (a) => `✅ $${a} USDT @CryptoBot hesabınıza gönderildi.`,
    wdNo: (a) => `↩️ $${a} çekim reddedildi, para bakiyenize döndü.`,
  },
  zh: {
    ref: (o) => `👥 <b>YURK 推广计划</b>\n\n💸 好友每次付款您获得 <b>${o.p1}%</b> — 永久\n🔗 好友的好友付款您获得 <b>${o.p2}%</b>\n🎁 好友首次付款，您获得 <b>+${o.days} 天</b>订阅\n\n好友：<b>${o.l1}</b> · 二级好友：<b>${o.l2}</b>\n已赚：<b>$${o.earned}</b> · 余额：<b>$${o.bal}</b>\n\n余额可用于支付订阅，满 $${o.min} 可提现为 USDT（在应用内 🚀）。\n\n您的链接：\n${o.link}`,
    earn: (a, lvl) => `💸 余额 +$${a} — ${lvl === 1 ? '您的好友' : '好友的好友'}购买了订阅！`,
    days: (n) => `🎁 您的好友首次付款 — 您获得 +${n} 天！`,
    wdReq: (a) => `⏳ 已收到 $${a} USDT 提现申请，审核后将转入 @CryptoBot。`,
    wdPaid: (a) => `✅ $${a} USDT 已转入您的 @CryptoBot。`,
    wdNo: (a) => `↩️ $${a} 提现被拒绝，金额已退回余额。`,
  },
  es: {
    ref: (o) => `👥 <b>Programa de socios YURK</b>\n\n💸 <b>${o.p1}%</b> de cada pago del amigo que invites — para siempre\n🔗 <b>${o.p2}%</b> de los pagos de los amigos de tus amigos\n🎁 <b>+${o.days} días</b> de suscripción por el primer pago de un amigo\n\nAmigos: <b>${o.l1}</b> · sus amigos: <b>${o.l2}</b>\nGanado: <b>$${o.earned}</b> · saldo: <b>$${o.bal}</b>\n\nUsa el saldo para tu suscripción o retíralo en USDT desde $${o.min} (en la app 🚀).\n\nTu enlace:\n${o.link}`,
    earn: (a, lvl) => `💸 +$${a} a tu saldo — ${lvl === 1 ? 'tu amigo' : 'el amigo de tu amigo'} compró una suscripción!`,
    days: (n) => `🎁 Tu amigo hizo su primer pago — ¡+${n} días para ti!`,
    wdReq: (a) => `⏳ Retiro de $${a} USDT solicitado. Llegará a @CryptoBot tras la revisión.`,
    wdPaid: (a) => `✅ $${a} USDT enviados a tu @CryptoBot.`,
    wdNo: (a) => `↩️ Retiro de $${a} rechazado, el dinero volvió a tu saldo.`,
  },
};
const RU_LIKE = new Set(['be', 'kk', 'ky', 'uz', 'tg', 'tk', 'az', 'hy', 'ka']);
const rt = (lang) => { const l = String(lang || '').slice(0, 2); return R[l] || (RU_LIKE.has(l) ? R.ru : R.en); };
const usd = (cents) => (cents / 100).toFixed(2);

// what the owner really received for a payment, in cents
function netCents(cfg, { method, stars = 0, usd: u = 0 }) {
  const m = cfg.money;
  if (method === 'stars') return Math.floor(stars * m.starUsd * 100);
  if (method === 'crypto') return Math.floor(u * (1 - m.cryptoFee) * 100);
  if (method === 'card') return Math.floor(u * (1 - m.cardFee) * 100);
  return 0; // paid from the referral balance: no new money, no new commission
}

function create({ cfg, store, send, cb = null, log = console }) {
  const ref = cfg.referral;

  // called by fulfil() after a payment was counted; `first` = the buyer's first payment
  async function onPayment({ uid, method, stars, usd: u, first, charge }, now = Date.now()) {
    const buyer = store.user(uid);
    if (!buyer || !buyer.ref) return [];
    const net = netCents(cfg, { method, stars, usd: u });
    const out = [];
    const l1 = store.user(buyer.ref);
    if (l1 && !l1.banned) {
      const c = Math.floor((net * ref.l1Pct) / 100);
      if (c > 0 && store.credit(l1.id, c, 'ref1', uid, charge, now)) { out.push([l1.id, c]); await send(l1.id, rt(l1.lang).earn(usd(c), 1)).catch(() => {}); }
      if (first && cfg.refDays > 0) { store.extend(l1.id, cfg.refDays, now); await send(l1.id, rt(l1.lang).days(cfg.refDays)).catch(() => {}); }
      const l2 = l1.ref ? store.user(l1.ref) : null;
      if (l2 && !l2.banned && l2.id !== uid) {
        const c2 = Math.floor((net * ref.l2Pct) / 100);
        if (c2 > 0 && store.credit(l2.id, c2, 'ref2', uid, charge, now)) { out.push([l2.id, c2]); await send(l2.id, rt(l2.lang).earn(usd(c2), 2)).catch(() => {}); }
      }
    }
    return out;
  }

  function info(u) {
    const s = store.refStats(u.id);
    return {
      link: cfg.botUsername ? `https://t.me/${cfg.botUsername}?start=r${u.id}` : '',
      p1: ref.l1Pct, p2: ref.l2Pct, days: cfg.refDays, min: ref.minOut,
      l1: s.l1, l2: s.l2, earned: usd(s.earned), bal: usd(u.bal || 0), balCents: u.bal || 0,
    };
  }
  const text = (u) => rt(u.lang).ref(info(u));

  // user asks to withdraw the whole balance: money is held, admins get approve / decline buttons
  async function requestWithdraw(u, now = Date.now()) {
    const cents = store.user(u.id).bal || 0;
    if (cents < ref.minOut * 100) return { error: 'min' };
    const id = store.withdrawRequest(u.id, cents, now);
    if (!id) return { error: 'balance' };
    await send(u.id, rt(u.lang).wdReq(usd(cents))).catch(() => {});
    const who = u.name || u.id;
    for (const a of cfg.adminIds) {
      await send(a, `💸 Вывод #${id}: <b>$${usd(cents)}</b> USDT для ${who} (id ${u.id}).\nЗаработал всего $${info(store.user(u.id)).earned}. Деньги уйдут с баланса вашего приложения в @CryptoBot.`,
        [[{ text: '✅ Выплатить', callback_data: `wd_ok:${id}` }, { text: '❌ Отклонить', callback_data: `wd_no:${id}` }]]).catch(() => {});
    }
    return { ok: true, id, amount: usd(cents) };
  }

  // admin pressed a button
  async function decide(adminChat, id, approve) {
    const w = store.withdrawal(id);
    if (!w || w.status !== 'pending') return send(adminChat, `Вывод #${id} уже обработан.`);
    const u = store.user(w.uid);
    if (!approve) {
      store.withdrawDecline(id);
      await send(w.uid, rt(u.lang).wdNo(usd(w.cents))).catch(() => {});
      return send(adminChat, `❌ Вывод #${id} отклонён, $${usd(w.cents)} вернулись пользователю на баланс.`);
    }
    if (!cb) return send(adminChat, '⚠️ CryptoBot не подключён (CRYPTOBOT_TOKEN).');
    try {
      await cb.call('transfer', { user_id: w.uid, asset: 'USDT', amount: usd(w.cents), spend_id: `yurk-wd-${id}`, comment: 'YURK partner payout' });
      store.withdrawPaid(id);
      await send(w.uid, rt(u.lang).wdPaid(usd(w.cents))).catch(() => {});
      return send(adminChat, `✅ Вывод #${id}: $${usd(w.cents)} USDT отправлены.`);
    } catch (e) {
      log.error('withdraw', id, e.message);
      return send(adminChat, `⚠️ CryptoBot не отправил вывод #${id}: ${e.message}\n\nЧастые причины: в @CryptoBot → Crypto Pay → приложение → Security не включены Transfers; на балансе приложения мало USDT; пользователь ни разу не запускал @CryptoBot. Заявка осталась в ожидании — нажмите кнопку ещё раз, когда исправите.`);
    }
  }

  return { onPayment, info, text, requestWithdraw, decide, netCents: (p) => netCents(cfg, p) };
}

module.exports = { create, netCents };
