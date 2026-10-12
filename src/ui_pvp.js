/* ============================================================
   OFFICE DREAMS — Studio Battle (PvP)
   A full-screen arena over the game: lobby → search → 3-2-1 →
   30 s tap race → result. The server owns the clock, the score
   and the rewards; this file only shows them and counts taps.
   ============================================================ */
const PV = {
  open: false, st: 'lobby', info: null, v: null, n: 0, off: 0, busy: false, timer: null, raf: 0,
  emote: null, opEk: 0, meEk: 0, opDisp: 0, bid: '', fails: 0, lastSfx: 0, duel: '', tapsShown: 0, resAt: 0,
};
const PV_EMOTES = ['😎', '🔥', '😂', '😱', '👏', '💀'];
const PV_LEAGUE = ['🥉', '🥈', '🥇', '💠', '🌈'];
const PV_LEAGUES = [0, 150, 400, 800, 1400];
const PV_DUR = 30000;

/* ---------- transport: the real server, or a local training bot in the demo ---------- */
const PVL = { tr: 0, w: 0, l: 0, d: 0, best: 0, b: null, q: 0 };
function pvMult(s) { return Math.round((1 + Math.min(1, Math.log2(1 + Math.max(0, wpTotal(s))) / 6)) * 100) / 100; }
function pvLeague(tr) { let i = 0; while (i + 1 < PV_LEAGUES.length && tr >= PV_LEAGUES[i + 1]) i++; return i; }
function pvBotLine(seed, rate) {
  let x = seed >>> 0 || 7;
  const rnd = () => { x = (Math.imul(x, 1103515245) + 12345) >>> 0; return x / 4294967296; };
  const out = [];
  let n = 0;
  for (let i = 0; i < 60; i++) { const r = i < 2 ? 0 : rate * (0.75 + rnd() * 0.5) * (i > 52 ? 1.15 : 1); n += Math.round(r * 0.5); out.push(n); }
  return out;
}
function pvLocal(path, body) {
  const now = Date.now();
  const L0 = PVL;
  const lineN = (b, t) => { const el = Math.min(t, b.end) - b.start; if (el <= 0) return 0; const i = Math.min(59, Math.floor(el / 500)); const p = i ? b.line[i - 1] : 0; return Math.round(p + (b.line[i] - p) * Math.min(1, (el - i * 500) / 500)); };
  const viewL = () => {
    const b = L0.b;
    if (!b) return L0.q ? { ok: true, st: 'search', now, since: L0.q } : { ok: true, st: 'idle', now };
    const done = now > b.end + 600;
    const opn = lineN(b, now), sm = Math.round(b.n * b.mult * 10), so = Math.round(opn * b.mult * 10);
    if (done && !b.res) {
      const win = sm > so ? 1 : sm < so ? -1 : 0;
      const d = win > 0 ? 20 : win < 0 ? -12 : 0;
      L0.tr = Math.max(0, L0.tr + d);
      if (win > 0) L0.w++; else if (win < 0) L0.l++; else L0.d++;
      L0.best = Math.max(L0.best, sm);
      b.res = { win, dTr: d, tr: L0.tr, league: pvLeague(L0.tr), cash: 0, gems: 0, rewarded: false, left: 0, my: sm, op: so, demo: true };
    }
    return { ok: true, st: done ? 'result' : 'battle', id: b.id, now, start: b.start, end: b.end, me: { n: b.n, mult: b.mult, score: sm, kind: 'live', tr: L0.tr, league: pvLeague(L0.tr) }, op: { name: '', kind: 'bot', tr: L0.tr, league: pvLeague(L0.tr), mult: b.mult, n: opn, score: so, e: b.e }, res: done ? b.res : null };
  };
  if (path === 'info') return { ok: true, demo: true, me: { tr: L0.tr, league: pvLeague(L0.tr), next: PV_LEAGUES[pvLeague(L0.tr) + 1] || null, w: L0.w, l: L0.l, d: L0.d, best: L0.best, left: 0, daily: 5, mult: pvMult(S), reward: 0, rank: null, hasRun: false }, online: { searching: 0, fighting: 0 }, top: [] };
  if (path === 'find') { if (L0.b && !L0.b.res) return viewL(); L0.b = null; L0.q = now; return viewL(); }
  if (path === 'cancel') { L0.q = 0; return { ok: true, st: 'idle' }; }
  if (path === 'tick') {
    if (L0.q && !L0.b && now - L0.q > 1600) { L0.q = 0; const seed = (now % 100000) + 1; L0.b = { id: 'demo' + seed, start: now + 3500, end: now + 3500 + PV_DUR, n: 0, mult: pvMult(S), line: pvBotLine(seed, 5.5 + L0.tr / 300), e: null }; }
    const b = L0.b;
    if (b && !b.res && body.n > b.n && now >= b.start && now <= b.end + 600) b.n = Math.min(body.n, Math.floor((15 * Math.min(PV_DUR, now - b.start)) / 1000) + 8);
    if (b && !b.res && now > b.start && now < b.end && Math.random() < 0.04) b.e = { i: Math.floor(Math.random() * 6), at: now, k: ((b.e && b.e.k) || 0) + 1 };
    return viewL();
  }
  return { ok: false };
}
function pvCall(path, body) {
  if (NET.mode !== 'online') return Promise.resolve(pvLocal(path, body || {}));
  const route = { info: ['GET', '/api/pvp'], find: ['POST', '/api/pvp/find'], tick: ['POST', '/api/pvp/tick'], cancel: ['POST', '/api/pvp/cancel'] }[path];
  return http(route[0], route[1], route[0] === 'GET' ? null : body || {}, { timeout: 8000 }).then((r) => {
    if (r.status === 401) return reauth().then((ok) => (ok ? http(route[0], route[1], route[0] === 'GET' ? null : body || {}, { timeout: 8000 }).then((x) => x.j) : null));
    return r.j;
  }).catch(() => null);
}

/* ---------- open / close ---------- */
function pvEl() {
  let el = $('#pvp');
  if (!el) {
    el = document.createElement('div');
    el.id = 'pvp';
    el.className = 'pvp';
    el.hidden = true;
    document.body.appendChild(el);
    el.addEventListener('click', (e) => {
      const b = e.target.closest('[data-pv]');
      if (!b || b.disabled) return;
      e.stopPropagation();
      const [a, x] = b.dataset.pv.split(':');
      if (PV_ACT[a]) PV_ACT[a](x);
    });
    el.addEventListener('pointerdown', (e) => {
      const t = e.target.closest('.pv-tap');
      if (t) { e.preventDefault(); pvTap(e); }
    }, { passive: false });
  }
  return el;
}
function pvOpen(duel) {
  ensureAudio();
  const el = pvEl();
  PV.open = true;
  UI.pvp = true;
  PV.duel = duel || '';
  el.hidden = false;
  el.classList.remove('out');
  document.body.classList.add('pv-on');
  lsSet('od2_pv_day', String(Math.floor(Date.now() / 86400000)));
  syncBackButton();
  if (PV.st === 'fight' || PV.st === 'search') { pvRender(); return; }
  PV.st = 'lobby';
  pvRender();
  pvLoadInfo().then(() => { if (PV.duel) pvFind(PV.duel); });
}
function pvClose(force) {
  if (!PV.open) return;
  if (PV.st === 'fight' && !force) return; // a running battle is not abandoned by an accidental back swipe
  if (PV.st === 'search') pvCall('cancel');
  pvStop();
  PV.open = false;
  UI.pvp = false;
  PV.st = 'lobby';
  const el = pvEl();
  el.classList.add('out');
  setTimeout(() => { if (!PV.open) { el.hidden = true; el.innerHTML = ''; } }, 220);
  document.body.classList.remove('pv-on');
  syncBackButton();
  render(true);
  if (!UI.modal && UI.queue.length) setTimeout(UI.queue.shift(), 300);
}
function pvStop() {
  if (PV.timer) { clearInterval(PV.timer); PV.timer = null; }
  if (PV.raf) { cancelAnimationFrame(PV.raf); PV.raf = 0; }
}
function pvLoop() {
  if (!PV.timer) PV.timer = setInterval(pvTick, 350);
  if (!PV.raf) PV.raf = requestAnimationFrame(pvFrame);
}
async function pvLoadInfo() {
  const r = await pvCall('info');
  if (r && r.ok) { PV.info = r; if (PV.st === 'lobby' && PV.open) pvRender(); }
  return r;
}

/* ---------- actions ---------- */
const PV_ACT = {
  close: () => pvClose(),
  find: () => pvFind(''),
  cancel: () => { pvCall('cancel'); pvStop(); PV.st = 'lobby'; pvRender(); },
  again: () => { PV.st = 'lobby'; PV.v = null; pvFind(''); },
  lobby: () => { PV.st = 'lobby'; PV.v = null; pvStop(); pvRender(); pvLoadInfo(); },
  share: () => {
    const link = PV.info && PV.info.link;
    if (!link) return;
    const url = 'https://t.me/share/url?url=' + encodeURIComponent(link) + '&text=' + encodeURIComponent(L('pv.share_text'));
    if (TG && TG.openTelegramLink) TG.openTelegramLink(url); else openLink(url);
  },
  emo: (i) => {
    if (PV.st !== 'fight') return;
    const k = Number(i);
    PV.emote = k;
    pvPopEmote('me', PV_EMOTES[k]);
    haptic('light');
  },
};
async function pvFind(duel) {
  PV.st = 'search';
  PV.v = null;
  PV.n = 0;
  PV.since = Date.now();
  pvRender();
  const r = await pvCall('find', duel ? { duel } : {});
  PV.duel = '';
  if (!r || !r.ok) {
    if (r && (r.err === 'self' || r.err === 'norun')) {
      toast(L(r.err === 'self' ? 'pv.err_self' : 'pv.err_norun'), r.err === 'self' ? '' : 'bad');
      if (r.err === 'norun') return pvFind('');
    } else toast(L('pv.err'), 'bad');
    PV.st = 'lobby';
    pvRender();
    return;
  }
  pvApply(r);
  pvLoop();
}
async function pvTick() {
  if (PV.busy || !PV.open) return;
  if (PV.st !== 'search' && PV.st !== 'fight') return;
  PV.busy = true;
  const body = { n: PV.n };
  if (PV.v && PV.v.id) body.b = PV.v.id;
  if (PV.emote !== null) { body.e = PV.emote; PV.emote = null; }
  const r = await pvCall('tick', body);
  PV.busy = false;
  if (!r || !r.ok) { if (++PV.fails > 25) { toast(L('pv.err'), 'bad'); PV_ACT.lobby(); } return; }
  PV.fails = 0;
  pvApply(r);
}
function pvApply(r) {
  if (typeof r.now === 'number') PV.off = r.now - Date.now();
  if (r.st === 'idle') { if (PV.st === 'search' || PV.st === 'fight') { PV.st = 'lobby'; pvStop(); pvRender(); } return; }
  if (r.st === 'search') { if (PV.st !== 'search') { PV.st = 'search'; pvRender(); } return; }
  if (r.st === 'battle') {
    const fresh = !PV.v || PV.v.id !== r.id;
    PV.v = r;
    if (fresh) { PV.n = Math.max(0, r.me.n || 0); PV.opDisp = 0; PV.opEk = r.op.e ? r.op.e.k : 0; PV.st = 'fight'; pvRender(); sfx('up'); haptic('medium'); }
    if (r.op.e && r.op.e.k !== PV.opEk) { PV.opEk = r.op.e.k; pvPopEmote('op', PV_EMOTES[r.op.e.i] || '🔥'); }
    return;
  }
  if (r.st === 'result') {
    PV.v = r;
    pvStop();
    PV.st = 'result';
    pvRender();
    const res = r.res || {};
    if (res.win > 0) { confetti(170); sfx('hit'); haptic('ok'); } else if (res.win < 0) { sfx('bad'); haptic('err'); } else sfx('release');
    // coins land in the game state on the server: fetch it right away
    if (NET.mode === 'online' && res.rewarded) setTimeout(() => netSync({ full: true }), 400);
    pvLoadInfo();
  }
}

/* ---------- tapping ---------- */
function pvPhase() {
  const v = PV.v;
  if (!v) return 'none';
  const now = Date.now() + PV.off;
  return now < v.start ? 'count' : now <= v.end ? 'fight' : 'over';
}
function pvTap(e) {
  if (PV.st !== 'fight' || pvPhase() !== 'fight') return;
  PV.n++;
  const t = performance.now();
  if (t - PV.lastSfx > 70) { PV.lastSfx = t; sfx('tap'); }
  haptic('light');
  const box = $('.pv-fight');
  if (!box) return;
  const r = box.getBoundingClientRect();
  const x = (e && e.clientX ? e.clientX : r.left + r.width / 2) - r.left + (Math.random() * 30 - 15);
  const y = (e && e.clientY ? e.clientY : r.top + r.height * 0.7) - r.top - 16;
  if (box.querySelectorAll('.pv-plus').length > 14) box.querySelector('.pv-plus').remove();
  const p = document.createElement('span');
  p.className = 'pv-plus px';
  p.textContent = '+' + Math.round((PV.v.me.mult || 1) * 10);
  p.style.left = x + 'px';
  p.style.top = y + 'px';
  box.appendChild(p);
  setTimeout(() => p.remove(), 650);
  const btn = $('.pv-tap');
  if (btn) { btn.classList.remove('hit'); void btn.offsetWidth; btn.classList.add('hit'); }
}
function pvPopEmote(who, ch) {
  const host = $(who === 'me' ? '.pv-row.me' : '.pv-row.op');
  if (!host) return;
  const s = document.createElement('span');
  s.className = 'pv-emo';
  s.textContent = ch;
  host.appendChild(s);
  setTimeout(() => s.remove(), 1600);
}

/* ---------- per-frame updates of the arena (no re-render while fighting) ---------- */
function pvFrame() {
  PV.raf = 0;
  if (!PV.open) return;
  if (PV.st === 'search') {
    const t = $('#pvSearchT');
    if (t) { const s = Math.floor((Date.now() - (PV.since || Date.now())) / 1000); t.textContent = Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }
  } else if (PV.st === 'fight' && PV.v) {
    const v = PV.v, now = Date.now() + PV.off, ph = pvPhase();
    const cnt = $('#pvCount'), clock = $('#pvClock'), tap = $('.pv-tap');
    if (ph === 'count') {
      const k = Math.ceil((v.start - now) / 1000);
      if (cnt) { const txt = k > 3 ? L('pv.ready') : String(Math.max(1, k)); if (cnt.dataset.k !== txt) { cnt.dataset.k = txt; cnt.textContent = txt; cnt.classList.remove('pop'); void cnt.offsetWidth; cnt.classList.add('pop'); if (k <= 3) sfx('blip'); } cnt.hidden = false; }
      if (tap) tap.disabled = true;
      if (clock) clock.textContent = '0:30';
    } else {
      if (cnt && !cnt.hidden) {
        if (cnt.dataset.k !== 'go') { cnt.dataset.k = 'go'; cnt.textContent = L('pv.go'); cnt.classList.remove('pop'); void cnt.offsetWidth; cnt.classList.add('pop', 'go'); sfx('combo'); setTimeout(() => { cnt.hidden = true; }, 650); }
      }
      if (tap) tap.disabled = ph !== 'fight';
      const left = Math.max(0, Math.ceil((v.end - now) / 1000));
      if (clock) { clock.textContent = ph === 'over' ? '0:00' : '0:' + String(left).padStart(2, '0'); clock.classList.toggle('hot', left <= 5 && ph === 'fight'); }
      if (ph === 'over') { const w = $('#pvLead'); if (w) w.textContent = L('pv.wait'); }
    }
    const meS = Math.round(PV.n * (v.me.mult || 1) * 10);
    const target = v.op.score || 0;
    PV.opDisp += (target - PV.opDisp) * 0.18;
    if (Math.abs(target - PV.opDisp) < 1) PV.opDisp = target;
    const opS = Math.round(PV.opDisp);
    const top = Math.max(meS, opS, 200);
    const bm = $('#pvBarMe'), bo = $('#pvBarOp'), sm = $('#pvScMe'), so = $('#pvScOp'), lead = $('#pvLead');
    if (bm) bm.style.transform = 'scaleX(' + (meS / top).toFixed(3) + ')';
    if (bo) bo.style.transform = 'scaleX(' + (opS / top).toFixed(3) + ')';
    if (sm) sm.textContent = fmt(meS);
    if (so) so.textContent = fmt(opS);
    if (lead && ph === 'fight') {
      const k = meS > opS ? 'lead' : meS < opS ? 'behind' : 'tie';
      if (lead.dataset.k !== k) { lead.dataset.k = k; lead.textContent = L('pv.' + k); lead.className = 'pv-lead ' + k; }
    }
  }
  if (PV.st === 'search' || PV.st === 'fight') PV.raf = requestAnimationFrame(pvFrame);
}

/* ---------- screens ---------- */
function pvOpName(op) { return op.kind === 'bot' ? L('pv.bot') : (op.name || '?'); }
function pvLeagueChip(lg) { return `<span class="pv-lg l${lg}">${PV_LEAGUE[lg]} ${L('pv.league' + lg)}</span>`; }
function pvRender() {
  const el = pvEl();
  let html = '';
  if (PV.st === 'lobby') html = pvLobby();
  else if (PV.st === 'search') html = pvSearch();
  else if (PV.st === 'fight') html = pvFight();
  else if (PV.st === 'result') html = pvResult();
  el.innerHTML = `<div class="pv-bg" aria-hidden="true"><i></i><i></i></div><div class="pv-box st-${PV.st}">${html}</div>`;
  if (PV.st === 'search' || PV.st === 'fight') { if (!PV.raf) PV.raf = requestAnimationFrame(pvFrame); }
}
function pvLobby() {
  const inf = PV.info;
  const me = inf && inf.me;
  const head = `<div class="pv-head"><h2 class="pv-title px">${I('sword', 18)} ${L('pv.title')}</h2><button class="pv-x" data-pv="close" aria-label="${esc(L('pv.close'))}">${I('close', 14)}</button></div>
    <p class="pv-sub">${L('pv.sub')}</p>`;
  if (!me) return head + '<div class="loading"></div>';
  const lg = me.league;
  const nextTxt = me.next ? L('pv.next', { n: fmt(me.next - me.tr) }) : L('pv.max');
  const prog = me.next ? Math.min(1, (me.tr - PV_LEAGUES[lg]) / (me.next - PV_LEAGUES[lg])) : 1;
  const dots = Array.from({ length: me.daily }, (_, i) => `<i class="${i < me.left ? 'on' : ''}"></i>`).join('');
  return head + `
    ${inf.demo ? `<div class="note">${I('info', 14)} ${L('pv.demo')}</div>` : ''}
    <section class="pv-card">
      <div class="pv-crest l${lg}"><span>${PV_LEAGUE[lg]}</span></div>
      <div class="pv-me">
        ${pvLeagueChip(lg)}
        <div class="pv-tr"><b class="px">${fmt(me.tr)}</b> <small>${L('pv.trophies')}</small></div>
        <div class="pv-prog"><i style="transform:scaleX(${prog.toFixed(3)})"></i></div>
        <small class="mut">${nextTxt}</small>
      </div>
    </section>
    <div class="pv-stats">
      <div><small>${L('pv.wl', { w: me.w, l: me.l })}</small></div>
      <div><small>${L('pv.best', { n: fmt(me.best) })}${me.rank ? ' · ' + L('pv.rank', { n: me.rank }) : ''}</small></div>
    </div>
    <div class="pv-power"><b class="px">×${me.mult.toFixed(2)}</b><div><strong>${L('pv.power')}</strong><small>${L('pv.power_hint')}</small></div></div>
    ${inf.demo ? '' : `<div class="pv-daily"><div class="pv-dots">${dots}</div><small>${me.left > 0 ? L('pv.left', { n: me.left, all: me.daily }) + ' · ' + L('pv.reward', { cash: money(me.reward) }) + (me.firstWin ? '<br>' + L('pv.first') : '') : L('pv.no_left')}</small></div>`}
    <button class="btn big glow pv-find" data-pv="find">${I('sword', 18)} ${L('pv.find')}</button>
    ${inf.demo ? '' : `<button class="btn ghost pv-duelb" data-pv="share" ${me.hasRun ? '' : 'disabled'}>🤝 ${L('pv.duel')}</button>
    <p class="hint">${me.hasRun ? L('pv.duel_hint') : L('pv.duel_need')}</p>`}
    ${inf.online && !inf.demo ? `<p class="pv-online"><i class="live-dot"></i>${L('pv.online', { s: inf.online.searching, f: inf.online.fighting })}</p>` : ''}
    <section class="pv-how"><h3>${L('pv.how')}</h3><ol><li>${L('pv.how1')}</li><li>${L('pv.how2')}</li><li>${L('pv.how3')}</li></ol></section>
    ${inf.demo ? '' : `<section class="pv-topl"><h3>${I('trophy', 14)} ${L('pv.top')}</h3>
      ${inf.top.length ? `<ol class="board">${inf.top.slice(0, 10).map((r) => `<li class="${r.me ? 'me' : ''}"><span class="px rk">${r.rank}</span><b>${PV_LEAGUE[r.league]} ${flag(r.country)} ${esc(r.name)}</b><span class="yel px">${fmt(r.tr)}</span></li>`).join('')}</ol>` : `<p class="sub">${L('pv.empty')}</p>`}
    </section>`}`;
}
function pvSearch() {
  return `<div class="pv-head"><h2 class="pv-title px">${I('sword', 18)} ${L('pv.title')}</h2></div>
    <div class="pv-radar" aria-hidden="true"><i></i><i></i><i></i><span>${I('sword', 34)}</span></div>
    <h3 class="pv-st-t">${L('pv.searching')}</h3>
    <b class="px pv-timer" id="pvSearchT">0:00</b>
    <p class="hint">${L('pv.search_hint')}</p>
    <button class="btn ghost" data-pv="cancel">${L('pv.cancel')}</button>`;
}
function pvSideCard(sd, mine) {
  const name = mine ? (NET.me && NET.me.name) || L('pv.you') : pvOpName(sd);
  return `<div class="pv-who"><b>${mine ? '' : flag(sd.country) + ' '}${esc(name)}</b><small>${mine ? L('pv.you') : L('pv.k_' + sd.kind)} · ${PV_LEAGUE[sd.league || 0]} ${fmt(sd.tr || 0)} · ×${(sd.mult || 1).toFixed(2)}</small></div>`;
}
function pvFight() {
  const v = PV.v;
  return `<div class="pv-fight">
    <div class="pv-ftop"><span class="pv-kind">${L('pv.k_' + v.op.kind)}</span><b class="px pv-clock" id="pvClock">0:30</b></div>
    <div class="pv-row me">${pvSideCard(v.me, true)}<div class="pv-bar"><i id="pvBarMe"></i></div><b class="px pv-sc" id="pvScMe">0</b></div>
    <div class="pv-vs px">${L('pv.vs')}</div>
    <div class="pv-row op">${pvSideCard(v.op, false)}<div class="pv-bar"><i id="pvBarOp"></i></div><b class="px pv-sc" id="pvScOp">0</b></div>
    <div class="pv-lead" id="pvLead">&nbsp;</div>
    <button class="pv-tap" disabled aria-label="${esc(L('pv.tap'))}"><span class="px">${L('pv.tap')}</span><small>+${Math.round(v.me.mult * 10)}</small></button>
    <div class="pv-emotes">${PV_EMOTES.map((e, i) => `<button data-pv="emo:${i}" aria-label="emote">${e}</button>`).join('')}</div>
    <div class="pv-count px" id="pvCount"></div>
  </div>`;
}
function pvResult() {
  const v = PV.v, r = v.res || { win: 0, dTr: 0, tr: 0, my: 0, op: 0 };
  const k = r.win > 0 ? 'win' : r.win < 0 ? 'lose' : 'draw';
  return `<div class="pv-res ${k}">
    <div class="pv-banner px">${L('pv.' + k)}</div>
    <div class="pv-final">
      <div><small>${L('pv.you')}</small><b class="px">${fmt(r.my)}</b></div>
      <span class="px">:</span>
      <div><small>${esc(pvOpName(v.op))}</small><b class="px">${fmt(r.op)}</b></div>
    </div>
    <p class="pv-kind">${L('pv.k_' + v.op.kind)}</p>
    <div class="pv-gain">
      <div class="pv-trg ${r.dTr > 0 ? 'up' : r.dTr < 0 ? 'down' : ''}">🏆 ${L('pv.res_tr', { d: (r.dTr > 0 ? '+' : '') + r.dTr, tr: fmt(r.tr) })}</div>
      ${r.rewarded ? `<div class="pv-rw"><small>${L('pv.res_cash')}</small><b class="grn">+${money(r.cash)}</b>${r.gems ? `<b class="mag">+${r.gems} 💎</b>` : ''}</div><small class="mut">${L('pv.res_left', { n: r.left })}</small>` : r.demo ? '' : `<small class="mut">${L('pv.res_none')}</small>`}
    </div>
    <button class="btn big glow" data-pv="again">${I('sword', 16)} ${L('pv.again')}</button>
    ${NET.mode === 'online' ? `<button class="btn ghost" data-pv="share">🤝 ${L('pv.duel')}</button>` : ''}
    <button class="linkbtn" data-pv="lobby">${L('pv.close')}</button>
  </div>`;
}

/* ---------- a friend's challenge link opened the game ---------- */
function pvStartParam() {
  try {
    const hp = new URLSearchParams((location.hash || '').replace(/^#/, ''));
    return String((TG && TG.initDataUnsafe && TG.initDataUnsafe.start_param) || qs('tgWebAppStartParam') || hp.get('tgWebAppStartParam') || qs('startapp') || qs('start') || '');
  } catch (e) { return ''; }
}
function pvCheckDuel() {
  const sp = pvStartParam();
  const m = /^duel_([A-Za-z0-9]{4,16})$/.exec(sp);
  if (!m || NET.mode !== 'online') return;
  const code = m[1].toLowerCase();
  if (NET.eco && NET.eco.ref && NET.eco.ref.code === code) return;
  if (lsGet('od2_duel_seen') === code) return;
  lsSet('od2_duel_seen', code);
  modalOrQueue(() => openModal(`<div class="pv-invite">
    <div class="pv-inv-ic">${I('sword', 40)}</div>
    <h2 class="h2 big">${L('pv.invite_t')}</h2>
    <p class="ev-text">${L('pv.invite')}</p>
    <button class="btn big glow" data-act="pvduel:${code}">${L('pv.accept')}</button>
    <button class="linkbtn" data-act="close">${L('pv.later')}</button>
  </div>`, { id: 'duel', mcls: 'relm' }));
}
