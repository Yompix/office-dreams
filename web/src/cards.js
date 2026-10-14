/* ===== ПОКЕР, ДЖЕКПОТ, ДУЕЛІ (кості й монетка) ===== */
const POS6 = [[50, 87], [11, 62], [11, 27], [50, 9], [89, 27], [89, 62]];
const POS2 = [[50, 87], [50, 9]];
const PK_MS = 25000;

function sitDialog(id) {
  const t = S.L && S.L.tables.find((x) => x.id === id); if (!t || !S.me) return;
  const bal = S.me.balance;
  if (bal < t.min) { modal(`<h3>${T("e_no_funds")}</h3><p>${T("min_buyin", fmt(t.min), fmt(bal))}</p><button class="btn w" id="mGo">${T("topup")}</button>`); $("mGo").onclick = () => { closeModal(); go("wallet"); }; return; }
  const max = Math.min(t.max, bal), def = Math.min(max, Math.max(t.min, Math.round((t.min + max) / 2 / t.bb) * t.bb));
  modal(`<h3>${T("table")} ${fmt(t.sb)}/${fmt(t.bb)}${t.seats === 2 ? " · " + T("pk2") : ""}</h3><p>${T("buyin_p")}</p><div class="res" style="color:var(--gold);display:flex;align-items:center;justify-content:center;gap:8px"><i class="coin" style="width:26px;height:26px"></i><span id="byV">${fmt(def)}</span></div>
  <input type="range" id="byR" min="${t.min}" max="${max}" step="${t.bb}" value="${def}">
  <div class="btns"><button class="btn dk sm" data-v="${t.min}">${T("min")}</button><button class="btn dk sm" data-v="${def}">½</button><button class="btn dk sm" data-v="${max}">${T("max")}</button></div>
  <button class="btn gr w" id="byGo">${T("sit")}</button>`);
  const r = $("byR"); r.oninput = () => ($("byV").textContent = fmt(r.value));
  $$("#mbox [data-v]").forEach((b) => (b.onclick = () => { r.value = b.dataset.v; r.oninput(); sfx.chip(); }));
  $("byGo").onclick = () => { const buyin = Number(r.value); closeModal(); pkOpen(id, buyin); };
}
function pkOpen(id, buyin) {
  S.tblId = id; S.tbl = null;
  if (!wsSend({ t: "sub", sub: "pk:" + id })) return;
  wsSend({ t: "pk.sit", table: id, buyin }); sfx.chip();
  ovOpen("poker", pkBackAsk); renderTable();
}
function pkBackAsk() {
  const t = S.tbl, seated = t && t.you >= 0;
  if (!seated) { pkClose(true); return; }
  const me = t.seats[t.you], inHand = me && me.inHand && !me.folded && ["preflop", "flop", "turn", "river"].includes(t.phase);
  confirmBox(T("leave_q"), inHand ? T("leave_hand") : T("leave_p"), T("leave"), () => pkClose(), "rd");
}
function pkClose(force) {
  const seated = S.tbl && S.tbl.you >= 0;
  if (seated && !force) wsSend({ t: "pk.leave" });
  ovClose("poker"); S.tblId = 0; S.tbl = null; wsSend({ t: "sub", sub: "lobby" });
}
$("pkBack").onclick = pkBackAsk;
function pkAdopt(t) {
  if (S.tblId !== t.id) { S.tblId = t.id; S.tbl = null; }
  if (!$("poker").classList.contains("on")) ovOpen("poker", pkBackAsk);
  pkUpdate(t);
}
function pkUpdate(t) {
  if (!S.tblId || t.id !== S.tblId) return;
  const prev = S.tbl; S.tbl = t; S.tblAt = Date.now();
  pkSounds(prev, t); renderTable(prev);
}
function pkSounds(prev, t) {
  if (prev && t.hand === prev.hand && t.board.length > prev.board.length) sfx.card();
  if (t.you >= 0 && t.toAct === t.you && (!prev || prev.toAct !== t.you || prev.hand !== t.hand)) { sfx.turn(); hap("medium"); }
  if (t.phase === "result" && (!prev || prev.phase !== "result") && t.result) {
    const me = t.result.winners.find((w) => w.i === t.you);
    if (me && me.amount > 0) { sfx.big(); burst(70); hap("success"); } else if (t.you >= 0 && t.seats[t.you] && t.seats[t.you].inHand) sfx.lose();
  }
}
function renderTable(prev) {
  const t = S.tbl, tab = $("pkTab");
  $$(".seat,.pbet", tab).forEach((e) => e.remove());
  if (!t) { $("pkTitle").textContent = T("connecting"); $("pkBoard").innerHTML = ""; $("pkMine").innerHTML = ""; $("pkMsg").textContent = ""; $("pkPot").innerHTML = ""; $("pkDock").innerHTML = ""; return; }
  const n = t.seats.length, P = n === 2 ? POS2 : POS6, base = t.you >= 0 ? t.you : 0;
  $("pkTitle").textContent = `${fmt(t.sb)}/${fmt(t.bb)}` + (n === 2 ? " · " + T("pk2") : "");
  const newHand = !prev || prev.hand !== t.hand;
  t.seats.forEach((s, i) => {
    const [x, y] = P[(i - base + n) % n], d = document.createElement("div");
    d.className = "seat" + (s ? "" : " empty") + (s && s.folded ? " fold" : "") + (t.toAct === i ? " act" : "") + (i === t.you ? " me" : "");
    d.style.left = x + "%"; d.style.top = y + "%";
    if (!s) { d.innerHTML = `<div class="sn">${T("free")}</div>`; tab.appendChild(d); return; }
    let h = `<canvas width="10" height="10"></canvas><div class="sn"><b>${esc(s.name)}</b><span>${fmt(s.stack)}</span></div>`;
    if (i !== t.you && s.cards.length) h += `<div class="scards">${s.cards.map((c) => cardHtml(c, newHand ? "deal" : c >= 0 && prev && prev.seats[i] && prev.seats[i].cards[0] < 0 ? "flip" : "")).join("")}</div>`;
    if (s.last && t.phase !== "wait") h += `<div class="last">${esc(s.last)}</div>`;
    if (t.toAct === i) h += `<div class="tb"><i></i></div>`;
    d.innerHTML = h; drawAv(d.querySelector("canvas"), s.name + i); tab.appendChild(d);
    const lerp = (a, b, k) => a + (b - a) * k;
    if (s.bet > 0) { const b = document.createElement("div"); b.className = "pbet"; b.innerHTML = `<i class="coin"></i>${fmt(s.bet)}`; b.style.left = lerp(x, 50, 0.4) + "%"; b.style.top = lerp(y, 46, i === t.you ? 0.25 : 0.4) + "%"; tab.appendChild(b); }
    if (i === t.btn) { const b = document.createElement("div"); b.className = "pbet pdb"; b.textContent = "D"; b.style.left = lerp(x, 50, 0.25) + (x <= 50 ? 9 : -9) + "%"; b.style.top = lerp(y, 46, 0.25) + "%"; tab.appendChild(b); }
  });
  const pb = prev && prev.hand === t.hand ? prev.board.length : 0;
  $("pkBoard").innerHTML = [0, 1, 2, 3, 4].map((k) => cardHtml(t.board[k], t.board[k] !== undefined && k >= pb ? "deal" : "")).join("");
  $$("#pkBoard .deal").forEach((c, k) => (c.style.animationDelay = k * 0.12 + "s"));
  $("pkPot").innerHTML = t.pot ? `<i class="coin"></i>${T("pot")} ${fmt(t.pot)}` : "";
  let msg = "";
  if (t.phase === "wait") msg = t.seats.filter(Boolean).length < 2 ? T("pk_wait") : T("pk_soon");
  else if (t.phase === "result" && t.result) msg = t.result.winners.map((w) => `${w.name} +${fmt(Math.max(0, w.amount))}${w.hand ? " · " + w.hand : ""}`).join("\n");
  $("pkMsg").textContent = msg; $("pkMsg").className = "tmsg" + (t.phase === "result" ? " win" : "");
  const me = t.you >= 0 ? t.seats[t.you] : null, mine = $("pkMine");
  if (me && me.cards.length && me.cards[0] >= 0) { const was = !newHand && prev && prev.seats[t.you] && prev.seats[t.you].cards.length; mine.innerHTML = me.cards.map((c) => cardHtml(c, (was ? "" : "deal") + (me.folded ? " dimc" : ""))).join(""); $$("#pkMine .deal").forEach((c, k) => (c.style.animationDelay = 0.15 + k * 0.15 + "s")); }
  else mine.innerHTML = "";
  pkDock(); pkTick();
}
function pkDock() {
  const t = S.tbl, dk = $("pkDock"), me = t && t.you >= 0 ? t.seats[t.you] : null;
  if (!me) { dk.innerHTML = `<div class="msg2">${T("seat_wait")}</div>`; return; }
  const myTurn = t.toAct === t.you && ["preflop", "flop", "turn", "river"].includes(t.phase);
  if (!myTurn) {
    const who = t.toAct >= 0 && t.seats[t.toAct] ? T("acts", t.seats[t.toAct].name) : "";
    dk.innerHTML = `<div class="msg2">${t.phase === "result" ? T("hand_end") : me.folded ? T("folded") : who || "&nbsp;"}</div><button class="btn dk w sm" id="pkLv">${T("leave_tbl")}</button>`;
    $("pkLv").onclick = pkBackAsk; return;
  }
  const toCall = Math.max(0, t.cur - me.bet), callAmt = Math.min(toCall, me.stack), maxTo = me.bet + me.stack, minTo = Math.min(maxTo, t.cur + t.minRaise), canRaise = me.stack > toCall;
  dk.innerHTML = `<div class="rpan" id="rp" hidden><div class="big" id="rV"></div><input type="range" id="rR" min="${minTo}" max="${maxTo}" step="${Math.max(1, t.bb / 2)}" value="${minTo}"><div class="btns"><button class="btn dk xs" data-f=".5">½</button><button class="btn dk xs" data-f=".75">¾</button><button class="btn dk xs" data-f="1">${T("pot")}</button><button class="btn dk xs" data-f="9">ALL-IN</button></div><button class="btn pk w sm" id="rOk">${T("confirm")}</button></div>
  <div class="btns"><button class="btn rd" id="aF">${T("fold")}</button><button class="btn gr" id="aC">${toCall === 0 ? T("check") : callAmt >= me.stack ? "ALL-IN" : T("call") + " " + fmt(callAmt)}</button>${canRaise ? `<button class="btn pk" id="aR">${t.cur === 0 ? T("bet") : T("raise")}</button>` : ""}</div><div class="tline"><i id="pkLine"></i></div>`;
  $("aF").onclick = () => { wsSend({ t: "pk.act", a: "fold" }); sfx.card(); };
  $("aC").onclick = () => { wsSend({ t: "pk.act", a: toCall === 0 ? "check" : "call" }); sfx.chip(); };
  if (canRaise) {
    const r = $("rR"), v = $("rV"), upd = () => (v.textContent = fmt(r.value) + (Number(r.value) >= maxTo ? " · ALL-IN" : ""));
    $("aR").onclick = () => { $("rp").hidden = !$("rp").hidden; sfx.click(); }; r.oninput = upd; upd();
    $$("#rp [data-f]").forEach((b) => (b.onclick = () => { const f = Number(b.dataset.f); r.value = f >= 9 ? maxTo : Math.min(maxTo, Math.max(minTo, t.cur + Math.round(Math.max(t.minRaise, (t.pot + toCall) * f)))); upd(); sfx.chip(); }));
    $("rOk").onclick = () => { wsSend({ t: "pk.act", a: "raise", to: Number(r.value) }); sfx.chip(); };
  }
}
function pkTick() {
  const t = S.tbl; if (!t || t.left === null || t.toAct < 0) return;
  const left = Math.max(0, t.left - (Date.now() - S.tblAt)), pct = Math.min(100, (left / PK_MS) * 100) + "%";
  $$("#pkTab .seat.act .tb i").forEach((e) => (e.style.width = pct)); const l = $("pkLine"); if (l) l.style.width = pct;
}
setInterval(() => { if (S.tblId) pkTick(); }, 200);

/* ---------- джекпот: колесо ---------- */
const JP_COL = ["#ff2fb0", "#27e6ff", "#ffc933", "#3dff7a", "#b05cff", "#ff6a2a", "#ff5f7f", "#5f9bff"];
const JP_BETS = [50, 100, 500, 1000];
function openJP() { S.jpOpen = true; ovOpen("jp", () => { S.jpOpen = false; ovClose("jp"); }); renderJP(); }
function renderJP() {
  const L = S.L; if (!L) return; const j = L.jp, me = myId();
  $("jpPot").textContent = fmt(j.pot);
  const w = $("jpW");
  if (j.players.length) { let a = 0; const seg = j.players.map((p, i) => { const f = (p.amt / Math.max(1, j.pot)) * 360, s = `${JP_COL[i % JP_COL.length]} ${a}deg ${a + f}deg`; a += f; return s; }); w.style.background = `conic-gradient(${seg.join(",")})`; }
  else w.style.background = "repeating-conic-gradient(#2a1470 0 15deg,#1a0b40 15deg 30deg)";
  if (!$("jpBets").dataset.built) { $("jpBets").dataset.built = 1; $("jpBets").innerHTML = JP_BETS.map((v) => `<button class="chip" data-jb="${v}">+${fmt(v)}</button>`).join(""); $("jpBets").onclick = (e) => { const b = e.target.closest("[data-jb]"); if (b && wsSend({ t: "jp.bet", amount: Number(b.dataset.jb) })) { sfx.chip(); hap("medium"); } }; }
  $("jpList").innerHTML = j.players.length ? j.players.map((p, i) => `<div class="row"><i class="sw2" style="background:${JP_COL[i % JP_COL.length]};box-shadow:0 0 8px ${JP_COL[i % JP_COL.length]}"></i><div class="grow"><b>${esc(p.name)}${p.uid === me ? " · " + T("you") : ""}</b></div><span class="stk" style="font:400 15px var(--fd);color:var(--gold)">${fmt(p.amt)}</span><small class="dim" style="min-width:38px;text-align:right">${Math.round((p.amt / j.pot) * 100)}%</small></div>`).join("") : `<div class="empty">${j.last ? T("jp_last", esc(j.last.name), fmt(j.last.win)) : T("jp_empty")}</div>`;
  if (j.state === "spin" && S.jpSeen !== j.id) {
    S.jpSeen = j.id; w.classList.remove("spin"); w.style.transform = "rotate(0deg)"; void w.offsetWidth; w.classList.add("spin"); w.style.transform = `rotate(${360 * 6 - j.ticket * 360}deg)`;
    let k = 0; const iv = setInterval(() => { sfx.tick(); if (++k > 24) clearInterval(iv); }, 180);
    setTimeout(() => { const win = j.winner; if (!win) return; $("jpT").innerHTML = `<b style="font-size:15px;color:var(--mint)" class="jpwin">${esc(win.name)}</b>`; if (win.uid === me) { sfx.big(); burst(80); rain(80); hap("success"); } else sfx.lose(); }, 4800);
  } else if (j.state === "open" && !j.pot) { w.classList.remove("spin"); w.style.transform = "rotate(0deg)"; }
  jpTimer();
}
function jpTimer() {
  const j = S.L && S.L.jp; if (!j || j.state === "spin") return; const left = jpLeft();
  $("jpT").textContent = left === null ? (j.players.length ? T("jp_need") : T("jp_open")) : T("jp_in", Math.ceil(left / 1000));
}

/* ---------- дуелі: кості й монетка ---------- */
const DIE_P = { 1: [4], 2: [0, 8], 3: [0, 4, 8], 4: [0, 2, 6, 8], 5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8] };
/* 3D-кубик: шість граней, обертання до потрібної грані з кількома зайвими обертами */
const DIE_ROT = { 1: [0, 0], 2: [0, -90], 3: [-90, 0], 4: [90, 0], 5: [0, 90], 6: [0, 180] };
const DIE_FACE = { 1: "", 2: "rotateY(90deg)", 3: "rotateX(90deg)", 4: "rotateX(-90deg)", 5: "rotateY(-90deg)", 6: "rotateY(180deg)" };
const die3 = (side) => `<div class="d3w ${side}"><div class="d3">${[1, 2, 3, 4, 5, 6].map((n) => `<div class="f" style="transform:${DIE_FACE[n]} translateZ(37px)">${[...Array(9)].map((_, i) => `<i${DIE_P[n].includes(i) ? ' class="p"' : ""}></i>`).join("")}</div>`).join("")}</div><i class="d3s"></i></div>`;
function dieRoll(w, n, dl) {
  const d = w.querySelector(".d3"), [rx, ry] = DIE_ROT[n], sx = (Math.random() < 0.5 ? -1 : 1) * 360 * (2 + Math.floor(Math.random() * 2)), sy = (Math.random() < 0.5 ? -1 : 1) * 360 * 2;
  d.style.transform = `rotateX(${Math.random() * 360}deg) rotateY(${Math.random() * 360}deg) rotateZ(${Math.random() * 90}deg)`;
  void d.offsetWidth; d.style.transition = `transform 1.25s cubic-bezier(.15,.7,.25,1) ${dl}s`; d.style.transform = `rotateX(${rx + sx}deg) rotateY(${ry + sy}deg) rotateZ(0deg)`;
  w.style.animationDelay = dl + "s"; w.classList.add("throw");
}
const dieHtml = (n, cls) => `<div class="die ${cls || ""}">${[...Array(9)].map((_, i) => `<i${DIE_P[n].includes(i) ? ' class="p"' : ""}></i>`).join("")}</div>`;
function duelResult(r) {
  if (S.room) closeSheet();
  const me = myId(), won = r.winner === me, mineA = r.a.uid === me, opp = mineA ? r.b : r.a, my = mineA ? r.a : r.b;
  const head = `<h3>${T("g_" + r.game)} · <span class="gold">${fmt(r.stake)}</span></h3><p>${T("vs")} ${esc(opp.name)}</p>`;
  const fin = `<div class="res ${won ? "w" : "l"}">${won ? T("victory") + " +" + fmt(r.win - r.stake) : T("defeat") + " −" + fmt(r.stake)}</div><div class="btns"><button class="btn dk" id="dOk">OK</button><button class="btn ${won ? "gr" : "pk"}" id="dAg">${T("again")}</button></div>`;
  if (r.game === "dice") {
    modal(head + `<div class="dice3" id="dd">${die3("me")}<b class="dim">VS</b>${die3("op")}</div><div class="dnums" id="dn"><b>${esc(T("you"))}</b><b>${esc(opp.name)}</b></div><div id="dFin" style="display:flex;flex-direction:column;gap:14px;visibility:hidden">${fin}</div>`);
    const ws = $$("#dd .d3w"); dieRoll(ws[0], my.roll, 0); dieRoll(ws[1], opp.roll, 0.12);
    [0, 1, 2, 3, 4, 5, 6, 7].forEach((i) => setTimeout(() => beep(300 + Math.random() * 500, 0.03, "square", 0.025), 80 + i * 120));
    setTimeout(() => { sfx.place(); hap("medium"); }, 1150);
    setTimeout(() => {
      const d = $("dd"); if (!d) return; const W = ws[won ? 0 : 1], L = ws[won ? 1 : 0];
      W.classList.add("win"); L.classList.add("lo"); burstAt(W, 26, ["#ffc933", "#fff3a0", "#ffffff"]); flashAt(W, "#ffc933");
      $("dn").innerHTML = `<b class="${won ? "w" : ""}">${my.roll}</b><b class="${won ? "" : "w"}">${opp.roll}</b>`; done();
    }, 1500);
  } else {
    modal(head + `<div class="coinw"><div class="coin4 ${won ? "hw" : "tl"}"><i class="cf h">★</i><i class="cf t">✕</i></div><i class="cs"></i></div><div id="dFin" style="display:flex;flex-direction:column;gap:14px;visibility:hidden">${fin}</div>`);
    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].forEach((i) => setTimeout(() => beep(1500 + (i % 2) * 300, 0.02, "square", 0.02), i * 115));
    setTimeout(() => { sfx.place(); hap("medium"); }, 1300);
    setTimeout(() => { const c = $$(".coin4")[0]; if (!c) return; c.classList.add("land"); if (won) { burstAt(c, 30, ["#ffc933", "#fff3a0", "#ffffff"]); flashAt(c, "#ffc933"); } done(); }, 1550);
  }
  function done() {
    const f = $("dFin"); if (!f) return; f.style.visibility = "visible"; f.style.animation = "pop .4s cubic-bezier(.2,1.5,.4,1)";
    $("dOk").onclick = () => { closeModal(); adsInter(); }; $("dAg").onclick = () => { closeModal(); S.stake = r.stake; openRoom(r.game); };
    if (won) { sfx.big(); burst(70); hap("success"); } else { sfx.lose(); hap("error"); }
  }
}
