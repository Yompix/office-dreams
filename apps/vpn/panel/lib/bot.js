'use strict';
/* Telegram bot: menu, free trial, Stars payments, referrals, admin commands. */
const { pick } = require('./texts.js');
const { DAY } = require('./store.js');

const fmtDate = (ms) => new Date(ms).toISOString().slice(0, 10).split('-').reverse().join('.');

function create({ cfg, store, tg, log = console }) {
  const subUrl = (u) => `${cfg.publicUrl}/sub/${u.token}`;
  const isAdmin = (id) => cfg.adminIds.includes(id);
  const planById = (id) => cfg.plans.find((p) => p.id === id);
  const send = (chat, text, kb) => tg.call('sendMessage', { chat_id: chat, text, parse_mode: 'HTML', disable_web_page_preview: true, ...(kb ? { reply_markup: { inline_keyboard: kb } } : {}) });

  function menu(u, now = Date.now()) {
    const t = pick(u.lang);
    const names = store.enabledNodes().map((n) => n.name).join(', ');
    const status = u.expires > now ? t.active(fmtDate(u.expires)) : t.none;
    const kb = [];
    if (!u.trial && u.expires <= now && cfg.trialDays > 0) kb.push([{ text: t.bTrial(cfg.trialDays), callback_data: 'trial' }]);
    kb.push([{ text: t.bKey, callback_data: 'key' }, { text: t.bBuy, callback_data: 'buy' }]);
    kb.push([{ text: t.bHelp, callback_data: 'help' }, { text: t.bRef, callback_data: 'ref' }]);
    if (cfg.support) kb.push([{ text: t.bSupport, url: cfg.support.startsWith('http') ? cfg.support : `https://t.me/${cfg.support.replace(/^@/, '')}` }]);
    return { text: `${t.hello(cfg.brand, names)}\n\n${status}`, kb };
  }
  const back = (t) => [[{ text: t.bBack, callback_data: 'menu' }]];

  async function showKey(chat, u, now) {
    const t = pick(u.lang);
    if (u.expires <= now) return send(chat, t.noSub, [[{ text: t.bBuy, callback_data: 'buy' }], ...back(t)]);
    return send(chat, t.key(subUrl(u), fmtDate(u.expires)), [
      [{ text: t.bConnect, url: subUrl(u) }],
      [{ text: t.bReset, callback_data: 'reset' }, { text: t.bHelp, callback_data: 'help' }],
      ...back(t),
    ]);
  }

  async function onAdmin(chat, text) {
    const [cmd, ...a] = text.trim().split(/\s+/);
    if (cmd === '/stats') {
      const s = store.stats();
      return send(chat, `📊 Пользователей: ${s.users} (+${s.new24h} за сутки)\nАктивных: ${s.active}\nПлатили: ${s.paying}\nОплат: ${s.payments}, звёзд всего: ${s.stars}, за 30 дн.: ${s.stars30d}\nУзлов: ${s.nodes}`);
    }
    if (cmd === '/nodes') {
      const L = store.nodes().map((n) => `#${n.id} ${n.enabled ? '🟢' : '⚪'} <b>${n.name}</b> ${n.host} · ${n.sni} · ${n.seen ? 'связь ' + Math.round((Date.now() - n.seen) / 60000) + ' мин назад' : 'ещё не подключался'}${n.pin ? '' : ' · без Hysteria2'}`);
      return send(chat, L.join('\n') || 'Узлов нет. /addnode Имя IP [сайт-маска]');
    }
    if (cmd === '/addnode') {
      const [name, host, sni] = a;
      if (!name || !host) return send(chat, 'Формат: /addnode 🇳🇱Нидерланды 1.2.3.4 [www.microsoft.com]');
      const n = store.addNode(name, host, sni || undefined);
      return send(chat, `✅ Узел #${n.id} «${n.name}» добавлен. Зайдите на сервер (Ubuntu/Debian, root) и выполните:\n\n<code>curl -fsSL ${cfg.publicUrl}/node/install.sh -o i.sh &amp;&amp; bash i.sh ${cfg.publicUrl} ${n.secret}</code>\n\nЧерез минуту проверьте /nodes.`);
    }
    if (cmd === '/node_off' || cmd === '/node_on') {
      const ok = store.setNodeEnabled(Number(a[0]), cmd === '/node_on');
      return send(chat, ok ? 'Готово.' : 'Нет такого узла.');
    }
    if (cmd === '/give') {
      const id = Number(a[0]), days = Number(a[1]);
      if (!store.user(id) || !(days > 0)) return send(chat, 'Формат: /give <telegram id> <дней>');
      return send(chat, `Готово, до ${fmtDate(store.extend(id, days))}.`);
    }
    return send(chat, 'Админ: /stats, /nodes, /addnode, /node_off N, /node_on N, /give id дней');
  }

  async function onMessage(m, now) {
    if (!m.from || m.from.is_bot) return;
    const chat = m.chat.id;
    if (m.successful_payment) return onPaid(m, now);
    const text = m.text || '';
    let ref = 0;
    const sp = /^\/start\s+r(\d+)/.exec(text);
    if (sp) ref = Number(sp[1]);
    const { user } = store.ensureUser(m.from, ref, now);
    const t = pick(user.lang);
    if (isAdmin(m.from.id) && /^\/(stats|nodes|addnode|node_off|node_on|give|admin)\b/.test(text)) return onAdmin(chat, text);
    if (text.startsWith('/key')) return showKey(chat, user, now);
    if (text.startsWith('/buy')) return send(chat, t.plans, plansKb(user));
    if (text.startsWith('/help')) return send(chat, t.help, back(t));
    const mn = menu(user, now);
    return send(chat, mn.text, mn.kb);
  }

  const plansKb = (u) => { const t = pick(u.lang); return [...cfg.plans.map((p) => [{ text: t.plan(p), callback_data: 'plan:' + p.id }]), ...back(t)]; };

  async function onCallback(q, now) {
    const { user: u } = store.ensureUser(q.from, 0, now);
    const t = pick(u.lang);
    const chat = q.message ? q.message.chat.id : q.from.id;
    await tg.call('answerCallbackQuery', { callback_query_id: q.id }).catch(() => {});
    const d = q.data || '';
    if (d === 'menu') { const mn = menu(u, now); return send(chat, mn.text, mn.kb); }
    if (d === 'trial') {
      const exp = cfg.trialDays > 0 ? store.startTrial(u.id, cfg.trialDays, now) : 0;
      if (!exp) return send(chat, t.trialUsed, plansKb(u));
      await send(chat, t.trialOk(fmtDate(exp)));
      return showKey(chat, store.user(u.id), now);
    }
    if (d === 'key') return showKey(chat, u, now);
    if (d === 'reset') { const nu = store.resetKey(u.id); await send(chat, t.reset); return showKey(chat, nu, now); }
    if (d === 'buy') return send(chat, t.plans, plansKb(u));
    if (d === 'help') return send(chat, t.help, back(t));
    if (d === 'ref') return send(chat, t.ref(`https://t.me/${cfg.botUsername}?start=r${u.id}`, cfg.refDays), back(t));
    if (d.startsWith('plan:')) {
      const p = planById(d.slice(5));
      if (!p) return;
      return tg.call('sendInvoice', {
        chat_id: chat, title: t.invTitle(cfg.brand, p), description: t.invDesc(p),
        payload: `${p.id}:${u.id}`, currency: 'XTR', prices: [{ label: t.invTitle(cfg.brand, p), amount: p.stars }],
      });
    }
  }

  // payload is "<plan>:<user id>"; price and currency must match the plan as it is now
  function checkPayload(payload, currency, amount) {
    const [pid, uid] = String(payload || '').split(':');
    const p = planById(pid);
    if (!p || currency !== 'XTR' || amount !== p.stars || !store.user(Number(uid))) return null;
    return { plan: p, uid: Number(uid) };
  }

  async function onPreCheckout(q) {
    const ok = !!checkPayload(q.invoice_payload, q.currency, q.total_amount);
    return tg.call('answerPreCheckoutQuery', { pre_checkout_query_id: q.id, ok, ...(ok ? {} : { error_message: 'Plan changed, please try again' }) });
  }

  async function onPaid(m, now) {
    const sp = m.successful_payment;
    const c = checkPayload(sp.invoice_payload, sp.currency, sp.total_amount);
    if (!c) { log.error('payment with bad payload', sp.invoice_payload, sp.telegram_payment_charge_id); return; }
    const before = store.user(c.uid);
    if (!store.recordPayment(sp.telegram_payment_charge_id, c.uid, c.plan.id, sp.total_amount, c.plan.days, now)) return; // already counted
    const exp = store.extend(c.uid, c.plan.days, now);
    await send(m.chat.id, pick(before.lang).paid(fmtDate(exp)));
    // first payment of an invited user rewards the inviter
    if (!before.paid && before.ref && cfg.refDays > 0) {
      const r = store.user(before.ref);
      if (r) { store.extend(r.id, cfg.refDays, now); await send(r.id, pick(r.lang).refBonus(cfg.refDays)).catch(() => {}); }
    }
    for (const a of cfg.adminIds) send(a, `💰 ${before.name || before.id} купил ${c.plan.id} за ${sp.total_amount} ⭐`).catch(() => {});
  }

  async function handle(upd, now = Date.now()) {
    try {
      if (upd.message) return await onMessage(upd.message, now);
      if (upd.callback_query) return await onCallback(upd.callback_query, now);
      if (upd.pre_checkout_query) return await onPreCheckout(upd.pre_checkout_query);
    } catch (e) { log.error('bot:', e.message); }
  }

  // reminder a day before the end, once per expiry date
  async function remind(now = Date.now()) {
    for (const u of store.toWarn(now, DAY)) {
      store.markWarned(u.id, u.expires);
      const t = pick(u.lang);
      await send(u.id, t.warn(fmtDate(u.expires)), [[{ text: t.bBuy, callback_data: 'buy' }]]).catch(() => {});
    }
  }

  return { handle, remind, menu };
}

module.exports = { create, fmtDate };
