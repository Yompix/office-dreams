'use strict';
// LANTERN: задания, серия, достижения, магазин косметики, сезон, рефералы, лайки, топ миров.
// Всё считает сервер по реальным событиям relay (время в комнате, гости, чат) — клиент только показывает.
// Цифры и баланс — godot/playtogether/docs/ECONOMY.md. Никаких лутбоксов и случайных платных наград.

const DAY = 24 * 3600_000;

const QUESTS_DAILY = [
  { id: 'd_play30', title: 'Сыграй 30 минут с другом', ev: 'together_min', target: 30, sparks: 20, xp: 30 },
  { id: 'd_host2', title: 'Захости мир, где побывало 2+ игрока', ev: 'host_visitor', target: 2, sparks: 25, xp: 30 },
  { id: 'd_public', title: 'Зайди в открытый мир', ev: 'join_public', target: 1, sparks: 15, xp: 15 },
  { id: 'd_invite', title: 'Пригласи друга по коду', ev: 'invite', target: 1, sparks: 10, xp: 10 },
  { id: 'd_chat', title: 'Напиши в чат комнаты', ev: 'room_chat', target: 1, sparks: 5, xp: 5 },
];
const QUESTS_WEEKLY = [
  { id: 'w_play300', title: 'Сыграйте с друзьями 5 часов', ev: 'together_min', target: 300, sparks: 150, xp: 200 },
  { id: 'w_host10', title: 'Прими в своём мире 10 гостей', ev: 'host_visitor', target: 10, sparks: 120, xp: 150 },
  { id: 'w_public5', title: 'Побывай в 5 открытых мирах', ev: 'join_public', target: 5, sparks: 80, xp: 100 },
];

// [id, название, титул, условие: [стат, порог], искры]
const ACHIEVEMENTS = [
  ['first_host', 'Первый костёр', 'Разжигатель', ['hosts', 1], 20],
  ['first_join', 'В гостях', 'Гость', ['joins', 1], 10],
  ['java_host', 'Кирка и блоки', 'Строитель', ['hosts_java', 1], 10],
  ['bedrock_host', 'Карманный мир', 'Мобильный', ['hosts_bedrock', 1], 10],
  ['cross_host', 'Мост между мирами', 'Мостостроитель', ['hosts_both', 1], 30],
  ['friend_1', 'Не один', 'Друг', ['friends', 1], 10],
  ['friend_5', 'Компания', 'Душа компании', ['friends', 5], 40],
  ['friend_20', 'Свой клан', 'Вожак', ['friends', 20], 100],
  ['hours_1', 'Первый час вместе', 'Искорка', ['together_min', 60], 20],
  ['hours_10', 'Десять часов', 'Огонёк', ['together_min', 600], 60],
  ['hours_50', 'Пятьдесят часов', 'Пламя', ['together_min', 3000], 150],
  ['hours_100', 'Сто часов', 'Хранитель огня', ['together_min', 6000], 300],
  ['visitors_10', 'Гостеприимный', 'Хозяин', ['visitors', 10], 50],
  ['visitors_100', 'Шумный двор', 'Мэр', ['visitors', 100], 200],
  ['public_10', 'Путешественник', 'Странник', ['join_public', 10], 50],
  ['chat_100', 'Болтун', 'Болтун', ['room_chat', 100], 30],
  ['streak_7', 'Неделя подряд', 'Постоянный', ['best_streak', 7], 50],
  ['streak_30', 'Месяц подряд', 'Неугасимый', ['best_streak', 30], 200],
  ['quests_10', 'Делу время', 'Исполнитель', ['quests', 10], 40],
  ['quests_100', 'Мастер заданий', 'Мастер', ['quests', 100], 250],
  ['lantern_5', 'Яркий фонарь', 'Светоч', ['lantern', 5], 100],
  ['lantern_9', 'Звезда', 'Звезда', ['lantern', 9], 500],
  ['likes_10', 'Любимый мир', 'Любимец', ['likes', 10], 60],
  ['referral_1', 'Привёл друга', 'Проводник', ['referrals', 1], 50],
];

// Косметика: [id, вид, название, цена искр, сезонная/премиум]
const SHOP = [
  ['flame_amber', 'flame', 'Янтарное пламя', 0], ['flame_orange', 'flame', 'Оранжевое пламя', 150],
  ['flame_rose', 'flame', 'Розовое пламя', 300], ['flame_violet', 'flame', 'Фиолетовое пламя', 450],
  ['flame_ice', 'flame', 'Ледяное пламя', 600], ['flame_emerald', 'flame', 'Изумрудное пламя', 800],
  ['flame_rainbow', 'flame', 'Радужное пламя', 1500],
  ['shape_cube', 'shape', 'Кубический фонарь', 0], ['shape_round', 'shape', 'Круглый фонарь', 400],
  ['shape_tall', 'shape', 'Башенка', 600], ['shape_star', 'shape', 'Звёздный фонарь', 1000],
  ['nick_white', 'nick', 'Белый ник', 0], ['nick_gold', 'nick', 'Золотой ник', 250], ['nick_sky', 'nick', 'Небесный ник', 250],
  ['nick_mint', 'nick', 'Мятный ник', 250], ['nick_flame', 'nick', 'Огненный ник', 700],
  ['frame_none', 'frame', 'Без рамки', 0], ['frame_wood', 'frame', 'Деревянная рамка', 200], ['frame_stone', 'frame', 'Каменная рамка', 300],
  ['frame_gold', 'frame', 'Золотая рамка', 900], ['frame_night', 'frame', 'Ночное небо', 700],
  ['sticker_wave', 'sticker', 'Стикеры «Привет»', 100], ['sticker_creeper', 'sticker', 'Стикеры «Бум»', 150], ['sticker_heart', 'sticker', 'Стикеры «Сердечки»', 150],
  ['camp_logs', 'campfire', 'Простой костёр', 0], ['camp_tent', 'campfire', 'Палатка у костра', 350], ['camp_stars', 'campfire', 'Звёздное небо', 500],
  ['camp_snow', 'campfire', 'Зимняя поляна', 500], ['camp_fireflies', 'campfire', 'Светлячки', 800],
].map(([id, kind, name, price]) => ({ id, kind, name, price }));
const DEFAULT_EQUIP = { flame: 'flame_amber', shape: 'shape_cube', nick: 'nick_white', frame: 'frame_none', campfire: 'camp_logs' };

// Сезон: 8 недель, 40 ступеней по 100 очков (очки = XP из заданий и игры вместе)
const SEASON = {
  id: 's1', title: 'Сезон 1 · Первые огни', start: Date.UTC(2026, 10, 2), weeks: 8, tierPoints: 100, tiers: 40,
  free: { 2: ['sparks', 30], 4: ['item', 'sticker_wave'], 6: ['sparks', 50], 8: ['item', 'frame_wood'], 10: ['sparks', 60], 14: ['sparks', 80],
    16: ['item', 'nick_sky'], 20: ['sparks', 100], 24: ['item', 'flame_orange'], 28: ['sparks', 120], 32: ['item', 'camp_tent'], 36: ['sparks', 150], 40: ['item', 'flame_s1'] },
  premium: { 1: 'flame_s1p', 5: 'shape_s1', 10: 'frame_s1', 15: 'camp_s1', 20: 'nick_s1', 25: 'sticker_s1', 30: 'shape_s1b', 35: 'camp_s1b', 40: 'flame_s1legend' },
};
const SEASON_ITEMS = [['flame_s1', 'flame', 'Пламя первых огней'], ['flame_s1p', 'flame', 'Пламя сезона (премиум)'], ['shape_s1', 'shape', 'Фонарь-ракушка'],
  ['frame_s1', 'frame', 'Рамка сезона'], ['camp_s1', 'campfire', 'Осенний костёр'], ['nick_s1', 'nick', 'Ник «Заря»'], ['sticker_s1', 'sticker', 'Стикеры сезона'],
  ['shape_s1b', 'shape', 'Фонарь-маяк'], ['camp_s1b', 'campfire', 'Северное сияние'], ['flame_s1legend', 'flame', 'Легендарное пламя']];
for (const [id, kind, name] of SEASON_ITEMS) SHOP.push({ id, kind, name, price: null, season: SEASON.id });

const STREAK_REWARD = (d) => Math.min(5 + 2 * d, 40);
const REFERRAL_MIN = 60;
const REFERRAL_SPARKS = 100;
const PRODUCTS = ['lantern_pro_month', 'lantern_pass_s1', 'sparks_500', 'sparks_1200'];

const dayKey = (t = Date.now()) => new Date(t).toISOString().slice(0, 10);
function weekKey(t = Date.now()) {
  const d = new Date(t); const day = (d.getUTCDay() + 6) % 7; d.setUTCDate(d.getUTCDate() - day);
  return 'w' + d.toISOString().slice(0, 10);
}
function hashInt(s) { let h = 2166136261; for (const c of s) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; }

class Economy {
  constructor(social) {
    this.social = social;
    this.db = social.store.db;
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS stats(uid INTEGER, key TEXT, value INTEGER, PRIMARY KEY(uid,key));
      CREATE TABLE IF NOT EXISTS quests(uid INTEGER, period TEXT, qid TEXT, progress INTEGER, claimed INTEGER DEFAULT 0, PRIMARY KEY(uid,period,qid));
      CREATE TABLE IF NOT EXISTS ach(uid INTEGER, id TEXT, ts INTEGER, PRIMARY KEY(uid,id));
      CREATE TABLE IF NOT EXISTS owned(uid INTEGER, item TEXT, ts INTEGER, PRIMARY KEY(uid,item));
      CREATE TABLE IF NOT EXISTS season(uid INTEGER, sid TEXT, points INTEGER DEFAULT 0, claimed TEXT DEFAULT '', premium INTEGER DEFAULT 0, PRIMARY KEY(uid,sid));
      CREATE TABLE IF NOT EXISTS visits(week TEXT, host INTEGER, guest INTEGER, PRIMARY KEY(week,host,guest));
      CREATE TABLE IF NOT EXISTS likes(day TEXT, host INTEGER, src INTEGER, PRIMARY KEY(day,host,src));
      CREATE TABLE IF NOT EXISTS purchases(token TEXT PRIMARY KEY, uid INTEGER, product TEXT, ts INTEGER, state TEXT);
      CREATE TABLE IF NOT EXISTS demo(uid INTEGER, product TEXT, amount INTEGER, ts INTEGER);
    `);
    for (const col of ['sparks INTEGER DEFAULT 0', 'xp INTEGER DEFAULT 0', 'streak INTEGER DEFAULT 0', 'best_streak INTEGER DEFAULT 0',
      "last_day TEXT DEFAULT ''", "grace_week TEXT DEFAULT ''", "streak_claimed TEXT DEFAULT ''", "equip TEXT DEFAULT ''", "title TEXT DEFAULT ''",
      'referrer INTEGER DEFAULT 0', 'referral_paid INTEGER DEFAULT 0', 'pro_until INTEGER DEFAULT 0']) {
      try { this.db.exec('ALTER TABLE users ADD COLUMN ' + col); } catch { /* уже есть */ }
    }
  }
  q(sql) { return this.db.prepare(sql); }

  stat(uid, key) { const r = this.q('SELECT value FROM stats WHERE uid=? AND key=?').get(uid, key); return r ? r.value : 0; }
  addStat(uid, key, n = 1) { this.q('INSERT INTO stats(uid,key,value) VALUES(?,?,?) ON CONFLICT(uid,key) DO UPDATE SET value=value+?').run(uid, key, n, n); }
  setMax(uid, key, v) { if (v > this.stat(uid, key)) this.q('INSERT INTO stats(uid,key,value) VALUES(?,?,?) ON CONFLICT(uid,key) DO UPDATE SET value=?').run(uid, key, v, v); }

  dailyQuests(uid, day = dayKey()) {
    const pool = [...QUESTS_DAILY];
    const out = [];
    let h = hashInt(uid + ':' + day);
    while (out.length < 3) { const i = h % pool.length; out.push(pool.splice(i, 1)[0]); h = hashInt(String(h)); }
    return out;
  }

  // ---- события от relay/social
  event(uid, ev, n = 1, extra = {}) {
    if (!uid) return;
    const map = { together_min: 'together_min', host_visitor: 'visitors', join_public: 'join_public', room_chat: 'room_chat', invite: 'invites' };
    if (map[ev]) this.addStat(uid, map[ev], n);
    if (ev === 'together_min') {
      this.q('UPDATE users SET xp=xp+?, minutes=minutes+? WHERE id=?').run(n, n, uid);
      this.seasonPoints(uid, n);
      this.checkReferral(uid);
    }
    const day = dayKey(); const week = weekKey();
    for (const q of this.dailyQuests(uid, day)) if (q.ev === ev) this.progress(uid, 'd' + day, q, n);
    for (const q of QUESTS_WEEKLY) if (q.ev === ev) this.progress(uid, week, q, n);
    this.checkAchievements(uid);
    this.touch(uid);
  }

  progress(uid, period, q, n) {
    this.q('INSERT INTO quests(uid,period,qid,progress) VALUES(?,?,?,?) ON CONFLICT(uid,period,qid) DO UPDATE SET progress=MIN(progress+?, ?)').run(uid, period, q.id, Math.min(n, q.target), n, q.target);
  }

  questsView(uid) {
    const day = 'd' + dayKey(); const week = weekKey();
    const row = (period, q) => {
      const r = this.q('SELECT progress, claimed FROM quests WHERE uid=? AND period=? AND qid=?').get(uid, period, q.id) || { progress: 0, claimed: 0 };
      return { id: q.id, title: q.title, target: q.target, progress: r.progress, claimed: !!r.claimed, sparks: q.sparks, xp: q.xp, period };
    };
    const nextDay = new Date(); nextDay.setUTCHours(24, 0, 0, 0);
    return { daily: this.dailyQuests(uid).map((q) => row(day, q)), weekly: QUESTS_WEEKLY.map((q) => row(week, q)), resets_in: nextDay - Date.now() };
  }

  claimQuest(uid, qid, period) {
    const q = [...QUESTS_DAILY, ...QUESTS_WEEKLY].find((x) => x.id === qid);
    if (!q) return 'Нет такого задания';
    const r = this.q('SELECT progress, claimed FROM quests WHERE uid=? AND period=? AND qid=?').get(uid, period, qid);
    if (!r || r.progress < q.target) return 'Задание ещё не выполнено';
    if (r.claimed) return 'Награда уже получена';
    if (period !== 'd' + dayKey() && period !== weekKey()) return 'Задание устарело';
    this.q('UPDATE quests SET claimed=1 WHERE uid=? AND period=? AND qid=?').run(uid, period, qid);
    this.give(uid, q.sparks, q.xp);
    this.addStat(uid, 'quests', 1);
    this.checkAchievements(uid);
    return '';
  }

  give(uid, sparks, xp = 0) {
    this.q('UPDATE users SET sparks=sparks+?, xp=xp+? WHERE id=?').run(sparks, xp, uid);
    if (xp) this.seasonPoints(uid, xp);
  }

  // ---- серия: вход каждый день; пропуск одного дня раз в неделю — серия делится пополам, а не сгорает
  touch(uid) {
    const u = this.social.user(uid);
    const today = dayKey();
    if (!u || u.last_day === today) return;
    let streak = 1; let grace = u.grace_week;
    if (u.last_day) {
      const gap = Math.round((Date.parse(today) - Date.parse(u.last_day)) / DAY);
      if (gap === 1) streak = u.streak + 1;
      else if (gap === 2 && u.grace_week !== weekKey()) { streak = Math.max(1, Math.floor(u.streak / 2)) + 1; grace = weekKey(); }
    }
    this.q('UPDATE users SET streak=?, last_day=?, grace_week=?, best_streak=MAX(best_streak, ?) WHERE id=?').run(streak, today, grace, streak, uid);
    this.setMax(uid, 'best_streak', streak);
  }

  claimStreak(uid) {
    const u = this.social.user(uid);
    if (u.streak_claimed === dayKey()) return 'Сегодня награда уже получена';
    if (u.last_day !== dayKey()) return 'Зайдите в игру сегодня';
    this.q('UPDATE users SET streak_claimed=? WHERE id=?').run(dayKey(), uid);
    this.give(uid, STREAK_REWARD(u.streak), 5);
    return '';
  }

  // ---- достижения
  checkAchievements(uid) {
    const u = this.social.user(uid);
    if (!u) return;
    const vals = { friends: this.social.friendsOf(uid).length, lantern: require('./social').lanternLevel(u.xp).level, best_streak: u.best_streak };
    const have = new Set(this.q('SELECT id FROM ach WHERE uid=?').all(uid).map((r) => r.id));
    for (const [id, name, title, [key, need], sparks] of ACHIEVEMENTS) {
      if (have.has(id)) continue;
      const v = key in vals ? vals[key] : this.stat(uid, key);
      if (v >= need) {
        this.q('INSERT OR IGNORE INTO ach(uid,id,ts) VALUES(?,?,?)').run(uid, id, Date.now());
        this.give(uid, sparks);
        if (!u.title) this.q('UPDATE users SET title=? WHERE id=?').run(title, uid);
        this.social.push(uid, { t: 'notice', kind: 'achievement', id, name, title, sparks });
      }
    }
  }

  achievementsView(uid) {
    const have = new Map(this.q('SELECT id, ts FROM ach WHERE uid=?').all(uid).map((r) => [r.id, r.ts]));
    const u = this.social.user(uid);
    const vals = { friends: this.social.friendsOf(uid).length, lantern: require('./social').lanternLevel(u.xp).level, best_streak: u.best_streak };
    return ACHIEVEMENTS.map(([id, name, title, [key, need], sparks]) => ({ id, name, title, need, sparks,
      progress: Math.min(need, key in vals ? vals[key] : this.stat(uid, key)), done: have.has(id), ts: have.get(id) || 0 }));
  }

  setTitle(uid, title) {
    if (title === '') { this.q("UPDATE users SET title='' WHERE id=?").run(uid); return ''; }
    const a = ACHIEVEMENTS.find((x) => x[2] === title);
    if (!a || !this.q('SELECT 1 FROM ach WHERE uid=? AND id=?').get(uid, a[0])) return 'Этот титул ещё не открыт';
    this.q('UPDATE users SET title=? WHERE id=?').run(title, uid);
    return '';
  }

  // ---- магазин
  owns(uid, item) { return SHOP.find((s) => s.id === item && s.price === 0) || !!this.q('SELECT 1 FROM owned WHERE uid=? AND item=?').get(uid, item); }
  equip(uid) { const u = this.social.user(uid); let e = {}; try { e = JSON.parse(u.equip || '{}'); } catch {} return { ...DEFAULT_EQUIP, ...e }; }

  buy(uid, item) {
    const it = SHOP.find((s) => s.id === item);
    if (!it) return 'Нет такого товара';
    if (it.price === null) return 'Этот предмет — награда сезона';
    if (this.owns(uid, item)) return 'Уже куплено';
    const u = this.social.user(uid);
    if (u.sparks < it.price) return 'Не хватает искр';
    this.q('UPDATE users SET sparks=sparks-? WHERE id=?').run(it.price, uid);
    this.q('INSERT INTO owned(uid,item,ts) VALUES(?,?,?)').run(uid, item, Date.now());
    return '';
  }

  wear(uid, item) {
    const it = SHOP.find((s) => s.id === item);
    if (!it || it.kind === 'sticker') return 'Нельзя надеть';
    if (!this.owns(uid, item)) return 'Сначала получите этот предмет';
    const e = this.equip(uid); e[it.kind] = item;
    this.q('UPDATE users SET equip=? WHERE id=?').run(JSON.stringify(e), uid);
    return '';
  }

  shopView(uid) {
    const owned = new Set(this.q('SELECT item FROM owned WHERE uid=?').all(uid).map((r) => r.item));
    return { items: SHOP.map((s) => ({ ...s, owned: s.price === 0 || owned.has(s.id) })), equip: this.equip(uid) };
  }

  // ---- сезон
  seasonNow() {
    const end = SEASON.start + SEASON.weeks * 7 * DAY;
    return { ...SEASON, end, active: Date.now() >= SEASON.start && Date.now() < end };
  }
  seasonPoints(uid, n) {
    const s = this.seasonNow(); if (!s.active) return;
    this.q('INSERT INTO season(uid,sid,points) VALUES(?,?,?) ON CONFLICT(uid,sid) DO UPDATE SET points=points+?').run(uid, s.id, n, n);
  }
  seasonView(uid) {
    const s = this.seasonNow();
    const r = this.q('SELECT points, claimed, premium FROM season WHERE uid=? AND sid=?').get(uid, s.id) || { points: 0, claimed: '', premium: 0 };
    const tier = Math.min(s.tiers, Math.floor(r.points / s.tierPoints));
    return { id: s.id, title: s.title, start: s.start, end: s.end, active: s.active, tiers: s.tiers, tierPoints: s.tierPoints, points: r.points, tier,
      premium: !!r.premium, premium_available: process.env.DEMO_PURCHASES !== '0', claimed: r.claimed ? r.claimed.split(',') : [], free: s.free, premiumTrack: s.premium,
      items: Object.fromEntries(SHOP.map((x) => [x.id, x.name])) };
  }
  claimSeason(uid, tier, track) {
    const v = this.seasonView(uid);
    if (tier > v.tier) return 'Ступень ещё не открыта';
    if (track === 'premium' && !v.premium) return 'Премиум-путь скоро';
    const key = (track === 'premium' ? 'p' : 'f') + tier;
    if (v.claimed.includes(key)) return 'Уже получено';
    const reward = track === 'premium' ? ['item', SEASON.premium[tier]] : SEASON.free[tier];
    if (!reward || !reward[1]) return 'На этой ступени нет награды';
    if (reward[0] === 'sparks') this.give(uid, reward[1]);
    else this.q('INSERT OR IGNORE INTO owned(uid,item,ts) VALUES(?,?,?)').run(uid, reward[1], Date.now());
    const claimed = [...v.claimed, key].join(',');
    this.q('INSERT INTO season(uid,sid,claimed) VALUES(?,?,?) ON CONFLICT(uid,sid) DO UPDATE SET claimed=?').run(uid, v.id, claimed, claimed);
    return '';
  }

  // ---- рефералы
  setReferrer(uid, tag) {
    const u = this.social.user(uid);
    if (u.referrer) return 'Пригласивший уже указан';
    if (Date.now() - u.created > 7 * DAY) return 'Код друга можно ввести в первую неделю';
    const r = this.social.findByTag(tag);
    if (!r || r.id === uid) return 'Игрок не найден';
    this.q('UPDATE users SET referrer=? WHERE id=?').run(r.id, uid);
    return '';
  }
  checkReferral(uid) {
    const u = this.social.user(uid);
    if (!u || !u.referrer || u.referral_paid || u.minutes < REFERRAL_MIN) return;
    this.q('UPDATE users SET referral_paid=1 WHERE id=?').run(uid);
    this.give(uid, REFERRAL_SPARKS); this.give(u.referrer, REFERRAL_SPARKS);
    this.addStat(u.referrer, 'referrals', 1);
    this.checkAchievements(u.referrer);
    this.social.push(u.referrer, { t: 'notice', kind: 'referral', from: this.social.pub(u), sparks: REFERRAL_SPARKS });
  }

  // ---- лайки и топ миров недели
  visit(hostId, guestId) {
    if (!hostId || !guestId || hostId === guestId) return;
    const r = this.q('INSERT OR IGNORE INTO visits(week,host,guest) VALUES(?,?,?)').run(weekKey(), hostId, guestId);
    if (r.changes) this.event(hostId, 'host_visitor', 1);
  }
  like(hostId, src) {
    if (!hostId || hostId === src) return 'Нельзя';
    const r = this.q('INSERT OR IGNORE INTO likes(day,host,src) VALUES(?,?,?)').run(dayKey(), hostId, src);
    if (!r.changes) return 'Сегодня вы уже ставили лайк';
    this.addStat(hostId, 'likes', 1);
    this.checkAchievements(hostId);
    return '';
  }
  top() {
    return this.q('SELECT host, COUNT(*) AS visits FROM visits WHERE week=? GROUP BY host ORDER BY visits DESC LIMIT 10').all(weekKey())
      .map((r) => ({ ...this.social.pub(this.social.user(r.host)), visits: r.visits, likes: this.stat(r.host, 'likes') }));
  }

  // ---- покупки (заготовка: проверка через Google Play Developer API — когда владелец добавит ключ сервисного аккаунта)
  verifyPurchase(uid, product, token) {
    if (!PRODUCTS.includes(product)) return { ok: false, error: 'unknown_product' };
    if (!/^[A-Za-z0-9._-]{20,4096}$/.test(String(token || ''))) return { ok: false, error: 'bad_token' };
    if (!process.env.GOOGLE_SERVICE_ACCOUNT_JSON) return { ok: false, error: 'billing_not_configured' };
    // TODO(владелец): androidpublisher.purchases.products/subscriptionsv2.get по ключу из GOOGLE_SERVICE_ACCOUNT_JSON,
    // затем начислить: sparks_500 → +500, sparks_1200 → +1200, lantern_pass_s1 → season.premium=1, lantern_pro_month → pro_until.
    return { ok: false, error: 'verification_pending' };
  }

  // ---- ДЕМО-режим: покупки без денег (помечены отдельно и стираются при выключении демо)
  demoPurchase(uid, product) {
    if (process.env.DEMO_PURCHASES === '0') return 'Демо-покупки выключены на сервере';
    if (!PRODUCTS.includes(product)) return 'Нет такого товара';
    const n = this.q('SELECT COUNT(*) AS n FROM demo WHERE uid=? AND ts>?').get(uid, Date.now() - DAY).n;
    if (n >= 20) return 'Хватит на сегодня :)';
    let amount = 0;
    if (product === 'sparks_500') amount = 500;
    if (product === 'sparks_1200') amount = 1200;
    if (amount) this.q('UPDATE users SET sparks=sparks+? WHERE id=?').run(amount, uid);
    if (product === 'lantern_pass_s1') this.q('INSERT INTO season(uid,sid,premium) VALUES(?,?,1) ON CONFLICT(uid,sid) DO UPDATE SET premium=1').run(uid, SEASON.id);
    if (product === 'lantern_pro_month') this.q('UPDATE users SET pro_until=? WHERE id=?').run(Date.now() + 30 * DAY, uid);
    this.q('INSERT INTO demo(uid,product,amount,ts) VALUES(?,?,?,?)').run(uid, product, amount, Date.now());
    return '';
  }
  demoWipe(uid) {
    const sum = this.q('SELECT COALESCE(SUM(amount),0) AS s FROM demo WHERE uid=?').get(uid).s;
    this.q('UPDATE users SET sparks=MAX(0, sparks-?), pro_until=0 WHERE id=?').run(sum, uid);
    this.q('UPDATE season SET premium=0 WHERE uid=?').run(uid);
    this.q('DELETE FROM demo WHERE uid=?').run(uid);
  }
  // Награда за просмотр рекламы (демо или AdMob): +20 искр, не больше 3 раз в день
  adReward(uid) {
    const key = 'ads_' + dayKey();
    if (this.stat(uid, key) >= 3) return 'На сегодня реклама закончилась';
    this.addStat(uid, key, 1);
    this.give(uid, 20);
    return '';
  }

  profile(uid) {
    const u = this.social.user(uid);
    const { lanternLevel } = require('./social');
    return { sparks: u.sparks, xp: u.xp, lantern: lanternLevel(u.xp), streak: u.streak, best_streak: u.best_streak,
      streak_reward: STREAK_REWARD(u.streak), streak_claimed: u.streak_claimed === dayKey(), title: u.title, equip: this.equip(uid),
      pro: u.pro_until > Date.now(), pro_until: u.pro_until, demo: process.env.DEMO_PURCHASES !== '0', ads_left: 3 - this.stat(uid, 'ads_' + dayKey()), referrer: !!u.referrer, store: { enabled: false, products: PRODUCTS },
      vanity: this.social.pro ? this.social.pro.vanityOf(uid) : '', cloud: !!(this.social.pro && this.social.pro.cloudEnabled()) };
  }

  handle(ws, uid, msg, send) {
    const r = (t, error, extra = {}) => send(ws, { t, error: error || '', ...extra });
    switch (msg.t) {
      case 'eco': return send(ws, { t: 'eco', profile: this.profile(uid), quests: this.questsView(uid) });
      case 'quest_claim': { const e = this.claimQuest(uid, String(msg.id), String(msg.period)); return send(ws, { t: 'eco', error: e, profile: this.profile(uid), quests: this.questsView(uid) }); }
      case 'streak_claim': { const e = this.claimStreak(uid); return send(ws, { t: 'eco', error: e, profile: this.profile(uid), quests: this.questsView(uid) }); }
      case 'achievements': return send(ws, { t: 'achievements', list: this.achievementsView(uid), title: this.social.user(uid).title });
      case 'set_title': return r('set_title', this.setTitle(uid, String(msg.title || '')));
      case 'shop': return send(ws, { t: 'shop', ...this.shopView(uid), sparks: this.social.user(uid).sparks });
      case 'buy': { const e = this.buy(uid, String(msg.item)); return send(ws, { t: 'shop', error: e, ...this.shopView(uid), sparks: this.social.user(uid).sparks }); }
      case 'wear': { const e = this.wear(uid, String(msg.item)); return send(ws, { t: 'shop', error: e, ...this.shopView(uid), sparks: this.social.user(uid).sparks }); }
      case 'season': return send(ws, { t: 'season', ...this.seasonView(uid) });
      case 'season_claim': { const e = this.claimSeason(uid, msg.tier >>> 0, msg.track === 'premium' ? 'premium' : 'free'); return send(ws, { t: 'season', error: e, ...this.seasonView(uid) }); }
      case 'referral': return r('referral', this.setReferrer(uid, msg.tag));
      case 'top': return send(ws, { t: 'top', hosts: this.top() });
      case 'demo_purchase': { const e = this.demoPurchase(uid, String(msg.product)); return send(ws, { t: 'demo_purchase', product: msg.product, error: e, profile: this.profile(uid) }); }
      case 'demo_wipe': this.demoWipe(uid); return send(ws, { t: 'eco', profile: this.profile(uid), quests: this.questsView(uid) });
      case 'ad_reward': { const e = this.adReward(uid); return send(ws, { t: 'ad_reward', error: e, profile: this.profile(uid) }); }
      case 'purchase': return send(ws, { t: 'purchase', product: msg.product, ...this.verifyPurchase(uid, msg.product, msg.token) });
    }
  }
}

const ECO_CMDS = new Set(['demo_purchase', 'demo_wipe', 'ad_reward', 'eco', 'quest_claim', 'streak_claim', 'achievements', 'set_title', 'shop', 'buy', 'wear', 'season', 'season_claim', 'referral', 'top', 'purchase']);
module.exports = { Economy, ECO_CMDS, QUESTS_DAILY, QUESTS_WEEKLY, ACHIEVEMENTS, SHOP, SEASON, STREAK_REWARD, dayKey, weekKey };
