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

