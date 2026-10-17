/* ============================================================
   OFFICE DREAMS — screens (HTML builders)
   ============================================================ */
const TABS = [
  { id: 'office', ico: 'office' },
  { id: 'studio', ico: 'games' },
  { id: 'quests', ico: 'quest' },
  { id: 'empire', ico: 'empire' },
  { id: 'drop', ico: 'drop' },
];
const SUBS = {
  studio: ['team', 'games'],
  drop: ['home', 'token', 'friends', 'tasks', 'top', 'shop'],
};
const SUB_ICO = { team: 'team', games: 'games', home: 'pts', token: 'sol', friends: 'users', tasks: 'list', top: 'trophy', shop: 'cart' };

/* ---------- derived helpers ---------- */
const projectPct = () => (S.project ? Math.min(1, S.project.pts / S.project.need) * 100 : 0);
function projectETA() {
  if (!S.project) return '';
  const rem = S.project.need - S.project.pts;
  return '≈ ' + fmtDur(rem / Math.max(0.1, wpTotal(S)) / (NET.speed || 1));
}
const claimableGoals = () => GOALS.filter((g) => S.goals[g.id] === 1);
const dailyClaimable = () => DAILY.filter((d) => !S.dq.done[d.id] && dailyProgress(S, d) >= d.need);
function checkinReady() {
  const e = NET.eco;
  return !!(e && e.checkin && !e.checkin.done && S.stats.taps >= 20);
}
function nextStep() {
  if (S.pending) return { k: 'next.event', act: 'event' };
  if (claimableGoals().length) return { k: 'next.claim', act: 'tab:quests' };
  if (S.stats.started === 0) return { k: 'next.start', act: 'go:studio:games' };
  if (S.stats.taps < 10) return { k: 'next.tap', act: 'work' };
  if (!S.project) return { k: 'next.start2', act: 'go:studio:games' };
  if (hiredCount(S) === 0 && S.cash >= ROLE.prog.hire) return { k: 'next.hire', act: 'go:studio:team' };
  if (checkinReady()) return { k: 'next.checkin', act: 'checkin' };
  if (dailyClaimable().length) return { k: 'next.daily', act: 'tab:quests' };
  const nf = nextFloor(S);
  const f = nf && FLOORS.find((x) => x.id === nf);
  if (f && S.cash >= f.price) return { k: 'next.floor', act: 'tab:empire' };
  if (S.energy < 2) return { k: 'next.rest', act: '' };
  if (S.stats.released >= 1 && NET.eco && NET.eco.ref && NET.eco.ref.total === 0 && NET.mode === 'online') return { k: 'next.invite', act: 'go:drop:friends' };
  return { k: 'next.work', act: 'work' };
}
function buffChips() {
  const out = [];
  if (S.buffs.turbo > 0) out.push(`<span class="chip cy">${I('bolt', 12)} ${L('buff.turbo')} <b data-t="buff:turbo"></b></span>`);
  if (S.buffs.hype > 0) out.push(`<span class="chip mag">${I('fire', 12)} ${L('buff.hype')} <b data-t="buff:hype"></b></span>`);
  if (S.buffs.invest > 0) out.push(`<span class="chip yel">${I('chart', 12)} ${L('buff.invest')}</span>`);
  return out.join('');
}

/* ---------- live bindings (updated 4×/s without re-render) ---------- */
// reputation (unbounded) is shown as a level 1–10 with progress inside the level; mood = average of the hired team
const REP_STEP = (lv) => 5 * lv * lv;
function repLevel() { const r = S.rep || 0; let lv = 1; while (lv < 10 && r >= REP_STEP(lv)) lv++; const lo = lv > 1 ? REP_STEP(lv - 1) : 0, hi = REP_STEP(lv); return { lv, pct: lv >= 10 && r >= hi ? 100 : ((r - lo) / (hi - lo)) * 100 }; }
function teamMood() { const h = ROLES.filter((r) => S.staff[r].lvl > 0); return h.length ? h.reduce((t, r) => t + S.staff[r].mood, 0) / h.length : 100; }
const LIVE = {
  replv: () => 'LV ' + repLevel().lv,
  moodv: () => Math.round(teamMood()) + '%',
  cash: () => fmtHud(S.cash),
  gems: () => String(S.gems),
  day: () => L('ui.day', { n: S.day }),
  clock: () => clockText(S),
  energy: () => Math.floor(S.energy) + '/' + maxEnergy(S),
  ppct: () => Math.floor(projectPct()) + '%',
  peta: () => projectETA(),
  dayleft: () => fmtDur((DAY_SEC - S.clock) / (NET.speed || 1)),
  buff: (id) => fmtDur(S.buffs[id] / (NET.speed || 1)),
  gearn: (id) => { const g = S.games.find((x) => String(x.id) === id); return g ? money(g.earned) : ''; },
  dprog: (id) => { const d = DAILY.find((x) => x.id === id); return d ? dailyProgress(S, d) + '/' + d.need : ''; },
  sales: () => money(dailySalesRate(S)),
  gwleft: () => { const g = NET.eco && NET.eco.giveaway; return g ? gwLeft(g.end) : ''; },
  cityleft: (k) => { const c = S.city || {}; const u = k === 'trn' ? c.trn && c.trn.end : k === 'ctr' ? c.ctr && c.ctr.until : k === 'next' ? c.ctrNext : c[k]; return mmss((u || 0) - S.t); },
};
const WLIVE = {
  rep: () => repLevel().pct,
  mood: () => teamMood(),
  daybar: () => (S.clock / DAY_SEC) * 100,
  energy: () => (S.energy / maxEnergy(S)) * 100,
  proj: () => projectPct(),
  gprog: (id) => { const g = S.games.find((x) => String(x.id) === id); return g ? Math.min(100, (g.earned / Math.max(1, g.total)) * 100) : 0; },
  dprog: (id) => { const d = DAILY.find((x) => x.id === id); return d ? (dailyProgress(S, d) / d.need) * 100 : 0; },
  citytrn: () => { const c = S.city && S.city.trn; if (!c) return 0; const tot = CITY.trnSec(S.staff[c.role].lvl); return (1 - (c.end - S.t) / tot) * 100; },
};
function live() {
  $$('[data-t]').forEach((el) => {
    const parts = el.dataset.t.split(':');
    const f = LIVE[parts[0]];
    if (!f) return;
    const v = f(parts[1]);
    if (el._v !== v) { el.textContent = v; el._v = v; }
  });
  $$('[data-w]').forEach((el) => {
    const parts = el.dataset.w.split(':');
    const f = WLIVE[parts[0]];
    if (!f) return;
    const v = Math.max(0, Math.min(100, f(parts[1]))).toFixed(1) + '%';
    if (el._v !== v) { el.style.width = v; el._v = v; }
  });
  const wb = $('#workbtn');
  if (wb) wb.classList.toggle('tired', S.energy < 2);
  updateSceneFx();
}

/* ---------- shell pieces ---------- */
// the player paid the one-time airdrop entry deposit: a verified real player
function isVerified() {
  const a = NET.eco && NET.eco.airdrop;
  return !!(a && a.entry && a.entry.done);
}
function hudHTML() {
  const name = (NET.me && NET.me.name) || (TG && TG.initDataUnsafe && TG.initDataUnsafe.user && TG.initDataUnsafe.user.first_name) || L('ui.founder');
  return `
  <button class="me" data-act="settings" aria-label="${esc(L('set.title'))}">
    ${imgTag(portrait('you', 90), 'av')}
    <span class="me-t"><b>${esc(name)}${isVerified() ? `<i class="vbadge" title="${esc(L('ver.badge'))}">${I('check', 10)}</i>` : ''}</b><small>${isVerified() ? `<span class="vtxt">${esc(L('ver.badge'))}</span>` : esc(L('rank.' + rankOf(S)))}</small></span>
  </button>
  <div class="res">
    <span class="pill cash" title="${esc(L('res.cash'))}">${I('coin', 16)}<b data-t="cash"></b></span>
    <span class="pill gem" title="${esc(L('res.gems'))}">${I('gem', 16)}<b data-t="gems"></b></span>
    <button class="pill pts" data-act="go:drop:home" title="${esc(L('res.pts'))}">${I('pts', 16)}<b id="hudpts">${fmtPts(NET.eco ? NET.eco.pts : 0)}</b></button>
  </div>
  <div class="dayrow">
    <span class="px dchip" data-t="day"></span><span class="px dchip mut" data-t="clock"></span>
    <div class="bar thin"><i data-w="daybar"></i></div>
    <span class="net ${NET.mode === 'demo' ? 'demo' : NET.online ? 'on' : 'off'}" title="${esc(NET.mode === 'demo' ? L('net.demo') : NET.online ? L('net.on') : L('net.off'))}">${NET.mode === 'demo' ? 'DEMO' : NET.online ? 'LIVE' : 'OFF'}</span>
  </div>`;
}
function tabsHTML() {
  const dots = {
    office: false,
    studio: !S.project,
    quests: !!S.pending || claimableGoals().length > 0 || dailyClaimable().length > 0,
    empire: (() => { const nf = nextFloor(S); const f = nf && FLOORS.find((x) => x.id === nf); return !!(f && S.cash >= f.price); })(),
    drop: checkinReady() || !!(UI.hub.tasks && UI.hub.tasks.some((t) => t.claimable)),
  };
  return TABS.map((t) => `<button role="tab" class="tab ${UI.tab === t.id ? 'on' : ''}" data-act="tab:${t.id}" aria-selected="${UI.tab === t.id}">
    ${I(t.ico, 22)}<span>${L('tab.' + t.id)}</span>${dots[t.id] && UI.tab !== t.id ? '<i class="dot"></i>' : ''}
  </button>`).join('');
}
function segHTML(group) {
  return `<div class="seg" role="tablist">${SUBS[group].map((id) => `<button class="${UI.sub[group] === id ? 'on' : ''}" data-act="sub:${group}:${id}">${I(SUB_ICO[id], 14)}<span>${L('sub.' + id)}</span></button>`).join('')}</div>`;
}

/* ---------- shared components ---------- */
function goalReward(rw, pts) {
  const out = [];
  if (rw.cash) out.push('+' + money(rw.cash));
  if (rw.gems) out.push('+' + rw.gems + ' ' + L('res.gems_s'));
  if (rw.rep) out.push('+' + rw.rep + ' ' + L('res.rep_s'));
  return out.join(' · ');
}
function goalRow(g) {
  const st = S.goals[g.id] || 0;
  const icon = st === 2 ? I('check', 16, 'grn') : st === 1 ? I('star', 16, 'yel') : I('lock', 16, 'dim');
  return `<div class="goal st${st}">
    ${icon}
    <div class="goal-t"><span>${esc(L('goal.' + g.id))}</span><small>${esc(goalReward(g.rw))} <em class="ptag">+${I('pts', 10)}</em></small></div>
    ${st === 1 ? `<button class="btn sm glow" data-act="claim:${g.id}">${L('ui.claim')}</button>` : st === 2 ? `<small class="mut">${L('ui.received')}</small>` : ''}
  </div>`;
}
function eventBanner() {
  if (!S.pending) return '';
  const ev = eventView(S);
  return `<button class="panel banner" data-act="event">
    ${imgTag(portrait(evFace(ev.id), 80), 'av')}
    <span class="banner-t"><b>${esc(L(ev.k + '.who'))}</b><small>${esc(L(ev.k + '.title'))}</small></span>
    <span class="btn pink sm">${L('ui.open')}</span>
  </button>`;
}
function tagChips(tags) {
  return (tags || []).map((t) => `<span class="tag t-${t}">${L('tag.' + t)}</span>`).join('');
}
function eventCard(inModal) {
  const ev = eventView(S);
  if (!ev) return '';
  return `<section class="${inModal ? '' : 'panel '}evcard">
    <div class="ev-head">${imgTag(portrait(evFace(ev.id), 80), 'av lg')}
      <div><small class="px who">${esc(L(ev.k + '.who'))}</small><h3 class="h3">${esc(L(ev.k + '.title'))}</h3></div></div>
    <p class="ev-text">${esc(L(ev.k + '.text', ev.p))}</p>
    <div class="choices">${ev.choices.map((c) => `<button class="choice ${c.style}" data-act="ev:${c.i}" ${c.disabled ? 'disabled' : ''}>
      <span class="c-l">${esc(L(ev.k + '.c' + c.i, c.lp))}</span>
      <span class="c-tags">${tagChips(c.tags)}${c.disabled ? `<span class="tag t-no">${c.needEnergy && S.energy < c.needEnergy ? L('err.energy') : L('err.cash')}</span>` : ''}</span>
    </button>`).join('')}</div>
  </section>`;
}
function outcomeCard(o) {
  if (!o) return '';
  return `<section class="panel outcome">
    <div class="ev-head">${imgTag(portrait(evFace(o.id), 80), 'av')}
      <div><small class="px who">${esc(L('ev.' + o.id + '.who'))}</small><h3 class="h3">${esc(L('ev.' + o.id + '.title'))}</h3></div></div>
    <p class="mut sm">${L('ui.you_chose')}: <b>${esc(L('ev.' + o.id + '.c' + o.i, o.lp))}</b></p>
    <p class="ev-text">${esc(L(o.rk, o.p))}</p>
  </section>`;
}
function dailyCard() {
  const allDone = DAILY.every((d) => S.dq.done[d.id]);
  return `<section class="panel">
    <div class="sec-h"><div><h2 class="h2">${I('calendar', 16)} ${L('daily.title')}</h2><p class="sub">${L('daily.sub', { cash: money(dailyCash(S)) })}</p></div></div>
    <div class="dlist">${DAILY.map((d) => {
      const done = S.dq.done[d.id], ready = !done && dailyProgress(S, d) >= d.need;
      return `<div class="drow ${done ? 'done' : ready ? 'ready' : ''}">
        <div class="d-t"><span>${L('daily.' + d.id, { n: d.need })}</span><div class="bar thin grn"><i data-w="dprog:${d.id}"></i></div></div>
        ${done ? I('check', 16, 'grn') : ready ? `<button class="btn sm glow" data-act="dly:${d.id}">${L('ui.claim')}</button>` : `<small class="px mut" data-t="dprog:${d.id}"></small>`}
      </div>`;
    }).join('')}</div>
    <p class="hint">${allDone ? L('daily.all_done') : L('daily.all_bonus')}</p>
  </section>`;
}

/* ---------- rewarded ads ---------- */
const adCashEst = () => Math.round(Math.min(20000, Math.max(100, S.income3 * 0.3)) / 10) * 10;
function adButton() {
  const a = NET.cfg && NET.cfg.ads, e = NET.eco && NET.eco.ads;
  if (NET.mode !== 'online' || !a || !e) return '';
  if (e.left <= 0) return `<div class="adbtn off">${I('info', 14)} ${L('ads.none')}</div>`;
  return `<button class="adbtn ${S.energy < 2 ? 'hot' : ''}" data-act="ad">
    <span class="ad-ico">▶</span>
    <span class="ad-t"><b>${L('ads.btn')}</b><small>${L('ads.gives', { cash: money(adCashEst()), pts: e.pts })}</small></span>
    <span class="ad-n px">${e.left}/${e.max}</span>
  </button>`;
}

/* ---------- OFFICE ---------- */
function tabOffice() {
  const tp = tapPower(S);
  const ns = nextStep();
  const p = S.project;
  const qDot = !!S.pending || claimableGoals().length > 0 || dailyClaimable().length > 0;
  const side = [
    ['list', 'tab:quests', 'side.tasks', 'yel', qDot],
    ['office', 'go:studio:games', 'side.pc', 'cy', !S.project],
    ['empire', 'city:open', 'side.city', 'mag', Object.values(cityReady()).some(Boolean)],
    ['users', 'go:studio:team', 'side.team', 'cy', ROLES.some((r) => S.staff[r].lvl === 0 && S.cash >= ROLE[r].hire)],
    ['up', 'pump', 'side.up', 'grn', S.cash >= youUpCost(S.you.lvl) && S.you.lvl < MAX_LVL],
    ['sword', 'pvp', 'side.pvp', 'mag', lsGet('od2_pv_day') !== String(Math.floor(Date.now() / 86400000))],
  ];
  return `
  <section class="panel officep">
    <div class="op-head">
      ${(() => { const sn = studioName(); return `<button class="op-studio ${sn ? '' : 'empty'}" data-act="studio">${I('office', 12)}<span>${sn ? esc(sn) : L('st.unnamed')}</span><i>✎</i></button>`; })()}
      <div class="op-meters">
        <div class="opm rep" data-act="meterhelp:rep" title="${esc(L('office.rep'))}"><span>⭐ ${L('office.rep')}</span><b class="px" data-t="replv"></b><i class="opm-bar"><i data-w="rep"></i></i></div>
        <div class="opm mood" data-act="meterhelp:mood" title="${esc(L('office.mood'))}"><span>😊 ${L('office.mood')}</span><b class="px" data-t="moodv"></b><i class="opm-bar"><i data-w="mood"></i></i></div>
      </div>
      <button class="helpbtn" data-act="help" aria-label="${esc(L('help.title'))}">?</button>
    </div>
    <div class="op-body">
      <div class="scene">
        <div class="scene-wrap">
          <canvas id="scene" width="512" height="352" aria-label="${esc(L('office.scene'))}"></canvas>
          <canvas id="bloom" width="256" height="176" aria-hidden="true"></canvas>
          <div class="scan" aria-hidden="true"></div>
        </div>
        <div class="scene-fx" data-keep></div>
        <div class="scene-top">
          <span class="chip trend">${I('fire', 12)} <b>${L('genre.' + S.trend.hot)}</b></span>
          ${buffChips()}
        </div>
        <button class="bubble" data-act="${ns.act}"><b class="b-h">${LR('office.bubble_h', null, 40000)}</b><span>${esc(LR(ns.k, null, 40000))}</span></button>
        <span class="daychip px"><span data-t="day"></span>, <span data-t="clock"></span></span>
      </div>
      <nav class="side" aria-label="${esc(L('office.title'))}">${side.map((x) => `<button class="sbtn s-${x[3]}" data-act="${x[1]}"><span class="s-ico">${I(x[0], 16)}</span><span class="s-l">${L(x[2])}</span>${x[4] ? '<i class="dot"></i>' : ''}</button>`).join('')}</nav>
    </div>
  </section>
  <section class="panel workp">
    ${p ? `<div class="pmini" data-act="go:studio:games">
      ${imgTag(coverURL(p.genre, p.name), 'cover sm')}
      <div class="pm-t"><b>${esc(p.name)}</b><small>${L('genre.' + p.genre)} · ${L('tier.' + p.tier)}</small>
        <div class="bar proj"><i data-w="proj"></i><span class="px" data-t="ppct"></span></div></div>
      <small class="mut eta" data-t="peta"></small>
    </div>` : `<button class="pmini empty" data-act="go:studio:games">${I('plus', 16)}<span>${L('office.no_project')}</span><b class="chev">›</b></button>`}
    <button class="workbtn" id="workbtn" aria-label="${esc(L('office.work'))}">
      <span class="wb-ring"></span>
      <span class="wb-in">${I('bolt', 26)}<span class="wb-l">${L('office.work')}</span>
      <small>+$${fmtSmall(tp.cash)} · +${tp.pts.toFixed(1)} ${L('ui.wp')}</small></span>
    </button>
    <div class="energy">${I('bolt', 14, 'yel')}<div class="bar yel"><i data-w="energy"></i></div><span class="px" data-t="energy"></span></div>
    ${adButton()}
  </section>
  ${giveawayCard(NET.eco, true)}
  ${cityBanner()}
  ${eventBanner()}
  ${dailyCard()}
  <section class="panel fin">
    <div class="stats3">
      <div><small>${L('fin.sales')}</small><b data-t="sales"></b></div>
      <div><small>${L('fin.salary')}</small><b>${money(salaryTotal(S))}</b></div>
      <div><small>${L('fin.dayleft')}</small><b data-t="dayleft"></b></div>
    </div>
  </section>`;
}

/* ---------- STUDIO: team ---------- */
const moodClass = (m) => (m >= 60 ? 'ok' : m >= 35 ? 'mid' : 'low');
function pips(l) {
  let h = '';
  for (let i = 1; i <= MAX_LVL; i++) h += `<span class="pip ${i <= l ? 'on' : ''}"></span>`;
  return `<div class="pips" aria-label="${esc(L('ui.level', { n: l }))}">${h}</div>`;
}
function empRow(r) {
  if (r === 'you') {
    return `<button class="emp ${UI.sel === 'you' ? 'on' : ''}" data-act="sel:you">
      ${imgTag(portrait('you', 90), 'av')}
      <span class="emp-t"><b>${L('role.you.name')}</b><small>${L('ui.level', { n: S.you.lvl })}</small></span>
      <span class="emp-m ok">${I('bolt', 14)}<small>${maxEnergy(S)}</small></span>
    </button>`;
  }
  const st = S.staff[r], hired = st.lvl > 0, locked = !hired && roleLocked(S, r);
  const canHire = !hired && !locked && S.cash >= ROLE[r].hire;
  return `<button class="emp ${hired ? '' : 'vac'} ${locked ? 'lock' : ''} ${UI.sel === r ? 'on' : ''}" data-act="sel:${r}">
    ${imgTag(portrait(r, hired ? st.mood : 80), 'av')}
    <span class="emp-t"><b>${L('role.' + r + '.name')}</b><small>${hired ? L('ui.level', { n: st.lvl }) + ' · ' + money(salaryOf(S, r)) + L('ui.per_day') : locked ? '🔒 ' + L('team.locked_s', { floor: L('floor.' + ROLE[r].need + '.name') }) : L('team.vacancy') + ' · ' + money(ROLE[r].hire)}</small></span>
    ${hired ? `<span class="emp-m ${moodClass(st.mood)}">${I('heart', 14)}<small>${Math.round(st.mood)}%</small></span>` : canHire ? '<i class="dot"></i>' : ''}
  </button>`;
}
function detailPanel() {
  const r = UI.sel;
  if (r === 'you') {
    const l = S.you.lvl, max = l >= MAX_LVL, cost = youUpCost(l), tp = tapPower(S);
    return `<section class="panel detail">
      <div class="d-head"><h3 class="h3">${L('role.you.name')}</h3><span class="lvl px">LV ${l}</span></div>
      <div class="d-body">${imgTag(portrait('you', 90), 'd-art')}
        <ul class="stats">
          <li><span>${L('team.tap_pts')}</span><b class="grn">+${tp.pts.toFixed(1)}</b></li>
          <li><span>${L('team.tap_cash')}</span><b>$${fmtSmall(tp.cash)}</b></li>
          <li><span>${L('team.max_energy')}</span><b>${maxEnergy(S)}</b></li>
          <li><span>${L('team.passive')}</span><b>+${(0.35 * (1 + 0.08 * (l - 1))).toFixed(2)}/${L('u.s')}</b></li>
        </ul></div>
      ${pips(l)}
      ${max ? `<p class="hint">${L('team.max')}</p>` : `<button class="btn" data-act="upy" ${S.cash >= cost ? '' : 'disabled'}>${L('ui.upgrade')} <b class="px">${money(cost)}</b></button>
      <p class="hint">${L('team.you_next')}</p>`}
    </section>`;
  }
  const st = S.staff[r], hired = st.lvl > 0;
  if (!hired) {
    return `<section class="panel detail">
      <div class="d-head"><h3 class="h3">${L('role.' + r + '.name')}</h3><span class="lvl">${L('team.vacancy')}</span></div>
      <div class="d-body">${imgTag(portrait(r, 80), 'd-art')}
        <ul class="stats">
          <li><span>${L('team.role')}</span><b>${L('role.' + r + '.perk')}</b></li>
          <li><span>${L('team.salary')}</span><b>${money(salaryOf(S, r, 1))}${L('ui.per_day')}</b></li>
        </ul></div>
      ${roleLocked(S, r) ? `<button class="btn" disabled>🔒 ${L('team.locked', { floor: L('floor.' + ROLE[r].need + '.name') })}</button>` : `<button class="btn" data-act="hire:${r}" ${S.cash >= ROLE[r].hire ? '' : 'disabled'}>${L('team.hire')} <b class="px">${money(ROLE[r].hire)}</b></button>`}
      <p class="hint">${L(r === 'mkt' ? 'team.hint_mkt' : ROLE[r].need ? 'team.hint_' + r : 'team.hint_dev')}</p>
    </section>`;
  }
  const l = st.lvl, max = l >= MAX_LVL, cost = upCost(r, l);
  // every stat shows what it was before the last upgrade (muted), then the value now
  const S0 = l > 1 ? Object.assign({}, S, { staff: Object.assign({}, S.staff, { [r]: Object.assign({}, st, { lvl: l - 1 }) }) }) : null;
  const vals = (SS, lv) => {
    const k = lv * moodF(st.mood), wp = `+${wpRole(SS, r).toFixed(1)}/${L('u.s')}`;
    const out = r === 'mkt' ? [['team.sales_bonus', `+${Math.round(0.07 * k * 100)}%`]]
      : r === 'qa' ? [['team.score_bonus', `+${(0.12 * k).toFixed(1)}`]]
      : r === 'prod' ? [['team.team_speed', `+${Math.round(4 * k)}%`]]
      : r === 'cm' ? [['team.tail', `−${Math.round((1 - saleDecay(SS)) * 100)}%`]]
      : r === 'snd' ? [['team.work_pts', wp], ['team.sales_bonus', `+${Math.round(3 * k)}%`]]
      : [['team.work_pts', wp]];
    return [['team.speed', `+${Math.round((speedOf(lv) - 1) * 100)}%`, 'grn']].concat(out.map((x) => [x[0], x[1], 'grn']), [['team.salary', money(salaryOf(SS, r)) + L('ui.per_day'), '']]);
  };
  const now = vals(S, l), was = S0 ? vals(S0, l - 1) : null;
  const row = (x, i) => `<li><span>${L(x[0])}</span><b class="${x[2]}">${was && was[i][1] !== x[1] ? `<s class="was">${was[i][1]}</s> ` : ''}${x[1]}</b></li>`;
  return `<section class="panel detail">
    <div class="d-head"><h3 class="h3">${L('role.' + r + '.name')}</h3><span class="lvl px">LV ${l}</span></div>
    <div class="d-body">${imgTag(portrait(r, st.mood), 'd-art')}
      <ul class="stats">
        ${row(now[0], 0)}
        <li><span>${L('team.mood')}</span><b class="${moodClass(st.mood)}">${Math.round(st.mood)}%</b></li>
        ${now.slice(1).map((x, i) => row(x, i + 1)).join('')}
      </ul></div>
    ${pips(l)}
    ${max ? `<p class="hint">${L('team.max')}</p>` : `<button class="btn" data-act="ups:${r}" ${S.cash >= cost ? '' : 'disabled'}>${L('ui.upgrade')} <b class="px">${money(cost)}</b></button>
    <p class="hint">${L('team.next', { sal: money(salaryOf(S, r, l + 1)) })}</p>`}
  </section>`;
}
function tabTeam() {
  return `<div class="team">
    <div class="emp-list">${['you'].concat(ROLES).map(empRow).join('')}</div>
    ${detailPanel()}
  </div>
  <p class="hint pad">${L('team.hint')}</p>`;
}

/* ---------- STUDIO: games ---------- */
function genreBtn(g) {
  const sel = S.sel.genre === g;
  const hot = S.trend.hot === g, cold = S.trend.cold === g;
  return `<button class="gbtn ${sel ? 'on' : ''}" data-act="genre:${g}">
    ${I(g, 24)}<span>${L('genre.' + g)}</span>
    <em class="trend ${hot ? 'hot' : cold ? 'cold' : ''}">${hot ? '▲ ×1.5' : cold ? '▼ ×0.75' : '×1'}</em>
  </button>`;
}
function tierBtn(t) {
  const ok = tierUnlocked(S, t);
  return `<button class="tbtn ${S.sel.tier === t ? 'on' : ''} ${ok ? '' : 'locked'}" data-act="tier:${t}">
    <span>${L('tier.' + t)}</span>${ok ? `<small>×${TIERS[t].rev} ${L('games.rev')}</small>` : `<small>${I('lock', 10)} ${L('floor.' + TIERS[t].need + '.name')}</small>`}
  </button>`;
}
function currentProjectCard() {
  const p = S.project;
  if (!p) return '';
  return `<section class="panel proj">
    <div class="sec-h"><h2 class="h2">${L('games.current')}</h2></div>
    <div class="proj-row">
      ${imgTag(coverURL(p.genre, p.name), 'cover lg')}
      <div class="proj-t">
        <b>${esc(p.name)}</b>
        <small>${L('genre.' + p.genre)} · ${L('tier.' + p.tier)} · ${L('games.score_est')} ${expectedScore(S, p.genre).toFixed(1)}</small>
        <div class="bar proj"><i data-w="proj"></i><span class="px" data-t="ppct"></span></div>
        <small class="mut" data-t="peta"></small>
      </div>
    </div>
    <div class="row2">
      <button class="btn green" data-act="tab:office">${I('bolt', 16)} ${L('games.speed_up')}</button>
      <button class="toggle" data-act="auto" aria-pressed="${S.auto}"><span class="sw"></span><span>${L('games.auto')}</span></button>
    </div>
  </section>`;
}
function newProjectCard() {
  const sel = S.sel, ok = tierUnlocked(S, sel.tier);
  const need = projectNeed(sel.genre, sel.tier);
  const sec = need / Math.max(0.1, wpTotal(S)) / (NET.speed || 1);
  const score = expectedScore(S, sel.genre);
  return `<section class="panel newp">
    <div class="sec-h"><div><h2 class="h2">${L('games.new')}</h2><p class="sub">${L('games.new_sub')}</p></div></div>
    <div class="genres">${GENRE_ORDER.map(genreBtn).join('')}</div>
    <div class="tiers">${TIER_ORDER.map(tierBtn).join('')}</div>
    <div class="namerow">${imgTag(coverURL(sel.genre, sel.name), 'cover sm')}<div><small>${L('games.name')}</small><b>${esc(sel.name)}</b></div><button class="btn ghost sm" data-act="rename">${I('refresh', 12)} ${L('games.other')}</button></div>
    <div class="stats3">
      <div><small>${L('games.need')}</small><b>${fmt(need)}</b></div>
      <div><small>${L('games.time')}</small><b>${fmtDur(sec)}</b></div>
      <div><small>${L('games.score_est')}</small><b>${score.toFixed(1)}</b></div>
    </div>
    <button class="btn big" data-act="start" ${ok && !S.project ? '' : 'disabled'}>${S.project ? L('games.busy') : ok ? L('games.start') : L('games.locked')}</button>
    <p class="hint">${L('games.hint')}</p>
  </section>`;
}
const scoreClass = (v) => (v >= 8 ? 'hi' : v >= 6 ? 'mid' : 'lo');
function gameRow(g) {
  return `<div class="game">
    ${imgTag(coverURL(g.genre, g.name), 'cover')}
    <div class="g-t"><b>${esc(g.name)}</b><small>${L('genre.' + g.genre)} · ${L('tier.' + g.tier)} · ${L('ui.day', { n: g.day })}</small>
      <div class="bar thin"><i data-w="gprog:${g.id}"></i></div>
      <small class="${g.active ? 'grn' : 'mut'}">${g.active ? L('games.on_sale', { rate: money(g.rate) }) : L('games.off_sale')} · <span data-t="gearn:${g.id}"></span></small></div>
    <span class="score px ${scoreClass(g.score)}">${g.score.toFixed(1)}</span>
  </div>`;
}
function tabGames() {
  return `${currentProjectCard()}${S.project ? '' : newProjectCard()}
  <section class="panel">
    <div class="sec-h"><h2 class="h2">${L('games.released')}</h2><small class="mut">${S.stats.released}</small></div>
    ${S.games.length ? `<div class="glist">${S.games.slice(0, 12).map(gameRow).join('')}</div>` : `<p class="sub">${L('games.none')}</p>`}
  </section>
  ${S.project ? newProjectCard() : ''}`;
}
function tabStudio() {
  return segHTML('studio') + (UI.sub.studio === 'team' ? tabTeam() : tabGames());
}

/* ---------- QUESTS ---------- */
function logRow(e) {
  return `<li class="log ${e.kind}"><span class="px">${L('ui.day_s', { n: e.day })}</span><span>${esc(L(e.k, e.p))}</span></li>`;
}
function tabQuests() {
  const goalsSorted = GOALS.slice().sort((a, b) => {
    const r = (g) => (S.goals[g.id] === 1 ? 0 : S.goals[g.id] === 2 ? 2 : 1);
    return r(a) - r(b);
  });
  const doneN = GOALS.filter((g) => S.goals[g.id] === 2).length;
  return `${S.pending ? eventCard(false) : `<section class="panel calm"><p class="sub">${I('quest', 16)} ${L('quests.none', { n: Math.max(1, S.nextEventDay - S.day) })}</p></section>`}
  ${S.lastOutcome && !S.pending ? outcomeCard(S.lastOutcome) : ''}
  ${dailyCard()}
  <section class="panel">
    <div class="sec-h"><div><h2 class="h2">${I('star', 16)} ${L('goals.title')}</h2><p class="sub">${L('goals.sub')}</p></div><small class="px mut">${doneN}/${GOALS.length}</small></div>
    <div class="goals">${goalsSorted.map(goalRow).join('')}</div>
  </section>
  <section class="panel">
    <h2 class="h2">${L('log.title')}</h2>
    <ul class="logs">${S.log.slice(0, 16).map(logRow).join('') || `<li class="mut">${L('log.empty')}</li>`}</ul>
  </section>`;
}

/* ---------- EMPIRE ---------- */
function floorRow(f) {
  const own = floorOn(S, f.id), next = nextFloor(S) === f.id;
  const lv = renoLvl(S, f.id), cost = renoCost(S, f.id), R0 = RENO[f.id];
  const stars = own ? `<span class="f-stars" aria-label="${lv}/5">${Array.from({ length: RENO_MAX }, (_, i) => `<i class="${i < lv ? 'on' : ''}">★</i>`).join('')}</span>` : '';
  const renoLine = own ? `<small class="f-reno">🔨 ${lv ? L('reno.now', { b: L('reno.k_' + R0.k, { p: Math.round(R0.v * lv * 100) }) }) : ''}${cost !== null ? (lv ? ' · ' : '') + L('reno.next', { b: L('reno.k_' + R0.k, { p: Math.round(R0.v * (lv + 1) * 100) }) }) : ''}</small>` : '';
  const btn = own
    ? cost !== null ? `<button class="btn sm reno" data-act="reno:${f.id}" ${S.cash >= cost ? '' : 'disabled'}><span>★${lv + 1}</span>${money(cost)}</button>` : `<span class="f-max px">★5</span>`
    : next ? `<button class="btn sm" data-act="flr:${f.id}" ${S.cash >= f.price ? '' : 'disabled'}>${money(f.price)}</button>` : `<small class="mut px">${money(f.price)}</small>`;
  return `<div class="floor ${own ? 'own lv' + lv : next ? 'next' : 'locked'}">
    <span class="f-no px">${f.id === 'b' ? 'B' : f.id.slice(1)}</span>
    <div class="f-t"><b>${L('floor.' + f.id + '.name')} · ${L('floor.' + f.id + '.sub')} ${stars}</b><small>${L('floor.' + f.id + '.perk')}</small>${renoLine}</div>
    ${btn}
  </div>`;
}
function itemRow(it) {
  const own = !!S.items[it.id];
  return `<div class="item ${own ? 'own' : ''}">
    <div class="f-t"><b>${L('item.' + it.id + '.name')}</b><small>${L('item.' + it.id + '.perk')}</small></div>
    ${own ? I('check', 16, 'grn') : `<button class="btn sm" data-act="buy:${it.id}" ${S.cash >= it.price ? '' : 'disabled'}>${money(it.price)}</button>`}
  </div>`;
}
function boostRow(b) {
  const on = S.buffs[b.id] > 0;
  return `<div class="item">
    <div class="f-t"><b>${L('boost.' + b.id + '.name')}${on ? ` <span class="chip cy sm" data-t="buff:${b.id}"></span>` : ''}</b><small>${L('boost.' + b.id + '.perk')}</small></div>
    <button class="btn sm pink" data-act="bst:${b.id}" ${S.gems >= b.price ? '' : 'disabled'}>${I('gem', 12)} ${b.price}</button>
  </div>`;
}
/* locations: after every IPO the studio moves to a new city with its own office look */
const LOCS = ['home', 'tokyo', 'valley', 'orbit'];
function locPanel() {
  const cur = Math.min(3, S.legacy || 0);
  return `<section class="panel locs">
    <h2 class="h2">🌍 ${L('loc.title')}</h2><p class="sub">${L('loc.sub')}</p>
    <div class="loc-list">${LOCS.map((id, i) => `<div class="loc ${i === cur ? 'on' : i < cur ? 'done' : 'lock'}"><span class="loc-ic">${['🏙️', '🗼', '🌴', '🛰️'][i]}</span><div><b>${L('loc.' + id)}</b><small>${i === 0 ? L('loc.start') : L('loc.after', { n: i })}${i === 3 ? ' +' : ''}</small></div>${i === cur ? `<span class="chip">${L('loc.here')}</span>` : i > cur ? '🔒' : '✓'}</div>`).join('')}</div>
  </section>`;
}
function tabEmpire() {
  const nf = nextFloor(S);
  return `<section class="panel towerp">
    <canvas id="tower" class="tower" width="80" height="124" aria-label="${esc(L('empire.tower'))}"></canvas>
    <div class="tw-t">
      <small class="px mut">${L('empire.rank')}</small>
      <b class="rank">${L('rank.' + rankOf(S))}</b>
      <ul class="stats">
        <li><span>${L('empire.earned')}</span><b>${money(S.earned)}</b></li>
        <li><span>${L('empire.rep')}</span><b>${S.rep.toFixed(1)}</b></li>
        <li><span>${L('empire.best')}</span><b>${S.stats.best ? S.stats.best.toFixed(1) : '—'}</b></li>
        ${S.legacy ? `<li><span>${L('empire.legacy')}</span><b class="yel">+${Math.round(S.legacy * 15)}%</b></li>` : ''}
      </ul>
    </div>
  </section>
  ${canPrestige(S) ? `<section class="panel ipo">
    <h2 class="h2">${I('crown', 16)} ${L('ipo.title')}</h2><p class="sub">${L('ipo.text', { gems: 3 + S.legacy })}</p>
    <p class="loc-next">✈️ ${L('loc.next', { name: L('loc.' + LOCS[Math.min(3, (S.legacy || 0) + 1)]) })}</p>
    <button class="btn gold" data-act="ipo">${L('ipo.btn')}</button></section>` : ''}
  ${locPanel()}
  <section class="panel">
    <div class="sec-h"><div><h2 class="h2">${L('empire.floors')}</h2><p class="sub">${nf ? L('empire.floors_sub') : L('empire.all_floors')} ${L('empire.reno_sub')}</p></div><span class="reno-total px" title="${esc(L('empire.stars'))}">★ ${renoStars(S)}/${FLOOR_ORDER.length * RENO_MAX}</span></div>
    <div class="floors">${FLOORS.slice().reverse().map(floorRow).join('')}</div>
  </section>
  <button class="panel citybn" data-act="city:visit:mall"><span class="cb-ico">🛋️</span><span class="cb-t"><b>${L('city.mall')}</b><small>${L('city.moved')}</small></span><b class="chev">›</b></button>`;
}

/* ---------- AIRDROP HUB ---------- */
function demoNote() {
  return NET.mode === 'demo' ? `<div class="note">${I('info', 14)} ${L('demo.note')}</div>` : '';
}
function riskNote(e) {
  if (e && e.dev) return `<div class="note">${I('gear', 14)} ${L('risk.dev')}</div>`;
  if (!e || !e.risk) return '';
  return `<div class="note warn">${I('shield', 14)} ${L(e.risk === 1 ? 'risk.watch' : 'risk.hold')}</div>`;
}
function checkinStrip(e) {
  const ci = e.checkin;
  const cur = ci.done ? ci.streak : ci.streak + 1;
  const slot = ((Math.max(1, cur) - 1) % 7);
  return `<div class="ci-row">${ci.table.map((v, i) => {
    const past = ci.done ? i <= slot : i < slot;
    const now = !ci.done && i === slot;
    return `<div class="ci ${past ? 'got' : ''} ${now ? 'now' : ''}"><small>${L('ui.day_s', { n: i + 1 })}</small><b>${v}</b>${past ? I('check', 10) : I('pts', 10)}</div>`;
  }).join('')}</div>`;
}
function hubHome() {
  const e = NET.eco;
  if (!e) return `<div class="loading">${L('ui.loading')}</div>`;
  const usedPct = Math.round((e.used || 0) * 100);
  const src = Object.keys(e.bySrc || {}).filter((k) => e.bySrc[k] > 0);
  const ci = e.checkin;
  return `${demoNote()}${riskNote(e)}${giveawayCard(e)}${airdropCard(e, true)}${mktPanel(true)}
  <section class="panel ptsp">
    <div class="season px">${esc((NET.cfg && NET.cfg.season && NET.cfg.season.name) || 'Season 0')}</div>
    <div class="bigpts">${I('pts', 34)}<b id="bigpts">${fmtPts(e.pts)}</b></div>
    <small class="mut">${L('hub.pts_label')}</small>
    <div class="today"><span>${L('hub.today')}: <b class="grn">+${fmtPts(e.today)}</b></span>${e.pass ? `<span class="chip yel">${I('star', 12)} Office Pass</span>` : ''}${e.sup ? `<span class="chip mag">${I('heart', 12)} ${L('sup.badge', { p: Math.round(((e.supBoost || 1.1) - 1) * 100) })}</span>` : ''}</div>
    <div class="meter"><div class="bar mag"><i style="width:${usedPct}%"></i></div><small>${L('hub.daily_meter', { p: usedPct })}</small></div>
    ${src.length ? `<div class="srcs">${src.map((k) => `<span class="chip">${L('src.' + k)} <b>+${fmtPts(e.bySrc[k])}</b></span>`).join('')}</div>` : ''}
  </section>
  <section class="panel">
    <div class="sec-h"><div><h2 class="h2">${I('calendar', 16)} ${L('hub.checkin')}</h2><p class="sub">${L('hub.checkin_sub', { n: ci.streak })}</p></div></div>
    ${checkinStrip(e)}
    ${ci.done ? `<button class="btn" disabled>${L('hub.checked')}</button>` : S.stats.taps < 20 ? `<button class="btn" disabled>${L('hub.checkin_play')}</button>` : `<button class="btn big glow" data-act="checkin">${L('hub.checkin_btn', { n: ci.next })}</button>`}
  </section>
  ${solCard(e)}
  <section class="panel how">
    <h2 class="h2">${I('info', 16)} ${L('hub.how')}</h2>
    <ul class="bul">
      <li>${L('hub.how1')}</li>
      <li>${L('hub.how2', { D: e.D })}</li>
      <li>${L('hub.how3')}</li>
      <li>${L('hub.how4')}</li>
    </ul>
    <p class="hint">${L('hub.disclaimer', { sym: esc((NET.cfg && NET.cfg.symbol) || 'OFFX') })}</p>
  </section>`;
}
function shortAddr(a) {
  a = String(a || '');
  return a.length > 14 ? a.slice(0, 6) + '…' + a.slice(-6) : a;
}
function hubFriends() {
  const e = NET.eco;
  if (!e) return `<div class="loading">${L('ui.loading')}</div>`;
  const r = e.ref || {};
  const f = UI.hub.friends;
  const act = r.active || 0;
  const goals = [[1, 20], [3, 60], [10, 200]];
  return `${demoNote()}
  <section class="panel invite">
    <div class="inv-art">${I('users', 30)}${I('gift', 22)}</div>
    <h2 class="h2">${L('fr.title2')}</h2>
    <div class="rw2">
      <div class="rw-box"><small>${L('fr.you_get')}</small><b class="mag">+${r.ptsEach || 25} ${L('res.pts_s')}</b><b>+${money(r.cashEach || 500)}</b><b>+${r.gemsEach || 2} 💎</b></div>
      <div class="rw-box"><small>${L('fr.friend_gets')}</small><b>+${money(r.welcome || 300)}</b><small class="mut">${L('fr.on_start')}</small></div>
    </div>
    ${r.link ? `<button class="btn big glow wide" data-act="share">${I('share', 16)} ${L('fr.share2')}</button>
    <button class="btn ghost sm" data-act="copy">${I('copy', 14)} ${L('fr.copy')}</button>` : `<button class="btn" disabled>${L('fr.no_link')}</button>`}
  </section>
  <section class="panel">
    <h2 class="h2">${L('fr.how')}</h2>
    <ol class="steps3">
      <li><span>1</span>${L('fr.step1')}</li>
      <li><span>2</span>${L('fr.step2')}</li>
      <li><span>3</span>${L('fr.step3')}</li>
    </ol>
    <p class="hint">${L('fr.plus', { p: Math.round((r.l1 || 0.1) * 100) })}</p>
  </section>
  <section class="panel">
    <div class="sec-h"><h2 class="h2">${L('fr.goals')}</h2><small class="mut">${L('fr.active_n', { n: act })}</small></div>
    <div class="ladder">${goals.map(([n, p]) => `<div class="rung ${act >= n ? 'on' : ''}"><b>${n}</b><small>${L('fr.friends_n')}</small><span class="mag">+${p} ${L('res.pts_s')}</span></div>`).join('')}</div>
    <p class="hint">${L('fr.goals_hint')}</p>
  </section>
  <section class="panel">
    <div class="sec-h"><h2 class="h2">${L('fr.list')}</h2><small class="mut">${r.total || 0}</small></div>
    ${!f ? `<div class="loading">${L('ui.loading')}</div>` : f.friends && f.friends.length ? `<div class="flist">${f.friends.map((x) => `<div class="fr">
      <span class="fav">${esc((x.name || '?').slice(0, 1).toUpperCase())}</span>
      <div class="f-t"><b>${esc(x.name || '—')}</b><small class="${x.active ? 'grn' : 'mut'}">${x.active ? '✅ ' + L('fr.is_active2') : '⏳ ' + L('fr.not_active2')}</small></div>
      <b class="mag">+${fmtPts(x.earned)}</b></div>`).join('')}</div>` : `<p class="sub">${L('fr.empty')}</p>`}
    <p class="hint">${L('fr.fair')}</p>
  </section>`;
}
function taskRow(t) {
  const ico = { channel: 'plane', chat: 'users', x: 'share', wallet: 'sol', play3: 'calendar', play14: 'calendar', streak3: 'fire', streak7: 'fire', invite1: 'users', invite3: 'users', invite10: 'users' }[t.id] || 'star';
  let right;
  if (t.done) right = I('check', 18, 'grn');
  else if (t.kind === 'tg') right = `<small class="mut">${L('task.tg_only')}</small>`;
  else if (t.claimable) right = `<button class="btn sm glow" data-act="task:claim:${t.id}">${L('ui.claim')}</button>`;
  else if (t.kind === 'channel') right = `<div class="tbtns"><button class="btn sm blue" data-act="task:go:${t.id}">${L('task.sub_btn')}</button><button class="btn sm ghost" data-act="task:claim:${t.id}">${L('task.check')}</button></div>`;
  else if (t.kind === 'visit') right = `<div class="tbtns"><button class="btn sm blue" data-act="task:go:${t.id}">${L('task.go')}</button>${t.started ? `<button class="btn sm ghost" data-act="task:claim:${t.id}">${L('task.check')}</button>` : ''}</div>`;
  else right = `<small class="px mut">${t.cur || 0}/${t.max}</small>`;
  const two = !t.done && !t.claimable && (t.kind === 'channel' || (t.kind === 'visit' && t.started));
  return `<div class="task ${t.done ? 'done' : ''} ${two ? 'two' : ''}">
    <span class="t-ico">${I(ico, 18)}</span>
    <div class="f-t"><b>${L('task.' + t.id)}</b><small class="mag">+${t.pts} ${L('res.pts_s')}</small></div>
    <div class="t-r">${right}</div>
  </div>`;
}
function hubTasks() {
  const t = UI.hub.tasks;
  return `${demoNote()}<section class="panel">
    <div class="sec-h"><div><h2 class="h2">${I('list', 16)} ${L('task.title')}</h2><p class="sub">${L('task.sub')}</p></div></div>
    ${t ? `<div class="tasks">${t.slice().sort((a, b) => (a.done ? 1 : 0) - (b.done ? 1 : 0) || (b.claimable ? 1 : 0) - (a.claimable ? 1 : 0)).map(taskRow).join('')}</div>` : `<div class="loading">${L('ui.loading')}</div>`}
  </section>`;
}
function hubTop() {
  const b = UI.hub.board;
  if (!b) return `<div class="loading">${L('ui.loading')}</div>`;
  const top3 = b.top.slice(0, 3);
  const pod = [top3[1], top3[0], top3[2]];
  return `${demoNote()}
  <section class="panel myrank">
    ${I('trophy', 28)}
    <div><small class="mut">${L('top.you')}</small><b class="px">${b.me ? '#' + b.me.rank : '—'}</b></div>
    <div class="r"><small class="mut">${L('top.players', { n: b.total })}</small><b class="mag">${b.me ? fmtPts(b.me.pts) : '0'}</b></div>
  </section>
  ${top3.length ? `<section class="podium">${pod.map((x, i) => x ? `<div class="pod p${[2, 1, 3][i]} ${x.me ? 'me' : ''}"><span class="pav">${esc((x.name || '?').slice(0, 1).toUpperCase())}</span><b>${flag(x.country)} ${esc(x.name || '—')}</b><small>${fmtPts(x.pts)}</small><div class="step px">${[2, 1, 3][i]}</div></div>` : '<div class="pod empty"></div>').join('')}</section>` : ''}
  <section class="panel">
    ${b.top.length > 3 ? `<ol class="board">${b.top.slice(3).map((x) => `<li class="${x.me ? 'me' : ''}"><span class="px rk">${x.rank}</span><b>${flag(x.country)} ${esc(x.name || '—')}</b><span class="mag">${fmtPts(x.pts)}</span></li>`).join('')}</ol>` : `<p class="sub">${b.top.length ? L('top.few') : L('top.empty')}</p>`}
    <p class="hint">${L('top.hint')}</p>
  </section>
  ${b.countries && b.countries.length ? `<section class="panel">
    <h2 class="h2">${I('trophy', 16)} ${L('top.countries')}</h2>
    <ol class="board">${b.countries.map((x) => `<li><span class="px rk">${x.rank}</span><b>${flag(x.c)} ${esc(countryName(x.c))} <small class="mut">· ${L('top.players_n', { n: x.n })}</small></b><span class="mag">${fmtPts(x.pts)}</span></li>`).join('')}</ol>
  </section>` : ''}`;
}
function flag(c) {
  if (!c || !/^[A-Z]{2}$/.test(c)) return '';
  return String.fromCodePoint(...[...c].map((x) => 127397 + x.charCodeAt(0)));
}
function countryName(c) {
  try { return new Intl.DisplayNames([LANG], { type: 'region' }).of(c) || c; } catch (e) { return c; }
}
function vipPanel(v) {
  if (!v) return '';
  return `<div class="vip-card">
    <div class="vip-h"><span class="vip-badge px">VIP ${v.lvl}</span><span>${L('shop.vip', { p: v.bonus })}</span></div>
    <div class="vip-bar"><i style="width:${(v.next ? Math.min(100, (v.spent / v.next.stars) * 100) : 100).toFixed(1)}%"></i></div>
    <small>${v.next ? L('shop.vip_next', { n: v.next.left, lvl: v.next.lvl, p: v.next.bonus }) : L('shop.vip_max')}</small>
  </div>`;
}
function hubShop() {
  const sh = UI.hub.shop;
  if (NET.mode === 'demo') return `${demoNote()}<section class="panel"><p class="sub">${L('shop.demo')}</p></section>`;
  if (!sh) return `<div class="loading">${L('ui.loading')}</div>`;
  return `<section class="panel">
    <div class="sec-h"><div><h2 class="h2">${I('cart', 16)} ${L('shop.title')}</h2><p class="sub">${L('shop.sub')}</p></div></div>
    ${vipPanel(sh.vip)}
    <div class="shop">${sh.items.map((it) => `<div class="sku ${it.sku === 'pass30' ? 'pass' : it.sku === 'starter' ? 'starter' : it.sku.startsWith('cash') ? 'cashp' : ''} ${it.owned ? 'owned' : ''}">
      <div class="sku-art">${it.sku === 'pass30' ? I('star3', 28) : it.sku === 'starter' ? I('gift', 28) : it.sku.startsWith('cash') ? I('coin', 28) : I('gem', 28)}</div>
      ${it.first || it.bonus ? `<div class="sku-tags">${it.first ? `<span class="tag x2">${L('shop.first')}</span>` : ''}${it.bonus ? `<span class="tag bon">+${it.bonus}%</span>` : ''}</div>` : ''}
      <b>${esc(it.title || it.sku)}</b>
      <small>${esc(it.desc || '')}</small>
      ${it.owned ? `<button class="btn sm" disabled>${L('shop.owned')}</button>` : `<div class="pay2"><button class="btn sm gold" data-act="pay:${it.sku}">⭐ ${it.stars}</button>${it.sol ? `<button class="btn sm solb" data-act="paysol:${it.sku}">${I('sol', 14)} ${it.sol} SOL</button>` : ''}</div>`}
    </div>`).join('')}</div>
    <p class="hint">${L('shop.vip_hint')}</p>
    ${sh.sol ? `<p class="hint">${L('shop.sol_hint')}</p>` : ''}
    <p class="hint">${L('shop.hint', { p: Math.round((((NET.eco && NET.eco.supBoost) || 1.1) - 1) * 100) })}</p>
  </section>`;
}
/* donation controls (Stars / SOL) + wall of thanks, shown inside the airdrop card */
function donateBits() {
  const d = UI.hub.donate;
  if (!d) return `<div class="loading">${L('ui.loading')}</div>`;
  const amt = donAmount();
  const chips = [100, 250, 500, 1000, 2500, 5000].filter((n) => n >= d.min && n <= d.max);
  const solAmt = d.sol ? solDonAmount() : 0;
  return `<div class="don-in">
    <h3 class="h3">⭐ ${L('don.title')}</h3>
    <div class="don-chips">${chips.map((n) => `<button class="chip ${n === amt ? 'on' : ''}" data-act="donamt:${n}">${fmt(n)} ⭐</button>`).join('')}</div>
    <label class="don-own"><small>${L('don.own', { min: d.min })}</small><input id="donin" type="number" inputmode="numeric" min="${d.min}" max="${d.max}" step="10" value="${amt}"></label>
    <div class="pay2"><button class="btn gold" data-act="donstars">⭐ ${L('don.give', { n: fmt(amt) })}</button>${d.sol ? `<button class="btn solb" data-act="donsol">${I('sol', 14)} ${solAmt} SOL</button>` : ''}</div>
    <small class="mut">${L('don.donors', { n: d.donors })}${d.mine ? ' · ' + L('don.mine', { n: fmt(d.mine) }) : ''}</small>
    <p class="hint">${L('don.note', { max: fmt(d.max) })}</p>
    ${d.top && d.top.length ? `<h3 class="h3">💖 ${L('don.wall')}</h3><ol class="board">${d.top.map((x) => `<li class="${x.me ? 'me' : ''}"><span class="px rk">${x.rank}</span><b>${flag(x.country)} ${esc(x.name || '—')}</b><span class="yel">${fmt(x.stars)} ⭐</span></li>`).join('')}</ol>` : ''}
  </div>`;
}
/* ---------- token market: live price on Solana (Jupiter), chart, holdings, buy ---------- */
function fmtUsdP(v) { if (!v) return '$0'; if (v >= 1) return '$' + v.toFixed(3); if (v >= 0.01) return '$' + v.toFixed(4); return '$' + v.toPrecision(3); }
function fmtUsdBig(v) { if (v === null || v === undefined) return '—'; return v >= 1e6 ? '$' + (v / 1e6).toFixed(2) + 'M' : v >= 1e3 ? '$' + (v / 1e3).toFixed(1) + 'K' : '$' + v.toFixed(0); }
function mktSpark(hist, up) {
  if (!hist || hist.length < 2) return `<div class="mk-nochart">${L('mk.collecting')}</div>`;
  const W = 320, H = 96, ys = hist.map((p) => p[1]), lo = Math.min(...ys), hi = Math.max(...ys), sp = hi - lo || hi || 1;
  const pts = hist.map((p, i) => [(i / (hist.length - 1)) * W, H - 6 - ((p[1] - lo) / sp) * (H - 14)]);
  const d = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join('');
  const col = up ? '#2ee66b' : '#ff4d6d';
  return `<svg class="mk-chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none"><defs><linearGradient id="mkg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${col}" stop-opacity=".45"/><stop offset="1" stop-color="${col}" stop-opacity="0"/></linearGradient></defs>
    <path d="${d}L${W} ${H}L0 ${H}Z" fill="url(#mkg)"/><path d="${d}" fill="none" stroke="${col}" stroke-width="2.5" vector-effect="non-scaling-stroke" style="filter:drop-shadow(0 0 6px ${col})"/>
    <circle cx="${pts[pts.length - 1][0]}" cy="${pts[pts.length - 1][1]}" r="4" fill="${col}" class="mk-dot"/></svg>`;
}
function mktSoon(mini) {
  const sym = esc((NET.cfg && NET.cfg.symbol) || 'OFFX'), ch = NET.cfg && NET.cfg.links && NET.cfg.links.channel;
  if (mini) return `<section class="panel mkt mini soon" data-act="mktab">${NET.mode === 'online' ? '<img class="mk-logo" src="/token.png" alt="">' : `<span class="mk-logo">${I('sol', 22)}</span>`}<div class="mk-mini-t"><b>${L('mk.soon_t', { sym })}</b><small>${L('mk.soon_s')}</small></div><span class="mk-soon-chip">${L('mk.soon')}</span></section>`;
  return `<section class="panel mkt soon">
    <div class="mk-h">${NET.mode === 'online' ? '<img class="mk-logo" src="/token.png" alt="">' : `<span class="mk-logo">${I('sol', 26)}</span>`}<div class="mk-name"><b>${sym} × Solana</b><small>${L('mk.soon_s')}</small></div><span class="mk-soon-chip">${L('mk.soon')}</span></div>
    <h2 class="h2">${L('mk.soon_t', { sym })}</h2>
    <p class="sub">${L('mk.soon_p', { sym })}</p>
    <ul class="bul"><li>${L('mk.soon_1', { sym })}</li><li>${L('mk.soon_2')}</li><li>${L('mk.soon_3', { sym })}</li></ul>
    ${ch ? `<button class="btn big glow wide" data-act="link:channel">${L('mk.soon_ch')}</button>` : ''}
  </section>`;
}
function mktPanel(mini) {
  if (!NET.cfg) return '';
  if (!NET.cfg.market) return mktSoon(mini);
  const r = UI.hub.mkt;
  if (!r || !r.on) return mini ? '' : `<section class="panel mkt"><div class="loading">${L('ui.loading')}</div></section>`;
  const m = r.m, sym = esc((m && m.sym) || NET.cfg.symbol || 'OFFX');
  if (!m) return mini ? '' : `<section class="panel mkt"><h2 class="h2">${sym}</h2><p class="sub">${L('mk.no_price')}</p></section>`;
  const ch = m.ch24, up = ch === null ? true : ch >= 0;
  const chip = ch === null ? '' : `<span class="mk-ch ${up ? 'up' : 'dn'}">${up ? '▲' : '▼'} ${Math.abs(ch).toFixed(2)}%</span>`;
  const logo = m.img ? `<img class="mk-logo" src="${esc(m.img)}" alt="">` : (NET.mode === 'online' ? '<img class="mk-logo" src="/token.png" alt="">' : `<span class="mk-logo">${I('sol', 26)}</span>`);
  if (mini) return `<section class="panel mkt mini" data-act="mktab">${logo}<div class="mk-mini-t"><b>${sym}</b><small>${L('mk.live')}</small></div><div class="mk-mini-p"><b class="px">${fmtUsdP(m.usd)}</b>${chip}</div><span class="mk-go">${L('mk.buy_s')} ›</span></section>`;
  const bal = UI.hub.mktBal, hasW = !!(NET.eco && NET.eco.sol);
  const rng = ['24h', '7d', '30d'].map((x) => `<button class="${(r.range || '24h') === x ? 'on' : ''}" data-act="mkrange:${x}">${L('mk.r_' + x)}</button>`).join('');
  return `<section class="panel mkt">
    <div class="mk-h">${logo}<div class="mk-name"><b>${sym}</b><small>Solana · Jupiter · ${L('mk.live')}</small></div><span class="mk-live"><i></i>LIVE</span></div>
    <div class="mk-price"><b class="px ${fmtUsdP(m.usd).length > 8 ? 'long' : ''}">${fmtUsdP(m.usd)}</b>${chip}</div>
    ${m.ton ? `<div class="mk-ton">≈ ${m.ton < 0.001 ? m.ton.toFixed(Math.min(14, 2 - Math.floor(Math.log10(m.ton)))) : m.ton.toFixed(5)} SOL</div>` : ''}
    <div class="mk-rng seg">${rng}</div>
    ${mktSpark(r.hist, up)}
    <div class="mk-stats"><div><small>${L('mk.vol')}</small><b>${fmtUsdBig(m.vol24)}</b></div><div><small>${L('mk.liq')}</small><b>${fmtUsdBig(m.liq)}</b></div><div><small>${L('mk.cap')}</small><b>${fmtUsdBig(m.fdv)}</b></div></div>
    <div class="mk-mine">${hasW ? (bal === null || bal === undefined ? `<small>${L('mk.bal_load')}</small>` : `<small>${L('mk.bal')}</small><b>${fmt(Math.floor(bal))} ${sym}</b><span class="mut">≈ ${(bal * m.usd >= 1000 ? fmtUsdBig(bal * m.usd) : fmtUsdP(bal * m.usd).replace('$0', '$0.00'))}</span>`) : `<small>${L('mk.bal_none')}</small><button class="btn ghost sm" data-act="sol:set">${L('sol.add')}</button>`}</div>
    <div class="mk-acts"><button class="btn big glow mk-buy" data-act="mkbuy">${I('sol', 18)} ${L('mk.buy_s')}</button><button class="btn big gold" data-act="donopen">⭐ ${L('don.btn')}</button></div>
    <button class="btn ghost sm wide" data-act="mkchart">${I('chart', 14)} ${L('mk.chart')}</button>
    <ol class="mk-how"><li>${L('mk.h1')}</li><li>${L('mk.h2', { sym })}</li><li>${L('mk.h3', { sym })}</li></ol>
    <p class="hint">${L('mk.risk')}</p>
  </section>`;
}

/* sponsor giveaway: prizes for the most active verified players over a few days */
function gwLeft(end) {
  const s = Math.max(0, Math.round((end - Date.now()) / 1000)), d = Math.floor(s / 86400);
  return d > 0 ? d + L('u.d') + ' ' + fmtDur(s - d * 86400) : fmtDur(s);
}
function giveawayCard(e, mini) {
  const g = e && e.giveaway;
  if (NET.mode === 'demo') return '';
  if (!g) return mini || !e ? '' : `<section class="panel gwnone"><span class="cb-ico">🎁</span><p class="hint">${L('gw.none')}</p>${NET.cfg && NET.cfg.links && NET.cfg.links.channel ? `<button class="btn sm" data-act="link:channel">📢 ${L('set.channel')}</button>` : ''}</section>`;
  const sum = g.prizes.reduce((a, b) => a + b, 0), medal = ['🥇', '🥈', '🥉'];
  const prizes = `<div class="gw-prizes">${g.prizes.map((p, i) => `<div class="gw-p p${i + 1}"><span>${medal[i] || '🏅'}</span><b class="px">$${p}</b><small>${L('gw.place', { n: i + 1 })}</small></div>`).join('')}</div>`;
  if (mini) return `<button class="panel gwbn" data-act="go:drop:home"><span class="cb-ico">🎁</span><span class="cb-t"><b>${L('gw.title_s', { sum })}</b><small>${g.done ? L('gw.done_s') : `${L('gw.left')} <span data-t="gwleft"></span>`}${g.me.rank ? ' · #' + g.me.rank : ''}</small></span><b class="chev">›</b></button>`;
  const top = g.top.length ? `<ol class="board gw-board">${g.top.map((x) => `<li class="${x.me ? 'me' : ''}"><span class="px rk">${medal[x.rank - 1] || x.rank}</span><b>${flag(x.country)} ${esc(x.name)}</b><span class="mag">${fmtPts(x.score)}${x.rank <= g.prizes.length ? ` · <i class="yel">$${g.prizes[x.rank - 1]}</i>` : ''}</span></li>`).join('')}</ol>` : `<p class="hint">${L('gw.empty')}</p>`;
  return `<section class="panel gw" id="gwcard">
    <div class="gw-h"><span class="gw-tag px">${L('gw.tag')}</span>${g.done ? `<span class="chip grn">${L('gw.done')}</span>` : `<span class="chip yel">⏳ <span data-t="gwleft"></span></span>`}</div>
    <h2 class="h2">🎁 ${L('gw.title', { sum })}</h2>
    <p class="sub">${L(g.done ? 'gw.sub_done' : 'gw.sub', { n: g.prizes.length })}</p>
    ${prizes}
    ${(() => {
      const subOk = g.sub !== false; // null = cannot be checked yet
      const ready = g.verified && subOk;
      const req = `<div class="gw-req">
        <div class="${g.verified ? 'ok' : ''}"><i>${g.verified ? '✅' : '1'}</i><span><b>${L('gw.req_ver')}</b><small>${L('gw.need_t')}</small></span>${g.verified ? '' : `<button class="btn sm glow" data-act="go:drop:token">${L('gw.verify')}</button>`}</div>
        <div class="${g.sub ? 'ok' : ''}"><i>${g.sub ? '✅' : '2'}</i><span><b>${L('gw.req_sub')}</b><small>${g.sub === null ? L('gw.sub_soon') : L('gw.req_sub_t')}</small></span>${g.sub ? '' : `<button class="btn sm glow" data-act="gwsponsor">📢 ${L('gw.sub_btn')}</button>${g.sub === false ? `<button class="btn sm ghost" data-act="gwcheck" ${UI.gwBusy ? 'disabled' : ''}>${L('gw.check')}</button>` : ''}`}</div>
      </div>`;
      return g.done ? '' : req + (ready ? `<div class="gw-me"><div><small>${L('gw.my')}</small><b class="px">${fmtPts(g.me.score)}</b></div><div><small>${L('gw.rank')}</small><b class="px">${g.me.rank ? '#' + g.me.rank : '—'}</b></div></div>` : `<p class="hint gw-lock">🔒 ${L('gw.lock')}</p>`);
    })()}
    ${g.pay ? `<div class="gw-pay ${g.pay.st}">${g.pay.st === 'sent' ? `✅ ${L('gw.pay_sent', { p: g.pay.usd })} ${g.pay.tx ? `<button class="linkbtn" data-act="gwtx">${L('ad.tx')}</button>` : ''}` : !NET.eco.sol ? `👛 ${L('gw.pay_wallet', { p: g.pay.usd })} <button class="btn sm glow" data-act="sol:set">${L('sol.add')}</button>` : `⏳ ${L('gw.pay_soon', { p: g.pay.usd })}`}</div>`
      : g.verified && !NET.eco.sol && !g.done ? `<div class="gw-pay wait">👛 ${L('gw.wallet_hint')} <button class="btn sm" data-act="sol:set">${L('sol.add')}</button></div>` : ''}
    <h3 class="h3">🏆 ${L('gw.top')}</h3>
    ${top}
    <p class="hint">${L('gw.rules')}</p>
  </section>`;
}
/* the OFFICE airdrop: fund progress → snapshot → claim */
function airdropCard(e, mini) {
  const a = e && e.airdrop;
  if (!a || NET.mode === 'demo') return '';
  const sym = esc((NET.cfg && NET.cfg.symbol) || 'OFFX');
  const amt = fmt(Math.floor(a.amount || 0));
  if (a.st === 'season') {
    const pct = Math.min(100, (a.usd / Math.max(1, a.goal)) * 100);
    return `<section class="panel adrop" id="adcard">
      <h2 class="h2">🪂 ${L('ad.wait_t', { sym })}</h2>
      ${mini ? '' : `<p class="sub">${L('ad.wait_s', { goal: '$' + fmt(a.goal), sym })}</p>`}
      <div class="fund-h"><b class="px">$${fmt(Math.round(a.usd))}</b><small class="mut">${L('don.of', { goal: '$' + fmt(a.goal) })}</small></div>
      <div class="vip-bar"><i style="width:${Math.max(2, pct).toFixed(1)}%"></i></div>
      ${a.verified ? `<small class="mut">✅ ${L('ad.verified_n', { n: fmt(a.verified) })}</small>` : ''}
      ${a.entry ? (a.entry.done ? `<div class="vcard"><i class="vbadge big">${I('check', 18)}</i><div><b>${L('ver.title')}</b><small>${L('ver.text')}</small></div></div>` : `<p class="sub">${L('ad.entry_t', { usd: '$' + a.entry.usd, sol: a.entry.sol })}</p>
        <button class="btn big glow wide" data-act="adentry">${I('sol', 16)} ${L('ad.entry_btn', { sol: a.entry.sol })}</button>`) : ''}
      ${mini ? `<button class="btn big gold wide" data-act="donopen" style="margin-top:8px">⭐ ${a.entry && !a.entry.done ? L('ad.entry_stars', { n: fmt((UI.hub.donate && UI.hub.donate.min) || 100) }) : L('don.btn_long')}</button>` : ''}
      ${a.entry && !a.entry.done ? `<p class="hint">${L('ad.entry_hint')}</p>` : ''}
      ${mini ? '' : donateBits()}
    </section>`;
  }
  let act;
  if (!(a.amount > 0)) act = `<p class="hint">${L('ad.none')}</p>`;
  else if (a.claim === 'sent') act = `<div class="ad-ok">${I('check', 16, 'grn')} ${L('ad.sent')}</div>${a.tx ? `<button class="linkbtn" data-act="adtx">${L('ad.tx')}</button>` : ''}`;
  else if (!e.sol) act = `<button class="btn big glow wide" data-act="sol:set">${I('sol', 16)} ${L('ad.need_wallet')}</button>`;
  else if (a.st !== 'claim') act = `<p class="hint">${L('ad.soon')}</p>`;
  else if (a.fee && !a.fee.paid) act = `<p class="sub">${L('ad.fee_t', { sol: a.fee.sol })}</p><button class="btn big glow wide" data-act="adfee">${I('sol', 16)} ${L('ad.fee_btn', { sol: a.fee.sol })}</button>`;
  else act = `<button class="btn big glow wide" data-act="adclaim" ${UI.adBusy ? 'disabled' : ''}>${UI.adBusy ? L('ad.sending') : L('ad.claim', { n: amt, sym })}</button>`;
  return `<section class="panel adrop live ${mini ? 'mini' : ''}">
    <h2 class="h2">🎉 ${L('ad.live_t', { sym })}</h2>
    ${a.amount > 0 ? `<div class="ad-amt"><small>${L('ad.yours')}</small><b class="px">${amt}</b><span>${sym}</span></div>` : ''}
    ${act}
    ${mini ? '' : `<p class="hint">${L('ad.how', { n: fmt(a.players || 0), sym })}</p>`}
  </section>`;
}
function solCard(e) {
  if (NET.mode === 'demo') return '';
  const w = e.sol;
  return `<section class="panel wallet solw">
    <div class="sec-h"><div><h2 class="h2">${I('sol', 16)} ${L('sol.title')}</h2><p class="sub">${L('sol.sub', { sym: esc((NET.cfg && NET.cfg.symbol) || 'OFFX') })}</p></div></div>
    ${w ? `<div class="waddr">${I('check', 14, 'grn')}<code>${esc(shortAddr(w))}</code></div>
      <div class="row2"><button class="btn ghost sm" data-act="sol:set">${L('wallet.change')}</button><button class="btn ghost sm" data-act="sol:off">${L('wallet.disconnect')}</button></div>`
    : `<button class="btn big glow wide" data-act="sol:set">${I('sol', 16)} ${L('sol.add')}</button>`}
  </section>`;
}
function hubToken() {
  const sym = esc((NET.cfg && NET.cfg.symbol) || 'OFFX');
  const e = NET.eco || {};
  const SUPPLY = 1e8;
  const big = (n) => (n >= 1e6 ? (n < 1e7 && n % 1e6 ? (n / 1e6).toFixed(1).replace(/\.0$/, '') : fmt(Math.round(n / 1e6))) + ' ' + L('tk2.mln') : fmt(Math.round(n)));
  // 90% of the supply is airdropped to players by points (10% goes to the liquidity pool): your share if the snapshot were taken today
  const early = SUPPLY * 0.9;
  const my = e.pts || 0, all = Math.max(e.ptsAll || 0, my, 1);
  const est = my > 0 ? (early * my) / all : 0;
  const share = my > 0 ? (my / all) * 100 : 0;
  const road = [['r1', 'now'], ['r2', 'next'], ['r3', 'next'], ['r4', 'later'], ['r5', 'later'], ['r6', 'later']];
  const uses = ['u1', 'u2', 'u3', 'u4'];
  return `${airdropCard(e)}${mktPanel()}<section class="panel tokhero2">
    <div class="coin3d"><div class="coin-face logo">${NET.mode === 'online' ? '<img src="/token.png" alt="">' : I('sol', 46)}</div></div>
    <h2 class="op-title">${sym}</h2>
    <p class="sub">${L('tok.what', { sym })}</p>
    <div class="hero-stats">
      <div><b class="px">100 ${L('tk2.bln_s')}</b><small>${L('tk2.supply')}</small></div>
      <div><b class="px">90%</b><small>${L('tk2.to_players')}</small></div>
      <div><b class="px">10%</b><small>${L('tk4.team0')}</small></div>
    </div>
    ${NET.cfg && NET.cfg.links && NET.cfg.links.site ? `<button class="btn ghost wide" data-act="site" style="margin-top:12px">🌐 ${L('tok.site')}</button>` : ''}
  </section>
  <section class="panel myshare">
    <h2 class="h2">🎁 ${L('tk2.my')}</h2>
    <div class="ms-grid">
      <div class="ms-big"><small>${L('tk2.est')}</small><b class="px">≈ ${big(est)}</b><span>${sym}</span></div>
      <div class="ms-side">
        <div><small>${L('tk2.my_pts')}</small><b class="mag">${fmtPts(my)}</b></div>
        <div><small>${L('tk2.my_share')}</small><b class="cy">${share >= 0.01 ? share.toFixed(2) : share > 0 ? '<0.01' : '0'}%</b></div>
      </div>
    </div>
    <p class="hint">${L('tk2.est_hint', { sym })}</p>
  </section>
  ${solCard(e)}
  <section class="panel">
    <h2 class="h2">${I('chart', 16)} ${L('tk4.title')}</h2>
    <p class="sub">${L('tk4.sub', { sym })}</p>
    <div class="formula">${L('tk4.formula', { sym })}</div>
    <ul class="bul"><li>${L('tk4.b1', { sym })}</li><li>${L('tk4.b2')}</li><li>${L('tk4.b3')}</li><li>${L('tk4.b4', { sym })}</li><li>${L('tk4.b6')}</li><li>${L('tk4.b5', { sym })}</li></ul>
    <p class="hint">${L('tk4.risk')}</p>
  </section>
  <section class="panel">
    <h2 class="h2">🚀 ${L('tk2.road')}</h2>
    <ol class="road2">${road.map((r, i) => `<li class="${r[1]}"><span class="rdot">${r[1] === 'now' ? '●' : i + 1}</span><div class="rd2"><div class="rd2-h"><b>${L('tk2.' + r[0], { sym })}</b><span class="chip ${r[1] === 'now' ? 'grn' : r[1] === 'next' ? 'cy' : ''}">${L('tok.' + r[1])}</span></div><small>${L('tk2.' + r[0] + 'd', { sym })}</small></div></li>`).join('')}</ol>
  </section>
  <section class="panel">
    <h2 class="h2">💡 ${L('tk2.uses', { sym })}</h2>
    <div class="uses">${uses.map((u) => `<div class="use">${L('tk2.' + u)}</div>`).join('')}</div>
    <p class="hint">${L('tk2.uses_hint')}</p>
  </section>
  <p class="hint pad">${L('hub.disclaimer', { sym })}</p>`;
}
function tabDrop() {
  const sub = UI.sub.drop;
  const body = sub === 'home' ? hubHome() : sub === 'token' ? hubToken() : sub === 'friends' ? hubFriends() : sub === 'tasks' ? hubTasks() : sub === 'top' ? hubTop() : hubShop();
  return segHTML('drop') + body;
}

/* ---------- tab dispatcher ---------- */
function tabHTML() {
  switch (UI.tab) {
    case 'studio': return tabStudio();
    case 'quests': return tabQuests();
    case 'empire': return tabEmpire();
    case 'drop': return tabDrop();
    case 'city': return tabCity();
    default: return tabOffice();
  }
}
