/* ===== ЛОБІ, ХАБ ІГОР, КАРТИ, КІМНАТИ, БОНУСИ, МЕНЮ ===== */
const GAMES = {
  chess: { k: "mx", img: "i-chess" }, checkers: { k: "mx", img: "i-checkers" }, four: { k: "mx", img: "i-four" }, mines: { k: "mx", img: "i-mines" },
  rps: { k: "mx", img: "i-rps" }, ttt: { k: "mx", img: "i-ttt" }, dice: { k: "duel", img: "i-dice" }, coin: { k: "duel", img: "" },
  bj: { k: "mx", img: "i-21" }, card: { k: "mx", img: "" },
};
const HUB = ["chess", "checkers", "four", "mines", "rps", "ttt", "dice", "coin"];
const STAKES = [50, 100, 250, 500, 1000, 2500];
const cardHtml = (c, cls, w) => {
  if (c === undefined || c === null) return `<span class="cd gh ${cls || ""}"${w ? ` style="--w:${w}px"` : ""}></span>`;
  if (c < 0) return `<span class="cd bk ${cls || ""}"${w ? ` style="--w:${w}px"` : ""}></span>`;
  const r = c % 13, s = "cdhs"[Math.floor(c / 13)], lab = r === 8 ? "10" : r < 8 ? String(r + 2) : "JQKA"[r - 9], sym = { c: "♣", d: "♦", h: "♥", s: "♠" }[s] + "︎";
  return `<span class="cd${s === "d" || s === "h" ? " r" : ""} ${cls || ""}"${w ? ` style="--w:${w}px"` : ""}><span>${lab}<span class="s2">${sym}</span></span><span class="s">${sym}</span></span>`;
};
function gIcon(g) {
  const m = GAMES[g];
  if (g === "card") return `<span class="ico hc">${cardHtml(51)}${cardHtml(24)}</span>`;
  if (g === "coin") return `<img src="${COIN}" alt="" style="image-rendering:pixelated;padding:8%">`;
  return `<img src="/a/${m.img}.webp" alt="">`;
}
const myId = () => (S.me ? S.me.id : "");
const chalsOf = (g) => { const L = S.L; if (!L) return []; return (GAMES[g].k === "mx" ? L.mx || [] : L.duels || []).filter((c) => c.game === g); };
const openCount = (g) => chalsOf(g).filter((c) => c.uid !== myId()).length;
const jpLeft = () => { const j = S.L && S.L.jp; if (!j || j.left === null) return null; return Math.max(0, j.left - (Date.now() - S.Lat)); };

/* ---------- живі дані з лобі ---------- */
function setLive(el, n, txtOn, txtOff) { if (!el) return; el.classList.toggle("idle", !n); el.querySelector("span").textContent = n ? txtOn : txtOff; }
function renderLive() {
  const L = S.L; if (!L) return; S.Lat = Date.now();
  $("onl").textContent = fmt(L.on || 1);
  const atTables = L.tables.reduce((a, t) => a + t.players, 0), cardCh = openCount("bj") + openCount("card");
  setLive($("lcCards"), atTables + cardCh, atTables ? T("at_tables", atTables) : T("waiting_n", cardCh), T("play_now"));
  const hubN = HUB.reduce((a, g) => a + openCount(g), 0);
  setLive($("lcGames"), hubN, T("waiting_n", hubN), T("games_n", HUB.length));
  $$(".quick4 .qt").forEach((b) => { const n = openCount(b.dataset.room), d = b.querySelector(".cnt"); d.hidden = !n; d.textContent = n; });
  const j = L.jp; $("lcPot").textContent = fmt(j.pot);
  setLive($("lcJp"), j.players.length, j.state === "spin" ? T("jp_spin") : T("players_n", j.players.length), T("jp_open"));
  tickJpTile();
  if (S.tab === "games") renderGames(); else if (S.tab === "cards") renderCards();
  if (S.room) renderRoom();
  if (S.jpOpen) renderJP();
}
function tickJpTile() {
  const L = S.L; if (!L) return; const left = jpLeft(), j = L.jp;
  $("lcJpBar").style.width = j.state === "spin" ? "100%" : left === null ? "0%" : (100 - (left / 30000) * 100).toFixed(1) + "%";
  if (left !== null && j.state === "open") $("lcJp").querySelector("span").textContent = T("jp_in", Math.ceil(left / 1000));
}
setInterval(() => { tickJpTile(); if (S.jpOpen) jpTimer(); }, 250);

/* ---------- хаб ігор 1 на 1 ---------- */
function renderGames() {
  const box = $("gGrid");
  if (!box.dataset.built) {
    box.dataset.built = 1;
    box.innerHTML = HUB.map((g) => `<button class="gt" data-room="${g}">${gIcon(g)}<b>${T("g_" + g)}</b><small></small></button>`).join("") + `<button class="gt train" id="gTrain"><img src="/a/i-robot.webp" alt=""><b>${T("training")}</b><small>${T("no_coins")}</small></button>`;
    $("gTrain").onclick = openTraining;
  }
  $$(".gt[data-room]", box).forEach((b) => { const n = openCount(b.dataset.room), s = b.querySelector("small"); b.classList.toggle("hot", n > 0); s.className = n ? "live" : ""; s.textContent = n ? T("waiting_n", n) : T("create"); });
  $("gOnl").textContent = S.L ? T("online_n", fmt(S.L.on || 1)) : "";
}

/* ---------- вкладка карт ---------- */
function renderCards() {
  const L = S.L; $("cOnl").textContent = L ? T("online_n", fmt(L.on || 1)) : "";
  $$(".mode").forEach((b) => b.classList.toggle("on", b.dataset.mode === S.pkMode));
  if (!L) { $("tList").innerHTML = `<div class="empty">${T("connecting")}</div>`; return; }
  const by = (n) => L.tables.filter((t) => t.seats === n);
  $("pk6n").textContent = T("players_n", by(6).reduce((a, t) => a + t.players, 0));
  $("pk2n").textContent = T("players_n", by(2).reduce((a, t) => a + t.players, 0));
  $("tList").innerHTML = by(Number(S.pkMode)).map((t) => `<div class="row"><div class="grow"><b>${T("blinds")} ${fmt(t.sb)}/${fmt(t.bb)}</b><small>${T("buyin")} ${fmt(t.min)}–${fmt(t.max)}</small><div class="seatsMini">${Array.from({ length: t.seats }, (_, i) => `<i class="${i < t.players ? "f" : ""}"></i>`).join("")}</div></div><button class="btn sm ${t.players >= t.seats ? "dk" : "gr"}" data-sit="${t.id}"${t.players >= t.seats ? " disabled" : ""}>${t.players >= t.seats ? T("full") : T("sit")}</button></div>`).join("");
  const bj = openCount("bj"), cd = openCount("card");
  setLive($("lcBj"), bj, T("waiting_n", bj), T("create"));
  setLive($("lcCard"), cd, T("waiting_n", cd), T("create"));
}
$("v-cards").addEventListener("click", (e) => {
  const m = e.target.closest("[data-mode]"); if (m) { S.pkMode = m.dataset.mode; sfx.click(); renderCards(); return; }
  const s = e.target.closest("[data-sit]"); if (s) sitDialog(Number(s.dataset.sit));
});

/* ---------- кімната гри: ставка, виклики ---------- */
function openRoom(g) {
  if (!GAMES[g]) return;
  if (S.match && !S.match.over && !S.match.local) { showMatch(); return; }
  S.room = g; sheet(`<div id="room"></div>`);
  S.room = g; renderRoom();
}
function renderRoom() {
  const g = S.room, box = $("room"); if (!g || !box) return;
  const list = chalsOf(g), me = myId(), mine = list.filter((c) => c.uid === me), others = list.filter((c) => c.uid !== me);
  const bal = S.me ? S.me.balance : 0;
  box.innerHTML = `<div class="shH"><span class="gi">${gIcon(g)}</span><div><h3>${T("g_" + g)}</h3><p>${T("r_" + g)}</p></div><button class="x" data-x>✕</button></div>
  <div><span class="lbl">${T("stake")}</span><div class="chips g3">${STAKES.map((v) => `<button class="chip${v === S.stake ? " on" : ""}" data-stk="${v}"><i class="coin" style="width:14px;height:14px"></i>${fmt(v)}</button>`).join("")}</div></div>
  ${mine.length ? mine.map((c) => `<div class="wait"><i></i><i></i><i></i><span>${T("wait_opp")} · ${fmt(c.stake)}</span></div><button class="btn dk w sm" data-cancel="${c.id}">${T("cancel_ch")}</button>`).join("") : ""}
  ${!mine.length || GAMES[g].k === "duel" ? `<button class="btn pk w" id="rCreate"${bal < S.stake ? " disabled" : ""}>${T("create_ch")} · ${fmt(S.stake)}</button>` : ""}
  ${bal < S.stake ? `<button class="btn w sm" data-go="wallet">${T("topup")}</button>` : ""}
  <div class="sec">${T("open_ch")}</div>
  <div class="chl">${others.length ? others.map((c) => `<div class="row"><canvas width="10" height="10" data-av="${esc(c.uid)}" style="width:34px;height:34px"></canvas><div class="grow"><b>${esc(c.name)}</b></div><span class="stk"><i class="coin"></i>${fmt(c.stake)}</span><button class="btn sm gr" data-join="${c.id}" data-s="${c.stake}">${T("play")}</button></div>`).join("") : `<div class="empty">${T("no_ch")}</div>`}</div>`;
  $$("canvas[data-av]", box).forEach((cv) => drawAv(cv, cv.dataset.av));
}
$("shIn").addEventListener("click", (e) => {
  const g = S.room; if (!g) return;
  if (e.target.closest("[data-x]")) { closeSheet(); return; }
  const k = GAMES[g].k;
  const st = e.target.closest("[data-stk]"); if (st) { S.stake = Number(st.dataset.stk); ls("stake", S.stake); sfx.chip(); renderRoom(); return; }
  if (e.target.closest("#rCreate")) { if (wsSend({ t: k === "mx" ? "mx.create" : "duel.create", game: g, stake: S.stake })) { sfx.chip(); hap("medium"); } return; }
  const c = e.target.closest("[data-cancel]"); if (c) { wsSend({ t: k === "mx" ? "mx.cancel" : "duel.cancel", id: Number(c.dataset.cancel) }); sfx.click(); return; }
  const j = e.target.closest("[data-join]");
  if (j) {
    if ((S.me ? S.me.balance : 0) < Number(j.dataset.s)) { toast(T("e_no_funds"), "bad"); return; }
    if (wsSend({ t: k === "mx" ? "mx.join" : "duel.join", id: Number(j.dataset.join) })) { sfx.chip(); hap("medium"); j.disabled = true; }
  }
});

/* ---------- спонсор і бонуси ---------- */
let SP = null;
async function spLoad(check) {
  try { SP = await api("/api/sponsor", check ? {} : undefined); } catch (e) { SP = null; }
  renderSp(); return SP;
}
function renderSp() {
  const b = $("spB"), on = SP && SP.link && SP.state !== "member";
  b.hidden = !on; $("giftDot").hidden = !on;
  if (!on) return;
  $("spT").textContent = T("sp_t");
  $("spS").textContent = SP.title || T("sp_s");
  $("spG").textContent = S.spOpened ? T("sp_check") : T("sp_go");
}
async function spClick() {
  if (!SP) return;
  if (!S.spOpened) { S.spOpened = true; openLink(SP.link); renderSp(); return; }
  const r = await spLoad(true);
  if (r && r.state === "member") { toast(T("sp_ok"), "good"); sfx.win(); burst(40); }
  else if (r && r.state === "req") toast(T("sp_req"));
  else { S.spOpened = false; renderSp(); toast(T("sp_no"), "bad"); }
}
$("spB").onclick = spClick;
document.addEventListener("visibilitychange", () => { if (!document.hidden && S.spOpened && SP && SP.state !== "member") setTimeout(() => spLoad(true), 600); });

/* прогрес до виводу: скільки монет ще треба і скільки відіграти; оцінка в переглядах реклами */
const wpBox = () => `<div class="card wp" id="wpCard"><div class="empty" style="padding:6px">${T("loading")}</div></div>`;
async function wpRender(withBtn) {
  try { await refreshMe(); } catch (e) {}
  const el = $("wpCard"), me = S.me; if (!el || !me) return;
  const c = me.cfg, min = c.wdMin, bal = me.balance, dep = me.deposited || 0, turn = me.turnover || 0;
  const needC = Math.max(0, min - bal), needT = Math.max(0, dep - turn), ready = !needC && !needT;
  const rw = (ADSI && ADSI.nets && ADSI.nets.length && ADSI.reward) || 0, usd = (n) => "$" + (n / 1000).toFixed(2);
  const bar = (v, of, cls) => `<div class="wpb ${cls || ""}"><i style="width:${Math.min(100, of ? (v / of) * 100 : 100).toFixed(1)}%"></i></div>`;
  el.innerHTML = `<div class="refh"><span style="font-size:30px">${ready ? "🎉" : "🎯"}</span><div><b>${ready ? T("wp_ok") : T("wp_t")}</b><p>${ready ? T("wp_ok_p", fmt(bal), usd(bal)) : T("wp_p", fmt(min), usd(min))}</p></div></div>
  <div class="kv"><span class="dim">${T("wp_bal")}</span><b>${fmt(Math.min(bal, min))} / ${fmt(min)}</b></div>${bar(bal, min, needC ? "" : "ok")}
  ${dep ? `<div class="kv"><span class="dim">${T("wp_turn")}</span><b>${fmt(Math.min(turn, dep))} / ${fmt(dep)}</b></div>${bar(turn, dep, needT ? "cy" : "ok")}` : ""}
  ${ready ? (withBtn ? `<button class="btn gr w sm" id="wpGo">${T("w_wd")}</button>` : "") : `<p class="dim" style="font-size:12px;margin:0">${[needC ? T("wp_need_c", fmt(needC)) + (rw ? " · " + T("wp_ads", fmt(Math.ceil(needC / rw))) : "") : "", needT ? T("wp_need_t", fmt(needT)) : ""].filter(Boolean).join("<br>")}</p>`}`;
  const g = $("wpGo"); if (g) g.onclick = () => { closeSheet(); go("wallet"); renderWallet("wd"); };
}

function openBonus() {
  const spDone = SP && SP.state === "member";
  sheet(`<div class="shH"><span class="gi"><img src="/a/i-gift.webp" alt=""></span><div><h3>${T("bonus_t")}</h3></div><button class="x" data-xs>✕</button></div>
  ${wpBox()}
  <div class="card" id="adsCard"><div class="refh"><span style="font-size:34px">📺</span><div><b>${T("ads_t")}</b><p id="adsP">${T("loading")}</p></div></div><div id="adsBtns" class="btns" style="margin-top:12px"></div></div>
  <div class="card" style="opacity:.75"><div class="refh"><span style="font-size:34px">🔒</span><div><b>${T("soon_t")}</b><p>${T("soon_p")}</p></div></div></div>
  ${SP && SP.link ? `<div class="card"><div class="refh"><span style="font-size:34px">📢</span><div><b>${esc(SP.title || T("sp_s"))}</b><p>${spDone ? T("sp_ok") : T("sp_t")}</p></div></div>${spDone ? "" : `<button class="btn cy w sm" id="bSp" style="margin-top:12px">${S.spOpened ? T("sp_check") : T("sp_go")}</button>`}</div>` : ""}
  <div class="card"><div class="refh"><img src="/a/i-friends.webp" alt=""><div><b>${T("ref_t")}</b><p>${T("ref_p", Math.round((S.me ? S.me.cfg.refPct : 0.2) * 100))}</p></div></div><button class="btn pk w sm" id="bRef" style="margin-top:12px">${T("ref_invite")}</button></div>
  <div class="card"><div class="refh"><img src="/a/i-robot.webp" alt=""><div><b>${T("training")}</b><p>${T("train_p")}</p></div></div><button class="btn dk w sm" id="bTr" style="margin-top:12px">${T("train_go")}</button></div>`);
  const sp = $("bSp"); if (sp) sp.onclick = async () => { await spClick(); closeSheet(); };
  $("bRef").onclick = () => { closeSheet(); shareRef(); };
  $("bTr").onclick = () => { closeSheet(); openTraining(); };
  $("shIn").querySelector("[data-xs]").onclick = closeSheet;
  adsRender().then(() => wpRender(true));
}
$("bGift").onclick = openBonus;

/* ---------- реклама за монети ---------- */
let ADSI = null, adsBusy = false;
const ADS_NAME = { adsgram: "Adsgram", monetag: "Monetag" };
function loadScript(src, data) {
  return new Promise((res, rej) => {
    if (document.querySelector(`script[src="${src}"]`)) return res();
    const s = document.createElement("script"); s.src = src; Object.assign(s.dataset, data || {}); s.onload = () => res(); s.onerror = () => rej(new Error("ads_load")); document.head.appendChild(s);
  });
}
async function adsRender() {
  const p = $("adsP"), box = $("adsBtns"); if (!p) return;
  try { ADSI = await api("/api/ads"); } catch (e) { ADSI = null; }
  if (!ADSI || (!ADSI.nets.length && !(ADSI.offers || []).length)) { $("adsCard").hidden = true; return; }
  p.textContent = ADSI.nets.length ? T("ads_p", ADSI.reward, ADSI.left, ADSI.day) : T("offers_p");
  const of = ADSI.offers || [];
  if (of.length) { box.insertAdjacentHTML("afterend", `<div class="sec" style="margin-top:14px">${T("offers_t")}</div><div class="tlist" style="margin-top:8px">${of.map((o, i) => `<div class="row"><div class="grow"><b>${esc(o.name)}</b><small>${T("offers_s")}</small></div><button class="btn sm cy" data-of="${i}">${T("offers_go")}</button></div>`).join("")}</div>`); $$("[data-of]", $("adsCard")).forEach((b) => (b.onclick = () => openLink(of[Number(b.dataset.of)].url))); }
  box.innerHTML = ADSI.nets.map((n) => `<button class="btn gr sm" data-ad="${n.net}"${ADSI.left ? "" : " disabled"}>▶ ${ADSI.nets.length > 1 ? ADS_NAME[n.net] || n.net : T("ads_go")}</button>`).join("");
  box.onclick = (e) => { const b = e.target.closest("[data-ad]"); if (b) adsShow(ADSI.nets.find((n) => n.net === b.dataset.ad)); };
}
/* звичайна реклама між іграми (без винагороди): кожна 3-тя завершена гра, не частіше ніж раз на 2 хвилини */
let interN = 0, interAt = 0;
async function adsInter() {
  try {
    if (++interN % 3 || Date.now() - interAt < 120000) return;
    if (!ADSI) ADSI = await api("/api/ads");
    const n = (ADSI.nets || []).find((x) => x.net === "monetag"); if (!n) return;
    await loadScript("https://libtl.com/sdk.js", { zone: n.id, sdk: "show_" + n.id });
    const f = window["show_" + n.id]; if (!f) return; interAt = Date.now();
    const p = f(); if (p && p.catch) p.catch(() => {});
  } catch (e) {}
}
async function adsShow(n) {
  if (!n || adsBusy) return; adsBusy = true; MUS.pause && MUS.pause();
  try {
    if (n.net === "adsgram") {
      await loadScript("https://sad.adsgram.ai/js/sad.min.js");
      const r = await window.Adsgram.init({ blockId: n.id }).show();
      if (r && r.done === false) throw new Error("ads_skip");
    } else if (n.net === "monetag") {
      await loadScript("https://libtl.com/sdk.js", { zone: n.id, sdk: "show_" + n.id });
      const f = window["show_" + n.id]; if (!f) throw new Error("ads_load");
      await f({ ymid: ADSI.uid });
    }
    if (ADSI.server) { toast(T("ads_cb"), "good"); setTimeout(() => { refreshMe(); adsRender(); }, 5000); }
    else { const r = await api("/api/ads/claim", { net: n.net }); toast("+" + r.reward + " 🪙", "good"); sfx.win(); adsRender(); }
  } catch (e) { const m = (e && e.message) || ""; toast(DICT["e_" + m] ? T("e_" + m) : T("e_ads_none"), "bad"); }
  adsBusy = false; MUS.sync();
}
$("lJp").onclick = openJP;

/* ---------- бокове меню: мова, звук, підтримка ---------- */
function openDrawer() {
  const sw = (id, on) => `<span class="sw${on ? " on" : ""}" id="${id}"></span>`;
  $("drw").innerHTML = `<div class="dh"><img src="/a/i-settings.webp" alt=""><b>${T("settings")}</b></div>
  <div><span class="lbl">${T("language")}</span><div class="seg" id="dLang">${["🇺🇦 УКР", "🇷🇺 РУС", "🇬🇧 ENG"].map((x, i) => `<button class="${i === S.li ? "on" : ""}" data-l="${LANGS[i]}">${x}</button>`).join("")}</div></div>
  <button class="li" data-t="mus"><span>🎵 ${T("music")}</span>${sw("dMus", S.mus)}</button>
  <button class="li" data-t="snd"><span>🔊 ${T("sounds")}</span>${sw("dSnd", S.snd)}</button>
  <button class="li" data-t="vib"><span>📳 ${T("vibration")}</span>${sw("dVib", S.vib)}</button>
  <button class="li" id="dSup"><img src="/a/i-support.webp" alt=""><span>${T("sup_t")}</span></button>
  <button class="li" id="dTr"><img src="/a/i-robot.webp" alt=""><span>${T("training")}</span></button>
  ${S.me && S.me.admin ? `<button class="li" id="dAdm"><img src="/a/i-trophy.webp" alt=""><span>${T("admin")}</span></button>` : ""}
  <div class="dfoot">18+ · ${T("resp")}<br>${T("rake_note", Math.round((S.me ? S.me.cfg.rake : 0.1) * 100))}<br>Music: Kevin MacLeod (incompetech.com), CC BY 4.0</div>`;
  $("drawer").classList.add("on"); pushBack("drawer", closeDrawer); sfx.open();
}
function closeDrawer() { $("drawer").classList.remove("on"); popBack("drawer"); }
$("bMenu").onclick = openDrawer;
$("drawer").addEventListener("click", (e) => {
  if (e.target.classList.contains("bk")) { closeDrawer(); return; }
  const l = e.target.closest("[data-l]"); if (l) { setLang(l.dataset.l); sfx.click(); openDrawer(); popBack("drawer"); pushBack("drawer", closeDrawer); return; }
  const t = e.target.closest("[data-t]");
  if (t) {
    const k = t.dataset.t; S[k] = !S[k]; ls(k, S[k] ? "1" : "0");
    t.querySelector(".sw").classList.toggle("on", S[k]);
    if (k === "mus") MUS.sync(); if (k === "snd") sfx.click(); if (k === "vib") hap("medium");
    return;
  }
  if (e.target.closest("#dSup")) { closeDrawer(); openSup(); }
  if (e.target.closest("#dTr")) { closeDrawer(); openTraining(); }
  if (e.target.closest("#dAdm")) location.href = "/admin";
});

/* ---------- 18+ ---------- */
$("gateOk").onclick = async () => { try { await api("/api/age", {}); S.me.ageOk = true; $("gate").classList.remove("on"); sfx.win(); burst(50); } catch (e) { errToast(e); } };

/* зірочки над містом */
(function () { const h = $("hero"); [[12, 18], [28, 9], [47, 6], [66, 14], [83, 8], [92, 22], [7, 34], [74, 30]].forEach(([x, y], i) => { const s = document.createElement("i"); s.className = "tw"; s.style.left = x + "%"; s.style.top = y + "%"; s.style.animationDelay = -i * 0.37 + "s"; h.appendChild(s); }); })();
