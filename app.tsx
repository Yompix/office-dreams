// Pixel Casino v3 — PvP-казино в Telegram Mini App (покер, дуелі, джекпот). Рейк 5% з банку.
import { randomInt, createHmac, timingSafeEqual } from "node:crypto";
import { SQL } from "bun";
const { WalletContractV5R1, WalletContractV4, TonClient, internal, toNano, SendMode, Address } = (globalThis as any).__TON;
const { mnemonicToPrivateKey } = (globalThis as any).__TC;

const env = Bun.env;
const BOT_TOKEN = env.BOT_TOKEN || "";
const WEBAPP_URL = (env.WEBAPP_URL || "").replace(/\/$/, "");
const ADMIN_ID = String(env.ADMIN_ID || "1274614664");
const TON_ADDR = env.TON_ADDR || "UQBPWOOfBN1O5vLtUqs81jqQqiO2MRJKoIdfzq0RxHS7D9T-";
const STAR_COINS = Number(env.STAR_COINS || 10); // 1 зірка = стільки монет
let TON_COINS = Number(env.TON_COINS || 1370); let TON_IN = Math.round(TON_COINS * 0.97); // 1 TON = стільки монет
const RAKE = Number(env.RAKE || 0.10); // комісія з банку
const REF_PCT = Number(env.REF_PCT || 0.20); // частка рейку, що йде запрошувачу
const WD_FEE = Number(env.WD_FEE || 0.05), WD_FEE_MIN = Number(env.WD_FEE_MIN || 50), WD_MIN = Number(env.WD_MIN || 1000);
const MIN_BET = 10, MAX_BET = 5000;
// закриття проєкту: CASINO_CLOSED=1 — без поповнень і нових ігор, лише вивід залишків до WD_UNTIL (рррр-мм-дд)
const CLOSED = env.CASINO_CLOSED === "1"; const WD_UNTIL = String(env.WD_UNTIL || "");
const WD_OFF = CLOSED && !WD_UNTIL; // закрито і без дати виводу — вивід вимкнено
const WD_MIN_NOW = CLOSED ? Math.min(WD_MIN, WD_FEE_MIN + 50) : WD_MIN;
const WALLET_MNEMONIC = env.WALLET_MNEMONIC || ""; // гаманець автовиплат (необов'язково)
const WALLET_VER = (env.WALLET_VERSION || "v5r1").toLowerCase();
const AUTO_MAX = Number(env.AUTO_PAY_MAX_TON || 5), AUTO_DAY = Number(env.AUTO_PAY_DAY_TON || 30);
let autoOn = false;
const CARD_NUM = (env.CARD_NUM || "").replace(/\D/g, "");
const CARD_RATE: Record<string, number> = { USD: Number(env.USD_COINS || 1000), EUR: Number(env.EUR_COINS || 1150) };
const CARD_IN: Record<string, number> = { USD: Math.round(CARD_RATE.USD * 0.97), EUR: Math.round(CARD_RATE.EUR * 0.97) };
async function ratesTick() {
  try {
    const j: any = await (await fetch("https://api.coingecko.com/api/v3/simple/price?ids=the-open-network,toncoin,tether&vs_currencies=usd,eur", { signal: AbortSignal.timeout(10000) })).json();
    const ton = Number(j["the-open-network"]?.usd || j.toncoin?.usd), eur = Number(j.tether?.eur);
    if (ton > 0.05 && ton < 200 && !env.TON_COINS) { TON_COINS = Math.round(ton * CARD_RATE.USD); TON_IN = Math.round(TON_COINS * 0.97); }
    if (eur > 0.5 && eur < 1.5 && !env.EUR_COINS) { CARD_RATE.EUR = Math.round(CARD_RATE.USD / eur); CARD_IN.EUR = Math.round(CARD_RATE.EUR * 0.97); }
    console.log("rates TON", TON_COINS, "/", TON_IN, "EUR", CARD_RATE.EUR, "/", CARD_IN.EUR);
  } catch (e: any) { console.error("rates", e.message); }
}
ratesTick(); setInterval(ratesTick, 10 * 60 * 1000);
const CARD_MIN: Record<string, number> = { USD: 5, EUR: 5 }, CARD_MAX = 5000;
let BOT_USER = "";
let HTML = "";

const db = new SQL(env.DATABASE_URL || "postgres://pgt@localhost:5544/casino");
const N = (x: any) => Number(x);
const rnd = (n: number) => randomInt(n);

// ---------- База ----------
async function initDb() {
  await db`create table if not exists users(id text primary key, name text not null default 'Player', balance bigint not null default 0 check (balance >= 0), age_ok boolean not null default false, supporter boolean not null default false, deposited bigint not null default 0, turnover bigint not null default 0, created timestamptz not null default now())`;
  await db`create table if not exists ledger(id bigserial primary key, user_id text not null, kind text not null, amount bigint not null, ref text, note text, created timestamptz not null default now())`;
  await db`create unique index if not exists ledger_kind_ref on ledger(kind, ref)`;
  await db`create index if not exists ledger_user on ledger(user_id, id desc)`;
  await db`create table if not exists withdrawals(id bigserial primary key, user_id text not null, addr text not null, coins bigint not null, fee bigint not null, ton numeric not null, status text not null default 'pending', created timestamptz not null default now())`;
  await db`create table if not exists holds(kind text not null, ref text not null, user_id text not null, amount bigint not null, primary key(kind, ref, user_id))`;
  await db`create table if not exists hands(id bigserial primary key, table_id int, pot bigint, rake bigint, info text, created timestamptz not null default now())`;
  await db`create table if not exists parts(name text primary key, body text not null)`;
  await db`alter table users add column if not exists banned boolean not null default false`;
  await db`create table if not exists card_dep(id bigserial primary key, user_id text not null, cur text not null, amount numeric not null, coins bigint not null, status text not null default 'new', created timestamptz not null default now())`;
  await db`create table if not exists card_wd(id bigserial primary key, user_id text not null, card text not null, cur text not null, amount numeric not null, coins bigint not null, fee bigint not null, status text not null default 'pending', created timestamptz not null default now())`;
  await db`alter table users add column if not exists ref_by text`;
  await db`alter table users add column if not exists lang text not null default ''`;
  await db`create index if not exists users_ref on users(ref_by)`;
  await db`insert into users(id, name, age_ok) values('house', 'house', true) on conflict do nothing`;
}

// змінити баланс (атомарно з записом у журнал); повертає false, якщо ref уже оброблено
async function adj(t: any, uid: string, delta: number, kind: string, ref: string | null = null, note: string | null = null) {
  delta = Math.trunc(delta);
  if (ref) {
    const r = await t`insert into ledger(user_id, kind, amount, ref, note) values(${uid}, ${kind}, ${delta}, ${ref}, ${note}) on conflict (kind, ref) do nothing returning id`;
    if (!r.length) return false;
  } else await t`insert into ledger(user_id, kind, amount, note) values(${uid}, ${kind}, ${delta}, ${note})`;
  const u = await t`update users set balance = balance + ${delta} where id = ${uid} and balance + ${delta} >= 0 returning balance`;
  if (!u.length) throw new Error("no_funds");
  return true;
}
const holdAdd = (t: any, kind: string, ref: string, uid: string, amount: number) =>
  t`insert into holds(kind, ref, user_id, amount) values(${kind}, ${ref}, ${uid}, ${Math.trunc(amount)}) on conflict (kind, ref, user_id) do update set amount = holds.amount + excluded.amount`;
const holdSet = (t: any, kind: string, ref: string, uid: string, amount: number) =>
  t`insert into holds(kind, ref, user_id, amount) values(${kind}, ${ref}, ${uid}, ${Math.trunc(amount)}) on conflict (kind, ref, user_id) do update set amount = excluded.amount`;
const holdDel = (t: any, kind: string, ref: string, uid?: string) =>
  uid ? t`delete from holds where kind = ${kind} and ref = ${ref} and user_id = ${uid}` : t`delete from holds where kind = ${kind} and ref = ${ref}`;
const getBal = async (uid: string) => { const r = await db`select balance from users where id = ${uid}`; return r.length ? N(r[0].balance) : 0; };
const getUserRow = async (uid: string) => (await db`select * from users where id = ${uid}`)[0];

// після перезапуску повертаємо все, що було «в грі» (столи, дуелі, джекпот)
async function recoverHolds() {
  const hs = await db`select * from holds`;
  for (const h of hs) {
    await db.begin(async (t: any) => {
      await adj(t, h.user_id, N(h.amount), "refund_" + h.kind, null, "restart " + h.ref);
      await holdDel(t, h.kind, h.ref, h.user_id);
    });
  }
  if (hs.length) console.log("Повернено holds:", hs.length);
}

// ---------- Авторизація ----------
function verify(initData: string) {
  if (!initData) return null;
  const params = new URLSearchParams(initData);
  const hash = params.get("hash"), userRaw = params.get("user");
  if (!userRaw) return null;
  if (BOT_TOKEN) {
    if (!hash) return null;
    params.delete("hash");
    const check = [...params.entries()].map(([k, v]) => k + "=" + v).sort().join("\n");
    const secret = createHmac("sha256", "WebAppData").update(BOT_TOKEN).digest();
    const calc = createHmac("sha256", secret).update(check).digest("hex");
    if (calc.length !== hash.length || !timingSafeEqual(Buffer.from(calc), Buffer.from(hash))) return null;
    if (Date.now() / 1000 - parseInt(params.get("auth_date") || "0", 10) > 86400) return null;
  }
  try { const u = JSON.parse(userRaw); return { id: "tg" + u.id, name: String(u.first_name || u.username || "Player").slice(0, 24), lang: String(u.language_code || "").slice(0, 5), sp: String(params.get("start_param") || "") }; } catch { return null; }
}
async function authFrom(initData: string, dev: string) {
  let v = verify(initData);
  if (!v && !BOT_TOKEN) { const d = String(dev || "guest").slice(0, 32).replace(/[^\w-]/g, ""); v = { id: "dev_" + d, name: "Guest " + d.slice(0, 6) }; }
  if (!v) return null;
  const r = await db`insert into users(id, name, lang) values(${v.id}, ${v.name}, ${(v as any).lang || ""}) on conflict (id) do update set name = excluded.name, lang = case when excluded.lang <> '' then excluded.lang else users.lang end returning *`;
  if (r[0].banned) return null;
  const sp = (v as any).sp; if (sp && !r[0].ref_by) { const rm = /^r(\d{4,15})$/.exec(sp); if (rm) setRef(v.id, "tg" + rm[1], v.name).catch(() => {}); }
  return r[0];
}

const json = (o: any, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
const err = (e: string, extra: any = {}) => json({ error: e, ...extra }, 400);
const isAdmin = (uid: string) => uid === "tg" + ADMIN_ID || (!BOT_TOKEN && uid === "dev_admin");
const needAge = (u: any) => { if (!u.age_ok) throw new Error("age"); };

// ---------- WebSocket-реєстр ----------
const socks = new Set<any>();
const send = (ws: any, o: any) => { try { ws.send(JSON.stringify(o)); } catch {} };
const toUser = (uid: string, o: any) => { for (const w of socks) if (w.data.uid === uid) send(w, o); };
async function pushBal(uid: string) { if (uid === "house") return; toUser(uid, { t: "bal", balance: await getBal(uid) }); }

// ---------- Реферальна програма ----------
const L3 = (lc: any, uk: string, ru: string, en: string) => { lc = String(lc || "").slice(0, 2).toLowerCase(); return lc === "ru" || lc === "be" ? ru : lc === "uk" || lc === "" ? uk : en; };
// прив'язка запрошувача: лише новому гравцеві, який ще не грав і не поповнював
async function setRef(uid: string, refId: string, name = "") {
  if (!refId || refId === uid) return;
  const r = await db`update users set ref_by = ${refId} where id = ${uid} and ref_by is null and turnover = 0 and deposited = 0 and exists (select 1 from users where id = ${refId} and id <> 'house' and not banned) returning id`;
  if (!r.length || !BOT_TOKEN || !refId.startsWith("tg")) return;
  const lc = (await db`select lang from users where id = ${refId}`)[0]?.lang || "";
  const pct = Math.round(REF_PCT * 100);
  tg("sendMessage", { chat_id: refId.slice(2), text: L3(lc, `👥 За твоїм посиланням зайшов ${name || "новий гравець"}. Ти отримуватимеш ${pct}% від комісії казино з його ігор.`, `👥 По твоей ссылке зашёл ${name || "новый игрок"}. Ты будешь получать ${pct}% от комиссии казино с его игр.`, `👥 ${name || "A new player"} joined via your link. You'll earn ${pct}% of the casino's commission from their games.`) }).catch(() => {});
}
// частка рейку запрошувачам; викликається всередині транзакції після зарахування рейку на house
async function refPay(t: any, parts: [string, number][], rake: number, tag: string) {
  if (rake <= 0 || !parts.length) return;
  const W = parts.reduce((a, p) => a + p[1], 0) || 1;
  for (const [uid, w] of parts) {
    const b = Math.floor(((rake * w) / W) * REF_PCT); if (b <= 0) continue;
    const r = await t`select ref_by from users where id = ${uid}`;
    const rb = r[0]?.ref_by; if (!rb || rb === uid) continue;
    const ok = await t`select 1 from users where id = ${rb} and id <> 'house' and not banned`; if (!ok.length) continue;
    await adj(t, "house", -b, "ref_cost", null, tag + " " + uid);
    await adj(t, rb, b, "ref_bonus", null, uid);
    setTimeout(() => pushBal(rb), 600);
  }
}

// ---------- Дуелі ----------
type Duel = { id: number; uid: string; name: string; stake: number; game: string };
const duels = new Map<number, Duel>(); let duelSeq = 1;
const checkStake = (n: any) => { n = Math.floor(Number(n)); if (!Number.isFinite(n) || n < MIN_BET || n > MAX_BET) throw new Error("bad_bet"); return n; };
const rakeOf = (pot: number) => Math.floor(pot * RAKE);

async function duelCreate(ws: any, m: any) {
  const { uid, name } = ws.data; const u = await getUserRow(uid); needAge(u);
  const stake = checkStake(m.stake); const game = m.game === "dice" ? "dice" : "coin";
  if ([...duels.values()].filter((d) => d.uid === uid).length >= 3) throw new Error("too_many");
  const id = duelSeq++;
  await db.begin(async (t: any) => { await adj(t, uid, -stake, "duel_stake", null, "d" + id); await holdAdd(t, "duel", String(id), uid, stake); await t`update users set turnover = turnover + ${stake} where id = ${uid}`; });
  duels.set(id, { id, uid, name, stake, game });
  pushBal(uid); sendLobby();
}
async function duelCancel(ws: any, m: any) {
  const d = duels.get(Number(m.id)); if (!d || d.uid !== ws.data.uid) return;
  duels.delete(d.id);
  await db.begin(async (t: any) => { await adj(t, d.uid, d.stake, "duel_refund", null, "d" + d.id); await holdDel(t, "duel", String(d.id), d.uid); await t`update users set turnover = turnover - ${d.stake} where id = ${d.uid}`; });
  pushBal(d.uid); sendLobby();
}
async function duelJoin(ws: any, m: any) {
  const { uid, name } = ws.data; const u = await getUserRow(uid); needAge(u);
  const d = duels.get(Number(m.id)); if (!d) throw new Error("gone"); if (d.uid === uid) throw new Error("own");
  duels.delete(d.id);
  let ra = 0, rb = 0;
  let winner: string;
  if (d.game === "dice") { do { ra = 1 + rnd(6); rb = 1 + rnd(6); } while (ra === rb); winner = ra > rb ? d.uid : uid; }
  else { const f = rnd(2); winner = f === 0 ? d.uid : uid; ra = f === 0 ? 1 : 0; rb = 1 - ra; }
  const pot = d.stake * 2, rake = rakeOf(pot), win = pot - rake;
  try {
    await db.begin(async (t: any) => {
      await adj(t, uid, -d.stake, "duel_stake", null, "d" + d.id);
      await t`update users set turnover = turnover + ${d.stake} where id = ${uid}`;
      await adj(t, winner, win, "duel_win", null, "d" + d.id);
      await adj(t, "house", rake, "rake", null, "duel d" + d.id);
      await refPay(t, [[d.uid, 1], [uid, 1]], rake, "duel");
      await holdDel(t, "duel", String(d.id), d.uid);
    });
  } catch (e) { duels.set(d.id, d); throw e; }
  const res = { t: "duel.result", id: d.id, game: d.game, stake: d.stake, win, rake, winner, a: { uid: d.uid, name: d.name, roll: ra }, b: { uid, name, roll: rb } };
  toUser(d.uid, res); if (uid !== d.uid) toUser(uid, res);
  pushBal(d.uid); pushBal(uid); sendLobby();
}

// ---------- Джекпот ----------
const jp: any = { id: 1, bets: new Map<string, { name: string; amt: number }>(), endsAt: 0, state: "open", winner: null, ticket: 0, last: null };
const JP_MS = 30000, JP_CAP = 5000;
const jpPot = () => [...jp.bets.values()].reduce((a, b) => a + b.amt, 0);
function jpView() {
  const pot = jpPot();
  return { id: jp.id, state: jp.state, pot, left: jp.endsAt ? Math.max(0, jp.endsAt - Date.now()) : null,
    players: [...jp.bets.entries()].map(([uid, b]) => ({ uid, name: b.name, amt: b.amt })), winner: jp.winner, ticket: jp.ticket, last: jp.last };
}
async function jpBet(ws: any, m: any) {
  const { uid, name } = ws.data; const u = await getUserRow(uid); needAge(u);
  if (jp.state !== "open") throw new Error("closed");
  if (jp.endsAt && jp.endsAt - Date.now() < 2500) throw new Error("closed");
  const amt = checkStake(m.amount);
  const have = jp.bets.get(uid)?.amt || 0; if (have + amt > JP_CAP) throw new Error("cap");
  const rid = jp.id;
  await db.begin(async (t: any) => { await adj(t, uid, -amt, "jp_bet", null, "r" + rid); await holdAdd(t, "jp", String(rid), uid, amt); await t`update users set turnover = turnover + ${amt} where id = ${uid}`; });
  if (jp.id !== rid || jp.state !== "open") return; // раунд встиг закритись — hold повернеться при наступному виплаті/рестарті
  const cur = jp.bets.get(uid); if (cur) cur.amt += amt; else jp.bets.set(uid, { name, amt });
  if (jp.bets.size >= 2 && !jp.endsAt) { jp.endsAt = Date.now() + JP_MS; setTimeout(() => jpSpin(rid), JP_MS); }
  pushBal(uid); sendLobby();
}
function jpSpin(rid: number) {
  if (jp.id !== rid || jp.state !== "open") return;
  jp.state = "spin";
  const pot = jpPot(); let r = rnd(pot); let winner = "";
  let before = 0;
  for (const [uid, b] of jp.bets) { if (r < b.amt) { winner = uid; jp.ticket = (before + r) / pot; break; } r -= b.amt; before += b.amt; }
  jp.winner = { uid: winner, name: jp.bets.get(winner)!.name };
  sendLobby();
  setTimeout(() => jpPay(rid, winner, pot), 5500);
}
async function jpPay(rid: number, winner: string, pot: number) {
  const rake = rakeOf(pot), win = pot - rake;
  const players = [...jp.bets.keys()];
  try {
    await db.begin(async (t: any) => {
      await adj(t, winner, win, "jp_win", null, "r" + rid); await adj(t, "house", rake, "rake", null, "jp r" + rid);
      await refPay(t, [...jp.bets.entries()].map(([u, b]: any) => [u, b.amt] as [string, number]), rake, "jp");
      await holdDel(t, "jp", String(rid));
    });
  } catch (e: any) { console.error("jpPay", e.message); }
  jp.last = { name: jp.winner.name, pot, win };
  jp.id++; jp.bets = new Map(); jp.endsAt = 0; jp.state = "open"; jp.winner = null; jp.ticket = 0;
  for (const p of players) pushBal(p);
  sendLobby();
}

// ---------- Покер (Texas Hold'em) ----------
const HAND_NAMES = ["Старша карта", "Пара", "Дві пари", "Трійка", "Стріт", "Флеш", "Фул-хаус", "Каре", "Стріт-флеш"];
function eval5(c: number[]) {
  const r = c.map((x) => x % 13).sort((a, b) => b - a);
  const flush = c.every((x) => Math.floor(x / 13) === Math.floor(c[0] / 13));
  const cnt: Record<number, number> = {}; r.forEach((x) => (cnt[x] = (cnt[x] || 0) + 1));
  const groups = Object.entries(cnt).map(([k, v]) => [+k, v]).sort((a, b) => b[1] - a[1] || b[0] - a[0]);
  let sh = -1;
  if (new Set(r).size === 5) { if (r[0] - r[4] === 4) sh = r[0]; else if (r[0] === 12 && r[1] === 3 && r[4] === 0) sh = 3; }
  let cat: number, vals: number[];
  if (sh >= 0 && flush) { cat = 8; vals = [sh]; }
  else if (groups[0][1] === 4) { cat = 7; vals = groups.map((g) => g[0]); }
  else if (groups[0][1] === 3 && groups[1][1] === 2) { cat = 6; vals = groups.map((g) => g[0]); }
  else if (flush) { cat = 5; vals = r; }
  else if (sh >= 0) { cat = 4; vals = [sh]; }
  else if (groups[0][1] === 3) { cat = 3; vals = groups.map((g) => g[0]); }
  else if (groups[0][1] === 2 && groups[1][1] === 2) { cat = 2; vals = groups.map((g) => g[0]); }
  else if (groups[0][1] === 2) { cat = 1; vals = groups.map((g) => g[0]); }
  else { cat = 0; vals = r; }
  let s = cat; for (let i = 0; i < 5; i++) s = s * 13 + (vals[i] ?? 0);
  return s;
}
function best7(cards: number[]) {
  let best = -1; const n = cards.length;
  for (let a = 0; a < n; a++) for (let b = a + 1; b < n; b++) for (let c = b + 1; c < n; c++) for (let d = c + 1; d < n; d++) for (let e = d + 1; e < n; e++) {
    const s = eval5([cards[a], cards[b], cards[c], cards[d], cards[e]]); if (s > best) best = s;
  }
  return best;
}
const catOf = (score: number) => Math.floor(score / 13 ** 5);
function shuffled() { const d = Array.from({ length: 52 }, (_, i) => i); for (let i = 51; i > 0; i--) { const j = rnd(i + 1); [d[i], d[j]] = [d[j], d[i]]; } return d; }

type Seat = { uid: string; name: string; stack: number; bet: number; total: number; folded: boolean; allin: boolean; acted: boolean; cards: number[]; inHand: boolean; leaving: boolean; timeouts: number; last: string };
type Table = { id: number; sb: number; bb: number; min: number; max: number; seats: (Seat | null)[]; btn: number; phase: string; board: number[]; deck: number[]; cur: number; minRaise: number; toAct: number; deadline: number; timer: any; startTimer: any; hand: number; result: any; flop: boolean; n: number };
const SEATS = 6;
const tables: Table[] = [
  { id: 1, sb: 5, bb: 10, min: 200, max: 1000, n: 6 }, { id: 2, sb: 25, bb: 50, min: 1000, max: 5000, n: 6 }, { id: 3, sb: 100, bb: 200, min: 4000, max: 20000, n: 6 },
  { id: 4, sb: 5, bb: 10, min: 200, max: 1000, n: 2 }, { id: 5, sb: 25, bb: 50, min: 1000, max: 5000, n: 2 }, { id: 6, sb: 100, bb: 200, min: 4000, max: 20000, n: 2 },
].map((c) => ({ ...c, seats: Array(c.n).fill(null), btn: -1, phase: "wait", board: [], deck: [], cur: 0, minRaise: c.bb, toAct: -1, deadline: 0, timer: null, startTimer: null, hand: 0, result: null, flop: false }));
const tableOf = (uid: string) => { for (const t of tables) { const i = t.seats.findIndex((s) => s && s.uid === uid); if (i >= 0) return { t, i }; } return null; };
const potOf = (t: Table) => t.seats.reduce((a, s) => a + (s ? s.total : 0), 0);
const inPlay = (t: Table) => t.phase === "preflop" || t.phase === "flop" || t.phase === "turn" || t.phase === "river";

function tableView(t: Table, uid: string) {
  const you = t.seats.findIndex((s) => s && s.uid === uid);
  const shown = t.phase === "result" && t.result ? new Set(t.result.shows.map((x: any) => x.i)) : new Set();
  return {
    id: t.id, sb: t.sb, bb: t.bb, min: t.min, max: t.max, phase: t.phase, board: t.board, pot: potOf(t), cur: t.cur, minRaise: t.minRaise,
    toAct: t.toAct, left: t.deadline ? Math.max(0, t.deadline - Date.now()) : null, btn: t.btn, hand: t.hand, you, result: t.result,
    seats: t.seats.map((s, i) => s ? {
      i, name: s.name, stack: s.stack, bet: s.bet, folded: s.folded, allin: s.allin, inHand: s.inHand, last: s.last, leaving: s.leaving,
      cards: s.uid === uid && s.cards.length ? s.cards : shown.has(i) ? s.cards : s.inHand && !s.folded && s.cards.length ? [-1, -1] : [],
    } : null),
  };
}
function sendTable(t: Table) {
  for (const w of socks) if (w.data.sub === "pk:" + t.id) send(w, { t: "pk", table: tableView(t, w.data.uid) });
  sendLobby();
}
const nextIdx = (t: Table, from: number, pred: (s: Seat) => boolean) => { for (let k = 1; k <= t.n; k++) { const i = (from + k) % t.n; const s = t.seats[i]; if (s && pred(s)) return i; } return -1; };
const canActFn = (s: Seat) => s.inHand && !s.folded && !s.allin;

async function pkSit(ws: any, m: any) {
  const { uid, name } = ws.data; const u = await getUserRow(uid); needAge(u);
  const t = tables.find((x) => x.id === Number(m.table)); if (!t) throw new Error("gone");
  { const f0 = tableOf(uid); if (f0) { ws.data.sub = "pk:" + f0.t.id; send(ws, { t: "pk", table: tableView(f0.t, uid), back: true }); return; } }
  const buyin = Math.floor(Number(m.buyin)); if (!Number.isFinite(buyin) || buyin < t.min || buyin > t.max) throw new Error("bad_buyin");
  const i = t.seats.findIndex((s) => !s); if (i < 0) throw new Error("full");
  t.seats[i] = { uid, name, stack: 0, bet: 0, total: 0, folded: false, allin: false, acted: false, cards: [], inHand: false, leaving: false, timeouts: 0, last: "" }; // резервуємо місце
  try {
    await db.begin(async (tx: any) => { await adj(tx, uid, -buyin, "pk_buyin", null, "t" + t.id); await holdSet(tx, "pk", String(t.id), uid, buyin); });
  } catch (e) { t.seats[i] = null; throw e; }
  t.seats[i]!.stack = buyin;
  pushBal(uid); sendTable(t); maybeStart(t);
}
async function cashOut(t: Table, i: number) {
  const s = t.seats[i]; if (!s) return;
  t.seats[i] = null; // спочатку звільняємо місце, щоб не виплатити двічі
  try {
    await db.begin(async (tx: any) => { if (s.stack > 0) await adj(tx, s.uid, s.stack, "pk_cashout", null, "t" + t.id); await holdDel(tx, "pk", String(t.id), s.uid); });
  } catch (e: any) { console.error("cashOut", e.message); t.seats[i] = s; return; }
  pushBal(s.uid);
}
async function pkLeave(uid: string) {
  const f = tableOf(uid); if (!f) return;
  const { t, i } = f; const s = t.seats[i]!;
  if (inPlay(t) && s.inHand) {
    s.leaving = true;
    if (!s.folded) { try { await act(t, i, "fold", 0, true); } catch {} }
    sendTable(t);
  } else if (t.phase === "result" && s.inHand) { s.leaving = true; sendTable(t); }
  else { await cashOut(t, i); sendTable(t); }
}
function maybeStart(t: Table) {
  if (t.phase !== "wait" || t.startTimer) return;
  const n = t.seats.filter((s) => s && s.stack > 0 && !s.leaving).length;
  if (n < 2) return;
  t.startTimer = setTimeout(() => { t.startTimer = null; startHand(t); }, 3000);
}
function put(s: Seat, n: number) { n = Math.min(n, s.stack); s.stack -= n; s.bet += n; s.total += n; if (s.stack === 0) s.allin = true; return n; }
function startHand(t: Table) {
  const el = t.seats.map((s, i) => (s && s.stack > 0 && !s.leaving ? i : -1)).filter((i) => i >= 0);
  if (el.length < 2) { t.phase = "wait"; sendTable(t); return; }
  t.hand++; t.result = null; t.board = []; t.flop = false; t.deck = shuffled();
  for (const s of t.seats) if (s) { s.inHand = false; s.folded = false; s.allin = false; s.acted = false; s.bet = 0; s.total = 0; s.cards = []; s.last = ""; }
  for (const i of el) t.seats[i]!.inHand = true;
  t.btn = nextIdx(t, t.btn < 0 ? -1 : t.btn, (s) => s.inHand);
  const hu = el.length === 2;
  const sbI = hu ? t.btn : nextIdx(t, t.btn, (s) => s.inHand);
  const bbI = nextIdx(t, sbI, (s) => s.inHand);
  put(t.seats[sbI]!, t.sb); t.seats[sbI]!.last = "SB";
  put(t.seats[bbI]!, t.bb); t.seats[bbI]!.last = "BB";
  for (const i of el) t.seats[i]!.cards = [t.deck.pop()!, t.deck.pop()!];
  t.cur = Math.max(t.seats[sbI]!.bet, t.seats[bbI]!.bet); t.minRaise = t.bb;
  t.phase = "preflop";
  t.toAct = nextIdx(t, bbI, canActFn);
  // якщо ніхто не може діяти (all-in від блайндів) — одразу далі
  const can = t.seats.filter((s) => s && canActFn(s)) as Seat[];
  if (can.length === 0 || (can.length === 1 && can[0].bet >= t.cur)) { t.toAct = -1; sendTable(t); setTimeout(() => nextPhase(t, t.hand), 1200); return; }
  startTurn(t);
}
function startTurn(t: Table) {
  clearTimeout(t.timer);
  t.deadline = Date.now() + 25000; const h = t.hand, who = t.toAct;
  t.timer = setTimeout(() => onTimeout(t, h, who), 25500);
  sendTable(t);
}
async function onTimeout(t: Table, h: number, who: number) {
  if (t.hand !== h || t.toAct !== who || !inPlay(t)) return;
  const s = t.seats[who]; if (!s) return;
  s.timeouts++;
  if (s.timeouts >= 2) s.leaving = true;
  try { await act(t, who, t.cur - s.bet > 0 ? "fold" : "check", 0, true); } catch (e: any) { console.error("timeout", e.message); }
}
async function act(t: Table, i: number, a: string, to: number, force = false) {
  if (!inPlay(t)) throw new Error("not_turn");
  if (!force && t.toAct !== i) throw new Error("not_turn");
  const s = t.seats[i]; if (!s || !s.inHand || s.folded) throw new Error("not_turn");
  const toCall = t.cur - s.bet;
  const isTurn = t.toAct === i;
  if (a === "fold") { s.folded = true; s.last = "fold"; }
  else if (a === "check") { if (toCall > 0) throw new Error("bad_act"); s.last = "check"; }
  else if (a === "call") { if (toCall <= 0) { s.last = "check"; } else { const p = put(s, toCall); s.last = p < toCall || s.allin ? "all-in" : "call"; } }
  else if (a === "raise") {
    to = Math.floor(Number(to)); const maxTo = s.bet + s.stack; const minTo = t.cur + t.minRaise;
    if (!Number.isFinite(to)) throw new Error("bad_act");
    if (to >= maxTo) to = maxTo; else if (to < minTo) throw new Error("bad_raise");
    if (to <= t.cur && to < maxTo) throw new Error("bad_raise");
    const wasCur = t.cur; put(s, to - s.bet);
    if (s.bet > wasCur) {
      const by = s.bet - wasCur; if (by >= t.minRaise) t.minRaise = by;
      t.cur = s.bet;
      for (const o of t.seats) if (o && o !== s && canActFn(o)) o.acted = false;
      s.last = s.allin ? "all-in" : wasCur === 0 ? "bet " + s.bet : "raise " + s.bet;
    } else s.last = "all-in";
  } else throw new Error("bad_act");
  s.acted = true; if (a !== "fold") s.timeouts = 0;
  if (isTurn) clearTimeout(t.timer);
  await afterAction(t, i);
}
async function afterAction(t: Table, from: number) {
  const alive = t.seats.filter((s) => s && s.inHand && !s.folded) as Seat[];
  if (alive.length === 1) { await finishHand(t, true); return; }
  const can = alive.filter((s) => !s.allin);
  const done = can.length === 0 || (can.length === 1 && can[0].bet >= t.cur) || can.every((s) => s.acted && s.bet === t.cur);
  if (done) { clearTimeout(t.timer); t.toAct = -1; t.deadline = 0; sendTable(t); setTimeout(() => nextPhase(t, t.hand), 600); return; }
  if (from === t.toAct) { t.toAct = nextIdx(t, from, canActFn); startTurn(t); } else sendTable(t);
}
function nextPhase(t: Table, h: number) {
  if (t.hand !== h || !inPlay(t)) return;
  for (const s of t.seats) if (s) { s.bet = 0; if (canActFn(s)) { s.acted = false; s.last = s.last === "fold" ? "fold" : ""; } }
  t.cur = 0; t.minRaise = t.bb;
  if (t.phase === "river") { finishHand(t, false); return; }
  if (t.phase === "preflop") { t.board.push(t.deck.pop()!, t.deck.pop()!, t.deck.pop()!); t.phase = "flop"; t.flop = true; }
  else if (t.phase === "flop") { t.board.push(t.deck.pop()!); t.phase = "turn"; }
  else if (t.phase === "turn") { t.board.push(t.deck.pop()!); t.phase = "river"; }
  const can = t.seats.filter((s) => s && canActFn(s));
  if (can.length <= 1) { sendTable(t); setTimeout(() => nextPhase(t, h), 1300); return; }
  t.toAct = nextIdx(t, t.btn, canActFn);
  startTurn(t);
}
// побудова банків (основний + побічні)
function buildPots(t: Table) {
  const ps: { i: number; total: number; folded: boolean }[] = [];
  t.seats.forEach((s, i) => { if (s && s.total > 0) ps.push({ i, total: s.total, folded: s.folded }); });
  const levels = [...new Set(ps.map((p) => p.total))].sort((a, b) => a - b);
  const pots: { amt: number; elig: number[] }[] = []; let prev = 0;
  for (const L of levels) {
    let amt = 0; for (const p of ps) amt += Math.max(0, Math.min(p.total, L) - prev);
    const elig = ps.filter((p) => !p.folded && p.total >= L).map((p) => p.i);
    if (amt > 0) { if (elig.length) pots.push({ amt, elig }); else if (pots.length) pots[pots.length - 1].amt += amt; }
    prev = L;
  }
  return pots;
}
async function finishHand(t: Table, uncontested: boolean) {
  clearTimeout(t.timer); t.phase = "settling"; t.deadline = 0; t.toAct = -1;
  const total = potOf(t);
  let rake = t.flop ? Math.min(rakeOf(total), 3 * t.bb) : 0;
  const pays: Record<number, number> = {}; const shows: any[] = []; const winners: any[] = [];
  const alive = t.seats.map((s, i) => ({ s, i })).filter((x) => x.s && x.s.inHand && !x.s.folded) as { s: Seat; i: number }[];
  if (uncontested) {
    pays[alive[0].i] = total - rake;
  } else {
    // добираємо борд до 5 карт, якщо потрібно
    while (t.board.length < 5) t.board.push(t.deck.pop()!);
    const score: Record<number, number> = {};
    for (const x of alive) { score[x.i] = best7([...x.s.cards, ...t.board]); shows.push({ i: x.i, cards: x.s.cards, hand: HAND_NAMES[catOf(score[x.i])] }); }
    const pots = buildPots(t);
    if (pots.length) { const r = Math.min(rake, pots[0].amt); pots[0].amt -= r; rake = r; }
    for (const p of pots) {
      const best = Math.max(...p.elig.map((i) => score[i]));
      let ws = p.elig.filter((i) => score[i] === best);
      // порядок для залишку: від місця після баттона
      ws = ws.sort((a, b) => ((a - t.btn - 1 + t.n) % t.n) - ((b - t.btn - 1 + t.n) % t.n));
      const share = Math.floor(p.amt / ws.length); let rem = p.amt - share * ws.length;
      for (const i of ws) { pays[i] = (pays[i] || 0) + share + (rem > 0 ? 1 : 0); if (rem > 0) rem--; }
    }
  }
  for (const [i, amt] of Object.entries(pays)) { const s = t.seats[+i]!; s.stack += amt; winners.push({ i: +i, name: s.name, amount: amt - s.total, hand: shows.find((x) => x.i === +i)?.hand || null }); }
  t.result = { winners, shows, pot: total, rake, uncontested };
  try {
    await db.begin(async (tx: any) => {
      for (let i = 0; i < t.n; i++) { const s = t.seats[i]; if (!s || !s.inHand) continue; await holdSet(tx, "pk", String(t.id), s.uid, s.stack); await tx`update users set turnover = turnover + ${s.total} where id = ${s.uid}`; }
      if (rake > 0) { await adj(tx, "house", rake, "rake", null, "pk t" + t.id + " h" + t.hand); await refPay(tx, t.seats.filter((s) => s && s.inHand && s.total > 0).map((s) => [s!.uid, s!.total] as [string, number]), rake, "pk"); }
      await tx`insert into hands(table_id, pot, rake, info) values(${t.id}, ${total}, ${rake}, ${JSON.stringify({ winners, board: t.board })})`;
    });
  } catch (e: any) { console.error("finishHand", e.message); }
  t.phase = "result"; sendTable(t);
  setTimeout(() => afterHand(t), uncontested ? 3500 : 6500);
}
async function afterHand(t: Table) {
  for (let i = 0; i < t.n; i++) {
    const s = t.seats[i]; if (!s) continue;
    if (s.leaving || s.stack === 0) await cashOut(t, i);
  }
  for (const s of t.seats) if (s) { s.inHand = false; s.bet = 0; s.total = 0; s.folded = false; s.allin = false; s.cards = []; s.last = ""; }
  t.phase = "wait"; t.board = []; t.cur = 0; t.result = null; t.flop = false;
  sendTable(t); maybeStart(t);
}

// ---------- Лобі ----------
function lobbyView() {
  return {
    t: "lobby", tables: tables.map((t) => ({ id: t.id, sb: t.sb, bb: t.bb, min: t.min, max: t.max, players: t.seats.filter(Boolean).length, seats: t.n })),
    mx: [...chals.values()].map((c) => ({ id: c.id, uid: c.uid, name: c.name, stake: c.stake, game: c.game })), on: new Set([...socks].map((x: any) => x.data.uid)).size,
    duels: [...duels.values()].map((d) => ({ id: d.id, uid: d.uid, name: d.name, stake: d.stake, game: d.game })), jp: jpView(), rake: RAKE,
  };
}
let lobbyQueued = false;
function sendLobby() {
  if (lobbyQueued) return; lobbyQueued = true;
  setTimeout(() => { lobbyQueued = false; const v = lobbyView(); for (const w of socks) if (w.data.sub === "lobby") send(w, v); }, 60);
}

// ---------- PvP-ігри 1 на 1 ----------
// rps — камінь-ножиці-папір, ttt — хрестики-нулики, card — висока карта,
// four — «Неон-4» (чотири в ряд), mines — «Сапер-дуель», bj — «21-дуель», checkers — шашки, chess — шахи
type MxGame = "rps" | "ttt" | "card" | "four" | "mines" | "bj" | "checkers" | "chess";
type Chal = { id: number; uid: string; name: string; stake: number; game: MxGame };
type Match = { id: number; game: MxGame; stake: number; p: string[]; names: string[]; st: any; turn: number; deadline: number; timer: any; tk: number; over: boolean };
const chals = new Map<number, Chal>(), matches = new Map<number, Match>(); let mxSeq = Date.now(); // унікальні номери й після перезапуску (ref у журналі не повторюється)
const mxJoining = new Set<string>();
const MX_GAMES = ["rps", "ttt", "card", "four", "mines", "bj", "checkers", "chess"];
const MX_MOVE_MS: Record<string, number> = { rps: 20000, ttt: 25000, four: 25000, mines: 20000, bj: 30000, checkers: 60000, chess: 90000 };
const TTT_LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
const mxActive = (uid: string) => [...matches.values()].find((m) => !m.over && m.p.includes(uid));
const toMx = (uid: string, o: any) => { for (const w of socks) if (w.data.uid === uid) send(w, o); };
const mxLobbyView = () => ({ t: "mx.lobby", chals: [...chals.values()].map((c) => ({ id: c.id, uid: c.uid, name: c.name, stake: c.stake, game: c.game })) });
let mxQueued = false;
function mxLobby() {
  sendLobby();
  if (mxQueued) return; mxQueued = true;
  setTimeout(() => { mxQueued = false; const v = mxLobbyView(); for (const w of socks) if (w.data.sub === "mx") send(w, v); }, 60);
}

// --- Неон-4: поле 7×6, рядок 0 — верхній
const F_W = 7, F_H = 6;
function fourWin(b: number[], at: number) {
  const v = b[at], r0 = Math.floor(at / F_W), c0 = at % F_W;
  for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) {
    const line = [at];
    for (const s of [1, -1]) { let r = r0 + dr * s, c = c0 + dc * s; while (r >= 0 && r < F_H && c >= 0 && c < F_W && b[r * F_W + c] === v) { line.push(r * F_W + c); r += dr * s; c += dc * s; } }
    if (line.length >= 4) return line;
  }
  return null;
}

// --- Сапер-дуель: поле 6×6, 7 мін, перший хід завжди безпечний
const M_N = 6, M_BOMBS = 7;
const mNb = (i: number) => { const r = Math.floor(i / M_N), c = i % M_N, o: number[] = []; for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) { if (!dr && !dc) continue; const rr = r + dr, cc = c + dc; if (rr >= 0 && rr < M_N && cc >= 0 && cc < M_N) o.push(rr * M_N + cc); } return o; };
function minesNew() { const s = new Set<number>(); while (s.size < M_BOMBS) s.add(rnd(M_N * M_N)); return s; }

// --- 21-дуель
const bjVal = (c: number) => { const r = c % 13; return r === 12 ? 11 : r >= 8 ? 10 : r + 2; };
function bjTotal(h: number[]) { let t = 0, a = 0; for (const c of h) { t += bjVal(c); if (c % 13 === 12) a++; } while (t > 21 && a) { t -= 10; a--; } return t; }

// --- Шашки (російські правила): 0 — білі знизу (w/W), 1 — чорні зверху (b/B)
const own = (p: string, who: number) => !!p && (who === 0 ? p === "w" || p === "W" : p === "b" || p === "B");
const isKing = (p: string) => p === "W" || p === "B";
const rc = (i: number) => [Math.floor(i / 8), i % 8];
const onB = (r: number, c: number) => r >= 0 && r < 8 && c >= 0 && c < 8;
const DIRS = [[-1, -1], [-1, 1], [1, -1], [1, 1]];
function ckNew() { const b = Array(64).fill(""); for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) if ((r + c) % 2 === 1) { if (r < 3) b[r * 8 + c] = "b"; else if (r > 4) b[r * 8 + c] = "w"; } return b; }
function ckCaps(b: string[], at: number, who: number, done: number[]) {
  const out: { to: number; cap: number }[] = []; const p = b[at]; const [r, c] = rc(at);
  for (const [dr, dc] of DIRS) {
    if (isKing(p)) {
      let rr = r + dr, cc = c + dc;
      while (onB(rr, cc) && !b[rr * 8 + cc]) { rr += dr; cc += dc; }
      if (!onB(rr, cc)) continue; const mid = rr * 8 + cc;
      if (!b[mid] || own(b[mid], who) || done.includes(mid)) continue;
      rr += dr; cc += dc;
      while (onB(rr, cc) && !b[rr * 8 + cc]) { out.push({ to: rr * 8 + cc, cap: mid }); rr += dr; cc += dc; }
    } else {
      const mr = r + dr, mc = c + dc, lr = r + 2 * dr, lc = c + 2 * dc;
      if (!onB(lr, lc)) continue; const mid = mr * 8 + mc, land = lr * 8 + lc;
      if (b[mid] && !own(b[mid], who) && !done.includes(mid) && !b[land]) out.push({ to: land, cap: mid });
    }
  }
  return out;
}
function ckSteps(st: any, who: number) {
  const b = st.b; const steps: { from: number; to: number; cap: number }[] = [];
  if (st.chain) { for (const x of ckCaps(b, st.chain.at, who, st.chain.cap)) steps.push({ from: st.chain.at, ...x }); return steps; }
  for (let i = 0; i < 64; i++) if (own(b[i], who)) for (const x of ckCaps(b, i, who, [])) steps.push({ from: i, ...x });
  if (steps.length) return steps;
  const fwd = who === 0 ? -1 : 1;
  for (let i = 0; i < 64; i++) {
    if (!own(b[i], who)) continue; const [r, c] = rc(i);
    for (const [dr, dc] of DIRS) {
      if (isKing(b[i])) { let rr = r + dr, cc = c + dc; while (onB(rr, cc) && !b[rr * 8 + cc]) { steps.push({ from: i, to: rr * 8 + cc, cap: -1 }); rr += dr; cc += dc; } }
      else if (dr === fwd && onB(r + dr, c + dc) && !b[(r + dr) * 8 + c + dc]) steps.push({ from: i, to: (r + dr) * 8 + c + dc, cap: -1 });
    }
  }
  return steps;
}

// --- Шахи: chess.js приходить із завантажувача
const ChessLib = (globalThis as any).__Chess;
function chMoves(g: any) { return g.moves({ verbose: true }).map((m: any) => [m.from, m.to, m.promotion || ""]); }

function mxView(m: Match, uid: string) {
  const i = m.p.indexOf(uid); const s = m.st || {}; let st: any;
  if (m.game === "rps") st = { round: s.round, mine: s.ch[i], opp: m.over ? s.ch[1 - i] : s.ch[1 - i] !== null, hist: s.hist.map((h: any) => ({ me: h[i], opp: h[1 - i] })) };
  else if (m.game === "ttt") st = { b: s.b };
  else if (m.game === "card") st = { mine: s.cards[i], opp: s.cards[1 - i] };
  else if (m.game === "four") st = { b: s.b, last: s.last, line: s.line || null };
  else if (m.game === "mines") st = { n: M_N, open: s.open, bombs: m.over ? [...s.bombs] : null, boom: s.boom ?? -1, left: M_N * M_N - M_BOMBS - s.open.filter((x: any) => typeof x === "number" && x >= 0).length };
  else if (m.game === "bj") {
    const oh = s.hands[1 - i];
    st = { mine: s.hands[i], mineT: bjTotal(s.hands[i]), done: s.done[i], opp: m.over ? oh : [oh[0]], oppN: oh.length, oppT: m.over ? bjTotal(oh) : null, oppDone: s.done[1 - i] };
  } else if (m.game === "checkers") st = { b: s.b, chain: s.chain ? s.chain.at : -1, last: s.last, steps: !m.over && m.turn === i ? ckSteps(s, i).map((x) => [x.from, x.to]) : [] };
  else if (m.game === "chess") st = { fen: s.g.fen(), last: s.last, check: s.g.inCheck(), moves: !m.over && m.turn === i ? chMoves(s.g) : [], cap: s.cap };
  return { id: m.id, game: m.game, stake: m.stake, you: i, names: m.names, turn: m.turn, left: Math.max(0, m.deadline - Date.now()), st };
}
const mxSend = (m: Match) => { for (const u of m.p) toMx(u, { t: "mx.state", m: mxView(m, u) }); };
function mxArm(m: Match, ms?: number) {
  ms = ms || MX_MOVE_MS[m.game] || 25000;
  clearTimeout(m.timer); const tk = ++m.tk; m.deadline = Date.now() + ms;
  m.timer = setTimeout(() => mxTimeout(m.id, tk), ms + 300);
}
async function mxTimeout(id: number, tk: number) {
  const m = matches.get(id); if (!m || m.over || m.tk !== tk) return;
  if (m.game === "rps") { const lack = [0, 1].filter((i) => m.st.ch[i] === null); await mxFinish(m, lack.length === 2 ? -1 : 1 - lack[0], "timeout"); }
  else if (m.game === "bj") { m.st.done = [true, true]; await bjResolve(m); }
  else await mxFinish(m, 1 - m.turn, "timeout");
}
async function mxFinish(m: Match, w: number, reason: string) {
  if (m.over) return; m.over = true; clearTimeout(m.timer);
  const pot = m.stake * 2; const rake = w >= 0 ? rakeOf(pot) : 0; const win = pot - rake;
  try {
    await db.begin(async (t: any) => {
      if (w >= 0) { await adj(t, m.p[w], win, "mx_win", "mx" + m.id, m.game); await adj(t, "house", rake, "rake", null, "mx " + m.game + " " + m.id); await refPay(t, m.p.map((u) => [u, 1] as [string, number]), rake, "mx"); }
      else for (const u of m.p) await adj(t, u, m.stake, "mx_refund", "mx" + m.id + u, m.game);
      await holdDel(t, "mx", String(m.id));
    });
  } catch (e: any) { console.error("mxFinish", e.message); }
  const base = { t: "mx.end", winner: w >= 0 ? m.p[w] : null, reason, win, rake };
  for (const u of m.p) toMx(u, { ...base, m: mxView(m, u) });
  for (const u of m.p) pushBal(u);
  setTimeout(() => matches.delete(m.id), 60000);
}
async function bjResolve(m: Match) {
  const [a, b] = m.st.hands.map((h: number[]) => bjTotal(h));
  const ba = a > 21, bb = b > 21;
  let w = -1;
  if (ba && bb) w = -1; else if (ba) w = 1; else if (bb) w = 0; else if (a > b) w = 0; else if (b > a) w = 1;
  await mxFinish(m, w, w < 0 ? "draw" : "bj");
}
async function mxRefundChal(c: Chal) {
  if (!chals.delete(c.id)) return;
  try { await db.begin(async (t: any) => { await adj(t, c.uid, c.stake, "mx_refund_c", "c" + c.id, null); await holdDel(t, "mx", String(c.id), c.uid); await t`update users set turnover = turnover - ${c.stake} where id = ${c.uid}`; }); } catch (e: any) { console.error("mxRefund", e.message); }
  pushBal(c.uid); mxLobby();
}
async function mxLeave(uid: string) { for (const c of [...chals.values()]) if (c.uid === uid) await mxRefundChal(c); }
async function mxCreate(ws: any, m: any) {
  const { uid, name } = ws.data; const u = await getUserRow(uid); needAge(u);
  const stake = checkStake(m.stake); const game = String(m.game) as MxGame; if (!MX_GAMES.includes(game)) throw new Error("bad_game");
  if (game === "chess" && !ChessLib) throw new Error("bad_game");
  if (mxActive(uid) || mxJoining.has(uid)) throw new Error("in_match");
  if ([...chals.values()].some((c) => c.uid === uid)) throw new Error("too_many");
  const id = mxSeq++;
  await db.begin(async (t: any) => { await adj(t, uid, -stake, "mx_stake", null, game + " c" + id); await holdAdd(t, "mx", String(id), uid, stake); await t`update users set turnover = turnover + ${stake} where id = ${uid}`; });
  chals.set(id, { id, uid, name, stake, game });
  pushBal(uid); mxLobby();
}
async function mxJoin(ws: any, m: any) {
  const { uid, name } = ws.data; const u = await getUserRow(uid); needAge(u);
  if (mxActive(uid) || mxJoining.has(uid)) throw new Error("in_match");
  if ([...chals.values()].some((c) => c.uid === uid)) throw new Error("has_chal");
  const c = chals.get(Number(m.id)); if (!c) throw new Error("gone"); if (c.uid === uid) throw new Error("own");
  chals.delete(c.id); mxJoining.add(uid);
  try {
    await db.begin(async (t: any) => { await adj(t, uid, -c.stake, "mx_stake", null, c.game + " c" + c.id); await holdAdd(t, "mx", String(c.id), uid, c.stake); await t`update users set turnover = turnover + ${c.stake} where id = ${uid}`; });
  } catch (e) { chals.set(c.id, c); mxJoining.delete(uid); mxLobby(); throw e; }
  const mm: Match = { id: c.id, game: c.game, stake: c.stake, p: [c.uid, uid], names: [c.name, name], st: null, turn: 0, deadline: 0, timer: null, tk: 0, over: false };
  if (c.game !== "rps" && c.game !== "card" && c.game !== "bj" && rnd(2)) { mm.p = [uid, c.uid]; mm.names = [name, c.name]; }
  matches.set(mm.id, mm); mxJoining.delete(uid);
  pushBal(uid); mxLobby();
  if (c.game === "rps") mm.st = { ch: [null, null], round: 1, hist: [] };
  else if (c.game === "ttt") mm.st = { b: Array(9).fill("") };
  else if (c.game === "four") mm.st = { b: Array(F_W * F_H).fill(0), last: -1 };
  else if (c.game === "mines") mm.st = { bombs: minesNew(), open: Array(M_N * M_N).fill(-1), first: true, boom: -1 };
  else if (c.game === "bj") { const d = shuffled(); mm.st = { deck: d, hands: [[d.pop(), d.pop()], [d.pop(), d.pop()]], done: [false, false] }; for (const k of [0, 1]) if (bjTotal(mm.st.hands[k]) === 21) mm.st.done[k] = true; if (mm.st.done[0] && mm.st.done[1]) { await bjResolve(mm); return; } }
  else if (c.game === "checkers") mm.st = { b: ckNew(), chain: null, last: null, quiet: 0, plies: 0 };
  else if (c.game === "chess") mm.st = { g: new ChessLib(), last: null, cap: [] };
  else { let a = 0, b = 0; do { a = rnd(52); b = rnd(52); } while (a % 13 === b % 13); mm.st = { cards: [a, b] }; await mxFinish(mm, a % 13 > b % 13 ? 0 : 1, "cards"); return; }
  mxArm(mm); mxSend(mm);
}
async function mxMove(ws: any, m: any) {
  const mm = matches.get(Number(m.id)); const uid = ws.data.uid; if (!mm || mm.over) throw new Error("gone");
  const i = mm.p.indexOf(uid); if (i < 0) throw new Error("gone");
  if (m.resign) { if (mm.game === "rps" || mm.game === "card") throw new Error("bad_move"); await mxFinish(mm, 1 - i, "resign"); return; }
  const g = mm.game, s = mm.st;
  if (g === "rps") {
    const mv = String(m.move); if (!["r", "p", "s"].includes(mv)) throw new Error("bad_move"); if (s.ch[i] !== null) return;
    s.ch[i] = mv;
    if (s.ch[0] === null || s.ch[1] === null) { mxSend(mm); return; }
    const [x, y] = s.ch; const beats: any = { r: "s", s: "p", p: "r" };
    if (x === y) {
      s.hist.push([x, y]);
      if (s.round >= 5) { await mxFinish(mm, -1, "draw"); return; }
      s.round++; s.ch = [null, null]; mxArm(mm); mxSend(mm); return;
    }
    await mxFinish(mm, beats[x] === y ? 0 : 1, "rps"); return;
  }
  if (g === "bj") {
    if (s.done[i]) return;
    const a = String(m.act);
    if (a === "hit") { s.hands[i].push(s.deck.pop()); if (bjTotal(s.hands[i]) >= 21) s.done[i] = true; }
    else if (a === "stand") s.done[i] = true; else throw new Error("bad_move");
    if (s.done[0] && s.done[1]) { await bjResolve(mm); return; }
    mxArm(mm); mxSend(mm); return;
  }
  if (mm.turn !== i) throw new Error("not_turn");
  if (g === "ttt") {
    const c = Math.floor(Number(m.cell)); if (!(c >= 0 && c < 9) || s.b[c]) throw new Error("bad_move");
    s.b[c] = i === 0 ? "X" : "O";
    if (TTT_LINES.some((l) => l.every((k) => s.b[k] === s.b[c]))) { await mxFinish(mm, i, "ttt"); return; }
    if (s.b.every((x: string) => x)) { await mxFinish(mm, -1, "draw"); return; }
  } else if (g === "four") {
    const col = Math.floor(Number(m.col)); if (!(col >= 0 && col < F_W)) throw new Error("bad_move");
    let at = -1; for (let r = F_H - 1; r >= 0; r--) if (!s.b[r * F_W + col]) { at = r * F_W + col; break; }
    if (at < 0) throw new Error("bad_move");
    s.b[at] = i + 1; s.last = at;
    const line = fourWin(s.b, at); if (line) { s.line = line; await mxFinish(mm, i, "four"); return; }
    if (s.b.every((x: number) => x)) { await mxFinish(mm, -1, "draw"); return; }
  } else if (g === "mines") {
    const c = Math.floor(Number(m.cell)); if (!(c >= 0 && c < M_N * M_N) || s.open[c] !== -1) throw new Error("bad_move");
    if (s.first && s.bombs.has(c)) { s.bombs.delete(c); let k; do { k = rnd(M_N * M_N); } while (k === c || s.bombs.has(k)); s.bombs.add(k); }
    s.first = false;
    if (s.bombs.has(c)) { s.open[c] = "X"; s.boom = c; await mxFinish(mm, 1 - i, "boom"); return; }
    s.open[c] = mNb(c).filter((k) => s.bombs.has(k)).length;
    if (s.open.filter((x: any) => typeof x === "number" && x >= 0).length >= M_N * M_N - M_BOMBS) { await mxFinish(mm, -1, "draw"); return; }
  } else if (g === "checkers") {
    const from = Math.floor(Number(m.from)), to = Math.floor(Number(m.to));
    const st = ckSteps(s, i).find((x) => x.from === from && x.to === to); if (!st) throw new Error("bad_move");
    let p = s.b[from]; s.b[from] = ""; s.b[to] = p; s.last = [from, to];
    const lastRow = i === 0 ? 0 : 7; let promoted = false;
    if (!isKing(p) && Math.floor(to / 8) === lastRow) { p = i === 0 ? "W" : "B"; s.b[to] = p; promoted = true; }
    s.plies++;
    if (st.cap >= 0) {
      const cap = s.chain ? [...s.chain.cap, st.cap] : [st.cap];
      if (ckCaps(s.b, to, i, cap).length) { s.chain = { at: to, cap }; mxArm(mm); mxSend(mm); return; }
      for (const k of cap) s.b[k] = ""; s.chain = null; s.quiet = 0;
    } else s.quiet = promoted ? 0 : s.quiet + 1;
    const opp = 1 - i;
    if (!s.b.some((x: string) => own(x, opp)) || !ckSteps(s, opp).length) { await mxFinish(mm, i, "checkers"); return; }
    if (s.quiet >= 60 || s.plies >= 300) { await mxFinish(mm, -1, "draw"); return; }
  } else if (g === "chess") {
    const gm = s.g; let mv: any;
    try { mv = gm.move({ from: String(m.from), to: String(m.to), promotion: m.promo ? String(m.promo) : "q" }); } catch { mv = null; }
    if (!mv) throw new Error("bad_move");
    s.last = [mv.from, mv.to]; if (mv.captured) s.cap.push((mv.color === "w" ? "b" : "w") + mv.captured);
    if (gm.isCheckmate()) { await mxFinish(mm, i, "mate"); return; }
    if (gm.isDraw() || gm.isStalemate() || gm.isThreefoldRepetition() || gm.isInsufficientMaterial()) { await mxFinish(mm, -1, "draw"); return; }
  } else throw new Error("bad_move");
  mm.turn = 1 - i; mxArm(mm); mxSend(mm);
}

async function onMessage(ws: any, raw: string) {
  let m: any; try { m = JSON.parse(raw); } catch { return; }
  const d = ws.data; d.n = (d.n || 0) + 1; if (d.n > 40) return; // простий ліміт повідомлень
  try {
    switch (m.t) {
      case "sub": d.sub = String(m.sub || ""); if (d.sub === "lobby") send(ws, lobbyView()); else if (d.sub.startsWith("pk:")) { const t = tables.find((x) => "pk:" + x.id === d.sub); if (t) send(ws, { t: "pk", table: tableView(t, d.uid) }); } break;
      case "mx.sub": d.sub = "mx"; send(ws, mxLobbyView()); { const a = mxActive(d.uid); if (a) send(ws, { t: "mx.state", m: mxView(a, d.uid) }); } break;
      case "mx.create": if (CLOSED) throw new Error("closed"); await mxCreate(ws, m); break;
      case "mx.cancel": { const c = chals.get(Number(m.id)); if (c && c.uid === d.uid) await mxRefundChal(c); break; }
      case "mx.join": if (CLOSED) throw new Error("closed"); await mxJoin(ws, m); break;
      case "mx.sync": { const a = mxActive(d.uid); if (a) send(ws, { t: "mx.state", m: mxView(a, d.uid) }); break; }
      case "mx.move": await mxMove(ws, m); break;
      case "mx.leave": await mxLeave(d.uid); d.sub = ""; break;
      case "duel.create": if (CLOSED) throw new Error("closed"); await duelCreate(ws, m); break;
      case "duel.cancel": await duelCancel(ws, m); break;
      case "duel.join": if (CLOSED) throw new Error("closed"); await duelJoin(ws, m); break;
      case "jp.bet": if (CLOSED) throw new Error("closed"); await jpBet(ws, m); break;
      case "pk.sit": if (CLOSED) throw new Error("closed"); await pkSit(ws, m); break;
      case "pk.leave": await pkLeave(d.uid); break;
      case "pk.where": { const f = tableOf(d.uid); if (f) { d.sub = "pk:" + f.t.id; send(ws, { t: "pk", table: tableView(f.t, d.uid), back: true }); } break; }
      case "pk.act": { const f = tableOf(d.uid); if (!f) throw new Error("not_turn"); await act(f.t, f.i, String(m.a), m.to); break; }
    }
  } catch (e: any) { send(ws, { t: "err", e: e.message || "err" }); }
}

// ---------- Техпідтримка ----------
const SUP_IDS = String(env.SUPPORT_IDS || "1274614664,6364882131").split(",").map((s) => s.trim()).filter(Boolean);
const supEsc = (s: any) => String(s ?? "").replace(/[<>&]/g, (c) => (c === "<" ? "&lt;" : c === ">" ? "&gt;" : "&amp;"));
let supReady: any = null;
const supLang = new Map<string, string>();
function supInit() {
  return (supReady ||= (async () => {
    await db`create table if not exists sup_msg(id bigserial primary key, uid text not null, agent text, body text not null default '', media text, created timestamptz not null default now())`;
    await db`create index if not exists sup_msg_uid on sup_msg(uid, id)`;
    await db`create table if not exists sup_map(chat text not null, mid bigint not null, uid text not null, primary key(chat, mid))`;
    await db`create table if not exists sup_seen(uid text primary key, seen bigint not null default 0)`;
  })());
}
async function supToAgents(uid: string, name: string, text: string, src: string, copy?: any) {
  await supInit();
  const u = (await db`select balance, deposited, turnover from users where id = ${uid}`)[0];
  let head = "🆘 <b>Техпідтримка</b> · " + (src === "app" ? "з гри" : "з бота") + "\n👤 <b>" + supEsc(name) + "</b> · ID <code>" + supEsc(uid.replace(/^tg/, "")) + "</code>";
  if (u) head += "\n💰 Баланс " + N(u.balance) + " · поповнив " + N(u.deposited) + " · обіг " + N(u.turnover);
  const msg = head + "\n\n" + (text ? supEsc(text) : "<i>[вкладення нижче]</i>") + "\n\n↩️ <i>Відповідай реплаєм на це повідомлення</i>";
  for (const a of SUP_IDS) {
    try {
      const r = await tg("sendMessage", { chat_id: a, parse_mode: "HTML", text: msg });
      if (r?.result?.message_id) await db`insert into sup_map(chat, mid, uid) values(${a}, ${r.result.message_id}, ${uid}) on conflict do nothing`;
      if (copy) {
        const c = await tg("copyMessage", { chat_id: a, from_chat_id: copy.chat, message_id: copy.mid, reply_to_message_id: r?.result?.message_id });
        if (c?.result?.message_id) await db`insert into sup_map(chat, mid, uid) values(${a}, ${c.result.message_id}, ${uid}) on conflict do nothing`;
      }
    } catch (e: any) { console.error("sup send", e.message); }
  }
}
async function supBot(m: any) {
  if (!m || !m.chat || m.chat.type !== "private" || !m.from || m.successful_payment) return false;
  const fid = String(m.from.id), text = String(m.text || m.caption || "").slice(0, 3000);
  if (text === "/sponsor" && SUP_IDS.includes(fid)) { await spStats(m.chat.id); return true; }
  const fwd = m.forward_origin?.chat || m.forward_from_chat;
  if (fwd && fwd.type === "channel" && SUP_IDS.includes(fid)) { await spLoad(); await spSet(fwd.id, fwd.title); return true; }
  if (text.startsWith("/")) return false;
  await supInit();
  if (SUP_IDS.includes(fid)) {
    const rt = m.reply_to_message;
    const mp = rt ? (await db`select uid from sup_map where chat = ${fid} and mid = ${rt.message_id}`)[0] : null;
    if (!mp) { await tg("sendMessage", { chat_id: m.chat.id, text: "↩️ Щоб відповісти гравцю, натисни «Відповісти» (Reply) на його повідомлення від бота." }); return true; }
    const uid = mp.uid, agent = String(m.from.first_name || "Підтримка").slice(0, 24);
    await db`insert into sup_msg(uid, agent, body, media) values(${uid}, ${agent}, ${text}, ${m.text ? null : "file"})`;
    let ok = true;
    if (uid.startsWith("tg")) {
      const lc = (await db`select lang from users where id = ${uid}`)[0]?.lang || supLang.get(uid) || "";
      const hd = L3(lc, "💬 Техпідтримка Pixel Casino", "💬 Техподдержка Pixel Casino", "💬 Pixel Casino Support");
      const ft = L3(lc, "Відповісти можна прямо тут або в грі (кнопка 💬 угорі).", "Ответить можно прямо здесь или в игре (кнопка 💬 вверху).", "You can reply right here or in the game (💬 button at the top).");
      const r = m.text
        ? await tg("sendMessage", { chat_id: uid.slice(2), parse_mode: "HTML", text: "<b>" + hd + "</b>\n\n" + supEsc(text) + "\n\n<i>" + ft + "</i>" })
        : await tg("copyMessage", { chat_id: uid.slice(2), from_chat_id: m.chat.id, message_id: m.message_id, parse_mode: "HTML", caption: "<b>" + hd + "</b>" + (text ? "\n\n" + supEsc(text) : "") });
      ok = !!r?.ok;
    }
    toUser(uid, { t: "sup" });
    await tg("sendMessage", { chat_id: m.chat.id, reply_to_message_id: m.message_id, text: ok ? "✅ Надіслано гравцю" : "⚠️ Збережено в чаті гри, але в бот не дійшло (гравець, мабуть, заблокував бота)" });
    for (const a of SUP_IDS) if (a !== fid) tg("sendMessage", { chat_id: a, parse_mode: "HTML", text: "💬 <b>" + supEsc(agent) + "</b> відповів гравцю <code>" + supEsc(uid.replace(/^tg/, "")) + "</code>:\n" + (supEsc(text) || "[вкладення]") }).catch(() => {});
    return true;
  }
  if (m.from.language_code) supLang.set("tg" + fid, String(m.from.language_code));
  const uid = "tg" + fid, name = String(m.from.first_name || m.from.username || "Player").slice(0, 24) + (m.from.username ? " @" + m.from.username : "");
  const media = m.text ? null : m.photo ? "photo" : m.document ? "doc" : m.voice ? "voice" : m.video ? "video" : "file";
  if (!text && !media) return true;
  await db`insert into sup_msg(uid, body, media) values(${uid}, ${text}, ${media})`;
  await supToAgents(uid, name, text, "bot", media ? { chat: m.chat.id, mid: m.message_id } : null);
  const n = (await db`select count(*)::int as n from sup_msg where uid = ${uid} and agent is null and created > now() - interval '30 minutes'`)[0].n;
  if (n <= 1) await tg("sendMessage", { chat_id: m.chat.id, text: L3(m.from.language_code, "✅ Передали твоє повідомлення в техпідтримку. Відповімо тут найближчим часом.", "✅ Передали твоё сообщение в техподдержку. Ответим здесь в ближайшее время.", "✅ Your message was sent to support. We'll reply here soon.") });
  return true;
}
async function supApi(url: string, req: Request, u: any, body: any) {
  if (url === "/api/sponsor") { await spLoad(); return json({ link: SP_LINK, title: spTitle, state: req.method === "POST" ? await spCheck(u) : ((await db`select state from sp_users where uid = ${u.id}`)[0]?.state || "") }); }
  if (url !== "/api/sup") return null;
  await supInit();
  if (req.method === "POST") {
    const text = String(body.text || "").trim().slice(0, 1000);
    if (!text) return json({ error: "empty" }, 400);
    const last = (await db`select created from sup_msg where uid = ${u.id} and agent is null order by id desc limit 1`)[0];
    if (last && Date.now() - new Date(last.created).getTime() < 2000) return json({ error: "slow" }, 429);
    await db`insert into sup_msg(uid, body) values(${u.id}, ${text})`;
    supToAgents(u.id, u.name, text, "app").catch((e: any) => console.error("sup", e.message));
  }
  const rows = await db`select id, agent, body, media, created from sup_msg where uid = ${u.id} order by id desc limit 60`;
  const seen = N((await db`select seen from sup_seen where uid = ${u.id}`)[0]?.seen || 0);
  const list = rows.reverse().map((r: any) => ({ id: N(r.id), me: !r.agent, agent: r.agent ? String(env.SUPPORT_NAME || "Pixel Casino") : "", text: r.body, media: r.media || "", at: new Date(r.created).getTime() }));
  const top = list.length ? list[list.length - 1].id : 0;
  const unread = list.filter((x: any) => !x.me && x.id > seen).length;
  if (new URL(req.url).searchParams.get("read") === "1" && top > seen) await db`insert into sup_seen(uid, seen) values(${u.id}, ${top}) on conflict (uid) do update set seen = excluded.seen`;
  return json({ list, unread });
}
(globalThis as any).__supBot = supBot;

// ---------- Спонсорський канал (банер + перевірка заявки/підписки) ----------
const SP_LINK = String(env.SPONSOR_LINK || "https://t.me/+FbW8nv1oTW41OWZi");
let spChat = "", spTitle = "", spLoaded: any = null;
function spLoad() {
  return (spLoaded ||= (async () => {
    await supInit();
    await db`create table if not exists sp_users(uid text primary key, state text not null, at timestamptz not null default now())`;
    const r = await db`select name, body from parts where name in ('sp_chat', 'sp_title')`;
    for (const x of r) { if (x.name === "sp_chat") spChat = x.body; else spTitle = x.body; }
  })());
}
async function spSet(chat: any, title: any) {
  spChat = String(chat); spTitle = String(title || "");
  await db`insert into parts(name, body) values('sp_chat', ${spChat}) on conflict (name) do update set body = excluded.body`;
  await db`insert into parts(name, body) values('sp_title', ${spTitle}) on conflict (name) do update set body = excluded.body`;
  for (const a of SUP_IDS) tg("sendMessage", { chat_id: a, text: "📢 Канал спонсора підключено: «" + spTitle + "». Перевірка підписки працює. Статистика: /sponsor" }).catch(() => {});
}
async function spUpd(upd: any) {
  await spLoad();
  const mc = upd.my_chat_member;
  if (mc) {
    if (mc.chat && (mc.chat.type === "channel" || mc.chat.type === "supergroup") && mc.new_chat_member?.status === "administrator") {
      await spSet(mc.chat.id, mc.chat.title);
    }
    return;
  }
  const jr = upd.chat_join_request;
  if (jr && !spChat) await spSet(jr.chat.id, jr.chat.title);
  if (jr && spChat && String(jr.chat.id) === spChat) await db`insert into sp_users(uid, state) values(${"tg" + jr.from.id}, 'req') on conflict (uid) do nothing`;
}
async function spCheck(u: any) {
  await spLoad();
  const ex = (await db`select state from sp_users where uid = ${u.id}`)[0];
  if (ex?.state === "member") return "member";
  if (spChat && u.id.startsWith("tg")) {
    const r = await tg("getChatMember", { chat_id: spChat, user_id: Number(u.id.slice(2)) });
    const s = r?.result?.status;
    if (s === "member" || s === "administrator" || s === "creator" || s === "restricted") {
      await db`insert into sp_users(uid, state) values(${u.id}, 'member') on conflict (uid) do update set state = 'member', at = now()`;
      return "member";
    }
  }
  if (ex) return "req";
  return spChat ? "no" : "nobot";
}
async function spStats(chat: any) {
  await spLoad();
  const c = (await db`select count(*) filter (where state = 'member')::int as m, count(*) filter (where state = 'req')::int as q from sp_users`)[0];
  await tg("sendMessage", { chat_id: chat, text: "📢 Спонсор: " + (spTitle || "канал ще не підключено. Перешли сюди будь-який пост із каналу спонсора (бот має бути там адміном)") + "\n✅ Підписані через гру: " + c.m + "\n📝 Подали заявку: " + c.q });
}
(globalThis as any).__spUpd = spUpd;

// ---------- Реклама за винагороду (Adsgram, Monetag) ----------
// Гравець дивиться рекламу й отримує монети. Монети з реклами треба один раз прокрутити в іграх (додаються до «поповнено»),
// тож з них капає комісія, а ферми акаунтів не можуть просто вивести їх.
const ADS_REWARD = Number(env.ADS_REWARD || 1), ADS_DAY = Number(env.ADS_DAY || 50), ADS_GAP = Number(env.ADS_GAP || 25) * 1000;
const ADS_SECRET = String(env.ADS_SECRET || "");
const ADS_NETS: Record<string, string> = { adsgram: String(env.ADSGRAM_BLOCK || ""), monetag: String(env.MONETAG_ZONE || "") };
let adsReady: any = null;
function adsInit() {
  return (adsReady ||= (async () => {
    await db`create table if not exists ad_views(id bigserial primary key, uid text not null, net text not null, at timestamptz not null default now())`;
    await db`create index if not exists ad_views_uid on ad_views(uid, at)`;
  })());
}
async function adsCredit(uid: string, net: string) {
  if (CLOSED) return { error: "closed" };
  await adsInit();
  const u = await getUserRow(uid); if (!u || u.banned) return { error: "gone" };
  const c = await db`select count(*)::int as n, max(at) as last from ad_views where uid = ${uid} and at > now() - interval '1 day'`;
  if (N(c[0].n) >= ADS_DAY) return { error: "ads_limit" };
  if (c[0].last && Date.now() - new Date(c[0].last).getTime() < ADS_GAP) return { error: "ads_wait" };
  await db.begin(async (t: any) => {
    const r = await t`insert into ad_views(uid, net) values(${uid}, ${net}) returning id`;
    await adj(t, uid, ADS_REWARD, "ad_reward", "ad" + N(r[0].id), net);
    await t`update users set deposited = deposited + ${ADS_REWARD} where id = ${uid}`;
  });
  pushBal(uid);
  return { ok: true, reward: ADS_REWARD, left: Math.max(0, ADS_DAY - N(c[0].n) - 1) };
}
// серверні сповіщення від рекламних мереж: /api/ads/cb/adsgram?s=СЕКРЕТ&userid=[userId], /api/ads/cb/monetag?s=СЕКРЕТ&ymid={ymid}&reward_event_type={reward_event_type}
async function adsCb(url: string, req: Request) {
  if (!url.startsWith("/api/ads/cb/")) return null;
  const q = new URL(req.url).searchParams;
  if (!ADS_SECRET || q.get("s") !== ADS_SECRET) return json({ error: "forbidden" }, 403);
  const net = url.slice(12); if (!(net in ADS_NETS)) return json({ error: "bad_net" }, 400);
  const ev = q.get("reward_event_type"); if (ev && ev !== "valued") return json({ ok: true, skip: true });
  const raw = String(q.get("userid") || q.get("ymid") || "").replace(/\D/g, ""); if (!raw) return json({ error: "no_user" }, 400);
  return json(await adsCredit("tg" + raw, net));
}
async function adsApi(url: string, req: Request, u: any, body: any) {
  if (url === "/api/ads") {
    await adsInit();
    const c = await db`select count(*)::int as n from ad_views where uid = ${u.id} and at > now() - interval '1 day'`;
    return json({ offers: OFFERS.map((o) => ({ net: o.net, name: o.name || o.net, url: o.url.split("{uid}").join(String(u.id).replace(/^tg/, "")) })), nets: Object.entries(ADS_NETS).filter(([, v]) => v).map(([net, id]) => ({ net, id })), reward: ADS_REWARD, day: ADS_DAY, left: Math.max(0, ADS_DAY - N(c[0].n)), gap: ADS_GAP / 1000, server: !!ADS_SECRET, uid: String(u.id).replace(/^tg/, "") });
  }
  if (url === "/api/ads/claim" && req.method === "POST") {
    if (ADS_SECRET) return json({ ok: true, wait: true });
    const net = String(body.net || ""); if (!ADS_NETS[net]) return err("bad_net");
    const r: any = await adsCredit(u.id, net); return r.error ? err(r.error) : json(r);
  }
  return null;
}

// ---------- Офервол і опитування (BitLabs, CPX Research, AdGem, Lootably, Ayet, Torox …) ----------
// OFFERWALLS = [{"net":"bitlabs","name":"BitLabs","url":"https://...{uid}..."}] — посилання з кабінету мережі, де id гравця замінено на {uid}
// Мережа сама надсилає підтвердження: /api/ads/offer/<net>?s=СЕКРЕТ&uid=..&coins=..&tx=..&status=..
let OFFERS: { net: string; name: string; url: string }[] = [];
try { OFFERS = JSON.parse(String(env.OFFERWALLS || "[]")).filter((o: any) => o && o.net && o.url); } catch { OFFERS = []; }
async function offerCb(url: string, req: Request) {
  if (!url.startsWith("/api/ads/offer/")) return null;
  const q = new URL(req.url).searchParams;
  const sec = String(env.OFFER_SECRET || ADS_SECRET);
  if (!sec || q.get("s") !== sec) return new Response("forbidden", { status: 403 });
  const net = url.slice(15).replace(/[^\w-]/g, "");
  const uid = "tg" + String(q.get("uid") || "").replace(/\D/g, "");
  const coins = Math.round(Number(q.get("coins") || 0)); const tx = String(q.get("tx") || "").slice(0, 120);
  const st = String(q.get("status") || "1").toLowerCase();
  if (uid === "tg" || !tx || !Number.isFinite(coins) || coins === 0) return new Response("bad", { status: 400 });
  if (CLOSED) return new Response("1"); // проєкт закрито: монети більше не нараховуються
  if (!(await getUserRow(uid))) return new Response("1"); // невідомий гравець — підтверджуємо, щоб мережа не повторювала
  const back = q.get("back") === "1" || st === "2" || st === "reversed" || st === "rejected" || st === "chargeback" || coins < 0;
  const amt = back ? -Math.abs(coins) : Math.abs(coins);
  let fresh = false;
  try {
    await db.begin(async (t: any) => {
      const r = await adj(t, uid, amt, back ? "offer_back" : "offer", net + ":" + tx + (back ? ":b" : ""), net);
      if (r) fresh = true;
      if (r) await t`update users set deposited = greatest(0, deposited + ${amt}) where id = ${uid}`;
    });
  } catch (e: any) { console.error("offer", net, e.message); }
  pushBal(uid);
  if (fresh && !back) tg("sendMessage", { chat_id: uid.slice(2), text: `🎁 +${amt} монет за виконане завдання` }).catch(() => {});
  return new Response("1");
}

// ---------- Графіка (згенерована, лежить у таблиці assets) ----------
const assetCache = new Map<string, { mime: string; data: Uint8Array }>();
async function serveAsset(name: string) {
  name = name.replace(/\.(webp|png|jpg)$/, "").replace(/[^\w-]/g, "");
  let a = assetCache.get(name);
  if (!a) {
    try { const r = await db`select mime, data from assets where name = ${name}`; if (r.length) { a = { mime: r[0].mime, data: r[0].data }; assetCache.set(name, a); } } catch {}
  }
  if (!a) return new Response("not found", { status: 404 });
  return new Response(a.data, { headers: { "content-type": a.mime, "cache-control": "public, max-age=604800" } });
}
// ---------- Маршрути ----------
const ADMIN_HTML = "<!doctype html><html lang=\"uk\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover\"><meta name=\"theme-color\" content=\"#07041a\"><title>Адмін-панель</title><script src=\"https://telegram.org/js/telegram-web-app.js\"></script><link href=\"https://fonts.googleapis.com/css2?family=Exo+2:wght@500;700&family=Russo+One&display=swap\" rel=\"stylesheet\"><style>\n:root{--mag:#ff2fb0;--cy:#27e6ff;--gold:#ffc933;--grn:#3dff7a;--red:#ff4a5e;--dim:#9b8cd0;--line:#4a2ab0}\n*{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent}\nbody{background:#07041a;background-image:radial-gradient(circle at 50% -10%,#2a0f66 0,transparent 55%);color:#f4eeff;font:500 14px/1.35 'Exo 2',system-ui,sans-serif;min-height:100vh;display:flex;justify-content:center}\nh1,h2,h3,b,button{font-family:'Russo One',Impact,'Arial Black',sans-serif;font-weight:400}\n#app{width:100%;max-width:480px;padding:0 12px 40px}\nheader{display:flex;align-items:center;gap:10px;padding:12px 0;position:sticky;top:0;z-index:5;background:linear-gradient(#07041a 80%,transparent)}\nheader h1{flex:1;font-size:20px;text-shadow:0 0 10px var(--mag)}\n.ib{width:38px;height:38px;border-radius:12px;border:2px solid var(--line);background:#140a38;color:var(--cy);font-size:18px}\n.tabs{display:grid;grid-template-columns:repeat(4,1fr);gap:5px;margin-bottom:12px}\n.tabs button{padding:11px 2px;border-radius:12px;border:2px solid #3a2a90;background:#0d0630;color:var(--dim);font-size:12px}\n.tabs button.on{background:linear-gradient(#2a1470,#1a0b4d);border-color:#7a2cff;color:#fff;box-shadow:0 0 10px #7a2cff66}\n.sec{display:none}.sec.on{display:block}\n.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}\n.tile{border:2px solid var(--line);border-radius:14px;background:linear-gradient(#150b3c,#0d0629);padding:12px}\n.tile small{display:block;color:var(--dim);font-size:11px}\n.tile b{display:block;font-size:20px;margin-top:2px;color:var(--gold);text-shadow:0 0 10px #ff9a1f66}\n.tile.wide{grid-column:span 2}.tile.wide b{font-size:28px}\n.card{border:2px solid var(--line);border-radius:14px;background:linear-gradient(#150b3c,#0d0629);padding:12px;margin-bottom:10px}\n.row{display:flex;align-items:center;justify-content:space-between;gap:8px}\n.card b.nm{font-size:15px}.dim{color:var(--dim)}.sm{font-size:12px}\n.badge{font:400 11px 'Russo One',sans-serif;border-radius:8px;padding:2px 8px;border:2px solid}\n.st-pending{color:#ffd93d;border-color:#ffd93d}.st-paid{color:#5dff8a;border-color:#5dff8a}.st-rejected{color:#ff7b8a;border-color:#ff7b8a}.st-banned{color:#ff7b8a;border-color:#ff7b8a}.st-claimed{color:#ffd93d;border-color:#ffd93d}.st-done{color:#5dff8a;border-color:#5dff8a}.st-new{color:#9fb0ff;border-color:#9fb0ff}\n.big{font:400 22px 'Russo One',sans-serif;color:var(--gold)}\n.btns{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px}\n.btn{border:0;border-radius:12px;padding:12px 8px;color:#fff;font-size:14px;text-shadow:0 2px 0 rgba(0,0,0,.35)}\n.btn:active{transform:translateY(2px)}\n.go{background:linear-gradient(#7dff4d,#25c21e);box-shadow:0 3px 0 #0f7a12}\n.red{background:linear-gradient(#ff6a7a,#c4162e);box-shadow:0 3px 0 #6a0a18}\n.navy{background:linear-gradient(#1d2f7a,#12205a);box-shadow:0 3px 0 #0a1238;border:2px solid #3d63ff;color:#cfe0ff;font-size:13px}\n.pur{background:linear-gradient(#b05cff,#6a22d8);box-shadow:0 3px 0 #3a0f8a}\n.inp{width:100%;background:#0d0630;border:2px solid #3a2a90;border-radius:12px;padding:12px;color:#cfe0ff;font:500 14px 'Exo 2',sans-serif;margin-bottom:10px}\n.addr{word-break:break-all;font-size:12px;color:#cfe0ff;background:#0d0630;border-radius:8px;padding:6px 8px;margin-top:8px}\n.empty{text-align:center;color:var(--dim);padding:24px 0}\n#modal{position:fixed;inset:0;background:rgba(5,2,18,.8);display:none;align-items:center;justify-content:center;z-index:20;padding:20px}\n#modal.on{display:flex}\n#mbox{width:100%;max-width:380px;border:3px solid var(--mag);border-radius:20px;background:linear-gradient(#1a0b4d,#0d0629);padding:18px}\n#toast{position:fixed;left:50%;top:64px;transform:translateX(-50%);z-index:30;background:#1a0b4d;border:2px solid var(--gold);border-radius:14px;padding:10px 16px;font:400 13px 'Russo One',sans-serif;display:none}\n#toast.bad{border-color:var(--red)}\n</style></head><body><div id=\"app\">\n<header><button class=\"ib\" id=\"back\">◀</button><h1>Адмін-панель</h1><button class=\"ib\" id=\"rf\">⟳</button></header>\n<div class=\"tabs\" id=\"tabs\"><button class=\"on\" data-t=\"stats\">Огляд</button><button data-t=\"wd\" id=\"tabWd\">Виводи</button><button data-t=\"cd\" id=\"tabCd\">Картка</button><button data-t=\"us\">Гравці</button></div>\n\n<section class=\"sec on\" id=\"s-stats\"><div class=\"grid\">\n<div class=\"tile wide\"><small>Дохід казино (рейк + комісії)</small><b id=\"stHouse\">…</b><small id=\"stHouseTon\"></small></div>\n<div class=\"tile\"><small>За останні 24 год</small><b id=\"stDay\">…</b></div>\n<div class=\"tile\"><small>Гравців</small><b id=\"stUsers\">…</b></div>\n<div class=\"tile\"><small>Зараз онлайн</small><b id=\"stOnline\">…</b></div>\n<div class=\"tile\"><small>Монет у іграх</small><b id=\"stHeld\">…</b></div>\n<div class=\"tile\"><small>Всього поповнено</small><b id=\"stDep\">…</b></div>\n<div class=\"tile\"><small>Виплачено TON</small><b id=\"stPaid\">…</b></div>\n<div class=\"tile wide\"><small>Виводів чекає</small><b id=\"stPend\">…</b></div>\n<div class=\"tile wide\"><small>Гаманець автовиплат</small><b id=\"payBal\" style=\"font-size:20px\">не підключено</b><small id=\"payAddr\" style=\"word-break:break-all;margin-top:4px\"></small><small id=\"payInfo\" style=\"margin-top:4px\"></small><div class=\"btns\" id=\"payBtns\" style=\"display:none\"><button class=\"btn navy\" id=\"payCopy\">Копіювати адресу</button><button class=\"btn go\" id=\"payAuto\">…</button></div></div>\n</div></section>\n\n<section class=\"sec\" id=\"s-wd\"><div id=\"wdList\"></div></section>\n\n<section class=\"sec\" id=\"s-cd\"><div id=\"cdList\"></div></section>\n\n<section class=\"sec\" id=\"s-us\"><input class=\"inp\" id=\"q\" placeholder=\"Пошук: ім'я або Telegram ID\" autocomplete=\"off\"><div id=\"usList\"></div></section>\n</div>\n<div id=\"modal\"><div id=\"mbox\"></div></div><div id=\"toast\"></div>\n<script>\nvar T=window.Telegram&&Telegram.WebApp;\nif(T){try{T.ready();T.expand();T.setHeaderColor(\"#07041a\");T.setBackgroundColor(\"#07041a\");T.BackButton.show();T.BackButton.onClick(function(){location.href=\"/\"})}catch(e){}}\nvar $=function(i){return document.getElementById(i)};\nfunction esc(s){return String(s).replace(/[<>&\"]/g,function(c){return {\"<\":\"&lt;\",\">\":\"&gt;\",\"&\":\"&amp;\",'\"':\"&quot;\"}[c]})}\nfunction fmt(n){return Math.round(Number(n)||0).toLocaleString(\"uk-UA\")}\nfunction toast(t,bad){var e=$(\"toast\");e.textContent=t;e.className=bad?\"bad\":\"\";e.style.display=\"block\";clearTimeout(toast.h);toast.h=setTimeout(function(){e.style.display=\"none\"},2500)}\nasync function api(p,b){var r=await fetch(p,{method:b?\"POST\":\"GET\",headers:{\"Content-Type\":\"application/json\",\"x-init-data\":(T&&T.initData)||\"\",\"x-dev-user\":localStorage.getItem(\"dev\")||\"admin\"},body:b?JSON.stringify(b):undefined});var j={};try{j=await r.json()}catch(e){}if(!r.ok)throw new Error(j.error||\"err\");return j}\nfunction fail(e){toast(e&&e.message===\"forbidden\"?\"Немає доступу\":e&&e.message===\"unauthorized\"?\"Відкрий через бота\":\"Помилка: \"+(e&&e.message||\"?\"),true)}\nfunction ask(t,cb){if(T&&T.showConfirm)T.showConfirm(t,function(ok){if(ok)cb()});else if(confirm(t))cb()}\nfunction modal(h){$(\"mbox\").innerHTML=h;$(\"modal\").className=\"on\"}\nfunction closeModal(){$(\"modal\").className=\"\"}\n$(\"modal\").onclick=function(e){if(e.target.id===\"modal\")closeModal()};\n$(\"back\").onclick=function(){location.href=\"/\"};\n\nvar tab=\"stats\";\nfunction setTab(t){tab=t;document.querySelectorAll(\"#tabs button\").forEach(function(b){b.classList.toggle(\"on\",b.dataset.t===t)});document.querySelectorAll(\".sec\").forEach(function(s){s.classList.toggle(\"on\",s.id===\"s-\"+t)});load()}\n$(\"tabs\").onclick=function(e){var b=e.target.closest(\"[data-t]\");if(b)setTab(b.dataset.t)};\n$(\"rf\").onclick=function(){load();toast(\"Оновлено\")};\nfunction load(){if(tab===\"stats\")loadStats();else if(tab===\"wd\")loadWd();else if(tab===\"cd\")loadCd();else loadUs()}\n\nasync function loadStats(){try{var s=await api(\"/api/admin/stats\");\n$(\"stHouse\").textContent=fmt(s.house)+\" монет\";$(\"stHouseTon\").textContent=\"≈ \"+(s.house/s.tonCoins).toFixed(2)+\" TON\";\n$(\"stDay\").textContent=fmt(s.day);$(\"stUsers\").textContent=fmt(s.users);$(\"stOnline\").textContent=fmt(s.online);$(\"stHeld\").textContent=fmt(s.held);\n$(\"stDep\").textContent=fmt(s.deposited);$(\"stPaid\").textContent=Number(s.paidTon).toFixed(2);\n$(\"stPend\").textContent=s.pendingWd+\" (\"+fmt(s.pendingCoins)+\" монет)\";\n$(\"tabWd\").textContent=s.pendingWd?\"Виводи (\"+s.pendingWd+\")\":\"Виводи\";$(\"tabCd\").textContent=s.pendingCard?\"Картка (\"+s.pendingCard+\")\":\"Картка\";\nvar p=s.payer;PAYER=!!p;\nif(p){$(\"payBal\").textContent=p.balance===null?\"баланс недоступний\":Number(p.balance).toFixed(3)+\" TON\";$(\"payAddr\").textContent=p.addr;\n$(\"payInfo\").textContent=\"Авто до \"+p.max+\" TON за раз, до \"+p.day+\" TON за добу. Поповнюй цей гаманець звичайним переказом TON.\";\n$(\"payBtns\").style.display=\"grid\";$(\"payAuto\").textContent=p.autoOn?\"Авто: УВІМКНЕНО\":\"Авто: вимкнено\";$(\"payAuto\").className=\"btn \"+(p.autoOn?\"go\":\"red\");PAYADDR=p.addr;AUTOON=p.autoOn}\nelse{$(\"payBal\").textContent=\"не підключено\";$(\"payAddr\").textContent=\"\";$(\"payInfo\").textContent=\"Додай змінну WALLET_MNEMONIC на Railway, щоб виплачати в один дотик або автоматично.\";$(\"payBtns\").style.display=\"none\"}}catch(e){fail(e)}}\nvar PAYER=false,PAYADDR=\"\",AUTOON=false;\n$(\"payCopy\").onclick=function(){if(navigator.clipboard)navigator.clipboard.writeText(PAYADDR).then(function(){toast(\"Адресу скопійовано\")},function(){toast(PAYADDR)});else toast(PAYADDR)};\n$(\"payAuto\").onclick=function(){ask(AUTOON?\"Вимкнути автовиплату? Усі виводи йтимуть на ручне підтвердження.\":\"Увімкнути автовиплату? Виводи до ліміту будуть виплачуватись самі з гаманця автовиплат.\",function(){api(\"/api/admin/auto\",{on:!AUTOON}).then(function(){toast(AUTOON?\"Автовиплату вимкнено\":\"Автовиплату увімкнено\");loadStats()}).catch(fail)})};\n\nvar ST={pending:\"очікує\",paid:\"виплачено\",rejected:\"відхилено\",sending:\"відправляється\",review:\"перевір вручну\"};\nfunction tonUrl(addr,ton,id){return \"ton://transfer/\"+addr+\"?amount=\"+Math.round(Number(ton)*1e9)+\"&text=Pixel%20Casino%20%23\"+id}\nasync function loadWd(){try{var r=await api(\"/api/admin/withdrawals\");var el=$(\"wdList\");\nvar np=r.items.filter(function(x){return x.status===\"pending\"||x.status===\"review\"}).length;$(\"tabWd\").textContent=np?\"Виводи (\"+np+\")\":\"Виводи\";\nif(!r.items.length){el.innerHTML='<div class=\"empty\">Виводів ще не було</div>';return}\nel.innerHTML=r.items.map(function(w){\nvar h='<div class=\"card\"><div class=\"row\"><b class=\"nm\">#'+w.id+' · '+esc(w.name||w.user)+'</b><span class=\"badge st-'+w.status+'\">'+(ST[w.status]||w.status)+'</span></div>';\nh+='<div class=\"row\" style=\"margin-top:6px\"><span class=\"big\">'+Number(w.ton)+' TON</span><span class=\"dim sm\">комісія '+fmt(w.fee)+' · '+new Date(w.created).toLocaleString(\"uk-UA\",{day:\"2-digit\",month:\"2-digit\",hour:\"2-digit\",minute:\"2-digit\"})+'</span></div>';\nh+='<div class=\"addr\">'+esc(w.addr)+'</div>';\nif(w.status===\"review\")h+='<div class=\"dim sm\" style=\"margin-top:8px\">Автовиплата не підтвердилась. Перевір історію гаманця автовиплат: якщо TON пішов, тисни «Виплачено», якщо ні, «Відхилити».</div>';\nif(w.status===\"pending\"||w.status===\"review\"){\nvar d='data-addr=\"'+esc(w.addr)+'\" data-ton=\"'+Number(w.ton)+'\" data-id=\"'+w.id+'\"';\nh+='<div class=\"btns\">';\nif(PAYER&&w.status===\"pending\")h+='<button class=\"btn go\" style=\"grid-column:span 2\" data-a=\"auto\" '+d+'>⚡ Виплатити автоматично</button>';\nh+='<button class=\"btn pur\" data-a=\"pay\" '+d+'>Tonkeeper</button><button class=\"btn pur\" data-a=\"ton\" '+d+'>Інший гаманець</button>';\nh+='<button class=\"btn navy\" data-a=\"wallet\" '+d+'>@wallet</button><button class=\"btn navy\" data-a=\"copy\" '+d+'>Копіювати адресу</button>';\nh+='<button class=\"btn go\" data-a=\"ok\" '+d+'>✅ Виплачено</button><button class=\"btn red\" data-a=\"no\" '+d+'>❌ Відхилити</button></div>'}\nreturn h+'</div>'}).join(\"\")}catch(e){fail(e)}}\n\n$(\"wdList\").onclick=function(e){var b=e.target.closest(\"[data-a]\");if(!b)return;var a=b.dataset.a;\nif(a===\"copy\"){if(navigator.clipboard)navigator.clipboard.writeText(b.dataset.addr).then(function(){toast(\"Адресу скопійовано\")},function(){toast(b.dataset.addr)});else toast(b.dataset.addr)}\nelse if(a===\"pay\"){var u=\"https://app.tonkeeper.com/transfer/\"+b.dataset.addr+\"?amount=\"+Math.round(Number(b.dataset.ton)*1e9)+\"&text=Pixel%20Casino%20%23\"+b.dataset.id;if(T&&T.openLink)T.openLink(u);else window.open(u,\"_blank\")}\nelse if(a===\"ton\"){location.href=tonUrl(b.dataset.addr,b.dataset.ton,b.dataset.id)}\nelse if(a===\"wallet\"){if(navigator.clipboard)navigator.clipboard.writeText(b.dataset.addr);toast(\"Адресу скопійовано. Надішли \"+b.dataset.ton+\" TON\");setTimeout(function(){if(T&&T.openTelegramLink)T.openTelegramLink(\"https://t.me/wallet\");else window.open(\"https://t.me/wallet\",\"_blank\")},900)}\nelse if(a===\"auto\")ask(\"Виплатити \"+b.dataset.ton+\" TON автоматично з гаманця автовиплат?\",function(){autoPay(b.dataset.id)})\nelse if(a===\"ok\")ask(\"Ти вже відправив \"+b.dataset.ton+\" TON? Гравець отримає повідомлення про виплату.\",function(){decide(b.dataset.id,\"ok\")});\nelse if(a===\"no\")ask(\"Відхилити вивід? Монети повернуться гравцю.\",function(){decide(b.dataset.id,\"no\")})};\nvar ERR={no_wallet:\"Гаманець автовиплат не підключено\",low_balance:\"На гаманці автовиплат не вистачає TON\",gone:\"Вже оброблено\",manual_addr:\"Цю адресу виплачуй вручну\",net:\"Мережа TON не відповіла, спробуй ще раз\",review:\"Не підтвердилось: перевір вручну\"};\nasync function autoPay(id){toast(\"Відправляю…\");try{var r=await api(\"/api/admin/wd\",{id:Number(id),action:\"auto\"});toast(r.done?\"Виплачено автоматично\":(ERR[r.error]||\"Не вийшло\"),!r.done);loadWd()}catch(e){fail(e)}}\nasync function decide(id,a){try{var r=await api(\"/api/admin/wd\",{id:Number(id),action:a});toast(r.done?(a===\"ok\"?\"Позначено як виплачено\":\"Відхилено, монети повернено\"):\"Вже оброблено\",!r.done);loadWd()}catch(e){fail(e)}}\n\nvar CSYM={USD:\"$\",EUR:\"€\"},DSTAT={new:\"чекає оплати\",claimed:\"гравець оплатив\",done:\"зараховано\",rejected:\"відхилено\"},WSTAT={pending:\"до виплати\",paid:\"виплачено\",rejected:\"відхилено\"};\nfunction dt(x){return new Date(x).toLocaleString(\"uk-UA\",{day:\"2-digit\",month:\"2-digit\",hour:\"2-digit\",minute:\"2-digit\"})}\nasync function loadCd(){try{var r=await api(\"/api/admin/card\");var el=$(\"cdList\");\nvar np=r.deps.filter(function(x){return x.status===\"claimed\"}).length+r.wds.filter(function(x){return x.status===\"pending\"}).length;$(\"tabCd\").textContent=np?\"Картка (\"+np+\")\":\"Картка\";\nvar h=\"\";\nr.wds.forEach(function(w){\nh+='<div class=\"card\"><div class=\"row\"><b class=\"nm\">Вивід #'+w.id+' · '+esc(w.name||w.user)+'</b><span class=\"badge st-'+w.status+'\">'+(WSTAT[w.status]||w.status)+'</span></div>';\nh+='<div class=\"row\" style=\"margin-top:6px\"><span class=\"big\">'+Number(w.amount).toFixed(2)+' '+esc(w.cur)+'</span><span class=\"dim sm\">'+fmt(w.coins)+' монет, комісія '+fmt(w.fee)+' · '+dt(w.created)+'</span></div>';\nh+='<div class=\"addr\">'+esc(w.card)+'</div>';\nif(w.status===\"pending\")h+='<div class=\"btns\"><button class=\"btn navy\" style=\"grid-column:span 2\" data-k=\"wd\" data-a=\"copy\" data-v=\"'+esc(w.card)+'\">Копіювати номер картки</button><button class=\"btn go\" data-k=\"wd\" data-a=\"ok\" data-id=\"'+w.id+'\" data-t=\"'+Number(w.amount).toFixed(2)+' '+esc(w.cur)+'\">✅ Виплачено</button><button class=\"btn red\" data-k=\"wd\" data-a=\"no\" data-id=\"'+w.id+'\">❌ Відхилити</button></div>';\nh+='</div>'});\nr.deps.forEach(function(d){\nh+='<div class=\"card\"><div class=\"row\"><b class=\"nm\">Поповнення #'+d.id+' · '+esc(d.name||d.user)+'</b><span class=\"badge st-'+d.status+'\">'+(DSTAT[d.status]||d.status)+'</span></div>';\nh+='<div class=\"row\" style=\"margin-top:6px\"><span class=\"big\">'+Number(d.amount).toFixed(2)+' '+esc(d.cur)+'</span><span class=\"dim sm\">'+fmt(d.coins)+' монет · '+dt(d.created)+'</span></div>';\nh+='<div class=\"dim sm\" style=\"margin-top:6px\">Коментар до переказу: C'+d.id+'</div>';\nif(d.status===\"new\"||d.status===\"claimed\")h+='<div class=\"btns\"><button class=\"btn go\" data-k=\"dep\" data-a=\"ok\" data-id=\"'+d.id+'\" data-t=\"'+Number(d.amount).toFixed(2)+' '+esc(d.cur)+'\">✅ Зарахувати</button><button class=\"btn red\" data-k=\"dep\" data-a=\"no\" data-id=\"'+d.id+'\">❌ Відхилити</button></div>';\nh+='</div>'});\nel.innerHTML=h||'<div class=\"empty\">Операцій по картці ще не було</div>'}catch(e){fail(e)}}\n$(\"cdList\").onclick=function(e){var b=e.target.closest(\"[data-a]\");if(!b)return;var a=b.dataset.a,k=b.dataset.k;\nif(a===\"copy\"){if(navigator.clipboard)navigator.clipboard.writeText(b.dataset.v).then(function(){toast(\"Номер скопійовано\")},function(){toast(b.dataset.v)});else toast(b.dataset.v)}\nelse if(a===\"ok\")ask(k===\"dep\"?\"Гроші \"+b.dataset.t+\" вже на картці? Гравцю нарахуються монети.\":\"Ти вже відправив \"+b.dataset.t+\" на картку гравця?\",function(){cdDecide(k,b.dataset.id,\"ok\")});\nelse if(a===\"no\")ask(k===\"dep\"?\"Відхилити поповнення?\":\"Відхилити вивід? Монети повернуться гравцю.\",function(){cdDecide(k,b.dataset.id,\"no\")})};\nasync function cdDecide(k,id,a){try{var r=await api(\"/api/admin/card\",{kind:k,id:Number(id),action:a});toast(r.done?(a===\"ok\"?\"Готово\":\"Відхилено\"):\"Вже оброблено\",!r.done);loadCd()}catch(e){fail(e)}}\n\nvar qt;$(\"q\").oninput=function(){clearTimeout(qt);qt=setTimeout(loadUs,300)};\nasync function loadUs(){try{var r=await api(\"/api/admin/users?q=\"+encodeURIComponent($(\"q\").value.trim()));var el=$(\"usList\");\nif(!r.items.length){el.innerHTML='<div class=\"empty\">Нікого не знайдено</div>';return}\nel.innerHTML=r.items.map(function(u){\nreturn '<div class=\"card\"><div class=\"row\"><b class=\"nm\">'+esc(u.name)+(u.supporter?' 💜':'')+'</b>'+(u.banned?'<span class=\"badge st-banned\">бан</span>':'')+'</div>'\n+'<div class=\"dim sm\">'+esc(u.id)+'</div>'\n+'<div class=\"row\" style=\"margin-top:6px\"><span class=\"big\">'+fmt(u.balance)+'</span><span class=\"dim sm\">поповнив '+fmt(u.deposited)+' · зіграв '+fmt(u.turnover)+'</span></div>'\n+'<div class=\"btns\"><button class=\"btn go\" data-a=\"grant\" data-id=\"'+esc(u.id)+'\" data-nm=\"'+esc(u.name)+'\">Монети</button><button class=\"btn '+(u.banned?'navy':'red')+'\" data-a=\"ban\" data-id=\"'+esc(u.id)+'\" data-b=\"'+(u.banned?0:1)+'\">'+(u.banned?'Розбанити':'Забанити')+'</button></div></div>'}).join(\"\")}catch(e){fail(e)}}\n\n$(\"usList\").onclick=function(e){var b=e.target.closest(\"[data-a]\");if(!b)return;\nif(b.dataset.a===\"grant\"){var id=b.dataset.id;\nmodal('<h3 style=\"margin-bottom:6px\">Монети: '+b.dataset.nm+'</h3><p class=\"dim sm\" style=\"margin-bottom:10px\">Введи суму і обери дію.</p><input class=\"inp\" id=\"gAmt\" type=\"number\" inputmode=\"numeric\" placeholder=\"1000\"><div class=\"btns\" style=\"margin-top:0\"><button class=\"btn go\" id=\"gPlus\">＋ Нарахувати</button><button class=\"btn red\" id=\"gMinus\">－ Зняти</button></div><button class=\"btn navy\" id=\"gNo\" style=\"width:100%;margin-top:8px\">Скасувати</button>');\n$(\"gNo\").onclick=closeModal;\n$(\"gPlus\").onclick=function(){grant(id,1)};$(\"gMinus\").onclick=function(){grant(id,-1)}}\nelse if(b.dataset.a===\"ban\"){var on=b.dataset.b===\"1\";ask(on?\"Забанити гравця? Він не зможе зайти в гру.\":\"Розбанити гравця?\",function(){ban(b.dataset.id,on)})}};\nasync function grant(id,sign){var n=Math.floor(Number($(\"gAmt\").value));if(!n||n<=0){toast(\"Введи суму\",true);return}\ntry{var r=await api(\"/api/admin/grant\",{id:id,coins:n*sign});closeModal();toast(\"Готово. Баланс: \"+fmt(r.balance));loadUs()}catch(e){closeModal();toast(e.message===\"no_funds\"?\"У гравця немає стільки монет\":\"Помилка: \"+e.message,true)}}\nasync function ban(id,on){try{await api(\"/api/admin/ban\",{id:id,banned:on});toast(on?\"Гравця забанено\":\"Гравця розбанено\");loadUs()}catch(e){fail(e)}}\n\nloadStats();setInterval(function(){if(tab===\"wd\"||tab===\"stats\"||tab===\"cd\")load()},20000);\n</script></body></html>\n";
const INJECT = "<script>(function(){try{var t=window.Telegram&&Telegram.WebApp;fetch(\"/api/me\",{headers:{\"x-init-data\":(t&&t.initData)||\"\",\"x-dev-user\":localStorage.getItem(\"dev\")||\"\"}}).then(function(r){return r.json()}).then(function(m){if(!m||!m.admin)return;var b=document.createElement(\"button\");b.textContent=\"🛠 Адмін\";b.style.cssText=\"position:fixed;right:10px;bottom:calc(86px + env(safe-area-inset-bottom));z-index:40;padding:9px 13px;border-radius:14px;border:2px solid #ff2fb0;background:#1a0b4d;color:#fff;font:400 13px 'Russo One',sans-serif;box-shadow:0 0 14px #ff2fb088\";b.onclick=function(){location.href=\"/admin\"};document.body.appendChild(b);var f=function(){b.style.display=document.body.classList.contains(\"game\")?\"none\":\"block\"};f();setInterval(f,500)}).catch(function(){})}catch(e){}})();</script><script>(function(){try{\nvar T=window.Telegram&&Telegram.WebApp;\nfunction $(i){return document.getElementById(i)}\nfunction esc(s){return String(s).replace(/[<>&\"]/g,function(c){return {\"<\":\"&lt;\",\">\":\"&gt;\",\"&\":\"&amp;\",'\"':\"&quot;\"}[c]})}\nfunction say(t){if(T&&T.showAlert)T.showAlert(t);else alert(t)}\nfunction api(p,b){return fetch(p,{method:b?\"POST\":\"GET\",headers:{\"Content-Type\":\"application/json\",\"x-init-data\":(T&&T.initData)||\"\",\"x-dev-user\":localStorage.getItem(\"dev\")||\"\"},body:b?JSON.stringify(b):undefined}).then(function(r){return r.json().then(function(j){if(!r.ok)throw new Error(j.error||\"fail\");return j})})}\nvar E={age:\"Спочатку підтверди 18+ у профілі\",bad_amount:\"Невірна сума\",min_amount:\"Сума менша за мінімальну\",too_many:\"Забагато відкритих заявок, дочекайся обробки\",wd_min:\"Менше мінімуму для виводу\",bad_card:\"Невірний номер картки\",need_turnover:\"Спочатку треба зіграти на суму поповнень\",no_funds:\"Недостатньо монет\",gone:\"Заявку вже оброблено\",bad_cur:\"Оберіть валюту\",tiny:\"Сума виводу занадто мала\"};\nvar SYM={USD:\"$\",EUR:\"€\"},DS={new:\"очікує оплати\",claimed:\"на перевірці\",done:\"зараховано\",rejected:\"відхилено\"},WS={pending:\"в обробці\",paid:\"виплачено\",rejected:\"відхилено, монети повернено\"};\nvar INFO=null,cdCur=\"USD\",cwCur=\"USD\";\nfunction pick(box,cur){var bs=box.querySelectorAll(\"button\");for(var i=0;i<bs.length;i++){bs[i].className=\"btn \"+(bs[i].getAttribute(\"data-c\")===cur?\"pur\":\"navy\");bs[i].style.flex=\"1\"}}\nfunction digits(s){return String(s).split(\"\").filter(function(c){return c>=\"0\"&&c<=\"9\"}).join(\"\")}\nfunction grp(s){var o=[];for(var i=0;i<s.length;i+=4)o.push(s.slice(i,i+4));return o.join(\" \")}\nfunction copy(t,m){if(navigator.clipboard)navigator.clipboard.writeText(t).then(function(){say(m)},function(){say(t)});else say(t)}\nvar dep=$(\"w-dep\"),wd=$(\"w-wd\");if(!dep||!wd)return;\nvar soon=null;dep.querySelectorAll(\".card\").forEach(function(c){if(c.textContent.indexOf(\"Скоро\")>-1)soon=c});\nvar cd=document.createElement(\"div\");cd.className=\"card\";cd.id=\"cardDep\";\ncd.innerHTML='<h3>Банківська картка 💳</h3><p class=\"dim\" style=\"font-size:13px;margin-bottom:8px\">Переказ на картку в доларах або євро. Зараховуємо після перевірки, зазвичай до години.</p>'\n+'<div id=\"cdCur\" style=\"display:flex;gap:8px;margin-bottom:8px\"><button data-c=\"USD\">USD $</button><button data-c=\"EUR\">EUR €</button></div>'\n+'<small class=\"dim\">Сума</small><input class=\"inp\" id=\"cdAmt\" type=\"number\" inputmode=\"decimal\" placeholder=\"10\">'\n+'<div class=\"dim\" id=\"cdPrev\" style=\"margin:8px 0;min-height:18px\"></div><button class=\"btn go\" id=\"cdGo\" style=\"width:100%\">ОТРИМАТИ РЕКВІЗИТИ</button><div id=\"cdBox\"></div>';\nif(soon)soon.parentNode.replaceChild(cd,soon);else dep.appendChild(cd);\nvar cw=document.createElement(\"div\");cw.className=\"card\";cw.id=\"cardWd\";\ncw.innerHTML='<h3>Вивід на картку 💳</h3><p class=\"dim\" style=\"font-size:13px;margin-bottom:8px\" id=\"cwNote\">Виплата на картку в доларах або євро, зазвичай до 1 години.</p>'\n+'<div id=\"cwCurB\" style=\"display:flex;gap:8px;margin-bottom:8px\"><button data-c=\"USD\">USD $</button><button data-c=\"EUR\">EUR €</button></div>'\n+'<small class=\"dim\">Номер картки</small><input class=\"inp\" id=\"cwCard\" inputmode=\"numeric\" placeholder=\"0000 0000 0000 0000\" autocomplete=\"off\">'\n+'<small class=\"dim\">Скільки монет вивести</small><input class=\"inp\" id=\"cwCoins\" type=\"number\" inputmode=\"numeric\" placeholder=\"1000\">'\n+'<div class=\"dim\" id=\"cwPrev\" style=\"margin:8px 0;min-height:18px\"></div><button class=\"btn go\" id=\"cwGo\" style=\"width:100%\">ВИВЕСТИ НА КАРТКУ</button><div id=\"cwList\" style=\"margin-top:10px\"></div>';\nvar first=wd.querySelector(\".card\");if(first&&first.nextSibling)wd.insertBefore(cw,first.nextSibling);else wd.appendChild(cw);\npick($(\"cdCur\"),cdCur);pick($(\"cwCurB\"),cwCur);\n$(\"cdCur\").onclick=function(e){var b=e.target.closest(\"[data-c]\");if(b){cdCur=b.getAttribute(\"data-c\");pick($(\"cdCur\"),cdCur);cdPrev()}};\n$(\"cwCurB\").onclick=function(e){var b=e.target.closest(\"[data-c]\");if(b){cwCur=b.getAttribute(\"data-c\");pick($(\"cwCurB\"),cwCur);cwPrev()}};\nfunction cdPrev(){var a=Number($(\"cdAmt\").value);if(!INFO||!a){$(\"cdPrev\").textContent=INFO?\"Мінімум \"+INFO.min[cdCur]+\" \"+cdCur:\"\";return}\n$(\"cdPrev\").textContent=\"Отримаєш \"+Math.floor(a*INFO.rates[cdCur]).toLocaleString(\"uk-UA\")+\" монет (1 \"+cdCur+\" = \"+INFO.rates[cdCur]+\" монет)\"}\nfunction cwPrev(){var c=Math.floor(Number($(\"cwCoins\").value));if(!INFO||!c){$(\"cwPrev\").textContent=\"\";return}\nvar fee=Math.max(INFO.wdFeeMin,Math.ceil(c*INFO.wdFee));var a=Math.floor((c-fee)/INFO.rates[cwCur]*100)/100;\n$(\"cwPrev\").textContent=a>0?\"Комісія \"+fee+\" монет. Отримаєш \"+a.toFixed(2)+\" \"+cwCur:\"Замало монет\"}\n$(\"cdAmt\").oninput=cdPrev;$(\"cwCoins\").oninput=cwPrev;\n$(\"cdGo\").onclick=function(){var a=Number($(\"cdAmt\").value);if(!a){say(\"Введи суму\");return}\napi(\"/api/card/dep\",{cur:cdCur,amount:a}).then(function(){$(\"cdAmt\").value=\"\";refresh()}).catch(function(e){say(E[e.message]||\"Помилка: \"+e.message)})};\n$(\"cwGo\").onclick=function(){var c=Math.floor(Number($(\"cwCoins\").value));var cardN=digits($(\"cwCard\").value);\nif(!c){say(\"Введи кількість монет\");return}\napi(\"/api/card/wd\",{cur:cwCur,card:cardN,coins:c}).then(function(r){$(\"cwCoins\").value=\"\";say(\"Заявку створено. Виплата: \"+Number(r.amount).toFixed(2)+\" \"+cwCur+\".\");refresh()}).catch(function(e){say(E[e.message]||\"Помилка: \"+e.message)})};\nfunction render(r){\nvar h=\"\";\nr.deps.forEach(function(d){\nif(d.status===\"new\"){h+='<div class=\"card\" style=\"margin-top:10px;border-color:#7a2cff\"><b>Заявка C'+d.id+'</b><p class=\"dim\" style=\"font-size:13px;margin:6px 0\">Переказ: <b>'+Number(d.amount).toFixed(2)+' '+esc(d.cur)+'</b>. Отримаєш '+Number(d.coins).toLocaleString(\"uk-UA\")+' монет.</p>'\n+'<small class=\"dim\">Номер картки</small><input class=\"inp\" readonly value=\"'+esc(r.cardFmt)+'\"><button class=\"btn navy\" data-x=\"copy\" data-v=\"'+esc(r.card)+'\" style=\"width:100%;margin-top:8px\">Копіювати номер</button>'\n+'<p class=\"dim\" style=\"font-size:12px;margin:8px 0\">За можливості вкажи коментар до переказу: C'+d.id+'. Після переказу натисни кнопку нижче.</p>'\n+'<button class=\"btn go\" data-x=\"claim\" data-id=\"'+d.id+'\" style=\"width:100%\">Я ОПЛАТИВ</button></div>'}\nelse if(d.status===\"claimed\"){h+='<div class=\"card\" style=\"margin-top:10px\"><b>Заявка C'+d.id+'</b> · '+Number(d.amount).toFixed(2)+' '+esc(d.cur)+'<p class=\"dim\" style=\"font-size:13px;margin-top:6px\">Оплату перевіряємо, зазвичай до години. Монети прийдуть самі.</p></div>'}\nelse h+='<div class=\"dim\" style=\"font-size:12px;margin-top:6px\">C'+d.id+' · '+Number(d.amount).toFixed(2)+' '+esc(d.cur)+' · '+(DS[d.status]||d.status)+'</div>'});\n$(\"cdBox\").innerHTML=h;\nvar w=\"\";r.wds.forEach(function(x){w+='<div class=\"dim\" style=\"font-size:12px;margin-top:6px\">#'+x.id+' · '+Number(x.amount).toFixed(2)+' '+esc(x.cur)+' · '+(WS[x.status]||x.status)+'</div>'});\n$(\"cwList\").innerHTML=w}\nfunction refresh(){api(\"/api/card/list\").then(function(r){INFO=r.info;render(r);cdPrev();cwPrev();\n$(\"cwNote\").textContent=\"Виплата на картку в доларах або євро, зазвичай до 1 години. Комісія \"+Math.round(INFO.wdFee*100)+\"% (мінімум \"+INFO.wdFeeMin+\" монет). Мінімум \"+INFO.wdMin+\" монет.\"}).catch(function(){})}\n$(\"cdBox\").onclick=function(e){var b=e.target.closest(\"[data-x]\");if(!b)return;var x=b.getAttribute(\"data-x\");\nif(x===\"copy\")copy(b.getAttribute(\"data-v\"),\"Номер картки скопійовано\");\nelse if(x===\"claim\")api(\"/api/card/claim\",{id:Number(b.getAttribute(\"data-id\"))}).then(function(){say(\"Дякуємо! Перевіряємо оплату.\");refresh()}).catch(function(e2){say(E[e2.message]||\"Помилка\")})};\nrefresh();setInterval(function(){var v=$(\"v-wallet\");if(v&&v.className.indexOf(\"on\")>-1)refresh()},15000);\n}catch(e){}})();</script><script>(function(){try{\nvar T=window.Telegram&&Telegram.WebApp;\nfunction $(i){return document.getElementById(i)}\nfunction esc(s){return String(s).replace(/[<>&\"]/g,function(c){return {\"<\":\"&lt;\",\">\":\"&gt;\",\"&\":\"&amp;\",'\"':\"&quot;\"}[c]})}\nfunction fmt(n){return Math.round(Number(n)||0).toLocaleString(\"uk-UA\")}\nvar GAMES={rps:{n:\"Камінь-ножиці-папір\",s:\"КНП\",e:\"✊\",d:\"Обираєте одночасно. Нічия: ще раунд.\"},ttt:{n:\"Хрестики-нулики\",s:\"Хрестики\",e:\"❌\",d:\"Три в ряд. На хід 25 секунд.\"},card:{n:\"Висока карта\",s:\"Карти\",e:\"🃏\",d:\"У кого карта старша, той і виграв.\"}};\nvar ERR={age:\"Спочатку підтверди 18+ у профілі\",bad_bet:\"Ставка від 10 до 5000\",no_funds:\"Не вистачає монет\",in_match:\"Ти вже в матчі\",has_chal:\"Спочатку скасуй свій виклик\",too_many:\"Забагато відкритих викликів\",gone:\"Виклик уже зайнятий\",own:\"Це твій виклик\",bad_game:\"Невідома гра\",not_turn:\"Зараз не твій хід\",bad_move:\"Так ходити не можна\"};\nvar RAKE=0.1,BAL=0,ws=null,sel=\"rps\",stake=50,CH=[],M=null,END=null,ME=\"\",isOpen=false,tick=null,msg=\"\";\nvar STAKES=[10,50,100,500,1000,2500];\nvar HAND={r:\"✊\",p:\"✋\",s:\"✌️\"};\nvar RANKS=[\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"J\",\"Q\",\"K\",\"A\"],SUITS=[\"♠\",\"♥\",\"♦\",\"♣\"];\nvar css=document.createElement(\"style\");\ncss.textContent=\"#mx{position:fixed;left:0;top:0;right:0;bottom:0;z-index:70;background:#07041a;background-image:radial-gradient(circle at 50% -10%,#2a0f66 0,transparent 55%);color:#f4eeff;overflow-y:auto;padding:12px 12px 40px;font:500 14px/1.35 'Exo 2',system-ui,sans-serif}\"\n+\"#mx .in{max-width:480px;margin:0 auto}#mx h2,#mx b,#mx button{font-family:'Russo One',Impact,sans-serif;font-weight:400}\"\n+\".mxh{display:flex;align-items:center;gap:10px;margin-bottom:12px}.mxh h2{flex:1;font-size:20px;text-shadow:0 0 10px #ff2fb0}\"\n+\".mxb{border:2px solid #4a2ab0;border-radius:12px;background:#140a38;color:#27e6ff;width:38px;height:38px;font-size:18px}\"\n+\".mxp{border:2px solid #4a2ab0;border-radius:20px;padding:6px 12px;color:#ffc933;background:#140a38}\"\n+\".mxc{border:2px solid #4a2ab0;border-radius:14px;background:linear-gradient(#150b3c,#0d0629);padding:12px;margin-bottom:10px}\"\n+\".mxd{color:#9b8cd0;font-size:12px}\"\n+\".mxg{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:8px 0}\"\n+\".mxg button{border:2px solid #3a2a90;border-radius:12px;background:#0d0630;color:#cfe0ff;padding:10px 2px;font-size:12px}\"\n+\".mxg button.on{border-color:#7a2cff;background:linear-gradient(#2a1470,#1a0b4d);color:#fff;box-shadow:0 0 10px #7a2cff66}\"\n+\".mxgo{width:100%;border:0;border-radius:14px;padding:14px;color:#fff;font-size:16px;background:linear-gradient(#7dff4d,#25c21e);box-shadow:0 3px 0 #0f7a12;text-shadow:0 2px 0 rgba(0,0,0,.35)}\"\n+\".mxsm{border:0;border-radius:10px;padding:9px 12px;color:#fff;font-size:13px;background:linear-gradient(#7dff4d,#25c21e);box-shadow:0 2px 0 #0f7a12}\"\n+\".mxrd{background:linear-gradient(#ff6a7a,#c4162e)!important;box-shadow:0 2px 0 #6a0a18!important}\"\n+\".mxrow{display:flex;align-items:center;gap:8px;padding:8px 0;border-top:1px solid #2a1a70}.mxrow:first-child{border-top:0}\"\n+\".mxhand{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:14px 0}.mxhand button{border:3px solid #3d63ff;border-radius:16px;background:#0d0630;font-size:48px;padding:16px 0}.mxhand button:disabled{opacity:.4}\"\n+\".mxt{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;max-width:300px;margin:14px auto}.mxt button{aspect-ratio:1/1;border:3px solid #3a2a90;border-radius:14px;background:#0d0630;font-size:46px;color:#fff}\"\n+\".mxX{color:#ff3ea5}.mxO{color:#27e6ff}\"\n+\".mxcards{display:flex;gap:14px;justify-content:center;margin:14px 0}.mxcard{width:110px;height:150px;border-radius:14px;background:#fff;color:#111;display:flex;flex-direction:column;align-items:center;justify-content:center;font:400 40px 'Russo One',sans-serif;box-shadow:0 0 18px #7a2cff88}.mxcard.red{color:#d21d3a}.mxcard small{font-size:34px}\"\n+\".mxcap{text-align:center;margin-top:6px}\"\n+\".mxbig{text-align:center;font:400 28px 'Russo One',sans-serif;margin:10px 0}.mxwin{color:#5dff8a;text-shadow:0 0 12px #25c21e}.mxlose{color:#ff7b8a}.mxdraw{color:#ffd93d}\"\n+\".mxtm{text-align:center;color:#ffc933;font:400 18px 'Russo One',sans-serif}\";\ndocument.head.appendChild(css);\nvar root=null;\nfunction snd(o){if(ws&&ws.readyState===1)ws.send(JSON.stringify(o))}\nfunction wsOpen(){var q=\"init=\"+encodeURIComponent((T&&T.initData)||\"\")+\"&dev=\"+encodeURIComponent(localStorage.getItem(\"dev\")||\"\");\nws=new WebSocket((location.protocol===\"https:\"?\"wss://\":\"ws://\")+location.host+\"/ws?\"+q);\nws.onopen=function(){snd({t:\"mx.sub\"})};\nws.onmessage=function(e){var m;try{m=JSON.parse(e.data)}catch(x){return}onMsg(m)};\nws.onclose=function(){if(isOpen)setTimeout(function(){if(isOpen)wsOpen()},1200)}}\nfunction onMsg(m){\nif(m.t===\"mx.lobby\"){CH=m.chals;if(!M&&!END)draw()}\nelse if(m.t===\"mx.state\"){M=m.m;END=null;M.t0=Date.now();draw()}\nelse if(m.t===\"mx.end\"){M=m.m;END=m;draw()}\nelse if(m.t===\"bal\"){BAL=m.balance;var b=$(\"mxBal\");if(b)b.textContent=fmt(BAL)}\nelse if(m.t===\"err\"){say(ERR[m.e]||(\"Помилка: \"+m.e))}}\nfunction say(t){msg=t;if(T&&T.showAlert)T.showAlert(t);else alert(t)}\nfunction head(back){return '<div class=\"mxh\">'+(back?'<button class=\"mxb\" id=\"mxBack\">◀</button>':'')+'<h2>Ігри 1 на 1</h2><div class=\"mxp\">🪙 <span id=\"mxBal\">'+fmt(BAL)+'</span></div></div>'}\nfunction cardHtml(c){var r=c%13,s=Math.floor(c/13);return '<div class=\"mxcard'+(s===1||s===2?' red':'')+'\"><span>'+RANKS[r]+'</span><small>'+SUITS[s]+'</small></div>'}\nfunction draw(){if(!root)return;var h='<div class=\"in\">';\nif(END)h+=endHtml();else if(M)h+=matchHtml();else h+=lobbyHtml();\nroot.innerHTML=h+'</div>';bind()}\nfunction lobbyHtml(){var h=head(true);\nh+='<div class=\"mxc\"><b>Обери гру</b><div class=\"mxg\">';\n[\"rps\",\"ttt\",\"card\"].forEach(function(k){h+='<button data-g=\"'+k+'\" class=\"'+(sel===k?'on':'')+'\"><div style=\"font-size:26px\">'+GAMES[k].e+'</div>'+GAMES[k].s+'</button>'});\nh+='</div><div class=\"mxd\">'+GAMES[sel].d+'</div></div>';\nh+='<div class=\"mxc\"><b>Ставка</b><div class=\"mxg\">';\nSTAKES.forEach(function(s){h+='<button data-s=\"'+s+'\" class=\"'+(stake===s?'on':'')+'\">'+fmt(s)+'</button>'});\nh+='</div><button class=\"mxgo\" id=\"mxMake\">СТВОРИТИ ВИКЛИК</button><div class=\"mxd\" style=\"margin-top:8px\">Переможець забирає банк мінус '+Math.round(RAKE*100)+'%. Нічия: ставки повертаються. Монети лишаються в банку, поки хтось не прийме виклик, виклик можна скасувати.</div></div>';\nh+='<div class=\"mxc\"><b>Відкриті виклики</b>';\nif(!CH.length)h+='<div class=\"mxd\" style=\"padding:10px 0\">Поки нікого. Створи виклик і дочекайся суперника.</div>';\nCH.forEach(function(c){var mine=c.uid===ME;h+='<div class=\"mxrow\"><span style=\"font-size:24px\">'+GAMES[c.game].e+'</span><div style=\"flex:1\"><b>'+esc(c.name)+'</b><div class=\"mxd\">'+GAMES[c.game].n+' · '+fmt(c.stake)+' монет</div></div>'+(mine?'<button class=\"mxsm mxrd\" data-x=\"'+c.id+'\">Скасувати</button>':'<button class=\"mxsm\" data-j=\"'+c.id+'\">Грати</button>')+'</div>'});\nreturn h+'</div>'}\nfunction left(){if(!M||END)return 0;return Math.max(0,Math.ceil((M.left-(Date.now()-M.t0))/1000))}\nfunction matchHtml(){var g=M.game,me=M.you,opp=M.names[1-me];\nvar h=head(false)+'<div class=\"mxc\"><div class=\"mxcap\"><b>'+esc(M.names[me])+'</b> проти <b>'+esc(opp)+'</b></div><div class=\"mxd mxcap\">'+GAMES[g].n+' · банк '+fmt(M.stake*2)+'</div><div class=\"mxtm\" id=\"mxT\">'+left()+' с</div></div>';\nif(g===\"rps\"){var s=M.st;h+='<div class=\"mxc\"><div class=\"mxcap\"><b>Раунд '+s.round+'</b></div>';\nif(s.mine===null){h+='<div class=\"mxd mxcap\">Обери свій знак</div><div class=\"mxhand\"><button data-m=\"r\">✊</button><button data-m=\"p\">✋</button><button data-m=\"s\">✌️</button></div>'}\nelse{h+='<div class=\"mxbig\">'+HAND[s.mine]+'</div><div class=\"mxd mxcap\">'+(s.opp?'Рахуємо…':'Чекаємо, поки суперник обере…')+'</div>'}\ns.hist.forEach(function(x,i){h+='<div class=\"mxrow\"><span>Раунд '+(i+1)+'</span><div style=\"flex:1;text-align:center;font-size:24px\">'+HAND[x.me]+' : '+HAND[x.opp]+'</div><span class=\"mxd\">нічия</span></div>'});\nh+='</div>'}\nelse if(g===\"ttt\"){var my=me===0?\"X\":\"O\";h+='<div class=\"mxc\"><div class=\"mxcap\"><b>'+(M.turn===me?'Твій хід':'Хід суперника')+'</b> · ти <span class=\"mx'+my+'\"><b>'+(my===\"X\"?\"❌\":\"⭕\")+'</b></span></div><div class=\"mxt\">';\nM.st.b.forEach(function(v,i){h+='<button data-c=\"'+i+'\"'+(v||M.turn!==me?' disabled':'')+'>'+(v===\"X\"?'<span class=\"mxX\">❌</span>':v===\"O\"?'<span class=\"mxO\">⭕</span>':'')+'</button>'});\nh+='</div></div>'}\nreturn h}\nfunction endHtml(){var e=END,me=M.you,g=M.game,win=e.winner===null?0:(e.winner===ME?1:-1);\nvar h=head(false);\nif(g===\"card\"&&M.st.mine!==undefined)h+='<div class=\"mxc\"><div class=\"mxcards\"><div class=\"mxcap\">'+cardHtml(M.st.mine)+'<div class=\"mxd\">Ти</div></div><div class=\"mxcap\">'+cardHtml(M.st.opp)+'<div class=\"mxd\">'+esc(M.names[1-me])+'</div></div></div></div>';\nif(g===\"ttt\"){h+='<div class=\"mxc\"><div class=\"mxt\">';M.st.b.forEach(function(v){h+='<button disabled>'+(v===\"X\"?'<span class=\"mxX\">❌</span>':v===\"O\"?'<span class=\"mxO\">⭕</span>':'')+'</button>'});h+='</div></div>'}\nif(g===\"rps\"){h+='<div class=\"mxc\"><div class=\"mxbig\">'+(M.st.mine?HAND[M.st.mine]:'—')+' : '+(M.st.opp?HAND[M.st.opp]:'—')+'</div></div>'}\nif(win>0)h+='<div class=\"mxbig mxwin\">ПЕРЕМОГА +'+fmt(e.win)+'</div><div class=\"mxd mxcap\">Чистий виграш '+fmt(e.win-M.stake)+' монет, рейк '+fmt(e.rake)+'</div>';\nelse if(win<0)h+='<div class=\"mxbig mxlose\">ПРОГРАШ −'+fmt(M.stake)+'</div>';\nelse h+='<div class=\"mxbig mxdraw\">НІЧИЯ</div><div class=\"mxd mxcap\">Ставки повернено</div>';\nif(e.reason===\"timeout\")h+='<div class=\"mxd mxcap\" style=\"margin-top:6px\">Час на хід вийшов</div>';\nh+='<button class=\"mxgo\" id=\"mxAgain\" style=\"margin-top:16px\">ДО ІГОР</button>';return h}\nfunction bind(){var q=function(s){return root.querySelectorAll(s)};\nvar bk=$(\"mxBack\");if(bk)bk.onclick=closeMx;\nq(\"[data-g]\").forEach(function(b){b.onclick=function(){sel=b.getAttribute(\"data-g\");draw()}});\nq(\"[data-s]\").forEach(function(b){b.onclick=function(){stake=Number(b.getAttribute(\"data-s\"));draw()}});\nvar mk=$(\"mxMake\");if(mk)mk.onclick=function(){snd({t:\"mx.create\",game:sel,stake:stake})};\nq(\"[data-j]\").forEach(function(b){b.onclick=function(){snd({t:\"mx.join\",id:Number(b.getAttribute(\"data-j\"))})}});\nq(\"[data-x]\").forEach(function(b){b.onclick=function(){snd({t:\"mx.cancel\",id:Number(b.getAttribute(\"data-x\"))})}});\nq(\"[data-m]\").forEach(function(b){b.onclick=function(){snd({t:\"mx.move\",id:M.id,move:b.getAttribute(\"data-m\")})}});\nq(\"[data-c]\").forEach(function(b){b.onclick=function(){snd({t:\"mx.move\",id:M.id,cell:Number(b.getAttribute(\"data-c\"))})}});\nvar ag=$(\"mxAgain\");if(ag)ag.onclick=function(){END=null;M=null;draw()}}\nfunction openMx(){if(isOpen)return;isOpen=true;M=null;END=null;\nroot=document.createElement(\"div\");root.id=\"mx\";document.body.appendChild(root);\nfetch(\"/api/me\",{headers:{\"x-init-data\":(T&&T.initData)||\"\",\"x-dev-user\":localStorage.getItem(\"dev\")||\"\"}}).then(function(r){return r.json()}).then(function(m){ME=m.id;BAL=m.balance;if(m.cfg)RAKE=m.cfg.rake;draw()}).catch(function(){});\ndraw();wsOpen();\ntick=setInterval(function(){var t=$(\"mxT\");if(t)t.textContent=left()+\" с\"},300)}\nfunction closeMx(){isOpen=false;clearInterval(tick);snd({t:\"mx.leave\"});try{ws.close()}catch(e){}if(root){root.parentNode.removeChild(root);root=null}}\nfunction addTile(){var g=document.querySelector(\"#v-lobby .games\");if(!g||$(\"mxTile\"))return;\nvar b=document.createElement(\"button\");b.className=\"gcard\";b.id=\"mxTile\";b.style.cssText=\"--c:#ffc933;grid-column:1/-1;flex-direction:row;justify-content:center;gap:14px\";\nb.innerHTML='<span style=\"font-size:30px;line-height:32px\">⚔️</span><span>ІГРИ 1 НА 1</span><em>камінь-ножиці · хрестики · карти</em>';\nb.onclick=openMx;g.appendChild(b)}\naddTile();setTimeout(addTile,800);\n}catch(e){}})();</script><script>(function(){try{\nvar T=window.Telegram&&Telegram.WebApp;\nvar D=[[\"(захист від відмивання).\", \"(защита от отмывания).\", \"(anti-money-laundering protection).\"], [\"(мінімум\", \"(минимум\", \"(minimum\"], [\") з банку.\", \") с банка.\", \") of the pot.\"], [\", мінімум 1000 монет.\", \", минимум 1000 монет.\", \", minimum 1000 coins.\"], [\". Монетки не вертаються, поки ніхто не прийме виклик, але його можна скасувати.\", \". Монеты не возвращаются, пока никто не примет вызов, но его можно отменить.\", \". Coins stay locked until someone accepts the challenge, but you can cancel it.\"], [\". У тебе\", \". У тебя\", \". You have\"], [\"1 на 1\", \"1 на 1\", \"1 vs 1\"], [\"TON · комісія\", \"TON · комиссия\", \"TON · fee\"], [\"TON-адреса\", \"TON-адрес\", \"TON address\"], [\"Telegram Зірки ⭐\", \"Telegram Звёзды ⭐\", \"Telegram Stars ⭐\"], [\"· гравців\", \"· игроков\", \"· players\"], [\"½ банку\", \"½ банка\", \"½ pot\"], [\"Історія\", \"История\", \"History\"], [\"Адреса\", \"Адрес\", \"Address\"], [\"Адресу скопійовано\", \"Адрес скопирован\", \"Address copied\"], [\"Баланс\", \"Баланс\", \"Balance\"], [\"Банк\", \"Банк\", \"Pot\"], [\"Банк раунду\", \"Банк раунда\", \"Round pot\"], [\"Банківська картка 💳\", \"Банковская карта 💳\", \"Bank card 💳\"], [\"Без коментаря кошти не зарахуються автоматично.\", \"Без комментария средства не зачислятся автоматически.\", \"Without the comment, funds will not be credited automatically.\"], [\"Блайнди\", \"Блайнды\", \"Blinds\"], [\"ВИВЕСТИ\", \"ВЫВЕСТИ\", \"WITHDRAW\"], [\"Вже недоступно\", \"Уже недоступно\", \"No longer available\"], [\"Вивести\", \"Вывести\", \"Withdraw\"], [\"Виводів в очікуванні\", \"Выводов в ожидании\", \"Pending withdrawals\"], [\"Виводів ще не було.\", \"Выводов ещё не было.\", \"No withdrawals yet.\"], [\"Вивід\", \"Вывод\", \"Withdrawal\"], [\"Вивід скасовано\", \"Вывод отменён\", \"Withdrawal cancelled\"], [\"Вивід у TON\", \"Вывод в TON\", \"Withdraw to TON\"], [\"Виграш у джекпоті\", \"Выигрыш в джекпоте\", \"Jackpot win\"], [\"Виграш у дуелі\", \"Выигрыш в дуэли\", \"Duel win\"], [\"Вийти\", \"Выйти\", \"Leave\"], [\"Вийти зі столу\", \"Выйти из-за стола\", \"Leave table\"], [\"Вийти зі столу?\", \"Выйти из-за стола?\", \"Leave the table?\"], [\"Вихід зі столу\", \"Выход из-за стола\", \"Table cash-out\"], [\"Вхід\", \"Вход\", \"Buy-in\"], [\"Вхід за стіл\", \"Вход за стол\", \"Table buy-in\"], [\"Вібрація\", \"Вибрация\", \"Vibration\"], [\"Відкрий гру через Telegram-бота\", \"Открой игру через Telegram-бота\", \"Open the game via the Telegram bot\"], [\"Відкрити Tonkeeper\", \"Открыть Tonkeeper\", \"Open Tonkeeper\"], [\"Відкриті виклики\", \"Открытые вызовы\", \"Open challenges\"], [\"ГАМАНЕЦЬ\", \"КОШЕЛЁК\", \"WALLET\"], [\"ГРАВЕЦЬ\", \"ИГРОК\", \"PLAYER\"], [\"Гаманець\", \"Кошелёк\", \"Wallet\"], [\"Гравець\", \"Игрок\", \"Player\"], [\"Гравців\", \"Игроков\", \"Players\"], [\"ДЖЕКПОТ\", \"ДЖЕКПОТ\", \"JACKPOT\"], [\"ДУЕЛІ\", \"ДУЭЛИ\", \"DUELS\"], [\"Джекпот\", \"Джекпот\", \"Jackpot\"], [\"До розіграшу\", \"До розыгрыша\", \"Draw in\"], [\"Донат Зірками йде на розвиток казино. Монети за це не нараховуються, але біля імені з'явиться значок підтримки.\", \"Донат Звёздами идёт на развитие казино. Монеты за это не начисляются, но рядом с именем появится значок поддержки.\", \"Star donations go to the casino's development. No coins are credited for it, but a supporter badge will appear next to your name.\"], [\"Дохід казино\", \"Доход казино\", \"Casino income\"], [\"Дуелі\", \"Дуэли\", \"Duels\"], [\"Дякуємо за підтримку!\", \"Спасибо за поддержку!\", \"Thanks for your support!\"], [\"ЗРОЗУМІЛО\", \"ПОНЯТНО\", \"GOT IT\"], [\"Забагато відкритих запитів\", \"Слишком много открытых запросов\", \"Too many open requests\"], [\"Занадто малий рейз\", \"Слишком маленький рейз\", \"Raise is too small\"], [\"Запит на вивід створено\", \"Запрос на вывод создан\", \"Withdrawal request created\"], [\"Зараз не твій хід\", \"Сейчас не твой ход\", \"It's not your turn\"], [\"Зараховується одразу після оплати. 1 ⭐ =\", \"Зачисляется сразу после оплаты. 1 ⭐ =\", \"Credited right after payment. 1 ⭐ =\"], [\"Звук\", \"Звук\", \"Sound\"], [\"Зірки на розвиток\", \"Звёзды на развитие\", \"Stars for development\"], [\"КОЛ\", \"КОЛЛ\", \"CALL\"], [\"Казино не грає проти тебе: ви граєте між собою, а ми беремо лише комісію з банку. Тільки 18+. Грай відповідально.\", \"Казино не играет против тебя: вы играете между собой, а мы берём только комиссию с банка. Только 18+. Играй ответственно.\", \"The casino doesn't play against you: you play each other, and we only take a commission from the pot. 18+ only. Play responsibly.\"], [\"Кинути виклик\", \"Бросить вызов\", \"Challenge\"], [\"Коментар (обов'язково!)\", \"Комментарий (обязательно!)\", \"Comment (required!)\"], [\"Коментар скопійовано\", \"Комментарий скопирован\", \"Comment copied\"], [\"Комісія виводу\", \"Комиссия вывода\", \"Withdrawal fee\"], [\"Копіювати адресу\", \"Копировать адрес\", \"Copy address\"], [\"Копіювати коментар\", \"Копировать комментарий\", \"Copy comment\"], [\"Кості\", \"Кости\", \"Dice\"], [\"Крутимо…\", \"Крутим…\", \"Spinning…\"], [\"Кінець роздачі\", \"Конец раздачи\", \"Hand over\"], [\"Лишитись\", \"Остаться\", \"Stay\"], [\"Лобі\", \"Лобби\", \"Lobby\"], [\"Ліміт ставки в раунді\", \"Лимит ставки в раунде\", \"Round bet limit\"], [\"МЕНІ Є 18, ПРОДОВЖИТИ\", \"МНЕ ЕСТЬ 18, ПРОДОЛЖИТЬ\", \"I'M 18+, CONTINUE\"], [\"Макс\", \"Макс\", \"Max\"], [\"Минулий раунд:\", \"Прошлый раунд:\", \"Last round:\"], [\"Монет у іграх\", \"Монет в играх\", \"Coins in play\"], [\"Монетка\", \"Монетка\", \"Coin\"], [\"Мої виводи\", \"Мои выводы\", \"My withdrawals\"], [\"Музика\", \"Музыка\", \"Music\"], [\"Мін\", \"Мин\", \"Min\"], [\"Середній\", \"Средний\", \"Mid\"], [\"Мінімальний вхід\", \"Минимальный вход\", \"Minimum buy-in\"], [\"Мінімум для виводу 1000 монет\", \"Минимум для вывода 1000 монет\", \"Minimum withdrawal is 1000 coins\"], [\"Місце ще не підтверджено…\", \"Место ещё не подтверждено…\", \"Seat not confirmed yet…\"], [\"Налаштування\", \"Настройки\", \"Settings\"], [\"Нарахування\", \"Начисление\", \"Credit\"], [\"Не вийшло, спробуй ще раз\", \"Не получилось, попробуй ещё раз\", \"Something failed, try again\"], [\"Не вистачає монет\", \"Не хватает монет\", \"Not enough coins\"], [\"Невірна TON-адреса\", \"Неверный TON-адрес\", \"Invalid TON address\"], [\"Невірна кількість\", \"Неверное количество\", \"Invalid amount\"], [\"Невірна сума\", \"Неверная сумма\", \"Invalid amount\"], [\"Недоступна дія\", \"Недоступное действие\", \"Action not available\"], [\"Немає зв'язку, перепідключаємось…\", \"Нет связи, переподключаемся…\", \"Connection lost, reconnecting…\"], [\"Немає зв'язку, спробуй ще раз\", \"Нет связи, попробуй ещё раз\", \"No connection, try again\"], [\"ОК\", \"ОК\", \"OK\"], [\"Обіг (зіграно)\", \"Оборот (сыграно)\", \"Turnover (wagered)\"], [\"Оплата не пройшла\", \"Оплата не прошла\", \"Payment failed\"], [\"Оплата прийнята, зараховуємо…\", \"Оплата принята, зачисляем…\", \"Payment received, crediting…\"], [\"Отримаєш\", \"Получишь\", \"You'll get\"], [\"ПІДТВЕРДИТИ\", \"ПОДТВЕРДИТЬ\", \"CONFIRM\"], [\"ПІДТРИМАВ ПРОЄКТ\", \"ПОДДЕРЖАЛ ПРОЕКТ\", \"PROJECT SUPPORTER\"], [\"Підтримав проєкт\", \"Поддержал проект\", \"Supported the project\"], [\"ПЕРЕМОГА +\", \"ПОБЕДА +\", \"WIN +\"], [\"ПОКЕР\", \"ПОКЕР\", \"POKER\"], [\"ПОПОВНИТИ\", \"ПОПОЛНИТЬ\", \"DEPOSIT\"], [\"Переказуй з будь-якого гаманця. Курс: 1 TON =\", \"Переводи с любого кошелька. Курс: 1 TON =\", \"Send from any wallet. Rate: 1 TON =\"], [\"Переможець забирає банк мінус\", \"Победитель забирает банк минус\", \"The winner takes the pot minus\"], [\"Переміг\", \"Победил\", \"Won\"], [\"Платежі тимчасово недоступні\", \"Платежи временно недоступны\", \"Payments are temporarily unavailable\"], [\"Повернення (перезапуск)\", \"Возврат (перезапуск)\", \"Refund (restart)\"], [\"Покер\", \"Покер\", \"Poker\"], [\"Поки немає викликів. Створи перший.\", \"Пока нет вызовов. Создай первый.\", \"No challenges yet. Create the first one.\"], [\"Поповнення TON\", \"Пополнение TON\", \"TON deposit\"], [\"Поповнення ⭐\", \"Пополнение ⭐\", \"Stars deposit ⭐\"], [\"Поповнити\", \"Пополнить\", \"Deposit\"], [\"Правила й комісії\", \"Правила и комиссии\", \"Rules & fees\"], [\"Прийняти\", \"Принять\", \"Accept\"], [\"Програш −\", \"Проигрыш −\", \"Loss −\"], [\"Проти\", \"Против\", \"Vs\"], [\"Профіль\", \"Профиль\", \"Profile\"], [\"Підключення…\", \"Подключение…\", \"Connecting…\"], [\"Підтримати\", \"Поддержать\", \"Support\"], [\"Підтримати проєкт\", \"Поддержать проект\", \"Support the project\"], [\"Підтримати проєкт 💜\", \"Поддержать проект 💜\", \"Support the project 💜\"], [\"Підтримка\", \"Поддержка\", \"Support\"], [\"Підтримка проєкту\", \"Поддержка проекта\", \"Project support\"], [\"РЕЙЗ\", \"РЕЙЗ\", \"RAISE\"], [\"Раунд відкритий\", \"Раунд открыт\", \"Round open\"], [\"Раунд закривається, зачекай наступний\", \"Раунд закрывается, подожди следующий\", \"Round is closing, wait for the next one\"], [\"Рейк\", \"Рейк\", \"Rake\"], [\"Роздача починається…\", \"Раздача начинается…\", \"Hand starting…\"], [\"Розіграш…\", \"Розыгрыш…\", \"Drawing…\"], [\"СІСТИ\", \"СЕСТЬ\", \"SIT\"], [\"СІСТИ ЗА СТІЛ\", \"СЕСТЬ ЗА СТОЛ\", \"TAKE A SEAT\"], [\"СТАВКА\", \"СТАВКА\", \"BET\"], [\"СТВОРИТИ ВИКЛИК\", \"СОЗДАТЬ ВЫЗОВ\", \"CREATE CHALLENGE\"], [\"Скасувати\", \"Отменить\", \"Cancel\"], [\"Скопіюй вручну:\", \"Скопируй вручную:\", \"Copy manually:\"], [\"Скоро\", \"Скоро\", \"Soon\"], [\"Скільки монет вивести\", \"Сколько монет вывести\", \"How many coins to withdraw\"], [\"Спершу зіграй на суму своїх поповнень\", \"Сначала сыграй на сумму своих пополнений\", \"First wager the amount of your deposits\"], [\"Спочатку підтверди, що тобі 18+\", \"Сначала подтверди, что тебе 18+\", \"First confirm you are 18+\"], [\"Ставок ще немає. Став першим.\", \"Ставок ещё нет. Ставь первым.\", \"No bets yet. Be the first.\"], [\"Статистика казино ▾\", \"Статистика казино ▾\", \"Casino stats ▾\"], [\"Стіл\", \"Стол\", \"Table\"], [\"Стіл заповнений\", \"Стол заполнен\", \"Table is full\"], [\"Сума входу поза межами столу\", \"Сумма входа вне лимитов стола\", \"Buy-in is outside the table limits\"], [\"Техаський холдем, до 6 гравців. Обери стіл і сідай.\", \"Техасский холдем, до 6 игроков. Выбери стол и садись.\", \"Texas Hold'em, up to 6 players. Pick a table and sit down.\"], [\"Ти вже сидиш за столом\", \"Ты уже сидишь за столом\", \"You're already seated at a table\"], [\"Ти граєш проти інших гравців. Казино нічого не виграє у тебе, воно лише бере комісію (рейк\", \"Ты играешь против других игроков. Казино ничего не выигрывает у тебя, оно только берёт комиссию (рейк\", \"You play against other players. The casino wins nothing from you, it only takes a commission (rake\"], [\"Ти склав карти\", \"Ты сбросил карты\", \"You folded\"], [\"Ти склеїш поточну роздачу, а фішки повернуться на баланс.\", \"Ты сбросишь текущую раздачу, а фишки вернутся на баланс.\", \"You'll fold the current hand and your chips return to your balance.\"], [\"Треба ще зіграти на\", \"Нужно ещё сыграть на\", \"You still need to wager\"], [\"Тільки 18+\", \"Только 18+\", \"18+ only\"], [\"ФОЛД\", \"ФОЛД\", \"FOLD\"], [\"Фішки повернуться на твій баланс.\", \"Фишки вернутся на твой баланс.\", \"Your chips return to your balance.\"], [\"Ходить\", \"Ходит\", \"Turn:\"], [\"Це гра на гроші між гравцями. Є ризик програти. Заходь, лише якщо тобі виповнилось 18 і ти розумієш правила.\", \"Это игра на деньги между игроками. Есть риск проиграть. Заходи, только если тебе исполнилось 18 и ты понимаешь правила.\", \"This is a real-money game between players. You can lose. Only enter if you are 18 or older and understand the rules.\"], [\"Це твій власний виклик\", \"Это твой собственный вызов\", \"This is your own challenge\"], [\"ЧЕК\", \"ЧЕК\", \"CHECK\"], [\"Чекай на наступну роздачу\", \"Жди следующую раздачу\", \"Wait for the next hand\"], [\"Чекаємо на суперників…\", \"Ждём соперников…\", \"Waiting for opponents…\"], [\"Чекаємо ще одного гравця\", \"Ждём ещё одного игрока\", \"Waiting for one more player\"], [\"Чим більша ставка, тим вища шанс. Таймер 30 с стартує, коли зайшли двоє.\", \"Чем больше ставка, тем выше шанс. Таймер 30 с стартует, когда зашли двое.\", \"The bigger the bet, the higher the chance. A 30 s timer starts when two players have joined.\"], [\"Ще немає операцій.\", \"Операций пока нет.\", \"No transactions yet.\"], [\"Щоб вивести, треба зіграти ще на\", \"Чтобы вывести, нужно сыграть ещё на\", \"To withdraw, you need to wager another\"], [\"Щось пішло не так\", \"Что-то пошло не так\", \"Something went wrong\"], [\"Як це працює\", \"Как это работает\", \"How it works\"], [\"банк\", \"банк\", \"pot\"], [\"виграв\", \"выиграл\", \"won\"], [\"викликів\", \"вызовов\", \"challenges\"], [\"виплачено\", \"выплачено\", \"paid\"], [\"відхилено\", \"отклонено\", \"rejected\"], [\"вільно\", \"свободно\", \"free\"], [\"з банку\", \"с банка\", \"from the pot\"], [\"з банку (у покері — тільки якщо роздача дійшла до флопу, не більше 3 великих блайндів).\", \"с банка (в покере — только если раздача дошла до флопа, не более 3 больших блайндов).\", \"of the pot (in poker — only if the hand reached the flop, at most 3 big blinds).\"], [\"за столами\", \"за столами\", \"at tables\"], [\"кості\", \"кости\", \"dice\"], [\"монет\", \"монет\", \"coins\"], [\"монет). Мінімум\", \"монет). Минимум\", \"coins). Minimum\"], [\"монет.\", \"монет.\", \"coins.\"], [\"монет. Виплату підтверджує адміністратор вручну.\", \"монет. Выплату подтверждает администратор вручную.\", \"coins. The payout is confirmed manually by an admin.\"], [\"монет. Мінімум 0.1 TON. Зарахуємо за 1–2 хв.\", \"монет. Минимум 0.1 TON. Зачислим за 1–2 мин.\", \"coins. Minimum 0.1 TON. Credited within 1–2 min.\"], [\"монетка\", \"монетка\", \"coin\"], [\"очікує\", \"ожидает\", \"pending\"], [\"поповнити · вивести\", \"пополнить · вывести\", \"deposit · withdraw\"], [\"с\", \"с\", \"s\"], [\"столи\", \"столы\", \"tables\"], [\"• Вивід у TON: комісія\", \"• Вывод в TON: комиссия\", \"• TON withdrawal: fee\"], [\"• Вивід: комісія\", \"• Вывод: комиссия\", \"• Withdrawal: fee\"], [\"• Дуелі та джекпот: рейк з банку переможця.\", \"• Дуэли и джекпот: рейк с банка победителя.\", \"• Duels and jackpot: rake from the winner's pot.\"], [\"• Покер: рейк лише якщо дійшло до флопу, не більше 3 великих блайндів.\", \"• Покер: рейк только если дошло до флопа, не более 3 больших блайндов.\", \"• Poker: rake only if the hand reached the flop, at most 3 big blinds.\"], [\"• Рейк\", \"• Рейк\", \"• Rake\"], [\"• Спочатку треба зіграти на суму поповнень.\", \"• Сначала нужно сыграть на сумму пополнений.\", \"• You must first wager the amount of your deposits.\"], [\"• Ти граєш проти інших гравців, казино грає лише роль «столу».\", \"• Ты играешь против других игроков, казино играет лишь роль «стола».\", \"• You play against other players; the casino only acts as the \\\"table\\\".\"], [\"• Тільки 18+.\", \"• Только 18+.\", \"• 18+ only.\"], [\"• Тільки 18+. Грай відповідально.\", \"• Только 18+. Играй ответственно.\", \"• 18+ only. Play responsibly.\"], [\"Старша карта\", \"Старшая карта\", \"High card\"], [\"Пара\", \"Пара\", \"Pair\"], [\"Дві пари\", \"Две пары\", \"Two pair\"], [\"Трійка\", \"Тройка\", \"Three of a kind\"], [\"Стріт\", \"Стрит\", \"Straight\"], [\"Флеш\", \"Флеш\", \"Flush\"], [\"Фул-хаус\", \"Фул-хаус\", \"Full house\"], [\"Каре\", \"Каре\", \"Four of a kind\"], [\"Стріт-флеш\", \"Стрит-флеш\", \"Straight flush\"], [\"%. Нічия: ставки повертаються. Монети лишаються в банку, поки хтось не прийме виклик, виклик можна скасувати.\", \"%. Ничья: ставки возвращаются. Монеты остаются в банке, пока кто-то не примет вызов, вызов можно отменить.\", \"%. Draw: stakes are returned. Coins stay locked until someone accepts, and you can cancel the challenge.\"], [\"% (мінімум\", \"% (минимум\", \"% (minimum\"], [\". Отримаєш\", \". Получишь\", \". You'll get\"], [\". Після переказу натисни кнопку нижче.\", \". После перевода нажми кнопку ниже.\", \". After the transfer, press the button below.\"], [\"· банк\", \"· банк\", \"· pot\"], [\"· ти\", \"· ты\", \"· you\"], [\"ІГРИ 1 НА 1\", \"ИГРЫ 1 НА 1\", \"1V1 GAMES\"], [\"Ігри 1 на 1\", \"Игры 1 на 1\", \"1v1 games\"], [\"ВИВЕСТИ НА КАРТКУ\", \"ВЫВЕСТИ НА КАРТУ\", \"WITHDRAW TO CARD\"], [\"Введи кількість монет\", \"Введи количество монет\", \"Enter the number of coins\"], [\"Введи суму\", \"Введи сумму\", \"Enter the amount\"], [\"Вивід на картку 💳\", \"Вывод на карту 💳\", \"Card withdrawal 💳\"], [\"Виклик уже зайнятий\", \"Вызов уже занят\", \"Challenge already taken\"], [\"Виплата на картку в доларах або євро, зазвичай до 1 години.\", \"Выплата на карту в долларах или евро, обычно до 1 часа.\", \"Payout to a card in dollars or euros, usually within 1 hour.\"], [\"Виплата на картку в доларах або євро, зазвичай до 1 години. Комісія\", \"Выплата на карту в долларах или евро, обычно до 1 часа. Комиссия\", \"Payout to a card in dollars or euros, usually within 1 hour. Fee\"], [\"Висока карта\", \"Старшая карта\", \"High card\"], [\"Грати\", \"Играть\", \"Play\"], [\"ДО ІГОР\", \"К ИГРАМ\", \"BACK TO GAMES\"], [\"Дякуємо! Перевіряємо оплату.\", \"Спасибо! Проверяем оплату.\", \"Thanks! We're checking your payment.\"], [\"За можливості вкажи коментар до переказу: C\", \"По возможности укажи комментарий к переводу: C\", \"If possible, add this comment to the transfer: C\"], [\"Забагато відкритих викликів\", \"Слишком много открытых вызовов\", \"Too many open challenges\"], [\"Забагато відкритих заявок, дочекайся обробки\", \"Слишком много открытых заявок, дождись обработки\", \"Too many open requests, wait until they are processed\"], [\"Замало монет\", \"Мало монет\", \"Not enough coins\"], [\"Заявка C\", \"Заявка C\", \"Request C\"], [\"Заявку вже оброблено\", \"Заявка уже обработана\", \"Request already processed\"], [\"Заявку створено. Виплата:\", \"Заявка создана. Выплата:\", \"Request created. Payout:\"], [\"КНП\", \"КНБ\", \"RPS\"], [\"Камінь-ножиці-папір\", \"Камень-ножницы-бумага\", \"Rock-paper-scissors\"], [\"Карти\", \"Карты\", \"Cards\"], [\"Комісія\", \"Комиссия\", \"Fee\"], [\"Копіювати номер\", \"Копировать номер\", \"Copy number\"], [\"Менше мінімуму для виводу\", \"Меньше минимума для вывода\", \"Below the minimum withdrawal\"], [\"Мінімум\", \"Минимум\", \"Minimum\"], [\"НІЧИЯ\", \"НИЧЬЯ\", \"DRAW\"], [\"Невідома гра\", \"Неизвестная игра\", \"Unknown game\"], [\"Невірний номер картки\", \"Неверный номер карты\", \"Invalid card number\"], [\"Недостатньо монет\", \"Недостаточно монет\", \"Insufficient coins\"], [\"Номер картки\", \"Номер карты\", \"Card number\"], [\"Номер картки скопійовано\", \"Номер карты скопирован\", \"Card number copied\"], [\"ОТРИМАТИ РЕКВІЗИТИ\", \"ПОЛУЧИТЬ РЕКВИЗИТЫ\", \"GET PAYMENT DETAILS\"], [\"Обери гру\", \"Выбери игру\", \"Pick a game\"], [\"Обери свій знак\", \"Выбери свой знак\", \"Pick your move\"], [\"Оберіть валюту\", \"Выберите валюту\", \"Choose a currency\"], [\"Обираєте одночасно. Нічия: ще раунд.\", \"Выбираете одновременно. Ничья: ещё раунд.\", \"You choose at the same time. Draw: another round.\"], [\"Оплату перевіряємо, зазвичай до години. Монети прийдуть самі.\", \"Оплату проверяем, обычно до часа. Монеты придут сами.\", \"We're checking the payment, usually within an hour. Coins will arrive automatically.\"], [\"ПРОГРАШ −\", \"ПРОИГРЫШ −\", \"LOSS −\"], [\"Переказ на картку в доларах або євро. Зараховуємо після перевірки, зазвичай до години.\", \"Перевод на карту в долларах или евро. Зачисляем после проверки, обычно до часа.\", \"Transfer to a card in dollars or euros. Credited after verification, usually within an hour.\"], [\"Переказ:\", \"Перевод:\", \"Transfer:\"], [\"Поки нікого. Створи виклик і дочекайся суперника.\", \"Пока никого. Создай вызов и дождись соперника.\", \"Nobody yet. Create a challenge and wait for an opponent.\"], [\"Помилка\", \"Ошибка\", \"Error\"], [\"Помилка:\", \"Ошибка:\", \"Error:\"], [\"Раунд\", \"Раунд\", \"Round\"], [\"Рахуємо…\", \"Считаем…\", \"Counting…\"], [\"Спочатку підтверди 18+ у профілі\", \"Сначала подтверди 18+ в профиле\", \"First confirm 18+ in your profile\"], [\"Спочатку скасуй свій виклик\", \"Сначала отмени свой вызов\", \"Cancel your challenge first\"], [\"Спочатку треба зіграти на суму поповнень\", \"Сначала нужно сыграть на сумму пополнений\", \"You must first wager the amount of your deposits\"], [\"Ставка\", \"Ставка\", \"Bet\"], [\"Ставка від 10 до 5000\", \"Ставка от 10 до 5000\", \"Bet from 10 to 5000\"], [\"Ставки повернено\", \"Ставки возвращены\", \"Stakes returned\"], [\"Сума\", \"Сумма\", \"Amount\"], [\"Сума виводу занадто мала\", \"Сумма вывода слишком мала\", \"Withdrawal amount is too small\"], [\"Сума менша за мінімальну\", \"Сумма меньше минимальной\", \"Amount is below the minimum\"], [\"Так ходити не можна\", \"Так ходить нельзя\", \"Illegal move\"], [\"Твій хід\", \"Твой ход\", \"Your turn\"], [\"Ти\", \"Ты\", \"You\"], [\"Ти вже в матчі\", \"Ты уже в матче\", \"You're already in a match\"], [\"Три в ряд. На хід 25 секунд.\", \"Три в ряд. На ход 25 секунд.\", \"Three in a row. 25 seconds per move.\"], [\"У кого карта старша, той і виграв.\", \"У кого карта старше, тот и выиграл.\", \"Highest card wins.\"], [\"Хрестики\", \"Крестики\", \"Tic-tac-toe\"], [\"Хрестики-нулики\", \"Крестики-нолики\", \"Tic-tac-toe\"], [\"Хід суперника\", \"Ход соперника\", \"Opponent's turn\"], [\"Це твій виклик\", \"Это твой вызов\", \"This is your challenge\"], [\"Час на хід вийшов\", \"Время на ход вышло\", \"Time's up\"], [\"Чекаємо, поки суперник обере…\", \"Ждём, пока соперник выберет…\", \"Waiting for the opponent to choose…\"], [\"Чистий виграш\", \"Чистый выигрыш\", \"Net win\"], [\"Я ОПЛАТИВ\", \"Я ОПЛАТИЛ\", \"I'VE PAID\"], [\"в обробці\", \"в обработке\", \"processing\"], [\"відхилено, монети повернено\", \"отклонено, монеты возвращены\", \"rejected, coins returned\"], [\"зараховано\", \"зачислено\", \"credited\"], [\"камінь-ножиці · хрестики · карти\", \"камень-ножницы · крестики · карты\", \"rock-paper-scissors · tic-tac-toe · cards\"], [\"монет (1\", \"монет (1\", \"coins (1\"], [\"монет)\", \"монет)\", \"coins)\"], [\"монет, рейк\", \"монет, рейк\", \"coins, rake\"], [\"монет. Отримаєш\", \"монет. Получишь\", \"coins. You'll get\"], [\"на перевірці\", \"на проверке\", \"under review\"], [\"нічия\", \"ничья\", \"draw\"], [\"очікує оплати\", \"ожидает оплаты\", \"awaiting payment\"], [\"проти\", \"против\", \"vs\"], [\"🛠 Адмін\", \"🛠 Админ\", \"🛠 Admin\"], [\"Запроси друзів\", \"Пригласи друзей\", \"Invite friends\"], [\"Отримуй частку з кожної їхньої гри\", \"Получай долю с каждой их игры\", \"Earn a share of every game they play\"], [\"Друг заходить за твоїм посиланням і грає. З кожної гри казино бере комісію, а ти отримуєш\", \"Друг заходит по твоей ссылке и играет. С каждой игры казино берёт комиссию, а ты получаешь\", \"Your friend joins via your link and plays. The casino takes a commission from every game, and you get\"], [\"від неї. Друг платить стільки ж, скільки й усі: твоя частка йде з комісії казино, назавжди.\", \"от неё. Друг платит столько же, сколько и все: твоя доля идёт из комиссии казино, навсегда.\", \"of it. Your friend pays the same as everyone else: your share comes out of the casino's commission, forever.\"], [\"Твоє посилання\", \"Твоя ссылка\", \"Your link\"], [\"Скопіювати\", \"Скопировать\", \"Copy\"], [\"Поділитись\", \"Поделиться\", \"Share\"], [\"Посилання скопійовано\", \"Ссылка скопирована\", \"Link copied\"], [\"Друзів\", \"Друзей\", \"Friends\"], [\"Грають\", \"Играют\", \"Playing\"], [\"Твій заробіток\", \"Твой заработок\", \"Your earnings\"], [\"Твої друзі\", \"Твои друзья\", \"Your friends\"], [\"Поки нікого. Поділись посиланням.\", \"Пока никого. Поделись ссылкой.\", \"Nobody yet. Share your link.\"], [\"Друг має вперше відкрити бота саме за твоїм посиланням. Бонус приходить автоматично після кожної його гри.\", \"Друг должен впервые открыть бота именно по твоей ссылке. Бонус приходит автоматически после каждой его игры.\", \"Your friend must open the bot for the first time through your link. The bonus arrives automatically after each of their games.\"], [\"Грай у Pixel Casino: покер, дуелі та джекпот проти живих гравців 🎰\", \"Играй в Pixel Casino: покер, дуэли и джекпот против живых игроков 🎰\", \"Play Pixel Casino: poker, duels and jackpot against real players 🎰\"], [\"Посилання поки недоступне, спробуй пізніше\", \"Ссылка пока недоступна, попробуй позже\", \"The link isn't available yet, try again later\"], [\"Не вдалося завантажити\", \"Не удалось загрузить\", \"Failed to load\"]],KIND={\"ref_bonus\": [\"Реферальний бонус\", \"Реферальный бонус\", \"Referral bonus\"], \"dep_card\": [\"Поповнення карткою\", \"Пополнение картой\", \"Card deposit\"], \"mx_win\": [\"Виграш 1 на 1\", \"Выигрыш 1 на 1\", \"1v1 win\"], \"mx_stake\": [\"Ставка 1 на 1\", \"Ставка 1 на 1\", \"1v1 stake\"], \"mx_refund\": [\"Повернення ставки\", \"Возврат ставки\", \"Stake refund\"], \"mx_refund_c\": [\"Повернення ставки\", \"Возврат ставки\", \"Stake refund\"], \"refund_mx\": [\"Повернення (перезапуск)\", \"Возврат (перезапуск)\", \"Refund (restart)\"]};\nfunction ls(k,v){try{if(v===undefined)return localStorage.getItem(k);localStorage.setItem(k,v)}catch(e){return null}}\nvar LANGS=[\"uk\",\"ru\",\"en\"],lang=ls(\"lang\");\nif(LANGS.indexOf(lang)<0){var cc=((T&&T.initDataUnsafe&&T.initDataUnsafe.user&&T.initDataUnsafe.user.language_code)||\"uk\").slice(0,2).toLowerCase();lang=cc===\"uk\"?\"uk\":(cc===\"ru\"||cc===\"be\")?\"ru\":\"en\"}\nvar CY=/[А-Яа-яІіЇїЄєҐґ]/,LET=\"А-Яа-яІіЇїЄєҐґ'ʼ’\";\nfunction rxEsc(s){return s.replace(/[.*+?^${}()|[\\]\\\\]/g,\"\\\\$&\")}\nvar CACHE={};\nfunction build(i){\n  var m={},ks=[];\n  D.forEach(function(r){m[r[0]]=r[i];ks.push(r[0])});\n  ks.sort(function(a,b){return b.length-a.length});\n  return {m:m,re:new RegExp(\"(^|[^\"+LET+\"])(\"+ks.map(rxEsc).join(\"|\")+\")(?![А-Яа-яІіЇїЄєҐґ'ʼ’])\",\"g\")};\n}\nfunction tr(s){\n  if(!s)return s;\n  var t=s.trim(),k=KIND[t];\n  if(k)return s.replace(t,k[LANGS.indexOf(lang)]);\n  if(lang===\"uk\"||!CY.test(s))return s;\n  var c=CACHE[lang]||(CACHE[lang]=build(LANGS.indexOf(lang)==1?1:2));\n  c.re.lastIndex=0;\n  return s.replace(c.re,function(a,p,w){var r=c.m[w];return p+(r===undefined?w:r)});\n}\nwindow.__tr=tr;\nvar ORIG=new WeakMap(),OURS=new WeakMap(),AO=new WeakMap();\nfunction doText(n){\n  var cur=n.nodeValue,o=ORIG.get(n),ours=OURS.get(n);\n  if(o===undefined||cur!==ours){o=cur;ORIG.set(n,o)}\n  var t=tr(o);\n  OURS.set(n,t);\n  if(t!==cur)n.nodeValue=t;\n}\nvar ATTRS=[\"placeholder\",\"title\",\"aria-label\",\"alt\"];\nfunction doAttr(el,a){\n  var v=el.getAttribute(a);if(v===null)return;\n  var st=AO.get(el);if(!st){st={};AO.set(el,st)}\n  var s=st[a];\n  if(!s||v!==s.ours){s={o:v,ours:v};st[a]=s}\n  var t=tr(s.o);s.ours=t;\n  if(t!==v)el.setAttribute(a,t);\n}\nfunction walk(root){\n  if(!root)return;\n  if(root.nodeType===3){if(root.parentNode&&/^(SCRIPT|STYLE|TEXTAREA)$/.test(root.parentNode.nodeName))return;doText(root);return}\n  if(root.nodeType!==1)return;\n  if(/^(SCRIPT|STYLE)$/.test(root.nodeName))return;\n  var w=document.createTreeWalker(root,5,null),n=w.currentNode;\n  var list=[];\n  while(n){list.push(n);n=w.nextNode()}\n  list.forEach(function(x){\n    if(x.nodeType===3){var p=x.parentNode;if(p&&/^(SCRIPT|STYLE|TEXTAREA)$/.test(p.nodeName))return;doText(x)}\n    else ATTRS.forEach(function(a){if(x.hasAttribute&&x.hasAttribute(a))doAttr(x,a)})\n  });\n}\nvar mo=new MutationObserver(function(ms){\n  ms.forEach(function(m){\n    if(m.type===\"characterData\")doText(m.target);\n    else if(m.type===\"attributes\")doAttr(m.target,m.attributeName);\n    else m.addedNodes.forEach(walk);\n  });\n});\nfunction start(){\n  document.documentElement.lang=lang;\n  walk(document.body);\n  mo.observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:ATTRS});\n}\nfunction setLang(l){\n  lang=l;ls(\"lang\",l);document.documentElement.lang=l;\n  walk(document.body);\n  if(chip)chip.textContent=\"🌐 \"+l.toUpperCase();\n}\ntry{\n  var oa=window.alert;window.alert=function(m){return oa.call(window,tr(String(m)))};\n  if(T){[\"showAlert\",\"showConfirm\"].forEach(function(f){var o=T[f];if(typeof o===\"function\")T[f]=function(m){var a=[].slice.call(arguments);a[0]=tr(String(m));return o.apply(T,a)}})}\n}catch(e){}\nvar chip=document.createElement(\"button\");\nchip.style.cssText=\"display:block;margin:4px auto 16px;padding:8px 14px;border-radius:14px;border:2px solid #4a2ab0;background:#140a38;color:#27e6ff;font:400 12px 'Russo One',sans-serif;box-shadow:0 0 12px #27e6ff44\";\nchip.textContent=\"🌐 \"+lang.toUpperCase();\nchip.onclick=function(){setLang(LANGS[(LANGS.indexOf(lang)+1)%3])};\n(document.getElementById(\"v-lobby\")||document.body).appendChild(chip);\n\n/* ---------- реферальна програма ---------- */\nfunction esc(s){return String(s).replace(/[<>&\"]/g,function(c){return {\"<\":\"&lt;\",\">\":\"&gt;\",\"&\":\"&amp;\",'\"':\"&quot;\"}[c]})}\nfunction fmt(n){return Math.round(Number(n)||0).toLocaleString(\"uk-UA\")}\nfunction api(p){return fetch(p,{headers:{\"x-init-data\":(T&&T.initData)||\"\",\"x-dev-user\":ls(\"dev\")||\"\"}}).then(function(r){return r.json().then(function(j){if(!r.ok)throw new Error(j.error||\"fail\");return j})})}\nvar css=document.createElement(\"style\");\ncss.textContent=\"#rf{position:fixed;left:0;top:0;right:0;bottom:0;z-index:70;background:#07041a;background-image:radial-gradient(circle at 50% -10%,#2a0f66 0,transparent 55%);color:#f4eeff;overflow-y:auto;padding:12px 12px 40px;font:500 14px/1.4 'Exo 2',system-ui,sans-serif;display:none}\"\n+\"#rf .in{max-width:480px;margin:0 auto}#rf h2,#rf b,#rf button{font-family:'Russo One',Impact,sans-serif;font-weight:400}\"\n+\".rfh{display:flex;align-items:center;gap:10px;margin-bottom:12px}.rfh h2{flex:1;font-size:20px;text-shadow:0 0 10px #ff2fb0}\"\n+\".rfb{border:2px solid #4a2ab0;border-radius:12px;background:#140a38;color:#27e6ff;width:38px;height:38px;font-size:18px}\"\n+\".rfc{border:2px solid #4a2ab0;border-radius:14px;background:linear-gradient(#150b3c,#0d0629);padding:12px;margin-bottom:10px}\"\n+\".rfd{color:#9b8cd0;font-size:12px}\"\n+\".rfi{width:100%;box-sizing:border-box;border:2px solid #3a2a90;border-radius:10px;background:#0d0630;color:#cfe0ff;padding:10px;font:500 13px 'Exo 2',sans-serif;margin:6px 0 8px}\"\n+\".rfr{display:flex;gap:8px}.rfr button{flex:1;border:0;border-radius:12px;padding:12px;color:#fff;font-size:14px;background:linear-gradient(#7dff4d,#25c21e);box-shadow:0 2px 0 #0f7a12}\"\n+\".rfr button.s{background:linear-gradient(#3c8cff,#1c4fc4);box-shadow:0 2px 0 #0d2a7a}\"\n+\".rfs{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:10px}.rfs div{border:2px solid #4a2ab0;border-radius:12px;background:#140a38;padding:10px 4px;text-align:center}\"\n+\".rfs small{display:block;color:#9b8cd0;font-size:11px;margin-bottom:4px}.rfs b{font-size:17px;color:#ffc933}\"\n+\".rfx{display:flex;justify-content:space-between;gap:8px;padding:8px 0;border-top:1px solid #2a1a70}.rfx:first-child{border-top:0}.rfx b{color:#5dff8a}\";\ndocument.head.appendChild(css);\nvar ov=document.createElement(\"div\");ov.id=\"rf\";\nov.innerHTML='<div class=\"in\"><div class=\"rfh\"><button class=\"rfb\" id=\"rfX\">◀</button><h2>Запроси друзів</h2></div>'\n+'<div class=\"rfc\"><p style=\"margin:0\">Друг заходить за твоїм посиланням і грає. З кожної гри казино бере комісію, а ти отримуєш <b id=\"rfP\" style=\"color:#ffc933\">20%</b> від неї. Друг платить стільки ж, скільки й усі: твоя частка йде з комісії казино, назавжди.</p></div>'\n+'<div class=\"rfc\"><small class=\"rfd\">Твоє посилання</small><input class=\"rfi\" id=\"rfL\" readonly><div class=\"rfr\"><button id=\"rfC\">Скопіювати</button><button class=\"s\" id=\"rfS\">Поділитись</button></div><div class=\"rfd\" id=\"rfM\" style=\"margin-top:8px;min-height:16px\"></div></div>'\n+'<div class=\"rfs\"><div><small>Друзів</small><b id=\"rfN\">0</b></div><div><small>Грають</small><b id=\"rfA\">0</b></div><div><small>Твій заробіток</small><b id=\"rfE\">0</b></div></div>'\n+'<div class=\"rfc\"><small class=\"rfd\">Твої друзі</small><div id=\"rfF\"></div></div>'\n+'<p class=\"rfd\" style=\"padding:0 4px\">Друг має вперше відкрити бота саме за твоїм посиланням. Бонус приходить автоматично після кожної його гри.</p></div>';\ndocument.body.appendChild(ov);\nvar LINK=\"\";\nfunction msg(t){var m=document.getElementById(\"rfM\");m.textContent=t}\nfunction openRef(){\n  ov.style.display=\"block\";msg(\"\");\n  api(\"/api/ref\").then(function(j){\n    LINK=j.link||\"\";\n    document.getElementById(\"rfL\").value=LINK;\n    document.getElementById(\"rfP\").textContent=Math.round((j.pct||0)*100)+\"%\";\n    document.getElementById(\"rfN\").textContent=fmt(j.n);\n    document.getElementById(\"rfA\").textContent=fmt(j.a);\n    document.getElementById(\"rfE\").textContent=fmt(j.earned);\n    document.getElementById(\"rfF\").innerHTML=j.friends&&j.friends.length?j.friends.map(function(f){return '<div class=\"rfx\"><span>'+esc(f.name)+'</span><b>+'+fmt(f.earned)+'</b></div>'}).join(\"\"):'<div class=\"rfd\">Поки нікого. Поділись посиланням.</div>';\n    if(!LINK)msg(\"Посилання поки недоступне, спробуй пізніше\");\n  }).catch(function(){msg(\"Не вдалося завантажити\")});\n}\ndocument.getElementById(\"rfX\").onclick=function(){ov.style.display=\"none\"};\ndocument.getElementById(\"rfC\").onclick=function(){\n  if(!LINK)return;\n  var done=function(){msg(\"Посилання скопійовано\")};\n  try{navigator.clipboard.writeText(LINK).then(done,function(){var i=document.getElementById(\"rfL\");i.select();document.execCommand(\"copy\");done()})}catch(e){var i=document.getElementById(\"rfL\");i.select();try{document.execCommand(\"copy\")}catch(_){}done()}\n};\ndocument.getElementById(\"rfS\").onclick=function(){\n  if(!LINK)return;\n  var u=\"https://t.me/share/url?url=\"+encodeURIComponent(LINK)+\"&text=\"+encodeURIComponent(tr(\"Грай у Pixel Casino: покер, дуелі та джекпот проти живих гравців 🎰\"));\n  if(T&&T.openTelegramLink)T.openTelegramLink(u);else window.open(u,\"_blank\");\n};\nfunction addPromo(){\n  var pr=document.querySelector(\".promo\");\n  if(!pr||document.getElementById(\"refBtn\"))return !!document.getElementById(\"refBtn\");\n  var b=document.createElement(\"button\");b.className=\"pcard\";b.id=\"refBtn\";\n  b.innerHTML='<span style=\"font-size:26px;line-height:1\">👥</span><div><b>Запроси друзів</b><small>Отримуй частку з кожної їхньої гри</small></div>';\n  b.onclick=openRef;\n  pr.insertBefore(b,pr.firstChild);\n  return true;\n}\nif(!addPromo()){var pi=setInterval(function(){if(addPromo())clearInterval(pi)},400)}\nstart();\n}catch(e){console.error(\"i18n\",e)}})();\n</script>";

const STAR_PACKS = [25, 50, 100, 250, 500, 1000];
async function handle(req: Request) {
  const url = new URL(req.url).pathname;
  if (url === "/health") return json({ ok: true });
  if (url === "/admin") return new Response(ADMIN_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache" } });
  if (url.startsWith("/a/")) return serveAsset(url.slice(3));
  if (!url.startsWith("/api/")) return new Response((globalThis as any).__WEB || HTML.replace("gain.value=0.11,", "gain.value=0,").replace("</body>", () => INJECT + ((globalThis as any).__FX || "") + "</body>"), { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache" } });
  { const ac = await adsCb(url, req); if (ac) return ac; const oc = await offerCb(url, req); if (oc) return oc; }
  const u = await authFrom(req.headers.get("x-init-data") || "", req.headers.get("x-dev-user") || "");
  if (!u) return json({ error: "unauthorized" }, 401);
  let body: any = {}; if (req.method === "POST") { try { body = await req.json(); } catch { body = {}; } }
  const numId = u.id.startsWith("tg") ? u.id.slice(2) : "dev";
  { const sr = await supApi(url, req, u, body); if (sr) return sr; }
  { const ar = await adsApi(url, req, u, body); if (ar) return ar; }

  if (url === "/api/me") return json({
    id: u.id, name: u.name, balance: N(u.balance), ageOk: u.age_ok, supporter: u.supporter, bot: BOT_USER, admin: isAdmin(u.id),
    cfg: { starCoins: STAR_COINS, tonCoins: TON_IN, tonOut: TON_COINS, rake: RAKE, refPct: REF_PCT, wdFee: WD_FEE, wdFeeMin: WD_FEE_MIN, wdMin: WD_MIN, tonAddr: TON_ADDR, comment: "dp" + numId, packs: STAR_PACKS, minBet: MIN_BET, maxBet: MAX_BET, closed: CLOSED, wdOff: WD_OFF, wdUntil: WD_UNTIL, wdMinNow: WD_MIN_NOW },
    turnover: N(u.turnover), deposited: N(u.deposited),
  });
  if (url === "/api/age" && req.method === "POST") { await db`update users set age_ok = true where id = ${u.id}`; return json({ ok: true }); }

  if (url === "/api/wallet") {
    const led = await db`select kind, amount, note, created from ledger where user_id = ${u.id} and kind not in ('duel_stake','duel_refund','jp_bet') order by id desc limit 30`;
    const wds = await db`select id, coins, fee, ton, status, addr, created from withdrawals where user_id = ${u.id} order by id desc limit 10`;
    return json({ ledger: led.map((r: any) => ({ kind: r.kind, amount: N(r.amount), note: r.note, t: r.created })), withdrawals: wds.map((r: any) => ({ id: N(r.id), coins: N(r.coins), fee: N(r.fee), ton: N(r.ton), status: r.status, addr: r.addr, t: r.created })), turnover: N(u.turnover), deposited: N(u.deposited) });
  }

  if (url === "/api/ref") {
    const c = await db`select count(*)::int as n, (count(*) filter (where turnover > 0))::int as a from users where ref_by = ${u.id}`;
    const e = await db`select coalesce(sum(amount),0) as s from ledger where user_id = ${u.id} and kind = 'ref_bonus'`;
    const f = await db`select x.name, coalesce(sum(l.amount),0) as s from users x left join ledger l on l.kind = 'ref_bonus' and l.user_id = ${u.id} and l.note = x.id where x.ref_by = ${u.id} group by x.id, x.name, x.created order by s desc, x.created desc limit 15`;
    return json({ link: BOT_USER ? `https://t.me/${BOT_USER}?start=r${numId}` : "", pct: REF_PCT, n: N(c[0].n), a: N(c[0].a), earned: N(e[0].s), friends: f.map((r: any) => ({ name: r.name, earned: N(r.s) })) });
  }

  if (url === "/api/stars/invoice" && req.method === "POST") {
    if (CLOSED) return err("closed");
    const donate = !!body.donate; const stars = Math.floor(Number(body.stars));
    if (!BOT_TOKEN) return err("no_bot");
    if (!Number.isFinite(stars) || stars < (donate ? 1 : 25) || stars > 10000) return err("bad_amount");
    if (!donate && !u.age_ok) return err("age");
    const r = await tg("createInvoiceLink", {
      title: donate ? "Підтримка проєкту" : "Поповнення монет", description: donate ? "Дякуємо, що підтримуєш Pixel Casino" : `${stars * STAR_COINS} монет на ігровий баланс`,
      payload: (donate ? "don:" : "dep:") + u.id, provider_token: "", currency: "XTR", prices: [{ label: donate ? "Підтримка" : "Монети", amount: stars }],
    });
    if (!r.ok) return err("invoice_failed", { detail: r.description });
    return json({ url: r.result });
  }

  if (url.startsWith("/api/card/")) {
    if (url === "/api/card/list") {
      const deps = await db`select id, cur, amount, coins, status from card_dep where user_id = ${u.id} order by id desc limit 8`;
      const wds = await db`select id, cur, amount, status from card_wd where user_id = ${u.id} order by id desc limit 8`;
      return json({ card: CARD_NUM, cardFmt: grp4(CARD_NUM), info: cardInfo(), deps: deps.map((r: any) => ({ id: N(r.id), cur: r.cur, amount: N(r.amount), coins: N(r.coins), status: r.status })), wds: wds.map((r: any) => ({ id: N(r.id), cur: r.cur, amount: N(r.amount), status: r.status })) });
    }
    if (url === "/api/card/dep" && req.method === "POST") {
      if (CLOSED) return err("closed");
      const cur = String(body.cur || ""); const amount = Math.round(Number(body.amount) * 100) / 100;
      if (!CARD_RATE[cur]) return err("bad_cur");
      if (!Number.isFinite(amount) || amount <= 0 || amount > CARD_MAX) return err("bad_amount");
      if (amount < CARD_MIN[cur]) return err("min_amount");
      const open = await db`select count(*)::int as c from card_dep where user_id = ${u.id} and status in ('new','claimed')`;
      if (N(open[0].c) >= 3) return err("too_many");
      const coins = Math.floor(amount * CARD_IN[cur]);
      const r = await db`insert into card_dep(user_id, cur, amount, coins) values(${u.id}, ${cur}, ${amount}, ${coins}) returning id`;
      return json({ ok: true, id: N(r[0].id) });
    }
    if (url === "/api/card/claim" && req.method === "POST") {
      const id = Math.floor(Number(body.id));
      const r = await db`update card_dep set status = 'claimed' where id = ${id} and user_id = ${u.id} and status = 'new' returning cur, amount, coins`;
      if (!r.length) return err("gone");
      adminAsk(`💳 Поповнення карткою #C${id}\nГравець: ${u.name} (${u.id})\nСума: ${N(r[0].amount).toFixed(2)} ${r[0].cur} (${N(r[0].coins)} монет)\n\nПеревір, що гроші на картці, і підтверди (або в 🛠 Адмін-панелі, вкладка «Картка»).`, "cd", id);
      return json({ ok: true });
    }
    if (url === "/api/card/wd" && req.method === "POST") {
      if (WD_OFF) return err("closed");
      if (!u.age_ok) return err("age");
      const cur = String(body.cur || ""); const coins = Math.floor(Number(body.coins)); const card = String(body.card || "").replace(/\D/g, "");
      if (!CARD_RATE[cur]) return err("bad_cur");
      if (!Number.isFinite(coins) || coins < WD_MIN_NOW) return err("wd_min", { min: WD_MIN_NOW });
      if (card.length < 16 || card.length > 19 || !luhn(card)) return err("bad_card");
      if (!CLOSED && N(u.turnover) < N(u.deposited)) return err("need_turnover", { need: N(u.deposited) - N(u.turnover) });
      const pend = await db`select count(*)::int as c from card_wd where user_id = ${u.id} and status = 'pending'`;
      if (N(pend[0].c) >= 3) return err("too_many");
      const fee = Math.max(WD_FEE_MIN, Math.ceil(coins * WD_FEE)); const amount = Math.floor(((coins - fee) / CARD_RATE[cur]) * 100) / 100;
      if (amount <= 0) return err("tiny");
      let id = 0;
      try {
        await db.begin(async (t: any) => {
          await adj(t, u.id, -coins, "withdraw", null, "card " + card.slice(-4));
          const r = await t`insert into card_wd(user_id, card, cur, amount, coins, fee) values(${u.id}, ${card}, ${cur}, ${amount}, ${coins}, ${fee}) returning id`;
          id = N(r[0].id);
          await adj(t, "house", fee, "wd_fee", "cw" + id, u.id);
        });
      } catch (e: any) { return err(e.message === "no_funds" ? "no_funds" : "fail"); }
      adminAsk(`💸 Вивід на картку #${id}\nГравець: ${u.name} (${u.id})\nСума: ${amount.toFixed(2)} ${cur} (комісія ${fee} монет)\nКартка: ${grp4(card)}\n\nПерекажи гроші й підтверди (або в 🛠 Адмін-панелі, вкладка «Картка»).`, "cw", id);
      pushBal(u.id);
      return json({ ok: true, id, amount });
    }
    return json({ error: "not_found" }, 404);
  }

  if (url === "/api/withdraw" && req.method === "POST") {
    if (WD_OFF) return err("closed");
    if (!u.age_ok) return err("age");
    const coins = Math.floor(Number(body.coins)); const addr = String(body.addr || "").trim();
    if (!Number.isFinite(coins) || coins < WD_MIN_NOW) return err("wd_min", { min: WD_MIN_NOW });
    if (!/^(UQ|EQ|kQ|0Q)[A-Za-z0-9_-]{46}$/.test(addr)) return err("bad_addr");
    if (!CLOSED && N(u.turnover) < N(u.deposited)) return err("need_turnover", { need: N(u.deposited) - N(u.turnover) });
    const pend = await db`select count(*)::int as c from withdrawals where user_id = ${u.id} and status = 'pending'`;
    if (N(pend[0].c) >= 3) return err("too_many");
    const fee = Math.max(WD_FEE_MIN, Math.ceil(coins * WD_FEE)); const ton = (coins - fee) / TON_COINS;
    let id = 0;
    try {
      await db.begin(async (t: any) => {
        await adj(t, u.id, -coins, "withdraw", null, addr);
        const r = await t`insert into withdrawals(user_id, addr, coins, fee, ton) values(${u.id}, ${addr}, ${coins}, ${fee}, ${ton}) returning id`;
        id = N(r[0].id);
        await adj(t, "house", fee, "wd_fee", "wd" + id, u.id);
      });
    } catch (e: any) { return err(e.message === "no_funds" ? "no_funds" : "fail"); }
    dispatchWd(id, ton, `${u.name} (${u.id})`, fee, addr).catch((e: any) => console.error("dispatch", e.message));
    pushBal(u.id);
    return json({ ok: true, id, ton, fee, balance: await getBal(u.id) });
  }

  if (url.startsWith("/api/admin/")) {
    if (!isAdmin(u.id)) return json({ error: "forbidden" }, 403);
    if (url === "/api/admin/stats") {
      const [h, c, w, hd, dp, pd, dy] = await Promise.all([getBal("house"), db`select count(*)::int as c from users where id <> 'house'`, db`select count(*)::int as c, coalesce(sum(coins - fee),0) as s from withdrawals where status = 'pending'`, db`select coalesce(sum(amount),0) as s from holds`,
        db`select coalesce(sum(deposited),0) as s from users`, db`select coalesce(sum(ton),0) as s from withdrawals where status = 'paid'`, db`select coalesce(sum(amount),0) as s from ledger where user_id = 'house' and created > now() - interval '1 day'`]);
      return json({ house: h, users: N(c[0].c), pendingWd: N(w[0].c), pendingCoins: N(w[0].s), held: N(hd[0].s), deposited: N(dp[0].s), paidTon: N(pd[0].s), day: N(dy[0].s), online: new Set([...socks].map((x) => x.data.uid)).size, tonCoins: TON_COINS, pendingCard: N((await db`select (select count(*) from card_dep where status = 'claimed') + (select count(*) from card_wd where status = 'pending') as c`)[0].c), payer: payer ? { addr: payer.addr, balance: await payer.balance().then((b) => Number(b) / 1e9).catch(() => null), autoOn, max: AUTO_MAX, day: AUTO_DAY } : null });
    }
    if (url === "/api/admin/withdrawals") {
      const rows = await db`select w.id, w.user_id, w.addr, w.coins, w.fee, w.ton, w.status, w.created, x.name from withdrawals w left join users x on x.id = w.user_id order by (w.status = 'pending') desc, w.id desc limit 60`;
      return json({ items: rows.map((r: any) => ({ id: N(r.id), user: r.user_id, name: r.name, addr: r.addr, coins: N(r.coins), fee: N(r.fee), ton: N(r.ton), status: r.status, created: r.created })) });
    }
    if (url === "/api/admin/wd" && req.method === "POST") {
      if (body.action === "auto") { const r = await autoPay(Number(body.id)); return json({ done: r.ok, error: r.error }); }
      return json({ done: await wdDecide(Number(body.id), body.action === "ok") });
    }
    if (url === "/api/admin/auto" && req.method === "POST") {
      if (!payer) return err("no_wallet");
      autoOn = !!body.on;
      await db`insert into parts(name, body) values('cfg_auto', ${autoOn ? "1" : "0"}) on conflict (name) do update set body = excluded.body`;
      return json({ ok: true, autoOn });
    }
    if (url === "/api/admin/card") {
      if (req.method === "POST") {
        const id = Math.floor(Number(body.id)); const ok = body.action === "ok";
        return json({ done: body.kind === "dep" ? await cardDepDecide(id, ok) : await cardWdDecide(id, ok) });
      }
      const deps = await db`select d.id, d.user_id, d.cur, d.amount, d.coins, d.status, d.created, x.name from card_dep d left join users x on x.id = d.user_id order by (d.status in ('new','claimed')) desc, d.id desc limit 40`;
      const wds = await db`select w.id, w.user_id, w.card, w.cur, w.amount, w.coins, w.fee, w.status, w.created, x.name from card_wd w left join users x on x.id = w.user_id order by (w.status = 'pending') desc, w.id desc limit 40`;
      return json({ deps: deps.map((r: any) => ({ id: N(r.id), user: r.user_id, name: r.name, cur: r.cur, amount: N(r.amount), coins: N(r.coins), status: r.status, created: r.created })),
        wds: wds.map((r: any) => ({ id: N(r.id), user: r.user_id, name: r.name, card: grp4(r.card), cur: r.cur, amount: N(r.amount), coins: N(r.coins), fee: N(r.fee), status: r.status, created: r.created })) });
    }
    if (url === "/api/admin/users") {
      const q = (new URL(req.url).searchParams.get("q") || "").trim().slice(0, 40);
      const rows = q
        ? await db`select id, name, balance, deposited, turnover, banned, supporter from users where id <> 'house' and (name ilike ${"%" + q + "%"} or id = ${q} or id = ${"tg" + q}) order by balance desc limit 30`
        : await db`select id, name, balance, deposited, turnover, banned, supporter from users where id <> 'house' order by created desc limit 30`;
      return json({ items: rows.map((r: any) => ({ id: r.id, name: r.name, balance: N(r.balance), deposited: N(r.deposited), turnover: N(r.turnover), banned: r.banned, supporter: r.supporter })) });
    }
    if (url === "/api/admin/grant" && req.method === "POST") {
      const id = String(body.id || ""); const coins = Math.trunc(Number(body.coins));
      if (!Number.isFinite(coins) || coins === 0 || id === "house") return err("bad_amount");
      if (!(await getUserRow(id))) return err("gone");
      try { await db.begin((t: any) => adj(t, id, coins, "grant", null, "admin")); } catch (e: any) { return err(e.message === "no_funds" ? "no_funds" : "fail"); }
      pushBal(id);
      return json({ ok: true, balance: await getBal(id) });
    }
    if (url === "/api/admin/ban" && req.method === "POST") {
      const id = String(body.id || ""); const on = !!body.banned;
      if (id === "house" || id === u.id) return err("bad_amount");
      await db`update users set banned = ${on} where id = ${id}`;
      if (on) for (const w of [...socks]) if (w.data.uid === id) { try { w.close(); } catch {} }
      return json({ ok: true });
    }
    return json({ error: "not_found" }, 404);
  }
  return json({ error: "not_found" }, 404);
}

// ---------- Telegram ----------
async function tg(method: string, payload: any = {}) {
  const r = await fetch("https://api.telegram.org/bot" + BOT_TOKEN + "/" + method, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(payload) });
  return r.json() as any;
}
async function onPayment(m: any) {
  const sp = m.successful_payment; const uid = String(sp.invoice_payload || "").slice(4); const kind = String(sp.invoice_payload || "").slice(0, 3);
  const stars = N(sp.total_amount); const ref = sp.telegram_payment_charge_id;
  const ex = await getUserRow(uid); if (!ex) return;
  if (kind === "dep") {
    const coins = stars * STAR_COINS;
    const ok = await db.begin(async (t: any) => { const r = await adj(t, uid, coins, "dep_stars", ref, stars + " ⭐"); if (r) await t`update users set deposited = deposited + ${coins} where id = ${uid}`; return r; });
    if (ok) { pushBal(uid); tg("sendMessage", { chat_id: m.chat.id, text: `✅ Поповнено: +${coins} монет (${stars} ⭐)` }).catch(() => {}); }
  } else if (kind === "don") {
    const ok = await db.begin(async (t: any) => { const r = await t`insert into ledger(user_id, kind, amount, ref, note) values(${uid}, 'donate', 0, ${ref}, ${stars + " ⭐"}) on conflict (kind, ref) do nothing returning id`; if (r.length) await t`update users set supporter = true where id = ${uid}`; return r.length > 0; });
    if (ok) tg("sendMessage", { chat_id: m.chat.id, text: `💜 Дякуємо за підтримку проєкту! (${stars} ⭐)` }).catch(() => {});
  }
}
async function onCommand(m: any) {
  const from = "tg" + m.from.id; const text: string = m.text; const chat = m.chat.id;
  if (text.startsWith("/start")) {
    const ref = (text.split(" ")[1] || "").replace(/[^\w-]/g, "");
    const lc = m.from.language_code;
    const rm = /^r(\d{4,15})$/.exec(ref);
    if (rm) { const nm = String(m.from.first_name || m.from.username || "Player").slice(0, 24); await db`insert into users(id, name, lang) values(${from}, ${nm}, ${String(lc || "").slice(0, 5)}) on conflict (id) do nothing`; await setRef(from, "tg" + rm[1], nm); }
    if (CLOSED) { await tg("sendMessage", { chat_id: chat, text: L3(lc,
      "🎰 Pixel Casino закрито: проєкт виявився неприбутковим, тому ми його зупиняємо. Дякуємо всім, хто грав!" + (WD_OFF ? "" : "\n\n💰 Твої монети нікуди не зникли: відкрий гру → Гаманець → Вивід і виведи залишок" + (WD_UNTIL ? " до " + WD_UNTIL : "") + ". Поповнення і нові ігри вимкнено. Питання — у техпідтримку в грі."),
      "🎰 Pixel Casino закрыт: проект оказался неприбыльным, поэтому мы его останавливаем. Спасибо всем, кто играл!" + (WD_OFF ? "" : "\n\n💰 Твои монеты никуда не делись: открой игру → Кошелёк → Вывод и выведи остаток" + (WD_UNTIL ? " до " + WD_UNTIL : "") + ". Пополнения и новые игры отключены. Вопросы — в техподдержку в игре."),
      "🎰 Pixel Casino is closed: the project turned out to be unprofitable, so we are shutting it down. Thank you all for playing!" + (WD_OFF ? "" : "\n\n💰 Your coins are safe: open the game → Wallet → Withdraw and cash out your balance" + (WD_UNTIL ? " by " + WD_UNTIL : "") + ". Deposits and new games are off. Questions — support in the game.")),
      reply_markup: WEBAPP_URL && !WD_OFF ? { inline_keyboard: [[{ text: "💰 Вивести / Вывести / Withdraw", web_app: { url: WEBAPP_URL } }]] } : undefined }); return; }
    await tg("sendMessage", { chat_id: chat, text: L3(lc, "🎰 Pixel Casino — онлайн-казино, де ти граєш проти інших гравців: покер, дуелі, джекпот.\nКазино не грає проти тебе, воно бере лише комісію з банку. Лише 18+.", "🎰 Pixel Casino — онлайн-казино, где ты играешь против других игроков: покер, дуэли, джекпот.\nКазино не играет против тебя, оно берёт только комиссию с банка. Только 18+.", "🎰 Pixel Casino — an online casino where you play against other players: poker, duels, jackpot.\nThe casino doesn't play against you, it only takes a commission from the pot. 18+ only."), reply_markup: { inline_keyboard: [[{ text: L3(lc, "▶ ГРАТИ", "▶ ИГРАТЬ", "▶ PLAY"), web_app: { url: WEBAPP_URL + "/" + (ref ? "?ref=" + ref : "") } }], ...(isAdmin(from) ? [[{ text: "🛠 Адмін-панель", web_app: { url: WEBAPP_URL + "/admin" } }]] : [])] } });
    return;
  }
  if (!isAdmin(from)) return;
  if (text.startsWith("/grant")) {
    const [, id, amt] = text.split(/\s+/); const coins = Math.floor(Number(amt)); const uid = id && id.startsWith("tg") ? id : "tg" + id;
    const ex = await getUserRow(uid); if (!ex || !Number.isFinite(coins) || coins === 0) return void tg("sendMessage", { chat_id: chat, text: "Формат: /grant <telegram_id> <монети>. Гравець має хоч раз відкрити гру." });
    await db.begin((t: any) => adj(t, uid, coins, "grant", null, "admin")); pushBal(uid);
    tg("sendMessage", { chat_id: chat, text: `Готово: ${uid} ${coins > 0 ? "+" : ""}${coins}` });
  } else if (text.startsWith("/stats")) {
    const [h, c, w, hd] = await Promise.all([getBal("house"), db`select count(*)::int as c from users where id <> 'house'`, db`select count(*)::int as c from withdrawals where status = 'pending'`, db`select coalesce(sum(amount),0) as s from holds`]);
    tg("sendMessage", { chat_id: chat, text: `📊 Дохід казино (рейк+комісії): ${h} монет ≈ ${(h / TON_COINS).toFixed(2)} TON\nГравців: ${N(c[0].c)}\nВиводів в очікуванні: ${N(w[0].c)}\nМонет на столах/в іграх: ${N(hd[0].s)}` });
  } else if (text.startsWith("/pending")) {
    const ws = await db`select * from withdrawals where status = 'pending' order by id`;
    if (!ws.length) return void tg("sendMessage", { chat_id: chat, text: "Немає виводів в очікуванні" });
    for (const w of ws) tg("sendMessage", { chat_id: chat, text: `💸 #${w.id}: ${N(w.ton)} TON → ${w.addr}\nГравець ${w.user_id}`, reply_markup: { inline_keyboard: [[{ text: "✅ Виплачено", callback_data: "wd:ok:" + w.id }, { text: "❌ Відхилити", callback_data: "wd:no:" + w.id }]] } });
  }
}
// ---------- Гаманець автовиплат ----------
type Payer = { addr: string; balance: () => Promise<bigint>; send: (to: string, ton: number, comment: string) => Promise<void> };
let payer: Payer | null = null;
async function initPayer() {
  if (!WALLET_MNEMONIC) return;
  try {
    const kp = await mnemonicToPrivateKey(WALLET_MNEMONIC.trim().split(/\s+/));
    const client = new TonClient({ endpoint: "https://toncenter.com/api/v2/jsonRPC", apiKey: env.TONCENTER_KEY || undefined });
    const wallet: any = WALLET_VER === "v4" ? WalletContractV4.create({ publicKey: kp.publicKey, workchain: 0 }) : WalletContractV5R1.create({ publicKey: kp.publicKey, workchain: 0 });
    const c: any = client.open(wallet);
    payer = {
      addr: wallet.address.toString({ bounceable: false }),
      balance: () => c.getBalance(),
      send: async (to, ton, comment) => {
        const seqno: number = await c.getSeqno();
        await c.sendTransfer({ seqno, secretKey: kp.secretKey, sendMode: SendMode.PAY_GAS_SEPARATELY | SendMode.IGNORE_ERRORS, messages: [internal({ to: Address.parse(to), value: toNano(ton.toFixed(9)), body: comment, bounce: false })] });
        for (let i = 0; i < 30; i++) { await new Promise((r) => setTimeout(r, 3000)); try { if ((await c.getSeqno()) > seqno) return; } catch {} }
        throw new Error("unconfirmed");
      },
    };
    console.log("Payer wallet", payer.addr);
  } catch (e: any) { console.error("payer", e.message); }
}
const alertAdmin = (text: string) => { if (BOT_TOKEN) tg("sendMessage", { chat_id: ADMIN_ID, text }).catch(() => {}); };
let payChain: Promise<any> = Promise.resolve();
const autoPay = (id: number): Promise<{ ok: boolean; error?: string }> => (payChain = payChain.then(() => autoPay1(id)).catch(() => ({ ok: false, error: "fail" })));
async function autoPay1(id: number): Promise<{ ok: boolean; error?: string }> {
  if (!payer) return { ok: false, error: "no_wallet" };
  const w = (await db`select id, user_id, addr, ton, status from withdrawals where id = ${id}`)[0];
  if (!w || w.status !== "pending") return { ok: false, error: "gone" };
  if (!/^(UQ|EQ)[A-Za-z0-9_-]{46}$/.test(w.addr)) return { ok: false, error: "manual_addr" };
  const ton = N(w.ton);
  let bal = 0n; try { bal = await payer.balance(); } catch { return { ok: false, error: "net" }; }
  if (bal < BigInt(Math.round(ton * 1e9)) + 50_000_000n) return { ok: false, error: "low_balance" };
  const cl = await db`update withdrawals set status = 'sending' where id = ${id} and status = 'pending' returning id`;
  if (!cl.length) return { ok: false, error: "gone" };
  try { await payer.send(w.addr, ton, "Pixel Casino #" + id); }
  catch (e: any) {
    await db`update withdrawals set status = 'review' where id = ${id}`;
    alertAdmin(`⚠️ Вивід #${id} (${ton} TON): автовиплата не підтвердилась (${e.message}). Перевір гаманець автовиплат: якщо TON пішов, познач «Виплачено», якщо ні, «Відхилити».`);
    return { ok: false, error: "review" };
  }
  await db`update withdrawals set status = 'paid' where id = ${id}`;
  if (BOT_TOKEN && w.user_id.startsWith("tg")) tg("sendMessage", { chat_id: w.user_id.slice(2), text: `✅ Вивід #${id} виконано: ${ton} TON відправлено.` }).catch(() => {});
  return { ok: true };
}
async function dispatchWd(id: number, ton: number, who: string, fee: number, addr: string) {
  const manual = () => { if (BOT_TOKEN) tg("sendMessage", { chat_id: ADMIN_ID, text: `💸 Запит на вивід #${id}\nГравець: ${who}\nСума: ${ton} TON (комісія ${fee} монет)\nАдреса: ${addr}\n\nОплата й підтвердження: 🛠 Адмін-панель`, reply_markup: { inline_keyboard: [[{ text: "✅ Виплачено", callback_data: "wd:ok:" + id }, { text: "❌ Відхилити", callback_data: "wd:no:" + id }]] } }).catch(() => {}); };
  if (!(autoOn && payer && ton <= AUTO_MAX)) return manual();
  const day = N((await db`select coalesce(sum(ton),0) as s from withdrawals where status in ('paid','sending','review') and created > now() - interval '1 day'`)[0].s);
  if (day + ton > AUTO_DAY) return manual();
  const r = await autoPay(id);
  if (r.ok) alertAdmin(`✅ Автовиплата #${id}: ${ton} TON → ${who}`);
  else if (r.error !== "review") { alertAdmin(`ℹ️ Автовиплата #${id} не вийшла (${r.error}), потрібна ручна виплата.`); manual(); }
}

// ---------- Картка (USD/EUR, ручне підтвердження) ----------
const luhn = (n: string) => { let sum = 0, alt = false; for (let i = n.length - 1; i >= 0; i--) { let d = Number(n[i]); if (alt) { d *= 2; if (d > 9) d -= 9; } sum += d; alt = !alt; } return sum % 10 === 0; };
const grp4 = (n: string) => n.replace(/(.{4})/g, "$1 ").trim();
const cardInfo = () => ({ holder: String(env.CARD_HOLDER || ""), rates: CARD_IN, out: CARD_RATE, min: CARD_MIN, wdFee: WD_FEE, wdFeeMin: WD_FEE_MIN, wdMin: WD_MIN });
async function cardDepDecide(id: number, ok: boolean): Promise<boolean> {
  let uid = "", coins = 0;
  const done = await db.begin(async (t: any) => {
    const r = await t`update card_dep set status = ${ok ? "done" : "rejected"} where id = ${id} and status in ('new','claimed') returning user_id, coins`;
    if (!r.length) return false; uid = r[0].user_id; coins = N(r[0].coins);
    if (ok) { await adj(t, uid, coins, "dep_card", "cd" + id, "card"); await t`update users set deposited = deposited + ${coins} where id = ${uid}`; }
    return true;
  });
  if (done) { pushBal(uid); if (BOT_TOKEN && uid.startsWith("tg")) tg("sendMessage", { chat_id: uid.slice(2), text: ok ? `✅ Поповнення карткою зараховано: +${coins} монет.` : `❌ Поповнення карткою #C${id} відхилено. Якщо ти платив, напиши в підтримку.` }).catch(() => {}); }
  return done;
}
async function cardWdDecide(id: number, ok: boolean): Promise<boolean> {
  let uid = "", amount = 0, cur = "";
  const done = await db.begin(async (t: any) => {
    const r = await t`update card_wd set status = ${ok ? "paid" : "rejected"} where id = ${id} and status = 'pending' returning user_id, coins, fee, amount, cur`;
    if (!r.length) return false; uid = r[0].user_id; amount = N(r[0].amount); cur = r[0].cur;
    if (!ok) { await adj(t, uid, N(r[0].coins), "wd_refund", "cwr" + id, null); await adj(t, "house", -N(r[0].fee), "wd_fee_refund", "cwr" + id, uid); }
    return true;
  });
  if (done) { pushBal(uid); if (BOT_TOKEN && uid.startsWith("tg")) tg("sendMessage", { chat_id: uid.slice(2), text: ok ? `✅ Вивід на картку виконано: ${amount.toFixed(2)} ${cur}.` : `❌ Вивід на картку відхилено, монети повернено на баланс.` }).catch(() => {}); }
  return done;
}
const adminAsk = (text: string, kind: string, id: number) => { if (BOT_TOKEN) tg("sendMessage", { chat_id: ADMIN_ID, text, reply_markup: { inline_keyboard: [[{ text: kind === "cd" ? "✅ Зарахувати" : "✅ Виплачено", callback_data: kind + ":ok:" + id }, { text: "❌ Відхилити", callback_data: kind + ":no:" + id }]] } }).catch(() => {}); };

async function wdDecide(id: number, ok: boolean): Promise<boolean> {
  if (ok) {
    const r = await db`update withdrawals set status = 'paid' where id = ${id} and status in ('pending','review') returning user_id, ton`;
    if (r.length && BOT_TOKEN) { const uid = r[0].user_id; if (uid.startsWith("tg")) tg("sendMessage", { chat_id: uid.slice(2), text: `✅ Вивід #${id} виконано: ${N(r[0].ton)} TON відправлено.` }).catch(() => {}); }
    return r.length > 0;
  }
  let uid = "";
  const done = await db.begin(async (t: any) => {
    const r = await t`update withdrawals set status = 'rejected' where id = ${id} and status in ('pending','review') returning user_id, coins, fee`;
    if (!r.length) return false; uid = r[0].user_id;
    await adj(t, uid, N(r[0].coins), "wd_refund", "wdr" + id, null); await adj(t, "house", -N(r[0].fee), "wd_fee_refund", "wdr" + id, uid); return true;
  });
  if (done) { pushBal(uid); if (BOT_TOKEN && uid.startsWith("tg")) tg("sendMessage", { chat_id: uid.slice(2), text: `❌ Вивід #${id} відхилено, монети повернено на баланс.` }).catch(() => {}); }
  return done;
}
async function onCallback(q: any) {
  const from = "tg" + q.from.id; const [kind, act2, idS] = String(q.data || "").split(":");
  if (!isAdmin(from)) return void tg("answerCallbackQuery", { callback_query_id: q.id, text: "Немає доступу" });
  if (kind === "cd" || kind === "cw") {
    if (act2 !== "ok" && act2 !== "no") return;
    const d2 = kind === "cd" ? await cardDepDecide(Number(idS), act2 === "ok") : await cardWdDecide(Number(idS), act2 === "ok");
    tg("answerCallbackQuery", { callback_query_id: q.id, text: d2 ? (act2 === "ok" ? "Готово" : "Відхилено") : "Вже оброблено" });
    if (q.message) tg("editMessageReplyMarkup", { chat_id: q.message.chat.id, message_id: q.message.message_id, reply_markup: { inline_keyboard: [] } }).catch(() => {});
    return;
  }
  if (act2 !== "ok" && act2 !== "no") return;
  const done = await wdDecide(Number(idS), act2 === "ok");
  tg("answerCallbackQuery", { callback_query_id: q.id, text: done ? (act2 === "ok" ? "Позначено як виплачено" : "Відхилено, монети повернено") : "Вже оброблено" });
  if (q.message) tg("editMessageReplyMarkup", { chat_id: q.message.chat.id, message_id: q.message.message_id, reply_markup: { inline_keyboard: [] } }).catch(() => {});
}
async function bot() {
  if (!BOT_TOKEN || !WEBAPP_URL) return console.log("Bot off (need BOT_TOKEN and WEBAPP_URL)");
  try { const me = await tg("getMe"); BOT_USER = me.result?.username || ""; } catch {}
  console.log("Bot polling as @" + BOT_USER);
  await tg("deleteWebhook");
  try { await tg("setChatMenuButton", { menu_button: { type: "web_app", text: "ГРАТИ", web_app: { url: WEBAPP_URL } } }); } catch {}
  let offset = 0;
  for (;;) {
    try {
      const r = await tg("getUpdates", { offset, timeout: 30, allowed_updates: ["message", "pre_checkout_query", "callback_query", "chat_join_request", "my_chat_member"] });
      for (const upd of r.result || []) {
        offset = upd.update_id + 1;
        try {
          if (upd.chat_join_request || upd.my_chat_member) await spUpd(upd); else if (upd.pre_checkout_query) await tg("answerPreCheckoutQuery", { pre_checkout_query_id: upd.pre_checkout_query.id, ok: true });
          else if (upd.callback_query) await onCallback(upd.callback_query);
          else if (upd.message?.successful_payment) await onPayment(upd.message);
          else if (upd.message && !upd.message.successful_payment && await supBot(upd.message)) {} else if (upd.message?.text?.startsWith("/")) await onCommand(upd.message);
        } catch (e: any) { console.error("update", e.message); }
      }
    } catch (e: any) { console.error("bot", e.message); await new Promise((r) => setTimeout(r, 5000)); }
  }
}

// ---------- Поповнення TON (опитування toncenter) ----------
async function tonPoll() {
  try {
    const r = await fetch(`https://toncenter.com/api/v2/getTransactions?address=${TON_ADDR}&limit=50`);
    const j: any = await r.json(); if (!j.ok) return;
    for (const tx of j.result || []) {
      const m = tx.in_msg; if (!m || !m.source) continue;
      const mm = /^dp(\d+)$/.exec(String(m.message || "").trim()); if (!mm) continue;
      const uid = "tg" + mm[1]; const val = N(m.value); const coins = Math.floor((val / 1e9) * TON_IN);
      if (coins < 100) continue;
      if (!(await getUserRow(uid))) continue;
      const hash = String(tx.transaction_id.hash);
      const ok = await db.begin(async (t: any) => { const d = await adj(t, uid, coins, "dep_ton", hash, val / 1e9 + " TON"); if (d) await t`update users set deposited = deposited + ${coins} where id = ${uid}`; return d; });
      if (ok) { pushBal(uid); if (BOT_TOKEN) tg("sendMessage", { chat_id: mm[1], text: `✅ TON-поповнення: +${coins} монет (${val / 1e9} TON)` }).catch(() => {}); }
    }
  } catch (e: any) { console.error("ton", e.message); }
}

await initDb(); await recoverHolds();
await initPayer();
autoOn = (await db`select body from parts where name = 'cfg_auto'`)[0]?.body === "1";
{
  const prs = await db`select name, body from parts where name like 'html%' order by name`;
  HTML = prs.map((r: any) => r.body).join("");
  console.log("HTML parts", prs.length, "len", HTML.length, "sha", new Bun.CryptoHasher("sha256").update(HTML).digest("hex").slice(0, 16));
}
Bun.serve({
  port: Number(env.PORT || 3000),
  async fetch(req, server) {
    const u = new URL(req.url);
    if (u.pathname === "/ws") {
      const row = await authFrom(u.searchParams.get("init") || "", u.searchParams.get("dev") || "");
      if (!row) return new Response("unauthorized", { status: 401 });
      if (server.upgrade(req, { data: { uid: row.id, name: row.name, sub: "", n: 0 } })) return;
      return new Response("upgrade failed", { status: 400 });
    }
    try { return await handle(req); } catch (e: any) { console.error("http", e.message); return json({ error: "server" }, 500); }
  },
  websocket: {
    open(ws: any) { socks.add(ws); send(ws, { t: "hello" }); },
    message(ws: any, msg: any) { onMessage(ws, String(msg)); },
    close(ws: any) {
      socks.delete(ws); const uid = ws.data.uid;
      if (![...socks].some((w) => w.data.uid === uid)) mxLeave(uid).catch(() => {});
      setTimeout(() => { if (![...socks].some((w) => w.data.uid === uid)) pkLeave(uid).catch(() => {}); }, 45000);
    },
  },
});
setInterval(() => { for (const w of socks) w.data.n = 0; }, 2000);
console.log("Pixel Casino v3 up");
// закриття: скільки в гравців лишилося монет (щоб знати, чи комусь ще потрібен вивід)
if (CLOSED) db`select count(*)::int as n, coalesce(sum(balance),0)::bigint as s, count(*) filter (where balance >= ${WD_MIN_NOW})::int as can, coalesce(max(balance),0)::bigint as mx from users where id <> 'house' and balance > 0 and not (id = ${"tg" + ADMIN_ID})`.then(async (r: any) => { const d = await db`select count(distinct l.user_id)::int as n, coalesce(sum(l.amount),0)::bigint as s from ledger l join users u on u.id = l.user_id where l.kind in ('dep_stars','dep_ton','dep_card') and u.balance > 0 and u.id <> 'house'`; console.log("closing deposits among holders: players", d[0].n, "coins", String(d[0].s)); return r; }).then((r: any) => console.log("closing balances: players", r[0].n, "coins", String(r[0].s), "can withdraw", r[0].can, "max", String(r[0].mx))).catch((e: any) => console.error("closing balances", e.message));
bot(); tonPoll(); setInterval(tonPoll, 30000);

