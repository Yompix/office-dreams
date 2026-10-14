/* ===== ЯДРО: стан, мова, API, звук, ефекти, навігація ===== */
const tg = window.Telegram && Telegram.WebApp && Telegram.WebApp.initData !== undefined ? Telegram.WebApp : null;
if (tg) { try { tg.ready(); tg.expand(); tg.setHeaderColor("#0b0624"); tg.setBackgroundColor("#0b0624"); tg.disableVerticalSwipes && tg.disableVerticalSwipes(); } catch (e) {} }
const $ = (id) => document.getElementById(id);
const $$ = (sel, root) => [...(root || document).querySelectorAll(sel)];
const ls = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } };
const esc = (s) => String(s ?? "").replace(/[<>&"]/g, (c) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;" }[c]));
const fmt = (n) => { n = Math.round(Number(n) || 0); const s = String(Math.abs(n)).replace(/\B(?=(\d{3})+(?!\d))/g, " "); return (n < 0 ? "−" : "") + s; };
const devUser = () => { let v = ls("dev"); if (!v) { v = Math.random().toString(36).slice(2, 8); ls("dev", v); } return v; };

const S = {
  me: null, L: null, tab: "lobby", li: 0,
  snd: (ls("snd") ?? "1") === "1", vib: (ls("vib") ?? "1") === "1", mus: (ls("mus") ?? "1") === "1",
  stake: Number(ls("stake")) || 100, pkMode: "6", wTab: "dep", wMeth: "ton",
  match: null, tbl: null, tblId: 0, tblAt: 0, room: null, jpOpen: false, jpSeen: 0, supOpen: false, stack: [],
};

/* ---------- мова ---------- */
const LANGS = ["uk", "ru", "en"];
(function () {
  let l = ls("lang");
  if (!LANGS.includes(l)) {
    const c = String((tg && tg.initDataUnsafe && tg.initDataUnsafe.user && tg.initDataUnsafe.user.language_code) || navigator.language || "").slice(0, 2).toLowerCase();
    l = c === "uk" ? "uk" : ["ru", "be", "kk", "uz", "ky", "tg", "az", "hy", "ka"].includes(c) ? "ru" : c ? "en" : "uk";
  }
  S.li = LANGS.indexOf(l);
})();
function T(k, ...a) { const v = DICT[k]; let s = v ? v[S.li] ?? v[0] : k; a.forEach((x, i) => (s = s.split("{" + i + "}").join(x))); return s; }
function applyLang() {
  document.documentElement.lang = LANGS[S.li];
  $$("[data-i]").forEach((e) => (e.textContent = T(e.dataset.i)));
  $$("[data-ip]").forEach((e) => (e.placeholder = T(e.dataset.ip)));
}
function setLang(l) { S.li = Math.max(0, LANGS.indexOf(l)); ls("lang", LANGS[S.li]); applyLang(); rerenderAll(); }

/* ---------- API ---------- */
async function api(path, body) {
  let r, j;
  try {
    r = await fetch(path, { method: body !== undefined ? "POST" : "GET", headers: { "Content-Type": "application/json", "x-init-data": (tg && tg.initData) || "", "x-dev-user": devUser() }, body: body !== undefined ? JSON.stringify(body) : undefined });
    j = await r.json();
  } catch (e) { throw Object.assign(new Error("net"), { data: {} }); }
  if (!r.ok) throw Object.assign(new Error(j.error || "fail"), { data: j });
  return j;
}
const errText = (e) => { const k = "e_" + ((e && e.message) || e); return DICT[k] ? T(k) : T("e_fail"); };
const errToast = (e) => toast(errText(e), "bad");

/* ---------- звук і вібрація ---------- */
let AC;
const getAC = () => { AC = AC || new (window.AudioContext || window.webkitAudioContext)(); if (AC.state === "suspended") AC.resume(); return AC; };
function beep(f, d = 0.08, type = "square", vol = 0.035, delay = 0, f2) {
  if (!S.snd) return;
  try {
    const a = getAC(), o = a.createOscillator(), g = a.createGain(), t = a.currentTime + delay;
    o.type = type; o.frequency.setValueAtTime(f, t); if (f2) o.frequency.exponentialRampToValueAtTime(f2, t + d);
    o.connect(g); g.connect(a.destination); g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + d); o.start(t); o.stop(t + d + 0.02);
  } catch (e) {}
}
const sfx = {
  click: () => beep(620, 0.04, "square", 0.025),
  tab: () => { beep(440, 0.05, "triangle", 0.05); beep(880, 0.06, "triangle", 0.04, 0.04); },
  open: () => beep(300, 0.18, "triangle", 0.05, 0, 900),
  tick: () => beep(1000, 0.025, "square", 0.02),
  card: () => beep(260, 0.06, "triangle", 0.06, 0, 140),
  chip: () => { beep(1300, 0.04, "square", 0.025); beep(1650, 0.04, "square", 0.025, 0.045); },
  place: () => beep(180, 0.09, "sine", 0.12, 0, 90),
  move: () => { beep(330, 0.05, "triangle", 0.06); beep(220, 0.06, "triangle", 0.05, 0.05); },
  win: () => [523, 659, 784, 1047].forEach((f, i) => beep(f, 0.14, "square", 0.04, i * 0.09)),
  big: () => [523, 659, 784, 1047, 1319, 1568].forEach((f, i) => beep(f, 0.16, "square", 0.04, i * 0.08)),
  lose: () => { beep(260, 0.18, "sawtooth", 0.035, 0, 180); beep(180, 0.3, "sawtooth", 0.035, 0.15, 110); },
  turn: () => { beep(784, 0.08, "square", 0.035); beep(1175, 0.1, "square", 0.035, 0.09); },
  boom: () => { beep(120, 0.5, "sawtooth", 0.08, 0, 30); beep(80, 0.6, "square", 0.05, 0.05, 25); },
};
const hap = (k) => { if (!S.vib || !tg || !tg.HapticFeedback) return; try { k === "success" || k === "error" || k === "warning" ? tg.HapticFeedback.notificationOccurred(k) : tg.HapticFeedback.impactOccurred(k || "light"); } catch (e) {} };

/* ---------- музика: лаунж-джаз з інтернету (Kevin MacLeod, incompetech.com, CC BY 4.0) ---------- */
const MUS = (function () {
  const IC = "https://incompetech.com/music/royalty-free/mp3-royaltyfree/";
  const PL = ["Lobby Time", "Bossa Antigua", "Hep Cats", "Smooth Lovin", "Investigations", "Cool Vibes"].sort(() => Math.random() - 0.5);
  const RADIO = ["https://ice1.somafm.com/illstreet-128-mp3", "https://ice2.somafm.com/secretagent-128-mp3"];
  let i = 0, fails = 0, radio = false, pausedAt = 0;
  const au = new Audio(); au.preload = "none"; au.volume = 0.32;
  const src = () => { au.src = radio ? RADIO[i % RADIO.length] : IC + encodeURIComponent(PL[i % PL.length]) + ".mp3"; };
  src();
  const play = () => { if (!au.paused) return; if (radio && pausedAt && Date.now() - pausedAt > 20000) src(); const p = au.play(); if (p && p.catch) p.catch(() => {}); };
  const pause = () => { if (!au.paused) { au.pause(); pausedAt = Date.now(); } };
  au.addEventListener("ended", () => { i++; src(); if (S.mus && !document.hidden) play(); });
  au.addEventListener("error", () => { if (++fails > 12) return; i++; if (!radio && fails >= PL.length) { radio = true; i = 0; } src(); if (S.mus && !document.hidden) play(); });
  au.addEventListener("playing", () => { fails = 0; });
  const kick = () => { if (S.mus && au.paused && !document.hidden) play(); };
  document.addEventListener("pointerdown", kick, { passive: true });
  document.addEventListener("touchend", kick, { passive: true });
  document.addEventListener("visibilitychange", () => { if (document.hidden) pause(); else if (S.mus) play(); if (AC) document.hidden ? AC.suspend() : AC.resume(); });
  return { sync: () => (S.mus ? play() : pause()), pause };
})();

/* ---------- тости, модалка, шторка, оверлеї ---------- */
function toast(msg, kind) { const d = document.createElement("div"); d.className = "tst" + (kind ? " " + kind : ""); d.textContent = msg; $("toasts").appendChild(d); setTimeout(() => d.remove(), 2800); }
async function copyTxt(txt) { try { await navigator.clipboard.writeText(txt); toast(T("copied"), "good"); } catch (e) { toast(txt); } }
function openLink(u) { if (tg && /^https:\/\/t\.me\//.test(u) && tg.openTelegramLink) tg.openTelegramLink(u); else if (tg && tg.openLink) tg.openLink(u); else window.open(u, "_blank"); }

function pushBack(id, close) { S.stack = S.stack.filter((x) => x.id !== id); S.stack.push({ id, close }); syncBack(); }
function popBack(id) { S.stack = S.stack.filter((x) => x.id !== id); syncBack(); }
function syncBack() { if (!tg || !tg.BackButton) return; S.stack.length || S.tab !== "lobby" ? tg.BackButton.show() : tg.BackButton.hide(); }
function goBack() { const top = S.stack[S.stack.length - 1]; if (top) top.close(); else if (S.tab !== "lobby") go("lobby"); }
if (tg && tg.BackButton) tg.BackButton.onClick(goBack);

function modal(html, onClose) { $("mbox").innerHTML = html; $("modal").classList.add("on"); S.modalClose = onClose; pushBack("modal", closeModal); }
function closeModal() { $("modal").classList.remove("on"); popBack("modal"); if (S.modalClose) { const f = S.modalClose; S.modalClose = null; f(); } }
$("modal").addEventListener("click", (e) => { if (e.target.id === "modal") closeModal(); });
function confirmBox(title, text, yes, onYes, cls) {
  modal(`<h3>${title}</h3>${text ? `<p>${text}</p>` : ""}<div class="btns"><button class="btn dk" id="cNo">${T("cancel")}</button><button class="btn ${cls || "pk"}" id="cYes">${yes}</button></div>`);
  $("cNo").onclick = closeModal; $("cYes").onclick = () => { closeModal(); onYes(); };
}

function sheet(html, onClose) { $("shIn").innerHTML = html; $("sheet").classList.add("on"); S.sheetClose = onClose; pushBack("sheet", closeSheet); sfx.open(); }
function closeSheet() { if (!$("sheet").classList.contains("on")) return; $("sheet").classList.remove("on"); popBack("sheet"); S.room = null; if (S.sheetClose) { const f = S.sheetClose; S.sheetClose = null; f(); } }
$("sheet").addEventListener("click", (e) => { if (e.target.id === "sheet") closeSheet(); });

function ovOpen(id, close) { $(id).classList.add("on"); pushBack(id, close || (() => ovClose(id))); }
function ovClose(id) { $(id).classList.remove("on"); popBack(id); }
document.addEventListener("click", (e) => { const c = e.target.closest("[data-close]"); if (c) { sfx.click(); const id = c.dataset.close; const s = S.stack.find((x) => x.id === id); s ? s.close() : ovClose(id); } });

/* ---------- піксельна графіка: монета, аватарки, іконки ---------- */
function pxCanvas(w, h, fn, scale) { const c = document.createElement("canvas"); c.width = w; c.height = h; const g = c.getContext("2d"); fn(g); if (!scale) return c; const b = document.createElement("canvas"); b.width = w * scale; b.height = h * scale; const bg = b.getContext("2d"); bg.imageSmoothingEnabled = false; bg.drawImage(c, 0, 0, b.width, b.height); return b; }
const COIN = pxCanvas(12, 12, (g) => {
  for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) {
    const d = Math.hypot(x - 5.5, y - 5.5); if (d > 6) continue;
    g.fillStyle = d > 5 ? "#9a4a00" : d > 4.2 ? "#ffd84a" : d > 3.4 ? "#d08a10" : (x + y < 9 ? "#fff3a0" : "#ffc933");
    g.fillRect(x, y, 1, 1);
  }
  g.fillStyle = "#b06a10"; g.fillRect(5, 4, 2, 4); g.fillStyle = "#fffbe0"; g.fillRect(3, 2, 2, 1); g.fillRect(2, 3, 1, 2);
}, 4).toDataURL();
document.documentElement.style.setProperty("--coin", `url(${COIN})`);

const hashStr = (s) => { let h = 2166136261; for (const ch of String(s)) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; };
const AV_COL = [["#ff2fb0", "#5a0a3a"], ["#27e6ff", "#0a3a5a"], ["#ffc933", "#5a3a0a"], ["#3dff7a", "#0a4a2a"], ["#b05cff", "#2a0a5a"], ["#ff6a2a", "#5a1a0a"]];
function drawAv(cv, seed) {
  if (!cv) return; const h = hashStr(seed), [fg, bg] = AV_COL[h % AV_COL.length]; cv.width = 10; cv.height = 10; const g = cv.getContext("2d");
  g.fillStyle = bg; g.fillRect(0, 0, 10, 10);
  for (let y = 0; y < 8; y++) for (let x = 0; x < 4; x++) if ((h >>> ((y * 4 + x) % 31)) & 1) { g.fillStyle = y === 2 || y === 3 ? "#ffffff" : fg; g.fillRect(1 + x, 1 + y, 1, 1); g.fillRect(8 - x, 1 + y, 1, 1); }
}

/* ефекти: конфеті з піксельних квадратиків */
const fxc = $("fx"), fg = fxc.getContext("2d"); let parts = [], fxRun = false;
const fxSize = () => { fxc.width = innerWidth; fxc.height = innerHeight; }; fxSize(); addEventListener("resize", fxSize);
function burst(n = 40, x = innerWidth / 2, y = innerHeight * 0.4, cols) {
  cols = cols || ["#ffc933", "#ff2fb0", "#27e6ff", "#3dff7a", "#ffffff"];
  for (let i = 0; i < n; i++) parts.push({ x, y, vx: (Math.random() - 0.5) * 13, vy: -Math.random() * 12 - 3, s: 4 + Math.floor(Math.random() * 3) * 2, c: cols[i % cols.length], life: 60 + Math.random() * 40 });
  if (!fxRun) { fxRun = true; requestAnimationFrame(fxLoop); }
}
function rain(n = 70) { for (let i = 0; i < n; i++) setTimeout(() => burst(1, Math.random() * innerWidth, -10, ["#ffc933", "#fff3a0"]), i * 25); }
function fxLoop() {
  fg.clearRect(0, 0, fxc.width, fxc.height); parts = parts.filter((p) => p.life > 0 && p.y < fxc.height + 20);
  for (const p of parts) { p.x += p.vx; p.y += p.vy; p.vy += 0.33; p.vx *= 0.99; p.life--; fg.globalAlpha = Math.min(1, p.life / 20); fg.fillStyle = p.c; fg.fillRect(Math.round(p.x / 3) * 3, Math.round(p.y / 3) * 3, p.s, p.s); }
  fg.globalAlpha = 1;
  if (parts.length) requestAnimationFrame(fxLoop); else { fxRun = false; fg.clearRect(0, 0, fxc.width, fxc.height); }
}

/* ---------- баланс із анімацією лічильника ---------- */
let balShown = 0, balAnim = 0;
function setBal(n, animate) {
  const from = balShown, to = Math.round(n); if (S.me) S.me.balance = to;
  cancelAnimationFrame(balAnim);
  const paint = (v) => $$(".balTxt").forEach((e) => (e.textContent = fmt(v)));
  if (!animate || from === to) { balShown = to; paint(to); return; }
  const b = $("bBal"); b.classList.remove("up", "down"); void b.offsetWidth; b.classList.add(to > from ? "up" : "down");
  const t0 = performance.now(), D = 650;
  const step = (t) => { const k = Math.min(1, (t - t0) / D), e = 1 - Math.pow(1 - k, 3); balShown = Math.round(from + (to - from) * e); paint(balShown); if (k < 1) balAnim = requestAnimationFrame(step); };
  balAnim = requestAnimationFrame(step);
}

/* ---------- WebSocket ---------- */
let ws, wsTimer, wsFails = 0;
function wsConnect() {
  clearTimeout(wsTimer);
  const q = new URLSearchParams({ init: (tg && tg.initData) || "", dev: devUser() });
  ws = new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws?" + q);
  ws.onopen = () => { wsFails = 0; wsSend({ t: "sub", sub: S.tblId ? "pk:" + S.tblId : "lobby" }); wsSend({ t: "mx.sync" }); wsSend({ t: "pk.where" }); };
  ws.onmessage = (e) => { try { onMsg(JSON.parse(e.data)); } catch (x) { console.error(x); } };
  ws.onclose = () => { wsTimer = setTimeout(wsConnect, Math.min(5000, 800 + 400 * wsFails++)); };
}
const wsSend = (o) => { if (ws && ws.readyState === 1) { ws.send(JSON.stringify(o)); return true; } toast(T("e_net"), "bad"); return false; };
function onMsg(m) {
  switch (m.t) {
    case "lobby": S.L = m; renderLive(); break;
    case "pk": if (m.back && m.table.you >= 0) pkAdopt(m.table); else pkUpdate(m.table); break;
    case "bal": { const d = m.balance - (S.me ? S.me.balance : m.balance); setBal(m.balance, true); if (d > 0 && S.tab === "wallet") { toast("+" + fmt(d), "good"); sfx.win(); } break; }
    case "err": errToast(m.e); rmFail(); if (S.tblId && (!S.tbl || S.tbl.you < 0) && $("poker").classList.contains("on")) pkClose(true); break;
    case "duel.result": duelResult(m); break;
    case "rm.req": rmReq(m); break;
    case "mx.state": mxState(m.m); break;
    case "mx.end": mxEnd(m); break;
    case "sup": supPoll(); break;
  }
}

/* ---------- навігація між вкладками ---------- */
const TABS = ["lobby", "games", "cards", "wallet", "profile"];
function go(tab, opt) {
  if (!TABS.includes(tab)) return;
  const was = S.tab; S.tab = tab;
  $$(".view").forEach((v) => v.classList.toggle("on", v.id === "v-" + tab));
  $$(".nb").forEach((b) => b.classList.toggle("on", b.dataset.go === tab));
  if (was !== tab) { sfx.tab(); hap("light"); }
  syncBack();
  if (tab === "games") renderGames(); else if (tab === "cards") renderCards(); else if (tab === "wallet") renderWallet(opt); else if (tab === "profile") renderProfile();
}
document.addEventListener("click", (e) => {
  const g = e.target.closest("[data-go]"); if (g) { go(g.dataset.go, g.dataset.opt); return; }
  const r = e.target.closest("[data-room]"); if (r) { openRoom(r.dataset.room); return; }
});
/* натискання — короткий клік і легка вібрація на всіх кнопках */
document.addEventListener("pointerdown", (e) => { if (e.target.closest("button")) { hap("light"); } }, { passive: true });

/* іконки нижнього меню (піксельні, 12×12) */
const NAV_PX = {
  nvLobby: [".....##.....", "....####....", "...######...", "..########..", ".##########.", "############", "..########..", "..##.##.##..", "..##.##.##..", "..##.##.##..", "..########..", "............"],
  nvGames: ["##........##", "###......###", ".###....###.", "..###..###..", "...######...", "....####....", "...######...", "..###..###..", "####....####", "###......###", ".##......##.", "............"],
  nvCards: [".....##.....", "....####....", "...######...", "..########..", ".##########.", "############", "############", ".####..####.", ".....##.....", "....####....", "...######...", "............"],
  nvWallet: ["............", ".#########..", "###########.", "##.........#", "##.....#####", "##.....##.##", "##.....#####", "##.........#", "###########.", ".#########..", "............", "............"],
  nvProfile: ["....####....", "...######...", "...######...", "...######...", "....####....", "............", "..########..", ".##########.", "############", "############", "............", "............"],
};
function pxSvg(rows) { let d = ""; rows.forEach((r, y) => { for (let x = 0; x < r.length; x++) if (r[x] === "#") d += `M${x} ${y}h1v1h-1z`; }); return `<path d="${d}"/>`; }
for (const [id, rows] of Object.entries(NAV_PX)) $(id).innerHTML = pxSvg(rows);
