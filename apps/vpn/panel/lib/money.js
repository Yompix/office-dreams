'use strict';
/* Money report for the owner: income after fees, Railway costs (traffic is measured on the node),
   how much to keep for the Railway bill and how much is free to take. */

const GB = 1024 ** 3;
const r2 = (v) => Math.round(v * 100) / 100;
const $ = (v) => (v < 0 ? '−$' : '$') + Math.abs(r2(v)).toFixed(2);

function monthBounds(now) {
  const d = new Date(now);
  const start = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1);
  const end = Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1);
  return { start, end, day: new Date(start).toISOString().slice(0, 10) };
}

function report(store, cfg, now = Date.now()) {
  const m = cfg.money;
  const { start, end, day } = monthBounds(now);
  const part = Math.min(1, Math.max((now - start) / (end - start), 1 / 30)); // share of the month passed

  const inc = { stars: 0, crypto: 0, card: 0 };
  let sales = 0, starsN = 0;
  for (const p of store.paymentsSince(start)) {
    sales += p.n;
    if (p.method === 'stars') { inc.stars += (p.stars || 0) * m.starUsd; starsN += p.stars || 0; }
    else if (p.method === 'crypto') inc.crypto += (p.usd || 0) * (1 - m.cryptoFee);
    else if (p.method === 'card') inc.card += (p.usd || 0) * (1 - m.cardFee);
  }
  const income = inc.stars + inc.crypto + inc.card;
  const partners = (store.commissionsSince ? store.commissionsSince(start) : 0) / 100; // owed to partners this month

  const t = store.trafficSince(day);
  const gb = t.b / GB;
  const egressNow = gb * m.egressUsdPerGb;
  const egressMonth = egressNow / part;                 // if traffic keeps the same pace
  const fixedMonth = m.fixedUsd + m.extraUsd;
  const costNow = egressNow + fixedMonth * part;
  const costMonth = egressMonth + fixedMonth;           // the expected Railway (+VPS) bill for the month
  const reserve = Math.min(income, costMonth);          // keep this for the bill
  const free = income - costMonth - partners;           // what is left for the owner

  return {
    month: day.slice(0, 7), sales, starsN, income: r2(income), inc: { stars: r2(inc.stars), crypto: r2(inc.crypto), card: r2(inc.card) },
    partners: r2(partners), gb: r2(gb), trafficUsers: t.users, gbPerUser: t.users ? r2(gb / t.users) : 0,
    egressNow: r2(egressNow), egressMonth: r2(egressMonth), fixedMonth: r2(fixedMonth),
    costNow: r2(costNow), costMonth: r2(costMonth), reserve: r2(reserve), free: r2(free), part: r2(part),
  };
}

function text(r) {
  const L = [
    `💼 <b>Финансы за ${r.month}</b> (прошло ${Math.round(r.part * 100)}% месяца)`,
    '',
    `💰 <b>Доход: ${$(r.income)}</b> — продаж: ${r.sales}`,
    `   ⭐ звёзды: ${$(r.inc.stars)} (${r.starsN} ⭐ после доли Telegram)`,
    `   💎 крипта: ${$(r.inc.crypto)} (после комиссии CryptoBot)`,
  ];
  if (r.inc.card) L.push(`   💳 карты: ${$(r.inc.card)}`);
  L.push(
    '',
    `🧾 <b>Расход Railway за месяц ≈ ${$(r.costMonth)}</b>`,
    `   трафик VPN: ${r.gb} ГБ сейчас → ${$(r.egressNow)}, к концу месяца ≈ ${$(r.egressMonth)}`,
    `   сервер (план + CPU/RAM): ${$(r.fixedMonth)}`,
    r.trafficUsers ? `   в среднем ${r.gbPerUser} ГБ на пользователя (${r.trafficUsers} чел.)` : '   трафика пока нет',
    '',
    `🤝 Партнёрам начислено: ${$(r.partners)} (их доля с оплат друзей)`,
    '',
    `🏦 <b>Отложить на Railway: ${$(r.reserve)}</b>`,
    r.free >= 0 ? `✅ <b>Можно забрать себе: ${$(r.free)}</b>` : `⚠️ <b>Пока минус ${$(-r.free)}</b>: доход не покрывает Railway. Нужно больше продаж или VPS вместо трафика Railway.`,
    '',
    '<i>Звёзды выводятся через Fragment через 21 день после оплаты; крипта лежит в @CryptoBot → Кошелёк.</i>',
  );
  return L.join('\n');
}

module.exports = { report, text, monthBounds };
