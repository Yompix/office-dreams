/* ============================================================
   OFFICE DREAMS — game engine v2
   Pure logic, no DOM, no language, no Math.random.
   Same file runs in the browser (prediction) and on the server
   (authoritative replay of the player's action log).
   ============================================================ */

const TICK = 0.25; // seconds of game time per engine tick (fixed step => exact replay)
const DAY_SEC = 90; // game seconds per game day
const MAX_LVL = 10;
const SALE_DECAY = 0.7;
const OFFLINE_CAP = 6 * 3600; // passive income is credited for at most 6 h away
const STATE_V = 2;
// economy tuning knobs: coins are meant to be scarce — progress takes weeks, not hours
// tuned with a progression sim: an active player (30 min/day) opens floor 1 on day ~2, floor 3 in ~2 weeks, the CEO floor in ~6 weeks
const ECON = { rev: 0.12, upGrowth: 2.15, salK: 0.5, tapK: 1 };

const ROLES = ['prog', 'art', 'mkt', 'des', 'qa', 'snd', 'prod', 'cm'];
// need = the floor that unlocks the vacancy; the four later roles each add their own kind of bonus
const ROLE = {
  prog: { wp: 1.0, hire: 140, up: 110, sal: 36 },
  art: { wp: 0.8, hire: 180, up: 120, sal: 34 },
  mkt: { wp: 0, hire: 300, up: 150, sal: 32 },
  des: { wp: 0.8, hire: 230, up: 135, sal: 38 },
  qa: { wp: 0, hire: 2600, up: 1100, sal: 70, need: 'f1' }, // tester: higher review scores
  snd: { wp: 0.7, hire: 16000, up: 6500, sal: 240, need: 'f2' }, // sound designer: works on games + a sales bonus
  prod: { wp: 0, hire: 90000, up: 32000, sal: 900, need: 'f3' }, // producer: the whole team works faster
  cm: { wp: 0, hire: 380000, up: 130000, sal: 3200, need: 'f4' }, // community manager: sales fade slower
};

const GENRES = {
  rpg: { pts: 900, rev: 1700, w: { prog: 0.3, art: 0.3, des: 0.4 }, names: ['Neon Quest', 'Crystal Oath', 'Dragon Ledger', 'Blade of Dawn', 'Moonlit Realm', 'Rune Keepers'] },
  plat: { pts: 520, rev: 880, w: { prog: 0.3, art: 0.5, des: 0.2 }, names: ['Neon Run', 'Pixel Hop', 'Jump Circuit', 'Turbo Leap', 'Sky Climber'] },
  shoot: { pts: 680, rev: 1180, w: { prog: 0.55, art: 0.25, des: 0.2 }, names: ['Laser Storm', 'Neon Strike', 'Cyber Siege', 'Orbit Raid', 'Gunmetal Zero'] },
  sim: { pts: 780, rev: 1380, w: { prog: 0.35, art: 0.15, des: 0.5 }, names: ['Tiny Tower Co.', 'Cafe Empire', 'Farm Byte', 'Robo Factory', 'Pocket City'] },
  horror: { pts: 460, rev: 860, w: { prog: 0.25, art: 0.45, des: 0.3 }, names: ['Dark Hallway', 'Whisper Box', 'Night Shift 404', 'Hollow Floor', 'Pixel Seance'] },
  puzzle: { pts: 300, rev: 520, w: { prog: 0.25, art: 0.25, des: 0.5 }, names: ['Block Dreams', 'Color Link', 'Brain Grid', 'Tile Alchemy', 'Logic Loop'] },
};
const GENRE_ORDER = ['rpg', 'plat', 'shoot', 'sim', 'horror', 'puzzle'];

const TIERS = {
  mini: { pts: 1, rev: 1, rep: 0.6, need: 'b', fit: 1e9, w: 1 },
  indie: { pts: 3.5, rev: 5.2, rep: 1.2, need: 'f2', fit: 1e9, w: 2 },
  big: { pts: 11, rev: 20, rep: 2.5, need: 'f4', fit: 1e9, w: 4 },
  aaa: { pts: 34, rev: 70, rep: 5, need: 'f5', fit: 1e9, w: 8 },
};
const TIER_ORDER = ['mini', 'indie', 'big', 'aaa'];

// texts for floors / items / boosts live in the language files (floor.<id>.name|sub|perk, item.<id>.name|perk, boost.<id>.name|perk)
const FLOORS = [
  { id: 'b', price: 0, fw: 0 },
  { id: 'f1', price: 1500, fw: 15 },
  { id: 'f2', price: 12000, fw: 25 },
  { id: 'f3', price: 80000, fw: 40 },
  { id: 'f4', price: 350000, fw: 60 },
  { id: 'f5', price: 1500000, fw: 100 },
];
const FLOOR_ORDER = ['b', 'f1', 'f2', 'f3', 'f4', 'f5'];
// renovation: every owned floor can be refitted up to ★5; each star strengthens that floor's speciality
const RENO = {
  b: { k: 'tap', v: 0.06, base: 900 },
  f1: { k: 'sal', v: 0.04, base: 1500 },
  f2: { k: 'wp', v: 0.04, base: 12000 },
  f3: { k: 'sales', v: 0.05, base: 80000 },
  f4: { k: 'wp', v: 0.04, base: 350000 },
  f5: { k: 'sales', v: 0.05, base: 1500000 },
};
const RENO_MAX = 5;
const RENO_COST = [0.6, 1.2, 2.4, 4.8, 9.6]; // × the floor's base price, by the current star count

const ITEMS = [
  { id: 'coffee', price: 350 },
  { id: 'chairs', price: 700 },
  { id: 'monitor2', price: 1200 },
  { id: 'lamp', price: 900 },
  { id: 'lounge', price: 3000 },
  { id: 'rack', price: 4500 },
];
const BOOSTS = [
  { id: 'turbo', price: 5 },
  { id: 'hype', price: 5 },
];

// airdrop-point weights (used by the server; exposed so the UI can explain them)
const PTS_W = { tap: 0.02, rel: 6, up: 1.5, ev: 3 };

/* ---------- hooks (set by UI) ---------- */
const hooks = { emit: function () {} };
const emit = (type, data) => hooks.emit(type, data);

/* ---------- helpers ---------- */
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const round2 = (v) => Math.round(v * 100) / 100;
function fmt(n) {
  n = Math.floor(n);
  if (n >= 1e9) return (n / 1e9).toFixed(2).replace(/\.?0+$/, '') + 'B';
  if (n >= 1e6) return (n / 1e6).toFixed(2).replace(/\.?0+$/, '') + 'M';
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
}
// deterministic PRNG (mulberry32) — state lives in s.rng
function rand(s) {
  s.rng = (s.rng + 0x6d2b79f5) >>> 0;
  let t = s.rng;
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const rnd = (s, a, b) => a + rand(s) * (b - a);
const pick = (s, arr) => arr[Math.floor(rand(s) * arr.length)];
const fail = (err) => ({ ok: false, err });
const OK = { ok: true };
function cleanName(str) {
  const t = String(str == null ? '' : str).replace(/[^\p{L}\p{N} .'!?\-]/gu, '').replace(/\s+/g, ' ').trim().slice(0, 22);
  return t;
}

/* ---------- state ---------- */
function newState(seed) {
  return {
    v: STATE_V,
    t: 0,
    tk: 0,
    day: 1,
    clock: 0,
    cash: 250,
    gems: 3,
    rep: 0,
    energy: 40,
    earned: 0,
    you: { lvl: 1 },
    staff: {
      prog: { lvl: 0, mood: 85 },
      art: { lvl: 0, mood: 85 },
      mkt: { lvl: 0, mood: 85 },
      des: { lvl: 0, mood: 85 },
      qa: { lvl: 0, mood: 85 },
      snd: { lvl: 0, mood: 85 },
      prod: { lvl: 0, mood: 85 },
      cm: { lvl: 0, mood: 85 },
    },
    floors: { b: 1, f1: 0, f2: 0, f3: 0, f4: 0, f5: 0 },
    flv: { b: 0, f1: 0, f2: 0, f3: 0, f4: 0, f5: 0 }, // renovation stars per floor
    items: { coffee: 0, chairs: 0, monitor2: 0, lamp: 0, lounge: 0, rack: 0 },
    project: null,
    sel: { genre: 'puzzle', tier: 'mini', name: 'Block Dreams' }, // local UI preference, not synced
    auto: false,
    games: [],
    trend: { hot: 'plat', cold: 'horror' },
    buffs: { turbo: 0, hype: 0, invest: 0 },
    goals: {},
    // cumulative, monotone counters — the server prices airdrop points from their growth
    stats: { taps: 0, started: 0, released: 0, best: 0, relW: 0, evs: 0, ups: 0, flrW: 0, goalW: 0, dqW: 0 },
    dayIncome: 0,
    dayTaps: 0,
    income3: 0,
    pending: null,
    nextEventDay: 2,
    idea: null, // {role, ttl}: a light bulb over an employee — tap it for a burst of progress
    ideaCd: 30,
    bug: null, // {ttl, lane}: a bug crawling across the office — squash it before it eats progress
    bugCd: 50,
    evCool: {},
    log: [],
    legacy: 0,
    // the city around the office: timers are in total game seconds (s.t), which also run while away (capped)
    city: { cof: 0, promo: 0, trn: null, ctr: null, offer: null, ctrNext: 0, done: 0 },
    dk: 0,
    dq: { dk: 0, base: { taps: 0, released: 0, evs: 0, ups: 0 }, done: {} },
    settings: { sound: true }, // local, not synced
    ending: false,
    endingSeen: false,
    savedAt: 0,
    rev: 0,
    rng: seed === undefined ? (Math.floor(Math.random() * 4294967296) >>> 0) : seed >>> 0,
  };
}
function bump(s) {
  s.rev++;
}

/* ---------- derived values ---------- */
const floorOn = (s, id) => s.floors[id] > 0;
const hiredCount = (s) => ROLES.filter((r) => s.staff[r].lvl > 0).length;
const moodF = (m) => 0.7 + 0.3 * (m / 100);
// strength of a support role: level × mood factor (0 when not hired)
const roleK = (s, r) => (s.staff[r] && s.staff[r].lvl > 0 ? s.staff[r].lvl * moodF(s.staff[r].mood) : 0);
const roleLocked = (s, r) => !!(ROLE[r].need && !(s.floors[ROLE[r].need] > 0));
const qaBonus = (s) => 0.12 * roleK(s, 'qa');
const prodBonus = (s) => 0.04 * roleK(s, 'prod');
const sndBonus = (s) => 0.03 * roleK(s, 'snd');
const saleDecay = (s) => Math.min(0.8, SALE_DECAY + 0.01 * roleK(s, 'cm'));
const speedOf = (l) => 1 + 0.18 * (l - 1);
const legacyMult = (s) => 1 + 0.15 * s.legacy;
const renoLvl = (s, id) => (s.flv && s.flv[id]) || 0;
function renoBonus(s, k) {
  let b = 0;
  for (const id of FLOOR_ORDER) if (RENO[id].k === k) b += RENO[id].v * renoLvl(s, id);
  return b;
}
const renoStars = (s) => FLOOR_ORDER.reduce((n, id) => n + renoLvl(s, id), 0);
const renoCost = (s, id) => (renoLvl(s, id) >= RENO_MAX ? null : Math.round(RENO[id].base * RENO_COST[renoLvl(s, id)]));
function salaryOf(s, role, lvl) {
  const l = lvl === undefined ? s.staff[role].lvl : lvl;
  if (l <= 0) return 0;
  return Math.round(ROLE[role].sal * ECON.salK * Math.pow(1.3, l - 1) * (floorOn(s, 'f1') ? 0.9 : 1) * (1 - renoBonus(s, 'sal')));
}
function upCost(role, lvl) {
  return Math.round(ROLE[role].up * Math.pow(ECON.upGrowth, lvl - 1));
}
function youUpCost(lvl) {
  return Math.round(80 * Math.pow(ECON.upGrowth, lvl - 1));
}
function maxEnergy(s) {
  return 40 + 10 * (s.you.lvl - 1);
}
function energyRegen(s) {
  return 1.4 + (s.items.coffee ? 0.6 : 0);
}
function wpMult(s) {
  let m = 1;
  if (s.items.chairs) m *= 1.06;
  if (s.items.monitor2) m *= 1.1;
  if (floorOn(s, 'f4')) m *= 1.12;
  m *= 1 + renoBonus(s, 'wp');
  m *= 1 + prodBonus(s);
  if (s.buffs.turbo > 0) m *= 2;
  return m;
}
function wpRole(s, r) {
  const st = s.staff[r];
  if (st.lvl <= 0 || ROLE[r].wp === 0) return 0;
  let k = ROLE[r].wp * speedOf(st.lvl) * moodF(st.mood);
  if (floorOn(s, 'f2') && (r === 'art' || r === 'des')) k *= 1.1;
  return k * wpMult(s);
}
function wpTotal(s) {
  let sum = 0.35 * (1 + 0.08 * (s.you.lvl - 1)) * wpMult(s);
  for (const r of ['prog', 'art', 'des', 'snd']) sum += wpRole(s, r);
  return sum;
}
function tapPower(s) {
  const k = 1 + 0.18 * (s.you.lvl - 1);
  const boost = (s.items.monitor2 ? 1.2 : 1) * (s.buffs.turbo > 0 ? 1.5 : 1);
  return { pts: 3 * k * boost, cash: 1.4 * ECON.tapK * k * (1 + 0.05 * FLOOR_ORDER.filter((f) => floorOn(s, f)).length) * (1 + 0.1 * s.legacy) * (1 + renoBonus(s, 'tap')) };
}
function salaryTotal(s) {
  let t = 0;
  for (const r of ROLES) t += salaryOf(s, r);
  return t;
}
function salesMult(s, tier) {
  let m = 1 + s.rep / 1500;
  if (tier) m /= 1 + s.rep / TIERS[tier].fit;
  if (floorOn(s, 'f3')) m *= 1.2;
  if (floorOn(s, 'f5')) m *= 1.15;
  m *= 1 + renoBonus(s, 'sales');
  if (s.items.rack) m *= 1.06;
  if (s.items.lounge) m *= 1.04;
  const mk = s.staff.mkt;
  if (mk.lvl > 0) m *= 1 + 0.07 * mk.lvl * moodF(mk.mood);
  m *= 1 + sndBonus(s);
  return m * legacyMult(s);
}
function dailySalesRate(s) {
  let r = 0;
  for (const g of s.games) if (g.active) r += g.rate;
  return r;
}
function projectNeed(genre, tier) {
  return Math.round(GENRES[genre].pts * TIERS[tier].pts);
}
function tierUnlocked(s, tier) {
  return floorOn(s, TIERS[tier].need);
}
function effLevel(s, r) {
  const st = s.staff[r];
  const base = 0.5 * s.you.lvl;
  return st.lvl > 0 ? Math.max(base, st.lvl * (0.85 + 0.15 * (st.mood / 100))) : base;
}
function qualityFor(s, genre) {
  const g = GENRES[genre];
  let q = 0;
  for (const r of ['prog', 'art', 'des']) {
    const qr = clamp(0.12 + 0.088 * effLevel(s, r), 0, 1);
    q += g.w[r] * qr;
  }
  return q;
}
function scoreMult(score) {
  return clamp(Math.pow(score / 6, 1.9), 0.1, 3.5);
}
function expectedScore(s, genre) {
  const hot = s.trend.hot === genre ? 0.3 : 0;
  return clamp(3 + 7 * qualityFor(s, genre) + hot + qaBonus(s), 1, 10);
}
// language-independent rank id; UI shows L('rank.' + id)
function rankOf(s) {
  if (s.floors.f5) return 'owner';
  if (s.floors.f4) return 'director';
  if (s.floors.f2) return 'lead';
  if (s.stats.released > 0) return 'indie';
  return 'freelancer';
}
function dayMinutes(s) {
  return Math.floor((s.clock / DAY_SEC) * 720);
}
function clockText(s) {
  const m = 480 + dayMinutes(s);
  return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
}
function incomeEstimate(s) {
  return Math.max(s.income3, 40);
}
function newName(s, genre) {
  return pick(s, GENRES[genre].names);
}

/* ---------- the city: coffee shop, academy, contract board, game store ---------- */
const CITY = {
  cofCd: 900, // coffee break: +60% energy + team mood, every 15 min, for a small bill
  cofCost: (s) => Math.max(20, Math.round(incomeEstimate(s) * 0.12 / 10) * 10),
  promoCd: 1800, // store promo for the newest game, every 30 min
  trnSec: (lvl) => 600 + 300 * lvl, // academy course length (10 min at level 1 … 55 min at level 9)
  trnCost: (role, lvl) => Math.round(upCost(role, lvl) * 0.4), // a course is 60% cheaper than an upgrade, but takes time
  trnGems: 2, // finish a course right now
  ctrSec: 3600, // a contract must be shipped within an hour
  ctrCd: 300, // a new offer 5 min after the last one ended
};
const cityOf = (s) => s.city || (s.city = { cof: 0, promo: 0, trn: null, ctr: null, offer: null, ctrNext: 0, done: 0 });
function coffeeBreak(s) {
  const c = cityOf(s);
  if (s.t < c.cof) return fail('cooldown');
  const cost = CITY.cofCost(s);
  if (s.cash < cost) return fail('cash');
  s.cash -= cost;
  c.cof = s.t + CITY.cofCd;
  s.energy = Math.min(maxEnergy(s), s.energy + maxEnergy(s) * 0.6);
  moodAll(s, 6);
  bump(s);
  emit('sfx', 'coin');
  return OK;
}
function storePromo(s) {
  const c = cityOf(s);
  const g = s.games.find((x) => x.active);
  if (!g) return fail('no_game');
  if (s.t < c.promo) return fail('cooldown');
  const cost = promoCost(s);
  if (s.cash < cost) return fail('cash');
  s.cash -= cost;
  g.rate *= 1.35;
  c.promo = s.t + CITY.promoCd;
  addLog(s, 'log.promo', { name: g.name }, 'good');
  bump(s);
  emit('sfx', 'hit');
  return OK;
}
function promoCost(s) {
  return Math.round(Math.max(80, incomeEstimate(s) * 0.4) / 10) * 10;
}
function startTraining(s, role) {
  const c = cityOf(s), st = s.staff[role];
  if (!st || st.lvl <= 0) return fail('hire_first');
  if (st.lvl >= MAX_LVL) return fail('max');
  if (c.trn) return fail('trn_busy');
  const cost = CITY.trnCost(role, st.lvl);
  if (s.cash < cost) return fail('cash');
  s.cash -= cost;
  c.trn = { role, end: s.t + CITY.trnSec(st.lvl) };
  bump(s);
  emit('sfx', 'up');
  return OK;
}
function finishTraining(s, now) {
  const c = cityOf(s);
  if (!c.trn) return fail('unavailable');
  if (now) {
    if (s.gems < CITY.trnGems) return fail('gems');
    s.gems -= CITY.trnGems;
  }
  const st = s.staff[c.trn.role];
  const role = c.trn.role;
  c.trn = null;
  if (st && st.lvl > 0 && st.lvl < MAX_LVL) {
    st.lvl++;
    st.mood = Math.min(100, st.mood + 8);
    s.stats.ups++;
    addLog(s, 'log.trained', { role: '$role.' + role + '.name', lvl: st.lvl }, 'good');
    emit('trained', { role, lvl: st.lvl });
  }
  bump(s);
  checkGoals(s);
  return OK;
}
function bestTier(s) {
  let t = 'mini';
  for (const x of ['indie', 'big']) if (tierUnlocked(s, x)) t = x;
  return t;
}
function makeOffer(s) {
  const pool = GENRE_ORDER.filter((g) => g !== s.trend.cold);
  const genre = pick(s, pool), tier = bestTier(s);
  const min = clamp(Math.floor(expectedScore(s, genre)), 5, 9);
  const cash = Math.round(Math.max(300, incomeEstimate(s) * 3 * Math.sqrt(TIERS[tier].rev)) / 10) * 10;
  return { genre, tier, min, cash, gems: tier === 'mini' ? 1 : 2 };
}
function takeContract(s) {
  const c = cityOf(s);
  if (c.ctr) return fail('ctr_busy');
  if (!c.offer) return fail('unavailable');
  c.ctr = Object.assign({}, c.offer, { until: s.t + CITY.ctrSec });
  c.offer = null;
  bump(s);
  emit('sfx', 'coin');
  return OK;
}
function dropContract(s) {
  const c = cityOf(s);
  if (!c.ctr) return fail('unavailable');
  c.ctr = null;
  c.ctrNext = s.t + CITY.ctrCd;
  bump(s);
  return OK;
}
// a released game may fulfil the active contract
function checkContract(s, game) {
  const c = cityOf(s), k = c.ctr;
  if (!k || game.genre !== k.genre || game.score < k.min || TIER_ORDER.indexOf(game.tier) < TIER_ORDER.indexOf(k.tier)) return;
  giveCash(s, k.cash);
  giveGems(s, k.gems);
  s.rep = round2(s.rep + 1);
  s.stats.evs++;
  c.done++;
  c.ctr = null;
  c.ctrNext = s.t + CITY.ctrCd;
  addLog(s, 'log.contract', { cash: fmt(k.cash), gems: k.gems }, 'good');
  emit('contract', k);
}
function cityTick(s) {
  const c = cityOf(s);
  if (c.trn && s.t >= c.trn.end) finishTraining(s, false);
  if (c.ctr && s.t >= c.ctr.until) {
    c.ctr = null;
    c.ctrNext = s.t + CITY.ctrCd;
    if (!s.off) emit('toast', { k: 'toast.ctr_late', kind: 'bad' });
    bump(s);
  }
  if (!c.ctr && !c.offer && s.stats.released >= 1 && s.t >= c.ctrNext) { c.offer = makeOffer(s); bump(s); }
}

/* ---------- log (language-independent: key + params) ---------- */
function addLog(s, k, p, kind, ev) {
  const e = { day: s.day, k, p: p || {}, kind: kind || 'info' };
  if (ev) e.ev = ev;
  s.log.unshift(e);
  if (s.log.length > 40) s.log.length = 40;
}
function giveGems(s, n) {
  s.gems = Math.max(0, Math.round(s.gems + n));
}
function giveCash(s, amount) {
  s.cash += amount;
  if (amount > 0) s.earned += amount;
}

/* ---------- actions ---------- */
function doTap(s) {
  const cost = 2;
  if (s.energy < cost) return null;
  s.energy -= cost;
  const p = tapPower(s);
  giveCash(s, p.cash);
  s.dayIncome += p.cash;
  s.stats.taps++;
  if (s.project) {
    s.project.pts += p.pts;
    if (s.project.pts >= s.project.need) release(s);
  }
  return p;
}
function hire(s, role) {
  const st = s.staff[role];
  if (st.lvl > 0) return fail('owned');
  if (roleLocked(s, role)) return fail('locked');
  const price = ROLE[role].hire;
  if (s.cash < price) return fail('cash');
  s.cash -= price;
  st.lvl = 1;
  st.mood = 90;
  s.stats.ups++;
  addLog(s, 'log.hire', { role: '$role.' + role + '.name' }, 'good');
  bump(s);
  checkGoals(s);
  emit('sfx', 'up');
  return OK;
}
function upgradeStaff(s, role) {
  const st = s.staff[role];
  if (st.lvl <= 0) return fail('hire_first');
  if (st.lvl >= MAX_LVL) return fail('max');
  const price = upCost(role, st.lvl);
  if (s.cash < price) return fail('cash');
  s.cash -= price;
  st.lvl++;
  st.mood = Math.min(100, st.mood + 5);
  s.stats.ups++;
  bump(s);
  checkGoals(s);
  emit('sfx', 'up');
  return OK;
}
function upgradeYou(s) {
  if (s.you.lvl >= MAX_LVL) return fail('max');
  const price = youUpCost(s.you.lvl);
  if (s.cash < price) return fail('cash');
  s.cash -= price;
  s.you.lvl++;
  s.energy = Math.min(maxEnergy(s), s.energy + 10);
  s.stats.ups++;
  bump(s);
  checkGoals(s);
  emit('sfx', 'up');
  return OK;
}
function buyItem(s, id) {
  const it = ITEMS.find((i) => i.id === id);
  if (!it || s.items[id]) return fail('owned');
  if (s.cash < it.price) return fail('cash');
  s.cash -= it.price;
  s.items[id] = 1;
  s.stats.ups++;
  addLog(s, 'log.item', { item: '$item.' + id + '.name' }, 'good');
  bump(s);
  checkGoals(s);
  emit('sfx', 'up');
  return OK;
}
function nextFloor(s) {
  for (const id of FLOOR_ORDER) if (!floorOn(s, id)) return id;
  return null;
}
function buyFloor(s, id) {
  const f = FLOORS.find((x) => x.id === id);
  if (!f || floorOn(s, id)) return fail('owned');
  if (nextFloor(s) !== id) return fail('prev_floor');
  if (s.cash < f.price) return fail('cash');
  s.cash -= f.price;
  s.floors[id] = 1;
  s.stats.ups++;
  s.stats.flrW += f.fw;
  addLog(s, 'log.floor', { name: '$floor.' + id + '.name', sub: '$floor.' + id + '.sub' }, 'good');
  if (id === 'f5') {
    s.ending = true;
    emit('ending', null);
  }
  bump(s);
  checkGoals(s);
  emit('sfx', 'floor');
  return OK;
}
function renovate(s, id) {
  const f = FLOORS.find((x) => x.id === id);
  if (!f || !RENO[id]) return fail('unavailable');
  if (!floorOn(s, id)) return fail('locked');
  const cost = renoCost(s, id);
  if (cost === null) return fail('max');
  if (s.cash < cost) return fail('cash');
  if (!s.flv) s.flv = { b: 0, f1: 0, f2: 0, f3: 0, f4: 0, f5: 0 };
  s.cash -= cost;
  s.flv[id] = renoLvl(s, id) + 1;
  s.stats.ups++;
  s.stats.flrW += Math.max(2, Math.round(f.fw * 0.15));
  addLog(s, 'log.reno', { name: '$floor.' + id + '.name', n: s.flv[id] }, 'good');
  bump(s);
  checkGoals(s);
  emit('reno', { id, lvl: s.flv[id] });
  emit('sfx', 'floor');
  return OK;
}
function buyBoost(s, id) {
  const b = BOOSTS.find((x) => x.id === id);
  if (!b) return fail('unavailable');
  if (s.gems < b.price) return fail('gems');
  s.gems -= b.price;
  s.buffs[id] += DAY_SEC;
  bump(s);
  emit('sfx', 'up');
  return OK;
}
function startProject(s, genre, tier, name) {
  if (s.project) return fail('busy');
  if (!GENRES[genre] || !TIERS[tier]) return fail('unavailable');
  if (!tierUnlocked(s, tier)) return fail('locked');
  const nm = cleanName(name) || newName(s, genre);
  s.project = { genre, tier, name: nm, pts: 0, need: projectNeed(genre, tier), day: s.day };
  s.stats.started++;
  addLog(s, 'log.start', { name: nm }, 'info');
  bump(s);
  checkGoals(s);
  emit('sfx', 'up');
  return OK;
}
function setAuto(s, on) {
  s.auto = !!on;
  bump(s);
  return OK;
}
function release(s) {
  const p = s.project;
  if (!p) return;
  const g = GENRES[p.genre], t = TIERS[p.tier];
  const q = qualityFor(s, p.genre);
  const hot = s.trend.hot === p.genre, cold = s.trend.cold === p.genre;
  let score = 3 + 7 * q + rnd(s, -0.55, 0.55) + (hot ? 0.3 : 0) + qaBonus(s);
  score = Math.round(clamp(score, 1, 10) * 10) / 10;
  const trend = hot ? 1.5 : cold ? 0.75 : 1;
  const total = g.rev * t.rev * ECON.rev * scoreMult(score) * trend * salesMult(s, p.tier);
  const game = {
    id: s.stats.released + 1,
    name: p.name,
    genre: p.genre,
    tier: p.tier,
    score,
    total: Math.round(total),
    rate: total * (1 - SALE_DECAY),
    earned: 0,
    active: true,
    day: s.day,
  };
  s.games.unshift(game);
  if (s.games.length > 40) s.games.length = 40;
  s.stats.released++;
  s.stats.relW += t.w * clamp(score / 6, 0.3, 1.5);
  if (score > s.stats.best) s.stats.best = score;
  let repGain = Math.max(0, score - 5) * t.rep * (s.items.lamp ? 1.15 : 1);
  if (score < 4) repGain = -1;
  s.rep = Math.max(0, round2(s.rep + repGain));
  let gemGain = 0;
  if (score >= 9) {
    gemGain = 1;
    giveGems(s, 1);
  }
  addLog(s, 'log.release', { name: game.name, score: score.toFixed(1) }, score >= 7 ? 'good' : 'info');
  s.project = null;
  checkContract(s, game);
  bump(s);
  emit('release', { game, repGain, gemGain });
  emit('sfx', score >= 7 ? 'hit' : 'release');
  checkGoals(s);
  if (s.auto && tierUnlocked(s, p.tier)) {
    startProject(s, p.genre, p.tier, newName(s, p.genre));
  }
}

function catchIdea(s) {
  if (!s.idea || !s.project) return fail('unavailable');
  const p = s.project;
  const bonus = Math.round(Math.max(20, p.need * 0.06));
  const cash = Math.round(tapPower(s).cash * 12);
  s.idea = null;
  p.pts += bonus;
  giveCash(s, cash);
  s.dayIncome += cash;
  s.stats.ideas = (s.stats.ideas || 0) + 1;
  bump(s);
  emit('sfx', 'coin');
  if (p.pts >= p.need) release(s);
  return { ok: true, bonus, cash };
}

function squashBug(s) {
  if (!s.bug) return fail('unavailable');
  const cash = Math.round(tapPower(s).cash * 8);
  s.bug = null;
  giveCash(s, cash);
  s.dayIncome += cash;
  s.stats.bugs = (s.stats.bugs || 0) + 1;
  bump(s);
  return { ok: true, cash };
}

/* ---------- long-term loop: IPO (prestige) ---------- */
function canPrestige(s) {
  return floorOn(s, 'f5');
}
function prestige(s) {
  if (!canPrestige(s)) return fail('locked');
  const n = newState(s.rng);
  n.gems = s.gems + 3 + s.legacy;
  n.goals = s.goals;
  n.stats = s.stats;
  n.legacy = s.legacy + 1;
  n.dk = s.dk;
  n.dq = s.dq;
  n.sel = s.sel;
  n.settings = s.settings;
  n.endingSeen = true;
  n.t = s.t;
  n.tk = s.tk;
  n.city = Object.assign({}, cityOf(s), { trn: null, ctr: null, offer: null });
  n.rev = s.rev + 1;
  for (const k of Object.keys(s)) delete s[k];
  Object.assign(s, n);
  addLog(s, 'log.ipo', { n: s.legacy }, 'good');
  checkGoals(s);
  emit('sfx', 'floor');
  emit('ipo', { n: s.legacy });
  return OK;
}

/* ---------- goals (permanent quests) ---------- */
// rw: cash / gems / rep. pts = airdrop points weight (one-time, priced by the server)
const GOALS = [
  { id: 'tap10', rw: { cash: 30 }, pts: 2, test: (s) => s.stats.taps >= 10 },
  { id: 'proj1', rw: { cash: 60 }, pts: 3, test: (s) => s.stats.started >= 1 },
  { id: 'hire1', rw: { gems: 1 }, pts: 4, test: (s) => hiredCount(s) >= 1 },
  { id: 'rel1', rw: { cash: 120, gems: 1 }, pts: 8, test: (s) => s.stats.released >= 1 },
  { id: 'shop1', rw: { cash: 100 }, pts: 3, test: (s) => Object.values(s.items).some((v) => v) },
  { id: 'earn1k', rw: { gems: 1 }, pts: 6, test: (s) => s.earned >= 1000 },
  { id: 'team4', rw: { gems: 2 }, pts: 12, test: (s) => hiredCount(s) >= 4 },
  { id: 'score7', rw: { gems: 2, rep: 2 }, pts: 12, test: (s) => s.stats.best >= 7 },
  { id: 'f1', rw: { gems: 2 }, pts: 15, test: (s) => s.floors.f1 > 0 },
  { id: 'lvl5', rw: { gems: 2 }, pts: 12, test: (s) => ROLES.some((r) => s.staff[r].lvl >= 5) },
  { id: 'rel5', rw: { gems: 2 }, pts: 15, test: (s) => s.stats.released >= 5 },
  { id: 'hit9', rw: { gems: 4, rep: 5 }, pts: 30, test: (s) => s.stats.best >= 9 },
  { id: 'f3', rw: { gems: 4 }, pts: 30, test: (s) => s.floors.f3 > 0 },
  { id: 'earn50k', rw: { gems: 5 }, pts: 35, test: (s) => s.earned >= 50000 },
  { id: 'f5', rw: { gems: 8 }, pts: 80, test: (s) => s.floors.f5 > 0 },
  { id: 'reno1', rw: { gems: 2 }, pts: 6, test: (s) => renoStars(s) >= 1 },
  { id: 'reno15', rw: { gems: 5 }, pts: 25, test: (s) => renoStars(s) >= 15 },
  { id: 'renomax', rw: { gems: 15 }, pts: 60, test: (s) => renoStars(s) >= FLOOR_ORDER.length * RENO_MAX },
  { id: 'ipo1', rw: { gems: 5 }, pts: 60, test: (s) => s.legacy >= 1 },
  { id: 'ipo3', rw: { gems: 10 }, pts: 120, test: (s) => s.legacy >= 3 },
];
function checkGoals(s) {
  for (const g of GOALS) {
    if (!s.goals[g.id] && g.test(s)) {
      s.goals[g.id] = 1;
      bump(s);
      emit('goal', g);
    }
  }
}
function claimGoal(s, id) {
  const g = GOALS.find((x) => x.id === id);
  if (!g || s.goals[id] !== 1) return fail('unavailable');
  s.goals[id] = 2;
  if (g.rw.cash) giveCash(s, g.rw.cash);
  if (g.rw.gems) giveGems(s, g.rw.gems);
  if (g.rw.rep) s.rep = round2(s.rep + g.rw.rep);
  s.stats.goalW += g.pts;
  bump(s);
  emit('sfx', 'coin');
  return { ok: true, goal: g };
}

/* ---------- daily missions (reset by the server's UTC day key) ---------- */
const DAILY = [
  { id: 'tap', need: 100, stat: 'taps', pts: 4 },
  { id: 'rel', need: 1, stat: 'released', pts: 4 },
  { id: 'ev', need: 1, stat: 'evs', pts: 4 },
  { id: 'up', need: 2, stat: 'ups', pts: 4 },
];
const DAILY_ALL_PTS = 6;
function dailyProgress(s, d) {
  return Math.max(0, Math.min(d.need, (s.stats[d.stat] || 0) - (s.dq.base[d.stat] || 0)));
}
function dailyCash(s) {
  return Math.round(Math.max(120, incomeEstimate(s) * 0.8) / 10) * 10;
}
function syncDay(s, dk) {
  if (!dk) return false;
  s.dk = dk;
  if (s.dq.dk === dk) return false;
  s.dq = { dk, base: { taps: s.stats.taps, released: s.stats.released, evs: s.stats.evs, ups: s.stats.ups }, done: {} };
  bump(s);
  return true;
}
function claimDaily(s, id) {
  const d = DAILY.find((x) => x.id === id);
  if (!d) return fail('unavailable');
  if (s.dq.done[id]) return fail('claimed');
  if (dailyProgress(s, d) < d.need) return fail('notyet');
  s.dq.done[id] = 1;
  giveCash(s, dailyCash(s));
  s.stats.dqW += d.pts;
  if (DAILY.every((x) => s.dq.done[x.id])) {
    s.stats.dqW += DAILY_ALL_PTS;
    giveGems(s, 1);
  }
  bump(s);
  emit('sfx', 'coin');
  return OK;
}

/* ---------- events (story quests). Texts: ev.<id>.who|title|text|c<i>|r<i>[variant] ---------- */
const moodAll = (s, d) => {
  for (const r of ROLES) if (s.staff[r].lvl > 0) s.staff[r].mood = clamp(s.staff[r].mood + d, 0, 100);
};
const lastGame = (s) => s.games[0];
const r10 = (x) => Math.round(x / 10) * 10;
const EVENTS = {
  deadline: {
    when: (s) => s.day >= 2,
    vals: (s) => ({ bonus: r10(Math.max(200, incomeEstimate(s) * 1.2)) }),
    tp: (v) => ({}),
    choices: [
      {
        style: 'green', tags: ['cash', 'burnout'],
        lp: (v) => ({ bonus: fmt(v.bonus) }),
        run: (s, v) => { giveCash(s, v.bonus); s.rep = round2(s.rep + 1); moodAll(s, -10); s.buffs.turbo += DAY_SEC * 0.6; return { bonus: fmt(v.bonus) }; },
      },
      {
        style: 'blue', tags: [],
        run: (s) => { moodAll(s, 4); return {}; },
      },
      {
        style: 'pink', tags: ['rep'],
        run: (s) => { s.rep = Math.max(0, round2(s.rep - 2)); s.energy = Math.min(maxEnergy(s), s.energy + 30); return {}; },
      },
    ],
  },
  bug: {
    when: (s) => s.games.length >= 1 && s.games[0].active,
    vals: (s) => ({ name: lastGame(s).name, cost: r10(Math.max(60, incomeEstimate(s) * 0.5)) }),
    tp: (v) => ({ name: v.name }),
    choices: [
      {
        style: 'green', tags: ['rep'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: fmt(v.cost) }),
        run: (s, v) => { s.cash -= v.cost; s.rep = round2(s.rep + 1); const g = lastGame(s); if (g) g.rate *= 1.1; return {}; },
      },
      {
        style: 'blue', tags: ['burnout'],
        run: (s) => { moodAll(s, -8); s.rep = round2(s.rep + 1); return {}; },
      },
      {
        style: 'pink', tags: ['rep'],
        run: (s) => { const g = lastGame(s); if (g) g.rate *= 0.7; s.rep = Math.max(0, round2(s.rep - 2)); return {}; },
      },
    ],
  },
  investor: {
    when: (s) => s.day >= 4 && s.earned >= 1500 && !(s.buffs.invest > 0),
    vals: (s) => ({ sum: Math.round(Math.max(400, incomeEstimate(s) * 4) / 50) * 50 }),
    tp: (v) => ({ sum: fmt(v.sum) }),
    choices: [
      {
        style: 'green', tags: ['cash'],
        lp: (v) => ({ sum: fmt(v.sum) }),
        run: (s, v) => { giveCash(s, v.sum); s.buffs.invest = DAY_SEC * 6; return { sum: fmt(v.sum) }; },
      },
      {
        style: 'blue', tags: ['cash', 'opportunity'],
        run: (s, v) => {
          if (rand(s) < 0.5) { const sum = Math.round(v.sum * 1.5); giveCash(s, sum); s.buffs.invest = DAY_SEC * 6; return { _v: 'a', sum: fmt(sum) }; }
          return { _v: 'b' };
        },
      },
      {
        style: 'pink', tags: ['rep'],
        run: (s) => { s.rep = round2(s.rep + 1); return {}; },
      },
    ],
  },
  poach: {
    when: (s) => s.day >= 5 && hiredCount(s) >= 2,
    vals: (s) => {
      const hired = ROLES.filter((r) => s.staff[r].lvl > 0);
      const role = pick(s, hired);
      return { role, cost: Math.max(60, salaryOf(s, role) * 3) };
    },
    tp: (v) => ({ role: '$role.' + v.role + '.name' }),
    choices: [
      {
        style: 'green', tags: ['burnout'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: fmt(v.cost) }),
        run: (s, v) => { s.cash -= v.cost; s.staff[v.role].mood = Math.min(100, s.staff[v.role].mood + 20); return {}; },
      },
      {
        style: 'blue', tags: ['opportunity'],
        run: (s, v) => {
          if (rand(s) < 0.6) { s.staff[v.role].mood = Math.min(100, s.staff[v.role].mood + 10); return { _v: 'a' }; }
          s.staff[v.role].mood = Math.max(0, s.staff[v.role].mood - 20); return { _v: 'b' };
        },
      },
      {
        style: 'pink', tags: ['burnout'],
        run: (s, v) => { const st = s.staff[v.role]; st.lvl = Math.max(1, st.lvl - 1); st.mood = 70; return {}; },
      },
    ],
  },
  hackathon: {
    when: (s) => s.day >= 4,
    vals: (s) => ({ gems: 2 + Math.floor(rand(s) * 2) }),
    tp: (v) => ({ gems: v.gems }),
    choices: [
      {
        style: 'green', tags: ['rep', 'opportunity'], needEnergy: 30,
        run: (s, v) => { s.energy = Math.max(0, s.energy - 30); giveGems(s, v.gems); s.rep = round2(s.rep + 2); return { gems: v.gems }; },
      },
      {
        style: 'blue', tags: ['burnout'],
        run: (s, v) => { const g = Math.max(1, v.gems - 1); giveGems(s, g); moodAll(s, -5); return { gems: g }; },
      },
      {
        style: 'pink', tags: [],
        run: () => ({}),
      },
    ],
  },
  blogger: {
    when: (s) => s.games.length >= 2 && s.games[0].active,
    vals: (s) => ({ name: lastGame(s).name, cost: r10(Math.max(80, incomeEstimate(s) * 0.6)) }),
    tp: (v) => ({ name: v.name }),
    choices: [
      {
        style: 'green', tags: ['cash'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: fmt(v.cost) }),
        run: (s, v) => { s.cash -= v.cost; const g = lastGame(s); if (g) g.rate *= 1.6; return {}; },
      },
      {
        style: 'blue', tags: ['opportunity'],
        run: (s) => { if (rand(s) < 0.35) { const g = lastGame(s); if (g) g.rate *= 2; return { _v: 'a' }; } return { _v: 'b' }; },
      },
      {
        style: 'pink', tags: [],
        run: () => ({}),
      },
    ],
  },
  party: {
    when: (s) => s.day >= 3 && hiredCount(s) >= 1,
    vals: (s) => ({ cost: r10(Math.max(80, incomeEstimate(s) * 0.6)) }),
    tp: () => ({}),
    choices: [
      {
        style: 'green', tags: ['burnout'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: fmt(v.cost) }),
        run: (s, v) => { s.cash -= v.cost; moodAll(s, 18); return {}; },
      },
      {
        style: 'blue', tags: [], need: (s, v) => Math.round(v.cost / 3),
        lp: (v) => ({ cost: fmt(Math.round(v.cost / 3)) }),
        run: (s, v) => { s.cash -= Math.round(v.cost / 3); moodAll(s, 7); return {}; },
      },
      {
        style: 'pink', tags: ['burnout'],
        run: (s) => { moodAll(s, -3); return {}; },
      },
    ],
  },
  phishing: {
    when: (s) => s.day >= 3 && s.cash >= 300,
    vals: () => ({}),
    tp: () => ({}),
    choices: [
      {
        style: 'green', tags: ['burnout'],
        run: (s) => { const loss = Math.round(Math.min(s.cash, Math.max(50, s.cash * 0.25))); s.cash -= loss; moodAll(s, -5); return { loss: fmt(loss) }; },
      },
      {
        style: 'blue', tags: ['opportunity'],
        run: (s) => { giveGems(s, 1); return {}; },
      },
      {
        style: 'pink', tags: ['rep'],
        run: (s) => { giveGems(s, 1); s.rep = round2(s.rep + 1); return {}; },
      },
    ],
  },
  award: {
    when: (s) => s.games.length >= 1 && s.stats.best >= 8.5 && !s.evCool.awardDone,
    vals: (s) => ({ name: (s.games.find((g) => g.score >= 8.5) || s.games[0]).name }),
    tp: (v) => ({ name: v.name }),
    choices: [
      {
        style: 'green', tags: ['rep', 'opportunity'],
        run: (s) => { s.evCool.awardDone = 1; s.rep = round2(s.rep + 5); giveGems(s, 4); return {}; },
      },
      {
        style: 'blue', tags: ['rep'],
        run: (s) => { s.evCool.awardDone = 1; s.rep = round2(s.rep + 2); giveGems(s, 2); return {}; },
      },
    ],
  },
  server: {
    when: (s) => s.games.length >= 1 && s.games.some((g) => g.active),
    vals: (s) => ({ cost: r10(Math.max(100, incomeEstimate(s) * 0.7)) }),
    tp: () => ({}),
    choices: [
      {
        style: 'green', tags: ['rep'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: fmt(v.cost) }),
        run: (s, v) => { s.cash -= v.cost; return {}; },
      },
      {
        style: 'pink', tags: ['rep'],
        run: (s) => { for (const g of s.games) if (g.active) g.rate *= 0.85; return {}; },
      },
    ],
  },
  coffee: {
    when: (s) => !s.items.coffee && hiredCount(s) >= 1 && s.day >= 3,
    vals: () => ({ cost: 60 }),
    tp: () => ({}),
    choices: [
      {
        style: 'green', tags: ['burnout'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: v.cost }),
        run: (s, v) => { s.cash -= v.cost; moodAll(s, 5); return {}; },
      },
      {
        style: 'pink', tags: ['burnout'],
        run: (s) => { moodAll(s, -4); return {}; },
      },
    ],
  },
  fan: {
    when: (s) => s.games.length >= 1,
    vals: (s) => ({ name: lastGame(s).name, cost: r10(Math.max(60, incomeEstimate(s) * 0.4)) }),
    tp: (v) => ({ name: v.name }),
    choices: [
      {
        style: 'green', tags: ['rep', 'opportunity'], need: (s, v) => v.cost,
        lp: (v) => ({ cost: fmt(v.cost) }),
        run: (s, v) => { s.cash -= v.cost; s.rep = round2(s.rep + 2); giveGems(s, 1); return {}; },
      },
      {
        style: 'blue', tags: ['rep'],
        run: (s) => { s.rep = round2(s.rep + 1); return {}; },
      },
    ],
  },
};
const EVENT_IDS = Object.keys(EVENTS);

function eventView(s) {
  if (!s.pending) return null;
  const def = EVENTS[s.pending.id];
  if (!def) return null;
  const v = s.pending.v || {};
  return {
    id: s.pending.id,
    k: 'ev.' + s.pending.id,
    p: def.tp(v),
    choices: def.choices.map((c, i) => {
      const need = c.need ? c.need(s, v) : 0;
      const needEnergy = c.needEnergy || 0;
      return {
        i,
        style: c.style,
        tags: c.tags,
        lp: c.lp ? c.lp(v) : {},
        disabled: !!((need && s.cash < need) || (needEnergy && s.energy < needEnergy)),
        need,
        needEnergy,
      };
    }),
  };
}
function spawnEvent(s, forceId) {
  if (s.pending) return;
  let id = forceId;
  if (!id) {
    const pool = EVENT_IDS.filter((e) => EVENTS[e].when(s) && !(s.evCool[e] > s.day));
    if (!pool.length) return;
    id = s.day === 2 && EVENTS.deadline.when(s) && !(s.evCool.deadline > 0) ? 'deadline' : pick(s, pool);
  }
  const def = EVENTS[id];
  s.pending = { id, v: def.vals(s) };
  s.evCool[id] = s.day + 6;
  s.nextEventDay = s.day + 2 + Math.floor(rand(s) * 2);
  bump(s);
  emit('event', eventView(s));
}
function chooseEvent(s, idx) {
  if (!s.pending) return fail('unavailable');
  const def = EVENTS[s.pending.id];
  const view = eventView(s);
  const ch = def.choices[idx];
  if (!ch || view.choices[idx].disabled) return fail('unavailable');
  const id = s.pending.id;
  const res = ch.run(s, s.pending.v || {});
  const rk = 'ev.' + id + '.r' + idx + (res._v || '');
  const p = Object.assign({}, res);
  delete p._v;
  addLog(s, rk, p, 'quest', id);
  s.lastOutcome = { id, i: idx, lp: view.choices[idx].lp, rk, p };
  s.pending = null;
  s.stats.evs++;
  bump(s);
  checkGoals(s);
  emit('sfx', 'coin');
  return { ok: true, outcome: s.lastOutcome };
}

/* ---------- time ---------- */
function endDay(s) {
  const total = s.off ? 0 : salaryTotal(s); // while the player is away the team is on paid leave: no payroll, no burnout
  let paid = 0;
  if (total > 0) {
    if (s.cash >= total) {
      s.cash -= total;
      paid = total;
      const base = 3 + (s.items.coffee ? 2 : 0) + (s.items.lounge ? 3 : 0) + (s.items.chairs ? 1 : 0);
      for (const r of ROLES) if (s.staff[r].lvl > 0) s.staff[r].mood = clamp(s.staff[r].mood + base - (s.staff[r].mood > 92 ? 2 : 0), 0, 100);
    } else {
      paid = s.cash;
      s.cash = 0;
      if (!s.off) {
        moodAll(s, -22);
        addLog(s, 'log.salary_short', {}, 'bad');
        emit('toast', { k: 'toast.salary_short', kind: 'bad' });
      }
    }
  }
  // CEO-floor dividends (in-game cash)
  if (floorOn(s, 'f5')) giveCash(s, Math.round(Math.max(50, s.income3 * 0.1)));
  for (const g of s.games) {
    if (!g.active) continue;
    g.rate *= saleDecay(s);
    if (g.rate < 1) g.active = false;
  }
  s.income3 = s.income3 === 0 ? s.dayIncome : s.income3 * 0.6 + s.dayIncome * 0.4;
  const report = { day: s.day, income: s.dayIncome, salary: paid };
  if (!s.off) emit('day', report);
  addLog(s, 'log.day', { day: s.day, income: fmt(s.dayIncome), salary: fmt(paid) }, 'day');
  s.dayIncome = 0;
  s.day++;
  if (rand(s) < 0.5) {
    const hot = pick(s, GENRE_ORDER);
    let cold = pick(s, GENRE_ORDER);
    if (cold === hot) cold = GENRE_ORDER[(GENRE_ORDER.indexOf(hot) + 1) % GENRE_ORDER.length];
    s.trend = { hot, cold };
  }
  if (!s.off && !s.pending && s.day >= s.nextEventDay) spawnEvent(s);
  bump(s);
}
function tick(s, dt) {
  s.t += dt;
  s.tk += dt / TICK;
  s.clock += dt;
  s.energy = Math.min(maxEnergy(s), s.energy + energyRegen(s) * dt);
  for (const k in s.buffs) if (s.buffs[k] > 0) s.buffs[k] = Math.max(0, s.buffs[k] - dt);
  // ideas: while the team works, now and then someone gets an idea the player can catch
  if (s.idea) {
    s.idea.ttl -= dt;
    if (s.idea.ttl <= 0 || !s.project) s.idea = null;
  } else if (!s.off && s.project && hiredCount(s) > 0) {
    s.ideaCd -= dt;
    if (s.ideaCd <= 0) {
      const hired = ROLES.filter((r) => s.staff[r].lvl > 0);
      s.idea = { role: pick(s, hired), ttl: 12 };
      s.ideaCd = 25 + rand(s) * 20;
      emit('idea', s.idea);
    }
  }
  // bugs: crawl across the floor; if nobody squashes one, it eats a bit of the project
  if (s.bug) {
    s.bug.ttl -= dt;
    if (s.bug.ttl <= 0 || !s.project) {
      if (s.bug.ttl <= 0 && s.project && !s.off) {
        const loss = Math.round(s.project.need * 0.02);
        s.project.pts = Math.max(0, s.project.pts - loss);
        emit('bugEscaped', { loss });
      }
      s.bug = null;
    }
  } else if (!s.off && s.project && s.stats.released >= 1) {
    s.bugCd -= dt;
    if (s.bugCd <= 0) {
      s.bug = { ttl: 10, lane: Math.floor(rand(s) * 3) };
      s.bugCd = 45 + rand(s) * 40;
      emit('bug', s.bug);
    }
  }
  if (s.project) {
    s.project.pts += wpTotal(s) * dt;
    if (s.off) s.project.pts = Math.min(s.project.pts, s.project.need - 1); // the team waits for you to ship
    else if (s.project.pts >= s.project.need) release(s);
  }
  const hype = s.buffs.hype > 0 ? 2 : 1;
  const inv = s.buffs.invest > 0 ? 0.85 : 1;
  for (const g of s.games) {
    if (!g.active) continue;
    const inc = ((g.rate * dt) / DAY_SEC) * hype * inv;
    s.cash += inc;
    s.earned += inc;
    g.earned += inc;
    s.dayIncome += inc;
  }
  if (s.clock >= DAY_SEC) {
    s.clock -= DAY_SEC;
    endDay(s);
  }
  if ((Math.round(s.tk) & 3) === 0) { checkGoals(s); cityTick(s); } // once per game second, identical on client and server
}
// live play: integer number of fixed ticks (identical on client and server)
function advanceTicks(s, n) {
  for (let i = 0; i < n; i++) tick(s, TICK);
}
// seconds => ticks (rounded)
function advance(s, seconds) {
  advanceTicks(s, Math.round(seconds / TICK));
  checkGoals(s);
}
// away from the game: coarse 1 s steps, no events, no mood penalties
function advanceOffline(s, seconds) {
  const sec = Math.floor(clamp(seconds, 0, OFFLINE_CAP));
  s.off = 1;
  for (let i = 0; i < sec; i++) tick(s, 1);
  s.off = 0;
  checkGoals(s);
  return sec;
}

/* ---------- action dispatcher (shared by client prediction and server replay) ---------- */
const ACTIONS = {
  tap: (s) => (doTap(s) ? OK : fail('energy')),
  hire: (s, a) => (ROLES.includes(a[0]) ? hire(s, a[0]) : fail('unavailable')),
  ups: (s, a) => (ROLES.includes(a[0]) ? upgradeStaff(s, a[0]) : fail('unavailable')),
  upy: (s) => upgradeYou(s),
  buy: (s, a) => buyItem(s, String(a[0])),
  flr: (s, a) => buyFloor(s, String(a[0])),
  reno: (s, a) => renovate(s, String(a[0])),
  bst: (s, a) => buyBoost(s, String(a[0])),
  new: (s, a) => startProject(s, String(a[0]), String(a[1]), a[2]),
  auto: (s, a) => setAuto(s, a[0]),
  clm: (s, a) => claimGoal(s, String(a[0])),
  dly: (s, a) => claimDaily(s, String(a[0])),
  ev: (s, a) => chooseEvent(s, a[0] | 0),
  ipo: (s) => prestige(s),
  idea: (s) => catchIdea(s),
  bug: (s) => squashBug(s),
  cof: (s) => coffeeBreak(s),
  prm: (s) => storePromo(s),
  trn: (s, a) => (ROLES.includes(a[0]) ? startTraining(s, a[0]) : fail('unavailable')),
  trd: (s) => finishTraining(s, true),
  ctr: (s) => takeContract(s),
  ctx: (s) => dropContract(s),
};
function applyAction(s, a) {
  const f = ACTIONS[a[0]];
  if (!f) return fail('unavailable');
  return f(s, a.slice(1));
}
// batch = [[dTicks, 'type', ...args], ...]; tail = idle ticks after the last action
function replay(s, batch, tail) {
  let fails = 0;
  for (const e of batch) {
    advanceTicks(s, e[0]);
    const r = applyAction(s, e.slice(1));
    if (!r.ok) fails++;
  }
  advanceTicks(s, tail);
  return fails;
}

/* ---------- save / load ---------- */
function serialize(s) {
  s.savedAt = Date.now();
  const o = JSON.parse(JSON.stringify(s));
  delete o.off;
  return JSON.stringify(o);
}
function mergeInto(dst, src) {
  for (const k of Object.keys(src)) {
    if (src[k] && typeof src[k] === 'object' && !Array.isArray(src[k]) && dst[k] && typeof dst[k] === 'object' && !Array.isArray(dst[k])) mergeInto(dst[k], src[k]);
    else dst[k] = src[k];
  }
  return dst;
}
function deserialize(str) {
  const o = typeof str === 'string' ? JSON.parse(str) : str;
  if (!o || typeof o !== 'object' || o.v !== STATE_V) throw new Error('bad save');
  const s = mergeInto(newState(o.rng), o);
  if (s.project && !GENRES[s.project.genre]) s.project = null;
  if (!GENRES[s.sel.genre]) s.sel = { genre: 'puzzle', tier: 'mini', name: 'Block Dreams' };
  if (!TIERS[s.sel.tier]) s.sel.tier = 'mini';
  if (s.pending && !EVENTS[s.pending.id]) s.pending = null;
  return s;
}
function encodeSave(s) {
  const json = serialize(s);
  return btoa(unescape(encodeURIComponent(json)));
}
function decodeSave(code) {
  const json = decodeURIComponent(escape(atob(code.trim())));
  return deserialize(json);
}
// cheap fingerprint for telemetry / desync detection (not security)
function stateHash(s) {
  const str = [s.tk, s.day, Math.round(s.cash), s.gems, s.stats.taps, s.stats.released, s.rng].join('|');
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0).toString(16);
}

if (typeof module !== 'undefined') {
  module.exports = {
    ECON, roleK, roleLocked, qaBonus, prodBonus, sndBonus, saleDecay, RENO, RENO_MAX, RENO_COST, renoLvl, renoBonus, renoStars, renoCost, renovate, TICK, DAY_SEC, MAX_LVL, OFFLINE_CAP, STATE_V, ROLES, ROLE, GENRES, GENRE_ORDER, TIERS, TIER_ORDER, FLOORS, FLOOR_ORDER, ITEMS, BOOSTS, GOALS, DAILY, DAILY_ALL_PTS, EVENTS, EVENT_IDS, PTS_W,
    hooks, newState, tick, advance, advanceTicks, advanceOffline, doTap, hire, upgradeStaff, upgradeYou, buyItem, buyFloor, buyBoost, startProject, setAuto, release, claimGoal,
    claimDaily, dailyProgress, dailyCash, syncDay, canPrestige, prestige, applyAction, replay,
    chooseEvent, spawnEvent, eventView, wpTotal, wpRole, tapPower, salaryTotal, salaryOf, upCost, youUpCost, qualityFor, expectedScore,
    projectNeed, tierUnlocked, nextFloor, checkGoals, catchIdea, squashBug, hiredCount, serialize, deserialize, encodeSave, decodeSave, rankOf, fmt, cleanName, stateHash,
    CITY, coffeeBreak, storePromo, promoCost, startTraining, finishTraining, takeContract, dropContract, cityTick,
    dailySalesRate, salesMult, maxEnergy, newName, endDay, floorOn, scoreMult, clockText, legacyMult, rand,
  };
}
