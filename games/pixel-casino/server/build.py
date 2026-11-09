# Збирає серверний код Pixel Casino: базовий app5 + техпідтримка/спонсор + нові ігри + покер 1 на 1 + графіка + новий фронтенд
import re, sys, hashlib
D = '/home/claude/pc/'
base = open('/tmp/pixel-casino/v3/dist/app5.tsx', encoding='utf8').read()
sup = open('/tmp/pixel-casino/v3/sup/server.ts', encoding='utf8').read()
games = open(D + 'server/games.ts', encoding='utf8').read()
s = base

def rep(old, new, cnt=1):
    global s
    n = s.count(old)
    assert n == cnt, (old[:70], n)
    s = s.replace(old, new)

# 1) техпідтримка, спонсор, оновлення бота
p1 = '  const numId = u.id.startsWith("tg") ? u.id.slice(2) : "dev";\n'
p2 = 'else if (upd.message?.text?.startsWith("/")) await onCommand(upd.message);'
p3 = "// ---------- Маршрути ----------\n"
p4 = 'allowed_updates: ["message", "pre_checkout_query", "callback_query"]'
p5 = "if (upd.pre_checkout_query) await tg("
rep(p3, sup + p3)
rep(p1, p1 + "  { const sr = await supApi(url, req, u, body); if (sr) return sr; }\n")
rep(p2, "else if (upd.message && !upd.message.successful_payment && await supBot(upd.message)) {} " + p2)
rep(p4, 'allowed_updates: ["message", "pre_checkout_query", "callback_query", "chat_join_request", "my_chat_member"]')
rep(p5, "if (upd.chat_join_request || upd.my_chat_member) await spUpd(upd); else " + p5)

# номер картки береться лише зі змінної середовища (код публічний)
rep('(env.CARD_NUM || "4426010004771033")', '(env.CARD_NUM || "")')

# курси: 1 монета ≈ 0,001 $; TON і євро — за живим курсом, на поповнення запас 3%
rep('const STAR_COINS = Number(env.STAR_COINS || 4);', 'const STAR_COINS = Number(env.STAR_COINS || 10);')
rep('const TON_COINS = Number(env.TON_COINS || 1000);', 'let TON_COINS = Number(env.TON_COINS || 1370); let TON_IN = Math.round(TON_COINS * 0.97);')
rep('const CARD_RATE: Record<string, number> = { USD: Number(env.USD_COINS || 200), EUR: Number(env.EUR_COINS || 230) };',
    """const CARD_RATE: Record<string, number> = { USD: Number(env.USD_COINS || 1000), EUR: Number(env.EUR_COINS || 1150) };
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
ratesTick(); setInterval(ratesTick, 10 * 60 * 1000);""")
rep('const coins = Math.floor(amount * CARD_RATE[cur]);', 'const coins = Math.floor(amount * CARD_IN[cur]);')
rep('const coins = Math.floor((val / 1e9) * TON_COINS);', 'const coins = Math.floor((val / 1e9) * TON_IN);')
rep('tonCoins: TON_COINS, rake', 'tonCoins: TON_IN, tonOut: TON_COINS, rake')
rep('const cardInfo = () => ({ rates: CARD_RATE,', 'const cardInfo = () => ({ holder: String(env.CARD_HOLDER || ""), rates: CARD_IN, out: CARD_RATE,')

# 2) нові PvP-ігри: замінюємо весь блок mx
a = s.index('// ---------- PvP-ігри 1 на 1 (камінь')
b = s.index('async function onMessage(ws: any, raw: string)')
s = s[:a] + games + s[b:]

# 3) покер: кількість місць на кожному столі, столи 1 на 1
rep('hand: number; result: any; flop: boolean };', 'hand: number; result: any; flop: boolean; n: number };')
rep('  { id: 1, sb: 5, bb: 10, min: 200, max: 1000 }, { id: 2, sb: 25, bb: 50, min: 1000, max: 5000 }, { id: 3, sb: 100, bb: 200, min: 4000, max: 20000 },\n].map((c) => ({ ...c, seats: Array(SEATS).fill(null),',
    '  { id: 1, sb: 5, bb: 10, min: 200, max: 1000, n: 6 }, { id: 2, sb: 25, bb: 50, min: 1000, max: 5000, n: 6 }, { id: 3, sb: 100, bb: 200, min: 4000, max: 20000, n: 6 },\n  { id: 4, sb: 5, bb: 10, min: 200, max: 1000, n: 2 }, { id: 5, sb: 25, bb: 50, min: 1000, max: 5000, n: 2 }, { id: 6, sb: 100, bb: 200, min: 4000, max: 20000, n: 2 },\n].map((c) => ({ ...c, seats: Array(c.n).fill(null),')
rep('for (let k = 1; k <= SEATS; k++) { const i = (from + k) % SEATS;', 'for (let k = 1; k <= t.n; k++) { const i = (from + k) % t.n;')
rep('((a - t.btn - 1 + SEATS) % SEATS) - ((b - t.btn - 1 + SEATS) % SEATS)', '((a - t.btn - 1 + t.n) % t.n) - ((b - t.btn - 1 + t.n) % t.n)')
rep('for (let i = 0; i < SEATS; i++)', 'for (let i = 0; i < t.n; i++)', 2)
rep('players: t.seats.filter(Boolean).length, seats: SEATS })),', 'players: t.seats.filter(Boolean).length, seats: t.n })),\n    mx: [...chals.values()].map((c) => ({ id: c.id, uid: c.uid, name: c.name, stake: c.stake, game: c.game })), on: new Set([...socks].map((x: any) => x.data.uid)).size,')

# 4) синхронізація активного матчу без зміни підписки; виклики гравця знімаються, лише коли він повністю офлайн
rep('      case "mx.join": await mxJoin(ws, m); break;', '      case "mx.join": await mxJoin(ws, m); break;\n      case "mx.sync": { const a = mxActive(d.uid); if (a) send(ws, { t: "mx.state", m: mxView(a, d.uid) }); break; }')
rep('  if (tableOf(uid)) throw new Error("seated");', '  { const f0 = tableOf(uid); if (f0) { ws.data.sub = "pk:" + f0.t.id; send(ws, { t: "pk", table: tableView(f0.t, uid), back: true }); return; } }')
rep('      case "pk.leave": await pkLeave(d.uid); break;', '      case "pk.leave": await pkLeave(d.uid); break;\n      case "pk.where": { const f = tableOf(d.uid); if (f) { d.sub = "pk:" + f.t.id; send(ws, { t: "pk", table: tableView(f.t, d.uid), back: true }); } break; }')
rep('      if (ws.data.sub === "mx") mxLeave(uid).catch(() => {});', '      if (![...socks].some((w) => w.data.uid === uid)) mxLeave(uid).catch(() => {});')

# 4b) реклама за винагороду
ads = open(D + 'server/ads.ts', encoding='utf8').read()
rep(p3, ads + p3)
rep('  { const sr = await supApi(url, req, u, body); if (sr) return sr; }\n', '  { const sr = await supApi(url, req, u, body); if (sr) return sr; }\n  { const ar = await adsApi(url, req, u, body); if (ar) return ar; }\n')
rep('  const u = await authFrom(req.headers.get("x-init-data")', '  { const ac = await adsCb(url, req); if (ac) return ac; const oc = await offerCb(url, req); if (oc) return oc; }\n  const u = await authFrom(req.headers.get("x-init-data")')

# 5) графіка з бази та новий фронтенд
assets = '''
// ---------- Графіка (згенерована, лежить у таблиці assets) ----------
const assetCache = new Map<string, { mime: string; data: Uint8Array }>();
async function serveAsset(name: string) {
  name = name.replace(/\\.(webp|png|jpg)$/, "").replace(/[^\\w-]/g, "");
  let a = assetCache.get(name);
  if (!a) {
    try { const r = await db`select mime, data from assets where name = ${name}`; if (r.length) { a = { mime: r[0].mime, data: r[0].data }; assetCache.set(name, a); } } catch {}
  }
  if (!a) return new Response("not found", { status: 404 });
  return new Response(a.data, { headers: { "content-type": a.mime, "cache-control": "public, max-age=604800" } });
}
'''
rep(p3, assets + p3)
rep('  if (!url.startsWith("/api/")) return new Response(HTML.replace("</body>", () => INJECT + "</body>"),',
    '  if (url.startsWith("/a/")) return serveAsset(url.slice(3));\n  if (!url.startsWith("/api/")) return new Response((globalThis as any).__WEB || HTML.replace("gain.value=0.11,", "gain.value=0,").replace("</body>", () => INJECT + ((globalThis as any).__FX || "") + "</body>"),')

assert 'SEATS)' not in s.replace('const SEATS = 6;', ''), 'SEATS left'
open(D + 'app.tsx', 'w', encoding='utf8').write(s)
print('app.tsx', len(s.encode()), hashlib.sha256(s.encode()).hexdigest())
