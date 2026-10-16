/* ===== ГАМАНЕЦЬ, ПРОФІЛЬ, ТЕХПІДТРИМКА, ЗАПУСК ===== */
const KIND = { dep_stars: "k_dep_stars", dep_ton: "k_dep_ton", dep_card: "k_dep_card", withdraw: "k_withdraw", wd_refund: "k_wd_refund", wd_fee_refund: "k_wd_refund", duel_win: "k_win", jp_win: "k_jp_win", mx_win: "k_win", mx_stake: "k_stake", mx_refund: "k_refund", mx_refund_c: "k_refund", pk_buyin: "k_pk_in", pk_cashout: "k_pk_out", grant: "k_grant", ref_bonus: "k_ref", ad_reward: "k_ad", offer: "k_offer", offer_back: "k_offer", refund_pk: "k_refund", refund_duel: "k_refund", refund_jp: "k_refund", refund_mx: "k_refund" };
const tDate = (t) => new Date(t).toLocaleString(["uk-UA", "ru-RU", "en-GB"][S.li], { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
let CARD = null;

$("wSeg").onclick = (e) => { const b = e.target.closest("[data-w]"); if (b) { S.wTab = b.dataset.w; sfx.click(); renderWallet(); } };
function renderWallet(opt) {
  if (opt) S.wTab = opt;
  $$("#wSeg button").forEach((b) => b.classList.toggle("on", b.dataset.w === S.wTab));
  const box = $("wBody"), c = S.me && S.me.cfg; if (!c) { box.innerHTML = `<div class="empty">${T("connecting")}</div>`; return; }
  if (S.wTab === "dep" && c.closed) { box.innerHTML = closedCard(); }
  else if (S.wTab === "dep") {
    const M = [["ton", "i-ton", "TON"], ["card", "i-card", T("m_card")]];
    box.innerHTML = `<div class="meth">${M.map(([k, ic, n]) => `<button data-m="${k}" class="${S.wMeth === k ? "on" : ""}"><img src="/a/${ic}.webp" alt="">${n}</button>`).join("")}</div><div id="wDep" style="display:flex;flex-direction:column;gap:12px"></div>`;
    $$(".meth [data-m]", box).forEach((b) => (b.onclick = () => { S.wMeth = b.dataset.m; sfx.click(); renderWallet(); }));
    const d = $("wDep");
    if (S.wMeth === "stars") {
      d.innerHTML = `<div class="packs">${c.packs.map((s) => `<button class="pack" data-s="${s}"><b>⭐ ${fmt(s)}</b><small><i class="coin"></i>${fmt(s * c.starCoins)}</small></button>`).join("")}</div><p class="dim" style="font-size:12px;text-align:center">${T("stars_note", c.starCoins)}</p>`;
      $$("[data-s]", d).forEach((b) => (b.onclick = () => buyStars(Number(b.dataset.s))));
    } else if (S.wMeth === "ton") {
      d.innerHTML = `<div class="card" style="display:flex;flex-direction:column;gap:10px"><div class="kv"><span class="dim">${T("rate")}</span><b>1 TON = ${fmt(c.tonCoins)} <i class="coin"></i></b></div>
      <span class="lbl" style="margin:0">${T("ton_addr")}</span><div class="copy"><span>${esc(c.tonAddr)}</span><button data-cp="${esc(c.tonAddr)}">${T("copy")}</button></div>
      <span class="lbl" style="margin:0">${T("ton_cmt")}</span><div class="copy"><span class="gold" style="font:400 16px var(--fd)">${esc(c.comment)}</span><button data-cp="${esc(c.comment)}">${T("copy")}</button></div>
      <button class="btn cy w" id="tOpen">${T("ton_open")}</button><p class="dim" style="font-size:12px">${T("ton_note")}</p></div>`;
      $("tOpen").onclick = () => openLink(`https://app.tonkeeper.com/transfer/${c.tonAddr}?text=${encodeURIComponent(c.comment)}`);
    } else cardDep(d);
  } else if (S.wTab === "wd") wdView(box);
  else histView(box);
  box.onclick = (e) => { const b = e.target.closest("[data-cp]"); if (b) copyTxt(b.dataset.cp); };
}
function closedCard() {
  const c = S.me.cfg;
  return `<div class="card" style="--bc:var(--gold);display:flex;flex-direction:column;gap:10px;text-align:center"><b class="fd" style="font-size:18px">${T("cl_t")}</b><p class="dim">${T("cl_p")}</p><p>${T("cl_wd")}${c.wdUntil ? " " + T("cl_until", esc(c.wdUntil)) : ""}</p><p class="dim" style="font-size:12px">${T("cl_off")}</p><button class="btn gr w" data-go="wallet" data-opt="wd">${T("cl_go")}</button></div>`;
}
function showClosed() {
  document.body.classList.add("closed");
  if (S.tab !== "wallet" && S.tab !== "profile") go("wallet", "wd");
  modal(`<h3>${T("cl_t")}</h3><p>${T("cl_p")}</p><p>${T("cl_wd")}${S.me.cfg.wdUntil ? " " + T("cl_until", esc(S.me.cfg.wdUntil)) : ""}</p><div class="btns"><button class="btn gr" id="clGo">${T("cl_go")}</button></div>`);
  $("clGo").onclick = () => { closeModal(); go("wallet", "wd"); };
}
async function buyStars(stars) {
  try {
    const r = await api("/api/stars/invoice", { stars }); sfx.click();
    if (tg && tg.openInvoice) tg.openInvoice(r.url, (st) => { if (st === "paid") { toast(T("paid_ok"), "good"); burst(50); setTimeout(refreshMe, 2500); } else if (st === "failed") toast(T("e_pay"), "bad"); });
    else openLink(r.url);
  } catch (e) { errToast(e); }
}
async function cardDep(d) {
  d.innerHTML = `<div class="empty">${T("loading")}</div>`;
  try { CARD = await api("/api/card/list"); } catch (e) { d.innerHTML = `<div class="empty">${errText(e)}</div>`; return; }
  const info = CARD.info, cur = S.cardCur || "USD";
  const open = CARD.deps.find((x) => x.status === "new");
  d.innerHTML = `<div class="card" style="display:flex;flex-direction:column;gap:12px">
   <div class="seg" id="cCur">${Object.keys(info.rates).map((k) => `<button class="${k === cur ? "on" : ""}" data-c="${k}">${k}</button>`).join("")}</div>
   <div class="kv"><span class="dim">${T("rate")}</span><b>1 ${cur} = ${fmt(info.rates[cur])} <i class="coin"></i></b></div>
   <div class="fld"><input id="cAmt" type="number" inputmode="decimal" min="${info.min[cur]}" placeholder="${T("amount")} ${cur} (${T("min")} ${info.min[cur]})"><span class="dim" id="cPrev"></span></div>
   <button class="btn w" id="cGo">${T("card_create")}</button></div>
   ${open ? cardPay(open) : ""}
   ${CARD.deps.length ? `<div class="sec">${T("card_reqs")}</div>` + CARD.deps.map((x) => `<div class="row"><div class="grow"><b>#C${x.id} · ${x.amount} ${x.cur}</b><small>${fmt(x.coins)} ${T("coins")}</small></div><b class="st-${x.status}">${T("st_" + x.status)}</b></div>`).join("") : ""}`;
  $$("#cCur [data-c]", d).forEach((b) => (b.onclick = () => { S.cardCur = b.dataset.c; cardDep(d); }));
  const a = $("cAmt"); a.oninput = () => { const v = Number(a.value) || 0; $("cPrev").innerHTML = v ? "≈ " + fmt(v * info.rates[cur]) : ""; };
  $("cGo").onclick = async () => { const amount = Number(a.value); if (!amount) return; try { await api("/api/card/dep", { cur, amount }); sfx.chip(); cardDep(d); } catch (e) { errToast(e); } };
  const cl = $("cClaim"); if (cl) cl.onclick = async () => { try { await api("/api/card/claim", { id: Number(cl.dataset.id) }); toast(T("card_claimed"), "good"); cardDep(d); } catch (e) { errToast(e); } };
}
const cardPay = (x) => `<div class="card" style="--bc:var(--gold);display:flex;flex-direction:column;gap:10px"><b class="fd" style="font-size:16px">${T("card_pay", x.amount, x.cur)}</b><div class="copy"><span style="font:400 17px var(--fd);letter-spacing:1px">${esc(CARD.cardFmt)}</span><button data-cp="${esc(CARD.card)}">${T("copy")}</button></div>${CARD.info.holder ? `<div class="kv"><span class="dim">${T("card_holder")}</span><b>${esc(CARD.info.holder)}</b></div>` : ""}<p class="dim" style="font-size:12px">${T("card_note")}</p><button class="btn gr w" id="cClaim" data-id="${x.id}">${T("card_paid")}</button></div>`;

async function wdView(box) {
  const c = S.me.cfg, how = S.wdHow || "ton";
  box.innerHTML = `<div class="seg" id="wdHow"><button class="${how === "ton" ? "on" : ""}" data-h="ton">TON</button><button class="${how === "card" ? "on" : ""}" data-h="card">${T("m_card")}</button></div>
  <div class="card" style="display:flex;flex-direction:column;gap:12px">
   <div class="fld"><i class="coin"></i><input id="wdC" type="number" inputmode="numeric" placeholder="${T("wd_coins", fmt(c.wdMinNow || c.wdMin))}"><button class="btn xs dk" id="wdAll">MAX</button></div>
   ${how === "ton" ? `<div class="fld"><input id="wdA" placeholder="${T("wd_addr")}" autocomplete="off"></div>` : `<div class="seg" id="wdCur"><button class="${(S.cardCur || "USD") === "USD" ? "on" : ""}" data-c="USD">USD</button><button class="${S.cardCur === "EUR" ? "on" : ""}" data-c="EUR">EUR</button></div><div class="fld"><input id="wdA" inputmode="numeric" placeholder="${T("wd_card")}" autocomplete="off"></div>`}
   <div class="kv"><span class="dim">${T("fee")}</span><b id="wdFee">${Math.round(c.wdFee * 100)}% (${T("min")} ${c.wdFeeMin})</b></div>
   <div class="kv"><span class="dim">${T("you_get")}</span><b id="wdGet" class="gold">—</b></div>
   <button class="btn pk w" id="wdGo">${T("w_wd")}</button><p class="dim" id="wdNote" style="font-size:12px"></p></div><div id="wdList"></div>`;
  $$("#wdHow [data-h]", box).forEach((b) => (b.onclick = () => { S.wdHow = b.dataset.h; sfx.click(); wdView(box); }));
  $$("#wdCur [data-c]", box).forEach((b) => (b.onclick = () => { S.cardCur = b.dataset.c; wdView(box); }));
  if (how === "card" && !CARD) { try { CARD = await api("/api/card/list"); } catch (e) {} }
  const inp = $("wdC");
  const prev = () => { const n = Math.floor(Number(inp.value) || 0); if (n < (c.wdMinNow || c.wdMin)) { $("wdGet").textContent = "—"; return; } const fee = Math.max(c.wdFeeMin, Math.ceil(n * c.wdFee)); $("wdFee").textContent = fmt(fee); const cur = S.cardCur || "USD"; $("wdGet").textContent = how === "ton" ? ((n - fee) / (c.tonOut || c.tonCoins)).toFixed(3) + " TON" : CARD ? (Math.floor(((n - fee) / (CARD.info.out || CARD.info.rates)[cur]) * 100) / 100).toFixed(2) + " " + cur : "…"; };
  inp.oninput = prev; $("wdAll").onclick = () => { inp.value = S.me.balance; prev(); };
  $("wdGo").onclick = async () => {
    const coins = Math.floor(Number(inp.value) || 0), addr = $("wdA").value.trim(); if (!coins || !addr) { toast(T("fill_all"), "bad"); return; }
    $("wdGo").disabled = true;
    try {
      if (how === "ton") await api("/api/withdraw", { coins, addr }); else await api("/api/card/wd", { cur: S.cardCur || "USD", coins, card: addr });
      toast(T("wd_ok"), "good"); sfx.win(); refreshMe(); wdView(box);
    } catch (e) { errToast(e); if (e.data && e.data.need) $("wdNote").textContent = T("need_turn", fmt(e.data.need)); }
    $("wdGo").disabled = false;
  };
  try {
    const w = await api("/api/wallet"), need = w.deposited - w.turnover;
    if (need > 0 && !c.closed && $("wdNote")) $("wdNote").textContent = T("need_turn", fmt(need));
    const rows = w.withdrawals.map((r) => `<div class="row"><div class="grow"><b>${r.ton} TON</b><small>${esc(r.addr.slice(0, 6))}…${esc(r.addr.slice(-4))} · ${tDate(r.t)}</small></div><b class="st-${r.status}">${T("st_" + r.status)}</b></div>`).concat(CARD ? CARD.wds.map((r) => `<div class="row"><div class="grow"><b>${r.amount} ${r.cur}</b><small>${T("m_card")}</small></div><b class="st-${r.status}">${T("st_" + r.status)}</b></div>`) : []);
    if (rows.length && $("wdList")) $("wdList").innerHTML = `<div class="sec" style="margin:4px 0 10px">${T("my_wd")}</div><div class="tlist">${rows.join("")}</div>`;
  } catch (e) {}
}
async function histView(box) {
  box.innerHTML = `<div class="empty">${T("loading")}</div>`;
  try {
    const w = await api("/api/wallet");
    box.innerHTML = w.ledger.length ? `<div class="tlist hist">${w.ledger.map((r) => `<div class="row"><div class="grow"><b>${KIND[r.kind] ? T(KIND[r.kind]) : esc(r.kind)}</b><small>${tDate(r.t)}${r.note && /^(chess|checkers|four|mines|rps|ttt|bj|card)\b/.test(r.note) ? " · " + T("g_" + r.note.split(" ")[0]) : ""}</small></div><b class="${r.amount > 0 ? "pos" : r.amount < 0 ? "neg" : ""}">${r.amount > 0 ? "+" : ""}${fmt(r.amount)}</b></div>`).join("")}</div>` : `<div class="empty">${T("no_ops")}</div>`;
  } catch (e) { box.innerHTML = `<div class="empty">${errText(e)}</div>`; }
}

/* ---------- профіль і друзі ---------- */
let REF = null;
async function renderProfile() {
  const m = S.me; if (!m) return;
  $("pName").textContent = m.name; $("pId").textContent = "ID " + String(m.id).replace(/^tg/, "") + (m.supporter ? " · 💜" : ""); drawAv($("pAv"), m.id);
  const body = $("pBody");
  const draw = () => {
    const r = REF || { n: 0, a: 0, earned: 0, link: "", friends: [], pct: m.cfg.refPct };
    body.innerHTML = `<div class="stats"><div class="stat"><b>${fmt(m.turnover)}</b><small>${T("turnover")}</small></div><div class="stat"><b>${fmt(m.deposited)}</b><small>${T("deposited")}</small></div><div class="stat"><b>${fmt(r.n)}</b><small>${T("friends")}</small></div></div>
    <div class="card" style="display:flex;flex-direction:column;gap:12px"><div class="refh"><img src="/a/i-friends.webp" alt=""><div><b>${T("ref_t")}</b><p>${T("ref_p", Math.round(r.pct * 100))}</p></div></div>
     ${r.link ? `<div class="copy"><span>${esc(r.link)}</span><button data-cp="${esc(r.link)}">${T("copy")}</button></div>` : ""}
     <div class="kv"><span class="dim">${T("ref_active")}</span><b>${fmt(r.a)} / ${fmt(r.n)}</b></div><div class="kv"><span class="dim">${T("ref_earned")}</span><b class="gold">+${fmt(r.earned)} <i class="coin"></i></b></div>
     <button class="btn pk w" id="pInv">${T("ref_invite")}</button></div>
    ${r.friends.length ? `<div class="sec">${T("friends")}</div><div class="tlist">${r.friends.map((f) => `<div class="row"><div class="grow"><b>${esc(f.name)}</b></div><b class="gold">+${fmt(f.earned)}</b></div>`).join("")}</div>` : ""}
    <div class="btns"><button class="btn dk sm" data-go="wallet" data-opt="hist">${T("w_hist")}</button><button class="btn dk sm" id="pSup">${T("sup_t")}</button></div>`;
    $("pInv").onclick = shareRef; $("pSup").onclick = openSup;
    body.onclick = (e) => { const b = e.target.closest("[data-cp]"); if (b) copyTxt(b.dataset.cp); };
  };
  draw();
  try { REF = await api("/api/ref"); if (S.tab === "profile") draw(); } catch (e) {}
}
async function shareRef() {
  if (!REF) { try { REF = await api("/api/ref"); } catch (e) { errToast(e); return; } }
  if (!REF.link) { toast(T("e_fail"), "bad"); return; }
  openLink("https://t.me/share/url?url=" + encodeURIComponent(REF.link) + "&text=" + encodeURIComponent(T("ref_msg")));
}

/* ---------- техпідтримка ---------- */
let SUPL = [];
async function supPoll(read) {
  try {
    const r = await api("/api/sup" + (read || S.supOpen ? "?read=1" : "")); SUPL = r.list;
    const d = $("supDot"); d.hidden = !r.unread || S.supOpen; d.textContent = r.unread;
    if (S.supOpen) renderSup();
    if (r.unread && !S.supOpen && !read) { /* нова відповідь — легкий сигнал */ sfx.turn(); }
  } catch (e) {}
}
function openSup() { S.supOpen = true; ovOpen("sup", closeSup); $("supDot").hidden = true; renderSup(); supPoll(true); }
function closeSup() { S.supOpen = false; ovClose("sup"); }
function renderSup() {
  const L = $("suL"), t = (x) => new Date(x).toLocaleTimeString(["uk-UA", "ru-RU", "en-GB"][S.li], { hour: "2-digit", minute: "2-digit" });
  let h = `<div class="suM sys">${T("sup_hi")}${SUPL.length ? "" : `<div class="chips" style="justify-content:center;margin-top:10px">${["sup_q1", "sup_q2", "sup_q3"].map((k) => `<button class="chip" style="color:#cfe0ff;font-size:13px" data-q="${k}">${T(k)}</button>`).join("")}</div>`}</div>`;
  h += SUPL.map((x) => `<div class="suM ${x.me ? "me" : "ag"}">${x.me ? "" : `<span class="n">${esc(x.agent)} · ${T("sup_t")}</span>`}${esc(x.text) || "📎"}<time>${t(x.at)}</time></div>`).join("");
  L.innerHTML = h; L.scrollTop = L.scrollHeight;
  $$("[data-q]", L).forEach((b) => (b.onclick = () => { $("suIn").value = T(b.dataset.q); supSend(); }));
}
async function supSend() {
  const i = $("suIn"), text = i.value.trim(); if (!text) return;
  $("suSend").disabled = true;
  try { const r = await api("/api/sup", { text }); SUPL = r.list; i.value = ""; i.style.height = ""; renderSup(); sfx.chip(); } catch (e) { errToast(e); }
  $("suSend").disabled = false;
}
$("suSend").onclick = supSend;
$("suIn").addEventListener("input", (e) => { const t = e.target; t.style.height = "auto"; t.style.height = Math.min(110, t.scrollHeight) + "px"; });
$("suIn").addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); supSend(); } });
$("bSup").onclick = openSup;

/* ---------- запуск ---------- */
async function refreshMe() {
  const m = await api("/api/me"); const first = !S.me; S.me = m;
  setBal(m.balance, !first);
  $("myName").textContent = m.name; drawAv($("myAv"), m.id);
  $("gate").classList.toggle("on", !m.ageOk);
  return m;
}
function rerenderAll() {
  $("gGrid").dataset.built = ""; $("jpBets").dataset.built = "";
  renderLive(); renderSp();
  if (S.tab === "games") renderGames(); else if (S.tab === "cards") renderCards(); else if (S.tab === "wallet") renderWallet(); else if (S.tab === "profile") renderProfile();
  if (S.room) renderRoom(); if (S.match) renderMatch(); if (S.tblId) renderTable(); if (S.jpOpen) renderJP(); if (S.supOpen) renderSup();
}
async function boot() {
  applyLang();
  $("hcIco").innerHTML = cardHtml(51) + cardHtml(24);
  ["lcCards", "lcGames", "lcJp"].forEach((id) => ($(id).querySelector("span").textContent = "…"));
  try { await refreshMe(); } catch (e) { errToast(e); }
  if (S.me && S.me.cfg && S.me.cfg.closed) showClosed();
  wsConnect(); spLoad(); supPoll();
  setInterval(() => { if (!document.hidden) refreshMe().catch(() => {}); }, 60000);
}
boot();
