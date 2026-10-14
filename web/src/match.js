/* ===== МАТЧІ 1 НА 1: шахи, шашки, неон-4, сапер, КНП, хрестики, 21, висока карта + тренування з ботом ===== */
const MX_MS = { rps: 20000, ttt: 25000, four: 25000, mines: 20000, bj: 30000, checkers: 60000, chess: 90000, card: 5000 };
const TURN_GAMES = ["ttt", "four", "mines", "checkers", "chess"];

/* ---------- піксельні шахові фігури ---------- */
const PIECE_PX = {
  p: ["............", "............", ".....##.....", "....####....", "....####....", ".....##.....", "....####....", ".....##.....", "....####....", "...######...", "..########..", "............"],
  r: ["............", "..##.##.##..", "..########..", "...######...", "....####....", "....####....", "....####....", "....####....", "...######...", "..########..", "..########..", "............"],
  n: ["............", ".....##.....", "....####....", "...######...", "..###.####..", "..########..", ".....#####..", "....#####...", "....####....", "...######...", "..########..", "............"],
  b: [".....##.....", "....####....", "...###.##...", "...##.###...", "...######...", "....####....", ".....##.....", "....####....", ".....##.....", "...######...", "..########..", "............"],
  q: [".#...##...#.", ".#...##...#.", ".##.####.##.", ".##########.", "..########..", "...######...", "....####....", "....####....", "...######...", "..########..", ".##########.", "............"],
  k: [".....##.....", "....####....", ".....##.....", "..##.##.##..", ".##########.", ".##########.", "..########..", "...######...", "....####....", "...######...", "..########..", ".##########."],
};
const PIECE = {};
for (const [k, rows] of Object.entries(PIECE_PX)) for (const white of [true, false]) {
  const body = white ? "#f6f1ff" : "#2c1266", shade = white ? "#b4a2f0" : "#1a0840", line = white ? "#140832" : "#ff5fc8", hi = white ? "#ffffff" : "#6a3ad0";
  PIECE[(white ? "w" : "b") + k] = pxCanvas(14, 14, (g) => {
    const on = (x, y) => rows[y] && rows[y][x] === "#";
    g.fillStyle = line; for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) if (on(x, y)) g.fillRect(x, y, 3, 3);
    for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) if (on(x, y)) { g.fillStyle = !on(x + 1, y) || !on(x, y + 1) ? shade : !on(x - 1, y) || !on(x, y - 1) ? hi : body; g.fillRect(x + 1, y + 1, 1, 1); }
  }, 4).toDataURL();
}
const FILES = "abcdefgh";
const sqName = (i) => FILES[i % 8] + (8 - Math.floor(i / 8));
const sqIdx = (n) => (8 - Number(n[1])) * 8 + FILES.indexOf(n[0]);
function fenBoard(fen) { const b = []; for (const r of fen.split(" ")[0].split("/")) for (const ch of r) { if (/\d/.test(ch)) for (let k = 0; k < +ch; k++) b.push(""); else b.push(ch); } return b; }

/* ---------- стан матчу ---------- */
function mxState(m) {
  const prev = S.match && S.match.m && S.match.m.id === m.id ? S.match.m : null;
  if (!prev) { closeSheet(); S.sel = null; }
  const wasMine = prev && myTurnOf(prev);
  S.match = { m, at: Date.now(), over: false, local: false, prev };
  S.pend = false;
  showMatch(); renderMatch();
  if (myTurnOf(m) && (!prev || !wasMine)) { sfx.turn(); hap("medium"); }
}
function mxEnd(e) {
  const m = e.m, prev = e.local ? S.match && S.match.prev : S.match && S.match.m && S.match.m.id === m.id ? S.match.m : null;
  if (!prev) closeSheet();
  S.match = { m, at: Date.now(), over: true, local: !!e.local, end: e, prev };
  S.pend = false; S.sel = null;
  showMatch(); renderMatch();
  const d = m.game === "card" ? 1800 : m.game === "rps" ? 2700 : e.reason === "boom" ? 1500 : e.reason === "four" || e.reason === "ttt" ? 1500 : 850;
  clearTimeout(S.endT); S.endT = setTimeout(() => endModal(e, m), d);
}
function myTurnOf(m) {
  if (!m) return false;
  if (m.game === "rps") return !m.st.mine;
  if (m.game === "bj") return !m.st.done;
  if (m.game === "card") return false;
  return m.turn === m.you;
}
function showMatch() { if (!$("match").classList.contains("on")) { ovOpen("match", mxBack); sfx.open(); } }
function mxBack() {
  const M = S.match;
  if (!M || M.over) { mxClose(); return; }
  if (M.local) { confirmBox(T("train_quit"), "", T("quit"), mxClose, "rd"); return; }
  if (M.m.game === "rps" || M.m.game === "card") { ovClose("match"); return; }
  askResign();
}
function mxHide() { ovClose("match"); }
function mxClose() { clearTimeout(S.endT); ovClose("match"); S.match = null; S.sel = null; }
function askResign() { const M = S.match; if (!M || M.over) return; confirmBox(T("resign_q"), M.local ? "" : T("resign_p", fmt(M.m.stake)), T("resign"), () => mxSendMove({ resign: true }), "rd"); }
$("mRes").onclick = askResign;
function mxSendMove(o) {
  const M = S.match; if (!M || M.over) return;
  if (M.local) { botMove(o); return; }
  if (wsSend({ t: "mx.move", id: M.m.id, ...o })) S.pend = !o.resign;
}

/* ---------- відмальовка ---------- */
function plSub(m, i) {
  const g = m.game;
  if (g === "chess" || g === "checkers") return i === 0 ? T("white") : T("black");
  if (g === "ttt") return i === 0 ? "✕" : "◯";
  if (g === "four") return i === m.you ? "🟡" : "🟣";
  return "";
}
function renderMatch() {
  const M = S.match; if (!M) return; const m = M.m, you = m.you, opp = 1 - you, g = m.game;
  $("mTitle").textContent = (M.local ? "🤖 " : "") + T("g_" + g);
  $("mPot").textContent = M.local ? T("training") : fmt(m.stake * 2);
  $("mPot").parentElement.querySelector(".coin").hidden = M.local;
  const fill = (el, i) => { drawAv(el.querySelector("canvas"), m.names[i] + (M.local && i === 1 ? "bot" : "")); el.querySelector("b").textContent = i === you ? m.names[i] + " · " + T("you") : m.names[i]; el.querySelector("small").textContent = plSub(m, i); };
  fill($("mOpp"), opp); fill($("mMe"), you);
  const myT = !M.over && myTurnOf(m), oppT = !M.over && (TURN_GAMES.includes(g) ? m.turn === opp : g === "rps" ? !m.st.opp : g === "bj" ? !m.st.oppDone : false);
  $("mMe").classList.toggle("act", myT); $("mOpp").classList.toggle("act", oppT);
  $("mRes").hidden = M.over || g === "rps" || g === "card";
  const st = $("mSt"); st.className = "mst" + (myT ? " me" : "");
  st.textContent = M.over ? "" : myT ? (g === "rps" ? T("pick") : g === "bj" ? T("bj_turn") : T("your_turn")) : g === "rps" || g === "bj" ? T("opp_thinks") : T("opp_turn");
  if (S.pend && !M.over) st.textContent = "…";
  $("mFoot").innerHTML = "";
  $$(".capt").forEach((c) => (c.innerHTML = ""));
  const box = $("mBoard");
  ({ chess: rChess, checkers: rCheckers, four: rFour, mines: rMines, ttt: rTtt, rps: rRps, bj: rBj, card: rCard }[g] || (() => (box.innerHTML = "")))(m, M, box);
  mxTimer();
}
function fit(el, ratio, max) {
  const b = $("mBoard"), w = b.clientWidth - 24, h = b.clientHeight - 8;
  const bw = Math.max(160, Math.min(w, h * ratio, max || 460)); el.style.width = bw + "px"; el.style.height = bw / ratio + "px"; return bw;
}
/* куди «стрибає» фігура: зсув від клітинки from до to в пікселях */
function moveAnim(el, fromD, toD, sq) { if (fromD < 0) return; const dx = ((fromD % 8) - (toD % 8)) * sq, dy = (Math.floor(fromD / 8) - Math.floor(toD / 8)) * sq; el.style.setProperty("--dx", dx + "px"); el.style.setProperty("--dy", dy + "px"); el.classList.add("mv"); }

function rChess(m, M, box) {
  const s = m.st, b = fenBoard(s.fen), flip = m.you === 1, myC = m.you === 0 ? "w" : "b";
  const d2i = (d) => (flip ? 63 - d : d);
  const moves = s.moves || [], last = s.last ? [sqIdx(s.last[0]), sqIdx(s.last[1])] : null;
  const pl = M.prev && M.prev.st && M.prev.st.last, isNew = last && (!pl || pl[0] !== s.last[0] || pl[1] !== s.last[1]);
  const turnC = s.fen.split(" ")[1];
  const kingAt = s.check ? b.findIndex((p) => p === (turnC === "w" ? "K" : "k")) : -1;
  const sel = S.sel, tgs = sel !== null && sel !== undefined ? moves.filter((x) => sqIdx(x[0]) === sel).map((x) => sqIdx(x[1])) : [];
  const canFrom = new Set(moves.map((x) => sqIdx(x[0])));
  let h = `<div class="b8" id="bd">`;
  for (let d = 0; d < 64; d++) {
    const i = d2i(d), r = Math.floor(i / 8), c = i % 8, p = b[i];
    const cls = ["sq", (r + c) % 2 ? "d" : "l"];
    if (last && (i === last[0] || i === last[1])) cls.push("lm");
    if (i === sel) cls.push("sel"); if (tgs.includes(i)) cls.push("tg", p ? "cap" : ""); if (i === kingAt) cls.push("chk"); if (canFrom.has(i) || tgs.includes(i)) cls.push("can");
    h += `<div class="${cls.join(" ")}" data-i="${i}">${p ? `<span class="pc" data-p="${i}" style="background-image:url(${PIECE[(p === p.toUpperCase() ? "w" : "b") + p.toLowerCase()]})"></span>` : ""}${Math.floor(d / 8) === 7 ? `<span class="co">${FILES[i % 8]}</span>` : ""}</div>`;
  }
  box.innerHTML = h + "</div>";
  const bd = $("bd"), sq = fit(bd, 1) / 8;
  if (isNew && !M.over) { const pe = bd.querySelector(`[data-p="${last[1]}"]`), fromD = flip ? 63 - last[0] : last[0], toD = flip ? 63 - last[1] : last[1]; if (pe) moveAnim(pe, fromD, toD, sq); sfx.move();
    const pc = M.prev && M.prev.st && M.prev.st.cap ? M.prev.st.cap.length : 0, tsq = bd.querySelector(`[data-i="${last[1]}"]`);
    if ((s.cap || []).length > pc) setTimeout(() => { burstAt(tsq, 18, ["#ff5fc8", "#ffffff", "#ffc933"]); flashAt(tsq, "#ff2fb0"); sfx.place(); hap("medium"); }, 260);
    if (s.check) setTimeout(() => { shakeEl(bd); beep(880, 0.12, "square", 0.04); beep(660, 0.16, "square", 0.04, 0.12); hap("warning"); }, 300);
  }
  // забрані фігури
  const capOf = (col) => (s.cap || []).filter((x) => x[0] === col).map((x) => `<i style="background-image:url(${PIECE[x]})"></i>`).join("");
  $("mMe").querySelector(".capt").innerHTML = capOf(myC === "w" ? "b" : "w");
  $("mOpp").querySelector(".capt").innerHTML = capOf(myC);
  bd.onclick = (e) => {
    const q = e.target.closest(".sq"); if (!q || M.over || !myTurnOf(m) || S.pend) return; const i = Number(q.dataset.i);
    if (S.sel !== null && S.sel !== undefined && tgs.includes(i)) {
      const opts = moves.filter((x) => sqIdx(x[0]) === S.sel && sqIdx(x[1]) === i), from = sqName(S.sel), to = sqName(i);
      if (opts.length > 1 || (opts[0] && opts[0][2])) { promoPick(myC, (pr) => { S.sel = null; mxSendMove({ from, to, promo: pr }); renderMatch(); }); return; }
      S.sel = null; mxSendMove({ from, to }); renderMatch(); return;
    }
    S.sel = canFrom.has(i) && S.sel !== i ? i : null; if (S.sel !== null) sfx.click(); renderMatch();
  };
}
function promoPick(col, cb) {
  const w = document.createElement("div"); w.className = "promo";
  w.innerHTML = ["q", "r", "b", "n"].map((k) => `<button data-k="${k}" style="background-image:url(${PIECE[col + k]})"></button>`).join("");
  w.onclick = (e) => { const b = e.target.closest("[data-k]"); w.remove(); if (b) cb(b.dataset.k); };
  $("mBoard").appendChild(w);
}

function rCheckers(m, M, box) {
  const s = m.st, b = s.b, flip = m.you === 1, d2i = (d) => (flip ? 63 - d : d);
  const steps = s.steps || [], canFrom = new Set(steps.map((x) => x[0]));
  if (s.chain >= 0 && myTurnOf(m)) S.sel = s.chain;
  const sel = S.sel, tgs = sel !== null && sel !== undefined ? steps.filter((x) => x[0] === sel).map((x) => x[1]) : [];
  const last = s.last, pl = M.prev && M.prev.st && M.prev.st.last, isNew = last && (!pl || pl[0] !== last[0] || pl[1] !== last[1]);
  let h = `<div class="b8" id="bd">`;
  for (let d = 0; d < 64; d++) {
    const i = d2i(d), r = Math.floor(i / 8), c = i % 8, p = b[i];
    const cls = ["sq", (r + c) % 2 ? "d" : "l"];
    if (last && (i === last[0] || i === last[1])) cls.push("lm"); if (i === sel) cls.push("sel"); if (tgs.includes(i)) cls.push("tg"); if (canFrom.has(i) || tgs.includes(i)) cls.push("can");
    h += `<div class="${cls.join(" ")}" data-i="${i}">${p ? `<span class="ck ${p.toLowerCase()}${p === p.toUpperCase() ? " K" : ""}" data-p="${i}"></span>` : ""}</div>`;
  }
  box.innerHTML = h + "</div>";
  const bd = $("bd"), sq = fit(bd, 1) / 8;
  if (isNew) {
    const pe = bd.querySelector(`[data-p="${last[1]}"]`); if (pe) moveAnim(pe, flip ? 63 - last[0] : last[0], flip ? 63 - last[1] : last[1], sq); sfx.move();
    const cnt = (a) => a.filter((x) => x).length, pb = M.prev && M.prev.st && M.prev.st.b;
    if (pb && cnt(b) < cnt(pb)) { const fx = Math.abs(last[0] - last[1]) > 9, mid = bd.querySelector(`[data-i="${fx ? (last[0] + last[1]) / 2 : last[1]}"]`) || bd.querySelector(`[data-i="${last[1]}"]`); setTimeout(() => { burstAt(mid, 16, ["#27e6ff", "#ff2fb0", "#ffffff"]); flashAt(mid, "#ffffff"); sfx.place(); hap("medium"); }, 200); }
    if (b[last[1]] && b[last[1]] === b[last[1]].toUpperCase() && pb && pb[last[0]] && pb[last[0]] !== pb[last[0]].toUpperCase()) { const k = bd.querySelector(`[data-p="${last[1]}"]`); if (k) k.classList.add("crown"); setTimeout(() => { burstAt(k, 20, ["#ffc933", "#fff3a0"]); sfx.win(); }, 300); }
  }
  bd.onclick = (e) => {
    const q = e.target.closest(".sq"); if (!q || M.over || !myTurnOf(m) || S.pend) return; const i = Number(q.dataset.i);
    if (S.sel !== null && S.sel !== undefined && tgs.includes(i)) { const from = S.sel; S.sel = null; mxSendMove({ from, to: i }); renderMatch(); return; }
    if (s.chain >= 0) return;
    S.sel = canFrom.has(i) && S.sel !== i ? i : null; if (S.sel !== null) sfx.click(); renderMatch();
  };
}

function rFour(m, M, box) {
  const s = m.st, me = m.you + 1, line = s.line || [], pb = M.prev && M.prev.st && M.prev.st.b;
  const isNew = s.last >= 0 && (!pb || !pb[s.last]);
  const my = !M.over && myTurnOf(m) && !S.pend;
  let h = `<div class="b4${my ? " my" : ""}" id="bd">`;
  for (let i = 0; i < 42; i++) { const v = s.b[i]; h += `<div class="c" data-c="${i % 7}">${v ? `<i class="${v === me ? "me" : "op"}${i === s.last && isNew ? " drop" : ""}${line.includes(i) ? " wl" : ""}" style="--r:${Math.floor(i / 7)}"></i>` : ""}</div>`; }
  box.innerHTML = h + "</div>";
  const bd = $("bd"); fit(bd, 7 / 6, 440);
  if (isNew) setTimeout(() => { sfx.place(); const d = bd.querySelector("i.drop"); if (d) d.parentElement.classList.add("land"); hap("light"); }, 120 + Math.floor(s.last / 7) * 60);
  if (line.length && M.over && isNew) setTimeout(() => line.forEach((k, j) => setTimeout(() => { const c = bd.children[k]; if (c) { c.classList.add("wlc"); burstAt(c, 10, ["#ffffff", "#ffc933", "#ff2fb0"]); beep(600 + j * 150, 0.08, "square", 0.04); } }, j * 110)), 450);
  bd.onclick = (e) => { const c = e.target.closest("[data-c]"); if (!c || !my) return; const col = Number(c.dataset.c); if (s.b[col]) return; mxSendMove({ col }); renderMatch(); };
}

function rMines(m, M, box) {
  const s = m.st, po = M.prev && M.prev.st && M.prev.st.open, bombs = new Set(s.bombs || []), my = !M.over && myTurnOf(m) && !S.pend;
  let h = `<div><div class="bm${my ? " my" : ""}" id="bd">`;
  for (let i = 0; i < 36; i++) {
    const v = s.open[i], nw = po && po[i] === -1 && v !== -1;
    if (v === "X") h += `<button class="o bomb boom" data-c="${i}">💥</button>`;
    else if (v === -1) h += bombs.has(i) ? `<button class="o bomb${M.over ? " nw" : ""}" data-c="${i}">💣</button>` : `<button data-c="${i}"></button>`;
    else h += `<button class="o n${v}${nw ? " nw" : ""}" data-c="${i}">${v || ""}</button>`;
  }
  box.innerHTML = h + `</div><div class="mleft">${T("mines_left", s.left)}</div></div>`;
  const bd = $("bd"); fit(bd, 1, 400);
  if (po && s.open.some((v, i) => po[i] === -1 && v !== -1)) {
    if (s.boom >= 0) { const c = bd.querySelector(`[data-c="${s.boom}"]`); sfx.boom(); hap("error"); flashAt(c, "#ff6a2a"); burstAt(c, 40, ["#ff6a2a", "#ffc933", "#ff4d6d", "#ffffff", "#5a4a8a"]); shakeEl($("match"), "bigshk"); bd.querySelectorAll(".bomb:not(.boom)").forEach((e, j) => (e.style.animationDelay = 0.25 + j * 0.08 + "s")); }
    else { sfx.click(); bd.querySelectorAll(".nw").forEach((c) => burstAt(c, 4, ["#27e6ff", "#ffffff"])); }
  }
  bd.onclick = (e) => { const c = e.target.closest("[data-c]"); if (!c || !my || c.classList.contains("o")) return; mxSendMove({ cell: Number(c.dataset.c) }); };
}

function rTtt(m, M, box) {
  const s = m.st, pb = M.prev && M.prev.st && M.prev.st.b, my = !M.over && myTurnOf(m) && !S.pend;
  let h = `<div class="bt" id="bd">`;
  const XO = { X: `<svg viewBox="0 0 100 100"><path d="M24 24L76 76"/><path d="M76 24L24 76"/></svg>`, O: `<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="27"/></svg>` };
  for (let i = 0; i < 9; i++) { const v = s.b[i]; h += `<button class="${v}${v && (!pb || !pb[i]) && pb ? " nw" : ""}" data-c="${i}">${XO[v] || ""}</button>`; }
  const wl = M.over ? T_LINES.find((l) => s.b[l[0]] && l.every((k) => s.b[k] === s.b[l[0]])) : null;
  if (wl) { const P = (k) => [(k % 3) * 33.33 + 16.67, Math.floor(k / 3) * 33.33 + 16.67], a = P(wl[0]), z = P(wl[2]), ex = (z[0] - a[0]) * 0.18, ey = (z[1] - a[1]) * 0.18; h += `<svg class="twl ${s.b[wl[0]]}" viewBox="0 0 100 100"><path d="M${a[0] - ex} ${a[1] - ey}L${z[0] + ex} ${z[1] + ey}"/></svg>`; }
  box.innerHTML = h + "</div>";
  const bd = $("bd"); fit(bd, 1, 330);
  if (pb && s.b.some((v, i) => v && !pb[i])) { sfx.place(); hap("light"); }
  if (wl && pb && !S.tttWl) { S.tttWl = 1; setTimeout(() => { S.tttWl = 0; wl.forEach((k, j) => setTimeout(() => burstAt(bd.children[k], 10, s.b[wl[0]] === "X" ? ["#ff2fb0", "#ffffff"] : ["#27e6ff", "#ffffff"]), j * 90)); beep(700, 0.1, "square", 0.04); beep(1050, 0.14, "square", 0.04, 0.1); }, 300); }
  bd.onclick = (e) => { const c = e.target.closest("[data-c]"); if (!c || !my || s.b[c.dataset.c]) return; mxSendMove({ cell: Number(c.dataset.c) }); };
}

/* КНП: руки качаються «камінь… ножиці… папір!», потім показ жестів, удар, ефект перемоги */
function rpsHand(el, k, side) { if (!el) return; const i = el.querySelector("img"); i.src = HAND.url(k, side); el.dataset.k = k; }
function rRps(m, M, box) {
  const s = m.st, over = M.over, nH = s.hist.length;
  let w = box.querySelector(".rps");
  if (!w || w.dataset.mid !== String(m.id) + (M.local ? "L" : "")) {
    box.innerHTML = `<div class="rps" data-mid="${m.id}${M.local ? "L" : ""}"><div class="rounds"></div>
    <div class="arena"><div class="rh op" id="rhO"><img alt=""></div><div class="rbub" id="rBub"></div><div class="rcall" id="rCall"><span>VS</span></div><div class="rh me" id="rhM"><img alt=""></div></div>
    <div class="pick">${["r", "p", "s"].map((k) => `<button data-k="${k}"><img src="${HAND.url(k, "me")}" alt=""><span>${T("rps_" + k)}</span></button>`).join("")}</div></div>`;
    w = box.querySelector(".rps"); rpsHand($("rhO"), "r", "op"); rpsHand($("rhM"), "r", "me");
    S.rpsSeen = over ? "" : m.id + ":" + nH; S.rpsBusy = 0;
    w.querySelector(".pick").onclick = (e) => { const b = e.target.closest("[data-k]"), st = S.match && S.match.m.st; if (!b || !st || st.mine || S.match.over || S.rpsBusy) return; sfx.chip(); hap("light"); st.mine = b.dataset.k; mxSendMove({ move: b.dataset.k }); renderMatch(); };
  }
  w.querySelector(".rounds").innerHTML = [1, 2, 3, 4, 5].map((r) => `<i class="${r < s.round ? "on" : r === s.round ? "cur" : ""}"></i>`).join("");
  // що показувати: фінал або щойно зіграну нічию
  const tok = over ? m.id + ":end" : m.id + ":" + nH;
  if (tok !== S.rpsSeen && (over ? s.mine && typeof s.opp === "string" : nH > 0)) {
    S.rpsSeen = tok;
    const h = over ? { me: s.mine, opp: s.opp } : s.hist[nH - 1];
    const wn = M.end ? (M.local ? M.end.winner === 0 : M.end.winner === myId()) : false;
    const res = h.me === h.opp ? 0 : over ? (wn ? 1 : -1) : 0;
    rpsPlay(h, res, () => { if (S.match && S.match.m.id === m.id) renderMatch(); });
  }
  const busy = S.rpsBusy > Date.now();
  const bub = $("rBub");
  if (!busy && !over) { bub.className = "rbub" + (s.opp ? " ok" : ""); bub.innerHTML = s.opp ? "✓ " + T("rps_ready") : "<i></i><i></i><i></i>"; }
  else if (!busy) bub.className = "rbub off";
  if (!busy && !over) { $("rCall").innerHTML = s.mine ? `<small>${T("rps_wait")}</small>` : `<span>VS</span>`; ["rhO", "rhM"].forEach((id) => { const e = $(id); if (!e.classList.contains("idle")) { e.className = "rh " + (id === "rhO" ? "op" : "me") + " idle"; rpsHand(e, "r", id === "rhO" ? "op" : "me"); } }); }
  const pk = w.querySelector(".pick");
  pk.classList.toggle("off", busy || over);
  $$("button", pk).forEach((b) => { b.disabled = !!s.mine; b.classList.toggle("on", s.mine === b.dataset.k); });
  if (busy) $("mSt").textContent = "";
}
function rpsPlay(h, res, done) {
  const hm = $("rhM"), ho = $("rhO"), call = $("rCall"), bub = $("rBub"), ar = hm && hm.parentElement;
  if (!hm || !ho) return;
  S.rpsBusy = Date.now() + 2500;
  bub.className = "rbub off";
  rpsHand(hm, "r", "me"); rpsHand(ho, "r", "op"); hm.className = "rh me pump"; ho.className = "rh op pump";
  const words = T("rps_chant").split("|");
  words.forEach((wd, i) => setTimeout(() => { if (!call.isConnected) return; call.innerHTML = `<b class="cw${i === 2 ? " last" : ""}">${wd}</b>`; beep(i === 2 ? 330 : 200, 0.07, "square", 0.05); hap("light"); }, 60 + i * 340));
  setTimeout(() => {
    if (!hm.isConnected) return;
    rpsHand(hm, h.me, "me"); rpsHand(ho, h.opp, "op"); hm.className = "rh me pop"; ho.className = "rh op pop"; sfx.chip(); hap("medium");
  }, 1080);
  setTimeout(() => {
    if (!hm.isConnected) return;
    if (!res) {
      hm.className = "rh me pop tie"; ho.className = "rh op pop tie"; flashAt(call, "#27e6ff"); burstAt(call, 16, ["#27e6ff", "#ffffff", "#b05cff"]);
      call.innerHTML = `<b class="cw tie">${T("rps_again")}</b>`; beep(520, 0.12, "triangle", 0.05); beep(520, 0.12, "triangle", 0.05, 0.14); hap("warning");
      return;
    }
    const W = res > 0 ? hm : ho, L = res > 0 ? ho : hm, g = res > 0 ? h.me : h.opp;
    W.className = W.className.replace(" pop", "") + " win"; L.className = L.className.replace(" pop", "") + " lose hit-" + g;
    flashAt(call, res > 0 ? "#ffc933" : "#ff4d6d"); shakeEl(ar);
    burstAt(L, g === "r" ? 26 : 20, g === "r" ? ["#9a8ad0", "#5a4a8a", "#ffffff"] : g === "s" ? ["#ffffff", "#bff8ff", "#27e6ff"] : ["#ffffff", "#fff3a0", "#e9e2ff"]);
    call.innerHTML = `<b class="cw ${res > 0 ? "good" : "bad"}">${T("rps_w_" + g)}</b>`;
    g === "r" ? sfx.boom() : g === "s" ? (beep(1800, 0.05, "square", 0.04), beep(1400, 0.06, "square", 0.04, 0.06)) : beep(400, 0.25, "sine", 0.06, 0, 900);
  }, 1380);
  setTimeout(() => { S.rpsBusy = 0; done && done(); }, 2500);
}

function rBj(m, M, box) {
  const s = m.st, pr = M.prev && M.prev.st, over = M.over;
  const oppCards = over ? s.opp : [s.opp[0], ...Array(Math.max(0, s.oppN - 1)).fill(-1)];
  const oppCls = (k) => (!pr ? "deal" : over ? (pr.oppT === null && k >= 1 ? "flip" : k >= pr.oppN ? "deal" : "") : k >= pr.oppN ? "deal" : "");
  const myCls = (k) => (!pr || k >= pr.mine.length ? "deal" : "");
  box.innerHTML = `<div class="bj"><div><div class="hand">${oppCards.map((c, k) => cardHtml(c, oppCls(k))).join("")}</div><div class="tot${s.oppT > 21 ? " bust" : ""}">${T("total")} <b>${over ? s.oppT : "?"}</b>${s.oppDone && !over ? " · " + T("stood") : ""}</div></div>
  <div><div class="tot${s.mineT > 21 ? " bust" : ""}">${T("total")} <b>${s.mineT}</b>${s.done && !over ? " · " + T("stood") : ""}</div><div class="hand">${s.mine.map((c, k) => cardHtml(c, myCls(k))).join("")}</div></div></div>`;
  if (!pr || s.mine.length > pr.mine.length || s.oppN > pr.oppN || (over && pr.oppT === null)) sfx.card();
  const tots = box.querySelectorAll(".tot");
  if (pr && s.mineT > 21 && pr.mineT <= 21) setTimeout(() => { shakeEl(tots[1]); sfx.lose(); hap("error"); }, 350);
  if (pr && s.mineT === 21 && pr.mineT !== 21) setTimeout(() => { tots[1].classList.add("bj21"); burstAt(tots[1], 24, ["#ffc933", "#fff3a0", "#ffffff"]); sfx.win(); hap("success"); }, 350);
  if (s.mineT === 21) tots[1].classList.add("bj21");
  if (over && pr && pr.oppT === null && s.oppT > 21) setTimeout(() => shakeEl(tots[0]), 600);
  if (!over) {
    const dis = s.done || S.pend ? " disabled" : "";
    $("mFoot").innerHTML = `<button class="btn cy" id="bjHit"${dis}>${T("bj_hit")}</button><button class="btn pk" id="bjStand"${dis}>${T("bj_stand")}</button>`;
    $("bjHit").onclick = () => { mxSendMove({ act: "hit" }); renderMatch(); };
    $("bjStand").onclick = () => { mxSendMove({ act: "stand" }); renderMatch(); };
  }
}

function rCard(m, M, box) {
  const s = m.st, me = s.mine, op = s.opp, won = M.end && M.end.winner === myId();
  box.innerHTML = `<div class="hc2"><div id="hcO">${cardHtml(-1, "deal")}</div><em>VS</em><div id="hcM">${cardHtml(-1, "deal")}</div></div>`;
  if (me === undefined) return;
  setTimeout(() => { const o = $("hcO"); if (o) { o.innerHTML = cardHtml(op, "flip" + (!won ? " win" : "")); sfx.card(); } }, 600);
  setTimeout(() => { const x = $("hcM"); if (x) { x.innerHTML = cardHtml(me, "flip" + (won ? " win" : "")); sfx.card(); } }, 1050);
  setTimeout(() => { const w = $(won ? "hcM" : "hcO"), l = $(won ? "hcO" : "hcM"); if (!w) return; w.classList.add("hcw"); l.classList.add("hcl"); burstAt(w, 26, ["#ffc933", "#fff3a0", "#ffffff"]); flashAt(w, "#ffc933"); }, 1500);
}

/* таймер ходу */
setInterval(() => { if (S.match && $("match").classList.contains("on")) mxTimer(); }, 200);
function mxTimer() {
  const M = S.match; if (!M) return; const m = M.m, tot = MX_MS[m.game] || 25000;
  const left = M.local ? tot : Math.max(0, m.left - (Date.now() - M.at)), sec = Math.ceil(left / 1000);
  for (const id of ["mMe", "mOpp"]) { const r = $(id).querySelector(".ring"); r.style.setProperty("--p", (left / tot).toFixed(3)); r.querySelector("span").textContent = M.local ? "∞" : sec; r.classList.toggle("low", !M.local && left < 5000); }
  if (!M.local && !M.over && myTurnOf(m) && left < 5000 && sec !== S.lastSec) { S.lastSec = sec; sfx.tick(); }
}

/* фінал */
function endModal(e, m) {
  if (!S.match || S.match.m !== m) return;
  const me = e.local ? 0 : myId(), won = e.local ? e.winner === 0 : e.winner === me, draw = e.local ? e.winner === null : !e.winner;
  const why = { timeout: won ? "why_to_w" : "why_to_l", resign: won ? "why_rs_w" : "why_rs_l", boom: won ? "why_boom_w" : "why_boom_l", mate: "why_mate", draw: "why_draw", cards: "why_cards", bj: "why_bj", four: "why_four", ttt: "why_ttt", checkers: won ? "why_checkers" : "why_checkers_l", rps: "why_rps" }[e.reason];
  const amt = e.local ? "" : won ? `<div class="res w">+${fmt(e.win - m.stake)} <i class="coin" style="width:24px;height:24px"></i></div>` : draw ? `<div class="res d">${T("refunded")}</div>` : `<div class="res l">−${fmt(m.stake)}</div>`;
  modal(`<h3 class="${won ? "gold" : ""}" style="font-size:30px">${won ? T("victory") : draw ? T("draw") : T("defeat")}</h3>${why ? `<p>${T(why)}</p>` : ""}${amt}<div class="btns"><button class="btn dk" id="eLobby">${T("to_lobby")}</button><button class="btn ${won ? "gr" : "pk"}" id="eAgain">${T("again")}</button></div>`);
  $("eLobby").onclick = () => { closeModal(); mxClose(); go("lobby"); if (!e.local) adsInter(); };
  $("eAgain").onclick = () => { closeModal(); mxClose(); if (!e.local) adsInter(); if (e.local) botStart(m.game); else { S.stake = m.stake; openRoom(m.game); } };
  if (won) { sfx.big(); hap("success"); burst(70); setTimeout(() => burst(50, innerWidth * 0.25, innerHeight * 0.35), 250); setTimeout(() => burst(50, innerWidth * 0.75, innerHeight * 0.35), 450); if (!e.local) rain(60); }
  else if (draw) { sfx.click(); hap("warning"); } else { sfx.lose(); hap("error"); }
}

/* ---------- тренування з ботом (без монет) ---------- */
function openTraining() {
  sheet(`<div class="shH"><span class="gi"><img src="/a/i-robot.webp" alt=""></span><div><h3>${T("training")}</h3><p>${T("train_p")}</p></div><button class="x" data-xs>✕</button></div>
  <div class="grid3" style="padding-top:14px;grid-template-columns:1fr 1fr">${["ttt", "four", "rps", "mines"].map((g) => `<button class="gt" data-bot="${g}">${gIcon(g)}<b>${T("g_" + g)}</b><small>${T("vs_bot")}</small></button>`).join("")}</div>`);
  $("shIn").querySelector("[data-xs]").onclick = closeSheet;
  $$("[data-bot]", $("shIn")).forEach((b) => (b.onclick = () => { closeSheet(); botStart(b.dataset.bot); }));
}
const F_LINES = (() => { const L = []; for (let r = 0; r < 6; r++) for (let c = 0; c < 7; c++) for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) { const l = []; for (let k = 0; k < 4; k++) { const rr = r + dr * k, cc = c + dc * k; if (rr < 0 || rr > 5 || cc < 0 || cc > 6) break; l.push(rr * 7 + cc); } if (l.length === 4) L.push(l); } return L; })();
const T_LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
const fourDrop = (b, col) => { for (let r = 5; r >= 0; r--) if (!b[r * 7 + col]) return r * 7 + col; return -1; };
const fourLine = (b, v) => F_LINES.find((l) => l.every((k) => b[k] === v));
function botStart(game) {
  const st = game === "ttt" ? { b: Array(9).fill("") } : game === "four" ? { b: Array(42).fill(0), last: -1, line: null } : game === "rps" ? { round: 1, mine: null, opp: false, hist: [] } : { n: 6, open: Array(36).fill(-1), bombs: null, boom: -1, left: 29 };
  const bombs = new Set(); while (bombs.size < 7) bombs.add(Math.floor(Math.random() * 36));
  S.match = { m: { id: -1, game, stake: 0, you: 0, names: [S.me ? S.me.name : "Player", T("bot")], turn: 0, left: 0, st }, at: Date.now(), local: true, over: false, prev: null, bombs, first: true };
  S.sel = null; S.pend = false; showMatch(); renderMatch();
}
function botEnd(w, reason) { const M = S.match; mxEnd({ local: true, winner: w, reason, win: 0, m: M.m }); }
function botStep(fn) { const M = S.match; M.prev = JSON.parse(JSON.stringify(M.m)); fn(M.m.st); M.at = Date.now(); }
function botMove(o) {
  const M = S.match, m = M.m, s = m.st, g = m.game;
  if (o.resign) { botStep(() => {}); botEnd(1, "resign"); return; }
  if (g === "rps") {
    const bot = "rps"[Math.floor(Math.random() * 3)];
    botStep((st) => { st.mine = o.move; st.opp = true; }); renderMatch();
    setTimeout(() => {
      if (o.move === bot) { botStep((st) => { st.hist.push({ me: o.move, opp: bot }); if (st.round >= 5) { st.opp = bot; } else { st.round++; st.mine = null; st.opp = false; } }); if (s.round >= 5 && s.opp === bot) { botEnd(null, "draw"); return; } renderMatch(); return; }
      botStep((st) => (st.opp = bot)); const beats = { r: "s", s: "p", p: "r" }; botEnd(beats[o.move] === bot ? 0 : 1, "rps");
    }, 700);
    return;
  }
  if (m.turn !== 0) return;
  const after = () => { m.turn = 1; renderMatch(); setTimeout(botReply, 450 + Math.random() * 450); };
  if (g === "ttt") { if (s.b[o.cell]) return; botStep((st) => (st.b[o.cell] = "X")); if (T_LINES.some((l) => l.every((k) => s.b[k] === "X"))) return botEnd(0, "ttt"); if (s.b.every((x) => x)) return botEnd(null, "draw"); after(); }
  else if (g === "four") { const at = fourDrop(s.b, o.col); if (at < 0) return; botStep((st) => { st.b[at] = 1; st.last = at; }); const l = fourLine(s.b, 1); if (l) { s.line = l; return botEnd(0, "four"); } if (s.b.every((x) => x)) return botEnd(null, "draw"); after(); }
  else if (g === "mines") { if (!minesOpen(o.cell, 0)) after(); }
}
function minesOpen(c, who) {
  const M = S.match, s = M.m.st;
  if (s.open[c] !== -1) return true;
  if (M.first && M.bombs.has(c)) { M.bombs.delete(c); let k; do { k = Math.floor(Math.random() * 36); } while (k === c || M.bombs.has(k)); M.bombs.add(k); }
  M.first = false;
  if (M.bombs.has(c)) { botStep((st) => { st.open[c] = "X"; st.boom = c; st.bombs = [...M.bombs].filter((x) => x !== c); }); botEnd(1 - who, "boom"); return true; }
  const r = Math.floor(c / 6), cc = c % 6; let n = 0; for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) { const rr = r + dr, c2 = cc + dc; if ((dr || dc) && rr >= 0 && rr < 6 && c2 >= 0 && c2 < 6 && M.bombs.has(rr * 6 + c2)) n++; }
  botStep((st) => { st.open[c] = n; st.left = 29 - st.open.filter((x) => typeof x === "number" && x >= 0).length; });
  if (s.left <= 0) { botEnd(null, "draw"); return true; }
  return false;
}
function botReply() {
  const M = S.match; if (!M || !M.local || M.over) return; const m = M.m, s = m.st, g = m.game;
  const done = () => { m.turn = 0; renderMatch(); sfx.turn(); };
  if (g === "ttt") {
    const free = s.b.map((v, i) => (v ? -1 : i)).filter((i) => i >= 0);
    const winAt = (v) => free.find((i) => T_LINES.some((l) => l.includes(i) && l.every((k) => k === i || s.b[k] === v)));
    let c = winAt("O"); if (c === undefined) c = winAt("X"); if (c === undefined && !s.b[4]) c = 4;
    if (c === undefined) { const cs = free.filter((i) => [0, 2, 6, 8].includes(i)); c = (cs.length && Math.random() < 0.7 ? cs : free)[Math.floor(Math.random() * (cs.length && Math.random() < 0.7 ? cs.length : free.length))]; }
    if (c === undefined || s.b[c]) c = free[Math.floor(Math.random() * free.length)];
    botStep((st) => (st.b[c] = "O")); if (T_LINES.some((l) => l.every((k) => s.b[k] === "O"))) return botEnd(1, "ttt"); if (s.b.every((x) => x)) return botEnd(null, "draw"); done();
  } else if (g === "four") {
    const cols = [0, 1, 2, 3, 4, 5, 6].filter((c) => fourDrop(s.b, c) >= 0);
    const tryV = (v) => cols.find((c) => { const b = s.b.slice(); b[fourDrop(b, c)] = v; return fourLine(b, v); });
    let c = tryV(2); if (c === undefined) c = tryV(1);
    if (c === undefined) {
      const safe = cols.filter((c) => { const b = s.b.slice(); b[fourDrop(b, c)] = 2; return !cols.some((c2) => { const at = fourDrop(b, c2); if (at < 0) return false; const b2 = b.slice(); b2[at] = 1; return fourLine(b2, 1); }); });
      const pool = safe.length ? safe : cols; const wgt = pool.map((c) => 4 - Math.abs(3 - c) + Math.random() * 2.5); c = pool[wgt.indexOf(Math.max(...wgt))];
    }
    const at = fourDrop(s.b, c); botStep((st) => { st.b[at] = 2; st.last = at; }); const l = fourLine(s.b, 2); if (l) { s.line = l; return botEnd(1, "four"); } if (s.b.every((x) => x)) return botEnd(null, "draw"); done();
  } else if (g === "mines") {
    const closed = s.open.map((v, i) => (v === -1 ? i : -1)).filter((i) => i >= 0);
    if (!minesOpen(closed[Math.floor(Math.random() * closed.length)], 1)) done();
  }
}
