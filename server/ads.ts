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
  if (!(await getUserRow(uid))) return new Response("1"); // невідомий гравець — підтверджуємо, щоб мережа не повторювала
  const back = st === "2" || st === "reversed" || st === "rejected" || st === "chargeback" || coins < 0;
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
